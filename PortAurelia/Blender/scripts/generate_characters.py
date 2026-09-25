"""Procedural rigged humanoid characters with swappable clothing and animations.

Output: Game/assets/generated/characters/human.glb
  Skeleton (humanoid bones) + meshes:
    Body_M, Body_F, Head_M, Head_F, Eyes, Brows,
    Hair_Short, Hair_Buzz, Hair_Long, Hair_Bun, Hair_Curly,
    Top_TShirt_M/F, Top_LongSleeve_M/F, Top_Jacket_M/F, Top_Suit_M/F, Top_Vest_M/F (police/security),
    Bottom_Jeans_M/F, Bottom_Shorts_M/F, Bottom_Skirt_F,
    Shoes_Sneakers, Shoes_Boots, Hat_Cap, Hat_Police, Glasses_Sun
  and ~35 animations (in place, 30 fps).

Character faces Godot -Z (Blender +Y). Bones are named by the character's own side:
.L = character's left (Blender -X).

Usage: python Blender/scripts/generate_characters.py
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bpy  # noqa: E402
import bmesh  # noqa: E402
from mathutils import Matrix, Vector, Euler, Quaternion  # noqa: E402

from _common import GEN, reset_scene, export_glb, get_material, srgb_to_linear  # noqa: E402

OUT = os.path.join(GEN, "characters")
FPS = 30

# ------------------------------------------------------------------ skeleton (Blender space, T-pose)
# name: (head, tail, parent)
BONES = {
    "Hips":       ((0, 0.0, 0.96), (0, 0.0, 1.06), None),
    "Spine":      ((0, 0.0, 1.06), (0, 0.0, 1.20), "Hips"),
    "Chest":      ((0, 0.0, 1.20), (0, 0.0, 1.40), "Spine"),
    "Neck":       ((0, 0.0, 1.46), (0, 0.01, 1.56), "Chest"),
    "Head":       ((0, 0.01, 1.56), (0, 0.01, 1.78), "Neck"),
    "Shoulder.L": ((-0.03, 0.0, 1.42), (-0.17, -0.01, 1.43), "Chest"),
    "UpperArm.L": ((-0.17, -0.01, 1.43), (-0.44, -0.01, 1.43), "Shoulder.L"),
    "LowerArm.L": ((-0.44, -0.01, 1.43), (-0.69, -0.01, 1.43), "UpperArm.L"),
    "Hand.L":     ((-0.69, -0.01, 1.43), (-0.84, -0.01, 1.43), "LowerArm.L"),
    "Shoulder.R": ((0.03, 0.0, 1.42), (0.17, -0.01, 1.43), "Chest"),
    "UpperArm.R": ((0.17, -0.01, 1.43), (0.44, -0.01, 1.43), "Shoulder.R"),
    "LowerArm.R": ((0.44, -0.01, 1.43), (0.69, -0.01, 1.43), "UpperArm.R"),
    "Hand.R":     ((0.69, -0.01, 1.43), (0.84, -0.01, 1.43), "LowerArm.R"),
    "UpperLeg.L": ((-0.095, 0.0, 0.94), (-0.105, 0.01, 0.52), "Hips"),
    "LowerLeg.L": ((-0.105, 0.01, 0.52), (-0.11, -0.02, 0.09), "UpperLeg.L"),
    "Foot.L":     ((-0.11, -0.02, 0.09), (-0.11, 0.11, 0.03), "LowerLeg.L"),
    "Toes.L":     ((-0.11, 0.11, 0.03), (-0.11, 0.18, 0.02), "Foot.L"),
    "UpperLeg.R": ((0.095, 0.0, 0.94), (0.105, 0.01, 0.52), "Hips"),
    "LowerLeg.R": ((0.105, 0.01, 0.52), (0.11, -0.02, 0.09), "UpperLeg.R"),
    "Foot.R":     ((0.11, -0.02, 0.09), (0.11, 0.11, 0.03), "LowerLeg.R"),
    "Toes.R":     ((0.11, 0.11, 0.03), (0.11, 0.18, 0.02), "Foot.R"),
}
DEFORM_ORDER = list(BONES.keys())


# ------------------------------------------------------------------ body via skin modifier
def body_skeleton(kind):
    """Vertices / edges / skin radii (x across, y depth) of the body stick figure."""
    f = kind == "F"
    sh = 0.155 if f else 0.18       # shoulder half width
    hip = 0.115 if f else 0.1
    V = []
    R = []
    E = []

    def v(p, r):
        V.append(p)
        R.append(r)
        return len(V) - 1

    pelvis = v((0, 0.0, 0.97), (0.15 if f else 0.145, 0.1))
    belly = v((0, 0.005, 1.08), (0.125 if f else 0.135, 0.1))
    waist = v((0, 0.005, 1.16), (0.11 if f else 0.13, 0.095))
    chest = v((0, 0.0, 1.28), (0.145 if f else 0.165, 0.11 if f else 0.115))
    upper = v((0, -0.005, 1.39), (0.155 if f else 0.175, 0.1))
    neck0 = v((0, -0.005, 1.47), (0.058, 0.058))
    neck1 = v((0, 0.0, 1.55), (0.05 if f else 0.055, 0.052))
    E += [(pelvis, belly), (belly, waist), (waist, chest), (chest, upper), (upper, neck0), (neck0, neck1)]
    for s in (-1, 1):
        cl = v((s * 0.12, -0.01, 1.42), (0.06, 0.055))
        shd = v((s * sh, -0.01, 1.425), (0.064 if f else 0.074, 0.064 if f else 0.072))
        mid = v((s * (sh + 0.12), -0.01, 1.43), (0.051 if f else 0.06, 0.05 if f else 0.058))
        elb = v((s * 0.44, -0.01, 1.43), (0.041 if f else 0.047, 0.04 if f else 0.045))
        fore = v((s * 0.57, -0.01, 1.43), (0.038 if f else 0.044, 0.033 if f else 0.038))
        wr = v((s * 0.69, -0.01, 1.43), (0.026 if f else 0.03, 0.02 if f else 0.022))
        palm = v((s * 0.76, -0.01, 1.428), (0.038 if f else 0.042, 0.016))
        fing = v((s * 0.84, -0.01, 1.422), (0.034 if f else 0.037, 0.012))
        thumb0 = v((s * 0.735, 0.03, 1.425), (0.013, 0.013))
        thumb1 = v((s * 0.78, 0.055, 1.42), (0.011, 0.011))
        E += [(upper, cl), (cl, shd), (shd, mid), (mid, elb), (elb, fore), (fore, wr), (wr, palm), (palm, fing),
              (palm, thumb0), (thumb0, thumb1)]
        hp = v((s * hip, 0.0, 0.93), (0.085 if f else 0.08, 0.085 if f else 0.08))
        th = v((s * (hip + 0.005), 0.005, 0.74), (0.08 if f else 0.078, 0.078 if f else 0.078))
        kn = v((s * 0.105, 0.01, 0.52), (0.054, 0.056))
        calf = v((s * 0.107, -0.01, 0.34), (0.054 if f else 0.058, 0.056))
        an = v((s * 0.11, -0.02, 0.1), (0.032, 0.034))
        heel = v((s * 0.11, -0.045, 0.045), (0.035, 0.035))
        ball = v((s * 0.11, 0.1, 0.035), (0.042, 0.022))
        toe = v((s * 0.11, 0.17, 0.028), (0.034, 0.018))
        E += [(pelvis, hp), (hp, th), (th, kn), (kn, calf), (calf, an), (an, heel), (heel, ball), (ball, toe)]
    return V, E, R, neck1


def make_skin_mesh(name, V, E, R, root, levels=1):
    me = bpy.data.meshes.new(name + "_skel")
    me.from_pydata(V, E, [])
    ob = bpy.data.objects.new(name + "_skel", me)
    bpy.context.scene.collection.objects.link(ob)
    sk = ob.modifiers.new("skin", "SKIN")
    sk.branch_smoothing = 0.7
    sk.use_smooth_shade = True
    for i, r in enumerate(R):
        me.skin_vertices[0].data[i].radius = r
    me.skin_vertices[0].data[root].use_root = True
    sub = ob.modifiers.new("sub", "SUBSURF")
    sub.levels = levels
    dg = bpy.context.evaluated_depsgraph_get()
    res = bpy.data.meshes.new_from_object(ob.evaluated_get(dg))
    bpy.data.objects.remove(ob, do_unlink=True)
    bpy.data.meshes.remove(me)
    res.name = name
    return res


# ------------------------------------------------------------------ weights
def seg_dist(p, a, b):
    ab = b - a
    t = max(0.0, min(1.0, (p - a).dot(ab) / max(ab.length_squared, 1e-9)))
    return (p - (a + ab * t)).length, t


def auto_weights(me, bones, allowed=None, rigid=None):
    """Distance based skin weights (top 3 bones, sharpened). Returns list of dicts."""
    out = []
    names = list(bones.keys()) if allowed is None else allowed
    segs = {n: (Vector(bones[n][0]), Vector(bones[n][1])) for n in names}
    for v in me.vertices:
        p = v.co
        if rigid:
            out.append({rigid: 1.0})
            continue
        side = -1 if p.x < -0.02 else (1 if p.x > 0.02 else 0)
        ds = []
        for n, (a, b) in segs.items():
            if side < 0 and n.endswith(".R"):
                continue
            if side > 0 and n.endswith(".L"):
                continue
            d, t = seg_dist(p, a, b)
            # arms: never let torso bones pull the hands
            ds.append((d, n))
        ds.sort()
        top = ds[:3]
        w = {}
        tot = 0.0
        for d, n in top:
            x = 1.0 / (max(d, 0.004) ** 4)
            w[n] = x
            tot += x
        for n in w:
            w[n] /= tot
        # prune tiny weights
        w = {n: x for n, x in w.items() if x > 0.03}
        tot = sum(w.values())
        out.append({n: x / tot for n, x in w.items()})
    return out


def mesh_object(name, me, arm, weights, mat, color=(1, 1, 1, 1), per_vertex_colors=None):
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    for bn in DEFORM_ORDER:
        ob.vertex_groups.new(name=bn)
    for i, w in enumerate(weights):
        for n, x in w.items():
            ob.vertex_groups[n].add([i], x, "REPLACE")
    me.materials.clear()
    me.materials.append(get_material(mat))
    if "Col" not in me.color_attributes:
        ca = me.color_attributes.new(name="Col", type="BYTE_COLOR", domain="CORNER")
    ca = me.color_attributes["Col"]
    for li, loop in enumerate(me.loops):
        c = per_vertex_colors[loop.vertex_index] if per_vertex_colors else color
        ca.data[li].color = (srgb_to_linear(c[0]), srgb_to_linear(c[1]), srgb_to_linear(c[2]), c[3])
    me.color_attributes.active_color = ca
    for p in me.polygons:
        p.use_smooth = True
    mod = ob.modifiers.new("Armature", "ARMATURE")
    mod.object = arm
    ob.parent = arm
    return ob


def inflate_region(me_src, name, keep_vertex, offset, weights_src, scale_z=None):
    """Copy the faces whose vertices all satisfy keep_vertex(i, co) and push them along normals."""
    bm = bmesh.new()
    bm.from_mesh(me_src)
    bm.verts.ensure_lookup_table()
    bm.normal_update()
    # remember source indices (for weights) and normals before deleting anything
    idx_layer = bm.verts.layers.int.new("src")
    for v in bm.verts:
        v[idx_layer] = v.index
    keep = set(i for i, v in enumerate(bm.verts) if keep_vertex(i, v.co))
    del_faces = [f for f in bm.faces if not all(v.index in keep for v in f.verts)]
    bmesh.ops.delete(bm, geom=del_faces, context="FACES")
    loose = [v for v in bm.verts if not v.link_faces]
    bmesh.ops.delete(bm, geom=loose, context="VERTS")
    bm.verts.ensure_lookup_table()
    src_idx = [v[idx_layer] for v in bm.verts]
    normals = [v.normal.copy() for v in bm.verts]
    for v, n in zip(bm.verts, normals):
        v.co += n * offset
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    weights = [weights_src[i] for i in src_idx]
    return me, weights


# ------------------------------------------------------------------ head
def make_head(kind):
    bm = bmesh.new()
    f = kind == "F"
    bmesh.ops.create_uvsphere(bm, u_segments=20, v_segments=14, radius=1.0)
    for v in bm.verts:
        x, y, z = v.co
        sx = 0.078 if f else 0.084
        sy = 0.098 if f else 0.102
        sz = 0.112 if f else 0.118
        # narrower jaw, forward chin
        if z < 0:
            k = 1.0 + z * (0.28 if f else 0.22)
            x *= k
            y = y * (1.0 + z * 0.1) + (0.12 if y > 0 else 0.0) * (-z) * 0.5
        # flatter back of head, slight forehead
        if y < 0:
            y *= 0.93
        v.co = Vector((x * sx, y * sy, z * sz))
        v.co.z += 1.665
        v.co.y += 0.012
    # nose
    geom = bmesh.ops.create_cone(bm, cap_ends=True, cap_tris=True, segments=6, radius1=0.018, radius2=0.004, depth=0.045)
    for v in geom["verts"]:
        v.co = Vector((v.co.x * 0.9, v.co.z * 0.55 + 0.108, v.co.y * 0.45 + 1.66))
        v.co.y += (0.0 if v.co.z > 1.65 else 0.0)
    # ears
    for s in (-1, 1):
        g = bmesh.ops.create_uvsphere(bm, u_segments=8, v_segments=6, radius=1.0)
        for v in g["verts"]:
            v.co = Vector((v.co.x * 0.012 + s * (0.082 if f else 0.087), v.co.y * 0.022 - 0.005, v.co.z * 0.03 + 1.66))
    me = bpy.data.meshes.new("Head_" + kind)
    bm.to_mesh(me)
    bm.free()
    return me


def front_y(me, x, z, r=0.02):
    """Front surface of the head mesh near (x, z)."""
    best = -1.0
    for v in me.vertices:
        if abs(v.co.x - x) < r and abs(v.co.z - z) < r and v.co.y > best:
            best = v.co.y
    return best


def make_eyes(head):
    bm = bmesh.new()
    cols = []
    iris_y = {}
    for s in (-1, 1):
        fy = front_y(head, s * 0.032, 1.688)
        cy = fy - 0.006
        g = bmesh.ops.create_uvsphere(bm, u_segments=10, v_segments=8, radius=0.0125)
        for v in g["verts"]:
            v.co += Vector((s * 0.032, cy, 1.688))
        g2 = bmesh.ops.create_circle(bm, cap_ends=True, segments=10, radius=0.0068)
        for v in g2["verts"]:
            v.co = Vector((v.co.x + s * 0.032, cy + 0.0122, v.co.y + 1.688))
        iris_y[s] = cy + 0.011
    me = bpy.data.meshes.new("Eyes")
    bm.to_mesh(me)
    bm.free()
    for v in me.vertices:
        s = -1 if v.co.x < 0 else 1
        cols.append((0.95, 0.95, 0.93, 1) if v.co.y < iris_y[s] else (0.16, 0.11, 0.07, 1))
    return me, cols


def make_brows_mouth(head):
    bm = bmesh.new()
    for s in (-1, 1):
        fy = front_y(head, s * 0.033, 1.713)
        g = bmesh.ops.create_cube(bm, size=1.0)
        for v in g["verts"]:
            v.co = Vector((v.co.x * 0.032 + s * 0.033, v.co.y * 0.006 + fy, v.co.z * 0.007 + 1.713))
    fy = front_y(head, 0.0, 1.618, 0.025)
    g = bmesh.ops.create_cube(bm, size=1.0)
    for v in g["verts"]:
        v.co = Vector((v.co.x * 0.036, v.co.y * 0.005 + fy, v.co.z * 0.0045 + 1.618))
    me = bpy.data.meshes.new("Brows")
    bm.to_mesh(me)
    bm.free()
    return me


def make_hair(style, kind):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=20, v_segments=12, radius=1.0)
    f = kind == "F"
    base = (0.088 if f else 0.092, 0.108 if f else 0.11, 0.124)
    rm = []
    for v in bm.verts:
        x, y, z = v.co
        cut = {"short": -0.05, "buzz": 0.05, "long": -0.3, "bun": -0.1, "curly": -0.15}[style]
        front_cut = 0.35 if style != "buzz" else 0.25
        if z < cut or (y > 0.55 and z < front_cut):
            rm.append(v)
            continue
        thick = {"short": 1.08, "buzz": 1.03, "long": 1.1, "bun": 1.07, "curly": 1.2}[style]
        v.co = Vector((x * base[0] * thick, y * base[1] * thick - (0.005 if style == "long" else 0.0), z * base[2] * thick + 1.666))
    bmesh.ops.delete(bm, geom=rm, context="VERTS")
    if style == "long":
        g = bmesh.ops.create_cube(bm, size=1.0)
        for v in g["verts"]:
            v.co = Vector((v.co.x * 0.17, v.co.y * 0.05 - 0.07, v.co.z * 0.2 + 1.56))
    if style == "bun":
        g = bmesh.ops.create_uvsphere(bm, u_segments=8, v_segments=6, radius=0.045)
        for v in g["verts"]:
            v.co += Vector((0, -0.08, 1.78))
    me = bpy.data.meshes.new("Hair_" + style)
    bm.to_mesh(me)
    bm.free()
    return me


def make_cap(police=False):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=16, v_segments=10, radius=1.0)
    rm = [v for v in bm.verts if v.co.z < 0.05]
    bmesh.ops.delete(bm, geom=rm, context="VERTS")
    for v in bm.verts:
        v.co = Vector((v.co.x * 0.1, v.co.y * 0.12, v.co.z * (0.09 if not police else 0.07) + 1.72))
    g = bmesh.ops.create_cube(bm, size=1.0)
    for v in g["verts"]:
        v.co = Vector((v.co.x * 0.17, v.co.y * 0.09 + 0.14, v.co.z * 0.01 + 1.725))
    if police:
        g = bmesh.ops.create_cone(bm, cap_ends=True, segments=16, radius1=0.12, radius2=0.13, depth=0.05)
        for v in g["verts"]:
            v.co += Vector((0, 0.0, 1.8))
    me = bpy.data.meshes.new("Hat")
    bm.to_mesh(me)
    bm.free()
    return me


def make_glasses():
    bm = bmesh.new()
    for s in (-1, 1):
        g = bmesh.ops.create_cube(bm, size=1.0)
        for v in g["verts"]:
            v.co = Vector((v.co.x * 0.04 + s * 0.033, v.co.y * 0.006 + 0.103, v.co.z * 0.022 + 1.688))
    g = bmesh.ops.create_cube(bm, size=1.0)
    for v in g["verts"]:
        v.co = Vector((v.co.x * 0.15, v.co.y * 0.004 + 0.1, v.co.z * 0.004 + 1.699))
    me = bpy.data.meshes.new("Glasses")
    bm.to_mesh(me)
    bm.free()
    return me


# ------------------------------------------------------------------ armature
def make_armature():
    arm_data = bpy.data.armatures.new("HumanRig")
    arm = bpy.data.objects.new("Skeleton", arm_data)
    bpy.context.scene.collection.objects.link(arm)
    bpy.context.view_layer.objects.active = arm
    arm.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    eb = arm_data.edit_bones
    for name, (h, t, parent) in BONES.items():
        b = eb.new(name)
        b.head = h
        b.tail = t
        b.roll = 0.0
        if parent:
            b.parent = eb[parent]
            b.use_connect = False
    bpy.ops.object.mode_set(mode="OBJECT")
    return arm


# ------------------------------------------------------------------ animation authoring
# Pose values are Euler angles in degrees, applied in the order Y, X, Z in character
# axes (X = character right, Y = forward, Z = up), relative to the parent bone.
# Values are given for the character's LEFT side and mirrored with (x, -y, -z).
#   Torso bones:   x = pitch (- leans forward), y = side bend, z = yaw (+ turns left)
#   UpperArm.L:    y = elevation (0 = T-pose, -80 = hanging), x = swing of a hanging arm
#                  (+ forward), z = azimuth of a raised arm (-90 = pointing forward)
#   LowerArm.L:    z = -elbow bend (forward/inward), y = + lifts the forearm up
#   UpperLeg.L:    x = hip flexion (+ forward), y = abduction
#   LowerLeg.L:    x = -knee bend;  Foot.L: x = + toes up
def mirror(e):
    return (e[0], -e[1], -e[2])


def pose_to_local(arm, bone, euler_deg):
    R = arm.data.bones[bone].matrix_local.to_3x3()
    d = Euler([math.radians(a) for a in euler_deg], "YXZ").to_matrix()
    return (R.inverted() @ d @ R).to_quaternion()


def key_pose(arm, frame, pose, hips_offset=(0, 0, 0)):
    for pb in arm.pose.bones:
        e = pose.get(pb.name, (0, 0, 0))
        pb.rotation_mode = "QUATERNION"
        pb.rotation_quaternion = pose_to_local(arm, pb.name, e)
        pb.keyframe_insert("rotation_quaternion", frame=frame)
    hp = arm.pose.bones["Hips"]
    R = arm.data.bones["Hips"].matrix_local.to_3x3()
    hp.location = R.inverted() @ Vector(hips_offset)
    hp.keyframe_insert("location", frame=frame)


def sym(pose_left, extra=None):
    p = {}
    for k, v in pose_left.items():
        if k.endswith(".L"):
            p[k] = v
            p[k[:-2] + ".R"] = mirror(v)
        else:
            p[k] = v
    p.update(extra or {})
    return p


def R_(v):
    """Right-side value from a left-side description."""
    return mirror(v)


ARMS_DOWN = {"UpperArm.L": (0, -80, 0), "LowerArm.L": (0, 0, -10), "Shoulder.L": (0, 0, 0)}
IDLE = sym(ARMS_DOWN)


def new_action(arm, name):
    act = bpy.data.actions.new(name)
    act.use_fake_user = True
    arm.animation_data_create()
    arm.animation_data.action = act
    return act


def finish_action(arm, act):
    track = arm.animation_data.nla_tracks.new()
    track.name = act.name
    track.strips.new(act.name, int(act.frame_range[0]), act)
    track.mute = True
    arm.animation_data.action = None


def cycle(arm, name, length_s, fn, samples=16):
    act = new_action(arm, name)
    for i in range(samples + 1):
        ph = (i % samples) / samples
        pose, hips = fn(ph)
        key_pose(arm, 1 + round(i * length_s * FPS / samples), pose, hips)
    finish_action(arm, act)


def keys(arm, name, frames):
    act = new_action(arm, name)
    for (t, pose, hips) in frames:
        key_pose(arm, 1 + round(t * FPS), pose, hips)
    finish_action(arm, act)


def merge(*poses):
    out = {}
    for p in poses:
        out.update(p)
    return out


def locomotion(stride, arm_swing, knee, bob, lean, elbow, twist=6.0, crouch=0.0):
    def fn(ph):
        s = math.sin(2 * math.pi * ph)
        c = math.cos(2 * math.pi * ph)
        pose = {}
        for side, sg in (("L", 1.0), ("R", -1.0)):
            leg = s * sg
            ph_side = 2 * math.pi * ph + (0.0 if side == "L" else math.pi)
            hip_fl = stride * leg + crouch * 55
            # knee bends most while the leg swings forward (recovery)
            k = knee * max(0.0, math.sin(ph_side - 1.2)) + 6 + crouch * 95
            foot = max(0.0, -leg) * 12 - k * 0.15 + crouch * 30 - hip_fl * 0.2
            pose["UpperLeg." + side] = (hip_fl, 0, 0)
            pose["LowerLeg." + side] = (-k, 0, 0)
            pose["Foot." + side] = (foot, 0, 0)
            pose["Toes." + side] = (max(0.0, -leg) * 18, 0, 0)
            swing = -arm_swing * leg
            bend = elbow + max(0.0, swing) * 0.5
            if side == "L":
                pose["UpperArm.L"] = (swing, -76, 0)
                pose["LowerArm.L"] = (0, 0, -bend)
            else:
                pose["UpperArm.R"] = (swing, 76, 0)
                pose["LowerArm.R"] = (0, 0, bend)
        pose["Hips"] = (-crouch * 10, c * 2.5, s * twist * 0.6)
        pose["Spine"] = (-lean * 0.5 - crouch * 12, 0, -s * twist * 0.4)
        pose["Chest"] = (-lean * 0.5, 0, -s * twist * 0.8)
        pose["Neck"] = (lean * 0.5, 0, s * twist * 0.3)
        pose["Head"] = (lean * 0.4, 0, s * twist * 0.3)
        hips = (0, 0, -abs(c) * bob + bob * 0.5 - crouch * 0.38)
        return pose, hips
    return fn


def _guard():
    return merge(IDLE, sym({"UpperArm.L": (0, -40, -55), "LowerArm.L": (0, 0, -125)}), {"Spine": (-6, 0, 0)})


def _aim_pistol(ph):
    b = 1.2 * math.sin(2 * math.pi * ph)
    return merge(IDLE, {"UpperArm.R": R_((0, -6 + b, -86)), "LowerArm.R": R_((0, 0, -6)),
                        "UpperArm.L": (0, -8 + b, -62), "LowerArm.L": (0, 0, -38),
                        "Chest": (0, 0, -6), "Head": (0, 0, 6)})


def _aim_rifle(ph):
    b = 1.0 * math.sin(2 * math.pi * ph)
    return merge(IDLE, {"UpperArm.R": R_((0, -50 + b, -35)), "LowerArm.R": R_((0, 0, -105)), "Hand.R": R_((0, 0, -10)),
                        "UpperArm.L": (0, -18 + b, -72), "LowerArm.L": (0, 0, -30),
                        "Spine": (0, 0, -12), "Chest": (0, 0, -14), "Head": (4, 0, 24)})


def _seated(hands_on_wheel):
    p = sym({"UpperLeg.L": (86, 4, 0), "LowerLeg.L": (-84, 0, 0), "Foot.L": (4, 0, 0)}, {"Spine": (4, 0, 0)})
    if hands_on_wheel:
        p.update(sym({"UpperArm.L": (0, -18, -58), "LowerArm.L": (0, 0, -52)}))
    else:
        p.update(sym({"UpperArm.L": (10, -76, 0), "LowerArm.L": (0, 0, -45)}))
    return p


def _swim(ph):
    s = math.sin(2 * math.pi * ph)
    pose = {"Hips": (-80, 0, s * 8), "Spine": (4, 0, -s * 6), "Neck": (30, 0, 0), "Head": (35, 0, 0)}
    pose["UpperArm.L"] = (0, -10 + 10 * s, -60 - 40 * s)
    pose["UpperArm.R"] = R_((0, -10 - 10 * s, -60 + 40 * s))
    pose["LowerArm.L"] = (0, 0, -20)
    pose["LowerArm.R"] = R_((0, 0, -20))
    pose["UpperLeg.L"] = (-6 + 16 * s, 0, 0)
    pose["UpperLeg.R"] = (-6 - 16 * s, 0, 0)
    pose["LowerLeg.L"] = (-18, 0, 0)
    pose["LowerLeg.R"] = (-18, 0, 0)
    return pose, (0, 0, 0)


def _tread(ph):
    s = math.sin(2 * math.pi * ph)
    pose = sym({"UpperArm.L": (0, -35, -30 + 20 * s), "LowerArm.L": (0, 0, -30), "UpperLeg.L": (28 + 14 * s, 5, 0),
                "LowerLeg.L": (-55, 0, 0)}, {"Spine": (-4, 0, 0)})
    return pose, (0, 0, 0.02 * s)


def _panic(ph):
    pose, hips = locomotion(40, 20, 85, 0.06, 12, 40, 8)(ph)
    s = math.sin(4 * math.pi * ph)
    pose["UpperArm.L"] = (0, 30 + 20 * s, -40)
    pose["UpperArm.R"] = R_((0, 30 - 20 * s, -40))
    pose["LowerArm.L"] = (0, 60, 0)
    pose["LowerArm.R"] = R_((0, 60, 0))
    pose["Head"] = (-10, 0, 0)
    return pose, hips


def build_animations(arm):
    tau = 2 * math.pi
    cycle(arm, "idle", 3.0, lambda ph: (merge(IDLE, {"Chest": (-1.2 * math.sin(tau * ph), 0, 0),
                                                        "Head": (2 * math.sin(tau * ph), 0, 4 * math.sin(math.pi * ph)),
                                                        "UpperArm.L": (0, -80 + math.sin(tau * ph), 0),
                                                        "UpperArm.R": R_((0, -80 + math.sin(tau * ph), 0))}),
                                          (0, 0, -0.004 * (1 + math.sin(tau * ph)))), 12)
    cycle(arm, "walk", 1.1, locomotion(26, 22, 55, 0.035, 3, 14))
    cycle(arm, "run", 0.72, locomotion(40, 38, 90, 0.06, 10, 70, 9))
    cycle(arm, "sprint", 0.56, locomotion(52, 55, 110, 0.08, 16, 90, 11))
    cycle(arm, "crouch_walk", 1.3, locomotion(18, 10, 25, 0.02, 10, 35, 4, crouch=0.85))
    cycle(arm, "crouch_idle", 2.0, lambda ph: locomotion(0, 0, 0, 0.0, 10, 35, 0, crouch=0.85)(0.25), 4)
    cycle(arm, "fall", 0.8, lambda ph: (sym({"UpperArm.L": (0, -20 + 10 * math.sin(tau * ph), -20), "LowerArm.L": (0, 30, 0),
                                              "UpperLeg.L": (25, 4, 0), "LowerLeg.L": (-40, 0, 0)},
                                             {"UpperLeg.R": (8, 0, 0), "LowerLeg.R": (-20, 0, 0), "Spine": (-8, 0, 0)}),
                                        (0, 0, 0)), 8)
    cycle(arm, "swim", 1.4, _swim, 16)
    cycle(arm, "swim_idle", 2.0, _tread, 12)
    cycle(arm, "drive", 2.0, lambda ph: (_seated(True), (0, 0, -0.46)), 4)
    cycle(arm, "sit", 3.0, lambda ph: (merge(_seated(False), {"Chest": (-2 * math.sin(tau * ph), 0, 0)}), (0, 0, -0.46)), 6)
    cycle(arm, "phone", 3.0, lambda ph: (merge(IDLE, {"UpperArm.R": R_((25, -62, 0)), "LowerArm.R": R_((0, 0, -145)),
                                                        "Head": (0, 4, 6 * math.sin(tau * ph))}), (0, 0, 0)), 8)
    cycle(arm, "talk", 2.4, lambda ph: (merge(IDLE, {"UpperArm.R": R_((20 + 10 * math.sin(tau * ph), -65, 0)),
                                                       "LowerArm.R": R_((0, 0, -60 - 20 * math.sin(2 * tau * ph))),
                                                       "UpperArm.L": (12, -70, 0), "LowerArm.L": (0, 0, -35),
                                                       "Head": (3 * math.sin(2 * tau * ph), 0, 8 * math.sin(tau * ph))}),
                                         (0, 0, 0)), 12)
    cycle(arm, "hands_up", 2.0, lambda ph: (merge(IDLE, sym({"UpperArm.L": (0, 5 + 3 * math.sin(tau * ph), -10),
                                                              "LowerArm.L": (0, 85, 0)}), {"Head": (-6, 0, 0)}), (0, 0, 0)), 6)
    cycle(arm, "cower", 1.0, lambda ph: (sym({"UpperArm.L": (0, -30, -50), "LowerArm.L": (0, 0, -130), "UpperLeg.L": (95, 8, 0),
                                              "LowerLeg.L": (-130, 0, 0), "Foot.L": (30, 0, 0)},
                                             {"Spine": (-30 - 2 * math.sin(3 * tau * ph), 0, 0), "Chest": (-20, 0, 0),
                                              "Head": (-25, 0, 0)}), (0, -0.05, -0.52)), 8)
    cycle(arm, "panic_run", 0.7, _panic)
    cycle(arm, "aim_pistol", 2.0, lambda ph: (_aim_pistol(ph), (0, 0, 0)), 6)
    cycle(arm, "aim_rifle", 2.0, lambda ph: (_aim_rifle(ph), (0, 0, 0)), 6)
    cycle(arm, "idle_armed", 3.0, lambda ph: (merge(IDLE, {"UpperArm.R": R_((15, -74, 0)), "LowerArm.R": R_((0, 0, -30))}),
                                               (0, 0, 0)), 6)
    keys(arm, "jump", [(0.0, IDLE, (0, 0, 0)),
                       (0.12, sym({"UpperLeg.L": (35, 0, 0), "LowerLeg.L": (-65, 0, 0), "Foot.L": (25, 0, 0),
                                   "UpperArm.L": (-25, -65, 0), "LowerArm.L": (0, 0, -20)}, {"Spine": (-12, 0, 0)}),
                        (0, 0, -0.12)),
                       (0.28, sym({"UpperArm.L": (0, 10, -40), "LowerArm.L": (0, 20, 0), "UpperLeg.L": (10, 0, 0),
                                   "LowerLeg.L": (-12, 0, 0), "Foot.L": (-20, 0, 0)}, {"Spine": (4, 0, 0)}), (0, 0, 0.05)),
                       (0.45, sym({"UpperArm.L": (0, -20, -30), "LowerArm.L": (0, 25, 0), "UpperLeg.L": (35, 0, 0),
                                   "LowerLeg.L": (-55, 0, 0)}), (0, 0, 0))])
    keys(arm, "land", [(0.0, sym({"UpperLeg.L": (50, 0, 0), "LowerLeg.L": (-85, 0, 0), "Foot.L": (35, 0, 0),
                                  "UpperArm.L": (20, -55, 0), "LowerArm.L": (0, 0, -30)}, {"Spine": (-15, 0, 0)}),
                        (0, 0, -0.22)),
                       (0.3, IDLE, (0, 0, 0))])
    keys(arm, "punch", [(0.0, _guard(), (0, 0, 0)),
                        (0.1, merge(_guard(), {"Chest": (0, 0, 25), "UpperArm.R": R_((0, 0, -88)), "LowerArm.R": R_((0, 0, -5))}),
                         (0, 0.03, 0)),
                        (0.22, merge(_guard(), {"Chest": (0, 0, 10)}), (0, 0, 0)),
                        (0.4, _guard(), (0, 0, 0))])
    keys(arm, "kick", [(0.0, _guard(), (0, 0, 0)),
                       (0.15, merge(_guard(), {"UpperLeg.R": (45, 0, 0), "LowerLeg.R": (-95, 0, 0), "Spine": (6, 0, 0)}), (0, 0, 0)),
                       (0.3, merge(_guard(), {"UpperLeg.R": (88, 0, 0), "LowerLeg.R": (-5, 0, 0), "Spine": (14, 0, 0)}), (0, -0.05, 0)),
                       (0.5, merge(_guard(), {"UpperLeg.R": (20, 0, 0), "LowerLeg.R": (-40, 0, 0)}), (0, 0, 0)),
                       (0.7, _guard(), (0, 0, 0))])
    keys(arm, "melee_swing", [(0.0, _guard(), (0, 0, 0)),
                              (0.18, merge(_guard(), {"Chest": (0, 0, -35), "UpperArm.R": R_((0, 30, 20)), "LowerArm.R": R_((0, 70, 0))}),
                               (0, 0, 0)),
                              (0.32, merge(_guard(), {"Chest": (0, 0, 35), "UpperArm.R": R_((0, -10, -95)), "LowerArm.R": R_((0, 0, -10))}),
                               (0, 0, 0)),
                              (0.6, _guard(), (0, 0, 0))])
    keys(arm, "shoot_pistol", [(0.0, _aim_pistol(0), (0, 0, 0)),
                               (0.05, merge(_aim_pistol(0), {"UpperArm.R": R_((0, 6, -86)), "UpperArm.L": (0, 4, -62)}), (0, 0, 0)),
                               (0.2, _aim_pistol(0), (0, 0, 0))])
    keys(arm, "shoot_rifle", [(0.0, _aim_rifle(0), (0, 0, 0)),
                              (0.04, merge(_aim_rifle(0), {"Chest": (5, 0, -14)}), (0, -0.01, 0)),
                              (0.12, _aim_rifle(0), (0, 0, 0))])
    keys(arm, "reload_pistol", [(0.0, _aim_pistol(0), (0, 0, 0)),
                                (0.3, merge(IDLE, {"UpperArm.R": R_((30, -60, 0)), "LowerArm.R": R_((0, 0, -90)),
                                                   "UpperArm.L": (20, -60, 0), "LowerArm.L": (0, 0, -70)}), (0, 0, 0)),
                                (0.8, merge(IDLE, {"UpperArm.R": R_((30, -60, 0)), "LowerArm.R": R_((0, 0, -90)),
                                                   "UpperArm.L": (35, -55, 0), "LowerArm.L": (0, 0, -105)}), (0, 0, 0)),
                                (1.2, _aim_pistol(0), (0, 0, 0))])
    keys(arm, "reload_rifle", [(0.0, _aim_rifle(0), (0, 0, 0)),
                               (0.4, merge(_aim_rifle(0), {"UpperArm.L": (20, -60, -20), "LowerArm.L": (0, 0, -80),
                                                           "Head": (-15, 0, 24)}), (0, 0, 0)),
                               (1.2, merge(_aim_rifle(0), {"UpperArm.L": (35, -50, -30), "LowerArm.L": (0, 0, -100),
                                                           "Head": (-15, 0, 24)}), (0, 0, 0)),
                               (1.8, _aim_rifle(0), (0, 0, 0))])
    keys(arm, "climb", [(0.0, sym({"UpperArm.L": (0, 60, -20), "LowerArm.L": (0, 10, 0)}), (0, 0, 0)),
                        (0.35, sym({"UpperArm.L": (0, 20, -40), "LowerArm.L": (0, 60, 0), "UpperLeg.L": (60, 0, 0),
                                    "LowerLeg.L": (-90, 0, 0)}, {"Spine": (-20, 0, 0)}), (0, 0, 0)),
                        (0.8, sym({"UpperArm.L": (0, -60, -20), "LowerArm.L": (0, 0, -20), "UpperLeg.L": (40, 0, 0),
                                   "LowerLeg.L": (-70, 0, 0)}, {"Spine": (-25, 0, 0)}), (0, 0, 0)),
                        (1.1, IDLE, (0, 0, 0))])
    keys(arm, "enter_vehicle", [(0.0, IDLE, (0, 0, 0)),
                                (0.3, merge(IDLE, {"UpperArm.L": (0, -30, -60), "LowerArm.L": (0, 0, -20),
                                                   "Spine": (-10, 0, 20)}), (0, 0, 0)),
                                (0.7, merge(_seated(False), {"Spine": (-15, 0, 40)}), (0, 0, -0.3)),
                                (1.0, _seated(True), (0, 0, -0.46))])
    keys(arm, "exit_vehicle", [(0.0, _seated(True), (0, 0, -0.46)),
                               (0.4, merge(_seated(False), {"Spine": (-15, 0, -40), "UpperLeg.L": (60, 25, 0)}), (0, 0, -0.3)),
                               (0.9, IDLE, (0, 0, 0))])
    keys(arm, "death_back", [(0.0, IDLE, (0, 0, 0)),
                             (0.25, sym({"UpperArm.L": (-20, -40, 0), "UpperLeg.L": (10, 0, 0), "LowerLeg.L": (-30, 0, 0)},
                                        {"Spine": (15, 0, 0), "Chest": (10, 0, 0), "Head": (20, 0, 0)}), (0, -0.1, -0.4)),
                             (0.6, sym({"UpperArm.L": (0, -10, 20), "LowerArm.L": (0, 0, -20), "UpperLeg.L": (15, 6, 0),
                                        "LowerLeg.L": (-10, 0, 0)},
                                       {"Hips": (82, 0, 0), "Spine": (5, 0, 0), "Head": (10, 0, 20)}), (0, -0.55, -0.84))])
    keys(arm, "death_front", [(0.0, IDLE, (0, 0, 0)),
                              (0.3, sym({"UpperArm.L": (40, -60, 0), "UpperLeg.L": (20, 0, 0), "LowerLeg.L": (-60, 0, 0)},
                                        {"Spine": (-25, 0, 0), "Head": (-20, 0, 0)}), (0, 0.1, -0.45)),
                              (0.7, sym({"UpperArm.L": (0, 30, -70), "LowerArm.L": (0, 0, -40), "UpperLeg.L": (-5, 0, 0)},
                                        {"Hips": (-86, 0, 0), "Head": (-10, 0, 30)}), (0, 0.5, -0.83))])
    keys(arm, "hit_react", [(0.0, IDLE, (0, 0, 0)),
                            (0.08, merge(IDLE, {"Spine": (12, 4, 6), "Chest": (10, 0, 0), "Head": (15, 0, 0)}), (0, -0.05, 0)),
                            (0.35, IDLE, (0, 0, 0))])


# ------------------------------------------------------------------ main
SKIN_BASE = (0.86, 0.66, 0.53, 1.0)


def main():
    reset_scene()
    arm = make_armature()
    bones = {n: (Vector(v[0]), Vector(v[1])) for n, v in BONES.items()}
    body_bones = [n for n in BONES]
    objs = []
    for kind in ("M", "F"):
        V, E, R, root = body_skeleton(kind)
        me = make_skin_mesh("Body_" + kind, V, E, R, root, levels=1)
        w = auto_weights(me, {n: BONES[n] for n in body_bones})
        body = mesh_object("Body_" + kind, me, arm, w, "char_skin", SKIN_BASE)
        objs.append(body)
        # ------------------------------------------------ clothing derived from the body
        def region(pred):
            return lambda i, co: pred(co)
        torso = lambda co: co.z > 0.9 and co.z < 1.5 and abs(co.x) < 0.3 + (0.0)  # noqa: E731
        tshirt = lambda co: co.z > 0.9 and co.z < 1.49 and abs(co.x) < 0.33  # noqa: E731
        longsl = lambda co: co.z > 0.9 and co.z < 1.49 and abs(co.x) < 0.68  # noqa: E731
        jacket = lambda co: co.z > 0.86 and co.z < 1.5 and abs(co.x) < 0.69  # noqa: E731
        jeans = lambda co: co.z < 1.02 and co.z > 0.1 and abs(co.x) < 0.25  # noqa: E731
        shorts = lambda co: co.z < 1.02 and co.z > 0.62 and abs(co.x) < 0.25  # noqa: E731
        skirt = lambda co: co.z < 1.02 and co.z > 0.58 and abs(co.x) < 0.25  # noqa: E731
        shoes = lambda co: co.z < 0.13  # noqa: E731
        vest = lambda co: co.z > 1.0 and co.z < 1.46 and abs(co.x) < 0.2  # noqa: E731
        items = [("Top_TShirt", tshirt, 0.012), ("Top_LongSleeve", longsl, 0.013), ("Top_Jacket", jacket, 0.03),
                 ("Top_Suit", jacket, 0.024), ("Top_Vest", vest, 0.045),
                 ("Bottom_Jeans", jeans, 0.016), ("Bottom_Shorts", shorts, 0.014), ("Shoes_Sneakers", shoes, 0.012),
                 ("Shoes_Boots", lambda co: co.z < 0.28, 0.016)]
        if kind == "F":
            items.append(("Bottom_Skirt", skirt, 0.03))
        for (name, pred, off) in items:
            cme, cw = inflate_region(me, f"{name}_{kind}", region(pred), off, w)
            if len(cme.vertices) == 0:
                continue
            if name == "Bottom_Skirt":
                # flare the skirt outwards below the hips
                for v in cme.vertices:
                    if v.co.z < 0.92:
                        k = (0.92 - v.co.z) * 0.6
                        v.co.x *= 1.0 + k
                        v.co.y *= 1.0 + k * 0.8
            objs.append(mesh_object(f"{name}_{kind}", cme, arm, cw, "char_cloth", (1, 1, 1, 1)))
        # ------------------------------------------------ head
        hme = make_head(kind)
        hw = [{"Head": 1.0}] * len(hme.vertices)
        # neck blend: lowest vertices follow the neck a little
        hw = [({"Head": 0.7, "Neck": 0.3} if v.co.z < 1.575 else {"Head": 1.0}) for v in hme.vertices]
        objs.append(mesh_object("Head_" + kind, hme, arm, hw, "char_skin", SKIN_BASE))
        head_ref = make_head(kind)
        eyes_me, eye_cols = make_eyes(head_ref)
        eyes_me.name = "Eyes_" + kind
        objs.append(mesh_object("Eyes_" + kind, eyes_me, arm, [{"Head": 1.0}] * len(eyes_me.vertices), "char_detail",
                                per_vertex_colors=eye_cols))
        br = make_brows_mouth(head_ref)
        objs.append(mesh_object("Brows_" + kind, br, arm, [{"Head": 1.0}] * len(br.vertices), "char_hair",
                                (0.12, 0.08, 0.06, 1)))
    for style in ("short", "buzz", "long", "bun", "curly"):
        hm = make_hair(style, "M" if style in ("short", "buzz", "curly") else "F")
        weights = [({"Head": 0.8, "Neck": 0.2} if v.co.z < 1.6 else {"Head": 1.0}) for v in hm.vertices]
        objs.append(mesh_object("Hair_" + style.capitalize(), hm, arm, weights, "char_hair", (1, 1, 1, 1)))
    cap = make_cap(False)
    objs.append(mesh_object("Hat_Cap", cap, arm, [{"Head": 1.0}] * len(cap.vertices), "char_cloth", (1, 1, 1, 1)))
    pcap = make_cap(True)
    objs.append(mesh_object("Hat_Police", pcap, arm, [{"Head": 1.0}] * len(pcap.vertices), "char_cloth", (1, 1, 1, 1)))
    gl = make_glasses()
    objs.append(mesh_object("Glasses_Sun", gl, arm, [{"Head": 1.0}] * len(gl.vertices), "char_detail", (0.02, 0.02, 0.02, 1)))
    build_animations(arm)
    os.makedirs(OUT, exist_ok=True)
    export_glb(os.path.join(OUT, "human.glb"), [arm], with_anim=True)
    print("[characters] meshes:", len(objs), "actions:", len(bpy.data.actions))


if __name__ == "__main__":
    main()
