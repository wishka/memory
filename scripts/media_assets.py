"""Звуки уведомлений и встроенные гифки Memory — в стиле приложения.

Звуки: стекло, звёзды, космос. Каждый — короткий (0,4–1,3 с), мягкая
атака, хвост-реверберация. Синтез: синусоиды с негармоническими
призвуками (так звучит стекло и колокольчик), огибающая и эхо.

Гифки: 240 × 240, ночное небо в цветах приложения, кристальный текст
(Unbounded) и простая анимация — мерцание, пульс, полёт.

Запуск: python3 scripts/media_assets.py  (нужны Pillow и numpy)
Пишет прямо в проект:
  app/src/main/res/raw/snd_*.wav            ios/Memory/Sounds/snd_*.wav
  app/src/main/assets/gifs/*.gif             ios/Memory/Gifs/*.gif
Тот же список — в data/Sounds.kt и data/Gifs.kt (и .swift).
"""
import math
import os
import random
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "app", "src", "main", "res", "raw")
IOS_SND = os.path.join(ROOT, "ios", "Memory", "Sounds")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "gifs")
IOS_GIF = os.path.join(ROOT, "ios", "Memory", "Gifs")
FONT = os.path.join(ROOT, "ios", "Memory", "Fonts", "Unbounded-SemiBold.ttf")

RATE = 32000


# ==================== ЗВУКИ ====================

def env(n, attack=0.004, decay=0.5):
    t = np.arange(n) / RATE
    a = np.clip(t / attack, 0, 1)
    return a * np.exp(-t / decay)


def tone(freq, dur, decay=0.45, partials=((1, 1.0), (2.76, 0.32), (5.4, 0.12)), attack=0.004):
    """Колокольчик/стекло: основной тон и негармонические призвуки, каждый гаснет быстрее."""
    n = int(dur * RATE)
    t = np.arange(n) / RATE
    out = np.zeros(n)
    for k, (mul, amp) in enumerate(partials):
        out += amp * np.sin(2 * np.pi * freq * mul * t) * env(n, attack, decay / (1 + k * 0.8))
    return out


def place(buf, sig, at):
    i = int(at * RATE)
    end = min(len(buf), i + len(sig))
    buf[i:end] += sig[: end - i]


def echo(sig, delay=0.11, fb=0.35, taps=4):
    out = sig.copy()
    d = int(delay * RATE)
    for k in range(1, taps + 1):
        shifted = np.zeros_like(sig)
        if k * d < len(sig):
            shifted[k * d:] = sig[: len(sig) - k * d] * (fb ** k)
        out += shifted
    return out


def sweep(f0, f1, dur, decay=0.3):
    n = int(dur * RATE)
    t = np.arange(n) / RATE
    f = f0 * (f1 / f0) ** (t / dur)
    phase = 2 * np.pi * np.cumsum(f) / RATE
    return np.sin(phase) * env(n, 0.003, decay)


def noise(dur, seed=1):
    rnd = np.random.default_rng(seed)
    return rnd.standard_normal(int(dur * RATE))


def lowpass(sig, a=0.08):
    out = np.zeros_like(sig)
    acc = 0.0
    for i, x in enumerate(sig):
        acc += a * (x - acc)
        out[i] = acc
    return out


def finish(sig, dur, peak=0.72):
    n = int(dur * RATE)
    sig = np.concatenate([sig, np.zeros(max(0, n - len(sig)))])[:n]
    fade = int(0.03 * RATE)
    sig[-fade:] *= np.linspace(1, 0, fade)
    m = np.max(np.abs(sig)) or 1
    return sig / m * peak


