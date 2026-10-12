package ru.wishka.memory.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import ru.wishka.memory.data.LatLon

/** Точка на карте: значок-эмодзи в кружке. mine — своя (сиреневый ободок). */
data class MapPin(val id: String, val at: LatLon, val title: String, val emoji: String = "📍", val mine: Boolean = false)

/**
 * Карта OpenStreetMap (osmdroid) — без ключей и сервисов Google. Тёмная:
 * плитки с инвертированными цветами, как всё приложение. me — где вы.
 */
@Composable
fun OsmMap(pins: List<MapPin>, center: LatLon, me: LatLon?, modifier: Modifier = Modifier, zoom: Double = 14.5, onPin: (String) -> Unit = {}) {
    val context = LocalContext.current
    val map = remember {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERTED_COLORS)
            controller.setZoom(zoom)
            controller.setCenter(GeoPoint(center.lat, center.lon))
            // Подпись «© OpenStreetMap contributors» — условие использования плиток OSM
            overlays.add(CopyrightOverlay(context).apply { setTextColor(0xFFBFC3E0.toInt()) })
        }
    }
    // Плитки грузятся, только пока экран на виду: onResume/onPause — вслед за экраном
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(map, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); map.onPause(); map.onDetach() }
    }
    // Центр — при создании и когда он сменился (узнали, где вы); иначе не мешаем двигать карту
    val shownCenter = remember { arrayOf(center) }
    AndroidView(factory = { map }, modifier = modifier, update = { view ->
        if (shownCenter[0] != center) {
            shownCenter[0] = center
            view.controller.setCenter(GeoPoint(center.lat, center.lon))
        }
        view.overlays.removeAll { it is Marker }
        pins.forEach { pin ->
            view.overlays.add(Marker(view).apply {
                position = GeoPoint(pin.at.lat, pin.at.lon)
                title = pin.title
                icon = BitmapDrawable(view.resources, pinBitmap(pin.emoji, if (pin.mine) 0xFFBC92CD.toInt() else 0xFF844CAD.toInt(), view.resources.displayMetrics.density))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> onPin(pin.id); true }
            })
        }
        me?.let {
            view.overlays.add(Marker(view).apply {
                position = GeoPoint(it.lat, it.lon)
                title = "•"
                icon = BitmapDrawable(view.resources, pinBitmap("", 0xFF8CC4FF.toInt(), view.resources.displayMetrics.density, small = true))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> true }
            })
        }
        view.invalidate()
    })
}

/** Кружок с эмодзи для маркера. */
private fun pinBitmap(emoji: String, ring: Int, density: Float, small: Boolean = false): Bitmap {
    val side = ((if (small) 18 else 36) * density).toInt().coerceAtLeast(8)
    val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = ring
    c.drawCircle(side / 2f, side / 2f, side / 2f, p)
    p.color = 0xFF0F153A.toInt()
    c.drawCircle(side / 2f, side / 2f, side / 2f - 2.5f * density, p)
    if (emoji.isNotEmpty()) {
        p.textSize = side * 0.5f
        p.textAlign = Paint.Align.CENTER
        c.drawText(emoji, side / 2f, side / 2f - (p.ascent() + p.descent()) / 2, p)
    }
    return bmp
}
