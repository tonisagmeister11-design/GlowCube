extends Node
## Drives a car fast across the city (kinematic along a road route) and logs per-second
## frame times, node/object counts, loaded chunks, traffic and memory, to find stutters
## and anything that piles up.  godot --path Game res://tests/drive_perf_test.tscn -- [--speed 70] [--time 150]

var world: GameWorld
var args := {}
var worst_frame := 0.0
var frame_times: Array[float] = []


func _ready() -> void:
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		args[a[i].trim_prefix("--")] = a[i + 1]
	Game.player_data = PlayerData.new()
	Game.pending_slot = -2
	world = (load("res://scenes/world.tscn") as PackedScene).instantiate()
	add_child(world)
	await Events.world_ready
	await _run()
	get_tree().quit(0)


func _process(delta: float) -> void:
	frame_times.append(delta)


func _run() -> void:
	var p := world.player as Player
	await get_tree().create_timer(1.0).timeout
	var lane := world.graph.closest_lane(p.global_position, 150.0)
	var l: RoadGraph.Lane = world.graph.lanes[lane["lane"]]
	var v := Vehicle.create("hypercar", Color(0.9, 0.1, 0.1))
	world.add_child(v)
	v.global_transform = Transform3D(Basis.looking_at(l.dir_at(lane["s"]), Vector3.UP), l.point_at(lane["s"]) + Vector3.UP * 0.6)
	await get_tree().create_timer(1.0).timeout
	p.enter_vehicle(v)
	await get_tree().create_timer(1.0).timeout
	# long route to the far side of the map, then back
	var pts := PackedVector3Array()
	var targets := [Vector3(1200, 0, 900), Vector3(-1300, 0, -600), Vector3(900, 0, -1100), Vector3(-900, 0, 1000)]
	var from := lane["lane"] as int
	for t in targets:
		var r := world.graph.route(from, t)
		if r.size() > 0:
			pts.append_array(world.graph.route_points(r, 6.0))
			from = r[r.size() - 1]
	print("route points: ", pts.size())
	var speed := float(args.get("speed", "70"))
	var total := float(args.get("time", "150"))
	var t := 0.0
	var idx := 0
	var sec := 0.0
	v.freeze = true
	var pos := pts[0]
	while t < total and idx < pts.size() - 1:
		var dt := get_physics_process_delta_time()
		await get_tree().physics_frame
		t += dt
		sec += dt
		var step := speed * dt
		while step > 0.0 and idx < pts.size() - 1:
			var seg := pts[idx + 1] - pos
			var d := seg.length()
			if d <= step:
				pos = pts[idx + 1]
				idx += 1
				step -= d
			else:
				pos += seg / d * step
				step = 0.0
		var dir := (pts[mini(idx + 1, pts.size() - 1)] - pos)
		dir.y = 0.0
		if dir.length() > 0.1:
			v.global_transform = Transform3D(Basis.looking_at(dir.normalized(), Vector3.UP), pos + Vector3.UP * 0.35)
		if sec >= 1.0:
			sec = 0.0
			var worst := 0.0
			var sum := 0.0
			for f in frame_times:
				worst = maxf(worst, f)
				sum += f
			var avg := sum / maxf(frame_times.size(), 1)
			frame_times.clear()
			print("PERF t=%3d fps=%5.1f worst=%5.0fms avg=%4.1fms nodes=%d objs=%d chunks=%d veh=%d peds=%d mem=%.0fMB phys=%.1fms proc=%.1fms draws=%d prims=%dk" % [
				int(t), Performance.get_monitor(Performance.TIME_FPS), worst * 1000.0, avg * 1000.0,
				Performance.get_monitor(Performance.OBJECT_NODE_COUNT), Performance.get_monitor(Performance.OBJECT_COUNT),
				world.streaming.loaded_chunks().size(), get_tree().get_nodes_in_group("vehicles").size(),
				get_tree().get_nodes_in_group("npc").size(), Performance.get_monitor(Performance.MEMORY_STATIC) / 1e6,
				Performance.get_monitor(Performance.TIME_PHYSICS_PROCESS) * 1000.0, Performance.get_monitor(Performance.TIME_PROCESS) * 1000.0,
				Performance.get_monitor(Performance.RENDER_TOTAL_DRAW_CALLS_IN_FRAME), int(Performance.get_monitor(Performance.RENDER_TOTAL_PRIMITIVES_IN_FRAME) / 1000)])