def make_sounds():
    s = {}
    # Кристалл: две стеклянные ноты, вторая чуть позже — «дзинь-дзинь»
    b = np.zeros(int(1.2 * RATE))
    place(b, tone(1568, 1.0, 0.5), 0)
    place(b, tone(2349, 0.9, 0.45) * 0.7, 0.09)
    s["crystal"] = finish(echo(b, 0.13, 0.25), 1.2)
    # Звёздная пыль: быстрая восходящая россыпь колокольчиков
    b = np.zeros(int(1.0 * RATE))
    for i, f in enumerate((1047, 1319, 1568, 2093)):
        place(b, tone(f, 0.6, 0.22) * (0.8 + 0.07 * i), i * 0.06)
    s["stardust"] = finish(echo(b, 0.09, 0.3), 1.0)
    # Комета: шорох-проводка сверху вниз и мягкий удар в конце
    b = np.zeros(int(1.0 * RATE))
    whoosh = lowpass(noise(0.45, 3), 0.12) * env(int(0.45 * RATE), 0.12, 0.2) * 0.5
    place(b, whoosh, 0)
    place(b, sweep(2600, 900, 0.4, 0.25) * 0.35, 0.02)
    place(b, tone(1175, 0.6, 0.35), 0.36)
    s["comet"] = finish(b, 1.0)
    # Луна: низкий мягкий колокол с длинным хвостом
    b = tone(659, 1.3, 0.75, ((1, 1.0), (2.0, 0.25), (3.01, 0.1)), attack=0.012)
    s["moon"] = finish(echo(b, 0.18, 0.2), 1.3)
    # Призма: аккорд из трёх стеклянных нот с переливом (тремоло)
    n = int(1.1 * RATE)
    t = np.arange(n) / RATE
    b = np.zeros(n)
    for i, f in enumerate((880, 1109, 1319)):
        place(b, tone(f, 1.0, 0.55) * 0.7, i * 0.025)
    b *= 1 + 0.18 * np.sin(2 * np.pi * 7 * t)
    s["prism"] = finish(b, 1.1)
    # Туманность: воздушная волна — шум, отфильтрованный, и тихая нота под ним
    n = int(1.0 * RATE)
    t = np.arange(n) / RATE
    swell = np.sin(np.pi * np.clip(t / 0.9, 0, 1)) ** 2
    b = lowpass(noise(1.0, 5), 0.05) * swell * 0.6 + np.sin(2 * np.pi * 784 * t) * swell * 0.35
    s["nebula"] = finish(b, 1.0, 0.6)
    # Орбита: две ноты туда-обратно с эхом
    b = np.zeros(int(0.9 * RATE))
    place(b, tone(988, 0.5, 0.25), 0)
    place(b, tone(1480, 0.5, 0.25), 0.12)
    s["orbit"] = finish(echo(b, 0.16, 0.35, 3), 0.9)
    # Капля света: короткое «плинк» с падающим тоном
    b = np.zeros(int(0.55 * RATE))
    place(b, sweep(1500, 820, 0.35, 0.12), 0)
    place(b, 0.3 * sweep(3000, 1640, 0.25, 0.06), 0)
    s["droplet"] = finish(echo(b, 0.1, 0.2, 2), 0.55)
    return s


def write_wav(path, sig):
    data = (np.clip(sig, -1, 1) * 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data.tobytes())


# ==================== ГИФКИ ====================

N = 240
FRAMES = 16
VOID, NIGHT, DEEP = (7, 8, 26), (15, 21, 58), (42, 33, 82)
LILAC, ORCHID, PINK, SKY = (188, 146, 205), (132, 76, 173), (255, 158, 222), (140, 196, 255)


