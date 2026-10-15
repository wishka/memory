// Сгенерировано scripts/sync_guide.py из docs/guide/guide.json — не править руками.
package ru.wishka.memory.data

/** Главы гида. Текст — в docs/guide/guide.json. */
internal object GuideContent {
    val chapters: List<GuideChapter> by lazy {
        listOf(chapter0(), chapter1(), chapter2(), chapter3(), chapter4(), chapter5(), chapter6(), chapter7(), chapter8(), chapter9(), chapter10(), chapter11())
    }

    private fun chapter0() = GuideChapter(
        "start", "🚀",
        GuideText("Первые шаги", "First steps", "Առաջին քայլեր"),
        GuideText("Вход, вкладки и как здесь всё устроено", "Signing in, tabs and how things work", "Մուտք, ներդիրներ և ինչպես է ամեն ինչ աշխատում"),
        listOf(
            GuideTopic(
                "signup", GuideText("Регистрация и вход", "Signing up and in", "Գրանցում և մուտք"),
                listOf(
                    GuideText("Придумайте логин и пароль — от 8 символов.", "Pick a username and a password of 8 characters or more.", "Ընտրեք մուտքանուն և գաղտնաբառ՝ առնվազն 8 նիշ։"),
                    GuideText("Отметьте три галочки: вам есть 16 лет, вы принимаете соглашение и правила, вы согласны на обработку данных.", "Tick three boxes: you are 16 or older, you accept the terms and rules, and you consent to data processing.", "Նշեք երեք վանդակները՝ դուք 16 տարեկան եք կամ ավելի, ընդունում եք համաձայնագիրը և կանոնները, համաձայն եք տվյալների մշակմանը։"),
                    GuideText("Memory запомнит вас — пароль понадобится, только если вы выйдете.", "Memory remembers you — you only need the password again after signing out.", "Memory-ն կհիշի ձեզ․ գաղտնաբառը պետք կգա միայն, եթե դուրս գաք։"),
                    GuideText("После пяти неверных попыток вход закрывается на 30 секунд.", "After five wrong attempts, signing in is paused for 30 seconds.", "Հինգ սխալ փորձից հետո մուտքը փակվում է 30 վայրկյանով։"),
                ),
                tip = GuideText("Хотите сначала осмотреться — войдите в демо: логин demo, пароль memory2026.", "Want to look around first? Use the demo: username demo, password memory2026.", "Ուզո՞ւմ եք նախ ծանոթանալ․ մտեք դեմո՝ մուտքանուն demo, գաղտնաբառ memory2026։"),
            ),
            GuideTopic(
                "taste", GuideText("Знакомство до регистрации", "A taste before signing up", "Ծանոթություն մինչև գրանցումը"),
                listOf(
                    GuideText("На первом экране выберите три любимых фильма или книги.", "On the first screen, pick three favourite films or books.", "Առաջին էկրանին ընտրեք երեք սիրելի ֆիլմ կամ գիրք։"),
                    GuideText("Сразу появится ваша полка и первый совет от людей с похожим вкусом.", "Your shelf appears right away, with a first tip from people with similar taste.", "Անմիջապես կհայտնվի ձեր դարակը և առաջին խորհուրդը նման ճաշակ ունեցող մարդկանցից։"),
                    GuideText("«Сохранить мою полку» ведёт к регистрации — выбранное перейдёт в новый аккаунт.", "“Save my shelf” takes you to sign-up, and your picks move into the new account.", "«Պահպանել իմ դարակը» կոճակը տանում է գրանցման․ ընտրածը կտեղափոխվի նոր հաշիվ։"),
                ),
            ),
            GuideTopic(
                "tabs", GuideText("Семь вкладок", "The seven tabs", "Յոթ ներդիր"),
                listOf(
                    GuideText("Лента — записи людей: тексты, фото, чек-ины, опросы.", "Feed — people’s posts: text, photos, check-ins, polls.", "Լրահոս՝ մարդկանց գրառումները՝ տեքստեր, լուսանկարներ, չեք-իններ, հարցումներ։"),
                    GuideText("Истории — ваши записи и записи друзей.", "Stories — your posts and your friends’.", "Պատմություններ՝ ձեր և ընկերների գրառումները։"),
                    GuideText("PRISM — короткие вертикальные ролики во весь экран.", "PRISM — short full-screen vertical videos.", "PRISM՝ կարճ ուղղահայաց տեսահոլովակներ ամբողջ էկրանով։"),
                    GuideText("Чаты — переписка, голосовые, вложения.", "Chats — messages, voice notes, attachments.", "Զրույցներ՝ նամակագրություն, ձայնային հաղորդագրություններ, կցորդներ։"),
                    GuideText("Группы — сообщества по интересам и витрины магазинов.", "Groups — communities around interests, and shop showcases.", "Խմբեր՝ ըստ հետաքրքրությունների համայնքներ և խանութների ցուցափեղկեր։"),
                    GuideText("Вместе — общие цели, обмен навыками, клады и вылазки.", "Together — shared goals, skill swaps, caches and outings.", "Միասին՝ ընդհանուր նպատակներ, հմտությունների փոխանակում, գանձեր և արշավներ։"),
                    GuideText("Профиль — ваша страница, полка, студия стикеров и настройки.", "Profile — your page, shelf, sticker studio and settings.", "Պրոֆիլ՝ ձեր էջը, դարակը, ստիկերների ստուդիան և կարգավորումները։"),
                ),
                tip = GuideText("Подпись видна у выбранной вкладки; на остальных — только значок.", "Only the selected tab shows its name; the others show just an icon.", "Անվանումը երևում է միայն ընտրված ներդիրի մոտ, մյուսների մոտ՝ միայն պատկերակը։"),
            ),
            GuideTopic(
                "back", GuideText("Как вернуться назад", "Going back", "Ինչպես վերադառնալ"),
                listOf(
                    GuideText("Жест или кнопка «назад» закрывает то, что открыто последним: фото, обсуждение, переписку, страницу человека, группу.", "The back gesture or button closes whatever opened last: a photo, comments, a chat, a person’s page, a group.", "«Հետ» ժեստը կամ կոճակը փակում է վերջին բացվածը՝ լուսանկարը, քննարկումը, զրույցը, մարդու էջը, խումբը։"),
                    GuideText("Со всех вкладок «назад» ведёт в Ленту, из Ленты — выход из приложения.", "From any tab, back leads to the Feed; from the Feed it leaves the app.", "Բոլոր ներդիրներից «Հետ»-ը տանում է Լրահոս, իսկ Լրահոսից՝ դուրս է գալիս հավելվածից։"),
                ),
            ),
            GuideTopic(
                "nudges", GuideText("Уведомления о людях", "Notifications about people", "Ծանուցումներ մարդկանց մասին"),
                listOf(
                    GuideText("Memory пишет о важном: подруга отметила фильм, который вы обсуждали; в группе новый участник; готовы итоги месяца.", "Memory tells you what matters: a friend checked in to a film you discussed, a new member joined your group, your monthly recap is ready.", "Memory-ն տեղեկացնում է կարևորի մասին՝ ընկերուհին նշել է ֆիլմը, որը դուք քննարկել եք, խմբում նոր մասնակից կա, ամսվա ամփոփումը պատրաստ է։"),
                    GuideText("Пока приложение открыто — плашка сверху, нажатие ведёт к событию.", "While the app is open, a banner slides in at the top; tap it to go there.", "Երբ հավելվածը բաց է, վերևում հայտնվում է վահանակ․ սեղմելով՝ կանցնեք իրադարձությանը։"),
                    GuideText("Что присылать и с каким звуком — в настройках уведомлений и звуков.", "Choose what to receive and which sound to play in notification and sound settings.", "Ինչ ստանալ և ինչ ձայնով՝ ընտրեք ծանուցումների և ձայների կարգավորումներում։"),
                ),
                action = GuideAction.Notifications,
            ),
        ),
    )

