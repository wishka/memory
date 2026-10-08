import CoreText
import SwiftUI
import UIKit

/// Два шрифта, как на Android: широкий тонкий Unbounded — для заголовков
/// и слова MEMORY, спокойный Inter — для текста. Оба под лицензией OFL,
/// с кириллицей. Файлы лежат в Memory/Fonts и подключаются при запуске —
/// без списка в Info.plist.
enum Fonts {
    static let unboundedExtraLight = "Unbounded-ExtraLight"
    static let unboundedRegular = "Unbounded-Regular"
    static let unboundedSemiBold = "Unbounded-SemiBold"
    static let interLight = "Inter-Light"
    static let interRegular = "Inter-Regular"
    static let interMedium = "Inter-Medium"
    static let interSemiBold = "Inter-SemiBold"

    private static var registered = false

    /// Подключить шрифты из бандла. Повторный вызов ничего не делает.
    static func register() {
        if registered { return }
        registered = true
        let names = [unboundedExtraLight, unboundedRegular, unboundedSemiBold,
                     interLight, interRegular, interMedium, interSemiBold]
        for name in names {
            guard let url = Bundle.main.url(forResource: name, withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }

    /// Шрифт для рисования (заставка): если файл не нашёлся — системный.
    static func uiFont(_ name: String, size: CGFloat) -> UIFont {
        UIFont(name: name, size: size) ?? UIFont.systemFont(ofSize: size, weight: .semibold)
    }
}

/// Стили текста — те же, что MemoryTypography на Android.
enum TextStyle {
    /// Заголовок экрана: «ЛЕНТА», «ЧАТЫ», «ПРОФИЛЬ».
    case display
    case headline
    case title
    case body
    case bodySmall
    case labelLarge
    /// Подписи вкладок и мелкие метки — капслоком с разрядкой.
    case label

    var font: Font {
        switch self {
        case .display: return .custom(Fonts.unboundedExtraLight, size: 30)
        case .headline: return .custom(Fonts.unboundedExtraLight, size: 22)
        case .title: return .custom(Fonts.interSemiBold, size: 16)
        case .body: return .custom(Fonts.interRegular, size: 16)
        case .bodySmall: return .custom(Fonts.interRegular, size: 14)
        case .labelLarge: return .custom(Fonts.interMedium, size: 14)
        case .label: return .custom(Fonts.interMedium, size: 10)
        }
    }

    /// Разрядка в пунктах: на Android она в долях кегля (em).
    var tracking: CGFloat {
        switch self {
        case .display: return 30 * 0.06
        case .headline: return 22 * 0.04
        case .labelLarge: return 14 * 0.02
        case .label: return 10 * 0.14
        default: return 0
        }
    }

    /// Межстрочный интервал сверх обычного.
    var lineSpacing: CGFloat {
        switch self {
        case .display: return 4
        case .headline: return 4
        case .body: return 4
        case .bodySmall: return 3
        default: return 0
        }
    }
}

extension View {
    func textStyle(_ style: TextStyle, _ color: Color = Palette.ink) -> some View {
        self.font(style.font)
            .tracking(style.tracking)
            .lineSpacing(style.lineSpacing)
            .foregroundStyle(color)
    }
}
