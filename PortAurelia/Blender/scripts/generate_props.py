"""Street furniture and large set-dressing props.

Every builder returns (lod0, lod1). Props face local -Z (the side facing the street).
Some types have visual variants (VARIANTS) that become separate library meshes.
"""
import math

from _common import MeshBuilder, box, cylinder, sweep, G

VARIANTS = {"billboard": 4, "container": 1}
BILLBOARD_ADS = [("SUNFIZZ", (0.95, 0.45, 0.1), (1.0, 1.0, 0.9)), ("NOVA MOBILE", (0.1, 0.25, 0.6), (0.9, 0.95, 1.0)),
                 ("SURF CITY RADIO", (0.1, 0.55, 0.6), (1.0, 0.95, 0.3)), ("VELOCITA", (0.7, 0.05, 0.1), (1.0, 1.0, 1.0))]


def hydrant(rng=None):
    a = MeshBuilder()
    red = (0.75, 0.1, 0.07, 1)
    cylinder(a, (0, 0, 0), 0.16, 0.08, "metal_painted", segs=10, col=red)
    cylinder(a, (0, 0.08, 0), 0.12, 0.55, "metal_painted", segs=10, col=red)
    cylinder(a, (0, 0.63, 0), 0.14, 0.12, "metal_painted", segs=10, r_top=0.05, col=red)
    for ax, s in (("x", 1), ("x", -1)):
        cylinder(a, (0.0, 0.42, 0.0), 0.05, 0.2 * s, "metal_painted", segs=6, col=red, axis="x")
    cylinder(a, (0, 0.42, 0), 0.07, -0.18, "metal_painted", segs=6, col=(0.6, 0.6, 0.6, 1), axis="z")
    return a, a


def trash_can(rng=None):
    a = MeshBuilder()
    g = (0.12, 0.3, 0.2, 1)
    cylinder(a, (0, 0, 0), 0.3, 0.95, "metal_painted", segs=12, col=g)
    cylinder(a, (0, 0.95, 0), 0.33, 0.08, "metal_painted", segs=12, r_top=0.26, col=(0.1, 0.1, 0.1, 1))
    return a, a


def bench(rng=None):
    a = MeshBuilder()
    w = 1.9
    for x in (-0.8, 0.8):
        box(a, (x, 0.22, 0.0), (0.08, 0.44, 0.5), "metal_dark")
        box(a, (x, 0.65, 0.22), (0.08, 0.5, 0.06), "metal_dark")
    for i in range(3):
        box(a, (0, 0.46, -0.16 + i * 0.16), (w, 0.04, 0.12), "wood")
    for i in range(2):
        box(a, (0, 0.62 + i * 0.18, 0.25), (w, 0.12, 0.04), "wood")
    return a, a


def newspaper_box(rng=None):
    a = MeshBuilder()
    col = rng.choice([(0.1, 0.3, 0.7, 1), (0.8, 0.1, 0.1, 1), (0.9, 0.8, 0.1, 1)]) if rng else (0.1, 0.3, 0.7, 1)
    box(a, (0, 0.55, 0), (0.5, 0.7, 0.45), "metal_painted", col=col)
    box(a, (0, 0.1, 0), (0.4, 0.2, 0.35), "metal_dark")
    box(a, (0, 0.65, -0.23), (0.36, 0.28, 0.02), "window_glass")
    return a, a


def parking_meter(rng=None):
    a = MeshBuilder()
    cylinder(a, (0, 0, 0), 0.04, 1.1, "metal", segs=6)
    box(a, (0, 1.25, 0), (0.18, 0.32, 0.14), "metal_painted", col=(0.25, 0.28, 0.3, 1))
    box(a, (0, 1.3, -0.075), (0.1, 0.08, 0.01), "window_glass")
    return a, a


def mailbox(rng=None):
    a = MeshBuilder()
    cylinder(a, (0, 0, 0), 0.04, 1.0, "wood", segs=6)
    box(a, (0, 1.1, 0), (0.22, 0.24, 0.45), "metal_painted", col=(0.2, 0.2, 0.22, 1))
    box(a, (0.13, 1.2, 0.05), (0.02, 0.18, 0.04), "metal_painted", col=(0.8, 0.1, 0.1, 1))
    return a, a


