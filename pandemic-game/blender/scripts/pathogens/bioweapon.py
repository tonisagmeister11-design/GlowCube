"""Bio-Waffe (fiktiv): instabile, gelappte Masse mit aufgerissener Huelle,
gluehendem Innenkern, Dornen und vier geriggten Tentakeln."""
import math

from mathutils import Vector, noise

import lib
from lib import Builder, Clip, fbm, frames, smoothstep, wave

ANIMATIONS = ["bioweapon_idle", "bioweapon_growth", "bioweapon_mutation", "bioweapon_overload"]
IDLE = "bioweapon_idle"

LOBES = [Vector(v).normalized() for v in ((1, 0.2, 0.5), (-0.6, 0.8, 0.1), (-0.3, -0.9, 0.4), (0.2, 0.1, -1), (0.6, -0.4, -0.3), (-0.8, -0.1, 0.7))]
LOBE_W = [0.22, 0.18, 0.2, 0.15, 0.12, 0.16]


def shell_r(d):
    r = 0.5
    for c, w in zip(LOBES, LOBE_W):
        r += w * max(0.0, d.dot(c)) ** 4
    return r * (1 + 0.07 * noise.noise(d * 2.3))


def cracks(co):
    w = Vector((noise.noise(co * 1.5), noise.noise(co * 1.5 + Vector((3, 3, 3))), 0)) * 0.3
    return abs(noise.noise((co + w) * 3.0))


