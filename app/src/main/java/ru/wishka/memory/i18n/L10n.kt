package ru.wishka.memory.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Язык приложения: как в системе телефона, русский, английский или армянский. */
enum class Lang(val code: String, private val name0: String) {
    System("system", "Как в системе"),
    Ru("ru", "Русский"),
    En("en", "English"),
    Hy("hy", "Հայերեն"),
    ;

    /** Названия языков — на своём языке; «Как в системе» переводится. */
    val title: String get() = if (this == System) t(name0) else name0
}

/**
 * Перевод интерфейса. Строки в коде — по-русски; t() возвращает их
 * английский вариант из словаря EN, если выбран английский. Состояние
 * наблюдаемое: смена языка в настройках сразу перерисовывает экраны.
 *
 * Тексты людей (записи, сообщения, имена) не переводятся — только
 * интерфейс.
 */
object L10n {
    /** Что выбрано в настройках. */
    var choice by mutableStateOf(Lang.System)

    /** Язык телефона — читается при запуске. */
    var systemLanguage: String = java.util.Locale.getDefault().language

    /**
     * Язык, который действует сейчас. «Как в системе»: армянский телефон —
     * армянский, русский и близкие — русский, остальные — английский.
     */
    val lang: Lang
        get() = when (choice) {
            Lang.System -> when (systemLanguage) {
                "hy" -> Lang.Hy
                in RUSSIAN_SPEAKING -> Lang.Ru
                else -> Lang.En
            }
            else -> choice
        }

    val english: Boolean get() = lang == Lang.En

    val armenian: Boolean get() = lang == Lang.Hy

    /** Локаль для дат и чисел. */
    val locale: java.util.Locale
        get() = when (lang) {
            Lang.En -> java.util.Locale.US
            Lang.Hy -> java.util.Locale("hy", "AM")
            else -> java.util.Locale("ru")
        }

    /** Множитель размера текста из настроек: 0.9 — мелкий … 1.3 — огромный. */
    var textScale by mutableFloatStateOf(1f)

    private val RUSSIAN_SPEAKING = setOf("ru", "be", "kk", "ky")
}

/** Строка интерфейса на выбранном языке. */
fun t(ru: String): String = when (L10n.lang) {
    Lang.En -> EN[ru] ?: ru
    Lang.Hy -> HY[ru] ?: ru
    else -> ru
}

/** Строка с подстановками: tf("Вы в %s", x) — каждое %s по порядку. */
fun tf(ru: String, vararg args: Any?): String {
    var out = t(ru)
    for (a in args) out = out.replaceFirst("%s", a.toString())
    return out
}
