import SwiftUI
import UIKit

/// Итоги месяца или года: картинка с личной статистикой — фильмы,
/// километры, книги, места, записи. Её можно опубликовать (в ленту и
/// истории), отправить другу в чат или сохранить в «Фото». Стрелки
/// листают прошлые периоды. То же — в RecapScreen.kt.
struct RecapScreen: View {
    let model: AppModel
    @State private var poster: UIImage?
    @State private var choosingChat = false
    @State private var sharing: SharedImage?

    private var back: Int { model.state.recapBack }
    @State private var notice: String?

    private var recap: Recap {
        let now = Date()
        let calendar = Calendar.current
        if model.state.recapPeriod == .year {
            return model.recap(.year, year: calendar.component(.year, from: now) - back, month: 0)
        }
        let date = calendar.date(byAdding: .month, value: -back, to: now) ?? now
        let c = calendar.dateComponents([.year, .month], from: date)
        return model.recap(.month, year: c.year ?? 2026, month: c.month ?? 1)
    }

    var body: some View {
        let recap = self.recap
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 14) {
                        RoundGlyphButton(icon: .back) { model.closeProfileSection() }
                        ScreenTitle(title: t("Итоги"), subtitle: t("Ваш месяц и год в цифрах"))
                    }
                    .padding(.horizontal, 12)
                    HStack(spacing: 8) {
                        Chip(text: t("Месяц"), active: model.state.recapPeriod == .month) { setPeriod(.month) }
                        Chip(text: t("Год"), active: model.state.recapPeriod == .year) { setPeriod(.year) }
                    }
                    .padding(.horizontal, 20)
                    HStack(spacing: 12) {
                        RoundGlyphButton(icon: .back, color: Palette.lilac) { model.setRecapBack(back + 1) }
                            .accessibilityLabel(t("Раньше"))
                        Text(recap.title + (back == 0 ? " · " + t("пока идёт") : "")).textStyle(.title)
                        Spacer()
                        if back > 0 {
                            RoundGlyphButton(icon: .back, color: Palette.lilac) { model.setRecapBack(back - 1) }
                                .scaleEffect(x: -1, y: 1)
                                .accessibilityLabel(t("Позже"))
                        }
                    }
                    .padding(.horizontal, 16)
                    ZStack {
                        LinearGradient(colors: [Palette.void, Palette.night], startPoint: .top, endPoint: .bottom)
                        if let poster {
                            Image(uiImage: poster).resizable().scaledToFit()
                        } else {
                            ProgressView().tint(Palette.lilac)
                        }
                    }
                    .aspectRatio(RecapImage.width / RecapImage.height, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                    .padding(.horizontal, 16)
                    .accessibilityLabel(recap.tiles.map { "\($0.value) \($0.label)" }.joined(separator: ", "))
                    VStack(alignment: .leading, spacing: 10) {
                        GlowButton(title: t("Опубликовать в ленте и историях"), fullWidth: true) {
                            if let image = RecapImage.attachment(recap, me: model.state.me) {
                                model.publish(tf("Мои итоги: %s ✦", recap.title), media: [image])
                                show(t("Опубликовано"))
                            }
                        }
                        HStack(spacing: 10) {
                            GhostButton(title: t("Отправить в чат")) { choosingChat = true }
                            GhostButton(title: t("В галерею")) { saveToPhotos(recap) }
                        }
                        if recap.empty {
                            Text(t("Пока считать нечего: отмечайте чек-ины в ленте — фильмы, книги, пробежки, места."))
                                .textStyle(.bodySmall, Palette.mist)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .padding(.top, 12)
                .padding(.bottom, tabBarSpace)
            }
            if let notice {
                Text(notice)
                    .textStyle(.labelLarge, Palette.lilac)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .glass(Capsule())
                    .padding(.bottom, tabBarSpace)
                    .transition(.opacity)
            }
        }
        .task(id: "\(recap.key)|\(recap.tiles.count)|\(recap.checkIns)|\(model.state.me.name)") {
            let me = model.state.me
            poster = await Task.detached(priority: .userInitiated) { RecapImage.render(recap, me: me) }.value
        }
        .glassActions(
            $choosingChat, title: t("Отправить в чат"), cancel: true,
            actions: model.state.chatList.map { chat in
                GlassAction(icon: .chats, title: chat.peer.name) {
                    if let image = RecapImage.attachment(recap, me: model.state.me) {
                        model.send(chat.id, tf("Мои итоги: %s", recap.title), attachments: [image])
                        show(tf("Отправлено: %s", chat.peer.name))
                    }
                }
            }
        )
        .sheet(item: $sharing) { item in
            RecapShareSheet(image: item.image)
        }
    }

    private func setPeriod(_ period: RecapPeriod) {
        model.setRecapPeriod(period)
    }

    /// В «Фото»; если приложению нельзя — через «Поделиться».
    private func saveToPhotos(_ recap: Recap) {
        let me = model.state.me
        Task { @MainActor in
            if await RecapImage.saveToPhotos(recap, me: me) {
                show(t("Сохранено в «Фото»"))
            } else {
                sharing = SharedImage(image: RecapImage.render(recap, me: me))
            }
        }
    }

    private func show(_ text: String) {
        withAnimation { notice = text }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            withAnimation { if notice == text { notice = nil } }
        }
    }
}

private struct SharedImage: Identifiable {
    let id = UUID()
    let image: UIImage
}

/// «Поделиться» картинкой итогов. Без разрешения на «Фото» пункт
/// «Сохранить изображение» скрыт: iOS закрыла бы приложение.
private struct RecapShareSheet: UIViewControllerRepresentable {
    let image: UIImage

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: [image], applicationActivities: nil)
        if !RecapImage.canSaveToPhotos { controller.excludedActivityTypes = [.saveToCameraRoll] }
        return controller
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// Карточка «Итоги» в профиле: главные цифры месяца и кнопки «Месяц» /
/// «Год» — открывают картинку итогов.
struct RecapCard: View {
    let model: AppModel

    var body: some View {
        let c = Calendar.current.dateComponents([.year, .month], from: Date())
        let recap = model.recap(.month, year: c.year ?? 2026, month: c.month ?? 1)
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                Text(t("Итоги").uppercased()).textStyle(.label, Palette.mist)
                Text(recap.title).textStyle(.title)
                if recap.empty {
                    Text(t("Отмечайте чек-ины — в конце месяца здесь соберутся фильмы, километры и книги."))
                        .textStyle(.bodySmall, Palette.mist)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    HStack(spacing: 8) {
                        ForEach(recap.tiles.prefix(3), id: \.self) { tile in
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(tile.emoji) \(tile.value)").textStyle(.title).lineLimit(1)
                                Text(tile.label).textStyle(.label, Palette.mist).lineLimit(2)
                            }
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .glass(radius: 16)
                        }
                    }
                }
                HStack(spacing: 10) {
                    GlowButton(title: t("Итоги месяца")) { model.openRecap(.month) }
                    GhostButton(title: t("Итоги года")) { model.openRecap(.year) }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .cardPadding()
    }
}
