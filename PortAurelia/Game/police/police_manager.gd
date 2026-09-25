class_name PoliceManager
extends Node
## Wanted level and police response.
##
## Crime -> report:  a cop who sees the crime reports it immediately; otherwise a civilian
##   witness flees and phones the police a few seconds later (killing the witness first
##   prevents the report). Gunfire in populated districts is reported by ear.
## Wanted level 1-5 from accumulated heat. Police units (patrol car + 2 officers) are
##   dispatched to the last known position; count and armament scale with the level.
## Unit FSM: RESPOND -> CHASE (vehicle pursuit) / ENGAGE (on foot: arrest or shoot)
##   -> SEARCH (player out of sight) -> RETURN (wanted cleared).
##   Roadblocks from level 3.
## Search: when no officer has seen the player for a while the stars flash and units
##   only search around the last known position. Staying hidden long enough clears the level.

signal level_changed(level: int)

enum U { RESPOND, CHASE, ENGAGE, SEARCH, BLOCK, RETURN }

const LEVEL_HEAT := [0.0, 1.0, 4.0, 9.0, 16.0, 26.0]
const CRIME_HEAT := {
	"shots_fired": 1.0, "assault": 1.0, "hit_pedestrian": 1.0, "carjack": 1.0, "vehicle_theft": 1.0,
	"murder": 4.0, "assault_cop": 4.0, "murder_cop": 9.0, "explosion": 4.0, "police_vehicle_destroyed": 5.0,
	"robbery": 4.0, "vandalism": 0.5,
}
const MIN_LEVEL := {"murder": 2, "assault_cop": 2, "murder_cop": 3, "explosion": 2, "robbery": 2}
const UNITS_PER_LEVEL := [0, 1, 2, 3, 4, 5]
const SIGHT := [0.0, 55.0, 65.0, 75.0, 85.0, 95.0]
const EVADE_TIME := [0.0, 12.0, 20.0, 30.0, 42.0, 55.0]

var world: GameWorld
var wanted_level := 0
var heat := 0.0
var last_seen := Vector3.ZERO
var time_unseen := 0.0
var searching := false
var units: Array = []            # Array[Dictionary]
var enabled := true
var rng := RandomNumberGenerator.new()
var _dispatch_timer := 0.0
var _sight_timer := 0.0
var _pending_reports: Array = [] # [{witness, kind, pos, time}]
var _roadblock_timer := 0.0
var _arrest_time := 0.0
var _violent := false            # player attacked police / used guns: cops shoot
var _contact := false            # police have seen the player since the level was raised


func _ready() -> void:
	name = "Police"
	world = GameWorld.instance
	world.police = self
	rng.randomize()
	Events.crime_committed.connect(_on_crime)
	Events.gunshot.connect(_on_gunshot)
	Events.vehicle_destroyed.connect(_on_vehicle_destroyed)
	Events.player_died.connect(func(): clear_wanted())


# ------------------------------------------------------------------ crime reporting
func _on_gunshot(pos: Vector3, shooter: Node, _loud: float) -> void:
	if shooter is Player and enabled:
		Events.crime_committed.emit("shots_fired", pos, 1, shooter)


func _on_vehicle_destroyed(v: Node) -> void:
	if v is Vehicle and (v as Vehicle).is_police and _player_nearby(v.global_position, 30.0):
		Events.crime_committed.emit("police_vehicle_destroyed", (v as Node3D).global_position, 3, world.player)


func _player_nearby(p: Vector3, r: float) -> bool:
	return world.player and world.player.global_position.distance_to(p) < r