    private fun chapter1() = GuideChapter(
        "feed", "📰",
        GuideText("Лента и записи", "Feed and posts", "Լրահոս և գրառումներ"),
        GuideText("Публикации, чек-ины, опросы, комментарии", "Posts, check-ins, polls, comments", "Հրապարակումներ, չեք-իններ, հարցումներ, մեկնաբանություններ"),
        listOf(
            GuideTopic(
                "post", GuideText("Новая запись", "A new post", "Նոր գրառում"),
                listOf(
                    GuideText("Нажмите на поле вверху Ленты и напишите текст.", "Tap the field at the top of the Feed and write something.", "Սեղմեք Լրահոսի վերևի դաշտին և գրեք տեքստը։"),
                    GuideText("Добавьте фото или видео — из галереи или с камеры, до 10 на запись.", "Add photos or videos from the gallery or camera, up to 10 per post.", "Ավելացրեք լուսանկար կամ տեսանյութ պատկերասրահից կամ տեսախցիկից՝ մինչև 10 մեկ գրառման մեջ։"),
                    GuideText("Можно приложить свой стикер, гифку, музыку, опрос или чек-ин.", "You can attach your own sticker, a GIF, music, a poll or a check-in.", "Կարող եք կցել ձեր ստիկերը, GIF, երաժշտություն, հարցում կամ չեք-ին։"),
                    GuideText("Нажмите «Опубликовать».", "Tap “Post”.", "Սեղմեք «Հրապարակել»։"),
                ),
                action = GuideAction.Feed,
            ),
            GuideTopic(
                "scenarios", GuideText("Сценарии: фильм, пробежка, книга", "Shortcuts: film, run, book", "Սցենարներ՝ ֆիլմ, վազք, գիրք"),
                listOf(
                    GuideText("Над полем записи — «Посмотрел фильм», «Пробежка», «Книга», «Я здесь».", "Above the post field: “Watched a movie”, “Run”, “Book”, “I’m here”.", "Գրառման դաշտի վերևում՝ «Դիտեցի ֆիլմ», «Վազք», «Գիրք», «Ես այստեղ եմ»։"),
                    GuideText("Сценарий сразу открывает чек-ин с нужными полями.", "A shortcut opens a check-in with the right fields.", "Սցենարն անմիջապես բացում է չեք-ինը՝ անհրաժեշտ դաշտերով։"),
                    GuideText("После публикации видно, что изменилось: полка, вклад в общие цели, план недели.", "After posting you see what changed: your shelf, your share in shared goals, your weekly plan.", "Հրապարակելուց հետո երևում է, թե ինչ է փոխվել՝ դարակը, ներդրումը ընդհանուր նպատակներում, շաբաթվա պլանը։"),
                ),
            ),
            GuideTopic(
                "checkin", GuideText("Чек-ины", "Check-ins", "Չեք-իններ"),
                listOf(
                    GuideText("20 занятий: фильм, книга, бег, йога, кофе, концерт, путешествие, «я здесь», настроение и другие.", "20 activities: film, book, running, yoga, coffee, concert, travel, “I’m here”, mood and more.", "20 զբաղմունք՝ ֆիլմ, գիրք, վազք, յոգա, սուրճ, համերգ, ճամփորդություն, «ես այստեղ եմ», տրամադրություն և այլն։"),
                    GuideText("Укажите, что именно и сколько: километры, минуты, страницы.", "Say what exactly and how much: kilometres, minutes, pages.", "Նշեք, թե կոնկրետ ինչ և որքան՝ կիլոմետրեր, րոպեներ, էջեր։"),
                    GuideText("Фильмам и книгам можно поставить оценку 1–5, книге — статус: хочу, читаю, прочитал.", "Rate films and books 1–5; give a book a status: want to read, reading, finished.", "Ֆիլմերին և գրքերին կարող եք գնահատական տալ 1–5, գրքին՝ կարգավիճակ՝ ուզում եմ կարդալ, կարդում եմ, կարդացել եմ։"),
                    GuideText("Для пробежки укажите время — Memory посчитает темп.", "For a run, enter the time and Memory works out your pace.", "Վազքի համար նշեք ժամանակը, և Memory-ն կհաշվի տեմպը։"),
                    GuideText("Своё занятие: название, эмодзи и единица. Долгое нажатие на своё — удалить.", "Your own activity: a name, an emoji and a unit. Long-press your own to delete it.", "Ձեր զբաղմունքը՝ անվանում, էմոջի և միավոր։ Ձերը ջնջելու համար՝ երկար սեղմեք։"),
                ),
            ),
            GuideTopic(
                "poll", GuideText("Опросы", "Polls", "Հարցումներ"),
                listOf(
                    GuideText("От 2 до 10 вариантов, один или несколько ответов.", "2 to 10 options, single or multiple choice.", "2-ից 10 տարբերակ, մեկ կամ մի քանի պատասխան։"),
                    GuideText("Голос можно поменять, пока опрос открыт.", "You can change your vote while the poll is open.", "Ձայնը կարող եք փոխել, քանի դեռ հարցումը բաց է։"),
                    GuideText("Автор завершает опрос — итоги видны всем.", "The author closes the poll and everyone sees the results.", "Հեղինակն ավարտում է հարցումը, և արդյունքները տեսանելի են բոլորին։"),
                ),
                tip = GuideText("Опросы работают в Ленте, в группах и в переписке.", "Polls work in the Feed, in groups and in chats.", "Հարցումներն աշխատում են Լրահոսում, խմբերում և զրույցներում։"),
            ),
            GuideTopic(
                "gif", GuideText("Гифки", "GIFs", "GIF-եր"),
                listOf(
                    GuideText("Встроенный набор Memory, недавние и свои из галереи.", "Memory’s built-in set, your recent ones, and your own from the gallery.", "Memory-ի ներկառուցված հավաքածուն, վերջին օգտագործվածները և ձերը՝ պատկերասրահից։"),
                    GuideText("Поиск по всем гифкам GIPHY — по слову.", "Search all of GIPHY by keyword.", "Որոնում GIPHY-ի բոլոր GIF-երում՝ ըստ բառի։"),
                ),
            ),
            GuideTopic(
                "like", GuideText("«Нравится» и комментарии", "Likes and comments", "«Հավանել» և մեկնաբանություններ"),
                listOf(
                    GuideText("Сердечко под записью — «нравится»: вспышка искры и кристальное сердце.", "The heart under a post is a like — a spark and a crystal heart.", "Գրառման տակի սրտիկը՝ «հավանել»․ կայծի բռնկում և բյուրեղյա սիրտ։"),
                    GuideText("Нажмите на комментарии, чтобы открыть обсуждение.", "Tap the comment count to open the discussion.", "Սեղմեք մեկնաբանություններին՝ քննարկումը բացելու համար։"),
                    GuideText("Долгое нажатие на комментарий — ответить, скопировать, открыть автора, пожаловаться или удалить свой.", "Long-press a comment to reply, copy, open the author, report it, or delete your own.", "Երկար սեղմեք մեկնաբանության վրա՝ պատասխանելու, պատճենելու, հեղինակին բացելու, բողոքելու կամ ձերը ջնջելու համար։"),
                    GuideText("Ответить можно своим стикером — кнопка слева от поля.", "Reply with your own sticker using the button left of the field.", "Կարող եք պատասխանել ձեր ստիկերով՝ դաշտից ձախ գտնվող կոճակով։"),
                ),
            ),
            GuideTopic(
                "activity", GuideText("Колокольчик: кто оценил и упомянул", "The bell: who liked and mentioned you", "Զանգակ․ ով հավանեց և նշեց ձեզ"),
                listOf(
                    GuideText("В «Ленте» вверху — колокольчик. Точка на нём — сколько нового.", "At the top of the Feed there’s a bell. The badge shows how much is new.", "«Լրահոսի» վերևում զանգակ է։ Նրա վրայի կետը ցույց է տալիս՝ որքան նոր բան կա։"),
                    GuideText("Внутри — кто оценил и прокомментировал ваши записи, упомянул вас, позвал в друзья или начал эфир.", "Inside: who liked or commented on your posts, mentioned you, sent a friend request or went live.", "Ներսում՝ ով հավանեց կամ մեկնաբանեց ձեր գրառումները, նշեց ձեզ, ընկերության հայտ ուղարկեց կամ եթեր սկսեց։"),
                    GuideText("Заявки в друзья можно принять прямо там. Нажатие на событие ведёт к записи или человеку.", "You can accept friend requests right there. Tapping an event opens the post or person.", "Ընկերության հայտերը կարող եք ընդունել հենց այնտեղ։ Իրադարձության վրա սեղմելը տանում է գրառմանը կամ մարդուն։"),
                ),
                tip = GuideText("Несколько «нравится» к одной записи складываются в одну строку.", "Several likes on one post are combined into a single line.", "Մեկ գրառման մի քանի հավանումները միավորվում են մեկ տողում։"),
                action = GuideAction.Feed,
            ),
            GuideTopic(
                "search", GuideText("Поиск, #теги и @упоминания", "Search, #tags and @mentions", "Որոնում, #պիտակներ և @նշումներ"),
                listOf(
                    GuideText("Лупа в «Ленте» — поиск людей, записей, групп и тегов.", "The magnifier in the Feed searches people, posts, groups and tags.", "«Լրահոսի» խոշորացույցը որոնում է մարդկանց, գրառումներ, խմբեր և պիտակներ։"),
                    GuideText("Пишите #тег в записи — по нему записи найдут другие. Нажатие на тег открывает все записи с ним.", "Write a #tag in a post so others can find it. Tapping a tag shows all posts with it.", "Գրառման մեջ գրեք #պիտակ, որ ուրիշները գտնեն այն։ Պիտակի վրա սեղմելը ցույց է տալիս դրանով բոլոր գրառումները։"),
                    GuideText("Наберите @ и начало имени — Memory подскажет человека. Он узнает об упоминании.", "Type @ and the start of a name — Memory suggests the person. They’ll be notified of the mention.", "Գրեք @ և անվան սկիզբը — Memory-ն կառաջարկի մարդուն։ Նա կիմանա նշման մասին։"),
                ),
                action = GuideAction.Feed,
            ),
            GuideTopic(
                "edit-share", GuideText("Изменить запись и поделиться ссылкой", "Edit a post and share a link", "Խմբագրել գրառումը և կիսվել հղումով"),
                listOf(
                    GuideText("«⋯» на своей записи → «Изменить»: поправьте текст или уберите лишние фото. У записи появится пометка «изменено».", "“⋯” on your post → “Edit”: fix the text or remove extra photos. The post gets an “edited” mark.", "Ձեր գրառման «⋯» → «Խմբագրել»՝ ուղղեք տեքստը կամ հեռացրեք ավելորդ լուսանկարները։ Գրառման վրա կհայտնվի «խմբագրված» նշումը։"),
                    GuideText("«⋯» на любой записи → «Поделиться ссылкой» или «Скопировать ссылку».", "“⋯” on any post → “Share link” or “Copy link”.", "Ցանկացած գրառման «⋯» → «Կիսվել հղումով» կամ «Պատճենել հղումը»։"),
                    GuideText("Ссылка открывает запись в Memory, а без приложения — подскажет, как его поставить. Ссылку на профиль — «⋯» на странице человека.", "The link opens the post in Memory, or explains how to install the app. For a profile link — “⋯” on the person’s page.", "Հղումը բացում է գրառումը Memory-ում, իսկ առանց հավելվածի՝ կբացատրի, ինչպես տեղադրել այն։ Պրոֆիլի հղումը՝ մարդու էջի «⋯»։"),
                ),
                action = GuideAction.Feed,
            ),
            GuideTopic(
                "repost", GuideText("Репост и «Отправить в чат»", "Repost and “Send to chat”", "Վերահրապարակում և «Ուղարկել զրույցին»"),
                listOf(
                    GuideText("Под записью — значок репоста: запись появится у вас в ленте карточкой, можно добавить свои слова.", "Under a post is the repost icon: it appears in your feed as a card, and you can add your own words.", "Գրառման տակ վերահրապարակման պատկերակն է․ այն կհայտնվի ձեր լրահոսում քարտով, կարող եք ավելացնել ձեր խոսքերը։"),
                    GuideText("Самолётик справа — отправить запись кому-то в переписку.", "The paper plane on the right sends the post to someone’s chat.", "Աջ կողմի ինքնաթիռիկը՝ գրառումն ուղարկել որևէ մեկի զրույցին։"),
                    GuideText("Нажатие на карточку внутри репоста открывает оригинал.", "Tapping the card inside a repost opens the original.", "Վերահրապարակման ներսի քարտին սեղմելով՝ կբացվի բնօրինակը։"),
                ),
                tip = GuideText("Записями закрытого профиля поделиться нельзя.", "Posts from a private profile can’t be shared.", "Փակ պրոֆիլի գրառումներով հնարավոր չէ կիսվել։"),
                action = GuideAction.Feed,
            ),
            GuideTopic(
                "photos", GuideText("Фото и видео на весь экран", "Full-screen photos and videos", "Լուսանկարներ և տեսանյութեր ամբողջ էկրանով"),
                listOf(
                    GuideText("Нажмите на фото или видео — откроется на весь экран.", "Tap a photo or video to open it full screen.", "Սեղմեք լուսանկարին կամ տեսանյութին, և այն կբացվի ամբողջ էկրանով։"),
                    GuideText("Двумя пальцами — увеличить, двойное касание — увеличить в этой точке.", "Pinch to zoom; double-tap to zoom in at that spot.", "Երկու մատով՝ մեծացնել, կրկնակի հպումով՝ մեծացնել այդ կետում։"),
                ),
            ),
            GuideTopic(
                "place-tags", GuideText("Место и отметки на фото", "Place and photo tags", "Վայր և նշումներ լուսանկարներում"),
                listOf(
                    GuideText("Значок глобуса в новой записи — добавить место: название и, по желанию, точку «где я сейчас».", "The globe icon in a new post adds a place: a name and, optionally, a “where I am now” pin.", "Նոր գրառման գլոբուսի պատկերակը՝ ավելացնել վայր․ անվանում և, ցանկության դեպքում, «որտեղ եմ հիմա» կետ։"),
                    GuideText("Нажмите на добавленное фото, коснитесь человека и выберите друга — так он будет отмечен.", "Tap an added photo, touch a person and choose a friend to tag them.", "Սեղմեք ավելացված լուսանկարին, հպեք մարդուն և ընտրեք ընկեր՝ նրան նշելու համար։"),
                    GuideText("Записи, где отметили вас, — в профиле, «Отметки на фото».", "Posts where you’re tagged are in your profile under “Photo tags”.", "Գրառումները, որտեղ ձեզ նշել են, պրոֆիլում են՝ «Նշումներ լուսանկարներում»։"),
                ),
                tip = GuideText("Отмечать можно только друзей.", "You can only tag friends.", "Կարելի է նշել միայն ընկերներին։"),
                action = GuideAction.Feed,
            ),
            GuideTopic(
                "pins", GuideText("Закладки и витрина профиля", "Saved posts and profile showcase", "Էջանիշներ և պրոֆիլի ցուցափեղկ"),
                listOf(
                    GuideText("Закладка сохраняет запись в «Сохранённое» в профиле.", "Bookmark a post to keep it in “Saved” on your profile.", "Էջանիշը պահպանում է գրառումը պրոֆիլի «Պահպանված» բաժնում։"),
                    GuideText("«⋯» на своей записи — «Закрепить в профиле»: до трёх записей встают на витрину.", "“⋯” on your own post → “Pin to profile”: up to three posts go on your showcase.", "«⋯» ձեր գրառման վրա → «Ամրացնել պրոֆիլում»․ մինչև երեք գրառում կհայտնվի ցուցափեղկին։"),
                ),
                action = GuideAction.Saved,
            ),
            GuideTopic(
                "memory", GuideText("«В этот день»", "“On this day”", "«Այս օրը»"),
                listOf(
                    GuideText("В Ленте появляется ваша запись год, месяц или неделю назад.", "The Feed shows your post from a year, a month or a week ago.", "Լրահոսում հայտնվում է ձեր գրառումը մեկ տարի, ամիս կամ շաբաթ առաջ։"),
                ),
            ),
        ),
    )

