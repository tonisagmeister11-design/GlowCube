class_name Player
extends CharacterBody3D
## The player character: on-foot movement (run / sprint / crouch / jump / fall /
## swim / ledge climb), aiming and combat via WeaponHolder, interaction,
## entering and leaving vehicles, death and arrest.

enum State { GROUND, AIR, SWIM, CLIMB, VEHICLE, DEAD, BUSTED, LOCKED }

const RUN_SPEED := 4.4
const WALK_SPEED := 1.7
const SPRINT_SPEED := 7.2
const CROUCH_SPEED := 1.5
const AIM_SPEED := 2.2
const SWIM_SPEED := 1.8
const JUMP_VELOCITY := 5.2
const GRAVITY := 17.0
const ACCEL := 14.0
const AIR_ACCEL := 3.0
const FALL_DAMAGE_SPEED := 13.0

var state := State.GROUND
var model: CharacterModel
var cam: CameraRig
var health: Health
var weapons: WeaponHolder
var vehicle: Node3D
var crouching := false
var aiming := false
var sprinting := false
var stamina := 1.0
var move_speed := 0.0
var input_enabled := true
var surface := "concrete"
var _col: CollisionShape3D
var _capsule: CapsuleShape3D
var _fall_speed := 0.0
var _air_time := 0.0
var _climb_target := Vector3.ZERO
var _climb_t := 0.0
var _enter_timer := 0.0
var _step_timer := 0.0
var _interact_target: Node = null
var _prompt_timer := 0.0


func _ready() -> void:
	add_to_group("player")
	collision_layer = 1 << 1
	collision_mask = 1 | (1 << 2) | (1 << 3) | (1 << 4)
	floor_snap_length = 0.45
	floor_max_angle = deg_to_rad(50.0)
	_capsule = CapsuleShape3D.new()
	_capsule.radius = 0.32
	_capsule.height = 1.8
	_col = CollisionShape3D.new()
	_col.shape = _capsule
	_col.position.y = 0.9
	add_child(_col)
	model = CharacterModel.new()
	model.name = "Model"
	model.outfit = Game.player_data.outfit if Game.player_data else default_outfit()
	add_child(model)
	health = Health.new()
	health.name = "Health"
	health.max_health = 100.0
	add_child(health)
	health.died.connect(_on_died)
	health.damaged.connect(_on_damaged)
	weapons = WeaponHolder.new()
	weapons.name = "Weapons"
	add_child(weapons)
	weapons.setup(self, model)
	Events.player_spawned.emit(self)


static func default_outfit() -> Dictionary:
	return {"body": "M", "skin": Color(0.78, 0.58, 0.44), "hair": "Short", "hair_color": Color(0.1, 0.07, 0.05),
		"top": "TShirt", "top_color": Color(0.95, 0.95, 0.93), "bottom": "Jeans", "bottom_color": Color(0.18, 0.26, 0.42),
		"shoes": "Sneakers", "shoes_color": Color(0.92, 0.92, 0.92)}


func set_camera(c: CameraRig) -> void:
	cam = c
	cam.set_target(self)


# ------------------------------------------------------------------ physics
func _physics_process(delta: float) -> void:
	match state:
		State.GROUND, State.AIR:
			_move_on_foot(delta)
		State.SWIM:
			_move_swim(delta)
		State.CLIMB:
			_move_climb(delta)
		State.VEHICLE:
			_vehicle_update(delta)
		State.DEAD, State.BUSTED, State.LOCKED:
			if not model.ragdolled:
				velocity.x = move_toward(velocity.x, 0.0, 20.0 * delta)
				velocity.z = move_toward(velocity.z, 0.0, 20.0 * delta)
				velocity.y -= GRAVITY * delta
				move_and_slide()
	_update_interaction(delta)


func _input_dir() -> Vector3:
	if not input_enabled or cam == null:
		return Vector3.ZERO
	var v := Input.get_vector("move_left", "move_right", "move_forward", "move_back")
	if v.length() < 0.05:
		return Vector3.ZERO
	var basis := Basis(Vector3.UP, cam.yaw)
	var d := basis * Vector3(v.x, 0, v.y)
	return d.limit_length(1.0)


