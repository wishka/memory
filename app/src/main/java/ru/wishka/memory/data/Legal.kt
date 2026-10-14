package ru.wishka.memory.data

import android.content.Context
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang
import ru.wishka.memory.i18n.t

/**
 * Юридическое: данные оператора, версия документов, возраст и согласие.
 * Тексты документов — файлы .md в docs/legal (их же публикует сайт и читает
 * приложение: Gradle добавляет docs/legal в assets). Данные оператора в
 * квадратных скобках заполняет владелец — здесь и в docs/legal. Те же
 * правила на iPhone — Legal.swift (копии текстов — ios/Memory/Legal,
 * scripts/sync_legal.py).
 */
object Legal {
    /** Версия документов: поменялась — каждый вошедший заново принимает их. */
    const val VERSION = "2026-10-14"

    /** С какого возраста можно пользоваться Memory (и рейтинг в магазинах). */
    const val MIN_AGE = 16

    const val OPERATOR = "[Название оператора: ООО «…» / ИП …, ՀՎՀՀ …]"
    const val PRIVACY_EMAIL = "[privacy@домен]"
    const val ABUSE_EMAIL = "[abuse@домен]"
    /** Где опубликованы документы (GitHub Pages из docs или свой домен). */
    const val SITE = "[https://сайт-memory]"

    /** Заполнены ли данные оператора — до этого в приложении видна пометка «черновик». */
    val filled: Boolean get() = listOf(OPERATOR, PRIVACY_EMAIL, ABUSE_EMAIL, SITE).none { it.startsWith("[") }
}

/** Документы, которые видны в приложении: «Настройки → Документы», регистрация. */
enum class LegalDoc(val id: String, private val ru: String) {
    Terms("terms", "Пользовательское соглашение"),
    Privacy("privacy", "Политика конфиденциальности"),
    Consent("consent", "Согласие на обработку персональных данных"),
    Community("community", "Правила сообщества"),
    Recommendations("recommendations", "Как работают рекомендации"),
    Licenses("licenses", "Лицензии и авторы"),
    ;

    val title: String get() = t(ru)
    /**
     * Файл на языке интерфейса: terms.hy.md (армянский — основная редакция),
     * terms.md (русский), terms.en.md (английский).
     */
    val file: String get() = fileFor(L10n.lang)

    fun fileFor(lang: Lang): String = when (lang) {
        Lang.Hy -> "$id.hy.md"
        Lang.En -> "$id.en.md"
        else -> "$id.md"
    }

    /** Текст из assets на языке интерфейса; нет перевода — русская редакция. */
    fun load(context: Context): String =
        runCatching { context.assets.open(file).bufferedReader().use { it.readText() } }
            .recoverCatching { context.assets.open("$id.md").bufferedReader().use { it.readText() } }
            .getOrDefault("")
}

/**
 * Согласие с документами — на телефоне, по аккаунту: версия и время.
 * С сервером оно же уйдёт в журнал согласий оператора.
 */
class ConsentStore(context: Context) {
    private val prefs = context.getSharedPreferences("legal", Context.MODE_PRIVATE)

    fun accepted(username: String): Boolean = version(username) == Legal.VERSION

    fun version(username: String): String? = prefs.getString(key(username), null)?.substringBefore('|')

    fun acceptedAt(username: String): Long = prefs.getString(key(username), null)?.substringAfter('|', "")?.toLongOrNull() ?: 0

    fun accept(username: String, now: Long) {
        prefs.edit().putString(key(username), "${Legal.VERSION}|$now").apply()
    }

    fun forget(username: String) {
        prefs.edit().remove(key(username)).apply()
    }

    private fun key(username: String) = "consent." + username.trim().removePrefix("@").lowercase()
}
