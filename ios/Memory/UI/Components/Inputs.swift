import SwiftUI
import UIKit

/// Поле ввода без системных рамок: текст на стекле, курсор сиреневый.
/// limit — сколько символов можно ввести; лишнее отрезается.
struct GlassField: View {
    let placeholder: String
    @Binding var text: String
    var limit: Int = 2000
    var singleLine: Bool = false
    var maxLines: Int = 6
    var secure: Bool = false
    var keyboard: UIKeyboardType = .default
    /// Логин и пароль: без автозамены и заглавной буквы.
    var plain: Bool = false

    var body: some View {
        field
            .font(TextStyle.body.font)
            .foregroundStyle(Palette.ink)
            .tint(Palette.lilac)
            .keyboardType(keyboard)
            .textInputAutocapitalization(plain ? .never : .sentences)
            .autocorrectionDisabled(plain)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .onChange(of: text) { _, value in
                if value.count > limit { text = String(value.prefix(limit)) }
            }
    }

    @ViewBuilder
    private var field: some View {
        let prompt = Text(placeholder).foregroundStyle(Palette.mist)
        if secure {
            SecureField("", text: $text, prompt: prompt)
        } else if singleLine {
            TextField("", text: $text, prompt: prompt)
        } else {
            TextField("", text: $text, prompt: prompt, axis: .vertical)
                .lineLimit(1...maxLines)
        }
    }
}

/// Кнопка-капсула с градиентом орхидея → синий.
struct GlowButton: View {
    let title: String
    var enabled: Bool = true
    var fullWidth: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title.uppercased())
                .textStyle(.label)
                .lineLimit(1)
                .padding(.horizontal, 20)
                .padding(.vertical, 10)
                .frame(maxWidth: fullWidth ? .infinity : nil)
                .background(
                    Capsule().fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .leading, endPoint: .trailing))
                )
                .opacity(enabled ? 1 : 0.4)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// Тихая кнопка: только стекло и тонкая рамка.
struct GhostButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title.uppercased())
                .textStyle(.label, Palette.lilac)
                .lineLimit(1)
                .padding(.horizontal, 20)
                .padding(.vertical, 10)
                .glass(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Метка-капсула: тема группы, интерес. active — выбрана.
struct Chip: View {
    let text: String
    var active: Bool = false
    var action: (() -> Void)? = nil

    var body: some View {
        let label = Text(text)
            .textStyle(.labelLarge, active ? Palette.ink : Palette.lilac)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .glass(Capsule(), strong: active)
        if let action {
            Button(action: action) { label }.buttonStyle(.plain)
        } else {
            label
        }
    }
}

/// Раскладка «по строкам»: элементы идут слева направо и переносятся,
/// когда строка кончилась. Для меток интересов и тем.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var widest: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > maxWidth {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
            widest = max(widest, x - spacing)
        }
        return CGSize(width: proposal.width ?? widest, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

/// Таблетка, которая раскрывает список под собой: «Все темы ▾»,
/// «Интересы · 3 ▾». open — список раскрыт, стрелка смотрит вверх.
struct DisclosurePill: View {
    let text: String
    let open: Bool
    var active: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(text).textStyle(.labelLarge, active || open ? Palette.ink : Palette.lilac)
                Glyph(kind: .chevronDown, color: Palette.lilac, size: 18)
                    .rotationEffect(.degrees(open ? 180 : 0))
            }
            .padding(.leading, 14)
            .padding(.trailing, 10)
            .padding(.vertical, 8)
            .glass(Capsule(), strong: active || open)
        }
        .buttonStyle(.plain)
    }
}
