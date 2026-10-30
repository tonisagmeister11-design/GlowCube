class_name CityMaterials
extends RefCounted
## Gemeinsame Materialbibliothek der Stadt. Geometrie verweist nur ueber
## Schluessel (String) auf Materialien, damit sie in Worker-Threads erzeugt
## werden kann; die Materialien selbst entstehen einmalig im Hauptthread.
##
## Globale Werte (Nacht, Naesse, beleuchtete Fenster) setzt der
## Tag-Nacht-Zyklus ueber set_globals() auf allen Shader-Materialien.

const TEX := "res://assets/textures/"
const SH := "res://assets/shaders/"

static var _mats: Dictionary = {}
static var _globals: Dictionary = {"night": 0.0, "wet": 0.0, "lit_fraction": 0.5, "shop_lit": 1.0, "day_seed": 0.0}
static var _glow_shader: Shader
static var _tex_cache: Dictionary = {}


static func tex(name: String) -> Texture2D:
	if _tex_cache.has(name):
		return _tex_cache[name]
	var path := TEX + name + ".png"
	var t: Texture2D = load(path) if ResourceLoader.exists(path) else null
	_tex_cache[name] = t
	return t


## Material zu einem Schluessel (wird bei Bedarf erzeugt).
static func get_mat(key: String) -> Material:
	var m: Material = _mats.get(key)
	if m != null:
		return m
	m = _create(key)
	if m == null:
		push_warning("CityMaterials: unbekannter Schluessel '%s'" % key)
		m = _plain(Color(1, 0, 1))
	_mats[key] = m
	_apply_globals(m)
	return m


static func lookup() -> Callable:
	return func(k): return CityMaterials.get_mat(k)


static func set_globals(values: Dictionary) -> void:
	for k in values:
		_globals[k] = values[k]
	for key in _mats:
		_apply_globals(_mats[key])


static func get_global(name: String, def := 0.0) -> float:
	return float(_globals.get(name, def))


static func _apply_globals(m: Material) -> void:
	if m is ShaderMaterial:
		for k in _globals:
			m.set_shader_parameter(k, _globals[k])
	elif m is StandardMaterial3D and m.has_meta("night_emission"):
		var e: Vector2 = m.get_meta("night_emission")
		m.emission_energy_multiplier = lerpf(e.x, e.y, float(_globals.night))


# ------------------------------------------------------------------ Erzeugung

static func _shader(name: String) -> Shader:
	return load(SH + name + ".gdshader")


static func _facade(texname: String, tile: float, tint: float, metallic := 0.0, glass := false) -> ShaderMaterial:
	var m := ShaderMaterial.new()
	m.shader = _shader("facade")
	m.set_shader_parameter("wall_tex", tex(texname + "_albedo"))
	m.set_shader_parameter("wall_nrm", tex(texname + "_normal"))
	m.set_shader_parameter("interior_tex", tex("interior_atlas"))
	m.set_shader_parameter("noise_tex", tex("noise"))
	m.set_shader_parameter("tile", tile)
	m.set_shader_parameter("tint_amount", tint)
	m.set_shader_parameter("wall_metallic", metallic)
	m.set_shader_parameter("glass_facade", 1.0 if glass else 0.0)
	return m


static func _surface(texname: String, tile: float, opts := {}) -> ShaderMaterial:
	var m := ShaderMaterial.new()
	m.shader = _shader("surface")
	m.set_shader_parameter("albedo_tex", tex(texname + "_albedo"))
	m.set_shader_parameter("normal_tex", tex(texname + "_normal"))
	m.set_shader_parameter("noise_tex", tex("noise"))
	m.set_shader_parameter("tile", tile)
	for k in opts:
		m.set_shader_parameter(k, opts[k])
	return m


static func _plain(c: Color, rough := 0.6, metal := 0.0, vcol := true) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = rough
	m.metallic = metal
	m.vertex_color_use_as_albedo = vcol
	m.vertex_color_is_srgb = true
	return m


static func _glow(day_energy: float, night_energy: float, base := 0.6) -> ShaderMaterial:
	if _glow_shader == null:
		_glow_shader = Shader.new()
		_glow_shader.code = """shader_type spatial;
render_mode cull_back;
uniform float day_energy = 0.0;
uniform float night_energy = 3.0;
uniform float base = 0.6;
uniform float night = 0.0;
varying vec4 vcol;
void vertex() { vcol = COLOR; }
void fragment() {
	vec3 c = pow(vcol.rgb, vec3(2.2));
	ALBEDO = c * base;
	ROUGHNESS = 0.35;
	EMISSION = c * mix(day_energy, night_energy, night);
}
"""
	var m := ShaderMaterial.new()
	m.shader = _glow_shader
	m.set_shader_parameter("day_energy", day_energy)
	m.set_shader_parameter("night_energy", night_energy)
	m.set_shader_parameter("base", base)
	return m


