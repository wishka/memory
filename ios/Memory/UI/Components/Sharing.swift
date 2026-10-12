import SwiftUI
import UIKit

/// Что отдать системному «Поделиться»: текст-приглашение со ссылкой и,
/// может быть, карточка-картинка. То же — в Sharing.kt.
struct SharePayload: Identifiable {
    let id = UUID()
    let items: [Any]
}

/// Поделиться карточкой-картинкой: рисуем её с вашим кодом приглашения,
/// добавляем текст-приглашение со ссылкой, привязанной к действию. В
/// журнал роста — что отправили.
func shareCard(_ model: AppModel, _ spec: ShareCardSpec, name: String, action: InviteAction,
               targetId: String = "", title: String = "") -> SharePayload {
    var card = spec
    card.inviteCode = model.state.inviteCode
    let image = ShareCard.render(card)
    let text = model.invite(action, targetId: targetId, title: title)
    model.logCardShared(name)
    return SharePayload(items: [text, image])
}

/// Позвать того, кого ещё нет в Memory: только текст со ссылкой.
func shareInvite(_ model: AppModel, _ action: InviteAction, targetId: String = "", title: String = "") -> SharePayload {
    SharePayload(items: [model.invite(action, targetId: targetId, title: title)])
}

/// Системное меню «Поделиться». Без разрешения на «Фото» пункт
/// «Сохранить изображение» скрыт: iOS закрыла бы приложение.
struct ActivitySheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        if !RecapImage.canSaveToPhotos { controller.excludedActivityTypes = [.saveToCameraRoll] }
        return controller
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

extension View {
    /// Показать «Поделиться», когда payload не nil.
    func shareSheet(_ payload: Binding<SharePayload?>) -> some View {
        sheet(item: payload) { item in
            ActivitySheet(items: item.items)
        }
    }
}
