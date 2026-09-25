"""Level-of-detail chain of Port Aurelia.

  LOD0  per chunk: Buildings_LOD0 + Detail_LOD0 + Markings_LOD0 (0-260 m)
  LOD1  per chunk: Buildings_LOD1 (simplified shells, 260 m - chunk unload)
  LOD2  city_far.glb: one merged low-detail node per chunk (terrain + building boxes),
        shown where chunks are not streamed in
  LOD3  Godot's automatic mesh LOD (importer) on props, vehicles and characters
Chunk pivots sit at the chunk centre because Godot measures visibility ranges from
the node origin.

Functions: `lod_decimate` (collapse decimation of any object), `build_far` (LOD2 file).
CLI:  python Blender/scripts/generate_lods.py   -> LOD report / validation of the city
"""
import os
import sys

import bpy


def lod_decimate(obj, ratio):
    """Collapse-decimate a copy of obj (returns new object)."""
    new = obj.copy()
    new.data = obj.data.copy()
    bpy.context.scene.collection.objects.link(new)
    mod = new.modifiers.new("dec", "DECIMATE")
    mod.ratio = ratio
    mod.use_collapse_triangulate = True
    bpy.context.view_layer.objects.active = new
    dg = bpy.context.evaluated_depsgraph_get()
    ev = new.evaluated_get(dg)
    me = bpy.data.meshes.new_from_object(ev)
    new.modifiers.clear()
    old = new.data
    new.data = me
    bpy.data.meshes.remove(old)
    return new


def chunk_center(wmin, chunk, cx, cz):
    return (wmin + (cx + 0.5) * chunk, 0.0, wmin + (cz + 0.5) * chunk)


def build_far(far_mbs, wmin, chunk, out_path):
    """LOD2: city_far.glb with one Far_<cx>_<cz> node per chunk."""
    from _common import clear_objects
    from export_glb import export_glb
    objs = []
    for (cx, cz), mb in sorted(far_mbs.items()):
        if not mb.empty():
            objs.append(mb.to_object(f"Far_{cx}_{cz}", merge_dist=0.0, origin=chunk_center(wmin, chunk, cx, cz)))
    if objs:
        export_glb(out_path, objs)
    clear_objects()


def main():
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from export_glb import read_glb_json, triangle_count
    here = os.path.dirname(os.path.abspath(__file__))
    city = os.path.join(os.path.dirname(os.path.dirname(here)), "Game", "assets", "generated", "city")
    levels = {"LOD0": 0, "LOD1": 0, "LOD2": 0}
    missing = 0
    chunks = [f for f in os.listdir(city) if f.startswith("chunk_") and f.endswith(".glb")]
    for f in chunks:
        j = read_glb_json(os.path.join(city, f))
        names = {}
        for n in j["nodes"]:
            if "mesh" in n:
                names[n["name"]] = triangle_count(j, n["mesh"])
        for n, t in names.items():
            if n.endswith("_LOD0") or n in ("Ground", "Structures"):
                levels["LOD0"] += t
            elif n.endswith("_LOD1"):
                levels["LOD1"] += t
        if "Buildings_LOD0" in names and "Buildings_LOD1" not in names:
            missing += 1
            print(f"  ! {f}: Buildings_LOD0 without LOD1")
    far = os.path.join(city, "city_far.glb")
    far_nodes = 0
    if os.path.exists(far):
        j = read_glb_json(far)
        for n in j["nodes"]:
            if "mesh" in n:
                far_nodes += 1
                levels["LOD2"] += triangle_count(j, n["mesh"])
    print(f"[lods] {len(chunks)} chunks, far nodes {far_nodes}")
    for k, v in levels.items():
        print(f"  {k}: {v} triangles")
    if far_nodes < len(chunks) * 0.8:
        print("  ! far HLOD does not cover the chunks")
        missing += 1
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
