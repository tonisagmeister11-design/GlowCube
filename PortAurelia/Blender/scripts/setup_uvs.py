"""UV conventions of the Port Aurelia pipeline + a validator.

* UV0: authored in Godot/glTF convention (v grows downwards). The glTF exporter flips
  Blender's V, so MeshBuilder stores `gltf_to_blender_v(v)` = 1 - v.
* Tiling textures use metric UVs: `planar_uv` / `wall_uv` give 1 UV unit per `scale` metres.
* UV2 on facades (material facade_*): (side length in m, floor height in m) so the facade
  shader can place a whole number of windows per wall and per floor.

CLI:  python Blender/scripts/setup_uvs.py   -> checks every generated mesh primitive has UV0
                                             and every facade primitive has UV2
"""
import os
import sys


def gltf_to_blender_v(v):
    return 1.0 - v


def planar_uv(p, axis="y", scale=1.0):
    """Metric planar projection of a Godot-space point (x, y, z)."""
    x, y, z = p
    if axis == "y":
        return (x / scale, z / scale)
    if axis == "x":
        return (z / scale, -y / scale)
    return (x / scale, -y / scale)


def wall_uv(dist_along, height, scale=1.0):
    """UV for vertical walls: u along the wall, v downwards from the top."""
    return (dist_along / scale, -height / scale)


def facade_uv2(side_length, floor_height):
    return (side_length, floor_height)


def _shader_uv_usage(game):
    """material name -> (uses UV, uses UV2), read from the Godot material and its shader."""
    import re
    out = {}
    mdir = os.path.join(game, "assets", "materials")
    for f in os.listdir(mdir):
        if not f.endswith(".tres"):
            continue
        txt = open(os.path.join(mdir, f), encoding="utf-8").read()
        m = re.search(r'path="res://([^"]+\.gdshader)"', txt)
        if not m:
            continue
        code = open(os.path.join(game, m.group(1)), encoding="utf-8").read()
        uses_tex = "texture(" in code and re.search(r"\bUV\b", code) is not None
        out[f[:-5]] = (uses_tex, re.search(r"\bUV2\b", code) is not None)
    return out


def main():
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from export_glb import all_glbs, read_glb_json
    here = os.path.dirname(os.path.abspath(__file__))
    gen = os.path.join(os.path.dirname(os.path.dirname(here)), "Game", "assets", "generated")
    problems = 0
    checked = 0
    game = os.path.join(os.path.dirname(os.path.dirname(here)), "Game")
    uses = _shader_uv_usage(game)
    for p in all_glbs(gen):
        j = read_glb_json(p)
        mats = [m.get("name", "") for m in j.get("materials", [])]
        for mesh in j.get("meshes", []):
            for prim in mesh["primitives"]:
                checked += 1
                attrs = prim["attributes"]
                mat = mats[prim["material"]] if "material" in prim else ""
                # collision-only meshes carry no UVs by design
                if mesh.get("name", "").startswith("Col_"):
                    continue
                need_uv, need_uv2 = uses.get(mat.split(".")[0], (True, mat.startswith("facade_")))
                if need_uv and "TEXCOORD_0" not in attrs:
                    problems += 1
                    print(f"  ! {os.path.basename(p)} {mesh.get('name')} ({mat}): no UV0")
                elif need_uv2 and "TEXCOORD_1" not in attrs:
                    problems += 1
                    print(f"  ! {os.path.basename(p)} {mesh.get('name')} ({mat}): facade without UV2")
    print(f"[uvs] {checked} primitives checked, {problems} problems")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
