"""Virus: asymmetrischer viraler Kern mit Kapsid-Platten, Lipidhuelle und
einzeln geriggten Spike-Proteinen (jeder Spike hat einen eigenen Bone)."""
import math

from mathutils import Vector, noise

import lib
from lib import Builder, Clip, fbm, frames, smoothstep, wave

ANIMATIONS = ["virus_idle", "virus_pulse", "virus_mutation", "virus_danger", "virus_selected", "virus_evolution"]
IDLE = "virus_idle"

CORE_R = 0.62
N_SPIKES = 44
DEFORM = Vector((1.0, 0.93, 1.06))


def core_point(d):
    """Asymmetrische Kernoberflaeche in Richtung d."""
    p = Vector((d.x * DEFORM.x, d.y * DEFORM.y, d.z * DEFORM.z)) * CORE_R
    return p * (1 + 0.05 * noise.noise(d * 1.4))


def plates(co):
    dist, _ = noise.voronoi(co * 3.2, distance_metric='DISTANCE', exponent=2.5)
    return dist[1] - dist[0]


def region(co):
    return smoothstep(-0.25, 0.25, noise.noise(co * 1.6 + Vector((3.1, 0.2, 1.7))))


def build(col):
    rnd = lib.rng(7)
    b = Builder()
    core_verts = b.icosphere((0, 0, 0), 1.0, 5, part=0)
    for v in core_verts:
        v.co = core_point(v.co.normalized())
    dirs = lib.fib_sphere(N_SPIKES, 0.18, rnd)
    spike_info = []
    for i, d in enumerate(dirs):
        base = core_point(d) * 0.97
        kind = i % 3
        L = rnd.uniform(0.3, 0.42) if kind != 2 else rnd.uniform(0.2, 0.27)
        bend = Vector((rnd.uniform(-1, 1), rnd.uniform(-1, 1), rnd.uniform(-1, 1))) * 0.05
        tip = base + d * L + bend
        mid = base + d * L * 0.5 + bend * 0.3
        pts = lib.catmull([base, mid, tip], 4)
        part = 100 + i
        if kind == 0:     # Kronen-Spike: Stiel + Knauf
            b.tube(pts, lambda t: 0.034 * (1 - 0.45 * t) + 0.006, sides=7, part=part, cap_start=False, cap_end=False)
            b.icosphere(tip + d * 0.03, 0.062, 2, part=part, scale=(1, 1, 0.75),
                        rot=Vector((0, 0, 1)).rotation_difference(d))
        elif kind == 1:   # Trimer-Keule: drei Lappen
            b.tube(pts, lambda t: 0.03 * (1 - 0.3 * t) + 0.005, sides=6, part=part, cap_start=False, cap_end=False)
            side = d.orthogonal().normalized()
            for k in range(3):
                off = (side * math.cos(k * math.tau / 3) + d.cross(side) * math.sin(k * math.tau / 3)) * 0.028
                b.icosphere(tip + off + d * 0.02, 0.036, 2, part=part)
        else:             # kurzer Pilz-Spike (Neuraminidase-artig)
            b.cone(base, tip, 0.03, 0.012, segs=6, part=part, cap=False)
            b.cone(tip, tip + d * 0.05, 0.075, 0.01, segs=8, part=part)
        spike_info.append((base, tip + d * 0.05, d))
    obj = b.to_object("Virus_Body", col)

    def surf(co, n, part, t):
        if part != 0:
            return 0.0
        r = region(co)
        pl = smoothstep(0.0, 0.12, plates(co))
        plate_disp = 0.035 * pl - 0.02
        env = 0.018 * fbm(co * 9.0, 3)
        return lib.lerp(env, plate_disp, r) + 0.01 * fbm(co * 26.0, 2)
    lib.displace(obj, surf)

    def col_fn(co, n, part, t):
        if part == 0:
            r = region(co)
            pl = smoothstep(0.0, 0.12, plates(co))
            plate_c = lib.lerp3((0.08, 0.005, 0.01), (0.5, 0.03, 0.05), pl)
            env_c = lib.lerp3((0.3, 0.02, 0.05), (0.7, 0.14, 0.13), smoothstep(-0.5, 0.6, fbm(co * 9.0, 3)))
            return lib.lerp3(env_c, plate_c, r)
        return lib.lerp3((0.35, 0.02, 0.04), (1.0, 0.42, 0.25), smoothstep(0.3, 1.0, t))
    lib.vertex_colors(obj, col_fn)
    lib.assign(obj, lib.material("Virus_Capsid", rough=0.38, coat=0.45, coat_rough=0.25, vcol=True))


    # --- Evolutionsobjekte
    evo = []
    eb = Builder()
    for d in lib.fib_sphere(30, 0.25, lib.rng(3)):
        base = core_point(d) * 0.98
        eb.cone(base, base + d * 0.22, 0.018, 0.002, segs=5, part=0, cap=False)
    e1 = eb.to_object("EVO1_Virus_Needles", col)
    lib.assign(e1, lib.material("Virus_Needle", (0.95, 0.35, 0.25), rough=0.3, coat=0.5))
    evo.append(e1)
    eb = Builder()
    r2 = lib.rng(5)
    for d in lib.fib_sphere(8, 0.2, r2):
        base = core_point(d) * 0.95
        side = d.orthogonal().normalized()
        pts = []
        for k in range(14):
            u = k / 13
            pts.append(base + d * (0.62 * u) + (side * math.cos(u * 9) + d.cross(side) * math.sin(u * 9)) * 0.06 * u)
        eb.tube(pts, lambda t: 0.028 * (1 - t) + 0.004, sides=6, part=0, cap_start=False)
    e2 = eb.to_object("EVO2_Virus_Tendrils", col)
    lib.assign(e2, lib.material("Virus_Tendril", (0.55, 0.02, 0.06), rough=0.35, coat=0.8,
                                emit=(0.9, 0.05, 0.1), emit_strength=0.5))
    evo.append(e2)
    eb = Builder()
    for d in lib.fib_sphere(12, 0.1, lib.rng(9)):
        base = core_point(d) * 0.95
        eb.cone(base, base + d * 0.75, 0.07, 0.0, segs=5, part=0, cap=False)
        side = d.orthogonal().normalized()
        for k in (0.35, 0.55):
            p = base + d * 0.75 * k
            eb.cone(p, p + (side + d).normalized() * 0.14, 0.02, 0.0, segs=4, part=0, cap=False)
    e3 = eb.to_object("EVO3_Virus_Crown", col)
    lib.assign(e3, lib.material("Virus_Crown", (0.2, 0.02, 0.02), rough=0.25, metal=0.3, coat=1.0,
                                emit=(1.0, 0.1, 0.02), emit_strength=0.8))
    evo.append(e3)

    # --- Rig: ein Bone pro Spike
    bones = [("core", (0, 0, 0), (0, 0, 0.3), None)]
    for i, (base, tip, d) in enumerate(spike_info):
        bones.append((f"spike_{i:02d}", tuple(base), tuple(tip), "core"))
    rig = lib.armature("Virus_Rig", col, bones)
    lib.auto_weights(obj, rig, lambda p: [f"spike_{p - 100:02d}"] if p >= 100 else None, "core")
    for o in evo:
        lib.auto_weights(o, rig, lambda p: None, "core")

    # --- Shape Keys (nur Kern, Spikes folgen den Bones)
    lib.shape_key(obj, "Pulse", lambda co, n, p, t: 0.045 if p == 0 else 0.0)
    lib.shape_key(obj, "Mutate", lambda co, n, p, t: 0.11 * fbm(co * 2.6 + Vector((5, 1, 2)), 2) if p == 0 else 0.0)
    lib.shape_key(obj, "Danger", lambda co, n, p, t: 0.07 * max(0.0, lib.ridge(co * 3.0, 2) - 1.0) if p == 0 else 0.0)
    lib.shape_key(obj, "Evolve", lambda co, n, p, t: 0.06 * abs(noise.noise(co * 4.0)) if p == 0 else 0.0)

    phase = [rnd.uniform(0, math.tau) for _ in spike_info]
    spikes = [f"spike_{i:02d}" for i in range(len(spike_info))]

    L = 180
    with Clip("virus_idle", L) as c:
        for f in frames(L, 6):
            for i, s in enumerate(spikes):
                c.bone(rig, s, f, rot=(0.09 * wave(f, L, phase[i], 2), 0, 0.09 * wave(f, L, phase[i] + 1.7, 1)),
                       scl=(1, 1 + 0.05 * wave(f, L, phase[i], 3), 1))
            c.shape(obj, "Pulse", f, 0.3 + 0.3 * wave(f, L, 0, 2))
            c.bone(rig, "core", f, rot=(0, 0, 0.15 * wave(f, L, 0, 1)))
    L = 60
    with Clip("virus_pulse", L) as c:
        for f in frames(L, 2):
            s = 0.5 - 0.5 * math.cos(math.tau * (f - 1) / L)
            c.shape(obj, "Pulse", f, s)
            c.bone(rig, "core", f, scl=1 + 0.05 * s)
            for i, sp in enumerate(spikes):
                c.bone(rig, sp, f, scl=(1, 1 + 0.18 * s, 1))
    L = 120
    with Clip("virus_mutation", L) as c:
        for f in frames(L, 3):
            env = math.sin(math.pi * (f - 1) / L)
            c.shape(obj, "Mutate", f, env)
            for i, sp in enumerate(spikes):
                c.bone(rig, sp, f, rot=(0.35 * env * wave(f, L, phase[i], 3), 0.6 * env * wave(f, L, phase[i], 2),
                                        0.35 * env * wave(f, L, phase[i] + 2, 3)),
                       scl=(1, 1 + 0.25 * env * wave(f, L, phase[i], 4), 1))
    L = 90
    with Clip("virus_danger", L) as c:
        for f in frames(L, 2):
            jit = wave(f, L, 0, 9)
            c.shape(obj, "Danger", f, 0.8 + 0.2 * jit)
            c.shape(obj, "Pulse", f, 0.5 + 0.5 * wave(f, L, 0, 3))
            for i, sp in enumerate(spikes):
                c.bone(rig, sp, f, scl=(0.85, 1.3 + 0.08 * wave(f, L, phase[i], 6), 0.85),
                       rot=(0.05 * wave(f, L, phase[i], 9), 0, 0))
    L = 60
    with Clip("virus_selected", L) as c:
        for f in frames(L, 2):
            u = (f - 1) / L
            pop = math.sin(min(u * 2.2, 1.0) * math.pi * 0.5) * (1 + 0.25 * math.sin(u * math.pi * 3) * (1 - u))
            for i, sp in enumerate(spikes):
                c.bone(rig, sp, f, scl=(1, 1 + 0.22 * pop, 1))
            c.shape(obj, "Pulse", f, 0.6 * pop)
            c.bone(rig, "core", f, rot=(0, 0, u * math.tau * 0.25))
    L = 150
    with Clip("virus_evolution", L) as c:
        for f in frames(L, 3):
            u = (f - 1) / L
            for i, (base, tip, d) in enumerate(spike_info):
                front = 1.0 - (d.z + 1) / 2
                k = math.exp(-((u * 1.4 - 0.2 - front) / 0.15) ** 2)
                c.bone(rig, spikes[i], f, scl=(1, 1 + 0.5 * k, 1), rot=(0.3 * k, 0, 0))
            c.shape(obj, "Evolve", f, math.sin(math.pi * u))
            c.shape(obj, "Mutate", f, 0.5 * math.sin(math.pi * u))
    return {"main": obj}
