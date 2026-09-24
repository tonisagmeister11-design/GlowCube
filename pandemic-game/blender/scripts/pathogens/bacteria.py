"""Bakterie: laengliche Stabzelle mit Zellmembran, Kapsel, Pili und Flagellen."""
import math

from mathutils import Vector

import lib
from lib import Builder, Clip, fbm, frames, wave

ANIMATIONS = ["bacteria_idle", "bacteria_pulse", "bacteria_mutation"]
IDLE = "bacteria_idle"

HALF = 0.72      # halbe Koerperlaenge
RAD = 0.30       # Zellradius
N_FLAG = 6


def body_path(n=56):
    pts = []
    for i in range(n):
        u = i / (n - 1)
        x = -HALF + 2 * HALF * u
        # leichte Kruemmung + Unregelmaessigkeit
        pts.append(Vector((x, 0.05 * math.sin(u * math.pi * 1.2) + 0.015 * math.sin(u * 9),
                           0.035 * math.sin(u * math.pi + 0.8))))
    return pts


def cap_radius(u, r=RAD):
    x = -HALF + 2 * HALF * u
    edge = HALF - abs(x)
    if edge < r:
        k = 1 - ((r - edge) / r) ** 2
        return r * math.sqrt(max(k, 0.0004))
    return r


def flagellum_path(i, rnd):
    # Flagellen haften am hinteren Pol und seitlich, wellen sich nach hinten
    a = math.tau * i / N_FLAG + rnd.uniform(-0.3, 0.3)
    if i < 3:
        start = Vector((-HALF + 0.05, math.cos(a) * 0.12, math.sin(a) * 0.12))
    else:
        start = Vector((-HALF * 0.35, math.cos(a) * RAD * 0.95, math.sin(a) * RAD * 0.95))
    side = Vector((0, math.cos(a), math.sin(a)))
    length = rnd.uniform(1.0, 1.3)
    pts = []
    n = 36
    for k in range(n):
        u = k / (n - 1)
        amp = 0.05 + 0.09 * u
        ph = u * math.tau * 2.2 + i
        back = Vector((-1, 0, 0)) * length * u
        spread = side * (0.18 * u + 0.08 * math.sqrt(u))
        helix = Vector((0, math.cos(ph), math.sin(ph))) * amp
        pts.append(start + back + spread + helix)
    return pts


