package ru.wishka.memory.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.Post
import ru.wishka.memory.data.Tags
import ru.wishka.memory.data.User
import ru.wishka.memory.ui.theme.Palette

/**
 * Что делают ссылки в тексте: #тег — поиск, @логин — страница человека,
 * адрес — ссылка Memory открывается в приложении, остальные — в браузере.
 * Плюс действия с записью из меню «⋯» любой карточки: ссылка и правка.
 * Даётся один раз в MainScreen — не нужно протаскивать через каждый экран.
 */
class LinkActions(
    val meId: Int,
    val onTag: (String) -> Unit,
    val onMention: (String) -> Unit,
    val onUrl: (String) -> Unit,
    /** Кого подсказать после «@». */
    val suggest: (String) -> List<User> = { emptyList() },
    /** «Поделиться ссылкой» на запись. */
    val onShareLink: (Post) -> Unit = {},
    /** «Скопировать ссылку». */
    val onCopyLink: (Post) -> Unit = {},
    /** «Изменить» свою запись. */
    val onEdit: (Post) -> Unit = {},
)

val LocalLinkActions = staticCompositionLocalOf<LinkActions?> { null }

/**
 * Текст, в котором #теги, @упоминания и ссылки подсвечены и нажимаются.
 * Без LinkActions (превью, тесты) — обычный текст.
 */
@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Palette.Ink,
    maxLines: Int = Int.MAX_VALUE,
) {
    val actions = LocalLinkActions.current
    val spans = remember(text) { Tags.spans(text) }
    if (actions == null || spans.isEmpty()) {
        Text(text, style = style, color = color, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        return
    }
    val annotated = remember(text, spans) {
        buildAnnotatedString {
            var pos = 0
            for (s in spans) {
                if (s.start > pos) append(text.substring(pos, s.start))
                val part = text.substring(s.start, s.end)
                withLink(
                    LinkAnnotation.Clickable(
                        tag = s.kind.name + ":" + s.value,
                        styles = TextLinkStyles(SpanStyle(color = Palette.Lilac)),
                        linkInteractionListener = {
                            when (s.kind) {
                                Tags.Kind.Tag -> actions.onTag(s.value)
                                Tags.Kind.Mention -> actions.onMention(s.value)
                                Tags.Kind.Url -> actions.onUrl(s.value)
                            }
                        },
                    ),
                ) { append(part) }
                pos = s.end
            }
            if (pos < text.length) append(text.substring(pos))
        }
    }
    Text(annotated, style = style, color = color, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/**
 * Подсказки под полем ввода: человек набирает «@ве» — ниже кнопки с
 * людьми; нажатие дописывает логин. Пусто — ничего не показывается.
 */
@Composable
fun MentionSuggestions(text: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val actions = LocalLinkActions.current ?: return
    val typing = remember(text) { Tags.typingMention(text) } ?: return
    val people = remember(typing.second) { actions.suggest(typing.second) }
    if (people.isEmpty()) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        people.forEach { u ->
            Row(
                modifier = Modifier
                    .glass(RoundedCornerShape(50))
                    .clickable(onClickLabel = "@" + u.handle, role = Role.Button) {
                        onChange(Tags.completeMention(text, typing.first, text.length, u.handle))
                    }
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(u, size = 26.dp)
                Spacer(Modifier.width(6.dp))
                Text("@" + u.handle, style = MaterialTheme.typography.labelLarge, color = Palette.Lilac)
            }
        }
    }
}
