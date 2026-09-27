class_name RaceDriver
extends Node
## AI racer for street races. Follows the race line (dense points along the route) with
## pure-pursuit steering, plans its speed from the curvature ahead (brakes before corners
## instead of in them), steers around cars in its way, rubber-bands a little to keep races
## close, and gets itself unstuck (reverse, then reset onto the track if flipped or lost).

var vehicle: Vehicle
var path := PackedVector3Array()
var idx := 0
var skill := 1.0             # cornering / top speed factor (0.9 .. 1.1)
var holding := true          # true on the grid until the start
var finished := false
var rival: Node3D = null     # the player, for rubber banding
var rival_idx := 0           # player's progress index (set by the race)
var _stuck := 0.0
var _reverse := 0.0
var _unstuck_tries := 0
var _dodge := 0.0            # lateral offset (m) to pass an obstacle
var _dodge_t := 0.0
var _flip_t := 0.0
var resets := 0
var _prog_idx := 0
var _prog_t := 0.0
var _block_dist := INF       # distance to a car ahead that cannot be passed


func setup(v: Vehicle, line: PackedVector3Array, s := 1.0) -> void:
	vehicle = v
	path = line
	skill = s
	v.ai_driver = self
	v.auto_drift = false
	idx = _closest(v.global_position, 0, path.size())


func on_vehicle_attacked(_source: Node) -> void:
	pass


func on_vehicle_destroyed() -> void:
	finished = true


func _closest(p: Vector3, from: int, to: int) -> int:
	var best := from
	var bd := INF
	for i in range(maxi(from, 0), mini(to, path.size())):
		var d := path[i].distance_squared_to(p)
		if d < bd:
			bd = d
			best = i
	return best


## Point `dist` metres ahead along the line from index i.
func point_ahead(i: int, dist: float) -> Vector3:
	var left := dist
	var k := clampi(i, 0, path.size() - 1)
	while k < path.size() - 1:
		var seg := path[k].distance_to(path[k + 1])
		if seg >= left:
			return path[k].lerp(path[k + 1], left / maxf(seg, 0.001))
		left -= seg
		k += 1
	return path[path.size() - 1]


## Highest speed that still lets the car make every corner within braking distance.
func _planned_speed(spd: float) -> float:
	var grip := float(vehicle.def.get("grip", 1.0)) * (1.0 + float(vehicle.def.get("downforce", 0.4)) * 0.08)
	var a_lat := 6.2 * grip * skill
	var a_brake := 6.5 * grip
	var top := float(vehicle.def["top"]) * 0.97 * skill
	var best := top
	var scan := spd * spd / (2.0 * a_brake) + 30.0
	var d := 0.0
	var k := idx
	while k < path.size() - 4 and d < scan:
		# heading change over three segments (~24 m) catches corners spread over several points
		var a := path[k + 1] - path[k]
		var b := path[k + 4] - path[k + 3]
		a.y = 0.0
		b.y = 0.0
		var span := 0.0
		for j in range(k, k + 4):
			span += path[j].distance_to(path[j + 1])
		d += a.length()
		var ang := a.angle_to(b)
		if ang > 0.05:
			var r := (span * 0.75) / ang
			var v_corner := maxf(sqrt(a_lat * r), 7.0)
			best = minf(best, sqrt(v_corner * v_corner + 2.0 * a_brake * maxf(d - 6.0, 0.0)))
		k += 1
	return best


