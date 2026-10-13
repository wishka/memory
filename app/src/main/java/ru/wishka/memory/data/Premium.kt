package ru.wishka.memory.data

import ru.wishka.memory.i18n.t

/**
 * Memory+ — премиум-доступ. Тарифы, цены, пробный период и что в какой
 * тариф входит. Описание для людей — в документе «Memory+: тарифы и
 * премиум-функции».
 *
 * Пока сервера нет, подписка хранится на телефоне, а оплата —
 * демонстрационная (деньги не списываются). Настоящую оплату подключают
 * через Google Play Billing и StoreKit: товары memory_plus_month,
 * memory_plus_year, memory_business_month, memory_business_year.
 */
enum class PlanTier(val id: String, private val ru: String, val rank: Int) {
    Free("free", "Бесплатно", 0),
    Plus("plus", "Memory+", 1),
    Business("business", "Memory+ Бизнес", 2),
    ;

    val title: String get() = t(ru)

    /** Этот тариф включает всё из [other]: Бизнес — и Memory+. */
    fun covers(other: PlanTier): Boolean = rank >= other.rank

    companion object {
        fun of(id: String?): PlanTier = entries.firstOrNull { it.id == id } ?: Free
    }
}

/** Срок оплаты. */
enum class BillingPeriod(val id: String, private val ru: String, val days: Int) {
    Month("month", "Месяц", 30),
    Year("year", "Год", 365),
    ;

    val title: String get() = t(ru)

    companion object {
        fun of(id: String?): BillingPeriod = entries.firstOrNull { it.id == id } ?: Month
    }
}

/**
 * Подписка аккаунта. [until] — до какого момента действует (мс);
 * [trial] — идёт пробный период; [autoRenew] — продлится сама;
 * [trialUsed] — пробный период уже брали (он один на аккаунт).
 */
data class Subscription(
    val tier: PlanTier = PlanTier.Free,
    val period: BillingPeriod = BillingPeriod.Month,
    val until: Long = 0,
    val trial: Boolean = false,
    val autoRenew: Boolean = false,
    val trialUsed: Boolean = false,
) {
    fun active(now: Long): Boolean = tier != PlanTier.Free && now < until

    /** Какой тариф действует сейчас: закончившийся — уже бесплатный. */
    fun tierAt(now: Long): PlanTier = if (active(now)) tier else PlanTier.Free

    /** Сколько полных и неполных дней осталось: 2.1 дня — «3 дня». */
    fun daysLeft(now: Long): Int = if (!active(now)) 0 else ((until - now + DAY_MS - 1) / DAY_MS).toInt()
}

const val DAY_MS = 24L * 60 * 60 * 1000

/** Что даёт премиум: для шторки «Это в Memory+» и значков PLUS. */
enum class PremiumFeature(val tier: PlanTier, private val ru: String, private val ruWhat: String) {
    StickerCutout(PlanTier.Plus, "Вырезка объекта", "Объект с фото вырезается сам — человек, питомец, предмет."),
    StickerLive(PlanTier.Plus, "Живые стикеры", "Эффекты — контур, комикс, объём, блеск — и движение: прыжок, пульс, парение."),
    StickerFrames(PlanTier.Plus, "Покадровая анимация", "Рисунок из нескольких кадров — до 12, со скоростью 4–12 к/с."),
    StickerPacks(PlanTier.Plus, "Коллекции стикеров", "Бесплатно — две коллекции, в Memory+ — сколько угодно."),
    Backdrops(PlanTier.Plus, "Все фоны переписок", "Все 10 фонов коллекции и своё фото."),
    ChatBackdrop(PlanTier.Plus, "Фон для отдельного чата", "У каждой переписки — свой фон."),
    Sounds(PlanTier.Plus, "Звуки Memory", "Все 8 звуков уведомлений: комета, луна, призма, орбита…"),
    PrismLong(PlanTier.Plus, "Ролики до 60 секунд", "Бесплатно — 15 и 30 секунд."),
    PrismFilters(PlanTier.Plus, "Все фильтры PRISM", "12 фильтров вместо четырёх."),
    PrismAdjust(PlanTier.Plus, "Цветокоррекция", "Яркость, контраст, насыщенность и тепло ролика."),
    Playlists(PlanTier.Plus, "Плейлисты без ограничений", "Бесплатно — три плейлиста."),
    Pins(PlanTier.Plus, "Шесть записей на витрине", "Бесплатно на витрине профиля — три записи."),
    Recs(PlanTier.Plus, "Больше советов по вкусу", "До 12 советов «Для вас» вместо трёх."),
    RecapHistory(PlanTier.Plus, "Итоги за любой период", "Бесплатно — текущий и прошлый месяц или год."),
    RecapHiRes(PlanTier.Plus, "Итоги в высоком разрешении", "Картинка 2160 × 2700 вместо 1080 × 1350."),
    ShopUnlimited(PlanTier.Business, "Витрина без ограничений", "Бесплатно — до 10 товаров в витрине группы."),
    GroupBackdrop(PlanTier.Business, "Фон страницы группы", "Цвета, градиенты или своё фото — группа в стиле вашего магазина."),
    GroupContacts(PlanTier.Business, "Контакты группы", "Телефон, почта, адрес и часы работы в карточке «О группе»."),
    ;

