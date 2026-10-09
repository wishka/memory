import Foundation

/// Правки ролика в редакторе PRISM. Видео не меняется, пока человек
/// правит: правки — это описание, по нему строится предпросмотр и
/// итоговый файл (VideoExporter). Те же правила — в VideoEdit.kt на
/// Android, чтобы ролик выглядел одинаково на обоих телефонах.
struct VideoEdit: Equatable {
    /// Исходное видео (снятое камерой или из галереи) — копия в папке приложения.
    let sourceURL: URL
    let sourceDurationMs: Int64
    /// Размер кадра с учётом поворота, записанного камерой.
    let sourceWidth: Int
    let sourceHeight: Int
    var trimStartMs: Int64 = 0
    var trimEndMs: Int64
    var speed: Double = 1
    /// Поворот по часовой стрелке: 0, 90, 180, 270.
    var rotation: Int = 0
    var mirror = false
    var aspect: Aspect = .portrait
    var filter: VideoFilter = .none
    /// Коррекция: −1…1, 0 — без изменений.
    var brightness: Double = 0
    var contrast: Double = 0
    var saturation: Double = 0
    var warmth: Double = 0
    var overlays: [VideoOverlay] = []
    /// Музыка из «Моей музыки»; nil — без музыки.
    var musicTrackId: String? = nil
    var musicStartMs: Int64 = 0
    var musicVolume: Double = 0.8
    /// Громкость звука самого видео: 0 — без звука.
    var originalVolume: Double = 1
    /// Кадр-обложка — время в исходном видео.
    var coverMs: Int64 = 0

    static let maxSide = 1920
    /// Самый длинный PRISM.
    static let maxDurationMs: Int64 = 60_000
    static let minDurationMs: Int64 = 1_000
    static let speeds: [Double] = [0.5, 0.75, 1, 1.5, 2, 3]

    init(sourceURL: URL, sourceDurationMs: Int64, sourceWidth: Int, sourceHeight: Int) {
        self.sourceURL = sourceURL
        self.sourceDurationMs = sourceDurationMs
        self.sourceWidth = sourceWidth
        self.sourceHeight = sourceHeight
        self.trimEndMs = min(sourceDurationMs, VideoEdit.maxDurationMs)
    }

    /// Сколько длится итоговый ролик.
    var outputDurationMs: Int64 { max(0, Int64(Double(trimEndMs - trimStartMs) / speed)) }

    private var turnedWidth: Int { rotation % 180 == 0 ? sourceWidth : sourceHeight }
    private var turnedHeight: Int { rotation % 180 == 0 ? sourceHeight : sourceWidth }

    /// Размер итогового кадра. Для заданных пропорций — 1080 по ширине;
    /// «Оригинал» — как снято, длинная сторона не больше 1920. Стороны
    /// чётные: так требует кодек H.264.
    func outputSize() -> (width: Int, height: Int) {
        var w: Int
        var h: Int
        if aspect == .original {
            let k = min(1, Double(VideoEdit.maxSide) / Double(max(1, max(turnedWidth, turnedHeight))))
            w = Int((Double(turnedWidth) * k).rounded())
            h = Int((Double(turnedHeight) * k).rounded())
        } else {
            w = 1080
            h = Int((1080.0 * Double(aspect.h) / Double(aspect.w)).rounded())
        }
        w = max(2, w - w % 2)
        h = max(2, h - h % 2)
        return (w, h)
    }

    /// Пропорции кадра (ширина / высота) — для предпросмотра.
    var outputAspect: Double {
        let s = outputSize()
        return Double(s.width) / Double(s.height)
    }

    /// Цвет: фильтр и коррекция одной матрицей 3×4 (см. ColorMatrix).
    var colorMatrix: [Double] {
        ColorMatrix.then(ColorMatrix.adjust(brightness, contrast, saturation, warmth), filter.matrix)
    }

    var colorIsIdentity: Bool {
        filter == .none && brightness == 0 && contrast == 0 && saturation == 0 && warmth == 0
    }

