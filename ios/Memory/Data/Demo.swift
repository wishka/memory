import Foundation

/// Выдуманные данные, пока у Memory нет сервера: всё живёт в памяти и
/// сбрасывается при перезапуске. Те же люди, записи и группы, что в
/// Android-версии.
enum Demo {
    static let me = User(
        id: 1, name: "Андрей", handle: "wishka",
        bio: "Собираю моменты, которые не хочется забывать.", hue: 0.15, online: true,
        city: "Москва", interests: ["Фото", "Музыка", "Путешествия"], joined: "октябрь 2026"
    )

    static let vera = User(
        id: 2, name: "Вера Лунная", handle: "vera.moon", bio: "Фотографирую ночное небо", hue: 0.05, online: true,
        city: "Санкт-Петербург", birthday: "14 марта", website: "veramoon.ru",
        interests: ["Фото", "Астрономия", "Природа"], joined: "май 2026", followers: 1284, followingCount: 211
    )
    static let mark = User(
        id: 3, name: "Марк Север", handle: "mark_north", bio: "Музыка, синтезаторы, полночь", hue: 0.75,
        city: "Казань", birthday: "2 ноября", interests: ["Музыка", "Кино"],
        joined: "июнь 2026", followers: 532, followingCount: 98
    )
    static let alina = User(
        id: 4, name: "Алина", handle: "alina.prism", bio: "Рисую свет", hue: 0.35, online: true,
        city: "Москва", birthday: "21 июля", website: "behance.net/alina.prism",
        interests: ["Искусство", "Фото"], joined: "июль 2026", followers: 2410, followingCount: 340
    )
    static let timur = User(
        id: 5, name: "Тимур", handle: "timur", bio: "Ищу звездопады", hue: 0.9,
        city: "Новосибирск", interests: ["Природа", "Спорт"], joined: "август 2026",
        followers: 87, followingCount: 120
    )
    static let sofia = User(
        id: 6, name: "София Ри", handle: "sofiari", bio: "Путешествия и плёнка", hue: 0.55,
        city: "Иркутск", birthday: "5 января", interests: ["Путешествия", "Фото", "Книги"],
        joined: "март 2026", followers: 978, followingCount: 260
    )

    static let people = [vera, mark, alina, timur, sofia]

    /// Изначальные подписки: Вера, Алина и София.
    static let following: Set<Int> = [2, 4, 6]

    static func person(_ id: Int) -> User? {
        id == me.id ? me : people.first { $0.id == id }
    }

    static let posts: [Post] = [
        Post(id: 101, author: vera, text: "Сегодня небо было как акварель: фиолетовый перетекал в синий " +
            "прямо над крышами. Кажется, такие вечера и стоит запоминать.", minutesAgo: 12, artSeed: 3, likes: 48, comments: 6),
        Post(id: 102, author: mark, text: "Записал новый трек в три часа ночи. Назвал его «Memory». " +
            "Совпадение? Не думаю.", minutesAgo: 95, likes: 21, comments: 3),
        Post(id: 103, author: alina, text: "Призма на подоконнике поймала солнце — вся комната в радугах.", minutesAgo: 260,
             artSeed: 11, likes: 87, liked: true, comments: 14),
        Post(id: 104, author: sofia, text: "Нашла старую плёнку из поездки на Байкал. Лёд, звёзды и тишина.", minutesAgo: 1500,
             artSeed: 27, likes: 132, comments: 19),
        Post(id: 105, author: timur, text: "Кто идёт смотреть звездопад в субботу? Беру термос и плед.", minutesAgo: 2900,
             likes: 9, comments: 11),
        Post(id: 106, author: vera, text: "Луна сегодня почти касалась шпиля Петропавловки.", minutesAgo: 4300,
             artSeed: 41, likes: 203, comments: 25),
        Post(id: 107, author: alina, text: "Новая серия «Свет в стекле» — скоро покажу целиком.", minutesAgo: 6100,
             artSeed: 52, likes: 156, comments: 31),
    ]

