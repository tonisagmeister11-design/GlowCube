extends RefCounted
## Prueft den Kreativmodus. Wichtigste Absicherung: In jedem anderen Modus
## tun die Werkzeuge nichts - kein Cheat darf in ein normales Spiel rutschen.

var runner


func _world(mode: String) -> WorldState:
	return WorldState.create_new({"seed": 91, "mode": mode, "rules": {"city_size": "small", "population": 300},
		"character": {"name": "Test", "body": "male", "age": 30.0, "appearance": {}},
		"start": {"situation": "own_apartment"}})


func test_mode_exists_with_rules() -> void:
	runner.check(GameRules.MODES.has("creative"), "Modus 'creative' vorhanden")
	var r := GameRules.make("creative", {})
	runner.check_eq(str(r.mode), "creative", "Modus wird uebernommen")
	runner.check(r.f("start_money") >= 1000000.0, "Viel Startgeld: %.0f" % r.f("start_money"))
	runner.check(not r.b("needs_can_harm"), "Vernachlaessigung schadet nicht")
	runner.check(not r.b("rent_required"), "Keine Miete")
	runner.check_eq(str(r.get_rule("death")), "disabled", "Kein Tod")


func test_money_via_ledger_keeps_invariant() -> void:
	var w := _world("creative")
	var acc := int(w.player.account)
	var before := w.ledger.balance(acc)
	runner.check(CreativeTools.give_money(w, 5000000.0), "Geld wird gegeben")
	runner.check_eq(w.ledger.balance(acc), before + 5000000.0, "Konto steigt")
	runner.check(w.ledger.conservation_error() < 0.01, "Geldmengen-Invariante bleibt: %.4f" % w.ledger.conservation_error())


func test_items_and_vehicles() -> void:
	var w := _world("creative")
	var items: Array = CreativeTools.item_ids()
	runner.check(items.size() > 10, "Waren verfuegbar: %d" % items.size())
	runner.check(CreativeTools.give_item(w, str(items[0]), 3), "Ware wird gegeben")
	runner.check_eq(int(w.player.inventory.get(str(items[0]), 0)), 3, "Menge im Inventar")
	var n := CreativeTools.give_all_vehicles(w)
	runner.check(n >= 5, "Alle Fahrzeuge gegeben: %d" % n)
	var goods := {}
	for v in w.player.vehicles:
		goods[str(v.good)] = true
	for g in VehicleCatalog.GOOD_IMPORTED:
		runner.check(goods.has(g), "KALDRA-Fahrzeug dabei: %s" % g)


func test_heal_and_story() -> void:
	var w := _world("creative")
	w.player.needs["hunger"] = 3.0
	runner.check(CreativeTools.heal_all(w), "Heilen")
	runner.check(float(w.player.needs.hunger) >= 99.0, "Hunger aufgefuellt")
	var n := CreativeTools.unlock_story(w)
	runner.check(n >= 3, "Kapitel freigeschaltet: %d" % n)
	runner.check_eq(w.story.unlocked.size(), StoryCatalog.CHAPTERS.size(), "Alle Kapitel offen")


func test_nothing_happens_outside_creative() -> void:
	# Die wichtigste Absicherung: in einem normalen Spiel wirken die Werkzeuge nicht.
	for mode in ["life", "sandbox", "free"]:
		var w := _world(mode)
		var acc := int(w.player.account)
		var before := w.ledger.balance(acc)
		var minted := w.ledger.total_minted
		runner.check(not CreativeTools.active(w), "%s: nicht aktiv" % mode)
		runner.check(not CreativeTools.give_money(w, 1000000.0), "%s: kein Geld" % mode)
		runner.check_eq(w.ledger.balance(acc), before, "%s: Konto unveraendert" % mode)
		runner.check_eq(w.ledger.total_minted, minted, "%s: nichts gepraegt" % mode)
		runner.check(not CreativeTools.give_item(w, "bread", 1), "%s: keine Ware" % mode)
		runner.check_eq(CreativeTools.give_all_vehicles(w), 0, "%s: keine Fahrzeuge" % mode)
		runner.check(not CreativeTools.heal_all(w), "%s: kein Heilen" % mode)
		runner.check_eq(CreativeTools.unlock_story(w), 0, "%s: Story bleibt zu" % mode)
		runner.check(not CreativeTools.set_hour(w, 3.0), "%s: Uhrzeit bleibt" % mode)
