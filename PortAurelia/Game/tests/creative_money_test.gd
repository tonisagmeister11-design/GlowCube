extends Node
## Creative mode alone: the O key opens "Geld nehmen" and gives money.
##   godot --headless --path Game res://tests/creative_money_test.tscn

var world: GameWorld
var results := []


func _ready() -> void:
	Game.player_data = PlayerData.new()
	Game.player_data.world_state["creative"] = true
	Game.pending_slot = -2
	world = load("res://scenes/world.tscn").instantiate()
	add_child(world)
	await Events.world_ready
	await get_tree().create_timer(1.5).timeout
	Game.state = Game.State.PLAYING
	var m0 := Game.player_data.money
	var ev := InputEventKey.new()
	ev.keycode = KEY_O
	ev.physical_keycode = KEY_O
	ev.pressed = true
	Input.parse_input_event(ev)
	await get_tree().create_timer(0.3).timeout
	var up := ev.duplicate()
	up.pressed = false
	Input.parse_input_event(up)
	check("O opens the money menu in creative mode", MenuPanel.is_open() and MenuPanel.current.title == "Geld nehmen",
		MenuPanel.current.title if MenuPanel.current else "no menu")
	if MenuPanel.is_open():
		(MenuPanel.current.items[2]["action"] as Callable).call()
	check("taking money works", Game.player_data.money == m0 + 1000000, "%d -> %d" % [m0, Game.player_data.money])
	if MenuPanel.is_open():
		MenuPanel.current.close()
	# not in creative: O does nothing
	CreativeMode.set_enabled(false)
	await get_tree().create_timer(0.3).timeout
	Input.parse_input_event(ev)
	await get_tree().create_timer(0.3).timeout
	Input.parse_input_event(up)
	check("without creative mode O gives no money menu", not MenuPanel.is_open())
	var failed := results.filter(func(r): return not r[1]).size()
	print("=== %d checks, %d failed ===" % [results.size(), failed])
	get_tree().quit(1 if failed > 0 else 0)


func check(n: String, ok: bool, info := "") -> void:
	results.append([n, ok])
	print(("PASS " if ok else "FAIL ") + n + ("  (" + info + ")" if info != "" else ""))
