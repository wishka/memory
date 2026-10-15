import Foundation

/// Качество фото и видео при отправке — «Настройки → Данные и сеть».
/// standard — фото до 2048 px, видео 720p; saver — 1280 px и 480p;
/// originalOnWifi — по Wi‑Fi без сжатия, в мобильной сети как standard.
/// id — как у Android (MediaQuality.id).
enum MediaQuality: String, CaseIterable, Identifiable {
    case standard = "standard"
    case saver = "saver"
    case originalOnWifi = "original"

    var id: String { rawValue }

    static func of(_ id: String?) -> MediaQuality { id.flatMap { MediaQuality(rawValue: $0) } ?? .standard }
}

/// Во что сжать: 0 — не трогать.
struct PhotoPlan: Equatable {
    let maxSide: Int
    let jpegQuality: Int
}

struct VideoPlan: Equatable {
    let height: Int
    let bitrate: Int
}

/// Ширина и высота в точках.
struct MediaSize: Equatable {
    let width: Int
    let height: Int

    init(_ width: Int, _ height: Int) {
        self.width = width
        self.height = height
    }
}

/// Правила сжатия — одинаковые на Android и iPhone (MediaPlan.kt).
/// Только расчёт: сами кодеки — MediaCompressor на каждой платформе.
enum MediaPlan {
    static func photo(_ quality: MediaQuality, wifi: Bool) -> PhotoPlan {
        if quality == .originalOnWifi && wifi { return PhotoPlan(maxSide: 0, jpegQuality: 95) }
        if quality == .saver { return PhotoPlan(maxSide: 1280, jpegQuality: 75) }
        return PhotoPlan(maxSide: 2048, jpegQuality: 82)
    }

    static func video(_ quality: MediaQuality, wifi: Bool) -> VideoPlan {
        if quality == .originalOnWifi && wifi { return VideoPlan(height: 0, bitrate: 0) }
        if quality == .saver { return VideoPlan(height: 480, bitrate: 1_200_000) }
        return VideoPlan(height: 720, bitrate: 2_500_000)
    }

    /// Как Math.round на Android: половина — вверх.
    private static func roundHalfUp(_ v: Double) -> Int { Int((v + 0.5).rounded(.down)) }

    /// Размер после сжатия: длинная сторона не больше maxSide, пропорции те же, не увеличиваем.
    static func fit(_ width: Int, _ height: Int, _ maxSide: Int) -> MediaSize {
        if maxSide <= 0 || width <= 0 || height <= 0 { return MediaSize(width, height) }
        let long = max(width, height)
        if long <= maxSide { return MediaSize(width, height) }
        let k: Double = Double(maxSide) / Double(long)
        let w = max(1, roundHalfUp(Double(width) * k))
        let h = max(1, roundHalfUp(Double(height) * k))
        return MediaSize(w, h)
    }

    /// Видео: короткая сторона — target (720), обе стороны чётные (так
    /// требуют кодеки). Уже меньше — не трогаем.
    static func fitVideo(_ width: Int, _ height: Int, _ target: Int) -> MediaSize {
        if target <= 0 || width <= 0 || height <= 0 { return MediaSize(width, height) }
        let short = min(width, height)
        if short <= target { return MediaSize(width, height) }
        let k: Double = Double(target) / Double(short)
        let w = even(Double(width) * k)
        let h = even(Double(height) * k)
        return MediaSize(w, h)
    }

    private static func even(_ v: Double) -> Int { max(2, roundHalfUp(v / 2) * 2) }

    /// Во сколько раз уменьшить при чтении (степень двойки) — чтобы не держать в памяти 50 Мп.
    static func sampleSize(_ width: Int, _ height: Int, _ maxSide: Int) -> Int {
        if maxSide <= 0 { return 1 }
        var s = 1
        while max(width, height) / (s * 2) >= maxSide { s *= 2 }
        return s
    }

    /// Стоит ли пересжимать видео: выше цели или битрейт заметно больше.
    static func needsVideoWork(_ width: Int, _ height: Int, bitrate: Int, _ plan: VideoPlan) -> Bool {
        if plan.height <= 0 { return false }
        if min(width, height) > plan.height { return true }
        return bitrate > 0 && bitrate > plan.bitrate * 3 / 2
    }
}
