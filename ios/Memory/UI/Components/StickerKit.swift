import SwiftUI
import UIKit

/// Свой стикер как вложение: копия PNG из «Студии». Копия — чтобы
/// сообщение или запись не потеряли картинку, если стикер потом удалят.
func stickerAttachment(_ sticker: Sticker) -> Attachment? {
    guard let data = try? Data(contentsOf: sticker.url),
          var attachment = MediaFiles.save(data, kind: .photo, ext: "png", name: "sticker.png") else { return nil }
    attachment.stickerFlag = true
    return attachment
}

/// Стикер-вложение без рамки и фона: прозрачная картинка как есть.
struct StickerView: View {
    let attachment: Attachment
    var size: CGFloat = 140
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            if let image { Image(uiImage: image).resizable().scaledToFit() }
        }
        .frame(width: size, height: size)
        .accessibilityLabel(t("Стикер"))
        .task(id: attachment.fileName) {
            let url = MediaFiles.url(attachment)
            image = await Task.detached { UIImage(contentsOfFile: url.path) }.value
        }
    }
}

/// Стикеры записи — рядком, крупно.
struct StickerRow: View {
    let stickers: [Attachment]
    var size: CGFloat = 120

    var body: some View {
        if !stickers.isEmpty {
            HStack(spacing: 8) {
                ForEach(stickers) { StickerView(attachment: $0, size: size) }
            }
        }
    }
}

/// Выбор своего стикера, смайла или значка из «Студии». Пусто — подсказка,
/// где их сделать. onPick получает уже скопированное вложение.
struct StickerPicker: View {
    let stickers: [Sticker]
    let onPick: (Attachment) -> Void
    var onOpenStudio: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(t("Мои стикеры")).textStyle(.headline)
            if stickers.isEmpty {
                Text(t("Стикеров пока нет. Сделайте значок, смайл или стикер из фото в «Студии» профиля."))
                    .textStyle(.bodySmall, Palette.mist)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 10) {
                    GhostButton(title: t("Закрыть")) { dismiss() }
                    if let onOpenStudio {
                        GlowButton(title: t("Открыть Студию")) {
                            dismiss()
                            onOpenStudio()
                        }
                    }
                }
                Spacer()
            } else {
                ScrollView {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 8) {
                        ForEach(stickers) { sticker in
                            Button {
                                if let attachment = stickerAttachment(sticker) { onPick(attachment) }
                                dismiss()
                            } label: {
                                StickerImage(sticker: sticker)
                                    .padding(10)
                                    .frame(maxWidth: .infinity)
                                    .aspectRatio(1, contentMode: .fit)
                                    .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Palette.plum.opacity(0.35)))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.night.ignoresSafeArea())
        .presentationDetents([.medium, .large])
    }
}
