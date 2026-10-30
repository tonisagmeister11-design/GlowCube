extends RefCounted
## Tests fuer CrimeSystem, Polizei-Laufbahn und Banden (mit Ersatzobjekten
## fuer Government, Economy, Population und PlayerLife).

const M := preload("res://tests/crime_mocks.gd")

var runner


func _reset_player_crime(w: WorldState) -> void:
	var c: CrimeSystem = w.crime
	c._set_wanted(w, 0)
	c.charges.clear()
	c.jail_until = 0.0


func _business_ids(w: WorldState) -> Array:
	var ids: Array = w.economy.businesses.keys()
	ids.sort()
	return ids


func test_attempt_outcomes_bounded() -> void:
	var w := M.make_world(4242)
	var c: CrimeSystem = w.crime
	var pacc: int = w.player.account
	var successes := 0
	var witnessed := 0
	var total := 0
	var loot_sum := 0.0
	var start_bal: float = w.ledger.balance(pacc)
	var ids: Array = w.population.persons.keys()
	ids.sort()
	for i in 60:
		_reset_player_crime(w)
		var pid: int = ids[(i * 37) % ids.size()]
		var r := c.attempt(w, "pickpocket", {"pos": w.population.position_of(w, pid)})
		for k in ["ok", "success", "loot", "witnessed", "wanted_delta", "reason", "text"]:
			if not runner.check(r.has(k), "Ergebnis ohne Feld %s" % k):
				return
		if not r.ok:
			continue
		total += 1
		if r.success:
			successes += 1
		if r.witnessed:
			witnessed += 1
			runner.check(int(r.wanted_delta) >= 1, "Gesehene Tat erhöht die Fahndung")
		if r.arrested:
			c.jail_until = 0.0
		loot_sum += float(r.loot)
	runner.check(total >= 40, "Genug gültige Versuche (%d)" % total)
	var rate := float(successes) / maxf(1.0, float(total))
	print("    Taschendiebstahl: %d/%d erfolgreich, %d gesehen, Beute %.2f" % [successes, total, witnessed, loot_sum])
	runner.check(rate > 0.15 and rate < 0.95, "Erfolgsquote begrenzt: %.2f" % rate)
	runner.check(witnessed > 0 and witnessed < total, "Zeugen manchmal, aber nicht immer (%d)" % witnessed)
	runner.check(loot_sum > 0.0, "Beute vorhanden")
	runner.check_near(w.ledger.conservation_error(), 0.0, 0.01, "Geldmenge erhalten")
	# Strafen gehen an den Staat; ohne Festnahmen entspricht der Kontozuwachs der Beute.
	if c.arrests_of_player == 0:
		runner.check_near(w.ledger.balance(pacc) - start_bal, loot_sum, 0.05, "Beute kommt vollständig beim Spieler an")
	runner.check(c.crimes.size() >= total, "Taten protokolliert")
	runner.check(c.risk_preview(w, "robbery").has("chance"), "Risikovorschau")


func test_loot_comes_from_victim() -> void:
	var w := M.make_world(4242, {"skills": {"strength": 90.0, "stealth": 90.0}})
	var c: CrimeSystem = w.crime
	var biz_ids := _business_ids(w)
	var done := false
	for i in 30:
		_reset_player_crime(w)
		var biz: int = biz_ids[i % biz_ids.size()]
		var bacc: int = w.economy.businesses[biz].account
		var b0: float = w.ledger.balance(bacc)
		var p0: float = w.ledger.balance(w.player.account)
		var r := c.attempt(w, "robbery", {"business_id": biz})
		if r.success and float(r.loot) > 0.0 and not r.arrested:
			runner.check_near(b0 - w.ledger.balance(bacc), float(r.loot), 0.01, "Geschäft verliert genau die Beute")
			runner.check_near(w.ledger.balance(w.player.account) - p0, float(r.loot), 0.01, "Spieler erhält genau die Beute")
			done = true
			break
	runner.check(done, "Mindestens ein Überfall gelingt")
	# Ladendiebstahl bewegt Waren, kein Geld
	_reset_player_crime(w)
	var biz2: int = biz_ids[0]
	var inv: Dictionary = w.economy.businesses[biz2].inventory
	var stock0: int = int(inv.bread.stock) + int(inv.snack.stock)
	var got := false
	for i in 20:
		_reset_player_crime(w)
		var r2 := c.attempt(w, "shoplifting", {"business_id": biz2})
		if r2.success:
			got = true
			break
	runner.check(got, "Ladendiebstahl gelingt irgendwann")
	runner.check(int(inv.bread.stock) + int(inv.snack.stock) < stock0, "Bestand sinkt")
	var qty := 0
	for g in w.player.inventory:
		qty += int(w.player.inventory[g])
	runner.check(qty > 0, "Ware im Inventar")
	runner.check_near(w.ledger.conservation_error(), 0.0, 0.01, "Geldmenge erhalten")