def sky_bg(seed):
    img = Image.new("RGB", (N, N))
    d = ImageDraw.Draw(img)
    for y in range(N):
        k = y / N
        c = tuple(int(NIGHT[i] + (DEEP[i] - NIGHT[i]) * k) for i in range(3))
        d.line([(0, y), (N, y)], fill=c)
    glow = Image.new("RGB", (N, N), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([20, 120, 200, 300], fill=ORCHID)
    gd.ellipse([140, -40, 300, 120], fill=(25, 86, 153))
    glow = glow.filter(ImageFilter.GaussianBlur(40))
    img = Image.blend(img, Image.composite(glow, img, Image.new("L", (N, N), 90)), 0.6)
    rnd = random.Random(seed)
    stars = [(rnd.random() * N, rnd.random() * N, rnd.random()) for _ in range(40)]
    return img, stars


def draw_stars(d, stars, f):
    for i, (x, y, p) in enumerate(stars):
        a = 0.35 + 0.65 * (0.5 + 0.5 * math.sin(2 * math.pi * (f / FRAMES + p)))
        r = 1.1 if i % 7 else 1.8
        c = tuple(int(255 * a) for _ in range(3))
        d.ellipse([x - r, y - r, x + r, y + r], fill=c)


def sparkle(d, x, y, r, col=(255, 255, 255)):
    w = max(1, int(r / 6))
    d.line([(x - r, y), (x + r, y)], fill=col, width=w)
    d.line([(x, y - r), (x, y + r)], fill=col, width=w)
    d.ellipse([x - r * 0.18, y - r * 0.18, x + r * 0.18, y + r * 0.18], fill=col)


def crystal_text(img, text, cy, size, phase, scale=1.0):
    """Кристальный текст: перелив розовый → сиреневый → голубой, белая кромка, бегущий блик."""
    font = ImageFont.truetype(FONT, int(size * scale))
    mask = Image.new("L", (N, N), 0)
    md = ImageDraw.Draw(mask)
    w = md.textlength(text, font=font)
    while w > N - 20 and font.size > 10:
        font = ImageFont.truetype(FONT, font.size - 2)
        w = md.textlength(text, font=font)
    md.text(((N - w) / 2, cy - font.size * 0.6), text, font=font, fill=255)
    grad = Image.new("RGB", (N, N))
    gd = ImageDraw.Draw(grad)
    for x in range(N):
        k = (x / N + phase * 0.3) % 1.0
        stops = [PINK, LILAC, SKY, PINK]
        i = int(k * 3)
        u = k * 3 - i
        c = tuple(int(stops[i][j] + (stops[i + 1][j] - stops[i][j]) * u) for j in range(3))
        gd.line([(x, 0), (x, N)], fill=c)
    # Бегущий блик
    band = Image.new("L", (N, N), 0)
    bd = ImageDraw.Draw(band)
    bx = -60 + (N + 120) * phase
    bd.polygon([(bx, 0), (bx + 26, 0), (bx - 34, N), (bx - 60, N)], fill=200)
    band = band.filter(ImageFilter.GaussianBlur(6))
    shine = Image.composite(Image.new("RGB", (N, N), (255, 255, 255)), grad, band)
    glow = mask.filter(ImageFilter.GaussianBlur(6))
    img.paste(Image.new("RGB", (N, N), LILAC), (0, 0), glow.point(lambda v: int(v * 0.6)))
    img.paste(shine, (0, 0), mask)
    edge = mask.filter(ImageFilter.FIND_EDGES).point(lambda v: 255 if v > 60 else 0)
    img.paste(Image.new("RGB", (N, N), (255, 255, 255)), (0, 0), edge.point(lambda v: int(v * 0.7)))


def heart(d, cx, cy, r, col):
    pts = []
    for k in range(60):
        t = 2 * math.pi * k / 60
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((cx + x * r / 16, cy - y * r / 16))
    d.polygon(pts, fill=col)


def diamond(d, cx, cy, r, col, outline=(255, 255, 255)):
    pts = [(cx, cy - r), (cx + r * 0.7, cy), (cx, cy + r), (cx - r * 0.7, cy)]
    d.polygon(pts, fill=col)
    d.line(pts + [pts[0]], fill=outline, width=2)
    d.line([pts[0], pts[2]], fill=outline, width=1)
    d.line([pts[1], pts[3]], fill=outline, width=1)


def make_gif(name, text, extra, seed):
    base, stars = sky_bg(seed)
    frames = []
    for f in range(FRAMES):
        img = base.copy()
        d = ImageDraw.Draw(img)
        draw_stars(d, stars, f)
        ph = f / FRAMES
        extra(img, d, ph)
        if text:
            pulse = 1.0 + 0.04 * math.sin(2 * math.pi * ph)
            crystal_text(img, text, 190 if extra is not none else 120, 30, ph, pulse)
        frames.append(img.quantize(colors=96, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE))
    frames[0].save(os.path.join(ASSETS, name + ".gif"), save_all=True, append_images=frames[1:], duration=70, loop=0, optimize=True, disposal=1)


def none(img, d, ph):
    pass


def x_heart(img, d, ph):
    s = 1 + 0.15 * abs(math.sin(math.pi * ph * 2))
    heart(d, 120, 95, 46 * s, PINK)
    sparkle(d, 160, 55, 10 + 6 * math.sin(2 * math.pi * ph))


def x_crystal(img, d, ph):
    w = 46 * abs(math.cos(math.pi * ph))  # поворот кристалла: сужается и расширяется
    pts = [(120, 40), (120 + w, 95), (120, 150), (120 - w, 95)]
    d.polygon(pts, fill=ORCHID)
    d.line(pts + [pts[0]], fill=(255, 255, 255), width=2)
    d.line([pts[0], pts[2]], fill=LILAC, width=1)
    sparkle(d, 120 + w * 0.4, 70, 9)


def x_rocket(img, d, ph):
    y = 150 - 110 * ph
    d.polygon([(120, y - 30), (134, y), (134, y + 22), (106, y + 22), (106, y)], fill=(240, 236, 255))
    d.ellipse([113, y - 6, 127, y + 8], fill=SKY)
    for k in range(5):
        fy = y + 26 + k * 7
        d.ellipse([120 - 6 + k, fy, 120 + 6 - k, fy + 6], fill=PINK if k % 2 else (255, 216, 107))


def x_moon(img, d, ph):
    # Полумесяц: круг минус сдвинутый круг — через маску, фон остаётся небом
    m = Image.new("L", (N, N), 0)
    md = ImageDraw.Draw(m)
    md.ellipse([80, 40, 160, 120], fill=255)
    off = 22 + 10 * math.sin(2 * math.pi * ph)
    md.ellipse([80 + off, 30, 160 + off, 110], fill=0)
    img.paste(Image.new("RGB", (N, N), LILAC), (0, 0), m.filter(ImageFilter.GaussianBlur(8)).point(lambda v: int(v * 0.5)))
    img.paste(Image.new("RGB", (N, N), (243, 238, 255)), (0, 0), m)
    for k, (x, y) in enumerate([(60, 60), (180, 140), (70, 130)]):
        sparkle(d, x, y, 5 + 4 * abs(math.sin(2 * math.pi * (ph + k / 3))))


def x_stars(img, d, ph):
    for k in range(6):
        a = 2 * math.pi * (k / 6 + ph)
        sparkle(d, 120 + 62 * math.cos(a), 95 + 40 * math.sin(a), 8 + 4 * math.sin(4 * a))
    diamond(d, 120, 95, 20, ORCHID)


def x_wave(img, d, ph):
    ang = 20 * math.sin(2 * math.pi * ph * 2)
    hand = Image.new("RGBA", (90, 90), (0, 0, 0, 0))
    hd = ImageDraw.Draw(hand)
    hd.text((10, 0), "", fill=(255, 255, 255))
    hd.rounded_rectangle([25, 30, 65, 80], 14, fill=(255, 216, 107))
    for k in range(4):
        hd.rounded_rectangle([25 + k * 10, 6 + abs(k - 1.5) * 4, 33 + k * 10, 40], 4, fill=(255, 216, 107))
    hand = hand.rotate(ang, center=(45, 80), resample=Image.BICUBIC)
    img.paste(hand, (75, 40), hand)


def x_hug(img, d, ph):
    s = 1 + 0.08 * math.sin(2 * math.pi * ph)
    heart(d, 105, 95, 30 * s, LILAC)
    heart(d, 138, 98, 30 * (2 - s), PINK)


def x_party(img, d, ph):
    rnd = random.Random(7)
    for k in range(26):
        x0 = rnd.random() * N
        sp = 0.6 + rnd.random()
        y = (rnd.random() * N + ph * N * sp) % (N * 0.75)
        col = rnd.choice([PINK, LILAC, SKY, (255, 216, 107), (123, 224, 176)])
        d.rectangle([x0, y, x0 + 5, y + 9], fill=col)


def x_coffee(img, d, ph):
    d.rounded_rectangle([88, 80, 140, 135], 10, fill=(243, 238, 255))
    d.arc([132, 92, 160, 120], -90, 90, fill=(243, 238, 255), width=6)
    for k in range(3):
        x = 100 + k * 14
        off = 6 * math.sin(2 * math.pi * (ph + k / 3))
        d.arc([x - 6 + off, 40, x + 6 + off, 75], 90, 270, fill=LILAC, width=3)


def x_yes(img, d, ph):
    s = min(1.0, ph * 3)
    d.ellipse([75, 45, 165, 135], outline=(123, 224, 176), width=6)
    pts = [(96, 92), (114, 110), (146, 74)]
    if s < 0.5:
        e = (pts[0][0] + (pts[1][0] - pts[0][0]) * s * 2, pts[0][1] + (pts[1][1] - pts[0][1]) * s * 2)
        d.line([pts[0], e], fill=(123, 224, 176), width=9)
    else:
        e = (pts[1][0] + (pts[2][0] - pts[1][0]) * (s - 0.5) * 2, pts[1][1] + (pts[2][1] - pts[1][1]) * (s - 0.5) * 2)
        d.line([pts[0], pts[1], e], fill=(123, 224, 176), width=9, joint="curve")


def x_wow(img, d, ph):
    r = 30 + 10 * abs(math.sin(2 * math.pi * ph))
    d.ellipse([120 - r, 90 - r, 120 + r, 90 + r], outline=(255, 216, 107), width=5)
    d.ellipse([105, 72, 115, 86], fill=(255, 255, 255))
    d.ellipse([125, 72, 135, 86], fill=(255, 255, 255))
    d.ellipse([112, 96, 128, 114], fill=(255, 255, 255))


def x_haha(img, d, ph):
    dy = 6 * math.sin(4 * math.pi * ph)
    d.ellipse([80, 50 + dy, 160, 130 + dy], fill=(255, 216, 107))
    d.arc([96, 70 + dy, 116, 86 + dy], 200, 340, fill=NIGHT, width=4)
    d.arc([124, 70 + dy, 144, 86 + dy], 200, 340, fill=NIGHT, width=4)
    d.chord([98, 88 + dy, 142, 120 + dy], 0, 180, fill=NIGHT)


def x_night(img, d, ph):
    x_moon(img, d, ph)


# id, подпись, рисунок — тот же порядок, что в Gifs.kt и Gifs.swift
GIFS = [
    ("hello", "Привет!", x_wave),
    ("thanks", "Спасибо", x_stars),
    ("love", "Люблю", x_heart),
    ("wow", "Ого!", x_wow),
    ("haha", "Ха-ха", x_haha),
    ("hug", "Обнимаю", x_hug),
    ("party", "Ура!", x_party),
    ("yes", "Да!", x_yes),
    ("morning", "Доброе утро", x_coffee),
    ("night", "Спокойной ночи", x_night),
    ("rocket", "Погнали!", x_rocket),
    ("crystal", "Memory", x_crystal),
]


def main():
    for d in (RAW, IOS_SND, ASSETS, IOS_GIF):
        os.makedirs(d, exist_ok=True)
    for name, sig in make_sounds().items():
        write_wav(os.path.join(RAW, f"snd_{name}.wav"), sig)
        write_wav(os.path.join(IOS_SND, f"snd_{name}.wav"), sig)
    for i, (name, text, extra) in enumerate(GIFS):
        make_gif(name, text, extra, seed=11 + i)
        with open(os.path.join(ASSETS, name + ".gif"), "rb") as src, open(os.path.join(IOS_GIF, name + ".gif"), "wb") as dst:
            dst.write(src.read())


if __name__ == "__main__":
    main()
