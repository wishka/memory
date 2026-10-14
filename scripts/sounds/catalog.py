"""Каталог звуков уведомлений Memory: 9 стилей, 57 звуков. Мелодии — свои.

Добавить звук: функция с @sound(...) в нужном стиле, затем
python3 scripts/make_sounds.py — он пишет файлы и код списка звуков.
"""
try:
    from engine import *  # noqa: F401,F403
except ImportError:
    # Без numpy/scipy (например, в CI) нужен только список звуков, не синтез
    note = None

N = note
CAT = []

# Бесплатно — по одному-два звука каждого стиля; остальные — в Memory+
FREE = {"bamboo", "bird", "bubble", "coin", "crystal", "dawn", "droplet", "glide", "jazz", "marimba", "ping", "pop", "pulse", "rhodes", "tick", "waterdrop"}

# Звук по умолчанию для каждого вида уведомлений (всегда доступен)
DEFAULTS = {"messages": "crystal", "prism": "prism", "reactions": "stardust", "follows": "orbit", "groups": "moon", "friends": "comet", "recap": "nebula"}


def sound(fid, family, ru, en, about, dur):
    def deco(fn):
        CAT.append(dict(id=fid, family=family, ru=ru, en=en, about=about, dur=dur, fn=fn, free=fid in FREE))
        return fn
    return deco


# ==================== 1. Стекло 2.0 — фирменные, но живые ====================
F = "glass"


@sound("crystal", F, "Кристалл", "Crystal", "Две стеклянные ноты вверх, лёгкий хвост — для сообщений", 1.3)
def _():
    m = Mix(1.3)
    m.place(glass(N("E6"), 1.2, 0.55, 1), 0, -0.25, 0.9)
    m.place(glass(N("B6"), 1.1, 0.5, 2), 0.085, 0.25, 0.75)
    return reverb(m.stereo(), 0.3, length=1.3, decay=0.4, bright=0.7)


@sound("stardust", F, "Звёздная пыль", "Stardust", "Россыпь из пяти колокольчиков вверх по пентатонике", 1.3)
def _():
    m = Mix(1.3)
    for i, n in enumerate(["C6", "D6", "E6", "G6", "C7"]):
        m.place(chime(N(n), 0.9, i), i * 0.045, -0.6 + 0.3 * i, 0.55 + 0.05 * i)
    return reverb(m.stereo(), 0.35, length=1.5, decay=0.5, bright=0.8)


@sound("moon", F, "Луна", "Moon", "Низкий мягкий колокол с биением и длинным хвостом", 1.8)
def _():
    m = Mix(1.8)
    m.place(bell(N("A4"), 1.8, 0.85, 3), 0, -0.1, 1)
    m.place(bell(N("A4") * 1.003, 1.8, 0.8, 4), 0.004, 0.1, 0.5)
    return reverb(m.stereo(), 0.35, length=1.8, decay=0.7, bright=0.4)


@sound("prism", F, "Призма", "Prism", "Стеклянный аккорд арпеджио с переливом", 1.4)
def _():
    m = Mix(1.4)
    for i, n in enumerate(["A5", "C#6", "E6", "A6"]):
        m.place(glass(N(n), 1.2, 0.6, i + 10), i * 0.03, -0.45 + 0.3 * i, 0.6)
    st = m.stereo()
    t = np.arange(st.shape[1]) / SR
    st = st * (1 + 0.12 * np.sin(2 * np.pi * 6.5 * t))
    return reverb(st, 0.35, length=1.5, decay=0.55, bright=0.7)


@sound("droplet", F, "Капля света", "Light drop", "Капля с бликом: «плинк» и стеклянная искра", 0.8)
def _():
    m = Mix(0.8)
    m.place(bubble(700, 1900, 0.12, 0.03), 0, 0, 0.9)
    m.place(glass(N("D7"), 0.6, 0.25, 5), 0.05, 0.3, 0.35)
    return reverb(m.stereo(), 0.3, length=1.0, decay=0.35, bright=0.8)


# ==================== 2. Маримба и калимба — тёплые, деревянные ====================
F = "wood"


@sound("marimba", F, "Маримба", "Marimba", "Тёплая двойная нота вверх — самый «дружелюбный» вариант", 1.0)
def _():
    m = Mix(1.0)
    m.place(marimba(N("G5"), 0.8, 1, 1), 0, -0.2)
    m.place(marimba(N("D6"), 0.8, 1, 2), 0.11, 0.2, 0.85)
    return reverb(m.stereo(), 0.2, length=0.9, decay=0.3, bright=0.5)


@sound("marimba3", F, "Маримба: три ноты", "Marimba trio", "Короткая фраза из трёх нот вверх — заметнее, для заявок и групп", 1.1)
def _():
    m = Mix(1.1)
    for i, (n, at) in enumerate([("E5", 0), ("A5", 0.1), ("C#6", 0.2)]):
        m.place(marimba(N(n), 0.8, 1, i), at, -0.3 + 0.3 * i, 0.8 + 0.1 * (i == 2))
    return reverb(m.stereo(), 0.22, length=1.0, decay=0.32, bright=0.5)


