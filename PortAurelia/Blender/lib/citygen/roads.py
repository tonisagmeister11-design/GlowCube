"""Road network: graph construction, intersections, lanes, signals, sidewalks.

Input: RoadDef polylines. Output: nodes, edges (trimmed), intersection polygons,
lane graph (lanes + connectors), traffic signal phases, pedestrian graph,
parking bays. Everything is plain data that is written to JSON for Godot and
consumed by the Blender mesh builders.
"""
import math

from . import geom as G
from .terrain import SEA_LEVEL

PARK_W = 2.3

ROAD_TYPES = {
    #            lanes  lane w  median shoulder walk   speed  (m/s)
    "highway":    dict(lanes=3, lw=3.7, median=2.4, shoulder=2.6, walk=0.0, speed=27.0),
    "expressway": dict(lanes=2, lw=3.6, median=2.0, shoulder=2.0, walk=0.0, speed=23.0),
    "ramp":       dict(lanes=1, lw=4.2, median=0.0, shoulder=1.2, walk=0.0, speed=15.0),
    "boulevard":  dict(lanes=2, lw=3.4, median=5.0, shoulder=0.4, walk=6.0, speed=14.0),
    "avenue":     dict(lanes=2, lw=3.4, median=2.2, shoulder=0.4, walk=4.5, speed=15.0),
    "street":     dict(lanes=1, lw=3.5, median=0.0, shoulder=0.3, walk=3.5, speed=12.0),
    "oneway":     dict(lanes=2, lw=3.4, median=0.0, shoulder=0.3, walk=3.5, speed=13.0),
    "rural":      dict(lanes=1, lw=3.5, median=0.0, shoulder=1.2, walk=0.0, speed=19.0),
    "hill":       dict(lanes=1, lw=3.3, median=0.0, shoulder=0.8, walk=0.0, speed=13.0),
    "service":    dict(lanes=1, lw=4.0, median=0.0, shoulder=0.5, walk=2.5, speed=10.0),
}

SIGNAL_TYPES = {"avenue", "boulevard", "street", "oneway", "ramp", "service"}


def half_width(rtype, oneway, parking):
    t = ROAD_TYPES[rtype]
    pw = PARK_W if parking else 0.0
    if oneway:
        return t["lanes"] * t["lw"] * 0.5 + pw + t["shoulder"]
    return t["median"] * 0.5 + t["lanes"] * t["lw"] + pw + t["shoulder"]


class RoadDef:
    def __init__(self, pts, rtype, level="surface", oneway=None, parking=False,
                 heights=None, name="", merge_start=False, merge_end=False, tags=None,
                 start_level=None, end_level=None):
        self.pts = [tuple(p) for p in pts]
        self.rtype = rtype
        self.level = level
        self.oneway = (rtype in ("ramp", "oneway")) if oneway is None else oneway
        self.parking = parking
        self.heights = heights  # list per point (elevated) or None (terrain)
        self.name = name
        self.merge_start = merge_start
        self.merge_end = merge_end
        self.tags = tags or {}
        self.splits = []  # (seg_index, t)
        ground = "surface" if rtype == "ramp" else level
        self.start_level = start_level or ground
        self.end_level = end_level or ground


class Node:
    __slots__ = ("id", "pos", "y", "edges", "level", "kind", "control", "sorted", "dirs",
                 "trim", "corners", "poly", "phases", "offset", "radius", "ped", "tags")

    def __init__(self, nid, pos, level):
        self.id = nid
        self.pos = pos
        self.y = 0.0
        self.edges = []
        self.level = level
        self.kind = "joint"
        self.control = "none"  # none | signal | stop | yield
        self.sorted = []
        self.dirs = {}
        self.trim = {}
        self.corners = []
        self.poly = None
        self.phases = {}
        self.offset = 0.0
        self.radius = 0.0
        self.ped = []
        self.tags = {}


class Edge:
    __slots__ = ("id", "a", "b", "pts", "ys", "rtype", "oneway", "parking", "level", "name",
                 "hw", "bridge", "tunnel", "lanes_f", "lanes_b", "tags", "district")

    def __init__(self, eid, a, b, pts, ys, rd):
        self.id = eid
        self.a = a
        self.b = b
        self.pts = pts
        self.ys = ys
        self.rtype = rd.rtype
        self.oneway = rd.oneway
        self.parking = rd.parking
        self.level = rd.level
        self.name = rd.name
        self.hw = half_width(rd.rtype, rd.oneway, rd.parking)
        self.bridge = [False] * len(pts)
        self.tunnel = [False] * len(pts)
        self.lanes_f = []
        self.lanes_b = []
        self.tags = dict(rd.tags)
        self.district = ""

    @property
    def walk(self):
        return ROAD_TYPES[self.rtype]["walk"]

    @property
    def speed(self):
        return ROAD_TYPES[self.rtype]["speed"]

    def length(self):
        return G.polyline_length(self.pts)

    def y_at(self, s):
        """Height at plan arc length s along the centreline."""
        acc = 0.0
        for i in range(len(self.pts) - 1):
            l = G.dist(self.pts[i], self.pts[i + 1])
            if acc + l >= s and l > 1e-6:
                t = (s - acc) / l
                return self.ys[i] + (self.ys[i + 1] - self.ys[i]) * t
            acc += l
        return self.ys[-1]


