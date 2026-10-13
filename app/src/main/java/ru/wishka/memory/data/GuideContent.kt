// Сгенерировано scripts/sync_guide.py из docs/guide/guide.json — не править руками.
package ru.wishka.memory.data

/** Главы гида. Текст — в docs/guide/guide.json. */
internal object GuideContent {
    val chapters: List<GuideChapter> by lazy {
        listOf(chapter0(), chapter1(), chapter2(), chapter3(), chapter4(), chapter5(), chapter6(), chapter7(), chapter8(), chapter9(), chapter10(), chapter11())
    }

    private fun chapter0() = GuideChapter(
        "start", "🚀",
        GuideText("Первые шаги", "First steps"),
        GuideText("Вход, вкладки и как здесь всё устроено", "Signing in, tabs and how things work"),
        listOf(
            GuideTopic(
                "signup", GuideText("Регистрация и вход", "Signing up and in"),
                listOf(
                    GuideText("Придумайте логин и пароль — от 8 символов.", "Pick a username and a password of 8 characters or more."),
                    GuideText("Отметьте три галочки: вам есть 16 лет, вы принимаете соглашение и правила, вы согласны на обработку данных.", "Tick three boxes: you are 16 or older, you accept the terms and rules, and you consent to data processing."),
                    GuideText("Memory запомнит вас — пароль понадобится, только если вы выйдете.", "Memory remembers you — you only need the password again after signing out."),
                    GuideText("После пяти неверных попыток вход закрывается на 30 секунд.", "After five wrong attempts, signing in is paused for 30 seconds."),
                ),
                tip = GuideText("Хотите сначала осмотреться — войдите в демо: логин demo, пароль memory2026.", "Want to look around first? Use the demo: username demo, password memory2026."),
            ),
            GuideTopic(
                "taste", GuideText("Знакомство до регистрации", "A taste before signing up"),
                listOf(
                    GuideText("На первом экране выберите три любимых фильма или книги.", "On the first screen, pick three favourite films or books."),
                    GuideText("Сразу появится ваша полка и первый совет от людей с похожим вкусом.", "Your shelf appears right away, with a first tip from people with similar taste."),
                    GuideText("«Сохранить мою полку» ведёт к регистрации — выбранное перейдёт в новый аккаунт.", "“Save my shelf” takes you to sign-up, and your picks move into the new account."),
                ),
            ),
            GuideTopic(
                "tabs", GuideText("Семь вкладок", "The seven tabs"),
                listOf(
                    GuideText("Лента — записи людей: тексты, фото, чек-ины, опросы.", "Feed — people’s posts: text, photos, check-ins, polls."),
                    GuideText("Истории — ваши записи и записи друзей.", "Stories — your posts and your friends’."),
                    GuideText("PRISM — короткие вертикальные ролики во весь экран.", "PRISM — short full-screen vertical videos."),
                    GuideText("Чаты — переписка, голосовые, вложения.", "Chats — messages, voice notes, attachments."),
                    GuideText("Группы — сообщества по интересам и витрины магазинов.", "Groups — communities around interests, and shop showcases."),
                    GuideText("Вместе — общие цели, обмен навыками, клады и вылазки.", "Together — shared goals, skill swaps, caches and outings."),
                    GuideText("Профиль — ваша страница, полка, студия стикеров и настройки.", "Profile — your page, shelf, sticker studio and settings."),
                ),
                tip = GuideText("Подпись видна у выбранной вкладки; на остальных — только значок.", "Only the selected tab shows its name; the others show just an icon."),
            ),
            GuideTopic(
                "back", GuideText("Как вернуться назад", "Going back"),
                listOf(
                    GuideText("Жест или кнопка «назад» закрывает то, что открыто последним: фото, обсуждение, переписку, страницу человека, группу.", "The back gesture or button closes whatever opened last: a photo, comments, a chat, a person’s page, a group."),
                    GuideText("Со всех вкладок «назад» ведёт в Ленту, из Ленты — выход из приложения.", "From any tab, back leads to the Feed; from the Feed it leaves the app."),
                ),
            ),
            GuideTopic(
                "nudges", GuideText("Уведомления о людях", "Notifications about people"),
                listOf(
                    GuideText("Memory пишет о важном: подруга отметила фильм, который вы обсуждали; в группе новый участник; готовы итоги месяца.", "Memory tells you what matters: a friend checked in to a film you discussed, a new member joined your group, your monthly recap is ready."),
                    GuideText("Пока приложение открыто — плашка сверху, нажатие ведёт к событию.", "While the app is open, a banner slides in at the top; tap it to go there."),
                    GuideText("Что присылать и с каким звуком — в настройках уведомлений и звуков.", "Choose what to receive and which sound to play in notification and sound settings."),
                ),
                action = GuideAction.Notifications,
            ),
        ),
    )

