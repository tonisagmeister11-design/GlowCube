"""Modular building generator.

A building spec (from the city plan) is turned into geometry from reusable
modules: facade walls (window grid drawn by the Godot facade shader using
UV = metres along/up the facade and UV2 = (side length, floor height)),
shopfront ground floors, doors, cornices/ledges, balconies, parapets, roofs
(flat, gable, hip, sawtooth), awnings, signs and roof equipment.

LOD0 = full detail, LOD1 = volumes + roofs only, FAR = one prism.
"""
import math

from _common import G, MeshBuilder, box, cylinder, prism, cap_polygon, sweep

SHOP_NAMES = ["CORA", "Nova Deli", "Sol & Sal", "Brightline", "Kiko", "Maison 9", "Luma", "Harbor Books",
              "Pixelhaus", "Tidewater", "Café Alba", "Urban Rack", "Marlo", "Seaside Optics", "Fresco",
              "Velvet", "Noodle Bar", "Parlour", "Aria", "Golden Crust", "Bodega 24", "Juno", "Vista Florist",
              "Ruby's", "Neon Tiger", "The Anchor", "Paloma", "Big Slice"]


class BuildingContext:
    def __init__(self, lod0, lod1, detail, far, col_building, rng):
        self.lod0 = lod0          # main volumes, full facades
        self.lod1 = lod1          # simplified volumes
        self.detail = detail      # small details (LOD0 only)
        self.far = far            # far representation
        self.col = col_building   # collision
        self.rng = rng
        self.signs = []           # (text, pos, rot_y, size, color) for 3D text pass
        self.relief = MeshBuilder()   # 3D facade relief (frames, sills, fins, balconies) - LOD0 only
        self.balcony_cols = {}    # (side start, side end) -> columns carrying balconies (no sill there)
        lod0.relief_ctx = self    # facade_walls on the LOD0 builder adds the matching relief


def seed_col(spec, tint=None):
    t = tint or spec.get("tint") or (1.0, 1.0, 1.0)
    s = (spec["seed"] % 997) / 997.0
    return (t[0], t[1], t[2], s)


def facade_walls(mb, poly, y0, y1, mat, col, floor_h, skip_side=None):
    """Walls of a vertical prism with per-side facade UVs (u reset per side)."""
    poly = G.ensure_ccw(poly)
    n = len(poly)
    for i in range(n):
        if skip_side is not None and i == skip_side:
            continue
        a = poly[i]
        b = poly[(i + 1) % n]
        l = G.dist(a, b)
        if l < 0.05:
            continue
        e = G.norm(G.sub(b, a))
        out = G.left(e)
        mb.face([(a[0], y0, a[1]), (b[0], y0, b[1]), (b[0], y1, b[1]), (a[0], y1, a[1])],
                [(0.0, y0), (l, y0), (l, y1), (0.0, y1)], mat, col, up=(out[0], 0, out[1]), uv2=(l, floor_h))
        rc = getattr(mb, "relief_ctx", None)
        if rc is not None:
            facade_relief(rc, a, b, y0, y1, mat, col, floor_h)


# ====================================================================== 3D facade relief
# Window grid of every facade material, mirrored from the facade shader / material library:
# style, spacing, width (fraction of the pitch), height and sill (fractions of the floor height).
FACADE_WIN = {
    "facade_stone": (0, 2.6, 0.45, 0.58, 0.28), "facade_brick_red": (0, 2.8, 0.42, 0.55, 0.28),
    "facade_brick_brown": (0, 2.8, 0.42, 0.55, 0.28), "facade_plaster": (0, 3.0, 0.45, 0.52, 0.28),
    "facade_stucco_white": (4, 3.2, 0.5, 0.48, 0.28), "facade_wood_siding": (4, 3.0, 0.42, 0.5, 0.28),
    "facade_concrete_panel": (1, 3.0, 1.0, 0.46, 0.36), "facade_glass_blue": (2, 1.6, 0.96, 0.8, 0.1),
    "facade_glass_green": (2, 1.6, 0.96, 0.8, 0.1), "facade_glass_silver": (2, 1.5, 0.96, 0.8, 0.1),
    "facade_glass_bronze": (2, 1.6, 0.96, 0.8, 0.1), "facade_glass_dark": (2, 1.8, 0.96, 0.8, 0.1),
}
TRIM_COLORS = [(0.96, 0.95, 0.92, 1), (0.93, 0.89, 0.8, 1), (0.82, 0.8, 0.76, 1), (0.36, 0.36, 0.37, 1),
               (0.2, 0.2, 0.21, 1), (0.88, 0.84, 0.74, 1)]
SHUTTER_COLORS = [(0.2, 0.36, 0.28, 1), (0.22, 0.32, 0.46, 1), (0.45, 0.2, 0.16, 1), (0.34, 0.36, 0.38, 1),
                  (0.9, 0.9, 0.88, 1), (0.52, 0.62, 0.66, 1)]
FIN_COLORS = [(0.62, 0.64, 0.66, 1), (0.16, 0.16, 0.17, 1), (0.8, 0.8, 0.78, 1), (0.42, 0.34, 0.24, 1)]
RELIEF_FULL_H = 22.0      # full window surrounds up to this height above the facade base, sills only above
RELIEF_MAX_H = 45.0       # no punched-window relief above this (invisible from the street, costs GPU time)


def _h(seed, k):
    """Deterministic 0..1 value per building seed (+ salt)."""
    x = math.sin(seed * 12.9898 + k * 78.233) * 43758.5453
    return x - math.floor(x)


def _rbox(mb, a, e, out, u0, u1, y0, y1, d0, d1, mat, col, top=True, bottom=True, sides=True):
    """Box in facade space (u along the side, y up, d out of the wall) without the hidden back face."""
    def P(u, y, d):
        return (a[0] + e[0] * u + out[0] * d, y, a[1] + e[1] * u + out[1] * d)
    on = (out[0], 0, out[1])
    mb.face([P(u0, y0, d1), P(u1, y0, d1), P(u1, y1, d1), P(u0, y1, d1)], [(u0, y0), (u1, y0), (u1, y1), (u0, y1)],
            mat, col, up=on)
    if top:
        mb.face([P(u0, y1, d0), P(u1, y1, d0), P(u1, y1, d1), P(u0, y1, d1)], [(u0, d0), (u1, d0), (u1, d1), (u0, d1)],
                mat, col, up=(0, 1, 0))
    if bottom:
        mb.face([P(u0, y0, d0), P(u1, y0, d0), P(u1, y0, d1), P(u0, y0, d1)], [(u0, d0), (u1, d0), (u1, d1), (u0, d1)],
                mat, col, up=(0, -1, 0))
    if sides:
        mb.face([P(u0, y0, d0), P(u0, y0, d1), P(u0, y1, d1), P(u0, y1, d0)], [(d0, y0), (d1, y0), (d1, y1), (d0, y1)],
                mat, col, up=(-e[0], 0, -e[1]))
        mb.face([P(u1, y0, d0), P(u1, y0, d1), P(u1, y1, d1), P(u1, y1, d0)], [(d0, y0), (d1, y0), (d1, y1), (d0, y1)],
                mat, col, up=(e[0], 0, e[1]))


def _side_bar(mb, a, e, out, u, y0, y1, d0, d1, col, left):
    """Single outward-facing quad for a baluster on the side of a balcony."""
    def P(uu, y, d):
        return (a[0] + e[0] * uu + out[0] * d, y, a[1] + e[1] * uu + out[1] * d)
    n = (-e[0], 0, -e[1]) if left else (e[0], 0, e[1])
    mb.face([P(u, y0, d0), P(u, y0, d1), P(u, y1, d1), P(u, y1, d0)], None, "metal_painted", col, up=n)


