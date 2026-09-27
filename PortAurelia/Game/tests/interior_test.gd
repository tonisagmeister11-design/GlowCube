extends Node
## Headless interior test: enter/leave store, clerk and counter menu, shooting range,
## safehouse sleep.

var world: GameWorld
var results := []


func _ready() -> void:
	Game.player_data = PlayerData.new()
	Game.pending_slot = -2
	world = load("res://scenes/world.tscn").instantiate()
	add_child(world)
	await Events.world_ready
	var im := InteriorManager.get_manager()
	var p := world.player as Player
	check("interior manager with 6 interiors", im != null and im.meta.size() == 6, str(im.meta.keys() if im else []))
	var shop := world.data.nearest_poi("shop_convenience", p.global_position)
	await im.enter(shop)
	await wait(1.0)
	check("entered store", im.is_inside() and p.global_position.y < -200.0, "y=%.1f" % p.global_position.y)
	check("standing on interior floor", p.is_on_floor(), "y=%.2f" % p.global_position.y)
	check("clerk present", im._npcs.size() == 1)
	var eco := world.economy as PoiManager
	eco._open_store(p, shop)
	await get_tree().process_frame
	check("store menu opens inside", MenuPanel.is_open())
	MenuPanel.current.close()
	# robbery: keep threatening the shopkeeper -> cash bundles drop, alarm raises the wanted level
	var clerk: NPC = im._npcs[0]
	p.weapons.give("pistol", 60)
	p.weapons.equip("pistol")
	var money0 := Game.player_data.money
	for i in 60:
		clerk.threatened_by(p)
		await wait(0.15)
	var drops := im.current.find_children("*", "Pickup", true, false)
	check("robbery drops loot", not im._rob.is_empty() and bool(im._rob["done"]) and drops.size() == InteriorManager.ROB_DROPS,
		"drops %d, paid $%d" % [drops.size(), int(im._rob.get("paid", 0))])
	check("shopkeeper hands up", clerk.state == NPC.S.HANDS_UP, "state %d" % clerk.state)
	check("robbery alarm wanted", int(world.police.wanted_level) >= 2, "wanted %d" % int(world.police.wanted_level))
	for d in drops:
		if not is_instance_valid(d):
			continue
		p.global_position = (d as Node3D).global_position
		await wait(0.1)
	check("loot collected", Game.player_data.money > money0 + 1000, "$%d -> $%d" % [money0, Game.player_data.money])
	world.police.call("set_wanted", 0)
	await im.leave()
	await wait(0.5)
	check("left store to street", not im.is_inside() and p.global_position.distance_to(shop["entrance_v"]) < 5.0,
		"d=%.1f" % p.global_position.distance_to(shop["entrance_v"]))
	# gun shop range
	var gs := world.data.nearest_poi("shop_weapons", p.global_position)
	p.weapons.give("pistol", 60)
	await im.enter(gs)
	await wait(0.5)
	im._start_range()
	check("range started", not im._range.is_empty() and im._range["targets"].size() == 3)
	await wait(1.5)
	var lit = im._range["targets"].filter(func(t): return t.lit)
	if not lit.is_empty():
		lit[0].on_hit(10.0, p, Vector3.ZERO, Vector3.ZERO)
	check("range counts hits", int(im._range["score"]) == 1, str(im._range.get("score")))
	await im.leave()
	# jewelry: smashing a display case scatters jewels and trips the alarm
	var jw := world.data.nearest_poi("jewelry", p.global_position)
	if not jw.is_empty():
		await im.enter(jw)
		await wait(0.5)
		var cases := im.current.find_children("*", "DisplayCase", true, false)
		check("jewelry display cases", cases.size() == 6, str(cases.size()))
		if cases.size() > 0:
			(cases[0] as DisplayCase).on_hit(40.0, p, Vector3.ZERO, Vector3.ZERO)
			await wait(0.2)
			check("case smashed -> jewels + alarm", (cases[0] as DisplayCase).broken and int(world.police.wanted_level) >= 3,
				"wanted %d" % int(world.police.wanted_level))
		world.police.call("set_wanted", 0)
		await im.leave()
	# gun shop owner fights back
	await im.enter(gs)
	await wait(0.5)
	var owner: NPC = im._npcs[0]
	p.health.invulnerable = true
	owner.threatened_by(p)
	await wait(0.3)
	check("gun shop owner fights back", owner.hostile and im._npcs.size() == 2, "guards %d" % (im._npcs.size() - 1))
	await im.leave()
	p.health.invulnerable = false
	world.police.call("clear_wanted")
	# safehouse sleep
	var sh := world.data.nearest_poi("safehouse", p.global_position)
	await im.enter(sh)
	await wait(0.5)
	check("safehouse markers", im._markers.size() == 4, str(im._markers.size()))
	var h0: float = world.day_night.hour
	await eco._sleep()
	var dh := fposmod(world.day_night.hour - h0, 24.0)
	check("sleep advances 6 h", dh > 5.9 and dh < 6.5, "%.2f h" % dh)
	await im.leave()
	var failed := results.filter(func(r): return not r[1]).size()
	print("=== %d checks, %d failed ===" % [results.size(), failed])
	get_tree().quit(1 if failed > 0 else 0)


func check(n: String, ok: bool, info := "") -> void:
	results.append([n, ok])
	print(("PASS " if ok else "FAIL ") + n + ("  (" + info + ")" if info != "" else ""))


func wait(t: float) -> void:
	await get_tree().create_timer(t).timeout
