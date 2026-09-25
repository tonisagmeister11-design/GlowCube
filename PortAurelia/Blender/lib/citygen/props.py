"""Street furniture, vegetation and lot dressing placement.

Every prop is (type, x, y, z, rot_y, variant). rot_y follows Godot's convention:
the prop's local -Z (Blender +Y) points to world direction (-sin r, -cos r).
"""
import math

from . import geom as G
from .roads import ROAD_TYPES, PARK_W
from .terrain import SEA_LEVEL
from .blocks import PAD_H

DENSE = {"downtown", "financial", "shopping", "entertainment", "oldtown"}
TREE_STREETS = {"residential": 15.0, "suburbs": 13.0, "shopping": 18.0, "oldtown": 20.0,
                "downtown": 24.0, "financial": 22.0, "entertainment": 26.0, "marina": 16.0}


def facing_rot(f):
    return math.atan2(-f[0], -f[1])


class PropPlacer:
    def __init__(self, net, terrain, rng, bb, areas):
        self.net = net
        self.T = terrain
        self.rng = rng
        self.bb = bb
        self.areas = areas
        self.props = []
        self.signals = []
        self.lot_parking = []

    def add(self, t, x, y, z, rot=0.0, var=0):
        self.props.append((t, round(x, 2), round(y, 2), round(z, 2), round(rot, 3), var))

    # ------------------------------------------------------------ roads
    def roads(self):
        rng = self.rng
        net = self.net
        for e in net.edges:
            t = ROAD_TYPES[e.rtype]
            pts, ys, t0, t1 = net.edge_trimmed(e)
            L = G.polyline_length(pts)
            if L < 8:
                continue
            district = self._district(pts[len(pts) // 2])
            if e.level == "elevated":
                self._highway_props(e, pts, ys, t0, L)
                continue
            if e.walk > 0:
                self._street_props(e, pts, ys, t0, L, district)
            else:
                # rural / hill roads: sparse wooden lamps and guard posts
                s = 20.0
                side = 1
                while s < L - 10:
                    p, d = G.polyline_point_at(pts, s)
                    if any(e.bridge):
                        pass
                    q = G.add(p, G.mul(G.right(d), side * (e.hw + 1.6)))
                    y = e.y_at(t0 + s)
                    if self.T.natural_height(*q) > SEA_LEVEL + 0.5:
                        self.add("lamp_wood", q[0], y, q[1], facing_rot(G.mul(G.right(d), -side)))
                    s += 70.0
                    side = -side
        self._signals()

    def _district(self, p):
        from .districts import district_at, style_of
        return style_of(district_at(*p))

    def _street_props(self, e, pts, ys, t0, L, district):
        rng = self.rng
        walk = e.walk
        # street lamps (staggered)
        spacing = 30.0 if district in DENSE else 36.0
        double = e.rtype in ("avenue", "boulevard")
        for side in (1, -1):
            s = 10.0 + (spacing * 0.5 if side < 0 else 0.0)
            while s < L - 8.0:
                p, d = G.polyline_point_at(pts, s)
                n = G.mul(G.right(d), side)
                q = G.add(p, G.mul(n, e.hw + 0.65))
                y = e.y_at(t0 + s) + PAD_H
                if e.bridge and e.bridge[min(len(e.bridge) - 1, int(len(e.bridge) * s / max(L, 1)))]:
                    self.add("lamp_street", q[0], y - PAD_H, q[1], facing_rot(G.mul(n, -1)))
                else:
                    self.add("lamp_street", q[0], y, q[1], facing_rot(G.mul(n, -1)))
                s += spacing
        # boulevard median palms + lamps
        if e.rtype == "boulevard":
            s = 12.0
            while s < L - 10.0:
                p, d = G.polyline_point_at(pts, s)
                y = e.y_at(t0 + s) + PAD_H
                self.add("palm_tall", p[0], y, p[1], rng.uniform(0, 6.28), rng.randint(0, 3))
                s += 16.0
            self._median_strip(e, pts, t0, L)
        # street trees
        sp = TREE_STREETS.get(district)
        if sp and not any(e.bridge):
            for side in (1, -1):
                s = 18.0 + rng.uniform(0, 6)
                while s < L - 12.0:
                    p, d = G.polyline_point_at(pts, s)
                    n = G.mul(G.right(d), side)
                    q = G.add(p, G.mul(n, e.hw + min(1.4, walk * 0.35)))
                    y = e.y_at(t0 + s) + PAD_H
                    if district in ("marina",) or (district == "shopping" and rng.chance(0.4)):
                        tt = "palm_short"
                    elif district in ("suburbs", "residential"):
                        tt = rng.choice(["tree_round", "tree_oak", "palm_tall", "tree_round"])
                    else:
                        tt = rng.choice(["tree_round", "tree_cypress", "tree_round"])
                    self.add(tt, q[0], y, q[1], rng.uniform(0, 6.28), rng.randint(0, 3))
                    s += sp * rng.uniform(0.85, 1.3)
        # hydrants, trash cans, benches, newspaper boxes, meters
        for side in (1, -1):
            s = rng.uniform(15, 40)
            while s < L - 10.0:
                p, d = G.polyline_point_at(pts, s)
                n = G.mul(G.right(d), side)
                q = G.add(p, G.mul(n, e.hw + 0.55))
                y = e.y_at(t0 + s) + PAD_H
                r = rng.random()
                if r < 0.35:
                    self.add("hydrant", q[0], y, q[1], facing_rot(G.mul(n, -1)))
                elif r < 0.65:
                    q2 = G.add(p, G.mul(n, e.hw + walk - 0.6))
                    self.add("trash_can", q2[0], y, q2[1], facing_rot(G.mul(n, -1)))
                elif r < 0.85 and district in DENSE | {"residential", "marina"}:
                    q2 = G.add(p, G.mul(n, e.hw + walk - 0.7))
                    self.add("bench", q2[0], y, q2[1], facing_rot(n) + math.pi)
                elif district in DENSE:
                    self.add("newspaper_box", q[0], y, q[1], facing_rot(G.mul(n, -1)))
                s += rng.uniform(25, 55)
        # bus stops on avenues
        if e.rtype in ("avenue", "boulevard") and L > 70:
            for side in (1, -1):
                if rng.chance(0.5):
                    s = L * rng.uniform(0.35, 0.65)
                    p, d = G.polyline_point_at(pts, s)
                    n = G.mul(G.right(d), side)
                    q = G.add(p, G.mul(n, e.hw + walk - 1.2))
                    y = e.y_at(t0 + s) + PAD_H
                    self.add("bus_stop", q[0], y, q[1], facing_rot(G.mul(n, -1)))
        # parking meters along dense parking streets
        if e.parking and district in ("downtown", "financial", "oldtown", "shopping"):
            for side in (1, -1):
                s = 12.0
                while s < L - 12.0:
                    p, d = G.polyline_point_at(pts, s)
                    n = G.mul(G.right(d), side)
                    q = G.add(p, G.mul(n, e.hw + 0.35))
                    self.add("parking_meter", q[0], e.y_at(t0 + s) + PAD_H, q[1], facing_rot(G.mul(n, -1)))
                    s += 13.0

    def _median_strip(self, e, pts, t0, L):
        # grass median strip is built by the road mesh builder; add bushes
        s = 20.0
        while s < L - 12:
            p, d = G.polyline_point_at(pts, s)
            y = e.y_at(t0 + s) + PAD_H
            self.add("bush", p[0] + d[0] * 8, y, p[1] + d[1] * 8, self.rng.uniform(0, 6.28), self.rng.randint(0, 2))
            s += 16.0

    def _highway_props(self, e, pts, ys, t0, L):
        if e.rtype == "ramp":
            s = 25.0
            while s < L - 10:
                p, d = G.polyline_point_at(pts, s)
                q = G.add(p, G.mul(G.right(d), e.hw - 0.3))
                self.add("lamp_highway", q[0], e.y_at(t0 + s), q[1], facing_rot(G.mul(G.right(d), -1)))
                s += 45.0
            return
        s = 20.0
        tun = e.tunnel
        while s < L - 10:
            p, d = G.polyline_point_at(pts, s)
            idx = min(len(tun) - 1, int(len(tun) * s / max(L, 1.0)))
            if not (tun and tun[idx]):
                self.add("lamp_highway_double", p[0], e.y_at(t0 + s), p[1], facing_rot(d))
            s += 50.0
        # billboards along the ring
        s = 150.0
        while s < L - 60:
            p, d = G.polyline_point_at(pts, s)
            side = 1 if self.rng.chance(0.5) else -1
            q = G.add(p, G.mul(G.right(d), side * (e.hw + 12.0)))
            if self.T.natural_height(*q) > SEA_LEVEL + 1 and not self._near_building(q, 12):
                self.add("billboard", q[0], self.T.height(*q), q[1], facing_rot(G.mul(d, -1)),
                         self.rng.randint(0, 7))
            s += self.rng.uniform(220, 420)

    def _near_building(self, q, r):
        for b in self.bb.buildings:
            c = b["footprint"][0]
            if abs(c[0] - q[0]) < 80 and abs(c[1] - q[1]) < 80:
                if G.point_in_poly(q, b["footprint"]):
                    return True
                x0, z0, x1, z1 = G.poly_bbox(b["footprint"])
                if x0 - r < q[0] < x1 + r and z0 - r < q[1] < z1 + r:
                    return True
        return False

    def _signals(self):
        net = self.net
        for n in net.nodes:
            if n.control not in ("signal", "stop") or n.level != "surface":
                continue
            for eid in n.sorted:
                e = net.edges[eid]
                has_in = (e.b == n.id and e.lanes_f) or (e.a == n.id and e.lanes_b)
                if not has_in:
                    continue
                d = n.dirs[eid]
                t = n.trim[eid]
                setback = 5.2 if e.walk > 0 else 1.5
                pos = G.add(n.pos, G.add(G.mul(d, t + setback), G.mul(G.left(d), e.hw + 0.9)))
                y = n.y + (PAD_H if e.walk > 0 else 0.0)
                rt = ROAD_TYPES[e.rtype]
                arm = (e.hw - rt["median"] * 0.5) if not e.oneway else e.hw * 2.0
                if n.control == "signal":
                    self.signals.append(dict(node=n.id, edge=eid, phase=n.phases.get(eid, 0),
                                             pos=(round(pos[0], 2), round(y, 2), round(pos[1], 2)),
                                             rot=round(facing_rot(G.mul(d, -1)), 4), arm=round(max(2.5, arm), 2)))
                else:
                    self.add("stop_sign", pos[0], y, pos[1], facing_rot(G.mul(d, -1)))

    # ------------------------------------------------------------ lots
    def lots(self):
        rng = self.rng
        for lot in self.bb.lots:
            kind = lot["kind"]
            poly = lot["poly"]
            y = lot["y"]
            c = G.poly_centroid(poly)
            if kind == "parking_lot":
                self._parking_rows(poly, y, lot["id"])
            elif kind in ("plaza", "landmark"):
                inner = G.poly_inset(poly, 3.0)
                if not inner:
                    continue
                self._scatter(inner, y, ["bench", "planter", "tree_round", "lamp_plaza"], 90.0, keep_out=self._bld_polys(lot))
                if kind == "plaza" and abs(G.poly_area(poly)) > 1500 and rng.chance(0.6):
                    if not self._inside_building(c):
                        self.add("fountain", c[0], y, c[1], 0.0)
            elif kind in ("pocket_park", "park_small"):
                inner = G.poly_inset(poly, 2.0)
                if inner:
                    self._scatter(inner, y, ["tree_oak", "tree_round", "bench", "bush", "tree_oak", "lamp_plaza"], 60.0)
            elif kind == "house_yard":
                self._house_yard(lot)
            elif kind == "yard_industrial":
                inner = G.poly_inset(poly, 2.0)
                if inner:
                    self._scatter(inner, y, ["pallet_stack", "barrel", "dumpster", "container", "barrel"], 160.0,
                                  keep_out=self._bld_polys(lot, 3.0))
                    self._fence(poly, y, gap_front=True, lot=lot)
            elif kind == "yard":
                inner = G.poly_inset(poly, 1.0)
                if inner:
                    self._scatter(inner, y, ["tree_round", "bush", "dumpster"], 220.0, keep_out=self._bld_polys(lot, 1.5))
            elif kind == "construction":
                self._fence(poly, y, gap_front=True, lot=lot, ptype="fence_construction")
                cx, cz = c
                self.add("tower_crane", cx + rng.uniform(-10, 10), y, cz + rng.uniform(-10, 10), rng.uniform(0, 6.28))
                inner = G.poly_inset(poly, 3.0)
                if inner:
                    self._scatter(inner, y, ["material_pile", "traffic_cone", "jersey_barrier", "pallet_stack", "container"],
                                  140.0, keep_out=self._bld_polys(lot, 2.0))

    def _bld_polys(self, lot, margin=1.0):
        out = []
        for b in self.bb.buildings:
            if b["block"] == lot["block"]:
                fp = b["footprint"]
                out.append((fp, margin))
        return out

    def _inside_building(self, p):
        for b in self.bb.buildings:
            if G.point_in_poly(p, b["footprint"]):
                return True
        return False

    def _scatter(self, poly, y, types, area_per, keep_out=None):
        rng = self.rng
        area = abs(G.poly_area(poly))
        n = int(area / area_per)
        x0, z0, x1, z1 = G.poly_bbox(poly)
        placed = 0
        tries = 0
        while placed < n and tries < n * 6:
            tries += 1
            p = (rng.uniform(x0, x1), rng.uniform(z0, z1))
            if not G.point_in_poly(p, poly):
                continue
            bad = False
            for (fp, m) in (keep_out or []):
                bx0, bz0, bx1, bz1 = G.poly_bbox(fp)
                if bx0 - m < p[0] < bx1 + m and bz0 - m < p[1] < bz1 + m:
                    bad = True
                    break
            if bad:
                continue
            t = rng.choice(types)
            self.add(t, p[0], y, p[1], rng.uniform(0, 6.28), rng.randint(0, 3))
            placed += 1

    def _fence(self, poly, y, gap_front=False, lot=None, ptype="fence_chain"):
        n = len(poly)
        for i in range(n):
            a, b = poly[i], poly[(i + 1) % n]
            l = G.dist(a, b)
            if l < 3:
                continue
            d = G.norm(G.sub(b, a))
            mid = G.lerp(a, b, 0.5)
            s = 1.5
            while s < l - 1.5:
                if gap_front and abs(s - l * 0.5) < 5.0 and i == 0:
                    s += 3.0
                    continue
                p = G.add(a, G.mul(d, s))
                self.add(ptype, p[0], y, p[1], facing_rot(G.right(d)))
                s += 3.0
            _ = mid

    def _house_yard(self, lot):
        rng = self.rng
        poly = lot["poly"]
        y = lot["y"]
        front = tuple(lot.get("front", (0.0, 1.0)))
        # hedge/fence along the sides and back
        n = len(poly)
        for i in range(n):
            a, b = poly[i], poly[(i + 1) % n]
            e = G.norm(G.sub(b, a))
            outward = G.left(e)
            if G.dot(outward, front) > 0.7:
                # front edge: mailbox
                mid = G.lerp(a, b, 0.2)
                q = G.sub(mid, G.mul(front, 0.8))
                self.add("mailbox", q[0], y, q[1], facing_rot(front))
                continue
            l = G.dist(a, b)
            s = 1.5
            ptype = "fence_wood" if rng.chance(0.6) else "hedge"
            while s < l - 1.0:
                p = G.add(a, G.mul(e, s))
                p = G.sub(p, G.mul(outward, 0.3))
                self.add(ptype, p[0], y, p[1], facing_rot(outward))
                s += 3.0
        # trees in the yard
        inner = G.poly_inset(poly, 2.0)
        if inner:
            blds = self._bld_polys(lot, 2.5)
            self._scatter(inner, y, ["tree_oak", "tree_round", "bush", "palm_short", "bush"], 110.0, keep_out=blds)
            if lot.get("pool"):
                # pool behind the house
                c = G.poly_centroid(inner)
                back = G.add(c, G.mul(front, -6.0))
                ok = all(not G.point_in_poly(back, fp) for fp, _ in blds)
                if ok:
                    self.add("pool", back[0], y, back[1], facing_rot(front))
        # driveway parking spot
        c = G.poly_centroid(poly)
        side = G.right(G.mul(front, -1))
        us = [G.dot(G.sub(p, c), side) for p in poly]
        dp = G.add(c, G.add(G.mul(side, max(us) - 3.0), G.mul(front, 4.0)))
        self.lot_parking.append(dict(pos=(round(dp[0], 2), round(y, 2), round(dp[1], 2)),
                                     dir=(round(-front[0], 3), round(-front[1], 3)), kind="driveway", lot=lot["id"]))

    def _parking_rows(self, poly, y, lot_id):
        c, u, v, hu, hv = G.obb(poly)
        if hv > hu:
            u, hu, hv = v, hv, hu
        v = G.right(u)
        # rows along u: bays 2.7 wide, 5.2 deep, aisles 6.5
        row_pitch = 5.2 * 2 + 6.5
        vv = -hv + 1.5
        bays = 0
        while vv + 5.2 < hv - 1.0:
            for facing in (1, -1):
                row_v = vv + (2.6 if facing == 1 else 5.2 + 6.5 + 2.6)
                if row_v > hv - 2.0:
                    continue
                uu = -hu + 2.5
                while uu < hu - 2.5:
                    p = G.add(c, G.add(G.mul(u, uu), G.mul(v, row_v)))
                    if G.point_in_poly(p, poly):
                        d = G.mul(v, -facing)
                        self.lot_parking.append(dict(pos=(round(p[0], 2), round(y, 2), round(p[1], 2)),
                                                     dir=(round(d[0], 3), round(d[1], 3)), kind="lot", lot=lot_id))
                        bays += 1
                    uu += 2.7
            vv += row_pitch
        # a couple of lamps
        for k in (-0.5, 0.5):
            p = G.add(c, G.mul(u, hu * k))
            self.add("lamp_plaza", p[0], y, p[1], 0.0)

    # ------------------------------------------------------------ nature
    def nature(self):
        rng = self.rng
        T = self.T
        from .districts import district_at
        step = 9.0
        x = -1590.0
        blds = self.bb.buildings
        # spatial hash of building bboxes for fast rejection
        grid = {}
        for b in blds:
            x0, z0, x1, z1 = G.poly_bbox(b["footprint"])
            for gx in range(int((x0 + 1600) // 50), int((x1 + 1600) // 50) + 1):
                for gz in range(int((z0 + 1600) // 50), int((z1 + 1600) // 50) + 1):
                    grid.setdefault((gx, gz), []).append((x0 - 4, z0 - 4, x1 + 4, z1 + 4))
        while x < 1590.0:
            z = -1590.0
            while z < 1590.0:
                px = x + rng.uniform(-4, 4)
                pz = z + rng.uniform(-4, 4)
                did = district_at(px, pz)
                dens = {"hills": 0.34, "luxury": 0.22, "rural": 0.05, "rural_east": 0.08}.get(did, 0.0)
                if dens > 0 and rng.chance(dens):
                    h = T.height(px, pz)
                    if h > SEA_LEVEL + 1.0 and T.road_mask[int((pz + 1600) / T.res), int((px + 1600) / T.res)] < 0.5 \
                            and not self._near_road_mask(px, pz):
                        key = (int((px + 1600) // 50), int((pz + 1600) // 50))
                        if not any(b[0] < px < b[2] and b[1] < pz < b[3] for b in grid.get(key, [])):
                            sl = T.slope(px, pz)
                            if sl > 0.9:
                                self.add("rock", px, h, pz, rng.uniform(0, 6.28), rng.randint(0, 3))
                            else:
                                if did in ("rural", "rural_east"):
                                    t = rng.choice(["tree_oak", "bush", "tree_oak", "rock"])
                                elif did == "luxury":
                                    t = rng.choice(["tree_pine", "tree_cypress", "tree_oak", "bush", "palm_tall"])
                                else:
                                    t = rng.choice(["tree_pine", "tree_pine", "tree_oak", "bush", "bush", "tree_cypress"])
                                self.add(t, px, h - 0.1, pz, rng.uniform(0, 6.28), rng.randint(0, 3))
                z += step
            x += step

    def _near_road_mask(self, x, z):
        T = self.T
        i = int((x + 1600) / T.res)
        j = int((z + 1600) / T.res)
        for dj in (-2, 0, 2):
            for di in (-2, 0, 2):
                jj = min(max(j + dj, 0), T.n - 1)
                ii = min(max(i + di, 0), T.n - 1)
                if T.road_mask[jj, ii] > 0.5:
                    return True
        return False

    def park_block(self, block):
        """Solace Park: lake loop path, trees, benches, lamps."""
        rng = self.rng
        poly = block["poly"]
        inner = G.poly_inset(poly, 4.0)
        if not inner:
            return []
        from .terrain import LAKE_C, LAKE_R
        paths = []
        # loop around the lake
        loop = [(LAKE_C[0] + math.cos(a) * (LAKE_R[0] + 28), LAKE_C[1] + math.sin(a) * (LAKE_R[1] + 22))
                for a in [i * 2 * math.pi / 48 for i in range(49)]]
        paths.append(loop)
        # cross paths
        c = G.poly_centroid(inner)
        x0, z0, x1, z1 = G.poly_bbox(inner)
        paths.append([(x0 + 2, c[1] + 70), (x1 - 2, c[1] + 70)])
        paths.append([(150.0, z0 + 2), (150.0, z1 - 2)])
        paths.append([(-220.0, z0 + 2), (-220.0, z1 - 2)])
        # trees avoiding lake and paths
        n = int(abs(G.poly_area(inner)) / 150)
        placed = 0
        tries = 0
        while placed < n and tries < n * 5:
            tries += 1
            p = (rng.uniform(x0, x1), rng.uniform(z0, z1))
            if not G.point_in_poly(p, inner):
                continue
            e = ((p[0] - LAKE_C[0]) / (LAKE_R[0] + 20)) ** 2 + ((p[1] - LAKE_C[1]) / (LAKE_R[1] + 16)) ** 2
            if e < 1.0:
                continue
            if any(min(G.point_seg_dist(p, pa[i], pa[i + 1])[0] for i in range(len(pa) - 1)) < 4.0 for pa in paths):
                continue
            self.add(rng.choice(["tree_oak", "tree_round", "palm_tall", "tree_oak", "tree_cypress"]),
                     p[0], self.T.height(*p), p[1], rng.uniform(0, 6.28), rng.randint(0, 3))
            placed += 1
        # benches + lamps along paths
        for pa in paths:
            L = G.polyline_length(pa)
            s = 8.0
            while s < L - 4:
                p, d = G.polyline_point_at(pa, s)
                q = G.add(p, G.mul(G.right(d), 2.6))
                self.add("lamp_plaza" if int(s / 25) % 2 == 0 else "bench", q[0], self.T.height(*q), q[1],
                         facing_rot(G.left(d)))
                s += 25.0
        return paths
