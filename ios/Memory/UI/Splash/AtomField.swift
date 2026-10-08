import Foundation

/// Как слово ведёт себя на заставке.
///
/// dissolve — слово стоит целиком и, начиная с последней буквы,
/// рассыпается на атомы. assemble — атомы слетаются из пространства и
/// собирают слово с первой буквы. assembleFromCenter — сборка начинается
/// с середины слова и расходится в обе стороны.
enum SplashMode {
    case dissolve
    case assemble
    case assembleFromCenter
}

/// Физика заставки: слово из точек-«атомов». Перенос AtomField.kt.
///
/// Положение каждого атома считается формулой от времени, а не шагами
/// симуляции. Поэтому кадр не зависит от частоты экрана (60 или 120 Гц —
/// картинка одна и та же), ничего не копится между кадрами, а правила
/// можно проверить обычным юнит-тестом.
///
/// Сборка — это распад, пущенный назад во времени: атом летит по той же
/// траектории, только к своей точке в букве, а не от неё.
///
/// xs, ys — точки букв в пикселях экрана. Время — в миллисекундах от
/// начала заставки.
final class AtomField {
    /// Тайминги в миллисекундах.
    struct Timing {
        /// dissolve: слово проявляется.
        var appearMs: Double = 500
        /// dissolve: слово стоит целиком перед распадом. assemble: собранное слово стоит до перехода.
        var holdMs: Double = 250
        /// Волна проходит по слову: распад — с конца, сборка — с начала.
        var sweepMs: Double = 800
        /// Разброс момента соседних атомов — край волны «рваный».
        var jitterMs: Double = 160
        /// Полёт атома.
        var lifeMs: Double = 700
        /// Пауза в конце перед переходом в приложение.
        var tailMs: Double = 120
    }

    static let crystallizeMs: Double = 350
    static let shimmerMs: Double = 550

    let mode: SplashMode
    let timing: Timing
    let size: Int

    private let xs: [Double]
    private let ys: [Double]
    /// dissolve: момент отрыва атома. assemble: момент, когда он встал на место.
    private let pivot: [Double]
    private let vx: [Double]
    private let vy: [Double]
    private let wobbleAmp: [Double]
    private let wobblePhase: [Double]
    private let radius: [Double]
    /// 0...1 — оттенок атома в полёте.
    let hue: [Double]

    /// Когда заставка полностью закончилась.
    let totalMs: Double
    /// Когда слово целиком собрано (assemble) или когда начинается распад (dissolve).
    let wordCompleteMs: Double

    init(xs: [Double], ys: [Double], mode: SplashMode = .dissolve, seed: Int = 7, timing: Timing = Timing()) {
        self.xs = xs
        self.ys = ys
        self.mode = mode
        self.timing = timing
        let n = xs.count
        size = n
        // Сначала локальные массивы, потом в свойства: так инициализация
        // класса не читает свои поля до того, как все они заданы
        var pivot = [Double](repeating: 0, count: n)
        var vx = [Double](repeating: 0, count: n)
        var vy = [Double](repeating: 0, count: n)
        var wobbleAmp = [Double](repeating: 0, count: n)
        var wobblePhase = [Double](repeating: 0, count: n)
        var radius = [Double](repeating: 0, count: n)
        var hue = [Double](repeating: 0, count: n)

        let minX = xs.min() ?? 0
        let maxX = xs.max() ?? 1
        let span = max(1, maxX - minX)
        var rnd = SeededRandom(seed)
        let t = timing
        for i in 0..<n {
            // 0 — левый край слова (M), 1 — правый (Y)
            let xn = (xs[i] - minX) / span
            let jitter = (rnd.nextDouble() * t.jitterMs).rounded(.down)
            switch mode {
            case .dissolve:
                // Распад с конца: правые атомы отрываются первыми
                pivot[i] = t.appearMs + t.holdMs + ((1 - xn) * t.sweepMs).rounded(.down) + jitter
            case .assemble:
                // Сборка с начала: левые атомы встают на место первыми
                pivot[i] = t.lifeMs + (xn * t.sweepMs).rounded(.down) + jitter
            case .assembleFromCenter:
                // От середины к краям: чем дальше от центра, тем позже
                pivot[i] = t.lifeMs + (abs(xn - 0.5) * 2 * t.sweepMs).rounded(.down) + jitter
            }
            let angle: Double
            if mode == .assembleFromCenter {
                // Левая половина — влево, правая — вправо, с разбросом вверх и вниз
                let spread = (rnd.nextDouble() - 0.5) * 1.6
                angle = xn < 0.5 ? Double.pi + spread : spread
            } else {
                // Разлёт вправо-вверх, как дым на ветру, с разбросом направлений
                angle = -Double.pi / 2 + 0.95 + (rnd.nextDouble() - 0.5) * 1.9
            }
            let speed = 0.1 + rnd.nextDouble() * 0.25   // пикс./мс
            vx[i] = cos(angle) * speed
            vy[i] = sin(angle) * speed
            wobbleAmp[i] = 4 + rnd.nextDouble() * 14
            wobblePhase[i] = rnd.nextDouble() * 2 * Double.pi
            radius[i] = 0.75 + rnd.nextDouble() * 0.5
            hue[i] = rnd.nextDouble()
        }

        self.pivot = pivot
        self.vx = vx
        self.vy = vy
        self.wobbleAmp = wobbleAmp
        self.wobblePhase = wobblePhase
        self.radius = radius
        self.hue = hue

        let lastPivot = pivot.max()
        switch mode {
        case .dissolve:
            totalMs = (lastPivot ?? (timing.appearMs + timing.holdMs)) + timing.lifeMs + timing.tailMs
            wordCompleteMs = timing.appearMs
        case .assemble, .assembleFromCenter:
            // После сборки слово «кристаллизуется» и по нему пробегает блик
            totalMs = (lastPivot ?? timing.lifeMs) + AtomField.crystallizeMs + AtomField.shimmerMs + timing.holdMs + timing.tailMs
            wordCompleteMs = lastPivot ?? 0
        }
    }

