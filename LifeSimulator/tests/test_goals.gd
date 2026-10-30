extends RefCounted
## Tests fuer die Lebensziele: Auswahl nach Lebensphase, Erfuellung im
## simulierten Leben, Belohnungen, Speichern/Laden und die Anzeige im HUD.

var runner
const F := preload("res://tests/sim_fixture.gd")


func _ids(list: Array) -> Array:
	return list.map(func(g): return str(g.id))


func test_goals_system_present() -> void:
	var w := F.small({"name": "Zoe Ziel", "age": 24.0, "body": "female"}, {"situation": "own_apartment"})
	runner.check(w.goals != null, "Goals-System vorhanden")
	runner.check(w.goals is Goals, "richtige Klasse")
	var order := []
	for e in WorldState.SYSTEM_ORDER:
		order.append(str(e[0]))
	runner.check(order.find("goals") > order.find("player"), "Goals nach player in SYSTEM_ORDER")


func test_nothing_completed_at_start() -> void:
	for sit in ["own_apartment", "homeless", "wealthy", "with_parents"]:
		var age: float = 10.0 if sit == "with_parents" else 28.0
		var w := F.small({"name": "Start", "age": age, "body": "male"}, {"situation": sit})
		runner.check(w.goals.completed.is_empty(), "keine Erfuellung beim Start (%s)" % sit)
		for e in w.events:
			runner.check(str(e[0]) != "goal_completed", "kein Zielereignis beim Start (%s)" % sit)
		runner.check(not w.goals.active(w).is_empty(), "offene Ziele vorhanden (%s)" % sit)
		runner.check(w.goals.active(w).size() <= Goals.MAX_ACTIVE, "hoechstens %d Ziele" % Goals.MAX_ACTIVE)


func test_child_goals_are_age_appropriate() -> void:
	var w := F.small({"name": "Lina Klein", "age": 9.0, "body": "female"}, {"situation": "with_parents"}, "free")
	var ids := _ids(w.goals.active(w))
	runner.check("school_day" in ids or "school_done" in ids, "Schulziel fuer ein Kind: %s" % str(ids))
	for forbidden in ["rent", "car", "property", "family", "office", "police"]:
		runner.check(not (forbidden in ids), "kein Erwachsenenziel '%s' fuer ein Kind" % forbidden)
	# Die Schule wird im Spiel besucht: Unterrichtsstunden erfuellen das Ziel.
	w.education.attend(w, "player", 0, 3.0)
	w.goals.on_hour(w, w.get_day(), w.get_hour())
	runner.check(w.goals.completed.has("school_day"), "Schulbesuch erfuellt das Ziel")


func test_adult_job_goal_chain() -> void:
	var w := F.small({"name": "Jan Jobsuch", "age": 27.0, "body": "male"}, {"situation": "own_apartment"})
	var ids := _ids(w.goals.active(w))
	runner.check("job" in ids, "Erwachsener sucht Arbeit: %s" % str(ids))
	runner.check(not ("first_shift" in ids), "Schichtziel erst nach der Stelle")
	# Eine passende Stelle annehmen
	var pid := -1
	for o in w.jobs.list_openings(w, "player", 0):
		if bool(o.get("eligible", false)):
			pid = int(o.position)
			break
	runner.check(pid >= 0, "geeignete Stelle gefunden")
	runner.check(w.player.apply_job(w, pid).ok, "Stelle angenommen")
	var skill0: float = float(w.player.skills.get("charisma", 0.0))
	var money0: float = w.ledger.balance(w.player.account)
	w.goals.on_hour(w, w.get_day(), w.get_hour())
	runner.check(w.goals.completed.has("job"), "Ziel 'Finde eine Arbeit' erfuellt")
	runner.check(float(w.player.skills.get("charisma", 0.0)) > skill0, "Erfahrung als Belohnung gutgeschrieben")
	runner.check_near(w.ledger.balance(w.player.account), money0, 0.01, "Ziele erschaffen kein Geld")
	var ev := false
	for e in w.events:
		if str(e[0]) == "goal_completed" and str(e[1][0].id) == "job":
			ev = true
	runner.check(ev, "Ereignis goal_completed")
	var ids2 := _ids(w.goals.active(w))
	runner.check("first_shift" in ids2, "naechstes Ziel ist die erste Schicht: %s" % str(ids2))
	# Schicht arbeiten
	var pos: Dictionary = w.jobs.positions[pid]
	pos.days_worked = 1
	w.goals.on_hour(w, w.get_day(), w.get_hour())
	runner.check(w.goals.completed.has("first_shift"), "erste Schicht erfuellt")


func test_money_and_skill_goals() -> void:
	var w := F.small({"name": "Mia Mehr", "age": 30.0, "body": "female"}, {"situation": "own_apartment"})
	runner.check(not w.goals.completed.has("save_5000"), "Sparziel offen")
	w.ledger.mint(w.player.account, 9000.0, "Test")
	w.goals.on_hour(w, w.get_day(), w.get_hour())
	runner.check(w.goals.completed.has("save_5000"), "Sparziel erfuellt")
	w.player.skills.cooking = 60.0
	w.goals.on_hour(w, w.get_day(), w.get_hour())
	runner.check(w.goals.completed.has("skill_50"), "Faehigkeitsziel erfuellt")
	# Faehigkeitsbelohnung des Autoziels
	var before: float = float(w.player.skills.driving)
	w.player.vehicles.append({"good": "car_small", "name": "Testwagen", "bought_day": 0})
	w.goals.on_hour(w, w.get_day(), w.get_hour())
	runner.check(w.goals.completed.has("car"), "Autoziel erfuellt")
	runner.check(float(w.player.skills.driving) > before, "Faehigkeit als Belohnung")


