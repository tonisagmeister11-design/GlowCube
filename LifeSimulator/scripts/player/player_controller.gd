class_name PlayerController
extends CharacterBody3D
## Spielerfigur: Bewegung relativ zur Kamera, Third-/First-Person-Kamera,
## Stufen/Bordsteine, Interaktion (Gruppe `interactable`) und Fahrzeuge
## (Gruppe `vehicle`). Haelt keinen Spielzustand; Aussehen und Alter kommen
## aus `Game.world.character` bzw. `Game.world.player`.
##
## Fahrzeuge: `vehicle` in Reichweite -> `can_enter(self)` / `enter(self)`.
## Waehrend der Fahrt folgt der Spielerknoten `get_camera_target()` (damit
## Verkehr/Passanten weiter dem Knoten in Gruppe `player` folgen koennen),
## Koerper und Kollision sind aus. Aussteigen: Taste `vehicle` -> `exit()`
## liefert den Ausstiegspunkt. Fahrzeuge, die den Spieler selbst absetzen,
## rufen `exit_vehicle(punkt)` auf.

signal footstep(position: Vector3, intensity: float)
signal vehicle_entered(vehicle: Node)
signal vehicle_exited(vehicle: Node)
signal camera_mode_changed(first_person: bool)

const GRAVITY := 9.81
const INTERACT_RANGE := 2.5
const VEHICLE_RANGE := 3.6

@export var walk_speed := 1.8
@export var run_speed := 5.4
@export var jump_velocity := 4.3
@export var acceleration := 12.0
@export var air_control := 2.5
@export var min_zoom := 1.4
@export var max_zoom := 8.0

var controls_enabled := true
var first_person := false
var character: Dictionary = {}
var age := 24.0
var vehicle: Node = null

var body: Humanoid
var collision: CollisionShape3D
var cam_pivot: Node3D
var spring: SpringArm3D
var camera: Camera3D

var cam_yaw := PI
var cam_pitch := -0.22
var zoom := 3.4
var body_yaw := 0.0
var step_height := 0.3

var _interact_target: Node = null
var _prompt_shown := false
var _prompt_text := ""
var _panel_open := false
var _scan_t := 0.0
var _vehicle_frame := -10
var _step_smooth := 0.0
var _pivot_base := 1.5
var _mouse_captured := false


func _ready() -> void:
	add_to_group("player")
	floor_max_angle = deg_to_rad(50.0)
	floor_snap_length = 0.35
	safe_margin = 0.02
	_load_character()
	_build_body()
	_build_camera()
	var bus := _bus()
	if bus:
		bus.open_panel.connect(func(_p, _c = {}): _panel_open = true)
		bus.close_panels.connect(func(): _panel_open = false)
		bus.player_aged.connect(func(_a): refresh_body())
		bus.player_life_stage_changed.connect(func(_s): refresh_body())
	var mode = _settings_value("gameplay", "camera_mode", "third")
	set_first_person(str(mode) == "first")


func _exit_tree() -> void:
	if _mouse_captured:
		Input.mouse_mode = Input.MOUSE_MODE_VISIBLE


# ================================================================== Aufbau

func _game() -> Node:
	return get_node_or_null("/root/Game")


func _bus() -> Node:
	return get_node_or_null("/root/EventBus")


func _settings_value(section: String, key: String, def):
	var s := get_node_or_null("/root/Settings")
	if s:
		var v = s.get_value(section, key)
		return def if v == null else v
	return def


func _world():
	var g := _game()
	return g.world if g else null


func _load_character() -> void:
	var w = _world()
	if w and w.character is Dictionary and not w.character.is_empty():
		character = w.character.duplicate(true)
	if character.is_empty():
		character = {"name": "Alex Berger", "body": "female", "age": 24.0, "appearance": Appearance.defaults("female", 24.0)}
	age = float(character.get("age", 24.0))
	if w and w.player:
		if w.player.has_method("age"):
			age = float(w.player.age(w))
		var app = w.player.get("appearance")
		if app is Dictionary and not app.is_empty():
			character.appearance = app


## Liest Alter/Aussehen neu und baut die Figur neu (z. B. nach Geburtstag).
func refresh_body() -> void:
	_load_character()
	_build_body()


## Setzt Figur direkt aus Editordaten {name, body, age, appearance}.
func refresh_body_from(d: Dictionary) -> void:
	character = d.duplicate(true)
	age = float(character.get("age", age))
	_build_body()


