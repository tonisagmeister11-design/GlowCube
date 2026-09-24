"""Pilz: gewundener Fruchtkoerper, verzweigtes Myzel (Hyphen), Sporangien
und einzelne Sporen-Objekte, die sich in fungus_spore_release loesen."""
import math

from mathutils import Vector, noise

import lib
from lib import Builder, Clip, fbm, frames, smoothstep, wave

ANIMATIONS = ["fungus_idle", "fungus_growth", "fungus_spore_release", "fungus_mutation"]
IDLE = "fungus_idle"

N_HYPHAE = 6
N_SPORANGIA = 7
BODY_C = Vector((0, 0, 0.12))


def body_shape(d):
    p = Vector((d.x * 0.72, d.y * 0.64, d.z * 0.5))
    # Lappung (zwei Hemisphaeren wie ein Gehirn)
    lobe = 1 - 0.1 * math.exp(-(d.x / 0.12) ** 2) * max(0.0, d.z)
    return BODY_C + p * lobe * (1 + 0.08 * noise.noise(d * 2.0))


def gyri(co):
    """Gyri (1) und schmale Sulci (0), mit Domain-Warp fuer maeandernde Windungen."""
    w = Vector((noise.noise(co * 1.7), noise.noise(co * 1.7 + Vector((5, 2, 1))), 0)) * 0.35
    n = noise.noise((co + w) * 3.6, noise_basis='PERLIN_NEW')
    return smoothstep(0.0, 0.2, abs(n))


