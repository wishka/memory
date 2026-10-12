import SwiftUI

/// Чек-ин в карточке записи: значок занятия в кристальном круге, что
/// делает человек, что именно и сколько.
struct CheckInBadge: View {
    let checkIn: CheckIn

    /// Оценка, статус книги, темп и время: «★★★★☆ · 5:30 /км · 40 мин».
    private var extra: String {
        let c = checkIn
        let parts = [
            c.rating > 0 ? String(repeating: "★", count: c.rating) + String(repeating: "☆", count: max(0, 5 - c.rating)) : "",
            c.statusTitle,
            c.paceTitle,
            c.minutes > 0 && timedActivities.contains(c.activity) ? minutesTitle(c.minutes) : "",
        ]
        return parts.filter { !$0.isEmpty }.joined(separator: " · ")
    }

    var body: some View {
        HStack(spacing: 12) {
            Text(checkIn.emoji)
                .font(.system(size: 24))
                .frame(width: 48, height: 48)
                .background(Circle().fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)))
            VStack(alignment: .leading, spacing: 2) {
                Text(checkIn.displayTitle.uppercased()).textStyle(.label, Palette.lilac).lineLimit(1)
                if !checkIn.subject.isEmpty {
                    Text(checkIn.subject).textStyle(.title).lineLimit(2)
                }
                if !extra.isEmpty {
                    Text(extra).textStyle(.bodySmall, Palette.pearlPink).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            if !checkIn.amountTitle.isEmpty {
                Text(checkIn.amountTitle)
                    .textStyle(.title)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .glass(Capsule(), strong: true)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(LinearGradient(colors: [Palette.violet.opacity(0.55), Palette.ocean.opacity(0.35)], startPoint: .topLeading, endPoint: .bottomTrailing))
        )
        .accessibilityElement(children: .combine)
    }
}

/// Что выбрано: занятие, к которому осталось указать «что именно» и «сколько».
private struct CheckInDraft {
    let emoji: String
    let title: String
    let hint: String
    let unit: String
    /// Что именно, сколько, оценка, статус книги, минуты → чек-ин.
    let make: (_ subject: String, _ amount: Double, _ rating: Int, _ status: String, _ minutes: Int) -> CheckIn
    /// Встроенное занятие («run», «book»…); "" — свой шаблон.
    var activity: String = ""

    static func of(_ a: CheckInActivity) -> CheckInDraft {
        CheckInDraft(emoji: a.emoji, title: t(a.title), hint: t(a.hint), unit: a.unit.isEmpty ? "" : t(a.unit),
                     make: { subject, amount, rating, status, minutes in
                         a.checkIn(subject, amount, rating: rating, status: status, minutes: minutes)
                     },
                     activity: a.id)
    }
}

/// Выбор чек-ина: встроенные занятия и свои; «＋ Свой чек-ин» создаёт
/// шаблон. Затем — что именно и сколько. Долгое нажатие на свой — удалить.
struct CheckInPicker: View {
    let model: AppModel
    /// Что именно — заранее (из рекомендации, с полки).
    let presetSubject: String
    let onDone: (CheckIn) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var draft: CheckInDraft?
    @State private var creating = false
    @State private var deleting: CheckInTemplate?

