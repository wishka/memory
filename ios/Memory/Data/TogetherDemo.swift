import CoreGraphics
import Foundation

/// Демо-наполнение вкладки «Вместе», пока нет сервера. Точки на карте —
/// вокруг центра Москвы; когда телефон узнает, где вы, они переедут к
/// вам (TogetherState.relocate). Те же данные — в TogetherDemo.kt.
enum TogetherDemo {
    private static let hour: TimeInterval = 3600
    private static let day: TimeInterval = 24 * hour

    static func initial(now: Date = Date()) -> TogetherState {
        let c = LatLon.moscow
        func ago(_ seconds: TimeInterval) -> Date { now.addingTimeInterval(-seconds) }
        func ahead(_ seconds: TimeInterval) -> Date { now.addingTimeInterval(seconds) }
        var r4 = SeededRandom(4)
        var r2 = SeededRandom(2)
        var r6 = SeededRandom(6)
        var r3 = SeededRandom(3)
        let goals: [CommunityGoal] = [
            CommunityGoal(id: "km", emoji: "🏃", title: "Вместе пробежим 1 000 000 км", description: "Каждый ваш чек-ин с километрами — бег, велосипед, прогулка — засчитывается сам.", unit: "км", target: 1_000_000, total: 734_210, participants: 18_402, metric: .km),
            CommunityGoal(id: "books", emoji: "📚", title: "10 000 прочитанных книг", description: "Отмечайте «Читаю книгу» — каждая книга двигает общую шкалу.", unit: "книг", target: 10_000, total: 6_412, participants: 3_918, metric: .books),
            CommunityGoal(id: "hours", emoji: "🧘", title: "100 000 часов заботы о себе", description: "Йога, тренировки, учёба: минуты из чек-инов складываются в общие часы.", unit: "ч", target: 100_000, total: 41_880, participants: 7_254, metric: .hours),
            CommunityGoal(id: "trees", emoji: "🌳", title: "Посадим 5 000 деревьев", description: "Посадили дерево или куст — добавьте вклад вручную.", unit: "деревьев", target: 5_000, total: 1_973, participants: 1_206, metric: .trees),
        ]
        let offers: [SkillOffer] = [
            SkillOffer(personId: 2, teaches: ["Фотография ночью", "Lightroom"], wants: ["Английский", "Гитара"], about: "Покажу, как снимать звёзды на телефон"),
            SkillOffer(personId: 3, teaches: ["Гитара", "Сведение музыки"], wants: ["Фотография", "Английский"], about: "30 минут — и первые три аккорда ваши"),
            SkillOffer(personId: 4, teaches: ["Рисование", "Акварель"], wants: ["Программирование", "Фотография ночью"], about: "Научу не бояться белого листа"),
            SkillOffer(personId: 5, teaches: ["Походы", "Ориентирование"], wants: ["Кулинария", "Гитара"], about: "Соберём рюкзак и проложим маршрут"),
            SkillOffer(personId: 6, teaches: ["Английский", "Плёночная фотография"], wants: ["Рисование", "Йога"], about: "Разговорный английский без учебника"),
        ]
        let journeys: [Journey] = [
            Journey(
                id: 9001, authorId: 4, emoji: "🤹", title: "Учусь жонглировать тремя мячами", days: 30,
                entries: [
                    JourneyEntry(id: 9101, day: 1, text: "Два мяча — получается, три — летят в разные стороны.", createdAt: ago(3 * day),
                                 tips: [Tip(id: 9201, authorId: 6, text: "Начни с одного мяча: бросай из руки в руку на уровень глаз, 5 минут каждый день.", helpful: 12, createdAt: ago(3 * day - hour))]),
                    JourneyEntry(id: 9102, day: 2, text: "Делала по совету: один мяч, глаза на верхней точке. Стало спокойнее.", createdAt: ago(2 * day)),
                    JourneyEntry(id: 9103, day: 3, text: "Первые четыре броска тремя мячами! Потом всё упало, но четыре было.", createdAt: ago(5 * hour),
                                 tips: [Tip(id: 9202, authorId: 5, text: "Встань лицом к стене — мячи перестанут улетать вперёд.", helpful: 4, createdAt: ago(3 * hour))]),
                ],
                createdAt: ago(3 * day)
            ),
            Journey(
                id: 9002, authorId: 3, emoji: "🎹", title: "Каждый день 15 минут за синтезатором", days: 21,
                entries: [
                    JourneyEntry(id: 9104, day: 1, text: "Гамма до мажор двумя руками — медленно, но ровно.", createdAt: ago(day)),
                    JourneyEntry(id: 9105, day: 2, text: "Пробую импровизировать на трёх нотах. Звучит как дождь.", createdAt: ago(2 * hour)),
                ],
                createdAt: ago(day)
            ),
        ]
        let missions: [Mission] = [
            Mission(id: "mosaic", emoji: "🧩", title: "Пять мозаик на стенах домов", description: "Найдите и сфотографируйте мозаики — советские панно, современные работы, мелочи в подъездах.", target: 5,
                    finds: [
                        MissionFind(id: 9301, authorId: 2, note: "Космонавты на торце дома", at: c.offset(east: 420, north: 310), createdAt: ago(2 * day)),
                        MissionFind(id: 9302, authorId: 6, note: "Птицы над входом в библиотеку", at: c.offset(east: -650, north: 180), createdAt: ago(day)),
                    ]),
            Mission(id: "sign", emoji: "🪧", title: "Самая старая вывеска района", description: "Ищите вывески, которым больше 30 лет: шрифты, неон, эмаль.", target: 3,
                    finds: [MissionFind(id: 9303, authorId: 5, note: "«Булочная» — ещё с эмалевыми буквами", at: c.offset(east: 150, north: -520), createdAt: ago(3 * day))]),
            Mission(id: "door", emoji: "🚪", title: "Двери, за которыми история", description: "Резные, кованые, необычные — сфотографируйте двери старых домов.", target: 7),
            Mission(id: "route", emoji: "🌉", title: "Маршрут «Огни набережных»", description: "Ночной маршрут вдоль воды: отметьте пять мостов и фонарей, которые светят ярче всех.", target: 5, unlockKm: 20),
            Mission(id: "cat", emoji: "🐈", title: "Коты-хранители дворов", description: "Познакомьтесь с местными котами и отметьте их дворы.", target: 5,
                    finds: [MissionFind(id: 9304, authorId: 4, note: "Рыжий страж арки", at: c.offset(east: -230, north: -410), createdAt: ago(6 * hour))]),
        ]
        let caches: [GeoCache] = [
            GeoCache(id: 9401, authorId: 5, title: "Звёздная капсула", hint: "У старого клёна, третий камень от скамейки — под ним.", at: c.offset(east: 260, north: 140), difficulty: 2, size: "Маленький", createdAt: ago(4 * day),
                     logs: [CacheLog(id: 9501, authorId: 2, text: "Нашла с первого раза, оставила открытку!", createdAt: ago(2 * day))]),
            GeoCache(id: 9402, authorId: 2, title: "Книжный тайник", hint: "Ищите там, где когда-то продавали газеты.", at: c.offset(east: -380, north: 520), difficulty: 3, size: "Средний", createdAt: ago(6 * day)),
            GeoCache(id: 9403, authorId: 6, title: "Плёночный кадр", hint: "Магнит на обратной стороне указателя, на высоте вытянутой руки.", at: c.offset(east: 610, north: -270), difficulty: 4, size: "Микро", createdAt: ago(day)),
        ]
        let outings: [Outing] = [
            Outing(id: 9601, organizerId: 5, emoji: "🍄", title: "За грибами в Лосиный остров", description: "Неспешно, с термосом. Новичкам покажем, какие грибы брать нельзя.", place: "Метро «Бульвар Рокоссовского», выход 1", startsAt: ahead(2 * day + 9 * hour), capacity: 8, participants: [5, 6, 4], at: c.offset(east: 4200, north: 9800)),
            Outing(id: 9602, organizerId: 2, emoji: "📷", title: "Фотопрогулка: город в синий час", description: "Снимаем огни после заката. Подойдёт любой телефон.", place: "Патриаршие пруды", startsAt: ahead(day + 18 * hour), capacity: 6, participants: [2, 4], at: c.offset(east: -1800, north: 900)),
            Outing(id: 9603, organizerId: 6, emoji: "🚴", title: "Велопробег по набережным", description: "30 км без спешки, остановки на кофе.", place: "Парк Горького, главный вход", startsAt: ahead(4 * day + 11 * hour), capacity: 10, participants: [6, 3, 5, 2, 4], at: c.offset(east: -900, north: -2100)),
        ]
        let drawings: [CollabDrawing] = [
            CollabDrawing(id: 9701, title: "Город, которого нет", maxTurns: 4,
                          turns: [DrawingTurn(authorId: 4, strokes: demoStrokes(&r4, color: 0xBC92CD))],
                          waitingFor: 1, createdAt: ago(5 * hour)),
            CollabDrawing(id: 9702, title: "Кот-космонавт", maxTurns: 3, turns: [
                DrawingTurn(authorId: 2, strokes: demoStrokes(&r2, color: 0x8CC4FF)),
                DrawingTurn(authorId: 6, strokes: demoStrokes(&r6, color: 0xFF9EDE)),
                DrawingTurn(authorId: 3, strokes: demoStrokes(&r3, color: 0xFFD86B)),
            ], waitingFor: nil, createdAt: ago(2 * day)),
        ]
        let shares: [ShareItem] = [
            ShareItem(id: 9801, kind: .book, title: "«Пикник на обочине», Стругацкие", description: "Карандашные пометки на полях — оставьте свои.", ownerId: 6, holderId: 4, history: [6], createdAt: ago(10 * day)),
            ShareItem(id: 9802, kind: .book, title: "«Задача трёх тел»", description: "Прочитала за выходные. Передаю дальше.", ownerId: 2, holderId: 2, createdAt: ago(day)),
            ShareItem(id: 9803, kind: .seeds, title: "Семена томатов «Чёрный принц»", description: "С прошлогоднего урожая, хватит на 20 кустов.", ownerId: 5, holderId: 5, createdAt: ago(2 * day)),
            ShareItem(id: 9804, kind: .seeds, title: "Черенки монстеры", description: "Укоренённые, в стаканчиках.", ownerId: 4, holderId: 4, createdAt: ago(3 * day)),
            ShareItem(id: 9805, kind: .repair, title: "Плёночный фотоаппарат «Зенит»", description: "Заедает затвор. Кто умеет — помогите, с меня кофе.", ownerId: 6, holderId: 6, createdAt: ago(4 * day)),
            ShareItem(id: 9806, kind: .repair, title: "Старый торшер", description: "Нужно перетянуть абажур и заменить провод.", ownerId: 3, holderId: 3, helpers: [5], createdAt: ago(day)),
        ]
        return TogetherState(
            goals: goals,
            offers: offers,
            myTeach: ["Фотография"],
            myWant: ["Гитара"],
            journeys: journeys,
            missions: missions,
            caches: caches,
            outings: outings,
            drawings: drawings,
            shares: shares
        )
    }

