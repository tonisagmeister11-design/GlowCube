"""High-resolution car bodies for generate_vehicles.py (Godot space: X right, Y up, front at -Z).

CarBody lofts ~110 cross sections of an 18-point ring per side:
  underbody, wheel-well liner, sill, bulging door side, character crease, shoulder,
  beltline, window seal, tumblehome glass, drip rail, rounded roof edge, crowned roof;
  in front of / behind the cabin the greenhouse points blend into a crowned hood / deck.
Wheel arches get flared lips, the nose and tail are rounded in plan and profile.

Panel gaps are drawn by car_paint.gdshader from UV: UV.x = alternating signed distance to
the nearest transverse shut line (doors, hood/bumper, trunk), UV.y = the same for the
longitudinal hood/trunk seams; UV2.x = 1 enables the gaps.

decal() projects convex outlines onto the body surface (clipped per face, offset along the
normal) - used for lamps, grilles, intakes, handles and plates so they sit flush on any shape.
"""
import math

from _common import MeshBuilder

UNDER, WELL = 0, 1
N_RING = 18


def smoothstep(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3.0 - 2.0 * t)


def lerp(a, b, t):
    return a + (b - a) * t


def _sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _norm(v):
    ln = math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) or 1.0
    return (v[0] / ln, v[1] / ln, v[2] / ln)


def newell(pts):
    nx = ny = nz = 0.0
    for i in range(len(pts)):
        a, b = pts[i], pts[(i + 1) % len(pts)]
        nx += (a[1] - b[1]) * (a[2] + b[2])
        ny += (a[2] - b[2]) * (a[0] + b[0])
        nz += (a[0] - b[0]) * (a[1] + b[1])
    return _norm((nx, ny, nz))


def clip_convex(subject, clip):
    """Sutherland-Hodgman: 2D polygon `subject` clipped by convex CCW polygon `clip`."""
    out = list(subject)
    for i in range(len(clip)):
        a, b = clip[i], clip[(i + 1) % len(clip)]
        inp = out
        out = []
        if not inp:
            break

        def inside(p):
            return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]) >= 0.0

        def inter(p, q):
            x1, y1, x2, y2 = p[0], p[1], q[0], q[1]
            x3, y3, x4, y4 = a[0], a[1], b[0], b[1]
            den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
            if abs(den) < 1e-12:
                return q
            t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den
            return (x1 + t * (x2 - x1), y1 + t * (y2 - y1))

        s = inp[-1]
        for e in inp:
            if inside(e):
                if not inside(s):
                    out.append(inter(s, e))
                out.append(e)
            elif inside(s):
                out.append(inter(s, e))
            s = e
    return out


def ccw(poly):
    a = 0.0
    for i in range(len(poly)):
        p, q = poly[i], poly[(i + 1) % len(poly)]
        a += p[0] * q[1] - q[0] * p[1]
    return poly if a > 0 else list(reversed(poly))


def rrect2(cx, cy, w, h, r, segs=3):
    r = min(r, w * 0.5, h * 0.5)
    pts = []
    for (x, y, a0) in ((w / 2 - r, h / 2 - r, 0.0), (-w / 2 + r, h / 2 - r, 90.0), (-w / 2 + r, -h / 2 + r, 180.0),
                       (w / 2 - r, -h / 2 + r, 270.0)):
        for i in range(segs + 1):
            a = math.radians(a0 + 90.0 * i / segs)
            pts.append((cx + x + math.cos(a) * r, cy + y + math.sin(a) * r))
    return pts


def circ2(cx, cy, r, segs=16):
    return [(cx + math.cos(math.tau * i / segs) * r, cy + math.sin(math.tau * i / segs) * r) for i in range(segs)]


