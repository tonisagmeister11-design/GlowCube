class_name Vehicle
extends RigidBody3D
## Drivable vehicle with raycast wheel physics, damage model, lights and sound.
##
## Inputs (throttle, brake, steer, handbrake) come from the player or an AI driver.
## Physics per wheel: spring/damper suspension, longitudinal drive/brake force and
## lateral tyre force limited by a friction circle; plus drag, downforce and a
## lowered centre of mass. Far away vehicles can be switched to kinematic mode by
## the traffic system (no physics cost).

signal destroyed_signal(v: Vehicle)

const GLB_DIR := "res://assets/generated/vehicles/"

var type_id := "sedan"
var def := {}
var meta := {}
var paint := Color(0.6, 0.05, 0.05)

# driver input
var throttle := 0.0
var brake_input := 0.0
var steer_input := 0.0
var handbrake := false
var horn := false

# state
var driver: Node3D = null
var ai_driver: Node = null
var owner_npc: Node = null
var player_owned := false
var is_parked := false
var is_police := false
var siren_on := false
var headlights_on := false
var speed_kmh := 0.0
var rpm := 900.0
var gear := 1
var camera_distance := 6.0
var destroyed := false
var engine_health := 1000.0
var body_health := 1000.0
var kinematic_mode := false
var in_water := false

var visual: Node3D
var body_mesh: MeshInstance3D
var _paint_meshes: Array[MeshInstance3D] = []
var _glass: MeshInstance3D
var _lights := {}
var _siren_mesh: MeshInstance3D
var _bumpers := {}
var _wheels: Array = []         # Array[Dictionary]
var _steer := 0.0
var _orig_arrays := []
var _deform_cooldown := 0.0
var _smoke: CPUParticles3D
var _fire: Node3D
var _burn_timer := -1.0
var _engine_audio: AudioStreamPlayer3D
var _skid_audio: AudioStreamPlayer3D
var _siren_audio: AudioStreamPlayer3D
var _horn_audio: AudioStreamPlayer3D
var _head_spots: Array[SpotLight3D] = []
var _siren_lights: Array[OmniLight3D] = []
var _blink := 0.0
var turn_signal := 0            # -1 left, 1 right
var _skid_amount := 0.0
var _smoke_timer := 0.0
var _glass_broken := false
var _last_collision_time := 0.0
var _flat := [false, false, false, false]


static func create(id: String, color := Color(-1, 0, 0)) -> Vehicle:
	var v := Vehicle.new()
	v.type_id = id
	if color.r >= 0.0:
		v.paint = color
	else:
		v.paint = VehicleDefs.random_color()
	return v


func _ready() -> void:
	add_to_group("vehicles")
	def = VehicleDefs.get_def(type_id)
	meta = VehicleDefs.meta(type_id)
	is_police = type_id == "police"
	camera_distance = float(def.get("cam", 6.0))
	mass = float(def["mass"])
	collision_layer = 1 << 2
	# characters are not solid for vehicles: impacts are resolved in _check_pedestrian_hits
	collision_mask = 1 | (1 << 2) | (1 << 4) | (1 << 5)
	contact_monitor = true
	max_contacts_reported = 4
	continuous_cd = true
	can_sleep = true
	linear_damp = 0.05
	angular_damp = 0.8
	var pm := PhysicsMaterial.new()
	pm.friction = 0.4
	pm.bounce = 0.05
	physics_material_override = pm
	_build_visual()
	_build_collision()
	_build_wheels()
	_build_audio()
	center_of_mass_mode = RigidBody3D.CENTER_OF_MASS_MODE_CUSTOM
	center_of_mass = Vector3(0, float(meta.get("ground", 0.2)) + float(meta.get("height", 1.4)) * 0.18, 0)
	body_entered.connect(_on_body_entered)


func _build_visual() -> void:
	var path := GLB_DIR + type_id + ".glb"
	if not ResourceLoader.exists(path):
		return
	var s: PackedScene = load(path)
	visual = s.instantiate()
	visual.name = "Visual"
	add_child(visual)
	for mi in visual.find_children("*", "MeshInstance3D", true, false):
		var n := String(mi.name)
		if n == "Body":
			body_mesh = mi
			_paint_meshes.append(mi)
			# own a unique copy of the mesh for deformation
			mi.mesh = mi.mesh.duplicate()
		elif n == "Detail" or n.begins_with("Bumper"):
			_paint_meshes.append(mi)
		if n == "Glass":
			_glass = mi
		elif n.begins_with("Light"):
			_lights[n] = mi
			var col := Color(1.0, 0.95, 0.85)
			match n:
				"LightBrake":
					col = Color(1.0, 0.06, 0.03)
				"LightTurnL", "LightTurnR":
					col = Color(1.0, 0.55, 0.05)
			mi.set_instance_shader_parameter("lamp_color", col)
			mi.set_instance_shader_parameter("intensity", 0.0)
		elif n == "Siren":
			_siren_mesh = mi
		elif n.begins_with("Bumper"):
			_bumpers[n] = {"node": mi, "damage": 0.0, "attached": true}
	for mi in _paint_meshes:
		mi.set_instance_shader_parameter("paint", paint)
		mi.set_instance_shader_parameter("dirt", randf_range(0.0, 0.35))