def bus_stop(rng=None):
    a = MeshBuilder()
    for x in (-1.9, 1.9):
        box(a, (x, 1.25, 0.6), (0.08, 2.5, 0.08), "metal")
        box(a, (x, 1.25, -0.4), (0.08, 2.5, 0.08), "metal")
    box(a, (0, 2.55, 0.1), (4.2, 0.1, 1.5), "metal_painted", col=(0.85, 0.87, 0.9, 1), bottom=True)
    box(a, (0, 1.4, 0.62), (3.8, 1.9, 0.03), "glass_clear")
    box(a, (1.9, 1.4, 0.1), (0.03, 1.9, 1.0), "sign_panel")
    box(a, (1.93, 1.5, 0.1), (0.02, 1.5, 0.8), "sign_emissive", col=(0.9, 0.9, 1.0, 1))
    box(a, (-0.5, 0.45, 0.35), (2.2, 0.05, 0.35), "metal")
    cylinder(a, (-2.3, 0, -0.5), 0.04, 2.8, "metal", segs=6)
    box(a, (-2.3, 2.6, -0.5), (0.5, 0.35, 0.03), "metal_painted", col=(0.1, 0.4, 0.8, 1))
    return a, a


def stop_sign(rng=None):
    a = MeshBuilder()
    cylinder(a, (0, 0, 0), 0.035, 2.6, "metal", segs=6)
    pts = []
    for i in range(8):
        ang = math.pi / 8 + 2 * math.pi * i / 8
        pts.append((math.cos(ang) * 0.38, 2.45 + math.sin(ang) * 0.38, -0.04))
    a.face(pts, None, "metal_painted", (0.8, 0.05, 0.05, 1), up=(0, 0, -1))
    a.face(list(reversed(pts)), None, "metal", up=(0, 0, 1))
    return a, a


def planter(rng=None):
    a = MeshBuilder()
    box(a, (0, 0.3, 0), (1.2, 0.6, 1.2), "concrete")
    box(a, (0, 0.58, 0), (1.05, 0.04, 1.05), "dirt")
    from generate_vegetation import crown_cards
    import citygen.geom as GG
    crown_cards(a, GG.Rng(5), (0, 1.0, 0), (0.5, 0.4, 0.5), 8, 0.8, "leaves", 0.3)
    return a, a


def bollard(rng=None):
    a = MeshBuilder()
    cylinder(a, (0, 0, 0), 0.12, 0.9, "metal_dark", segs=8, r_top=0.1)
    return a, a


def dumpster(rng=None):
    a = MeshBuilder()
    col = (0.1, 0.35, 0.2, 1)
    box(a, (0, 0.7, 0), (1.9, 1.2, 1.1), "metal_painted", col=col)
    box(a, (0, 1.33, 0.05), (1.95, 0.06, 1.2), "metal_painted", col=(0.08, 0.08, 0.08, 1))
    for x in (-0.8, 0.8):
        cylinder(a, (x, 0.0, -0.4), 0.08, 0.1, "rubber", segs=6, axis="y")
        cylinder(a, (x, 0.0, 0.4), 0.08, 0.1, "rubber", segs=6, axis="y")
    return a, a


def barrel(rng=None):
    a = MeshBuilder()
    col = rng.choice([(0.1, 0.2, 0.6, 1), (0.7, 0.2, 0.1, 1), (0.2, 0.4, 0.2, 1)]) if rng else (0.1, 0.2, 0.6, 1)
    cylinder(a, (0, 0, 0), 0.3, 0.9, "metal_painted", segs=12, col=col)
    for y in (0.3, 0.6):
        cylinder(a, (0, y, 0), 0.31, 0.03, "metal_painted", segs=12, col=(0.5, 0.5, 0.5, 1), top=False)
    return a, a


def pallet_stack(rng=None):
    a = MeshBuilder()
    for i in range(4):
        box(a, (0, 0.08 + i * 0.15, 0), (1.2, 0.12, 1.0), "wood")
    box(a, (0, 1.0, 0), (1.1, 0.7, 0.9), "fabric", col=(0.75, 0.62, 0.45, 1))
    return a, a


