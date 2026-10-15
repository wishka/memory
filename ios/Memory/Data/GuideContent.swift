// Сгенерировано scripts/sync_guide.py из docs/guide/guide.json — не править руками.
import Foundation

/// Главы гида. Текст — в docs/guide/guide.json.
enum GuideContent {
    static let chapters: [GuideChapter] = [chapter0(), chapter1(), chapter2(), chapter3(), chapter4(), chapter5(), chapter6(), chapter7(), chapter8(), chapter9(), chapter10(), chapter11()]

    private static func chapter0() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "signup", title: GuideText(ru: "Регистрация и вход", en: "Signing up and in", hy: "Գրանցում և մուտք"),
            steps: [
                GuideText(ru: "Придумайте логин и пароль — от 8 символов.", en: "Pick a username and a password of 8 characters or more.", hy: "Ընտրեք մուտքանուն և գաղտնաբառ՝ առնվազն 8 նիշ։"),
                GuideText(ru: "Отметьте три галочки: вам есть 16 лет, вы принимаете соглашение и правила, вы согласны на обработку данных.", en: "Tick three boxes: you are 16 or older, you accept the terms and rules, and you consent to data processing.", hy: "Նշեք երեք վանդակները՝ դուք 16 տարեկան եք կամ ավելի, ընդունում եք համաձայնագիրը և կանոնները, համաձայն եք տվյալների մշակմանը։"),
                GuideText(ru: "Memory запомнит вас — пароль понадобится, только если вы выйдете.", en: "Memory remembers you — you only need the password again after signing out.", hy: "Memory-ն կհիշի ձեզ․ գաղտնաբառը պետք կգա միայն, եթե դուրս գաք։"),
                GuideText(ru: "После пяти неверных попыток вход закрывается на 30 секунд.", en: "After five wrong attempts, signing in is paused for 30 seconds.", hy: "Հինգ սխալ փորձից հետո մուտքը փակվում է 30 վայրկյանով։"),
            ],
            tip: GuideText(ru: "Хотите сначала осмотреться — войдите в демо: логин demo, пароль memory2026.", en: "Want to look around first? Use the demo: username demo, password memory2026.", hy: "Ուզո՞ւմ եք նախ ծանոթանալ․ մտեք դեմո՝ մուտքանուն demo, գաղտնաբառ memory2026։"), action: nil
        ))
        topics.append(GuideTopic(
            id: "taste", title: GuideText(ru: "Знакомство до регистрации", en: "A taste before signing up", hy: "Ծանոթություն մինչև գրանցումը"),
            steps: [
                GuideText(ru: "На первом экране выберите три любимых фильма или книги.", en: "On the first screen, pick three favourite films or books.", hy: "Առաջին էկրանին ընտրեք երեք սիրելի ֆիլմ կամ գիրք։"),
                GuideText(ru: "Сразу появится ваша полка и первый совет от людей с похожим вкусом.", en: "Your shelf appears right away, with a first tip from people with similar taste.", hy: "Անմիջապես կհայտնվի ձեր դարակը և առաջին խորհուրդը նման ճաշակ ունեցող մարդկանցից։"),
                GuideText(ru: "«Сохранить мою полку» ведёт к регистрации — выбранное перейдёт в новый аккаунт.", en: "“Save my shelf” takes you to sign-up, and your picks move into the new account.", hy: "«Պահպանել իմ դարակը» կոճակը տանում է գրանցման․ ընտրածը կտեղափոխվի նոր հաշիվ։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "tabs", title: GuideText(ru: "Семь вкладок", en: "The seven tabs", hy: "Յոթ ներդիր"),
            steps: [
                GuideText(ru: "Лента — записи людей: тексты, фото, чек-ины, опросы.", en: "Feed — people’s posts: text, photos, check-ins, polls.", hy: "Լրահոս՝ մարդկանց գրառումները՝ տեքստեր, լուսանկարներ, չեք-իններ, հարցումներ։"),
                GuideText(ru: "Истории — ваши записи и записи друзей.", en: "Stories — your posts and your friends’.", hy: "Պատմություններ՝ ձեր և ընկերների գրառումները։"),
                GuideText(ru: "PRISM — короткие вертикальные ролики во весь экран.", en: "PRISM — short full-screen vertical videos.", hy: "PRISM՝ կարճ ուղղահայաց տեսահոլովակներ ամբողջ էկրանով։"),
                GuideText(ru: "Чаты — переписка, голосовые, вложения.", en: "Chats — messages, voice notes, attachments.", hy: "Զրույցներ՝ նամակագրություն, ձայնային հաղորդագրություններ, կցորդներ։"),
                GuideText(ru: "Группы — сообщества по интересам и витрины магазинов.", en: "Groups — communities around interests, and shop showcases.", hy: "Խմբեր՝ ըստ հետաքրքրությունների համայնքներ և խանութների ցուցափեղկեր։"),
                GuideText(ru: "Вместе — общие цели, обмен навыками, клады и вылазки.", en: "Together — shared goals, skill swaps, caches and outings.", hy: "Միասին՝ ընդհանուր նպատակներ, հմտությունների փոխանակում, գանձեր և արշավներ։"),
                GuideText(ru: "Профиль — ваша страница, полка, студия стикеров и настройки.", en: "Profile — your page, shelf, sticker studio and settings.", hy: "Պրոֆիլ՝ ձեր էջը, դարակը, ստիկերների ստուդիան և կարգավորումները։"),
            ],
            tip: GuideText(ru: "Подпись видна у выбранной вкладки; на остальных — только значок.", en: "Only the selected tab shows its name; the others show just an icon.", hy: "Անվանումը երևում է միայն ընտրված ներդիրի մոտ, մյուսների մոտ՝ միայն պատկերակը։"), action: nil
        ))
        topics.append(GuideTopic(
            id: "back", title: GuideText(ru: "Как вернуться назад", en: "Going back", hy: "Ինչպես վերադառնալ"),
            steps: [
                GuideText(ru: "Жест или кнопка «назад» закрывает то, что открыто последним: фото, обсуждение, переписку, страницу человека, группу.", en: "The back gesture or button closes whatever opened last: a photo, comments, a chat, a person’s page, a group.", hy: "«Հետ» ժեստը կամ կոճակը փակում է վերջին բացվածը՝ լուսանկարը, քննարկումը, զրույցը, մարդու էջը, խումբը։"),
                GuideText(ru: "Со всех вкладок «назад» ведёт в Ленту, из Ленты — выход из приложения.", en: "From any tab, back leads to the Feed; from the Feed it leaves the app.", hy: "Բոլոր ներդիրներից «Հետ»-ը տանում է Լրահոս, իսկ Լրահոսից՝ դուրս է գալիս հավելվածից։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "nudges", title: GuideText(ru: "Уведомления о людях", en: "Notifications about people", hy: "Ծանուցումներ մարդկանց մասին"),
            steps: [
                GuideText(ru: "Memory пишет о важном: подруга отметила фильм, который вы обсуждали; в группе новый участник; готовы итоги месяца.", en: "Memory tells you what matters: a friend checked in to a film you discussed, a new member joined your group, your monthly recap is ready.", hy: "Memory-ն տեղեկացնում է կարևորի մասին՝ ընկերուհին նշել է ֆիլմը, որը դուք քննարկել եք, խմբում նոր մասնակից կա, ամսվա ամփոփումը պատրաստ է։"),
                GuideText(ru: "Пока приложение открыто — плашка сверху, нажатие ведёт к событию.", en: "While the app is open, a banner slides in at the top; tap it to go there.", hy: "Երբ հավելվածը բաց է, վերևում հայտնվում է վահանակ․ սեղմելով՝ կանցնեք իրադարձությանը։"),
                GuideText(ru: "Что присылать и с каким звуком — в настройках уведомлений и звуков.", en: "Choose what to receive and which sound to play in notification and sound settings.", hy: "Ինչ ստանալ և ինչ ձայնով՝ ընտրեք ծանուցումների և ձայների կարգավորումներում։"),
            ],
            tip: nil, action: .notifications
        ))
        return GuideChapter(id: "start", emoji: "🚀",
                            title: GuideText(ru: "Первые шаги", en: "First steps", hy: "Առաջին քայլեր"),
                            lead: GuideText(ru: "Вход, вкладки и как здесь всё устроено", en: "Signing in, tabs and how things work", hy: "Մուտք, ներդիրներ և ինչպես է ամեն ինչ աշխատում"),
                            topics: topics)
    }

    private static func chapter1() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "post", title: GuideText(ru: "Новая запись", en: "A new post", hy: "Նոր գրառում"),
            steps: [
                GuideText(ru: "Нажмите на поле вверху Ленты и напишите текст.", en: "Tap the field at the top of the Feed and write something.", hy: "Սեղմեք Լրահոսի վերևի դաշտին և գրեք տեքստը։"),
                GuideText(ru: "Добавьте фото или видео — из галереи или с камеры, до 10 на запись.", en: "Add photos or videos from the gallery or camera, up to 10 per post.", hy: "Ավելացրեք լուսանկար կամ տեսանյութ պատկերասրահից կամ տեսախցիկից՝ մինչև 10 մեկ գրառման մեջ։"),
                GuideText(ru: "Можно приложить свой стикер, гифку, музыку, опрос или чек-ин.", en: "You can attach your own sticker, a GIF, music, a poll or a check-in.", hy: "Կարող եք կցել ձեր ստիկերը, GIF, երաժշտություն, հարցում կամ չեք-ին։"),
                GuideText(ru: "Нажмите «Опубликовать».", en: "Tap “Post”.", hy: "Սեղմեք «Հրապարակել»։"),
            ],
            tip: nil, action: .feed
        ))
        topics.append(GuideTopic(
            id: "scenarios", title: GuideText(ru: "Сценарии: фильм, пробежка, книга", en: "Shortcuts: film, run, book", hy: "Սցենարներ՝ ֆիլմ, վազք, գիրք"),
            steps: [
                GuideText(ru: "Над полем записи — «Посмотрел фильм», «Пробежка», «Книга», «Я здесь».", en: "Above the post field: “Watched a movie”, “Run”, “Book”, “I’m here”.", hy: "Գրառման դաշտի վերևում՝ «Դիտեցի ֆիլմ», «Վազք», «Գիրք», «Ես այստեղ եմ»։"),
                GuideText(ru: "Сценарий сразу открывает чек-ин с нужными полями.", en: "A shortcut opens a check-in with the right fields.", hy: "Սցենարն անմիջապես բացում է չեք-ինը՝ անհրաժեշտ դաշտերով։"),
                GuideText(ru: "После публикации видно, что изменилось: полка, вклад в общие цели, план недели.", en: "After posting you see what changed: your shelf, your share in shared goals, your weekly plan.", hy: "Հրապարակելուց հետո երևում է, թե ինչ է փոխվել՝ դարակը, ներդրումը ընդհանուր նպատակներում, շաբաթվա պլանը։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "checkin", title: GuideText(ru: "Чек-ины", en: "Check-ins", hy: "Չեք-իններ"),
            steps: [
                GuideText(ru: "20 занятий: фильм, книга, бег, йога, кофе, концерт, путешествие, «я здесь», настроение и другие.", en: "20 activities: film, book, running, yoga, coffee, concert, travel, “I’m here”, mood and more.", hy: "20 զբաղմունք՝ ֆիլմ, գիրք, վազք, յոգա, սուրճ, համերգ, ճամփորդություն, «ես այստեղ եմ», տրամադրություն և այլն։"),
                GuideText(ru: "Укажите, что именно и сколько: километры, минуты, страницы.", en: "Say what exactly and how much: kilometres, minutes, pages.", hy: "Նշեք, թե կոնկրետ ինչ և որքան՝ կիլոմետրեր, րոպեներ, էջեր։"),
                GuideText(ru: "Фильмам и книгам можно поставить оценку 1–5, книге — статус: хочу, читаю, прочитал.", en: "Rate films and books 1–5; give a book a status: want to read, reading, finished.", hy: "Ֆիլմերին և գրքերին կարող եք գնահատական տալ 1–5, գրքին՝ կարգավիճակ՝ ուզում եմ կարդալ, կարդում եմ, կարդացել եմ։"),
                GuideText(ru: "Для пробежки укажите время — Memory посчитает темп.", en: "For a run, enter the time and Memory works out your pace.", hy: "Վազքի համար նշեք ժամանակը, և Memory-ն կհաշվի տեմպը։"),
                GuideText(ru: "Своё занятие: название, эмодзи и единица. Долгое нажатие на своё — удалить.", en: "Your own activity: a name, an emoji and a unit. Long-press your own to delete it.", hy: "Ձեր զբաղմունքը՝ անվանում, էմոջի և միավոր։ Ձերը ջնջելու համար՝ երկար սեղմեք։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "poll", title: GuideText(ru: "Опросы", en: "Polls", hy: "Հարցումներ"),
            steps: [
                GuideText(ru: "От 2 до 10 вариантов, один или несколько ответов.", en: "2 to 10 options, single or multiple choice.", hy: "2-ից 10 տարբերակ, մեկ կամ մի քանի պատասխան։"),
                GuideText(ru: "Голос можно поменять, пока опрос открыт.", en: "You can change your vote while the poll is open.", hy: "Ձայնը կարող եք փոխել, քանի դեռ հարցումը բաց է։"),
                GuideText(ru: "Автор завершает опрос — итоги видны всем.", en: "The author closes the poll and everyone sees the results.", hy: "Հեղինակն ավարտում է հարցումը, և արդյունքները տեսանելի են բոլորին։"),
            ],
            tip: GuideText(ru: "Опросы работают в Ленте, в группах и в переписке.", en: "Polls work in the Feed, in groups and in chats.", hy: "Հարցումներն աշխատում են Լրահոսում, խմբերում և զրույցներում։"), action: nil
        ))
        topics.append(GuideTopic(
            id: "gif", title: GuideText(ru: "Гифки", en: "GIFs", hy: "GIF-եր"),
            steps: [
                GuideText(ru: "Встроенный набор Memory, недавние и свои из галереи.", en: "Memory’s built-in set, your recent ones, and your own from the gallery.", hy: "Memory-ի ներկառուցված հավաքածուն, վերջին օգտագործվածները և ձերը՝ պատկերասրահից։"),
                GuideText(ru: "Поиск по всем гифкам GIPHY — по слову.", en: "Search all of GIPHY by keyword.", hy: "Որոնում GIPHY-ի բոլոր GIF-երում՝ ըստ բառի։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "like", title: GuideText(ru: "«Нравится» и комментарии", en: "Likes and comments", hy: "«Հավանել» և մեկնաբանություններ"),
            steps: [
                GuideText(ru: "Сердечко под записью — «нравится»: вспышка искры и кристальное сердце.", en: "The heart under a post is a like — a spark and a crystal heart.", hy: "Գրառման տակի սրտիկը՝ «հավանել»․ կայծի բռնկում և բյուրեղյա սիրտ։"),
                GuideText(ru: "Нажмите на комментарии, чтобы открыть обсуждение.", en: "Tap the comment count to open the discussion.", hy: "Սեղմեք մեկնաբանություններին՝ քննարկումը բացելու համար։"),
                GuideText(ru: "Долгое нажатие на комментарий — ответить, скопировать, открыть автора, пожаловаться или удалить свой.", en: "Long-press a comment to reply, copy, open the author, report it, or delete your own.", hy: "Երկար սեղմեք մեկնաբանության վրա՝ պատասխանելու, պատճենելու, հեղինակին բացելու, բողոքելու կամ ձերը ջնջելու համար։"),
                GuideText(ru: "Ответить можно своим стикером — кнопка слева от поля.", en: "Reply with your own sticker using the button left of the field.", hy: "Կարող եք պատասխանել ձեր ստիկերով՝ դաշտից ձախ գտնվող կոճակով։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "activity", title: GuideText(ru: "Колокольчик: кто оценил и упомянул", en: "The bell: who liked and mentioned you", hy: "Զանգակ․ ով հավանեց և նշեց ձեզ"),
            steps: [
                GuideText(ru: "В «Ленте» вверху — колокольчик. Точка на нём — сколько нового.", en: "At the top of the Feed there’s a bell. The badge shows how much is new.", hy: "«Լրահոսի» վերևում զանգակ է։ Նրա վրայի կետը ցույց է տալիս՝ որքան նոր բան կա։"),
                GuideText(ru: "Внутри — кто оценил и прокомментировал ваши записи, упомянул вас, позвал в друзья или начал эфир.", en: "Inside: who liked or commented on your posts, mentioned you, sent a friend request or went live.", hy: "Ներսում՝ ով հավանեց կամ մեկնաբանեց ձեր գրառումները, նշեց ձեզ, ընկերության հայտ ուղարկեց կամ եթեր սկսեց։"),
                GuideText(ru: "Заявки в друзья можно принять прямо там. Нажатие на событие ведёт к записи или человеку.", en: "You can accept friend requests right there. Tapping an event opens the post or person.", hy: "Ընկերության հայտերը կարող եք ընդունել հենց այնտեղ։ Իրադարձության վրա սեղմելը տանում է գրառմանը կամ մարդուն։"),
            ],
            tip: GuideText(ru: "Несколько «нравится» к одной записи складываются в одну строку.", en: "Several likes on one post are combined into a single line.", hy: "Մեկ գրառման մի քանի հավանումները միավորվում են մեկ տողում։"), action: .feed
        ))
        topics.append(GuideTopic(
            id: "search", title: GuideText(ru: "Поиск, #теги и @упоминания", en: "Search, #tags and @mentions", hy: "Որոնում, #պիտակներ և @նշումներ"),
            steps: [
                GuideText(ru: "Лупа в «Ленте» — поиск людей, записей, групп и тегов.", en: "The magnifier in the Feed searches people, posts, groups and tags.", hy: "«Լրահոսի» խոշորացույցը որոնում է մարդկանց, գրառումներ, խմբեր և պիտակներ։"),
                GuideText(ru: "Пишите #тег в записи — по нему записи найдут другие. Нажатие на тег открывает все записи с ним.", en: "Write a #tag in a post so others can find it. Tapping a tag shows all posts with it.", hy: "Գրառման մեջ գրեք #պիտակ, որ ուրիշները գտնեն այն։ Պիտակի վրա սեղմելը ցույց է տալիս դրանով բոլոր գրառումները։"),
                GuideText(ru: "Наберите @ и начало имени — Memory подскажет человека. Он узнает об упоминании.", en: "Type @ and the start of a name — Memory suggests the person. They’ll be notified of the mention.", hy: "Գրեք @ և անվան սկիզբը — Memory-ն կառաջարկի մարդուն։ Նա կիմանա նշման մասին։"),
            ],
            tip: nil, action: .feed
        ))
        topics.append(GuideTopic(
            id: "edit-share", title: GuideText(ru: "Изменить запись и поделиться ссылкой", en: "Edit a post and share a link", hy: "Խմբագրել գրառումը և կիսվել հղումով"),
            steps: [
                GuideText(ru: "«⋯» на своей записи → «Изменить»: поправьте текст или уберите лишние фото. У записи появится пометка «изменено».", en: "“⋯” on your post → “Edit”: fix the text or remove extra photos. The post gets an “edited” mark.", hy: "Ձեր գրառման «⋯» → «Խմբագրել»՝ ուղղեք տեքստը կամ հեռացրեք ավելորդ լուսանկարները։ Գրառման վրա կհայտնվի «խմբագրված» նշումը։"),
                GuideText(ru: "«⋯» на любой записи → «Поделиться ссылкой» или «Скопировать ссылку».", en: "“⋯” on any post → “Share link” or “Copy link”.", hy: "Ցանկացած գրառման «⋯» → «Կիսվել հղումով» կամ «Պատճենել հղումը»։"),
                GuideText(ru: "Ссылка открывает запись в Memory, а без приложения — подскажет, как его поставить. Ссылку на профиль — «⋯» на странице человека.", en: "The link opens the post in Memory, or explains how to install the app. For a profile link — “⋯” on the person’s page.", hy: "Հղումը բացում է գրառումը Memory-ում, իսկ առանց հավելվածի՝ կբացատրի, ինչպես տեղադրել այն։ Պրոֆիլի հղումը՝ մարդու էջի «⋯»։"),
            ],
            tip: nil, action: .feed
        ))
        topics.append(GuideTopic(
            id: "repost", title: GuideText(ru: "Репост и «Отправить в чат»", en: "Repost and “Send to chat”", hy: "Վերահրապարակում և «Ուղարկել զրույցին»"),
            steps: [
                GuideText(ru: "Под записью — значок репоста: запись появится у вас в ленте карточкой, можно добавить свои слова.", en: "Under a post is the repost icon: it appears in your feed as a card, and you can add your own words.", hy: "Գրառման տակ վերահրապարակման պատկերակն է․ այն կհայտնվի ձեր լրահոսում քարտով, կարող եք ավելացնել ձեր խոսքերը։"),
                GuideText(ru: "Самолётик справа — отправить запись кому-то в переписку.", en: "The paper plane on the right sends the post to someone’s chat.", hy: "Աջ կողմի ինքնաթիռիկը՝ գրառումն ուղարկել որևէ մեկի զրույցին։"),
                GuideText(ru: "Нажатие на карточку внутри репоста открывает оригинал.", en: "Tapping the card inside a repost opens the original.", hy: "Վերահրապարակման ներսի քարտին սեղմելով՝ կբացվի բնօրինակը։"),
            ],
            tip: GuideText(ru: "Записями закрытого профиля поделиться нельзя.", en: "Posts from a private profile can’t be shared.", hy: "Փակ պրոֆիլի գրառումներով հնարավոր չէ կիսվել։"), action: .feed
        ))
        topics.append(GuideTopic(
            id: "photos", title: GuideText(ru: "Фото и видео на весь экран", en: "Full-screen photos and videos", hy: "Լուսանկարներ և տեսանյութեր ամբողջ էկրանով"),
            steps: [
                GuideText(ru: "Нажмите на фото или видео — откроется на весь экран.", en: "Tap a photo or video to open it full screen.", hy: "Սեղմեք լուսանկարին կամ տեսանյութին, և այն կբացվի ամբողջ էկրանով։"),
                GuideText(ru: "Двумя пальцами — увеличить, двойное касание — увеличить в этой точке.", en: "Pinch to zoom; double-tap to zoom in at that spot.", hy: "Երկու մատով՝ մեծացնել, կրկնակի հպումով՝ մեծացնել այդ կետում։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "place-tags", title: GuideText(ru: "Место и отметки на фото", en: "Place and photo tags", hy: "Վայր և նշումներ լուսանկարներում"),
            steps: [
                GuideText(ru: "Значок глобуса в новой записи — добавить место: название и, по желанию, точку «где я сейчас».", en: "The globe icon in a new post adds a place: a name and, optionally, a “where I am now” pin.", hy: "Նոր գրառման գլոբուսի պատկերակը՝ ավելացնել վայր․ անվանում և, ցանկության դեպքում, «որտեղ եմ հիմա» կետ։"),
                GuideText(ru: "Нажмите на добавленное фото, коснитесь человека и выберите друга — так он будет отмечен.", en: "Tap an added photo, touch a person and choose a friend to tag them.", hy: "Սեղմեք ավելացված լուսանկարին, հպեք մարդուն և ընտրեք ընկեր՝ նրան նշելու համար։"),
                GuideText(ru: "Записи, где отметили вас, — в профиле, «Отметки на фото».", en: "Posts where you’re tagged are in your profile under “Photo tags”.", hy: "Գրառումները, որտեղ ձեզ նշել են, պրոֆիլում են՝ «Նշումներ լուսանկարներում»։"),
            ],
            tip: GuideText(ru: "Отмечать можно только друзей.", en: "You can only tag friends.", hy: "Կարելի է նշել միայն ընկերներին։"), action: .feed
        ))
        topics.append(GuideTopic(
            id: "pins", title: GuideText(ru: "Закладки и витрина профиля", en: "Saved posts and profile showcase", hy: "Էջանիշներ և պրոֆիլի ցուցափեղկ"),
            steps: [
                GuideText(ru: "Закладка сохраняет запись в «Сохранённое» в профиле.", en: "Bookmark a post to keep it in “Saved” on your profile.", hy: "Էջանիշը պահպանում է գրառումը պրոֆիլի «Պահպանված» բաժնում։"),
                GuideText(ru: "«⋯» на своей записи — «Закрепить в профиле»: до трёх записей встают на витрину.", en: "“⋯” on your own post → “Pin to profile”: up to three posts go on your showcase.", hy: "«⋯» ձեր գրառման վրա → «Ամրացնել պրոֆիլում»․ մինչև երեք գրառում կհայտնվի ցուցափեղկին։"),
            ],
            tip: nil, action: .saved
        ))
        topics.append(GuideTopic(
            id: "memory", title: GuideText(ru: "«В этот день»", en: "“On this day”", hy: "«Այս օրը»"),
            steps: [
                GuideText(ru: "В Ленте появляется ваша запись год, месяц или неделю назад.", en: "The Feed shows your post from a year, a month or a week ago.", hy: "Լրահոսում հայտնվում է ձեր գրառումը մեկ տարի, ամիս կամ շաբաթ առաջ։"),
            ],
            tip: nil, action: nil
        ))
        return GuideChapter(id: "feed", emoji: "📰",
                            title: GuideText(ru: "Лента и записи", en: "Feed and posts", hy: "Լրահոս և գրառումներ"),
                            lead: GuideText(ru: "Публикации, чек-ины, опросы, комментарии", en: "Posts, check-ins, polls, comments", hy: "Հրապարակումներ, չեք-իններ, հարցումներ, մեկնաբանություններ"),
                            topics: topics)
    }

    private static func chapter2() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "view", title: GuideText(ru: "Смотреть истории", en: "Watching stories", hy: "Դիտել պատմությունները"),
            steps: [
                GuideText(ru: "Вверху «Историй» — кружки друзей. Цветное кольцо — есть новые, серое — всё просмотрено, зелёное — история «для близких».", en: "At the top of Stories are friends’ circles. A colored ring means new stories, grey — all seen, green — a close-friends story.", hy: "«Պատմություններ»-ի վերևում ընկերների շրջանակներն են։ Գունավոր օղակ՝ նորեր կան, մոխրագույն՝ բոլորը դիտված են, կանաչ՝ «մտերիմների համար» պատմություն։"),
                GuideText(ru: "Касание справа — дальше, слева — назад, удержание — пауза. Истории идут по кругу от друга к другу.", en: "Tap the right side to go forward, the left to go back, hold to pause. Stories run from one friend to the next.", hy: "Հպեք աջ կողմին՝ առաջ, ձախին՝ հետ, պահեք՝ դադար։ Պատմությունները հերթով անցնում են ընկերից ընկեր։"),
                GuideText(ru: "Внизу — ответ: он придёт автору в переписку вместе с самой историей. Сердечко — тоже ответ.", en: "Reply at the bottom: it goes to the author’s chat together with the story. The heart is a reply too.", hy: "Ներքևում՝ պատասխան․ այն կգնա հեղինակի զրույցին՝ պատմության հետ միասին։ Սրտիկը նույնպես պատասխան է։"),
            ],
            tip: nil, action: .stories
        ))
        topics.append(GuideTopic(
            id: "new", title: GuideText(ru: "Новая история", en: "A new story", hy: "Նոր պատմություն"),
            steps: [
                GuideText(ru: "Нажмите на свой кружок с «+»: фото или видео, или просто текст на цветном фоне.", en: "Tap your own circle with “+”: a photo or video, or just text on a colored background.", hy: "Սեղմեք ձեր «+»-ով շրջանակին՝ լուսանկար կամ տեսանյութ, կամ պարզապես տեքստ գունավոր ֆոնի վրա։"),
                GuideText(ru: "Выберите, кому: «Все друзья» или «Близкие друзья».", en: "Choose who sees it: “All friends” or “Close friends”.", hy: "Ընտրեք՝ ում համար․ «Բոլոր ընկերները» կամ «Մտերիմ ընկերներ»։"),
                GuideText(ru: "История видна сутки, потом остаётся только у вас — в архиве.", en: "A story is visible for a day, then stays only with you, in the archive.", hy: "Պատմությունը տեսանելի է մեկ օր, հետո մնում է միայն ձեզ մոտ՝ արխիվում։"),
            ],
            tip: nil, action: .stories
        ))
        topics.append(GuideTopic(
            id: "viewers", title: GuideText(ru: "Кто посмотрел", en: "Who viewed", hy: "Ով է դիտել"),
            steps: [
                GuideText(ru: "Откройте свою историю и нажмите «Просмотры» внизу — там все, кто её видел, и кто поставил сердечко.", en: "Open your story and tap “Views” at the bottom — everyone who saw it, and who sent a heart.", hy: "Բացեք ձեր պատմությունը և սեղմեք ներքևի «Դիտումներ»-ին՝ բոլորը, ով տեսել է, և ով սրտիկ է ուղարկել։"),
                GuideText(ru: "В меню «⋯» своей истории — «Удалить историю» и «В «Актуальное»».", en: "Your story’s “⋯” menu has “Delete story” and “Add to highlights”.", hy: "Ձեր պատմության «⋯» ընտրացանկում՝ «Ջնջել պատմությունը» և «Ավելացնել ընտրվածին»։"),
            ],
            tip: nil, action: .stories
        ))
        topics.append(GuideTopic(
            id: "highlights", title: GuideText(ru: "«Актуальное»", en: "Highlights", hy: "Ընտրված"),
            steps: [
                GuideText(ru: "Подборки историй в профиле — они не исчезают через сутки.", en: "Story collections on your profile — they don’t disappear after a day.", hy: "Պատմությունների հավաքածուներ պրոֆիլում՝ մեկ օր անց չեն անհետանում։"),
                GuideText(ru: "Профиль → «Новая подборка»: дайте название и выберите истории из архива. Долгое нажатие на подборку — изменить или удалить.", en: "Profile → “New collection”: give it a name and pick stories from the archive. Long-press a collection to edit or delete it.", hy: "Պրոֆիլ → «Նոր հավաքածու»՝ անվանեք և ընտրեք պատմություններ արխիվից։ Երկար սեղմեք հավաքածուին՝ խմբագրելու կամ ջնջելու համար։"),
            ],
            tip: GuideText(ru: "«Актуальное» видят те же, кто видит ваши истории, — друзья.", en: "Highlights are seen by the same people who see your stories — your friends.", hy: "Ընտրվածը տեսնում են նույն մարդիկ, ովքեր տեսնում են ձեր պատմությունները՝ ընկերները։"), action: .profile
        ))
        topics.append(GuideTopic(
            id: "close-friends", title: GuideText(ru: "Близкие друзья", en: "Close friends", hy: "Մտերիմ ընկերներ"),
            steps: [
                GuideText(ru: "В новой истории выберите «Близкие друзья» → «Изменить список» и отметьте людей.", en: "In a new story choose “Close friends” → “Edit list” and pick people.", hy: "Նոր պատմության մեջ ընտրեք «Մտերիմ ընկերներ» → «Խմբագրել ցանկը» և նշեք մարդկանց։"),
                GuideText(ru: "Истории «для близких» видят только они; у них кольцо зелёное.", en: "Close-friends stories are seen only by them; they get a green ring.", hy: "«Մտերիմների համար» պատմությունները տեսնում են միայն նրանք․ օղակը կանաչ է։"),
            ],
            tip: GuideText(ru: "Никто не узнает, в списке он или нет.", en: "Nobody is told whether they’re on the list.", hy: "Ոչ ոք չի իմանա՝ ցանկում է, թե ոչ։"), action: .stories
        ))
        topics.append(GuideTopic(
            id: "live", title: GuideText(ru: "Прямой эфир", en: "Live stream", hy: "Ուղիղ եթեր"),
            steps: [
                GuideText(ru: "«Истории» → «Эфир»: назовите эфир, выберите, кто видит, и нажмите «Начать».", en: "Stories → “Live”: name the stream, choose who can see it and tap “Start”.", hy: "«Պատմություններ» → «Եթեր»՝ անվանեք եթերը, ընտրեք, ով է տեսնում, և սեղմեք «Սկսել»։"),
                GuideText(ru: "Друзья получат уведомление. Зрители пишут в чат эфира и шлют сердечки.", en: "Friends are notified. Viewers write in the live chat and send hearts.", hy: "Ընկերները ծանուցում կստանան։ Դիտողները գրում են եթերի զրուցարանում և սրտիկներ ուղարկում։"),
                GuideText(ru: "Эфиры друзей — кружками с меткой «В ЭФИРЕ» над историями.", en: "Friends’ streams appear as circles marked LIVE above the stories.", hy: "Ընկերների եթերները՝ «ԵԹԵՐ» նշումով շրջանակներ պատմությունների վերևում։"),
            ],
            tip: GuideText(ru: "Эфир не сохраняется: он есть, пока идёт. Пока сервера нет, видео видит только ведущий — это демо.", en: "Streams aren’t saved — they exist only while live. Until the server is ready, only the host sees the video — it’s a demo.", hy: "Եթերը չի պահպանվում՝ այն կա, քանի դեռ ընթանում է։ Քանի դեռ սերվեր չկա, տեսանյութը տեսնում է միայն վարողը — սա դեմո է։"), action: .stories
        ))
        topics.append(GuideTopic(
            id: "friends-posts", title: GuideText(ru: "Записи друзей", en: "Friends’ posts", hy: "Ընկերների գրառումները"),
            steps: [
                GuideText(ru: "Ниже историй — записи только друзей, без чужих людей. Кнопки с именами оставляют одного человека.", en: "Below the stories are posts from friends only. Name chips filter to one person.", hy: "Պատմություններից ներքև՝ միայն ընկերների գրառումները։ Անուններով կոճակները թողնում են մեկ մարդու։"),
            ],
            tip: nil, action: .stories
        ))
        return GuideChapter(id: "stories", emoji: "🌙",
                            title: GuideText(ru: "Истории", en: "Stories", hy: "Պատմություններ"),
                            lead: GuideText(ru: "Истории на сутки, «Актуальное», близкие друзья и эфиры", en: "Stories for a day, highlights, close friends and live streams", hy: "Պատմություններ մեկ օրով, ընտրված, մտերիմ ընկերներ և եթերներ"),
                            topics: topics)
    }

    private static func chapter3() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "watch", title: GuideText(ru: "Смотреть", en: "Watching", hy: "Դիտել"),
            steps: [
                GuideText(ru: "Листайте вверх и вниз — ролики во весь экран.", en: "Swipe up and down through full-screen videos.", hy: "Թերթեք վեր և վար՝ տեսահոլովակներն ամբողջ էկրանով են։"),
                GuideText(ru: "Нажатие — пауза, двойное нажатие — «нравится».", en: "Tap to pause, double-tap to like.", hy: "Հպում՝ դադար, կրկնակի հպում՝ «հավանել»։"),
                GuideText(ru: "Справа — «нравится», комментарии, «Поделиться» и «Сохранить».", en: "On the right: like, comments, “Share” and “Save”.", hy: "Աջ կողմում՝ «հավանել», մեկնաբանություններ, «Կիսվել» և «Պահպանել»։"),
            ],
            tip: nil, action: .prism
        ))
        topics.append(GuideTopic(
            id: "shoot", title: GuideText(ru: "Снять свой ролик", en: "Shooting your own", hy: "Նկարահանել ձեր տեսահոլովակը"),
            steps: [
                GuideText(ru: "Нажмите «+» вверху справа.", en: "Tap “+” in the top right.", hy: "Սեղմեք «+» վերևում՝ աջ կողմում։"),
                GuideText(ru: "Камера: передняя или задняя, вспышка, таймер 3 или 10 секунд, длина 15, 30 или 60 секунд.", en: "Camera: front or back, flash, a 3 or 10 second timer, 15, 30 or 60 seconds long.", hy: "Տեսախցիկ՝ առջևի կամ հետևի, լուսաթարթիչ, ժամաչափ 3 կամ 10 վայրկյան, տևողություն 15, 30 կամ 60 վայրկյան։"),
                GuideText(ru: "Запись можно ставить на паузу и продолжать.", en: "You can pause recording and carry on.", hy: "Տեսագրումը կարող եք դադարեցնել և շարունակել։"),
                GuideText(ru: "Или возьмите видео из галереи.", en: "Or pick a video from your gallery.", hy: "Կամ վերցրեք տեսանյութ պատկերասրահից։"),
            ],
            tip: GuideText(ru: "Выберите музыку до съёмки — она будет звучать во время записи и сразу встанет в редактор.", en: "Choose music before shooting — it plays while you record and is already in the editor.", hy: "Ընտրեք երաժշտությունը մինչև նկարահանումը․ այն կհնչի տեսագրման ընթացքում և անմիջապես կհայտնվի խմբագրիչում։"), action: .newPrism
        ))
        topics.append(GuideTopic(
            id: "edit", title: GuideText(ru: "Редактор", en: "The editor", hy: "Խմբագրիչ"),
            steps: [
                GuideText(ru: "Обрезка до минуты и скорость от 0.5× до 3×.", en: "Trim to a minute and set speed from 0.5× to 3×.", hy: "Կտրում մինչև մեկ րոպե և արագություն 0.5×-ից 3×։"),
                GuideText(ru: "Кадр 9:16, 4:5, 1:1 или оригинал; поворот и отражение.", en: "Frame 9:16, 4:5, 1:1 or original; rotate and flip.", hy: "Կադր 9:16, 4:5, 1:1 կամ բնօրինակ, պտտում և հայելային արտացոլում։"),
                GuideText(ru: "12 фильтров и коррекция: яркость, контраст, насыщенность, тепло.", en: "12 filters and adjustments: brightness, contrast, saturation, warmth.", hy: "12 ֆիլտր և ուղղում՝ պայծառություն, կոնտրաստ, հագեցվածություն, ջերմություն։"),
                GuideText(ru: "Текст с цветом, фоном или свечением — двигайте его пальцем.", en: "Text with colour, a background or a glow — drag it with your finger.", hy: "Տեքստ՝ գույնով, ֆոնով կամ լուսարձակումով․ տեղաշարժեք այն մատով։"),
                GuideText(ru: "Стикеры, музыка с выбором начала, громкость видео и музыки, обложка.", en: "Stickers, music with a chosen start point, video and music volume, a cover.", hy: "Ստիկերներ, երաժշտություն՝ սկզբնակետի ընտրությամբ, տեսանյութի և երաժշտության ձայնի ուժգնություն, շապիկ։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "publish", title: GuideText(ru: "Публикация", en: "Publishing", hy: "Հրապարակում"),
            steps: [
                GuideText(ru: "Добавьте подпись и опубликуйте — ролик соберётся в обычный видеофайл.", en: "Add a caption and publish — the video is rendered into a regular video file.", hy: "Ավելացրեք նկարագրություն և հրապարակեք․ տեսահոլովակը կհավաքվի սովորական տեսաֆայլի։"),
                GuideText(ru: "Свой ролик можно удалить — корзина справа.", en: "Delete your own video with the bin on the right.", hy: "Ձեր տեսահոլովակը կարող եք ջնջել՝ աջ կողմի աղբարկղով։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "library", title: GuideText(ru: "Медиатека: музыка для роликов", en: "Library: music for videos", hy: "Մեդիադարան՝ երաժշտություն տեսահոլովակների համար"),
            steps: [
                GuideText(ru: "Вкладка «Медиатека» рядом с «PRISM».", en: "The “Library” tab next to “PRISM”.", hy: "«Մեդիադարան» ներդիրը «PRISM»-ի կողքին է։"),
                GuideText(ru: "Открытая музыка с лицензиями Creative Commons: поиск, жанры, прослушивание.", en: "Open music under Creative Commons licences: search, genres, listening.", hy: "Բաց երաժշտություն Creative Commons լիցենզիաներով՝ որոնում, ժանրեր, ունկնդրում։"),
                GuideText(ru: "Понравившееся сохраняйте в «Мою музыку», если площадка разрешает скачивание.", en: "Save tracks you like to “My music” where the source allows downloads.", hy: "Հավանածը պահպանեք «Իմ երաժշտությունը» բաժնում, եթե հարթակը թույլ է տալիս ներբեռնում։"),
            ],
            tip: GuideText(ru: "Автор и лицензия трека сами попадают в подпись ролика.", en: "The track’s author and licence are added to your caption automatically.", hy: "Թրեքի հեղինակն ու լիցենզիան ինքնաբերաբար ավելանում են տեսահոլովակի նկարագրությանը։"), action: nil
        ))
        return GuideChapter(id: "prism", emoji: "💎",
                            title: GuideText(ru: "PRISM", en: "PRISM", hy: "PRISM"),
                            lead: GuideText(ru: "Короткие ролики: смотреть, снимать, монтировать", en: "Short videos: watch, shoot, edit", hy: "Կարճ տեսահոլովակներ՝ դիտել, նկարահանել, մոնտաժել"),
                            topics: topics)
    }

    private static func chapter4() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "list", title: GuideText(ru: "Список переписок", en: "The chat list", hy: "Զրույցների ցանկ"),
            steps: [
                GuideText(ru: "Сверху — поиск, у переписок — счётчик непрочитанных.", en: "Search at the top; unread counts on each chat.", hy: "Վերևում՝ որոնում, զրույցների մոտ՝ չկարդացածների հաշվիչ։"),
                GuideText(ru: "Долгое нажатие на переписку — закрепить вверху, без звука, отметить прочитанной, заблокировать, очистить или удалить.", en: "Long-press a chat to pin it, mute it, mark it read, block, clear or delete it.", hy: "Երկար սեղմեք զրույցի վրա՝ ամրացնելու վերևում, անձայն դարձնելու, կարդացած նշելու, արգելափակելու, մաքրելու կամ ջնջելու համար։"),
            ],
            tip: nil, action: .chats
        ))
        topics.append(GuideTopic(
            id: "attach", title: GuideText(ru: "Вложения", en: "Attachments", hy: "Կցորդներ"),
            steps: [
                GuideText(ru: "Скрепка — фото, видео, документы, свои стикеры, гифки, опрос и место на карте.", en: "The paperclip: photos, videos, documents, your stickers, GIFs, a poll and a location.", hy: "Ամրակ՝ լուսանկարներ, տեսանյութեր, փաստաթղթեր, ձեր ստիկերները, GIF-եր, հարցում և վայր քարտեզի վրա։"),
                GuideText(ru: "Место приходит маленькой картой; нажатие — карта на весь экран и «Открыть в Картах».", en: "A location arrives as a small map; tap it for a full map and “Open in Maps”.", hy: "Վայրը գալիս է փոքր քարտեզով․ սեղմելով՝ կբացվի քարտեզն ամբողջ էկրանով և «Բացել Քարտեզներում»։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "voice", title: GuideText(ru: "Голосовые", en: "Voice notes", hy: "Ձայնային հաղորդագրություններ"),
            steps: [
                GuideText(ru: "Когда поле пустое, вместо кнопки отправки — микрофон.", en: "When the field is empty, the send button becomes a microphone.", hy: "Երբ դաշտը դատարկ է, ուղարկելու կոճակի փոխարեն խոսափող է։"),
                GuideText(ru: "Во время записи видно время и волну; можно удалить или отправить. До 5 минут.", en: "While recording you see the time and a waveform; delete or send. Up to 5 minutes.", hy: "Ձայնագրման ժամանակ երևում են ժամանակը և ալիքը․ կարող եք ջնջել կամ ուղարկել։ Մինչև 5 րոպե։"),
                GuideText(ru: "Нажатие на волну в сообщении перематывает.", en: "Tap the waveform in a message to seek.", hy: "Հաղորդագրության ալիքին սեղմելով՝ կարող եք հետ կամ առաջ տալ։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "message", title: GuideText(ru: "Действия с сообщением", en: "Message actions", hy: "Գործողություններ հաղորդագրության հետ"),
            steps: [
                GuideText(ru: "Долгое нажатие на сообщение — ответить, переслать, закрепить, удалить у себя или у всех.", en: "Long-press a message to reply, forward, pin, or delete for you or for everyone.", hy: "Երկար սեղմեք հաղորդագրության վրա՝ պատասխանելու, փոխանցելու, ամրացնելու, ձեզ մոտ կամ բոլորի մոտ ջնջելու համար։"),
                GuideText(ru: "Закреплённое видно полоской под шапкой.", en: "A pinned message shows as a strip under the header.", hy: "Ամրացվածը երևում է վերնագրի տակ՝ շերտով։"),
                GuideText(ru: "Нажатие на имя собеседника — его страница.", en: "Tap the other person’s name to open their page.", hy: "Զրուցակցի անվանը սեղմելով՝ կբացվի նրա էջը։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "secret", title: GuideText(ru: "Шифрование и исчезающие сообщения", en: "Encryption and disappearing messages", hy: "Գաղտնագրում և անհետացող հաղորդագրություններ"),
            steps: [
                GuideText(ru: "«⋯» в переписке → «Сквозное шифрование»: сообщения читаете только вы двое.", en: "“⋯” in a chat → “End-to-end encryption”: only the two of you can read the messages.", hy: "Նամակագրության «⋯» → «Ծայրից ծայր գաղտնագրում»՝ հաղորդագրությունները կարդում եք միայն դուք երկուսով։"),
                GuideText(ru: "«Код безопасности» — 60 цифр. Сверьте с собеседником: совпали — переписку никто не перехватывает.", en: "The “Safety code” is 60 digits. Compare it with your friend: if it matches, no one is intercepting.", hy: "«Անվտանգության կոդը» 60 թվանշան է։ Համեմատեք զրուցակցի հետ՝ եթե համընկնում է, ոչ ոք չի որսում։"),
                GuideText(ru: "«Исчезающие» — новые сообщения исчезнут у обоих через 5 минут, час, день или неделю.", en: "“Disappearing” — new messages vanish for both of you after 5 minutes, an hour, a day or a week.", hy: "«Անհետացող»՝ նոր հաղորդագրությունները կանհետանան երկուսի մոտ 5 րոպեից, ժամից, օրից կամ շաբաթից։"),
            ],
            tip: GuideText(ru: "Значки у времени: 🔒 — ушло зашифрованным, ⏱ — исчезнет.", en: "Icons by the time: 🔒 — sent encrypted, ⏱ — will disappear.", hy: "Ժամի կողքի նշանները՝ 🔒 — ուղարկված է գաղտնագրված, ⏱ — կանհետանա։"), action: .chats
        ))
        topics.append(GuideTopic(
            id: "backdrop", title: GuideText(ru: "Фон переписки", en: "Chat background", hy: "Զրույցի ֆոն"),
            steps: [
                GuideText(ru: "Общий фон для всех чатов — в настройках, «Фон переписок».", en: "One background for all chats: Settings → “Chat backgrounds”.", hy: "Ընդհանուր ֆոն բոլոր զրույցների համար՝ կարգավորումներում, «Զրույցների ֆոն»։"),
                GuideText(ru: "Свой фон для одной переписки — «⋯» в её шапке.", en: "A background for a single chat: “⋯” in its header.", hy: "Առանձին ֆոն մեկ զրույցի համար՝ «⋯» նրա վերնագրում։"),
                GuideText(ru: "Выбирайте из коллекции — космос, туманность, сияние, кристаллы — или своё фото.", en: "Pick from the collection — space, nebula, aurora, crystals — or your own photo.", hy: "Ընտրեք հավաքածուից՝ տիեզերք, միգամածություն, բևեռափայլ, բյուրեղներ, կամ ձեր լուսանկարը։"),
            ],
            tip: nil, action: .wallpaper
        ))
        topics.append(GuideTopic(
            id: "calls", title: GuideText(ru: "Звонки", en: "Calls", hy: "Զանգեր"),
            steps: [
                GuideText(ru: "В переписке вверху — трубка (голосовой звонок) и камера (видеозвонок).", en: "In a chat, the phone (voice call) and camera (video call) icons are at the top.", hy: "Զրույցում վերևում՝ հեռախոս (ձայնային զանգ) և տեսախցիկ (տեսազանգ)։"),
                GuideText(ru: "Во время звонка: микрофон, камера, смена камеры, громкая связь и красная кнопка — завершить.", en: "During a call: microphone, camera, switch camera, speaker and the red button to hang up.", hy: "Զանգի ժամանակ՝ խոսափող, տեսախցիկ, տեսախցիկի փոխում, բարձրախոս և կարմիր կոճակը՝ ավարտել։"),
                GuideText(ru: "«Чаты» → «Звонки» — история звонков; нажатие — перезвонить.", en: "Chats → “Calls” is the call history; tap to call back.", hy: "«Զրույցներ» → «Զանգեր»՝ զանգերի պատմությունը․ սեղմեք՝ հետ զանգելու համար։"),
            ],
            tip: GuideText(ru: "Пока сервера нет, звонки — демо: экран и история работают, голос и видео пойдут позже. Кто может звонить — в «Приватности».", en: "Until the server is ready, calls are a demo: the screen and history work, voice and video come later. Who can call you is in Privacy.", hy: "Քանի դեռ սերվեր չկա, զանգերը դեմո են․ էկրանը և պատմությունն աշխատում են, ձայնն ու տեսանյութը՝ ավելի ուշ։ Ով կարող է զանգել՝ «Գաղտնիություն»-ում։"), action: .chats
        ))
        return GuideChapter(id: "chats", emoji: "💬",
                            title: GuideText(ru: "Чаты", en: "Chats", hy: "Զրույցներ"),
                            lead: GuideText(ru: "Переписка, голосовые, вложения, фон", en: "Messages, voice notes, attachments, backgrounds", hy: "Նամակագրություն, ձայնային հաղորդագրություններ, կցորդներ, ֆոն"),
                            topics: topics)
    }

    private static func chapter5() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "find", title: GuideText(ru: "Найти и вступить", en: "Find and join", hy: "Գտնել և միանալ"),
            steps: [
                GuideText(ru: "Ищите по названию или по теме — почти 40 тем.", en: "Search by name or by topic — nearly 40 topics.", hy: "Որոնեք ըստ անվանման կամ թեմայի՝ մոտ 40 թեմա։"),
                GuideText(ru: "Откройте группу и нажмите «Вступить». Выйти можно в её настройках.", en: "Open a group and tap “Join”. You can leave from its settings.", hy: "Բացեք խումբը և սեղմեք «Միանալ»։ Դուրս գալ կարող եք նրա կարգավորումներում։"),
            ],
            tip: nil, action: .groups
        ))
        topics.append(GuideTopic(
            id: "create", title: GuideText(ru: "Своя группа", en: "Your own group", hy: "Ձեր խումբը"),
            steps: [
                GuideText(ru: "«+» вверху вкладки «Группы».", en: "Tap “+” at the top of the Groups tab.", hy: "«+» «Խմբեր» ներդիրի վերևում։"),
                GuideText(ru: "Название, описание и тема — и «Создать».", en: "Name, description and topic — then “Create”.", hy: "Անվանում, նկարագրություն և թեմա, ապա՝ «Ստեղծել»։"),
                GuideText(ru: "Шестерёнка на заставке: своя заставка, описание, кто может писать — все или только вы.", en: "The gear on the cover: your own cover, description, who can post — everyone or only you.", hy: "Ատամնանիվը շապիկի վրա՝ ձեր շապիկը, նկարագրությունը, ով կարող է գրել՝ բոլորը, թե միայն դուք։"),
                GuideText(ru: "«⋯» на записи в группе — закрепить её первой.", en: "“⋯” on a group post pins it to the top.", hy: "«⋯» խմբի գրառման վրա՝ այն առաջինը ամրացնելու համար։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "look", title: GuideText(ru: "Оформление группы", en: "Styling your group", hy: "Խմբի ձևավորում"),
            steps: [
                GuideText(ru: "Шестерёнка → «Оформление».", en: "Gear → “Look”.", hy: "Ատամնանիվ → «Ձևավորում»։"),
                GuideText(ru: "Цвет группы — один из семи: в нём кнопки, цены и вкладки.", en: "A group colour — one of seven — tints buttons, prices and tabs.", hy: "Խմբի գույնը՝ յոթից մեկը․ դրանով են ներկվում կոճակները, գները և ներդիրները։"),
                GuideText(ru: "Фон страницы — цвета, градиенты или своё фото.", en: "A page background — colours, gradients or your own photo.", hy: "Էջի ֆոն՝ գույներ, գրադիենտներ կամ ձեր լուսանկարը։"),
            ],
            tip: GuideText(ru: "Так группа-магазин выглядит в своём стиле.", en: "That way a shop group looks like your own brand.", hy: "Այսպես խումբ-խանութը կունենա իր սեփական ոճը։"), action: nil
        ))
        topics.append(GuideTopic(
            id: "about", title: GuideText(ru: "Карточка «О группе»", en: "The “About” card", hy: "«Խմբի մասին» քարտը"),
            steps: [
                GuideText(ru: "Шестерёнка → «О группе»: подробное описание, сайт, телефон, почта, адрес, часы работы.", en: "Gear → “About the group”: a longer description, website, phone, email, address, opening hours.", hy: "Ատամնանիվ → «Խմբի մասին»՝ մանրամասն նկարագրություն, կայք, հեռախոս, էլ. փոստ, հասցե, աշխատանքային ժամեր։"),
                GuideText(ru: "Участники видят карточку над записями: нажатие звонит, пишет или открывает карту.", en: "Members see the card above the posts: tap to call, email or open a map.", hy: "Մասնակիցները քարտը տեսնում են գրառումների վերևում․ սեղմելով՝ կարող են զանգել, գրել կամ բացել քարտեզը։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "shop-owner", title: GuideText(ru: "Витрина: как продавать", en: "Showcase: selling", hy: "Ցուցափեղկ՝ ինչպես վաճառել"),
            steps: [
                GuideText(ru: "В своей группе откройте вкладку «Витрина» и добавьте товар.", en: "In your group, open the “Showcase” tab and add a product.", hy: "Ձեր խմբում բացեք «Ցուցափեղկ» ներդիրը և ավելացրեք ապրանք։"),
                GuideText(ru: "До пяти фото, название, цена, описание, категория, наличие и ссылка на ваш сайт.", en: "Up to five photos, a title, price, description, category, stock and a link to your site.", hy: "Մինչև հինգ լուսանկար, անվանում, գին, նկարագրություն, կատեգորիա, առկայություն և հղում ձեր կայքին։"),
                GuideText(ru: "Пустая цена или 0 — «Цена по запросу».", en: "An empty price or 0 shows “Price on request”.", hy: "Դատարկ գին կամ 0՝ «Գինը՝ ըստ հարցման»։"),
                GuideText(ru: "В карточке товара — «Изменить», «Убрать», «Выше в витрине» и «Ниже».", en: "In a product card: “Edit”, “Remove”, “Move up” and “Move down”.", hy: "Ապրանքի քարտում՝ «Փոփոխել», «Հեռացնել», «Բարձրացնել ցուցափեղկում» և «Իջեցնել»։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "shop-buyer", title: GuideText(ru: "Витрина: как купить", en: "Showcase: buying", hy: "Ցուցափեղկ՝ ինչպես գնել"),
            steps: [
                GuideText(ru: "Фильтруйте товары по категориям и откройте карточку.", en: "Filter products by category and open a card.", hy: "Զտեք ապրանքներն ըստ կատեգորիաների և բացեք քարտը։"),
                GuideText(ru: "«Написать продавцу» — откроется переписка, товар уже приложен.", en: "“Message the seller” opens a chat with the product attached.", hy: "«Գրել վաճառողին»՝ կբացվի զրույց, ապրանքն արդեն կցված է։"),
                GuideText(ru: "«Купить на сайте» — если продавец дал ссылку.", en: "“Buy on the website” — if the seller added a link.", hy: "«Գնել կայքում»՝ եթե վաճառողը հղում է տրամադրել։"),
            ],
            tip: GuideText(ru: "Оплаты внутри Memory нет: о цене, оплате и доставке договаривайтесь с продавцом.", en: "There is no payment inside Memory: agree on price, payment and delivery with the seller.", hy: "Memory-ում վճարում չկա․ գնի, վճարման և առաքման մասին պայմանավորվեք վաճառողի հետ։"), action: nil
        ))
        return GuideChapter(id: "groups", emoji: "🪐",
                            title: GuideText(ru: "Группы и витрины", en: "Groups and shops", hy: "Խմբեր և ցուցափեղկեր"),
                            lead: GuideText(ru: "Сообщества, магазины, оформление", en: "Communities, shops, styling", hy: "Համայնքներ, խանութներ, ձևավորում"),
                            topics: topics)
    }

    private static func chapter6() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "goals", title: GuideText(ru: "Цели", en: "Goals", hy: "Նպատակներ"),
            steps: [
                GuideText(ru: "Общие шкалы сообщества: «Вместе пробежим 1 000 000 км», «10 000 прочитанных книг».", en: "Shared community counters: “Let’s run 1,000,000 km”, “10,000 books read”.", hy: "Համայնքի ընդհանուր սանդղակներ՝ «Միասին կվազենք 1 000 000 կմ», «10 000 կարդացված գիրք»։"),
                GuideText(ru: "Вступили — и ваши чек-ины засчитываются сами. Вклад можно добавить и вручную.", en: "Join and your check-ins count automatically. You can also add progress by hand.", hy: "Միացաք, և ձեր չեք-իններն ինքնաբերաբար հաշվվում են։ Ներդրումը կարող եք ավելացնել նաև ձեռքով։"),
            ],
            tip: nil, action: .together
        ))
        topics.append(GuideTopic(
            id: "skills", title: GuideText(ru: "Навыки", en: "Skills", hy: "Հմտություններ"),
            steps: [
                GuideText(ru: "Банк времени без денег: научили кого-то 30 минут — получили 30 минут на любой навык.", en: "A time bank without money: teach someone for 30 minutes, get 30 minutes of any skill.", hy: "Ժամանակի բանկ՝ առանց փողի․ ինչ-որ մեկին սովորեցրիք 30 րոպե՝ ստացաք 30 րոպե ցանկացած հմտության համար։"),
                GuideText(ru: "Отметьте, чему научите и чему хотите научиться; первыми идут взаимные обмены.", en: "Mark what you can teach and what you want to learn; mutual swaps come first.", hy: "Նշեք, թե ինչ կարող եք սովորեցնել և ինչ եք ուզում սովորել․ առաջինը ցուցադրվում են փոխադարձ փոխանակումները։"),
                GuideText(ru: "Договорённость уходит в чат, «Занятие прошло» двигает баланс.", en: "The arrangement goes to a chat; “Session done” updates the balance.", hy: "Պայմանավորվածությունն անցնում է զրույց, «Դասն անցկացվեց» կոճակը թարմացնում է հաշվեկշիռը։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "journeys", title: GuideText(ru: "Дневники", en: "Journeys", hy: "Օրագրեր"),
            steps: [
                GuideText(ru: "Короткие челленджи по дням: «День 3: учусь жонглировать».", en: "Short day-by-day challenges: “Day 3: learning to juggle”.", hy: "Կարճ մարտահրավերներ ըստ օրերի՝ «Օր 3․ սովորում եմ ձեռնածություն»։"),
                GuideText(ru: "Выкладывайте процесс — сообщество советует, вы отмечаете полезное.", en: "Share your progress — the community gives tips and you mark the helpful ones.", hy: "Կիսվեք ընթացքով․ համայնքը խորհուրդներ է տալիս, իսկ դուք նշում եք օգտակարները։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "missions", title: GuideText(ru: "Миссии и клады", en: "Missions and caches", hy: "Առաքելություններ և գանձեր"),
            steps: [
                GuideText(ru: "Миссии — квесты по городу: фото находки встаёт на общую карту там, где вы.", en: "Missions are city quests: your photo goes on a shared map where you are.", hy: "Առաքելությունները քաղաքային քվեստներ են․ գտածոյի լուսանկարը հայտնվում է ընդհանուր քարտեզի վրա այնտեղ, որտեղ դուք եք։"),
                GuideText(ru: "Клады — спрячьте контейнер с подсказкой. «Нашёл!» можно нажать только в 150 м от клада.", en: "Caches: hide a container with a clue. “Found it!” works only within 150 m of the cache.", hy: "Գանձեր՝ թաքցրեք տարա հուշումով։ «Գտա՛» կոճակը կարելի է սեղմել միայն գանձից 150 մ հեռավորության վրա։"),
            ],
            tip: GuideText(ru: "Найдите три клада — и сможете прятать свои.", en: "Find three caches and you can hide your own.", hy: "Գտեք երեք գանձ, և կկարողանաք թաքցնել ձերը։"), action: nil
        ))
        topics.append(GuideTopic(
            id: "outings", title: GuideText(ru: "Вылазки, рисунок, обмен", en: "Outings, drawing, swaps", hy: "Արշավներ, նկար, փոխանակում"),
            steps: [
                GuideText(ru: "Вылазки — события до 10 человек: грибы, фотопрогулка, велопробег.", en: "Outings are events for up to 10 people: mushroom picking, photo walks, bike rides.", hy: "Արշավներ՝ իրադարձություններ մինչև 10 հոգու համար՝ սնկահավաք, ֆոտոզբոսանք, հեծանվարշավ։"),
                GuideText(ru: "Рисунок — эстафета: каждый дорисовывает своё и передаёт дальше; готовое можно опубликовать.", en: "Drawing is a relay: everyone adds something and passes it on; the result can be published.", hy: "Նկար՝ էստաֆետ․ յուրաքանչյուրը լրացնում է իրենը և փոխանցում հաջորդին, պատրաստը կարելի է հրապարակել։"),
                GuideText(ru: "Обмен — книжный клуб, семена и растения, ремонт вещей.", en: "Swaps: a book club, seeds and plants, repairing things.", hy: "Փոխանակում՝ գրքի ակումբ, սերմեր և բույսեր, իրերի նորոգում։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "invite", title: GuideText(ru: "Позвать того, кого нет в Memory", en: "Invite someone who isn’t on Memory", hy: "Հրավիրել նրան, ով Memory-ում չէ"),
            steps: [
                GuideText(ru: "В рисунке, обмене, вылазке и навыках есть «Позвать» — уйдёт ссылка-приглашение.", en: "Drawing, swaps, outings and skills have “Invite”, which sends an invite link.", hy: "Նկարում, փոխանակումում, արշավում և հմտություններում կա «Հրավիրել» կոճակը․ կուղարկվի հրավերի հղում։"),
                GuideText(ru: "Ваш код приглашения — в настройках, «Друзья и идеи».", en: "Your invite code is in Settings → “Friends & ideas”.", hy: "Ձեր հրավերի կոդը՝ կարգավորումներում, «Ընկերներ և գաղափարներ»։"),
            ],
            tip: nil, action: .growth
        ))
        return GuideChapter(id: "together", emoji: "🤝",
                            title: GuideText(ru: "Вместе", en: "Together", hy: "Միասին"),
                            lead: GuideText(ru: "Делать что-то вместе, а не соревноваться", en: "Doing things together, not competing", hy: "Ինչ-որ բան անել միասին, ոչ թե մրցել"),
                            topics: topics)
    }

    private static func chapter7() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "add", title: GuideText(ru: "Добавить в друзья", en: "Adding a friend", hy: "Ավելացնել ընկերներին"),
            steps: [
                GuideText(ru: "Откройте страницу человека — нажмите на имя или аватар.", en: "Open a person’s page by tapping their name or avatar.", hy: "Բացեք մարդու էջը՝ սեղմեք անվանը կամ ավատարին։"),
                GuideText(ru: "«Добавить в друзья» отправит заявку; пока её не приняли, видно «Заявка отправлена».", en: "“Add friend” sends a request; until it’s accepted you see “Request sent”.", hy: "«Ավելացնել ընկերներին» կոճակը կուղարկի հայտ․ քանի դեռ այն չի ընդունվել, երևում է «Հայտն ուղարկված է»։"),
                GuideText(ru: "Когда человек примет заявку, вы станете друзьями взаимно.", en: "When they accept, you become friends both ways.", hy: "Երբ մարդն ընդունի հայտը, դուք փոխադարձաբար ընկերներ կդառնաք։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "requests", title: GuideText(ru: "Заявки в друзья", en: "Friend requests", hy: "Ընկերության հայտեր"),
            steps: [
                GuideText(ru: "Входящие — в профиле, «Заявки в друзья»: принять или отклонить.", en: "Incoming requests are in Profile → “Friend requests”: accept or decline.", hy: "Մուտքայինները՝ պրոֆիլում, «Ընկերության հայտեր»․ ընդունեք կամ մերժեք։"),
                GuideText(ru: "Там же — заявки, которые отправили вы.", en: "Requests you’ve sent are there too.", hy: "Այնտեղ են նաև ձեր ուղարկած հայտերը։"),
            ],
            tip: nil, action: .requests
        ))
        topics.append(GuideTopic(
            id: "remove", title: GuideText(ru: "Убрать из друзей", en: "Removing a friend", hy: "Հեռացնել ընկերներից"),
            steps: [
                GuideText(ru: "В списке друзей или на странице человека — «Удалить из друзей», с подтверждением.", en: "In your friends list or on their page: “Remove from friends”, with a confirmation.", hy: "Ընկերների ցանկում կամ մարդու էջում՝ «Հեռացնել ընկերներից»՝ հաստատումով։"),
            ],
            tip: nil, action: .friends
        ))
        topics.append(GuideTopic(
            id: "why", title: GuideText(ru: "Зачем друзья", en: "What friends see", hy: "Ինչու են պետք ընկերները"),
            steps: [
                GuideText(ru: "Записи друзей — во вкладке «Истории».", en: "Friends’ posts appear in Stories.", hy: "Ընկերների գրառումները՝ «Պատմություններ» ներդիրում։"),
                GuideText(ru: "Закрытый профиль видят только друзья.", en: "Only friends can see a private profile.", hy: "Փակ պրոֆիլը տեսնում են միայն ընկերները։"),
            ],
            tip: nil, action: nil
        ))
        return GuideChapter(id: "friends", emoji: "✨",
                            title: GuideText(ru: "Друзья", en: "Friends", hy: "Ընկերներ"),
                            lead: GuideText(ru: "Дружба взаимная — по заявке", en: "Friendship is mutual — by request", hy: "Ընկերությունը փոխադարձ է՝ հայտով"),
                            topics: topics)
    }

    private static func chapter8() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "edit", title: GuideText(ru: "Ваша страница", en: "Your page", hy: "Ձեր էջը"),
            steps: [
                GuideText(ru: "Аватар — фото из галереи или с камеры, с обрезкой по кругу.", en: "Avatar: a photo from the gallery or camera, cropped to a circle.", hy: "Ավատար՝ լուսանկար պատկերասրահից կամ տեսախցիկից՝ շրջանաձև կտրվածքով։"),
                GuideText(ru: "«О себе», город, день рождения, сайт и интересы.", en: "“About me”, city, birthday, website and interests.", hy: "«Իմ մասին», քաղաք, ծննդյան օր, կայք և հետաքրքրություններ։"),
                GuideText(ru: "Счётчики записей, друзей и групп открывают свои списки.", en: "The post, friend and group counters open their lists.", hy: "Գրառումների, ընկերների և խմբերի հաշվիչները բացում են համապատասխան ցանկերը։"),
            ],
            tip: nil, action: .profile
        ))
        topics.append(GuideTopic(
            id: "home", title: GuideText(ru: "Главная профиля", en: "Profile home", hy: "Պրոֆիլի գլխավոր էջ"),
            steps: [
                GuideText(ru: "Витрина: до трёх закреплённых записей, ваши стикеры и музыка.", en: "Showcase: up to three pinned posts, your stickers and music.", hy: "Ցուցափեղկ՝ մինչև երեք ամրացված գրառում, ձեր ստիկերները և երաժշտությունը։"),
                GuideText(ru: "Активность за две недели и кристаллы-достижения.", en: "Two weeks of activity and crystal achievements.", hy: "Ակտիվությունը երկու շաբաթվա ընթացքում և բյուրեղ-նվաճումներ։"),
                GuideText(ru: "Вкладки «Записи · PRISM · Медиа», ваши группы и друзья.", en: "Tabs “Posts · PRISM · Media”, your groups and friends.", hy: "«Գրառումներ · PRISM · Մեդիա» ներդիրները, ձեր խմբերը և ընկերները։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "shelf", title: GuideText(ru: "Полка", en: "Shelf", hy: "Դարակ"),
            steps: [
                GuideText(ru: "Собирается из чек-инов сама: фильмы с оценками, книги по статусам, спорт, места.", en: "Built from your check-ins: rated films, books by status, sport, places.", hy: "Ինքնաբերաբար կազմվում է չեք-իններից՝ ֆիլմեր՝ գնահատականներով, գրքեր՝ ըստ կարգավիճակի, սպորտ, վայրեր։"),
                GuideText(ru: "«Для вас» — советы от людей с похожим вкусом и план бега на неделю.", en: "“For you”: tips from people with similar taste and a weekly running plan.", hy: "«Ձեզ համար»՝ խորհուրդներ նման ճաշակ ունեցող մարդկանցից և վազքի շաբաթական պլան։"),
                GuideText(ru: "«Права» — что вы уже открыли: свои клады, миссии, цели, звание наставника.", en: "“Abilities”: what you’ve earned — your own caches, missions, goals, the mentor title.", hy: "«Իրավունքներ»՝ ինչ եք արդեն բացել՝ ձեր գանձերը, առաքելությունները, նպատակները, դաստիարակի կոչումը։"),
            ],
            tip: nil, action: .shelf
        ))
        topics.append(GuideTopic(
            id: "recap", title: GuideText(ru: "Итоги месяца и года", en: "Monthly and yearly recaps", hy: "Ամսվա և տարվա ամփոփում"),
            steps: [
                GuideText(ru: "Картинка на звёздном небе: фильмы, книги, километры, места, записи, «нравится».", en: "A picture on a starry sky: films, books, kilometres, places, posts, likes.", hy: "Նկար աստղազարդ երկնքի ֆոնին՝ ֆիլմեր, գրքեր, կիլոմետրեր, վայրեր, գրառումներ, «հավանումներ»։"),
                GuideText(ru: "Стрелки листают прошлые периоды.", en: "Arrows page through earlier periods.", hy: "Սլաքներով կարող եք թերթել նախորդ ժամանակահատվածները։"),
                GuideText(ru: "Опубликуйте в Ленту или Истории, отправьте в чат или сохраните в галерею.", en: "Post it to the Feed or Stories, send it to a chat or save it to your gallery.", hy: "Հրապարակեք Լրահոսում կամ Պատմություններում, ուղարկեք զրույց կամ պահպանեք պատկերասրահում։"),
            ],
            tip: nil, action: .recap
        ))
        topics.append(GuideTopic(
            id: "studio", title: GuideText(ru: "Студия стикеров", en: "Sticker studio", hy: "Ստիկերների ստուդիա"),
            steps: [
                GuideText(ru: "Из фото — с формой (круг, сердце, кристалл, звезда), обводкой и подписью.", en: "From a photo, with a shape (circle, heart, crystal, star), outline and caption.", hy: "Լուսանկարից՝ ձևով (շրջան, սիրտ, բյուրեղ, աստղ), եզրագծով և մակագրությամբ։"),
                GuideText(ru: "Вырезка — объект с фото вырезается сам.", en: "Cut-out: the subject is lifted out of the photo automatically.", hy: "Կտրվածք՝ օբյեկտն ինքնաբերաբար կտրվում է լուսանկարից։"),
                GuideText(ru: "Рисунок кистью — и покадровый, до 12 кадров.", en: "Draw with a brush, including frame-by-frame animation, up to 12 frames.", hy: "Նկարում վրձինով, նաև կադր առ կադր՝ մինչև 12 կադր։"),
                GuideText(ru: "Конструктор смайлов и значок с текстом или эмодзи.", en: "An emoji face builder and a badge with text or an emoji.", hy: "Սմայլիկների կոնստրուկտոր և կրծքանշան՝ տեքստով կամ էմոջիով։"),
                GuideText(ru: "Живые стикеры: эффекты (контур, комикс, объём, блеск) и движение (прыжок, пульс, парение…).", en: "Live stickers: effects (outline, comic, 3D, shine) and motion (jump, pulse, float…).", hy: "Կենդանի ստիկերներ՝ էֆեկտներ (եզրագիծ, կոմիքս, ծավալ, փայլ) և շարժում (ցատկ, զարկերակ, սավառնում…)։"),
            ],
            tip: GuideText(ru: "Собирайте стикеры в коллекции — они станут вкладками при выборе стикера.", en: "Group stickers into collections — they become tabs in the sticker picker.", hy: "Հավաքեք ստիկերները հավաքածուներում․ դրանք կդառնան ներդիրներ ստիկեր ընտրելիս։"), action: .studio
        ))
        topics.append(GuideTopic(
            id: "plus", title: GuideText(ru: "Memory+ и пробный период", en: "Memory+ and the free trial", hy: "Memory+ և փորձաշրջան"),
            steps: [
                GuideText(ru: "Профиль → карточка «Memory+»: тарифы, цены и что входит.", en: "Profile → the “Memory+” card: plans, prices and what’s included.", hy: "Պրոֆիլ → «Memory+» քարտը՝ սակագներ, գներ և ինչ է ներառված։"),
                GuideText(ru: "Memory+ — стикеры, фоны, звуки, PRISM до 60 секунд, больше плейлистов и итоги за любой период.", en: "Memory+: stickers, backgrounds, sounds, PRISM up to 60 seconds, more playlists and recaps for any period.", hy: "Memory+՝ ստիկերներ, ֆոներ, ձայներ, PRISM՝ մինչև 60 վայրկյան, ավելի շատ փլեյլիստներ և ամփոփումներ ցանկացած ժամանակահատվածի համար։"),
                GuideText(ru: "Memory+ Бизнес — всё из Memory+ и витрина без ограничений, фон страницы группы и контакты.", en: "Memory+ Business: everything in Memory+, plus an unlimited shop, a group page background and contacts.", hy: "Memory+ Բիզնես՝ ամբողջը Memory+-ից, գումարած անսահմանափակ ցուցափեղկ, խմբի էջի ֆոն և կոնտակտներ։"),
                GuideText(ru: "Попробуйте любой тариф 3 дня бесплатно — один раз на аккаунт.", en: "Try either plan free for 3 days — once per account.", hy: "Փորձեք ցանկացած սակագին 3 օր անվճար՝ մեկ անգամ յուրաքանչյուր հաշվի համար։"),
                GuideText(ru: "Сменить тариф или срок и отменить продление — там же.", en: "Switch plan or term and cancel renewal in the same place.", hy: "Սակագինը կամ ժամկետը փոխել և երկարաձգումը չեղարկել կարող եք նույն տեղում։"),
            ],
            tip: GuideText(ru: "Когда подписка заканчивается, ничего не пропадает: созданное остаётся, нельзя только добавить новое сверх бесплатного.", en: "When a subscription ends, nothing is lost: what you made stays; you just can’t add more beyond the free limits.", hy: "Երբ բաժանորդագրությունն ավարտվում է, ոչինչ չի կորչում․ ստեղծածը մնում է, պարզապես չեք կարող ավելացնել նորը՝ անվճարից ավելի։"), action: .premium
        ))
        topics.append(GuideTopic(
            id: "music", title: GuideText(ru: "Моя музыка", en: "My music", hy: "Իմ երաժշտությունը"),
            steps: [
                GuideText(ru: "Свои треки (mp3, m4a, flac) и плейлисты: создать, переименовать, поменять порядок.", en: "Your own tracks (mp3, m4a, flac) and playlists: create, rename, reorder.", hy: "Ձեր թրեքերը (mp3, m4a, flac) և փլեյլիստները՝ ստեղծել, վերանվանել, փոխել հերթականությունը։"),
                GuideText(ru: "Плеер — внизу экрана.", en: "The player sits at the bottom of the screen.", hy: "Նվագարկիչը էկրանի ներքևում է։"),
            ],
            tip: GuideText(ru: "Музыка хранится только на телефоне и видна только вам.", en: "Music stays on your phone and only you can see it.", hy: "Երաժշտությունը պահվում է միայն հեռախոսում և տեսանելի է միայն ձեզ։"), action: .music
        ))
        return GuideChapter(id: "profile", emoji: "👤",
                            title: GuideText(ru: "Профиль", en: "Profile", hy: "Պրոֆիլ"),
                            lead: GuideText(ru: "Страница, полка, итоги, студия и музыка", en: "Your page, shelf, recaps, studio and music", hy: "Էջ, դարակ, ամփոփումներ, ստուդիա և երաժշտություն"),
                            topics: topics)
    }

    private static func chapter9() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "who", title: GuideText(ru: "Кто что видит", en: "Who sees what", hy: "Ով ինչ է տեսնում"),
            steps: [
                GuideText(ru: "Настройки → «Приватность»: кто видит сведения и друзей, кто может писать и комментировать.", en: "Settings → “Privacy”: who sees your details and friends, who can message and comment.", hy: "Կարգավորումներ → «Գաղտնիություն»՝ ով է տեսնում ձեր տվյալները և ընկերներին, ով կարող է գրել և մեկնաբանել։"),
                GuideText(ru: "Там же — «в сети», день рождения и закрытый профиль.", en: "Also: online status, birthday and a private profile.", hy: "Այնտեղ են նաև «առցանց» կարգավիճակը, ծննդյան օրը և փակ պրոֆիլը։"),
                GuideText(ru: "Закрытый профиль: записи и чек-ины видят только друзья.", en: "Private profile: only friends see your posts and check-ins.", hy: "Փակ պրոֆիլ՝ գրառումները և չեք-ինները տեսնում են միայն ընկերները։"),
            ],
            tip: nil, action: .privacy
        ))
        topics.append(GuideTopic(
            id: "recs", title: GuideText(ru: "Персональные рекомендации", en: "Personal recommendations", hy: "Անհատական առաջարկություններ"),
            steps: [
                GuideText(ru: "Советы «Для вас» строятся по вашим оценкам.", en: "“For you” tips are based on your ratings.", hy: "«Ձեզ համար» խորհուրդները կազմվում են ձեր գնահատականների հիման վրա։"),
                GuideText(ru: "Их можно выключить: «Приватность» → «Персональные рекомендации».", en: "Turn them off in “Privacy” → “Personal recommendations”.", hy: "Դրանք կարող եք անջատել՝ «Գաղտնիություն» → «Անհատական առաջարկություններ»։"),
            ],
            tip: nil, action: .privacy
        ))
        topics.append(GuideTopic(
            id: "my-data", title: GuideText(ru: "Скачать мои данные", en: "Download my data", hy: "Ներբեռնել իմ տվյալները"),
            steps: [
                GuideText(ru: "Настройки → «Приватность» → «Скачать мои данные».", en: "Settings → “Privacy” → “Download my data”.", hy: "Կարգավորումներ → «Գաղտնիություն» → «Ներբեռնել իմ տվյալները»։"),
                GuideText(ru: "Выберите, класть ли фото, видео и музыку, и место для архива — «Файлы», диск или флешка.", en: "Choose whether to include photos, videos and music, and where to save the archive — Files, a drive or a USB stick.", hy: "Ընտրեք՝ ներառե՞լ լուսանկարները, տեսանյութերն ու երաժշտությունը, և արխիվի տեղը՝ «Ֆայլեր», սկավառակ կամ ֆլեշ։"),
                GuideText(ru: "В архиве — профиль, записи, переписка, группы, PRISM, полка и настройки. Пароля и кодов там нет.", en: "The archive has your profile, posts, chats, groups, PRISM, shelf and settings. No password or codes.", hy: "Արխիվում՝ պրոֆիլ, գրառումներ, նամակագրություն, խմբեր, PRISM, դարակ և կարգավորումներ։ Գաղտնաբառ և կոդեր չկան։"),
            ],
            tip: nil, action: .privacy
        ))
        topics.append(GuideTopic(
            id: "crashes", title: GuideText(ru: "Отчёты о сбоях и статистика", en: "Crash reports and statistics", hy: "Խափանումների հաշվետվություններ և վիճակագրություն"),
            steps: [
                GuideText(ru: "Настройки → «Приватность» → «Сбои и статистика»: два отдельных переключателя, по умолчанию выключены.", en: "Settings → “Privacy” → “Crashes and statistics”: two separate switches, off by default.", hy: "Կարգավորումներ → «Գաղտնիություն» → «Խափանումներ և վիճակագրություն»՝ երկու առանձին անջատիչ, լռելյայն անջատված։"),
                GuideText(ru: "Отчёт о сбое — что сломалось и где в коде, без ваших записей и переписки.", en: "A crash report says what broke and where in the code — without your posts or chats.", hy: "Խափանման հաշվետվությունը ցույց է տալիս՝ ինչ է խափանվել և որտեղ կոդում, առանց ձեր գրառումների և նամակագրության։"),
                GuideText(ru: "Статистика — раз в день счётчики вроде «опубликовано записей: 3», без текстов.", en: "Statistics — once a day, counters like “posts published: 3”, no texts.", hy: "Վիճակագրություն՝ օրը մեկ հաշվիչներ, օրինակ՝ «հրապարակված գրառումներ՝ 3», առանց տեքստերի։"),
            ],
            tip: nil, action: .privacy
        ))
        topics.append(GuideTopic(
            id: "report", title: GuideText(ru: "Пожаловаться", en: "Reporting", hy: "Բողոքել"),
            steps: [
                GuideText(ru: "«Пожаловаться» есть у записи, комментария, ролика, сообщения и человека.", en: "“Report” is available on posts, comments, videos, messages and people.", hy: "«Բողոքել» կոճակը կա գրառման, մեկնաբանության, տեսահոլովակի, հաղորդագրության և մարդու մոտ։"),
                GuideText(ru: "Выберите причину и при желании добавьте пару слов и блокировку.", en: "Pick a reason, and optionally add a note and block them.", hy: "Ընտրեք պատճառը և, ցանկության դեպքում, ավելացրեք մի քանի բառ և արգելափակում։"),
                GuideText(ru: "У вас это сразу скрывается, жалоба уходит модераторам.", en: "It’s hidden for you right away, and the report goes to moderators.", hy: "Ձեզ մոտ այն անմիջապես թաքցվում է, իսկ բողոքն ուղարկվում է մոդերատորներին։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "block", title: GuideText(ru: "Чёрный список", en: "Blocking", hy: "Սև ցուցակ"),
            steps: [
                GuideText(ru: "«⋯» на странице человека → «Заблокировать».", en: "“⋯” on a person’s page → “Block”.", hy: "«⋯» մարդու էջում → «Արգելափակել»։"),
                GuideText(ru: "Его записи, комментарии и сообщения больше не видны, писать ему нельзя.", en: "Their posts, comments and messages disappear, and you can’t message each other.", hy: "Նրա գրառումները, մեկնաբանությունները և հաղորդագրություններն այլևս չեն երևում, և նրան գրել հնարավոր չէ։"),
                GuideText(ru: "Разблокировать — в настройках, «Чёрный список».", en: "Unblock in Settings → “Blocked”.", hy: "Արգելաբացել կարող եք կարգավորումներում՝ «Սև ցուցակ»։"),
            ],
            tip: nil, action: .blacklist
        ))
        topics.append(GuideTopic(
            id: "password", title: GuideText(ru: "Пароль, восстановление и удаление аккаунта", en: "Password, recovery and deleting your account", hy: "Գաղտնաբառ, վերականգնում և հաշվի ջնջում"),
            steps: [
                GuideText(ru: "Настройки → «Безопасность»: сменить пароль.", en: "Settings → “Security”: change your password.", hy: "Կարգավորումներ → «Անվտանգություն»՝ փոխել գաղտնաբառը։"),
                GuideText(ru: "Там же — коды восстановления (новые — по паролю) и почта: подтвердите её кодом из письма.", en: "Also there: recovery codes (new ones need your password) and email — confirm it with the code from the message.", hy: "Այնտեղ են նաև վերականգնման կոդերը (նորերը՝ գաղտնաբառով) և էլ. փոստը՝ հաստատեք այն նամակի կոդով։"),
                GuideText(ru: "И удалить аккаунт: всё ваше будет стёрто с телефона без возврата.", en: "And delete your account: everything of yours is erased from the phone for good.", hy: "Եվ ջնջել հաշիվը․ ձեր ամբողջ տվյալներն անվերադարձ կջնջվեն հեռախոսից։"),
            ],
            tip: nil, action: .security
        ))
        topics.append(GuideTopic(
            id: "docs", title: GuideText(ru: "Документы", en: "Documents", hy: "Փաստաթղթեր"),
            steps: [
                GuideText(ru: "Соглашение, политика конфиденциальности, согласие на обработку, правила сообщества — в настройках, «Документы».", en: "Terms, privacy policy, data consent and community rules are in Settings → “Documents”.", hy: "Համաձայնագիրը, գաղտնիության քաղաքականությունը, մշակման համաձայնությունը, համայնքի կանոնները՝ կարգավորումներում, «Փաստաթղթեր»։"),
                GuideText(ru: "Когда документы меняются, Memory попросит принять новую версию.", en: "When documents change, Memory asks you to accept the new version.", hy: "Երբ փաստաթղթերը փոխվում են, Memory-ն կխնդրի ընդունել նոր տարբերակը։"),
            ],
            tip: nil, action: .legal
        ))
        return GuideChapter(id: "privacy", emoji: "🛡",
                            title: GuideText(ru: "Приватность и безопасность", en: "Privacy and safety", hy: "Գաղտնիություն և անվտանգություն"),
                            lead: GuideText(ru: "Кто что видит, жалобы, блокировка, пароль", en: "Who sees what, reports, blocking, password", hy: "Ով ինչ է տեսնում, բողոքներ, արգելափակում, գաղտնաբառ"),
                            topics: topics)
    }

    private static func chapter10() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "open", title: GuideText(ru: "Где настройки", en: "Where settings are", hy: "Որտեղ են կարգավորումները"),
            steps: [
                GuideText(ru: "Профиль → шестерёнка вверху.", en: "Profile → the gear at the top.", hy: "Պրոֆիլ → ատամնանիվը վերևում։"),
            ],
            tip: nil, action: .settings
        ))
        topics.append(GuideTopic(
            id: "notify", title: GuideText(ru: "Уведомления и звуки", en: "Notifications and sounds", hy: "Ծանուցումներ և ձայներ"),
            steps: [
                GuideText(ru: "Включайте и выключайте: сообщения, PRISM друзей, «нравится», группы, друзья, итоги.", en: "Toggle messages, friends’ PRISM, likes, groups, friends and recaps.", hy: "Միացրեք և անջատեք՝ հաղորդագրություններ, ընկերների PRISM, «հավանումներ», խմբեր, ընկերներ, ամփոփումներ։"),
                GuideText(ru: "У каждого вида — свой звук: кристалл, звёздная пыль, комета, луна, призма…", en: "Each kind has its own sound: crystal, stardust, comet, moon, prism…", hy: "Յուրաքանչյուր տեսակ ունի իր ձայնը՝ բյուրեղ, աստղափոշի, գիսաստղ, լուսին, պրիզմա…"),
            ],
            tip: nil, action: .sounds
        ))
        topics.append(GuideTopic(
            id: "text", title: GuideText(ru: "Язык и размер текста", en: "Language and text size", hy: "Լեզու և տեքստի չափ"),
            steps: [
                GuideText(ru: "Язык: как в системе, русский или English — меняется сразу.", en: "Language: system, Russian or English — it switches instantly.", hy: "Լեզու՝ ինչպես համակարգում, ռուսերեն կամ English․ փոխվում է անմիջապես։"),
                GuideText(ru: "Размер текста — от мелкого до огромного.", en: "Text size — from small to huge.", hy: "Տեքստի չափ՝ մանրից մինչև հսկայական։"),
            ],
            tip: GuideText(ru: "Язык и размер текста хранятся на телефоне, остальное — в аккаунте.", en: "Language and text size are stored on the phone; everything else belongs to the account.", hy: "Լեզուն և տեքստի չափը պահվում են հեռախոսում, մնացածը՝ հաշվում։"), action: .language
        ))
        topics.append(GuideTopic(
            id: "theme", title: GuideText(ru: "Светлая и тёмная тема", en: "Light and dark theme", hy: "Բաց և մուգ թեմա"),
            steps: [
                GuideText(ru: "Настройки → «Оформление»: «Как в системе», «Тёмное» или «Светлое».", en: "Settings → “Look”: “System default”, “Dark” or “Light”.", hy: "Կարգավորումներ → «Տեսք»՝ «Ինչպես համակարգում», «Մուգ» կամ «Բաց»։"),
            ],
            tip: GuideText(ru: "PRISM, эфиры, звонки, истории и фото на весь экран всегда тёмные — так лучше видно.", en: "PRISM, live streams, calls, stories and full-screen photos are always dark — they look better that way.", hy: "PRISM-ը, եթերները, զանգերը, պատմությունները և լիաէկրան լուսանկարները միշտ մուգ են՝ այդպես ավելի լավ է երևում։"), action: .settings
        ))
        topics.append(GuideTopic(
            id: "screen-reader", title: GuideText(ru: "Экранный диктор", en: "Screen reader", hy: "Էկրանի ընթերցիչ"),
            steps: [
                GuideText(ru: "Memory работает с TalkBack на Android и VoiceOver на iPhone: у кнопок-значков есть названия.", en: "Memory works with TalkBack on Android and VoiceOver on iPhone: icon buttons have names.", hy: "Memory-ն աշխատում է TalkBack-ի (Android) և VoiceOver-ի (iPhone) հետ՝ պատկերակ-կոճակներն ունեն անուններ։"),
                GuideText(ru: "Карточки записей читаются целиком, «нравится» и комментарии — с числом, переключатели — со словами «вкл.» и «выкл.».", en: "Post cards are read as a whole, likes and comments with their counts, switches as on or off.", hy: "Գրառումների քարտերը կարդացվում են ամբողջությամբ, հավանումներն ու մեկնաբանությունները՝ թվով, անջատիչները՝ միացված/անջատված։"),
                GuideText(ru: "Размер текста — Настройки → «Размер текста».", en: "Text size — Settings → “Text size”.", hy: "Տեքստի չափը՝ Կարգավորումներ → «Տեքստի չափը»։"),
            ],
            tip: nil, action: .language
        ))
        topics.append(GuideTopic(
            id: "ideas", title: GuideText(ru: "Идеи и ошибки", en: "Ideas and bugs", hy: "Գաղափարներ և սխալներ"),
            steps: [
                GuideText(ru: "Настройки → «Друзья и идеи»: «Предложить идею» или «Сообщить об ошибке».", en: "Settings → “Friends & ideas”: “Suggest an idea” or “Report a bug”.", hy: "Կարգավորումներ → «Ընկերներ և գաղափարներ»՝ «Առաջարկել գաղափար» կամ «Հայտնել սխալի մասին»։"),
            ],
            tip: nil, action: .growth
        ))
        topics.append(GuideTopic(
            id: "logout", title: GuideText(ru: "Выйти", en: "Signing out", hy: "Դուրս գալ"),
            steps: [
                GuideText(ru: "Последняя строка настроек. Записи и переписка останутся на телефоне и вернутся, когда вы войдёте снова.", en: "The last row in settings. Your posts and chats stay on the phone and come back when you sign in again.", hy: "Կարգավորումների վերջին տողը։ Գրառումները և նամակագրությունը կմնան հեռախոսում և կվերադառնան, երբ նորից մուտք գործեք։"),
            ],
            tip: nil, action: nil
        ))
        return GuideChapter(id: "settings", emoji: "⚙",
                            title: GuideText(ru: "Настройки", en: "Settings", hy: "Կարգավորումներ"),
                            lead: GuideText(ru: "Уведомления, звуки, язык, текст", en: "Notifications, sounds, language, text", hy: "Ծանուցումներ, ձայներ, լեզու, տեքստ"),
                            topics: topics)
    }

    private static func chapter11() -> GuideChapter {
        var topics: [GuideTopic] = []
        topics.append(GuideTopic(
            id: "where-data", title: GuideText(ru: "Где хранятся мои записи?", en: "Where are my posts stored?", hy: "Որտե՞ղ են պահվում իմ գրառումները"),
            steps: [
                GuideText(ru: "Пока на вашем телефоне — отдельно для каждого аккаунта. Они переживают перезапуск приложения.", en: "For now, on your phone — separately for each account. They survive app restarts.", hy: "Առայժմ՝ ձեր հեռախոսում, յուրաքանչյուր հաշվի համար առանձին։ Դրանք պահպանվում են հավելվածը վերագործարկելուց հետո։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "forgot", title: GuideText(ru: "Я забыл пароль", en: "I forgot my password", hy: "Մոռացել եմ գաղտնաբառը"),
            steps: [
                GuideText(ru: "На экране входа — «Забыли пароль?».", en: "On the sign-in screen tap “Forgot password?”.", hy: "Մուտքի էկրանին սեղմեք «Մոռացե՞լ եք գաղտնաբառը»։"),
                GuideText(ru: "Введите логин и один из кодов восстановления, которые вы сохранили при регистрации, — и новый пароль.", en: "Enter your login, one of the recovery codes you saved when signing up, and a new password.", hy: "Մուտքագրեք մուտքանունը, գրանցվելիս պահպանած վերականգնման կոդերից մեկը և նոր գաղտնաբառ։"),
                GuideText(ru: "Нет кодов — «Письмо на почту», если вы подтвердили почту в «Безопасности».", en: "No codes? Choose “Email” if you confirmed your email in Security.", hy: "Կոդեր չկա՞ն — ընտրեք «Նամակ էլ. փոստին», եթե հաստատել եք էլ. փոստը «Անվտանգություն» բաժնում։"),
            ],
            tip: GuideText(ru: "Каждый код работает один раз. Новые коды — в «Безопасности».", en: "Each code works once. New codes are in Security.", hy: "Յուրաքանչյուր կոդ աշխատում է մեկ անգամ։ Նոր կոդերը՝ «Անվտանգություն» բաժնում։"), action: nil
        ))
        topics.append(GuideTopic(
            id: "no-posts", title: GuideText(ru: "Почему я не вижу записи человека?", en: "Why can’t I see someone’s posts?", hy: "Ինչո՞ւ չեմ տեսնում մարդու գրառումները"),
            steps: [
                GuideText(ru: "У него закрытый профиль — добавьте его в друзья.", en: "Their profile is private — add them as a friend.", hy: "Նրա պրոֆիլը փակ է․ ավելացրեք նրան ընկերներին։"),
                GuideText(ru: "Или он в вашем чёрном списке — проверьте настройки.", en: "Or they’re on your block list — check your settings.", hy: "Կամ նա ձեր սև ցուցակում է․ ստուգեք կարգավորումները։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "gif-search", title: GuideText(ru: "Не работает поиск гифок", en: "GIF search doesn’t work", hy: "GIF-երի որոնումը չի աշխատում"),
            steps: [
                GuideText(ru: "Поиску нужен интернет. Встроенные гифки, недавние и свои из галереи работают и без него.", en: "Search needs the internet. Built-in, recent and gallery GIFs work offline.", hy: "Որոնման համար անհրաժեշտ է ինտերնետ։ Ներկառուցված, վերջին և պատկերասրահից ձեր GIF-երն աշխատում են նաև առանց դրա։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "pay", title: GuideText(ru: "Как оплатить товар из витрины?", en: "How do I pay for a product?", hy: "Ինչպե՞ս վճարել ցուցափեղկի ապրանքի համար"),
            steps: [
                GuideText(ru: "Внутри Memory оплаты нет. Напишите продавцу или купите на его сайте.", en: "There is no payment inside Memory. Message the seller or buy on their website.", hy: "Memory-ում վճարում չկա։ Գրեք վաճառողին կամ գնեք նրա կայքում։"),
            ],
            tip: nil, action: nil
        ))
        topics.append(GuideTopic(
            id: "plus-free", title: GuideText(ru: "Что останется бесплатным?", en: "What stays free?", hy: "Ի՞նչն է մնալու անվճար"),
            steps: [
                GuideText(ru: "Всё общение и безопасность: лента, истории, PRISM, чаты, голосовые, друзья, группы, покупки в витринах, приватность, жалобы и удаление аккаунта.", en: "All communication and safety: feed, stories, PRISM, chats, voice notes, friends, groups, buying from shops, privacy, reports and account deletion.", hy: "Ամբողջ հաղորդակցությունը և անվտանգությունը՝ լրահոս, պատմություններ, PRISM, զրույցներ, ձայնային հաղորդագրություններ, ընկերներ, խմբեր, գնումներ ցուցափեղկերից, գաղտնիություն, բողոքներ և հաշվի ջնջում։"),
            ],
            tip: nil, action: .premium
        ))
        topics.append(GuideTopic(
            id: "guide-again", title: GuideText(ru: "Как снова найти этот гид?", en: "How do I find this guide again?", hy: "Ինչպե՞ս կրկին գտնել այս ուղեցույցը"),
            steps: [
                GuideText(ru: "Профиль → шестерёнка → «Гид по Memory».", en: "Profile → gear → “Memory guide”.", hy: "Պրոֆիլ → ատամնանիվ → «Memory ուղեցույց»։"),
            ],
            tip: nil, action: nil
        ))
        return GuideChapter(id: "faq", emoji: "❓",
                            title: GuideText(ru: "Частые вопросы", en: "FAQ", hy: "Հաճախ տրվող հարցեր"),
                            lead: GuideText(ru: "Короткие ответы", en: "Short answers", hy: "Կարճ պատասխաններ"),
                            topics: topics)
    }
}
