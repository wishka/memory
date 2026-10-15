"""Значок приложения — кристальная монограмма «M» и «Y» на лиловом небе.

Большая «M»: две ножки по краям, а её внутренние линии — буква «Y»:
два плеча от верхних углов сходятся в центре и уходят вниз ножкой.
Чтобы читались обе буквы, они разного стекла: ножки «M» — сиреневое с
голубым, «Y» — светлее, розовое с белым, и лежит поверх. Буквы — стекло:
полупрозрачная заливка, грани-фаски (светлая и тёмная половина каждого
штриха), яркий переливающийся контур с белой сердцевиной и искры.

Запуск: python3 scripts/crystal_icon.py  (нужны Pillow и numpy)
Пишет прямо в проект:
  app/src/main/res/mipmap-*/ic_launcher_background.png  — лиловое небо (108dp)
  app/src/main/res/mipmap-*/ic_launcher_foreground.png  — монограмма (108dp)
  app/src/main/res/mipmap-*/ic_launcher_monochrome.png  — силуэт для тематических значков
  app/src/main/res/mipmap-*/ic_launcher.png, ic_launcher_round.png — 48dp целиком
  ios/Memory/Assets.xcassets/AppIcon.appiconset/AppIcon.png — 1024×1024 без прозрачности
Рядом кладёт docs/icon-preview.png — как значок выглядит в круге и в квадрате.
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
IOS = os.path.join(ROOT, "ios", "Memory", "Assets.xcassets", "AppIcon.appiconset")

# Небо светлее прежнего: фиолетовый сверху, лиловый снизу
SKY_TOP, SKY_MID, SKY_LOW = 0x2B2168, 0x47318E, 0x6A4BA8
LILAC, ORCHID, VIOLET, OCEAN, PINK = 0xBC92CD, 0x844CAD, 0x5F3F96, 0x2F5FA8, 0xE79AD6
M_EDGE = [0xC6A2FF, 0x9FB8FF, 0x8CC4FF]   # ножки «M»: сирень → голубой
Y_EDGE = [0xFFB8E8, 0xFFD9F2, 0xFFFFFF]   # «Y»: розовый → белый
SS = int(os.environ.get("SS", "4"))  # рисуем с запасом и уменьшаем — края гладкие


def rgb(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], dtype=np.float32)


def to_image(color, alpha):
    a = np.clip(alpha, 0, 1)[..., None]
    return Image.fromarray(np.concatenate([np.clip(color, 0, 255), a * 255], axis=-1).astype(np.uint8), "RGBA")


def over(dst_c, dst_a, src_c, src_a):
    """Наложение src поверх dst (непредумноженные цвета, альфа 0…1)."""
    sa = src_a[..., None]
    da = dst_a[..., None]
    out_a = sa + da * (1 - sa)
    out_c = (src_c * sa + dst_c * da * (1 - sa)) / np.maximum(out_a, 1e-6)
    return out_c, out_a[..., 0]


def grid(n):
    y, x = np.mgrid[0:n, 0:n].astype(np.float32)
    return (x + 0.5) / n, (y + 0.5) / n


def blur(m, r):
    return np.asarray(Image.fromarray((np.clip(m, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r)), np.float32) / 255


# ---------- Небо ----------

def sky(n, seed=7, count=700):
    """Лиловое небо: градиент, мягкая туманность, звёзды. Квадрат n×n, непрозрачный."""
    u, v = grid(n)
    c0, c1, c2 = rgb(SKY_TOP), rgb(SKY_MID), rgb(SKY_LOW)
    c = np.where((v < 0.5)[..., None], c0 + (c1 - c0) * (v / 0.5)[..., None], c1 + (c2 - c1) * ((v - 0.5) / 0.5)[..., None])
    a = np.ones((n, n), np.float32)
    for col, x, y, r, s in [(ORCHID, 0.22, 0.75, 0.48, 0.50), (LILAC, 0.80, 0.25, 0.42, 0.35),
                            (OCEAN, 0.85, 0.80, 0.40, 0.30), (PINK, 0.15, 0.18, 0.30, 0.18),
                            (LILAC, 0.50, 0.52, 0.45, 0.22)]:
        d = np.sqrt((u - x) ** 2 + (v - y) ** 2) / r
        c, a = over(c, a, np.broadcast_to(rgb(col), c.shape), np.clip(1 - d, 0, 1) ** 2 * s)
    img = to_image(c, a)
    rnd = random.Random(seed)
    stars = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(stars)
    for _ in range(count):
        x, y = rnd.random() * n, rnd.random() * n
        r = n * (0.0012 + rnd.random() ** 3 * 0.0032)
        tint = rnd.choice([(255, 255, 255), (240, 228, 255), (220, 235, 255), (255, 232, 248)])
        d.ellipse([x - r, y - r, x + r, y + r], fill=tint + (int(80 + rnd.random() * 150),))
    img = Image.alpha_composite(img, stars.filter(ImageFilter.GaussianBlur(n * 0.004)))
    img = Image.alpha_composite(img, stars)
    bright = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bright)
    for x, y, r in [(0.14, 0.13, 0.020), (0.87, 0.15, 0.015), (0.85, 0.86, 0.018), (0.11, 0.84, 0.013)]:
        sparkle(bd, x * n, y * n, r * n, n)
    img = Image.alpha_composite(img, bright.filter(ImageFilter.GaussianBlur(n * 0.002)))
    return Image.alpha_composite(img, bright)


def sparkle(d, x, y, r, n, alpha=255):
    w = max(1, int(n * 0.0022))
    d.line([(x - r, y), (x + r, y)], fill=(255, 255, 255, alpha), width=w)
    d.line([(x, y - r), (x, y + r)], fill=(255, 255, 255, alpha), width=w)
    q = r * 0.3
    d.ellipse([x - q * 0.5, y - q * 0.5, x + q * 0.5, y + q * 0.5], fill=(255, 255, 255, alpha))


# ---------- Монограмма ----------

def band(a, b, w):
    """Четырёхугольник-штрих шириной w вдоль отрезка a → b."""
    dx, dy = b[0] - a[0], b[1] - a[1]
    L = math.hypot(dx, dy)
    nx, ny = -dy / L * w / 2, dx / L * w / 2
    return [(a[0] + nx, a[1] + ny), (b[0] + nx, b[1] + ny), (b[0] - nx, b[1] - ny), (a[0] - nx, a[1] - ny)]


def _hit(p, d, q, e):
    """Пересечение прямых p + d·k и q + e·m."""
    det = d[0] * (-e[1]) - d[1] * (-e[0])
    k = ((q[0] - p[0]) * (-e[1]) - (q[1] - p[1]) * (-e[0])) / det
    return (p[0] + d[0] * k, p[1] + d[1] * k)


def glyph(n, h):
    """Штрихи монограммы: (часть, центр-линия, полигон). h — высота букв, px.

    «Y» собрана из кусков без нахлёста: плечо — от верхнего края до оси,
    ножка — от места, где плечи встречаются, до низа; поэтому в развилке
    нет швов.
    """
    cx, cy = n / 2, n / 2
    w = h * 1.06                 # ширина «M»
    t = h * 0.19                 # толщина ножек «M»
    s = h * 0.17                 # толщина «Y»
    top, bottom = cy - h / 2, cy + h / 2
    left, right = cx - w / 2, cx + w / 2
    jy = top + h * 0.56          # где сходятся оси плеч «Y»
    parts = []
    for x in (left + t / 2, right - t / 2):
        parts.append(("M", ((x, top), (x, bottom)), [(x - t / 2, top), (x + t / 2, top), (x + t / 2, bottom), (x - t / 2, bottom)]))
    horiz = (1.0, 0.0)
    vert = (0.0, 1.0)
    stem_top = None
    arms = []
    for sgn in (-1, 1):
        a = (cx + sgn * (w / 2 - t * 0.5), top)
        b = (cx, jy)
        d = (b[0] - a[0], b[1] - a[1])
        L = math.hypot(*d)
        nx, ny = -d[1] / L * s / 2, d[0] / L * s / 2
        # Внешняя кромка — дальше от оси, внутренняя — ближе
        e1, e2 = (a[0] + nx, a[1] + ny), (a[0] - nx, a[1] - ny)
        outer, inner = (e1, e2) if abs(e1[0] - cx) > abs(e2[0] - cx) or e1[1] > e2[1] else (e2, e1)
        if outer[1] < inner[1]:
            outer, inner = inner, outer
        o_top = _hit(outer, d, (cx, top), horiz)
        i_top = _hit(inner, d, (cx, top), horiz)
        crotch = _hit(inner, d, (cx, top), vert)
        foot = _hit(outer, d, (cx + sgn * s / 2, top), vert)
        stem_top = foot[1]
        arms.append((sgn, a, b, [o_top, i_top, crotch, (cx, foot[1]), foot]))
    for sgn, a, b, pts in arms:
        parts.append(("Y", (a, b), pts))
    parts.append(("Y", ((cx, stem_top), (cx, bottom)), [(cx - s / 2, stem_top), (cx + s / 2, stem_top), (cx + s / 2, bottom), (cx - s / 2, bottom)]))
    return parts, top


def poly_mask(n, pts):
    m = Image.new("L", (n, n), 0)
    ImageDraw.Draw(m).polygon(pts, fill=255)
    return np.asarray(m, np.float32) / 255


def gradient(n, stops, h):
    """Перелив по диагонали внутри букв."""
    u, v = grid(n)
    k = h / n
    t = np.clip(((u - 0.5 + k * 0.55) * 0.55 + (v - 0.5 + k * 0.5) * 0.85) / (k * 1.4), 0, 1)
    c0, c1, c2 = (rgb(x) for x in stops)
    return np.where((t < 0.5)[..., None], c0 + (c1 - c0) * (t / 0.5)[..., None], c1 + (c2 - c1) * ((t - 0.5) / 0.5)[..., None])


def edges(m, width):
    """Контур маски: маска минус её сжатие."""
    img = Image.fromarray((m * 255).astype(np.uint8))
    size = max(3, int(width) | 1)
    inner = np.asarray(img.filter(ImageFilter.MinFilter(size)), np.float32) / 255
    return np.clip(m - inner, 0, 1)


def letters(n, h):
    """Кристальные буквы на прозрачном фоне."""
    parts, top = glyph(n, h)
    u, v = grid(n)
    yy = v * n
    clip_top = (yy >= top).astype(np.float32)
    c = np.zeros((n, n, 3), np.float32)
    a = np.zeros((n, n), np.float32)
    masks = {"M": np.zeros((n, n), np.float32), "Y": np.zeros((n, n), np.float32)}
    for kind, _, pts in parts:
        masks[kind] = np.maximum(masks[kind], poly_mask(n, pts) * clip_top)
    union = np.maximum(masks["M"], masks["Y"])
    # Тёмная подложка-тень: буквы отделяются от светлого неба
    c, a = over(c, a, np.broadcast_to(rgb(0x1A1240), c.shape), blur(union, h * 0.05) * 0.55)
    # Ореол
    c, a = over(c, a, np.broadcast_to(rgb(LILAC), c.shape), blur(union, h * 0.12) * 0.45)
    lw = max(2, int(h * 0.022))
    for kind, stops, glass in (("M", M_EDGE, 0x6E58C8), ("Y", Y_EDGE, 0xF2C6F0)):
        grad = gradient(n, stops, h)
        m = masks[kind]
        # Стекло: полупрозрачная заливка с переливом
        tint = grad * 0.5 + rgb(glass) * 0.5
        c, a = over(c, a, tint, m * (0.42 if kind == "M" else 0.55))
        # Фаски: у каждого штриха половина к свету светлее, другая темнее
        for k2, (a0, b0), pts in parts:
            if k2 != kind:
                continue
            pm = poly_mask(n, pts) * clip_top
            dx, dy = b0[0] - a0[0], b0[1] - a0[1]
            side = (u * n - a0[0]) * dy - (v * n - a0[1]) * dx
            # Свет сверху слева: светлая та сторона, нормаль которой смотрит туда
            nx, ny = dy, -dx
            lit_side = 1.0 if (nx * -0.6 + ny * -0.8) > 0 else -1.0
            light = pm * (np.sign(side) * lit_side > 0)
            shade = pm * (np.sign(side) * lit_side <= 0)
            c, a = over(c, a, np.full_like(c, 255), light * 0.22)
            c, a = over(c, a, np.broadcast_to(rgb(0x2A1C6A), c.shape), shade * 0.20)
            # Гребень по середине штриха — граница фасок
            ridge = Image.new("L", (n, n), 0)
            ImageDraw.Draw(ridge).line([a0, b0], fill=255, width=max(1, lw // 2))
            r = np.asarray(ridge, np.float32) / 255 * pm
            c, a = over(c, a, grad, r * 0.75)
        # Контур: перелив, свечение и белая сердцевина
        e = edges(m, lw)
        c, a = over(c, a, grad, np.clip(blur(e, lw * 1.4) * 1.2, 0, 1))
        c, a = over(c, a, grad, e)
        core = edges(m, max(2, lw // 2))
        c, a = over(c, a, np.full_like(c, 255), core * (0.55 if kind == "M" else 0.8))
    # Блик — диагональная полоса по стеклу
    hb = np.exp(-(((u - 0.5) * 0.9 + (v - 0.5) * 0.5 + h / n * 0.12) / (h / n * 0.09)) ** 2)
    c, a = over(c, a, np.full_like(c, 255), union * hb * 0.30)
    img = to_image(c, a)
    # Искры на вершинах
    sp = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sp)
    cx = n / 2
    sparkle(sd, cx, top + h * 0.5, h * 0.10, n)
    sparkle(sd, cx - h * 0.53 + h * 0.085, top, h * 0.08, n, 230)
    sparkle(sd, cx + h * 0.53 - h * 0.085, top + h, h * 0.06, n, 200)
    img = Image.alpha_composite(img, sp.filter(ImageFilter.GaussianBlur(h * 0.015)))
    return Image.alpha_composite(img, sp)


def silhouette(n, h):
    """Силуэт для тематических значков Android 13+: обе буквы, «Y» отделена тонким зазором."""
    parts, top = glyph(n, h)
    _, v = grid(n)
    clip_top = (v * n >= top).astype(np.float32)
    mm = np.zeros((n, n), np.float32)
    ym = np.zeros((n, n), np.float32)
    for kind, _, pts in parts:
        if kind == "M":
            mm = np.maximum(mm, poly_mask(n, pts) * clip_top)
        else:
            ym = np.maximum(ym, poly_mask(n, pts) * clip_top)
    gap = np.asarray(Image.fromarray((ym * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(max(3, int(h * 0.03) | 1))), np.float32) / 255
    m = np.maximum(mm * (1 - gap), ym)
    return to_image(np.full((n, n, 3), 255, np.float32), m)


def down(img, px):
    return img.resize((px, px), Image.LANCZOS)


def preview(ios_img, fg_full):
    """Как значок выглядит: квадрат iPhone и круг Android — рядом, на тёмном и светлом."""
    W, H = 1400, 760
    out = Image.new("RGB", (W, H), (24, 22, 40))
    d = ImageDraw.Draw(out)
    d.rectangle([W // 2, 0, W, H], fill=(236, 234, 244))
    for i, x in enumerate((60, W // 2 + 60)):
        sq = down(ios_img, 300)
        m = Image.new("L", (1200, 1200), 0)
        ImageDraw.Draw(m).rounded_rectangle([0, 0, 1199, 1199], radius=270, fill=255)
        out.paste(sq, (x, 60), m.resize((300, 300), Image.LANCZOS))
        circ = down(fg_full, 300)
        cm = Image.new("L", (1200, 1200), 0)
        ImageDraw.Draw(cm).ellipse([0, 0, 1199, 1199], fill=255)
        out.paste(circ, (x + 340 - 30, 60), cm.resize((300, 300), Image.LANCZOS))
        small = down(ios_img, 60)
        out.paste(small, (x + 120, 470), m.resize((60, 60), Image.LANCZOS))
        out.paste(down(ios_img, 120), (x + 260, 440), m.resize((120, 120), Image.LANCZOS))
    return out


def main():
    base = 432 * SS                         # 108dp × 4 (xxxhdpi) с запасом
    h = base * 40 / 108                     # буквы 40dp — внутри безопасного круга 66dp
    bg = sky(base)
    fg = letters(base, h)
    mono = silhouette(base, h)
    full = Image.alpha_composite(bg, fg)
    densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, k in densities.items():
        d = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        px = int(108 * k)
        down(bg, px).convert("RGB").save(os.path.join(d, "ic_launcher_background.png"))
        down(fg, px).save(os.path.join(d, "ic_launcher_foreground.png"))
        down(mono, px).save(os.path.join(d, "ic_launcher_monochrome.png"))
        # Старые лаунчеры: видимая часть 72dp из 108
        crop = full.crop((int(base * 18 / 108), int(base * 18 / 108), int(base * 90 / 108), int(base * 90 / 108)))
        legacy = int(48 * k)
        sq = down(crop, legacy)
        rmask = Image.new("L", (legacy * 4, legacy * 4), 0)
        ImageDraw.Draw(rmask).rounded_rectangle([0, 0, legacy * 4 - 1, legacy * 4 - 1], radius=legacy * 4 * 0.18, fill=255)
        out = Image.new("RGBA", (legacy, legacy), (0, 0, 0, 0))
        out.paste(sq, (0, 0), rmask.resize((legacy, legacy), Image.LANCZOS))
        out.save(os.path.join(d, "ic_launcher.png"))
        cmask = Image.new("L", (legacy * 4, legacy * 4), 0)
        ImageDraw.Draw(cmask).ellipse([0, 0, legacy * 4 - 1, legacy * 4 - 1], fill=255)
        out = Image.new("RGBA", (legacy, legacy), (0, 0, 0, 0))
        out.paste(sq, (0, 0), cmask.resize((legacy, legacy), Image.LANCZOS))
        out.save(os.path.join(d, "ic_launcher_round.png"))
    # iPhone: непрозрачный квадрат 1024, буквы крупнее — углы скругляет система
    n = 1024 * SS
    ios = Image.alpha_composite(sky(n, seed=9, count=420), letters(n, n * 0.52))
    os.makedirs(IOS, exist_ok=True)
    ios_small = down(ios, 1024).convert("RGB")
    ios_small.save(os.path.join(IOS, "AppIcon.png"))
    # Android в круге: видимые 72dp из 108
    circle = full.crop((int(base * 18 / 108), int(base * 18 / 108), int(base * 90 / 108), int(base * 90 / 108)))
    preview(ios_small, circle).save(os.path.join(ROOT, "docs", "icon-preview.png"))


if __name__ == "__main__":
    main()
