// Сгенерировано scripts/make_sounds.py из scripts/sounds/catalog.py — не править руками.
package ru.wishka.memory.data

import ru.wishka.memory.i18n.L10n

/** Стили звуков — разделы в выборе звука. */
enum class SoundStyle(val id: String, private val ru: String, private val en: String, private val hy: String) {
    Glass("glass", "Стекло 2.0", "Glass", "Ապակի"),
    Wood("wood", "Маримба и калимба", "Marimba & kalimba", "Մարիմբա և կալիմբա"),
    Pop("pop", "Поп и пузыри", "Pops & bubbles", "Փոփ և պղպջակներ"),
    Keys("keys", "Электропиано и лоу-фай", "Keys & lo-fi", "Էլեկտրադաշնամուր և լո-ֆայ"),
    Synth("synth", "Синт", "Synth", "Սինթ"),
    Ambient("ambient", "Амбиент", "Ambient", "Էմբիենթ"),
    EightBit("8bit", "8-бит", "8-bit", "8-բիթ"),
    Minimal("minimal", "Минимал", "Minimal", "Մինիմալ"),
    Nature("nature", "Природа", "Nature", "Բնություն"),
    Phone("phone", "Телефон", "Phone", "Հեռախոս"),
    ;

    val title: String get() = when {
        L10n.english -> en
        L10n.armenian -> hy
        else -> ru
    }
}

/**
 * Звуки уведомлений Memory. Файлы — res/raw/snd_<id>.ogg. free — доступен
 * без Memory+ (звук по умолчанию для вида доступен всегда).
 */
