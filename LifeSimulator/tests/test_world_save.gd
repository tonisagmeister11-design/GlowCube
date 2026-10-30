extends RefCounted
## Speichern und Laden mitten im Spiel: Kontostaende, Personen, Stellen,
## Wohnungen, Bestaende und Spielerzustand muessen identisch sein.

var runner
const F := preload("res://tests/sim_fixture.gd")


func _snapshot(w: WorldState) -> Dictionary:
	var acc := {}
	for id in w.ledger.accounts:
		acc[id] = snappedf(float(w.ledger.accounts[id].balance), 0.01)
	var jobs := {}
	for pid in w.jobs.positions:
		var p: Dictionary = w.jobs.positions[pid]
		jobs[pid] = "%s:%d:%d" % [p.holder_kind, int(p.holder), int(p.rank)]
	var homes := {}
	for pid in w.housing.properties:
		var p: Dictionary = w.housing.properties[pid]
		homes[pid] = "%s:%d:%s:%d" % [p.tenant.kind, int(p.tenant.id), p.owner.kind, int(p.owner.id)]
	var stock := {}
	for bid in w.economy.businesses:
		var b: Dictionary = w.economy.businesses[bid]
		for gid in b.inventory:
			stock["%d:%s" % [bid, gid]] = int(b.inventory[gid].stock)
	var persons := {}
	for id in w.population.persons:
		var p: Dictionary = w.population.persons[id]
		persons[id] = "%s:%s:%d:%s" % [p.first, p.activity, int(p.household), str(p.job)]
	var pl = w.player
	return {"acc": acc, "jobs": jobs, "homes": homes, "stock": stock, "persons": persons, "time": w.time_minutes,
		"player": [pl.name, pl.birth, pl.home, pl.household, str(pl.inventory), str(pl.needs), pl.education, str(pl.job)],
		"loans": w.economy.bank.loans.size(), "gov": str(w.government.rates)}


func _compare(a: Dictionary, b: Dictionary, label: String) -> void:
	for k in a:
		runner.check(a[k] == b[k], "%s: %s identisch" % [label, k])


func test_roundtrip_memory_and_file() -> void:
	var w := F.medium()
	w.advance(1440.0 * 1.5 + 130.0)
	var pl = w.player
	pl.inventory["bread"] = 3
	var before := _snapshot(w)
	# im Speicher
	var w2 := WorldState.from_save(w.to_save())
	_compare(before, _snapshot(w2), "Speicher")
	runner.check(w2.ledger.conservation_error() < 0.05, "Geldmenge nach Laden konsistent")
	# ueber die Datei (store_var)
	var err := SaveSystem.save("test_sim_roundtrip", w, w.player.save_meta(w))
	runner.check_eq(err, OK, "Speichern")
	var res := SaveSystem.load_data("test_sim_roundtrip")
	runner.check(res.ok, "Laden")
	if res.ok:
		var w3 := WorldState.from_save(res.data)
		_compare(before, _snapshot(w3), "Datei")
		var p0: Dictionary = w.population.persons.values()[3]
		var p3: Dictionary = w3.population.persons[int(p0.id)]
		runner.check(p3.appearance.skin is Color and p3.location.to is Vector2, "Typen erhalten")
		# weiter simulieren funktioniert
		w3.advance(600.0)
		runner.check(w3.ledger.conservation_error() < 0.5, "Simulation nach Laden konsistent")
		runner.check(w3.population.stats(w3, true).employed > 900, "Beschaeftigte nach Laden")
	SaveSystem.delete("test_sim_roundtrip")