func _move_on_foot(delta: float) -> void:
	var dir := _input_dir()
	var on_floor := is_on_floor()
	aiming = input_enabled and Input.is_action_pressed("aim") and weapons.has_ranged()
	sprinting = input_enabled and Input.is_action_pressed("sprint") and dir.length() > 0.3 and not aiming and not crouching \
		and stamina > 0.05
	if input_enabled and Input.is_action_just_pressed("crouch") and on_floor:
		_set_crouch(not crouching)
	var target_speed := RUN_SPEED
	if crouching:
		target_speed = CROUCH_SPEED
	elif aiming:
		target_speed = AIM_SPEED
	elif sprinting:
		target_speed = SPRINT_SPEED
	var mag := dir.length()
	if mag < 0.6:
		target_speed = minf(target_speed, WALK_SPEED * mag / 0.6 + 0.01)
	stamina = clampf(stamina + (-0.12 if sprinting else 0.2) * delta, 0.0, 1.0)
	var hv := Vector3(velocity.x, 0, velocity.z)
	var want := dir.normalized() * target_speed if mag > 0.0 else Vector3.ZERO
	var acc := ACCEL if on_floor else AIR_ACCEL
	hv = hv.move_toward(want, acc * delta * maxf(target_speed, 3.0))
	velocity.x = hv.x
	velocity.z = hv.z
	# facing
	if aiming or weapons.is_firing():
		var fy := cam.yaw
		rotation.y = lerp_angle(rotation.y, fy, clampf(delta * 18.0, 0.0, 1.0))
	elif hv.length() > 0.3:
		var ty := atan2(-hv.x, -hv.z)
		rotation.y = lerp_angle(rotation.y, ty, clampf(delta * 10.0, 0.0, 1.0))
	# gravity / jump
	if on_floor:
		if _air_time > 0.35 and _fall_speed > 7.0:
			model.play_oneshot("land")
			if _fall_speed > FALL_DAMAGE_SPEED:
				health.take_damage((_fall_speed - FALL_DAMAGE_SPEED) * 9.0, null)
		_air_time = 0.0
		_fall_speed = 0.0
		state = State.GROUND
		if input_enabled and Input.is_action_just_pressed("jump") and not crouching:
			if not _try_climb():
				velocity.y = JUMP_VELOCITY
				model.play_oneshot("jump")
	else:
		_air_time += delta
		velocity.y -= GRAVITY * delta
		_fall_speed = maxf(_fall_speed, -velocity.y)
		if _air_time > 0.25:
			state = State.AIR
	move_and_slide()
	move_speed = Vector2(velocity.x, velocity.z).length()
	# animation
	if state == State.AIR and _air_time > 0.45:
		model.set_mode("fall")
	else:
		model.set_mode("crouch" if crouching else "ground")
	model.set_locomotion(move_speed)
	var ranged := weapons.current_is_ranged()
	var up := "" if not ranged else weapons.upper_anim()
	model.set_upper(up, 1.0 if (aiming or weapons.is_firing()) and ranged else (0.35 if ranged else 0.0))
	model.set_aim_pitch(cam.pitch * 0.85 if aiming else 0.0)
	if cam:
		cam.aiming = aiming
		cam.sprinting = sprinting
	_footsteps(delta)
	# water
	if global_position.y < GameWorld.instance.sea_level() - 1.05 if GameWorld.instance else false:
		_enter_swim()
	if input_enabled:
		weapons.handle_input(aiming)
		if Input.is_action_just_pressed("vehicle_enter"):
			_try_enter_vehicle()


func _set_crouch(c: bool) -> void:
	crouching = c
	_capsule.height = 1.25 if c else 1.8
	_col.position.y = _capsule.height * 0.5


func _try_climb() -> bool:
	## Jump pressed facing a wall with a ledge 0.9 - 2.3 m above the feet -> climb.
	var space := get_world_3d().direct_space_state
	var fwd := -global_basis.z
	var q := PhysicsRayQueryParameters3D.create(global_position + Vector3(0, 0.6, 0), global_position + Vector3(0, 0.6, 0) + fwd * 0.9)
	q.exclude = [get_rid()]
	var wall := space.intersect_ray(q)
	if wall.is_empty():
		return false
	var top_probe: Vector3 = wall["position"] + fwd * 0.35 + Vector3(0, 2.6, 0)
	var q2 := PhysicsRayQueryParameters3D.create(top_probe, top_probe - Vector3(0, 2.4, 0))
	q2.exclude = [get_rid()]
	var top := space.intersect_ray(q2)
	if top.is_empty():
		return false
	var h: float = top["position"].y - global_position.y
	if h < 0.8 or h > 2.35 or (top["normal"] as Vector3).y < 0.8:
		return false
	# enough head room on top?
	var q3 := PhysicsRayQueryParameters3D.create(top["position"] + Vector3(0, 0.1, 0), top["position"] + Vector3(0, 1.8, 0))
	q3.exclude = [get_rid()]
	if not space.intersect_ray(q3).is_empty():
		return false
	state = State.CLIMB
	_climb_target = top["position"] + fwd * 0.3
	_climb_t = 0.0
	velocity = Vector3.ZERO
	model.play_oneshot("climb")
	return true


