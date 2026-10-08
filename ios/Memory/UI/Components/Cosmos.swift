import SwiftUI

/// Фон всех экранов: глубокий тёмно-синий, два размытых свечения —
/// фиолетовое сверху справа и синее снизу слева — и редкие звёзды.
/// Звёзды неподвижны и одинаковы при каждом запуске: мерцающий фон
/// отвлекал бы от ленты.
struct CosmicBackground: View {
    private static let stars: [(Double, Double, Double)] = {
        var rnd = SeededRandom(42)
        return (0..<70).map { _ in (rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble()) }
    }()

    var body: some View {
        Canvas { context, size in
            let rect = CGRect(origin: .zero, size: size)
            context.fill(Path(rect), with: .linearGradient(
                Gradient(colors: [Palette.night, Palette.void, Palette.void]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)
            ))
            context.fill(Path(rect), with: .radialGradient(
                Gradient(colors: [Palette.orchidRGB.opacity(0.32), Palette.orchidRGB.opacity(0)]),
                center: CGPoint(x: size.width * 0.92, y: size.height * 0.06),
                startRadius: 0, endRadius: size.width * 0.9
            ))
            context.fill(Path(rect), with: .radialGradient(
                Gradient(colors: [Palette.oceanRGB.opacity(0.30), Palette.oceanRGB.opacity(0)]),
                center: CGPoint(x: size.width * 0.05, y: size.height * 0.88),
                startRadius: 0, endRadius: size.width
            ))
            for (x, y, s) in Self.stars {
                let r = (0.6 + s * 1.3) / 2
                context.fill(
                    SplashSky.circle(CGPoint(x: x * size.width, y: y * size.height), r),
                    with: .color(Palette.inkRGB.opacity(0.15 + s * 0.45))
                )
            }
        }
        .ignoresSafeArea()
    }
}

extension View {
    /// Стекло: полупрозрачная сливовая заливка и тонкая сиренево-синяя кромка.
    func glass<S: Shape>(_ shape: S, strong: Bool = false) -> some View {
        self
            .background(shape.fill(Palette.plumRGB.opacity(strong ? 0.7 : 0.38)))
            .overlay(
                // Линия в 2pt по контуру, обрезанная формой, — кромка в 1pt внутри
                shape.stroke(
                    LinearGradient(
                        colors: [Palette.lilacRGB.opacity(0.38), Palette.skyRGB.opacity(0.12)],
                        startPoint: .topLeading, endPoint: .bottomTrailing
                    ),
                    lineWidth: 2
                )
            )
            .clipShape(shape)
    }

    func glass(radius: CGFloat = 22, strong: Bool = false) -> some View {
        glass(RoundedRectangle(cornerRadius: radius, style: .continuous), strong: strong)
    }

    /// Отступы карточки от краёв экрана — как на Android (16).
    func cardPadding() -> some View {
        self.frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 16)
    }
}

/// Стеклянная карточка.
struct GlassCard<Content: View>: View {
    var radius: CGFloat = 22
    @ViewBuilder let content: () -> Content

    var body: some View {
        content().glass(radius: radius)
    }
}

/// Градиент аватара по оттенку человека: от сирени через фиолетовый к синему.
func avatarGradient(_ hue: Double) -> LinearGradient {
    let a = Palette.lilacRGB.lerp(Palette.skyRGB, hue)
    let b = Palette.violetRGB.lerp(Palette.oceanRGB, 1 - hue)
    return LinearGradient(colors: [a.color, b.color], startPoint: .topLeading, endPoint: .bottomTrailing)
}

/// Аватар: градиентный кружок с инициалами. ring — светящееся кольцо
/// (свой профиль); showOnline — точка «в сети».
struct Avatar: View {
    let user: User
    var size: CGFloat = 44
    var ring: Bool = false
    var showOnline: Bool = false

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ZStack {
                Circle()
                    .fill(avatarGradient(user.hue))
                    .padding(ring ? 5 : 0)
                Text(user.initials)
                    .font(.custom(Fonts.interSemiBold, size: size * 0.34))
                    .foregroundStyle(Palette.ink)
                if ring {
                    Circle().strokeBorder(
                        AngularGradient(colors: [Palette.lilac, Palette.sky, Palette.orchid, Palette.lilac], center: .center),
                        lineWidth: 1.5
                    )
                }
            }
            .frame(width: size, height: size)
            if showOnline && user.online {
                Circle()
                    .fill(Palette.sky)
                    .padding(2)
                    .background(Circle().fill(Palette.void))
                    .frame(width: size * 0.26, height: size * 0.26)
            }
        }
    }
}

/// Заголовок экрана — тонкий широкий Unbounded капслоком, как в референсах.
struct ScreenTitle: View {
    let title: String
    var subtitle: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title.uppercased())
                .textStyle(.display)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            if let subtitle {
                Text(subtitle.uppercased()).textStyle(.label, Palette.mist)
            }
        }
    }
}

/// Тонкая разделительная линия с затуханием к краям.
struct FadeLine: View {
    var body: some View {
        LinearGradient(
            colors: [Palette.lilacRGB.opacity(0), Palette.lilacRGB.opacity(0.35), Palette.lilacRGB.opacity(0)],
            startPoint: .leading, endPoint: .trailing
        )
        .frame(height: 1)
        .frame(maxWidth: .infinity)
    }
}

/// Подпись раздела: «МОИ ГРУППЫ», «ЗАПИСИ · 3».
struct SectionTitle: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .textStyle(.label, Palette.mist)
            .padding(.horizontal, 24)
            .padding(.vertical, 4)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Круглая стеклянная кнопка со значком: «назад», «+», «изменить».
struct RoundGlyphButton: View {
    let icon: LineIcon
    var color: Color = Palette.ink
    var iconSize: CGFloat = 24
    var strong: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Glyph(kind: icon, color: color, size: iconSize)
                .frame(width: 44, height: 44)
                .glass(Circle(), strong: strong)
        }
        .buttonStyle(.plain)
    }
}