    private fun chapter2() = GuideChapter(
        "stories", "🌙",
        GuideText("Истории", "Stories", "Պատմություններ"),
        GuideText("Истории на сутки, «Актуальное», близкие друзья и эфиры", "Stories for a day, highlights, close friends and live streams", "Պատմություններ մեկ օրով, ընտրված, մտերիմ ընկերներ և եթերներ"),
        listOf(
            GuideTopic(
                "view", GuideText("Смотреть истории", "Watching stories", "Դիտել պատմությունները"),
                listOf(
                    GuideText("Вверху «Историй» — кружки друзей. Цветное кольцо — есть новые, серое — всё просмотрено, зелёное — история «для близких».", "At the top of Stories are friends’ circles. A colored ring means new stories, grey — all seen, green — a close-friends story.", "«Պատմություններ»-ի վերևում ընկերների շրջանակներն են։ Գունավոր օղակ՝ նորեր կան, մոխրագույն՝ բոլորը դիտված են, կանաչ՝ «մտերիմների համար» պատմություն։"),
                    GuideText("Касание справа — дальше, слева — назад, удержание — пауза. Истории идут по кругу от друга к другу.", "Tap the right side to go forward, the left to go back, hold to pause. Stories run from one friend to the next.", "Հպեք աջ կողմին՝ առաջ, ձախին՝ հետ, պահեք՝ դադար։ Պատմությունները հերթով անցնում են ընկերից ընկեր։"),
                    GuideText("Внизу — ответ: он придёт автору в переписку вместе с самой историей. Сердечко — тоже ответ.", "Reply at the bottom: it goes to the author’s chat together with the story. The heart is a reply too.", "Ներքևում՝ պատասխան․ այն կգնա հեղինակի զրույցին՝ պատմության հետ միասին։ Սրտիկը նույնպես պատասխան է։"),
                ),
                action = GuideAction.Stories,
            ),
            GuideTopic(
                "new", GuideText("Новая история", "A new story", "Նոր պատմություն"),
                listOf(
                    GuideText("Нажмите на свой кружок с «+»: фото или видео, или просто текст на цветном фоне.", "Tap your own circle with “+”: a photo or video, or just text on a colored background.", "Սեղմեք ձեր «+»-ով շրջանակին՝ լուսանկար կամ տեսանյութ, կամ պարզապես տեքստ գունավոր ֆոնի վրա։"),
                    GuideText("Выберите, кому: «Все друзья» или «Близкие друзья».", "Choose who sees it: “All friends” or “Close friends”.", "Ընտրեք՝ ում համար․ «Բոլոր ընկերները» կամ «Մտերիմ ընկերներ»։"),
                    GuideText("История видна сутки, потом остаётся только у вас — в архиве.", "A story is visible for a day, then stays only with you, in the archive.", "Պատմությունը տեսանելի է մեկ օր, հետո մնում է միայն ձեզ մոտ՝ արխիվում։"),
                ),
                action = GuideAction.Stories,
            ),
            GuideTopic(
                "viewers", GuideText("Кто посмотрел", "Who viewed", "Ով է դիտել"),
                listOf(
                    GuideText("Откройте свою историю и нажмите «Просмотры» внизу — там все, кто её видел, и кто поставил сердечко.", "Open your story and tap “Views” at the bottom — everyone who saw it, and who sent a heart.", "Բացեք ձեր պատմությունը և սեղմեք ներքևի «Դիտումներ»-ին՝ բոլորը, ով տեսել է, և ով սրտիկ է ուղարկել։"),
                    GuideText("В меню «⋯» своей истории — «Удалить историю» и «В «Актуальное»».", "Your story’s “⋯” menu has “Delete story” and “Add to highlights”.", "Ձեր պատմության «⋯» ընտրացանկում՝ «Ջնջել պատմությունը» և «Ավելացնել ընտրվածին»։"),
                ),
                action = GuideAction.Stories,
            ),
            GuideTopic(
                "highlights", GuideText("«Актуальное»", "Highlights", "Ընտրված"),
                listOf(
                    GuideText("Подборки историй в профиле — они не исчезают через сутки.", "Story collections on your profile — they don’t disappear after a day.", "Պատմությունների հավաքածուներ պրոֆիլում՝ մեկ օր անց չեն անհետանում։"),
                    GuideText("Профиль → «Новая подборка»: дайте название и выберите истории из архива. Долгое нажатие на подборку — изменить или удалить.", "Profile → “New collection”: give it a name and pick stories from the archive. Long-press a collection to edit or delete it.", "Պրոֆիլ → «Նոր հավաքածու»՝ անվանեք և ընտրեք պատմություններ արխիվից։ Երկար սեղմեք հավաքածուին՝ խմբագրելու կամ ջնջելու համար։"),
                ),
                tip = GuideText("«Актуальное» видят те же, кто видит ваши истории, — друзья.", "Highlights are seen by the same people who see your stories — your friends.", "Ընտրվածը տեսնում են նույն մարդիկ, ովքեր տեսնում են ձեր պատմությունները՝ ընկերները։"),
                action = GuideAction.Profile,
            ),
            GuideTopic(
                "close-friends", GuideText("Близкие друзья", "Close friends", "Մտերիմ ընկերներ"),
                listOf(
                    GuideText("В новой истории выберите «Близкие друзья» → «Изменить список» и отметьте людей.", "In a new story choose “Close friends” → “Edit list” and pick people.", "Նոր պատմության մեջ ընտրեք «Մտերիմ ընկերներ» → «Խմբագրել ցանկը» և նշեք մարդկանց։"),
                    GuideText("Истории «для близких» видят только они; у них кольцо зелёное.", "Close-friends stories are seen only by them; they get a green ring.", "«Մտերիմների համար» պատմությունները տեսնում են միայն նրանք․ օղակը կանաչ է։"),
                ),
                tip = GuideText("Никто не узнает, в списке он или нет.", "Nobody is told whether they’re on the list.", "Ոչ ոք չի իմանա՝ ցանկում է, թե ոչ։"),
                action = GuideAction.Stories,
            ),
            GuideTopic(
                "live", GuideText("Прямой эфир", "Live stream", "Ուղիղ եթեր"),
                listOf(
                    GuideText("«Истории» → «Эфир»: назовите эфир, выберите, кто видит, и нажмите «Начать».", "Stories → “Live”: name the stream, choose who can see it and tap “Start”.", "«Պատմություններ» → «Եթեր»՝ անվանեք եթերը, ընտրեք, ով է տեսնում, և սեղմեք «Սկսել»։"),
                    GuideText("Друзья получат уведомление. Зрители пишут в чат эфира и шлют сердечки.", "Friends are notified. Viewers write in the live chat and send hearts.", "Ընկերները ծանուցում կստանան։ Դիտողները գրում են եթերի զրուցարանում և սրտիկներ ուղարկում։"),
                    GuideText("Эфиры друзей — кружками с меткой «В ЭФИРЕ» над историями.", "Friends’ streams appear as circles marked LIVE above the stories.", "Ընկերների եթերները՝ «ԵԹԵՐ» նշումով շրջանակներ պատմությունների վերևում։"),
                ),
                tip = GuideText("Эфир не сохраняется: он есть, пока идёт. Пока сервера нет, видео видит только ведущий — это демо.", "Streams aren’t saved — they exist only while live. Until the server is ready, only the host sees the video — it’s a demo.", "Եթերը չի պահպանվում՝ այն կա, քանի դեռ ընթանում է։ Քանի դեռ սերվեր չկա, տեսանյութը տեսնում է միայն վարողը — սա դեմո է։"),
                action = GuideAction.Stories,
            ),
            GuideTopic(
                "friends-posts", GuideText("Записи друзей", "Friends’ posts", "Ընկերների գրառումները"),
                listOf(
                    GuideText("Ниже историй — записи только друзей, без чужих людей. Кнопки с именами оставляют одного человека.", "Below the stories are posts from friends only. Name chips filter to one person.", "Պատմություններից ներքև՝ միայն ընկերների գրառումները։ Անուններով կոճակները թողնում են մեկ մարդու։"),
                ),
                action = GuideAction.Stories,
            ),
        ),
    )

    private fun chapter3() = GuideChapter(
        "prism", "💎",
        GuideText("PRISM", "PRISM", "PRISM"),
        GuideText("Короткие ролики: смотреть, снимать, монтировать", "Short videos: watch, shoot, edit", "Կարճ տեսահոլովակներ՝ դիտել, նկարահանել, մոնտաժել"),
        listOf(
            GuideTopic(
                "watch", GuideText("Смотреть", "Watching", "Դիտել"),
                listOf(
                    GuideText("Листайте вверх и вниз — ролики во весь экран.", "Swipe up and down through full-screen videos.", "Թերթեք վեր և վար՝ տեսահոլովակներն ամբողջ էկրանով են։"),
                    GuideText("Нажатие — пауза, двойное нажатие — «нравится».", "Tap to pause, double-tap to like.", "Հպում՝ դադար, կրկնակի հպում՝ «հավանել»։"),
                    GuideText("Справа — «нравится», комментарии, «Поделиться» и «Сохранить».", "On the right: like, comments, “Share” and “Save”.", "Աջ կողմում՝ «հավանել», մեկնաբանություններ, «Կիսվել» և «Պահպանել»։"),
                ),
                action = GuideAction.Prism,
            ),
            GuideTopic(
                "shoot", GuideText("Снять свой ролик", "Shooting your own", "Նկարահանել ձեր տեսահոլովակը"),
                listOf(
                    GuideText("Нажмите «+» вверху справа.", "Tap “+” in the top right.", "Սեղմեք «+» վերևում՝ աջ կողմում։"),
                    GuideText("Камера: передняя или задняя, вспышка, таймер 3 или 10 секунд, длина 15, 30 или 60 секунд.", "Camera: front or back, flash, a 3 or 10 second timer, 15, 30 or 60 seconds long.", "Տեսախցիկ՝ առջևի կամ հետևի, լուսաթարթիչ, ժամաչափ 3 կամ 10 վայրկյան, տևողություն 15, 30 կամ 60 վայրկյան։"),
                    GuideText("Запись можно ставить на паузу и продолжать.", "You can pause recording and carry on.", "Տեսագրումը կարող եք դադարեցնել և շարունակել։"),
                    GuideText("Или возьмите видео из галереи.", "Or pick a video from your gallery.", "Կամ վերցրեք տեսանյութ պատկերասրահից։"),
                ),
                tip = GuideText("Выберите музыку до съёмки — она будет звучать во время записи и сразу встанет в редактор.", "Choose music before shooting — it plays while you record and is already in the editor.", "Ընտրեք երաժշտությունը մինչև նկարահանումը․ այն կհնչի տեսագրման ընթացքում և անմիջապես կհայտնվի խմբագրիչում։"),
                action = GuideAction.NewPrism,
            ),
            GuideTopic(
                "edit", GuideText("Редактор", "The editor", "Խմբագրիչ"),
                listOf(
                    GuideText("Обрезка до минуты и скорость от 0.5× до 3×.", "Trim to a minute and set speed from 0.5× to 3×.", "Կտրում մինչև մեկ րոպե և արագություն 0.5×-ից 3×։"),
                    GuideText("Кадр 9:16, 4:5, 1:1 или оригинал; поворот и отражение.", "Frame 9:16, 4:5, 1:1 or original; rotate and flip.", "Կադր 9:16, 4:5, 1:1 կամ բնօրինակ, պտտում և հայելային արտացոլում։"),
                    GuideText("12 фильтров и коррекция: яркость, контраст, насыщенность, тепло.", "12 filters and adjustments: brightness, contrast, saturation, warmth.", "12 ֆիլտր և ուղղում՝ պայծառություն, կոնտրաստ, հագեցվածություն, ջերմություն։"),
                    GuideText("Текст с цветом, фоном или свечением — двигайте его пальцем.", "Text with colour, a background or a glow — drag it with your finger.", "Տեքստ՝ գույնով, ֆոնով կամ լուսարձակումով․ տեղաշարժեք այն մատով։"),
                    GuideText("Стикеры, музыка с выбором начала, громкость видео и музыки, обложка.", "Stickers, music with a chosen start point, video and music volume, a cover.", "Ստիկերներ, երաժշտություն՝ սկզբնակետի ընտրությամբ, տեսանյութի և երաժշտության ձայնի ուժգնություն, շապիկ։"),
                ),
            ),
            GuideTopic(
                "publish", GuideText("Публикация", "Publishing", "Հրապարակում"),
                listOf(
                    GuideText("Добавьте подпись и опубликуйте — ролик соберётся в обычный видеофайл.", "Add a caption and publish — the video is rendered into a regular video file.", "Ավելացրեք նկարագրություն և հրապարակեք․ տեսահոլովակը կհավաքվի սովորական տեսաֆայլի։"),
                    GuideText("Свой ролик можно удалить — корзина справа.", "Delete your own video with the bin on the right.", "Ձեր տեսահոլովակը կարող եք ջնջել՝ աջ կողմի աղբարկղով։"),
                ),
            ),
            GuideTopic(
                "library", GuideText("Медиатека: музыка для роликов", "Library: music for videos", "Մեդիադարան՝ երաժշտություն տեսահոլովակների համար"),
                listOf(
                    GuideText("Вкладка «Медиатека» рядом с «PRISM».", "The “Library” tab next to “PRISM”.", "«Մեդիադարան» ներդիրը «PRISM»-ի կողքին է։"),
                    GuideText("Открытая музыка с лицензиями Creative Commons: поиск, жанры, прослушивание.", "Open music under Creative Commons licences: search, genres, listening.", "Բաց երաժշտություն Creative Commons լիցենզիաներով՝ որոնում, ժանրեր, ունկնդրում։"),
                    GuideText("Понравившееся сохраняйте в «Мою музыку», если площадка разрешает скачивание.", "Save tracks you like to “My music” where the source allows downloads.", "Հավանածը պահպանեք «Իմ երաժշտությունը» բաժնում, եթե հարթակը թույլ է տալիս ներբեռնում։"),
                ),
                tip = GuideText("Автор и лицензия трека сами попадают в подпись ролика.", "The track’s author and licence are added to your caption automatically.", "Թրեքի հեղինակն ու լիցենզիան ինքնաբերաբար ավելանում են տեսահոլովակի նկարագրությանը։"),
            ),
        ),
    )

