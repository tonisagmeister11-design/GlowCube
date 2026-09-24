"""Hilfsfunktionen fuer die Blender-Pipeline (Blender 5.x, bpy).

Alle Pathogen-Modelle werden prozedural ueber die Blender-Python-API erzeugt:
Meshes (bmesh), Materialien (Principled BSDF), Armatures, Skinning,
Shape Keys und NLA-Animationen, die der glTF-Exporter als benannte
Animationen in die GLB-Datei schreibt.
"""
import math
import random

import bmesh
import bpy
from mathutils import Euler, Matrix, Quaternion, Vector, noise

FPS = 30
TAU = math.pi * 2


# --------------------------------------------------------------------------
# Szene & Collections
# --------------------------------------------------------------------------

def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.fps = FPS
    sc.frame_start = 1
    sc.frame_end = 240
    sc.unit_settings.system = 'METRIC'
    return sc


def collection(name, parent=None):
    col = bpy.data.collections.get(name)
    if col is None:
        col = bpy.data.collections.new(name)
        (parent or bpy.context.scene.collection).children.link(col)
    return col


def link(obj, col):
    for c in list(obj.users_collection):
        c.objects.unlink(obj)
    col.objects.link(obj)
    return obj


# --------------------------------------------------------------------------
# Noise
# --------------------------------------------------------------------------

def fbm(p, octaves=4, lac=2.0, gain=0.5, basis='PERLIN_NEW'):
    amp, freq, total = 1.0, 1.0, 0.0
    for _ in range(octaves):
        total += amp * noise.noise(p * freq, noise_basis=basis)
        amp *= gain
        freq *= lac
    return total


def ridge(p, octaves=3):
    """Gefaltete Kaemme (Gehirn-/Pilzwindungen): 1 - |noise|."""
    amp, freq, total = 1.0, 1.0, 0.0
    for _ in range(octaves):
        total += amp * (1.0 - abs(noise.noise(p * freq, noise_basis='PERLIN_NEW')))
        amp *= 0.5
        freq *= 2.1
    return total


def smoothstep(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def lerp(a, b, t):
    return a + (b - a) * t


def lerp3(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))


# --------------------------------------------------------------------------
# bmesh-Bausteine
# --------------------------------------------------------------------------

