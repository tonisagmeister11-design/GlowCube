extends Node
## Kreativmodus im laufenden Spiel: Geld, Fliegen, Fahrzeug geben, Menue oeffnen.
var out := "/tmp"


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
	get_tree().root.add_child.call_deferred(_Driver.new(out))
	Game.start_new_game({"seed": 4242, "mode": "creative", "rules": {"city_size": "small"},
		"character": {"name": "Tester", "body": "male", "age": 30.0, "appearance": {}},
		"start": {"situation": "own_apartment"}})


class _Driver extends Node:
	var out := ""
	var t := 0.0
	var step := 0
	var player: Node = null
	var y0 := 0.0

	func _init(o: String) -> void:
		out = o

	func _shot(tag: String) -> void:
		get_viewport().get_texture().get_image().save_png("%s/creative_%s.png" % [out, tag])

	func _process(delta: float) -> void:
		t += delta
		match step:
			0:
				if Game.playing:
					t = 0.0
					step = 1
			1:
				if t > 4.0:
					for n in get_tree().get_nodes_in_group("player"):
						if n.has_method("set_fly"):
							player = n
					var w = Game.world
					print("Modus: ", w.rules.mode, "  Konto: ", w.ledger.balance(int(w.player.account)))
					CreativeTools.give_money(w, 5000000.0)
					print("Konto nach +5 Mio: ", w.ledger.balance(int(w.player.account)))
					print("Fahrzeuge gegeben: ", CreativeTools.give_all_vehicles(w))
					y0 = player.global_position.y
					player.set_fly(true)
					print("Fliegen an: ", player.fly_mode)
					Input.action_press("jump")
					t = 0.0
					step = 2
			2:
				if t > 2.0:
					Input.action_release("jump")
					print("Hoehe: vorher %.2f nachher %.2f" % [y0, player.global_position.y])
					_shot("flug")
					EventBus.open_panel.emit("creative", {})
					t = 0.0
					step = 3
			3:
				if t > 1.0:
					_shot("menue")
					get_tree().quit(0)
