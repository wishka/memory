package ru.wishka.memory.push

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import ru.wishka.memory.data.Notifications
import ru.wishka.memory.data.Nudge
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import ru.wishka.memory.BuildConfig

/**
 * Пуши с сервера через Firebase Cloud Messaging. Ключи проекта Firebase
 * приходят при сборке (переменные FIREBASE_* или firebase.* в
 * local.properties — см. docs/PUSH.md). Без ключей FCM не включается:
 * приложение работает с локальными уведомлениями.
 */
object PushSetup {
    private const val TAG = "MemoryPush"

    val configured: Boolean
        get() = BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() && BuildConfig.FIREBASE_SENDER_ID.isNotBlank()

    fun init(context: Context) {
        if (!configured) return
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true
            messaging.token.addOnSuccessListener { token -> PushTokens.save(context, token) }
        }.onFailure { Log.w(TAG, "FCM не запустился", it) }
    }
}

/**
 * Токен устройства для пушей. Пока сервера нет, он только хранится на
 * телефоне; когда сервер появится — уйдёт ему вместе с языком
 * интерфейса (см. docs/PUSH.md).
 */
object PushTokens {
    private const val PREFS = "push"

    fun save(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("fcm", token).apply()
    }

    fun current(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("fcm", null)
}

/**
 * Мост между службой пушей и экраном: пока приложение открыто, пуш уходит
 * плашкой; выключатели уведомлений хранятся и здесь — служба работает без
 * экрана и без загруженного аккаунта.
 */
object PushBus {
    val incoming = MutableSharedFlow<Nudge>(extraBufferCapacity = 8)

    private const val PREFS = "push"

    fun saveSettings(context: Context, n: Notifications) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("all", n.all).putBoolean("messages", n.messages).putBoolean("reactions", n.reactions)
            .putBoolean("groups", n.groups).putBoolean("follows", n.follows).putBoolean("friends", n.friends)
            .putBoolean("recap", n.recap)
            .apply()
    }

    fun settings(context: Context): Notifications {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Notifications(
            all = p.getBoolean("all", true), messages = p.getBoolean("messages", true),
            reactions = p.getBoolean("reactions", true), groups = p.getBoolean("groups", true),
            follows = p.getBoolean("follows", true), friends = p.getBoolean("friends", true),
            recap = p.getBoolean("recap", true),
        )
    }
}
