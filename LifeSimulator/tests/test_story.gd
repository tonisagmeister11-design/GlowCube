extends RefCounted
## Tests fuer den Story-Modus: Freischaltung, Ablauf, Belohnungen,
## Ortszuordnung und der inszenierte Hinterhalt.

var runner


func _world() -> WorldState:
	return WorldState.create_new({"seed": 2026, "mode": "life",
		"rules": {"city_size": "small", "population": 500},
		"character": {"name": "Testfigur", "body": "male", "age": 26.0, "appearance": {}},
		"start": {"situation": "own_apartment"}})


func test_catalog_is_consistent() -> void:
	var ids := StoryCatalog.ordered_missions()
	runner.check(ids.size() >= 7, "Mindestens sieben Missionen: %d" % ids.size())
	for id in ids:
		var m: Dictionary = StoryCatalog.mission(str(id))
		runner.check(not m.is_empty(), "Mission '%s' existiert" % id)
		runner.check(str(m.get("title", "")) != "", "Mission '%s' hat Titel" % id)
		runner.check((m.get("steps", []) as Array).size() > 0, "Mission '%s' hat Schritte" % id)
		for st in m.steps:
			runner.check(str(st.get("type", "")) in ["goto", "interact", "drive", "deliver", "collect",
				"survive", "flee", "talk", "choice"], "Schritt-Typ gueltig in '%s': %s" % [id, str(st.get("type", ""))])
			for d in st.get("dialog", []):
				runner.check(not StoryCatalog.speaker(str(d[0])).is_empty(), "Sprecher bekannt: %s" % str(d[0]))
		runner.check(StoryCatalog.chapter_of(str(id)) != "", "Mission '%s' gehoert zu einem Kapitel" % id)


func test_locked_without_gang() -> void:
	var w := _world()
	var st = w.story
	runner.check(st != null, "Story-System geladen")
	if st == null:
		return
	runner.check(not st.in_gang(), "Ohne Bande keine Bandenzugehoerigkeit")
	runner.check_eq(st.next_mission(), "", "Ohne Bande keine Mission")
	var r: Dictionary = st.accept()
	runner.check(not r.get("ok", false), "Annahme ohne Mission scheitert")


func test_full_mission_flow() -> void:
	var w := _world()
	var st = w.story
	# Bande beitreten, damit Kapitel 1 freigeschaltet wird
	var g = w.crime.gangs
	var gid := 0
	if "gangs" in g and not g.gangs.is_empty():
		gid = int(g.gangs.keys()[0])
	if g.has_method("contact"):
		g.contact(w, gid)
	if int(st._gang_rank()) < 0 and "player_gang" in g:
		g.player_gang = gid
	st._check_unlocks()
	runner.check(st.unlocked.has("ch1"), "Kapitel 1 freigeschaltet: %s" % str(st.unlocked))
	var first: String = st.next_mission()
	runner.check_eq(first, "m_paket", "Erste Mission ist die Lieferung")
	var acc: Dictionary = st.accept()
	runner.check(acc.get("ok", false), "Mission angenommen: %s" % str(acc.get("reason", "")))
	runner.check_eq(st.step, 0, "Beginnt bei Schritt 0")
	# Alle Schritte durchlaufen
	var money0: float = w.ledger.balance(w.player.account)
	var guard := 0
	while st.is_active() and guard < 20:
		guard += 1
		st.advance_step()
	runner.check(st.completed.has("m_paket"), "Mission abgeschlossen")
	runner.check(w.ledger.balance(w.player.account) > money0, "Belohnung gezahlt")
	runner.check_near(w.ledger.conservation_error(), 0.0, 0.05, "Geldmenge bleibt stimmig")
	runner.check_eq(st.next_mission(), "m_schutzgeld", "Naechste Mission folgt")


func test_fail_and_retry() -> void:
	var w := _world()
	var st = w.story
	st.unlocked.append("ch1")
	st.accept("m_paket")
	runner.check(st.is_active(), "Mission laeuft")
	var f: Dictionary = st.fail()
	runner.check(f.get("failed", false), "Mission gescheitert")
	runner.check(not st.is_active(), "Keine Mission mehr aktiv")
	runner.check_eq(int(st.failed.get("m_paket", 0)), 1, "Fehlversuch gezaehlt")
	runner.check(st.accept("m_paket").get("ok", false), "Erneuter Versuch moeglich")


func test_save_load() -> void:
	var w := _world()
	var st = w.story
	st.unlocked.append("ch1")
	st.accept("m_paket")
	st.advance_step()
	st.choices["m_container"] = "deal"
	var d: Dictionary = w.to_save()
	var w2 := WorldState.from_save(d)
	runner.check_eq(w2.story.active, "m_paket", "Aktive Mission nach Laden")
	runner.check_eq(int(w2.story.step), int(st.step), "Schritt nach Laden")
	runner.check(w2.story.unlocked.has("ch1"), "Kapitel nach Laden")
	runner.check_eq(str(w2.story.choices.get("m_container", "")), "deal", "Entscheidung nach Laden")


func test_place_mapping_in_city() -> void:
	# Jeder verwendete Ortsschluessel muss in der erzeugten Stadt ein Gebaeude finden
	var w := _world()
	var rn := StoryRunner.new()
	rn.world = w
	rn.story = w.story
	var places := {}
	for id in StoryCatalog.ordered_missions():
		for stp in StoryCatalog.mission(str(id)).steps:
			if str(stp.get("place", "")) != "":
				places[str(stp.place)] = true
	runner.check(places.size() >= 5, "Mehrere Orte verwendet: %d" % places.size())
	for p in places:
		var bid: int = rn.building_for_place(str(p))
		runner.check(bid >= 0, "Ort '%s' findet ein Gebaeude" % p)
		if bid >= 0:
			var b: Dictionary = w.city.buildings[bid]
			runner.check(b.sidewalk_point != Vector2.INF, "Ort '%s' ist erreichbar (%s)" % [p, str(b.label)])
	rn.free()


func test_ambush_definition() -> void:
	var m: Dictionary = StoryCatalog.mission("m_hinterhalt")
	runner.check(m.has("scripted_ambush"), "Hinterhalt ist inszeniert")
	var a: Dictionary = m.scripted_ambush
	runner.check(float(a.get("aim_time", 0.0)) >= 3.0, "Absprungfenster mindestens drei Sekunden: %.1f" % float(a.get("aim_time", 0.0)))
	runner.check((a.get("beats", []) as Array).size() >= 4, "Mehrere Dialogzeilen im Hinterhalt")
	runner.check(str(a.get("prompt", "")) != "", "Aufforderung vorhanden")
	# Der Fahrschritt loest die Inszenierung aus
	var drive_step := {}
	for stp in m.steps:
		if str(stp.get("scripted", "")) == "ambush":
			drive_step = stp
	runner.check(not drive_step.is_empty(), "Ein Schritt startet den Hinterhalt")
	runner.check_eq(str(drive_step.get("type", "")), "drive", "Der Hinterhalt passiert beim Fahren")


func test_rpg_models_build() -> void:
	var l := RpgLauncher.build_launcher()
	runner.check(l != null and l.get_child_count() >= 8, "Werfer hat mehrere Teile: %d" % l.get_child_count())
	var verts := 0
	for ch in l.get_children():
		if ch is MeshInstance3D and ch.mesh != null:
			verts += ch.mesh.get_faces().size()
	runner.check(verts > 0, "Werfer hat Geometrie: %d" % verts)
	l.free()
	var r := RpgLauncher.build_rocket()
	runner.check(r != null and r.get_child_count() >= 4, "Rakete hat Teile")
	r.free()
