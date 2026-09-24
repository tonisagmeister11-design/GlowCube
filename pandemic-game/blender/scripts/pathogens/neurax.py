"""Neurax-Wurm: vielgliedriger Wurm mit komplexem Kopf (Mandibeln), neuronalen
Filamenten, leuchtenden Ganglien und vollstaendig segmentiertem Rig."""
import math

from mathutils import Vector

import lib
from lib import Builder, Clip, fbm, frames, smoothstep, wave

ANIMATIONS = ["neurax_idle", "neurax_crawl", "neurax_pulse", "neurax_control"]
IDLE = "neurax_idle"

N_SEG = 16
N_SPINE = 10
N_FIL = 12


def spine(u):
    # Schwanz (u=0) nach oben gekruemmt, Kopf (u=1) leicht gesenkt
    x = -1.05 + 2.0 * u
    z = 0.42 * (1 - u) ** 2.4 - 0.05 * smoothstep(0.85, 1.0, u)
    y = 0.12 * math.sin(u * math.pi * 1.3)
    return Vector((x, y, z))


def radius(u):
    body = 0.04 + 0.24 * smoothstep(0.0, 0.55, u) - 0.05 * smoothstep(0.8, 0.9, u)
    seg = abs(math.sin(u * N_SEG * math.pi)) ** 0.5
    ridge = 1 + 0.14 * seg * smoothstep(0.05, 0.15, u) * (1 - smoothstep(0.84, 0.88, u))
    head = 0.07 * math.exp(-((u - 0.93) / 0.04) ** 2)
    tip = math.sqrt(max(0.0, 1 - ((u - 0.97) / 0.03) ** 2)) if u > 0.97 else 1.0
    return max(0.006, (body * ridge + head) * tip)


def frame_at(u):
    p = spine(u)
    d = (spine(min(1, u + 0.01)) - spine(max(0, u - 0.01))).normalized()
    s = d.cross(Vector((0, 0, 1))).normalized()
    up = s.cross(d).normalized()
    return p, d, s, up


