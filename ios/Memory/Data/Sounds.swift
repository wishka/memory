import AudioToolbox
import AVFoundation
import Foundation
import UserNotifications

/// Звуки уведомлений в стиле Memory: стекло, звёзды, космос. Файлы —
/// Sounds/snd_*.wav (их рисует scripts/media_assets.py). У каждого вида
/// уведомлений свой звук; поменять — «Настройки → Уведомления → Звуки».
/// Те же звуки и правила — в Sounds.kt.
enum NotifySound: String, CaseIterable, Identifiable {
    case crystal, stardust, comet, moon, prism, nebula, orbit, droplet, system, silent

    var id: String { rawValue }

    private var ru: String {
        switch self {
        case .crystal: return "Кристалл"
        case .stardust: return "Звёздная пыль"
        case .comet: return "Комета"
        case .moon: return "Луна"
        case .prism: return "Призма"
        case .nebula: return "Туманность"
        case .orbit: return "Орбита"
        case .droplet: return "Капля света"
        case .system: return "Как в телефоне"
        case .silent: return "Без звука"
        }
    }

    var title: String { t(ru) }

    /// Имя файла без расширения; nil — звук телефона или тишина.
    var file: String? { self == .system || self == .silent ? nil : "snd_\(rawValue)" }

    static func of(_ id: String?) -> NotifySound? { allCases.first { $0.rawValue == id } }

    /// Звук по умолчанию: у каждого вида — свой, чтобы различать на слух.
    static func `default`(_ kind: NudgeKind) -> NotifySound {
        switch kind {
        case .messages: return .crystal
        case .prism: return .prism
        case .reactions: return .stardust
        case .follows: return .orbit
        case .groups: return .moon
        case .friends: return .comet
        case .recap: return .nebula
        }
    }

    /// Звук системного уведомления: свой файл, звук телефона или ничего.
    var notificationSound: UNNotificationSound? {
        switch self {
        case .silent: return nil
        case .system: return UNNotificationSound.default
        default: return UNNotificationSound(named: UNNotificationSoundName("snd_\(rawValue).wav"))
        }
    }
}

/// Выбор звуков — настройка телефона, не аккаунта.
struct SoundPrefs {
    private let defaults: UserDefaults
    private static let prefix = "sound."

    init(_ defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func get(_ kind: NudgeKind) -> NotifySound {
        NotifySound.of(defaults.string(forKey: Self.prefix + kind.rawValue)) ?? NotifySound.default(kind)
    }

    func set(_ kind: NudgeKind, _ sound: NotifySound) {
        defaults.set(sound.rawValue, forKey: Self.prefix + kind.rawValue)
    }

    func all() -> [NudgeKind: NotifySound] {
        Dictionary(uniqueKeysWithValues: NudgeKind.allCases.map { ($0, get($0)) })
    }
}

/// Проиграть звук: в выборе звука и для плашки, когда приложение открыто.
enum SoundPlayer {
    private static var current: AVAudioPlayer?

    static func url(_ sound: NotifySound) -> URL? {
        guard let file = sound.file else { return nil }
        return Bundle.main.url(forResource: file, withExtension: "wav")
    }

    static func play(_ sound: NotifySound) {
        switch sound {
        case .silent:
            return
        case .system:
            // Звук уведомления телефона
            AudioServicesPlaySystemSound(1007)
        default:
            guard let url = url(sound) else { return }
            // Не перебиваем музыку: звук уведомления — поверх, как у системных
            let session = AVAudioSession.sharedInstance()
            if session.category == .soloAmbient { try? session.setCategory(.ambient, options: [.mixWithOthers]) }
            current?.stop()
            current = try? AVAudioPlayer(contentsOf: url)
            current?.play()
        }
    }
}

extension NudgeKind {
    /// Название вида уведомлений в выборе звуков.
    var soundTitle: String {
        switch self {
        case .messages: return t("Сообщения")
        case .prism: return t("PRISM друзей")
        case .reactions: return t("Лайки и комментарии")
        case .follows: return t("Новые подписчики")
        case .groups: return t("Группы")
        case .friends: return t("Друзья")
        case .recap: return t("Итоги")
        }
    }
}

/// Порядок в настройках: сначала самое частое.
let soundKinds: [NudgeKind] = [.messages, .prism, .reactions, .follows, .groups, .friends, .recap]