def container(rng=None, color=(0.75, 0.25, 0.15)):
    """20 ft shipping container 6.1 x 2.6 x 2.44 (tinted per instance in Godot)."""
    a = MeshBuilder()
    box(a, (0, 1.3, 0), (6.06, 2.56, 2.44), "container", col=(1, 1, 1, 1), uv_scale=0.5)
    for x in (-3.0, 3.0):
        box(a, (x, 1.3, 0), (0.1, 2.6, 2.46), "metal_painted", col=(0.25, 0.25, 0.25, 1))
    return a, a


def fence_wood(rng=None):
    a = MeshBuilder()
    box(a, (0, 0.75, 0), (0.1, 1.5, 0.1), "wood")
    box(a, (0, 1.2, 0.0), (3.0, 0.1, 0.04), "wood")
    box(a, (0, 0.45, 0.0), (3.0, 0.1, 0.04), "wood")
    for i in range(12):
        box(a, (-1.4 + i * 0.25, 0.8, -0.03), (0.16, 1.5, 0.02), "wood", col=(0.95, 0.93, 0.9, 1))
    return a, a


def fence_chain(rng=None):
    a = MeshBuilder()
    cylinder(a, (0, 0, 0), 0.04, 2.1, "metal", segs=6)
    box(a, (0, 2.05, 0), (3.0, 0.04, 0.04), "metal")
    # mesh panel as semi-transparent card
    a.face([(-1.5, 0.05, 0), (1.5, 0.05, 0), (1.5, 2.0, 0), (-1.5, 2.0, 0)], [(0, 1), (3, 1), (3, 0), (0, 0)],
           "glass_clear", up=(0, 0, -1))
    return a, a


def fence_construction(rng=None):
    a = MeshBuilder()
    box(a, (0, 0.1, 0), (0.6, 0.2, 0.3), "concrete")
    box(a, (0, 1.1, 0), (3.0, 1.9, 0.03), "fabric", col=(0.1, 0.35, 0.2, 1))
    box(a, (-1.5, 1.1, 0), (0.05, 2.0, 0.05), "metal")
    return a, a


def jersey_barrier(rng=None):
    a = MeshBuilder()
    sweep(a, [(-1.5, 0, 0), (1.5, 0, 0)], [(-0.3, 0.0), (0.3, 0.0), (0.12, 0.3), (0.1, 0.8), (-0.1, 0.8), (-0.12, 0.3)],
          "barrier", closed_profile=True)
    return a, a


def traffic_cone(rng=None):
    a = MeshBuilder()
    box(a, (0, 0.02, 0), (0.4, 0.04, 0.4), "plastic", col=(0.1, 0.1, 0.1, 1))
    cylinder(a, (0, 0.04, 0), 0.16, 0.66, "plastic", segs=10, r_top=0.03, col=(1.0, 0.35, 0.05, 1))
    cylinder(a, (0, 0.35, 0), 0.105, 0.1, "plastic", segs=10, r_top=0.09, col=(0.95, 0.95, 0.95, 1))
    return a, a


def material_pile(rng=None):
    a = MeshBuilder()
    for i in range(6):
        box(a, (0, 0.1 + i * 0.2, 0), (4.0, 0.18, 0.9), "wood")
    for i in range(3):
        cylinder(a, (-1.8, 0.2 + i * 0.35, 1.4), 0.15, 4.0, "metal", segs=6, axis="x")
    return a, a


def billboard(rng=None, variant=0):
    a = MeshBuilder()
    text, bg, fg = BILLBOARD_ADS[variant % len(BILLBOARD_ADS)]
    cylinder(a, (0, 0, 0), 0.35, 9.0, "metal", segs=10)
    box(a, (0, 9.0, 0.3), (12.4, 0.2, 0.9), "metal_dark")
    box(a, (0, 11.6, 0.1), (12.0, 5.0, 0.3), "metal_dark")
    box(a, (0, 11.6, -0.06), (11.6, 4.6, 0.02), "sign_emissive", col=(bg[0], bg[1], bg[2], 1))
    for x in (-4, 0, 4):
        box(a, (x, 14.3, -0.4), (0.1, 0.1, 0.9), "metal")
        box(a, (x, 14.2, -0.8), (0.5, 0.15, 0.2), "light_emissive")
    a._billboard_text = (text, fg)
    return a, a


