import SwiftUI

/// Нижняя панель: парящая «капсула» с семью вкладками —
/// Лента · Истории · PRISM · Чаты · Группы · Вместе · Профиль.
///
/// Почти непрозрачная: под ней прокручивается лента, и сквозь слишком
/// прозрачное стекло текст записей мешал читать значки. Подпись видна
/// только у выбранной вкладки — семь подписей на узком экране не
/// помещаются. Выбранная вкладка светится сиренью, под ней короткая
/// светящаяся черта. На «Чатах» — счётчик непрочитанных.
struct BottomBar: View {
    let selected: AppTab
    let unread: Int
    let onSelect: (AppTab) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(AppTab.allCases) { tab in
                item(tab)
            }
        }
        .frame(height: 68)
        .background(
            RoundedRectangle(cornerRadius: 28, style: .continuous).fill(Palette.nightRGB.opacity(0.92))
        )
        .glass(RoundedRectangle(cornerRadius: 28, style: .continuous), strong: true)
        .padding(.horizontal, 12)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isTabBar)
    }

    private func icon(_ tab: AppTab) -> LineIcon {
        switch tab {
        case .feed: return .feed
        case .stories: return .stories
        case .prism: return .prism
        case .chats: return .chats
        case .groups: return .groups
        case .together: return .together
        case .profile: return .profile
        }
    }

    private func item(_ tab: AppTab) -> some View {
        let active = tab == selected
        let tint = active ? Palette.lilac : Palette.mist
        return Button {
            onSelect(tab)
        } label: {
            VStack(spacing: 2) {
                ZStack {
                    // Мягкое свечение за значком выбранной вкладки
                    Circle()
                        .fill(RadialGradient(
                            colors: [Palette.orchidRGB.opacity(0.45), Palette.orchidRGB.opacity(0)],
                            center: .center, startRadius: 0, endRadius: 20
                        ))
                        .frame(width: 40, height: 40)
                        .opacity(active ? 1 : 0)
                    Glyph(kind: icon(tab), color: tint)
                    if tab == .chats && unread > 0 {
                        Text("\(unread)")
                            .font(.custom(Fonts.interMedium, size: 10))
                            .foregroundStyle(Palette.ink)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 1)
                            .background(Capsule().fill(LinearGradient(colors: [Palette.orchid, Palette.sky], startPoint: .leading, endPoint: .trailing)))
                            .offset(x: 13, y: -11)
                    }
                }
                .frame(height: 28)
                if active {
                    Text(tab.title.uppercased())
                        .textStyle(.label, tint)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Capsule()
                    .fill(LinearGradient(colors: [Palette.lilac, Palette.sky], startPoint: .leading, endPoint: .trailing))
                    .frame(width: 18, height: 2)
                    .opacity(active ? 1 : 0)
                    .padding(.top, 2)
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
            .animation(.easeOut(duration: 0.2), value: active)
        }
        .buttonStyle(.plain)
        // Вкладка: название, у «Чатов» — сколько непрочитанных; выбранная отмечена
        .accessibilityLabel(tab == .chats && unread > 0 ? tf("%s, непрочитанных: %s", tab.title, unread) : tab.title)
        .accessibilityAddTraits(active ? [.isSelected] : [])
    }
}
