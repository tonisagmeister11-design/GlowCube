"""Parametric vehicle generator: 16 original vehicles.

Body = high-resolution loft (see _carbody.py): bulging sides with character crease and
shoulder, flared wheel arches, rounded nose/tail, crowned hood/roof, tumblehome glass with
black window seals and B-pillars, shader-drawn panel gaps. The lower nose/tail become the
detachable bumpers. Lamps, grilles, intakes, plates and handles are decals projected flush
onto the body; mirrors, wipers, exhausts, fin antenna, spoilers; interior with seats, dash,
steering wheel, headliner and door cards; wheels with shaped tyres and alloy rims.
Light meshes: head / brake / reverse / turn L / turn R / siren; wheels pivot at their centre.

Output per vehicle: Game/assets/generated/vehicles/<id>.glb
Metadata:           Game/assets/generated/vehicles/vehicles_meta.json
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from _common import GEN, MeshBuilder, reset_scene, clear_objects, export_glb, box, cylinder, sweep, add_signs  # noqa: E402
from _shapes import extrude, slab, bbox, lathe, disc, tube, rrect, smooth_loop  # noqa: E402
from _carbody import CarBody, rrect2, circ2  # noqa: E402

OUT = os.path.join(GEN, "vehicles")

# id: parameters (metres). style: hatch | sedan | coupe | super | suv | pickup | van | truck | bus
VEHICLES = {
    "compact":   dict(name="Pico", style="hatch", L=3.85, W=1.72, H=1.46, WB=2.46, R=0.31, gc=0.16, hood=0.86,
                      ws=0.30, rs=0.46, re=0.86, rw=0.97, rear=0.92, paint="civil"),
    "sedan":     dict(name="Meridian", style="sedan", L=4.72, W=1.82, H=1.45, WB=2.76, R=0.33, gc=0.16, hood=0.9,
                      ws=0.31, rs=0.44, re=0.70, rw=0.82, rear=0.98, paint="civil"),
    "luxury":    dict(name="Regent", style="sedan", L=5.12, W=1.92, H=1.48, WB=3.02, R=0.35, gc=0.15, hood=0.94,
                      ws=0.33, rs=0.46, re=0.71, rw=0.82, rear=1.0, paint="civil", chrome=True),
    "sports":    dict(name="Vento GT", style="coupe", L=4.45, W=1.88, H=1.27, WB=2.58, R=0.34, gc=0.12, hood=0.78,
                      ws=0.36, rs=0.52, re=0.66, rw=0.86, rear=0.88, paint="civil", spoiler=True),
    "supercar":  dict(name="Stratos X", style="super", L=4.62, W=2.02, H=1.13, WB=2.72, R=0.35, gc=0.1, hood=0.66,
                      ws=0.30, rs=0.47, re=0.56, rw=0.9, rear=0.85, paint="civil", spoiler=True),
    "suv":       dict(name="Terra", style="suv", L=4.85, W=1.96, H=1.78, WB=2.86, R=0.38, gc=0.22, hood=1.08,
                      ws=0.28, rs=0.40, re=0.93, rw=0.985, rear=1.22, paint="civil"),
    "pickup":    dict(name="Ranchero", style="pickup", L=5.35, W=2.0, H=1.86, WB=3.25, R=0.4, gc=0.24, hood=1.15,
                      ws=0.27, rs=0.36, re=0.52, rw=0.55, rear=1.05, paint="civil"),
    "van":       dict(name="Porter", style="van", L=5.05, W=2.02, H=2.22, WB=3.15, R=0.35, gc=0.18, hood=1.1,
                      ws=0.10, rs=0.2, re=0.98, rw=0.995, rear=2.1, paint="civil"),
    "truck":     dict(name="Hauler", style="truck", L=7.6, W=2.42, H=3.4, WB=4.25, R=0.5, gc=0.3, hood=1.6,
                      ws=0.07, rs=0.13, re=0.26, rw=0.27, rear=3.3, paint="work"),
    "bus":       dict(name="CityLiner", style="bus", L=12.0, W=2.55, H=3.1, WB=6.5, R=0.5, gc=0.3, hood=2.9,
                      ws=0.02, rs=0.04, re=0.985, rw=0.995, rear=3.0, paint="bus"),
    "taxi":      dict(base="sedan", paint="taxi", sign="TAXI"),
    "police":    dict(base="sedan", paint="police", lightbar=True, name="Interceptor"),
    "ambulance": dict(base="van", paint="ambulance", lightbar=True, name="Medic One"),
    "fire_truck": dict(base="truck", paint="fire", lightbar=True, ladder=True, name="Engine 3"),
    "delivery":  dict(base="van", paint="delivery", name="Porter Cargo"),
    "motorcycle": dict(name="Falco 600", style="bike", L=2.1, W=0.75, H=1.15, WB=1.42, R=0.31, gc=0.14, paint="civil"),
}


def resolve(vid):
    p = dict(VEHICLES[vid])
    if "base" in p:
        base = dict(VEHICLES[p["base"]])
        base.update({k: v for k, v in p.items() if k != "base"})
        p = base
    return p


# ------------------------------------------------------------------ profiles
def top_at(p, f):
    """Height of the body top at normalised length position f (0 front .. 1 rear)."""
    H, hood, rear = p["H"], p["hood"], p["rear"]
    ws, rs, re, rw = p["ws"], p["rs"], p["re"], p["rw"]
    st = p["style"]
    if st not in ("van", "truck", "bus") and f < ws:
        # hood slopes down to a lower, rounded nose
        t = min(1.0, f / (ws * 0.85))
        return hood * (0.8 + 0.2 * (1 - (1 - t) ** 2) + 0.03 * max(0.0, (f - ws * 0.85) / (ws * 0.15)))
    if f < 0.035:
        return hood * (0.78 + 0.22 * f / 0.035)
    if f < ws:
        return hood * (1.0 + 0.03 * (f - 0.035) / max(ws - 0.035, 1e-3))
    if f < rs:
        t = (f - ws) / (rs - ws)
        return hood * 1.03 + (H - hood * 1.03) * math.sin(t * math.pi * 0.5) ** 0.8
    if f < re:
        return H
    if f < rw:
        t = (f - re) / (rw - re)
        return H + (rear - H) * t ** (1.2 if st in ("sedan", "coupe", "super") else 0.6)
    if st in ("pickup",):
        return rear
    t = (f - rw) / max(1.0 - rw, 1e-3)
    return rear - (rear - rear * 0.85) * t ** 3


def width_at(p, f, y_rel):
    """Half width at length position f and relative height (0 = bottom, 1 = top)."""
    W = p["W"] * 0.5
    nose = 1.0 - (1.0 - min(1.0, f / 0.08)) ** 2 * 0.22
    tail = 1.0 - (1.0 - min(1.0, (1.0 - f) / 0.06)) ** 2 * 0.15
    return W * nose * tail


# ------------------------------------------------------------------ liveries
def liveries(kind):
    white = (0.95, 0.95, 0.95, 1)
    if kind == "police":
        def lv(f, y, c):
            if 0.3 < f < 0.72 and y < 0.95:
                return (0.06, 0.07, 0.12, 1)
            return white
        return lv
    if kind == "taxi":
        return lambda f, y, c: (0.98, 0.78, 0.05, 1) if y > 0.55 or f < 0.2 or f > 0.85 else (0.95, 0.72, 0.05, 1)
    if kind == "ambulance":
        return lambda f, y, c: (0.85, 0.08, 0.08, 1) if 0.7 < y < 0.95 else white
    if kind == "fire":
        return lambda f, y, c: (0.75, 0.06, 0.05, 1) if y > 0.9 else (0.12, 0.12, 0.12, 1) if y < 0.6 else (0.75, 0.06, 0.05, 1)
    if kind == "bus":
        return lambda f, y, c: (0.95, 0.52, 0.1, 1) if y < 1.4 or y > 2.9 else white
    if kind == "delivery":
        return lambda f, y, c: (0.2, 0.45, 0.25, 1) if y < 0.8 else white
    if kind == "work":
        return lambda f, y, c: white if f > 0.3 else (0.2, 0.3, 0.6, 1)
    # civilian: white vertex colour, per-instance paint via shader
    return lambda f, y, c: (1, 1, 1, 1)


# ------------------------------------------------------------------ builders
def _flipped(mb):
    """Same faces with reversed winding (inward-facing barrel etc.)."""
    out = MeshBuilder()
    for i, f in enumerate(mb.faces):
        pts = [mb.verts[k] for k in reversed(f)]
        out.face(pts, list(reversed(mb.uvs[i])), mb.mats[mb.fmat[i]], mb.cols[i][0])
    return out


def wheel_mesh(R, width, rim_style=0):
    """Tyre with rounded shoulders, bulged sidewalls and three tread grooves; alloy rim with
    lip, barrel, concave spokes (4 styles), hub, lug nuts and a brake disc. Outer face at +X."""
    mb = MeshBuilder()
    hw = width * 0.5
    segs = 32
    prof = [(-hw * 0.9, R * 0.655), (-hw * 1.02, R * 0.74), (-hw * 1.055, R * 0.84), (-hw * 1.0, R * 0.93),
            (-hw * 0.86, R * 0.982), (-hw * 0.7, R)]
    for gx in (-0.36, 0.0, 0.36):
        g = min(0.006, hw * 0.06)
        prof += [(hw * gx - g, R), (hw * gx - g, R - 0.008), (hw * gx + g, R - 0.008), (hw * gx + g, R)]
    prof += [(hw * 0.7, R), (hw * 0.86, R * 0.982), (hw * 1.0, R * 0.93), (hw * 1.055, R * 0.84),
             (hw * 1.02, R * 0.74), (hw * 0.9, R * 0.655)]
    arc = [0.0]
    for a, b in zip(prof[:-1], prof[1:]):
        arc.append(arc[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
    tot = arc[-1]

    def TP(x, r, t):
        a = math.tau * t / segs
        return (x, math.sin(a) * r, math.cos(a) * r)

    for i in range(len(prof) - 1):
        (x0, r0), (x1, r1) = prof[i], prof[i + 1]
        for t in range(segs):
            q = [TP(x0, r0, t), TP(x1, r1, t), TP(x1, r1, t + 1), TP(x0, r0, t + 1)]
            uv = [(arc[i] / tot, t / segs), (arc[i + 1] / tot, t / segs), (arc[i + 1] / tot, (t + 1) / segs),
                  (arc[i] / tot, (t + 1) / segs)]
            am = math.tau * (t + 0.5) / segs
            dx, dr = x1 - x0, r1 - r0
            ln = math.hypot(dx, dr) or 1.0
            up = (-dr / ln, math.sin(am) * dx / ln, math.cos(am) * dx / ln)
            mb.face(q, uv, "tire", up=up)
    wc = (1, 1, 1, 1)
    rr = R * 0.64
    # rim lip + barrel (barrel faces inwards, visible between the spokes)
    lathe(mb, [(hw * 0.52, R * 0.655), (hw * 0.64, R * 0.672), (hw * 0.7, R * 0.645), (hw * 0.66, rr * 0.97)],
          "rim", segs, (0.0, 0.0), "x", wc, cap0=False, cap1=False)
    bar = MeshBuilder()
    lathe(bar, [(-hw * 0.88, rr), (hw * 0.62, rr * 0.97)], "rim", segs, (0.0, 0.0), "x", (0.55, 0.55, 0.55, 1),
          cap0=False, cap1=False)
    mb.merge(_flipped(bar))
    # back plate (dark) so the rim never looks hollow
    disc(mb, (-hw * 0.3, 0.0, 0.0), rr * 0.98, "metal_dark", "x", 1.0, segs, (0.3, 0.3, 0.3, 1))
    # spokes
    style = rim_style % 4
    n, w_h, w_r = [(5, 0.05, 0.032), (10, 0.024, 0.018), (6, 0.058, 0.05), (7, 0.036, 0.026)][style]
    x_hub, x_rim, depth = hw * 0.34, hw * 0.6, 0.028
    rh = R * 0.2
    for kk in range(n):
        for split in ((-0.09, 0.09) if style == 0 else (0.0,)):
            a = math.tau * kk / n + split
            twist = 0.18 if style == 3 else 0.0

            def S(r, off, x, ang=a):
                aa = ang + (twist * (r - rh) / (rr - rh))
                ca, sa = math.cos(aa), math.sin(aa)
                return (x, sa * r + ca * off, ca * r - sa * off)

            wh, wr = (w_h * 0.55, w_r * 0.6) if style == 0 else (w_h, w_r)
            hub_o = [S(rh, -wh / 2, x_hub), S(rh, wh / 2, x_hub)]
            rim_o = [S(rr * 0.98, -wr / 2, x_rim), S(rr * 0.98, wr / 2, x_rim)]
            hub_i = [S(rh, -wh / 2, x_hub - depth), S(rh, wh / 2, x_hub - depth)]
            rim_i = [S(rr * 0.98, -wr / 2, x_rim - depth * 0.8), S(rr * 0.98, wr / 2, x_rim - depth * 0.8)]
            mid = S((rh + rr) * 0.5, 0.0, (x_hub + x_rim) * 0.5)
            radial = (0.0, mid[1], mid[2])
            mb.face([hub_o[0], rim_o[0], rim_o[1], hub_o[1]], None, "rim", wc, up=(1, 0, 0))
            mb.face([hub_o[0], hub_i[0], rim_i[0], rim_o[0]], None, "rim", (0.7, 0.7, 0.7, 1),
                    up=_sub3(hub_o[0], hub_o[1]))
            mb.face([hub_o[1], rim_o[1], rim_i[1], hub_i[1]], None, "rim", (0.7, 0.7, 0.7, 1),
                    up=_sub3(hub_o[1], hub_o[0]))
    # hub, centre cap, lug nuts, brake disc
    lathe(mb, [(x_hub - depth, rh * 1.05), (x_hub + 0.004, rh * 1.05), (x_hub + 0.012, rh * 0.8),
               (x_hub + 0.016, rh * 0.45), (x_hub + 0.017, 0.0)], "rim", 20, (0.0, 0.0), "x", wc, cap0=False)
    disc(mb, (x_hub + 0.0175, 0.0, 0.0), rh * 0.36, "car_trim", "x", 1.0, 16, (0.05, 0.05, 0.05, 1))
    for kk in range(5):
        a = math.tau * kk / 5 + 0.3
        lathe(mb, [(x_hub + 0.012, 0.0085), (x_hub + 0.024, 0.0085), (x_hub + 0.027, 0.0055), (x_hub + 0.028, 0.0)],
              "chrome", 6, (math.cos(a) * rh * 0.6, math.sin(a) * rh * 0.6), "x", wc, cap0=False)
    lathe(mb, [(-hw * 0.12, R * 0.26), (-hw * 0.12, R * 0.5), (hw * 0.02, R * 0.5), (hw * 0.02, R * 0.26)], "metal",
          segs, (0.0, 0.0), "x", (0.6, 0.6, 0.6, 1), cap0=False, cap1=False)
    return mb


def _sub3(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _mirror_outline(poly):
    return [(-x, y) for (x, y) in poly]


def build_car(vid, p):
    paint = p.get("paint", "civil")
    fixed = paint != "civil"
    lv = liveries(paint)
    pm = "car_paint_vc" if fixed else "car_paint"
    cb = CarBody(p, top_at, lv, pm)
    body, glass, inner = cb.build()
    L, W, H, gc, hood, R = p["L"], p["W"], p["H"], p["gc"], p["hood"], p["R"]
    st = p["style"]
    boxy = st in ("van", "truck", "bus")
    hw = W * 0.5
    zf, zr = -L * 0.5, L * 0.5
    lights = {k: MeshBuilder() for k in ("LightHead", "LightBrake", "LightReverse", "LightTurnL", "LightTurnR", "Siren")}
    detail = MeshBuilder()
    bf, br = cb.bf, cb.br
    interior = MeshBuilder()
    interior.merge(inner)
    W1 = (1, 1, 1, 1)
    DK = (0.05, 0.05, 0.05, 1)
    lux = bool(p.get("chrome"))
    sporty = st in ("coupe", "super")
    fr = cb.section_info(0.0)
    rr_ = cb.section_info(1.0)
    # ---------------------------------------------------------------- front: lamps, grille, intake, plate
    ly = (fr["belt"] - 0.07) if not boxy else gc + 0.6
    if st == "bus":
        ly = gc + 0.55
    x0, x1 = fr["hw"] * (0.38 if not boxy else 0.55), fr["hw"] * 0.94
    lh = 0.06 if not boxy else 0.08
    for sx in (1, -1):
        def M(poly):
            return poly if sx > 0 else _mirror_outline(poly)
        lamp = [(x0, ly - lh * 0.55), (x1 - 0.06, ly - lh), (x1, ly - lh * 0.4), (x1 - 0.01, ly + lh * 0.8),
                (x0 + 0.03, ly + lh * 0.9)]
        cb.decal(detail, M(lamp), "z", -1, 0.003, "metal_dark", DK)
        for t, rp in ((0.34, 0.026), (0.62, 0.026)):
            cx = x0 + (x1 - x0) * t
            cb.decal(detail, M(circ2(cx, ly + 0.006, rp + 0.008)), "z", -1, 0.005, "chrome", W1)
            cb.decal(lights["LightHead"], M(circ2(cx, ly + 0.006, rp)), "z", -1, 0.0068, "car_light", W1)
        drl = [(x0 + 0.02, ly - lh * 0.62), (x1 - 0.07, ly - lh * 0.93), (x1 - 0.025, ly - lh * 0.5),
               (x1 - 0.035, ly - lh * 0.25), (x0 + 0.03, ly - lh * 0.37)]
        cb.decal(lights["LightHead"], M(drl), "z", -1, 0.0068, "car_light", W1)
        turn = [(x1 - 0.034, ly - lh * 0.3), (x1 - 0.006, ly - lh * 0.34), (x1 - 0.01, ly + lh * 0.6),
                (x1 - 0.034, ly + lh * 0.62)]
        cb.decal(lights["LightTurnR" if sx > 0 else "LightTurnL"], M(turn), "z", -1, 0.0068, "car_light", W1)
        # fog lamp
        fy = gc + 0.22 if not boxy else gc + 0.35
        cb.decal(detail, M(circ2(fr["hw"] * 0.74, fy, 0.042)), "z", -1, 0.004, "chrome", W1)
        cb.decal(lights["LightHead"], M(circ2(fr["hw"] * 0.74, fy, 0.032)), "z", -1, 0.006, "car_light", W1)
    gx = fr["hw"] * (0.33 if not boxy else 0.5)
    gy0, gy1 = (ly - 0.13, ly + 0.03) if not boxy else (ly - 0.05, ly + 0.35)
    if st == "bus":
        gy0, gy1 = gc + 0.3, gc + 0.45
    grille = [(-gx, gy1), (gx, gy1), (gx + 0.03, (gy0 + gy1) * 0.5), (gx - 0.03, gy0), (-gx + 0.03, gy0),
              (-gx - 0.03, (gy0 + gy1) * 0.5)]
    cb.decal(detail, grille, "z", -1, 0.003, "metal_dark", DK)
    nsl = 4 if not boxy else 7
    for i in range(nsl):
        y = gy0 + (gy1 - gy0) * (i + 0.7) / (nsl + 0.4)
        cb.decal(detail, rrect2(0.0, y, gx * 1.9, 0.009, 0.004, 1), "z", -1, 0.007, "chrome" if lux else "car_trim",
                 W1 if lux else DK)
    cb.decal(detail, circ2(0.0, gy1 - 0.035 if not boxy else gy1 - 0.08, 0.03), "z", -1, 0.009, "chrome", W1)
    iy0, iy1 = gc + 0.12, gc + 0.2
    intake = [(-fr["hw"] * 0.56, iy1), (fr["hw"] * 0.56, iy1), (fr["hw"] * 0.5, iy0), (-fr["hw"] * 0.5, iy0)]
    cb.decal(detail, intake, "z", -1, 0.003, "metal_dark", DK)
    for i in range(3):
        cb.decal(detail, rrect2(0.0, iy0 + 0.02 + i * 0.022, fr["hw"] * 1.0, 0.006, 0.003, 1), "z", -1, 0.006,
                 "car_trim", DK)
    py = gc + (0.3 if not boxy else 0.42)
    cb.decal(detail, rrect2(0.0, py, 0.52, 0.12, 0.012), "z", -1, 0.008, "plastic", (0.95, 0.95, 0.93, 1))
    pz = cb.surface_z(0.0, py, -1)
    plate = "PA %d%02d" % (abs(hash(vid)) % 9 + 1, abs(hash(vid + "x")) % 100)
    if pz is not None:
        add_signs(detail, [(plate, (0.0, py - 0.03, pz - 0.0105), math.pi, 0.06, (0.08, 0.1, 0.22))])
    # ---------------------------------------------------------------- rear: lamps, plate, exhaust, diffuser
    tbelt = rr_["belt"]
    horizontal = st in ("sedan", "coupe", "super") or lux
    thw = rr_["hw"]
    for sx in (1, -1):
        def M(poly):
            return poly if sx > 0 else _mirror_outline(poly)
        if horizontal:
            ty = tbelt - 0.08
            tail = [(thw * 0.42, ty - 0.035), (thw * 0.9, ty - 0.048), (thw * 0.97, ty - 0.01), (thw * 0.95, ty + 0.04),
                    (thw * 0.45, ty + 0.034)]
            bar = [(thw * 0.46, ty + 0.004), (thw * 0.92, ty - 0.004), (thw * 0.93, ty + 0.012), (thw * 0.47, ty + 0.018)]
            rev = rrect2(thw * 0.53, ty - 0.017, 0.07, 0.022, 0.006, 1)
            trn = rrect2(thw * 0.86, ty - 0.024, 0.07, 0.02, 0.006, 1)
        else:
            y0 = max(gc + 0.45, tbelt - 0.2)
            y1 = min(rr_["top"] - 0.1, tbelt + 0.18) if not boxy else min(rr_["top"] - 0.4, gc + 1.2)
            tail = [(thw * 0.8, y0), (thw * 0.96, y0), (thw * 0.97, y1), (thw * 0.84, y1)]
            bar = [(thw * 0.86, y0 + 0.03), (thw * 0.92, y0 + 0.03), (thw * 0.93, y1 - 0.03), (thw * 0.87, y1 - 0.03)]
            rev = rrect2(thw * 0.89, y0 + (y1 - y0) * 0.25, 0.05, 0.04, 0.006, 1)
            trn = rrect2(thw * 0.89, y0 + (y1 - y0) * 0.62, 0.05, 0.04, 0.006, 1)
        cb.decal(lights["LightBrake"], M(tail), "z", 1, 0.004, "car_light", (0.55, 0.55, 0.55, 1))
        cb.decal(lights["LightBrake"], M(bar), "z", 1, 0.0065, "car_light", W1)
        cb.decal(lights["LightReverse"], M(rev), "z", 1, 0.0065, "car_light", W1)
        cb.decal(lights["LightTurnR" if sx > 0 else "LightTurnL"], M(trn), "z", 1, 0.0065, "car_light", W1)
        # side repeater on the front fender
        zr_ = cb.z_of(0.16)
        cb.decal(lights["LightTurnR" if sx > 0 else "LightTurnL"], rrect2(zr_, fr["belt"] - 0.1, 0.07, 0.018, 0.008, 2),
                 "x", sx, 0.004, "car_light", W1, zone=None)
    if sporty or lux:
        cb.decal(lights["LightBrake"], rrect2(0.0, tbelt - 0.07, thw * 0.9, 0.012, 0.005, 1), "z", 1, 0.004,
                 "car_light", (0.7, 0.7, 0.7, 1))
    rpy = gc + 0.36 if horizontal else gc + 0.4
    cb.decal(detail, rrect2(0.0, rpy, 0.52, 0.12, 0.012), "z", 1, 0.008, "plastic", (0.95, 0.95, 0.93, 1))
    rz = cb.surface_z(0.0, rpy, 1)
    if rz is not None:
        add_signs(detail, [(plate, (0.0, rpy - 0.03, rz + 0.0105), 0.0, 0.06, (0.08, 0.1, 0.22))])
    cb.decal(detail, [(-thw * 0.6, gc + 0.17), (thw * 0.6, gc + 0.17), (thw * 0.66, gc + 0.09), (-thw * 0.66, gc + 0.09)],
             "z", 1, 0.003, "metal_dark", DK)
    if st not in ("bus",):
        for sx in ((-1, 1) if (sporty or lux) else (1,)):
            ex, ey = sx * thw * 0.58, gc + 0.13
            ez = cb.surface_z(ex, ey, 1) or zr
            lathe(detail, [(ez - 0.12, 0.034), (ez + 0.035, 0.034), (ez + 0.04, 0.03)], "chrome", 14, (ex, ey), "z", W1,
                  cap0=False, cap1=False)
            disc(detail, (ex, ey, ez + 0.02), 0.029, "car_trim", "z", 1.0, 14, DK)
    # ---------------------------------------------------------------- sides: handles, mirrors, wipers, fin
    handle_zs = []
    if st in ("sedan", "hatch", "suv", "pickup"):
        handle_zs = [cb.z_of(cb.b_pillar) - 0.16, cb.z_of(cb.glass_f1) - 0.1]
    elif st in ("coupe", "super"):
        handle_zs = [cb.z_of(cb.glass_f1) - 0.18]
    elif st in ("van",):
        handle_zs = [cb.z_of(p["rs"]) + 0.02]
    for sx in (1, -1):
        for hz in handle_zs:
            hy = cb.section_info((hz - zf) / L)["belt"] - 0.07
            cb.decal(detail, rrect2(hz, hy, 0.165, 0.036, 0.014), "x", sx, 0.002, "car_trim", DK)
            cb.decal(detail, rrect2(hz, hy + 0.002, 0.14, 0.022, 0.01), "x", sx, 0.011, "chrome" if lux else pm,
                     W1 if lux else lv(0.5, hy, None))
    if st != "bus":
        mf = p["ws"] + (p["rs"] - p["ws"]) * (0.3 if not boxy else 0.6)
        mi = cb.section_info(mf)
        mz = cb.z_of(mf)
        my = mi["belt"] + (0.1 if not boxy else 0.35)
        mx = mi["hw"] * 0.93
        mw, mh = (0.19, 0.11) if not boxy else (0.12, 0.3)
        for sx in (1, -1):
            x0m, x1m = (mx + 0.05, mx + 0.05 + mw) if sx > 0 else (-(mx + 0.05 + mw), -(mx + 0.05))
            extrude(detail, rrect(0.1, mh, 0.035, 3), "x", x0m, x1m, pm, 0.014, lv(0.5, my, None), lv(0.5, my, None),
                    offset=(mz, my))
            gx0, gx1 = (x0m + 0.012, x1m - 0.012)
            extrude(detail, rrect(gx1 - gx0, mh - 0.02, 0.028, 3), "z", mz + 0.049, mz + 0.054, "chrome", 0.0, W1, W1,
                    offset=((gx0 + gx1) * 0.5, my))
            bbox(detail, (sx * (mx + 0.03), my - mh * 0.3, mz - 0.01), (0.07, 0.025, 0.05), "car_trim", 0.004, DK)
    # wipers
    wf = p["ws"] + 0.012
    wi = cb.section_info(wf)
    wz = cb.z_of(wf) + 0.02
    for sx, ang in ((-1, 0.18), (0.25, 0.14)):
        cx = sx * wi["hw"] * 0.35
        blen = wi["hw"] * 0.8
        pts = [(cx, wi["belt"] + 0.035, wz), (cx + blen * math.cos(ang), wi["belt"] + 0.035 + blen * math.sin(ang) * 0.3, wz - 0.01)]
        sweep(detail, pts, [(-0.008, -0.006), (0.008, -0.006), (0.008, 0.006), (-0.008, 0.006)], "car_trim",
              DK, closed_profile=True)
    if not boxy:
        fi = cb.section_info(p["re"] - 0.02)
        fz = cb.z_of(p["re"] - 0.02)
        slab(detail, smooth_loop([(fz - 0.08, fi["top"] + 0.02), (fz + 0.08, fi["top"] + 0.02), (fz + 0.07, fi["top"] + 0.05),
                                  (fz - 0.03, fi["top"] + 0.075)], 1), 0.022, "car_trim", 0.006, col=DK)
    # ---------------------------------------------------------------- interior
    seat_z = zf + L * (p["rs"] + p["re"]) * 0.5 - (0.2 if not boxy else 0.0)
    if st in ("van", "truck"):
        seat_z = zf + L * 0.14 + 0.5
    if st == "bus":
        seat_z = zf + 1.0
    seat_y = gc + 0.25 if st not in ("truck", "bus") else gc + 0.9
    leather = (0.42, 0.26, 0.14, 1) if lux else (0.13, 0.12, 0.11, 1)
    roof_y = top_at(p, (p["rs"] + p["re"]) * 0.5)
    ih = max(0.55, min(1.0, (roof_y - 0.07 - seat_y) / 0.98))      # seats scaled to the cabin height
    for sx in (-1, 1):
        sxp = sx * hw * 0.42
        extrude(interior, rrect(0.5, 0.14 * ih, 0.05, 3), "x", sxp - 0.24, sxp + 0.24, "car_interior", 0.03, leather,
                leather, offset=(seat_z + 0.13, seat_y + 0.2 * ih))
        extrude(interior, smooth_loop([(seat_z + 0.34, seat_y + 0.22 * ih), (seat_z + 0.46, seat_y + 0.22 * ih),
                                       (seat_z + 0.52, seat_y + 0.82 * ih), (seat_z + 0.42, seat_y + 0.84 * ih)], 1), "x",
                sxp - 0.22, sxp + 0.22, "car_interior", 0.03, leather, leather)
        for bx in (-0.22, 0.22):
            extrude(interior, rrect(0.1, 0.46 * ih, 0.04, 2), "x", sxp + bx - 0.03, sxp + bx + 0.03, "car_interior", 0.015,
                    leather, leather, offset=(seat_z + 0.44, seat_y + 0.52 * ih))
        extrude(interior, rrect(0.12, 0.11 * ih, 0.04, 2), "x", sxp - 0.11, sxp + 0.11, "car_interior", 0.02, leather,
                leather, offset=(seat_z + 0.5, seat_y + 0.92 * ih))
    if st in ("sedan", "hatch", "suv") or lux:
        extrude(interior, rrect(0.42, 0.13 * ih, 0.05, 3), "x", -hw * 0.8, hw * 0.8, "car_interior", 0.03, leather, leather,
                offset=(seat_z + 0.86, seat_y + 0.18 * ih))
        extrude(interior, rrect(0.11, 0.46 * ih, 0.05, 3), "x", -hw * 0.8, hw * 0.8, "car_interior", 0.03, leather, leather,
                offset=(seat_z + 1.06, seat_y + 0.46 * ih))
    dash_z = seat_z - 0.72
    dy = hood if not boxy else hood - 0.1
    extrude(interior, smooth_loop([(dash_z - 0.2, dy - 0.02), (dash_z + 0.12, dy - 0.02), (dash_z + 0.2, dy - 0.12),
                                   (dash_z + 0.1, dy - 0.32), (dash_z - 0.2, dy - 0.32)], 1), "x", -hw * 0.88, hw * 0.88,
            "car_interior", 0.02, (0.08, 0.08, 0.085, 1), (0.08, 0.08, 0.085, 1))
    extrude(interior, rrect(0.2, 0.09, 0.03, 2), "x", -hw * 0.62, -hw * 0.22, "car_interior", 0.01, (0.04, 0.04, 0.045, 1),
            (0.04, 0.04, 0.045, 1), offset=(dash_z + 0.1, dy + 0.02))
    extrude(interior, rrect(0.12, 0.16, 0.02, 2), "z", dash_z + 0.19, dash_z + 0.2, "car_interior", 0.0, (0.02, 0.03, 0.05, 1),
            (0.02, 0.03, 0.05, 1), offset=(0.0, dy - 0.13))
    bbox(interior, (0.0, seat_y + 0.25, seat_z - 0.1), (0.2, 0.2, 0.9), "car_interior", 0.02, (0.07, 0.07, 0.075, 1))
    swc = (-hw * 0.42, dy + 0.0, seat_z - 0.45)
    ring = [(swc[0] + math.cos(a) * 0.18, swc[1] + math.sin(a) * 0.18 * 0.97, swc[2] + math.sin(a) * 0.04)
            for a in [math.tau * i / 20 for i in range(21)]]
    tube(interior, ring, 0.016, "car_interior", 8, (0.06, 0.06, 0.06, 1))
    for a in (math.pi * 0.5 + math.pi, 0.0, math.pi):
        tube(interior, [swc, (swc[0] + math.cos(a) * 0.17, swc[1] + math.sin(a) * 0.17, swc[2])], 0.012, "car_interior", 6,
             (0.08, 0.08, 0.08, 1))
    tube(interior, [swc, (swc[0], swc[1] - 0.12, swc[2] - 0.25)], 0.03, "car_interior", 8, (0.07, 0.07, 0.07, 1))
    driver_seat = (-hw * 0.42, seat_y + 0.1, seat_z + 0.1)
    # ---------------------------------------------------------------- style specific extras
    if p.get("spoiler"):
        sz = zr - 0.22
        sy = rr_["belt"] + (0.2 if st == "super" else 0.07)
        extrude(detail, smooth_loop([(sz - 0.14, sy), (sz + 0.12, sy + 0.012), (sz + 0.13, sy + 0.03), (sz - 0.12, sy + 0.022)], 1),
                "x", -hw * 0.86, hw * 0.86, "car_trim", 0.006, DK, DK)
        for sx in (-0.6, 0.6):
            bbox(detail, (sx * hw, sy - 0.06, sz), (0.03, 0.13 if st == "super" else 0.09, 0.1), "car_trim", 0.004, DK)
    if st == "pickup":
        bz0 = zf + L * p["rw"]
        for sx in (-1, 1):
            bbox(body, (sx * (hw - 0.05), p["rear"] + 0.25, (bz0 + zr) * 0.5), (0.08, 0.5, zr - bz0), pm, 0.02,
                 lv(0.9, 1.0, None))
        bbox(body, (0, p["rear"] + 0.25, zr - 0.04), (W - 0.1, 0.5, 0.08), pm, 0.02, lv(0.9, 1.0, None))
        bbox(detail, (0, p["rear"] + 0.01, (bz0 + zr) * 0.5), (W - 0.2, 0.02, zr - bz0 - 0.1), "car_trim", 0.0, DK)
    if st == "truck":
        bz0 = zf + L * 0.3
        bbox(body, (0, (gc + 0.9 + p["rear"]) * 0.5 + 0.2, (bz0 + zr) * 0.5), (W, p["rear"] - 0.7, zr - bz0),
             pm, 0.03, (0.95, 0.95, 0.95, 1) if paint != "fire" else (0.75, 0.06, 0.05, 1))
        for k in range(8):
            zz = bz0 + (zr - bz0) * (k + 0.5) / 8
            for sx in (-1, 1):
                bbox(detail, (sx * (hw + 0.008), (gc + 0.9 + p["rear"]) * 0.5 + 0.2, zz), (0.012, p["rear"] - 0.8, 0.04),
                     "metal", 0.0, W1)
    if p.get("lightbar"):
        ry = top_at(p, (p["rs"] + p["re"]) * 0.5) + cb.crown
        if st in ("van", "truck"):
            ry = top_at(p, 0.15) + cb.crown
        rz = zf + L * ((p["rs"] + p["re"]) * 0.5 if st not in ("van", "truck") else 0.12)
        extrude(detail, rrect(0.3, 0.07, 0.03, 2), "x", -0.62, 0.62, "car_trim", 0.01, DK, DK, offset=(rz, ry + 0.05))
        for sx, colr in ((-1, (1.0, 0.1, 0.1, 1)), (1, (0.1, 0.3, 1.0, 1))):
            extrude(lights["Siren"], rrect(0.24, 0.08, 0.03, 2), "x", min(sx * 0.06, sx * 0.58), max(sx * 0.06, sx * 0.58),
                    "car_siren", 0.012, colr, colr, offset=(rz, ry + 0.12))
        if paint == "police":
            extrude(bf, rrect(0.08, 0.36, 0.02, 2), "x", -W * 0.3, W * 0.3, "metal_dark", 0.01, DK, DK,
                    offset=(zf - 0.12, gc + 0.42))
            for sx in (-1, 1):
                bbox(bf, (sx * W * 0.22, gc + 0.42, zf - 0.06), (0.05, 0.4, 0.12), "metal_dark", 0.01, DK)
    if p.get("sign"):
        ry = top_at(p, (p["rs"] + p["re"]) * 0.5) + cb.crown
        rz = zf + L * (p["rs"] + p["re"]) * 0.5
        extrude(detail, rrect(0.25, 0.2, 0.05, 3), "x", -0.35, 0.35, "plastic", 0.02, (1.0, 0.9, 0.3, 1), (1.0, 0.9, 0.3, 1),
                offset=(rz, ry + 0.1))
        add_signs(lights["LightHead"], [(p["sign"], (0, ry + 0.1, rz - 0.13), math.pi, 0.14, (0.1, 0.1, 0.1)),
                                         (p["sign"], (0, ry + 0.1, rz + 0.13), 0.0, 0.14, (0.1, 0.1, 0.1))])
    if p.get("ladder"):
        ry = p["rear"] + 0.3
        for sx in (-0.4, 0.4):
            box(detail, (sx, ry, 0.5), (0.08, 0.1, L * 0.7), "metal")
        for k in range(14):
            box(detail, (0, ry, -L * 0.3 + k * L * 0.05 + 0.5), (0.8, 0.05, 0.05), "metal")
    if paint == "police":
        add_signs(detail, [("POLICE", (hw + 0.014, 0.75, zf + L * 0.52), math.pi * 0.5, 0.22, (0.95, 0.95, 0.95)),
                           ("POLICE", (-hw - 0.014, 0.75, zf + L * 0.52), -math.pi * 0.5, 0.22, (0.95, 0.95, 0.95))])
    if paint == "ambulance":
        add_signs(detail, [("MEDIC ONE", (hw + 0.014, 1.2, 0.3), math.pi * 0.5, 0.25, (0.85, 0.08, 0.08)),
                           ("MEDIC ONE", (-hw - 0.014, 1.2, 0.3), -math.pi * 0.5, 0.25, (0.85, 0.08, 0.08))])
    if paint == "delivery":
        add_signs(detail, [("QUIKSHIP", (hw + 0.014, 1.35, 0.4), math.pi * 0.5, 0.3, (0.2, 0.45, 0.25)),
                           ("QUIKSHIP", (-hw - 0.014, 1.35, 0.4), -math.pi * 0.5, 0.3, (0.2, 0.45, 0.25))])
    if st == "bus":
        add_signs(detail, [("12  HARBOR", (0, p["H"] - 0.35, zf - 0.03), math.pi, 0.25, (1.0, 0.7, 0.1))])
        cb.decal(detail, rrect2(zf + 1.1, gc + 1.05, 1.0, 1.9, 0.05), "x", 1, 0.004, "car_trim", DK)
    wheels = {}
    track = W * 0.5 - 0.16
    for tag, z in (("F", -p["WB"] * 0.5), ("R", p["WB"] * 0.5)):
        for side, sx in (("L", -1), ("R", 1)):
            wheels[f"Wheel_{tag}{side}"] = (sx * track, R, z)
    return dict(body=body, glass=glass, bf=bf, br=br, detail=detail, interior=interior, lights=lights,
                wheels=wheels, seat=driver_seat, fixed=fixed)


def build_bike(p):
    body = MeshBuilder()
    detail = MeshBuilder()
    lights = {k: MeshBuilder() for k in ("LightHead", "LightBrake", "LightReverse", "LightTurnL", "LightTurnR", "Siren")}
    R = p["R"]
    wb = p["WB"]
    # frame
    sweep(detail, [(0, R, -wb * 0.5 + 0.1), (0, 0.85, -wb * 0.5 + 0.35), (0, 0.8, 0.0), (0, R + 0.1, wb * 0.5 - 0.1)],
          [(-0.04, -0.04), (0.04, -0.04), (0.04, 0.04), (-0.04, 0.04)], "metal_dark", closed_profile=True)
    box(body, (0, 0.88, -0.2), (0.34, 0.24, 0.6), "car_paint")                 # tank
    box(body, (0, 0.92, 0.35), (0.3, 0.1, 0.6), "car_interior", col=(0.08, 0.08, 0.08, 1))  # seat
    box(body, (0, 0.6, 0.0), (0.3, 0.3, 0.5), "metal_dark")                    # engine
    box(body, (0, 0.95, 0.75), (0.28, 0.12, 0.35), "car_paint")                # tail
    box(body, (0, 0.95, -0.72), (0.3, 0.3, 0.2), "car_paint")                  # fairing
    sweep(detail, [(-0.35, 1.05, -0.62), (0.35, 1.05, -0.62)], [(-0.02, -0.02), (0.02, -0.02), (0.02, 0.02), (-0.02, 0.02)],
          "metal_dark", closed_profile=True)
    sweep(detail, [(0, 1.05, -0.62), (0, R, -wb * 0.5)], [(-0.07, -0.02), (0.07, -0.02), (0.07, 0.02), (-0.07, 0.02)], "chrome",
          closed_profile=True)
    box(lights["LightHead"], (0, 0.95, -0.83), (0.14, 0.1, 0.04), "car_light")
    box(lights["LightBrake"], (0, 0.95, 0.93), (0.14, 0.06, 0.04), "car_light")
    box(lights["LightTurnL"], (-0.15, 0.9, -0.8), (0.04, 0.04, 0.04), "car_light")
    box(lights["LightTurnR"], (0.15, 0.9, -0.8), (0.04, 0.04, 0.04), "car_light")
    cylinder(detail, (0.12, 0.45, 0.4), 0.05, 0.6, "chrome", segs=8, axis="z")
    wheels = {"Wheel_F": (0, R, -wb * 0.5), "Wheel_R": (0, R, wb * 0.5)}
    return dict(body=body, glass=MeshBuilder(), bf=MeshBuilder(), br=MeshBuilder(), detail=detail, interior=MeshBuilder(),
                lights=lights, wheels=wheels, seat=(0, 0.72, 0.3), fixed=False)


def main():
    reset_scene()
    os.makedirs(OUT, exist_ok=True)
    meta = {}
    for vid in VEHICLES:
        p = resolve(vid)
        parts = build_bike(p) if p["style"] == "bike" else build_car(vid, p)
        objs = []
        for key, name in (("body", "Body"), ("glass", "Glass"), ("bf", "BumperF"), ("br", "BumperR"), ("detail", "Detail"),
                          ("interior", "Interior")):
            mb = parts[key]
            if not mb.empty():
                objs.append(mb.to_object(name, smooth=key == "body", auto_smooth=40 if key == "body" else None))
        for lname, mb in parts["lights"].items():
            if not mb.empty():
                objs.append(mb.to_object(lname))
        wm = wheel_mesh(p["R"], 0.24 if p["style"] != "bike" else 0.13, hash(vid) % 3)
        for wname, pos in parts["wheels"].items():
            mb = wm
            if pos[0] < 0:
                mb = wm.transformed(lambda v: (-v[0], v[1], -v[2]))
            moved = mb.transformed(lambda v, pos=pos: (v[0] + pos[0], v[1] + pos[1], v[2] + pos[2]))
            objs.append(moved.to_object(wname, origin=pos, smooth=True))
        export_glb(os.path.join(OUT, f"{vid}.glb"), objs)
        clear_objects()
        meta[vid] = dict(name=p.get("name", vid), style=p["style"], length=p["L"], width=p["W"], height=p["H"],
                         wheelbase=p["WB"], wheel_radius=p["R"], ground=p["gc"], seat=list(parts["seat"]),
                         wheels={k: list(v) for k, v in parts["wheels"].items()}, fixed_livery=parts["fixed"],
                         lightbar=bool(p.get("lightbar")), hood=p.get("hood", 1.0))
        print(f"  {vid}: {parts['body'].tri_count() + parts['detail'].tri_count()} tris")
    with open(os.path.join(OUT, "vehicles_meta.json"), "w") as f:
        json.dump(meta, f, indent=1)
    print("[vehicles]", len(meta))


if __name__ == "__main__":
    main()
