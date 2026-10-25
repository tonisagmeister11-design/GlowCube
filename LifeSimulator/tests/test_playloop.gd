extends RefCounted
## Spielschleife von Anfang bis Ende: Start, Einkauf, Essen, Schlafen,
## Arbeit suchen und arbeiten, Miete, Bus, Kriminalitaet, Alterung.
## Deckt auf, was ein Spieler tatsaechlich tut.

var runner


func _world(start := "own_apartment", age := 24.0, mode := "life") -> WorldState:
	return WorldState.create_new({"seed": 4242, "mode": mode,
		"rules": {"city_size": "small", "population": 600},
		"character": {"name": "Testfigur", "body": "female", "age": age, "appearance": {}},
		"start": {"situation": start}})


func test_core_loop() -> void:
	var w := _world()
	var p = w.player
	runner.check(p.alive, "Spieler lebt")
	runner.check(w.ledger.balance(p.account) > 0.0, "Startgeld vorhanden")
	# Einkaufen in einem offenen Laden
	w.time_minutes = 10.0 * 60.0
	var shop := -1
	for b in w.city.buildings:
		if b.business_type in ["supermarket", "grocery"]:
			var bid: int = w.economy.business_for_building(int(b.id))
			if bid >= 0 and w.economy.is_open(w, bid):
				shop = bid
				break
	runner.check(shop >= 0, "Offener Lebensmittelladen gefunden")
	if shop >= 0:
		var items: Array = w.economy.shop_items(shop)
		runner.check(items.size() > 0, "Laden hat Waren")
		var food := ""
		for it in items:
			var g: Dictionary = GoodsCatalog.get_good(str(it.good))
			if float(g.needs.get("hunger", 0.0)) > 0.0 and int(it.stock) > 0:
				food = str(it.good)
				break
		runner.check(food != "", "Essbare Ware im Sortiment")
		if food != "":
			var before: float = w.ledger.balance(p.account)
			var r: Dictionary = p.buy(w, shop, food, 2)
			runner.check(r.get("ok", false), "Kauf gelingt: " + str(r.get("reason", "")))
			runner.check(w.ledger.balance(p.account) < before, "Geld wurde abgezogen")
			runner.check(int(p.inventory.get(food, 0)) >= 2, "Ware im Inventar")
			p.needs.hunger = 20.0
			var e: Dictionary = p.eat(w, food)
			runner.check(e.get("ok", false), "Essen gelingt: " + str(e.get("reason", "")))
			runner.check(float(p.needs.hunger) > 20.0, "Hunger gestillt")
	# Schlafen
	p.needs.energy = 20.0
	var s: Dictionary = p.sleep(w, 8.0)
	runner.check(s.get("ok", false), "Schlafen gelingt: " + str(s.get("reason", "")))
	w.advance(float(s.get("minutes", 480.0)) + 10.0)
	runner.check(float(p.needs.energy) > 50.0, "Energie nach Schlaf: %.0f" % float(p.needs.energy))


func test_job_and_work() -> void:
	var w := _world()
	var p = w.player
	var openings: Array = w.jobs.list_openings(w, "player", 0)
	runner.check(openings.size() > 0, "Offene Stellen vorhanden")
	var hired := false
	for o in openings:
		if not bool(o.get("eligible", false)):
			continue
		var r: Dictionary = p.apply_job(w, int(o.position))
		if r.get("ok", false):
			hired = true
			break
	runner.check(hired, "Bewerbung erfolgreich")
	if not hired:
		return
	var job: Dictionary = w.jobs.job_of("player", 0, w)
	runner.check(not job.is_empty(), "Job vorhanden")
	# Bis zum Schichtbeginn vorspulen und arbeiten
	var worked := false
	var guard := 0
	while not worked and guard < 14:
		guard += 1
		var r2: Dictionary = p.work_shift(w)
		if r2.get("ok", false):
			var before: float = w.ledger.balance(p.account)
			w.advance(float(r2.get("minutes", 480.0)) + 20.0)
			runner.check(w.ledger.balance(p.account) > before, "Lohn wurde gezahlt (%.2f -> %.2f)" % [before, w.ledger.balance(p.account)])
			worked = true
		else:
			w.advance(240.0)
	runner.check(worked, "Schicht konnte gearbeitet werden")


