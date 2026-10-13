import SwiftUI

/// Гид по Memory — страница настроек: список глав с поиском или одна глава.
/// Тексты — на выбранном языке (GuideText), кнопки ведут туда, о чём тема.
/// Что ищут и что раскрыто — живёт на экране настроек (query, expanded),
/// чтобы пережить переход между главами.
struct GuidePage: View {
    let model: AppModel
    @Binding var query: String
    @Binding var expanded: Set<String>

    var body: some View {
        let state = model.state
        if let chapter = Guide.chapter(state.guideChapter) {
            GuideChapterHead(chapter: chapter, read: state.guideRead)
            ForEach(chapter.topics.indices, id: \.self) { i in
                let topic = chapter.topics[i]
                let key = chapter.key(topic)
                GuideTopicCard(
                    topic: topic,
                    label: nil,
                    number: i + 1,
                    expanded: expanded.contains(key),
                    read: state.guideRead.contains(key),
                    onToggle: { toggle(chapter, topic) },
                    onAction: { model.guideGo($0) }
                )
            }
            GuideFooter(model: model, chapter: chapter)
        } else {
            GuideIntro(read: state.guideRead.count, query: $query)
            if query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                ForEach(Guide.chapters) { c in
                    GuideChapterCard(chapter: c, read: state.guideRead) { model.openGuideChapter(c.id) }
                }
            } else {
                let hits = Guide.search(query)
                if hits.isEmpty {
                    Text(t("Ничего не нашлось. Попробуйте другое слово."))
                        .textStyle(.bodySmall, Palette.mist)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 24)
                } else {
                    ForEach(hits) { hit in
                        GuideTopicCard(
                            topic: hit.topic,
                            label: hit.chapter.emoji + "  " + hit.chapter.title.text,
                            number: nil,
                            expanded: expanded.contains(hit.id),
                            read: state.guideRead.contains(hit.id),
                            onToggle: { toggle(hit.chapter, hit.topic) },
                            onAction: { model.guideGo($0) }
                        )
                    }
                }
            }
        }
    }

    /// Раскрыли тему — она прочитана; повторное нажатие сворачивает.
    private func toggle(_ chapter: GuideChapter, _ topic: GuideTopic) {
        let key = chapter.key(topic)
        if expanded.contains(key) {
            expanded.remove(key)
        } else {
            model.markGuideRead(chapter, topic)
            expanded.insert(key)
        }
    }
}

// MARK: - Части

/// Вверху списка глав: что это, сколько прочитано и поиск.
private struct GuideIntro: View {
    let read: Int
    @Binding var query: String

    var body: some View {
        let total = Guide.topicCount
        let done = min(read, total)
        VStack(spacing: 12) {
            GlassCard {
                VStack(alignment: .leading, spacing: 10) {
                    Text(t("Как устроен Memory — по шагам. Раскройте тему, а кнопка внизу темы откроет нужный экран."))
                        .textStyle(.bodySmall)
                        .fixedSize(horizontal: false, vertical: true)
                    GuideProgressLine(done: done, total: total)
                    Text(tf("Прочитано %s из %s", done, plural(total, "тема", "темы", "тем")))
                        .textStyle(.labelLarge, Palette.mist)
                }
                .padding(18)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            GlassField(placeholder: t("Найти в гиде: «витрина», «пароль», «голосовые»…"), text: $query, limit: 60, singleLine: true)
                .glass(radius: 16)
        }
        .padding(.horizontal, 16)
    }
}

/// Полоска прогресса в цветах приложения.
struct GuideProgressLine: View {
    let done: Int
    let total: Int

    var body: some View {
        let part: CGFloat = total == 0 ? 0 : min(1, CGFloat(done) / CGFloat(total))
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.plum)
                if part > 0 {
                    Capsule()
                        .fill(LinearGradient(colors: [Palette.lilac, Palette.sky], startPoint: .leading, endPoint: .trailing))
                        .frame(width: geo.size.width * part)
                }
            }
        }
        .frame(height: 6)
    }
}

/// Значок главы в круглом градиенте.
struct GuideEmojiBadge: View {
    let emoji: String
    var big: Bool = false

    var body: some View {
        let size: CGFloat = big ? 64 : 48
        Text(emoji)
            .font(.system(size: big ? 30 : 22))
            .frame(width: size, height: size)
            .background(
                Circle().fill(
                    LinearGradient(
                        colors: [Palette.orchid.opacity(0.55), Palette.ocean.opacity(0.45)],
                        startPoint: .topLeading, endPoint: .bottomTrailing
                    )
                )
            )
            .overlay(Circle().strokeBorder(Palette.line, lineWidth: 1))
    }
}

/// Глава в списке: значок, название, о чём, сколько прочитано.
private struct GuideChapterCard: View {
    let chapter: GuideChapter
    let read: Set<String>
    let onOpen: () -> Void

