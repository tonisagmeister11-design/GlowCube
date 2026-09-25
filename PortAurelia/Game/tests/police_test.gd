extends Node
## Headless police/wanted test.
##   godot --headless --path Game res://tests/police_test.tscn

var world: GameWorld
var results := []


func _ready() -> void:
	Game.player_data = PlayerData.new()
	Game.pending_slot = -2
	world = load("res://scenes/world.tscn").instantiate()
	add_child(world)
	await Events.world_ready
	var pol: PoliceManager = world.police
	var p := world.player as Player
	check("police manager registered", pol != null)
	await wait(2.0)
	# ---- witness report
	var fwd := -p.global_basis.z
	var w: NPC = world.peds.call("spawn_npc", p.global_position + fwd * 5.0, "civilian", {}, true)
	await wait(0.5)
	Events.crime_committed.emit("assault", p.global_position, 1, p)
	var callers: Array = (world.peds as PedManager).peds.filter(func(n): return is_instance_valid(n) and (n as NPC).crime_reported)
	check("witness flees to call", callers.size() == 1 and (callers[0] as NPC).state == NPC.S.FLEE, "%d callers" % callers.size())
	var t := 0.0
	while pol.wanted_level == 0 and t < 12.0:
		await wait(0.5)
		t += 0.5
	check("witness report gives wanted level", pol.wanted_level == 1, "level %d after %.1f s" % [pol.wanted_level, t])
	# ---- dispatch & arrival
	t = 0.0
	while pol.units.is_empty() and t < 20.0:
		await wait(0.5)
		t += 0.5
	check("police unit dispatched", not pol.units.is_empty(), "after %.1f s" % t)
	t = 0.0
	var engaged := false
	while t < 70.0 and not engaged:
		await wait(0.5)
		t += 0.5
		for u in pol.units:
			if u["state"] == PoliceManager.U.ENGAGE and not u["cops"].is_empty():
				engaged = true
		if p.state == Player.State.BUSTED:
			engaged = true
	var dist := -1.0
	if not pol.units.is_empty() and is_instance_valid(pol.units[0]["vehicle"]):
		dist = (pol.units[0]["vehicle"] as Node3D).global_position.distance_to(p.global_position)
	check("police arrive and deploy officers", engaged, "t=%.1f dist=%.1f" % [t, dist])
	# ---- arrest (player unarmed, standing still)
	t = 0.0
	while p.state != Player.State.BUSTED and t < 30.0:
		await wait(0.5)
		t += 0.5
	check("unarmed player gets arrested", p.state == Player.State.BUSTED, "t=%.1f" % t)
	await wait(6.0)
	check("respawned after arrest, wanted cleared", p.state == Player.State.GROUND and pol.wanted_level == 0,
		"state=%d level=%d" % [p.state, pol.wanted_level])
	# ---- level 3: officers shoot
	p.health.invulnerable = false
	var hp0 := p.health.health
	pol.set_wanted(3)
	pol.last_seen = p.global_position
	t = 0.0
	var shot_at := false
	var shots := [0]
	Events.gunshot.connect(func(pos, shooter, l): if shooter is NPC: shots[0] += 1)
	while t < 80.0 and not shot_at:
		await wait(0.5)
		t += 0.5
		p.health.health = maxf(p.health.health, 60.0)
		shot_at = shots[0] > 2
	check("officers open fire at level 3", shot_at, "%d police shots, t=%.1f" % [shots[0], t])
	# ---- evade
	pol.set_wanted(1)
	p.health.invulnerable = true
	var far := world.data.nearest_poi("hospital", p.global_position)
	p.teleport(far["entrance_v"] + far["facing_v"] * 3.0)
	world.streaming.load_area_blocking(p.global_position, 200.0)
	t = 0.0
	var saw_search := false
	while pol.wanted_level > 0 and t < 60.0:
		await wait(0.5)
		t += 0.5
		saw_search = saw_search or pol.searching
	check("police search then give up", saw_search and pol.wanted_level == 0, "search=%s level=%d t=%.1f" % [saw_search, pol.wanted_level, t])
	var failed := results.filter(func(r): return not r[1]).size()
	print("=== %d checks, %d failed ===" % [results.size(), failed])
	get_tree().quit(1 if failed > 0 else 0)


func check(n: String, ok: bool, info := "") -> void:
	results.append([n, ok])
	print(("PASS " if ok else "FAIL ") + n + ("  (" + info + ")" if info != "" else ""))


func wait(t: float) -> void:
	await get_tree().create_timer(t).timeout