    private fun chapter1() = GuideChapter(
        "feed", "📰",
        GuideText("Лента и записи", "Feed and posts"),
        GuideText("Публикации, чек-ины, опросы, комментарии", "Posts, check-ins, polls, comments"),
        listOf(
            GuideTopic(
                "post", GuideText("Новая запись", "A new post"),
                listOf(
                    GuideText("Нажмите на поле вверху Ленты и напишите текст.", "Tap the field at the top of the Feed and write something."),
                    GuideText("Добавьте фото или видео — из галереи или с камеры, до 10 на запись.", "Add photos or videos from the gallery or camera, up to 10 per post."),
                    GuideText("Можно приложить свой стикер, гифку, музыку, опрос или чек-ин.", "You can attach your own sticker, a GIF, music, a poll or a check-in."),
                    GuideText("Нажмите «Опубликовать».", "Tap “Post”."),
                ),
                action = GuideAction.Feed,
            ),
            GuideTopic(
                "scenarios", GuideText("Сценарии: фильм, пробежка, книга", "Shortcuts: film, run, book"),
                listOf(
                    GuideText("Над полем записи — «Посмотрел фильм», «Пробежка», «Книга», «Я здесь».", "Above the post field: “Watched a movie”, “Run”, “Book”, “I’m here”."),
                    GuideText("Сценарий сразу открывает чек-ин с нужными полями.", "A shortcut opens a check-in with the right fields."),
                    GuideText("После публикации видно, что изменилось: полка, вклад в общие цели, план недели.", "After posting you see what changed: your shelf, your share in shared goals, your weekly plan."),
                ),
            ),
            GuideTopic(
                "checkin", GuideText("Чек-ины", "Check-ins"),
                listOf(
                    GuideText("20 занятий: фильм, книга, бег, йога, кофе, концерт, путешествие, «я здесь», настроение и другие.", "20 activities: film, book, running, yoga, coffee, concert, travel, “I’m here”, mood and more."),
                    GuideText("Укажите, что именно и сколько: километры, минуты, страницы.", "Say what exactly and how much: kilometres, minutes, pages."),
                    GuideText("Фильмам и книгам можно поставить оценку 1–5, книге — статус: хочу, читаю, прочитал.", "Rate films and books 1–5; give a book a status: want to read, reading, finished."),
                    GuideText("Для пробежки укажите время — Memory посчитает темп.", "For a run, enter the time and Memory works out your pace."),
                    GuideText("Своё занятие: название, эмодзи и единица. Долгое нажатие на своё — удалить.", "Your own activity: a name, an emoji and a unit. Long-press your own to delete it."),
                ),
            ),
            GuideTopic(
                "poll", GuideText("Опросы", "Polls"),
                listOf(
                    GuideText("От 2 до 10 вариантов, один или несколько ответов.", "2 to 10 options, single or multiple choice."),
                    GuideText("Голос можно поменять, пока опрос открыт.", "You can change your vote while the poll is open."),
                    GuideText("Автор завершает опрос — итоги видны всем.", "The author closes the poll and everyone sees the results."),
                ),
                tip = GuideText("Опросы работают в Ленте, в группах и в переписке.", "Polls work in the Feed, in groups and in chats."),
            ),
            GuideTopic(
                "gif", GuideText("Гифки", "GIFs"),
                listOf(
                    GuideText("Встроенный набор Memory, недавние и свои из галереи.", "Memory’s built-in set, your recent ones, and your own from the gallery."),
                    GuideText("Поиск по всем гифкам GIPHY — по слову.", "Search all of GIPHY by keyword."),
                ),
            ),
            GuideTopic(
                "like", GuideText("«Нравится» и комментарии", "Likes and comments"),
                listOf(
                    GuideText("Сердечко под записью — «нравится»: вспышка искры и кристальное сердце.", "The heart under a post is a like — a spark and a crystal heart."),
                    GuideText("Нажмите на комментарии, чтобы открыть обсуждение.", "Tap the comment count to open the discussion."),
                    GuideText("Долгое нажатие на комментарий — ответить, скопировать, открыть автора, пожаловаться или удалить свой.", "Long-press a comment to reply, copy, open the author, report it, or delete your own."),
                    GuideText("Ответить можно своим стикером — кнопка слева от поля.", "Reply with your own sticker using the button left of the field."),
                ),
            ),
            GuideTopic(
                "photos", GuideText("Фото и видео на весь экран", "Full-screen photos and videos"),
                listOf(
                    GuideText("Нажмите на фото или видео — откроется на весь экран.", "Tap a photo or video to open it full screen."),
                    GuideText("Двумя пальцами — увеличить, двойное касание — увеличить в этой точке.", "Pinch to zoom; double-tap to zoom in at that spot."),
                ),
            ),
            GuideTopic(
                "pins", GuideText("Закладки и витрина профиля", "Saved posts and profile showcase"),
                listOf(
                    GuideText("Закладка сохраняет запись в «Сохранённое» в профиле.", "Bookmark a post to keep it in “Saved” on your profile."),
                    GuideText("«⋯» на своей записи — «Закрепить в профиле»: до трёх записей встают на витрину.", "“⋯” on your own post → “Pin to profile”: up to three posts go on your showcase."),
                ),
                action = GuideAction.Saved,
            ),
            GuideTopic(
                "memory", GuideText("«В этот день»", "“On this day”"),
                listOf(
                    GuideText("В Ленте появляется ваша запись год, месяц или неделю назад.", "The Feed shows your post from a year, a month or a week ago."),
                ),
            ),
        ),
    )

