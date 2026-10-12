package ru.wishka.memory.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Где телефон — для клада, находки миссии и вылазки. Обходимся системным
 * LocationManager, без сервисов Google: работает и на телефонах без них.
 */
object Locator {
    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun permitted(context: Context): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Текущее место; null — нет разрешения, геолокация выключена или не успела за 10 с. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): LatLon? {
        if (!permitted(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        // Сначала сеть (быстро), потом GPS; на всё — не больше 10 с
        val fresh = withTimeoutOrNull(10_000) {
            providers.reversed().firstNotNullOfOrNull { provider -> fresh(context, lm, provider) }
        }
        if (fresh != null) return LatLon(fresh.latitude, fresh.longitude)
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        return last?.let { LatLon(it.latitude, it.longitude) }
    }

    /** Одно свежее место от провайдера: Android 11+ — getCurrentLocation, раньше — requestSingleUpdate. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun fresh(context: Context, lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= 30) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    lm.getCurrentLocation(provider, signal, context.mainExecutor) { if (cont.isActive) cont.resume(it) }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) { if (cont.isActive) cont.resume(location) }
                    override fun onProviderDisabled(provider: String) { if (cont.isActive) cont.resume(null) }
                    override fun onProviderEnabled(provider: String) {}
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }
                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
                runCatching {
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
}
