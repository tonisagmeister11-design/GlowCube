"""Generate Godot ShaderMaterial .tres files for every material name used by the
Blender pipeline. The Godot post-import script swaps imported materials for these
by name, so tweaking a value here (and re-running) restyles the whole city.

Usage: python Tools/Materials/build_materials.py
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
GAME = os.path.normpath(os.path.join(HERE, "..", "..", "Game"))
OUT = os.path.join(GAME, "assets", "materials")

SH = "res://assets/shaders/"
TX = "res://assets/textures/"

# name -> (shader, params). Texture params are given as ("tex", "name.png").
T = lambda n: ("tex", TX + n)  # noqa: E731


def surf(albedo=None, normal=None, **kw):
    p = {}
    if albedo:
        p["albedo_tex"] = T(albedo)
    if normal:
        p["normal_tex"] = T(normal)
    p["macro_noise"] = T("detail_noise.png")
    p.update(kw)
    return ("surface.gdshader", p)


def facade(style, wall="plaster", scale=(0.25, 0.25), **kw):
    p = {"style": style, "wall_tex": T(wall + "_col.png"), "wall_nrm": T(wall + "_nrm.png"),
         "detail_noise": T("detail_noise.png"), "wall_scale": ("vec2", scale)}
    p.update(kw)
    return ("facade.gdshader", p)


def weapon(base, **kw):
    p = {"base_color": base, "noise_tex": T("detail_noise.png")}
    p.update(kw)
    return ("weapon.gdshader", p)


def col(r, g, b):
    return ("color", (r, g, b))


MATERIALS = {
    # --------------------------------------------------------------- ground
    "road_asphalt": surf("asphalt_col.png", "asphalt_nrm.png", uv_scale=0.11, macro_strength=0.3),
    "parking": surf("asphalt_col.png", "asphalt_nrm.png", uv_scale=0.14, color=col(1.08, 1.08, 1.08)),
    "sidewalk": surf("pavers_col.png", "pavers_nrm.png", uv_scale=0.25, macro_strength=0.2),
    "curb": surf("concrete_col.png", "concrete_nrm.png", uv_scale=0.6, color=col(1.12, 1.1, 1.06), puddles=0.0),
    "concrete": surf("concrete_col.png", "concrete_nrm.png", uv_scale=0.1, macro_strength=0.3),
    "bridge_concrete": surf("concrete_col.png", "concrete_nrm.png", uv_scale=0.12, color=col(1.05, 1.03, 1.0),
                            puddles=0.0),
    "barrier": surf("concrete_col.png", "concrete_nrm.png", uv_scale=0.35, color=col(1.18, 1.16, 1.12), puddles=0.0),
    "tunnel_wall": surf("pavers_col.png", "pavers_nrm.png", uv_scale=0.8, color=col(1.3, 1.3, 1.25), rough_mul=0.5,
                        puddles=0.0),
    "grass": surf("grass_col.png", "grass_nrm.png", uv_scale=0.2, macro_strength=0.35),
    "sand": surf("sand_col.png", "sand_nrm.png", uv_scale=0.12),
    "dirt": surf("dirt_col.png", "dirt_nrm.png", uv_scale=0.15),
    "rock": surf("rock_col.png", "rock_nrm.png", uv_scale=0.1, puddles=0.0),
    "crop": surf("crop_col.png", "crop_nrm.png", uv_scale=1.0, world_uv=False),
    "road_marking": ("marking.gdshader", {"macro_noise": T("detail_noise.png")}),
    "terrain": ("terrain.gdshader", {
        "grass_col": T("grass_col.png"), "grass_nrm": T("grass_nrm.png"),
        "dirt_col": T("dirt_col.png"), "dirt_nrm": T("dirt_nrm.png"),
        "sand_col": T("sand_col.png"), "sand_nrm": T("sand_nrm.png"),
        "rock_col": T("rock_col.png"), "rock_nrm": T("rock_nrm.png"),
        "macro_noise": T("detail_noise.png")}),
    # --------------------------------------------------------------- building parts
    "roof_flat": surf("roof_membrane_col.png", "roof_membrane_nrm.png", uv_scale=0.1),
    "roof_tiles": surf("roof_tiles_col.png", "roof_tiles_nrm.png", uv_scale=0.35, world_uv=False, use_vertex_tint=True,
                       color=col(1.5, 1.5, 1.5), puddles=0.0),
    "roof_shingles": surf("shingles_col.png", "shingles_nrm.png", uv_scale=0.3, world_uv=False, use_vertex_tint=True,
                          color=col(2.0, 2.0, 2.0), puddles=0.0),
    "roof_metal": surf("metal_siding_col.png", "metal_siding_nrm.png", uv_scale=0.15, world_uv=False,
                       use_vertex_tint=True, metallic=0.5, puddles=0.0),
    "trim": surf("plaster_col.png", "plaster_nrm.png", uv_scale=0.5, color=col(0.95, 0.93, 0.88), puddles=0.0),
    "window_glass": surf(None, None, color=col(0.07, 0.09, 0.12), metallic=0.7, rough_mul=0.05, puddles=0.0),
    "shopfront": facade(5, "plaster", glass_color=col(0.1, 0.12, 0.14), lit_energy=1.6, win_spacing=3.0, room_depth=6.0),
    "sign_panel": surf("concrete_col.png", None, uv_scale=0.5, color=col(0.16, 0.16, 0.17), rough_mul=0.5, puddles=0.0),
    "sign_emissive": ("emissive.gdshader", {"mode": 2, "energy": 3.2}),
    "light_emissive": ("emissive.gdshader", {"mode": 1, "energy": 6.0, "color": col(1.0, 0.9, 0.72)}),
    "light_always": ("emissive.gdshader", {"mode": 0, "energy": 4.0, "color": col(1.0, 0.95, 0.85),
                                           "use_vertex_color": False}),
    "awning": surf("wood_siding_col.png", None, uv_scale=1.2, use_vertex_tint=True, rough_mul=1.1, puddles=0.0),
    "door": surf("wood_planks_col.png", "wood_planks_nrm.png", uv_scale=0.5, color=col(0.55, 0.45, 0.4), puddles=0.0),
    "interior_wall": surf("plaster_col.png", "plaster_nrm.png", uv_scale=0.3, color=col(1.0, 0.98, 0.95), puddles=0.0),
    "interior_floor": surf("wood_planks_col.png", "wood_planks_nrm.png", uv_scale=0.4, puddles=0.0, rough_mul=0.6),
    # --------------------------------------------------------------- generic props
    "metal": surf(None, None, color=col(0.62, 0.64, 0.66), metallic=0.85, rough_mul=0.35, puddles=0.0),
    "metal_dark": surf(None, None, color=col(0.09, 0.09, 0.1), metallic=0.6, rough_mul=0.45, puddles=0.0),
    "metal_painted": surf(None, None, use_vertex_tint=True, metallic=0.35, rough_mul=0.45, puddles=0.0),
    "chrome": surf(None, None, color=col(0.95, 0.95, 0.97), metallic=1.0, rough_mul=0.1, puddles=0.0),
    "plastic": surf(None, None, use_vertex_tint=True, rough_mul=0.5, puddles=0.0),
    "rubber": surf(None, None, color=col(0.04, 0.04, 0.04), rough_mul=0.9, puddles=0.0),
    "fabric": surf(None, None, use_vertex_tint=True, rough_mul=0.95, puddles=0.0),
    "wood": surf("wood_planks_col.png", "wood_planks_nrm.png", uv_scale=0.5, puddles=0.0),
    "wood_planks": surf("wood_planks_col.png", "wood_planks_nrm.png", uv_scale=0.35),
    "container": surf("metal_siding_col.png", "metal_siding_nrm.png", uv_scale=0.4, world_uv=False,
                      use_vertex_tint=True, metallic=0.3, puddles=0.0),
    "water_deco": surf(None, None, color=col(0.08, 0.42, 0.55), metallic=0.1, rough_mul=0.04, puddles=0.0),
    "glass_clear": ("glass.gdshader", {"tint": col(0.6, 0.7, 0.75), "opacity": 0.25,
                                       "noise_tex": T("detail_noise.png")}),
    "signal_lens": surf(None, None, color=col(0.05, 0.05, 0.05), rough_mul=0.2, puddles=0.0),
    # --------------------------------------------------------------- vegetation
    "bark": surf("bark_col.png", "bark_nrm.png", uv_scale=1.0, world_uv=False, puddles=0.0),
    "palm_bark": surf("palm_bark_col.png", "palm_bark_nrm.png", uv_scale=1.0, world_uv=False, puddles=0.0),
    "leaves": ("foliage.gdshader", {"albedo_tex": T("leaves_col.png")}),
    "leaves_dark": ("foliage.gdshader", {"albedo_tex": T("leaves_dark_col.png")}),
    "palm_frond": ("foliage.gdshader", {"albedo_tex": T("palm_frond_col.png"), "sway": 0.35, "flutter": 0.06}),
    "pine": ("foliage.gdshader", {"albedo_tex": T("pine_col.png"), "sway": 0.15, "alpha_cut": 0.35}),
    # --------------------------------------------------------------- vehicles & characters
    "car_paint": ("car_paint.gdshader", {"noise_tex": T("detail_noise.png")}),
    "car_paint_vc": ("car_paint.gdshader", {"noise_tex": T("detail_noise.png"), "use_vertex_color": True}),
    "car_glass": ("glass.gdshader", {"tint": col(0.05, 0.07, 0.09), "opacity": 0.78,
                                     "noise_tex": T("detail_noise.png")}),
    "car_trim": surf(None, None, color=col(0.05, 0.05, 0.055), metallic=0.2, rough_mul=0.6, puddles=0.0),
    "car_interior": surf(None, None, use_vertex_tint=True, rough_mul=0.8, puddles=0.0),
    "tire": surf("tire_col.png", "tire_nrm.png", uv_scale=1.0, world_uv=False, rough_mul=1.0, puddles=0.0),
    "rim": surf(None, None, color=col(0.72, 0.73, 0.75), metallic=0.9, rough_mul=0.25, puddles=0.0),
    "skin": surf(None, None, use_vertex_tint=True, rough_mul=0.55, puddles=0.0),
    "hair": surf(None, None, use_vertex_tint=True, rough_mul=0.7, puddles=0.0),
    "cloth": surf("wood_siding_col.png", None, uv_scale=3.0, world_uv=False, use_vertex_tint=True, rough_mul=1.05,
                  puddles=0.0, macro_strength=0.05),
    "leather": surf(None, None, use_vertex_tint=True, rough_mul=0.45, puddles=0.0),
    "eye": surf(None, None, use_vertex_tint=True, rough_mul=0.1, puddles=0.0),
    "car_light": ("vehicle_light.gdshader", {}),
    "car_siren": ("vehicle_siren.gdshader", {}),
    "char_skin": ("character.gdshader", {"roughness": 0.55, "sss": 0.35}),
    "char_cloth": ("character.gdshader", {"roughness": 0.9}),
    "char_hair": ("character.gdshader", {"roughness": 0.6, "rim": 0.15}),
    "char_detail": ("character.gdshader", {"roughness": 0.15}),
    # weapons (weapon.gdshader): edge wear from vertex colour R, AO from G
    "gun_metal": weapon(col(0.045, 0.047, 0.05), metallic=0.8, roughness=0.42, wear_color=col(0.42, 0.42, 0.41),
                        wear_amount=0.55),
    "gun_metal_dark": weapon(col(0.015, 0.015, 0.017), metallic=0.5, roughness=0.6, wear_amount=0.0),
    "gun_steel": weapon(col(0.56, 0.56, 0.55), metallic=1.0, roughness=0.34, wear_color=col(0.78, 0.78, 0.76),
                        wear_roughness=0.18, wear_amount=0.4, aniso=0.45),
    "gun_polymer": weapon(col(0.04, 0.04, 0.043), metallic=0.0, roughness=0.62, wear_color=col(0.12, 0.12, 0.12),
                          wear_metallic=0.0, wear_roughness=0.45, wear_amount=0.5, specular_amount=0.4),
    "gun_polymer_grip": weapon(col(0.035, 0.035, 0.038), metallic=0.0, roughness=0.8, wear_amount=0.0,
                               stipple=0.55, specular_amount=0.35),
    "gun_polymer_tan": weapon(col(0.42, 0.35, 0.25), metallic=0.0, roughness=0.72, wear_color=col(0.3, 0.27, 0.22),
                              wear_metallic=0.0, wear_roughness=0.6, wear_amount=0.45, specular_amount=0.35),
    "gun_wood": weapon(col(0.34, 0.18, 0.085), metallic=0.0, roughness=0.42, wear_color=col(0.55, 0.36, 0.2),
                       wear_metallic=0.0, wear_roughness=0.5, wear_amount=0.35, wood=True,
                       wood_dark=col(0.11, 0.05, 0.022), grain_scale=420.0, clearcoat_amount=0.6),
    "bat_wood": weapon(col(0.86, 0.72, 0.52), metallic=0.0, roughness=0.38, wear_amount=0.0, wood=True,
                       wood_dark=col(0.62, 0.46, 0.29), grain_scale=300.0, clearcoat_amount=0.8),
    "gun_rubber": weapon(col(0.03, 0.03, 0.03), metallic=0.0, roughness=0.93, wear_amount=0.0, stipple=0.25,
                         specular_amount=0.25),
    "gun_brass": weapon(col(0.78, 0.58, 0.28), metallic=1.0, roughness=0.28, wear_amount=0.0),
    "gun_lens": weapon(col(0.02, 0.03, 0.05), metallic=0.4, roughness=0.03, wear_amount=0.0, micro=0.0,
                       clearcoat_amount=1.0, fresnel_tint=1.0),
    "blade_steel": weapon(col(0.72, 0.72, 0.72), metallic=1.0, roughness=0.2, wear_color=col(0.95, 0.95, 0.95),
                          wear_roughness=0.08, wear_amount=1.0, aniso=0.6, micro=0.1),
    "gun_sight": ("emissive.gdshader", {"mode": 0, "energy": 5.0, "color": col(0.35, 1.0, 0.45)}),
    "gun_sight_red": ("emissive.gdshader", {"mode": 0, "energy": 7.0, "color": col(1.0, 0.1, 0.05)}),
}

FACADES = {
    "glass_blue": facade(2, "concrete", glass_color=col(0.10, 0.20, 0.32), spandrel_color=col(0.12, 0.18, 0.26),
                         win_spacing=1.6, glass_metal=0.65, lit_ratio=0.45),
    "glass_green": facade(2, "concrete", glass_color=col(0.10, 0.22, 0.2), spandrel_color=col(0.12, 0.2, 0.18),
                          win_spacing=1.6, glass_metal=0.6, lit_ratio=0.4),
    "glass_silver": facade(2, "concrete", glass_color=col(0.26, 0.29, 0.32), spandrel_color=col(0.5, 0.52, 0.55),
                           win_spacing=1.5, glass_metal=0.75, lit_ratio=0.4),
    "glass_bronze": facade(2, "concrete", glass_color=col(0.22, 0.15, 0.08), spandrel_color=col(0.25, 0.18, 0.1),
                           win_spacing=1.6, glass_metal=0.7, lit_ratio=0.4),
    "glass_dark": facade(2, "concrete", glass_color=col(0.05, 0.06, 0.07), spandrel_color=col(0.08, 0.08, 0.09),
                         win_spacing=1.8, glass_metal=0.6, lit_ratio=0.35),
    "concrete_panel": facade(1, "concrete", (0.12, 0.12), wall_color=col(1.12, 1.1, 1.06), glass_color=col(0.09, 0.12, 0.15),
                             lit_ratio=0.4),
    "stone": facade(0, "concrete", (0.2, 0.2), wall_color=col(0.95, 0.88, 0.76), win_spacing=2.6, win_width=0.45,
                    win_height=0.58, frame_color=col(0.3, 0.28, 0.25)),
    "brick_red": facade(0, "brick", (0.5, 1.0), win_spacing=2.8, win_width=0.42, win_height=0.55,
                        frame_color=col(0.85, 0.83, 0.78), tint_strength=0.0),
    "brick_brown": facade(0, "brick", (0.5, 1.0), wall_color=col(0.72, 0.6, 0.5), win_spacing=2.8, win_width=0.42,
                          win_height=0.55, frame_color=col(0.2, 0.2, 0.2), tint_strength=0.0),
    "plaster": facade(0, "plaster", (0.25, 0.25), win_spacing=3.0, win_width=0.45, win_height=0.52,
                      frame_color=col(0.9, 0.9, 0.88)),
    "stucco_white": facade(4, "plaster", (0.25, 0.25), wall_color=col(1.05, 1.05, 1.03), win_spacing=3.2, win_width=0.5,
                           win_height=0.48, frame_color=col(0.95, 0.95, 0.95)),
    "metal_siding": facade(3, "metal_siding", (0.25, 0.25), win_spacing=6.0, wall_rough_mul=0.9),
    "wood_siding": facade(4, "wood_siding", (0.35, 0.35), win_spacing=3.0, win_width=0.42, win_height=0.5,
                          frame_color=col(0.95, 0.95, 0.95)),
}
for k, v in FACADES.items():
    MATERIALS["facade_" + k] = v


def fmt(v):
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, int):
        return str(v)
    if isinstance(v, float):
        return repr(v)
    raise TypeError(v)


def write(name, shader, params):
    exts = [("Shader", SH + shader)]
    lines = []
    for key, val in params.items():
        if isinstance(val, tuple) and val[0] == "tex":
            if ("Texture2D", val[1]) not in exts:
                exts.append(("Texture2D", val[1]))
            idx = exts.index(("Texture2D", val[1])) + 1
            lines.append(f'shader_parameter/{key} = ExtResource("{idx}")')
        elif isinstance(val, tuple) and val[0] == "color":
            r, g, b = val[1]
            lines.append(f"shader_parameter/{key} = Color({r}, {g}, {b}, 1)")
        elif isinstance(val, tuple) and val[0] == "vec2":
            lines.append(f"shader_parameter/{key} = Vector2({val[1][0]}, {val[1][1]})")
        else:
            lines.append(f"shader_parameter/{key} = {fmt(val)}")
    out = [f'[gd_resource type="ShaderMaterial" load_steps={len(exts) + 1} format=3]', ""]
    for i, (t, p) in enumerate(exts):
        out.append(f'[ext_resource type="{t}" path="{p}" id="{i + 1}"]')
    out.append("")
    out.append("[resource]")
    out.append(f'resource_name = "{name}"')
    out.append('render_priority = 0')
    out.append('shader = ExtResource("1")')
    out.extend(lines)
    with open(os.path.join(OUT, name + ".tres"), "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, (shader, params) in MATERIALS.items():
        write(name, shader, params)
    print(f"{len(MATERIALS)} materials -> {OUT}")


if __name__ == "__main__":
    main()
