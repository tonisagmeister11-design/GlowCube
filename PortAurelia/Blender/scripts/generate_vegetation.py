"""Procedural vegetation: palms, broadleaf trees, pines, cypresses, bushes, hedges, rocks.

Each builder returns (lod0, lod1) MeshBuilders with the base at the origin.
Foliage uses alpha-tested cards with normals bent away from the crown centre
(soft, volumetric lighting). Vertex colour R = wind sway weight (0 trunk .. 1 tips).
"""
import math

from _common import G, MeshBuilder, cylinder


def _n3(v):
    l = math.sqrt(v[0] ** 2 + v[1] ** 2 + v[2] ** 2) or 1.0
    return (v[0] / l, v[1] / l, v[2] / l)


def trunk(mb, path, r0, r1, mat="bark", segs=8, sway_top=0.2):
    """Tapered tube along a list of 3D points. UV u around, v up (metres * 0.5)."""
    n = len(path)
    acc = [0.0]
    for i in range(1, n):
        a, b = path[i - 1], path[i]
        acc.append(acc[-1] + math.sqrt(sum((b[k] - a[k]) ** 2 for k in range(3))))
    L = acc[-1] or 1.0
    rings = []
    for i, p in enumerate(path):
        t = acc[i] / L
        r = r0 + (r1 - r0) * t
        ring = []
        for s in range(segs):
            a = 2 * math.pi * s / segs
            ring.append((p[0] + math.cos(a) * r, p[1], p[2] + math.sin(a) * r))
        rings.append(ring)
    for i in range(n - 1):
        w0 = sway_top * acc[i] / L
        w1 = sway_top * acc[i + 1] / L
        for s in range(segs):
            s2 = (s + 1) % segs
            a, b = rings[i][s], rings[i][s2]
            c, d = rings[i + 1][s2], rings[i + 1][s]
            u0, u1 = s / segs, (s + 1) / segs
            v0, v1 = acc[i] * 0.5, acc[i + 1] * 0.5
            mid = ((a[0] + c[0]) * 0.5 - path[i][0], 0.0, (a[2] + c[2]) * 0.5 - path[i][2])
            mb.face([a, b, c, d], [(u0, -v0), (u1, -v0), (u1, -v1), (u0, -v1)], mat,
                    cols=[(w0, 0, 0, 1), (w0, 0, 0, 1), (w1, 0, 0, 1), (w1, 0, 0, 1)], up=mid)


def card(mb, center, right, up, w, h, mat, crown_c, sway, uv=(0, 0, 1, 1), bend=0.75):
    """Alpha card with normals blended towards the outward direction from the crown centre."""
    hw, hh = w * 0.5, h * 0.5
    pts = []
    for (a, b) in ((-hw, -hh), (hw, -hh), (hw, hh), (-hw, hh)):
        pts.append((center[0] + right[0] * a + up[0] * b, center[1] + right[1] * a + up[1] * b,
                    center[2] + right[2] * a + up[2] * b))
    fn = _n3((right[1] * up[2] - right[2] * up[1], right[2] * up[0] - right[0] * up[2], right[0] * up[1] - right[1] * up[0]))
    nrm = []
    for p in pts:
        o = _n3((p[0] - crown_c[0], p[1] - crown_c[1] + 0.3, p[2] - crown_c[2]))
        nrm.append(_n3((o[0] * bend + fn[0] * (1 - bend), o[1] * bend + fn[1] * (1 - bend), o[2] * bend + fn[2] * (1 - bend))))
    u0, v0, u1, v1 = uv
    mb.face(pts, [(u0, v1), (u1, v1), (u1, v0), (u0, v0)], mat, cols=[(sway, 0, 0, 1)] * 4, nrm=nrm)


