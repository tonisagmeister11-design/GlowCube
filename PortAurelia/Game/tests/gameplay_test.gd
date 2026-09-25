extends Node
## Automated gameplay smoke test. Boots scenes/world.tscn, then drives the player
## with simulated input and checks the results. Prints PASS/FAIL lines and exits
## with code 0 (all passed) or 1.
##   godot --path Game res://tests/gameplay_test.tscn -- [--shots dir] [--vehicle sports]

var world: GameWorld
var shots := ""
var results := []
var args := {}


func _ready() -> void:
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		args[a[i].trim_prefix("--")] = a[i + 1]
	shots = args.get("shots", "")
	Game.player_data = PlayerData.new()
	Game.pending_slot = -2
	var s: PackedScene = load("res://scenes/world.tscn")
	world = s.instantiate()
	add_child(world)
	await Events.world_ready
	await _run()
	var failed := results.filter(func(r): return not r[1]).size()
	print("=== %d checks, %d failed ===" % [results.size(), failed])
	get_tree().quit(1 if failed > 0 else 0)


func check(name: String, ok: bool, info := "") -> void:
	results.append([name, ok])
	print(("PASS " if ok else "FAIL ") + name + ("  (" + info + ")" if info != "" else ""))


func wait(t: float) -> void:
	await get_tree().create_timer(t).timeout


func shot(name: String) -> void:
	if shots == "" or DisplayServer.get_name() == "headless":
		return
	await get_tree().process_frame
	get_viewport().get_texture().get_image().save_png(shots.path_join(name + ".png"))


func hold(action: String, t: float) -> void:
	Input.action_press(action)
	await wait(t)
	Input.action_release(action)


func _run() -> void:
	var p := world.player as Player
	await wait(1.5)
	check("player spawned on ground", p.is_on_floor(), "pos=%s" % p.global_position)
	check("chunks loaded", world.streaming.loaded_chunks().size() > 4, str(world.streaming.loaded_chunks().size()))
	await shot("01_spawn")
	# ---------------------------------------------------------------- walking
	var start := p.global_position
	var home := world.data.nearest_poi("safehouse", p.global_position)
	var f: Vector3 = home.get("facing_v", Vector3.FORWARD)
	p.cam.yaw = atan2(-f.x, -f.z)
	await hold("move_forward", 2.0)
	var moved := start.distance_to(p.global_position)
	check("player runs forward", moved > 5.0, "moved %.1f m" % moved)
	# turn to run along the street
	p.cam.yaw += PI * 0.5
	Input.action_press("sprint")
	start = p.global_position
	await hold("move_forward", 1.5)
	Input.action_release("sprint")
	var moved2 := start.distance_to(p.global_position)
	check("player sprints faster", moved2 / 1.5 > 5.5, "%.1f m/s" % (moved2 / 1.5))
	await hold("jump", 0.1)
	await wait(0.25)
	check("player jumps", p.state == Player.State.AIR or p.global_position.y > start.y + 0.3 or not p.is_on_floor(), "y=%.2f" % p.global_position.y)
	await wait(1.0)
	await shot("02_walk")
	# ---------------------------------------------------------------- vehicle
	var lane := world.graph.closest_lane(p.global_position, 120.0)
	check("road lane near player", not lane.is_empty(), str(lane.get("dist", -1)))
	if lane.is_empty():
		return
	var l: RoadGraph.Lane = world.graph.lanes[lane["lane"]]
	var s: float = clampf(lane["s"], 4.0, maxf(l.length - 10.0, 4.0))
	var pos := l.point_at(s)
	var dir := l.dir_at(s)
	var v := Vehicle.create(args.get("vehicle", "sports"), Color(0.8, 0.1, 0.05))
	world.add_child(v)
	v.global_transform = Transform3D(Basis.looking_at(dir, Vector3.UP), pos + Vector3.UP * 0.6)
	await wait(1.5)
	check("vehicle settles on road", v.linear_velocity.length() < 1.0 and v.global_position.y < pos.y + 1.0,
		"y=%.2f road=%.2f v=%.2f" % [v.global_position.y, pos.y, v.linear_velocity.length()])
	p.global_position = v.get_entry_point()
	await wait(0.3)
	await hold("vehicle_enter", 0.1)
	await wait(0.8)
	check("player entered vehicle", p.state == Player.State.VEHICLE)
	await shot("03_in_car")
	var vs := v.global_position
	await hold("accelerate", 4.0)
	var spd := v.speed_kmh
	check("vehicle accelerates", spd > 45.0, "%.0f km/h after 4 s" % spd)
	await shot("04_driving")
	var yaw0 := v.global_rotation.y
	Input.action_press("accelerate")
	await hold("steer_left", 1.0)
	Input.action_release("accelerate")
	check("vehicle steers", absf(angle_difference(yaw0, v.global_rotation.y)) > 0.2, "dyaw=%.2f" % angle_difference(yaw0, v.global_rotation.y))
	await hold("brake", 2.5)
	check("vehicle brakes", absf(v.speed_kmh) < 20.0, "%.0f km/h" % v.speed_kmh)
	check("vehicle upright", v.global_basis.y.dot(Vector3.UP) > 0.8)
	await wait(0.5)
	await hold("vehicle_enter", 0.1)
	await wait(1.0)
	check("player exited vehicle", p.state == Player.State.GROUND, "state=%d" % p.state)
	# ---------------------------------------------------------------- weapons
	p.weapons.give("pistol", 45)
	p.weapons.equip("pistol")
	var clip0: int = p.weapons.owned["pistol"]["clip"]
	Input.action_press("aim")
	await wait(0.3)
	await hold("fire", 0.1)
	await wait(0.3)
	Input.action_release("aim")
	check("pistol fires", int(p.weapons.owned["pistol"]["clip"]) == clip0 - 1, "clip %d -> %d" % [clip0, p.weapons.owned["pistol"]["clip"]])
	await hold("reload", 0.1)
	await wait(1.5)
	check("pistol reloads", int(p.weapons.owned["pistol"]["clip"]) == 15)
	await shot("05_armed")
	print("FPS ", Engine.get_frames_per_second(), " phys objs ", Performance.get_monitor(Performance.PHYSICS_3D_ACTIVE_OBJECTS))