def wrap_pi(a):
    while a > math.pi:
        a -= 2 * math.pi
    while a <= -math.pi:
        a += 2 * math.pi
    return a


class Network:
    def __init__(self, terrain):
        self.terrain = terrain
        self.defs = []
        self.nodes = []
        self.edges = []
        self.roundabouts = []  # (pos, radius)
        self.lanes = []  # dict records
        self.ped_nodes = []
        self.ped_edges = []
        self.parking = []
        self._node_hash = {}

    def add(self, rd):
        self.defs.append(rd)
        return rd

    def add_roundabout(self, pos, radius):
        self.roundabouts.append((pos, radius))

    # ------------------------------------------------------------ graph build
    def _node_at(self, p, level):
        key = (round(p[0] / 1.5), round(p[1] / 1.5), level)
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                k = (key[0] + dx, key[1] + dz, level)
                for nid in self._node_hash.get(k, []):
                    if G.dist(self.nodes[nid].pos, p) < 1.2:
                        return self.nodes[nid]
        n = Node(len(self.nodes), p, level)
        self.nodes.append(n)
        self._node_hash.setdefault(key, []).append(n.id)
        return n

    def build(self):
        defs = self.defs
        # 1) crossings among surface roads
        for i in range(len(defs)):
            A = defs[i]
            if A.level != "surface":
                continue
            ab = G.poly_bbox(A.pts)
            for j in range(i + 1, len(defs)):
                B = defs[j]
                if B.level != "surface":
                    continue
                bb = G.poly_bbox(B.pts)
                if ab[2] < bb[0] - 1 or bb[2] < ab[0] - 1 or ab[3] < bb[1] - 1 or bb[3] < ab[1] - 1:
                    continue
                for si in range(len(A.pts) - 1):
                    for sj in range(len(B.pts) - 1):
                        r = G.seg_intersect(A.pts[si], A.pts[si + 1], B.pts[sj], B.pts[sj + 1])
                        if r is None:
                            continue
                        _, ta, tb = r
                        A.splits.append((si, ta))
                        B.splits.append((sj, tb))
        # 2) T-junctions: endpoint lying on another road
        for i, A in enumerate(defs):
            for end in (0, -1):
                if (end == 0 and A.merge_start) or (end == -1 and A.merge_end):
                    continue
                p = A.pts[end]
                end_level = A.start_level if end == 0 else A.end_level
                for j, B in enumerate(defs):
                    if i == j:
                        continue
                    if B.level != end_level:
                        continue
                    for sj in range(len(B.pts) - 1):
                        d, t = G.point_seg_dist(p, B.pts[sj], B.pts[sj + 1])
                        if d < 1.0:
                            B.splits.append((sj, t))
                            A.pts[end] = G.lerp(B.pts[sj], B.pts[sj + 1], t)
                            break
        # 3) merge points (ramps joining elevated roads)
        merges = []
        for i, A in enumerate(defs):
            for end, flag in ((0, A.merge_start), (-1, A.merge_end)):
                if not flag:
                    continue
                p = A.pts[end]
                best = None
                for j, B in enumerate(defs):
                    if i == j or B.level != "elevated" or B.rtype == "ramp":
                        continue
                    for sj in range(len(B.pts) - 1):
                        d, t = G.point_seg_dist(p, B.pts[sj], B.pts[sj + 1])
                        if best is None or d < best[0]:
                            best = (d, j, sj, t)
                if best is None:
                    continue
                _, j, sj, t = best
                B = defs[j]
                B.splits.append((sj, t))
                q = G.lerp(B.pts[sj], B.pts[sj + 1], t)
                merges.append((i, end, q))
        merge_points = {(i, end): q for i, end, q in merges}

        # 4) cut defs into edges
        for di, rd in enumerate(defs):
            level = rd.level
            cuts = sorted(set((s, round(t, 6)) for s, t in rd.splits))
            # interpolated heights for split points
            pieces = []
            cur_pts = [rd.pts[0]]
            cur_ys = [rd.heights[0] if rd.heights else None]
            for si in range(len(rd.pts) - 1):
                seg_cuts = sorted(t for s, t in cuts if s == si and 1e-4 < t < 1 - 1e-4)
                for t in seg_cuts:
                    p = G.lerp(rd.pts[si], rd.pts[si + 1], t)
                    y = None
                    if rd.heights:
                        y = rd.heights[si] + (rd.heights[si + 1] - rd.heights[si]) * t
                    cur_pts.append(p)
                    cur_ys.append(y)
                    pieces.append((cur_pts, cur_ys))
                    cur_pts = [p]
                    cur_ys = [y]
                # vertex splits at t==0 / t==1 on interior points
                nxt = rd.pts[si + 1]
                cur_pts.append(nxt)
                cur_ys.append(rd.heights[si + 1] if rd.heights else None)
                if si + 1 < len(rd.pts) - 1 and any(
                        (s == si and t >= 1 - 1e-4) or (s == si + 1 and t <= 1e-4) for s, t in cuts):
                    pieces.append((cur_pts, cur_ys))
                    cur_pts = [nxt]
                    cur_ys = [cur_ys[-1]]
            pieces.append((cur_pts, cur_ys))
            for pi, (pts, ys) in enumerate(pieces):
                pts = [p for k, p in enumerate(pts) if k == 0 or G.dist(p, pts[k - 1]) > 0.05]
                if len(pts) < 2 or G.polyline_length(pts) < 1.0:
                    continue
                start_p = pts[0]
                end_p = pts[-1]
                # interior split points stay on the road's own level; the two
                # ends attach to the network named by start_level / end_level
                a_level = rd.start_level if pi == 0 else level
                b_level = rd.end_level if pi == len(pieces) - 1 else level
                if pi == 0 and (di, 0) in merge_points:
                    na = self._node_at(merge_points[(di, 0)], "elevated")
                    na.kind = "merge"
                else:
                    na = self._node_at(start_p, a_level)
                if pi == len(pieces) - 1 and (di, -1) in merge_points:
                    nb = self._node_at(merge_points[(di, -1)], "elevated")
                    nb.kind = "merge"
                else:
                    nb = self._node_at(end_p, b_level)
                if na.id == nb.id:
                    continue
                ys2 = list(ys) if rd.heights else [0.0] * len(pts)
                if rd.heights and len(ys2) != len(pts):
                    ys2 = [rd.heights[0]] * len(pts)
                e = Edge(len(self.edges), na.id, nb.id, pts, ys2, rd)
                e.tags["has_heights"] = bool(rd.heights)
                self.edges.append(e)
                na.edges.append(e.id)
                nb.edges.append(e.id)
        self._classify_nodes()
        self._compute_heights()

    # ------------------------------------------------------------ node classes
    def _classify_nodes(self):
        for n in self.nodes:
            deg = len(n.edges)
            if n.kind == "merge":
                continue
            for (p, r) in self.roundabouts:
                if G.dist(p, n.pos) < 2.0:
                    n.kind = "roundabout"
                    n.radius = r
                    break
            if n.kind == "roundabout":
                n.control = "yield"
                continue
            if deg == 1:
                n.kind = "end"
            elif deg == 2:
                n.kind = "joint"
            else:
                n.kind = "int"
                types = [self.edges[e].rtype for e in n.edges]
                if n.level == "surface":
                    major = sum(1 for t in types if t in ("avenue", "boulevard", "ramp", "oneway"))
                    if major >= 1 and all(t in SIGNAL_TYPES for t in types):
                        n.control = "signal"
                    elif deg >= 4 and all(t in SIGNAL_TYPES for t in types):
                        n.control = "signal"
                    else:
                        n.control = "stop"
                else:
                    n.control = "yield"

    # ------------------------------------------------------------ heights
    def _compute_heights(self):
        T = self.terrain
        for n in self.nodes:
            if n.level == "surface":
                n.y = max(T.natural_height(*n.pos), 0.0) if not self._near_water(n.pos) else 0.0
        for e in self.edges:
            if e.tags.get("has_heights"):
                # elevated: heights given; nodes adopt them
                na, nb = self.nodes[e.a], self.nodes[e.b]
                if na.level == "elevated" and na.kind != "merge":
                    na.y = e.ys[0]
                if nb.level == "elevated" and nb.kind != "merge":
                    nb.y = e.ys[-1]
        for n in self.nodes:
            if n.kind == "merge":
                # find the through (non-ramp) edge height at that node
                for eid in n.edges:
                    e = self.edges[eid]
                    if e.rtype != "ramp":
                        n.y = e.ys[0] if e.a == n.id else e.ys[-1]
                        break
        for e in self.edges:
            na, nb = self.nodes[e.a], self.nodes[e.b]
            if e.tags.get("has_heights"):
                if e.rtype == "ramp":
                    # pin ramp ends to their nodes and interpolate linearly
                    total = e.length()
                    acc = 0.0
                    ys = []
                    for i in range(len(e.pts)):
                        if i > 0:
                            acc += G.dist(e.pts[i - 1], e.pts[i])
                        t = acc / max(total, 1e-6)
                        s = t * t * (3 - 2 * t)
                        ys.append(na.y + (nb.y - na.y) * s)
                    e.ys = ys
                self._mark_tunnels(e)
                continue
            # surface: resample and follow terrain
            pts = e.pts
            need = False
            for i in range(len(pts) - 1):
                l = G.dist(pts[i], pts[i + 1])
                steps = max(1, int(l / 5.0))
                for k in range(steps + 1):
                    p = G.lerp(pts[i], pts[i + 1], k / steps)
                    h = T.natural_height(*p)
                    if abs(h) > 0.05:
                        need = True
                        break
                if need:
                    break
            if need:
                pts = G.polyline_resample(pts, 5.0)
                e.pts = pts
            ys = [T.natural_height(*p) for p in pts]
            bridge = [self._near_water(p) or y < -0.4 for p, y in zip(pts, ys)]
            # bridges: interpolate across water runs
            i = 0
            while i < len(pts):
                if bridge[i]:
                    j = i
                    while j < len(pts) and bridge[j]:
                        j += 1
                    y0 = ys[i - 1] if i > 0 else na.y
                    y1 = ys[j] if j < len(pts) else nb.y
                    y0 = max(y0, 0.0)
                    y1 = max(y1, 0.0)
                    for k in range(i, j):
                        t = (k - i + 1) / (j - i + 1)
                        ys[k] = y0 + (y1 - y0) * t + math.sin(t * math.pi) * 0.6
                    i = j
                else:
                    i += 1
            # smooth
            if len(pts) > 4:
                sm = list(ys)
                for k in range(1, len(pts) - 1):
                    if bridge[k]:
                        continue
                    lo = max(0, k - 3)
                    hi = min(len(pts), k + 4)
                    sm[k] = sum(ys[lo:hi]) / (hi - lo)
                ys = sm
            ys[0] = na.y
            ys[-1] = nb.y
            # blend towards node heights near ends
            total = G.polyline_length(pts)
            acc = 0.0
            for k in range(len(pts)):
                if k > 0:
                    acc += G.dist(pts[k - 1], pts[k])
                wa = 1.0 - G.smoothstep(0.0, 25.0, acc)
                wb = 1.0 - G.smoothstep(0.0, 25.0, total - acc)
                ys[k] = ys[k] * (1 - wa) + na.y * wa
                ys[k] = ys[k] * (1 - wb) + nb.y * wb
            e.ys = ys
            e.bridge = bridge

    def _near_water(self, p):
        return self.terrain.natural_height(*p) < SEA_LEVEL + 0.8 or self.terrain.natural_height(*p) < -0.9

    def _mark_tunnels(self, e):
        T = self.terrain
        tun = []
        bri = []
        for p, y in zip(e.pts, e.ys):
            h = T.natural_height(*p)
            tun.append(h > y + 8.5)
            bri.append(h < y - 2.5)
        e.tunnel = tun
        e.bridge = bri

    # ------------------------------------------------------------ intersections
    def _edge_dir_from(self, e, nid):
        """Unit direction leaving node nid along edge e (first real segment)."""
        pts = e.pts if e.a == nid else list(reversed(e.pts))
        base = pts[0]
        for p in pts[1:]:
            if G.dist(p, base) > 0.5:
                return G.norm(G.sub(p, base))
        return G.norm(G.sub(pts[-1], pts[0]))

    def compute_intersections(self):
        for n in self.nodes:
            if not n.edges:
                continue
            dirs = {}
            for eid in n.edges:
                dirs[eid] = self._edge_dir_from(self.edges[eid], n.id)
            n.dirs = dirs
            n.sorted = sorted(n.edges, key=lambda eid: G.angle(dirs[eid]))
            n.trim = {eid: 0.0 for eid in n.edges}
            n.corners = []
            if n.kind == "merge":
                continue
            deg = len(n.sorted)
            P = n.pos
            if n.kind == "roundabout":
                R = n.radius
                for eid in n.sorted:
                    n.trim[eid] = R + 0.8
                for i in range(deg):
                    e0 = self.edges[n.sorted[i]]
                    e1 = self.edges[n.sorted[(i + 1) % deg]]
                    d0 = dirs[e0.id]
                    d1 = dirs[e1.id]
                    a0 = G.angle(d0) + math.asin(min(0.95, e0.hw / (R + 0.8)))
                    a1 = G.angle(d1) - math.asin(min(0.95, e1.hw / (R + 0.8)))
                    while a1 < a0:
                        a1 += 2 * math.pi
                    start = G.add(P, G.add(G.mul(d0, n.trim[e0.id]), G.mul(G.right(d0), e0.hw)))
                    end = G.add(P, G.add(G.mul(d1, n.trim[e1.id]), G.mul(G.left(d1), e1.hw)))
                    arcp = G.arc(P, R + 1.0, a0, a1, max(4, int((a1 - a0) * R / 3.0)))
                    n.corners.append([start] + arcp + [end])
                n.poly = G.arc(P, R + 1.0, 0, 2 * math.pi, 40)[:-1]
                continue
            if deg == 1:
                e = self.edges[n.sorted[0]]
                d = dirs[e.id]
                r_pt = G.add(P, G.mul(G.right(d), e.hw))
                l_pt = G.add(P, G.mul(G.left(d), e.hw))
                back = G.mul(d, -e.hw * 0.6)
                n.corners.append([r_pt, G.add(G.add(P, back), G.mul(G.right(d), e.hw * 0.7)),
                                  G.add(P, back),
                                  G.add(G.add(P, back), G.mul(G.left(d), e.hw * 0.7)), l_pt])
                continue
            corner_pts = []
            for i in range(deg):
                e0 = self.edges[n.sorted[i]]
                e1 = self.edges[n.sorted[(i + 1) % deg]]
                d0 = dirs[e0.id]
                d1 = dirs[e1.id]
                p0 = G.add(P, G.mul(G.right(d0), e0.hw))
                p1 = G.add(P, G.mul(G.left(d1), e1.hw))
                sector = (G.angle(d1) - G.angle(d0)) % (2 * math.pi)
                if deg == 2 and sector > math.pi * 0.92:
                    c = None
                else:
                    r = G.line_intersect(p0, d0, p1, d1)
                    c = None
                    if r is not None and r[0] > -1.0 and r[1] > -1.0 and sector < math.pi * 0.98:
                        c = G.add(p0, G.mul(d0, r[0]))
                        if G.dist(c, P) > 80.0:
                            c = None
                corner_pts.append((c, p0, p1))
                if c is not None:
                    n.trim[e0.id] = max(n.trim[e0.id], G.dot(G.sub(c, P), d0))
                    n.trim[e1.id] = max(n.trim[e1.id], G.dot(G.sub(c, P), d1))
            margin = 0.0
            if n.kind == "int":
                has_walk = any(self.edges[eid].walk > 0 for eid in n.edges)
                margin = 2.5 if n.level == "surface" else 1.0
                if has_walk:
                    margin = 3.0
            for eid in n.sorted:
                n.trim[eid] = max(0.0, n.trim[eid]) + margin
                # never trim more than 45% of an edge
                n.trim[eid] = min(n.trim[eid], self.edges[eid].length() * 0.45)
            poly = []
            for i in range(deg):
                e0 = self.edges[n.sorted[i]]
                e1 = self.edges[n.sorted[(i + 1) % deg]]
                d0 = dirs[e0.id]
                d1 = dirs[e1.id]
                c, p0, p1 = corner_pts[i]
                r0 = G.add(P, G.add(G.mul(d0, n.trim[e0.id]), G.mul(G.right(d0), e0.hw)))
                l1 = G.add(P, G.add(G.mul(d1, n.trim[e1.id]), G.mul(G.left(d1), e1.hw)))
                if c is None:
                    curve = [r0, l1]
                elif n.kind == "int" and n.level == "surface":
                    curve = G.bezier2(r0, c, l1, 6)
                else:
                    curve = [r0, c, l1]
                n.corners.append(curve)
                l0 = G.add(P, G.add(G.mul(d0, n.trim[e0.id]), G.mul(G.left(d0), e0.hw)))
                poly.append(l0)
                poly.extend(curve)
            if deg >= 3 or any(n.trim[e] > 0.3 for e in n.sorted):
                n.poly = G.poly_dedupe(poly, 0.05)

    # ------------------------------------------------------------ edge geometry
    def edge_trimmed(self, e):
        """Centreline of edge e trimmed by node intersection areas, with heights.

        Returns (pts, ys, t0, t1) where pts is a list of (x, z)."""
        t0 = self.nodes[e.a].trim.get(e.id, 0.0)
        t1 = self.nodes[e.b].trim.get(e.id, 0.0)
        total = e.length()
        pts = G.polyline_trim(e.pts, t0, t1)
        ys = []
        # recompute arc lengths along the original polyline
        acc = t0
        for i, p in enumerate(pts):
            if i > 0:
                acc += G.dist(pts[i - 1], p)
            ys.append(e.y_at(min(acc, total)))
        return pts, ys, t0, t1

    def edge_side_boundary(self, e, from_node, side):
        """Carriageway boundary along e travelling from `from_node`; side=+1 right, -1 left."""
        pts, ys, t0, t1 = self.edge_trimmed(e)
        if from_node != e.a:
            pts = list(reversed(pts))
        return G.polyline_offset(pts, side * e.hw)

    # ------------------------------------------------------------ blocks
    def find_faces(self):
        """Trace faces of the surface graph (blocks). Returns list of CCW polygons."""
        used = set()
        faces = []
        for e in self.edges:
            if e.level != "surface" or e.rtype == "ramp":
                continue
            for (start, end) in ((e.a, e.b), (e.b, e.a)):
                if (e.id, start) in used:
                    continue
                poly = []
                cur_e, cur_from = e, start
                guard = 0
                ok = True
                while guard < 400:
                    guard += 1
                    key = (cur_e.id, cur_from)
                    if key in used:
                        break
                    used.add(key)
                    cur_to = cur_e.b if cur_from == cur_e.a else cur_e.a
                    poly.extend(self.edge_side_boundary(cur_e, cur_from, -1))
                    node = self.nodes[cur_to]
                    srt = [x for x in node.sorted if self.edges[x].level == "surface"
                           and self.edges[x].rtype != "ramp"]
                    if cur_e.id not in srt:
                        ok = False
                        break
                    k = srt.index(cur_e.id)
                    nxt = self.edges[srt[(k + 1) % len(srt)]]
                    # corner polyline between cur_e (right side leaving node) and nxt (left side)
                    corner = self._corner_between(node, cur_e.id, nxt.id)
                    if corner:
                        poly.extend(corner)
                    cur_e, cur_from = nxt, cur_to
                if not ok or len(poly) < 3:
                    continue
                poly = G.poly_dedupe(poly, 0.1)
                if len(poly) < 3:
                    continue
                a = G.poly_area(poly)
                if a < -50.0:
                    faces.append(list(reversed(poly)))
        return faces

    def _corner_between(self, node, e0, e1):
        """Corner curve from e0's right boundary to e1's left boundary at node (surface only)."""
        srt = node.sorted
        if node.kind == "merge":
            return []
        if e0 not in srt or e1 not in srt:
            return []
        i = srt.index(e0)
        # corners are computed for the full sorted list; if ramps exist, walk forward
        deg = len(srt)
        if deg == 1:
            return list(node.corners[0]) if node.corners else []
        j = srt.index(e1)
        if (i + 1) % deg == j and i < len(node.corners):
            return list(node.corners[i])
        # skip edges in between (e.g. ramps): join r0 of e0 to l1 of e1 directly
        P = node.pos
        E0 = self.edges[e0]
        E1 = self.edges[e1]
        d0 = node.dirs[e0]
        d1 = node.dirs[e1]
        r0 = G.add(P, G.add(G.mul(d0, node.trim[e0]), G.mul(G.right(d0), E0.hw)))
        l1 = G.add(P, G.add(G.mul(d1, node.trim[e1]), G.mul(G.left(d1), E1.hw)))
        return [r0, l1]

    # ------------------------------------------------------------ lanes
    def build_lanes(self):
        lanes = []

        def add_lane(rec):
            rec["id"] = len(lanes)
            lanes.append(rec)
            return rec["id"]

        for e in self.edges:
            t = ROAD_TYPES[e.rtype]
            pts, ys, t0, t1 = self.edge_trimmed(e)
            na, nb = self.nodes[e.a], self.nodes[e.b]
            # stop line setback for crosswalks
            sa = 4.5 if (na.kind == "int" and na.level == "surface" and e.walk > 0) else 0.5
            sb = 4.5 if (nb.kind == "int" and nb.level == "surface" and e.walk > 0) else 0.5
            if na.kind in ("joint", "end", "merge"):
                sa = 0.0
            if nb.kind in ("joint", "end", "merge"):
                sb = 0.0
            base = list(zip(pts, ys))
            L = G.polyline_length(pts)
            if L - sa - sb < 2.0:
                sa = sb = 0.0
            pts2 = G.polyline_trim(pts, sa, sb)
            ys2 = [e.y_at(min(t0 + sa + s, e.length())) for s in _cum(pts2)]
            n = t["lanes"]
            lw = t["lw"]
            e.lanes_f = []
            e.lanes_b = []
            if e.oneway:
                for i in range(n):
                    off = -n * lw * 0.5 + (i + 0.5) * lw
                    lp = G.polyline_offset(pts2, off)
                    e.lanes_f.append(add_lane(dict(edge=e.id, dir=1, index=i, count=n,
                                                   pts=_p3(lp, ys2), speed=t["speed"],
                                                   from_node=e.a, to_node=e.b, kind="lane")))
            else:
                for i in range(n):
                    off = t["median"] * 0.5 + (i + 0.5) * lw
                    lp = G.polyline_offset(pts2, off)
                    e.lanes_f.append(add_lane(dict(edge=e.id, dir=1, index=i, count=n,
                                                   pts=_p3(lp, ys2), speed=t["speed"],
                                                   from_node=e.a, to_node=e.b, kind="lane")))
                rp = list(reversed(pts2))
                rys = list(reversed(ys2))
                for i in range(n):
                    off = t["median"] * 0.5 + (i + 0.5) * lw
                    lp = G.polyline_offset(rp, off)
                    e.lanes_b.append(add_lane(dict(edge=e.id, dir=-1, index=i, count=n,
                                                   pts=_p3(lp, rys), speed=t["speed"],
                                                   from_node=e.b, to_node=e.a, kind="lane")))
            for group in (e.lanes_f, e.lanes_b):
                for k, lid in enumerate(group):
                    lanes[lid]["left"] = group[k - 1] if k > 0 else -1
                    lanes[lid]["right"] = group[k + 1] if k + 1 < len(group) else -1
            _ = base
        for rec in lanes:
            rec["next"] = []
        # connectors
        for n in self.nodes:
            incoming = []
            outgoing = []
            for eid in n.edges:
                e = self.edges[eid]
                if e.b == n.id:
                    incoming.append((e, e.lanes_f))
                    outgoing.append((e, e.lanes_b))
                if e.a == n.id:
                    incoming.append((e, e.lanes_b))
                    outgoing.append((e, e.lanes_f))
            for (ein, lin) in incoming:
                if not lin:
                    continue
                d_in = G.mul(n.dirs[ein.id], -1.0)
                for (eout, lout) in outgoing:
                    if not lout:
                        continue
                    if eout.id == ein.id and len(n.edges) > 1:
                        continue
                    d_out = n.dirs[eout.id]
                    delta = wrap_pi(G.angle(d_out) - G.angle(d_in))
                    if abs(delta) < math.radians(35):
                        turn = "straight"
                    elif delta > 0:
                        turn = "right"
                    else:
                        turn = "left"
                    if abs(delta) > math.radians(160) and len(n.edges) > 1:
                        continue
                    pairs = []
                    ni, no = len(lin), len(lout)
                    if n.kind in ("joint", "merge", "end") or ni == 1 and no == 1:
                        for i in range(ni):
                            pairs.append((lin[i], lout[min(i, no - 1)]))
                    elif turn == "straight":
                        for i in range(ni):
                            pairs.append((lin[i], lout[min(i, no - 1)]))
                    elif turn == "right":
                        pairs.append((lin[-1], lout[-1]))
                    else:
                        pairs.append((lin[0], lout[0]))
                    if n.kind == "merge":
                        # ramps join/leave the outer lane only
                        if ein.rtype == "ramp" and eout.rtype != "ramp":
                            pairs = [(lin[-1], lout[-1])]
                        elif eout.rtype == "ramp" and ein.rtype != "ramp":
                            pairs = [(lin[-1], lout[-1])]
                    for (la, lb) in pairs:
                        A = lanes[la]
                        B = lanes[lb]
                        p0 = A["pts"][-1]
                        p1 = B["pts"][0]
                        if n.kind == "joint" and G.dist((p0[0], p0[2]), (p1[0], p1[2])) < 0.3:
                            A["next"].append(lb)
                            continue
                        path = self._connector_path(n, A, B, ein, eout)
                        cid = add_lane(dict(edge=-1, dir=0, index=0, count=1, pts=path,
                                            speed=min(A["speed"], B["speed"]) * (0.55 if turn != "straight" else 0.9),
                                            from_node=n.id, to_node=n.id, kind="connector",
                                            node=n.id, turn=turn, from_lane=la, to_lane=lb,
                                            in_edge=ein.id, left=-1, right=-1, next=[lb]))
                        A["next"].append(cid)
        self.lanes = lanes

    def _connector_path(self, n, A, B, ein, eout):
        p0 = A["pts"][-1]
        p1 = B["pts"][0]
        a2 = (p0[0], p0[2])
        b2 = (p1[0], p1[2])
        d0 = G.norm(G.sub(a2, (A["pts"][-2][0], A["pts"][-2][2])))
        d1 = G.norm(G.sub((B["pts"][1][0], B["pts"][1][2]), b2))
        if n.kind == "roundabout":
            P = n.pos
            R = n.radius * 0.5 + 3.5
            ai = math.atan2(a2[1] - P[1], a2[0] - P[0])
            ao = math.atan2(b2[1] - P[1], b2[0] - P[0])
            # travel direction: decreasing angle (counter-clockwise on a north-up map)
            while ao > ai:
                ao -= 2 * math.pi
            if ai - ao < 0.4:
                ao -= 2 * math.pi
            steps = max(6, int((ai - ao) * R / 4.0))
            ring = [(P[0] + math.cos(ai + (ao - ai) * k / steps) * R,
                     P[1] + math.sin(ai + (ao - ai) * k / steps) * R) for k in range(steps + 1)]
            path2 = [a2] + ring + [b2]
        else:
            k = G.dist(a2, b2) * 0.42
            path2 = G.bezier3(a2, G.add(a2, G.mul(d0, k)), G.sub(b2, G.mul(d1, k)), b2,
                              max(4, int(G.dist(a2, b2) / 3.0)))
        n_ = len(path2)
        out = []
        for i, p in enumerate(path2):
            t = i / max(1, n_ - 1)
            out.append((round(p[0], 2), round(p0[1] + (p1[1] - p0[1]) * t, 2), round(p[1], 2)))
        return out

    # ------------------------------------------------------------ signals
    def build_signals(self, rng):
        for n in self.nodes:
            if n.control != "signal":
                continue
            axes = []
            phases = {}
            for eid in n.sorted:
                th = G.angle(n.dirs[eid]) % math.pi
                best = None
                for ai, ax in enumerate(axes):
                    dd = abs(((th - ax) + math.pi / 2) % math.pi - math.pi / 2)
                    if dd < math.radians(35) and (best is None or dd < best[0]):
                        best = (dd, ai)
                if best is None:
                    if len(axes) < 2:
                        axes.append(th)
                        phases[eid] = len(axes) - 1
                    else:
                        # closest of the existing two
                        d0 = abs(((th - axes[0]) + math.pi / 2) % math.pi - math.pi / 2)
                        d1 = abs(((th - axes[1]) + math.pi / 2) % math.pi - math.pi / 2)
                        phases[eid] = 0 if d0 < d1 else 1
                else:
                    phases[eid] = best[1]
            if len(axes) < 2:
                # all edges on one axis (should not happen) -> treat as stop
                n.control = "stop"
                continue
            n.phases = phases
            n.offset = round(rng.uniform(0, 30), 2)

    # ------------------------------------------------------------ pedestrians
    def build_ped_graph(self):
        ped_nodes = []
        ped_edges = []
        corner_node = {}

        def pn(pos, y, kind="corner", node=-1):
            ped_nodes.append(dict(id=len(ped_nodes), pos=(round(pos[0], 2), round(y + 0.16, 2), round(pos[1], 2)),
                                  kind=kind, node=node))
            return len(ped_nodes) - 1

        for n in self.nodes:
            if n.level != "surface" or not n.corners:
                continue
            srt = n.sorted
            deg = len(srt)
            for i, curve in enumerate(n.corners):
                e0 = self.edges[srt[i % deg]]
                e1 = self.edges[srt[(i + 1) % deg]]
                walk = max(e0.walk, e1.walk)
                if walk <= 0:
                    continue
                mid = curve[len(curve) // 2]
                away = G.norm(G.sub(mid, n.pos))
                if G.length(away) < 0.1:
                    away = G.right(n.dirs[e0.id])
                pos = G.add(mid, G.mul(away, walk * 0.5))
                corner_node[(n.id, i)] = pn(pos, n.y, "corner", n.id)
        # along edges
        for e in self.edges:
            if e.level != "surface" or e.walk <= 0 or e.rtype == "ramp":
                continue
            na, nb = self.nodes[e.a], self.nodes[e.b]
            if e.id not in na.sorted or e.id not in nb.sorted:
                continue
            ka = na.sorted.index(e.id)
            kb = nb.sorted.index(e.id)
            dega, degb = len(na.sorted), len(nb.sorted)
            sides = [
                ((na.id, ka % dega), (nb.id, (kb - 1) % degb), +1),
                ((na.id, (ka - 1) % dega), (nb.id, kb % degb), -1),
            ]
            pts, ys, t0, t1 = self.edge_trimmed(e)
            for (ca, cb, side) in sides:
                if ca not in corner_node or cb not in corner_node:
                    continue
                path = G.polyline_offset(pts, side * (e.hw + e.walk * 0.5))
                path3 = [(round(p[0], 2), round(y + 0.16, 2), round(p[1], 2)) for p, y in zip(path, ys)]
                ped_edges.append(dict(a=corner_node[ca], b=corner_node[cb], kind="walk", edge=e.id,
                                      path=path3))
        # crosswalks
        for n in self.nodes:
            if n.level != "surface" or n.kind != "int":
                continue
            deg = len(n.sorted)
            for k, eid in enumerate(n.sorted):
                e = self.edges[eid]
                if e.walk <= 0 or e.rtype == "ramp":
                    continue
                ca = (n.id, (k - 1) % deg)
                cb = (n.id, k)
                if ca not in corner_node or cb not in corner_node:
                    continue
                d = n.dirs[eid]
                t = n.trim[eid] + 2.0
                c = G.add(n.pos, G.mul(d, t))
                pa = G.add(c, G.mul(G.left(d), e.hw + 0.8))
                pb = G.add(c, G.mul(G.right(d), e.hw + 0.8))
                y = n.y + 0.02
                ped_edges.append(dict(a=corner_node[ca], b=corner_node[cb], kind="crosswalk", edge=eid,
                                      node=n.id, phase=n.phases.get(eid, -1) if n.phases else -1,
                                      path=[(round(pa[0], 2), round(y, 2), round(pa[1], 2)),
                                            (round(pb[0], 2), round(y, 2), round(pb[1], 2))]))
        self.ped_nodes = ped_nodes
        self.ped_edges = ped_edges
        self._corner_node = corner_node

    # ------------------------------------------------------------ parking
    def build_parking_bays(self, rng):
        bays = []
        for e in self.edges:
            if not e.parking:
                continue
            pts, ys, t0, t1 = self.edge_trimmed(e)
            L = G.polyline_length(pts)
            if L < 30:
                continue
            off = e.hw - ROAD_TYPES[e.rtype]["shoulder"] - PARK_W * 0.5
            for side in (1, -1):
                s = 9.0
                while s < L - 9.0:
                    if rng.chance(0.12):
                        s += 7.0  # driveway / hydrant gap
                        continue
                    p, d = G.polyline_point_at(pts, s)
                    pos = G.add(p, G.mul(G.right(d), side * off))
                    heading = d if (side == 1 or e.oneway) else G.mul(d, -1)
                    y = e.y_at(t0 + s)
                    bays.append(dict(pos=(round(pos[0], 2), round(y, 2), round(pos[1], 2)),
                                     dir=(round(heading[0], 3), round(heading[1], 3)),
                                     edge=e.id, kind="street"))
                    s += 6.2
        self.parking = bays


def _cum(pts):
    out = [0.0]
    for i in range(1, len(pts)):
        out.append(out[-1] + G.dist(pts[i - 1], pts[i]))
    return out


def _p3(pts, ys):
    n = min(len(pts), len(ys))
    if len(ys) != len(pts):
        # resample heights proportionally
        ys = [ys[min(len(ys) - 1, int(i * (len(ys) - 1) / max(1, len(pts) - 1)))] for i in range(len(pts))]
        n = len(pts)
    return [(round(pts[i][0], 2), round(ys[i], 2), round(pts[i][1], 2)) for i in range(n)]
