import AVFoundation
import SwiftUI
import UIKit

/// Ведущий эфира: я или друг из демо-данных.
private func liveHost(_ state: UiState, _ live: LiveStream) -> User {
    if live.hostId == state.me.id { return state.me }
    return Demo.person(live.hostId) ?? User(id: live.hostId, name: t("Кто-то"), handle: "unknown")
}

/// Метка «В ЭФИРЕ».
struct LiveBadge: View {
    var body: some View {
        Text(t("В ЭФИРЕ"))
            .textStyle(.label)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(LinearGradient(colors: [Palette.pearlPink, Palette.orchid], startPoint: .leading, endPoint: .trailing))
            )
    }
}

/// Кружок «Эфир» в ряду историй: начать свой или вернуться в идущий.
/// То же — LiveStartCircle в LiveScreen.kt.
struct LiveStartCircle: View {
    let model: AppModel

    var body: some View {
        let mine = model.state.myLive
        Button {
            if mine != nil { model.returnToMyLive() } else { model.openLiveStart() }
        } label: {
            VStack(spacing: 4) {
                Glyph(kind: mine != nil ? .video : .plus, color: Palette.lilac, size: 24)
                    .frame(width: 54, height: 54)
                    .glass(Circle(), strong: mine != nil)
                    .padding(4)
                Text(mine != nil ? t("Мой эфир") : t("Эфир"))
                    .textStyle(.labelLarge)
                    .lineLimit(1)
            }
            .frame(width: 72)
            .padding(.vertical, 4)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(mine != nil ? t("Мой эфир") : t("Эфир"))
    }
}

/// Кружок эфира друга с меткой «В ЭФИРЕ». То же — LiveCircle в LiveScreen.kt.
struct LiveCircle: View {
    let model: AppModel
    let live: LiveStream

    var body: some View {
        let person = liveHost(model.state, live)
        Button { model.watchLive(live.id) } label: {
            VStack(spacing: 4) {
                ZStack(alignment: .bottom) {
                    Avatar(user: person, size: 54)
                        .padding(4)
                        .overlay(
                            Circle().strokeBorder(
                                AngularGradient(colors: [Palette.pearlPink, Palette.orchid, Palette.pearlPink], center: .center),
                                lineWidth: 2
                            )
                        )
                    LiveBadge()
                }
                Text(person.firstName)
                    .textStyle(.labelLarge)
                    .lineLimit(1)
            }
            .frame(width: 72)
            .padding(.vertical, 4)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(tf("%s в эфире: %s", person.name, live.title))
        .accessibilityAddTraits(.isButton)
    }
}

/// Запуск эфира: название и кто видит.
struct LiveStartDialog: View {
    let model: AppModel
    @State private var title = ""
    @State private var friendsOnly = false
    @State private var error: String?

    var body: some View {
        CenterDialog(onDismiss: { model.closeLiveStart() }) {
            Text(t("Прямой эфир"))
                .textStyle(.title)
                .accessibilityAddTraits(.isHeader)
            GlassField(placeholder: t("О чём эфир"), text: $title, limit: Lives.maxTitle, singleLine: true)
                .glass(radius: 16)
                .submitLabel(.go)
                .onSubmit(start)
            Toggle(isOn: $friendsOnly) {
                Text(t("Только для друзей")).textStyle(.body)
            }
            .tint(Palette.orchid)
            Text(t("Друзья получат уведомление. Эфир не сохраняется — он есть, пока идёт."))
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
            if let error {
                Text(error).textStyle(.bodySmall, Palette.error)
            }
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена")) { model.closeLiveStart() }
                Spacer()
                GlowButton(title: t("Начать"), action: start)
            }
        }
        .onChange(of: title) { _, _ in error = nil }
    }

    private func start() {
        error = model.startLive(title, friendsOnly: friendsOnly)
    }
}

/// Эфир во весь экран — показывается, пока открыт эфир (свой или друга).
struct LiveCover: View {
    let model: AppModel

    var body: some View {
        ZStack {
            Palette.void.ignoresSafeArea()
            if let live = model.state.watchingLive {
                LiveScreen(model: model, live: live)
            }
        }
    }
}

/// Эфир во весь экран. Ведущий видит свою камеру; зритель — пока
/// демо-картинку (видео пойдёт через LiveKit). Сверху — кто, «В ЭФИРЕ»,
/// зрители и время; снизу — чат эфира, поле и сердечко.
struct LiveScreen: View {
    let model: AppModel
    let live: LiveStream
    @State private var text = ""

