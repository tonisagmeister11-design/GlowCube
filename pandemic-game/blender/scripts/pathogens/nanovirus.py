"""Nano-Virus: bio-mechanischer Phage – facettierter Metallkopf mit leuchtenden
Fenstern und Energiekern, rotierender Scan-Kragen, kontrahierbare Schwanzscheide,
Basisplatte und sechs zweigliedrige Beine (je zwei Bones)."""
import math

import bmesh
from mathutils import Vector

import lib
from lib import Builder, Clip, frames, smoothstep, wave

ANIMATIONS = ["nanovirus_idle", "nanovirus_scan", "nanovirus_activate", "nanovirus_repair"]
IDLE = "nanovirus_idle"

HEAD_C = Vector((0, 0, 0.62))
N_LEGS = 6


def build(col):
    rnd = lib.rng(51)
    # --- Kopf: Ikosaeder mit eingesetzten Paneelen (flach schattiert)
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=1, radius=0.38)
    for v in bm.verts:
        v.co.z *= 1.22
        v.co += HEAD_C
    res = bmesh.ops.inset_individual(bm, faces=list(bm.faces), thickness=0.03, depth=-0.012)
    for f in bm.faces:
        # Fenster = die eingesetzten Dreiecke, Rahmen = Vierecke
        f.material_index = 1 if len(f.verts) == 3 else 0
    # Fenster leicht eindruecken
    head = bpy_obj_from_bm(bm, "Nanovirus_Head", col)
    # --- Mechanik (Kragen, Scheide, Basisplatte, Beine) in einem Mesh
    b = Builder()
    b.set_material_index(0)
    b.cylinder((0, 0, 0.12), (0, 0, 0.2), 0.16, segs=12, part=1, radius2=0.2)        # Kragen
    for k in range(6):
        a = k * math.tau / 6
        p = Vector((math.cos(a) * 0.18, math.sin(a) * 0.18, 0.16))
        b.tube([p, p + Vector((math.cos(a) * 0.1, math.sin(a) * 0.1, -0.08)),
                p + Vector((math.cos(a) * 0.14, math.sin(a) * 0.14, -0.2))], [0.012, 0.01, 0.006], sides=5, part=1)
    for k in range(8):                                                                  # Scheidenringe
        z = 0.08 - k * 0.065
        b.cylinder((0, 0, z - 0.045), (0, 0, z), 0.1, segs=12, part=2)
    b.set_material_index(1)
    b.cylinder((0, 0, -0.5), (0, 0, 0.14), 0.05, segs=8, part=2)                        # Energie-Rohr
    b.set_material_index(0)
    b.cylinder((0, 0, -0.58), (0, 0, -0.5), 0.2, segs=6, part=3)                         # Basisplatte
    for k in range(6):
        a = k * math.tau / 6 + 0.5
        p = Vector((math.cos(a) * 0.12, math.sin(a) * 0.12, -0.58))
        b.cone(p, p + Vector((0, 0, -0.1)), 0.02, 0.004, segs=4, part=3, cap=False)
    legs = []
    for i in range(N_LEGS):
        a = i * math.tau / N_LEGS
        o = Vector((math.cos(a), math.sin(a), 0))
        hip = Vector((0, 0, -0.54)) + o * 0.18
        knee = Vector((0, 0, -0.3)) + o * 0.62
        foot = Vector((0, 0, -1.02)) + o * 0.9
        b.set_material_index(0)
        b.cylinder(hip, knee, 0.026, segs=6, part=20 + i * 2)
        b.cylinder(knee, foot, 0.02, segs=6, part=21 + i * 2, radius2=0.01)
        b.set_material_index(2)
        b.icosphere(knee, 0.042, 1, part=20 + i * 2)
        b.icosphere(hip, 0.035, 1, part=20 + i * 2)
        b.cone(foot, foot + (foot - knee).normalized() * 0.07, 0.018, 0.0, segs=4, part=21 + i * 2, cap=False)
        legs.append((hip, knee, foot))
    mech = b.to_object("Nanovirus_Mechanics", col, smooth=False)

    metal = lib.material("Nano_Chrome", (0.55, 0.5, 0.52), rough=0.22, metal=1.0, coat=0.4)
    glow = lib.material("Nano_Energy", (1.0, 0.1, 0.05), rough=0.2, emit=(1.0, 0.08, 0.04), emit_strength=4.0)
    joint = lib.material("Nano_Joint", (0.08, 0.08, 0.09), rough=0.35, metal=0.8, emit=(0.9, 0.1, 0.05), emit_strength=0.3)
    lib.assign(head, metal, glow)
    lib.assign(mech, metal, glow, joint)

    # innerer Kern (sichtbar als pulsierende Kugel unter den Fenstern)
    cb = Builder()
    cb.icosphere(HEAD_C, 0.2, 3, part=5)
    core = cb.to_object("Nanovirus_Core", col)
    lib.assign(core, glow)

    # --- Rig
    bones = [("base", (0, 0, -0.58), (0, 0, -0.5), None),
             ("sheath", (0, 0, -0.5), (0, 0, 0.12), "base", True),
             ("collar", (0, 0, 0.12), (0, 0, 0.22), "sheath", True),
             ("head", (0, 0, 0.22), (0, 0, 1.1), "collar", True)]
    for i, (hip, knee, foot) in enumerate(legs):
        bones.append((f"leg{i}_up", tuple(hip), tuple(knee), "base"))
        bones.append((f"leg{i}_low", tuple(knee), tuple(foot), f"leg{i}_up", True))
    rig = lib.armature("Nanovirus_Rig", col, bones)

    def rules(p):
        if p == 1:
            return ["collar"]
        if p == 2:
            return ["sheath"]
        if p == 3:
            return ["base"]
        if p >= 20:
            i, lo = divmod(p - 20, 2)
            return [f"leg{i}_{'low' if lo else 'up'}"]
        return ["head"]
    lib.auto_weights(mech, rig, rules, "base")
    lib.auto_weights(head, rig, lambda p: ["head"], "head")
    lib.auto_weights(core, rig, lambda p: ["head"], "head")

    lib.shape_key(head, "Panels", lambda co, n, p, t: 0.035)
    lib.shape_key(core, "Charge", lambda co, n, p, t: 0.08)

    # --- Evolution
    eb = Builder()
    top = HEAD_C + Vector((0, 0, 0.46))
    eb.cylinder(top - Vector((0, 0, 0.05)), top + Vector((0, 0, 0.25)), 0.015, segs=6)
    for k in range(3):
        a = k * math.tau / 3
        p = top + Vector((math.cos(a) * 0.08, math.sin(a) * 0.08, -0.04))
        eb.cylinder(p, p + Vector((math.cos(a) * 0.06, math.sin(a) * 0.06, 0.16)), 0.01, segs=5)
        eb.icosphere(p + Vector((math.cos(a) * 0.06, math.sin(a) * 0.06, 0.17)), 0.02, 1)
    eb.icosphere(top + Vector((0, 0, 0.27)), 0.035, 1)
    e1 = eb.to_object("EVO1_Nano_Antenna", col, smooth=False)
    lib.assign(e1, metal)
    eb = Builder()
    for k in range(6):
        a = k * math.tau / 6 + math.pi / 6
        p = Vector((math.cos(a) * 0.19, math.sin(a) * 0.19, 0.2))
        eb.cylinder(p, p + Vector((math.cos(a) * 0.12, math.sin(a) * 0.12, 0.02)), 0.022, segs=6, radius2=0.012)
    e2 = eb.to_object("EVO2_Nano_Emitters", col, smooth=False)
    lib.assign(e2, glow)
    eb = Builder()
    eb.torus((0, 0, -0.2), 0.3, 0.018, segs=36, sides=6)
    eb.torus((0, 0, -0.2), 0.36, 0.008, segs=36, sides=4)
    e3 = eb.to_object("EVO3_Nano_Halo", col)
    lib.assign(e3, glow)
    lib.auto_weights(e1, rig, lambda p: ["head"], "head")
    lib.auto_weights(e2, rig, lambda p: ["collar"], "collar")
    lib.auto_weights(e3, rig, lambda p: ["sheath"], "sheath")

    ups = [f"leg{i}_up" for i in range(N_LEGS)]
    lows = [f"leg{i}_low" for i in range(N_LEGS)]

    L = 150
    with Clip("nanovirus_idle", L) as c:
        for f in frames(L, 5):
            for i in range(N_LEGS):
                c.bone(rig, ups[i], f, rot=(0.05 * wave(f, L, i, 2), 0, 0))
                c.bone(rig, lows[i], f, rot=(0.06 * wave(f, L, i + 1, 2), 0, 0))
            c.bone(rig, "head", f, rot=(0, 0.25 * wave(f, L, 0, 1), 0))
            c.bone(rig, "base", f, loc=(0, 0.02 * wave(f, L, 0, 2), 0))
            c.shape(core, "Charge", f, 0.5 + 0.5 * wave(f, L, 0, 3))
    L = 120
    with Clip("nanovirus_scan", L) as c:
        for f in frames(L, 2):
            u = (f - 1) / L
            c.bone(rig, "head", f, rot=(0, math.tau * u, 0))
            c.bone(rig, "collar", f, rot=(0, -math.tau * 2 * u, 0))
            c.shape(head, "Panels", f, 0.5 + 0.5 * wave(f, L, 0, 6))
            for i in range(N_LEGS):
                lift = max(0.0, math.sin(math.tau * (u * 2 - i / N_LEGS)))
                c.bone(rig, ups[i], f, rot=(-0.35 * lift, 0, 0))
                c.bone(rig, lows[i], f, rot=(0.25 * lift, 0, 0))
    L = 90
    with Clip("nanovirus_activate", L) as c:
        for f in frames(L, 2):
            u = (f - 1) / L
            k = math.sin(math.pi * min(1.0, u * 1.6)) if u < 0.62 else 0.0
            snap = smoothstep(0.1, 0.3, u) * (1 - smoothstep(0.6, 0.9, u))
            c.bone(rig, "sheath", f, scl=(1 + 0.25 * snap, 1 - 0.45 * snap, 1 + 0.25 * snap))
            c.bone(rig, "collar", f, scl=(1 / (1 + 0.25 * snap), 1 / (1 - 0.45 * snap), 1 / (1 + 0.25 * snap)))
            for i in range(N_LEGS):
                c.bone(rig, ups[i], f, rot=(0.45 * snap, 0, 0))
                c.bone(rig, lows[i], f, rot=(-0.6 * snap, 0, 0))
            c.shape(core, "Charge", f, snap + 0.3 * k)
            c.shape(head, "Panels", f, snap)
    L = 120
    with Clip("nanovirus_repair", L) as c:
        for f in frames(L, 3):
            u = (f - 1) / L
            for i in range(N_LEGS):
                if i % 2 == 0:
                    act = math.sin(math.pi * u) ** 2
                    c.bone(rig, ups[i], f, rot=(-0.9 * act, 0.2 * act * wave(f, L, i, 6), 0))
                    c.bone(rig, lows[i], f, rot=(-1.2 * act + 0.15 * act * wave(f, L, i, 10), 0, 0))
                else:
                    c.bone(rig, ups[i], f, rot=(0.1 * math.sin(math.pi * u), 0, 0))
                    c.bone(rig, lows[i], f, rot=(0, 0, 0))
            c.shape(head, "Panels", f, 0.5 + 0.5 * wave(f, L, 0, 10))
            c.bone(rig, "head", f, rot=(0, 0.4 * math.sin(math.tau * u), 0))
    return {"main": head}


def bpy_obj_from_bm(bm, name, col):
    import bpy
    lay = bm.verts.layers.float.new("part")
    for v in bm.verts:
        v[lay] = 0.0
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    col.objects.link(o)
    for p in me.polygons:
        p.use_smooth = False
    return o
