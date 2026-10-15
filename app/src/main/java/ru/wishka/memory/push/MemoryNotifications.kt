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
import ru.wishka.memory.data.NotifySound
import ru.wishka.memory.data.SoundPlayer
import ru.wishka.memory.data.SoundPrefs
import android.media.AudioAttributes
import ru.wishka.memory.i18n.t

/** Открыто ли приложение на экране: тогда уведомление — плашкой внутри, а не системным. */
object AppVisibility {
    @Volatile
    var foreground = false
}

/**
 * Системные уведомления Memory: свой канал на каждый раздел (друзья,
 * группы, отметки, заявки в друзья, сообщения, итоги) — человек может выключить
 * любой из них в настройках телефона. Нажатие открывает приложение там,
 * о чём уведомление.
 */
object MemoryNotifications {
    private const val EXTRA_KIND = "memory.nudge.kind"
    private const val EXTRA_TITLE = "memory.nudge.title"
    private const val EXTRA_TEXT = "memory.nudge.text"
    private const val EXTRA_TARGET = "memory.nudge.target"
    private const val EXTRA_ID = "memory.nudge.id"

    /**
     * Канал = вид уведомления + звук: у канала Android звук задаётся один раз,
     * поэтому новый звук — новый канал (старый удаляем).
     */
    private fun channelId(kind: NudgeKind, sound: NotifySound) = "memory_" + kind.name.lowercase() + "_" + sound.id + "_" + SOUNDS_VERSION

    /** Версия набора звуков: звуки обновили — каналы пересоздаются с новыми файлами. */
    private const val SOUNDS_VERSION = "v2"

    private fun channelId(context: Context, kind: NudgeKind) = channelId(kind, SoundPrefs(context).get(kind))

    private fun channelName(kind: NudgeKind) = when (kind) {
        NudgeKind.Friends -> t("Друзья")
        NudgeKind.Groups -> t("Группы")
        NudgeKind.Reactions -> t("Лайки и комментарии")
        NudgeKind.Follows -> t("Заявки в друзья")
        NudgeKind.Messages -> t("Сообщения")
        NudgeKind.Recap -> t("Итоги")
        NudgeKind.Prism -> "PRISM"
    }

    /** Каналы — при запуске (Android 8+) и после смены звука: со своим звуком у каждого вида. */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val sounds = SoundPrefs(context)
        NudgeKind.entries.forEach { kind ->
            val sound = sounds.get(kind)
            val id = channelId(kind, sound)
            // Каналы этого вида с другим звуком (и старые, без звука в имени) — больше не нужны
            val prefix = "memory_" + kind.name.lowercase()
            manager.notificationChannels.filter { (it.id == prefix || it.id.startsWith(prefix + "_")) && it.id != id }
                .forEach { manager.deleteNotificationChannel(it.id) }
            val importance = when {
                sound == NotifySound.Silent -> NotificationManager.IMPORTANCE_LOW
                kind == NudgeKind.Messages -> NotificationManager.IMPORTANCE_HIGH
                else -> NotificationManager.IMPORTANCE_DEFAULT
            }
            val channel = NotificationChannel(id, channelName(kind), importance)
            when (sound) {
                NotifySound.System -> Unit
                NotifySound.Silent -> channel.setSound(null, null)
                else -> channel.setSound(
                    SoundPlayer.uri(context, sound),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
            }
            manager.createNotificationChannel(channel)
        }
        // Запасной канал для пушей FCM без своего канала — не удаляется при смене звуков.
        // Прежние версии (memory_default и т. п.) — со старым звуком, их убираем
        manager.notificationChannels.filter { it.id.startsWith("memory_default") && it.id != DEFAULT_CHANNEL }
            .forEach { manager.deleteNotificationChannel(it.id) }
        if (manager.getNotificationChannel(DEFAULT_CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(DEFAULT_CHANNEL, "Memory", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(
                    SoundPlayer.uri(context, NotifySound.Crystal),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
            })
        }
    }

    /** Канал по умолчанию для FCM (AndroidManifest: default_notification_channel_id). */
    const val DEFAULT_CHANNEL = "memory_default_v2"

    /** Сменили звук в настройках: запомнить и пересоздать канал. */
    fun changeSound(context: Context, kind: NudgeKind, sound: NotifySound) {
        SoundPrefs(context).set(kind, sound)
        createChannels(context)
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
        val sound = SoundPrefs(context).get(nudge.kind)
        val notification = NotificationCompat.Builder(context, channelId(context, nudge.kind))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF844CAD.toInt())
            .setContentTitle(nudge.title)
            .setContentText(nudge.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(nudge.text))
            .setContentIntent(pending)
            .setAutoCancel(true)
        // До Android 8 звук задаёт само уведомление, а не канал
        if (Build.VERSION.SDK_INT < 26) {
            val uri = SoundPlayer.uri(context, sound)
            if (uri != null) notification.setSound(uri) else if (sound == NotifySound.Silent) notification.setSilent(true)
        }
        NotificationManagerCompat.from(context).notify(code, notification.build())
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
