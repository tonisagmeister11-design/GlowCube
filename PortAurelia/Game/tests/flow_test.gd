extends Node
## Game flow test: main menu -> new game -> loading screen -> world -> pause -> save -> load.
## This node is autoloaded-free: it survives scene changes by living under /root.

var results := []
var step := 0


func _ready() -> void:
	# reparent to root so scene changes don't free the test
	var r := get_tree().root
	get_parent().remove_child.call_deferred(self)
	r.add_child.call_deferred(self)
	await get_tree().process_frame
	await get_tree().process_frame
	_run()


func _run() -> void:
	get_tree().change_scene_to_file("res://scenes/main_menu.tscn")
	await wait(1.0)
	check("main menu loaded", get_tree().current_scene != null and get_tree().current_scene.name == "MainMenu")
	var buttons := get_tree().current_scene.find_children("*", "Button", true, false)
	var labels := buttons.map(func(b): return (b as Button).text)
	check("menu buttons", labels.has("SPIEL STARTEN") and labels.has("FORTSETZEN") and labels.has("LADEN") \
		and labels.has("EINSTELLUNGEN") and labels.has("BEENDEN"), str(labels))
	Game.new_game()
	await Events.world_ready
	check("world ready after loading screen", GameWorld.instance != null and Game.state == Game.State.PLAYING)
	await wait(1.0)
	var pm: PauseMenu = GameWorld.instance.get_node_or_null("PauseMenu")
	check("pause menu present", pm != null)
	pm._open()
	await wait(0.2)
	check("game paused", get_tree().paused and Game.state == Game.State.PAUSED)
	pm._resume()
	await wait(0.2)
	check("game resumed", not get_tree().paused)
	Game.player_data.money = 4242
	Game.player_data.missions["m01"] = "done"
	var entry := {"id": "veh_test", "type": "sports", "color": "#cc2211", "upgrades": {"engine": 2, "brakes": 0, "armor": 0, "tires": 0}}
	Game.player_data.owned_vehicles.append(entry)
	var pl := GameWorld.instance.player
	var car := (GameWorld.instance.economy as PoiManager).spawn_owned_vehicle(entry, pl.global_position + Vector3(6, 0, 0), Vector3.FORWARD)
	await wait(1.0)
	var car_pos := car.global_position
	check("save slot 1", SaveManager.save_slot(0))
	Game.load_game(0)
	await Events.world_ready
	check("load restores money", Game.player_data.money == 4242, str(Game.player_data.money))
	check("load keeps story progress", Game.player_data.missions.get("m01", "") == "done", str(Game.player_data.missions))
	await wait(3.0)
	var restored: Vehicle = null
	for v in get_tree().get_nodes_in_group("vehicles"):
		if (v as Vehicle).owned_id == "veh_test":
			restored = v
	check("parked personal vehicle restored", restored != null and restored.global_position.distance_to(car_pos) < 3.0 \
		and int(restored.upgrades["engine"]) == 2)
	SaveManager.delete_slot(0)
	Game.to_main_menu()
	await wait(1.0)
	check("back to main menu", get_tree().current_scene.name == "MainMenu")
	var failed := results.filter(func(x): return not x[1]).size()
	print("=== %d checks, %d failed ===" % [results.size(), failed])
	get_tree().quit(1 if failed > 0 else 0)


func check(n: String, ok: bool, info := "") -> void:
	results.append([n, ok])
	print(("PASS " if ok else "FAIL ") + n + ("  (" + info + ")" if info != "" else ""))


func wait(t: float) -> void:
	await get_tree().create_timer(t, true).timeout
