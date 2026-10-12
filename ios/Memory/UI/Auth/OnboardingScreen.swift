import SwiftUI

/// Сколько любимого выбрать, чтобы полка и подборка ожили.
let onboardingPicks = 3

/// Знакомство до регистрации — как обучение в игре, а не серая форма:
/// как здесь всё устроено → выберите три любимых фильма или книги → вот
/// ваша полка и первый совет от человека с похожим вкусом → «Сохранить
/// мою полку» ведёт к регистрации. То же — в OnboardingScreen.kt.
struct OnboardingScreen: View {
    let onDone: ([CatalogTitle]) -> Void
    let onSkip: () -> Void

    @State private var step = 0
    @State private var picked: [String] = []

    private var picks: [CatalogTitle] {
        picked.compactMap { key in Catalog.all.first { $0.id == key } }
    }

    var body: some View {
        ZStack {
            SplashSkyView()
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Spacer().frame(height: 20)
                    Text("MEMORY").textStyle(.display).frame(maxWidth: .infinity)
                    switch step {
                    case 0: intro
                    case 1: choose
                    default: result
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 24)
            }
        }
    }

    // MARK: Шаг 1 — как всё устроено

    @ViewBuilder
    private var intro: some View {
        centered(t("Приложение, которое помнит, что вы любите"))
        ForEach(Array([
            ("🎬", t("Отмечаете в одно касание: «посмотрел фильм», «пробежал 5 км», «читаю книгу».")),
            ("📚", t("Полка собирается сама — ничего не нужно заполнять вручную.")),
            ("✨", t("Советы — от людей с похожим вкусом, а не «самое популярное».")),
            ("🔓", t("Чем больше делаете, тем больше открывается: свои клады, миссии, цели.")),
        ].enumerated()), id: \.offset) { _, item in
            GlassCard {
                HStack(spacing: 12) {
                    Text(item.0).font(.system(size: 28))
                    Text(item.1).textStyle(.body).fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 0)
                }
                .padding(14)
            }
        }
        GlowButton(title: t("Начать — выбрать любимое"), fullWidth: true) { step = 1 }
        GhostButton(title: t("У меня уже есть аккаунт"), action: onSkip).frame(maxWidth: .infinity)
    }

    // MARK: Шаг 2 — выбрать любимое

    @ViewBuilder
    private var choose: some View {
        centered(tf("Выберите %s любимых фильма или книги", onboardingPicks))
        Text(tf("Выбрано: %s", picks.count)).textStyle(.bodySmall, Palette.lilac).frame(maxWidth: .infinity)
        ForEach(0..<2, id: \.self) { i in
            Text((i == 0 ? t("Кино") : t("Книги")).uppercased()).textStyle(.label, Palette.mist)
            FlowLayout(spacing: 8) {
                ForEach(i == 0 ? Catalog.films : Catalog.books) { c in
                    let on = picked.contains(c.id)
                    Button {
                        if on { picked.removeAll { $0 == c.id } } else { picked.append(c.id) }
                    } label: {
                        HStack(spacing: 6) {
                            Text(c.emoji).font(.system(size: 16))
                            Text((on ? "✓ " : "") + c.title).textStyle(.labelLarge, on ? Palette.ink : Palette.mist)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .glass(Capsule(), strong: on)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        GlowButton(title: picks.count >= onboardingPicks ? t("Посмотреть мою полку") : tf("Ещё %s", onboardingPicks - picks.count),
                   enabled: picks.count >= onboardingPicks, fullWidth: true) { step = 2 }
        GhostButton(title: t("Назад")) { step = 0 }.frame(maxWidth: .infinity)
    }

    // MARK: Шаг 3 — ваша полка и первые советы

    @ViewBuilder
    private var result: some View {
        let picks = self.picks
        let rated = picks.map { Rated(kind: $0.kind, subject: $0.title, rating: 5) }
        let keys = Set(rated.map(\.key))
        let recs = recommend(rated, exclude: keys, friends: DemoTaste.ratings, kind: "film", limit: 1)
            + recommend(rated, exclude: keys, friends: DemoTaste.ratings, kind: "book", limit: 1)
        centered(t("Ваша полка"))
        GlassCard {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(picks) { p in
                    Text("\(p.emoji)  \(p.title) · ★★★★★").textStyle(.body)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        if !recs.isEmpty {
            Text(t("ПЕРВЫЕ СОВЕТЫ")).textStyle(.label, Palette.mist)
            ForEach(recs, id: \.key) { r in
                let who = Demo.person(r.personId)?.firstName ?? ""
                GlassCard {
                    HStack(spacing: 12) {
                        Text(r.emoji).font(.system(size: 28))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(r.subject).textStyle(.title)
                            Text(r.match.common > 0
                                 ? tf("%s тоже любит то, что выбрали вы, и ставит этому %s★", who, r.theirRating)
                                 : tf("%s ставит %s★", who, r.theirRating))
                                .textStyle(.bodySmall, Palette.mist)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(14)
                }
            }
        }
        Text(t("Аккаунт хранится на этом телефоне: ни почты, ни номера не нужно."))
            .textStyle(.bodySmall, Palette.mist)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
        GlowButton(title: t("Сохранить мою полку"), fullWidth: true) { onDone(picks) }
        GhostButton(title: t("Назад")) { step = 1 }.frame(maxWidth: .infinity)
    }

    private func centered(_ text: String) -> some View {
        Text(text)
            .textStyle(.headline)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity)
    }
}
