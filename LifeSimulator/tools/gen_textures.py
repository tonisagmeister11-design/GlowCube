#!/usr/bin/env python3
"""Erzeugt alle kachelbaren Stadt-Texturen fuer Life Simulator.

Aufruf (aus dem Projektordner LifeSimulator/):
    python3 tools/gen_textures.py

Ausgabe unter assets/textures/:
  <name>_albedo.png  RGBA, sRGB-Farbe, Alpha = Rauheit (roughness)
  <name>_normal.png  RGB, Normal Map im OpenGL-Format (gruen = oben)
sowie Sonderatlanten (Verkehrsschilder, Innenraum-Rueckwaende, Rauschen).
Zu jeder PNG wird eine .import-Datei mit Mipmaps / VRAM-Kompression
geschrieben, damit die Texturen auch ohne Editor korrekt importiert werden.

Alles ist deterministisch (feste Seeds) und nur mit numpy + PIL erzeugt.
"""

import os
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "assets", "textures")
FONT_BOLD = os.path.join(ROOT, "assets", "fonts", "Montserrat-Variable.ttf")


# ------------------------------------------------------------------ Grundlagen

def rng(seed):
    return np.random.default_rng(seed)


def fft_noise(size, beta, seed, lo=1.0, hi=None):
    """Kachelbares Rauschen mit 1/f^beta Spektrum, normiert auf 0..1."""
    r = rng(seed)
    white = r.standard_normal((size, size))
    f = np.fft.fft2(white)
    fy = np.fft.fftfreq(size)[:, None] * size
    fx = np.fft.fftfreq(size)[None, :] * size
    k = np.sqrt(fx * fx + fy * fy)
    k[0, 0] = 1.0
    amp = 1.0 / np.power(k, beta)
    amp[k < lo] = 0.0
    if hi is not None:
        amp[k > hi] = 0.0
    out = np.real(np.fft.ifft2(f * amp))
    out -= out.min()
    out /= max(out.max(), 1e-9)
    return out


def norm01(a):
    a = a - a.min()
    return a / max(a.max(), 1e-9)


def blur_wrap(a, radius):
    """Gauss-Weichzeichner mit Kachelung (per FFT)."""
    size_y, size_x = a.shape
    fy = np.fft.fftfreq(size_y)[:, None]
    fx = np.fft.fftfreq(size_x)[None, :]
    sigma = radius
    g = np.exp(-2.0 * (math.pi ** 2) * (sigma ** 2) * (fx * fx + fy * fy))
    return np.real(np.fft.ifft2(np.fft.fft2(a) * g))


def normal_from_height(h, strength):
    """OpenGL-Normal-Map (gruen = +Y = Bildoben) aus Hoehenfeld, kachelbar."""
    dx = (np.roll(h, -1, axis=1) - np.roll(h, 1, axis=1)) * 0.5
    drow = (np.roll(h, -1, axis=0) - np.roll(h, 1, axis=0)) * 0.5
    nx = -dx * strength
    ny = drow * strength
    nz = np.ones_like(h)
    ln = np.sqrt(nx * nx + ny * ny + nz * nz)
    n = np.stack([nx / ln, ny / ln, nz / ln], axis=-1)
    return ((n * 0.5 + 0.5) * 255.0).clip(0, 255).astype(np.uint8)


def save_rgba(name, rgb, rough):
    os.makedirs(OUT, exist_ok=True)
    rgb8 = (np.clip(rgb, 0, 1) * 255.0 + 0.5).astype(np.uint8)
    a8 = (np.clip(rough, 0, 1) * 255.0 + 0.5).astype(np.uint8)
    img = np.dstack([rgb8, a8])
    path = os.path.join(OUT, name + "_albedo.png")
    Image.fromarray(img, "RGBA").save(path, optimize=True)
    write_import(path, normal=False)


def save_normal(name, n8):
    path = os.path.join(OUT, name + "_normal.png")
    Image.fromarray(n8, "RGB").save(path, optimize=True)
    write_import(path, normal=True)


def save_image(name, img, normal=False, mip=True):
    path = os.path.join(OUT, name + ".png")
    img.save(path, optimize=True)
    write_import(path, normal=normal, mip=mip)


def write_import(path, normal=False, mip=True):
    """Importeinstellungen: VRAM-Kompression + Mipmaps fuer 3D."""
    rel = "res://" + os.path.relpath(path, ROOT).replace(os.sep, "/")
    txt = (
        "[remap]\n\n"
        "importer=\"texture\"\n"
        "type=\"CompressedTexture2D\"\n\n"
        "[deps]\n\n"
        "source_file=\"%s\"\n\n"
        "[params]\n\n"
        "compress/mode=2\n"
        "compress/high_quality=false\n"
        "compress/lossy_quality=0.7\n"
        "compress/hdr_compression=1\n"
        "compress/normal_map=%d\n"
        "compress/channel_pack=0\n"
        "mipmaps/generate=%s\n"
        "mipmaps/limit=-1\n"
        "roughness/mode=0\n"
        "roughness/src_normal=\"\"\n"
        "process/fix_alpha_border=true\n"
        "process/premult_alpha=false\n"
        "process/normal_map_invert_y=false\n"
        "process/hdr_as_srgb=false\n"
        "process/hdr_clamp_exposure=false\n"
        "process/size_limit=0\n"
        "detect_3d/compress_to=0\n"
    ) % (rel, 1 if normal else 2, "true" if mip else "false")
    with open(path + ".import", "w") as f:
        f.write(txt)


