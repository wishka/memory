// Сгенерировано scripts/sync_guide.py из docs/guide/guide.json — не править руками.
import Foundation

/// Главы гида. Текст — в docs/guide/guide.json.
enum GuideContent {
    static let chapters: [GuideChapter] = [chapter0(), chapter1(), chapter2(), chapter3(), chapter4(), chapter5(), chapter6(), chapter7(), chapter8(), chapter9(), chapter10(), chapter11()]

    private static func chapter0() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "signup", title: GuideText(ru: "Регистрация и вход", en: "Signing up and in"),
            steps: [
                GuideText(ru: "Придумайте логин и пароль — от 8 символов.", en: "Pick a username and a password of 8 characters or more."),
                GuideText(ru: "Отметьте три галочки: вам есть 16 лет, вы принимаете соглашение и правила, вы согласны на обработку данных.", en: "Tick three boxes: you are 16 or older, you accept the terms and rules, and you consent to data processing."),
                GuideText(ru: "Memory запомнит вас — пароль понадобится, только если вы выйдете.", en: "Memory remembers you — you only need the password again after signing out."),
                GuideText(ru: "После пяти неверных попыток вход закрывается на 30 секунд.", en: "After five wrong attempts, signing in is paused for 30 seconds."),
            ],
            tip: GuideText(ru: "Хотите сначала осмотреться — войдите в демо: логин demo, пароль memory2026.", en: "Want to look around first? Use the demo: username demo, password memory2026."), action: nil
        ))
        topics.append(GuideTopic(
            id: "taste", title: GuideText(ru: "Знакомство до регистрации", en: "A taste before signing up"),
            steps: [
                GuideText(ru: "На первом экране выберите три любимых фильма или книги.", en: "On the first screen, pick three favourite films or books."),
                GuideText(ru: "Сразу появится ваша полка и первый совет от людей с похожим вкусом.", en: "Your shelf appears right away, with a first tip from people with similar taste."),
                GuideText(ru: "«Сохранить мою полку» ведёт к регистрации — выбранное перейдёт в новый аккаунт.", en: "“Save my shelf” takes you to sign-up, and your picks move into the new account."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "tabs", title: GuideText(ru: "Семь вкладок", en: "The seven tabs"),
            steps: [
                GuideText(ru: "Лента — записи людей: тексты, фото, чек-ины, опросы.", en: "Feed — people’s posts: text, photos, check-ins, polls."),
                GuideText(ru: "Истории — ваши записи и записи друзей.", en: "Stories — your posts and your friends’."),
                GuideText(ru: "PRISM — короткие вертикальные ролики во весь экран.", en: "PRISM — short full-screen vertical videos."),
                GuideText(ru: "Чаты — переписка, голосовые, вложения.", en: "Chats — messages, voice notes, attachments."),
                GuideText(ru: "Группы — сообщества по интересам и витрины магазинов.", en: "Groups — communities around interests, and shop showcases."),
                GuideText(ru: "Вместе — общие цели, обмен навыками, клады и вылазки.", en: "Together — shared goals, skill swaps, caches and outings."),
                GuideText(ru: "Профиль — ваша страница, полка, студия стикеров и настройки.", en: "Profile — your page, shelf, sticker studio and settings."),
            ],
            tip: GuideText(ru: "Подпись видна у выбранной вкладки; на остальных — только значок.", en: "Only the selected tab shows its name; the others show just an icon."), action: nil
        ))
        topics.append(GuideTopic(
            id: "back", title: GuideText(ru: "Как вернуться назад", en: "Going back"),
            steps: [
                GuideText(ru: "Жест или кнопка «назад» закрывает то, что открыто последним: фото, обсуждение, переписку, страницу человека, группу.", en: "The back gesture or button closes whatever opened last: a photo, comments, a chat, a person’s page, a group."),
                GuideText(ru: "Со всех вкладок «назад» ведёт в Ленту, из Ленты — выход из приложения.", en: "From any tab, back leads to the Feed; from the Feed it leaves the app."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "nudges", title: GuideText(ru: "Уведомления о людях", en: "Notifications about people"),
            steps: [
                GuideText(ru: "Memory пишет о важном: подруга отметила фильм, который вы обсуждали; в группе новый участник; готовы итоги месяца.", en: "Memory tells you what matters: a friend checked in to a film you discussed, a new member joined your group, your monthly recap is ready."),
                GuideText(ru: "Пока приложение открыто — плашка сверху, нажатие ведёт к событию.", en: "While the app is open, a banner slides in at the top; tap it to go there."),
                GuideText(ru: "Что присылать и с каким звуком — в настройках уведомлений и звуков.", en: "Choose what to receive and which sound to play in notification and sound settings."),
            ],
            tip: nil, action: .notifications
        ))
        return GuideChapter(id: "start", emoji: "🚀",
                            title: GuideText(ru: "Первые шаги", en: "First steps"),
                            lead: GuideText(ru: "Вход, вкладки и как здесь всё устроено", en: "Signing in, tabs and how things work"),
                            topics: topics)
    }

    private static func chapter1() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "post", title: GuideText(ru: "Новая запись", en: "A new post"),
            steps: [
                GuideText(ru: "Нажмите на поле вверху Ленты и напишите текст.", en: "Tap the field at the top of the Feed and write something."),
                GuideText(ru: "Добавьте фото или видео — из галереи или с камеры, до 10 на запись.", en: "Add photos or videos from the gallery or camera, up to 10 per post."),
                GuideText(ru: "Можно приложить свой стикер, гифку, музыку, опрос или чек-ин.", en: "You can attach your own sticker, a GIF, music, a poll or a check-in."),
                GuideText(ru: "Нажмите «Опубликовать».", en: "Tap “Post”."),
            ],
            tip: nil, action: .feed
        ))
        topics.append(GuideTopic(
            id: "scenarios", title: GuideText(ru: "Сценарии: фильм, пробежка, книга", en: "Shortcuts: film, run, book"),
            steps: [
                GuideText(ru: "Над полем записи — «Посмотрел фильм», «Пробежка», «Книга», «Я здесь».", en: "Above the post field: “Watched a movie”, “Run”, “Book”, “I’m here”."),
                GuideText(ru: "Сценарий сразу открывает чек-ин с нужными полями.", en: "A shortcut opens a check-in with the right fields."),
                GuideText(ru: "После публикации видно, что изменилось: полка, вклад в общие цели, план недели.", en: "After posting you see what changed: your shelf, your share in shared goals, your weekly plan."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "checkin", title: GuideText(ru: "Чек-ины", en: "Check-ins"),
            steps: [
                GuideText(ru: "20 занятий: фильм, книга, бег, йога, кофе, концерт, путешествие, «я здесь», настроение и другие.", en: "20 activities: film, book, running, yoga, coffee, concert, travel, “I’m here”, mood and more."),
                GuideText(ru: "Укажите, что именно и сколько: километры, минуты, страницы.", en: "Say what exactly and how much: kilometres, minutes, pages."),
                GuideText(ru: "Фильмам и книгам можно поставить оценку 1–5, книге — статус: хочу, читаю, прочитал.", en: "Rate films and books 1–5; give a book a status: want to read, reading, finished."),
                GuideText(ru: "Для пробежки укажите время — Memory посчитает темп.", en: "For a run, enter the time and Memory works out your pace."),
                GuideText(ru: "Своё занятие: название, эмодзи и единица. Долгое нажатие на своё — удалить.", en: "Your own activity: a name, an emoji and a unit. Long-press your own to delete it."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "poll", title: GuideText(ru: "Опросы", en: "Polls"),
            steps: [
                GuideText(ru: "От 2 до 10 вариантов, один или несколько ответов.", en: "2 to 10 options, single or multiple choice."),
                GuideText(ru: "Голос можно поменять, пока опрос открыт.", en: "You can change your vote while the poll is open."),
                GuideText(ru: "Автор завершает опрос — итоги видны всем.", en: "The author closes the poll and everyone sees the results."),
            ],
            tip: GuideText(ru: "Опросы работают в Ленте, в группах и в переписке.", en: "Polls work in the Feed, in groups and in chats."), action: nil
        ))
        topics.append(GuideTopic(
            id: "gif", title: GuideText(ru: "Гифки", en: "GIFs"),
            steps: [
                GuideText(ru: "Встроенный набор Memory, недавние и свои из галереи.", en: "Memory’s built-in set, your recent ones, and your own from the gallery."),
                GuideText(ru: "Поиск по всем гифкам GIPHY — по слову.", en: "Search all of GIPHY by keyword."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "like", title: GuideText(ru: "«Нравится» и комментарии", en: "Likes and comments"),
            steps: [
                GuideText(ru: "Сердечко под записью — «нравится»: вспышка искры и кристальное сердце.", en: "The heart under a post is a like — a spark and a crystal heart."),
                GuideText(ru: "Нажмите на комментарии, чтобы открыть обсуждение.", en: "Tap the comment count to open the discussion."),
                GuideText(ru: "Долгое нажатие на комментарий — ответить, скопировать, открыть автора, пожаловаться или удалить свой.", en: "Long-press a comment to reply, copy, open the author, report it, or delete your own."),
                GuideText(ru: "Ответить можно своим стикером — кнопка слева от поля.", en: "Reply with your own sticker using the button left of the field."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "photos", title: GuideText(ru: "Фото и видео на весь экран", en: "Full-screen photos and videos"),
            steps: [
                GuideText(ru: "Нажмите на фото или видео — откроется на весь экран.", en: "Tap a photo or video to open it full screen."),
                GuideText(ru: "Двумя пальцами — увеличить, двойное касание — увеличить в этой точке.", en: "Pinch to zoom; double-tap to zoom in at that spot."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "pins", title: GuideText(ru: "Закладки и витрина профиля", en: "Saved posts and profile showcase"),
            steps: [
                GuideText(ru: "Закладка сохраняет запись в «Сохранённое» в профиле.", en: "Bookmark a post to keep it in “Saved” on your profile."),
                GuideText(ru: "«⋯» на своей записи — «Закрепить в профиле»: до трёх записей встают на витрину.", en: "“⋯” on your own post → “Pin to profile”: up to three posts go on your showcase."),
            ],
            tip: nil, action: .saved
        ))
        topics.append(GuideTopic(
            id: "memory", title: GuideText(ru: "«В этот день»", en: "“On this day”"),
            steps: [
                GuideText(ru: "В Ленте появляется ваша запись год, месяц или неделю назад.", en: "The Feed shows your post from a year, a month or a week ago."),
            ],
            tip: nil, action: nil
        ))
        return GuideChapter(id: "feed", emoji: "📰",
                            title: GuideText(ru: "Лента и записи", en: "Feed and posts"),
                            lead: GuideText(ru: "Публикации, чек-ины, опросы, комментарии", en: "Posts, check-ins, polls, comments"),
                            topics: topics)
    }

    private static func chapter2() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "view", title: GuideText(ru: "Что здесь", en: "What’s here"),
            steps: [
                GuideText(ru: "Ваши записи и записи друзей — без чужих людей.", en: "Your posts and your friends’ — no strangers."),
                GuideText(ru: "Сверху — друзья в кольцах: нажатие показывает записи одного человека, долгое нажатие — его страницу.", en: "Friends in rings at the top: tap to see one person’s posts, long-press for their page."),
            ],
            tip: nil, action: .stories
        ))
        topics.append(GuideTopic(
            id: "new", title: GuideText(ru: "Новая история", en: "A new story"),
            steps: [
                GuideText(ru: "Поле вверху — то же, что в Ленте: фото, стикеры, чек-ины.", en: "The field at the top works like the Feed’s: photos, stickers, check-ins."),
            ],
            tip: nil, action: nil
        ))
        return GuideChapter(id: "stories", emoji: "🌙",
                            title: GuideText(ru: "Истории", en: "Stories"),
                            lead: GuideText(ru: "Записи друзей — отдельно от общей ленты", en: "Friends’ posts, apart from the main feed"),
                            topics: topics)
    }

    private static func chapter3() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "watch", title: GuideText(ru: "Смотреть", en: "Watching"),
            steps: [
                GuideText(ru: "Листайте вверх и вниз — ролики во весь экран.", en: "Swipe up and down through full-screen videos."),
                GuideText(ru: "Нажатие — пауза, двойное нажатие — «нравится».", en: "Tap to pause, double-tap to like."),
                GuideText(ru: "Справа — «нравится», комментарии, «Поделиться» и «Сохранить».", en: "On the right: like, comments, “Share” and “Save”."),
            ],
            tip: nil, action: .prism
        ))
        topics.append(GuideTopic(
            id: "shoot", title: GuideText(ru: "Снять свой ролик", en: "Shooting your own"),
            steps: [
                GuideText(ru: "Нажмите «+» вверху справа.", en: "Tap “+” in the top right."),
                GuideText(ru: "Камера: передняя или задняя, вспышка, таймер 3 или 10 секунд, длина 15, 30 или 60 секунд.", en: "Camera: front or back, flash, a 3 or 10 second timer, 15, 30 or 60 seconds long."),
                GuideText(ru: "Запись можно ставить на паузу и продолжать.", en: "You can pause recording and carry on."),
                GuideText(ru: "Или возьмите видео из галереи.", en: "Or pick a video from your gallery."),
            ],
            tip: GuideText(ru: "Выберите музыку до съёмки — она будет звучать во время записи и сразу встанет в редактор.", en: "Choose music before shooting — it plays while you record and is already in the editor."), action: .newPrism
        ))
        topics.append(GuideTopic(
            id: "edit", title: GuideText(ru: "Редактор", en: "The editor"),
            steps: [
                GuideText(ru: "Обрезка до минуты и скорость от 0.5× до 3×.", en: "Trim to a minute and set speed from 0.5× to 3×."),
                GuideText(ru: "Кадр 9:16, 4:5, 1:1 или оригинал; поворот и отражение.", en: "Frame 9:16, 4:5, 1:1 or original; rotate and flip."),
                GuideText(ru: "12 фильтров и коррекция: яркость, контраст, насыщенность, тепло.", en: "12 filters and adjustments: brightness, contrast, saturation, warmth."),
                GuideText(ru: "Текст с цветом, фоном или свечением — двигайте его пальцем.", en: "Text with colour, a background or a glow — drag it with your finger."),
                GuideText(ru: "Стикеры, музыка с выбором начала, громкость видео и музыки, обложка.", en: "Stickers, music with a chosen start point, video and music volume, a cover."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "publish", title: GuideText(ru: "Публикация", en: "Publishing"),
            steps: [
                GuideText(ru: "Добавьте подпись и опубликуйте — ролик соберётся в обычный видеофайл.", en: "Add a caption and publish — the video is rendered into a regular video file."),
                GuideText(ru: "Свой ролик можно удалить — корзина справа.", en: "Delete your own video with the bin on the right."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "library", title: GuideText(ru: "Медиатека: музыка для роликов", en: "Library: music for videos"),
            steps: [
                GuideText(ru: "Вкладка «Медиатека» рядом с «PRISM».", en: "The “Library” tab next to “PRISM”."),
                GuideText(ru: "Открытая музыка с лицензиями Creative Commons: поиск, жанры, прослушивание.", en: "Open music under Creative Commons licences: search, genres, listening."),
                GuideText(ru: "Понравившееся сохраняйте в «Мою музыку», если площадка разрешает скачивание.", en: "Save tracks you like to “My music” where the source allows downloads."),
            ],
            tip: GuideText(ru: "Автор и лицензия трека сами попадают в подпись ролика.", en: "The track’s author and licence are added to your caption automatically."), action: nil
        ))
        return GuideChapter(id: "prism", emoji: "💎",
                            title: GuideText(ru: "PRISM", en: "PRISM"),
                            lead: GuideText(ru: "Короткие ролики: смотреть, снимать, монтировать", en: "Short videos: watch, shoot, edit"),
                            topics: topics)
    }

    private static func chapter4() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "list", title: GuideText(ru: "Список переписок", en: "The chat list"),
            steps: [
                GuideText(ru: "Сверху — поиск, у переписок — счётчик непрочитанных.", en: "Search at the top; unread counts on each chat."),
                GuideText(ru: "Долгое нажатие на переписку — закрепить вверху, без звука, отметить прочитанной, заблокировать, очистить или удалить.", en: "Long-press a chat to pin it, mute it, mark it read, block, clear or delete it."),
            ],
            tip: nil, action: .chats
        ))
        topics.append(GuideTopic(
            id: "attach", title: GuideText(ru: "Вложения", en: "Attachments"),
            steps: [
                GuideText(ru: "Скрепка — фото, видео, документы, свои стикеры, гифки, опрос и место на карте.", en: "The paperclip: photos, videos, documents, your stickers, GIFs, a poll and a location."),
                GuideText(ru: "Место приходит маленькой картой; нажатие — карта на весь экран и «Открыть в Картах».", en: "A location arrives as a small map; tap it for a full map and “Open in Maps”."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "voice", title: GuideText(ru: "Голосовые", en: "Voice notes"),
            steps: [
                GuideText(ru: "Когда поле пустое, вместо кнопки отправки — микрофон.", en: "When the field is empty, the send button becomes a microphone."),
                GuideText(ru: "Во время записи видно время и волну; можно удалить или отправить. До 5 минут.", en: "While recording you see the time and a waveform; delete or send. Up to 5 minutes."),
                GuideText(ru: "Нажатие на волну в сообщении перематывает.", en: "Tap the waveform in a message to seek."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "message", title: GuideText(ru: "Действия с сообщением", en: "Message actions"),
            steps: [
                GuideText(ru: "Долгое нажатие на сообщение — ответить, переслать, закрепить, удалить у себя или у всех.", en: "Long-press a message to reply, forward, pin, or delete for you or for everyone."),
                GuideText(ru: "Закреплённое видно полоской под шапкой.", en: "A pinned message shows as a strip under the header."),
                GuideText(ru: "Нажатие на имя собеседника — его страница.", en: "Tap the other person’s name to open their page."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "backdrop", title: GuideText(ru: "Фон переписки", en: "Chat background"),
            steps: [
                GuideText(ru: "Общий фон для всех чатов — в настройках, «Фон переписок».", en: "One background for all chats: Settings → “Chat backgrounds”."),
                GuideText(ru: "Свой фон для одной переписки — «⋯» в её шапке.", en: "A background for a single chat: “⋯” in its header."),
                GuideText(ru: "Выбирайте из коллекции — космос, туманность, сияние, кристаллы — или своё фото.", en: "Pick from the collection — space, nebula, aurora, crystals — or your own photo."),
            ],
            tip: nil, action: .wallpaper
        ))
        return GuideChapter(id: "chats", emoji: "💬",
                            title: GuideText(ru: "Чаты", en: "Chats"),
                            lead: GuideText(ru: "Переписка, голосовые, вложения, фон", en: "Messages, voice notes, attachments, backgrounds"),
                            topics: topics)
    }

    private static func chapter5() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "find", title: GuideText(ru: "Найти и вступить", en: "Find and join"),
            steps: [
                GuideText(ru: "Ищите по названию или по теме — почти 40 тем.", en: "Search by name or by topic — nearly 40 topics."),
                GuideText(ru: "Откройте группу и нажмите «Вступить». Выйти можно в её настройках.", en: "Open a group and tap “Join”. You can leave from its settings."),
            ],
            tip: nil, action: .groups
        ))
        topics.append(GuideTopic(
            id: "create", title: GuideText(ru: "Своя группа", en: "Your own group"),
            steps: [
                GuideText(ru: "«+» вверху вкладки «Группы».", en: "Tap “+” at the top of the Groups tab."),
                GuideText(ru: "Название, описание и тема — и «Создать».", en: "Name, description and topic — then “Create”."),
                GuideText(ru: "Шестерёнка на заставке: своя заставка, описание, кто может писать — все или только вы.", en: "The gear on the cover: your own cover, description, who can post — everyone or only you."),
                GuideText(ru: "«⋯» на записи в группе — закрепить её первой.", en: "“⋯” on a group post pins it to the top."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "look", title: GuideText(ru: "Оформление группы", en: "Styling your group"),
            steps: [
                GuideText(ru: "Шестерёнка → «Оформление».", en: "Gear → “Look”."),
                GuideText(ru: "Цвет группы — один из семи: в нём кнопки, цены и вкладки.", en: "A group colour — one of seven — tints buttons, prices and tabs."),
                GuideText(ru: "Фон страницы — цвета, градиенты или своё фото.", en: "A page background — colours, gradients or your own photo."),
            ],
            tip: GuideText(ru: "Так группа-магазин выглядит в своём стиле.", en: "That way a shop group looks like your own brand."), action: nil
        ))
        topics.append(GuideTopic(
            id: "about", title: GuideText(ru: "Карточка «О группе»", en: "The “About” card"),
            steps: [
                GuideText(ru: "Шестерёнка → «О группе»: подробное описание, сайт, телефон, почта, адрес, часы работы.", en: "Gear → “About the group”: a longer description, website, phone, email, address, opening hours."),
                GuideText(ru: "Участники видят карточку над записями: нажатие звонит, пишет или открывает карту.", en: "Members see the card above the posts: tap to call, email or open a map."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "shop-owner", title: GuideText(ru: "Витрина: как продавать", en: "Showcase: selling"),
            steps: [
                GuideText(ru: "В своей группе откройте вкладку «Витрина» и добавьте товар.", en: "In your group, open the “Showcase” tab and add a product."),
                GuideText(ru: "До пяти фото, название, цена, описание, категория, наличие и ссылка на ваш сайт.", en: "Up to five photos, a title, price, description, category, stock and a link to your site."),
                GuideText(ru: "Пустая цена или 0 — «Цена по запросу».", en: "An empty price or 0 shows “Price on request”."),
                GuideText(ru: "В карточке товара — «Изменить», «Убрать», «Выше в витрине» и «Ниже».", en: "In a product card: “Edit”, “Remove”, “Move up” and “Move down”."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "shop-buyer", title: GuideText(ru: "Витрина: как купить", en: "Showcase: buying"),
            steps: [
                GuideText(ru: "Фильтруйте товары по категориям и откройте карточку.", en: "Filter products by category and open a card."),
                GuideText(ru: "«Написать продавцу» — откроется переписка, товар уже приложен.", en: "“Message the seller” opens a chat with the product attached."),
                GuideText(ru: "«Купить на сайте» — если продавец дал ссылку.", en: "“Buy on the website” — if the seller added a link."),
            ],
            tip: GuideText(ru: "Оплаты внутри Memory нет: о цене, оплате и доставке договаривайтесь с продавцом.", en: "There is no payment inside Memory: agree on price, payment and delivery with the seller."), action: nil
        ))
        return GuideChapter(id: "groups", emoji: "🪐",
                            title: GuideText(ru: "Группы и витрины", en: "Groups and shops"),
                            lead: GuideText(ru: "Сообщества, магазины, оформление", en: "Communities, shops, styling"),
                            topics: topics)
    }

    private static func chapter6() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "goals", title: GuideText(ru: "Цели", en: "Goals"),
            steps: [
                GuideText(ru: "Общие шкалы сообщества: «Вместе пробежим 1 000 000 км», «10 000 прочитанных книг».", en: "Shared community counters: “Let’s run 1,000,000 km”, “10,000 books read”."),
                GuideText(ru: "Вступили — и ваши чек-ины засчитываются сами. Вклад можно добавить и вручную.", en: "Join and your check-ins count automatically. You can also add progress by hand."),
            ],
            tip: nil, action: .together
        ))
        topics.append(GuideTopic(
            id: "skills", title: GuideText(ru: "Навыки", en: "Skills"),
            steps: [
                GuideText(ru: "Банк времени без денег: научили кого-то 30 минут — получили 30 минут на любой навык.", en: "A time bank without money: teach someone for 30 minutes, get 30 minutes of any skill."),
                GuideText(ru: "Отметьте, чему научите и чему хотите научиться; первыми идут взаимные обмены.", en: "Mark what you can teach and what you want to learn; mutual swaps come first."),
                GuideText(ru: "Договорённость уходит в чат, «Занятие прошло» двигает баланс.", en: "The arrangement goes to a chat; “Session done” updates the balance."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "journeys", title: GuideText(ru: "Дневники", en: "Journeys"),
            steps: [
                GuideText(ru: "Короткие челленджи по дням: «День 3: учусь жонглировать».", en: "Short day-by-day challenges: “Day 3: learning to juggle”."),
                GuideText(ru: "Выкладывайте процесс — сообщество советует, вы отмечаете полезное.", en: "Share your progress — the community gives tips and you mark the helpful ones."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "missions", title: GuideText(ru: "Миссии и клады", en: "Missions and caches"),
            steps: [
                GuideText(ru: "Миссии — квесты по городу: фото находки встаёт на общую карту там, где вы.", en: "Missions are city quests: your photo goes on a shared map where you are."),
                GuideText(ru: "Клады — спрячьте контейнер с подсказкой. «Нашёл!» можно нажать только в 150 м от клада.", en: "Caches: hide a container with a clue. “Found it!” works only within 150 m of the cache."),
            ],
            tip: GuideText(ru: "Найдите три клада — и сможете прятать свои.", en: "Find three caches and you can hide your own."), action: nil
        ))
        topics.append(GuideTopic(
            id: "outings", title: GuideText(ru: "Вылазки, рисунок, обмен", en: "Outings, drawing, swaps"),
            steps: [
                GuideText(ru: "Вылазки — события до 10 человек: грибы, фотопрогулка, велопробег.", en: "Outings are events for up to 10 people: mushroom picking, photo walks, bike rides."),
                GuideText(ru: "Рисунок — эстафета: каждый дорисовывает своё и передаёт дальше; готовое можно опубликовать.", en: "Drawing is a relay: everyone adds something and passes it on; the result can be published."),
                GuideText(ru: "Обмен — книжный клуб, семена и растения, ремонт вещей.", en: "Swaps: a book club, seeds and plants, repairing things."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "invite", title: GuideText(ru: "Позвать того, кого нет в Memory", en: "Invite someone who isn’t on Memory"),
            steps: [
                GuideText(ru: "В рисунке, обмене, вылазке и навыках есть «Позвать» — уйдёт ссылка-приглашение.", en: "Drawing, swaps, outings and skills have “Invite”, which sends an invite link."),
                GuideText(ru: "Ваш код приглашения — в настройках, «Друзья и идеи».", en: "Your invite code is in Settings → “Friends & ideas”."),
            ],
            tip: nil, action: .growth
        ))
        return GuideChapter(id: "together", emoji: "🤝",
                            title: GuideText(ru: "Вместе", en: "Together"),
                            lead: GuideText(ru: "Делать что-то вместе, а не соревноваться", en: "Doing things together, not competing"),
                            topics: topics)
    }

    private static func chapter7() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "add", title: GuideText(ru: "Добавить в друзья", en: "Adding a friend"),
            steps: [
                GuideText(ru: "Откройте страницу человека — нажмите на имя или аватар.", en: "Open a person’s page by tapping their name or avatar."),
                GuideText(ru: "«Добавить в друзья» отправит заявку; пока её не приняли, видно «Заявка отправлена».", en: "“Add friend” sends a request; until it’s accepted you see “Request sent”."),
                GuideText(ru: "Когда человек примет заявку, вы станете друзьями взаимно.", en: "When they accept, you become friends both ways."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "requests", title: GuideText(ru: "Заявки в друзья", en: "Friend requests"),
            steps: [
                GuideText(ru: "Входящие — в профиле, «Заявки в друзья»: принять или отклонить.", en: "Incoming requests are in Profile → “Friend requests”: accept or decline."),
                GuideText(ru: "Там же — заявки, которые отправили вы.", en: "Requests you’ve sent are there too."),
            ],
            tip: nil, action: .requests
        ))
        topics.append(GuideTopic(
            id: "remove", title: GuideText(ru: "Убрать из друзей", en: "Removing a friend"),
            steps: [
                GuideText(ru: "В списке друзей или на странице человека — «Удалить из друзей», с подтверждением.", en: "In your friends list or on their page: “Remove from friends”, with a confirmation."),
            ],
            tip: nil, action: .friends
        ))
        topics.append(GuideTopic(
            id: "why", title: GuideText(ru: "Зачем друзья", en: "What friends see"),
            steps: [
                GuideText(ru: "Записи друзей — во вкладке «Истории».", en: "Friends’ posts appear in Stories."),
                GuideText(ru: "Закрытый профиль видят только друзья.", en: "Only friends can see a private profile."),
            ],
            tip: nil, action: nil
        ))
        return GuideChapter(id: "friends", emoji: "✨",
                            title: GuideText(ru: "Друзья", en: "Friends"),
                            lead: GuideText(ru: "Дружба взаимная — по заявке", en: "Friendship is mutual — by request"),
                            topics: topics)
    }

    private static func chapter8() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "edit", title: GuideText(ru: "Ваша страница", en: "Your page"),
            steps: [
                GuideText(ru: "Аватар — фото из галереи или с камеры, с обрезкой по кругу.", en: "Avatar: a photo from the gallery or camera, cropped to a circle."),
                GuideText(ru: "«О себе», город, день рождения, сайт и интересы.", en: "“About me”, city, birthday, website and interests."),
                GuideText(ru: "Счётчики записей, друзей и групп открывают свои списки.", en: "The post, friend and group counters open their lists."),
            ],
            tip: nil, action: .profile
        ))
        topics.append(GuideTopic(
            id: "home", title: GuideText(ru: "Главная профиля", en: "Profile home"),
            steps: [
                GuideText(ru: "Витрина: до трёх закреплённых записей, ваши стикеры и музыка.", en: "Showcase: up to three pinned posts, your stickers and music."),
                GuideText(ru: "Активность за две недели и кристаллы-достижения.", en: "Two weeks of activity and crystal achievements."),
                GuideText(ru: "Вкладки «Записи · PRISM · Медиа», ваши группы и друзья.", en: "Tabs “Posts · PRISM · Media”, your groups and friends."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "shelf", title: GuideText(ru: "Полка", en: "Shelf"),
            steps: [
                GuideText(ru: "Собирается из чек-инов сама: фильмы с оценками, книги по статусам, спорт, места.", en: "Built from your check-ins: rated films, books by status, sport, places."),
                GuideText(ru: "«Для вас» — советы от людей с похожим вкусом и план бега на неделю.", en: "“For you”: tips from people with similar taste and a weekly running plan."),
                GuideText(ru: "«Права» — что вы уже открыли: свои клады, миссии, цели, звание наставника.", en: "“Abilities”: what you’ve earned — your own caches, missions, goals, the mentor title."),
            ],
            tip: nil, action: .shelf
        ))
        topics.append(GuideTopic(
            id: "recap", title: GuideText(ru: "Итоги месяца и года", en: "Monthly and yearly recaps"),
            steps: [
                GuideText(ru: "Картинка на звёздном небе: фильмы, книги, километры, места, записи, «нравится».", en: "A picture on a starry sky: films, books, kilometres, places, posts, likes."),
                GuideText(ru: "Стрелки листают прошлые периоды.", en: "Arrows page through earlier periods."),
                GuideText(ru: "Опубликуйте в Ленту или Истории, отправьте в чат или сохраните в галерею.", en: "Post it to the Feed or Stories, send it to a chat or save it to your gallery."),
            ],
            tip: nil, action: .recap
        ))
        topics.append(GuideTopic(
            id: "studio", title: GuideText(ru: "Студия стикеров", en: "Sticker studio"),
            steps: [
                GuideText(ru: "Из фото — с формой (круг, сердце, кристалл, звезда), обводкой и подписью.", en: "From a photo, with a shape (circle, heart, crystal, star), outline and caption."),
                GuideText(ru: "Вырезка — объект с фото вырезается сам.", en: "Cut-out: the subject is lifted out of the photo automatically."),
                GuideText(ru: "Рисунок кистью — и покадровый, до 12 кадров.", en: "Draw with a brush, including frame-by-frame animation, up to 12 frames."),
                GuideText(ru: "Конструктор смайлов и значок с текстом или эмодзи.", en: "An emoji face builder and a badge with text or an emoji."),
                GuideText(ru: "Живые стикеры: эффекты (контур, комикс, объём, блеск) и движение (прыжок, пульс, парение…).", en: "Live stickers: effects (outline, comic, 3D, shine) and motion (jump, pulse, float…)."),
            ],
            tip: GuideText(ru: "Собирайте стикеры в коллекции — они станут вкладками при выборе стикера.", en: "Group stickers into collections — they become tabs in the sticker picker."), action: .studio
        ))
        topics.append(GuideTopic(
            id: "plus", title: GuideText(ru: "Memory+ и пробный период", en: "Memory+ and the free trial"),
            steps: [
                GuideText(ru: "Профиль → карточка «Memory+»: тарифы, цены и что входит.", en: "Profile → the “Memory+” card: plans, prices and what’s included."),
                GuideText(ru: "Memory+ — стикеры, фоны, звуки, PRISM до 60 секунд, больше плейлистов и итоги за любой период.", en: "Memory+: stickers, backgrounds, sounds, PRISM up to 60 seconds, more playlists and recaps for any period."),
                GuideText(ru: "Memory+ Бизнес — всё из Memory+ и витрина без ограничений, фон страницы группы и контакты.", en: "Memory+ Business: everything in Memory+, plus an unlimited shop, a group page background and contacts."),
                GuideText(ru: "Попробуйте любой тариф 3 дня бесплатно — один раз на аккаунт.", en: "Try either plan free for 3 days — once per account."),
                GuideText(ru: "Сменить тариф или срок и отменить продление — там же.", en: "Switch plan or term and cancel renewal in the same place."),
            ],
            tip: GuideText(ru: "Когда подписка заканчивается, ничего не пропадает: созданное остаётся, нельзя только добавить новое сверх бесплатного.", en: "When a subscription ends, nothing is lost: what you made stays; you just can’t add more beyond the free limits."), action: .premium
        ))
        topics.append(GuideTopic(
            id: "music", title: GuideText(ru: "Моя музыка", en: "My music"),
            steps: [
                GuideText(ru: "Свои треки (mp3, m4a, flac) и плейлисты: создать, переименовать, поменять порядок.", en: "Your own tracks (mp3, m4a, flac) and playlists: create, rename, reorder."),
                GuideText(ru: "Плеер — внизу экрана.", en: "The player sits at the bottom of the screen."),
            ],
            tip: GuideText(ru: "Музыка хранится только на телефоне и видна только вам.", en: "Music stays on your phone and only you can see it."), action: .music
        ))
        return GuideChapter(id: "profile", emoji: "👤",
                            title: GuideText(ru: "Профиль", en: "Profile"),
                            lead: GuideText(ru: "Страница, полка, итоги, студия и музыка", en: "Your page, shelf, recaps, studio and music"),
                            topics: topics)
    }

    private static func chapter9() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "who", title: GuideText(ru: "Кто что видит", en: "Who sees what"),
            steps: [
                GuideText(ru: "Настройки → «Приватность»: кто видит сведения и друзей, кто может писать и комментировать.", en: "Settings → “Privacy”: who sees your details and friends, who can message and comment."),
                GuideText(ru: "Там же — «в сети», день рождения и закрытый профиль.", en: "Also: online status, birthday and a private profile."),
                GuideText(ru: "Закрытый профиль: записи и чек-ины видят только друзья.", en: "Private profile: only friends see your posts and check-ins."),
            ],
            tip: nil, action: .privacy
        ))
        topics.append(GuideTopic(
            id: "recs", title: GuideText(ru: "Персональные рекомендации", en: "Personal recommendations"),
            steps: [
                GuideText(ru: "Советы «Для вас» строятся по вашим оценкам.", en: "“For you” tips are based on your ratings."),
                GuideText(ru: "Их можно выключить: «Приватность» → «Персональные рекомендации».", en: "Turn them off in “Privacy” → “Personal recommendations”."),
            ],
            tip: nil, action: .privacy
        ))
        topics.append(GuideTopic(
            id: "report", title: GuideText(ru: "Пожаловаться", en: "Reporting"),
            steps: [
                GuideText(ru: "«Пожаловаться» есть у записи, комментария, ролика, сообщения и человека.", en: "“Report” is available on posts, comments, videos, messages and people."),
                GuideText(ru: "Выберите причину и при желании добавьте пару слов и блокировку.", en: "Pick a reason, and optionally add a note and block them."),
                GuideText(ru: "У вас это сразу скрывается, жалоба уходит модераторам.", en: "It’s hidden for you right away, and the report goes to moderators."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "block", title: GuideText(ru: "Чёрный список", en: "Blocking"),
            steps: [
                GuideText(ru: "«⋯» на странице человека → «Заблокировать».", en: "“⋯” on a person’s page → “Block”."),
                GuideText(ru: "Его записи, комментарии и сообщения больше не видны, писать ему нельзя.", en: "Their posts, comments and messages disappear, and you can’t message each other."),
                GuideText(ru: "Разблокировать — в настройках, «Чёрный список».", en: "Unblock in Settings → “Blocked”."),
            ],
            tip: nil, action: .blacklist
        ))
        topics.append(GuideTopic(
            id: "password", title: GuideText(ru: "Пароль и удаление аккаунта", en: "Password and deleting your account"),
            steps: [
                GuideText(ru: "Настройки → «Безопасность»: сменить пароль.", en: "Settings → “Security”: change your password."),
                GuideText(ru: "Там же — удалить аккаунт: всё ваше будет стёрто с телефона без возврата.", en: "Also there: delete your account — everything of yours is erased from the phone for good."),
            ],
            tip: nil, action: .security
        ))
        topics.append(GuideTopic(
            id: "docs", title: GuideText(ru: "Документы", en: "Documents"),
            steps: [
                GuideText(ru: "Соглашение, политика конфиденциальности, согласие на обработку, правила сообщества — в настройках, «Документы».", en: "Terms, privacy policy, data consent and community rules are in Settings → “Documents”."),
                GuideText(ru: "Когда документы меняются, Memory попросит принять новую версию.", en: "When documents change, Memory asks you to accept the new version."),
            ],
            tip: nil, action: .legal
        ))
        return GuideChapter(id: "privacy", emoji: "🛡",
                            title: GuideText(ru: "Приватность и безопасность", en: "Privacy and safety"),
                            lead: GuideText(ru: "Кто что видит, жалобы, блокировка, пароль", en: "Who sees what, reports, blocking, password"),
                            topics: topics)
    }

    private static func chapter10() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "open", title: GuideText(ru: "Где настройки", en: "Where settings are"),
            steps: [
                GuideText(ru: "Профиль → шестерёнка вверху.", en: "Profile → the gear at the top."),
            ],
            tip: nil, action: .settings
        ))
        topics.append(GuideTopic(
            id: "notify", title: GuideText(ru: "Уведомления и звуки", en: "Notifications and sounds"),
            steps: [
                GuideText(ru: "Включайте и выключайте: сообщения, PRISM друзей, «нравится», группы, друзья, итоги.", en: "Toggle messages, friends’ PRISM, likes, groups, friends and recaps."),
                GuideText(ru: "У каждого вида — свой звук: кристалл, звёздная пыль, комета, луна, призма…", en: "Each kind has its own sound: crystal, stardust, comet, moon, prism…"),
            ],
            tip: nil, action: .sounds
        ))
        topics.append(GuideTopic(
            id: "text", title: GuideText(ru: "Язык и размер текста", en: "Language and text size"),
            steps: [
                GuideText(ru: "Язык: как в системе, русский или English — меняется сразу.", en: "Language: system, Russian or English — it switches instantly."),
                GuideText(ru: "Размер текста — от мелкого до огромного.", en: "Text size — from small to huge."),
            ],
            tip: GuideText(ru: "Язык и размер текста хранятся на телефоне, остальное — в аккаунте.", en: "Language and text size are stored on the phone; everything else belongs to the account."), action: .language
        ))
        topics.append(GuideTopic(
            id: "ideas", title: GuideText(ru: "Идеи и ошибки", en: "Ideas and bugs"),
            steps: [
                GuideText(ru: "Настройки → «Друзья и идеи»: «Предложить идею» или «Сообщить об ошибке».", en: "Settings → “Friends & ideas”: “Suggest an idea” or “Report a bug”."),
            ],
            tip: nil, action: .growth
        ))
        topics.append(GuideTopic(
            id: "logout", title: GuideText(ru: "Выйти", en: "Signing out"),
            steps: [
                GuideText(ru: "Последняя строка настроек. Записи и переписка останутся на телефоне и вернутся, когда вы войдёте снова.", en: "The last row in settings. Your posts and chats stay on the phone and come back when you sign in again."),
            ],
            tip: nil, action: nil
        ))
        return GuideChapter(id: "settings", emoji: "⚙",
                            title: GuideText(ru: "Настройки", en: "Settings"),
                            lead: GuideText(ru: "Уведомления, звуки, язык, текст", en: "Notifications, sounds, language, text"),
                            topics: topics)
    }

    private static func chapter11() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "where-data", title: GuideText(ru: "Где хранятся мои записи?", en: "Where are my posts stored?"),
            steps: [
                GuideText(ru: "Пока на вашем телефоне — отдельно для каждого аккаунта. Они переживают перезапуск приложения.", en: "For now, on your phone — separately for each account. They survive app restarts."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "forgot", title: GuideText(ru: "Я забыл пароль", en: "I forgot my password"),
            steps: [
                GuideText(ru: "Пароль хранится на телефоне только в зашифрованном виде, восстановить его нельзя. Создайте новый аккаунт.", en: "The password is stored on the phone only in encrypted form and can’t be recovered. Create a new account."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "no-posts", title: GuideText(ru: "Почему я не вижу записи человека?", en: "Why can’t I see someone’s posts?"),
            steps: [
                GuideText(ru: "У него закрытый профиль — добавьте его в друзья.", en: "Their profile is private — add them as a friend."),
                GuideText(ru: "Или он в вашем чёрном списке — проверьте настройки.", en: "Or they’re on your block list — check your settings."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "gif-search", title: GuideText(ru: "Не работает поиск гифок", en: "GIF search doesn’t work"),
            steps: [
                GuideText(ru: "Поиску нужен интернет. Встроенные гифки, недавние и свои из галереи работают и без него.", en: "Search needs the internet. Built-in, recent and gallery GIFs work offline."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "pay", title: GuideText(ru: "Как оплатить товар из витрины?", en: "How do I pay for a product?"),
            steps: [
                GuideText(ru: "Внутри Memory оплаты нет. Напишите продавцу или купите на его сайте.", en: "There is no payment inside Memory. Message the seller or buy on their website."),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "plus-free", title: GuideText(ru: "Что останется бесплатным?", en: "What stays free?"),
            steps: [
                GuideText(ru: "Всё общение и безопасность: лента, истории, PRISM, чаты, голосовые, друзья, группы, покупки в витринах, приватность, жалобы и удаление аккаунта.", en: "All communication and safety: feed, stories, PRISM, chats, voice notes, friends, groups, buying from shops, privacy, reports and account deletion."),
            ],
            tip: nil, action: .premium
        ))
        topics.append(GuideTopic(
            id: "guide-again", title: GuideText(ru: "Как снова найти этот гид?", en: "How do I find this guide again?"),
            steps: [
                GuideText(ru: "Профиль → шестерёнка → «Гид по Memory».", en: "Profile → gear → “Memory guide”."),
            ],
            tip: nil, action: nil
        ))
        return GuideChapter(id: "faq", emoji: "❓",
                            title: GuideText(ru: "Частые вопросы", en: "FAQ"),
                            lead: GuideText(ru: "Короткие ответы", en: "Short answers"),
                            topics: topics)
    }
}