    private fun chapter4() = GuideChapter(
        "chats", "💬",
        GuideText("Чаты", "Chats", "Զրույցներ"),
        GuideText("Переписка, голосовые, вложения, фон", "Messages, voice notes, attachments, backgrounds", "Նամակագրություն, ձայնային հաղորդագրություններ, կցորդներ, ֆոն"),
        listOf(
            GuideTopic(
                "list", GuideText("Список переписок", "The chat list", "Զրույցների ցանկ"),
                listOf(
                    GuideText("Сверху — поиск, у переписок — счётчик непрочитанных.", "Search at the top; unread counts on each chat.", "Վերևում՝ որոնում, զրույցների մոտ՝ չկարդացածների հաշվիչ։"),
                    GuideText("Долгое нажатие на переписку — закрепить вверху, без звука, отметить прочитанной, заблокировать, очистить или удалить.", "Long-press a chat to pin it, mute it, mark it read, block, clear or delete it.", "Երկար սեղմեք զրույցի վրա՝ ամրացնելու վերևում, անձայն դարձնելու, կարդացած նշելու, արգելափակելու, մաքրելու կամ ջնջելու համար։"),
                ),
                action = GuideAction.Chats,
            ),
            GuideTopic(
                "attach", GuideText("Вложения", "Attachments", "Կցորդներ"),
                listOf(
                    GuideText("Скрепка — фото, видео, документы, свои стикеры, гифки, опрос и место на карте.", "The paperclip: photos, videos, documents, your stickers, GIFs, a poll and a location.", "Ամրակ՝ լուսանկարներ, տեսանյութեր, փաստաթղթեր, ձեր ստիկերները, GIF-եր, հարցում և վայր քարտեզի վրա։"),
                    GuideText("Место приходит маленькой картой; нажатие — карта на весь экран и «Открыть в Картах».", "A location arrives as a small map; tap it for a full map and “Open in Maps”.", "Վայրը գալիս է փոքր քարտեզով․ սեղմելով՝ կբացվի քարտեզն ամբողջ էկրանով և «Բացել Քարտեզներում»։"),
                ),
            ),
            GuideTopic(
                "voice", GuideText("Голосовые", "Voice notes", "Ձայնային հաղորդագրություններ"),
                listOf(
                    GuideText("Когда поле пустое, вместо кнопки отправки — микрофон.", "When the field is empty, the send button becomes a microphone.", "Երբ դաշտը դատարկ է, ուղարկելու կոճակի փոխարեն խոսափող է։"),
                    GuideText("Во время записи видно время и волну; можно удалить или отправить. До 5 минут.", "While recording you see the time and a waveform; delete or send. Up to 5 minutes.", "Ձայնագրման ժամանակ երևում են ժամանակը և ալիքը․ կարող եք ջնջել կամ ուղարկել։ Մինչև 5 րոպե։"),
                    GuideText("Нажатие на волну в сообщении перематывает.", "Tap the waveform in a message to seek.", "Հաղորդագրության ալիքին սեղմելով՝ կարող եք հետ կամ առաջ տալ։"),
                ),
            ),
            GuideTopic(
                "message", GuideText("Действия с сообщением", "Message actions", "Գործողություններ հաղորդագրության հետ"),
                listOf(
                    GuideText("Долгое нажатие на сообщение — ответить, переслать, закрепить, удалить у себя или у всех.", "Long-press a message to reply, forward, pin, or delete for you or for everyone.", "Երկար սեղմեք հաղորդագրության վրա՝ պատասխանելու, փոխանցելու, ամրացնելու, ձեզ մոտ կամ բոլորի մոտ ջնջելու համար։"),
                    GuideText("Закреплённое видно полоской под шапкой.", "A pinned message shows as a strip under the header.", "Ամրացվածը երևում է վերնագրի տակ՝ շերտով։"),
                    GuideText("Нажатие на имя собеседника — его страница.", "Tap the other person’s name to open their page.", "Զրուցակցի անվանը սեղմելով՝ կբացվի նրա էջը։"),
                ),
            ),
            GuideTopic(
                "secret", GuideText("Шифрование и исчезающие сообщения", "Encryption and disappearing messages", "Գաղտնագրում և անհետացող հաղորդագրություններ"),
                listOf(
                    GuideText("«⋯» в переписке → «Сквозное шифрование»: сообщения читаете только вы двое.", "“⋯” in a chat → “End-to-end encryption”: only the two of you can read the messages.", "Նամակագրության «⋯» → «Ծայրից ծայր գաղտնագրում»՝ հաղորդագրությունները կարդում եք միայն դուք երկուսով։"),
                    GuideText("«Код безопасности» — 60 цифр. Сверьте с собеседником: совпали — переписку никто не перехватывает.", "The “Safety code” is 60 digits. Compare it with your friend: if it matches, no one is intercepting.", "«Անվտանգության կոդը» 60 թվանշան է։ Համեմատեք զրուցակցի հետ՝ եթե համընկնում է, ոչ ոք չի որսում։"),
                    GuideText("«Исчезающие» — новые сообщения исчезнут у обоих через 5 минут, час, день или неделю.", "“Disappearing” — new messages vanish for both of you after 5 minutes, an hour, a day or a week.", "«Անհետացող»՝ նոր հաղորդագրությունները կանհետանան երկուսի մոտ 5 րոպեից, ժամից, օրից կամ շաբաթից։"),
                ),
                tip = GuideText("Значки у времени: 🔒 — ушло зашифрованным, ⏱ — исчезнет.", "Icons by the time: 🔒 — sent encrypted, ⏱ — will disappear.", "Ժամի կողքի նշանները՝ 🔒 — ուղարկված է գաղտնագրված, ⏱ — կանհետանա։"),
                action = GuideAction.Chats,
            ),
            GuideTopic(
                "backdrop", GuideText("Фон переписки", "Chat background", "Զրույցի ֆոն"),
                listOf(
                    GuideText("Общий фон для всех чатов — в настройках, «Фон переписок».", "One background for all chats: Settings → “Chat backgrounds”.", "Ընդհանուր ֆոն բոլոր զրույցների համար՝ կարգավորումներում, «Զրույցների ֆոն»։"),
                    GuideText("Свой фон для одной переписки — «⋯» в её шапке.", "A background for a single chat: “⋯” in its header.", "Առանձին ֆոն մեկ զրույցի համար՝ «⋯» նրա վերնագրում։"),
                    GuideText("Выбирайте из коллекции — космос, туманность, сияние, кристаллы — или своё фото.", "Pick from the collection — space, nebula, aurora, crystals — or your own photo.", "Ընտրեք հավաքածուից՝ տիեզերք, միգամածություն, բևեռափայլ, բյուրեղներ, կամ ձեր լուսանկարը։"),
                ),
                action = GuideAction.Wallpaper,
            ),
            GuideTopic(
                "calls", GuideText("Звонки", "Calls", "Զանգեր"),
                listOf(
                    GuideText("В переписке вверху — трубка (голосовой звонок) и камера (видеозвонок).", "In a chat, the phone (voice call) and camera (video call) icons are at the top.", "Զրույցում վերևում՝ հեռախոս (ձայնային զանգ) և տեսախցիկ (տեսազանգ)։"),
                    GuideText("Во время звонка: микрофон, камера, смена камеры, громкая связь и красная кнопка — завершить.", "During a call: microphone, camera, switch camera, speaker and the red button to hang up.", "Զանգի ժամանակ՝ խոսափող, տեսախցիկ, տեսախցիկի փոխում, բարձրախոս և կարմիր կոճակը՝ ավարտել։"),
                    GuideText("«Чаты» → «Звонки» — история звонков; нажатие — перезвонить.", "Chats → “Calls” is the call history; tap to call back.", "«Զրույցներ» → «Զանգեր»՝ զանգերի պատմությունը․ սեղմեք՝ հետ զանգելու համար։"),
                ),
                tip = GuideText("Пока сервера нет, звонки — демо: экран и история работают, голос и видео пойдут позже. Кто может звонить — в «Приватности».", "Until the server is ready, calls are a demo: the screen and history work, voice and video come later. Who can call you is in Privacy.", "Քանի դեռ սերվեր չկա, զանգերը դեմո են․ էկրանը և պատմությունն աշխատում են, ձայնն ու տեսանյութը՝ ավելի ուշ։ Ով կարող է զանգել՝ «Գաղտնիություն»-ում։"),
                action = GuideAction.Chats,
            ),
        ),
    )