    private fun chapter2() = GuideChapter(
        "stories", "🌙",
        GuideText("Истории", "Stories"),
        GuideText("Записи друзей — отдельно от общей ленты", "Friends’ posts, apart from the main feed"),
        listOf(
            GuideTopic(
                "view", GuideText("Что здесь", "What’s here"),
                listOf(
                    GuideText("Ваши записи и записи друзей — без чужих людей.", "Your posts and your friends’ — no strangers."),
                    GuideText("Сверху — друзья в кольцах: нажатие показывает записи одного человека, долгое нажатие — его страницу.", "Friends in rings at the top: tap to see one person’s posts, long-press for their page."),
                ),
                action = GuideAction.Stories,
            ),
            GuideTopic(
                "new", GuideText("Новая история", "A new story"),
                listOf(
                    GuideText("Поле вверху — то же, что в Ленте: фото, стикеры, чек-ины.", "The field at the top works like the Feed’s: photos, stickers, check-ins."),
                ),
            ),
        ),
    )

    private fun chapter3() = GuideChapter(
        "prism", "💎",
        GuideText("PRISM", "PRISM"),
        GuideText("Короткие ролики: смотреть, снимать, монтировать", "Short videos: watch, shoot, edit"),
        listOf(
            GuideTopic(
                "watch", GuideText("Смотреть", "Watching"),
                listOf(
                    GuideText("Листайте вверх и вниз — ролики во весь экран.", "Swipe up and down through full-screen videos."),
                    GuideText("Нажатие — пауза, двойное нажатие — «нравится».", "Tap to pause, double-tap to like."),
                    GuideText("Справа — «нравится», комментарии, «Поделиться» и «Сохранить».", "On the right: like, comments, “Share” and “Save”."),
                ),
                action = GuideAction.Prism,
            ),
            GuideTopic(
                "shoot", GuideText("Снять свой ролик", "Shooting your own"),
                listOf(
                    GuideText("Нажмите «+» вверху справа.", "Tap “+” in the top right."),
                    GuideText("Камера: передняя или задняя, вспышка, таймер 3 или 10 секунд, длина 15, 30 или 60 секунд.", "Camera: front or back, flash, a 3 or 10 second timer, 15, 30 or 60 seconds long."),
                    GuideText("Запись можно ставить на паузу и продолжать.", "You can pause recording and carry on."),
                    GuideText("Или возьмите видео из галереи.", "Or pick a video from your gallery."),
                ),
                tip = GuideText("Выберите музыку до съёмки — она будет звучать во время записи и сразу встанет в редактор.", "Choose music before shooting — it plays while you record and is already in the editor."),
                action = GuideAction.NewPrism,
            ),
            GuideTopic(
                "edit", GuideText("Редактор", "The editor"),
                listOf(
                    GuideText("Обрезка до минуты и скорость от 0.5× до 3×.", "Trim to a minute and set speed from 0.5× to 3×."),
                    GuideText("Кадр 9:16, 4:5, 1:1 или оригинал; поворот и отражение.", "Frame 9:16, 4:5, 1:1 or original; rotate and flip."),
                    GuideText("12 фильтров и коррекция: яркость, контраст, насыщенность, тепло.", "12 filters and adjustments: brightness, contrast, saturation, warmth."),
                    GuideText("Текст с цветом, фоном или свечением — двигайте его пальцем.", "Text with colour, a background or a glow — drag it with your finger."),
                    GuideText("Стикеры, музыка с выбором начала, громкость видео и музыки, обложка.", "Stickers, music with a chosen start point, video and music volume, a cover."),
                ),
            ),
            GuideTopic(
                "publish", GuideText("Публикация", "Publishing"),
                listOf(
                    GuideText("Добавьте подпись и опубликуйте — ролик соберётся в обычный видеофайл.", "Add a caption and publish — the video is rendered into a regular video file."),
                    GuideText("Свой ролик можно удалить — корзина справа.", "Delete your own video with the bin on the right."),
                ),
            ),
            GuideTopic(
                "library", GuideText("Медиатека: музыка для роликов", "Library: music for videos"),
                listOf(
                    GuideText("Вкладка «Медиатека» рядом с «PRISM».", "The “Library” tab next to “PRISM”."),
                    GuideText("Открытая музыка с лицензиями Creative Commons: поиск, жанры, прослушивание.", "Open music under Creative Commons licences: search, genres, listening."),
                    GuideText("Понравившееся сохраняйте в «Мою музыку», если площадка разрешает скачивание.", "Save tracks you like to “My music” where the source allows downloads."),
                ),
                tip = GuideText("Автор и лицензия трека сами попадают в подпись ролика.", "The track’s author and licence are added to your caption automatically."),
            ),
        ),
    )

