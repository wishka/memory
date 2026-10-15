import SwiftUI
import UIKit

/// Красная «положить трубку» и зелёная «принять» — одинаковые в любой теме.
private let hangUpRed = RGB(0xE5536F)
private let acceptGreen = RGB(0x3FCF8E)
private let callNight = RGB(0x241A4A)

/// Что написать под именем: «Вызов…», «Входящий видеозвонок», «3:12».
private func callStatus(_ call: ActiveCall, _ now: Date) -> String {
    switch call.phase {
    case .calling: return t("Вызов…")
    case .incoming: return call.video ? t("Входящий видеозвонок") : t("Входящий звонок")
    case .active: return Lives.clock(call.durationMs(now))
    }
}

/// Звонок во весь экран — показывается в CallWindow, пока идёт звонок.
/// Жестом не закрывается: только «Завершить» или «Отклонить».
struct CallCover: View {
    let model: AppModel

    var body: some View {
        ZStack {
            LinearGradient(colors: [callNight.color, Palette.dark.void.color], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
            if let call = model.state.call {
                CallScreen(model: model, call: call)
            }
        }
    }
}

/// Звонок во весь экран. Голосовой — большой аватар; видео — собеседник
/// во весь экран и своя камера окошком. Входящий — «Принять» и «Отклонить».
/// То же — CallScreen в CallScreen.kt.
struct CallScreen: View {
    let model: AppModel
    let call: ActiveCall

    var body: some View {
        let active = call.phase == .active
        let videoOn = call.video && call.cameraOn && call.phase != .incoming
        ZStack(alignment: .topTrailing) {
            LinearGradient(colors: [callNight.color, Palette.dark.void.color], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
            if call.video && active {
                // Собеседник: пока сервера нет — демо-картинка вместо его видео
                Nebula(seed: call.peerId * 37 + 11).ignoresSafeArea()
            }
            if let peer = Demo.person(call.peerId) {
                VStack(spacing: 0) {
                    Spacer().frame(height: 32)
                    Avatar(user: peer, size: call.video && active ? 72 : 128)
                    Spacer().frame(height: 16)
                    Text(peer.name)
                        .textStyle(.headline, Palette.onMedia)
                        .multilineTextAlignment(.center)
                    // Время разговора идёт каждую секунду — как у эфира
                    TimelineView(.periodic(from: .now, by: 1)) { context in
                        Text(callStatus(call, context.date))
                            .textStyle(.body, Palette.onMediaRGB.opacity(0.8))
                            .monospacedDigit()
                    }
                    .padding(.top, 4)
                    .accessibilityAddTraits(.updatesFrequently)
                    if !model.callTransport.connected {
                        Text(t("Демо: голос и видео пойдут, когда подключим сервер"))
                            .textStyle(.bodySmall, Palette.onMediaRGB.opacity(0.6))
                            .multilineTextAlignment(.center)
                            .padding(.top, 8)
                    }
                    Spacer()
                    if call.phase == .incoming {
                        HStack {
                            Spacer()
                            BigCallButton(icon: .hangUp, label: t("Отклонить"), color: hangUpRed.color) { model.declineCall() }
                            Spacer()
                            BigCallButton(icon: call.video ? .video : .phone, label: t("Принять"), color: acceptGreen.color) { model.acceptCall() }
                            Spacer()
                        }
                    } else {
                        HStack {
                            Spacer()
                            SmallCallButton(
                                icon: call.muted ? .micOff : .mic,
                                label: call.muted ? t("Включить микрофон") : t("Выключить микрофон"),
                                active: call.muted
                            ) { model.toggleMute() }
                            Spacer()
                            if call.video {
                                SmallCallButton(
                                    icon: call.cameraOn ? .video : .videoOff,
                                    label: call.cameraOn ? t("Выключить камеру") : t("Включить камеру"),
                                    active: !call.cameraOn
                                ) { model.toggleCamera() }
                                Spacer()
                                SmallCallButton(icon: .flip, label: t("Сменить камеру"), active: false) { model.flipCamera() }
                                Spacer()
                            }
                            SmallCallButton(
                                icon: .volume,
                                label: call.speaker ? t("Динамик выключить") : t("Громкая связь"),
                                active: call.speaker
                            ) { model.toggleSpeaker() }
                            Spacer()
                        }
                        Spacer().frame(height: 24)
                        BigCallButton(icon: .hangUp, label: t("Завершить"), color: hangUpRed.color) { model.endCall() }
                    }
                    Spacer().frame(height: 16)
                }
                .frame(maxWidth: .infinity)
                .padding(24)
            }
            // Своя камера — окошком в углу
            if videoOn {
                CameraPreview(front: call.frontCamera)
                    .frame(width: 110, height: 160)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .padding(16)
            }
        }
        // Звонок — всегда на ночном фоне
        .environment(\.colorScheme, .dark)
    }
}

private struct BigCallButton: View {
    let icon: LineIcon
    let label: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Glyph(kind: icon, color: .white, size: 30)
                    .frame(width: 72, height: 72)
                    .background(Circle().fill(color))
                Text(label).textStyle(.labelLarge, Palette.onMedia)
            }
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(.isButton)
    }
}

