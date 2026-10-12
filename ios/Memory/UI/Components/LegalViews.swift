import SwiftUI

// MARK: - Документы

/// Документ из docs/legal: простая разметка — заголовки «#», «##»,
/// пункты «- », жирное «**…**», таблицы показываются строками.
struct LegalText: View {
    let text: String

    var body: some View {
        let lines = text.components(separatedBy: .newlines)
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(lines.enumerated()), id: \.offset) { _, raw in
                row(raw)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func row(_ raw: String) -> some View {
        let str = raw.replacingOccurrences(of: "\\s+$", with: "", options: .regularExpression)
        let trimmed = str.drop { $0 == " " || $0 == "\t" }
        if str.trimmingCharacters(in: .whitespaces).isEmpty || str.hasPrefix("| ---") {
            EmptyView()
        } else if str.hasPrefix("# ") {
            Text(legalPlain(String(str.dropFirst(2)))).textStyle(.headline)
                .fixedSize(horizontal: false, vertical: true)
        } else if str.hasPrefix("## ") {
            Text(legalPlain(String(str.dropFirst(3)))).textStyle(.title, Palette.lilac)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 6)
        } else if trimmed.hasPrefix("- ") {
            HStack(alignment: .top, spacing: 10) {
                Text("◇").textStyle(.bodySmall, Palette.lilac)
                Text(legalPlain(String(trimmed.dropFirst(2)))).textStyle(.bodySmall)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.leading, str.hasPrefix("  ") ? 16 : 0)
        } else if str.hasPrefix("|") {
            let cells = str.trimmingCharacters(in: CharacterSet(charactersIn: "|"))
                .components(separatedBy: "|")
                .map { legalPlain($0.trimmingCharacters(in: .whitespaces)) }
            Text(cells.joined(separator: " · ")).textStyle(.bodySmall)
                .fixedSize(horizontal: false, vertical: true)
        } else {
            Text(legalPlain(str)).textStyle(.bodySmall)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// Без разметки: жирное и ссылки — простым текстом.
func legalPlain(_ s: String) -> String {
    s.replacingOccurrences(of: "**", with: "")
        .replacingOccurrences(of: "\\[([^\\]]+)\\]\\(([^)]+)\\)", with: "$1", options: .regularExpression)
        .replacingOccurrences(of: "`", with: "")
}

/// Документ во весь экран.
struct LegalDocViewer: View {
    let doc: LegalDoc
    let onClose: () -> Void

    @State private var text: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Button(action: onClose) {
                    Glyph(kind: .close, color: Palette.ink, size: 22)
                        .frame(width: 44, height: 44)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(t("Закрыть"))
                Text(doc.title).textStyle(.title).lineLimit(2)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            FadeLine()
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if !Legal.filled {
                        Text(t("Черновик: данные оператора ещё не заполнены."))
                            .textStyle(.labelLarge, Palette.pearlPink)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    if let text {
                        LegalText(text: text.isEmpty ? t("Документ не найден в сборке.") : text)
                    }
                }
                .padding(20)
                .frame(maxWidth: 720, alignment: .leading)
                .frame(maxWidth: .infinity)
            }
        }
        .background(Palette.void.ignoresSafeArea())
        .task(id: doc) { text = doc.load() }
    }
}

/// Ссылки на документы рядком — под галочками согласия и под формой входа.
struct LegalLinks: View {
    let docs: [LegalDoc]
    let onOpen: (LegalDoc) -> Void

    var body: some View {
        FlowLayout(spacing: 12) {
            ForEach(docs) { doc in
                Button { onOpen(doc) } label: {
                    Text(doc.title)
                        .textStyle(.bodySmall, Palette.lilac)
                        .padding(.vertical, 2)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - Согласие

/// Галочка с подписью: вся строка нажимается.
struct CheckRow: View {
    let checked: Bool
    let text: String
    let onChange: (Bool) -> Void

    var body: some View {
        Button { onChange(!checked) } label: {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(checked ? Palette.orchid : Color.clear)
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .stroke(checked ? Palette.orchid : Palette.mist, lineWidth: 1.5)
                    if checked {
                        Glyph(kind: .check, color: Palette.ink, size: 16)
                    }
                }
                .frame(width: 22, height: 22)
                Text(text)
                    .textStyle(.bodySmall)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.vertical, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(checked ? .isSelected : [])
        .sensoryFeedback(.selection, trigger: checked)
    }
}

/// Три отдельные галочки, как требует 152-ФЗ: возраст, соглашение с
/// правилами и — отдельно — согласие на обработку данных.
struct ConsentState: Equatable {
    var age = false
    var terms = false
    var data = false

    var all: Bool { age && terms && data }
}

struct ConsentChecks: View {
    @Binding var consent: ConsentState
    let onOpen: (LegalDoc) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            CheckRow(checked: consent.age, text: tf("Мне есть %s лет", Legal.minAge)) { consent.age = $0 }
            CheckRow(checked: consent.terms, text: t("Принимаю пользовательское соглашение и правила сообщества")) { consent.terms = $0 }
            CheckRow(checked: consent.data, text: t("Даю согласие на обработку персональных данных")) { consent.data = $0 }
            LegalLinks(docs: [.terms, .community, .privacy, .consent], onOpen: onOpen)
                .padding(.leading, 34)
                .padding(.top, 4)
        }
    }
}

/// Документы обновились или аккаунт создан до них: войти дальше — только
/// приняв. Не согласен — выход из аккаунта (данные на телефоне остаются).
struct ConsentGate: View {
    let onAccept: () -> Void
    let onLogout: () -> Void

    @State private var consent = ConsentState()
    @State private var open: LegalDoc?

    var body: some View {
        ZStack {
            CosmicBackground().ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Spacer(minLength: 40)
                    Text("MEMORY").textStyle(.display)
                    Text(t("Документы Memory")).textStyle(.headline)
                    Text(t("Перед тем как продолжить, прочитайте и примите пользовательское соглашение, правила сообщества и согласие на обработку данных. Когда они изменятся, мы попросим об этом снова."))
                        .textStyle(.bodySmall, Palette.mist)
                        .fixedSize(horizontal: false, vertical: true)
                    GlassCard {
                        ConsentChecks(consent: $consent) { open = $0 }
                            .padding(14)
                    }
                    GlowButton(title: t("Принять и продолжить"), enabled: consent.all, fullWidth: true) {
                        if consent.all { onAccept() }
                    }
                    GhostButton(title: t("Не принимаю — выйти"), action: onLogout)
                        .frame(maxWidth: .infinity)
                    Spacer(minLength: 24)
                }
                .padding(20)
                .frame(maxWidth: 520)
                .frame(maxWidth: .infinity)
            }
        }
        .fullScreenCover(item: $open) { doc in
            LegalDocViewer(doc: doc) { open = nil }
        }
    }
}

// MARK: - Жалобы

/// Жалоба: причина, пара слов и — для чужого — «заблокировать автора».
/// Отправленное сразу скрывается у вас. «Правила сообщества» открываются
/// прямо поверх окна жалобы.
struct ReportDialog: View {
    let target: ReportTarget
    let onSend: (ReportReason, String, Bool) -> Void
    let onDismiss: () -> Void