def lerp(a, b, t):
    return a + (b - a) * t


def col(*c):
    return np.array(c, dtype=np.float64)


def tint(gray, c):
    """Grauwert-Feld (HxW) mit Farbe multiplizieren -> HxWx3."""
    return gray[..., None] * col(*c)[None, None, :]


# ------------------------------------------------------------------ Materialien

def asphalt():
    s = 1024
    base = fft_noise(s, 1.6, 11, lo=1)            # grossflaechige Flecken
    mid = fft_noise(s, 1.0, 12, lo=8)
    fine = fft_noise(s, 0.2, 13, lo=64)
    r = rng(14)
    # Gesteinskoernung: helle und dunkle Punkte
    grain = fft_noise(s, 0.0, 15, lo=180)
    stones_l = np.clip((grain - 0.70) * 8.0, 0, 1)
    stones_d = np.clip((0.28 - grain) * 8.0, 0, 1)
    # Risse: Isolinien eines mittleren Rauschens, nur in Teilbereichen
    crack_src = fft_noise(s, 1.4, 16, lo=3)
    crack = np.clip(1.0 - np.abs(crack_src - 0.5) / 0.0045, 0, 1)
    crack_mask = np.clip((fft_noise(s, 1.8, 17, lo=1) - 0.62) * 6.0, 0, 1)
    crack *= crack_mask
    g = 0.34 + (base - 0.5) * 0.06 + (mid - 0.5) * 0.05 + (fine - 0.5) * 0.06
    g += stones_l * 0.16 - stones_d * 0.10
    g *= 1.0 - crack * 0.45
    rgb = tint(g, (1.0, 0.99, 0.97))
    rough = 0.86 + (fine - 0.5) * 0.1 - stones_l * 0.12 + crack * 0.05
    h = fine * 0.5 + stones_l * 0.9 - stones_d * 0.5 - crack * 1.2 + mid * 0.3
    save_rgba("asphalt", rgb, rough)
    save_normal("asphalt", normal_from_height(h, 2.2))


def slab_grid(s, nx, ny, seed, grout_px, bevel_px, offset_rows=False):
    """Plattenraster: liefert (id, rand_dist) fuer jede Platte."""
    y, x = np.mgrid[0:s, 0:s].astype(np.float64)
    ch = s / ny
    cw = s / nx
    row = np.floor(y / ch)
    xo = x + (np.mod(row, 2) * cw * 0.5 if offset_rows else 0.0)
    xo = np.mod(xo, s)
    col_i = np.floor(xo / cw)
    ly = y - row * ch
    lx = xo - col_i * cw
    edge = np.minimum(np.minimum(lx, cw - 1 - lx), np.minimum(ly, ch - 1 - ly))
    ids = (row * 131 + col_i * 7919) % 10007
    r = rng(seed)
    table = r.random(10007)
    return table[ids.astype(np.int64)], edge


def pavers():
    """Gehwegplatten 40 x 40 cm (Kachel 2 m, 5 x 5 Platten)."""
    s = 512
    rid, edge = slab_grid(s, 5, 5, 21, 3, 6)
    fine = fft_noise(s, 0.4, 22, lo=40)
    stain = fft_noise(s, 1.7, 23, lo=1)
    grout = np.clip(1.0 - (edge - 1.0) / 1.5, 0, 1)
    bevel = np.clip(edge / 6.0, 0, 1)
    g = 0.66 + (rid - 0.5) * 0.08 + (fine - 0.5) * 0.08 - np.clip(stain - 0.6, 0, 1) * 0.25
    g = lerp(g, 0.36, grout)
    rgb = tint(g, (1.0, 0.985, 0.95))
    rough = 0.82 + (fine - 0.5) * 0.12 + grout * 0.1
    h = bevel * 0.6 + fine * 0.15 + (rid - 0.5) * 0.15
    save_rgba("pavers", rgb, rough)
    save_normal("pavers", normal_from_height(h * 6.0, 1.4))


def plaza():
    """Rechteck-Pflaster im Laeuferverband, warme Toene (Plaetze, Maerkte)."""
    s = 512
    rid, edge = slab_grid(s, 10, 20, 31, 2, 4, offset_rows=True)
    fine = fft_noise(s, 0.5, 32, lo=30)
    grout = np.clip(1.0 - (edge - 0.5) / 1.5, 0, 1)
    bevel = np.clip(edge / 4.0, 0, 1)
    c1 = col(0.72, 0.62, 0.52)
    c2 = col(0.60, 0.56, 0.52)
    c3 = col(0.66, 0.50, 0.42)
    pick = rid[..., None]
    rgb = np.where(pick < 0.4, c1, np.where(pick < 0.75, c2, c3)) * (0.9 + 0.2 * fine[..., None])
    rgb = lerp(rgb, col(0.35, 0.33, 0.31), grout[..., None])
    rough = 0.8 + (fine - 0.5) * 0.1 + grout * 0.1
    h = bevel + fine * 0.2
    save_rgba("plaza", rgb, rough)
    save_normal("plaza", normal_from_height(h * 4.0, 1.5))


