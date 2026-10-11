"""Значок приложения — прозрачный кристалл PRISM на звёздном небе.

Кристалл — тот же, что во вкладке PRISM: две четырёхгранные пирамиды,
сиреневый контур с переливом (розовый → сиреневый → голубой), линия
экватора, ребро-гребень и тонкое кольцо вокруг. Сам кристалл прозрачный:
грани — лёгкое стекло, сквозь него видны звёзды и задние рёбра.

Запуск: python3 scripts/crystal_icon.py  (нужны Pillow и numpy)
Пишет прямо в проект:
  app/src/main/res/mipmap-*/ic_launcher_background.png  — звёздное небо (108dp)
  app/src/main/res/mipmap-*/ic_launcher_foreground.png  — кристалл и кольцо (108dp)
  app/src/main/res/mipmap-*/ic_launcher_monochrome.png  — линии для тематических значков
  app/src/main/res/mipmap-*/ic_launcher.png, ic_launcher_round.png — 48dp целиком
  ios/Memory/Assets.xcassets/AppIcon.appiconset/AppIcon.png — 1024×1024 без прозрачности

Значки на телефоне не анимируются (ни Android, ни iPhone этого не дают),
поэтому кристалл на значке стоит в повороте; вращается он в самом
приложении — в шапке ленты (CrystalLogo).
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
IOS = os.path.join(ROOT, "ios", "Memory", "Assets.xcassets", "AppIcon.appiconset")

VOID, NIGHT, DEEP = 0x07081A, 0x0F153A, 0x1A1F5C
LILAC, ORCHID, VIOLET, OCEAN, SKY = 0xBC92CD, 0x844CAD, 0x5F3F96, 0x195699, 0x307EC0
EDGE = [0xFF9EDE, 0xC6A2FF, 0x8CC4FF]
SS = 4  # рисуем с запасом и уменьшаем — края гладкие


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


# ---------- Звёздное небо ----------

def sky(n, seed=7, count=1000):
    """Ночное небо: градиент, туманность, count звёзд. Квадрат n×n, непрозрачный."""
    u, v = grid(n)
    t = v
    c = np.where((t < 0.5)[..., None],
                 rgb(VOID) + (rgb(NIGHT) - rgb(VOID)) * (t / 0.5)[..., None],
                 rgb(NIGHT) + (rgb(DEEP) - rgb(NIGHT)) * ((t - 0.5) / 0.5)[..., None])
    a = np.ones((n, n), np.float32)
    rnd = random.Random(seed)
    # Туманность — мягкие облака цветов палитры
    for col, x, y, r, s in [(ORCHID, 0.25, 0.72, 0.42, 0.45), (OCEAN, 0.78, 0.30, 0.40, 0.45),
                            (VIOLET, 0.70, 0.80, 0.35, 0.40), (SKY, 0.18, 0.22, 0.28, 0.22),
                            (LILAC, 0.52, 0.50, 0.30, 0.18)]:
        d = np.sqrt((u - x) ** 2 + (v - y) ** 2) / r
        k = np.clip(1 - d, 0, 1) ** 2 * s
        c, a = over(c, a, np.broadcast_to(rgb(col), c.shape), k)
    # Звёзды: много мелких, несколько ярких с ореолом и лучами
    img = to_image(c, a)
    stars = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(stars)
    for _ in range(count):
        x, y = rnd.random() * n, rnd.random() * n
        r = n * (0.0012 + rnd.random() ** 3 * 0.0035)
        al = int(90 + rnd.random() * 165)
        tint = rnd.choice([(255, 255, 255), (230, 220, 255), (210, 230, 255), (255, 225, 245)])
        d.ellipse([x - r, y - r, x + r, y + r], fill=tint + (al,))
    glow = stars.filter(ImageFilter.GaussianBlur(n * 0.004))
    img = Image.alpha_composite(img, glow)
    img = Image.alpha_composite(img, stars)
    bright = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bright)
    for x, y, r in [(0.16, 0.14, 0.022), (0.86, 0.18, 0.016), (0.82, 0.84, 0.02), (0.12, 0.80, 0.014), (0.30, 0.42, 0.010)]:
        sparkle(bd, x * n, y * n, r * n, n)
    img = Image.alpha_composite(img, bright.filter(ImageFilter.GaussianBlur(n * 0.002)))
    img = Image.alpha_composite(img, bright)
    return img


def sparkle(d, x, y, r, n, alpha=255):
    w = max(1, int(n * 0.0022))
    d.line([(x - r, y), (x + r, y)], fill=(255, 255, 255, alpha), width=w)
    d.line([(x, y - r), (x, y + r)], fill=(255, 255, 255, alpha), width=w)
    q = r * 0.3
    d.ellipse([x - q * 0.5, y - q * 0.5, x + q * 0.5, y + q * 0.5], fill=(255, 255, 255, alpha))


# ---------- Кристалл ----------

ANGLE = 0.35        # поворот кристалла вокруг вертикали — как в шапке ленты
LIGHT = np.array([-0.45, -0.55, 0.70]) / np.linalg.norm([-0.45, -0.55, 0.70])


def geometry(n, ch):
    """Вершины в пикселях: верх, низ и четыре на экваторе (x, y, z)."""
    cx, cy = n / 2, n / 2
    r = ch * 0.55
    eq = [(cx + r * math.sin(ANGLE + k * math.pi / 2), cy, r * math.cos(ANGLE + k * math.pi / 2)) for k in range(4)]
    return (cx, cy - ch, 0.0), (cx, cy + ch, 0.0), eq


def edge_gradient(n, ch):
    """Перелив рёбер по диагонали: розовый → сиреневый → голубой."""
    u, v = grid(n)
    cx, cy = 0.5, 0.5
    s = ch / n
    t = np.clip(((u - (cx - s * 0.6)) * 0.45 + (v - (cy - s)) * 0.9) / (s * 2 * 1.0), 0, 1)
    c0, c1, c2 = (rgb(h) for h in EDGE)
    return np.where((t < 0.5)[..., None], c0 + (c1 - c0) * (t / 0.5)[..., None], c1 + (c2 - c1) * ((t - 0.5) / 0.5)[..., None])


def line_mask(n, segments, width, dashed=False):
    m = Image.new("L", (n, n), 0)
    d = ImageDraw.Draw(m)
    for a, b in segments:
        if dashed:
            L = math.dist(a, b)
            steps = max(2, int(L / (width * 4)))
            for i in range(0, steps, 2):
                p = (a[0] + (b[0] - a[0]) * i / steps, a[1] + (b[1] - a[1]) * i / steps)
                q = (a[0] + (b[0] - a[0]) * (i + 1) / steps, a[1] + (b[1] - a[1]) * (i + 1) / steps)
                d.line([p, q], fill=255, width=width)
        else:
            d.line([a, b], fill=255, width=width)
            r = width / 2
            for p in (a, b):
                d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=255)
    return np.asarray(m, np.float32) / 255


def poly_mask(n, pts):
    m = Image.new("L", (n, n), 0)
    ImageDraw.Draw(m).polygon(pts, fill=255)
    return np.asarray(m, np.float32) / 255


def crystal(n, ch, ring=True):
    """Прозрачный кристалл с кольцом на прозрачном фоне. ch — полувысота, px."""
    top, bottom, eq = geometry(n, ch)
    xy = lambda p: (p[0], p[1])
    grad = edge_gradient(n, ch)
    c = np.zeros((n, n, 3), np.float32)
    a = np.zeros((n, n), np.float32)
    u, v = grid(n)

    # Ореол за стеклом
    d = np.sqrt((u - 0.5) ** 2 + (v - 0.5) ** 2) / (ch * 1.55 / n)
    c, a = over(c, a, np.broadcast_to(rgb(LILAC), c.shape), np.clip(1 - d, 0, 1) ** 2 * 0.38)

    # Грани: почти прозрачное стекло. Задние — едва заметны, передние —
    # светлее к свету и с лёгким переливом; у освещённых — блик.
    faces = []
    for k in range(4):
        p, q = eq[k], eq[(k + 1) % 4]
        for apex in (top, bottom):
            A, P, Q = (np.array([apex[0], apex[1], apex[2]]), np.array(p), np.array(q))
            nrm = np.cross(P - A, Q - A)
            centre = (A + P + Q) / 3 - np.array([n / 2, n / 2, 0])
            if np.dot(nrm, centre) < 0:
                nrm = -nrm
            nrm = nrm / np.linalg.norm(nrm)
            faces.append((nrm[2], float(np.dot(nrm, LIGHT)), [xy(apex), xy(p), xy(q)]))
    for front in (False, True):
        for z, lit, pts in faces:
            if (z > 0) != front:
                continue
            m = poly_mask(n, pts)
            if not front:
                c, a = over(c, a, np.broadcast_to(rgb(VIOLET), c.shape), m * 0.10)
                continue
            tint = grad * 0.6 + 255 * 0.4
            c, a = over(c, a, tint, m * (0.07 + 0.16 * max(0.0, lit)))
            if lit < 0.35:
                c, a = over(c, a, np.broadcast_to(rgb(0x4A2F9A), c.shape), m * 0.16)
    # Блик по стеклу — диагональная полоса внутри силуэта
    left = min(eq, key=lambda p: p[0])
    right = max(eq, key=lambda p: p[0])
    outline = [xy(top), xy(right), xy(bottom), xy(left)]
    body = poly_mask(n, outline)
    band = np.exp(-(((u - 0.5) * 1.0 + (v - 0.5) * 0.55 + 0.035 * ch / n * 4) / (ch / n * 0.16)) ** 2)
    c, a = over(c, a, np.full_like(c, 255), body * band * 0.30)

    # Рёбра: задние — тонкие и тусклые (видны сквозь стекло), передние и контур — яркие
    back = [(xy(apex), xy(p)) for p in eq if p[2] < 0 and p not in (left, right) for apex in (top, bottom)]
    front_ridge = [(xy(apex), xy(p)) for p in eq if p[2] > 0 and p not in (left, right) for apex in (top, bottom)]
    w = max(1, int(ch * 0.035))
    m_back = line_mask(n, back, max(1, int(w * 0.55)))
    c, a = over(c, a, grad, m_back * 0.45)
    # Экватор — сзади пунктиром, спереди сплошной
    eq_back = [(xy(eq[k]), xy(eq[(k + 1) % 4])) for k in range(4) if eq[k][2] + eq[(k + 1) % 4][2] < 0]
    eq_front = [(xy(eq[k]), xy(eq[(k + 1) % 4])) for k in range(4) if eq[k][2] + eq[(k + 1) % 4][2] >= 0]
    c, a = over(c, a, grad, line_mask(n, eq_back, max(1, int(w * 0.55))) * 0.45)
    strong = line_mask(n, front_ridge + eq_front + list(zip(outline, outline[1:] + outline[:1])), w)
    glow = np.asarray(Image.fromarray((strong * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(w * 1.6)), np.float32) / 255
    c, a = over(c, a, grad, np.clip(glow * 0.9, 0, 1))
    c, a = over(c, a, grad, strong)
    # Белая сердцевина рёбер — стеклянный блеск
    core = line_mask(n, front_ridge + list(zip(outline, outline[1:] + outline[:1])), max(1, int(w * 0.35)))
    c, a = over(c, a, np.full_like(c, 255), core * 0.55)

    # Тонкое кольцо, как во вкладке PRISM
    if ring:
        rr = ch * 1.22
        dist = np.abs(np.sqrt((u * n - n / 2) ** 2 + (v * n - n / 2) ** 2) - rr)
        ring_m = np.clip(1 - dist / max(1.0, w * 0.4), 0, 1)
        c, a = over(c, a, np.broadcast_to(rgb(LILAC), c.shape), ring_m * 0.45)

    img = to_image(c, a)
    # Искры на гранях
    sp = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sp)
    sparkle(sd, n / 2 - ch * 0.12, n / 2 - ch * 0.42, ch * 0.13, n)
    sparkle(sd, n / 2 + ch * 0.10, n / 2 + ch * 0.38, ch * 0.08, n, 220)
    sparkle(sd, top[0], top[1], ch * 0.10, n, 200)
    img = Image.alpha_composite(img, sp.filter(ImageFilter.GaussianBlur(ch * 0.02)))
    img = Image.alpha_composite(img, sp)
    return img


def lines_only(n, ch):
    """Силуэт для тематических значков Android 13+: только линии кристалла и кольцо."""
    top, bottom, eq = geometry(n, ch)
    xy = lambda p: (p[0], p[1])
    left = min(eq, key=lambda p: p[0])
    right = max(eq, key=lambda p: p[0])
    outline = [xy(top), xy(right), xy(bottom), xy(left)]
    front = [(xy(apex), xy(p)) for p in eq if p[2] > 0 and p not in (left, right) for apex in (top, bottom)]
    w = max(1, int(ch * 0.06))
    m = line_mask(n, list(zip(outline, outline[1:] + outline[:1])) + front + [(xy(left), xy(right))], w)
    u, v = grid(n)
    dist = np.abs(np.sqrt((u * n - n / 2) ** 2 + (v * n - n / 2) ** 2) - ch * 1.22)
    m = np.maximum(m, np.clip(1 - dist / (w * 0.4), 0, 1) * 0.8)
    return to_image(np.full((n, n, 3), 255, np.float32), m)


def down(img, px):
    return img.resize((px, px), Image.LANCZOS)


def main():
    base = 432 * SS                         # 108dp × 4 (xxxhdpi) с запасом
    ch = base * 25 / 108                    # кристалл 50dp, кольцо ~61dp — внутри безопасного круга 66dp
    bg = sky(base)
    fg = crystal(base, ch)
    mono = lines_only(base, ch)
    full = Image.alpha_composite(bg, fg)
    densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, k in densities.items():
        d = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        px = int(108 * k)
        down(bg, px).convert("RGB").save(os.path.join(d, "ic_launcher_background.png"))
        down(fg, px).save(os.path.join(d, "ic_launcher_foreground.png"))
        down(mono, px).save(os.path.join(d, "ic_launcher_monochrome.png"))
        # Старые лаунчеры (на Android 8+ не нужны, но пусть будут): видимая часть 72dp из 108
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
    # iPhone: непрозрачный квадрат 1024, кристалл крупнее — углы скругляет система
    n = 1024 * SS
    ios = Image.alpha_composite(sky(n, seed=9, count=520), crystal(n, n * 0.33))
    os.makedirs(IOS, exist_ok=True)
    down(ios, 1024).convert("RGB").save(os.path.join(IOS, "AppIcon.png"))


if __name__ == "__main__":
    main()
