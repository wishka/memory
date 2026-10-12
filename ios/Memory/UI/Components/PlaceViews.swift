import MapKit
import SwiftUI

extension SharedPlace: Identifiable {
    var id: String { "\(lat),\(lon),\(title)" }

    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: lat, longitude: lon) }

    func region(_ meters: Double) -> MapCameraPosition {
        .region(MKCoordinateRegion(center: coordinate, latitudinalMeters: meters, longitudinalMeters: meters))
    }
}

/// Место в переписке: маленькая карта с точкой и подпись. Нажатие —
/// карта на весь экран (сама карта в пузыре не листается, чтобы не мешать
/// прокрутке чата).
struct PlaceBubble: View {
    let place: SharedPlace
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 0) {
                Map(initialPosition: place.region(700), interactionModes: []) {
                    Marker(place.title.isEmpty ? t("Место") : place.title, coordinate: place.coordinate)
                        .tint(Palette.orchid)
                }
                .mapStyle(.standard(pointsOfInterest: .excludingAll))
                .environment(\.colorScheme, .dark)
                .allowsHitTesting(false)
                .frame(height: 140)
                HStack(spacing: 8) {
                    Glyph(kind: .pin, color: Palette.lilac, size: 18)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(place.title.isEmpty ? t("Геолокация") : place.title).textStyle(.body).lineLimit(1)
                        Text(place.coords).textStyle(.label, Palette.mist)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
            }
            .frame(width: 240)
            .background(Palette.voidRGB.opacity(0.35))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

/// Карта на весь экран: листается и масштабируется; «Открыть в Картах» — в приложении карт.
struct PlaceViewer: View {
    let place: SharedPlace
    let onClose: () -> Void

    var body: some View {
        ZStack(alignment: .top) {
            Map(initialPosition: place.region(500)) {
                Marker(place.title.isEmpty ? t("Место") : place.title, coordinate: place.coordinate)
                    .tint(Palette.orchid)
                UserAnnotation()
            }
            .mapStyle(.standard(pointsOfInterest: .excludingAll))
            .environment(\.colorScheme, .dark)
            .ignoresSafeArea()
            HStack(spacing: 10) {
                RoundGlyphButton(icon: .close, strong: true, action: onClose)
                VStack(alignment: .leading, spacing: 1) {
                    Text(place.title.isEmpty ? t("Геолокация") : place.title).textStyle(.title).lineLimit(1)
                    Text(place.coords).textStyle(.label, Palette.mist)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Palette.voidRGB.opacity(0.7)))
            }
            .padding(12)
            VStack {
                Spacer()
                GlowButton(title: t("Открыть в Картах"), fullWidth: true) { openInMaps() }
                    .padding(16)
            }
        }
        .background(Palette.void.ignoresSafeArea())
    }

    private func openInMaps() {
        let item = MKMapItem(placemark: MKPlacemark(coordinate: place.coordinate))
        item.name = place.title.isEmpty ? t("Место") : place.title
        item.openInMaps(launchOptions: [
            MKLaunchOptionsMapCenterKey: NSValue(mkCoordinate: place.coordinate),
            MKLaunchOptionsMapSpanKey: NSValue(mkCoordinateSpan: MKCoordinateSpan(latitudeDelta: 0.01, longitudeDelta: 0.01)),
        ])
    }
}
