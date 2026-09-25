class_name NPC
extends CharacterBody3D
## A pedestrian / non-player character.
##
## State machine:
##   IDLE, WALK (sidewalk graph), WAIT_CROSS (at a crosswalk until the signal allows),
##   TALK, PHONE, SIT, WINDOW (shopping), WORK, JOG,
##   FLEE, PANIC (cowering), HANDS_UP, FIGHT (melee or ranged, uses cover), SEARCH,
##   GOTO (scripted by missions / police), KNOCKED (ragdoll, gets up), DEAD.
##
## Near the player the NPC uses physics (move_and_slide) and full-rate animation;
## far away it slides along its path without collision and animates at low rate.

enum S { IDLE, WALK, WAIT_CROSS, TALK, PHONE, SIT, WINDOW, WORK, JOG, FLEE, PANIC, HANDS_UP, FIGHT, SEARCH, GOTO, KNOCKED, DEAD }

const WALK_SPEED := 1.35
const RUN_SPEED := 4.2
const SPRINT_SPEED := 6.2
const GRAVITY := 17.0
const NEAR_DIST := 55.0

signal arrived
signal died(npc: NPC)

var role := "civilian"          # civilian, business, worker, tourist, jogger, gang, cop, medic, driver
var state := S.IDLE
var model: CharacterModel
var health: Health
var weapons: WeaponHolder
var manager: Node
var outfit := {}
var female := false
var bravery := 0.3              # chance to fight back instead of fleeing
var hostile := false            # attacks the player on sight (gangs when provoked, cops when wanted)
var target: Node3D = null       # combat target
var near := true
var persistent := false         # not despawned by the ped manager (mission / police)
var crime_reported := false
var money := 0

var _graph: PedGraph
var _edge := -1
var _from_node := -1
var _to_node := -1
var _path := PackedVector3Array()
var _path_i := 0
var _offset := 0.0
var _speed := 0.0
var _want_speed := 0.0
var _timer := 0.0
var _think := 0.0
var _flee_from := Vector3.ZERO
var _goal := Vector3.INF
var _goal_run := false
var _anim_accum := 0.0
var _anim_frame := 0
var _cover_pos := Vector3.INF
var _cover_timer := 0.0
var _burst := 0.0
var _burst_pause := 0.0
var _crouched := false
var _sit_xform := Transform3D()
var _last_attacker: Node3D = null
var _assault_reported := false
var _dodge_cd := 0.0
var _scream_cd := 0.0
var _voice_pitch := 1.0
var _stuck := 0.0
var _last_pos := Vector3.ZERO
var _col: CollisionShape3D


func _ready() -> void:
	add_to_group("npc")
	collision_layer = 1 << 3
	collision_mask = 1 | (1 << 1) | (1 << 2) | (1 << 3)
	floor_snap_length = 0.4
	floor_max_angle = deg_to_rad(50.0)
	_col = CollisionShape3D.new()
	var cap := CapsuleShape3D.new()
	cap.radius = 0.3
	cap.height = 1.78
	_col.shape = cap
	_col.position.y = 0.89
	add_child(_col)
	model = CharacterModel.new()
	model.name = "Model"
	model.outfit = outfit
	add_child(model)
	health = Health.new()
	health.name = "Health"
	health.max_health = 100.0 if role in ["cop", "gang"] else 70.0
	health.regen_to = 0.0
	add_child(health)
	health.damaged.connect(_on_damaged)
	health.died.connect(_on_died)
	weapons = WeaponHolder.new()
	weapons.name = "Weapons"
	add_child(weapons)
	weapons.setup(self, model, false)
	weapons.accuracy = 0.55
	female = outfit.get("body", "M") == "F"
	_voice_pitch = randf_range(1.15, 1.4) if female else randf_range(0.85, 1.05)
	_offset = randf_range(-0.7, 0.7)
	_graph = manager.get("ped_graph") if manager else null
	_last_pos = global_position
	if model.tree:
		model.tree.callback_mode_process = AnimationMixer.ANIMATION_CALLBACK_MODE_PROCESS_MANUAL


