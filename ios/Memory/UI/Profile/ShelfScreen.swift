import SwiftUI

/// Что оценить на полке: занятие («movie», «book») и что именно.
private struct RateTarget: Identifiable {
    let activity: String
    let subject: String
    var id: String { activity + ":" + subject }
}

/// Друг и насколько совпадает вкус.
private struct TasteFriend: Identifiable {
    let id: Int
    let match: TasteMatch
}

/// Полка — профиль, который собирается сам: кино, книги, спорт, места из
/// чек-инов, подборка «для вас» по вкусу друзей, план бега и права,
/// которые открываются за действия. Ничего не надо заполнять вручную.
/// То же — в ShelfScreen.kt.
struct ShelfScreen: View {
    let model: AppModel
    @State private var rating: RateTarget?
    @State private var sharing: SharePayload?

    var body: some View {
        let state = model.state
        let shelf = state.shelf
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 14) {
                    RoundGlyphButton(icon: .back) { model.closeProfileSection() }
                    ScreenTitle(title: t("Полка"), subtitle: t("Собирается сама из ваших чек-инов"))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 4)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(ShelfTab.allCases) { tab in
                            Chip(text: "\(tab.emoji) \(tab.title)", active: state.shelfTab == tab) { model.selectShelfTab(tab) }
                        }
                    }
                    .padding(.horizontal, 16)
                }
                switch state.shelfTab {
                case .forYou: forYou(state, shelf)
                case .films: films(shelf)
                case .books: books(shelf)
                case .sport: sport(shelf)
                case .places: places(shelf)
                case .progress: progress(state)
                }
                GhostButton(title: t("Поделиться полкой")) {
                    let spec = ShareCardSpec(
                        kicker: t("Полка"), emoji: "📚", title: tf("Полка: %s", state.me.name),
                        subtitle: t("Что я смотрю, читаю и где бегаю"),
                        lines: shelfLines(shelf),
                        author: "\(state.me.name) · @\(state.me.handle)"
                    )
                    sharing = shareCard(model, spec, name: "shelf", action: .shelf)
                }
                .padding(.horizontal, 16)
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .sheet(item: $rating) { target in
            RateSheet(subject: target.subject, onRate: { value in
                model.rateOnShelf(target.activity, target.subject, value)
                rating = nil
            }, onDismiss: { rating = nil })
        }
        .shareSheet($sharing)
    }

    // MARK: Для вас

    @ViewBuilder
    private func forYou(_ state: UiState, _ shelf: Shelf) -> some View {
        RunPlanCard(plan: state.runPlan)
        if !state.privacy.personalRecs {
            // Персональные рекомендации выключены в «Приватности» — ни подборки, ни «Похожего вкуса»
            ShelfHint(text: t("Персональные рекомендации выключены в «Настройках → Приватность». Включите их, чтобы полка советовала фильмы и книги."))
            RecsHow(model: model)
        } else {
            recommended(state, shelf)
        }
    }

    @ViewBuilder
    private func recommended(_ state: UiState, _ shelf: Shelf) -> some View {
        let films = state.recommendations("film", shelf: shelf)
        let books = state.recommendations("book", shelf: shelf)
        if shelf.rated.count < 3 {
            ShelfHint(text: t("Оцените ещё пару фильмов или книг — подборка станет точнее. Мы советуем то, что любят люди с похожим вкусом."))
        }
        if !films.isEmpty {
            ShelfCaption(text: t("Кино для вас"))
            ForEach(films, id: \.key) { r in
                RecCard(model: model, rec: r) { rating = RateTarget(activity: "movie", subject: r.subject) }
            }
        }
        if !books.isEmpty {
            ShelfCaption(text: t("Книги для вас"))
            ForEach(books, id: \.key) { r in
                RecCard(model: model, rec: r) { rating = RateTarget(activity: "book", subject: r.subject) }
            }
        }
        if !films.isEmpty || !books.isEmpty {
            RecsHow(model: model)
        }
        // С кем совпадает вкус
        let mine = shelf.rated
        let matches = DemoTaste.ratings.keys.sorted()
            .filter { !state.blocked.contains($0) }
            .map { TasteFriend(id: $0, match: tasteMatch(mine, DemoTaste.ratings[$0] ?? [])) }
            .filter { $0.match.common >= 2 }
            .stableSorted { $0.match.similarity > $1.match.similarity }
        if !matches.isEmpty {
            ShelfCaption(text: t("Похожий вкус"))
            ForEach(matches) { item in
                if let person = Demo.person(item.id) {
                    GlassCard {
                        Button { model.openPerson(item.id) } label: {
                            HStack(spacing: 12) {
                                Avatar(user: person, size: 40)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(person.name).textStyle(.title)
                                    Text(tf("Общих оценок: %s", item.match.common)).textStyle(.bodySmall, Palette.mist)
                                }
                                Spacer(minLength: 0)
                                Text(verbatim: "\(item.match.percent)%").textStyle(.title, Palette.pearlPink)
                            }
                            .padding(12)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                    .cardPadding()
                }
            }
        }
    }

    // MARK: Кино и книги

    @ViewBuilder
    private func films(_ shelf: Shelf) -> some View {
        if shelf.films.isEmpty {
            ShelfHint(text: t("Отметьте «Посмотрел фильм» в ленте — фильм встанет на полку с вашей оценкой."))
        }
        if !shelf.watched.isEmpty {
            ShelfCaption(text: tf("Посмотрено · %s", shelf.watched.count))
            ForEach(shelf.watched, id: \.key) { item in
                ItemRow(item: item) { rating = RateTarget(activity: item.activity, subject: item.subject) }
            }
        }
        if !shelf.plannedFilms.isEmpty {
            ShelfCaption(text: tf("В планах · %s", shelf.plannedFilms.count))
            ForEach(shelf.plannedFilms, id: \.key) { item in
                ItemRow(item: item, action: t("Посмотрел")) { rating = RateTarget(activity: item.activity, subject: item.subject) }
            }
        }
    }

    @ViewBuilder
    private func books(_ shelf: Shelf) -> some View {
        if shelf.books.isEmpty {
            ShelfHint(text: t("Отметьте книгу в ленте: «Читаю», «Прочитал» или «Хочу прочитать»."))
        }
        ForEach([BookStatus.reading, .done, .want]) { status in
            let list = shelf.booksBy(status)
            if !list.isEmpty {
                ShelfCaption(text: "\(status.title) · \(list.count)")
                ForEach(list, id: \.key) { item in
                    ItemRow(item: item, action: status != .done ? t("Прочитал") : nil) {
                        rating = RateTarget(activity: "book", subject: item.subject)
                    }
                }
            }
        }
    }

    // MARK: Спорт и места

    @ViewBuilder
    private func sport(_ shelf: Shelf) -> some View {
        if shelf.sport.isEmpty {
            ShelfHint(text: t("Отметьте пробежку, заезд или тренировку — здесь соберутся километры, рекорды и лучший темп."))
        }
        ForEach(shelf.sport, id: \.activity) { s in
            SportCard(stats: s)
        }
    }

    @ViewBuilder
    private func places(_ shelf: Shelf) -> some View {
        if shelf.places.isEmpty {
            ShelfHint(text: t("Отметьте «Я здесь», кофейню или концерт — получится ваша карта мест."))
        }
        ForEach(shelf.places, id: \.subject) { p in
            GlassCard {
                HStack(spacing: 12) {
                    Text(p.emoji).font(.system(size: 24))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(p.subject).textStyle(.title)
                        Text(plural(p.count, "раз", "раза", "раз")).textStyle(.bodySmall, Palette.mist)
                    }
                    Spacer(minLength: 0)
                    if p.rating > 0 { Stars(rating: p.rating, size: 14) }
                }
                .padding(12)
            }
            .cardPadding()
        }
        if !shelf.other.isEmpty {
            ShelfCaption(text: t("Ещё занятия"))
            ForEach(shelf.other, id: \.title) { o in
                GlassCard {
                    HStack(spacing: 12) {
                        Text(o.emoji).font(.system(size: 22))
                        Text(o.title).textStyle(.title)
                        Spacer(minLength: 0)
                        Text(plural(o.count, "раз", "раза", "раз")).textStyle(.bodySmall, Palette.mist)
                    }
                    .padding(12)
                }
                .cardPadding()
            }
        }
    }

    // MARK: Права

    @ViewBuilder
    private func progress(_ state: UiState) -> some View {
        ShelfHint(text: t("Здесь не значки, а возможности: делаете — открываете новое."))
        ForEach(state.unlocks) { u in
            GlassCard {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 12) {
                        Text(u.unlock.emoji).font(.system(size: 26))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(u.unlock.title).textStyle(.title)
                            Text(u.unlock.reward).textStyle(.bodySmall, Palette.mist)
                        }
                        Spacer(minLength: 0)
                        Text(u.open ? "🔓" : "🔒").font(.system(size: 20))
                    }
                    ShelfBar(fraction: u.fraction)
                    Text(u.open ? t("Открыто") : u.unlock.how + " · " + tf("осталось %s", u.left))
                        .textStyle(.labelLarge, u.open ? Palette.pearlPink : Palette.lilac)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .cardPadding()
        }
        let km = state.movedKm
        ForEach(state.together.missions.filter { $0.unlockKm > 0 }) { m in
            GlassCard {
                VStack(alignment: .leading, spacing: 6) {
                    Text("\(m.emoji) \(m.title)").textStyle(.title)
                    ShelfBar(fraction: min(1, max(0, km / m.unlockKm)))
                    Text(km >= m.unlockKm
                         ? t("Маршрут открыт — он во «Вместе → Миссии»")
                         : tf("%s из %s км пробежек, прогулок и заездов", amountNumber(tenths(km)), amountNumber(m.unlockKm)))
                        .textStyle(.labelLarge, Palette.lilac)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .cardPadding()
        }
    }
}