def beach_umbrella(rng=None):
    a = MeshBuilder()
    cylinder(a, (0, 0, 0), 0.03, 2.3, "metal", segs=6)
    col = rng.choice([(0.9, 0.2, 0.2, 1), (0.1, 0.5, 0.8, 1), (0.95, 0.8, 0.2, 1), (0.2, 0.7, 0.4, 1)]) if rng else (0.9, 0.2, 0.2, 1)
    segs = 8
    for i in range(segs):
        a0 = 2 * math.pi * i / segs
        a1 = 2 * math.pi * (i + 1) / segs
        c = col if i % 2 == 0 else (0.95, 0.95, 0.92, 1)
        a.face([(0, 2.5, 0), (math.cos(a1) * 1.4, 2.0, math.sin(a1) * 1.4), (math.cos(a0) * 1.4, 2.0, math.sin(a0) * 1.4)],
               None, "fabric", c, up=(0, 1, 0))
        a.face([(0, 2.49, 0), (math.cos(a0) * 1.4, 1.99, math.sin(a0) * 1.4), (math.cos(a1) * 1.4, 1.99, math.sin(a1) * 1.4)],
               None, "fabric", c, up=(0, -1, 0))
    box(a, (0.9, 0.2, 0.4), (0.6, 0.05, 1.8), "fabric", col=(0.2, 0.5, 0.8, 1))
    return a, a


def lifeguard_tower(rng=None):
    a = MeshBuilder()
    for x in (-1.0, 1.0):
        for z in (-1.0, 1.0):
            box(a, (x, 1.3, z), (0.15, 2.6, 0.15), "wood", col=(0.95, 0.95, 0.95, 1))
    box(a, (0, 2.6, 0), (2.8, 0.15, 2.8), "wood")
    box(a, (0, 3.6, 0), (2.4, 1.9, 2.4), "metal_painted", col=(0.3, 0.75, 0.85, 1))
    box(a, (0, 3.9, -1.21), (1.8, 0.8, 0.02), "window_glass")
    box(a, (0, 4.7, 0), (3.0, 0.2, 3.0), "metal_painted", col=(0.95, 0.35, 0.3, 1), bottom=True)
    sweep(a, [(0, 0.0, -3.0), (0, 2.6, -1.3)], [(-0.5, 0), (0.5, 0), (0.5, 0.06), (-0.5, 0.06)], "wood", closed_profile=True)
    return a, a


def pool(rng=None):
    a = MeshBuilder()
    box(a, (0, 0.03, 0), (9.0, 0.06, 5.0), "sidewalk", col=(0.95, 0.95, 0.95, 1))
    a.face([(-4.0, 0.07, -2.0), (4.0, 0.07, -2.0), (4.0, 0.07, 2.0), (-4.0, 0.07, 2.0)], None, "water_deco", up=(0, 1, 0))
    return a, a


def fountain(rng=None):
    a = MeshBuilder()
    cylinder(a, (0, 0, 0), 5.0, 0.6, "concrete", segs=24)
    cylinder(a, (0, 0.55, 0), 4.6, 0.02, "water_deco", segs=24)
    cylinder(a, (0, 0.6, 0), 0.8, 1.6, "concrete", segs=12, r_top=0.4)
    cylinder(a, (0, 2.2, 0), 1.8, 0.3, "concrete", segs=16, r_top=1.9)
    cylinder(a, (0, 2.5, 0), 1.7, 0.02, "water_deco", segs=16)
    return a, a


def pavilion(rng=None):
    a = MeshBuilder()
    for i in range(8):
        ang = 2 * math.pi * i / 8
        cylinder(a, (math.cos(ang) * 4.0, 0.3, math.sin(ang) * 4.0), 0.18, 3.2, "trim", segs=8)
    cylinder(a, (0, 0, 0), 4.6, 0.3, "concrete", segs=16)
    cylinder(a, (0, 3.5, 0), 4.8, 1.8, "roof_metal", segs=16, r_top=0.3, col=(0.3, 0.5, 0.45, 1))
    return a, a