    private fun chapter4() = GuideChapter(
        "chats", "💬",
        GuideText("Чаты", "Chats"),
        GuideText("Переписка, голосовые, вложения, фон", "Messages, voice notes, attachments, backgrounds"),
        listOf(
            GuideTopic(
                "list", GuideText("Список переписок", "The chat list"),
                listOf(
                    GuideText("Сверху — поиск, у переписок — счётчик непрочитанных.", "Search at the top; unread counts on each chat."),
                    GuideText("Долгое нажатие на переписку — закрепить вверху, без звука, отметить прочитанной, заблокировать, очистить или удалить.", "Long-press a chat to pin it, mute it, mark it read, block, clear or delete it."),
                ),
                action = GuideAction.Chats,
            ),
            GuideTopic(
                "attach", GuideText("Вложения", "Attachments"),
                listOf(
                    GuideText("Скрепка — фото, видео, документы, свои стикеры, гифки, опрос и место на карте.", "The paperclip: photos, videos, documents, your stickers, GIFs, a poll and a location."),
                    GuideText("Место приходит маленькой картой; нажатие — карта на весь экран и «Открыть в Картах».", "A location arrives as a small map; tap it for a full map and “Open in Maps”."),
                ),
            ),
            GuideTopic(
                "voice", GuideText("Голосовые", "Voice notes"),
                listOf(
                    GuideText("Когда поле пустое, вместо кнопки отправки — микрофон.", "When the field is empty, the send button becomes a microphone."),
                    GuideText("Во время записи видно время и волну; можно удалить или отправить. До 5 минут.", "While recording you see the time and a waveform; delete or send. Up to 5 minutes."),
                    GuideText("Нажатие на волну в сообщении перематывает.", "Tap the waveform in a message to seek."),
                ),
            ),
            GuideTopic(
                "message", GuideText("Действия с сообщением", "Message actions"),
                listOf(
                    GuideText("Долгое нажатие на сообщение — ответить, переслать, закрепить, удалить у себя или у всех.", "Long-press a message to reply, forward, pin, or delete for you or for everyone."),
                    GuideText("Закреплённое видно полоской под шапкой.", "A pinned message shows as a strip under the header."),
                    GuideText("Нажатие на имя собеседника — его страница.", "Tap the other person’s name to open their page."),
                ),
            ),
            GuideTopic(
                "backdrop", GuideText("Фон переписки", "Chat background"),
                listOf(
                    GuideText("Общий фон для всех чатов — в настройках, «Фон переписок».", "One background for all chats: Settings → “Chat backgrounds”."),
                    GuideText("Свой фон для одной переписки — «⋯» в её шапке.", "A background for a single chat: “⋯” in its header."),
                    GuideText("Выбирайте из коллекции — космос, туманность, сияние, кристаллы — или своё фото.", "Pick from the collection — space, nebula, aurora, crystals — or your own photo."),
                ),
                action = GuideAction.Wallpaper,
            ),
        ),
    )

    private fun chapter5() = GuideChapter(
        "groups", "🪐",
        GuideText("Группы и витрины", "Groups and shops"),
        GuideText("Сообщества, магазины, оформление", "Communities, shops, styling"),
        listOf(
            GuideTopic(
                "find", GuideText("Найти и вступить", "Find and join"),
                listOf(
                    GuideText("Ищите по названию или по теме — почти 40 тем.", "Search by name or by topic — nearly 40 topics."),
                    GuideText("Откройте группу и нажмите «Вступить». Выйти можно в её настройках.", "Open a group and tap “Join”. You can leave from its settings."),
                ),
                action = GuideAction.Groups,
            ),
            GuideTopic(
                "create", GuideText("Своя группа", "Your own group"),
                listOf(
                    GuideText("«+» вверху вкладки «Группы».", "Tap “+” at the top of the Groups tab."),
                    GuideText("Название, описание и тема — и «Создать».", "Name, description and topic — then “Create”."),
                    GuideText("Шестерёнка на заставке: своя заставка, описание, кто может писать — все или только вы.", "The gear on the cover: your own cover, description, who can post — everyone or only you."),
                    GuideText("«⋯» на записи в группе — закрепить её первой.", "“⋯” on a group post pins it to the top."),
                ),
            ),
            GuideTopic(
                "look", GuideText("Оформление группы", "Styling your group"),
                listOf(
                    GuideText("Шестерёнка → «Оформление».", "Gear → “Look”."),
                    GuideText("Цвет группы — один из семи: в нём кнопки, цены и вкладки.", "A group colour — one of seven — tints buttons, prices and tabs."),
                    GuideText("Фон страницы — цвета, градиенты или своё фото.", "A page background — colours, gradients or your own photo."),
                ),
                tip = GuideText("Так группа-магазин выглядит в своём стиле.", "That way a shop group looks like your own brand."),
            ),
            GuideTopic(
                "about", GuideText("Карточка «О группе»", "The “About” card"),
                listOf(
                    GuideText("Шестерёнка → «О группе»: подробное описание, сайт, телефон, почта, адрес, часы работы.", "Gear → “About the group”: a longer description, website, phone, email, address, opening hours."),
                    GuideText("Участники видят карточку над записями: нажатие звонит, пишет или открывает карту.", "Members see the card above the posts: tap to call, email or open a map."),
                ),
            ),
            GuideTopic(
                "shop-owner", GuideText("Витрина: как продавать", "Showcase: selling"),
                listOf(
                    GuideText("В своей группе откройте вкладку «Витрина» и добавьте товар.", "In your group, open the “Showcase” tab and add a product."),
                    GuideText("До пяти фото, название, цена, описание, категория, наличие и ссылка на ваш сайт.", "Up to five photos, a title, price, description, category, stock and a link to your site."),
                    GuideText("Пустая цена или 0 — «Цена по запросу».", "An empty price or 0 shows “Price on request”."),
                    GuideText("В карточке товара — «Изменить», «Убрать», «Выше в витрине» и «Ниже».", "In a product card: “Edit”, “Remove”, “Move up” and “Move down”."),
                ),
            ),
            GuideTopic(
                "shop-buyer", GuideText("Витрина: как купить", "Showcase: buying"),
                listOf(
                    GuideText("Фильтруйте товары по категориям и откройте карточку.", "Filter products by category and open a card."),
                    GuideText("«Написать продавцу» — откроется переписка, товар уже приложен.", "“Message the seller” opens a chat with the product attached."),
                    GuideText("«Купить на сайте» — если продавец дал ссылку.", "“Buy on the website” — if the seller added a link."),
                ),
                tip = GuideText("Оплаты внутри Memory нет: о цене, оплате и доставке договаривайтесь с продавцом.", "There is no payment inside Memory: agree on price, payment and delivery with the seller."),
            ),
        ),
    )

