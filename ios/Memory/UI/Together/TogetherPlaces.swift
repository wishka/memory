import MapKit
import SwiftUI

/// Миссии, клады и вылазки — разделы «Вместе» с картой. То же — в TogetherPlaces.kt.

/// Точка на карте раздела. mine — ваша (подсвечена).
struct TogetherPin: Identifiable, Equatable {
    let id: String
    let at: LatLon
    let title: String
    var emoji: String = "📍"
    var mine: Bool = false
    /// Вы здесь: синяя точка вместо значка.
    var me: Bool = false
}

/// Карта MapKit: значки-эмодзи в кружках и синяя точка «вы здесь».
struct TogetherMap: View {
    let pins: [TogetherPin]
    let center: LatLon
    let me: LatLon?
    var onPin: (String) -> Void = { _ in }
    @State private var position: MapCameraPosition = .automatic

    private var markers: [TogetherPin] {
        guard let me else { return pins }
        return pins + [TogetherPin(id: "me", at: me, title: "", me: true)]
    }

    var body: some View {
        Map(position: $position) {
            ForEach(markers) { pin in
                Annotation(pin.title, coordinate: CLLocationCoordinate2D(latitude: pin.at.lat, longitude: pin.at.lon)) {
                    marker(pin)
                }
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .environment(\.colorScheme, .dark)
        .onAppear { position = Self.camera(center) }
        .onChange(of: center) { _, c in
            withAnimation(.easeOut(duration: 0.3)) { position = Self.camera(c) }
        }
    }

    private static func camera(_ c: LatLon) -> MapCameraPosition {
        .region(MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: c.lat, longitude: c.lon),
            latitudinalMeters: 3000, longitudinalMeters: 3000
        ))
    }

    @ViewBuilder
    private func marker(_ pin: TogetherPin) -> some View {
        if pin.me {
            Circle()
                .fill(Palette.sky)
                .frame(width: 16, height: 16)
                .overlay(Circle().stroke(Palette.ink, lineWidth: 2.5))
        } else {
            Button { onPin(pin.id) } label: {
                Text(pin.emoji)
                    .font(.system(size: 17))
                    .frame(width: 34, height: 34)
                    .background(
                        Circle().fill(pin.mine
                            ? LinearGradient(colors: [Palette.orchid, Palette.ocean], startPoint: .topLeading, endPoint: .bottomTrailing)
                            : LinearGradient(colors: [Palette.night, Palette.void], startPoint: .top, endPoint: .bottom))
                    )
                    .overlay(Circle().stroke(Palette.lilac, lineWidth: 1.5))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(pin.title)
        }
    }
}

/// Карта раздела: квадратная, со скруглением; под ней — «Где я?», пока место не известно.
private struct SectionMap: View {
    let model: AppModel
    let geo: TogetherGeo
    let pins: [TogetherPin]
    var onPin: (String) -> Void = { _ in }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            TogetherMap(pins: pins, center: geo.here ?? model.state.together.anchor, me: geo.here, onPin: onPin)
                .frame(height: 300)
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            if geo.here == nil {
                HStack(spacing: 8) {
                    TogetherHint(text: geo.locating ? t("Ищем, где вы…") : t("Покажите, где вы, — и точки рядом с вами будут ближе"))
                    Spacer(minLength: 0)
                    if !geo.locating {
                        GhostButton(title: t("Где я?")) { geo.locate() }
                    }
                }
            }
        }
    }
}

private let noLocation = "Не удалось узнать, где вы. Включите геолокацию."

// MARK: - 4. Миссии

struct MissionsSection: View {
    let model: AppModel
    let geo: TogetherGeo
    @State private var adding: Mission?
    @State private var selected: String?
    @State private var creating = false

