package ru.wishka.memory.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.theme.Palette

/**
 * Своя камера для эфира и видеозвонка: передняя или задняя, просит
 * разрешение на камеру и микрофон, если его нет.
 */
@Composable
fun CameraPreview(modifier: Modifier, front: Boolean = true) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        allowed = r[Manifest.permission.CAMERA] == true || granted(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) {
        if (!allowed || !granted(Manifest.permission.RECORD_AUDIO)) ask.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    LaunchedEffect(front) { controller.cameraSelector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA }
    LaunchedEffect(allowed) { if (allowed) controller.bindToLifecycle(owner) }
    DisposableEffect(Unit) { onDispose { controller.unbind() } }
    Box(modifier) {
        if (allowed) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            })
        } else {
            Text(
                t("Нет доступа к камере. Разрешите его в настройках телефона."),
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Mist,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }
    }
}

