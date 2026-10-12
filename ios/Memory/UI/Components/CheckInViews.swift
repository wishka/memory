import SwiftUI

/// Чек-ин в карточке записи: значок занятия в кристальном круге, что
/// делает человек, что именно и сколько.
struct CheckInBadge: View {
    let checkIn: CheckIn

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
    let make: (String, Double) -> CheckIn
}

/// Выбор чек-ина: встроенные занятия и свои; «＋ Свой чек-ин» создаёт
/// шаблон. Затем — что именно и сколько. Долгое нажатие на свой — удалить.
struct CheckInPicker: View {
    let model: AppModel
    let onDone: (CheckIn) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var draft: CheckInDraft?
    @State private var creating = false
    @State private var deleting: CheckInTemplate?

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 3)

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let draft {
                FillStep(draft: draft, onBack: { self.draft = nil }) { checkIn in
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
                                                     unit: tpl.unit, make: tpl.checkIn)
                            }
                            .onLongPressGesture(minimumDuration: 0.4) { deleting = tpl }
                        }
                        ForEach(checkInActivities) { a in
                            tile(a.emoji, t(a.title)) {
                                draft = CheckInDraft(emoji: a.emoji, title: t(a.title), hint: t(a.hint),
                                                     unit: a.unit.isEmpty ? "" : t(a.unit), make: a.checkIn)
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
    @State private var subject = ""
    @State private var amount = ""

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
            HStack(spacing: 10) {
                GhostButton(title: t("Назад"), action: onBack)
                GlowButton(title: t("Готово")) {
                    let value = Double(amount.replacingOccurrences(of: ",", with: ".")) ?? 0
                    onDone(draft.make(subject, value))
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
