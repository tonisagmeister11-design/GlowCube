extends RefCounted
## Tests fuer das Spielerleben: Beduerfnisse, Startsituationen, Kindheit,
## Krankheit, Tod und Erbe.

var runner
const F := preload("res://tests/sim_fixture.gd")


func test_needs_and_actions() -> void:
	var w := F.small({"name": "Nina Not", "age": 30.0, "body": "female"}, {"situation": "own_apartment"})
	var pl = w.player
	runner.check(pl.home >= 0, "eigene Wohnung")
	var h0: float = pl.needs.hunger
	w.advance(180.0)
	runner.check(float(pl.needs.hunger) < h0, "Hunger sinkt")
	pl.needs.energy = 20.0
	var r: Dictionary = pl.sleep(w, 8.0)
	runner.check(r.ok and float(r.minutes) == 480.0, "Schlafen startet")
	w.advance(480.0)
	runner.check(float(pl.needs.energy) > 80.0, "Energie erholt (%.0f)" % float(pl.needs.energy))
	pl.needs.hygiene = 10.0
	runner.check(pl.shower(w).ok and float(pl.needs.hygiene) == 100.0, "Duschen")
	pl.needs.thirst = 10.0
	runner.check(pl.drink(w).ok and float(pl.needs.thirst) > 40.0, "Leitungswasser")
	runner.check(not pl.eat(w, "steak").ok, "Essen nur aus dem Inventar")
	runner.check(pl.movement_speed_factor() > 0.7, "Tempo Erwachsener")
	runner.check_eq(pl.life_stage(), "adult", "Lebensphase")
	var meta: Dictionary = pl.save_meta(w)
	for k in ["character_name", "age", "money", "job_title"]:
		runner.check(meta.has(k), "save_meta %s" % k)


func test_start_homeless() -> void:
	var w := F.small({"name": "Paul Platte", "age": 40.0, "body": "male"}, {"situation": "homeless"})
	var pl = w.player
	runner.check(pl.home < 0 and w.ledger.balance(pl.account) <= 20.0, "obdachlos, fast kein Geld")
	runner.check(not pl.shower(w).ok, "keine Dusche ohne Wohnung")
	var r: Dictionary = pl.sleep(w, 6.0)
	runner.check(r.ok and str(r.reason) != "", "draussen schlafen mit Hinweis")


func test_child_with_parents() -> void:
	var w := F.small({"name": "Emil", "age": 8.0, "body": "male"}, {"situation": "auto"}, "free")
	var pl = w.player
	var pop = w.population
	runner.check(pl.household >= 0, "Kind lebt in einem Haushalt")
	var h: Dictionary = pop.households[pl.household]
	runner.check(h.player_member and not pop.adults_of(w, pl.household).is_empty(), "echte Eltern im Haushalt")
	runner.check(pl.home == int(h.home) and pl.home >= 0, "Zuhause der Eltern")
	runner.check(not w.education.enrollment_of("player", 0).is_empty(), "schulpflichtig eingeschrieben")
	runner.check(not pl.rent_home(w, w.housing.vacant_rentals()[0]).ok, "Kind kann nicht mieten")
	var m0: float = w.ledger.balance(pl.account)
	w.advance(1440.0 * 7.0)
	runner.check(w.ledger.balance(pl.account) > m0, "Taschengeld")
	runner.check(pl.eat_at_home(w).ok or float(h.pantry) < 1.0, "Essen zu Hause")
	runner.check_eq(pl.life_stage(), "child", "Lebensphase Kind")
	runner.check(not pl.heirs(w).is_empty(), "Familie als Erben")


func test_turning_adult_playable() -> void:
	var w := F.small({"name": "Tara", "age": 17.95, "body": "female"}, {"situation": "with_parents"}, "free")
	var pl = w.player
	w.advance(1440.0)
	runner.check(pl.age(w) >= 18.0 and pl.alive, "volljaehrig und spielbar")
	runner.check(pl.household >= 0, "darf vorerst bei den Eltern bleiben")
	var found := false
	for e in w.events:
		if e[0] == "notify" and str(e[1][0]).contains("volljährig"):
			found = true
	runner.check(found, "Hinweis zur Volljaehrigkeit")


func test_illness_and_doctor() -> void:
	var w := F.small({"name": "Kim Krank", "age": 35.0, "body": "female"}, {"situation": "own_apartment"})
	var pl = w.player
	pl.illness = {"name": "Grippe", "severity": 0.8, "since": 0}
	var h0: float = pl.needs.health
	w.advance(240.0)
	runner.check(float(pl.needs.health) < h0, "Krankheit schadet")
	var r: Dictionary = pl.see_doctor(w)
	runner.check(r.ok and pl.illness.is_empty(), "Arzt heilt")
	var old := F.small({"name": "Oma", "age": 85.0, "body": "female"}, {"situation": "own_apartment"})
	runner.check(old.player.movement_speed_factor() < pl.movement_speed_factor(), "Alter verlangsamt")


func test_death_and_heir() -> void:
	var w := F.small({"name": "Hans Erbe", "age": 10.0, "body": "male"}, {"situation": "with_parents"}, "sandbox", {"death": "continue_as_heir"})
	var pl = w.player
	var heirs: Array = pl.heirs(w)
	runner.check(not heirs.is_empty(), "Erben vorhanden")
	runner.check(not pl.continue_as(w, int(heirs[0].id)).ok, "lebend kein Wechsel")
	var money0: float = w.ledger.balance(pl.account)
	pl.die(w, "Test")
	runner.check(not pl.alive and pl.cause_of_death == "Test", "tot")
	var died := false
	for e in w.events:
		if e[0] == "player_died":
			died = true
	runner.check(died, "Ereignis player_died")
	runner.check(pl.can_continue_as_heir(w), "Erbe moeglich")
	var hid: int = int(heirs[0].id)
	var r: Dictionary = pl.continue_as(w, hid)
	runner.check(r.ok and pl.alive, "weiter als Erbe: %s" % str(r.get("reason", "")))
	runner.check(not w.population.persons.has(hid), "Erbe ist jetzt die Spielfigur")
	runner.check(w.ledger.balance(pl.account) >= money0 - 0.01, "Vermoegen geerbt")
	# Ohne Erben: ehrlich ablehnen
	var w2 := F.small({"name": "Solo", "age": 40.0, "body": "male"}, {"situation": "homeless"}, "sandbox", {"death": "continue_as_heir"})
	w2.player.die(w2, "Test")
	runner.check(not w2.player.can_continue_as_heir(w2), "ohne Erben kein Weiterspielen")
	# Kein Tod
	var w3 := F.small({"name": "Ewig", "age": 40.0, "body": "male"}, {"situation": "homeless"}, "free")
	w3.player.die(w3, "Test")
	runner.check(w3.player.alive, "Regel disabled: kein Tod")
	# Neue Figur
	var r4: Dictionary = w2.player.restart_as_new(w2, {"name": "Neu", "age": 22.0, "body": "female"}, {"situation": "shared_room"})
	runner.check(r4.ok and w2.player.alive and w2.player.name == "Neu", "neue Figur")
