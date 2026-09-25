extends Node
## Headless pedestrian test: spawning, sidewalk walking, reactions, damage, car impact.
##   godot --headless --path Game res://tests/ped_test.tscn

var world: GameWorld
var results := []


func _ready() -> void:
	Game.player_data = PlayerData.new()
	Game.pending_slot = -2
	world = load("res://scenes/world.tscn").instantiate()
	add_child(world)
	await Events.world_ready
	var pm: PedManager = world.peds
	check("ped manager registered", pm != null)
	check("ped graph loaded", pm.ped_graph.node_pos.size() > 100, str(pm.ped_graph.node_pos.size()))
	await wait(10.0)
	check("pedestrians spawned", pm.count() >= 5, "%d peds" % pm.count())
	var start := {}
	for n in pm.peds:
		start[n] = (n as Node3D).global_position
	await wait(6.0)
	var moved := 0
	var walking := 0
	var fell := 0
	for n in start:
		if not is_instance_valid(n):
			continue
		var npc := n as NPC
		if npc.global_position.distance_to(start[n]) > 3.0:
			moved += 1
		if npc.state in [NPC.S.WALK, NPC.S.JOG, NPC.S.WAIT_CROSS]:
			walking += 1
		if npc.global_position.y < start[n].y - 3.0:
			fell += 1
	check("pedestrians walk", moved >= start.size() / 3, "%d/%d moved, %d walking" % [moved, start.size(), walking])
	check("nobody fell through ground", fell == 0, str(fell))
	# scripted NPC right next to the player
	var pp := world.player.global_position
	var fwd := -world.player.global_basis.z
	var n1 := pm.spawn_npc(pp + fwd * 4.0, "civilian", {}, true)
	await wait(0.5)
	check("npc stands on ground", n1.is_on_floor() or absf(n1.global_position.y - pp.y) < 0.6, "y=%.2f" % n1.global_position.y)
	n1.threatened_by(world.player)
	await wait(0.2)
	check("threat reaction", n1.state in [NPC.S.HANDS_UP, NPC.S.FLEE], "state=%d" % n1.state)
	Events.gunshot.emit(pp, world.player, 1.0)
	await wait(0.2)
	var reacting := 0
	for n in pm.peds_near(pp, 60.0):
		if (n as NPC).state in [NPC.S.FLEE, NPC.S.PANIC, NPC.S.HANDS_UP, NPC.S.FIGHT]:
			reacting += 1
	check("gunshot panic", reacting >= 1, "%d reacting" % reacting)
	# kill -> ragdoll + loot
	var pickups_before := _count_pickups()
	n1.health.take_damage(500.0, world.player, n1.global_position + Vector3.UP, fwd)
	await wait(0.5)
	check("npc dies", n1.is_dead() and n1.model.ragdolled)
	check("loot dropped", _count_pickups() > pickups_before)
	# car impact
	var n2 := pm.spawn_npc(pp + fwd * 3.0 + world.player.global_basis.x * 12.0, "civilian", {}, true)
	await wait(0.3)
	n2._set_state(NPC.S.IDLE)
	n2._timer = 30.0
	var v := Vehicle.create("sedan")
	world.add_child(v)
	var side := world.player.global_basis.x
	v.global_transform = Transform3D(Basis.looking_at(side, Vector3.UP), n2.global_position - side * 14.0 + Vector3.UP * 0.6)
	await wait(0.6)
	v.linear_velocity = side * 14.0
	var t := 0.0
	while t < 2.5 and n2.state != NPC.S.KNOCKED and not n2.is_dead():
		v.throttle = 1.0
		await get_tree().physics_frame
		t += get_physics_process_delta_time()
	check("car knocks pedestrian down", n2.state == NPC.S.KNOCKED or n2.is_dead(), "state=%d hp=%.0f" % [n2.state, n2.health.health])
	var failed := results.filter(func(r): return not r[1]).size()
	print("FPS ", Engine.get_frames_per_second(), " peds ", pm.count())
	print("=== %d checks, %d failed ===" % [results.size(), failed])
	get_tree().quit(1 if failed > 0 else 0)


func _count_pickups() -> int:
	var c := 0
	for ch in world.get_children():
		if ch is Pickup:
			c += 1
	return c


func check(n: String, ok: bool, info := "") -> void:
	results.append([n, ok])
	print(("PASS " if ok else "FAIL ") + n + ("  (" + info + ")" if info != "" else ""))


func wait(t: float) -> void:
	await get_tree().create_timer(t).timeout
