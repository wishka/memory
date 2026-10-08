import SwiftUI

/// Число и подпись под ним: «128 ПОДПИСЧИКИ».
struct Stat: View {
    let value: Int
    let label: String

    var body: some View {
        VStack(spacing: 2) {
            Text(compact(value)).textStyle(.headline)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(label.uppercased()).textStyle(.label, Palette.mist)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
    }
}

/// 1284 → «1,3 тыс.»: длинные числа не распирают колонку.
func compact(_ n: Int) -> String {
    let ru = Locale(identifier: "ru_RU")
    if n >= 1_000_000 { return String(format: "%.1f млн", locale: ru, Double(n) / 1_000_000) }
    if n >= 10_000 { return "\(n / 1000) тыс." }
    if n >= 1_000 { return String(format: "%.1f тыс.", locale: ru, Double(n) / 1000) }
    return String(n)
}

/// Строка сведения: значок, подпись и значение. Пустое значение не показывается.
struct InfoRow: View {
    let icon: LineIcon
    let label: String
    let value: String

    var body: some View {
        if !value.trimmingCharacters(in: .whitespaces).isEmpty {
            HStack(spacing: 12) {
                Glyph(kind: icon, color: Palette.lilac, size: 20)
                Text(label).textStyle(.bodySmall, Palette.mist)
                Spacer(minLength: 8)
                Text(value).textStyle(.body)
                    .multilineTextAlignment(.trailing)
            }
            .padding(.vertical, 6)
        }
    }
}

/// Сведения о человеке: город, день рождения, сайт, «в Memory с».
struct UserInfo: View {
    let user: User

    var body: some View {
        VStack(spacing: 0) {
            InfoRow(icon: .pin, label: "Город", value: user.city)
            InfoRow(icon: .gift, label: "День рождения", value: user.birthday)
            InfoRow(icon: .link, label: "Сайт", value: user.website)
            InfoRow(icon: .calendar, label: "В Memory с", value: user.joined)
        }
    }
}

/// Интересы метками-капсулами.
struct InterestChips: View {
    let interests: [String]

    var body: some View {
        if !interests.isEmpty {
            FlowLayout(spacing: 8) {
                ForEach(interests, id: \.self) { Chip(text: $0) }
            }
        }
    }
}