func _build_collision() -> void:
	var L := float(meta.get("length", 4.5))
	var W := float(meta.get("width", 1.8))
	var H := float(meta.get("height", 1.4))
	var gc := float(meta.get("ground", 0.2))
	var hood := float(meta.get("hood", H * 0.6))
	var lower := CollisionShape3D.new()
	var b := BoxShape3D.new()
	var low_top := hood if def.get("bike", false) == false else 0.95
	b.size = Vector3(W * 0.96, maxf(low_top - gc - 0.05, 0.3), L * 0.97)
	lower.shape = b
	lower.position = Vector3(0, gc + 0.05 + b.size.y * 0.5, 0)
	add_child(lower)
	if H > hood + 0.15 and not def.get("bike", false):
		var upper := CollisionShape3D.new()
		var u := BoxShape3D.new()
		u.size = Vector3(W * 0.8, H - hood, L * 0.55)
		upper.shape = u
		upper.position = Vector3(0, hood + u.size.y * 0.5, L * 0.02)
		add_child(upper)


func _build_wheels() -> void:
	var travel := float(def.get("travel", 0.2))
	var R := float(meta.get("wheel_radius", 0.33))
	var wm: Dictionary = meta.get("wheels", {})
	var names := wm.keys()
	names.sort()
	for n in names:
		var p: Array = wm[n]
		var node: Node3D = visual.find_child(n, true, false) if visual else null
		var front := String(n).contains("F")
		var left := String(n).ends_with("L")
		var pos := Vector3(p[0], p[1], p[2])
		var w := {"name": n, "node": node, "pos": pos, "mount": pos + Vector3(0, travel, 0), "front": front,
			"left": left, "radius": R, "travel": travel, "compress": 0.0, "last_compress": 0.0, "contact": false,
			"spin": 0.0, "load": 0.0, "slip": 0.0, "ground_pos": Vector3.ZERO, "surface": "road", "rest_y": p[1]}
		_wheels.append(w)
	if def.get("bike", false):
		# hidden outrigger contacts keep the motorcycle upright; visuals stay two-wheeled
		for side in [-0.32, 0.32]:
			for z in [-0.45, 0.45]:
				_wheels.append({"name": "aux", "node": null, "pos": Vector3(side, R, z), "mount": Vector3(side, R + travel, z),
					"front": z < 0, "left": side < 0, "radius": R, "travel": travel, "compress": 0.0, "last_compress": 0.0,
					"contact": false, "spin": 0.0, "load": 0.0, "slip": 0.0, "ground_pos": Vector3.ZERO, "surface": "road",
					"rest_y": R, "aux": true})


func _build_audio() -> void:
	_engine_audio = AudioStreamPlayer3D.new()
	_engine_audio.stream = AudioManager.get_stream(String(def.get("sound", "engine_loop")))
	_engine_audio.bus = "Vehicles"
	_engine_audio.unit_size = 6.0
	_engine_audio.max_distance = 90.0
	_engine_audio.volume_db = -8.0
	add_child(_engine_audio)
	_skid_audio = AudioStreamPlayer3D.new()
	_skid_audio.stream = AudioManager.get_stream("tire_screech")
	_skid_audio.bus = "Vehicles"
	_skid_audio.max_distance = 80.0
	add_child(_skid_audio)
	if def.get("siren", false):
		_siren_audio = AudioStreamPlayer3D.new()
		_siren_audio.stream = AudioManager.get_stream("siren_wail")
		_siren_audio.bus = "Vehicles"
		_siren_audio.unit_size = 20.0
		_siren_audio.max_distance = 350.0
		add_child(_siren_audio)
	_horn_audio = AudioStreamPlayer3D.new()
	_horn_audio.stream = AudioManager.get_stream("horn")
	_horn_audio.bus = "Vehicles"
	_horn_audio.max_distance = 120.0
	add_child(_horn_audio)