def playground(rng=None):
    a = MeshBuilder()
    box(a, (0, 0.02, 0), (12.0, 0.04, 9.0), "sand")
    for x in (-3.0, 3.0):
        box(a, (x, 1.6, 0), (0.12, 3.2, 0.12), "metal_painted", col=(0.9, 0.2, 0.2, 1))
    box(a, (0, 3.2, 0), (6.2, 0.12, 0.12), "metal_painted", col=(0.9, 0.2, 0.2, 1))
    box(a, (0, 1.5, 3.0), (2.0, 0.15, 2.0), "wood")
    for (x, z) in ((-1, 2), (1, 2), (-1, 4), (1, 4)):
        box(a, (x, 0.75, z), (0.12, 1.5, 0.12), "metal_painted", col=(0.2, 0.5, 0.9, 1))
    sweep(a, [(0, 1.5, 4.0), (0, 0.1, 7.0)], [(-0.4, 0), (0.4, 0), (0.4, 0.05), (-0.4, 0.05)], "plastic", closed_profile=True,
          col=(0.95, 0.8, 0.1, 1))
    return a, a


# ------------------------------------------------------------------ boats, planes, cranes
def _hull(mb, length, beam, depth, col, deck_col=(0.9, 0.88, 0.84, 1)):
    """Simple lofted hull along -Z (bow at -Z)."""
    n = 10
    rings = []
    for i in range(n + 1):
        t = i / n
        z = -length * 0.5 + length * t
        bow = min(1.0, t * 3.0) if True else 1.0
        w = beam * 0.5 * (0.25 + 0.75 * math.sin(min(1.0, t * 1.6) * math.pi * 0.5)) * (0.2 + 0.8 * bow)
        ring = [(-w, depth * 0.8, z), (-w * 0.9, 0.0, z), (-w * 0.3, -depth * 0.2, z), (w * 0.3, -depth * 0.2, z),
                (w * 0.9, 0.0, z), (w, depth * 0.8, z)]
        rings.append(ring)
    for i in range(n):
        for k in range(len(rings[i]) - 1):
            a, b = rings[i][k], rings[i][k + 1]
            c, d = rings[i + 1][k + 1], rings[i + 1][k]
            mb.face([a, b, c, d], None, "metal_painted", col)
    deck = [r[0] for r in rings] + [r[-1] for r in reversed(rings)]
    mb.face([(p[0], p[1], p[2]) for p in deck], None, "wood_planks", deck_col, up=(0, 1, 0))


def boat_small(rng=None):
    a = MeshBuilder()
    _hull(a, 6.0, 2.2, 1.0, (0.95, 0.95, 0.95, 1))
    box(a, (0, 1.3, 0.8), (1.4, 0.8, 1.4), "metal_painted", col=(0.95, 0.95, 0.95, 1))
    box(a, (0, 1.4, 0.1), (1.3, 0.5, 0.04), "window_glass")
    return a, a


def sailboat(rng=None):
    a = MeshBuilder()
    _hull(a, 9.0, 3.0, 1.2, (0.92, 0.92, 0.95, 1))
    cylinder(a, (0, 0.9, -0.5), 0.08, 12.0, "metal", segs=6)
    box(a, (0, 1.4, 1.5), (1.8, 0.6, 3.0), "metal_painted", col=(0.95, 0.95, 0.95, 1))
    return a, a


def yacht(rng=None):
    a = MeshBuilder()
    _hull(a, 18.0, 5.0, 2.2, (0.98, 0.98, 0.98, 1))
    box(a, (0, 2.8, 1.5), (4.0, 1.6, 9.0), "metal_painted", col=(0.97, 0.97, 0.97, 1))
    box(a, (0, 2.9, 1.5), (4.05, 0.6, 8.0), "window_glass")
    box(a, (0, 4.1, 2.5), (3.2, 1.2, 5.0), "metal_painted", col=(0.97, 0.97, 0.97, 1))
    box(a, (0, 4.2, 2.5), (3.25, 0.5, 4.5), "window_glass")
    return a, a


