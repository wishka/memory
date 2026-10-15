package ru.wishka.memory.data

import ru.wishka.memory.i18n.t

/**
 * Фон переписки. Коллекция — рисуется кодом в цветах приложения (ни одной
 * картинки в сборке), плюс своё фото с затемнением, чтобы пузыри читались.
 * Общий фон — в настройках, свой у чата — в его меню. Те же фоны — в
 * Backdrops.swift.
 */
enum class BackdropStyle(val id: String, private val ru: String) {
    Cosmos("cosmos", "Космос"),
    Nebula("nebula", "Туманность"),
    Aurora("aurora", "Сияние"),
    Crystals("crystals", "Кристаллы"),
    Constellations("constellations", "Созвездия"),
    Prism("prism", "Призма"),
    Dusk("dusk", "Сумерки"),
    Deep("deep", "Глубина"),
    Lilac("lilac", "Лиловый"),
    Plain("plain", "Однотонный"),
    Photo("photo", "Своё фото"),
    ;

    val title: String get() = t(ru)

    companion object {
        fun of(id: String?): BackdropStyle = entries.firstOrNull { it.id == id } ?: Cosmos

        /** Коллекция — всё, кроме своего фото. */
        val COLLECTION: List<BackdropStyle> get() = entries.filter { it != Photo }
    }
}

/** Выбранный фон: стиль, для своего фото — путь к копии и затемнение 0…0.8. */
data class Backdrop(val style: String = BackdropStyle.Cosmos.id, val photo: String = "", val dim: Float = 0.35f) {
    val kind: BackdropStyle get() = BackdropStyle.of(style).let { if (it == BackdropStyle.Photo && photo.isEmpty()) BackdropStyle.Cosmos else it }

    companion object {
        val DEFAULT = Backdrop()
    }
}

/** Какой фон у переписки: свой у чата, иначе общий. */
fun Chat.backdropOr(global: Backdrop): Backdrop = backdrop ?: global
