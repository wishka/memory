import Foundation

/// Фон переписки. Коллекция — рисуется кодом в цветах приложения (ни одной
/// картинки в сборке), плюс своё фото с затемнением, чтобы пузыри читались.
/// Общий фон — в настройках, свой у чата — в его меню. Те же фоны — в
/// Backdrops.kt.
enum BackdropStyle: String, CaseIterable, Identifiable {
    case cosmos, nebula, aurora, crystals, constellations, prism, dusk, deep, lilac, plain, photo

    var id: String { rawValue }

    private var ru: String {
        switch self {
        case .cosmos: return "Космос"
        case .nebula: return "Туманность"
        case .aurora: return "Сияние"
        case .crystals: return "Кристаллы"
        case .constellations: return "Созвездия"
        case .prism: return "Призма"
        case .dusk: return "Сумерки"
        case .deep: return "Глубина"
        case .lilac: return "Лиловый"
        case .plain: return "Однотонный"
        case .photo: return "Своё фото"
        }
    }

    var title: String { t(ru) }

    static func of(_ id: String?) -> BackdropStyle { allCases.first { $0.rawValue == id } ?? .cosmos }

    /// Коллекция — всё, кроме своего фото.
    static var collection: [BackdropStyle] { allCases.filter { $0 != .photo } }

    /// Номер по порядку — зерно звёзд у фона.
    var index: Int { Self.allCases.firstIndex(of: self) ?? 0 }
}

/// Выбранный фон: стиль, для своего фото — имя копии в папке вложений и
/// затемнение 0…0.8.
struct Backdrop: Equatable, Codable {
    var style: String = BackdropStyle.cosmos.id
    var photo: String = ""
    var dim: Double = 0.35

    var kind: BackdropStyle {
        let k = BackdropStyle.of(style)
        return k == .photo && photo.isEmpty ? .cosmos : k
    }

    static let `default` = Backdrop()
}

extension Backdrop {
    private enum CodingKeys: String, CodingKey {
        case style, photo, dim
    }

    /// Чтение с запасом: пропущенное поле — значение по умолчанию.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        style = try c.decodeIfPresent(String.self, forKey: .style) ?? BackdropStyle.cosmos.id
        photo = try c.decodeIfPresent(String.self, forKey: .photo) ?? ""
        dim = try c.decodeIfPresent(Double.self, forKey: .dim) ?? 0.35
    }
}

extension Chat {
    /// Какой фон у переписки: свой у чата, иначе общий.
    func backdropOr(_ global: Backdrop) -> Backdrop { backdrop ?? global }
}
