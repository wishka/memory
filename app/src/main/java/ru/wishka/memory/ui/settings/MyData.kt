package ru.wishka.memory.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.wishka.memory.data.DataExport
import ru.wishka.memory.data.sizeTitle
import ru.wishka.memory.i18n.t
import ru.wishka.memory.i18n.tf
import ru.wishka.memory.ui.components.GlassCard
import ru.wishka.memory.ui.components.GlowButton
import ru.wishka.memory.ui.theme.Palette

/** Что нужно карточке «Мои данные»: размер медиа и сама выгрузка. */
class DataActions(
    /** Имя архива по умолчанию. */
    val fileName: () -> String,
    /** Сколько займут фото, видео и музыка, байт. */
    val mediaBytes: suspend () -> Long,
    /** Записать архив по адресу, который выбрал человек; ответ — сколько файлов медиа вошло, null — не вышло. */
    val export: suspend (uri: android.net.Uri, withMedia: Boolean) -> Int?,
)

/**
 * «Приватность → Мои данные»: архив со всем, что Memory хранит о вас, —
 * право на доступ к своим данным. Человек сам выбирает, куда сохранить
 * (Файлы, Диск, флешка).
 */
@Composable
fun MyDataCard(actions: DataActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var withMedia by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var bytes by remember { mutableStateOf<Long?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { bytes = actions.mediaBytes() }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val count = actions.export(uri, withMedia)
            busy = false
            Toast.makeText(
                context,
                if (count == null) t("Не удалось сохранить архив. Попробуйте другое место.")
                else if (withMedia) tf("Архив сохранён, файлов медиа: %s", count) else t("Архив сохранён"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("Мои данные").uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mist)
            Text(
                t("Архив со всем, что Memory хранит о вас: профиль, записи, комментарии, переписка, группы, PRISM, полка, настройки и лента активности."),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Ink,
            )
            Row(
                modifier = Modifier.toggleable(value = withMedia, role = androidx.compose.ui.semantics.Role.Switch) { withMedia = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t("С фото, видео и музыкой"), style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                    bytes?.let { Text(sizeTitle(it), style = MaterialTheme.typography.bodyMedium, color = Palette.Mist) }
                }
                MemorySwitch(withMedia, onChange = null)
            }
            GlowButton(
                if (busy) t("Собираем архив…") else t("Скачать мои данные"),
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = { save.launch(actions.fileName()) },
            )
            Text(
                t("Пароля и кодов восстановления в архиве нет: Memory хранит только их отпечатки."),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Mist,
            )
        }
    }
}

/** Имя архива для текущего входа. */
fun exportFileName(login: String): String = DataExport.fileName(login, System.currentTimeMillis())
