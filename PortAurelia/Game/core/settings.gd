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
	},
}

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
	var aa: int = get_value("graphics", "anti_aliasing")
	vp.screen_space_aa = Viewport.SCREEN_SPACE_AA_FXAA if aa == 1 else Viewport.SCREEN_SPACE_AA_DISABLED
	vp.use_taa = aa == 2
	vp.msaa_3d = Viewport.MSAA_4X if aa == 3 else Viewport.MSAA_DISABLED
	var q: int = get_value("graphics", "quality")
	vp.mesh_lod_threshold = [4.0, 2.5, 1.5, 1.0][clampi(q, 0, 3)]
	var sq: int = get_value("graphics", "shadow_quality")
	RenderingServer.directional_shadow_atlas_set_size([1024, 2048, 4096, 8192][clampi(sq, 0, 3)], true)
	vp.positional_shadow_atlas_size = [512, 1024, 2048, 4096][clampi(sq, 0, 3)]
	var tq: int = get_value("graphics", "texture_quality")
	vp.anisotropic_filtering_level = [Viewport.ANISOTROPY_2X, Viewport.ANISOTROPY_4X, Viewport.ANISOTROPY_16X][clampi(tq, 0, 2)]
	apply_effects()


## Screen-space effects on the world environment (called again when the world loads).
func apply_effects() -> void:
	if not is_inside_tree():
		return
	var w = get_tree().get_first_node_in_group("world_environment")
	if w == null or not (w is WorldEnvironment):
		return
	var env: Environment = (w as WorldEnvironment).environment
	var fx: int = get_value("graphics", "effects")
	var q: int = get_value("graphics", "quality")
	env.ssao_enabled = fx >= 1
	env.ssr_enabled = fx >= 2 and q >= 2
	env.ssil_enabled = fx >= 2 and q >= 3
	env.glow_enabled = true
	env.volumetric_fog_enabled = fx >= 2 and q >= 3


## Streaming radius for world chunks in metres (view distance setting)
func view_distance() -> float:
	return [380.0, 520.0, 760.0][clampi(int(get_value("graphics", "view_distance")), 0, 2)]


func quality() -> int:
	return int(get_value("graphics", "quality"))


func effects() -> int:
	return int(get_value("graphics", "effects"))
