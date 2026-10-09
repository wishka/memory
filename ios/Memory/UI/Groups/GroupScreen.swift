import SwiftUI

/// Страница группы: обложка, описание, вступление, записи участников.
struct GroupScreen: View {
    let model: AppModel
    let group: MemoryGroup
    @State private var text = ""
    @State private var confirmDelete = false

    var body: some View {
        GeometryReader { geo in
            ScrollView {
                LazyVStack(spacing: 14) {
                    cover(safeTop: geo.safeAreaInsets.top)
                    about
                    composer
                    let posts = group.posts.filter { !model.state.isBlocked($0.author.id) }
                    SectionTitle(text: posts.isEmpty ? t("Записей пока нет") : tf("Записи · %s", posts.count))
                    ForEach(posts) { post in
                        PostCard(
                            post: post,
                            onLike: { model.toggleGroupPostLike(group.id, post.id) },
                            onAuthor: { model.openPerson(post.author.id) },
                            onComments: { model.openComments(post.id) },
                            onOpenMedia: model.openMedia
                        )
                    }
                }
                .padding(.bottom, tabBarSpace)
            }
            .ignoresSafeArea(edges: .top)
            .scrollDismissesKeyboard(.interactively)
        }
    }

    /// Обложка во всю ширину, под строкой состояния.
    private func cover(safeTop: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            Nebula(seed: group.seed)
            LinearGradient(
                stops: [
                    .init(color: Palette.voidRGB.opacity(0), location: 0.4),
                    .init(color: Palette.void, location: 1),
                ],
                startPoint: .top, endPoint: .bottom
            )
            RoundGlyphButton(icon: .back, strong: true) { model.closeGroup() }
                .padding(.top, safeTop + 12)
                .padding(.leading, 12)
            VStack(alignment: .leading, spacing: 2) {
                Spacer()
                Text(group.name.uppercased()).textStyle(.headline)
                Text("\(t(group.topic)) · \(plural(group.members, "участник", "участника", "участников"))".uppercased())
                    .textStyle(.label, Palette.mist)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        }
        .frame(height: 220 + safeTop)
        .clipped()
    }

    private var about: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !group.description.isEmpty {
                Text(group.description).textStyle(.body).fixedSize(horizontal: false, vertical: true)
            }
            HStack(spacing: 10) {
                if group.mine {
                    GhostButton(title: t("Удалить группу")) { confirmDelete = true }
                } else if group.joined {
                    GhostButton(title: t("Выйти из группы")) { model.toggleGroup(group.id) }
                } else {
                    GlowButton(title: t("Вступить")) { model.toggleGroup(group.id) }
                }
                // Уведомления группы: колокольчик включает и выключает
                if group.joined {
                    Button { model.toggleGroupMute(group.id) } label: {
                        HStack(spacing: 6) {
                            Glyph(kind: group.muted ? .bellOff : .bell, color: Palette.lilac, size: 18)
                            Text(group.muted ? t("Без звука").uppercased() : t("Уведомления").uppercased())
                                .textStyle(.label, group.muted ? Palette.mist : Palette.ink)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .glass(Capsule(), strong: !group.muted)
                    }
                    .buttonStyle(.plain)
                }
            }
            if confirmDelete {
                GlassCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(tf("Удалить «%s» вместе с записями? Отменить будет нельзя.", group.name))
                            .textStyle(.body)
                            .fixedSize(horizontal: false, vertical: true)
                        HStack(spacing: 10) {
                            GhostButton(title: t("Отмена")) { confirmDelete = false }
                            GlowButton(title: t("Удалить")) { model.deleteGroup(group.id) }
                        }
                    }
                    .padding(16)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20)
    }

    @ViewBuilder
    private var composer: some View {
        if group.joined {
            GlassCard {
                VStack(spacing: 0) {
                    GlassField(placeholder: t("Написать в группу"), text: $text, limit: 2000)
                    HStack {
                        Spacer()
                        GlowButton(title: t("Опубликовать"), enabled: !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
                            model.postInGroup(group.id, text)
                            text = ""
                        }
                    }
                    .padding(.trailing, 12)
                    .padding(.bottom, 12)
                }
            }
            .cardPadding()
        } else {
            Text(t("Вступите, чтобы писать в группу."))
                .textStyle(.bodySmall, Palette.mist)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
        }
    }
}
