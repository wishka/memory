import AVFoundation
import SwiftUI
import UIKit

/// Своя камера для эфира и видеозвонка: передняя или задняя; просит
/// разрешение на камеру и микрофон, если его нет. То же — CameraPreview.kt.
@Observable
final class LiveCameraModel {
    let session = AVCaptureSession()
    private(set) var allowed = true
    @ObservationIgnored private let queue = DispatchQueue(label: "memory.live.camera")
    /// Какая камера сейчас подключена: true — передняя.
    @ObservationIgnored private var front = true

    func start(front: Bool = true) {
        Task { @MainActor in
            let video = await AVCaptureDevice.requestAccess(for: .video)
            _ = await AVCaptureDevice.requestAccess(for: .audio)
            allowed = video
            guard video else { return }
            self.front = front
            let session = self.session
            queue.async {
                if session.inputs.isEmpty {
                    LiveCameraModel.attach(session, front: front)
                }
                if !session.isRunning { session.startRunning() }
            }
        }
    }

    /// Сменить камеру на ходу: передняя ↔ задняя.
    func use(front: Bool) {
        guard allowed, front != self.front else { return }
        self.front = front
        let session = self.session
        queue.async {
            session.beginConfiguration()
            session.inputs.forEach { session.removeInput($0) }
            session.commitConfiguration()
            LiveCameraModel.attach(session, front: front)
        }
    }

    func stop() {
        let session = self.session
        queue.async {
            if session.isRunning { session.stopRunning() }
        }
    }

    private static func attach(_ session: AVCaptureSession, front: Bool) {
        session.beginConfiguration()
        session.sessionPreset = .high
        if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: front ? .front : .back),
           let input = try? AVCaptureDeviceInput(device: device), session.canAddInput(input) {
            session.addInput(input)
        }
        session.commitConfiguration()
    }
}

/// Картинка своей камеры во весь размер рамки. Нет разрешения — подсказка.
struct CameraPreview: View {
    var front: Bool = true
    @State private var camera = LiveCameraModel()

    var body: some View {
        ZStack {
            Color.black
            if camera.allowed {
                CameraLayer(session: camera.session)
            } else {
                Text(t("Нет доступа к камере. Разрешите его в настройках телефона."))
                    .textStyle(.body, Palette.mist)
                    .multilineTextAlignment(.center)
                    .padding(32)
            }
        }
        .onAppear { camera.start(front: front) }
        .onDisappear { camera.stop() }
        .onChange(of: front) { _, now in camera.use(front: now) }
        .accessibilityHidden(true)
    }
}

/// Картинка с камеры (AVCaptureVideoPreviewLayer).
private struct CameraLayer: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        view.backgroundColor = .black
        return view
    }

    func updateUIView(_ view: PreviewView, context: Context) {}

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}
