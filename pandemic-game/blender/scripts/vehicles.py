"""Rendert hochauflösende Top-Down-Assets (Verkehrsflugzeug, Frachtschiff,
Asteroid) mit weichem Schatten für die Weltkarte. bpy (Blender 5.x)."""
import os
import math

import bpy
import bmesh
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "vehicles")
os.makedirs(OUT, exist_ok=True)
RES = 512


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.device = 'CPU'
    sc.cycles.samples = 128
    sc.cycles.use_denoising = True
    sc.render.resolution_x = RES
    sc.render.resolution_y = RES
    sc.render.film_transparent = True
    try: sc.view_settings.view_transform = 'Standard'
    except Exception: pass
    return sc


def mat(name, color, rough=0.4, metal=0.0, emit=None, es=0.0, clear=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes.get("Principled BSDF")
    b.inputs['Base Color'].default_value = (*color, 1)
    b.inputs['Roughness'].default_value = rough
    b.inputs['Metallic'].default_value = metal
    if clear:
        b.inputs['Coat Weight'].default_value = clear
    if emit:
        b.inputs['Emission Color'].default_value = (*emit, 1)
        b.inputs['Emission Strength'].default_value = es
    return m


def add(o):
    bpy.context.scene.collection.objects.link(o)
    return o


def cube(name, loc, scale, material, rot=(0, 0, 0), bevel=0.0, smooth=False):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=2)
    if bevel:
        bmesh.ops.bevel(bm, geom=bm.edges[:] + bm.verts[:], offset=bevel, segments=2, affect='EDGES')
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    o.location = loc
    o.scale = scale
    o.rotation_euler = rot
    o.data.materials.append(material)
    for p in o.data.polygons:
        p.use_smooth = smooth
    return add(o)


def cyl(name, loc, r, depth, material, rot=(0, 0, 0), verts=32):
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


def setup(scene, extent, sun_e=4.0):
    cam_d = bpy.data.cameras.new("Cam")
    cam_d.type = 'ORTHO'
    cam_d.ortho_scale = extent
    cam = bpy.data.objects.new("Cam", cam_d)
    cam.location = (0, 0, 14)
    add(cam)
    scene.camera = cam
    sun = bpy.data.lights.new("Sun", 'SUN')
    sun.energy = sun_e
    sun.angle = 0.15
    so = bpy.data.objects.new("Sun", sun)
    so.rotation_euler = (math.radians(40), math.radians(14), math.radians(25))
    add(so)
    fill = bpy.data.lights.new("Fill", 'SUN')
    fill.energy = 1.4
    fo = bpy.data.objects.new("Fill", fill)
    fo.rotation_euler = (math.radians(-32), math.radians(-22), 0)
    add(fo)
    bm = bmesh.new()
    bmesh.ops.create_grid(bm, size=extent * 2, x_segments=1, y_segments=1)
    me = bpy.data.meshes.new("Floor")
    bm.to_mesh(me)
    bm.free()
    floor = bpy.data.objects.new("Floor", me)
    floor.location = (0, 0, -0.9)
    add(floor)
    floor.is_shadow_catcher = True
    w = bpy.data.worlds.new("W")
    w.use_nodes = True
    w.node_tree.nodes["Background"].inputs[1].default_value = 0.4
    scene.world = w


def render(path):
    bpy.context.scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


def subsurf(o, lvl=1):
    m = o.modifiers.new("s", 'SUBSURF')
    m.levels = lvl
    dg = bpy.context.evaluated_depsgraph_get()
    ev = o.evaluated_get(dg)
    nm = bpy.data.meshes.new_from_object(ev)
    o.modifiers.clear()
    o.data = nm


