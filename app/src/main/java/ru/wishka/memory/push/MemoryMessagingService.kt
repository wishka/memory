package ru.wishka.memory.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import ru.wishka.memory.data.Nudge
import ru.wishka.memory.data.NudgeKind
import ru.wishka.memory.data.NudgeTarget
import ru.wishka.memory.data.Nudges

/**
 * Приёмник пушей FCM. Сервер присылает факты (тип события, кто, что) —
 * текст складывается на телефоне тем же Nudges, что и для локальных
 * уведомлений: на языке интерфейса и с теми же словами.
 */
class MemoryMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushTokens.save(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val nudge = Nudges.fromPush(message.data)
            ?: message.notification?.let { n ->
                Nudge(NudgeKind.Friends, n.title ?: "Memory", n.body.orEmpty(), NudgeTarget.Post)
            }
            ?: return
        // Выключенный раздел уведомлений — молчим
        if (!Nudges.allowed(nudge, PushBus.settings(applicationContext))) return
        // Приложение открыто — плашкой внутри, свёрнуто — системным уведомлением
        if (AppVisibility.foreground && PushBus.incoming.tryEmit(nudge)) return
        MemoryNotifications.show(applicationContext, nudge)
    }
}
