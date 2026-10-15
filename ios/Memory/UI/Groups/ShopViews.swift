import PhotosUI
import SwiftUI

/// Витрина группы: карточки товаров, метки категорий, сведения о группе,
/// товар во весь экран и редактор товара. Те же экраны — ShopViews.kt.

extension GroupAccent {
    /// Цвет группы как Color.
    var color: Color { rgb.color }

    func opacity(_ a: Double) -> Color { rgb.opacity(a) }

    /// Текст на кнопке в цвете группы: на тёмной орхидее — светлый, на остальных — тёмный.
    var onColor: Color { self == .orchid ? Palette.ink : Palette.void }

    /// Цвет группы для текста на тёмном фоне: орхидея светлее, чтобы читалась.
    var textColor: Color { self == .orchid ? RGB(0xC9A4EC).color : color }
}

/// Кнопка в цвете группы: под стиль магазина.
struct AccentButton: View {
    let title: String
    let accent: GroupAccent
    var enabled: Bool = true
    var fullWidth: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title.uppercased())
                .textStyle(.label, enabled ? accent.onColor : Palette.mist)
                .lineLimit(1)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .frame(maxWidth: fullWidth ? .infinity : nil)
                .background(
                    Capsule().fill(enabled
                        ? LinearGradient(colors: [accent.color, accent.opacity(0.7)], startPoint: .leading, endPoint: .trailing)
                        : LinearGradient(colors: [Palette.plum, Palette.plum], startPoint: .leading, endPoint: .trailing))
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// Вкладка «Записи» / «Витрина» в цвете группы.
struct TabPill: View {
    let text: String
    let active: Bool
    let accent: GroupAccent
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .textStyle(.labelLarge, active ? accent.onColor : accent.textColor)
                .lineLimit(1)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background { if active { Capsule().fill(accent.color) } }
                .modifier(GlassUnless(active: active))
        }
        .buttonStyle(.plain)
    }
}

/// Стекло под неактивной вкладкой; у активной — сплошной цвет.
private struct GlassUnless: ViewModifier {
    let active: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if active {
            content.clipShape(Capsule())
        } else {
            content.glass(Capsule())
        }
    }
}

/// Фото товара или туманность, пока фото нет.
struct ProductImage: View {
    let photo: Attachment?
    let seed: Int
    var maxPixel: CGFloat = 720

    var body: some View {
        if let photo, photo.kind == .photo {
            MediaTile(attachment: photo, maxPixel: maxPixel)
        } else {
            Nebula(seed: seed)
        }
    }
}

/// Карточка товара в витрине: фото, название, цена; нет в наличии — приглушено.
struct ProductCard: View {
    let product: Product
    let accent: GroupAccent
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 0) {
                Color.clear
                    .aspectRatio(1, contentMode: .fit)
                    .overlay { ProductImage(photo: product.photos.first, seed: product.seed) }
                    .clipped()
                    .overlay(alignment: .topLeading) {
                        if !product.inStock {
                            Text(t("Нет в наличии"))
                                .textStyle(.label)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(Palette.voidRGB.opacity(0.7)))
                                .padding(8)
                        }
                    }
                VStack(alignment: .leading, spacing: 2) {
                    Text(product.title)
                        .textStyle(.body)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    Text(product.priceTitle)
                        .textStyle(.title, product.inStock ? accent.textColor : Palette.mist)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
            }
            .glass(radius: 18)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Метки категорий витрины; nil — все. Меньше двух категорий — не показываются.
struct CategoryChips: View {
    let categories: [String]
    let selected: String?
    let accent: GroupAccent
    let onSelect: (String?) -> Void

    var body: some View {
        if categories.count >= 2 {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    pill(nil)
                    ForEach(categories, id: \.self) { pill($0) }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private func pill(_ category: String?) -> some View {
        CategoryPill(text: category ?? t("Все"), active: category == selected, accent: accent) { onSelect(category) }
    }
}

/// Метка категории в цвете группы.
struct CategoryPill: View {
    let text: String
    let active: Bool
    let accent: GroupAccent
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .textStyle(.labelLarge, active ? accent.onColor : accent.textColor)
                .lineLimit(1)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background {
                    if active {
                        Capsule().fill(accent.color)
                    } else {
                        Capsule().strokeBorder(accent.opacity(0.6), lineWidth: 1)
                    }
                }
        }
        .buttonStyle(.plain)
    }
}

