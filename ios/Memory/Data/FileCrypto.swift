import CryptoKit
import Foundation

/// Ключ одного файла секретной переписки: 32 байта ключа AES и 4 байта
/// начала нонса. Едет внутри сообщения, запечатанный ключом переписки
/// (ChatCrypto) — сервер видит только зашифрованный файл. То же — в FileCrypto.kt.
struct FileKey: Equatable {
    let key: [UInt8]
    let prefix: [UInt8]

    /// base64(ключ + начало нонса) — 36 байт.
    func encoded() -> String {
        let raw: [UInt8] = key + prefix
        return Data(raw).base64EncodedString()
    }

    /// Случайный ключ (UInt8.random — системный криптостойкий генератор).
    static func random() -> FileKey {
        var gen = SystemRandomNumberGenerator()
        var k: [UInt8] = []
        for _ in 0..<32 { k.append(UInt8.random(in: 0...255, using: &gen)) }
        var p: [UInt8] = []
        for _ in 0..<4 { p.append(UInt8.random(in: 0...255, using: &gen)) }
        return FileKey(key: k, prefix: p)
    }

    static func decode(_ s: String) -> FileKey? {
        guard let data = Data(base64Encoded: s), data.count == 36 else { return nil }
        let raw: [UInt8] = Array(data)
        return FileKey(key: Array(raw[0..<32]), prefix: Array(raw[32..<36]))
    }
}

/// Файл не расшифровался: не MEMF1, странный размер куска, подделка, перестановка или обрезка.
enum FileCryptoError: Error {
    case notMemf
    case badChunk
    case tampered
}

/// Шифрование файлов секретной переписки — формат MEMF1, одинаковый на
/// Android и iPhone:
///
///     "MEMF" | версия 1 (1 байт) | размер куска (4 байта, big-endian)
///     затем куски: шифротекст + тег 16 байт
///
/// Каждый кусок — AES-256-GCM, нонс = prefix (4 байта) + номер куска (8 байт,
/// big-endian), AAD = заголовок (9 байт) + флаг «последний» (1 байт). Номер
/// в нонсе не даёт переставить куски, флаг — незаметно отрезать хвост.
/// Кусками — чтобы большое видео не держать в памяти целиком.
enum FileCrypto {
    static let chunk = 64 * 1024
    static let tagSize = 16
    /// Ключ файла в имени вложения на проводе: «ek:» + конверт ChatCrypto.
    static let sealed = "ek:"
    private static let magic: [UInt8] = [0x4D, 0x45, 0x4D, 0x46]
    private static let maxChunk: Int = 16 << 20

    private static func header(_ chunk: Int) -> Data {
        let size = UInt32(truncatingIfNeeded: chunk)
        var bytes: [UInt8] = magic
        bytes.append(1)
        bytes.append(UInt8((size >> 24) & 0xFF))
        bytes.append(UInt8((size >> 16) & 0xFF))
        bytes.append(UInt8((size >> 8) & 0xFF))
        bytes.append(UInt8(size & 0xFF))
        return Data(bytes)
    }

    private static func nonce(_ prefix: [UInt8], _ index: UInt64) throws -> AES.GCM.Nonce {
        var bytes: [UInt8] = prefix
        for i in 0..<8 {
            let shift = UInt64(56 - i * 8)
            let byte = (index >> shift) & 0xFF
            bytes.append(UInt8(byte))
        }
        return try AES.GCM.Nonce(data: bytes)
    }

    private static func aad(_ head: Data, last: Bool) -> Data {
        var d = head
        d.append(last ? 1 : 0)
        return d
    }

    private static func seal(_ plain: Data, _ key: FileKey, _ index: UInt64, _ aad: Data) throws -> Data {
        let box = try AES.GCM.seal(plain, using: SymmetricKey(data: key.key), nonce: nonce(key.prefix, index), authenticating: aad)
        // Без нонса: он не хранится, а выводится из номера куска
        var out = Data(box.ciphertext)
        out.append(box.tag)
        return out
    }

    private static func open(_ sealedChunk: Data, _ key: FileKey, _ index: UInt64, _ aad: Data) throws -> Data {
        if sealedChunk.count < tagSize { throw FileCryptoError.tampered }
        let bytes = Data(sealedChunk)
        let split = bytes.count - tagSize
        let body = bytes.prefix(split)
        let tag = bytes.suffix(tagSize)
        do {
            let box = try AES.GCM.SealedBox(nonce: nonce(key.prefix, index), ciphertext: body, tag: tag)
            return try AES.GCM.open(box, using: SymmetricKey(data: key.key), authenticating: aad)
        } catch {
            throw FileCryptoError.tampered
        }
    }