    private fun chapter6() = GuideChapter(
        "together", "🤝",
        GuideText("Вместе", "Together"),
        GuideText("Делать что-то вместе, а не соревноваться", "Doing things together, not competing"),
        listOf(
            GuideTopic(
                "goals", GuideText("Цели", "Goals"),
                listOf(
                    GuideText("Общие шкалы сообщества: «Вместе пробежим 1 000 000 км», «10 000 прочитанных книг».", "Shared community counters: “Let’s run 1,000,000 km”, “10,000 books read”."),
                    GuideText("Вступили — и ваши чек-ины засчитываются сами. Вклад можно добавить и вручную.", "Join and your check-ins count automatically. You can also add progress by hand."),
                ),
                action = GuideAction.Together,
            ),
            GuideTopic(
                "skills", GuideText("Навыки", "Skills"),
                listOf(
                    GuideText("Банк времени без денег: научили кого-то 30 минут — получили 30 минут на любой навык.", "A time bank without money: teach someone for 30 minutes, get 30 minutes of any skill."),
                    GuideText("Отметьте, чему научите и чему хотите научиться; первыми идут взаимные обмены.", "Mark what you can teach and what you want to learn; mutual swaps come first."),
                    GuideText("Договорённость уходит в чат, «Занятие прошло» двигает баланс.", "The arrangement goes to a chat; “Session done” updates the balance."),
                ),
            ),
            GuideTopic(
                "journeys", GuideText("Дневники", "Journeys"),
                listOf(
                    GuideText("Короткие челленджи по дням: «День 3: учусь жонглировать».", "Short day-by-day challenges: “Day 3: learning to juggle”."),
                    GuideText("Выкладывайте процесс — сообщество советует, вы отмечаете полезное.", "Share your progress — the community gives tips and you mark the helpful ones."),
                ),
            ),
            GuideTopic(
                "missions", GuideText("Миссии и клады", "Missions and caches"),
                listOf(
                    GuideText("Миссии — квесты по городу: фото находки встаёт на общую карту там, где вы.", "Missions are city quests: your photo goes on a shared map where you are."),
                    GuideText("Клады — спрячьте контейнер с подсказкой. «Нашёл!» можно нажать только в 150 м от клада.", "Caches: hide a container with a clue. “Found it!” works only within 150 m of the cache."),
                ),
                tip = GuideText("Найдите три клада — и сможете прятать свои.", "Find three caches and you can hide your own."),
            ),
            GuideTopic(
                "outings", GuideText("Вылазки, рисунок, обмен", "Outings, drawing, swaps"),
                listOf(
                    GuideText("Вылазки — события до 10 человек: грибы, фотопрогулка, велопробег.", "Outings are events for up to 10 people: mushroom picking, photo walks, bike rides."),
                    GuideText("Рисунок — эстафета: каждый дорисовывает своё и передаёт дальше; готовое можно опубликовать.", "Drawing is a relay: everyone adds something and passes it on; the result can be published."),
                    GuideText("Обмен — книжный клуб, семена и растения, ремонт вещей.", "Swaps: a book club, seeds and plants, repairing things."),
                ),
            ),
            GuideTopic(
                "invite", GuideText("Позвать того, кого нет в Memory", "Invite someone who isn’t on Memory"),
                listOf(
                    GuideText("В рисунке, обмене, вылазке и навыках есть «Позвать» — уйдёт ссылка-приглашение.", "Drawing, swaps, outings and skills have “Invite”, which sends an invite link."),
                    GuideText("Ваш код приглашения — в настройках, «Друзья и идеи».", "Your invite code is in Settings → “Friends & ideas”."),
                ),
                action = GuideAction.Growth,
            ),
        ),
    )

