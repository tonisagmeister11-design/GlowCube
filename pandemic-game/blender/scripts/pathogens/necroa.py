"""Necroa-Virus: deformierte, verrottende Virushuelle mit tumorartigen Auswuechsen,
abgebrochenen Spikes und einem pulsierenden, giftgruenen Kern hinter Loechern."""
import math

from mathutils import Vector, noise

import lib
from lib import Builder, Clip, fbm, frames, smoothstep, wave

ANIMATIONS = ["necroa_idle", "necroa_mutate", "necroa_activate"]
IDLE = "necroa_idle"

TUMORS = [(Vector(v).normalized(), r) for v, r in (((1, 0.3, 0.4), 0.3), ((-0.7, 0.6, 0.3), 0.24), ((0.1, -0.9, -0.2), 0.27),
                                                    ((-0.3, -0.2, -1), 0.2), ((0.5, 0.7, -0.6), 0.17), ((-0.8, -0.5, 0.6), 0.15))]


def shell_r(d):
    r = 0.58 * (1 + 0.16 * noise.noise(d * 1.2 + Vector((4, 4, 4))) + 0.05 * noise.noise(d * 4))
    return r * Vector((d.x * 1.08, d.y * 0.9, d.z)).length


def build(col):
    rnd = lib.rng(81)
    b = Builder()
    vs = b.icosphere((0, 0, 0), 1.0, 5, part=0)
    for v in vs:
        d = v.co.normalized()
        v.co = d * shell_r(d)
    tumor_centers = []
    for i, (d, r) in enumerate(TUMORS):
        c = d * shell_r(d) * 0.92
        tv = b.icosphere(c, r, 4, part=10 + i, scale=(1, 1.0, 0.85), rot=Vector((0, 0, 1)).rotation_difference(d))
        tumor_centers.append((c, d, r))
    # Spikes: teils intakt & verbogen, teils abgebrochen
    for i, d in enumerate(lib.fib_sphere(30, 0.3, rnd)):
        if any(d.dot(td) > 0.85 for td, _ in TUMORS):
            continue
        base = d * shell_r(d) * 0.97
        broken = rnd.random() < 0.55
        L = rnd.uniform(0.08, 0.14) if broken else rnd.uniform(0.25, 0.36)
        side = d.orthogonal().normalized()
        tip = base + d * L + side * (0.02 if broken else rnd.uniform(0.05, 0.12))
        b.tube(lib.catmull([base, (base + tip) / 2 + side * 0.02, tip], 3),
               lambda t: 0.03 * (1 - 0.3 * t) + 0.006, sides=6, part=2 if broken else 3, cap_start=False)
        if not broken:
            b.icosphere(tip, 0.04, 2, part=3, scale=(1, 0.7, 1.2))
    shell = b.to_object("Necroa_Shell", col)
    lib.displace(shell, lambda co, n, p, t: (0.025 * fbm(co * 6, 3) + 0.01 * fbm(co * 20, 2)) if p in (0,) or p >= 10 else 0.0)
    # Loecher in der Huelle -> gruener Kern sichtbar
    lib.delete_faces(shell, lambda c, n, p: p == 0 and noise.noise(c * 3.3 + Vector((1, 2, 3))) > 0.42)

    def col_fn(co, n, part, t):
        rot = smoothstep(-0.3, 0.5, fbm(co * 4 + Vector((9, 1, 1)), 3))
        base = lib.lerp3((0.32, 0.36, 0.28), (0.16, 0.2, 0.14), rot)
        blood = smoothstep(0.2, 0.6, noise.noise(co * 2.5))
        base = lib.lerp3(base, (0.45, 0.04, 0.05), blood * 0.8)
        if part >= 10:
            vein = 1 - smoothstep(0.0, 0.08, abs(noise.noise(co * 9)))
            return lib.lerp3(lib.lerp3((0.55, 0.45, 0.35), (0.6, 0.1, 0.1), blood), (0.35, 0.9, 0.2), vein * 0.7)
        if part == 2:
            return lib.lerp3((0.25, 0.25, 0.2), (0.85, 0.82, 0.7), t)
        if part == 3:
            return lib.lerp3((0.3, 0.28, 0.22), (0.5, 0.08, 0.06), t)
        edge = 1 - smoothstep(0.3, 0.42, noise.noise(co * 3.3 + Vector((1, 2, 3))))
        return lib.lerp3((0.3, 0.8, 0.15), base, edge)
    lib.vertex_colors(shell, col_fn)
    lib.assign(shell, lib.material("Necroa_Rot", rough=0.55, coat=0.5, coat_rough=0.3, vcol=True))

    cb = Builder()
    vs = cb.icosphere((0, 0, 0), 1.0, 4, part=1)
    for v in vs:
        d = v.co.normalized()
        v.co = d * shell_r(d) * 0.84
    core = cb.to_object("Necroa_Core", col)
    lib.displace(core, lambda co, n, p, t: 0.04 * fbm(co * 5, 2))
    lib.assign(core, lib.material("Necroa_Core", (0.4, 1.0, 0.2), rough=0.35, emit=(0.35, 1.0, 0.12), emit_strength=3.5))

    # --- Evolution
    eb = Builder()
    for d in lib.fib_sphere(16, 0.3, lib.rng(2)):
        base = d * shell_r(d)
        eb.cone(base, base + d * 0.14 + d.orthogonal() * 0.03, 0.03, 0.004, segs=4, part=0, cap=False)
    e1 = eb.to_object("EVO1_Necroa_BoneShards", col)
    lib.assign(e1, lib.material("Necroa_Bone", (0.88, 0.84, 0.72), rough=0.5))
    eb = Builder()
    for d in lib.fib_sphere(12, 0.4, lib.rng(3)):
        eb.icosphere(d * shell_r(d) * 1.03, 0.06, 3, part=0, scale=(1, 1, 0.7), rot=Vector((0, 0, 1)).rotation_difference(d))
    e2 = eb.to_object("EVO2_Necroa_Pustules", col)
    lib.assign(e2, lib.material("Necroa_Pus", (0.6, 0.9, 0.2), rough=0.2, coat=1.0, emit=(0.4, 0.9, 0.1), emit_strength=1.4))
    eb = Builder()
    r2 = lib.rng(4)
    for d in lib.fib_sphere(9, 0.3, r2):
        base = d * shell_r(d) * 0.95
        side = d.orthogonal().normalized()
        ctrl = [base + d * 0.12 * k + side * 0.06 * math.sin(k * 1.2) for k in range(6)]
        eb.tube(lib.catmull(ctrl, 3), lambda t: 0.03 * (1 - t) + 0.004, sides=6, part=0, cap_start=False)
    e3 = eb.to_object("EVO3_Necroa_Tendrils", col)
    lib.assign(e3, lib.material("Necroa_Tendril", (0.25, 0.05, 0.05), rough=0.4, coat=0.8, emit=(0.3, 0.8, 0.1), emit_strength=0.3))

    bones = [("core", (0, 0, 0), (0, 0, 0.3), None)]
    for i, (c, d, r) in enumerate(tumor_centers):
        bones.append((f"tumor{i}", tuple(d * 0.3), tuple(c + d * r), "core"))
    rig = lib.armature("Necroa_Rig", col, bones)
    lib.auto_weights(shell, rig, lambda p: [f"tumor{p - 10}"] if p >= 10 else None, "core")
    for o in (core, e1, e2, e3):
        lib.auto_weights(o, rig, lambda p: None, "core")

    lib.shape_key(shell, "Mutate", lambda co, n, p, t: 0.12 * fbm(co * 2.4 + Vector((3, 1, 8)), 2) if p == 0 or p >= 10 else 0.0)
    lib.shape_key(shell, "Activate", lambda co, n, p, t: 0.06 if p == 0 else (0.09 if p >= 10 else 0.0))
    lib.shape_key(shell, "Evolve", lambda co, n, p, t: 0.05 * abs(noise.noise(co * 5)) if p == 0 else 0.0)
    lib.shape_key(core, "Beat", lambda co, n, p, t: 0.07)

    tumors = [f"tumor{i}" for i in range(len(TUMORS))]
    L = 150
    with Clip("necroa_idle", L) as c:
        for f in frames(L, 3):
            beat = max(0.0, math.sin(math.tau * 2 * (f - 1) / L)) ** 4
            c.shape(core, "Beat", f, beat)
            for i, tb in enumerate(tumors):
                c.bone(rig, tb, f, scl=1 + 0.06 * wave(f, L, i * 1.1, 2), rot=(0.04 * wave(f, L, i, 1), 0, 0))
            c.bone(rig, "core", f, rot=(0.04 * wave(f, L, 0, 1), 0.06 * wave(f, L, 1, 1), 0))
    L = 120
    with Clip("necroa_mutate", L) as c:
        for f in frames(L, 2):
            env = math.sin(math.pi * (f - 1) / L)
            c.shape(shell, "Mutate", f, env * (0.85 + 0.15 * wave(f, L, 0, 9)))
            c.shape(shell, "Evolve", f, env)
            for i, tb in enumerate(tumors):
                c.bone(rig, tb, f, scl=1 + 0.35 * env * (0.5 + 0.5 * wave(f, L, i * 2.3, 4)))
            c.shape(core, "Beat", f, 0.5 + 0.5 * wave(f, L, 0, 6))
    L = 90
    with Clip("necroa_activate", L) as c:
        for f in frames(L, 2):
            u = (f - 1) / L
            ramp = smoothstep(0.0, 0.25, u) * (1 - smoothstep(0.8, 1.0, u))
            c.shape(shell, "Activate", f, ramp)
            c.shape(core, "Beat", f, ramp * (0.6 + 0.4 * wave(f, L, 0, 8)))
            c.bone(rig, "core", f, scl=1 + 0.06 * ramp, rot=(0.08 * ramp * wave(f, L, 0, 10), 0, 0))
            for i, tb in enumerate(tumors):
                c.bone(rig, tb, f, scl=1 + 0.2 * ramp * (0.5 + 0.5 * wave(f, L, i, 8)))
    return {"main": shell}
