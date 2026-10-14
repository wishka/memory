import SwiftUI

// Memory+ на экране: значок PLUS, карточка в профиле, экран тарифов и
// шторка «Это в Memory+». То же — в PremiumScreen.kt.

/// Значок «PLUS» у премиум-вещей: сиренево-голубая плашка.
struct PlusBadge: View {
    var business: Bool = false

    init(business: Bool = false) {
        self.business = business
    }

    /// Плашка для премиум-функции: «PLUS» или «BIZ».
    init(_ feature: PremiumFeature) {
        self.business = feature.tier == .business
    }

    var body: some View {
        Text(business ? "BIZ" : "PLUS")
            .font(.custom(Fonts.interSemiBold, size: 9))
            .foregroundStyle(Palette.void)
            .padding(.horizontal, 6)
            .padding(.vertical, 1)
            .background(Capsule().fill(LinearGradient(colors: [Palette.lilac, Palette.sky], startPoint: .leading, endPoint: .trailing)))
    }
}

/// Дата окончания: «16 октября».
private func premiumDateTitle(_ date: Date) -> String {
    let f = DateFormatter()
    let english = L10n.shared.english
    f.locale = L10n.shared.locale
    f.dateFormat = english ? "MMMM d" : "d MMMM"
    return f.string(from: date)
}

/// «3 дня» — сколько длится пробный период.
private var trialDaysTitle: String { plural(Premium.trialDays, "день", "дня", "дней") }

/// Состояние подписки одной строкой: «Memory+ · пробный, ещё 3 дня».
func subscriptionStatus(_ sub: MemorySubscription, _ plan: PlanTier, now: Date) -> String {
    if plan == .free && sub.tier != .free && sub.trial { return t("Пробный период закончился") }
    if plan == .free { return t("Бесплатный тариф") }
    if sub.trial { return tf("%s · пробный, ещё %s", plan.title, plural(sub.daysLeft(now), "день", "дня", "дней")) }
    if sub.autoRenew { return tf("%s · продлится %s", plan.title, premiumDateTitle(sub.until)) }
    return tf("%s · до %s, без продления", plan.title, premiumDateTitle(sub.until))
}

/// Кружок со звёздочкой Memory+.
private struct PremiumStar: View {
    var size: CGFloat = 46

    var body: some View {
        Text("✦")
            .font(.system(size: size * 0.48))
            .foregroundStyle(Palette.ink)
            .frame(width: size, height: size)
            .background(Circle().fill(LinearGradient(colors: [Palette.orchid, Palette.sky], startPoint: .topLeading, endPoint: .bottomTrailing)))
    }
}

/// Карточка в профиле: какой тариф и вход на экран Memory+.
struct PremiumProfileCard: View {
    let model: AppModel

    var body: some View {
        let state = model.state
        Button { model.openPremium() } label: {
            GlassCard {
                HStack(spacing: 14) {
                    PremiumStar()
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 8) {
                            Text("Memory+").textStyle(.title)
                            if state.plan != .free {
                                PlusBadge(business: state.plan == .business)
                            }
                        }
                        Text(state.plan == .free && !state.subscription.trialUsed
                             ? tf("Попробуйте %s бесплатно", trialDaysTitle)
                             : subscriptionStatus(state.subscription, state.plan, now: Date()))
                            .textStyle(.bodySmall, Palette.mist)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Text(state.plan == .free ? t("Тарифы") : t("Управлять")).textStyle(.labelLarge, Palette.lilac)
                }
                .padding(16)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .cardPadding()
    }
}

/// Что подтверждают: оформить или сменить тариф, отменить продление.
private enum PremiumConfirm: Identifiable {
    case subscribe(PlanTier)
    case cancel

    var id: String {
        switch self {
        case .subscribe(let tier): return tier.id
        case .cancel: return "cancel"
        }
    }
}

/// Экран «Memory+» в профиле: текущий тариф, пробный период, выбор тарифа
/// и срока, смена тарифа, отмена и возобновление продления.
struct PremiumScreen: View {
    let model: AppModel
    @State private var period: BillingPeriod
    @State private var confirm: PremiumConfirm?
    @State private var notice: String?

    init(model: AppModel) {
        self.model = model
        let sub = model.state.subscription
        _period = State(initialValue: sub.tier != .free ? sub.period : .year)
    }

