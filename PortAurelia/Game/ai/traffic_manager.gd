class_name TrafficManager
extends Node
## Spawns, drives and recycles ambient traffic and parked cars around the player.
##
## * Density depends on quality setting, district, time of day and weather.
## * Vehicle mix depends on the district (downtown taxis/buses, harbour trucks, ...).
## * Moving cars spawn out of view 70-230 m away, parked cars on free parking bays.
## * Near cars (< 140 m) are physically simulated; far cars move kinematically.
## * Keeps a spatial grid of ALL vehicles for fast neighbourhood queries.

const SPAWN_MIN := 70.0
const SPAWN_MAX := 230.0
const DESPAWN := 290.0
const NEAR_PHYSICS := 140.0
const GRID := 25.0

const MIX := {
	"downtown": {"sedan": 5, "taxi": 4, "luxury": 2, "compact": 3, "suv": 2, "bus": 1, "van": 1, "delivery": 1, "sports": 1},
	"financial": {"sedan": 4, "taxi": 4, "luxury": 4, "suv": 2, "sports": 1, "supercar": 0.4},
	"shopping": {"sedan": 4, "compact": 4, "taxi": 2, "suv": 2, "delivery": 1, "bus": 0.6},
	"entertainment": {"sedan": 3, "sports": 2, "taxi": 3, "compact": 2, "motorcycle": 1},
	"oldtown": {"compact": 5, "sedan": 3, "van": 1, "motorcycle": 1.5, "taxi": 1},
	"residential": {"sedan": 4, "compact": 4, "suv": 3, "pickup": 1, "van": 1, "motorcycle": 0.6},
	"suburbs": {"suv": 4, "pickup": 3, "sedan": 3, "compact": 2, "van": 1},
	"luxury": {"luxury": 4, "sports": 3, "supercar": 1.5, "suv": 3},
	"industrial": {"truck": 3, "van": 3, "pickup": 3, "delivery": 2, "sedan": 1},
	"construction": {"truck": 3, "pickup": 3, "van": 2},
	"harbor": {"truck": 5, "van": 2, "pickup": 2},
	"airport": {"taxi": 4, "van": 2, "sedan": 2, "bus": 1},
	"marina": {"sedan": 3, "luxury": 2, "sports": 2, "suv": 2, "taxi": 1},
	"beach": {"compact": 3, "suv": 3, "sports": 2, "pickup": 1, "motorcycle": 1},
	"park": {"sedan": 3, "compact": 3, "taxi": 1},
	"hills": {"suv": 3, "pickup": 3, "sports": 1},
	"rural": {"pickup": 5, "truck": 2, "suv": 2},
	"rural_east": {"pickup": 5, "truck": 2, "suv": 2},
	"sea": {"sedan": 1},
}

var world: GameWorld
var vehicles: Array = []            # every Vehicle in the world (traffic, parked, player, police)
var drivers := {}                   # Vehicle -> TrafficDriver
var parked := {}                    # Vehicle -> parking index
var enabled := true
var target_moving := 26
var target_parked := 24
var _grid := {}
var _spawn_timer := 0.0
var _despawn_queue: Array = []
var rng := RandomNumberGenerator.new()


func _ready() -> void:
	name = "Traffic"
	world = GameWorld.instance
	world.traffic = self
	rng.randomize()
	var q: int = Settings.quality() if Settings else 2
	target_moving = [12, 20, 28, 38][clampi(q, 0, 3)]
	target_parked = [10, 16, 24, 32][clampi(q, 0, 3)]
	Events.gunshot.connect(_on_gunshot)
	Events.explosion.connect(func(p, r, s): _on_gunshot(p, s, 2.0))
	get_tree().node_added.connect(_on_node_added)
	for v in get_tree().get_nodes_in_group("vehicles"):
		_register(v)


func _on_node_added(n: Node) -> void:
	if n is Vehicle:
		_register.call_deferred(n)


func _register(v: Node) -> void:
	if v is Vehicle and not vehicles.has(v):
		vehicles.append(v)
		v.tree_exiting.connect(func(): _unregister(v))


func _unregister(v: Node) -> void:
	vehicles.erase(v)
	drivers.erase(v)
	parked.erase(v)


