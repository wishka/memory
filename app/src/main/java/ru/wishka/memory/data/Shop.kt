package ru.wishka.memory.data

import ru.wishka.memory.i18n.t
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Витрина группы: каталог товаров с фото, ценой, описанием и наличием.
 * Оплаты внутри приложения нет: покупатель пишет продавцу (товар
 * прикрепляется к сообщению) или идёт на сайт магазина. Те же правила —
 * в Shop.swift.
 */
data class Product(
    val id: Long,
    val title: String,
    /** Цена в рублях; 0 — «по запросу». */
    val price: Long = 0,
    val description: String = "",
    val photos: List<Attachment> = emptyList(),
    val category: String = "",
    val inStock: Boolean = true,
    /** Страница товара на сайте магазина; "" — нет. */
    val url: String = "",
    /** Туманность вместо фото, пока его нет. */
    val seed: Int = 0,
) {
    val priceTitle: String get() = priceTitle(price)
}

/** «1 290 ₽»; 0 — «Цена по запросу». */
fun priceTitle(price: Long): String {
    if (price <= 0) return t("Цена по запросу")
    val symbols = DecimalFormatSymbols(Locale.ROOT).apply { groupingSeparator = ' ' }
    return DecimalFormat("#,##0", symbols).format(price) + " ₽"
}

/**
 * Цена из того, что ввёл продавец, в рублях: «1 290», «1290 ₽», «1 290 руб.»,
 * «1290,50» и «1290.50» (копейки отбрасываются), «1,290» и «1.290» (разряды).
 * Пусто — 0 (по запросу); не число — null.
 */
fun parsePrice(text: String): Long? {
    var s = text.trim().lowercase()
        .replace(Regex("(руб(лей|ля|ль)?|р)\\.?$"), "")
        .replace("₽", "")
        .replace(Regex("[\\s  ]"), "")
    if (s.isEmpty()) return 0
    // Последняя точка или запятая с 1–2 цифрами после — копейки; остальные — разряды
    val tail = Regex("[.,](\\d{1,2})$").find(s)
    if (tail != null) s = s.substring(0, tail.range.first)
    s = s.replace(".", "").replace(",", "")
    if (s.isEmpty() || !s.all { it.isDigit() }) return null
    return s.toLongOrNull()?.takeIf { it in 0..100_000_000 }
}

/** Ссылка на сайт: без схемы — https://. Пусто — пусто. */
fun normalizeUrl(text: String): String {
    val s = text.trim()
    if (s.isEmpty()) return ""
    return if (s.startsWith("http://", true) || s.startsWith("https://", true)) s else "https://$s"
}

/** Дополнительно о группе: подробное описание, сайт и контакты. Пустое — не показывается. */
data class GroupInfo(
    val about: String = "",
    val website: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val hours: String = "",
) {
    val isEmpty: Boolean get() = listOf(about, website, phone, email, address, hours).all { it.isBlank() }
}

/** Цвет группы: кнопки, цены, метки — под стиль магазина. */
enum class GroupAccent(val id: String, val argb: Long, private val ru: String) {
    Lilac("", 0xFFBC92CD, "Сиреневый"),
    Orchid("orchid", 0xFF844CAD, "Орхидея"),
    Sky("sky", 0xFF8CC4FF, "Небо"),
    Mint("mint", 0xFF7BE0B0, "Мята"),
    Gold("gold", 0xFFFFD86B, "Золото"),
    Coral("coral", 0xFFFF8A65, "Коралл"),
    Pink("pink", 0xFFFF9EDE, "Розовый"),
    ;

    val title: String get() = t(ru)

    companion object {
        fun of(id: String?): GroupAccent = entries.firstOrNull { it.id == id } ?: Lilac
    }
}

/** Категории витрины по порядку появления; пустая — «Без категории». */
fun List<Product>.categories(): List<String> = map { it.category.trim() }.filter { it.isNotEmpty() }.distinct()