# ------------------------------------------------------------------ driving
func _physics_process(delta: float) -> void:
	if kinematic_mode:
		return
	if driver is Player and (driver as Player).input_enabled and not destroyed:
		_player_input(delta)
	elif driver == null and ai_driver == null:
		throttle = 0.0
		steer_input = 0.0
		brake_input = 0.0 if linear_velocity.length() < 0.5 else 0.4
		handbrake = linear_velocity.length() < 2.0
	if linear_velocity.length() > 2.5:
		_check_pedestrian_hits()
	_update_effects(delta)


var _ped_query: PhysicsShapeQueryParameters3D
var _recent_hits := {}


## Characters (player layer 2, NPC layer 4) touched by the moving body get knocked over.
func _check_pedestrian_hits() -> void:
	if _ped_query == null:
		_ped_query = PhysicsShapeQueryParameters3D.new()
		var b := BoxShape3D.new()
		b.size = Vector3(float(meta.get("width", 1.8)) + 0.2, float(meta.get("height", 1.4)) * 0.8,
			float(meta.get("length", 4.5)) + 0.3)
		_ped_query.shape = b
		_ped_query.collision_mask = (1 << 1) | (1 << 3)
	_ped_query.transform = global_transform * Transform3D(Basis.IDENTITY, Vector3(0, float(meta.get("height", 1.4)) * 0.45, 0))
	var hits := get_world_3d().direct_space_state.intersect_shape(_ped_query, 6)
	var now := Time.get_ticks_msec()
	for h in hits:
		var c: Object = h["collider"]
		if c == driver or not c.has_method("on_vehicle_impact"):
			continue
		if _recent_hits.get(c.get_instance_id(), 0) > now:
			continue
		_recent_hits[c.get_instance_id()] = now + 1500
		var rel := linear_velocity.length()
		c.call("on_vehicle_impact", self, rel)
		linear_velocity *= 0.94
		if rel > 6.0:
			AudioManager.play_3d("punch_hit", (c as Node3D).global_position + Vector3.UP, 2.0)


func _player_input(delta: float) -> void:
	var fwd := Input.get_action_strength("accelerate")
	var back := Input.get_action_strength("brake")
	var v_long := -linear_velocity.dot(global_basis.z)
	if v_long < 1.0 and back > 0.1 and fwd < 0.1:
		throttle = -back * 0.55   # reverse
		brake_input = 0.0
	else:
		throttle = fwd
		brake_input = back if v_long > 0.5 else 0.0
	steer_input = Input.get_axis("steer_right", "steer_left")
	handbrake = Input.is_action_pressed("handbrake")
	if Input.is_action_just_pressed("horn"):
		_horn_audio.play()
	if Input.is_action_just_pressed("headlights"):
		headlights_on = not headlights_on
	if Input.is_action_just_pressed("siren") and def.get("siren", false):
		set_siren(not siren_on)


