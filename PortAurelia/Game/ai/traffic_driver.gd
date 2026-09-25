class_name TrafficDriver
extends Node
## AI driver for one traffic vehicle.
## Follows the lane graph with pure-pursuit steering, obeys traffic signals and stop
## signs, keeps distance to vehicles and pedestrians ahead, changes lanes when
## blocked, pulls over for sirens, flees from gunfire and can be carjacked.
## When far from the player the vehicle runs in kinematic mode (no physics).

enum Mode { DRIVE, PULL_OVER, FLEE, STUCK, ABANDONED, PARKING, RESPOND, PURSUE, HOLD }

var vehicle: Vehicle
var manager: Node
var graph: RoadGraph
var signals: TrafficSignals
var lane := -1
var s := 0.0
var mode := Mode.DRIVE
var aggression := 1.0
var cruise_factor := 1.0
var npc_outfit := {}
var _next_lane := -1
var _stop_timer := 0.0
var _stopped_at := -1
var _stuck_time := 0.0
var _mode_timer := 0.0
var _honk_timer := 0.0
var _blocked_time := 0.0
var _kin_speed := 0.0
var _reverse_time := 0.0
var target_speed := 0.0
## Emergency / police driving
var route := PackedInt32Array()      # lane ids to follow (RESPOND)
var route_target := Vector3.INF
var pursue_target: Node3D = null      # node to chase directly (PURSUE)
var _reroute_timer := 0.0


func setup(v: Vehicle, m: Node, lane_id: int, s0: float) -> void:
	vehicle = v
	manager = m
	graph = GameWorld.instance.graph
	signals = GameWorld.instance.signals
	lane = lane_id
	s = s0
	aggression = randf_range(0.85, 1.15)
	cruise_factor = randf_range(0.82, 1.0) * (1.08 if v.type_id in ["sports", "supercar"] else 1.0)
	v.ai_driver = self
	_pick_next()


func _lane() -> RoadGraph.Lane:
	return graph.lanes[lane]


func _pick_next() -> void:
	var l := _lane()
	if not route.is_empty():
		var i := route.find(lane)
		if i >= 0 and i + 1 < route.size() and l.next.has(route[i + 1]):
			_next_lane = route[i + 1]
			var rn: RoadGraph.Lane = graph.lanes[_next_lane]
			vehicle.turn_signal = [0, -1, 1][rn.turn] if rn.is_connector else 0
			return
	if l.next.is_empty():
		_next_lane = -1
		return
	# prefer going straight; avoid immediate U-turn style loops
	var options := Array(l.next)
	var weights := []
	for n in options:
		var nl: RoadGraph.Lane = graph.lanes[n]
		var w := 1.0
		if nl.is_connector:
			w = [3.0, 1.0, 1.2][nl.turn]
		weights.append(w)
	var total := 0.0
	for w in weights:
		total += w
	var r := randf() * total
	for i in options.size():
		r -= weights[i]
		if r <= 0.0:
			_next_lane = options[i]
			break
	if _next_lane == -1:
		_next_lane = options[0]
	var nxt: RoadGraph.Lane = graph.lanes[_next_lane]
	vehicle.turn_signal = 0
	if nxt.is_connector:
		vehicle.turn_signal = [0, -1, 1][nxt.turn]


func _advance_lane() -> bool:
	if _next_lane < 0:
		return false
	var over := s - _lane().length
	lane = _next_lane
	s = maxf(over, 0.0)
	_stopped_at = -1
	_pick_next()
	return true


