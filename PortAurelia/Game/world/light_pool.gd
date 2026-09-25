class_name LightPool
extends Node3D
## At night the nearest street lamps around the camera get real spot lights from a
## fixed-size pool (distant lamps only show their emissive heads). Keeps the number
## of dynamic lights constant regardless of how many lamps are loaded.

var streaming: StreamingManager
var pool_size := 28
var _lights: Array[SpotLight3D] = []
var _timer := 0.0
var active := false


func setup(s: StreamingManager) -> void:
	streaming = s
	pool_size = [12, 20, 28, 40][clampi(Settings.quality(), 0, 3)] if Settings else 28
	for i in pool_size:
		var l := SpotLight3D.new()
		l.light_color = Color(1.0, 0.82, 0.58)
		l.light_energy = 6.0
		l.spot_range = 20.0
		l.spot_angle = 58.0
		l.spot_attenuation = 0.9
		l.spot_angle_attenuation = 1.4
		l.shadow_enabled = false
		l.light_specular = 0.4
		l.distance_fade_enabled = true
		l.distance_fade_begin = 90.0
		l.distance_fade_length = 30.0
		l.rotation_degrees = Vector3(-90, 0, 0)
		l.visible = false
		add_child(l)
		_lights.append(l)


func _process(delta: float) -> void:
	if streaming == null:
		return
	_timer -= delta
	if _timer > 0.0:
		return
	_timer = 0.3
	var lights_on: float = ShaderGlobals.get_value("city_lights")
	active = lights_on > 0.3
	if not active:
		for l in _lights:
			l.visible = false
		return
	var cam := get_viewport().get_camera_3d()
	if cam == null:
		return
	var cp := cam.global_position
	var fwd := -cam.global_basis.z
	var cands := []
	for c in streaming.lamp_lights:
		var cc := streaming.world.chunk_center(c)
		if Vector2(cc.x - cp.x, cc.z - cp.z).length() > 260.0:
			continue
		for p in streaming.lamp_lights[c]:
			var d: float = p.distance_squared_to(cp)
			if d > 120.0 * 120.0:
				continue
			# favour lamps in front of the camera
			var dir: Vector3 = (p - cp)
			var facing := dir.normalized().dot(fwd)
			cands.append([d * (1.6 - facing * 0.6), p])
	cands.sort_custom(func(a, b): return a[0] < b[0])
	for i in _lights.size():
		var l := _lights[i]
		if i < cands.size():
			l.global_position = cands[i][1] + Vector3(0, -0.15, 0)
			l.light_energy = 6.0 * lights_on
			l.visible = true
		else:
			l.visible = false
