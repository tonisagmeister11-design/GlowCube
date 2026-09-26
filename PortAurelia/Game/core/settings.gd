extends Node
## Player settings (graphics, audio, controls), persisted in Config/settings.cfg.

signal changed(section: String, key: String)

const DEFAULTS := {
	"graphics": {
		"resolution": Vector2i(1920, 1080),
		"window_mode": 0,          # 0 fullscreen, 1 windowed, 2 borderless
		"vsync": true,
		"quality": 2,              # 0 low, 1 medium, 2 high, 3 ultra
		"texture_quality": 2,      # 0 low .. 2 high
		"shadow_quality": 2,       # 0 off .. 3 ultra
		"view_distance": 1,        # 0 near, 1 normal, 2 far
		"effects": 2,              # 0 low .. 2 high (particles, SSR, SSAO)
		"anti_aliasing": 1,        # 0 off, 1 FXAA, 2 TAA, 3 MSAA 4x
		"fps_limit": 0,
		"fov": 70.0,
		"performance_mode": 0,     # 0 off, 1 balanced, 2 maximum (weak laptops)
		"renderer": 0,             # 0 auto (by performance mode), 1 Forward+, 2 Mobile, 3 Compatibility (OpenGL)
		"render_scale": 1.0,       # 3D resolution scale (upscaled)
	},
	"audio": {
		"master": 0.9,
		"sfx": 1.0,
		"weapons": 1.0,
		"vehicles": 1.0,
		"environment": 1.0,
		"voice": 1.0,
		"music": 0.0,              # music is disabled until the player enables it
		"music_enabled": false,
		"ui": 0.8,
	},
	"controls": {
		"mouse_sensitivity": 1.0,
		"invert_y": false,
		"controller_enabled": true,
		"controller_sensitivity": 1.0,
		"vibration": true,
	},
	"gameplay": {
		"subtitles": true,
		"minimap_rotate": true,
		"hud_scale": 1.0,
		"speed_units": 0,          # 0 km/h, 1 mph
		"show_hud": true,
		"show_minimap": true,
		"crosshair": true,
	},
	"camera": {
		"foot_view": 0,            # 0 third person, 1 first person
		"vehicle_view": 0,         # 0 chase near, 1 chase far, 2 hood, 3 cockpit (first person)
		"distance": 1.0,           # third-person distance multiplier
		"height": 1.0,             # third-person height multiplier
		"shake": 1.0,              # camera shake intensity (0 = off)
		"auto_center": true,       # vehicle camera re-centres behind the car
		"head_bob": true,          # first person head bob while walking
	},
}

const RENDERERS := ["", "forward_plus", "mobile", "gl_compatibility"]

var data := {}
var _path := ""


func _ready() -> void:
	_path = Paths.config_dir.path_join("settings.cfg")
	data = DEFAULTS.duplicate(true)
	load_settings()
	apply_all()


func get_value(section: String, key: String):
	return data.get(section, {}).get(key, DEFAULTS.get(section, {}).get(key))


func set_value(section: String, key: String, value, apply_now := true) -> void:
	if not data.has(section):
		data[section] = {}
	data[section][key] = value
	if apply_now:
		apply_all()
	changed.emit(section, key)


func load_settings() -> void:
	var cfg := ConfigFile.new()
	if cfg.load(_path) != OK:
		return
	for section in DEFAULTS:
		for key in DEFAULTS[section]:
			if cfg.has_section_key(section, key):
				data[section][key] = cfg.get_value(section, key)


func save_settings() -> void:
	var cfg := ConfigFile.new()
	for section in data:
		for key in data[section]:
			cfg.set_value(section, key, data[section][key])
	cfg.save(_path)


func apply_all() -> void:
	_apply_display()
	_apply_audio()
	_apply_rendering()


