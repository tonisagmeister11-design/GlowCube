"""Parasit: segmentierter, flacher Wurm mit Hakenkranz-Kopf (Scolex),
Saugnaepfen und einer 12-teiligen Wirbelsaeulen-Kette fuer Muskelbewegung."""
import math

from mathutils import Vector

import lib
from lib import Builder, Clip, fbm, frames, smoothstep, wave

ANIMATIONS = ["parasite_idle", "parasite_crawl", "parasite_contract", "parasite_expand"]
IDLE = "parasite_idle"

N_BONES = 12
N_SEG = 26
LENGTH = 2.3


def spine(u):
    x = -LENGTH / 2 + LENGTH * u
    return Vector((x, 0.32 * math.sin(u * math.pi * 1.6 + 0.4) * (0.4 + 0.6 * u), 0.06 * math.sin(u * math.pi * 3)))


def radius(u):
    # Schwanz spitz, Hals duenn, Kopf rund; Segment-Wuelste
    base = 0.05 + 0.1 * smoothstep(0.0, 0.35, u) - 0.06 * smoothstep(0.78, 0.9, u)
    seg = abs(math.sin(u * N_SEG * math.pi)) ** 0.35
    ridge = 1 + 0.16 * seg * smoothstep(0.02, 0.1, u) * (1 - smoothstep(0.8, 0.86, u))
    head = 0.085 * math.exp(-((u - 0.95) / 0.035) ** 2)
    tip = math.sqrt(max(0.0, 1 - ((u - 0.965) / 0.035) ** 2)) if u > 0.965 else 1.0
    return max(0.004, (base * ridge + head) * tip) * 1.35