def curb():
    s = 256
    grain = fft_noise(s, 0.0, 41, lo=60)
    mid = fft_noise(s, 1.2, 42, lo=4)
    g = 0.70 + (grain - 0.5) * 0.25 + (mid - 0.5) * 0.08
    speck = np.clip((grain - 0.75) * 6.0, 0, 1)
    g -= speck * 0.25
    rgb = tint(g, (0.98, 0.98, 0.98))
    rough = 0.7 + (grain - 0.5) * 0.15
    save_rgba("curb", rgb, rough)
    save_normal("curb", normal_from_height(grain * 0.6 + mid, 1.0))


def concrete():
    s = 512
    mid = fft_noise(s, 1.3, 51, lo=2)
    fine = fft_noise(s, 0.5, 52, lo=30)
    pores = np.clip((fft_noise(s, 0.0, 53, lo=150) - 0.78) * 7.0, 0, 1)
    # Schalungsfugen alle 1.0 m (Kachel 3 m -> 3 Felder) senkrecht und waagerecht
    y, x = np.mgrid[0:s, 0:s].astype(np.float64)
    seam = np.zeros((s, s))
    for k in range(3):
        p = k * s / 3.0
        seam = np.maximum(seam, np.clip(1.0 - np.abs(x - p) / 1.2, 0, 1))
    seam = np.maximum(seam, np.clip(1.0 - np.abs(y - 0) / 1.2, 0, 1))
    seam = np.maximum(seam, np.clip(1.0 - np.abs(y - s / 2.0) / 1.2, 0, 1))
    g = 0.68 + (mid - 0.5) * 0.12 + (fine - 0.5) * 0.06 - pores * 0.15 - seam * 0.12
    rgb = tint(g, (1.0, 0.99, 0.97))
    rough = 0.88 + (fine - 0.5) * 0.08
    h = fine * 0.3 - pores * 0.8 - seam * 0.8 + mid * 0.2
    save_rgba("concrete", rgb, rough)
    save_normal("concrete", normal_from_height(h, 1.6))


def brick():
    """Ziegel im Laeuferverband. Kachel = 1.2 m x 1.2 m, 4 Steine x 16 Schichten."""
    s = 512
    rid, edge = slab_grid(s, 4, 16, 61, 2, 3, offset_rows=True)
    fine = fft_noise(s, 0.6, 62, lo=30)
    mid = fft_noise(s, 1.4, 63, lo=2)
    mortar = np.clip(1.0 - (edge - 1.5) / 1.5, 0, 1)
    base = col(0.62, 0.30, 0.22)
    var = np.stack([rid, np.roll(rid, 37, axis=1), np.roll(rid, 91, axis=0)], -1)
    rgb = base[None, None, :] * (0.78 + 0.35 * var) * (0.92 + 0.16 * fine[..., None])
    # einzelne dunkle Klinker
    dark = (rid > 0.9)[..., None]
    rgb = np.where(dark, rgb * 0.62, rgb)
    rgb *= (0.95 + 0.1 * mid[..., None])
    rgb = lerp(rgb, col(0.72, 0.70, 0.66), mortar[..., None])
    rough = 0.86 + (fine - 0.5) * 0.1
    h = (1.0 - mortar) * 0.8 + fine * 0.25
    save_rgba("brick", rgb, rough)
    save_normal("brick", normal_from_height(h * 3.0, 1.6))


def plaster():
    """Feiner Putz, fast weiss (wird im Shader eingefaerbt). Kachel 2 m."""
    s = 512
    fine = fft_noise(s, 0.3, 71, lo=60)
    mid = fft_noise(s, 1.0, 72, lo=6)
    big = fft_noise(s, 1.8, 73, lo=1)
    g = 0.86 + (fine - 0.5) * 0.07 + (mid - 0.5) * 0.04 + (big - 0.5) * 0.05
    rgb = tint(g, (1.0, 1.0, 1.0))
    rough = 0.92 + (fine - 0.5) * 0.06
    save_rgba("plaster", rgb, rough)
    save_normal("plaster", normal_from_height(fine * 0.8 + mid * 0.4, 1.3))


