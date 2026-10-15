package ru.wishka.memory.net

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.wishka.memory.data.DevicePrefs
import ru.wishka.memory.data.User
import ru.wishka.memory.data.auth.SecurePrefs
import java.util.concurrent.TimeUnit

/**
 * Отправка, пока приложение закрыто: догрузить фото и видео и отправить
 * очередь (WorkManager — запустит, когда есть сеть, и повторит при обрыве).
 * Когда приложение снова открыто — работу снимаем, дальше отправляет оно само.
 *
 * С демо-сервером не нужна: он живёт только внутри открытого приложения.
 */
class NetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (NetSetup.usesDemo) return Result.success()
        val prefs = DevicePrefs(applicationContext)
        val login = prefs.lastLogin ?: return Result.success()
        val me = User(prefs.lastMeId, prefs.lastMeName, login)
        val net = NetSetup.forAccount(
            applicationContext, login, { me }, System::currentTimeMillis,
            quality = { prefs.mediaQuality }, unmetered = { false },
            tokens = SecurePrefs(applicationContext),
        )
        try {
            repeat(MAX_ROUNDS) {
                if (!net.busy) return Result.success()
                val r = net.pump(maxUploads = 5)
                r.uploads.filter { it.state == ru.wishka.memory.data.net.UploadState.Done }.forEach(MediaCompressor::cleanup)
                if (r.authLost) return Result.failure()
                if (r.offline) return Result.retry()
            }
            return if (net.busy) Result.retry() else Result.success()
        } finally {
            (net.cache as? SqliteCache)?.close()
        }
    }

    companion object {
        private const val NAME = "memory-net"
        private const val MAX_ROUNDS = 40

        /** Приложение ушло в фон: досылать, когда будет сеть. */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<NetWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            runCatching { WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request) }
        }

        /** Приложение снова открыто — отправляет само. */
        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(NAME) }
        }
    }
}
