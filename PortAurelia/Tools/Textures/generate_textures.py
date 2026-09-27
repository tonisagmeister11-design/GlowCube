"""Procedural, tileable PBR textures for Port Aurelia.

Pure numpy + zlib (no Pillow) so it also runs inside Blender's Python.
Outputs into Game/assets/textures/:
    <name>_col.png  RGBA  (RGB albedo, A roughness)
    <name>_nrm.png  RGB   (OpenGL-style normal map)
Alpha-tested foliage textures carry opacity in A instead of roughness.

Usage:  python generate_textures.py [output_dir]
"""
import os
import struct
import sys
import zlib

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "Game", "assets", "textures"))
RNG = np.random.default_rng(424242)


# ---------------------------------------------------------------- PNG writer
def save_png(path, arr):
    """arr: HxW (gray), HxWx3 or HxWx4 float [0,1] or uint8."""
    a = np.asarray(arr)
    if a.dtype != np.uint8:
        a = (np.clip(a, 0.0, 1.0) * 255.0 + 0.5).astype(np.uint8)
    if a.ndim == 2:
        a = a[:, :, None]
    h, w, c = a.shape
    color_type = {1: 0, 2: 4, 3: 2, 4: 6}[c]
    raw = b"".join(b"\x00" + a[y].tobytes() for y in range(h))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, color_type, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


# ---------------------------------------------------------------- noise
def periodic_noise(size, period, seed):
    """Tileable value noise (smooth), shape (size, size) in [0,1]."""
    rng = np.random.default_rng(seed)
    lat = rng.random((period, period))
    coords = np.arange(size) * period / size
    i0 = np.floor(coords).astype(int)
    f = coords - i0
    f = f * f * (3 - 2 * f)
    i1 = (i0 + 1) % period
    a = lat[np.ix_(i0, i0)]
    b = lat[np.ix_(i0, i1)]
    c = lat[np.ix_(i1, i0)]
    d = lat[np.ix_(i1, i1)]
    fy = f[:, None]
    fx = f[None, :]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(size, base_period, octaves, seed, gain=0.5):
    s = np.zeros((size, size))
    amp = 1.0
    tot = 0.0
    p = base_period
    for o in range(octaves):
        s += periodic_noise(size, p, seed + o * 31) * amp
        tot += amp
        amp *= gain
        p *= 2
        if p > size:
            break
    return s / tot


def white(size, seed):
    return np.random.default_rng(seed).random((size, size))