# ------------------------------------------------------------------ queries
func vehicles_near(p: Vector3, r: float) -> Array:
	var out := []
	var c0 := Vector2i(floori((p.x - r) / GRID), floori((p.z - r) / GRID))
	var c1 := Vector2i(floori((p.x + r) / GRID), floori((p.z + r) / GRID))
	var r2 := r * r
	for x in range(c0.x, c1.x + 1):
		for z in range(c0.y, c1.y + 1):
			for v in _grid.get(Vector2i(x, z), []):
				if is_instance_valid(v) and (v as Node3D).global_position.distance_squared_to(p) < r2:
					out.append(v)
	return out


func _rebuild_grid() -> void:
	_grid.clear()
	for v in vehicles:
		if not is_instance_valid(v):
			continue
		var p: Vector3 = (v as Node3D).global_position
		var k := Vector2i(floori(p.x / GRID), floori(p.z / GRID))
		if not _grid.has(k):
			_grid[k] = []
		_grid[k].append(v)


# ------------------------------------------------------------------ main loop
func _physics_process(delta: float) -> void:
	if world == null or world.player == null:
		return
	_rebuild_grid()
	var pp := world.player.global_position
	for v in drivers.keys():
		if not is_instance_valid(v):
			drivers.erase(v)
			continue
		var d: TrafficDriver = drivers[v]
		var dist := (v as Node3D).global_position.distance_to(pp)
		var near := dist < NEAR_PHYSICS and world.streaming.is_loaded_at((v as Node3D).global_position)
		d.tick(delta, near)
	# sirens make traffic pull over
	for v in vehicles:
		if is_instance_valid(v) and (v as Vehicle).siren_on:
			for o in vehicles_near((v as Node3D).global_position, 45.0):
				if drivers.has(o):
					(drivers[o] as TrafficDriver).on_siren((v as Node3D).global_position)
	_process_despawns(pp)
	_spawn_timer -= delta
	if _spawn_timer <= 0.0 and enabled:
		_spawn_timer = 0.35
		_maintain_population(pp)


func _density_factor(p: Vector3) -> float:
	var d := world.data.district_at(p)
	var f := float(d.get("traffic", 0.6))
	var h := 12.0
	if world.day_night:
		h = float(world.day_night.get("hour"))
	var tod := 1.0
	if h < 5.5 or h > 23.0:
		tod = 0.35
	elif h < 7.0 or h > 21.0:
		tod = 0.65
	elif (h > 7.5 and h < 9.5) or (h > 16.5 and h < 19.0):
		tod = 1.15
	var weather := 1.0
	if world.weather and String(world.weather.get("state")) in ["rain", "heavy_rain", "storm"]:
		weather = 0.8
	return clampf(f * tod * weather, 0.15, 1.2)


func _maintain_population(pp: Vector3) -> void:
	var dens := _density_factor(pp)
	var want_moving := int(target_moving * dens)
	var want_parked := int(target_parked * clampf(dens + 0.3, 0.3, 1.0))
	if drivers.size() < want_moving:
		_spawn_moving(pp)
	if parked.size() < want_parked:
		_spawn_parked(pp)


func _pick_type(p: Vector3) -> String:
	var did: String = world.data.district_at(p).get("id", "downtown")
	var mix: Dictionary = MIX.get(did, MIX["downtown"])
	var total := 0.0
	for k in mix:
		total += float(mix[k])
	var r := rng.randf() * total
	for k in mix:
		r -= float(mix[k])
		if r <= 0.0:
			return k
	return "sedan"


func _spawn_moving(pp: Vector3) -> void:
	var cam := get_viewport().get_camera_3d()
	var ids := world.graph.lanes_near(pp, SPAWN_MAX)
	if ids.is_empty():
		return
	for attempt in 8:
		var id: int = ids[rng.randi() % ids.size()]
		var lane: RoadGraph.Lane = world.graph.lanes[id]
		if lane.length < 20.0:
			continue
		var s := rng.randf_range(5.0, lane.length - 8.0)
		var p := lane.point_at(s)
		var d := p.distance_to(pp)
		if d < SPAWN_MIN or d > SPAWN_MAX:
			continue
		if not world.streaming.is_loaded_at(p):
			continue
		if cam and d < 160.0 and cam.is_position_in_frustum(p):
			continue
		if not vehicles_near(p, 14.0).is_empty():
			continue
		var type_id := _pick_type(p)
		if type_id == "motorcycle" and lane.speed > 20.0:
			type_id = "sedan"
		var v := Vehicle.create(type_id)
		var dir := lane.dir_at(s)
		v.transform = Transform3D(Basis.looking_at(dir, Vector3.UP), p + Vector3.UP * 0.05)
		world.add_child(v)
		var drv := TrafficDriver.new()
		v.add_child(drv)
		drv.npc_outfit = {}
		drv.setup(v, self, id, s)
		v.set_kinematic(true)
		drv._kin_speed = lane.speed * 0.7
		drivers[v] = drv
		_register(v)
		return


