extends Node
## End-to-End: neues Spiel -> Ladebildschirm -> Welt mit HUD -> Laden oeffnen.
## Aufruf: xvfb-run ... res://tests/visual/play_shot.tscn -- --out=/tmp/x

var out := "/tmp"


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
	get_tree().root.add_child.call_deferred(_Driver.new(out))
	Game.start_new_game({"seed": 777, "mode": "life", "rules": {"city_size": "medium"},
		"character": {"name": "Mara Lindner", "body": "female", "age": 24.0, "appearance": {}},
		"start": {"situation": "own_apartment"}})


class _Driver extends Node:
	var out := ""
	var t := 0.0
	var step := 0
	var t0 := 0

	func _init(o: String) -> void:
		out = o
		t0 = Time.get_ticks_msec()

	func _shot(name: String) -> void:
		get_viewport().get_texture().get_image().save_png(out + "/" + name + ".png")
		print("SHOT ", name, " t=", (Time.get_ticks_msec() - t0) / 1000.0)

	func _process(delta: float) -> void:
		t += delta
		match step:
			0:
				if t > 1.5:
					_shot("loading")
					step = 1
			1:
				if Game.playing:
					print("WORLD READY after ", (Time.get_ticks_msec() - t0) / 1000.0, " s")
					t = 0.0
					step = 2
				elif t > 120.0:
					print("TIMEOUT waiting for world")
					get_tree().quit(1)
			2:
				if t > 6.0:
					_shot("world_hud")
					var w: WorldState = Game.world
					var shop := -1
					for b in w.city.buildings:
						if b.business_type == "supermarket":
							shop = b.id
							break
					w.time_minutes = 10 * 60.0
					EventBus.open_panel.emit("building", {"building": shop})
					t = 0.0
					step = 3
			3:
				if t > 1.0:
					_shot("shop")
					get_tree().quit(0)