func _on_crime(kind: String, pos: Vector3, severity: int, offender: Node) -> void:
	if not enabled or not (offender is Player):
		return
	if kind in ["shots_fired", "assault_cop", "murder_cop", "murder", "explosion"]:
		_violent = _violent or kind != "shots_fired" or wanted_level >= 2
	# police see it themselves
	if wanted_level > 0 or _cop_can_see(pos, 60.0):
		_report(kind, pos)
		return
	# vehicle theft of a parked car is only noticed by police
	if kind == "vehicle_theft":
		return
	var peds = world.peds
	var ws: Array = peds.call("witnesses", pos, 40.0, 3) if peds else []
	if not ws.is_empty():
		var w: NPC = ws[0]
		w.crime_reported = true
		_pending_reports.append({"witness": w, "kind": kind, "pos": pos, "time": rng.randf_range(4.0, 7.0)})
		if w.state != NPC.S.FLEE:
			w.flee_from(pos, 12.0)
		return
	# gunfire / explosions in populated areas are heard and reported
	if kind in ["shots_fired", "murder", "explosion"] and float(world.data.district_at(pos).get("ped", 0.0)) > 0.3:
		_pending_reports.append({"witness": null, "kind": kind, "pos": pos, "time": rng.randf_range(6.0, 10.0)})


func _report(kind: String, pos: Vector3) -> void:
	heat += float(CRIME_HEAT.get(kind, 1.0))
	var lvl := 0
	for i in LEVEL_HEAT.size():
		if heat >= LEVEL_HEAT[i]:
			lvl = i
	lvl = maxi(lvl, int(MIN_LEVEL.get(kind, 1)))
	last_seen = world.player.global_position
	time_unseen = 0.0
	set_wanted(maxi(wanted_level, lvl))


func set_wanted(lvl: int) -> void:
	lvl = clampi(lvl, 0, 5)
	if lvl == wanted_level:
		return
	var up := lvl > wanted_level
	wanted_level = lvl
	heat = maxf(heat, LEVEL_HEAT[lvl])
	if lvl >= 2:
		_violent = true
	if up:
		AudioManager.play_ui("wanted_up", -2.0)
		searching = false
		_contact = false
		time_unseen = 0.0
		_dispatch_timer = minf(_dispatch_timer, [0.0, 8.0, 5.0, 3.0, 2.0, 1.0][lvl])
	Events.wanted_changed.emit(lvl)
	level_changed.emit(lvl)
	if lvl == 0:
		Events.wanted_cleared.emit()


func clear_wanted() -> void:
	heat = 0.0
	searching = false
	_contact = false
	time_unseen = 0.0
	_violent = false
	_pending_reports.clear()
	set_wanted(0)
	for u in units:
		_set_unit(u, U.RETURN)


func is_searching() -> bool:
	return searching


# ------------------------------------------------------------------ main loop
func _physics_process(delta: float) -> void:
	if world == null or world.player == null:
		return
	# witness calls
	for r in _pending_reports.duplicate():
		r["time"] -= delta
		var w = r["witness"]
		if w != null and (not is_instance_valid(w) or (w as NPC).is_dead()):
			_pending_reports.erase(r)
			continue
		if w != null and r["time"] < 2.5 and (w as NPC).state == NPC.S.FLEE and not (w as NPC).get_meta("calling", false):
			(w as NPC).set_meta("calling", true)
			(w as NPC).model.set_upper("phone", 1.0)
		if r["time"] <= 0.0:
			_pending_reports.erase(r)
			if w != null:
				(w as NPC).model.set_upper("", 0.0)
			_report(r["kind"], r["pos"])
	_update_units(delta)
	if wanted_level == 0:
		_cleanup_units()
		return
	# sight
	_sight_timer -= delta
	if _sight_timer <= 0.0:
		_sight_timer = 0.3
		if _cop_can_see(world.player.global_position + Vector3.UP * 1.2, SIGHT[wanted_level]):
			last_seen = world.player.global_position
			time_unseen = 0.0
			_contact = true
			if searching:
				searching = false
				Events.wanted_changed.emit(wanted_level)
		elif _contact or _units_near(last_seen, 70.0):
			# the escape timer only runs once police are actually looking for the player
			time_unseen += 0.3
	var hide_after := 5.0 + wanted_level
	if not searching and time_unseen > hide_after:
		searching = true
		Events.wanted_search_started.emit(last_seen, search_radius())
		for u in units:
			if u["state"] != U.BLOCK:
				_set_unit(u, U.SEARCH)
	if searching and time_unseen > hide_after + EVADE_TIME[wanted_level]:
		Events.notify.emit("Du hast die Polizei abgehängt.", 3.0)
		clear_wanted()
		return
	# dispatch
	_dispatch_timer -= delta
	if _dispatch_timer <= 0.0:
		_dispatch_timer = rng.randf_range(4.0, 7.0) / (1.0 + wanted_level * 0.25)
		var active := 0
		for u in units:
			if u["state"] != U.RETURN and u["state"] != U.BLOCK:
				active += 1
		if active < UNITS_PER_LEVEL[wanted_level]:
			_spawn_unit()
	_roadblock_timer -= delta
	if wanted_level >= 3 and _roadblock_timer <= 0.0 and not searching:
		_roadblock_timer = 40.0
		_spawn_roadblock()
	_check_arrest(delta)


