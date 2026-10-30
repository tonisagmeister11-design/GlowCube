class_name DayNight
extends Node3D
## Tag-Nacht-Zyklus und Wetter: Sonne/Mond (DirectionalLight3D), eigener
## Himmels-Shader mit Wolken und Sternen, Nebel, Belichtung, Glow.
## Setzt die globalen Materialwerte (Nacht, Naesse, beleuchtete Fenster),
## Regen als Partikel um die Kamera.
##
## Zeitquelle: world.get_hour_f() (WorldState) oder die Eigenschaft `hour`,
## wenn keine Welt gesetzt ist. Wetter: "clear" | "cloudy" | "overcast" | "rain";
## mit auto_weather wird es deterministisch aus Seed und Tag bestimmt.
##
## Oeffentlich: setup(world), set_hour(h), set_weather(name, instant := false),
##   get_night() -> float, environment (Environment), sun (DirectionalLight3D)

var world: WorldState
var hour := 12.0
var weather := "clear"
var auto_weather := true
var shadow_quality := 2
var quality := 2
## Nebelverstaerkung bei kurzer Sichtweite (vom Leistungsmanager gesetzt).
var fog_boost := 1.0


## Liest die Stufe aus dem Leistungsmanager und schaltet teure Effekte ab.
func _read_perf() -> void:
	var perf := get_node_or_null("/root/Perf")
	if perf == null:
		return
	quality = int(perf.level)
	shadow_quality = perf.i("shadows")
	if sun != null:
		sun.shadow_enabled = perf.shadows_enabled()
		sun.directional_shadow_max_distance = maxf(40.0, perf.f("shadow_distance"))
		sun.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_4_SPLITS if shadow_quality >= 2 \
			else DirectionalLight3D.SHADOW_PARALLEL_2_SPLITS
		sun.directional_shadow_blend_splits = shadow_quality >= 2
	if environment != null:
		environment.glow_enabled = perf.b("glow")
		environment.ssao_enabled = perf.b("ssao")
		environment.ssr_enabled = perf.b("ssr")
		environment.sdfgi_enabled = false
		# Im Leistungsmodus ersetzt dichterer Nebel die fehlende Sichtweite,
		# damit die Welt am Rand nicht einfach abbricht.
		environment.fog_enabled = true
		var vd: float = perf.view_distance()
		fog_boost = clampf(520.0 / maxf(vd, 120.0), 1.0, 4.5)
		if perf.i("sky_quality") <= 0 and environment.sky != null:
			environment.sky.radiance_size = Sky.RADIANCE_SIZE_32
			environment.sky.process_mode = Sky.PROCESS_MODE_REALTIME

var environment: Environment
var sun: DirectionalLight3D
var moon: DirectionalLight3D
var _sky_mat: ShaderMaterial
var _env_node: WorldEnvironment
var _rain: CPUParticles3D
var _cloud := 0.3
var _wet := 0.0
var _rain_amt := 0.0
var _cloud_target := 0.3
var _rain_target := 0.0
var _cloud_off := Vector2.ZERO
var _last_day := -1
var _accum := 1.0

const WEATHER := {
	"clear": {"cloud": 0.28, "rain": 0.0},
	"cloudy": {"cloud": 0.55, "rain": 0.0},
	"overcast": {"cloud": 0.85, "rain": 0.0},
	"rain": {"cloud": 0.95, "rain": 1.0},
}


func _ready() -> void:
	if environment == null:
		_create()


func setup(w: WorldState) -> void:
	world = w
	if environment == null:
		_create()
	_read_perf()
	var perf := get_node_or_null("/root/Perf")
	if perf != null and not perf.applied.is_connected(_read_perf):
		perf.applied.connect(_read_perf)
	_update(true)


