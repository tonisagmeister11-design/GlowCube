extends RefCounted
## Tests fuer Berufe, Arbeitsmarkt und Bildung.

var runner
const F := preload("res://tests/sim_fixture.gd")


func test_occupations() -> void:
	var legal := Occupations.legal_ids()
	runner.check(legal.size() >= 30, "mindestens 30 Berufe (%d)" % legal.size())
	runner.check(Occupations.illegal_ids().size() >= 1, "illegale Taetigkeiten markiert")
	for id in ["cashier", "salesperson", "warehouse_worker", "delivery_driver", "bus_driver", "taxi_driver", "mechanic",
			"craftsman", "cook", "waiter", "cleaner", "construction_worker", "office_clerk", "teacher", "doctor", "nurse",
			"firefighter", "police_officer", "security_guard", "entrepreneur", "manager", "lawyer", "journalist", "politician"]:
		runner.check(Occupations.OCCUPATIONS.has(id), "Beruf %s" % id)
		var o: Dictionary = Occupations.OCCUPATIONS.get(id, {})
		if not o.is_empty():
			runner.check(o.ranks.size() >= 1 and o.has("shift") and o.has("days") and o.has("workplaces"), "%s vollstaendig" % id)
	runner.check(Occupations.OCCUPATIONS.doctor.wage > Occupations.OCCUPATIONS.nurse.wage, "Arzt verdient mehr als Pflege")
	runner.check(Occupations.OCCUPATIONS.nurse.wage > Occupations.OCCUPATIONS.cashier.wage, "Pflege mehr als Kasse")


func test_positions_filled() -> void:
	var w := F.medium()
	var held := 0
	var pub := 0
	for p in w.jobs.positions.values():
		if p.holder_kind != "":
			held += 1
			if p.employer_kind == "gov":
				pub += 1
	runner.check(held > 1000, "viele besetzte Stellen (%d)" % held)
	runner.check(pub > 50, "oeffentlicher Dienst besetzt (%d)" % pub)
	var st: Dictionary = w.population.stats(w, true)
	print("    Arbeitslosenquote %.1f %%" % (float(st.unemployment_rate) * 100.0))
	runner.check(float(st.unemployment_rate) >= 0.02 and float(st.unemployment_rate) <= 0.15, "Arbeitslosigkeit 2-15 %%")


func test_apply_work_quit() -> void:
	var w := F.small({"name": "Jo Job", "age": 26.0, "body": "male"}, {"situation": "own_apartment"})
	var pl = w.player
	var ops: Array = w.jobs.list_openings(w, "player", 0)
	runner.check(not ops.is_empty(), "offene Stellen")
	var blocked := {}
	var ok := {}
	for o in ops:
		if not o.eligible and blocked.is_empty():
			blocked = o
		if o.eligible and ok.is_empty():
			ok = o
	if not blocked.is_empty():
		var r0: Dictionary = pl.apply_job(w, int(blocked.position))
		runner.check(not r0.ok and str(r0.reason).contains("Anforderungen"), "Anforderungen geprueft")
	runner.check(not ok.is_empty(), "passende Stelle")
	if ok.is_empty():
		return
	var r: Dictionary = pl.apply_job(w, int(ok.position))
	runner.check(r.ok, "Bewerbung erfolgreich")
	var j: Dictionary = w.jobs.job_of("player", 0, w)
	runner.check(not j.is_empty() and j.has("title") and j.has("wage") and j.has("shift"), "job_of")
	var m0: float = w.ledger.balance(pl.account)
	var res: Dictionary = w.jobs.work(w, "player", 0, 4.0)
	runner.check(res.ok and float(res.pay) > 0.0 and float(res.tax) > 0.0, "Lohn mit Steuer")
	runner.check_near(w.ledger.balance(pl.account), m0 + float(res.pay), 0.02, "Nettolohn gutgeschrieben")
	# Schicht als Aktivitaet: zur Schichtzeit beginnen, Zeit vergehen lassen
	var pos: Dictionary = w.jobs.positions[int(ok.position)]
	for i in 8:
		if w.jobs.is_workday(pos, w.get_day()) and w.get_hour_f() < float(pos.shift_start):
			break
		w.advance(240.0)
	F.advance_to_hour(w, float(pos.shift_start) + 0.1)
	var s: Dictionary = pl.work_shift(w)
	if s.ok:
		var m1: float = w.ledger.balance(pl.account)
		w.advance(float(s.minutes) + 5.0)
		runner.check(w.ledger.balance(pl.account) > m1, "Schicht bezahlt")
	if not w.jobs.job_of("player", 0).is_empty():
		runner.check(pl.quit_job(w).ok, "Kuendigung")
	runner.check(w.jobs.job_of("player", 0).is_empty(), "keine Stelle mehr")


func test_education() -> void:
	var w := F.small({"name": "Lu Lern", "age": 19.0, "body": "female"}, {"situation": "shared_room"})
	var pl = w.player
	runner.check_eq(pl.education, "school", "Schulabschluss mit 19")
	runner.check(not pl.enroll(w, "master").ok, "Master ohne Bachelor abgelehnt")
	var r: Dictionary = pl.enroll(w, "vocational", "crafts")
	runner.check(r.ok, "Ausbildung: %s" % str(r.get("reason", "")))
	var e: Dictionary = w.education.enrollment_of("player", 0)
	var need: float = float(e.required)
	var h0: float = float(pl.skills.handiwork)
	w.education.attend(w, "player", 0, need)
	runner.check(float(pl.skills.handiwork) > h0, "Faehigkeit steigt")
	runner.check_eq(pl.education, "school", "Mindestdauer noch nicht erreicht")
	w.advance(1440.0 * float(e.min_days) + 60.0)
	var res: Dictionary = w.education.attend(w, "player", 0, 1.0)
	runner.check(res.completed and pl.education == "vocational", "Abschluss Berufsausbildung")
	w.ledger.mint(pl.account, 500.0, "Test")
	runner.check(pl.enroll(w, "cooking_course").ok, "Kurs gebucht")
	var c0: float = float(pl.skills.cooking)
	var rc: Dictionary = w.education.attend(w, "player", 0, 8.0)
	runner.check(rc.completed and float(pl.skills.cooking) > c0, "Kurs abgeschlossen")
	runner.check_eq(Education.grade_for_age(7.5), 2, "Klassenstufe nach Alter")
