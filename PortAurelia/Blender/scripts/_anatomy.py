"""Anatomical human meshes for generate_characters.py.

Bodies and heads are modelled as sums of implicit "muscle" primitives (Blender metaballs:
ellipsoids, capsules, balls, negative carving balls), polygonised at millimetre resolution and
decimated to a game budget. That gives smooth anatomical transitions (deltoid into the arm,
pecs, lats, glutes, calves, knuckles, cheekbones, lips ...) without hand-made topology.

Coordinates are Blender space of the T-pose rig: character faces +Y, up is +Z and the
character's left side is -X.
"""
import math

import bpy
import bmesh
from mathutils import Vector, Matrix, noise
from mathutils.bvhtree import BVHTree


def _k(stiff):
    """Blender metaball field: s * (1 - d^2/r^2)^3, threshold 0.6 -> surface at k * r."""
    return math.sqrt(1.0 - (0.6 / stiff) ** (1.0 / 3.0))


def _rot_x_to(d, up=Vector((0, 0, 1))):
    """Quaternion that turns local +X into direction d (local Z stays close to `up`)."""
    x = Vector(d).normalized()
    z = Vector(up) - x * x.dot(up)
    if z.length < 1e-6:
        z = Vector((0, 1, 0)) - x * x.dot(Vector((0, 1, 0)))
    z.normalize()
    y = z.cross(x)
    m = Matrix((x, y, z)).transposed()
    return m.to_quaternion()


class Meta:
    """Collects metaball elements in named families (families never blend with each other)."""

    def __init__(self):
        self.fams = {}

    def ell(self, fam, co, radii, s=4.0, rot=None, neg=False):
        self.fams.setdefault(fam, []).append(("ELLIPSOID", Vector(co), tuple(radii), s, rot, neg, 0.0))

    def ball(self, fam, co, r, s=4.0, neg=False):
        self.fams.setdefault(fam, []).append(("BALL", Vector(co), (r, r, r), s, None, neg, 0.0))

    def cap(self, fam, a, b, r, s=4.0, neg=False):
        a, b = Vector(a), Vector(b)
        d = b - a
        self.fams.setdefault(fam, []).append(("CAPSULE", (a + b) * 0.5, (r, r, r), s, _rot_x_to(d), neg,
                                              d.length * 0.5))

    def chain(self, fam, pts, radii, s=4.0):
        for i in range(len(pts) - 1):
            self.cap(fam, pts[i], pts[i + 1], radii[i], s)

    def polygonize(self, res):
        """Returns a bmesh with every family polygonised (families as separate islands)."""
        bm = bmesh.new()
        for fam, els in self.fams.items():
            mb = bpy.data.metaballs.new("mb_" + fam)
            mb.resolution = res
            mb.render_resolution = res
            mb.threshold = 0.6
            ob = bpy.data.objects.new("mb_" + fam, mb)
            bpy.context.scene.collection.objects.link(ob)
            for typ, co, radii, s, rot, neg, half in els:
                e = mb.elements.new()
                e.type = typ
                e.co = co
                e.stiffness = s
                e.use_negative = neg
                k = _k(s)
                big = max(radii)
                e.radius = big / k
                if typ == "ELLIPSOID":
                    e.size_x, e.size_y, e.size_z = (r / big for r in radii)
                elif typ == "CAPSULE":
                    e.size_x = half
                if rot is not None:
                    e.rotation = rot
            dg = bpy.context.evaluated_depsgraph_get()
            me = bpy.data.meshes.new_from_object(ob.evaluated_get(dg))
            bm.from_mesh(me)
            bpy.data.objects.remove(ob, do_unlink=True)
            bpy.data.metaballs.remove(mb)
            bpy.data.meshes.remove(me)
        bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=res * 0.05)
        return bm


def decimate(bm, target_tris, name):
    """bmesh -> decimated Mesh datablock with about target_tris triangles."""
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    tris = sum(len(p.vertices) - 2 for p in me.polygons)
    if tris > target_tris:
        ob = bpy.data.objects.new(name + "_tmp", me)
        bpy.context.scene.collection.objects.link(ob)
        mod = ob.modifiers.new("dec", "DECIMATE")
        mod.ratio = target_tris / tris
        mod.use_collapse_triangulate = True
        dg = bpy.context.evaluated_depsgraph_get()
        res = bpy.data.meshes.new_from_object(ob.evaluated_get(dg))
        bpy.data.objects.remove(ob, do_unlink=True)
        bpy.data.meshes.remove(me)
        res.name = name
        me = res
    for p in me.polygons:
        p.use_smooth = True
    return me


def L(m, f, fem):
    return f if fem else m


# ====================================================================== body
FINGERS = [  # (y offset of the knuckle, length, radius, spread deg, knuckle x)
    (0.027, 0.080, 0.0093, 4.0, 0.792),
    (0.0085, 0.088, 0.0096, 0.0, 0.796),
    (-0.0095, 0.082, 0.0091, -3.0, 0.792),
    (-0.0265, 0.066, 0.0079, -8.0, 0.784),
]


