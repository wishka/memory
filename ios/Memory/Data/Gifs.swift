import Foundation

/// Гифки: встроенный набор Memory (Gifs/*.gif, рисует scripts/media_assets.py),
/// свои из галереи и поиск GIPHY. Выбранная гифка копируется в папку
/// вложений обычным вложением-фото с расширением .gif — так она переживёт
/// перезапуск и уйдёт в «Поделиться» как есть. Те же правила — в Gifs.kt.
struct BuiltInGif: Identifiable, Equatable {
    let id: String
    let ru: String

    var title: String { t(ru) }
}

enum Gifs {
    /// В том же порядке, что в scripts/media_assets.py.
    static let builtIn: [BuiltInGif] = [
        BuiltInGif(id: "hello", ru: "Привет!"), BuiltInGif(id: "thanks", ru: "Спасибо"), BuiltInGif(id: "love", ru: "Люблю"),
        BuiltInGif(id: "wow", ru: "Ого!"), BuiltInGif(id: "haha", ru: "Ха-ха"), BuiltInGif(id: "hug", ru: "Обнимаю"),
        BuiltInGif(id: "party", ru: "Ура!"), BuiltInGif(id: "yes", ru: "Да!"), BuiltInGif(id: "morning", ru: "Доброе утро"),
        BuiltInGif(id: "night", ru: "Спокойной ночи"), BuiltInGif(id: "rocket", ru: "Погнали!"), BuiltInGif(id: "crystal", ru: "Memory"),
    ]

    /// Встроенная гифка в сборке — для показа в выборе.
    static func bundleURL(_ gif: BuiltInGif) -> URL? {
        Bundle.main.url(forResource: gif.id, withExtension: "gif")
    }

    /// Встроенная гифка — копией во вложения.
    static func attachment(_ gif: BuiltInGif) -> Attachment? {
        guard let url = bundleURL(gif), let data = try? Data(contentsOf: url) else { return nil }
        return MediaFiles.save(data, kind: .photo, ext: "gif", name: "\(gif.id).gif")
    }

    /// Данные — гифка: файл начинается с «GIF8».
    static func isGif(_ data: Data) -> Bool {
        data.count >= 4 && data.prefix(4).elementsEqual(Array("GIF8".utf8))
    }

    /// Копия гифки (недавней): недавняя может уйти из списка, а сообщение — останется.
    static func copy(_ gif: Attachment) -> Attachment? {
        guard let data = try? Data(contentsOf: MediaFiles.url(gif)) else { return nil }
        return MediaFiles.save(data, kind: .photo, ext: "gif", name: gif.name.isEmpty ? gif.fileName : gif.name)
    }
}

/// Гифка из поиска GIPHY.
struct GiphyGif: Identifiable, Equatable {
    let id: String
    let title: String
    let previewUrl: String
    let url: String
    let width: Int
    let height: Int
}

enum GiphyResult: Equatable {
    case gifs([GiphyGif])
    case failure(String)
}

/// Поиск GIPHY (developers.giphy.com). Ключ лежит в Secrets.plist
/// (GIPHY_API_KEY; в git не попадает, CI создаёт его из секрета) — как
/// ключ Jamendo в MusicCatalog. Нет ключа — поиска нет. Условие GIPHY —
/// подпись «Powered by GIPHY» рядом с результатами.
enum Giphy {
    private static let api = "https://api.giphy.com/v1/gifs/"
    private static let userAgent = "Memory/1.0 (iOS; github.com/wishka/memory)"

    static var key: String {
        guard let url = Bundle.main.url(forResource: "Secrets", withExtension: "plist"),
              let dict = NSDictionary(contentsOf: url),
              let key = dict["GIPHY_API_KEY"] as? String else { return "" }
        return key.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static var available: Bool { !key.isEmpty }

    private static func get(_ url: URL, timeout: TimeInterval = 15) async -> Data? {
        var request = URLRequest(url: url, timeoutInterval: timeout)
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        guard let result = try? await URLSession.shared.data(for: request),
              let http = result.1 as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { return nil }
        return result.0
    }

    /// Пустой запрос — популярное сейчас.
    static func search(_ query: String, offset: Int = 0) async -> GiphyResult {
        guard available else { return .failure("no-key") }
        let q = query.trimmingCharacters(in: .whitespaces)
        var c = URLComponents(string: api + (q.isEmpty ? "trending" : "search"))!
        var items = [
            URLQueryItem(name: "api_key", value: key),
            URLQueryItem(name: "limit", value: "24"),
            URLQueryItem(name: "offset", value: "\(offset)"),
            URLQueryItem(name: "rating", value: "pg-13"),
        ]
        if !q.isEmpty {
            items.append(URLQueryItem(name: "q", value: q))
            items.append(URLQueryItem(name: "lang", value: "ru"))
        }
        c.queryItems = items
        guard let url = c.url, let data = await get(url) else { return .failure("network") }
        return .gifs(parse(data))
    }

    /// Разбор ответа: превью — fixed_width_small, сама гифка — fixed_width (до ~200 px, лёгкая).
    static func parse(_ data: Data) -> [GiphyGif] {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let list = root["data"] as? [[String: Any]] else { return [] }
        return list.compactMap { o in
            guard let images = o["images"] as? [String: Any],
                  let full = (images["fixed_width"] as? [String: Any]) ?? (images["downsized"] as? [String: Any]) else { return nil }
            let small = (images["fixed_width_small"] as? [String: Any]) ?? full
            guard let url = full["url"] as? String, !url.isEmpty else { return nil }
            let preview = (small["url"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? url
            func number(_ v: Any?) -> Int? {
                if let s = v as? String { return Int(s) }
                return v as? Int
            }
            return GiphyGif(
                id: o["id"] as? String ?? "", title: o["title"] as? String ?? "",
                previewUrl: preview, url: url,
                width: number(full["width"]) ?? 200, height: number(full["height"]) ?? 200
            )
        }
    }

    /// Папка кэша превью.
    private static var cacheDirectory: URL {
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("giphy", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    /// Превью из поиска — в кэш (по имени из адреса), чтобы показать гифкой.
    static func cachePreview(_ url: String) async -> URL? {
        let name = String(UInt32(truncatingIfNeeded: stableHash(url)), radix: 16) + ".gif"
        let file = cacheDirectory.appendingPathComponent(name)
        if let size = (try? FileManager.default.attributesOfItem(atPath: file.path))?[.size] as? Int, size > 0 { return file }
        guard let remote = URL(string: url), let data = await get(remote), Gifs.isGif(data) else { return nil }
        // Сначала во временный «.part», потом на место: оборванный файл не сочтётся готовым
        let part = file.appendingPathExtension("part")
        do {
            try data.write(to: part)
            try? FileManager.default.removeItem(at: file)
            try FileManager.default.moveItem(at: part, to: file)
            return file
        } catch {
            try? FileManager.default.removeItem(at: part)
            return nil
        }
    }

    /// Скачать выбранную гифку во вложения.
    static func download(_ gif: GiphyGif) async -> Attachment? {
        guard let remote = URL(string: gif.url), let data = await get(remote, timeout: 20) else { return nil }
        return MediaFiles.save(data, kind: .photo, ext: "gif", name: "giphy-\(gif.id).gif")
    }

    /// Хэш строки, одинаковый между запусками (hashValue в Swift каждый раз другой).
    private static func stableHash(_ s: String) -> UInt64 {
        var h: UInt64 = 1_469_598_103_934_665_603
        for b in s.utf8 {
            h ^= UInt64(b)
            h = h &* 1_099_511_628_211
        }
        return h
    }
}
