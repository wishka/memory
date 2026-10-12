package ru.wishka.memory.ui.groups

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.wishka.memory.data.Attachment
import ru.wishka.memory.data.GroupAccent
import ru.wishka.memory.data.GroupInfo
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.data.MediaKind
import ru.wishka.memory.data.normalizeUrl
import ru.wishka.memory.data.Product
import ru.wishka.memory.data.ProductRef
import ru.wishka.memory.data.parsePrice
import ru.wishka.memory.data.priceTitle
import ru.wishka.memory.i18n.t
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.components.GhostButton
import ru.wishka.memory.ui.components.GlassField
import ru.wishka.memory.ui.components.Glyph
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.MediaTile
import ru.wishka.memory.ui.components.Nebula
import ru.wishka.memory.ui.components.PendingAttachments
import ru.wishka.memory.ui.components.PostMedia
import ru.wishka.memory.ui.components.glass
import ru.wishka.memory.ui.components.rememberMediaPicker
import ru.wishka.memory.ui.settings.MemorySwitch
import ru.wishka.memory.ui.theme.Palette

/** Цвет группы как Color. */
val GroupAccent.color: Color get() = Color(argb)

/** Текст на кнопке в цвете группы: на тёмной орхидее — светлый, на остальных — тёмный. */
val GroupAccent.onColor: Color get() = if (this == GroupAccent.Orchid) Palette.Ink else Palette.Void

/** Цвет группы для текста на тёмном фоне: орхидея светлее, чтобы читалась. */
val GroupAccent.textColor: Color get() = if (this == GroupAccent.Orchid) Color(0xFFC9A4EC) else color

/** Кнопка в цвете группы: под стиль магазина. */
@Composable
fun AccentButton(text: String, accent: GroupAccent, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) Brush.linearGradient(listOf(accent.color, accent.color.copy(alpha = 0.7f))) else Brush.linearGradient(listOf(Palette.Plum, Palette.Plum)))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) accent.onColor else Palette.Mist, maxLines = 1)
    }
}

/** Фото товара или туманность, пока фото нет. */
@Composable
fun ProductImage(photo: Attachment?, seed: Int, modifier: Modifier) {
    if (photo != null && photo.kind == MediaKind.Photo) MediaTile(photo, modifier, maxSide = 720)
    else Nebula(seed, modifier)
}