# ------------------------------------------------------------------ main update
func tick(delta: float, near: bool) -> void:
	if vehicle == null or not is_instance_valid(vehicle) or vehicle.destroyed:
		return
	if mode == Mode.ABANDONED:
		return
	if mode == Mode.PURSUE or mode == Mode.HOLD:
		if near:
			_drive_pursue(delta)
		else:
			_follow_route_update(delta)
			target_speed = minf(_lane().speed * 1.7, 34.0)
			_drive_kinematic(delta)
		return
	if mode == Mode.RESPOND:
		_follow_route_update(delta)
	var l := _lane()
	var pos := vehicle.global_position
	if near:
		s = l.closest_s(pos) if not vehicle.kinematic_mode else s
	# advance to the next lane when reaching the end
	if s >= l.length - 0.5:
		if not _advance_lane():
			if mode == Mode.RESPOND or mode == Mode.PURSUE:
				_reroute_timer = 0.0
				s = _lane().length - 0.6
				return
			manager.call("request_despawn", vehicle)
			return
		l = _lane()
	_mode_timer -= delta
	if mode == Mode.FLEE and _mode_timer <= 0.0:
		mode = Mode.DRIVE
		aggression = randf_range(0.9, 1.1)
	vehicle.headlights_on = ShaderGlobals.get_value("night", 0.0) > 0.35
	target_speed = _desired_speed(delta)
	if near:
		_drive_physical(delta)
	else:
		_drive_kinematic(delta)


func _desired_speed(delta: float) -> float:
	var l := _lane()
	var v := l.speed * cruise_factor * aggression
	if mode == Mode.FLEE:
		v = l.speed * 1.5
	elif mode == Mode.RESPOND:
		v = minf(l.speed * 1.7, 34.0)
	# slow down before sharp connectors
	if _next_lane >= 0:
		var nl: RoadGraph.Lane = graph.lanes[_next_lane]
		var dist_end := l.length - s
		if nl.is_connector and nl.turn != 0 and dist_end < 25.0:
			v = minf(v, lerpf(nl.speed, v, clampf((dist_end - 5.0) / 20.0, 0.0, 1.0)))
	# signals / stop signs at the end of this lane
	if not l.is_connector and _next_lane >= 0 and mode != Mode.FLEE and mode != Mode.RESPOND:
		var node: int = l.to_node
		var nd: Dictionary = graph.nodes[node]
		var dist_end := l.length - s
		if nd["control"] == "signal":
			var st := signals.state(node, l.edge)
			if st != TrafficSignals.GREEN:
				var stop_dist := dist_end - 1.0
				# run a yellow when too close to stop comfortably
				if not (st == TrafficSignals.YELLOW_L and stop_dist < _stop_distance(vehicle.speed())):
					v = minf(v, _approach_speed(stop_dist))
		elif nd["control"] == "stop" and _stopped_at != node:
			if dist_end < 2.5:
				_stop_timer += delta
				v = 0.0
				if _stop_timer > 1.2 and not _intersection_busy(node):
					_stopped_at = node
					_stop_timer = 0.0
			else:
				v = minf(v, _approach_speed(dist_end - 1.5))
	if mode == Mode.RESPOND and not l.is_connector and _next_lane >= 0:
		# emergency vehicles slow down through intersections instead of stopping
		if graph.nodes[l.to_node]["control"] != "none" and l.length - s < 20.0:
			v = minf(v, 12.0)
	# vehicles and obstacles ahead
	var gap := _gap_ahead()
	if gap < INF:
		var safe := 4.0 + vehicle.speed() * 1.4
		if gap < safe:
			v = minf(v, maxf(0.0, (gap - 4.0) * 0.9))
			_blocked_time += delta if vehicle.speed() < 1.0 else 0.0
		else:
			_blocked_time = 0.0
	else:
		_blocked_time = 0.0
	if mode == Mode.PULL_OVER:
		v = 0.0 if _mode_timer < 3.5 else 4.0
		if _mode_timer <= 0.0:
			mode = Mode.DRIVE
	return v


func _stop_distance(speed: float) -> float:
	return speed * speed / (2.0 * 5.5) + 2.0


func _approach_speed(dist: float) -> float:
	if dist <= 0.3:
		return 0.0
	return sqrt(2.0 * 4.5 * dist)


func _intersection_busy(node: int) -> bool:
	var p: Vector3 = graph.nodes[node]["pos"]
	for v in manager.call("vehicles_near", p, 14.0):
		if v != vehicle and (v as Vehicle).speed() > 1.0:
			return true
	return false


