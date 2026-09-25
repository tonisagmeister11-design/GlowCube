"""The concrete road layout of Port Aurelia.

Grid lines per district, the elevated highway ring with diamond interchanges,
the northern expressway spur (with tunnel), winding hill roads, harbour and
airport service roads.
"""
import math

from . import geom as G
from .roads import RoadDef, ROAD_TYPES, half_width

HWY_Y = 10.0
HWY_RING = dict(x0=-940.0, x1=800.0, z0=-720.0, z1=610.0, r=100.0)

# --------------------------------------------------------------------- grid lines
AVENUES_Z = [-450.0, -50.0, 350.0]

RES_X = [-780.0, -680.0, -580.0, -480.0, -390.0]
RES_Z = [-660.0, -560.0, -350.0, -250.0, -150.0, 50.0, 150.0, 250.0, 450.0, 550.0]
DT_X_ONEWAY_N = -200.0
DT_X_ONEWAY_S = -100.0
DT_X_STREET = [110.0]
DT_Z = [-350.0, -250.0, -150.0, 50.0, 150.0, 250.0, 450.0, 550.0]
SUB_X = [-1480.0, -1360.0, -1240.0, -1120.0, -1000.0]
SUB_Z = [-660.0, -560.0, -340.0, -230.0, -140.0, 50.0, 150.0, 250.0, 460.0, 570.0]
EAST_X = [450.0, 660.0]
EAST_Z = [-350.0, -250.0, -150.0, 50.0, 150.0, 250.0, 450.0, 550.0]

ROUNDABOUTS = [((-1240.0, 150.0), 17.0), ((0.0, 770.0), 17.0)]


def _line(a, b):
    return [a, b]


def _smooth_path(ctrl, step=8.0):
    """Catmull-Rom through control points -> dense polyline."""
    pts = []
    n = len(ctrl)
    for i in range(n - 1):
        p0 = ctrl[max(0, i - 1)]
        p1 = ctrl[i]
        p2 = ctrl[i + 1]
        p3 = ctrl[min(n - 1, i + 2)]
        seg = G.dist(p1, p2)
        k = max(2, int(seg / step))
        for j in range(k):
            t = j / k
            t2, t3 = t * t, t * t * t
            x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                       + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3)
            z = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                       + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)
            pts.append((x, z))
    pts.append(ctrl[-1])
    return pts


def highway_ring_points(step=10.0):
    x0, x1, z0, z1, r = HWY_RING["x0"], HWY_RING["x1"], HWY_RING["z0"], HWY_RING["z1"], HWY_RING["r"]
    pts = []
    # clockwise on a north-up map starting at the NW corner going east along the north leg
    segs = [
        ("line", (x0 + r, z0), (x1 - r, z0)),
        ("arc", (x1 - r, z0 + r), -math.pi / 2, 0.0),
        ("line", (x1, z0 + r), (x1, z1 - r)),
        ("arc", (x1 - r, z1 - r), 0.0, math.pi / 2),
        ("line", (x1 - r, z1), (x0 + r, z1)),
        ("arc", (x0 + r, z1 - r), math.pi / 2, math.pi),
        ("line", (x0, z1 - r), (x0, z0 + r)),
        ("arc", (x0 + r, z0 + r), math.pi, math.pi * 1.5),
    ]
    for s in segs:
        if s[0] == "line":
            a, b = s[1], s[2]
            n = max(1, int(G.dist(a, b) / step))
            for i in range(n):
                pts.append(G.lerp(a, b, i / n))
        else:
            c, a0, a1 = s[1], s[2], s[3]
            n = max(4, int(abs(a1 - a0) * r / step))
            for i in range(n):
                a = a0 + (a1 - a0) * i / n
                pts.append((c[0] + math.cos(a) * r, c[1] + math.sin(a) * r))
    pts.append(pts[0])
    return pts


