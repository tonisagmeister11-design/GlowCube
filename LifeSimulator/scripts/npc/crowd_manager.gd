class_name CrowdManager
extends Node3D
## Sichtbare Fussgaenger um den Spieler. Wird von world_root als Kind von
## "Actors" erzeugt: setup(world, city_root).
##
## Mit Population (persons_near/visible_state/route_of): echte Personen, die
## draussen sind, werden als NpcAgent dargestellt – Pendler laufen ihre Route
## zum Ziel und verschwinden im Gebaeude, Obdachlose sitzen/schlafen an ihren
## Plaetzen, Spaziergaenger schlendern. Ohne Population: Passanten zwischen
## zufaelligen Hauseingaengen, Banken und Haltestellen.
## LOD: < 22 m volle Figur, < 55 m vereinfacht, sonst Stellvertreter; ueber
## RADIUS keine Figur. Hoechstzahl je nach Settings gameplay/population_density.

const RADIUS := 105.0
## Wirksamer Radius: der Leistungsmanager darf ihn verkleinern.
const DESPAWN := 125.0
const BASE_MAX := 70

var world: WorldState
var city: CityModel
var city_root: Node3D
var traffic: TrafficManager
var agents: Dictionary = {}          # Schluessel (person_id oder -n) -> NpcAgent
var rng := RandomNumberGenerator.new()
var _focus := Vector2.ZERO
var _scan_t := 0.0
var _ambient_id := 0
var _benches: Array = []
var _use_population := false


func setup(p_world: WorldState, p_city_root: Node3D) -> void:
	world = p_world
	city = world.city
	city_root = p_city_root
	name = "CrowdManager"
	add_to_group("crowd_manager")
	rng.seed = hash("crowd:%d" % world.seed)
	var pop = world.get("population")
	_use_population = pop != null and pop.has_method("persons_near") and pop.has_method("visible_state")
	for p in city.props:
		if p.kind == "bench":
			_benches.append(p)


func signal_time() -> float:
	return Time.get_ticks_msec() / 1000.0


func _radius() -> float:
	var perf := get_node_or_null("/root/Perf")
	return minf(RADIUS, perf.f("npc_detail_radius")) if perf != null else RADIUS


func _max_agents() -> int:
	var perf := get_node_or_null("/root/Perf")
	if perf != null:
		return perf.max_peds()
	return _max_agents_legacy()


func _max_agents_legacy() -> int:
	var d := 1.0
	var s := get_node_or_null("/root/Settings")
	if s:
		d = clampf(float(s.get_value("gameplay", "population_density")), 0.2, 2.0)
	return clampi(int(BASE_MAX * d), 25, 120)


func _process(delta: float) -> void:
	if traffic == null:
		traffic = get_tree().get_first_node_in_group("traffic_manager") as TrafficManager
	var p := get_tree().get_first_node_in_group("player")
	var f: Node3D = p if p is Node3D else get_viewport().get_camera_3d()
	if f:
		_focus = Vector2(f.global_position.x, f.global_position.z)
	_scan_t -= delta
	if _scan_t <= 0.0:
		_scan_t = 0.6
		if _use_population:
			_scan_population()
		else:
			_scan_ambient()
	for k in agents.keys():
		var a: NpcAgent = agents[k]
		var d := a.pos2.distance_to(_focus)
		var lvl := 0 if d < 22.0 else (1 if d < 55.0 else 2)
		if absf(d - (22.0 if a.lod == 0 else 55.0)) > 4.0:
			a.set_lod(lvl)
		a.tick(delta)
		if a.state == "done" or d > DESPAWN:
			a.queue_free()
			agents.erase(k)


# ============================================================ Population

func _scan_population() -> void:
	var pop = world.population
	var ids: Array = pop.persons_near(world, _focus, _radius(), true)
	var maxn := _max_agents()
	var spawned := 0
	for id in ids:
		if agents.size() >= maxn or spawned >= 6:
			break
		if agents.has(id):
			continue
		var vs: Dictionary = pop.visible_state(world, id)
		if vs.is_empty() or vs.get("indoors", false):
			continue
		_spawn_person(id, vs)
		spawned += 1
	# Personen, die laut Daten nach drinnen gegangen sind
	for k in agents.keys():
		if k < 0:
			continue
		var a: NpcAgent = agents[k]
		if a.state in ["idle", "sit", "sleep"]:
			var vs2: Dictionary = pop.visible_state(world, k)
			if vs2.is_empty() or vs2.get("indoors", false) or vs2.get("moving", false):
				a.state = "done"


