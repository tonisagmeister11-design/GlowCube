extends RefCounted
## Tests fuer Warenkatalog, Geschaefte, Kaeufe, Bank, Staat und Geldmenge.

var runner
const F := preload("res://tests/sim_fixture.gd")


func test_goods_catalog() -> void:
	runner.check(GoodsCatalog.GOODS.size() >= 80, "mindestens 80 Waren (%d)" % GoodsCatalog.GOODS.size())
	for gid in GoodsCatalog.GOODS:
		var g: Dictionary = GoodsCatalog.GOODS[gid]
		for k in ["name", "category", "base_price", "unit", "needs", "consumable", "durable", "icon"]:
			runner.check(g.has(k), "%s hat Feld %s" % [gid, k])
		runner.check(float(g.base_price) > 0.0, "%s Preis > 0" % gid)
	for t in GoodsCatalog.SHOP_ASSORTMENT:
		for gid in GoodsCatalog.SHOP_ASSORTMENT[t]:
			runner.check(GoodsCatalog.GOODS.has(gid), "Sortiment %s: %s existiert" % [t, gid])
	var cats := {}
	for gid in GoodsCatalog.GOODS:
		cats[GoodsCatalog.GOODS[gid].category] = true
	for c in ["bakery", "produce", "meat", "groceries", "drinks", "clothing", "electronics", "furniture", "hardware",
			"medicine", "luxury", "secondhand", "restaurant", "fastfood", "cafe", "bar", "service", "fuel", "vehicle"]:
		runner.check(cats.has(c), "Kategorie %s vorhanden" % c)


func test_businesses_and_prices() -> void:
	var w := F.medium()
	var eco = w.economy
	runner.check(eco.businesses.size() >= 100, "genug Geschaefte (%d)" % eco.businesses.size())
	var lux := 0.0
	var cheap := 1e9
	for id in eco.businesses:
		var b: Dictionary = eco.businesses[id]
		if not b.type in BusinessAI.NO_GOODS:
			runner.check(not b.inventory.is_empty(), "%s hat Sortiment" % b.name)
		for k in ["id", "building", "type", "name", "account", "inventory", "open", "close", "tier", "employees", "owner", "status"]:
			runner.check(b.has(k), "Geschaeft hat %s" % k)
		if b.type == "luxury":
			lux = maxf(lux, eco.price_of(id, "designer_bag"))
		if b.type == "secondhand":
			cheap = minf(cheap, eco.price_of(id, "used_clothes"))
	if lux > 0.0:
		runner.check(lux > 1000.0, "Luxusware teuer")
	runner.check(cheap < 15.0, "Secondhand billig")
	# Restaurantpreise plausibel (12-40 EUR Hauptgericht)
	for id in eco.list_businesses("restaurant"):
		var p: float = eco.price_of(id, "schnitzel")
		if p > 0.0:
			runner.check(p > 12.0 and p < 40.0, "Schnitzelpreis plausibel (%.2f)" % p)


func _open_supermarket(w: WorldState) -> int:
	for id in w.economy.list_businesses("supermarket"):
		if w.economy.is_open(w, id) and int(w.economy.businesses[id].inventory.bread.stock) > 2:
			return id
	return -1


func test_player_buy() -> void:
	var w := F.medium()
	F.advance_to_hour(w, 10.0)
	var eco = w.economy
	var sid := _open_supermarket(w)
	runner.check(sid >= 0, "geoeffneter Supermarkt")
	if sid < 0:
		return
	var pl = w.player
	var L: Ledger = w.ledger
	w.ledger.mint(pl.account, 100.0, "Test")
	var m0 := L.balance(pl.account)
	var s0: int = int(eco.businesses[sid].inventory.bread.stock)
	var gov0 := L.balance(w.government.account)
	var price: float = eco.price_of(sid, "bread")
	var res: Dictionary = pl.buy(w, sid, "bread", 2)
	runner.check(res.ok, "Kauf gelingt: %s" % str(res.get("reason", "")))
	runner.check_near(L.balance(pl.account), m0 - price * 2.0, 0.02, "Geld abgebucht")
	runner.check_eq(int(eco.businesses[sid].inventory.bread.stock), s0 - 2, "Bestand reduziert")
	runner.check(L.balance(w.government.account) > gov0, "Umsatzsteuer an Staat")
	runner.check_eq(int(pl.inventory.get("bread", 0)), 2, "Inventar")
	var e: Dictionary = pl.eat(w, "bread")
	runner.check(e.ok, "Essen aus Inventar")
	# Zu wenig Geld
	var poor := L.open_account("household", 9999, "Testhaushalt", 0.0)
	var r2: Dictionary = eco.buy(w, poor, sid, "bread", 1)
	runner.check(not r2.ok and str(r2.reason).contains("Geld"), "ohne Geld: Ablehnung mit Grund")
	# Ausverkauft
	var r3: Dictionary = eco.buy(w, pl.account, sid, "bread", 100000)
	runner.check(not r3.ok, "zu grosse Menge abgelehnt")
	runner.check(w.ledger.conservation_error() < 0.05, "Geldmenge konsistent")