func _build_body() -> void:
	if body:
		body.queue_free()
	body = Humanoid.new()
	body.name = "Body"
	add_child(body)
	body.setup(character.get("appearance", {}), age, str(character.get("body", "female")))
	body.rotation.y = body_yaw
	body.footstep.connect(func(_side: int, intensity: float): footstep.emit(global_position, intensity))
	var h := body.height()
	var crawling := age < 1.0
	var r := clampf(0.17 * h / 1.75 + 0.04, 0.11, 0.3)
	var ch := maxf(h * (0.45 if crawling else 0.96), r * 2.0 + 0.02)
	if collision == null:
		collision = CollisionShape3D.new()
		collision.name = "Collision"
		add_child(collision)
	var cap := CapsuleShape3D.new()
	cap.radius = r
	cap.height = ch
	collision.shape = cap
	collision.position = Vector3(0, ch * 0.5, 0)
	step_height = clampf(0.3 * h / 1.75, 0.22, 0.34)
	_pivot_base = body.eye_height() * 0.93 if not crawling else h * 0.5
	if cam_pivot:
		cam_pivot.position.y = _pivot_base
	if first_person:
		body.set_shadow_only(true)


func _build_camera() -> void:
	cam_pivot = Node3D.new()
	cam_pivot.name = "CameraPivot"
	cam_pivot.position = Vector3(0, _pivot_base, 0)
	add_child(cam_pivot)
	spring = SpringArm3D.new()
	spring.name = "SpringArm"
	spring.spring_length = zoom
	spring.margin = 0.15
	var sph := SphereShape3D.new()
	sph.radius = 0.18
	spring.shape = sph
	spring.add_excluded_object(get_rid())
	cam_pivot.add_child(spring)
	camera = Camera3D.new()
	camera.name = "Camera"
	camera.fov = 70.0
	camera.near = 0.05
	camera.far = 2000.0
	spring.add_child(camera)
	camera.current = true
	_update_camera(0.0)


# ================================================================== Oeffentliche API

func get_body() -> Humanoid:
	return body


func get_camera() -> Camera3D:
	return camera


func set_controls_enabled(enabled: bool) -> void:
	controls_enabled = enabled
	if not enabled:
		velocity.x = 0.0
		velocity.z = 0.0
		_clear_prompt()


## Setzt den Spieler an eine Position; yaw = Blickrichtung (0 = +Z).
func teleport(pos: Vector3, yaw := 0.0) -> void:
	if vehicle:
		exit_vehicle(pos)
	global_position = pos
	velocity = Vector3.ZERO
	body_yaw = yaw
	if body:
		body.rotation.y = yaw
	cam_yaw = yaw + PI
	_step_smooth = 0.0
	_update_camera(0.0)


func set_first_person(on: bool) -> void:
	first_person = on
	if body:
		body.set_shadow_only(on and vehicle == null)
	camera_mode_changed.emit(on)


func is_in_vehicle() -> bool:
	return vehicle != null


func enter_vehicle(v: Node) -> bool:
	if vehicle or v == null:
		return false
	if v.has_method("can_enter") and not v.can_enter(self):
		return false
	vehicle = v
	_vehicle_frame = Engine.get_physics_frames()
	if v.has_method("enter"):
		v.enter(self)
	velocity = Vector3.ZERO
	if body:
		body.visible = false
	collision.disabled = true
	_clear_prompt()
	vehicle_entered.emit(v)
	return true


## Verlaesst das Fahrzeug. pos: Ausstiegspunkt (sonst von vehicle.exit()).
func exit_vehicle(pos = null) -> void:
	if vehicle == null:
		return
	var v := vehicle
	vehicle = null
	var target: Vector3 = global_position
	if pos is Vector3:
		target = pos
	elif v.has_method("exit"):
		var r = v.exit()
		if r is Vector3:
			target = r
	global_position = target
	velocity = Vector3.ZERO
	collision.disabled = false
	if body:
		body.visible = true
		body.set_shadow_only(first_person)
	cam_pivot.position = Vector3(0, _pivot_base, 0)
	_vehicle_frame = Engine.get_physics_frames()
	vehicle_exited.emit(v)


# ================================================================== Eingabe

func _ui_blocked() -> bool:
	var g := _game()
	return _panel_open or (g != null and int(g.paused_by_ui) > 0)


func _update_mouse_mode() -> void:
	var want := controls_enabled and not _ui_blocked() and DisplayServer.get_name() != "headless"
	if want != _mouse_captured:
		_mouse_captured = want
		Input.mouse_mode = Input.MOUSE_MODE_CAPTURED if want else Input.MOUSE_MODE_VISIBLE


func _unhandled_input(event: InputEvent) -> void:
	if _ui_blocked():
		return
	if event is InputEventMouseMotion and Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
		var sens := float(_settings_value("controls", "mouse_sensitivity", 0.25)) * 0.012
		var inv := -1.0 if bool(_settings_value("controls", "invert_y", false)) else 1.0
		cam_yaw -= event.relative.x * sens
		cam_pitch = clampf(cam_pitch - event.relative.y * sens * inv, -1.35, 1.2 if first_person else 0.75)
	elif event is InputEventMouseButton and event.pressed and not first_person:
		if event.button_index == MOUSE_BUTTON_WHEEL_UP:
			zoom = clampf(zoom * 0.9, min_zoom, max_zoom)
		elif event.button_index == MOUSE_BUTTON_WHEEL_DOWN:
			zoom = clampf(zoom * 1.1, min_zoom, max_zoom)
	if event.is_action_pressed("camera_toggle"):
		set_first_person(not first_person)


