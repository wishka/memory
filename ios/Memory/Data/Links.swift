import Foundation

/// Куда ведёт ссылка Memory.
enum LinkTarget: Equatable {
    case postLink(Int64)
    case profile(String)
    case prismLink(Int64)
    case groupLink(Int)
    case tagLink(String)
}

/// Ссылки на запись, профиль, PRISM, группу и тег.
///
/// Для мессенджеров — веб-ссылка на страницу docs/l (GitHub Pages): она
/// открывает приложение по memory://…, а если его нет — рассказывает, как
/// поставить. Внутри Memory такие ссылки открываются сразу, без браузера.
///
///   https://wishka.github.io/memory/l/?p=12345   — запись
///   https://wishka.github.io/memory/l/?u=vera     — профиль @vera
///   memory://post/12345, memory://u/vera, memory://prism/7, memory://group/3, memory://tag/закат
///
/// Те же правила — в Links.kt.
enum Links {
    static let webURL = "https://wishka.github.io/memory/l/"
    static let scheme = "memory"

    /// Кодирование как у URLEncoder (Java): буквы, цифры и «.-*_» — как есть, пробел — «+», остальное — %XX.
    private static func enc(_ s: String) -> String {
        var out = ""
        for byte in s.utf8 {
            switch byte {
            case UInt8(ascii: "a")...UInt8(ascii: "z"), UInt8(ascii: "A")...UInt8(ascii: "Z"), UInt8(ascii: "0")...UInt8(ascii: "9"),
                 UInt8(ascii: "."), UInt8(ascii: "-"), UInt8(ascii: "*"), UInt8(ascii: "_"):
                out.append(Character(Unicode.Scalar(byte)))
            case UInt8(ascii: " "):
                out.append("+")
            default:
                out.append(String(format: "%%%02X", UInt32(byte)))
            }
        }
        return out
    }

    /// Раскодирование как у URLDecoder: «+» — пробел, %XX — байты; испорченное — "".
    private static func dec(_ s: String) -> String {
        s.replacingOccurrences(of: "+", with: " ").removingPercentEncoding ?? ""
    }

    private static func dropPrefix(_ s: String, _ prefix: String) -> String {
        s.hasPrefix(prefix) ? String(s.dropFirst(prefix.count)) : s
    }

    /// Часть строки до первого знака (вся строка, если его нет).
    private static func before(_ s: String, _ c: Character) -> String {
        s.firstIndex(of: c).map { String(s[..<$0]) } ?? s
    }

    /// Часть строки после первого знака (или missing, если его нет).
    private static func after(_ s: String, _ c: Character, _ missing: String) -> String {
        s.firstIndex(of: c).map { String(s[s.index(after: $0)...]) } ?? missing
    }

    static func post(_ id: Int64) -> String { webURL + "?p=" + String(id) }
    static func profile(_ handle: String) -> String { webURL + "?u=" + enc(dropPrefix(handle, "@").lowercased()) }
    static func prism(_ id: Int64) -> String { webURL + "?r=" + String(id) }
    static func group(_ id: Int) -> String { webURL + "?g=" + String(id) }
    static func tag(_ tag: String) -> String { webURL + "?t=" + enc(dropPrefix(tag, "#").lowercased()) }

    /// Та же ссылка, но сразу в приложение (её открывает страница docs/l).
    static func app(_ target: LinkTarget) -> String {
        switch target {
        case .postLink(let id): return "\(scheme)://post/\(id)"
        case .profile(let handle): return "\(scheme)://u/\(enc(handle))"
        case .prismLink(let id): return "\(scheme)://prism/\(id)"
        case .groupLink(let id): return "\(scheme)://group/\(id)"
        case .tagLink(let tag): return "\(scheme)://tag/\(enc(tag))"
        }
    }

    static func web(_ target: LinkTarget) -> String {
        switch target {
        case .postLink(let id): return post(id)
        case .profile(let handle): return profile(handle)
        case .prismLink(let id): return prism(id)
        case .groupLink(let id): return group(id)
        case .tagLink(let t): return tag(t)
        }
    }

    /// Разобрать ссылку: веб или memory://. nil — это не ссылка Memory (или приглашение — его разбирает Invites).
    static func parse(_ link: String) -> LinkTarget? {
        let s = link.trimmingCharacters(in: .whitespacesAndNewlines)
        let webBase = webURL.hasSuffix("/") ? String(webURL.dropLast()) : webURL
        if s.hasPrefix(webBase) {
            let q = before(after(s, "?", ""), "#")
            var params: [String: String] = [:]
            for part in q.split(separator: "&", omittingEmptySubsequences: false).map(String.init) where part.contains("=") {
                params[before(part, "=")] = dec(after(part, "=", ""))
            }
            if let p = params["p"] { return Int64(p).map { LinkTarget.postLink($0) } }
            if let u = params["u"] { return cleanHandle(u).map { LinkTarget.profile($0) } }
            if let r = params["r"] { return Int64(r).map { LinkTarget.prismLink($0) } }
            if let g = params["g"] { return Int(g).map { LinkTarget.groupLink($0) } }
            if let t = params["t"] { return cleanTag(t).map { LinkTarget.tagLink($0) } }
            return nil
        }
        let prefix = "\(scheme)://"
        if s.hasPrefix(prefix) {
            var rest = before(before(dropPrefix(s, prefix), "?"), "#")
            while rest.hasSuffix("/") { rest.removeLast() }
            let kind = before(rest, "/")
            let value = dec(after(rest, "/", ""))
            switch kind {
            case "post": return Int64(value).map { LinkTarget.postLink($0) }
            case "u": return cleanHandle(value).map { LinkTarget.profile($0) }
            case "prism": return Int64(value).map { LinkTarget.prismLink($0) }
            case "group": return Int(value).map { LinkTarget.groupLink($0) }
            case "tag": return cleanTag(value).map { LinkTarget.tagLink($0) }
            default: return nil
            }
        }
        return nil
    }

    private static let handleChars = Set("abcdefghijklmnopqrstuvwxyz0123456789._")

    private static func cleanHandle(_ raw: String) -> String? {
        let h = dropPrefix(raw, "@").lowercased()
        guard (3...20).contains(h.count), h.allSatisfy({ handleChars.contains($0) }) else { return nil }
        return h
    }

    private static func cleanTag(_ raw: String) -> String? {
        let t = dropPrefix(raw, "#").lowercased()
        guard (2...40).contains(t.utf16.count), t.allSatisfy({ $0.isLetter || $0.isNumber || $0 == "_" }) else { return nil }
        return t
    }

    /// Текст для «Поделиться»: начало записи и ссылка.
    static func shareText(_ p: Post) -> String {
        var preview = p.text.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespacesAndNewlines)
        if preview.utf16.count > 100 {
            preview = String(preview.prefix(100))
            while let last = preview.last, last.isWhitespace { preview.removeLast() }
            preview += "…"
        }
        return [preview, post(p.id)].filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }.joined(separator: "\n")
    }
}
