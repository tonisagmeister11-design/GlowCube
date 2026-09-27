"""Street lights and traffic light parts.

Convention: props face local -Z (towards the road for lamps). Light source
positions (for Godot's light pool) are listed in LIGHT_POINTS.
Traffic lights are assembled in Godot from tl_pole + tl_arm (scaled along X)
+ tl_head (lenses coloured via vertex colour, lit by a per-instance state).
"""
import math

from _common import MeshBuilder, box, cylinder, sweep

LIGHT_POINTS = {
    "lamp_street": [(0.0, 8.2, -2.4)],
    "lamp_plaza": [(0.0, 3.8, 0.0)],
    "lamp_wood": [(0.0, 7.15, -1.38)],
    "lamp_highway": [(0.0, 10.4, -3.3)],
    "lamp_highway_double": [(-3.4, 10.3, 0.0), (3.4, 10.3, 0.0)],
}


POLE = (0.24, 0.25, 0.26, 1)        # anthracite powder coat
GALV = (0.62, 0.64, 0.66, 1)        # galvanised steel


def _section(cx, cy, cz, w, h, n=12, axis="z", e=0.55):
    """Rounded-rectangle (superellipse) cross-section in the XY plane at depth cz."""
    pts = []
    for i in range(n):
        a = 2 * math.pi * (i + 0.5) / n
        c, s_ = math.cos(a), math.sin(a)
        x = w * 0.5 * math.copysign(abs(c) ** e, c)
        y = h * 0.5 * math.copysign(abs(s_) ** e, s_)
        pts.append((cx + x, cy + y, cz))
    return pts


def loft(mb, sections, mat, col=(1, 1, 1, 1), caps=True):
    """Skin consecutive closed sections (same vertex count) into a smooth solid."""
    n = len(sections[0])
    for k in range(len(sections) - 1):
        s0, s1 = sections[k], sections[k + 1]
        c0 = [sum(p[i] for p in s0) / n for i in range(3)]
        for i in range(n):
            a, b = s0[i], s0[(i + 1) % n]
            c, d = s1[(i + 1) % n], s1[i]
            mid = [(a[j] + b[j] + c[j] + d[j]) * 0.25 for j in range(3)]
            out = (mid[0] - c0[0], mid[1] - c0[1], 0.0)
            mb.face([a, b, c, d], None, mat, col, up=out)
    if caps:
        for sec, sign in ((sections[0], 1), (sections[-1], -1)):
            cz = sec[0][2]
            other = sections[1][0][2] if sign == 1 else sections[-2][0][2]
            mb.face(list(sec), None, mat, col, up=(0, 0, cz - other))


def _octa_pole(mb, y0, y1, r0, r1, col, segs=8):
    cylinder(mb, (0, y0, 0), r0, y1 - y0, "metal_painted", segs=segs, r_top=r1, col=col)


def _base(mb, col, segs=12):
    """Cast base: plinth, flared skirt with an access door and a collar ring."""
    cylinder(mb, (0, 0, 0), 0.3, 0.12, "concrete", segs=segs, r_top=0.28)
    cylinder(mb, (0, 0.12, 0), 0.24, 0.62, "metal_painted", segs=segs, r_top=0.15, col=col)
    cylinder(mb, (0, 0.74, 0), 0.17, 0.06, "metal_painted", segs=segs, col=col)
    box(mb, (0, 0.42, -0.2), (0.14, 0.3, 0.03), "metal_painted", col=(col[0] * 0.8, col[1] * 0.8, col[2] * 0.8, 1))


