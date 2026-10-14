import SwiftUI

/// Лента активности: кто оценил, прокомментировал, упомянул, позвал в
/// друзья. Сверху — заявки в друзья с кнопками, ниже — «Новое» и «Раньше».
/// То же — ActivityScreen.kt.
struct ActivityScreen: View {
    let model: AppModel

    var body: some View {
        let state = model.state
        let groups = Activity.group(state.activity, state.me, blocked: state.blocked)
        let fresh = groups.filter { $0.at > state.activitySeen }
        let older = groups.filter { $0.at <= state.activitySeen }
        let requests = state.requestPeople
        ScrollView {
            LazyVStack(spacing: 10) {
                HStack(spacing: 14) {
                    RoundGlyphButton(icon: .back) { model.closeActivity() }
                    Text(t("Активность").uppercased())
                        .textStyle(.headline)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .accessibilityAddTraits(.isHeader)
                    Spacer()
                    if !fresh.isEmpty {
                        Button { model.markActivitySeen() } label: {
                            Text(t("Прочитано"))
                                .textStyle(.bodySmall, Palette.lilac)
                                .padding(8)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 4)

                // Заявки в друзья — с ответом прямо здесь
                if !requests.isEmpty {
                    ActivitySection(title: tf("Заявки в друзья · %s", requests.count))
                    ForEach(requests) { person in
                        GlassCard {
                            HStack(spacing: 12) {
                                Button { model.openPerson(person.id) } label: {
                                    Avatar(user: person, size: 44)
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(person.name)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(person.name).textStyle(.title).lineLimit(1)
                                    Text("@" + person.handle).textStyle(.bodySmall, Palette.mist).lineLimit(1)
                                }
                                Spacer(minLength: 4)
                                GhostButton(title: t("Нет")) { model.declineRequest(person.id) }
                                GlowButton(title: t("Принять")) { model.approveRequest(person.id) }
                            }
                            .padding(12)
                        }
                        .cardPadding()
                    }
                }
                if groups.isEmpty && requests.isEmpty {
                    Text(t("Здесь появится, кто оценил и прокомментировал ваши записи, упомянул вас или позвал в друзья."))
                        .textStyle(.bodySmall, Palette.mist)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(24)
                }
                if !fresh.isEmpty {
                    ActivitySection(title: t("Новое"))
                    ForEach(fresh) { g in
                        ActivityRow(group: g, unread: true) { model.openActivityItem(g) }
                    }
                }
                if !older.isEmpty {
                    ActivitySection(title: t("Раньше"))
                    ForEach(older) { g in
                        ActivityRow(group: g, unread: false) { model.openActivityItem(g) }
                    }
                }
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
    }
}

private struct ActivitySection: View {
    let title: String

    var body: some View {
        Text(title.uppercased())
            .textStyle(.label, Palette.mist)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)
            .padding(.vertical, 4)
            .accessibilityAddTraits(.isHeader)
    }
}

private struct ActivityRow: View {
    let group: ActivityGroup
    let unread: Bool
    let onOpen: () -> Void

    var body: some View {
        let text = Activity.text(group)
        let ago = agoTitle(minutesSince(group.at, 0))
        Button(action: onOpen) {
            HStack(spacing: 12) {
                // Два аватара внахлёст, если людей несколько
                ZStack(alignment: .bottomTrailing) {
                    if let first = group.actors.first {
                        Avatar(user: first, size: group.actors.count > 1 ? 36 : 44)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    }
                    if group.actors.count > 1 {
                        Avatar(user: group.actors[1], size: 30)
                            .offset(x: 2, y: 2)
                    }
                }
                .frame(width: 48, height: 48)
                VStack(alignment: .leading, spacing: 2) {
                    Text(text)
                        .textStyle(.body)
                        .lineLimit(3)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(ago).textStyle(.bodySmall, Palette.mist)
                }
                Spacer(minLength: 0)
                if unread {
                    Circle().fill(Palette.lilac).frame(width: 8, height: 8)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .modifier(UnreadGlass(on: unread))
            .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
        .accessibilityLabel(text + ", " + ago)
    }
}

/// Непрочитанное — на стекле, прочитанное — без фона.
private struct UnreadGlass: ViewModifier {
    let on: Bool

    func body(content: Content) -> some View {
        if on {
            content.glass(radius: 18)
        } else {
            content
        }
    }
}

/// Колокольчик с точкой-счётчиком новых событий — для шапки «Ленты».
struct ActivityBell: View {
    let unread: Int
    let action: () -> Void

    var body: some View {
        RoundGlyphButton(
            icon: .bell, color: Palette.lilac,
            label: unread > 0 ? tf("Активность, новых: %s", unread) : t("Активность"),
            action: action
        )
        .overlay(alignment: .topTrailing) {
            if unread > 0 {
                Text(unread > 99 ? "99+" : "\(unread)")
                    .font(.custom(Fonts.interMedium, size: 10))
                    .foregroundStyle(Palette.ink)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 1)
                    .background(Capsule().fill(Palette.orchid))
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
    }
}
