extends RefCounted
## Tests fuer Politik: Parteien, Kandidatur, Wahlen, Befugnisse, Wirkungen.

const M := preload("res://tests/crime_mocks.gd")

var runner


func test_party_and_candidacy_rules() -> void:
	var young := M.make_world(4242, {"age": 14.0})
	runner.check(not young.politics.join_party(young, 1).ok, "Unter 16 kein Parteibeitritt")
	var w := M.make_world(4242)
	var pol: Politics = w.politics
	runner.check(pol.party_list().size() >= 4, "Mindestens vier Parteien")
	runner.check(not pol.run_for(w, "council").ok, "Ohne Partei keine Kandidatur")
	var j := pol.join_party(w, 2)
	runner.check(j.ok, "Parteibeitritt")
	runner.check_eq(str(w.player.political_office), "member", "Spielerfeld political_office")
	runner.check(not pol.run_for(w, "council").ok, "Zu wenig Einfluss für den Stadtrat")
	var c := pol.campaign(w, 6.0, 400.0)
	runner.check(c.ok and float(c.influence_gain) > 0.0, "Wahlkampf bringt Einfluss")
	while pol.influence < 12.0:
		pol.campaign(w, 4.0, 200.0, false)
	runner.check(pol.run_for(w, "council").ok, "Kandidatur Stadtrat")
	runner.check(not pol.run_for(w, "council").ok, "Keine Doppelkandidatur")
	runner.check(not pol.run_for(w, "mayor").ok, "Bürgermeister braucht Ratserfahrung oder viel Einfluss")
	runner.check(not pol.run_for(w, "president").ok, "Präsident erst nach höheren Ämtern")
	var up := pol.upcoming_elections(w)
	runner.check(up.size() == 4 and bool(up[0].player_candidate), "Wahlkalender mit Kandidatur")
	# Verbrechen sperrt hoehere Aemter
	pol.influence = 60.0
	w.crime.caught_in_act(w, "robbery", w.city.buildings[2].door)
	w.crime.arrest_player(w, "Test")
	var r := pol.run_for(w, "mayor")
	runner.check(not r.ok and str(r.reason).contains("Verbrechen"), "Vorstrafe sperrt Bürgermeisteramt: %s" % str(r.get("reason", "")))
	runner.check_near(w.ledger.conservation_error(), 0.0, 0.01, "Geldmenge erhalten")


func test_election_deterministic() -> void:
	var results := []
	for i in 2:
		var w := M.make_world(4242)
		var pol: Politics = w.politics
		pol.join_party(w, 1)
		pol.influence = 60.0
		pol.held_offices.append("council")
		runner.check(pol.run_for(w, "mayor").ok, "Kandidatur Bürgermeister")
		var res := pol.force_election(w, "mayor")
		results.append(res)
		runner.check(w.events.any(func(e): return e[0] == "election_finished"), "Signal election_finished")
		runner.check(pol.last_results.has("mayor"), "Ergebnis gespeichert")
		runner.check_eq(res.candidates.size(), 4, "Ein Kandidat je Partei")
		var share := 0.0
		for cnd in res.candidates:
			share += float(cnd.share)
		runner.check_near(share, 1.0, 0.001, "Stimmenanteile summieren sich")
		if res.player_elected:
			runner.check_eq(pol.office, "mayor", "Gewählt -> Amt")
	runner.check_eq(results[0].winner.name, results[1].winner.name, "Gleicher Seed, gleicher Sieger")
	runner.check_eq(results[0].turnout, results[1].turnout, "Gleiche Wahlbeteiligung")
	# Listenwahl
	var w2 := M.make_world(4242)
	var r2: Dictionary = w2.politics.force_election(w2, "council")
	var seats := 0
	for pid in r2.seats:
		seats += int(r2.seats[pid])
	runner.check_eq(seats, 9, "Neun Ratssitze verteilt")


