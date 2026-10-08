import Foundation

/// Записи, переписка, комментарии, группы и закладки — на iPhone, в файле
/// Application Support/state-<логин>.json. Пока у Memory нет сервера, так
/// своё не пропадает после перезапуска. У каждого аккаунта свой файл.
final class StateStore {
    private let directory: URL

    init(directory: URL? = nil) {
        self.directory = directory
            ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: self.directory, withIntermediateDirectories: true)
    }

    private func file(_ login: String) -> URL {
        let safe = login.filter { $0.isLetter || $0.isNumber || $0 == "." || $0 == "_" }
        return directory.appendingPathComponent("state-\(safe).json")
    }

    /// nil — файла нет, он от другой версии приложения или испорчен.
    func load(_ login: String) -> Snapshot? {
        guard let data = try? Data(contentsOf: file(login)),
              let snapshot = try? Self.decoder.decode(Snapshot.self, from: data),
              snapshot.version == Snapshot.currentVersion else { return nil }
        return snapshot
    }

    /// Запись целиком во временный файл и замена: оборванная запись не испортит сохранённое.
    func save(_ login: String, _ snapshot: Snapshot) {
        guard let data = try? Self.encoder.encode(snapshot) else { return }
        try? data.write(to: file(login), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    private static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .millisecondsSince1970
        return e
    }()

    private static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .millisecondsSince1970
        return d
    }()
}