    private fun chapter5() = GuideChapter(
        "groups", "🪐",
        GuideText("Группы и витрины", "Groups and shops", "Խմբեր և ցուցափեղկեր"),
        GuideText("Сообщества, магазины, оформление", "Communities, shops, styling", "Համայնքներ, խանութներ, ձևավորում"),
        listOf(
            GuideTopic(
                "find", GuideText("Найти и вступить", "Find and join", "Գտնել և միանալ"),
                listOf(
                    GuideText("Ищите по названию или по теме — почти 40 тем.", "Search by name or by topic — nearly 40 topics.", "Որոնեք ըստ անվանման կամ թեմայի՝ մոտ 40 թեմա։"),
                    GuideText("Откройте группу и нажмите «Вступить». Выйти можно в её настройках.", "Open a group and tap “Join”. You can leave from its settings.", "Բացեք խումբը և սեղմեք «Միանալ»։ Դուրս գալ կարող եք նրա կարգավորումներում։"),
                ),
                action = GuideAction.Groups,
            ),
            GuideTopic(
                "create", GuideText("Своя группа", "Your own group", "Ձեր խումբը"),
                listOf(
                    GuideText("«+» вверху вкладки «Группы».", "Tap “+” at the top of the Groups tab.", "«+» «Խմբեր» ներդիրի վերևում։"),
                    GuideText("Название, описание и тема — и «Создать».", "Name, description and topic — then “Create”.", "Անվանում, նկարագրություն և թեմա, ապա՝ «Ստեղծել»։"),
                    GuideText("Шестерёнка на заставке: своя заставка, описание, кто может писать — все или только вы.", "The gear on the cover: your own cover, description, who can post — everyone or only you.", "Ատամնանիվը շապիկի վրա՝ ձեր շապիկը, նկարագրությունը, ով կարող է գրել՝ բոլորը, թե միայն դուք։"),
                    GuideText("«⋯» на записи в группе — закрепить её первой.", "“⋯” on a group post pins it to the top.", "«⋯» խմբի գրառման վրա՝ այն առաջինը ամրացնելու համար։"),
                ),
            ),
            GuideTopic(
                "look", GuideText("Оформление группы", "Styling your group", "Խմբի ձևավորում"),
                listOf(
                    GuideText("Шестерёнка → «Оформление».", "Gear → “Look”.", "Ատամնանիվ → «Ձևավորում»։"),
                    GuideText("Цвет группы — один из семи: в нём кнопки, цены и вкладки.", "A group colour — one of seven — tints buttons, prices and tabs.", "Խմբի գույնը՝ յոթից մեկը․ դրանով են ներկվում կոճակները, գները և ներդիրները։"),
                    GuideText("Фон страницы — цвета, градиенты или своё фото.", "A page background — colours, gradients or your own photo.", "Էջի ֆոն՝ գույներ, գրադիենտներ կամ ձեր լուսանկարը։"),
                ),
                tip = GuideText("Так группа-магазин выглядит в своём стиле.", "That way a shop group looks like your own brand.", "Այսպես խումբ-խանութը կունենա իր սեփական ոճը։"),
            ),
            GuideTopic(
                "about", GuideText("Карточка «О группе»", "The “About” card", "«Խմբի մասին» քարտը"),
                listOf(
                    GuideText("Шестерёнка → «О группе»: подробное описание, сайт, телефон, почта, адрес, часы работы.", "Gear → “About the group”: a longer description, website, phone, email, address, opening hours.", "Ատամնանիվ → «Խմբի մասին»՝ մանրամասն նկարագրություն, կայք, հեռախոս, էլ. փոստ, հասցե, աշխատանքային ժամեր։"),
                    GuideText("Участники видят карточку над записями: нажатие звонит, пишет или открывает карту.", "Members see the card above the posts: tap to call, email or open a map.", "Մասնակիցները քարտը տեսնում են գրառումների վերևում․ սեղմելով՝ կարող են զանգել, գրել կամ բացել քարտեզը։"),
                ),
            ),
            GuideTopic(
                "shop-owner", GuideText("Витрина: как продавать", "Showcase: selling", "Ցուցափեղկ՝ ինչպես վաճառել"),
                listOf(
                    GuideText("В своей группе откройте вкладку «Витрина» и добавьте товар.", "In your group, open the “Showcase” tab and add a product.", "Ձեր խմբում բացեք «Ցուցափեղկ» ներդիրը և ավելացրեք ապրանք։"),
                    GuideText("До пяти фото, название, цена, описание, категория, наличие и ссылка на ваш сайт.", "Up to five photos, a title, price, description, category, stock and a link to your site.", "Մինչև հինգ լուսանկար, անվանում, գին, նկարագրություն, կատեգորիա, առկայություն և հղում ձեր կայքին։"),
                    GuideText("Пустая цена или 0 — «Цена по запросу».", "An empty price or 0 shows “Price on request”.", "Դատարկ գին կամ 0՝ «Գինը՝ ըստ հարցման»։"),
                    GuideText("В карточке товара — «Изменить», «Убрать», «Выше в витрине» и «Ниже».", "In a product card: “Edit”, “Remove”, “Move up” and “Move down”.", "Ապրանքի քարտում՝ «Փոփոխել», «Հեռացնել», «Բարձրացնել ցուցափեղկում» և «Իջեցնել»։"),
                ),
            ),
            GuideTopic(
                "shop-buyer", GuideText("Витрина: как купить", "Showcase: buying", "Ցուցափեղկ՝ ինչպես գնել"),
                listOf(
                    GuideText("Фильтруйте товары по категориям и откройте карточку.", "Filter products by category and open a card.", "Զտեք ապրանքներն ըստ կատեգորիաների և բացեք քարտը։"),
                    GuideText("«Написать продавцу» — откроется переписка, товар уже приложен.", "“Message the seller” opens a chat with the product attached.", "«Գրել վաճառողին»՝ կբացվի զրույց, ապրանքն արդեն կցված է։"),
                    GuideText("«Купить на сайте» — если продавец дал ссылку.", "“Buy on the website” — if the seller added a link.", "«Գնել կայքում»՝ եթե վաճառողը հղում է տրամադրել։"),
                ),
                tip = GuideText("Оплаты внутри Memory нет: о цене, оплате и доставке договаривайтесь с продавцом.", "There is no payment inside Memory: agree on price, payment and delivery with the seller.", "Memory-ում վճարում չկա․ գնի, վճարման և առաքման մասին պայմանավորվեք վաճառողի հետ։"),
            ),
        ),
    )

    private fun chapter6() = GuideChapter(
        "together", "🤝",
        GuideText("Вместе", "Together", "Միասին"),
        GuideText("Делать что-то вместе, а не соревноваться", "Doing things together, not competing", "Ինչ-որ բան անել միասին, ոչ թե մրցել"),
        listOf(
            GuideTopic(
                "goals", GuideText("Цели", "Goals", "Նպատակներ"),
                listOf(
                    GuideText("Общие шкалы сообщества: «Вместе пробежим 1 000 000 км», «10 000 прочитанных книг».", "Shared community counters: “Let’s run 1,000,000 km”, “10,000 books read”.", "Համայնքի ընդհանուր սանդղակներ՝ «Միասին կվազենք 1 000 000 կմ», «10 000 կարդացված գիրք»։"),
                    GuideText("Вступили — и ваши чек-ины засчитываются сами. Вклад можно добавить и вручную.", "Join and your check-ins count automatically. You can also add progress by hand.", "Միացաք, և ձեր չեք-իններն ինքնաբերաբար հաշվվում են։ Ներդրումը կարող եք ավելացնել նաև ձեռքով։"),
                ),
                action = GuideAction.Together,
            ),
            GuideTopic(
                "skills", GuideText("Навыки", "Skills", "Հմտություններ"),
                listOf(
                    GuideText("Банк времени без денег: научили кого-то 30 минут — получили 30 минут на любой навык.", "A time bank without money: teach someone for 30 minutes, get 30 minutes of any skill.", "Ժամանակի բանկ՝ առանց փողի․ ինչ-որ մեկին սովորեցրիք 30 րոպե՝ ստացաք 30 րոպե ցանկացած հմտության համար։"),
                    GuideText("Отметьте, чему научите и чему хотите научиться; первыми идут взаимные обмены.", "Mark what you can teach and what you want to learn; mutual swaps come first.", "Նշեք, թե ինչ կարող եք սովորեցնել և ինչ եք ուզում սովորել․ առաջինը ցուցադրվում են փոխադարձ փոխանակումները։"),
                    GuideText("Договорённость уходит в чат, «Занятие прошло» двигает баланс.", "The arrangement goes to a chat; “Session done” updates the balance.", "Պայմանավորվածությունն անցնում է զրույց, «Դասն անցկացվեց» կոճակը թարմացնում է հաշվեկշիռը։"),
                ),
            ),
            GuideTopic(
                "journeys", GuideText("Дневники", "Journeys", "Օրագրեր"),
                listOf(
                    GuideText("Короткие челленджи по дням: «День 3: учусь жонглировать».", "Short day-by-day challenges: “Day 3: learning to juggle”.", "Կարճ մարտահրավերներ ըստ օրերի՝ «Օր 3․ սովորում եմ ձեռնածություն»։"),
                    GuideText("Выкладывайте процесс — сообщество советует, вы отмечаете полезное.", "Share your progress — the community gives tips and you mark the helpful ones.", "Կիսվեք ընթացքով․ համայնքը խորհուրդներ է տալիս, իսկ դուք նշում եք օգտակարները։"),
                ),
            ),
            GuideTopic(
                "missions", GuideText("Миссии и клады", "Missions and caches", "Առաքելություններ և գանձեր"),
                listOf(
                    GuideText("Миссии — квесты по городу: фото находки встаёт на общую карту там, где вы.", "Missions are city quests: your photo goes on a shared map where you are.", "Առաքելությունները քաղաքային քվեստներ են․ գտածոյի լուսանկարը հայտնվում է ընդհանուր քարտեզի վրա այնտեղ, որտեղ դուք եք։"),
                    GuideText("Клады — спрячьте контейнер с подсказкой. «Нашёл!» можно нажать только в 150 м от клада.", "Caches: hide a container with a clue. “Found it!” works only within 150 m of the cache.", "Գանձեր՝ թաքցրեք տարա հուշումով։ «Գտա՛» կոճակը կարելի է սեղմել միայն գանձից 150 մ հեռավորության վրա։"),
                ),
                tip = GuideText("Найдите три клада — и сможете прятать свои.", "Find three caches and you can hide your own.", "Գտեք երեք գանձ, և կկարողանաք թաքցնել ձերը։"),
            ),
            GuideTopic(
                "outings", GuideText("Вылазки, рисунок, обмен", "Outings, drawing, swaps", "Արշավներ, նկար, փոխանակում"),
                listOf(
                    GuideText("Вылазки — события до 10 человек: грибы, фотопрогулка, велопробег.", "Outings are events for up to 10 people: mushroom picking, photo walks, bike rides.", "Արշավներ՝ իրադարձություններ մինչև 10 հոգու համար՝ սնկահավաք, ֆոտոզբոսանք, հեծանվարշավ։"),
                    GuideText("Рисунок — эстафета: каждый дорисовывает своё и передаёт дальше; готовое можно опубликовать.", "Drawing is a relay: everyone adds something and passes it on; the result can be published.", "Նկար՝ էստաֆետ․ յուրաքանչյուրը լրացնում է իրենը և փոխանցում հաջորդին, պատրաստը կարելի է հրապարակել։"),
                    GuideText("Обмен — книжный клуб, семена и растения, ремонт вещей.", "Swaps: a book club, seeds and plants, repairing things.", "Փոխանակում՝ գրքի ակումբ, սերմեր և բույսեր, իրերի նորոգում։"),
                ),
            ),
            GuideTopic(
                "invite", GuideText("Позвать того, кого нет в Memory", "Invite someone who isn’t on Memory", "Հրավիրել նրան, ով Memory-ում չէ"),
                listOf(
                    GuideText("В рисунке, обмене, вылазке и навыках есть «Позвать» — уйдёт ссылка-приглашение.", "Drawing, swaps, outings and skills have “Invite”, which sends an invite link.", "Նկարում, փոխանակումում, արշավում և հմտություններում կա «Հրավիրել» կոճակը․ կուղարկվի հրավերի հղում։"),
                    GuideText("Ваш код приглашения — в настройках, «Друзья и идеи».", "Your invite code is in Settings → “Friends & ideas”.", "Ձեր հրավերի կոդը՝ կարգավորումներում, «Ընկերներ և գաղափարներ»։"),
                ),
                action = GuideAction.Growth,
            ),
        ),
    )