    /// Несколько линий-каракулей: «ход» демо-друга в общем рисунке.
    /// color — 0xRRGGBB, как у кистей студии.
    static func demoStrokes(_ random: inout SeededRandom, color: UInt32) -> [DrawStroke] {
        var strokes: [DrawStroke] = []
        let count = 3 + random.nextInt(3)
        for _ in 0..<count {
            var x = 0.15 + random.nextDouble() * 0.7
            var y = 0.15 + random.nextDouble() * 0.7
            var points: [CGPoint] = []
            let length = 8 + random.nextInt(10)
            for _ in 0..<length {
                x = min(0.95, max(0.05, x + (random.nextDouble() - 0.5) * 0.12))
                y = min(0.95, max(0.05, y + (random.nextDouble() - 0.5) * 0.12))
                points.append(CGPoint(x: x, y: y))
            }
            strokes.append(DrawStroke(points: points, color: color, width: 0.02 + CGFloat(random.nextDouble()) * 0.02))
        }
        return strokes
    }

    /// Советы, которые в демо приходят к вашим записям дневника.
    static let tips = [
        "Записывай, что именно не получилось — через неделю увидишь, как это ушло.",
        "Делай чуть меньше, чем можешь: так завтра захочется вернуться.",
        "Сними себя на видео — со стороны ошибки видно сразу.",
        "Попробуй в то же время каждый день — привычка сделает половину работы.",
        "Не перескакивай этапы: медленно и правильно быстрее, чем быстро и с ошибками.",
    ]
}
