extends Node
## Renders screenshots of the UI screens: main menu, HUD, map, phone, pause, settings.
##   godot --path Game res://tests/ui_shots.tscn -- --out dir

var out := "user://"


func _ready() -> void:
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		if a[i] == "--out":
			out = a[i + 1]
	var r := get_tree().root
	get_parent().remove_child.call_deferred(self)
	r.add_child.call_deferred(self)
	await get_tree().process_frame
	await get_tree().process_frame
	get_tree().change_scene_to_file("res://scenes/main_menu.tscn")
	await wait(3.0)
	await shot("ui_menu")
	Game.new_game()
	await Events.world_ready
	await wait(25.0)
	await shot("ui_hud")
	var w := GameWorld.instance
	w.get_node("MapScreen").call("open")
	await wait(2.0)
	await shot("ui_map")
	w.get_node("MapScreen").call("close")
	await wait(0.5)
	w.get_node("Phone").call("open_home")
	await wait(1.5)
	await shot("ui_phone")
	MenuPanel.current.close()
	var eco := w.economy as PoiManager
	eco._open_weapons(w.player, w.data.nearest_poi("shop_weapons", w.player.global_position))
	await wait(1.5)
	await shot("ui_shop")
	MenuPanel.current.close()
	var pm = w.get_node("PauseMenu")
	pm.call("_open")
	await wait(1.5)
	await shot("ui_pause")
	pm.call("_settings")
	await wait(1.5)
	await shot("ui_settings")
	get_tree().quit()


func shot(n: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png(out.path_join(n + ".png"))
	print("shot ", n)


func wait(t: float) -> void:
	await get_tree().create_timer(t, true, false, true).timeout
