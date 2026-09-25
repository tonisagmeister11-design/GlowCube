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
	check("interior manager with 4 interiors", im != null and im.meta.size() == 4, str(im.meta.keys() if im else []))
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
