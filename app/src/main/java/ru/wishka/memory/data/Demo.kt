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
        city = "Москва", interests = listOf("Фото", "Музыка", "Путешествия"), joined = "октябрь 2026",
    )

    private val vera = User(
        2, "Вера Лунная", "vera.moon", "Фотографирую ночное небо", 0.05f, online = true,
        city = "Санкт-Петербург", birthday = "14 марта", website = "veramoon.ru",
        interests = listOf("Фото", "Астрономия", "Природа"), joined = "май 2026", followers = 1284, followingCount = 211,
    )
    private val mark = User(
        3, "Марк Север", "mark_north", "Музыка, синтезаторы, полночь", 0.75f,
        city = "Казань", birthday = "2 ноября", interests = listOf("Музыка", "Кино"),
        joined = "июнь 2026", followers = 532, followingCount = 98,
    )
    private val alina = User(
        4, "Алина", "alina.prism", "Рисую свет", 0.35f, online = true,
        city = "Москва", birthday = "21 июля", website = "behance.net/alina.prism",
        interests = listOf("Искусство", "Фото"), joined = "июль 2026", followers = 2410, followingCount = 340,
    )
    private val timur = User(
        5, "Тимур", "timur", "Ищу звездопады", 0.9f,
        city = "Новосибирск", interests = listOf("Природа", "Спорт"), joined = "август 2026",
        followers = 87, followingCount = 120,
    )
    private val sofia = User(
        6, "София Ри", "sofiari", "Путешествия и плёнка", 0.55f,
        city = "Иркутск", birthday = "5 января", interests = listOf("Путешествия", "Фото", "Книги"),
        joined = "март 2026", followers = 978, followingCount = 260,
    )

    val people = listOf(vera, mark, alina, timur, sofia)

    /** Изначальные подписки: Вера, Алина и София. */
    val following = setOf(2, 4, 6)

    /** Кто подписан на вас: Вера, Марк, Алина и Тимур. */
    val followers = setOf(2, 3, 4, 5)

    fun person(id: Int): User? = if (id == me.id) me else people.firstOrNull { it.id == id }

    val posts = listOf(
        Post(101, vera, "Сегодня небо было как акварель: фиолетовый перетекал в синий " +
            "прямо над крышами. Кажется, такие вечера и стоит запоминать.", 12, artSeed = 3, likes = 48, comments = 6,
            commentList = listOf(
                Comment(1001, alina, "Какие цвета! Где снимала?", 10),
                Comment(1002, sofia, "Это стоит распечатать и повесить на стену", 8),
            )),
        Post(102, mark, "Записал новый трек в три часа ночи. Назвал его «Memory». " +
            "Совпадение? Не думаю.", 95, likes = 21, comments = 3),
        Post(103, alina, "Призма на подоконнике поймала солнце — вся комната в радугах.", 260,
            artSeed = 11, likes = 87, liked = true, comments = 14,
            commentList = listOf(Comment(1003, vera, "Обожаю такие утра ✨", 200))),
        Post(104, sofia, "Нашла старую плёнку из поездки на Байкал. Лёд, звёзды и тишина.", 1500,
            artSeed = 27, likes = 132, comments = 19,
            commentList = listOf(
                Comment(1004, timur, "Байкал зимой — мечта", 1400),
                Comment(1005, mark, "Плёнка передаёт холод лучше любой цифры", 1300),
            )),
        Post(105, timur, "Кто идёт смотреть звездопад в субботу? Беру термос и плед.", 2900,
            likes = 9, comments = 11),
        Post(106, vera, "Луна сегодня почти касалась шпиля Петропавловки.", 4300,
            artSeed = 41, likes = 203, comments = 25),
        Post(107, alina, "Новая серия «Свет в стекле» — скоро покажу целиком.", 6100,
            artSeed = 52, likes = 156, comments = 31),
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

    val groups = listOf(
        Group(
            1, "Ночное небо", "Фото звёзд, луны и северного сияния. Делимся местами и советами по съёмке.",
            "Фото", 4821, seed = 61, joined = true,
            posts = listOf(
                Post(201, vera, "Сегодня в 23:40 МКС пролетит над Петербургом — кто снимает?", 40, likes = 64, comments = 12),
                Post(202, timur, "Нашёл тёмное место в 40 км от Новосибирска: Млечный Путь виден глазом.", 900, artSeed = 71, likes = 120, comments = 18),
            ),
        ),
        Group(
            2, "Синтезаторная полночь", "Электронная музыка, своё звучание и ночные сессии.",
            "Музыка", 1290, seed = 62,
            posts = listOf(Post(203, mark, "Выложил пресеты для полночного пэда — бесплатно.", 300, likes = 40, comments = 7)),
        ),
        Group(
            3, "Плёнка и дорога", "Путешествия, плёночная фотография, заметки из поездок.",
            "Путешествия", 3305, seed = 63, joined = true,
            posts = listOf(Post(204, sofia, "Ольхон зимой: где жить и что снимать — длинный пост в комментариях.", 2000, artSeed = 72, likes = 210, comments = 44)),
        ),
        Group(
            4, "Свет и стекло", "Искусство света: призмы, витражи, отражения.", "Искусство", 870, seed = 64,
            posts = listOf(Post(205, alina, "Задание недели: снимите радугу от призмы у себя дома.", 1200, likes = 77, comments = 23)),
        ),
        Group(5, "Читаем медленно", "Книжный клуб: одна книга в месяц и встречи по воскресеньям.", "Книги", 640, seed = 65),
        Group(6, "Тропы и вершины", "Походы выходного дня и большие маршруты.", "Природа", 2150, seed = 66),
    )

    val prisms = listOf(
        Prism(301, vera, "Таймлапс: луна поднимается над Невой 🌙", "Ночной город — Lumen", 81, 1820, 64,
            commentList = listOf(Comment(1101, sofia, "Залипла на минуту 🌙", 30))),
        Prism(302, alina, "Как призма раскладывает свет на радугу", "Prism Waves — Alina", 82, 3400, 120, liked = true,
            commentList = listOf(
                Comment(1102, vera, "Хочу такую же призму!", 90),
                Comment(1103, mark, "Под этот трек идеально", 60),
            )),
        Prism(303, mark, "Собираю трек из шума дождя за 30 секунд", "Rain Synth — Mark North", 83, 950, 41),
        Prism(304, sofia, "Байкал: лёд звенит, если по нему постучать", "Ice Bells — Sofia Ri", 84, 5120, 233),
        Prism(305, timur, "Звездопад Персеиды, 20 минут в 15 секундах", "Meteor — Timur", 85, 760, 28),
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
