#!/usr/bin/env python3
"""Документы Memory — один источник: docs/legal (его публикует сайт).

Android берёт их прямо оттуда (app/build.gradle.kts добавляет docs/legal в
assets). iPhone — из своей папки ios/Memory/Legal (Xcode видит только файлы
внутри проекта), и этот скрипт её обновляет.

    python3 scripts/sync_legal.py          # скопировать в ios/Memory/Legal
    python3 scripts/sync_legal.py --check  # только проверить (CI): копии совпадают
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "docs" / "legal"
DST = ROOT / "ios" / "Memory" / "Legal"
# Что нужно приложению; страницы только для сайта и магазинов не копируем
APP_DOCS = ["terms", "privacy", "consent", "community", "recommendations", "licenses"]


def main() -> int:
    check = "--check" in sys.argv
    stale = []
    DST.mkdir(parents=True, exist_ok=True)
    # Каждый документ — на трёх языках: name.hy.md (основной), name.md (русский), name.en.md
    files = [f"{name}{suffix}.md" for name in APP_DOCS for suffix in ("", ".hy", ".en")]
    for file in files:
        src = SRC / file
        dst = DST / file
        text = src.read_text(encoding="utf-8")
        if not dst.exists() or dst.read_text(encoding="utf-8") != text:
            stale.append(dst.relative_to(ROOT))
            if not check:
                dst.write_text(text, encoding="utf-8")
    if check and stale:
        print("Устарели копии документов для iPhone — запустите python3 scripts/sync_legal.py:")
        for p in stale:
            print("  ", p)
        return 1
    print("Документы для iPhone " + ("в порядке" if check else f"обновлены: {len(stale)}"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