private struct SmallCallButton: View {
    let icon: LineIcon
    let label: String
    let active: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Glyph(kind: icon, color: active ? callNight.color : .white, size: 24)
                .frame(width: 56, height: 56)
                .background(Circle().fill(active ? Color.white : Color.white.opacity(0.16)))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityAddTraits(active ? .isSelected : [])
    }
}

/// История звонков — во вкладке «Чаты», переключатель «Звонки»: кто, видео
/// или голос, входящий или исходящий, пропущенный. Нажатие — перезвонить.
/// То же — callLogItems в CallScreen.kt.
struct CallLogList: View {
    let model: AppModel
    let onError: (String) -> Void

    var body: some View {
        let log = model.state.callLog
        if log.isEmpty {
            Text(t("Звонков пока не было. Позвонить можно из переписки — значки трубки и камеры вверху."))
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(24)
        }
        ForEach(log) { r in
            if let peer = Demo.person(r.peerId) {
                let bad = r.missed || r.declined
                let arrow: String = r.outgoing ? "↗ " : "↙ "
                let line: String = arrow + Calls.title(r) + " · " + agoTitle(minutesSince(r.at, 0))
                Button {
                    if let error = model.callBack(r) { onError(error) }
                } label: {
                    HStack(spacing: 12) {
                        Avatar(user: peer, size: 44)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(peer.name)
                                .textStyle(.title, bad && !r.outgoing ? Palette.pearlPink : Palette.ink)
                                .lineLimit(1)
                            Text(line)
                                .textStyle(.bodySmall, Palette.mist)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 0)
                        Glyph(kind: r.video ? .video : .phone, color: Palette.lilac, size: 22)
                    }
                    .padding(12)
                    .glass(radius: 18)
                    .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .combine)
                .accessibilityHint(t("Перезвонить"))
                .padding(.horizontal, 16)
                .padding(.vertical, 4)
            }
        }
        if !log.isEmpty {
            Button { model.clearCallLog() } label: {
                Text(t("Очистить историю звонков"))
                    .textStyle(.labelLarge, Palette.mist)
                    .padding(8)
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(20)
        }
    }
}

/// Окно звонка — над всем приложением, даже над открытыми шторками и
/// экранами во весь экран (системные модальные окна иначе закрывают
/// друг друга). Жестом не закрывается. Вызывается только из интерфейса (главный поток).
final class CallWindow {
    static let shared = CallWindow()
    private var window: UIWindow?

    func show(_ model: AppModel) {
        guard window == nil else { return }
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        guard let scene = scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first else { return }
        let host = UIHostingController(rootView: CallCover(model: model).preferredColorScheme(.dark))
        host.view.backgroundColor = .clear
        let window = UIWindow(windowScene: scene)
        window.windowLevel = .alert + 1
        window.rootViewController = host
        window.makeKeyAndVisible()
        self.window = window
    }

    func hide() {
        guard let window else { return }
        window.isHidden = true
        window.rootViewController = nil
        self.window = nil
        // Ввод с клавиатуры — снова в окно приложения
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        scenes.flatMap(\.windows).first { $0.windowLevel == .normal }?.makeKey()
    }
}

private struct CallWindowModifier: ViewModifier {
    let model: AppModel

    func body(content: Content) -> some View {
        content
            .onChange(of: model.state.call != nil, initial: true) { _, active in
                if active { CallWindow.shared.show(model) } else { CallWindow.shared.hide() }
            }
            .onDisappear { CallWindow.shared.hide() }
    }
}

extension View {
    /// Идущий звонок — своим окном поверх всего приложения.
    func callWindow(_ model: AppModel) -> some View {
        modifier(CallWindowModifier(model: model))
    }
}
