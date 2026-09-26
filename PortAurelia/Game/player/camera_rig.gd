class_name CameraRig
extends Node3D
## Third-person camera: smooth follow, collision (spring arm), aiming zoom with
## shoulder offset, sprint behaviour, vehicle cameras (near / far chase, hood, cockpit),
## first-person mode, look-behind and camera shake. Views, distance, height, shake and
## auto-centring come from the "camera" settings; V cycles the views in game.

const PITCH_MIN := deg_to_rad(-70.0)
const PITCH_MAX := deg_to_rad(60.0)

var target: Node3D
var vehicle: Node3D
var yaw := 0.0
var pitch := deg_to_rad(-12.0)
var aiming := false
var sprinting := false
var first_person := false
var look_behind := false
var locked := false
var shake := 0.0
var scoped := false                 # sniper scope: first-person zoom
var scope_fov := 20.0

var camera: Camera3D
var arm: SpringArm3D
var pivot: Node3D
var _arm_len := 3.4
var _fov := 70.0
var _offset := Vector3.ZERO
var _idle_input := 0.0
var _mouse_delta := Vector2.ZERO
var vehicle_view := 0               # 0 chase near, 1 chase far, 2 hood, 3 cockpit
var _look_off := Vector2.ZERO       # free look offset (yaw, pitch) in hood / cockpit views
var _fp_state := false
var _bob := 0.0


func _ready() -> void:
	pivot = Node3D.new()
	add_child(pivot)
	arm = SpringArm3D.new()
	arm.spring_length = 3.4
	arm.margin = 0.2
	arm.collision_mask = 1 | (1 << 2)
	var sph := SphereShape3D.new()
	sph.radius = 0.22
	arm.shape = sph
	pivot.add_child(arm)
	camera = Camera3D.new()
	camera.far = 4500.0
	camera.near = 0.08
	camera.fov = 70.0
	arm.add_child(camera)
	camera.current = true
	Events.explosion.connect(_on_explosion)
	first_person = int(Settings.get_value("camera", "foot_view")) == 1
	vehicle_view = clampi(int(Settings.get_value("camera", "vehicle_view")), 0, 3)
	Settings.changed.connect(_on_setting)


func _on_setting(section: String, key: String) -> void:
	if section != "camera":
		return
	if key == "foot_view":
		first_person = int(Settings.get_value("camera", "foot_view")) == 1
	elif key == "vehicle_view":
		vehicle_view = clampi(int(Settings.get_value("camera", "vehicle_view")), 0, 3)


## V key: on foot third <-> first person, in a vehicle near -> far -> hood -> cockpit.
func cycle_view() -> void:
	if vehicle:
		vehicle_view = (vehicle_view + 1) % 4
		_look_off = Vector2.ZERO
	else:
		first_person = not first_person


func is_first_person() -> bool:
	if vehicle:
		return vehicle_view == 3
	return first_person or scoped


func set_target(t: Node3D) -> void:
	target = t
	if t is CollisionObject3D:
		arm.add_excluded_object((t as CollisionObject3D).get_rid())


func set_vehicle(v: Node3D) -> void:
	if vehicle and vehicle is CollisionObject3D:
		arm.remove_excluded_object((vehicle as CollisionObject3D).get_rid())
	vehicle = v
	if v and v is CollisionObject3D:
		arm.add_excluded_object((v as CollisionObject3D).get_rid())
		yaw = v.global_rotation.y
		pitch = deg_to_rad(-10.0)


func _unhandled_input(event: InputEvent) -> void:
	if locked or Input.mouse_mode != Input.MOUSE_MODE_CAPTURED:
		return
	if event is InputEventMouseMotion:
		_mouse_delta += (event as InputEventMouseMotion).relative
	elif scoped and event is InputEventMouseButton and event.pressed:
		var mb := event as InputEventMouseButton
		if mb.button_index == MOUSE_BUTTON_WHEEL_UP:
			scope_fov = maxf(6.0, scope_fov * 0.85)
		elif mb.button_index == MOUSE_BUTTON_WHEEL_DOWN:
			scope_fov = minf(35.0, scope_fov / 0.85)