def body_meta(kind):
    fem = kind == "F"
    m = Meta()
    B = "body"
    # ---------------------------------------------------------------- torso
    m.ell(B, (0, -0.01, 0.955), (L(0.150, 0.166, fem), L(0.098, 0.104, fem), 0.095))           # pelvis
    m.ell(B, (0, 0.018, 1.03), (L(0.128, 0.124, fem), L(0.084, 0.078, fem), 0.08))              # lower belly
    m.ell(B, (0, 0.008, 1.12), (L(0.127, 0.106, fem), L(0.086, 0.076, fem), 0.10))              # waist
    m.ell(B, (0, 0.0, 1.27), (L(0.148, 0.127, fem), L(0.104, 0.090, fem), 0.15))                # ribcage
    m.ell(B, (0, -0.012, 1.385), (L(0.162, 0.138, fem), L(0.088, 0.078, fem), 0.07))            # shoulder girdle
    for s in (-1, 1):
        if fem:
            m.ell(B, (s * 0.066, 0.064, 1.297), (0.058, 0.05, 0.054), 3.0)                      # breasts
        else:
            m.ell(B, (s * 0.064, 0.056, 1.325), (0.068, 0.032, 0.054), 3.0)                      # pectorals
            for z in (1.075, 1.135, 1.195):
                m.ell(B, (s * 0.032, 0.068, z), (0.024, 0.013, 0.021), 2.5)                      # abdominals
        m.ell(B, (s * 0.1, 0.008, 1.06), (0.034, 0.05, 0.065), 2.5)                              # obliques
        m.ell(B, (s * 0.098, -0.034, 1.27), (L(0.05, 0.038, fem), 0.054, 0.11), 2.5)            # lats
        m.cap(B, (s * 0.03, -0.028, 1.488), (s * 0.15, -0.026, 1.435), L(0.036, 0.029, fem), 3.0)  # trapezius
        m.ell(B, (s * 0.08, -0.066, 1.335), (0.048, 0.022, 0.06), 2.5)                           # scapula
        m.ell(B, (s * 0.07, -0.062, L(0.92, 0.915, fem)), (L(0.078, 0.09, fem), L(0.063, 0.075, fem),
                                                              L(0.084, 0.092, fem)))             # glutes
        m.cap(B, (s * 0.034, -0.008, 1.555), (s * 0.017, 0.044, 1.448), 0.015)                   # sternocleido
        if not fem:
            m.cap(B, (s * 0.026, 0.048, 1.437), (s * 0.15, 0.018, 1.447), 0.0095, 3.0)       # clavicle
    m.cap(B, (0, -0.002, 1.44), (0, 0.008, 1.57), L(0.056, 0.048, fem))                          # neck
    # ---------------------------------------------------------------- arms (T-pose along X)
    for s in (-1, 1):
        m.ell(B, (s * 0.188, -0.006, 1.426), (L(0.055, 0.045, fem), L(0.058, 0.048, fem), L(0.058, 0.048, fem)), 3.0)
        m.cap(B, (s * 0.2, -0.01, 1.43), (s * 0.43, -0.01, 1.43), L(0.041, 0.035, fem))           # humerus
        m.ell(B, (s * 0.31, 0.01, 1.428), (0.075, L(0.031, 0.024, fem), L(0.033, 0.026, fem)), 3.0)  # biceps
        m.ell(B, (s * 0.29, -0.028, 1.432), (0.085, L(0.031, 0.026, fem), L(0.033, 0.028, fem)), 3.0)  # triceps
        m.ball(B, (s * 0.44, -0.016, 1.43), 0.033)                                               # elbow
        m.ell(B, (s * 0.50, -0.008, 1.43), (0.075, L(0.042, 0.034, fem), L(0.036, 0.030, fem)))   # forearm
        m.cap(B, (s * 0.52, -0.01, 1.43), (s * 0.675, -0.01, 1.43), L(0.026, 0.022, fem))
        m.ell(B, (s * 0.69, -0.01, 1.43), (0.02, L(0.028, 0.024, fem), L(0.018, 0.015, fem)))     # wrist
        # hand: palm + thenar in the body family, each finger its own family (no webbing)
        hs = L(1.0, 0.9, fem)
        m.ell(B, (s * 0.745, -0.006, 1.428), (0.05 * hs, 0.041 * hs, 0.0155 * hs))
        m.ell(B, (s * 0.722, 0.021, 1.424), (0.027 * hs, 0.017 * hs, 0.0135 * hs))
        side = "L" if s < 0 else "R"
        for i, (fy, ln, r, spread, kx) in enumerate(FINGERS):
            ln *= hs
            r *= hs
            p = Vector((s * (0.69 + (kx - 0.69) * hs), fy * hs, 1.431))
            ang = math.radians(spread)
            d = Vector((s * math.cos(ang), math.sin(ang), 0.0))
            pts = [p.copy()]
            pitch = 0.0
            for seg_len, bend in ((0.45, 10.0), (0.32, 20.0), (0.23, 16.0)):
                pitch += math.radians(bend)
                dd = Vector((d.x * math.cos(pitch), d.y * math.cos(pitch), -math.sin(pitch)))
                p = p + dd * (ln * seg_len)
                pts.append(p.copy())
            # knuckle base sits inside the palm
            pts[0] = pts[0] - Vector((s * 0.014, 0, 0.001))
            m.chain(f"fg{side}{i}", pts, [r, r * 0.9, r * 0.8], 4.0)
        th = [Vector((s * 0.722, 0.03 * hs, 1.423)), Vector((s * (0.69 + 0.058 * hs), 0.052 * hs, 1.416)),
              Vector((s * (0.69 + 0.083 * hs), 0.064 * hs, 1.410)), Vector((s * (0.69 + 0.104 * hs), 0.071 * hs, 1.405))]
        m.chain(f"th{side}", th, [0.0125 * hs, 0.0108 * hs, 0.0096 * hs], 4.0)
    # ---------------------------------------------------------------- legs
    for s in (-1, 1):
        m.ell(B, (s * 0.1, 0.004, 0.84), (L(0.081, 0.092, fem), L(0.084, 0.09, fem), 0.12))       # upper thigh
        m.ell(B, (s * 0.1, 0.03, 0.70), (L(0.061, 0.058, fem), 0.05, 0.14))                      # quads
        m.ell(B, (s * 0.134, 0.004, 0.74), (0.034, 0.054, 0.12))                                 # vastus lateralis
        m.ell(B, (s * 0.1, -0.03, 0.72), (0.054, 0.047, 0.14))                                   # hamstrings
        m.ell(B, (s * 0.074, 0.0, 0.8), (0.038, 0.054, 0.10))                                    # adductors
        m.ell(B, (s * 0.105, 0.012, 0.52), (0.047, 0.05, 0.055))                                 # knee
        m.ball(B, (s * 0.105, 0.05, 0.525), 0.025)                                               # kneecap
        m.ell(B, (s * 0.107, -0.03, 0.39), (L(0.049, 0.044, fem), 0.049, 0.10))                  # calf
        m.cap(B, (s * 0.107, 0.004, 0.48), (s * 0.11, -0.012, 0.12), L(0.035, 0.031, fem))       # shin
        m.ell(B, (s * 0.11, -0.018, 0.085), (0.028, 0.032, 0.03))                                # ankle
        m.ball(B, (s * 0.136, -0.02, 0.085), 0.011)
        m.ball(B, (s * 0.086, -0.015, 0.09), 0.011)
        m.ell(B, (s * 0.11, -0.036, 0.04), (0.03, 0.038, 0.038))                                 # heel
        m.ell(B, (s * 0.11, 0.045, 0.037), (0.034, 0.075, 0.028))                                # arch
        m.ell(B, (s * 0.112, 0.115, 0.024), (0.043, 0.03, 0.022))                                # ball
        m.ell(B, (s * 0.11, 0.158, 0.019), (0.04, 0.028, 0.016))                                 # toes
    return m


