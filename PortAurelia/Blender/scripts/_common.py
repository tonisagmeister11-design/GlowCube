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
if HERE not in sys.path:
    sys.path.insert(0, HERE)

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
from setup_materials import MATERIAL_PREVIEW, FACADES, get_material  # noqa: E402,F401
from setup_uvs import gltf_to_blender_v  # noqa: E402


# ------------------------------------------------------------------ mesh builder
FACADE_FLOOR_H = 3.2


def _facade_uvs(pts):
    """Facade convention for faces built without explicit facade UVs:
    UV = (metres along the wall, metres above the face base), UV2 = (wall length, floor height)."""
    n = newell(pts)
    ln = math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]) or 1.0
    y0 = min(p[1] for p in pts)
    if abs(n[1] / ln) < 0.7:
        tx, tz = -n[2], n[0]
        tl = math.sqrt(tx * tx + tz * tz) or 1.0
        tx, tz = tx / tl, tz / tl
        us = [(p[0] - pts[0][0]) * tx + (p[2] - pts[0][2]) * tz for p in pts]
        u0 = min(us)
        uvs = [(u - u0, p[1] - y0) for u, p in zip(us, pts)]
        return uvs, (max(max(us) - u0, 0.5), FACADE_FLOOR_H)
    xs = [p[0] for p in pts]
    return [(p[0] - min(xs), p[2]) for p in pts], (max(max(xs) - min(xs), 0.5), FACADE_FLOOR_H)


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
        if uv2 is None and mat.startswith("facade_"):
            uvs, uv2 = _facade_uvs(pts)
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

    def to_object(self, name, collection=None, smooth=False, merge_dist=0.0005, auto_smooth=None, origin=None):
        """origin: optional Godot-space point that becomes the object's pivot."""
        me = bpy.data.meshes.new(name)
        ox, oy, oz = origin if origin is not None else (0.0, 0.0, 0.0)
        bverts = [(v[0] - ox, -(v[2] - oz), v[1] - oy) for v in self.verts]
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
                flat_uv.append(gltf_to_blender_v(u[1]))
            if has_uv2:
                u2 = self.uv2[fi] or (0.0, 0.0)
                for _ in f:
                    flat_uv2.append(u2[0])
                    flat_uv2.append(gltf_to_blender_v(u2[1]))
            for c in self.cols[fi]:
                flat_col.extend((srgb_to_linear(c[0]), srgb_to_linear(c[1]), srgb_to_linear(c[2]), c[3]))
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
        if origin is not None:
            obj.location = (ox, -oz, oy)
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
            # zero-area slivers (collinear cap triangles) get random normals and render as black specks
            bm.normal_update()
            slivers = [f for f in bm.faces if f.calc_area() < 1e-11]
            if slivers:
                bmesh.ops.delete(bm, geom=slivers, context="FACES_ONLY")
            bm.to_mesh(me)
            bm.free()
        me.polygons.foreach_set("use_smooth", [smooth] * len(me.polygons))
        if auto_smooth is not None:
            _set_auto_smooth(obj, auto_smooth)
        return obj


def srgb_to_linear(c):
    """Vertex colours are authored in sRGB; glTF stores linear values."""
    if c <= 0.04045:
        return c / 12.92
    return ((c + 0.055) / 1.055) ** 2.4


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
from export_glb import export_glb  # noqa: E402,F401
from generate_lods import lod_decimate  # noqa: E402,F401


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