func _action_pressed(action: String) -> bool:
	return InputMap.has_action(action) and Input.is_action_just_pressed(action)


func _process(delta: float) -> void:
	_update_mouse_mode()
	_update_camera(delta)


func _update_camera(delta: float) -> void:
	if cam_pivot == null:
		return
	_step_smooth = lerpf(_step_smooth, 0.0, 1.0 - exp(-12.0 * delta)) if delta > 0.0 else 0.0
	if vehicle and vehicle.has_method("get_camera_target"):
		var tgt = vehicle.get_camera_target()
		if tgt is Node3D:
			cam_pivot.global_position = (tgt as Node3D).global_position + Vector3(0, 1.0, 0)
		spring.spring_length = lerpf(spring.spring_length, maxf(zoom, 5.5), 1.0 - exp(-6.0 * maxf(delta, 0.0)))
	else:
		var py := _pivot_base + _step_smooth
		if first_person and body:
			py = body.eye_height() + _step_smooth
		cam_pivot.position = Vector3(0, py, 0)
		var target_len := 0.0 if first_person else zoom
		spring.spring_length = target_len if delta <= 0.0 else lerpf(spring.spring_length, target_len, 1.0 - exp(-10.0 * delta))
	cam_pivot.global_rotation = Vector3(cam_pitch, cam_yaw, 0.0)
	camera.position = Vector3(0.0, 0.0, 0.0)
	if first_person and vehicle == null:
		camera.position = Vector3(0, 0, -0.08)


# ================================================================== Bewegung

## Geschwindigkeitsfaktor aus Alter und Energie (PlayerLife) oder Alterskurve.
func speed_factor() -> float:
	var w = _world()
	if w and w.player and w.player.has_method("movement_speed_factor"):
		return clampf(float(w.player.movement_speed_factor()), 0.05, 2.0)
	return Humanoid.age_speed_factor(age)


func _physics_process(delta: float) -> void:
	if vehicle:
		_vehicle_physics()
		return
	var input := Vector2.ZERO
	var active := controls_enabled and not _ui_blocked()
	if active:
		input = Input.get_vector("move_left", "move_right", "move_forward", "move_back")
	var fwd := Vector3(-sin(cam_yaw), 0.0, -cos(cam_yaw))
	var right := Vector3(cos(cam_yaw), 0.0, -sin(cam_yaw))
	var wish := right * input.x + fwd * (-input.y)
	if wish.length() > 1.0:
		wish = wish.normalized()
	var f := speed_factor()
	var walk := walk_speed * f
	var run := maxf(walk, run_speed * f * Humanoid.age_run_factor(age) / maxf(Humanoid.age_speed_factor(age), 0.2))
	var sprint := active and Input.is_action_pressed("sprint")
	var target := wish * (run if sprint else walk)
	var on_floor := is_on_floor()
	var acc := acceleration if on_floor else air_control
	var hv := Vector3(velocity.x, 0.0, velocity.z)
	hv = hv.lerp(target, 1.0 - exp(-acc * delta))
	velocity.x = hv.x
	velocity.z = hv.z
	if not on_floor:
		velocity.y -= GRAVITY * delta
	elif active and _action_pressed("jump") and age >= 3.0:
		velocity.y = jump_velocity * clampf(sqrt(f), 0.5, 1.1)
		on_floor = false
	# Blickrichtung des Koerpers
	if first_person:
		body_yaw = cam_yaw + PI
	elif wish.length() > 0.1:
		var ty := atan2(wish.x, wish.z)
		body_yaw = lerp_angle(body_yaw, ty, 1.0 - exp(-10.0 * delta))
	if body:
		body.rotation.y = body_yaw
	_move_with_steps(delta, on_floor)
	if body:
		var hs := Vector2(get_real_velocity().x, get_real_velocity().z).length()
		body.set_motion(minf(hs, target.length() + 0.5), is_on_floor() or (on_floor and velocity.y <= 0.0))
		if body.get_action() in ["walk", "run"]:
			body.set_action("idle")
	_scan_t -= delta
	if _scan_t <= 0.0:
		_scan_t = 0.1
		_scan_interactables()
	if active:
		if _action_pressed("interact") and _interact_target and is_instance_valid(_interact_target):
			interact_nearest()
		elif _action_pressed("vehicle") and Engine.get_physics_frames() - _vehicle_frame > 2:
			var v := _nearest_vehicle()
			if v:
				enter_vehicle(v)