func _create() -> void:
	_env_node = WorldEnvironment.new()
	_env_node.name = "WorldEnvironment"
	environment = Environment.new()
	_sky_mat = ShaderMaterial.new()
	_sky_mat.shader = load("res://assets/shaders/sky.gdshader")
	_sky_mat.set_shader_parameter("noise_tex", load("res://assets/textures/noise.png"))
	var sky := Sky.new()
	sky.sky_material = _sky_mat
	sky.radiance_size = Sky.RADIANCE_SIZE_128
	sky.process_mode = Sky.PROCESS_MODE_INCREMENTAL if not _is_compat() else Sky.PROCESS_MODE_AUTOMATIC
	environment.background_mode = Environment.BG_SKY
	environment.sky = sky
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
	environment.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
	environment.tonemap_mode = Environment.TONE_MAPPER_ACES
	environment.tonemap_white = 6.0
	environment.fog_enabled = true
	environment.fog_mode = Environment.FOG_MODE_EXPONENTIAL
	environment.fog_sky_affect = 0.25
	environment.fog_sun_scatter = 0.25
	environment.glow_enabled = true
	environment.glow_intensity = 0.7
	environment.glow_bloom = 0.04
	environment.glow_hdr_threshold = 1.1
	environment.glow_blend_mode = Environment.GLOW_BLEND_MODE_SOFTLIGHT
	environment.adjustment_enabled = true
	environment.adjustment_saturation = 1.08
	environment.adjustment_contrast = 1.04
	_env_node.environment = environment
	add_child(_env_node)
	sun = DirectionalLight3D.new()
	sun.name = "Sun"
	sun.shadow_enabled = true
	sun.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_4_SPLITS
	sun.directional_shadow_max_distance = 260.0
	sun.directional_shadow_blend_splits = true
	sun.shadow_bias = 0.04
	sun.shadow_normal_bias = 1.2
	sun.light_angular_distance = 0.5
	add_child(sun)
	moon = DirectionalLight3D.new()
	moon.name = "Moon"
	moon.shadow_enabled = false
	moon.light_color = Color(0.55, 0.65, 0.95)
	moon.sky_mode = DirectionalLight3D.SKY_MODE_LIGHT_ONLY
	add_child(moon)
	_rain = CPUParticles3D.new()
	_rain.name = "Rain"
	_rain.amount = 2500
	_rain.lifetime = 1.2
	_rain.emission_shape = CPUParticles3D.EMISSION_SHAPE_BOX
	_rain.emission_box_extents = Vector3(25, 1, 25)
	_rain.direction = Vector3(0.1, -1, 0)
	_rain.spread = 3.0
	_rain.gravity = Vector3(0, -30, 0)
	_rain.initial_velocity_min = 14.0
	_rain.initial_velocity_max = 18.0
	_rain.local_coords = false
	var drop := QuadMesh.new()
	drop.size = Vector2(0.015, 0.5)
	var dm := StandardMaterial3D.new()
	dm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	dm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	dm.albedo_color = Color(0.8, 0.85, 0.9, 0.35)
	dm.billboard_mode = BaseMaterial3D.BILLBOARD_FIXED_Y
	drop.material = dm
	_rain.mesh = drop
	_rain.emitting = false
	add_child(_rain)
	_read_settings()
	if has_node("/root/Settings"):
		get_node("/root/Settings").changed.connect(func(sec, _k, _v): if sec == "graphics": _read_settings())


func _is_compat() -> bool:
	return RenderingServer.get_rendering_device() == null


func _read_settings() -> void:
	if not has_node("/root/Settings"):
		return
	var s := get_node("/root/Settings")
	quality = int(s.get_value("graphics", "quality"))
	shadow_quality = int(s.get_value("graphics", "shadows"))
	if sun:
		sun.shadow_enabled = shadow_quality > 0
		sun.directional_shadow_max_distance = [80.0, 160.0, 260.0, 420.0][clampi(shadow_quality, 0, 3)]
		sun.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_2_SPLITS if shadow_quality <= 1 else DirectionalLight3D.SHADOW_PARALLEL_4_SPLITS
	if environment and not _is_compat():
		environment.ssao_enabled = quality >= 2
		environment.ssao_radius = 1.2
		environment.ssao_intensity = 1.6
		environment.ssr_enabled = quality >= 3
		environment.sdfgi_enabled = false
		environment.glow_enabled = quality >= 1


