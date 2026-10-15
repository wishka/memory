import BackgroundTasks
import Foundation
import UIKit

/// Отправка, пока приложение свёрнуто или закрыто (на Android — NetWorker
/// и WorkManager):
///
/// 1. Свернули с неотправленным — beginBackgroundTask: система даёт
///    приложению ещё немного времени, отправка идёт обычными проходами
///    модели, пока очередь не опустеет или время не кончится.
/// 2. Не успели — BGProcessingTask «ru.wishka.memory.net» (когда есть сеть):
///    система запустит его позже, даже если приложение выгружено. Он
///    открывает сеть последнего вошедшего (DevicePrefs.lastLogin) и шлёт
///    очередь. Регистрируется, только если идентификатор есть в
///    BGTaskSchedulerPermittedIdentifiers в Info.plist — иначе регистрация
///    уронила бы приложение. С демо-сервером не нужен: он живёт только
///    внутри открытого приложения.
///
/// Открыли приложение снова — фоновую работу снимаем, дальше отправляет оно само.
final class BackgroundNet {
    static let identifier = "ru.wishka.memory.net"
    static let shared = BackgroundNet()

    /// Сколько проходов отправки за один фоновый запуск.
    private static let maxRounds = 40

    private var taskId: UIBackgroundTaskIdentifier = .invalid
    private var work: Task<Void, Never>?

    /// Разрешён ли фоновый запуск в Info.plist.
    static var permitted: Bool {
        let ids = Bundle.main.object(forInfoDictionaryKey: "BGTaskSchedulerPermittedIdentifiers") as? [String]
        return ids?.contains(identifier) ?? false
    }

    /// Фоновая обработка имеет смысл: разрешена и сервер настоящий.
    private static var processingEnabled: Bool { permitted && !NetSetup.usesDemo }

    // MARK: - Свернули: дослать сразу

    /// Приложение ушло в фон: есть неотправленное — дослать, пока система даёт время.
    func enteredBackground(_ model: AppModel) {
        guard model.net.busy else { return }
        if taskId == .invalid {
            taskId = UIApplication.shared.beginBackgroundTask(withName: "memory-net") { [weak self] in
                // Время вышло: остальное — фоновой обработкой
                self?.work?.cancel()
                self?.finish()
            }
        }
        work?.cancel()
        work = Task { @MainActor [weak self] in
            let done = await model.drainNet()
            // Снятая работа (приложение открыли или свернули снова) не закрывает
            // чужое фоновое время — его закрыл тот, кто снял
            if Task.isCancelled { return }
            if !done { BackgroundNet.schedule() }
            self?.finish()
        }
        // На случай, если система не даст доработать
        BackgroundNet.schedule()
    }

    /// Приложение снова открыто — отправляет само.
    func becameActive() {
        work?.cancel()
        work = nil
        finish()
        BackgroundNet.cancel()
    }

    private func finish() {
        guard taskId != .invalid else { return }
        let id = taskId
        taskId = .invalid
        UIApplication.shared.endBackgroundTask(id)
    }

    // MARK: - Закрыто: BGProcessingTask

    /// Зарегистрировать обработчик — до конца запуска приложения (MemoryApp.init).
    static func register() {
        guard processingEnabled else { return }
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            guard let processing = task as? BGProcessingTask else {
                task.setTaskCompleted(success: true)
                return
            }
            BackgroundNet.handle(processing)
        }
    }

    /// Досылать, когда будет сеть.
    static func schedule() {
        guard processingEnabled else { return }
        let request = BGProcessingTaskRequest(identifier: identifier)
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        try? BGTaskScheduler.shared.submit(request)
    }

    static func cancel() {
        guard processingEnabled else { return }
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: identifier)
    }

    private static func handle(_ task: BGProcessingTask) {
        let job = Task { () -> Bool in
            await BackgroundNet.run()
        }
        task.expirationHandler = {
            job.cancel()
        }
        Task {
            let ok = await job.value
            // Не всё ушло — попробовать ещё раз позже
            if !ok { BackgroundNet.schedule() }
            task.setTaskCompleted(success: ok)
        }
    }

    /// Одна фоновая отправка: сеть последнего вошедшего, проходы до пустой
    /// очереди. Ответ — всё ли ушло (или повторять бессмысленно).
    static func run() async -> Bool {
        let prefs = DevicePrefs()
        guard let login = prefs.lastLogin else { return true }
        let me = User(id: prefs.lastMeId, name: prefs.lastMeName, handle: login)
        let quality = prefs.mediaQuality
        let net = NetSetup.forAccount(
            login: login, me: { me }, clock: { Json.ms(Date()) },
            quality: { quality }, unmetered: { false }, tokens: SecureStore()
        )
        defer { (net.cache as? SqliteCache)?.close() }
        for _ in 0..<maxRounds {
            if Task.isCancelled { return false }
            if !net.busy { return true }
            let r = await net.pump(maxUploads: 5)
            for job in r.uploads where job.state == .done { MediaCompressor.cleanup(job) }
            // Вход закрыт — ждём, пока войдут снова в приложении
            if r.authLost { return true }
            if r.offline { return false }
            // Всё ждёт паузы после ошибок — не крутиться впустую
            let wait: Int64 = net.nextAttemptAt() - Json.ms(Date())
            if wait > 30_000 { return false }
            if wait > 0 { try? await Task.sleep(nanoseconds: UInt64(wait) * 1_000_000) }
        }
        return !net.busy
    }
}
