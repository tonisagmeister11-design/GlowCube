#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Life Simulator - Audio-Generator.

Erzeugt saemtliche Klaenge des Spiels synthetisch (nur numpy, keine Samples
Dritter) als 16-Bit-WAV unter assets/audio/{sfx,ambience,music}/. Alle Klaenge
sind eigene Werke dieses Projekts und damit rechtlich unproblematisch.

Aufruf (aus dem Ordner LifeSimulator/):
  python3 tools/gen_audio.py                    # alles erzeugen + Analyse
  python3 tools/gen_audio.py --only ui_click    # nur bestimmte Klaenge (Komma-Liste, Praefix erlaubt)
  python3 tools/gen_audio.py --analyze          # nur Analyse der vorhandenen Dateien

Grundsaetze:
* Deterministisch: jeder Klang hat einen eigenen Zufallsgenerator (Seed aus dem Namen).
* Schleifen (Ambiente, Musik, Motoren, Sirene) werden zirkulaer erzeugt: Rauschen
  wird im Frequenzbereich geformt (damit periodisch), Ereignisse und Nachhall
  werden modulo Schleifenlaenge addiert. Die Naht ist dadurch stetig. Die
  Schleifenpunkte stehen im "smpl"-Chunk der WAV-Datei, den Godot beim Import
  erkennt (edit/loop_mode = "Detect From WAV").