## Spielt 12 Tage lang so, wie ein Mensch spielt: essen, trinken, schlafen,
## arbeiten. Danach muss die Figur leben und Geld verdient haben.
func test_normal_play_survives_and_progresses() -> void:
	var w := _world()
	var p = w.player
	var start_money: float = w.ledger.balance(p.account)
	var hired := false
	for o in w.jobs.list_openings(w, "player", 0):
		if bool(o.get("eligible", false)) and p.apply_job(w, int(o.position)).get("ok", false):
			hired = true
			break
	runner.check(hired, "Arbeit gefunden")
	var shifts := 0
	for i in 288:  # 12 Tage in Ein-Stunden-Schritten
		w.advance(60.0)
		if not p.alive:
			break
		if not p.activity.is_empty():
			continue
		# Versorgen wie ein Spieler
		if float(p.needs.hunger) < 45.0:
			if not p.eat_at_home(w).get("ok", false):
				var shop := _open_food_shop(w)
				if shop >= 0:
					for it in w.economy.shop_items(shop):
						var g: Dictionary = GoodsCatalog.get_good(str(it.good))
						if float(g.needs.get("hunger", 0.0)) > 20.0 and int(it.stock) > 0:
							p.buy(w, shop, str(it.good), 3)
							p.eat(w, str(it.good))
							break
		if float(p.needs.thirst) < 45.0:
			p.drink(w, "")
		if float(p.needs.hygiene) < 40.0:
			p.shower(w)
		if float(p.needs.energy) < 35.0:
			p.sleep(w, 8.0)
			continue
		if p.work_shift(w).get("ok", false):
			shifts += 1
	runner.check(p.alive, "Lebt nach 12 Tagen normalen Spielens")
	runner.check(shifts >= 4, "Mehrere Schichten gearbeitet: %d" % shifts)
	if p.alive:
		runner.check(float(p.needs.health) > 50.0, "Gesundheit in Ordnung: %.0f" % float(p.needs.health))
		runner.check(w.ledger.balance(p.account) > start_money * 0.5, "Kein Ruin: %.2f -> %.2f" % [start_money, w.ledger.balance(p.account)])


func _open_food_shop(w) -> int:
	for b in w.city.buildings:
		if b.business_type in ["supermarket", "grocery", "convenience", "bakery"]:
			var bid: int = w.economy.business_for_building(int(b.id))
			if bid >= 0 and w.economy.is_open(w, bid):
				return bid
	return -1


func test_homeless_start_and_survival() -> void:
	var w := _world("homeless", 40.0)
	var p = w.player
	runner.check(int(p.home) < 0, "Startet ohne Wohnung")
	runner.check(w.ledger.balance(p.account) < 500.0, "Wenig Startgeld: %.2f" % w.ledger.balance(p.account))
	# Notunterkunft erreichbar?
	var shelters: Array = w.city.buildings_of_kind("shelter")
	runner.check(shelters.size() > 0, "Notunterkunft existiert")
	# Drei Tage ohne Eingriff: muss noch leben (Warnzeit fuer den Spieler)
	w.advance(4320.0)
	runner.check(p.alive, "Lebt nach drei Tagen ohne Versorgung (genug Warnzeit)")
	runner.check(w.events.size() > 0, "Es gab Hinweise/Ereignisse")


func test_child_start_and_growing_up() -> void:
	var w := _world("auto", 8.0)
	var p = w.player
	runner.check(p.life_stage() == "child", "Kind-Lebensphase: " + p.life_stage())
	runner.check(int(p.household) >= 0, "Kind gehoert zu einem Haushalt")
	runner.check(int(p.home) >= 0, "Kind hat ein Zuhause")
	runner.check(int(p.get("school")) >= 0 if "school" in p else true, "Schule zugewiesen")
	# Bis ins Erwachsenenalter altern (10 Jahre * 3 Tage * 1440)
	var days: float = w.rules.f("days_per_year") * 1440.0 * 10.5
	w.advance(days)
	runner.check(p.age(w) >= 18.0, "Ist erwachsen: %.1f Jahre" % p.age(w))
	runner.check(p.alive, "Lebt noch (Haushalt versorgt Kinder)")
	runner.check(p.life_stage() in ["teen", "young_adult", "adult"], "Lebensphase: " + p.life_stage())


