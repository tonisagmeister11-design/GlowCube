"""Higher-level procedural modelling primitives on top of MeshBuilder (Godot space:
X right, Y up, Z back / -Z forward).

  extrude(mb, prof, axis, a0, a1, ...)   2D profile (convex or concave) extruded along an
                                         axis with chamfered edges (crisp highlights)
  lathe(mb, prof, ...)                   surface of revolution from (axial, radius) pairs
  sweep_shape(mb, path, shape, ...)      2D cross-section swept along a 3D path (tubes, guards)
  rail(mb, ...)                          Picatinny rail with teeth
  rrect / circle / smooth_path           profile helpers

Vertex colour convention used by the weapon / vehicle shaders:
  R = edge-wear mask (1 on chamfers), G = ambient occlusion (1 = open), B = free
"""
import math

BASE = (0.0, 1.0, 1.0, 1.0)
EDGE = (1.0, 1.0, 1.0, 1.0)


def ao(g):
    """Base colour with ambient occlusion `g` (0 dark .. 1 open)."""
    return (0.0, g, 1.0, 1.0)


# ====================================================================== 2D helpers
def rrect(w, h, r, segs=3, cx=0.0, cy=0.0):
    """Rounded rectangle (width w, height h, corner radius r), CCW."""
    r = min(r, w * 0.5, h * 0.5)
    pts = []
    corners = [(w / 2 - r, h / 2 - r, 0.0), (-w / 2 + r, h / 2 - r, 90.0),
               (-w / 2 + r, -h / 2 + r, 180.0), (w / 2 - r, -h / 2 + r, 270.0)]
    for (x, y, a0) in corners:
        for i in range(segs + 1):
            a = math.radians(a0 + 90.0 * i / segs)
            pts.append((cx + x + math.cos(a) * r, cy + y + math.sin(a) * r))
    return pts


def circle(r, segs=24, cx=0.0, cy=0.0, a0=0.0):
    return [(cx + math.cos(a0 + math.tau * i / segs) * r, cy + math.sin(a0 + math.tau * i / segs) * r)
            for i in range(segs)]


def smooth_path(pts, iters=2):
    """Chaikin corner cutting (keeps end points)."""
    for _ in range(iters):
        out = [pts[0]]
        for a, b in zip(pts[:-1], pts[1:]):
            out.append(tuple(a[k] * 0.75 + b[k] * 0.25 for k in range(len(a))))
            out.append(tuple(a[k] * 0.25 + b[k] * 0.75 for k in range(len(a))))
        out.append(pts[-1])
        pts = out
    return pts


def smooth_loop(pts, iters=2):
    """Chaikin for closed polygons."""
    for _ in range(iters):
        out = []
        n = len(pts)
        for i in range(n):
            a, b = pts[i], pts[(i + 1) % n]
            out.append((a[0] * 0.75 + b[0] * 0.25, a[1] * 0.75 + b[1] * 0.25))
            out.append((a[0] * 0.25 + b[0] * 0.75, a[1] * 0.25 + b[1] * 0.75))
        pts = out
    return pts


def signed_area(pts):
    s = 0.0
    for i in range(len(pts)):
        x0, y0 = pts[i]
        x1, y1 = pts[(i + 1) % len(pts)]
        s += x0 * y1 - x1 * y0
    return s * 0.5


def offset_poly(pts, d):
    """Miter offset of a closed polygon: d > 0 shrinks (inset), works for mildly concave shapes."""
    ccw = signed_area(pts) > 0
    n = len(pts)
    out = []
    for i in range(n):
        p0, p1, p2 = pts[i - 1], pts[i], pts[(i + 1) % n]
        e0 = (p1[0] - p0[0], p1[1] - p0[1])
        e1 = (p2[0] - p1[0], p2[1] - p1[1])
        l0 = math.hypot(*e0) or 1.0
        l1 = math.hypot(*e1) or 1.0
        # inward normals
        if ccw:
            n0 = (-e0[1] / l0, e0[0] / l0)
            n1 = (-e1[1] / l1, e1[0] / l1)
        else:
            n0 = (e0[1] / l0, -e0[0] / l0)
            n1 = (e1[1] / l1, -e1[0] / l1)
        m = (n0[0] + n1[0], n0[1] + n1[1])
        ml = math.hypot(*m)
        if ml < 1e-6:
            m = n0
            ml = 1.0
        m = (m[0] / ml, m[1] / ml)
        cosh = max(m[0] * n0[0] + m[1] * n0[1], 0.35)
        out.append((p1[0] + m[0] * d / cosh, p1[1] + m[1] * d / cosh))
    return out