func _integrate_forces(state: PhysicsDirectBodyState3D) -> void:
	if kinematic_mode:
		return
	var dt := state.step
	var xf := state.transform
	var up := xf.basis.y
	var fwd := -xf.basis.z
	var v := state.linear_velocity
	var v_long_body := v.dot(fwd)
	speed_kmh = v_long_body * 3.6
	var spd := v.length()
	# steering (reduced at speed)
	var max_steer := deg_to_rad(float(def["steer"])) * lerpf(1.0, 0.28, clampf(spd / 45.0, 0.0, 1.0))
	_steer = move_toward(_steer, steer_input * max_steer, dt * 2.6)
	var space := state.get_space_state()
	var grip := float(def["grip"]) * (0.78 if ShaderGlobals.get_value("wetness", 0.0) > 0.4 else 1.0)
	var driven := []
	var n_contact := 0
	for w in _wheels:
		var mount_w: Vector3 = xf * (w["mount"] as Vector3)
		var R: float = w["radius"]
		var travel: float = w["travel"]
		var q := PhysicsRayQueryParameters3D.create(mount_w, mount_w - up * (travel + R + 0.05))
		q.exclude = [get_rid()]
		q.collision_mask = 1 | (1 << 2)
		var hit := space.intersect_ray(q)
		w["last_compress"] = w["compress"]
		if hit.is_empty():
			w["contact"] = false
			w["compress"] = 0.0
			w["load"] = 0.0
			continue
		n_contact += 1
		w["contact"] = true
		var dist: float = mount_w.distance_to(hit["position"])
		var susp := dist - R
		var compress := clampf(travel - susp, 0.0, travel + 0.05)
		w["compress"] = compress
		var comp_vel: float = (compress - float(w["last_compress"])) / dt
		var k := float(def["spring"]) * (0.5 if w.get("aux", false) else 1.0)
		var c := float(def["damp"]) * (0.5 if w.get("aux", false) else 1.0)
		var f_s := maxf(0.0, compress * k + comp_vel * c)
		var contact: Vector3 = hit["position"]
		w["ground_pos"] = contact
		var col: Object = hit["collider"]
		w["surface"] = String(col.get_meta("surface")) if col and col.has_meta("surface") else "road"
		var normal: Vector3 = hit["normal"]
		state.apply_force(normal * f_s, contact - xf.origin)
		w["load"] = f_s
		if w.get("aux", false) and not def.get("bike", false):
			continue
		# tyre frame
		var wf := fwd
		if w["front"]:
			wf = fwd.rotated(up, _steer)
		wf = (wf - normal * wf.dot(normal)).normalized()
		var wr := wf.cross(normal).normalized()
		var r := contact - xf.origin
		var pv := v + state.angular_velocity.cross(r)
		var v_long := pv.dot(wf)
		var v_lat := pv.dot(wr)
		var surf_grip := 1.0
		match String(w["surface"]):
			"grass", "sand", "dirt":
				surf_grip = 0.65
			"concrete", "sidewalk":
				surf_grip = 0.95
		var flat_i := _wheels.find(w)
		if flat_i < 4 and _flat[flat_i]:
			surf_grip *= 0.55
		var mu := grip * surf_grip * f_s * 1.05
		var lat_grip := 1.0
		if handbrake and not w["front"]:
			lat_grip = 0.32
		var f_lat := -v_lat * mass * 0.25 / dt / float(_wheels.size()) * 0.35
		f_lat = clampf(f_lat, -mu * lat_grip, mu * lat_grip)
		var f_long := 0.0
		var drive_mode := String(def["drive"])
		var is_driven: bool = drive_mode == "awd" or (drive_mode == "fwd" and w["front"]) or (drive_mode == "rwd" and not w["front"])
		if is_driven and not destroyed and not in_water:
			var n_driven := 4 if drive_mode == "awd" else 2
			var power := float(def["power"]) * (0.45 if engine_health < 150.0 else 1.0)
			var top := float(def["top"])
			var eng := 0.0
			if throttle > 0.0:
				eng = minf(mass * 6.5, power / maxf(absf(v_long_body), 4.0)) * throttle
				eng *= clampf(1.0 - v_long_body / top, 0.0, 1.0) * 1.2
			elif throttle < 0.0:
				eng = throttle * mass * 3.5 * clampf(1.0 + v_long_body / 8.0, 0.0, 1.0)
			f_long += eng / n_driven
		if brake_input > 0.0:
			f_long -= signf(v_long) * float(def["brake"]) * brake_input / 4.0 * clampf(absf(v_long) * 2.0, 0.0, 1.0)
		if handbrake and not w["front"]:
			f_long -= signf(v_long) * float(def["brake"]) * 0.35 * clampf(absf(v_long), 0.0, 1.0)
		f_long -= v_long * 12.0  # rolling resistance
		# friction circle
		var total := Vector2(f_long, f_lat)
		var slip := 0.0
		if total.length() > mu:
			slip = total.length() / maxf(mu, 1.0) - 1.0
			total = total.normalized() * mu
		w["slip"] = maxf(slip, absf(v_lat) / 8.0 if absf(v_lat) > 2.5 else 0.0)
		state.apply_force(wf * total.x + wr * total.y, contact - xf.origin)
		w["spin"] = float(w["spin"]) + v_long / R * dt
	# aerodynamic drag and downforce
	var drag := -v * spd * 0.42
	state.apply_central_force(drag)
	var df := float(def.get("downforce", 0.4))
	state.apply_central_force(-up * spd * spd * df)
	# keep bikes upright
	if def.get("bike", false):
		var tilt := up.cross(Vector3.UP)
		state.apply_torque(tilt * mass * 40.0 - state.angular_velocity * Vector3(1, 0, 1) * mass * 4.0)
	# collision damage from contact impulses
	if state.get_contact_count() > 0:
		var total_imp := 0.0
		var imp_pos := Vector3.ZERO
		for i in state.get_contact_count():
			var imp := state.get_contact_impulse(i).length()
			if imp > total_imp:
				total_imp = imp
				imp_pos = state.get_contact_local_position(i)
		if total_imp > mass * 2.5:
			call_deferred("_collision_damage", total_imp, imp_pos, state.get_contact_collider_object(0))
	# gearbox (for sound)
	var ratios := [0.0, 11.0, 7.5, 5.4, 4.2, 3.4, 2.9]
	var wheel_rpm := absf(v_long_body) / 0.33 * 60.0 / TAU
	rpm = clampf(wheel_rpm * ratios[gear], 850.0, 7200.0)
	if rpm > 6200.0 and gear < 6:
		gear += 1
	elif rpm < 2600.0 and gear > 1:
		gear -= 1
	if n_contact == 0 and throttle != 0.0:
		rpm = lerpf(rpm, 7000.0 * absf(throttle), 0.1)