func set_hour(h: float) -> void:
	hour = fposmod(h, 24.0)
	_update(true)


func set_weather(name: String, instant := false) -> void:
	if not WEATHER.has(name):
		return
	weather = name
	auto_weather = false
	_cloud_target = WEATHER[name].cloud
	_rain_target = WEATHER[name].rain
	if instant:
		_cloud = _cloud_target
		_rain_amt = _rain_target
		_wet = _rain_target
		_update(true)


func get_night() -> float:
	return CityMaterials.get_global("night")


func _process(delta: float) -> void:
	if world:
		hour = world.get_hour_f()
		if auto_weather and world.get_day() != _last_day:
			_last_day = world.get_day()
			_pick_weather(_last_day)
	var k := clampf(delta * 0.05, 0.0, 1.0)
	_cloud = lerpf(_cloud, _cloud_target, k)
	_rain_amt = lerpf(_rain_amt, _rain_target, k * 2.0)
	_wet = clampf(_wet + (delta * 0.02 if _rain_amt > 0.3 else -delta * 0.004), 0.0, 1.0)
	_accum += delta
	if _accum > 0.25:
		_accum = 0.0
		_update(false)
	if _rain:
		var cam := get_viewport().get_camera_3d()
		_rain.emitting = _rain_amt > 0.2
		if cam:
			_rain.global_position = cam.global_position + Vector3(0, 12, 0)


func _pick_weather(day: int) -> void:
	var r := RandomNumberGenerator.new()
	r.seed = hash("weather:%d:%d" % [world.seed if world else 0, day])
	var v := r.randf()
	var w := "clear"
	if v > 0.88:
		w = "rain"
	elif v > 0.75:
		w = "overcast"
	elif v > 0.45:
		w = "cloudy"
	weather = w
	_cloud_target = WEATHER[w].cloud
	_rain_target = WEATHER[w].rain


static func _ss(a: float, b: float, x: float) -> float:
	return smoothstep(a, b, x)


