"""Blocks, lots and building specifications.

Faces of the surface road graph become blocks. Each block is classified by
district and either subdivided into lots with buildings, or turned into a park,
canal strip, harbour yard, airport field or natural terrain.
"""
import math

from . import geom as G
from .districts import district_at, style_of
from .terrain import SEA_LEVEL

PAD_H = 0.16  # curb height

# lot/building rules per district style
STYLES = {
    "financial":     dict(walk=4.5, rows=2, lot_w=(70, 110), inset=6.0, kind="tower"),
    "downtown":      dict(walk=4.5, rows=2, lot_w=(24, 48), inset=0.3, kind="downtown"),
    "shopping":      dict(walk=4.5, rows=2, lot_w=(16, 34), inset=0.3, kind="retail"),
    "entertainment": dict(walk=4.5, rows=2, lot_w=(22, 46), inset=0.3, kind="entertainment"),
    "oldtown":       dict(walk=3.5, rows=2, lot_w=(9, 16), inset=0.2, kind="oldtown"),
    "residential":   dict(walk=3.5, rows=2, lot_w=(18, 34), inset=1.5, kind="apartment"),
    "suburbs":       dict(walk=3.0, rows=2, lot_w=(19, 26), inset=0.0, kind="house"),
    "industrial":    dict(walk=3.0, rows=2, lot_w=(40, 80), inset=4.0, kind="industrial"),
    "construction":  dict(walk=3.0, rows=1, lot_w=(60, 120), inset=3.0, kind="construction"),
    "marina":        dict(walk=4.0, rows=1, lot_w=(30, 60), inset=2.0, kind="marina"),
    "harbor":        dict(walk=2.5, rows=1, lot_w=(70, 140), inset=6.0, kind="harbor"),
    "airport":       dict(walk=2.5, rows=1, lot_w=(80, 160), inset=8.0, kind="airport"),
    "beach":         dict(walk=4.0, rows=1, lot_w=(20, 40), inset=2.0, kind="beachfront"),
}

# landmark / special blocks: point -> building style
LANDMARK_BLOCKS = [
    ((55.0, -200.0), "landmark_aurelia_tower", "Aurelia Tower"),
    ((-50.0, -300.0), "landmark_bank_tower", "Meridian Bank"),
    ((120.0, 735.0), "landmark_grand_hotel", "Grand Solace Hotel"),
    ((605.0, -555.0), "landmark_stadium", "Aurelia Arena Stadium"),
    ((395.0, -400.0), "landmark_convention", "Harborview Convention Center"),
    ((-150.0, 400.0), "landmark_mall", "Galleria Plaza"),
    ((-150.0, 100.0), "parking_garage", "City Parking"),
    ((-250.0, -100.0), "police_station", "APD Central"),
    ((-630.0, 100.0), "police_station", "APD Palmview"),
    ((-435.0, -300.0), "hospital", "St. Aurelia Hospital"),
    ((505.0, 100.0), "fire_station", "Fire Station 3"),
    ((-435.0, 400.0), "gas_station", "Fuel Point"),
    ((610.0, 400.0), "gas_station", "Fuel Point"),
    ((-1180.0, 520.0), "gas_station", "Fuel Point"),
    ((55.0, -400.0), "car_dealer", "Aurelia Motors"),
    ((-530.0, -300.0), "park_small", "Palmview Green"),
    ((-1060.0, -200.0), "park_small", "Westbrook Commons"),
    ((170.0, 300.0), "plaza", "Galleria Square"),
    ((-250.0, 500.0), "parking_lot", "Parking"),
    ((395.0, 300.0), "parking_lot", "Parking"),
    ((-1180.0, -390.0), "school", "Westbrook High"),
    ((-630.0, 500.0), "parking_lot", "Parking"),
]

# per-landmark footprint inset (m) and height
LANDMARK_SPECS = {
    "landmark_aurelia_tower": dict(inset=4.0, height=330.0),
    "landmark_bank_tower": dict(inset=5.0, height=215.0),
    "landmark_grand_hotel": dict(inset=4.0, height=120.0),
    "landmark_stadium": dict(inset=6.0, height=38.0),
    "landmark_convention": dict(inset=4.0, height=26.0),
    "landmark_mall": dict(inset=1.0, height=22.0),
    "parking_garage": dict(inset=1.0, height=18.0),
    "police_station": dict(inset=2.0, height=14.0),
    "hospital": dict(inset=3.0, height=34.0),
    "fire_station": dict(inset=4.0, height=11.0),
    "gas_station": dict(inset=2.0, height=6.5),
    "car_dealer": dict(inset=4.0, height=12.0),
    "school": dict(inset=6.0, height=11.0),
}