@sound("kalimba", F, "Калимба", "Kalimba", "Звенящий щипок язычков, как «пианино для пальцев»", 1.2)
def _():
    m = Mix(1.2)
    m.place(kalimba(N("C6"), 1.0, 1), 0, -0.25)
    m.place(kalimba(N("E6"), 1.0, 2), 0.09, 0.0, 0.8)
    m.place(kalimba(N("G6"), 1.0, 3), 0.18, 0.25, 0.7)
    return reverb(m.stereo(), 0.25, length=1.2, decay=0.4, bright=0.6)


@sound("vibes", F, "Вибрафон", "Vibes", "Мягкие металлические пластины с вибрато — спокойно и джазово", 1.6)
def _():
    m = Mix(1.6)
    m.place(vibraphone(N("F5"), 1.5, 1), 0, -0.2, 0.8)
    m.place(vibraphone(N("A5"), 1.5, 2), 0.0, 0.2, 0.6)
    m.place(vibraphone(N("E6"), 1.4, 3), 0.12, 0.0, 0.7)
    return reverb(m.stereo(), 0.3, length=1.6, decay=0.55, bright=0.5)


@sound("bamboo", F, "Бамбук", "Bamboo", "Два деревянных «ток-ток» — коротко и тихо", 0.5)
def _():
    m = Mix(0.5)
    m.place(wood(N("A5"), 0.25, 1), 0, -0.2)
    m.place(wood(N("E6"), 0.25, 2), 0.09, 0.2, 0.8)
    return reverb(m.stereo(), 0.15, length=0.6, decay=0.18, bright=0.5)


# ==================== 3. Поп и пузыри — как в мессенджерах ====================
F = "pop"


@sound("pop", F, "Поп", "Pop", "Короткий сочный «поп» — минимум, но приятно", 0.35)
def _():
    m = Mix(0.35)
    m.place(pop(1100, 320, 0.14, 0.02, 0.2), 0, 0, 1)
    return reverb(m.stereo(), 0.12, length=0.5, decay=0.12, bright=0.6)


@sound("bubble", F, "Пузырь", "Bubble", "Пузырёк, поднимающийся вверх — «бульк»", 0.4)
def _():
    m = Mix(0.4)
    m.place(bubble(450, 1500, 0.16, 0.045), 0, 0, 1)
    return reverb(m.stereo(), 0.15, length=0.5, decay=0.15, bright=0.6)


@sound("bloop", F, "Блуп-блуп", "Bloop", "Два пузыря подряд, второй выше — игриво", 0.55)
def _():
    m = Mix(0.55)
    m.place(bubble(420, 1200, 0.13, 0.04), 0, -0.25, 0.9)
    m.place(bubble(560, 1700, 0.13, 0.04), 0.1, 0.25, 0.9)
    return reverb(m.stereo(), 0.15, length=0.6, decay=0.15, bright=0.6)


@sound("taptap", F, "Тап-тап", "Tap-tap", "Двойной мягкий тап с тоном — почти как вибро", 0.4)
def _():
    m = Mix(0.4)
    for i, f in enumerate((1250, 1650)):
        x = pop(f, f * 0.7, 0.07, 0.01, 0.12)
        m.place(x, i * 0.085, -0.15 + 0.3 * i, 0.9)
    return reverb(m.stereo(), 0.1, length=0.4, decay=0.1, bright=0.7)


@sound("boing", F, "Пружинка", "Spring", "Весёлый «боинг» с отскоком — для реакций", 0.6)
def _():
    n = secs(0.6)
    t = np.arange(n) / SR
    f = 520 * (1 + 0.35 * np.exp(-t / 0.15) * np.sin(2 * np.pi * 14 * t))
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_exp(n, 0.002, 0.18)
    m = Mix(0.6)
    m.place(x, 0, 0, 1)
    return reverb(m.stereo(), 0.15, length=0.6, decay=0.2, bright=0.5)


@sound("swoosh", F, "Отправлено", "Sent", "Мягкий «вжух» и тихий тик — как отправка", 0.6)
def _():
    m = Mix(0.6)
    nz = noise(0.3, 4)
    nz = bp(nz, 1200, 6000) * env_adsr(len(nz), 0.12, 0.05, 0.6, 0.05)
    m.place(nz, 0, -0.5, 0.25)
    m.place(nz[::-1] * 0.0 + nz, 0.0, 0.5, 0.25)
    m.place(pop(1800, 1400, 0.06, 0.01, 0.3), 0.2, 0.2, 0.7)
    return reverb(m.stereo(), 0.15, length=0.6, decay=0.2, bright=0.7)


# ==================== 4. Электропиано и лоу-фай ====================
F = "keys"


