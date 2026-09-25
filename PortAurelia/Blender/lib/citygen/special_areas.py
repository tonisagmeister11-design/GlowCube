"""Non-grid areas: harbour yards, airport, beach, marina docks, hills villas, rural farms,
parks, canal promenade. Produces building specs, lots and area descriptors."""
import math

from . import geom as G
from .blocks import PAD_H, PLASTER_COLORS
from .terrain import SEA_LEVEL, HARBOR_BASINS, MARINA_BASIN, LAKE_C, LAKE_R, coast_z


class AreaBuilder:
    def __init__(self, bb, net, terrain, rng):
        self.bb = bb  # BlockBuilder (shares building/lot lists)
        self.net = net
        self.T = terrain
        self.rng = rng
        self.areas = []  # dict(kind, poly/params)
        self.static_props = []  # extra props tied to areas

    def building(self, footprint, style, height, y=None, **kw):
        bid = len(self.bb.buildings)
        c = G.poly_centroid(footprint)
        if y is None:
            y = min(self.T.height(*p) for p in footprint) + 0.05
        floor_h = kw.pop("floor_h", 3.5)
        front = kw.pop("front", (0.0, 1.0))
        spec = dict(id=bid, block=-1, district=kw.pop("district", ""), style=style,
                    footprint=[(round(p[0], 3), round(p[1], 3)) for p in G.ensure_ccw(footprint)],
                    y=round(y, 3), height=round(height, 2), floor_h=floor_h,
                    floors=max(1, int(height / floor_h)), front=(round(front[0], 4), round(front[1], 4)),
                    front_mid=(round(c[0] + front[0] * 5, 3), round(c[1] + front[1] * 5, 3)),
                    seed=self.rng.randint(0, 999999), usage=kw.pop("usage", "generic"))
        spec.update(kw)
        self.bb.buildings.append(spec)
        return spec

    def prop(self, ptype, x, z, rot=0.0, y=None, scale=1.0):
        if y is None:
            y = self.T.height(x, z)
        self.static_props.append((ptype, x, y, z, rot, scale))

    def rect(self, cx, cz, w, d, a=0.0):
        u = (math.cos(a), math.sin(a))
        v = G.right(u)
        return G.rect_poly((cx, cz), u, v, w * 0.5, d * 0.5)

    # ------------------------------------------------------------------ harbour
    def harbor(self):
        rng = self.rng
        # quay wall outline: along coast and around basins
        self.areas.append(dict(kind="quay", x0=330.0, x1=1600.0, z=1070.0))
        for (x0, z0, x1, z1) in HARBOR_BASINS:
            self.areas.append(dict(kind="basin", rect=(x0, z0, x1, min(z1, 1070.0))))
        # container yards between quay roads
        yards = [(360.0, 800.0, 410.0, 1060.0), (575.0, 800.0, 690.0, 1060.0),
                 (890.0, 800.0, 1010.0, 1060.0), (1190.0, 800.0, 1290.0, 1060.0),
                 (1310.0, 800.0, 1480.0, 1060.0), (360.0, 660.0, 600.0, 765.0),
                 (640.0, 660.0, 930.0, 765.0), (970.0, 660.0, 1280.0, 765.0),
                 (1320.0, 660.0, 1480.0, 765.0)]
        for (x0, z0, x1, z1) in yards:
            poly = [(x0, z0), (x1, z0), (x1, z1), (x0, z1)]
            self.areas.append(dict(kind="harbor_yard", poly=poly))
            # container stacks in rows
            w = x1 - x0
            d = z1 - z0
            if w > 80 and z0 > 700 and rng.chance(0.4):
                # warehouse
                fp = self.rect((x0 + x1) * 0.5, (z0 + z1) * 0.5, min(w - 16, 90), min(d - 16, 60))
                self.building(fp, "warehouse", rng.uniform(11, 16), y=PAD_H * 0 + 0.05, district="harbor",
                              facade="metal_siding", tint=rng.choice([(0.55, 0.62, 0.7), (0.72, 0.36, 0.28), (0.7, 0.7, 0.68)]),
                              roof="flat", floor_h=5.0)
                continue
            cols = int((w - 8) / 14.0)
            rows = int((d - 8) / 3.0)
            for ci in range(cols):
                for ri in range(0, rows, 1):
                    if rng.chance(0.18):
                        continue
                    cx = x0 + 8 + ci * 14 + 6.1
                    cz = z0 + 6 + ri * 2.8
                    stack = rng.randint(1, 4)
                    for s in range(stack):
                        self.prop("container", cx, cz, rot=0.0, y=0.05 + s * 2.6,
                                  scale=rng.randint(0, 7))
        # gantry cranes along the quay (landmark: port cranes)
        crane_x = [380.0, 470.0, 640.0, 760.0, 920.0, 1080.0, 1230.0, 1380.0]
        for x in crane_x:
            z = 1058.0
            for (bx0, bz0, bx1, bz1) in HARBOR_BASINS:
                if bx0 - 10 < x < bx1 + 10:
                    z = bz0 - 12.0
            self.prop("gantry_crane", x, z, rot=0.0, y=0.05)
        # cargo ships in basins / at quay
        self.prop("cargo_ship", 820.0, 1130.0, rot=0.0, y=SEA_LEVEL)
        self.prop("cargo_ship", 1260.0, 1125.0, rot=0.0, y=SEA_LEVEL)
        self.prop("tug_boat", 490.0, 950.0, rot=1.57, y=SEA_LEVEL)
        # observation tower on the breakwater between marina and canal mouth
        self.building(self.rect(212.0, 930.0, 14, 14), "landmark_observation_tower", 92.0, y=0.05,
                      district="marina", name="Harbor Watch", usage="landmark_observation_tower")

    # ------------------------------------------------------------------ airport
    def airport(self):
        rng = self.rng
        # runway along z at x = 1260, taxiway at 1170, apron by the terminal
        self.areas.append(dict(kind="runway", rect=(1235.0, -640.0, 1285.0, 560.0)))
        self.areas.append(dict(kind="taxiway", rect=(1160.0, -620.0, 1180.0, 540.0)))
        for z in (-560.0, -200.0, 200.0, 500.0):
            self.areas.append(dict(kind="taxiway", rect=(1180.0, z - 10, 1235.0, z + 10)))
        self.areas.append(dict(kind="apron", rect=(1030.0, -330.0, 1160.0, 230.0)))
        # terminal
        term = [(1010.0, -240.0), (1060.0, -240.0), (1060.0, 140.0), (1010.0, 140.0)]
        self.building(term, "airport_terminal", 22.0, y=0.05, district="airport", name="Aurelia International",
                      usage="airport_terminal", front=(-1.0, 0.0), floor_h=5.5)
        # control tower
        self.building(self.rect(1120.0, 300.0, 12, 12), "control_tower", 58.0, y=0.05, district="airport",
                      usage="control_tower")
        # hangars in the north
        for i, z in enumerate((-560.0, -470.0, -380.0)):
            self.building(self.rect(1080.0, z, 70, 60), "hangar", 18.0, y=0.05, district="airport",
                          usage="hangar", front=(1.0, 0.0), facade="metal_siding", tint=(0.78, 0.8, 0.82))
        # cargo sheds south
        for z in (300.0, 420.0):
            self.building(self.rect(960.0, z, 60, 70), "warehouse", 12.0, y=0.05, district="airport",
                          facade="metal_siding", tint=(0.7, 0.72, 0.75), roof="flat", floor_h=5.0)
        # parked aircraft on the apron
        for z in (-260.0, -150.0, -40.0, 70.0, 170.0):
            self.prop("airliner", 1110.0, z, rot=math.pi * 0.5, y=0.05)
        self.prop("airliner", 1170.0, -600.0, rot=0.0, y=0.05)
        self.prop("small_plane", 1120.0, -420.0, rot=math.pi, y=0.05)
        self.prop("small_plane", 1130.0, -500.0, rot=math.pi, y=0.05)
        # terminal parking lot
        self.areas.append(dict(kind="parking_area", poly=[(895.0, -240.0), (985.0, -240.0), (985.0, 140.0), (895.0, 140.0)]))
        # perimeter fence line
        self.areas.append(dict(kind="fence", pts=[(1200.0, -690.0), (1590.0, -690.0), (1590.0, 630.0), (1200.0, 630.0)]))

    # ------------------------------------------------------------------ beach & marina
    def beach(self):
        rng = self.rng
        # boardwalk south of Sunstrand Blvd
        bw = []
        x = -1480.0
        while x <= -310.0:
            bw.append((x, 766.0))
            x += 20.0
        self.areas.append(dict(kind="boardwalk", pts=bw, width=8.0))
        # palms along the boardwalk
        x = -1470.0
        while x < -310.0:
            self.prop("palm_tall", x + rng.uniform(-2, 2), 760.0 + rng.uniform(-1, 1), rot=rng.uniform(0, 6.28))
            x += rng.uniform(14, 22)
        # lifeguard towers and umbrellas on the sand
        x = -1420.0
        while x < -340.0:
            zc = coast_z(x) - 35.0
            self.prop("lifeguard_tower", x, zc, rot=math.pi)
            for k in range(rng.randint(2, 6)):
                ux = x + rng.uniform(-60, 60)
                uz = coast_z(ux) - rng.uniform(40, 75)
                self.prop("beach_umbrella", ux, uz, rot=rng.uniform(0, 6.28))
            x += rng.uniform(150, 220)
        # pier with ferris wheel
        self.areas.append(dict(kind="pier", pts=[(-700.0, 770.0), (-700.0, 1010.0)], width=16.0))
        self.prop("ferris_wheel", -700.0, 990.0, rot=0.0, y=2.2)
        self.building(self.rect(-700.0, 940.0, 14, 18), "beach_shop", 5.0, y=2.2, district="beach",
                      facade="stucco_white", tint=(0.95, 0.9, 0.8), shopfront=True, awning=True, front=(1.0, 0.0))
        # beach parking at the access roads
        for x in (-1300.0, -1000.0, -450.0):
            self.areas.append(dict(kind="parking_area", poly=[(x - 30, 772.0), (x + 30, 772.0), (x + 30, 792.0), (x - 30, 792.0)]))

    def marina(self):
        rng = self.rng
        x0, z0, x1, z1 = MARINA_BASIN
        self.areas.append(dict(kind="basin", rect=(x0, z0, x1, 955.0)))
        # docks (piers) reaching into the basin with moored boats
        for i, x in enumerate(range(int(x0) + 30, int(x1) - 20, 55)):
            self.areas.append(dict(kind="dock", pts=[(float(x), z0 - 2.0), (float(x), z0 + 120.0)], width=3.0))
            for k in range(6):
                z = z0 + 15 + k * 18
                for side in (-1, 1):
                    if rng.chance(0.75):
                        self.prop(rng.choice(["yacht", "boat_small", "boat_small", "sailboat"]),
                                  x + side * 9.0, z, rot=math.pi * 0.5 * side, y=SEA_LEVEL)
        # breakwater
        self.areas.append(dict(kind="breakwater", pts=[(x1 + 25.0, 900.0), (x1 + 25.0, 960.0)], width=18.0))

    # ------------------------------------------------------------------ parks
    def parks(self):
        rng = self.rng
        # Solace Park lake + paths are handled by the park block; here: extra features
        self.areas.append(dict(kind="lake", center=LAKE_C, radii=LAKE_R))
        self.prop("fountain", 120.0, -520.0)
        self.prop("pavilion", -200.0, -600.0)
        self.prop("playground", 150.0, -610.0)
        self.building(self.rect(-230.0, -500.0, 18, 12), "park_cafe", 5.0, district="park",
                      facade="wood_siding", tint=(0.8, 0.7, 0.55), shopfront=True, awning=True, front=(0.0, 1.0))

    # ------------------------------------------------------------------ hills & rural
    def hills(self):
        rng = self.rng
        T = self.T
        villas = 0
        # luxury villas along hill roads
        for e in self.net.edges:
            if e.rtype not in ("hill",):
                continue
            L = e.length()
            s = 25.0
            while s < L - 25.0:
                p, d = G.polyline_point_at(e.pts, s)
                for side in (1, -1):
                    if not rng.chance(0.55):
                        continue
                    n = G.mul(G.right(d), side)
                    c = G.add(p, G.mul(n, e.hw + 18.0))
                    if c[1] > -700 and c[0] > -300:
                        continue
                    ang = math.atan2(n[1], n[0])
                    fp = self.rect(c[0], c[1], rng.uniform(16, 24), rng.uniform(13, 18), ang + math.pi * 0.5)
                    if self._blocked(fp, 5.0):
                        continue
                    ys = [T.height(*q) for q in fp]
                    if max(ys) - min(ys) > 9.0:
                        continue
                    y = max(ys)
                    self.building(fp, "villa", rng.uniform(6.5, 10.0), y=y, district="luxury",
                                  front=(-n[0], -n[1]), tint=rng.choice([(0.95, 0.94, 0.9), (0.9, 0.86, 0.78), (0.35, 0.36, 0.38)]),
                                  pool=rng.chance(0.6), facade="stucco_white", floor_h=3.3)
                    villas += 1
                s += rng.uniform(40, 70)
        # TV tower on the peak
        self.building(self.rect(402.0, -1160.0, 22, 22), "landmark_tv_tower", 210.0,
                      y=T.height(402.0, -1160.0), district="hills", name="Aurelia Sky Tower",
                      usage="landmark_tv_tower")
        # farms along rural roads
        for e in self.net.edges:
            if e.rtype != "rural":
                continue
            L = e.length()
            s = 60.0
            while s < L - 60.0:
                p, d = G.polyline_point_at(e.pts, s)
                if p[1] > -700 and p[0] < 850:
                    s += 80
                    continue
                side = 1 if rng.chance(0.5) else -1
                n = G.mul(G.right(d), side)
                c = G.add(p, G.mul(n, e.hw + 30.0))
                ang = math.atan2(n[1], n[0])
                fp = self.rect(c[0], c[1], 14, 10, ang + math.pi * 0.5)
                if not self._blocked(fp, 8.0) and rng.chance(0.5):
                    ys = [T.height(*q) for q in fp]
                    if max(ys) - min(ys) < 6:
                        self.building(fp, "farmhouse", 7.0, y=max(ys), district="rural", front=(-n[0], -n[1]),
                                      facade="wood_siding", tint=rng.choice([(0.92, 0.9, 0.84), (0.75, 0.3, 0.25)]),
                                      roof="gable", roof_color=(0.4, 0.38, 0.36), floor_h=3.2)
                        bc = G.add(c, G.mul(n, 28.0))
                        bfp = self.rect(bc[0], bc[1], 18, 12, ang)
                        bys = [T.height(*q) for q in bfp]
                        if not self._blocked(bfp, 4.0) and max(bys) - min(bys) < 6:
                            self.building(bfp, "barn", 9.0, y=max(bys), district="rural", front=(-n[0], -n[1]),
                                          facade="wood_siding", tint=(0.62, 0.18, 0.14), roof="gable",
                                          roof_color=(0.35, 0.35, 0.36), floor_h=4.5)
                        # field next to the farm
                        fc = G.add(c, G.add(G.mul(n, 60.0), G.mul(d, 60.0)))
                        self.areas.append(dict(kind="field", poly=self.rect(fc[0], fc[1], 90, 70, ang),
                                               crop=rng.randint(0, 2)))
                s += rng.uniform(150, 260)

    def _blocked(self, fp, margin):
        c = G.poly_centroid(fp)
        # roads
        for e in self.net.edges:
            x0, z0, x1, z1 = G.poly_bbox(e.pts)
            if c[0] < x0 - 60 or c[0] > x1 + 60 or c[1] < z0 - 60 or c[1] > z1 + 60:
                continue
            for i in range(len(e.pts) - 1):
                for q in list(fp) + [c]:
                    d, _ = G.point_seg_dist(q, e.pts[i], e.pts[i + 1])
                    if d < e.hw + margin:
                        return True
        # other buildings
        for b in self.bb.buildings[-400:]:
            if G.polys_overlap_aabb(fp, b["footprint"], margin):
                return True
        if any(self.T.natural_height(*q) < SEA_LEVEL + 1.0 for q in fp):
            return True
        return False
