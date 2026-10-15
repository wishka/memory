import Foundation
import UIKit

/// Откуда трек медиатеки.
enum CatalogSource: String, CaseIterable {
    case jamendo = "Jamendo"
    case openverse = "Openverse"
}

/// Трек медиатеки PRISM: слушается потоком, в «Мою музыку» скачивается,
/// если площадка это разрешает. license — «CC BY-NC 3.0»; его и автора
/// нужно указывать, когда трек звучит в ролике.
struct CatalogTrack: Identifiable, Equatable {
    let id: String
    let source: CatalogSource
    let title: String
    let artist: String
    let durationMs: Int64
    let streamURL: URL
    let downloadURL: URL?
    let imageURL: URL?
    let license: String
    let licenseURL: URL?
    let pageURL: URL?

    var downloadable: Bool { downloadURL != nil }
}

/// Медиатека PRISM — открытая музыка из двух источников:
///  - Jamendo (api.jamendo.com/v3.0) — независимые музыканты, лицензии
///    Creative Commons; нужен ключ client_id (devportal.jamendo.com).
///    Бесплатный ключ — для некоммерческого приложения;
///  - Openverse (api.openverse.org) — каталог открытого аудио без ключа;
///    берём CC0 и CC BY.
/// Ключ Jamendo лежит в Secrets.plist рядом с кодом (в git не попадает,
/// CI создаёт его из секрета JAMENDO_CLIENT_ID). Те же правила — в
/// MusicCatalog.kt на Android.
enum MusicCatalog {
    static let page = 20
    private static let userAgent = "Memory/1.0 (iOS; github.com/wishka/memory)"

