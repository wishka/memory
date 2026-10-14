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

/// Набор цветов одной темы — названия те же, что у Palette. То же — PaletteColors в Color.kt.
struct PaletteColors {
    let lilac: RGB
    let orchid: RGB
    let violet: RGB
    let plum: RGB
    let night: RGB
    let ocean: RGB
    let sky: RGB
    let void: RGB
    let ink: RGB
    let mist: RGB
    /// Линии и рамки — lilac с этой прозрачностью.
    let line: RGB
    let lineAlpha: Double
    let pearlPink: RGB
    let error: RGB
}

/// Палитра Memory — фиолетово-синий космос, та же, что на Android.
///
/// Семь цветов из референса (от светлой сирени до небесно-синего) плюс
/// два служебных: почти чёрный фон и светлый текст с лиловым оттенком —
/// чистый белый на таком фоне режет глаз.
///
/// Светлая тема — «рассвет» той же гаммы: заливки (кнопки, свои
/// сообщения, стекло) — пастельные, текст и значки — тёмные. Названия
/// цветов значат одно и то же в обеих темах: ink — текст, mist —
/// второстепенный текст, orchid/ocean — градиент кнопок, lilac — акцент.
///
/// Цвета читают ThemeState.shared при каждом обращении: ThemeState —
/// @Observable, поэтому экраны, которые берут цвета из Palette в body,
/// перерисовываются сами, когда тема меняется.
enum Palette {
    static let dark = PaletteColors(
        lilac: RGB(0xBC92CD),     // 1 — акценты, выбранная вкладка
        orchid: RGB(0x844CAD),    // 2 — градиенты, кнопки
        violet: RGB(0x5F3F96),    // 3 — свои сообщения, заливки
        plum: RGB(0x3C2C59),      // 4 — стеклянные карточки
        night: RGB(0x0F153A),     // 5 — основной фон
        ocean: RGB(0x195699),     // 6 — вторичный акцент
        sky: RGB(0x307EC0),       // 7 — ссылки, индикаторы
        void: RGB(0x07081A),      // глубина фона, заставка
        ink: RGB(0xEDE6F5),       // основной текст
        mist: RGB(0x9C93B8),      // второстепенный текст
        line: RGB(0xBC92CD), lineAlpha: 0.2, // тонкие линии и рамки
        pearlPink: RGB(0xFF9EDE), // удаление и прочее необратимое
        error: RGB(0xFFB4AB)      // ошибки в формах — читается на тёмном
    )

    static let light = PaletteColors(
        lilac: RGB(0x7A47A8),     // акцент — темнее, чтобы читался на светлом
        orchid: RGB(0xD7B9F0),    // градиенты и кнопки — пастель
        violet: RGB(0xE6D6F7),    // свои сообщения, заливки
        plum: RGB(0xFFFFFF),      // стекло — белое
        night: RGB(0xF7F3FD),     // основной фон
        ocean: RGB(0xBDD8F5),     // вторичный акцент — пастель
        sky: RGB(0x2C6CAE),       // ссылки, индикаторы
        void: RGB(0xEDE6F8),      // глубина фона
        ink: RGB(0x1F1A33),       // основной текст
        mist: RGB(0x6A6285),      // второстепенный текст
        line: RGB(0x7A47A8), lineAlpha: 0.2, // линии и рамки
        pearlPink: RGB(0xC0398C), // удаление — насыщеннее, чтобы читалось
        error: RGB(0xB3266F)      // ошибки в формах — читается на светлом
    )

    /// Цвета темы, которая действует сейчас.
    static var current: PaletteColors { ThemeState.shared.light ? light : dark }

    static var lilacRGB: RGB { current.lilac }
    static var orchidRGB: RGB { current.orchid }
    static var violetRGB: RGB { current.violet }
    static var plumRGB: RGB { current.plum }
    static var nightRGB: RGB { current.night }
    static var oceanRGB: RGB { current.ocean }
    static var skyRGB: RGB { current.sky }
    static var voidRGB: RGB { current.void }
    static var inkRGB: RGB { current.ink }
    static var mistRGB: RGB { current.mist }

    static var lilac: Color { lilacRGB.color }     // акценты, выбранная вкладка
    static var orchid: Color { orchidRGB.color }   // градиенты, кнопки
    static var violet: Color { violetRGB.color }   // свои сообщения, заливки
    static var plum: Color { plumRGB.color }       // стеклянные карточки
    static var night: Color { nightRGB.color }     // основной фон
    static var ocean: Color { oceanRGB.color }     // вторичный акцент
    static var sky: Color { skyRGB.color }         // ссылки, индикаторы
    static var void: Color { voidRGB.color }       // глубина фона, заставка
    static var ink: Color { inkRGB.color }         // основной текст
    static var mist: Color { mistRGB.color }       // второстепенный текст
    static var line: Color { current.line.opacity(current.lineAlpha) } // тонкие линии и рамки

    /// Ошибки в формах — мягкий розово-красный на тёмном, насыщенный на светлом.
    static var error: Color { current.error.color }

    /// Розовый перламутр — тон кристалла; удаление и прочее необратимое.
    static var pearlPink: Color { current.pearlPink.color }

    /// Текст и значки поверх фото и видео — светлые в любой теме.
    static var onMediaRGB: RGB { dark.ink }
    static var onMedia: Color { dark.ink.color }
}
