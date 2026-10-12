package ru.wishka.memory.data

import kotlin.random.Random

/**
 * Демо-наполнение вкладки «Вместе», пока нет сервера. Точки на карте —
 * вокруг центра Москвы; когда телефон узнает, где вы, они переедут к
 * вам (TogetherState.relocate). Те же данные — в TogetherDemo.swift.
 */
object TogetherDemo {
    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR

    fun initial(now: Long = System.currentTimeMillis()): TogetherState {
        val c = LatLon.DEFAULT
        return TogetherState(
            goals = listOf(
                CommunityGoal("km", "🏃", "Вместе пробежим 1 000 000 км", "Каждый ваш чек-ин с километрами — бег, велосипед, прогулка — засчитывается сам.", "км", 1_000_000.0, 734_210.0, 18_402, GoalMetric.Km),
                CommunityGoal("books", "📚", "10 000 прочитанных книг", "Отмечайте «Читаю книгу» — каждая книга двигает общую шкалу.", "книг", 10_000.0, 6_412.0, 3_918, GoalMetric.Books),
                CommunityGoal("hours", "🧘", "100 000 часов заботы о себе", "Йога, тренировки, учёба: минуты из чек-инов складываются в общие часы.", "ч", 100_000.0, 41_880.0, 7_254, GoalMetric.Hours),
                CommunityGoal("trees", "🌳", "Посадим 5 000 деревьев", "Посадили дерево или куст — добавьте вклад вручную.", "деревьев", 5_000.0, 1_973.0, 1_206, GoalMetric.Trees),
            ),
            offers = listOf(
                SkillOffer(2, listOf("Фотография ночью", "Lightroom"), listOf("Английский", "Гитара"), "Покажу, как снимать звёзды на телефон"),
                SkillOffer(3, listOf("Гитара", "Сведение музыки"), listOf("Фотография", "Английский"), "30 минут — и первые три аккорда ваши"),
                SkillOffer(4, listOf("Рисование", "Акварель"), listOf("Программирование", "Фотография ночью"), "Научу не бояться белого листа"),
                SkillOffer(5, listOf("Походы", "Ориентирование"), listOf("Кулинария", "Гитара"), "Соберём рюкзак и проложим маршрут"),
                SkillOffer(6, listOf("Английский", "Плёночная фотография"), listOf("Рисование", "Йога"), "Разговорный английский без учебника"),
            ),
            myTeach = listOf("Фотография"),
            myWant = listOf("Гитара"),
            journeys = listOf(
                Journey(
                    9001, 4, "🤹", "Учусь жонглировать тремя мячами", 30,
                    listOf(
                        JourneyEntry(9101, 1, "Два мяча — получается, три — летят в разные стороны.", createdAt = now - 3 * DAY,
                            tips = listOf(Tip(9201, 6, "Начни с одного мяча: бросай из руки в руку на уровень глаз, 5 минут каждый день.", 12, createdAt = now - 3 * DAY + HOUR))),
                        JourneyEntry(9102, 2, "Делала по совету: один мяч, глаза на верхней точке. Стало спокойнее.", createdAt = now - 2 * DAY),
                        JourneyEntry(9103, 3, "Первые четыре броска тремя мячами! Потом всё упало, но четыре было.", createdAt = now - 5 * HOUR,
                            tips = listOf(Tip(9202, 5, "Встань лицом к стене — мячи перестанут улетать вперёд.", 4, createdAt = now - 3 * HOUR))),
                    ),
                    createdAt = now - 3 * DAY,
                ),
                Journey(
                    9002, 3, "🎹", "Каждый день 15 минут за синтезатором", 21,
                    listOf(
                        JourneyEntry(9104, 1, "Гамма до мажор двумя руками — медленно, но ровно.", createdAt = now - DAY),
                        JourneyEntry(9105, 2, "Пробую импровизировать на трёх нотах. Звучит как дождь.", createdAt = now - 2 * HOUR),
                    ),
                    createdAt = now - DAY,
                ),
            ),
            missions = listOf(
                Mission("mosaic", "🧩", "Пять мозаик на стенах домов", "Найдите и сфотографируйте мозаики — советские панно, современные работы, мелочи в подъездах.", 5,
                    finds = listOf(
                        MissionFind(9301, 2, "Космонавты на торце дома", c.offset(420.0, 310.0), createdAt = now - 2 * DAY),
                        MissionFind(9302, 6, "Птицы над входом в библиотеку", c.offset(-650.0, 180.0), createdAt = now - DAY),
                    )),
                Mission("sign", "🪧", "Самая старая вывеска района", "Ищите вывески, которым больше 30 лет: шрифты, неон, эмаль.", 3,
                    finds = listOf(MissionFind(9303, 5, "«Булочная» — ещё с эмалевыми буквами", c.offset(150.0, -520.0), createdAt = now - 3 * DAY))),
                Mission("door", "🚪", "Двери, за которыми история", "Резные, кованые, необычные — сфотографируйте двери старых домов.", 7),
                Mission("route", "🌉", "Маршрут «Огни набережных»", "Ночной маршрут вдоль воды: отметьте пять мостов и фонарей, которые светят ярче всех.", 5, unlockKm = 20.0),
                Mission("cat", "🐈", "Коты-хранители дворов", "Познакомьтесь с местными котами и отметьте их дворы.", 5,
                    finds = listOf(MissionFind(9304, 4, "Рыжий страж арки", c.offset(-230.0, -410.0), createdAt = now - 6 * HOUR))),
            ),
            caches = listOf(
                Cache(9401, 5, "Звёздная капсула", "У старого клёна, третий камень от скамейки — под ним.", c.offset(260.0, 140.0), 2, "Маленький", now - 4 * DAY,
                    listOf(CacheLog(9501, 2, "Нашла с первого раза, оставила открытку!", now - 2 * DAY))),
                Cache(9402, 2, "Книжный тайник", "Ищите там, где когда-то продавали газеты.", c.offset(-380.0, 520.0), 3, "Средний", now - 6 * DAY),
                Cache(9403, 6, "Плёночный кадр", "Магнит на обратной стороне указателя, на высоте вытянутой руки.", c.offset(610.0, -270.0), 4, "Микро", now - DAY),
            ),
            outings = listOf(
                Outing(9601, 5, "🍄", "За грибами в Лосиный остров", "Неспешно, с термосом. Новичкам покажем, какие грибы брать нельзя.", "Метро «Бульвар Рокоссовского», выход 1", now + 2 * DAY + 9 * HOUR, 8, listOf(5, 6, 4), c.offset(4200.0, 9800.0)),
                Outing(9602, 2, "📷", "Фотопрогулка: город в синий час", "Снимаем огни после заката. Подойдёт любой телефон.", "Патриаршие пруды", now + DAY + 18 * HOUR, 6, listOf(2, 4), c.offset(-1800.0, 900.0)),
                Outing(9603, 6, "🚴", "Велопробег по набережным", "30 км без спешки, остановки на кофе.", "Парк Горького, главный вход", now + 4 * DAY + 11 * HOUR, 10, listOf(6, 3, 5, 2, 4), c.offset(-900.0, -2100.0)),
            ),
            drawings = listOf(
                CollabDrawing(9701, "Город, которого нет", 4, listOf(DrawingTurn(4, demoStrokes(Random(4), 0xFFBC92CD.toInt()))), waitingFor = 1, createdAt = now - 5 * HOUR),
                CollabDrawing(9702, "Кот-космонавт", 3, listOf(
                    DrawingTurn(2, demoStrokes(Random(2), 0xFF8CC4FF.toInt())),
                    DrawingTurn(6, demoStrokes(Random(6), 0xFFFF9EDE.toInt())),
                    DrawingTurn(3, demoStrokes(Random(3), 0xFFFFD86B.toInt())),
                ), waitingFor = null, createdAt = now - 2 * DAY),
            ),
            shares = listOf(
                ShareItem(9801, ShareKind.Book, "«Пикник на обочине», Стругацкие", "Карандашные пометки на полях — оставьте свои.", 6, 4, history = listOf(6), createdAt = now - 10 * DAY),
                ShareItem(9802, ShareKind.Book, "«Задача трёх тел»", "Прочитала за выходные. Передаю дальше.", 2, 2, createdAt = now - DAY),
                ShareItem(9803, ShareKind.Seeds, "Семена томатов «Чёрный принц»", "С прошлогоднего урожая, хватит на 20 кустов.", 5, 5, createdAt = now - 2 * DAY),
                ShareItem(9804, ShareKind.Seeds, "Черенки монстеры", "Укоренённые, в стаканчиках.", 4, 4, createdAt = now - 3 * DAY),
                ShareItem(9805, ShareKind.Repair, "Плёночный фотоаппарат «Зенит»", "Заедает затвор. Кто умеет — помогите, с меня кофе.", 6, 6, createdAt = now - 4 * DAY),
                ShareItem(9806, ShareKind.Repair, "Старый торшер", "Нужно перетянуть абажур и заменить провод.", 3, 3, helpers = listOf(5), createdAt = now - DAY),
            ),
        )
    }

    /** Несколько линий-каракулей: «ход» демо-друга в общем рисунке. */
    fun demoStrokes(random: Random, color: Int): List<DrawStroke> = List(3 + random.nextInt(3)) {
        var x = 0.15f + random.nextFloat() * 0.7f
        var y = 0.15f + random.nextFloat() * 0.7f
        val points = List(8 + random.nextInt(10)) {
            x = (x + (random.nextFloat() - 0.5f) * 0.12f).coerceIn(0.05f, 0.95f)
            y = (y + (random.nextFloat() - 0.5f) * 0.12f).coerceIn(0.05f, 0.95f)
            x to y
        }
        DrawStroke(points, color, 0.02f + random.nextFloat() * 0.02f)
    }

    /** Советы, которые в демо приходят к вашим записям дневника. */
    val tips = listOf(
        "Записывай, что именно не получилось — через неделю увидишь, как это ушло.",
        "Делай чуть меньше, чем можешь: так завтра захочется вернуться.",
        "Сними себя на видео — со стороны ошибки видно сразу.",
        "Попробуй в то же время каждый день — привычка сделает половину работы.",
        "Не перескакивай этапы: медленно и правильно быстрее, чем быстро и с ошибками.",
    )
}
