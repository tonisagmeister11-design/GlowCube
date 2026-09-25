"""Material setup for the Blender pipeline.

Blender only needs preview colours: every material is exported by *name* and the Godot
post-import script swaps it for the PBR ShaderMaterial Game/assets/materials/<name>.tres
(built by Tools/Materials/build_materials.py). This module owns the material names and a
validator that checks every name used in the generated GLBs has a Godot material.

CLI:  python Blender/scripts/setup_materials.py   -> exit code 1 if a material is missing
"""
import json
import os
import sys

import bpy

# preview colours only; Godot replaces materials by name on import
MATERIAL_PREVIEW = {
    "road_asphalt": (0.12, 0.12, 0.13), "road_marking": (0.9, 0.9, 0.85), "sidewalk": (0.6, 0.58, 0.55),
    "curb": (0.65, 0.64, 0.6), "concrete": (0.55, 0.55, 0.53), "parking": (0.15, 0.15, 0.16),
    "terrain": (0.35, 0.45, 0.2), "grass": (0.3, 0.45, 0.15), "sand": (0.85, 0.77, 0.58),
    "dirt": (0.45, 0.36, 0.25), "rock": (0.5, 0.48, 0.44), "bridge_concrete": (0.6, 0.6, 0.58),
    "metal": (0.6, 0.62, 0.64), "barrier": (0.72, 0.71, 0.68), "tunnel_wall": (0.75, 0.75, 0.72),
    "light_emissive": (1.0, 0.95, 0.8), "roof_flat": (0.45, 0.45, 0.44), "roof_tiles": (0.6, 0.3, 0.2),
    "roof_shingles": (0.3, 0.3, 0.32), "roof_metal": (0.6, 0.62, 0.64), "trim": (0.85, 0.83, 0.78),
    "window_glass": (0.2, 0.3, 0.4), "shopfront": (0.2, 0.25, 0.3), "awning": (0.7, 0.2, 0.2),
    "sign_emissive": (1.0, 0.3, 0.6), "sign_panel": (0.2, 0.2, 0.22), "metal_dark": (0.12, 0.12, 0.13),
    "door": (0.3, 0.22, 0.16), "wood": (0.5, 0.38, 0.26), "plastic": (0.8, 0.8, 0.8),
    "metal_painted": (0.4, 0.45, 0.5), "rubber": (0.05, 0.05, 0.05), "fabric": (0.6, 0.6, 0.6),
    "bark": (0.35, 0.27, 0.2), "leaves": (0.25, 0.4, 0.12), "palm_frond": (0.3, 0.45, 0.15),
    "palm_bark": (0.5, 0.42, 0.3), "pine": (0.15, 0.28, 0.12), "glass_clear": (0.7, 0.8, 0.85),
    "signal_lens": (0.2, 0.2, 0.2), "container": (0.6, 0.3, 0.2), "water_deco": (0.2, 0.4, 0.5),
    "wood_planks": (0.5, 0.38, 0.26), "interior_wall": (0.9, 0.88, 0.84), "interior_floor": (0.5, 0.45, 0.4),
    "crop": (0.45, 0.5, 0.2), "chrome": (0.9, 0.9, 0.92),
}
FACADES = ["glass_blue", "glass_green", "glass_silver", "glass_bronze", "glass_dark", "concrete_panel",
           "stone", "brick_red", "brick_brown", "plaster", "stucco_white", "metal_siding", "wood_siding"]
for _f in FACADES:
    MATERIAL_PREVIEW["facade_" + _f] = (0.6, 0.62, 0.66) if _f.startswith("glass") else (0.8, 0.76, 0.7)


def get_material(name):
    m = bpy.data.materials.get(name)
    if m is not None:
        return m
    m = bpy.data.materials.new(name)
    col = MATERIAL_PREVIEW.get(name, (0.8, 0.8, 0.8))
    m.diffuse_color = (col[0], col[1], col[2], 1.0)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get("Principled BSDF")
    if bsdf:
        bsdf.inputs["Base Color"].default_value = (col[0], col[1], col[2], 1.0)
        bsdf.inputs["Roughness"].default_value = 0.8
    return m


def main():
    from export_glb import all_glbs, read_glb_json
    here = os.path.dirname(os.path.abspath(__file__))
    game = os.path.join(os.path.dirname(os.path.dirname(here)), "Game")
    mat_dir = os.path.join(game, "assets", "materials")
    have = {f[:-5] for f in os.listdir(mat_dir) if f.endswith(".tres")}
    used = {}
    for p in all_glbs(os.path.join(game, "assets", "generated")):
        for m in read_glb_json(p).get("materials", []):
            name = m.get("name", "").split(".")[0]
            used.setdefault(name, set()).add(os.path.basename(p))
    missing = sorted(n for n in used if n not in have)
    report = {"materials_used": len(used), "materials_available": len(have), "missing": missing}
    out = os.path.join(os.path.dirname(here), "build", "material_report.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w") as f:
        json.dump(report, f, indent=1)
    print(f"[materials] {len(used)} used, {len(have)} available, missing: {missing or 'none'}")
    for n in missing:
        print(f"  ! {n} used in {sorted(used[n])[:4]}")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    sys.exit(main())
