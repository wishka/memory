import SwiftUI

/// Достижение-кристалл в профиле: за что даётся и получено ли.
struct Achievement: Identifiable {
    let title: String
    let hint: String
    let earned: Bool
    var id: String { title }
}

/// Своё в профиле: достижения, активность, медиа — те же правила, что
/// ProfileHome.kt на Android.
extension UiState {
    /// Моменты своих комментариев — в ленте, группах и PRISM.
    var myCommentDates: [Date?] {
        let all = posts + groups.flatMap(\.posts)
        return all.flatMap(\.commentList).filter { $0.author.id == me.id }.map(\.createdAt)
            + prisms.flatMap(\.commentList).filter { $0.author.id == me.id }.map(\.createdAt)
    }

    var myPrisms: [Prism] { prisms.filter { $0.author.id == me.id } }

    var likesReceived: Int { myPosts.reduce(0) { $0 + $1.likes } + myPrisms.reduce(0) { $0 + $1.likes } }

    var achievements: [Achievement] {
        [
            Achievement(title: t("Первый момент"), hint: t("Опубликовать первую запись"), earned: !myPosts.isEmpty),
            Achievement(title: t("Режиссёр"), hint: t("Снять свой PRISM"), earned: !myPrisms.isEmpty),
            Achievement(title: t("Летописец"), hint: t("10 записей в ленте"), earned: myPosts.count >= 10),
            Achievement(title: t("Душа компании"), hint: t("Состоять в 5 группах"), earned: myGroups.count >= 5),
            Achievement(title: t("Основатель"), hint: t("Создать свою группу"), earned: groups.contains { $0.mine }),
            Achievement(title: t("Меломан"), hint: t("10 треков в «Моей музыке»"), earned: tracks.count >= 10),
            Achievement(title: t("Художник"), hint: t("Сделать стикер в «Студии»"), earned: !stickers.isEmpty),
            Achievement(title: t("Собеседник"), hint: t("Написать 20 комментариев"), earned: myCommentDates.count >= 20),
            Achievement(title: t("Звезда"), hint: t("Собрать 100 «нравится»"), earned: likesReceived >= 100),
            Achievement(title: t("Свой образ"), hint: t("Поставить фото в профиль"), earned: me.hasAvatar),
        ]
    }

    /// Активность за 14 дней: записи, PRISM и комментарии по дням; последний — сегодня.
    func activity(now: Date = Date()) -> [Int] {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: now)
        let moments: [Date?] = myPosts.map(\.createdAt)
            + groups.flatMap(\.posts).filter { $0.author.id == me.id }.map(\.createdAt)
            + myPrisms.map(\.createdAt)
            + myCommentDates
        var counts = Array(repeating: 0, count: 14)
        for case let date? in moments {
            let back = calendar.dateComponents([.day], from: calendar.startOfDay(for: date), to: today).day ?? 99
            if (0...13).contains(back) { counts[13 - back] += 1 }
        }
        return counts
    }
}

private func cardTitle(_ text: String) -> some View {
    Text(text.uppercased()).textStyle(.label, Palette.mist)
}

/// Витрина: закреплённые записи, свои стикеры из «Студии» и «Моя музыка».
struct ShowcaseCard: View {
    let model: AppModel