def stone():
    """Naturstein-Quader (Sandstein/Kalkstein). Kachel 2.4 m, 8 Lagen."""
    s = 512
    y, x = np.mgrid[0:s, 0:s].astype(np.float64)
    rows = 8
    rh = s / rows
    r = rng(81)
    row = np.floor(y / rh).astype(np.int64)
    # Zufaellige Fugen je Lage (kachelbar)
    cuts = []
    for k in range(rows):
        n = r.integers(3, 5)
        c = np.sort(r.random(n)) * s
        cuts.append(c)
    blk = np.zeros((s, s))
    edge_x = np.full((s, s), 1e9)
    for k in range(rows):
        m = row == k
        c = cuts[k]
        xs = x[m]
        idx = np.searchsorted(c, xs) % len(c)
        blk[m] = (idx + k * 17) % 97
        d = np.min(np.abs(xs[:, None] - np.concatenate([c, c - s, c + s])[None, :]), axis=1)
        edge_x[m] = d
    ly = y - row * rh
    edge_y = np.minimum(ly, rh - 1 - ly)
    edge = np.minimum(edge_x, edge_y)
    table = r.random(97)
    rid = table[blk.astype(np.int64)]
    fine = fft_noise(s, 0.5, 82, lo=30)
    mid = fft_noise(s, 1.3, 83, lo=3)
    joint = np.clip(1.0 - (edge - 1.0) / 1.5, 0, 1)
    bevel = np.clip(edge / 5.0, 0, 1)
    g = 0.80 + (rid - 0.5) * 0.10 + (fine - 0.5) * 0.08 + (mid - 0.5) * 0.06
    rgb = tint(g, (1.0, 0.95, 0.86))
    rgb = lerp(rgb, col(0.55, 0.52, 0.48), joint[..., None])
    rough = 0.84 + (fine - 0.5) * 0.1
    h = bevel * 0.8 + fine * 0.3 + mid * 0.2
    save_rgba("stone", rgb, rough)
    save_normal("stone", normal_from_height(h * 3.0, 1.5))


def wood():
    """Holzverschalung waagerecht, 10 Bretter je 2 m Kachel."""
    s = 512
    y, x = np.mgrid[0:s, 0:s].astype(np.float64)
    boards = 10
    bh = s / boards
    row = np.floor(y / bh)
    ly = y - row * bh
    r = rng(91)
    shade = r.random(boards)[row.astype(np.int64)]
    grain_src = fft_noise(s, 1.0, 92, lo=2)
    # gestreckte Maserung: in x stark verwischen
    streak = np.real(np.fft.ifft2(np.fft.fft2(fft_noise(s, 0.2, 93, lo=20)) *
                                  np.exp(-2 * math.pi ** 2 * (40 ** 2) * (np.fft.fftfreq(s)[None, :] ** 2))))
    streak = norm01(streak)
    gap = np.clip(1.0 - (ly - 0.5) / 2.0, 0, 1)
    overlap = np.clip(ly / bh, 0, 1)
    g = 0.55 + (shade - 0.5) * 0.12 + (streak - 0.5) * 0.25 + (grain_src - 0.5) * 0.05
    rgb = tint(g, (0.78, 0.56, 0.38))
    rgb *= (1.0 - gap * 0.6)[..., None]
    rough = 0.78 + (streak - 0.5) * 0.1
    h = overlap * 1.0 + streak * 0.3 - gap
    save_rgba("wood", rgb, rough)
    save_normal("wood", normal_from_height(h * 2.0, 1.5))


def metal():
    """Trapezblech mit senkrechten Rippen (Rippenabstand 0.2 m, Kachel 2 m)."""
    s = 512
    y, x = np.mgrid[0:s, 0:s].astype(np.float64)
    period = s / 10.0
    ph = np.mod(x, period) / period
    prof = np.clip(np.abs(ph - 0.5) * 4.0 - 0.6, 0, 1)  # Trapez
    streaks = norm01(np.real(np.fft.ifft2(np.fft.fft2(fft_noise(s, 0.5, 101, lo=10)) *
                                          np.exp(-2 * math.pi ** 2 * (30 ** 2) * (np.fft.fftfreq(s)[:, None] ** 2)))))
    mid = fft_noise(s, 1.4, 102, lo=2)
    g = 0.70 + (streaks - 0.5) * 0.12 + (mid - 0.5) * 0.06 - prof * 0.04
    rgb = tint(g, (1.0, 1.0, 1.0))
    rough = 0.42 + (streaks - 0.5) * 0.2 + (mid - 0.5) * 0.1
    h = prof * 1.0
    save_rgba("metal", rgb, rough)
    save_normal("metal", normal_from_height(h * 5.0, 1.0))


def roof_tiles():
    """Tondachziegel: 6 Reihen x 8 Ziegel je 2 m Kachel, versetzt."""
    s = 512
    y, x = np.mgrid[0:s, 0:s].astype(np.float64)
    rows, cols = 6, 8
    rh = s / rows
    cw = s / cols
    row = np.floor(y / rh)
    xo = np.mod(x + np.mod(row, 2) * cw * 0.5, s)
    ci = np.floor(xo / cw)
    lx = (xo - ci * cw) / cw
    ly = (y - row * rh) / rh
    r = rng(111)
    table = r.random(4096)
    rid = table[((row * 61 + ci * 13) % 4096).astype(np.int64)]
    # Wellenprofil quer, Schattenkante unten (Ueberlappung)
    wave = np.sin(lx * math.pi) ** 0.7
    lip = np.clip((ly - 0.82) / 0.18, 0, 1)
    fine = fft_noise(s, 0.6, 112, lo=30)
    g = 0.78 + (rid - 0.5) * 0.18 + (fine - 0.5) * 0.08
    g *= 0.75 + 0.25 * wave
    g *= 1.0 - lip * 0.35
    g *= 0.85 + 0.15 * ly
    rgb = tint(g, (1.0, 0.96, 0.92))
    rough = 0.72 + (fine - 0.5) * 0.1
    h = wave * 0.6 + ly * 0.8 - lip * 1.2 + fine * 0.1
    save_rgba("roof_tiles", rgb, rough)
    save_normal("roof_tiles", normal_from_height(h * 3.0, 1.6))