def make_body(kind, target_tris=8500):
    bm = body_meta(kind).polygonize(0.0045)
    for v in bm.verts:                          # flat soles
        if v.co.z < 0.006:
            v.co.z = 0.006
    return decimate(bm, target_tris, "Body_" + kind)


def skin_colors(me, kind, head=False):
    """Per-vertex skin variation (multiplied with the per-instance skin tint)."""
    fem = kind == "F"
    cols = []
    for v in me.vertices:
        x, y, z = v.co
        r, g, b = 0.97, 0.95, 0.94
        n = noise.noise(v.co * 18.0) * 0.025
        r, g, b = r + n, g + n, b + n
        if head:
            lip = math.exp(-((x / 0.023) ** 2 + ((z - 1.617) / 0.0105) ** 2)) * max(0.0, min(1.0, (y - 0.081) / 0.01))
            r, g, b = r * (1 - 0.14 * lip), g * (1 - 0.38 * lip), b * (1 - 0.34 * lip)
            for s in (-1, 1):                                                                    # rosy cheeks
                ch = math.exp(-(((x - s * 0.045) / 0.025) ** 2 + ((z - 1.645) / 0.022) ** 2)) * (y > 0.04)
                g *= 1 - 0.06 * ch
                b *= 1 - 0.07 * ch
            nose = math.exp(-((x / 0.013) ** 2 + ((z - 1.652) / 0.014) ** 2)) * (y > 0.09)
            g *= 1 - 0.06 * nose
            b *= 1 - 0.06 * nose
            if not fem:                                                                          # stubble shadow
                jaw = (z < 1.64) * (y > -0.01) * (1 - lip)
                jaw *= max(0.0, min(1.0, (1.642 - z) / 0.012)) if z > 1.63 else 1.0
                if abs(x) < 0.02 and 1.624 < z < 1.638:
                    jaw = max(jaw, 0.8) * (1 - lip)                                              # moustache zone
                if z < 1.585 and y < 0.03:
                    jaw *= max(0.0, (z - 1.555) / 0.03)
                r, g, b = r * (1 - 0.1 * jaw), g * (1 - 0.1 * jaw), b * (1 - 0.06 * jaw)
            for s in (-1, 1):                                                                    # eye sockets
                es = math.exp(-(((x - s * 0.032) / 0.02) ** 2 + ((z - 1.678) / 0.012) ** 2)) * (y > 0.06)
                r, g, b = r * (1 - 0.07 * es), g * (1 - 0.09 * es), b * (1 - 0.06 * es)
        else:
            # knuckles, elbows, knees slightly redder; palms and soles lighter
            for s in (-1, 1):
                for c, rad in (((s * 0.44, -0.03, 1.43), 0.03), ((s * 0.105, 0.05, 0.525), 0.035),
                               ((s * 0.79, 0.0, 1.44), 0.03)):
                    d = (Vector(c) - v.co).length
                    k = math.exp(-(d / rad) ** 2)
                    g *= 1 - 0.05 * k
                    b *= 1 - 0.06 * k
            if abs(x) > 0.7 and z < 1.425:
                r, g, b = r * 1.04, g * 1.05, b * 1.05
        cols.append((min(r, 1.0), min(g, 1.0), min(b, 1.0), 1.0))
    return cols


