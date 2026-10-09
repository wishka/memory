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

    /// Кегль без учёта настройки «Размер текста».
    var baseSize: CGFloat {
        switch self {
        case .display: return 30
        case .headline: return 22
        case .title: return 16
        case .body: return 16
        case .bodySmall: return 14
        case .labelLarge: return 14
        case .label: return 10
        }
    }

    /// Шрифт с учётом «Размера текста» из настроек: при смене размера
    /// экраны перерисовываются сами — L10n наблюдаемый.
    var font: Font {
        let size = baseSize * CGFloat(L10n.shared.textScale)
        switch self {
        case .display, .headline: return .custom(Fonts.unboundedExtraLight, size: size)
        case .title: return .custom(Fonts.interSemiBold, size: size)
        case .body, .bodySmall: return .custom(Fonts.interRegular, size: size)
        case .labelLarge, .label: return .custom(Fonts.interMedium, size: size)
        }
    }

    /// Разрядка в пунктах: на Android она в долях кегля (em).
    var tracking: CGFloat {
        let em: CGFloat
        switch self {
        case .display: em = 0.06
        case .headline: em = 0.04
        case .labelLarge: em = 0.02
        case .label: em = 0.14
        default: em = 0
        }
        return baseSize * CGFloat(L10n.shared.textScale) * em
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