# ------------------------------------------------------------------ public API
## Start walking the sidewalk network from the closest point.
func start_walking() -> void:
	if _graph == null:
		_set_state(S.IDLE)
		return
	var c := _graph.closest(global_position, 40.0)
	if c.is_empty():
		_set_state(S.IDLE)
		return
	var e: int = c["edge"]
	var forward := randf() < 0.5
	_edge = e
	_from_node = _graph.edge_a[e] if forward else _graph.edge_b[e]
	_to_node = _graph.other_end(e, _from_node)
	_set_path(_graph.oriented_path(e, _from_node))
	# skip waypoints behind us
	var best := 0
	var bd := INF
	for i in _path.size():
		var d := _path[i].distance_to(global_position)
		if d < bd:
			bd = d
			best = i
	_path_i = mini(best + 1, _path.size() - 1)
	_set_state(S.JOG if role == "jogger" else S.WALK)


## Walk/run to a point (missions, police). Emits `arrived`.
func go_to(p: Vector3, run := false) -> void:
	_goal = p
	_goal_run = run
	_set_state(S.GOTO)


func engage(t: Node3D) -> void:
	if state == S.DEAD:
		return
	target = t
	hostile = true
	_set_state(S.FIGHT)


func flee_from(p: Vector3, duration := 14.0) -> void:
	if state in [S.DEAD, S.KNOCKED, S.FIGHT]:
		return
	_flee_from = p
	_timer = duration
	_stand_up()
	if _graph and randf() < 0.8:
		var n := _graph.nearest_node(global_position, 60.0)
		if n >= 0:
			_from_node = n
			_to_node = n
			_path = PackedVector3Array([_graph.node_pos[n]])
			_path_i = 0
	else:
		_path = PackedVector3Array()
	_set_state(S.FLEE)
	_scream()


func cower(duration := 8.0) -> void:
	if state in [S.DEAD, S.KNOCKED, S.FIGHT]:
		return
	_stand_up()
	_timer = duration
	_set_state(S.PANIC)
	_scream()


func hands_up(duration := 6.0) -> void:
	if state in [S.DEAD, S.KNOCKED, S.FIGHT]:
		return
	_stand_up()
	_timer = duration
	_set_state(S.HANDS_UP)


func sit_at(xf: Transform3D, duration := 40.0) -> void:
	_sit_xform = xf
	_timer = duration
	global_transform = xf
	_set_state(S.SIT)


func is_dead() -> bool:
	return state == S.DEAD


func is_busy() -> bool:
	return state in [S.FLEE, S.PANIC, S.HANDS_UP, S.FIGHT, S.KNOCKED, S.DEAD, S.GOTO]


func is_head_hit(p: Vector3) -> bool:
	return state != S.DEAD and not model.ragdolled and p.y > global_position.y + 1.52


## Called by the ped manager when a gun is fired nearby.
func hear_gunshot(pos: Vector3, shooter: Node) -> void:
	if state == S.DEAD or state == S.KNOCKED:
		return
	if role == "gang" and shooter is Player and weapons.has_ranged() and pos.distance_to(global_position) < 40.0:
		engage(shooter as Node3D)
		return
	if role == "cop":
		return
	if state in [S.FLEE, S.PANIC]:
		_timer = maxf(_timer, 8.0)
		return
	var d := pos.distance_to(global_position)
	if d < 12.0 and randf() < 0.35:
		cower(randf_range(5.0, 10.0))
	else:
		flee_from(pos)


## Player points a gun at this NPC.
func threatened_by(p: Node3D) -> void:
	if state in [S.DEAD, S.KNOCKED, S.FIGHT, S.HANDS_UP]:
		return
	if role in ["gang"] and weapons.has_ranged():
		engage(p)
		return
	if role == "cop":
		return
	Events.npc_threatened.emit(self, p)
	if p.global_position.distance_to(global_position) < 10.0 and randf() < 0.7:
		_face(p.global_position)
		hands_up(randf_range(4.0, 8.0))
	else:
		flee_from(p.global_position)


func on_vehicle_impact(v: Node3D, rel_speed: float) -> void:
	if state == S.DEAD or state == S.KNOCKED:
		return
	var dmg := (rel_speed - 3.0) * 9.0
	var dir: Vector3 = (v as RigidBody3D).linear_velocity if v is RigidBody3D else (global_position - v.global_position)
	dir = dir.normalized()
	var drv = v.get("driver")
	var offender: Node = drv if drv is Player else v
	if dmg > 0.0:
		health.take_damage(dmg, offender, global_position + Vector3.UP, dir)
	if state != S.DEAD and rel_speed > 4.0:
		knock_down(dir * rel_speed * 14.0 + Vector3.UP * rel_speed * 6.0)
	if drv is Player and not _assault_reported:
		_assault_reported = true
		Events.crime_committed.emit("hit_pedestrian", global_position, 1, drv)