def build(col):
    rnd = lib.rng(21)
    b = Builder()
    vs = b.icosphere((0, 0, 0), 1.0, 6, part=0)
    for v in vs:
        v.co = body_shape(v.co.normalized())
    # Stiel
    b.tube([Vector((0, 0, -0.1)), Vector((0.02, 0.01, -0.3)), Vector((-0.01, 0, -0.48))],
           lambda t: 0.2 - 0.06 * t, sides=14, part=1, cap_start=False)
    # Hyphen (Myzel) mit Seitenzweigen
    hyph_paths = []
    for i in range(N_HYPHAE):
        a = math.tau * i / N_HYPHAE + rnd.uniform(-0.3, 0.3)
        out = Vector((math.cos(a), math.sin(a), 0))
        ctrl = [Vector((0, 0, -0.42)) + out * 0.1]
        for k in range(1, 6):
            ctrl.append(ctrl[0] + out * 0.2 * k + Vector((rnd.uniform(-.06, .06), rnd.uniform(-.06, .06),
                                                             -0.1 * k + 0.012 * k * k)))
        pts = lib.catmull(ctrl, 4)
        hyph_paths.append(ctrl)
        b.tube(pts, lambda t: 0.055 * (1 - t) + 0.01, sides=7, part=20 + i, cap_start=False)
        for k in (2, 3, 4):
            p0 = ctrl[k]
            side = out.cross(Vector((0, 0, 1))) * rnd.choice((-1, 1))
            br = [p0, p0 + (side * 0.18 + out * 0.08 + Vector((0, 0, -0.05))),
                  p0 + (side * 0.3 + out * 0.12 + Vector((0, 0, -0.12)))]
            b.tube(lib.catmull(br, 3), lambda t: 0.022 * (1 - t) + 0.006, sides=5, part=20 + i, cap_start=False)
    # Sporangien (Stiel + Kopf) auf der Oberseite
    spor = []
    dirs = [d for d in lib.fib_sphere(40, 0.1, rnd) if d.z > 0.35][:N_SPORANGIA]
    for i, d in enumerate(dirs):
        base = body_shape(d) - d * 0.02
        L = rnd.uniform(0.28, 0.42)
        tip = base + (d + Vector((0, 0, 0.8))).normalized() * L
        mid = (base + tip) / 2 + d.orthogonal().normalized() * 0.03
        b.tube(lib.catmull([base, mid, tip], 4), lambda t: 0.028 - 0.01 * t, sides=6, part=50 + i,
               cap_start=False, cap_end=False)
        head = tip + (tip - base).normalized() * 0.07
        b.icosphere(head, 0.085, 3, part=50 + i, scale=(1, 1, 0.9))
        spor.append((base, tip, head))
    obj = b.to_object("Fungus_Body", col)

    def surf(co, n, part, t):
        if part == 0:
            g = gyri(co)
            return 0.055 * g - 0.04 + 0.006 * fbm(co * 20, 2)
        if part == 1:
            return 0.02 * fbm(co * 8, 2)
        if part >= 50:
            return 0.012 * abs(noise.noise(co * 40))
        return 0.004 * noise.noise(co * 30)
    lib.displace(obj, surf)

    def col_fn(co, n, part, t):
        if part == 0:
            g = gyri(co)
            hi = lib.lerp3((0.78, 0.42, 0.4), (0.95, 0.72, 0.66), smoothstep(-0.4, 0.5, fbm(co * 6, 2)))
            return lib.lerp3((0.22, 0.01, 0.03), hi, g)
        if part == 1:
            return (0.7, 0.45, 0.4)
        if part >= 50:
            head = smoothstep(0.85, 1.0, t) if t > 0 else 1.0
            return lib.lerp3((0.75, 0.55, 0.45), (0.55, 0.06, 0.08), head)
        return lib.lerp3((0.85, 0.65, 0.58), (0.98, 0.9, 0.85), t)
    lib.vertex_colors(obj, col_fn)
    lib.assign(obj, lib.material("Fungus_Flesh", rough=0.6, coat=0.25, sheen=0.4, vcol=True))

    # --- Rig
    bones = [("root", (0, 0, -0.45), (0, 0, 0.1), None), ("body", (0, 0, 0.1), (0, 0, 0.55), "root")]
    for i, ctrl in enumerate(hyph_paths):
        bones += lib.chain_bones(f"hypha{i}_", ctrl[::1][:6], "root")
    for i, (base, tip, head) in enumerate(spor):
        bones.append((f"stalk{i}", tuple(base), tuple(tip), "body"))
        bones.append((f"head{i}", tuple(tip), tuple(head + (head - tip) * 0.9), f"stalk{i}", True))
    rig = lib.armature("Fungus_Rig", col, bones)

    def rules(part):
        if part in (0,):
            return ["body"]
        if part == 1:
            return ["root", "body"]
        if 20 <= part < 50:
            return [f"hypha{part - 20}_{k:02d}" for k in range(5)]
        if part >= 50:
            return [f"stalk{part - 50}", f"head{part - 50}"]
        return None
    lib.auto_weights(obj, rig, rules, "root", power=3)

    # --- Shape Keys
    lib.shape_key(obj, "Grow", lambda co, n, p, t: 0.05 if p == 0 else 0.0)
    lib.shape_key(obj, "Wrinkle", lambda co, n, p, t: 0.06 * (gyri(co * 1.4) - 0.6) if p == 0 else 0.0)
    lib.shape_key(obj, "Evolve", lambda co, n, p, t: 0.1 * max(0.0, noise.noise(co * 3.0)) if p == 0 else 0.0)

    # --- Sporen: separate Objekte, die sich loesen koennen
    spore_mat = lib.material("Fungus_Spore", (0.95, 0.85, 0.55), rough=0.4, emit=(1.0, 0.7, 0.3), emit_strength=0.8)
    sb = Builder()
    sb.icosphere((0, 0, 0), 0.026, 1)
    spore_mesh_obj = sb.to_object("Fungus_SporeMesh", col)
    lib.assign(spore_mesh_obj, spore_mat)
    spore_mesh = spore_mesh_obj.data
    bpy_objs = __import__("bpy").data.objects
    bpy_objs.remove(spore_mesh_obj)
    spores = []
    for i, (base, tip, head) in enumerate(spor):
        for k in range(4):
            d = lib.fib_sphere(4, 0.3, rnd)[k]
            pos = head + d * 0.09
            o = bpy_objs.new(f"Fungus_Spore_{i}_{k}", spore_mesh)
            col.objects.link(o)
            o.location = pos
            lib.parent_to(o, rig)
            spores.append((o, pos, (pos - body_shape(Vector((0, 0, 1)))).normalized(), rnd.uniform(0, 1)))

    # --- Evolutionsobjekte
    eb = Builder()
    for d in [d for d in lib.fib_sphere(50, 0.1, lib.rng(4)) if -0.1 < d.z < 0.5][:12]:
        base = body_shape(d)
        tip = base + (d + Vector((0, 0, 0.3))).normalized() * 0.16
        eb.tube([base, (base + tip) / 2, tip], [0.016, 0.013, 0.01], sides=5, part=0, cap_start=False)
        eb.icosphere(tip, 0.04, 1, part=0)
    e1 = eb.to_object("EVO1_Fungus_Buds", col)
    lib.assign(e1, lib.material("Fungus_Bud", (0.7, 0.1, 0.12), rough=0.5, coat=0.3))
    eb = Builder()
    r2 = lib.rng(8)
    for k in range(26):
        a = r2.uniform(0, math.tau)
        rr = r2.uniform(0.5, 1.05)
        p0 = Vector((math.cos(a) * rr, math.sin(a) * rr, -0.5 - r2.uniform(0, 0.25)))
        a2 = a + r2.uniform(-0.8, 0.8)
        p1 = Vector((math.cos(a2) * rr * 0.6, math.sin(a2) * rr * 0.6, -0.45))
        eb.tube(lib.catmull([p0, (p0 + p1) / 2 + Vector((0, 0, -0.05)), p1], 3), [0.006] * 7, sides=4, part=0)
    e2 = eb.to_object("EVO2_Fungus_Mycelium", col)
    lib.assign(e2, lib.material("Fungus_Mycelium", (0.95, 0.9, 0.85), rough=0.7, emit=(0.6, 0.5, 0.45),
                                emit_strength=0.3))
    eb = Builder()
    for d in [d for d in lib.fib_sphere(30, 0.2, lib.rng(12)) if d.z > -0.2][:9]:
        c = body_shape(d) + d * 0.05
        eb.icosphere(c, 0.075, 2, part=0, scale=(1, 1, 1.3), rot=Vector((0, 0, 1)).rotation_difference(d))
    e3 = eb.to_object("EVO3_Fungus_Pods", col)
    lib.assign(e3, lib.material("Fungus_Pod", (0.9, 0.95, 0.3), rough=0.25, emit=(0.8, 1.0, 0.2), emit_strength=2.0))
    for o in (e1, e2, e3):
        lib.auto_weights(o, rig, lambda p: ["body"], "body")

    stalks = [f"stalk{i}" for i in range(len(spor))]
    heads = [f"head{i}" for i in range(len(spor))]
    hyph = [[f"hypha{i}_{k:02d}" for k in range(5)] for i in range(N_HYPHAE)]

    L = 180
    with Clip("fungus_idle", L) as c:
        for f in frames(L, 6):
            c.shape(obj, "Grow", f, 0.4 + 0.4 * wave(f, L, 0, 2))
            c.bone(rig, "body", f, rot=(0.03 * wave(f, L, 0, 1), 0.03 * wave(f, L, 1, 1), 0))
            for i, s in enumerate(stalks):
                c.bone(rig, s, f, rot=(0.12 * wave(f, L, i, 2), 0, 0.12 * wave(f, L, i * 1.7, 1)))
                c.bone(rig, heads[i], f, scl=1 + 0.06 * wave(f, L, i * 2, 3))
            for i, ch in enumerate(hyph):
                for k, bn in enumerate(ch):
                    c.bone(rig, bn, f, rot=(0.03 * wave(f, L, i + k * 0.6, 2), 0, 0.04 * wave(f, L, i - k * 0.6, 1)))
    L = 150
    with Clip("fungus_growth", L) as c:
        for f in frames(L, 3):
            u = (f - 1) / L
            for i, ch in enumerate(hyph):
                for k, bn in enumerate(ch):
                    g = smoothstep(k * 0.12, k * 0.12 + 0.3, u)
                    c.bone(rig, bn, f, scl=(0.4 + 0.6 * g, 0.05 + 0.95 * g, 0.4 + 0.6 * g))
            for i, s in enumerate(stalks):
                g = smoothstep(0.35 + i * 0.05, 0.7 + i * 0.04, u)
                c.bone(rig, s, f, scl=(0.3 + 0.7 * g, 0.02 + 0.98 * g, 0.3 + 0.7 * g))
            c.bone(rig, "body", f, scl=0.55 + 0.45 * smoothstep(0.0, 0.5, u))
            c.shape(obj, "Grow", f, math.sin(math.pi * u))
            for o, pos, d, ph in spores:
                g = smoothstep(0.75, 1.0, u)
                c.obj(o, f, scl=max(g, 0.001))
    L = 120
    with Clip("fungus_spore_release", L) as c:
        for f in frames(L, 2):
            u = (f - 1) / L
            burst = smoothstep(0.15, 0.3, u)
            squeeze = math.exp(-((u - 0.2) / 0.07) ** 2)
            for i, h in enumerate(heads):
                c.bone(rig, h, f, scl=1 - 0.2 * squeeze + 0.25 * burst * (1 - u))
            c.shape(obj, "Grow", f, 0.8 * squeeze)
            for o, pos, d, ph in spores:
                start = 0.2 + ph * 0.1
                k = max(0.0, (u - start) / (0.95 - start))
                if u < start:
                    c.obj(o, f, loc=pos, scl=1)
                else:
                    swirl = Vector((math.cos(k * 6 + ph * 6), math.sin(k * 6 + ph * 6), 0)) * 0.15 * k
                    c.obj(o, f, loc=pos + d * (1.6 * k) + Vector((0, 0, 0.9 * k)) + swirl,
                          scl=max(0.001, (1 - k) * (1 + 0.5 * math.sin(k * 3))))
            # am Ende wachsen neue Sporen nach
            if f == L + 1:
                for o, pos, d, ph in spores:
                    c.obj(o, f, loc=pos, scl=1)
    L = 120
    with Clip("fungus_mutation", L) as c:
        for f in frames(L, 3):
            env = math.sin(math.pi * (f - 1) / L)
            c.shape(obj, "Wrinkle", f, env)
            c.shape(obj, "Evolve", f, env * 0.7)
            for i, s in enumerate(stalks):
                c.bone(rig, s, f, rot=(0.4 * env * wave(f, L, i, 5), 0, 0.3 * env * wave(f, L, i * 2, 4)))
            c.bone(rig, "body", f, scl=(1 + 0.08 * env * wave(f, L, 0, 6), 1, 1 - 0.05 * env * wave(f, L, 0, 6)))
    return {"main": obj}