# ====================================================================== head
def head_meta(kind, ears=True):
    fem = kind == "F"
    m = Meta()
    H = "head"
    m.ell(H, (0, -0.012, 1.697), (L(0.078, 0.076, fem), L(0.096, 0.093, fem), L(0.094, 0.092, fem)))  # cranium
    m.ell(H, (0, 0.038, 1.714), (0.063, 0.05, 0.052))                                              # forehead
    m.ell(H, (0, 0.044, 1.652), (L(0.062, 0.058, fem), 0.054, 0.054))                              # midface
    m.ell(H, (0, 0.036, 1.608), (L(0.055, 0.047, fem), 0.052, 0.03))                               # jaw
    m.ell(H, (0, 0.079, 1.586), (L(0.025, 0.019, fem), 0.018, L(0.018, 0.015, fem)))               # chin
    for s in (-1, 1):
        m.ball(H, (s * L(0.047, 0.041, fem), 0.006, 1.614), L(0.017, 0.014, fem), 2.5)             # jaw angle
        m.ell(H, (s * 0.044, 0.07, 1.664), (0.017, 0.013, 0.011), 2.5)                             # cheekbone
        m.cap(H, (s * 0.012, 0.093, 1.702), (s * 0.05, 0.081, 1.705), L(0.0095, 0.007, fem), 3.0)  # brow ridge
        m.ball(H, (s * 0.032, 0.102, 1.682), 0.0165, 4.0, True)                                    # eye socket
        m.ball(H, (s * 0.0122, 0.099, 1.6475), 0.0074)                                             # nose wing
        m.ball(H, (s * 0.0062, 0.1075, 1.6405), 0.0033, 4.0, True)                                 # nostril
        if ears:
            m.ell(H, (s * 0.076, -0.008, 1.665), (0.009, 0.019, 0.029))                            # ear
            m.ball(H, (s * 0.075, -0.002, 1.641), 0.0075)                                          # lobe
            m.ball(H, (s * 0.0845, -0.004, 1.667), 0.008, 4.0, True)                               # concha
    m.cap(H, (0, 0.093, 1.686), (0, 0.105, 1.658), L(0.0066, 0.006, fem))                          # nose bridge
    m.ball(H, (0, 0.108, 1.6525), L(0.0102, 0.0092, fem))                                          # nose tip
    m.ell(H, (0, 0.066, 1.619), (L(0.033, 0.03, fem), 0.026, 0.024), 3.0)                          # dental arch
    m.ell(H, (0, 0.095, 1.632), (0.011, 0.009, 0.009))                                             # philtrum
    m.ell(H, (0, 0.095, 1.6225), (L(0.022, 0.021, fem), 0.0085, L(0.0065, 0.0075, fem)))             # upper lip
    m.ell(H, (0, 0.093, 1.6115), (L(0.019, 0.019, fem), 0.009, L(0.0075, 0.0085, fem)))             # lower lip
    m.ell(H, (0, 0.1045, 1.6168), (0.02, 0.006, 0.0015), 4.0, None, True)                          # mouth line
    return m


def make_head(kind, target_tris=4600):
    bm = head_meta(kind).polygonize(0.0022)
    return decimate(bm, target_tris, "Head_" + kind)


def head_bvh(kind):
    bm = head_meta(kind, ears=False).polygonize(0.004)
    bm.normal_update()
    tree = BVHTree.FromBMesh(bm)
    bm.free()
    return tree


def surface_y(tree, x, z, y_from=0.3):
    """Front surface of the head at (x, z): ray along -Y."""
    hit = tree.ray_cast(Vector((x, y_from, z)), Vector((0, -1, 0)), 1.0)
    return hit[0].y if hit[0] is not None else None


