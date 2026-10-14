package ru.wishka.memory.data

import org.json.JSONArray
import org.json.JSONObject
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.Snapshot
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Сведения аккаунта для выгрузки — то, что хранится вне файла состояния. */
data class AccountExport(
    val login: String,
    val displayName: String,
    val info: ProfileInfo,
    val email: String,
    val emailConfirmed: Boolean,
    val consentVersion: String?,
    val consentAt: Long,
    val recoveryCodesLeft: Int,
)

/**
 * «Скачать мои данные» — право на доступ к своим данным (закон РА о
 * защите персональных данных, ст. 15 и 20; см. docs/legal/privacy.md).
 *
 * Архив .zip:
 *   README.txt      — что где лежит, на языке интерфейса
 *   account.json    — аккаунт: логин, имя, сведения профиля, почта, согласие
 *   memory.json     — записи, переписка, группы, PRISM, полка, настройки,
 *                     лента активности, жалобы — всё, что приложение хранит
 *   media/…         — фото, видео, голосовые, стикеры и своя музыка (по желанию)
 *
 * Пароль и коды восстановления в архив не попадают: их нет даже на
 * телефоне — только отпечатки.
 */
object DataExport {
    /** Пути файлов, на которые ссылается сохранённое: вложения, стикеры, аватар, музыка. */
    fun files(s: Snapshot, avatar: String = ""): List<String> {
        val atts = buildList {
            fun post(p: Post) {
                addAll(p.media)
                p.commentList.mapNotNullTo(this) { it.sticker }
            }
            s.posts.forEach(::post)
            s.groups.forEach { g -> g.posts.forEach(::post); g.cover?.let(::add); g.shop.forEach { pr -> addAll(pr.photos) } }
            s.chats.forEach { c -> c.messages.forEach { addAll(it.attachments) } }
            s.prisms.forEach { p -> p.video?.let(::add); p.cover?.let(::add) }
            addAll(s.recentGifs)
        }
        val paths = atts.flatMap { listOf(it.path) + it.frames } +
            s.stickers.flatMap { listOf(it.path) + it.frames } +
            s.tracks.map { it.path } +
            listOf(avatar, s.backdrop.photo) +
            s.chats.mapNotNull { it.backdrop?.photo }
        return paths.filter { it.isNotBlank() && !it.startsWith("http") && !it.startsWith("asset:") }.distinct()
    }

    /** Имя архива: memory-anna-2026-10-14.zip */
    fun fileName(login: String, now: Long): String {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getDefault() }.format(Date(now))
        return "memory-${login.filter { it.isLetterOrDigit() || it in "._" }}-$day.zip"
    }

    fun accountJson(a: AccountExport, now: Long): JSONObject = JSONObject()
        .put("exportedAt", iso(now))
        .put("login", a.login)
        .put("displayName", a.displayName)
        .put("profile", JSONObject()
            .put("bio", a.info.bio).put("city", a.info.city).put("birthday", a.info.birthday)
            .put("website", a.info.website).put("interests", JSONArray(a.info.interests))
            .put("avatar", if (a.info.avatar.isBlank()) JSONObject.NULL else "media/" + File(a.info.avatar).name))
        .put("email", if (a.email.isBlank()) JSONObject.NULL else JSONObject().put("address", a.email).put("confirmed", a.emailConfirmed))
        .put("consent", JSONObject()
            .put("documentsVersion", a.consentVersion ?: JSONObject.NULL)
            .put("acceptedAt", if (a.consentAt > 0) iso(a.consentAt) else JSONObject.NULL))
        .put("recoveryCodesLeft", a.recoveryCodesLeft)
        .put("password", t("не хранится — только отпечаток PBKDF2, в архив не попадает"))

    fun readme(a: AccountExport, now: Long, mediaCount: Int, withMedia: Boolean): String = buildString {
        appendLine(tf("Данные аккаунта @%s в Memory", a.login))
        appendLine(tf("Выгружено: %s", iso(now)))
        appendLine()
        appendLine(t("account.json — логин, имя, сведения профиля, почта и когда вы приняли документы."))
        appendLine(t("memory.json — записи, комментарии, переписка, группы, PRISM, полка, настройки приватности и уведомлений, лента активности и жалобы."))
        if (withMedia) appendLine(tf("media/ — ваши фото, видео, голосовые, стикеры и музыка: %s файлов.", mediaCount))
        else appendLine(t("Фото и видео не вошли: при выгрузке был выбран архив без медиа."))
        appendLine()
        appendLine(t("Пароля и кодов восстановления здесь нет: Memory хранит только их отпечатки."))
        appendLine(t("Вопросы о данных — по адресу из политики конфиденциальности (Настройки → Документы)."))
    }

    /**
     * Записать архив. files — пути на телефоне (см. files()); пропавшие
     * пропускаются. Ответ — сколько файлов медиа вошло.
     */
    fun write(out: OutputStream, account: AccountExport, stateJson: String, files: List<String>, withMedia: Boolean, now: Long): Int {
        val present = if (withMedia) files.map(::File).filter { it.isFile } else emptyList()
        ZipOutputStream(out).use { zip ->
            fun text(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            text("README.txt", readme(account, now, present.size, withMedia))
            text("account.json", accountJson(account, now).toString(2))
            text("memory.json", stateJson)
            val used = mutableSetOf<String>()
            for (f in present) {
                // Одинаковые имена из разных папок не затирают друг друга
                var name = f.name
                var i = 1
                while (!used.add(name)) name = f.nameWithoutExtension + "-" + (i++) + (if (f.extension.isEmpty()) "" else "." + f.extension)
                zip.putNextEntry(ZipEntry("media/$name"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return present.size
    }

    /** Сколько займут файлы медиа, байт — чтобы предупредить о большом архиве. */
    fun mediaBytes(files: List<String>): Long = files.map(::File).filter { it.isFile }.sumOf { it.length() }

    private fun iso(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = TimeZone.getDefault() }.format(Date(ms))
}