    var body: some View {
        let state = model.state
        let mine = state.myLive?.id == live.id
        let person = liveHost(state, live)
        ZStack {
            Palette.void.ignoresSafeArea()
            if mine {
                CameraPreview().ignoresSafeArea()
            } else {
                Nebula(seed: live.seed).ignoresSafeArea()
                VStack(spacing: 0) {
                    Avatar(user: person, size: 96)
                    if !model.liveTransport.connected {
                        Text(t("Демо: видео эфира появится, когда подключим сервер"))
                            .textStyle(.bodySmall, Palette.mist)
                            .multilineTextAlignment(.center)
                            .padding(24)
                    }
                }
            }
            // Затемнение сверху и снизу — чтобы текст читался на любом видео
            VStack(spacing: 0) {
                LinearGradient(colors: [Color.black.opacity(0.55), .clear], startPoint: .top, endPoint: .bottom)
                    .frame(height: 160)
                Spacer()
                LinearGradient(colors: [.clear, Color.black.opacity(0.65)], startPoint: .top, endPoint: .bottom)
                    .frame(height: 320)
            }
            .ignoresSafeArea()
            .allowsHitTesting(false)

            VStack(spacing: 0) {
                topBar(person: person, mine: mine)
                Spacer()
                LiveChat(live: live)
                    .frame(height: 240)
                    .padding(.horizontal, 12)
                inputRow(mine: mine)
            }
        }
        .overlay {
            if state.confirmEndLive && mine {
                CenterDialog(onDismiss: { model.cancelEndLive() }) {
                    Text(t("Закончить эфир?")).textStyle(.title)
                    Text(tf("Зрителей сейчас: %s. Эфир не сохранится.", live.viewers))
                        .textStyle(.bodySmall, Palette.mist)
                        .fixedSize(horizontal: false, vertical: true)
                    HStack(spacing: 10) {
                        GhostButton(title: t("Продолжить")) { model.cancelEndLive() }
                        Spacer()
                        GlowButton(title: t("Закончить")) { model.endLive() }
                    }
                }
                .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.2), value: state.confirmEndLive)
        // Жалоба на ведущего — поверх эфира (экран эфира закрывает остальное)
        .reportSheet(model: model)
    }

    private func topBar(person: User, mine: Bool) -> some View {
        HStack(spacing: 10) {
            Avatar(user: person, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(person.name).textStyle(.title).lineLimit(1)
                Text(live.title).textStyle(.bodySmall, Palette.mist).lineLimit(1)
            }
            Spacer(minLength: 4)
            VStack(alignment: .trailing, spacing: 4) {
                LiveBadge()
                // Время эфира идёт каждую секунду
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    Text("👁 \(live.viewers) · \(Lives.clock(live.durationMs(context.date)))")
                        .textStyle(.labelLarge)
                        .monospacedDigit()
                }
                .accessibilityLabel(tf("Зрителей: %s", live.viewers))
            }
            if !mine {
                Button { model.reportPerson(person) } label: {
                    Glyph(kind: .flag, color: Palette.pearlPink, size: 18)
                        .frame(width: 40, height: 40)
                        .glass(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(LineIcon.flag.label)
            }
            Button {
                if mine { _ = model.back() } else { model.leaveLive() }
            } label: {
                Glyph(kind: .close, color: Palette.ink, size: 18)
                    .frame(width: 40, height: 40)
                    .glass(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(mine ? t("Закончить эфир") : LineIcon.close.label)
        }
        .padding(12)
    }

    private func inputRow(mine: Bool) -> some View {
        let empty = text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return HStack(spacing: 8) {
            GlassField(placeholder: mine ? t("Написать зрителям") : t("Комментарий"), text: $text, limit: Lives.maxComment, singleLine: true)
                .glass(radius: 22)
                .submitLabel(.send)
                .onSubmit(send)
            Button(action: send) {
                Glyph(kind: .send, color: empty ? Palette.mist : Palette.ink)
                    .frame(width: 46, height: 46)
                    .glass(Circle())
            }
            .buttonStyle(.plain)
            .disabled(empty)
            .accessibilityLabel(LineIcon.send.label)
            if !mine {
                Button { model.sendHeart() } label: {
                    Glyph(kind: .heartFilled, color: Palette.ink)
                        .frame(width: 46, height: 46)
                        .background(
                            Circle().fill(LinearGradient(colors: [Palette.pearlPink, Palette.orchid], startPoint: .topLeading, endPoint: .bottomTrailing))
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(t("Сердечко"))
            }
        }
        .padding(12)
    }

    private func send() {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        model.sendLiveComment(text)
        text = ""
    }
}

/// Чат эфира: новые снизу.
private struct LiveChat: View {
    let live: LiveStream

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(showsIndicators: false) {
                LazyVStack(alignment: .leading, spacing: 6) {
                    ForEach(live.comments) { c in
                        (Text(c.name.split(separator: " ").first.map(String.init) ?? c.name)
                            .font(TextStyle.labelLarge.font)
                            .foregroundColor(Palette.lilac)
                         + Text("  " + c.text)
                            .font(TextStyle.bodySmall.font)
                            .foregroundColor(Palette.ink))
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .id(c.id)
                    }
                }
            }
            .defaultScrollAnchor(.bottom)
            .onChange(of: live.comments.count) { _, _ in
                if let last = live.comments.last {
                    withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo(last.id, anchor: .bottom) }
                }
                // VoiceOver читает новый комментарий вслух
                if let last = live.comments.last, UIAccessibility.isVoiceOverRunning {
                    UIAccessibility.post(notification: .announcement, argument: last.name + ": " + last.text)
                }
            }
        }
    }
}

/// Итог своего эфира.
struct LiveSummaryDialog: View {
    let model: AppModel
    let live: LiveStream

    var body: some View {
        CenterDialog(onDismiss: { model.dismissLiveSummary() }) {
            Text(t("Эфир закончен"))
                .textStyle(.title)
                .accessibilityAddTraits(.isHeader)
            Text(Lives.summary(live, live.endedAt ?? Date()))
                .textStyle(.body)
                .fixedSize(horizontal: false, vertical: true)
            HStack {
                Spacer()
                GlowButton(title: t("Готово")) { model.dismissLiveSummary() }
            }
        }
    }
}
