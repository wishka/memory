import SwiftUI

/// Опрос в записи, в группе или в переписке. До голоса — варианты
/// кнопками, после — полосы с процентами (свой выбор отмечен галочкой).
/// Голос можно поменять, пока опрос открыт; автор может его завершить.
struct PollCard: View {
    let poll: Poll
    let onVote: (Int) -> Void
    /// «Завершить» — только у автора; nil — кнопки нет.
    var onClose: (() -> Void)? = nil

    var body: some View {
        let showResults = poll.voted || poll.closed
        let percents = poll.percents()
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Glyph(kind: .poll, color: Palette.lilac, size: 18)
                Text(poll.closed ? t("Опрос завершён") : poll.multiple ? t("Опрос · можно несколько") : t("Опрос"))
                    .textStyle(.label, Palette.mist)
            }
            Text(poll.question).textStyle(.title).fixedSize(horizontal: false, vertical: true)
            ForEach(Array(poll.options.enumerated()), id: \.offset) { i, option in
                PollOptionRow(
                    text: option.text,
                    percent: percents.indices.contains(i) ? percents[i] : 0,
                    fraction: showResults ? poll.barFraction(i) : 0,
                    mine: poll.mine.contains(i),
                    leader: poll.closed && poll.leader == i,
                    showResults: showResults,
                    enabled: !poll.closed
                ) { onVote(i) }
            }
            HStack {
                Text(poll.voters == 0 ? t("Пока никто не голосовал") : plural(poll.voters, "голос", "голоса", "голосов"))
                    .textStyle(.bodySmall, Palette.mist)
                Spacer(minLength: 8)
                if let onClose, !poll.closed {
                    Button(action: onClose) {
                        Text(t("Завершить")).textStyle(.bodySmall, Palette.lilac)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Palette.voidRGB.opacity(0.35)))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Palette.line, lineWidth: 1))
    }
}

private struct PollOptionRow: View {
    let text: String
    let percent: Int
    let fraction: Double
    let mine: Bool
    let leader: Bool
    let showResults: Bool
    let enabled: Bool
    let onTap: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        Button(action: onTap) {
            HStack(spacing: 6) {
                if mine { Glyph(kind: .check, color: Palette.ink, size: 16) }
                Text(text).textStyle(.body).frame(maxWidth: .infinity, alignment: .leading)
                if showResults { Text("\(percent)%").textStyle(.title) }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(minHeight: 44)
            .background(alignment: .leading) {
                // Полоса доли голосов
                GeometryReader { geo in
                    LinearGradient(
                        colors: mine || leader
                            ? [Palette.orchidRGB.opacity(0.75), Palette.violetRGB.opacity(0.6)]
                            : [Palette.violetRGB.opacity(0.45), Palette.plumRGB.opacity(0.35)],
                        startPoint: .leading, endPoint: .trailing
                    )
                    .frame(width: geo.size.width * fraction)
                    .animation(.easeOut(duration: 0.6), value: fraction)
                }
            }
            .background(Palette.plumRGB.opacity(0.45))
            .clipShape(shape)
            .overlay(shape.stroke(mine ? Palette.lilac : Palette.line, lineWidth: 1))
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// Новый опрос: вопрос, от 2 до 10 вариантов, один или несколько ответов.
struct PollEditorSheet: View {
    let onCreate: (PollDraft) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var question = ""
    @State private var options = ["", ""]
    @State private var multiple = false

    private var draft: PollDraft { PollDraft(question: question, options: options, multiple: multiple) }

    var body: some View {
        let valid = draft.build(0) != nil
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(t("Новый опрос")).textStyle(.headline)
                Spacer()
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    GlassField(placeholder: t("Вопрос"), text: $question, limit: 200)
                        .glass(radius: 16)
                    ForEach(options.indices, id: \.self) { i in
                        HStack(spacing: 6) {
                            GlassField(placeholder: t("Вариант") + " \(i + 1)", text: optionBinding(i), limit: 100, singleLine: true)
                                .glass(radius: 16)
                            if options.count > Poll.minOptions {
                                Button { options.remove(at: i) } label: {
                                    Glyph(kind: .close, color: Palette.mist, size: 18)
                                        .frame(width: 32, height: 32)
                                        .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(LineIcon.trash.label)
                            }
                        }
                    }
                    if options.count < Poll.maxOptions {
                        Button { options.append("") } label: {
                            Text("+ " + t("Добавить вариант")).textStyle(.body, Palette.lilac)
                                .padding(.vertical, 6)
                                .padding(.horizontal, 4)
                        }
                        .buttonStyle(.plain)
                    }
                    Toggle(isOn: $multiple) { Text(t("Можно выбрать несколько")).textStyle(.body) }
                        .tint(Palette.orchid)
                        .padding(.vertical, 4)
                }
            }
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена")) { dismiss() }
                Spacer()
                GlowButton(title: t("Готово"), enabled: valid) {
                    onCreate(draft)
                    dismiss()
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }

    /// Поле варианта: индекс может уйти после удаления — читаем бережно.
    private func optionBinding(_ i: Int) -> Binding<String> {
        Binding(
            get: { options.indices.contains(i) ? options[i] : "" },
            set: { if options.indices.contains(i) { options[i] = $0 } }
        )
    }
}

/// Опрос, готовый к отправке, — над полем ввода, с крестиком.
struct PendingPoll: View {
    let draft: PollDraft
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Glyph(kind: .poll, color: Palette.lilac, size: 18)
            VStack(alignment: .leading, spacing: 1) {
                Text(draft.question).textStyle(.body).lineLimit(1)
                Text(plural(draft.options.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.count, "вариант", "варианта", "вариантов"))
                    .textStyle(.label, Palette.mist)
            }
            Spacer(minLength: 0)
            Button(action: onRemove) {
                Glyph(kind: .close, color: Palette.mist, size: 16)
                    .frame(width: 28, height: 28)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(LineIcon.trash.label)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .glass(radius: 14)
    }
}