    var body: some View {
        let state = model.state
        let pinned = state.profilePins.compactMap { id in state.myPosts.first { $0.id == id } }
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                cardTitle(t("Витрина")).padding(.horizontal, 16)
                if pinned.isEmpty {
                    Text(t("Закрепите до трёх своих записей — «⋯» на записи в ленте."))
                        .textStyle(.bodySmall, Palette.mist)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 16)
                } else {
                    HStack(spacing: 10) {
                        ForEach(pinned) { post in
                            PinnedTile(post: post) { model.openComments(post.id) }
                        }
                        ForEach(0..<(3 - pinned.count), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity) }
                    }
                    .padding(.horizontal, 16)
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(state.stickers.prefix(12)) { sticker in
                            Button { model.openProfileSection(.stickers) } label: {
                                StickerImage(sticker: sticker)
                                    .padding(6)
                                    .frame(width: 64, height: 64)
                                    .glass(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            }
                            .buttonStyle(.plain)
                        }
                        Button { model.openProfileSection(.stickers) } label: {
                            VStack(spacing: 2) {
                                Glyph(kind: .plus, color: Palette.lilac, size: 20)
                                Text(t("Студия")).textStyle(.label, Palette.lilac)
                            }
                            .frame(width: 64, height: 64)
                            .glass(RoundedRectangle(cornerRadius: 16, style: .continuous), strong: true)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 16)
                }
                Button { model.openProfileSection(.music) } label: {
                    HStack(spacing: 12) {
                        Glyph(kind: .music, color: Palette.ink, size: 20)
                            .frame(width: 40, height: 40)
                            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(t("Моя музыка")).textStyle(.title)
                            Group {
                                if let last = state.tracks.first {
                                    Text(tf("Новое: %s", last.artist.isEmpty ? last.title : "\(last.artist) — \(last.title)"))
                                } else {
                                    Text(t("Загрузите свои треки"))
                                }
                            }
                            .textStyle(.bodySmall, Palette.mist)
                            .lineLimit(1)
                        }
                        Spacer(minLength: 0)
                        Text("\(state.tracks.count)").textStyle(.bodySmall, Palette.mist)
                    }
                    .padding(.horizontal, 16)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            .padding(.vertical, 16)
        }
        .cardPadding()
    }
}

private struct PinnedTile: View {
    let post: Post
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            ZStack(alignment: .bottomLeading) {
                Group {
                    if let visual = post.media.first(where: { $0.kind != .document }) {
                        MediaTile(attachment: visual, maxPixel: 400)
                    } else if let seed = post.artSeed {
                        Nebula(seed: seed)
                    } else {
                        LinearGradient(colors: [Palette.violet, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)
                    }
                }
                LinearGradient(
                    stops: [.init(color: .clear, location: 0.45), .init(color: Palette.voidRGB.opacity(0.85), location: 1)],
                    startPoint: .top, endPoint: .bottom
                )
                Text(post.text.isEmpty ? t("Момент") : post.text)
                    .textStyle(.labelLarge)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .padding(8)
                Glyph(kind: .pushpin, color: Palette.ink, size: 14)
                    .padding(6)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            }
            .aspectRatio(0.8, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity)
    }
}

/// Активность за две недели и кристаллы-достижения.
struct AchievementsCard: View {
    let state: UiState
    /// Выбранный кристалл — подсказка к нему в карточке, в стиле приложения.
    @State private var selected: String?

    var body: some View {
        let list = state.achievements
        let days = state.activity()
        let total = days.reduce(0, +)
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    cardTitle(t("Активность и достижения"))
                    Spacer()
                    Text(tf("%s из %s", list.filter(\.earned).count, list.count)).textStyle(.labelLarge, Palette.lilac)
                }
                HStack {
                    miniStat(state.myPosts.count, t("записи"))
                    Spacer()
                    miniStat(state.myPrisms.count, t("PRISM"))
                    Spacer()
                    miniStat(state.likesReceived, t("нравится"))
                    Spacer()
                    miniStat(state.stickers.count, t("стикеры"))
                }
                ActivityBars(days: days).frame(height: 56)
                Text(total == 0
                     ? t("За две недели пока тихо — самое время поделиться моментом.")
                     : tf("За две недели: %s", plural(total, "действие", "действия", "действий")))
                    .textStyle(.bodySmall, Palette.mist)
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 5), spacing: 8) {
                    ForEach(list) { a in
                        Button { withAnimation { selected = selected == a.title ? nil : a.title } } label: {
                            VStack(spacing: 4) {
                                AchievementCrystal(earned: a.earned).frame(width: 36, height: 36)
                                Text(a.title)
                                    .textStyle(.label, a.earned ? Palette.ink : Palette.mist)
                                    .multilineTextAlignment(.center)
                                    .lineLimit(2)
                            }
                            .padding(.vertical, 4)
                            .frame(maxWidth: .infinity)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(selected == a.title ? Palette.plum.opacity(0.7) : .clear)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                if let a = list.first(where: { $0.title == selected }) {
                    HStack(spacing: 10) {
                        AchievementCrystal(earned: a.earned).frame(width: 22, height: 22)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(a.title).textStyle(.title)
                            Text(a.earned ? tf("Получено: %s", a.hint) : tf("Чтобы получить: %s", a.hint))
                                .textStyle(.bodySmall, a.earned ? Palette.lilac : Palette.mist)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .glass(RoundedRectangle(cornerRadius: 16, style: .continuous), strong: a.earned)
                    .transition(.opacity)
                }
            }
            .padding(16)
        }
        .cardPadding()
    }

    private func miniStat(_ value: Int, _ label: String) -> some View {
        VStack(spacing: 0) {
            Text("\(value)").textStyle(.headline)
            Text(label.uppercased()).textStyle(.label, Palette.mist)
        }
    }
}

