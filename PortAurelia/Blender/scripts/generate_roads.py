"""Road, sidewalk, marking, bridge, highway and tunnel geometry for city chunks.

Called by generate_city.py per chunk. All functions append to MeshBuilders in
Godot space. Can also be run standalone for a quick test:
    python generate_roads.py -- 8 9     (chunk indices)
"""
import math

from _common import G, box, cylinder, sweep, cap_polygon

PAD_H = 0.16
MARK_Y = 0.018
WHITE = (0.95, 0.95, 0.92, 1.0)
YELLOW = (0.95, 0.72, 0.12, 1.0)


def _p3(p, y):
    return (p[0], y, p[1])


def _strip(mb, left, right, ysl, ysr, mat, col=(1, 1, 1, 1), v0=0.0, uscale=1.0, up=(0, 1, 0), lateral=None):
    """Quad strip between two polylines with equal point counts."""
    acc = v0
    n = min(len(left), len(right))
    for i in range(n - 1):
        a, b = left[i], right[i]
        c, d = right[i + 1], left[i + 1]
        seg = G.dist(G.lerp(a, b, 0.5), G.lerp(c, d, 0.5))
        if lateral is None:
            wl = 0.0
            wr = G.dist(a, b)
        else:
            wl, wr = lateral
        uvs = [(wl * uscale, acc), (wr * uscale, acc), (wr * uscale, acc + seg), (wl * uscale, acc + seg)]
        mb.face([_p3(a, ysl[i]), _p3(b, ysr[i]), _p3(c, ysr[i + 1]), _p3(d, ysl[i + 1])], uvs, mat, col, up=up)
        acc += seg
    return acc


def _dashes(mb, pts, ys, off, width, dash, gap, col, y_off=MARK_Y, start=0.0):
    """Painted dashed (or solid if gap == 0) line along a polyline at lateral offset."""
    line = G.polyline_offset(pts, off) if abs(off) > 1e-4 else list(pts)
    L = G.polyline_length(line)
    if L < 0.5:
        return
    cum = [0.0]
    for i in range(1, len(line)):
        cum.append(cum[-1] + G.dist(line[i - 1], line[i]))

    def y_at(s):
        for i in range(len(cum) - 1):
            if cum[i + 1] >= s:
                t = (s - cum[i]) / max(cum[i + 1] - cum[i], 1e-6)
                return ys[i] + (ys[i + 1] - ys[i]) * t
        return ys[-1]

    s = start
    step = dash + gap if gap > 0 else L
    while s < L - 0.1:
        s1 = min(L, s + (dash if gap > 0 else L))
        # sub-polyline between s and s1
        seg = _sub_polyline(line, cum, s, s1)
        if len(seg) >= 2:
            lft = G.polyline_offset(seg, -width * 0.5)
            rgt = G.polyline_offset(seg, width * 0.5)
            ss = [s]
            for i in range(1, len(seg)):
                ss.append(ss[-1] + G.dist(seg[i - 1], seg[i]))
            yy = [y_at(v) + y_off for v in ss]
            _strip(mb, lft, rgt, yy, yy, "road_marking", col)
        s += step


def _sub_polyline(line, cum, s0, s1):
    out = [G.polyline_point_at(line, s0)[0]]
    for i in range(1, len(line) - 1):
        if s0 < cum[i] < s1:
            out.append(line[i])
    out.append(G.polyline_point_at(line, s1)[0])
    return out