## Distance to the nearest vehicle/pedestrian/player ahead along our heading.
func _gap_ahead() -> float:
	var pos := vehicle.global_position
	var fwd := -vehicle.global_basis.z
	var best := INF
	var look := 12.0 + vehicle.speed() * 2.2
	for other in manager.call("vehicles_near", pos + fwd * look * 0.5, look * 0.6 + 6.0):
		if other == vehicle:
			continue
		var o := other as Node3D
		var d := o.global_position - pos
		var along := d.dot(fwd)
		if along <= 0.0 or along > look:
			continue
		var lateral := absf(d.dot(vehicle.global_basis.x))
		var width := 2.6 if along > 8.0 else 2.2
		if lateral < width:
			best = minf(best, along - 2.5)
	# pedestrians / player on the road
	var p := GameWorld.instance.player
	if p and not (p as Player).is_in_vehicle() and mode != Mode.RESPOND:
		var d := p.global_position - pos
		var along := d.dot(fwd)
		if along > 0.0 and along < look and absf(d.dot(vehicle.global_basis.x)) < 1.8:
			best = minf(best, along - 2.5)
			if along < 14.0 and _honk_timer <= 0.0:
				vehicle.honk(0.5)
				_honk_timer = 6.0
	var peds = GameWorld.instance.peds
	if peds and peds.has_method("peds_near"):
		for ped in peds.call("peds_near", pos + fwd * 8.0, 10.0):
			var d: Vector3 = (ped as Node3D).global_position - pos
			var along := d.dot(fwd)
			if along > 0.0 and along < look and absf(d.dot(vehicle.global_basis.x)) < 1.6:
				best = minf(best, along - 2.5)
	return best


# ------------------------------------------------------------------ physical driving
func _drive_physical(delta: float) -> void:
	if vehicle.kinematic_mode:
		vehicle.set_kinematic(false)
		vehicle.linear_velocity = -vehicle.global_basis.z * _kin_speed
	var l := _lane()
	var spd := vehicle.speed()
	var look := clampf(5.0 + spd * 0.7, 5.0, 22.0)
	var target := _path_point(s + look)
	if mode == Mode.PULL_OVER:
		target += vehicle.global_basis.x * 2.2
	var local := vehicle.global_transform.affine_inverse() * target
	var steer_ang := atan2(-local.x, -local.z)
	var max_steer := deg_to_rad(float(vehicle.def["steer"]))
	vehicle.steer_input = clampf(steer_ang / max_steer * 1.3, -1.0, 1.0)
	var err := target_speed - spd
	if _reverse_time > 0.0:
		_reverse_time -= delta
		vehicle.throttle = -0.6
		vehicle.brake_input = 0.0
		vehicle.steer_input = -vehicle.steer_input
		return
	if err > 0.5:
		vehicle.throttle = clampf(err * 0.35, 0.15, 1.0) * aggression
		vehicle.brake_input = 0.0
	elif err < -1.0:
		vehicle.throttle = 0.0
		vehicle.brake_input = clampf(-err * 0.25, 0.2, 1.0)
	else:
		vehicle.throttle = 0.1 if target_speed > 0.5 else 0.0
		vehicle.brake_input = 0.0 if target_speed > 0.5 else 0.6
	vehicle.handbrake = target_speed < 0.2 and spd < 0.5
	# stuck handling: off the road or blocked for long
	var lane_pos := l.point_at(s)
	if lane_pos.distance_to(vehicle.global_position) > 10.0:
		_stuck_time += delta
	elif spd < 0.5 and target_speed > 2.0:
		_stuck_time += delta
	else:
		_stuck_time = maxf(0.0, _stuck_time - delta)
	if _stuck_time > 6.0:
		_stuck_time = 0.0
		_reverse_time = 1.5
	if mode == Mode.RESPOND and _blocked_time > 1.5:
		_blocked_time = 0.0
		_try_lane_change()
	if _blocked_time > 7.0 and _honk_timer <= 0.0:
		vehicle.honk(0.8)
		_honk_timer = 8.0
		_try_lane_change()
	_honk_timer -= delta
	if vehicle.global_basis.y.dot(Vector3.UP) < 0.3:
		manager.call("request_despawn", vehicle)