func knock_down(impulse: Vector3) -> void:
	if state == S.DEAD:
		return
	weapons.holster()
	_set_state(S.KNOCKED)
	_timer = randf_range(2.5, 3.5)
	_col.disabled = true
	model.start_ragdoll(impulse, "Hips")
	AudioManager.play_voice("pain", global_position)


# ------------------------------------------------------------------ state machine
func _set_state(s: int) -> void:
	state = s
	_think = 0.0
	match s:
		S.IDLE:
			_timer = randf_range(3.0, 9.0)
			model.set_mode("ground")
		S.TALK:
			model.play_loop("talk")
		S.PHONE:
			model.play_loop("phone")
		S.SIT:
			model.play_loop("sit")
		S.PANIC:
			model.play_loop("cower")
		S.HANDS_UP:
			model.play_loop("hands_up")
		S.WINDOW, S.WORK:
			model.set_mode("ground")
		_:
			model.set_mode("ground")


func _physics_process(delta: float) -> void:
	if state == S.DEAD:
		return
	var p := GameWorld.instance.player if GameWorld.instance else null
	var dist := p.global_position.distance_to(global_position) if p else 0.0
	near = dist < NEAR_DIST or persistent or state in [S.FIGHT, S.FLEE, S.KNOCKED]
	_dodge_cd -= delta
	_scream_cd -= delta
	_think -= delta
	match state:
		S.IDLE:
			_want_speed = 0.0
			_timer -= delta
			if _timer <= 0.0:
				start_walking()
		S.WALK, S.JOG:
			_walk_graph(delta, WALK_SPEED * randf_range(0.97, 1.03) if state == S.WALK else RUN_SPEED * 0.9)
		S.WAIT_CROSS:
			_want_speed = 0.0
			_timer -= delta
			var ts: TrafficSignals = GameWorld.instance.signals
			var rn := _graph.edge_road_node[_edge]
			var re := _graph.edge_road_edge[_edge]
			if rn < 0 or ts.ped_can_cross(rn, re) or _timer <= 0.0:
				_set_state(S.JOG if role == "jogger" else S.WALK)
		S.TALK, S.PHONE, S.WINDOW, S.WORK, S.SIT:
			_want_speed = 0.0
			_timer -= delta
			if _timer <= 0.0:
				_stand_up()
				start_walking()
		S.FLEE:
			_flee(delta)
		S.PANIC, S.HANDS_UP:
			_want_speed = 0.0
			_timer -= delta
			if _timer <= 0.0:
				if state == S.HANDS_UP and p and p.get("aiming") and _player_aims_at_me(p):
					_timer = 1.5
				else:
					flee_from(p.global_position if p else global_position, 10.0)
		S.FIGHT:
			_fight(delta)
		S.GOTO:
			_goto(delta)
		S.KNOCKED:
			_knocked(delta)
			return
		S.SEARCH:
			_want_speed = 0.0
	_avoid_vehicles(p)
	_move(delta)
	_animate(delta, dist)


func _move(delta: float) -> void:
	var dir := Vector3.ZERO
	if _want_speed > 0.01:
		dir = _steer_dir()
	_speed = move_toward(_speed, _want_speed, delta * 8.0)
	var hv := dir * _speed
	if state in [S.SIT]:
		global_transform = _sit_xform
		return
	if near:
		velocity.x = hv.x
		velocity.z = hv.z
		if is_on_floor():
			velocity.y = -0.5
		else:
			velocity.y -= GRAVITY * delta
		# separation from other pedestrians and the player
		if manager and _speed > 0.3:
			var sep := Vector3.ZERO
			for o in manager.call("peds_near", global_position, 1.3):
				if o != self:
					var d: Vector3 = global_position - (o as Node3D).global_position
					d.y = 0.0
					if d.length() > 0.01:
						sep += d.normalized() * (1.3 - d.length())
			velocity += sep * 2.0
		move_and_slide()
		if global_position.y < -30.0:
			queue_free()
	else:
		global_position += hv * delta
		if _path.size() > 0 and _path_i < _path.size():
			global_position.y = lerpf(global_position.y, _path[_path_i].y - 0.16, clampf(delta * 4.0, 0.0, 1.0))
	if hv.length() > 0.2:
		var ty := atan2(-hv.x, -hv.z)
		rotation.y = lerp_angle(rotation.y, ty, clampf(delta * 8.0, 0.0, 1.0))
	# stuck detection while walking
	if _want_speed > 0.5 and near:
		if global_position.distance_to(_last_pos) < _want_speed * delta * 0.2:
			_stuck += delta
		else:
			_stuck = maxf(0.0, _stuck - delta)
		if _stuck > 2.5:
			_stuck = 0.0
			_on_stuck()
	_last_pos = global_position