def build(col):
    rnd = lib.rng(61)
    b = Builder()
    vs = b.icosphere((0, 0, 0), 1.0, 5, part=0)
    for v in vs:
        d = v.co.normalized()
        v.co = d * shell_r(d)
    # Dornen
    thorn_dirs = lib.fib_sphere(20, 0.25, rnd)
    for i, d in enumerate(thorn_dirs):
        base = d * shell_r(d) * 0.97
        side = d.orthogonal().normalized()
        L = rnd.uniform(0.18, 0.34)
        pts = [base + d * L * u + side * 0.06 * u * u for u in (0, 0.33, 0.66, 1.0)]
        b.tube(lib.catmull(pts, 3), lambda t: 0.045 * (1 - t) + 0.002, sides=6, part=2, cap_start=False)
    # Tentakel
    tent = []
    for i, c in enumerate(LOBES[:4]):
        base = c * shell_r(c) * 0.92
        side = c.orthogonal().normalized()
        ctrl = [base + c * 0.22 * k + side * 0.08 * math.sin(k * 1.4) + c.cross(side) * 0.05 * math.cos(k) for k in range(6)]
        tent.append(ctrl)
        b.tube(lib.catmull(ctrl, 5), lambda t: 0.07 * (1 - t) ** 0.8 + 0.006, sides=8, part=10 + i, cap_start=False)
    shell = b.to_object("Bioweapon_Shell", col)
    lib.displace(shell, lambda co, n, p, t: (0.03 * fbm(co * 5, 3) + 0.012 * fbm(co * 18, 2)) if p == 0 else
                 (0.01 * noise.noise(co * 12) if p >= 10 else 0.0))
    # Huelle aufreissen: Risse zeigen den gluehenden Kern
    lib.delete_faces(shell, lambda c, n, p: p == 0 and cracks(c) < 0.07)

    def col_fn(co, n, part, t):
        if part == 0:
            k = cracks(co)
            edge = 1 - smoothstep(0.07, 0.2, k)
            base = lib.lerp3((0.12, 0.01, 0.02), (0.45, 0.03, 0.05), smoothstep(-0.4, 0.6, fbm(co * 5, 3)))
            return lib.lerp3(base, (1.0, 0.45, 0.1), edge)
        if part == 2:
            return lib.lerp3((0.25, 0.02, 0.02), (0.9, 0.85, 0.7), t)
        return lib.lerp3((0.4, 0.03, 0.06), (0.9, 0.3, 0.2), t)
    lib.vertex_colors(shell, col_fn)
    lib.assign(shell, lib.material("Bioweapon_Flesh", rough=0.3, coat=1.0, coat_rough=0.08, vcol=True))

    cb = Builder()
    vs = cb.icosphere((0, 0, 0), 1.0, 4, part=1)
    for v in vs:
        d = v.co.normalized()
        v.co = d * shell_r(d) * 0.86
    core = cb.to_object("Bioweapon_Core", col)
    lib.displace(core, lambda co, n, p, t: 0.03 * fbm(co * 6, 2))
    lib.assign(core, lib.material("Bioweapon_Core", (1.0, 0.55, 0.1), rough=0.3, emit=(1.0, 0.35, 0.03), emit_strength=5.0))

    # --- Evolution
    eb = Builder()
    for d in lib.fib_sphere(26, 0.3, lib.rng(3)):
        base = d * shell_r(d)
        eb.cone(base, base + d * 0.16, 0.025, 0.0, segs=5, part=0, cap=False)
    e1 = eb.to_object("EVO1_Bioweapon_Spines", col)
    lib.assign(e1, lib.material("Bioweapon_Spine", (0.9, 0.85, 0.7), rough=0.3, coat=0.4))
    eb = Builder()
    for d in lib.fib_sphere(14, 0.35, lib.rng(4)):
        eb.icosphere(d * shell_r(d) * 1.02, rnd.uniform(0.05, 0.09), 2, part=0)
    e2 = eb.to_object("EVO2_Bioweapon_Pustules", col)
    lib.assign(e2, lib.material("Bioweapon_Pustule", (0.9, 0.9, 0.2), rough=0.2, coat=1.0,
                                emit=(0.7, 0.9, 0.05), emit_strength=1.6))
    eb = Builder()
    for d in lib.fib_sphere(7, 0.1, lib.rng(5)):
        base = d * shell_r(d) * 0.95
        eb.cone(base, base + d * 0.55, 0.09, 0.0, segs=4, part=0, cap=False)
    e3 = eb.to_object("EVO3_Bioweapon_Horns", col)
    lib.assign(e3, lib.material("Bioweapon_Horn", (0.05, 0.05, 0.05), rough=0.2, metal=0.5,
                                emit=(1.0, 0.2, 0.0), emit_strength=0.4))

    bones = [("core", (0, 0, 0), (0, 0, 0.3), None)]
    for i, ctrl in enumerate(tent):
        bones += lib.chain_bones(f"tent{i}_", ctrl[:5], "core")
    rig = lib.armature("Bioweapon_Rig", col, bones)

    def rules(p):
        if p >= 10:
            return [f"tent{p - 10}_{k:02d}" for k in range(4)]
        return None
    lib.auto_weights(shell, rig, rules, "core", power=3)
    for o in (core, e1, e2, e3):
        lib.auto_weights(o, rig, lambda p: None, "core")

    for o in (shell, core):
        lib.shape_key(o, "Pulse", lambda co, n, p, t: 0.05 if p in (0, 1) else 0.0)
        lib.shape_key(o, "Growth", lambda co, n, p, t: sum(0.22 * max(0.0, co.normalized().dot(c)) ** 3 for c in LOBES[:3])
                      if p in (0, 1) else 0.0)
        lib.shape_key(o, "Mutation", lambda co, n, p, t: 0.14 * fbm(co * 3.2 + Vector((2, 7, 1)), 2) if p in (0, 1) else 0.0)
        lib.shape_key(o, "Overload", lambda co, n, p, t: (0.12 + 0.08 * lib.ridge(co * 4, 1)) if p in (0, 1) else 0.0)
    lib.shape_key(shell, "Evolve", lambda co, n, p, t: 0.08 * max(0.0, noise.noise(co * 4.2)) if p == 0 else 0.0)

    tents = [[f"tent{i}_{k:02d}" for k in range(4)] for i in range(4)]

    def writhe(c, f, L, amp, cyc):
        for i, ch in enumerate(tents):
            for k, bn in enumerate(ch):
                c.bone(rig, bn, f, rot=(amp * wave(f, L, i * 1.3 - k * 0.8, cyc), 0, amp * wave(f, L, i * 2 - k * 0.8 + 1.4, cyc)))

    L = 120
    with Clip("bioweapon_idle", L) as c:
        for f in frames(L, 3):
            beat = max(0.0, math.sin(math.tau * 3 * (f - 1) / L)) ** 3
            for o in (shell, core):
                c.shape(o, "Pulse", f, 0.2 + 0.8 * beat)
            c.bone(rig, "core", f, rot=(0.05 * wave(f, L, 0, 1), 0.08 * wave(f, L, 1, 1), 0.05 * wave(f, L, 0, 2)))
            writhe(c, f, L, 0.2, 2)
    L = 150
    with Clip("bioweapon_growth", L) as c:
        for f in frames(L, 3):
            u = (f - 1) / L
            g = math.sin(math.pi * u)
            for o in (shell, core):
                c.shape(o, "Growth", f, g)
                c.shape(o, "Pulse", f, 0.5 + 0.5 * wave(f, L, 0, 5))
            c.bone(rig, "core", f, scl=1 + 0.12 * g)
            writhe(c, f, L, 0.3 * g + 0.05, 3)
    L = 120
    with Clip("bioweapon_mutation", L) as c:
        for f in frames(L, 2):
            env = math.sin(math.pi * (f - 1) / L)
            for o in (shell, core):
                c.shape(o, "Mutation", f, env * (0.8 + 0.2 * wave(f, L, 0, 11)))
            c.shape(shell, "Evolve", f, env)
            c.bone(rig, "core", f, rot=(0.12 * env * wave(f, L, 0, 7), 0.12 * env * wave(f, L, 2, 5), 0))
            writhe(c, f, L, 0.45 * env + 0.05, 5)
    L = 90
    with Clip("bioweapon_overload", L) as c:
        for f in frames(L, 2):
            u = (f - 1) / L
            ramp = smoothstep(0.0, 0.7, u) * (1 - smoothstep(0.85, 1.0, u))
            jit = wave(f, L, 0, 15)
            for o in (shell, core):
                c.shape(o, "Overload", f, ramp * (0.8 + 0.2 * jit))
                c.shape(o, "Pulse", f, 0.5 + 0.5 * jit)
            c.bone(rig, "core", f, scl=1 + 0.08 * ramp + 0.02 * jit)
            writhe(c, f, L, 0.55 * ramp + 0.1, 6)
    return {"main": shell}