@sound("rhodes", F, "Родес", "Rhodes", "Тёплый аккорд электропиано — уютно, как лоу-фай", 1.5)
def _():
    m = Mix(1.5)
    for i, n in enumerate(["D5", "F#5", "A5", "C#6"]):
        m.place(fm_ep(N(n), 1.4, 2.0, 0.8, i), i * 0.018, -0.3 + 0.2 * i, 0.5)
    st = m.stereo()
    t = np.arange(st.shape[1]) / SR
    st[0] *= 1 + 0.15 * np.sin(2 * np.pi * 4.2 * t)
    st[1] *= 1 + 0.15 * np.sin(2 * np.pi * 4.2 * t + np.pi)
    return reverb(st, 0.25, length=1.4, decay=0.5, bright=0.4)


@sound("lofi", F, "Лоу-фай", "Lo-fi", "Две ноты пиано с «плёночным» шорохом и дрожанием", 1.3)
def _():
    m = Mix(1.3)
    m.place(fm_ep(N("A5"), 1.2, 1.6, 0.6, 1), 0, -0.15, 0.8)
    m.place(fm_ep(N("E6"), 1.1, 1.6, 0.55, 2), 0.16, 0.15, 0.7)
    st = m.stereo()
    st = np.stack([lp(st[0], 5000), lp(st[1], 5000)])
    hiss = lp(noise(1.3, 9), 6000) * 0.012
    st = st + hiss
    return reverb(st, 0.2, length=1.2, decay=0.45, bright=0.3)


@sound("jazz", F, "Джаз", "Jazz", "Нота и мягкий квартовый аккорд — «слово по делу»", 1.3)
def _():
    m = Mix(1.3)
    m.place(fm_ep(N("G5"), 0.6, 1.8, 0.3, 1), 0, 0, 0.7)
    for i, n in enumerate(["C5", "F5", "Bb5", "Eb6"]):
        m.place(fm_ep(N(n), 1.1, 1.6, 0.6, i + 3), 0.14 + i * 0.012, -0.3 + 0.2 * i, 0.4)
    return reverb(m.stereo(), 0.25, length=1.3, decay=0.5, bright=0.4)


@sound("fmbell", F, "Цифровой колокол", "Digital bell", "Чистый FM-колокол, как в синтезаторах 80-х", 1.4)
def _():
    m = Mix(1.4)
    m.place(fm_bell(N("E6"), 1.3, 3.5, 2.5, 0.6), 0, -0.2, 0.7)
    m.place(fm_bell(N("B6"), 1.2, 3.5, 2.0, 0.5), 0.1, 0.2, 0.5)
    return reverb(m.stereo(), 0.3, length=1.4, decay=0.5, bright=0.6)


# ==================== 5. Синт — современный поп ====================
F = "synth"


@sound("spark", F, "Искра", "Spark", "Быстрое яркое арпеджио вверх — заметно и празднично", 1.0)
def _():
    m = Mix(1.0)
    for i, n in enumerate(["C6", "E6", "G6", "C7"]):
        m.place(synth_pluck(N(n), 0.5, (6000, 600), 0.06, 3, 10, 0.18, i), i * 0.055, -0.45 + 0.3 * i, 0.55)
    return reverb(m.stereo(), 0.3, length=1.2, decay=0.4, bright=0.8)


@sound("neon", F, "Неон", "Neon", "Плак с октавой и отзвуком — как в электронной музыке", 1.1)
def _():
    m = Mix(1.1)
    x = synth_pluck(N("A5"), 0.6, (5000, 500), 0.07, 5, 14, 0.22, 1)
    m.place(x, 0, -0.1, 0.8)
    m.place(synth_pluck(N("A6"), 0.5, (8000, 600), 0.05, 3, 10, 0.15, 2), 0.0, 0.1, 0.35)
    m.place(x * 0.35, 0.18, 0.6)
    m.place(x * 0.15, 0.36, -0.6)
    return reverb(m.stereo(), 0.25, length=1.1, decay=0.4, bright=0.7)


@sound("pulse", F, "Пульс", "Pulse", "Два синт-плака — «тин-тин», чётко и современно", 0.8)
def _():
    m = Mix(0.8)
    m.place(synth_pluck(N("E6"), 0.4, (5500, 700), 0.04, 3, 8, 0.1, 1), 0, -0.2, 0.8)
    m.place(synth_pluck(N("E6"), 0.4, (5500, 700), 0.04, 3, 8, 0.12, 2), 0.13, 0.2, 0.8)
    return reverb(m.stereo(), 0.22, length=0.9, decay=0.3, bright=0.7)


@sound("future", F, "Фьючер", "Future", "Аккорд-стаб на «суперпиле» — ярко, для PRISM", 1.0)
def _():
    m = Mix(1.0)
    for i, n in enumerate(["F5", "A5", "C6", "E6"]):
        m.place(synth_pluck(N(n), 0.7, (6000, 900), 0.12, 5, 18, 0.28, i), 0, -0.3 + 0.2 * i, 0.4)
    return reverb(m.stereo(), 0.3, length=1.2, decay=0.45, bright=0.6)