func _physics_process(delta: float) -> void:
	if vehicle == null or not is_instance_valid(vehicle) or vehicle.destroyed:
		return
	vehicle.drift_input = false
	if holding or finished or path.size() < 3:
		vehicle.throttle = 0.0
		vehicle.brake_input = 1.0
		vehicle.handbrake = true
		vehicle.steer_input = 0.0
		if finished and vehicle.speed() > 1.0:
			vehicle.handbrake = false
		return
	vehicle.handbrake = false
	var pos := vehicle.global_position
	idx = _closest(pos, idx - 2, idx + 14)
	var spd := vehicle.speed()
	# steering towards a look-ahead point, shifted sideways while passing
	var look := clampf(5.0 + spd * 0.38, 6.0, 30.0)
	var target := point_ahead(idx, look)
	var fwd_line := (point_ahead(idx, look + 4.0) - target).normalized()
	var side := fwd_line.cross(Vector3.UP).normalized()
	_avoid(delta, spd)
	target += side * _dodge
	var local := vehicle.global_transform.affine_inverse() * target
	var steer_ang := atan2(-local.x, -local.z)
	var max_steer := deg_to_rad(float(vehicle.def["steer"]))
	if _reverse > 0.0:
		_reverse -= delta
		vehicle.throttle = -0.7
		vehicle.brake_input = 0.0
		vehicle.steer_input = -clampf(steer_ang / max_steer * 1.5, -1.0, 1.0)
		return
	vehicle.steer_input = clampf(steer_ang / max_steer * 1.6, -1.0, 1.0)
	var want := _planned_speed(spd)
	if _block_dist < INF:
		want = minf(want, maxf(_block_dist - 4.0, 0.0) * 0.9 + 3.0)   # boxed in: follow, don't ram
	# rubber band: ease off when far ahead, dig deeper when far behind
	var gap := idx - rival_idx
	var boost := 0
	if rival and is_instance_valid(rival):
		if gap < -25:
			boost = 3
			want *= 1.06
		elif gap < -10:
			boost = 1
		elif gap > 30:
			want *= 0.9
	vehicle.upgrades["engine"] = boost
	var err := want - spd
	if err > 0.5:
		vehicle.throttle = clampf(err * 0.5, 0.3, 1.0)
		vehicle.brake_input = 0.0
	elif err < -1.5:
		vehicle.throttle = 0.0
		vehicle.brake_input = clampf(-err * 0.3, 0.3, 1.0)
	else:
		vehicle.throttle = 0.45
		vehicle.brake_input = 0.0
	# unstuck / reset: judged by progress along the line, not just speed (a car grinding
	# along a wall or pushing another car is "moving" but not getting anywhere)
	if idx > _prog_idx:
		_prog_idx = idx
		_prog_t = 0.0
		_unstuck_tries = 0
	else:
		_prog_t += delta
	if spd < 2.0:
		_stuck += delta
	else:
		_stuck = maxf(0.0, _stuck - delta)
	if _stuck > 1.6 or (_prog_t > 3.0 and _reverse <= 0.0 and _unstuck_tries == 0):
		_stuck = 0.0
		_unstuck_tries += 1
		_reverse = 1.3
	if _prog_t > 6.5:
		_unstuck_tries = 2
	if vehicle.global_basis.y.dot(Vector3.UP) < 0.4:
		_flip_t += delta
	else:
		_flip_t = 0.0
	if _flip_t > 2.0 or _unstuck_tries >= 2 or pos.distance_to(path[idx]) > 35.0:
		reset_on_track()


## Put the car back onto the race line (flipped, wedged or lost).
func reset_on_track() -> void:
	_flip_t = 0.0
	_unstuck_tries = 0
	_stuck = 0.0
	resets += 1
	# first free spot a little further along the line (past whatever blocked us)
	var space := vehicle.get_world_3d().direct_space_state
	var shape := BoxShape3D.new()
	shape.size = Vector3(2.6, 1.4, 5.5)
	var i := clampi(idx + 2, 0, path.size() - 2)
	for k in range(idx + 2, mini(idx + 16, path.size() - 1)):
		var q := PhysicsShapeQueryParameters3D.new()
		q.shape = shape
		var dk := path[k + 1] - path[k]
		dk.y = 0.0
		q.transform = Transform3D(Basis.looking_at(dk.normalized(), Vector3.UP), path[k] + Vector3.UP * 1.0)
		q.collision_mask = 1 << 2
		q.exclude = [vehicle.get_rid()]
		if space.intersect_shape(q, 1).is_empty():
			i = k
			break
	idx = i
	_prog_idx = i
	_prog_t = 0.0
	var p := path[i]
	var dir := (path[i + 1] - p)
	dir.y = 0.0
	vehicle.linear_velocity = Vector3.ZERO
	vehicle.angular_velocity = Vector3.ZERO
	vehicle.global_transform = Transform3D(Basis.looking_at(dir.normalized(), Vector3.UP), p + Vector3.UP * 0.8)


## Pass slower cars: probe ahead and pick the free side.
func _avoid(delta: float, spd: float) -> void:
	_dodge_t -= delta
	if _dodge_t > 0.0:
		return
	_dodge_t = 0.15
	var space := vehicle.get_world_3d().direct_space_state
	var fwd := -vehicle.global_basis.z
	var from := vehicle.global_position + Vector3.UP * 0.6 + fwd * 2.6
	var reach := clampf(spd * 1.4, 10.0, 45.0)
	var hit := _ray_dist(space, from, from + fwd * reach)
	_block_dist = INF
	if hit == INF:
		_dodge = move_toward(_dodge, 0.0, 1.2)
		return
	var right := vehicle.global_basis.x
	var r_free := not _ray(space, from + right * 2.8, from + right * 2.8 + fwd * reach)
	var l_free := not _ray(space, from - right * 2.8, from - right * 2.8 + fwd * reach)
	if r_free and (not l_free or _dodge >= 0.0):
		_dodge = 3.0
	elif l_free:
		_dodge = -3.0
	else:
		_block_dist = hit


func _ray(space: PhysicsDirectSpaceState3D, a: Vector3, b: Vector3) -> bool:
	return _ray_dist(space, a, b) != INF


func _ray_dist(space: PhysicsDirectSpaceState3D, a: Vector3, b: Vector3) -> float:
	var q := PhysicsRayQueryParameters3D.create(a, b)
	q.exclude = [vehicle.get_rid()]
	q.collision_mask = 1 << 2 | 1 << 1
	var r := space.intersect_ray(q)
	return INF if r.is_empty() else a.distance_to(r["position"])