func test_arrest_fine_jail_record() -> void:
	var w := M.make_world(4242)
	var c: CrimeSystem = w.crime
	var gacc: int = w.government.account
	var g0: float = w.ledger.balance(gacc)
	var pos: Vector2 = w.city.buildings[10].door
	c.caught_in_act(w, "robbery", pos)
	runner.check(c.wanted_level() >= 3, "Raub unter Zeugen: Fahndung >= 3")
	runner.check_eq(int(w.player.wanted), c.wanted_level(), "Spielerfeld wanted gespiegelt")
	runner.check(w.events.any(func(e): return e[0] == "wanted_level_changed"), "Signal wanted_level_changed")
	var r := c.arrest_player(w, "Test")
	runner.check(r.ok, "Festnahme")
	runner.check(float(r.paid) > 0.0, "Geldstrafe bezahlt")
	runner.check_near(w.ledger.balance(gacc) - g0, float(r.paid), 0.01, "Strafe geht an die Staatskasse")
	runner.check(c.in_jail(w), "In Haft")
	runner.check_eq(c.wanted_level(), 0, "Fahndung beendet")
	runner.check(c.has_record("player", 0, "felony"), "Vorstrafe: Verbrechen")
	runner.check(not c.has_record("npc", 5, ""), "NPC ohne Vorstrafe")
	runner.check(w.player.criminal_record.size() > 0, "Vorstrafe beim Spieler eingetragen")
	runner.check(w.news.any(func(n): return n.category == "Polizei"), "Polizeimeldung")
	runner.check(not c.attempt(w, "pickpocket", {}).ok, "In Haft keine Taten")
	var s := c.serve_sentence(w, true)
	runner.check(s.ok and float(s.minutes) > 0.0, "Haft absitzen")
	runner.check(not c.in_jail(w), "Nach der Haft frei")
	# Arm: Teilzahlung, Rest wird Haft
	var pacc: int = w.player.account
	w.ledger.transfer(pacc, gacc, w.ledger.balance(pacc) - 10.0, "leeren")
	c.caught_in_act(w, "burglary", pos)
	var r2 := c.arrest_player(w, "Test")
	runner.check(float(r2.paid) <= 10.01, "Nur Teilzahlung möglich")
	runner.check(float(r2.jail_hours) > float(CrimeSystem.TYPES.burglary.jail), "Unbezahlte Strafe verlängert die Haft")
	# Kaution
	w.ledger.mint(pacc, 100000.0, "Test")
	var b := c.post_bail(w)
	runner.check(b.ok, "Kaution bezahlt: %s" % str(b.get("reason", "")))
	runner.check(not c.in_jail(w), "Nach Kaution frei")
	# Selbst stellen mindert die Strafe
	c.caught_in_act(w, "pickpocket", pos)
	var t := c.turn_yourself_in(w)
	runner.check(t.ok, "Selbst stellen")
	runner.check_near(w.ledger.conservation_error(), 0.0, 0.01, "Geldmenge erhalten")


