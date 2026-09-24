"""Menschliches Koerper-Hologramm fuer den Krankheitsbildschirm (wie das
Roentgen-Figurenpanel): Metaball-Koerper + benannte Organe, die im Spiel je
nach entwickelten Symptomen aufleuchten."""
import math

import bpy
from mathutils import Vector

import lib
from lib import Builder, Clip, frames, wave

ANIMATIONS = ["human_idle"]
IDLE = "human_idle"


def build(col):
    mb = bpy.data.metaballs.new("HumanMeta")
    mb.resolution = 0.04
    mb.render_resolution = 0.04
    mb.threshold = 0.35
    mobj = bpy.data.objects.new("HumanMetaObj", mb)
    col.objects.link(mobj)

    def cap(a, b, r):
        a, b = Vector(a), Vector(b)
        e = mb.elements.new(type='CAPSULE')
        e.co = (a + b) / 2
        d = b - a
        e.radius = r * 1.25
        e.size_x = d.length / 2
        e.rotation = Vector((1, 0, 0)).rotation_difference(d.normalized())
        e.stiffness = 2.0

    def ball(c, r, stiff=2.0):
        e = mb.elements.new(type='BALL')
        e.co = c
        e.radius = r * 1.25
        e.stiffness = stiff

    ball((0, 0, 1.62), 0.12)                                    # Kopf
    cap((0, 0, 1.44), (0, 0, 1.52), 0.06)                       # Hals
    cap((0, 0, 1.0), (0, 0, 1.33), 0.17)                        # Brustkorb
    cap((-0.14, 0, 1.36), (0.14, 0, 1.36), 0.08)                # Schultern
    cap((0, 0, 0.85), (0, 0, 1.0), 0.15)                        # Bauch
    cap((-0.09, 0, 0.85), (0.09, 0, 0.85), 0.12)                # Becken
    for s in (-1, 1):
        cap((s * 0.2, 0, 1.35), (s * 0.33, 0.02, 1.06), 0.055)  # Oberarm
        cap((s * 0.33, 0.02, 1.06), (s * 0.42, -0.02, 0.8), 0.045)  # Unterarm
        ball((s * 0.44, -0.03, 0.74), 0.045)                   # Hand
        cap((s * 0.1, 0, 0.82), (s * 0.12, 0, 0.45), 0.075)    # Oberschenkel
        cap((s * 0.12, 0, 0.45), (s * 0.13, 0.02, 0.08), 0.055)  # Unterschenkel
        cap((s * 0.13, 0.02, 0.05), (s * 0.14, -0.08, 0.03), 0.035)  # Fuss
    dg = bpy.context.evaluated_depsgraph_get()
    me = bpy.data.meshes.new_from_object(mobj.evaluated_get(dg), depsgraph=dg)
    body = bpy.data.objects.new("Human_Body", me)
    col.objects.link(body)
    bpy.data.objects.remove(mobj)
    dec = body.modifiers.new("Decimate", 'DECIMATE')
    dec.ratio = 0.5
    lib.apply_modifiers(body)
    for p in body.data.polygons:
        p.use_smooth = True
    # Zentrieren: Huefte in den Ursprung
    for v in body.data.vertices:
        v.co.z -= 0.9
    lib.assign(body, lib.material("Holo_Body", (0.9, 0.12, 0.1), rough=0.4, emit=(1.0, 0.15, 0.1), emit_strength=0.6, alpha=0.35))

    organs = {}

    def organ(name, color, fn):
        b = Builder()
        fn(b)
        o = b.to_object("Organ_" + name, col)
        for v in o.data.vertices:
            v.co.z -= 0.9
        lib.assign(o, lib.material("Organ_" + name, color, rough=0.4, emit=color, emit_strength=1.0))
        organs[name] = o
        return o

    organ("Brain", (1.0, 0.55, 0.5), lambda b: b.icosphere((0, 0.01, 1.64), 0.085, 3, scale=(0.9, 1.1, 0.85)))
    organ("Lungs", (1.0, 0.35, 0.3), lambda b: [b.icosphere((s * 0.075, 0, 1.2), 0.075, 3, scale=(0.8, 0.7, 1.4)) for s in (-1, 1)])
    organ("Heart", (1.0, 0.1, 0.1), lambda b: b.icosphere((0.03, -0.03, 1.17), 0.045, 3, scale=(1, 0.9, 1.15)))
    organ("Liver", (0.8, 0.2, 0.15), lambda b: b.icosphere((0.05, -0.01, 1.0), 0.07, 3, scale=(1.4, 0.8, 0.6)))
    organ("Stomach", (1.0, 0.5, 0.2), lambda b: b.icosphere((-0.06, -0.02, 0.98), 0.055, 3, scale=(1.2, 0.8, 0.9)))
    organ("Intestines", (1.0, 0.6, 0.3), lambda b: [b.torus((0, -0.02, 0.87 - 0.03 * k), 0.06 - 0.008 * k, 0.018, segs=18, sides=6)
                                                     for k in range(3)])
    organ("Kidneys", (0.9, 0.3, 0.4), lambda b: [b.icosphere((s * 0.07, 0.05, 0.93), 0.028, 2, scale=(0.8, 0.7, 1.3)) for s in (-1, 1)])
    organ("Skin", (1.0, 0.3, 0.25), lambda b: b.torus((0, 0, 1.5), 0.01, 0.002, segs=6, sides=3))

    L = 120
    with Clip("human_idle", L) as c:
        for f in frames(L, 2):
            u = (f - 1) / L
            breath = 1 + 0.06 * wave(f, L, 0, 2)
            c.obj(organs["Lungs"], f, scl=(breath, breath, 1 + 0.03 * wave(f, L, 0, 2)))
            beat = 1 + 0.12 * max(0.0, math.sin(math.tau * 5 * u)) ** 6
            c.obj(organs["Heart"], f, scl=beat)
    return {"main": body}