class Builder:
    """Sammelt Geometrie in einem bmesh und merkt sich pro Vertex
    eine Teil-ID ("part") und einen Laengsparameter ("t")."""

    def __init__(self):
        self.bm = bmesh.new()
        self.part = self.bm.verts.layers.float.new("part")
        self.tpar = self.bm.verts.layers.float.new("tpar")
        self.mat_layer = None
        self._mat = 0

    def set_material_index(self, idx):
        self._mat = idx

    def _tag(self, verts, part, tvals=None):
        for i, v in enumerate(verts):
            v[self.part] = float(part)
            if tvals is not None:
                v[self.tpar] = tvals[i]

    def _tag_faces(self, faces):
        for f in faces:
            f.material_index = self._mat

    def icosphere(self, center=(0, 0, 0), radius=1.0, subdiv=3, part=0, scale=(1, 1, 1), rot=None):
        m = Matrix.Translation(center)
        if rot is not None:
            m = m @ rot.to_matrix().to_4x4()
        m = m @ Matrix.Diagonal((*scale, 1))
        res = bmesh.ops.create_icosphere(self.bm, subdivisions=subdiv, radius=radius, matrix=m)
        self._tag(res['verts'], part)
        faces = list({f for v in res['verts'] for f in v.link_faces})
        self._tag_faces(faces)
        return res['verts']

    def uvsphere(self, center=(0, 0, 0), radius=1.0, segs=16, rings=10, part=0, scale=(1, 1, 1), rot=None):
        m = Matrix.Translation(center)
        if rot is not None:
            m = m @ rot.to_matrix().to_4x4()
        m = m @ Matrix.Diagonal((*scale, 1))
        res = bmesh.ops.create_uvsphere(self.bm, u_segments=segs, v_segments=rings, radius=radius, matrix=m)
        self._tag(res['verts'], part)
        faces = list({f for v in res['verts'] for f in v.link_faces})
        self._tag_faces(faces)
        return res['verts']

    def cone(self, base, tip, r1, r2, segs=8, part=0, cap=True):
        base, tip = Vector(base), Vector(tip)
        d = tip - base
        q = Vector((0, 0, 1)).rotation_difference(d.normalized())
        m = Matrix.Translation((base + tip) / 2) @ q.to_matrix().to_4x4()
        res = bmesh.ops.create_cone(self.bm, cap_ends=cap, cap_tris=False, segments=segs,
                                    radius1=r1, radius2=r2, depth=d.length, matrix=m)
        self._tag(res['verts'], part, [((v.co - base).dot(d) / d.length_squared) for v in res['verts']])
        faces = list({f for v in res['verts'] for f in v.link_faces})
        self._tag_faces(faces)
        return res['verts']

    def tube(self, pts, radii, sides=10, part=0, cap_start=True, cap_end=True,
             ring_shape=None, twist=0.0, normal_hint=None):
        """Rohr entlang eines Pfades (parallel transport frames).
        radii: Liste oder Funktion(t)->r. ring_shape: Funktion(t, angle)->Faktor."""
        pts = [Vector(p) for p in pts]
        n = len(pts)
        # Laengenparameter
        acc = [0.0]
        for i in range(1, n):
            acc.append(acc[-1] + (pts[i] - pts[i - 1]).length)
        total = acc[-1] or 1.0
        ts = [a / total for a in acc]
        if callable(radii):
            radii = [radii(t) for t in ts]
        tang = []
        for i in range(n):
            if i == 0:
                t = pts[1] - pts[0]
            elif i == n - 1:
                t = pts[-1] - pts[-2]
            else:
                t = pts[i + 1] - pts[i - 1]
            tang.append(t.normalized())
        ref = Vector(normal_hint) if normal_hint else (Vector((0, 0, 1)) if abs(tang[0].z) < 0.9 else Vector((1, 0, 0)))
        N = tang[0].cross(ref).normalized()
        rings = []
        frames = []
        for i in range(n):
            if i > 0:
                ax = tang[i - 1].cross(tang[i])
                if ax.length > 1e-7:
                    ang = tang[i - 1].angle(tang[i])
                    N = (Matrix.Rotation(ang, 3, ax.normalized()) @ N).normalized()
            B = tang[i].cross(N)
            frames.append((N.copy(), B.copy(), tang[i].copy()))
            ring = []
            for s in range(sides):
                a = TAU * s / sides + twist * ts[i]
                f = ring_shape(ts[i], a) if ring_shape else 1.0
                off = (N * math.cos(a) + B * math.sin(a)) * radii[i] * f
                v = self.bm.verts.new(pts[i] + off)
                ring.append(v)
            rings.append(ring)
        faces = []
        for i in range(n - 1):
            for s in range(sides):
                faces.append(self.bm.faces.new((rings[i][s], rings[i][(s + 1) % sides],
                                                rings[i + 1][(s + 1) % sides], rings[i + 1][s])))
        allv = [v for r in rings for v in r]
        tv = [ts[i] for i in range(n) for _ in range(sides)]
        if cap_start:
            c = self.bm.verts.new(pts[0] - tang[0] * radii[0] * 0.5)
            allv.append(c); tv.append(0.0)
            for s in range(sides):
                faces.append(self.bm.faces.new((c, rings[0][(s + 1) % sides], rings[0][s])))
        if cap_end:
            c = self.bm.verts.new(pts[-1] + tang[-1] * radii[-1] * 0.5)
            allv.append(c); tv.append(1.0)
            for s in range(sides):
                faces.append(self.bm.faces.new((rings[-1][s], rings[-1][(s + 1) % sides], c)))
        self._tag(allv, part, tv)
        self._tag_faces(faces)
        return rings, frames

    def prism(self, base, direction, radius, length, sides=6, tip=0.35, part=0, twist=0.0, taper=0.9):
        """Kristall: n-seitiges Prisma mit Spitze."""
        base, d = Vector(base), Vector(direction).normalized()
        q = Vector((0, 0, 1)).rotation_difference(d)
        rot = q.to_matrix() @ Matrix.Rotation(twist, 3, 'Z')
        body = length * (1 - tip)
        verts, faces = [], []
        r0 = []
        r1 = []
        for s in range(sides):
            a = TAU * s / sides
            p = Vector((math.cos(a), math.sin(a), 0))
            r0.append(self.bm.verts.new(base + rot @ (p * radius)))
            r1.append(self.bm.verts.new(base + rot @ (p * radius * taper + Vector((0, 0, body)))))
        apex = self.bm.verts.new(base + rot @ Vector((0, 0, length)))
        bottom = self.bm.verts.new(base + rot @ Vector((0, 0, -radius * 0.3)))
        for s in range(sides):
            s2 = (s + 1) % sides
            faces.append(self.bm.faces.new((r0[s], r0[s2], r1[s2], r1[s])))
            faces.append(self.bm.faces.new((r1[s], r1[s2], apex)))
            faces.append(self.bm.faces.new((r0[s2], r0[s], bottom)))
        verts = r0 + r1 + [apex, bottom]
        tv = [0.0] * sides + [1 - tip] * sides + [1.0, 0.0]
        self._tag(verts, part, tv)
        self._tag_faces(faces)
        return verts

    def torus(self, center, R, r, segs=24, sides=8, axis=(0, 0, 1), part=0, wobble=None):
        """Ring (geschlossen), axis = Normale der Ringebene."""
        c = Vector(center)
        q = Vector((0, 0, 1)).rotation_difference(Vector(axis).normalized())
        rings = []
        for i in range(segs):
            a = TAU * i / segs
            rr = R * (wobble(a) if wobble else 1.0)
            dirv = q @ Vector((math.cos(a), math.sin(a), 0))
            up = q @ Vector((0, 0, 1))
            ring = []
            for s in range(sides):
                b = TAU * s / sides
                ring.append(self.bm.verts.new(c + dirv * (rr + r * math.cos(b)) + up * r * math.sin(b)))
            rings.append(ring)
        faces = []
        for i in range(segs):
            i2 = (i + 1) % segs
            for s in range(sides):
                s2 = (s + 1) % sides
                faces.append(self.bm.faces.new((rings[i][s], rings[i2][s], rings[i2][s2], rings[i][s2])))
        allv = [v for r_ in rings for v in r_]
        self._tag(allv, part, [i / segs for i in range(segs) for _ in range(sides)])
        self._tag_faces(faces)
        return allv

    def cylinder(self, base, top, radius, segs=12, part=0, radius2=None):
        return self.cone(base, top, radius, radius if radius2 is None else radius2, segs=segs, part=part, cap=True)

    def to_object(self, name, col, smooth=True):
        bmesh.ops.recalc_face_normals(self.bm, faces=self.bm.faces)
        me = bpy.data.meshes.new(name)
        self.bm.to_mesh(me)
        self.bm.free()
        obj = bpy.data.objects.new(name, me)
        col.objects.link(obj)
        for p in me.polygons:
            p.use_smooth = smooth
        return obj