func test_wanted_decays() -> void:
	var w := M.make_world(4242)
	var c: CrimeSystem = w.crime
	c.last_seen = w.time_minutes
	c._set_wanted(w, 1)
	runner.check(c.arrest_chance_per_hour(w) > 0.0, "Festnahmerisiko bei Fahndung")
	var hid := c.hide(w, 4.0)
	runner.check(hid.ok, "Untertauchen")
	runner.check_eq(c.wanted_level(), 0, "Fahndung klingt ab")
	runner.check_eq(c.arrests_of_player, 0, "Untergetaucht nicht gefasst")
	# Hoehere Fahndung -> hoeheres Risiko; Verstecken senkt es
	c._set_wanted(w, 4)
	var p4 := c.arrest_chance_per_hour(w)
	c._set_wanted(w, 1)
	var p1 := c.arrest_chance_per_hour(w)
	runner.check(p4 > p1, "Stufe 4 riskanter als Stufe 1")
	c.hiding_until = w.time_minutes + 600.0
	runner.check(c.arrest_chance_per_hour(w) < p1, "Verstecken senkt das Risiko")
	c.hiding_until = 0.0
	# Ohne Verstecken wird man bei hoher Stufe frueher oder spaeter gefasst
	c.last_seen = w.time_minutes
	c._set_wanted(w, 5)
	w.advance(24 * 60.0)
	runner.check(c.arrests_of_player >= 1 or c.wanted_level() == 0, "Fahndung endet (Festnahme oder Abklingen)")


func test_npc_crime_by_district() -> void:
	var w := M.make_world(4242)
	var c: CrimeSystem = w.crime
	w.advance(5 * 24 * 60.0)
	var hi_sum := 0.0
	var hi_n := 0
	var lo_sum := 0.0
	var lo_n := 0
	for d in w.city.districts:
		var n := float(c.district_stats.get(int(d.id), {}).get("total", 0))
		if float(d.crime) >= 0.3:
			hi_sum += n
			hi_n += 1
		elif float(d.crime) <= 0.15:
			lo_sum += n
			lo_n += 1
	var hi := hi_sum / maxf(1.0, hi_n)
	var lo := lo_sum / maxf(1.0, lo_n)
	print("    NPC-Taten je Stadtteil in 5 Tagen: kriminell %.1f, ruhig %.1f, gesamt %d, aufgeklärt %d, offene Fälle %d" % [hi, lo, c.total_npc_crimes, c.total_npc_solved, c.open_cases(w).size()])
	runner.check(c.total_npc_crimes > 20, "NPC-Kriminalität findet statt")
	runner.check(hi > lo * 1.8, "Mehr Taten in Problemvierteln (%.1f) als in ruhigen (%.1f)" % [hi, lo])
	runner.check(c.total_npc_solved > 0, "Polizei klärt Taten auf")
	runner.check(c.open_cases(w).size() > 0, "Offene Fälle für Polizisten")
	runner.check(c.pending_dispatches.size() > 0, "Einsatzmeldungen für den Verkehr")
	runner.check(c.history.size() >= 4, "Tagesstatistik")
	runner.check_near(w.ledger.conservation_error(), 0.0, 0.01, "Geldmenge erhalten")
	# Regel crime_rate = 0 schaltet NPC-Kriminalitaet ab
	var w0 := M.make_world(4242, {"rules": {"crime_rate": 0.0}})
	w0.advance(24 * 60.0)
	runner.check_eq(w0.crime.total_npc_crimes, 0, "Keine NPC-Taten bei Kriminalitätsrate 0")


func test_police_budget_effect() -> void:
	var a := M.make_world(4242)
	var b := M.make_world(4242)
	b.government.budget.police = 0.30
	var ca: CrimeSystem = a.crime
	var cb: CrimeSystem = b.crime
	runner.check(cb.police_effectiveness(b) > ca.police_effectiveness(a), "Mehr Budget, mehr Polizeiwirkung")
	runner.check(cb.arrest_chance_for_level(b, 3) > ca.arrest_chance_for_level(a, 3), "Höhere Festnahmewahrscheinlichkeit")
	runner.check(cb.expected_npc_crimes_per_day(b) < ca.expected_npc_crimes_per_day(a), "Prognose: weniger Straftaten")
	a.advance(6 * 24 * 60.0)
	b.advance(6 * 24 * 60.0)
	var ra := float(ca.total_npc_solved) / maxf(1.0, ca.total_npc_crimes)
	var rb := float(cb.total_npc_solved) / maxf(1.0, cb.total_npc_crimes)
	print("    6 Tage: Standardbudget %d Taten (Quote %.2f), doppeltes Polizeibudget %d Taten (Quote %.2f)" % [ca.total_npc_crimes, ra, cb.total_npc_crimes, rb])
	runner.check(cb.total_npc_crimes < ca.total_npc_crimes, "Weniger NPC-Taten mit höherem Polizeibudget")
	runner.check(rb > ra, "Höhere Aufklärungsquote mit höherem Polizeibudget")