@sound("glide", F, "Скольжение", "Glide", "Нота, плавно взлетающая на квинту — «уиии»", 0.8)
def _():
    n = secs(0.8)
    t = np.arange(n) / SR
    f = N("D5") * 1.5 ** np.clip(t / 0.18, 0, 1)
    x = np.zeros(n)
    for k, a in ((1, 1), (2, 0.3), (3, 0.12)):
        x += a * np.sin(2 * np.pi * np.cumsum(f * k) / SR)
    x *= env_exp(n, 0.01, 0.3)
    m = Mix(0.8)
    m.place(x, 0, 0, 1)
    return reverb(m.stereo(), 0.3, length=1.0, decay=0.4, bright=0.6)


# ==================== 6. Амбиент — мягкие, для итогов и групп ====================
F = "ambient"


@sound("dawn", F, "Рассвет", "Dawn", "Аккорд медленно «раскрывается» и гаснет", 1.8)
def _():
    m = Mix(1.8)
    m.place(pad([N("D5"), N("F#5"), N("A5"), N("E6")], 1.6, 0.35, 0.9, 3, 9, 3000, 1), 0, 0, 1)
    m.place(chime(N("A6"), 1.2, 2), 0.3, 0.4, 0.3)
    return reverb(m.stereo(), 0.4, length=2.0, decay=0.8, bright=0.6)


@sound("aurora", F, "Сияние", "Aurora", "Мерцающий высокий пэд с блёстками", 1.8)
def _():
    m = Mix(1.8)
    m.place(pad([N("A5"), N("E6"), N("B6")], 1.6, 0.25, 0.9, 4, 14, 5000, 4), 0, 0, 0.8)
    for i, n in enumerate(["E7", "B6", "F#7"]):
        m.place(glass(N(n), 0.8, 0.3, i), 0.2 + i * 0.17, (-0.6, 0.6, 0)[i], 0.18)
    return reverb(m.stereo(), 0.45, length=2.0, decay=0.8, bright=0.8)


@sound("deep", F, "Глубина", "Deep", "Низкий тёплый гул и одна нота — для итогов месяца", 1.8)
def _():
    m = Mix(1.8)
    m.place(pad([N("A3"), N("E4"), N("A4")], 1.6, 0.3, 0.9, 3, 6, 1200, 7), 0, 0, 1)
    m.place(bell(N("E5"), 1.5, 0.8, 8), 0.12, 0.2, 0.35)
    return reverb(m.stereo(), 0.4, length=2.0, decay=0.9, bright=0.3)


# ==================== 7. 8-бит — игровые ====================
F = "8bit"


def chip(f, dur, duty=0.25, decay=0.2):
    x = square(f, dur, duty)
    return x * env_exp(len(x), 0.001, decay)


@sound("coin", F, "Монетка", "Coin", "Классическое «динь» с прыжком вверх", 0.6)
def _():
    m = Mix(0.6)
    m.place(chip(N("B5"), 0.08, 0.25, 0.5), 0, 0, 0.6)
    m.place(chip(N("E6"), 0.45, 0.25, 0.22), 0.07, 0, 0.6)
    return reverb(m.stereo(), 0.1, length=0.5, decay=0.15, bright=0.5)


@sound("levelup", F, "Новый уровень", "Level up", "Быстрая восходящая фраза — для достижений и итогов", 0.9)
def _():
    m = Mix(0.9)
    for i, n in enumerate(["C5", "E5", "G5", "C6", "E6", "G6"]):
        m.place(chip(N(n), 0.12, 0.5, 0.08), i * 0.06, -0.3 + 0.12 * i, 0.5)
    m.place(chip(N("C7"), 0.4, 0.5, 0.18), 0.36, 0.3, 0.5)
    return reverb(m.stereo(), 0.12, length=0.6, decay=0.2, bright=0.5)


@sound("powerup", F, "Бонус", "Power-up", "Свип вверх с трелью — весело", 0.7)
def _():
    n = secs(0.7)
    t = np.arange(n) / SR
    f = 300 * (6 ** np.clip(t / 0.45, 0, 1)) * (1 + 0.04 * np.sign(np.sin(2 * np.pi * 30 * t)))
    ph = 2 * np.pi * np.cumsum(f) / SR
    # мягкий «квадрат» без алиасинга: tanh от синуса
    x = np.tanh(4 * np.sin(ph)) * 0.5
    x = lp(x, 6000) * env_adsr(n, 0.005, 0.05, 0.8, 0.12, 0.35)
    m = Mix(0.7)
    m.place(x, 0, 0, 0.6)
    return reverb(m.stereo(), 0.1, length=0.5, decay=0.15, bright=0.5)