def _frame_ring(mb, a, e, out, u0, u1, y0, y1, t, d, mat, col):
    """Picture-frame window surround: front ring and inner reveals (14 tris)."""
    def P(u, y, dd):
        return (a[0] + e[0] * u + out[0] * dd, y, a[1] + e[1] * u + out[1] * dd)
    on = (out[0], 0, out[1])
    U0, U1, Y0, Y1 = u0 - t, u1 + t, y0 - t, y1 + t
    ring = [((U0, Y0), (U1, Y0), (u1, y0), (u0, y0)), ((U1, Y0), (U1, Y1), (u1, y1), (u1, y0)),
            ((U1, Y1), (U0, Y1), (u0, y1), (u1, y1)), ((U0, Y1), (U0, Y0), (u0, y0), (u0, y1))]
    for q in ring:
        mb.face([P(uu, yy, d) for (uu, yy) in q], [(uu, yy) for (uu, yy) in q], mat, col, up=on)
    # inner reveals (top and jambs; the sill closes the bottom). The outer edges are only a few cm
    # deep and never visible from the street, so they are left out to keep the city light.
    mb.face([P(u0, y1, 0), P(u1, y1, 0), P(u1, y1, d), P(u0, y1, d)], None, mat, col, up=(0, -1, 0))
    mb.face([P(u0, y0, 0), P(u0, y1, 0), P(u0, y1, d), P(u0, y0, d)], None, mat, col, up=(e[0], 0, e[1]))
    mb.face([P(u1, y0, 0), P(u1, y1, 0), P(u1, y1, d), P(u1, y0, d)], None, mat, col, up=(-e[0], 0, -e[1]))


def facade_relief(ctx, a, b, y0, y1, mat, col, fh):
    """Real geometry that matches the shader-drawn windows of one facade side: window surrounds,
    sills and lintels, shutters, flower boxes and AC units for punched windows, sill/head bands for
    ribbon windows, vertical fins and floor slabs for curtain walls."""
    spec = FACADE_WIN.get(mat)
    if spec is None or fh > 20.0 or y1 - y0 < 2.0:
        return
    style, spacing, ww, wh, sill = spec
    l = G.dist(a, b)
    if l < 2.0:
        return
    e = G.norm(G.sub(b, a))
    out = G.left(e)
    mb = ctx.relief
    seed = col[3] if len(col) > 3 else 0.5
    ncol = max(1.0, math.floor(l / spacing + 0.5))
    pitch = l / ncol
    k0 = int(math.ceil((y0 + 0.25) / fh - sill))
    k1 = int(math.floor((y1 - 0.2) / fh - sill - wh))
    if style == 2:
        # curtain wall: slim vertical fins on the mullion lines + a slab edge on every floor line
        fcol = FIN_COLORS[int(_h(seed, 3) * len(FIN_COLORS))]
        every = 2 if _h(seed, 4) < 0.5 else 3
        depth = 0.18 + 0.2 * _h(seed, 5)
        for c in range(0, int(ncol) + 1, every):
            u = c * pitch
            u0, u1 = max(0.0, u - 0.05), min(l, u + 0.05)
            _rbox(mb, a, e, out, u0, u1, y0 + 0.1, y1 - 0.05, 0.0, depth, "metal_painted", fcol, bottom=False)
        k = int(math.ceil(y0 / fh))
        while k * fh < y1 - 0.5:
            y = k * fh
            if y > y0 + 0.5:
                _rbox(mb, a, e, out, 0.0, l, y - 0.12, y + 0.12, 0.0, 0.12, "metal_painted", fcol, sides=False)
            k += 1
        return
    if style == 1:
        # ribbon windows: continuous precast sill and head bands
        tcol = TRIM_COLORS[int(_h(seed, 6) * 3)]
        for k in range(k0, k1 + 1):
            ys = (k + sill) * fh
            _rbox(mb, a, e, out, -0.05, l + 0.05, ys - 0.22, ys, 0.0, 0.22, "trim_tint", tcol)
            yh = (k + sill + wh) * fh
            _rbox(mb, a, e, out, -0.05, l + 0.05, yh, yh + 0.14, 0.0, 0.1, "trim_tint", tcol, bottom=True)
        return
    # punched windows
    if "stone" in mat or ("brick" in mat and _h(seed, 15) < 0.5):
        # quoins / corner pilasters
        qc = (0.86, 0.82, 0.74, 1) if "stone" in mat else (0.9, 0.88, 0.82, 1)
        for (q0, q1) in ((0.0, 0.55), (l - 0.55, l)):
            _rbox(mb, a, e, out, q0, q1, y0, y1, 0.0, 0.09, "trim_tint", qc, top=False, bottom=False)
    brick = "brick" in mat
    stone = "stone" in mat
    house = style == 4
    tcol = TRIM_COLORS[int(_h(seed, 7) * len(TRIM_COLORS))]
    if brick or stone:
        tcol = (0.86, 0.82, 0.74, 1) if _h(seed, 8) < 0.7 else (0.95, 0.94, 0.9, 1)
    shutters = house and _h(seed, 9) < 0.55 or (mat == "facade_plaster" and _h(seed, 10) < 0.25)
    scol = SHUTTER_COLORS[int(_h(seed, 11) * len(SHUTTER_COLORS))]
    ac_rate = 0.0 if house or stone else 0.07 * _h(seed, 12)
    flowers = (house or stone or brick) and _h(seed, 13) < 0.45
    bal = ctx.balcony_cols.get((round(a[0], 2), round(a[1], 2), round(b[0], 2), round(b[1], 2)), set())
    rng = ctx.rng
    for k in range(k0, k1 + 1):
        ys = (k + sill) * fh
        yt = ys + wh * fh
        if ys - y0 > RELIEF_MAX_H:
            break
        full = ys - y0 < RELIEF_FULL_H
        for c in range(int(ncol)):
            uc = (c + 0.5) * pitch
            hw = ww * pitch * 0.5
            u0, u1 = uc - hw, uc + hw
            if (c, k) in bal:
                # balcony door: plain surround, no sill
                _frame_ring(mb, a, e, out, u0, u1, k * fh + 0.05, yt, 0.08, 0.06, "trim_tint", tcol)
                continue
            if full:
                if brick:
                    # stone lintel + sill, the brick reveals do the rest
                    _rbox(mb, a, e, out, u0 - 0.12, u1 + 0.12, yt, yt + 0.24, 0.0, 0.08, "trim_tint", tcol, top=False,
                          sides=False)
                else:
                    _frame_ring(mb, a, e, out, u0, u1, ys, yt, 0.11 if stone else 0.08, 0.07, "trim_tint", tcol)
                    if stone and _h(seed, 14) < 0.6:
                        # cornice-style hood over the window
                        _rbox(mb, a, e, out, u0 - 0.2, u1 + 0.2, yt + 0.11, yt + 0.24, 0.0, 0.16, "trim_tint", tcol)
            # sill (always, sticks out further than the surround)
            _rbox(mb, a, e, out, u0 - 0.1, u1 + 0.1, ys - 0.07, ys, 0.0, 0.14 if full else 0.1, "trim_tint", tcol,
                  sides=False)
            if not full:
                continue
            if shutters:
                sw = hw * 0.95
                for (s0, s1) in ((u0 - 0.1 - sw, u0 - 0.1), (u1 + 0.1, u1 + 0.1 + sw)):
                    if s0 > 0.1 and s1 < l - 0.1:
                        _rbox(mb, a, e, out, s0, s1, ys + 0.02, yt - 0.02, 0.0, 0.04, "metal_painted", scol,
                              top=False, bottom=False, sides=False)
            r = rng.random()
            if flowers and r < 0.18:
                _rbox(mb, a, e, out, u0 + 0.05, u1 - 0.05, ys, ys + 0.18, 0.02, 0.22, "awning",
                      (0.35, 0.2, 0.12, 1), bottom=False)
                _rbox(mb, a, e, out, u0 + 0.08, u1 - 0.08, ys + 0.18, ys + 0.34, 0.04, 0.2, "hedge", (1, 1, 1, 1),
                      bottom=False)
            elif r > 1.0 - ac_rate and k * fh > y0 + 2.0:
                _rbox(mb, a, e, out, uc - 0.4, uc + 0.4, ys - 0.62, ys - 0.1, 0.0, 0.42, "metal",
                      (0.9, 0.9, 0.88, 1))