    var body: some View {
        let state = model.state
        let sub = state.subscription
        let now = Date()
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 14) {
                    RoundGlyphButton(icon: .back) { model.closeProfileSection() }
                    ScreenTitle(title: "Memory+")
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 4)

                // Текущий тариф
                GlassCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(t("Ваш тариф").uppercased()).textStyle(.label, Palette.mist)
                        Text(state.plan.title).textStyle(.headline)
                        Text(subscriptionStatus(sub, state.plan, now: now))
                            .textStyle(.bodySmall, Palette.mist)
                            .fixedSize(horizontal: false, vertical: true)
                        if state.plan != .free && !sub.trial {
                            HStack(spacing: 10) {
                                if sub.autoRenew {
                                    GhostButton(title: t("Отменить продление")) { confirm = .cancel }
                                } else {
                                    GlowButton(title: t("Возобновить")) { model.resumeRenewal() }
                                }
                            }
                        }
                        Text(t("Оплата пока демонстрационная: деньги не списываются. Настоящая оплата — через Google Play и App Store."))
                            .textStyle(.bodySmall, Palette.mist)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(18)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .cardPadding()

                // Срок
                HStack(spacing: 10) {
                    ForEach(BillingPeriod.allCases) { p in
                        let active = p == period
                        Button { period = p } label: {
                            Text(p == .year ? yearTitle(p) : p.title)
                                .textStyle(.labelLarge, active ? Palette.ink : Palette.mist)
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .glass(Capsule(), strong: active)
                                .overlay(Capsule().stroke(active ? Palette.lilac : Palette.line, lineWidth: active ? 1.5 : 0))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)

                ForEach([PlanTier.plus, PlanTier.business]) { tier in
                    PlanCard(
                        tier: tier,
                        period: period,
                        current: state.plan == tier,
                        sameTerms: state.plan == tier && sub.period == period && !sub.trial,
                        trialAvailable: !sub.trialUsed,
                        onTrial: {
                            let error = model.startTrial(tier)
                            notice = error ?? tf("%s: пробный период начался", tier.title)
                        },
                        onSubscribe: { confirm = .subscribe(tier) }
                    )
                }

                // Бесплатный тариф
                GlassCard {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(PlanTier.free.title).textStyle(.title)
                        Text(t("Всегда бесплатно: лента, истории, PRISM, чаты, голосовые, друзья, группы, покупки в витринах, приватность и безопасность."))
                            .textStyle(.bodySmall, Palette.mist)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(18)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .cardPadding()
            }
            .padding(.top, 12)
            .padding(.bottom, tabBarSpace)
        }
        .toast($notice)
        // Подтверждение: оформить или сменить тариф, отменить продление
        .alert(confirmTitle(state), isPresented: confirmShown, presenting: confirm) { what in
            switch what {
            case .cancel:
                Button(t("Отменить продление"), role: .destructive) {
                    model.cancelRenewal()
                    confirm = nil
                }
            case .subscribe(let tier):
                Button(t("Оформить")) {
                    model.subscribe(tier, period)
                    notice = tf("Готово: %s", tier.title)
                    confirm = nil
                }
            }
            Button(t("Не сейчас"), role: .cancel) { confirm = nil }
        } message: { what in
            switch what {
            case .cancel:
                Text(tf("%s будет работать до %s, потом тариф станет бесплатным. Ничего из созданного не пропадёт.",
                        state.plan.title, premiumDateTitle(sub.until)))
            case .subscribe(let tier):
                Text(tf("%s — %s. Новый тариф действует сразу, срок считается заново. Оплата демонстрационная.",
                        tier.title, Premium.priceTitle(tier, period)))
            }
        }
    }

    /// «Год · выгоднее до 38%». Процент — отдельно: tf подставляет только %s.
    private func yearTitle(_ p: BillingPeriod) -> String {
        tf("%s · выгоднее до %s%", p.title, Premium.yearSaving(.plus))
    }

    private var confirmShown: Binding<Bool> {
        Binding(get: { confirm != nil }, set: { if !$0 { confirm = nil } })
    }