/** Карточка товара в витрине: фото, название, цена; нет в наличии — приглушено. */
@Composable
fun ProductCard(product: Product, accent: GroupAccent, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .glass(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Box {
            ProductImage(product.photos.firstOrNull(), product.seed, Modifier.fillMaxWidth().aspectRatio(1f))
            if (!product.inStock) {
                Text(
                    t("Нет в наличии"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Ink,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(8.dp))
                        .background(Palette.Void.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(product.title, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(product.priceTitle, style = MaterialTheme.typography.titleMedium, color = if (product.inStock) accent.textColor else Palette.Mist)
        }
    }
}

/** Метки категорий витрины; null — все. */
@Composable
fun CategoryChips(categories: List<String>, selected: String?, accent: GroupAccent, onSelect: (String?) -> Unit) {
    if (categories.size < 2) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (listOf<String?>(null) + categories).forEach { c ->
            val active = c == selected
            Text(
                c ?: t("Все"),
                style = MaterialTheme.typography.labelLarge,
                color = if (active) accent.onColor else accent.textColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .then(if (active) Modifier.background(accent.color) else Modifier.border(1.dp, accent.color.copy(alpha = 0.6f), RoundedCornerShape(50)))
                    .clickable { onSelect(c) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Подробно о группе: описание, сайт, телефон, почта, адрес и часы работы.
 * Длинное описание сворачивается.
 */
@Composable
fun GroupInfoCard(info: GroupInfo, accent: GroupAccent, modifier: Modifier = Modifier) {
    if (info.isEmpty) return
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    fun open(uri: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
            .onFailure { Toast.makeText(context, t("Нечем открыть ссылку"), Toast.LENGTH_SHORT).show() }
    }
    Column(modifier.glass(RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (info.about.isNotBlank()) {
            Text(
                info.about,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Ink,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
        if (info.website.isNotBlank()) InfoLine(LineIcon.Globe, info.website.removePrefix("https://").removePrefix("http://"), accent) { open(normalizeUrl(info.website)) }
        if (info.phone.isNotBlank()) InfoLine(LineIcon.Chats, info.phone, accent) { open("tel:" + info.phone.filter { it.isDigit() || it == '+' }) }
        if (info.email.isNotBlank()) InfoLine(LineIcon.Send, info.email, accent) { open("mailto:" + info.email) }
        if (info.address.isNotBlank()) InfoLine(LineIcon.Pin, info.address, null) { open("geo:0,0?q=" + Uri.encode(info.address)) }
        if (info.hours.isNotBlank()) InfoLine(LineIcon.Calendar, info.hours, null, null)
    }
}

@Composable
private fun InfoLine(icon: LineIcon, text: String, accent: GroupAccent?, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, accent?.textColor ?: Palette.Lilac, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (accent != null) accent.textColor else Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Товар во весь экран: фото (листаются, увеличиваются пальцами), цена,
 * наличие, описание. Покупатель пишет продавцу или идёт на сайт; создатель
 * группы — меняет или убирает товар.
 */
@Composable
fun ProductSheet(
    product: Product,
    accent: GroupAccent,
    mine: Boolean,
    onClose: () -> Unit,
    onAsk: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenPhoto: (Attachment) -> Unit,
    /** Переставить в витрине: -1 — выше, +1 — ниже; null — некуда. */
    onMove: ((Int) -> Unit)? = null,
    /** Почему написать нельзя (продавец заблокирован); null — можно. */
    askBlockedReason: String? = null,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Palette.Void).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) { Glyph(LineIcon.Close, Palette.Ink) }
                Spacer(Modifier.width(8.dp))
                Text(product.title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (product.photos.isNotEmpty()) {
                    PostMedia(product.photos, onOpen = onOpenPhoto, modifier = Modifier.padding(horizontal = 16.dp), height = 340.dp)
                } else {
                    Nebula(product.seed, Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)))
                }
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(product.title, style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
                    Text(product.priceTitle, style = MaterialTheme.typography.headlineSmall, color = accent.textColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (product.inStock) t("В наличии") else t("Нет в наличии"),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (product.inStock) Color(0xFF7BE0B0) else Palette.Mist,
                        )
                        if (product.category.isNotBlank()) Text(product.category, style = MaterialTheme.typography.labelLarge, color = Palette.Mist)
                    }
                    if (product.description.isNotBlank()) Text(product.description, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
                }
            }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mine) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccentButton(t("Изменить"), accent, Modifier.weight(1f), onClick = onEdit)
                        GhostButton(t("Убрать"), onClick = { confirmDelete = true })
                    }
                    if (onMove != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(t("Выше в витрине"), onClick = { onMove(-1) })
                        GhostButton(t("Ниже"), onClick = { onMove(1) })
                    }
                } else {
                    if (askBlockedReason != null) Text(askBlockedReason, style = MaterialTheme.typography.bodyMedium, color = Palette.Mist)
                    else if (onAsk != null) AccentButton(t("Написать продавцу"), accent, Modifier.fillMaxWidth(), onClick = onAsk)
                    if (product.url.isNotBlank()) GhostButton(t("Купить на сайте"), modifier = Modifier.fillMaxWidth(), onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalizeUrl(product.url)))) }
                            .onFailure { Toast.makeText(context, t("Нечем открыть ссылку"), Toast.LENGTH_SHORT).show() }
                    })
                    Text(
                        t("Оплата и доставка — по договорённости с продавцом. Memory не принимает платежи."),
                        style = MaterialTheme.typography.labelSmall, color = Palette.Mist,
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Palette.Night,
            title = { Text(t("Убрать товар из витрины?"), style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(t("Убрать"), color = Palette.PearlPink) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Отмена"), color = Palette.Mist) } },
        )
    }
}

/**
 * Новый товар или правка: фото (до 5), название, цена, категория,
 * описание, наличие, ссылка на сайт. onSave отвечает текстом ошибки или null.
 */
@Composable
fun ProductEditor(
    initial: Product?,
    categories: List<String>,
    accent: GroupAccent,
    onSave: (Product) -> String?,
    onClose: () -> Unit,
) {
    var photos by remember { mutableStateOf(initial?.photos.orEmpty()) }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var price by remember { mutableStateOf(initial?.price?.takeIf { it > 0 }?.toString().orEmpty()) }
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var inStock by remember { mutableStateOf(initial?.inStock ?: true) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberMediaPicker { picked ->
        val fit = (photos + picked.filter { it.kind == MediaKind.Photo }).take(AppViewModel.MAX_PRODUCT_PHOTOS)
        // Видео и лишнее сверх пяти — не нужны
        picked.filter { it !in fit }.forEach { MediaFiles.delete(it) }
        photos = fit
    }
    val field = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp))
    val before = initial?.photos.orEmpty()
    // Закрыли без сохранения — новые копии фото больше не нужны
    val cancel = {
        (photos - before.toSet()).forEach { MediaFiles.delete(it) }
        onClose()
    }
    Dialog(onDismissRequest = cancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Palette.Void).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = cancel), contentAlignment = Alignment.Center) { Glyph(LineIcon.Close, Palette.Ink) }
                Spacer(Modifier.width(8.dp))
                Text(if (initial == null) t("Новый товар") else t("Товар"), style = MaterialTheme.typography.titleLarge, color = Palette.Ink, modifier = Modifier.weight(1f))
                AccentButton(t("Сохранить"), accent, enabled = title.isNotBlank()) {
                    val parsed = parsePrice(price)
                    if (parsed == null) {
                        error = t("Цена — числом, например 1290")
                        return@AccentButton
                    }
                    val product = (initial ?: Product(0, "")).copy(
                        title = title, price = parsed, category = category, description = description,
                        inStock = inStock, url = url, photos = photos,
                    )
                    error = onSave(product)
                    if (error == null) {
                        // Убранные из своего товара фото — удалить (на свои товары никто не ссылается)
                        (before - photos.toSet()).forEach { MediaFiles.delete(it) }
                        onClose()
                    }
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PendingAttachments(photos, onRemove = { removed ->
                    photos = photos - removed
                    if (removed !in before) MediaFiles.delete(removed)
                })
                if (photos.size < AppViewModel.MAX_PRODUCT_PHOTOS) {
                    GhostButton(if (photos.isEmpty()) t("Добавить фото") else t("Ещё фото"), onClick = { picker.pickMedia() })
                }
                GlassField(title, { title = it.take(80); error = null }, t("Название"), singleLine = true, modifier = field)
                GlassField(price, { price = it.take(14); error = null }, t("Цена, ₽ — пусто: по запросу"), singleLine = true, keyboardType = KeyboardType.Number, modifier = field)
                GlassField(category, { category = it.take(40) }, t("Категория — например, «Призмы»"), singleLine = true, modifier = field)
                if (categories.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { c -> CategoryPill(c, c == category, accent) { category = c } }
                    }
                }
                GlassField(description, { description = it.take(2000) }, t("Описание: размер, материал, доставка"), maxLines = 8, modifier = field)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("В наличии"), style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, modifier = Modifier.weight(1f))
                    MemorySwitch(inStock) { inStock = it }
                }
                GlassField(url, { url = it.take(300) }, t("Ссылка на товар на сайте — необязательно"), singleLine = true, keyboardType = KeyboardType.Uri, modifier = field)
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun CategoryPill(text: String, active: Boolean, accent: GroupAccent, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) accent.onColor else accent.textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (active) Modifier.background(accent.color) else Modifier.border(1.dp, accent.color.copy(alpha = 0.6f), RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Товар в сообщении: фото, название, цена. Нажатие — открыть товар в группе. */
@Composable
fun ProductBubble(ref: ProductRef, onOpen: () -> Unit) {
    val photo = remember(ref.photo) { if (ref.photo.isEmpty()) null else Attachment("p" + ref.productId, MediaKind.Photo, ref.photo) }
    Row(
        Modifier.width(250.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Void.copy(alpha = 0.35f)).clickable(onClick = onOpen).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductImage(photo, ref.seed, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(ref.title, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(priceTitle(ref.price), style = MaterialTheme.typography.labelLarge, color = Palette.Lilac)
        }
    }
}
