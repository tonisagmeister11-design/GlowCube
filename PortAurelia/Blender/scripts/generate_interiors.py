"""Procedural enterable interiors (safehouse apartment, store, gun shop with range, bank).

Each interior is exported as Game/assets/generated/interiors/<name>.glb with render
geometry (`Interior`), emissive light panels, props and per-surface collision
(`Col_<surface>-colonly`). Game/assets/generated/interiors/interiors.json holds the
gameplay points (spawn, exit door, counters, bed, vault, shooting lanes, lights).

Local space: origin on the floor just inside the entrance door, the room extends
towards -Z (the player walks in along -Z). Godot space (X right, Y up, Z back).

Usage:  python Blender/scripts/generate_interiors.py
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from _common import GEN, MeshBuilder, reset_scene, clear_objects, box, cylinder  # noqa: E402
from export_glb import export_glb  # noqa: E402
from generate_colliders import new_collision_builders, collision_objects  # noqa: E402

OUT = os.path.join(GEN, "interiors")
WALL_T = 0.2


class Room:
    def __init__(self, name, w, d, h, floor_mat="interior_floor", floor_col=(1, 1, 1, 1),
                 wall_col=(1, 1, 1, 1), ceil_col=(0.95, 0.95, 0.95, 1)):
        self.name = name
        self.w, self.d, self.h = w, d, h
        self.mb = MeshBuilder()
        self.cols = new_collision_builders()
        self.points = {}
        self.lights = []
        self.floor_mat = floor_mat
        self.floor_col = floor_col
        self.wall_col = wall_col
        self.ceil_col = ceil_col
        self._shell()

    # ---------------------------------------------------------------- shell
    def _shell(self):
        w, d, h = self.w, self.d, self.h
        # floor & ceiling (room spans x -w/2..w/2, z 0..-d)
        box(self.mb, (0, -0.1, -d / 2), (w + 0.4, 0.2, d + 0.4), self.floor_mat, col=self.floor_col, top=True)
        box(self.mb, (0, h + 0.1, -d / 2), (w + 0.4, 0.2, d + 0.4), "interior_wall", col=self.ceil_col,
            top=False, bottom=True)
        self.collide("wood" if self.floor_mat in ("wood_planks", "wood") else "concrete",
                      (0, -0.1, -d / 2), (w + 0.4, 0.2, d + 0.4))
        self.collide("building", (0, h + 0.1, -d / 2), (w + 0.4, 0.2, d + 0.4))
        # side and back walls
        self.wall((-w / 2 - WALL_T / 2, h / 2, -d / 2), (WALL_T, h, d))
        self.wall((w / 2 + WALL_T / 2, h / 2, -d / 2), (WALL_T, h, d))
        self.wall((0, h / 2, -d - WALL_T / 2), (w, h, WALL_T))
        # front wall with a door gap (1.4 m wide, 2.3 m high)
        gw = 1.4
        side = (w - gw) / 2
        self.wall((-w / 2 + side / 2, h / 2, WALL_T / 2), (side, h, WALL_T))
        self.wall((w / 2 - side / 2, h / 2, WALL_T / 2), (side, h, WALL_T))
        self.wall((0, 2.3 + (h - 2.3) / 2, WALL_T / 2), (gw, h - 2.3, WALL_T))
        # closed door leaf in the gap (the exit), baseboards
        box(self.mb, (0, 1.15, WALL_T * 0.8), (gw, 2.3, 0.06), "door", col=(0.55, 0.4, 0.3, 1))
        self.collide("building", (0, 1.15, WALL_T * 0.8), (gw, 2.3, 0.1))
        for x in (-w / 2 + 0.03, w / 2 - 0.03):
            box(self.mb, (x, 0.06, -d / 2), (0.04, 0.12, d), "trim")
        box(self.mb, (0, 0.06, -d + 0.03), (w, 0.12, 0.04), "trim")
        self.points["spawn"] = [0.0, 0.05, -1.2, 0.0]
        self.points["exit"] = [0.0, 0.05, -0.6]

    def wall(self, c, s):
        box(self.mb, c, s, "interior_wall", col=self.wall_col, top=False)
        self.collide("building", c, s)

    def collide(self, surf, c, s):
        box(self.cols[surf], c, s, "concrete", top=True, bottom=True)

    def solid(self, c, s, mat, col=(1, 1, 1, 1), surf="building"):
        box(self.mb, c, s, mat, col=col, top=True)
        self.collide(surf, c, s)

    def ceiling_light(self, x, z, energy=1.6, rng=9.0, color=(1.0, 0.95, 0.85)):
        box(self.mb, (x, self.h - 0.03, z), (1.2, 0.05, 0.4), "light_always", col=(1, 0.97, 0.9, 1), top=False, bottom=True)
        self.lights.append([x, self.h - 0.3, z, rng, energy, list(color)])

    def export(self):
        objs = [self.mb.to_object("Interior")]
        objs.extend(collision_objects(self.cols))
        export_glb(os.path.join(OUT, self.name + ".glb"), objs)
        clear_objects()
        return {"size": [self.w, self.h, self.d], "points": self.points, "lights": self.lights}


# ====================================================================== furniture helpers
def sofa(r, x, z, rot=0.0, col=(0.3, 0.35, 0.45, 1)):
    c, s = math.cos(rot), math.sin(rot)

    def p(lx, lz):
        return (x + lx * c + lz * s, z - lx * s + lz * c)
    b = p(0, 0)
    box(r.mb, (b[0], 0.22, b[1]), (2.2, 0.44, 0.9), "fabric", col=col, rot_y=rot)
    bb = p(0, 0.38)
    box(r.mb, (bb[0], 0.65, bb[1]), (2.2, 0.5, 0.18), "fabric", col=col, rot_y=rot)
    for sx in (-1.05, 1.05):
        a = p(sx, 0)
        box(r.mb, (a[0], 0.45, a[1]), (0.18, 0.4, 0.9), "fabric", col=col, rot_y=rot)
    r.collide("wood", (b[0], 0.3, b[1]), (2.2 if abs(s) < 0.5 else 0.9, 0.6, 0.9 if abs(s) < 0.5 else 2.2))


def table(r, x, z, w=1.4, d=0.8, h=0.75):
    box(r.mb, (x, h - 0.03, z), (w, 0.06, d), "wood", col=(0.7, 0.55, 0.4, 1))
    for sx in (-1, 1):
        for sz in (-1, 1):
            box(r.mb, (x + sx * (w / 2 - 0.06), (h - 0.06) / 2, z + sz * (d / 2 - 0.06)), (0.06, h - 0.06, 0.06), "metal_dark")
    r.collide("wood", (x, h / 2, z), (w, h, d))


def chair(r, x, z, rot=0.0):
    box(r.mb, (x, 0.45, z), (0.45, 0.05, 0.45), "wood", col=(0.6, 0.45, 0.3, 1))
    back = (x + math.sin(rot) * 0.2, z + math.cos(rot) * 0.2)
    box(r.mb, (back[0], 0.72, back[1]), (0.45, 0.5, 0.05), "wood", col=(0.6, 0.45, 0.3, 1), rot_y=rot)
    for sx in (-0.19, 0.19):
        for sz in (-0.19, 0.19):
            box(r.mb, (x + sx, 0.22, z + sz), (0.04, 0.44, 0.04), "metal_dark")


def shelf(r, x, z, length, rot=0.0, depth=0.5, height=1.8, goods=True, seed=1):
    """Store shelf with coloured goods boxes."""
    ax = abs(math.sin(rot)) < 0.5
    sx, sz = (length, depth) if ax else (depth, length)
    box(r.mb, (x, height / 2, z), (sx, height, sz), "metal_painted", col=(0.85, 0.86, 0.88, 1))
    r.collide("building", (x, height / 2, z), (sx, height, sz))
    if not goods:
        return
    k = seed
    for level in range(4):
        y = 0.25 + level * 0.42
        n = int(length / 0.3)
        for i in range(n):
            k = (k * 1103515245 + 12345) & 0x7FFFFFFF
            col = [(0.9, 0.2, 0.2, 1), (0.2, 0.5, 0.9, 1), (0.95, 0.8, 0.2, 1), (0.3, 0.75, 0.35, 1),
                   (0.95, 0.95, 0.95, 1), (0.95, 0.5, 0.15, 1)][k % 6]
            hgt = 0.18 + (k % 5) * 0.03
            t = -length / 2 + 0.15 + i * 0.3
            for side in (-1, 1):
                off = side * (depth / 2 + 0.08)
                if ax:
                    c = (x + t, y + hgt / 2, z + off)
                    s = (0.24, hgt, 0.16)
                else:
                    c = (x + off, y + hgt / 2, z + t)
                    s = (0.16, hgt, 0.24)
                box(r.mb, c, s, "plastic", col=col)


def fridge_wall(r, x0, x1, z, height=2.1):
    box(r.mb, ((x0 + x1) / 2, height / 2, z), (x1 - x0, height, 0.7), "metal_painted", col=(0.9, 0.9, 0.92, 1))
    r.collide("building", ((x0 + x1) / 2, height / 2, z), (x1 - x0, height, 0.7))
    n = int((x1 - x0) / 0.9)
    for i in range(n):
        cx = x0 + 0.45 + i * 0.9
        box(r.mb, (cx, height / 2 + 0.05, z + 0.36), (0.8, height - 0.3, 0.02), "light_always", col=(0.8, 0.9, 1.0, 1))
        box(r.mb, (cx, height / 2 + 0.05, z + 0.38), (0.84, height - 0.26, 0.02), "glass_clear")


def counter(r, x, z, length, rot=0.0, col=(0.35, 0.3, 0.28, 1)):
    ax = abs(math.sin(rot)) < 0.5
    s = (length, 1.0, 0.7) if ax else (0.7, 1.0, length)
    box(r.mb, (x, 0.5, z), s, "wood", col=col)
    top = (s[0] + 0.08, 0.05, s[2] + 0.08)
    box(r.mb, (x, 1.025, z), top, "concrete", col=(0.85, 0.85, 0.83, 1))
    r.collide("wood", (x, 0.52, z), (s[0], 1.05, s[2]))


# ====================================================================== interiors
def safehouse():
    r = Room("safehouse", 11.0, 9.0, 3.0, floor_mat="wood_planks", floor_col=(0.8, 0.65, 0.5, 1),
             wall_col=(0.92, 0.9, 0.86, 1))
    # living area
    sofa(r, -2.5, -4.6, rot=0.0)
    table(r, -2.5, -3.4, 1.2, 0.6, 0.42)
    box(r.mb, (-2.5, 1.2, -8.85), (2.0, 1.1, 0.08), "metal_dark")                    # TV
    box(r.mb, (-2.5, 1.2, -8.8), (1.9, 1.0, 0.02), "glass_clear")
    box(r.mb, (-2.5, 0.3, -8.6), (2.4, 0.6, 0.5), "wood", col=(0.35, 0.25, 0.2, 1))  # TV board
    r.collide("wood", (-2.5, 0.3, -8.6), (2.4, 0.6, 0.5))
    box(r.mb, (-2.5, 0.01, -5.2), (3.2, 0.02, 2.4), "fabric", col=(0.55, 0.2, 0.2, 1))  # rug
    # bed area
    box(r.mb, (3.6, 0.3, -7.2), (2.0, 0.4, 2.3), "wood", col=(0.4, 0.3, 0.22, 1))
    box(r.mb, (3.6, 0.58, -7.2), (1.9, 0.2, 2.2), "fabric", col=(0.92, 0.92, 0.95, 1))
    box(r.mb, (3.6, 0.72, -8.05), (1.6, 0.14, 0.45), "fabric", col=(0.95, 0.95, 0.97, 1))
    box(r.mb, (3.6, 0.7, -6.8), (1.95, 0.08, 1.4), "fabric", col=(0.2, 0.3, 0.5, 1))
    box(r.mb, (3.6, 0.9, -8.4), (2.1, 1.0, 0.1), "wood", col=(0.4, 0.3, 0.22, 1))
    r.collide("wood", (3.6, 0.45, -7.2), (2.0, 0.9, 2.3))
    # wardrobe
    r.solid((5.1, 1.1, -3.0), (0.6, 2.2, 1.8), "wood", col=(0.55, 0.42, 0.3, 1))
    # kitchen + desk
    counter(r, -4.9, -1.9, 2.6, rot=math.pi / 2, col=(0.9, 0.9, 0.9, 1))
    box(r.mb, (-5.2, 1.6, -1.9), (0.4, 0.7, 2.6), "wood", col=(0.9, 0.9, 0.9, 1))
    table(r, 1.2, -1.6, 1.3, 0.7, 0.75)
    chair(r, 1.2, -1.0, rot=0.0)
    box(r.mb, (1.2, 0.95, -1.75), (0.5, 0.32, 0.03), "metal_dark")   # laptop
    for x, z in ((-2.5, -3.0), (3.0, -6.0), (0.5, -1.5), (-3.8, -7.5)):
        r.ceiling_light(x, z, energy=1.2)
    r.points.update(bed=[3.6, 0.05, -5.6], wardrobe=[4.4, 0.05, -3.0], save=[1.2, 0.05, -0.5],
                    sofa=[-2.5, 0.05, -4.3])
    return r


def store():
    r = Room("store", 14.0, 12.0, 3.4, floor_col=(0.9, 0.9, 0.88, 1), wall_col=(0.95, 0.95, 0.95, 1))
    for i, x in enumerate((-4.0, 0.0, 4.0)):
        shelf(r, x, -6.0, 5.0, rot=math.pi / 2, seed=11 + i * 7)
    fridge_wall(r, -6.8, 6.8, -11.6)
    counter(r, 4.8, -2.4, 3.2, rot=math.pi / 2, col=(0.2, 0.35, 0.55, 1))
    box(r.mb, (4.8, 1.2, -2.0), (0.4, 0.3, 0.35), "metal_dark")       # register
    box(r.mb, (6.7, 1.5, -2.4), (0.4, 2.0, 2.6), "metal_painted", col=(0.95, 0.6, 0.15, 1))  # cigarette rack
    r.collide("building", (6.7, 1.5, -2.4), (0.4, 2.0, 2.6))
    box(r.mb, (0, 3.0, -0.3), (5.0, 0.5, 0.05), "sign_emissive", col=(1.0, 0.3, 0.3, 1))
    for x in (-4.5, 0.0, 4.5):
        for z in (-2.5, -6.0, -9.5):
            r.ceiling_light(x, z, energy=1.4)
    r.points.update(counter=[3.9, 0.05, -2.4], clerk=[5.9, 0.05, -2.4, math.pi / 2])
    return r


def gunshop():
    r = Room("gunshop", 12.0, 22.0, 3.4, floor_col=(0.45, 0.45, 0.47, 1), wall_col=(0.55, 0.55, 0.52, 1))
    # sales area
    counter(r, 0.0, -4.5, 7.0, col=(0.25, 0.22, 0.2, 1))
    box(r.mb, (0.0, 1.07, -4.5), (6.6, 0.02, 0.6), "glass_clear")
    for i in range(6):
        x = -3.0 + i * 1.2
        box(r.mb, (x, 1.8, -7.8), (0.9, 0.12, 0.08), "gun_metal")
        box(r.mb, (x, 1.4, -7.8), (0.7, 0.1, 0.08), "gun_polymer")
    box(r.mb, (0, 1.6, -7.95), (8.0, 2.2, 0.1), "wood", col=(0.4, 0.28, 0.2, 1))
    # partition with a doorway to the shooting range
    r.solid((-3.5, 1.7, -9.0), (5.0, 3.4, 0.2), "interior_wall", col=(0.55, 0.55, 0.52, 1))
    r.solid((3.5, 1.7, -9.0), (5.0, 3.4, 0.2), "interior_wall", col=(0.55, 0.55, 0.52, 1))
    # range: booths + target boards at the back
    for i, x in enumerate((-3.0, 0.0, 3.0)):
        r.solid((x - 1.4, 0.8, -10.3), (0.1, 1.6, 1.6), "wood", col=(0.5, 0.4, 0.3, 1))
        box(r.mb, (x, 0.9, -10.9), (2.8, 0.06, 0.5), "wood", col=(0.5, 0.4, 0.3, 1))
        r.collide("wood", (x, 0.45, -10.9), (2.8, 0.9, 0.5))
        box(r.mb, (x, 1.5, -20.5), (0.9, 1.3, 0.05), "trim", col=(0.95, 0.95, 0.92, 1))
        cylinder(r.mb, (x, 1.5, -20.47), 0.3, 0.02, "sign_emissive", segs=16, axis="z", col=(0.9, 0.15, 0.1, 1))
        r.points["lane_%d" % i] = [x, 0.05, -10.0]
        r.points["target_%d" % i] = [x, 1.5, -20.45]
    box(r.mb, (0, 0.5, -21.5), (12.0, 1.0, 1.0), "concrete", col=(0.4, 0.38, 0.35, 1))   # backstop
    for z in (-2.5, -6.0, -12.0, -16.0, -19.5):
        r.ceiling_light(0.0, z, energy=1.5)
    r.points.update(counter=[0.0, 0.05, -3.6], clerk=[0.0, 0.05, -5.4, math.pi], range=[0.0, 0.05, -9.6])
    return r


def bank():
    r = Room("bank", 20.0, 18.0, 5.0, floor_mat="concrete", floor_col=(0.92, 0.9, 0.86, 1),
             wall_col=(0.88, 0.84, 0.76, 1))
    for x in (-6.0, 6.0):
        for z in (-4.0, -10.0):
            cylinder(r.mb, (x, 0, z), 0.45, 5.0, "concrete", segs=16, col=(0.95, 0.93, 0.9, 1))
            r.collide("building", (x, 2.5, z), (0.9, 5.0, 0.9))
    counter(r, 0.0, -12.0, 12.0, col=(0.4, 0.3, 0.22, 1))
    box(r.mb, (0.0, 1.9, -12.0), (12.0, 1.6, 0.04), "glass_clear")
    r.collide("building", (0.0, 1.9, -12.0), (12.0, 1.6, 0.1))
    # vault: back wall section with a round steel door
    r.solid((0.0, 2.5, -17.5), (6.0, 5.0, 0.6), "metal_dark")
    cylinder(r.mb, (0.0, 2.0, -17.15), 1.6, 0.3, "chrome", segs=32, axis="z")
    for a in range(6):
        ang = a * math.pi / 3
        box(r.mb, (math.cos(ang) * 0.9, 2.0 + math.sin(ang) * 0.9, -16.85), (0.12, 0.12, 0.25), "metal")
    # side passage around the counter to the vault
    box(r.mb, (-9.2, 0.01, -14.5), (1.2, 0.02, 4.0), "fabric", col=(0.5, 0.1, 0.12, 1))
    box(r.mb, (0.0, 0.01, -6.0), (4.0, 0.02, 10.0), "fabric", col=(0.5, 0.1, 0.12, 1))  # carpet
    box(r.mb, (0.0, 4.2, -0.3), (6.0, 0.6, 0.06), "sign_panel", col=(0.1, 0.25, 0.2, 1))
    for x in (-6.0, 0.0, 6.0):
        for z in (-3.0, -8.0, -14.5):
            r.ceiling_light(x, z, energy=1.3, rng=11.0)
    # counter is open at the left end so the vault can be reached
    r.points.update(vault=[0.0, 0.05, -15.8], teller=[0.0, 0.05, -13.2, math.pi], guard_0=[-7.5, 0.05, -6.0],
                    guard_1=[7.5, 0.05, -6.0], guard_2=[-4.0, 0.05, -15.0])
    return r


def main():
    reset_scene()
    os.makedirs(OUT, exist_ok=True)
    meta = {}
    for fn in (safehouse, store, gunshop, bank):
        r = fn()
        meta[r.name] = r.export()
        print(f"[interiors] {r.name}: {r.mb.tri_count()} tris")
    with open(os.path.join(OUT, "interiors.json"), "w") as f:
        json.dump(meta, f, indent=1)


if __name__ == "__main__":
    main()