TOWER_GLASS = ["glass_blue", "glass_green", "glass_silver", "glass_bronze", "glass_dark"]
OFFICE_FACADES = ["concrete_panel", "stone", "glass_silver", "glass_blue", "concrete_panel"]
PLASTER_COLORS = [
    (0.93, 0.86, 0.74), (0.90, 0.78, 0.62), (0.86, 0.62, 0.48), (0.95, 0.92, 0.85),
    (0.78, 0.84, 0.86), (0.92, 0.74, 0.66), (0.80, 0.72, 0.60), (0.96, 0.88, 0.70),
    (0.70, 0.78, 0.70), (0.88, 0.82, 0.78),
]
SKYLINE_CENTER = (50.0, -210.0)


def _front_dir(lot, block_poly):
    """Outward direction of the lot edge that lies on the block boundary (street side)."""
    best = None
    n = len(lot)
    for i in range(n):
        a, b = lot[i], lot[(i + 1) % n]
        l = G.dist(a, b)
        if l < 2.0:
            continue
        mid = G.lerp(a, b, 0.5)
        dmin = min(G.point_seg_dist(mid, block_poly[j], block_poly[(j + 1) % len(block_poly)])[0]
                   for j in range(len(block_poly)))
        score = dmin - l * 0.02
        if best is None or score < best[0]:
            e = G.norm(G.sub(b, a))
            best = (score, G.left(e), mid, l)
    if best is None:
        return (0.0, 1.0), G.poly_centroid(lot), 1.0
    return best[1], best[2], best[3]


def _cut_rows(poly, rows, rng):
    c, u, v, hu, hv = G.obb(poly)
    if hv > hu:
        u, hu, hv = v, hv, hu
    v = G.right(u)
    if rows >= 2 and hv > 22.0:
        a, b = G.poly_split(poly, c, u)
        return [r for r in (a, b) if len(r) >= 3 and abs(G.poly_area(r)) > 30], u
    return [poly], u


def _cut_lots(row, u, wmin, wmax, rng):
    us = [G.dot(p, u) for p in row]
    umin, umax = min(us), max(us)
    lots = []
    s = umin
    while s < umax - 0.5:
        w = rng.uniform(wmin, wmax)
        if umax - (s + w) < wmin * 0.6:
            w = umax - s
        p0 = G.mul(u, s)
        piece = G.clip_halfplane(row, p0, u)
        piece = G.clip_halfplane(piece, G.mul(u, s + w), G.mul(u, -1))
        piece = G.poly_dedupe(piece, 0.05)
        if len(piece) >= 3 and abs(G.poly_area(piece)) > 20.0:
            lots.append(G.ensure_ccw(piece))
        s += w
    return lots


def _rect_in_lot(lot, front, front_set, back_set, side_set, max_w=None, max_d=None):
    """Axis-aligned (to the frontage) rectangle inside a lot."""
    fwd = G.mul(front, -1.0)  # into the lot
    side = G.right(fwd)
    us = [G.dot(p, side) for p in lot]
    vs = [G.dot(p, fwd) for p in lot]
    u0, u1 = min(us) + side_set, max(us) - side_set
    v0, v1 = min(vs) + front_set, max(vs) - back_set
    if max_w and u1 - u0 > max_w:
        m = (u0 + u1) * 0.5
        u0, u1 = m - max_w * 0.5, m + max_w * 0.5
    if max_d and v1 - v0 > max_d:
        v1 = v0 + max_d
    if u1 - u0 < 4 or v1 - v0 < 4:
        return None
    pts = [G.add(G.mul(side, u), G.mul(fwd, v)) for (u, v) in ((u0, v0), (u1, v0), (u1, v1), (u0, v1))]
    return G.ensure_ccw(pts)


