"""glTF/GLB export for the Port Aurelia pipeline + a validation/summary tool.

Conventions: Godot space (Y up) is exported with `export_yup`, vertex colours as the
ACTIVE colour attribute, materials by name only (Godot replaces them on import from
Game/assets/materials/<name>.tres), no embedded images.

CLI:  python Blender/scripts/export_glb.py            -> summary of all generated GLBs
"""
import json
import os
import struct
import sys

import bpy


def export_glb(path, objects=None, with_anim=False, collection_name=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if objects is not None:
        bpy.ops.object.select_all(action="DESELECT")
        for o in objects:
            o.select_set(True)
            for c in o.children_recursive:
                c.select_set(True)
    kw = dict(filepath=path, export_format="GLB", use_selection=objects is not None,
              export_apply=True, export_vertex_color="ACTIVE", export_all_vertex_colors=False,
              export_animations=with_anim, export_skins=with_anim, export_materials="EXPORT",
              export_image_format="NONE", export_extras=True, export_yup=True,
              export_tangents=False, export_normals=True)
    if with_anim:
        kw.update(export_animation_mode="ACTIONS", export_force_sampling=True,
                  export_optimize_animation_size=True, export_anim_single_armature=True)
    try:
        bpy.ops.export_scene.gltf(**kw)
    except TypeError:
        kw.pop("export_all_vertex_colors", None)
        bpy.ops.export_scene.gltf(**kw)
    return path


def read_glb_json(path):
    """Returns the JSON chunk of a GLB file (no bpy needed)."""
    with open(path, "rb") as f:
        head = f.read(20)
        if head[:4] != b"glTF":
            raise ValueError(f"{path}: not a GLB file")
        length = struct.unpack("<I", head[12:16])[0]
        return json.loads(f.read(length))


def all_glbs(root):
    out = []
    for dp, _dn, fn in os.walk(root):
        for f in fn:
            if f.endswith(".glb"):
                out.append(os.path.join(dp, f))
    return sorted(out)


def triangle_count(j, mesh_index):
    tris = 0
    for p in j["meshes"][mesh_index]["primitives"]:
        if "indices" in p:
            tris += j["accessors"][p["indices"]]["count"] // 3
    return tris


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    gen = os.path.join(os.path.dirname(os.path.dirname(here)), "Game", "assets", "generated")
    files = all_glbs(gen)
    total_size = 0
    groups = {}
    for p in files:
        rel = os.path.relpath(p, gen)
        grp = rel.split(os.sep)[0]
        size = os.path.getsize(p)
        total_size += size
        j = read_glb_json(p)
        tris = sum(triangle_count(j, i) for i in range(len(j.get("meshes", []))))
        g = groups.setdefault(grp, [0, 0, 0])
        g[0] += 1
        g[1] += size
        g[2] += tris
    for grp, (n, size, tris) in sorted(groups.items()):
        print(f"{grp:12s} {n:4d} files  {size / 1e6:8.1f} MB  {tris:10d} tris")
    print(f"{'total':12s} {len(files):4d} files  {total_size / 1e6:8.1f} MB")
    return 0 if files else 1


if __name__ == "__main__":
    sys.exit(main())