func _steer_dir() -> Vector3:
	var tgt := _current_target()
	var d := tgt - global_position
	d.y = 0.0
	if d.length() < 0.01:
		return Vector3.ZERO
	return d.normalized()


func _current_target() -> Vector3:
	if state == S.GOTO:
		return _goal
	if state == S.FIGHT and target:
		return _cover_pos if _cover_pos != Vector3.INF else target.global_position
	if _path_i < _path.size():
		return _path[_path_i]
	return global_position


func _animate(delta: float, dist: float) -> void:
	if model.tree == null:
		return
	if state in [S.WALK, S.JOG, S.FLEE, S.GOTO, S.FIGHT, S.IDLE, S.WAIT_CROSS, S.WINDOW, S.WORK, S.SEARCH]:
		if not _crouched:
			model.set_mode("ground")
		model.set_locomotion(_speed)
	# animation LOD: full rate near, reduced far away
	_anim_accum += delta
	_anim_frame += 1
	var every := 1 if dist < 25.0 else (2 if dist < 60.0 else 4)
	if _anim_frame % every == 0:
		model.tree.advance(_anim_accum)
		_anim_accum = 0.0


# ------------------------------------------------------------------ sidewalk walking
func _set_path(pts: PackedVector3Array) -> void:
	# offset laterally so pedestrians don't walk in a single file
	_path = PackedVector3Array()
	for i in pts.size():
		var a := pts[maxi(i - 1, 0)]
		var b := pts[mini(i + 1, pts.size() - 1)]
		var t := b - a
		t.y = 0.0
		var off := Vector3.ZERO
		if t.length() > 0.01:
			off = t.normalized().cross(Vector3.UP) * _offset
		_path.append(pts[i] + off)
	_path_i = 0


func _walk_graph(delta: float, spd: float) -> void:
	_want_speed = spd
	if _path.is_empty() or _graph == null:
		_set_state(S.IDLE)
		return
	var tgt := _path[_path_i]
	var d := Vector2(tgt.x - global_position.x, tgt.z - global_position.z).length()
	if d < 0.7:
		_path_i += 1
		if _path_i >= _path.size():
			_arrive_node()


func _arrive_node() -> void:
	var n := _to_node
	# ambient activities at corners
	if role != "jogger" and randf() < 0.12:
		_start_activity()
		return
	var options := []
	for e in _graph.node_edges[n]:
		if e != _edge:
			options.append(e)
	if options.is_empty():
		options = _graph.node_edges[n]
	if options.is_empty():
		_set_state(S.IDLE)
		return
	var e: int = options[randi() % options.size()]
	_edge = e
	_from_node = n
	_to_node = _graph.other_end(e, n)
	_set_path(_graph.oriented_path(e, n))
	if _graph.edge_kind[e] == PedGraph.Kind.CROSSWALK:
		_timer = 45.0
		_set_state(S.WAIT_CROSS)
		var ts: TrafficSignals = GameWorld.instance.signals
		if _graph.edge_road_node[e] < 0 or ts.ped_can_cross(_graph.edge_road_node[e], _graph.edge_road_edge[e]):
			_set_state(S.JOG if role == "jogger" else S.WALK)