func test_gang_career_to_boss() -> void:
	var w := M.make_world(777, {"skills": {"stealth": 85.0, "strength": 85.0, "charisma": 85.0, "fitness": 80.0, "driving": 80.0}, "money": 20000.0})
	var c: CrimeSystem = w.crime
	var g: Gangs = c.gangs
	runner.check(g.gangs.size() >= 4, "Mindestens vier Organisationen")
	for gid in g.gangs:
		var gg: Dictionary = g.gangs[gid]
		runner.check(not gg.territory.is_empty(), "%s hat ein Revier" % gg.name)
		runner.check(int(gg.hideout) >= 0, "%s hat ein Versteck" % gg.name)
		runner.check(int(gg.account) >= 0, "%s hat eine Kasse" % gg.name)
	var members := 0
	for gid in g.gangs:
		members += g.gangs[gid].members.size()
	runner.check(members > 0, "Banden haben NPC-Mitglieder")
	var gid: int = 4  # Famiglia Valcorvo
	runner.check_eq(str(g.gangs[gid].boss_title), "Pate", "Mafia-Familie mit Paten")
	# Kontakt nur im Versteck
	var wrong := g.contact(w, gid, int(g.gangs[gid].hideout) + 1)
	runner.check(not wrong.ok, "Kontakt nur im Versteck")
	var con := g.contact(w, gid, int(g.gangs[gid].hideout))
	runner.check(con.ok, "Kontakt aufgenommen: %s" % str(con.get("reason", "")))
	var mid: int = con.mission_id
	var m: Dictionary = g.get_mission(mid)
	for s in m.steps:
		var pr := g.progress_mission(w, mid, str(s.event), {})
		runner.check(pr.ok, "Aufnahmeschritt %s: %s" % [s.event, str(pr.get("reason", ""))])
	var comp := g.complete_mission(w, mid)
	runner.check(comp.ok, "Aufnahmeprüfung bestanden: %s" % str(comp.get("text", "")))
	runner.check_eq(g.player_gang, gid, "Mitglied")
	runner.check_eq(int(w.player.gang), gid, "Spielerfeld gang gespiegelt")
	runner.check(not g.boss_decision(w, "succeed").ok, "Nachfolge als Mitläufer unmöglich")
	var req := g.rank_requirements(w, gid)
	runner.check(req.requirements.size() >= 3, "Nachvollziehbare Bedingungen")
	var ranks_seen := {0: true}
	var completed := 0
	var iters := 0
	while iters < 2500 and not _boss_ready(w, g):
		iters += 1
		if c.in_jail(w):
			c.serve_sentence(w, true)
			continue
		if c.wanted_level() > 0:
			c.hide(w, 4.0)
			continue
		var list := g.available_missions(w)
		var active := {}
		for mm in list:
			if mm.status == "active":
				active = mm
				break
		if active.is_empty():
			var accepted := false
			for mm in list:
				if mm.status == "available" and mm.rank_ok:
					accepted = g.accept_mission(w, int(mm.id)).ok
					if accepted:
						break
			if not accepted:
				w.advance(8 * 60.0)
			continue
		var aid := int(active.id)
		var cur: Dictionary = g.get_mission(aid)
		for s in cur.steps:
			if s.done or cur.status != "active":
				continue
			g.progress_mission(w, aid, str(s.event), {})
			break
		cur = g.get_mission(aid)
		if not cur.is_empty() and cur.status == "active" and g._all_done(cur):
			var cr := g.complete_mission(w, aid)
			if cr.ok:
				completed += 1
				ranks_seen[g.rank] = true
		w.advance(30.0)
	print("    Bandenlaufbahn: %d Aufträge, Rang %s, Ruf %.0f, Loyalität %.0f, Vertrauen %.0f, %d Schleifen, Tag %d" % [
		completed, g.rank_title(gid, g.rank), g.get_rep(gid), g.loyalty, g.boss_trust(w, gid), iters, w.get_day()])
	runner.check_eq(g.rank, Gangs.UNDERBOSS_RANK, "Unterboss erreicht")
	runner.check(ranks_seen.size() >= 6, "Alle Ränge nacheinander durchlaufen (%d)" % ranks_seen.size())
	var rr := g.rank_requirements(w, gid)
	runner.check(rr.boss_options.size() == 3, "Entscheidung an der Spitze angeboten")
	runner.check(_boss_ready(w, g), "Bedingungen für den Paten erfüllt")
	# Nachfolge: Anfuehrer wird verhaftet
	var leader := int(g.gangs[gid].leader)
	if leader >= 0:
		c.convict_npc(w, leader, "extortion")
	else:
		g.gangs[gid].vacancy = true
		g.gangs[gid].vacancy_since = w.get_day()
	runner.check(bool(g.gangs[gid].vacancy), "Führung vakant nach Verhaftung")
	var bd := g.boss_decision(w, "succeed")
	runner.check(bd.ok and bd.success, "Nachfolge angetreten: %s" % str(bd.get("text", bd.get("reason", ""))))
	runner.check(g.is_boss, "Spieler führt die Organisation")
	runner.check_eq(g.rank_title(gid, g.rank), "Pate", "Titel Pate")
	runner.check(g.boss_order(w, "raise_protection").ok, "Befehle als Pate")
	runner.check_near(w.ledger.conservation_error(), 0.0, 0.01, "Geldmenge erhalten")


