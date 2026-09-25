"""Shared helpers for all Port Aurelia Blender scripts.

Works both with `blender -b -P script.py -- args` and with the `bpy` Python module.
Geometry is accumulated in Godot space (X east, Y up, Z south) by MeshBuilder and
converted to Blender space only when the mesh object is created.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BLENDER_DIR = os.path.dirname(HERE)
PROJECT = os.path.dirname(BLENDER_DIR)
LIB = os.path.join(BLENDER_DIR, "lib")
BUILD = os.path.join(BLENDER_DIR, "build")
GAME = os.path.join(PROJECT, "Game")
GEN = os.path.join(GAME, "assets", "generated")
if LIB not in sys.path:
    sys.path.insert(0, LIB)

import bpy  # noqa: E402
import bmesh  # noqa: E402

from citygen import geom as G  # noqa: E402,F401


def script_args():
    if "--" in sys.argv:
        return sys.argv[sys.argv.index("--") + 1:]
    return sys.argv[1:]


def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    for m in list(bpy.data.meshes):
        bpy.data.meshes.remove(m)
    for m in list(bpy.data.materials):
        bpy.data.materials.remove(m)


def clear_objects():
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)
    for m in list(bpy.data.meshes):
        if m.users == 0:
            bpy.data.meshes.remove(m)
    for a in list(bpy.data.armatures):
        if a.users == 0:
            bpy.data.armatures.remove(a)
    for c in list(bpy.data.curves):
        if c.users == 0:
            bpy.data.curves.remove(c)


# ------------------------------------------------------------------ materials
# preview colours only; Godot replaces materials by name on import
MATERIAL_PREVIEW = {
    "road_asphalt": (0.12, 0.12, 0.13), "road_marking": (0.9, 0.9, 0.85), "sidewalk": (0.6, 0.58, 0.55),
    "curb": (0.65, 0.64, 0.6), "concrete": (0.55, 0.55, 0.53), "parking": (0.15, 0.15, 0.16),
    "terrain": (0.35, 0.45, 0.2), "grass": (0.3, 0.45, 0.15), "sand": (0.85, 0.77, 0.58),
    "dirt": (0.45, 0.36, 0.25), "rock": (0.5, 0.48, 0.44), "bridge_concrete": (0.6, 0.6, 0.58),
    "metal": (0.6, 0.62, 0.64), "barrier": (0.72, 0.71, 0.68), "tunnel_wall": (0.75, 0.75, 0.72),
    "light_emissive": (1.0, 0.95, 0.8), "roof_flat": (0.45, 0.45, 0.44), "roof_tiles": (0.6, 0.3, 0.2),
    "roof_shingles": (0.3, 0.3, 0.32), "roof_metal": (0.6, 0.62, 0.64), "trim": (0.85, 0.83, 0.78),
    "window_glass": (0.2, 0.3, 0.4), "shopfront": (0.2, 0.25, 0.3), "awning": (0.7, 0.2, 0.2),
    "sign_emissive": (1.0, 0.3, 0.6), "sign_panel": (0.2, 0.2, 0.22), "metal_dark": (0.12, 0.12, 0.13),
    "door": (0.3, 0.22, 0.16), "wood": (0.5, 0.38, 0.26), "plastic": (0.8, 0.8, 0.8),
    "metal_painted": (0.4, 0.45, 0.5), "rubber": (0.05, 0.05, 0.05), "fabric": (0.6, 0.6, 0.6),
    "bark": (0.35, 0.27, 0.2), "leaves": (0.25, 0.4, 0.12), "palm_frond": (0.3, 0.45, 0.15),
    "palm_bark": (0.5, 0.42, 0.3), "pine": (0.15, 0.28, 0.12), "glass_clear": (0.7, 0.8, 0.85),
    "signal_lens": (0.2, 0.2, 0.2), "container": (0.6, 0.3, 0.2), "water_deco": (0.2, 0.4, 0.5),
    "wood_planks": (0.5, 0.38, 0.26), "interior_wall": (0.9, 0.88, 0.84), "interior_floor": (0.5, 0.45, 0.4),
    "crop": (0.45, 0.5, 0.2), "chrome": (0.9, 0.9, 0.92),
}
FACADES = ["glass_blue", "glass_green", "glass_silver", "glass_bronze", "glass_dark", "concrete_panel",
           "stone", "brick_red", "brick_brown", "plaster", "stucco_white", "metal_siding", "wood_siding"]
for _f in FACADES:
    MATERIAL_PREVIEW["facade_" + _f] = (0.6, 0.62, 0.66) if _f.startswith("glass") else (0.8, 0.76, 0.7)


def get_material(name):
    m = bpy.data.materials.get(name)
    if m is not None:
        return m
    m = bpy.data.materials.new(name)
    col = MATERIAL_PREVIEW.get(name, (0.8, 0.8, 0.8))
    m.diffuse_color = (col[0], col[1], col[2], 1.0)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get("Principled BSDF")
    if bsdf:
        bsdf.inputs["Base Color"].default_value = (col[0], col[1], col[2], 1.0)
        bsdf.inputs["Roughness"].default_value = 0.8
    return m


# ------------------------------------------------------------------ mesh builder
class MeshBuilder:
    """Accumulates polygons in Godot space with per-corner UV / colour and a material per face."""

    def __init__(self):
        self.verts = []
        self.faces = []
        self.uvs = []     # per face: list of (u, v)
        self.uv2 = []     # per face: (u, v) constant per face (facade params) or None
        self.nrm = []     # per face: list of custom normals (Godot space) or None
        self.cols = []    # per face: list of (r, g, b, a)
        self.fmat = []
        self.mats = []
        self._mat_idx = {}

    def mat(self, name):
        i = self._mat_idx.get(name)
        if i is None:
            i = len(self.mats)
            self.mats.append(name)
            self._mat_idx[name] = i
        return i

    def empty(self):
        return not self.faces

    def face(self, pts, uvs=None, mat="concrete", col=(1.0, 1.0, 1.0, 1.0), up=None, uv2=None, nrm=None, cols=None):
        """Add a polygon (list of 3D Godot points). If `up` is given the winding is
        flipped so that the face normal points along `up`."""
        if len(pts) < 3:
            return
        if up is not None:
            n = newell(pts)
            if n[0] * up[0] + n[1] * up[1] + n[2] * up[2] < 0:
                pts = list(reversed(pts))
                if uvs is not None:
                    uvs = list(reversed(uvs))
                if nrm is not None:
                    nrm = list(reversed(nrm))
                if cols is not None:
                    cols = list(reversed(cols))
        base = len(self.verts)
        self.verts.extend(pts)
        self.faces.append(list(range(base, base + len(pts))))
        if uvs is None:
            uvs = [(p[0], p[2]) for p in pts]
        self.uvs.append(list(uvs))
        self.uv2.append(uv2)
        self.nrm.append(list(nrm) if nrm is not None else None)
        if cols is not None:
            self.cols.append([tuple(c) if len(c) == 4 else (c[0], c[1], c[2], 1.0) for c in cols])
        else:
            if len(col) == 3:
                col = (col[0], col[1], col[2], 1.0)
            self.cols.append([col] * len(pts))
        self.fmat.append(self.mat(mat))

    def quad(self, a, b, c, d, mat, uvs=None, col=(1, 1, 1, 1), up=None):
        self.face([a, b, c, d], uvs, mat, col, up)

    def merge(self, other):
        for i, f in enumerate(other.faces):
            pts = [other.verts[k] for k in f]
            base = len(self.verts)
            self.verts.extend(pts)
            self.faces.append(list(range(base, base + len(pts))))
            self.uvs.append(other.uvs[i])
            self.uv2.append(other.uv2[i])
            self.nrm.append(other.nrm[i])
            self.cols.append(other.cols[i])
            self.fmat.append(self.mat(other.mats[other.fmat[i]]))

    def transformed(self, fn):
        out = MeshBuilder()
        out.mats = list(self.mats)
        out._mat_idx = dict(self._mat_idx)
        out.verts = [fn(v) for v in self.verts]
        out.faces = [list(f) for f in self.faces]
        out.uvs = [list(u) for u in self.uvs]
        out.uv2 = list(self.uv2)
        out.nrm = list(self.nrm)
        out.cols = [list(c) for c in self.cols]
        out.fmat = list(self.fmat)
        return out

    def tri_count(self):
        return sum(len(f) - 2 for f in self.faces)

    def to_object(self, name, collection=None, smooth=False, merge_dist=0.0005, auto_smooth=None):
        me = bpy.data.meshes.new(name)
        bverts = [(v[0], -v[2], v[1]) for v in self.verts]
        me.from_pydata(bverts, [], self.faces)
        # create every layer first: adding a layer invalidates earlier layer references
        me.uv_layers.new(name="UVMap")
        has_uv2 = any(u is not None for u in self.uv2)
        if has_uv2:
            me.uv_layers.new(name="UVMap2")
        me.color_attributes.new(name="Col", type="BYTE_COLOR", domain="CORNER")
        flat_uv = []
        flat_uv2 = []
        flat_col = []
        # UVs are authored in Godot/glTF convention (v down); the glTF exporter flips
        # Blender's V, so store 1 - v here to arrive unchanged in Godot.
        for fi, f in enumerate(self.faces):
            for u in self.uvs[fi]:
                flat_uv.append(u[0])
                flat_uv.append(1.0 - u[1])
            if has_uv2:
                u2 = self.uv2[fi] or (0.0, 0.0)
                for _ in f:
                    flat_uv2.append(u2[0])
                    flat_uv2.append(1.0 - u2[1])
            for c in self.cols[fi]:
                flat_col.extend((c[0], c[1], c[2], c[3]))
        nloops = len(me.loops)
        if len(flat_uv) == nloops * 2:
            me.uv_layers["UVMap"].data.foreach_set("uv", flat_uv)
            if has_uv2:
                me.uv_layers["UVMap2"].data.foreach_set("uv", flat_uv2)
            me.color_attributes["Col"].data.foreach_set("color", flat_col)
        else:
            print(f"  ! {name}: loop count mismatch ({nloops} vs {len(flat_uv) // 2}), skipping UV/colour")
        if len(me.polygons) == len(self.fmat):
            me.polygons.foreach_set("material_index", self.fmat)
        for mname in self.mats:
            me.materials.append(get_material(mname))
        me.color_attributes.active_color = me.color_attributes["Col"]
        me.uv_layers.active = me.uv_layers["UVMap"]
        me.validate(clean_customdata=False)
        obj = bpy.data.objects.new(name, me)
        (collection or bpy.context.scene.collection).objects.link(obj)
        if any(n is not None for n in self.nrm):
            me.polygons.foreach_set("use_smooth", [True] * len(me.polygons))
            cn = []
            for fi, f in enumerate(self.faces):
                fn = self.nrm[fi]
                if fn is None:
                    pts = [self.verts[k] for k in f]
                    nn = newell(pts)
                    l = math.sqrt(nn[0] ** 2 + nn[1] ** 2 + nn[2] ** 2) or 1.0
                    fn = [(nn[0] / l, nn[1] / l, nn[2] / l)] * len(f)
                for v in fn:
                    cn.append((v[0], -v[2], v[1]))
            if len(cn) == len(me.loops):
                me.normals_split_custom_set(cn)
            return obj
        if merge_dist > 0:
            bm = bmesh.new()
            bm.from_mesh(me)
            bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=merge_dist)
            bm.to_mesh(me)
            bm.free()
        me.polygons.foreach_set("use_smooth", [smooth] * len(me.polygons))
        if auto_smooth is not None:
            _set_auto_smooth(obj, auto_smooth)
        return obj


def _set_auto_smooth(obj, angle_deg):
    me = obj.data
    for p in me.polygons:
        p.use_smooth = True
    try:
        # Blender 4.1+: smooth by angle via modifier-free API
        me.set_sharp_from_angle(angle=math.radians(angle_deg))
    except AttributeError:
        me.use_auto_smooth = True
        me.auto_smooth_angle = math.radians(angle_deg)


def newell(pts):
    nx = ny = nz = 0.0
    n = len(pts)
    for i in range(n):
        x0, y0, z0 = pts[i]
        x1, y1, z1 = pts[(i + 1) % n]
        nx += (y0 - y1) * (z0 + z1)
        ny += (z0 - z1) * (x0 + x1)
        nz += (x0 - x1) * (y0 + y1)
    return (nx, ny, nz)


# ------------------------------------------------------------------ primitive helpers (Godot space)
def box(mb, center, size, mat, col=(1, 1, 1, 1), rot_y=0.0, uv_scale=1.0, top=True, bottom=False, mats=None):
    """Axis-aligned (rotated about Y) box. mats: optional dict side->material ('top','side','bottom')."""
    cx, cy, cz = center
    sx, sy, sz = size[0] * 0.5, size[1] * 0.5, size[2] * 0.5
    c, s = math.cos(rot_y), math.sin(rot_y)

    def P(x, y, z):
        return (cx + x * c + z * s, cy + y, cz - x * s + z * c)

    corners = [P(-sx, -sy, -sz), P(sx, -sy, -sz), P(sx, -sy, sz), P(-sx, -sy, sz),
               P(-sx, sy, -sz), P(sx, sy, -sz), P(sx, sy, sz), P(-sx, sy, sz)]
    sm = (mats or {}).get("side", mat)
    tm = (mats or {}).get("top", mat)
    bm_ = (mats or {}).get("bottom", mat)
    sides = [(0, 1, 5, 4, 2 * sx), (1, 2, 6, 5, 2 * sz), (2, 3, 7, 6, 2 * sx), (3, 0, 4, 7, 2 * sz)]
    for (a, b, cc, d, w) in sides:
        pa, pb, pc, pd = corners[a], corners[b], corners[cc], corners[d]
        mid = ((pa[0] + pc[0]) * 0.5, (pa[1] + pc[1]) * 0.5, (pa[2] + pc[2]) * 0.5)
        outward = (mid[0] - cx, 0.0, mid[2] - cz)
        mb.face([pa, pb, pc, pd], [(0, 0), (w * uv_scale, 0), (w * uv_scale, 2 * sy * uv_scale), (0, 2 * sy * uv_scale)],
                sm, col, up=outward)
    if top:
        pts = [corners[4], corners[5], corners[6], corners[7]]
        mb.face(pts, [(p[0] * uv_scale, p[2] * uv_scale) for p in pts], tm, col, up=(0, 1, 0))
    if bottom:
        pts = [corners[0], corners[1], corners[2], corners[3]]
        mb.face(pts, [(p[0] * uv_scale, p[2] * uv_scale) for p in pts], bm_, col, up=(0, -1, 0))


def prism(mb, poly2d, y0, y1, mat_side, mat_top=None, col=(1, 1, 1, 1), top=True, bottom=False,
          uv_mode="facade", u_offset=0.0, mat_bottom=None):
    """Vertical extrusion of a plan polygon. Facade UVs: u = perimeter metres, v = height metres."""
    poly = G.ensure_ccw(poly2d)
    n = len(poly)
    u = u_offset
    cx = sum(p[0] for p in poly) / n
    cz = sum(p[1] for p in poly) / n
    for i in range(n):
        a = poly[i]
        b = poly[(i + 1) % n]
        l = G.dist(a, b)
        if l < 1e-4:
            continue
        pa0 = (a[0], y0, a[1])
        pb0 = (b[0], y0, b[1])
        pb1 = (b[0], y1, b[1])
        pa1 = (a[0], y1, a[1])
        mid = ((a[0] + b[0]) * 0.5, (a[1] + b[1]) * 0.5)
        e = G.norm(G.sub(b, a))
        outward = G.left(e)  # positive-area polygon: interior on the right
        uvs = [(u, y0), (u + l, y0), (u + l, y1), (u, y1)] if uv_mode == "facade" else None
        mb.face([pa0, pb0, pb1, pa1], uvs, mat_side, col, up=(outward[0], 0.0, outward[1]))
        u += l
        _ = mid
    if top:
        pts = [(p[0], y1, p[1]) for p in poly]
        _cap(mb, pts, mat_top or mat_side, col, (0, 1, 0))
    if bottom:
        pts = [(p[0], y0, p[1]) for p in poly]
        _cap(mb, pts, mat_bottom or mat_top or mat_side, col, (0, -1, 0))
    _ = (cx, cz)
    return u


def _cap(mb, pts3, mat, col, up):
    poly2 = [(p[0], p[2]) for p in pts3]
    if G.poly_is_convex(G.ensure_ccw(poly2)) and len(pts3) <= 12:
        mb.face(pts3, [(p[0], p[2]) for p in pts3], mat, col, up=up)
        return
    tris = G.triangulate(poly2)
    for (i, j, k) in tris:
        tri = [pts3[i], pts3[j], pts3[k]]
        mb.face(tri, [(p[0], p[2]) for p in tri], mat, col, up=up)


def cap_polygon(mb, poly2d, y, mat, col=(1, 1, 1, 1), up=True, uv_scale=1.0):
    pts = [(p[0], y, p[1]) for p in poly2d]
    poly2 = [(p[0], p[1]) for p in poly2d]
    tris = G.triangulate(poly2)
    for (i, j, k) in tris:
        tri = [pts[i], pts[j], pts[k]]
        mb.face(tri, [(p[0] * uv_scale, p[2] * uv_scale) for p in tri], mat, col, up=(0, 1 if up else -1, 0))


def cylinder(mb, base, radius, height, mat, segs=10, col=(1, 1, 1, 1), top=True, bottom=False, r_top=None,
             axis="y", uv_v_scale=1.0):
    """Cylinder along +Y (or 'x'/'z') starting at `base`."""
    r1 = radius
    r2 = radius if r_top is None else r_top
    bx, by, bz = base

    def P(a, r, h):
        ca, sa = math.cos(a) * r, math.sin(a) * r
        if axis == "y":
            return (bx + ca, by + h, bz + sa)
        if axis == "x":
            return (bx + h, by + ca, bz + sa)
        return (bx + ca, by + sa, bz + h)

    circ = 2 * math.pi * max(r1, r2)
    for i in range(segs):
        a0 = 2 * math.pi * i / segs
        a1 = 2 * math.pi * (i + 1) / segs
        p0, p1 = P(a0, r1, 0), P(a1, r1, 0)
        p2, p3 = P(a1, r2, height), P(a0, r2, height)
        am = (a0 + a1) * 0.5
        if axis == "y":
            out = (math.cos(am), 0, math.sin(am))
        elif axis == "x":
            out = (0, math.cos(am), math.sin(am))
        else:
            out = (math.cos(am), math.sin(am), 0)
        u0, u1 = circ * i / segs, circ * (i + 1) / segs
        mb.face([p0, p1, p2, p3], [(u0, 0), (u1, 0), (u1, height * uv_v_scale), (u0, height * uv_v_scale)], mat, col, up=out)
    ax_up = {"y": (0, 1, 0), "x": (1, 0, 0), "z": (0, 0, 1)}[axis]
    if top and r2 > 0:
        pts = [P(2 * math.pi * i / segs, r2, height) for i in range(segs)]
        mb.face(pts, [(math.cos(2 * math.pi * i / segs) * 0.5 + 0.5, math.sin(2 * math.pi * i / segs) * 0.5 + 0.5) for i in range(segs)],
                mat, col, up=ax_up)
    if bottom and r1 > 0:
        pts = [P(2 * math.pi * i / segs, r1, 0) for i in range(segs)]
        mb.face(pts, None, mat, col, up=(-ax_up[0], -ax_up[1], -ax_up[2]))


def sweep(mb, path3, profile2, mat, col=(1, 1, 1, 1), closed_profile=False, uv_len_scale=1.0, up=(0, 1, 0)):
    """Sweep a 2D profile (x = lateral right, y = up) along a 3D path (Godot space)."""
    n = len(path3)
    if n < 2:
        return
    frames = []
    for i in range(n):
        if i == 0:
            d = _sub3(path3[1], path3[0])
        elif i == n - 1:
            d = _sub3(path3[-1], path3[-2])
        else:
            d = _add3(_norm3(_sub3(path3[i], path3[i - 1])), _norm3(_sub3(path3[i + 1], path3[i])))
        d = _norm3(d)
        r = _norm3(_cross3(d, up))
        if _len3(r) < 1e-6:
            r = (1, 0, 0)
        u = _cross3(r, d)
        frames.append((r, u))
    acc = [0.0]
    for i in range(1, n):
        acc.append(acc[-1] + _len3(_sub3(path3[i], path3[i - 1])))
    m = len(profile2)
    rings = []
    for i in range(n):
        r, u = frames[i]
        p = path3[i]
        rings.append([(p[0] + r[0] * q[0] + u[0] * q[1], p[1] + r[1] * q[0] + u[1] * q[1],
                       p[2] + r[2] * q[0] + u[2] * q[1]) for q in profile2])
    plen = [0.0]
    for k in range(1, m):
        plen.append(plen[-1] + math.hypot(profile2[k][0] - profile2[k - 1][0], profile2[k][1] - profile2[k - 1][1]))
    segs = m if closed_profile else m - 1
    for i in range(n - 1):
        for k in range(segs):
            k2 = (k + 1) % m
            a, b = rings[i][k], rings[i][k2]
            c, d = rings[i + 1][k2], rings[i + 1][k]
            uvs = [(acc[i] * uv_len_scale, plen[k]), (acc[i] * uv_len_scale, plen[k2] if k2 else plen[-1]),
                   (acc[i + 1] * uv_len_scale, plen[k2] if k2 else plen[-1]), (acc[i + 1] * uv_len_scale, plen[k])]
            mb.face([a, b, c, d], uvs, mat, col)


def _sub3(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _add3(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def _cross3(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _len3(a):
    return math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])


def _norm3(a):
    l = _len3(a)
    return (a[0] / l, a[1] / l, a[2] / l) if l > 1e-9 else (0.0, 0.0, 0.0)


# ------------------------------------------------------------------ export
def export_glb(path, objects=None, with_anim=False, collection_name=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if objects is not None:
        bpy.ops.object.select_all(action="DESELECT")
        for o in objects:
            o.select_set(True)
            for c in o.children_recursive:
                c.select_set(True)
    kw = dict(filepath=path, export_format="GLB", use_selection=objects is not None,
              export_apply=True, export_vertex_color="ACTIVE", export_all_vertex_colors=False,
              export_animations=with_anim, export_skins=with_anim, export_materials="EXPORT",
              export_image_format="NONE", export_extras=True, export_yup=True,
              export_tangents=False, export_normals=True)
    if with_anim:
        kw.update(export_animation_mode="ACTIONS", export_force_sampling=True,
                  export_optimize_animation_size=True, export_anim_single_armature=True)
    try:
        bpy.ops.export_scene.gltf(**kw)
    except TypeError:
        kw.pop("export_all_vertex_colors", None)
        bpy.ops.export_scene.gltf(**kw)
    return path


def lod_decimate(obj, ratio):
    """Collapse-decimate a copy of obj (returns new object)."""
    new = obj.copy()
    new.data = obj.data.copy()
    bpy.context.scene.collection.objects.link(new)
    mod = new.modifiers.new("dec", "DECIMATE")
    mod.ratio = ratio
    mod.use_collapse_triangulate = True
    bpy.context.view_layer.objects.active = new
    dg = bpy.context.evaluated_depsgraph_get()
    ev = new.evaluated_get(dg)
    me = bpy.data.meshes.new_from_object(ev)
    new.modifiers.clear()
    old = new.data
    new.data = me
    bpy.data.meshes.remove(old)
    return new


# ====================================================================== signs (3D text)
def text_mesh(text, size, depth=0.04):
    """Return (verts, faces) of a centred 3D text in Blender local space (XY plane, facing +Z)."""
    cu = bpy.data.curves.new("sign_txt", type="FONT")
    cu.body = text
    cu.size = size
    cu.extrude = depth
    cu.align_x = "CENTER"
    cu.align_y = "CENTER"
    cu.resolution_u = 2
    ob = bpy.data.objects.new("sign_txt", cu)
    bpy.context.scene.collection.objects.link(ob)
    dg = bpy.context.evaluated_depsgraph_get()
    ev = ob.evaluated_get(dg)
    me = bpy.data.meshes.new_from_object(ev)
    verts = [tuple(v.co) for v in me.vertices]
    faces = [list(p.vertices) for p in me.polygons]
    bpy.data.objects.remove(ob, do_unlink=True)
    bpy.data.curves.remove(cu)
    bpy.data.meshes.remove(me)
    return verts, faces


def add_signs(mb, signs):
    cache = {}
    for (text, pos, rot, size, color) in signs:
        key = (text, round(size, 2))
        if key not in cache:
            cache[key] = text_mesh(text, size)
        verts, faces = cache[key]
        o = (math.sin(rot), 0.0, math.cos(rot))
        r = (math.cos(rot), 0.0, -math.sin(rot))
        pv = [(pos[0] + r[0] * v[0] + o[0] * v[2], pos[1] + v[1], pos[2] + r[2] * v[0] + o[2] * v[2]) for v in verts]
        col = (color[0], color[1], color[2], 1.0)
        for f in faces:
            pts = [pv[k] for k in f]
            mb.face(pts, [(0, 0)] * len(pts), "sign_emissive", col)