def parent_to(obj, rig, bone=None):
    mw = obj.matrix_world.copy()
    obj.parent = rig
    if bone:
        obj.parent_type = 'BONE'
        obj.parent_bone = bone
    bpy.context.view_layer.update()
    obj.matrix_world = mw
    return obj


def apply_subsurf(obj, levels=1):
    mod = obj.modifiers.new("Subsurf", 'SUBSURF')
    mod.levels = levels
    mod.render_levels = levels
    return apply_modifiers(obj)


def apply_modifiers(obj):
    dg = bpy.context.evaluated_depsgraph_get()
    ev = obj.evaluated_get(dg)
    me = bpy.data.meshes.new_from_object(ev, preserve_all_data_layers=True, depsgraph=dg)
    old = obj.data
    obj.modifiers.clear()
    obj.data = me
    me.name = old.name
    bpy.data.meshes.remove(old)
    return obj


def attr_values(obj, name):
    a = obj.data.attributes.get(name)
    if a is None:
        return [0.0] * len(obj.data.vertices)
    vals = [0.0] * len(obj.data.vertices)
    a.data.foreach_get("value", vals)
    return vals


def displace(obj, fn, parts=None):
    """fn(co, normal, part, t) -> Verschiebung entlang der Normalen (oder Vector)."""
    me = obj.data
    part = attr_values(obj, "part")
    tpar = attr_values(obj, "tpar")
    me.update()
    new = []
    for v in me.vertices:
        pi = int(round(part[v.index]))
        if parts is not None and pi not in parts:
            new.append(v.co.copy())
            continue
        d = fn(v.co.copy(), v.normal.copy(), pi, tpar[v.index])
        if isinstance(d, Vector):
            new.append(v.co + d)
        else:
            new.append(v.co + v.normal * d)
    for v, c in zip(me.vertices, new):
        v.co = c
    me.update()


