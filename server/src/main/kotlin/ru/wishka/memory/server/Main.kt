package ru.wishka.memory.server

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.netty.Netty
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Запуск: переменные окружения (см. server/.env.example).
 *  PORT          — порт HTTP (8080)
 *  DATA_DIR      — где лежат загруженные файлы (/data)
 *  DATABASE_URL  — jdbc:postgresql://db:5432/memory; пусто — всё в памяти
 *  DB_USER, DB_PASSWORD
 *  FCM_CREDENTIALS — путь к ключу сервисного аккаунта Firebase (JSON); нет файла — без пушей
 */
fun main() {
    val env = System.getenv()
    val port = env["PORT"]?.toIntOrNull() ?: 8080
    val dataDir = File(env["DATA_DIR"] ?: "data").apply { mkdirs() }
    val dbUrl = env["DATABASE_URL"].orEmpty()
    val store: Store = if (dbUrl.isBlank()) {
        println("DATABASE_URL не задан — данные в памяти, пропадут при перезапуске")
        MemoryStore()
    } else {
        PgStore(dbUrl, env["DB_USER"].orEmpty(), env["DB_PASSWORD"].orEmpty())
    }
    val fcm = File(env["FCM_CREDENTIALS"] ?: "/run/secrets/firebase.json")
    val pusher: Pusher = if (fcm.isFile) {
        println("Пуши: FCM, ключ ${fcm.path}")
        FcmPusher(fcm.readText())
    } else {
        println("Пуши выключены: нет ${fcm.path} (docs/SERVER.md → «Пуши»)")
        NoPush
    }
    val core = Core(store, dataDir, pusher = pusher)
    println("Memory server: порт $port, файлы — ${dataDir.absolutePath}")
    embeddedServer(Netty, port = port, host = "0.0.0.0") { memory(core) }.start(wait = true)
}

/** Методы, у которых есть тело. */
private val WITH_BODY = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)

fun Application.memory(core: Core) {
    install(WebSockets) {
        // Поля самого WebSocketOptions (мс) — есть во всех версиях Ktor 2 и 3
        pingPeriodMillis = 30_000
        timeoutMillis = 60_000
        maxFrameSize = 64L * 1024
    }
    routing {
        // Живой канал: docs/REALTIME.md
        webSocket("/v1/realtime") {
            val auth = call.request.headers["Authorization"] ?: call.request.queryParameters["access"]?.let { "Bearer $it" }
            val session = withContext(Dispatchers.IO) { core.realtimeSession(auth) }
            if (session == null) {
                close(CloseReason(4401, "token"))
                return@webSocket
            }
            val (userId, until) = session
            val queue = Channel<String>(256)
            val peer = object : Hub.Peer {
                override fun send(text: String) {
                    queue.trySend(text)
                }
            }
            core.hub.join(userId, peer)
            val writer = launch { for (text in queue) outgoing.send(Frame.Text(text)) }
            // Токен истёк — закрыть: телефон обновит его и подключится снова
            val expiry = launch {
                delay((until - core.now()).coerceAtLeast(0))
                close(CloseReason(4401, "token expired"))
            }
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        if (text.length <= 4096) withContext(Dispatchers.IO) { core.onClientFrame(userId, text) }
                    }
                }
            } finally {
                core.hub.leave(userId, peer)
                queue.close()
                writer.cancel()
                expiry.cancel()
            }
        }
        // Всё остальное — в ядро (Core.handle)
        route("{...}") {
            handle {
                val method = call.request.httpMethod
                val body: ByteArray? = if (method in WITH_BODY) call.receive<ByteArray>() else null
                val headers = call.request.headers.entries().associate { it.key to it.value.firstOrNull().orEmpty() }
                val query = call.request.queryParameters.entries().associate { it.key to it.value.firstOrNull().orEmpty() }
                val req = Req(method.value, call.request.path(), query, headers, body)
                val resp = withContext(Dispatchers.IO) { core.handle(req) }
                for ((name, value) in resp.headers) call.response.header(name, value)
                val status = HttpStatusCode.fromValue(resp.code)
                val type = resp.contentType?.let { ContentType.parse(it) }
                val file = resp.file
                when {
                    file != null -> {
                        call.response.status(status)
                        call.respond(LocalFileContent(file, type ?: ContentType.Application.OctetStream))
                    }
                    resp.body.isEmpty() && type == null -> call.respond(status)
                    else -> call.respondBytes(resp.body, type, status)
                }
            }
        }
    }
}
