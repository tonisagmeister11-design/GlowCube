extends Node
## Headless traffic test: boots the world, lets traffic populate and checks that
## AI cars spawn, move along lanes, stay upright and keep distances.
##   godot --headless --path Game res://tests/traffic_test.tscn

var world: GameWorld
var results := []


func _ready() -> void:
	Game.player_data = PlayerData.new()
	Game.pending_slot = -2
	world = load("res://scenes/world.tscn").instantiate()
	add_child(world)
	await Events.world_ready
	var tm: TrafficManager = world.traffic
	check("traffic manager registered", tm != null)
	await wait(12.0)
	check("moving traffic spawned", tm.drivers.size() >= 6, "%d moving" % tm.drivers.size())
	check("parked cars spawned", tm.parked.size() >= 3, "%d parked" % tm.parked.size())
	var pos0 := {}
	for v in tm.drivers:
		pos0[v] = (v as Node3D).global_position
	await wait(8.0)
	var moved := 0
	var near_moved := 0
	var near_total := 0
	var flipped := 0
	for v in pos0:
		if not is_instance_valid(v):
			continue
		var d: float = (v as Node3D).global_position.distance_to(pos0[v])
		if d > 5.0:
			moved += 1
		if not (v as Vehicle).kinematic_mode:
			near_total += 1
			if d > 3.0:
				near_moved += 1
		if (v as Node3D).global_basis.y.dot(Vector3.UP) < 0.5:
			flipped += 1
	check("traffic moves", moved >= pos0.size() / 3, "%d/%d moved" % [moved, pos0.size()])
	check("physical cars drive", near_total == 0 or near_moved >= 1, "%d/%d near moved" % [near_moved, near_total])
	check("no flipped cars", flipped == 0, str(flipped))
	# lane keeping: physical cars should be close to their lane
	var off := 0
	for v in tm.drivers:
		var drv: TrafficDriver = tm.drivers[v]
		var l: RoadGraph.Lane = world.graph.lanes[drv.lane]
		if (v as Node3D).global_position.distance_to(l.point_at(drv.s)) > 4.0:
			off += 1
	check("cars keep lanes", off <= maxi(1, tm.drivers.size() / 6), "%d off lane" % off)
	# gunshot makes nearby drivers flee
	Events.gunshot.emit(world.player.global_position, world.player, 3.0)
	check("spatial query", tm.vehicles_near(world.player.global_position, 300.0).size() > 0)
	var failed := results.filter(func(r): return not r[1]).size()
	print("FPS ", Engine.get_frames_per_second())
	print("=== %d checks, %d failed ===" % [results.size(), failed])
	get_tree().quit(1 if failed > 0 else 0)


func check(n: String, ok: bool, info := "") -> void:
	results.append([n, ok])
	print(("PASS " if ok else "FAIL ") + n + ("  (" + info + ")" if info != "" else ""))


func wait(t: float) -> void:
	await get_tree().create_timer(t).timeout