def vertex_colors(obj, fn, name="Col"):
    """fn(co, normal, part, t) -> (r,g,b) linear."""
    me = obj.data
    part = attr_values(obj, "part")
    tpar = attr_values(obj, "tpar")
    attr = me.color_attributes.get(name) or me.color_attributes.new(name, 'FLOAT_COLOR', 'POINT')
    for v in me.vertices:
        c = fn(v.co.copy(), v.normal.copy(), int(round(part[v.index])), tpar[v.index])
        attr.data[v.index].color = (c[0], c[1], c[2], 1.0)
    me.color_attributes.active_color = attr
    return attr


def strip_helper_attributes(obj):
    for n in ("part", "tpar"):
        a = obj.data.attributes.get(n)
        if a:
            obj.data.attributes.remove(a)


def set_face_materials(obj, fn):
    """fn(center, normal, part) -> material index"""
    me = obj.data
    part = attr_values(obj, "part")
    for p in me.polygons:
        pi = int(round(part[p.vertices[0]]))
        p.material_index = fn(p.center.copy(), p.normal.copy(), pi)


def delete_faces(obj, fn):
    """Loecher/Risse: loescht Flaechen fuer die fn(center, normal, part) True liefert."""
    me = obj.data
    part = attr_values(obj, "part")
    bm = bmesh.new()
    bm.from_mesh(me)
    lay = bm.verts.layers.float.get("part")
    kill = []
    for f in bm.faces:
        pi = int(round(f.verts[0][lay])) if lay else 0
        if fn(f.calc_center_median(), f.normal, pi):
            kill.append(f)
    bmesh.ops.delete(bm, geom=kill, context='FACES_ONLY')
    loose = [v for v in bm.verts if not v.link_faces]
    bmesh.ops.delete(bm, geom=loose, context='VERTS')
    bm.to_mesh(me)
    bm.free()
    me.update()


def solidify(obj, thickness, offset=-1.0):
    m = obj.modifiers.new("Solid", 'SOLIDIFY')
    m.thickness = thickness
    m.offset = offset
    m.use_even_offset = True
    return apply_modifiers(obj)


def tri_count(obj):
    return sum(len(p.vertices) - 2 for p in obj.data.polygons)


# --------------------------------------------------------------------------
# Materialien (glTF-kompatibel: Principled BSDF)
# --------------------------------------------------------------------------

