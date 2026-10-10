#!/usr/bin/env python3
"""Значок приложения — тот же кристалл, что в шапке ленты (CrystalMark).

Рисует с запасом (×4) и уменьшает — так края гладкие. Выход:
  android/mipmap-*/ic_launcher_foreground.png  — кристалл на прозрачном (108dp)
  android/mipmap-*/ic_launcher_monochrome.png  — силуэт для тематических значков
  android/mipmap-*/ic_launcher.png, ic_launcher_round.png — то же, 48dp
  ios/AppIcon.png — 1024×1024 без прозрачности (так требует App Store)
"""
import os
import random
from PIL import Image, ImageDraw, ImageFilter, ImageChops

OUT = os.path.dirname(os.path.abspath(__file__))
IRIDESCENT = [0xFF9EDE, 0xC6A2FF, 0x8CC4FF, 0xFFF4FF, 0xFFDB8F, 0xD08CFF, 0x7AA8FF]
TOP_Y, BOTTOM_Y, MID_Y, HALF_W, RIDGE_X = 0.04, 0.96, 0.5, 0.2, 0.545


def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def lerp(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(4))


def mask(size, poly):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).polygon(poly, fill=255)
    return m


def crystal(L):
    """Кристалл в квадрате стороной L (RGBA), как CrystalMark.render."""
    S = (L, L)
    P = lambda x, y: (x * L, y * L)
    top, bottom = P(0.5, TOP_Y), P(0.5, BOTTOM_Y)
    left, right = P(0.5 - HALF_W, MID_Y), P(0.5 + HALF_W, MID_Y)
    ridge = P(RIDGE_X, MID_Y)
    outline = [top, right, bottom, left]
    shape = mask(S, outline)
    img = Image.new("RGBA", S, (0, 0, 0, 0))

    # 1. Ореол
    glow = Image.new("RGBA", S, rgb(0xC49BFF, 0))
    glow.putalpha(shape.filter(ImageFilter.GaussianBlur(L * 0.06)).point(lambda v: int(v * 0.63)))
    img = Image.alpha_composite(img, glow)

    # 2. Тело — перламутр по диагонали (как LinearGradient с MIRROR)
    body = Image.new("RGBA", S)
    px = body.load()
    x0, y0, x1, y1 = 0.3 * L, 0, 0.7 * L, L
    dx, dy = x1 - x0, y1 - y0
    dd = dx * dx + dy * dy
    cols = [rgb(c) for c in IRIDESCENT]
    n = len(cols) - 1
    for y in range(L):
        for x in range(L):
            t = ((x - x0) * dx + (y - y0) * dy) / dd
            t = t % 2
            if t > 1:
                t = 2 - t
            k = min(n - 1e-9, max(0, t * n))
            i = int(k)
            px[x, y] = lerp(cols[i], cols[i + 1], k - i)
    layer = Image.new("RGBA", S, (0, 0, 0, 0))
    layer.paste(body, (0, 0), shape)

    over = Image.new("RGBA", S, (0, 0, 0, 0))
    d = ImageDraw.Draw(over)
    # 3. Грани
    d.polygon([top, left, ridge], fill=(255, 255, 255, 85))
    d.polygon([top, ridge, right], fill=rgb(0x4A2F9A, 58))
    d.polygon([left, bottom, ridge], fill=rgb(0x4A2F9A, 58))
    d.polygon([ridge, bottom, right], fill=(255, 255, 255, 48))
    rnd = random.Random(11)
    fx, lit = -L * 0.2, True
    while fx < L:
        w = L * (0.02 + rnd.random() * 0.05)
        d.polygon([(fx, 0), (fx + w, 0), (fx + w + L * 0.3, L), (fx + L * 0.3, L)],
                  fill=(255, 255, 255, 51) if lit else rgb(0x4A2F9A, 34))
        fx += w + L * (0.02 + rnd.random() * 0.06)
        lit = not lit
    # 4. Внутренний кант
    d.line(outline + [top], fill=(255, 255, 255, 64), width=int(L * 0.05), joint="curve")
    d.line(outline + [top], fill=rgb(0x8A6FD8, 102), width=int(L * 0.02), joint="curve")
    clipped = Image.new("RGBA", S, (0, 0, 0, 0))
    clipped.paste(over, (0, 0), shape)
    layer = Image.alpha_composite(layer, clipped)
    # Прозрачность тела — по форме
    layer.putalpha(ImageChops.multiply(layer.getchannel("A"), shape))
    img = Image.alpha_composite(img, layer)

    top_d = ImageDraw.Draw(img)
    # 5. Рёбра
    ew = max(1, int(L * 0.012))
    top_d.line([left, right], fill=(255, 255, 255, 204), width=ew)
    top_d.line([top, ridge, bottom], fill=(255, 255, 255, 204), width=ew, joint="curve")
    # 6. Контур
    top_d.line(outline + [top], fill=(255, 255, 255, 255), width=max(1, int(L * 0.018)), joint="curve")
    # 7. Блики-звёздочки
    sw = max(1, int(L * 0.012))
    for sx, sy, r in [(0.44, 0.30, 0.05), (0.58, 0.68, 0.035)]:
        top_d.line([P(sx - r, sy), P(sx + r, sy)], fill=(255, 255, 255, 255), width=sw)
        top_d.line([P(sx, sy - r), P(sx, sy + r)], fill=(255, 255, 255, 255), width=sw)
    return img