@sound("pixel", F, "Пиксель", "Pixel", "Короткий «блип-блип» — тихий 8-бит", 0.35)
def _():
    m = Mix(0.35)
    m.place(chip(N("A6"), 0.06, 0.125, 0.04), 0, -0.2, 0.5)
    m.place(chip(N("E7"), 0.08, 0.125, 0.05), 0.07, 0.2, 0.5)
    return reverb(m.stereo(), 0.08, length=0.3, decay=0.1, bright=0.5)


# ==================== 8. Минимал — почти незаметные ====================
F = "minimal"


@sound("tick", F, "Тик", "Tick", "Одиночный стеклянный тик — самое тихое", 0.3)
def _():
    m = Mix(0.3)
    m.place(glass(N("A7"), 0.25, 0.04, 1), 0, 0, 0.8)
    m.place(pop(2600, 2000, 0.03, 0.005, 0.4), 0, 0, 0.4)
    return reverb(m.stereo(), 0.12, length=0.4, decay=0.12, bright=0.7)


@sound("knock", F, "Тук-тук", "Knock", "Два деревянных стука, как в дверь", 0.45)
def _():
    m = Mix(0.45)
    m.place(wood(N("C5"), 0.2, 1), 0, -0.1, 1)
    m.place(wood(N("C5") * 1.02, 0.2, 2), 0.12, 0.1, 0.85)
    return reverb(m.stereo(), 0.12, length=0.5, decay=0.15, bright=0.4)


@sound("softtap", F, "Мягкий тап", "Soft tap", "Приглушённый тон-тап с округлым звуком", 0.35)
def _():
    m = Mix(0.35)
    x = modal(N("E5"), 0.3, [(1, 1, 0.07), (2.0, 0.3, 0.03)], attack=0.002, mallet=0.15, mallet_hi=2500)
    m.place(x, 0, 0, 1)
    return reverb(m.stereo(), 0.1, length=0.4, decay=0.12, bright=0.3)


@sound("ping", F, "Пинг", "Ping", "Одна чистая высокая нота с коротким хвостом", 0.7)
def _():
    m = Mix(0.7)
    m.place(chime(N("E6"), 0.6, 1), 0, 0, 1)
    return reverb(m.stereo(), 0.25, length=0.8, decay=0.3, bright=0.6)


# ==================== 9. Природа ====================
F = "nature"


@sound("waterdrop", F, "Капля", "Water drop", "Настоящая капля в воду: «плюх» с резонансом", 0.5)
def _():
    m = Mix(0.5)
    m.place(bubble(900, 2600, 0.09, 0.02), 0, 0, 0.9)
    nz = bp(noise(0.03, 3), 2000, 8000) * np.exp(-np.arange(secs(0.03)) / (SR * 0.004))
    m.place(nz, 0, 0, 0.15)
    m.place(bubble(1200, 2200, 0.06, 0.015), 0.11, 0.3, 0.25)
    return reverb(m.stereo(), 0.3, length=0.8, decay=0.3, bright=0.6)


@sound("bird", F, "Птичка", "Bird", "Короткая трель: «чирик-чирик»", 0.6)
def _():
    m = Mix(0.6)
    for i in range(3):
        x = chirp(3200 + 300 * i, 5200 + 200 * i, 0.06, 0.05)
        m.place(x, i * 0.09, -0.2 + 0.2 * i, 0.5)
    m.place(chirp(4800, 2900, 0.12, 0.1), 0.3, 0.2, 0.5)
    return reverb(m.stereo(), 0.25, length=0.8, decay=0.3, bright=0.6)


@sound("windchime", F, "Колокольчики ветра", "Wind chimes", "Несколько колокольчиков вразнобой, как на ветру", 1.8)
def _():
    m = Mix(1.8)
    rng = np.random.default_rng(3)
    for i, n in enumerate(["G6", "E6", "C7", "A6", "D7"]):
        m.place(chime(N(n), 1.3, i + 20), i * 0.11 + rng.uniform(0, 0.04), rng.uniform(-0.7, 0.7), 0.45)
    return reverb(m.stereo(), 0.4, length=1.8, decay=0.7, bright=0.8)


@sound("string", F, "Струна", "String", "Щипок струны и квинта — акустика, как гитара", 1.3)
def _():
    m = Mix(1.3)
    m.place(pluck_string(N("E4"), 1.2, 0.997, 0.6, 1), 0, -0.15, 0.8)
    m.place(pluck_string(N("B4"), 1.1, 0.997, 0.6, 2), 0.09, 0.15, 0.7)
    return reverb(m.stereo(), 0.2, length=1.2, decay=0.45, bright=0.4)



# ==================== Вторая волна ====================

