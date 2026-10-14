import SwiftUI
import UIKit

/// Что делают ссылки в тексте: #тег — поиск, @логин — страница человека,
/// адрес — ссылка Memory открывается в приложении, остальные — в браузере.
/// Плюс действия с записью из меню «⋯» любой карточки: ссылка и правка.
/// Даётся один раз в MainScreen через окружение — не нужно протаскивать
/// через каждый экран. То же — LinkActions в RichText.kt.
struct LinkActions {
    var meId: Int
    var onTag: (String) -> Void
    var onMention: (String) -> Void
    var onUrl: (String) -> Void
    /// Кого подсказать после «@».
    var suggest: (String) -> [User] = { _ in [] }
    /// «Поделиться ссылкой» на запись.
    var onShareLink: (Post) -> Void = { _ in }
    /// «Скопировать ссылку».
    var onCopyLink: (Post) -> Void = { _ in }
    /// «Изменить» свою запись.
    var onEdit: (Post) -> Void = { _ in }
    /// «Репост» — своя лента, можно со своими словами.
    var onRepost: (Post) -> Void = { _ in }
    /// «Отправить в чат».
    var onSendToChat: (Post) -> Void = { _ in }
    /// Нажали на оригинал внутри репоста.
    var onOpenRef: (PostRef) -> Void = { _ in }
    /// Страница человека — из отметок на фото.
    var onPerson: (Int) -> Void = { _ in }
    /// Убрать отметку с собой на фото записи.
    var onUntagMe: (Post) -> Void = { _ in }
}

private struct LinkActionsKey: EnvironmentKey {
    static let defaultValue: LinkActions? = nil
}

extension EnvironmentValues {
    /// Ссылки в тексте и действия с записью; nil — превью и тесты: обычный текст.
    var linkActions: LinkActions? {
        get { self[LinkActionsKey.self] }
        set { self[LinkActionsKey.self] = newValue }
    }
}

/// Текст, в котором #теги, @упоминания и ссылки подсвечены и нажимаются.
/// Ссылки внутри — свои адреса (memory-tag://, memory-mention://,
/// memory-url://): их перехватывает openURL этого же текста.
/// Без LinkActions — обычный текст.
struct RichText: View {
    let text: String
    var style: TextStyle = .body
    var color: Color = Palette.ink
    var lineLimit: Int? = nil

    @Environment(\.linkActions) private var actions

    var body: some View {
        let spans = Tags.spans(text)
        if let actions, !spans.isEmpty {
            Text(Self.attributed(text, spans))
                .textStyle(style, color)
                .tint(Palette.lilac)
                .lineLimit(lineLimit)
                .environment(\.openURL, OpenURLAction { url in
                    Self.handle(url, actions)
                })
        } else {
            Text(text)
                .textStyle(style, color)
                .lineLimit(lineLimit)
        }
    }

    private static let tagPrefix = "memory-tag://t/"
    private static let mentionPrefix = "memory-mention://m/"
    private static let urlPrefix = "memory-url://u/"
    private static let safe = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-")

    private static func link(_ prefix: String, _ value: String) -> URL? {
        guard let encoded = value.addingPercentEncoding(withAllowedCharacters: safe) else { return nil }
        return URL(string: prefix + encoded)
    }

    /// Текст со ссылками; позиции span — в UTF-16, как в NSString.
    static func attributed(_ text: String, _ spans: [Tags.Span]) -> AttributedString {
        let ns = text as NSString
        var out = AttributedString()
        var pos = 0
        for s in spans {
            guard s.start >= pos, s.end <= ns.length, s.end > s.start else { continue }
            if s.start > pos {
                out += AttributedString(ns.substring(with: NSRange(location: pos, length: s.start - pos)))
            }
            var part = AttributedString(ns.substring(with: NSRange(location: s.start, length: s.end - s.start)))
            let url: URL?
            switch s.kind {
            case .tag: url = link(tagPrefix, s.value)
            case .mention: url = link(mentionPrefix, s.value)
            case .url: url = link(urlPrefix, s.value)
            }
            if let url { part.link = url }
            out += part
            pos = s.end
        }
        if pos < ns.length {
            out += AttributedString(ns.substring(from: pos))
        }
        return out
    }

    private static func value(_ s: String, _ prefix: String) -> String? {
        guard s.hasPrefix(prefix) else { return nil }
        let raw = String(s.dropFirst(prefix.count))
        return raw.removingPercentEncoding ?? raw
    }

    private static func handle(_ url: URL, _ actions: LinkActions) -> OpenURLAction.Result {
        let s = url.absoluteString
        if let v = value(s, tagPrefix) {
            actions.onTag(v)
        } else if let v = value(s, mentionPrefix) {
            actions.onMention(v)
        } else if let v = value(s, urlPrefix) {
            actions.onUrl(v)
        } else {
            actions.onUrl(s)
        }
        return .handled
    }
}

/// Подсказки под полем ввода: человек набирает «@ве» — ниже кнопки с
/// людьми; нажатие дописывает логин. Пусто — ничего не показывается.
struct MentionSuggestions: View {
    @Binding var text: String
    @Environment(\.linkActions) private var actions

    var body: some View {
        if let actions, let typing = Tags.typingMention(text) {
            let people = actions.suggest(typing.part)
            if !people.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(people) { u in
                            Button {
                                text = Tags.completeMention(text, at: typing.at, cursor: text.utf16.count, handle: u.handle)
                            } label: {
                                HStack(spacing: 6) {
                                    Avatar(user: u, size: 26)
                                    Text("@" + u.handle).textStyle(.labelLarge, Palette.lilac)
                                }
                                .padding(.leading, 4)
                                .padding(.trailing, 12)
                                .padding(.vertical, 4)
                                .glass(Capsule())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("@" + u.handle)
                        }
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }
}

/// Скопировать текст в буфер обмена.
func copyText(_ text: String) {
    UIPasteboard.general.string = text
}
