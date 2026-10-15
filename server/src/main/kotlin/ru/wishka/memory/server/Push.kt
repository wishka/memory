package ru.wishka.memory.server

import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.util.Base64

/**
 * Куда уходят пуши. Сервер шлёт факты (docs/PUSH.md: type, actor,
 * actorId, chatId, body…) — текст складывает телефон, на своём языке.
 */
interface Pusher {
    /** false — токен больше не действует (приложение удалили): его надо забыть. */
    fun push(token: String, data: Map<String, String>): Boolean
}

/** Пуши выключены (не задан FCM_CREDENTIALS). */
object NoPush : Pusher {
    override fun push(token: String, data: Map<String, String>) = true
}

/**
 * Firebase Cloud Messaging, API HTTP v1. Вход — ключ сервисного аккаунта
 * (JSON из Firebase: Настройки проекта → Сервисные аккаунты → Создать ключ):
 * сервер подписывает им JWT (RS256), меняет на токен доступа Google на
 * час и шлёт сообщения только с данными (data) и высоким приоритетом.
 */
class FcmPusher(
    credentials: String,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
    private val clock: () -> Long = System::currentTimeMillis,
) : Pusher {
    private val email: String
    private val projectId: String
    private val tokenUri: String
    private val key: PrivateKey

    init {
        val o = JSONObject(credentials)
        email = o.getString("client_email")
        projectId = o.getString("project_id")
        tokenUri = o.optString("token_uri").ifEmpty { "https://oauth2.googleapis.com/token" }
        key = privateKey(o.getString("private_key"))
    }

    @Volatile private var access: String? = null
    @Volatile private var accessUntil = 0L

    /** JWT для обмена на токен доступа (виден тестам: подпись проверяется открытым ключом). */
    fun assertion(nowSec: Long = clock() / 1000): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = JSONObject().put("alg", "RS256").put("typ", "JWT").toString()
        val claims = JSONObject().put("iss", email).put("scope", SCOPE).put("aud", tokenUri)
            .put("iat", nowSec).put("exp", nowSec + 3600).toString()
        val unsigned = enc.encodeToString(header.toByteArray()) + "." + enc.encodeToString(claims.toByteArray())
        val sig = Signature.getInstance("SHA256withRSA").apply { initSign(key); update(unsigned.toByteArray()) }.sign()
        return unsigned + "." + enc.encodeToString(sig)
    }

    private fun accessToken(): String {
        val cached = access
        if (cached != null && clock() < accessUntil) return cached
        val form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8") +
            "&assertion=" + URLEncoder.encode(assertion(), "UTF-8")
        val req = HttpRequest.newBuilder(URI(tokenUri)).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form)).build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        check(resp.statusCode() == 200) { "Google OAuth: ${resp.statusCode()} ${resp.body().take(200)}" }
        val o = JSONObject(resp.body())
        val token = o.getString("access_token")
        access = token
        // С запасом в пять минут
        accessUntil = clock() + (o.optLong("expires_in", 3600) - 300) * 1000
        return token
    }

    override fun push(token: String, data: Map<String, String>): Boolean {
        val message = JSONObject().put("message", JSONObject()
            .put("token", token)
            .put("data", JSONObject().also { d -> data.forEach { (k, v) -> d.put(k, v) } })
            .put("android", JSONObject().put("priority", "HIGH")))
        val req = HttpRequest.newBuilder(URI("https://fcm.googleapis.com/v1/projects/$projectId/messages:send"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer " + accessToken())
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(message.toString())).build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() == 200) return true
        // Приложение удалено или токен сменился — этот токен больше не нужен
        val gone = resp.statusCode() == 404 || resp.body().contains("UNREGISTERED")
        if (!gone) System.err.println("FCM: ${resp.statusCode()} ${resp.body().take(300)}")
        return !gone
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/firebase.messaging"

        fun privateKey(pem: String): PrivateKey {
            val b64 = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "").filterNot { it.isWhitespace() }
            return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64)))
        }
    }
}