/// Главное с полки — для карточки «Поделиться».
func shelfLines(_ shelf: Shelf) -> [EchoLine] {
    var lines: [EchoLine] = []
    for item in shelf.watched.filter({ $0.rating >= 4 }).prefix(2) {
        lines.append(EchoLine(emoji: item.emoji, text: "\(item.subject) · \(String(repeating: "★", count: item.rating))"))
    }
    if let reading = shelf.booksBy(.reading).first {
        lines.append(EchoLine(emoji: "📖", text: tf("Читаю: %s", reading.subject)))
    }
    if let done = shelf.booksBy(.done).first {
        lines.append(EchoLine(emoji: "🏁", text: tf("Прочитал: %s", done.subject)))
    }
    if let run = shelf.sport.first(where: { $0.totalKm > 0 }) {
        lines.append(EchoLine(emoji: run.emoji, text: tf("%s км всего", amountNumber(tenths(run.totalKm)))))
    }
    if lines.isEmpty { lines.append(EchoLine(emoji: "✨", text: t("Полка только начинается"))) }
    return Array(lines.prefix(5))
}

// MARK: - Части полки

private struct RecCard: View {
    let model: AppModel
    let rec: Recommendation
    let onSeen: () -> Void

    var body: some View {
        let r = rec
        let who = Demo.person(r.personId)?.firstName ?? ""
        GlassCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 12) {
                    Text(r.emoji).font(.system(size: 30))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(r.subject).textStyle(.title)
                        // На Android «%%» остаётся двумя знаками — здесь показываем один
                        Text((r.match.common >= 2
                              ? tf("%s ставит %s★ · вкус совпадает на %s%%", who, r.theirRating, r.match.percent)
                              : tf("%s ставит %s★", who, r.theirRating)).replacingOccurrences(of: "%%", with: "%"))
                            .textStyle(.bodySmall, Palette.mist)
                        if r.friends > 1 {
                            Text(tf("Нравится друзьям: %s", r.friends)).textStyle(.labelLarge, Palette.lilac)
                        }
                    }
                    Spacer(minLength: 0)
                }
                FlowLayout(spacing: 8) {
                    GlowButton(title: r.kind == "book" ? t("Хочу прочитать") : t("Хочу посмотреть")) { model.planOnShelf(r.kind, r.subject) }
                    GhostButton(title: r.kind == "book" ? t("Читал") : t("Смотрел"), action: onSeen)
                    GhostButton(title: t("Не моё")) { model.dismissRecommendation(r.key) }
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .cardPadding()
    }
}

