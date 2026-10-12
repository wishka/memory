import Observation
import SwiftUI
import UIKit

/// Вкладка «Вместе»: не соревнование, а общее дело. Сверху — разделы
/// метками; под ними — выбранный раздел. То же — в TogetherScreen.kt.
struct TogetherScreen: View {
    let model: AppModel
    @State private var geo = TogetherGeo()

    var body: some View {
        let state = model.state
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    ScreenTitle(title: t("Вместе"), subtitle: t("Не соревнуемся — делаем вместе"))
                        .padding(.horizontal, 20)
                        .padding(.vertical, 8)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(TogetherSection.allCases) { s in
                                Chip(text: "\(s.emoji) \(s.title)", active: s == state.togetherSection) {
                                    model.selectTogetherSection(s)
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                    VStack(alignment: .leading, spacing: 12) {
                        section(state.togetherSection)
                    }
                    .padding(.horizontal, 16)
                }
                .padding(.top, 12)
                .padding(.bottom, tabBarSpace)
            }
            .scrollDismissesKeyboard(.interactively)

            if let notice = geo.notice {
                Text(notice)
                    .textStyle(.labelLarge, Palette.lilac)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .glass(Capsule())
                    .padding(.bottom, tabBarSpace)
                    .transition(.opacity)
            }
        }
        .task {
            geo.model = model
            // Разрешение уже есть — узнаём место сразу, без вопросов
            if geo.here == nil && Locator.shared.permitted { geo.locate() }
        }
    }

    @ViewBuilder
    private func section(_ section: TogetherSection) -> some View {
        switch section {
        case .challenges: ChallengesSection(model: model)
        case .skills: SkillsSection(model: model)
        case .journeys: JourneysSection(model: model)
        case .missions: MissionsSection(model: model, geo: geo)
        case .caches: CachesSection(model: model, geo: geo)
        case .outings: OutingsSection(model: model, geo: geo)
        case .drawing: DrawingSection(model: model, geo: geo)
        case .sharing: SharingSection(model: model)
        }
    }
}

// MARK: - Общее

/// Где вы и как спросить место; заодно — короткие подсказки внизу экрана.
@Observable
final class TogetherGeo {
    var here: LatLon?
    var locating = false
    var notice: String?
    @ObservationIgnored weak var model: AppModel?

    /// Узнать место (при первом разе iOS спросит разрешение). nil — не вышло.
    func locate(_ onDone: @escaping (LatLon?) -> Void = { _ in }) {
        locating = true
        Task { @MainActor in
            let place = await Locator.shared.current()
            self.locating = false
            if let place {
                self.here = place
                self.model?.setLocation(place)
            }
            onDone(place)
        }
    }

    func show(_ text: String) {
        withAnimation { notice = text }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            withAnimation { if self.notice == text { self.notice = nil } }
        }
    }
}

/// Человек по номеру: вы или знакомый из демо.
func personOf(_ state: UiState, _ id: Int) -> User {
    if id == state.me.id { return state.me }
    return Demo.person(id) ?? User(id: id, name: t("Участник"), handle: "member")
}

/// Люди, которым можно что-то передать: ваши подписки и знакомые, без чёрного списка.
func togetherPeople(_ state: UiState) -> [User] {
    var seen = Set<Int>()
    return (state.followedPeople + Demo.people).filter { p in
        guard !state.blocked.contains(p.id), !seen.contains(p.id) else { return false }
        seen.insert(p.id)
        return true
    }
}

extension Array {
    /// Устойчивая сортировка по ключу — как sortedBy на Android.
    func togetherSorted<K: Comparable>(descending: Bool = false, by key: (Element) -> K) -> [Element] {
        enumerated()
            .sorted { a, b in
                let ka = key(a.element)
                let kb = key(b.element)
                if ka == kb { return a.offset < b.offset }
                return descending ? ka > kb : ka < kb
            }
            .map(\.element)
    }
}

/// Карточка раздела: стекло и отступы.
struct TogetherCard<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) { content() }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

struct TogetherCaption: View {
    let text: String

    var body: some View {
        Text(text.uppercased()).textStyle(.label, Palette.mist)
    }
}

struct TogetherHint: View {
    let text: String