/// Подробно о группе: описание, сайт, телефон, почта, адрес и часы работы.
/// Длинное описание сворачивается; сайт, телефон, почта и адрес открываются.
struct GroupInfoCard: View {
    let info: GroupInfo
    let accent: GroupAccent
    @Environment(\.openURL) private var openURL
    @State private var expanded = false
    @State private var failed = false

    var body: some View {
        if !info.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                if !info.about.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text(info.about)
                        .textStyle(.bodySmall)
                        .lineLimit(expanded ? nil : 4)
                        .fixedSize(horizontal: false, vertical: true)
                        .contentShape(Rectangle())
                        .onTapGesture { withAnimation(.easeOut(duration: 0.2)) { expanded.toggle() } }
                }
                if !blank(info.website) {
                    line(.globe, displayUrl(info.website), accent: accent) { open(normalizeUrl(info.website)) }
                }
                if !blank(info.phone) {
                    line(.chats, info.phone, accent: accent) { open("tel:" + info.phone.filter { $0.isNumber || $0 == "+" }) }
                }
                if !blank(info.email) {
                    line(.send, info.email, accent: accent) { open("mailto:" + info.email.trimmingCharacters(in: .whitespaces)) }
                }
                if !blank(info.address) {
                    line(.pin, info.address, accent: nil) {
                        let q = info.address.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
                        open("http://maps.apple.com/?q=" + q)
                    }
                }
                if !blank(info.hours) {
                    line(.calendar, info.hours, accent: nil, action: nil)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glass(radius: 18)
            .alert(t("Нечем открыть ссылку"), isPresented: $failed) {
                Button(t("Понятно"), role: .cancel) {}
            }
        }
    }

    private func blank(_ s: String) -> Bool { s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    private func displayUrl(_ s: String) -> String {
        var v = s
        for prefix in ["https://", "http://"] where v.hasPrefix(prefix) { v.removeFirst(prefix.count) }
        return v
    }

    private func open(_ link: String) {
        guard let url = URL(string: link) else {
            failed = true
            return
        }
        openURL(url) { accepted in
            if !accepted { failed = true }
        }
    }

    @ViewBuilder
    private func line(_ icon: LineIcon, _ text: String, accent: GroupAccent?, action: (() -> Void)?) -> some View {
        let row = HStack(spacing: 10) {
            Glyph(kind: icon, color: accent?.textColor ?? Palette.lilac, size: 18)
            Text(text)
                .textStyle(.bodySmall, accent?.textColor ?? Palette.ink)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
            Spacer(minLength: 0)
        }
        .padding(.vertical, 2)
        .contentShape(Rectangle())
        if let action {
            Button(action: action) { row }.buttonStyle(.plain)
        } else {
            row
        }
    }
}