# ============================================================== surface roads
def build_surface_edge(plan, e, mb_ground, mb_mark, mb_struct, mb_col):
    pts = [tuple(p) for p in e["trim_pts"]]
    ys = list(e["trim_ys"])
    if len(pts) < 2 or G.polyline_length(pts) < 0.3:
        return
    hw = e["hw"]
    rt = e["rtype"]
    bridge_any = any(e["bridge"])
    left = G.polyline_offset(pts, -hw)
    right = G.polyline_offset(pts, hw)
    _strip(mb_ground, left, right, ys, ys, "road_asphalt", lateral=(-hw, hw))
    _strip(mb_col["road"], left, right, ys, ys, "road_asphalt")

    # ---------------------------------------------------------- markings
    nodes = plan["nodes"]
    na, nb = nodes[e["a"]], nodes[e["b"]]
    lanes, lw, med = e["lanes"], e["lw"], e["median"]
    park = e["parking"]
    sh = e["shoulder"]
    # distance the markings keep from intersection edges (crosswalk band)
    total = G.polyline_length(pts)
    cw_a = 4.2 if (na["kind"] == "int" and e["walk"] > 0) else 0.0
    cw_b = 4.2 if (nb["kind"] == "int" and e["walk"] > 0) else 0.0
    mpts = G.polyline_trim(pts, cw_a, cw_b) if total > cw_a + cw_b + 2 else None
    if mpts:
        mys = _ys_for(pts, ys, mpts, cw_a)
        if e["oneway"]:
            for i in range(1, lanes):
                off = -lanes * lw * 0.5 + i * lw
                _dashes(mb_mark, mpts, mys, off, 0.12, 3.0, 6.0, WHITE)
            edge_off = lanes * lw * 0.5
            _dashes(mb_mark, mpts, mys, edge_off, 0.12, 1, 0, WHITE)
            _dashes(mb_mark, mpts, mys, -edge_off, 0.12, 1, 0, YELLOW if rt in ("ramp",) else WHITE)
        else:
            if rt == "avenue":
                pass  # raised median below
            elif rt == "boulevard":
                pass
            elif rt in ("street", "service", "hill", "rural"):
                if rt == "rural" or rt == "hill":
                    _dashes(mb_mark, mpts, mys, 0.0, 0.12, 3.0, 5.0, YELLOW)
                else:
                    _dashes(mb_mark, mpts, mys, -0.12, 0.1, 1, 0, YELLOW)
                    _dashes(mb_mark, mpts, mys, 0.12, 0.1, 1, 0, YELLOW)
            for i in range(1, lanes):
                off = med * 0.5 + i * lw
                _dashes(mb_mark, mpts, mys, off, 0.12, 3.0, 6.0, WHITE)
                _dashes(mb_mark, mpts, mys, -off, 0.12, 3.0, 6.0, WHITE)
            edge = med * 0.5 + lanes * lw
            if park or sh > 0.6:
                _dashes(mb_mark, mpts, mys, edge, 0.12, 1, 0, WHITE)
                _dashes(mb_mark, mpts, mys, -edge, 0.12, 1, 0, WHITE)
        # parking bay ticks
        if park:
            edge = (med * 0.5 + lanes * lw) if not e["oneway"] else lanes * lw * 0.5
            for side in (1, -1):
                s = 9.0 - 3.1
                L = G.polyline_length(mpts)
                while s < L - 6:
                    p, d = G.polyline_point_at(mpts, s)
                    n = G.right(d)
                    a = G.add(p, G.mul(n, side * edge))
                    b = G.add(p, G.mul(n, side * (edge + 2.2)))
                    y = _y_along(mpts, mys, s) + MARK_Y
                    t = G.mul(d, 0.06)
                    mb_mark.face([_p3(G.sub(a, t), y), _p3(G.add(a, t), y), _p3(G.add(b, t), y), _p3(G.sub(b, t), y)],
                                 None, "road_marking", WHITE, up=(0, 1, 0))
                    s += 6.2
    # stop lines + crosswalks at intersections
    for end, node in ((0, na), (1, nb)):
        if node["kind"] != "int" or node["level"] != "surface":
            continue
        if e["walk"] > 0:
            _crosswalk(mb_mark, pts, ys, hw, end)
        incoming = (end == 1) or e["oneway"] is False
        if node["control"] in ("signal", "stop") and incoming:
            _stop_line(mb_mark, pts, ys, e, end, 4.6 if e["walk"] > 0 else 0.8)

    # ---------------------------------------------------------- medians
    if not e["oneway"] and rt in ("avenue", "boulevard") and total > 12:
        mp = G.polyline_trim(pts, 3.0 + cw_a, 3.0 + cw_b)
        if len(mp) >= 2 and G.polyline_length(mp) > 4:
            mys = _ys_for(pts, ys, mp, 3.0 + cw_a)
            half = med * 0.5 - 0.15
            l_ = G.polyline_offset(mp, -half)
            r_ = G.polyline_offset(mp, half)
            top = [y + PAD_H for y in mys]
            _strip(mb_struct, l_, r_, top, top, "grass" if rt == "boulevard" else "concrete")
            _curb_side(mb_struct, l_, mys, top, outward_sign=-1)
            _curb_side(mb_struct, r_, mys, top, outward_sign=1)
            _strip(mb_col["sidewalk"], l_, r_, top, top, "concrete")

    # ---------------------------------------------------------- bridges
    if bridge_any:
        _bridge_structure(e, pts, ys, mb_struct, mb_col)


def _ys_for(pts, ys, sub, offset_start):
    """Heights for a trimmed sub-polyline (arc length offset `offset_start`)."""
    out = []
    acc = offset_start
    for i, p in enumerate(sub):
        if i > 0:
            acc += G.dist(sub[i - 1], p)
        out.append(_y_along(pts, ys, acc))
    return out


def _y_along(pts, ys, s):
    acc = 0.0
    for i in range(len(pts) - 1):
        l = G.dist(pts[i], pts[i + 1])
        if acc + l >= s and l > 1e-6:
            return ys[i] + (ys[i + 1] - ys[i]) * (s - acc) / l
        acc += l
    return ys[-1]


