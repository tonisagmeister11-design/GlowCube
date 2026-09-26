class_name SkyEnvironment
extends Node3D
## Sun, moon, procedural sky, fog and post-processing.
## Driven by DayNightCycle (hour) and WeatherSystem (clouds, rain, fog).

const LATITUDE_MAX_ELEVATION := 64.0

var world_env: WorldEnvironment
var env: Environment
var sun: DirectionalLight3D
var moon: DirectionalLight3D
var sky_mat: ShaderMaterial

var hour := 12.0
var cloud_cover := 0.3
var overcast := 0.0      # 0..1 heavy clouds darken everything
var fog_amount := 0.0    # 0..1 weather fog
var rain := 0.0

var _sun_dir := Vector3.UP


func _ready() -> void:
	world_env = WorldEnvironment.new()
	world_env.add_to_group("world_environment")
	add_to_group("sky_environment")
	env = Environment.new()
	var sky := Sky.new()
	sky_mat = ShaderMaterial.new()
	sky_mat.shader = load("res://assets/shaders/sky.gdshader")
	sky_mat.set_shader_parameter("cloud_tex", load("res://assets/textures/cloud_noise.png"))
	sky.sky_material = sky_mat
	sky.radiance_size = Sky.RADIANCE_SIZE_256
	sky.process_mode = Sky.PROCESS_MODE_REALTIME
	env.background_mode = Environment.BG_SKY
	env.sky = sky
	env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
	env.ambient_light_sky_contribution = 0.55
	env.ambient_light_color = Color(0.66, 0.63, 0.6)
	env.ambient_light_energy = 1.0
	env.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	env.tonemap_exposure = 1.0
	env.tonemap_white = 6.0
	env.glow_enabled = true
	env.glow_intensity = 0.55
	env.glow_strength = 0.9
	env.glow_bloom = 0.04
	env.glow_hdr_threshold = 1.1
	env.glow_blend_mode = Environment.GLOW_BLEND_MODE_SOFTLIGHT
	env.ssao_enabled = true
	env.ssao_radius = 1.4
	env.ssao_intensity = 1.6
	env.ssao_power = 1.4
	env.fog_enabled = true
	env.fog_mode = Environment.FOG_MODE_EXPONENTIAL
	env.fog_density = 0.0006
	env.fog_sky_affect = 0.25
	env.fog_aerial_perspective = 0.55
	env.fog_sun_scatter = 0.25
	env.adjustment_enabled = true
	env.adjustment_saturation = 1.12
	env.adjustment_contrast = 1.06
	env.adjustment_brightness = 1.0
	world_env.environment = env
	add_child(world_env)
	Settings.apply_effects()

	sun = DirectionalLight3D.new()
	sun.name = "Sun"
	sun.shadow_enabled = true
	apply_quality(Settings.quality() if Settings else 2)
	sun.directional_shadow_split_1 = 0.06
	sun.directional_shadow_split_2 = 0.18
	sun.directional_shadow_split_3 = 0.45
	sun.directional_shadow_blend_splits = true
	sun.directional_shadow_fade_start = 0.85
	sun.shadow_bias = 0.04
	sun.shadow_normal_bias = 1.2
	sun.light_angular_distance = 0.6
	add_child(sun)

	moon = DirectionalLight3D.new()
	moon.name = "Moon"
	moon.shadow_enabled = false
	moon.light_color = Color(0.55, 0.64, 0.85)
	moon.light_energy = 0.0
	add_child(moon)
	apply()


## Shadow range / cascades per graphics quality (0 low .. 3 ultra).
func apply_quality(q: int) -> void:
	if sun == null:
		return
	q = clampi(q, 0, 3)
	sun.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_2_SPLITS if q <= 1 else DirectionalLight3D.SHADOW_PARALLEL_4_SPLITS
	sun.directional_shadow_max_distance = [110.0, 180.0, 280.0, 420.0][q]
	sun.shadow_enabled = Settings.shadow_quality() > 0 if Settings else true
	# performance modes: smaller sky reflection map, updated over several frames
	var perf: int = Settings.perf_mode() if Settings else 0
	if env and env.sky:
		env.sky.radiance_size = [Sky.RADIANCE_SIZE_256, Sky.RADIANCE_SIZE_128, Sky.RADIANCE_SIZE_64][perf]
		env.sky.process_mode = Sky.PROCESS_MODE_REALTIME if perf == 0 else Sky.PROCESS_MODE_INCREMENTAL


func set_time(h: float) -> void:
	hour = fposmod(h, 24.0)
	apply()