def build(col):
    rnd = lib.rng(11)
    b = Builder()
    # --- Zellkoerper (Teil 0)
    path = body_path()
    b.tube(path, lambda t: cap_radius(t), sides=34, part=0,
           ring_shape=lambda t, a: 1 + 0.03 * math.sin(a * 3 + t * 7) + 0.02 * math.sin(a * 5 - t * 11))
    # --- Pili (Teil 2): feine Haerchen auf der Membran
    for k, d in enumerate(lib.fib_sphere(90, 0.12, rnd)):
        x = d.x * HALF * 0.85
        u = (x + HALF) / (2 * HALF)
        r = cap_radius(u)
        if r < RAD * 0.6:
            continue
        rad = Vector((0, d.y, d.z)).normalized()
        p0 = Vector((x, 0, 0)) + rad * (r * 0.97)
        L = rnd.uniform(0.07, 0.13)
        bend = Vector((rnd.uniform(-1, 1), rnd.uniform(-1, 1), rnd.uniform(-1, 1))) * 0.02
        pts = [p0, p0 + rad * L * 0.5 + bend, p0 + rad * L + bend * 2.5]
        b.tube(pts, [0.009, 0.006, 0.003], sides=4, part=2, cap_start=False)
    # --- Flagellen (Teil 10+i)
    flag_paths = []
    for i in range(N_FLAG):
        fp = flagellum_path(i, rnd)
        flag_paths.append(fp)
        b.tube(fp, lambda t: 0.02 * (1 - 0.6 * t) + 0.004, sides=6, part=10 + i, cap_start=False)
    body = b.to_object("Bacteria_Body", col)

    # Membranstruktur: grobe Beulen + feine Poren
    def surf(co, n, part, t):
        if part == 0:
            return 0.022 * fbm(co * 4.0, 3) + 0.007 * fbm(co * 22.0, 2)
        return 0.0
    lib.displace(body, surf)

    def col_fn(co, n, part, t):
        if part == 0:
            g = fbm(co * 4.0, 3)
            fine = fbm(co * 22.0, 2)
            base = lib.lerp3((0.42, 0.05, 0.04), (0.85, 0.22, 0.12), lib.smoothstep(-0.5, 0.6, g))
            base = lib.lerp3(base, (0.25, 0.02, 0.03), max(0, -fine) * 0.8)
            # Pole etwas heller (Zellteilungszone)
            pole = lib.smoothstep(0.45, 0.72, abs(co.x))
            return lib.lerp3(base, (0.95, 0.45, 0.35), pole * 0.4)
        if part == 2:
            return (0.95, 0.55, 0.45)
        return lib.lerp3((0.75, 0.25, 0.2), (0.98, 0.75, 0.62), t)
    lib.vertex_colors(body, col_fn)
    body_mat = lib.material("Bacteria_Membrane", rough=0.42, coat=0.5, coat_rough=0.2, vcol=True)
    lib.assign(body, body_mat)

    # --- Schleimkapsel (halbtransparent, eigenes Objekt)
    cb = Builder()
    cb.tube(path[::2] + [path[-1]] if len(path) % 2 == 0 else path[::2], lambda t: cap_radius(t, RAD + 0.07) + 0.012,
            sides=22, part=1)
    capsule = cb.to_object("Bacteria_Capsule", col)
    lib.displace(capsule, lambda co, n, p, t: 0.012 * fbm(co * 3.0, 2))
    lib.assign(capsule, lib.material("Bacteria_Slime", (0.95, 0.25, 0.2), rough=0.15, coat=1.0,
                                     emit=(0.6, 0.05, 0.04), emit_strength=0.15, alpha=0.22))

    # --- Evolutionsstufen (werden im Spiel je nach Entwicklung eingeblendet)
    evo_objs = []
    # EVO1: Resistenz-Plaques auf der Membran
    eb = Builder()
    for d in lib.fib_sphere(26, 0.2, rnd):
        x = d.x * HALF * 0.7
        rad = Vector((0, d.y, d.z)).normalized()
        c = Vector((x, 0, 0)) + rad * (RAD + 0.02)
        eb.icosphere(c, 0.05, 1, part=0, scale=(1.4, 1.0, 0.45),
                     rot=Vector((0, 0, 1)).rotation_difference(rad))
    evo1 = eb.to_object("EVO1_Bacteria_Plaques", col)
    lib.assign(evo1, lib.material("Bacteria_Plaque", (0.95, 0.6, 0.2), rough=0.35, coat=0.6,
                                  emit=(1.0, 0.35, 0.05), emit_strength=0.6))
    evo_objs.append(evo1)
    # EVO2: zweites Pili-Buendel (lange Sex-Pili)
    eb = Builder()
    for d in lib.fib_sphere(14, 0.3, rnd):
        rad = Vector((0, d.y, d.z)).normalized()
        p0 = Vector((d.x * HALF * 0.6, 0, 0)) + rad * RAD
        pts = [p0 + rad * 0.12 * k + Vector((0.03 * math.sin(k), 0, 0)) for k in range(6)]
        eb.tube(pts, lambda t: 0.012 * (1 - t) + 0.003, sides=5, part=0, cap_start=False)
    evo2 = eb.to_object("EVO2_Bacteria_Pili", col)
    lib.assign(evo2, lib.material("Bacteria_Pili", (1.0, 0.7, 0.55), rough=0.5, coat=0.4))
    evo_objs.append(evo2)
    # EVO3: Toxin-Vesikel (leuchtende Blasen)
    eb = Builder()
    for d in lib.fib_sphere(18, 0.4, rnd):
        rad = Vector((0, d.y, d.z)).normalized()
        c = Vector((d.x * HALF * 0.8, 0, 0)) + rad * (RAD + 0.1 + rnd.uniform(0, 0.06))
        eb.icosphere(c, rnd.uniform(0.03, 0.055), 3, part=0)
    evo3 = eb.to_object("EVO3_Bacteria_Toxins", col)
    lib.assign(evo3, lib.material("Bacteria_Toxin", (1.0, 0.85, 0.2), rough=0.2,
                                  emit=(1.0, 0.6, 0.05), emit_strength=2.5))
    evo_objs.append(evo3)

    # --- Rig: Wirbelsaeule + Flagellen-Ketten
    spine_pts = [Vector((-HALF + 2 * HALF * k / 4, 0, 0)) for k in range(5)]
    bones = [("root", (0, 0, 0), (0, 0, 0.25), None)]
    bones += lib.chain_bones("spine_", spine_pts, "root")
    for i, fp in enumerate(flag_paths):
        idx = [0, 6, 12, 18, 24, 30, 35]
        bones += lib.chain_bones(f"flag{i}_", [fp[k] for k in idx], "spine_00")
    rig = lib.armature("Bacteria_Rig", col, bones)
    spine = [f"spine_{k:02d}" for k in range(4)]

    def rules(part):
        if part in (0, 1, 2):
            return spine
        if part >= 10:
            i = part - 10
            return [f"flag{i}_{k:02d}" for k in range(6)]
        return None
    lib.auto_weights(body, rig, rules, "root", power=3)
    lib.auto_weights(capsule, rig, lambda p: spine, "root", power=3)
    for o in evo_objs:
        lib.auto_weights(o, rig, lambda p: spine, "root", power=3)

    # --- Shape Keys
    lib.shape_key(body, "Pulse", lambda co, n, p, t: 0.035 if p == 0 else 0.0)

    def pinch(co, n, p, t):
        if p not in (0, 2):
            return 0.0
        k = math.exp(-(co.x / 0.13) ** 2)
        radial = Vector((0, co.y, co.z))
        return -radial * 0.55 * k
    lib.shape_key(body, "Divide", pinch)
    lib.shape_key(body, "Evolve", lambda co, n, p, t: max(0.0, fbm(co * 5.5, 2)) * 0.09 if p == 0 else 0.0)
    lib.shape_key(capsule, "Pulse", lambda co, n, p, t: 0.05)
    lib.shape_key(capsule, "Divide", lambda co, n, p, t: -Vector((0, co.y, co.z)) * 0.5 * math.exp(-(co.x / 0.15) ** 2))

    # --- Animationen
    def flag_wave(c, f, L, amp, speed=2):
        for i in range(N_FLAG):
            for k in range(6):
                a = amp * (0.4 + 0.12 * k)
                c.bone(rig, f"flag{i}_{k:02d}", f,
                       rot=(a * wave(f, L, -k * 0.9 + i, speed), 0, a * 0.7 * wave(f, L, -k * 0.9 + i + 1.3, speed)))

    L = 150
    with Clip("bacteria_idle", L) as c:
        for f in frames(L, 5):
            for k, bn in enumerate(spine):
                c.bone(rig, bn, f, rot=(0, 0.03 * wave(f, L, k * 0.8), 0.04 * wave(f, L, k * 0.7 + 1)))
            c.bone(rig, "root", f, loc=(0, 0.02 * wave(f, L, 0, 1), 0.03 * wave(f, L, 1.5, 1)),
                   rot=(0.08 * wave(f, L, 0, 1), 0, 0))
            flag_wave(c, f, L, 0.35, 3)
            c.shape(body, "Pulse", f, 0.35 + 0.35 * wave(f, L, 0, 2))
            c.shape(capsule, "Pulse", f, 0.35 + 0.35 * wave(f, L, 0.4, 2))
    L = 60
    with Clip("bacteria_pulse", L) as c:
        for f in frames(L, 3):
            s = 0.5 - 0.5 * math.cos(math.tau * (f - 1) / L)
            c.shape(body, "Pulse", f, s)
            c.shape(capsule, "Pulse", f, s)
            c.bone(rig, "root", f, scl=1 + 0.06 * s)
            flag_wave(c, f, L, 0.5, 2)
    L = 120
    with Clip("bacteria_mutation", L) as c:
        for f in frames(L, 4):
            u = (f - 1) / L
            env = math.sin(math.pi * u)
            c.shape(body, "Divide", f, env * 0.9)
            c.shape(capsule, "Divide", f, env * 0.9)
            c.shape(body, "Evolve", f, env)
            for k, bn in enumerate(spine):
                c.bone(rig, bn, f, rot=(0.1 * env * wave(f, L, k * 2.1, 6), 0, 0.12 * env * wave(f, L, k, 4)))
            flag_wave(c, f, L, 0.6, 5)
    return {"main": body}