func add_shake(amount: float) -> void:
	shake = minf(2.0, shake + amount * float(Settings.get_value("camera", "shake")))


## Explosions shake the camera (and rumble the controller) depending on the distance.
func _on_explosion(pos: Vector3, radius: float, _source) -> void:
	if camera == null:
		return
	var d := camera.global_position.distance_to(pos)
	var k := clampf(1.0 - d / (radius * 7.0), 0.0, 1.0)
	if k <= 0.0:
		return
	add_shake(1.7 * k * k + 0.2)
	var pads := Input.get_connected_joypads()
	if not pads.is_empty() and Settings.get_value("controls", "vibration"):
		Input.start_joy_vibration(pads[0], 0.6 * k, k, 0.7)


func forward_flat() -> Vector3:
	return Vector3(-sin(yaw), 0.0, -cos(yaw))


func aim_direction() -> Vector3:
	return -camera.global_basis.z


func _process(delta: float) -> void:
	if target == null or not is_instance_valid(target):
		return
	var sens: float = Settings.get_value("controls", "mouse_sensitivity") if Settings else 1.0
	var inv := -1.0 if (Settings and Settings.get_value("controls", "invert_y")) else 1.0
	var md := _mouse_delta * 0.0025 * sens * (0.6 if aiming else 1.0) * (scope_fov / 70.0 if scoped else 1.0)
	_mouse_delta = Vector2.ZERO
	var pad := Vector2(Input.get_axis("look_left", "look_right"), Input.get_axis("look_up", "look_down"))
	var pad_sens: float = Settings.get_value("controls", "controller_sensitivity") if Settings else 1.0
	md += pad * delta * 2.6 * pad_sens
	if not locked:
		yaw -= md.x
		pitch = clampf(pitch - md.y * inv, PITCH_MIN, PITCH_MAX)
	if md.length() > 0.0005:
		_idle_input = 0.0
	else:
		_idle_input += delta

	var follow: Node3D = vehicle if vehicle else target
	var base := follow.global_position
	var dist_mul := float(Settings.get_value("camera", "distance"))
	var h_mul := float(Settings.get_value("camera", "height"))
	var arm_len := 3.4 * dist_mul
	var height := 1.55 * lerpf(1.0, h_mul, 0.6)
	var side := 0.35
	var fov: float = Settings.get_value("graphics", "fov") if Settings else 70.0
	var eye_view := false
	if vehicle:
		var size: float = vehicle.get("camera_distance") if vehicle.get("camera_distance") else 6.5
		var spd: float = vehicle.get("speed_kmh") if vehicle.get("speed_kmh") != null else 0.0
		side = 0.0
		if vehicle_view >= 2 and vehicle.has_method("camera_eye"):
			eye_view = true
			arm_len = 0.0
			height = 0.0
			fov += clampf(absf(spd) / 200.0, 0.0, 1.0) * 8.0 + (6.0 if vehicle_view == 3 else 0.0)
		else:
			var far := vehicle_view == 1
			arm_len = size * (1.45 if far else 1.0) * dist_mul
			height = (1.6 + size * (0.16 if far else 0.12)) * h_mul
			# auto-recentre behind the vehicle while driving
			if _idle_input > 1.2 and absf(spd) > 12.0 and Settings.get_value("camera", "auto_center"):
				var target_yaw: float = vehicle.global_rotation.y
				if spd < -5.0:
					target_yaw += PI
				yaw = lerp_angle(yaw, target_yaw, clampf(delta * 2.5, 0.0, 1.0))
				pitch = lerp_angle(pitch, deg_to_rad(-9.0), clampf(delta * 1.5, 0.0, 1.0))
			fov += clampf(absf(spd) / 200.0, 0.0, 1.0) * 12.0
	elif aiming:
		arm_len = 1.55 * lerpf(1.0, dist_mul, 0.4)
		height = 1.6
		side = 0.55
		fov -= 12.0
	elif sprinting:
		arm_len = 3.9 * dist_mul
		fov += 6.0
	if scoped and not vehicle:
		arm_len = 0.0
		side = 0.0
		height = 1.62
		fov = scope_fov
	elif first_person and not vehicle:
		arm_len = 0.0
		side = 0.0
		height = 1.68
		# gentle head bob while walking
		var sp: float = target.get("move_speed") if target.get("move_speed") != null else 0.0
		if Settings.get_value("camera", "head_bob") and sp > 0.5:
			_bob += delta * sp * 1.9
			height += sin(_bob * 2.0) * 0.025 * clampf(sp / 5.0, 0.3, 1.0)
	_arm_len = lerpf(_arm_len, arm_len, clampf(delta * 6.0, 0.0, 1.0)) if not eye_view else 0.0
	_fov = lerpf(_fov, fov, clampf(delta * (14.0 if scoped else 5.0), 0.0, 1.0))
	camera.fov = _fov
	arm.spring_length = _arm_len
	var y := yaw + (PI if look_behind else 0.0)
	if eye_view:
		# hood / cockpit: glued to the car (follows its pitch and roll), free look springs back
		if md.length() > 0.0005:
			_look_off.x = clampf(_look_off.x - md.x, -2.2, 2.2)
			_look_off.y = clampf(_look_off.y - md.y * inv, -0.9, 0.6)
		elif _idle_input > 0.8:
			_look_off = _look_off.lerp(Vector2.ZERO, clampf(delta * 3.0, 0.0, 1.0))
		var vb: Basis = vehicle.global_basis.orthonormalized()
		global_transform = Transform3D(vb, vehicle.to_global(vehicle.call("camera_eye", vehicle_view)))
		pivot.rotation = Vector3(_look_off.y + deg_to_rad(-4.0), _look_off.x + (PI if look_behind else 0.0), 0.0)
		yaw = vehicle.global_rotation.y
		pitch = deg_to_rad(-10.0)
		arm.position = Vector3.ZERO
	else:
		# smooth follow
		global_basis = Basis.IDENTITY
		var desired := base + Vector3(0, height, 0)
		global_position = global_position.lerp(desired, clampf(delta * (14.0 if not vehicle else 20.0), 0.0, 1.0)) \
			if global_position.distance_to(desired) < 8.0 else desired
		pivot.rotation = Vector3(pitch, y, 0.0)
		_offset = _offset.lerp(Vector3(side, 0, 0), clampf(delta * 8.0, 0.0, 1.0))
		arm.position = _offset
	# the player's own head is hidden (shadow only) whenever the camera is inside it
	var fp := is_first_person()
	if fp != _fp_state:
		_fp_state = fp
		var model = target.get("model") if target else null
		if model and model.has_method("set_first_person"):
			model.call("set_first_person", fp)
	if shake > 0.0:
		shake = maxf(0.0, shake - delta * 2.5)
		var t := Time.get_ticks_msec() * 0.05
		camera.h_offset = (sin(t * 1.7) + sin(t * 4.3) * 0.4) * shake * 0.08
		camera.v_offset = (cos(t * 2.3) + cos(t * 5.1) * 0.4) * shake * 0.08
		camera.rotation.z = sin(t * 3.1) * shake * 0.012
	else:
		camera.h_offset = 0.0
		camera.v_offset = 0.0
		camera.rotation.z = 0.0


## Aim ray: returns {position, normal, collider} of what the crosshair points at (or far point).
func aim_hit(max_dist := 300.0, exclude: Array = []) -> Dictionary:
	var from := camera.global_position
	var dir := aim_direction()
	var q := PhysicsRayQueryParameters3D.create(from, from + dir * max_dist)
	q.exclude = exclude
	q.collision_mask = 1 | (1 << 2) | (1 << 3) | (1 << 5)
	var r := get_world_3d().direct_space_state.intersect_ray(q)
	if r.is_empty():
		return {"position": from + dir * max_dist, "normal": -dir, "collider": null}
	return r