func _spawn_parked(pp: Vector3) -> void:
	var cam := get_viewport().get_camera_3d()
	for c in world.streaming.loaded_chunks():
		if rng.randf() > 0.35:
			continue
		var ids: Array = world.data.parking_by_chunk.get(c, [])
		if ids.is_empty():
			continue
		for attempt in 3:
			var i: int = ids[rng.randi() % ids.size()]
			var pk: Dictionary = world.data.parking[i]
			if pk["occupied"]:
				continue
			var p: Vector3 = pk["pos"]
			var d := p.distance_to(pp)
			if d < 35.0 or d > 150.0:
				continue
			if cam and d < 90.0 and cam.is_position_in_frustum(p):
				continue
			if not vehicles_near(p, 4.0).is_empty():
				continue
			var type_id := _pick_type(p)
			if type_id in ["bus", "truck", "fire_truck"]:
				type_id = "van" if rng.randf() < 0.5 else "sedan"
			var v := Vehicle.create(type_id)
			v.is_parked = true
			var dir: Vector3 = pk["dir"]
			v.transform = Transform3D(Basis.looking_at(dir, Vector3.UP), p + Vector3.UP * 0.02)
			world.add_child(v)
			v.set_kinematic(true)
			pk["occupied"] = true
			parked[v] = i
			_register(v)
			return


func _process_despawns(pp: Vector3) -> void:
	var cam := get_viewport().get_camera_3d()
	for v in _despawn_queue:
		if is_instance_valid(v):
			_despawn(v)
	_despawn_queue.clear()
	for v in drivers.keys():
		if not is_instance_valid(v):
			continue
		var p: Vector3 = (v as Node3D).global_position
		var d := p.distance_to(pp)
		if d > DESPAWN or (d > 180.0 and cam and not cam.is_position_in_frustum(p)):
			_despawn(v)
	for v in parked.keys():
		if not is_instance_valid(v):
			continue
		var veh := v as Vehicle
		if veh.driver != null or (not veh.kinematic_mode and veh.linear_velocity.length() > 1.5):
			# taken by the player or pushed: no longer a parked car
			world.data.parking[parked[v]]["occupied"] = false
			parked.erase(v)
			veh.set_kinematic(false)
			continue
		if veh.global_position.distance_to(pp) < 25.0 and veh.kinematic_mode:
			veh.set_kinematic(false)   # allow physical interaction up close
		elif veh.global_position.distance_to(pp) > 60.0 and not veh.kinematic_mode and veh.speed() < 0.1:
			veh.set_kinematic(true)
		if veh.global_position.distance_to(pp) > 200.0:
			world.data.parking[parked[v]]["occupied"] = false
			_despawn(v)


func _despawn(v: Node) -> void:
	if (v as Vehicle).driver != null or v == _player_vehicle():
		return
	if parked.has(v):
		world.data.parking[parked[v]]["occupied"] = false
	drivers.erase(v)
	parked.erase(v)
	vehicles.erase(v)
	v.queue_free()


func _player_vehicle() -> Node:
	var p := world.player as Player
	return p.vehicle if p else null


func request_despawn(v: Node) -> void:
	var cam := get_viewport().get_camera_3d()
	var p: Vector3 = (v as Node3D).global_position
	if cam and cam.is_position_in_frustum(p) and p.distance_to(world.player.global_position) < 120.0:
		# don't pop visible cars; drop the AI instead
		release_vehicle(v)
		return
	_despawn_queue.append(v)


## Stop controlling a vehicle (driver abandoned it). It becomes a normal physics object.
func release_vehicle(v: Node) -> void:
	drivers.erase(v)
	if is_instance_valid(v):
		(v as Vehicle).set_kinematic(false)


func _on_gunshot(pos: Vector3, shooter: Node, loudness: float) -> void:
	for v in vehicles_near(pos, 45.0 * loudness):
		if drivers.has(v) and v != _player_vehicle():
			(drivers[v] as TrafficDriver).on_threat(pos)


func set_enabled(on: bool) -> void:
	enabled = on
	if not on:
		for v in drivers.keys():
			_despawn(v)


func count_moving() -> int:
	return drivers.size()
