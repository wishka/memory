import SwiftUI

/// Дневники процесса: «День 3: учусь жонглировать». Выкладывают не
/// результат, а путь, а сообщество отвечает советами — вместо «нравится».
/// То же — в TogetherJourneys.kt.
struct JourneysSection: View {
    let model: AppModel
    @State private var openId: Int64?
    @State private var creating = false

    var body: some View {
        let state = model.state
        let journeys = state.together.journeys
        VStack(alignment: .leading, spacing: 12) {
            if let open = journeys.first(where: { $0.id == openId }) {
                JourneyPage(model: model, journey: open) { openId = nil }
            } else {
                TogetherCard {
                    TogetherHint(text: t("Короткий челлендж: выкладывайте процесс по дням — «День 3: учусь жонглировать». Вместо лайков здесь советы, а автор отмечает полезные."))
                    GlowButton(title: t("Начать свой челлендж")) { creating = true }
                }
                let mine = journeys.filter { $0.authorId == state.me.id }
                if !mine.isEmpty {
                    TogetherCaption(text: t("Мои челленджи"))
                    ForEach(mine) { j in
                        JourneyCard(state: state, journey: j) { openId = j.id }
                    }
                }
                TogetherCaption(text: t("Сообщество учится"))
                ForEach(journeys.filter { $0.authorId != state.me.id && !state.blocked.contains($0.authorId) }) { j in
                    JourneyCard(state: state, journey: j) { openId = j.id }
                }
            }
        }
        .sheet(isPresented: $creating) {
            NewJourneySheet(model: model) { creating = false }
        }
    }
}

private struct NewJourneySheet: View {
    let model: AppModel
    let onClose: () -> Void
    @State private var emoji = "🔥"
    @State private var title = ""
    @State private var days = 30

    private let emojis = ["🔥", "🤹", "🎸", "🗣️", "🏃", "🎨", "🧘", "📖", "🧶", "🍳"] + Array(checkInEmoji.prefix(8))

    var body: some View {
        TogetherSheet(title: t("Свой челлендж")) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(Array(emojis.enumerated()), id: \.offset) { _, e in
                        Button { emoji = e } label: {
                            Text(e)
                                .font(.system(size: 22))
                                .padding(8)
                                .glass(Capsule(), strong: e == emoji)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            TogetherField(placeholder: t("Чему учитесь? «Учусь жонглировать»"), text: $title, limit: 60)
            TogetherCaption(text: t("Сколько дней"))
            HStack(spacing: 8) {
                ForEach([7, 14, 30, 100], id: \.self) { d in
                    Chip(text: tf("%s дн.", d), active: days == d) { days = d }
                }
            }
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onClose)
                GlowButton(title: t("Начать"), enabled: !title.trimmingCharacters(in: .whitespaces).isEmpty) {
                    if model.startJourney(emoji: emoji, title: title, days: days) { onClose() }
                }
            }
        }
    }
}

private struct JourneyCard: View {
    let state: UiState
    let journey: Journey
    let onOpen: () -> Void