def tug_boat(rng=None):
    a = MeshBuilder()
    _hull(a, 22.0, 8.0, 3.0, (0.12, 0.12, 0.14, 1), (0.6, 0.2, 0.15, 1))
    box(a, (0, 4.5, 2.0), (5.0, 3.0, 7.0), "metal_painted", col=(0.95, 0.95, 0.9, 1))
    box(a, (0, 6.8, 1.0), (4.0, 1.6, 3.5), "metal_painted", col=(0.95, 0.95, 0.9, 1))
    box(a, (0, 7.0, 1.0), (4.05, 0.7, 3.2), "window_glass")
    cylinder(a, (0, 6.0, 4.5), 0.7, 3.0, "metal_painted", segs=10, col=(0.8, 0.15, 0.1, 1))
    return a, a


def cargo_ship(rng=None):
    a = MeshBuilder()
    L, B = 170.0, 26.0
    _hull(a, L, B, 14.0, (0.12, 0.22, 0.35, 1), (0.5, 0.2, 0.15, 1))
    # superstructure at the stern (+Z)
    box(a, (0, 20.0, L * 0.4), (20.0, 12.0, 12.0), "metal_painted", col=(0.95, 0.95, 0.92, 1))
    box(a, (0, 27.0, L * 0.4), (18.0, 2.0, 10.0), "window_glass")
    cylinder(a, (0, 26.0, L * 0.45), 2.5, 10.0, "metal_painted", segs=10, col=(0.8, 0.2, 0.1, 1))
    # container stacks on deck
    import citygen.geom as GG
    r = GG.Rng(11)
    palette = [(0.75, 0.2, 0.15), (0.15, 0.35, 0.6), (0.2, 0.55, 0.3), (0.85, 0.65, 0.15), (0.6, 0.6, 0.62), (0.45, 0.15, 0.4)]
    z = -L * 0.35
    while z < L * 0.3:
        for x in range(-4, 5):
            h = r.randint(1, 4)
            for k in range(h):
                c = r.choice(palette)
                box(a, (x * 2.5, 12.0 + k * 2.6 + 1.3, z), (2.4, 2.5, 6.0), "container", col=(c[0], c[1], c[2], 1))
        z += 6.4
    return a, a


def airliner(rng=None):
    a = MeshBuilder()
    L = 44.0
    livery = (0.95, 0.96, 0.97, 1)
    cylinder(a, (0, 3.5, -L * 0.5), 2.0, L, "metal_painted", segs=14, col=livery, axis="z")
    cylinder(a, (0, 3.5, -L * 0.5), 2.0, -4.0, "metal_painted", segs=14, r_top=0.4, col=livery, axis="z")
    cylinder(a, (0, 3.7, L * 0.5), 2.0, 6.0, "metal_painted", segs=14, r_top=0.5, col=livery, axis="z")
    # windows band + cheatline
    box(a, (0, 4.3, 0), (4.05, 0.25, L - 6), "window_glass")
    box(a, (0, 3.2, 0), (4.05, 0.3, L - 4), "metal_painted", col=(0.1, 0.35, 0.7, 1))
    # wings
    for sx in (-1, 1):
        a.face([(sx * 1.8, 2.6, -3.0), (sx * 19.0, 3.4, 5.0), (sx * 19.0, 3.4, 7.5), (sx * 1.8, 2.6, 6.0)], None,
               "metal_painted", livery, up=(0, 1, 0))
        a.face([(sx * 1.8, 2.5, -3.0), (sx * 1.8, 2.5, 6.0), (sx * 19.0, 3.3, 7.5), (sx * 19.0, 3.3, 5.0)], None,
               "metal_painted", (0.7, 0.72, 0.75, 1), up=(0, -1, 0))
        cylinder(a, (sx * 7.0, 1.8, -1.0), 1.1, 4.2, "metal_painted", segs=12, col=(0.8, 0.82, 0.85, 1), axis="z")
        a.face([(sx * 1.0, 4.5, L * 0.5), (sx * 8.0, 4.8, L * 0.5 + 4.0), (sx * 8.0, 4.8, L * 0.5 + 5.5), (sx * 1.0, 4.5, L * 0.5 + 5.0)],
               None, "metal_painted", livery, up=(0, 1, 0))
    # tail fin
    a.face([(0, 5.0, L * 0.5 - 2.0), (0, 12.0, L * 0.5 + 4.5), (0, 12.0, L * 0.5 + 6.5), (0, 5.0, L * 0.5 + 5.5)], None,
           "metal_painted", (0.1, 0.35, 0.7, 1), up=(1, 0, 0))
    a.face([(0, 5.0, L * 0.5 - 2.0), (0, 5.0, L * 0.5 + 5.5), (0, 12.0, L * 0.5 + 6.5), (0, 12.0, L * 0.5 + 4.5)], None,
           "metal_painted", (0.1, 0.35, 0.7, 1), up=(-1, 0, 0))
    # gear
    for (x, z) in ((0, -16.0), (-3.0, 2.0), (3.0, 2.0)):
        cylinder(a, (x, 1.5, z), 0.12, -1.0, "metal", segs=6)
        cylinder(a, (x - 0.2, 0.5, z), 0.5, 0.4, "rubber", segs=10, axis="x")
    return a, a