def eye_centers(tree, kind):
    out = {}
    for s in (-1, 1):
        x = s * 0.032
        lid = surface_y(tree, x, 1.697)
        cheek = surface_y(tree, x, 1.664)
        front = min(lid, cheek) + 0.0015
        out[s] = Vector((x, front - 0.0122, 1.682))
    return out


EYE_R = 0.0122


def make_eyes(eyes):
    """Eyeballs with sclera, limbal ring, two-tone iris and pupil (vertex colours)."""
    bm = bmesh.new()
    for s, c in eyes.items():
        g = bmesh.ops.create_uvsphere(bm, u_segments=20, v_segments=14, radius=EYE_R)
        for v in g["verts"]:
            # sphere poles along +Y (looking forward)
            x, y, z = v.co
            v.co = Vector((x, z, -y)) + c
    me = bpy.data.meshes.new("Eyes")
    bm.to_mesh(me)
    bm.free()
    cols = []
    for v in me.vertices:
        s = -1 if v.co.x < 0 else 1
        d = (v.co - eyes[s]).normalized()
        a = math.degrees(math.acos(max(-1.0, min(1.0, d.y))))    # angle from the view axis
        if a < 11:
            c = (0.015, 0.012, 0.01)
        elif a < 25:
            t = (a - 11) / 14
            ray = 0.5 + 0.5 * math.sin(math.atan2(d.z, d.x) * 23.0)
            c = ((0.38 - 0.18 * t) * (0.85 + 0.3 * ray), (0.26 - 0.12 * t) * (0.85 + 0.3 * ray), (0.12 - 0.05 * t))
        elif a < 29:
            c = (0.09, 0.065, 0.045)
        else:
            vein = max(0.0, noise.noise(v.co * 900.0)) * 0.1
            shade = 1.0 - 0.25 * min(1.0, max(0.0, (a - 35) / 30))
            c = (0.8 * shade, (0.76 - vein) * shade, (0.72 - vein) * shade)
        cols.append((c[0], c[1], c[2], 1.0))
    return me, cols


def _lid_edge(az_deg, upper):
    t = (az_deg / 68.0) ** 2
    return (23.0 - 30.0 * t) if upper else (-24.0 + 27.0 * t)


def eyelids(eyes):
    """Upper and lower lid shells hugging the eyeball, almond-shaped opening (skin, joined
    into the head mesh)."""
    bm = bmesh.new()
    segs_u, segs_v = 18, 7
    for s, c in eyes.items():
        for upper in (True, False):
            rows = []
            for j in range(segs_v + 1):
                t = j / segs_v
                row = []
                for i in range(segs_u + 1):
                    az_d = -68 + 136 * i / segs_u
                    edge = _lid_edge(az_d, upper)
                    el_d = edge + (80.0 - edge) * t if upper else edge - (edge + 70.0) * t
                    el = math.radians(el_d)
                    az = math.radians(az_d)
                    thick = (0.0022 if upper else 0.0016) * (0.55 + 0.45 * t)
                    d = Vector((math.sin(az) * math.cos(el), math.cos(az) * math.cos(el), math.sin(el)))
                    row.append(bm.verts.new(c + d * (EYE_R + thick)))
                rows.append(row)
            for j in range(segs_v):
                for i in range(segs_u):
                    bm.faces.new([rows[j][i], rows[j][i + 1], rows[j + 1][i + 1], rows[j + 1][i]])
    bm.normal_update()
    for f in bm.faces:
        ctr = f.calc_center_median()
        s = -1 if ctr.x < 0 else 1
        if f.normal.dot(ctr - eyes[s]) < 0:
            f.normal_flip()
    return bm


def lashes_brows(tree, eyes, kind):
    """Eyebrows following the brow surface + upper lashes (hair-tinted mesh)."""
    fem = kind == "F"
    bm = bmesh.new()
    for s, c in eyes.items():
        # brows: 9 stations from medial to lateral
        top, bot = [], []
        n = 9
        for i in range(n):
            t = i / (n - 1)
            x = s * (0.012 + 0.046 * t)
            zc = 1.705 + 0.006 * math.sin(math.pi * min(1.0, t * 1.25)) - 0.004 * t
            w = L(0.0062, 0.0042, fem) * (1.0 - 0.55 * t) + 0.0015
            pts = []
            for z in (zc - w * 0.5, zc + w * 0.5):
                y = surface_y(tree, x, z)
                pts.append(bm.verts.new((x, (y if y is not None else 0.09) + 0.0012, z)))
            bot.append(pts[0])
            top.append(pts[1])
        for i in range(n - 1):
            f = [bot[i], bot[i + 1], top[i + 1], top[i]]
            if s < 0:
                f.reverse()
            bm.faces.new(f)
        # lashes along the upper lid edge
        lash_in, lash_out = [], []
        for i in range(11):
            az_d = -60 + 120 * i / 10
            az = math.radians(az_d)
            el = math.radians(_lid_edge(az_d, True) + 2.0)
            d = Vector((math.sin(az) * math.cos(el), math.cos(az) * math.cos(el), math.sin(el)))
            p = c + d * (EYE_R + 0.0024)
            lash_in.append(bm.verts.new(p))
            lash_out.append(bm.verts.new(p + d * 0.0025 + Vector((0, 0.0012, 0.0016))))
        for i in range(10):
            f = [lash_in[i], lash_in[i + 1], lash_out[i + 1], lash_out[i]]
            if s > 0:
                f.reverse()
            bm.faces.new(f)
    bm.normal_update()
    for f in bm.faces:
        if f.normal.y < 0:
            f.normal_flip()
    me = bpy.data.meshes.new("Brows")
    bm.to_mesh(me)
    bm.free()
    return me