    var body: some View {
        Text(text)
            .textStyle(.bodySmall, Palette.mist)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// Окно поверх — шторкой: заголовок, содержимое с прокруткой.
struct TogetherSheet<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text(title).textStyle(.title)
                content()
            }
            .padding(16)
            .padding(.top, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollDismissesKeyboard(.interactively)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }
}

/// Поле ввода в стекле на всю ширину.
struct TogetherField: View {
    let placeholder: String
    @Binding var text: String
    var limit: Int = 200
    var singleLine: Bool = true
    var keyboard: UIKeyboardType = .default

    var body: some View {
        GlassField(placeholder: placeholder, text: $text, limit: limit, singleLine: singleLine, keyboard: keyboard)
            .glass(radius: 18)
    }
}

/// Ошибка в окне — розовым.
struct TogetherError: View {
    let text: String?

    var body: some View {
        if let text {
            Text(text)
                .textStyle(.bodySmall, Palette.pearlPink)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// Человек строкой: аватар, имя и подпись.
struct PersonLine: View {
    let user: User
    let subtitle: String
    var onTap: (() -> Void)? = nil

    var body: some View {
        let row = HStack(spacing: 10) {
            Avatar(user: user, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(user.name).textStyle(.title).lineLimit(1)
                if !subtitle.isEmpty {
                    Text(subtitle).textStyle(.bodySmall, Palette.mist).lineLimit(2).multilineTextAlignment(.leading)
                }
            }
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
        if let onTap {
            Button(action: onTap) { row }.buttonStyle(.plain)
        } else {
            row
        }
    }
}

/// Шкала прогресса: градиент по заполненной части.
struct TogetherProgress: View {
    let progress: Double

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.plum)
                Capsule()
                    .fill(LinearGradient(colors: [Palette.pearlPink, Palette.lilac, Palette.sky], startPoint: .leading, endPoint: .trailing))
                    .frame(width: geo.size.width * CGFloat(min(1, max(0, progress))))
            }
        }
        .frame(height: 10)
    }
}

/// «734 210» — с разрядами.
func bigNumber(_ value: Double) -> String {
    let n = Int64(value)
    let digits = String(n.magnitude)
    var out = ""
    for (i, ch) in digits.enumerated() {
        if i > 0 && (digits.count - i) % 3 == 0 { out.append(" ") }
        out.append(ch)
    }
    return (n < 0 ? "-" : "") + out
}

/// Круглый значок-эмодзи.
struct EmojiBadge: View {
    let emoji: String
    var size: CGFloat = 44

    var body: some View {
        Text(emoji)
            .font(.system(size: size / 2))
            .frame(width: size, height: size)
            .background(Circle().fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)))
    }
}

// MARK: - 1. Общие цели

private struct ChallengesSection: View {
    let model: AppModel
    @State private var contributing: CommunityGoal?
    @State private var creating = false

    var body: some View {
        let together = model.state.together
        // «Сейчас вместе» — живое присутствие: сколько людей сейчас двигают цели
        let now = together.goals.reduce(0) { $0 + $1.participants } / 37 + 140
        VStack(alignment: .leading, spacing: 12) {
            TogetherCard {
                HStack(spacing: 8) {
                    Circle().fill(Palette.sky).frame(width: 10, height: 10)
                    Text(tf("Сейчас вместе с вами: %s", bigNumber(Double(now)))).textStyle(.title)
                }
                TogetherHint(text: t("Не соревнование: все двигают одну шкалу. Ваши чек-ины — бег, книги, тренировки — засчитываются сами, если вы участвуете."))
            }
            ForEach(together.goals) { goal in
                goalCard(goal)
            }
            // Своя цель — право за вклад в чужие
            if let own = model.state.unlocks.first(where: { $0.unlock == .ownGoal }) {
                TogetherCard {
                    if own.open {
                        TogetherHint(text: t("Вы открыли право предложить сообществу свою цель."))
                        GlowButton(title: t("Своя цель")) { creating = true }
                    } else {
                        TogetherHint(text: "🔒 " + t("Своя общая цель") + ": " + own.unlock.how + " · " + tf("осталось %s", own.left))
                    }
                }
            }
        }
        .sheet(item: $contributing) { goal in
            ContributeSheet(model: model, goal: goal) { contributing = nil }
        }
        .sheet(isPresented: $creating) {
            OwnGoalSheet(model: model) { creating = false }
        }
    }