def roof_cap(mb, poly, y, mat="roof_flat", col=(1, 1, 1, 1)):
    cap_polygon(mb, poly, y, mat, col)


def parapet(mb, poly, y, h=0.9, t=0.3, mat="trim", col=(1, 1, 1, 1)):
    """Low wall around the roof edge."""
    poly = G.ensure_ccw(poly)
    inner = G.poly_inset(poly, t)
    if not inner or len(inner) != len(poly):
        prism(mb, poly, y, y + h, mat, top=True, col=col)
        return
    inner = G.ensure_ccw(inner)
    n = len(poly)
    for i in range(n):
        a, b = poly[i], poly[(i + 1) % n]
        ia, ib = inner[i], inner[(i + 1) % n]
        e = G.norm(G.sub(b, a))
        out = G.left(e)
        mb.face([(a[0], y, a[1]), (b[0], y, b[1]), (b[0], y + h, b[1]), (a[0], y + h, a[1])], None, mat, col,
                up=(out[0], 0, out[1]))
        mb.face([(ib[0], y, ib[1]), (ia[0], y, ia[1]), (ia[0], y + h, ia[1]), (ib[0], y + h, ib[1])], None, mat, col,
                up=(-out[0], 0, -out[1]))
        mb.face([(a[0], y + h, a[1]), (b[0], y + h, b[1]), (ib[0], y + h, ib[1]), (ia[0], y + h, ia[1])], None, mat, col,
                up=(0, 1, 0))


def ledge(mb, poly, y, depth=0.25, h=0.3, mat="trim", col=(1, 1, 1, 1)):
    """Horizontal band sticking out of the facade (cornice / floor ledge)."""
    outer = _outset(poly, depth)
    if not outer:
        return
    prism(mb, outer, y, y + h, mat, top=True, bottom=True, col=col, uv_mode="world")


def _outset(poly, d):
    poly = G.ensure_ccw(poly)
    n = len(poly)
    lines = []
    for i in range(n):
        a = poly[i]
        b = poly[(i + 1) % n]
        e = G.norm(G.sub(b, a))
        lines.append((G.add(a, G.mul(G.left(e), d)), e))
    res = []
    for i in range(n):
        p0, d0 = lines[i - 1]
        p1, d1 = lines[i]
        r = G.line_intersect(p0, d0, p1, d1)
        res.append(p1 if r is None else G.add(p0, G.mul(d0, r[0])))
    return res


def front_side_index(poly, front):
    poly = G.ensure_ccw(poly)
    best = (-2.0, 0)
    for i in range(len(poly)):
        a, b = poly[i], poly[(i + 1) % len(poly)]
        if G.dist(a, b) < 1.0:
            continue
        out = G.left(G.norm(G.sub(b, a)))
        d = G.dot(out, front)
        if d > best[0]:
            best = (d, i)
    return best[1]


def shopfront(ctx, poly, y0, h, spec, side=None, col=(1, 1, 1, 1), awning=False, sign=None, all_sides=False):
    """Ground floor: pillars at the corners, recessed glass shop windows, doors, sign band."""
    poly = G.ensure_ccw(poly)
    n = len(poly)
    rng = ctx.rng
    fi = front_side_index(poly, spec["front"]) if side is None else side
    sides = range(n) if all_sides else [fi]
    inner = G.poly_inset(poly, 0.35)
    if not inner or len(inner) != n:
        inner = poly
    inner = G.ensure_ccw(inner)
    for i in range(n):
        a, b = poly[i], poly[(i + 1) % n]
        l = G.dist(a, b)
        e = G.norm(G.sub(b, a))
        out = G.left(e)
        if i not in sides or l < 4.0:
            # plain wall on non-shop sides
            ctx.lod0.face([(a[0], y0, a[1]), (b[0], y0, b[1]), (b[0], y0 + h, b[1]), (a[0], y0 + h, a[1])],
                          [(0, 0), (l, 0), (l, h), (0, h)], "facade_" + spec.get("facade", "plaster"), col,
                          up=(out[0], 0, out[1]), uv2=(l, 99.0))
            continue
        ia, ib = inner[i], inner[(i + 1) % n]
        # recessed glass
        gh = h - 1.1
        ctx.lod0.face([(ia[0], y0, ia[1]), (ib[0], y0, ib[1]), (ib[0], y0 + gh, ib[1]), (ia[0], y0 + gh, ia[1])],
                      [(0, 0), (l, 0), (l, gh), (0, gh)], "shopfront", (1, 1, 1, (spec["seed"] % 101) / 101.0),
                      up=(out[0], 0, out[1]), uv2=(l, gh))
        # reveal faces (soffit + jambs)
        ctx.lod0.face([(a[0], y0 + gh, a[1]), (b[0], y0 + gh, b[1]), (ib[0], y0 + gh, ib[1]), (ia[0], y0 + gh, ia[1])],
                      None, "metal_dark", up=(0, -1, 0))
        # sign band above glass
        ctx.lod0.face([(a[0], y0 + gh, a[1]), (b[0], y0 + gh, b[1]), (b[0], y0 + h, b[1]), (a[0], y0 + h, a[1])],
                      [(0, 0), (l, 0), (l, 1.1), (0, 1.1)], "sign_panel", col, up=(out[0], 0, out[1]))
        # mullions / pillars
        k = 0.0
        step = max(2.5, l / max(1, round(l / 3.2)))
        while k <= l + 0.01:
            p = G.add(a, G.mul(e, k))
            p = G.add(p, G.mul(out, -0.18))
            w = 0.5 if (k < 0.01 or k > l - 0.01) else 0.12
            box(ctx.detail, (p[0], y0 + gh * 0.5, p[1]), (w, gh, 0.36 if w > 0.2 else 0.2), "metal_dark",
                rot_y=math.atan2(-e[0], -e[1]) + math.pi * 0.5)
            k += step
        # door in the middle
        mid = G.lerp(ia, ib, 0.5)
        dp = G.add(mid, G.mul(out, 0.02))
        box(ctx.detail, (dp[0], y0 + 1.1, dp[1]), (1.8, 2.2, 0.06), "door", rot_y=math.atan2(-e[0], -e[1]) + math.pi * 0.5)
        if awning:
            _awning(ctx, a, b, out, y0 + gh + 0.1, rng)
        if sign:
            c = G.lerp(a, b, 0.5)
            c = G.add(c, G.mul(out, 0.08))
            ctx.signs.append((sign, (c[0], y0 + gh + 0.55, c[1]), math.atan2(out[0], out[1]), min(0.8, l / max(4, len(sign)) * 1.3),
                              rng.choice([(1.0, 0.95, 0.85), (1.0, 0.4, 0.3), (0.3, 0.8, 1.0), (1.0, 0.8, 0.2)])))
    # collision for the whole ground floor
    prism(ctx.col, poly, y0, y0 + h, "concrete", top=False)


