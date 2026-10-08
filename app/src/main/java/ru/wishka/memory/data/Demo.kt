package ru.wishka.memory.data

/**
 * Выдуманные данные, пока у Memory нет сервера: всё живёт в памяти и
 * сбрасывается при перезапуске. Экраны работают с ними так же, как будут
 * работать с настоящими, — замена источника не потребует их переписывать.
 */
object Demo {
    val me = User(
        id = 1, name = "Андрей", handle = "wishka",
        bio = "Собираю моменты, которые не хочется забывать.", hue = 0.15f, online = true,
    )

    private val vera = User(2, "Вера Лунная", "vera.moon", "Фотографирую ночное небо", 0.05f, online = true)
    private val mark = User(3, "Марк Север", "mark_north", "Музыка, синтезаторы, полночь", 0.75f)
    private val alina = User(4, "Алина", "alina.prism", "Рисую свет", 0.35f, online = true)
    private val timur = User(5, "Тимур", "timur", "", 0.9f)
    private val sofia = User(6, "София Ри", "sofiari", "Путешествия и плёнка", 0.55f)

    val people = listOf(vera, mark, alina, timur, sofia)

    val posts = listOf(
        Post(101, vera, "Сегодня небо было как акварель: фиолетовый перетекал в синий " +
            "прямо над крышами. Кажется, такие вечера и стоит запоминать.", 12, artSeed = 3, likes = 48, comments = 6),
        Post(102, mark, "Записал новый трек в три часа ночи. Назвал его «Memory». " +
            "Совпадение? Не думаю.", 95, likes = 21, comments = 3),
        Post(103, alina, "Призма на подоконнике поймала солнце — вся комната в радугах.", 260,
            artSeed = 11, likes = 87, liked = true, comments = 14),
        Post(104, sofia, "Нашла старую плёнку из поездки на Байкал. Лёд, звёзды и тишина.", 1500,
            artSeed = 27, likes = 132, comments = 19),
        Post(105, timur, "Кто идёт смотреть звездопад в субботу? Беру термос и плед.", 2900,
            likes = 9, comments = 11),
    )

    val chats = listOf(
        Chat(
            1, vera,
            listOf(
                Message(1, false, "Ты видел сегодняшний закат?", "19:02"),
                Message(2, true, "Видел! Как будто кто-то пролил чернила", "19:05"),
                Message(3, false, "Скинула тебе фото в ленту ✨", "19:06"),
            ),
            unread = 1,
        ),
        Chat(
            2, mark,
            listOf(
                Message(4, false, "Послушай трек, нужен честный отзыв", "вчера"),
                Message(5, true, "Вечером обязательно", "вчера"),
            ),
        ),
        Chat(
            3, alina,
            listOf(
                Message(6, true, "Где ты купила такую призму?", "пн"),
                Message(7, false, "На блошином рынке, за сто рублей :)", "пн"),
                Message(8, false, "Могу показать, где", "пн"),
            ),
            unread = 2,
        ),
        Chat(
            4, sofia,
            listOf(Message(9, false, "Привет! Давно не виделись", "12.09")),
        ),
    )

    /** Ответы собеседника в демо-переписке — по кругу. */
    val replies = listOf(
        "Как красиво сказано",
        "Согласна, это стоит запомнить",
        "Расскажи подробнее!",
        "Ха, точно 😄",
        "Давай встретимся на выходных?",
    )
}