func _process(delta: float) -> void:
	# wheel visuals
	for w in _wheels:
		var node: Node3D = w["node"]
		if node == null:
			continue
		var rest_y: float = w["rest_y"]
		var y := rest_y
		if w["contact"]:
			y = rest_y + float(w["compress"])
		var p: Vector3 = w["pos"]
		node.position = Vector3(p.x, y, p.z)
		var steer := _steer if w["front"] else 0.0
		node.rotation = Vector3(-float(w["spin"]), steer, 0.0)
	if def.get("bike", false) and visual:
		visual.rotation.z = lerp_angle(visual.rotation.z, -_steer * clampf(absf(speed_kmh) / 40.0, 0.0, 1.0) * 1.2, delta * 5.0)


func _update_effects(delta: float) -> void:
	# lights
	var night: float = ShaderGlobals.get_value("city_lights", 0.0)
	var occupied := driver != null or ai_driver != null
	var head_on := (headlights_on or night > 0.4) and occupied and not destroyed
	_set_light("LightHead", 1.0 if head_on else 0.0)
	var braking := (brake_input > 0.1 or (throttle < 0.0 and speed_kmh > 1.0)) and occupied
	_set_light("LightBrake", 1.0 if braking else (0.35 if head_on else 0.0))
	_set_light("LightReverse", 1.0 if speed_kmh < -1.0 and occupied else 0.0)
	_blink += delta
	var blink_on := fmod(_blink, 0.8) < 0.4
	_set_light("LightTurnL", 1.0 if turn_signal == -1 and blink_on else 0.0)
	_set_light("LightTurnR", 1.0 if turn_signal == 1 and blink_on else 0.0)
	_update_headlight_spots(head_on)
	# engine audio
	if occupied and not destroyed:
		if not _engine_audio.playing:
			_engine_audio.play(randf())
		_engine_audio.pitch_scale = float(def.get("pitch", 1.0)) * (0.55 + rpm / 7000.0 * 1.35)
		_engine_audio.volume_db = lerpf(-12.0, -2.0, clampf(absf(throttle), 0.0, 1.0)) + (4.0 if driver is Player else 0.0)
	elif _engine_audio.playing:
		_engine_audio.stop()
	# tyre skid
	var slip := 0.0
	for w in _wheels:
		if w["contact"] and not w.get("aux", false):
			slip = maxf(slip, float(w["slip"]))
	_skid_amount = lerpf(_skid_amount, clampf(slip, 0.0, 1.0), delta * 8.0)
	if _skid_amount > 0.15 and absf(speed_kmh) > 8.0:
		if not _skid_audio.playing:
			_skid_audio.play()
		_skid_audio.volume_db = linear_to_db(_skid_amount) - 2.0
		_smoke_timer -= delta
		if _smoke_timer <= 0.0 and _skid_amount > 0.4:
			_smoke_timer = 0.12
			for w in _wheels:
				if not w["front"] and w["contact"] and not w.get("aux", false):
					VFX.burst(w["ground_pos"], Vector3.UP, "tire_smoke")
	elif _skid_audio.playing:
		_skid_audio.stop()
	# siren flashing lights
	if _siren_mesh:
		_siren_mesh.set_instance_shader_parameter("active", 1.0 if siren_on else 0.0)
	for i in _siren_lights.size():
		var l := _siren_lights[i]
		var phase := fmod(Time.get_ticks_msec() / 400.0, 1.0)
		l.visible = siren_on and ((phase < 0.5) == (i == 0))
	# water
	var sea := GameWorld.instance.sea_level() if GameWorld.instance else -1.5
	var was := in_water
	in_water = global_position.y < sea - 0.6
	if in_water and not was:
		VFX.burst(global_position, Vector3.UP, "splash")
		AudioManager.play_3d("splash", global_position, 2.0)
		engine_health = minf(engine_health, 100.0)
	if in_water:
		linear_velocity *= 0.98
	# burning
	if _burn_timer >= 0.0:
		_burn_timer -= delta
		if _burn_timer <= 0.0 and not destroyed:
			explode()