func _update(_force: bool) -> void:
	if environment == null:
		return
	# Sonnenstand: Aufgang 6:00, Untergang 20:00, Mittag 13:00 im Sueden (+z)
	var day_frac := (hour - 6.0) / 14.0
	var el := sin(day_frac * PI) * deg_to_rad(58.0)
	var t := clampf(day_frac, -0.25, 1.25)
	# Osten = +x, Sueden = +z, Westen = -x (Norden = -z)
	var sun_dir := Vector3(cos(PI * t) * cos(el), sin(el), sin(PI * t) * cos(el)).normalized()
	var elev_deg := rad_to_deg(el)
	var night := 1.0 - _ss(-7.0, 3.0, elev_deg)
	var golden := (1.0 - _ss(4.0, 22.0, elev_deg)) * (1.0 - night)
	var cloud := _cloud
	var overcast := _ss(0.5, 0.95, cloud)
	# Sonne
	sun.look_at_from_position(Vector3.ZERO, -sun_dir, Vector3.UP if absf(sun_dir.y) < 0.99 else Vector3.FORWARD)
	var sun_col := Color(1.0, 0.96, 0.9).lerp(Color(1.0, 0.62, 0.34), golden)
	sun.light_color = sun_col
	sun.light_energy = maxf(0.0, (1.45 - golden * 0.35) * (1.0 - night) * (1.0 - overcast * 0.75))
	sun.visible = sun.light_energy > 0.01
	# Mond gegenueber
	var moon_dir := Vector3(-sun_dir.x, absf(sun_dir.y) * 0.8 + 0.25, -sun_dir.z).normalized()
	moon.look_at_from_position(Vector3.ZERO, -moon_dir, Vector3.UP)
	moon.light_energy = 0.06 * night * (1.0 - overcast * 0.6)
	moon.visible = night > 0.05
	moon.shadow_enabled = false
	# Himmel
	var zen_day := Color(0.20, 0.42, 0.80)
	var hor_day := Color(0.68, 0.80, 0.93)
	var zen_gold := Color(0.24, 0.36, 0.62)
	var hor_gold := Color(1.0, 0.66, 0.40)
	var zen_night := Color(0.012, 0.02, 0.05)
	var hor_night := Color(0.05, 0.07, 0.12)
	var zen := zen_day.lerp(zen_gold, golden).lerp(zen_night, night)
	var hor := hor_day.lerp(hor_gold, golden).lerp(hor_night, night)
	var grey := Color(0.55, 0.58, 0.62).lerp(Color(0.04, 0.05, 0.07), night)
	zen = zen.lerp(grey * 0.85, overcast * 0.85)
	hor = hor.lerp(grey, overcast * 0.85)
	_sky_mat.set_shader_parameter("zenith_col", zen)
	_sky_mat.set_shader_parameter("horizon_col", hor)
	_sky_mat.set_shader_parameter("ground_col", hor.darkened(0.45))
	_sky_mat.set_shader_parameter("sun_col", sun_col.lerp(Color(1.0, 0.5, 0.25), golden * 0.3))
	_sky_mat.set_shader_parameter("sun_dir", sun_dir)
	_sky_mat.set_shader_parameter("moon_dir", moon_dir)
	_sky_mat.set_shader_parameter("sun_glow", (0.4 + golden * 0.9) * (1.0 - overcast) * (1.0 - night))
	_sky_mat.set_shader_parameter("sun_disk", (1.0 - overcast) * (1.0 - night))
	_sky_mat.set_shader_parameter("cloud_cover", cloud)
	var cl := Color(1.0, 0.97, 0.93).lerp(Color(1.0, 0.72, 0.52), golden).lerp(Color(0.10, 0.11, 0.14), night)
	var cs := Color(0.58, 0.62, 0.70).lerp(Color(0.48, 0.40, 0.48), golden).lerp(Color(0.03, 0.035, 0.05), night)
	cl = cl.lerp(Color(0.62, 0.64, 0.68).lerp(Color(0.05, 0.05, 0.06), night), overcast * 0.6)
	_sky_mat.set_shader_parameter("cloud_lit", cl)
	_sky_mat.set_shader_parameter("cloud_shade", cs)
	_sky_mat.set_shader_parameter("stars", night)
	_cloud_off += Vector2(0.0008, 0.0003) * (1.0 + overcast)
	_sky_mat.set_shader_parameter("cloud_offset", _cloud_off)
	# Umgebung
	environment.ambient_light_energy = lerpf(1.0, 0.1, night) * lerpf(1.0, 0.85, overcast)
	environment.ambient_light_sky_contribution = 1.0
	environment.fog_light_color = hor.lerp(Color(0.6, 0.62, 0.65), overcast * 0.5)
	environment.fog_density = (lerpf(0.00045, 0.0016, overcast) + _rain_amt * 0.002 + golden * 0.0002) * fog_boost
	environment.tonemap_exposure = lerpf(1.0, 1.25, night) * lerpf(1.0, 1.15, overcast)
	environment.glow_intensity = lerpf(0.45, 0.95, night)
	# Materialwerte
	var lit := 0.08
	var h := hour
	if h >= 17.0 and h < 23.0:
		lit = lerpf(0.25, 0.5, _ss(17.0, 20.0, h))
	elif h >= 23.0 or h < 1.0:
		lit = 0.38
	elif h >= 1.0 and h < 5.0:
		lit = lerpf(0.3, 0.12, _ss(1.0, 4.0, h))
	elif h >= 5.0 and h < 8.0:
		lit = lerpf(0.2, 0.32, _ss(5.0, 6.5, h))
	var shop := 1.0 if (h >= 6.0 and h < 22.5) else 0.35
	CityMaterials.set_globals({"night": night, "wet": _wet, "lit_fraction": lit, "shop_lit": shop,
		"day_seed": float(world.get_day() if world else 0)})
	if _rain:
		_rain.amount = 2500
		_rain.speed_scale = 1.0
		_rain.visible = _rain_amt > 0.2
