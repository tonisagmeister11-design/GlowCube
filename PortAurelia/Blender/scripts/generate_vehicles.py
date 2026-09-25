"""Parametric vehicle generator: 16 original vehicles.

Body = loft of cross sections along the length (front at Godot -Z), with wheel
arches cut into the sides, greenhouse (glass) with pillars, separate bumpers,
light meshes (head / brake / reverse / turn L / turn R / siren), interior and
wheels (pivot at the wheel centre).

Output per vehicle: Game/assets/generated/vehicles/<id>.glb
Metadata:           Game/assets/generated/vehicles/vehicles_meta.json
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from _common import GEN, MeshBuilder, reset_scene, clear_objects, export_glb, box, cylinder, sweep, add_signs  # noqa: E402

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


RING = [  # (relative height 0..1 in the lower body, lateral factor) bottom-centre -> side -> beltline
    (0.0, 0.0), (0.0, 0.82), (0.06, 0.97), (0.35, 1.0), (0.75, 1.0), (1.0, 0.97)]


def build_body(p, paint_col, livery):
    """Returns (body, glass, arch) MeshBuilders."""
    body = MeshBuilder()
    glass = MeshBuilder()
    L = p["L"]
    gc = p["gc"]
    st = p["style"]
    zf = -L * 0.5
    n = 34
    fs = [i / n for i in range(n + 1)]
    wb = p["WB"]
    axle_f = -wb * 0.5 - (0.0)
    axle_r = wb * 0.5
    wheel_zs = [axle_f + (L * 0.5 - wb * 0.5 - L * 0.5 + 0.0) * 0.0, axle_r]
    wheel_zs = [-wb * 0.5 + p.get("axle_shift", 0.0), wb * 0.5 + p.get("axle_shift", 0.0)]
    R = p["R"]
    arch_r = R * 1.18
    wy = R
    sections = []
    for f in fs:
        z = zf + L * f
        top = top_at(p, f)
        belt = min(top, p["hood"] * (1.02 if st not in ("van", "truck", "bus") else 1.0) + 0.02)
        if st in ("van", "truck", "bus", "suv"):
            belt = min(top, p["hood"] * 1.0)
        hw = width_at(p, f, 0)
        # lowest point of the side skirt (wheel arches)
        bottom = gc
        for wz in wheel_zs:
            dz = abs(z - wz)
            if dz < arch_r:
                bottom = max(bottom, wy + math.sqrt(arch_r * arch_r - dz * dz) * 0.92)
        pts = []
        # lower body ring (right side), then greenhouse, then mirrored
        for (hr, lf) in RING:
            y = bottom + (belt - bottom) * hr if hr > 0 else bottom
            if hr == 0.0 and lf == 0.0:
                y = gc
            pts.append((hw * lf, y, "lower"))
        in_cabin = belt < top - 0.05
        if in_cabin:
            tumble = 0.78 if st not in ("van", "truck", "bus") else 0.95
            for k in range(1, 4):
                t = k / 3.0
                y = belt + (top - belt) * t
                x = hw * (0.97 - (0.97 - tumble) * t ** 1.2) if k < 3 else hw * tumble * 0.82
                pts.append((x, y, "glass" if k < 3 else "roof_edge"))
            pts.append((0.0, top, "roof"))
        else:
            pts.append((hw * 0.9, top, "top"))
            pts.append((0.0, top + 0.01, "top"))
        sections.append((z, f, pts, in_cabin))
    # pillars
    b_pillar = (p["rs"] + p["re"]) * 0.5
    for i in range(len(sections) - 1):
        z0, f0, P0, c0 = sections[i]
        z1, f1, P1, c1 = sections[i + 1]
        m = min(len(P0), len(P1))
        fm = (f0 + f1) * 0.5
        for side in (1, -1):
            for k in range(m - 1):
                a = P0[k]
                b = P0[k + 1]
                c = P1[k + 1]
                d = P1[k]
                pa = (a[0] * side, a[1], z0)
                pb = (b[0] * side, b[1], z0)
                pc = (c[0] * side, c[1], z1)
                pd = (d[0] * side, d[1], z1)
                kind = b[2]
                is_glass = False
                if c0 and c1 and kind in ("glass", "roof_edge"):
                    if p["ws"] + 0.02 < fm < p["rw"] - 0.015 and abs(fm - b_pillar) > 0.025 and st not in ("bus",):
                        is_glass = kind == "glass"
                    if st == "bus" and 0.08 < fm < 0.97:
                        is_glass = kind == "glass"
                if c0 and c1 and kind == "roof" and (p["ws"] < fm < p["rs"] - 0.01 or p["re"] + 0.01 < fm < p["rw"]):
                    is_glass = st not in ("pickup",) or fm < p["rs"]
                # windscreen of vans/trucks/buses: front face above the hood
                if st in ("van", "truck", "bus") and fm < p["rs"] and kind in ("glass", "roof_edge", "roof") and c0:
                    is_glass = True
                col = livery(fm, (a[1] + c[1]) * 0.5, paint_col)
                pts = [pa, pb, pc, pd] if side == 1 else [pd, pc, pb, pa]
                if is_glass:
                    glass.face(pts, None, "car_glass")
                else:
                    body.face(pts, None, "car_paint", col)
    # front and rear caps
    for (z, f, P, c), sgn in ((sections[0], -1), (sections[-1], 1)):
        ring = [(x, y, z) for (x, y, _) in P] + [(-x, y, z) for (x, y, _) in reversed(P)]
        cx = 0.0
        cy = sum(q[1] for q in ring) / len(ring)
        for k in range(len(ring) - 1):
            tri = [(cx, cy, z), ring[k], ring[k + 1]]
            body.face(tri, None, "car_paint", livery(f, cy, paint_col), up=(0, 0, sgn))
    return body, glass


def wheel_mesh(R, width, rim_style=0):
    mb = MeshBuilder()
    segs = 18
    # tyre: outer tread + two sidewalls (axis along X)
    for i in range(segs):
        a0 = 2 * math.pi * i / segs
        a1 = 2 * math.pi * (i + 1) / segs
        def P(r, x, a):
            return (x, math.sin(a) * r, math.cos(a) * r)
        hw = width * 0.5
        r_in = R * 0.66
        mb.face([P(R, -hw, a0), P(R, hw, a0), P(R, hw, a1), P(R, -hw, a1)],
                [(0, i / segs), (1, i / segs), (1, (i + 1) / segs), (0, (i + 1) / segs)], "tire",
                up=(0, math.sin((a0 + a1) / 2), math.cos((a0 + a1) / 2)))
        for sx in (-1, 1):
            q = [P(R * 0.98, sx * hw * 1.02, a0), P(R * 0.98, sx * hw * 1.02, a1), P(r_in, sx * hw * 0.9, a1), P(r_in, sx * hw * 0.9, a0)]
            mb.face(q, None, "tire", up=(sx, 0, 0))
    # rim (outer face at +X), spokes
    r_rim = R * 0.66
    cylinder(mb, (width * 0.36, 0, 0), r_rim, -width * 0.5, "rim", segs=segs, axis="x", top=False, bottom=False)
    nsp = [5, 6, 10][rim_style % 3]
    for k in range(nsp):
        a = 2 * math.pi * k / nsp
        ca, sa = math.cos(a), math.sin(a)
        w = 0.05 if nsp < 10 else 0.025
        p0 = (width * 0.4, sa * 0.06, ca * 0.06)
        p1 = (width * 0.36, sa * r_rim * 0.95, ca * r_rim * 0.95)
        tang = (0, ca * w, -sa * w)
        mb.face([(p0[0], p0[1] - tang[1], p0[2] - tang[2]), (p1[0], p1[1] - tang[1], p1[2] - tang[2]),
                 (p1[0], p1[1] + tang[1], p1[2] + tang[2]), (p0[0], p0[1] + tang[1], p0[2] + tang[2])],
                None, "rim", up=(1, 0, 0))
    cylinder(mb, (width * 0.36, 0, 0), 0.07, 0.06, "rim", segs=8, axis="x")
    # brake disc
    cylinder(mb, (width * 0.1, 0, 0), R * 0.55, 0.03, "metal_dark", segs=segs, axis="x")
    return mb


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
def build_car(vid, p):
    paint = p.get("paint", "civil")
    fixed = paint != "civil"
    lv = liveries(paint)
    body, glass = build_body(p, (1, 1, 1, 1), lv)
    if fixed:
        body.mats = ["car_paint_vc" if m == "car_paint" else m for m in body.mats]
        body._mat_idx = {m: i for i, m in enumerate(body.mats)}
    L, W, H, gc, hood, R = p["L"], p["W"], p["H"], p["gc"], p["hood"], p["R"]
    st = p["style"]
    hw = W * 0.5
    zf, zr = -L * 0.5, L * 0.5
    lights = {k: MeshBuilder() for k in ("LightHead", "LightBrake", "LightReverse", "LightTurnL", "LightTurnR", "Siren")}
    detail = MeshBuilder()
    bf = MeshBuilder()
    br = MeshBuilder()
    interior = MeshBuilder()
    # --- bumpers
    bh = min(0.55, hood * 0.5)
    box(bf, (0, gc + bh * 0.5, zf - 0.03), (W * 0.96, bh, 0.22), "car_trim", bottom=True)
    box(br, (0, gc + bh * 0.5, zr + 0.03), (W * 0.96, bh, 0.22), "car_trim", bottom=True)
    # --- grille / plates
    box(detail, (0, gc + bh + 0.1, zf - 0.005), (W * 0.45, 0.16, 0.04), "metal_dark")
    box(detail, (0, gc + bh * 0.5, zf - 0.15), (0.5, 0.12, 0.02), "plastic", col=(0.95, 0.95, 0.95, 1))
    box(detail, (0, gc + bh * 0.8, zr + 0.15), (0.5, 0.12, 0.02), "plastic", col=(0.95, 0.95, 0.95, 1))
    # --- lights
    ly = gc + bh + 0.14 if st not in ("truck", "bus", "van") else gc + bh + 0.25
    for sx in (-1, 1):
        box(lights["LightHead"], (sx * hw * 0.68, ly, zf + 0.02), (hw * 0.42, 0.11, 0.06), "car_light")
        box(lights["LightBrake"], (sx * hw * 0.72, ly + 0.02, zr - 0.01), (hw * 0.36, 0.12, 0.06), "car_light")
        box(lights["LightReverse"], (sx * hw * 0.45, ly - 0.08, zr), (0.12, 0.05, 0.05), "car_light")
        tl = lights["LightTurnL"] if sx < 0 else lights["LightTurnR"]
        box(tl, (sx * hw * 0.93, ly - 0.02, zf + 0.06), (0.1, 0.07, 0.08), "car_light")
        box(tl, (sx * hw * 0.93, ly - 0.04, zr - 0.04), (0.1, 0.07, 0.06), "car_light")
    # --- mirrors
    if st not in ("bus",):
        mz = zf + L * p["ws"] + 0.15
        for sx in (-1, 1):
            box(detail, (sx * (hw + 0.08), hood + 0.12, mz), (0.16, 0.1, 0.08), "car_paint" if not fixed else "car_trim")
    # --- interior (seats + steering wheel + dash)
    seat_z = zf + L * (p["rs"] + p["re"]) * 0.5 - (0.2 if st not in ("van", "truck", "bus") else 0.0)
    if st in ("van", "truck"):
        seat_z = zf + L * 0.14 + 0.5
    if st == "bus":
        seat_z = zf + 1.0
    seat_y = gc + 0.25 if st not in ("truck", "bus") else gc + 0.9
    for sx in (-1, 1):
        box(interior, (sx * hw * 0.42, seat_y + 0.2, seat_z + 0.15), (0.5, 0.18, 0.5), "car_interior", col=(0.15, 0.14, 0.13, 1))
        box(interior, (sx * hw * 0.42, seat_y + 0.55, seat_z + 0.42), (0.5, 0.7, 0.14), "car_interior", col=(0.15, 0.14, 0.13, 1))
    box(interior, (0, hood - 0.08, seat_z - 0.75), (W * 0.9, 0.2, 0.4), "car_interior", col=(0.1, 0.1, 0.1, 1))
    cylinder(interior, (-hw * 0.42, hood + 0.0, seat_z - 0.45), 0.18, 0.04, "car_trim", segs=12, axis="z")
    driver_seat = (-hw * 0.42, seat_y + 0.1, seat_z + 0.1)
    # --- style specific extras
    if p.get("spoiler"):
        box(detail, (0, p["rear"] + 0.18, zr - 0.25), (W * 0.9, 0.04, 0.28), "car_trim")
        for sx in (-0.6, 0.6):
            box(detail, (sx * hw, p["rear"] + 0.08, zr - 0.25), (0.05, 0.18, 0.12), "car_trim")
    if p.get("chrome"):
        box(detail, (0, gc + bh + 0.1, zf - 0.01), (W * 0.5, 0.2, 0.03), "chrome")
    if st == "pickup":
        # bed walls on the open rear
        bz0 = zf + L * p["rw"]
        for sx in (-1, 1):
            box(body, (sx * (hw - 0.05), p["rear"] + 0.25, (bz0 + zr) * 0.5), (0.08, 0.5, zr - bz0), "car_paint", col=(1, 1, 1, 1))
        box(body, (0, p["rear"] + 0.25, zr - 0.04), (W - 0.1, 0.5, 0.08), "car_paint", col=(1, 1, 1, 1))
    if st == "truck":
        # cargo box behind the cab
        bz0 = zf + L * 0.3
        box(body, (0, (gc + 0.9 + p["rear"]) * 0.5 + 0.2, (bz0 + zr) * 0.5), (W, p["rear"] - 0.7, zr - bz0),
            "car_paint_vc" if fixed else "car_paint", col=(0.95, 0.95, 0.95, 1) if paint != "fire" else (0.75, 0.06, 0.05, 1))
    if p.get("lightbar"):
        ry = top_at(p, (p["rs"] + p["re"]) * 0.5) + 0.02
        if st in ("van", "truck"):
            ry = top_at(p, 0.15) + 0.02
        rz = zf + L * ((p["rs"] + p["re"]) * 0.5 if st not in ("van", "truck") else 0.12)
        box(detail, (0, ry + 0.05, rz), (1.2, 0.08, 0.3), "car_trim")
        box(lights["Siren"], (-0.32, ry + 0.13, rz), (0.52, 0.1, 0.24), "car_siren", col=(1.0, 0.1, 0.1, 1))
        box(lights["Siren"], (0.32, ry + 0.13, rz), (0.52, 0.1, 0.24), "car_siren", col=(0.1, 0.3, 1.0, 1))
        if paint == "police":
            box(bf, (0, gc + 0.45, zf - 0.2), (W * 0.6, 0.5, 0.08), "metal_dark")  # push bar
    if p.get("sign"):
        ry = top_at(p, (p["rs"] + p["re"]) * 0.5)
        rz = zf + L * (p["rs"] + p["re"]) * 0.5
        box(detail, (0, ry + 0.1, rz), (0.7, 0.2, 0.25), "plastic", col=(1.0, 0.9, 0.3, 1))
        add_signs(lights["LightHead"], [(p["sign"], (0, ry + 0.1, rz - 0.13), math.pi, 0.14, (0.1, 0.1, 0.1)),
                                         (p["sign"], (0, ry + 0.1, rz + 0.13), 0.0, 0.14, (0.1, 0.1, 0.1))])
    if p.get("ladder"):
        ry = p["rear"] + 0.3
        for sx in (-0.4, 0.4):
            box(detail, (sx, ry, 0.5), (0.08, 0.1, L * 0.7), "metal")
        for k in range(14):
            box(detail, (0, ry, -L * 0.3 + k * L * 0.05 + 0.5), (0.8, 0.05, 0.05), "metal")
    if paint == "police":
        add_signs(detail, [("POLICE", (hw + 0.012, 0.75, zf + L * 0.52), math.pi * 0.5, 0.22, (0.95, 0.95, 0.95)),
                           ("POLICE", (-hw - 0.012, 0.75, zf + L * 0.52), -math.pi * 0.5, 0.22, (0.95, 0.95, 0.95))])
    if paint == "ambulance":
        add_signs(detail, [("MEDIC ONE", (hw + 0.012, 1.2, 0.3), math.pi * 0.5, 0.25, (0.85, 0.08, 0.08)),
                           ("MEDIC ONE", (-hw - 0.012, 1.2, 0.3), -math.pi * 0.5, 0.25, (0.85, 0.08, 0.08))])
    if paint == "delivery":
        add_signs(detail, [("QUIKSHIP", (hw + 0.012, 1.35, 0.4), math.pi * 0.5, 0.3, (0.2, 0.45, 0.25)),
                           ("QUIKSHIP", (-hw - 0.012, 1.35, 0.4), -math.pi * 0.5, 0.3, (0.2, 0.45, 0.25))])
    if st == "bus":
        add_signs(detail, [("12  HARBOR", (0, p["H"] - 0.35, zf - 0.02), math.pi, 0.25, (1.0, 0.7, 0.1))])
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
