import Foundation

/// Сведения аккаунта для выгрузки — то, что хранится вне файла состояния.
/// consentAt — когда приняты документы, мс с 1970; 0 — не приняты.
struct AccountExport: Equatable {
    var login: String
    var displayName: String
    var info: ProfileInfo
    var email: String
    var emailConfirmed: Bool
    var consentVersion: String?
    var consentAt: Int64
    var recoveryCodesLeft: Int
}

/// «Скачать мои данные» — право на доступ к своим данным (закон РА о
/// защите персональных данных, ст. 15 и 20; см. docs/legal/privacy.md).
///
/// Архив .zip:
///   README.txt      — что где лежит, на языке интерфейса
///   account.json    — аккаунт: логин, имя, сведения профиля, почта, согласие
///   memory.json     — записи, переписка, группы, PRISM, полка, настройки,
///                     лента активности, жалобы — всё, что приложение хранит
///   media/…         — фото, видео, голосовые, стикеры и своя музыка (по желанию)
///
/// Пароль и коды восстановления в архив не попадают: их нет даже на
/// телефоне — только отпечатки. То же — в DataExport.kt.
///
/// Файлы вложений на iPhone хранятся именем в папке вложений (MediaFiles);
/// полный путь (начинается с «/») берётся как есть.
enum DataExport {
    /// Имена (или пути) файлов, на которые ссылается сохранённое: вложения, стикеры, аватар, музыка.
    static func files(_ s: Snapshot, avatar: String = "") -> [String] {
        var atts: [Attachment] = []
        func post(_ p: Post) {
            atts += p.media
            atts += p.commentList.compactMap(\.sticker)
        }
        s.posts.forEach(post)
        for g in s.groups {
            g.posts.forEach(post)
            if let cover = g.cover { atts.append(cover) }
            for product in g.shop { atts += product.photos }
        }
        for c in s.chats {
            for m in c.messages { atts += m.attachments }
        }
        for p in s.prisms {
            if let video = p.video { atts.append(video) }
            if let cover = p.cover { atts.append(cover) }
        }
        atts += s.recentGifs ?? []
        atts += (s.stories ?? []).compactMap(\.media)
        var paths: [String] = atts.flatMap { [$0.fileName] + $0.frames }
        paths += (s.stickers ?? []).flatMap { (st) -> [String] in [st.fileName] + (st.frameFiles ?? []) }
        paths += (s.tracks ?? []).map(\.fileName)
        paths += [avatar, s.backdrop?.photo ?? ""]
        paths += s.chats.compactMap { $0.backdrop?.photo }
        var seen = Set<String>()
        return paths.filter { p in
            !p.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !p.hasPrefix("http") && !p.hasPrefix("asset:")
                && seen.insert(p).inserted
        }
    }

    /// Где лежит файл: полный путь — как есть, имя — в папке вложений.
    static func url(_ path: String) -> URL {
        path.hasPrefix("/") ? URL(fileURLWithPath: path) : MediaFiles.resolve(path)
    }

    private static func isFile(_ url: URL) -> Bool {
        var dir: ObjCBool = false
        return FileManager.default.fileExists(atPath: url.path, isDirectory: &dir) && !dir.boolValue
    }

    /// Имя архива: memory-anna-2026-10-14.zip
    static func fileName(_ login: String, _ now: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd"
        let safe = login.filter { $0.isLetter || $0.isNumber || $0 == "." || $0 == "_" }
        return "memory-\(safe)-\(f.string(from: now)).zip"
    }