func test_npc_shop() -> void:
	var w := F.medium()
	var sid := _open_supermarket(w)
	if sid < 0:
		F.advance_to_hour(w, 11.0)
		sid = _open_supermarket(w)
	var hid: int = w.population.households.keys()[5]
	var acc: int = w.population.households[hid].account
	w.ledger.mint(acc, 200.0, "Test")
	var b0 := w.ledger.balance(acc)
	var rev0: float = w.economy.businesses[sid].revenue_today
	var spent: float = w.economy.npc_shop(w, acc, sid, 30.0)
	runner.check(spent > 0.0 and spent <= 30.01, "NPC kauft ein (%.2f)" % spent)
	runner.check_near(w.ledger.balance(acc), b0 - spent, 0.02, "Haushaltskonto belastet")
	runner.check(float(w.economy.businesses[sid].revenue_today) > rev0, "Umsatz steigt")


func test_closed_shop() -> void:
	var w := F.medium()
	F.advance_to_hour(w, 3.0)
	var bid := -1
	for id in w.economy.list_businesses("bakery"):
		bid = id
		break
	if bid >= 0:
		var r: Dictionary = w.economy.buy(w, w.player.account, bid, "bread", 1)
		runner.check(not r.ok and str(r.reason).contains("geschlossen"), "nachts geschlossen: %s" % str(r.reason))


func test_government_policy() -> void:
	var w := F.medium()
	var gov = w.government
	runner.check(not gov.set_rate(w, "income_tax", 2.0).ok, "Steuersatz ausserhalb der Grenzen abgelehnt")
	runner.check(gov.set_rate(w, "sales_tax", 0.15).ok, "Steuersatz setzen")
	gov.set_rate(w, "sales_tax", 0.12)
	runner.check(gov.set_budget(w, "police", 0.3).ok, "Haushalt setzen")
	var s := 0.0
	for k in gov.budget:
		s += float(gov.budget[k])
	runner.check_near(s, 1.0, 0.001, "Haushaltsanteile summieren sich zu 1")
	runner.check(gov.modifier("police_effectiveness") > gov.modifier("education_quality"), "mehr Geld fuer Polizei wirkt")
	gov.budget = Government.DEFAULT_BUDGET.duplicate()
	runner.check(gov.modifier("bus_fare") > 0.0, "Busfahrpreis")
	runner.check(not gov.set_policy(w, "unbekannt", 1.0).ok, "unbekannte Regelung")
	var sp: Dictionary = gov.split_wage(100.0, 13.0)
	runner.check(float(sp.net) > 60.0 and float(sp.net) < 85.0, "Nettolohn plausibel (%.1f)" % float(sp.net))


func test_bank_loans() -> void:
	var w := F.medium()
	var bank: Bank = w.economy.bank
	runner.check_near(Bank.annuity(10000.0, 0.0, 10), 1000.0, 0.01, "Annuitaet ohne Zins")
	var lim: Dictionary = bank.credit_limit(w, "player", 0)
	if float(lim.weekly_income) <= 0.0:
		var r0: Dictionary = bank.request_loan(w, "player", 0, w.player.account, 500.0)
		runner.check(not r0.ok, "ohne Einkommen kein Kredit")
	# Spieler mit Stelle
	var ops: Array = w.jobs.list_openings(w, "player", 0, {"eligible_only": true})
	if ops.is_empty():
		return
	w.player.apply_job(w, int(ops[0].position))
	lim = bank.credit_limit(w, "player", 0)
	runner.check(float(lim.max_loan) > 0.0, "Kreditrahmen mit Einkommen")
	var m0 := w.ledger.balance(w.player.account)
	var amt: float = minf(1000.0, float(lim.max_loan))
	var r: Dictionary = bank.request_loan(w, "player", 0, w.player.account, amt)
	runner.check(r.ok, "Kredit bewilligt: %s" % str(r.get("reason", "")))
	if r.ok:
		runner.check_near(w.ledger.balance(w.player.account), m0 + amt, 0.01, "Auszahlung")
		var rp: Dictionary = bank.repay(w, int(r.loan_id), amt)
		runner.check(rp.ok and float(rp.remaining) == 0.0, "Tilgung")
	w.player.quit_job(w)


func test_money_stability_short() -> void:
	var w := F.medium()
	var m0 := w.ledger.total_money()
	var t0 := Time.get_ticks_msec()
	w.advance(1440.0 * 6.0)
	var ms := Time.get_ticks_msec() - t0
	var m1 := w.ledger.total_money()
	print("    6 Tage: %d ms, Geldmenge %.0f -> %.0f (%.1f %%)" % [ms, m0, m1, (m1 / m0 - 1.0) * 100.0])
	runner.check(w.ledger.conservation_error() < 0.5, "Buchhaltung konsistent (%.3f)" % w.ledger.conservation_error())
	runner.check(absf(m1 / m0 - 1.0) < 0.15, "Geldmenge stabil")
	var q: Dictionary = w.economy.quick_report(w)
	var ratio := float(q.businesses_bankrupt) / float(q.businesses_active + q.businesses_bankrupt)
	runner.check(ratio <= 0.15, "hoechstens 15 %% insolvent (%.2f)" % ratio)
	var flows: Dictionary = w.economy.history[-1].flows
	runner.check(float(flows.get("sales_npc", 0.0)) > 1000.0, "NPC-Umsatz vorhanden")