    val title: String get() = t(ru)
    val what: String get() = t(ruWhat)
}

object Premium {
    const val TRIAL_DAYS = 3

    const val FREE_PINS = 3
    const val PLUS_PINS = 6
    const val FREE_PLAYLISTS = 3
    const val FREE_PACKS = 2
    const val FREE_RECS = 3
    const val PLUS_RECS = 12
    const val FREE_PRODUCTS = 10

    /** Итоги: бесплатно — текущий и прошлый период (шаг назад 0 и 1). */
    const val FREE_RECAP_BACK = 1

    /** Множитель размера картинки итогов в Memory+. */
    const val HI_RES = 2

    val FREE_BACKDROPS = setOf(BackdropStyle.Cosmos, BackdropStyle.Nebula, BackdropStyle.Aurora, BackdropStyle.Plain)
    val FREE_SOUNDS = setOf(NotifySound.Crystal, NotifySound.System, NotifySound.Silent)
    val FREE_FILTERS = setOf(VideoFilter.None, VideoFilter.Crystal, VideoFilter.Lilac, VideoFilter.Night, VideoFilter.Noir)
    val FREE_LENGTHS = setOf(15, 30)

    /** Цена в рублях. */
    fun price(tier: PlanTier, period: BillingPeriod): Int = when (tier) {
        PlanTier.Free -> 0
        PlanTier.Plus -> if (period == BillingPeriod.Month) 199 else 1490
        PlanTier.Business -> if (period == BillingPeriod.Month) 490 else 3990
    }

    /** На сколько процентов год выгоднее двенадцати месяцев. */
    fun yearSaving(tier: PlanTier): Int {
        val month = price(tier, BillingPeriod.Month) * 12
        if (month == 0) return 0
        return Math.round(100.0 * (month - price(tier, BillingPeriod.Year)) / month).toInt()
    }

    /** «199 ₽/мес», «1 490 ₽/год». */
    fun priceTitle(tier: PlanTier, period: BillingPeriod): String =
        priceTitle(price(tier, period).toLong()) + if (period == BillingPeriod.Month) t("/мес") else t("/год")

    /** Что входит в тариф — по пунктам, для экрана тарифов. */
    fun features(tier: PlanTier): List<PremiumFeature> = when (tier) {
        PlanTier.Free -> emptyList()
        PlanTier.Plus -> PremiumFeature.entries.filter { it.tier == PlanTier.Plus }
        PlanTier.Business -> PremiumFeature.entries.filter { it.tier == PlanTier.Business }
    }

    /** Код товара в магазине приложений. */
    fun productId(tier: PlanTier, period: BillingPeriod): String = "memory_${tier.id}_${period.id}"
}