    /// account.json — красиво, с отступами.
    static func accountJSON(_ a: AccountExport, _ now: Date) -> String {
        let avatar: Any = a.info.avatar.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? NSNull() : "media/" + (a.info.avatar as NSString).lastPathComponent
        let email: Any = a.email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? NSNull() : ["address": a.email, "confirmed": a.emailConfirmed] as [String: Any]
        let consentAt: Any = a.consentAt > 0 ? iso(Date(timeIntervalSince1970: Double(a.consentAt) / 1000)) : NSNull()
        let root: [String: Any] = [
            "exportedAt": iso(now),
            "login": a.login,
            "displayName": a.displayName,
            "profile": [
                "bio": a.info.bio, "city": a.info.city, "birthday": a.info.birthday,
                "website": a.info.website, "interests": a.info.interests, "avatar": avatar,
            ] as [String: Any],
            "email": email,
            "consent": [
                "documentsVersion": (a.consentVersion as Any?) ?? NSNull(),
                "acceptedAt": consentAt,
            ] as [String: Any],
            "recoveryCodesLeft": a.recoveryCodesLeft,
            "password": t("не хранится — только отпечаток PBKDF2, в архив не попадает"),
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]),
              let text = String(data: data, encoding: .utf8) else { return "{}" }
        return text
    }

    static func readme(_ a: AccountExport, _ now: Date, _ mediaCount: Int, _ withMedia: Bool) -> String {
        var lines: [String] = []
        lines.append(tf("Данные аккаунта @%s в Memory", a.login))
        lines.append(tf("Выгружено: %s", iso(now)))
        lines.append("")
        lines.append(t("account.json — логин, имя, сведения профиля, почта и когда вы приняли документы."))
        lines.append(t("memory.json — записи, комментарии, переписка, группы, PRISM, полка, настройки приватности и уведомлений, лента активности и жалобы."))
        if withMedia {
            lines.append(tf("media/ — ваши фото, видео, голосовые, стикеры и музыка: %s файлов.", mediaCount))
        } else {
            lines.append(t("Фото и видео не вошли: при выгрузке был выбран архив без медиа."))
        }
        lines.append("")
        lines.append(t("Пароля и кодов восстановления здесь нет: Memory хранит только их отпечатки."))
        lines.append(t("Вопросы о данных — по адресу из политики конфиденциальности (Настройки → Документы)."))
        return lines.map { $0 + "\n" }.joined()
    }

    /// Записать архив в файл to. files — имена или пути (см. files());
    /// пропавшие пропускаются. Ответ — сколько файлов медиа вошло.
    @discardableResult
    static func write(to target: URL, account: AccountExport, stateJson: String, files: [String], withMedia: Bool, now: Date) throws -> Int {
        let present = withMedia ? files.map(url).filter(isFile) : []
        FileManager.default.createFile(atPath: target.path, contents: nil)
        let handle = try FileHandle(forWritingTo: target)
        defer { try? handle.close() }
        let zip = ZipWriter(date: now) { chunk in try handle.write(contentsOf: chunk) }
        try zip.add("README.txt", Data(readme(account, now, present.count, withMedia).utf8))
        try zip.add("account.json", Data(accountJSON(account, now).utf8))
        try zip.add("memory.json", Data(stateJson.utf8))
        var used = Set<String>()
        for file in present {
            // Одинаковые имена из разных папок не затирают друг друга
            let original = file.lastPathComponent
            let ext = (original as NSString).pathExtension
            let base = (original as NSString).deletingPathExtension
            var name = original
            var i = 1
            while !used.insert(name).inserted {
                name = base + "-" + String(i) + (ext.isEmpty ? "" : "." + ext)
                i += 1
            }
            try zip.add("media/" + name, try Data(contentsOf: file, options: .mappedIfSafe))
        }
        try zip.finish()
        return present.count
    }

    /// Сколько займут файлы медиа, байт — чтобы предупредить о большом архиве.
    static func mediaBytes(_ files: [String]) -> Int64 {
        files.map(url).filter(isFile).reduce(Int64(0)) { sum, u in
            let size = (try? FileManager.default.attributesOfItem(atPath: u.path)[.size] as? NSNumber)?.int64Value ?? 0
            return sum + size
        }
    }

    private static func iso(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd'T'HH:mm:ssXXX"
        return f.string(from: date)
    }
}

