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
                    SectionTitle(text: group.posts.isEmpty ? "Записей пока нет" : "Записи · \(group.posts.count)")
                    ForEach(group.posts) { post in
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
                Text("\(group.topic) · \(plural(group.members, "участник", "участника", "участников"))".uppercased())
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
                    GhostButton(title: "Удалить группу") { confirmDelete = true }
                } else if group.joined {
                    GhostButton(title: "Выйти из группы") { model.toggleGroup(group.id) }
                } else {
                    GlowButton(title: "Вступить") { model.toggleGroup(group.id) }
                }
            }
            if confirmDelete {
                GlassCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Удалить «\(group.name)» вместе с записями? Отменить будет нельзя.")
                            .textStyle(.body)
                            .fixedSize(horizontal: false, vertical: true)
                        HStack(spacing: 10) {
                            GhostButton(title: "Отмена") { confirmDelete = false }
                            GlowButton(title: "Удалить") { model.deleteGroup(group.id) }
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
                    GlassField(placeholder: "Написать в группу", text: $text, limit: 2000)
                    HStack {
                        Spacer()
                        GlowButton(title: "Опубликовать", enabled: !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
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
            Text("Вступите, чтобы писать в группу.")
                .textStyle(.bodySmall, Palette.mist)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
        }
    }
}