class CarBody:
    def __init__(self, p, top_at, livery, paint_mat="car_paint"):
        self.p = p
        self.top_at = top_at
        self.livery = livery
        self.paint_mat = paint_mat
        st = p["style"]
        self.st = st
        self.boxy = st in ("van", "truck", "bus")
        self.four_door = st in ("sedan", "hatch", "suv", "pickup")
        L = p["L"]
        self.L = L
        self.zf = -L * 0.5
        self.wheel_zs = [-p["WB"] * 0.5 + p.get("axle_shift", 0.0), p["WB"] * 0.5 + p.get("axle_shift", 0.0)]
        self.arch_r = p["R"] * 1.2
        self.tum = {"van": 0.96, "truck": 0.97, "bus": 0.985, "suv": 0.86, "pickup": 0.86}.get(st, 0.8)
        self.crown = 0.02 if self.boxy else 0.032
        self.b_pillar = (p["rs"] + p["re"]) * 0.5
        # side glass extent (normalised length)
        ws, rs, re, rw = p["ws"], p["rs"], p["re"], p["rw"]
        self.glass_f0 = ws + (rs - ws) * (0.5 if not self.boxy else 0.3)
        if st in ("sedan", "coupe", "super"):
            self.glass_f1 = re + (rw - re) * 0.38
        elif st == "pickup":
            self.glass_f1 = re + (rw - re) * 0.6
        else:
            self.glass_f1 = rw - 0.02
        if st == "bus":
            self.glass_f0, self.glass_f1 = 0.085, 0.97
        if st == "truck":
            self.glass_f1 = rw - 0.005
        # transverse shut lines (normalised length) and their active part of the ring
        self.seams_t = []
        fd = ws + (rs - ws) * 0.55
        if st == "bus":
            fd = 0.04
        self.seams_t.append((0.075 if not self.boxy else 0.03, 3, 17, 0.0, ws))       # bumper / hood front
        if not self.boxy or st == "van":
            self.seams_t.append((fd, 3, 10, 0.0, 1.0))                                 # front door front
            if self.four_door:
                self.seams_t.append((self.b_pillar, 3, 10, 0.0, 1.0))
                self.seams_t.append((re + (rw - re) * 0.22, 3, 10, 0.0, 1.0))
            else:
                self.seams_t.append((re + (rw - re) * (0.08 if st != "van" else 0.0) if st != "van" else rs + 0.1,
                                     3, 10, 0.0, 1.0))
        if st in ("sedan", "coupe", "super"):
            self.seams_t.append((rw + 0.012, 11, 17, rw, 1.0))                         # trunk lid front
        self.seams_t.append((0.93 if not self.boxy else 0.975, 3, 17, rw, 1.0))       # trunk / bumper rear
        self.seams_t.sort()
        self.sections = []
        self.faces = []          # (pts3d, normal) of the outer paint surface, for decals
        self._build_sections()

    # ------------------------------------------------------------------ geometry
    def z_of(self, f):
        return self.zf + self.L * f

    def ring(self, f):
        p = self.p
        st = self.st
        L, W, gc = self.L, p["W"], p["gc"]
        boxy = self.boxy
        if boxy:
            hw = W * 0.5 * (1 - 0.05 * (1 - min(1, f / 0.04)) ** 2) * (1 - 0.03 * (1 - min(1, (1 - f) / 0.03)) ** 2)
        else:
            hw = W * 0.5 * (1 - 0.15 * (1 - min(1, f / 0.13)) ** 2.2) * (1 - 0.09 * (1 - min(1, (1 - f) / 0.1)) ** 2.2)
        top = self.top_at(p, f)
        belt = p["hood"] + (0.05 * f if not boxy else 0.0)
        if st == "pickup":
            belt = p["hood"] + 0.02 * f
        if not boxy:
            # fenders / hood slope down towards the nose, slight drop at the tail
            belt -= 0.13 * (1 - min(1.0, f / 0.16)) ** 1.6 + 0.04 * (1 - min(1.0, (1 - f) / 0.08)) ** 2
        belt = min(belt, top)
        rn = (0.2 if not boxy else 0.08) / L
        rt = (0.16 if not boxy else 0.06) / L
        k = 0.0
        if f < rn:
            t = f / rn
            k = 1 - math.sqrt(max(0.0, 1 - (1 - t) ** 2))
        elif f > 1 - rt:
            t = (1 - f) / rt
            k = 1 - math.sqrt(max(0.0, 1 - (1 - t) ** 2))
        hw_r = hw * (1 - 0.14 * k)
        z = self.z_of(f)
        sill = gc + 0.07 + 0.09 * k
        bottom = sill
        fl = 0.0
        ar = self.arch_r
        for wz in self.wheel_zs:
            dz = abs(z - wz)
            if dz < ar:
                bottom = max(bottom, p["R"] + math.sqrt(ar * ar - dz * dz) * 0.9)
            fl = max(fl, 0.012 * smoothstep(ar * 1.3, ar * 0.75, dz))
        if boxy:
            fl *= 0.5
        belt_e = max(belt - 0.05 * k, bottom + 0.085)
        top_r = max(top - 0.09 * k, belt_e)
        b2 = bottom
        b3 = max(sill + 0.06, bottom + 0.025)
        b3 = min(b3, belt_e - 0.17)
        b2 = min(b2, b3 - 0.012)
        y4 = b3 + (belt_e - b3) * 0.32
        y5 = b3 + (belt_e - b3) * 0.6
        y6 = max(belt_e - 0.12 * (1 - 0.5 * k), y5 + 0.01)
        y7 = max(belt_e - 0.10 * (1 - 0.5 * k), y6 + 0.008)
        y8 = max(belt_e - 0.04, y7 + 0.008)
        side_bulge = 1.0 if not boxy else 0.3
        pts = [
            (0.0, gc + 0.03 + 0.06 * k),
            (hw_r * 0.8, gc + 0.02 + 0.06 * k),
            (hw_r * 0.95 + fl * 0.6, b2),
            (hw_r * 0.985 + fl * 0.85, b3),
            (hw_r * (1.0 + 0.0 * side_bulge) + fl, y4),
            (hw_r * (1.0 + 0.004 * side_bulge) + fl * 0.7, y5),
            (hw_r * (0.999) + fl * 0.3, y6),
            (hw_r * (1.0 + 0.003 * side_bulge), y7),
            (hw_r * 0.988, y8),
            (hw_r * 0.962, belt_e),
        ]
        gh = top_r - belt_e
        c = smoothstep(0.03, 0.2, gh)
        tum = self.tum

        def g(t):
            return 0.95 - (0.95 - tum) * t ** 1.1

        cab = [(0.95, belt_e + 0.012), (g(0.25), belt_e + gh * 0.3), (g(0.55), belt_e + gh * 0.62),
               (g(0.9), top_r - min(0.05, gh * 0.12)), (tum, top_r - min(0.028, gh * 0.06)),
               (tum * 0.93, top_r - 0.006), (tum * 0.58, top_r + self.crown * 0.78), (0.0, top_r + self.crown)]
        hcen = top_r + 0.024
        hood = []
        for xf in (0.935, 0.84, 0.72, 0.6, 0.47, 0.34, 0.19, 0.0):
            s = (1 - (xf / 0.96) ** 2) ** 0.7
            hood.append((xf, belt_e + 0.004 + (hcen - belt_e) * s))
        for (hx, hy), (cx, cy) in zip(hood, cab):
            pts.append((hw_r * lerp(hx, cx, c), lerp(hy, cy, c)))
        # monotonic heights up to the beltline (robust against odd parameter combinations)
        out = []
        for i, (x, y) in enumerate(pts):
            if 1 < i <= 9 and out:
                y = max(y, out[-1][1] + 0.002)
            out.append((x, y))
        return out, dict(c=c, belt=belt_e, top=top_r, hw=hw_r, k=k)

    def _build_sections(self):
        n = 112
        fs = []
        for i in range(n + 1):
            u = i / n
            fs.append(0.62 * u + 0.38 * (0.5 - 0.5 * math.cos(math.pi * u)))
        # exact sections at shut lines and pillars keep the gaps and glass edges straight
        extra = [s[0] for s in self.seams_t] + [self.glass_f0, self.glass_f1, self.b_pillar - 0.022,
                                                 self.b_pillar + 0.022, self.p["ws"], self.p["rs"], self.p["re"],
                                                 self.p["rw"]]
        for e in extra:
            if 0.0 < e < 1.0 and min(abs(e - f) for f in fs) > 0.002:
                fs.append(e)
        fs.sort()
        for f in fs:
            pts, info = self.ring(f)
            self.sections.append((self.z_of(f), f, pts, info))
        self.rake = []
        fn2 = 0.3 / self.L
        for (z, f, pts, info) in self.sections:
            sh = []
            for (x, y) in pts:
                s_ = smoothstep(self.p["gc"] + 0.22, info["belt"] + 0.03, y)
                d = 0.0
                if f < fn2 and not self.boxy:
                    d = 0.085 * s_ * (1 - f / fn2) ** 2
                sh.append(d)
            self.rake.append(sh)

    # ------------------------------------------------------------------ classification
    def seg_kind(self, k, fm, cabin):
        """Material class of ring segment k (between ring points k and k+1) at length fm."""
        p = self.p
        st = self.st
        if k <= WELL:
            return "under"
        if k <= 8:
            return "paint"
        side_glass = cabin and self.glass_f0 < fm < self.glass_f1
        bpil = self.four_door and abs(fm - self.b_pillar) < 0.022 and st != "bus"
        if st == "bus" and side_glass:
            # thin black pillars between the bus windows
            if (fm * 14.0) % 1.0 < 0.08:
                bpil = True
        if k == 9:
            return "trim" if side_glass else "paint"
        if k in (10, 11, 12):
            if side_glass:
                return "trim" if bpil else "glass"
            return "paint"
        if k == 13:
            return "trim" if side_glass else "paint"
        # roof segments: windscreen / rear window
        if cabin and p["ws"] < fm < p["rs"] - 0.004:
            return "glass"
        if cabin and p["re"] + 0.006 < fm < p["rw"] and st not in ("pickup", "truck", "van", "bus"):
            return "glass"
        if st in ("van", "truck", "bus") and cabin and fm < p["rs"]:
            return "glass"
        return "paint"

    # ------------------------------------------------------------------ panel gaps
    def seam_uv(self, x, y, z, ring_idx, f):
        """Returns (uv, uv2): uv = alternating signed distances to the nearest transverse /
        longitudinal shut line, uv2 = activity masks (the shader draws a gap only where the
        interpolated mask is > 0.5, so lines end cleanly)."""
        best = None
        for j, (sf, k0, k1, f0, f1) in enumerate(self.seams_t):
            sz = self.z_of(sf)
            d = abs(z - sz)
            if best is None or d < best[0]:
                active = k0 <= ring_idx <= k1 and f0 - 0.005 <= f <= f1 + 0.005
                best = (d, (z - sz) * (1 if j % 2 == 0 else -1), active)
        u, mu = best[1], 1.0 if best[2] else 0.0
        p = self.p
        hw = p["W"] * 0.5
        v = abs(x) - hw * 0.78
        mv = 0.0
        if not self.boxy and ring_idx >= 10:
            in_hood = 0.075 <= f <= p["ws"] + 0.005
            in_trunk = self.st in ("sedan", "coupe", "super") and p["rw"] + 0.012 <= f <= 0.93
            mv = 1.0 if (in_hood or in_trunk) else 0.0
        return (u, v), (mu, mv)

    # ------------------------------------------------------------------ emit
    def build(self):
        body = MeshBuilder()
        glass = MeshBuilder()
        inner = MeshBuilder()
        self.bf = MeshBuilder()
        self.br = MeshBuilder()
        f_bf = self.seams_t[0][0]
        f_br = self.seams_t[-1][0]
        self.f_bf, self.f_br = f_bf, f_br
        S = self.sections
        st = self.st
        for i in range(len(S) - 1):
            z0, f0, P0, I0 = S[i]
            z1, f1, P1, I1 = S[i + 1]
            R0, R1 = self.rake[i], self.rake[i + 1]
            fm = (f0 + f1) * 0.5
            cabin = I0["c"] > 0.5 and I1["c"] > 0.5
            for side in (1, -1):
                for k in range(N_RING - 1):
                    a = (P0[k][0] * side, P0[k][1], z0 + R0[k])
                    b = (P0[k + 1][0] * side, P0[k + 1][1], z0 + R0[k + 1])
                    c = (P1[k + 1][0] * side, P1[k + 1][1], z1 + R1[k + 1])
                    d = (P1[k][0] * side, P1[k][1], z1 + R1[k])
                    pts = [a, b, c, d] if side == 1 else [d, c, b, a]
                    idx = [(k, f0), (k + 1, f0), (k + 1, f1), (k, f1)]
                    if side != 1:
                        idx = list(reversed(idx))
                    kind = self.seg_kind(k, fm, cabin)
                    if kind == "glass":
                        glass.face(pts, [(q[0], q[2]) for q in pts], "car_glass")
                        continue
                    if kind in ("under", "trim"):
                        mat = "car_trim"
                        if kind == "trim" and self.p.get("chrome") and k == 9:
                            mat = "chrome"
                        body.face(pts, [(1.0, 1.0)] * 4, mat, (0.05, 0.05, 0.05, 1))
                        continue
                    su = [self.seam_uv(q[0], q[1], q[2], ii, ff) for q, (ii, ff) in zip(pts, idx)]
                    uvs = [q[0] for q in su]
                    uv2s = [q[1] for q in su]
                    col = self.livery(fm, (a[1] + c[1]) * 0.5, None)
                    target = body
                    if 2 <= k <= 8 and fm < f_bf:
                        target = self.bf
                    elif 2 <= k <= 8 and fm > f_br:
                        target = self.br
                    target.face(pts, uvs, self.paint_mat, col, uv2=uv2s)
                    n = newell(pts)
                    self.faces.append((pts, n))
                    # interior trim: headliner, pillars and door cards seen from inside
                    if cabin and 3 <= k <= 16:
                        off = 0.03 if k < 9 else 0.02
                        ip = [(q[0] - n[0] * off, q[1] - n[1] * off, q[2] - n[2] * off) for q in reversed(pts)]
                        inner.face(ip, None, "car_interior", (0.2, 0.19, 0.18, 1) if k >= 9 else (0.1, 0.1, 0.1, 1))
        # windows seen from inside
        for fi, f in enumerate(list(glass.faces)):
            pts = [glass.verts[q] for q in f]
            n = newell(pts)
            glass.face([(q[0] - n[0] * 0.004, q[1] - n[1] * 0.004, q[2] - n[2] * 0.004) for q in reversed(pts)],
                       None, "car_glass")
        # nose and tail caps (flat fascia panels, get lamps and grilles as decals)
        for (z, f, P, I), sgn, RK in ((S[0], -1, self.rake[0]), (S[-1], 1, self.rake[-1])):
            ring = [(x, y, z + RK[i]) for i, (x, y) in enumerate(P)]
            ring = ring + [(-x, y, zz) for (x, y, zz) in reversed(ring[1:-1])]
            cy = sum(q[1] for q in ring) / len(ring)
            cz = sum(q[2] for q in ring) / len(ring)
            belt = I["belt"]
            for k in range(len(ring)):
                tri = [(0.0, cy, cz), ring[k], ring[(k + 1) % len(ring)]]
                col = self.livery(f, cy, None)
                ymid = (ring[k][1] + ring[(k + 1) % len(ring)][1]) * 0.5
                target = body if ymid > belt - 0.06 else (self.bf if sgn < 0 else self.br)
                target.face(tri, [(1.0, 1.0)] * 3, self.paint_mat, col, up=(0, 0, sgn), uv2=(0.0, 0.0))
                n = newell(tri)
                if n[2] * sgn < 0:
                    n = (-n[0], -n[1], -n[2])
                self.faces.append((tri if (newell(tri)[2] * sgn > 0) else list(reversed(tri)), n))
        # dark crash structure behind each bumper (visible once a bumper is torn off)
        for fz, sgn in ((f_bf, -1), (f_br, 1)):
            z, f, P, I = min(S, key=lambda q: abs(q[1] - fz))
            ring = [(x * 0.97, y, z + 0.02 * -sgn) for (x, y) in P[1:9]]
            ring = ring + [(-x, y, zz) for (x, y, zz) in reversed(ring)]
            body.face(ring, None, "car_trim", (0.05, 0.05, 0.05, 1), up=(0, 0, sgn))
        return body, glass, inner

    # ------------------------------------------------------------------ decals
    def decal(self, mb, outline, axis, sgn, offset, mat, col=(1, 1, 1, 1), min_facing=0.25, uv2=None, zone=None):
        """Project the convex outline (2D: (x, y) for axis 'z', (z, y) for axis 'x') onto the body
        faces facing axis * sgn, clip, and add the pieces to mb offset along the face normals.
        zone: optional (lo, hi) range of the remaining coordinate (z for 'x', x for 'z')."""
        outline = ccw(outline)
        ai = 2 if axis == "z" else 0
        for pts, n in self.faces:
            if n[ai] * sgn < min_facing:
                continue
            if axis == "z":
                pr = [(q[0], q[1]) for q in pts]
            else:
                pr = [(q[2], q[1]) for q in pts]
            if zone is not None:
                oth = [q[0] for q in pts] if axis == "z" else [q[2] for q in pts]
                if max(oth) < zone[0] or min(oth) > zone[1]:
                    continue
            # quick reject
            if (max(q[0] for q in pr) < min(o[0] for o in outline) or min(q[0] for q in pr) > max(o[0] for o in outline)
                    or max(q[1] for q in pr) < min(o[1] for o in outline) or min(q[1] for q in pr) > max(o[1] for o in outline)):
                continue
            clipped = clip_convex(pr, outline)
            if len(clipped) < 3:
                continue
            p0 = pts[0]
            out = []
            for (u, v) in clipped:
                if axis == "z":
                    zz = p0[2] - (n[0] * (u - p0[0]) + n[1] * (v - p0[1])) / n[2]
                    q = (u, v, zz)
                else:
                    xx = p0[0] - (n[2] * (u - p0[2]) + n[1] * (v - p0[1])) / n[0]
                    q = (xx, v, u)
                out.append((q[0] + n[0] * offset, q[1] + n[1] * offset, q[2] + n[2] * offset))
            mb.face(out, [(1.0, 1.0)] * len(out), mat, col, up=n, uv2=uv2)

    def surface_z(self, x, y, sgn=-1):
        """z of the outer surface hit by a ray along z from outside (front sgn=-1 / rear +1)."""
        best = None
        for pts, n in self.faces:
            if abs(n[2]) < 0.05:
                continue
            pr = [(q[0], q[1]) for q in pts]
            if not _point_in_poly((x, y), pr):
                continue
            p0 = pts[0]
            zz = p0[2] - (n[0] * (x - p0[0]) + n[1] * (y - p0[1])) / n[2]
            if best is None or (zz < best if sgn < 0 else zz > best):
                best = zz
        return best

    def surface_x(self, z, y):
        """Outer |x| of the right side at (z, y)."""
        best = None
        for pts, n in self.faces:
            if n[0] < 0.2:
                continue
            pr = [(q[2], q[1]) for q in pts]
            if not _point_in_poly((z, y), pr):
                continue
            p0 = pts[0]
            xx = p0[0] - (n[2] * (z - p0[2]) + n[1] * (y - p0[1])) / n[0]
            if best is None or xx > best:
                best = xx
        return best

    def section_info(self, f):
        best = min(self.sections, key=lambda s: abs(s[1] - f))
        return best[3]


def _point_in_poly(pt, poly):
    x, y = pt
    inside = False
    j = len(poly) - 1
    for i in range(len(poly)):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if (yi > y) != (yj > y):
            xint = xi + (y - yi) * (xj - xi) / ((yj - yi) or 1e-12)
            if x < xint:
                inside = not inside
        j = i
    return inside
