class_name PedManager
extends Node
## Spawns and recycles pedestrians around the player and routes world events to them.
##
## * Population depends on quality, district pedestrian density, time of day and weather.
## * Roles and outfits depend on the district (business people downtown, workers in the
##   harbour, tourists at the beach, joggers in parks, gangs in Old Town at night).
## * Scenarios: sitting on benches, waiting at bus stops, window shopping, workers.
## * Reactions: gunshots, explosions, dead bodies, the player aiming at people,
##   carjacked drivers fleeing. Provides witness queries for the police.

const SPAWN_MIN := 28.0
const SPAWN_MAX := 105.0
const DESPAWN := 140.0
const GRID := 10.0

var world: GameWorld
var ped_graph: PedGraph
var peds: Array = []
var enabled := true
var target_count := 28
var rng := RandomNumberGenerator.new()
var _grid := {}
var _spawn_timer := 0.0
var _aim_timer := 0.0
var _benches := {}          # chunk -> Array[Transform3D] (seat transforms)
var _bench_users := {}      # NPC -> key
var _bench_taken := {}      # key -> NPC


func _ready() -> void:
	name = "Peds"
	world = GameWorld.instance
	world.peds = self
	rng.randomize()
	ped_graph = PedGraph.new()
	ped_graph.load_graph()
	var q: int = Settings.quality() if Settings else 2
	var pop: float = Settings.population_scale() if Settings else 1.0
	target_count = int([12, 20, 30, 40][clampi(q, 0, 3)] * pop)
	Events.gunshot.connect(_on_gunshot)
	Events.explosion.connect(_on_explosion)
	Events.npc_killed.connect(_on_npc_killed)


# ------------------------------------------------------------------ queries
func peds_near(p: Vector3, r: float) -> Array:
	var out := []
	var c0 := Vector2i(floori((p.x - r) / GRID), floori((p.z - r) / GRID))
	var c1 := Vector2i(floori((p.x + r) / GRID), floori((p.z + r) / GRID))
	var r2 := r * r
	for x in range(c0.x, c1.x + 1):
		for z in range(c0.y, c1.y + 1):
			for n in _grid.get(Vector2i(x, z), []):
				if is_instance_valid(n) and not (n as NPC).is_dead() and (n as Node3D).global_position.distance_squared_to(p) < r2:
					out.append(n)
	return out


## Living NPCs that can see `pos` (line of sight, not cops/gangs). Used by the police.
func witnesses(pos: Vector3, radius := 45.0, max_count := 4) -> Array:
	var out := []
	var space := world.get_world_3d().direct_space_state
	for n in peds_near(pos, radius):
		var npc := n as NPC
		if npc.role in ["cop", "gang"] or npc.state == NPC.S.KNOCKED:
			continue
		var q := PhysicsRayQueryParameters3D.create(npc.global_position + Vector3.UP * 1.6, pos + Vector3.UP * 1.0)
		q.collision_mask = 1
		if space.intersect_ray(q).is_empty():
			out.append(npc)
			if out.size() >= max_count:
				break
	return out


func count() -> int:
	return peds.size()


func _rebuild_grid() -> void:
	_grid.clear()
	for n in peds:
		if not is_instance_valid(n):
			continue
		var p: Vector3 = (n as Node3D).global_position
		var k := Vector2i(floori(p.x / GRID), floori(p.z / GRID))
		if not _grid.has(k):
			_grid[k] = []
		_grid[k].append(n)


# ------------------------------------------------------------------ spawning
func spawn_npc(pos: Vector3, role := "civilian", outfit := {}, persistent := false, yaw := 0.0) -> NPC:
	var n := NPC.new()
	n.role = role
	n.manager = self
	n.persistent = persistent
	n.outfit = outfit if not outfit.is_empty() else Outfits.random(role, rng)
	n.bravery = {"civilian": 0.15, "business": 0.05, "worker": 0.35, "tourist": 0.05, "jogger": 0.1,
		"gang": 1.0, "cop": 1.0, "driver": 0.2, "medic": 0.0}.get(role, 0.15)
	n.money = rng.randi_range(40, 300) if role == "business" else rng.randi_range(5, 80)
	n.position = pos
	n.rotation.y = yaw
	world.add_child(n)
	if role == "gang":
		if rng.randf() < 0.45:
			n.weapons.give("pistol", 45)
		elif rng.randf() < 0.5:
			n.weapons.give("bat")
	peds.append(n)
	n.tree_exiting.connect(func(): _forget(n))
	return n