    private fun chapter7() = GuideChapter(
        "friends", "✨",
        GuideText("Друзья", "Friends"),
        GuideText("Дружба взаимная — по заявке", "Friendship is mutual — by request"),
        listOf(
            GuideTopic(
                "add", GuideText("Добавить в друзья", "Adding a friend"),
                listOf(
                    GuideText("Откройте страницу человека — нажмите на имя или аватар.", "Open a person’s page by tapping their name or avatar."),
                    GuideText("«Добавить в друзья» отправит заявку; пока её не приняли, видно «Заявка отправлена».", "“Add friend” sends a request; until it’s accepted you see “Request sent”."),
                    GuideText("Когда человек примет заявку, вы станете друзьями взаимно.", "When they accept, you become friends both ways."),
                ),
            ),
            GuideTopic(
                "requests", GuideText("Заявки в друзья", "Friend requests"),
                listOf(
                    GuideText("Входящие — в профиле, «Заявки в друзья»: принять или отклонить.", "Incoming requests are in Profile → “Friend requests”: accept or decline."),
                    GuideText("Там же — заявки, которые отправили вы.", "Requests you’ve sent are there too."),
                ),
                action = GuideAction.Requests,
            ),
            GuideTopic(
                "remove", GuideText("Убрать из друзей", "Removing a friend"),
                listOf(
                    GuideText("В списке друзей или на странице человека — «Удалить из друзей», с подтверждением.", "In your friends list or on their page: “Remove from friends”, with a confirmation."),
                ),
                action = GuideAction.Friends,
            ),
            GuideTopic(
                "why", GuideText("Зачем друзья", "What friends see"),
                listOf(
                    GuideText("Записи друзей — во вкладке «Истории».", "Friends’ posts appear in Stories."),
                    GuideText("Закрытый профиль видят только друзья.", "Only friends can see a private profile."),
                ),
            ),
        ),
    )

    private fun chapter8() = GuideChapter(
        "profile", "👤",
        GuideText("Профиль", "Profile"),
        GuideText("Страница, полка, итоги, студия и музыка", "Your page, shelf, recaps, studio and music"),
        listOf(
            GuideTopic(
                "edit", GuideText("Ваша страница", "Your page"),
                listOf(
                    GuideText("Аватар — фото из галереи или с камеры, с обрезкой по кругу.", "Avatar: a photo from the gallery or camera, cropped to a circle."),
                    GuideText("«О себе», город, день рождения, сайт и интересы.", "“About me”, city, birthday, website and interests."),
                    GuideText("Счётчики записей, друзей и групп открывают свои списки.", "The post, friend and group counters open their lists."),
                ),
                action = GuideAction.Profile,
            ),
            GuideTopic(
                "home", GuideText("Главная профиля", "Profile home"),
                listOf(
                    GuideText("Витрина: до трёх закреплённых записей, ваши стикеры и музыка.", "Showcase: up to three pinned posts, your stickers and music."),
                    GuideText("Активность за две недели и кристаллы-достижения.", "Two weeks of activity and crystal achievements."),
                    GuideText("Вкладки «Записи · PRISM · Медиа», ваши группы и друзья.", "Tabs “Posts · PRISM · Media”, your groups and friends."),
                ),
            ),
            GuideTopic(
                "shelf", GuideText("Полка", "Shelf"),
                listOf(
                    GuideText("Собирается из чек-инов сама: фильмы с оценками, книги по статусам, спорт, места.", "Built from your check-ins: rated films, books by status, sport, places."),
                    GuideText("«Для вас» — советы от людей с похожим вкусом и план бега на неделю.", "“For you”: tips from people with similar taste and a weekly running plan."),
                    GuideText("«Права» — что вы уже открыли: свои клады, миссии, цели, звание наставника.", "“Abilities”: what you’ve earned — your own caches, missions, goals, the mentor title."),
                ),
                action = GuideAction.Shelf,
            ),
            GuideTopic(
                "recap", GuideText("Итоги месяца и года", "Monthly and yearly recaps"),
                listOf(
                    GuideText("Картинка на звёздном небе: фильмы, книги, километры, места, записи, «нравится».", "A picture on a starry sky: films, books, kilometres, places, posts, likes."),
                    GuideText("Стрелки листают прошлые периоды.", "Arrows page through earlier periods."),
                    GuideText("Опубликуйте в Ленту или Истории, отправьте в чат или сохраните в галерею.", "Post it to the Feed or Stories, send it to a chat or save it to your gallery."),
                ),
                action = GuideAction.Recap,
            ),
            GuideTopic(
                "studio", GuideText("Студия стикеров", "Sticker studio"),
                listOf(
                    GuideText("Из фото — с формой (круг, сердце, кристалл, звезда), обводкой и подписью.", "From a photo, with a shape (circle, heart, crystal, star), outline and caption."),
                    GuideText("Вырезка — объект с фото вырезается сам.", "Cut-out: the subject is lifted out of the photo automatically."),
                    GuideText("Рисунок кистью — и покадровый, до 12 кадров.", "Draw with a brush, including frame-by-frame animation, up to 12 frames."),
                    GuideText("Конструктор смайлов и значок с текстом или эмодзи.", "An emoji face builder and a badge with text or an emoji."),
                    GuideText("Живые стикеры: эффекты (контур, комикс, объём, блеск) и движение (прыжок, пульс, парение…).", "Live stickers: effects (outline, comic, 3D, shine) and motion (jump, pulse, float…)."),
                ),
                tip = GuideText("Собирайте стикеры в коллекции — они станут вкладками при выборе стикера.", "Group stickers into collections — they become tabs in the sticker picker."),
                action = GuideAction.Studio,
            ),
            GuideTopic(
                "plus", GuideText("Memory+ и пробный период", "Memory+ and the free trial"),
                listOf(
                    GuideText("Профиль → карточка «Memory+»: тарифы, цены и что входит.", "Profile → the “Memory+” card: plans, prices and what’s included."),
                    GuideText("Memory+ — стикеры, фоны, звуки, PRISM до 60 секунд, больше плейлистов и итоги за любой период.", "Memory+: stickers, backgrounds, sounds, PRISM up to 60 seconds, more playlists and recaps for any period."),
                    GuideText("Memory+ Бизнес — всё из Memory+ и витрина без ограничений, фон страницы группы и контакты.", "Memory+ Business: everything in Memory+, plus an unlimited shop, a group page background and contacts."),
                    GuideText("Попробуйте любой тариф 3 дня бесплатно — один раз на аккаунт.", "Try either plan free for 3 days — once per account."),
                    GuideText("Сменить тариф или срок и отменить продление — там же.", "Switch plan or term and cancel renewal in the same place."),
                ),
                tip = GuideText("Когда подписка заканчивается, ничего не пропадает: созданное остаётся, нельзя только добавить новое сверх бесплатного.", "When a subscription ends, nothing is lost: what you made stays; you just can’t add more beyond the free limits."),
                action = GuideAction.Premium,
            ),
            GuideTopic(
                "music", GuideText("Моя музыка", "My music"),
                listOf(
                    GuideText("Свои треки (mp3, m4a, flac) и плейлисты: создать, переименовать, поменять порядок.", "Your own tracks (mp3, m4a, flac) and playlists: create, rename, reorder."),
                    GuideText("Плеер — внизу экрана.", "The player sits at the bottom of the screen."),
                ),
                tip = GuideText("Музыка хранится только на телефоне и видна только вам.", "Music stays on your phone and only you can see it."),
                action = GuideAction.Music,
            ),
        ),
    )