    var body: some View {
        let j = journey
        let author = personOf(state, j.authorId)
        let tips = j.entries.reduce(0) { $0 + $1.tips.count }
        TogetherCard {
            Button(action: onOpen) {
                HStack(spacing: 12) {
                    EmojiBadge(emoji: j.emoji)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(j.title).textStyle(.title).multilineTextAlignment(.leading)
                        Text(tf("%s · день %s из %s", author.name, j.entries.count, j.days)).textStyle(.bodySmall, Palette.mist)
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            TogetherProgress(progress: Double(j.entries.count) / Double(max(1, j.days)))
            if let last = j.entries.last {
                Text(tf("День %s: %s", last.day, last.text)).textStyle(.bodySmall).lineLimit(2)
            }
            Text(tf("Советов: %s", tips)).textStyle(.labelLarge, Palette.lilac)
        }
    }
}

private struct JourneyPage: View {
    let model: AppModel
    let journey: Journey
    let onBack: () -> Void
    @State private var text = ""
    @State private var media: [Attachment] = []
    @State private var sharing: SharePayload?

    var body: some View {
        let state = model.state
        let j = journey
        let mine = j.authorId == state.me.id
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                GhostButton(title: t("Назад"), action: onBack)
                Text("\(j.emoji) \(j.title)").textStyle(.title)
                Spacer(minLength: 0)
            }
            TogetherCard {
                Text(tf("%s · день %s из %s", personOf(state, j.authorId).name, j.entries.count, j.days)).textStyle(.bodySmall, Palette.mist)
                TogetherProgress(progress: Double(j.entries.count) / Double(max(1, j.days)))
            }
            if mine {
                TogetherCard {
                    TogetherCaption(text: tf("День %s", j.nextDay))
                    TogetherField(placeholder: t("Что получилось и что нет?"), text: $text, limit: 1000, singleLine: false)
                    PendingAttachments(items: media) { item in
                        media.removeAll { $0.id == item.id }
                        MediaFiles.delete(item)
                    }
                    HStack(spacing: 10) {
                        AttachButton(documents: false, size: 40) { picked in
                            media = Array((media + picked).prefix(4))
                            // Сверх четырёх — не прикладываем: копии удаляем
                            for extra in picked where !media.contains(extra) { MediaFiles.delete(extra) }
                        }
                        GlowButton(title: t("Записать день"), enabled: !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !media.isEmpty) {
                            model.addJourneyEntry(j.id, text: text, media: media)
                            text = ""
                            media = []
                        }
                    }
                }
            }
            if mine {
                HStack(spacing: 10) {
                    GhostButton(title: t("Поделиться")) {
                        let spec = ShareCardSpec(
                            kicker: t("Челлендж"), emoji: j.emoji, title: j.title,
                            subtitle: tf("День %s из %s", j.entries.count, j.days),
                            lines: j.entries.suffix(3).reversed().map { EchoLine(emoji: "📓", text: tf("День %s: %s", $0.day, $0.text)) },
                            author: "\(state.me.name) · @\(state.me.handle)"
                        )
                        sharing = shareCard(model, spec, name: "journey-\(j.id)", action: .journey, targetId: "\(j.id)", title: j.title)
                    }
                    GhostButton(title: t("Позвать за советом")) {
                        sharing = shareInvite(model, .journey, targetId: "\(j.id)", title: j.title)
                    }
                }
            }
            if j.entries.isEmpty {
                TogetherHint(text: t("Пока ни одного дня. Первый шаг — самый важный."))
            }
            ForEach(Array(j.entries.reversed())) { e in
                EntryCard(model: model, journey: j, entry: e, mine: mine)
            }
        }
        .shareSheet($sharing)
    }
}

private struct EntryCard: View {
    let model: AppModel
    let journey: Journey
    let entry: JourneyEntry
    let mine: Bool
    @State private var tip = ""

    var body: some View {
        let state = model.state
        let e = entry
        TogetherCard {
            Text(tf("День %s", e.day)).textStyle(.labelLarge, Palette.lilac)
            Text(e.text).textStyle(.body).fixedSize(horizontal: false, vertical: true)
            PostMediaView(media: e.media) { model.openMedia($0) }
            Text(agoTitle(minutesSince(e.createdAt, 0))).textStyle(.bodySmall, Palette.mist)
            if !e.tips.isEmpty {
                TogetherCaption(text: tf("Советы · %s", e.tips.count))
            }
            ForEach(e.tips.togetherSorted(descending: true) { $0.helpful }) { tp in
                let author = personOf(state, tp.authorId)
                VStack(alignment: .leading, spacing: 6) {
                    PersonLine(user: author, subtitle: tp.text) { model.openPerson(author.id) }
                    Chip(text: (tp.markedByMe ? "✓ " : "") + tf("Полезно · %s", tp.helpful), active: tp.markedByMe) {
                        model.toggleHelpful(journey.id, e.id, tp.id)
                    }
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .glass(radius: 16)
            }
            if !mine {
                TogetherField(placeholder: t("Дать совет — как сделать лучше?"), text: $tip, limit: 500, singleLine: false)
                GlowButton(title: t("Отправить совет"), enabled: !tip.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
                    model.addTip(journey.id, e.id, tip)
                    tip = ""
                }
            }
        }
    }
}