def build_plane():
    sc = reset()
    body = mat("Body", (0.93, 0.95, 0.98), rough=0.25, metal=0.15, clear=0.3)
    accent = mat("Accent", (0.86, 0.14, 0.12), rough=0.3)
    dark = mat("Dark", (0.1, 0.11, 0.14), rough=0.4, metal=0.6)
    glass = mat("Glass", (0.08, 0.35, 0.6), rough=0.08, metal=0.7)
    engine = mat("Eng", (0.2, 0.2, 0.23), rough=0.35, metal=0.7)
    # Rumpf: gerundeter Zylinder, Nase +Y
    fus = cyl("Fuselage", (0, -0.1, 0), 0.44, 5.0, body, rot=(math.radians(90), 0, 0), verts=40)
    for v in fus.data.vertices:
        y = v.co.y
        if y > 1.6:
            s = max(0.06, 1 - ((y - 1.6) / 1.1) ** 1.5)
            v.co.x *= s; v.co.z *= s
        elif y < -2.0:
            s = max(0.5, 1 + (y + 2.0) / 1.4)
            v.co.x *= s; v.co.z *= s
    fus.data.update()
    subsurf(fus, 1)
    # Cockpitfenster
    cube("Cockpit", (0, 1.7, 0.16), (0.26, 0.4, 0.16), glass, bevel=0.05, smooth=True)
    # Fensterreihe
    for i in range(8):
        cube(f"Win{i}", (0.42, 1.2 - i * 0.42, 0.05), (0.02, 0.06, 0.06), glass)
        cube(f"WinL{i}", (-0.42, 1.2 - i * 0.42, 0.05), (0.02, 0.06, 0.06), glass)
    # Hauptflügel gepfeilt (Keil)
    for s in (-1, 1):
        bm = bmesh.new()
        bmesh.ops.create_cube(bm, size=2)
        me = bpy.data.meshes.new(f"Wing{s}")
        bm.to_mesh(me); bm.free()
        w = bpy.data.objects.new(f"Wing{s}", me)
        w.scale = (1.9, 0.62, 0.05); w.location = (s * 1.9, -0.35, -0.06)
        w.rotation_euler = (0, 0, math.radians(-s * 20))
        w.data.materials.append(body)
        add(w)
        # Flügelspitze verjüngen
        for v in w.data.vertices:
            if v.co.x * s > 0.3:
                v.co.y *= 0.5
        w.data.update()
        cyl(f"Engn{s}", (s * 2.0, -0.5, -0.3), 0.17, 0.62, engine, rot=(math.radians(90), 0, 0), verts=24)
        cyl(f"EngInlet{s}", (s * 2.0, -0.16, -0.3), 0.18, 0.06, dark, rot=(math.radians(90), 0, 0), verts=24)
    # Höhenleitwerk
    for s in (-1, 1):
        w = cube(f"Tail{s}", (s * 0.6, -2.25, 0.02), (0.72, 0.32, 0.045), body, rot=(0, 0, math.radians(-s * 26)))
        for v in w.data.vertices:
            if v.co.x * s > 0.3:
                v.co.y *= 0.5
        w.data.update()
    # Seitenleitwerk (aufrecht)
    cube("Fin", (0, -2.25, 0.42), (0.05, 0.42, 0.5), accent, rot=(math.radians(18), 0, 0), bevel=0.03, smooth=True)
    # Rumpfstreifen
    cube("Stripe", (0.4, -0.3, 0.0), (0.015, 2.1, 0.09), accent, rot=(0, 0, 0))
    cube("StripeL", (-0.4, -0.3, 0.0), (0.015, 2.1, 0.09), accent)
    setup(sc, 6.4)
    render(os.path.join(OUT, "plane.png"))