def _awning(ctx, a, b, out, y, rng):
    col = rng.choice([(0.72, 0.15, 0.12, 1), (0.1, 0.35, 0.25, 1), (0.12, 0.2, 0.45, 1), (0.85, 0.65, 0.2, 1),
                      (0.9, 0.9, 0.88, 1)])
    d = 1.6
    a2 = G.add(a, G.mul(out, d))
    b2 = G.add(b, G.mul(out, d))
    ctx.detail.face([(a[0], y + 0.6, a[1]), (b[0], y + 0.6, b[1]), (b2[0], y, b2[1]), (a2[0], y, a2[1])],
                    None, "awning", col, up=(out[0], 1, out[1]))
    ctx.detail.face([(a2[0], y, a2[1]), (b2[0], y, b2[1]), (b2[0], y - 0.3, b2[1]), (a2[0], y - 0.3, a2[1])],
                    None, "awning", col, up=(out[0], 0, out[1]))
    ctx.detail.face([(b[0], y + 0.6, b[1]), (a[0], y + 0.6, a[1]), (a2[0], y, a2[1]), (b2[0], y, b2[1])],
                    None, "awning", col, up=(-out[0], -1, -out[1]))


def roof_equipment(ctx, poly, y, rng, amount=1.0):
    """AC units, vents, water tank and elevator housing on flat roofs."""
    inner = G.poly_inset(poly, 2.0)
    if not inner:
        return
    x0, z0, x1, z1 = G.poly_bbox(inner)
    area = abs(G.poly_area(inner))
    n = int(min(10, area / 90.0) * amount)
    for _ in range(n):
        p = (rng.uniform(x0, x1), rng.uniform(z0, z1))
        if not G.point_in_poly(p, inner):
            continue
        t = rng.random()
        if t < 0.55:
            box(ctx.detail, (p[0], y + 0.6, p[1]), (rng.uniform(1.2, 2.4), 1.2, rng.uniform(1.0, 1.8)), "metal",
                rot_y=0.0)
        elif t < 0.75:
            cylinder(ctx.detail, (p[0], y, p[1]), 0.35, 1.2, "metal", segs=8)
        elif t < 0.88 and area > 300:
            box(ctx.detail, (p[0], y + 1.6, p[1]), (4.0, 3.2, 4.0), "trim")
        else:
            # water tank on legs
            for dx in (-0.8, 0.8):
                for dz in (-0.8, 0.8):
                    box(ctx.detail, (p[0] + dx, y + 1.0, p[1] + dz), (0.15, 2.0, 0.15), "metal_dark")
            cylinder(ctx.detail, (p[0], y + 2.0, p[1]), 1.3, 2.4, "wood", segs=10, r_top=1.3)
            cylinder(ctx.detail, (p[0], y + 4.4, p[1]), 1.35, 0.6, "metal_dark", segs=10, r_top=0.1)


