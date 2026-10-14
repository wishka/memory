import SwiftUI
import UniformTypeIdentifiers

/// Что нужно карточке «Мои данные»: размер медиа и сама выгрузка.
/// То же — DataActions в MyData.kt.
struct DataActions {
    /// Имя архива по умолчанию.
    let fileName: () -> String
    /// Сколько займут фото, видео и музыка, байт.
    let mediaBytes: () async -> Int64
    /// Записать архив в файл; ответ — сколько файлов медиа вошло, nil — не вышло.
    let export: (_ to: URL, _ withMedia: Bool) async -> Int?
}

/// Готовый архив во временной папке — для системного «Сохранить в Файлы».
private struct ZipDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.zip] }

    let url: URL

    init(url: URL) {
        self.url = url
    }

    init(configuration: ReadConfiguration) throws {
        throw CocoaError(.fileReadUnsupportedScheme)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        try FileWrapper(url: url, options: .immediate)
    }
}

/// «Приватность → Мои данные»: архив со всем, что Memory хранит о вас, —
/// право на доступ к своим данным. Архив собирается во временный файл,
/// а куда его сохранить (Файлы, iCloud Drive, флешка), человек выбирает сам.
struct MyDataCard: View {
    let actions: DataActions

    @State private var withMedia = true
    @State private var busy = false
    @State private var bytes: Int64?
    @State private var document: ZipDocument?
    @State private var exporting = false
    @State private var mediaCount = 0
    @State private var message: String?

    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 10) {
                Text(t("Мои данные").uppercased())
                    .textStyle(.label, Palette.mist)
                    .accessibilityAddTraits(.isHeader)
                Text(t("Архив со всем, что Memory хранит о вас: профиль, записи, комментарии, переписка, группы, PRISM, полка, настройки и лента активности."))
                    .textStyle(.bodySmall)
                    .fixedSize(horizontal: false, vertical: true)
                Toggle(isOn: $withMedia) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(t("С фото, видео и музыкой")).textStyle(.body)
                        if let bytes {
                            Text(sizeTitle(bytes)).textStyle(.bodySmall, Palette.mist)
                        }
                    }
                }
                .tint(Palette.orchid)
                GlowButton(
                    title: busy ? t("Собираем архив…") : t("Скачать мои данные"),
                    enabled: !busy,
                    fullWidth: true,
                    action: build
                )
                if let message {
                    Text(message)
                        .textStyle(.bodySmall, Palette.lilac)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Text(t("Пароля и кодов восстановления в архиве нет: Memory хранит только их отпечатки."))
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(16)
        }
        .cardPadding()
        .task { bytes = await actions.mediaBytes() }
        .fileExporter(
            isPresented: $exporting,
            document: document,
            contentType: .zip,
            defaultFilename: document?.url.lastPathComponent
        ) { result in
            switch result {
            case .success:
                message = withMedia ? tf("Архив сохранён, файлов медиа: %s", mediaCount) : t("Архив сохранён")
            case .failure:
                message = t("Не удалось сохранить архив. Попробуйте другое место.")
            }
            cleanUp()
        }
        .onChange(of: exporting) { _, shown in
            // Закрыли окно сохранения, ничего не выбрав, — временный архив не нужен
            if !shown && document != nil {
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 500_000_000)
                    if !exporting { cleanUp() }
                }
            }
        }
    }

    /// Собрать архив во временный файл и предложить сохранить его.
    private func build() {
        guard !busy else { return }
        busy = true
        message = nil
        let target = FileManager.default.temporaryDirectory.appendingPathComponent(actions.fileName())
        try? FileManager.default.removeItem(at: target)
        let media = withMedia
        Task { @MainActor in
            let count = await actions.export(target, media)
            busy = false
            if let count {
                mediaCount = count
                document = ZipDocument(url: target)
                exporting = true
            } else {
                try? FileManager.default.removeItem(at: target)
                message = t("Не удалось сохранить архив. Попробуйте другое место.")
            }
        }
    }

    private func cleanUp() {
        if let url = document?.url { try? FileManager.default.removeItem(at: url) }
        document = nil
    }
}