func _move_with_steps(delta: float, was_on_floor: bool) -> void:
	var start := global_transform
	var h_motion := Vector3(velocity.x, 0.0, velocity.z) * delta
	move_and_slide()
	if not was_on_floor or h_motion.length() < 0.0005 or velocity.y > 0.5:
		return
	var moved := global_position - start.origin
	moved.y = 0.0
	if moved.length() >= h_motion.length() * 0.8:
		return
	var blocked := false
	for i in get_slide_collision_count():
		var c := get_slide_collision(i)
		if absf(c.get_normal().y) < 0.35:
			blocked = true
			break
	if not blocked:
		return
	# Stufe hochsteigen: hoch, vor, runter
	var t := start
	var up := Vector3.UP * step_height
	if test_move(t, up):
		return
	t.origin += up
	# Weit genug vor, damit die Kapselrundung ueber der Kante liegt
	var cap := collision.shape as CapsuleShape3D
	var reach := (cap.radius + 0.02) if cap else 0.25
	var fwd := h_motion.normalized() * maxf(h_motion.length(), reach)
	if test_move(t, fwd):
		return
	t.origin += fwd
	var col := KinematicCollision3D.new()
	if test_move(t, Vector3.DOWN * (step_height + 0.05), col):
		if col.get_normal().y > 0.7:
			t.origin += col.get_travel()
			var dy := t.origin.y - start.origin.y
			if dy > 0.01 and dy <= step_height + 0.01:
				global_transform = t
				velocity.y = 0.0
				_step_smooth -= dy
				apply_floor_snap()


func _vehicle_physics() -> void:
	if not is_instance_valid(vehicle):
		vehicle = null
		collision.disabled = false
		if body:
			body.visible = true
		return
	if vehicle.has_method("get_camera_target"):
		var tgt = vehicle.get_camera_target()
		if tgt is Node3D:
			global_position = (tgt as Node3D).global_position
	if controls_enabled and not _ui_blocked() and _action_pressed("vehicle") and Engine.get_physics_frames() - _vehicle_frame > 2:
		exit_vehicle()


# ================================================================== Interaktion

func _facing() -> Vector3:
	return Vector3(sin(body_yaw), 0.0, cos(body_yaw))


func _scan_interactables() -> void:
	if not controls_enabled or vehicle:
		_clear_prompt()
		return
	var best: Node = null
	var best_score := INF
	var origin := global_position + Vector3(0, 0.9, 0)
	var facing := _facing()
	for n in get_tree().get_nodes_in_group("interactable"):
		if not (n is Node3D) or not n.is_inside_tree():
			continue
		var d: Vector3 = (n as Node3D).global_position - origin
		var flat := Vector3(d.x, 0.0, d.z)
		var dist := flat.length()
		if dist > INTERACT_RANGE or absf(d.y) > 2.2:
			continue
		var dot := facing.dot(flat.normalized()) if dist > 0.05 else 1.0
		if dot < -0.25:
			continue
		var score := dist - dot * 0.6
		if score < best_score:
			best_score = score
			best = n
	var prompt := ""
	if best:
		prompt = str(best.get_prompt(self)) if best.has_method("get_prompt") else "Interagieren"
	else:
		var v := _nearest_vehicle()
		if v:
			var key := "F"
			var s := get_node_or_null("/root/Settings")
			if s:
				key = s.binding_label("vehicle")
			prompt = "Einsteigen (%s)" % key
	_interact_target = best
	if prompt == "":
		_clear_prompt()
	elif not _prompt_shown or prompt != _prompt_text:
		_prompt_shown = true
		_prompt_text = prompt
		var bus := _bus()
		if bus:
			bus.interaction_available.emit(prompt)


func _clear_prompt() -> void:
	_interact_target = null
	if _prompt_shown:
		_prompt_shown = false
		_prompt_text = ""
		var bus := _bus()
		if bus:
			bus.interaction_cleared.emit()


## Fuehrt die Interaktion mit dem aktuell erkannten Objekt aus.
func interact_nearest() -> bool:
	_scan_interactables()
	if _interact_target and is_instance_valid(_interact_target) and _interact_target.has_method("interact"):
		_interact_target.interact(self)
		return true
	return false


func get_interaction_target() -> Node:
	return _interact_target


func _nearest_vehicle() -> Node:
	var best: Node = null
	var bd := VEHICLE_RANGE
	for v in get_tree().get_nodes_in_group("vehicle"):
		if not (v is Node3D):
			continue
		var d := (v as Node3D).global_position.distance_to(global_position)
		if d < bd:
			if v.has_method("is_occupied") and v.is_occupied():
				continue
			if v.has_method("can_enter") and not v.can_enter(self):
				continue
			bd = d
			best = v
	return best