def normal_from_height(h, strength):
    dx = (np.roll(h, -1, axis=1) - np.roll(h, 1, axis=1)) * strength
    dy = (np.roll(h, -1, axis=0) - np.roll(h, 1, axis=0)) * strength
    n = np.stack([-dx, dy, np.ones_like(h)], axis=-1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    return n * 0.5 + 0.5


def colorize(t, c0, c1):
    t = t[..., None]
    return np.array(c0) * (1 - t) + np.array(c1) * t


def rgba(col, rough):
    rough = np.broadcast_to(np.asarray(rough, dtype=np.float64), col.shape[:2])
    return np.concatenate([np.clip(col, 0, 1), np.clip(rough, 0, 1)[..., None]], axis=-1)


def blur(a, r=1):
    out = a.copy()
    for _ in range(r):
        out = (out + np.roll(out, 1, 0) + np.roll(out, -1, 0) + np.roll(out, 1, 1) + np.roll(out, -1, 1)) / 5.0
    return out


def save_pbr(name, col, rough, height, strength):
    save_png(os.path.join(OUT, f"{name}_col.png"), rgba(col, rough))
    save_png(os.path.join(OUT, f"{name}_nrm.png"), normal_from_height(height, strength))
    print("  ", name)


# ---------------------------------------------------------------- materials
def cracks(S, period, seed, width=0.012):
    """Thin branching crack lines from ridged noise (1 on the crack)."""
    r = 1.0 - np.abs(fbm(S, period, 5, seed) * 2.0 - 1.0)
    return np.clip((r - (1.0 - width)) / width, 0.0, 1.0)


def asphalt(S=1024):
    """Worn road asphalt: bitumen with mixed-tone aggregate, polished stones, fine cracks,
    darker tar sealing lines, patches and oil stains."""
    n1 = fbm(S, 8, 6, 1)
    grain = white(S, 2)
    # aggregate: stones of three sizes and tones (some bright quartz, some dark basalt)
    big = blur((white(S, 4) > 0.955).astype(float), 2)
    mid = blur((white(S, 5) > 0.93).astype(float), 1)
    tone = white(S, 6)
    stones = np.clip(big * 1.6 + mid, 0, 1)
    speck = (white(S, 3) > 0.988).astype(float)
    crack = cracks(S, 6, 7, 0.012) * np.clip((fbm(S, 2, 3, 8) - 0.5) * 3, 0, 1)
    seal = cracks(S, 3, 9, 0.02) * np.clip((fbm(S, 2, 3, 10) - 0.6) * 3, 0, 1)
    patch = np.clip((fbm(S, 2, 4, 11) - 0.66) * 5, 0, 1)
    oil = np.clip((fbm(S, 4, 4, 12) - 0.66) * 6, 0, 1)
    h = n1 * 0.25 + grain * 0.2 + stones * 0.55 - crack * 0.7 + seal * 0.15 - patch * 0.1
    base = 0.16 + n1 * 0.05 + grain * 0.045 + speck * 0.16
    base = base + stones * (tone - 0.45) * 0.14
    stain = fbm(S, 4, 4, 5)
    base = base * (0.86 + 0.28 * stain)
    base = base * (1 - crack * 0.35) * (1 - seal * 0.25) * (1 - patch * 0.12) * (1 - oil * 0.25)
    col = np.stack([base, base * 0.995, base * 1.03], -1)
    rough = 0.84 + grain * 0.1 - stain * 0.08 - stones * 0.12 - seal * 0.25 - oil * 0.3
    save_pbr("asphalt", col, rough, h, 3.4)

def concrete(S=1024):
    """Cast concrete: fine aggregate, air pores, cloudy tone variation and water stains."""
    n = fbm(S, 4, 7, 11)
    g = white(S, 12)
    agg = blur((white(S, 14) > 0.96).astype(float), 1)
    pores = (white(S, 13) > 0.992).astype(float)
    y = np.mgrid[0:S, 0:S][0] / S
    streak = blur(np.repeat(fbm(S, 16, 3, 15)[:1, :], S, 0), 1) * (0.6 + 0.4 * fbm(S, 2, 3, 16))
    blot = fbm(S, 2, 4, 17)
    h = n * 0.45 + g * 0.1 + agg * 0.15 - pores * 0.5
    v = 0.58 + (n - 0.5) * 0.16 + g * 0.035 + (agg - 0.2) * 0.04 - pores * 0.2 + (blot - 0.5) * 0.08
    v = v - np.clip(streak - 0.55, 0, 1) * 0.08
    col = np.stack([v, v * 0.99, v * 0.955], -1)
    rough = 0.84 + g * 0.1 - agg * 0.05
    save_pbr("concrete", col, rough, h, 2.2)

def pavers(S=1024, tiles=8):
    """Sidewalk slabs: tiles x tiles squares per texture (1 texture = 4 m -> 50 cm slabs).
    Bevelled edges, per-slab tone, dirt in the joints, a few cracked or stained slabs."""
    y, x = np.mgrid[0:S, 0:S] / S * tiles
    ix, iy = np.floor(x), np.floor(y)
    fx = x - ix
    fy = y - iy
    gap = 0.03
    edge = np.minimum(np.minimum(fx, 1 - fx), np.minimum(fy, 1 - fy))
    joint = np.clip(edge / gap, 0, 1)
    bevel = np.clip(edge / (gap * 2.6), 0, 1) ** 0.6
    tile_id = (ix * 7 + iy * 13).astype(int)
    rnd = lambda k: (np.sin(tile_id * 12.9898 + k * 78.233) * 43758.5453) % 1.0  # noqa: E731
    tint = rnd(1)
    warm = rnd(2)
    cracked = rnd(3) > 0.95
    stained = rnd(4) > 0.8
    n = fbm(S, 8, 6, 21)
    g = white(S, 22)
    agg = blur(blur((white(S, 23) > 0.9).astype(float), 1), 1)
    crk = cracks(S, 4, 24, 0.012) * cracked
    stain = np.clip((fbm(S, 8, 4, 25) - 0.5) * 3, 0, 1) * stained
    gum = blur((white(S, 26) > 0.99997).astype(float), 2) > 0.05
    h = bevel * 0.55 + n * 0.2 + g * 0.05 + agg * 0.08 - crk * 0.4
    v = (0.6 + tint * 0.1 + (n - 0.5) * 0.09 + (agg - 0.2) * 0.02) * (0.45 + 0.55 * joint)
    v = v * (1 - crk * 0.22) * (1 - stain * 0.22) * (1 - gum * 0.35)
    col = np.stack([v * (1.0 + warm * 0.03), v * 0.97, v * (0.94 - warm * 0.03)], -1)
    rough = 0.76 + g * 0.1 + (1 - joint) * 0.12 - stain * 0.1
    save_pbr("pavers", col, rough, h, 4.0)

def grass(S=1024):
    """Lawn: blade clumps in several greens, clover patches, dry spots and bare soil."""
    n = fbm(S, 4, 6, 31)
    n2 = fbm(S, 16, 5, 32)
    g = white(S, 33)
    blades = blur(g, 1)
    fine = white(S, 35)
    clumps = fbm(S, 32, 3, 36)
    clover = np.clip((fbm(S, 8, 4, 37) - 0.62) * 6, 0, 1) * blur((white(S, 38) > 0.6).astype(float), 2)
    soil = np.clip((fbm(S, 4, 5, 39) - 0.72) * 7, 0, 1)
    h = blades * 0.45 + n2 * 0.35 + clumps * 0.25 - soil * 0.3 + clover * 0.15
    col = colorize(n, (0.2, 0.33, 0.09), (0.35, 0.45, 0.15))
    col = col * (0.6 + 0.65 * blades[..., None]) * (0.85 + 0.3 * clumps[..., None])
    col = col + (fine[..., None] > 0.985) * np.array([0.12, 0.14, 0.04])       # sunlit blade tips
    col = col * (1 - clover[..., None] * 0.3) + np.array([0.16, 0.34, 0.12]) * clover[..., None] * 0.3
    dry = np.clip((fbm(S, 2, 3, 34) - 0.55) * 3, 0, 1)[..., None]
    col = col * (1 - dry * 0.55) + np.array([0.48, 0.43, 0.24]) * dry * 0.55
    col = col * (1 - soil[..., None] * 0.7) + np.array([0.3, 0.24, 0.16]) * soil[..., None] * 0.7
    rough = 0.9 - g * 0.06 + soil * 0.05
    save_pbr("grass", col, rough, h, 3.2)

def sand(S=1024):
    n = fbm(S, 4, 7, 41)
    g = white(S, 42)
    y, x = np.mgrid[0:S, 0:S] / S
    ripple = np.sin((y * 18 + n * 3.0) * 2 * np.pi) * 0.5 + 0.5
    h = ripple * 0.35 + g * 0.2 + n * 0.3
    col = colorize(n * 0.7 + g * 0.3, (0.80, 0.70, 0.52), (0.92, 0.84, 0.66))
    rough = 0.9 + g * 0.08
    save_pbr("sand", col, rough, h, 2.0)


def dirt(S=1024):
    n = fbm(S, 4, 7, 51)
    g = white(S, 52)
    peb = blur((white(S, 53) > 0.97).astype(float), 1)
    h = n * 0.4 + peb * 0.6 + g * 0.1
    col = colorize(n, (0.36, 0.28, 0.19), (0.55, 0.45, 0.32)) * (0.9 + 0.2 * peb[..., None])
    rough = 0.93
    save_pbr("dirt", col, np.full((S, S), rough) - g * 0.05, h, 3.0)


def rock(S=1024):
    n = fbm(S, 4, 8, 61)
    ridges = 1 - np.abs(fbm(S, 8, 5, 62) * 2 - 1)
    h = n * 0.5 + ridges ** 3 * 0.6
    col = colorize(n, (0.38, 0.36, 0.33), (0.62, 0.58, 0.52)) * (0.8 + 0.3 * ridges[..., None])
    rough = 0.88 - ridges * 0.1
    save_pbr("rock", col, rough, h, 4.0)


def brick(S=1024, rows=16, cols=8):
    """Running bond brick: 1 texture = 2 m wide x 1 m high at default scale. Every brick has its
    own tone and texture, rounded arrises, recessed sandy mortar, some dark clinkers and a
    little white efflorescence."""
    y, x = np.mgrid[0:S, 0:S] / S
    ry = y * rows
    row = np.floor(ry)
    rx = x * cols + (row % 2) * 0.5
    fx = rx - np.floor(rx)
    fy = ry - row
    mortar = 0.055
    e = np.minimum(np.minimum(fx, 1 - fx) * 2.2, np.minimum(fy, 1 - fy))
    jm = np.clip(e / mortar, 0, 1)
    round_ = np.clip(e / (mortar * 2.2), 0, 1) ** 0.5
    bid = (np.floor(rx) * 17 + row * 31).astype(int)
    rnd = lambda k: (np.sin(bid * 78.233 + k * 12.9898) * 43758.5453) % 1.0  # noqa: E731
    tint = rnd(1)
    clinker = (rnd(2) > 0.93)[..., None]
    n = fbm(S, 16, 5, 71)
    g = white(S, 72)
    pits = blur((white(S, 73) > 0.985).astype(float), 1)
    eff = np.clip((fbm(S, 4, 4, 74) - 0.62) * 4, 0, 1)
    msand = white(S, 75)
    h = round_ * 0.65 + n * 0.18 + g * 0.07 - pits * 0.2 + (1 - jm) * msand * 0.08
    base = colorize(tint, (0.48, 0.2, 0.13), (0.72, 0.38, 0.25)) * (0.82 + 0.28 * n[..., None])
    base = base * (1 - pits[..., None] * 0.25)
    base = base * (1 - clinker * 0.45) + clinker * np.array([0.16, 0.1, 0.08]) * 0.45
    mort = np.array([0.7, 0.68, 0.63])[None, None, :] * (0.85 + 0.25 * msand[..., None])
    col = base * jm[..., None] + mort * (1 - jm[..., None])
    col = col * (1 - eff[..., None] * 0.25) + np.array([0.85, 0.84, 0.8]) * eff[..., None] * 0.25
    rough = 0.84 + g * 0.1 + (1 - jm) * 0.04
    save_pbr("brick", col, rough, h, 5.0)

def plaster(S=1024):
    """Render / stucco: sandy grain, soft trowel swirls, water streaks running down from the top."""
    n = fbm(S, 8, 7, 81)
    g = white(S, 82)
    sand_ = blur(white(S, 84), 1)
    swirl = fbm(S, 16, 3, 85)
    streak = blur(np.repeat(fbm(S, 32, 3, 86)[:1, :], S, 0), 1) * fbm(S, 2, 3, 87)
    h = n * 0.35 + sand_ * 0.35 + swirl * 0.2
    v = 0.86 + (n - 0.5) * 0.07 + (sand_ - 0.5) * 0.05 + (swirl - 0.5) * 0.03
    grime = np.clip((fbm(S, 2, 4, 83) - 0.5) * 2, 0, 1)
    v = v - grime * 0.08 - np.clip(streak - 0.4, 0, 1) * 0.04
    col = np.stack([v, v * 0.995, v * 0.985], -1)
    save_pbr("plaster", col, 0.9 - g * 0.05, h, 1.8)

def metal_siding(S=1024, ribs=16):
    y, x = np.mgrid[0:S, 0:S] / S
    rib = np.abs(np.sin(x * ribs * np.pi)) ** 0.6
    streak = fbm(S, 8, 5, 91)
    streak_v = blur(np.repeat(white(S, 92)[:1, :], S, 0), 2)
    h = rib * 0.8 + streak * 0.1
    v = 0.72 + (rib - 0.5) * 0.12 - streak_v * 0.08 - (streak - 0.5) * 0.06
    col = np.stack([v, v, v], -1)
    rough = 0.45 + streak * 0.3
    save_pbr("metal_siding", col, rough, h, 6.0)


def wood_siding(S=1024, boards=10):
    y, x = np.mgrid[0:S, 0:S] / S
    by = y * boards
    fy = by - np.floor(by)
    groove = np.clip(fy / 0.08, 0, 1) * np.clip((1 - fy) / 0.02, 0, 1)
    grain = fbm(S, 4, 5, 101)
    fib = blur(np.repeat(white(S, 102)[:, :1], S, 1).T, 1)
    h = groove * 0.8 + grain * 0.1 + fy * 0.2
    v = 0.84 + (grain - 0.5) * 0.08 - (1 - groove) * 0.25
    col = np.stack([v, v, v], -1)
    save_pbr("wood_siding", col, 0.8 + fib * 0.1, h, 5.0)


def wood_planks(S=1024, boards=8):
    y, x = np.mgrid[0:S, 0:S] / S
    bx = x * boards
    b = np.floor(bx)
    fx = bx - b
    gap = np.clip(np.minimum(fx, 1 - fx) / 0.04, 0, 1)
    tint = ((np.sin(b * 43.1) * 43758.5) % 1.0)[..., None] if False else ((np.sin(b * 43.1) * 43758.5) % 1.0)
    grain = fbm(S, 8, 6, 111)
    stretched = periodic_noise(S, 64, 112)
    h = gap * 0.7 + grain * 0.2
    col = colorize(tint * 0.6 + grain * 0.4, (0.42, 0.30, 0.20), (0.62, 0.48, 0.33)) * gap[..., None]
    col += (1 - gap[..., None]) * 0.08
    save_pbr("wood_planks", col, 0.75 + stretched * 0.15, h, 4.0)


def roof_tiles(S=1024, rows=10, cols=10):
    y, x = np.mgrid[0:S, 0:S] / S
    ry = y * rows
    row = np.floor(ry)
    fy = ry - row
    rx = x * cols + (row % 2) * 0.5
    fx = rx - np.floor(rx)
    curve = np.sin(fx * np.pi) ** 0.5
    h = curve * 0.6 + fy * 0.5
    tid = (np.floor(rx) * 5 + row * 11).astype(int)
    tint = (np.sin(tid * 91.7) * 43758.5) % 1.0
    n = fbm(S, 8, 5, 121)
    col = colorize(tint * 0.5 + n * 0.5, (0.55, 0.26, 0.16), (0.72, 0.40, 0.26)) * (0.7 + 0.3 * curve[..., None]) * (0.75 + 0.25 * fy[..., None])
    save_pbr("roof_tiles", col, 0.7 + n * 0.15, h, 5.0)


def roof_membrane(S=1024):
    n = fbm(S, 4, 7, 131)
    g = white(S, 132)
    y, x = np.mgrid[0:S, 0:S] / S
    seams = np.clip(np.abs(((x * 4) % 1.0) - 0.5) * 60 - 28, 0, 1)
    h = n * 0.4 + g * 0.3 + (1 - seams) * 0.3
    v = 0.48 + (n - 0.5) * 0.12 + g * 0.06 - (1 - seams) * 0.05
    col = np.stack([v, v * 0.99, v * 0.97], -1)
    save_pbr("roof_membrane", col, 0.9 - g * 0.05, h, 3.0)


def shingles(S=1024, rows=14, cols=9):
    y, x = np.mgrid[0:S, 0:S] / S
    ry = y * rows
    row = np.floor(ry)
    fy = ry - row
    rx = x * cols + (row % 2) * 0.5
    fx = rx - np.floor(rx)
    e = np.clip(np.minimum(fx, 1 - fx) / 0.03, 0, 1)
    h = fy * 0.6 + e * 0.3
    g = white(S, 141)
    n = fbm(S, 16, 5, 142)
    v = (0.3 + n * 0.12 + g * 0.06) * (0.7 + 0.3 * fy) * (0.8 + 0.2 * e)
    col = np.stack([v, v, v * 1.02], -1)
    save_pbr("shingles", col, 0.88 + g * 0.08, h, 4.0)


def tire(S=512):
    y, x = np.mgrid[0:S, 0:S] / S
    tread = (np.abs(((x * 12 + (np.abs(y - 0.5) * 6)) % 1.0) - 0.5) > 0.2).astype(float)
    groove = (np.abs(y - 0.5) < 0.04).astype(float)
    h = tread * 0.7 * (1 - groove)
    v = 0.07 + tread * 0.03
    col = np.stack([v, v, v], -1)
    save_pbr("tire", col, 0.85 - tread * 0.1, h, 5.0)


def water_normal(S=1024):
    h = fbm(S, 8, 7, 151, 0.55)
    h2 = fbm(S, 16, 6, 152, 0.5)
    save_png(os.path.join(OUT, "water_nrm.png"), normal_from_height(h * 0.6 + h2 * 0.4, 6.0))
    save_png(os.path.join(OUT, "water_nrm2.png"), normal_from_height(fbm(S, 4, 7, 153, 0.55), 5.0))
    print("   water")


def cloud_noise(S=512):
    """RGBA fbm at different scales for the procedural sky."""
    r = fbm(S, 4, 6, 161, 0.55)
    g = fbm(S, 8, 6, 162, 0.5)
    b = 1 - np.abs(fbm(S, 4, 6, 163) * 2 - 1)
    a = fbm(S, 16, 5, 164)
    save_png(os.path.join(OUT, "cloud_noise.png"), np.stack([r, g, b, a], -1))
    print("   cloud_noise")


def detail_noise(S=512):
    save_png(os.path.join(OUT, "detail_noise.png"),
             np.stack([fbm(S, 8, 6, 171), fbm(S, 32, 4, 172), white(S, 173), fbm(S, 2, 5, 174)], -1))
    print("   detail_noise")


# ---------------------------------------------------------------- foliage
def _stamp_ellipse(img, cx, cy, rx, ry, ang, val):
    S = img.shape[0]
    r = int(max(rx, ry)) + 2
    x0, x1 = max(0, int(cx - r)), min(S, int(cx + r))
    y0, y1 = max(0, int(cy - r)), min(S, int(cy + r))
    if x1 <= x0 or y1 <= y0:
        return
    yy, xx = np.mgrid[y0:y1, x0:x1]
    dx = xx - cx
    dy = yy - cy
    c, s = np.cos(ang), np.sin(ang)
    u = (dx * c + dy * s) / rx
    v = (-dx * s + dy * c) / ry
    m = (u * u + v * v) <= 1.0
    img[y0:y1, x0:x1][m] = val


def palm_frond(S=1024):
    """One palm frond, rib along the texture's vertical centre (base at bottom)."""
    alpha = np.zeros((S, S))
    shade = np.zeros((S, S))
    rng = np.random.default_rng(181)
    cx = S * 0.5
    # rib
    for y in range(int(S * 0.02), int(S * 0.98)):
        w = 5 * (1 - y / S) + 2
        alpha[y, int(cx - w):int(cx + w)] = 1
        shade[y, int(cx - w):int(cx + w)] = 0.6
    # leaflets
    n = 46
    for i in range(n):
        t = i / n
        y = S * (0.97 - t * 0.93)
        length = S * 0.46 * np.sin(np.pi * (0.15 + 0.85 * t)) ** 0.7
        for side in (-1, 1):
            ang = side * (0.95 - 0.35 * t) + rng.uniform(-0.08, 0.08)
            steps = 30
            for k in range(steps):
                f = k / steps
                px = cx + side * np.sin(abs(ang)) * length * f
                py = y - np.cos(ang) * length * f * 0.9 + (f ** 2) * S * 0.06
                w = S * 0.012 * (1 - f * 0.8)
                _stamp_ellipse(alpha, px, py, w * 1.6, w, np.pi * 0.5 - ang * side, 1)
                _stamp_ellipse(shade, px, py, w * 1.6, w, np.pi * 0.5 - ang * side, 0.4 + 0.6 * f)
    n = fbm(S, 8, 4, 182)
    col = colorize(np.clip(shade * 0.7 + n * 0.3, 0, 1), (0.14, 0.28, 0.08), (0.36, 0.52, 0.18))
    tip_dry = np.clip((np.abs(np.mgrid[0:S, 0:S][1] - cx) / (S * 0.45)) - 0.7, 0, 1)[..., None] * 1.5
    col = col * (1 - tip_dry) + np.array([0.55, 0.48, 0.25]) * tip_dry
    save_png(os.path.join(OUT, "palm_frond_col.png"), np.concatenate([col, alpha[..., None]], -1))
    print("   palm_frond")


def leaf_cluster(S=1024, name="leaves", c0=(0.16, 0.30, 0.08), c1=(0.38, 0.52, 0.16), density=900, seed=191):
    alpha = np.zeros((S, S))
    shade = np.zeros((S, S))
    rng = np.random.default_rng(seed)
    for i in range(density):
        # denser in the middle, ragged edges
        r = np.sqrt(rng.random()) * 0.46
        a = rng.random() * 2 * np.pi
        cx = S * (0.5 + np.cos(a) * r)
        cy = S * (0.5 + np.sin(a) * r)
        sz = S * rng.uniform(0.012, 0.022)
        ang = rng.random() * np.pi
        v = rng.uniform(0.3, 1.0) * (1.0 - r)
        _stamp_ellipse(alpha, cx, cy, sz * 1.7, sz, ang, 1)
        _stamp_ellipse(shade, cx, cy, sz * 1.7, sz, ang, v)
    n = fbm(S, 8, 4, seed + 1)
    col = colorize(np.clip(shade * 0.8 + n * 0.2, 0, 1), c0, c1)
    save_png(os.path.join(OUT, f"{name}_col.png"), np.concatenate([col, alpha[..., None]], -1))
    print("  ", name)


def pine_needles(S=1024):
    alpha = np.zeros((S, S))
    shade = np.zeros((S, S))
    rng = np.random.default_rng(201)
    # a branch spray: many thin needles around a central twig (horizontal)
    for i in range(2600):
        t = rng.random()
        x0 = S * (0.05 + 0.9 * t)
        y0 = S * 0.5 + rng.normal(0, S * 0.02)
        ang = rng.uniform(0.3, 1.2) * (1 if rng.random() < 0.5 else -1)
        L = S * rng.uniform(0.06, 0.16) * (1.0 - 0.5 * abs(t - 0.4))
        for k in range(8):
            f = k / 8
            _stamp_ellipse(alpha, x0 + np.cos(ang) * L * f, y0 + np.sin(ang) * L * f, 3.5, 1.4, ang, 1)
            _stamp_ellipse(shade, x0 + np.cos(ang) * L * f, y0 + np.sin(ang) * L * f, 3.5, 1.4, ang, 0.3 + 0.7 * f)
    col = colorize(shade, (0.08, 0.18, 0.08), (0.22, 0.36, 0.16))
    save_png(os.path.join(OUT, "pine_col.png"), np.concatenate([col, alpha[..., None]], -1))
    print("   pine")


def bark(S=512):
    y, x = np.mgrid[0:S, 0:S] / S
    fib = fbm(S, 4, 6, 211)
    vert = periodic_noise(S, 32, 212)
    stretched = blur(np.repeat(white(S, 213)[:1, :], S, 0), 2)
    h = stretched * 0.6 + fib * 0.4
    col = colorize(h, (0.24, 0.18, 0.13), (0.45, 0.36, 0.27))
    save_pbr("bark", col, 0.92 - vert * 0.05, h, 5.0)


def palm_bark(S=512, rings=10):
    y, x = np.mgrid[0:S, 0:S] / S
    fy = (y * rings) % 1.0
    ring = np.clip(np.abs(fy - 0.5) * 3 - 0.6, 0, 1)
    fib = fbm(S, 8, 5, 221)
    h = (1 - ring) * 0.6 + fib * 0.3 + fy * 0.3
    col = colorize(fib * 0.5 + fy * 0.5, (0.36, 0.30, 0.22), (0.58, 0.50, 0.38)) * (0.75 + 0.25 * (1 - ring[..., None]))
    save_pbr("palm_bark", col, 0.9, h, 5.0)


def crop_field(S=512):
    y, x = np.mgrid[0:S, 0:S] / S
    rows = np.sin(x * 32 * np.pi) * 0.5 + 0.5
    n = fbm(S, 8, 5, 231)
    h = rows * 0.7 + n * 0.3
    col = colorize(rows * 0.6 + n * 0.4, (0.30, 0.26, 0.12), (0.52, 0.56, 0.20))
    save_pbr("crop", col, 0.9, h, 4.0)


def main():
    global OUT
    if len(sys.argv) > 1:
        OUT = sys.argv[1]
    os.makedirs(OUT, exist_ok=True)
    print("textures ->", OUT)
    try:  # car decal atlas needs Pillow for text (the Blender venv has it)
        sys.path.insert(0, HERE)
        import generate_decals
        generate_decals.build(OUT)
    except ImportError as ex:
        print("  ! car decals skipped:", ex)
    asphalt()
    concrete()
    pavers()
    grass()
    sand()
    dirt()
    rock()
    brick()
    plaster()
    metal_siding()
    wood_siding()
    wood_planks()
    roof_tiles()
    roof_membrane()
    shingles()
    tire()
    bark()
    palm_bark()
    crop_field()
    water_normal()
    cloud_noise()
    detail_noise()
    palm_frond()
    leaf_cluster()
    leaf_cluster(name="leaves_dark", c0=(0.10, 0.22, 0.07), c1=(0.26, 0.40, 0.12), density=1100, seed=195)
    pine_needles()


if __name__ == "__main__":
    main()
