extends Node
## Spielt den Hinterhalt im echten Spiel durch und macht Bilder:
## Verfolger taucht auf, Werfer angelegt, Absprung, Einschlag.
## Aufruf: ... res://tests/visual/ambush_shot.tscn -- --out=/tmp/x [--jump=0]

var out := "/tmp"
var do_jump := true


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
		elif a.begins_with("--jump="):
			do_jump = a.substr(7) != "0"
	Perf.set_level(2, false)
	get_tree().root.add_child.call_deferred(_Driver.new(out, do_jump))
	Game.start_new_game({"seed": 2026, "mode": "life", "rules": {"city_size": "small", "population": 500},
		"character": {"name": "Luca Valcorvo", "body": "male", "age": 28.0, "appearance": {}},
		"start": {"situation": "own_apartment"}})


class _Driver extends Node:
	var out := ""
	var do_jump := true
	var t := 0.0
	var step := 0
	var runner: Node
	var shots := 0

	func _init(o: String, j: bool) -> void:
		out = o
		do_jump = j

	func _shot(name: String) -> void:
		get_viewport().get_texture().get_image().save_png("%s/ambush_%s.png" % [out, name])
		shots += 1
		print("SHOT ", name)

	func _process(delta: float) -> void:
		t += delta
		match step:
			0:
				if Game.playing:
					_setup_mission()
					t = 0.0
					step = 1
				elif t > 150.0:
					print("TIMEOUT: Welt nicht bereit")
					get_tree().quit(1)
			1:
				# Warten, bis der Verfolger erschienen ist
				if runner != null and runner.chase != null:
					_shot("verfolger")
					t = 0.0
					step = 2
				elif t > 30.0:
					print("TIMEOUT: kein Verfolger (scripted=%s)" % str(runner.scripted if runner else "?"))
					get_tree().quit(1)
			2:
				# Warten, bis gezielt wird
				if runner != null and runner.aiming:
					_shot("zielen")
					t = 0.0
					step = 3
				elif t > 30.0:
					print("TIMEOUT: kein Zielen")
					get_tree().quit(1)
			3:
				if t > 0.6:
					if do_jump:
						runner.debug_force_jump()
						_shot("absprung")
					t = 0.0
					step = 4
			4:
				if t > 2.2:
					_shot("einschlag")
					print("ERGEBNIS: gesprungen=%s, Mission aktiv=%s, Schritt=%d, Leben=%.0f" % [
						str(do_jump), str(Game.world.story.is_active()), int(Game.world.story.step),
						float(Game.world.player.needs.health)])
					get_tree().quit(0)

	func _setup_mission() -> void:
		var w: WorldState = Game.world
		var st = w.story
		var g = w.crime.gangs
		var gid := 0
		if "gangs" in g and not g.gangs.is_empty():
			gid = int(g.gangs.keys()[0])
		if g.has_method("contact"):
			g.contact(w, gid)
		if "player_gang" in g and int(g.player_gang) < 0:
			g.player_gang = gid
		# Direkt die Hinterhalt-Mission beim Fahrschritt starten
		st.unlocked.append("ch1")
		st.unlocked.append("ch2")
		st.accept("m_hinterhalt")
		st.step = 1
		st.step_data = {}
		runner = get_tree().get_first_node_in_group("story_runner")
		if runner == null:
			print("FEHLER: kein StoryRunner")
			get_tree().quit(1)
			return
		# Inszenierung sofort ausloesen, Vorlauf verkuerzen
		runner._start_scripted("ambush")
		runner.scripted_t = 5.2