* Pegel: Einzelklaenge Spitze <= -1 dBFS, Musik ca. -16 LUFS, Ambiente leiser.
"""
import argparse
import os
import shutil
import struct
import subprocess
import sys
import wave
import zlib

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "assets", "audio"))
SR_SFX = 32000
SR_AMB = 22050
SR_MUS = 22050
TAU = 2.0 * np.pi


# ======================================================================== Grundlagen

def rng_for(name):
    return np.random.default_rng(zlib.crc32(name.encode("utf-8")))


def db2a(d):
    return 10.0 ** (d / 20.0)


def a2db(a):
    return 20.0 * np.log10(max(float(a), 1e-12))


def mtof(m):
    return 440.0 * 2.0 ** ((m - 69) / 12.0)


def secs(n, sr):
    return np.arange(n) / float(sr)


def ns(sec, sr):
    return int(round(sec * sr))


def write_wav(path, x, sr, loop=False):
    """16-Bit-PCM mit TPDF-Dither; bei loop=True mit smpl-Chunk (Vorwaertsschleife ueber die ganze Datei)."""
    x = np.asarray(x, dtype=np.float64)
    if x.ndim == 1:
        x = x[:, None]
    n, ch = x.shape
    r = rng_for("dither:" + os.path.basename(path))
    dither = (r.random((n, ch)) - r.random((n, ch))) / 32768.0
    y = np.clip(x + dither, -1.0, 32767.0 / 32768.0)
    pcm = np.round(y * 32767.0).astype("<i2")
    data = pcm.tobytes()
    fmt = struct.pack("<HHIIHH", 1, ch, sr, sr * ch * 2, ch * 2, 16)
    chunks = b"fmt " + struct.pack("<I", len(fmt)) + fmt
    if loop:
        smpl = struct.pack("<9I", 0, 0, int(1e9 / sr), 60, 0, 0, 0, 1, 0)
        smpl += struct.pack("<6I", 0, 0, 0, n, 0, 0)
        chunks += b"smpl" + struct.pack("<I", len(smpl)) + smpl
    chunks += b"data" + struct.pack("<I", len(data)) + data
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"RIFF" + struct.pack("<I", 4 + len(chunks)) + b"WAVE" + chunks)


def read_wav(path):
    with wave.open(path, "rb") as w:
        sr = w.getframerate()
        ch = w.getnchannels()
        raw = w.readframes(w.getnframes())
    x = np.frombuffer(raw, dtype="<i2").astype(np.float64) / 32768.0
    x = x.reshape(-1, ch)
    loop = False
    with open(path, "rb") as f:
        blob = f.read()
    pos = 12
    while pos + 8 <= len(blob):
        cid = blob[pos:pos + 4]
        size = struct.unpack("<I", blob[pos + 4:pos + 8])[0]
        if cid == b"smpl":
            loop = True
        pos += 8 + size + (size & 1)
    return x, sr, loop


# ======================================================================== Filter

def _lp_curve(f, fc, order):
    return 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))


def _hp_curve(f, fc, order):
    r = (f / fc) ** (2 * order)
    return np.sqrt(r / (1.0 + r))


def shape(x, sr, fn, circular=False):
    """Nullphasige Filterung im Frequenzbereich. circular=True: periodisch (fuer Schleifen)."""
    x = np.asarray(x, dtype=np.float64)
    n = len(x)
    pad = 0 if circular else min(n, int(0.25 * sr))
    m = n + pad
    X = np.fft.rfft(x, m, axis=0)
    f = np.fft.rfftfreq(m, 1.0 / sr)
    H = fn(f)
    if x.ndim == 2:
        H = H[:, None]
    return np.fft.irfft(X * H, m, axis=0)[:n]


def lp(x, sr, fc, order=2, circ=False):
    return shape(x, sr, lambda f: _lp_curve(f, fc, order), circ)


def hp(x, sr, fc, order=2, circ=False):
    return shape(x, sr, lambda f: _hp_curve(f, fc, order), circ)


def bp(x, sr, lo, hi, order=2, circ=False):
    return shape(x, sr, lambda f: _hp_curve(f, lo, order) * _lp_curve(f, hi, order), circ)


def biquad_coeffs(kind, f0, sr, q=0.707, gain_db=0.0):
    """RBJ-Kochbuch-Biquads (kausal)."""
    w = TAU * f0 / sr
    cw, sw = np.cos(w), np.sin(w)
    alpha = sw / (2.0 * q)
    A = 10.0 ** (gain_db / 40.0)
    if kind == "lp":
        b = [(1 - cw) / 2, 1 - cw, (1 - cw) / 2]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "hp":
        b = [(1 + cw) / 2, -(1 + cw), (1 + cw) / 2]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "bp":
        b = [alpha, 0.0, -alpha]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "peak":
        b = [1 + alpha * A, -2 * cw, 1 - alpha * A]
        a = [1 + alpha / A, -2 * cw, 1 - alpha / A]
    else:
        raise ValueError(kind)
    b = np.array(b) / a[0]
    a = np.array(a) / a[0]
    return b, a


def biquad_ir(b, a, n):
    y = np.zeros(n)
    x1 = x2 = y1 = y2 = 0.0
    for i in range(n):
        x0 = 1.0 if i == 0 else 0.0
        v = b[0] * x0 + b[1] * x1 + b[2] * x2 - a[1] * y1 - a[2] * y2
        y[i] = v
        x2, x1 = x1, x0
        y2, y1 = y1, v
    return y


def fftconv(x, h, circular=False):
    """Faltung (linear, auf Eingangslaenge gekuerzt) oder zirkulaer. x/h mono oder stereo."""
    x = np.asarray(x, dtype=np.float64)
    h = np.asarray(h, dtype=np.float64)
    n = len(x)
    if circular:
        hf = np.zeros((n,) + h.shape[1:])
        for i in range(0, len(h), n):
            seg = h[i:i + n]
            hf[:len(seg)] += seg
        X = np.fft.rfft(x, n, axis=0)
        Hh = np.fft.rfft(hf, n, axis=0)
        if X.ndim == 1 and Hh.ndim == 2:
            X = X[:, None]
        elif X.ndim == 2 and Hh.ndim == 1:
            Hh = Hh[:, None]
        return np.fft.irfft(X * Hh, n, axis=0)
    m = n + len(h) - 1
    nfft = 1 << (m - 1).bit_length()
    X = np.fft.rfft(x, nfft, axis=0)
    Hh = np.fft.rfft(h, nfft, axis=0)
    if X.ndim == 1 and Hh.ndim == 2:
        X = X[:, None]
    elif X.ndim == 2 and Hh.ndim == 1:
        Hh = Hh[:, None]
    return np.fft.irfft(X * Hh, nfft, axis=0)[:n]


def filt(x, sr, kind, f0, q=0.707, gain_db=0.0, circ=False, ir_sec=0.3):
    b, a = biquad_coeffs(kind, f0, sr, q, gain_db)
    h = biquad_ir(b, a, max(64, ns(ir_sec, sr)))
    return fftconv(x, h, circ)


# ======================================================================== Rauschen, Huellkurven

def white(n, rng):
    return rng.standard_normal(n)


def colored(n, sr, rng, slope=-0.5, lo=20.0, circ=True):
    """Farbiges Rauschen: Amplitude ~ f^slope (pink = -0.5, braun = -1)."""
    x = rng.standard_normal(n)

    def fn(f):
        g = np.where(f > lo, (np.maximum(f, lo) / 1000.0) ** slope, (lo / 1000.0) ** slope * (f / lo) ** 2)
        return g
    y = shape(x, sr, fn, circ)
    return y / (np.std(y) + 1e-12)


def smooth_random(n, sr, rate_hz, rng, lo=0.0, hi=1.0):
    """Periodische, glatte Zufallskurve (Huellkurven fuer Wind, Verkehr)."""
    x = rng.standard_normal(n)
    y = shape(x, sr, lambda f: np.exp(-(f / rate_hz) ** 2), True)
    y = (y - y.min()) / (y.max() - y.min() + 1e-12)
    return lo + (hi - lo) * y


def raised(nn):
    return 0.5 - 0.5 * np.cos(np.pi * np.arange(nn) / max(nn, 1))


def env_exp(n, sr, tau, attack=0.002):
    e = np.exp(-secs(n, sr) / tau)
    a = min(n, ns(attack, sr))
    if a > 0:
        e[:a] *= raised(a)
    return e


def env_ar(n, sr, attack, release, hold=None):
    """Anstieg - Halten - Abfall (raised cosine)."""
    e = np.ones(n)
    a = min(n, ns(attack, sr))
    r = min(n - a, ns(release, sr))
    if a > 0:
        e[:a] = raised(a)
    if r > 0:
        e[n - r:] = raised(r)[::-1]
    return e


def fade(x, sr, fin=0.001, fout=0.005):
    x = np.array(x, dtype=np.float64)
    a = min(len(x), ns(fin, sr))
    b = min(len(x), ns(fout, sr))
    if a > 0:
        x[:a] *= raised(a) if x.ndim == 1 else raised(a)[:, None]
    if b > 0:
        x[len(x) - b:] *= raised(b)[::-1] if x.ndim == 1 else raised(b)[::-1, None]
    return x


def place(buf, sig, start, circ=True):
    """Addiert sig ab Index start (zirkulaer oder abgeschnitten)."""
    n = len(buf)
    sig = np.asarray(sig)
    if len(sig) == 0:
        return
    if circ:
        if len(sig) > n:
            folded = np.zeros((n,) + sig.shape[1:])
            for i in range(0, len(sig), n):
                seg = sig[i:i + n]
                folded[:len(seg)] += seg
            sig = folded
        idx = (int(start) + np.arange(len(sig))) % n
        buf[idx] += sig
    else:
        s = int(start)
        if s >= n:
            return
        e = min(n, s + len(sig))
        buf[s:e] += sig[:e - s]


def pan2(sig, pan):
    """Konstante Leistung, pan -1 (links) .. 1 (rechts)."""
    ang = (pan + 1.0) * np.pi / 4.0
    return np.stack([sig * np.cos(ang), sig * np.sin(ang)], axis=1)


# ======================================================================== Nachhall

def make_ir(sr, t60=1.6, predelay=0.02, stereo=True, damp=0.5, seed="ir", early=6, size=1.0):
    """Synthetische Raumantwort: frequenzabhaengig abklingendes Rauschen + fruehe Reflexionen."""
    r = rng_for(seed)
    n = ns(t60 * 1.15 + predelay, sr)
    t = secs(n, sr)
    chans = []
    for c in range(2 if stereo else 1):
        noise = r.standard_normal(n)
        low = lp(noise, sr, 500, 2)
        high = hp(noise, sr, 3000, 2)
        mid = noise - low - high
        out = np.zeros(n)
        for band, tt in ((low, t60 * 1.1), (mid, t60), (high, t60 * (1.0 - 0.75 * damp))):
            out += band * 10.0 ** (-3.0 * t / max(tt, 0.05))
        pd = ns(predelay, sr)
        out[:pd] = 0.0
        ramp = ns(0.012 * size, sr)
        out[pd:pd + ramp] *= raised(ramp)
        for k in range(early):
            tap = pd + ns(r.uniform(0.003, 0.05 * size), sr)
            if tap < n:
                out[tap] += r.choice([-1.0, 1.0]) * r.uniform(0.6, 1.4) * 6.0 / (k + 2)
        out /= np.sqrt(np.sum(out ** 2)) + 1e-12
        chans.append(out)
    return np.stack(chans, axis=1) if stereo else chans[0]


def reverb(x, sr, ir, wet=0.3, dry=1.0, circ=False):
    y = fftconv(x, ir, circ)
    if np.ndim(x) == 1 and np.ndim(y) == 2:
        x = np.stack([x, x], axis=1)
    return dry * x + wet * y


# ======================================================================== Pegel

def k_curve(f):
    hpc = _hp_curve(f, 38.0, 2)
    shelf = np.sqrt(1.0 + 1.512 * f ** 2 / (f ** 2 + 1500.0 ** 2))
    return hpc * shelf


def lufs(x, sr):
    """Naeherung von BS.1770 (K-Bewertung im Frequenzbereich, Gating)."""
    x = np.asarray(x, dtype=np.float64)
    if x.ndim == 1:
        x = x[:, None]
    xk = shape(x, sr, k_curve, True)
    blk = ns(0.4, sr)
    hop = blk // 4
    ms = []
    for s in range(0, max(1, len(xk) - blk + 1), hop):
        seg = xk[s:s + blk]
        ms.append(np.sum(np.mean(seg ** 2, axis=0)))
    ms = np.array(ms)
    ld = -0.691 + 10 * np.log10(ms + 1e-20)
    g = ms[ld > -70]
    if len(g) == 0:
        return -99.0
    rel = -0.691 + 10 * np.log10(np.mean(g)) - 10
    g2 = ms[(ld > -70) & (ld > rel)]
    return float(-0.691 + 10 * np.log10(np.mean(g2) + 1e-20))


def limiter(x, sr, ceiling_db=-1.0, circ=True):
    """Weicher Spitzenbegrenzer (Blockmaxima, Halten, Glaetten)."""
    c = db2a(ceiling_db)
    x = np.asarray(x, dtype=np.float64)
    a = np.abs(x) if x.ndim == 1 else np.max(np.abs(x), axis=1)
    blk = max(8, ns(0.004, sr))
    n = len(a)
    nb = (n + blk - 1) // blk
    ap = np.zeros(nb * blk)
    ap[:n] = a
    bm = ap.reshape(nb, blk).max(axis=1)
    g = np.minimum(1.0, c / np.maximum(bm, 1e-9))
    k = 6
    gm = g.copy()
    for s in range(1, k + 1):
        gm = np.minimum(gm, np.roll(g, s) if circ else np.concatenate([np.ones(s), g[:-s]]))
        gm = np.minimum(gm, np.roll(g, -s) if circ else np.concatenate([g[s:], np.ones(s)]))
    gs = np.zeros_like(gm)
    for s in range(-k // 2, k // 2 + 1):
        gs += np.roll(gm, s)
    gs /= (k + 1)
    gs = np.minimum(gs, 1.0)
    centers = np.arange(nb) * blk + blk / 2
    gain = np.interp(np.arange(n), centers, gs)
    y = x * (gain if x.ndim == 1 else gain[:, None])
    return np.clip(y, -c, c)


def remove_dc(x, sr, circ):
    y = hp(x, sr, 18.0, 2, circ)
    return y - np.mean(y, axis=0)


def finalize_oneshot(x, sr, peak_db=-1.0, tail_db=-60.0):
    x = remove_dc(np.asarray(x, dtype=np.float64), sr, False)
    a = np.abs(x) if x.ndim == 1 else np.max(np.abs(x), axis=1)
    thr = np.max(a) * db2a(tail_db)
    idx = np.nonzero(a > thr)[0]
    end = min(len(x), (idx[-1] if len(idx) else len(x) - 1) + ns(0.02, sr))
    x = fade(x[:end], sr, 0.0005, min(0.03, end / sr * 0.2))
    p = np.max(np.abs(x))
    return x * db2a(peak_db) / (p + 1e-12)


def finalize_loop(x, sr, target_lufs=None, peak_db=-1.0, ceiling_db=-1.0):
    x = remove_dc(np.asarray(x, dtype=np.float64), sr, True)
    if target_lufs is None:
        p = np.max(np.abs(x))
        return x * db2a(peak_db) / (p + 1e-12)
    for _ in range(3):
        x = x * db2a(target_lufs - lufs(x, sr))
        if np.max(np.abs(x)) > db2a(ceiling_db):
            x = limiter(x, sr, ceiling_db, True)
    return x


# ======================================================================== Instrumente

def modes(sr, dur, freqs, amps, taus, rng=None, attack=0.0005, jitter=0.0):
    """Modalsynthese: Summe gedaempfter Sinusschwingungen."""
    n = ns(dur, sr)
    t = secs(n, sr)
    y = np.zeros(n)
    for f, a, tau in zip(freqs, amps, taus):
        if rng is not None and jitter > 0:
            f *= 1.0 + rng.uniform(-jitter, jitter)
        if f >= 0.45 * sr:
            continue
        ph = rng.uniform(0, TAU) if rng is not None else 0.0
        y += a * np.exp(-t / tau) * np.sin(TAU * f * t + ph)
    a = ns(attack, sr)
    if a > 0:
        y[:a] *= raised(a)
    return y


def piano(f0, dur, sr, vel=0.7, release=0.35):
    """Additives Klavier: leicht inharmonische Teiltoene, zweistufiges Abklingen."""
    n = ns(dur + release, sr)
    t = secs(n, sr)
    y = np.zeros(n)
    B = 0.0004
    bright = 0.35 + 0.5 * vel
    tau_base = float(np.clip(2.4 * (261.6 / f0) ** 0.5, 0.7, 5.0))
    for k in range(1, 22):
        fk = k * f0 * np.sqrt(1.0 + B * k * k)
        if fk > 0.42 * sr or fk > 7000:
            break
        amp = (1.0 / k ** 1.2) * np.exp(-(k - 1) * (1.0 - bright) * 0.9)
        tau = tau_base / (1.0 + 0.45 * (k - 1))
        env = 0.55 * np.exp(-t / (tau * 0.22)) + 0.45 * np.exp(-t / tau)
        y += amp * env * np.sin(TAU * fk * t)
    a = ns(0.003, sr)
    y[:a] *= raised(a)
    nd = ns(dur, sr)
    if nd < n:
        y[nd:] *= np.exp(-(t[nd:] - dur) / (release / 4.0))
    return y * vel


def epiano(f0, dur, sr, vel=0.6, release=0.4):
    """FM-E-Piano (zwei Operatoren, Modulationsindex klingt ab)."""
    n = ns(dur + release, sr)
    t = secs(n, sr)
    tau = float(np.clip(3.0 * (220.0 / f0) ** 0.4, 1.0, 4.0))
    env = np.exp(-t / tau)
    a = ns(0.004, sr)
    env[:a] *= raised(a)
    idx = (0.9 * vel + 0.3) * np.exp(-t / 0.35) + 0.25
    mod = np.sin(TAU * f0 * t)
    y = np.sin(TAU * f0 * t + idx * mod)
    y += 0.12 * vel * np.sin(TAU * 2 * f0 * t + 0.5 * idx * mod) * np.exp(-t / (tau * 0.4))
    if 7 * f0 < 0.4 * sr:
        y += 0.035 * vel * np.sin(TAU * 7 * f0 * t) * np.exp(-t / 0.03)
    y *= env
    nd = ns(dur, sr)
    if nd < n:
        y[nd:] *= np.exp(-(t[nd:] - dur) / 0.09)
    return y * vel


def pad(f0, dur, sr, rng, cutoff=1300.0, attack=1.2, release=1.8, detune=(-3.0, 0.0, 3.0)):
    """Warme Flaeche: bandbegrenzte Saegezahn-Teiltoene, sanfte Huellkurve."""
    n = ns(dur + release, sr)
    t = secs(n, sr)
    y = np.zeros(n)
    for c in detune:
        f = f0 * 2.0 ** (c / 1200.0)
        k = 1
        while True:
            fk = k * f
            if fk > min(0.42 * sr, cutoff * 4.0):
                break
            y += (1.0 / k) * np.exp(-fk / cutoff) * np.sin(TAU * fk * t + rng.uniform(0, TAU))
            k += 1
    y /= len(detune)
    env = np.ones(n)
    a = min(n, ns(attack, sr))
    env[:a] = raised(a)
    nd = ns(dur, sr)
    r = n - nd
    if r > 0:
        env[nd:] *= raised(r)[::-1]
    y *= env * (1.0 + 0.05 * np.sin(TAU * 0.21 * t + rng.uniform(0, TAU)))
    return y


def ks_pluck(f0, dur, sr, rng, bright=0.5, t60=2.2):
    """Karplus-Strong-Saite; intern mit ganzzahliger Periode, dann auf sr umgerechnet."""
    L = int(np.ceil(sr / f0))
    sr_i = L * f0
    n_i = int((dur + 0.05) * sr_i) + 2 * L
    y = np.zeros(n_i)
    exc = rng.standard_normal(L)
    exc = lp(exc, sr_i, 800 + 5000 * bright, 1)
    tri = 1.0 - np.abs(np.linspace(-1, 1, L))
    exc = 0.6 * exc / (np.std(exc) + 1e-9) * 0.5 + 0.4 * (tri - tri.mean()) * 2.0
    pos = max(1, int(L * 0.18))
    exc = exc - np.roll(exc, pos) * 0.6
    y[:L] = exc
    g = 10.0 ** (-3.0 / (t60 * f0))
    for i in range(L, n_i, L):
        e = min(n_i, i + L)
        prev = y[i - L:e - L]
        prev2 = y[i - L - 1:e - L - 1] if i - L - 1 >= 0 else np.concatenate([[0.0], y[i - L:e - L - 1]])
        y[i:e] = g * (0.5 + 0.25 * bright) * prev + g * (0.5 - 0.25 * bright) * prev2
    n_out = ns(dur, sr)
    t_out = np.arange(n_out) / sr * sr_i
    out = np.interp(t_out, np.arange(n_i), y)
    out = fade(out, sr, 0.0005, 0.08)
    return out / (np.max(np.abs(out)) + 1e-9)


def bass(f0, dur, sr, vel=0.7):
    n = ns(dur + 0.25, sr)
    t = secs(n, sr)
    y = np.sin(TAU * f0 * t) + 0.3 * np.sin(2 * TAU * f0 * t) * np.exp(-t / 0.3)
    y += 0.12 * np.sin(3 * TAU * f0 * t) * np.exp(-t / 0.12)
    env = 0.6 + 0.4 * np.exp(-t / 0.4)
    a = ns(0.008, sr)
    env[:a] *= raised(a)
    nd = ns(dur, sr)
    env[nd:] *= np.exp(-(t[nd:] - dur) / 0.06)
    return y * env * vel


def mallet(f0, sr, vel=0.7, dur=1.4):
    """Marimba-artig: abgestimmte Teiltoene 1 : 4 : 10."""
    tau = float(np.clip(0.7 * (440.0 / f0) ** 0.3, 0.25, 1.2))
    y = modes(sr, dur, [f0, 4.0 * f0, 9.9 * f0], [1.0, 0.22 * vel, 0.05 * vel], [tau, tau * 0.18, 0.04],
              attack=0.0012)
    return y * vel


def bell(f0, sr, dur=1.2, vel=0.8, bright=1.0):
    """Weiche Glocke (Klangschale/Glockenspiel), Teiltoene nahe harmonisch."""
    ratios = [1.0, 2.0, 3.0, 4.16, 5.43]
    amps = [1.0, 0.45 * bright, 0.18 * bright, 0.08 * bright, 0.04 * bright]
    taus = [dur * 0.45, dur * 0.25, dur * 0.12, dur * 0.07, dur * 0.05]
    return modes(sr, dur, [f0 * r for r in ratios], amps, taus, attack=0.0015) * vel


def kick(sr, vel=0.8):
    n = ns(0.45, sr)
    t = secs(n, sr)
    f = 48.0 + 70.0 * np.exp(-t / 0.035)
    ph = np.cumsum(TAU * f / sr)
    y = np.sin(ph) * np.exp(-t / 0.22)
    click = lp(np.random.default_rng(7).standard_normal(n), sr, 1500, 2) * np.exp(-t / 0.004) * 0.15
    return fade((y + click) * vel, sr, 0.001, 0.02)


def snare_soft(sr, rng, vel=0.6):
    n = ns(0.3, sr)
    t = secs(n, sr)
    noise = bp(rng.standard_normal(n), sr, 900, 5500, 2) * np.exp(-t / 0.09)
    tone = np.sin(TAU * 185 * t) * np.exp(-t / 0.05) * 0.5
    y = noise * 0.7 + tone
    return fade(y * vel / (np.max(np.abs(y)) + 1e-9), sr, 0.001, 0.02)


def hat_soft(sr, rng, vel=0.4, decay=0.035):
    n = ns(0.12, sr)
    t = secs(n, sr)
    y = hp(rng.standard_normal(n), sr, 6000, 2) * np.exp(-t / decay)
    return fade(y * vel / (np.max(np.abs(y)) + 1e-9), sr, 0.0005, 0.01)


def shaker(sr, rng, vel=0.3):
    n = ns(0.14, sr)
    t = secs(n, sr)
    env = (t / 0.03) ** 1.5 * np.exp(-t / 0.035)
    y = bp(rng.standard_normal(n), sr, 3500, 9000, 2) * env
    return fade(y * vel / (np.max(np.abs(y)) + 1e-9), sr, 0.0005, 0.01)


# ======================================================================== UI

def ui_hover():
    sr = SR_SFX
    y = modes(sr, 0.04, [2600, 5200], [1.0, 0.15], [0.006, 0.003], attack=0.0008)
    return finalize_oneshot(y, sr, -20.0), sr


def ui_click():
    sr = SR_SFX
    r = rng_for("ui_click")
    y = modes(sr, 0.06, [620, 1850, 3400], [0.35, 1.0, 0.4], [0.012, 0.008, 0.004], attack=0.0003)
    n = len(y)
    y += hp(r.standard_normal(n), sr, 4000) * env_exp(n, sr, 0.0015, 0.0001) * 0.3
    return finalize_oneshot(y, sr, -10.0), sr


def _two_notes(m1, m2, gap, vel2=0.85, dur=0.6, rev=0.12, name="x"):
    sr = SR_SFX
    n = ns(gap + dur, sr)
    y = np.zeros(n)
    place(y, bell(mtof(m1), sr, dur, 1.0, 0.7), 0, False)
    place(y, bell(mtof(m2), sr, dur, vel2, 0.7), ns(gap, sr), False)
    ir = make_ir(sr, 0.5, 0.008, False, 0.6, "ui_ir")
    y = reverb(y, sr, ir, rev, 1.0)
    return y


def ui_confirm():
    return finalize_oneshot(_two_notes(84, 91, 0.075), SR_SFX, -9.0), SR_SFX


def ui_back():
    return finalize_oneshot(_two_notes(79, 72, 0.07, 0.8, 0.45), SR_SFX, -11.0), SR_SFX


def ui_error():
    sr = SR_SFX
    n = ns(0.45, sr)
    y = np.zeros(n)
    for i, m in enumerate([63, 58]):
        f = mtof(m)
        nn = ns(0.2, sr)
        t = secs(nn, sr)
        tone = np.zeros(nn)
        for k in (1, 3, 5, 7):
            tone += np.sin(TAU * f * k * t) / (k * k)
        tone *= env_exp(nn, sr, 0.07, 0.004)
        place(y, tone, ns(0.13 * i, sr), False)
    y = lp(y, sr, 2500, 2)
    return finalize_oneshot(y, sr, -9.0), sr


def ui_notification():
    sr = SR_SFX
    n = ns(1.1, sr)
    y = np.zeros(n)
    for i, (m, v) in enumerate([(81, 0.8), (85, 0.85), (88, 1.0)]):
        place(y, bell(mtof(m), sr, 0.9, v, 0.6), ns(0.085 * i, sr), False)
    y = reverb(y, sr, make_ir(sr, 0.7, 0.01, False, 0.6, "ui_ir2"), 0.15)
    return finalize_oneshot(y, sr, -8.0), sr


def _coin(sr, rng, base, vel=1.0, dur=0.5):
    ratios = [1.0, 2.76, 5.40, 8.93, 13.3]
    amps = [0.6, 1.0, 0.7, 0.4, 0.2]
    taus = [dur * 0.5, dur * 0.4, dur * 0.25, dur * 0.15, dur * 0.08]
    y = modes(sr, dur, [base * r for r in ratios], amps, taus, rng, 0.0003, 0.01)
    n = len(y)
    y += hp(rng.standard_normal(n), sr, 5000) * env_exp(n, sr, 0.002, 0.0001) * 0.4
    return y * vel


def money_gain():
    sr = SR_SFX
    r = rng_for("money_gain")
    n = ns(1.0, sr)
    y = np.zeros(n)
    place(y, _coin(sr, r, 1250, 0.8), 0, False)
    place(y, _coin(sr, r, 1480, 0.9), ns(0.07, sr), False)
    place(y, bell(mtof(88), sr, 0.7, 0.35), ns(0.12, sr), False)
    place(y, bell(mtof(95), sr, 0.7, 0.3), ns(0.19, sr), False)
    y = reverb(y, sr, make_ir(sr, 0.5, 0.008, False, 0.5, "ui_ir"), 0.12)
    return finalize_oneshot(y, sr, -7.0), sr


def money_spend():
    sr = SR_SFX
    r = rng_for("money_spend")
    n = ns(0.6, sr)
    y = np.zeros(n)
    t = secs(n, sr)
    sw = bp(r.standard_normal(n), sr, 600, 3000, 2)
    sweep_env = np.exp(-((t - 0.12) / 0.07) ** 2)
    y += sw * sweep_env * 0.25
    place(y, _coin(sr, r, 980, 0.8, 0.35), ns(0.05, sr), False)
    place(y, _coin(sr, r, 880, 0.5, 0.3), ns(0.16, sr), False)
    return finalize_oneshot(y, sr, -9.0), sr


def _blip_sweep(f_a, f_b, dur, sr, rng):
    n = ns(dur, sr)
    t = secs(n, sr)
    f = f_a * (f_b / f_a) ** (t / dur)
    ph = np.cumsum(TAU * f / sr)
    y = np.sin(ph) * np.sin(np.pi * t / dur) ** 1.5
    whoosh = bp(rng.standard_normal(n), sr, min(f_a, f_b) * 2, max(f_a, f_b) * 4, 1) * np.sin(np.pi * t / dur) ** 2
    return y + 0.15 * whoosh


def phone_open():
    sr = SR_SFX
    r = rng_for("phone_open")
    y = np.zeros(ns(0.35, sr))
    place(y, _blip_sweep(520, 1150, 0.13, sr, r), 0, False)
    place(y, bell(mtof(91), sr, 0.25, 0.35), ns(0.11, sr), False)
    return finalize_oneshot(y, sr, -10.0), sr


def phone_close():
    sr = SR_SFX
    r = rng_for("phone_close")
    y = np.zeros(ns(0.3, sr))
    place(y, _blip_sweep(1050, 480, 0.13, sr, r), 0, False)
    return finalize_oneshot(y, sr, -11.0), sr


def ui_levelup():
    sr = SR_SFX
    r = rng_for("levelup")
    n = ns(2.6, sr)
    y = np.zeros(n)
    notes = [72, 76, 79, 84]
    for i, m in enumerate(notes):
        place(y, piano(mtof(m), 0.6, sr, 0.75), ns(0.09 * i, sr), False)
        place(y, bell(mtof(m + 12), sr, 0.8, 0.25), ns(0.09 * i, sr), False)
    chord = np.zeros(n)
    for m in (60, 64, 67, 72):
        place(chord, pad(mtof(m), 1.0, sr, r, 1800, 0.25, 1.1), ns(0.33, sr), False)
    y += chord * 0.18
    place(y, bell(mtof(96), sr, 1.2, 0.3), ns(0.36, sr), False)
    y = reverb(y, sr, make_ir(sr, 1.3, 0.015, False, 0.5, "jingle_ir"), 0.25)
    return finalize_oneshot(y, sr, -6.0), sr


def interact():
    sr = SR_SFX
    r = rng_for("interact")
    y = _blip_sweep(700, 1000, 0.05, sr, r)
    y = np.concatenate([y, np.zeros(ns(0.05, sr))])
    return finalize_oneshot(y, sr, -12.0), sr


def pickup():
    sr = SR_SFX
    r = rng_for("pickup")
    n = ns(0.35, sr)
    y = np.zeros(n)
    rust = _grains(sr, r, 0.18, 900, 1500, 6000) * 0.6
    place(y, rust, 0, False)
    place(y, bell(mtof(86), sr, 0.25, 0.4), ns(0.08, sr), False)
    return finalize_oneshot(y, sr, -9.0), sr


def scanner_beep():
    sr = SR_SFX
    n = ns(0.13, sr)
    t = secs(n, sr)
    y = (np.sin(TAU * 1880 * t) + 0.15 * np.sin(TAU * 3760 * t)) * env_ar(n, sr, 0.004, 0.02)
    return finalize_oneshot(y, sr, -12.0), sr


def shutter():
    sr = SR_SFX
    r = rng_for("shutter")
    n = ns(0.2, sr)
    y = np.zeros(n)
    for i, (f, d) in enumerate([(2400, 0.0), (1700, 0.065)]):
        c = modes(sr, 0.05, [f, f * 1.7, f * 2.9], [1.0, 0.6, 0.3], [0.006, 0.004, 0.003], r, 0.0002, 0.03)
        c += hp(r.standard_normal(len(c)), sr, 3000) * env_exp(len(c), sr, 0.003) * 0.5
        place(y, c * (1.0 if i == 0 else 0.8), ns(d, sr), False)
    return finalize_oneshot(y, sr, -9.0), sr


# ======================================================================== Koerpergeraeusche, Gegenstaende

def _grains(sr, rng, dur, rate, lo, hi, curve=None):
    """Koernige Geraeusche (Rascheln, Knirschen): dichte Mikroimpulse."""
    n = ns(dur, sr)
    y = np.zeros(n)
    count = int(rate * dur)
    for _ in range(count):
        s = int(rng.uniform(0, n))
        g_len = ns(rng.uniform(0.0008, 0.004), sr)
        g = rng.standard_normal(g_len) * np.exp(-np.arange(g_len) / (g_len / 3.0 + 1))
        amp = rng.exponential(0.5)
        if curve is not None:
            amp *= curve[min(s, n - 1)]
        place(y, g * amp, s, False)
    return bp(y, sr, lo, hi, 2)


def eat():
    sr = SR_SFX
    r = rng_for("eat")
    n = ns(1.0, sr)
    y = np.zeros(n)
    for i in range(2):
        nn = ns(0.16, sr)
        tt = secs(nn, sr)
        crv = np.exp(-tt / 0.05) + 0.3
        c = _grains(sr, r, 0.16, 1400, 800, 6000, crv)
        c += lp(r.standard_normal(nn), sr, 300) * np.exp(-tt / 0.03) * 0.3
        place(y, c, ns(0.05 + 0.45 * i + r.uniform(0, 0.03), sr), False)
    return finalize_oneshot(y, sr, -8.0), sr


def drink():
    sr = SR_SFX
    r = rng_for("drink")
    n = ns(0.9, sr)
    y = np.zeros(n)
    for i in range(2):
        nn = ns(0.22, sr)
        tt = secs(nn, sr)
        f = 330 * np.exp(-tt / 0.15) + 170
        g = np.sin(np.cumsum(TAU * f / sr)) * np.sin(np.pi * tt / 0.22) ** 2
        g += lp(r.standard_normal(nn), sr, 600) * np.sin(np.pi * tt / 0.22) ** 2 * 0.25
        place(y, g, ns(0.04 + 0.38 * i, sr), False)
    y = lp(y, sr, 1800, 2)
    return finalize_oneshot(y, sr, -9.0), sr


def keys_jingle():
    sr = SR_SFX
    r = rng_for("keys")
    n = ns(0.6, sr)
    y = np.zeros(n)
    for i in range(9):
        base = r.uniform(2800, 5200)
        c = modes(sr, 0.18, [base, base * 1.53, base * 2.31], [1.0, 0.6, 0.35], [0.05, 0.03, 0.02], r, 0.0002)
        place(y, c * r.uniform(0.3, 1.0), ns(r.uniform(0.0, 0.32), sr), False)
    return finalize_oneshot(y, sr, -8.0), sr


def bag_rustle():
    sr = SR_SFX
    r = rng_for("bag")
    dur = 0.55
    n = ns(dur, sr)
    crv = smooth_random(n, sr, 12, r, 0.1, 1.0) * np.sin(np.pi * secs(n, sr) / dur)
    y = _grains(sr, r, dur, 2200, 700, 7000, crv)
    return finalize_oneshot(y, sr, -9.0), sr


# ======================================================================== Schritte

def footstep_concrete(i):
    sr = SR_SFX
    r = rng_for("fs_concrete%d" % i)
    n = ns(0.3, sr)
    y = np.zeros(n)
    heel_len = ns(0.05, sr)
    th = secs(heel_len, sr)
    heel = filt(r.standard_normal(heel_len), sr, "bp", r.uniform(900, 1600), 1.1) * np.exp(-th / r.uniform(0.005, 0.009))
    heel += np.sin(TAU * r.uniform(85, 120) * th) * np.exp(-th / 0.02) * 0.5
    heel += hp(r.standard_normal(heel_len), sr, 3000) * np.exp(-th / 0.002) * 0.4
    place(y, heel, 0, False)
    toe_len = ns(0.08, sr)
    tt = secs(toe_len, sr)
    toe_env = (tt / 0.008).clip(0, 1) * np.exp(-tt / r.uniform(0.015, 0.025))
    toe = bp(r.standard_normal(toe_len), sr, 1800, 6500, 2) * toe_env
    place(y, toe * r.uniform(0.25, 0.4), ns(r.uniform(0.05, 0.08), sr), False)
    y += _grains(sr, r, 0.3, 120, 2000, 8000, np.exp(-secs(n, sr) / 0.06)) * 0.15
    return finalize_oneshot(y, sr, r.uniform(-4.0, -3.0)), sr


def footstep_grass(i):
    sr = SR_SFX
    r = rng_for("fs_grass%d" % i)
    dur = 0.28
    n = ns(dur, sr)
    t = secs(n, sr)
    t2 = r.uniform(0.06, 0.09)
    crv = np.exp(-t / 0.04) + 0.6 * np.exp(-np.maximum(t - t2, 0) / 0.05) * (t > t2)
    y = _grains(sr, r, dur, 1600, 1200, 7000, crv)
    y += lp(r.standard_normal(n), sr, 180) * np.exp(-t / 0.03) * 0.25
    return finalize_oneshot(y, sr, r.uniform(-7.0, -6.0)), sr


def footstep_wood(i):
    sr = SR_SFX
    r = rng_for("fs_wood%d" % i)
    n = ns(0.35, sr)
    y = np.zeros(n)
    base = r.uniform(120, 160)
    knock = modes(sr, 0.25, [base, base * 2.1, base * 3.6, base * 6.1, base * 9.3],
                  [1.0, 0.7, 0.45, 0.25, 0.1], [0.06, 0.045, 0.03, 0.02, 0.012], r, 0.0008, 0.03)
    nl = ns(0.03, sr)
    tr = bp(r.standard_normal(nl), sr, 800, 4000, 2) * np.exp(-secs(nl, sr) / 0.004) * 0.6
    place(y, knock, 0, False)
    place(y, tr, 0, False)
    toe = modes(sr, 0.12, [base * 2.3, base * 4.4], [0.6, 0.3], [0.03, 0.015], r, 0.001, 0.05)
    place(y, toe * r.uniform(0.2, 0.35), ns(r.uniform(0.06, 0.09), sr), False)
    if i == 3:
        cl = ns(0.18, sr)
        ct = secs(cl, sr)
        rate = 45 + 30 * np.sin(np.pi * ct / 0.18)
        pulses = np.zeros(cl)
        pos = 0.0
        while pos < 0.18:
            pulses[ns(pos, sr)] = 1.0
            pos += 1.0 / rate[min(ns(pos, sr), cl - 1)]
        creak = filt(pulses, sr, "bp", 900, 4.0) + 0.5 * filt(pulses, sr, "bp", 1700, 5.0)
        place(y, creak * np.sin(np.pi * ct / 0.18) * 0.25, ns(0.05, sr), False)
    return finalize_oneshot(y, sr, r.uniform(-4.0, -3.0)), sr


# ======================================================================== Tueren, Laden

def _metal_click(sr, rng, base=2600, dur=0.04, amp=1.0):
    c = modes(sr, dur, [base, base * 1.58, base * 2.42, base * 3.3], [1.0, 0.7, 0.4, 0.2],
              [0.012, 0.008, 0.005, 0.003], rng, 0.0002, 0.04)
    c += hp(rng.standard_normal(len(c)), sr, 3500) * env_exp(len(c), sr, 0.0015, 0.0001) * 0.5
    return c * amp


def _creak(sr, rng, dur, f_lo, f_hi, res=(850, 1650)):
    n = ns(dur, sr)
    t = secs(n, sr)
    rate = f_lo + (f_hi - f_lo) * smooth_random(n, sr, 4.0, rng)
    pulses = np.zeros(n)
    pos = 0.0
    while pos < dur:
        i = ns(pos, sr)
        if i >= n:
            break
        pulses[i] = rng.uniform(0.6, 1.0)
        pos += 1.0 / rate[i]
    y = filt(pulses, sr, "bp", res[0], 3.0) + 0.6 * filt(pulses, sr, "bp", res[1], 4.0)
    return y * np.sin(np.pi * t / dur) ** 0.8


def door_open():
    sr = SR_SFX
    r = rng_for("door_open")
    n = ns(1.1, sr)
    y = np.zeros(n)
    place(y, _metal_click(sr, r, 2500, 0.05, 0.7), 0, False)
    place(y, _metal_click(sr, r, 1900, 0.05, 1.0), ns(0.09, sr), False)
    place(y, _creak(sr, r, 0.55, 40, 110) * 0.35, ns(0.16, sr), False)
    t = secs(n, sr)
    air = lp(r.standard_normal(n), sr, 500, 2) * np.exp(-((t - 0.5) / 0.22) ** 2) * 0.25
    y += air
    y = reverb(y, sr, make_ir(sr, 0.45, 0.005, False, 0.6, "room_ir"), 0.15)
    return finalize_oneshot(y, sr, -3.0), sr


def door_close():
    sr = SR_SFX
    r = rng_for("door_close")
    n = ns(0.9, sr)
    t = secs(n, sr)
    y = lp(r.standard_normal(n), sr, 450, 2) * np.exp(-((t - 0.13) / 0.08) ** 2) * 0.3
    thud = modes(sr, 0.5, [68, 112, 176, 410, 760], [1.0, 0.8, 0.5, 0.35, 0.15],
                 [0.09, 0.07, 0.05, 0.03, 0.015], r, 0.001)
    nl = ns(0.02, sr)
    thud[:nl] += bp(r.standard_normal(nl), sr, 300, 3000) * np.exp(-secs(nl, sr) / 0.004) * 0.8
    place(y, thud, ns(0.22, sr), False)
    place(y, _metal_click(sr, r, 2200, 0.05, 0.45), ns(0.235, sr), False)
    y = reverb(y, sr, make_ir(sr, 0.45, 0.005, False, 0.6, "room_ir"), 0.15)
    return finalize_oneshot(y, sr, -2.0), sr


def shop_bell():
    sr = SR_SFX
    r = rng_for("shop_bell")
    n = ns(2.0, sr)
    y = np.zeros(n)
    base = 1460.0
    ratios = [1.0, 2.32, 4.25, 6.63, 9.38]
    for i, (d, v) in enumerate([(0.0, 1.0), (0.12, 0.6), (0.31, 0.35), (0.52, 0.15)]):
        b = modes(sr, 1.6, [base * q for q in ratios], [1.0, 0.6, 0.35, 0.18, 0.08],
                  [0.7, 0.45, 0.25, 0.12, 0.06], r, 0.0003, 0.004)
        place(y, b * v, ns(d, sr), False)
    y = reverb(y, sr, make_ir(sr, 0.6, 0.006, False, 0.5, "shop_ir"), 0.15)
    return finalize_oneshot(y, sr, -5.0), sr


def cash_register():
    sr = SR_SFX
    r = rng_for("cash_register")
    n = ns(1.4, sr)
    t = secs(n, sr)
    y = np.zeros(n)
    sl = ns(0.28, sr)
    st = secs(sl, sr)
    slide = bp(r.standard_normal(sl), sr, 300, 2500, 2) * np.sin(np.pi * st / 0.28) ** 2
    rattle = _grains(sr, r, 0.28, 500, 1000, 5000, np.sin(np.pi * st / 0.28))
    place(y, slide * 0.5 + rattle * 0.4, ns(0.12, sr), False)
    thud = modes(sr, 0.2, [140, 260, 520], [1.0, 0.5, 0.3], [0.04, 0.03, 0.02], r, 0.0008)
    place(y, thud * 0.8, ns(0.38, sr), False)
    place(y, bell(2350, sr, 1.0, 0.9, 0.8), 0, False)
    for k in range(5):
        place(y, _coin(sr, r, r.uniform(1500, 2600), r.uniform(0.15, 0.35), 0.25), ns(0.42 + r.uniform(0, 0.25), sr), False)
    y = reverb(y, sr, make_ir(sr, 0.5, 0.006, False, 0.5, "shop_ir"), 0.12)
    return finalize_oneshot(y, sr, -3.0), sr


# ======================================================================== Fahrzeuge

def _harm_tone(f, sr, n, kmax_f=5000.0, slope=0.9, phase=None):
    t = secs(n, sr)
    if phase is None:
        phase = TAU * f * t
    y = np.zeros(n)
    k = 1
    fmax = float(np.max(f)) if np.ndim(f) else f
    while k * fmax < min(kmax_f, 0.42 * sr):
        y += np.sin(k * phase) / k ** slope
        k += 1
    return y


def horn(freqs, dur, name, peak=-2.0):
    sr = SR_SFX
    r = rng_for(name)
    n = ns(dur, sr)
    t = secs(n, sr)
    y = np.zeros(n)
    for f in freqs:
        fm = f * (1.0 + 0.002 * np.sin(TAU * 5.5 * t + r.uniform(0, TAU)))
        ph = np.cumsum(TAU * fm / sr)
        y += _harm_tone(fm, sr, n, 4500.0, 0.8, ph)
    y = filt(y, sr, "peak", 2400, 1.0, 5.0)
    y = lp(y, sr, 3800, 2)
    y *= env_ar(n, sr, 0.015, 0.07)
    y = reverb(y, sr, make_ir(sr, 0.8, 0.01, False, 0.6, "street_ir"), 0.12)
    return finalize_oneshot(y, sr, peak), sr


def horn_car():
    return horn([415.0, 523.0], 0.45, "horn_car")


def bus_horn():
    return horn([233.0, 294.0], 0.75, "bus_horn")


def bus_door():
    sr = SR_SFX
    r = rng_for("bus_door")
    n = ns(1.2, sr)
    t = secs(n, sr)
    hiss_env = np.where(t < 0.01, t / 0.01, np.exp(-(t - 0.01) / 0.28))
    y = bp(r.standard_normal(n), sr, 1800, 9000, 2) * hiss_env * 0.7
    y += bp(r.standard_normal(n), sr, 400, 1500, 2) * hiss_env * 0.15
    thud = modes(sr, 0.3, [95, 180, 330], [1.0, 0.6, 0.3], [0.06, 0.04, 0.02], r, 0.001)
    place(y, thud * 0.9, ns(0.62, sr), False)
    place(y, _metal_click(sr, r, 1800, 0.05, 0.4), ns(0.63, sr), False)
    return finalize_oneshot(y, sr, -3.0), sr


def brake_squeal():
    sr = SR_SFX
    r = rng_for("brake")
    dur = 1.0
    n = ns(dur, sr)
    t = secs(n, sr)
    f = 2350.0 * (1.0 + 0.003 * np.sin(TAU * 6.0 * t)) * (1.0 - 0.02 * t)
    ph = np.cumsum(TAU * f / sr)
    env = np.clip(t / 0.12, 0, 1) ** 2 * np.clip((dur - t) / 0.45, 0, 1) ** 1.5
    y = (np.sin(ph) + 0.12 * np.sin(2 * ph)) * env * 0.5
    y += lp(r.standard_normal(n), sr, 900, 2) * np.clip((dur - t) / dur, 0, 1) * 0.35
    return finalize_oneshot(y, sr, -10.0), sr


def car_door():
    sr = SR_SFX
    r = rng_for("car_door")
    n = ns(0.5, sr)
    y = modes(sr, 0.5, [82, 145, 230, 520, 1100], [1.0, 0.7, 0.4, 0.25, 0.1], [0.07, 0.05, 0.035, 0.02, 0.01], r, 0.001)
    nl = ns(0.02, sr)
    y[:nl] += bp(r.standard_normal(nl), sr, 400, 3500) * np.exp(-secs(nl, sr) / 0.004)
    place(y, _metal_click(sr, r, 2000, 0.04, 0.35), ns(0.012, sr), False)
    y = y[:n]
    return finalize_oneshot(y, sr, -2.0), sr


def _engine_loop(name, period, n_periods, res, cutoff, noise_amt, sr=SR_SFX):
    """Motor als Folge von Zuendimpulsen (Auspuffresonanzen), periodisch ueber n_periods."""
    r = rng_for(name)
    n = period * n_periods
    y = np.zeros(n)
    pl = period * 4
    t = secs(pl, sr)
    shape_p = np.zeros(pl)
    for f, a, tau in res:
        shape_p += a * np.exp(-t / tau) * np.sin(TAU * f * t)
    shape_p *= np.clip(t / 0.0015, 0, 1)
    cyl = 1.0 + 0.1 * r.standard_normal(4)
    for i in range(n_periods):
        g = cyl[i % 4] * (1.0 + 0.05 * r.standard_normal())
        jit = int(r.normal(0, period * 0.01))
        place(y, shape_p * g, i * period + jit, True)
    tt = secs(n, sr)
    fire = sr / period
    mod = 0.6 + 0.4 * np.cos(TAU * fire * tt)
    mech = bp(r.standard_normal(n), sr, 900, 3500, 2, circ=True)
    y = y / (np.std(y) + 1e-9) + noise_amt * mech / (np.std(mech) + 1e-9) * mod
    y += 0.35 * np.sin(TAU * fire * 0.5 * tt)
    y = lp(y, sr, cutoff, 2, circ=True)
    return finalize_loop(y, sr, None, -3.0), sr


def engine_idle():
    return _engine_loop("engine_idle", 1120, 56, [(95, 1.0, 0.012), (185, 0.7, 0.008), (340, 0.35, 0.005)], 900, 0.12)


def engine_rev():
    return _engine_loop("engine_rev", 320, 160, [(120, 1.0, 0.006), (240, 0.8, 0.005), (480, 0.5, 0.003), (900, 0.25, 0.002)], 2200, 0.3)


def siren():
    """Fiktiver Zweiton (nicht das reale Martinshorn): 494 / 659 Hz, je 0,6 s."""
    sr = SR_SFX
    seg = 0.6
    n = ns(seg * 4, sr)
    t = secs(n, sr)
    sq = np.sign(np.sin(TAU * t / (2 * seg)))
    sm = lp(sq, sr, 20.0, 2, circ=True)
    sm = sm / np.max(np.abs(sm))
    f = 576.5 + 82.5 * sm
    ph = np.cumsum(TAU * f / sr)
    cycles = ph[-1] / TAU
    ph -= TAU * (cycles - round(cycles)) * np.arange(n) / n
    y = _harm_tone(f, sr, n, 4000.0, 1.0, ph)
    y = filt(y, sr, "peak", 1800, 0.9, 4.0, circ=True)
    y = lp(y, sr, 3500, 2, circ=True)
    return finalize_loop(y, sr, None, -2.0), sr


# ======================================================================== Ambiente-Bausteine

def babble(n, sr, rng, voices=6, f0_range=(95.0, 230.0), density=0.6, child=False, kmax=3500.0):
    """Unverstaendliches Stimmengemurmel: Formant-gewichtete Obertoene je Silbe."""
    vowels = [(730, 1090), (530, 1840), (270, 2290), (570, 840), (440, 1020), (660, 1720), (300, 870), (490, 1350)]
    fs = 1.2 if child else 1.0
    cr = 200
    nc = n * cr // sr + 2
    out = np.zeros(n)
    for v in range(voices):
        base = rng.uniform(*f0_range)
        f0c = np.full(nc, base)
        F1c = np.full(nc, 500.0)
        F2c = np.full(nc, 1500.0)
        ampc = np.zeros(nc)
        tc = int(rng.uniform(0.2, 1.5) * cr)
        while tc < nc - int(0.3 * cr):
            plen = int(rng.uniform(0.8, 3.0) * cr)
            i = 0
            while i < plen and tc + i < nc - int(0.25 * cr):
                sl = int(rng.uniform(0.08, 0.26) * cr * (0.8 if child else 1.0))
                F1, F2 = vowels[rng.integers(len(vowels))]
                e = min(nc, tc + i + sl)
                seg = e - (tc + i)
                ampc[tc + i:e] = np.sin(np.pi * np.linspace(0, 1, seg)) ** 0.7 * rng.uniform(0.5, 1.0)
                F1c[tc + i:e] = F1 * fs
                F2c[tc + i:e] = F2 * fs
                contour = 1.15 - 0.3 * i / plen
                if child and rng.random() < 0.15:
                    contour *= np.linspace(1.0, rng.uniform(1.3, 1.6), seg)
                f0c[tc + i:e] = base * contour * (1.0 + (0.12 if child else 0.07) * rng.standard_normal())
                i += sl
            tc += plen + int(rng.uniform(0.3, 2.5) * cr / density)
        k5 = np.ones(5) / 5
        f0c = np.convolve(f0c, k5, "same")
        F1c = np.convolve(F1c, k5, "same")
        F2c = np.convolve(F2c, k5, "same")
        xs = np.arange(n) * cr / sr
        f0 = np.interp(xs, np.arange(nc), f0c)
        F1 = np.interp(xs, np.arange(nc), F1c)
        F2 = np.interp(xs, np.arange(nc), F2c)
        amp = np.interp(xs, np.arange(nc), ampc)
        phase = np.cumsum(TAU * f0 / sr)
        sig = np.zeros(n)
        kmax_n = int(kmax / f0_range[0]) + 1
        for k in range(1, kmax_n):
            fk = k * f0
            g = (1.0 / k) * (1.0 / (1.0 + ((fk - F1) / 90.0) ** 2) + 0.5 / (1.0 + ((fk - F2) / 140.0) ** 2) + 0.03)
            g = np.where(fk < min(kmax, 0.45 * sr), g, 0.0)
            sig += g * np.sin(k * phase)
        sig *= amp
        out += np.roll(sig, rng.integers(n)) * rng.uniform(0.4, 1.0)
    return out


def car_pass(sr, rng, dur):
    n = ns(dur, sr)
    t = secs(n, sr)
    x = (t - dur / 2) / (dur / 5)
    env = 1.0 / (1.0 + x ** 2)
    noise = bp(rng.standard_normal(n), sr, 120, 2200, 2)
    hiss = hp(rng.standard_normal(n), sr, 1500, 2) * env ** 1.5 * 0.25
    fe = rng.uniform(55, 110) * (1.0 - 0.035 * np.tanh(x * 1.5))
    tone = _harm_tone(fe, sr, n, 1200.0, 1.2, np.cumsum(TAU * fe / sr)) * 0.35
    y = (noise / (np.std(noise) + 1e-9) * 0.7 + tone) * env + hiss / (np.std(hiss) + 1e-9) * 0.1 * env
    return y


def chirp_note(sr, dur, fa, fb, harm=0.1, curve=1.0):
    n = ns(dur, sr)
    t = secs(n, sr)
    f = fa + (fb - fa) * (t / dur) ** curve
    ph = np.cumsum(TAU * f / sr)
    y = np.sin(ph)
    if 2 * max(fa, fb) < 0.45 * sr:
        y += harm * np.sin(2 * ph)
    return y * np.sin(np.pi * t / dur) ** 1.5


def bird_warbler(sr, rng):
    parts = []
    base = rng.uniform(3000, 5000)
    for _ in range(rng.integers(6, 12)):
        d = rng.uniform(0.04, 0.09)
        fa = base * rng.uniform(0.8, 1.2)
        fb = fa + rng.uniform(-1500, 1500)
        parts.append(chirp_note(sr, d, max(1500, fa), float(np.clip(fb, 1500, 0.44 * sr)), 0.05))
        parts.append(np.zeros(ns(rng.uniform(0.015, 0.05), sr)))
    return np.concatenate(parts)


def bird_tit(sr, rng):
    parts = []
    hi = rng.uniform(4600, 5400)
    lo = rng.uniform(3300, 3900)
    for _ in range(rng.integers(3, 6)):
        parts.append(chirp_note(sr, 0.09, hi, hi * 0.96, 0.05))
        parts.append(np.zeros(ns(0.03, sr)))
        parts.append(chirp_note(sr, 0.11, lo, lo * 0.93, 0.05))
        parts.append(np.zeros(ns(0.07, sr)))
    return np.concatenate(parts)


def bird_coo(sr, rng):
    parts = []
    f = rng.uniform(480, 600)
    for i in range(rng.integers(3, 6)):
        d = rng.uniform(0.35, 0.6) if i != 1 else 0.25
        parts.append(chirp_note(sr, d, f * 0.97, f * 1.02, 0.25, 0.6) * (0.8 if i else 1.0))
        parts.append(np.zeros(ns(rng.uniform(0.12, 0.25), sr)))
    return lp(np.concatenate(parts), sr, 1500, 2)


def bird_whistle(sr, rng):
    parts = []
    for _ in range(rng.integers(4, 8)):
        d = rng.uniform(0.12, 0.3)
        fa = rng.uniform(1700, 3100)
        fb = fa * rng.uniform(0.8, 1.25)
        n = ns(d, sr)
        t = secs(n, sr)
        vib = 1.0 + 0.01 * np.sin(TAU * rng.uniform(20, 35) * t)
        f = (fa + (fb - fa) * (0.5 - 0.5 * np.cos(np.pi * t / d))) * vib
        y = np.sin(np.cumsum(TAU * f / sr)) * np.sin(np.pi * t / d) ** 1.2
        parts.append(y)
        parts.append(np.zeros(ns(rng.uniform(0.03, 0.12), sr)))
    return np.concatenate(parts)


def bark(sr, rng):
    d = rng.uniform(0.12, 0.2)
    n = ns(d, sr)
    t = secs(n, sr)
    f0 = rng.uniform(380, 520) * (1.0 - 0.35 * t / d)
    ph = np.cumsum(TAU * f0 / sr)
    y = np.zeros(n)
    for k in range(1, 14):
        fk = k * f0
        g = (1.0 / k) * (1.0 / (1.0 + ((fk - 900) / 250.0) ** 2) + 0.6 / (1.0 + ((fk - 1800) / 350.0) ** 2) + 0.05)
        y += np.where(fk < 0.45 * sr, g, 0.0) * np.sin(k * ph)
    env = np.clip(t / 0.008, 0, 1) * np.exp(-t / 0.06)
    y = y * env + bp(rng.standard_normal(n), sr, 500, 2500, 2) * env * 0.08
    return y


def cricket_chirp(sr, rng, f):
    pulses = rng.integers(3, 5)
    pd = rng.uniform(0.012, 0.018)
    sp = rng.uniform(0.026, 0.034)
    n = ns(sp * pulses + pd, sr)
    y = np.zeros(n)
    pn = ns(pd, sr)
    pt = secs(pn, sr)
    p = np.sin(TAU * f * pt) * np.sin(np.pi * pt / pd) ** 2
    for i in range(pulses):
        place(y, p, ns(i * sp, sr), False)
    return y


def distant(sig, sr, dist, ir=None, wet=0.4):
    """Entfernung: Pegel, Hoehendaempfung, Nachhallanteil."""
    g = 1.0 / (1.0 + dist / 15.0)
    fc = 9000.0 / (1.0 + dist / 60.0)
    y = lp(sig, sr, fc, 1) * g
    if ir is not None:
        y = reverb(y, sr, ir, wet * min(1.0, dist / 80.0 + 0.3), 1.0)
    return y


def periodic_events(N, sr, rng, count):
    """Gleichmaessig verteilte Zeitpunkte mit Jitter."""
    return [int((i + rng.uniform(0.1, 0.9)) * N / count) for i in range(count)]


# ======================================================================== Ambiente-Schleifen

AMB_LEN = 36.0


def amb_city(day=True):
    sr = SR_AMB
    name = "amb_city_day" if day else "amb_city_night"
    r = rng_for(name)
    N = ns(AMB_LEN, sr)
    ir = make_ir(sr, 2.2, 0.03, False, 0.7, "street_far")
    y = np.zeros(N)
    rumble = lp(colored(N, sr, r, -1.0), sr, 260, 2, True)
    y += rumble / np.std(rumble) * smooth_random(N, sr, 0.12, r, 0.6, 1.0) * (1.0 if day else 0.55)
    mid = bp(colored(N, sr, r, -0.5), sr, 300, 2500, 2, True)
    y += mid / np.std(mid) * smooth_random(N, sr, 0.2, r, 0.4, 1.0) * (0.4 if day else 0.12)
    cars = 12 if day else 4
    for s in periodic_events(N, sr, r, cars):
        dur = r.uniform(3.0, 6.0)
        c = car_pass(sr, r, dur) * r.uniform(0.6, 1.4) * (1.0 if day else 0.8)
        place(y, distant(c, sr, r.uniform(25, 80)), s)
    sec = np.zeros(N)
    if day:
        for s in periodic_events(N, sr, r, 3):
            f = r.uniform(380, 470)
            hn = ns(r.uniform(0.18, 0.45), sr)
            ht = secs(hn, sr)
            h = _harm_tone(np.full(hn, f), sr, hn, 3000.0, 0.9, TAU * f * ht) * env_ar(hn, sr, 0.015, 0.05)
            place(sec, distant(h, sr, r.uniform(120, 220)) * 0.5, s)
        talk = babble(N, sr, r, 8, (95, 230), 0.7)
        talk = lp(talk, sr, 2600, 2, True)
        sec += talk / (np.std(talk) + 1e-9) * 0.12
        for s in periodic_events(N, sr, r, 3):
            sec_b = bird_tit(sr, r) if r.random() < 0.5 else bird_warbler(sr, r)
            place(sec, distant(sec_b, sr, r.uniform(60, 120)) * 0.25, s)
    else:
        crickets = np.zeros(N)
        for c in range(4):
            f = r.uniform(4200, 5200)
            period = r.uniform(0.5, 0.9)
            count = int(AMB_LEN / period)
            amp = r.uniform(0.3, 1.0)
            for i in range(count):
                place(crickets, cricket_chirp(sr, r, f) * amp * r.uniform(0.85, 1.0), int(i * N / count) + ns(r.uniform(0, 0.01), sr))
        crickets = lp(crickets, sr, 7000, 1, True)
        sec += crickets / (np.max(np.abs(crickets)) + 1e-9) * 0.35
        wind = bp(colored(N, sr, r, -0.5), sr, 300, 2000, 2, True)
        sec += wind / np.std(wind) * smooth_random(N, sr, 0.08, r, 0.0, 1.0) ** 2 * 0.06
    y = y / (np.std(y) + 1e-9) + sec * 2.0
    y = reverb(y, sr, ir, 0.2, 1.0, circ=True)
    return finalize_loop(y, sr, -25.0 if day else -28.0), sr


def amb_city_day():
    return amb_city(True)


def amb_city_night():
    return amb_city(False)


def _birds(N, sr, r, count, ir, dist_range=(15, 70), kinds=("warbler", "tit", "coo", "whistle")):
    buf = np.zeros(N)
    fns = {"warbler": bird_warbler, "tit": bird_tit, "coo": bird_coo, "whistle": bird_whistle}
    for s in periodic_events(N, sr, r, count):
        k = kinds[r.integers(len(kinds))]
        b = fns[k](sr, r) * (0.6 if k == "coo" else 1.0)
        place(buf, distant(b, sr, r.uniform(*dist_range), ir, 0.35), s)
    return buf


def _leaves(N, sr, r, amount=1.0):
    wind = bp(colored(N, sr, r, -0.5), sr, 450, 5000, 2, True)
    gust = smooth_random(N, sr, 0.08, r, 0.15, 1.0) ** 2
    flutter = smooth_random(N, sr, 3.0, r, 0.7, 1.0)
    return wind / np.std(wind) * gust * flutter * amount


def amb_park():
    sr = SR_AMB
    r = rng_for("amb_park")
    N = ns(AMB_LEN, sr)
    ir = make_ir(sr, 1.8, 0.02, False, 0.7, "park_ir")
    y = _leaves(N, sr, r, 0.25)
    far = lp(colored(N, sr, r, -1.0), sr, 200, 2, True)
    y += far / np.std(far) * 0.12
    y += _birds(N, sr, r, 16, ir) * 0.9
    y = reverb(y, sr, ir, 0.12, 1.0, circ=True)
    return finalize_loop(y, sr, -26.0), sr


def amb_residential():
    sr = SR_AMB
    r = rng_for("amb_residential")
    N = ns(AMB_LEN, sr)
    ir = make_ir(sr, 2.0, 0.03, False, 0.7, "res_ir")
    y = _leaves(N, sr, r, 0.12)
    far = lp(colored(N, sr, r, -1.0), sr, 220, 2, True)
    y += far / np.std(far) * 0.15
    y += _birds(N, sr, r, 7, ir, (30, 90), ("tit", "whistle", "coo")) * 0.7
    kids = babble(N, sr, r, 5, (240, 380), 0.45, True, 3200.0)
    kids = lp(kids, sr, 1800, 2, True)
    kids = reverb(kids, sr, ir, 0.5, 1.0, circ=True)
    y += kids / (np.std(kids) + 1e-9) * 0.05
    for s in periodic_events(N, sr, r, 2):
        grp = np.zeros(ns(1.6, sr))
        pos = 0.0
        for _ in range(r.integers(2, 4)):
            place(grp, bark(sr, r), ns(pos, sr), False)
            pos += r.uniform(0.28, 0.5)
        place(y, distant(grp, sr, r.uniform(90, 160), ir, 0.6) * 0.6, s)
    car = car_pass(sr, r, 5.0)
    place(y, distant(car, sr, 50) * 0.8, ns(r.uniform(5, 30), sr))
    y = reverb(y, sr, ir, 0.1, 1.0, circ=True)
    return finalize_loop(y, sr, -28.0), sr


def amb_shop():
    sr = SR_AMB
    r = rng_for("amb_shop")
    N = ns(AMB_LEN, sr)
    ir = make_ir(sr, 0.8, 0.01, False, 0.6, "shop_room")
    t = secs(N, sr)
    hvac = lp(colored(N, sr, r, -0.8), sr, 600, 2, True)
    y = hvac / np.std(hvac) * 0.35
    y += (np.sin(TAU * 100 * t) + 0.4 * np.sin(TAU * 200 * t) + 0.2 * np.sin(TAU * 300 * t)) * 0.02
    talk = babble(N, sr, r, 5, (100, 240), 0.55)
    talk = lp(talk, sr, 3500, 2, True)
    y += talk / (np.std(talk) + 1e-9) * 0.3
    beeps = np.zeros(N)
    for s in periodic_events(N, sr, r, 5):
        bn = ns(0.1, sr)
        bt = secs(bn, sr)
        b = np.sin(TAU * 1880 * bt) * env_ar(bn, sr, 0.004, 0.02)
        place(beeps, b, s)
        if r.random() < 0.5:
            place(beeps, b, s + ns(0.9, sr))
    y += beeps * 0.06
    rattle = np.zeros(N)
    for s in periodic_events(N, sr, r, 2):
        d = r.uniform(1.5, 2.5)
        cn = ns(d, sr)
        c = _grains(sr, r, d, 300, 600, 4000, np.sin(np.pi * secs(cn, sr) / d))
        place(rattle, c, s)
    y += rattle / (np.max(np.abs(rattle)) + 1e-9) * 0.08
    y = reverb(y, sr, ir, 0.3, 1.0, circ=True)
    return finalize_loop(y, sr, -27.0), sr


def amb_rain():
    sr = SR_AMB
    r = rng_for("amb_rain")
    N = ns(AMB_LEN, sr)
    bed = hp(colored(N, sr, r, -0.4), sr, 300, 2, True)
    bed = filt(bed, sr, "peak", 4500, 0.7, 4.0, circ=True)
    y = bed / np.std(bed) * smooth_random(N, sr, 0.15, r, 0.8, 1.0)
    rumble = lp(colored(N, sr, r, -1.0), sr, 400, 2, True)
    y += rumble / np.std(rumble) * 0.4
    drops = np.zeros(N)
    count = int(140 * AMB_LEN)
    starts = r.integers(0, N, count)
    for s in starts:
        f = r.uniform(1500, 6500)
        dn = ns(r.uniform(0.004, 0.012), sr)
        dt = secs(dn, sr)
        d = np.sin(TAU * f * dt) * np.exp(-dt / (dn / sr / 3.0))
        place(drops, d * r.exponential(0.4), s)
    for s in r.integers(0, N, int(6 * AMB_LEN)):
        dn = ns(0.03, sr)
        dt = secs(dn, sr)
        d = np.sin(TAU * r.uniform(600, 1400) * dt) * np.exp(-dt / 0.008)
        place(drops, d * r.uniform(0.5, 1.2), s)
    y += drops / (np.std(drops) + 1e-9) * 0.25
    return finalize_loop(y, sr, -24.0), sr


def amb_river():
    sr = SR_AMB
    r = rng_for("amb_river")
    N = ns(AMB_LEN, sr)
    bed = lp(colored(N, sr, r, -0.7), sr, 1400, 2, True)
    y = bed / np.std(bed) * smooth_random(N, sr, 0.3, r, 0.7, 1.0)
    for c, w in ((500, 0.3), (900, 0.25), (1600, 0.15)):
        band = bp(colored(N, sr, r, -0.5), sr, c * 0.8, c * 1.25, 2, True)
        y += band / np.std(band) * smooth_random(N, sr, 1.5, r, 0.0, 1.0) ** 2 * w
    bub = np.zeros(N)
    for s in r.integers(0, N, int(55 * AMB_LEN)):
        f0 = r.uniform(300, 1500)
        tau = r.uniform(0.006, 0.025)
        dn = ns(tau * 4, sr)
        dt = secs(dn, sr)
        f = f0 * (1.0 + r.uniform(2, 8) * dt)
        b = np.sin(np.cumsum(TAU * f / sr)) * np.exp(-dt / tau) * np.clip(dt / 0.001, 0, 1)
        place(bub, b * r.exponential(0.5), s)
    y += bub / (np.std(bub) + 1e-9) * 0.35
    y = reverb(y, sr, make_ir(sr, 1.2, 0.01, False, 0.7, "river_ir"), 0.15, 1.0, circ=True)
    return finalize_loop(y, sr, -25.0), sr


# ======================================================================== Musik

class Song:
    def __init__(self, name, bpm, bars, sr=SR_MUS):
        self.name = name
        self.sr = sr
        self.beat = 60.0 / bpm
        self.bar = self.beat * 4
        self.n = ns(self.bar * bars, sr)
        self.bars = bars
        self.buses = {}
        self.rng = rng_for(name)

    def bus(self, b):
        if b not in self.buses:
            self.buses[b] = np.zeros((self.n, 2))
        return self.buses[b]

    def add(self, bus_name, sig, t_beats, pan=0.0, gain=1.0):
        place(self.bus(bus_name), pan2(np.asarray(sig) * gain, pan), ns(t_beats * self.beat, self.sr))

    def mix(self, levels, ir, wet, master_lp=None):
        out = np.zeros((self.n, 2))
        send = np.zeros((self.n, 2))
        for b, buf in self.buses.items():
            g, s = levels.get(b, (1.0, 0.3))
            out += buf * g
            send += buf * g * s
        out += fftconv(send, ir, True) * wet
        if master_lp:
            out = lp(out, self.sr, master_lp, 1, True)
        return out


def voicing_root(m):
    """Grundton in die Bass-Lage 33..44 legen."""
    while m >= 45:
        m -= 12
    while m < 33:
        m += 12
    return m


def mus_menu():
    s = Song("mus_menu", 80, 32)
    sr = s.sr
    r = s.rng
    C = {
        "Dmaj7": (50, [54, 57, 61, 64]), "Bm7": (47, [54, 57, 62, 66]), "Gmaj7": (43, [54, 59, 62, 66]),
        "Asus4": (45, [52, 57, 62, 64]), "A": (45, [52, 57, 61, 64]), "A6": (45, [54, 57, 61, 64]),
        "F#m7": (42, [52, 57, 61, 64]), "Em7": (40, [55, 59, 62, 66]),
    }
    A = ["Dmaj7", "Bm7", "Gmaj7", "Asus4"]
    B = ["Gmaj7", "A6", "F#m7", "Bm7", "Em7", "F#m7", "Gmaj7", "Asus4"]
    prog = A + A + A + A + B + A + ["Dmaj7", "Bm7", "Gmaj7", "A"]
    for bar, ch in enumerate(prog):
        root, v = C[ch]
        t0 = bar * 4
        for m in v:
            s.add("pad", pad(mtof(m), s.bar * 0.98, sr, r, 1100, 1.4, 1.8), t0, r.uniform(-0.5, 0.5), 0.16)
        if bar >= 8:
            s.add("bass", bass(mtof(voicing_root(root)), s.bar * 0.9, sr, 0.55), t0, 0.0, 0.5)
        dense = 8 <= bar < 28
        arp = [v[0] + 12, v[1] + 12, v[2] + 12, v[3] + 12, v[2] + 12, v[1] + 12, v[3] + 12, v[2] + 12]
        steps = range(8) if dense else range(0, 8, 2)
        for i in steps:
            vel = (0.42 if i % 2 == 0 else 0.3) * (1.0 if dense else 0.85) * r.uniform(0.9, 1.05)
            s.add("piano", piano(mtof(arp[i]), s.beat * 1.6, sr, vel), t0 + i * 0.5 + r.uniform(0, 0.012), 0.25 * np.sin(i), 1.0)
    mel_a = [
        [(78, 1.5), (76, 0.5), (74, 1), (69, 1)], [(71, 1.5), (73, 0.5), (74, 1), (78, 1)],
        [(76, 2), (74, 1), (71, 1)], [(73, 3), (None, 1)],
        [(78, 1.5), (81, 0.5), (78, 1), (76, 1)], [(74, 1.5), (76, 0.5), (78, 1), (71, 1)],
        [(74, 1), (73, 1), (71, 1), (74, 1)], [(76, 2), (None, 2)],
    ]
    mel_b = [
        [(71, 1), (74, 1), (78, 1.5), (76, 0.5)], [(76, 1), (73, 1), (69, 2)],
        [(73, 1), (76, 1), (81, 1.5), (78, 0.5)], [(78, 2), (74, 1), (71, 1)],
        [(79, 1.5), (78, 0.5), (76, 1), (74, 1)], [(73, 1.5), (74, 0.5), (76, 2)],
        [(74, 1), (71, 1), (74, 1), (78, 1)], [(76, 3), (None, 1)],
    ]

    def melody(bars, start_bar, vel=0.62, octave=0):
        for bi, notes in enumerate(bars):
            t = (start_bar + bi) * 4
            for m, d in notes:
                if m is not None:
                    s.add("lead", piano(mtof(m + octave), d * s.beat * 1.1, sr, vel * r.uniform(0.93, 1.03)), t + r.uniform(0, 0.01), 0.1)
                t += d
    # Teil A mit Melodie (Takt 8..15), Teil B (16..23), A' (24..27) mit halber Melodie
    melody(mel_a, 8)
    melody(mel_b, 16, 0.62)
    melody(mel_a[:4], 24, 0.5)
    for bar in (8, 16, 24):
        s.add("bell", bell(mtof(86), sr, 1.6, 0.25), bar * 4, 0.4)
    ir = make_ir(sr, 2.4, 0.025, True, 0.6, "music_hall")
    out = s.mix({"pad": (0.9, 0.5), "piano": (0.9, 0.35), "lead": (1.0, 0.35), "bass": (0.8, 0.05), "bell": (0.6, 0.6)}, ir, 0.55)
    return finalize_loop(out, sr, -16.0), sr


def mus_day():
    s = Song("mus_day", 90, 24)
    sr = s.sr
    r = s.rng
    G = {
        "G": [43, 47, 50, 55, 59, 67], "D/F#": [42, 45, 50, 57, 62, 66], "Em7": [40, 47, 50, 55, 59, 62],
        "Cmaj7": [48, 52, 55, 59, 64, 67], "Am7": [45, 52, 55, 60, 64, 67], "D": [50, 54, 57, 62, 66, 69],
        "Bm7": [47, 54, 57, 62, 66, 69], "Dsus4": [50, 55, 57, 62, 67, 69],
    }
    A = ["G", "D/F#", "Em7", "Cmaj7"]
    B = ["Am7", "D", "Bm7", "Em7", "Cmaj7", "D", "G", "Dsus4"]
    prog = A + A + A + A + B
    pattern = [0, 3, 4, 3, 1, 3, 4, 5]
    for bar, ch in enumerate(prog):
        v = G[ch]
        t0 = bar * 4
        for i, si in enumerate(pattern):
            m = v[si]
            vel = (0.55 if i in (0, 4) else 0.38) * r.uniform(0.9, 1.05)
            swing = 0.04 if i % 2 else 0.0
            pl = ks_pluck(mtof(m), s.beat * 2.5, sr, r, 0.35 + 0.2 * vel, 2.0)
            s.add("guitar", pl * vel, t0 + i * 0.5 + swing + r.uniform(0, 0.01), -0.25 + 0.1 * (si - 2), 1.0)
        root = voicing_root(v[0])
        if bar >= 4:
            s.add("bass", bass(mtof(root), s.beat * 1.7, sr, 0.5), t0, 0.0)
            s.add("bass", bass(mtof(root if bar % 2 else root + 7 if root + 7 < 47 else root - 5), s.beat * 1.7, sr, 0.4), t0 + 2, 0.0)
        for m in v[2:5]:
            s.add("pad", pad(mtof(m + 12), s.bar * 0.98, sr, r, 1500, 1.0, 1.5), t0, r.uniform(-0.6, 0.6), 0.06)
        if 4 <= bar:
            for i in range(8):
                if i % 2 == 1:
                    s.add("perc", shaker(sr, r, 0.22 * r.uniform(0.8, 1.1)), t0 + i * 0.5 + 0.04, 0.35)
    mel = [
        [(74, 1), (71, 0.5), (74, 0.5), (79, 1), (78, 1)], [(76, 1.5), (74, 0.5), (69, 2)],
        [(71, 1), (74, 1), (76, 1), (79, 1)], [(76, 2), (74, 1), (71, 1)],
        [(74, 1), (79, 1), (81, 1), (83, 1)], [(81, 1.5), (78, 0.5), (74, 2)],
        [(76, 1), (79, 1), (78, 1), (76, 1)], [(74, 3), (None, 1)],
        [(72, 2), (76, 2)], [(78, 2), (74, 2)], [(74, 2), (78, 2)], [(79, 2), (76, 2)],
        [(76, 2), (79, 2)], [(78, 2), (81, 2)], [(83, 2), (79, 2)], [(81, 4)],
    ]
    for bi, notes in enumerate(mel):
        t = (8 + bi) * 4
        for m, d in notes:
            if m is not None:
                s.add("lead", mallet(mtof(m), sr, 0.6 * r.uniform(0.92, 1.03), min(1.6, d * s.beat + 0.6)), t, 0.2)
            t += d
    ir = make_ir(sr, 1.6, 0.02, True, 0.6, "music_room")
    out = s.mix({"guitar": (0.75, 0.25), "bass": (0.7, 0.05), "pad": (0.8, 0.5), "perc": (0.5, 0.2), "lead": (0.85, 0.35)}, ir, 0.45)
    return finalize_loop(out, sr, -16.0), sr


def mus_night():
    s = Song("mus_night", 72, 20)
    sr = s.sr
    r = s.rng
    C = {
        "Fmaj9": (41, [57, 60, 64, 67]), "Em7": (40, [55, 59, 62, 64]), "Dm9": (38, [53, 57, 60, 64]),
        "Cmaj9": (36, [52, 55, 59, 62]), "Bbmaj9": (34, [57, 60, 62, 65]), "Am7": (45, [55, 60, 64, 67]),
        "Gm9": (43, [53, 58, 62, 69]), "C9sus": (36, [53, 58, 62, 67]),
    }
    A = ["Fmaj9", "Em7", "Dm9", "Cmaj9"]
    B = ["Bbmaj9", "Am7", "Gm9", "C9sus"]
    prog = A + A + A + B + A
    sw = 0.5 * 0.18
    for bar, ch in enumerate(prog):
        root, v = C[ch]
        t0 = bar * 4
        for hit, dur, vel in ((0.0, 1.6, 0.5), (2.5 + sw, 1.2, 0.36)):
            for j, m in enumerate(v):
                s.add("ep", epiano(mtof(m), dur * s.beat, sr, vel * r.uniform(0.9, 1.05)), t0 + hit + j * 0.02, -0.3 + 0.2 * j, 1.0)
        if bar >= 4:
            s.add("bass", bass(mtof(voicing_root(root)), s.beat * 1.6, sr, 0.55), t0, 0.0)
            s.add("bass", bass(mtof(voicing_root(root)), s.beat * 0.8, sr, 0.4), t0 + 2.5 + sw, 0.0)
        if 4 <= bar < 20:
            for b, v_ in ((0.0, 0.8), (2.5 + sw, 0.5)):
                s.add("drums", kick(sr, v_), t0 + b, 0.0, 0.9)
            for b in (1.0, 3.0):
                s.add("drums", snare_soft(sr, r, 0.45 * r.uniform(0.9, 1.05)), t0 + b + 0.01, 0.1)
            for i in range(8):
                tb = i * 0.5 + (sw if i % 2 else 0.0)
                s.add("hats", hat_soft(sr, r, (0.22 if i % 2 == 0 else 0.15) * r.uniform(0.8, 1.1)), t0 + tb, 0.3)
    mel = [
        [(76, 1.5), (74, 0.5), (72, 2)], [(76, 1), (79, 1), (76, 1), (74, 1)],
        [(74, 2), (69, 1), (72, 1)], [(74, 3), (None, 1)],
    ]
    for start in (12, 16):
        for bi, notes in enumerate(mel):
            t = (start + bi) * 4
            for m, d in notes:
                if m is not None:
                    s.add("lead", epiano(mtof(m), d * s.beat, sr, 0.5) * 0.8, t, 0.15)
                    s.add("lead", bell(mtof(m + 12), sr, 0.8, 0.08), t, 0.15)
                t += d
    ir = make_ir(sr, 1.9, 0.03, True, 0.75, "music_night")
    out = s.mix({"ep": (0.8, 0.35), "bass": (0.75, 0.03), "drums": (0.5, 0.08), "hats": (0.35, 0.15), "lead": (0.8, 0.4)}, ir, 0.4, master_lp=7500)
    return finalize_loop(out, sr, -16.0), sr


# ======================================================================== Katalog

def V(fn, i):
    return lambda: fn(i)


SOUNDS = {
    # name: (Funktion, Unterordner, Schleife)
    "ui_hover": (ui_hover, "sfx", False),
    "ui_click": (ui_click, "sfx", False),
    "ui_confirm": (ui_confirm, "sfx", False),
    "ui_back": (ui_back, "sfx", False),
    "ui_error": (ui_error, "sfx", False),
    "ui_notification": (ui_notification, "sfx", False),
    "ui_money_gain": (money_gain, "sfx", False),
    "ui_money_spend": (money_spend, "sfx", False),
    "ui_phone_open": (phone_open, "sfx", False),
    "ui_phone_close": (phone_close, "sfx", False),
    "ui_levelup": (ui_levelup, "sfx", False),
    "interact": (interact, "sfx", False),
    "pickup": (pickup, "sfx", False),
    "eat": (eat, "sfx", False),
    "drink": (drink, "sfx", False),
    "keys_jingle": (keys_jingle, "sfx", False),
    "camera_shutter": (shutter, "sfx", False),
    "door_open": (door_open, "sfx", False),
    "door_close": (door_close, "sfx", False),
    "shop_bell": (shop_bell, "sfx", False),
    "cash_register": (cash_register, "sfx", False),
    "scanner_beep": (scanner_beep, "sfx", False),
    "bag_rustle": (bag_rustle, "sfx", False),
    "car_horn": (horn_car, "sfx", False),
    "bus_horn": (bus_horn, "sfx", False),
    "bus_door": (bus_door, "sfx", False),
    "brake_squeal": (brake_squeal, "sfx", False),
    "car_door": (car_door, "sfx", False),
    "engine_idle": (engine_idle, "sfx", True),
    "engine_rev": (engine_rev, "sfx", True),
    "siren": (siren, "sfx", True),
    "amb_city_day": (amb_city_day, "ambience", True),
    "amb_city_night": (amb_city_night, "ambience", True),
    "amb_park": (amb_park, "ambience", True),
    "amb_residential": (amb_residential, "ambience", True),
    "amb_shop": (amb_shop, "ambience", True),
    "amb_rain": (amb_rain, "ambience", True),
    "amb_river": (amb_river, "ambience", True),
    "mus_menu": (mus_menu, "music", True),
    "mus_day": (mus_day, "music", True),
    "mus_night": (mus_night, "music", True),
}
for _i in range(1, 5):
    SOUNDS["footstep_concrete_%d" % _i] = (V(footstep_concrete, _i - 1), "sfx", False)
    SOUNDS["footstep_grass_%d" % _i] = (V(footstep_grass, _i - 1), "sfx", False)
    SOUNDS["footstep_wood_%d" % _i] = (V(footstep_wood, _i - 1), "sfx", False)


# ======================================================================== Analyse

def ffmpeg_lufs(path):
    if shutil.which("ffmpeg") is None:
        return None
    try:
        p = subprocess.run(["ffmpeg", "-nostats", "-hide_banner", "-i", path, "-af", "ebur128=framelog=quiet", "-f", "null", "-"],
                           capture_output=True, text=True, timeout=120)
    except Exception:
        return None
    val = None
    for line in p.stderr.splitlines():
        line = line.strip()
        if line.startswith("I:") and "LUFS" in line:
            val = float(line.split()[1])
    return val


def analyze(names=None):
    rows = []
    total = 0
    for name in sorted(SOUNDS):
        _, sub, loop_expected = SOUNDS[name]
        path = os.path.join(OUT, sub, name + ".wav")
        if not os.path.exists(path):
            rows.append((name, "FEHLT"))
            continue
        total += os.path.getsize(path)
        if names and not any(name.startswith(nm) for nm in names):
            continue
        x, sr, loop = read_wav(path)
        mono = x.mean(axis=1)
        peak = a2db(np.max(np.abs(x)))
        rms = a2db(np.sqrt(np.mean(x ** 2)))
        dc = float(np.max(np.abs(np.mean(x, axis=0))))
        dur = len(x) / sr
        d = np.abs(np.diff(x, axis=0)).max(axis=1)
        p999 = float(np.percentile(d, 99.9))
        seam = float(np.max(np.abs(x[0] - x[-1]))) if loop else 0.0
        X = np.abs(np.fft.rfft(mono)) ** 2
        f = np.fft.rfftfreq(len(mono), 1.0 / sr)
        cen = float(np.sum(f * X) / (np.sum(X) + 1e-20))
        cum = np.cumsum(X) / (np.sum(X) + 1e-20)
        bw95 = float(f[np.searchsorted(cum, 0.95)])
        lu = ffmpeg_lufs(path) if (loop or dur > 1.5) else None
        rows.append((name, sr, x.shape[1], dur, peak, rms, dc, loop, loop_expected, seam, p999, cen, bw95, lu))
    print("%-22s %6s %2s %7s %7s %7s %8s %4s %9s %9s %7s %7s %7s" % (
        "Name", "Rate", "Ch", "Dauer", "Spitze", "RMS", "DC", "Loop", "Naht", "p99.9dx", "Schwerp", "95%Bw", "LUFS"))
    problems = []
    for row in rows:
        if len(row) == 2:
            print("%-22s FEHLT" % row[0])
            problems.append(row[0] + ": Datei fehlt")
            continue
        name, sr, ch, dur, peak, rms, dc, loop, loop_exp, seam, p999, cen, bw, lu = row
        print("%-22s %6d %2d %6.2fs %6.1fdB %6.1fdB %8.5f %4s %9.5f %9.5f %6.0fHz %6.0fHz %7s" % (
            name, sr, ch, dur, peak, rms, dc, "ja" if loop else "-", seam, p999, cen, bw,
            ("%.1f" % lu) if lu is not None else "-"))
        if peak > -0.9:
            problems.append("%s: Spitze %.2f dBFS > -1" % (name, peak))
        if dc > 0.002:
            problems.append("%s: DC %.4f" % (name, dc))
        if loop != loop_exp:
            problems.append("%s: Schleifenmarke falsch" % name)
        if loop and seam > max(p999, 1e-3):
            problems.append("%s: Nahtsprung %.4f > p99.9 %.4f" % (name, seam, p999))
    print("\nGesamtgroesse: %.2f MB" % (total / 1e6))
    print("Probleme: %d" % len(problems))
    for p in problems:
        print("  - " + p)
    return problems


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default="")
    ap.add_argument("--analyze", action="store_true")
    args = ap.parse_args()
    names = [s for s in args.only.split(",") if s]
    if not args.analyze:
        for name in sorted(SOUNDS):
            if names and not any(name.startswith(nm) for nm in names):
                continue
            fn, sub, loop = SOUNDS[name]
            x, sr = fn()
            path = os.path.join(OUT, sub, name + ".wav")
            write_wav(path, x, sr, loop)
            print("geschrieben: %s (%.2f s)" % (os.path.relpath(path, os.path.join(HERE, "..")), len(x) / sr), flush=True)
    problems = analyze(names)
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