def small_plane(rng=None):
    a = MeshBuilder()
    col = (0.95, 0.95, 0.95, 1)
    cylinder(a, (0, 1.4, -4.0), 0.7, 8.0, "metal_painted", segs=10, r_top=0.3, col=col, axis="z")
    box(a, (0, 1.9, -1.0), (11.0, 0.12, 1.6), "metal_painted", col=col)
    box(a, (0, 2.2, 3.5), (0.1, 1.4, 1.2), "metal_painted", col=(0.8, 0.1, 0.1, 1))
    box(a, (0, 1.5, 3.8), (3.6, 0.08, 0.9), "metal_painted", col=col)
    box(a, (0, 1.8, -2.2), (1.2, 0.5, 1.2), "window_glass")
    return a, a


def gantry_crane(rng=None):
    """Ship-to-shore container crane (landmark). Boom points to -Z (over the water)."""
    a = MeshBuilder()
    col = (0.85, 0.22, 0.12, 1)
    for x in (-9.0, 9.0):
        for z in (-8.0, 8.0):
            box(a, (x, 24.0, z), (1.4, 48.0, 1.4), "metal_painted", col=col)
        box(a, (x, 1.2, 0), (2.0, 2.4, 20.0), "metal_painted", col=(0.3, 0.3, 0.3, 1))
    for y in (20.0, 46.0):
        box(a, (0, y, -8.0), (19.4, 1.6, 1.4), "metal_painted", col=col)
        box(a, (0, y, 8.0), (19.4, 1.6, 1.4), "metal_painted", col=col)
        box(a, (-9.0, y, 0), (1.4, 1.6, 17.0), "metal_painted", col=col)
        box(a, (9.0, y, 0), (1.4, 1.6, 17.0), "metal_painted", col=col)
    # boom
    box(a, (0, 48.5, -22.0), (5.0, 3.0, 76.0), "metal_painted", col=col)
    box(a, (0, 50.5, 20.0), (6.0, 5.0, 10.0), "metal_painted", col=(0.95, 0.95, 0.95, 1))
    box(a, (0, 60.0, 4.0), (1.2, 20.0, 1.2), "metal_painted", col=col)
    sweep(a, [(0, 70.0, 4.0), (0, 50.0, -55.0)], [(-0.15, -0.15), (0.15, -0.15), (0.15, 0.15), (-0.15, 0.15)], "metal",
          closed_profile=True)
    sweep(a, [(0, 70.0, 4.0), (0, 51.0, 22.0)], [(-0.15, -0.15), (0.15, -0.15), (0.15, 0.15), (-0.15, 0.15)], "metal",
          closed_profile=True)
    box(a, (0, 45.0, -30.0), (4.0, 3.0, 4.0), "metal_painted", col=(0.95, 0.95, 0.95, 1))
    return a, a