    var body: some View {
        let state = model.state
        let missions = state.together.missions
        let shown = missions.filter { selected == nil || $0.id == selected }
        let pins: [TogetherPin] = shown.flatMap { m in
            m.finds.filter { !state.blocked.contains($0.authorId) }.map { f in TogetherPin(id: "\(m.id):\(f.id)", at: f.at, title: f.note, emoji: m.emoji, mine: f.authorId == state.me.id) }
        }
        VStack(alignment: .leading, spacing: 12) {
            SectionMap(model: model, geo: geo, pins: pins)
            TogetherHint(text: t("Задания по городу. Находки всех участников — на общей карте."))
            if let own = state.unlocks.first(where: { $0.unlock == .ownMission }) {
                TogetherCard {
                    if own.open {
                        TogetherHint(text: t("Вы открыли право придумать миссию для своего района."))
                        GlowButton(title: t("Своя миссия")) { creating = true }
                    } else {
                        TogetherHint(text: "🔒 " + t("Своя миссия района") + ": " + own.unlock.how + " · " + tf("осталось %s", own.left))
                    }
                }
            }
            ForEach(missions) { m in
                missionCard(m, state: state)
            }
        }
        .sheet(item: $adding) { m in
            AddFindSheet(model: model, geo: geo, mission: m) { adding = nil }
        }
        .sheet(isPresented: $creating) {
            OwnMissionSheet(model: model) { creating = false }
        }
    }

    private func missionCard(_ m: Mission, state: UiState) -> some View {
        let mine = m.mineCount(state.me.id)
        let km = state.movedKm
        let locked = m.locked(km)
        let photos = m.finds.filter { !state.blocked.contains($0.authorId) }.compactMap(\.photo)
        return TogetherCard {
            Button { selected = selected == m.id ? nil : m.id } label: {
                HStack(spacing: 12) {
                    EmojiBadge(emoji: m.emoji)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(m.title).textStyle(.title).multilineTextAlignment(.leading)
                        Text(tf("Ваши находки: %s из %s · всего на карте: %s", mine, m.target, m.finds.count))
                            .textStyle(.bodySmall, Palette.mist)
                            .multilineTextAlignment(.leading)
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            TogetherProgress(progress: Double(mine) / Double(max(1, m.target)))
            Text(m.description).textStyle(.bodySmall, Palette.mist).fixedSize(horizontal: false, vertical: true)
            if !photos.isEmpty {
                HStack(spacing: 6) {
                    ForEach(Array(photos.suffix(4))) { photo in
                        MediaTile(attachment: photo, maxPixel: 256) { model.openMedia(photo) }
                            .frame(width: 64, height: 64)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }
            }
            if locked {
                TogetherProgress(progress: km / m.unlockKm)
                TogetherHint(text: "🔒 " + tf("Откроется после %s км пробежек, прогулок и заездов — осталось %s км",
                                             amountNumber(m.unlockKm), amountNumber(tenths(m.unlockKm - km))))
            } else {
                HStack(spacing: 10) {
                    if !m.joined {
                        GhostButton(title: t("Участвовать")) { model.joinMission(m.id) }
                    }
                    GlowButton(title: t("Добавить находку")) { adding = m }
                }
            }
        }
    }
}

/// Своя миссия района: что ищем, что считается находкой, сколько найти.
private struct OwnMissionSheet: View {
    let model: AppModel
    let onClose: () -> Void
    @State private var emoji = "🗺️"
    @State private var title = ""
    @State private var description = ""
    @State private var target = 5
    @State private var error: String?

    var body: some View {
        TogetherSheet(title: t("Своя миссия")) {
            HStack(spacing: 6) {
                ForEach(["🗺️", "🧱", "🌳", "🎨", "🏛️", "🐦"], id: \.self) { e in
                    Chip(text: e, active: emoji == e) { emoji = e }
                }
            }
            TogetherField(placeholder: t("Что ищем? «Старые почтовые ящики»"), text: $title, limit: 60)
                .onChange(of: title) { _, _ in error = nil }
            TogetherField(placeholder: t("Пара слов: что считается находкой"), text: $description, limit: 300, singleLine: false)
            TogetherCaption(text: tf("Сколько найти: %s", target))
            HStack(spacing: 6) {
                ForEach([3, 5, 7, 10], id: \.self) { n in
                    Chip(text: "\(n)", active: target == n) { target = n }
                }
            }
            TogetherError(text: error)
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onClose)
                GlowButton(title: t("Создать"), enabled: !title.trimmingCharacters(in: .whitespaces).isEmpty) {
                    error = model.createMission(emoji: emoji, title: title, description: description, target: target)
                    if error == nil { onClose() }
                }
            }
        }
    }
}

private struct AddFindSheet: View {
    let model: AppModel
    let geo: TogetherGeo
    let mission: Mission
    let onClose: () -> Void
    @State private var note = ""
    @State private var photo: [Attachment] = []
    @State private var error: String?

