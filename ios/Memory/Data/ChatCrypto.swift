import CryptoKit
import Foundation

/// Пара ключей X25519: закрытый остаётся на телефоне, открытый уходит собеседнику.
/// Оба — по 32 байта, как у X25519 в Android-версии (ChatCrypto.kt): телефоны понимают друг друга.
struct ChatKeyPair: Equatable {
    let privateKey: [UInt8]
    let publicKey: [UInt8]

    static func generate() -> ChatKeyPair {
        let key = Curve25519.KeyAgreement.PrivateKey()
        return ChatKeyPair(privateKey: Array(key.rawRepresentation), publicKey: Array(key.publicKey.rawRepresentation))
    }

    /// Пара из закрытого ключа (32 байта).
    static func of(_ privateKey: [UInt8]) -> ChatKeyPair {
        guard let key = try? Curve25519.KeyAgreement.PrivateKey(rawRepresentation: privateKey) else {
            preconditionFailure("X25519: закрытый ключ — 32 байта")
        }
        return ChatKeyPair(privateKey: privateKey, publicKey: Array(key.publicKey.rawRepresentation))
    }
}

/// Сквозное шифрование переписки.
///
///  ключ сессии = HKDF-SHA256(X25519(мой закрытый, его открытый),
///                            salt = «memory-e2e-v1», info = открытые ключи по порядку)
///  сообщение   = AES-256-GCM, конверт = base64(nonce 12 байт | шифртекст | тег 16 байт)
///
/// Тот же формат на Android (ChatCrypto.kt). Сервер видит только
/// конверты — прочитать их могут лишь два телефона. «Код безопасности» —
/// 60 цифр из обоих открытых ключей: совпал у обоих — между вами никто не встал.
enum ChatCrypto {
    private static let salt = "memory-e2e-v1"

    /// Два открытых ключа по порядку (побайтно, без знака) — одинаково у обоих собеседников.
    private static func ordered(_ a: [UInt8], _ b: [UInt8]) -> [UInt8] {
        compare(a, b) <= 0 ? a + b : b + a
    }

    private static func compare(_ a: [UInt8], _ b: [UInt8]) -> Int {
        for i in 0..<min(a.count, b.count) {
            let d = Int(a[i]) - Int(b[i])
            if d != 0 { return d }
        }
        return 0
    }

    /// HKDF-SHA256 (RFC 5869).
    static func hkdf(_ ikm: [UInt8], salt: [UInt8], info: [UInt8], length: Int = 32) -> [UInt8] {
        let key = HKDF<SHA256>.deriveKey(
            inputKeyMaterial: SymmetricKey(data: ikm), salt: salt, info: info, outputByteCount: length
        )
        return key.withUnsafeBytes { Array($0) }
    }

    /// Ключ сессии: мой закрытый × его открытый → HKDF. Ошибка — испорченный или слабый ключ собеседника.
    static func sessionKey(_ mine: ChatKeyPair, _ peerPublic: [UInt8]) throws -> [UInt8] {
        let priv = try Curve25519.KeyAgreement.PrivateKey(rawRepresentation: mine.privateKey)
        let pub = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: peerPublic)
        let shared = try priv.sharedSecretFromKeyAgreement(with: pub)
        let key = shared.hkdfDerivedSymmetricKey(
            using: SHA256.self, salt: Data(salt.utf8), sharedInfo: Data(ordered(mine.publicKey, peerPublic)), outputByteCount: 32
        )
        return key.withUnsafeBytes { Array($0) }
    }

    /// Зашифровать текст: конверт для сервера. aad — к чему привязан (переписка), проверяется при расшифровке.
    static func seal(_ key: [UInt8], _ plaintext: String, aad: String = "") throws -> String {
        let box = try AES.GCM.seal(Data(plaintext.utf8), using: SymmetricKey(data: key), nonce: AES.GCM.Nonce(),
                                   authenticating: Data(aad.utf8))
        guard let combined = box.combined else { throw CryptoKitError.incorrectParameterSize }
        return combined.base64EncodedString()
    }

    /// Расшифровать конверт; nil — подделан, чужой ключ или испорчен.
    static func open(_ key: [UInt8], _ envelope: String, aad: String = "") -> String? {
        guard let raw = Data(base64Encoded: envelope), raw.count >= 12 + 16,
              let box = try? AES.GCM.SealedBox(combined: raw),
              let plain = try? AES.GCM.open(box, using: SymmetricKey(data: key), authenticating: Data(aad.utf8)) else { return nil }
        return String(data: plain, encoding: .utf8)
    }

    /// Код безопасности: 12 групп по 5 цифр, одинаковый у обоих собеседников.
    static func safetyNumber(_ a: [UInt8], _ b: [UInt8]) -> String {
        let digest = Array(SHA512.hash(data: Data(Array(salt.utf8) + ordered(a, b))))
        return (0..<12).map { i -> String in
            var v: UInt64 = 0
            for j in 0..<5 { v = (v << 8) | UInt64(digest[i * 5 + j]) }
            let s = String(v % 100_000)
            return String(repeating: "0", count: max(0, 5 - s.count)) + s
        }.joined(separator: " ")
    }

    /// Ключи демо-собеседника: пока сервера нет, «его телефон» живёт здесь же.
    /// Детерминированы от номера, чтобы код безопасности не менялся.
    static func demoPeer(_ peerId: Int) -> ChatKeyPair {
        ChatKeyPair.of(Array(SHA256.hash(data: Data("memory-demo-peer-\(peerId)".utf8))))
    }
}

/// Свой ключ шифрования переписки — на телефоне, в защищённой части
/// хранилища: ключи с префиксом «session.» SecureStore кладёт в Связку
/// ключей. Один ключ на аккаунт; новый телефон — новый ключ.
final class ChatKeys {
    private let store: KeyValueStore

    init(_ store: KeyValueStore) {
        self.store = store
    }

    private func key(_ login: String) -> String { "session.e2e.\(login.lowercased())" }

    func identity(_ login: String) -> ChatKeyPair {
        if let hex = store.get(key(login)), hex.count == 64, let bytes = [UInt8](hex: hex), bytes.count == 32 {
            return ChatKeyPair.of(bytes)
        }
        let pair = ChatKeyPair.generate()
        store.put(key(login), pair.privateKey.hex)
        return pair
    }

    func forget(_ login: String) {
        store.remove(key(login))
    }
}

/// Сколько живут исчезающие сообщения. 0 — не исчезают.
enum Disappear: String, CaseIterable, Identifiable {
    case off = "Выкл."
    case fiveMinutes = "5 минут"
    case hour = "1 час"
    case day = "1 день"
    case week = "1 неделя"

    var id: String { rawValue }

    /// Срок жизни, мс.
    var ms: Int64 {
        switch self {
        case .off: return 0
        case .fiveMinutes: return 5 * 60_000
        case .hour: return 60 * 60_000
        case .day: return 24 * 60 * 60_000
        case .week: return 7 * 24 * 60 * 60_000
        }
    }

    /// Название на выбранном языке.
    var title: String { t(rawValue) }

    static func of(_ ms: Int64) -> Disappear { allCases.first { $0.ms == ms } ?? .off }
}
