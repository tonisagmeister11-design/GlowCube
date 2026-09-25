class_name Mission
extends Node
## Base class for story missions, side jobs and minigames.
##
## Missions are written as coroutines in `run()`:
##     objective("Steig in den Wagen.")
##     if not await until(func(): return player.vehicle == car): return
##     if not await reach(target_pos, 8.0, true): return
##     complete()
## `until` / `reach` return false as soon as the mission ended (failed / aborted), so the
## script simply returns. Fail conditions registered with `fail_if` are checked every frame.
## Everything spawned through the helpers is cleaned up when the mission ends.

signal finished(success: bool)

var id := ""
var title := ""
var reward := 0
var manager: Node
var world: GameWorld
var player: Player
var active := false
var success := false
var time_left := -1.0              # countdown shown in the HUD when >= 0
var blip_list: Array = []          # {pos | node, color, icon, edge}
var _fail_checks: Array = []       # [Callable, reason]
var _spawned: Array = []
var _markers: Array = []
var _params := {}


func setup(mgr: Node, params := {}) -> void:
	manager = mgr
	world = GameWorld.instance
	player = world.player as Player
	_params = params


## Override: the mission script.
func run() -> void:
	complete()


func begin() -> void:
	active = true
	Events.mission_started.emit(id)
	Events.big_message.emit(title.to_upper(), "", 3.0)
	run()


func _process(delta: float) -> void:
	if not active:
		return
	if time_left >= 0.0:
		time_left -= delta
		if time_left <= 0.0:
			fail("Die Zeit ist abgelaufen.")
			return
	for fc in _fail_checks:
		if (fc[0] as Callable).call():
			fail(fc[1])
			return


# ------------------------------------------------------------------ flow helpers
func objective(text: String) -> void:
	Events.mission_objective.emit(text)


func fail_if(cond: Callable, reason: String) -> void:
	_fail_checks.append([cond, reason])


## Wait until `cond` is true. Returns false if the mission ended meanwhile.
func until(cond: Callable, timeout := -1.0) -> bool:
	var t := 0.0
	while active:
		if cond.call():
			return true
		await get_tree().process_frame
		t += get_process_delta_time()
		if timeout > 0.0 and t > timeout:
			return true
	return false


func wait(seconds: float) -> bool:
	var t := 0.0
	while active and t < seconds:
		await get_tree().process_frame
		t += get_process_delta_time()
	return active


## Go to a position (checkpoint marker + blip + GPS). `in_vehicle`: must arrive driving.
func reach(pos: Vector3, radius := 6.0, in_vehicle := false, marker_color := Color(1.0, 0.85, 0.2)) -> bool:
	var m := checkpoint(pos, radius, marker_color, in_vehicle)
	var b := add_blip(pos, Color(1.0, 0.85, 0.2), "", true)
	Events.waypoint_set.emit(pos)
	var ok := await until(func():
		var p := player.global_position
		if in_vehicle and not player.is_in_vehicle():
			return false
		return Vector2(p.x - pos.x, p.z - pos.z).length() < radius and absf(p.y - pos.y) < 6.0)
	remove_blip(b)
	if is_instance_valid(m):
		m.queue_free()
	Events.waypoint_set.emit(Vector3.INF)
	if ok:
		AudioManager.play_ui("checkpoint", -4.0)
	return ok


func checkpoint(pos: Vector3, radius: float, col := Color(1.0, 0.85, 0.2), big := false) -> InteractMarker:
	var m := InteractMarker.new()
	m.vehicle_marker = true
	m.interact_radius = radius if not big else radius * 0.7
	m.color = col
	world.add_child(m)
	m.global_position = pos
	_markers.append(m)
	return m


func add_blip(target, col: Color, icon := "", edge := true) -> Dictionary:
	var b := {"target": target, "color": col, "icon": icon, "edge": edge, "size": 9.0}
	blip_list.append(b)
	return b


func remove_blip(b: Dictionary) -> void:
	blip_list.erase(b)


func blips() -> Array:
	var out := []
	for b in blip_list:
		var t = b["target"]
		var pos: Vector3
		if t is Vector3:
			pos = t
		elif is_instance_valid(t):
			if t is NPC and (t as NPC).is_dead():
				continue
			pos = (t as Node3D).global_position
		else:
			continue
		out.append({"pos": pos, "color": b["color"], "icon": b["icon"], "edge": b["edge"], "size": b["size"]})
	return out