def build_ship():
    sc = reset()
    hull = mat("Hull", (0.16, 0.18, 0.22), rough=0.45, metal=0.4)
    red = mat("Red", (0.62, 0.1, 0.09), rough=0.5)
    deck = mat("Deck", (0.42, 0.28, 0.2), rough=0.7)
    white = mat("White", (0.92, 0.92, 0.94), rough=0.35)
    funnel = mat("Funnel", (0.85, 0.55, 0.1), rough=0.4)
    dark = mat("D", (0.12, 0.12, 0.14), rough=0.5, metal=0.5)
    cont = [mat("C0", (0.82, 0.5, 0.12), rough=0.55), mat("C1", (0.14, 0.42, 0.7), rough=0.55),
            mat("C2", (0.2, 0.6, 0.32), rough=0.55), mat("C3", (0.72, 0.2, 0.2), rough=0.55),
            mat("C4", (0.75, 0.72, 0.2), rough=0.55), mat("C5", (0.5, 0.5, 0.55), rough=0.55)]
    # Rumpf über bmesh: spitzer Bug (+Y), runder Kiel, Sheer
    bm = bmesh.new()
    res = bmesh.ops.create_cube(bm, size=2)
    for v in bm.verts:
        v.co.x *= 0.62; v.co.y *= 2.7; v.co.z *= 0.42
    for v in bm.verts:
        if v.co.y > 2.0:                       # Bug zuspitzen
            s = max(0.08, 1 - (v.co.y - 2.0) / 3.2)
            v.co.x *= s
            v.co.y += 0.5
        if v.co.z < 0:                          # Kiel schmaler
            v.co.x *= 0.7
    bmesh.ops.bevel(bm, geom=bm.edges[:], offset=0.05, segments=2, affect='EDGES')
    me = bpy.data.meshes.new("Hull")
    bm.to_mesh(me); bm.free()
    ho = bpy.data.objects.new("Hull", me)
    ho.data.materials.append(hull)
    for p in ho.data.polygons:
        p.use_smooth = False
    add(ho)
    cube("Boot", (0, 0.1, -0.28), (0.6, 2.55, 0.16), red)      # rote Wasserlinie
    cube("Deck", (0, 0.1, 0.16), (0.56, 2.5, 0.05), deck)
    # Containerstapel ordentlich
    r = __import__("random"); r.seed(7)
    for iy, y in enumerate([1.5, 1.05, 0.6, 0.15, -0.3]):
        wmax = 0.5 if y < 1.3 else 0.34
        cols = [-0.34, 0, 0.34] if wmax > 0.4 else [-0.18, 0.18]
        for ix, xx in enumerate(cols):
            hstack = r.choice([0.11, 0.11, 0.22])
            c = cont[(ix + iy + r.randint(0, 5)) % 6]
            cube(f"Ct{ix}{iy}", (xx, y, 0.28 + hstack), (0.16, 0.19, 0.11 + hstack), c, bevel=0.01)
    # Aufbau + Brücke achtern
    cube("Super", (0, -1.7, 0.42), (0.5, 0.42, 0.34), white, bevel=0.03, smooth=True)
    cube("Bridge", (0, -1.55, 0.72), (0.42, 0.26, 0.08), dark)
    # Schornstein
    cyl("Funnel", (0, -1.95, 0.72), 0.14, 0.5, funnel, verts=20)
    cube("FunnelTop", (0, -1.95, 0.98), (0.16, 0.16, 0.03), dark)
    # Bugmast
    cyl("Mast", (0, 1.6, 0.35), 0.025, 0.5, white, verts=8)
    setup(sc, 6.6)
    render(os.path.join(OUT, "ship.png"))


def build_asteroid():
    sc = reset()
    rock = mat("Rock", (0.14, 0.12, 0.13), rough=0.95, metal=0.08)
    crater = mat("Crater", (0.07, 0.06, 0.07), rough=1.0)
    hot = mat("Hot", (1.0, 0.32, 0.06), rough=0.55, emit=(1.0, 0.32, 0.05), es=2.2)
    glow = mat("Glow", (1.0, 0.25, 0.04), rough=0.5, emit=(1.0, 0.3, 0.05), es=1.8)
    from mathutils import noise
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=4, radius=1.35)
    for v in bm.verts:
        d = v.co.normalized()
        v.co += d * (0.30 * noise.noise(v.co * 1.2) + 0.14 * noise.noise(v.co * 2.7) + 0.05 * noise.noise(v.co * 6.0))
    me = bpy.data.meshes.new("Ast")
    bm.to_mesh(me); bm.free()
    o = bpy.data.objects.new("Ast", me)
    o.data.materials.append(rock)
    o.data.materials.append(hot)
    o.data.materials.append(crater)
    for p in o.data.polygons:
        p.use_smooth = True
    add(o)
    # sichtbare Krater: einige nach oben zeigende Flächen eindrücken + dunkel
    rng = __import__("random"); rng.seed(9)
    bm2 = bmesh.new(); bm2.from_mesh(o.data)
    bm2.faces.ensure_lookup_table()
    up = [f for f in bm2.faces if f.normal.z > 0.35]
    rng.shuffle(up)
    for f in up[:14]:
        r = bmesh.ops.poke(bm2, faces=[f], offset=-0.18)
        for nf in r['faces']:
            nf.material_index = 2
    bm2.to_mesh(o.data); bm2.free()
    o.data.update()
    # glühende, angeschmolzene Vorderkante (nach unten/vorn zeigend)
    for p in o.data.polygons:
        if p.material_index == 0 and (p.normal.z < -0.15 or p.normal.y < -0.4):
            p.material_index = 1
    subsurf(o, 1)
    # Glut-Halo darunter
    cyl("Halo", (0, 0.2, -1.0), 1.35, 0.04, glow, verts=40)
    setup(sc, 3.6, sun_e=2.6)
    render(os.path.join(OUT, "asteroid.png"))


build_plane()
build_ship()
build_asteroid()
print("Fahrzeuge + Asteroid gerendert nach", OUT)
