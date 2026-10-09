package ru.wishka.memory.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File

/**
 * «Моя музыка» на телефоне: выбранный файл копируется в память
 * приложения (как вложения), из него читаются название, исполнитель и
 * длительность. Треки видит и слушает только владелец: это личная
 * библиотека, а не публикация.
 */
object MusicLibrary {
    /** Скопировать трек; null — файл не читается, это не аудио или он больше 200 МБ. */
    fun import(context: Context, uri: Uri, now: Long): MusicTrack? {
        val copy = MediaFiles.import(context, uri, MediaKind.Document) ?: return null
        val tags = runCatching {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(copy.path)
                Triple(
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                )
            } finally {
                r.release()
            }
        }.getOrNull()
        if (tags == null || tags.third <= 0) {
            File(copy.path).delete()
            return null
        }
        val fallback = copy.name.substringBeforeLast('.').ifBlank { copy.name }
        return MusicTrack(
            id = copy.id,
            title = tags.first.ifBlank { fallback }.take(120),
            artist = tags.second.take(120),
            path = copy.path,
            durationMs = tags.third,
            addedAt = now,
        )
    }

    fun delete(track: MusicTrack) {
        runCatching { File(track.path).delete() }
    }
}