    static var jamendoKey: String {
        guard let url = Bundle.main.url(forResource: "Secrets", withExtension: "plist"),
              let dict = NSDictionary(contentsOf: url),
              let key = dict["JAMENDO_CLIENT_ID"] as? String else { return "" }
        return key.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static var hasJamendoKey: Bool { !jamendoKey.isEmpty }

    /// Жанры-метки: Jamendo ищет по тегам, Openverse — по словам.
    static let genres: [(tag: String, title: String)] = [
        ("lofi", "Lo-fi"), ("ambient", "Эмбиент"), ("electronic", "Электроника"), ("pop", "Поп"),
        ("rock", "Рок"), ("hiphop", "Хип-хоп"), ("jazz", "Джаз"), ("classical", "Классика"),
        ("cinematic", "Кино"), ("acoustic", "Акустика"), ("chillout", "Чилаут"), ("dance", "Танцевальная"),
    ]

    enum CatalogError: LocalizedError {
        case noKey, network, server(String)
        var errorDescription: String? {
            switch self {
            case .noKey: return t("Jamendo не подключён: в сборке нет ключа JAMENDO_CLIENT_ID.")
            case .network: return t("Нет связи с медиатекой. Проверьте интернет и попробуйте ещё раз.")
            case .server(let message): return message
            }
        }
    }

    private static func json(_ url: URL) async throws -> [String: Any] {
        var request = URLRequest(url: url, timeoutInterval: 15)
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        guard let result = try? await URLSession.shared.data(for: request),
              let http = result.1 as? HTTPURLResponse, (200..<300).contains(http.statusCode),
              let object = try? JSONSerialization.jsonObject(with: result.0) as? [String: Any] else {
            throw CatalogError.network
        }
        return object
    }

    /// Поиск: query — слова, genre — метка; оба пустые — популярное за неделю.
    static func search(_ source: CatalogSource, query: String, genre: String?, page: Int) async throws -> (tracks: [CatalogTrack], more: Bool) {
        switch source {
        case .jamendo: return try await jamendo(query: query, genre: genre, page: page)
        case .openverse: return try await openverse(query: query, genre: genre, page: page)
        }
    }

    private static func jamendo(query: String, genre: String?, page: Int) async throws -> ([CatalogTrack], Bool) {
        guard hasJamendoKey else { throw CatalogError.noKey }
        var c = URLComponents(string: "https://api.jamendo.com/v3.0/tracks/")!
        var items = [
            URLQueryItem(name: "client_id", value: jamendoKey),
            URLQueryItem(name: "format", value: "json"),
            URLQueryItem(name: "audioformat", value: "mp32"),
            URLQueryItem(name: "imagesize", value: "200"),
            URLQueryItem(name: "limit", value: "\(Self.page)"),
            URLQueryItem(name: "offset", value: "\(page * Self.page)"),
        ]
        let words = query.trimmingCharacters(in: .whitespaces)
        if words.isEmpty {
            items.append(URLQueryItem(name: "order", value: "popularity_week"))
        } else {
            items.append(URLQueryItem(name: "search", value: words))
            items.append(URLQueryItem(name: "boost", value: "popularity_month"))
        }
        if let genre { items.append(URLQueryItem(name: "fuzzytags", value: genre)) }
        c.queryItems = items
        let root = try await json(c.url!)
        if let headers = root["headers"] as? [String: Any], (headers["status"] as? String) != "success" {
            let message = (headers["error_message"] as? String) ?? ""
            throw CatalogError.server(message.isEmpty ? t("Jamendo не ответил") : message)
        }
        let results = root["results"] as? [[String: Any]] ?? []
        let tracks: [CatalogTrack] = results.compactMap { o in
            guard let stream = (o["audio"] as? String).flatMap(URL.init(string:)) else { return nil }
            let ccURL = o["license_ccurl"] as? String ?? ""
            let allowed = o["audiodownload_allowed"] as? Bool ?? true
            let image = (o["image"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? (o["album_image"] as? String ?? "")
            return CatalogTrack(
                id: "jamendo-\(o["id"].map { "\($0)" } ?? UUID().uuidString)",
                source: .jamendo,
                title: o["name"] as? String ?? "",
                artist: o["artist_name"] as? String ?? "",
                durationMs: Int64((o["duration"] as? Int) ?? Int((o["duration"] as? String) ?? "") ?? 0) * 1000,
                streamURL: stream,
                downloadURL: allowed ? (o["audiodownload"] as? String).flatMap { $0.isEmpty ? nil : URL(string: $0) } : nil,
                imageURL: URL(string: image),
                license: licenseFromURL(ccURL),
                licenseURL: URL(string: ccURL),
                pageURL: (o["shareurl"] as? String).flatMap(URL.init(string:))
            )
        }
        return (tracks, results.count == Self.page)
    }

    private static func openverse(query: String, genre: String?, page: Int) async throws -> ([CatalogTrack], Bool) {
        let words = [query.trimmingCharacters(in: .whitespaces), genre ?? ""].filter { !$0.isEmpty }.joined(separator: " ")
        var c = URLComponents(string: "https://api.openverse.org/v1/audio/")!
        c.queryItems = [
            URLQueryItem(name: "q", value: words.isEmpty ? "music" : words),
            URLQueryItem(name: "category", value: "music"),
            URLQueryItem(name: "license", value: "cc0,by"),
            URLQueryItem(name: "mature", value: "false"),
            URLQueryItem(name: "page_size", value: "\(Self.page)"),
            URLQueryItem(name: "page", value: "\(page + 1)"),
        ]
        let root = try await json(c.url!)
        let results = root["results"] as? [[String: Any]] ?? []
        let tracks: [CatalogTrack] = results.compactMap { o in
            // Только https: обычный http телефон не пропустит (App Transport Security)
            guard let file = (o["url"] as? String).flatMap(URL.init(string:)), file.scheme == "https" else { return nil }
            let title = o["title"] as? String ?? ""
            return CatalogTrack(
                id: "openverse-\(o["id"] as? String ?? UUID().uuidString)",
                source: .openverse,
                title: title.isEmpty ? t("Без названия") : title,
                artist: o["creator"] as? String ?? "",
                durationMs: Int64(o["duration"] as? Int ?? 0),
                streamURL: file,
                downloadURL: file,
                imageURL: (o["thumbnail"] as? String).flatMap(URL.init(string:)),
                license: licenseLabel(o["license"] as? String ?? "", o["license_version"] as? String ?? ""),
                licenseURL: (o["license_url"] as? String).flatMap(URL.init(string:)),
                pageURL: (o["foreign_landing_url"] as? String).flatMap(URL.init(string:))
            )
        }
        let pages = root["page_count"] as? Int ?? 1
        return (tracks, page + 1 < pages)
    }

    /// «cc0» → «CC0», «by-nc», «4.0» → «CC BY-NC 4.0».
    static func licenseLabel(_ code: String, _ version: String) -> String {
        if code.isEmpty { return "" }
        if code.lowercased() == "cc0" { return "CC0" }
        if code.lowercased() == "pdm" { return t("Общественное достояние") }
        return "CC \(code.uppercased()) \(version)".trimmingCharacters(in: .whitespaces)
    }

    /// «…/licenses/by-nc-sa/3.0/» → «CC BY-NC-SA 3.0».
    static func licenseFromURL(_ url: String) -> String {
        guard let range = url.range(of: "/licenses/") else { return url.contains("publicdomain") ? "CC0" : "" }
        let parts = url[range.upperBound...].split(separator: "/").map(String.init)
        guard let code = parts.first, !code.isEmpty else { return "" }
        return licenseLabel(code, parts.count > 1 ? parts[1] : "")
    }

    /// Скачать трек в «Мою музыку» (до 200 МБ). nil — не получилось.
    static func download(_ track: CatalogTrack) async -> MusicTrack? {
        guard let source = track.downloadURL else { return nil }
        var request = URLRequest(url: source, timeoutInterval: 60)
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        guard let result = try? await URLSession.shared.download(for: request),
              let http = result.1 as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { return nil }
        let temp = result.0
        let size = (try? temp.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
        guard Int64(size) <= MediaFiles.maxBytes else {
            try? FileManager.default.removeItem(at: temp)
            return nil
        }
        let id = UUID().uuidString
        // Расширение — как у файла: по нему плеер понимает формат (mp3, ogg, flac…)
        let ext = source.pathExtension.isEmpty || source.pathExtension.count > 5 ? "mp3" : source.pathExtension.lowercased()
        let fileName = "\(id).\(ext)"
        do {
            try FileManager.default.moveItem(at: temp, to: MediaFiles.directory.appendingPathComponent(fileName))
        } catch {
            return nil
        }
        return MusicTrack(
            id: id, title: String(track.title.prefix(120)), artist: String(track.artist.prefix(120)),
            fileName: fileName, durationMs: track.durationMs, addedAt: Date(),
            license: track.license, sourceUrl: track.pageURL?.absoluteString
        )
    }

    private static let covers = NSCache<NSURL, UIImage>()

    /// Обложка трека, с кэшем в памяти.
    static func cover(_ url: URL?) async -> UIImage? {
        guard let url else { return nil }
        if let cached = covers.object(forKey: url as NSURL) { return cached }
        var request = URLRequest(url: url, timeoutInterval: 10)
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        guard let result = try? await URLSession.shared.data(for: request),
              let http = result.1 as? HTTPURLResponse, (200..<300).contains(http.statusCode),
              let image = UIImage(data: result.0) else { return nil }
        covers.setObject(image, forKey: url as NSURL)
        return image
    }
}
