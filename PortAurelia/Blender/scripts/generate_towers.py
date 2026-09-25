"""Skyscrapers and landmark buildings of Port Aurelia."""
import math

from _common import G, box, cylinder, prism, cap_polygon, sweep
from generate_buildings import (facade_walls, roof_cap, parapet, ledge, roof_equipment, shopfront, seed_col,
                                front_side_index, _far, gable_roof, build_generic)


def _poly(spec):
    return G.ensure_ccw([tuple(p) for p in spec["footprint"]])


def rounded_rect(c, u, v, hu, hv, r, segs=3):
    """Rectangle with rounded (chamfered) corners."""
    r = min(r, hu * 0.9, hv * 0.9)
    pts = []
    corners = [(hu - r, hv - r, 0.0), (-hu + r, hv - r, math.pi * 0.5), (-hu + r, -hv + r, math.pi),
               (hu - r, -hv + r, math.pi * 1.5)]
    for (cu, cv, a0) in corners:
        for k in range(segs + 1):
            a = a0 + (math.pi * 0.5) * k / segs
            pu = cu + math.cos(a) * r
            pv = cv + math.sin(a) * r
            pts.append(G.add(c, G.add(G.mul(u, pu), G.mul(v, pv))))
    return G.ensure_ccw(pts)


def ngon(c, r, n, rot=0.0):
    return G.ensure_ccw([(c[0] + math.cos(rot + 2 * math.pi * i / n) * r, c[1] + math.sin(rot + 2 * math.pi * i / n) * r)
                         for i in range(n)])


def _shaft(ctx, poly, y0, y1, mat, col, fh, lod1=True, colli=True):
    facade_walls(ctx.lod0, poly, y0, y1, mat, col, fh)
    if lod1:
        facade_walls(ctx.lod1, poly, y0, y1, mat, col, fh)
    if colli:
        prism(ctx.col, poly, y0, y1, "concrete", top=True)


def _cap(ctx, poly, y, mat="roof_flat"):
    roof_cap(ctx.lod0, poly, y, mat)
    roof_cap(ctx.lod1, poly, y, mat)


# ====================================================================== generic tower
def build_tower(ctx, spec):
    poly = _poly(spec)
    rng = ctx.rng
    y0 = spec["y"]
    H = spec["height"]
    fh = spec["floor_h"]
    fac = "facade_" + spec.get("facade", "glass_blue")
    col = seed_col(spec, (1.0, 1.0, 1.0))
    c, u, v, hu, hv = G.obb(poly)
    y = y0
    # podium
    if spec.get("podium") and hu > 14 and hv > 14:
        ph = rng.uniform(8.0, 16.0)
        shopfront(ctx, poly, y0, 4.5, spec, col=(0.9, 0.9, 0.9, col[3]), all_sides=True)
        _shaft(ctx, poly, y0 + 4.5, y0 + ph, "facade_stone", (0.85, 0.83, 0.8, col[3]), 4.0)
        _cap(ctx, poly, y0 + ph)
        parapet(ctx.detail, poly, y0 + ph, 1.0, 0.3)
        y = y0 + ph
        shaft = rounded_rect(c, u, v, hu * rng.uniform(0.6, 0.8), hv * rng.uniform(0.6, 0.8), rng.choice([0.0, 3.0, 6.0]))
    else:
        shopfront(ctx, poly, y0, 5.0, spec, col=col, all_sides=True)
        y = y0 + 5.0
        shaft = rounded_rect(c, u, v, hu, hv, rng.choice([0.0, 0.0, 4.0]))
    top = y0 + H
    n_set = spec.get("setbacks", 0)
    levels = [y + (top - y) * f for f in [0.5, 0.72, 0.88][:n_set]] + [top]
    cur = shaft
    prev = y
    for lv in levels:
        _shaft(ctx, cur, prev, lv, fac, col, fh)
        # mechanical band between sections
        if lv < top:
            ledge(ctx.detail, cur, lv - 0.2, 0.25, 0.9, "metal_dark")
            _cap(ctx, cur, lv)
            nxt = G.poly_inset(cur, rng.uniform(2.5, 5.0))
            if not nxt or abs(G.poly_area(nxt)) < 200:
                prev = lv
                break
            cur = nxt
        prev = lv
    _cap(ctx, cur, top)
    _crown(ctx, cur, top, spec.get("crown", "flat"), rng, fac, col)
    _far(ctx, shaft, y0, top, fac, col)