# ------------------------------------------------------------------ spawning
func spawn_vehicle(type_id: String, pos: Vector3, dir: Vector3, color := Color(-1, 0, 0)) -> Vehicle:
	var v := Vehicle.create(type_id, color)
	dir.y = 0.0
	if dir.length() < 0.01:
		dir = Vector3.FORWARD
	v.transform = Transform3D(Basis.looking_at(dir.normalized(), Vector3.UP), pos + Vector3.UP * 0.6)
	world.add_child(v)
	if world.traffic:
		(world.traffic as TrafficManager).keep[v] = true
	_spawned.append(v)
	return v


func spawn_npc(role: String, pos: Vector3, weapon := "", outfit := {}) -> NPC:
	var n: NPC = world.peds.call("spawn_npc", pos, role, outfit, true)
	if weapon != "":
		n.weapons.give(weapon, 400)
		n.weapons.equip(weapon)
	_spawned.append(n)
	return n


## An AI driver for a mission vehicle (uses the traffic driver's emergency modes).
func drive(v: Vehicle, outfit := {}) -> TrafficDriver:
	var drv := (world.traffic as TrafficManager).make_driver(v, outfit)
	if drv:
		(world.traffic as TrafficManager).keep[v] = true
	return drv


## Lane position `dist` metres away from `from` (for spawning cars on roads).
func road_point(from: Vector3, min_d: float, max_d: float) -> Dictionary:
	var g := world.graph
	var ids := g.lanes_near(from, max_d)
	var rng := RandomNumberGenerator.new()
	rng.randomize()
	for attempt in 40:
		if ids.is_empty():
			break
		var lid: int = ids[rng.randi() % ids.size()]
		var l: RoadGraph.Lane = g.lanes[lid]
		if l.is_connector or l.length < 20.0:
			continue
		var s := rng.randf_range(5.0, l.length - 8.0)
		var p := l.point_at(s)
		var d := p.distance_to(from)
		if d >= min_d and d <= max_d:
			return {"pos": p, "dir": l.dir_at(s), "lane": lid, "s": s}
	return {}


## Road point near the centre of a district.
func district_point(did: String) -> Dictionary:
	for d in world.data.districts:
		if d["id"] == did:
			var r: Array = d["rect"]
			var c := Vector3((r[0] + r[2]) * 0.5, 0, (r[1] + r[3]) * 0.5)
			var cl := world.graph.closest_lane(c, 400.0)
			if not cl.is_empty():
				var l: RoadGraph.Lane = world.graph.lanes[cl["lane"]]
				return {"pos": cl["pos"], "dir": l.dir_at(cl["s"]), "lane": cl["lane"], "s": cl["s"]}
	return road_point(player.global_position, 100.0, 400.0)


## Sidewalk point near `p`.
func sidewalk_near(p: Vector3) -> Vector3:
	var c: Dictionary = (world.peds as PedManager).ped_graph.closest(p, 80.0)
	return c["pos"] if not c.is_empty() else p


func poi(t: String, near := Vector3.INF) -> Dictionary:
	return world.data.nearest_poi(t, near if near != Vector3.INF else player.global_position)


func poi_pos(p: Dictionary, out := 3.0) -> Vector3:
	return (p["entrance_v"] as Vector3) + (p["facing_v"] as Vector3) * out


# ------------------------------------------------------------------ end
func complete() -> void:
	if not active:
		return
	active = false
	success = true
	_cleanup(true)
	finished.emit(true)


func fail(reason := "") -> void:
	if not active:
		return
	active = false
	success = false
	Events.mission_failed.emit(id, reason)
	_cleanup(false)
	finished.emit(false)


func abort() -> void:
	active = false
	_cleanup(false)


func _cleanup(_ok: bool) -> void:
	Events.mission_objective.emit("")
	Events.waypoint_set.emit(Vector3.INF)
	for m in _markers:
		if is_instance_valid(m):
			m.queue_free()
	var tm := world.traffic as TrafficManager
	for n in _spawned:
		if not is_instance_valid(n):
			continue
		if n is Vehicle:
			var v := n as Vehicle
			if tm:
				tm.unprotect(v)
			if v.driver == null and v.global_position.distance_to(player.global_position) > 60.0:
				v.queue_free()
		elif n is NPC:
			(n as NPC).persistent = false
			if (n as NPC).hostile and not (n as NPC).is_dead():
				(n as NPC).flee_from(player.global_position, 10.0)
	_spawned.clear()
	blip_list.clear()
