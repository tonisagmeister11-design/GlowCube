class_name DebugFlyCamera
extends Camera3D
## Freie Flugkamera, solange kein Spieler existiert (oder zum Debuggen).
## Rechte Maustaste halten: Umsehen. WASD/Pfeile: Bewegen, Shift: schneller,
## Q/E: runter/hoch, Mausrad: Tempo.

var speed := 12.0
var yaw := 0.0
var pitch := -0.1
var _looking := false


func _ready() -> void:
	far = 6000.0
	fov = 65.0
	yaw = rotation.y
	pitch = rotation.x


func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseButton:
		if event.button_index == MOUSE_BUTTON_RIGHT:
			_looking = event.pressed
			Input.mouse_mode = Input.MOUSE_MODE_CAPTURED if _looking else Input.MOUSE_MODE_VISIBLE
		elif event.button_index == MOUSE_BUTTON_WHEEL_UP and event.pressed:
			speed = minf(speed * 1.25, 400.0)
		elif event.button_index == MOUSE_BUTTON_WHEEL_DOWN and event.pressed:
			speed = maxf(speed / 1.25, 2.0)
	elif event is InputEventMouseMotion and _looking:
		var sens := 0.0025
		yaw -= event.relative.x * sens
		pitch = clampf(pitch - event.relative.y * sens, -1.5, 1.5)
		rotation = Vector3(pitch, yaw, 0.0)


func _process(delta: float) -> void:
	var dir := Vector3.ZERO
	if _pressed("move_forward"):
		dir -= global_transform.basis.z
	if _pressed("move_back"):
		dir += global_transform.basis.z
	if _pressed("move_left"):
		dir -= global_transform.basis.x
	if _pressed("move_right"):
		dir += global_transform.basis.x
	if Input.is_key_pressed(KEY_E):
		dir += Vector3.UP
	if Input.is_key_pressed(KEY_Q):
		dir -= Vector3.UP
	var s := speed * (4.0 if Input.is_key_pressed(KEY_SHIFT) else 1.0)
	if dir.length_squared() > 0.0:
		global_position += dir.normalized() * s * delta
	global_position.y = maxf(global_position.y, 0.5)


func _pressed(action: String) -> bool:
	return InputMap.has_action(action) and Input.is_action_pressed(action)


## Gleiche Schnittstelle wie der Spieler.
func teleport(pos: Vector3, y := 0.0) -> void:
	global_position = pos + Vector3(0, 1.7, 0)
	yaw = y
	rotation = Vector3(pitch, yaw, 0.0)