func _forget(n: Node) -> void:
	peds.erase(n)
	release_bench(n)


## Driver pulled out / fleeing from a car.
func spawn_fleeing_driver(pos: Vector3, outfit: Dictionary, by: Node) -> NPC:
	var n := spawn_npc(pos, "driver", outfit)
	var from: Vector3 = (by as Node3D).global_position if by is Node3D else pos
	n.flee_from(from, 16.0)
	if by is Player and not (by as Player).is_armed() and rng.randf() < 0.2:
		n.engage(by as Node3D)
	return n


func _physics_process(delta: float) -> void:
	if world == null or world.player == null:
		return
	_rebuild_grid()
	var pp := world.player.global_position
	_spawn_timer -= delta
	if _spawn_timer <= 0.0 and enabled:
		_spawn_timer = 0.3
		var want := int(target_count * _density(pp))
		if _living() < want:
			_spawn_ambient(pp)
		_despawn_far(pp)
	_aim_timer -= delta
	if _aim_timer <= 0.0:
		_aim_timer = 0.2
		_check_player_threat()


func _living() -> int:
	var c := 0
	for n in peds:
		if is_instance_valid(n) and not (n as NPC).is_dead() and not (n as NPC).persistent:
			c += 1
	return c


func _density(p: Vector3) -> float:
	var d := world.data.district_at(p)
	var f := float(d.get("ped", 0.5))
	var h := 12.0
	if world.day_night:
		h = float(world.day_night.get("hour"))
	var tod := 1.0
	if h < 5.0 or h > 23.5:
		tod = 0.25
	elif h < 7.0 or h > 21.0:
		tod = 0.55
	var weather := 1.0
	if world.weather:
		var ws := String(world.weather.get("state"))
		weather = {"rain": 0.6, "heavy_rain": 0.35, "storm": 0.25, "fog": 0.8}.get(ws, 1.0)
	return clampf(f * tod * weather, 0.08, 1.0)


func _hour() -> float:
	return float(world.day_night.get("hour")) if world.day_night else 12.0


func _pick_role(p: Vector3) -> String:
	var did: String = world.data.district_at(p).get("id", "downtown")
	var r := rng.randf()
	var night := _hour() > 21.0 or _hour() < 5.0
	match did:
		"financial", "downtown":
			if r < 0.45:
				return "business"
		"industrial", "harbor", "construction":
			if r < 0.6:
				return "worker"
		"beach", "marina":
			if r < 0.55:
				return "tourist"
			if r < 0.7:
				return "jogger"
		"park":
			if r < 0.3:
				return "jogger"
		"shopping", "entertainment":
			if r < 0.2:
				return "tourist"
	if did in ["oldtown", "industrial"] and r > (0.8 if night else 0.93):
		return "gang"
	return "civilian"


func _spawn_ambient(pp: Vector3) -> void:
	var cam := get_viewport().get_camera_3d()
	# scenario spawn: someone sitting on a bench
	if rng.randf() < 0.18:
		var b := find_bench(pp + Vector3(rng.randf_range(-60, 60), 0, rng.randf_range(-60, 60)), 40.0, null)
		if not b.is_empty():
			var bp: Vector3 = b["xform"].origin
			var d := bp.distance_to(pp)
			if d > SPAWN_MIN and d < SPAWN_MAX and not (cam and d < 60.0 and cam.is_position_in_frustum(bp)):
				var n := spawn_npc(bp, _pick_role(bp))
				_take_bench(n, b["key"])
				n.sit_at(b["xform"], rng.randf_range(30.0, 90.0))
				return
	var sp := ped_graph.random_point(pp, SPAWN_MIN, SPAWN_MAX, rng)
	if sp.is_empty():
		return
	var pos: Vector3 = sp["pos"]
	if not world.streaming.is_loaded_at(pos):
		return
	if cam and pos.distance_to(pp) < 65.0 and cam.is_position_in_frustum(pos + Vector3.UP):
		return
	if not peds_near(pos, 2.0).is_empty():
		return
	var role := _pick_role(pos)
	var n := spawn_npc(pos + Vector3.UP * 0.05 - Vector3.UP * 0.16, role)
	var r := rng.randf()
	if role == "worker" and r < 0.35:
		n._timer = rng.randf_range(20.0, 60.0)
		n._set_state(NPC.S.WORK)
	elif r < 0.08:
		n._timer = rng.randf_range(10.0, 25.0)
		n._set_state(NPC.S.PHONE)
	else:
		n.start_walking()


