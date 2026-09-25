"""Build the reusable asset libraries (props, vegetation, lights) as GLB + metadata.

Output:
    Game/assets/generated/props/props_library.glb   nodes: <type>, <type>_LOD1
    Game/assets/generated/props/props_meta.json     AABB, light points, variants

Usage: python Blender/scripts/build_asset_library.py
"""
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from _common import G, GEN, MeshBuilder, reset_scene, clear_objects, export_glb, add_signs  # noqa: E402
from generate_vegetation import VEGETATION  # noqa: E402
from generate_lights import LIGHTS, LIGHT_POINTS  # noqa: E402
from generate_props import PROPS, VARIANTS  # noqa: E402

OUT = os.path.join(GEN, "props")


def aabb(mb):
    if not mb.verts:
        return [0, 0, 0, 0, 0, 0]
    xs = [v[0] for v in mb.verts]
    ys = [v[1] for v in mb.verts]
    zs = [v[2] for v in mb.verts]
    return [round(min(xs), 3), round(min(ys), 3), round(min(zs), 3), round(max(xs), 3), round(max(ys), 3), round(max(zs), 3)]


def main():
    t0 = time.time()
    reset_scene()
    rng = G.Rng(1234)
    objs = []
    meta = {"types": {}}
    entries = []
    for name, fn in VEGETATION.items():
        entries.append((name, fn, "vegetation", None))
    for name, fn in LIGHTS.items():
        entries.append((name, fn, "light", None))
    for name, fn in PROPS.items():
        n = VARIANTS.get(name, 1)
        if n > 1:
            for v in range(n):
                entries.append((f"{name}_{v}", fn, "prop", v))
        else:
            entries.append((name, fn, "prop", None))
    for (name, fn, kind, variant) in entries:
        try:
            lod0, lod1 = fn(rng, variant=variant) if variant is not None else fn(rng)
        except TypeError:
            lod0, lod1 = fn(rng)
        txt = getattr(lod0, "_billboard_text", None)
        if txt:
            text, fg = txt
            add_signs(lod0, [(text, (0.0, 11.6, -0.08), 3.14159, 1.5, fg)])
        smooth = kind == "vegetation"
        objs.append(lod0.to_object(name, merge_dist=0.0 if smooth else 0.0005))
        if lod1 is not lod0 and not lod1.empty():
            objs.append(lod1.to_object(name + "_LOD1", merge_dist=0.0 if smooth else 0.0005))
        meta["types"][name] = dict(kind=kind, aabb=aabb(lod0), tris=lod0.tri_count(),
                                   lod1=lod1 is not lod0, lights=LIGHT_POINTS.get(name, []))
        print(f"  {name}: {lod0.tri_count()} tris")
    meta["variants"] = VARIANTS
    os.makedirs(OUT, exist_ok=True)
    export_glb(os.path.join(OUT, "props_library.glb"), objs)
    with open(os.path.join(OUT, "props_meta.json"), "w") as f:
        json.dump(meta, f, indent=1)
    clear_objects()
    print(f"[library] {len(entries)} props in {time.time() - t0:.1f}s")


if __name__ == "__main__":
    main()