def _curb_side(mb, line, ybot, ytop, outward_sign):
    for i in range(len(line) - 1):
        a, b = line[i], line[i + 1]
        d = G.norm(G.sub(b, a))
        n = G.mul(G.right(d), outward_sign)
        mb.face([_p3(a, ybot[i]), _p3(b, ybot[i + 1]), _p3(b, ytop[i + 1]), _p3(a, ytop[i])],
                [(0, 0), (G.dist(a, b), 0), (G.dist(a, b), PAD_H), (0, PAD_H)], "curb", up=(n[0], 0, n[1]))


def _crosswalk(mb, pts, ys, hw, end):
    """Zebra stripes 0.6 m wide, 3 m long in the 4 m band next to the intersection."""
    if end == 0:
        p, d = G.polyline_point_at(pts, 0.5)
        y = ys[0]
    else:
        L = G.polyline_length(pts)
        p, d = G.polyline_point_at(pts, L - 3.5)
        y = ys[-1]
    n = G.right(d)
    y += MARK_Y
    k = -hw + 0.6
    while k < hw - 0.5:
        a = G.add(p, G.mul(n, k))
        b = G.add(p, G.mul(n, k + 0.6))
        c = G.add(b, G.mul(d, 3.0))
        dd = G.add(a, G.mul(d, 3.0))
        mb.face([_p3(a, y), _p3(b, y), _p3(c, y), _p3(dd, y)], None, "road_marking", WHITE, up=(0, 1, 0))
        k += 1.2


def _stop_line(mb, pts, ys, e, end, setback):
    L = G.polyline_length(pts)
    if L < setback + 1:
        return
    if end == 1:
        p, d = G.polyline_point_at(pts, L - setback)
        y = ys[-1]
        side_from, side_to = (0.0, e["hw"]) if not e["oneway"] else (-e["hw"], e["hw"])
    else:
        p, d = G.polyline_point_at(pts, setback)
        d = G.mul(d, -1)
        y = ys[0]
        side_from, side_to = (0.0, e["hw"])
    n = G.right(d)
    if not e["oneway"]:
        side_from = e["median"] * 0.5
    a = G.add(p, G.mul(n, side_from))
    b = G.add(p, G.mul(n, side_to - 0.4))
    t = G.mul(d, 0.25)
    y += MARK_Y
    mb.face([_p3(G.sub(a, t), y), _p3(G.sub(b, t), y), _p3(G.add(b, t), y), _p3(G.add(a, t), y)],
            None, "road_marking", WHITE, up=(0, 1, 0))


def _bridge_structure(e, pts, ys, mb, mb_col):
    """Girder, railings and bridge sidewalks for surface bridges (canal crossings)."""
    br = e["bridge"]
    full = [tuple(p) for p in e["pts"]]
    fys = list(e["ys"])
    hw = e["hw"]
    walk = e["walk"]
    i = 0
    n = len(full)
    while i < n:
        if not br[i]:
            i += 1
            continue
        j = i
        while j < n and br[j]:
            j += 1
        a0 = max(0, i - 2)
        a1 = min(n, j + 2)
        seg = full[a0:a1]
        sys_ = fys[a0:a1]
        if len(seg) < 2:
            i = j
            continue
        w = hw + walk
        l_ = G.polyline_offset(seg, -w)
        r_ = G.polyline_offset(seg, w)
        bot = [y - 1.4 for y in sys_]
        # underside + girder sides
        _strip(mb, r_, l_, bot, bot, "bridge_concrete", up=(0, -1, 0))
        _strip(mb, l_, l_, bot, sys_, "bridge_concrete", up=None)
        for k in range(len(seg) - 1):
            for line, sgn in ((l_, -1), (r_, 1)):
                a, b = line[k], line[k + 1]
                d = G.norm(G.sub(b, a))
                nn = G.mul(G.right(d), sgn)
                mb.face([_p3(a, bot[k]), _p3(b, bot[k + 1]), _p3(b, sys_[k + 1] + PAD_H), _p3(a, sys_[k] + PAD_H)],
                        None, "bridge_concrete", up=(nn[0], 0, nn[1]))
        # sidewalks on the bridge
        if walk > 0:
            for sgn in (-1, 1):
                inner = G.polyline_offset(seg, sgn * hw)
                outer = G.polyline_offset(seg, sgn * w)
                top = [y + PAD_H for y in sys_]
                if sgn < 0:
                    _strip(mb, outer, inner, top, top, "sidewalk")
                    _strip(mb_col["sidewalk"], outer, inner, top, top, "sidewalk")
                else:
                    _strip(mb, inner, outer, top, top, "sidewalk")
                    _strip(mb_col["sidewalk"], inner, outer, top, top, "sidewalk")
                _curb_side(mb, inner, sys_, top, outward_sign=-sgn)
        # railings
        for sgn in (-1, 1):
            line = G.polyline_offset(seg, sgn * (w - 0.15))
            path = [(p[0], y + PAD_H, p[1]) for p, y in zip(line, sys_)]
            sweep(mb, [(p[0], p[1] + 1.05, p[2]) for p in path], [(-0.05, -0.06), (0.05, -0.06), (0.05, 0.06), (-0.05, 0.06)],
                  "metal", closed_profile=True)
            sweep(mb, path, [(-0.12, 0.0), (0.12, 0.0), (0.12, 0.35), (-0.12, 0.35)], "bridge_concrete", closed_profile=True)
            L = G.polyline_length(line)
            s = 0.0
            while s < L:
                p, d = G.polyline_point_at(line, s)
                y = _y_along(line, sys_, s) + PAD_H
                box(mb, (p[0], y + 0.7, p[1]), (0.06, 0.7, 0.06), "metal")
                s += 1.6
        i = j