    /// Зашифровать поток: read(n) — до n байт (меньше — только в конце), write — куда класть.
    static func encrypt(read: (Int) throws -> Data, write: (Data) throws -> Void, key: FileKey, chunk: Int = FileCrypto.chunk) throws {
        let head = header(chunk)
        try write(head)
        var index: UInt64 = 0
        var current = try read(chunk)
        while true {
            var next = Data()
            if current.count == chunk { next = try read(chunk) }
            let last = next.isEmpty
            try write(FileCrypto.seal(current, key, index, FileCrypto.aad(head, last: last)))
            if last { break }
            current = next
            index += 1
        }
    }

    /// Расшифровать поток; подделка, перестановка или обрезка — ошибка.
    static func decrypt(read: (Int) throws -> Data, write: (Data) throws -> Void, key: FileKey) throws {
        let head = try read(9)
        let bytes: [UInt8] = Array(head)
        if bytes.count != 9 { throw FileCryptoError.notMemf }
        let start: [UInt8] = Array(bytes[0..<4])
        let version: UInt8 = bytes[4]
        if start != magic || version != 1 { throw FileCryptoError.notMemf }
        let b5 = UInt32(bytes[5]) << 24
        let b6 = UInt32(bytes[6]) << 16
        let b7 = UInt32(bytes[7]) << 8
        let b8 = UInt32(bytes[8])
        let chunk = Int(b5 | b6 | b7 | b8)
        if chunk < 1 || chunk > maxChunk { throw FileCryptoError.badChunk }
        let full = chunk + tagSize
        var index: UInt64 = 0
        var current = try read(full)
        while true {
            var next = Data()
            if current.count == full { next = try read(full) }
            let last = next.isEmpty
            try write(FileCrypto.open(current, key, index, FileCrypto.aad(head, last: last)))
            if last { break }
            current = next
            index += 1
        }
    }

    // MARK: - Байты целиком

    /// Чтение из Data кусками: ровно n байт или сколько осталось.
    private final class DataReader {
        private let data: Data
        private var offset = 0

        init(_ data: Data) { self.data = Data(data) }

        func read(_ n: Int) -> Data {
            let end = min(data.count, offset + n)
            if offset >= end { return Data() }
            let out = data.subdata(in: offset..<end)
            offset = end
            return out
        }
    }

    static func encrypt(_ bytes: Data, _ key: FileKey, chunk: Int = FileCrypto.chunk) throws -> Data {
        let reader = DataReader(bytes)
        var out = Data()
        try encrypt(read: { reader.read($0) }, write: { out.append($0) }, key: key, chunk: chunk)
        return out
    }

    static func decrypt(_ bytes: Data, _ key: FileKey) throws -> Data {
        let reader = DataReader(bytes)
        var out = Data()
        try decrypt(read: { reader.read($0) }, write: { out.append($0) }, key: key)
        return out
    }

    // MARK: - Файлы (кусками по 64 КиБ, без загрузки целиком в память)

    /// Ровно n байт из файла или сколько осталось до конца.
    private static func readFully(_ handle: FileHandle, _ n: Int) throws -> Data {
        var out = Data()
        while out.count < n {
            let part = try handle.read(upToCount: n - out.count) ?? Data()
            if part.isEmpty { break }
            out.append(part)
        }
        return out
    }

    private static func openPair(_ src: URL, _ dst: URL) throws -> (FileHandle, FileHandle) {
        let fm = FileManager.default
        try? fm.removeItem(at: dst)
        try fm.createDirectory(at: dst.deletingLastPathComponent(), withIntermediateDirectories: true)
        fm.createFile(atPath: dst.path, contents: nil)
        let input = try FileHandle(forReadingFrom: src)
        do {
            let output = try FileHandle(forWritingTo: dst)
            return (input, output)
        } catch {
            try? input.close()
            throw error
        }
    }

    static func encryptFile(_ src: URL, _ dst: URL, _ key: FileKey) throws {
        let (input, output) = try openPair(src, dst)
        defer {
            try? input.close()
            try? output.close()
        }
        try encrypt(read: { try readFully(input, $0) }, write: { try output.write(contentsOf: $0) }, key: key)
    }

    static func decryptFile(_ src: URL, _ dst: URL, _ key: FileKey) throws {
        let (input, output) = try openPair(src, dst)
        defer { try? input.close() }
        do {
            try decrypt(read: { try readFully(input, $0) }, write: { try output.write(contentsOf: $0) }, key: key)
        } catch {
            // Недорасшифрованное не оставляем
            try? output.close()
            try? FileManager.default.removeItem(at: dst)
            throw error
        }
        try output.close()
    }
}