    private func goalCard(_ goal: CommunityGoal) -> some View {
        TogetherCard {
            HStack(spacing: 12) {
                EmojiBadge(emoji: goal.emoji)
                VStack(alignment: .leading, spacing: 2) {
                    Text(goal.title).textStyle(.title)
                    Text(tf("Участников: %s", bigNumber(Double(goal.participants)))).textStyle(.bodySmall, Palette.mist)
                }
                Spacer(minLength: 0)
            }
            TogetherProgress(progress: goal.progress)
            // На Android «%%» остаётся двумя знаками — здесь показываем один
            Text(tf("%s из %s %s · %s%%", bigNumber(goal.total), bigNumber(goal.target), t(goal.unit), Int(goal.progress * 100))
                .replacingOccurrences(of: "%%", with: "%"))
                .textStyle(.labelLarge, Palette.lilac)
            Text(goal.description).textStyle(.bodySmall, Palette.mist).fixedSize(horizontal: false, vertical: true)
            if goal.joined && goal.mine > 0 {
                Text(tf("Ваш вклад: %s %s", amountNumber((goal.mine * 10).rounded() / 10), t(goal.unit))).textStyle(.bodySmall)
            }
            HStack(spacing: 10) {
                if !goal.joined {
                    GlowButton(title: t("Участвовать")) { model.joinGoal(goal.id) }
                } else {
                    GhostButton(title: t("Вы участвуете")) {}
                }
                GhostButton(title: t("Внести вклад")) { contributing = goal }
            }
        }
    }
}

private struct ContributeSheet: View {
    let model: AppModel
    let goal: CommunityGoal
    let onClose: () -> Void
    @State private var amount = ""

    var body: some View {
        TogetherSheet(title: goal.title) {
            TogetherHint(text: tf("Сколько добавить, %s?", t(goal.unit)))
            TogetherField(placeholder: t("Например, 5"), text: $amount, limit: 7, keyboard: .decimalPad)
                .onChange(of: amount) { _, value in
                    let clean = String(value.filter { $0.isNumber || $0 == "," || $0 == "." }.prefix(7))
                    if clean != value { amount = clean }
                }
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onClose)
                GlowButton(title: t("Добавить")) {
                    model.contribute(goal.id, Double(amount.replacingOccurrences(of: ",", with: ".")) ?? 0)
                    onClose()
                }
            }
        }
    }
}

/// Своя общая цель: название, как засчитывается вклад, сколько и чего.
private struct OwnGoalSheet: View {
    let model: AppModel
    let onClose: () -> Void
    @State private var emoji = "🎯"
    @State private var title = ""
    @State private var description = ""
    @State private var unit = ""
    @State private var target = ""
    @State private var error: String?

    var body: some View {
        TogetherSheet(title: t("Своя цель")) {
            HStack(spacing: 6) {
                ForEach(["🎯", "🌳", "🧹", "🚲", "📚", "🐾"], id: \.self) { e in
                    Chip(text: e, active: emoji == e) { emoji = e }
                }
            }
            TogetherField(placeholder: t("Цель: «Уберём 100 дворов»"), text: $title, limit: 60)
                .onChange(of: title) { _, _ in error = nil }
            TogetherField(placeholder: t("Как засчитывается вклад"), text: $description, limit: 300, singleLine: false)
            HStack(spacing: 8) {
                TogetherField(placeholder: t("Сколько"), text: $target, limit: 9, keyboard: .numberPad)
                    .onChange(of: target) { _, value in
                        let digits = String(value.filter(\.isNumber).prefix(9))
                        if digits != value { target = digits }
                    }
                TogetherField(placeholder: t("Чего: дворов"), text: $unit, limit: 12)
            }
            TogetherError(text: error)
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onClose)
                GlowButton(title: t("Создать"), enabled: !title.trimmingCharacters(in: .whitespaces).isEmpty) {
                    error = model.createGoal(emoji: emoji, title: title, description: description, unit: unit, target: Double(target) ?? 0)
                    if error == nil { onClose() }
                }
            }
        }
    }
}

// MARK: - 2. Банк времени

extension SkillOffer: Identifiable {
    var id: Int { personId }
}

/// Что добавляем: чему научу или чему хочу научиться.
private enum SkillSide: String, Identifiable {
    case teach, want
    var id: String { rawValue }
}