func sun_direction() -> Vector3:
	return _sun_dir


func apply() -> void:
	# sun path: rises in the east (+X) at 06:00, south (+Z) at noon, sets west at 18:00
	var t := (hour - 6.0) / 12.0
	var phi := t * PI
	var el := sin(t * PI) * deg_to_rad(LATITUDE_MAX_ELEVATION)
	if t < 0.0 or t > 1.0:
		el = -abs(sin(t * PI)) * deg_to_rad(40.0)
	_sun_dir = Vector3(cos(el) * cos(phi), sin(el), cos(el) * sin(phi)).normalized()
	var sun_el_deg := rad_to_deg(el)

	var day := clampf(smoothstep(-6.0, 8.0, sun_el_deg), 0.0, 1.0)
	var golden := clampf(1.0 - absf(sun_el_deg - 2.0) / 14.0, 0.0, 1.0) * day
	var night := 1.0 - day

	# sun light
	var dir := _sun_dir if _sun_dir.y > -0.05 else Vector3(_sun_dir.x, 0.05, _sun_dir.z).normalized()
	sun.look_at_from_position(Vector3.ZERO, -dir, Vector3.UP if absf(dir.y) < 0.99 else Vector3.FORWARD)
	var sun_col := Color(1.0, 0.96, 0.9).lerp(Color(1.0, 0.58, 0.32), golden)
	sun.light_color = sun_col
	var cloud_dim := 1.0 - overcast * 0.75
	sun.light_energy = lerpf(0.0, 1.35, smoothstep(-3.0, 10.0, sun_el_deg)) * cloud_dim
	sun.visible = sun.light_energy > 0.01
	sun.shadow_opacity = clampf(1.0 - overcast * 0.8, 0.15, 1.0)

	# moon opposite the sun
	var md := Vector3(-_sun_dir.x, absf(_sun_dir.y) * 0.8 + 0.3, -_sun_dir.z).normalized()
	moon.look_at_from_position(Vector3.ZERO, -md, Vector3.UP)
	moon.light_energy = 0.16 * night * (1.0 - overcast * 0.6)
	moon.visible = moon.light_energy > 0.005

	# sky shader
	sky_mat.set_shader_parameter("day", day)
	sky_mat.set_shader_parameter("sunset", golden)
	sky_mat.set_shader_parameter("cloud_cover", clampf(cloud_cover + overcast * 0.6, 0.0, 1.0))
	sky_mat.set_shader_parameter("cloud_dark", overcast)
	sky_mat.set_shader_parameter("fog_haze", fog_amount)
	sky_mat.set_shader_parameter("moon_dir", md)
	var zen_day := Color(0.16, 0.38, 0.78).lerp(Color(0.42, 0.46, 0.52), overcast)
	var hor_day := Color(0.62, 0.78, 0.94).lerp(Color(0.62, 0.65, 0.68), overcast)
	sky_mat.set_shader_parameter("zenith_day", zen_day)
	sky_mat.set_shader_parameter("horizon_day", hor_day.lerp(Color(0.8, 0.8, 0.8), fog_amount))

	# ambient + fog
	env.ambient_light_energy = lerpf(0.25, 1.0, day) * (1.0 - overcast * 0.25)
	env.background_energy_multiplier = lerpf(0.6, 1.0, day)
	var fog_col := hor_day.lerp(Color(1.0, 0.7, 0.5), golden * 0.5)
	fog_col = fog_col.lerp(Color(0.05, 0.06, 0.1), night)
	env.fog_light_color = fog_col
	env.fog_light_energy = lerpf(0.4, 1.0, day)
	env.fog_density = lerpf(0.00045, 0.0009, overcast) + fog_amount * 0.012 + rain * 0.0015
	env.fog_sun_scatter = 0.25 * day * (1.0 - overcast)
	env.tonemap_exposure = lerpf(1.25, 1.0, day)
	env.glow_intensity = lerpf(0.9, 0.5, day)

	# shared shader globals
	ShaderGlobals.set_value("night", night)
	ShaderGlobals.set_value("sun_dir", _sun_dir)
	var lights := clampf(1.0 - smoothstep(-2.0, 6.0, sun_el_deg), 0.0, 1.0)
	lights = maxf(lights, overcast * 0.6 * float(hour > 16.0 or hour < 8.0))
	ShaderGlobals.set_value("city_lights", lights)


func is_night() -> bool:
	return _sun_dir.y < 0.05


func city_lights_on() -> bool:
	return float(ShaderGlobals.get_value("city_lights")) > 0.5