func _units_near(p: Vector3, r: float) -> bool:
	for u in units:
		if u["state"] == U.RETURN:
			continue
		if is_instance_valid(u["vehicle"]) and (u["vehicle"] as Node3D).global_position.distance_to(p) < r:
			return true
		for c in u["cops"]:
			if is_instance_valid(c) and (c as Node3D).global_position.distance_to(p) < r:
				return true
	return false


func search_radius() -> float:
	return 60.0 + wanted_level * 30.0


## Any officer (on foot or in a police car) with line of sight to `pos`.
func _cop_can_see(pos: Vector3, dist: float) -> bool:
	var space := world.get_world_3d().direct_space_state
	var eyes: Array = []
	for u in units:
		if u["state"] == U.RETURN:
			continue
		for c in u["cops"]:
			if is_instance_valid(c) and not (c as NPC).is_dead():
				eyes.append((c as Node3D).global_position + Vector3.UP * 1.6)
		if is_instance_valid(u["vehicle"]) and u["cops"].is_empty():
			eyes.append((u["vehicle"] as Node3D).global_position + Vector3.UP * 1.4)
	# ambient patrol cars in traffic
	if world.traffic:
		for v in world.traffic.call("vehicles_near", pos, dist):
			if (v as Vehicle).is_police and (v as Vehicle).ai_driver != null:
				eyes.append((v as Node3D).global_position + Vector3.UP * 1.4)
	for e in eyes:
		if (e as Vector3).distance_to(pos) > dist:
			continue
		var q := PhysicsRayQueryParameters3D.create(e, pos)
		q.collision_mask = 1
		if space.intersect_ray(q).is_empty():
			return true
	return false


# ------------------------------------------------------------------ units
func _spawn_unit(at_lane := -1, s := -1.0) -> Dictionary:
	var pp := world.player.global_position
	var target := last_seen if searching else pp
	var cam := get_viewport().get_camera_3d()
	var lane_id := at_lane
	var ls := s
	if lane_id < 0:
		var ids := world.graph.lanes_near(target, 240.0)
		for attempt in 12:
			if ids.is_empty():
				break
			var id: int = ids[rng.randi() % ids.size()]
			var l: RoadGraph.Lane = world.graph.lanes[id]
			if l.length < 15.0 or l.is_connector:
				continue
			var t := rng.randf_range(3.0, l.length - 5.0)
			var p := l.point_at(t)
			var d := p.distance_to(pp)
			if d < 110.0 or d > 240.0 or not world.streaming.is_loaded_at(p):
				continue
			if cam and cam.is_position_in_frustum(p) and d < 160.0:
				continue
			lane_id = id
			ls = t
			break
	if lane_id < 0:
		return {}
	var lane: RoadGraph.Lane = world.graph.lanes[lane_id]
	var v := Vehicle.create("police")
	v.transform = Transform3D(Basis.looking_at(lane.dir_at(ls), Vector3.UP), lane.point_at(ls) + Vector3.UP * 0.1)
	world.add_child(v)
	var drv := TrafficDriver.new()
	v.add_child(drv)
	drv.npc_outfit = Outfits.random("cop", rng)
	drv.setup(v, world.traffic, lane_id, ls)
	(world.traffic as TrafficManager).adopt(v, drv)
	v.set_kinematic(true)
	drv._kin_speed = 15.0
	v.set_siren(true)
	var u := {"vehicle": v, "driver": drv, "cops": [], "state": U.RESPOND, "timer": 0.0, "outfits": [drv.npc_outfit, Outfits.random("cop", rng)]}
	units.append(u)
	drv.respond_to(target)
	return u