private struct RunPlanCard: View {
    let plan: RunPlan

    private var note: String {
        if plan.done { return t("План выполнен — на следующей неделе он чуть вырастет.") }
        switch plan.reason {
        case .start: return t("Начнём с 5 км за неделю: две-три лёгкие пробежки.")
        case .comeback: return t("Вы пропустили неделю — начинаем мягче, чем раньше.")
        case .grow: return t("На 10% больше прошлой недели — так растут без травм.")
        }
    }

    var body: some View {
        let bars = plan.weeksKm + [plan.doneKm]
        let top = max(1, (plan.weeksKm + [plan.targetKm]).max() ?? 1)
        GlassCard {
            VStack(alignment: .leading, spacing: 8) {
                Text(t("ПЛАН БЕГА НА НЕДЕЛЮ")).textStyle(.label, Palette.mist)
                Text(tf("%s из %s км", amountNumber(tenths(plan.doneKm)), amountNumber(plan.targetKm))).textStyle(.headline)
                ShelfBar(fraction: plan.progress)
                Text(note).textStyle(.bodySmall, Palette.mist).fixedSize(horizontal: false, vertical: true)
                // Четыре прошлые недели столбиками и эта
                HStack(alignment: .bottom, spacing: 8) {
                    ForEach(Array(bars.enumerated()), id: \.offset) { i, km in
                        VStack(spacing: 2) {
                            RoundedRectangle(cornerRadius: 6, style: .continuous)
                                .fill(i == 4 ? Palette.pearlPink : Palette.lilac.opacity(0.6))
                                .frame(height: max(3, 44 * CGFloat(km / top)))
                            Text(i == 4 ? t("эта") : "−\(4 - i)").textStyle(.label, Palette.mist)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                .frame(height: 64, alignment: .bottom)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .cardPadding()
    }
}

private struct ItemRow: View {
    let item: ShelfItem
    var action: String? = nil
    let onRate: () -> Void

    var body: some View {
        let details = [
            item.times > 1 ? plural(item.times, "раз", "раза", "раз") : nil,
            item.pages > 0 ? tf("%s стр.", amountNumber(item.pages)) : nil,
        ].compactMap { $0 }.joined(separator: " · ")
        GlassCard {
            HStack(spacing: 12) {
                Text(item.emoji).font(.system(size: 26))
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.subject).textStyle(.title)
                    if !details.isEmpty {
                        Text(details).textStyle(.bodySmall, Palette.mist)
                    }
                    if item.rating > 0 { Stars(rating: item.rating, size: 16) }
                }
                Spacer(minLength: 0)
                if let action {
                    GhostButton(title: action, action: onRate)
                } else if item.rating == 0 {
                    GhostButton(title: t("Оценить"), action: onRate)
                }
            }
            .padding(12)
        }
        .cardPadding()
    }
}

private struct SportCard: View {
    let stats: SportStats

    var body: some View {
        let s = stats
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("\(s.emoji)  " + plural(s.count, "раз", "раза", "раз")).textStyle(.title)
                HStack(spacing: 8) {
                    if s.totalKm > 0 {
                        ShelfTile(value: amountNumber(tenths(s.totalKm)), label: t("км всего"))
                        ShelfTile(value: amountNumber(tenths(s.monthKm)), label: t("км за месяц"))
                        ShelfTile(value: amountNumber(s.longestKm), label: t("самая длинная"))
                    } else {
                        ShelfTile(value: minutesTitle(s.minutes), label: t("всего"))
                    }
                }
                if let pace = s.bestPace {
                    Text(tf("Лучший темп: %s /км", paceClock(pace))).textStyle(.bodySmall, Palette.pearlPink)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .cardPadding()
    }
}

/// Оценить: пять звёзд.
private struct RateSheet: View {
    let subject: String
    let onRate: (Int) -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 14) {
            Text(subject).textStyle(.title).multilineTextAlignment(.center)
            Text(t("Ваша оценка")).textStyle(.bodySmall, Palette.mist)
            Stars(rating: 0, size: 36, onRate: onRate)
            GhostButton(title: t("Отмена"), action: onDismiss)
        }
        .padding(20)
        .frame(maxWidth: .infinity)
        .presentationDetents([.height(260)])
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }
}

/// Полоска прогресса.
struct ShelfBar: View {
    let fraction: Double

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.plum)
                Capsule().fill(Palette.lilac)
                    .frame(width: geo.size.width * CGFloat(min(1, max(0, fraction))))
            }
        }
        .frame(height: 8)
    }
}