func _move_climb(delta: float) -> void:
	_climb_t += delta / 1.0
	var start := global_position
	if _climb_t < 0.55:
		global_position = start.lerp(Vector3(start.x, _climb_target.y, start.z), clampf(delta * 5.0, 0.0, 1.0))
	else:
		global_position = global_position.lerp(_climb_target, clampf(delta * 6.0, 0.0, 1.0))
	if _climb_t >= 1.05:
		global_position = _climb_target + Vector3(0, 0.05, 0)
		state = State.GROUND


# ------------------------------------------------------------------ swimming
func _enter_swim() -> void:
	state = State.SWIM
	_set_crouch(false)
	model.set_mode("swim")
	weapons.holster()


func _move_swim(delta: float) -> void:
	var sea := GameWorld.instance.sea_level() if GameWorld.instance else -1.5
	var dir := _input_dir()
	var fast := Input.is_action_pressed("sprint")
	var spd := SWIM_SPEED * (1.5 if fast else 1.0)
	var hv := Vector3(velocity.x, 0, velocity.z).move_toward(dir * spd, 4.0 * delta)
	velocity.x = hv.x
	velocity.z = hv.z
	var target_y := sea - 1.25
	velocity.y = (target_y - global_position.y) * 3.0
	if hv.length() > 0.2:
		rotation.y = lerp_angle(rotation.y, atan2(-hv.x, -hv.z), clampf(delta * 5.0, 0.0, 1.0))
	move_and_slide()
	model.set_locomotion(hv.length())
	model.set_upper("", 0.0)
	# leave the water when the ground rises
	if is_on_floor() and global_position.y > sea - 1.0:
		state = State.GROUND
		model.set_mode("ground")
	elif global_position.y > sea - 0.6:
		var q := PhysicsRayQueryParameters3D.create(global_position + Vector3.UP, global_position + Vector3.DOWN * 0.6)
		q.exclude = [get_rid()]
		if not get_world_3d().direct_space_state.intersect_ray(q).is_empty():
			state = State.GROUND
			model.set_mode("ground")


# ------------------------------------------------------------------ vehicles
func _try_enter_vehicle() -> void:
	var best: Node3D = null
	var bd := 4.5
	for v in get_tree().get_nodes_in_group("vehicles"):
		var n := v as Node3D
		if n == null or not n.has_method("get_entry_point"):
			continue
		var d := global_position.distance_to(n.get_entry_point())
		if d < bd and n.get("destroyed") != true:
			bd = d
			best = n
	if best:
		enter_vehicle(best)


func enter_vehicle(v: Node3D) -> void:
	vehicle = v
	state = State.VEHICLE
	velocity = Vector3.ZERO
	weapons.holster()
	_set_crouch(false)
	_col.disabled = true
	var had_driver: Node = v.call("eject_driver", self)
	if had_driver:
		Events.vehicle_stolen.emit(v, self)
		Events.crime_committed.emit("carjack", v.global_position, 2, self)
	elif v.get("owner_npc") == null and v.get("player_owned") != true and v.get("is_parked") == true:
		Events.crime_committed.emit("vehicle_theft", v.global_position, 1, self)
	v.call("set_driver", self)
	reparent(v)
	transform = v.call("driver_seat_transform")
	model.set_mode("drive")
	_enter_timer = 0.6
	if cam:
		cam.set_vehicle(v)
	Events.player_entered_vehicle.emit(v)


func exit_vehicle() -> void:
	if vehicle == null:
		return
	var v := vehicle
	var exit_pos: Vector3 = v.call("get_exit_point")
	v.call("set_driver", null)
	vehicle = null
	reparent(GameWorld.instance if GameWorld.instance else get_tree().current_scene)
	global_position = exit_pos
	rotation = Vector3(0, v.global_rotation.y, 0)
	_col.disabled = false
	state = State.GROUND
	model.set_mode("ground")
	model.play_oneshot("exit_vehicle")
	if cam:
		cam.set_vehicle(null)
	Events.player_exited_vehicle.emit(v)


