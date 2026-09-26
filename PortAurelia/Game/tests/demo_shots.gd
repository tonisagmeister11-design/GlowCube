extends Node
## Demo screenshots: general gameplay, driving, running over a pedestrian, shooting.
##   godot --path Game res://tests/demo_shots.tscn -- --out dir

var world: GameWorld
var out := "user://"
var views := false


func _ready() -> void:
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		if a[i] == "--out":
			out = a[i + 1]
		elif a[i] == "--views":
			views = a[i + 1] == "1"
		elif a[i] == "--perf":
			Settings.set_value("graphics", "performance_mode", int(a[i + 1]))
	Game.player_data = PlayerData.new()
	Game.pending_slot = -2
	world = load("res://scenes/world.tscn").instantiate()
	add_child(world)
	await Events.world_ready
	var p := world.player as Player
	var home := world.data.nearest_poi("safehouse", p.global_position)
	var f: Vector3 = home.get("facing_v", Vector3.FORWARD)
	p.cam.yaw = atan2(-f.x, -f.z)
	await wait(20.0)   # let the loading overlay finish fading and the area settle (slow software renderer)

	# ---------------------------------------------------------------- 1) general gameplay
	Input.action_press("move_forward")
	Input.action_press("sprint")
	await wait(2.0)
	Input.action_release("move_forward")
	Input.action_release("sprint")
	await wait(0.3)
	await shot("01_gameplay")

	# ---------------------------------------------------------------- 2) driving
	var lane := world.graph.closest_lane(p.global_position, 120.0)
	if lane.is_empty():
		get_tree().quit()
		return
	var l: RoadGraph.Lane = world.graph.lanes[lane["lane"]]
	var s: float = clampf(lane["s"], 4.0, maxf(l.length - 30.0, 4.0))
	var pos := l.point_at(s)
	var dir := l.dir_at(s)
	# clear the spawn point (a traffic car parked there would crash into the new one)
	for other in get_tree().get_nodes_in_group("vehicles"):
		if other is Node3D and (other as Node3D).global_position.distance_to(pos) < 14.0:
			other.queue_free()
	await get_tree().process_frame
	var car := Vehicle.create("sports", Color(0.85, 0.1, 0.05))
	world.add_child(car)
	car.transform = Transform3D(Basis.looking_at(dir, Vector3.UP), pos + Vector3.UP * 0.6)
	await wait(1.0)
	p.global_position = car.get_entry_point()
	await wait(0.3)
	p.enter_vehicle(car)
	await wait(0.8)
	Input.action_press("accelerate")
	await wait(3.0)
	await shot("02_driving")
	if views:
		p.cam.vehicle_view = 2
		await wait(1.0)
		await shot("05_hood_cam")
		p.cam.vehicle_view = 3
		await wait(1.0)
		await shot("06_cockpit")
		p.cam.vehicle_view = 0
		await wait(0.4)

	# ---------------------------------------------------------------- 3) running over a pedestrian
	var ahead := car.global_position - car.global_basis.z * 12.0
	var victim: NPC = world.peds.call("spawn_npc", ahead, "civilian", {}, true)
	victim._set_state(NPC.S.IDLE)
	victim._timer = 999.0
	victim.set_physics_process(false)   # stand still in the road (no dodge) so the car actually hits
	var t := 0.0
	var hit_shot := false
	while t < 4.0:
		await get_tree().process_frame
		t += get_process_delta_time()
		if not hit_shot and (victim.state == NPC.S.KNOCKED or victim.is_dead()):
			hit_shot = true
			await wait(0.2)
			await shot("03_run_over")
	if not hit_shot:
		await shot("03_run_over")
	Input.action_release("accelerate")
	Input.action_press("brake")
	await wait(1.5)
	Input.action_release("brake")

	# ---------------------------------------------------------------- 4) shooting
	p.exit_vehicle()
	await wait(1.0)
	p.weapons.give("rifle", 120)
	p.weapons.equip("rifle")
	var target_pos := p.global_position - p.global_basis.z * 10.0
	var target: NPC = world.peds.call("spawn_npc", target_pos, "gang", {}, true)
	target._set_state(NPC.S.IDLE)
	target._timer = 999.0
	target.rotation.y = p.rotation.y + PI
	p.cam.yaw = atan2(target.global_position.x - p.global_position.x, target.global_position.z - p.global_position.z) + PI
	await wait(0.3)
	Input.action_press("aim")
	await wait(0.4)
	Input.action_press("fire")
	await get_tree().process_frame
	await get_tree().process_frame
	await shot("04_shooting")
	await wait(0.1)
	Input.action_release("fire")
	Input.action_release("aim")
	if views:
		p.cam.first_person = true
		await wait(2.5)
		await shot("07_first_person")
		p.cam.first_person = false

	get_tree().quit()


func shot(name: String) -> void:
	await RenderingServer.frame_post_draw
	await get_tree().process_frame
	get_viewport().get_texture().get_image().save_png(out.path_join(name + ".png"))
	print("shot ", name)


func wait(t: float) -> void:
	await get_tree().create_timer(t, true, false, true).timeout