func test_eat_goal_needs_recovery() -> void:
	var w := F.small({"name": "Hugo Hunger", "age": 26.0, "body": "male"}, {"situation": "own_apartment"})
	runner.check(not w.goals.completed.has("eat"), "Essensziel zu Beginn offen")
	w.player.needs.hunger = 30.0
	w.goals.on_hour(w, w.get_day(), w.get_hour())
	runner.check(not w.goals.completed.has("eat"), "Hunger allein erfuellt nichts")
	w.player.needs.hunger = 85.0
	w.goals.on_hour(w, w.get_day(), w.get_hour())
	runner.check(w.goals.completed.has("eat"), "nach dem Essen erfuellt")


func test_simulated_life_progresses() -> void:
	var w := F.small({"name": "Rita Lauf", "age": 23.0, "body": "female"}, {"situation": "shared_room"})
	var before: int = w.goals.completed.size()
	w.advance(1440.0 * 3.0)
	runner.check(w.goals.completed.size() >= before, "Ziele bleiben konsistent")
	runner.check(not w.goals.active(w).is_empty(), "immer ein naechster Schritt")
	for g in w.goals.active(w):
		runner.check(str(g.title) != "" and str(g.hint) != "", "Titel und Hinweis gesetzt")
		runner.check(str(g.reward_text) != "", "Belohnungstext")


func test_places_and_today_and_intro() -> void:
	var w := F.small({"name": "Ort Otto", "age": 25.0, "body": "male"}, {"situation": "own_apartment"})
	var with_place := 0
	for g in w.goals.active(w):
		if bool(g.has_pos):
			with_place += 1
			runner.check(str(g.place_label) != "", "Ortsbezeichnung vorhanden")
			runner.check(g.pos is Vector2, "Ortsposition ist Vector2")
	runner.check(with_place > 0, "mindestens ein Ziel mit Ort")
	var today: Array = w.goals.today_lines(w)
	runner.check(today.size() >= 2, "Heute-Zeilen: %s" % str(today))
	var has_shop := false
	for line in today:
		if str(line).contains("Supermärkte"):
			has_shop = true
	runner.check(has_shop, "Ladenoeffnungszeiten in der Heute-Zeile")
	var intro: Array = w.goals.intro_lines(w)
	runner.check(intro.size() >= 4 and intro.size() <= 7, "Introkarte 4-6 Zeilen (%d)" % intro.size())
	runner.check(str(intro[0]).contains("Ort Otto"), "Intro nennt die Spielfigur")
	for line in intro:
		runner.check(str(line) != "", "keine leere Introzeile")


func test_save_roundtrip() -> void:
	var w := F.small({"name": "Sara Speicher", "age": 29.0, "body": "female"}, {"situation": "own_apartment"})
	w.ledger.mint(w.player.account, 9000.0, "Test")
	w.goals.on_hour(w, w.get_day(), w.get_hour())
	w.goals.intro_shown = true
	runner.check(w.goals.completed.has("save_5000"), "Ziel erfuellt vor dem Speichern")
	var data: Dictionary = w.to_save()
	runner.check(data.systems.has("goals"), "Ziele im Spielstand")
	var w2 := WorldState.from_save(data)
	runner.check(w2.goals != null, "Ziele nach dem Laden vorhanden")
	runner.check(w2.goals.completed.has("save_5000"), "erfuelltes Ziel erhalten")
	runner.check(bool(w2.goals.intro_shown), "Introkarte bleibt gesehen")
	runner.check_eq(w2.goals.skipped.size(), w.goals.skipped.size(), "uebersprungene Ziele erhalten")


func test_hud_builds_with_tracker() -> void:
	var w := WorldState.create_new({"seed": 7, "mode": "life", "rules": {"city_size": "small", "population": 400},
		"character": {"name": "HUD Hanna", "body": "female", "age": 26.0}, "start": {"situation": "own_apartment"}})
	Game.world = w
	var ui: Control = load("res://scenes/ui/game_ui.tscn").instantiate()
	runner.add_child(ui)
	await runner.get_tree().process_frame
	await runner.get_tree().process_frame
	runner.check(ui.get("_goal_box") != null, "Zielverfolger im HUD")
	var titles := []
	for c in ui._goal_box.get_children():
		if c is Button:
			titles.append(str(c.text))
	runner.check(titles.size() >= 2, "Hilfeknopf und mindestens ein Ziel: %s" % str(titles))
	var help_found := false
	for t in titles:
		if str(t).contains("Was kann ich tun?"):
			help_found = true
	runner.check(help_found, "Knopf 'Was kann ich tun?'")
	ui._open_help()
	await runner.get_tree().process_frame
	runner.check(ui.get("_panel") != null, "Hilfefenster offen")
	ui._close_panel()
	await runner.get_tree().process_frame
	ui._show_intro(w.goals.intro_lines(w))
	await runner.get_tree().process_frame
	runner.check(ui.get("_intro") != null, "Introkarte gebaut")
	ui._close_intro()
	await runner.get_tree().process_frame
	runner.check(ui.get("_intro") == null, "Introkarte schliessbar")
	runner.check_eq(Game.paused_by_ui, 0, "Pause-Zaehler zurueckgesetzt")
	ui.queue_free()
	Game.world = null
	await runner.get_tree().process_frame