/// Столбики активности: последний — сегодня.
struct ActivityBars: View {
    let days: [Int]

    var body: some View {
        Canvas { ctx, size in
            let top = max(1, days.max() ?? 0)
            let gap: CGFloat = 4
            let w = (size.width - gap * CGFloat(days.count - 1)) / CGFloat(days.count)
            for (i, v) in days.enumerated() {
                let h = v == 0 ? 3 : max(6, size.height * CGFloat(v) / CGFloat(top))
                let rect = CGRect(x: CGFloat(i) * (w + gap), y: size.height - h, width: w, height: h)
                let path = Path(roundedRect: rect, cornerRadius: w / 3)
                if v == 0 {
                    ctx.fill(path, with: .color(Palette.plum))
                } else {
                    ctx.fill(path, with: .linearGradient(Gradient(colors: [Palette.lilac, Palette.ocean]), startPoint: CGPoint(x: 0, y: rect.minY), endPoint: CGPoint(x: 0, y: rect.maxY)))
                }
            }
        }
    }
}

/// Кристалл достижения: получено — перламутр, нет — тусклый контур.
struct AchievementCrystal: View {
    let earned: Bool

    var body: some View {
        Canvas { ctx, size in
            let s = size.width
            var path = Path()
            path.move(to: CGPoint(x: s * 0.5, y: s * 0.02))
            path.addLine(to: CGPoint(x: s * 0.84, y: s * 0.5))
            path.addLine(to: CGPoint(x: s * 0.5, y: s * 0.98))
            path.addLine(to: CGPoint(x: s * 0.16, y: s * 0.5))
            path.closeSubpath()
            if earned {
                ctx.fill(path, with: .linearGradient(Gradient(colors: Glyph.crystalColors), startPoint: .zero, endPoint: CGPoint(x: s, y: s)))
                var facets = Path()
                facets.move(to: CGPoint(x: s * 0.16, y: s * 0.5)); facets.addLine(to: CGPoint(x: s * 0.84, y: s * 0.5))
                facets.move(to: CGPoint(x: s * 0.5, y: s * 0.02)); facets.addLine(to: CGPoint(x: s * 0.56, y: s * 0.5))
                ctx.stroke(facets, with: .color(.white.opacity(0.7)), lineWidth: s * 0.03)
                ctx.stroke(path, with: .color(.white.opacity(0.8)), lineWidth: s * 0.04)
            } else {
                ctx.fill(path, with: .color(Palette.plum.opacity(0.6)))
                ctx.stroke(path, with: .color(Palette.mist.opacity(0.5)), lineWidth: s * 0.04)
            }
        }
    }
}

/// Свои записи, PRISM и медиа — вкладками.
struct ProfileContentTabs: View {
    let model: AppModel
    @State private var tab = 0