def _rand_dir(rng):
    z = rng.uniform(-1, 1)
    a = rng.uniform(0, 2 * math.pi)
    r = math.sqrt(1 - z * z)
    return (r * math.cos(a), z, r * math.sin(a))


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def crown_cards(mb, rng, center, radii, count, size, mat, sway=1.0, flatten=1.0):
    for i in range(count):
        d = _rand_dir(rng)
        rr = rng.uniform(0.35, 1.0) ** 0.5
        p = (center[0] + d[0] * radii[0] * rr, center[1] + d[1] * radii[1] * rr * flatten, center[2] + d[2] * radii[2] * rr)
        n = _rand_dir(rng)
        right = _n3(_cross(n, (0, 1, 0))) if abs(n[1]) < 0.95 else (1, 0, 0)
        up = _n3(_cross(right, n))
        s = size * rng.uniform(0.75, 1.25)
        card(mb, p, right, up, s, s, mat, center, sway)


# ------------------------------------------------------------------ palms
def palm(rng, height=13.0, fronds=14, lean=0.6, short=False):
    lod0 = MeshBuilder()
    lod1 = MeshBuilder()
    # slightly curved trunk
    n = 8
    ang = rng.uniform(0, 2 * math.pi)
    path = []
    for i in range(n + 1):
        t = i / n
        off = lean * t * t * height * 0.12
        path.append((math.cos(ang) * off, t * height, math.sin(ang) * off))
    r0 = 0.32 if not short else 0.45
    trunk(lod0, path, r0, r0 * 0.62, "palm_bark", 8, 0.25)
    trunk(lod1, path[::2], r0, r0 * 0.62, "palm_bark", 5, 0.25)
    top = path[-1]
    # crown knob
    cylinder(lod0, (top[0], top[1] - 0.8, top[2]), r0 * 0.7, 1.2, "palm_bark", segs=8, r_top=r0 * 0.9)
    L = 4.6 if not short else 3.6
    for f in range(fronds):
        a = 2 * math.pi * f / fronds + rng.uniform(-0.15, 0.15)
        pitch0 = rng.uniform(0.25, 0.75)
        droop = rng.uniform(1.4, 2.2)
        length = L * rng.uniform(0.85, 1.15)
        _frond(lod0, top, a, pitch0, droop, length, 1.9, segs=6)
        if f % 2 == 0:
            _frond(lod1, top, a, pitch0, droop, length, 2.1, segs=3)
    # a few dead fronds hanging down
    for k in range(3):
        a = rng.uniform(0, 2 * math.pi)
        _frond(lod0, (top[0], top[1] - 0.6, top[2]), a, -0.9, 0.4, 2.8, 1.2, segs=3, tint=(0.62, 0.5, 0.3))
    return lod0, lod1


def _frond(mb, base, a, pitch0, droop, length, width, segs=6, tint=(1, 1, 1)):
    dh = (math.cos(a), 0.0, math.sin(a))
    side = (-math.sin(a), 0.0, math.cos(a))
    pts = [base]
    p = base
    for i in range(segs):
        s = (i + 1) / segs
        pitch = pitch0 - droop * s ** 1.6
        step = length / segs
        d = (dh[0] * math.cos(pitch), math.sin(pitch), dh[2] * math.cos(pitch))
        p = (p[0] + d[0] * step, p[1] + d[1] * step, p[2] + d[2] * step)
        pts.append(p)
    fold = 0.35
    for i in range(segs):
        s0, s1 = i / segs, (i + 1) / segs
        w0 = width * (0.35 + 0.65 * math.sin(math.pi * (0.15 + 0.85 * s0)) ** 0.6)
        w1 = width * (0.35 + 0.65 * math.sin(math.pi * (0.15 + 0.85 * s1)) ** 0.6)
        c0, c1 = pts[i], pts[i + 1]
        for sgn in (-1, 1):
            e0 = (c0[0] + side[0] * sgn * w0 * 0.5, c0[1] + fold * w0 * 0.25, c0[2] + side[2] * sgn * w0 * 0.5)
            e1 = (c1[0] + side[0] * sgn * w1 * 0.5, c1[1] + fold * w1 * 0.25 - 0.1 * s1, c1[2] + side[2] * sgn * w1 * 0.5)
            ue = 0.5 + sgn * 0.5
            up_n = _n3((side[0] * sgn * 0.3, 1.0, side[2] * sgn * 0.3))
            cols = [(0.4 + 0.6 * s0, 0, 0, 1), (0.4 + 0.6 * s1, 0, 0, 1), (0.4 + 0.6 * s1, 0, 0, 1), (0.4 + 0.6 * s0, 0, 0, 1)]
            if sgn < 0:
                pts4 = [e0, e1, c1, c0]
                uvs = [(0.0, 1 - s0), (0.0, 1 - s1), (0.5, 1 - s1), (0.5, 1 - s0)]
            else:
                pts4 = [c0, c1, e1, e0]
                uvs = [(0.5, 1 - s0), (0.5, 1 - s1), (1.0, 1 - s1), (1.0, 1 - s0)]
            mb.face(pts4, uvs, "palm_frond", cols=cols, nrm=[up_n] * 4)
        _ = tint


