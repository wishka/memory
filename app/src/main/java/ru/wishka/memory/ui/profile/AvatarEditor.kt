package ru.wishka.memory.ui.profile

import ru.wishka.memory.i18n.t
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.theme.Palette
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** Выбор фото для аватара: из галереи или камерой. Готовое фото — в onPicked (путь к копии). */
class AvatarPicker(val pickPhoto: () -> Unit, val takePhoto: () -> Unit)

@Composable
fun rememberAvatarPicker(onPicked: (String) -> Unit): AvatarPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val item = withContext(Dispatchers.IO) { MediaFiles.import(context, uri, MediaKind.Photo) }
            if (item != null) onPicked(item.path)
            else Toast.makeText(context, t("Не удалось открыть фото"), Toast.LENGTH_SHORT).show()
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = capturePath ?: return@rememberLauncherForActivityResult
        capturePath = null
        val item = if (ok) MediaFiles.captured(File(path), MediaKind.Photo) else null
        if (item != null) onPicked(item.path) else File(path).delete()
    }
    return remember(gallery, camera) {
        AvatarPicker(
            pickPhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            takePhoto = {
                val (file, uri) = MediaFiles.newCapture(context, MediaKind.Photo)
                capturePath = file.absolutePath
                try {
                    camera.launch(uri)
                } catch (e: Exception) {
                    file.delete()
                    capturePath = null
                    Toast.makeText(context, t("Камера недоступна"), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

/**
 * Обрезка аватара: фото под круглой рамкой, его можно двигать пальцем и
 * увеличивать щипком. «Готово» сохраняет квадрат 512 × 512 под кругом —
 * путь к нему приходит в onDone; «Отмена» — null. Исходное фото после
 * этого удаляется: оно было копией.
 */
@Composable
fun AvatarCropper(sourcePath: String, onDone: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    val bitmap by produceState<Bitmap?>(initialValue = null, sourcePath) {
        value = withContext(Dispatchers.IO) { decodeUpright(sourcePath, 2048) }
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var saving by remember { mutableStateOf(false) }
    // Размер области под фото: из него — диаметр круга (с полями по 24dp)
    var area by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(LocalDensity.current) { 48.dp.toPx() }
    val cropGeometry = bitmap?.let { b ->
        val circle = min(area.width, area.height) - margin
        if (circle > 0f) CropGeometry(b.width, b.height, circle) else null
    }

    fun finish(result: String?) {
        File(sourcePath).delete()
        onDone(result)
    }

    Dialog(
        onDismissRequest = { finish(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.Void)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Text(
                t("ПЕРЕМЕСТИТЕ И МАСШТАБИРУЙТЕ"),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Mist,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp, bottom = 12.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { area = it },
            ) {
                val bmp = bitmap
                val geometry = cropGeometry
                if (bmp != null && geometry != null) {
                    val image: ImageBitmap = remember(bmp) { bmp.asImageBitmap() }
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(geometry) {
                                detectTransformGestures { _, dragged, scaled, _ ->
                                    zoom = (zoom * scaled).coerceIn(1f, 5f)
                                    pan = geometry.clamp(pan + dragged, zoom)
                                }
                            },
                    ) {
                        val scale = geometry.scale(zoom)
                        val dw = bmp.width * scale
                        val dh = bmp.height * scale
                        val left = size.width / 2 + pan.x - dw / 2
                        val top = size.height / 2 + pan.y - dh / 2
                        drawImage(
                            image,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bmp.width, bmp.height),
                            dstOffset = IntOffset(left.toInt(), top.toInt()),
                            dstSize = IntSize(dw.toInt(), dh.toInt()),
                        )
                        // Затемнение вокруг круга и тонкий светлый контур
                        val circle = geometry.circle
                        val c = Offset(size.width / 2, size.height / 2)
                        val hole = Rect(c.x - circle / 2, c.y - circle / 2, c.x + circle / 2, c.y + circle / 2)
                        val mask = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(0f, 0f, size.width, size.height))
                            addOval(hole)
                        }
                        drawPath(mask, Color.Black.copy(alpha = 0.62f))
                        drawCircle(Palette.Lilac, circle / 2, c, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                GhostButton(t("Отмена"), onClick = { finish(null) })
                GlowButton(t("Готово"), enabled = bitmap != null && cropGeometry != null && !saving, onClick = {
                    val bmp = bitmap
                    val geometry = cropGeometry
                    if (bmp != null && geometry != null && !saving) {
                        saving = true
                        scope.launch {
                            val path = withContext(Dispatchers.IO) {
                                runCatching { geometry.cropTo(bmp, zoom, pan, File(sourcePath).parentFile!!) }.getOrNull()
                            }
                            saving = false
                            finish(path)
                        }
                    }
                })
            }
        }
    }
}

/**
 * Геометрия обрезки. Фото вписано так, что при zoom = 1 круг диаметром
 * circle закрыт целиком; pan — сдвиг центра фото от центра круга.
 */
class CropGeometry(private val bw: Int, private val bh: Int, val circle: Float) {
    /** Масштаб «пиксель фото → пиксель экрана». */
    fun scale(zoom: Float): Float = circle / min(bw, bh) * zoom

    /** Не даём сдвинуть фото так, чтобы в круге появилась пустота. */
    fun clamp(pan: Offset, zoom: Float): Offset {
        val s = scale(zoom)
        val maxX = max(0f, (bw * s - circle) / 2)
        val maxY = max(0f, (bh * s - circle) / 2)
        return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
    }

    /** Квадрат фото под кругом, в пикселях фото: x, y, сторона. */
    fun sourceSquare(zoom: Float, pan: Offset): Triple<Int, Int, Int> {
        val s = scale(zoom)
        val p = clamp(pan, zoom)
        // Левый верх круга относительно левого верха фото — в пикселях экрана
        val x = (bw * s - circle) / 2 - p.x
        val y = (bh * s - circle) / 2 - p.y
        val side = (circle / s).toInt().coerceIn(1, min(bw, bh))
        val sx = (x / s).toInt().coerceIn(0, bw - side)
        val sy = (y / s).toInt().coerceIn(0, bh - side)
        return Triple(sx, sy, side)
    }

    fun cropTo(bitmap: Bitmap, zoom: Float, pan: Offset, dir: File): String {
        val (x, y, side) = sourceSquare(zoom, pan)
        val square = Bitmap.createBitmap(bitmap, x, y, side, side)
        val out = if (side > AVATAR_PX) Bitmap.createScaledBitmap(square, AVATAR_PX, AVATAR_PX, true) else square
        val file = File(dir, "avatar-${UUID.randomUUID()}.jpg")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file.absolutePath
    }

    companion object {
        const val AVATAR_PX = 512
    }
}

/** Фото с прореживанием до maxSide и с поворотом по EXIF: снимок с камеры часто лежит боком. */
private fun decodeUpright(path: String, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val degrees = runCatching {
        when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (degrees == 0f) return raw
    val m = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
}