    var body: some View {
        TogetherSheet(title: "\(mission.emoji) \(mission.title)") {
            TogetherHint(text: t("Сфотографируйте находку — она встанет на карту там, где вы сейчас."))
            TogetherField(placeholder: t("Что это? «Птицы над входом»"), text: $note, limit: 200)
            PendingAttachments(items: photo) { item in
                photo = []
                MediaFiles.delete(item)
            }
            AttachButton(documents: false, size: 40) { picked in
                photo = Array(picked.filter { $0.kind == .photo }.prefix(1))
                for extra in picked where !photo.contains(extra) { MediaFiles.delete(extra) }
            }
            TogetherError(text: error)
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onClose)
                GlowButton(title: t("На карту"), enabled: !note.trimmingCharacters(in: .whitespaces).isEmpty || !photo.isEmpty) {
                    error = nil
                    geo.locate { here in
                        if let here {
                            model.addFind(mission.id, note: note, at: here, photo: photo.first)
                            onClose()
                        } else {
                            error = t(noLocation)
                        }
                    }
                }
            }
        }
    }
}

// MARK: - 5. Клады

struct CachesSection: View {
    let model: AppModel
    let geo: TogetherGeo
    @State private var openId: Int64?
    @State private var hiding = false

    var body: some View {
        let state = model.state
        let caches = state.together.caches.filter { !state.blocked.contains($0.authorId) }
        let here = geo.here
        let pins: [TogetherPin] = caches.map { c in
            TogetherPin(id: "\(c.id)", at: c.at, title: c.title, emoji: c.foundByMe ? "✅" : "💎", mine: c.authorId == state.me.id)
        }
        VStack(alignment: .leading, spacing: 12) {
            SectionMap(model: model, geo: geo, pins: pins) { id in openId = Int64(id) }
            TogetherCard {
                TogetherHint(text: t("Спрячьте маленький контейнер, оставьте подсказку — другие найдут и отметятся. Отметиться можно, только оказавшись рядом."))
                if let right = state.unlocks.first(where: { $0.unlock == .hideCache }), !right.open {
                    TogetherProgress(progress: right.fraction)
                    TogetherHint(text: "🔒 " + t("Прятать клады можно, когда найдёте три чужих") + " · " + tf("осталось %s", right.left))
                } else {
                    GlowButton(title: t("Спрятать клад здесь")) { hiding = true }
                }
            }
            ForEach(caches.togetherSorted { c in here?.distance(to: c.at) ?? 0 }) { c in
                cacheCard(c, here: here)
            }
        }
        .sheet(isPresented: Binding(get: { openId != nil }, set: { if !$0 { openId = nil } })) {
            if let c = model.state.together.caches.first(where: { $0.id == openId }) {
                CacheSheet(model: model, geo: geo, cache: c) { openId = nil }
            }
        }
        .sheet(isPresented: $hiding) {
            HideCacheSheet(model: model, geo: geo) { hiding = false }
        }
    }

