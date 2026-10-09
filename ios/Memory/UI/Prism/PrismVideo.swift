import AVFoundation
import SwiftUI
import UIKit

/// Слой видео для SwiftUI: AVPlayerLayer с нужным заполнением кадра.
struct PlayerLayerView: UIViewRepresentable {
    let player: AVPlayer
    var gravity: AVLayerVideoGravity = .resizeAspectFill

    func makeUIView(context: Context) -> PlayerUIView {
        let view = PlayerUIView()
        view.playerLayer.player = player
        view.playerLayer.videoGravity = gravity
        view.backgroundColor = .black
        return view
    }

    func updateUIView(_ view: PlayerUIView, context: Context) {
        if view.playerLayer.player !== player { view.playerLayer.player = player }
        view.playerLayer.videoGravity = gravity
    }

    final class PlayerUIView: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    }
}

/// Плеер по кругу: доиграл — сначала. Ход — для полосы сверху.
@Observable
final class LoopingPlayer {
    let player = AVPlayer()
    private(set) var position: Double = 0
    private(set) var duration: Double = 1

    @ObservationIgnored private var endObserver: NSObjectProtocol?
    @ObservationIgnored private var timeObserver: Any?

    /// Ход отслеживается с первого видео: пустой плеер, созданный при
    /// перерисовке экрана и сразу выброшенный, ничего не стоит.
    private func observeTime() {
        if timeObserver != nil { return }
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(value: 1, timescale: 20), queue: .main) { [weak self] time in
            guard let self else { return }
            self.position = CMTimeGetSeconds(time)
            if let d = self.player.currentItem?.duration, d.isNumeric, CMTimeGetSeconds(d) > 0 {
                self.duration = CMTimeGetSeconds(d)
            }
        }
    }

    deinit {
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
    }

    /// Поставить новое видео; играет, если playing.
    func load(_ item: AVPlayerItem, playing: Bool, keepPosition: Bool = false) {
        observeTime()
        let at = player.currentTime()
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        player.replaceCurrentItem(with: item)
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main
        ) { [weak self] _ in
            self?.player.seek(to: .zero)
            self?.player.play()
        }
        if keepPosition { player.seek(to: at, toleranceBefore: .zero, toleranceAfter: .zero) }
        if playing { player.play() }
    }

    func set(playing: Bool) {
        if playing { player.play() } else { player.pause() }
    }
}

/// Свой ролик PRISM: видео по кругу во весь экран (с обрезкой по краям).
/// Играет, только пока ролик на экране и не на паузе.
struct PrismVideo: View {
    let url: URL
    let playing: Bool
    var onProgress: (Double) -> Void = { _ in }

    @State private var looper = LoopingPlayer()

    var body: some View {
        PlayerLayerView(player: looper.player)
            .onAppear {
                if looper.player.currentItem == nil {
                    looper.load(AVPlayerItem(url: url), playing: playing)
                } else {
                    looper.set(playing: playing)
                }
            }
            .onDisappear { looper.set(playing: false) }
            .onChange(of: playing) { _, now in looper.set(playing: now) }
            .onChange(of: looper.position) { _, p in onProgress(min(1, p / max(looper.duration, 0.001))) }
    }
}