def lamp_street(rng=None):
    """Modern LED street light: cast base, tapered octagonal mast, swept arm and a slim
    aerodynamic luminaire with a glowing diffuser underneath."""
    a = MeshBuilder()
    b = MeshBuilder()
    for mb, segs, lo in ((a, 12, False), (b, 6, True)):
        if lo:
            cylinder(mb, (0, 0, 0), 0.22, 0.75, "metal_painted", segs=segs, r_top=0.15, col=POLE)
        else:
            _base(mb, POLE, segs)
        _octa_pole(mb, 0.78, 8.1, 0.12, 0.075, POLE, 8 if not lo else 6)
        cylinder(mb, (0, 8.1, 0), 0.085, 0.08, "metal_painted", segs=8, col=POLE)
    # arm: rises out of the mast top and sweeps out over the road
    pts = []
    for i in range(11):
        t = i / 10
        pts.append((0.0, 8.05 + math.sin(t * math.pi * 0.62) * 0.42, -t * 2.0))
    prof = [(math.cos(k * math.pi / 4) * 0.05, math.sin(k * math.pi / 4) * 0.045) for k in range(8)]
    sweep(a, pts, prof, "metal_painted", POLE, closed_profile=True)
    sweep(b, pts[::3] + [pts[-1]], prof[::2], "metal_painted", POLE, closed_profile=True)
    # luminaire: lofted wedge, thin at the arm, widest over the diffuser
    ys = 8.36
    secs = [(-1.9, 0.1, 0.09, 0.0), (-2.02, 0.3, 0.13, 0.0), (-2.3, 0.44, 0.15, 0.005), (-2.62, 0.44, 0.13, 0.01),
            (-2.84, 0.3, 0.09, 0.012), (-2.9, 0.16, 0.05, 0.012)]
    loft(a, [_section(0, ys + dy, z, w, h) for (z, w, h, dy) in secs], "metal_painted", POLE)
    loft(b, [_section(0, ys + dy, z, w, h, n=6) for (z, w, h, dy) in secs[::2] + [secs[-1]]], "metal_painted", POLE)
    for mb in (a, b):
        # diffuser (lit) and a thin heat-sink ridge on top
        box(mb, (0.0, ys - 0.07, -2.4), (0.34, 0.012, 0.66), "light_emissive", bottom=True, top=False)
    for k in range(4):
        box(a, (0.0, ys + 0.085, -2.1 - k * 0.18), (0.3, 0.02, 0.03), "metal_painted", col=POLE)
    # small asset plate on the mast
    box(a, (0.0, 2.6, -0.105), (0.12, 0.16, 0.02), "metal_painted", col=(0.85, 0.8, 0.2, 1))
    return a, b


def lamp_plaza(rng=None):
    """Classic cast-iron lantern: fluted column on a tiered base, four-pane glass lantern with
    a pitched roof and finial."""
    a = MeshBuilder()
    b = MeshBuilder()
    col = (0.07, 0.09, 0.08, 1)
    for mb, segs in ((a, 12), (b, 6)):
        cylinder(mb, (0, 0, 0), 0.24, 0.16, "metal_painted", segs=segs, r_top=0.22, col=col)
        cylinder(mb, (0, 0.16, 0), 0.17, 0.5, "metal_painted", segs=segs, r_top=0.1, col=col)
        cylinder(mb, (0, 0.66, 0), 0.12, 0.08, "metal_painted", segs=segs, col=col)
        cylinder(mb, (0, 0.74, 0), 0.07, 2.55, "metal_painted", segs=segs, r_top=0.055, col=col)
        cylinder(mb, (0, 3.29, 0), 0.1, 0.1, "metal_painted", segs=segs, r_top=0.15, col=col)
        cylinder(mb, (0, 3.39, 0), 0.16, 0.05, "metal_painted", segs=4, col=col)
    if True:
        mb = a
        # fluting on the column
        for k in range(8):
            ang = k * math.pi / 4
            box(mb, (math.cos(ang) * 0.06, 1.95, math.sin(ang) * 0.06), (0.018, 2.2, 0.018), "metal_painted", col=col)
    for mb in (a, b):
        # lantern: glowing core + 4 corner posts + roof
        cylinder(mb, (0, 3.44, 0), 0.16, 0.68, "light_emissive", segs=4, r_top=0.21, top=False)
        for k in range(4):
            ang = k * math.pi / 2
            box(mb, (math.cos(ang) * 0.2, 3.78, math.sin(ang) * 0.2), (0.03, 0.72, 0.03), "metal_painted", col=col)
        cylinder(mb, (0, 4.12, 0), 0.3, 0.05, "metal_painted", segs=4, col=col)
        cylinder(mb, (0, 4.17, 0), 0.27, 0.28, "metal_painted", segs=4, r_top=0.03, col=col)
        cylinder(mb, (0, 4.45, 0), 0.025, 0.14, "metal_painted", segs=6, col=col)
    return a, b


