#!/usr/bin/env python3
"""Звуки уведомлений Memory — один источник: scripts/sounds/catalog.py.

Скрипт синтезирует все звуки (scripts/sounds/engine.py) и пишет:
  app/src/main/res/raw/snd_<id>.ogg              — Android (Ogg Vorbis)
  ios/Memory/Sounds/snd_<id>.caf                  — iPhone (CAF, IMA4: годится и для пушей)
  app/src/main/java/ru/wishka/memory/data/SoundCatalog.kt
  ios/Memory/Data/SoundCatalog.swift              — список звуков, стили, бесплатные, по умолчанию

    python3 scripts/make_sounds.py           # всё пересобрать (нужны numpy, scipy и ffmpeg)
    python3 scripts/make_sounds.py --code    # только код списка (без звуков)
    python3 scripts/make_sounds.py --check   # CI: код списка совпадает с каталогом

Синтез детерминированный: те же числа — тот же звук.
"""
import os
import pathlib
import subprocess
import sys
import tempfile
import wave

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts" / "sounds"))

from catalog import CAT, DEFAULTS, FAMILIES, FAMILIES_EN, FAMILIES_HY, HY_NAMES  # noqa: E402

RAW = ROOT / "app" / "src" / "main" / "res" / "raw"
IOS = ROOT / "ios" / "Memory" / "Sounds"
KT = ROOT / "app" / "src" / "main" / "java" / "ru" / "wishka" / "memory" / "data" / "SoundCatalog.kt"
SWIFT = ROOT / "ios" / "Memory" / "Data" / "SoundCatalog.swift"

STYLE_NAMES = {"glass": "Glass", "wood": "Wood", "pop": "Pop", "keys": "Keys", "synth": "Synth",
               "ambient": "Ambient", "8bit": "EightBit", "minimal": "Minimal", "nature": "Nature"}
KINDS = ["messages", "prism", "reactions", "follows", "groups", "friends", "recap"]
SWIFT_KEYWORDS = {"switch", "default", "case", "class", "struct", "enum", "func", "let", "var", "in", "is", "as", "do", "if", "for", "while", "return", "repeat", "string"}
# Свои звуки кончились — дальше звук телефона и тишина
EXTRA = [("system", "Как в телефоне", "Phone default", "Ինչպես հեռախոսում"), ("silent", "Без звука", "Silent", "Առանց ձայնի")]


# Имена, которые в Kotlin путаются со стандартными
KT_RENAME = {"string": "GuitarString", "switch": "Toggle", "system": "System", "silent": "Silent"}


def kt_name(i):
    if i in KT_RENAME:
        return KT_RENAME[i]
    return "".join(p[:1].upper() + p[1:] for p in i.replace("8", "eight_").split("_"))


def swift_case(i):
    name = i if not i[0].isdigit() else "eight" + i[1:]
    return name + "Sound" if name in SWIFT_KEYWORDS else name


def esc(s):
    return s.replace("\\", "\\\\").replace('"', '\\"')


def kotlin():
    lines = [
        "// Сгенерировано scripts/make_sounds.py из scripts/sounds/catalog.py — не править руками.",
        "package ru.wishka.memory.data",
        "",
        "import ru.wishka.memory.i18n.L10n",
        "",
        "/** Стили звуков — разделы в выборе звука. */",
        "enum class SoundStyle(val id: String, private val ru: String, private val en: String, private val hy: String) {",
    ]
    for f, (ru, _) in FAMILIES.items():
        lines.append(f'    {STYLE_NAMES[f]}("{f}", "{esc(ru)}", "{esc(FAMILIES_EN[f])}", "{esc(FAMILIES_HY[f])}"),')
    lines += [
        '    Phone("phone", "Телефон", "Phone", "Հեռախոս"),',
        "    ;",
        "",
        "    val title: String get() = when {",
        "        L10n.english -> en",
        "        L10n.armenian -> hy",
        "        else -> ru",
        "    }",
        "}",
        "",
        "/**",
        " * Звуки уведомлений Memory. Файлы — res/raw/snd_<id>.ogg. free — доступен",
        " * без Memory+ (звук по умолчанию для вида доступен всегда).",
        " */",
        "enum class NotifySound(val id: String, private val ru: String, private val en: String, private val hy: String, val style: SoundStyle, val free: Boolean) {",
    ]
    for c in CAT:
        lines.append(f'    {kt_name(c["id"])}("{c["id"]}", "{esc(c["ru"])}", "{esc(c["en"])}", "{esc(HY_NAMES[c["id"]])}", SoundStyle.{STYLE_NAMES[c["family"]]}, {str(c["free"]).lower()}),')
    for i, ru, en, hy in EXTRA:
        lines.append(f'    {kt_name(i)}("{i}", "{ru}", "{en}", "{hy}", SoundStyle.Phone, true),')
    lines += [
        "    ;",
        "",
        "    val title: String get() = when {",
        "        L10n.english -> en",
        "        L10n.armenian -> hy",
        "        else -> ru",
        "    }",
        "",
        "    /** Имя файла без расширения; null — звук телефона или тишина. */",
        '    val file: String? get() = if (this == System || this == Silent) null else "snd_$id"',
        "",
        "    companion object {",
        "        fun of(id: String?): NotifySound? = entries.firstOrNull { it.id == id }",
        "",
        "        /** Звук по умолчанию: у каждого вида — свой, чтобы различать на слух. */",
        "        fun default(kind: NudgeKind): NotifySound = when (kind) {",
    ]
    for k in KINDS:
        lines.append(f"            NudgeKind.{k.capitalize()} -> {kt_name(DEFAULTS[k])}")
    lines += [
        "        }",
        "",
        "        /** Звуки стиля — в порядке каталога. */",
        "        fun inStyle(style: SoundStyle): List<NotifySound> = entries.filter { it.style == style }",
        "    }",
        "}",
        "",
    ]
    return "\n".join(lines)


