package ru.wishka.memory.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import ru.wishka.memory.i18n.t

/**
 * Звуки уведомлений Memory: 57 своих в девяти стилях (стекло, маримба,
 * пузыри, лоу-фай, синт, амбиент, 8-бит, минимал, природа), звук телефона
 * и тишина. Список — SoundCatalog.kt, файлы — res/raw/snd_*.ogg; всё это
 * собирает scripts/make_sounds.py из scripts/sounds/catalog.py.
 * У каждого вида уведомлений свой звук; поменять — «Настройки → Звуки».
 */

/** Выбор звуков — настройка телефона (каналы уведомлений тоже на телефоне). */
class SoundPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sounds", Context.MODE_PRIVATE)

    fun get(kind: NudgeKind): NotifySound = NotifySound.of(prefs.getString(kind.name, null)) ?: NotifySound.default(kind)

    fun set(kind: NudgeKind, sound: NotifySound) {
        prefs.edit().putString(kind.name, sound.id).apply()
    }

    fun all(): Map<NudgeKind, NotifySound> = NudgeKind.entries.associateWith { get(it) }
}

/** Проиграть звук: в выборе звука и для плашки, когда приложение открыто. */
object SoundPlayer {
    private var current: MediaPlayer? = null

    fun uri(context: Context, sound: NotifySound): Uri? {
        val file = sound.file ?: return if (sound == NotifySound.System) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else null
        val res = context.resources.getIdentifier(file, "raw", context.packageName)
        // По имени, а не по номеру ресурса: номер меняется при добавлении звуков, а канал его помнит
        return if (res == 0) null else Uri.parse("android.resource://${context.packageName}/raw/$file")
    }

    fun play(context: Context, sound: NotifySound) {
        val uri = uri(context, sound) ?: return
        runCatching {
            current?.release()
            current = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(context, uri)
                setOnCompletionListener { it.release(); if (current === it) current = null }
                prepare()
                start()
            }
        }
    }
}

/** Название вида уведомлений в выборе звуков. */
val NudgeKind.soundTitle: String
    get() = when (this) {
        NudgeKind.Messages -> t("Сообщения")
        NudgeKind.Prism -> t("PRISM друзей")
        NudgeKind.Reactions -> t("Лайки и комментарии")
        NudgeKind.Follows -> t("Заявки в друзья")
        NudgeKind.Groups -> t("Группы")
        NudgeKind.Friends -> t("Друзья")
        NudgeKind.Recap -> t("Итоги")
    }

/** Порядок в настройках: сначала самое частое. */
val SOUND_KINDS = listOf(NudgeKind.Messages, NudgeKind.Prism, NudgeKind.Reactions, NudgeKind.Follows, NudgeKind.Groups, NudgeKind.Friends, NudgeKind.Recap)