func test_office_powers() -> void:
	var w := M.make_world(4242)
	var pol: Politics = w.politics
	var gov = w.government
	pol.join_party(w, 3)
	runner.check(not pol.apply_decision(w, "budget_transit", 0.2).ok, "Mitglied ohne Befugnis")
	pol._take_office(w, "mayor", w.get_day() + 14)
	var bad := pol.apply_decision(w, "income_tax", 0.3)
	runner.check(not bad.ok, "Bürgermeister darf Einkommensteuer nicht ändern")
	runner.check_near(float(gov.rates.income_tax), 0.2, 0.0001, "Einkommensteuer unverändert")
	var total0 := 0.0
	for k in gov.budget:
		total0 += float(gov.budget[k])
	var ok := pol.apply_decision(w, "budget_transit", 0.2)
	runner.check(ok.ok, "Bürgermeister steuert Nahverkehrsbudget")
	runner.check_near(float(gov.budget.transit), 0.2, 0.0001, "Budgetanteil gesetzt")
	var total1 := 0.0
	for k in gov.budget:
		total1 += float(gov.budget[k])
	runner.check_near(total1, total0, 0.0001, "Summe der Anteile bleibt gleich")
	runner.check(pol.apply_decision(w, "bus_fare", 1.5).ok, "Fahrpreis")
	runner.check_near(float(gov.policies.bus_fare), 1.5, 0.0001, "Fahrpreis in policies")
	var ids := []
	for a in pol.available_actions(w):
		ids.append(a.id)
	runner.check("budget_transit" in ids and not ("income_tax" in ids), "Entscheidungsliste passt zum Amt")
	# Minister: nur eigenes Ressort
	pol._take_office(w, "minister", -1)
	pol.portfolio = "interior"
	runner.check(pol.apply_decision(w, "budget_police", 0.2).ok, "Innenminister: Polizeibudget")
	runner.check(not pol.apply_decision(w, "budget_education", 0.3).ok, "Innenminister: kein Bildungsbudget")
	# Praesident: alles
	pol._take_office(w, "president", w.get_day() + 24)
	var t := pol.apply_decision(w, "income_tax", 0.3)
	runner.check(t.ok, "Präsident ändert Einkommensteuer")
	runner.check_near(float(gov.rates.income_tax), 0.3, 0.0001, "Einkommensteuer gesetzt")
	runner.check(w.events.any(func(e): return e[0] == "policy_changed"), "Signal policy_changed")
	runner.check(pol.apply_decision(w, "rent_cap", 1.0).ok, "Mietpreisbremse")
	var g0: float = w.ledger.balance(gov.account)
	runner.check(pol.apply_decision(w, "infrastructure_program", 50000.0).ok, "Infrastrukturprogramm")
	runner.check_near(g0 - w.ledger.balance(gov.account), 50000.0, 0.01, "Programm kostet Staatsgeld")
	# Stadtrat: Antrag mit Abstimmung
	pol._take_office(w, "council", w.get_day() + 10)
	runner.check(not pol.apply_decision(w, "property_tax", 0.02).ok, "Stadtrat entscheidet nicht direkt")
	var mo := pol.apply_decision(w, "motion:property_tax", 0.02)
	runner.check(mo.ok and mo.has("passed"), "Antrag abgestimmt")
	runner.check_near(w.ledger.conservation_error(), 0.0, 0.01, "Geldmenge erhalten")