def silhouette(L):
    """Силуэт для тематических значков Android 13+: форма с прорезями рёбер."""
    m = Image.new("RGBA", (L, L), (0, 0, 0, 0))
    P = lambda x, y: (x * L, y * L)
    d = ImageDraw.Draw(m)
    d.polygon([P(0.5, TOP_Y), P(0.5 + HALF_W, MID_Y), P(0.5, BOTTOM_Y), P(0.5 - HALF_W, MID_Y)], fill=(255, 255, 255, 255))
    cut = max(1, int(L * 0.022))
    d.line([P(0.5 - HALF_W, MID_Y), P(0.5 + HALF_W, MID_Y)], fill=(0, 0, 0, 0), width=cut)
    d.line([P(0.5, TOP_Y), P(RIDGE_X, MID_Y), P(0.5, BOTTOM_Y)], fill=(0, 0, 0, 0), width=cut, joint="curve")
    return m


def place(art, canvas_px, height_frac):
    """Кристалл высотой height_frac от стороны холста, по центру."""
    side = int(canvas_px * height_frac / (BOTTOM_Y - TOP_Y))
    a = art.resize((side, side), Image.LANCZOS)
    out = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    out.alpha_composite(a, ((canvas_px - side) // 2, (canvas_px - side) // 2))
    return out


def main():
    big = crystal(2048)
    sil = silhouette(2048)
    big.save(os.path.join(OUT, "crystal-2048.png"))
    densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, k in densities.items():
        d = os.path.join(OUT, "android", f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        fg = int(108 * k)
        # Безопасная зона адаптивного значка — круг 66dp из 108: кристалл высотой 64dp
        place(big, fg, 64 / 108).save(os.path.join(d, "ic_launcher_foreground.png"))
        place(sil, fg, 60 / 108).save(os.path.join(d, "ic_launcher_monochrome.png"))
        legacy = int(48 * k)
        place(big, legacy, 0.9).save(os.path.join(d, "ic_launcher.png"))
        place(big, legacy, 0.9).save(os.path.join(d, "ic_launcher_round.png"))
    # iPhone: значок обязан быть непрозрачным квадратом — ночной фон и свечение
    S = 1024
    bg = Image.new("RGBA", (S, S))
    bp = bg.load()
    top, mid, bot = rgb(0x07081A), rgb(0x0F153A), rgb(0x2A2152)
    for y in range(S):
        t = y / (S - 1)
        c = lerp(top, mid, t * 2) if t < 0.5 else lerp(mid, bot, (t - 0.5) * 2)
        for x in range(S):
            bp[x, y] = c
    halo = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(halo).ellipse([S * 0.18, S * 0.12, S * 0.82, S * 0.88], fill=rgb(0x844CAD, 120))
    bg = Image.alpha_composite(bg, halo.filter(ImageFilter.GaussianBlur(S * 0.12)))
    bg = Image.alpha_composite(bg, place(big, S, 0.78))
    os.makedirs(os.path.join(OUT, "ios"), exist_ok=True)
    bg.convert("RGB").save(os.path.join(OUT, "ios", "AppIcon.png"))
    # Превью: как значки выглядят на разных фонах
    prev = Image.new("RGBA", (3 * 220, 220), (0, 0, 0, 255))
    for i, col in enumerate([(240, 240, 245, 255), (30, 30, 40, 255), (90, 140, 200, 255)]):
        tile = Image.new("RGBA", (220, 220), col)
        tile.alpha_composite(place(big, 220, 64 / 108 * 220 / 220))
        prev.paste(tile, (i * 220, 0))
    ios = bg.resize((220, 220))
    out = Image.new("RGB", (4 * 220 + 30, 220), (0, 0, 0))
    out.paste(prev.convert("RGB"), (0, 0))
    out.paste(ios.convert("RGB"), (3 * 220 + 30, 0))
    out.save(os.path.join(OUT, "preview.png"))


if __name__ == "__main__":
    main()