private struct SkillsSection: View {
    let model: AppModel
    @State private var adding: SkillSide?
    @State private var dealWith: SkillOffer?
    @State private var sharing: SharePayload?

    var body: some View {
        let state = model.state
        let together = state.together
        let deals = together.deals.filter { $0.status != .declined }
        let matches = together.matches().filter { !state.blocked.contains($0.offer.personId) }
        VStack(alignment: .leading, spacing: 12) {
            TogetherCard {
                TogetherCaption(text: t("Банк времени"))
                Text(minutesTitle(together.balance)).textStyle(.headline)
                if let mentor = state.unlocks.first(where: { $0.unlock == .mentor }) {
                    Text(mentor.open
                         ? "🎓 " + t("Вы наставник — вас первым видят в подборе")
                         : "🎓 " + mentor.unlock.how + " · " + tf("осталось %s", mentor.left))
                        .textStyle(.labelLarge, Palette.lilac)
                        .fixedSize(horizontal: false, vertical: true)
                }
                TogetherHint(text: t("Никаких денег: научили кого-то 30 минут — получили 30 минут, потратить их можно на любой навык у любого. Обмен «я тебе — ты мне» баланс не меняет."))
            }
            TogetherCard {
                TogetherCaption(text: t("Я научу"))
                FlowLayout(spacing: 8) {
                    ForEach(together.myTeach, id: \.self) { s in
                        Chip(text: "\(s)  ×", active: true) { model.removeSkill(teach: true, s) }
                    }
                    Chip(text: "＋ " + t("Добавить")) { adding = .teach }
                }
                TogetherCaption(text: t("Хочу научиться"))
                FlowLayout(spacing: 8) {
                    ForEach(together.myWant, id: \.self) { s in
                        Chip(text: "\(s)  ×", active: true) { model.removeSkill(teach: false, s) }
                    }
                    Chip(text: "＋ " + t("Добавить")) { adding = .want }
                }
            }
            if !deals.isEmpty {
                TogetherCaption(text: t("Мои договорённости"))
                ForEach(deals) { deal in
                    DealCard(model: model, deal: deal)
                }
            }
            TogetherCaption(text: t("Подходят вам"))
            ForEach(Array(matches.enumerated()), id: \.offset) { _, match in
                offerCard(state, match.offer, score: match.score)
            }
            // Нужного навыка нет среди знакомых — позвать того, кто умеет
            TogetherCard {
                TogetherHint(text: t("Знаете того, кто умеет то, чему вы хотите научиться? Позовите — обменяетесь навыками."))
                GhostButton(title: t("Позвать в обмен навыками")) {
                    sharing = shareInvite(model, .lesson, title: together.myWant.first ?? "")
                }
            }
        }
        .shareSheet($sharing)
        .sheet(item: $adding) { side in
            AddSkillSheet(model: model, teach: side == .teach) { adding = nil }
        }
        .sheet(item: $dealWith) { offer in
            DealSheet(model: model, offer: offer) { dealWith = nil }
        }
    }

    private func offerCard(_ state: UiState, _ offer: SkillOffer, score: Int) -> some View {
        let person = personOf(state, offer.personId)
        return TogetherCard {
            PersonLine(user: person, subtitle: offer.about) { model.openPerson(person.id) }
            if score == 2 {
                Text(t("✦ Взаимный обмен")).textStyle(.labelLarge, Palette.lilac)
            }
            Text(tf("Научит: %s", offer.teaches.joined(separator: ", "))).textStyle(.bodySmall)
            Text(tf("Хочет: %s", offer.wants.joined(separator: ", "))).textStyle(.bodySmall, Palette.mist)
            GlowButton(title: t("Договориться")) { dealWith = offer }
        }
    }
}

private struct AddSkillSheet: View {
    let model: AppModel
    let teach: Bool
    let onClose: () -> Void
    @State private var skill = ""

    var body: some View {
        TogetherSheet(title: teach ? t("Чему вы научите?") : t("Чему хотите научиться?")) {
            TogetherField(placeholder: t("Например, гитара, английский, фото"), text: $skill, limit: 40)
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onClose)
                GlowButton(title: t("Добавить"), enabled: !skill.trimmingCharacters(in: .whitespaces).isEmpty) {
                    model.addSkill(teach: teach, skill)
                    onClose()
                }
            }
        }
    }
}