def _map(axis, u, v, a):
    """Profile coords (u, v) + axial coord a -> 3D point.
    axis 'x': profile (z, y);  axis 'z': profile (x, y);  axis 'y': profile (x, z)."""
    if axis == "x":
        return (a, v, u)
    if axis == "z":
        return (u, v, a)
    return (u, a, v)


def _axis_vec(axis, s=1.0):
    return {"x": (s, 0.0, 0.0), "y": (0.0, s, 0.0), "z": (0.0, 0.0, s)}[axis]


# ====================================================================== extrusion
def extrude(mb, prof, axis, a0, a1, mat, bevel=0.0, col=BASE, edge_col=EDGE, uv_scale=25.0, cap0=True, cap1=True,
            cap_mat=None, offset=(0.0, 0.0)):
    """Extrude closed 2D profile `prof` along `axis` from a0 to a1 (a0 < a1).
    With bevel > 0 both ends get a 45-degree chamfer ring (edge-wear coloured)."""
    ou, ov = offset
    prof = [(u + ou, v + ov) for (u, v) in prof]
    b = min(bevel, (a1 - a0) * 0.45)
    inner = offset_poly(prof, b) if b > 0 else prof
    ccw = signed_area(prof) > 0
    n = len(prof)
    s0, s1 = a0 + b, a1 - b

    def P(q, a):
        return _map(axis, q[0], q[1], a)

    # perimeter for UVs
    per = [0.0]
    for i in range(n):
        p, q = prof[i], prof[(i + 1) % n]
        per.append(per[-1] + math.hypot(q[0] - p[0], q[1] - p[1]))
    for i in range(n):
        p, q = prof[i], prof[(i + 1) % n]
        du, dv = q[0] - p[0], q[1] - p[1]
        ln = math.hypot(du, dv) or 1.0
        # outward normal of this edge in profile space
        nu, nv = (dv / ln, -du / ln) if ccw else (-dv / ln, du / ln)
        out3 = _map(axis, nu, nv, 0.0)
        u0, u1 = per[i] * uv_scale, per[i + 1] * uv_scale
        mb.face([P(p, s0), P(q, s0), P(q, s1), P(p, s1)],
                [(u0, s0 * uv_scale), (u1, s0 * uv_scale), (u1, s1 * uv_scale), (u0, s1 * uv_scale)],
                mat, col, up=out3)
        if b > 0:
            pi, qi = inner[i], inner[(i + 1) % n]
            ax = _axis_vec(axis)
            up1 = tuple(out3[k] + ax[k] for k in range(3))
            up0 = tuple(out3[k] - ax[k] for k in range(3))
            mb.face([P(p, s1), P(q, s1), P(qi, a1), P(pi, a1)], None, mat, edge_col, up=up1)
            mb.face([P(p, s0), P(q, s0), P(qi, a0), P(pi, a0)], None, mat, edge_col, up=up0)
    cm = cap_mat or mat
    if cap1:
        mb.face([P(q, a1) for q in inner], [(q[0] * uv_scale, q[1] * uv_scale) for q in inner], cm, col,
                up=_axis_vec(axis, 1.0))
    if cap0:
        mb.face([P(q, a0) for q in inner], [(q[0] * uv_scale, q[1] * uv_scale) for q in inner], cm, col,
                up=_axis_vec(axis, -1.0))


def slab(mb, prof_zy, x_half, mat, bevel=0.0, x0=0.0, col=BASE, **kw):
    """Side-profile part (profile in (z, y)) symmetric about x0 with half-width x_half."""
    extrude(mb, prof_zy, "x", x0 - x_half, x0 + x_half, mat, bevel, col, **kw)


def bbox(mb, center, size, mat, bevel=0.0, col=BASE, axis="z"):
    """Beveled box (chamfered along one axis' end faces and its side edges via profile)."""
    cx, cy, cz = center
    sx, sy, sz = size
    if axis == "z":
        prof = _chamfer_rect(sx, sy, bevel)
        extrude(mb, prof, "z", cz - sz / 2, cz + sz / 2, mat, bevel, col, offset=(cx, cy))
    elif axis == "x":
        prof = _chamfer_rect(sz, sy, bevel)
        extrude(mb, prof, "x", cx - sx / 2, cx + sx / 2, mat, bevel, col, offset=(cz, cy))
    else:
        prof = _chamfer_rect(sx, sz, bevel)
        extrude(mb, prof, "y", cy - sy / 2, cy + sy / 2, mat, bevel, col, offset=(cx, cz))