    /// Время обложки в итоговом ролике.
    var coverOutputMs: Int64 {
        let t = min(max(coverMs, trimStartMs), trimEndMs)
        return Int64(Double(t - trimStartMs) / speed)
    }
}

/// Пропорции кадра.
enum Aspect: String, CaseIterable {
    case portrait = "9:16"
    case tall = "4:5"
    case square = "1:1"
    case original = "Оригинал"

    var w: Int {
        switch self {
        case .portrait: return 9
        case .tall: return 4
        case .square: return 1
        case .original: return 0
        }
    }

    var h: Int {
        switch self {
        case .portrait: return 16
        case .tall: return 5
        case .square: return 1
        case .original: return 0
        }
    }

    var title: String { t(rawValue) }
}

/// Текст или стикер поверх видео. x, y — центр, доли кадра; size —
/// высота строки, доля высоты кадра.
struct VideoOverlay: Identifiable, Equatable {
    var id: Int64
    var text: String
    var x: Double = 0.5
    var y: Double = 0.5
    var size: Double = 0.05
    /// Цвет 0xRRGGBB.
    var color: UInt32 = 0xFFFFFF
    var style: OverlayStyle = .plain
    var font: OverlayFont = .inter
    var sticker = false
}

enum OverlayStyle: String, CaseIterable {
    case plain = "Обычный"
    case box = "С фоном"
    case glow = "Свечение"

    var title: String { t(rawValue) }
}

enum OverlayFont: String, CaseIterable {
    case inter = "Inter"
    case unbounded = "Unbounded"
}

/// Цвета текста — из палитры приложения и пара контрастных.
let overlayColors: [UInt32] = [0xFFFFFF, 0xF3EEFF, 0xBC92CD, 0xFF9EDE, 0x8CC4FF, 0x307EC0, 0xFFE08A, 0x0B0820]

/// Стикеры: эмодзи и свои кристальные знаки.
let stickers = [
    "✦", "✧", "◇", "☾", "★", "✨", "💜", "🔮", "🌙", "⭐", "💫", "🌌",
    "🔥", "😂", "😍", "🥹", "😎", "🤍", "🎉", "🎵", "🎬", "📍", "👀", "🫶",
    "🌈", "⚡", "☕", "🌸", "🍀", "🐾", "🚀", "💎",
]

/// Фильтры — готовые цветовые настроения.
enum VideoFilter: String, CaseIterable {
    case none = "Нет"
    case crystal = "Кристалл"
    case lilac = "Сирень"
    case night = "Ночь"
    case noir = "Ч/Б"
    case sepia = "Сепия"
    case warm = "Тёплый"
    case cold = "Холодный"
    case vintage = "Винтаж"
    case neon = "Неон"
    case film = "Плёнка"
    case drama = "Драма"
    case pastel = "Пастель"

    var title: String { t(rawValue) }

    /// Матрица 3×4 фильтра.
    var matrix: [Double] {
        typealias M = ColorMatrix
        switch self {
        case .none: return M.identity
        case .crystal: return M.chain(M.offset(0.02, 0.02, 0.02), M.scale(0.96, 1, 1.1), M.saturation(1.1))
        case .lilac: return M.chain(M.offset(0.03, 0, 0.05), M.scale(1.06, 0.92, 1.08))
        case .night: return M.chain(M.scale(0.82, 0.88, 1.05), M.saturation(0.85), M.contrast(1.1))
        case .noir: return M.chain(M.saturation(0), M.contrast(1.15))
        case .sepia: return M.sepia
        case .warm: return M.scale(1.1, 1.02, 0.86)
        case .cold: return M.scale(0.88, 1, 1.14)
        case .vintage: return M.chain(M.offset(0.06, 0.06, 0.06), M.contrast(0.85), M.mix(M.sepia, 0.45))
        case .neon: return M.chain(M.saturation(1.7), M.contrast(1.1))
        case .film: return M.chain(M.offset(0.03, 0.04, 0.02), M.saturation(0.8), M.contrast(0.92))
        case .drama: return M.chain(M.saturation(0.85), M.contrast(1.35))
        case .pastel: return M.chain(M.offset(0.08, 0.08, 0.08), M.contrast(0.8), M.saturation(0.9))
        }
    }
}