def balconies(ctx, poly, y0, y1, floor_h, spec, side_filter=None, mat=None):
    """Balconies that sit on the window grid of the facade (door behind each one), stacked in
    columns like real apartment blocks. Registers the columns so the facade relief draws balcony
    doors instead of sills there. Call before facade_walls."""
    rng = ctx.rng
    poly = G.ensure_ccw(poly)
    n = len(poly)
    fac = mat or ("facade_" + spec.get("facade", "plaster"))
    win = FACADE_WIN.get(fac)
    if win is None or win[0] == 2:
        return
    _, spacing, ww, wh, sill = win
    seed = (spec["seed"] % 997) / 997.0
    kind = int(_h(seed, 21) * 3)                         # 0 glass, 1 bars, 2 solid parapet
    if spec.get("style") in ("oldtown", "highrise_classic"):
        kind = 1
    depth = 1.15 + 0.4 * _h(seed, 22)
    rail_col = FIN_COLORS[int(_h(seed, 23) * len(FIN_COLORS))]
    slab_col = TRIM_COLORS[int(_h(seed, 24) * 3)]
    fi = front_side_index(poly, spec["front"])
    for i in range(n):
        if side_filter == "front" and i != fi:
            continue
        a, b = poly[i], poly[(i + 1) % n]
        l = G.dist(a, b)
        if l < 6:
            continue
        if not rng.chance(0.85 if i == fi else 0.4):
            continue
        e = G.norm(G.sub(b, a))
        out = G.left(e)
        ncol = int(max(1.0, math.floor(l / spacing + 0.5)))
        pitch = l / ncol
        pattern = rng.randint(0, 3)
        cols = [c for c in range(ncol) if (pattern == 0 or (pattern == 1 and c % 2 == 0) or
                                           (pattern == 2 and c % 2 == 1) or
                                           (pattern == 3 and (c in (0, ncol - 1) or c == ncol // 2)))]
        if not cols:
            continue
        k0 = int(math.ceil((y0 + floor_h * 0.8) / floor_h))
        k_top = int(math.floor((y1 - 1.6) / floor_h))
        key = (round(a[0], 2), round(a[1], 2), round(b[0], 2), round(b[1], 2))
        reg = ctx.balcony_cols.setdefault(key, set())
        w = min(pitch - 0.25, max(ww * pitch + 0.9, pitch * 0.8))
        mb = ctx.relief
        for k in range(k0, k_top + 1):
            if (k + sill + wh) * floor_h > y1 - 0.2:
                continue
            yf = k * floor_h
            for c in cols:
                reg.add((c, k))
                uc = (c + 0.5) * pitch
                u0, u1 = uc - w * 0.5, uc + w * 0.5
                # slab with a crisp edge band
                _rbox(mb, a, e, out, u0, u1, yf - 0.2, yf + 0.02, 0.0, depth, "concrete", (1, 1, 1, 1))
                _rbox(mb, a, e, out, u0 - 0.02, u1 + 0.02, yf - 0.22, yf + 0.04, depth - 0.02, depth + 0.03,
                      "trim_tint", slab_col, top=True, bottom=True)
                if kind == 0:
                    # frameless glass balustrade with a slim metal handrail
                    for (pu0, pu1, pd0, pd1) in ((u0 + 0.04, u1 - 0.04, depth - 0.1, depth - 0.08),):
                        _rbox(mb, a, e, out, pu0, pu1, yf + 0.04, yf + 1.0, pd0, pd1, "glass_clear", (1, 1, 1, 1),
                              top=False, bottom=False)
                    for uu in (u0 + 0.04, u1 - 0.06):
                        _rbox(mb, a, e, out, uu, uu + 0.02, yf + 0.04, yf + 1.0, 0.05, depth - 0.1, "glass_clear",
                              (1, 1, 1, 1), top=False, bottom=False, sides=True)
                    _rbox(mb, a, e, out, u0 + 0.02, u1 - 0.02, yf + 1.0, yf + 1.05, depth - 0.12, depth - 0.06,
                          "metal_painted", rail_col)
                elif kind == 1:
                    # metal railing: handrail, bottom rail and balusters
                    _rbox(mb, a, e, out, u0 + 0.03, u1 - 0.03, yf + 0.98, yf + 1.04, depth - 0.1, depth - 0.04,
                          "metal_painted", rail_col)
                    _rbox(mb, a, e, out, u0 + 0.03, u1 - 0.03, yf + 0.1, yf + 0.14, depth - 0.09, depth - 0.05,
                          "metal_painted", rail_col)
                    nb = max(3, int((u1 - u0) / 0.16))
                    for t in range(nb + 1):
                        uu = u0 + 0.05 + (u1 - u0 - 0.1) * t / nb
                        _rbox(mb, a, e, out, uu - 0.012, uu + 0.012, yf + 0.02, yf + 0.98, depth - 0.085, depth - 0.07,
                              "metal_painted", rail_col, top=False, bottom=False, sides=False)
                    for uu in (u0 + 0.03, u1 - 0.06):
                        _rbox(mb, a, e, out, uu, uu + 0.03, yf + 0.98, yf + 1.04, 0.0, depth - 0.1, "metal_painted",
                              rail_col)
                        ns = max(2, int(depth / 0.16))
                        for t in range(1, ns):
                            dd = (depth - 0.1) * t / ns
                            _side_bar(mb, a, e, out, uu + 0.015, yf + 0.02, yf + 0.98, dd - 0.012, dd + 0.012,
                                      rail_col, uu < (u0 + u1) * 0.5)
                    if spec.get("style") in ("oldtown", "highrise_classic"):
                        # carved brackets under the slab
                        for uu in (u0 + 0.25, u1 - 0.45):
                            _rbox(mb, a, e, out, uu, uu + 0.2, yf - 0.55, yf - 0.2, 0.0, depth * 0.55, "trim_tint",
                                  slab_col, top=False)
                else:
                    # solid parapet walls
                    _rbox(mb, a, e, out, u0, u1, yf + 0.02, yf + 1.0, depth - 0.12, depth, "trim_tint", slab_col,
                          bottom=False)
                    for (s0, s1) in ((u0, u0 + 0.12), (u1 - 0.12, u1)):
                        _rbox(mb, a, e, out, s0, s1, yf + 0.02, yf + 1.0, 0.0, depth - 0.12, "trim_tint", slab_col,
                              bottom=False)
                    _rbox(mb, a, e, out, u0 - 0.03, u1 + 0.03, yf + 1.0, yf + 1.06, depth - 0.15, depth + 0.03,
                          "concrete", (1, 1, 1, 1))


def gable_roof(mb, poly, y, h, overhang=0.5, mat="roof_tiles", col=(1, 1, 1, 1), hip=False, wall_mat=None, wall_col=None,
               floor_h=3.0):
    """Gable or hip roof over a (near-)rectangular footprint."""
    c, u, v, hu, hv = G.obb(poly)
    if hv > hu:
        u, hu, hv = v, hv, hu
    v = G.right(u)
    hu += overhang
    hv += overhang

    def P(a, b, yy):
        q = G.add(c, G.add(G.mul(u, a), G.mul(v, b)))
        return (q[0], yy, q[1])

    ridge = hu - (hv if hip else 0.0)
    ridge = max(ridge, 0.1)
    A, B, C_, D = P(-hu, -hv, y), P(hu, -hv, y), P(hu, hv, y), P(-hu, hv, y)
    R1, R2 = P(-ridge, 0, y + h), P(ridge, 0, y + h)
    slope_len = math.hypot(hv, h)
    mb.face([A, B, R2, R1], [(0, 0), (2 * hu, 0), (hu + ridge, slope_len), (hu - ridge, slope_len)], mat, col, up=(0, 1, 0))
    mb.face([C_, D, R1, R2], [(0, 0), (2 * hu, 0), (hu + ridge, slope_len), (hu - ridge, slope_len)], mat, col, up=(0, 1, 0))
    if hip:
        mb.face([B, C_, R2], [(0, 0), (2 * hv, 0), (hv, slope_len)], mat, col, up=(0, 1, 0))
        mb.face([D, A, R1], [(0, 0), (2 * hv, 0), (hv, slope_len)], mat, col, up=(0, 1, 0))
    else:
        # gable end walls
        gm = wall_mat or mat
        gc = wall_col or col
        B2, C2 = P(hu - overhang, -hv + overhang, y), P(hu - overhang, hv - overhang, y)
        A2, D2 = P(-hu + overhang, -hv + overhang, y), P(-hu + overhang, hv - overhang, y)
        R2b, R1b = P(hu - overhang, 0, y + h * (hv - overhang) / hv), P(-hu + overhang, 0, y + h * (hv - overhang) / hv)
        wl = 2 * (hv - overhang)
        mb.face([B2, C2, R2b], [(0, y), (wl, y), (wl * 0.5, R2b[1])], gm, gc, up=(u[0], 0, u[1]), uv2=(wl, floor_h))
        mb.face([D2, A2, R1b], [(0, y), (wl, y), (wl * 0.5, R1b[1])], gm, gc, up=(-u[0], 0, -u[1]), uv2=(wl, floor_h))
    # underside of overhang
    mb.face([A, B, C_, D], None, "trim", (0.9, 0.9, 0.9, 1), up=(0, -1, 0))


# ====================================================================== styles
def build(ctx, spec):
    st = spec["style"]
    fn = STYLE_BUILDERS.get(st)
    if fn is None:
        from generate_towers import LANDMARK_BUILDERS
        fn = LANDMARK_BUILDERS.get(st, build_generic)
    fn(ctx, spec)


def _far(ctx, poly, y0, y1, mat, col):
    prism(ctx.far, poly, y0, y1, mat, mat_top="roof_flat", col=col, top=True, uv_mode="facade")


def build_generic(ctx, spec, gf=True):
    poly = [tuple(p) for p in spec["footprint"]]
    y0 = spec["y"]
    H = spec["height"]
    fh = spec["floor_h"]
    fac = "facade_" + spec.get("facade", "plaster")
    col = seed_col(spec)
    rng = ctx.rng
    gf_h = 4.2 if spec.get("shopfront") else 0.0
    if gf_h > 0 and gf:
        names = SHOP_NAMES
        sign = spec.get("name") if spec.get("usage", "generic") != "generic" else (rng.choice(names) if rng.chance(0.6) else None)
        shopfront(ctx, poly, y0, gf_h, spec, col=col, awning=spec.get("awning", False), sign=sign)
    else:
        gf_h = 0.0
    y_top = y0 + H
    setbacks = spec.get("setbacks", 0)
    cur = poly
    ys = y0 + gf_h
    segments = []
    if setbacks and H > 40:
        levels = [ys + (y_top - ys) * f for f in ([0.55, 0.8, 0.92][:setbacks])] + [y_top]
        prev = ys
        for i, lv in enumerate(levels):
            segments.append((cur, prev, lv))
            nxt = G.poly_inset(cur, rng.uniform(2.0, 4.5))
            if not nxt or abs(G.poly_area(nxt)) < 150:
                segments[-1] = (cur, prev, y_top)
                break
            cur = nxt
            prev = lv
    else:
        segments.append((poly, ys, y_top))
    if not spec.get("balconies") and spec["style"] in ("hotel", "midrise") and _h(col[3], 31) < 0.45:
        spec = dict(spec)
        spec["balconies"] = True
    if spec.get("balconies"):
        p0, a0, b0 = segments[0]
        balconies(ctx, p0, a0, b0, fh, spec)
    for (p, a, b) in segments:
        facade_walls(ctx.lod0, p, a, b, fac, col, fh)
        facade_walls(ctx.lod1, p, a, b, fac, col, fh)
        roof_cap(ctx.lod0, p, b)
        roof_cap(ctx.lod1, p, b)
        parapet(ctx.detail, p, b, 0.9 if b - a > 8 else 0.6)
        prism(ctx.col, p, a, b, "concrete", top=True)
    if gf_h == 0:
        # stone plinth along the foot of the building
        ledge(ctx.relief, poly, y0 - 0.4, 0.07, 1.1, mat="concrete")
    else:
        facade_walls(ctx.lod1, poly, y0, y0 + gf_h, "shopfront", (1, 1, 1, 0.5), gf_h)
        if segments and segments[0][0] is poly:
            ledge(ctx.detail, poly, y0 + gf_h - 0.05, 0.3, 0.35)
    if spec.get("cornice"):
        ledge(ctx.detail, poly, y_top - 0.6, 0.45, 0.6)
        y = y0 + gf_h + fh
        while y < y_top - fh:
            ledge(ctx.detail, poly, y - 0.12, 0.08, 0.14)
            y += fh
    last = segments[-1][0]
    roof_equipment(ctx, last, segments[-1][2], rng)
    if H > 70 and rng.chance(0.5):
        c = G.poly_centroid(last)
        cylinder(ctx.detail, (c[0], segments[-1][2], c[1]), 0.25, rng.uniform(8, 25), "metal", segs=6, r_top=0.05)
    _far(ctx, poly, y0, y_top, fac, col)


def build_office(ctx, spec):
    build_generic(ctx, spec)


def build_apartment(ctx, spec):
    build_generic(ctx, spec)


def build_oldtown(ctx, spec):
    spec = dict(spec)
    spec["cornice"] = True
    build_generic(ctx, spec)
    # decorative window sills on the front: small ledges per floor already via cornice


def build_retail(ctx, spec):
    spec = dict(spec)
    spec["shopfront"] = True
    build_generic(ctx, spec)


def build_entertainment(ctx, spec):
    build_generic(ctx, spec)
    rng = ctx.rng
    poly = G.ensure_ccw([tuple(p) for p in spec["footprint"]])
    fi = front_side_index(poly, spec["front"])
    a, b = poly[fi], poly[(fi + 1) % len(poly)]
    e = G.norm(G.sub(b, a))
    out = G.left(e)
    mid = G.lerp(a, b, 0.5)
    y0 = spec["y"]
    st = spec["style"]
    names = {"theater": ["ROYALE", "THE ORPHEUM", "LYRIC", "ODEON"], "cinema": ["CINEMAX", "STARLIGHT", "PICTURE PALACE"],
             "club": ["NOCTURNE", "PULSE", "VOLT", "ECLIPSE", "NEON", "BASSMENT", "AFTERGLOW"]}
    text = rng.choice(names.get(st, ["NEON"]))
    if spec.get("name"):
        text = spec["name"].upper()
    if st in ("theater", "cinema"):
        # marquee canopy
        p = G.add(mid, G.mul(out, 1.6))
        ang = math.atan2(-e[0], -e[1]) + math.pi * 0.5
        box(ctx.detail, (p[0], y0 + 4.6, p[1]), (min(12, G.dist(a, b) * 0.8), 1.2, 3.2), "sign_panel", rot_y=ang, bottom=True)
        box(ctx.detail, (p[0], y0 + 4.6, p[1]), (min(12, G.dist(a, b) * 0.8) + 0.1, 0.3, 3.3), "sign_emissive",
            col=(1.0, 0.85, 0.5, 1), rot_y=ang)
        # vertical blade sign
        q = G.add(G.add(mid, G.mul(e, G.dist(a, b) * 0.3)), G.mul(out, 1.0))
        box(ctx.detail, (q[0], y0 + 9.0, q[1]), (0.4, 7.0, 2.0), "sign_panel", rot_y=ang)
        ctx.signs.append((text, (p[0] + out[0] * 1.66, y0 + 4.6, p[1] + out[1] * 1.66), math.atan2(out[0], out[1]), 0.8,
                          (1.0, 0.85, 0.4)))
    else:
        c = G.add(mid, G.mul(out, 0.15))
        ctx.signs.append((text, (c[0], y0 + spec["height"] * 0.6, c[1]), math.atan2(out[0], out[1]), 1.6,
                          rng.choice([(1.0, 0.2, 0.6), (0.2, 0.9, 1.0), (0.7, 0.3, 1.0), (1.0, 0.5, 0.1)])))


def build_house(ctx, spec):
    poly = G.ensure_ccw([tuple(p) for p in spec["footprint"]])
    y0 = spec["y"]
    H = spec["height"]
    fh = spec["floor_h"]
    fac = "facade_" + spec.get("facade", "stucco_white")
    col = seed_col(spec)
    rng = ctx.rng
    # plinth
    prism(ctx.lod0, poly, y0 - 0.3, y0 + 0.3, "concrete", top=False)
    if H > fh * 1.8 and _h(col[3], 32) < 0.4:
        balconies(ctx, poly, y0 + 0.3, y0 + H, fh, spec, side_filter="front", mat=fac)
    facade_walls(ctx.lod0, poly, y0 + 0.3, y0 + H, fac, col, fh)
    facade_walls(ctx.lod1, poly, y0, y0 + H, fac, col, fh)
    prism(ctx.col, poly, y0, y0 + H, "concrete", top=True)
    roof = spec.get("roof", "gable")
    rc = spec.get("roof_color", (0.5, 0.3, 0.2))
    rcol = (rc[0], rc[1], rc[2], 1.0)
    c, u, v, hu, hv = G.obb(poly)
    rh = min(hu, hv) * 0.75
    if roof in ("gable", "hip"):
        mat = "roof_tiles" if rc[0] > 0.45 else "roof_shingles"
        gable_roof(ctx.lod0, poly, y0 + H, rh, 0.5, mat, rcol, hip=(roof == "hip"), wall_mat=fac, wall_col=col, floor_h=fh)
        gable_roof(ctx.lod1, poly, y0 + H, rh, 0.3, mat, rcol, hip=True)
        gable_roof(ctx.far, poly, y0 + H, rh, 0.0, mat, rcol, hip=True)
        prism(ctx.far, poly, y0, y0 + H, fac, col=col, top=False)
    else:
        roof_cap(ctx.lod0, poly, y0 + H)
        roof_cap(ctx.lod1, poly, y0 + H)
        parapet(ctx.detail, poly, y0 + H, 0.5, 0.25)
        _far(ctx, poly, y0, y0 + H, fac, col)
    # front door + porch
    fi = front_side_index(poly, spec["front"])
    a, b = poly[fi], poly[(fi + 1) % len(poly)]
    e = G.norm(G.sub(b, a))
    out = G.left(e)
    ang = math.atan2(-e[0], -e[1]) + math.pi * 0.5
    dpos = G.add(G.lerp(a, b, 0.35), G.mul(out, 0.05))
    box(ctx.detail, (dpos[0], y0 + 0.3 + 1.05, dpos[1]), (1.0, 2.1, 0.08), "door", rot_y=ang)
    step = G.add(G.lerp(a, b, 0.35), G.mul(out, 0.8))
    box(ctx.detail, (step[0], y0 + 0.15, step[1]), (2.0, 0.3, 1.4), "concrete", rot_y=ang)
    if spec.get("garage"):
        gp = G.add(G.lerp(a, b, 0.78), G.mul(out, 0.04))
        box(ctx.detail, (gp[0], y0 + 1.25, gp[1]), (2.8, 2.3, 0.08), "metal", col=(0.92, 0.92, 0.9, 1), rot_y=ang)
    # chimney
    if roof in ("gable", "hip") and rng.chance(0.4):
        cp = G.add(c, G.mul(u, hu * 0.5))
        box(ctx.detail, (cp[0], y0 + H + rh * 0.7, cp[1]), (0.8, rh * 1.2, 0.8), "facade_brick_red", col=(1, 1, 1, 0))


def build_villa(ctx, spec):
    poly = G.ensure_ccw([tuple(p) for p in spec["footprint"]])
    y0 = spec["y"]
    H = spec["height"]
    col = seed_col(spec)
    rng = ctx.rng
    # plinth down to the terrain
    prism(ctx.lod0, poly, y0 - 4.0, y0 + 0.2, "concrete", top=True)
    prism(ctx.col, poly, y0 - 4.0, y0 + 0.2, "concrete", top=True)
    c, u, v, hu, hv = G.obb(poly)
    # lower volume = full footprint, upper volume shifted cantilever
    h1 = H * 0.5
    facade_walls(ctx.lod0, poly, y0 + 0.2, y0 + h1, "facade_stucco_white", col, 3.3)
    facade_walls(ctx.lod1, poly, y0 + 0.2, y0 + h1, "facade_stucco_white", col, 3.3)
    prism(ctx.col, poly, y0 + 0.2, y0 + h1, "concrete")
    up = G.rect_poly(G.add(c, G.mul(u, hu * 0.25)), u, v, hu * 0.8, hv * 0.9)
    facade_walls(ctx.lod0, up, y0 + h1, y0 + H, "facade_glass_dark", (1, 1, 1, col[3]), 3.3)
    facade_walls(ctx.lod1, up, y0 + h1, y0 + H, "facade_glass_dark", (1, 1, 1, col[3]), 3.3)
    prism(ctx.col, up, y0 + h1, y0 + H, "concrete")
    roof_cap(ctx.lod0, poly, y0 + h1)
    roof_cap(ctx.lod1, poly, y0 + h1)
    # thin overhanging roof slab
    slab = G.rect_poly(G.add(c, G.mul(u, hu * 0.25)), u, v, hu * 0.8 + 1.2, hv * 0.9 + 1.2)
    prism(ctx.lod0, slab, y0 + H, y0 + H + 0.35, "trim", top=True, bottom=True, uv_mode="world")
    prism(ctx.lod1, slab, y0 + H, y0 + H + 0.35, "trim", top=True, bottom=True, uv_mode="world")
    _far(ctx, poly, y0, y0 + H, "facade_stucco_white", col)
    if spec.get("pool"):
        fi = front_side_index(poly, spec["front"])
        back = G.mul(spec["front"], -1)
        pc = G.add(c, G.mul(back, hv + 5.0))
        pr = G.rect_poly(pc, u, v, 5.0, 2.5)
        deck = G.rect_poly(pc, u, v, 7.0, 4.0)
        prism(ctx.lod0, deck, y0 - 3.0, y0 + 0.15, "sidewalk", top=True)
        prism(ctx.col, deck, y0 - 3.0, y0 + 0.15, "concrete", top=True)
        cap_polygon(ctx.detail, pr, y0 + 0.17, "water_deco")


def build_warehouse(ctx, spec):
    poly = G.ensure_ccw([tuple(p) for p in spec["footprint"]])
    y0 = spec["y"]
    H = spec["height"]
    col = seed_col(spec)
    fac = "facade_" + spec.get("facade", "metal_siding")
    rng = ctx.rng
    facade_walls(ctx.lod0, poly, y0, y0 + H, fac, col, 99.0)
    facade_walls(ctx.lod1, poly, y0, y0 + H, fac, col, 99.0)
    prism(ctx.col, poly, y0, y0 + H, "concrete", top=True)
    roof = spec.get("roof", "flat")
    if roof == "sawtooth":
        c, u, v, hu, hv = G.obb(poly)
        if hv > hu:
            u, hu, hv = v, hv, hu
        v = G.right(u)
        n = max(2, int(hu * 2 / 9.0))
        w = hu * 2 / n
        for i in range(n):
            a0 = -hu + i * w
            a1 = a0 + w

            def P(a, b, yy):
                q = G.add(c, G.add(G.mul(u, a), G.mul(v, b)))
                return (q[0], yy, q[1])
            yb = y0 + H
            yt = yb + 3.0
            ctx.lod0.face([P(a0, -hv, yb), P(a0, hv, yb), P(a1, hv, yt), P(a1, -hv, yt)], None, "roof_metal", up=(0, 1, 0))
            ctx.lod0.face([P(a1, -hv, yt), P(a1, hv, yt), P(a1, hv, yb), P(a1, -hv, yb)], None, "window_glass",
                          up=(u[0], 0, u[1]))
            for bb in (-hv, hv):
                ctx.lod0.face([P(a0, bb, yb), P(a1, bb, yb), P(a1, bb, yt)], None, fac, col, up=(0, 0, 1), uv2=(w, 99.0))
        roof_cap(ctx.lod1, poly, y0 + H + 1.5, "roof_metal")
    else:
        roof_cap(ctx.lod0, poly, y0 + H, "roof_metal")
        roof_cap(ctx.lod1, poly, y0 + H, "roof_metal")
        parapet(ctx.detail, poly, y0 + H, 0.6, 0.25, "metal_dark")
        roof_equipment(ctx, poly, y0 + H, rng, 0.5)
    # loading docks with roll-up doors on the front
    fi = front_side_index(poly, spec["front"])
    a, b = poly[fi], poly[(fi + 1) % len(poly)]
    l = G.dist(a, b)
    e = G.norm(G.sub(b, a))
    out = G.left(e)
    ang = math.atan2(-e[0], -e[1]) + math.pi * 0.5
    ndoor = max(1, int(l / 9.0))
    for i in range(ndoor):
        p = G.add(G.add(a, G.mul(e, (i + 0.5) * l / ndoor)), G.mul(out, 0.05))
        box(ctx.detail, (p[0], y0 + 2.4, p[1]), (4.0, 4.6, 0.1), "metal", col=(0.8, 0.82, 0.84, 1), rot_y=ang)
        q = G.add(p, G.mul(out, 1.5))
        box(ctx.detail, (q[0], y0 + 0.6, q[1]), (4.6, 1.2, 3.0), "concrete", rot_y=ang)
        box(ctx.col, (q[0], y0 + 0.6, q[1]), (4.6, 1.2, 3.0), "concrete", rot_y=ang)
    _far(ctx, poly, y0, y0 + H, fac, col)


def build_factory(ctx, spec):
    build_warehouse(ctx, spec)
    rng = ctx.rng
    poly = G.ensure_ccw([tuple(p) for p in spec["footprint"]])
    c = G.poly_centroid(poly)
    y0 = spec["y"]
    for i in range(rng.randint(1, 2)):
        p = G.add(c, (rng.uniform(-6, 6), rng.uniform(-6, 6)))
        h = spec["height"] + rng.uniform(12, 28)
        cylinder(ctx.lod0, (p[0], y0, p[1]), 1.6, h, "facade_brick_red", segs=12, r_top=1.2, col=(1, 1, 1, 0))
        cylinder(ctx.lod1, (p[0], y0, p[1]), 1.6, h, "facade_brick_red", segs=8, r_top=1.2, col=(1, 1, 1, 0))
        cylinder(ctx.far, (p[0], y0, p[1]), 1.6, h, "facade_brick_red", segs=6, r_top=1.2, col=(1, 1, 1, 0))
        box(ctx.col, (p[0], y0 + h * 0.5, p[1]), (3.0, h, 3.0), "concrete")


def build_tanks(ctx, spec):
    poly = G.ensure_ccw([tuple(p) for p in spec["footprint"]])
    c, u, v, hu, hv = G.obb(poly)
    y0 = spec["y"]
    rng = ctx.rng
    r = min(hu, hv) * 0.45
    n = max(1, int(hu / max(r, 1) ))
    for i in range(min(n, 4)):
        p = G.add(c, G.mul(u, -hu + r + i * (2 * hu - 2 * r) / max(1, min(n, 4) - 1))) if n > 1 else c
        h = spec["height"] * rng.uniform(0.8, 1.2)
        cylinder(ctx.lod0, (p[0], y0, p[1]), r, h, "metal", segs=16, col=(0.85, 0.86, 0.85, 1))
        cylinder(ctx.lod0, (p[0], y0 + h, p[1]), r, r * 0.25, "metal", segs=16, r_top=0.3, col=(0.8, 0.8, 0.8, 1))
        cylinder(ctx.lod1, (p[0], y0, p[1]), r, h, "metal", segs=10)
        cylinder(ctx.far, (p[0], y0, p[1]), r, h, "metal", segs=6)
        box(ctx.col, (p[0], y0 + h * 0.5, p[1]), (r * 1.8, h, r * 1.8), "concrete")


def build_construction(ctx, spec):
    """Concrete skeleton: slabs + columns up to floors_built, scaffolding on one side."""
    poly = G.ensure_ccw([tuple(p) for p in spec["footprint"]])
    y0 = spec["y"]
    fh = spec["floor_h"]
    nb = spec.get("floors_built", 6)
    c, u, v, hu, hv = G.obb(poly)
    for f in range(nb + 1):
        y = y0 + f * fh
        slab = G.poly_inset(poly, 0.2) or poly
        prism(ctx.lod0, slab, y - 0.3, y, "concrete", top=True, bottom=True, uv_mode="world")
        prism(ctx.lod1, slab, y - 0.3, y, "concrete", top=True, bottom=True, uv_mode="world")
        prism(ctx.col, slab, y - 0.3, y, "concrete", top=True, bottom=True)
        if f == nb:
            break
        nu = max(2, int(hu * 2 / 7.0))
        nv = max(2, int(hv * 2 / 7.0))
        for i in range(nu + 1):
            for j in range(nv + 1):
                p = G.add(c, G.add(G.mul(u, -hu + 0.6 + i * (2 * hu - 1.2) / nu), G.mul(v, -hv + 0.6 + j * (2 * hv - 1.2) / nv)))
                box(ctx.lod0, (p[0], y + fh * 0.5, p[1]), (0.6, fh, 0.6), "concrete")
                if (i in (0, nu)) or (j in (0, nv)):
                    box(ctx.col, (p[0], y + fh * 0.5, p[1]), (0.6, fh, 0.6), "concrete")
    # elevator core
    core = G.rect_poly(c, u, v, 3.5, 3.5)
    prism(ctx.lod0, core, y0, y0 + (nb + 1) * fh, "concrete", top=True)
    prism(ctx.lod1, core, y0, y0 + (nb + 1) * fh, "concrete", top=True)
    prism(ctx.col, core, y0, y0 + (nb + 1) * fh, "concrete", top=True)
    # scaffolding on the front face
    fi = front_side_index(poly, spec["front"])
    a, b = poly[fi], poly[(fi + 1) % len(poly)]
    e = G.norm(G.sub(b, a))
    out = G.left(e)
    l = G.dist(a, b)
    ang = math.atan2(-e[0], -e[1]) + math.pi * 0.5
    for f in range(nb):
        y = y0 + f * fh
        p = G.add(G.lerp(a, b, 0.5), G.mul(out, 1.0))
        box(ctx.detail, (p[0], y + 0.05, p[1]), (l, 0.1, 1.0), "wood", rot_y=ang)
        box(ctx.detail, (p[0] + out[0] * 0.5, y + 1.0, p[1] + out[1] * 0.5), (l, 0.06, 0.06), "metal", rot_y=ang)
    k = 0.0
    while k <= l:
        p = G.add(G.add(a, G.mul(e, k)), G.mul(out, 1.5))
        box(ctx.detail, (p[0], y0 + nb * fh * 0.5, p[1]), (0.07, nb * fh, 0.07), "metal")
        k += 2.5
    prism(ctx.far, poly, y0, y0 + nb * fh, "concrete", top=True)


def build_small_pavilion(ctx, spec):
    poly = G.ensure_ccw([tuple(p) for p in spec["footprint"]])
    y0 = spec["y"]
    H = spec["height"]
    col = seed_col(spec)
    fac = "facade_" + spec.get("facade", "stucco_white")
    shopfront(ctx, poly, y0, min(H, 4.0), spec, col=col, awning=True,
              sign=spec.get("name") or ctx.rng.choice(SHOP_NAMES), all_sides=False)
    if H > 4.5:
        facade_walls(ctx.lod0, poly, y0 + 4.0, y0 + H, fac, col, 3.4)
    facade_walls(ctx.lod1, poly, y0, y0 + H, fac, col, 3.4)
    roof_cap(ctx.lod0, poly, y0 + H)
    roof_cap(ctx.lod1, poly, y0 + H)
    parapet(ctx.detail, poly, y0 + H, 0.5, 0.2)
    prism(ctx.col, poly, y0, y0 + H, "concrete", top=True)
    _far(ctx, poly, y0, y0 + H, fac, col)


def build_farmhouse(ctx, spec):
    s = dict(spec)
    s["roof"] = "gable"
    s["garage"] = False
    build_house(ctx, s)


def build_barn(ctx, spec):
    s = dict(spec)
    s["roof"] = "gable"
    s["garage"] = False
    poly = G.ensure_ccw([tuple(p) for p in spec["footprint"]])
    y0 = spec["y"]
    H = spec["height"] * 0.6
    col = seed_col(spec)
    facade_walls(ctx.lod0, poly, y0, y0 + H, "facade_wood_siding", col, 99.0)
    facade_walls(ctx.lod1, poly, y0, y0 + H, "facade_wood_siding", col, 99.0)
    prism(ctx.col, poly, y0, y0 + H, "concrete", top=True)
    rc = spec.get("roof_color", (0.35, 0.35, 0.36))
    gable_roof(ctx.lod0, poly, y0 + H, spec["height"] * 0.55, 0.4, "roof_metal", (rc[0], rc[1], rc[2], 1),
               wall_mat="facade_wood_siding", wall_col=col, floor_h=99.0)
    gable_roof(ctx.lod1, poly, y0 + H, spec["height"] * 0.55, 0.2, "roof_metal", (rc[0], rc[1], rc[2], 1), hip=True)
    gable_roof(ctx.far, poly, y0 + H, spec["height"] * 0.55, 0.0, "roof_metal", (rc[0], rc[1], rc[2], 1), hip=True)
    prism(ctx.far, poly, y0, y0 + H, "facade_wood_siding", col=col, top=False)
    fi = front_side_index(poly, spec["front"])
    a, b = poly[fi], poly[(fi + 1) % len(poly)]
    e = G.norm(G.sub(b, a))
    out = G.left(e)
    p = G.add(G.lerp(a, b, 0.5), G.mul(out, 0.05))
    box(ctx.detail, (p[0], y0 + 2.0, p[1]), (4.0, 4.0, 0.1), "wood", col=(0.9, 0.9, 0.9, 1),
        rot_y=math.atan2(-e[0], -e[1]) + math.pi * 0.5)


STYLE_BUILDERS = {
    "office": build_office, "midrise": build_generic, "highrise_classic": build_oldtown, "hotel": build_generic,
    "retail": build_retail, "oldtown": build_oldtown, "apartment": build_apartment, "house": build_house,
    "villa": build_villa, "warehouse": build_warehouse, "factory": build_factory, "tanks": build_tanks,
    "construction": build_construction, "theater": build_entertainment, "club": build_entertainment,
    "cinema": build_entertainment, "marina_shop": build_small_pavilion, "beach_shop": build_small_pavilion,
    "park_cafe": build_small_pavilion, "farmhouse": build_farmhouse, "barn": build_barn,
}