private struct ShelfTile: View {
    let value: String
    let label: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).textStyle(.title).lineLimit(1).minimumScaleFactor(0.7)
            Text(label).textStyle(.label, Palette.mist).lineLimit(2)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glass(radius: 16)
    }
}

private struct ShelfCaption: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .textStyle(.label, Palette.mist)
            .padding(.horizontal, 20)
            .padding(.vertical, 2)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// «Как работают рекомендации» — правила рекомендаций (149-ФЗ, ст. 10.2-2).
private struct RecsHow: View {
    let model: AppModel

    var body: some View {
        Button { model.openLegal(.recommendations) } label: {
            Text(t("Как работают рекомендации")).textStyle(.bodySmall, Palette.lilac)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct ShelfHint: View {
    let text: String

    var body: some View {
        Text(text)
            .textStyle(.bodySmall, Palette.mist)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 20)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Карточка «Полка» в профиле: что уже собралось и вход на полку.
struct ShelfCard: View {
    let model: AppModel

    var body: some View {
        let shelf = model.state.shelf
        let km = shelf.sport.reduce(0) { $0 + $1.totalKm }
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                Text(t("ПОЛКА")).textStyle(.label, Palette.mist)
                HStack(spacing: 8) {
                    tile("\(shelf.watched.count)", t("фильмов")) { model.openShelf(.films) }
                    tile("\(shelf.booksBy(.done).count)", t("книг прочитано")) { model.openShelf(.books) }
                    tile(amountNumber(tenths(km)), t("км")) { model.openShelf(.sport) }
                }
                HStack(spacing: 10) {
                    GlowButton(title: t("Для вас")) { model.openShelf(.forYou) }
                    GhostButton(title: t("Вся полка")) { model.openShelf(.films) }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .cardPadding()
    }

    private func tile(_ value: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ShelfTile(value: value, label: label).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
