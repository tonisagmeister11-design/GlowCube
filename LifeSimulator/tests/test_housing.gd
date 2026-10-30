extends RefCounted
## Tests fuer den Immobilienmarkt: Preise, Mieten, Mieten/Kaufen, Raeumung.

var runner
const F := preload("res://tests/sim_fixture.gd")


func test_properties_and_prices() -> void:
	var w := F.medium()
	var H = w.housing
	runner.check(H.properties.size() > 2000, "genug Wohneinheiten (%d)" % H.properties.size())
	var by_kind := {}
	for pid in H.properties:
		var p: Dictionary = H.properties[pid]
		if not by_kind.has(p.kind):
			by_kind[p.kind] = []
		by_kind[p.kind].append(p)
	for k in ["room", "apartment", "house", "villa", "estate", "shelter_bed"]:
		runner.check(by_kind.has(k), "Art %s vorhanden" % k)
	var avg := func(kind: String, key: String) -> float:
		var s := 0.0
		for p in by_kind.get(kind, []):
			s += float(p[key])
		return s / maxf(1.0, by_kind.get(kind, []).size())
	var room_rent: float = avg.call("room", "rent_per_week")
	var villa_rent: float = avg.call("villa", "rent_per_week")
	var estate_price: float = avg.call("estate", "price")
	var house_price: float = avg.call("house", "price")
	print("    Miete Zimmer %.0f, Villa %.0f / Woche; Haus %.0f, Anwesen %.0f" % [room_rent, villa_rent, house_price, estate_price])
	runner.check(room_rent >= 70.0 and room_rent <= 160.0, "Zimmermiete 70-160")
	runner.check(villa_rent >= 1200.0 and villa_rent <= 4000.0, "Villenmiete")
	runner.check(house_price >= 250000.0 and house_price <= 900000.0, "Hauspreis")
	runner.check(estate_price >= 3000000.0, "Anwesen teuer")
	# Lage: reiche Viertel teurer pro m2 als arme
	var rich := []
	var poor := []
	for p in by_kind.apartment:
		var ppm: float = float(p.price) / float(p.size_m2)
		if float(p.wealth) >= 0.7:
			rich.append(ppm)
		elif float(p.wealth) <= 0.25:
			poor.append(ppm)
	if not rich.is_empty() and not poor.is_empty():
		runner.check(rich.reduce(func(a, b): return a + b) / rich.size() > poor.reduce(func(a, b): return a + b) / poor.size() * 1.5,
			"reiche Lage deutlich teurer")
	runner.check(not H.list_available({"mode": "rent", "limit": 5}, w).is_empty(), "Mietangebote vorhanden")
	runner.check(not H.list_available({"mode": "buy", "limit": 5}, w).is_empty(), "Kaufangebote vorhanden")


func test_rent_pay_and_evict() -> void:
	var w := F.small({"name": "Mia Miete", "age": 30.0, "body": "female"}, {"situation": "homeless"})
	var pl = w.player
	var H = w.housing
	runner.check_eq(H.home_of("player", 0), -1, "obdachlos ohne Wohnung")
	var offers: Array = H.list_available({"mode": "rent", "kind": "room", "limit": 3}, w)
	runner.check(not offers.is_empty(), "Zimmer verfuegbar")
	var pid: int = int(offers[0].id)
	var r: Dictionary = pl.rent_home(w, pid)
	runner.check(not r.ok, "ohne Geld/Einkommen abgelehnt")
	w.ledger.mint(pl.account, 2000.0, "Test")
	r = pl.rent_home(w, pid)
	runner.check(r.ok, "Zimmer gemietet: %s" % str(r.get("reason", "")))
	runner.check_eq(H.home_of("player", 0), pid, "home_of")
	var rent: float = float(H.properties[pid].rent_per_week)
	var b0: float = w.ledger.balance(pl.account)
	w.advance(1440.0 * 7.0)
	runner.check(w.ledger.balance(pl.account) <= b0 - rent * 0.5, "Wochenmiete abgebucht")
	# Geld weg -> Rueckstand -> Kuendigung
	w.ledger.transfer(pl.account, w.government.account, w.ledger.balance(pl.account), "Test")
	w.advance(1440.0 * 15.0)
	runner.check_eq(H.home_of("player", 0), -1, "Raeumung nach Mietschulden")


func test_buy_property() -> void:
	var w := F.small({"name": "Kai Kauf", "age": 40.0, "body": "male"}, {"situation": "wealthy"})
	var pl = w.player
	var H = w.housing
	runner.check(pl.home >= 0, "wohlhabender Start mit Haus")
	runner.check(H.properties[pl.home].owner.kind == "player", "Haus gehoert dem Spieler")
	var offers: Array = H.list_available({"mode": "buy", "max_price": 400000.0, "limit": 3}, w)
	if offers.is_empty():
		return
	var pid: int = int(offers[0].id)
	var price: float = float(offers[0].price)
	var m0: float = w.ledger.balance(pl.account)
	var r: Dictionary = pl.buy_home(w, pid)
	runner.check(r.ok, "Kauf: %s" % str(r.get("reason", "")))
	runner.check_near(w.ledger.balance(pl.account), m0 - price * 1.05, 1.0, "Kaufpreis + 5 % Nebenkosten")
	runner.check(H.properties[pid].owner.kind == "player", "Eigentum uebertragen")
	var s: Dictionary = H.sell(w, "player", 0, pid)
	runner.check(s.ok and H.properties[pid].for_sale, "zum Verkauf anbieten")
	runner.check(w.ledger.conservation_error() < 0.05, "Geldmenge konsistent")
