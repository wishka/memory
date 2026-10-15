package ru.wishka.memory

import android.app.Application
import ru.wishka.memory.data.DevicePrefs
import ru.wishka.memory.push.MemoryNotifications
import ru.wishka.memory.push.PushSetup

/**
 * Приложение: язык интерфейса, каналы уведомлений и пуши — до любого
 * экрана, в том числе когда процесс поднял пуш, а не человек.
 */
class MemoryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DevicePrefs(this).load()
        // Отчёты о сбоях и статистика — только если человек разрешил (по умолчанию нет)
        ru.wishka.memory.telemetry.TelemetrySetup.init(this)
        MemoryNotifications.createChannels(this)
        PushSetup.init(this)
    }
}