# ============================================================== intersections
def build_node(plan, n, mb_ground, mb_struct, mb_col):
    poly = [tuple(p) for p in n["poly"]]
    if len(poly) < 3:
        return
    y = n["y"]
    if n["level"] == "elevated":
        # elevated junction deck
        cap_polygon(mb_ground, poly, y, "road_asphalt")
        cap_polygon(mb_col["road"], poly, y, "road_asphalt")
        cap_polygon(mb_struct, poly, y - 1.8, "bridge_concrete", up=False)
        return
    cap_polygon(mb_ground, poly, y, "road_asphalt")
    cap_polygon(mb_col["road"], poly, y, "road_asphalt")
    if n["kind"] == "roundabout":
        R = n["radius"] * 0.45
        c = tuple(n["pos"])
        ring = G.arc(c, R, 0, 2 * math.pi, 28)[:-1]
        top = y + PAD_H * 2
        cap_polygon(mb_struct, ring, top, "grass")
        for i in range(len(ring)):
            a, b = ring[i], ring[(i + 1) % len(ring)]
            out = G.norm(G.sub(G.lerp(a, b, 0.5), c))
            mb_struct.face([_p3(a, y), _p3(b, y), _p3(b, top), _p3(a, top)], None, "curb", up=(out[0], 0, out[1]))
        cap_polygon(mb_col["sidewalk"], ring, top, "grass")


