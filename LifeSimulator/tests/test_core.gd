extends RefCounted
## Tests fuer Ledger, Regeln und Zeitrechnung.

var runner


func test_ledger_conservation() -> void:
	var l := Ledger.new()
	var a := l.open_account("player", 0, "Spieler", 100.0)
	var b := l.open_account("business", 1, "Laden")
	runner.check(l.transfer(a, b, 40.0, "Kauf"), "Gedeckte Zahlung muss gelingen")
	runner.check(not l.transfer(a, b, 100.0, "Kauf"), "Ungedeckte Zahlung muss scheitern")
	runner.check_near(l.balance(a), 60.0, 0.001, "Spielerkonto")
	runner.check_near(l.balance(b), 40.0, 0.001, "Ladenkonto")
	l.mint(b, 10.0, "Export")
	l.burn(b, 25.0, "Import")
	runner.check_near(l.conservation_error(), 0.0, 0.01, "Geldmenge")
	runner.check_near(l.transfer_partial(a, b, 1000.0, "Rest"), 60.0, 0.001, "Teilzahlung")
	runner.check_eq(l.player_log.size() > 0, true, "Kontoauszug")


func test_rules_presets() -> void:
	var r := GameRules.make("free")
	runner.check(not r.b("needs_can_harm"), "Freier Modus ohne Schaden")
	var s := GameRules.make("sandbox", {"start_money": 99999.0, "days_per_year": 500})
	runner.check_near(s.f("start_money"), 99999.0, 0.1, "Sandbox Startgeld")
	runner.check_eq(int(s.get_rule("days_per_year")), 60, "Sandbox clamp")
	var l := GameRules.make("life", {"start_money": 1.0})
	runner.check_near(l.f("start_money"), 1500.0, 0.1, "Lebensmodus ignoriert Overrides")
	var back := GameRules.from_save(s.to_save())
	runner.check_near(back.f("start_money"), 99999.0, 0.1, "Regeln speichern/laden")