# ====================================================================== hair
def make_hair(style, kind, tree):
    """Hair shell shrink-wrapped onto the (ear-less) head, with a shaped hairline, strand
    grooves and clumps; long styles fall to the shoulders. Double-sided (inner shell)."""
    fem = kind == "F"
    thick = {"short": 0.009, "buzz": 0.0028, "long": 0.011, "bun": 0.008, "curly": 0.02}[style]
    ctr = Vector((0, -0.01, 1.675))
    nu, nv = 48, 26
    grid = []
    bm = bmesh.new()
    for j in range(1, nv + 1):
        pol = math.pi * j / nv                           # 0 = top
        row = []
        for i in range(nu):
            az = 2 * math.pi * i / nu                    # 0 = front (+Y)
            d = Vector((math.sin(pol) * math.sin(az), math.sin(pol) * math.cos(az), math.cos(pol)))
            front = math.cos(az)                         # 1 front, -1 back
            side = abs(math.sin(az))
            # hairline height (z) per azimuth
            if style == "buzz":
                line = 1.705 - 0.02 * side - 0.07 * max(0.0, -front)
            elif style in ("long", "bun"):
                line = 1.72 - 0.05 * side - 0.12 * max(0.0, -front)
            else:
                temple = math.exp(-((abs(math.degrees(az) if az < math.pi else math.degrees(az) - 360) - 38) / 14) ** 2)
                line = 1.726 + 0.008 * temple - 0.052 * side - 0.09 * max(0.0, -front) - 0.01 * max(0.0, side - 0.8)
            hit = tree.ray_cast(ctr + d * 0.3, -d, 0.35)
            if hit[0] is None:
                row.append(None)
                continue
            p = hit[0]
            if p.z < line:
                row.append(None)
                continue
            # strand grooves (along the flow from crown to hairline) and clumps
            g = 0.0
            if style != "buzz":
                g = 0.0018 * math.sin(az * 38 + noise.noise(p * 40) * 3.0) + 0.003 * noise.noise(p * 26)
            if style == "curly":
                g += 0.006 * noise.noise(p * 70) + 0.004 * abs(noise.noise(p * 140))
            # volume on top, fringe falls a little over the forehead
            vol = thick * (1.0 + 0.35 * max(0.0, math.cos(pol)))
            q = p + hit[1] * (vol + g)
            if style in ("long", "bun") and front > 0.6 and p.z < line + 0.02:
                q += Vector((0, 0.004, -0.004))
            row.append(q)
        grid.append(row)
    rows = len(grid)
    V = [[bm.verts.new(p) if p is not None else None for p in r] for r in grid]
    for j in range(rows - 1):
        for i in range(nu):
            a, b, c, d = V[j][i], V[j][(i + 1) % nu], V[j + 1][(i + 1) % nu], V[j + 1][i]
            quad = [a, b, c, d]
            ok = [q for q in quad if q is not None]
            if len(ok) >= 3:
                try:
                    bm.faces.new(ok)
                except ValueError:
                    pass
    # top cap
    top = [v for v in V[0] if v is not None]
    if len(top) >= 3:
        hit = tree.ray_cast(ctr + Vector((0, 0, 0.3)), Vector((0, 0, -1)), 0.35)
        cv = bm.verts.new(hit[0] + Vector((0, 0, thick * 1.35)))
        for i in range(nu):
            a, b = V[0][i], V[0][(i + 1) % nu]
            if a is not None and b is not None:
                try:
                    bm.faces.new([cv, b, a])
                except ValueError:
                    pass
    if style == "long":
        _curtain(bm, tree, ctr, thick)
    if style == "bun":
        g = bmesh.ops.create_uvsphere(bm, u_segments=14, v_segments=10, radius=0.042)
        for v in g["verts"]:
            v.co = Vector((v.co.x, v.co.y * 0.9, v.co.z * 0.85)) + Vector((0, -0.085, 1.765))
    bm.normal_update()
    for f in bm.faces:
        if f.normal.dot(f.calc_center_median() - ctr) < 0:
            f.normal_flip()
    # inner shell (double-sided look from below / inside long hair)
    geom = bmesh.ops.duplicate(bm, geom=list(bm.faces))
    new_faces = [e for e in geom["geom"] if isinstance(e, bmesh.types.BMFace)]
    new_verts = [e for e in geom["geom"] if isinstance(e, bmesh.types.BMVert)]
    bm.normal_update()
    for v in new_verts:
        v.co -= v.normal * 0.0015
    bmesh.ops.reverse_faces(bm, faces=new_faces)
    me = bpy.data.meshes.new("Hair_" + style)
    bm.to_mesh(me)
    bm.free()
    return me


