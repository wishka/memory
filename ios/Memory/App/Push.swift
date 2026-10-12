import Observation
import UIKit
import UserNotifications

/// Уведомления на iPhone: локальные (их собирает само приложение из
/// событий) и пуши APNs с сервера. Сервер присылает только факты — текст
/// складывается здесь же, тем же Nudges, что и для локальных, на языке
/// интерфейса. Нажатие открывает приложение там, о чём уведомление.
/// Подключение APNs — docs/PUSH.md.
@Observable
final class PushCenter {
    static let shared = PushCenter()

    /// Уведомление, по которому открыли приложение; RootView откроет его и обнулит.
    var opened: Nudge?
    /// Пуш, пришедший, пока приложение открыто: RootView покажет его плашкой.
    var incoming: Nudge?

    /// Токен устройства для APNs: пока сервера нет, только хранится.
    var token: String? {
        get { UserDefaults.standard.string(forKey: "apnsToken") }
        set { UserDefaults.standard.set(newValue, forKey: "apnsToken") }
    }

    /// Спросить разрешение (один раз) и зарегистрироваться в APNs.
    func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
        }
    }

    /// Показать системным уведомлением — когда приложение свёрнуто.
    func show(_ nudge: Nudge) {
        let content = UNMutableNotificationContent()
        content.title = nudge.title
        content.body = nudge.text
        content.sound = .default
        content.threadIdentifier = nudge.kind.rawValue
        content.userInfo = [
            "kind": nudge.kind.rawValue, "title": nudge.title, "text": nudge.text,
            "target": nudge.target.rawValue, "targetId": String(nudge.targetId),
        ]
        let request = UNNotificationRequest(identifier: nudge.id.uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }

    /// Уведомление из userInfo: своё локальное или пуш с сервера.
    static func nudge(from userInfo: [AnyHashable: Any]) -> Nudge? {
        var data: [String: String] = [:]
        for (key, value) in userInfo {
            if let k = key as? String { data[k] = "\(value)" }
        }
        if let target = data["target"].flatMap(NudgeTarget.init(rawValue:)) {
            return Nudge(
                kind: data["kind"].flatMap(NudgeKind.init(rawValue:)) ?? .friends,
                title: data["title"] ?? "", text: data["text"] ?? "",
                target: target, targetId: Int64(data["targetId"] ?? "") ?? 0
            )
        }
        return Nudges.fromPush(data)
    }
}

/// Делегат приложения: регистрация в APNs и нажатия на уведомления.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        PushCenter.shared.token = deviceToken.map { String(format: "%02x", $0) }.joined()
    }

    /// Без возможности Push Notifications в Xcode регистрация не пройдёт —
    /// это не ошибка приложения: локальные уведомления работают и так.
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {}

    /// Тихий пуш с данными: текст собираем здесь и показываем сами.
    func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable: Any]) async -> UIBackgroundFetchResult {
        guard let nudge = PushCenter.nudge(from: userInfo) else { return .noData }
        PushCenter.shared.show(nudge)
        return .newData
    }

    /// Пока приложение открыто, системный баннер не нужен — пуш показывается своей плашкой.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        if let nudge = PushCenter.nudge(from: notification.request.content.userInfo) {
            await MainActor.run { PushCenter.shared.incoming = nudge }
        }
        return []
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        guard let nudge = PushCenter.nudge(from: info) else { return }
        await MainActor.run { PushCenter.shared.opened = nudge }
    }
}
