extends Node
## Headless economy test: shops, weapons, property, mechanic, dealer, save slot, bus travel.

var world: GameWorld
var results := []


func _ready() -> void:
	Game.player_data = PlayerData.new()
	Game.pending_slot = -2
	world = load("res://scenes/world.tscn").instantiate()
	add_child(world)
	await Events.world_ready
	var eco: PoiManager = world.economy
	var p := world.player as Player
	var pd := Game.player_data
	pd.money = 20000
	check("markers created", eco.markers.size() >= 25, str(eco.markers.size()))
	check("radar blips", eco.blips().size() > 0, str(eco.blips().size()))
	# weapon store
	var ws := world.data.nearest_poi("shop_weapons", p.global_position)
	eco._open_weapons(p, ws)
	await get_tree().process_frame
	check("menu opens and locks input", MenuPanel.is_open() and not p.input_enabled)
	var items: Array = MenuPanel.current.items
	var pistol: Dictionary = items.filter(func(i): return String(i["label"]).begins_with("Pistole"))[0]
	MenuPanel.current._choose(pistol)
	check("buy pistol", p.weapons.has_weapon("pistol") and pd.money == 20000 - 1200, "money %d" % pd.money)
	MenuPanel.current.close()
	await get_tree().process_frame
	check("menu closes and unlocks input", not MenuPanel.is_open() and p.input_enabled)
	# food heals
	p.health.health = 30.0
	eco._open_store(p, world.data.nearest_poi("shop_convenience", p.global_position))
	await get_tree().process_frame
	MenuPanel.current._choose(MenuPanel.current.items[1])
	check("food heals", p.health.health > 70.0, "hp %.0f" % p.health.health)
	MenuPanel.current.close()
	# property
	var prop := world.data.nearest_poi("property_apartment", p.global_position)
	pd.money = 50000
	eco._open_property(p, prop)
	await get_tree().process_frame
	MenuPanel.current._choose(MenuPanel.current.items[0])
	check("buy property", eco._owns(prop) and pd.money == 5000, "money %d" % pd.money)
	await get_tree().process_frame
	if MenuPanel.is_open():
		MenuPanel.current.close()
	# dealer
	pd.money = 30000
	var dealer := world.data.nearest_poi("car_dealer", p.global_position)
	eco._buy_vehicle(dealer, "sedan")
	check("buy vehicle", pd.owned_vehicles.size() == 1)
	# mechanic upgrade + repair
	var v := Vehicle.create("sedan")
	world.add_child(v)
	v.global_position = p.global_position + Vector3(0, 0.6, 6)
	await wait(0.5)
	v.body_health = 300.0
	v.engine_health = 400.0
	var mech := world.data.nearest_poi("mechanic", p.global_position)
	eco._open_mechanic(mech, v)
	await get_tree().process_frame
	MenuPanel.current._choose(MenuPanel.current.items[0])
	check("mechanic repairs", v.body_health == 1000.0 and v.engine_health == 1000.0)
	eco._open_mechanic(mech, v)
	await get_tree().process_frame
	var eng: Dictionary = MenuPanel.current.items.filter(func(i): return String(i["label"]).begins_with("Motor"))[0]
	MenuPanel.current._choose(eng)
	check("engine upgrade", int(v.upgrades["engine"]) == 1)
	# save to slot 3
	check("save slot", SaveManager.save_slot(2) and SaveManager.has_slot(2))
	var info := SaveManager.slot_info(2)
	check("slot info", int(info.get("money", -1)) == pd.money, str(info))
	SaveManager.delete_slot(2)
	# bus travel
	var start := p.global_position
	var hosp := world.data.nearest_poi("hospital", p.global_position)
	await eco._travel(hosp, start.distance_to(hosp["entrance_v"]))
	check("bus travel teleports", p.global_position.distance_to(hosp["entrance_v"]) < 8.0, "d=%.1f" % p.global_position.distance_to(hosp["entrance_v"]))
	var failed := results.filter(func(r): return not r[1]).size()
	print("=== %d checks, %d failed ===" % [results.size(), failed])
	get_tree().quit(1 if failed > 0 else 0)


func check(n: String, ok: bool, info := "") -> void:
	results.append([n, ok])
	print(("PASS " if ok else "FAIL ") + n + ("  (" + info + ")" if info != "" else ""))


func wait(t: float) -> void:
	await get_tree().create_timer(t).timeout