def _curtain(bm, tree, ctr, thick):
    """Long hair falling over the back and sides down to the shoulders."""
    cols, rows = 30, 12
    grid = []
    for i in range(cols + 1):
        az = math.radians(62 + (360 - 124) * i / cols)          # skips the face
        d = Vector((math.sin(az), math.cos(az), 0.0))
        hit = tree.ray_cast(Vector((ctr.x, ctr.y, 1.665)) + d * 0.3, -d, 0.35)
        r0 = ((hit[0] - Vector((ctr.x, ctr.y, 1.665))).length if hit[0] is not None else 0.085) + thick * 0.8
        back = max(0.0, -math.cos(az))
        zb = 1.52 - 0.13 * back + 0.02 * noise.noise(Vector((az * 3.0, 0.0, 0.0)))
        col = []
        for j in range(rows + 1):
            t = j / rows
            z = 1.69 - (1.69 - zb) * t
            r = r0 + 0.012 * t + 0.025 * t * t * back
            r += 0.003 * math.sin(az * 30 + t * 4) * t
            p = Vector((ctr.x, ctr.y, z)) + d * r
            col.append(bm.verts.new(p))
        grid.append(col)
    for i in range(cols):
        for j in range(rows):
            bm.faces.new([grid[i][j], grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1]])


def hair_colors(me):
    cols = []
    for v in me.vertices:
        n = noise.noise(v.co * 60.0) * 0.12 + noise.noise(Vector((v.co.x * 400, v.co.y * 400, v.co.z * 30))) * 0.1
        k = max(0.6, min(1.15, 0.95 + n))
        cols.append((k, k, k, 1.0))
    return cols


# ====================================================================== clothing
def cloth_from_body(me_src, name, zmin, zmax, xmax, offset, wrinkle=0.0, xmin=None, smooth=4):
    """Cut the body along clean planes (bisect), keep the inside, push it out along the normals,
    add a folded hem (visible fabric thickness) and fabric wrinkles."""
    bm = bmesh.new()
    bm.from_mesh(me_src)
    planes = []
    if zmin is not None:
        planes.append(((0, 0, zmin), (0, 0, 1)))
    if zmax is not None:
        planes.append(((0, 0, zmax), (0, 0, 1)))
    if xmax is not None:
        planes += [((xmax, 0, 0), (1, 0, 0)), ((-xmax, 0, 0), (1, 0, 0))]
    for co, no in planes:
        geom = list(bm.verts) + list(bm.edges) + list(bm.faces)
        bmesh.ops.bisect_plane(bm, geom=geom, plane_co=co, plane_no=no, dist=1e-5)
    eps = 1e-4

    def inside(p):
        if zmin is not None and p.z < zmin - eps:
            return False
        if zmax is not None and p.z > zmax + eps:
            return False
        if xmax is not None and abs(p.x) > xmax + eps:
            return False
        if xmin is not None and abs(p.x) < xmin - eps:
            return False
        return True

    dead = [f for f in bm.faces if not all(inside(v.co) for v in f.verts)]
    bmesh.ops.delete(bm, geom=dead, context="FACES")
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if not v.link_faces], context="VERTS")
    inner = [v for v in bm.verts if not v.is_boundary]
    for _ in range(smooth):
        bmesh.ops.smooth_laplacian_vert(bm, verts=inner, lambda_factor=0.6, lambda_border=0.0,
                                        use_x=True, use_y=True, use_z=True, preserve_volume=True)
    bm.normal_update()
    normals = {v: v.normal.copy() for v in bm.verts}
    for v in bm.verts:
        n = normals[v]
        w = 0.0
        if wrinkle > 0.0:
            p = v.co
            w = wrinkle * (noise.noise(p * 22.0) * 0.6 + 0.4 * math.sin(p.z * 180 + noise.noise(p * 9) * 4))
        v.co += n * (offset + w)
    # hem: extrude the open borders inwards so the cloth has visible thickness
    border = [e for e in bm.edges if e.is_boundary]
    if border:
        ext = bmesh.ops.extrude_edge_only(bm, edges=border)
        nv = [e for e in ext["geom"] if isinstance(e, bmesh.types.BMVert)]
        for v in nv:
            ln = [e.other_vert(v) for e in v.link_edges]
            src = [o for o in ln if o in normals]
            n = normals[src[0]] if src else Vector((0, 0, 0))
            v.co -= n * offset * 0.85
    bm.normal_update()
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = True
    return me