# ============================================================== elevated roads
def build_elevated_edge(plan, e, mb_ground, mb_mark, mb_struct, mb_col, terrain):
    pts = [tuple(p) for p in e["trim_pts"]]
    ys = list(e["trim_ys"])
    if len(pts) < 2:
        return
    # densify so decks follow curves & heights
    dense = G.polyline_resample(pts, 6.0) if G.polyline_length(pts) > 12 else pts
    dys = _ys_for(pts, ys, dense, 0.0)
    hw = e["hw"]
    rt = e["rtype"]
    # tunnel flags along dense points (from the full polyline flags)
    tun = [_flag_at(e, p) for p in dense]
    left = G.polyline_offset(dense, -hw)
    right = G.polyline_offset(dense, hw)
    _strip(mb_ground, left, right, dys, dys, "road_asphalt", lateral=(-hw, hw))
    _strip(mb_col["road"], left, right, dys, dys, "road_asphalt")
    # deck underside + edges (skip where the road lies on terrain / in tunnel)
    depth = 1.8
    bot = [y - depth for y in dys]
    for k in range(len(dense) - 1):
        ground_here = terrain(dense[k]) > dys[k] - 1.0 and terrain(dense[k + 1]) > dys[k + 1] - 1.0
        if tun[k] and tun[k + 1]:
            continue
        a_l, b_l, a_r, b_r = left[k], left[k + 1], right[k], right[k + 1]
        if not ground_here:
            mb_struct.face([_p3(a_r, bot[k]), _p3(a_l, bot[k]), _p3(b_l, bot[k + 1]), _p3(b_r, bot[k + 1])], None,
                           "bridge_concrete", up=(0, -1, 0))
        for (a, b, sgn) in ((a_l, b_l, -1), (a_r, b_r, 1)):
            d = G.norm(G.sub(b, a))
            nn = G.mul(G.right(d), sgn)
            yb0 = bot[k] if not ground_here else min(bot[k], terrain(a) - 0.3)
            yb1 = bot[k + 1] if not ground_here else min(bot[k + 1], terrain(b) - 0.3)
            mb_struct.face([_p3(a, yb0), _p3(b, yb1), _p3(b, dys[k + 1]), _p3(a, dys[k])],
                           [(0, 0), (G.dist(a, b), 0), (G.dist(a, b), depth), (0, depth)],
                           "bridge_concrete", up=(nn[0], 0, nn[1]))
    # jersey barriers on both edges (and median for two-way)
    prof = [(-0.3, 0.0), (0.3, 0.0), (0.12, 0.3), (0.1, 0.85), (-0.1, 0.85), (-0.12, 0.3)]
    for sgn in (-1, 1):
        line = G.polyline_offset(dense, sgn * (hw - 0.3))
        path = [(p[0], y, p[1]) for p, y in zip(line, dys)]
        _sweep_split(mb_struct, path, prof, tun, "barrier")
        _sweep_split(mb_col["building"], path, [(-0.3, 0), (0.3, 0), (0.1, 0.9), (-0.1, 0.9)], tun, "barrier")
    if not e["oneway"]:
        path = [(p[0], y, p[1]) for p, y in zip(dense, dys)]
        _sweep_split(mb_struct, path, prof, [False] * len(dense), "barrier")
        _sweep_split(mb_col["building"], path, [(-0.3, 0), (0.3, 0), (0.1, 0.9), (-0.1, 0.9)], [False] * len(dense), "barrier")
    # markings
    lanes, lw, med = e["lanes"], e["lw"], e["median"]
    if e["oneway"]:
        for i in range(1, lanes):
            _dashes(mb_mark, dense, dys, -lanes * lw * 0.5 + i * lw, 0.14, 3.0, 9.0, WHITE)
        _dashes(mb_mark, dense, dys, lanes * lw * 0.5, 0.15, 1, 0, WHITE)
        _dashes(mb_mark, dense, dys, -lanes * lw * 0.5, 0.15, 1, 0, YELLOW)
    else:
        for i in range(1, lanes):
            for sgn in (-1, 1):
                _dashes(mb_mark, dense, dys, sgn * (med * 0.5 + i * lw), 0.14, 3.0, 9.0, WHITE)
        for sgn in (-1, 1):
            _dashes(mb_mark, dense, dys, sgn * (med * 0.5 + lanes * lw), 0.15, 1, 0, WHITE)
            _dashes(mb_mark, dense, dys, sgn * (med * 0.5 + 0.2), 0.15, 1, 0, YELLOW)
    # pillars
    L = G.polyline_length(dense)
    s = 16.0
    spacing = 34.0
    surface_edges = plan["_surface_index"]
    while s < L - 5:
        p, d = G.polyline_point_at(dense, s)
        y = _y_along(dense, dys, s)
        g = terrain(p)
        if y - g > 3.5 and not _flag_at(e, p):
            if not _on_surface_road(surface_edges, p, 2.5) and g > -1.2:
                _pillar(mb_struct, mb_col, p, d, y - depth, g, hw)
            elif g <= -1.2:
                # in water: taller pier down to the bed
                _pillar(mb_struct, mb_col, p, d, y - depth, g, hw)
        s += spacing
    # tunnel shell
    if any(tun):
        _tunnel(mb_struct, mb_col, dense, dys, tun, hw)


def _flag_at(e, p):
    best = None
    for q, f in zip(e["pts"], e["tunnel"]):
        d = (q[0] - p[0]) ** 2 + (q[1] - p[1]) ** 2
        if best is None or d < best[0]:
            best = (d, f)
    return bool(best and best[1])


def _sweep_split(mb, path, prof, skip, mat):
    run = []
    for p, s in zip(path, skip):
        if s:
            if len(run) >= 2:
                sweep(mb, run, prof, mat, closed_profile=True)
            run = []
        else:
            run.append(p)
    if len(run) >= 2:
        sweep(mb, run, prof, mat, closed_profile=True)


