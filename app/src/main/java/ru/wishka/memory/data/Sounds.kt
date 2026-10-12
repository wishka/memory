package ru.wishka.memory.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import ru.wishka.memory.i18n.t

/**
 * Звуки уведомлений в стиле Memory: стекло, звёзды, космос. Файлы —
 * res/raw/snd_*.wav (их рисует scripts/media_assets.py). У каждого вида
 * уведомлений свой звук; поменять — «Настройки → Уведомления → Звуки».
 * Те же звуки и правила — в Sounds.swift.
 */
enum class NotifySound(val id: String, private val ru: String) {
    Crystal("crystal", "Кристалл"),
    Stardust("stardust", "Звёздная пыль"),
    Comet("comet", "Комета"),
    Moon("moon", "Луна"),
    Prism("prism", "Призма"),
    Nebula("nebula", "Туманность"),
    Orbit("orbit", "Орбита"),
    Droplet("droplet", "Капля света"),
    System("system", "Как в телефоне"),
    Silent("silent", "Без звука"),
    ;

    val title: String get() = t(ru)

    /** Имя файла без расширения; null — звук телефона или тишина. */
    val file: String? get() = if (this == System || this == Silent) null else "snd_$id"

    companion object {
        fun of(id: String?): NotifySound? = entries.firstOrNull { it.id == id }

        /** Звук по умолчанию: у каждого вида — свой, чтобы различать на слух. */
        fun default(kind: NudgeKind): NotifySound = when (kind) {
            NudgeKind.Messages -> Crystal
            NudgeKind.Prism -> Prism
            NudgeKind.Reactions -> Stardust
            NudgeKind.Follows -> Orbit
            NudgeKind.Groups -> Moon
            NudgeKind.Friends -> Comet
            NudgeKind.Recap -> Nebula
        }
    }
}

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
        return if (res == 0) null else Uri.parse("android.resource://${context.packageName}/$res")
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