func _start_activity() -> void:
	var r := randf()
	# talk to someone nearby
	if r < 0.35 and manager:
		for o in manager.call("peds_near", global_position, 6.0):
			var other := o as NPC
			if other != self and other.state in [S.WALK, S.IDLE]:
				var mid := (global_position + other.global_position) * 0.5
				_face(other.global_position)
				other._face(global_position)
				var t := randf_range(8.0, 20.0)
				_timer = t
				other._timer = t
				_set_state(S.TALK)
				other._set_state(S.TALK)
				return
	if r < 0.6:
		_timer = randf_range(6.0, 16.0)
		_set_state(S.PHONE)
	elif r < 0.8 and manager and manager.has_method("find_bench"):
		var b: Dictionary = manager.call("find_bench", global_position, 25.0, self)
		if not b.is_empty():
			go_to(b["xform"].origin, false)
			arrived.connect(func(): if state == S.GOTO or state == S.IDLE: sit_at(b["xform"], randf_range(20.0, 60.0)), CONNECT_ONE_SHOT)
			return
		_set_state(S.IDLE)
	else:
		_set_state(S.IDLE)


func _stand_up() -> void:
	if state == S.SIT:
		global_position += -global_basis.z * 0.45
		if manager and manager.has_method("release_bench"):
			manager.call("release_bench", self)
	model.set_mode("ground")


func _face(p: Vector3) -> void:
	var d := p - global_position
	if Vector2(d.x, d.z).length() > 0.01:
		rotation.y = atan2(-d.x, -d.z)


func _on_stuck() -> void:
	if state in [S.WALK, S.JOG]:
		_offset = -_offset
		# turn around along the current edge
		if _graph and _edge >= 0:
			var tmp := _from_node
			_from_node = _to_node
			_to_node = tmp
			_set_path(_graph.oriented_path(_edge, _from_node))
			var best := 0
			var bd := INF
			for i in _path.size():
				var d := _path[i].distance_to(global_position)
				if d < bd:
					bd = d
					best = i
			_path_i = mini(best + 1, _path.size() - 1)
	elif state == S.FLEE:
		_path = PackedVector3Array()
	elif state == S.GOTO:
		_goal += Vector3(randf_range(-2, 2), 0, randf_range(-2, 2))


# ------------------------------------------------------------------ flee
func _flee(delta: float) -> void:
	_want_speed = SPRINT_SPEED * 0.92 if _timer > 6.0 else RUN_SPEED
	_timer -= delta
	if _timer <= 0.0:
		start_walking()
		return
	# follow the sidewalk graph away from the threat; fall back to running straight away
	if _path_i >= _path.size() or _path.is_empty():
		if _graph and _to_node >= 0 and _path.size() > 0:
			var n := _to_node
			var best := -1
			var bd := -INF
			for e in _graph.node_edges[n]:
				var m := _graph.other_end(e, n)
				var score := _graph.node_pos[m].distance_to(_flee_from) + randf() * 8.0
				if score > bd:
					bd = score
					best = e
			if best >= 0:
				_edge = best
				_from_node = n
				_to_node = _graph.other_end(best, n)
				_set_path(_graph.oriented_path(best, n))
				return
		var away := global_position - _flee_from
		away.y = 0.0
		if away.length() < 0.1:
			away = Vector3(randf() - 0.5, 0, randf() - 0.5)
		_path = PackedVector3Array([global_position + away.normalized() * 15.0])
		_path_i = 0
		_to_node = -1
		return
	var tgt := _path[_path_i]
	if Vector2(tgt.x - global_position.x, tgt.z - global_position.z).length() < 1.0:
		_path_i += 1


# ------------------------------------------------------------------ goto
func _goto(_delta: float) -> void:
	var d := Vector2(_goal.x - global_position.x, _goal.z - global_position.z).length()
	_want_speed = (RUN_SPEED if _goal_run else WALK_SPEED) if d > 0.6 else 0.0
	if d <= 0.6:
		_set_state(S.IDLE)
		_timer = 2.0
		arrived.emit()