@sound("comet", "glass", "Комета", "Comet", "Шорох пролетающей кометы и стеклянный «дзинь» в конце", 1.3)
def _():
    m = Mix(1.3)
    nz = noise(0.5, 3)
    nz = sweep_lp(nz, 9000, 1500, 0.2) * env_adsr(len(nz), 0.18, 0.05, 0.7, 0.08, 0.1)
    m.place(nz, 0, -0.7, 0.18)
    m.place(nz[::-1] * 0.6, 0.05, 0.7, 0.15)
    m.place(glass(N("G6"), 1.0, 0.5, 4), 0.32, 0.2, 0.8)
    m.place(glass(N("D7"), 0.8, 0.35, 5), 0.36, 0.4, 0.3)
    return reverb(m.stereo(), 0.35, length=1.4, decay=0.5, bright=0.7)


@sound("nebula", "glass", "Туманность", "Nebula", "Воздушная волна с мерцающими стеклянными призвуками", 1.6)
def _():
    m = Mix(1.6)
    m.place(pad([N("E5"), N("B5"), N("G#6")], 1.4, 0.4, 0.8, 4, 12, 4200, 2), 0, 0, 0.8)
    air = lp(hp(noise(1.4, 6), 2500), 9000) * env_adsr(secs(1.4), 0.5, 0.1, 0.6, 0.25, 0.3)
    m.place(air, 0, 0, 0.05)
    for i, n in enumerate(["B6", "E7"]):
        m.place(glass(N(n), 0.8, 0.35, i + 30), 0.35 + i * 0.2, (-0.5, 0.5)[i], 0.2)
    return reverb(m.stereo(), 0.45, length=1.8, decay=0.75, bright=0.7)


@sound("orbit", "glass", "Орбита", "Orbit", "Две ноты, облетающие слева направо, с эхом", 1.4)
def _():
    m = Mix(1.4)
    for k in range(4):
        g = 0.85 * 0.5 ** k
        m.place(glass(N("D6"), 0.7, 0.3, k), k * 0.17, -0.8 + 0.5 * k, g)
        m.place(glass(N("A6"), 0.7, 0.3, k + 9), 0.085 + k * 0.17, 0.8 - 0.5 * k, g * 0.8)
    return reverb(m.stereo(), 0.3, length=1.3, decay=0.45, bright=0.7)


def handpan(f, dur=1.6, seed=0):
    x = modal(f, dur, [(1, 1.0, 0.9), (2.0, 0.45, 0.6), (3.0, 0.2, 0.35), (0.5, 0.15, 1.0), (4.03, 0.06, 0.2)],
              attack=0.004, mallet=0.12, mallet_hi=2500, seed=seed)
    t = np.arange(len(x)) / SR
    return x * (1 + 0.06 * np.sin(2 * np.pi * 3.1 * t))


@sound("handpan", "wood", "Ханг", "Handpan", "Мягкий металлический «ханг» — медитативно и модно", 1.7)
def _():
    m = Mix(1.7)
    m.place(handpan(N("D5"), 1.6, 1), 0, -0.2, 0.9)
    m.place(handpan(N("A5"), 1.5, 2), 0.16, 0.2, 0.75)
    return reverb(m.stereo(), 0.3, length=1.6, decay=0.6, bright=0.4)


@sound("xylophone", "wood", "Ксилофон", "Xylophone", "Быстрая яркая тройка нот — звонко и бодро", 0.9)
def _():
    m = Mix(0.9)
    for i, n in enumerate(["G6", "B6", "D7"]):
        x = modal(N(n), 0.5, [(1, 1.0, 0.22), (3.0, 0.3, 0.06), (6.0, 0.1, 0.03)], attack=0.0008, mallet=0.4, mallet_hi=8000, seed=i)
        m.place(x, i * 0.06, -0.3 + 0.3 * i, 0.75)
    return reverb(m.stereo(), 0.2, length=0.9, decay=0.3, bright=0.6)


@sound("musicbox", "wood", "Шкатулка", "Music box", "Мелодия из музыкальной шкатулки — четыре ноты", 1.6)
def _():
    m = Mix(1.6)
    for i, (n, at) in enumerate([("E6", 0), ("G6", 0.14), ("C7", 0.28), ("B6", 0.5)]):
        x = modal(N(n), 1.1, [(1, 1.0, 0.6), (5.0, 0.12, 0.08), (11.3, 0.04, 0.03)], attack=0.0008, mallet=0.08, mallet_hi=9000, seed=i)
        m.place(x, at, -0.2 + 0.13 * i, 0.6)
    return reverb(m.stereo(), 0.3, length=1.4, decay=0.5, bright=0.6)


@sound("popchime", "pop", "Поп-звон", "Pop chime", "«Поп» и маленький звонкий аккорд — для лайков", 0.8)
def _():
    m = Mix(0.8)
    m.place(pop(1000, 300, 0.12, 0.02, 0.15), 0, 0, 0.8)
    m.place(chime(N("E6"), 0.6, 1), 0.05, -0.3, 0.35)
    m.place(chime(N("G#6"), 0.6, 2), 0.05, 0.3, 0.3)
    return reverb(m.stereo(), 0.22, length=0.9, decay=0.3, bright=0.7)