/// Цветовые матрицы 3×4: строки — новые R, G, B; в каждой три множителя
/// и сдвиг. then(a, b) — сначала b, потом a. Цвет — 0…1. Те же формулы,
/// что ColorMatrix на Android.
enum ColorMatrix {
    static let identity: [Double] = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0]

    static let sepia: [Double] = [
        0.393, 0.769, 0.189, 0,
        0.349, 0.686, 0.168, 0,
        0.272, 0.534, 0.131, 0,
    ]

    static func scale(_ r: Double, _ g: Double, _ b: Double) -> [Double] { [r, 0, 0, 0, 0, g, 0, 0, 0, 0, b, 0] }

    static func offset(_ r: Double, _ g: Double, _ b: Double) -> [Double] { [1, 0, 0, r, 0, 1, 0, g, 0, 0, 1, b] }

    /// Насыщенность: 0 — серое, 1 — как есть, больше 1 — ярче.
    static func saturation(_ s: Double) -> [Double] {
        let lr = 0.2126 * (1 - s)
        let lg = 0.7152 * (1 - s)
        let lb = 0.0722 * (1 - s)
        return [lr + s, lg, lb, 0, lr, lg + s, lb, 0, lr, lg, lb + s, 0]
    }

    /// Контраст вокруг середины серого.
    static func contrast(_ k: Double) -> [Double] {
        let o = 0.5 * (1 - k)
        return [k, 0, 0, o, 0, k, 0, o, 0, 0, k, o]
    }

    /// Смесь с матрицей m: amount — доля m.
    static func mix(_ m: [Double], _ amount: Double) -> [Double] {
        (0..<12).map { identity[$0] * (1 - amount) + m[$0] * amount }
    }

    /// Коррекция из ползунков −1…1.
    static func adjust(_ brightness: Double, _ contrast: Double, _ saturation: Double, _ warmth: Double) -> [Double] {
        chain(
            scale(1 + 0.12 * warmth, 1, 1 - 0.12 * warmth),
            ColorMatrix.saturation(1 + saturation),
            ColorMatrix.contrast(1 + 0.6 * contrast),
            offset(0.25 * brightness, 0.25 * brightness, 0.25 * brightness)
        )
    }

    /// chain(a, b, c) = then(a, then(b, c)): последняя применяется первой.
    static func chain(_ ms: [Double]...) -> [Double] {
        ms.dropLast().reversed().reduce(ms.last ?? identity) { acc, m in then(m, acc) }
    }

    /// Сначала b, потом a.
    static func then(_ a: [Double], _ b: [Double]) -> [Double] {
        var out = [Double](repeating: 0, count: 12)
        for r in 0..<3 {
            for c in 0..<3 {
                out[r * 4 + c] = a[r * 4] * b[c] + a[r * 4 + 1] * b[4 + c] + a[r * 4 + 2] * b[8 + c]
            }
            out[r * 4 + 3] = a[r * 4] * b[3] + a[r * 4 + 1] * b[7] + a[r * 4 + 2] * b[11] + a[r * 4 + 3]
        }
        return out
    }

    /// Применить к цвету — для проверок и образцов фильтров.
    static func apply(_ m: [Double], _ r: Double, _ g: Double, _ b: Double) -> (Double, Double, Double) {
        func clamp(_ v: Double) -> Double { min(1, max(0, v)) }
        return (
            clamp(m[0] * r + m[1] * g + m[2] * b + m[3]),
            clamp(m[4] * r + m[5] * g + m[6] * b + m[7]),
            clamp(m[8] * r + m[9] * g + m[10] * b + m[11])
        )
    }
}