def _chamfer_rect(w, h, c):
    c = min(c, w * 0.3, h * 0.3)
    if c <= 0:
        return [(w / 2, h / 2), (-w / 2, h / 2), (-w / 2, -h / 2), (w / 2, -h / 2)]
    return [(w / 2 - c, h / 2), (-w / 2 + c, h / 2), (-w / 2, h / 2 - c), (-w / 2, -h / 2 + c),
            (-w / 2 + c, -h / 2), (w / 2 - c, -h / 2), (w / 2, -h / 2 + c), (w / 2, h / 2 - c)]


# ====================================================================== lathe
def lathe(mb, prof, mat, segs=24, center=(0.0, 0.0), axis="z", col=BASE, cap0=True, cap1=True, mats=None,
          cols=None, a_off=0.0):
    """Surface of revolution. prof: [(axial, radius), ...] ordered along the axis.
    center: position of the axis in the perpendicular plane ((x, y) for 'z', (z, y) for 'x', (x, z) for 'y').
    mats / cols: optional per-segment material / colour lists (len(prof) - 1)."""
    cu, cv = center
    if prof[-1][0] < prof[0][0]:
        # always build along the increasing axis so normals point outwards
        prof = list(reversed(prof))
        cap0, cap1 = cap1, cap0
        if mats:
            mats = list(reversed(mats))
        if cols:
            cols = list(reversed(cols))

    def P(a, r, t):
        ang = math.tau * t / segs + a_off
        u, v = cu + math.cos(ang) * r, cv + math.sin(ang) * r
        return _map(axis, u, v, a)

    ax = _axis_vec(axis)
    for i in range(len(prof) - 1):
        (a_0, r_0), (a_1, r_1) = prof[i], prof[i + 1]
        m = mats[i] if mats else mat
        c = cols[i] if cols else col
        for t in range(segs):
            p00, p01 = P(a_0, r_0, t), P(a_0, r_0, t + 1)
            p10, p11 = P(a_1, r_1, t), P(a_1, r_1, t + 1)
            ang = math.tau * (t + 0.5) / segs + a_off
            rad = _map(axis, math.cos(ang), math.sin(ang), 0.0)
            # slope of the profile tilts the outward normal
            da, dr = a_1 - a_0, r_1 - r_0
            ln = math.hypot(da, dr) or 1.0
            nr, na = da / ln, -dr / ln
            up = tuple(rad[k] * nr + ax[k] * na for k in range(3))
            if abs(nr) < 1e-4 and abs(na) < 1e-4:
                continue
            uvs = [(t / segs, a_0 * 10), (t / segs, a_1 * 10), ((t + 1) / segs, a_1 * 10), ((t + 1) / segs, a_0 * 10)]
            quad = [p00, p10, p11, p01]
            if r_0 < 1e-6:
                mb.face([p00, p10, p11], None, m, c, up=up)
            elif r_1 < 1e-6:
                mb.face([p00, p10, p01], None, m, c, up=up)
            else:
                mb.face(quad, uvs, m, c, up=up)
    a_s, r_s = prof[0]
    a_e, r_e = prof[-1]
    if cap0 and r_s > 1e-6:
        mb.face([P(a_s, r_s, t) for t in range(segs)], None, mats[0] if mats else mat, cols[0] if cols else col,
                up=_axis_vec(axis, -1.0 if a_e > a_s else 1.0))
    if cap1 and r_e > 1e-6:
        mb.face([P(a_e, r_e, t) for t in range(segs)], None, mats[-1] if mats else mat, cols[-1] if cols else col,
                up=_axis_vec(axis, 1.0 if a_e > a_s else -1.0))


def disc(mb, center3, r, mat, axis="z", facing=1.0, segs=20, col=BASE):
    cx, cy, cz = center3
    pts = []
    for i in range(segs):
        a = math.tau * i / segs
        c, s = math.cos(a) * r, math.sin(a) * r
        if axis == "z":
            pts.append((cx + c, cy + s, cz))
        elif axis == "x":
            pts.append((cx, cy + s, cz + c))
        else:
            pts.append((cx + c, cy, cz + s))
    mb.face(pts, None, mat, col, up=_axis_vec(axis, facing))


