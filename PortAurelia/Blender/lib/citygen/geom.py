"""2D/3D geometry helpers used by the city generator.

All 2D points are tuples (x, z) in Godot plan space.
"""
import math

EPS = 1e-9


# ---------------------------------------------------------------- vectors
def add(a, b):
    return (a[0] + b[0], a[1] + b[1])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1])


def mul(a, s):
    return (a[0] * s, a[1] * s)


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1]


def cross(a, b):
    return a[0] * b[1] - a[1] * b[0]


def length(a):
    return math.hypot(a[0], a[1])


def dist(a, b):
    return math.hypot(a[0] - b[0], a[1] - b[1])


def norm(a):
    l = length(a)
    if l < EPS:
        return (0.0, 0.0)
    return (a[0] / l, a[1] / l)


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def right(d):
    """Right-hand perpendicular in plan space (X east, Z south, Y up)."""
    return (-d[1], d[0])


def left(d):
    return (d[1], -d[0])


def angle(d):
    return math.atan2(d[1], d[0])


def rot(v, a):
    c, s = math.cos(a), math.sin(a)
    return (v[0] * c - v[1] * s, v[0] * s + v[1] * c)


def g2b(x, y, z):
    """Godot (x, y-up, z-south) -> Blender (x, y-north, z-up)."""
    return (x, -z, y)


def clamp(v, lo, hi):
    return lo if v < lo else hi if v > hi else v