func _apply_display() -> void:
	if DisplayServer.get_name() == "headless":
		return
	var mode: int = get_value("graphics", "window_mode")
	match mode:
		0:
			DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_EXCLUSIVE_FULLSCREEN)
		2:
			DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_FULLSCREEN)
		_:
			DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_WINDOWED)
			var res: Vector2i = get_value("graphics", "resolution")
			DisplayServer.window_set_size(res)
	DisplayServer.window_set_vsync_mode(DisplayServer.VSYNC_ENABLED if get_value("graphics", "vsync") else DisplayServer.VSYNC_DISABLED)
	Engine.max_fps = int(get_value("graphics", "fps_limit"))


func _apply_audio() -> void:
	_bus("Master", get_value("audio", "master"))
	_bus("SFX", get_value("audio", "sfx"))
	_bus("Weapons", get_value("audio", "weapons"))
	_bus("Vehicles", get_value("audio", "vehicles"))
	_bus("Environment", get_value("audio", "environment"))
	_bus("Voice", get_value("audio", "voice"))
	_bus("UI", get_value("audio", "ui"))
	var music_on: bool = get_value("audio", "music_enabled")
	_bus("Music", get_value("audio", "music") if music_on else 0.0)


func _bus(name: String, linear: float) -> void:
	var idx := AudioServer.get_bus_index(name)
	if idx < 0:
		return
	AudioServer.set_bus_mute(idx, linear <= 0.001)
	AudioServer.set_bus_volume_db(idx, linear_to_db(maxf(linear, 0.0001)))


func _apply_rendering() -> void:
	var vp := get_viewport()
	if vp == null:
		return
	var perf := perf_mode()
	var compat := RenderingServer.get_current_rendering_method() == "gl_compatibility"
	var aa: int = get_value("graphics", "anti_aliasing") if perf < 2 else 0
	vp.screen_space_aa = Viewport.SCREEN_SPACE_AA_FXAA if aa == 1 else Viewport.SCREEN_SPACE_AA_DISABLED
	vp.use_taa = aa == 2 and not compat
	vp.msaa_3d = Viewport.MSAA_4X if aa == 3 else Viewport.MSAA_DISABLED
	# 3D resolution: performance modes render fewer pixels and upscale (FSR where available)
	var scale := minf(float(get_value("graphics", "render_scale")), [1.0, 0.8, 0.6][perf])
	vp.scaling_3d_mode = Viewport.SCALING_3D_MODE_BILINEAR if compat or scale >= 0.999 else Viewport.SCALING_3D_MODE_FSR
	vp.scaling_3d_scale = scale
	vp.fsr_sharpness = 0.35
	var q := quality()
	vp.mesh_lod_threshold = [4.0, 2.5, 1.5, 1.0][q] * [1.0, 1.4, 2.4][perf]
	var sq := shadow_quality()
	RenderingServer.directional_shadow_atlas_set_size([1024, 1024, 2048, 4096][clampi(sq, 0, 3)] if perf == 0 else
		[1024, 1024, 2048, 2048][clampi(sq, 0, 3)], true)
	vp.positional_shadow_atlas_size = [256, 1024, 2048, 4096][clampi(sq, 0, 3)] if perf == 0 else [256, 512, 1024, 1024][clampi(sq, 0, 3)]
	var tq: int = get_value("graphics", "texture_quality")
	vp.anisotropic_filtering_level = [Viewport.ANISOTROPY_2X, Viewport.ANISOTROPY_4X, Viewport.ANISOTROPY_16X][clampi(tq, 0, 2)] \
		if perf < 2 else Viewport.ANISOTROPY_2X
	# FPS limit: maximum performance caps at 30 FPS unless the player picked a limit (saves power)
	var fps := int(get_value("graphics", "fps_limit"))
	Engine.max_fps = fps if fps > 0 or perf < 2 else 30
	# character micro detail (pores, weave, strands) off in the performance modes
	for m in ["char_skin", "char_cloth", "char_hair"]:
		var mat := load("res://assets/materials/%s.tres" % m) as ShaderMaterial
		if mat:
			mat.set_shader_parameter("detail", [1.0, 0.6, 0.0][perf])
	apply_effects()
	_write_renderer_override()
	if is_inside_tree():
		for sky in get_tree().get_nodes_in_group("sky_environment"):
			sky.call("apply_quality", q)