    private fun chapter9() = GuideChapter(
        "privacy", "🛡",
        GuideText("Приватность и безопасность", "Privacy and safety"),
        GuideText("Кто что видит, жалобы, блокировка, пароль", "Who sees what, reports, blocking, password"),
        listOf(
            GuideTopic(
                "who", GuideText("Кто что видит", "Who sees what"),
                listOf(
                    GuideText("Настройки → «Приватность»: кто видит сведения и друзей, кто может писать и комментировать.", "Settings → “Privacy”: who sees your details and friends, who can message and comment."),
                    GuideText("Там же — «в сети», день рождения и закрытый профиль.", "Also: online status, birthday and a private profile."),
                    GuideText("Закрытый профиль: записи и чек-ины видят только друзья.", "Private profile: only friends see your posts and check-ins."),
                ),
                action = GuideAction.Privacy,
            ),
            GuideTopic(
                "recs", GuideText("Персональные рекомендации", "Personal recommendations"),
                listOf(
                    GuideText("Советы «Для вас» строятся по вашим оценкам.", "“For you” tips are based on your ratings."),
                    GuideText("Их можно выключить: «Приватность» → «Персональные рекомендации».", "Turn them off in “Privacy” → “Personal recommendations”."),
                ),
                action = GuideAction.Privacy,
            ),
            GuideTopic(
                "report", GuideText("Пожаловаться", "Reporting"),
                listOf(
                    GuideText("«Пожаловаться» есть у записи, комментария, ролика, сообщения и человека.", "“Report” is available on posts, comments, videos, messages and people."),
                    GuideText("Выберите причину и при желании добавьте пару слов и блокировку.", "Pick a reason, and optionally add a note and block them."),
                    GuideText("У вас это сразу скрывается, жалоба уходит модераторам.", "It’s hidden for you right away, and the report goes to moderators."),
                ),
            ),
            GuideTopic(
                "block", GuideText("Чёрный список", "Blocking"),
                listOf(
                    GuideText("«⋯» на странице человека → «Заблокировать».", "“⋯” on a person’s page → “Block”."),
                    GuideText("Его записи, комментарии и сообщения больше не видны, писать ему нельзя.", "Their posts, comments and messages disappear, and you can’t message each other."),
                    GuideText("Разблокировать — в настройках, «Чёрный список».", "Unblock in Settings → “Blocked”."),
                ),
                action = GuideAction.Blacklist,
            ),
            GuideTopic(
                "password", GuideText("Пароль и удаление аккаунта", "Password and deleting your account"),
                listOf(
                    GuideText("Настройки → «Безопасность»: сменить пароль.", "Settings → “Security”: change your password."),
                    GuideText("Там же — удалить аккаунт: всё ваше будет стёрто с телефона без возврата.", "Also there: delete your account — everything of yours is erased from the phone for good."),
                ),
                action = GuideAction.Security,
            ),
            GuideTopic(
                "docs", GuideText("Документы", "Documents"),
                listOf(
                    GuideText("Соглашение, политика конфиденциальности, согласие на обработку, правила сообщества — в настройках, «Документы».", "Terms, privacy policy, data consent and community rules are in Settings → “Documents”."),
                    GuideText("Когда документы меняются, Memory попросит принять новую версию.", "When documents change, Memory asks you to accept the new version."),
                ),
                action = GuideAction.Legal,
            ),
        ),
    )