def material(name, color=(0.8, 0.1, 0.1), rough=0.5, metal=0.0, emit=None, emit_strength=0.0,
             coat=0.0, coat_rough=0.15, sheen=0.0, transmission=0.0, ior=1.45, alpha=1.0,
             vcol=False, specular=0.5):
    mat = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    bsdf = nt.nodes.new('ShaderNodeBsdfPrincipled')
    nt.links.new(bsdf.outputs['BSDF'], out.inputs['Surface'])
    bsdf.inputs['Base Color'].default_value = (*color, 1)
    bsdf.inputs['Roughness'].default_value = rough
    bsdf.inputs['Metallic'].default_value = metal
    bsdf.inputs['IOR'].default_value = ior
    bsdf.inputs['Specular IOR Level'].default_value = specular
    if coat:
        bsdf.inputs['Coat Weight'].default_value = coat
        bsdf.inputs['Coat Roughness'].default_value = coat_rough
    if sheen:
        bsdf.inputs['Sheen Weight'].default_value = sheen
    if transmission:
        bsdf.inputs['Transmission Weight'].default_value = transmission
    if emit is not None:
        bsdf.inputs['Emission Color'].default_value = (*emit, 1)
        bsdf.inputs['Emission Strength'].default_value = emit_strength
    if alpha < 1.0:
        bsdf.inputs['Alpha'].default_value = alpha
        mat.surface_render_method = 'BLENDED'
    if vcol:
        vc = nt.nodes.new('ShaderNodeVertexColor')
        vc.layer_name = "Col"
        nt.links.new(vc.outputs['Color'], bsdf.inputs['Base Color'])
    mat.diffuse_color = (*color, 1)
    return mat


def assign(obj, *mats):
    obj.data.materials.clear()
    for m in mats:
        obj.data.materials.append(m)


# --------------------------------------------------------------------------
# Rigging
# --------------------------------------------------------------------------

def armature(name, col, bones):
    """bones: Liste (name, head, tail, parent|None, connect=False)."""
    arm = bpy.data.armatures.new(name + "_Data")
    obj = bpy.data.objects.new(name, arm)
    col.objects.link(obj)
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    for b in bones:
        bname, head, tail, parent = b[:4]
        eb = arm.edit_bones.new(bname)
        eb.head = Vector(head)
        eb.tail = Vector(tail)
        if parent:
            eb.parent = arm.edit_bones[parent]
            eb.use_connect = bool(b[4]) if len(b) > 4 else False
    bpy.ops.object.mode_set(mode='OBJECT')
    obj.select_set(False)
    for pb in obj.pose.bones:
        pb.rotation_mode = 'XYZ'
    arm.display_type = 'STICK'
    return obj


def bind(mesh_obj, arm_obj, weights):
    """weights: dict vertex_index -> list[(bone, w)]"""
    mesh_obj.parent = arm_obj
    mod = mesh_obj.modifiers.new("Armature", 'ARMATURE')
    mod.object = arm_obj
    groups = {}
    for b in arm_obj.data.bones:
        groups[b.name] = mesh_obj.vertex_groups.get(b.name) or mesh_obj.vertex_groups.new(name=b.name)
    for vi, lst in weights.items():
        tot = sum(w for _, w in lst) or 1.0
        for bn, w in lst:
            if w / tot > 1e-4:
                groups[bn].add([vi], w / tot, 'REPLACE')


def seg_dist(p, a, b):
    ab = b - a
    t = max(0.0, min(1.0, (p - a).dot(ab) / (ab.length_squared or 1e-9)))
    return (a + ab * t - p).length