func _set_light(n: String, v: float) -> void:
	var mi: MeshInstance3D = _lights.get(n)
	if mi:
		mi.set_instance_shader_parameter("intensity", v)


func _update_headlight_spots(on: bool) -> void:
	var want := on and (driver is Player or is_police or _near_player(60.0))
	if want and _head_spots.is_empty():
		var L := float(meta.get("length", 4.5))
		var W := float(meta.get("width", 1.8))
		for sx in [-1.0, 1.0]:
			var s := SpotLight3D.new()
			s.light_color = Color(1.0, 0.95, 0.85)
			s.light_energy = 4.0 if driver is Player else 2.5
			s.spot_range = 40.0 if driver is Player else 24.0
			s.spot_angle = 32.0
			s.shadow_enabled = false
			s.position = Vector3(sx * W * 0.34, float(meta.get("ground", 0.2)) + 0.55, -L * 0.5 - 0.05)
			s.rotation_degrees = Vector3(-6, 0, 0)
			add_child(s)
			_head_spots.append(s)
	elif not want and not _head_spots.is_empty():
		for s in _head_spots:
			s.queue_free()
		_head_spots.clear()


func _near_player(d: float) -> bool:
	var w := GameWorld.instance
	return w != null and w.player != null and w.player.global_position.distance_to(global_position) < d


func set_siren(on: bool) -> void:
	siren_on = on
	if _siren_audio:
		if on and not _siren_audio.playing:
			_siren_audio.play(randf() * 3.0)
		elif not on:
			_siren_audio.stop()
	if on and _siren_lights.is_empty() and (driver is Player or _near_player(120.0)):
		var H := float(meta.get("height", 1.45))
		for c in [Color(1, 0.1, 0.1), Color(0.1, 0.3, 1.0)]:
			var l := OmniLight3D.new()
			l.light_color = c
			l.light_energy = 3.0
			l.omni_range = 9.0
			l.shadow_enabled = false
			l.position = Vector3(-0.3 if c.r > 0.5 else 0.3, H + 0.3, 0)
			add_child(l)
			_siren_lights.append(l)
	elif not on:
		for l in _siren_lights:
			l.queue_free()
		_siren_lights.clear()


func honk(duration := 0.6) -> void:
	if not _horn_audio.playing:
		_horn_audio.play()
		get_tree().create_timer(duration).timeout.connect(func(): if is_instance_valid(_horn_audio): _horn_audio.stop())


# ------------------------------------------------------------------ occupants
func driver_seat_transform() -> Transform3D:
	var s: Array = meta.get("seat", [-0.4, 0.5, 0.0])
	return Transform3D(Basis.IDENTITY, Vector3(s[0], float(s[1]) - 0.4, s[2]))


func get_entry_point() -> Vector3:
	var s: Array = meta.get("seat", [-0.4, 0.5, 0.0])
	var W := float(meta.get("width", 1.8))
	return global_transform * Vector3(-W * 0.5 - 0.6, 0.0, float(s[2]))


func get_exit_point() -> Vector3:
	var s: Array = meta.get("seat", [-0.4, 0.5, 0.0])
	var W := float(meta.get("width", 1.8))
	for side in [-1.0, 1.0]:
		var p := global_transform * Vector3(side * (W * 0.5 + 0.8), 0.3, float(s[2]))
		var q := PhysicsRayQueryParameters3D.create(global_transform * Vector3(0, 0.8, float(s[2])), p + Vector3.UP * 0.5)
		q.exclude = [get_rid()]
		q.collision_mask = 1 | (1 << 2)
		if get_world_3d().direct_space_state.intersect_ray(q).is_empty():
			return p
	return global_position + Vector3.UP * (float(meta.get("height", 1.5)) + 0.5)


func set_driver(d: Node3D) -> void:
	driver = d
	if d:
		is_parked = false
		freeze = false
		kinematic_mode = false
		sleeping = false
		AudioManager.play_3d("door_close", global_position, -4.0)
	else:
		throttle = 0.0
		steer_input = 0.0
		AudioManager.play_3d("door_open", global_position, -6.0)


## Removes an NPC driver (carjacking). Returns the ejected NPC or null.
func eject_driver(by: Node) -> Node:
	if ai_driver == null:
		return null
	var npc: Node = ai_driver.call("abandon_vehicle", by) if ai_driver.has_method("abandon_vehicle") else null
	ai_driver = null
	return npc if npc else self


