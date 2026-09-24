"""Rendert 3D-Fahrzeuge (Verkehrsflugzeug + Frachtschiff) als Top-Down-PNG mit
weichem Schatten für die Weltkarte. Aufruf über die bvenv-Python (bpy)."""
import os
import sys
import math

import bpy
import bmesh
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "vehicles")
os.makedirs(OUT, exist_ok=True)


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.device = 'CPU'
    sc.cycles.samples = 64
    sc.render.resolution_x = 256
    sc.render.resolution_y = 256
    sc.render.film_transparent = True
    return sc


def mat(name, color, rough=0.4, metal=0.0, emit=None, es=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes.get("Principled BSDF")
    b.inputs['Base Color'].default_value = (*color, 1)
    b.inputs['Roughness'].default_value = rough
    b.inputs['Metallic'].default_value = metal
    if emit:
        b.inputs['Emission Color'].default_value = (*emit, 1)
        b.inputs['Emission Strength'].default_value = es
    return m


def add(obj):
    bpy.context.scene.collection.objects.link(obj)
    return obj


def cube(name, loc, scale, material, rot=(0, 0, 0)):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=2)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    o.location = loc
    o.scale = scale
    o.rotation_euler = rot
    o.data.materials.append(material)
    for p in o.data.polygons:
        p.use_smooth = False
    return add(o)


def cyl(name, loc, r, depth, material, rot=(0, 0, 0), verts=24):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=verts, radius1=r, radius2=r, depth=depth)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    o.location = loc
    o.rotation_euler = rot
    o.data.materials.append(material)
    for p in o.data.polygons:
        p.use_smooth = True
    return add(o)


def setup_render(scene, extent):
    # Orthographische Kamera direkt von oben, Nase zeigt nach +Y (oben im Bild)
    cam_d = bpy.data.cameras.new("Cam")
    cam_d.type = 'ORTHO'
    cam_d.ortho_scale = extent
    cam = bpy.data.objects.new("Cam", cam_d)
    cam.location = (0, 0, 12)
    cam.rotation_euler = (0, 0, 0)
    add(cam)
    scene.camera = cam
    sun = bpy.data.lights.new("Sun", 'SUN')
    sun.energy = 4.0
    sun.angle = 0.2
    so = bpy.data.objects.new("Sun", sun)
    so.rotation_euler = (math.radians(38), math.radians(12), math.radians(20))
    add(so)
    fill = bpy.data.lights.new("Fill", 'SUN')
    fill.energy = 1.2
    fo = bpy.data.objects.new("Fill", fill)
    fo.rotation_euler = (math.radians(-30), math.radians(-20), 0)
    add(fo)
    # Schattenfänger
    bm = bmesh.new()
    bmesh.ops.create_grid(bm, size=extent * 2, x_segments=1, y_segments=1)
    me = bpy.data.meshes.new("Floor")
    bm.to_mesh(me)
    bm.free()
    floor = bpy.data.objects.new("Floor", me)
    floor.location = (0, 0, -0.6)
    add(floor)
    floor.is_shadow_catcher = True
    world = bpy.data.worlds.new("W")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[1].default_value = 0.35
    scene.world = world


def render(path):
    bpy.context.scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


def build_plane():
    sc = reset()
    body = mat("Body", (0.9, 0.92, 0.96), rough=0.3, metal=0.2)
    accent = mat("Accent", (0.85, 0.15, 0.12), rough=0.35)
    dark = mat("Dark", (0.15, 0.16, 0.2), rough=0.5, metal=0.4)
    glass = mat("Glass", (0.1, 0.5, 0.8), rough=0.1, metal=0.6)
    # Rumpf (entlang Y), Nase +Y
    fus = cyl("Fuselage", (0, 0, 0), 0.42, 4.6, body, rot=(math.radians(90), 0, 0))
    # Nase zuspitzen
    for v in fus.data.vertices:
        f = (v.co.y + 2.3) / 4.6
        if v.co.y > 1.4:
            s = max(0.15, 1 - (v.co.y - 1.4) / 1.2)
            v.co.x *= s; v.co.z *= s
        if v.co.y < -1.8:
            s = max(0.4, 1 + (v.co.y + 1.8) / 1.5)
            v.co.x *= s; v.co.z *= s
    fus.data.update()
    cube("Cockpit", (0, 1.5, 0.15), (0.28, 0.35, 0.18), glass)
    # Hauptflügel (gepfeilt)
    for s in (-1, 1):
        w = cube(f"Wing{s}", (s * 1.5, -0.2, -0.05), (1.5, 0.5, 0.06), body, rot=(0, 0, math.radians(-s * 18)))
        eng = cyl(f"Eng{s}", (s * 1.7, -0.55, -0.28), 0.16, 0.5, dark, rot=(math.radians(90), 0, 0))
    # Leitwerk
    for s in (-1, 1):
        cube(f"Tail{s}", (s * 0.7, -2.0, 0), (0.7, 0.35, 0.05), body, rot=(0, 0, math.radians(-s * 22)))
    # Seitenleitwerk
    cube("Fin", (0, -2.05, 0.35), (0.05, 0.4, 0.4), accent, rot=(math.radians(20), 0, 0))
    # Rumpfstreifen
    cube("Stripe", (0, -0.2, 0.36), (0.42, 2.0, 0.02), accent)
    setup_render(sc, 6.2)
    render(os.path.join(OUT, "plane.png"))


def build_ship():
    sc = reset()
    hull = mat("Hull", (0.32, 0.36, 0.42), rough=0.5, metal=0.3)
    red = mat("HullRed", (0.7, 0.12, 0.1), rough=0.5)
    deck = mat("Deck", (0.5, 0.5, 0.55), rough=0.6)
    white = mat("White", (0.9, 0.9, 0.92), rough=0.4)
    cont = [mat("C0", (0.8, 0.5, 0.15)), mat("C1", (0.15, 0.4, 0.7)), mat("C2", (0.2, 0.6, 0.3)), mat("C3", (0.7, 0.2, 0.2))]
    # Rumpf (Bug +Y), zuspitzen
    h = cube("Hull", (0, 0, -0.1), (0.95, 2.6, 0.5), hull)
    for v in h.data.vertices:
        if v.co.y > 1.4:
            s = max(0.15, 1 - (v.co.y - 1.4) / 1.2)
            v.co.x *= s
    h.data.update()
    cube("Waterline", (0, 0, -0.35), (0.96, 2.4, 0.18), red)
    cube("Deck", (0, -0.1, 0.18), (0.9, 2.3, 0.06), deck)
    # Container
    r = __import__("random"); r.seed(3)
    for iy, y in enumerate([1.2, 0.7, 0.2, -0.3]):
        for ix, x in enumerate([-0.45, 0, 0.45]):
            c = cont[(ix + iy) % 4]
            cube(f"Cont{ix}{iy}", (x, y, 0.32), (0.2, 0.22, 0.12), c)
    # Aufbau achtern
    cube("Bridge", (0, -1.5, 0.4), (0.55, 0.4, 0.32), white)
    cube("BridgeTop", (0, -1.5, 0.72), (0.4, 0.28, 0.06), deck)
    setup_render(sc, 6.4)
    render(os.path.join(OUT, "ship.png"))


build_plane()
build_ship()
print("Fahrzeuge gerendert nach", OUT)