static func _create(key: String) -> Material:
	match key:
		# Fassaden
		"fac_plaster": return _facade("plaster", 2.0, 1.0)
		"fac_brick": return _facade("brick", 1.2, 0.3)
		"fac_stone": return _facade("stone", 2.4, 0.55)
		"fac_concrete": return _facade("concrete", 3.0, 0.7)
		"fac_glass": return _facade("concrete", 3.0, 0.0, 0.0, true)
		"fac_metal": return _facade("metal", 2.0, 1.0, 0.45)
		"fac_wood": return _facade("wood", 2.0, 0.55)
		"fac_steel": return _facade("metal", 2.0, 0.85, 0.6)
		# Boden (Weltkoordinaten)
		"g_asphalt": return _surface("asphalt", 4.5, {"macro": 0.28, "puddles": 1.0, "normal_depth": 0.8})
		"g_asphalt_ov": return _surface("asphalt", 4.5, {"macro": 0.28, "puddles": 1.0, "depth_bias": 0.00025, "normal_depth": 0.8})
		"g_pavers": return _surface("pavers", 2.0, {"macro": 0.12, "puddles": 0.5})
		"g_pavers_ov": return _surface("pavers", 2.0, {"macro": 0.12, "puddles": 0.5, "depth_bias": 0.0003})
		"g_plaza": return _surface("plaza", 2.0, {"macro": 0.15, "puddles": 0.5})
		"g_plaza_ov": return _surface("plaza", 2.0, {"macro": 0.15, "puddles": 0.5, "depth_bias": 0.0003})
		"g_curb": return _surface("curb", 1.0, {"macro": 0.05})
		"g_grass": return _surface("grass", 4.0, {"macro": 0.35, "normal_depth": 0.7})
		"g_grass_ov": return _surface("grass", 4.0, {"macro": 0.35, "normal_depth": 0.7, "depth_bias": 0.0003})
		"g_gravel": return _surface("gravel", 3.0, {"macro": 0.2})
		"g_gravel_ov": return _surface("gravel", 3.0, {"macro": 0.2, "depth_bias": 0.0003})
		"g_dirt": return _surface("dirt", 4.0, {"macro": 0.3})
		"g_dirt_ov": return _surface("dirt", 4.0, {"macro": 0.3, "depth_bias": 0.0003})
		"g_concrete": return _surface("concrete", 3.0, {"macro": 0.15, "puddles": 0.6})
		"g_concrete_ov": return _surface("concrete", 3.0, {"macro": 0.15, "puddles": 0.6, "depth_bias": 0.0003})
		"g_stone": return _surface("stone", 2.4, {"macro": 0.12})
		"g_far": return _surface("asphalt", 30.0, {"macro": 0.2})
		# UV-Flaechen (Meter-UV)
		"u_roof_tiles": return _surface("roof_tiles", 2.0, {"use_uv": 1.0, "macro": 0.15, "normal_depth": 1.3})
		"u_roof_flat": return _surface("roof_flat", 4.0, {"use_uv": 1.0, "macro": 0.2})
		"u_metal": return _surface("metal", 2.0, {"use_uv": 1.0, "macro": 0.05, "metallic": 0.55})
		"u_wood": return _surface("wood", 2.0, {"use_uv": 1.0, "macro": 0.05})
		"u_concrete": return _surface("concrete", 3.0, {"use_uv": 1.0, "macro": 0.1})
		"u_stone": return _surface("stone", 2.4, {"use_uv": 1.0, "macro": 0.08})
		"u_plaster": return _surface("plaster", 2.0, {"use_uv": 1.0, "macro": 0.05})
		"u_brick": return _surface("brick", 1.2, {"use_uv": 1.0, "macro": 0.05})
		"u_fabric": return _surface("fabric", 1.0, {"use_uv": 1.0, "macro": 0.0, "normal_depth": 0.4})
		"u_bark": return _surface("bark", 1.2, {"use_uv": 1.0, "macro": 0.0})
		# Einfarbig ueber Vertexfarbe
		"paint": return _plain(Color.WHITE, 0.55)
		"paint_rough": return _plain(Color.WHITE, 0.85)
		"paint_metal": return _plain(Color.WHITE, 0.35, 0.75)
		"chrome": return _plain(Color.WHITE, 0.12, 1.0)
		"glass":
			var g := _plain(Color(0.06, 0.08, 0.09), 0.05, 0.3, false)
			g.metallic_specular = 0.8
			return g
		"glass_clear":
			var gc := _plain(Color(0.75, 0.85, 0.9, 0.22), 0.04, 0.0, false)
			gc.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
			gc.cull_mode = BaseMaterial3D.CULL_DISABLED
			return gc
		"lamp_glow": return _glow(0.0, 6.0, 0.9)
		"sign_glow": return _glow(0.15, 1.6, 0.85)
		"window_glow": return _glow(0.0, 2.2, 0.3)
		"neon": return _glow(0.4, 3.5, 0.6)
		"water":
			var w := ShaderMaterial.new()
			w.shader = _shader("water")
			w.set_shader_parameter("wave_nrm", tex("water_normal"))
			w.set_shader_parameter("noise_tex", tex("noise"))
			return w
		"pool_water":
			var pw := ShaderMaterial.new()
			pw.shader = _shader("water")
			pw.set_shader_parameter("wave_nrm", tex("water_normal"))
			pw.set_shader_parameter("noise_tex", tex("noise"))
			pw.set_shader_parameter("deep_col", Color(0.10, 0.45, 0.55))
			pw.set_shader_parameter("shallow_col", Color(0.25, 0.65, 0.70))
			pw.set_shader_parameter("scale", 4.0)
			pw.set_shader_parameter("flow", 0.0)
			return pw
		"foliage":
			var f := ShaderMaterial.new()
			f.shader = _shader("foliage")
			f.set_shader_parameter("leaf_tex", tex("leaves_albedo"))
			f.set_shader_parameter("leaf_nrm", tex("leaves_normal"))
			f.set_shader_parameter("noise_tex", tex("noise"))
			return f
		"hedge":
			var h := ShaderMaterial.new()
			h.shader = _shader("foliage")
			h.set_shader_parameter("leaf_tex", tex("leaves_albedo"))
			h.set_shader_parameter("leaf_nrm", tex("leaves_normal"))
			h.set_shader_parameter("noise_tex", tex("noise"))
			h.set_shader_parameter("wind", 0.0)
			h.set_shader_parameter("tile", 1.1)
			return h
		"marking":
			var mk := ShaderMaterial.new()
			mk.shader = _shader("marking")
			mk.set_shader_parameter("noise_tex", tex("noise"))
			return mk
		"marking_fresh":
			var mf := ShaderMaterial.new()
			mf.shader = _shader("marking")
			mf.set_shader_parameter("noise_tex", tex("noise"))
			mf.set_shader_parameter("wear", 0.05)
			return mf
		"signs":
			var s := StandardMaterial3D.new()
			s.albedo_texture = tex("signs_atlas")
			s.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
			s.alpha_scissor_threshold = 0.5
			s.roughness = 0.4
			s.cull_mode = BaseMaterial3D.CULL_BACK
			s.emission_enabled = true
			s.emission_texture = tex("signs_atlas")
			s.emission = Color(1, 1, 1)
			s.set_meta("night_emission", Vector2(0.0, 0.25))
			return s
		"signal":
			var sg := ShaderMaterial.new()
			sg.shader = _shader("signal")
			return sg
	return null


## Alle bekannten Materialien vorab erzeugen (verhindert Ruckler bei der
## ersten Verwendung; Shader werden dabei kompiliert).
static func warmup() -> void:
	for k in ["fac_plaster", "fac_brick", "fac_stone", "fac_concrete", "fac_glass", "fac_metal", "fac_wood", "fac_steel",
			"g_asphalt", "g_asphalt_ov", "g_pavers", "g_pavers_ov", "g_plaza", "g_plaza_ov", "g_curb", "g_grass", "g_grass_ov",
			"g_gravel", "g_gravel_ov", "g_dirt", "g_dirt_ov", "g_concrete", "g_concrete_ov", "g_stone",
			"u_roof_tiles", "u_roof_flat", "u_metal", "u_wood", "u_concrete", "u_stone", "u_plaster", "u_brick", "u_fabric", "u_bark",
			"paint", "paint_rough", "paint_metal", "chrome", "glass", "glass_clear", "lamp_glow", "sign_glow", "window_glow", "neon",
			"water", "pool_water", "foliage", "hedge", "marking", "marking_fresh", "signs", "signal"]:
		get_mat(k)