# ====================================================================== sweeps
def _norm(v):
    ln = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / ln for c in v)


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def sweep_shape(mb, path, shape, mat, col=BASE, side_ref=(1.0, 0.0, 0.0), caps=True, scales=None):
    """Sweep closed 2D `shape` [(s, t)] along 3D `path`. s runs along side_ref, t along the
    binormal. scales: optional per-point scale factors."""
    rings = []
    n = len(path)
    for i in range(n):
        p = path[i]
        a = path[max(i - 1, 0)]
        b = path[min(i + 1, n - 1)]
        tgt = _norm((b[0] - a[0], b[1] - a[1], b[2] - a[2]))
        d = sum(side_ref[k] * tgt[k] for k in range(3))
        side = _norm(tuple(side_ref[k] - tgt[k] * d for k in range(3)))
        up = _cross(tgt, side)
        sc = scales[i] if scales else 1.0
        rings.append([tuple(p[k] + (side[k] * s + up[k] * t) * sc for k in range(3)) for (s, t) in shape])
    m = len(shape)
    for i in range(n - 1):
        ra, rb = rings[i], rings[i + 1]
        for j in range(m):
            q = [ra[j], rb[j], rb[(j + 1) % m], ra[(j + 1) % m]]
            ca = tuple(sum(v[k] for v in ra) / m for k in range(3))
            mid = tuple((q[0][k] + q[3][k]) * 0.5 for k in range(3))
            out = _norm(tuple(mid[k] - ca[k] for k in range(3)))
            mb.face(q, None, mat, col, up=out)
    if caps:
        t0 = _norm(tuple(path[0][k] - path[1][k] for k in range(3)))
        t1 = _norm(tuple(path[-1][k] - path[-2][k] for k in range(3)))
        mb.face(rings[0], None, mat, col, up=t0)
        mb.face(rings[-1], None, mat, col, up=t1)


def tube(mb, path, r, mat, segs=10, col=BASE, side_ref=(1.0, 0.0, 0.0), caps=True):
    sweep_shape(mb, path, circle(r, segs), mat, col, side_ref, caps)


def arc_path(center, r, a0, a1, n, plane="zy", fixed=0.0):
    """Points on an arc; plane 'zy' -> (x=fixed, y, z)."""
    out = []
    for i in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * i / n)
        u, v = center[0] + math.cos(a) * r, center[1] + math.sin(a) * r
        if plane == "zy":
            out.append((fixed, v, u))
        else:
            out.append((u, v, fixed))
    return out


# ====================================================================== accessories
def rail(mb, z0, z1, y, mat, width=0.021, base_h=0.004, tooth_h=0.0032, x=0.0, col=BASE, pitch=0.01):
    """Picatinny rail along -Z from z1 (rear) to z0 (front) sitting on height y."""
    bbox(mb, (x, y + base_h / 2, (z0 + z1) / 2), (width * 0.82, base_h, z1 - z0), mat, 0.0008, col)
    zz = z0 + pitch * 0.25
    while zz + pitch * 0.5 < z1:
        bbox(mb, (x, y + base_h + tooth_h / 2, zz + pitch * 0.26), (width, tooth_h, pitch * 0.52), mat, 0.0007, EDGE)
        zz += pitch


def mlok_slots(mb, z0, z1, y, x, depth_axis, mat, n, length=0.032, height=0.007, gap=0.012, col=ao(0.25)):
    """Dark rounded slots (visual cut-outs) on a handguard face."""
    z = z0
    for _ in range(n):
        if z + length > z1:
            break
        prof = rrect(length, height, height * 0.5, 3)
        if depth_axis == "x":
            extrude(mb, prof, "x", x - 0.0004, x + 0.0004, mat, 0.0, col, offset=(z + length / 2, y))
        else:
            extrude(mb, [(p[1], p[0]) for p in prof], "y", y - 0.0004, y + 0.0004, mat, 0.0, col,
                    offset=(x, z + length / 2))
        z += length + gap


def screw(mb, center3, r, mat, axis="x", facing=1.0, col=EDGE):
    cx, cy, cz = center3
    a = {"x": cx, "y": cy, "z": cz}[axis]
    c = {"x": (cz, cy), "y": (cx, cz), "z": (cx, cy)}[axis]
    lathe(mb, [(a, r), (a + 0.0008 * facing, r * 0.92), (a + 0.0012 * facing, 0.0)] if facing > 0 else
          [(a - 0.0012, 0.0), (a - 0.0008, r * 0.92), (a, r)], mat, 10, c, axis, col, cap0=False, cap1=False)