    private func cacheCard(_ c: GeoCache, here: LatLon?) -> some View {
        let raw: [String?] = [
            here.map { distanceTitle($0.distance(to: c.at)) },
            tf("сложность %s/5", c.difficulty),
            t(c.size),
            plural(c.logs.count, "находка", "находки", "находок"),
        ]
        let parts = raw.compactMap { $0 }
        return TogetherCard {
            Button { openId = c.id } label: {
                HStack(spacing: 12) {
                    EmojiBadge(emoji: c.foundByMe ? "✅" : "💎")
                    VStack(alignment: .leading, spacing: 2) {
                        Text(c.title).textStyle(.title).multilineTextAlignment(.leading)
                        Text(parts.joined(separator: " · ")).textStyle(.bodySmall, Palette.mist).multilineTextAlignment(.leading)
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }
}

private struct HideCacheSheet: View {
    let model: AppModel
    let geo: TogetherGeo
    let onClose: () -> Void
    @State private var title = ""
    @State private var hint = ""
    @State private var difficulty = 2
    @State private var size = "Маленький"
    @State private var error: String?

    var body: some View {
        TogetherSheet(title: t("Спрятать клад")) {
            TogetherHint(text: t("Клад встанет на карту там, где вы сейчас. Прячьте так, чтобы никому не мешать: не в частных дворах и не в памятниках."))
            TogetherField(placeholder: t("Название: «Звёздная капсула»"), text: $title, limit: 60)
            TogetherField(placeholder: t("Подсказка: где искать"), text: $hint, limit: 300, singleLine: false)
            TogetherCaption(text: t("Сложность"))
            HStack(spacing: 6) {
                ForEach(1...5, id: \.self) { d in
                    Chip(text: "\(d)", active: difficulty == d) { difficulty = d }
                }
            }
            TogetherCaption(text: t("Размер"))
            HStack(spacing: 6) {
                ForEach(["Микро", "Маленький", "Средний"], id: \.self) { s in
                    Chip(text: t(s), active: size == s) { size = s }
                }
            }
            TogetherError(text: error)
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onClose)
                GlowButton(title: t("Спрятать"), enabled: !title.trimmingCharacters(in: .whitespaces).isEmpty && !hint.trimmingCharacters(in: .whitespaces).isEmpty) {
                    error = nil
                    geo.locate { here in
                        if let here {
                            if model.hideCache(title: title, hint: hint, difficulty: difficulty, size: size, at: here) { onClose() }
                        } else {
                            error = t(noLocation)
                        }
                    }
                }
            }
        }
    }
}

private struct CacheSheet: View {
    let model: AppModel
    let geo: TogetherGeo
    let cache: GeoCache
    let onClose: () -> Void
    @State private var showHint = false
    @State private var note = ""
    @State private var error: String?

    var body: some View {
        let state = model.state
        let c = cache
        let author = personOf(state, c.authorId)
        let distance = geo.here.map { $0.distance(to: c.at) }
        let rawFacts: [String?] = [
            distance.map { tf("До клада: %s", distanceTitle($0)) },
            tf("Сложность %s/5", c.difficulty),
            t(c.size),
        ]
        let facts = rawFacts.compactMap { $0 }
        TogetherSheet(title: "💎 \(c.title)") {
            PersonLine(user: author, subtitle: tf("спрятал(а) %s", agoTitle(minutesSince(c.createdAt, 0)))) {
                onClose()
                model.openPerson(author.id)
            }
            Text(facts.joined(separator: " · ")).textStyle(.labelLarge, Palette.lilac)
            if showHint || c.authorId == state.me.id {
                Text(c.hint).textStyle(.body).fixedSize(horizontal: false, vertical: true)
            } else {
                GhostButton(title: t("Показать подсказку")) { showHint = true }
            }
            if c.authorId == state.me.id {
                TogetherHint(text: t("Это ваш клад. Когда его найдут, придёт уведомление."))
            } else if c.foundByMe {
                Text(t("Вы нашли этот клад ✓")).textStyle(.title, Palette.sky)
            } else {
                TogetherField(placeholder: t("Запись в журнале: «Нашёл, оставил открытку»"), text: $note, limit: 300)
                TogetherError(text: error)
                GlowButton(title: t("Нашёл!")) {
                    error = nil
                    geo.locate { here in
                        guard let here else {
                            error = t(noLocation)
                            return
                        }
                        if !model.findCache(c.id, text: note, here: here) {
                            error = tf("Вы в %s от клада — подойдите ближе (до %s)", distanceTitle(here.distance(to: c.at)), distanceTitle(cacheRadiusM))
                        }
                    }
                }
            }
            if !c.logs.isEmpty {
                TogetherCaption(text: t("Журнал находок"))
            }
            ForEach(Array(c.logs.reversed())) { log in
                PersonLine(user: personOf(state, log.authorId), subtitle: log.text)
            }
            GhostButton(title: t("Закрыть"), action: onClose)
        }
    }
}

// MARK: - 6. Вылазки

private let outingFormatterRu: DateFormatter = {
    let f = DateFormatter()
    f.locale = Locale(identifier: "ru_RU")
    f.dateFormat = "EEEE, d MMMM · HH:mm"
    return f
}()

private let outingFormatterEn: DateFormatter = {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_US")
    f.dateFormat = "EEEE, MMMM d · HH:mm"
    return f
}()

/// «суббота, 17 октября · 11:00».
func outingTime(_ date: Date) -> String {
    (L10n.shared.english ? outingFormatterEn : outingFormatterRu).string(from: date)
}

struct OutingsSection: View {
    let model: AppModel
    let geo: TogetherGeo
    @State private var creating = false

    var body: some View {
        let state = model.state
        let outings = state.together.outings.filter { !state.blocked.contains($0.organizerId) }
        let pins: [TogetherPin] = outings.compactMap { o in
            o.at.map { TogetherPin(id: "\(o.id)", at: $0, title: o.title, emoji: o.emoji, mine: o.organizerId == state.me.id) }
        }
        VStack(alignment: .leading, spacing: 12) {
            SectionMap(model: model, geo: geo, pins: pins)
            TogetherCard {
                TogetherHint(text: tf("Малой группой — до %s человек: поход за грибами, фотопрогулка, велопробег.", Outing.maxPeople))
                GlowButton(title: t("Собрать вылазку")) { creating = true }
            }
            ForEach(outings) { o in
                OutingCard(model: model, outing: o)
            }
        }
        .sheet(isPresented: $creating) {
            CreateOutingSheet(model: model, geo: geo) { creating = false }
        }
    }
}

private struct CreateOutingSheet: View {
    let model: AppModel
    let geo: TogetherGeo
    let onClose: () -> Void
    @State private var emoji = "🥾"
    @State private var title = ""
    @State private var description = ""
    @State private var place = ""
    @State private var dayOffset = 1
    @State private var hour = 11
    @State private var capacity = 6
    @State private var error: String?

    private var days: [(Int, String)] {
        [(0, t("Сегодня")), (1, t("Завтра")), (2, t("Послезавтра")), (6, t("Через неделю"))]
    }

    private var capacityValue: Binding<Double> {
        Binding(get: { Double(capacity) }, set: { capacity = Int($0.rounded()) })
    }

    var body: some View {
        TogetherSheet(title: t("Собрать вылазку")) {
            FlowLayout(spacing: 6) {
                ForEach(["🥾", "🍄", "📷", "🚴", "🏕️", "🛶", "🌌", "🎨", "☕"], id: \.self) { e in
                    Chip(text: e, active: emoji == e) { emoji = e }
                }
            }
            TogetherField(placeholder: t("Что делаем? «Фотопрогулка по крышам»"), text: $title, limit: 60)
            TogetherField(placeholder: t("Пара слов: что взять, кому подойдёт"), text: $description, limit: 500, singleLine: false)
            TogetherField(placeholder: t("Где встречаемся"), text: $place, limit: 80)
            TogetherCaption(text: t("Когда"))
            FlowLayout(spacing: 6) {
                ForEach(days.indices, id: \.self) { i in
                    Chip(text: days[i].1, active: dayOffset == days[i].0) { dayOffset = days[i].0 }
                }
            }
            FlowLayout(spacing: 6) {
                ForEach([8, 10, 12, 15, 18, 20], id: \.self) { h in
                    Chip(text: "\(h):00", active: hour == h) { hour = h }
                }
            }
            TogetherCaption(text: tf("Сколько человек: %s", capacity))
            Slider(value: capacityValue, in: 2...Double(Outing.maxPeople), step: 1).tint(Palette.lilac)
            TogetherError(text: error)
            HStack(spacing: 10) {
                GhostButton(title: t("Отмена"), action: onClose)
                GlowButton(title: t("Собрать"), enabled: !title.trimmingCharacters(in: .whitespaces).isEmpty && !place.trimmingCharacters(in: .whitespaces).isEmpty) {
                    let calendar = Calendar.current
                    let day = calendar.date(byAdding: .day, value: dayOffset, to: Date()) ?? Date()
                    let start = calendar.date(bySettingHour: hour, minute: 0, second: 0, of: day) ?? day
                    if model.createOuting(emoji: emoji, title: title, description: description, place: place,
                                          startsAt: start, capacity: capacity, at: geo.here) {
                        onClose()
                    } else {
                        error = t("Укажите, что делаем и где встречаемся")
                    }
                }
            }
        }
    }
}

private struct OutingCard: View {
    let model: AppModel
    let outing: Outing
    @State private var sharing: SharePayload?

    var body: some View {
        let state = model.state
        let o = outing
        let me = state.me.id
        let organizer = personOf(state, o.organizerId)
        let joined = o.participants.contains(me)
        TogetherCard {
            HStack(spacing: 12) {
                EmojiBadge(emoji: o.emoji)
                VStack(alignment: .leading, spacing: 2) {
                    Text(o.title).textStyle(.title)
                    Text(outingTime(o.startsAt)).textStyle(.bodySmall, Palette.lilac)
                }
                Spacer(minLength: 0)
            }
            Text("📍 \(o.place)").textStyle(.bodySmall)
            if !o.description.isEmpty {
                Text(o.description).textStyle(.bodySmall, Palette.mist).fixedSize(horizontal: false, vertical: true)
            }
            HStack(spacing: 16) {
                HStack(spacing: -8) {
                    ForEach(Array(o.participants.prefix(8)), id: \.self) { id in
                        Avatar(user: personOf(state, id), size: 30)
                    }
                }
                Text(tf("%s из %s · собирает %s", o.participants.count, o.capacity, organizer.name))
                    .textStyle(.bodySmall, Palette.mist)
            }
            FlowLayout(spacing: 10) {
                if o.organizerId == me {
                    GhostButton(title: t("Отменить вылазку")) { model.cancelOuting(o.id) }
                } else if joined {
                    GhostButton(title: t("Вы идёте ✓")) { model.toggleOuting(o.id) }
                } else if o.full {
                    GhostButton(title: t("Мест нет")) {}
                } else {
                    GlowButton(title: t("Иду")) { model.toggleOuting(o.id) }
                }
                if o.organizerId != me {
                    GhostButton(title: t("Написать")) { model.writeTo(o.organizerId) }
                }
                if !o.full && (joined || o.organizerId == me) {
                    GhostButton(title: t("Позвать")) {
                        sharing = shareInvite(model, .outing, targetId: "\(o.id)", title: o.title)
                    }
                }
            }
        }
        .shareSheet($sharing)
    }
}