## Screen-space effects on the world environment (called again when the world loads).
func apply_effects() -> void:
	if not is_inside_tree():
		return
	var w = get_tree().get_first_node_in_group("world_environment")
	if w == null or not (w is WorldEnvironment):
		return
	var env: Environment = (w as WorldEnvironment).environment
	var fx := effects()
	var q := quality()
	var fplus := RenderingServer.get_current_rendering_method() == "forward_plus"
	env.ssao_enabled = fx >= 1 and fplus
	env.ssr_enabled = fx >= 2 and q >= 2 and fplus
	env.ssil_enabled = fx >= 2 and q >= 3 and fplus
	env.glow_enabled = perf_mode() < 2
	env.volumetric_fog_enabled = fx >= 2 and q >= 3 and fplus


# ------------------------------------------------------------------ performance mode
func perf_mode() -> int:
	return clampi(int(get_value("graphics", "performance_mode")), 0, 2)


## Renderer the game should start with ("forward_plus", "mobile", "gl_compatibility").
func wanted_renderer() -> String:
	var r := clampi(int(get_value("graphics", "renderer")), 0, 3)
	if r == 0:
		return "gl_compatibility" if perf_mode() == 2 else "forward_plus"
	return RENDERERS[r]


func renderer_restart_needed() -> bool:
	return wanted_renderer() != RenderingServer.get_current_rendering_method()


## The renderer can only change at start-up: exported builds write override.cfg next to
## StartGame.exe (Godot loads it automatically before the renderer is created).
func _write_renderer_override() -> void:
	if OS.has_feature("editor") or not OS.has_feature("template"):
		return
	var path := OS.get_executable_path().get_base_dir().path_join("override.cfg")
	var want := wanted_renderer()
	if want == "forward_plus":
		if FileAccess.file_exists(path):
			DirAccess.remove_absolute(path)
		return
	var f := FileAccess.open(path, FileAccess.WRITE)
	if f == null:
		return
	f.store_string("; written by Harbor Heat (Einstellungen > Grafik > Renderer)\n[rendering]\n\n" +
		"renderer/rendering_method=\"%s\"\nrenderer/rendering_method.mobile=\"%s\"\n" % [want, want])
	f.close()


## Population multiplier for pedestrians and traffic.
func population_scale() -> float:
	var s: float = [1.0, 0.8, 0.55][perf_mode()]
	# the OpenGL renderer has a small per-instance shader parameter buffer
	if RenderingServer.get_current_rendering_method() == "gl_compatibility":
		s = minf(s, 0.55)
	return s


func shadow_quality() -> int:
	var sq := int(get_value("graphics", "shadow_quality"))
	return [sq, mini(sq, 1), 0][perf_mode()]


## Streaming radius for world chunks in metres (view distance setting)
func view_distance() -> float:
	var vd := clampi(int(get_value("graphics", "view_distance")), 0, 2)
	vd = [vd, mini(vd, 1), 0][perf_mode()]
	return [380.0, 520.0, 760.0][vd] * (0.8 if perf_mode() == 2 else 1.0)


## Effective graphics quality (0 low .. 3 ultra), capped by the performance mode.
func quality() -> int:
	var q := clampi(int(get_value("graphics", "quality")), 0, 3)
	return [q, mini(q, 1), 0][perf_mode()]


## Effective effects level (0 low .. 2 high), capped by the performance mode.
func effects() -> int:
	var fx := clampi(int(get_value("graphics", "effects")), 0, 2)
	return [fx, mini(fx, 1), 0][perf_mode()]