func _set_unit(u: Dictionary, st: int) -> void:
	if u["state"] == st:
		return
	u["state"] = st
	u["timer"] = 0.0
	var v: Vehicle = u["vehicle"] if is_instance_valid(u["vehicle"]) else null
	var drv: TrafficDriver = u["driver"] if is_instance_valid(u["driver"]) else null
	match st:
		U.CHASE:
			_recall_cops(u)
			if drv:
				drv.pursue(_player_target())
		U.ENGAGE:
			if drv:
				drv.hold()
			if u["cops"].is_empty():
				_deploy_cops(u)
		U.SEARCH:
			if drv:
				drv.respond_to(last_seen + Vector3(rng.randf_range(-40, 40), 0, rng.randf_range(-40, 40)))
			for c in u["cops"]:
				if is_instance_valid(c):
					(c as NPC).hostile = false
					(c as NPC).search_area(last_seen, search_radius() * 0.4)
		U.RETURN:
			if v:
				v.set_siren(false)
			for c in u["cops"]:
				if is_instance_valid(c) and not (c as NPC).is_dead():
					(c as NPC).hostile = false
					(c as NPC).target = null
					if v:
						(c as NPC).go_to(v.get_exit_point(), false)
		U.BLOCK:
			if drv:
				drv.hold()


func _in_view(p: Vector3) -> bool:
	var cam := get_viewport().get_camera_3d()
	return cam != null and cam.is_position_in_frustum(p) and cam.global_position.distance_to(p) < 150.0


func _remove_unit(u: Dictionary) -> void:
	for c in u["cops"]:
		if is_instance_valid(c):
			c.queue_free()
	var v = u["vehicle"]
	units.erase(u)
	if is_instance_valid(v) and (v as Vehicle).driver == null:
		(world.traffic as TrafficManager).unprotect(v)
		(world.traffic as TrafficManager)._despawn(v)


func _player_target() -> Node3D:
	var p := world.player as Player
	return p.vehicle if p.is_in_vehicle() and p.vehicle else p


func _deploy_cops(u: Dictionary) -> void:
	var v: Vehicle = u["vehicle"]
	if not is_instance_valid(v) or world.peds == null:
		return
	var n_cops := 2 if v.type_id == "police" else 1
	for i in n_cops:
		var side := -1.0 if i == 0 else 1.0
		var W := float(v.meta.get("width", 1.8))
		var p := v.global_transform * Vector3(side * (W * 0.5 + 0.7), 0.2, -0.3)
		var cop: NPC = world.peds.call("spawn_npc", p, "cop", u["outfits"][i], true)
		cop.unit = u
		var weapon := "pistol"
		if wanted_level >= 4:
			weapon = ["smg", "rifle", "shotgun"][rng.randi() % 3]
		elif wanted_level >= 3 and rng.randf() < 0.4:
			weapon = "shotgun"
		cop.weapons.give(weapon, 300)
		cop.weapons.equip(weapon)
		cop.weapons.accuracy = 0.45 + wanted_level * 0.07
		cop.health.max_health = 110.0 + wanted_level * 10.0
		cop.health.health = cop.health.max_health
		if wanted_level >= 4:
			cop.health.armor = 50.0
		u["cops"].append(cop)
	if is_instance_valid(u["driver"]) and (world.traffic as TrafficManager):
		(world.traffic as TrafficManager)._release_model(v)


## Officers get back into the car (removed when they reach it).
func _recall_cops(u: Dictionary) -> void:
	var v: Vehicle = u["vehicle"] if is_instance_valid(u["vehicle"]) else null
	for c in u["cops"]:
		if is_instance_valid(c) and not (c as NPC).is_dead() and v:
			(c as NPC).hostile = false
			(c as NPC).target = null
			(c as NPC).go_to(v.get_exit_point(), true)


