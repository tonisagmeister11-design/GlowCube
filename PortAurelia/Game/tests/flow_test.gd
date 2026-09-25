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
	check("save slot 1", SaveManager.save_slot(0))
	Game.load_game(0)
	await Events.world_ready
	check("load restores money", Game.player_data.money == 4242, str(Game.player_data.money))
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
