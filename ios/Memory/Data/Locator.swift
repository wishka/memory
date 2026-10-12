import CoreLocation
import Foundation

/// Где телефон — для клада, находки миссии и вылазки. Разовый запрос
/// места через CLLocationManager: разрешение «при использовании»,
/// requestLocation и не больше 10 секунд ожидания; не успел — последнее
/// известное место. То же — в Locator.kt.
///
/// Нужен ключ NSLocationWhenInUseUsageDescription в Info.plist.
/// Вызывать с главного потока: менеджер присылает ответы туда, где создан.
final class Locator: NSObject, CLLocationManagerDelegate {
    static let shared = Locator()

    private let manager = CLLocationManager()
    private var waiters: [CheckedContinuation<LatLon?, Never>] = []
    private var authWaiters: [CheckedContinuation<Void, Never>] = []
    /// Номер запроса: таймер старого запроса не обрывает новый.
    private var generation = 0

    private override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
    }

    /// Разрешение уже есть.
    var permitted: Bool {
        let status = manager.authorizationStatus
        return status == .authorizedWhenInUse || status == .authorizedAlways
    }

    /// Текущее место; nil — нет разрешения, геолокация выключена или не успела за 10 с.
    @MainActor
    func current() async -> LatLon? {
        if manager.authorizationStatus == .notDetermined {
            // Без ключа в Info.plist iOS молча не покажет окно — не ждём минуту впустую
            guard Bundle.main.object(forInfoDictionaryKey: "NSLocationWhenInUseUsageDescription") != nil else { return nil }
            await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
                authWaiters.append(cont)
                manager.requestWhenInUseAuthorization()
                // Окно не показалось (например, нет ключа в Info.plist) — не ждём вечно
                DispatchQueue.main.asyncAfter(deadline: .now() + 60) { [weak self] in
                    self?.resumeAuthWaiters()
                }
            }
        }
        guard permitted else { return nil }
        let fresh: LatLon? = await withCheckedContinuation { (cont: CheckedContinuation<LatLon?, Never>) in
            waiters.append(cont)
            if waiters.count == 1 {
                generation += 1
                let mine = generation
                manager.requestLocation()
                DispatchQueue.main.asyncAfter(deadline: .now() + 10) { [weak self] in
                    guard let self, self.generation == mine else { return }
                    self.finish(nil)
                }
            }
        }
        if let fresh { return fresh }
        return manager.location.map { LatLon(lat: $0.coordinate.latitude, lon: $0.coordinate.longitude) }
    }

    private func finish(_ place: LatLon?) {
        let pending = waiters
        waiters = []
        generation += 1
        pending.forEach { $0.resume(returning: place) }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let last = locations.last else { return }
        finish(LatLon(lat: last.coordinate.latitude, lon: last.coordinate.longitude))
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        finish(nil)
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        // Пока человек не ответил, статус «не определён» — ждём дальше
        guard manager.authorizationStatus != .notDetermined else { return }
        resumeAuthWaiters()
    }

    private func resumeAuthWaiters() {
        let pending = authWaiters
        authWaiters = []
        pending.forEach { $0.resume() }
    }
}