def build(col):
    rnd = lib.rng(71)
    b = Builder()
    n = 150
    b.tube([spine(i / (n - 1)) for i in range(n)], radius, sides=20, part=0,
           ring_shape=lambda t, a: 1 - 0.1 * max(0.0, -math.sin(a)) * (1 - smoothstep(0.85, 0.9, t)), normal_hint=(0, 0, 1))
    # Rueckenstacheln je Segment
    for i in range(2, N_SEG - 2):
        u = (i + 0.5) / N_SEG
        p, d, s, up = frame_at(u)
        r = radius(u)
        for sgn in (-0.35, 0.35):
            base = p + (up + s * sgn).normalized() * r * 0.95
            b.cone(base, base + (up + s * sgn * 1.4 - d * 0.5).normalized() * 0.09, 0.02, 0.0, segs=5, part=2, cap=False)
    # Kopf: Mandibeln
    hp, hd, hs, hu = frame_at(0.95)
    apex = spine(1.0)
    pincers = []
    for sgn in (-1, 1):
        base = apex - hd * 0.05 + hs * sgn * 0.07 - hu * 0.02
        ctrl = [base, base + hd * 0.1 + hs * sgn * 0.06, base + hd * 0.2 + hs * sgn * 0.02, base + hd * 0.24 - hs * sgn * 0.05]
        b.tube(lib.catmull(ctrl, 4), lambda t: 0.028 * (1 - t) + 0.003, sides=6, part=3 if sgn < 0 else 4, cap_start=False)
        pincers.append(ctrl)
    # Neuronale Filamente, die vom Kopf herabhaengen
    fil = []
    for i in range(N_FIL):
        a = math.pi * (0.15 + 0.7 * i / (N_FIL - 1))
        root = hp - hd * 0.02 + (hs * math.cos(a) - hu * math.sin(a)) * 0.13
        ctrl = [root]
        for k in range(1, 6):
            ctrl.append(root + (-hu * 0.14 + hs * math.cos(a) * 0.05 - hd * 0.06) * k +
                        Vector((rnd.uniform(-.03, .03), rnd.uniform(-.03, .03), 0.01 * k * k)))
        fil.append(ctrl)
        b.tube(lib.catmull(ctrl, 5), lambda t: 0.012 * (1 - t) + 0.0025, sides=5, part=10 + i, cap_start=False)
    body = b.to_object("Neurax_Body", col)
    lib.displace(body, lambda co, n_, p, t: (0.006 * fbm(co * 20, 2)) if p == 0 else 0.0)

    def col_fn(co, n_, part, t):
        if part == 0:
            seg = abs(math.sin(t * N_SEG * math.pi))
            groove = 1 - smoothstep(0.0, 0.3, seg)
            belly = smoothstep(0.2, -0.6, n_.z)
            base = lib.lerp3((0.62, 0.42, 0.34), (0.86, 0.66, 0.55), smoothstep(-0.5, 0.5, fbm(co * 6, 2)))
            base = lib.lerp3(base, (0.95, 0.8, 0.7), belly * 0.5)
            return lib.lerp3(base, (0.3, 0.1, 0.08), groove * 0.8)
        if part == 2:
            return (0.35, 0.15, 0.1)
        if part in (3, 4):
            return lib.lerp3((0.4, 0.15, 0.1), (0.1, 0.05, 0.04), t)
        return lib.lerp3((0.8, 0.55, 0.45), (1.0, 0.75, 0.5), t)
    lib.vertex_colors(body, col_fn)
    lib.assign(body, lib.material("Neurax_Flesh", rough=0.42, coat=0.7, coat_rough=0.2, sheen=0.25, vcol=True))

    # Ganglien + Nervenstrang (leuchtend, eigenes Objekt)
    gb = Builder()
    npts = []
    for i in range(40):
        u = 0.1 + 0.82 * i / 39
        p, d, s, up = frame_at(u)
        npts.append(p + up * radius(u) * 1.0)
    gb.tube(npts, lambda t: 0.012, sides=5, part=0)
    for i in range(1, N_SEG - 2):
        u = (i + 0.5) / N_SEG
        p, d, s, up = frame_at(u)
        gb.icosphere(p + up * radius(u) * 1.02, 0.03 + 0.012 * u, 2, part=0)
    nerves = gb.to_object("Neurax_Ganglia", col)
    glow = lib.material("Neurax_Neural", (1.0, 0.55, 0.15), rough=0.25, emit=(1.0, 0.45, 0.08), emit_strength=3.0)
    lib.assign(nerves, glow)

    # --- Rig: 10 Wirbel, 12 Filament-Ketten (je 4 Bones), 2 Mandibeln
    bones = [("root", (0, 0, 0), (0, 0, 0.2), None)]
    bones += lib.chain_bones("spine_", [spine(k / N_SPINE) for k in range(N_SPINE + 1)], "root")
    head_bone = f"spine_{N_SPINE - 1:02d}"
    for i, ctrl in enumerate(fil):
        bones += lib.chain_bones(f"fil{i:02d}_", ctrl[:5], head_bone)
    for j, ctrl in enumerate(pincers):
        bones.append((f"jaw{j}", tuple(ctrl[0]), tuple(ctrl[-1]), head_bone))
    rig = lib.armature("Neurax_Rig", col, bones)
    spn = [f"spine_{k:02d}" for k in range(N_SPINE)]

    def rules(p):
        if p in (0, 2):
            return spn
        if p in (3, 4):
            return [f"jaw{p - 3}"]
        if p >= 10:
            return [f"fil{p - 10:02d}_{k:02d}" for k in range(4)]
        return None
    lib.auto_weights(body, rig, rules, "root", power=3)
    lib.auto_weights(nerves, rig, lambda p: spn, "root", power=3)

    lib.shape_key(body, "Pulse", lambda co, n_, p, t: 0.025 * smoothstep(0.1, 0.4, t) if p == 0 else 0.0)
    lib.shape_key(body, "Evolve", lambda co, n_, p, t: 0.02 * max(0.0, fbm(co * 9, 2)) if p == 0 else 0.0)
    lib.shape_key(nerves, "Glow", lambda co, n_, p, t: 0.022)

    # --- Evolution
    eb = Builder()
    hp2 = apex + hu * 0.05
    for k in range(5):
        a = (k - 2) * 0.35
        dirv = (hu + hs * math.sin(a) + hd * 0.3).normalized()
        ctrl = [hp2 - hd * 0.05, hp2 + dirv * 0.18, hp2 + dirv * 0.32 + hd * 0.08]
        eb.tube(lib.catmull(ctrl, 4), lambda t: 0.014 * (1 - t) + 0.003, sides=5, part=0, cap_start=False)
        eb.icosphere(ctrl[-1], 0.022, 2, part=0)
    e1 = eb.to_object("EVO1_Neurax_Antennae", col)
    lib.assign(e1, glow)
    eb = Builder()
    for i in range(2, N_SEG - 3):
        u = (i + 0.5) / N_SEG
        p, d, s, up = frame_at(u)
        for sgn in (-1, 1):
            base = p + s * sgn * radius(u) * 0.9 - up * 0.04
            eb.cone(base, base + (s * sgn - up * 0.6).normalized() * 0.12, 0.018, 0.004, segs=5, part=0, cap=False)
    e2 = eb.to_object("EVO2_Neurax_Legs", col)
    lib.assign(e2, lib.material("Neurax_Leg", (0.3, 0.12, 0.08), rough=0.4, coat=0.5))
    eb = Builder()
    for i in range(3, N_SEG - 3, 2):
        u = (i + 0.5) / N_SEG
        p, d, s, up = frame_at(u)
        eb.torus(p, radius(u) * 1.08, 0.012, segs=24, sides=5, axis=d, part=0)
    e3 = eb.to_object("EVO3_Neurax_Rings", col)
    lib.assign(e3, glow)
    lib.auto_weights(e1, rig, lambda p: [head_bone], "root")
    lib.auto_weights(e2, rig, lambda p: spn, "root", power=3)
    lib.auto_weights(e3, rig, lambda p: spn, "root", power=3)

    fils = [[f"fil{i:02d}_{k:02d}" for k in range(4)] for i in range(N_FIL)]

    def dangle(c, f, L, amp, cyc, spread=0.0, lift=0.0):
        for i, ch in enumerate(fils):
            side = (i - (N_FIL - 1) / 2) / (N_FIL / 2)
            for k, bn in enumerate(ch):
                c.bone(rig, bn, f, rot=(amp * wave(f, L, i * 0.7 - k * 0.9, cyc) - lift * (1 if k == 0 else 0.3),
                                        0, amp * 0.6 * wave(f, L, i - k, cyc) + spread * side * (1 if k == 0 else 0.2)))

    L = 180
    with Clip("neurax_idle", L) as c:
        for f in frames(L, 5):
            for k, bn in enumerate(spn):
                c.bone(rig, bn, f, rot=(0.03 * wave(f, L, k * 0.5, 1), 0, 0.05 * wave(f, L, k * 0.6, 1)))
            dangle(c, f, L, 0.12, 2)
            c.shape(body, "Pulse", f, 0.3 + 0.3 * wave(f, L, 0, 2))
            c.shape(nerves, "Glow", f, 0.5 + 0.5 * wave(f, L, 0, 3))
            for j in range(2):
                c.bone(rig, f"jaw{j}", f, rot=(0, 0, (0.12 if j else -0.12) * (0.5 + 0.5 * wave(f, L, 0, 3))))
    L = 90
    with Clip("neurax_crawl", L) as c:
        for f in frames(L, 2):
            for k, bn in enumerate(spn):
                c.bone(rig, bn, f, rot=(0.12 * wave(f, L, -k * 0.7, 2), 0, 0.18 * wave(f, L, -k * 0.7 + 1, 2)),
                       scl=(1, 1 + 0.06 * wave(f, L, -k * 0.9, 2), 1))
            dangle(c, f, L, 0.25, 2)
            for j in range(2):
                c.bone(rig, f"jaw{j}", f, rot=(0, 0, (0.3 if j else -0.3) * (0.5 + 0.5 * wave(f, L, 0, 4))))
    L = 60
    with Clip("neurax_pulse", L) as c:
        for f in frames(L, 2):
            s = 0.5 - 0.5 * math.cos(math.tau * (f - 1) / L)
            c.shape(body, "Pulse", f, s)
            c.shape(nerves, "Glow", f, s)
            c.bone(rig, "root", f, scl=1 + 0.04 * s)
            dangle(c, f, L, 0.1, 1)
    L = 150
    with Clip("neurax_control", L) as c:
        for f in frames(L, 3):
            u = (f - 1) / L
            env = smoothstep(0.0, 0.3, u) * (1 - smoothstep(0.75, 1.0, u))
            for k, bn in enumerate(spn):
                raise_ = env * 0.12 * smoothstep(5, 9, k)
                c.bone(rig, bn, f, rot=(-raise_, 0, 0.04 * wave(f, L, k, 3)))
            dangle(c, f, L, 0.08 + 0.05 * env, 6, spread=0.9 * env, lift=1.4 * env)
            c.shape(nerves, "Glow", f, env * (0.7 + 0.3 * wave(f, L, 0, 12)))
            for j in range(2):
                c.bone(rig, f"jaw{j}", f, rot=(0, 0, (0.5 if j else -0.5) * env))
    return {"main": body}