    private fun chapter7() = GuideChapter(
        "friends", "✨",
        GuideText("Друзья", "Friends", "Ընկերներ"),
        GuideText("Дружба взаимная — по заявке", "Friendship is mutual — by request", "Ընկերությունը փոխադարձ է՝ հայտով"),
        listOf(
            GuideTopic(
                "add", GuideText("Добавить в друзья", "Adding a friend", "Ավելացնել ընկերներին"),
                listOf(
                    GuideText("Откройте страницу человека — нажмите на имя или аватар.", "Open a person’s page by tapping their name or avatar.", "Բացեք մարդու էջը՝ սեղմեք անվանը կամ ավատարին։"),
                    GuideText("«Добавить в друзья» отправит заявку; пока её не приняли, видно «Заявка отправлена».", "“Add friend” sends a request; until it’s accepted you see “Request sent”.", "«Ավելացնել ընկերներին» կոճակը կուղարկի հայտ․ քանի դեռ այն չի ընդունվել, երևում է «Հայտն ուղարկված է»։"),
                    GuideText("Когда человек примет заявку, вы станете друзьями взаимно.", "When they accept, you become friends both ways.", "Երբ մարդն ընդունի հայտը, դուք փոխադարձաբար ընկերներ կդառնաք։"),
                ),
            ),
            GuideTopic(
                "requests", GuideText("Заявки в друзья", "Friend requests", "Ընկերության հայտեր"),
                listOf(
                    GuideText("Входящие — в профиле, «Заявки в друзья»: принять или отклонить.", "Incoming requests are in Profile → “Friend requests”: accept or decline.", "Մուտքայինները՝ պրոֆիլում, «Ընկերության հայտեր»․ ընդունեք կամ մերժեք։"),
                    GuideText("Там же — заявки, которые отправили вы.", "Requests you’ve sent are there too.", "Այնտեղ են նաև ձեր ուղարկած հայտերը։"),
                ),
                action = GuideAction.Requests,
            ),
            GuideTopic(
                "remove", GuideText("Убрать из друзей", "Removing a friend", "Հեռացնել ընկերներից"),
                listOf(
                    GuideText("В списке друзей или на странице человека — «Удалить из друзей», с подтверждением.", "In your friends list or on their page: “Remove from friends”, with a confirmation.", "Ընկերների ցանկում կամ մարդու էջում՝ «Հեռացնել ընկերներից»՝ հաստատումով։"),
                ),
                action = GuideAction.Friends,
            ),
            GuideTopic(
                "why", GuideText("Зачем друзья", "What friends see", "Ինչու են պետք ընկերները"),
                listOf(
                    GuideText("Записи друзей — во вкладке «Истории».", "Friends’ posts appear in Stories.", "Ընկերների գրառումները՝ «Պատմություններ» ներդիրում։"),
                    GuideText("Закрытый профиль видят только друзья.", "Only friends can see a private profile.", "Փակ պրոֆիլը տեսնում են միայն ընկերները։"),
                ),
            ),
        ),
    )

    private fun chapter8() = GuideChapter(
        "profile", "👤",
        GuideText("Профиль", "Profile", "Պրոֆիլ"),
        GuideText("Страница, полка, итоги, студия и музыка", "Your page, shelf, recaps, studio and music", "Էջ, դարակ, ամփոփումներ, ստուդիա և երաժշտություն"),
        listOf(
            GuideTopic(
                "edit", GuideText("Ваша страница", "Your page", "Ձեր էջը"),
                listOf(
                    GuideText("Аватар — фото из галереи или с камеры, с обрезкой по кругу.", "Avatar: a photo from the gallery or camera, cropped to a circle.", "Ավատար՝ լուսանկար պատկերասրահից կամ տեսախցիկից՝ շրջանաձև կտրվածքով։"),
                    GuideText("«О себе», город, день рождения, сайт и интересы.", "“About me”, city, birthday, website and interests.", "«Իմ մասին», քաղաք, ծննդյան օր, կայք և հետաքրքրություններ։"),
                    GuideText("Счётчики записей, друзей и групп открывают свои списки.", "The post, friend and group counters open their lists.", "Գրառումների, ընկերների և խմբերի հաշվիչները բացում են համապատասխան ցանկերը։"),
                ),
                action = GuideAction.Profile,
            ),
            GuideTopic(
                "home", GuideText("Главная профиля", "Profile home", "Պրոֆիլի գլխավոր էջ"),
                listOf(
                    GuideText("Витрина: до трёх закреплённых записей, ваши стикеры и музыка.", "Showcase: up to three pinned posts, your stickers and music.", "Ցուցափեղկ՝ մինչև երեք ամրացված գրառում, ձեր ստիկերները և երաժշտությունը։"),
                    GuideText("Активность за две недели и кристаллы-достижения.", "Two weeks of activity and crystal achievements.", "Ակտիվությունը երկու շաբաթվա ընթացքում և բյուրեղ-նվաճումներ։"),
                    GuideText("Вкладки «Записи · PRISM · Медиа», ваши группы и друзья.", "Tabs “Posts · PRISM · Media”, your groups and friends.", "«Գրառումներ · PRISM · Մեդիա» ներդիրները, ձեր խմբերը և ընկերները։"),
                ),
            ),
            GuideTopic(
                "shelf", GuideText("Полка", "Shelf", "Դարակ"),
                listOf(
                    GuideText("Собирается из чек-инов сама: фильмы с оценками, книги по статусам, спорт, места.", "Built from your check-ins: rated films, books by status, sport, places.", "Ինքնաբերաբար կազմվում է չեք-իններից՝ ֆիլմեր՝ գնահատականներով, գրքեր՝ ըստ կարգավիճակի, սպորտ, վայրեր։"),
                    GuideText("«Для вас» — советы от людей с похожим вкусом и план бега на неделю.", "“For you”: tips from people with similar taste and a weekly running plan.", "«Ձեզ համար»՝ խորհուրդներ նման ճաշակ ունեցող մարդկանցից և վազքի շաբաթական պլան։"),
                    GuideText("«Права» — что вы уже открыли: свои клады, миссии, цели, звание наставника.", "“Abilities”: what you’ve earned — your own caches, missions, goals, the mentor title.", "«Իրավունքներ»՝ ինչ եք արդեն բացել՝ ձեր գանձերը, առաքելությունները, նպատակները, դաստիարակի կոչումը։"),
                ),
                action = GuideAction.Shelf,
            ),
            GuideTopic(
                "recap", GuideText("Итоги месяца и года", "Monthly and yearly recaps", "Ամսվա և տարվա ամփոփում"),
                listOf(
                    GuideText("Картинка на звёздном небе: фильмы, книги, километры, места, записи, «нравится».", "A picture on a starry sky: films, books, kilometres, places, posts, likes.", "Նկար աստղազարդ երկնքի ֆոնին՝ ֆիլմեր, գրքեր, կիլոմետրեր, վայրեր, գրառումներ, «հավանումներ»։"),
                    GuideText("Стрелки листают прошлые периоды.", "Arrows page through earlier periods.", "Սլաքներով կարող եք թերթել նախորդ ժամանակահատվածները։"),
                    GuideText("Опубликуйте в Ленту или Истории, отправьте в чат или сохраните в галерею.", "Post it to the Feed or Stories, send it to a chat or save it to your gallery.", "Հրապարակեք Լրահոսում կամ Պատմություններում, ուղարկեք զրույց կամ պահպանեք պատկերասրահում։"),
                ),
                action = GuideAction.Recap,
            ),
            GuideTopic(
                "studio", GuideText("Студия стикеров", "Sticker studio", "Ստիկերների ստուդիա"),
                listOf(
                    GuideText("Из фото — с формой (круг, сердце, кристалл, звезда), обводкой и подписью.", "From a photo, with a shape (circle, heart, crystal, star), outline and caption.", "Լուսանկարից՝ ձևով (շրջան, սիրտ, բյուրեղ, աստղ), եզրագծով և մակագրությամբ։"),
                    GuideText("Вырезка — объект с фото вырезается сам.", "Cut-out: the subject is lifted out of the photo automatically.", "Կտրվածք՝ օբյեկտն ինքնաբերաբար կտրվում է լուսանկարից։"),
                    GuideText("Рисунок кистью — и покадровый, до 12 кадров.", "Draw with a brush, including frame-by-frame animation, up to 12 frames.", "Նկարում վրձինով, նաև կադր առ կադր՝ մինչև 12 կադր։"),
                    GuideText("Конструктор смайлов и значок с текстом или эмодзи.", "An emoji face builder and a badge with text or an emoji.", "Սմայլիկների կոնստրուկտոր և կրծքանշան՝ տեքստով կամ էմոջիով։"),
                    GuideText("Живые стикеры: эффекты (контур, комикс, объём, блеск) и движение (прыжок, пульс, парение…).", "Live stickers: effects (outline, comic, 3D, shine) and motion (jump, pulse, float…).", "Կենդանի ստիկերներ՝ էֆեկտներ (եզրագիծ, կոմիքս, ծավալ, փայլ) և շարժում (ցատկ, զարկերակ, սավառնում…)։"),
                ),
                tip = GuideText("Собирайте стикеры в коллекции — они станут вкладками при выборе стикера.", "Group stickers into collections — they become tabs in the sticker picker.", "Հավաքեք ստիկերները հավաքածուներում․ դրանք կդառնան ներդիրներ ստիկեր ընտրելիս։"),
                action = GuideAction.Studio,
            ),
            GuideTopic(
                "plus", GuideText("Memory+ и пробный период", "Memory+ and the free trial", "Memory+ և փորձաշրջան"),
                listOf(
                    GuideText("Профиль → карточка «Memory+»: тарифы, цены и что входит.", "Profile → the “Memory+” card: plans, prices and what’s included.", "Պրոֆիլ → «Memory+» քարտը՝ սակագներ, գներ և ինչ է ներառված։"),
                    GuideText("Memory+ — стикеры, фоны, звуки, PRISM до 60 секунд, больше плейлистов и итоги за любой период.", "Memory+: stickers, backgrounds, sounds, PRISM up to 60 seconds, more playlists and recaps for any period.", "Memory+՝ ստիկերներ, ֆոներ, ձայներ, PRISM՝ մինչև 60 վայրկյան, ավելի շատ փլեյլիստներ և ամփոփումներ ցանկացած ժամանակահատվածի համար։"),
                    GuideText("Memory+ Бизнес — всё из Memory+ и витрина без ограничений, фон страницы группы и контакты.", "Memory+ Business: everything in Memory+, plus an unlimited shop, a group page background and contacts.", "Memory+ Բիզնես՝ ամբողջը Memory+-ից, գումարած անսահմանափակ ցուցափեղկ, խմբի էջի ֆոն և կոնտակտներ։"),
                    GuideText("Попробуйте любой тариф 3 дня бесплатно — один раз на аккаунт.", "Try either plan free for 3 days — once per account.", "Փորձեք ցանկացած սակագին 3 օր անվճար՝ մեկ անգամ յուրաքանչյուր հաշվի համար։"),
                    GuideText("Сменить тариф или срок и отменить продление — там же.", "Switch plan or term and cancel renewal in the same place.", "Սակագինը կամ ժամկետը փոխել և երկարաձգումը չեղարկել կարող եք նույն տեղում։"),
                ),
                tip = GuideText("Когда подписка заканчивается, ничего не пропадает: созданное остаётся, нельзя только добавить новое сверх бесплатного.", "When a subscription ends, nothing is lost: what you made stays; you just can’t add more beyond the free limits.", "Երբ բաժանորդագրությունն ավարտվում է, ոչինչ չի կորչում․ ստեղծածը մնում է, պարզապես չեք կարող ավելացնել նորը՝ անվճարից ավելի։"),
                action = GuideAction.Premium,
            ),
            GuideTopic(
                "music", GuideText("Моя музыка", "My music", "Իմ երաժշտությունը"),
                listOf(
                    GuideText("Свои треки (mp3, m4a, flac) и плейлисты: создать, переименовать, поменять порядок.", "Your own tracks (mp3, m4a, flac) and playlists: create, rename, reorder.", "Ձեր թրեքերը (mp3, m4a, flac) և փլեյլիստները՝ ստեղծել, վերանվանել, փոխել հերթականությունը։"),
                    GuideText("Плеер — внизу экрана.", "The player sits at the bottom of the screen.", "Նվագարկիչը էկրանի ներքևում է։"),
                ),
                tip = GuideText("Музыка хранится только на телефоне и видна только вам.", "Music stays on your phone and only you can see it.", "Երաժշտությունը պահվում է միայն հեռախոսում և տեսանելի է միայն ձեզ։"),
                action = GuideAction.Music,
            ),
        ),
    )