@sound("celesta", "keys", "Челеста", "Celesta", "Сказочные колокольчики-клавиши — тихо и волшебно", 1.4)
def _():
    m = Mix(1.4)
    for i, (n, at) in enumerate([("C7", 0), ("G6", 0.1), ("E7", 0.2)]):
        x = modal(N(n), 1.1, [(1, 1.0, 0.5), (4.0, 0.15, 0.1), (2.0, 0.1, 0.25)], attack=0.001, mallet=0.06, mallet_hi=8000, seed=i)
        m.place(x, at, -0.3 + 0.3 * i, 0.6)
    return reverb(m.stereo(), 0.35, length=1.4, decay=0.55, bright=0.7)


@sound("laser", "synth", "Лазер", "Laser", "Короткий «пиу» вниз — для игривых уведомлений", 0.45)
def _():
    n = secs(0.45)
    t = np.arange(n) / SR
    f = 300 + 2600 * np.exp(-t / 0.05)
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.tanh(2.5 * np.sin(ph)) * env_exp(n, 0.002, 0.09)
    m = Mix(0.45)
    m.place(lp(x, 7000), 0, 0, 0.7)
    m.place(lp(x, 7000) * 0.3, 0.09, 0.5)
    return reverb(m.stereo(), 0.2, length=0.6, decay=0.25, bright=0.6)


@sound("wave", "synth", "Волна", "Wave", "Аккорд накатывает и откатывает, как волна", 1.2)
def _():
    m = Mix(1.2)
    n = secs(1.1)
    t = np.arange(n) / SR
    sw = np.sin(np.pi * np.clip(t / 1.0, 0, 1)) ** 1.5
    for i, f in enumerate([N("A4"), N("E5"), N("C#6")]):
        x = sweep_lp(saw(f, 1.1, 14, 3, i), 400, 5000, 0.4) * sw
        m.place(x, 0, -0.4 + 0.4 * i, 0.3)
    return reverb(m.stereo(), 0.3, length=1.3, decay=0.5, bright=0.6)


@sound("space", "ambient", "Космос", "Space", "Глубокий гул и далёкий сигнал — как из космоса", 2.0)
def _():
    m = Mix(2.0)
    m.place(pad([N("C3"), N("G3"), N("D4")], 1.8, 0.4, 1.0, 3, 5, 900, 11), 0, 0, 1)
    for i in range(3):
        m.place(chime(N("G6"), 0.5, i), 0.3 + i * 0.32, (-0.6, 0.6, 0)[i], 0.22 * 0.6 ** i)
    return reverb(m.stereo(), 0.5, length=2.2, decay=1.0, bright=0.4)


def chip_tone(f, dur, duty=0.25, decay=0.2):
    x = square(f, dur, duty)
    return x * env_exp(len(x), 0.001, decay)


@sound("chipmsg", "8bit", "Пиксельное сообщение", "Pixel message", "Две 8-битные ноты «тиу-тиу» — как в старых играх", 0.5)
def _():
    m = Mix(0.5)
    m.place(chip_tone(N("E6"), 0.1, 0.5, 0.06), 0, -0.2, 0.5)
    m.place(chip_tone(N("A6"), 0.25, 0.5, 0.1), 0.1, 0.2, 0.5)
    return reverb(m.stereo(), 0.1, length=0.4, decay=0.12, bright=0.5)


@sound("chest", "8bit", "Сундук", "Treasure", "Открытый сундук: быстрое арпеджио и «тадам»", 1.1)
def _():
    m = Mix(1.1)
    for i, n in enumerate(["G5", "A5", "B5", "C6", "D6", "E6", "F#6"]):
        m.place(chip_tone(N(n), 0.07, 0.25, 0.05), i * 0.045, 0, 0.4)
    for n in ["G5", "B5", "D6", "G6"]:
        m.place(chip_tone(N(n), 0.55, 0.5, 0.25), 0.36, 0, 0.25)
    return reverb(m.stereo(), 0.12, length=0.6, decay=0.2, bright=0.5)


@sound("switch", "minimal", "Переключатель", "Switch", "Тихий щелчок тумблера с лёгким тоном", 0.3)
def _():
    m = Mix(0.3)
    m.place(wood(N("C7"), 0.08, 1), 0, -0.1, 0.6)
    m.place(wood(N("G6"), 0.08, 2), 0.045, 0.1, 0.45)
    return reverb(m.stereo(), 0.08, length=0.3, decay=0.08, bright=0.5)


@sound("ding", "minimal", "Дзынь", "Ding", "Маленький колокольчик — одна нота, коротко", 0.8)
def _():
    m = Mix(0.8)
    m.place(bell(N("A6"), 0.7, 0.35, 1), 0, 0, 1)
    return reverb(m.stereo(), 0.2, length=0.8, decay=0.3, bright=0.6)


