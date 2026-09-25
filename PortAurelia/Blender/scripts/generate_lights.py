"""Street lights and traffic light parts.

Convention: props face local -Z (towards the road for lamps). Light source
positions (for Godot's light pool) are listed in LIGHT_POINTS.
Traffic lights are assembled in Godot from tl_pole + tl_arm (scaled along X)
+ tl_head (lenses coloured via vertex colour, lit by a per-instance state).
"""
import math

from _common import MeshBuilder, box, cylinder, sweep

LIGHT_POINTS = {
    "lamp_street": [(0.0, 8.25, -2.35)],
    "lamp_plaza": [(0.0, 3.9, 0.0)],
    "lamp_wood": [(0.0, 7.2, -1.3)],
    "lamp_highway": [(0.0, 10.2, -3.2)],
    "lamp_highway_double": [(-3.2, 10.2, 0.0), (3.2, 10.2, 0.0)],
}


def lamp_street(rng=None):
    a = MeshBuilder()
    b = MeshBuilder()
    for mb, segs in ((a, 10), (b, 6)):
        cylinder(mb, (0, 0, 0), 0.22, 0.6, "metal_dark", segs=segs, r_top=0.18)
        cylinder(mb, (0, 0.6, 0), 0.12, 7.5, "metal", segs=segs, r_top=0.08, col=(0.55, 0.57, 0.6, 1))
    # curved arm towards -Z
    pts = []
    for i in range(9):
        t = i / 8
        pts.append((0.0, 8.0 + math.sin(t * math.pi * 0.5) * 0.35, -t * 2.3))
    sweep(a, pts, [(-0.05, -0.05), (0.05, -0.05), (0.05, 0.05), (-0.05, 0.05)], "metal", closed_profile=True)
    sweep(b, pts[::2], [(-0.05, -0.05), (0.05, -0.05), (0.05, 0.05), (-0.05, 0.05)], "metal", closed_profile=True)
    for mb in (a, b):
        box(mb, (0.0, 8.4, -2.35), (0.42, 0.14, 0.85), "metal_dark", bottom=True)
        box(mb, (0.0, 8.32, -2.35), (0.34, 0.02, 0.7), "light_emissive", bottom=True)
    return a, b


def lamp_plaza(rng=None):
    a = MeshBuilder()
    for mb in (a,):
        cylinder(mb, (0, 0, 0), 0.12, 0.4, "metal_dark", segs=8)
        cylinder(mb, (0, 0.4, 0), 0.06, 3.2, "metal_dark", segs=8)
        cylinder(mb, (0, 3.6, 0), 0.2, 0.55, "light_emissive", segs=8, r_top=0.24)
        cylinder(mb, (0, 4.15, 0), 0.28, 0.12, "metal_dark", segs=8, r_top=0.05)
    return a, a


def lamp_wood(rng=None):
    a = MeshBuilder()
    cylinder(a, (0, 0, 0), 0.14, 8.0, "wood", segs=7, r_top=0.11)
    box(a, (0, 7.3, -0.7), (0.08, 0.08, 1.4), "metal")
    box(a, (0, 7.25, -1.35), (0.25, 0.12, 0.5), "metal_dark", bottom=True)
    box(a, (0, 7.18, -1.35), (0.18, 0.02, 0.4), "light_emissive", bottom=True)
    return a, a


def lamp_highway(rng=None):
    a = MeshBuilder()
    cylinder(a, (0, 0, 0), 0.18, 10.4, "metal", segs=8, r_top=0.1)
    box(a, (0, 10.35, -1.6), (0.1, 0.1, 3.2), "metal")
    box(a, (0, 10.3, -3.25), (0.4, 0.14, 0.9), "metal_dark", bottom=True)
    box(a, (0, 10.22, -3.25), (0.32, 0.02, 0.75), "light_emissive", bottom=True)
    return a, a


def lamp_highway_double(rng=None):
    a = MeshBuilder()
    cylinder(a, (0, 0.8, 0), 0.18, 9.6, "metal", segs=8, r_top=0.1)
    box(a, (0, 10.35, 0), (6.4, 0.1, 0.1), "metal")
    for sx in (-1, 1):
        box(a, (sx * 3.25, 10.3, 0), (0.9, 0.14, 0.4), "metal_dark", bottom=True)
        box(a, (sx * 3.25, 10.22, 0), (0.75, 0.02, 0.32), "light_emissive", bottom=True)
    return a, a


# ------------------------------------------------------------------ traffic lights
def tl_pole(rng=None):
    a = MeshBuilder()
    cylinder(a, (0, 0, 0), 0.2, 0.5, "metal_dark", segs=8, r_top=0.16)
    cylinder(a, (0, 0.5, 0), 0.13, 6.0, "metal_painted", segs=10, r_top=0.11, col=(0.2, 0.21, 0.22, 1))
    # pedestrian push button box + small side signal
    box(a, (0.0, 1.1, 0.16), (0.14, 0.22, 0.08), "metal_painted", col=(0.85, 0.75, 0.1, 1))
    box(a, (0.0, 3.2, 0.32), (0.32, 0.9, 0.26), "metal_painted", col=(0.12, 0.12, 0.12, 1))
    return a, a


def tl_arm(rng=None):
    """Unit-length arm along local -X at 6.1 m height (scaled in X by Godot)."""
    a = MeshBuilder()
    box(a, (-0.5, 6.1, 0.0), (1.0, 0.16, 0.16), "metal_painted", col=(0.2, 0.21, 0.22, 1))
    return a, a


def tl_head(rng=None):
    """Signal head: body, visors and three lenses (vertex colour identifies R/Y/G)."""
    a = MeshBuilder()
    box(a, (0, 0, 0), (0.38, 1.05, 0.28), "metal_painted", col=(0.1, 0.1, 0.1, 1))
    box(a, (0, 0, -0.02), (0.6, 1.3, 0.04), "metal_painted", col=(0.05, 0.05, 0.05, 1))  # backplate
    for i, col in enumerate(((1.0, 0.1, 0.05, 1), (1.0, 0.65, 0.0, 1), (0.1, 1.0, 0.45, 1))):
        y = 0.33 - i * 0.33
        cylinder(a, (0, y, 0.14), 0.12, 0.02, "signal_lens", segs=12, col=col, axis="z")
        # visor
        box(a, (0, y + 0.12, 0.24), (0.3, 0.02, 0.2), "metal_painted", col=(0.1, 0.1, 0.1, 1))
    return a, a


LIGHTS = {
    "lamp_street": lamp_street, "lamp_plaza": lamp_plaza, "lamp_wood": lamp_wood,
    "lamp_highway": lamp_highway, "lamp_highway_double": lamp_highway_double,
    "tl_pole": tl_pole, "tl_arm": tl_arm, "tl_head": tl_head,
}
