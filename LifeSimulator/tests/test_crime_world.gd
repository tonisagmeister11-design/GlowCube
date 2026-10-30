extends Node
## Tests fuer die Weltschicht der Kriminalitaet (scripts/crime/crime_world.gd):
## Auftragsschritte nur vor Ort, Taten aus Gebaeude-/NPC-/Autofenstern,
## Haft ohne Softlock und Einsatzmeldungen an den Verkehr.

const M := preload("res://tests/crime_mocks.gd")

var runner


func _make(seed_value: int, opts := {}) -> Array:
	var w := M.make_world(seed_value, opts)
	var cw := CrimeWorld.new()
	add_child(cw)
	cw.setup(w, null)
	cw.set_process(false)
	cw.fallback_pos = Vector2.ZERO
	return [w, cw]


func _free(cw: CrimeWorld) -> void:
	cw.queue_free()


func _business_ids(w: WorldState) -> Array:
	var ids: Array = w.economy.businesses.keys()
	ids.sort()
	return ids


## Aufnahme in eine Bande ueber die Weltschicht (Schritte vor Ort).
func _join_gang(w: WorldState, cw: CrimeWorld, gid: int) -> bool:
	var g: Gangs = w.crime.gangs
	var con: Dictionary = g.contact(w, gid, int(g.gangs[gid].hideout))
	if not con.get("ok", false):
		return false
	var mid := int(con.mission_id)
	var guard := 0
	while guard < 8:
		guard += 1
		var st: Dictionary = cw.current_step(mid)
		if st.is_empty():
			break
		cw.fallback_pos = cw.step_target(st)
		var r: Dictionary = cw.progress_here(str(st.event), {})
		if not r.get("ok", false):
			return false
	var c: Dictionary = cw.complete_active_mission(mid)
	return bool(c.get("ok", false))


func test_mission_steps_need_the_world() -> void:
	var pair := _make(4242, {"skills": {"stealth": 80.0, "charisma": 80.0, "strength": 80.0}})
	var w: WorldState = pair[0]
	var cw: CrimeWorld = pair[1]
	var g: Gangs = w.crime.gangs
	var gid := 4
	var con: Dictionary = g.contact(w, gid, int(g.gangs[gid].hideout))
	if not runner.check(con.get("ok", false), "Kontakt im Versteck: %s" % str(con.get("reason", ""))):
		_free(cw)
		return
	var mid := int(con.mission_id)
	var m: Dictionary = g.get_mission(mid)
	runner.check(m.steps.size() >= 2, "Aufnahmeauftrag hat mehrere Schritte")
	# Ziel und Entfernung stehen in der Welt zur Verfuegung
	var st: Dictionary = cw.current_step(mid)
	runner.check(not st.is_empty(), "Aktueller Schritt bekannt")
	cw.fallback_pos = cw.step_target(st) + Vector2(400.0, 400.0)
	runner.check(cw.step_distance(mid) > 100.0, "Entfernung zum Ziel wird berechnet")
	runner.check(cw.mission_hud_line().contains("Auftrag:"), "HUD-Zeile zum Auftrag")
	# Zu weit weg: Schritt wird abgelehnt
	var far: Dictionary = cw.progress_here(str(st.event), {})
	runner.check(not far.get("ok", false), "Schritt scheitert ausserhalb des Zielorts")
	runner.check(str(far.get("reason", "")).contains("Zielort"), "Begruendung nennt den Zielort")
	runner.check(not bool(g.get_mission(mid).steps[0].done), "Schritt bleibt offen")
	# Ohne erledigte Schritte kein Abschluss
	runner.check(not cw.can_complete(mid), "Abschluss gesperrt, solange Schritte offen sind")
	var early: Dictionary = cw.complete_active_mission(mid)
	runner.check(not early.get("ok", false), "Vorzeitiger Abschluss abgelehnt")
	runner.check_eq(str(g.get_mission(mid).status), "active", "Auftrag laeuft weiter")
	# Vor Ort klappt es
	var steps := 0
	var guard := 0
	while guard < 8:
		guard += 1
		var cur: Dictionary = cw.current_step(mid)
		if cur.is_empty():
			break
		cw.fallback_pos = cw.step_target(cur)
		var r: Dictionary = cw.progress_here(str(cur.event), {})
		if not runner.check(r.get("ok", false), "Schritt %s vor Ort: %s" % [str(cur.event), str(r.get("reason", ""))]):
			break
		steps += 1
	runner.check(steps >= 2, "Alle Schritte vor Ort erledigt (%d)" % steps)
	runner.check(cw.can_complete(mid), "Abschluss jetzt moeglich")
	var done: Dictionary = cw.complete_active_mission(mid)
	runner.check(done.get("ok", false), "Auftrag abgeschlossen: %s" % str(done.get("reason", "")))
	runner.check_eq(g.player_gang, gid, "Aufnahme in die Bande")
	# Neuer Auftrag bekommt wieder ein Ziel in der Welt
	var list: Array = g.available_missions(w)
	var accepted := false
	for mm in list:
		if str(mm.status) == "available" and bool(mm.rank_ok):
			accepted = bool(g.accept_mission(w, int(mm.id)).ok)
			if accepted:
				break
	if accepted:
		var st2: Dictionary = cw.current_step()
		runner.check(not st2.is_empty(), "Neuer Auftrag hat einen Schritt")
		var tgt: Vector2 = cw.step_target(st2)
		runner.check(tgt.length() >= 0.0 and is_finite(tgt.x) and is_finite(tgt.y), "Zielpunkt gueltig")
	_free(cw)


