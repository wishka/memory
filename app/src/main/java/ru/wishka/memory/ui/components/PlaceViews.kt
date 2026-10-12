package ru.wishka.memory.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ru.wishka.memory.data.LatLon
import ru.wishka.memory.data.Locator
import ru.wishka.memory.data.SharedPlace
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.theme.Palette
import java.util.Locale

/** Координаты для подписи: 55.75580, 37.61730. */
fun SharedPlace.coords(): String = String.format(Locale.US, "%.5f, %.5f", lat, lon)

/**
 * Место в переписке: маленькая карта с точкой и подпись. Нажатие —
 * карта на весь экран (касание проходит мимо самой карты — она в пузыре
 * не листается, чтобы не мешать прокрутке чата).
 */
@Composable
fun PlaceBubble(place: SharedPlace, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Column(
        modifier
            .width(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Void.copy(alpha = 0.35f)),
    ) {
        Box(Modifier.fillMaxWidth().height(140.dp)) {
            OsmMap(
                pins = listOf(MapPin("place", place.at, place.title.ifEmpty { t("Место") })),
                center = place.at,
                me = null,
                modifier = Modifier.fillMaxSize(),
                zoom = 15.0,
            )
            // Прозрачная крышка: касание — открыть карту, а не двигать её
            Box(Modifier.fillMaxSize().clickable(onClick = onOpen))
        }
        Row(Modifier.clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(LineIcon.Pin, Palette.Lilac, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(place.title.ifEmpty { t("Геолокация") }, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(place.coords(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
            }
        }
    }
}

/** Карта на весь экран: листается и масштабируется; «Открыть в Картах» — в любом навигаторе. */
@Composable
fun PlaceViewer(place: SharedPlace, onClose: () -> Unit, me: LatLon? = null) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Palette.Void)) {
            OsmMap(
                pins = listOf(MapPin("place", place.at, place.title.ifEmpty { t("Место") })),
                center = place.at,
                me = me,
                modifier = Modifier.fillMaxSize(),
                zoom = 16.0,
            )
            Row(
                Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Palette.Void.copy(alpha = 0.7f)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Close, Palette.Ink) }
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Palette.Void.copy(alpha = 0.7f)).padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(place.title.ifEmpty { t("Геолокация") }, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(place.coords(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlowButton(t("Открыть в Картах"), modifier = Modifier.fillMaxWidth(), onClick = {
                    val label = Uri.encode(place.title.ifEmpty { t("Место") })
                    val uri = Uri.parse("geo:${place.lat},${place.lon}?q=${place.lat},${place.lon}($label)")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        .onFailure { Toast.makeText(context, t("Нет приложения с картами"), Toast.LENGTH_SHORT).show() }
                })
            }
        }
    }
}

/**
 * Узнать, где я, — с вопросом о разрешении, если его ещё нет.
 * onDone(null) — не разрешили или телефон не нашёл себя.
 */
class LocateRequest(val busy: Boolean, val locate: (onDone: (LatLon?) -> Unit) -> Unit)

@Composable
fun rememberLocate(): LocateRequest {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<((LatLon?) -> Unit)?>(null) }
    fun fetch(onDone: (LatLon?) -> Unit) {
        busy = true
        scope.launch {
            val at = Locator.current(context)
            busy = false
            if (at == null) Toast.makeText(context, t("Не удалось определить, где вы. Включите геолокацию."), Toast.LENGTH_SHORT).show()
            onDone(at)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val done = pending ?: {}
        pending = null
        if (result.values.any { it }) fetch(done) else done(null)
    }
    return LocateRequest(busy) { onDone ->
        if (Locator.permitted(context)) fetch(onDone)
        else {
            pending = onDone
            permission.launch(Locator.PERMISSIONS)
        }
    }
}
