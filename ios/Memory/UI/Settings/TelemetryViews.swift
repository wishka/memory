import SwiftUI

/// Переключатель с заголовком и пояснением — одним элементом для VoiceOver.
private struct ConsentSwitch: View {
    let title: String
    let hint: String
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).textStyle(.body)
                Text(hint)
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .tint(Palette.orchid)
    }
}

private var crashHint: String { t("Что сломалось и где в коде — без ваших записей, имён и переписки.") }
private var usageHint: String { t("Раз в день — сколько раз открывали Memory, публиковали записи, искали. Без текстов и без того, кто именно.") }

/// «Приватность → Сбои и статистика»: два отдельных согласия и последний
/// сбой, который можно отправить вручную. То же — TelemetryViews.kt.
struct TelemetryCard: View {
    @State private var crashes = TelemetrySetup.crashesOn
    @State private var usage = TelemetrySetup.analyticsOn
    @State private var last = TelemetrySetup.lastCrash()
    @State private var message: String?

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                Text(t("Сбои и статистика").uppercased())
                    .textStyle(.label, Palette.mist)
                    .accessibilityAddTraits(.isHeader)
                ConsentSwitch(title: t("Отчёты о сбоях"), hint: crashHint, isOn: $crashes)
                FadeLine()
                ConsentSwitch(title: t("Статистика использования"), hint: usageHint, isOn: $usage)
                if !TelemetrySetup.hasKey {
                    Text(t("В этой сборке отправка выключена: всё остаётся на телефоне."))
                        .textStyle(.bodySmall, Palette.mist)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let report = last {
                    FadeLine()
                    Text(tf("Последний сбой — %s: %s", agoTitle(minutesSince(report.at, 0)), report.title))
                        .textStyle(.bodySmall)
                        .fixedSize(horizontal: false, vertical: true)
                    HStack(spacing: 10) {
                        if TelemetrySetup.hasKey {
                            GhostButton(title: t("Отправить отчёт")) {
                                let sent = TelemetrySetup.sendLastCrash()
                                message = sent ? t("Спасибо! Отчёт отправлен.") : t("Не получилось отправить.")
                                if sent { last = nil }
                            }
                        }
                        GhostButton(title: t("Удалить")) {
                            TelemetrySetup.clearCrash()
                            last = nil
                        }
                    }
                }
                if let message {
                    Text(message).textStyle(.bodySmall, Palette.lilac)
                }
                Text(t("Отчёты обрабатывает сервис Sentry (Functional Software, Inc., США) — подробнее в политике конфиденциальности."))
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(16)
        }
        .cardPadding()
        .onChange(of: crashes) { _, value in TelemetrySetup.update(crashes: value, analytics: usage) }
        .onChange(of: usage) { _, value in TelemetrySetup.update(crashes: crashes, analytics: value) }
    }
}

/// Один раз после входа — спросить, можно ли присылать отчёты о сбоях и
/// статистику. По умолчанию — нет: включает только сам человек.
struct TelemetryConsentDialog: View {
    let onDone: () -> Void
    @State private var crashes = false
    @State private var usage = false

    var body: some View {
        CenterDialog(onDismiss: {}) {
            Text(t("Помочь сделать Memory лучше?"))
                .textStyle(.title)
                .accessibilityAddTraits(.isHeader)
            ConsentSwitch(title: t("Отчёты о сбоях"), hint: crashHint, isOn: $crashes)
            ConsentSwitch(title: t("Статистика использования"), hint: usageHint, isOn: $usage)
            Text(t("Изменить можно в любой момент: Настройки → Приватность."))
                .textStyle(.bodySmall, Palette.mist)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 10) {
                GhostButton(title: t("Не сейчас")) {
                    TelemetrySetup.update(crashes: false, analytics: false)
                    onDone()
                }
                Spacer()
                GlowButton(title: t("Сохранить")) {
                    TelemetrySetup.update(crashes: crashes, analytics: usage)
                    onDone()
                }
            }
        }
    }
}