func test_shop_and_car_crime_from_panels() -> void:
	var pair := _make(4242, {"skills": {"stealth": 95.0, "driving": 95.0}})
	var w: WorldState = pair[0]
	var cw: CrimeWorld = pair[1]
	var c: CrimeSystem = w.crime
	var biz: int = _business_ids(w)[0]
	var bid := int(w.economy.businesses[biz].building)
	cw.fallback_pos = w.city.buildings[bid].door
	var txt: String = cw.risk_text("shoplifting", {"business_id": biz})
	runner.check(txt.contains("Erfolgschance"), "Risikovorschau im Ladenfenster: %s" % txt)
	runner.check(txt.contains("Fahndung"), "Vorschau nennt die Fahndung")
	var acc: int = w.economy.businesses[biz].account
	var tries := 0
	var ok_once := false
	while tries < 25 and not ok_once:
		tries += 1
		c._set_wanted(w, 0)
		c.charges.clear()
		c.jail_until = 0.0
		cw.jailed = false
		var before: float = w.ledger.balance(acc)
		var r: Dictionary = cw.attempt("shoplifting", {"business_id": biz, "building": bid})
		if not runner.check(r.get("ok", false), "Ladendiebstahl ausgefuehrt: %s" % str(r.get("reason", ""))):
			break
		if bool(r.get("success", false)):
			ok_once = true
			runner.check(w.ledger.balance(acc) <= before + 0.001, "Beute kommt vom Geschaeft")
	runner.check(ok_once, "Ladendiebstahl gelingt mindestens einmal")
	runner.check(c.crimes.size() > 0, "Tat wird protokolliert")
	runner.check_near(w.ledger.conservation_error(), 0.0, 0.01, "Geldmenge erhalten")
	# Autodiebstahl ohne Verkehrsmodul: Tat laeuft, das Auto bleibt unberuehrt
	c._set_wanted(w, 0)
	c.charges.clear()
	c.jail_until = 0.0
	cw.jailed = false
	var car: Dictionary = cw.steal_parked_car(cw.fallback_pos)
	runner.check(car.get("ok", false), "Autodiebstahl versucht: %s" % str(car.get("reason", "")))
	runner.check(cw.risk_text("car_theft", {}).contains("Erfolgschance"), "Vorschau fuer den Autodiebstahl")
	_free(cw)