    static let chats: [Chat] = [
        Chat(id: 1, peer: vera, messages: [
            Message(id: 1, fromMe: false, text: "Ты видел сегодняшний закат?", time: "19:02"),
            Message(id: 2, fromMe: true, text: "Видел! Как будто кто-то пролил чернила", time: "19:05"),
            Message(id: 3, fromMe: false, text: "Скинула тебе фото в ленту ✨", time: "19:06"),
        ], unread: 1),
        Chat(id: 2, peer: mark, messages: [
            Message(id: 4, fromMe: false, text: "Послушай трек, нужен честный отзыв", time: "вчера"),
            Message(id: 5, fromMe: true, text: "Вечером обязательно", time: "вчера"),
        ]),
        Chat(id: 3, peer: alina, messages: [
            Message(id: 6, fromMe: true, text: "Где ты купила такую призму?", time: "пн"),
            Message(id: 7, fromMe: false, text: "На блошином рынке, за сто рублей :)", time: "пн"),
            Message(id: 8, fromMe: false, text: "Могу показать, где", time: "пн"),
        ], unread: 2),
        Chat(id: 4, peer: sofia, messages: [
            Message(id: 9, fromMe: false, text: "Привет! Давно не виделись", time: "12.09"),
        ]),
    ]

    static let groups: [MemoryGroup] = [
        MemoryGroup(
            id: 1, name: "Ночное небо", description: "Фото звёзд, луны и северного сияния. Делимся местами и советами по съёмке.",
            topic: "Фото", members: 4821, seed: 61, joined: true,
            posts: [
                Post(id: 201, author: vera, text: "Сегодня в 23:40 МКС пролетит над Петербургом — кто снимает?", minutesAgo: 40, likes: 64, comments: 12),
                Post(id: 202, author: timur, text: "Нашёл тёмное место в 40 км от Новосибирска: Млечный Путь виден глазом.", minutesAgo: 900, artSeed: 71, likes: 120, comments: 18),
            ]
        ),
        MemoryGroup(
            id: 2, name: "Синтезаторная полночь", description: "Электронная музыка, своё звучание и ночные сессии.",
            topic: "Музыка", members: 1290, seed: 62,
            posts: [Post(id: 203, author: mark, text: "Выложил пресеты для полночного пэда — бесплатно.", minutesAgo: 300, likes: 40, comments: 7)]
        ),
        MemoryGroup(
            id: 3, name: "Плёнка и дорога", description: "Путешествия, плёночная фотография, заметки из поездок.",
            topic: "Путешествия", members: 3305, seed: 63, joined: true,
            posts: [Post(id: 204, author: sofia, text: "Ольхон зимой: где жить и что снимать — длинный пост в комментариях.", minutesAgo: 2000, artSeed: 72, likes: 210, comments: 44)]
        ),
        MemoryGroup(
            id: 4, name: "Свет и стекло", description: "Искусство света: призмы, витражи, отражения.", topic: "Искусство", members: 870, seed: 64,
            posts: [Post(id: 205, author: alina, text: "Задание недели: снимите радугу от призмы у себя дома.", minutesAgo: 1200, likes: 77, comments: 23)]
        ),
        MemoryGroup(id: 5, name: "Читаем медленно", description: "Книжный клуб: одна книга в месяц и встречи по воскресеньям.", topic: "Книги", members: 640, seed: 65),
        MemoryGroup(id: 6, name: "Тропы и вершины", description: "Походы выходного дня и большие маршруты.", topic: "Природа", members: 2150, seed: 66),
    ]

    static let reels: [Reel] = [
        Reel(id: 301, author: vera, caption: "Таймлапс: луна поднимается над Невой 🌙", music: "Ночной город — Lumen", seed: 81, likes: 1820, comments: 64),
        Reel(id: 302, author: alina, caption: "Как призма раскладывает свет на радугу", music: "Prism Waves — Alina", seed: 82, likes: 3400, comments: 120, liked: true),
        Reel(id: 303, author: mark, caption: "Собираю трек из шума дождя за 30 секунд", music: "Rain Synth — Mark North", seed: 83, likes: 950, comments: 41),
        Reel(id: 304, author: sofia, caption: "Байкал: лёд звенит, если по нему постучать", music: "Ice Bells — Sofia Ri", seed: 84, likes: 5120, comments: 233),
        Reel(id: 305, author: timur, caption: "Звездопад Персеиды, 20 минут в 15 секундах", music: "Meteor — Timur", seed: 85, likes: 760, comments: 28),
    ]

    /// Ответы собеседника в демо-переписке — по кругу.
    static let replies = [
        "Как красиво сказано",
        "Согласна, это стоит запомнить",
        "Расскажи подробнее!",
        "Ха, точно 😄",
        "Давай встретимся на выходных?",
    ]
}
