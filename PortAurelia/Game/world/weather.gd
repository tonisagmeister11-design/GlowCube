class_name Weather
extends Node
## Weather state machine: sunny, cloudy, rain, heavy_rain, fog, storm.
## Changes every 15-40 in-game minutes... (real: 6-16 min) with smooth transitions.
## Affects: sky / clouds / light (SkyEnvironment), fog density & visibility, road
## wetness (shader global -> puddles & reflections, tyre grip), wind (foliage, rain
## slant), rain particles around the camera, lightning flashes + thunder, audio.

const STATES := {
	#            cloud  overcast fog  rain  wind  wet  grip
	"sunny":      [0.25, 0.0,    0.0, 0.0,  0.2,  0.0, 1.0],
	"cloudy":     [0.7,  0.35,   0.0, 0.0,  0.45, 0.0, 1.0],
	"rain":       [0.8,  0.65,   0.1, 0.5,  0.55, 0.8, 0.85],
	"heavy_rain": [0.95, 0.85,   0.2, 1.0,  0.75, 1.0, 0.75],
	"fog":        [0.6,  0.4,    1.0, 0.0,  0.1,  0.3, 0.95],
	"storm":      [1.0,  1.0,    0.25, 1.0, 1.0,  1.0, 0.7],
}
const NAMES := {"sunny": "Sonnig", "cloudy": "Bewölkt", "rain": "Regen", "heavy_rain": "Starkregen",
	"fog": "Nebel", "storm": "Gewitter"}
# Markov chain of what follows what
const NEXT := {
	"sunny": {"sunny": 4, "cloudy": 3, "fog": 0.5},
	"cloudy": {"sunny": 3, "cloudy": 2, "rain": 2, "fog": 0.6},
	"rain": {"cloudy": 2, "rain": 1, "heavy_rain": 1.5, "storm": 0.6},
	"heavy_rain": {"rain": 2, "storm": 1, "cloudy": 1},
	"fog": {"sunny": 2, "cloudy": 2},
	"storm": {"heavy_rain": 2, "rain": 2},
}

var state := "sunny"
var target := "sunny"
var auto_change := true
var cur := [0.25, 0.0, 0.0, 0.0, 0.2, 0.0, 1.0]
var wetness := 0.0
var grip := 1.0
var _timer := 600.0
var _rain: GPUParticles3D
var _flash_timer := 8.0
var _flash := 0.0
var rng := RandomNumberGenerator.new()


func _ready() -> void:
	name = "Weather"
	GameWorld.instance.weather = self
	rng.randomize()
	if Game.player_data and STATES.has(Game.player_data.weather):
		state = Game.player_data.weather
	target = state
	cur = STATES[state].duplicate()
	wetness = cur[5]
	_timer = rng.randf_range(360.0, 960.0)
	_build_rain()
	_apply(0.0)


func set_weather(s: String, instant := false) -> void:
	if not STATES.has(s):
		return
	target = s
	if instant:
		cur = STATES[s].duplicate()
		wetness = cur[5]
	state = s
	_timer = rng.randf_range(360.0, 960.0)
	Events.weather_changed.emit(s)


func display_name() -> String:
	return NAMES.get(state, state)


func _process(delta: float) -> void:
	if get_tree().paused:
		return
	if auto_change:
		_timer -= delta
		if _timer <= 0.0:
			set_weather(_pick_next())
	# blend towards the target state (about a minute for a full change)
	var goal: Array = STATES[target]
	for i in cur.size():
		cur[i] = move_toward(cur[i], goal[i], delta / 60.0)
	# roads dry slower than they get wet
	wetness = move_toward(wetness, goal[5], delta / (20.0 if goal[5] > wetness else 150.0))
	_apply(delta)


func _pick_next() -> String:
	var opts: Dictionary = NEXT[state]
	var total := 0.0
	for k in opts:
		total += float(opts[k])
	var r := rng.randf() * total
	for k in opts:
		r -= float(opts[k])
		if r <= 0.0:
			return k
	return "sunny"


func _apply(delta: float) -> void:
	var w := GameWorld.instance
	var sky := w.sky
	var rain_amt: float = cur[3]
	if sky:
		sky.cloud_cover = cur[0]
		sky.overcast = cur[1]
		sky.fog_amount = cur[2]
		sky.rain = rain_amt
		sky.apply()
	grip = cur[6]
	ShaderGlobals.set_value("wetness", wetness)
	ShaderGlobals.set_value("wind", cur[4])
	# rain particles follow the camera
	var cam := get_viewport().get_camera_3d()
	if _rain:
		_rain.emitting = rain_amt > 0.05 and not _under_roof(cam)
		_rain.amount_ratio = clampf(rain_amt, 0.05, 1.0)
		if cam:
			_rain.global_position = cam.global_position + Vector3.UP * 8.0 - cam.global_basis.z * 6.0
	# lightning during storms
	if target == "storm" and cur[1] > 0.8:
		_flash_timer -= delta
		if _flash_timer <= 0.0:
			_flash_timer = rng.randf_range(6.0, 22.0)
			_lightning()
	if _flash > 0.0 and sky:
		_flash = maxf(0.0, _flash - delta * 3.0)
		sky.env.ambient_light_energy += _flash * 2.0
		sky.env.background_energy_multiplier += _flash * 1.5


func _lightning() -> void:
	_flash = 1.0
	var cam := get_viewport().get_camera_3d()
	var pos := cam.global_position if cam else Vector3.ZERO
	var dist := rng.randf_range(300.0, 1500.0)
	get_tree().create_timer(dist / 343.0).timeout.connect(func():
		AudioManager.play_ui("thunder", lerpf(0.0, -12.0, dist / 1500.0)))


func _under_roof(cam: Camera3D) -> bool:
	if cam == null:
		return false
	var q := PhysicsRayQueryParameters3D.create(cam.global_position, cam.global_position + Vector3.UP * 40.0)
	q.collision_mask = 1
	return not get_viewport().world_3d.direct_space_state.intersect_ray(q).is_empty()


func _build_rain() -> void:
	_rain = GPUParticles3D.new()
	_rain.name = "Rain"
	_rain.amount = 6000 if Settings.quality() >= 2 else 2500
	_rain.lifetime = 0.9
	_rain.visibility_aabb = AABB(Vector3(-30, -30, -30), Vector3(60, 60, 60))
	_rain.local_coords = false
	var pm := ParticleProcessMaterial.new()
	pm.emission_shape = ParticleProcessMaterial.EMISSION_SHAPE_BOX
	pm.emission_box_extents = Vector3(22, 2, 22)
	pm.direction = Vector3(0.15, -1, 0.05)
	pm.spread = 3.0
	pm.initial_velocity_min = 18.0
	pm.initial_velocity_max = 22.0
	pm.gravity = Vector3(0, -9.8, 0)
	_rain.process_material = pm
	var mesh := QuadMesh.new()
	mesh.size = Vector2(0.015, 0.5)
	var mat := StandardMaterial3D.new()
	mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.albedo_color = Color(0.75, 0.8, 0.88, 0.35)
	mat.billboard_mode = BaseMaterial3D.BILLBOARD_FIXED_Y
	mat.billboard_keep_scale = true
	mesh.material = mat
	_rain.draw_pass_1 = mesh
	_rain.emitting = false
	add_child(_rain)


func serialize() -> String:
	return state