def make_skirt(kind):
    """Flared skirt as its own surface (not two leg tubes): elliptical rings from the waist
    over the hips to the hem, with soft pleats. Returns (mesh, weights)."""
    rings = [(1.02, 0.138, 0.098), (0.97, 0.162, 0.118), (0.92, 0.178, 0.134), (0.84, 0.19, 0.145),
             (0.72, 0.205, 0.158), (0.6, 0.222, 0.172)]
    nu = 48
    bm = bmesh.new()
    grid = []
    for (z, rx, ry) in rings:
        row = []
        t = (1.02 - z) / 0.42
        for i in range(nu):
            a = 2 * math.pi * i / nu
            pleat = 1.0 + 0.035 * t * math.sin(a * 14)
            row.append(bm.verts.new((math.sin(a) * rx * pleat, math.cos(a) * ry * pleat - 0.012, z)))
        grid.append(row)
    for j in range(len(rings) - 1):
        for i in range(nu):
            bm.faces.new([grid[j][i], grid[j][(i + 1) % nu], grid[j + 1][(i + 1) % nu], grid[j + 1][i]])
    bm.normal_update()
    for f in bm.faces:
        c = f.calc_center_median()
        if f.normal.dot(Vector((c.x, c.y + 0.012, 0))) < 0:
            f.normal_flip()
    geom = bmesh.ops.duplicate(bm, geom=list(bm.faces))
    nf = [e for e in geom["geom"] if isinstance(e, bmesh.types.BMFace)]
    nv = [e for e in geom["geom"] if isinstance(e, bmesh.types.BMVert)]
    bm.normal_update()
    for v in nv:
        v.co -= v.normal * 0.003
    bmesh.ops.reverse_faces(bm, faces=nf)
    me = bpy.data.meshes.new("Bottom_Skirt_" + kind)
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = True
    weights = []
    for v in me.vertices:
        t = max(0.0, min(1.0, (1.0 - v.co.z) / 0.4))
        w = {"Hips": 1.0 - 0.65 * t}
        if abs(v.co.x) < 0.04:
            w["UpperLeg.L"] = 0.325 * t
            w["UpperLeg.R"] = 0.325 * t
        else:
            w["UpperLeg.L" if v.co.x < 0 else "UpperLeg.R"] = 0.65 * t
        weights.append({k: x for k, x in w.items() if x > 0.0})
    return me, weights


# body zones, hidden by the game when clothing fully covers them (see character_model.gd)
BODY_ZONES = ["Base", "Neck", "Torso", "Hips", "Arms", "Thighs", "Shins", "Ankles", "Feet"]


def _zone(c):
    x, z = abs(c.x), c.z
    if x > 0.672:
        return "Base"                       # hands
    if z >= 1.49:
        return "Neck"                       # hidden in first person (camera inside the head)
    if x > 0.33:
        return "Arms" if z > 1.2 else "Base"
    if z >= 1.02:
        return "Torso"
    if z >= 0.9:
        return "Hips"
    if z >= 0.6:
        return "Thighs"
    if z >= 0.28:
        return "Shins"
    if z >= 0.13:
        return "Ankles"
    return "Feet"


def split_body(me):
    """Cuts the body along the clothing planes and returns {zone: Mesh}."""
    bm = bmesh.new()
    bm.from_mesh(me)
    planes = [((0, 0, z), (0, 0, 1)) for z in (0.13, 0.28, 0.6, 0.9, 1.02, 1.49)]
    planes += [((x, 0, 0), (1, 0, 0)) for x in (0.33, -0.33, 0.672, -0.672)]
    for co, no in planes:
        geom = list(bm.verts) + list(bm.edges) + list(bm.faces)
        bmesh.ops.bisect_plane(bm, geom=geom, plane_co=co, plane_no=no, dist=1e-5)
    out = {}
    for zone in BODY_ZONES:
        b2 = bm.copy()
        dead = [f for f in b2.faces if _zone(f.calc_center_median()) != zone]
        bmesh.ops.delete(b2, geom=dead, context="FACES")
        bmesh.ops.delete(b2, geom=[v for v in b2.verts if not v.link_faces], context="VERTS")
        if len(b2.faces) == 0:
            b2.free()
            continue
        m = bpy.data.meshes.new(me.name + "_" + zone)
        b2.to_mesh(m)
        b2.free()
        for p in m.polygons:
            p.use_smooth = True
        out[zone] = m
    bm.free()
    return out


def rest_uvs(me):
    """UVMap = Godot rest position (x, y), UVMap2.x = rest z: stable detail for the shader."""
    uv = me.uv_layers.new(name="UVMap")
    uv2 = me.uv_layers.new(name="UVMap2")
    for li, loop in enumerate(me.loops):
        co = me.vertices[loop.vertex_index].co
        gx, gy, gz = co.x, co.z, -co.y
        # the glTF exporter flips V (v' = 1 - v): store 1 - v to arrive unchanged
        uv.data[li].uv = (gx, 1.0 - gy)
        uv2.data[li].uv = (gz, 1.0)
