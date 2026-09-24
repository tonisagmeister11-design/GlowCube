"""Xenolith: ausserirdische Kristallstruktur – zerkluefteter Meteoritengesteins-Kern,
asymmetrische Hexagonal-Kristalle (je ein Bone), wachsende Kleinkristalle,
leuchtender Energiekern."""
import math

from mathutils import Vector, noise

import lib
from lib import Builder, Clip, fbm, frames, smoothstep, wave

ANIMATIONS = ["xenolith_idle", "xenolith_resonance", "xenolith_growth", "xenolith_energy"]
IDLE = "xenolith_idle"


def rock_r(d):
    return 0.42 * (1 + 0.22 * fbm(d * 1.6, 3) + 0.08 * abs(noise.noise(d * 5))) * Vector((d.x * 1.15, d.y, d.z * 0.8)).length


def build(col):
    rnd = lib.rng(111)
    rb = Builder()
    vs = rb.icosphere((0, 0, 0), 1.0, 4, part=0)
    for v in vs:
        d = v.co.normalized()
        v.co = d * rock_r(d)
    rock = rb.to_object("Xenolith_Rock", col, smooth=True)
    lib.displace(rock, lambda co, n, p, t: 0.03 * lib.ridge(co * 5, 2) - 0.03)
    lib.vertex_colors(rock, lambda co, n, p, t: lib.lerp3((0.03, 0.025, 0.04), (0.2, 0.14, 0.22),
                                                           smoothstep(-0.4, 0.6, fbm(co * 6, 3))))
    lib.assign(rock, lib.material("Xeno_Rock", rough=0.75, metal=0.3, vcol=True))

    # Kristalle
    cb = Builder()
    crystals = []
    big = [((0.2, 0.1, 1.0), 0.13, 1.05), ((0.9, -0.3, 0.5), 0.1, 0.8), ((-0.7, 0.5, 0.6), 0.11, 0.9),
           ((-0.3, -0.9, 0.3), 0.08, 0.65), ((0.5, 0.8, 0.1), 0.09, 0.7), ((-0.9, -0.2, -0.3), 0.07, 0.55),
           ((0.6, -0.5, -0.6), 0.075, 0.6), ((0.0, 0.6, -0.8), 0.06, 0.45), ((0.35, -0.2, 0.9), 0.06, 0.62)]
    for i, (dv, r, L) in enumerate(big):
        d = (Vector(dv).normalized() + Vector((rnd.uniform(-.1, .1), rnd.uniform(-.1, .1), rnd.uniform(-.1, .1)))).normalized()
        base = d * rock_r(d) * 0.55
        cb.prism(base, d, r, L, sides=6, tip=rnd.uniform(0.2, 0.32), part=10 + i, twist=rnd.uniform(0, 1), taper=rnd.uniform(0.8, 1.0))
        crystals.append((base, d, L, False))
    small = lib.fib_sphere(14, 0.3, rnd)
    for j, d in enumerate(small):
        base = d * rock_r(d) * 0.85
        L = rnd.uniform(0.14, 0.26)
        cb.prism(base, d, L * 0.18, L, sides=6, tip=0.3, part=30 + j, twist=rnd.uniform(0, 1))
        crystals.append((base, d, L, True))
    crys = cb.to_object("Xenolith_Crystals", col, smooth=False)

    def ccol(co, n, part, t):
        k = t
        return lib.lerp3((0.12, 0.02, 0.25), (0.75, 0.35, 1.0), k ** 1.5) if part < 30 else lib.lerp3((0.1, 0.25, 0.3), (0.4, 1.0, 0.95), k)
    lib.vertex_colors(crys, ccol)
    lib.assign(crys, lib.material("Xeno_Crystal", rough=0.08, metal=0.1, coat=1.0, coat_rough=0.02, ior=2.0,
                                  emit=(0.35, 0.08, 0.6), emit_strength=1.2, vcol=True))

    # Energiekern (zwischen den Kristallen sichtbar)
    kb = Builder()
    kb.icosphere((0, 0, 0.05), 0.3, 3, part=0)
    core = kb.to_object("Xenolith_Core", col)
    lib.displace(core, lambda co, n, p, t: 0.04 * noise.noise(co * 8))
    # Kern ragt durch Risse im Gestein
    lib.delete_faces(rock, lambda c, n, p: noise.noise(c * 4 + Vector((2, 2, 2))) > 0.45)
    lib.assign(core, lib.material("Xeno_Energy", (0.6, 1.0, 0.95), rough=0.2, emit=(0.3, 1.0, 0.9), emit_strength=5.0))

    # --- Evolution
    eb = Builder()
    for k, d in enumerate(lib.fib_sphere(10, 0.3, lib.rng(5))):
        p = d * rnd.uniform(1.0, 1.25)
        eb.prism(p, (d + Vector((0.3, 0.2, 0.5))).normalized(), 0.035, 0.16, sides=4, tip=0.4)
    e1 = eb.to_object("EVO1_Xeno_Shards", col, smooth=False)
    lib.assign(e1, lib.material("Xeno_Shard", (0.4, 1.0, 0.9), rough=0.1, emit=(0.2, 0.9, 0.8), emit_strength=1.5))
    eb = Builder()
    for k in range(18):
        a = k * math.tau / 18
        d = Vector((math.cos(a), math.sin(a), 0.05 * math.sin(a * 3)))
        eb.prism(d * 0.78 + Vector((0, 0, -0.2)), (d + Vector((0, 0, 0.8))).normalized(), 0.035, 0.18 + 0.06 * math.sin(a * 2), sides=5)
    e2 = eb.to_object("EVO2_Xeno_Ring", col, smooth=False)
    lib.assign(e2, lib.material("Xeno_RingCrystal", (0.6, 0.2, 0.9), rough=0.1, emit=(0.5, 0.1, 0.9), emit_strength=1.0))
    eb = Builder()
    eb.prism((0, 0, 0.3), (0.05, -0.05, 1), 0.17, 1.4, sides=6, tip=0.3, twist=0.3)
    e3 = eb.to_object("EVO3_Xeno_Spire", col, smooth=False)
    lib.assign(e3, lib.material("Xeno_Spire", (0.9, 0.3, 1.0), rough=0.05, coat=1.0, emit=(0.8, 0.2, 1.0), emit_strength=2.0))

    bones = [("root", (0, 0, 0), (0, 0, 0.2), None)]
    for i, (base, d, L, sm) in enumerate(crystals):
        bones.append((f"crystal{i:02d}", tuple(base), tuple(base + d * L), "root"))
    rig = lib.armature("Xenolith_Rig", col, bones)
    nb = len(big)

    def rules(p):
        if 10 <= p < 30:
            return [f"crystal{p - 10:02d}"]
        if p >= 30:
            return [f"crystal{nb + p - 30:02d}"]
        return None
    lib.auto_weights(crys, rig, rules, "root")
    for o in (rock, core, e1, e2, e3):
        lib.auto_weights(o, rig, lambda p: None, "root")
    lib.shape_key(core, "Charge", lambda co, n, p, t: 0.12)
    lib.shape_key(rock, "Evolve", lambda co, n, p, t: 0.04 * max(0.0, noise.noise(co * 6)))

    names = [f"crystal{i:02d}" for i in range(len(crystals))]
    ph = [rnd.uniform(0, math.tau) for _ in names]
    L = 180
    with Clip("xenolith_idle", L) as c:
        for f in frames(L, 5):
            c.shape(core, "Charge", f, 0.5 + 0.5 * wave(f, L, 0, 2))
            c.bone(rig, "root", f, rot=(0.04 * wave(f, L, 0, 1), 0, 0.05 * wave(f, L, 1, 1)))
            for i, bn in enumerate(names):
                c.bone(rig, bn, f, scl=(1, 1 + 0.03 * wave(f, L, ph[i], 2), 1))
    L = 90
    with Clip("xenolith_resonance", L) as c:
        for f in frames(L, 1):
            u = (f - 1) / L
            for i, bn in enumerate(names):
                k = math.exp(-((u * 1.2 - i / len(names)) / 0.08) ** 2)
                c.bone(rig, bn, f, scl=(1 + 0.08 * k, 1 + 0.12 * k * (0.6 + 0.4 * math.sin(f * 1.7 + i)), 1 + 0.08 * k),
                       rot=(0.05 * k * math.sin(f * 2.3 + i), 0, 0))
            c.shape(core, "Charge", f, 0.5 + 0.5 * math.sin(u * math.tau * 6))
    L = 150
    with Clip("xenolith_growth", L) as c:
        for f in frames(L, 3):
            u = (f - 1) / L
            for i, bn in enumerate(names):
                if crystals[i][3]:
                    g = smoothstep(0.05 + (i - nb) * 0.04, 0.35 + (i - nb) * 0.04, u)
                    c.bone(rig, bn, f, scl=(0.2 + 0.8 * g, 0.01 + 0.99 * g, 0.2 + 0.8 * g))
                else:
                    c.bone(rig, bn, f, scl=(1, 1 + 0.06 * math.sin(math.pi * u), 1))
            c.shape(core, "Charge", f, math.sin(math.pi * u))
    L = 120
    with Clip("xenolith_energy", L) as c:
        for f in frames(L, 2):
            u = (f - 1) / L
            env = smoothstep(0.0, 0.3, u) * (1 - smoothstep(0.7, 1.0, u))
            c.shape(core, "Charge", f, env * (0.7 + 0.3 * wave(f, L, 0, 10)))
            c.shape(rock, "Evolve", f, env)
            for i, (base, d, Lc, sm) in enumerate(crystals):
                c.bone(rig, names[i], f, loc=(0, 0.08 * env * (0.6 + 0.4 * wave(f, L, ph[i], 6)), 0))
    return {"main": crys}