/// Архив .zip без сжатия (stored): фото и видео и так сжаты, а Foundation
/// не умеет писать zip. Имена — в UTF-8 (флаг 11), CRC-32 — у каждого файла.
/// Без zip64: до 4 ГБ на архив.
final class ZipWriter {
    private let sink: (Data) throws -> Void
    private var offset: UInt32 = 0
    private var central = Data()
    private var entries: UInt16 = 0
    private let dosTime: UInt16
    private let dosDate: UInt16

    init(date: Date = Date(), sink: @escaping (Data) throws -> Void) {
        self.sink = sink
        let c = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        let year = max(1980, c.year ?? 1980)
        // По частям: одним выражением компилятор Swift не успевает вывести типы
        let hour: Int = c.hour ?? 0
        let minute: Int = c.minute ?? 0
        let second: Int = c.second ?? 0
        let month: Int = c.month ?? 1
        let day: Int = c.day ?? 1
        let time: Int = (hour << 11) | (minute << 5) | (second / 2)
        let dateBits: Int = ((year - 1980) << 9) | (month << 5) | day
        dosTime = UInt16(time)
        dosDate = UInt16(dateBits)
    }

    func add(_ name: String, _ body: Data) throws {
        let nameBytes = Data(name.utf8)
        let crc = CRC32.checksum(body)
        let size = UInt32(truncatingIfNeeded: body.count)
        var local = Data()
        local.le32(0x0403_4b50)
        local.le16(20)          // версия, нужная для распаковки
        local.le16(0x0800)      // имя в UTF-8
        local.le16(0)           // без сжатия
        local.le16(dosTime)
        local.le16(dosDate)
        local.le32(crc)
        local.le32(size)
        local.le32(size)
        local.le16(UInt16(nameBytes.count))
        local.le16(0)
        local.append(nameBytes)

        var entry = Data()
        entry.le32(0x0201_4b50)
        entry.le16(20)          // кем создан
        entry.le16(20)
        entry.le16(0x0800)
        entry.le16(0)
        entry.le16(dosTime)
        entry.le16(dosDate)
        entry.le32(crc)
        entry.le32(size)
        entry.le32(size)
        entry.le16(UInt16(nameBytes.count))
        entry.le16(0)           // доп. поле
        entry.le16(0)           // комментарий
        entry.le16(0)           // диск
        entry.le16(0)           // внутренние атрибуты
        entry.le32(0)           // внешние атрибуты
        entry.le32(offset)
        entry.append(nameBytes)
        central.append(entry)

        try sink(local)
        try sink(body)
        offset = offset &+ UInt32(truncatingIfNeeded: local.count) &+ size
        entries &+= 1
    }

    func finish() throws {
        var end = Data()
        end.le32(0x0605_4b50)
        end.le16(0)
        end.le16(0)
        end.le16(entries)
        end.le16(entries)
        end.le32(UInt32(truncatingIfNeeded: central.count))
        end.le32(offset)
        end.le16(0)
        try sink(central)
        try sink(end)
    }
}

/// CRC-32 (IEEE 802.3) — как в zip.
enum CRC32 {
    private static let table: [UInt32] = (0..<256).map { i -> UInt32 in
        var c = UInt32(i)
        for _ in 0..<8 { c = (c & 1) != 0 ? 0xEDB8_8320 ^ (c >> 1) : c >> 1 }
        return c
    }

    static func checksum(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFF_FFFF
        data.withUnsafeBytes { (raw: UnsafeRawBufferPointer) in
            for byte in raw {
                crc = table[Int((crc ^ UInt32(byte)) & 0xFF)] ^ (crc >> 8)
            }
        }
        return crc ^ 0xFFFF_FFFF
    }
}

private extension Data {
    mutating func le16(_ v: UInt16) {
        append(UInt8(v & 0xFF))
        append(UInt8(v >> 8))
    }

    mutating func le32(_ v: UInt32) {
        append(UInt8(v & 0xFF))
        append(UInt8((v >> 8) & 0xFF))
        append(UInt8((v >> 16) & 0xFF))
        append(UInt8(v >> 24))
    }
}
