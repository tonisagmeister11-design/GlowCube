extends RefCounted
## Tests fuer Bevoelkerung, Tagesablaeufe, Positionen und Leistung.

var runner
const F := preload("res://tests/sim_fixture.gd")


func test_population_setup() -> void:
	var w := F.medium()
	var pop = w.population
	print("    Weltaufbau (mittel, 2400 Einwohner): %d ms" % F.created_ms())
	runner.check(F.created_ms() < 8000, "Aufbau in vertretbarer Zeit")
	var n: int = pop.persons.size()
	runner.check(n >= 2280 and n <= 2600, "Einwohnerzahl ~2400 (%d)" % n)
	var st: Dictionary = pop.stats(w)
	var hr := float(st.homeless) / float(n)
	runner.check(hr >= 0.01 and hr <= 0.05, "Wohnungslose 1-5 %% (%.3f)" % hr)
	runner.check(int(st.children) > 300, "Kinder vorhanden")
	var med: Dictionary = st.median_balance_by_zone
	if med.has("poor") and med.has("suburb"):
		runner.check(float(med.poor) < float(med.suburb), "arme Viertel aermer")
	var kids_in_school := 0
	var kids := 0
	for id in pop.persons:
		var p: Dictionary = pop.persons[id]
		var a: float = w.age_years(float(p.birth))
		if a >= 7.0 and a < 15.0 and not p.homeless:
			kids += 1
			if int(p.school) >= 0:
				kids_in_school += 1
		var ap: Dictionary = p.appearance
		for k in ["skin", "hair_style", "hair_color", "eye_color", "face", "build", "top", "bottom", "shoes", "accessories", "beard"]:
			if not ap.has(k):
				runner.check(false, "appearance.%s fehlt" % k)
				return
		runner.check(int(ap.hair_style) >= 0 and int(ap.hair_style) <= 9 and int(ap.beard) <= 4, "Wertebereiche Aussehen")
	runner.check(kids_in_school > kids * 0.9, "Kinder gehen zur Schule")


func test_daily_routine() -> void:
	var w := F.medium()
	var pop = w.population
	# naechster Werktag 10:30
	while w.is_weekend() or w.get_hour_f() > 10.0:
		w.advance(120.0)
	F.advance_to_hour(w, 10.5)
	var acts := {}
	for id in pop.persons:
		var a: String = pop.persons[id].activity
		acts[a] = int(acts.get(a, 0)) + 1
	print("    Werktag 10:30: ", acts)
	runner.check(int(acts.get("work", 0)) > 500, "viele bei der Arbeit")
	runner.check(int(acts.get("school", 0)) > 150, "Kinder in der Schule")
	F.advance_to_hour(w, 3.5)
	var sleeping := 0
	for id in pop.persons:
		if pop.persons[id].activity == "sleep":
			sleeping += 1
	runner.check(sleeping > pop.persons.size() * 0.6, "nachts schlafen die meisten (%d)" % sleeping)


func test_positions_and_index() -> void:
	var w := F.medium()
	var pop = w.population
	F.advance_to_hour(w, 7.4)
	var mover := -1
	for t in 24:
		for id in pop.persons:
			if pop.persons[id].activity == "commute" and pop.persons[id].location.mode == "walk":
				mover = id
				break
		if mover >= 0:
			break
		w.advance(5.0)
	runner.check(mover >= 0, "jemand pendelt morgens")
	if mover >= 0:
		var loc: Dictionary = pop.persons[mover].location
		var pos: Vector2 = pop.position_of(w, mover)
		var box := Rect2(loc.from, Vector2.ZERO).expand(loc.to).grow(150.0)
		runner.check(box.has_point(pos), "Position zwischen Start und Ziel")
		var vs: Dictionary = pop.visible_state(w, mover)
		for k in ["pos", "heading", "activity", "moving", "building"]:
			runner.check(vs.has(k), "visible_state.%s" % k)
		runner.check(vs.moving, "bewegt sich")
		var near: Array = pop.persons_near(w, pos, 5.0)
		runner.check(near.has(mover), "persons_near findet Pendler")
		var r: Dictionary = pop.route_of(w, mover)
		runner.check(r.has("points") and r.points.size() >= 2, "Route")
	var any: int = pop.persons.keys()[10]
	var p2: Vector2 = pop.position_of(w, any)
	var found: Array = pop.persons_near(w, p2, 3.0)
	runner.check(found.has(any), "persons_near findet stehende Person")
	var t0 := Time.get_ticks_usec()
	for i in 100:
		pop.persons_near(w, p2, 120.0, true)
	print("    persons_near(120 m): %.2f ms" % ((Time.get_ticks_usec() - t0) / 100000.0))


func test_day_performance() -> void:
	var w := F.medium()
	var t0 := Time.get_ticks_msec()
	w.advance(1440.0)
	var ms := Time.get_ticks_msec() - t0
	print("    world.advance(1440) mit %d Personen: %d ms" % [w.population.persons.size(), ms])
	runner.check(ms < 3000, "ein Spieltag unter 3 s (Ziel 1,5 s)")


func test_lifecycle() -> void:
	var w := F.medium()
	var births := 0
	var deaths := 0
	var n0: int = w.population.persons.size()
	for d in 4:
		w.advance(1440.0)
		var s: Dictionary = w.population.day_stats
		births += int(s.get("births", 0))
		deaths += int(s.get("deaths", 0))
	runner.check(births > 0 and deaths > 0, "Geburten (%d) und Todesfaelle (%d)" % [births, deaths])
	var n1: int = w.population.persons.size()
	runner.check(absf(float(n1 - n0)) / float(n0) < 0.08, "Bevoelkerung stabil")
	for hid in w.population.households:
		var h: Dictionary = w.population.households[hid]
		for m in h.members:
			if not w.population.persons.has(m):
				runner.check(false, "Haushaltsmitglied existiert")
				return