private struct DealSheet: View {
    let model: AppModel
    let offer: SkillOffer
    let onClose: () -> Void
    @State private var giveSkill: String
    @State private var getSkill: String
    @State private var minutes = 30
    @State private var error: String?

    init(model: AppModel, offer: SkillOffer, onClose: @escaping () -> Void) {
        self.model = model
        self.offer = offer
        self.onClose = onClose
        let together = model.state.together
        let give = together.myTeach.first { mine in offer.wants.contains { $0.lowercased() == mine.lowercased() } } ?? ""
        let get = offer.teaches.first { theirs in together.myWant.contains { $0.lowercased() == theirs.lowercased() } } ?? offer.teaches.first ?? ""
        _giveSkill = State(initialValue: give)
        _getSkill = State(initialValue: get)
    }

    var body: some View {
        let state = model.state
        let person = personOf(state, offer.personId)
        let change = (giveSkill.isEmpty ? 0 : minutes) - (getSkill.isEmpty ? 0 : minutes)
        TogetherSheet(title: tf("Обмен с %s", person.name)) {
            TogetherCaption(text: t("Вы научите"))
            FlowLayout(spacing: 8) {
                Chip(text: t("Ничему — заплачу часами"), active: giveSkill.isEmpty) { giveSkill = "" }
                ForEach(state.together.myTeach, id: \.self) { s in
                    Chip(text: s, active: giveSkill == s) { giveSkill = s }
                }
            }
            TogetherCaption(text: t("Вас научат"))
            FlowLayout(spacing: 8) {
                Chip(text: t("Ничему — пусть будут часы"), active: getSkill.isEmpty) { getSkill = "" }
                ForEach(offer.teaches, id: \.self) { s in
                    Chip(text: s, active: getSkill == s) { getSkill = s }
                }
            }
            TogetherCaption(text: t("Сколько"))
            HStack(spacing: 8) {
                ForEach([30, 60, 90], id: \.self) { m in
                    Chip(text: minutesTitle(m), active: minutes == m) { minutes = m }
                }
            }
            TogetherHint(text: change > 0
                ? tf("В банк времени придёт %s", minutesTitle(change))
                : change < 0
                ? tf("Из банка времени уйдёт %s (у вас %s)", minutesTitle(-change), minutesTitle(state.together.balance))
                : t("Обмен один к одному — баланс не меняется"))
            TogetherError(text: error)
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onClose)
                GlowButton(title: t("Предложить")) {
                    error = model.proposeDeal(person.id, give: giveSkill, get: getSkill, minutes: minutes)
                    if error == nil { onClose() }
                }
            }
        }
    }
}

private struct DealCard: View {
    let model: AppModel
    let deal: SkillDeal

    private var subtitle: String {
        if !deal.give.isEmpty && !deal.get.isEmpty {
            return tf("Вы — «%s», вам — «%s» · %s", deal.give, deal.get, minutesTitle(deal.minutes))
        }
        if !deal.get.isEmpty { return tf("Урок «%s» за %s", deal.get, minutesTitle(deal.minutes)) }
        return tf("Вы учите «%s» · %s", deal.give, minutesTitle(deal.minutes))
    }

    private var statusTitle: String {
        switch deal.status {
        case .proposed: return t("Ждём ответа")
        case .agreed: return t("Договорились — встретьтесь и проведите занятие")
        case .done: return t("Занятие прошло ✓")
        case .declined: return t("Отменено")
        }
    }

    var body: some View {
        let person = personOf(model.state, deal.personId)
        TogetherCard {
            PersonLine(user: person, subtitle: subtitle) { model.openPerson(person.id) }
            Text(statusTitle).textStyle(.labelLarge, deal.status == .done ? Palette.sky : Palette.lilac)
            HStack(spacing: 10) {
                if deal.status == .agreed {
                    GlowButton(title: t("Занятие прошло")) { model.setDeal(deal.id, .done) }
                }
                if deal.status != .done {
                    GhostButton(title: t("Отменить")) { model.setDeal(deal.id, .declined) }
                }
                RoundGlyphButton(icon: .chats, color: Palette.lilac, iconSize: 20) { model.writeTo(person.id) }
            }
        }
    }
}