func test_decisions_have_measurable_effects() -> void:
	var w := M.make_world(4242)
	var pol: Politics = w.politics
	var crime: CrimeSystem = w.crime
	pol.join_party(w, 1)
	pol._take_office(w, "president", w.get_day() + 24)
	var e0 := crime.police_effectiveness(w)
	var c0 := crime.expected_npc_crimes_per_day(w)
	var r := pol.apply_decision(w, "budget_police", 0.3)
	runner.check(r.ok, "Polizeibudget erhöht")
	var der: Dictionary = r.effects.derived
	runner.check(der.has("police_effectiveness") and der.police_effectiveness[1] > der.police_effectiveness[0], "Polizeiwirkung steigt")
	runner.check(der.has("npc_crimes_per_day") and der.npc_crimes_per_day[1] < der.npc_crimes_per_day[0], "Prognose NPC-Taten sinkt")
	runner.check(der.has("arrest_chance_wanted3") and der.arrest_chance_wanted3[1] > der.arrest_chance_wanted3[0], "Festnahmewahrscheinlichkeit steigt")
	runner.check(crime.police_effectiveness(w) > e0 and crime.expected_npc_crimes_per_day(w) < c0, "CrimeSystem liest die Entscheidung")
	var s0 := crime.strictness(w)
	pol.apply_decision(w, "police_strictness", 1.4)
	runner.check(crime.strictness(w) > s0, "Gesetz Polizeibefugnisse wirkt auf die Polizeihärte")
	var wf0 := crime.welfare_factor(w)
	pol.apply_decision(w, "welfare_level", 1.6)
	runner.check(crime.welfare_factor(w) < wf0, "Mehr Grundsicherung senkt Armutskriminalität")
	var sat0: Dictionary = pol.satisfaction(w)
	var ap0 := pol.approval
	pol.apply_decision(w, "income_tax", 0.35)
	var sat1: Dictionary = pol.satisfaction(w)
	runner.check(float(sat1.taxes) < float(sat0.taxes), "Höhere Steuern belasten die Zufriedenheit")
	runner.check(pol.approval < ap0, "Steuererhöhung kostet Zustimmung")
	var prev := pol.effect_preview(w, "income_tax")
	runner.check(prev.contains("€"), "Vorschau mit geschätzten Einnahmen: " + prev)
	# Bildung wirkt langfristig
	pol.apply_decision(w, "budget_education", 0.35)
	var idx0 := float(pol.indices.education)
	w.advance(8 * 24 * 60.0)
	runner.check(float(pol.indices.education) > idx0 + 0.05, "Bildungsindex steigt langfristig (%.3f)" % float(pol.indices.education))
	runner.check(float(w.government.policies.get("education_index", 0.0)) > 1.0, "Index für andere Systeme veröffentlicht")


func test_npc_government_and_schedule() -> void:
	var w := M.make_world(4242)
	var pol: Politics = w.politics
	w.advance(11 * 24 * 60.0)
	runner.check(pol.last_results.has("council"), "Ratswahl nach Terminplan")
	runner.check(int(pol.next_election.council) > 10, "Nächste Ratswahl geplant")
	runner.check(w.news.any(func(n): return n.category == "Politik"), "Politiknachrichten")
	runner.check(pol.offices().size() == 6, "Ämterübersicht")
	runner.check_near(w.ledger.conservation_error(), 0.0, 0.01, "Geldmenge erhalten")


func test_politics_save_load() -> void:
	var w := M.make_world(4242)
	var pol: Politics = w.politics
	pol.join_party(w, 4)
	pol.influence = 40.0
	pol._take_office(w, "president", 30)
	pol.apply_decision(w, "budget_police", 0.25)
	pol.force_election(w, "council")
	var d := M.save_roundtrip(pol.to_save())
	var p2 := Politics.new()
	p2.from_save(w, d)
	p2.post_load(w)
	runner.check_eq(p2.party, 4, "Partei")
	runner.check_eq(p2.office, "president", "Amt")
	runner.check_near(p2.influence, pol.influence, 0.001, "Einfluss")
	runner.check_eq(p2.decisions.size(), pol.decisions.size(), "Entscheidungen")
	runner.check_eq(p2.council_seats, pol.council_seats, "Ratssitze")
	runner.check(p2.last_results.has("council"), "Wahlergebnis")
	runner.check_near(float(p2.parties[4].popularity), float(pol.parties[4].popularity), 0.0001, "Beliebtheit")
	runner.check(p2.apply_decision(w, "sales_tax", 0.12).ok, "Geladenes System entscheidet weiter")
