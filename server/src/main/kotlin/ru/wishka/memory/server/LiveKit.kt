package ru.wishka.memory.server

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Токены LiveKit (звонки и эфиры): JWT HS256, подписанный секретом API.
 * Сам LiveKit (LiveKit Cloud или свой) пускает в комнату по такому токену:
 * кто (identity), куда (room) и что можно (публиковать или только смотреть).
 * docs/LIVE.md, https://docs.livekit.io/frontends/reference/tokens-grants/
 */
open class LiveKit(val url: String, private val apiKey: String, private val apiSecret: String) {
    /**
     * publish — можно публиковать камеру и микрофон (звонок, ведущий эфира);
     * false — только смотреть и слушать (зритель эфира).
     */
    fun token(identity: Int, name: String, room: String, publish: Boolean, now: Long, ttlMs: Long = TTL): String {
        val grant = JSONObject().put("room", room).put("roomJoin", true).put("canSubscribe", true)
            .put("canPublish", publish).put("canPublishData", false)
        if (publish) grant.put("canPublishSources", JSONArray(listOf("camera", "microphone")))
        return sign(JSONObject().put("sub", identity.toString()).put("name", name), grant, now, ttlMs)
    }

    private fun sign(base: JSONObject, grant: JSONObject, now: Long, ttlMs: Long): String {
        val claims = base.put("iss", apiKey).put("nbf", now / 1000 - 10).put("exp", (now + ttlMs) / 1000).put("video", grant)
        val header = b64(JSONObject().put("alg", "HS256").put("typ", "JWT").toString().toByteArray())
        val body = b64(claims.toString().toByteArray())
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(apiSecret.toByteArray(), "HmacSHA256")) }
        val sig = b64(mac.doFinal("$header.$body".toByteArray()))
        return "$header.$body.$sig"
    }

    /**
     * Закрыть комнату: звонок закончился, эфир кончился — все, кто в ней,
     * отключаются (их токены больше ничего не дают). В фоне: LiveKit
     * недоступен — звонок всё равно считается законченным.
     */
    open fun deleteRoom(room: String) = admin("DeleteRoom", JSONObject().put("room", room), JSONObject().put("roomCreate", true))

    /** Выгнать человека из комнаты (заблокировали, больше не может смотреть). */
    open fun removeParticipant(room: String, identity: Int) =
        admin("RemoveParticipant", JSONObject().put("room", room).put("identity", identity.toString()), JSONObject().put("room", room).put("roomAdmin", true))

    /** Server API LiveKit (Twirp): POST https://…/twirp/livekit.RoomService/<метод>. */
    private fun admin(method: String, body: JSONObject, grant: JSONObject) {
        val base = url.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://").trimEnd('/')
        val token = sign(JSONObject(), grant, System.currentTimeMillis(), 10 * 60_000L)
        val req = java.net.http.HttpRequest.newBuilder(java.net.URI.create("$base/twirp/livekit.RoomService/$method"))
            .header("Authorization", "Bearer $token").header("Content-Type", "application/json")
            .timeout(java.time.Duration.ofSeconds(10))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString())).build()
        runCatching { http.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.discarding()) }
    }

    companion object {
        /** Сколько действует токен: только чтобы войти в комнату; выгоняет сервер (deleteRoom, removeParticipant). */
        const val TTL = 2 * 60 * 60_000L

        private val http by lazy { java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build() }

        private fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        /** Из переменных окружения; чего-то нет — звонки и эфиры выключены (null). */
        fun fromEnv(env: Map<String, String>): LiveKit? {
            val url = env["LIVEKIT_URL"].orEmpty().trim()
            val key = env["LIVEKIT_API_KEY"].orEmpty().trim()
            val secret = env["LIVEKIT_API_SECRET"].orEmpty().trim()
            return if (url.isEmpty() || key.isEmpty() || secret.isEmpty()) null else LiveKit(url, key, secret)
        }
    }
}
