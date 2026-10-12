import CoreImage
import UIKit
import Vision

/// Вырезать объект с фото — как «Создать стикер» в iOS 17. Vision находит
/// главный объект (человек, питомец, предмет) и отдаёт его без фона.
/// nil — объекта не нашлось; тогда студия предлагает обрезку формой.
/// На Android то же делает ML Kit (SubjectCutout.kt).
enum SubjectCutout {
    private static let ci = CIContext()

    /// Не в главном потоке: Vision думает заметное время.
    static func cut(_ source: UIImage) async -> UIImage? {
        await Task.detached(priority: .userInitiated) { SubjectCutout.cutNow(source) }.value
    }

    private static func cutNow(_ source: UIImage) -> UIImage? {
        guard let cg = upright(source).cgImage else { return nil }
        let handler = VNImageRequestHandler(cgImage: cg, options: [:])
        let request = VNGenerateForegroundInstanceMaskRequest()
        do {
            try handler.perform([request])
            guard let result = request.results?.first, !result.allInstances.isEmpty else { return nil }
            let buffer = try result.generateMaskedImage(ofInstances: result.allInstances, from: handler, croppedToInstancesExtent: true)
            let image = CIImage(cvPixelBuffer: buffer)
            guard let out = ci.createCGImage(image, from: image.extent) else { return nil }
            return StickerFx.fit(StickerFx.trim(UIImage(cgImage: out)))
        } catch {
            return nil
        }
    }

    /// Снимок с камеры бывает «повёрнут» флажком: рисуем его прямо, чтобы Vision видел то же, что человек.
    private static func upright(_ image: UIImage) -> UIImage {
        if image.imageOrientation == .up { return image }
        let format = UIGraphicsImageRendererFormat()
        format.scale = image.scale
        return UIGraphicsImageRenderer(size: image.size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
    }
}
