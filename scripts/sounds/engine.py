"""Синтез звуков уведомлений Memory — новый движок.

48 кГц, стерео, модальный синтез (маримба, калимба, стекло, колокол),
FM (электропиано, колокол), Karplus–Strong (струна), субтрактивный синт
с огибающей фильтра, «поп» с падением высоты, свёрточная реверберация,
мягкое ограничение и выравнивание громкости.
"""
import numpy as np
from scipy.signal import butter, sosfilt, fftconvolve, lfilter

SR = 48000


def secs(n):
    return int(round(n * SR))


def t_axis(dur):
    return np.arange(secs(dur)) / SR


def note(n):
    """Частота по имени: 'A4', 'C#5', 'Eb6'."""
    names = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
    s = n[0]
    acc = 0
    i = 1
    while i < len(n) and n[i] in "#b":
        acc += 1 if n[i] == "#" else -1
        i += 1
    octave = int(n[i:])
    midi = 12 * (octave + 1) + names[s] + acc
    return 440.0 * 2 ** ((midi - 69) / 12)


# ---------- огибающие ----------

def env_exp(n, attack=0.003, decay=0.4, curve_start=1.0):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-5), 0, 1)
    a = np.sin(a * np.pi / 2) ** 2          # мягкая атака без щелчка
    return a * np.exp(-t / decay) * curve_start


def env_adsr(n, a=0.01, d=0.1, s=0.6, r=0.3, hold=0.0):
    t = np.arange(n) / SR
    out = np.zeros(n)
    out = np.where(t < a, t / a, out)
    m = (t >= a) & (t < a + d)
    out = np.where(m, 1 - (1 - s) * (t - a) / d, out)
    m = (t >= a + d) & (t < a + d + hold)
    out = np.where(m, s, out)
    tr = t - (a + d + hold)
    out = np.where(tr >= 0, s * np.exp(-np.maximum(tr, 0) / r), out)
    return out


# ---------- фильтры ----------

def lp(x, f, order=2):
    sos = butter(order, min(f, SR * 0.45), "low", fs=SR, output="sos")
    return sosfilt(sos, x)


def hp(x, f, order=2):
    sos = butter(order, f, "high", fs=SR, output="sos")
    return sosfilt(sos, x)


def bp(x, lo, hi, order=2):
    sos = butter(order, [lo, min(hi, SR * 0.45)], "band", fs=SR, output="sos")
    return sosfilt(sos, x)


def sweep_lp(x, f0, f1, tau, q=0.9, block=64):
    """Фильтр с огибающей: срез идёт от f0 к f1 экспоненциально (tau, с)."""
    out = np.zeros_like(x)
    z = np.zeros(2)
    for i in range(0, len(x), block):
        tt = i / SR
        f = f1 + (f0 - f1) * np.exp(-tt / tau)
        f = min(max(f, 40), SR * 0.45)
        w0 = 2 * np.pi * f / SR
        alpha = np.sin(w0) / (2 * q)
        c = np.cos(w0)
        b = np.array([(1 - c) / 2, 1 - c, (1 - c) / 2]) / (1 + alpha)
        a = np.array([1, -2 * c / (1 + alpha), (1 - alpha) / (1 + alpha)])
        seg = x[i:i + block]
        y, z = lfilter(b, a, seg, zi=z)
        out[i:i + block] = y
    return out


# ---------- источники ----------

def modal(freq, dur, modes, attack=0.002, mallet=0.0, mallet_hi=6000, seed=0):
    """Ударный инструмент: моды (множитель, громкость, затухание с)."""
    n = secs(dur)
    t = np.arange(n) / SR
    out = np.zeros(n)
    rng = np.random.default_rng(seed)
    for ratio, amp, dec in modes:
        f = freq * ratio
        if f > SR * 0.45:
            continue
        ph = rng.uniform(0, 0.3)
        out += amp * np.sin(2 * np.pi * f * t + ph) * env_exp(n, attack, dec)
    if mallet > 0:
        k = secs(0.012)
        nz = rng.standard_normal(k) * np.exp(-np.arange(k) / (SR * 0.0025))
        nz = bp(nz, 800, mallet_hi)
        out[:k] += nz * mallet
    return out


def marimba(f, dur=0.9, bright=1.0, seed=0):
    return modal(f, dur, [(1, 1.0, 0.42), (3.93, 0.32 * bright, 0.12), (9.2, 0.08 * bright, 0.05), (2.0, 0.06, 0.2)],
                 attack=0.0015, mallet=0.35 * bright, mallet_hi=4000, seed=seed)


def vibraphone(f, dur=1.4, seed=0):
    x = modal(f, dur, [(1, 1.0, 0.9), (3.98, 0.25, 0.3), (9.8, 0.06, 0.1)], attack=0.002, mallet=0.15, seed=seed)
    t = np.arange(len(x)) / SR
    return x * (1 + 0.22 * np.sin(2 * np.pi * 5.5 * t))