    var body: some View {
        let count = chapter.topics.filter { read.contains(chapter.key($0)) }.count
        let done = count == chapter.topics.count
        let topics = plural(chapter.topics.count, "тема", "темы", "тем")
        Button(action: onOpen) {
            GlassCard {
                HStack(spacing: 14) {
                    GuideEmojiBadge(emoji: chapter.emoji)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(chapter.title.text).textStyle(.title).lineLimit(1)
                        Text(chapter.lead.text).textStyle(.bodySmall, Palette.mist).lineLimit(2)
                        Text(done ? t("Прочитано") : count == 0 ? topics : tf("%s из %s", count, topics))
                            .textStyle(.labelLarge, done ? Palette.lilac : Palette.mist)
                    }
                    Spacer(minLength: 8)
                    Glyph(kind: done ? .check : .chevronDown, color: done ? Palette.lilac : Palette.mist, size: 20)
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .cardPadding()
    }
}

/// Шапка главы.
private struct GuideChapterHead: View {
    let chapter: GuideChapter
    let read: Set<String>

    var body: some View {
        let count = chapter.topics.filter { read.contains(chapter.key($0)) }.count
        VStack(spacing: 8) {
            GuideEmojiBadge(emoji: chapter.emoji, big: true)
            Text(chapter.title.text)
                .textStyle(.headline)
                .multilineTextAlignment(.center)
            Text(chapter.lead.text)
                .textStyle(.bodySmall, Palette.mist)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            GuideProgressLine(done: count, total: chapter.topics.count)
                .padding(.horizontal, 40)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 16)
    }
}

/// Тема: заголовок, по нажатию — шаги по номерам, совет и кнопка
/// «Открыть …». label — глава (в результатах поиска).
private struct GuideTopicCard: View {
    let topic: GuideTopic
    let label: String?
    let number: Int?
    let expanded: Bool
    let read: Bool
    let onToggle: () -> Void
    let onAction: (GuideAction) -> Void

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 0) {
                Button(action: onToggle) {
                    HStack(spacing: 12) {
                        if let number {
                            ZStack {
                                Circle().fill(read ? Palette.lilac.opacity(0.22) : Palette.plum)
                                if read {
                                    Glyph(kind: .check, color: Palette.lilac, size: 16)
                                } else {
                                    Text("\(number)").textStyle(.labelLarge)
                                }
                            }
                            .frame(width: 28, height: 28)
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            if let label {
                                Text(label).textStyle(.labelLarge, Palette.mist).lineLimit(1)
                            }
                            Text(topic.title.text)
                                .textStyle(.title)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 8)
                        Glyph(kind: expanded ? .close : .chevronDown, color: Palette.mist, size: 18)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                if expanded {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(topic.steps.indices, id: \.self) { i in
                            HStack(alignment: .top, spacing: 10) {
                                Text("\(i + 1)")
                                    .font(.custom(Fonts.interMedium, size: 11))
                                    .foregroundStyle(Palette.ink)
                                    .frame(width: 22, height: 22)
                                    .background(
                                        Circle().fill(
                                            LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)
                                        )
                                    )
                                Text(topic.steps[i].text)
                                    .textStyle(.bodySmall)
                                    .fixedSize(horizontal: false, vertical: true)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                        if let tip = topic.tip {
                            HStack(alignment: .top, spacing: 10) {
                                Glyph(kind: .info, color: Palette.sky, size: 18)
                                Text(tip.text)
                                    .textStyle(.bodySmall)
                                    .fixedSize(horizontal: false, vertical: true)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .padding(12)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Palette.sky.opacity(0.12))
                            )
                        }
                        if let action = topic.action {
                            GlowButton(title: tf("Открыть: %s", action.title)) { onAction(action) }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .cardPadding()
    }
}

/// Внизу главы: ко всем главам и к следующей.
private struct GuideFooter: View {
    let model: AppModel
    let chapter: GuideChapter

    var body: some View {
        let chapters = Guide.chapters
        let index = chapters.firstIndex { $0.id == chapter.id } ?? chapters.count
        let next: GuideChapter? = index + 1 < chapters.count ? chapters[index + 1] : nil
        HStack(spacing: 10) {
            GhostButton(title: t("Все главы")) { model.closeGuideChapter() }
            if let next {
                GlowButton(title: tf("Дальше: %s", next.title.text), fullWidth: true) {
                    model.openGuideChapter(next.id)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
    }
}

extension GuideAction {
    /// Название места, куда ведёт кнопка темы.
    var title: String {
        switch self {
        case .feed: return AppTab.feed.title
        case .stories: return AppTab.stories.title
        case .prism: return AppTab.prism.title
        case .newPrism: return t("Новый ролик")
        case .chats: return AppTab.chats.title
        case .groups: return AppTab.groups.title
        case .together: return AppTab.together.title
        case .profile: return AppTab.profile.title
        case .friends: return ProfileSection.friends.title
        case .requests: return ProfileSection.requests.title
        case .saved: return ProfileSection.saved.title
        case .music: return ProfileSection.music.title
        case .studio: return ProfileSection.stickers.title
        case .shelf: return ProfileSection.shelf.title
        case .recap: return ProfileSection.recap.title
        case .settings: return SettingsPage.main.title
        case .privacy: return SettingsPage.privacy.title
        case .security: return SettingsPage.security.title
        case .blacklist: return SettingsPage.blacklist.title
        case .notifications: return SettingsPage.notifications.title
        case .sounds: return SettingsPage.sounds.title
        case .wallpaper: return SettingsPage.wallpaper.title
        case .language: return SettingsPage.language.title
        case .legal: return SettingsPage.legal.title
        case .growth: return SettingsPage.growth.title
        case .premium: return ProfileSection.premium.title
        }
    }
}

/// Карточка в ленте для новичка: открыть гид или скрыть карточку.
struct GuideFeedCard: View {
    let model: AppModel

    var body: some View {
        GlassCard {
            HStack(alignment: .top, spacing: 14) {
                GuideEmojiBadge(emoji: "🧭")
                VStack(alignment: .leading, spacing: 8) {
                    Text(t("Гид по Memory")).textStyle(.title)
                    Text(t("Как здесь всё устроено — коротко и по шагам: записи, PRISM, группы, витрины, друзья."))
                        .textStyle(.bodySmall, Palette.mist)
                        .fixedSize(horizontal: false, vertical: true)
                    HStack(spacing: 10) {
                        GlowButton(title: t("Открыть гид")) { model.openGuide() }
                        GhostButton(title: t("Скрыть")) { model.hideGuideCard() }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(16)
        }
        .cardPadding()
    }
}