def lamp_wood(rng=None):
    """Timber utility pole with a crossarm, insulators and a small cobra-head luminaire."""
    a = MeshBuilder()
    cylinder(a, (0, 0, 0), 0.15, 8.0, "wood", segs=8, r_top=0.11)
    box(a, (0, 7.6, 0), (1.6, 0.1, 0.1), "wood")
    for x in (-0.7, -0.35, 0.35, 0.7):
        cylinder(a, (x, 7.65, 0), 0.03, 0.12, "metal_painted", segs=6, col=(0.55, 0.6, 0.5, 1))
    pts = [(0.0, 7.1 + math.sin(t / 6 * math.pi * 0.5) * 0.2, -t / 6 * 1.2) for t in range(7)]
    sweep(a, pts, [(math.cos(k * math.pi / 3) * 0.035, math.sin(k * math.pi / 3) * 0.035) for k in range(6)],
          "metal_painted", GALV, closed_profile=True)
    secs = [(-1.1, 0.1, 0.08, 0.0), (-1.2, 0.26, 0.13, 0.0), (-1.45, 0.3, 0.14, 0.0), (-1.62, 0.18, 0.08, 0.0)]
    loft(a, [_section(0, 7.32 + dy, z, w, h, n=8) for (z, w, h, dy) in secs], "metal_painted", GALV)
    box(a, (0, 7.235, -1.38), (0.2, 0.012, 0.36), "light_emissive", bottom=True, top=False)
    return a, a


def _cobra(mb, y, z0, col):
    secs = [(z0, 0.12, 0.1, 0.0), (z0 - 0.14, 0.36, 0.16, 0.0), (z0 - 0.5, 0.46, 0.17, 0.0), (z0 - 0.86, 0.38, 0.13, 0.0),
            (z0 - 1.0, 0.18, 0.07, 0.0)]
    loft(mb, [_section(0, y + dy, z, w, h, n=10) for (z, w, h, dy) in secs], "metal_painted", col)
    box(mb, (0, y - 0.085, z0 - 0.55), (0.34, 0.012, 0.66), "light_emissive", bottom=True, top=False)


def lamp_highway(rng=None):
    """Tall galvanised mast with a long outreach arm and a cobra-head luminaire."""
    a = MeshBuilder()
    cylinder(a, (0, 0, 0), 0.28, 0.35, "concrete", segs=10, r_top=0.26)
    cylinder(a, (0, 0.35, 0), 0.19, 10.0, "metal_painted", segs=10, r_top=0.1, col=GALV)
    pts = [(0.0, 10.2 + math.sin(t / 8 * math.pi * 0.6) * 0.5, -t / 8 * 2.8) for t in range(9)]
    sweep(a, pts, [(math.cos(k * math.pi / 4) * 0.055, math.sin(k * math.pi / 4) * 0.055) for k in range(8)],
          "metal_painted", GALV, closed_profile=True)
    _cobra(a, 10.6, -2.75, GALV)
    return a, a


def lamp_highway_double(rng=None):
    """Median mast with two outreach arms."""
    a = MeshBuilder()
    cylinder(a, (0, 0.8, 0), 0.2, 9.4, "metal_painted", segs=10, r_top=0.11, col=GALV)
    for sx in (-1, 1):
        pts = [(sx * t / 8 * 2.9, 10.15 + math.sin(t / 8 * math.pi * 0.6) * 0.45, 0.0) for t in range(9)]
        sweep(a, pts, [(math.cos(k * math.pi / 4) * 0.055, math.sin(k * math.pi / 4) * 0.055) for k in range(8)],
              "metal_painted", GALV, closed_profile=True)
        head = MeshBuilder()
        _cobra(head, 10.5, -2.85, GALV)
        # rotate the -Z facing head onto the +/-X arm
        c, s_ = (0.0, 1.0) if sx < 0 else (0.0, -1.0)
        a.merge(head.transformed(lambda p, c=c, s_=s_: (p[0] * c + p[2] * s_, p[1], -p[0] * s_ + p[2] * c)))
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