func _update_units(delta: float) -> void:
	var p := world.player as Player
	var pp := p.global_position
	var in_vehicle := p.is_in_vehicle()
	for u in units.duplicate():
		var v: Vehicle = u["vehicle"] if is_instance_valid(u["vehicle"]) else null
		u["timer"] += delta
		# drop dead / removed officers
		for c in u["cops"].duplicate():
			if not is_instance_valid(c):
				u["cops"].erase(c)
		if v == null or v.destroyed:
			# unit without a car keeps fighting on foot
			if u["cops"].is_empty():
				units.erase(u)
				continue
		var vd := v.global_position.distance_to(pp) if v else 0.0
		match u["state"]:
			U.RESPOND:
				# progress watchdog: a unit that makes no headway (stuck in traffic) is replaced
				if vd < float(u.get("best_d", INF)) - 8.0:
					u["best_d"] = vd
					u["stall"] = 0.0
				else:
					u["stall"] = float(u.get("stall", 0.0)) + delta
				if v and float(u["stall"]) > 12.0 and vd > 80.0 and not _in_view(v.global_position):
					_remove_unit(u)
					continue
				# stuck close by (dead end, traffic jam): officers continue on foot
				if v and float(u["stall"]) > 5.0 and vd <= 80.0 and not in_vehicle:
					_set_unit(u, U.ENGAGE)
					continue
				if v and vd < (45.0 if in_vehicle else 30.0):
					_set_unit(u, U.CHASE if in_vehicle else U.ENGAGE)
				elif v and is_instance_valid(u["driver"]) and u["timer"] > 3.0:
					u["timer"] = 0.0
					(u["driver"] as TrafficDriver).respond_to(pp)
			U.CHASE:
				if not in_vehicle and vd < 45.0:
					_set_unit(u, U.ENGAGE)
				elif v and is_instance_valid(u["driver"]):
					var drv: TrafficDriver = u["driver"]
					drv.pursue_target = _player_target()
					# cops finished boarding?
					for c in u["cops"].duplicate():
						if is_instance_valid(c) and (c as NPC).global_position.distance_to(v.get_exit_point()) < 1.8:
							u["cops"].erase(c)
							c.queue_free()
					if not u["cops"].is_empty():
						drv.hold()
					elif drv.mode == TrafficDriver.Mode.HOLD:
						drv.pursue(_player_target())
			U.ENGAGE:
				if in_vehicle and (p.vehicle as Vehicle).speed() > 6.0 and vd > 25.0 and v and not v.destroyed:
					_set_unit(u, U.CHASE)
				else:
					_drive_cops(u, p)
			U.SEARCH:
				if not searching:
					_set_unit(u, U.RESPOND)
				elif u["timer"] > 12.0 and v and is_instance_valid(u["driver"]) and u["cops"].is_empty():
					u["timer"] = 0.0
					(u["driver"] as TrafficDriver).respond_to(last_seen + Vector3(rng.randf_range(-60, 60), 0, rng.randf_range(-60, 60)))
			U.BLOCK:
				if not searching:
					for c in u["cops"]:
						if is_instance_valid(c) and not (c as NPC).is_dead() and (c as NPC).global_position.distance_to(pp) < 60.0:
							_cop_attack(c as NPC, p)
			U.RETURN:
				var all_in := true
				for c in u["cops"].duplicate():
					if not is_instance_valid(c) or (c as NPC).is_dead():
						continue
					if v and (c as NPC).global_position.distance_to(v.get_exit_point()) < 1.8:
						u["cops"].erase(c)
						c.queue_free()
					else:
						all_in = false
						if (c as NPC).state != NPC.S.GOTO and v:
							(c as NPC).go_to(v.get_exit_point(), false)
				var far := v == null or vd > 220.0
				if all_in or u["timer"] > 25.0 or far:
					for c in u["cops"]:
						if is_instance_valid(c) and not (c as NPC).is_dead():
							(c as NPC).persistent = false
					u["cops"].clear()
					if v:
						v.set_siren(false)
						var tm := world.traffic as TrafficManager
						var drv: TrafficDriver = u["driver"] if is_instance_valid(u["driver"]) else null
						if drv == null and v.driver == null:
							drv = tm.make_driver(v, u["outfits"][0])
						if drv:
							drv.resume_traffic()
						tm.unprotect(v)
					units.erase(u)


