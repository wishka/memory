import SwiftUI

/// Что карточкам знать о сети: есть ли она и сколько ушло у каждой отправки.
/// Даётся один раз в MainScreen через окружение — как LinkActions.
/// То же — SyncInfo / LocalSync в NetViews.kt.
struct SyncInfo {
    var status: NetStatus = .online
    var progress: (String) -> Float? = { _ in nil }
}

private struct SyncInfoKey: EnvironmentKey {
    static let defaultValue = SyncInfo()
}

extension EnvironmentValues {
    /// Сеть и прогресс отправок — для строки «Отправляется…» под своей записью.
    var syncInfo: SyncInfo {
        get { self[SyncInfoKey.self] }
        set { self[SyncInfoKey.self] = newValue }
    }
}

/// Полоска сверху: «Нет сети» / «Сервер не отвечает» и сколько ждёт отправки.
struct NetBanner: View {
    let status: NetStatus
    let waiting: Int

    private var text: String {
        let base: String = status == .offline ? t("Нет сети — показываем сохранённое") : t("Сервер не отвечает — попробуем ещё")
        if waiting <= 0 { return base }
        let tail: String = tf("ждут отправки: %s", waiting)
        return base + " · " + tail
    }

    var body: some View {
        if status != .online {
            let dot: Color = status == .offline ? Palette.pearlPink : Palette.sky
            HStack(spacing: 8) {
                Circle().fill(dot).frame(width: 8, height: 8)
                Text(text).textStyle(.labelLarge, Palette.ink)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 6)
            .background(Capsule().fill(Palette.void.opacity(0.85)))
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.updatesFrequently)
        }
    }
}

/// Тонкая полоска прогресса — как LinearProgressIndicator на Android.
struct ThinProgressBar: View {
    let value: Float

    var body: some View {
        let clamped: CGFloat = CGFloat(min(max(value, 0), 1))
        GeometryReader { geo in
            let filled: CGFloat = geo.size.width * clamped
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.line)
                Capsule().fill(Palette.lilac).frame(width: filled)
            }
        }
        .frame(height: 3)
        .accessibilityHidden(true)
    }
}

/// Под своей записью, пока она не на сервере: «Отправляется · 45 %» или «Ждёт сети».
struct SyncLine: View {
    let owner: String
    @Environment(\.syncInfo) private var sync

    var body: some View {
        if !owner.isEmpty {
            let progress: Float? = sync.progress(owner)
            let offline: Bool = sync.status != .online
            let moving: Float? = progress.flatMap { $0 < 1 ? $0 : nil }
            VStack(alignment: .leading, spacing: 4) {
                Text(title(offline: offline, moving: moving)).textStyle(.label, Palette.mist)
                if let moving {
                    ThinProgressBar(value: moving)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func title(offline: Bool, moving: Float?) -> String {
        if offline { return t("Ждёт сети — отправится сама") }
        if let moving {
            let percent: Int = Int(moving * 100)
            return tf("Отправляется · %s", "\(percent)%")
        }
        return t("Отправляется…")
    }
}

/// Низ ленты: подгружается старое, «это всё» или «нет сети».
struct FeedFooter: View {
    let paging: FeedPaging
    let onRetry: () -> Void

    private var errorText: String {
        let base: String = paging.error == .offline ? t("Нет сети — дальше загрузим, когда появится") : t("Не загрузилось")
        guard paging.fromCache && paging.cachedAt > 0 else { return base }
        let minutes: Int = minutesSince(Json.date(paging.cachedAt), 0)
        let saved: String = tf("сохранено %s", agoTitle(minutes))
        return base + " · " + saved
    }

    var body: some View {
        Group {
            if paging.loading {
                ProgressView().tint(Palette.lilac)
            } else if paging.error != nil {
                VStack(spacing: 6) {
                    Text(errorText)
                        .textStyle(.bodySmall, Palette.mist)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                    Button(action: onRetry) {
                        Text(t("Повторить"))
                            .textStyle(.labelLarge, Palette.lilac)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            } else if paging.end {
                Text(t("Вы всё посмотрели ✦")).textStyle(.bodySmall, Palette.mist)
            } else {
                Color.clear.frame(height: 24)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(16)
    }
}