    private fun chapter10() = GuideChapter(
        "settings", "⚙",
        GuideText("Настройки", "Settings"),
        GuideText("Уведомления, звуки, язык, текст", "Notifications, sounds, language, text"),
        listOf(
            GuideTopic(
                "open", GuideText("Где настройки", "Where settings are"),
                listOf(
                    GuideText("Профиль → шестерёнка вверху.", "Profile → the gear at the top."),
                ),
                action = GuideAction.Settings,
            ),
            GuideTopic(
                "notify", GuideText("Уведомления и звуки", "Notifications and sounds"),
                listOf(
                    GuideText("Включайте и выключайте: сообщения, PRISM друзей, «нравится», группы, друзья, итоги.", "Toggle messages, friends’ PRISM, likes, groups, friends and recaps."),
                    GuideText("У каждого вида — свой звук: кристалл, звёздная пыль, комета, луна, призма…", "Each kind has its own sound: crystal, stardust, comet, moon, prism…"),
                ),
                action = GuideAction.Sounds,
            ),
            GuideTopic(
                "text", GuideText("Язык и размер текста", "Language and text size"),
                listOf(
                    GuideText("Язык: как в системе, русский или English — меняется сразу.", "Language: system, Russian or English — it switches instantly."),
                    GuideText("Размер текста — от мелкого до огромного.", "Text size — from small to huge."),
                ),
                tip = GuideText("Язык и размер текста хранятся на телефоне, остальное — в аккаунте.", "Language and text size are stored on the phone; everything else belongs to the account."),
                action = GuideAction.Language,
            ),
            GuideTopic(
                "ideas", GuideText("Идеи и ошибки", "Ideas and bugs"),
                listOf(
                    GuideText("Настройки → «Друзья и идеи»: «Предложить идею» или «Сообщить об ошибке».", "Settings → “Friends & ideas”: “Suggest an idea” or “Report a bug”."),
                ),
                action = GuideAction.Growth,
            ),
            GuideTopic(
                "logout", GuideText("Выйти", "Signing out"),
                listOf(
                    GuideText("Последняя строка настроек. Записи и переписка останутся на телефоне и вернутся, когда вы войдёте снова.", "The last row in settings. Your posts and chats stay on the phone and come back when you sign in again."),
                ),
            ),
        ),
    )

    private fun chapter11() = GuideChapter(
        "faq", "❓",
        GuideText("Частые вопросы", "FAQ"),
        GuideText("Короткие ответы", "Short answers"),
        listOf(
            GuideTopic(
                "where-data", GuideText("Где хранятся мои записи?", "Where are my posts stored?"),
                listOf(
                    GuideText("Пока на вашем телефоне — отдельно для каждого аккаунта. Они переживают перезапуск приложения.", "For now, on your phone — separately for each account. They survive app restarts."),
                ),
            ),
            GuideTopic(
                "forgot", GuideText("Я забыл пароль", "I forgot my password"),
                listOf(
                    GuideText("Пароль хранится на телефоне только в зашифрованном виде, восстановить его нельзя. Создайте новый аккаунт.", "The password is stored on the phone only in encrypted form and can’t be recovered. Create a new account."),
                ),
            ),
            GuideTopic(
                "no-posts", GuideText("Почему я не вижу записи человека?", "Why can’t I see someone’s posts?"),
                listOf(
                    GuideText("У него закрытый профиль — добавьте его в друзья.", "Their profile is private — add them as a friend."),
                    GuideText("Или он в вашем чёрном списке — проверьте настройки.", "Or they’re on your block list — check your settings."),
                ),
            ),
            GuideTopic(
                "gif-search", GuideText("Не работает поиск гифок", "GIF search doesn’t work"),
                listOf(
                    GuideText("Поиску нужен интернет. Встроенные гифки, недавние и свои из галереи работают и без него.", "Search needs the internet. Built-in, recent and gallery GIFs work offline."),
                ),
            ),
            GuideTopic(
                "pay", GuideText("Как оплатить товар из витрины?", "How do I pay for a product?"),
                listOf(
                    GuideText("Внутри Memory оплаты нет. Напишите продавцу или купите на его сайте.", "There is no payment inside Memory. Message the seller or buy on their website."),
                ),
            ),
            GuideTopic(
                "plus-free", GuideText("Что останется бесплатным?", "What stays free?"),
                listOf(
                    GuideText("Всё общение и безопасность: лента, истории, PRISM, чаты, голосовые, друзья, группы, покупки в витринах, приватность, жалобы и удаление аккаунта.", "All communication and safety: feed, stories, PRISM, chats, voice notes, friends, groups, buying from shops, privacy, reports and account deletion."),
                ),
                action = GuideAction.Premium,
            ),
            GuideTopic(
                "guide-again", GuideText("Как снова найти этот гид?", "How do I find this guide again?"),
                listOf(
                    GuideText("Профиль → шестерёнка → «Гид по Memory».", "Profile → gear → “Memory guide”."),
                ),
            ),
        ),
    )
}
