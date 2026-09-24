"""Simian Flu: geodaetisches Kapsid-Gitter (Streben + Knoten mit Glykoproteinen),
darin eine DNA-Doppelhelix mit Basenpaaren – deutlich 'genetische' Formensprache."""
import math

import bmesh
from mathutils import Matrix, Vector

import lib
from lib import Builder, Clip, frames, smoothstep, wave

ANIMATIONS = ["simian_idle", "simian_mutation", "simian_evolution"]
IDLE = "simian_idle"

CAGE_R = 0.64
HELIX_R = 0.17
H0, H1 = -0.46, 0.46
TURNS = 2.6
BASES = [(1.0, 0.3, 0.2), (1.0, 0.85, 0.3), (0.3, 0.8, 1.0), (0.55, 1.0, 0.45)]


def helix_pt(z, strand):
    u = (z - H0) / (H1 - H0)
    a = u * TURNS * math.tau + strand * math.pi * 0.82
    return Vector((math.cos(a) * HELIX_R, math.sin(a) * HELIX_R, z))


def build(col):
    rnd = lib.rng(91)
    # --- Gitterkaefig
    tmp = bmesh.new()
    bmesh.ops.create_icosphere(tmp, subdivisions=3, radius=CAGE_R)
    for v in tmp.verts:
        v.co *= 1 + 0.03 * math.sin(v.co.x * 9) * math.cos(v.co.y * 7)
    b = Builder()
    for e in tmp.edges:
        p0, p1 = e.verts[0].co.copy(), e.verts[1].co.copy()
        b.tube([p0, p1], [0.014, 0.014], sides=5, part=0, cap_start=False, cap_end=False)
    knobs = []
    for i, v in enumerate(tmp.verts):
        d = v.co.normalized()
        b.icosphere(v.co, 0.03, 1, part=1)
        if i % 4 == 0:
            tip = v.co + d * 0.11
            b.tube([v.co, v.co + d * 0.06, tip], [0.012, 0.01, 0.008], sides=5, part=2, cap_start=False, cap_end=False)
            b.icosphere(tip + d * 0.02, 0.03, 2, part=2, scale=(1, 1, 0.7), rot=Vector((0, 0, 1)).rotation_difference(d))
            knobs.append(v.co.copy())
    tmp.free()
    cage = b.to_object("Simian_Capsid", col)

    def cage_col(co, n, part, t):
        if part == 0:
            return (0.55, 0.06, 0.08)
        if part == 1:
            return (0.95, 0.4, 0.3)
        return lib.lerp3((0.8, 0.2, 0.2), (1.0, 0.8, 0.6), t)
    lib.vertex_colors(cage, cage_col)
    lib.assign(cage, lib.material("Simian_Capsid", rough=0.3, metal=0.1, coat=0.7, vcol=True))

    # --- DNA-Doppelhelix
    hb = Builder()
    n = 90
    for s in (0, 1):
        pts = [helix_pt(H0 + (H1 - H0) * i / (n - 1), s) for i in range(n)]
        hb.tube(pts, lambda t: 0.024, sides=8, part=10 + s)
    rungs = 22
    for k in range(rungs):
        z = H0 + 0.03 + (H1 - H0 - 0.06) * k / (rungs - 1)
        a, c = helix_pt(z, 0), helix_pt(z, 1)
        m = (a + c) / 2
        hb.cylinder(a, m, 0.012, segs=6, part=20 + (k % 4))
        hb.cylinder(m, c, 0.012, segs=6, part=20 + ((k + 2) % 4))
    helix = hb.to_object("Simian_DNA", col)

    def dna_col(co, n, part, t):
        if part in (10, 11):
            return (0.95, 0.92, 0.9) if part == 10 else (0.9, 0.5, 0.5)
        return BASES[part - 20]
    lib.vertex_colors(helix, dna_col)
    lib.assign(helix, lib.material("Simian_DNA", rough=0.25, coat=0.6, emit=(0.5, 0.15, 0.1), emit_strength=0.6, vcol=True))

    # --- Evolution
    eb = Builder()
    for p in knobs:
        d = p.normalized()
        eb.cone(p + d * 0.12, p + d * 0.3, 0.02, 0.0, segs=5, part=0, cap=False)
    e1 = eb.to_object("EVO1_Simian_Spikes", col)
    lib.assign(e1, lib.material("Simian_Spike", (1.0, 0.55, 0.4), rough=0.3, coat=0.6))
    eb = Builder()
    for k in range(5):
        d = lib.fib_sphere(5, 0.2, lib.rng(k))[k]
        eb.torus(d * 0.38, 0.07, 0.012, segs=18, sides=5, axis=d.orthogonal(), part=0)
    e2 = eb.to_object("EVO2_Simian_Plasmids", col)
    lib.assign(e2, lib.material("Simian_Plasmid", (0.4, 0.9, 1.0), rough=0.25, emit=(0.2, 0.7, 1.0), emit_strength=1.5))
    eb = Builder()
    eb.icosphere((0, 0, 0), 0.1, 3, part=0)
    e3 = eb.to_object("EVO3_Simian_Nucleus", col)
    lib.assign(e3, lib.material("Simian_Nucleus", (1.0, 0.9, 0.4), rough=0.2, emit=(1.0, 0.6, 0.1), emit_strength=3.0))

    # --- Rig
    bones = [("root", (0, 0, 0), (0, 0, 0.1), None), ("cage", (0, 0, -CAGE_R), (0, 0, CAGE_R), "root")]
    hz = [H0 + (H1 - H0) * k / 5 for k in range(6)]
    bones += lib.chain_bones("dna_", [Vector((0, 0, z)) for z in hz], "root")
    rig = lib.armature("Simian_Rig", col, bones)
    dna = [f"dna_{k:02d}" for k in range(5)]
    lib.auto_weights(cage, rig, lambda p: ["cage"], "cage")
    lib.auto_weights(helix, rig, lambda p: dna, "root", power=2)
    lib.auto_weights(e1, rig, lambda p: ["cage"], "cage")
    lib.auto_weights(e2, rig, lambda p: None, "root")
    lib.auto_weights(e3, rig, lambda p: None, "root")

    def unwind(co, n, p, t):
        ang = -co.z * 2.2
        r = Matrix.Rotation(ang, 3, 'Z') @ co
        radial = Vector((r.x, r.y, 0))
        return (r - co) + radial.normalized() * 0.06 if radial.length > 1e-5 else Vector()
    lib.shape_key(helix, "Unwind", unwind)
    lib.shape_key(helix, "Evolve", lambda co, n, p, t: Vector((0, 0, co.z * 0.15)))
    lib.shape_key(cage, "Breathe", lambda co, n, p, t: co.normalized() * 0.05)
    lib.shape_key(cage, "Evolve", lambda co, n, p, t: co.normalized() * 0.04 * math.sin(co.x * 12) * math.cos(co.z * 10))

    L = 180
    with Clip("simian_idle", L) as c:
        for f in frames(L, 4):
            u = (f - 1) / L
            c.bone(rig, "cage", f, rot=(0, math.tau * u * 0.5 * 0 + 0.3 * wave(f, L, 0, 1), 0))
            for k, bn in enumerate(dna):
                c.bone(rig, bn, f, rot=(0, 0.25 * wave(f, L, k * 0.6, 1), 0))
            c.shape(cage, "Breathe", f, 0.5 + 0.5 * wave(f, L, 0, 2))
    L = 120
    with Clip("simian_mutation", L) as c:
        for f in frames(L, 2):
            env = math.sin(math.pi * (f - 1) / L)
            c.shape(helix, "Unwind", f, env)
            for k, bn in enumerate(dna):
                c.bone(rig, bn, f, rot=(0.15 * env * wave(f, L, k, 5), 0.5 * env * wave(f, L, k * 1.3, 3), 0))
            c.shape(cage, "Breathe", f, env * (0.5 + 0.5 * wave(f, L, 0, 8)))
    L = 150
    with Clip("simian_evolution", L) as c:
        for f in frames(L, 3):
            u = (f - 1) / L
            env = smoothstep(0.0, 0.4, u) * (1 - smoothstep(0.7, 1.0, u))
            c.bone(rig, "cage", f, scl=1 + 0.14 * env, rot=(0, u * math.pi, 0))
            c.shape(helix, "Evolve", f, env)
            c.shape(cage, "Evolve", f, env)
            for k, bn in enumerate(dna):
                c.bone(rig, bn, f, rot=(0, math.pi * 0.5 * env * (k / 4), 0))
    return {"main": cage}
