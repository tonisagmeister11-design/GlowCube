extends Node
## Headless mission test: story mission 1 end-to-end (teleporting between objectives),
## failure handling, race generation, postcards.

var world: GameWorld
var results := []


func _ready() -> void:
	Game.player_data = PlayerData.new()
	Game.pending_slot = -2
	world = load("res://scenes/world.tscn").instantiate()
	add_child(world)
	await Events.world_ready
	var mm: MissionManager = world.missions
	var p := world.player as Player
	var pd := Game.player_data
	check("story giver present", not mm.next_story().is_empty() and mm.blips().size() > 0)
	var money0 := pd.money
	check("start m01", mm.start_story("m01"))
	await wait(0.5)
	var car: Vehicle = null
	for n in mm.current._spawned:
		if n is Vehicle:
			car = n
	check("mission car spawned", car != null)
	await wait(1.0)
	p.enter_vehicle(car)
	await wait(1.5)
	check("player in mission car", p.vehicle == car)
	var mech := world.data.nearest_poi("mechanic", p.global_position)
	var target: Vector3 = (mech["entrance_v"] as Vector3) + (mech["facing_v"] as Vector3) * 7.0
	world.streaming.load_area_blocking(target, 200.0)
	car.global_position = target + Vector3.UP * 0.8
	car.linear_velocity = Vector3.ZERO
	await wait(3.5)
	p.exit_vehicle()
	await wait(1.0)
	var diner := world.data.nearest_poi("diner", p.global_position)
	var dpos: Vector3 = (diner["entrance_v"] as Vector3) + (diner["facing_v"] as Vector3) * 3.0
	world.streaming.load_area_blocking(dpos, 200.0)
	p.teleport(dpos + Vector3.UP * 0.3)
	await wait(4.0)
	check("m01 completed", pd.missions.get("m01", "") == "done", "missions=%s" % pd.missions)
	check("m01 reward paid", pd.money >= money0 + 1000, "money %d" % pd.money)
	check("next story is m02", mm.next_story().get("id", "") == "m02")
	# failure: destroy the delivery van
	await wait(1.0)
	check("start m02", mm.start_story("m02"))
	await wait(0.5)
	var van: Vehicle = null
	for n in mm.current._spawned:
		if n is Vehicle:
			van = n
	van.explode() if van else null
	await wait(1.0)
	check("m02 fails when van destroyed", not mm.is_active() and pd.missions.get("m02", "") != "done")
	# race generation
	var race_ok := mm.start_side("race")
	await wait(1.0)
	var cps := 0
	if mm.is_active():
		cps = (mm.current as Mission).get("checkpoints").size()
	check("race track generated", race_ok and cps >= 3, "%d checkpoints" % cps)
	mm.abort_current()
	await wait(0.5)
	check("postcards placed", mm._postcards.size() == MissionManager.POSTCARDS, str(mm._postcards.size()))
	var pc: Dictionary = mm._postcards[0]
	world.streaming.load_area_blocking(pc["pos"], 150.0)
	p.teleport(pc["pos"] + Vector3(3, 0.4, 0))
	await wait(1.5)
	p.teleport(pc["pos"] + Vector3(0, 0.3, 0))
	await wait(1.5)
	check("postcard collected", pd.collectibles.has(pc["id"]), str(pd.collectibles))
	var failed := results.filter(func(r): return not r[1]).size()
	print("=== %d checks, %d failed ===" % [results.size(), failed])
	get_tree().quit(1 if failed > 0 else 0)


func check(n: String, ok: bool, info := "") -> void:
	results.append([n, ok])
	print(("PASS " if ok else "FAIL ") + n + ("  (" + info + ")" if info != "" else ""))


func wait(t: float) -> void:
	await get_tree().create_timer(t).timeout
