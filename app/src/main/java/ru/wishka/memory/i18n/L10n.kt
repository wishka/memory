package ru.wishka.memory.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Язык приложения: как в системе телефона, русский или английский. */
enum class Lang(val code: String, private val name0: String) {
    System("system", "Как в системе"),
    Ru("ru", "Русский"),
    En("en", "English"),
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

    /** Русский — для русского и близких языков интерфейса телефона, иначе английский. */
    val english: Boolean
        get() = when (choice) {
            Lang.En -> true
            Lang.Ru -> false
            Lang.System -> systemLanguage !in RUSSIAN_SPEAKING
        }

    /** Множитель размера текста из настроек: 0.9 — мелкий … 1.3 — огромный. */
    var textScale by mutableFloatStateOf(1f)

    private val RUSSIAN_SPEAKING = setOf("ru", "be", "kk", "ky")
}

/** Строка интерфейса на выбранном языке. */
fun t(ru: String): String = if (L10n.english) EN[ru] ?: ru else ru

/** Строка с подстановками: tf("Вы в %s", x) — каждое %s по порядку. */
fun tf(ru: String, vararg args: Any?): String {
    var out = t(ru)
    for (a in args) out = out.replaceFirst("%s", a.toString())
    return out
}