func test_arrest_leads_to_jail_and_release() -> void:
	var pair := _make(4242)
	var w: WorldState = pair[0]
	var cw: CrimeWorld = pair[1]
	var c: CrimeSystem = w.crime
	cw.fallback_pos = Vector2(40.0, 40.0)
	# Schwere Tat erzwingen, damit eine Haftstrafe entsteht
	var crime_entry: Dictionary = c._log_crime(w, {"type": "robbery", "pos": cw.fallback_pos, "district": 0,
		"perp_kind": "player", "perp": -1, "victim_kind": "business", "victim": -1, "loot": 0.0,
		"success": true, "witnessed": true})
	c.charges.append(int(crime_entry.id))
	c._set_wanted(w, 3)
	var ar: Dictionary = c.arrest_player(w, "Test")
	runner.check(ar.get("ok", false), "Festnahme durchgefuehrt")
	runner.check(float(ar.get("jail_hours", 0.0)) > 0.0, "Haftstrafe verhaengt")
	cw.tick(0.1)
	runner.check(cw.jailed, "Weltschicht erkennt die Haft")
	runner.check(cw.jail_hud_line().contains("Haft"), "HUD zeigt die Haft")
	runner.check(cw.jail_anchor != Vector3.INF and is_finite(cw.jail_anchor.x), "Zellenpunkt gesetzt")
	runner.check(cw.objective == null, "Kein Auftragsziel waehrend der Haft")
	# Keine Taten aus der Zelle heraus
	var blocked: Dictionary = cw.attempt("pickpocket", {})
	runner.check(not blocked.get("ok", false), "Keine Straftat aus der Haft")
	# Kaution ist jederzeit bezifferbar, Absitzen beendet die Haft
	runner.check(c.bail_amount(w) > 0.0, "Kaution bezifferbar")
	var served: Dictionary = cw.serve_sentence()
	runner.check(served.get("ok", false), "Strafe abgesessen")
	runner.check(not c.in_jail(w), "Nach der Haft frei")
	runner.check(not cw.jailed, "Weltschicht hebt die Haft auf")
	var rel: Vector3 = cw.release_position()
	runner.check(is_finite(rel.x) and is_finite(rel.z), "Entlassungspunkt gueltig")
	runner.check(Vector2(rel.x, rel.z).distance_to(Vector2(cw.jail_anchor.x, cw.jail_anchor.z)) < 120.0,
		"Entlassung in der Naehe der Wache")
	# Kein Softlock: der Spieler kann wieder handeln
	runner.check_eq(c.wanted_level(), 0, "Fahndung nach der Haft zurueckgesetzt")
	var after: Dictionary = cw.attempt("pickpocket", {"pos": cw.fallback_pos})
	runner.check(after.has("ok"), "Nach der Haft wieder handlungsfaehig")
	# Kaution als zweiter Weg aus der Zelle
	c.charges.append(int(crime_entry.id))
	c.arrest_player(w, "Test 2")
	cw.tick(0.1)
	if c.in_jail(w):
		w.ledger.mint(w.player.account, c.bail_amount(w) + 100.0, "Testgeld")
		var bail: Dictionary = cw.post_bail()
		runner.check(bail.get("ok", false), "Kaution bezahlt: %s" % str(bail.get("reason", "")))
		runner.check(not c.in_jail(w), "Nach Kaution frei")
		runner.check(not cw.jailed, "Weltschicht hebt die Haft nach Kaution auf")
	_free(cw)


func test_dispatch_reaches_the_player() -> void:
	var pair := _make(4242, {"skills": {"strength": 20.0}})
	var w: WorldState = pair[0]
	var cw: CrimeWorld = pair[1]
	var c: CrimeSystem = w.crime
	var biz: int = _business_ids(w)[0]
	var bid := int(w.economy.businesses[biz].building)
	cw.fallback_pos = w.city.buildings[bid].door
	c.pending_dispatches.clear()
	var seen := false
	for i in 30:
		c._set_wanted(w, 0)
		c.charges.clear()
		c.jail_until = 0.0
		cw.jailed = false
		var r: Dictionary = cw.attempt("robbery", {"business_id": biz, "building": bid})
		if bool(r.get("witnessed", false)):
			seen = true
			break
	runner.check(seen, "Tat wurde beobachtet")
	runner.check(c.pending_dispatches.size() > 0, "Einsatzmeldung erzeugt")
	var d: Dictionary = c.pending_dispatches[0]
	runner.check(Vector2(d.pos).distance_to(cw.fallback_pos) < 5.0, "Einsatz fuehrt zum Spieler")
	# Laufende Fahndung erzeugt weitere Einsaetze (Streifenwagen fahren nach)
	c.pending_dispatches.clear()
	c._set_wanted(w, 3)
	cw.jailed = false
	cw._dispatch_t = 0.0
	cw._update_pursuit(0.1)
	runner.check(c.pending_dispatches.size() > 0, "Fahndung meldet den Standort nach")
	runner.check(str(c.pending_dispatches[0].kind) == "pursuit", "Einsatzart Fahndung")
	# Der Verkehr holt die Meldungen ab
	var taken: Array = c.take_dispatches(10)
	runner.check(taken.size() > 0, "Verkehr erhaelt die Meldungen")
	runner.check(c.pending_dispatches.is_empty(), "Meldungen werden entnommen")
	_free(cw)