def kalimba(f, dur=1.0, seed=0):
    return modal(f, dur, [(1, 1.0, 0.55), (5.4, 0.18, 0.08), (6.27, 0.08, 0.06), (13.9, 0.03, 0.03)],
                 attack=0.001, mallet=0.25, mallet_hi=7000, seed=seed)


def glass(f, dur=1.2, decay=0.6, seed=0):
    return modal(f, dur, [(1, 1.0, decay), (2.32, 0.28, decay * 0.55), (4.25, 0.12, decay * 0.35), (6.63, 0.05, decay * 0.2)],
                 attack=0.001, mallet=0.08, mallet_hi=9000, seed=seed)


def bell(f, dur=1.6, decay=0.9, seed=0):
    return modal(f, dur, [(0.5, 0.25, decay * 1.2), (1, 1.0, decay), (1.19, 0.35, decay * 0.8), (1.5, 0.2, decay * 0.6),
                          (2.0, 0.3, decay * 0.5), (2.74, 0.12, decay * 0.3), (3.76, 0.06, decay * 0.2)],
                 attack=0.0015, mallet=0.06, seed=seed)


def chime(f, dur=1.4, seed=0):
    return modal(f, dur, [(1, 1.0, 0.8), (2.76, 0.4, 0.4), (5.4, 0.2, 0.2), (8.93, 0.08, 0.1)], attack=0.001, mallet=0.05, seed=seed)


def wood(f, dur=0.25, seed=0):
    return modal(f, dur, [(1, 1.0, 0.045), (2.57, 0.5, 0.025), (4.1, 0.2, 0.015)], attack=0.0008, mallet=0.6, mallet_hi=5000, seed=seed)


def fm_ep(f, dur=1.2, index=2.2, decay=0.7, seed=0):
    """Электропиано (FM, как у Rhodes/DX): яркая атака, тёплый хвост."""
    n = secs(dur)
    t = np.arange(n) / SR
    idx = index * np.exp(-t / 0.18) + 0.25
    mod = np.sin(2 * np.pi * f * 1.0 * t) * idx
    car = np.sin(2 * np.pi * f * t + mod)
    tine = np.sin(2 * np.pi * f * 14 * t) * np.exp(-t / 0.012) * 0.15
    return (car + tine) * env_exp(n, 0.002, decay)


def fm_bell(f, dur=1.6, ratio=3.5, index=3.0, decay=0.9):
    n = secs(dur)
    t = np.arange(n) / SR
    idx = index * np.exp(-t / 0.5)
    car = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * ratio * t))
    return car * env_exp(n, 0.002, decay)


def pluck_string(f, dur=1.0, damp=0.996, bright=0.5, seed=0):
    """Karplus–Strong: щипок струны."""
    n = secs(dur)
    p = max(2, int(SR / f))
    rng = np.random.default_rng(seed)
    buf = rng.uniform(-1, 1, p)
    buf = lp(buf, 2000 + 8000 * bright, 1)
    out = np.zeros(n)
    for i in range(n):
        out[i] = buf[i % p]
        nxt = buf[(i + 1) % p]
        buf[i % p] = damp * 0.5 * (buf[i % p] + nxt)
    return out * env_exp(n, 0.001, dur * 0.8)