func _spawn_person(id: int, vs: Dictionary) -> void:
	var pop = world.population
	var person: Dictionary = pop.get_person(id)
	var app: Dictionary = person.get("appearance", {})
	var a := NpcAgent.new()
	add_child(a)
	a.person_id = id
	a.display_name = pop.person_name(id) if pop.has_method("person_name") else "%s %s" % [person.get("first", ""), person.get("last", "")]
	a.homeless = bool(person.get("homeless", false))
	var age: float = float(vs.get("age", 30.0))
	a.speed = rng.randf_range(1.15, 1.45) * (0.7 if age > 70.0 else 1.0) * (0.75 if a.homeless else 1.0)
	a.side_off = rng.randf_range(-0.7, 0.7)
	var d := _focus.distance_to(vs.pos)
	a.setup(self, app, age, str(vs.get("body", person.get("body", "female"))), 0 if d < 22.0 else (1 if d < 55.0 else 2))
	a.pos2 = vs.pos
	a.dir2 = vs.get("dir", Vector2(0, 1))
	if vs.get("moving", false):
		var to: Vector2 = person.location.to
		var r := PedPaths.route(city, vs.pos, to)
		a.set_route(r, "enter")
		var b: int = int(person.location.get("to_b", person.location.get("building", -1)))
		if b >= 0 and b < city.buildings.size():
			a.target_door = city.buildings[b].door
	else:
		a.end_action = "idle"
		if a.homeless:
			var h := world.get_hour_f()
			a.state = "sleep" if (h < 6.0 or h > 22.0) else "sit"
		else:
			var bench := _bench_near(vs.pos, 12.0)
			if not bench.is_empty() and rng.randf() < 0.5:
				a.pos2 = bench.pos
				a.dir2 = Vector2(sin(bench.rot), cos(bench.rot))
				a.state = "sit"
			else:
				a.state = "idle"
	a._apply_pose(0.0)
	agents[id] = a


func _bench_near(p: Vector2, r: float) -> Dictionary:
	for b in _benches:
		if b.pos.distance_to(p) < r:
			return b
	return {}


# ============================================================ Passanten (ohne Population)

func _scan_ambient() -> void:
	var maxn := _max_agents()
	var spawned := 0
	while agents.size() < maxn and spawned < 4:
		spawned += 1
		var near := _buildings_near(_focus, _radius() * 0.9)
		if near.size() < 2:
			return
		var a_b: Dictionary = near[rng.randi() % near.size()]
		var start: Vector2 = a_b.sidewalk_point
		var a := NpcAgent.new()
		add_child(a)
		_ambient_id += 1
		var body_kind := "female" if rng.randf() < 0.5 else "male"
		var age := rng.randf_range(8.0, 82.0)
		var app := NpcAppearance.generate(rng, body_kind, age, rng.randf())
		var d := _focus.distance_to(start)
		a.setup(self, app, age, body_kind, 0 if d < 22.0 else (1 if d < 55.0 else 2))
		a.speed = rng.randf_range(1.1, 1.5)
		a.side_off = rng.randf_range(-0.7, 0.7)
		var roll := rng.randf()
		if roll < 0.15 and not city.bus_stops.is_empty():
			var st := _stop_near(start)
			if not st.is_empty():
				a.set_route(PedPaths.route(city, start, st.pos + Vector2(rng.randf_range(-2, 2), 0)), "bus")
				a.bus_stop = int(st.id)
		elif roll < 0.25 and not _benches.is_empty():
			var bench := _bench_near(start, 80.0)
			if not bench.is_empty():
				a.set_route(PedPaths.route(city, start, bench.pos), "sit")
				a.set_meta("dur", rng.randf_range(20.0, 60.0))
		if a.path.is_empty():
			var b_b: Dictionary = near[rng.randi() % near.size()]
			a.set_route(PedPaths.route(city, start, b_b.sidewalk_point), "enter")
			a.target_door = b_b.door
		a._apply_pose(0.0)
		agents[-_ambient_id] = a


func _buildings_near(p: Vector2, r: float) -> Array:
	var out := []
	var k := city.chunk_key(p)
	for dx in [-1, 0, 1]:
		for dz in [-1, 0, 1]:
			var c = city.chunks.get(k + Vector2i(dx, dz))
			if c == null:
				continue
			for bid in c.buildings:
				var b: Dictionary = city.buildings[bid]
				if b.sidewalk_point.distance_to(p) < r:
					out.append(b)
	return out


func _stop_near(p: Vector2) -> Dictionary:
	var best := {}
	var bd := 200.0
	for s in city.bus_stops:
		var d: float = s.pos.distance_to(p)
		if d < bd:
			bd = d
			best = s
	return best


## Fussgaenger auf der Fahrbahn (fuer den Verkehr): [{pos, r, vel}]
func get_road_obstacles() -> Array:
	var out := []
	for a in agents.values():
		var o: Dictionary = a.road_obstacle()
		if not o.is_empty():
			out.append(o)
	return out


func visible_count() -> int:
	return agents.size()
