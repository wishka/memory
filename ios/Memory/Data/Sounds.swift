import AudioToolbox
import AVFoundation
import Foundation
import UserNotifications

/// Звуки уведомлений Memory: 57 своих в девяти стилях, звук телефона и тишина.
/// Список — SoundCatalog.swift, файлы — Sounds/snd_*.caf; всё это собирает
/// scripts/make_sounds.py из scripts/sounds/catalog.py. Те же звуки — в Sounds.kt.
extension NotifySound {
    /// Звук системного уведомления: свой файл, звук телефона или ничего.
    var notificationSound: UNNotificationSound? {
        switch self {
        case .silent: return nil
        case .system: return UNNotificationSound.default
        default: return UNNotificationSound(named: UNNotificationSoundName("snd_\(rawValue).caf"))
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
        return Bundle.main.url(forResource: file, withExtension: "caf")
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
        case .follows: return t("Заявки в друзья")
        case .groups: return t("Группы")
        case .friends: return t("Друзья")
        case .recap: return t("Итоги")
        }
    }
}

/// Порядок в настройках: сначала самое частое.
let soundKinds: [NudgeKind] = [.messages, .prism, .reactions, .follows, .groups, .friends, .recap]