def saw(f, dur, detune=0.0, voices=1, seed=0):
    """Пила без алиасинга (сумма гармоник), можно «суперпилу»."""
    n = secs(dur)
    t = np.arange(n) / SR
    out = np.zeros(n)
    rng = np.random.default_rng(seed)
    for v in range(voices):
        dv = (v - (voices - 1) / 2) * detune if voices > 1 else 0
        fv = f * 2 ** (dv / 1200)
        ph = rng.uniform(0, 2 * np.pi)
        k_max = int((SR * 0.45) // fv)
        for k in range(1, min(k_max, 60) + 1):
            out += np.sin(2 * np.pi * fv * k * t + ph * k) / k
    return out / voices


def square(f, dur, duty=0.5):
    n = secs(dur)
    t = np.arange(n) / SR
    out = np.zeros(n)
    k_max = int((SR * 0.45) // f)
    for k in range(1, min(k_max, 40) + 1):
        out += np.sin(np.pi * k * duty) / k * np.cos(2 * np.pi * f * k * t)
    return out


def synth_pluck(f, dur=0.6, cutoff=(6000, 400), tau=0.08, voices=3, detune=12, decay=0.25, seed=0):
    x = saw(f, dur, detune, voices, seed)
    x = sweep_lp(x, cutoff[0], cutoff[1], tau)
    return x * env_exp(len(x), 0.002, decay)


def pop(f0=900, f1=250, dur=0.12, tau=0.025, click=0.25):
    """«Поп»: синус с быстрым падением высоты и щелчок."""
    n = secs(dur)
    t = np.arange(n) / SR
    f = f1 + (f0 - f1) * np.exp(-t / tau)
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) * env_exp(n, 0.0008, dur * 0.35)
    k = secs(0.004)
    x[:k] += lp(np.random.default_rng(1).standard_normal(k), 4500) * np.linspace(1, 0, k) * click * 1.6
    return x


def bubble(f0=500, f1=1400, dur=0.14, tau=0.05):
    """Пузырь/капля: высота растёт (резонанс схлопывающегося пузырька)."""
    n = secs(dur)
    t = np.arange(n) / SR
    f = f0 * (f1 / f0) ** (1 - np.exp(-t / tau))
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * env_exp(n, 0.001, dur * 0.4)


def chirp(f0, f1, dur, decay=None):
    n = secs(dur)
    t = np.arange(n) / SR
    f = f0 * (f1 / f0) ** (t / dur)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * env_exp(n, 0.004, decay or dur)


def noise(dur, seed=0):
    return np.random.default_rng(seed).standard_normal(secs(dur))


def pad(freqs, dur, attack=0.25, release=0.6, voices=3, detune=8, cutoff=2200, seed=0):
    n = secs(dur)
    out = np.zeros(n)
    for i, f in enumerate(freqs):
        out += saw(f, dur, detune, voices, seed + i)
    out = lp(out, cutoff, 2)
    hold = max(0, dur - attack - release)
    e = env_adsr(n, attack, 0.05, 0.9, release / 3, hold)
    return out * e / max(1, len(freqs))


# ---------- сборка ----------

class Mix:
    """Стереомикс: place(сигнал, время, панорама -1..1, громкость)."""

    def __init__(self, dur):
        self.L = np.zeros(secs(dur))
        self.R = np.zeros(secs(dur))

    def place(self, x, at=0.0, pan=0.0, gain=1.0):
        i = secs(at)
        end = min(len(self.L), i + len(x))
        if end <= i:
            return self
        seg = x[: end - i] * gain
        # конец ноты — мягко, без щелчка (8 мс)
        f = min(len(seg), secs(0.008))
        if f > 1:
            seg = seg.copy()
            seg[-f:] *= np.cos(np.linspace(0, np.pi / 2, f)) ** 2
        a = (pan + 1) * np.pi / 4
        self.L[i:end] += seg * np.cos(a)
        self.R[i:end] += seg * np.sin(a)
        return self

    def stereo(self):
        return np.stack([self.L, self.R])


def reverb_ir(length=1.4, decay=0.45, bright=0.5, predelay=0.012, seed=7, width=1.0):
    n = secs(length)
    rng = np.random.default_rng(seed)
    t = np.arange(n) / SR
    irs = []
    for ch in range(2):
        nz = rng.standard_normal(n)
        hi = lp(nz, 2000 + 9000 * bright, 1) * np.exp(-t / (decay * 0.45))
        lo = lp(nz, 1500, 1) * np.exp(-t / decay)
        ir = 0.6 * hi + 0.8 * lo
        # ранние отражения
        for k, (dt, g) in enumerate([(0.007, 0.5), (0.013, 0.35), (0.021, 0.3), (0.029, 0.22)]):
            j = secs(dt + 0.002 * ch * k)
            if j < n:
                ir[j] += g
        ir = np.concatenate([np.zeros(secs(predelay)), ir])
        irs.append(ir)
    m = (irs[0] + irs[1]) / 2
    irs = [m + (irs[c] - m) * width for c in range(2)]
    norm = np.sqrt(np.sum(irs[0] ** 2))
    return [ir / norm for ir in irs]


def reverb(st, wet=0.25, **kw):
    ir = reverb_ir(**kw)
    n = st.shape[1]
    out = np.zeros_like(st)
    for c in range(2):
        mono = (st[0] + st[1]) / 2
        w = fftconvolve(mono, ir[c])[:n]
        out[c] = st[c] * (1 - wet * 0.5) + w * wet
    return out


def master(st, dur=None, target_rms_db=-16.0, peak_db=-1.0, hp_hz=120, tail_fade=0.06):
    """Срез низа (телефонные динамики), выравнивание громкости, мягкий лимит, затухание хвоста."""
    st = np.stack([hp(st[0], hp_hz), hp(st[1], hp_hz)])
    if dur is not None:
        n = secs(dur)
        st = st[:, :n] if st.shape[1] >= n else np.pad(st, ((0, 0), (0, n - st.shape[1])))
    # громкость по самой плотной части (первые 400 мс), но не больше 4 дБ «поджатия» пиков
    ceiling = 10 ** (peak_db / 20)
    win = st[:, : secs(0.4)]
    rms = np.sqrt(np.mean(win ** 2)) + 1e-9
    peak = np.max(np.abs(st)) + 1e-9
    g_loud = 10 ** (target_rms_db / 20) / rms
    g_peak = ceiling / peak
    st = st * min(g_loud, g_peak * 10 ** (4 / 20))
    # мягкий лимитер: выше порога — tanh, без жёсткого среза
    thr = ceiling * 0.7
    over = np.abs(st) > thr
    st = np.where(over, np.sign(st) * (thr + (ceiling - thr) * np.tanh((np.abs(st) - thr) / (ceiling - thr))), st)
    f = secs(tail_fade)
    st[:, -f:] *= np.linspace(1, 0, f) ** 2
    st[:, :secs(0.001)] *= np.linspace(0, 1, secs(0.001))
    return st