def _ramp_set(axis, c, k, L=190.0):
    """Four ramps of a diamond interchange.

    axis 'x': the highway leg runs along z at x = c; k is the crossing road's z.
    axis 'z': the leg runs along x at z = c; k is the crossing road's x.
    Traffic keeps right; the leg's southbound/eastbound carriageway is on the
    west/south side respectively.
    """
    hw = half_width("highway", False, False)
    rw = half_width("ramp", True, False)
    off = hw + rw - 0.6
    ramps = []

    def mk(ctrl, merge_start, merge_end):
        pts = _smooth_path(ctrl, 6.0)
        ramps.append(RoadDef(pts, "ramp", level="elevated", oneway=True,
                             merge_start=merge_start, merge_end=merge_end,
                             heights=[0.0] * len(pts), name="Ramp"))

    if axis == "x":
        # southbound (+z) lanes on the west side (x < c)
        mk([(c - off, k - L), (c - 26, k - L + 60), (c - 35, k - 40), (c - 35, k)], True, False)
        mk([(c - 35, k), (c - 35, k + 40), (c - 26, k + L - 60), (c - off, k + L)], False, True)
        # northbound (-z) lanes on the east side
        mk([(c + off, k + L), (c + 26, k + L - 60), (c + 35, k + 40), (c + 35, k)], True, False)
        mk([(c + 35, k), (c + 35, k - 40), (c + 26, k - L + 60), (c + off, k - L)], False, True)
    else:
        # eastbound (+x) lanes on the south side (z > c)
        mk([(k - L, c + off), (k - L + 60, c + 26), (k - 40, c + 35), (k, c + 35)], True, False)
        mk([(k, c + 35), (k + 40, c + 35), (k + L - 60, c + 26), (k + L, c + off)], False, True)
        # westbound (-x) lanes on the north side
        mk([(k + L, c - off), (k + L - 60, c - 26), (k + 40, c - 35), (k, c - 35)], True, False)
        mk([(k, c - 35), (k - 40, c - 35), (k - L + 60, c - 26), (k - L, c - off)], False, True)
    return ramps


