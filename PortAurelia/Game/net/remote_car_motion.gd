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
var _offset_target := 0.0
var _gap := 100.0
var _interp := 160.0            # render delay, eased so that update-rate changes never jump
var _flags := 0
var _driver: CharacterModel
var _drv_t := 0.0
var hidden_until_smooth := false
var debug_jumps := 0
var _prev_rt := 0.0
var _rt := 0.0   # a moving car appears only once its motion is interpolated


func push(ms: int, pos: Vector3, q: Quaternion, steer: float, kmh: float, flags: int) -> void:
	_delays.append(float(Time.get_ticks_msec() - ms))
	if _delays.size() > 40:
		_delays.pop_front()
	_offset_target = _delays.min()
	if _delays.size() == 1:
		_offset = _offset_target
	if not _snaps.is_empty():
		if ms <= int(_snaps[-1][0]):
			return
		_gap = lerpf(_gap, float(ms - int(_snaps[-1][0])), 0.3)
	_snaps.append([ms, pos, q.normalized(), steer, kmh, flags])
	while _snaps.size() > 24:
		_snaps.pop_front()
	if _snaps.size() == 1:
		car.global_transform = Transform3D(Basis(q.normalized()), pos)
	_flags = flags


func _physics_process(delta: float) -> void:
	if car == null or _snaps.is_empty():
		return
	var target_interp := clampf(_gap * 1.6, 110.0, 900.0)
	# the delay grows quickly (never run out of updates) and shrinks slowly
	_interp = move_toward(_interp, target_interp, delta * (400.0 if target_interp > _interp else 100.0))
	_offset = move_toward(_offset, _offset_target, delta * 100.0)   # clock estimate settles smoothly
	# own render clock: it runs a little slower or faster to stay at the target delay, and slows
	# down when an update is late - so the car never freezes and then jumps
	var target_rt := float(Time.get_ticks_msec()) - _offset - _interp
	var last_ms := float(_snaps[-1][0])
	if _rt == 0.0 or absf(target_rt - _rt) > 2500.0:
		_rt = target_rt
	else:
		var rate := clampf(1.0 + (target_rt - _rt) / 600.0, 0.6, 1.4)
		if _rt > last_ms:
			rate = minf(rate, 0.35)
		_rt += delta * 1000.0 * rate
	var rt := _rt
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
		# newest update is late: continue a little along the last motion
		var p0: Array = _snaps[-2]
		var dt := float(int(a[0]) - int(p0[0]))
		var ahead := minf(rt - float(a[0]), 300.0)
		pos = a[1]
		if dt > 0.0:
			pos += ((a[1] as Vector3) - (p0[1] as Vector3)) / dt * ahead
		q = a[2]
	else:
		pos = a[1]
		q = a[2]
	if OS.has_environment("HH_NET_DEBUG") and car.visible:
		# speed along the rendered (host time) path: a jump shows up as an impossible speed
		var drt := rt - _prev_rt
		var step := car.global_position.distance_to(pos)
		if step > 0.8 and (drt <= 0.5 or step / (drt / 1000.0) > 70.0):
			debug_jumps += 1
			print("NETDBG car path jump %.1f m in %.0f ms host time (interp %.0f gap %.0f snaps %d seg %.1f)" % [step, drt, _interp, _gap,
				_snaps.size(), (a[1] as Vector3).distance_to(b[1])])
	_prev_rt = rt
	# a late update that changed the path is blended in over a few frames instead of popping
	if car.visible and car.global_position.distance_to(pos) < 30.0 and car.global_position.y > -300.0:
		var k := 1.0 - exp(-delta * 16.0)
		pos = car.global_position.lerp(pos, k)
		q = car.global_basis.get_rotation_quaternion().slerp(q, k)
	if hidden_until_smooth and b[0] != a[0]:
		hidden_until_smooth = false
		car.visible = true
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