# ------------------------------------------------------------------ combat
func _fight(delta: float) -> void:
	if target == null or not is_instance_valid(target) or _target_dead():
		target = null
		hostile = false
		_crouched = false
		start_walking()
		return
	var tp := target.global_position
	var d := tp.distance_to(global_position)
	if d > 90.0:
		target = null
		start_walking()
		return
	var ranged := weapons.has_ranged()
	if ranged and not weapons.current_is_ranged():
		for id in weapons.owned_sorted():
			if WeaponData.is_ranged(id):
				weapons.equip(id)
				break
	if not ranged:
		# melee: close in and punch
		_cover_pos = Vector3.INF
		_face(tp)
		if d > 1.3:
			_want_speed = RUN_SPEED
		else:
			_want_speed = 0.0
			weapons.melee()
		return
	# ranged: use cover, keep distance, fire in bursts
	_cover_timer -= delta
	if _cover_timer <= 0.0:
		_cover_timer = randf_range(5.0, 9.0)
		_cover_pos = _find_cover(tp)
	var want_dist := 12.0
	if _cover_pos != Vector3.INF:
		var cd := Vector2(_cover_pos.x - global_position.x, _cover_pos.z - global_position.z).length()
		_want_speed = RUN_SPEED if cd > 0.8 else 0.0
		if cd <= 0.8:
			_cover_pos = Vector3.INF
			_crouched = true
			model.set_mode("crouch")
	elif d > 28.0:
		_want_speed = RUN_SPEED
	elif d < 5.0:
		_want_speed = 0.0
	else:
		_want_speed = 0.0 if d < want_dist + 6.0 else WALK_SPEED * 1.6
	if _want_speed < 0.1:
		_face(tp)
	var los := _has_los(tp + Vector3.UP * 1.2)
	model.set_upper(weapons.upper_anim(), 1.0 if los else 0.4)
	if los and d < float(WeaponData.get_def(weapons.current_id()).get("range", 100.0)):
		_burst_pause -= delta
		if _burst_pause <= 0.0:
			if _crouched:
				_crouched = false
				model.set_mode("ground")
			_burst += delta
			var aim := tp + Vector3.UP * randf_range(0.9, 1.5)
			if target is Player and (target as Player).is_in_vehicle():
				aim = tp + Vector3.UP * 0.8
			weapons.fire_at(aim, true)
			if _burst > randf_range(0.8, 2.0):
				_burst = 0.0
				_burst_pause = randf_range(0.6, 1.8)
				if _cover_pos == Vector3.INF and randf() < 0.5:
					_crouched = true
					model.set_mode("crouch")


func _target_dead() -> bool:
	var h = target.get_node_or_null("Health")
	return h is Health and (h as Health).dead


func _has_los(to: Vector3) -> bool:
	var from := global_position + Vector3.UP * 1.5
	var q := PhysicsRayQueryParameters3D.create(from, to)
	q.exclude = [get_rid()]
	q.collision_mask = 1 | (1 << 2)
	var r := get_world_3d().direct_space_state.intersect_ray(q)
	if r.is_empty():
		return true
	var c = r["collider"]
	return c == target or (target is Player and c == (target as Player).vehicle)


## Samples nearby points and returns one that blocks the line of fire at crouch height.
func _find_cover(threat: Vector3) -> Vector3:
	var space := get_world_3d().direct_space_state
	var best := Vector3.INF
	var bd := INF
	for i in 10:
		var ang := TAU * i / 10.0 + randf() * 0.3
		var r := randf_range(2.5, 9.0)
		var p := global_position + Vector3(cos(ang) * r, 0, sin(ang) * r)
		# path to the spot must be free
		var q := PhysicsRayQueryParameters3D.create(global_position + Vector3.UP * 0.8, p + Vector3.UP * 0.8)
		q.exclude = [get_rid()]
		q.collision_mask = 1 | (1 << 2)
		if not space.intersect_ray(q).is_empty():
			continue
		# must be hidden from the threat at crouch height
		var q2 := PhysicsRayQueryParameters3D.create(threat + Vector3.UP * 1.4, p + Vector3.UP * 0.9)
		q2.collision_mask = 1 | (1 << 2)
		var hit := space.intersect_ray(q2)
		if hit.is_empty() or (hit["position"] as Vector3).distance_to(p) > 2.0:
			continue
		var score := p.distance_to(global_position) + absf(p.distance_to(threat) - 14.0) * 0.5
		if score < bd:
			bd = score
			best = p
	return best


# ------------------------------------------------------------------ damage & death
func _on_damaged(amount: float, source: Node, _pos: Vector3, dir: Vector3) -> void:
	if state == S.DEAD:
		return
	_last_attacker = source as Node3D
	if amount > 6.0 and state != S.KNOCKED:
		model.play_oneshot("hit_react")
		if randf() < 0.5:
			AudioManager.play_voice("pain", global_position)
	if source is Player and not _assault_reported and role != "gang":
		_assault_reported = true
		Events.crime_committed.emit("assault_cop" if role == "cop" else "assault", global_position,
			2 if role == "cop" else 1, source)
	if source == null or state == S.KNOCKED:
		return
	if role in ["cop", "gang"] or (randf() < bravery and not (source is Player and (source as Player).is_armed() and (source as Player).weapons.current_is_ranged())):
		if source is Node3D:
			engage(source as Node3D)
	elif state != S.FIGHT:
		flee_from((source as Node3D).global_position if source is Node3D else global_position)