def roof_flat():
    """Flachdach: Kies / Bitumenbahn, Kachel 4 m."""
    s = 512
    grain = fft_noise(s, 0.0, 121, lo=120)
    mid = fft_noise(s, 1.2, 122, lo=3)
    big = fft_noise(s, 1.8, 123, lo=1)
    g = 0.55 + (grain - 0.5) * 0.25 + (mid - 0.5) * 0.08 + (big - 0.5) * 0.1
    rgb = tint(g, (1.0, 0.99, 0.97))
    rough = 0.93
    save_rgba("roof_flat", rgb, np.full((s, s), rough))
    save_normal("roof_flat", normal_from_height(grain, 1.2))


def grass():
    s = 1024
    big = fft_noise(s, 1.9, 131, lo=1)
    mid = fft_noise(s, 1.2, 132, lo=6)
    fine = fft_noise(s, 0.2, 133, lo=120)
    # Halmstruktur: gestrecktes Rauschen in zwei Richtungen
    blades = fft_noise(s, 0.0, 134, lo=200)
    dry = np.clip((fft_noise(s, 1.6, 135, lo=2) - 0.55) * 2.5, 0, 1)
    c_g = col(0.30, 0.46, 0.17)
    c_d = col(0.52, 0.54, 0.26)
    c_dark = col(0.17, 0.30, 0.10)
    t = (mid - 0.5) * 0.8 + (fine - 0.5) * 0.6 + (blades - 0.5) * 0.8
    rgb = lerp(c_g[None, None, :], c_d[None, None, :], dry[..., None] * 0.8)
    rgb = lerp(rgb, c_dark[None, None, :], np.clip(-t, 0, 1)[..., None] * 0.9)
    rgb *= (0.9 + 0.2 * big[..., None]) * (0.92 + 0.16 * blades[..., None])
    # wenige Kleeblueten
    flowers = np.clip((fft_noise(s, 0.0, 136, lo=300) - 0.93) * 20.0, 0, 1) * np.clip((big - 0.4) * 3, 0, 1)
    rgb = lerp(rgb, col(0.92, 0.92, 0.85)[None, None, :], flowers[..., None])
    rough = 0.95 - flowers * 0.2
    h = blades * 0.7 + fine * 0.4 + mid * 0.2
    save_rgba("grass", rgb, rough)
    save_normal("grass", normal_from_height(h, 2.0))


def gravel():
    s = 512
    pebbles = fft_noise(s, 0.6, 141, lo=50)
    mid = fft_noise(s, 1.4, 142, lo=3)
    cell = np.clip((pebbles - 0.45) * 3.0, 0, 1)
    g = 0.48 + (pebbles - 0.5) * 0.35 + (mid - 0.5) * 0.1
    rgb = tint(g, (0.93, 0.86, 0.74))
    rough = 0.94
    save_rgba("gravel", rgb, np.full((s, s), rough))
    save_normal("gravel", normal_from_height(cell * 1.0 + pebbles, 2.2))


def dirt():
    s = 512
    mid = fft_noise(s, 1.3, 151, lo=2)
    fine = fft_noise(s, 0.3, 152, lo=60)
    stones = np.clip((fft_noise(s, 0.0, 153, lo=100) - 0.82) * 6.0, 0, 1)
    g = 0.45 + (mid - 0.5) * 0.2 + (fine - 0.5) * 0.12 + stones * 0.15
    rgb = tint(g, (0.82, 0.66, 0.48))
    save_rgba("dirt", rgb, 0.95 - stones * 0.1)
    save_normal("dirt", normal_from_height(fine * 0.5 + mid * 0.5 + stones, 1.8))


def water():
    """Wellen-Normal-Map (zwei Schichten werden im Shader gegeneinander bewegt)."""
    s = 512
    a = fft_noise(s, 2.2, 161, lo=3, hi=40)
    b = fft_noise(s, 1.6, 162, lo=12, hi=90)
    h = a * 1.0 + b * 0.35
    save_normal("water", normal_from_height(h, 18.0))