def _crown(ctx, poly, top, kind, rng, fac, col):
    c = G.poly_centroid(poly)
    if kind == "spire":
        parapet(ctx.detail, poly, top, 3.0, 0.4, "metal_dark")
        cylinder(ctx.lod0, (c[0], top, c[1]), 1.2, 6.0, "metal", segs=8, r_top=0.8)
        cylinder(ctx.lod0, (c[0], top + 6.0, c[1]), 0.8, rng.uniform(20, 40), "metal", segs=8, r_top=0.08)
        cylinder(ctx.far, (c[0], top, c[1]), 0.8, 30, "metal", segs=4, r_top=0.08)
    elif kind == "slant":
        # glass crown sloped towards one side
        cc, u, v, hu, hv = G.obb(poly)
        h = min(hu, hv) * 0.9

        def P(a, b, yy):
            q = G.add(cc, G.add(G.mul(u, a), G.mul(v, b)))
            return (q[0], yy, q[1])
        A, B, C_, D = P(-hu, -hv, top), P(hu, -hv, top), P(hu, hv, top), P(-hu, hv, top)
        B2, C2 = P(hu, -hv, top + h), P(hu, hv, top + h)
        for mb in (ctx.lod0, ctx.lod1, ctx.far):
            mb.face([A, B2, C2, D], None, fac, col, up=(0, 1, 0), uv2=(hv * 2, 4.0))
            mb.face([B, C_, C2, B2], [(0, top), (2 * hv, top), (2 * hv, top + h), (0, top + h)], fac, col,
                    up=(u[0], 0, u[1]), uv2=(2 * hv, 4.0))
            mb.face([A, B, B2], [(0, top), (2 * hu, top), (2 * hu, top + h)], fac, col, up=(-v[0], 0, -v[1]), uv2=(2 * hu, 4.0))
            mb.face([C_, D, C2], [(0, top), (2 * hu, top), (0, top + h)], fac, col, up=(v[0], 0, v[1]), uv2=(2 * hu, 4.0))
    elif kind == "crown":
        # stepped crown with lit fins
        cur = poly
        y = top
        for i in range(3):
            nxt = G.poly_inset(cur, 1.8)
            if not nxt:
                break
            facade_walls(ctx.lod0, nxt, y, y + 4.0, "facade_stone", (0.9, 0.88, 0.84, col[3]), 4.0)
            roof_cap(ctx.lod0, nxt, y + 4.0)
            ledge(ctx.detail, nxt, y + 3.6, 0.3, 0.4, "sign_emissive")
            cur = nxt
            y += 4.0
        c2 = G.poly_centroid(cur)
        cylinder(ctx.lod0, (c2[0], y, c2[1]), 0.5, 14.0, "metal", segs=6, r_top=0.05)
    else:
        parapet(ctx.detail, poly, top, 1.6, 0.35, "metal_dark")
        roof_equipment(ctx, poly, top, rng, 0.7)
        if rng.chance(0.4):
            # helipad
            r = min(8.0, math.sqrt(abs(G.poly_area(poly))) * 0.25)
            ring = ngon(c, r, 16)
            cap_polygon(ctx.detail, ring, top + 1.2, "concrete")
            prism(ctx.detail, ring, top, top + 1.2, "metal_dark", top=False)


# ====================================================================== landmarks
def build_aurelia_tower(ctx, spec):
    """330 m flagship: octagonal tapered glass shaft with three setbacks, sky lobby rings,
    observation deck and a lit spire."""
    poly = _poly(spec)
    rng = ctx.rng
    y0 = spec["y"]
    H = spec["height"]
    c, u, v, hu, hv = G.obb(poly)
    col = seed_col(spec, (1, 1, 1))
    fac = "facade_glass_blue"
    # plaza podium
    pod = rounded_rect(c, u, v, hu, hv, 8.0, 4)
    shopfront(ctx, pod, y0, 6.0, spec, col=(0.95, 0.95, 0.95, col[3]), all_sides=True)
    _shaft(ctx, pod, y0 + 6.0, y0 + 18.0, "facade_glass_silver", col, 6.0)
    _cap(ctx, pod, y0 + 18.0)
    parapet(ctx.detail, pod, y0 + 18.0, 1.2, 0.4, "metal_dark")
    r0 = min(hu, hv) * 0.82
    sections = [(y0 + 18.0, y0 + 130.0, r0), (y0 + 130.0, y0 + 215.0, r0 * 0.82),
                (y0 + 215.0, y0 + 275.0, r0 * 0.66), (y0 + 275.0, y0 + 300.0, r0 * 0.52)]
    for i, (a, b, r) in enumerate(sections):
        sh = ngon(c, r, 8, math.pi / 8)
        _shaft(ctx, sh, a, b, fac, col, 4.0)
        _cap(ctx, sh, b)
        ring = ngon(c, r + 0.6, 8, math.pi / 8)
        prism(ctx.detail, ring, b - 2.2, b, "metal_dark", top=True, bottom=True, uv_mode="world")
        prism(ctx.far, sh, a, b, fac, col=col, top=True)
        # vertical fins at the octagon corners
        for k in range(8):
            ang = math.pi / 8 + 2 * math.pi * k / 8
            p = (c[0] + math.cos(ang) * (r + 0.25), c[1] + math.sin(ang) * (r + 0.25))
            box(ctx.detail, (p[0], (a + b) * 0.5, p[1]), (0.35, b - a, 0.35), "metal", rot_y=-ang)
    # observation deck (glass drum) and lit crown
    deck = ngon(c, r0 * 0.6, 16)
    _shaft(ctx, deck, y0 + 300.0, y0 + 308.0, "facade_glass_dark", col, 8.0)
    _cap(ctx, deck, y0 + 308.0)
    ledge(ctx.detail, deck, y0 + 307.0, 0.5, 0.8, "sign_emissive")
    cylinder(ctx.lod0, (c[0], y0 + 308.0, c[1]), 2.0, 8.0, "metal", segs=10, r_top=1.2)
    cylinder(ctx.lod0, (c[0], y0 + 316.0, c[1]), 1.2, H - 316.0 + y0 + 14.0, "metal", segs=8, r_top=0.1)
    cylinder(ctx.far, (c[0], y0 + 308.0, c[1]), 1.5, 36.0, "metal", segs=4, r_top=0.1)
    cylinder(ctx.detail, (c[0], y0 + H + 12.0, c[1]), 0.4, 1.2, "sign_emissive", segs=6, col=(1, 0.2, 0.1, 1))


