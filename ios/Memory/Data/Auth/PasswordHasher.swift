import CommonCrypto
import Foundation
import Security

/// Хранение паролей: PBKDF2-HMAC-SHA256 с солью на каждый аккаунт.
///
/// Сам пароль нигде не сохраняется — только соль и производный ключ.
/// Даже если кто-то вытащит настройки с телефона, пароль из них не
/// восстановить, а перебор упирается в 120 000 итераций на каждую попытку.
enum PasswordHasher {
    static let iterations = 120_000
    private static let keyBytes = 32
    private static let saltBytes = 16

    static func newSalt() -> [UInt8] { randomBytes(saltBytes) }

    static func hash(_ password: String, salt: [UInt8], iterations: Int = PasswordHasher.iterations) -> [UInt8] {
        var derived = [UInt8](repeating: 0, count: keyBytes)
        let passwordBytes = Array(password.utf8)
        let status = passwordBytes.withUnsafeBytes { pw -> Int32 in
            salt.withUnsafeBufferPointer { saltPtr -> Int32 in
                derived.withUnsafeMutableBufferPointer { out -> Int32 in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        pw.baseAddress?.assumingMemoryBound(to: CChar.self),
                        passwordBytes.count,
                        saltPtr.baseAddress,
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        UInt32(iterations),
                        out.baseAddress,
                        keyBytes
                    )
                }
            }
        }
        precondition(status == Int32(kCCSuccess), "PBKDF2 failed: \(status)")
        return derived
    }

    /// Сравнение за постоянное время: по времени ответа не угадать, сколько байт совпало.
    static func verify(_ password: String, salt: [UInt8], expected: [UInt8], iterations: Int = PasswordHasher.iterations) -> Bool {
        let actual = hash(password, salt: salt, iterations: iterations)
        guard actual.count == expected.count else { return false }
        var diff: UInt8 = 0
        for i in 0..<actual.count {
            diff |= actual[i] ^ expected[i]
        }
        return diff == 0
    }

    static func randomToken() -> String { randomBytes(32).hex }

    static func randomBytes(_ count: Int) -> [UInt8] {
        var bytes = [UInt8](repeating: 0, count: count)
        let status = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        if status != errSecSuccess {
            // Запасной путь: системный генератор Swift тоже криптостойкий
            var rng = SystemRandomNumberGenerator()
            for i in 0..<count { bytes[i] = UInt8.random(in: 0...255, using: &rng) }
        }
        return bytes
    }
}

extension Array where Element == UInt8 {
    var hex: String { map { String(format: "%02x", $0) }.joined() }

    init?(hex: String) {
        let chars = Array(hex.utf8)
        guard chars.count % 2 == 0 else { return nil }
        var out = [UInt8]()
        out.reserveCapacity(chars.count / 2)
        var i = 0
        while i < chars.count {
            guard let byte = UInt8(String(decoding: chars[i..<i + 2], as: UTF8.self), radix: 16) else { return nil }
            out.append(byte)
            i += 2
        }
        self = out
    }
}