/// Товар во весь экран: фото (листаются, увеличиваются пальцами), цена,
/// наличие, описание. Покупатель пишет продавцу или идёт на сайт; создатель
/// группы — меняет или убирает товар.
struct ProductSheet: View {
    let product: Product
    let accent: GroupAccent
    let mine: Bool
    let onClose: () -> Void
    /// «Написать продавцу»; nil — некому писать.
    let onAsk: (() -> Void)?
    let onEdit: () -> Void
    let onDelete: () -> Void
    let onOpenPhoto: (Attachment) -> Void
    /// Переставить в витрине: -1 — выше, +1 — ниже; nil — некуда.
    var onMove: ((Int) -> Void)? = nil
    /// Почему написать нельзя (продавец заблокирован); nil — можно.
    var askBlockedReason: String? = nil
    @Environment(\.openURL) private var openURL
    @State private var confirmDelete = false
    @State private var notice: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                RoundGlyphButton(icon: .close, action: onClose)
                Text(product.title).textStyle(.title).lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if !product.photos.isEmpty {
                        PostMediaView(media: product.photos, height: 340, onOpen: onOpenPhoto)
                            .padding(.horizontal, 16)
                    } else {
                        Nebula(seed: product.seed)
                            .frame(height: 260)
                            .frame(maxWidth: .infinity)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .padding(.horizontal, 16)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text(product.title).textStyle(.headline).fixedSize(horizontal: false, vertical: true)
                        Text(product.priceTitle).textStyle(.headline, accent.textColor)
                        HStack(spacing: 12) {
                            Text(product.inStock ? t("В наличии") : t("Нет в наличии"))
                                .textStyle(.labelLarge, product.inStock ? RGB(0x7BE0B0).color : Palette.mist)
                            if !product.category.trimmingCharacters(in: .whitespaces).isEmpty {
                                Text(product.category).textStyle(.labelLarge, Palette.mist)
                            }
                        }
                        if !product.description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            Text(product.description).textStyle(.body).fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(.horizontal, 20)
                }
                .padding(.vertical, 8)
            }

            VStack(alignment: .leading, spacing: 10) {
                if mine {
                    HStack(spacing: 10) {
                        AccentButton(title: t("Изменить"), accent: accent, fullWidth: true, action: onEdit)
                        GhostButton(title: t("Убрать")) { confirmDelete = true }
                    }
                    if let onMove {
                        HStack(spacing: 10) {
                            GhostButton(title: t("Выше в витрине")) { onMove(-1) }
                            GhostButton(title: t("Ниже")) { onMove(1) }
                        }
                    }
                } else {
                    if let askBlockedReason {
                        Text(askBlockedReason)
                            .textStyle(.bodySmall, Palette.mist)
                            .fixedSize(horizontal: false, vertical: true)
                    } else if let onAsk {
                        AccentButton(title: t("Написать продавцу"), accent: accent, fullWidth: true, action: onAsk)
                    }
                    if !product.url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        GhostButton(title: t("Купить на сайте")) { openSite() }
                            .frame(maxWidth: .infinity)
                    }
                    Text(t("Оплата и доставка — по договорённости с продавцом. Memory не принимает платежи."))
                        .textStyle(.label, Palette.mist)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(16)
        }
        .background(Palette.void.ignoresSafeArea())
        .glassActions(
            $confirmDelete,
            title: t("Убрать товар из витрины?"),
            cancel: true,
            actions: [GlassAction(icon: .trash, title: t("Убрать"), destructive: true, action: onDelete)]
        )
        .toast($notice)
    }

    private func openSite() {
        guard let url = URL(string: normalizeUrl(product.url)) else {
            notice = t("Нечем открыть ссылку")
            return
        }
        openURL(url) { accepted in
            if !accepted { notice = t("Нечем открыть ссылку") }
        }
    }
}

/// Новый товар или правка: фото (до 5), название, цена, категория,
/// описание, наличие, ссылка на сайт. onSave отвечает текстом ошибки или nil.
struct ProductEditor: View {
    let initial: Product?
    let categories: [String]
    let accent: GroupAccent
    let onSave: (Product) -> String?
    let onClose: () -> Void

    @State private var photos: [Attachment]
    @State private var title: String
    @State private var price: String
    @State private var category: String
    @State private var description: String
    @State private var inStock: Bool
    @State private var url: String
    @State private var error: String?
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var loading = false
    /// Фото, добавленные в этом редакторе: если товар не сохранили — их копии удаляются.
    @State private var added: Set<String> = []
    @State private var saved = false

    init(initial: Product?, categories: [String], accent: GroupAccent,
         onSave: @escaping (Product) -> String?, onClose: @escaping () -> Void) {
        self.initial = initial
        self.categories = categories
        self.accent = accent
        self.onSave = onSave
        self.onClose = onClose
        _photos = State(initialValue: initial?.photos ?? [])
        _title = State(initialValue: initial?.title ?? "")
        _price = State(initialValue: (initial?.price ?? 0) > 0 ? String(initial?.price ?? 0) : "")
        _category = State(initialValue: initial?.category ?? "")
        _description = State(initialValue: initial?.description ?? "")
        _inStock = State(initialValue: initial?.inStock ?? true)
        _url = State(initialValue: initial?.url ?? "")
    }

