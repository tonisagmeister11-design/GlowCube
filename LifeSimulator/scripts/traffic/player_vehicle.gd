class_name PlayerVehicle
extends CharacterBody3D
## Vom Spieler fahrbares Fahrzeug (Vertrag "Fahrzeuge" in ARCHITECTURE.md):
## can_enter/enter/exit/get_camera_target/is_occupied, vehicle_id.
## Arcade-Fahrphysik (Einspurmodell mit Seitenschlupf, Handbremse zum
## Driften), Kollision ueber move_and_slide mit Gebaeuden, Requisiten und
## KI-Fahrzeugen (AnimatableBody3D). KI-Fahrzeuge bremsen fuer das
## Spielerfahrzeug (TrafficManager meldet es als Hindernis).
## Eigene Fahrzeuge (vehicle_id >= 0) sind immer zugaenglich; fremde nur,
## wenn sie entriegelt sind (set_locked(false), z.B. durch das Kriminalitaetssystem).

signal entered(player)
signal exited(player)

@export var vehicle_id := -1
var type_id := "sedan"
var model_id := ""                # VehicleCatalog-Modell oder GoodsCatalog-Ware
var locked := true
var world                         # WorldState (optional)
var visual: VehicleVisual
var camera_target: Node3D
var driver: Node = null
var speed := 0.0                  # vorwaerts (m/s, negativ = rueckwaerts)
var lat_speed := 0.0
var steer := 0.0
var throttle := 0.0
var braking := false
var top_speed := 45.0
var accel := 4.5
var brake_decel := 9.0
var wheelbase := 2.8
var _engine: Node
var _horn_cd := 0.0
var _impact_cd := 0.0


## Aufbau: type_id siehe VehicleCatalog.TYPES; model_id fuer Fahrwerte.
func setup(p_type: String, p_color: Color, p_model := "", p_world = null) -> void:
	type_id = p_type
	model_id = p_model
	world = p_world
	add_to_group("vehicle")
	var t := VehicleCatalog.type_info(p_type)
	var h := VehicleCatalog.handling(p_model if p_model != "" else p_type)
	top_speed = h.top_speed
	accel = h.accel
	brake_decel = h.brake
	wheelbase = t.length * 0.6
	visual = VehicleVisual.new()
	visual.build(p_type, p_color, randi())
	add_child(visual)
	var cs := CollisionShape3D.new()
	var bs := BoxShape3D.new()
	bs.size = Vector3(t.width, t.height * 0.75, t.length)
	cs.shape = bs
	cs.position = Vector3(0, t.height * 0.4 + 0.12, 0)
	add_child(cs)
	camera_target = Node3D.new()
	camera_target.name = "CameraTarget"
	camera_target.position = Vector3(0, t.height * 0.7, 0)
	add_child(camera_target)
	floor_snap_length = 0.6
	safe_margin = 0.04
	collision_layer = 1
	collision_mask = 1


func can_enter(_player) -> bool:
	return driver == null and (vehicle_id >= 0 or not locked)


func set_locked(on: bool) -> void:
	locked = on


func is_occupied() -> bool:
	return driver != null


func enter(player) -> void:
	driver = player
	braking = false
	_play("car_door")
	if _engine == null and ClassDB.class_exists("Node3D") and ResourceLoader.exists("res://scripts/audio/engine_sound.gd"):
		_engine = load("res://scripts/audio/engine_sound.gd").new()
		_engine.set("base_pitch", 0.7 if type_id in ["van", "truck", "bus"] else (1.2 if type_id == "motorcycle" else 1.0))
		if type_id != "bicycle":
			add_child(_engine)
	entered.emit(player)


## Aussteigen: liefert einen Punkt auf dem Gehweg neben dem Fahrzeug.
func exit() -> Vector3:
	var p := driver
	driver = null
	throttle = 0.0
	if _engine:
		_engine.queue_free()
		_engine = null
	_play("car_door")
	_store_position()
	exited.emit(p)
	return exit_point()


func exit_point() -> Vector3:
	var left := -global_transform.basis.x.normalized()
	var base: Vector3 = global_position + left * (VehicleCatalog.type_info(type_id).width * 0.5 + 0.9)
	var city: CityModel = world.city if world != null else null
	if city:
		var p2 := Vector2(global_position.x, global_position.z)
		var ne := city.nearest_edge(p2)
		if ne.edge >= 0 and ne.dist < 20.0:
			var e: Dictionary = city.edges[ne.edge]
			var side: int = ne.side
			if (e.sidewalk[1] if side > 0 else e.sidewalk[0]) <= 0.0:
				side = -side
			var q := city.edge_point(e, ne.t, city.sidewalk_offset(e, side))
			if q.distance_to(p2) < 14.0:
				return Vector3(q.x, 0.15, q.y)
	return base + Vector3(0, 0.15, 0)


func get_camera_target() -> Node3D:
	return camera_target


func _store_position() -> void:
	if vehicle_id < 0 or world == null or world.player == null:
		return
	var vs: Array = world.player.get("vehicles") if world.player.get("vehicles") != null else []
	if vehicle_id < vs.size() and typeof(vs[vehicle_id]) == TYPE_DICTIONARY:
		vs[vehicle_id]["pos"] = [global_position.x, global_position.z, rotation.y]