def build_bank_tower(ctx, spec):
    """Meridian Bank: bronze glass slab with stone pilasters and a stepped art-deco crown."""
    poly = _poly(spec)
    rng = ctx.rng
    y0 = spec["y"]
    H = spec["height"]
    c, u, v, hu, hv = G.obb(poly)
    col = seed_col(spec, (1, 1, 1))
    # classic stone base with colonnade
    base = G.rect_poly(c, u, v, hu, hv)
    shopfront(ctx, base, y0, 7.0, spec, col=(0.9, 0.86, 0.78, col[3]), all_sides=True, sign="MERIDIAN BANK")
    _shaft(ctx, base, y0 + 7.0, y0 + 24.0, "facade_stone", (0.92, 0.88, 0.8, col[3]), 4.2)
    _cap(ctx, base, y0 + 24.0)
    ledge(ctx.detail, base, y0 + 23.0, 0.6, 1.0, "trim")
    shaft = G.rect_poly(c, u, v, hu * 0.72, hv * 0.72)
    top = y0 + H - 30.0
    _shaft(ctx, shaft, y0 + 24.0, top, "facade_glass_bronze", col, 4.0)
    # stone pilasters
    for sgn_u in (-1, 1):
        for sgn_v in (-1, 1):
            p = G.add(c, G.add(G.mul(u, sgn_u * hu * 0.72), G.mul(v, sgn_v * hv * 0.72)))
            box(ctx.lod0, (p[0], (y0 + 24.0 + top) * 0.5, p[1]), (2.4, top - y0 - 24.0, 2.4), "facade_stone",
                col=(0.9, 0.87, 0.8, col[3]))
    prism(ctx.far, shaft, y0, top, "facade_glass_bronze", col=col, top=True)
    cur = shaft
    y = top
    for i, h in enumerate((10.0, 8.0, 6.0, 5.0)):
        nxt = G.poly_inset(cur, 2.4 + i * 0.4)
        if not nxt:
            break
        _shaft(ctx, nxt, y, y + h, "facade_stone", (0.9, 0.87, 0.8, col[3]), 5.0)
        _cap(ctx, nxt, y + h)
        ledge(ctx.detail, nxt, y + h - 0.6, 0.35, 0.6, "sign_emissive", (1.0, 0.8, 0.45, 1))
        prism(ctx.far, nxt, y, y + h, "facade_stone", col=col, top=True)
        cur = nxt
        y += h
    cc = G.poly_centroid(cur)
    cylinder(ctx.lod0, (cc[0], y, cc[1]), 0.7, 12.0, "metal", segs=8, r_top=0.05)


def build_grand_hotel(ctx, spec):
    """Curved sail-like slab facing the sea with continuous balconies."""
    poly = _poly(spec)
    y0 = spec["y"]
    H = spec["height"]
    c, u, v, hu, hv = G.obb(poly)
    if hv > hu:
        u, hu, hv = v, hv, hu
    v = G.right(u)
    col = seed_col(spec, (0.98, 0.97, 0.95))
    # curved footprint (lens)
    pts = []
    n = 14
    for i in range(n + 1):
        t = -1 + 2 * i / n
        pts.append(G.add(c, G.add(G.mul(u, t * hu * 0.95), G.mul(v, hv * 0.25 + hv * 0.35 * (1 - t * t)))))
    for i in range(n + 1):
        t = 1 - 2 * i / n
        pts.append(G.add(c, G.add(G.mul(u, t * hu * 0.95), G.mul(v, -hv * 0.35))))
    lens = G.ensure_ccw(pts)
    shopfront(ctx, lens, y0, 6.0, spec, col=col, all_sides=True)
    _shaft(ctx, lens, y0 + 6.0, y0 + H, "facade_glass_silver", col, 3.4)
    _cap(ctx, lens, y0 + H)
    parapet(ctx.detail, lens, y0 + H, 2.0, 0.4, "trim")
    prism(ctx.far, lens, y0, y0 + H, "facade_glass_silver", col=col, top=True)
    # balcony bands every floor on the curved side
    y = y0 + 9.4
    front = pts[:n + 1]
    while y < y0 + H - 2:
        off = [G.add(p, G.mul(v, 1.0)) for p in front]
        path = [(p[0], y, p[1]) for p in off]
        sweep(ctx.detail, path, [(-0.9, -0.12), (0.9, -0.12), (0.9, 0.0), (-0.9, 0.0)], "trim", closed_profile=True)
        sweep(ctx.detail, [(p[0], y + 1.0, p[2]) for p in [(q[0], 0, q[1]) for q in [G.add(p, G.mul(v, 0.9)) for p in off]]],
              [(-0.03, -1.0), (0.03, -1.0), (0.03, 0.0), (-0.03, 0.0)], "glass_clear", closed_profile=True)
        y += 3.4
    # roof sign
    ctx.signs.append(("GRAND SOLACE", (c[0] + v[0] * (hv * 0.6), y0 + H + 2.5, c[1] + v[1] * (hv * 0.6)),
                      math.atan2(v[0], v[1]), 3.0, (1.0, 0.9, 0.7)))