    private fun chapter9() = GuideChapter(
        "privacy", "🛡",
        GuideText("Приватность и безопасность", "Privacy and safety", "Գաղտնիություն և անվտանգություն"),
        GuideText("Кто что видит, жалобы, блокировка, пароль", "Who sees what, reports, blocking, password", "Ով ինչ է տեսնում, բողոքներ, արգելափակում, գաղտնաբառ"),
        listOf(
            GuideTopic(
                "who", GuideText("Кто что видит", "Who sees what", "Ով ինչ է տեսնում"),
                listOf(
                    GuideText("Настройки → «Приватность»: кто видит сведения и друзей, кто может писать и комментировать.", "Settings → “Privacy”: who sees your details and friends, who can message and comment.", "Կարգավորումներ → «Գաղտնիություն»՝ ով է տեսնում ձեր տվյալները և ընկերներին, ով կարող է գրել և մեկնաբանել։"),
                    GuideText("Там же — «в сети», день рождения и закрытый профиль.", "Also: online status, birthday and a private profile.", "Այնտեղ են նաև «առցանց» կարգավիճակը, ծննդյան օրը և փակ պրոֆիլը։"),
                    GuideText("Закрытый профиль: записи и чек-ины видят только друзья.", "Private profile: only friends see your posts and check-ins.", "Փակ պրոֆիլ՝ գրառումները և չեք-ինները տեսնում են միայն ընկերները։"),
                ),
                action = GuideAction.Privacy,
            ),
            GuideTopic(
                "recs", GuideText("Персональные рекомендации", "Personal recommendations", "Անհատական առաջարկություններ"),
                listOf(
                    GuideText("Советы «Для вас» строятся по вашим оценкам.", "“For you” tips are based on your ratings.", "«Ձեզ համար» խորհուրդները կազմվում են ձեր գնահատականների հիման վրա։"),
                    GuideText("Их можно выключить: «Приватность» → «Персональные рекомендации».", "Turn them off in “Privacy” → “Personal recommendations”.", "Դրանք կարող եք անջատել՝ «Գաղտնիություն» → «Անհատական առաջարկություններ»։"),
                ),
                action = GuideAction.Privacy,
            ),
            GuideTopic(
                "my-data", GuideText("Скачать мои данные", "Download my data", "Ներբեռնել իմ տվյալները"),
                listOf(
                    GuideText("Настройки → «Приватность» → «Скачать мои данные».", "Settings → “Privacy” → “Download my data”.", "Կարգավորումներ → «Գաղտնիություն» → «Ներբեռնել իմ տվյալները»։"),
                    GuideText("Выберите, класть ли фото, видео и музыку, и место для архива — «Файлы», диск или флешка.", "Choose whether to include photos, videos and music, and where to save the archive — Files, a drive or a USB stick.", "Ընտրեք՝ ներառե՞լ լուսանկարները, տեսանյութերն ու երաժշտությունը, և արխիվի տեղը՝ «Ֆայլեր», սկավառակ կամ ֆլեշ։"),
                    GuideText("В архиве — профиль, записи, переписка, группы, PRISM, полка и настройки. Пароля и кодов там нет.", "The archive has your profile, posts, chats, groups, PRISM, shelf and settings. No password or codes.", "Արխիվում՝ պրոֆիլ, գրառումներ, նամակագրություն, խմբեր, PRISM, դարակ և կարգավորումներ։ Գաղտնաբառ և կոդեր չկան։"),
                ),
                action = GuideAction.Privacy,
            ),
            GuideTopic(
                "crashes", GuideText("Отчёты о сбоях и статистика", "Crash reports and statistics", "Խափանումների հաշվետվություններ և վիճակագրություն"),
                listOf(
                    GuideText("Настройки → «Приватность» → «Сбои и статистика»: два отдельных переключателя, по умолчанию выключены.", "Settings → “Privacy” → “Crashes and statistics”: two separate switches, off by default.", "Կարգավորումներ → «Գաղտնիություն» → «Խափանումներ և վիճակագրություն»՝ երկու առանձին անջատիչ, լռելյայն անջատված։"),
                    GuideText("Отчёт о сбое — что сломалось и где в коде, без ваших записей и переписки.", "A crash report says what broke and where in the code — without your posts or chats.", "Խափանման հաշվետվությունը ցույց է տալիս՝ ինչ է խափանվել և որտեղ կոդում, առանց ձեր գրառումների և նամակագրության։"),
                    GuideText("Статистика — раз в день счётчики вроде «опубликовано записей: 3», без текстов.", "Statistics — once a day, counters like “posts published: 3”, no texts.", "Վիճակագրություն՝ օրը մեկ հաշվիչներ, օրինակ՝ «հրապարակված գրառումներ՝ 3», առանց տեքստերի։"),
                ),
                action = GuideAction.Privacy,
            ),
            GuideTopic(
                "report", GuideText("Пожаловаться", "Reporting", "Բողոքել"),
                listOf(
                    GuideText("«Пожаловаться» есть у записи, комментария, ролика, сообщения и человека.", "“Report” is available on posts, comments, videos, messages and people.", "«Բողոքել» կոճակը կա գրառման, մեկնաբանության, տեսահոլովակի, հաղորդագրության և մարդու մոտ։"),
                    GuideText("Выберите причину и при желании добавьте пару слов и блокировку.", "Pick a reason, and optionally add a note and block them.", "Ընտրեք պատճառը և, ցանկության դեպքում, ավելացրեք մի քանի բառ և արգելափակում։"),
                    GuideText("У вас это сразу скрывается, жалоба уходит модераторам.", "It’s hidden for you right away, and the report goes to moderators.", "Ձեզ մոտ այն անմիջապես թաքցվում է, իսկ բողոքն ուղարկվում է մոդերատորներին։"),
                ),
            ),
            GuideTopic(
                "block", GuideText("Чёрный список", "Blocking", "Սև ցուցակ"),
                listOf(
                    GuideText("«⋯» на странице человека → «Заблокировать».", "“⋯” on a person’s page → “Block”.", "«⋯» մարդու էջում → «Արգելափակել»։"),
                    GuideText("Его записи, комментарии и сообщения больше не видны, писать ему нельзя.", "Their posts, comments and messages disappear, and you can’t message each other.", "Նրա գրառումները, մեկնաբանությունները և հաղորդագրություններն այլևս չեն երևում, և նրան գրել հնարավոր չէ։"),
                    GuideText("Разблокировать — в настройках, «Чёрный список».", "Unblock in Settings → “Blocked”.", "Արգելաբացել կարող եք կարգավորումներում՝ «Սև ցուցակ»։"),
                ),
                action = GuideAction.Blacklist,
            ),
            GuideTopic(
                "password", GuideText("Пароль, восстановление и удаление аккаунта", "Password, recovery and deleting your account", "Գաղտնաբառ, վերականգնում և հաշվի ջնջում"),
                listOf(
                    GuideText("Настройки → «Безопасность»: сменить пароль.", "Settings → “Security”: change your password.", "Կարգավորումներ → «Անվտանգություն»՝ փոխել գաղտնաբառը։"),
                    GuideText("Там же — коды восстановления (новые — по паролю) и почта: подтвердите её кодом из письма.", "Also there: recovery codes (new ones need your password) and email — confirm it with the code from the message.", "Այնտեղ են նաև վերականգնման կոդերը (նորերը՝ գաղտնաբառով) և էլ. փոստը՝ հաստատեք այն նամակի կոդով։"),
                    GuideText("И удалить аккаунт: всё ваше будет стёрто с телефона без возврата.", "And delete your account: everything of yours is erased from the phone for good.", "Եվ ջնջել հաշիվը․ ձեր ամբողջ տվյալներն անվերադարձ կջնջվեն հեռախոսից։"),
                ),
                action = GuideAction.Security,
            ),
            GuideTopic(
                "docs", GuideText("Документы", "Documents", "Փաստաթղթեր"),
                listOf(
                    GuideText("Соглашение, политика конфиденциальности, согласие на обработку, правила сообщества — в настройках, «Документы».", "Terms, privacy policy, data consent and community rules are in Settings → “Documents”.", "Համաձայնագիրը, գաղտնիության քաղաքականությունը, մշակման համաձայնությունը, համայնքի կանոնները՝ կարգավորումներում, «Փաստաթղթեր»։"),
                    GuideText("Когда документы меняются, Memory попросит принять новую версию.", "When documents change, Memory asks you to accept the new version.", "Երբ փաստաթղթերը փոխվում են, Memory-ն կխնդրի ընդունել նոր տարբերակը։"),
                ),
                action = GuideAction.Legal,
            ),
        ),
    )