func _despawn_far(pp: Vector3) -> void:
	var cam := get_viewport().get_camera_3d()
	for n in peds.duplicate():
		if not is_instance_valid(n):
			peds.erase(n)
			continue
		var npc := n as NPC
		if npc.persistent:
			continue
		var d := npc.global_position.distance_to(pp)
		var limit := 80.0 if npc.is_dead() else DESPAWN
		if d > limit or (d > 90.0 and cam and not cam.is_position_in_frustum(npc.global_position + Vector3.UP)):
			if npc.state == NPC.S.FIGHT and npc.target == world.player and d < 120.0:
				continue
			npc.queue_free()


# ------------------------------------------------------------------ benches
func _bench_list(c: Vector2i) -> Array:
	if _benches.has(c):
		return _benches[c]
	var out := []
	for pr in world.data.props_by_chunk.get("%d_%d" % [c.x, c.y], []):
		if pr[0] == "bench" or pr[0] == "bus_stop":
			var b := Basis(Vector3.UP, float(pr[4]))
			var o := Vector3(pr[1], pr[2], pr[3])
			if pr[0] == "bench":
				for x in [-0.45, 0.45]:
					out.append(Transform3D(b, o + b * Vector3(x, 0.0, 0.05)))
			else:
				for x in [-1.1, -0.3]:
					out.append(Transform3D(b, o + b * Vector3(x, 0.0, 0.4)))
	_benches[c] = out
	return out


func find_bench(p: Vector3, radius: float, _who) -> Dictionary:
	var c := world.data.chunk_of(p)
	var best := {}
	var bd := radius
	for dx in range(-1, 2):
		for dz in range(-1, 2):
			var cc := c + Vector2i(dx, dz)
			var lst := _bench_list(cc)
			for i in lst.size():
				var key := "%d_%d_%d" % [cc.x, cc.y, i]
				if _bench_taken.has(key) and is_instance_valid(_bench_taken[key]):
					continue
				var d := (lst[i] as Transform3D).origin.distance_to(p)
				if d < bd:
					bd = d
					best = {"xform": lst[i], "key": key}
	return best


func _take_bench(n: NPC, key: String) -> void:
	_bench_taken[key] = n
	_bench_users[n] = key


func release_bench(n) -> void:
	if _bench_users.has(n):
		_bench_taken.erase(_bench_users[n])
		_bench_users.erase(n)


# ------------------------------------------------------------------ reactions
func _on_gunshot(pos: Vector3, shooter: Node, loudness: float) -> void:
	for n in peds_near(pos, 70.0 * loudness):
		if n != shooter:
			(n as NPC).hear_gunshot(pos, shooter)


func _on_explosion(pos: Vector3, radius: float, _source: Node) -> void:
	for n in peds_near(pos, radius * 10.0):
		var npc := n as NPC
		if npc.role != "cop":
			npc.flee_from(pos, 20.0)


func _on_npc_killed(dead: Node, killer: Node) -> void:
	var pos := (dead as Node3D).global_position
	for n in peds_near(pos, 25.0):
		var npc := n as NPC
		if npc != dead and npc.role not in ["cop", "gang"] and not npc.is_busy():
			npc.flee_from(killer.global_position if killer is Node3D else pos, 16.0)


## The player aiming a gun at pedestrians makes them raise their hands or run.
func _check_player_threat() -> void:
	var p := world.player as Player
	if p == null or p.state != Player.State.GROUND or not p.weapons.current_is_ranged():
		return
	if p.aiming and p.cam:
		var hit := p.cam.aim_hit(60.0, [p.get_rid()])
		var c = hit.get("collider")
		if c is NPC:
			(c as NPC).threatened_by(p)
			return
		# near misses: people close to the aim line
		var from: Vector3 = p.cam.camera.global_position
		var dir: Vector3 = (hit["position"] - from).normalized()
		for n in peds_near(p.global_position, 30.0):
			var npc := n as NPC
			var to := npc.global_position + Vector3.UP * 1.2 - from
			var along := to.dot(dir)
			if along > 0.0 and (to - dir * along).length() < 1.2:
				npc.threatened_by(p)
	else:
		# a drawn weapon makes people nearby nervous
		for n in peds_near(p.global_position, 5.0):
			var npc := n as NPC
			if not npc.is_busy() and npc.role not in ["cop", "gang"] and rng.randf() < 0.1:
				npc.flee_from(p.global_position, 8.0)


func set_enabled(on: bool) -> void:
	enabled = on
	if not on:
		for n in peds.duplicate():
			if is_instance_valid(n) and not (n as NPC).persistent:
				n.queue_free()