    /// start — сразу к заполнению: сценарий «Посмотрел фильм», «Пробежка»…
    init(model: AppModel, start: String? = nil, presetSubject: String = "", onDone: @escaping (CheckIn) -> Void) {
        self.model = model
        self.presetSubject = presetSubject
        self.onDone = onDone
        _draft = State(initialValue: start.flatMap { activity($0) }.map { CheckInDraft.of($0) })
    }

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 3)

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let draft {
                FillStep(draft: draft, presetSubject: presetSubject, onBack: { self.draft = nil }) { checkIn in
                    onDone(checkIn)
                    dismiss()
                }
            } else if creating {
                CreateStep(model: model) { creating = false }
            } else {
                Text(t("Чек-ин")).textStyle(.headline)
                Text(t("Чем вы сейчас заняты?")).textStyle(.bodySmall, Palette.mist)
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 8) {
                        tile("＋", t("Свой чек-ин"), accent: true) { creating = true }
                        ForEach(model.state.checkInTemplates) { tpl in
                            tile(tpl.emoji, tpl.title) {
                                draft = CheckInDraft(emoji: tpl.emoji, title: tpl.title, hint: tpl.hint.isEmpty ? t("Что именно?") : tpl.hint,
                                                     unit: tpl.unit, make: { subject, amount, _, _, _ in tpl.checkIn(subject, amount) })
                            }
                            .onLongPressGesture(minimumDuration: 0.4) { deleting = tpl }
                        }
                        ForEach(checkInActivities) { a in
                            tile(a.emoji, t(a.title)) {
                                draft = CheckInDraft.of(a)
                            }
                        }
                    }
                }
                GhostButton(title: t("Отмена")) { dismiss() }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.large])
        .glassActions(
            item: $deleting,
            title: { "\($0.emoji) \($0.title)" },
            message: { _ in t("Удалить свой чек-ин? Записи с ним останутся.") },
            cancel: true
        ) { tpl in
            [GlassAction(icon: .trash, title: t("Удалить"), destructive: true) { model.deleteCheckInTemplate(tpl.id) }]
        }
    }

    private func tile(_ emoji: String, _ title: String, accent: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Text(emoji).font(.system(size: 28)).foregroundStyle(Palette.lilac)
                Text(title)
                    .textStyle(.labelLarge)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
            }
            .padding(8)
            .frame(maxWidth: .infinity, minHeight: 96)
            .glass(RoundedRectangle(cornerRadius: 18, style: .continuous), strong: accent)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private struct FillStep: View {
    let draft: CheckInDraft
    let onBack: () -> Void
    let onDone: (CheckIn) -> Void
    @State private var subject: String
    @State private var amount = ""
    @State private var rating = 0
    @State private var status: String
    @State private var minutes = ""

    init(draft: CheckInDraft, presetSubject: String, onBack: @escaping () -> Void, onDone: @escaping (CheckIn) -> Void) {
        self.draft = draft
        self.onBack = onBack
        self.onDone = onDone
        _subject = State(initialValue: presetSubject)
        _status = State(initialValue: draft.activity == "book" ? BookStatus.reading.id : "")
    }

    private var amountValue: Double { Double(amount.replacingOccurrences(of: ",", with: ".")) ?? 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Text(draft.emoji).font(.system(size: 32))
                Text(draft.title).textStyle(.title)
            }
            GlassField(placeholder: draft.hint, text: $subject, limit: 80, singleLine: true)
                .glass(RoundedRectangle(cornerRadius: 18, style: .continuous))
            if !draft.unit.isEmpty {
                HStack(spacing: 10) {
                    GlassField(placeholder: t("Сколько?"), text: $amount, limit: 7, singleLine: true, keyboard: .decimalPad)
                        .glass(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    Text(draft.unit).textStyle(.title, Palette.lilac)
                }
            }
            // Время — из него темп: «5:30 /км»
            if timedActivities.contains(draft.activity) {
                GlassField(placeholder: t("Время, минут (необязательно)"), text: $minutes, limit: 4, singleLine: true, keyboard: .numberPad)
                    .glass(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .onChange(of: minutes) { _, value in
                        let digits = String(value.filter(\.isNumber).prefix(4))
                        if digits != value { minutes = digits }
                    }
                let pace = paceTitle(draft.activity, amountValue, Int(minutes) ?? 0)
                if !pace.isEmpty {
                    Text(tf("Темп: %s", pace)).textStyle(.bodySmall, Palette.pearlPink)
                }
            }
            // Статус книги
            if draft.activity == "book" {
                HStack(spacing: 6) {
                    ForEach(BookStatus.allCases) { b in
                        Chip(text: b.title, active: status == b.id) { status = b.id }
                    }
                }
            }
            // Оценка
            if ratedActivities.contains(draft.activity) {
                Stars(rating: rating) { rating = rating == $0 ? 0 : $0 }
            }
            HStack(spacing: 10) {
                GhostButton(title: t("Назад"), action: onBack)
                GlowButton(title: t("Готово")) {
                    onDone(draft.make(subject, amountValue, rating, status, Int(minutes) ?? 0))
                }
            }
            Spacer()
        }
    }
}

private struct CreateStep: View {
    let model: AppModel
    let onBack: () -> Void
    @State private var emoji = checkInEmoji[0]
    @State private var title = ""
    @State private var unit = ""
    @State private var hint = ""
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(t("Свой чек-ин")).textStyle(.headline)
            Text(t("Например: «Скалодром», «Медитация», «Прочитал главу»")).textStyle(.bodySmall, Palette.mist)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(checkInEmoji, id: \.self) { e in
                        Button { emoji = e } label: {
                            Text(e).font(.system(size: 20))
                                .frame(width: 42, height: 42)
                                .glass(Circle(), strong: e == emoji)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            GlassField(placeholder: t("Название"), text: $title, limit: 40, singleLine: true)
                .glass(RoundedRectangle(cornerRadius: 18, style: .continuous))
            GlassField(placeholder: t("Подсказка: «Где?», «Что именно?»"), text: $hint, limit: 60, singleLine: true)
                .glass(RoundedRectangle(cornerRadius: 18, style: .continuous))
            GlassField(placeholder: t("Единица (необязательно): км, мин, раз"), text: $unit, limit: 12, singleLine: true)
                .glass(RoundedRectangle(cornerRadius: 18, style: .continuous))
            HStack(spacing: 6) {
                ForEach(["км", "мин", "ч", "раз", "стр."], id: \.self) { u in
                    Chip(text: t(u), active: unit == t(u)) { unit = unit == t(u) ? "" : t(u) }
                }
            }
            if let error {
                Text(error).textStyle(.bodySmall, Palette.pearlPink)
            }
            HStack(spacing: 10) {
                GhostButton(title: t("Назад"), action: onBack)
                GlowButton(title: t("Создать"), enabled: !title.trimmingCharacters(in: .whitespaces).isEmpty) {
                    error = model.createCheckInTemplate(emoji: emoji, title: title, unit: unit, hint: hint)
                    if error == nil { onBack() }
                }
            }
            Spacer()
        }
    }
}

/// Звёзды 1–5: нажатие на ту же — снять оценку. onRate = nil — только показать.
struct Stars: View {
    let rating: Int
    var size: CGFloat = 30
    var onRate: ((Int) -> Void)? = nil

    var body: some View {
        HStack(spacing: 4) {
            ForEach(1...5, id: \.self) { i in
                let star = Text(i <= rating ? "★" : "☆")
                    .font(.system(size: size))
                    .foregroundStyle(i <= rating ? Palette.pearlPink : Palette.mist)
                if let onRate {
                    Button { onRate(i) } label: { star.padding(2).contentShape(Circle()) }
                        .buttonStyle(.plain)
                } else {
                    star
                }
            }
        }
    }
}