func _vehicle_update(delta: float) -> void:
	if vehicle == null or not is_instance_valid(vehicle):
		state = State.GROUND
		_col.disabled = false
		return
	transform = vehicle.call("driver_seat_transform")
	_enter_timer -= delta
	if input_enabled and _enter_timer <= 0.0 and Input.is_action_just_pressed("vehicle_enter"):
		var spd: float = vehicle.get("speed_kmh")
		if absf(spd) < 25.0:
			exit_vehicle()
	# drive-by: aiming from the car
	if input_enabled:
		weapons.handle_vehicle_input()


# ------------------------------------------------------------------ interaction
func _update_interaction(delta: float) -> void:
	_prompt_timer -= delta
	if _prompt_timer > 0.0:
		return
	_prompt_timer = 0.15
	if state != State.GROUND:
		if _interact_target:
			_interact_target = null
			Events.interaction_prompt.emit("")
		return
	var best: Node = null
	var bd := 2.6
	for n in get_tree().get_nodes_in_group("interactable"):
		var n3 := n as Node3D
		if n3 == null or not n3.is_visible_in_tree():
			continue
		var d := global_position.distance_to(n3.global_position)
		var r: float = n3.get("interact_radius") if n3.get("interact_radius") else 2.6
		if d < minf(bd, r) and n.call("can_interact", self):
			bd = d
			best = n
	if best != _interact_target:
		_interact_target = best
		Events.interaction_prompt.emit(best.call("get_prompt", self) if best else "")


func _unhandled_input(event: InputEvent) -> void:
	if not input_enabled:
		return
	if event.is_action_pressed("interact") and _interact_target and state == State.GROUND:
		_interact_target.call("interact", self)
	elif event.is_action_pressed("camera_mode") and cam:
		cam.first_person = not cam.first_person
	elif event.is_action_pressed("look_behind") and cam:
		cam.look_behind = true
	elif event.is_action_released("look_behind") and cam:
		cam.look_behind = false


# ------------------------------------------------------------------ footsteps
func _footsteps(delta: float) -> void:
	if state != State.GROUND or move_speed < 0.6:
		_step_timer = 0.0
		return
	_step_timer -= delta
	if _step_timer <= 0.0:
		_step_timer = clampf(1.4 / maxf(move_speed, 1.0), 0.26, 0.6)
		surface = _ground_surface()
		AudioManager.play_footstep(global_position, surface, move_speed)


func _ground_surface() -> String:
	var q := PhysicsRayQueryParameters3D.create(global_position + Vector3.UP * 0.3, global_position + Vector3.DOWN * 0.5)
	q.exclude = [get_rid()]
	var r := get_world_3d().direct_space_state.intersect_ray(q)
	if r.is_empty():
		return "concrete"
	var c: Object = r["collider"]
	if c and c.has_meta("surface"):
		return String(c.get_meta("surface"))
	return "concrete"


# ------------------------------------------------------------------ damage / death
func _on_damaged(amount: float, source: Node, _pos: Vector3, _dir: Vector3) -> void:
	Events.player_damaged.emit(amount, source)
	if cam:
		cam.add_shake(minf(amount / 30.0, 0.6))
	if state == State.GROUND and amount > 8.0:
		model.play_oneshot("hit_react")


func _on_died(source: Node) -> void:
	if state == State.VEHICLE:
		exit_vehicle()
	state = State.DEAD
	input_enabled = false
	weapons.holster()
	var dir := Vector3.ZERO
	if source is Node3D:
		dir = (global_position - (source as Node3D).global_position).normalized()
	model.start_ragdoll(dir * 60.0 + Vector3.UP * 20.0)
	Events.player_died.emit()


func arrest() -> void:
	if state == State.DEAD or state == State.BUSTED:
		return
	if state == State.VEHICLE:
		exit_vehicle()
	state = State.BUSTED
	input_enabled = false
	weapons.holster()
	model.play_loop("hands_up")
	Events.player_busted.emit()


func respawn(pos: Vector3, yaw := 0.0) -> void:
	model.stop_ragdoll()
	if vehicle:
		exit_vehicle()
	global_position = pos
	rotation = Vector3(0, yaw, 0)
	velocity = Vector3.ZERO
	health.revive(1.0)
	state = State.GROUND
	input_enabled = true
	model.set_mode("ground")
	if cam:
		cam.yaw = yaw
	Events.player_respawned.emit("respawn")


func is_in_vehicle() -> bool:
	return state == State.VEHICLE


func is_armed() -> bool:
	return weapons.current_is_ranged() or weapons.current_id() != "unarmed"


func teleport(p: Vector3) -> void:
	if vehicle:
		vehicle.global_position = p + Vector3.UP
		vehicle.set("linear_velocity", Vector3.ZERO)
	else:
		global_position = p
		velocity = Vector3.ZERO