func _boss_ready(w: WorldState, g: Gangs) -> bool:
	if g.player_gang < 0 or g.rank != Gangs.UNDERBOSS_RANK:
		return false
	var rr := g.rank_requirements(w, g.player_gang)
	for r in rr.requirements:
		if not r.met:
			return false
	return true


func test_gang_daily_and_rivals() -> void:
	var w := M.make_world(4242)
	var g: Gangs = w.crime.gangs
	var funds0 := 0.0
	for gid in g.gangs:
		funds0 += w.ledger.balance(int(g.gangs[gid].account))
	w.advance(6 * 24 * 60.0)
	var funds1 := 0.0
	for gid in g.gangs:
		funds1 += w.ledger.balance(int(g.gangs[gid].account))
	runner.check(funds1 != funds0, "Bandenkassen bewegen sich (Schutzgeld, Löhne)")
	runner.check(w.news.any(func(n): return str(n.text).contains("Revier")), "Revierkämpfe in den Nachrichten")
	runner.check_eq(g.overview(w).size(), g.gangs.size(), "Übersicht für das Telefon")
	runner.check_near(w.ledger.conservation_error(), 0.0, 0.01, "Geldmenge erhalten")


func test_police_career() -> void:
	var w := M.make_world(4242, {"skills": {"logic": 80.0, "fitness": 70.0}})
	var c: CrimeSystem = w.crime
	var pc: PoliceCareer = c.police
	runner.check(pc.join_requirements(w).size() >= 5, "Eintrittsbedingungen")
	var j := pc.join(w)
	runner.check(j.ok, "Eintritt in den Polizeidienst: %s" % str(j.get("reason", "")))
	runner.check_eq(int(w.player.police_rank), 0, "Dienstgrad gespiegelt")
	runner.check(not c.gangs.contact(w, 1, -1).ok, "Polizisten können keiner Bande beitreten")
	var bal0: float = w.ledger.balance(w.player.account)
	var p := pc.patrol(w, 4.0)
	runner.check(p.ok, "Streife")
	runner.check(w.ledger.balance(w.player.account) > bal0, "Lohn für die Streife")
	w.advance(2 * 24 * 60.0)
	var solved := 0
	for round_i in 6:
		var cases := c.open_cases(w)
		if cases.is_empty():
			w.advance(24 * 60.0)
			continue
		var cid := int(cases[0].id)
		runner.check(pc.take_case(w, cid).ok, "Fall übernehmen")
		for k in 8:
			var inv := pc.investigate(w, cid, 2.0)
			if inv.suspect_known:
				break
		var ar := pc.arrest_suspect(w, cid)
		if ar.ok:
			solved += 1
		elif c.cases.get(cid, {}).get("status", "") == "assigned":
			ar = pc.arrest_suspect(w, cid)
			if ar.ok:
				solved += 1
	runner.check(solved >= 1, "Fälle aufgeklärt (%d)" % solved)
	runner.check(pc.arrests >= solved, "Festnahmen gezählt")
	runner.check(pc.rank_requirements(w).requirements.size() >= 4, "Beförderungsbedingungen sichtbar")
	# Straftat im Dienst -> Entlassung
	c.caught_in_act(w, "robbery", w.city.buildings[3].door)
	c.arrest_player(w, "Test")
	runner.check(not pc.member, "Entlassung nach Verbrechen")
	c.serve_sentence(w, true)
	var j2 := pc.join(w)
	runner.check(not j2.ok, "Polizeidienst mit Vorstrafe gesperrt")
	runner.check(str(j2.reason).contains("Verbrechen"), "Grund genannt: %s" % str(j2.reason))