## Officers on foot: arrest attempt at low levels when the player is not violent, otherwise shoot.
func _drive_cops(u: Dictionary, p: Player) -> void:
	for c in u["cops"]:
		if not is_instance_valid(c) or (c as NPC).is_dead():
			continue
		_cop_attack(c as NPC, p)


func _cop_attack(cop: NPC, p: Player) -> void:
	if cop.state == NPC.S.KNOCKED:
		return
	var shoot := _violent or wanted_level >= 2 or (p.is_armed() and p.weapons.current_is_ranged() and p.aiming)
	if shoot:
		if cop.state != NPC.S.FIGHT or cop.target != _player_target():
			cop.engage(_player_target())
	else:
		if cop.state != NPC.S.CHASE:
			cop.hostile = false
			cop.chase(p)


func _check_arrest(delta: float) -> void:
	var p := world.player as Player
	if p.state == Player.State.DEAD or p.state == Player.State.BUSTED or wanted_level == 0:
		_arrest_time = 0.0
		return
	if wanted_level > 2 or (_violent and wanted_level > 1):
		_arrest_time = 0.0
		return
	var close := false
	for u in units:
		for c in u["cops"]:
			if is_instance_valid(c) and not (c as NPC).is_dead() and (c as NPC).state != NPC.S.KNOCKED:
				var d := (c as Node3D).global_position.distance_to(p.global_position)
				if d < (3.2 if p.is_in_vehicle() else 2.2):
					close = true
	var slow := not p.is_in_vehicle() or (p.vehicle as Vehicle).speed() < 1.0
	if close and slow and not p.aiming and p.move_speed < 2.5:
		_arrest_time += delta
		if _arrest_time > (2.0 if p.is_in_vehicle() else 1.2):
			_arrest_time = 0.0
			p.arrest()
			for u in units:
				_set_unit(u, U.RETURN)
	else:
		_arrest_time = maxf(0.0, _arrest_time - delta * 2.0)


# ------------------------------------------------------------------ roadblocks
func _spawn_roadblock() -> void:
	var p := world.player as Player
	var vel := Vector3.ZERO
	if p.is_in_vehicle():
		vel = (p.vehicle as RigidBody3D).linear_velocity
	if vel.length() < 8.0:
		return
	var ahead := p.global_position + vel.normalized() * 150.0
	var c := world.graph.closest_lane(ahead, 60.0, vel.normalized())
	if c.is_empty():
		return
	var lane: RoadGraph.Lane = world.graph.lanes[c["lane"]]
	if lane.is_connector or not world.streaming.is_loaded_at(c["pos"]):
		return
	var s: float = clampf(c["s"], 4.0, maxf(lane.length - 4.0, 4.0))
	var center := lane.point_at(s)
	var dir := lane.dir_at(s)
	var right := dir.cross(Vector3.UP).normalized()
	for i in 2:
		var pos := center + right * (-2.0 + i * 4.2) + Vector3.UP * 0.1
		var v := Vehicle.create("police")
		# parked across the road
		v.transform = Transform3D(Basis.looking_at(right if i == 0 else -right, Vector3.UP), pos)
		world.add_child(v)
		v.set_siren(true)
		v.set_kinematic(true)
		(world.traffic as TrafficManager).keep[v] = true
		var u := {"vehicle": v, "driver": null, "cops": [], "state": U.BLOCK, "timer": 0.0,
			"outfits": [Outfits.random("cop", rng), Outfits.random("cop", rng)]}
		units.append(u)
		_deploy_cops(u)
		for cop in u["cops"]:
			# stand behind the cars
			(cop as NPC).global_position = pos - dir * 3.0 + right * rng.randf_range(-1.5, 1.5)
			(cop as NPC).rotation.y = atan2(dir.x, dir.z)
			(cop as NPC)._set_state(NPC.S.IDLE)
			(cop as NPC)._timer = 999.0
	Events.notify.emit("Straßensperre voraus!", 2.5)


func _cleanup_units() -> void:
	for u in units:
		if u["state"] != U.RETURN:
			_set_unit(u, U.RETURN)


# ------------------------------------------------------------------ save
func serialize() -> Dictionary:
	return {"level": wanted_level}


func deserialize(d: Dictionary) -> void:
	pass  # wanted level is not restored from saves (saving is blocked while wanted)