    var body: some View {
        let state = model.state
        let myPrisms = state.myPrisms
        let media = state.myPosts.flatMap { $0.media.filter { $0.kind != .document } } + myPrisms.compactMap(\.cover)
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Chip(text: tf("Записи · %s", state.myPosts.count), active: tab == 0) { tab = 0 }
                Chip(text: tf("PRISM · %s", myPrisms.count), active: tab == 1) { tab = 1 }
                Chip(text: tf("Медиа · %s", media.count), active: tab == 2) { tab = 2 }
            }
            .padding(.horizontal, 16)
            switch tab {
            case 0:
                if state.myPosts.isEmpty { empty(t("Пока ни одной записи. Поделитесь первым моментом во вкладке «Лента».")) }
                ForEach(state.myPosts.prefix(3)) { post in
                    PostCard(
                        post: post,
                        onLike: { model.toggleLike(post.id) },
                        onComments: { model.openComments(post.id) },
                        onOpenMedia: model.openMedia
                    )
                }
                if state.myPosts.count > 3 {
                    GhostButton(title: t("Все записи")) { model.openProfileSection(.posts) }
                        .padding(.horizontal, 16)
                }
            case 1:
                if myPrisms.isEmpty { empty(t("Своих PRISM пока нет — «+» во вкладке PRISM.")) }
                grid {
                    ForEach(myPrisms) { prism in
                        Button { model.openPrism(prism.id) } label: { PrismTile(prism: prism) }.buttonStyle(.plain)
                    }
                }
            default:
                if media.isEmpty { empty(t("Здесь соберутся фото и видео из ваших записей и обложки PRISM.")) }
                grid {
                    ForEach(media) { a in
                        MediaTile(attachment: a, maxPixel: 360) { model.openMedia(a) }
                            .aspectRatio(1, contentMode: .fill)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }
            }
        }
    }

    private func empty(_ text: String) -> some View {
        Text(text).textStyle(.bodySmall, Palette.mist).fixedSize(horizontal: false, vertical: true).padding(.horizontal, 24)
    }

    private func grid<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 3), spacing: 6) { content() }
            .padding(.horizontal, 16)
    }
}

private struct PrismTile: View {
    let prism: Prism

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            if let cover = prism.cover {
                MediaTile(attachment: cover, maxPixel: 360)
            } else {
                PrismArt(spec: PrismSpec(seed: prism.seed), timeMs: 1_500)
            }
            HStack(spacing: 4) {
                Glyph(kind: .play, color: Palette.ink, size: 12)
                Text("\(prism.likes)").textStyle(.label)
            }
            .padding(6)
        }
        .aspectRatio(0.62, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

/// Мои группы и взаимные подписки — лентами.
struct PeopleRows: View {
    let model: AppModel

    var body: some View {
        let state = model.state
        let mutual = state.followers.filter { state.following.contains($0.id) && !state.isBlocked($0.id) }
        if !state.myGroups.isEmpty || !mutual.isEmpty {
            GlassCard {
                VStack(alignment: .leading, spacing: 12) {
                    if !state.myGroups.isEmpty {
                        cardTitle(tf("Мои группы · %s", state.myGroups.count)).padding(.horizontal, 16)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(state.myGroups) { group in
                                    Button { model.openGroup(group.id) } label: {
                                        VStack(spacing: 6) {
                                            GroupCover(group: group, maxPixel: 240)
                                                .frame(width: 72, height: 72)
                                                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                                            Text(group.name).textStyle(.labelLarge).lineLimit(1)
                                        }
                                        .frame(width: 84)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                    if !mutual.isEmpty {
                        cardTitle(tf("Взаимные подписки · %s", mutual.count)).padding(.horizontal, 16)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(mutual) { person in
                                    Button { model.openPerson(person.id) } label: {
                                        VStack(spacing: 6) {
                                            Avatar(user: person, size: 56, ring: true, showOnline: true)
                                            Text(person.name.split(separator: " ").first.map(String.init) ?? person.name)
                                                .textStyle(.labelLarge)
                                                .lineLimit(1)
                                        }
                                        .frame(width: 72)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                }
                .padding(.vertical, 16)
            }
            .cardPadding()
        }
    }
}