enum class NotifySound(val id: String, private val ru: String, private val en: String, private val hy: String, val style: SoundStyle, val free: Boolean) {
    Crystal("crystal", "Кристалл", "Crystal", "Բյուրեղ", SoundStyle.Glass, true),
    Stardust("stardust", "Звёздная пыль", "Stardust", "Աստղափոշի", SoundStyle.Glass, false),
    Moon("moon", "Луна", "Moon", "Լուսին", SoundStyle.Glass, false),
    Prism("prism", "Призма", "Prism", "Պրիզմա", SoundStyle.Glass, false),
    Droplet("droplet", "Капля света", "Light drop", "Լույսի կաթիլ", SoundStyle.Glass, true),
    Marimba("marimba", "Маримба", "Marimba", "Մարիմբա", SoundStyle.Wood, true),
    Marimba3("marimba3", "Маримба: три ноты", "Marimba trio", "Մարիմբա․ երեք նոտա", SoundStyle.Wood, false),
    Kalimba("kalimba", "Калимба", "Kalimba", "Կալիմբա", SoundStyle.Wood, false),
    Vibes("vibes", "Вибрафон", "Vibes", "Վիբրաֆոն", SoundStyle.Wood, false),
    Bamboo("bamboo", "Бамбук", "Bamboo", "Բամբուկ", SoundStyle.Wood, true),
    Pop("pop", "Поп", "Pop", "Փոփ", SoundStyle.Pop, true),
    Bubble("bubble", "Пузырь", "Bubble", "Պղպջակ", SoundStyle.Pop, true),
    Bloop("bloop", "Блуп-блуп", "Bloop", "Բլուպ-բլուպ", SoundStyle.Pop, false),
    Taptap("taptap", "Тап-тап", "Tap-tap", "Թափ-թափ", SoundStyle.Pop, false),
    Boing("boing", "Пружинка", "Spring", "Զսպանակ", SoundStyle.Pop, false),
    Swoosh("swoosh", "Отправлено", "Sent", "Ուղարկված է", SoundStyle.Pop, false),
    Rhodes("rhodes", "Родес", "Rhodes", "Ռոդս", SoundStyle.Keys, true),
    Lofi("lofi", "Лоу-фай", "Lo-fi", "Լո-ֆայ", SoundStyle.Keys, false),
    Jazz("jazz", "Джаз", "Jazz", "Ջազ", SoundStyle.Keys, true),
    Fmbell("fmbell", "Цифровой колокол", "Digital bell", "Թվային զանգ", SoundStyle.Keys, false),
    Spark("spark", "Искра", "Spark", "Կայծ", SoundStyle.Synth, false),
    Neon("neon", "Неон", "Neon", "Նեոն", SoundStyle.Synth, false),
    Pulse("pulse", "Пульс", "Pulse", "Զարկ", SoundStyle.Synth, true),
    Future("future", "Фьючер", "Future", "Ֆյուչեր", SoundStyle.Synth, false),
    Glide("glide", "Скольжение", "Glide", "Սահք", SoundStyle.Synth, true),
    Dawn("dawn", "Рассвет", "Dawn", "Արշալույս", SoundStyle.Ambient, true),
    Aurora("aurora", "Сияние", "Aurora", "Բևեռափայլ", SoundStyle.Ambient, false),
    Deep("deep", "Глубина", "Deep", "Խորություն", SoundStyle.Ambient, false),
    Coin("coin", "Монетка", "Coin", "Մետաղադրամ", SoundStyle.EightBit, true),
    Levelup("levelup", "Новый уровень", "Level up", "Նոր մակարդակ", SoundStyle.EightBit, false),
    Powerup("powerup", "Бонус", "Power-up", "Բոնուս", SoundStyle.EightBit, false),
    Pixel("pixel", "Пиксель", "Pixel", "Պիքսել", SoundStyle.EightBit, false),
    Tick("tick", "Тик", "Tick", "Տիկ", SoundStyle.Minimal, true),
    Knock("knock", "Тук-тук", "Knock", "Թակ-թակ", SoundStyle.Minimal, false),
    Softtap("softtap", "Мягкий тап", "Soft tap", "Փափուկ հպում", SoundStyle.Minimal, false),
    Ping("ping", "Пинг", "Ping", "Պինգ", SoundStyle.Minimal, true),
    Waterdrop("waterdrop", "Капля", "Water drop", "Կաթիլ", SoundStyle.Nature, true),
    Bird("bird", "Птичка", "Bird", "Թռչնակ", SoundStyle.Nature, true),
    Windchime("windchime", "Колокольчики ветра", "Wind chimes", "Քամու զանգակներ", SoundStyle.Nature, false),
    GuitarString("string", "Струна", "String", "Լար", SoundStyle.Nature, false),
    Comet("comet", "Комета", "Comet", "Գիսաստղ", SoundStyle.Glass, false),
    Nebula("nebula", "Туманность", "Nebula", "Միգամածություն", SoundStyle.Glass, false),
    Orbit("orbit", "Орбита", "Orbit", "Ուղեծիր", SoundStyle.Glass, false),
    Handpan("handpan", "Ханг", "Handpan", "Հանգ", SoundStyle.Wood, false),
    Xylophone("xylophone", "Ксилофон", "Xylophone", "Քսիլոֆոն", SoundStyle.Wood, false),
    Musicbox("musicbox", "Шкатулка", "Music box", "Երաժշտական տուփ", SoundStyle.Wood, false),
    Popchime("popchime", "Поп-звон", "Pop chime", "Փոփ-զանգ", SoundStyle.Pop, false),
    Celesta("celesta", "Челеста", "Celesta", "Չելեստա", SoundStyle.Keys, false),
    Laser("laser", "Лазер", "Laser", "Լազեր", SoundStyle.Synth, false),
    Wave("wave", "Волна", "Wave", "Ալիք", SoundStyle.Synth, false),
    Space("space", "Космос", "Space", "Տիեզերք", SoundStyle.Ambient, false),
    Chipmsg("chipmsg", "Пиксельное сообщение", "Pixel message", "Պիքսելային հաղորդագրություն", SoundStyle.EightBit, false),
    Chest("chest", "Сундук", "Treasure", "Սնդուկ", SoundStyle.EightBit, false),
    Toggle("switch", "Переключатель", "Switch", "Անջատիչ", SoundStyle.Minimal, false),
    Ding("ding", "Дзынь", "Ding", "Զնգոց", SoundStyle.Minimal, false),
    Cricket("cricket", "Сверчок", "Cricket", "Ծղրիդ", SoundStyle.Nature, false),
    Harp("harp", "Арфа", "Harp", "Տավիղ", SoundStyle.Nature, false),
    System("system", "Как в телефоне", "Phone default", "Ինչպես հեռախոսում", SoundStyle.Phone, true),
    Silent("silent", "Без звука", "Silent", "Առանց ձայնի", SoundStyle.Phone, true),
    ;

    val title: String get() = when {
        L10n.english -> en
        L10n.armenian -> hy
        else -> ru
    }

    /** Имя файла без расширения; null — звук телефона или тишина. */
    val file: String? get() = if (this == System || this == Silent) null else "snd_$id"

    companion object {
        fun of(id: String?): NotifySound? = entries.firstOrNull { it.id == id }

        /** Звук по умолчанию: у каждого вида — свой, чтобы различать на слух. */
        fun default(kind: NudgeKind): NotifySound = when (kind) {
            NudgeKind.Messages -> Crystal
            NudgeKind.Prism -> Prism
            NudgeKind.Reactions -> Stardust
            NudgeKind.Follows -> Orbit
            NudgeKind.Groups -> Moon
            NudgeKind.Friends -> Comet
            NudgeKind.Recap -> Nebula
        }

        /** Звуки стиля — в порядке каталога. */
        fun inStyle(style: SoundStyle): List<NotifySound> = entries.filter { it.style == style }
    }
}
