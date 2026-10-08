import SwiftUI

/// Цвет в виде 0xRRGGBB: удобно переносить из Android-палитры и смешивать.
struct RGB: Equatable {
    let r: Double
    let g: Double
    let b: Double

    init(_ hex: UInt32) {
        r = Double((hex >> 16) & 0xFF) / 255
        g = Double((hex >> 8) & 0xFF) / 255
        b = Double(hex & 0xFF) / 255
    }

    init(r: Double, g: Double, b: Double) {
        self.r = r
        self.g = g
        self.b = b
    }

    var color: Color { Color(.sRGB, red: r, green: g, blue: b, opacity: 1) }

    func opacity(_ a: Double) -> Color { Color(.sRGB, red: r, green: g, blue: b, opacity: a) }

    /// Смесь двух цветов: t = 0 — этот, t = 1 — other.
    func lerp(_ other: RGB, _ t: Double) -> RGB {
        let k = min(1, max(0, t))
        return RGB(r: r + (other.r - r) * k, g: g + (other.g - g) * k, b: b + (other.b - b) * k)
    }
}

/// Палитра Memory — фиолетово-синий космос, та же, что на Android.
///
/// Семь цветов из референса (от светлой сирени до небесно-синего) плюс
/// два служебных: почти чёрный фон и светлый текст с лиловым оттенком —
/// чистый белый на таком фоне режет глаз.
enum Palette {
    static let lilacRGB = RGB(0xBC92CD)
    static let orchidRGB = RGB(0x844CAD)
    static let violetRGB = RGB(0x5F3F96)
    static let plumRGB = RGB(0x3C2C59)
    static let nightRGB = RGB(0x0F153A)
    static let oceanRGB = RGB(0x195699)
    static let skyRGB = RGB(0x307EC0)
    static let voidRGB = RGB(0x07081A)
    static let inkRGB = RGB(0xEDE6F5)
    static let mistRGB = RGB(0x9C93B8)

    static let lilac = lilacRGB.color     // акценты, выбранная вкладка
    static let orchid = orchidRGB.color   // градиенты, кнопки
    static let violet = violetRGB.color   // свои сообщения, заливки
    static let plum = plumRGB.color       // стеклянные карточки
    static let night = nightRGB.color     // основной фон
    static let ocean = oceanRGB.color     // вторичный акцент
    static let sky = skyRGB.color         // ссылки, индикаторы
    static let void = voidRGB.color       // глубина фона, заставка
    static let ink = inkRGB.color         // основной текст
    static let mist = mistRGB.color       // второстепенный текст
    static let line = lilacRGB.opacity(0.2) // тонкие линии и рамки

    /// Ошибки в формах — мягкий розово-красный, читается на тёмном.
    static let error = RGB(0xFFB4AB).color

    /// Розовый перламутр — «нравится» в Reels и тон кристалла.
    static let pearlPink = RGB(0xFF9EDE).color
}
