"""Procedural rigged humanoid characters with swappable clothing and animations.

Output: Game/assets/generated/characters/human.glb
  Skeleton (humanoid bones) + meshes:
    Body_M, Body_F   anatomical bodies built from implicit muscle primitives (see _anatomy.py):
                     pecs/breasts, abdominals, lats, trapezius, deltoids, biceps/triceps, glutes,
                     quads, calves, kneecaps, ankles; hands with five separate fingers
    Head_M, Head_F   cranium, brow ridge, eye sockets, cheekbones, nose (bridge, tip, wings,
                     nostrils), dental arch, lips, chin, jaw, ears + almond-shaped eyelids
    Eyes_M/F         sclera, limbal ring, radial iris, pupil;  Brows_M/F  brows + upper lashes
    Hair_Short, Hair_Buzz, Hair_Long, Hair_Bun, Hair_Curly (shrink-wrapped, strand grooves)
    Top_TShirt_M/F, Top_LongSleeve_M/F, Top_Jacket_M/F, Top_Suit_M/F, Top_Vest_M/F (police/security),
    Bottom_Jeans_M/F, Bottom_Shorts_M/F, Bottom_Skirt_F  (cut from the body along clean planes,
    smoothed so the fabric drapes, folded hems, wrinkles), Shoes_Sneakers, Shoes_Boots,
    Hat_Cap, Hat_Police, Glasses_Sun
  Every mesh stores its rest-pose position in UV/UV2 for stable shader detail (pores, weave,
  hair strands) and per-vertex colour variation (lips, cheeks, stubble, knuckles ...).
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
import _anatomy as A  # noqa: E402

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


def make_glasses(eyes):
    """Sunglasses sized to the eye positions: two rounded lenses, bridge, temples."""
    bm = bmesh.new()
    for s, c in eyes.items():
        g = bmesh.ops.create_circle(bm, cap_ends=True, segments=16, radius=1.0)
        for v in g["verts"]:
            x, y, z = v.co
            v.co = Vector((c.x + x * 0.026 + s * 0.003, c.y + A.EYE_R + 0.011, c.z + y * 0.018 - 0.002))
        g = bmesh.ops.create_cube(bm, size=1.0)
        for v in g["verts"]:
            v.co = Vector((v.co.x * 0.004 + s * 0.071, v.co.y * 0.1 + c.y - 0.035, v.co.z * 0.005 + c.z + 0.008))
    ex = eyes[1]
    g = bmesh.ops.create_cube(bm, size=1.0)
    for v in g["verts"]:
        v.co = Vector((v.co.x * 0.03, v.co.y * 0.003 + ex.y + A.EYE_R + 0.011, v.co.z * 0.004 + ex.z + 0.01))
    for s in (-1, 1):
        g = bmesh.ops.create_cube(bm, size=1.0)
        for v in g["verts"]:
            v.co = Vector((v.co.x * 0.018 + s * 0.058, v.co.y * 0.004 + ex.y + A.EYE_R + 0.004, v.co.z * 0.005 + ex.z + 0.008))
    bm.normal_update()
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
# (name, zmin, zmax, |x| max, offset, wrinkle amplitude)
CLOTHES = [("Top_TShirt", 0.9, 1.49, 0.33, 0.010, 0.0016), ("Top_LongSleeve", 0.9, 1.49, 0.672, 0.011, 0.0018),
           ("Top_Jacket", 0.86, 1.5, 0.678, 0.026, 0.0028), ("Top_Suit", 0.86, 1.5, 0.678, 0.02, 0.0012),
           ("Top_Vest", 1.0, 1.46, 0.2, 0.04, 0.0008), ("Bottom_Jeans", 0.1, 1.02, 0.25, 0.013, 0.0018),
           ("Bottom_Shorts", 0.6, 1.02, 0.25, 0.012, 0.0015), ("Shoes_Sneakers", None, 0.13, None, 0.011, 0.0),
           ("Shoes_Boots", None, 0.28, None, 0.014, 0.0)]


def main():
    reset_scene()
    arm = make_armature()
    objs = []
    trees = {}
    for kind in ("M", "F"):
        me = A.make_body(kind)
        w = auto_weights(me, BONES)
        cols = A.skin_colors(me, kind)
        A.rest_uvs(me)
        objs.append(mesh_object("Body_" + kind, me, arm, w, "char_skin", per_vertex_colors=cols))
        # ------------------------------------------------ clothing cut from the body
        items = list(CLOTHES)
        if kind == "F":
            sme, sw = A.make_skirt(kind)
            A.rest_uvs(sme)
            objs.append(mesh_object("Bottom_Skirt_F", sme, arm, sw, "char_cloth", (1, 1, 1, 1)))
        for (name, z0, z1, xm, off, wr) in items:
            cme = A.cloth_from_body(me, f"{name}_{kind}", z0, z1, xm, off, wr,
                                    smooth=14 if name.startswith("Top") else 5)
            if len(cme.vertices) == 0:
                continue
            ccols = None
            if name.startswith("Shoes"):
                sole = (0.93, 0.93, 0.92, 1) if name == "Shoes_Sneakers" else (0.3, 0.28, 0.26, 1)
                ccols = []
                for v in cme.vertices:
                    if v.co.z < 0.012:
                        v.co.z = 0.0
                    ccols.append(sole if v.co.z < 0.024 else (1, 1, 1, 1))
            cw = auto_weights(cme, BONES)
            A.rest_uvs(cme)
            objs.append(mesh_object(f"{name}_{kind}", cme, arm, cw, "char_cloth", (1, 1, 1, 1), ccols))
        # ------------------------------------------------ head, eyelids, eyes, brows + lashes
        tree = A.head_bvh(kind)
        trees[kind] = tree
        eyes = A.eye_centers(tree, kind)
        hme = A.make_head(kind)
        bm = bmesh.new()
        bm.from_mesh(hme)
        lids = A.eyelids(eyes)
        lme = bpy.data.meshes.new("lids")
        lids.to_mesh(lme)
        lids.free()
        bm.from_mesh(lme)
        bm.to_mesh(hme)
        bm.free()
        for p in hme.polygons:
            p.use_smooth = True
        hw = [({"Head": 0.6, "Neck": 0.4} if v.co.z < 1.56 else ({"Head": 0.85, "Neck": 0.15} if v.co.z < 1.585
                                                                 else {"Head": 1.0})) for v in hme.vertices]
        hcols = A.skin_colors(hme, kind, head=True)
        A.rest_uvs(hme)
        objs.append(mesh_object("Head_" + kind, hme, arm, hw, "char_skin", per_vertex_colors=hcols))
        eyes_me, eye_cols = A.make_eyes(eyes)
        eyes_me.name = "Eyes_" + kind
        A.rest_uvs(eyes_me)
        objs.append(mesh_object("Eyes_" + kind, eyes_me, arm, [{"Head": 1.0}] * len(eyes_me.vertices), "char_detail",
                                per_vertex_colors=eye_cols))
        br = A.lashes_brows(tree, eyes, kind)
        A.rest_uvs(br)
        objs.append(mesh_object("Brows_" + kind, br, arm, [{"Head": 1.0}] * len(br.vertices), "char_hair",
                                (0.72, 0.72, 0.72, 1)))
        if kind == "M":
            glasses_eyes = eyes
    for style in ("short", "buzz", "long", "bun", "curly"):
        hm = A.make_hair(style, "M", trees["M"])
        weights = [({"Head": 0.8, "Neck": 0.2} if v.co.z < 1.6 else {"Head": 1.0}) for v in hm.vertices]
        if style == "long":
            weights = [({"Chest": 0.5, "Neck": 0.5} if v.co.z < 1.5 else ({"Head": 0.5, "Neck": 0.5} if v.co.z < 1.6
                                                                         else {"Head": 1.0})) for v in hm.vertices]
        hcols = A.hair_colors(hm)
        A.rest_uvs(hm)
        objs.append(mesh_object("Hair_" + style.capitalize(), hm, arm, weights, "char_hair", per_vertex_colors=hcols))
    cap = make_cap(False)
    A.rest_uvs(cap)
    objs.append(mesh_object("Hat_Cap", cap, arm, [{"Head": 1.0}] * len(cap.vertices), "char_cloth", (1, 1, 1, 1)))
    pcap = make_cap(True)
    A.rest_uvs(pcap)
    objs.append(mesh_object("Hat_Police", pcap, arm, [{"Head": 1.0}] * len(pcap.vertices), "char_cloth", (1, 1, 1, 1)))
    gl = make_glasses(glasses_eyes)
    A.rest_uvs(gl)
    objs.append(mesh_object("Glasses_Sun", gl, arm, [{"Head": 1.0}] * len(gl.vertices), "char_detail", (0.02, 0.02, 0.02, 1)))
    build_animations(arm)
    os.makedirs(OUT, exist_ok=True)
    export_glb(os.path.join(OUT, "human.glb"), [arm], with_anim=True)
    tris = sum(sum(len(p.vertices) - 2 for p in o.data.polygons) for o in objs)
    print("[characters] meshes:", len(objs), "actions:", len(bpy.data.actions), "tris:", tris)
    for o in objs:
        print("   ", o.name, sum(len(p.vertices) - 2 for p in o.data.polygons))


if __name__ == "__main__":
    main()