func _on_died(source: Node) -> void:
	var impulse := Vector3.ZERO
	if source is Node3D:
		impulse = (global_position - (source as Node3D).global_position).normalized() * 40.0 + Vector3.UP * 10.0
	_set_state(S.DEAD)
	_stand_up()
	weapons.holster()
	_col.disabled = true
	collision_layer = 0
	AudioManager.play_voice("death", global_position)
	if model.ragdolled:
		pass
	else:
		model.start_ragdoll(impulse, "Chest")
	_drop_loot()
	var killer: Node = source
	if source is Vehicle:
		killer = (source as Vehicle).driver
	Events.npc_killed.emit(self, killer)
	if killer is Player:
		Events.crime_committed.emit("murder_cop" if role == "cop" else "murder", global_position,
			4 if role == "cop" else 3, killer)
	died.emit(self)
	# bodies are cleaned up by the ped manager when far or after a while
	get_tree().create_timer(60.0).timeout.connect(func(): if is_instance_valid(self): queue_free())


func _drop_loot() -> void:
	var w := GameWorld.instance
	if w == null:
		return
	var cash := money if money > 0 else randi_range(5, 60)
	Pickup.spawn(w, global_position + Vector3(randf_range(-0.5, 0.5), 0.1, randf_range(-0.5, 0.5)), "money", cash)
	var wid := weapons.current_id()
	if wid == "unarmed":
		for id in weapons.owned:
			if id != "unarmed":
				wid = id
	if wid != "unarmed" and weapons.owned.has(wid):
		var ammo: int = int(weapons.owned[wid]["clip"]) + int(weapons.owned[wid]["reserve"])
		Pickup.spawn(w, global_position + Vector3(0.6, 0.1, 0.3), "weapon", maxi(ammo, 12), wid)


func _knocked(delta: float) -> void:
	_timer -= delta
	if _timer > 0.0:
		return
	if health.dead:
		return
	var c := model.ragdoll_center()
	model.stop_ragdoll()
	_col.disabled = false
	global_position = Vector3(c.x, maxf(c.y - 0.2, global_position.y), c.z)
	velocity = Vector3.ZERO
	if _last_attacker and is_instance_valid(_last_attacker) and randf() < bravery * 0.6 and role != "cop":
		engage(_last_attacker)
	else:
		flee_from(_last_attacker.global_position if _last_attacker and is_instance_valid(_last_attacker) else global_position)


func _avoid_vehicles(p: Node3D) -> void:
	if _dodge_cd > 0.0 or not near or state in [S.SIT, S.DEAD, S.KNOCKED]:
		return
	var tm = GameWorld.instance.traffic
	if tm == null:
		return
	_dodge_cd = 0.25
	for v in tm.call("vehicles_near", global_position, 12.0):
		var veh := v as Vehicle
		var vel := veh.linear_velocity
		if vel.length() < 4.0 or veh.kinematic_mode:
			continue
		var rel := global_position - veh.global_position
		rel.y = 0.0
		var along := rel.dot(vel.normalized())
		var lateral := (rel - vel.normalized() * along).length()
		if along > 0.0 and along < vel.length() * 1.3 and lateral < 2.0:
			# jump out of the way
			var side := vel.normalized().cross(Vector3.UP)
			if side.dot(rel) < 0.0:
				side = -side
			velocity += side * 5.5 + Vector3.UP * 2.5
			_scream()
			if state in [S.WALK, S.IDLE, S.PHONE, S.TALK, S.WINDOW, S.WAIT_CROSS] and veh.driver is Player:
				flee_from(veh.global_position, 6.0)
			return


func _player_aims_at_me(p: Node3D) -> bool:
	var pl := p as Player
	if pl == null or pl.cam == null:
		return false
	var to := global_position + Vector3.UP * 1.2 - pl.cam.camera.global_position
	return to.normalized().dot(pl.cam.aim_direction()) > 0.97 and to.length() < 40.0


func _scream() -> void:
	if _scream_cd > 0.0:
		return
	_scream_cd = randf_range(4.0, 8.0)
	if randf() < 0.6:
		AudioManager.play_voice("scream", global_position)