def auto_weights(mesh_obj, arm_obj, rules, default_bone, max_inf=2, power=4.0):
    """rules: Funktion(part)-> Liste Bone-Namen oder None (-> default_bone).
    Gewichte nach Abstand zu den Bone-Segmenten (Ruhelage)."""
    part = attr_values(mesh_obj, "part")
    bones = {b.name: (arm_obj.matrix_world @ b.head_local, arm_obj.matrix_world @ b.tail_local)
             for b in arm_obj.data.bones}
    mw = mesh_obj.matrix_world
    weights = {}
    for v in mesh_obj.data.vertices:
        cands = rules(int(round(part[v.index])))
        if not cands:
            weights[v.index] = [(default_bone, 1.0)]
            continue
        if len(cands) == 1:
            weights[v.index] = [(cands[0], 1.0)]
            continue
        p = mw @ v.co
        ds = sorted(((seg_dist(p, *bones[c]), c) for c in cands))[:max_inf]
        ws = [(c, 1.0 / (d + 1e-4) ** power) for d, c in ds]
        weights[v.index] = ws
    bind(mesh_obj, arm_obj, weights)


def chain_bones(prefix, pts, parent=None):
    out = []
    prev = parent
    for i in range(len(pts) - 1):
        n = f"{prefix}{i:02d}"
        out.append((n, tuple(pts[i]), tuple(pts[i + 1]), prev, prev is not None and prev.startswith(prefix)))
        prev = n
    return out


# --------------------------------------------------------------------------
# Shape Keys
# --------------------------------------------------------------------------

def shape_key(obj, name, fn):
    """fn(co, normal, part, t) -> Verschiebung (Skalar entlang Normale oder Vector)."""
    if obj.data.shape_keys is None:
        obj.shape_key_add(name="Basis", from_mix=False)
    part = attr_values(obj, "part")
    tpar = attr_values(obj, "tpar")
    kb = obj.shape_key_add(name=name, from_mix=False)
    me = obj.data
    for v in me.vertices:
        d = fn(v.co.copy(), v.normal.copy(), int(round(part[v.index])), tpar[v.index])
        if isinstance(d, Vector):
            kb.data[v.index].co = v.co + d
        else:
            kb.data[v.index].co = v.co + v.normal * d
    kb.value = 0.0
    return kb


# --------------------------------------------------------------------------
# Animation: Clips -> NLA-Tracks (werden als benannte glTF-Animationen exportiert)
# --------------------------------------------------------------------------

class Clip:
    """Kontext fuer eine benannte Animation.

    with Clip("virus_idle", 120) as c:
        c.bone(rig, "Spike_00", 1, rot=(0,0,0))
        c.shape(mesh, "Pulse", 60, 0.5)
        c.obj(spore, 30, loc=(0,0,1))
    """
    registry = []

    def __init__(self, name, length):
        self.name = name
        self.length = length
        self.ids = {}

    def __enter__(self):
        return self

    def _action(self, idb, suffix):
        key = idb.name_full + "|" + suffix
        if key not in self.ids:
            idb.animation_data_create()
            act = bpy.data.actions.new(f"{self.name}__{idb.name}")
            idb.animation_data.action = act
            self.ids[key] = idb
        return idb

    def bone(self, rig, bone, frame, rot=None, loc=None, scl=None):
        self._action(rig, "rig")
        pb = rig.pose.bones[bone]
        if rot is not None:
            pb.rotation_euler = Euler(rot)
            pb.keyframe_insert("rotation_euler", frame=frame)
        if loc is not None:
            pb.location = Vector(loc)
            pb.keyframe_insert("location", frame=frame)
        if scl is not None:
            pb.scale = Vector(scl) if hasattr(scl, "__len__") else Vector((scl, scl, scl))
            pb.keyframe_insert("scale", frame=frame)

    def obj(self, ob, frame, loc=None, rot=None, scl=None):
        self._action(ob, "obj")
        if loc is not None:
            ob.location = Vector(loc)
            ob.keyframe_insert("location", frame=frame)
        if rot is not None:
            ob.rotation_euler = Euler(rot)
            ob.keyframe_insert("rotation_euler", frame=frame)
        if scl is not None:
            ob.scale = Vector(scl) if hasattr(scl, "__len__") else Vector((scl, scl, scl))
            ob.keyframe_insert("scale", frame=frame)

    def shape(self, ob, key, frame, value):
        k = ob.data.shape_keys
        self._action(k, "key")
        kb = k.key_blocks[key]
        kb.value = value
        kb.keyframe_insert("value", frame=frame)

    def __exit__(self, *exc):
        if exc[0] is not None:
            return False
        for idb in self.ids.values():
            ad = idb.animation_data
            act = ad.action
            tr = ad.nla_tracks.new()
            tr.name = self.name
            strip = tr.strips.new(self.name, 1, act)
            strip.frame_end = 1 + self.length
            strip.action_frame_end = 1 + self.length
            ad.action = None
        Clip.registry.append(self.name)
        return False