def build_layout(net, terrain):
    R = net.add

    # ---------------------------------------------------------------- avenues
    for z in AVENUES_Z:
        R(RoadDef(_line((-1480.0, z), (750.0, z)), "avenue", name=f"Avenue {int(z)}"))
    # airport access continues the -50 avenue east of the highway
    R(RoadDef(_line((750.0, -50.0), (1000.0, -50.0)), "avenue", name="Airport Road"))
    R(RoadDef(_line((-880.0, -660.0), (-880.0, 700.0)), "avenue", name="Palmview Avenue"))
    R(RoadDef(_line((-300.0, -660.0), (-300.0, 700.0)), "avenue", name="Westgate Avenue"))
    R(RoadDef(_line((340.0, -660.0), (340.0, 700.0)), "avenue", name="Canal Avenue"))
    R(RoadDef(_line((0.0, -450.0), (0.0, 770.0)), "boulevard", name="Aurelia Boulevard"))
    # coastal boulevard
    R(RoadDef(_line((-1480.0, 700.0), (750.0, 700.0)), "boulevard", name="Sunstrand Boulevard"))

    # ---------------------------------------------------------------- residential
    for x in RES_X:
        z1 = 700.0 if x == -580.0 else 550.0
        R(RoadDef(_line((x, -660.0), (x, z1)), "street", parking=True, name="Palmview St"))
    for z in RES_Z:
        x1 = -300.0
        R(RoadDef(_line((-880.0, z), (x1, z)), "street", parking=True, name="Residential St"))

    # ---------------------------------------------------------------- downtown / shopping
    R(RoadDef(_line((DT_X_ONEWAY_N, 550.0), (DT_X_ONEWAY_N, -450.0)), "oneway", parking=True, name="North Street"))
    R(RoadDef(_line((DT_X_ONEWAY_S, -450.0), (DT_X_ONEWAY_S, 550.0)), "oneway", parking=True, name="South Street"))
    for x in DT_X_STREET:
        R(RoadDef(_line((x, -450.0), (x, 550.0)), "street", parking=True, name="Commerce St"))
    R(RoadDef(_line((240.0, -660.0), (240.0, 700.0)), "street", parking=True, name="Canal Street"))
    for z in DT_Z:
        if z == -250.0:
            R(RoadDef(_line((-300.0, z), (240.0, z)), "oneway", parking=True, name="Market East"))
        elif z == 150.0:
            R(RoadDef(_line((240.0, z), (-300.0, z)), "oneway", parking=True, name="Market West"))
        else:
            R(RoadDef(_line((-300.0, z), (240.0, z)), "street", parking=True, name="Downtown St"))
    # park north edge
    R(RoadDef(_line((-300.0, -660.0), (240.0, -660.0)), "street", name="Parkside Drive"))

    # canal bridges (streets) and bank streets
    R(RoadDef(_line((240.0, 150.0), (340.0, 150.0)), "street", name="Canal Bridge"))
    R(RoadDef(_line((240.0, -660.0), (340.0, -660.0)), "street", name="North Canal Bridge"))

    # ---------------------------------------------------------------- marina
    R(RoadDef(_line((-300.0, 770.0), (240.0, 770.0)), "service", name="Marina Drive"))
    R(RoadDef(_line((-300.0, 700.0), (-300.0, 770.0)), "service", name="Marina Access"))
    R(RoadDef(_line((240.0, 700.0), (240.0, 770.0)), "service", name="Yacht Club Road"))

    # ---------------------------------------------------------------- suburbs
    for x in SUB_X:
        R(RoadDef(_line((x, -660.0), (x, 700.0)), "street", parking=(x != -1480.0), name="Westbrook Lane"))
    for z in SUB_Z:
        R(RoadDef(_line((-1480.0, z), (-1000.0, z)), "street", name="Westbrook Road"))

    # ---------------------------------------------------------------- east side
    for x in EAST_X:
        R(RoadDef(_line((x, -660.0), (x, 640.0)), "street", parking=True, name="Ironworks St"))
    # 560 leaves room for the stadium block in the north
    R(RoadDef(_line((560.0, -450.0), (560.0, 640.0)), "street", parking=True, name="Foundry St"))
    R(RoadDef(_line((750.0, -660.0), (750.0, 700.0)), "street", name="Eastside Road"))
    for z in EAST_Z:
        R(RoadDef(_line((340.0, z), (750.0, z)), "street", parking=(z > 250.0), name="East St"))
    R(RoadDef(_line((340.0, -660.0), (750.0, -660.0)), "street", name="Stadium Road"))
    R(RoadDef(_line((340.0, 640.0), (1500.0, 640.0)), "avenue", name="Harbor Road"))

    # ---------------------------------------------------------------- harbour
    for x in (620.0, 950.0, 1300.0):
        R(RoadDef(_line((x, 640.0), (x, 1040.0)), "service", name="Quay Road"))
    R(RoadDef(_line((340.0, 780.0), (1500.0, 780.0)), "service", name="Terminal Road"))
    R(RoadDef(_line((1500.0, 640.0), (1500.0, 780.0)), "service", name="East Pier Road"))

    # ---------------------------------------------------------------- airport
    R(RoadDef(_line((1000.0, -260.0), (1000.0, 160.0)), "avenue", name="Terminal Drive"))
    R(RoadDef(_line((1000.0, -260.0), (880.0, -260.0)), "service", name="Terminal Loop N"))
    R(RoadDef(_line((1000.0, 160.0), (880.0, 160.0)), "service", name="Terminal Loop S"))
    R(RoadDef(_line((880.0, -600.0), (880.0, 540.0)), "service", name="Airport Service Road"))
    R(RoadDef(_line((750.0, 350.0), (880.0, 350.0)), "service", name="Airport Cargo Link"))

    # ---------------------------------------------------------------- beach access
    for x in (-1300.0, -1000.0, -700.0, -450.0):
        R(RoadDef(_line((x, 700.0), (x, 770.0)), "service", name="Beach Access"))

    # ---------------------------------------------------------------- hills
    # Crestline Drive: from Palmview up into the luxury hills and on to the valley
    crest = _smooth_path([(-580.0, -660.0), (-580.0, -700.0), (-580.0, -790.0), (-530.0, -870.0), (-640.0, -935.0),
                          (-560.0, -1025.0), (-690.0, -1110.0), (-760.0, -1240.0), (-800.0, -1480.0)])
    R(RoadDef(crest, "hill", name="Crestline Drive"))
    R(RoadDef(_smooth_path([(-640.0, -935.0), (-780.0, -900.0), (-940.0, -930.0), (-1100.0, -990.0),
                            (-1280.0, -930.0), (-1360.0, -800.0), (-1360.0, -660.0)]), "hill",
              name="Vista Ridge Road"))
    R(RoadDef(_smooth_path([(-560.0, -1025.0), (-440.0, -990.0), (-360.0, -1060.0), (-420.0, -1160.0),
                            (-560.0, -1180.0), (-690.0, -1110.0)]), "hill", name="Summit Loop"))
    R(RoadDef(_smooth_path([(-1100.0, -990.0), (-1180.0, -1120.0), (-1300.0, -1220.0), (-1400.0, -1480.0)]),
              "hill", name="Canyon Road"))
    # Tower Road up to the TV tower
    R(RoadDef(_smooth_path([(450.0, -660.0), (450.0, -700.0), (450.0, -800.0), (380.0, -890.0), (490.0, -985.0),
                            (420.0, -1080.0), (395.0, -1120.0)]), "hill", name="Tower Road"))
    R(RoadDef(_smooth_path([(395.0, -1120.0), (300.0, -1200.0), (240.0, -1330.0), (200.0, -1480.0)]),
              "hill", name="Tower Road North"))
    # Eastern valley road
    R(RoadDef(_smooth_path([(750.0, -660.0), (820.0, -800.0), (1000.0, -900.0), (1200.0, -1010.0),
                            (1290.0, -1200.0), (1300.0, -1480.0)]), "rural", name="Valley Road"))
    R(RoadDef(_smooth_path([(880.0, -600.0), (930.0, -700.0), (1000.0, -900.0)]), "rural",
              name="Airport North Road"))
    # rural east-west road
    R(RoadDef(_smooth_path([(-1480.0, -1480.0), (-1400.0, -1480.0), (-800.0, -1480.0), (-300.0, -1500.0),
                            (0.0, -1480.0), (200.0, -1480.0), (700.0, -1460.0), (1300.0, -1480.0),
                            (1480.0, -1470.0)], 10.0), "rural", name="Dry Creek Road"))
    R(RoadDef(_smooth_path([(-1480.0, -660.0), (-1500.0, -900.0), (-1450.0, -1150.0), (-1480.0, -1480.0)]),
              "rural", name="West Canyon Road"))
    R(RoadDef(_smooth_path([(700.0, -1460.0), (650.0, -1300.0), (560.0, -1250.0)]), "rural", name="Farm Lane"))
    R(RoadDef(_smooth_path([(-300.0, -1500.0), (-250.0, -1350.0), (-120.0, -1300.0)]), "rural",
              name="Orchard Lane"))

    # ---------------------------------------------------------------- highway ring
    ring = highway_ring_points(10.0)
    R(RoadDef(ring, "highway", level="elevated", heights=[HWY_Y] * len(ring), name="Aurelia Ring"))

    # northern expressway spur with tunnel through the ridge
    spur_ctrl = [(0.0, -720.0), (0.0, -800.0), (40.0, -900.0), (60.0, -1020.0), (20.0, -1160.0),
                 (-20.0, -1300.0), (-10.0, -1400.0), (0.0, -1480.0)]
    spur = _smooth_path(spur_ctrl, 10.0)
    L = G.polyline_length(spur)
    ys = []
    acc = 0.0
    end_y = max(terrain.natural_height(0.0, -1480.0), 0.0)
    for i, p in enumerate(spur):
        if i > 0:
            acc += G.dist(spur[i - 1], p)
        t = acc / L
        s = t * t * (3 - 2 * t)
        ys.append(HWY_Y + (end_y - HWY_Y) * s)
    R(RoadDef(spur, "expressway", level="elevated", heights=ys, name="North Expressway",
              start_level="elevated", end_level="surface"))

    # ---------------------------------------------------------------- interchanges
    x0, x1, z0, z1 = HWY_RING["x0"], HWY_RING["x1"], HWY_RING["z0"], HWY_RING["z1"]
    for k in (-50.0, 350.0):
        for r in _ramp_set("x", x0, k):
            R(r)
        for r in _ramp_set("x", x1, k):
            R(r)
    for k in (-580.0, 450.0):
        for r in _ramp_set("z", z0, k):
            R(r)
    for k in (-580.0, 0.0):
        for r in _ramp_set("z", z1, k):
            R(r)

    for pos, rad in ROUNDABOUTS:
        net.add_roundabout(pos, rad)