func test_police_blocked_by_felony_and_age() -> void:
	var w := M.make_world(4242, {"age": 16.0})
	runner.check(not w.crime.police.join(w).ok, "Unter 18 kein Polizeidienst")
	var w2 := M.make_world(4242)
	w2.crime.caught_in_act(w2, "car_theft", w2.city.buildings[5].door)
	w2.crime.arrest_player(w2, "Test")
	runner.check(w2.crime.has_record("player", 0, "felony"), "Verbrechen in der Akte")
	runner.check(not w2.crime.police.join(w2).ok, "Mit Verbrechen kein Polizeidienst")


func test_save_load_roundtrip() -> void:
	var w := M.make_world(4242)
	var c: CrimeSystem = w.crime
	w.advance(24 * 60.0)
	c.attempt(w, "pickpocket", {"pos": w.population.position_of(w, 3)})
	c.caught_in_act(w, "vandalism", w.city.buildings[4].door)
	var con := c.gangs.contact(w, 2, -1)
	runner.check(con.ok, "Kontakt")
	var d := M.save_roundtrip(c.to_save())
	var c2 := CrimeSystem.new()
	c2.from_save(w, d)
	c2.post_load(w)
	runner.check_eq(c2.wanted, c.wanted, "Fahndung")
	runner.check_eq(c2.charges.size(), c.charges.size(), "Anklagen")
	runner.check_eq(c2.cases.size(), c.cases.size(), "Fälle")
	runner.check_eq(c2.crimes.size(), mini(c.crimes.size(), 120), "Protokoll")
	runner.check_eq(c2.gangs.gangs.size(), c.gangs.gangs.size(), "Banden")
	runner.check_eq(c2.gangs.missions.size(), c.gangs.missions.size(), "Aufträge")
	runner.check_eq(c2.gangs.gangs[1].territory, c.gangs.gangs[1].territory, "Reviere")
	runner.check_eq(c2.total_npc_crimes, c.total_npc_crimes, "Statistik")
	var any_case: Dictionary = c2.cases.values()[0] if not c2.cases.is_empty() else {}
	runner.check(any_case.is_empty() or typeof(any_case.pos) == TYPE_VECTOR2, "Positionen als Vector2 wiederhergestellt")
	runner.check(c2.gangs.get_mission(int(con.mission_id)).steps[0].pos is Vector2, "Auftragsziel als Vector2")
	# Weiterlaufen mit dem geladenen System
	w.crime = c2
	for s in w.systems:
		if s[0] == "crime":
			s[1] = c2
	w.advance(24 * 60.0)
	runner.check(c2.total_npc_crimes >= c.total_npc_crimes, "Geladenes System läuft weiter")


func test_world_without_partner_systems() -> void:
	# Echte WorldState-Erzeugung: fehlende Systeme werden uebersprungen,
	# Kriminalitaet und Politik muessen trotzdem laufen und speicherbar sein.
	var w := WorldState.create_new({"seed": 99, "mode": "life", "rules": {"city_size": "small", "population": 300}})
	runner.check(w.crime != null, "CrimeSystem geladen")
	runner.check(w.politics != null, "Politics geladen")
	w.advance(26 * 60.0)
	var r: Dictionary = w.crime.attempt(w, "vandalism", {"pos": w.city.buildings[0].door})
	runner.check(r.has("ok"), "Tat ohne Partnersysteme möglich oder sauber abgelehnt")
	var d := M.save_roundtrip(w.to_save())
	var w2 := WorldState.from_save(d)
	runner.check(w2.crime != null and w2.politics != null, "Laden ohne Partnersysteme")
	w2.advance(60.0)
	runner.check_eq(w2.crime.gangs.gangs.size(), w.crime.gangs.gangs.size(), "Banden nach dem Laden")
