package ru.wishka.memory.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.wishka.memory.MainActivity
import ru.wishka.memory.R
import ru.wishka.memory.data.Nudge
import ru.wishka.memory.data.NudgeKind
import ru.wishka.memory.data.NudgeTarget
import ru.wishka.memory.i18n.t

/** Открыто ли приложение на экране: тогда уведомление — плашкой внутри, а не системным. */
object AppVisibility {
    @Volatile
    var foreground = false
}

/**
 * Системные уведомления Memory: свой канал на каждый раздел (друзья,
 * группы, отметки, подписки, сообщения, итоги) — человек может выключить
 * любой из них в настройках телефона. Нажатие открывает приложение там,
 * о чём уведомление.
 */
object MemoryNotifications {
    private const val EXTRA_KIND = "memory.nudge.kind"
    private const val EXTRA_TITLE = "memory.nudge.title"
    private const val EXTRA_TEXT = "memory.nudge.text"
    private const val EXTRA_TARGET = "memory.nudge.target"
    private const val EXTRA_ID = "memory.nudge.id"

    private fun channelId(kind: NudgeKind) = "memory_" + kind.name.lowercase()

    private fun channelName(kind: NudgeKind) = when (kind) {
        NudgeKind.Friends -> t("Друзья")
        NudgeKind.Groups -> t("Группы")
        NudgeKind.Reactions -> t("Лайки и комментарии")
        NudgeKind.Follows -> t("Подписки")
        NudgeKind.Messages -> t("Сообщения")
        NudgeKind.Recap -> t("Итоги")
    }

    /** Каналы — один раз при запуске (Android 8+). */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        NudgeKind.entries.forEach { kind ->
            val importance = if (kind == NudgeKind.Messages) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            manager.createNotificationChannel(NotificationChannel(channelId(kind), channelName(kind), importance))
        }
    }

    /** Можно ли показывать: на Android 13+ нужно разрешение. */
    fun permitted(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission")
    fun show(context: Context, nudge: Nudge) {
        if (!permitted(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_KIND, nudge.kind.name)
            .putExtra(EXTRA_TITLE, nudge.title)
            .putExtra(EXTRA_TEXT, nudge.text)
            .putExtra(EXTRA_TARGET, nudge.target.name)
            .putExtra(EXTRA_ID, nudge.targetId)
        val code = (nudge.text.hashCode() and 0x7fffffff)
        val pending = PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId(nudge.kind))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF844CAD.toInt())
            .setContentTitle(nudge.title)
            .setContentText(nudge.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(nudge.text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(code, notification)
    }

    /** Уведомление, которым открыли приложение; null — открыли не им. */
    fun fromIntent(intent: Intent?): Nudge? {
        val target = intent?.getStringExtra(EXTRA_TARGET) ?: return null
        return Nudge(
            NudgeKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) } ?: NudgeKind.Friends,
            intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            intent.getStringExtra(EXTRA_TEXT).orEmpty(),
            NudgeTarget.entries.firstOrNull { it.name == target } ?: return null,
            intent.getLongExtra(EXTRA_ID, 0),
        )
    }
}