def build_stadium(ctx, spec):
    poly = _poly(spec)
    y0 = spec["y"]
    c, u, v, hu, hv = G.obb(poly)
    if hv > hu:
        u, hu, hv = v, hv, hu
    v = G.right(u)
    ru, rv = hu * 0.95, hv * 0.95
    H = spec["height"]
    col = seed_col(spec, (0.95, 0.95, 0.96))
    seg = 40

    def ell(scale_u, scale_v, y):
        return [(c[0] + u[0] * math.cos(2 * math.pi * i / seg) * scale_u + v[0] * math.sin(2 * math.pi * i / seg) * scale_v,
                 y,
                 c[1] + u[1] * math.cos(2 * math.pi * i / seg) * scale_u + v[1] * math.sin(2 * math.pi * i / seg) * scale_v)
                for i in range(seg)]
    outer = [(p[0], p[2]) for p in ell(ru, rv, 0)]
    # outer wall with vertical ribs
    _shaft(ctx, G.ensure_ccw(outer), y0, y0 + H * 0.75, "facade_concrete_panel", col, 5.0)
    prism(ctx.far, G.ensure_ccw(outer), y0, y0 + H * 0.75, "facade_concrete_panel", col=col, top=True)
    # tiered stands (inside, sloping down to the pitch)
    tiers = 6
    for t in range(tiers):
        f0 = 0.95 - t * 0.07
        f1 = f0 - 0.07
        ya = y0 + H * 0.72 - t * (H * 0.62 / tiers)
        yb = ya - H * 0.62 / tiers
        A = ell(ru * f0, rv * f0, ya)
        B = ell(ru * f1, rv * f1, yb)
        for i in range(seg):
            j = (i + 1) % seg
            ctx.lod0.face([A[i], A[j], B[j], B[i]], None, "concrete", (0.7, 0.72, 0.75, 1), up=(0, 1, 0))
            ctx.lod0.face([B[i], B[j], (B[j][0], ya, B[j][2]), (B[i][0], ya, B[i][2])], None, "plastic",
                          (0.1 + 0.6 * ((i // 4) % 2), 0.3, 0.7, 1), up=None)
    # pitch
    pitch = [(p[0], p[2]) for p in ell(ru * 0.52, rv * 0.52, 0)]
    cap_polygon(ctx.lod0, pitch, y0 + 0.4, "grass")
    # roof ring
    Ro = ell(ru * 1.0, rv * 1.0, y0 + H)
    Ri = ell(ru * 0.72, rv * 0.72, y0 + H - 2.0)
    for i in range(seg):
        j = (i + 1) % seg
        for mb in (ctx.lod0, ctx.lod1):
            mb.face([Ro[i], Ro[j], Ri[j], Ri[i]], None, "roof_metal", (0.9, 0.9, 0.92, 1), up=(0, 1, 0))
            mb.face([Ri[i], Ri[j], Ro[j], Ro[i]], None, "roof_metal", (0.9, 0.9, 0.92, 1), up=(0, -1, 0))
        if i % 4 == 0:
            p = Ro[i]
            box(ctx.lod0, (p[0], y0 + H * 0.5, p[2]), (0.8, H, 0.8), "metal")
    ring = [(p[0], p[2]) for p in ell(ru, rv, 0)]
    prism(ctx.col, G.ensure_ccw(ring), y0, y0 + H * 0.75, "concrete", top=False)
    ctx.signs.append(("AURELIA ARENA", (c[0] + v[0] * (rv + 0.3), y0 + H * 0.55, c[1] + v[1] * (rv + 0.3)),
                      math.atan2(v[0], v[1]), 3.0, (1.0, 1.0, 1.0)))


def build_convention(ctx, spec):
    poly = _poly(spec)
    y0 = spec["y"]
    H = spec["height"]
    c, u, v, hu, hv = G.obb(poly)
    col = seed_col(spec, (1, 1, 1))
    shopfront(ctx, poly, y0, 7.0, spec, col=col, all_sides=True, sign="CONVENTION CENTER")
    _shaft(ctx, poly, y0 + 7.0, y0 + H - 4, "facade_glass_silver", col, 6.0)
    prism(ctx.far, poly, y0, y0 + H, "facade_glass_silver", col=col, top=True)
    # wavy roof
    n = 12
    if hv > hu:
        u, hu, hv = v, hv, hu
    v = G.right(u)
    for i in range(n):
        a0 = -hu - 2 + i * (2 * hu + 4) / n
        a1 = a0 + (2 * hu + 4) / n
        y_a = y0 + H - 4 + 3.5 * math.sin(i / n * math.pi * 2)
        y_b = y0 + H - 4 + 3.5 * math.sin((i + 1) / n * math.pi * 2)

        def P(a, b, yy):
            q = G.add(c, G.add(G.mul(u, a), G.mul(v, b)))
            return (q[0], yy, q[1])
        for mb in (ctx.lod0, ctx.lod1):
            mb.face([P(a0, -hv - 2, y_a), P(a1, -hv - 2, y_b), P(a1, hv + 2, y_b), P(a0, hv + 2, y_a)], None, "roof_metal",
                    (0.92, 0.92, 0.94, 1), up=(0, 1, 0))
            mb.face([P(a0, hv + 2, y_a - 0.4), P(a1, hv + 2, y_b - 0.4), P(a1, -hv - 2, y_b - 0.4), P(a0, -hv - 2, y_a - 0.4)],
                    None, "trim", up=(0, -1, 0))
    _cap(ctx, poly, y0 + H - 4.1)


def build_mall(ctx, spec):
    poly = _poly(spec)
    y0 = spec["y"]
    H = spec["height"]
    c, u, v, hu, hv = G.obb(poly)
    col = seed_col(spec, (0.95, 0.93, 0.9))
    shopfront(ctx, poly, y0, 6.0, spec, col=col, all_sides=True, sign="GALLERIA PLAZA", awning=False)
    _shaft(ctx, poly, y0 + 6.0, y0 + H, "facade_concrete_panel", col, 5.0)
    _cap(ctx, poly, y0 + H)
    parapet(ctx.detail, poly, y0 + H, 1.4, 0.4, "trim")
    prism(ctx.far, poly, y0, y0 + H, "facade_concrete_panel", col=col, top=True)
    # glass atrium dome/skylight
    sky = G.rect_poly(c, u, v, hu * 0.4, hv * 0.3)
    prism(ctx.lod0, sky, y0 + H, y0 + H + 5.0, "window_glass", top=True)
    roof_equipment(ctx, poly, y0 + H, ctx.rng, 1.2)


def build_parking_garage(ctx, spec):
    poly = _poly(spec)
    y0 = spec["y"]
    H = spec["height"]
    c, u, v, hu, hv = G.obb(poly)
    levels = int(H / 3.0)
    for i in range(levels + 1):
        y = y0 + i * 3.0
        slab = poly
        prism(ctx.lod0, slab, y - 0.35, y, "concrete", top=True, bottom=True, uv_mode="world")
        prism(ctx.lod1, slab, y - 0.35, y, "concrete", top=True, bottom=True, uv_mode="world")
        prism(ctx.col, slab, y - 0.35, y, "concrete", top=True, bottom=True)
        if i < levels:
            # parapet walls (open facade)
            parapet(ctx.lod0, slab, y, 1.0, 0.25, "concrete")
            parapet(ctx.col, slab, y, 1.0, 0.25, "concrete")
            nu = max(2, int(hu * 2 / 8.0))
            nv = max(2, int(hv * 2 / 8.0))
            for a in range(nu + 1):
                for b in range(nv + 1):
                    if a not in (0, nu) and b not in (0, nv):
                        continue
                    p = G.add(c, G.add(G.mul(u, -hu + 0.4 + a * (2 * hu - 0.8) / nu), G.mul(v, -hv + 0.4 + b * (2 * hv - 0.8) / nv)))
                    box(ctx.lod0, (p[0], y + 1.5, p[1]), (0.5, 3.0, 0.5), "concrete")
    prism(ctx.far, poly, y0, y0 + H, "facade_concrete_panel", col=(0.8, 0.8, 0.8, 0.5), top=True)
    # ramp tower
    fi = front_side_index(poly, spec["front"])
    a, b = poly[fi], poly[(fi + 1) % len(poly)]
    out = G.left(G.norm(G.sub(b, a)))
    ctx.signs.append(("P  CITY PARKING", (G.lerp(a, b, 0.5)[0] + out[0] * 0.3, y0 + H - 1.5, G.lerp(a, b, 0.5)[1] + out[1] * 0.3),
                      math.atan2(out[0], out[1]), 1.4, (0.3, 0.7, 1.0)))


def build_police(ctx, spec):
    s = dict(spec)
    s["facade"] = "concrete_panel"
    s["tint"] = (0.85, 0.87, 0.9)
    s["shopfront"] = False
    build_generic(ctx, s, gf=False)
    poly = _poly(spec)
    fi = front_side_index(poly, spec["front"])
    a, b = poly[fi], poly[(fi + 1) % len(poly)]
    e = G.norm(G.sub(b, a))
    out = G.left(e)
    mid = G.lerp(a, b, 0.5)
    y0 = spec["y"]
    ang = math.atan2(-e[0], -e[1]) + math.pi * 0.5
    # blue band + entrance canopy
    ledge(ctx.detail, poly, y0 + 3.6, 0.2, 0.7, "metal_painted", (0.1, 0.25, 0.7, 1))
    p = G.add(mid, G.mul(out, 1.8))
    box(ctx.detail, (p[0], y0 + 3.3, p[1]), (8.0, 0.4, 3.6), "concrete", rot_y=ang, bottom=True)
    box(ctx.detail, (G.add(mid, G.mul(out, 0.05))[0], y0 + 1.2, G.add(mid, G.mul(out, 0.05))[1]), (3.0, 2.4, 0.1),
        "window_glass", rot_y=ang)
    ctx.signs.append(("POLICE", (mid[0] + out[0] * 0.3, y0 + 5.2, mid[1] + out[1] * 0.3), math.atan2(out[0], out[1]), 1.1,
                      (0.4, 0.7, 1.0)))
    # flag pole
    fp = G.add(G.add(mid, G.mul(out, 5.0)), G.mul(e, 6.0))
    cylinder(ctx.detail, (fp[0], y0, fp[1]), 0.08, 10.0, "metal", segs=6)


def build_hospital(ctx, spec):
    s = dict(spec)
    s["facade"] = "stucco_white"
    s["tint"] = (0.97, 0.97, 0.97)
    s["shopfront"] = False
    build_generic(ctx, s, gf=False)
    poly = _poly(spec)
    c = G.poly_centroid(poly)
    y0 = spec["y"]
    H = spec["height"]
    ring = ngon(c, 7.0, 16)
    cap_polygon(ctx.detail, ring, y0 + H + 1.1, "concrete")
    prism(ctx.detail, ring, y0 + H, y0 + H + 1.1, "metal_dark", top=False)
    fi = front_side_index(poly, spec["front"])
    a, b = poly[fi], poly[(fi + 1) % len(poly)]
    out = G.left(G.norm(G.sub(b, a)))
    mid = G.lerp(a, b, 0.5)
    ctx.signs.append(("+ HOSPITAL", (mid[0] + out[0] * 0.3, y0 + H - 3.0, mid[1] + out[1] * 0.3), math.atan2(out[0], out[1]),
                      1.8, (1.0, 0.2, 0.2)))


def build_fire_station(ctx, spec):
    poly = _poly(spec)
    y0 = spec["y"]
    H = spec["height"]
    col = (1, 1, 1, 0.3)
    _shaft(ctx, poly, y0, y0 + H, "facade_brick_red", col, 3.6)
    _cap(ctx, poly, y0 + H)
    parapet(ctx.detail, poly, y0 + H, 0.9, 0.3, "trim")
    prism(ctx.far, poly, y0, y0 + H, "facade_brick_red", col=col, top=True)
    fi = front_side_index(poly, spec["front"])
    a, b = poly[fi], poly[(fi + 1) % len(poly)]
    e = G.norm(G.sub(b, a))
    out = G.left(e)
    l = G.dist(a, b)
    ang = math.atan2(-e[0], -e[1]) + math.pi * 0.5
    for i in range(3):
        p = G.add(G.add(a, G.mul(e, l * (0.2 + 0.3 * i))), G.mul(out, 0.06))
        box(ctx.detail, (p[0], y0 + 2.4, p[1]), (4.2, 4.8, 0.1), "metal_painted", col=(0.8, 0.1, 0.08, 1), rot_y=ang)
    mid = G.lerp(a, b, 0.5)
    ctx.signs.append(("FIRE STATION 3", (mid[0] + out[0] * 0.3, y0 + H - 2.0, mid[1] + out[1] * 0.3), math.atan2(out[0], out[1]),
                      0.9, (1.0, 0.9, 0.8)))
    # hose tower
    tp = G.add(a, G.mul(e, -0.0))
    box(ctx.lod0, (tp[0] - out[0] * 3, y0 + H + 5, tp[1] - out[1] * 3), (4, H + 10, 4), "facade_brick_red", col=col)


def build_gas_station(ctx, spec):
    poly = _poly(spec)
    y0 = spec["y"]
    c, u, v, hu, hv = G.obb(poly)
    front = spec["front"]
    back = G.mul(front, -1)
    side = G.right(back)
    # shop at the back
    shop_c = G.add(c, G.mul(back, max(2.0, hv * 0.55)))
    shop = G.rect_poly(shop_c, side, back, min(10.0, hu * 0.8), 5.0)
    s = dict(spec)
    s["footprint"] = shop
    s["height"] = 4.5
    s["name"] = "FUEL POINT"
    shopfront(ctx, G.ensure_ccw(shop), y0, 4.5, s, col=(0.95, 0.95, 0.95, 0.5), sign="FUEL POINT")
    _cap(ctx, G.ensure_ccw(shop), y0 + 4.5)
    parapet(ctx.detail, G.ensure_ccw(shop), y0 + 4.5, 0.6, 0.2, "metal_painted", (0.9, 0.3, 0.05, 1))
    prism(ctx.col, G.ensure_ccw(shop), y0, y0 + 4.5, "concrete", top=True)
    prism(ctx.far, G.ensure_ccw(shop), y0, y0 + 4.5, "facade_plaster", col=(1, 1, 1, 0.5), top=True)
    # canopy over the pumps
    can_c = G.add(c, G.mul(front, max(2.0, hv * 0.25)))
    canopy = G.rect_poly(can_c, side, back, min(14.0, hu * 0.9), 6.0)
    prism(ctx.lod0, canopy, y0 + 5.2, y0 + 6.2, "metal_painted", col=(0.95, 0.95, 0.95, 1), top=True, bottom=True, uv_mode="world")
    prism(ctx.lod1, canopy, y0 + 5.2, y0 + 6.2, "metal_painted", col=(0.95, 0.95, 0.95, 1), top=True, bottom=True, uv_mode="world")
    ledge(ctx.detail, canopy, y0 + 5.5, 0.05, 0.5, "metal_painted", (0.9, 0.3, 0.05, 1))
    cap_polygon(ctx.detail, G.poly_inset(canopy, 0.5) or canopy, y0 + 5.19, "light_emissive")
    for k in (-0.5, 0.5):
        p = G.add(can_c, G.mul(side, min(14.0, hu * 0.9) * k))
        box(ctx.lod0, (p[0], y0 + 2.6, p[1]), (0.5, 5.2, 0.5), "metal")
        box(ctx.col, (p[0], y0 + 2.6, p[1]), (0.5, 5.2, 0.5), "metal")
        # pump island
        box(ctx.lod0, (p[0], y0 + 0.1, p[1]), (1.2, 0.2, 4.0), "concrete", rot_y=math.atan2(-back[0], -back[1]))
        box(ctx.detail, (p[0], y0 + 0.95, p[1]), (0.7, 1.5, 0.5), "metal_painted", col=(0.9, 0.3, 0.05, 1),
            rot_y=math.atan2(-back[0], -back[1]))
        box(ctx.col, (p[0], y0 + 0.9, p[1]), (1.2, 1.6, 4.0), "concrete", rot_y=math.atan2(-back[0], -back[1]))
    # price pylon
    pp = G.add(G.add(c, G.mul(front, hv * 0.95)), G.mul(side, hu * 0.8))
    box(ctx.detail, (pp[0], y0 + 3.5, pp[1]), (1.6, 7.0, 0.4), "sign_panel", rot_y=math.atan2(-side[0], -side[1]))
    ctx.signs.append(("FUEL", (pp[0] + front[0] * 0.25, y0 + 6.2, pp[1] + front[1] * 0.25), math.atan2(front[0], front[1]),
                      0.5, (1.0, 0.5, 0.1)))


def build_car_dealer(ctx, spec):
    poly = _poly(spec)
    y0 = spec["y"]
    H = spec["height"]
    col = seed_col(spec, (1, 1, 1))
    shopfront(ctx, poly, y0, H - 1.5, spec, col=col, all_sides=True, sign="AURELIA MOTORS")
    _shaft(ctx, poly, y0 + H - 1.5, y0 + H, "facade_metal_siding", (0.2, 0.22, 0.25, col[3]), 99.0)
    _cap(ctx, poly, y0 + H)
    prism(ctx.far, poly, y0, y0 + H, "facade_glass_dark", col=col, top=True)


def build_school(ctx, spec):
    s = dict(spec)
    s["facade"] = "brick_red"
    s["shopfront"] = False
    s["cornice"] = True
    build_generic(ctx, s, gf=False)


def build_tv_tower(ctx, spec):
    """Concrete TV tower: tapering shaft, observation pod with windows, antenna mast."""
    poly = _poly(spec)
    c = G.poly_centroid(poly)
    y0 = spec["y"]
    H = spec["height"]
    # base building
    base = ngon(c, 12.0, 12)
    _shaft(ctx, base, y0 - 2.0, y0 + 6.0, "facade_concrete_panel", (0.85, 0.85, 0.85, 0.3), 4.0)
    _cap(ctx, base, y0 + 6.0)
    shaft_h = H * 0.62
    for mb in (ctx.lod0, ctx.lod1, ctx.far):
        cylinder(mb, (c[0], y0, c[1]), 5.5, shaft_h, "concrete", segs=20 if mb is ctx.lod0 else 10, r_top=3.2,
                 uv_v_scale=0.25)
    box(ctx.col, (c[0], y0 + shaft_h * 0.5, c[1]), (8.0, shaft_h, 8.0), "concrete")
    py = y0 + shaft_h
    # pod: lower cone, glass drum, upper cone
    for mb, segs in ((ctx.lod0, 28), (ctx.lod1, 14), (ctx.far, 8)):
        cylinder(mb, (c[0], py, c[1]), 3.2, 6.0, "concrete", segs=segs, r_top=15.0)
        cylinder(mb, (c[0], py + 6.0, c[1]), 15.0, 7.0, "facade_glass_dark", segs=segs, col=(1, 1, 1, 0.7))
        cylinder(mb, (c[0], py + 13.0, c[1]), 15.0, 5.0, "concrete", segs=segs, r_top=6.0, top=True)
    cylinder(ctx.detail, (c[0], py + 9.0, c[1]), 15.3, 0.4, "sign_emissive", segs=28, col=(1.0, 0.85, 0.6, 1))
    # mast
    mast_y = py + 18.0
    cylinder(ctx.lod0, (c[0], mast_y, c[1]), 2.2, 20.0, "metal_painted", segs=10, r_top=1.4, col=(0.9, 0.2, 0.15, 1))
    cylinder(ctx.lod0, (c[0], mast_y + 20.0, c[1]), 1.0, H - (mast_y - y0) - 20.0, "metal_painted", segs=8, r_top=0.15,
             col=(0.92, 0.92, 0.92, 1))
    cylinder(ctx.far, (c[0], mast_y, c[1]), 1.5, H - (mast_y - y0), "metal", segs=4, r_top=0.15)
    cylinder(ctx.detail, (c[0], y0 + H, c[1]), 0.5, 1.0, "sign_emissive", segs=6, col=(1, 0.1, 0.05, 1))


def build_observation_tower(ctx, spec):
    poly = _poly(spec)
    c = G.poly_centroid(poly)
    y0 = spec["y"]
    H = spec["height"]
    for mb, segs in ((ctx.lod0, 16), (ctx.lod1, 10), (ctx.far, 6)):
        cylinder(mb, (c[0], y0 - 4.0, c[1]), 4.0, H - 12.0 + 4.0, "facade_stucco_white", segs=segs, r_top=2.5,
                 col=(0.98, 0.98, 0.98, 0.2), uv_v_scale=1.0)
        cylinder(mb, (c[0], y0 + H - 12.0, c[1]), 2.5, 2.0, "concrete", segs=segs, r_top=7.0)
        cylinder(mb, (c[0], y0 + H - 10.0, c[1]), 7.0, 5.0, "facade_glass_dark", segs=segs, col=(1, 1, 1, 0.6))
        cylinder(mb, (c[0], y0 + H - 5.0, c[1]), 7.2, 1.0, "trim", segs=segs, r_top=4.0, top=True)
    cylinder(ctx.lod0, (c[0], y0 + H - 4.0, c[1]), 0.3, 6.0, "metal", segs=6, r_top=0.05)
    cylinder(ctx.detail, (c[0], y0 + H - 7.5, c[1]), 7.3, 0.3, "sign_emissive", segs=16, col=(0.6, 0.85, 1.0, 1))
    box(ctx.col, (c[0], y0 + H * 0.5 - 2, c[1]), (6, H, 6), "concrete")


def build_airport_terminal(ctx, spec):
    poly = _poly(spec)
    y0 = spec["y"]
    H = spec["height"]
    c, u, v, hu, hv = G.obb(poly)
    if hv > hu:
        u, hu, hv = v, hv, hu
    v = G.right(u)
    col = seed_col(spec, (1, 1, 1))
    shopfront(ctx, poly, y0, 7.0, spec, col=col, all_sides=True, sign="AURELIA INTERNATIONAL")
    _shaft(ctx, poly, y0 + 7.0, y0 + H - 6.0, "facade_glass_silver", col, 7.0)
    prism(ctx.far, poly, y0, y0 + H, "facade_glass_silver", col=col, top=True)
    # curved roof along the long axis
    n = 10
    for i in range(n):
        b0 = -hv - 4 + i * (2 * hv + 8) / n
        b1 = b0 + (2 * hv + 8) / n
        ya = y0 + H - 6 + 6.0 * math.sin((i / n) * math.pi)
        yb = y0 + H - 6 + 6.0 * math.sin(((i + 1) / n) * math.pi)

        def P(a, b, yy):
            q = G.add(c, G.add(G.mul(u, a), G.mul(v, b)))
            return (q[0], yy, q[1])
        for mb in (ctx.lod0, ctx.lod1):
            mb.face([P(-hu - 3, b0, ya), P(hu + 3, b0, ya), P(hu + 3, b1, yb), P(-hu - 3, b1, yb)], None, "roof_metal",
                    (0.9, 0.92, 0.94, 1), up=(0, 1, 0))
            mb.face([P(-hu - 3, b1, yb - 0.5), P(hu + 3, b1, yb - 0.5), P(hu + 3, b0, ya - 0.5), P(-hu - 3, b0, ya - 0.5)],
                    None, "trim", up=(0, -1, 0))
    _cap(ctx, poly, y0 + H - 6.1)
    # jet bridges on the airside (+x)
    for k in (-0.7, -0.35, 0.0, 0.35, 0.7):
        p = G.add(c, G.add(G.mul(u, k * hu), G.mul(v, hv)))
        q = G.add(p, G.mul(v, 22.0))
        mid = G.lerp(p, q, 0.5)
        ang = math.atan2(-v[0], -v[1])
        box(ctx.lod0, (mid[0], y0 + 5.0, mid[1]), (3.2, 3.0, 22.0), "metal", col=(0.8, 0.82, 0.85, 1), rot_y=ang)
        box(ctx.lod0, (q[0], y0 + 1.8, q[1]), (1.0, 3.6, 1.0), "metal_dark", rot_y=ang)


def build_control_tower(ctx, spec):
    poly = _poly(spec)
    c = G.poly_centroid(poly)
    y0 = spec["y"]
    H = spec["height"]
    for mb, segs in ((ctx.lod0, 12), (ctx.lod1, 8), (ctx.far, 6)):
        cylinder(mb, (c[0], y0, c[1]), 3.2, H - 10.0, "concrete", segs=segs, r_top=2.6)
        cylinder(mb, (c[0], y0 + H - 10.0, c[1]), 2.6, 2.0, "concrete", segs=segs, r_top=6.5)
        cylinder(mb, (c[0], y0 + H - 8.0, c[1]), 6.5, 5.0, "facade_glass_dark", segs=segs, r_top=7.2, col=(1, 1, 1, 0.8))
        cylinder(mb, (c[0], y0 + H - 3.0, c[1]), 7.4, 1.0, "trim", segs=segs, r_top=5.0, top=True)
    cylinder(ctx.lod0, (c[0], y0 + H - 2.0, c[1]), 0.2, 6.0, "metal", segs=6)
    box(ctx.col, (c[0], y0 + H * 0.5, c[1]), (6, H, 6), "concrete")


def build_hangar(ctx, spec):
    poly = _poly(spec)
    y0 = spec["y"]
    H = spec["height"]
    c, u, v, hu, hv = G.obb(poly)
    col = seed_col(spec, (0.8, 0.82, 0.85))
    n = 12
    arc_pts = []
    for i in range(n + 1):
        a = math.pi * i / n
        arc_pts.append((math.cos(a) * hv, math.sin(a) * H))
    for mb in (ctx.lod0, ctx.lod1, ctx.far):
        for i in range(n):
            (b0, y_0), (b1, y_1) = arc_pts[i], arc_pts[i + 1]

            def P(a, b, yy):
                q = G.add(c, G.add(G.mul(u, a), G.mul(v, b)))
                return (q[0], y0 + yy, q[1])
            mb.face([P(-hu, b0, y_0), P(hu, b0, y_0), P(hu, b1, y_1), P(-hu, b1, y_1)],
                    [(0, i), (2 * hu, i), (2 * hu, i + 1), (0, i + 1)], "roof_metal", col, up=None)
        # end walls
        for sgn in (-1, 1):
            ring = []
            for (b, yy) in arc_pts:
                q = G.add(c, G.add(G.mul(u, sgn * hu), G.mul(v, b)))
                ring.append((q[0], y0 + yy, q[1]))
            mb.face(ring, None, "facade_metal_siding", col, up=(u[0] * sgn, 0, u[1] * sgn), uv2=(hv * 2, 99.0))
    prism(ctx.col, G.rect_poly(c, u, v, hu, hv), y0, y0 + H * 0.8, "concrete", top=True)


LANDMARK_BUILDERS = {
    "tower": build_tower,
    "landmark_aurelia_tower": build_aurelia_tower,
    "landmark_bank_tower": build_bank_tower,
    "landmark_grand_hotel": build_grand_hotel,
    "landmark_stadium": build_stadium,
    "landmark_convention": build_convention,
    "landmark_mall": build_mall,
    "parking_garage": build_parking_garage,
    "police_station": build_police,
    "hospital": build_hospital,
    "fire_station": build_fire_station,
    "gas_station": build_gas_station,
    "car_dealer": build_car_dealer,
    "school": build_school,
    "landmark_tv_tower": build_tv_tower,
    "landmark_observation_tower": build_observation_tower,
    "airport_terminal": build_airport_terminal,
    "control_tower": build_control_tower,
    "hangar": build_hangar,
}
