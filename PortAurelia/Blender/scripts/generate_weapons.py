"""Original high-detail weapon models: P9 pistol, .44 revolver, MP-Vector SMG, AR-7 assault
rifle, pump shotgun, LR-9 bolt-action sniper rifle with scope, baseball bat and tactical knife.

Built from side profiles with chamfered edges, lathe parts and swept tubes (see _shapes.py).
Vertex colour R marks edges for the wear shader, G is baked ambient occlusion.

Convention: the grip (hand) is centred at the origin, the barrel points along Godot -Z.
Muzzle positions are written to weapons_meta.json.
Output: Game/assets/generated/weapons/weapons.glb
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from _common import GEN, MeshBuilder, reset_scene, export_glb  # noqa: E402
from _shapes import (BASE, EDGE, ao, extrude, slab, bbox, lathe, disc, sweep_shape, tube, rail, mlok_slots,  # noqa: E402
                     rrect, circle, smooth_path, smooth_loop, screw)

OUT = os.path.join(GEN, "weapons")
M, MD, ST = "gun_metal", "gun_metal_dark", "gun_steel"
P, PG, PT = "gun_polymer", "gun_polymer_grip", "gun_polymer_tan"
W, R, L, BR = "gun_wood", "gun_rubber", "gun_lens", "gun_brass"
SG, SR, BL, BW = "gun_sight", "gun_sight_red", "blade_steel", "bat_wood"
DARK = ao(0.35)


def finish(mb, grip_center, muzzle):
    """Move the model so the grip centre is at the origin."""
    gz, gy = grip_center
    mb.verts = [(v[0], v[1] - gy, v[2] - gz) for v in mb.verts]
    return mb, (muzzle[0], muzzle[1] - gy, muzzle[2] - gz)


def serrations(mb, z0, z1, n, y0, y1, x, mat=MD, slant=0.0, w=0.0014):
    for i in range(n):
        z = z0 + (z1 - z0) * (i + 0.5) / n
        for sx in (-1, 1):
            prof = [(z - w / 2 + slant, y1), (z - w / 2, y0), (z + w / 2, y0), (z + w / 2 + slant, y1)]
            extrude(mb, prof, "x", sx * x - 0.0003, sx * x + 0.0003, mat, 0.0, DARK)


def trigger_guard(mb, pts_zy, w=0.0075, t=0.0042, mat=P):
    path = [(0.0, y, z) for (z, y) in smooth_path(pts_zy, 2)]
    sweep_shape(mb, path, rrect(w, t, t * 0.45, 2), mat, BASE, side_ref=(1, 0, 0))


def grip_profile(top_front, top_rear, bot_front, bot_rear, grooves=3, depth=0.0025, beavertail=None):
    """Angled pistol grip side profile with finger grooves on the front strap."""
    front = []
    for i in range(grooves * 2 + 1):
        t = i / (grooves * 2)
        z = top_front[0] + (bot_front[0] - top_front[0]) * t
        y = top_front[1] + (bot_front[1] - top_front[1]) * t
        if 0 < i < grooves * 2 and i % 2 == 1:
            z += depth
        front.append((z, y))
    pts = [top_front] + front[1:] + [bot_rear]
    rear = [top_rear]
    if beavertail:
        rear = [beavertail, top_rear]
    pts = pts + [(bot_rear[0], bot_rear[1])] + list(reversed(rear))
    # remove duplicates
    clean = []
    for p in pts:
        if not clean or abs(clean[-1][0] - p[0]) + abs(clean[-1][1] - p[1]) > 1e-5:
            clean.append(p)
    return smooth_loop(clean, 1)


# ====================================================================== pistol
def pistol():
    a = MeshBuilder()
    by = 0.078                                       # bore axis
    # slide
    slide = [(-0.176, 0.064), (0.028, 0.064), (0.028, 0.090), (0.022, 0.095), (-0.166, 0.095), (-0.176, 0.086)]
    slab(a, slide, 0.0135, M, 0.0032)
    serrations(a, -0.004, 0.022, 9, 0.068, 0.090, 0.0136, slant=-0.002)
    serrations(a, -0.160, -0.140, 6, 0.070, 0.090, 0.0136, slant=-0.002)
    # ejection port with barrel hood
    extrude(a, rrect(0.042, 0.016, 0.002, 2), "x", 0.0133, 0.0139, MD, 0.0, DARK, offset=(-0.04, 0.086))
    extrude(a, rrect(0.036, 0.011, 0.001, 2), "x", 0.0120, 0.0141, ST, 0.0, BASE, offset=(-0.041, 0.0855))
    # barrel crown + guide rod
    lathe(a, [(-0.179, 0.0), (-0.179, 0.0045), (-0.178, 0.0068), (-0.170, 0.0068)], ST, 20, (0, by))
    disc(a, (0, by, -0.1792), 0.0044, MD, "z", -1.0, 16, DARK)
    lathe(a, [(-0.1775, 0.0), (-0.1775, 0.0038), (-0.170, 0.0038)], M, 12, (0, 0.069))
    # sights (tritium dots)
    slab(a, [(0.010, 0.095), (0.024, 0.095), (0.024, 0.1015), (0.012, 0.1015)], 0.011, M, 0.001)
    for sx in (-1, 1):
        slab(a, [(0.012, 0.1012), (0.022, 0.1012), (0.022, 0.1065), (0.014, 0.1065)], 0.0034, M, 0.0008, x0=sx * 0.0058)
        disc(a, (sx * 0.0058, 0.1040, 0.0222), 0.0011, SG, "z", 1.0, 10)
    slab(a, [(-0.168, 0.095), (-0.160, 0.095), (-0.160, 0.1015), (-0.166, 0.1015)], 0.0021, M, 0.0006)
    disc(a, (0, 0.0995, -0.1598), 0.0011, SG, "z", 1.0, 10)
    # frame / dust cover with accessory rail
    frame = [(-0.172, 0.0525), (-0.050, 0.0525), (-0.036, 0.047), (0.034, 0.047), (0.040, 0.056), (0.040, 0.064),
             (-0.172, 0.064)]
    slab(a, frame, 0.0128, P, 0.002)
    for z in (-0.152, -0.138, -0.124):
        extrude(a, [(z - 0.0025, 0.051), (z + 0.0025, 0.051), (z + 0.0025, 0.0527), (z - 0.0025, 0.0527)], "x",
                -0.011, 0.011, MD, 0.0, DARK)
    # grip with finger grooves, beavertail and stipple
    grip = grip_profile((-0.012, 0.050), (0.034, 0.050), (0.018, -0.070), (0.063, -0.068), 3, 0.0028,
                        beavertail=(0.045, 0.057))
    slab(a, grip, 0.0152, PG, 0.0042)
    slab(a, [(0.014, -0.068), (0.066, -0.066), (0.067, -0.077), (0.012, -0.078)], 0.0162, P, 0.0022)  # base plate
    # trigger guard, trigger, controls
    trigger_guard(a, [(-0.048, 0.049), (-0.057, 0.040), (-0.057, 0.022), (-0.049, 0.013), (-0.024, 0.012),
                      (-0.006, 0.021)], 0.0082, 0.0046)
    trig = [(-0.035, 0.047), (-0.030, 0.047), (-0.029, 0.036), (-0.031, 0.026), (-0.036, 0.022), (-0.039, 0.029),
            (-0.038, 0.039)]
    slab(a, smooth_loop(trig, 1), 0.0028, M, 0.0008)
    slab(a, [(-0.035, 0.036), (-0.032, 0.036), (-0.033, 0.027), (-0.036, 0.029)], 0.0009, MD, 0.0, col=DARK)
    bbox(a, (-0.0138, 0.059, -0.022), (0.0022, 0.0045, 0.03), M, 0.0008, axis="z")      # slide stop
    bbox(a, (-0.0134, 0.058, -0.063), (0.0018, 0.004, 0.009), M, 0.0006, axis="z")      # takedown lever
    lathe(a, [(-0.0152, 0.0), (-0.0152, 0.0035), (-0.0172, 0.0032), (-0.0172, 0.0)], P, 12, (-0.005, 0.034), "x")
    for (z, y) in ((-0.03, 0.056), (0.006, 0.056)):
        for sx in (-1, 1):
            screw(a, (sx * 0.0128, y, z), 0.0016, ST, "x", sx)
    return finish(a, (0.028, -0.008), (0, by, -0.18))


# ====================================================================== revolver
def revolver():
    a = MeshBuilder()
    by = 0.084
    # frame parts (steel): top strap, recoil shield, front frame, lower frame
    slab(a, [(-0.068, 0.099), (0.026, 0.099), (0.032, 0.104), (0.032, 0.108), (-0.068, 0.106)], 0.0105, ST, 0.0022)
    slab(a, [(0.004, 0.050), (0.030, 0.052), (0.034, 0.100), (0.004, 0.100)], 0.0155, ST, 0.003)
    slab(a, [(-0.072, 0.055), (-0.054, 0.055), (-0.054, 0.104), (-0.072, 0.104)], 0.0125, ST, 0.0025)
    slab(a, [(-0.072, 0.046), (0.030, 0.046), (0.030, 0.058), (-0.072, 0.058)], 0.0125, ST, 0.0025)
    # fluted cylinder with chambers and cartridges
    cyl = []
    for i in range(48):
        ang = math.tau * i / 48
        k = i % 8
        r = 0.0225 if k not in (3, 4, 5) else 0.0225 - (0.0028 if k == 4 else 0.0018)
        cyl.append((math.cos(ang) * r, by - 0.004 + math.sin(ang) * r))
    extrude(a, cyl, "z", -0.052, 0.002, ST, 0.0025, BASE, EDGE)
    for i in range(6):
        ang = math.tau * (i + 0.5) / 6 - math.pi / 2 + math.tau / 16
        cx, cy = math.cos(ang) * 0.0135, by - 0.004 + math.sin(ang) * 0.0135
        disc(a, (cx, cy, -0.0521), 0.0058, MD, "z", -1.0, 14, DARK)
        disc(a, (cx, cy, -0.0523), 0.0040, BR, "z", -1.0, 12)
    lathe(a, [(-0.056, 0.0), (-0.056, 0.004), (-0.052, 0.004)], ST, 12, (0, by - 0.004))
    # barrel with underlug and ventilated rib
    lathe(a, [(-0.215, 0.0), (-0.215, 0.0052), (-0.2142, 0.0098), (-0.056, 0.0098)], ST, 28, (0, by))
    disc(a, (0, by, -0.2153), 0.0052, MD, "z", -1.0, 16, DARK)
    extrude(a, rrect(0.017, 0.022, 0.006, 3), "z", -0.214, -0.058, ST, 0.002, BASE, EDGE, offset=(0, by - 0.013))
    extrude(a, rrect(0.0085, 0.006, 0.0015, 2), "z", -0.212, -0.06, ST, 0.001, BASE, EDGE, offset=(0, by + 0.0115))
    for i in range(7):
        z = -0.2 + i * 0.02
        extrude(a, [(z, by + 0.0092), (z + 0.012, by + 0.0092), (z + 0.012, by + 0.0102), (z, by + 0.0102)], "x",
                -0.0035, 0.0035, MD, 0.0, DARK)
    slab(a, [(-0.21, by + 0.0145), (-0.198, by + 0.0145), (-0.202, by + 0.024), (-0.207, by + 0.024)], 0.0022, ST, 0.0006)
    extrude(a, [(-0.2045, by + 0.017), (-0.2035, by + 0.017), (-0.2038, by + 0.022), (-0.2042, by + 0.022)], "x",
            -0.0023, 0.0023, SR, 0.0)
    # rear sight, hammer, trigger, guard
    slab(a, [(0.016, 0.106), (0.030, 0.106), (0.030, 0.1115), (0.016, 0.1115)], 0.0065, MD, 0.0008)
    hammer = [(0.028, 0.090), (0.036, 0.100), (0.043, 0.113), (0.053, 0.116), (0.055, 0.111), (0.044, 0.104),
              (0.038, 0.088)]
    slab(a, smooth_loop(hammer, 1), 0.0045, ST, 0.001)
    for i in range(5):
        z = 0.044 + i * 0.002
        extrude(a, [(z, 0.1115 + i * 0.0006), (z + 0.001, 0.1115 + i * 0.0006), (z + 0.001, 0.1145 + i * 0.0005),
                    (z, 0.1145 + i * 0.0005)], "x", -0.0048, 0.0048, MD, 0.0, DARK)
    trig = [(-0.012, 0.046), (-0.006, 0.046), (-0.004, 0.032), (-0.008, 0.020), (-0.014, 0.018), (-0.013, 0.030)]
    slab(a, smooth_loop(trig, 1), 0.0035, ST, 0.001)
    trigger_guard(a, [(-0.026, 0.047), (-0.032, 0.036), (-0.028, 0.017), (-0.012, 0.010), (0.006, 0.016),
                      (0.012, 0.030)], 0.0072, 0.0045, ST)
    # grip: steel core + walnut panels with finger grooves + medallion
    core = grip_profile((0.012, 0.050), (0.036, 0.056), (0.030, -0.066), (0.071, -0.060), 3, 0.003,
                        beavertail=(0.040, 0.066))
    slab(a, core, 0.0085, ST, 0.0015)
    panel = grip_profile((0.0145, 0.047), (0.034, 0.052), (0.031, -0.062), (0.068, -0.057), 3, 0.003)
    for sx in (-1, 1):
        extrude(a, panel, "x", min(sx * 0.0072, sx * 0.0178), max(sx * 0.0072, sx * 0.0178), W, 0.0045)
        lathe(a, [(sx * 0.0170, 0.0047), (sx * 0.0181, 0.0046), (sx * 0.0186, 0.0032), (sx * 0.0189, 0.0)], BR, 16,
              (0.047, -0.004), "x", EDGE, cap0=False, cap1=False)
    for (z, y) in ((-0.03, 0.052), (0.018, 0.053), (-0.06, 0.09)):
        for sx in (-1, 1):
            screw(a, (sx * 0.0125, y, z), 0.0017, ST, "x", sx)
    bbox(a, (-0.016, 0.07, 0.012), (0.004, 0.005, 0.01), ST, 0.001)                      # cylinder latch
    return finish(a, (0.042, -0.006), (0, by, -0.216))


# ====================================================================== SMG
def smg():
    a = MeshBuilder()
    by = 0.074
    upper = [(-0.215, 0.058), (0.080, 0.058), (0.080, 0.097), (0.072, 0.101), (-0.205, 0.101), (-0.215, 0.093)]
    slab(a, upper, 0.0165, M, 0.0035)
    rail(a, -0.205, 0.072, 0.101, M, 0.021)
    # side cocking handle slot + knob (left)
    extrude(a, rrect(0.12, 0.006, 0.003, 2), "x", -0.0168, -0.0162, MD, 0.0, DARK, offset=(-0.08, 0.082))
    lathe(a, [(-0.0168, 0.0042), (-0.029, 0.0042), (-0.031, 0.0032), (-0.031, 0.0)], M, 14, (-0.03, 0.082), "x",
          cap0=False)
    # ejection port right
    extrude(a, rrect(0.05, 0.016, 0.002, 2), "x", 0.0163, 0.0169, MD, 0.0, DARK, offset=(-0.03, 0.084))
    # lower + pistol grip + magwell
    lower = [(-0.130, 0.058), (0.040, 0.058), (0.046, 0.046), (0.010, 0.040), (-0.070, 0.040), (-0.082, 0.028),
             (-0.082, -0.004), (-0.118, -0.004), (-0.124, 0.03)]
    slab(a, lower, 0.0152, P, 0.0028)
    grip = grip_profile((-0.008, 0.042), (0.026, 0.044), (0.012, -0.068), (0.048, -0.064), 3, 0.0024)
    slab(a, grip, 0.0158, PG, 0.0042)
    # straight magazine with ribs
    mag = [(-0.114, 0.0), (-0.086, 0.0), (-0.083, -0.150), (-0.113, -0.152)]
    slab(a, mag, 0.0112, M, 0.0018)
    for y in (-0.03, -0.06, -0.09, -0.12):
        for sx in (-1, 1):
            extrude(a, [(-0.110, y), (-0.087, y - 0.0004), (-0.087, y + 0.0035), (-0.110, y + 0.0035)], "x",
                    sx * 0.0112 - 0.0005, sx * 0.0112 + 0.0005, MD, 0.0, DARK)
    slab(a, [(-0.117, -0.150), (-0.080, -0.148), (-0.080, -0.160), (-0.117, -0.162)], 0.0128, P, 0.002)
    trigger_guard(a, [(-0.066, 0.041), (-0.068, 0.020), (-0.050, 0.012), (-0.020, 0.012), (-0.004, 0.022)],
                  0.009, 0.0048, P)
    slab(a, smooth_loop([(-0.044, 0.04), (-0.039, 0.04), (-0.038, 0.029), (-0.043, 0.022), (-0.047, 0.03)], 1),
         0.0028, M, 0.0008)
    lathe(a, [(0.0152, 0.0), (0.0152, 0.004), (0.0178, 0.004), (0.0178, 0.0)], M, 10, (0.012, 0.052), "x")  # selector
    # handguard with M-LOK slots + vertical foregrip
    extrude(a, rrect(0.034, 0.042, 0.009, 3), "z", -0.305, -0.205, P, 0.003, BASE, EDGE, offset=(0, 0.077))
    for sx in (-1, 1):
        mlok_slots(a, -0.298, -0.212, 0.078, sx * 0.0171, "x", MD, 3, 0.022, 0.0065, 0.008)
    rail(a, -0.3, -0.21, 0.098, M, 0.021)
    lathe(a, [(0.056, 0.0), (0.056, 0.012), (0.050, 0.0135), (-0.02, 0.0135), (-0.035, 0.0145), (-0.04, 0.0)], PG, 20,
          (0.0, -0.255), "y")
    # barrel + flash hider
    lathe(a, [(-0.305, 0.0086), (-0.318, 0.0086), (-0.318, 0.0112), (-0.352, 0.0112), (-0.353, 0.0102),
              (-0.353, 0.0)], M, 24, (0, by), cap0=False)
    disc(a, (0, by, -0.3532), 0.0055, MD, "z", -1.0, 14, DARK)
    for k in range(3):
        ang = math.tau * k / 3 + 0.5
        cx, cy = math.cos(ang) * 0.0112, by + math.sin(ang) * 0.0112
        bbox(a, (cx, cy, -0.340), (0.003 if abs(math.cos(ang)) < 0.5 else 0.0012,
                                   0.003 if abs(math.cos(ang)) >= 0.5 else 0.0012, 0.022), MD, 0.0, DARK)
    # sights on rail
    slab(a, [(0.050, 0.108), (0.064, 0.108), (0.064, 0.128), (0.056, 0.128)], 0.004, M, 0.001)
    lathe(a, [(0.059, 0.0045), (0.061, 0.0045)], M, 16, (0, 0.121), "z", cap0=False, cap1=False)
    for sx in (-1, 1):
        slab(a, [(-0.19, 0.108), (-0.178, 0.108), (-0.18, 0.128), (-0.188, 0.128)], 0.0018, M, 0.0006, x0=sx * 0.008)
    slab(a, [(-0.186, 0.108), (-0.182, 0.108), (-0.1825, 0.124), (-0.1855, 0.124)], 0.0012, M, 0.0004)
    # telescoping stock: two rods + butt plate with rubber pad and cheek rest
    for sx in (-1, 1):
        lathe(a, [(0.08, 0.0048), (0.27, 0.0048)], ST, 12, (sx * 0.011, 0.066), cap0=False)
    stock = [(0.255, 0.030), (0.285, 0.024), (0.290, 0.108), (0.262, 0.108), (0.255, 0.094)]
    slab(a, stock, 0.018, P, 0.004)
    slab(a, [(0.285, 0.022), (0.296, 0.022), (0.296, 0.11), (0.285, 0.11)], 0.0195, R, 0.003)
    slab(a, [(0.20, 0.100), (0.262, 0.100), (0.262, 0.112), (0.205, 0.108)], 0.012, P, 0.003)
    for sx in (-1, 1):
        screw(a, (sx * 0.0165, 0.078, 0.03), 0.0018, ST, "x", sx)
    return finish(a, (0.020, -0.013), (0, by, -0.354))


# ====================================================================== assault rifle
def rifle():
    a = MeshBuilder()
    by = 0.066
    # upper receiver
    upper = [(-0.160, 0.046), (0.072, 0.046), (0.072, 0.084), (-0.160, 0.084)]
    slab(a, upper, 0.0155, M, 0.0032)
    rail(a, -0.158, 0.068, 0.084, M, 0.021)
    # ejection port cover (right) with ribs, forward assist, brass deflector
    extrude(a, rrect(0.058, 0.018, 0.002, 2), "x", 0.0152, 0.0162, M, 0.0005, BASE, EDGE, offset=(-0.02, 0.066))
    for i in range(5):
        z = -0.042 + i * 0.011
        extrude(a, [(z, 0.059), (z + 0.004, 0.059), (z + 0.004, 0.073), (z, 0.073)], "x", 0.0161, 0.0166, MD, 0.0, DARK)
    lathe(a, [(0.030, 0.0), (0.030, 0.0068), (0.056, 0.0068), (0.058, 0.0078), (0.064, 0.0078), (0.064, 0.0)],
          M, 16, (0.019, 0.074), "z")
    slab(a, [(0.018, 0.070), (0.030, 0.070), (0.030, 0.084), (0.022, 0.084)], 0.004, M, 0.001, x0=0.0175)
    # charging handle
    slab(a, [(0.068, 0.078), (0.092, 0.078), (0.094, 0.084), (0.068, 0.084)], 0.0085, M, 0.0012)
    slab(a, [(0.084, 0.078), (0.096, 0.078), (0.096, 0.085), (0.086, 0.085)], 0.0185, M, 0.0015)
    # lower receiver: magwell, trigger area, buffer boss
    lower = [(-0.138, 0.046), (0.062, 0.046), (0.068, 0.056), (0.078, 0.056), (0.078, 0.034), (0.050, 0.030),
             (0.010, 0.028), (-0.058, 0.028), (-0.066, 0.020), (-0.070, -0.012), (-0.128, -0.012), (-0.136, 0.018)]
    slab(a, lower, 0.0148, M, 0.0028)
    extrude(a, rrect(0.034, 0.066, 0.004, 2), "y", -0.0135, -0.0105, M, 0.001, BASE, EDGE, offset=(0, -0.099))
    # takedown pins, bolt catch, mag release, selector
    for (z, y) in ((-0.125, 0.040), (0.058, 0.040)):
        for sx in (-1, 1):
            lathe(a, [(sx * 0.0148, 0.003), (sx * 0.0162, 0.003), (sx * 0.0164, 0.0)], ST, 12, (z, y), "x", cap0=False)
    bbox(a, (-0.0158, 0.045, -0.05), (0.003, 0.012, 0.018), M, 0.0008)
    lathe(a, [(0.0148, 0.0), (0.0148, 0.0048), (0.0175, 0.0048), (0.0178, 0.0)], M, 14, (-0.055, 0.033), "x")
    slab(a, [(0.018, 0.036), (0.030, 0.036), (0.034, 0.042), (0.018, 0.042)], 0.0013, M, 0.0004, x0=-0.0158)
    # pistol grip (angled, finger ridge, stipple)
    grip = grip_profile((-0.012, 0.030), (0.020, 0.032), (0.012, -0.074), (0.050, -0.068), 1, 0.004)
    slab(a, grip, 0.0158, PG, 0.0045)
    trigger_guard(a, [(-0.056, 0.029), (-0.057, 0.017), (-0.046, 0.011), (-0.018, 0.011), (-0.006, 0.018)],
                  0.017, 0.0045, M)
    slab(a, smooth_loop([(-0.034, 0.029), (-0.029, 0.029), (-0.028, 0.019), (-0.033, 0.012), (-0.037, 0.02)], 1),
         0.0028, M, 0.0008)
    # curved 30-round magazine with ribs and base plate
    front, rear = [], []
    for i in range(9):
        t = i / 8
        ang = math.radians(8 + 16 * t)
        front.append((-0.130 + math.sin(ang) * 0.0 - 0.09 * t * t * 0.45, 0.0 - 0.2 * t))
        rear.append((-0.068 - 0.09 * t * t * 0.45 + 0.004 * t, 0.0 - 0.2 * t + 0.004 * t))
    mag = front + list(reversed(rear))
    slab(a, smooth_loop(mag, 1), 0.0112, M, 0.0018)
    for k in range(1, 7):
        t = k / 7.5
        fz, fy = -0.130 - 0.09 * t * t * 0.45, -0.2 * t
        rz = -0.068 - 0.09 * t * t * 0.45 + 0.004 * t
        for sx in (-1, 1):
            extrude(a, [(fz + 0.006, fy), (rz - 0.006, fy + 0.004 * t), (rz - 0.006, fy + 0.0045 + 0.004 * t),
                        (fz + 0.006, fy + 0.0045)], "x", sx * 0.0112 - 0.0006, sx * 0.0112 + 0.0006, MD, 0.0, DARK)
    t = 1.0
    bz0, bz1 = -0.130 - 0.09 * 0.45 - 0.004, -0.068 - 0.09 * 0.45 + 0.008
    slab(a, [(bz0, -0.198), (bz1, -0.194), (bz1, -0.207), (bz0, -0.211)], 0.0128, P, 0.002)
    # free-float M-LOK handguard, top rail, end cap
    hg = rrect(0.044, 0.048, 0.014, 4)
    extrude(a, hg, "z", -0.470, -0.158, P, 0.004, BASE, EDGE, offset=(0, by + 0.001))
    for sx in (-1, 1):
        mlok_slots(a, -0.455, -0.175, by + 0.001, sx * 0.0222, "x", MD, 7, 0.032, 0.0075, 0.009)
    mlok_slots(a, -0.455, -0.175, by - 0.0232, 0.0, "y", MD, 7, 0.032, 0.0075, 0.009)
    rail(a, -0.468, -0.160, by + 0.025, M, 0.021)
    # barrel, gas block glimpse, muzzle brake with ports
    lathe(a, [(-0.470, 0.0092), (-0.560, 0.0082)], ST, 20, (0, by), cap0=False, cap1=False)
    brake = [(-0.560, 0.0082), (-0.560, 0.0115), (-0.563, 0.0122), (-0.612, 0.0122), (-0.615, 0.0108),
             (-0.615, 0.0)]
    lathe(a, brake, M, 24, (0, by), cap0=False)
    disc(a, (0, by, -0.6153), 0.0055, MD, "z", -1.0, 14, DARK)
    for k in range(3):
        z = -0.572 - k * 0.013
        for sx in (-1, 1):
            extrude(a, rrect(0.008, 0.009, 0.0025, 2), "x", sx * 0.0122 - 0.0006, sx * 0.0122 + 0.0006, MD, 0.0,
                    DARK, offset=(z, by + 0.001))
    # buffer tube + adjustable stock with cheek riser and rubber pad
    lathe(a, [(0.072, 0.0165), (0.080, 0.0165), (0.080, 0.0145), (0.300, 0.0145)], M, 20, (0, 0.063), cap0=False)
    stock = [(0.175, 0.040), (0.310, 0.008), (0.318, 0.008), (0.318, 0.098), (0.240, 0.094), (0.195, 0.090),
             (0.175, 0.080)]
    slab(a, stock, 0.0195, P, 0.0045)
    slab(a, [(0.318, 0.006), (0.330, 0.006), (0.330, 0.100), (0.318, 0.100)], 0.0215, R, 0.003)
    slab(a, [(0.215, 0.094), (0.300, 0.098), (0.300, 0.109), (0.225, 0.104)], 0.0135, P, 0.003)
    slab(a, [(0.200, 0.030), (0.236, 0.022), (0.240, 0.028), (0.205, 0.036)], 0.006, P, 0.0015)       # adj. lever
    for sx in (-1, 1):
        lathe(a, [(sx * 0.0195, 0.0045), (sx * 0.0215, 0.0045), (sx * 0.0215, 0.0)], ST, 12, (0.29, 0.03), "x",
              cap0=False)
    # red dot sight on the rail: base, hood frame, lens, reticle
    rz0, rz1 = -0.05, 0.005
    slab(a, [(rz0, 0.0912), (rz1, 0.0912), (rz1, 0.099), (rz0, 0.099)], 0.014, M, 0.0015)
    for sx in (-1, 1):
        bbox(a, (sx * 0.0155, 0.113, (rz0 + rz1) / 2), (0.003, 0.022, rz1 - rz0), M, 0.001)
    bbox(a, (0, 0.1275, (rz0 + rz1) / 2), (0.028, 0.003, rz1 - rz0), M, 0.001)
    extrude(a, rrect(0.028, 0.024, 0.004, 3), "z", rz0 + 0.0015, rz0 + 0.003, L, 0.0, BASE, offset=(0, 0.113))
    disc(a, (0, 0.113, rz0 + 0.0032), 0.0009, SR, "z", 1.0, 10)
    lathe(a, [(0.0, 0.0), (0.0, 0.006), (-0.012, 0.006), (-0.012, 0.0)], M, 14, (-0.02, 0.113), "x")    # turret
    return finish(a, (0.019, -0.022), (0, by, -0.616))


# ====================================================================== shotgun
def shotgun():
    a = MeshBuilder()
    by = 0.084
    recv = [(-0.125, 0.046), (0.062, 0.046), (0.062, 0.090), (0.052, 0.101), (-0.115, 0.101), (-0.125, 0.094)]
    slab(a, recv, 0.0165, M, 0.0045)
    extrude(a, rrect(0.07, 0.022, 0.003, 2), "x", 0.0163, 0.0170, MD, 0.0, DARK, offset=(-0.03, 0.078))
    extrude(a, rrect(0.07, 0.020, 0.006, 3), "y", 0.0452, 0.0462, MD, 0.0, DARK, offset=(0, -0.03))
    lathe(a, [(0.0, 0.0), (0.0, 0.0038), (0.004, 0.0034), (0.004, 0.0)], M, 12, (0.040, 0.101), "y")      # safety
    # barrel, vent rib, bead
    lathe(a, [(-0.125, 0.0125), (-0.680, 0.0118), (-0.682, 0.0105), (-0.682, 0.0)], M, 28, (0, by), cap0=False)
    disc(a, (0, by, -0.6822), 0.0095, MD, "z", -1.0, 18, DARK)
    bbox(a, (0, by + 0.0165, -0.40), (0.008, 0.002, 0.55), M, 0.0006)
    for i in range(18):
        z = -0.14 - i * 0.03
        bbox(a, (0, by + 0.0135, z), (0.0035, 0.004, 0.006), M, 0.0005)
    lathe(a, [(by + 0.0175, 0.0), (by + 0.0175, 0.0024), (by + 0.021, 0.0018), (by + 0.022, 0.0)], BR, 12,
          (0.0, -0.672), "y")
    # magazine tube, cap, barrel clamp
    lathe(a, [(-0.125, 0.0118), (-0.585, 0.0118)], M, 24, (0, 0.056), cap0=False, cap1=False)
    lathe(a, [(-0.585, 0.013), (-0.610, 0.013), (-0.612, 0.0105), (-0.612, 0.0)], M, 24, (0, 0.056))
    for i in range(10):
        z = -0.588 - i * 0.0022
        bbox(a, (0, 0.056 + 0.0132, z), (0.0035, 0.0006, 0.0009), MD, 0.0, DARK)
    extrude(a, rrect(0.03, 0.058, 0.012, 3), "z", -0.520, -0.505, M, 0.0015, BASE, EDGE, offset=(0, 0.07))
    # pump forend with grooves + action bars
    prof = [(-0.46, 0.0), (-0.46, 0.0175)]
    for i in range(12):
        z = -0.452 + i * 0.013
        prof += [(z, 0.021), (z + 0.009, 0.021), (z + 0.0095, 0.0195), (z + 0.013, 0.0195)]
    prof += [(-0.29, 0.021), (-0.284, 0.0175), (-0.284, 0.0)]
    lathe(a, prof, P, 28, (0, 0.058))
    for sx in (-1, 1):
        bbox(a, (sx * 0.0145, 0.060, -0.205), (0.002, 0.005, 0.16), ST, 0.0005)
    # walnut stock with pistol grip, rubber recoil pad
    stock = smooth_loop([(0.060, 0.099), (0.160, 0.093), (0.400, 0.084), (0.400, -0.034), (0.300, -0.020),
                         (0.160, 0.012), (0.105, 0.018), (0.086, 0.004), (0.072, -0.028), (0.068, -0.052),
                         (0.052, -0.059), (0.036, -0.053), (0.030, -0.030), (0.029, 0.000), (0.033, 0.030),
                         (0.036, 0.046), (0.060, 0.046)], 2)
    slab(a, stock, 0.0205, W, 0.006)
    slab(a, smooth_loop([(0.400, 0.088), (0.422, 0.089), (0.422, -0.034), (0.400, -0.033)], 1), 0.0225, R, 0.004)
    for i in range(6):
        y = -0.02 + i * 0.018
        bbox(a, (0, y, 0.421), (0.036, 0.004, 0.003), MD, 0.0, DARK)
    # trigger + guard
    trigger_guard(a, [(-0.02, 0.047), (-0.024, 0.030), (-0.012, 0.018), (0.012, 0.018), (0.028, 0.030)],
                  0.009, 0.0048, M)
    slab(a, smooth_loop([(0.004, 0.047), (0.009, 0.047), (0.010, 0.036), (0.006, 0.026), (0.001, 0.034)], 1),
         0.003, M, 0.0008)
    for (z, y) in ((-0.1, 0.06), (0.03, 0.058)):
        for sx in (-1, 1):
            screw(a, (sx * 0.0165, y, z), 0.0022, ST, "x", sx)
    return finish(a, (0.045, -0.008), (0, by, -0.683))


# ====================================================================== sniper rifle
def sniper():
    a = MeshBuilder()
    by = 0.078
    # tan chassis: forend, action bed, grip, butt with cheek riser
    chassis = [(-0.560, 0.044), (-0.150, 0.040), (-0.110, 0.030), (0.070, 0.030), (0.080, 0.062), (-0.150, 0.066),
               (-0.560, 0.062)]
    slab(a, chassis, 0.024, PT, 0.005)
    for sx in (-1, 1):
        mlok_slots(a, -0.54, -0.18, 0.052, sx * 0.0242, "x", MD, 9, 0.03, 0.0075, 0.009)
    grip = grip_profile((0.018, 0.030), (0.052, 0.030), (0.032, -0.082), (0.070, -0.078), 3, 0.0026)
    slab(a, grip, 0.0162, PG, 0.0045)
    butt = [(0.070, 0.030), (0.090, 0.030), (0.330, 0.004), (0.345, 0.004), (0.345, 0.086), (0.090, 0.070)]
    slab(a, butt, 0.0205, PT, 0.0045)
    for z in (0.14, 0.20, 0.26):
        extrude(a, rrect(0.034, 0.02, 0.008, 3), "x", -0.0207, 0.0207, MD, 0.0, DARK, offset=(z, 0.046))
    slab(a, [(0.150, 0.086), (0.300, 0.089), (0.300, 0.104), (0.160, 0.100)], 0.0155, PT, 0.004)      # cheek riser
    for sx in (-1, 1):
        lathe(a, [(sx * 0.0156, 0.004), (sx * 0.021, 0.004), (sx * 0.021, 0.0)], ST, 10, (0.22, 0.095), "x", cap0=False)
    slab(a, [(0.345, 0.0), (0.360, 0.0), (0.360, 0.090), (0.345, 0.090)], 0.0225, R, 0.004)
    slab(a, [(0.230, 0.004), (0.262, 0.004), (0.262, -0.012), (0.232, -0.012)], 0.007, M, 0.0015)       # monopod
    # round action, bolt shroud, bolt handle with ball knob
    lathe(a, [(-0.135, 0.0), (-0.135, 0.017), (-0.130, 0.019), (0.068, 0.019), (0.070, 0.0165)], M, 28, (0, by),
          cap1=False)
    lathe(a, [(0.070, 0.0165), (0.098, 0.0145), (0.104, 0.0110), (0.104, 0.0)], M, 24, (0, by))
    extrude(a, rrect(0.06, 0.014, 0.003, 2), "x", 0.0172, 0.0196, MD, 0.0, DARK, offset=(-0.02, by + 0.004))
    bolt_path = [(0.016, by + 0.002, 0.062), (0.034, by - 0.006, 0.066), (0.046, by - 0.014, 0.072)]
    tube(a, bolt_path, 0.0038, ST, 12, side_ref=(0, 0, 1))
    lathe(a, [(0.044, 0.0), (0.0455, 0.006), (0.051, 0.0098), (0.057, 0.0092), (0.062, 0.004), (0.063, 0.0)],
          P, 18, (0.072, by - 0.016), "x")
    # detachable magazine
    slab(a, [(-0.070, 0.031), (-0.004, 0.031), (-0.002, -0.022), (-0.068, -0.024)], 0.0115, M, 0.002)
    trigger_guard(a, [(0.004, 0.031), (0.000, 0.012), (0.014, 0.004), (0.040, 0.006), (0.050, 0.020)], 0.012,
                  0.005, M)
    slab(a, smooth_loop([(0.018, 0.030), (0.023, 0.030), (0.024, 0.019), (0.020, 0.011), (0.015, 0.019)], 1),
         0.0028, M, 0.0008)
    # fluted heavy barrel + large muzzle brake
    flute = []
    for i in range(48):
        ang = math.tau * i / 48
        k = i % 8
        r = 0.0125 if k not in (3, 4, 5) else (0.0108 if k == 4 else 0.0114)
        flute.append((math.cos(ang) * r, by + math.sin(ang) * r))
    extrude(a, flute, "z", -0.740, -0.135, M, 0.0012, BASE, EDGE, cap0=False, cap1=False)
    brake = [(-0.740, 0.0118), (-0.740, 0.0155), (-0.744, 0.0165), (-0.815, 0.0165), (-0.820, 0.0145),
             (-0.820, 0.0)]
    lathe(a, brake, M, 28, (0, by), cap0=False)
    disc(a, (0, by, -0.8203), 0.0062, MD, "z", -1.0, 16, DARK)
    for k in range(4):
        z = -0.755 - k * 0.015
        for sx in (-1, 1):
            extrude(a, rrect(0.009, 0.013, 0.003, 2), "x", sx * 0.0165 - 0.0007, sx * 0.0165 + 0.0007, MD, 0.0,
                    DARK, offset=(z, by + 0.001))
    # scope rail, rings, scope body with turrets, lenses
    rail(a, -0.13, 0.06, by + 0.019, M, 0.021)
    sy = by + 0.056
    for rz in (-0.090, 0.030):
        slab(a, [(rz - 0.010, by + 0.026), (rz + 0.010, by + 0.026), (rz + 0.010, sy), (rz - 0.010, sy)], 0.009, M,
             0.0015)
        lathe(a, [(rz - 0.0095, 0.0185), (rz + 0.0095, 0.0185)], M, 28, (0, sy), cap0=False, cap1=False)
    scope = [(-0.318, 0.0), (-0.318, 0.0262), (-0.310, 0.0262), (-0.302, 0.0272), (-0.262, 0.0272), (-0.220, 0.0155),
             (0.050, 0.0155), (0.066, 0.0165), (0.074, 0.0215), (0.112, 0.0215), (0.116, 0.0205), (0.116, 0.0)]
    lathe(a, scope, M, 36, (0, sy), cap0=False, cap1=False)
    for rz in (-0.300, 0.100):
        for i in range(8):
            z = rz + i * 0.0018
            lathe(a, [(z, 0.0277 if rz < 0 else 0.0220), (z + 0.0009, 0.0277 if rz < 0 else 0.0220)], MD, 36, (0, sy),
                  cap0=False, cap1=False, col=DARK)
    disc(a, (0, sy, -0.3170), 0.0245, L, "z", -1.0, 32)
    disc(a, (0, sy, 0.1150), 0.0185, L, "z", 1.0, 28)
    bbox(a, (0, sy + 0.012, -0.055), (0.034, 0.020, 0.05), M, 0.004)                     # turret housing
    lathe(a, [(sy + 0.022, 0.0125), (sy + 0.040, 0.0125), (sy + 0.042, 0.0115), (sy + 0.042, 0.0)], M, 28,
          (0.0, -0.055), "y", cap0=False)
    for i in range(12):
        ang = math.tau * i / 12
        bbox(a, (math.cos(ang) * 0.0127, sy + 0.036, -0.055 + math.sin(ang) * 0.0127), (0.0014, 0.007, 0.0014), MD,
             0.0, DARK)
    lathe(a, [(0.017, 0.011), (0.032, 0.011), (0.033, 0.010), (0.033, 0.0)], M, 24, (-0.055, sy), "x", cap0=False)
    lathe(a, [(-0.017, 0.0), (-0.033, 0.0), (-0.033, 0.013), (-0.017, 0.013)], M, 24, (-0.055, sy), "x", cap0=False)
    # folded bipod
    for sx in (-1, 1):
        bbox(a, (sx * 0.012, 0.036, -0.40), (0.006, 0.006, 0.20), M, 0.0015)
        bbox(a, (sx * 0.012, 0.036, -0.30), (0.012, 0.008, 0.014), R, 0.002)
    bbox(a, (0, 0.036, -0.505), (0.034, 0.012, 0.022), M, 0.002)
    return finish(a, (0.046, -0.026), (0, by, -0.821))


# ====================================================================== melee
def bat():
    a = MeshBuilder()
    prof = [(0.090, 0.0), (0.090, 0.018), (0.086, 0.0245), (0.080, 0.0245), (0.076, 0.0165), (0.06, 0.0142),
            (-0.10, 0.0145), (-0.25, 0.0175), (-0.40, 0.0255), (-0.55, 0.0325), (-0.72, 0.0345), (-0.77, 0.033),
            (-0.785, 0.028), (-0.79, 0.018), (-0.79, 0.0)]
    lathe(a, prof, BW, 32, (0, 0))
    lathe(a, [(0.075, 0.0158), (0.060, 0.0155), (-0.16, 0.0158), (-0.17, 0.0152)], R, 32, (0, 0), cap0=False,
          cap1=False)
    for i in range(16):
        z = 0.06 - i * 0.0138
        lathe(a, [(z, 0.0159), (z - 0.0015, 0.0161)], MD, 32, (0, 0), cap0=False, cap1=False, col=DARK)
    return a, (0, 0, -0.79)


def knife():
    a = MeshBuilder()
    # blade: drop point, flat grind (spine thickness -> sharp edge), fuller
    n = 16
    spine, edge = [], []
    for i in range(n + 1):
        t = i / n
        z = -0.046 - t * 0.156
        sy = 0.0125 - 0.0 * t
        if t > 0.62:
            k = (t - 0.62) / 0.38
            sy = 0.0125 - k * k * 0.0135
        ey = -0.0125 + (t ** 2.2) * 0.0105
        spine.append((z, sy))
        edge.append((z, ey))
    th = 0.0024
    for sx in (-1, 1):
        for i in range(n):
            (z0, s0), (z1, s1) = spine[i], spine[i + 1]
            (_, e0), (_, e1) = edge[i], edge[i + 1]
            m0, m1 = e0 + (s0 - e0) * 0.42, e1 + (s1 - e1) * 0.42
            a.face([(sx * th, s0, z0), (sx * th, s1, z1), (sx * th * 0.9, m1, z1), (sx * th * 0.9, m0, z0)], None, BL,
                   BASE, up=(sx, 0, 0))
            a.face([(sx * th * 0.9, m0, z0), (sx * th * 0.9, m1, z1), (0, e1, z1), (0, e0, z0)], None, BL, EDGE,
                   up=(sx, -0.3, 0))
        extrude(a, rrect(0.07, 0.0028, 0.0014, 2), "x", sx * th - 0.0003, sx * th + 0.0002, MD, 0.0, DARK,
                offset=(-0.095, 0.004))
    for i in range(n):
        (z0, s0), (z1, s1) = spine[i], spine[i + 1]
        a.face([(-th, s0, z0), (th, s0, z0), (th, s1, z1), (-th, s1, z1)], None, BL, EDGE, up=(0, 1, 0))
    a.face([(-th, spine[0][1], spine[0][0]), (th, spine[0][1], spine[0][0]), (0, edge[0][1], edge[0][0])], None, BL,
           BASE, up=(0, 0, 1))
    # guard, rubber handle with grooves, pommel
    slab(a, [(-0.050, -0.022), (-0.042, -0.022), (-0.042, 0.020), (-0.050, 0.020)], 0.009, M, 0.0015)
    handle = smooth_loop([(-0.042, 0.014), (0.060, 0.013), (0.066, 0.009), (0.066, -0.011), (0.040, -0.015),
                          (0.020, -0.011), (0.000, -0.016), (-0.022, -0.012), (-0.042, -0.016)], 1)
    slab(a, handle, 0.0115, R, 0.004)
    for i in range(6):
        z = -0.025 + i * 0.013
        extrude(a, [(z, -0.018), (z + 0.004, -0.018), (z + 0.004, 0.017), (z, 0.017)], "x", -0.0118, 0.0118, MD, 0.0,
                DARK)
    slab(a, [(0.064, -0.013), (0.076, -0.012), (0.078, 0.0), (0.076, 0.012), (0.064, 0.014)], 0.0105, M, 0.002)
    return finish(a, (0.012, 0.0), (0, 0, -0.203))


WEAPONS = {"pistol": pistol, "revolver": revolver, "smg": smg, "rifle": rifle, "shotgun": shotgun,
           "sniper": sniper, "bat": bat, "knife": knife}


def main():
    reset_scene()
    objs = []
    meta = {}
    for name, fn in WEAPONS.items():
        mb, muzzle = fn()
        objs.append(mb.to_object(name, merge_dist=0.00005, auto_smooth=32))
        meta[name] = {"muzzle": [round(v, 4) for v in muzzle], "tris": mb.tri_count()}
        print(f"  {name}: {mb.tri_count()} tris")
    os.makedirs(OUT, exist_ok=True)
    export_glb(os.path.join(OUT, "weapons.glb"), objs)
    with open(os.path.join(OUT, "weapons_meta.json"), "w") as f:
        json.dump(meta, f, indent=1)
    print("[weapons]", len(objs))


if __name__ == "__main__":
    main()