def build(col):
    rnd = lib.rng(31)
    b = Builder()
    n = 140
    pts = [spine(i / (n - 1)) for i in range(n)]

    def flat(t, a):
        # abgeflachter (bandwurmartiger) Querschnitt, Kopf rund
        f = 0.62 + 0.38 * smoothstep(0.86, 0.93, t)
        return 1.0 / math.sqrt(math.cos(a) ** 2 + (math.sin(a) / f) ** 2) * f
    b.tube(pts, radius, sides=18, part=0, ring_shape=lambda t, a: flat(t, a) if t < 0.99 else 1.0,
           normal_hint=(0, 0, 1))
    head_u = 0.95
    hc = spine(head_u)
    hd = (spine(0.99) - spine(0.9)).normalized()
    side = hd.cross(Vector((0, 0, 1))).normalized()
    up = side.cross(hd).normalized()
    # Saugnaepfe
    for k in range(4):
        a = k * math.tau / 4 + math.pi / 4
        dirv = side * math.cos(a) + up * math.sin(a)
        b.torus(hc + dirv * 0.105, 0.03, 0.011, segs=10, sides=5, axis=dirv, part=2)
    # Hakenkranz am Rostellum
    apex = spine(0.995) + hd * 0.02
    for k in range(14):
        a = k * math.tau / 14
        dirv = side * math.cos(a) + up * math.sin(a)
        p0 = apex - hd * 0.03 + dirv * 0.03
        p1 = p0 + dirv * 0.04 + hd * 0.03
        p2 = p1 - hd * 0.015 + dirv * 0.012
        b.tube([p0, p1, p2], [0.008, 0.005, 0.001], sides=4, part=3, cap_start=False)
    obj = b.to_object("Parasite_Body", col)

    def surf(co, n_, part, t):
        if part == 0:
            return 0.006 * fbm(co * 18, 2) + 0.004 * fbm(co * 50, 1)
        return 0.0
    lib.displace(obj, surf)

    def col_fn(co, n_, part, t):
        if part == 0:
            seg = abs(math.sin(t * N_SEG * math.pi))
            groove = 1 - smoothstep(0.0, 0.25, seg)
            base = lib.lerp3((0.86, 0.66, 0.58), (0.96, 0.84, 0.76), smoothstep(-0.5, 0.5, fbm(co * 7, 2)))
            base = lib.lerp3(base, (0.55, 0.08, 0.1), groove * smoothstep(0.02, 0.1, t) * 0.85)
            # Mittelkanal (Uterus-Aeste) dunkler
            mid = math.exp(-(co.z / 0.02) ** 2) * 0.0
            head = smoothstep(0.88, 0.94, t)
            return lib.lerp3(base, (0.9, 0.5, 0.45), head * 0.5 + mid)
        if part == 2:
            return (0.6, 0.12, 0.14)
        return (0.95, 0.9, 0.75)
    lib.vertex_colors(obj, col_fn)
    lib.assign(obj, lib.material("Parasite_Flesh", rough=0.35, coat=0.9, coat_rough=0.1, sheen=0.2, vcol=True))

    # --- Rig: Kette entlang der Wirbelsaeule
    us = [i / N_BONES for i in range(N_BONES + 1)]
    bpts = [spine(u) for u in us]
    bones = [("root", (0, 0, 0), (0, 0, 0.2), None)]
    bones += lib.chain_bones("seg_", bpts, "root")
    rig = lib.armature("Parasite_Rig", col, bones)
    chain = [f"seg_{k:02d}" for k in range(N_BONES)]
    lib.auto_weights(obj, rig, lambda p: chain if p == 0 else [chain[-1]], "root", power=3)

    # --- Evolution
    eb = Builder()
    for i in range(1, N_SEG - 4):
        u = (i + 0.5) / N_SEG
        c = spine(u)
        d = (spine(u + 0.01) - c).normalized()
        s = d.cross(Vector((0, 0, 1))).normalized()
        r = radius(u)
        for sgn in (-1, 1):
            p0 = c + s * sgn * r * 0.95
            eb.cone(p0, p0 + (s * sgn + d * -0.6).normalized() * 0.07, 0.012, 0.0, segs=4, part=0, cap=False)
    e1 = eb.to_object("EVO1_Parasite_Barbs", col)
    lib.assign(e1, lib.material("Parasite_Barb", (0.95, 0.88, 0.7), rough=0.3, coat=0.5))
    eb = Builder()
    r2 = lib.rng(2)
    for i in range(4, N_SEG - 6, 2):
        u = (i + 0.5) / N_SEG
        c = spine(u) + Vector((0, 0, radius(u) * 0.45))
        for k in range(5):
            eb.icosphere(c + Vector((r2.uniform(-.04, .04), r2.uniform(-.04, .04), r2.uniform(0, .02))),
                         0.018, 1, part=0)
    e2 = eb.to_object("EVO2_Parasite_Eggs", col)
    lib.assign(e2, lib.material("Parasite_Egg", (0.9, 0.8, 0.3), rough=0.25, emit=(0.9, 0.55, 0.1), emit_strength=1.2))
    eb = Builder()
    for k in range(6):
        a = k * math.tau / 6
        dirv = side * math.cos(a) + up * math.sin(a)
        p0 = apex + dirv * 0.04
        path = [p0 + hd * 0.08 * j + dirv * (0.05 * j + 0.02 * math.sin(j * 1.3)) for j in range(6)]
        eb.tube(lib.catmull(path, 3), lambda t: 0.012 * (1 - t) + 0.002, sides=5, part=0, cap_start=False)
    e3 = eb.to_object("EVO3_Parasite_Feelers", col)
    lib.assign(e3, lib.material("Parasite_Feeler", (0.85, 0.2, 0.2), rough=0.3, coat=0.8,
                                emit=(0.9, 0.1, 0.1), emit_strength=0.6))
    lib.auto_weights(e1, rig, lambda p: chain, "root", power=3)
    lib.auto_weights(e2, rig, lambda p: chain, "root", power=3)
    lib.auto_weights(e3, rig, lambda p: [chain[-1]], "root")

    lib.shape_key(obj, "Bloat", lambda co, n_, p, t: 0.03 * smoothstep(0.05, 0.3, t) * (1 - smoothstep(0.8, 0.9, t)) if p == 0 else 0.0)
    lib.shape_key(obj, "Evolve", lambda co, n_, p, t: 0.02 * max(0, fbm(co * 12, 2)) if p == 0 else 0.0)

    def bend(c, f, L, amp, cycles, speed_phase=1.0, vert=0.0):
        for k, bn in enumerate(chain):
            ph = -k * 0.75 * speed_phase
            c.bone(rig, bn, f, rot=(vert * wave(f, L, ph + 1, cycles), 0, amp * wave(f, L, ph, cycles)))

    L = 180
    with Clip("parasite_idle", L) as c:
        for f in frames(L, 5):
            bend(c, f, L, 0.08, 1, vert=0.05)
            c.shape(obj, "Bloat", f, 0.3 + 0.3 * wave(f, L, 0, 2))
    L = 90
    with Clip("parasite_crawl", L) as c:
        for f in frames(L, 3):
            bend(c, f, L, 0.28, 2, 1.0)
            c.bone(rig, "root", f, loc=(0.03 * wave(f, L, 0, 4), 0, 0))
    def chain_scale(c, f, desired):
        """desired: Liste (sx, sy, sz) je Bone in Weltmass -> relative Skalierung,
        da Kind-Bones im glTF die Skalierung des Parents erben."""
        prev = (1.0, 1.0, 1.0)
        for bn, d in zip(chain, desired):
            c.bone(rig, bn, f, scl=tuple(d[i] / prev[i] for i in range(3)))
            prev = d

    L = 60
    with Clip("parasite_contract", L) as c:
        for f in frames(L, 2):
            u = (f - 1) / L
            s = 0.5 - 0.5 * math.cos(math.tau * u)
            des = []
            for k in range(N_BONES):
                lag = 0.5 - 0.5 * math.cos(math.tau * max(0.0, u - k * 0.015))
                des.append((1 + 0.22 * lag, 1 - 0.25 * lag, 1 + 0.22 * lag))
            chain_scale(c, f, des)
            c.shape(obj, "Bloat", f, s)
    L = 60
    with Clip("parasite_expand", L) as c:
        for f in frames(L, 2):
            u = (f - 1) / L
            des = []
            for k in range(N_BONES):
                lag = 0.5 - 0.5 * math.cos(math.tau * max(0.0, u - (N_BONES - k) * 0.015))
                des.append((1 - 0.16 * lag, 1 + 0.2 * lag, 1 - 0.16 * lag))
            chain_scale(c, f, des)
    return {"main": obj}
