#!/usr/bin/env python3
"""Гид по Memory — один источник: docs/guide/guide.json (русский, английский, армянский).

Скрипт собирает из него код для обоих приложений:
  app/src/main/java/ru/wishka/memory/data/GuideContent.kt
  ios/Memory/Data/GuideContent.swift

    python3 scripts/sync_guide.py          # пересобрать
    python3 scripts/sync_guide.py --check  # только проверить (CI): код совпадает с guide.json

Править текст гида — только в guide.json, потом запустить скрипт.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "docs" / "guide" / "guide.json"
KT = ROOT / "app" / "src" / "main" / "java" / "ru" / "wishka" / "memory" / "data" / "GuideContent.kt"
SWIFT = ROOT / "ios" / "Memory" / "Data" / "GuideContent.swift"

ACTIONS = [
    "Feed", "Stories", "Prism", "NewPrism", "Chats", "Groups", "Together", "Profile",
    "Friends", "Requests", "Saved", "Music", "Studio", "Shelf", "Recap",
    "Settings", "Privacy", "Security", "Blacklist", "Notifications", "Sounds",
    "Wallpaper", "Language", "Legal", "Growth", "Premium",
]


def esc(s: str, kotlin: bool) -> str:
    s = s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")
    return s.replace("$", "\\$") if kotlin else s


def check_source(data) -> list:
    """Ошибки в guide.json: пустые строки, повторы id, неизвестные действия."""
    errors, ids = [], set()

    def text(t, where):
        if not isinstance(t, dict) or not all(t.get(k, "").strip() for k in ("ru", "en", "hy")):
            errors.append(f"{where}: нужен текст ru, en и hy")

    for c in data["chapters"]:
        if c["id"] in ids:
            errors.append(f"повтор id главы {c['id']}")
        ids.add(c["id"])
        text(c["title"], c["id"])
        text(c["lead"], c["id"])
        topic_ids = set()
        for t in c["topics"]:
            where = f"{c['id']}/{t['id']}"
            if t["id"] in topic_ids:
                errors.append(f"повтор id темы {where}")
            topic_ids.add(t["id"])
            text(t["title"], where)
            if not t["steps"]:
                errors.append(f"{where}: нет шагов")
            for s in t["steps"]:
                text(s, where)
            if t.get("tip"):
                text(t["tip"], where)
            if t.get("action") and t["action"] not in ACTIONS:
                errors.append(f"{where}: неизвестное действие {t['action']}")
    return errors


def kotlin(data) -> str:
    def g(t):
        return f'GuideText("{esc(t["ru"], True)}", "{esc(t["en"], True)}", "{esc(t["hy"], True)}")'

    out = [
        "// Сгенерировано scripts/sync_guide.py из docs/guide/guide.json — не править руками.",
        "package ru.wishka.memory.data",
        "",
        "/** Главы гида. Текст — в docs/guide/guide.json. */",
        "internal object GuideContent {",
        "    val chapters: List<GuideChapter> by lazy {",
        "        listOf(" + ", ".join(f"chapter{i}()" for i in range(len(data["chapters"]))) + ")",
        "    }",
    ]
    for i, c in enumerate(data["chapters"]):
        out += ["", f"    private fun chapter{i}() = GuideChapter(",
                f'        "{c["id"]}", "{c["emoji"]}",', f"        {g(c['title'])},", f"        {g(c['lead'])},",
                "        listOf("]
        for t in c["topics"]:
            out.append(f'            GuideTopic(\n                "{t["id"]}", {g(t["title"])},\n                listOf(')
            for s in t["steps"]:
                out.append(f"                    {g(s)},")
            out.append("                ),")
            if t.get("tip"):
                out.append(f"                tip = {g(t['tip'])},")
            if t.get("action"):
                out.append(f"                action = GuideAction.{t['action']},")
            out.append("            ),")
        out += ["        ),", "    )"]
    out += ["}", ""]
    return "\n".join(out)


def swift(data) -> str:
    def g(t):
        return f'GuideText(ru: "{esc(t["ru"], False)}", en: "{esc(t["en"], False)}", hy: "{esc(t["hy"], False)}")'

    def lower(a):
        return a[0].lower() + a[1:]

    out = [
        "// Сгенерировано scripts/sync_guide.py из docs/guide/guide.json — не править руками.",
        "import Foundation",
        "",
        "/// Главы гида. Текст — в docs/guide/guide.json.",
        "enum GuideContent {",
        "    static let chapters: [GuideChapter] = [" + ", ".join(f"chapter{i}()" for i in range(len(data["chapters"]))) + "]",
    ]
    for i, c in enumerate(data["chapters"]):
        out += ["", f"    private static func chapter{i}() -> GuideChapter " + "{",
                f"        var topics: [GuideTopic] = []"]
        for t in c["topics"]:
            steps = ",\n".join(f"                {g(s)}" for s in t["steps"])
            tip = g(t["tip"]) if t.get("tip") else "nil"
            action = f".{lower(t['action'])}" if t.get("action") else "nil"
            out.append(
                f'        topics.append(GuideTopic(\n            id: "{t["id"]}", title: {g(t["title"])},\n'
                f"            steps: [\n{steps},\n            ],\n            tip: {tip}, action: {action}\n        ))"
            )
        out += [f'        return GuideChapter(id: "{c["id"]}", emoji: "{c["emoji"]}",',
                f"                            title: {g(c['title'])},",
                f"                            lead: {g(c['lead'])},",
                "                            topics: topics)", "    }"]
    out += ["}", ""]
    return "\n".join(out)


def main() -> int:
    check = "--check" in sys.argv
    data = json.loads(SRC.read_text(encoding="utf-8"))
    errors = check_source(data)
    if errors:
        print("Ошибки в docs/guide/guide.json:")
        for e in errors:
            print("  ", e)
        return 1
    stale = []
    for path, text in ((KT, kotlin(data)), (SWIFT, swift(data))):
        if not path.exists() or path.read_text(encoding="utf-8") != text:
            stale.append(path.relative_to(ROOT))
            if not check:
                path.write_text(text, encoding="utf-8")
    if check and stale:
        print("Код гида устарел — запустите python3 scripts/sync_guide.py:")
        for p in stale:
            print("  ", p)
        return 1
    topics = sum(len(c["topics"]) for c in data["chapters"])
    print(f"Гид: {len(data['chapters'])} глав, {topics} тем — " + ("в порядке" if check else f"обновлено файлов: {len(stale)}"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