func _play(sound: String) -> void:
	var audio := get_node_or_null("/root/Audio")
	if audio and audio.has_method("play_sfx"):
		audio.play_sfx(sound, global_position)


func _input_enabled() -> bool:
	if driver == null:
		return false
	var g := get_node_or_null("/root/Game")
	if g and int(g.get("paused_by_ui")) > 0:
		return false
	if driver.get("controls_enabled") == false:
		return false
	return true


func _axis(neg: String, pos: String) -> float:
	if not InputMap.has_action(neg) or not InputMap.has_action(pos):
		return 0.0
	return Input.get_axis(neg, pos)


func _physics_process(delta: float) -> void:
	var hb := true
	throttle = 0.0
	var steer_in := 0.0
	if _input_enabled():
		throttle = _axis("move_back", "move_forward")
		steer_in = _axis("move_right", "move_left")
		hb = InputMap.has_action("handbrake") and Input.is_action_pressed("handbrake")
		if InputMap.has_action("horn") and Input.is_action_pressed("horn") and _horn_cd <= 0.0:
			_horn_cd = 0.6
			_play("car_horn" if type_id != "bus" else "bus_horn")
	drive(delta, throttle, steer_in, hb)


## Fahrphysik eines Schritts (auch fuer Tests aufrufbar).
func drive(delta: float, thr: float, steer_in: float, handbrake: bool) -> void:
	_horn_cd -= delta
	_impact_cd -= delta
	braking = false
	if thr > 0.05:
		if speed < -0.5:
			speed = move_toward(speed, 0.0, brake_decel * delta)
			braking = true
		else:
			var f := 1.0 - clampf(speed / top_speed, 0.0, 1.0)
			speed += accel * thr * (0.35 + 0.65 * f) * delta
	elif thr < -0.05:
		if speed > 0.5:
			speed = move_toward(speed, 0.0, brake_decel * -thr * delta)
			braking = true
		else:
			speed = maxf(speed - accel * 0.6 * -thr * delta, -top_speed * 0.2)
	# Roll- und Luftwiderstand
	speed -= signf(speed) * minf(absf(speed), (0.35 + 0.0018 * speed * speed) * delta)
	if handbrake:
		speed = move_toward(speed, 0.0, (3.5 if driver != null else 12.0) * delta)
		braking = braking or driver != null
	# Lenkung (bei hoher Geschwindigkeit geringer)
	var max_steer := 0.6 / (1.0 + absf(speed) / 14.0)
	steer = move_toward(steer, steer_in * max_steer, delta * 2.5)
	var yaw_rate := speed / wheelbase * tan(steer)
	if handbrake and absf(speed) > 6.0:
		yaw_rate *= 1.5
	rotation.y += yaw_rate * delta
	# Seitenschlupf: Haftung zieht die Querbewegung zur Fahrzeugachse
	var fwd := -global_transform.basis.z
	var right := global_transform.basis.x
	var grip := 1.6 if handbrake else 9.0
	lat_speed = lerpf(lat_speed, 0.0, 1.0 - exp(-grip * delta))
	lat_speed += -yaw_rate * speed * delta * (0.35 if handbrake else 0.08)
	var vy := velocity.y
	if not is_on_floor():
		vy -= 18.0 * delta
	else:
		vy = minf(vy, 0.0)
	velocity = fwd * speed + right * lat_speed + Vector3(0, vy, 0)
	var before := Vector2(velocity.x, velocity.z).length()
	move_and_slide()
	# Aufprall: Geschwindigkeit aus der tatsaechlichen Bewegung uebernehmen
	if get_slide_collision_count() > 0:
		var after := Vector2(velocity.x, velocity.z)
		speed = after.dot(Vector2(fwd.x, fwd.z))
		lat_speed = after.dot(Vector2(right.x, right.z))
		if before - after.length() > 5.0 and _impact_cd <= 0.0:
			_impact_cd = 1.0
			_play("brake_squeal")
	if global_position.y < -5.0:
		global_position.y = 0.5
		velocity = Vector3.ZERO
	# Darstellung: Neigung, Raeder, Lichter
	if visual:
		var night := false
		if world != null:
			var h: float = world.get_hour_f()
			night = h < 6.5 or h > 19.5
		visual.set_state(speed, steer, braking or (thr < -0.05 and speed > 0.5), 0, night, false, false, delta)
		visual.rotation.z = lerpf(visual.rotation.z, clampf(-yaw_rate * speed * 0.012, -0.08, 0.08), 0.1)
		visual.rotation.x = lerpf(visual.rotation.x, clampf(-thr * 0.02 + (0.03 if braking else 0.0), -0.04, 0.05), 0.1)
	if _engine and _engine.has_method("set_load"):
		_engine.set_load(absf(speed) / maxf(top_speed, 1.0), absf(thr))


## Hindernisdaten fuer die KI (Position, Radius, Geschwindigkeit in 2D).
func obstacle() -> Dictionary:
	var fwd := -global_transform.basis.z
	var v2 := Vector2(velocity.x, velocity.z)
	var t := VehicleCatalog.type_info(type_id)
	return {"pos": Vector2(global_position.x, global_position.z), "r": t.length * 0.5, "vel": v2,
		"dir": Vector2(fwd.x, fwd.z)}