func set_kinematic(on: bool) -> void:
	if on == kinematic_mode:
		return
	kinematic_mode = on
	freeze_mode = RigidBody3D.FREEZE_MODE_KINEMATIC
	freeze = on
	if on:
		linear_velocity = Vector3.ZERO
		angular_velocity = Vector3.ZERO


# ------------------------------------------------------------------ damage
func _on_body_entered(_body: Node) -> void:
	pass


func _collision_damage(impulse: float, local_pos: Vector3, other: Object) -> void:
	var now := Time.get_ticks_msec() / 1000.0
	if now - _last_collision_time < 0.25:
		return
	_last_collision_time = now
	var sev := impulse / mass
	var dmg := sev * 18.0
	body_health = maxf(0.0, body_health - dmg)
	engine_health = maxf(0.0, engine_health - dmg * (0.9 if local_pos.z < 0.0 else 0.35))
	AudioManager.play_3d("crash_%d" % (randi() % 3), global_transform * local_pos, clampf(sev - 6.0, -12.0, 6.0))
	VFX.burst(global_transform * local_pos, Vector3.UP, "sparks")
	Events.vehicle_collision.emit(self, other, impulse, global_transform * local_pos)
	_deform(local_pos, clampf(sev * 0.012, 0.0, 0.18))
	for mi in _paint_meshes:
		mi.set_instance_shader_parameter("damage", clampf(1.0 - body_health / 1000.0, 0.0, 1.0))
	# bumpers
	var key := "BumperF" if local_pos.z < 0.0 else "BumperR"
	if _bumpers.has(key) and _bumpers[key]["attached"]:
		_bumpers[key]["damage"] = float(_bumpers[key]["damage"]) + sev
		if float(_bumpers[key]["damage"]) > 38.0:
			_detach_bumper(key)
	if sev > 22.0 and not _glass_broken:
		break_glass()
	if driver is Player and sev > 10.0:
		var p := driver as Player
		if p.cam:
			p.cam.add_shake(minf(sev / 40.0, 1.0))
		if sev > 30.0:
			p.health.take_damage((sev - 30.0) * 1.2, null)
	_check_engine()


func _deform(local_pos: Vector3, amount: float) -> void:
	if body_mesh == null or amount < 0.01 or _deform_cooldown > Time.get_ticks_msec():
		return
	_deform_cooldown = Time.get_ticks_msec() + 120
	var mesh := body_mesh.mesh as ArrayMesh
	if mesh == null:
		return
	var center := local_pos
	var radius := 0.9
	var inward := Vector3(-center.x, 0.0, -center.z).normalized()
	var new_mesh := ArrayMesh.new()
	for si in mesh.get_surface_count():
		var arr := mesh.surface_get_arrays(si)
		var verts: PackedVector3Array = arr[Mesh.ARRAY_VERTEX]
		for i in verts.size():
			var d := verts[i].distance_to(center)
			if d < radius:
				var f := (1.0 - d / radius)
				verts[i] += inward * amount * f * f + Vector3(randf() - 0.5, randf() - 0.5, randf() - 0.5) * amount * 0.15 * f
		arr[Mesh.ARRAY_VERTEX] = verts
		new_mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arr)
		new_mesh.surface_set_material(si, mesh.surface_get_material(si))
	body_mesh.mesh = new_mesh


func _detach_bumper(key: String) -> void:
	var b: Dictionary = _bumpers[key]
	b["attached"] = false
	var mi: MeshInstance3D = b["node"]
	var rb := RigidBody3D.new()
	rb.mass = 20.0
	rb.collision_layer = 1 << 4
	rb.collision_mask = 1 | (1 << 2)
	var cs := CollisionShape3D.new()
	var bs := BoxShape3D.new()
	var aabb := mi.get_aabb()
	bs.size = aabb.size.max(Vector3(0.05, 0.05, 0.05))
	cs.shape = bs
	cs.position = aabb.get_center()
	rb.add_child(cs)
	var gt := mi.global_transform
	mi.get_parent().remove_child(mi)
	rb.add_child(mi)
	mi.transform = Transform3D.IDENTITY
	get_tree().current_scene.add_child(rb)
	rb.global_transform = gt
	rb.linear_velocity = linear_velocity * 0.6
	get_tree().create_timer(40.0).timeout.connect(rb.queue_free)


func break_glass() -> void:
	if _glass_broken:
		return
	_glass_broken = true
	if _glass:
		_glass.set_instance_shader_parameter("broken", 1.0)
	AudioManager.play_3d("glass_break", global_position + Vector3.UP, 0.0)
	VFX.burst(global_position + Vector3.UP * 1.2, Vector3.UP, "glass")