func _path_point(dist: float) -> Vector3:
	var l := _lane()
	if dist <= l.length:
		return l.point_at(dist)
	if _next_lane >= 0:
		var nl: RoadGraph.Lane = graph.lanes[_next_lane]
		return nl.point_at(minf(dist - l.length, nl.length))
	return l.pts[-1]


func _try_lane_change() -> void:
	var l := _lane()
	for side in [l.left, l.right]:
		if side < 0:
			continue
		var nl: RoadGraph.Lane = graph.lanes[side]
		var ns := nl.closest_s(vehicle.global_position) + 10.0
		if ns >= nl.length - 5.0:
			continue
		var p := nl.point_at(ns)
		var free := true
		for v in manager.call("vehicles_near", p, 7.0):
			if v != vehicle:
				free = false
				break
		if free:
			lane = side
			s = ns - 10.0
			_pick_next()
			vehicle.turn_signal = -1 if side == l.left else 1
			return


# ------------------------------------------------------------------ kinematic (far) driving
func _drive_kinematic(delta: float) -> void:
	if not vehicle.kinematic_mode:
		_kin_speed = vehicle.speed()
		vehicle.set_kinematic(true)
	_kin_speed = move_toward(_kin_speed, target_speed, delta * 4.0)
	s += _kin_speed * delta
	var l := _lane()
	if s >= l.length:
		if not _advance_lane():
			if mode == Mode.RESPOND or mode == Mode.PURSUE:
				_reroute_timer = 0.0
				s = _lane().length - 0.6
				return
			manager.call("request_despawn", vehicle)
			return
	var p := _path_point(s)
	var ahead := _path_point(s + 3.0)
	var dir := ahead - p
	if dir.length() > 0.05:
		vehicle.global_transform = Transform3D(Basis.looking_at(dir.normalized(), Vector3.UP), p + Vector3.UP * 0.02)
	vehicle.speed_kmh = _kin_speed * 3.6


# ------------------------------------------------------------------ reactions
func on_siren(from: Vector3) -> void:
	if mode == Mode.DRIVE and from.distance_to(vehicle.global_position) < 45.0:
		var to_me := vehicle.global_position - from
		if to_me.dot(-vehicle.global_basis.z) > 0.0:
			mode = Mode.PULL_OVER
			_mode_timer = 5.0


func on_threat(at: Vector3) -> void:
	if mode == Mode.ABANDONED:
		return
	if vehicle.speed() < 3.0 and randf() < 0.4:
		abandon_vehicle(null)
		return
	mode = Mode.FLEE
	_mode_timer = 20.0
	aggression = 1.4


func on_vehicle_attacked(source: Node) -> void:
	on_threat(vehicle.global_position)


func on_vehicle_destroyed() -> void:
	mode = Mode.ABANDONED


## The driver leaves the car (carjacking or panic). Spawns a fleeing pedestrian.
func abandon_vehicle(by: Node) -> Node:
	mode = Mode.ABANDONED
	vehicle.throttle = 0.0
	vehicle.brake_input = 1.0
	vehicle.ai_driver = null
	vehicle.turn_signal = 0
	var peds = GameWorld.instance.peds
	var npc: Node = null
	if peds and peds.has_method("spawn_fleeing_driver"):
		npc = peds.call("spawn_fleeing_driver", vehicle.get_exit_point(), npc_outfit, by)
	manager.call("release_vehicle", vehicle)
	return npc


# ------------------------------------------------------------------ emergency driving
## Drive to `target` along the lane graph with emergency behaviour (police, ambulance).
func respond_to(target: Vector3) -> void:
	route_target = target
	mode = Mode.RESPOND
	_reroute_timer = 0.0
	_follow_route_update(0.0)


## Chase a node directly (off-lane steering), used by police pursuits.
func pursue(t: Node3D) -> void:
	pursue_target = t
	mode = Mode.PURSUE


func hold() -> void:
	mode = Mode.HOLD
	pursue_target = null


func resume_traffic() -> void:
	mode = Mode.DRIVE
	route = PackedInt32Array()
	pursue_target = null
	var c := graph.closest_lane(vehicle.global_position, 60.0, -vehicle.global_basis.z)
	if not c.is_empty():
		lane = c["lane"]
		s = c["s"]
	_pick_next()