def tower_crane(rng=None):
    a = MeshBuilder()
    col = (0.95, 0.75, 0.1, 1)
    H = 55.0
    for (x, z) in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
        box(a, (x * 0.9, H * 0.5, z * 0.9), (0.2, H, 0.2), "metal_painted", col=col)
    y = 3.0
    while y < H:
        box(a, (0, y, -0.9), (1.8, 0.12, 0.12), "metal_painted", col=col)
        box(a, (0, y, 0.9), (1.8, 0.12, 0.12), "metal_painted", col=col)
        box(a, (-0.9, y, 0), (0.12, 0.12, 1.8), "metal_painted", col=col)
        box(a, (0.9, y, 0), (0.12, 0.12, 1.8), "metal_painted", col=col)
        y += 3.0
    box(a, (0, H + 1.0, -14.0), (1.4, 1.4, 44.0), "metal_painted", col=col)
    box(a, (0, H + 0.5, 6.0), (2.5, 2.0, 4.0), "concrete")
    box(a, (1.6, H - 1.0, 0.0), (2.0, 2.2, 2.4), "metal_painted", col=(0.9, 0.9, 0.9, 1))
    cylinder(a, (0, H + 2.0, 0), 0.25, 6.0, "metal_painted", segs=6, col=col)
    sweep(a, [(0, H - 10.0, -28.0), (0, H + 0.3, -28.0)], [(-0.02, -0.02), (0.02, -0.02), (0.02, 0.02), (-0.02, 0.02)], "metal",
          closed_profile=True)
    box(a, (0, H - 10.5, -28.0), (0.6, 0.6, 0.6), "metal_dark")
    return a, a


def ferris_wheel(rng=None):
    a = MeshBuilder()
    R = 20.0
    cy = R + 4.0
    for sx in (-2.0, 2.0):
        sweep(a, [(sx, 0, -8.0), (sx, cy, 0)], [(-0.3, -0.3), (0.3, -0.3), (0.3, 0.3), (-0.3, 0.3)], "metal_painted",
              closed_profile=True, col=(0.95, 0.95, 0.95, 1))
        sweep(a, [(sx, 0, 8.0), (sx, cy, 0)], [(-0.3, -0.3), (0.3, -0.3), (0.3, 0.3), (-0.3, 0.3)], "metal_painted",
              closed_profile=True, col=(0.95, 0.95, 0.95, 1))
        ring = [(sx * 0.8, cy + math.sin(2 * math.pi * i / 32) * R, math.cos(2 * math.pi * i / 32) * R) for i in range(33)]
        sweep(a, ring, [(-0.2, -0.2), (0.2, -0.2), (0.2, 0.2), (-0.2, 0.2)], "metal_painted", closed_profile=True,
              col=(0.95, 0.95, 0.95, 1))
    cylinder(a, (-2.5, cy, 0), 0.8, 5.0, "metal", segs=10, axis="x")
    for i in range(16):
        ang = 2 * math.pi * i / 16
        p = (0, cy + math.sin(ang) * R, math.cos(ang) * R)
        sweep(a, [(0, cy, 0), p], [(-0.08, -0.08), (0.08, -0.08), (0.08, 0.08), (-0.08, 0.08)], "metal", closed_profile=True)
        colr = [(0.9, 0.2, 0.2, 1), (0.2, 0.5, 0.9, 1), (0.95, 0.8, 0.1, 1), (0.2, 0.75, 0.4, 1)][i % 4]
        box(a, (p[0], p[1] - 1.6, p[2]), (2.2, 2.0, 2.0), "metal_painted", col=colr)
        box(a, (p[0], p[1] - 0.4, p[2]), (0.3, 0.3, 0.3), "light_emissive", col=(1, 0.9, 0.6, 1))
    return a, a


PROPS = {
    "hydrant": hydrant, "trash_can": trash_can, "bench": bench, "newspaper_box": newspaper_box,
    "parking_meter": parking_meter, "mailbox": mailbox, "bus_stop": bus_stop, "stop_sign": stop_sign,
    "planter": planter, "bollard": bollard, "dumpster": dumpster, "barrel": barrel, "pallet_stack": pallet_stack,
    "container": container, "fence_wood": fence_wood, "fence_chain": fence_chain,
    "fence_construction": fence_construction, "jersey_barrier": jersey_barrier, "traffic_cone": traffic_cone,
    "material_pile": material_pile, "billboard": billboard, "beach_umbrella": beach_umbrella,
    "lifeguard_tower": lifeguard_tower, "pool": pool, "fountain": fountain, "pavilion": pavilion,
    "playground": playground, "boat_small": boat_small, "sailboat": sailboat, "yacht": yacht, "tug_boat": tug_boat,
    "cargo_ship": cargo_ship, "airliner": airliner, "small_plane": small_plane, "gantry_crane": gantry_crane,
    "tower_crane": tower_crane, "ferris_wheel": ferris_wheel,
}