class BlockBuilder:
    def __init__(self, net, terrain, rng, elevated_footprints):
        self.net = net
        self.T = terrain
        self.rng = rng
        self.elev = elevated_footprints  # list of (a, b, half_width)
        self.blocks = []
        self.lots = []
        self.buildings = []
        self.special_areas = []

    def _under_highway(self, poly, margin=2.0):
        c = G.poly_centroid(poly)
        x0, z0, x1, z1 = G.poly_bbox(poly)
        rad = max(x1 - x0, z1 - z0)
        for (a, b, hw) in self.elev:
            d, _ = G.point_seg_dist(c, a, b)
            if d > rad + hw + margin:
                continue
            # test polygon corners and centre against the deck strip
            for p in list(poly) + [c]:
                dd, _ = G.point_seg_dist(p, a, b)
                if dd < hw + margin:
                    return True
            # test deck centre line points inside polygon
            for t in (0.0, 0.25, 0.5, 0.75, 1.0):
                if G.point_in_poly(G.lerp(a, b, t), poly):
                    return True
        return False

    def classify(self, faces):
        for f in faces:
            area = G.poly_area(f)
            c = G.poly_centroid(f)
            did = district_at(*c)
            st = style_of(did)
            kind = "block"
            nat = self.T.natural_height(*c)
            if did in ("hills", "luxury", "rural", "rural_east") or area > 90000:
                kind = "natural"
            elif did == "park":
                kind = "park"
            elif abs(c[0] - 290.0) < 35 and nat < -0.5:
                kind = "canal"
            elif nat < SEA_LEVEL:
                kind = "water"
            elif did == "harbor":
                kind = "harbor"
            elif did == "airport":
                kind = "airport"
            block = dict(id=len(self.blocks), poly=[(round(p[0], 3), round(p[1], 3)) for p in f],
                         kind=kind, district=did, style=st, center=c, area=area,
                         y=0.0 if kind != "natural" else nat)
            self.blocks.append(block)
        return self.blocks

    def generate(self):
        rng = self.rng
        for b in self.blocks:
            if b["kind"] != "block":
                continue
            st = b["style"]
            rules = STYLES.get(st)
            if rules is None:
                b["kind"] = "plaza"
                continue
            # pads follow terrain: in the flat city this is ~0
            b["y"] = max(0.0, self.T.natural_height(*b["center"]))
            inner = G.poly_inset(b["poly"], rules["walk"])
            if len(inner) < 3:
                b["kind"] = "plaza"
                continue
            b["inner"] = [(round(p[0], 3), round(p[1], 3)) for p in inner]
            special = None
            for (pt, sstyle, name) in LANDMARK_BLOCKS:
                if G.point_in_poly(pt, b["poly"]):
                    special = (sstyle, name)
                    break
            if special:
                self._special_block(b, inner, special)
                continue
            if st == "construction":
                self._construction_block(b, inner)
                continue
            rows, u = _cut_rows(inner, rules["rows"], rng)
            for row in rows:
                for lot in _cut_lots(row, u, rules["lot_w"][0], rules["lot_w"][1], rng):
                    self._fill_lot(b, lot, rules)

    # ------------------------------------------------------------ helpers
    def _add_building(self, block, lot, footprint, style, height, **kw):
        front, fmid, flen = _front_dir(lot, block["inner"])
        bid = len(self.buildings)
        floor_h = kw.pop("floor_h", 3.4)
        y = block["y"] + PAD_H
        spec = dict(id=bid, block=block["id"], district=block["district"], style=style,
                    footprint=[(round(p[0], 3), round(p[1], 3)) for p in footprint],
                    y=round(y, 3), height=round(height, 2), floor_h=floor_h,
                    floors=max(1, int(height / floor_h)),
                    front=(round(front[0], 4), round(front[1], 4)),
                    front_mid=(round(fmid[0], 3), round(fmid[1], 3)),
                    seed=self.rng.randint(0, 999999), usage=kw.pop("usage", "generic"))
        spec.update(kw)
        self.buildings.append(spec)
        return spec

    def _add_lot(self, block, lot, kind, **kw):
        rec = dict(id=len(self.lots), block=block["id"], kind=kind,
                   poly=[(round(p[0], 3), round(p[1], 3)) for p in lot], y=block["y"] + PAD_H)
        rec.update(kw)
        self.lots.append(rec)
        return rec

    def _special_block(self, b, inner, special):
        sstyle, name = special
        spec = LANDMARK_SPECS.get(sstyle)
        if sstyle in ("park_small", "plaza", "parking_lot"):
            self._add_lot(b, inner, sstyle, name=name)
            b["special"] = sstyle
            return
        fp = G.poly_inset(inner, spec["inset"]) or inner
        extra = {}
        if sstyle in ("landmark_aurelia_tower", "landmark_bank_tower"):
            extra["facade"] = "glass_blue" if sstyle == "landmark_aurelia_tower" else "glass_bronze"
        self._add_lot(b, inner, "landmark", name=name)
        self._add_building(b, inner, fp, sstyle, spec["height"], name=name, usage=sstyle,
                           floor_h=4.0 if "tower" in sstyle else 3.6, **extra)
        b["special"] = sstyle

    def _construction_block(self, b, inner):
        lot = self._add_lot(b, inner, "construction")
        fp = G.poly_inset(inner, 8.0)
        if fp:
            h = self.rng.uniform(30, 80)
            self._add_building(b, inner, fp, "construction", h, floors_built=self.rng.randint(3, 12),
                               floor_h=3.8)
        _ = lot

    # ------------------------------------------------------------ lot filling
    def _fill_lot(self, b, lot, rules):
        rng = self.rng
        st = b["style"]
        kind = rules["kind"]
        if self._under_highway(lot, 3.0):
            self._add_lot(b, lot, "parking_lot", name="Parking")
            return
        area = abs(G.poly_area(lot))
        dsky = G.dist(G.poly_centroid(lot), SKYLINE_CENTER)
        front, fmid, flen = _front_dir(lot, b["inner"])
        if kind == "tower":
            fp = G.poly_inset(lot, rules["inset"])
            if not fp:
                return
            fall = max(0.25, 1.0 - dsky / 520.0)
            h = rng.uniform(95, 230) * fall + 30
            self._add_lot(b, lot, "plaza")
            self._add_building(b, lot, fp, "tower", h, facade=rng.choice(TOWER_GLASS),
                               floor_h=4.0, podium=rng.chance(0.6),
                               setbacks=rng.randint(0, 3), crown=rng.choice(["flat", "spire", "slant", "crown", "flat"]))
            return
        if kind == "downtown":
            fp = G.poly_inset(lot, rules["inset"]) if area > 60 else None
            if not fp:
                return
            fall = max(0.15, 1.0 - dsky / 600.0)
            r = rng.random()
            if r < 0.08 and area > 900:
                self._add_lot(b, lot, "plaza")
                return
            if r < 0.14 and area > 900:
                self._add_lot(b, lot, "parking_lot", name="Parking")
                return
            h = rng.uniform(16, 60) + rng.uniform(0, 120) * fall * fall
            sub = rng.weighted([("office", 5), ("highrise_classic", 2), ("hotel", 1), ("midrise", 3)])
            fac = rng.choice(OFFICE_FACADES) if sub != "highrise_classic" else rng.choice(["stone", "brick_brown"])
            self._add_building(b, lot, fp, sub, h, facade=fac, floor_h=3.8,
                               shopfront=rng.chance(0.7), setbacks=rng.randint(0, 2) if h > 60 else 0,
                               tint=rng.choice(PLASTER_COLORS))
            return
        if kind == "retail":
            fp = G.poly_inset(lot, rules["inset"])
            if not fp:
                return
            h = rng.uniform(7, 18)
            self._add_building(b, lot, fp, "retail", h, facade=rng.choice(["plaster", "brick_red", "concrete_panel", "plaster"]),
                               tint=rng.choice(PLASTER_COLORS), floor_h=4.2, shopfront=True,
                               awning=rng.chance(0.5))
            return
        if kind == "entertainment":
            fp = G.poly_inset(lot, rules["inset"])
            if not fp:
                return
            sub = rng.weighted([("theater", 2), ("club", 3), ("retail", 2), ("office", 1), ("cinema", 1)])
            h = rng.uniform(9, 24) if sub != "office" else rng.uniform(25, 60)
            self._add_building(b, lot, fp, sub, h, facade=rng.choice(["plaster", "concrete_panel", "brick_red", "glass_dark"]),
                               tint=rng.choice(PLASTER_COLORS), floor_h=4.0, shopfront=True, neon=True)
            return
        if kind == "oldtown":
            fp = G.poly_inset(lot, rules["inset"])
            if not fp:
                return
            h = rng.uniform(9, 19)
            self._add_building(b, lot, fp, "oldtown", h, facade=rng.choice(["brick_red", "plaster", "plaster", "brick_brown", "stucco_white"]),
                               tint=rng.choice(PLASTER_COLORS), floor_h=3.4, shopfront=rng.chance(0.8),
                               balconies=rng.chance(0.6), cornice=True)
            return
        if kind == "apartment":
            r = rng.random()
            if r < 0.06:
                self._add_lot(b, lot, "pocket_park")
                return
            fp = _rect_in_lot(lot, front, 2.0, 5.0, 1.2) or G.poly_inset(lot, 1.5)
            if not fp:
                return
            h = rng.weighted([(rng.uniform(8, 13), 4), (rng.uniform(14, 24), 3), (rng.uniform(25, 34), 1)])
            self._add_lot(b, lot, "yard")
            self._add_building(b, lot, fp, "apartment", h, facade=rng.choice(["plaster", "stucco_white", "brick_red", "plaster"]),
                               tint=rng.choice(PLASTER_COLORS), floor_h=3.1, balconies=rng.chance(0.7),
                               shopfront=rng.chance(0.25))
            return
        if kind == "house":
            fp = _rect_in_lot(lot, front, rng.uniform(5.5, 8.0), 7.0, rng.uniform(2.5, 4.0), max_w=16, max_d=14)
            self._add_lot(b, lot, "house_yard", front=front, pool=rng.chance(0.25))
            if not fp:
                return
            floors = rng.weighted([(1, 5), (2, 4)])
            h = floors * 3.0 + 0.3
            self._add_building(b, lot, fp, "house", h, facade=rng.choice(["stucco_white", "plaster", "wood_siding", "plaster"]),
                               tint=rng.choice(PLASTER_COLORS), floor_h=3.0,
                               roof=rng.choice(["gable", "hip", "hip", "flat"]),
                               roof_color=rng.choice([(0.55, 0.25, 0.18), (0.35, 0.33, 0.32), (0.6, 0.45, 0.3), (0.25, 0.25, 0.27)]),
                               garage=rng.chance(0.7))
            return
        if kind == "industrial":
            r = rng.random()
            if r < 0.12:
                self._add_lot(b, lot, "yard_industrial", containers=True)
                return
            fp = G.poly_inset(lot, rules["inset"] + rng.uniform(0, 5))
            if not fp:
                return
            sub = rng.weighted([("warehouse", 5), ("factory", 2), ("tanks", 1)])
            h = rng.uniform(8, 15) if sub != "factory" else rng.uniform(12, 22)
            self._add_lot(b, lot, "yard_industrial", containers=rng.chance(0.4))
            self._add_building(b, lot, fp, sub, h, facade=rng.choice(["metal_siding", "concrete_panel", "brick_brown", "metal_siding"]),
                               tint=rng.choice([(0.7, 0.72, 0.74), (0.62, 0.66, 0.62), (0.75, 0.7, 0.6), (0.5, 0.55, 0.6)]),
                               floor_h=5.0, roof=rng.choice(["flat", "sawtooth", "flat"]))
            return
        if kind == "marina":
            fp = _rect_in_lot(lot, front, 4.0, 4.0, 3.0, max_d=22) or G.poly_inset(lot, 3.0)
            if not fp:
                return
            h = rng.uniform(7, 14)
            self._add_building(b, lot, fp, "marina_shop", h, facade=rng.choice(["stucco_white", "wood_siding"]),
                               tint=(0.95, 0.95, 0.93), floor_h=3.5, shopfront=True, awning=True)
            return
        if kind == "beachfront":
            fp = _rect_in_lot(lot, front, 3.0, 3.0, 3.0, max_d=16)
            if not fp:
                return
            self._add_building(b, lot, fp, "beach_shop", rng.uniform(4.5, 8), facade="stucco_white",
                               tint=rng.choice(PLASTER_COLORS), floor_h=4.0, shopfront=True, awning=True)
            return