    @State private var reason: ReportReason?
    @State private var note = ""
    @State private var block: Bool
    @State private var rules: LegalDoc?

    init(target: ReportTarget, onSend: @escaping (ReportReason, String, Bool) -> Void, onDismiss: @escaping () -> Void) {
        self.target = target
        self.onSend = onSend
        self.onDismiss = onDismiss
        // На человека жалуются чаще всего вместе с блокировкой
        _block = State(initialValue: target.kind == .person)
    }

    private var title: String {
        switch target.kind {
        case .post: return t("Пожаловаться на запись")
        case .comment: return t("Пожаловаться на комментарий")
        case .prism: return t("Пожаловаться на PRISM")
        case .message: return t("Пожаловаться на сообщение")
        case .person: return t("Пожаловаться на человека")
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).textStyle(.title)
                        .padding(.bottom, 4)
                    Text(t("Что не так? Жалоба анонимна для автора."))
                        .textStyle(.bodySmall, Palette.mist)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.bottom, 6)
                    ForEach(ReportReason.allCases) { r in
                        Button { withAnimation(.easeOut(duration: 0.15)) { reason = r } } label: {
                            HStack {
                                Text(r.title).textStyle(reason == r ? .title : .body, reason == r ? Palette.ink : Palette.mist)
                                Spacer(minLength: 8)
                                if reason == r { Glyph(kind: .check, color: Palette.lilac, size: 18) }
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 10)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                    if reason != nil {
                        GlassField(placeholder: t("Подробнее — необязательно"), text: $note, limit: 500, maxLines: 4)
                            .glass(radius: 16)
                            .padding(.top, 4)
                    }
                    if target.authorId != 0 {
                        Toggle(isOn: $block) {
                            Text(target.authorName.isEmpty ? t("Заблокировать автора") : tf("Заблокировать: %s", target.authorName))
                                .textStyle(.body)
                        }
                        .tint(Palette.orchid)
                        .padding(.top, 8)
                    }
                    Button { rules = .community } label: {
                        Text(t("Правила сообщества")).textStyle(.bodySmall, Palette.lilac)
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 10)
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 12)
            }
            .scrollDismissesKeyboard(.interactively)
            FadeLine()
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onDismiss)
                Spacer()
                GlowButton(title: t("Отправить"), enabled: reason != nil) {
                    if let reason { onSend(reason, note, block) }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
        .fullScreenCover(item: $rules) { doc in
            LegalDocViewer(doc: doc) { rules = nil }
        }
    }
}

/// Окно жалобы на то, что сейчас в model.state.reporting, и подсказка
/// «Спасибо…» после отправки. active = false — окно показывает другой
/// экран (шторка обсуждения поверх: из неё и жалуются на комментарий).
private struct ReportPresenter: ViewModifier {
    let model: AppModel
    let active: Bool
    @State private var notice: String?

    private var reporting: Binding<ReportTarget?> {
        Binding(
            get: { active ? model.state.reporting : nil },
            set: { if $0 == nil { model.cancelReport() } }
        )
    }

    func body(content: Content) -> some View {
        content
            .sheet(item: reporting) { target in
                ReportDialog(
                    target: target,
                    onSend: { reason, note, block in
                        model.report(target, reason, note: note, block: block)
                        notice = t("Спасибо. Жалоба ушла модераторам, у вас это скрыто.")
                    },
                    onDismiss: { model.cancelReport() }
                )
            }
            .toast($notice)
    }
}

extension View {
    /// Жалобы: окно выбора причины поверх этого экрана.
    func reportSheet(model: AppModel, active: Bool = true) -> some View {
        modifier(ReportPresenter(model: model, active: active))
    }
}