def _on_surface_road(index, p, margin):
    key = (int((p[0] + 1600) // 100), int((p[1] + 1600) // 100))
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            for (a, b, hw) in index.get((key[0] + dx, key[1] + dz), []):
                d, _ = G.point_seg_dist(p, a, b)
                if d < hw + margin:
                    return True
    return False


def _pillar(mb, mb_col, p, d, top_y, ground_y, hw):
    """T-shaped pier: column(s) + cap beam."""
    n = G.right(d)
    ang = math.atan2(-d[0], -d[1])
    cap_w = hw * 2 - 1.0
    box(mb, (p[0], top_y - 0.6, p[1]), (cap_w, 1.2, 2.0), "bridge_concrete", rot_y=ang, bottom=True)
    cols = [0.0] if hw < 9 else [-hw * 0.45, hw * 0.45]
    for off in cols:
        c = G.add(p, G.mul(n, off))
        h = top_y - 1.2 - ground_y + 0.5
        if h > 0.5:
            cylinder(mb, (c[0], ground_y - 0.5, c[1]), 0.85, h, "bridge_concrete", segs=12)
            box(mb_col["building"], (c[0], ground_y - 0.5 + h * 0.5, c[1]), (1.6, h, 1.6), "bridge_concrete")


def _tunnel(mb, mb_col, dense, dys, tun, hw):
    """Arched tunnel tube with tiled walls, ceiling light strip and portal faces."""
    R = hw + 1.0
    prof = []
    for k in range(13):
        a = math.pi * k / 12
        prof.append((math.cos(a) * R, math.sin(a) * R * 0.55 + 1.2))
    prof = [(R, 0.0)] + prof + [(-R, 0.0)]
    prof_in = list(reversed(prof))  # inward facing
    runs = []
    run = []
    for i, t in enumerate(tun):
        if t:
            run.append(i)
        elif run:
            runs.append(run)
            run = []
    if run:
        runs.append(run)
    for r in runs:
        i0 = max(0, r[0] - 1)
        i1 = min(len(dense) - 1, r[-1] + 1)
        path = [(dense[i][0], dys[i], dense[i][1]) for i in range(i0, i1 + 1)]
        sweep(mb, path, prof_in, "tunnel_wall")
        sweep(mb_col["building"], path, prof_in, "tunnel_wall")
        # light strip
        sweep(mb, [(p[0], p[1] + R * 0.55 + 1.1, p[2]) for p in path], [(-0.3, 0), (0.3, 0)], "light_emissive")
        # portals
        for idx, sgn in ((i0, -1), (i1, 1)):
            p = dense[idx]
            if idx + sgn < 0 or idx + sgn >= len(dense):
                continue
            d = G.norm(G.sub(dense[min(len(dense) - 1, idx + 1)], dense[max(0, idx - 1)]))
            ang = math.atan2(-d[0], -d[1])
            y = dys[idx]
            n = G.right(d)
            # frame around the opening
            for off, w in ((-(R + 4.0), 8.0), ((R + 4.0), 8.0)):
                c = G.add(p, G.mul(n, off))
                box(mb, (c[0], y + 5.0, c[1]), (w, 12.0, 1.5), "bridge_concrete", rot_y=ang)
                box(mb_col["building"], (c[0], y + 5.0, c[1]), (w, 12.0, 1.5), "bridge_concrete", rot_y=ang)
            box(mb, (p[0], y + R * 0.55 + 1.2 + 4.0, p[1]), (2 * R + 16.0, 8.0, 1.5), "bridge_concrete", rot_y=ang)


# ============================================================== blocks & pads
def build_block(plan, b, mb_ground, mb_struct, mb_col, mb_detail):
    kind = b["kind"]
    if kind in ("natural", "water"):
        return
    poly = [tuple(p) for p in b["poly"]]
    if len(poly) < 3:
        return
    y = b["y"]
    top = y + PAD_H
    walk = {"downtown": 4.5, "financial": 4.5, "shopping": 4.5, "entertainment": 4.5, "oldtown": 3.5,
            "residential": 3.5, "suburbs": 3.0, "industrial": 3.0, "construction": 3.0, "marina": 4.0,
            "harbor": 2.5, "airport": 2.5, "park": 4.0, "canal": 4.0, "beach": 4.0}.get(b["style"], 3.5)
    inner = G._poly_inset_miter(G.ensure_ccw(poly), walk)
    if not inner or len(inner) != len(poly):
        inner = None
    # curb faces (outward = towards road)
    ring_poly = G.ensure_ccw(poly)
    n = len(ring_poly)
    for i in range(n):
        a, c = ring_poly[i], ring_poly[(i + 1) % n]
        e_ = G.norm(G.sub(c, a))
        out = G.left(e_)
        l = G.dist(a, c)
        mb_struct.face([_p3(a, y), _p3(c, y), _p3(c, top), _p3(a, top)], [(0, 0), (l, 0), (l, PAD_H), (0, PAD_H)],
                       "curb", up=(out[0], 0, out[1]))
    if inner:
        inner = G.ensure_ccw(inner)
        # sidewalk ring
        for i in range(n):
            a, c = ring_poly[i], ring_poly[(i + 1) % n]
            ia, ic = inner[i], inner[(i + 1) % n]
            mb_ground.face([_p3(a, top), _p3(c, top), _p3(ic, top), _p3(ia, top)], None, "sidewalk", up=(0, 1, 0))
            mb_col["sidewalk"].face([_p3(a, top), _p3(c, top), _p3(ic, top), _p3(ia, top)], None, "sidewalk", up=(0, 1, 0))
        # curb top edge strip (concrete kerb stone 0.25 m)
        kerb = G._poly_inset_miter(ring_poly, 0.28)
        if kerb and len(kerb) == n:
            kerb = G.ensure_ccw(kerb)
            for i in range(n):
                a, c = ring_poly[i], ring_poly[(i + 1) % n]
                ka, kc = kerb[i], kerb[(i + 1) % n]
                mb_struct.face([_p3(a, top + 0.004), _p3(c, top + 0.004), _p3(kc, top + 0.004), _p3(ka, top + 0.004)],
                               None, "curb", up=(0, 1, 0))
    if kind in ("park", "canal"):
        return  # interior is terrain
    if not inner:
        cap_polygon(mb_ground, poly, top, "sidewalk")
        cap_polygon(mb_col["sidewalk"], poly, top, "sidewalk")
        return
    base = {"residential": "grass", "suburbs": "grass", "industrial": "concrete", "harbor": "concrete",
            "airport": "concrete", "construction": "dirt"}.get(b["style"], "sidewalk")
    cap_polygon(mb_ground, inner, top, base)
    cap_polygon(mb_col["grass" if base == "grass" else "sidewalk"], inner, top, base)


def build_lot(plan, lot, mb_ground, mb_mark, mb_detail):
    kind = lot["kind"]
    poly = [tuple(p) for p in lot["poly"]]
    y = lot["y"] + 0.02
    mat = {"parking_lot": "parking", "plaza": "sidewalk", "landmark": "sidewalk", "pocket_park": "grass",
           "park_small": "grass", "house_yard": "grass", "yard_industrial": "concrete", "yard": "grass",
           "construction": "dirt"}.get(kind)
    if mat is None:
        return
    cap_polygon(mb_ground, poly, y, mat)
    if kind == "house_yard":
        # driveway from the street to the garage side
        front = lot.get("front", (0.0, 1.0))
        c = G.poly_centroid(poly)
        side = G.right(G.mul(front, -1))
        us = [G.dot(G.sub(p, c), side) for p in poly]
        vs = [G.dot(G.sub(p, c), G.mul(front, -1)) for p in poly]
        u = max(us) - 3.0
        v0 = min(vs)
        v1 = v0 + 9.0
        pts = []
        for (uu, vv) in ((u - 1.6, v0), (u + 1.6, v0), (u + 1.6, v1), (u - 1.6, v1)):
            q = G.add(c, G.add(G.mul(side, uu), G.mul(G.mul(front, -1), vv)))
            pts.append(_p3(q, y + 0.012))
        mb_ground.face(pts, None, "concrete", up=(0, 1, 0))


# ============================================================== areas
def build_area(plan, a, mb_ground, mb_mark, mb_struct, mb_col, terrain):
    k = a["kind"]
    if k == "runway" or k == "taxiway" or k == "apron":
        x0, z0, x1, z1 = a["rect"]
        y = 0.06 if k != "apron" else 0.05
        mat = "road_asphalt" if k != "apron" else "concrete"
        pts = [(x0, y, z0), (x1, y, z0), (x1, y, z1), (x0, y, z1)]
        mb_ground.face(pts, None, mat, up=(0, 1, 0))
        mb_col["road"].face(pts, None, mat, up=(0, 1, 0))
        if k == "runway":
            cx = (x0 + x1) * 0.5
            z = z0 + 40
            while z < z1 - 40:
                mb_mark.face([(cx - 0.45, y + MARK_Y, z), (cx + 0.45, y + MARK_Y, z), (cx + 0.45, y + MARK_Y, z + 30),
                              (cx - 0.45, y + MARK_Y, z + 30)], None, "road_marking", WHITE, up=(0, 1, 0))
                z += 60
            for zz in (z0 + 6, z1 - 36):
                for i in range(8):
                    xx = x0 + 4 + i * 5.6
                    mb_mark.face([(xx, y + MARK_Y, zz), (xx + 1.8, y + MARK_Y, zz), (xx + 1.8, y + MARK_Y, zz + 30),
                                  (xx, y + MARK_Y, zz + 30)], None, "road_marking", WHITE, up=(0, 1, 0))
            for xx in (x0 + 1.0, x1 - 1.9):
                mb_mark.face([(xx, y + MARK_Y, z0), (xx + 0.9, y + MARK_Y, z0), (xx + 0.9, y + MARK_Y, z1),
                              (xx, y + MARK_Y, z1)], None, "road_marking", WHITE, up=(0, 1, 0))
        elif k == "taxiway":
            cx = (x0 + x1) * 0.5
            cz = (z0 + z1) * 0.5
            if (x1 - x0) < (z1 - z0):
                mb_mark.face([(cx - 0.15, y + MARK_Y, z0), (cx + 0.15, y + MARK_Y, z0), (cx + 0.15, y + MARK_Y, z1),
                              (cx - 0.15, y + MARK_Y, z1)], None, "road_marking", YELLOW, up=(0, 1, 0))
            else:
                mb_mark.face([(x0, y + MARK_Y, cz - 0.15), (x1, y + MARK_Y, cz - 0.15), (x1, y + MARK_Y, cz + 0.15),
                              (x0, y + MARK_Y, cz + 0.15)], None, "road_marking", YELLOW, up=(0, 1, 0))
    elif k in ("parking_area",):
        poly = [tuple(p) for p in a["poly"]]
        y = max(terrain(G.poly_centroid(poly)), 0.0) + 0.05
        cap_polygon(mb_ground, poly, y, "parking")
        cap_polygon(mb_col["road"], poly, y, "parking")
    elif k == "harbor_yard":
        pass  # harbour land is concrete terrain
    elif k in ("boardwalk", "pier", "dock", "breakwater"):
        pts = [tuple(p) for p in a["pts"]]
        w = a.get("width", 4.0) * 0.5
        if k == "boardwalk":
            ys = [max(terrain(p), 0.0) + 0.35 for p in pts]
        elif k == "pier":
            ys = [2.2 for p in pts]
        elif k == "dock":
            ys = [-0.55 for p in pts]
        else:
            ys = [1.2 for p in pts]
        mat = "wood_planks" if k != "breakwater" else "rock"
        l_ = G.polyline_offset(pts, -w)
        r_ = G.polyline_offset(pts, w)
        _strip(mb_struct, l_, r_, ys, ys, mat, lateral=(-w, w))
        _strip(mb_col["wood" if mat == "wood_planks" else "building"], l_, r_, ys, ys, mat)
        thick = 0.3 if k != "breakwater" else 4.0
        bot = [y - thick for y in ys]
        _strip(mb_struct, r_, l_, bot, bot, mat, up=(0, -1, 0))
        for line, sgn in ((l_, -1), (r_, 1)):
            for i in range(len(line) - 1):
                aa, bb = line[i], line[i + 1]
                dd = G.norm(G.sub(bb, aa))
                nn = G.mul(G.right(dd), sgn)
                mb_struct.face([_p3(aa, bot[i]), _p3(bb, bot[i + 1]), _p3(bb, ys[i + 1]), _p3(aa, ys[i])], None, mat,
                               up=(nn[0], 0, nn[1]))
        if k in ("pier", "dock", "boardwalk"):
            L = G.polyline_length(pts)
            s = 1.0
            while s < L:
                p, d = G.polyline_point_at(pts, s)
                n = G.right(d)
                y = _y_along(pts, ys, s)
                g = terrain(p)
                for sgn in (-1, 1):
                    q = G.add(p, G.mul(n, sgn * (w - 0.3)))
                    h = y - min(g, -6.0 if k != "boardwalk" else g) + 0.1
                    cylinder(mb_struct, (q[0], y - h, q[1]), 0.18 if k != "pier" else 0.3, h, "wood", segs=6)
                s += 4.0 if k != "pier" else 6.0
            if k == "pier":
                for sgn in (-1, 1):
                    line = G.polyline_offset(pts, sgn * (w - 0.1))
                    sweep(mb_struct, [(p[0], y + 1.0, p[1]) for p, y in zip(line, ys)],
                          [(-0.06, -0.06), (0.06, -0.06), (0.06, 0.06), (-0.06, 0.06)], "wood", closed_profile=True)
    elif k == "basin":
        x0, z0, x1, z1 = a["rect"]
        _quay_wall(mb_struct, mb_col, [(x0, z1), (x0, z0), (x1, z0), (x1, z1)])
    elif k == "quay":
        _quay_wall(mb_struct, mb_col, [(a["x1"], a["z"]), (a["x0"], a["z"])], outward=True)
    elif k == "field":
        poly = [tuple(p) for p in a["poly"]]
        pts = [(p[0], terrain(p) + 0.05, p[1]) for p in poly]
        mb_ground.face(pts, [(p[0] * 0.25, p[2] * 0.25) for p in pts], "crop", up=(0, 1, 0))


def _quay_wall(mb, mb_col, pts, outward=False):
    """Vertical concrete wall from y=0.2 down to -8 along a polyline (water side = right)."""
    for i in range(len(pts) - 1):
        a, b = pts[i], pts[i + 1]
        l = G.dist(a, b)
        d = G.norm(G.sub(b, a))
        n = G.right(d)
        mb.face([_p3(a, -8.0), _p3(b, -8.0), _p3(b, 0.25), _p3(a, 0.25)], [(0, -8), (l, -8), (l, 0.25), (0, 0.25)],
                "concrete", up=(n[0], 0, n[1]))
        mb_col["building"].face([_p3(a, -8.0), _p3(b, -8.0), _p3(b, 0.25), _p3(a, 0.25)], None, "concrete",
                                up=(n[0], 0, n[1]))
        # coping stone
        a2 = G.add(a, G.mul(n, -1.2))
        b2 = G.add(b, G.mul(n, -1.2))
        mb.face([_p3(a2, 0.25), _p3(b2, 0.25), _p3(b, 0.25), _p3(a, 0.25)], None, "concrete", up=(0, 1, 0))
        # bollards
        s = 6.0
        while s < l - 2:
            p = G.add(a, G.mul(d, s))
            p = G.add(p, G.mul(n, -0.6))
            cylinder(mb, (p[0], 0.25, p[1]), 0.22, 0.6, "metal_dark", segs=8, r_top=0.26)
            s += 18.0