def leaves():
    """Laubtextur fuer Baumkronen und Hecken: viele kleine Blaetter."""
    s = 512
    r = rng(171)
    img = Image.new("RGB", (s, s), (24, 44, 16))
    hmap = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(img)
    dh = ImageDraw.Draw(hmap)
    for i in range(5200):
        x = r.random() * s
        y = r.random() * s
        ln = r.uniform(7, 15)
        wd = ln * r.uniform(0.38, 0.55)
        ang = r.uniform(0, math.pi)
        shade = r.uniform(0.55, 1.15)
        hue = r.uniform(-0.06, 0.06)
        c = (int(np.clip((0.26 + hue) * shade * 255, 0, 255)), int(np.clip(0.44 * shade * 255, 0, 255)),
             int(np.clip((0.14 - hue * 0.5) * shade * 255, 0, 255)))
        pts = []
        for k in range(10):
            t = k / 10.0 * 2 * math.pi
            px = math.cos(t) * ln * 0.5
            py = math.sin(t) * wd * 0.5
            pts.append((px * math.cos(ang) - py * math.sin(ang), px * math.sin(ang) + py * math.cos(ang)))
        for ox in (-s, 0, s):
            for oy in (-s, 0, s):
                poly = [(x + px + ox, y + py + oy) for px, py in pts]
                d.polygon(poly, fill=c)
                dh.polygon(poly, fill=int(80 + 170 * (i / 5200.0)))
    rgb = np.asarray(img).astype(np.float64) / 255.0
    h = np.asarray(hmap.filter(ImageFilter.GaussianBlur(1.2))).astype(np.float64) / 255.0
    rough = 0.75 - (h - 0.5) * 0.1
    save_rgba("leaves", rgb, rough)
    save_normal("leaves", normal_from_height(h, 6.0))


def bark():
    s = 256
    y, x = np.mgrid[0:s, 0:s].astype(np.float64)
    n = fft_noise(s, 0.8, 181, lo=4)
    stretch = norm01(np.real(np.fft.ifft2(np.fft.fft2(fft_noise(s, 0.4, 182, lo=8)) *
                                          np.exp(-2 * math.pi ** 2 * (14 ** 2) * (np.fft.fftfreq(s)[:, None] ** 2)))))
    ridges = np.clip(1.0 - np.abs(stretch - 0.5) * 6.0, 0, 1)
    g = 0.42 + (n - 0.5) * 0.15 - ridges * 0.18
    rgb = tint(g, (0.62, 0.52, 0.42))
    save_rgba("bark", rgb, np.full((s, s), 0.92))
    save_normal("bark", normal_from_height(stretch * 2.0 - ridges, 3.0))


def fabric():
    """Markise / Zeltstoff: feines Gewebe, weiss (wird eingefaerbt)."""
    s = 256
    y, x = np.mgrid[0:s, 0:s].astype(np.float64)
    weave = 0.5 + 0.25 * np.sin(x * math.pi / 2.0) * np.sin(y * math.pi / 2.0)
    n = fft_noise(s, 1.0, 191, lo=3)
    g = 0.88 + (weave - 0.5) * 0.08 + (n - 0.5) * 0.08
    save_rgba("fabric", tint(g, (1, 1, 1)), np.full((s, s), 0.9))
    save_normal("fabric", normal_from_height(weave, 0.8))


def noise_pack():
    """Vier unabhaengige Rauschkanaele fuer Shader (Schmutz, Variation, Wellen)."""
    s = 256
    a = fft_noise(s, 1.8, 201, lo=1)
    b = fft_noise(s, 1.2, 202, lo=3)
    c = fft_noise(s, 0.6, 203, lo=12)
    d = fft_noise(s, 0.1, 204, lo=40)
    img = np.dstack([a, b, c, d])
    img8 = (img * 255.0 + 0.5).astype(np.uint8)
    path = os.path.join(OUT, "noise.png")
    Image.fromarray(img8, "RGBA").save(path, optimize=True)
    write_import(path, normal=False)


# ------------------------------------------------------------------ Atlanten

def font(size, weight=b"Bold"):
    f = ImageFont.truetype(FONT_BOLD, size)
    try:
        f.set_variation_by_name(weight)
    except Exception:
        pass
    return f


