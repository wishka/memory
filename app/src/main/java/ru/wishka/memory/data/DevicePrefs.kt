package ru.wishka.memory.data

import android.content.Context
import ru.wishka.memory.i18n.L10n
import ru.wishka.memory.i18n.Lang

/**
 * Настройки телефона, а не аккаунта: язык и размер текста. Одни на всех,
 * кто входит с этого телефона, и действуют уже на экране входа.
 */
class DevicePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)

    /** Прочитать сохранённое в L10n — при запуске, до первого экрана. */
    fun load() {
        L10n.choice = Lang.entries.firstOrNull { it.code == prefs.getString("lang", null) } ?: Lang.System
        L10n.textScale = prefs.getFloat("textScale", 1f).coerceIn(TEXT_SCALES.first(), TEXT_SCALES.last())
    }

    fun setLanguage(lang: Lang) {
        L10n.choice = lang
        prefs.edit().putString("lang", lang.code).apply()
    }

    fun setTextScale(scale: Float) {
        L10n.textScale = scale
        prefs.edit().putFloat("textScale", scale).apply()
    }

    companion object {
        /** Варианты размера текста: мелкий, обычный, крупный, очень крупный. */
        val TEXT_SCALES = listOf(0.9f, 1f, 1.15f, 1.3f)
    }
}