def smoothstep(e0, e1, x):
    if e0 == e1:
        return 0.0 if x < e0 else 1.0
    t = clamp((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


# ---------------------------------------------------------------- lines
def line_intersect(p, d, q, e):
    """Intersect lines p + t*d and q + s*e. Returns (t, s) or None if parallel."""
    den = cross(d, e)
    if abs(den) < 1e-7:
        return None
    w = sub(q, p)
    t = cross(w, e) / den
    s = cross(w, d) / den
    return t, s


def seg_intersect(a, b, c, d):
    """Proper segment intersection. Returns (point, ta, tc) or None."""
    r = sub(b, a)
    s = sub(d, c)
    den = cross(r, s)
    if abs(den) < 1e-9:
        return None
    w = sub(c, a)
    t = cross(w, s) / den
    u = cross(w, r) / den
    if -1e-9 <= t <= 1 + 1e-9 and -1e-9 <= u <= 1 + 1e-9:
        return add(a, mul(r, t)), t, u
    return None


def point_seg_dist(p, a, b):
    ab = sub(b, a)
    l2 = dot(ab, ab)
    if l2 < EPS:
        return dist(p, a), 0.0
    t = clamp(dot(sub(p, a), ab) / l2, 0.0, 1.0)
    return dist(p, add(a, mul(ab, t))), t


# ---------------------------------------------------------------- polylines
def polyline_length(pts):
    return sum(dist(pts[i], pts[i + 1]) for i in range(len(pts) - 1))


def polyline_point_at(pts, s):
    """Point and unit direction at arc length s."""
    if s <= 0:
        return pts[0], norm(sub(pts[1], pts[0]))
    acc = 0.0
    for i in range(len(pts) - 1):
        l = dist(pts[i], pts[i + 1])
        if acc + l >= s and l > EPS:
            t = (s - acc) / l
            return lerp(pts[i], pts[i + 1], t), norm(sub(pts[i + 1], pts[i]))
        acc += l
    return pts[-1], norm(sub(pts[-1], pts[-2]))


def polyline_trim(pts, t0, t1):
    """Cut `t0` metres from the start and `t1` from the end of a polyline."""
    total = polyline_length(pts)
    if t0 + t1 >= total - 0.05:
        mid, _ = polyline_point_at(pts, total * 0.5)
        return [mid, mid]
    out = []
    p0, _ = polyline_point_at(pts, t0)
    out.append(p0)
    acc = 0.0
    for i in range(len(pts) - 1):
        l = dist(pts[i], pts[i + 1])
        acc_next = acc + l
        if t0 < acc_next and acc_next < total - t1 and i + 1 < len(pts) - 1:
            out.append(pts[i + 1])
        acc = acc_next
    p1, _ = polyline_point_at(pts, total - t1)
    out.append(p1)
    return out


def polyline_resample(pts, step):
    total = polyline_length(pts)
    n = max(1, int(math.ceil(total / step)))
    return [polyline_point_at(pts, total * i / n)[0] for i in range(n + 1)]


def polyline_offset(pts, off):
    """Offset polyline to the right by `off` (negative = left) with miter joins."""
    n = len(pts)
    if n < 2:
        return list(pts)
    out = []
    for i in range(n):
        if i == 0:
            d = norm(sub(pts[1], pts[0]))
            out.append(add(pts[0], mul(right(d), off)))
        elif i == n - 1:
            d = norm(sub(pts[-1], pts[-2]))
            out.append(add(pts[-1], mul(right(d), off)))
        else:
            d0 = norm(sub(pts[i], pts[i - 1]))
            d1 = norm(sub(pts[i + 1], pts[i]))
            m = norm(add(right(d0), right(d1)))
            c = dot(m, right(d0))
            if c < 0.3:
                c = 0.3
            out.append(add(pts[i], mul(m, off / c)))
    return out


def polyline_tangents(pts):
    n = len(pts)
    tans = []
    for i in range(n):
        if i == 0:
            d = sub(pts[1], pts[0])
        elif i == n - 1:
            d = sub(pts[-1], pts[-2])
        else:
            d = add(norm(sub(pts[i], pts[i - 1])), norm(sub(pts[i + 1], pts[i])))
        tans.append(norm(d))
    return tans


def bezier2(a, c, b, n):
    out = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        out.append((u * u * a[0] + 2 * u * t * c[0] + t * t * b[0],
                    u * u * a[1] + 2 * u * t * c[1] + t * t * b[1]))
    return out


def bezier3(a, c1, c2, b, n):
    out = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        out.append((u ** 3 * a[0] + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t ** 3 * b[0],
                    u ** 3 * a[1] + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t ** 3 * b[1]))
    return out


def arc(center, radius, a0, a1, n):
    return [(center[0] + math.cos(a0 + (a1 - a0) * i / n) * radius,
             center[1] + math.sin(a0 + (a1 - a0) * i / n) * radius) for i in range(n + 1)]


# ---------------------------------------------------------------- polygons
def poly_area(poly):
    """Signed area (shoelace)."""
    a = 0.0
    n = len(poly)
    for i in range(n):
        x0, z0 = poly[i]
        x1, z1 = poly[(i + 1) % n]
        a += x0 * z1 - x1 * z0
    return a * 0.5


def poly_centroid(poly):
    a = poly_area(poly)
    if abs(a) < EPS:
        xs = [p[0] for p in poly]
        zs = [p[1] for p in poly]
        return (sum(xs) / len(xs), sum(zs) / len(zs))
    cx = cz = 0.0
    n = len(poly)
    for i in range(n):
        x0, z0 = poly[i]
        x1, z1 = poly[(i + 1) % n]
        f = x0 * z1 - x1 * z0
        cx += (x0 + x1) * f
        cz += (z0 + z1) * f
    return (cx / (6 * a), cz / (6 * a))


def poly_bbox(poly):
    xs = [p[0] for p in poly]
    zs = [p[1] for p in poly]
    return min(xs), min(zs), max(xs), max(zs)


def point_in_poly(p, poly):
    x, z = p
    inside = False
    n = len(poly)
    j = n - 1
    for i in range(n):
        xi, zi = poly[i]
        xj, zj = poly[j]
        if (zi > z) != (zj > z):
            xc = xi + (z - zi) * (xj - xi) / (zj - zi)
            if x < xc:
                inside = not inside
        j = i
    return inside


def poly_dedupe(poly, tol=0.05):
    out = []
    for p in poly:
        if not out or dist(out[-1], p) > tol:
            out.append(p)
    if len(out) > 1 and dist(out[0], out[-1]) <= tol:
        out.pop()
    return out


def poly_simplify_collinear(poly, tol=0.02):
    out = list(poly)
    changed = True
    while changed and len(out) > 3:
        changed = False
        for i in range(len(out)):
            a = out[i - 1]
            b = out[i]
            c = out[(i + 1) % len(out)]
            if abs(cross(sub(b, a), sub(c, b))) < tol * (dist(a, b) + dist(b, c)):
                out.pop(i)
                changed = True
                break
    return out


def ensure_ccw(poly):
    """Make winding positive in our shoelace convention."""
    return list(poly) if poly_area(poly) > 0 else list(reversed(poly))


def poly_is_convex(poly):
    n = len(poly)
    sign = 0
    for i in range(n):
        c = cross(sub(poly[(i + 1) % n], poly[i]), sub(poly[(i + 2) % n], poly[(i + 1) % n]))
        if abs(c) < 1e-6:
            continue
        s = 1 if c > 0 else -1
        if sign == 0:
            sign = s
        elif s != sign:
            return False
    return True


def poly_inset(poly, d):
    """Inset a polygon by distance d.

    Convex polygons are inset exactly by clipping with every edge's inward-shifted
    half-plane (never produces spikes). Non-convex input falls back to a miter
    offset with a sanity check."""
    poly = ensure_ccw(poly_dedupe(poly, 0.02))
    if len(poly) < 3:
        return []
    if poly_is_convex(poly):
        out = list(poly)
        n = len(poly)
        for i in range(n):
            a = poly[i]
            b = poly[(i + 1) % n]
            e = norm(sub(b, a))
            if length(e) < 0.5:
                continue
            inward = right(e)
            out = clip_halfplane(out, add(a, mul(inward, d)), inward)
            if len(out) < 3:
                return []
        out = poly_dedupe(out, 0.02)
        if len(out) < 3 or poly_area(out) < 1.0:
            return []
        return out
    return _poly_inset_miter(poly, d)


def _poly_inset_miter(poly, d):
    orig_area = poly_area(poly)
    n = len(poly)
    lines = []
    for i in range(n):
        a = poly[i]
        b = poly[(i + 1) % n]
        e = norm(sub(b, a))
        # positive area winding => interior is on the right side of each edge in our convention
        inward = right(e)
        lines.append((add(a, mul(inward, d)), e))
    out = []
    for i in range(n):
        p0, d0 = lines[i - 1]
        p1, d1 = lines[i]
        r = line_intersect(p0, d0, p1, d1)
        if r is None:
            out.append(p1)
        else:
            out.append(add(p0, mul(d0, r[0])))
    a = poly_area(out)
    if a <= 0 or a > orig_area:
        return []
    return out


def clip_halfplane(poly, p, n):
    """Keep the part of polygon where dot(x - p, n) >= 0 (Sutherland-Hodgman)."""
    out = []
    m = len(poly)
    for i in range(m):
        a = poly[i]
        b = poly[(i + 1) % m]
        da = dot(sub(a, p), n)
        db = dot(sub(b, p), n)
        if da >= 0:
            out.append(a)
        if (da >= 0) != (db >= 0):
            t = da / (da - db)
            out.append(lerp(a, b, t))
    return out


def poly_split(poly, p, d):
    """Split a convex polygon by the line p + t*d. Returns (right_part, left_part)."""
    n = right(d)
    a = clip_halfplane(poly, p, n)
    b = clip_halfplane(poly, p, mul(n, -1))
    return a, b


def obb(poly):
    """Minimum-area oriented bounding box via edge directions.
    Returns (center, axis_u, axis_v, half_u, half_v)."""
    best = None
    n = len(poly)
    for i in range(n):
        e = norm(sub(poly[(i + 1) % n], poly[i]))
        if length(e) < 0.5:
            continue
        v = right(e)
        us = [dot(p, e) for p in poly]
        vs = [dot(p, v) for p in poly]
        area = (max(us) - min(us)) * (max(vs) - min(vs))
        if best is None or area < best[0]:
            cu = (max(us) + min(us)) * 0.5
            cv = (max(vs) + min(vs)) * 0.5
            center = add(mul(e, cu), mul(v, cv))
            best = (area, center, e, v, (max(us) - min(us)) * 0.5, (max(vs) - min(vs)) * 0.5)
    if best is None:
        c = poly_centroid(poly)
        return c, (1.0, 0.0), (0.0, 1.0), 1.0, 1.0
    return best[1], best[2], best[3], best[4], best[5]


def triangulate(poly):
    """Ear clipping triangulation of a simple polygon. Returns index triples."""
    pts = list(poly)
    n = len(pts)
    if n < 3:
        return []
    idx = list(range(n))
    if poly_area(pts) < 0:
        idx.reverse()
    tris = []
    guard = 0
    while len(idx) > 3 and guard < 10000:
        guard += 1
        ear_found = False
        m = len(idx)
        for k in range(m):
            i0, i1, i2 = idx[k - 1], idx[k], idx[(k + 1) % m]
            a, b, c = pts[i0], pts[i1], pts[i2]
            if cross(sub(b, a), sub(c, b)) <= 1e-10:
                continue
            ok = True
            for j in idx:
                if j in (i0, i1, i2):
                    continue
                if _pt_in_tri(pts[j], a, b, c):
                    ok = False
                    break
            if ok:
                tris.append((i0, i1, i2))
                idx.pop(k)
                ear_found = True
                break
        if not ear_found:
            # degenerate polygon: fan the remainder
            for k in range(1, len(idx) - 1):
                tris.append((idx[0], idx[k], idx[k + 1]))
            return tris
    if len(idx) == 3:
        tris.append((idx[0], idx[1], idx[2]))
    return tris


def _pt_in_tri(p, a, b, c):
    d1 = cross(sub(b, a), sub(p, a))
    d2 = cross(sub(c, b), sub(p, b))
    d3 = cross(sub(a, c), sub(p, c))
    return d1 >= -1e-10 and d2 >= -1e-10 and d3 >= -1e-10


def rect_poly(center, u, v, hu, hv):
    c = center
    return [
        add(add(c, mul(u, -hu)), mul(v, -hv)),
        add(add(c, mul(u, hu)), mul(v, -hv)),
        add(add(c, mul(u, hu)), mul(v, hv)),
        add(add(c, mul(u, -hu)), mul(v, hv)),
    ]


def polys_overlap_aabb(a, b, margin=0.0):
    ax0, az0, ax1, az1 = poly_bbox(a)
    bx0, bz0, bx1, bz1 = poly_bbox(b)
    return not (ax1 + margin < bx0 or bx1 + margin < ax0 or az1 + margin < bz0 or bz1 + margin < az0)


def convex_overlap(a, b):
    """SAT test for two convex polygons."""
    for poly in (a, b):
        n = len(poly)
        for i in range(n):
            e = sub(poly[(i + 1) % n], poly[i])
            ax = right(norm(e))
            pa = [dot(p, ax) for p in a]
            pb = [dot(p, ax) for p in b]
            if max(pa) < min(pb) or max(pb) < min(pa):
                return False
    return True


# ---------------------------------------------------------------- random
class Rng:
    """Small deterministic RNG (xorshift) independent of Python's `random` version."""

    def __init__(self, seed):
        self.s = (seed * 2654435761 + 0x9E3779B9) & 0xFFFFFFFF or 1

    def next_u32(self):
        x = self.s
        x ^= (x << 13) & 0xFFFFFFFF
        x ^= x >> 17
        x ^= (x << 5) & 0xFFFFFFFF
        self.s = x & 0xFFFFFFFF
        return self.s

    def random(self):
        return self.next_u32() / 4294967296.0

    def uniform(self, a, b):
        return a + (b - a) * self.random()

    def randint(self, a, b):
        return a + int(self.random() * (b - a + 1)) if b >= a else a

    def choice(self, seq):
        return seq[int(self.random() * len(seq)) % len(seq)]

    def chance(self, p):
        return self.random() < p

    def weighted(self, items):
        """items: list of (value, weight)."""
        total = sum(w for _, w in items)
        r = self.random() * total
        for v, w in items:
            r -= w
            if r <= 0:
                return v
        return items[-1][0]

    def shuffle(self, seq):
        for i in range(len(seq) - 1, 0, -1):
            j = int(self.random() * (i + 1))
            seq[i], seq[j] = seq[j], seq[i]


def hash2(x, z, seed=0):
    h = (int(x * 73856093) ^ int(z * 19349663) ^ (seed * 83492791)) & 0xFFFFFFFF
    h = ((h ^ (h >> 16)) * 0x45D9F3B) & 0xFFFFFFFF
    h = ((h ^ (h >> 16)) * 0x45D9F3B) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFFFF) / 4294967296.0


# ---------------------------------------------------------------- noise
def _fade(t):
    return t * t * t * (t * (t * 6 - 15) + 10)


def value_noise(x, z, seed=0):
    xi, zi = math.floor(x), math.floor(z)
    xf, zf = x - xi, z - zi
    a = hash2(xi, zi, seed)
    b = hash2(xi + 1, zi, seed)
    c = hash2(xi, zi + 1, seed)
    d = hash2(xi + 1, zi + 1, seed)
    u, v = _fade(xf), _fade(zf)
    return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v


def fbm(x, z, octaves=4, seed=0):
    amp = 0.5
    f = 1.0
    s = 0.0
    norm_ = 0.0
    for o in range(octaves):
        s += value_noise(x * f, z * f, seed + o * 17) * amp
        norm_ += amp
        amp *= 0.5
        f *= 2.03
    return s / norm_