func _follow_route_update(delta: float) -> void:
	_reroute_timer -= delta
	if pursue_target and is_instance_valid(pursue_target):
		route_target = pursue_target.global_position
	if route_target == Vector3.INF:
		return
	if _reroute_timer <= 0.0:
		_reroute_timer = 4.0
		var c := graph.closest_lane(vehicle.global_position, 60.0, -vehicle.global_basis.z)
		if not c.is_empty():
			lane = c["lane"]
			s = c["s"]
		route = graph.route(lane, route_target, 3000)
		_pick_next()
	# switch to a side lane when the route says so
	var i := route.find(lane)
	if i >= 0 and i + 1 < route.size():
		var l := _lane()
		var nxt := route[i + 1]
		if nxt == l.left or nxt == l.right:
			var nl: RoadGraph.Lane = graph.lanes[nxt]
			lane = nxt
			s = minf(nl.closest_s(vehicle.global_position) + 2.0, nl.length - 0.5)
			_pick_next()


func _drive_pursue(delta: float) -> void:
	if vehicle.kinematic_mode:
		vehicle.set_kinematic(false)
		vehicle.linear_velocity = -vehicle.global_basis.z * _kin_speed
	var spd := vehicle.speed()
	if mode == Mode.HOLD or pursue_target == null or not is_instance_valid(pursue_target):
		vehicle.throttle = 0.0
		vehicle.brake_input = 1.0
		vehicle.handbrake = spd < 1.0
		return
	var tp := pursue_target.global_position
	var tv := Vector3.ZERO
	if pursue_target is RigidBody3D:
		tv = (pursue_target as RigidBody3D).linear_velocity
	elif pursue_target is CharacterBody3D:
		tv = (pursue_target as CharacterBody3D).velocity
	var dist := tp.distance_to(vehicle.global_position)
	var lead := clampf(dist / maxf(spd, 5.0), 0.0, 1.5)
	var aim := tp + tv * lead
	# far away: follow roads, close: steer straight at the target
	var target := aim
	if dist > 60.0 and not route.is_empty():
		_follow_route_update(delta)
		s = _lane().closest_s(vehicle.global_position)
		if s >= _lane().length - 0.5:
			_advance_lane()
		target = _path_point(s + clampf(6.0 + spd * 0.7, 6.0, 24.0))
	elif dist > 60.0:
		_follow_route_update(delta)
	var local := vehicle.global_transform.affine_inverse() * target
	var steer_ang := atan2(-local.x, -local.z)
	var max_steer := deg_to_rad(float(vehicle.def["steer"]))
	var want := clampf(tv.length() + dist * 0.6, 6.0, 42.0)
	if dist < 9.0 and tv.length() < 2.0:
		want = 0.0
	# reverse out when the target is behind and close
	if local.z > 3.0 and dist < 14.0 and spd < 4.0:
		_reverse_time = 1.2
	if _reverse_time > 0.0:
		_reverse_time -= delta
		vehicle.throttle = -0.7
		vehicle.brake_input = 0.0
		vehicle.steer_input = -clampf(steer_ang / max_steer, -1.0, 1.0)
		return
	vehicle.steer_input = clampf(steer_ang / max_steer * 1.4, -1.0, 1.0)
	var err := want - spd
	if err > 0.5:
		vehicle.throttle = clampf(err * 0.3, 0.3, 1.0)
		vehicle.brake_input = 0.0
	else:
		vehicle.throttle = 0.0
		vehicle.brake_input = clampf(-err * 0.3, 0.2, 1.0)
	vehicle.handbrake = want < 0.1 and spd < 1.0
	# stuck: back up and try again
	if spd < 0.8 and want > 3.0:
		_stuck_time += delta
		if _stuck_time > 2.5:
			_stuck_time = 0.0
			_reverse_time = 1.4
	else:
		_stuck_time = 0.0
	if vehicle.global_basis.y.dot(Vector3.UP) < 0.3:
		_stuck_time += delta