func on_hit(damage: float, source: Node, pos: Vector3, dir: Vector3) -> void:
	var local := to_local(pos)
	# tyres
	for i in mini(4, _wheels.size()):
		var w: Dictionary = _wheels[i]
		if (w["pos"] as Vector3).distance_to(local) < float(w["radius"]) + 0.1 and not _flat[i]:
			_flat[i] = true
			if w["node"]:
				(w["node"] as Node3D).scale = Vector3(1.0, 0.85, 0.85)
			AudioManager.play_3d("impact_0", pos, 2.0)
			return
	engine_health = maxf(0.0, engine_health - damage * (2.2 if local.z < -0.5 else 0.8))
	body_health = maxf(0.0, body_health - damage)
	if local.y > float(meta.get("hood", 1.0)) and randf() < 0.3:
		break_glass()
	for mi in _paint_meshes:
		mi.set_instance_shader_parameter("damage", clampf(1.0 - body_health / 1000.0, 0.0, 1.0))
	if source and source.is_in_group("player") and (driver != null or ai_driver != null):
		Events.crime_committed.emit("shoot_vehicle", pos, 1 if not is_police else 2, source)
	if ai_driver and ai_driver.has_method("on_vehicle_attacked"):
		ai_driver.call("on_vehicle_attacked", source)
	_check_engine()


func _check_engine() -> void:
	var front := Vector3(0, float(meta.get("hood", 1.0)), -float(meta.get("length", 4.5)) * 0.35)
	if engine_health < 400.0 and _smoke == null:
		_smoke = VFX.engine_smoke(self, front, false)
	if engine_health < 150.0 and _smoke:
		var q := _smoke.mesh as QuadMesh
		if q:
			q.material = VFX._particle_material(Color(0.15, 0.15, 0.15, 0.5), false)
	if engine_health <= 0.0 and _fire == null and not destroyed:
		_fire = VFX.fire(self, front)
		_burn_timer = 6.0


func explode() -> void:
	if destroyed:
		return
	destroyed = true
	_burn_timer = -1.0
	Combat.explode(get_world_3d(), global_position + Vector3.UP, 7.0, 180.0, self)
	apply_central_impulse(Vector3.UP * mass * 6.0 + Vector3(randf() - 0.5, 0, randf() - 0.5) * mass * 2.0)
	apply_torque_impulse(Vector3(randf() - 0.5, 0, randf() - 0.5) * mass * 3.0)
	for mi in _paint_meshes:
		mi.set_instance_shader_parameter("paint", Color(0.06, 0.05, 0.05))
		mi.set_instance_shader_parameter("dirt", 1.0)
	break_glass()
	for k in _bumpers.keys():
		if _bumpers[k]["attached"]:
			_detach_bumper(k)
	if driver is Player:
		(driver as Player).health.take_damage(500.0, self)
	elif ai_driver and ai_driver.has_method("on_vehicle_destroyed"):
		ai_driver.call("on_vehicle_destroyed")
	Events.vehicle_destroyed.emit(self)
	destroyed_signal.emit(self)
	get_tree().create_timer(25.0).timeout.connect(func():
		if is_instance_valid(_fire):
			_fire.queue_free())


func repair() -> void:
	engine_health = 1000.0
	body_health = 1000.0
	_glass_broken = false
	_flat = [false, false, false, false]
	if _glass:
		_glass.set_instance_shader_parameter("broken", 0.0)
	for w in _wheels:
		if w["node"]:
			(w["node"] as Node3D).scale = Vector3.ONE
	if _smoke:
		_smoke.queue_free()
		_smoke = null
	for mi in _paint_meshes:
		mi.set_instance_shader_parameter("damage", 0.0)
		mi.set_instance_shader_parameter("dirt", 0.0)
	# restore the original body shape
	if visual:
		var s: PackedScene = load(GLB_DIR + type_id + ".glb")
		var fresh := s.instantiate()
		var b := fresh.find_child("Body", true, false) as MeshInstance3D
		if b and body_mesh:
			body_mesh.mesh = b.mesh.duplicate()
		fresh.free()


func set_paint(c: Color) -> void:
	paint = c
	for mi in _paint_meshes:
		mi.set_instance_shader_parameter("paint", c)


func state_dict() -> Dictionary:
	return {"type": type_id, "color": paint.to_html(), "engine": engine_health, "body": body_health}


func speed() -> float:
	return linear_velocity.length()