def reset_pose(rig, keep_rest=True):
    for pb in rig.pose.bones:
        pb.location = (0, 0, 0)
        pb.rotation_euler = (0, 0, 0)
        pb.scale = (1, 1, 1)


def wave(f, length, phase=0.0, cycles=1):
    """Sinus in [-1,1], exakt loopend ueber length Frames."""
    return math.sin(TAU * cycles * (f - 1) / length + phase)


def frames(length, step):
    fs = list(range(1, length + 2, step))
    if fs[-1] != length + 1:
        fs.append(length + 1)
    return fs


# --------------------------------------------------------------------------
# Export & Speichern
# --------------------------------------------------------------------------

def select_only(objs):
    for o in bpy.context.view_layer.objects:
        o.select_set(False)
    for o in objs:
        o.select_set(True)


def collection_objects(col):
    out = list(col.objects)
    for c in col.children:
        out += collection_objects(c)
    return out


def export_glb(col, path):
    objs = collection_objects(col)
    for o in objs:
        o.hide_set(False)
        o.hide_viewport = False
    select_only(objs)
    bpy.ops.export_scene.gltf(
        filepath=path,
        export_format='GLB',
        use_selection=True,
        export_apply=False,
        export_animations=True,
        export_animation_mode='NLA_TRACKS',
        export_force_sampling=True,
        export_frame_step=2,
        export_optimize_animation_size=True,
        export_morph=True,
        export_morph_normal=True,
        export_skins=True,
        export_all_influences=False,
        export_def_bones=False,
        export_yup=True,
        export_extras=True,
        export_texcoords=False,
        export_normals=True,
        export_tangents=False,
        export_vertex_color='MATERIAL',
        export_cameras=False,
        export_lights=False,
    )
    select_only([])


def rng(seed):
    return random.Random(seed)


def fib_sphere(n, jitter=0.0, r=None):
    """Gleichmaessig verteilte Richtungen (Fibonacci), optional verwackelt."""
    r = r or random.Random(1)
    out = []
    ga = math.pi * (3 - math.sqrt(5))
    for i in range(n):
        y = 1 - (i + 0.5) / n * 2
        rad = math.sqrt(1 - y * y)
        th = ga * i
        v = Vector((math.cos(th) * rad, math.sin(th) * rad, y))
        if jitter:
            v += Vector((r.uniform(-1, 1), r.uniform(-1, 1), r.uniform(-1, 1))) * jitter
        out.append(v.normalized())
    return out


def catmull(ctrl, samples_per_seg=6):
    ctrl = [Vector(c) for c in ctrl]
    pts = []
    n = len(ctrl)
    for i in range(n - 1):
        p0 = ctrl[max(i - 1, 0)]
        p1, p2 = ctrl[i], ctrl[i + 1]
        p3 = ctrl[min(i + 2, n - 1)]
        for s in range(samples_per_seg):
            t = s / samples_per_seg
            t2, t3 = t * t, t * t * t
            pts.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
                              (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    pts.append(ctrl[-1])
    return pts