    /// 0...1 — насколько слово превратилось из атомов в кристалл.
    func crystal(_ t: Double) -> Double {
        if mode == .dissolve { return 0 }
        return min(1, max(0, (t - wordCompleteMs) / Self.crystallizeMs))
    }

    /// Положение блика по слову: nil — блика нет, 0...1 — слева направо.
    func shimmer(_ t: Double) -> Double? {
        if mode == .dissolve { return nil }
        let p = (t - wordCompleteMs - Self.crystallizeMs) / Self.shimmerMs
        return (0...1).contains(p) ? p : nil
    }

    /// dissolve: момент отрыва атома i. assemble: момент, когда он встал на место.
    func pivotAt(_ i: Int) -> Double { pivot[i] }

    /// Время полёта атома: 0 — атом в букве, больше нуля — в пути.
    private func flight(_ i: Int, _ t: Double) -> Double {
        mode == .dissolve ? max(0, t - pivot[i]) : max(0, pivot[i] - t)
    }

    /// Атом ещё (или уже) виден.
    func alive(_ i: Int, _ t: Double) -> Bool { flight(i, t) < timing.lifeMs }

    /// Доля пути: 0 — в букве, 1 — растворён в пространстве.
    func decay(_ i: Int, _ t: Double) -> Double {
        min(1, max(0, flight(i, t) / timing.lifeMs))
    }

    func x(_ i: Int, _ t: Double) -> Double {
        let a = flight(i, t)
        let wobble = a > 0 ? sin(a * 0.012 + wobblePhase[i]) * wobbleAmp[i] * (a / timing.lifeMs) : 0
        return xs[i] + vx[i] * a + wobble
    }

    func y(_ i: Int, _ t: Double) -> Double {
        let a = flight(i, t)
        // Лёгкое ускорение вверх: атомы «всплывают» (при сборке — опускаются на место)
        return ys[i] + vy[i] * a - 0.00009 * a * a
    }

    /// Прозрачность: атом проявляется по мере приближения к букве и тает при удалении.
    func alpha(_ i: Int, _ t: Double) -> Double {
        let fade = 1 - pow(decay(i, t), 1.4)
        switch mode {
        case .dissolve:
            return smooth(min(1, t / timing.appearMs)) * fade
        case .assemble, .assembleFromCenter:
            // Когда слово стало кристаллом, атомы растворяются в нём
            return fade * (1 - crystal(t))
        }
    }

    /// Множитель радиуса: у буквы атом вспыхивает, вдали — тает.
    func radiusScale(_ i: Int, _ t: Double) -> Double {
        let d = decay(i, t)
        if d <= 0 { return radius[i] }
        let flash = d < 0.15 ? 1 + d / 0.15 * 0.6 : 1.6 - (d - 0.15) / 0.85 * 1.2
        return radius[i] * max(0.2, flash)
    }

    private func smooth(_ v: Double) -> Double { v * v * (3 - 2 * v) }
}
