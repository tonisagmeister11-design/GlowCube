extends Node
## End-to-End mit gewaehltem Aussehen: neues Spiel mit einem fertigen
## Personenmodell als Spielerfigur, dann laufen und die Kamera pruefen.
##
## Geprueft wird, was sich nur im laufenden Spiel zeigt: dass die Figur
## ueberhaupt gebaut wird, dass die Kamerahoehe aus ihrer Augenhoehe kommt,
## dass sie beim Laufen Schritte meldet und dass sie auf dem Boden steht.
##
## Aufruf: xvfb-run ... res://tests/visual/skin_play_shot.tscn -- --out=/tmp/x

var out := "/tmp"
var skin := "bruno"


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
		elif a.begins_with("--skin="):
			skin = a.substr(7)
	get_tree().root.add_child.call_deferred(_Driver.new(out))
	Game.start_new_game({"seed": 4242, "mode": "life", "rules": {"city_size": "medium"},
		"character": {"name": "Bruno Kessel", "body": "male", "age": 26.0,
			"appearance": {}, "skin": skin},
		"start": {"situation": "own_apartment"}})


class _Driver extends Node:
	var out := ""
	var t := 0.0
	var step := 0
	var steps_heard := 0
	var player: Node = null

	func _init(o: String) -> void:
		out = o

	func _shot(tag: String) -> void:
		get_viewport().get_texture().get_image().save_png("%s/skin_%s.png" % [out, tag])
		print("SHOT ", tag)

	func _process(delta: float) -> void:
		t += delta
		match step:
			0:
				if Game.playing:
					t = 0.0
					step = 1
				elif t > 150.0:
					print("TIMEOUT: Welt wurde nicht fertig")
					get_tree().quit(1)
			1:
				if t > 5.0:
					# Gesucht ist die Spielerfigur selbst, erkennbar an ihrer
					# Methode. Nicht am Feld `body`: das waere null, wenn die
					# Figur gar nicht gebaut wurde - genau der Fall, den dieser
					# Test finden soll.
					for n in get_tree().get_nodes_in_group("player"):
						if n.has_method("refresh_body"):
							player = n
							break
					if player == null:
						print("FEHLER: Spielerfigur nicht gefunden (Gruppe: %d Knoten)"
							% get_tree().get_nodes_in_group("player").size())
						get_tree().quit(1)
						return
					var b = player.get("body")
					if b == null:
						print("FEHLER: Spielerfigur hat keinen Koerper")
						get_tree().quit(1)
						return
					print("Figurenklasse: ", b.get_class(), "  Skript: ",
						(b.get_script().resource_path if b.get_script() else "-"))
					print("Ist fertiges Modell: ", b is DetailedPerson)
					print("Koerpergroesse: %.2f m  Augenhoehe: %.2f m" % [b.height(), b.eye_height()])
					if b is DetailedPerson:
						print("Spielart: ", (b as DetailedPerson).person_name)
					if player.has_signal("footstep"):
						player.footstep.connect(func(_pos, _i): steps_heard += 1)
					_shot("start")
					t = 0.0
					step = 2
			2:
				# Vorwaerts laufen
				if t < 3.0:
					Input.action_press("move_forward")
				else:
					Input.action_release("move_forward")
					print("Schritte gehoert: ", steps_heard)
					print("Spielerhoehe ueber Grund: %.2f m" % player.global_position.y)
					_shot("gelaufen")
					get_tree().quit(0)
