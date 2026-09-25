"""Original weapon models: pistol, revolver, SMG, assault rifle, shotgun, sniper rifle,
baseball bat and knife.

Convention: the grip (hand) is at the origin, the barrel points along Godot -Z.
Muzzle positions are written to weapons_meta.json.
Output: Game/assets/generated/weapons/weapons.glb
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from _common import GEN, MeshBuilder, reset_scene, export_glb, box, cylinder  # noqa: E402

OUT = os.path.join(GEN, "weapons")
M, P, W = "gun_metal", "gun_polymer", "gun_wood"


def barrel(mb, z0, length, r, y=0.0, mat=M, segs=10):
    cylinder(mb, (0, y, z0), r, -length, mat, segs=segs, axis="z", top=True, bottom=True)


def pistol():
    a = MeshBuilder()
    box(a, (0, 0.085, -0.07), (0.03, 0.035, 0.19), M)             # slide
    box(a, (0, 0.058, -0.06), (0.028, 0.02, 0.16), P)             # frame
    box(a, (0, 0.0, 0.0), (0.028, 0.11, 0.045), P, rot_y=0.0)     # grip
    box(a, (0, 0.03, -0.035), (0.01, 0.03, 0.035), M)             # trigger guard
    barrel(a, -0.165, 0.01, 0.006, 0.085)
    box(a, (0, 0.105, -0.15), (0.006, 0.006, 0.008), M)           # sight
    return a, (0, 0.085, -0.18)


def revolver():
    a = MeshBuilder()
    barrel(a, -0.06, 0.16, 0.009, 0.085)
    box(a, (0, 0.096, -0.13), (0.012, 0.01, 0.12), M)
    cylinder(a, (0, 0.078, -0.035), 0.022, -0.045, M, segs=10, axis="z", top=True, bottom=True)
    box(a, (0, 0.078, 0.0), (0.025, 0.035, 0.05), M)
    box(a, (0, 0.01, 0.02), (0.026, 0.1, 0.04), W)
    box(a, (0, 0.04, -0.03), (0.008, 0.02, 0.03), M)
    return a, (0, 0.085, -0.22)


def smg():
    a = MeshBuilder()
    box(a, (0, 0.07, -0.08), (0.04, 0.06, 0.26), P)
    barrel(a, -0.21, 0.08, 0.009, 0.075)
    box(a, (0, 0.0, 0.0), (0.03, 0.1, 0.04), P)
    box(a, (0, -0.02, -0.09), (0.022, 0.14, 0.03), M)             # magazine
    box(a, (0, 0.06, 0.12), (0.03, 0.03, 0.16), M)                # stock
    box(a, (0, 0.107, -0.08), (0.02, 0.012, 0.1), M)
    return a, (0, 0.075, -0.3)


def rifle():
    a = MeshBuilder()
    box(a, (0, 0.07, -0.1), (0.05, 0.075, 0.34), M)               # receiver
    box(a, (0, 0.065, -0.36), (0.045, 0.055, 0.2), P)             # handguard
    barrel(a, -0.46, 0.14, 0.01, 0.07)
    box(a, (0, 0.0, 0.0), (0.03, 0.11, 0.045), P)                 # grip
    box(a, (0, -0.02, -0.13), (0.028, 0.16, 0.05), M, rot_y=0.0)  # magazine
    box(a, (0, 0.06, 0.2), (0.04, 0.08, 0.22), P)                 # stock
    box(a, (0, 0.12, -0.12), (0.03, 0.03, 0.12), M)               # optic
    cylinder(a, (0, 0.14, -0.06), 0.018, -0.14, M, segs=10, axis="z", top=True, bottom=True)
    return a, (0, 0.07, -0.6)


def shotgun():
    a = MeshBuilder()
    barrel(a, -0.12, 0.5, 0.013, 0.085)
    barrel(a, -0.12, 0.38, 0.012, 0.058)                          # magazine tube
    box(a, (0, 0.072, -0.35), (0.04, 0.04, 0.14), W)              # pump
    box(a, (0, 0.075, -0.05), (0.045, 0.06, 0.18), M)
    box(a, (0, 0.0, 0.02), (0.032, 0.1, 0.045), W)
    box(a, (0, 0.05, 0.22), (0.042, 0.1, 0.28), W)
    return a, (0, 0.085, -0.62)


def sniper():
    a = MeshBuilder()
    barrel(a, -0.22, 0.6, 0.011, 0.075)
    box(a, (0, 0.07, -0.08), (0.048, 0.06, 0.3), M)
    box(a, (0, 0.055, -0.3), (0.05, 0.05, 0.22), P)
    cylinder(a, (0, 0.14, 0.05), 0.022, -0.3, M, segs=12, axis="z", top=True, bottom=True)
    cylinder(a, (0, 0.14, -0.25), 0.028, -0.04, M, segs=12, axis="z", top=True, bottom=True)
    box(a, (0, 0.0, 0.0), (0.03, 0.11, 0.045), P)
    box(a, (0, 0.05, 0.24), (0.045, 0.12, 0.3), P)
    box(a, (0.03, 0.09, -0.02), (0.03, 0.01, 0.01), M)            # bolt
    return a, (0, 0.075, -0.82)


def bat():
    a = MeshBuilder()
    cylinder(a, (0, 0, 0.06), 0.017, -0.28, W, segs=10, axis="z", r_top=0.02)
    cylinder(a, (0, 0, -0.22), 0.02, -0.55, W, segs=10, axis="z", r_top=0.034, top=True)
    cylinder(a, (0, 0, 0.07), 0.025, -0.02, M, segs=10, axis="z", top=True, bottom=True)
    return a, (0, 0, -0.8)


def knife():
    a = MeshBuilder()
    box(a, (0, 0, 0.02), (0.022, 0.028, 0.11), P)
    box(a, (0, 0, -0.04), (0.04, 0.035, 0.01), M)
    a.face([(0, 0.012, -0.045), (0, 0.012, -0.22), (0, -0.01, -0.2), (0, -0.014, -0.045)], None, "chrome", up=(1, 0, 0))
    a.face([(0, 0.012, -0.045), (0, -0.014, -0.045), (0, -0.01, -0.2), (0, 0.012, -0.22)], None, "chrome", up=(-1, 0, 0))
    return a, (0, 0, -0.22)


WEAPONS = {"pistol": pistol, "revolver": revolver, "smg": smg, "rifle": rifle, "shotgun": shotgun,
           "sniper": sniper, "bat": bat, "knife": knife}


def main():
    reset_scene()
    objs = []
    meta = {}
    for name, fn in WEAPONS.items():
        mb, muzzle = fn()
        objs.append(mb.to_object(name))
        meta[name] = {"muzzle": list(muzzle), "tris": mb.tri_count()}
    os.makedirs(OUT, exist_ok=True)
    export_glb(os.path.join(OUT, "weapons.glb"), objs)
    with open(os.path.join(OUT, "weapons_meta.json"), "w") as f:
        json.dump(meta, f, indent=1)
    print("[weapons]", len(objs))


if __name__ == "__main__":
    main()