def signs_atlas():
    """Verkehrszeichen 4 x 2 Zellen a 256 px (1024 x 512).

    0 Stop, 1 Vorfahrt gewaehren, 2 Kreisverkehr, 3 Bushaltestelle,
    4 Krankenhaus (blaues H), 5 Parken, 6 Vorfahrtstrasse, 7 Rueckseite (grau).
    """
    W, H, C = 1024, 512, 256
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    white = (245, 245, 245, 255)
    red = (200, 28, 36, 255)
    blue = (20, 82, 170, 255)
    yellow = (250, 196, 20, 255)
    green = (20, 120, 70, 255)

    def cell(i):
        return (i % 4) * C, (i // 4) * C

    # 0 Stop
    x, y = cell(0)
    cx, cy, r = x + 128, y + 128, 120
    oct_pts = [(cx + r * math.cos(math.pi / 8 + k * math.pi / 4), cy + r * math.sin(math.pi / 8 + k * math.pi / 4)) for k in range(8)]
    d.polygon(oct_pts, fill=white)
    r2 = 108
    oct2 = [(cx + r2 * math.cos(math.pi / 8 + k * math.pi / 4), cy + r2 * math.sin(math.pi / 8 + k * math.pi / 4)) for k in range(8)]
    d.polygon(oct2, fill=red)
    f = font(66)
    d.text((cx, cy + 2), "STOP", font=f, fill=white, anchor="mm")
    # 1 Vorfahrt gewaehren (Dreieck Spitze unten)
    x, y = cell(1)
    tri = [(x + 8, y + 22), (x + 248, y + 22), (x + 128, y + 236)]
    d.polygon(tri, fill=red)
    tri2 = [(x + 40, y + 40), (x + 216, y + 40), (x + 128, y + 192)]
    d.polygon(tri2, fill=white)
    # 2 Kreisverkehr (blauer Kreis mit drei Pfeilen)
    x, y = cell(2)
    cx, cy = x + 128, y + 128
    d.ellipse([cx - 118, cy - 118, cx + 118, cy + 118], fill=white)
    d.ellipse([cx - 110, cy - 110, cx + 110, cy + 110], fill=blue)
    for k in range(3):
        a0 = math.radians(k * 120 - 90)
        a1 = a0 + math.radians(80)
        d.arc([cx - 62, cy - 62, cx + 62, cy + 62], math.degrees(a0), math.degrees(a1), fill=white, width=20)
        ax, ay = cx + 62 * math.cos(a1), cy + 62 * math.sin(a1)
        tx, ty = -math.sin(a1), math.cos(a1)
        nx, ny = math.cos(a1), math.sin(a1)
        head = [(ax + tx * 30, ay + ty * 30), (ax + nx * 24, ay + ny * 24), (ax - nx * 24, ay - ny * 24)]
        d.polygon(head, fill=white)
    # 3 Bushaltestelle (gelber Kreis, gruenes H)
    x, y = cell(3)
    cx, cy = x + 128, y + 128
    d.ellipse([cx - 120, cy - 120, cx + 120, cy + 120], fill=green)
    d.ellipse([cx - 108, cy - 108, cx + 108, cy + 108], fill=yellow)
    d.text((cx, cy + 6), "H", font=font(150), fill=green, anchor="mm")
    # 4 Krankenhaus: blaues Quadrat, weisses H
    x, y = cell(4)
    d.rounded_rectangle([x + 8, y + 8, x + 248, y + 248], radius=22, fill=blue)
    d.text((x + 128, y + 136), "H", font=font(190), fill=white, anchor="mm")
    # 5 Parken
    x, y = cell(5)
    d.rounded_rectangle([x + 8, y + 8, x + 248, y + 248], radius=22, fill=white)
    d.rounded_rectangle([x + 16, y + 16, x + 240, y + 240], radius=18, fill=blue)
    d.text((x + 128, y + 136), "P", font=font(190), fill=white, anchor="mm")
    # 6 Vorfahrtstrasse (gelbe Raute)
    x, y = cell(6)
    cx, cy = x + 128, y + 128
    d.polygon([(cx, cy - 122), (cx + 122, cy), (cx, cy + 122), (cx - 122, cy)], fill=white)
    d.polygon([(cx, cy - 104), (cx + 104, cy), (cx, cy + 104), (cx - 104, cy)], fill=(30, 30, 30, 255))
    d.polygon([(cx, cy - 92), (cx + 92, cy), (cx, cy + 92), (cx - 92, cy)], fill=white)
    d.polygon([(cx, cy - 68), (cx + 68, cy), (cx, cy + 68), (cx - 68, cy)], fill=yellow)
    # 7 Rueckseite
    x, y = cell(7)
    d.rectangle([x, y, x + C - 1, y + C - 1], fill=(150, 152, 155, 255))
    save_image("signs_atlas", img)


def interior_atlas():
    """Rueckwaende fuer die Innenraum-Illusion hinter Fenstern.

    4 x 2 Zellen a 256 px: 0 Wohnzimmer, 1 Schlafzimmer, 2 Kueche,
    3 Buero hell, 4 Buero Grossraum, 5 Ladenregale, 6 Restaurant, 7 Bibliothek.
    Die Bilder sind bewusst gedaempft; nachts werden sie im Shader beleuchtet.
    """
    W, H, C = 1024, 512, 256
    img = Image.new("RGB", (W, H), (0, 0, 0))
    d = ImageDraw.Draw(img)
    r = rng(301)

    def cell(i):
        return (i % 4) * C, (i // 4) * C

    def rect(x, y, x0, y0, x1, y1, c):
        d.rectangle([x + x0, y + y0, x + x1, y + y1], fill=c)

    # 0 Wohnzimmer: warme Tapete, Bild, Sofa, Stehlampe
    x, y = cell(0)
    rect(x, y, 0, 0, 255, 255, (196, 170, 138))
    for k in range(0, 256, 16):
        rect(x, y, k, 0, k + 2, 190, (186, 160, 128))
    rect(x, y, 0, 190, 255, 255, (120, 84, 56))
    rect(x, y, 90, 50, 170, 105, (60, 70, 90))
    rect(x, y, 96, 56, 164, 99, (150, 120, 90))
    rect(x, y, 40, 140, 220, 200, (70, 90, 110))
    rect(x, y, 40, 125, 220, 150, (85, 105, 128))
    rect(x, y, 228, 60, 236, 200, (60, 50, 40))
    rect(x, y, 214, 48, 250, 70, (250, 230, 180))
    # 1 Schlafzimmer: blaue Wand, Bett, Vorhang links
    x, y = cell(1)
    rect(x, y, 0, 0, 255, 255, (150, 168, 186))
    rect(x, y, 0, 196, 255, 255, (150, 120, 92))
    rect(x, y, 60, 120, 200, 140, (110, 80, 60))
    rect(x, y, 56, 140, 204, 200, (230, 228, 220))
    rect(x, y, 70, 132, 120, 150, (240, 240, 236))
    rect(x, y, 0, 0, 40, 255, (180, 70, 70))
    rect(x, y, 216, 120, 246, 196, (120, 90, 70))
    # 2 Kueche: Oberschraenke, Arbeitsplatte, Fliesen
    x, y = cell(2)
    rect(x, y, 0, 0, 255, 255, (228, 226, 214))
    for k in range(0, 256, 22):
        rect(x, y, k, 100, k + 1, 150, (200, 198, 188))
    rect(x, y, 0, 40, 255, 96, (90, 120, 110))
    for k in range(0, 256, 52):
        rect(x, y, k, 40, k + 2, 96, (60, 80, 74))
    rect(x, y, 0, 150, 255, 160, (60, 56, 52))
    rect(x, y, 0, 160, 255, 255, (230, 230, 226))
    for k in range(0, 256, 52):
        rect(x, y, k, 160, k + 2, 255, (190, 190, 186))
    # 3 Buero hell: Weisse Wand, Regale, Deckenleuchte
    x, y = cell(3)
    rect(x, y, 0, 0, 255, 255, (222, 224, 226))
    rect(x, y, 0, 0, 255, 18, (240, 240, 240))
    rect(x, y, 40, 8, 216, 14, (255, 255, 250))
    rect(x, y, 20, 70, 110, 200, (140, 120, 100))
    for k in range(80, 200, 30):
        rect(x, y, 22, k, 108, k + 4, (90, 76, 62))
        for j in range(24, 104, 9):
            rect(x, y, j, k - 22, j + 6, k, tuple(int(c) for c in r.integers(60, 200, 3)))
    rect(x, y, 130, 150, 250, 158, (200, 200, 205))
    rect(x, y, 160, 110, 200, 148, (30, 34, 40))
    rect(x, y, 0, 210, 255, 255, (120, 124, 130))
    # 4 Grossraumbuero: Trennwaende, Monitore
    x, y = cell(4)
    rect(x, y, 0, 0, 255, 255, (200, 204, 210))
    for k in range(10, 256, 60):
        rect(x, y, k, 6, k + 40, 12, (255, 255, 250))
    for k in range(0, 256, 64):
        rect(x, y, k + 6, 140, k + 58, 190, (110, 120, 132))
        rect(x, y, k + 20, 116, k + 44, 138, (25, 30, 36))
        rect(x, y, k + 22, 118, k + 42, 134, (70, 110, 150))
    rect(x, y, 0, 205, 255, 255, (90, 96, 104))
    # 5 Ladenregale: bunte Waren
    x, y = cell(5)
    rect(x, y, 0, 0, 255, 255, (236, 236, 232))
    for sx in range(0, 256, 86):
        rect(x, y, sx + 4, 30, sx + 82, 230, (200, 200, 198))
        for k in range(52, 230, 36):
            rect(x, y, sx + 4, k, sx + 82, k + 4, (150, 150, 150))
            j = sx + 8
            while j < sx + 76:
                w = int(r.integers(6, 14))
                h = int(r.integers(14, 30))
                c = tuple(int(v) for v in r.integers(40, 240, 3))
                rect(x, y, j, k - h, j + w, k - 1, c)
                j += w + 2
    rect(x, y, 0, 232, 255, 255, (180, 176, 168))
    # 6 Restaurant: dunkles Holz, warme Lampen, Bilder
    x, y = cell(6)
    rect(x, y, 0, 0, 255, 255, (120, 70, 50))
    rect(x, y, 0, 120, 255, 255, (80, 50, 34))
    for k in range(20, 256, 70):
        rect(x, y, k, 40, k + 40, 90, (210, 180, 120))
        rect(x, y, k + 18, 0, k + 22, 30, (40, 30, 20))
        d.ellipse([x + k + 8, y + 26, x + k + 32, y + 42], fill=(255, 210, 140))
    rect(x, y, 0, 190, 255, 200, (60, 36, 24))
    # 7 Bibliothek / Arbeitszimmer
    x, y = cell(7)
    rect(x, y, 0, 0, 255, 255, (150, 120, 92))
    for k in range(30, 230, 34):
        rect(x, y, 10, k, 245, k + 4, (90, 62, 40))
        j = 12
        while j < 240:
            w = int(r.integers(5, 10))
            c = tuple(int(v) for v in r.integers(50, 190, 3))
            rect(x, y, j, k - int(r.integers(18, 28)), j + w, k - 1, c)
            j += w + 1
    save_image("interior_atlas", img)


def main():
    os.makedirs(OUT, exist_ok=True)
    for fn in (asphalt, pavers, plaza, curb, concrete, brick, plaster, stone, wood, metal,
               roof_tiles, roof_flat, grass, gravel, dirt, water, leaves, bark, fabric,
               noise_pack, signs_atlas, interior_atlas):
        fn()
        print("ok", fn.__name__)
    total = 0
    for f in os.listdir(OUT):
        if f.endswith(".png"):
            total += os.path.getsize(os.path.join(OUT, f))
    print("Gesamtgroesse: %.1f MB" % (total / 1e6))


if __name__ == "__main__":
    main()