func test_rent_and_bills() -> void:
	var w := _world()
	var p = w.player
	var start: float = w.ledger.balance(p.account)
	# Zwei Wochen laufen lassen: Miete muss abgebucht werden
	w.advance(14.0 * 1440.0)
	runner.check(w.ledger.balance(p.account) != start, "Kontostand veraendert sich (Miete/Kosten)")
	runner.check(w.ledger.conservation_error() < 0.05, "Geldmenge stimmt: %.4f" % w.ledger.conservation_error())


func test_transit_and_crime_available() -> void:
	var w := _world()
	runner.check(w.city.bus_lines.size() > 0, "Buslinien vorhanden")
	runner.check(w.city.bus_stops.size() > 0, "Haltestellen vorhanden")
	var t := Transit.new()
	if t.has_method("setup"):
		t.setup(w)
	var arr: Array = t.next_arrivals(w, 0, 3)
	runner.check(arr.size() > 0, "Abfahrten berechenbar")
	# Straftat versuchen
	var r: Dictionary = w.crime.attempt(w, "shoplifting", {"pos": w.city.spawn_points[0].pos})
	runner.check(r.has("ok"), "Straftat liefert Ergebnis")


func test_save_load_midgame() -> void:
	var w := _world()
	var p = w.player
	w.advance(3000.0)
	var money: float = w.ledger.balance(p.account)
	var age: float = p.age(w)
	var d: Dictionary = w.to_save()
	var w2 := WorldState.from_save(d)
	runner.check_near(w2.ledger.balance(w2.player.account), money, 0.02, "Geld nach Laden")
	runner.check_near(w2.player.age(w2), age, 0.01, "Alter nach Laden")
	runner.check_eq(w2.player.name, p.name, "Name nach Laden")
	runner.check_eq(w2.population.persons.size(), w.population.persons.size(), "Bevoelkerung nach Laden")


## Der wichtigste Aufstiegspfad: von der Strasse in die eigene Wohnung.
func test_homeless_can_climb_out() -> void:
	var w := _world("homeless", 30.0)
	var p = w.player
	runner.check(int(p.home) < 0, "Startet obdachlos")
	# Arbeit suchen (ohne Abschluss: einfache Stellen)
	var hired := false
	for o in w.jobs.list_openings(w, "player", 0):
		if bool(o.get("eligible", false)) and p.apply_job(w, int(o.position)).get("ok", false):
			hired = true
			break
	runner.check(hired, "Auch ohne Wohnung und Abschluss gibt es Arbeit")
	if not hired:
		return
	var rented := false
	for i in 480:  # bis zu 20 Tage
		w.advance(60.0)
		if not p.alive:
			break
		if not p.activity.is_empty():
			continue
		if float(p.needs.hunger) < 40.0:
			var shop := _open_food_shop(w)
			if shop >= 0:
				for it in w.economy.shop_items(shop):
					var g: Dictionary = GoodsCatalog.get_good(str(it.good))
					if float(g.needs.get("hunger", 0.0)) > 20.0 and int(it.stock) > 0:
						if p.buy(w, shop, str(it.good), 2).get("ok", false):
							p.eat(w, str(it.good))
						break
		if float(p.needs.thirst) < 40.0:
			var shop2 := _open_food_shop(w)
			if shop2 >= 0:
				for it in w.economy.shop_items(shop2):
					var g2: Dictionary = GoodsCatalog.get_good(str(it.good))
					if float(g2.needs.get("thirst", 0.0)) > 20.0 and int(it.stock) > 0:
						if p.buy(w, shop2, str(it.good), 2).get("ok", false):
							p.drink(w, str(it.good))
						break
		if float(p.needs.energy) < 30.0:
			p.sleep(w, 7.0)
			continue
		# Sobald genug Geld da ist: Zimmer oder Wohnung mieten
		if not rented and w.ledger.balance(p.account) > 300.0:
			for pr in w.housing.list_available({"mode": "rent", "limit": 40}, w):
				if float(pr.get("rent_per_week", 1e9)) <= w.ledger.balance(p.account) * 0.6:
					if p.rent_home(w, int(pr.id)).get("ok", false):
						rented = true
						break
		p.work_shift(w)
	runner.check(p.alive, "Überlebt den Aufstieg")
	runner.check(rented and int(p.home) >= 0, "Konnte eine Unterkunft mieten (Geld: %.2f)" % w.ledger.balance(p.account))
