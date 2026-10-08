package ru.wishka.memory.ui.components

import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.sizeTitle
import ru.wishka.memory.ui.theme.Palette
import java.io.File

/** Уменьшенная картинка вложения; читается не в главном потоке. null — пока читается или не удалось. */
@Composable
fun rememberThumbnail(attachment: Attachment, maxSide: Int = 720): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, attachment.path) {
        value = withContext(Dispatchers.IO) { MediaFiles.thumbnail(attachment, maxSide)?.asImageBitmap() }
    }
    return bitmap
}

/** Плитка фото или видео: картинка с обрезкой по рамке, у видео — значок «пуск». */
@Composable
fun MediaTile(attachment: Attachment, modifier: Modifier = Modifier, maxSide: Int = 720, onClick: (() -> Unit)? = null) {
    val thumb = rememberThumbnail(attachment, maxSide)
    Box(
        modifier = modifier
            .background(Palette.Plum.copy(alpha = 0.6f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (thumb != null) {
            Image(thumb, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Glyph(if (attachment.kind == MediaKind.Video) LineIcon.Video else LineIcon.Image, Palette.Mist)
        }
        if (attachment.kind == MediaKind.Video) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Palette.Void.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Glyph(LineIcon.Play, Palette.Ink, size = 22.dp) }
        }
    }
}

/**
 * Фото и видео записи. Одно — во всю ширину; несколько — листаются
 * вбок, с номером «2 / 5». Нажатие открывает на весь экран.
 */
@Composable
fun PostMedia(media: List<Attachment>, onOpen: (Attachment) -> Unit, modifier: Modifier = Modifier, height: Dp = 260.dp) {
    if (media.isEmpty()) return
    if (media.size == 1) {
        MediaTile(media[0], modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp)), onClick = { onOpen(media[0]) })
        return
    }
    val pager = rememberPagerState(pageCount = { media.size })
    Box(modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp))) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            MediaTile(media[page], Modifier.fillMaxSize(), onClick = { onOpen(media[page]) })
        }
        Text(
            "${pager.currentPage + 1} / ${media.size}",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Ink,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Palette.Void.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Документ: значок, имя и размер. Нажатие — открыть в подходящем приложении. */
@Composable
fun DocumentRow(attachment: Attachment, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Void.copy(alpha = 0.35f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(LineIcon.Document, Palette.Lilac, size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                attachment.name.ifEmpty { "Документ" },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (attachment.size > 0) {
                Text(sizeTitle(attachment.size), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
            }
        }
    }
}

/** Фото или видео на весь экран поверх приложения. */
@Composable
fun MediaViewer(attachment: Attachment, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            when (attachment.kind) {
                MediaKind.Video -> AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            val controller = MediaController(context)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setVideoURI(Uri.fromFile(File(attachment.path)))
                            setOnPreparedListener { start() }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> {
                    val image = rememberThumbnail(attachment, maxSide = 2048)
                    if (image != null) {
                        Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Palette.Void.copy(alpha = 0.6f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Glyph(LineIcon.Close, Palette.Ink) }
        }
    }
}

/**
 * Откуда взять вложение: галерея (фото и видео сразу, до 10), камера —
 * фото или видео, файлы — документ. Выбранное копируется в память
 * приложения (MediaFiles) и приходит в onPicked.
 */
class MediaPicker(
    val pickMedia: () -> Unit,
    val takePhoto: () -> Unit,
    val recordVideo: () -> Unit,
    val pickDocuments: () -> Unit,
)

@Composable
fun rememberMediaPicker(onPicked: (List<Attachment>) -> Unit): MediaPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Куда камера пишет снимок; переживает поворот экрана и уход в камеру
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    var captureKind by rememberSaveable { mutableStateOf(MediaKind.Photo) }

    fun importAll(uris: List<Uri>, kind: MediaKind?) {
        if (uris.isEmpty()) return
        scope.launch {
            val items = withContext(Dispatchers.IO) { uris.mapNotNull { MediaFiles.import(context, it, kind) } }
            if (items.size < uris.size) {
                Toast.makeText(context, "Не все файлы удалось добавить (до 200 МБ)", Toast.LENGTH_SHORT).show()
            }
            if (items.isNotEmpty()) onPicked(items)
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        importAll(uris, null)
    }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        importAll(uris, MediaKind.Document)
    }
    fun onCaptured(ok: Boolean) {
        val path = capturePath ?: return
        capturePath = null
        val file = File(path)
        val item = if (ok) MediaFiles.captured(file, captureKind) else null
        if (item != null) onPicked(listOf(item)) else file.delete()
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> onCaptured(ok) }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok -> onCaptured(ok) }

    fun launchCamera(kind: MediaKind) {
        val (file, uri) = MediaFiles.newCapture(context, kind)
        capturePath = file.absolutePath
        captureKind = kind
        try {
            if (kind == MediaKind.Video) video.launch(uri) else photo.launch(uri)
        } catch (e: Exception) {
            file.delete()
            capturePath = null
            Toast.makeText(context, "Камера недоступна", Toast.LENGTH_SHORT).show()
        }
    }

    return remember(gallery, documents, photo, video) {
        MediaPicker(
            pickMedia = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            takePhoto = { launchCamera(MediaKind.Photo) },
            recordVideo = { launchCamera(MediaKind.Video) },
            pickDocuments = { documents.launch(arrayOf("*/*")) },
        )
    }
}

/** Кнопка-скрепка с меню: галерея, фото, видео и (в чатах) документ. */
@Composable
fun AttachButton(picker: MediaPicker, documents: Boolean, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .glass(CircleShape)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) { Glyph(LineIcon.Attach, Palette.Lilac, size = 22.dp) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuRow(LineIcon.Image, "Фото или видео из галереи") { open = false; picker.pickMedia() }
            MenuRow(LineIcon.Camera, "Снять фото") { open = false; picker.takePhoto() }
            MenuRow(LineIcon.Video, "Снять видео") { open = false; picker.recordVideo() }
            if (documents) MenuRow(LineIcon.Document, "Документ") { open = false; picker.pickDocuments() }
        }
    }
}

@Composable
fun MenuRow(icon: LineIcon, title: String, color: Color = Palette.Ink, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(icon, color, size = 20.dp)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            }
        },
        onClick = onClick,
    )
}

/** Вложения, которые ещё не отправлены: маленькие плитки с крестиком. */
@Composable
fun PendingAttachments(items: List<Attachment>, onRemove: (Attachment) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            Box {
                if (item.kind == MediaKind.Document) {
                    DocumentRow(item, Modifier.width(180.dp).height(64.dp))
                } else {
                    MediaTile(item, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), maxSide = 256)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Palette.Void.copy(alpha = 0.75f))
                        .clickable { onRemove(item) },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcon.Close, Palette.Ink, size = 14.dp) }
            }
        }
    }
}
