import Foundation

/// Люди с сервера: кого встретили в ленте, в переписках, в поиске. Демо-люди
/// (Demo.people) — отдельно; Demo.person ищет и здесь.
///
/// Номера на сервере — с 100 000, так что с демо-людьми (2–6) не путаются.
/// Свой номер на сервере (selfId) телефон знает после входа: всё, что пришло
/// с ним, — «я» (Demo.me.id), а на сервер «я» уходит его номером.
///
/// Сохраняется вместе с состоянием (Snapshot.people), чтобы после
/// перезапуска авторы записей не превратились в «Неизвестного». То же — в Directory.kt.
enum Directory {
    private static let lock = NSLock()
    private static var _selfId = 0
    private static var people: [Int: User] = [:]
    /// Открытые ключи X25519 (сквозное шифрование) — base64.
    private static var keys: [Int: String] = [:]
    private static var _version = 0

    private static func locked<R>(_ body: () -> R) -> R {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }

    /// Свой номер на сервере; 0 — не вошли (демо).
    static var selfId: Int {
        get { locked { _selfId } }
        set { locked { _selfId = newValue } }
    }

    /// Сколько раз менялся справочник — экранам, чтобы перерисоваться.
    static var version: Int { locked { _version } }

    static func get(_ id: Int) -> User? { locked { people[id] } }

    static func all() -> [User] {
        let list: [User] = locked { Array(people.values) }
        return list.sorted { $0.name.lowercased() < $1.name.lowercased() }
    }

    /// Открытый ключ человека; nil — ещё не прислал (секретная переписка недоступна).
    static func key(_ id: Int) -> [UInt8]? {
        guard let text = locked({ keys[id] }), let data = Data(base64Encoded: text) else { return nil }
        return data.count == 32 ? Array(data) : nil
    }

    /// Номер с сервера → номер в приложении: свой — это Demo.me.id.
    static func local(_ id: Int) -> Int {
        let me = selfId
        return me != 0 && id == me ? Demo.me.id : id
    }

    /// Номер в приложении → номер на сервере.
    static func remote(_ id: Int) -> Int {
        let me = selfId
        return me != 0 && id == Demo.me.id ? me : id
    }

    /// Человек с сервера: GET /v1/users/{id}, поиск, authorInfo в записи или сообщении.
    @discardableResult
    static func remember(_ o: JSONObject?) -> User? {
        guard let o else { return nil }
        let id = Json.int(o, "id")
        if id <= 0 || local(id) == Demo.me.id { return nil }
        let key = Json.string(o, "publicKey")
        return locked { () -> User in
            let old = people[id]
            let user = merged(o, id: id, old: old)
            let keyChanged = !key.isEmpty && keys[id] != key
            let changed = old != user || keyChanged
            people[id] = user
            if !key.isEmpty { keys[id] = key }
            if changed { _version += 1 }
            return user
        }
    }

    /// Новые поля поверх прежних: чего нет в ответе — остаётся как было.
    private static func merged(_ o: JSONObject, id: Int, old: User?) -> User {
        let name = Json.string(o, "name")
        let handle = Json.string(o, "handle")
        let login = Json.string(o, "login")
        let oldHandle: String = old?.handle ?? ""
        let fallbackName: String = old?.name ?? handle
        let fallbackHandle: String = login.isEmpty ? oldHandle : login
        var u = User(id: id, name: name.isEmpty ? fallbackName : name, handle: handle.isEmpty ? fallbackHandle : handle)
        u.bio = o["bio"] != nil ? Json.string(o, "bio") : (old?.bio ?? "")
        u.hue = o["hue"] != nil ? Json.double(o, "hue", 0.5) : (old?.hue ?? 0.5)
        u.city = o["city"] != nil ? Json.string(o, "city") : (old?.city ?? "")
        return u
    }

    static func json() -> [JSONObject] {
        let pairs: [(User, String)] = locked { people.values.map { ($0, keys[$0.id] ?? "") } }
        return pairs.map { pair in
            let u = pair.0
            var o: JSONObject = [:]
            o["id"] = u.id
            o["name"] = u.name
            o["handle"] = u.handle
            o["bio"] = u.bio
            o["hue"] = u.hue
            o["city"] = u.city
            o["publicKey"] = pair.1
            return o
        }
    }

    /// Справочник аккаунта (при открытии): прежний забывается.
    static func load(_ arr: [JSONObject]?) {
        locked {
            people.removeAll()
            keys.removeAll()
        }
        for o in arr ?? [] { remember(o) }
        locked { _version += 1 }
    }

    static func clear() { load(nil) }

    /// Для сохранения на телефоне (Snapshot.people) — тот же состав, что json().
    static func entries() -> [DirectoryEntry] {
        json().map { o in
            let u = User(id: Json.int(o, "id"), name: Json.string(o, "name"), handle: Json.string(o, "handle"),
                         bio: Json.string(o, "bio"), hue: Json.double(o, "hue", 0.5), city: Json.string(o, "city"))
            return DirectoryEntry(user: u, publicKey: Json.string(o, "publicKey"))
        }
    }

    /// Справочник из сохранения; nil (старый файл) — пустой.
    static func restore(_ list: [DirectoryEntry]?) {
        let arr: [JSONObject]? = list.map { items in items.map { $0.json() } }
        load(arr)
    }
}

/// Человек справочника в сохранении: он сам и его открытый ключ (base64; "" — нет).
struct DirectoryEntry: Codable, Equatable {
    var user: User
    var publicKey: String

    func json() -> JSONObject {
        var o: JSONObject = [:]
        o["id"] = user.id
        o["name"] = user.name
        o["handle"] = user.handle
        o["bio"] = user.bio
        o["hue"] = user.hue
        o["city"] = user.city
        o["publicKey"] = publicKey
        return o
    }
}