    private fun chapter10() = GuideChapter(
        "settings", "⚙",
        GuideText("Настройки", "Settings", "Կարգավորումներ"),
        GuideText("Уведомления, звуки, язык, текст", "Notifications, sounds, language, text", "Ծանուցումներ, ձայներ, լեզու, տեքստ"),
        listOf(
            GuideTopic(
                "open", GuideText("Где настройки", "Where settings are", "Որտեղ են կարգավորումները"),
                listOf(
                    GuideText("Профиль → шестерёнка вверху.", "Profile → the gear at the top.", "Պրոֆիլ → ատամնանիվը վերևում։"),
                ),
                action = GuideAction.Settings,
            ),
            GuideTopic(
                "notify", GuideText("Уведомления и звуки", "Notifications and sounds", "Ծանուցումներ և ձայներ"),
                listOf(
                    GuideText("Включайте и выключайте: сообщения, PRISM друзей, «нравится», группы, друзья, итоги.", "Toggle messages, friends’ PRISM, likes, groups, friends and recaps.", "Միացրեք և անջատեք՝ հաղորդագրություններ, ընկերների PRISM, «հավանումներ», խմբեր, ընկերներ, ամփոփումներ։"),
                    GuideText("У каждого вида — свой звук: кристалл, звёздная пыль, комета, луна, призма…", "Each kind has its own sound: crystal, stardust, comet, moon, prism…", "Յուրաքանչյուր տեսակ ունի իր ձայնը՝ բյուրեղ, աստղափոշի, գիսաստղ, լուսին, պրիզմա…"),
                ),
                action = GuideAction.Sounds,
            ),
            GuideTopic(
                "text", GuideText("Язык и размер текста", "Language and text size", "Լեզու և տեքստի չափ"),
                listOf(
                    GuideText("Язык: как в системе, русский или English — меняется сразу.", "Language: system, Russian or English — it switches instantly.", "Լեզու՝ ինչպես համակարգում, ռուսերեն կամ English․ փոխվում է անմիջապես։"),
                    GuideText("Размер текста — от мелкого до огромного.", "Text size — from small to huge.", "Տեքստի չափ՝ մանրից մինչև հսկայական։"),
                ),
                tip = GuideText("Язык и размер текста хранятся на телефоне, остальное — в аккаунте.", "Language and text size are stored on the phone; everything else belongs to the account.", "Լեզուն և տեքստի չափը պահվում են հեռախոսում, մնացածը՝ հաշվում։"),
                action = GuideAction.Language,
            ),
            GuideTopic(
                "theme", GuideText("Светлая и тёмная тема", "Light and dark theme", "Բաց և մուգ թեմա"),
                listOf(
                    GuideText("Настройки → «Оформление»: «Как в системе», «Тёмное» или «Светлое».", "Settings → “Look”: “System default”, “Dark” or “Light”.", "Կարգավորումներ → «Տեսք»՝ «Ինչպես համակարգում», «Մուգ» կամ «Բաց»։"),
                ),
                tip = GuideText("PRISM, эфиры, звонки, истории и фото на весь экран всегда тёмные — так лучше видно.", "PRISM, live streams, calls, stories and full-screen photos are always dark — they look better that way.", "PRISM-ը, եթերները, զանգերը, պատմությունները և լիաէկրան լուսանկարները միշտ մուգ են՝ այդպես ավելի լավ է երևում։"),
                action = GuideAction.Settings,
            ),
            GuideTopic(
                "network", GuideText("Данные и сеть", "Data and network", "Տվյալներ և ցանց"),
                listOf(
                    GuideText("Настройки → «Данные и сеть»: качество фото и видео при отправке — «Стандартное», «Экономия трафика» или «Оригинал по Wi‑Fi».", "Settings → “Data and network”: photo and video quality when sending — “Standard”, “Data saver” or “Original on Wi‑Fi”.", "Կարգավորումներ → «Տվյալներ և ցանց»՝ լուսանկարների և տեսանյութերի որակը ուղարկելիս՝ «Ստանդարտ», «Թրաֆիկի խնայում» կամ «Բնօրինակ Wi‑Fi-ով»։"),
                    GuideText("Там же — загрузки: пауза, продолжить, отменить; и сколько места занимает сохранённое для офлайна.", "There you'll also find uploads — pause, resume, cancel — and how much space offline content takes.", "Այնտեղ են նաև վերբեռնումները՝ դադար, շարունակել, չեղարկել, և որքան տեղ է զբաղեցնում անցանցի համար պահպանվածը։"),
                ),
                tip = GuideText("Оборвалась связь — загрузка продолжится с того же места, даже после перезапуска.", "Connection dropped? The upload resumes from the same place, even after a restart.", "Կապը կտրվե՞ց։ Վերբեռնումը կշարունակվի նույն տեղից, նույնիսկ վերագործարկումից հետո։"),
                action = GuideAction.Settings,
            ),
            GuideTopic(
                "screen-reader", GuideText("Экранный диктор", "Screen reader", "Էկրանի ընթերցիչ"),
                listOf(
                    GuideText("Memory работает с TalkBack на Android и VoiceOver на iPhone: у кнопок-значков есть названия.", "Memory works with TalkBack on Android and VoiceOver on iPhone: icon buttons have names.", "Memory-ն աշխատում է TalkBack-ի (Android) և VoiceOver-ի (iPhone) հետ՝ պատկերակ-կոճակներն ունեն անուններ։"),
                    GuideText("Карточки записей читаются целиком, «нравится» и комментарии — с числом, переключатели — со словами «вкл.» и «выкл.».", "Post cards are read as a whole, likes and comments with their counts, switches as on or off.", "Գրառումների քարտերը կարդացվում են ամբողջությամբ, հավանումներն ու մեկնաբանությունները՝ թվով, անջատիչները՝ միացված/անջատված։"),
                    GuideText("Размер текста — Настройки → «Размер текста».", "Text size — Settings → “Text size”.", "Տեքստի չափը՝ Կարգավորումներ → «Տեքստի չափը»։"),
                ),
                action = GuideAction.Language,
            ),
            GuideTopic(
                "ideas", GuideText("Идеи и ошибки", "Ideas and bugs", "Գաղափարներ և սխալներ"),
                listOf(
                    GuideText("Настройки → «Друзья и идеи»: «Предложить идею» или «Сообщить об ошибке».", "Settings → “Friends & ideas”: “Suggest an idea” or “Report a bug”.", "Կարգավորումներ → «Ընկերներ և գաղափարներ»՝ «Առաջարկել գաղափար» կամ «Հայտնել սխալի մասին»։"),
                ),
                action = GuideAction.Growth,
            ),
            GuideTopic(
                "logout", GuideText("Выйти", "Signing out", "Դուրս գալ"),
                listOf(
                    GuideText("Последняя строка настроек. Записи и переписка останутся на телефоне и вернутся, когда вы войдёте снова.", "The last row in settings. Your posts and chats stay on the phone and come back when you sign in again.", "Կարգավորումների վերջին տողը։ Գրառումները և նամակագրությունը կմնան հեռախոսում և կվերադառնան, երբ նորից մուտք գործեք։"),
                ),
            ),
        ),
    )

    private fun chapter11() = GuideChapter(
        "faq", "❓",
        GuideText("Частые вопросы", "FAQ", "Հաճախ տրվող հարցեր"),
        GuideText("Короткие ответы", "Short answers", "Կարճ պատասխաններ"),
        listOf(
            GuideTopic(
                "where-data", GuideText("Где хранятся мои записи?", "Where are my posts stored?", "Որտե՞ղ են պահվում իմ գրառումները"),
                listOf(
                    GuideText("Пока на вашем телефоне — отдельно для каждого аккаунта. Они переживают перезапуск приложения.", "For now, on your phone — separately for each account. They survive app restarts.", "Առայժմ՝ ձեր հեռախոսում, յուրաքանչյուր հաշվի համար առանձին։ Դրանք պահպանվում են հավելվածը վերագործարկելուց հետո։"),
                ),
            ),
            GuideTopic(
                "offline", GuideText("Что работает без сети", "What works offline", "Ինչ է աշխատում առանց ցանցի"),
                listOf(
                    GuideText("Лента, которую вы уже видели, открывается и без сети — сверху полоска «Нет сети».", "The feed you've already seen opens offline — a “No network” strip appears at the top.", "Արդեն դիտված լրահոսը բացվում է նաև առանց ցանցի — վերևում «Ցանց չկա» շերտն է։"),
                    GuideText("Записи, «нравится» и сообщения можно отправлять как обычно: они встанут в очередь и уйдут сами, когда сеть появится.", "You can post, like and message as usual: everything queues up and sends itself once you're back online.", "Կարող եք գրառել, հավանել և գրել ինչպես սովորաբար․ ամեն ինչ հերթագրվում է և ինքնուրույն ուղարկվում, երբ ցանցը հայտնվի։"),
                    GuideText("Пока не ушло — у записи «Ждёт сети», у сообщения 🕓.", "Until it's sent, a post shows “Waiting for network” and a message shows 🕓.", "Մինչև ուղարկվելը գրառման մոտ գրված է «Սպասում է ցանցին», հաղորդագրության մոտ՝ 🕓։"),
                ),
            ),
            GuideTopic(
                "forgot", GuideText("Я забыл пароль", "I forgot my password", "Մոռացել եմ գաղտնաբառը"),
                listOf(
                    GuideText("На экране входа — «Забыли пароль?».", "On the sign-in screen tap “Forgot password?”.", "Մուտքի էկրանին սեղմեք «Մոռացե՞լ եք գաղտնաբառը»։"),
                    GuideText("Введите логин и один из кодов восстановления, которые вы сохранили при регистрации, — и новый пароль.", "Enter your login, one of the recovery codes you saved when signing up, and a new password.", "Մուտքագրեք մուտքանունը, գրանցվելիս պահպանած վերականգնման կոդերից մեկը և նոր գաղտնաբառ։"),
                    GuideText("Нет кодов — «Письмо на почту», если вы подтвердили почту в «Безопасности».", "No codes? Choose “Email” if you confirmed your email in Security.", "Կոդեր չկա՞ն — ընտրեք «Նամակ էլ. փոստին», եթե հաստատել եք էլ. փոստը «Անվտանգություն» բաժնում։"),
                ),
                tip = GuideText("Каждый код работает один раз. Новые коды — в «Безопасности».", "Each code works once. New codes are in Security.", "Յուրաքանչյուր կոդ աշխատում է մեկ անգամ։ Նոր կոդերը՝ «Անվտանգություն» բաժնում։"),
            ),
            GuideTopic(
                "no-posts", GuideText("Почему я не вижу записи человека?", "Why can’t I see someone’s posts?", "Ինչո՞ւ չեմ տեսնում մարդու գրառումները"),
                listOf(
                    GuideText("У него закрытый профиль — добавьте его в друзья.", "Their profile is private — add them as a friend.", "Նրա պրոֆիլը փակ է․ ավելացրեք նրան ընկերներին։"),
                    GuideText("Или он в вашем чёрном списке — проверьте настройки.", "Or they’re on your block list — check your settings.", "Կամ նա ձեր սև ցուցակում է․ ստուգեք կարգավորումները։"),
                ),
            ),
            GuideTopic(
                "gif-search", GuideText("Не работает поиск гифок", "GIF search doesn’t work", "GIF-երի որոնումը չի աշխատում"),
                listOf(
                    GuideText("Поиску нужен интернет. Встроенные гифки, недавние и свои из галереи работают и без него.", "Search needs the internet. Built-in, recent and gallery GIFs work offline.", "Որոնման համար անհրաժեշտ է ինտերնետ։ Ներկառուցված, վերջին և պատկերասրահից ձեր GIF-երն աշխատում են նաև առանց դրա։"),
                ),
            ),
            GuideTopic(
                "pay", GuideText("Как оплатить товар из витрины?", "How do I pay for a product?", "Ինչպե՞ս վճարել ցուցափեղկի ապրանքի համար"),
                listOf(
                    GuideText("Внутри Memory оплаты нет. Напишите продавцу или купите на его сайте.", "There is no payment inside Memory. Message the seller or buy on their website.", "Memory-ում վճարում չկա։ Գրեք վաճառողին կամ գնեք նրա կայքում։"),
                ),
            ),
            GuideTopic(
                "plus-free", GuideText("Что останется бесплатным?", "What stays free?", "Ի՞նչն է մնալու անվճար"),
                listOf(
                    GuideText("Всё общение и безопасность: лента, истории, PRISM, чаты, голосовые, друзья, группы, покупки в витринах, приватность, жалобы и удаление аккаунта.", "All communication and safety: feed, stories, PRISM, chats, voice notes, friends, groups, buying from shops, privacy, reports and account deletion.", "Ամբողջ հաղորդակցությունը և անվտանգությունը՝ լրահոս, պատմություններ, PRISM, զրույցներ, ձայնային հաղորդագրություններ, ընկերներ, խմբեր, գնումներ ցուցափեղկերից, գաղտնիություն, բողոքներ և հաշվի ջնջում։"),
                ),
                action = GuideAction.Premium,
            ),
            GuideTopic(
                "guide-again", GuideText("Как снова найти этот гид?", "How do I find this guide again?", "Ինչպե՞ս կրկին գտնել այս ուղեցույցը"),
                listOf(
                    GuideText("Профиль → шестерёнка → «Гид по Memory».", "Profile → gear → “Memory guide”.", "Պրոֆիլ → ատամնանիվ → «Memory ուղեցույց»։"),
                ),
            ),
        ),
    )
}
