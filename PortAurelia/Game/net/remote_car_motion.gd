class_name RemoteCarMotion
extends Node
## Client: smooth movement of a car that the host simulates (shared traffic, mission cars).
## Same snapshot interpolation as the players, with a delay that adapts to how often this car
## is updated (far cars get fewer updates).

var car: Vehicle
var driver_outfit := {}
var _snaps: Array = []          # [ms, pos, quat, steer, kmh, flags]
var _delays: Array = []
var _offset := 0.0
var _gap := 100.0
var _flags := 0
var _driver: CharacterModel
var _drv_t := 0.0


func push(ms: int, pos: Vector3, q: Quaternion, steer: float, kmh: float, flags: int) -> void:
	_delays.append(float(Time.get_ticks_msec() - ms))
	if _delays.size() > 40:
		_delays.pop_front()
	_offset = _delays.min()
	if not _snaps.is_empty():
		if ms <= int(_snaps[-1][0]):
			return
		_gap = lerpf(_gap, float(ms - int(_snaps[-1][0])), 0.3)
	_snaps.append([ms, pos, q.normalized(), steer, kmh, flags])
	while _snaps.size() > 24:
		_snaps.pop_front()
	if _snaps.size() == 1 or car.global_position.distance_to(pos) > 25.0:
		car.global_transform = Transform3D(Basis(q.normalized()), pos)
	_flags = flags


func _physics_process(delta: float) -> void:
	if car == null or _snaps.is_empty():
		return
	var interp := clampf(_gap * 1.6, 110.0, 900.0)
	var rt := float(Time.get_ticks_msec()) - _offset - interp
	var a: Array = _snaps[0]
	var b: Array = _snaps[0]
	for i in _snaps.size():
		if float(_snaps[i][0]) <= rt:
			a = _snaps[i]
			b = _snaps[mini(i + 1, _snaps.size() - 1)]
	var pos: Vector3
	var q: Quaternion
	if b[0] != a[0]:
		var t := clampf((rt - float(a[0])) / float(int(b[0]) - int(a[0])), 0.0, 1.0)
		pos = (a[1] as Vector3).lerp(b[1], t)
		q = (a[2] as Quaternion).slerp(b[2], t)
	elif _snaps.size() >= 2 and rt > float(a[0]):
		# newest snapshot is late: continue a little along the last motion
		var p0: Array = _snaps[-2]
		var dt := float(int(a[0]) - int(p0[0]))
		var ahead := minf(rt - float(a[0]), 250.0)
		pos = a[1]
		if dt > 0.0:
			pos += ((a[1] as Vector3) - (p0[1] as Vector3)) / dt * ahead
		q = a[2]
	else:
		pos = a[1]
		q = a[2]
	car.global_transform = Transform3D(Basis(q), pos)
	car.net_visual(float(a[3]), float(a[4]), int(a[5]), delta)
	_drv_t -= delta
	if _drv_t <= 0.0:
		_drv_t = 0.5
		_update_driver()


## A visible driver behind the wheel when the car is driven and close by.
func _update_driver() -> void:
	var cam := get_viewport().get_camera_3d()
	var want := not driver_outfit.is_empty() and (_flags & 16) != 0 and not car.destroyed and cam != null \
		and cam.global_position.distance_to(car.global_position) < 70.0
	if want and _driver == null:
		_driver = CharacterModel.new()
		_driver.prune_hidden = true
		_driver.outfit = driver_outfit
		car.add_child(_driver)
		_driver.transform = car.driver_seat_transform()
		_driver.set_mode("drive")
	elif not want and _driver != null:
		_driver.queue_free()
		_driver = null