    private var remaining: Int { max(0, AppModel.maxProductPhotos - photos.count) }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                RoundGlyphButton(icon: .close) { close() }
                Text(initial == nil ? t("Новый товар") : t("Товар")).textStyle(.headline).lineLimit(1)
                Spacer(minLength: 0)
                AccentButton(title: t("Сохранить"), accent: accent,
                             enabled: !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) { save() }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    PendingAttachments(items: photos) { item in
                        photos.removeAll { $0.id == item.id }
                        if added.contains(item.id) {
                            added.remove(item.id)
                            MediaFiles.delete(item)
                        }
                    }
                    if remaining > 0 {
                        PhotosPicker(selection: $pickerItems, maxSelectionCount: remaining, matching: .images) {
                            HStack(spacing: 8) {
                                if loading { ProgressView().tint(Palette.lilac) }
                                Text((photos.isEmpty ? t("Добавить фото") : t("Ещё фото")).uppercased())
                                    .textStyle(.label, Palette.lilac)
                                    .lineLimit(1)
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 10)
                            .glass(Capsule())
                        }
                        .disabled(loading)
                    }
                    field(t("Название"), $title, limit: 80) { error = nil }
                    field(t("Цена, ₽ — пусто: по запросу"), $price, limit: 14, keyboard: .numberPad) { error = nil }
                    field(t("Категория — например, «Призмы»"), $category, limit: 40)
                    if !categories.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(categories, id: \.self) { c in
                                    CategoryPill(text: c, active: c == category, accent: accent) { category = c }
                                }
                            }
                        }
                    }
                    GlassField(placeholder: t("Описание: размер, материал, доставка"), text: $description, limit: 2000, maxLines: 8)
                        .glass(radius: 16)
                    HStack {
                        Text(t("В наличии")).textStyle(.body)
                            .accessibilityHidden(true)
                        Spacer()
                        Toggle("", isOn: $inStock)
                            .labelsHidden()
                            .accessibilityLabel(t("В наличии"))
                            .tint(accent.color)
                    }
                    field(t("Ссылка на товар на сайте — необязательно"), $url, limit: 300, keyboard: .URL, plain: true)
                    if let error {
                        Text(error).textStyle(.bodySmall, Palette.error)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(Palette.void.ignoresSafeArea())
        .onChange(of: pickerItems) { _, items in
            guard !items.isEmpty else { return }
            pickerItems = []
            load(items)
        }
    }

    private func field(_ placeholder: String, _ text: Binding<String>, limit: Int, keyboard: UIKeyboardType = .default,
                       plain: Bool = false, onEdit: (() -> Void)? = nil) -> some View {
        GlassField(placeholder: placeholder, text: text, limit: limit, singleLine: true, keyboard: keyboard, plain: plain)
            .glass(radius: 16)
            .onChange(of: text.wrappedValue) { _, _ in onEdit?() }
    }

    private func load(_ items: [PhotosPickerItem]) {
        loading = true
        let limit = remaining
        Task {
            var picked: [Attachment] = []
            for item in items.prefix(limit) {
                guard let data = try? await item.loadTransferable(type: Data.self) else { continue }
                let ext = item.supportedContentTypes.first?.preferredFilenameExtension ?? "jpg"
                let copy = await Task.detached(priority: .userInitiated) { MediaFiles.save(data, kind: .photo, ext: ext) }.value
                if let copy { picked.append(copy) }
            }
            loading = false
            let room = max(0, AppModel.maxProductPhotos - photos.count)
            let kept = Array(picked.prefix(room))
            // Лишние (выбрали больше, чем помещается) — сразу удалить копии
            picked.dropFirst(room).forEach { MediaFiles.delete($0) }
            photos += kept
            added.formUnion(kept.map(\.id))
        }
    }

    private func save() {
        guard let parsed = parsePrice(price) else {
            error = t("Цена — числом, например 1290")
            return
        }
        var product = initial ?? Product(id: 0, title: "")
        product.title = title
        product.price = parsed
        product.category = category
        product.description = description
        product.inStock = inStock
        product.url = url
        product.photos = photos
        error = onSave(product)
        if error == nil {
            saved = true
            // Убранные из своего товара фото — удалить (на свои товары никто не ссылается)
            let kept = Set(photos.map(\.id))
            (initial?.photos ?? []).filter { !kept.contains($0.id) }.forEach { MediaFiles.delete($0) }
            onClose()
        }
    }

    /// Закрыть без сохранения: добавленные копии фото больше не нужны.
    private func close() {
        if !saved {
            photos.filter { added.contains($0.id) }.forEach { MediaFiles.delete($0) }
        }
        onClose()
    }
}

/// Товар в сообщении: фото, название, цена. Нажатие — открыть товар в группе.
struct ProductBubble: View {
    let ref: ProductRef
    let onOpen: () -> Void

    var body: some View {
        let photo: Attachment? = ref.photo.isEmpty ? nil : Attachment(id: "p\(ref.productId)", kind: .photo, fileName: ref.photo)
        Button(action: onOpen) {
            HStack(spacing: 10) {
                ProductImage(photo: photo, seed: ref.seed, maxPixel: 200)
                    .frame(width: 56, height: 56)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(ref.title).textStyle(.body).lineLimit(2).multilineTextAlignment(.leading)
                    Text(priceTitle(ref.price)).textStyle(.labelLarge, Palette.lilac).lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .padding(8)
            .frame(width: 250)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Palette.voidRGB.opacity(0.35)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
