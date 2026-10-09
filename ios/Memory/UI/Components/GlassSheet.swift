import SwiftUI

/// Пункт меню-шторки: значок, название, действие. destructive — удаление
/// и прочее необратимое: розовым.
struct GlassAction: Identifiable {
    let id = UUID()
    let icon: LineIcon
    let title: String
    var destructive = false
    let action: () -> Void
}

/// Меню и подтверждения в стиле приложения — шторкой снизу на тёмном
/// стекле, вместо системных меню (белый текст на чёрном) и диалогов.
/// Действие выполняется после того, как шторка закрылась: так из меню
/// можно сразу открыть другое окно — галерею, камеру, выбор переписки.
private struct GlassActionsSheet: View {
    let title: String?
    let message: String?
    let actions: [GlassAction]
    let cancel: Bool
    let choose: (GlassAction?) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let title {
                Text(title)
                    .textStyle(.title)
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                    .padding(.bottom, message == nil ? 8 : 2)
            }
            if let message {
                Text(message)
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 8)
            }
            ForEach(actions) { a in
                row(icon: a.icon, title: a.title, color: a.destructive ? Palette.pearlPink : Palette.ink, iconColor: a.destructive ? Palette.pearlPink : Palette.lilac) {
                    choose(a)
                }
            }
            if cancel {
                FadeLine().padding(.horizontal, 16).padding(.vertical, 4)
                row(icon: .close, title: t("Отмена"), color: Palette.mist, iconColor: Palette.mist) { choose(nil) }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 14)
        .padding(.bottom, 6)
        .presentationDetents([.height(height)])
        .presentationDragIndicator(.visible)
        .presentationBackground(Palette.night)
        .presentationCornerRadius(28)
    }

    private var height: CGFloat {
        var h: CGFloat = 44
        if title != nil { h += 36 }
        if let message { h += message.count > 60 ? 56 : 32 }
        h += CGFloat(actions.count) * 52
        if cancel { h += 62 }
        return h
    }

    private func row(icon: LineIcon, title: String, color: Color, iconColor: Color, tap: @escaping () -> Void) -> some View {
        Button(action: tap) {
            HStack(spacing: 14) {
                Glyph(kind: icon, color: iconColor, size: 20)
                Text(title).textStyle(.body, color)
                Spacer()
            }
            .padding(.horizontal, 20)
            .frame(height: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private struct GlassActionsModifier: ViewModifier {
    @Binding var isPresented: Bool
    let title: String?
    let message: String?
    let actions: [GlassAction]
    let cancel: Bool
    @State private var pending: (() -> Void)?

    func body(content: Content) -> some View {
        content.sheet(isPresented: $isPresented, onDismiss: {
            let run = pending
            pending = nil
            run?()
        }) {
            GlassActionsSheet(title: title, message: message, actions: actions, cancel: cancel) { chosen in
                pending = chosen?.action
                isPresented = false
            }
        }
    }
}

private struct GlassItemActionsModifier<Item: Identifiable>: ViewModifier {
    @Binding var item: Item?
    let title: (Item) -> String?
    let message: (Item) -> String?
    let actions: (Item) -> [GlassAction]
    let cancel: Bool
    @State private var pending: (() -> Void)?

    func body(content: Content) -> some View {
        content.sheet(item: $item, onDismiss: {
            let run = pending
            pending = nil
            run?()
        }) { current in
            GlassActionsSheet(title: title(current), message: message(current), actions: actions(current), cancel: cancel) { chosen in
                pending = chosen?.action
                item = nil
            }
        }
    }
}

extension View {
    /// Меню или подтверждение шторкой в стиле приложения.
    func glassActions(
        _ isPresented: Binding<Bool>,
        title: String? = nil,
        message: String? = nil,
        cancel: Bool = false,
        actions: [GlassAction]
    ) -> some View {
        modifier(GlassActionsModifier(isPresented: isPresented, title: title, message: message, actions: actions, cancel: cancel))
    }

    /// То же для выбранного элемента: переписки, сообщения, трека.
    func glassActions<Item: Identifiable>(
        item: Binding<Item?>,
        title: @escaping (Item) -> String? = { _ in nil },
        message: @escaping (Item) -> String? = { _ in nil },
        cancel: Bool = false,
        actions: @escaping (Item) -> [GlassAction]
    ) -> some View {
        modifier(GlassItemActionsModifier(item: item, title: title, message: message, actions: actions, cancel: cancel))
    }
}
