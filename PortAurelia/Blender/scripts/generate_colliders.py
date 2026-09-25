"""Collision generation.

World chunks carry one static trimesh per surface type, named `Col_<surface>-colonly`
(Godot's importer turns `-colonly` into a StaticBody3D with a ConcavePolygonShape3D; the
post-import script puts it on physics layer 1 and stores the surface for footstep sounds
and tyre grip). Props / vehicles / characters use primitive colliders built in Godot.

CLI:  python Blender/scripts/generate_colliders.py   -> validates chunk collisions
"""
import os
import sys

SURFACES = ("road", "sidewalk", "grass", "sand", "concrete", "wood", "building")


def new_collision_builders():
    from _common import MeshBuilder
    return {k: MeshBuilder() for k in SURFACES}


def collision_objects(cols, merge_dist=0.01):
    """Mesh objects for every non-empty surface builder."""
    objs = []
    for surf, mb in cols.items():
        if not mb.empty():
            objs.append(mb.to_object(f"Col_{surf}-colonly", merge_dist=merge_dist))
    return objs


def box_collider(mb, center, size):
    """Axis aligned collision box (collision builders take no materials)."""
    from _common import box
    box(mb, center, size, "concrete", top=True, bottom=True)


def main():
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from export_glb import read_glb_json, triangle_count
    here = os.path.dirname(os.path.abspath(__file__))
    city = os.path.join(os.path.dirname(os.path.dirname(here)), "Game", "assets", "generated", "city")
    bad = 0
    counts = {s: 0 for s in SURFACES}
    chunks = sorted(f for f in os.listdir(city) if f.startswith("chunk_") and f.endswith(".glb"))
    for f in chunks:
        j = read_glb_json(os.path.join(city, f))
        cols = [n for n in j["nodes"] if n["name"].startswith("Col_")]
        if not cols:
            bad += 1
            print(f"  ! {f}: no collision")
        for n in cols:
            surf = n["name"][4:].split("-")[0]
            if surf in counts and "mesh" in n:
                counts[surf] += triangle_count(j, n["mesh"])
    print(f"[colliders] {len(chunks)} chunks, {bad} without collision")
    for k, v in counts.items():
        print(f"  {k:9s} {v} triangles")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