@sound("cricket", "nature", "Сверчок", "Cricket", "Летняя ночь: три коротких стрекотания", 0.8)
def _():
    m = Mix(0.8)
    for k in range(3):
        n = secs(0.09)
        t = np.arange(n) / SR
        x = np.sin(2 * np.pi * 4600 * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 55 * t)) ** 3 * env_adsr(n, 0.005, 0.01, 1, 0.02, 0.05)
        m.place(lp(x, 8000), k * 0.15, 0.3, 0.6)
    return reverb(m.stereo(), 0.3, length=0.9, decay=0.35, bright=0.5)


@sound("harp", "nature", "Арфа", "Harp", "Глиссандо арфы вверх — нежно и торжественно", 1.5)
def _():
    m = Mix(1.5)
    for i, n in enumerate(["C5", "E5", "G5", "C6", "E6", "G6", "C7"]):
        m.place(pluck_string(N(n), 1.1, 0.998, 0.35, i), i * 0.035, -0.6 + 0.2 * i, 0.4)
    return reverb(m.stereo(), 0.35, length=1.5, decay=0.6, bright=0.5)

# Армянские названия звуков и стилей
HY_NAMES = {'crystal': 'Բյուրեղ', 'stardust': 'Աստղափոշի', 'moon': 'Լուսին', 'prism': 'Պրիզմա', 'droplet': 'Լույսի կաթիլ', 'comet': 'Գիսաստղ', 'nebula': 'Միգամածություն', 'orbit': 'Ուղեծիր', 'marimba': 'Մարիմբա', 'marimba3': 'Մարիմբա․ երեք նոտա', 'kalimba': 'Կալիմբա', 'vibes': 'Վիբրաֆոն', 'bamboo': 'Բամբուկ', 'handpan': 'Հանգ', 'xylophone': 'Քսիլոֆոն', 'musicbox': 'Երաժշտական տուփ', 'pop': 'Փոփ', 'bubble': 'Պղպջակ', 'bloop': 'Բլուպ-բլուպ', 'taptap': 'Թափ-թափ', 'boing': 'Զսպանակ', 'swoosh': 'Ուղարկված է', 'popchime': 'Փոփ-զանգ', 'rhodes': 'Ռոդս', 'lofi': 'Լո-ֆայ', 'jazz': 'Ջազ', 'fmbell': 'Թվային զանգ', 'celesta': 'Չելեստա', 'spark': 'Կայծ', 'neon': 'Նեոն', 'pulse': 'Զարկ', 'future': 'Ֆյուչեր', 'glide': 'Սահք', 'laser': 'Լազեր', 'wave': 'Ալիք', 'dawn': 'Արշալույս', 'aurora': 'Բևեռափայլ', 'deep': 'Խորություն', 'space': 'Տիեզերք', 'coin': 'Մետաղադրամ', 'levelup': 'Նոր մակարդակ', 'powerup': 'Բոնուս', 'pixel': 'Պիքսել', 'chipmsg': 'Պիքսելային հաղորդագրություն', 'chest': 'Սնդուկ', 'tick': 'Տիկ', 'knock': 'Թակ-թակ', 'softtap': 'Փափուկ հպում', 'ping': 'Պինգ', 'switch': 'Անջատիչ', 'ding': 'Զնգոց', 'waterdrop': 'Կաթիլ', 'bird': 'Թռչնակ', 'windchime': 'Քամու զանգակներ', 'string': 'Լար', 'cricket': 'Ծղրիդ', 'harp': 'Տավիղ'}

FAMILIES_HY = {'glass': 'Ապակի', 'wood': 'Մարիմբա և կալիմբա', 'pop': 'Փոփ և պղպջակներ', 'keys': 'Էլեկտրադաշնամուր և լո-ֆայ', 'synth': 'Սինթ', 'ambient': 'Էմբիենթ', '8bit': '8-բիթ', 'minimal': 'Մինիմալ', 'nature': 'Բնություն'}

FAMILIES_EN = {"glass": "Glass", "wood": "Marimba & kalimba", "pop": "Pops & bubbles", "keys": "Keys & lo-fi", "synth": "Synth", "ambient": "Ambient", "8bit": "8-bit", "minimal": "Minimal", "nature": "Nature"}

FAMILIES = {
    "glass": ("Стекло 2.0", "Фирменный стиль Memory, но богаче: живые призвуки, стерео, настоящий хвост"),
    "wood": ("Маримба и калимба", "Тёплые деревянные звуки — сейчас самые популярные у приложений"),
    "pop": ("Поп и пузыри", "Короткие «мессенджерные» звуки: поп, бульк, тап"),
    "keys": ("Электропиано и лоу-фай", "Уютные аккорды, как в лоу-фай плейлистах"),
    "synth": ("Синт", "Яркие плаки из современной электронной музыки"),
    "ambient": ("Амбиент", "Мягкие, медленные — для итогов и групп"),
    "8bit": ("8-бит", "Игровые: монетка, новый уровень"),
    "minimal": ("Минимал", "Почти незаметные — тик, тап, пинг"),
    "nature": ("Природа", "Капля, птичка, колокольчики, струна"),
}