def swift():
    lines = [
        "// Сгенерировано scripts/make_sounds.py из scripts/sounds/catalog.py — не править руками.",
        "import Foundation",
        "",
        "/// Стили звуков — разделы в выборе звука.",
        "enum SoundStyle: String, CaseIterable, Identifiable {",
    ]
    for f in FAMILIES:
        name = STYLE_NAMES[f][:1].lower() + STYLE_NAMES[f][1:]
        lines.append(f'    case {name} = "{f}"')
    lines += [
        '    case phone = "phone"',
        "",
        "    var id: String { rawValue }",
        "",
        "    var title: String {",
        "        switch self {",
    ]
    for f, (ru, _) in FAMILIES.items():
        name = STYLE_NAMES[f][:1].lower() + STYLE_NAMES[f][1:]
        lines.append(f'        case .{name}: return L10n.shared.pick(ru: "{esc(ru)}", en: "{esc(FAMILIES_EN[f])}", hy: "{esc(FAMILIES_HY[f])}")')
    lines += [
        '        case .phone: return L10n.shared.pick(ru: "Телефон", en: "Phone", hy: "Հեռախոս")',
        "        }",
        "    }",
        "}",
        "",
        "/// Звуки уведомлений Memory. Файлы — Sounds/snd_<id>.caf. free — доступен",
        "/// без Memory+ (звук по умолчанию для вида доступен всегда).",
        "enum NotifySound: String, CaseIterable, Identifiable {",
    ]
    for c in CAT:
        lines.append(f'    case {swift_case(c["id"])} = "{c["id"]}"')
    for i, _, _, _ in EXTRA:
        lines.append(f'    case {i} = "{i}"')
    lines += ["", "    var id: String { rawValue }", "", "    private var names: (ru: String, en: String, hy: String) {", "        switch self {"]
    for c in CAT:
        lines.append(f'        case .{swift_case(c["id"])}: return ("{esc(c["ru"])}", "{esc(c["en"])}", "{esc(HY_NAMES[c["id"]])}")')
    for i, ru, en, hy in EXTRA:
        lines.append(f'        case .{i}: return ("{ru}", "{en}", "{hy}")')
    lines += ["        }", "    }", "", "    var title: String { L10n.shared.pick(ru: names.ru, en: names.en, hy: names.hy) }", "", "    var style: SoundStyle {", "        switch self {"]
    by_style = {}
    for c in CAT:
        by_style.setdefault(c["family"], []).append("." + swift_case(c["id"]))
    for f, cases in by_style.items():
        name = STYLE_NAMES[f][:1].lower() + STYLE_NAMES[f][1:]
        lines.append(f"        case {', '.join(cases)}: return .{name}")
    lines += ["        case .system, .silent: return .phone", "        }", "    }", "", "    var free: Bool {", "        switch self {"]
    free = ["." + swift_case(c["id"]) for c in CAT if c["free"]] + [".system", ".silent"]
    lines += [f"        case {', '.join(free)}: return true", "        default: return false", "        }", "    }", ""]
    lines += [
        "    /// Имя файла без расширения; nil — звук телефона или тишина.",
        '    var file: String? { self == .system || self == .silent ? nil : "snd_\\(rawValue)" }',
        "",
        "    static func of(_ id: String?) -> NotifySound? { allCases.first { $0.rawValue == id } }",
        "",
        "    /// Звуки стиля — в порядке каталога.",
        "    static func inStyle(_ style: SoundStyle) -> [NotifySound] { allCases.filter { $0.style == style } }",
        "",
        "    /// Звук по умолчанию: у каждого вида — свой, чтобы различать на слух.",
        "    static func `default`(_ kind: NudgeKind) -> NotifySound {",
        "        switch kind {",
    ]
    for k in KINDS:
        lines.append(f"        case .{k}: return .{swift_case(DEFAULTS[k])}")
    lines += ["        }", "    }", "}", ""]
    return "\n".join(lines)


def render_audio():
    import numpy as np
    from engine import SR, master

    RAW.mkdir(parents=True, exist_ok=True)
    IOS.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for c in CAT:
            st = master(c["fn"](), c["dur"])
            data = (np.clip(st.T, -1, 1) * 32767).astype(np.int16)
            wav = os.path.join(tmp, c["id"] + ".wav")
            with wave.open(wav, "wb") as w:
                w.setnchannels(2)
                w.setsampwidth(2)
                w.setframerate(SR)
                w.writeframes(data.tobytes())
            name = "snd_" + c["id"]
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5",
                            "-map_metadata", "-1", str(RAW / (name + ".ogg"))], check=True)
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ar", "44100", "-c:a", "adpcm_ima_qt",
                            "-f", "caf", str(IOS / (name + ".caf"))], check=True)
    print(f"Звуки: {len(CAT)} — {RAW.relative_to(ROOT)} и {IOS.relative_to(ROOT)}")


def main() -> int:
    check = "--check" in sys.argv
    stale = []
    for path, text in ((KT, kotlin()), (SWIFT, swift())):
        if not path.exists() or path.read_text(encoding="utf-8") != text:
            stale.append(path.relative_to(ROOT))
            if not check:
                path.write_text(text, encoding="utf-8")
    if check:
        if stale:
            print("Список звуков устарел — запустите python3 scripts/make_sounds.py --code:")
            for p in stale:
                print("  ", p)
            return 1
        print(f"Список звуков: {len(CAT)} — в порядке")
        return 0
    if "--code" not in sys.argv:
        render_audio()
    print(f"Код списка звуков: обновлено файлов {len(stale)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
