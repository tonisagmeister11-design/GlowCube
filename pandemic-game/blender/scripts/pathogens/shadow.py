"""Shadow Plague: schwarzes, herzartiges Organ mit rot gluehenden Adern,
Schatten-Tentakeln (Schleier) und um den Kern kreisenden Splittern."""
import math

from mathutils import Vector, noise

import lib
from lib import Builder, Clip, fbm, frames, smoothstep, wave

ANIMATIONS = ["shadow_idle", "shadow_pulse", "shadow_attack", "shadow_evolution"]
IDLE = "shadow_idle"

L1 = Vector((0.55, 0.1, 0.75)).normalized()
L2 = Vector((-0.55, 0.1, 0.75)).normalized()
N_SHARDS = 27
N_VEIL = 6


def heart_r(d):
    r = 0.42 + 0.16 * max(0.0, d.dot(L1)) ** 3 + 0.16 * max(0.0, d.dot(L2)) ** 3 + 0.24 * max(0.0, -d.z) ** 2.5
    return r * (1 + 0.05 * noise.noise(d * 3))


def surf_pt(d):
    return d * heart_r(d)


def build(col):
    rnd = lib.rng(101)
    b = Builder()
    vs = b.icosphere((0, 0, 0), 1.0, 5, part=0)
    for v in vs:
        v.co = surf_pt(v.co.normalized())
    # Arterien oben
    for i, (dx, dy) in enumerate(((0.12, 0.05), (-0.1, -0.06), (0.0, 0.14))):
        base = Vector((dx, dy, 0.45))
        ctrl = [base - Vector((0, 0, 0.1)), base + Vector((dx * 0.8, dy, 0.18)), base + Vector((dx * 2.6, dy * 2.2, 0.25)),
                base + Vector((dx * 3.8, dy * 3, 0.12))]
        b.tube(lib.catmull(ctrl, 5), lambda t: 0.08 * (1 - 0.35 * t), sides=10, part=1)
    heart = b.to_object("Shadow_Heart", col)
    lib.displace(heart, lambda co, n, p, t: 0.018 * fbm(co * 7, 3) - 0.02 * (1 - smoothstep(0.0, 0.08, abs(noise.noise(co * 4.5)))))

    def col_fn(co, n, part, t):
        v = 1 - smoothstep(0.0, 0.08, abs(noise.noise(co * 4.5)))
        base = lib.lerp3((0.015, 0.01, 0.015), (0.08, 0.02, 0.03), smoothstep(-0.4, 0.6, fbm(co * 7, 3)))
        return lib.lerp3(base, (0.9, 0.02, 0.03), v)
    lib.vertex_colors(heart, col_fn)
    lib.assign(heart, lib.material("Shadow_Obsidian", rough=0.18, metal=0.2, coat=1.0, coat_rough=0.05, vcol=True))

    # Gluehende Adern (Kurven auf der Oberflaeche)
    vb = Builder()
    for i in range(9):
        d = lib.fib_sphere(9, 0.2, rnd)[i]
        step = d.orthogonal().normalized()
        pts = []
        for k in range(26):
            pts.append(surf_pt(d) * 1.012)
            step = (step + Vector((rnd.uniform(-.5, .5), rnd.uniform(-.5, .5), rnd.uniform(-.5, .5)))).normalized()
            d = (d + step * 0.07).normalized()
        vb.tube(pts, lambda t: 0.014 * (1 - t) + 0.004, sides=5, part=0)
    veins = vb.to_object("Shadow_Veins", col)
    glow = lib.material("Shadow_Blood", (1.0, 0.05, 0.05), rough=0.3, emit=(1.0, 0.02, 0.02), emit_strength=4.0)
    lib.assign(veins, glow)

    # Schleier-Tentakel (flache Baender)
    tb = Builder()
    veil = []
    for i in range(N_VEIL):
        a = i * math.tau / N_VEIL + 0.3
        out = Vector((math.cos(a), math.sin(a), 0))
        base = surf_pt((out + Vector((0, 0, -0.3))).normalized()) * 0.95
        ctrl = [base + out * 0.25 * k + Vector((0, 0, -0.18 * k + 0.02 * k * k)) + out.cross(Vector((0, 0, 1))) * 0.1 * math.sin(k) for k in range(6)]
        veil.append(ctrl)
        tb.tube(lib.catmull(ctrl, 5), lambda t: 0.09 * (1 - t) + 0.01, sides=8, part=10 + i,
                ring_shape=lambda t, ang: 1.0 / math.sqrt(math.cos(ang) ** 2 + (math.sin(ang) / 0.18) ** 2),
                normal_hint=(0, 0, 1))
    veils = tb.to_object("Shadow_Veil", col)
    lib.vertex_colors(veils, lambda co, n, p, t: lib.lerp3((0.03, 0.0, 0.01), (0.35, 0.0, 0.04), t))
    lib.assign(veils, lib.material("Shadow_Veil", rough=0.4, coat=0.5, emit=(0.4, 0.0, 0.02), emit_strength=0.3,
                                   alpha=0.8, vcol=True))

    # Splitter auf drei Orbits
    sb = Builder()
    orbits = [(Vector((0, 0, 1)), 0.9), (Vector((1, 0.3, 0.2)).normalized(), 1.0), (Vector((-0.4, 1, 0.3)).normalized(), 1.1)]
    for i in range(N_SHARDS):
        o = i % 3
        ax, R = orbits[o]
        s1 = ax.orthogonal().normalized()
        s2 = ax.cross(s1)
        a = (i // 3) * math.tau / (N_SHARDS // 3) + o
        c = (s1 * math.cos(a) + s2 * math.sin(a)) * R
        dirv = Vector((rnd.uniform(-1, 1), rnd.uniform(-1, 1), rnd.uniform(-1, 1))).normalized()
        sb.cone(c - dirv * 0.05, c + dirv * 0.07, 0.03, 0.0, segs=3, part=30 + o)
    shards = sb.to_object("Shadow_Shards", col, smooth=False)
    lib.assign(shards, lib.material("Shadow_Shard", (0.03, 0.02, 0.03), rough=0.1, metal=0.6, emit=(0.8, 0.0, 0.02),
                                    emit_strength=0.8))

    # --- Evolution
    eb = Builder()
    for d in lib.fib_sphere(18, 0.2, lib.rng(1)):
        if d.z < -0.5:
            continue
        base = surf_pt(d)
        eb.cone(base, base + d * 0.2 + Vector((0, 0, 0.05)), 0.03, 0.0, segs=4, part=0, cap=False)
    e1 = eb.to_object("EVO1_Shadow_Thorns", col)
    lib.assign(e1, lib.material("Shadow_Thorn", (0.02, 0.02, 0.02), rough=0.15, metal=0.5))
    eb = Builder()
    eb.torus((0, 0, 0.1), 1.25, 0.012, segs=48, sides=4, axis=(0.2, 0.1, 1), part=0)
    eb.torus((0, 0, 0.1), 1.35, 0.006, segs=48, sides=4, axis=(0.25, 0.05, 1), part=0)
    e2 = eb.to_object("EVO2_Shadow_Halo", col)
    lib.assign(e2, glow)
    eb = Builder()
    for sgn in (-1, 1):
        base = Vector((sgn * 0.3, 0.1, 0.35))
        for k in range(4):
            ctrl = [base, base + Vector((sgn * 0.4, 0.1, 0.25 + 0.08 * k)), base + Vector((sgn * (0.8 + 0.1 * k), 0.15, 0.1 + 0.2 * k))]
            eb.tube(lib.catmull(ctrl, 4), lambda t: 0.025 * (1 - t) + 0.004, sides=5, part=0, cap_start=False)
    e3 = eb.to_object("EVO3_Shadow_Wings", col)
    lib.assign(e3, lib.material("Shadow_WingBone", (0.05, 0.01, 0.02), rough=0.3, emit=(0.7, 0.0, 0.03), emit_strength=0.6))

    # --- Rig
    bones = [("root", (0, 0, 0), (0, 0, 0.3), None)]
    for o, (ax, R) in enumerate(orbits):
        bones.append((f"orbit{o}", (0, 0, 0), tuple(ax * 0.4), "root"))
    for i, ctrl in enumerate(veil):
        bones += lib.chain_bones(f"veil{i}_", ctrl[:5], "root")
    rig = lib.armature("Shadow_Rig", col, bones)
    lib.auto_weights(heart, rig, lambda p: None, "root")
    lib.auto_weights(veins, rig, lambda p: None, "root")
    lib.auto_weights(shards, rig, lambda p: [f"orbit{p - 30}"], "root")
    lib.auto_weights(veils, rig, lambda p: [f"veil{p - 10}_{k:02d}" for k in range(4)], "root", power=3)
    for o in (e1, e3):
        lib.auto_weights(o, rig, lambda p: None, "root")
    lib.auto_weights(e2, rig, lambda p: ["orbit0"], "root")

    lib.shape_key(heart, "Beat", lambda co, n, p, t: 0.05 if p == 0 else 0.015)
    lib.shape_key(heart, "Evolve", lambda co, n, p, t: 0.06 * max(0.0, lib.ridge(co * 3, 1) - 0.6) if p == 0 else 0.0)
    lib.shape_key(veins, "Beat", lambda co, n, p, t: co.normalized() * 0.05)

    vl = [[f"veil{i}_{k:02d}" for k in range(4)] for i in range(N_VEIL)]

    def heartbeat(u, n=2):
        x = (u * n) % 1.0
        return math.exp(-((x - 0.1) / 0.04) ** 2) + 0.6 * math.exp(-((x - 0.25) / 0.04) ** 2)

    def orbit(c, f, L, speed=1.0, scl=1.0):
        u = (f - 1) / L
        c.bone(rig, "orbit0", f, rot=(0, math.tau * u * speed, 0), scl=scl)
        c.bone(rig, "orbit1", f, rot=(0, -math.tau * u * speed, 0), scl=scl)
        c.bone(rig, "orbit2", f, rot=(0, math.tau * u * speed, 0), scl=scl)

    def sway(c, f, L, amp, cyc, fwd=0.0):
        for i, ch in enumerate(vl):
            for k, bn in enumerate(ch):
                c.bone(rig, bn, f, rot=(amp * wave(f, L, i - k * 0.8, cyc) - fwd * (0.6 if k == 0 else 0.25), 0,
                                        amp * wave(f, L, i * 2 - k * 0.8, cyc)))

    L = 180
    with Clip("shadow_idle", L) as c:
        for f in frames(L, 3):
            u = (f - 1) / L
            hb = heartbeat(u, 3)
            c.shape(heart, "Beat", f, hb)
            c.shape(veins, "Beat", f, hb)
            orbit(c, f, L, 1)
            sway(c, f, L, 0.15, 2)
    L = 60
    with Clip("shadow_pulse", L) as c:
        for f in frames(L, 1):
            u = (f - 1) / L
            hb = heartbeat(u, 2) * 1.2
            c.shape(heart, "Beat", f, hb)
            c.shape(veins, "Beat", f, hb)
            c.bone(rig, "root", f, scl=1 + 0.05 * hb)
            orbit(c, f, L, 0.5, 1 + 0.1 * hb)
    L = 90
    with Clip("shadow_attack", L) as c:
        for f in frames(L, 2):
            u = (f - 1) / L
            wind = smoothstep(0.0, 0.3, u) * (1 - smoothstep(0.3, 0.45, u))
            strike = smoothstep(0.3, 0.45, u) * (1 - smoothstep(0.7, 1.0, u))
            orbit(c, f, L, 2, 1 - 0.45 * wind + 0.35 * strike)
            sway(c, f, L, 0.1, 4, fwd=-0.8 * wind + 1.4 * strike)
            c.shape(heart, "Beat", f, wind + strike)
            c.shape(veins, "Beat", f, strike)
    L = 150
    with Clip("shadow_evolution", L) as c:
        for f in frames(L, 3):
            u = (f - 1) / L
            env = math.sin(math.pi * u)
            c.shape(heart, "Evolve", f, env)
            c.shape(heart, "Beat", f, heartbeat(u, 4) * env)
            c.bone(rig, "root", f, scl=1 + 0.1 * env)
            orbit(c, f, L, 1, 1 + 0.4 * env)
            sway(c, f, L, 0.2 + 0.2 * env, 3)
    return {"main": heart}