# ------------------------------------------------------------------ broadleaf trees
def broadleaf(rng, height=9.0, crown=(3.2, 2.6, 3.2), cards=46, mat="leaves", branches=5):
    lod0 = MeshBuilder()
    lod1 = MeshBuilder()
    trunk_h = height * 0.45
    path = [(0, 0, 0), (rng.uniform(-0.2, 0.2), trunk_h * 0.5, rng.uniform(-0.2, 0.2)), (0, trunk_h, 0)]
    trunk(lod0, path, 0.28, 0.18, "bark", 8, 0.0)
    trunk(lod1, path, 0.28, 0.18, "bark", 5, 0.0)
    cc = (0.0, trunk_h + crown[1] * 0.85, 0.0)
    for b in range(branches):
        a = 2 * math.pi * b / branches + rng.uniform(-0.3, 0.3)
        end = (math.cos(a) * crown[0] * 0.6, trunk_h + crown[1] * rng.uniform(0.6, 1.2), math.sin(a) * crown[2] * 0.6)
        trunk(lod0, [(0, trunk_h * 0.85, 0), end], 0.12, 0.04, "bark", 5, 0.3)
    crown_cards(lod0, rng, cc, crown, cards, 2.4, mat, sway=1.0)
    crown_cards(lod1, rng, cc, crown, max(8, cards // 5), 3.6, mat, sway=1.0)
    return lod0, lod1


def pine(rng, height=14.0):
    lod0 = MeshBuilder()
    lod1 = MeshBuilder()
    trunk(lod0, [(0, 0, 0), (0, height, 0)], 0.3, 0.06, "bark", 7, 0.1)
    trunk(lod1, [(0, 0, 0), (0, height, 0)], 0.3, 0.06, "bark", 4, 0.1)
    whorls = 9
    for w in range(whorls):
        t = w / whorls
        y = height * (0.22 + 0.74 * t)
        radius = (1 - t) * 3.2 + 0.4
        count = 7
        for k in range(count):
            a = 2 * math.pi * k / count + w * 0.5 + rng.uniform(-0.2, 0.2)
            d = (math.cos(a), -0.25, math.sin(a))
            mid = (d[0] * radius * 0.5, y, d[2] * radius * 0.5)
            right = _n3(d)
            up = _n3(_cross(right, (0, 1, 0)))
            up = _n3((up[0], 0.25, up[2]))
            for mb, keep in ((lod0, True), (lod1, k % 3 == 0)):
                if keep:
                    card(mb, mid, right, up, radius * 1.1, 1.4, "pine", (0, y, 0), 0.4 + 0.6 * t, bend=0.5)
    return lod0, lod1


def cypress(rng, height=11.0):
    lod0 = MeshBuilder()
    lod1 = MeshBuilder()
    trunk(lod0, [(0, 0, 0), (0, height * 0.3, 0)], 0.2, 0.15, "bark", 6, 0.0)
    cc = (0.0, height * 0.55, 0.0)
    crown_cards(lod0, rng, cc, (0.9, height * 0.45, 0.9), 40, 1.6, "leaves_dark", sway=1.0)
    crown_cards(lod1, rng, cc, (0.9, height * 0.45, 0.9), 8, 2.4, "leaves_dark", sway=1.0)
    return lod0, lod1


def bush(rng, size=1.3):
    lod0 = MeshBuilder()
    lod1 = MeshBuilder()
    cc = (0.0, size * 0.55, 0.0)
    crown_cards(lod0, rng, cc, (size, size * 0.6, size), 14, 1.2, "leaves", sway=0.4)
    crown_cards(lod1, rng, cc, (size, size * 0.6, size), 4, 1.8, "leaves", sway=0.4)
    return lod0, lod1


def hedge(rng):
    lod0 = MeshBuilder()
    lod1 = MeshBuilder()
    for i in range(10):
        p = (rng.uniform(-1.4, 1.4), rng.uniform(0.3, 1.1), rng.uniform(-0.3, 0.3))
        n = _rand_dir(rng)
        right = _n3(_cross(n, (0, 1, 0))) if abs(n[1]) < 0.95 else (1, 0, 0)
        up = _n3(_cross(right, n))
        card(lod0, p, right, up, 1.2, 1.2, "leaves_dark", (p[0], 0.7, 0.0), 0.2)
    card(lod1, (0, 0.7, 0), (1, 0, 0), (0, 1, 0), 3.0, 1.4, "leaves_dark", (0, 0.7, -1.0), 0.2)
    card(lod1, (0, 0.7, 0), (1, 0, 0), (0, 0.2, 1), 3.0, 0.8, "leaves_dark", (0, 0.0, 0.0), 0.2)
    return lod0, lod1


def rock(rng, size=1.6):
    """Irregular low-poly boulder (displaced icosphere-ish)."""
    lod0 = MeshBuilder()
    rings = 5
    segs = 8
    pts = []
    for r in range(rings + 1):
        phi = math.pi * r / rings
        ring = []
        for s in range(segs):
            th = 2 * math.pi * s / segs
            k = 1.0 + rng.uniform(-0.22, 0.22)
            ring.append((math.sin(phi) * math.cos(th) * size * k, math.cos(phi) * size * 0.6 * k + size * 0.35,
                         math.sin(phi) * math.sin(th) * size * k))
        pts.append(ring)
    for r in range(rings):
        for s in range(segs):
            s2 = (s + 1) % segs
            a, b, c, d = pts[r][s], pts[r][s2], pts[r + 1][s2], pts[r + 1][s]
            mid = ((a[0] + c[0]) * 0.5, (a[1] + c[1]) * 0.5 - size * 0.35, (a[2] + c[2]) * 0.5)
            lod0.face([a, b, c, d], None, "rock", up=mid)
    return lod0, lod0


VEGETATION = {
    "palm_tall": lambda rng: palm(rng, 14.0, 15, 0.9),
    "palm_short": lambda rng: palm(rng, 6.5, 13, 0.3, short=True),
    "tree_round": lambda rng: broadleaf(rng, 8.0, (2.6, 2.3, 2.6), 40, "leaves", 4),
    "tree_oak": lambda rng: broadleaf(rng, 11.0, (4.2, 3.0, 4.2), 64, "leaves", 6),
    "tree_pine": lambda rng: pine(rng, 15.0),
    "tree_cypress": lambda rng: cypress(rng, 11.0),
    "bush": lambda rng: bush(rng, 1.2),
    "hedge": lambda rng: hedge(rng),
    "rock": lambda rng: rock(rng, 1.8),
}