    private func confirmTitle(_ state: UiState) -> String {
        switch confirm {
        case .none: return ""
        case .some(.cancel): return t("Отменить продление?")
        case .some(.subscribe(let tier)):
            return state.plan == .free ? tf("Оформить %s?", tier.title) : tf("Перейти на %s?", tier.title)
        }
    }
}

/// Карточка тарифа: цена, что входит, «Попробовать 3 дня», «Оформить».
private struct PlanCard: View {
    let tier: PlanTier
    let period: BillingPeriod
    let current: Bool
    let sameTerms: Bool
    let trialAvailable: Bool
    let onTrial: () -> Void
    let onSubscribe: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(tier.title).textStyle(.headline)
                    Spacer()
                    if current {
                        Text(t("Сейчас")).textStyle(.labelLarge, Palette.lilac)
                    }
                }
                Text(Premium.priceTitle(tier, period)).textStyle(.headline, Palette.lilac)
                if period == .year {
                    Text(tf("Это %s в месяц — выгоднее на %s%", priceTitle(Int64(Premium.price(tier, period) / 12)), Premium.yearSaving(tier)))
                        .textStyle(.labelLarge, Palette.mist)
                }
                if tier == .business {
                    Text(t("Всё из Memory+, а ещё:")).textStyle(.bodySmall)
                }
                ForEach(Premium.features(tier)) { f in
                    HStack(alignment: .top, spacing: 10) {
                        Glyph(kind: .check, color: Palette.lilac, size: 18)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(f.title).textStyle(.title)
                            Text(f.what)
                                .textStyle(.bodySmall, Palette.mist)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                Spacer().frame(height: 2)
                if trialAvailable {
                    GhostButton(title: tf("Попробовать %s бесплатно", trialDaysTitle), action: onTrial)
                        .frame(maxWidth: .infinity)
                }
                if !sameTerms {
                    GlowButton(
                        title: current ? tf("Перейти на %s", period.title.lowercased()) : tf("Оформить за %s", Premium.priceTitle(tier, period)),
                        fullWidth: true,
                        action: onSubscribe
                    )
                }
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .overlay(shape.stroke(current ? Palette.lilac : Palette.line, lineWidth: current ? 1.5 : 0))
        .cardPadding()
    }
}

/// Шторка «Это в Memory+»: что за функция, в каком тарифе, пробный период
/// и «Все тарифы».
struct PaywallSheet: View {
    let model: AppModel
    let feature: PremiumFeature
    /// Подсказка после пробного периода — у того, кто показывает шторку:
    /// сама шторка при этом закрывается.
    let onNotice: (String) -> Void

    var body: some View {
        CenterDialog(onDismiss: { model.closePaywall() }) {
            VStack(spacing: 12) {
                PremiumStar(size: 56)
                PlusBadge(feature)
                Text(feature.title).textStyle(.title).multilineTextAlignment(.center)
                Text(feature.what)
                    .textStyle(.bodySmall, Palette.mist)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                Text(tf("Входит в %s — от %s.", feature.tier.title, Premium.priceTitle(feature.tier, .month)))
                    .textStyle(.bodySmall)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                if !model.state.subscription.trialUsed {
                    GlowButton(title: tf("Попробовать %s бесплатно", trialDaysTitle), fullWidth: true) {
                        let tier = feature.tier
                        let error = model.startTrial(tier)
                        onNotice(error ?? tf("%s: пробный период начался", tier.title))
                    }
                }
                GhostButton(title: t("Все тарифы")) { model.openPremium() }
                Button { model.closePaywall() } label: {
                    Text(t("Не сейчас")).textStyle(.labelLarge, Palette.mist).padding(.vertical, 6)
                }
                .buttonStyle(.plain)
            }
            .frame(maxWidth: .infinity)
        }
    }
}

/// Шторка «Это в Memory+» поверх экрана, где бы ни нажали. Ставится на
/// главный экран и на то, что открыто поверх него (камера PRISM).
private struct PaywallHost: ViewModifier {
    let model: AppModel
    @State private var notice: String?

    func body(content: Content) -> some View {
        content
            .overlay {
                if let feature = model.state.paywall {
                    PaywallSheet(model: model, feature: feature) { notice = $0 }
                        .transition(.opacity)
                }
            }
            .animation(.easeOut(duration: 0.2), value: model.state.paywall)
            .toast($notice)
    }
}

extension View {
    func paywall(_ model: AppModel) -> some View { modifier(PaywallHost(model: model)) }
}
