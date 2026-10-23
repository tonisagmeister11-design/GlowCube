extends RefCounted
## Tests fuer Stadtgenerierung und Validierung.

var runner


func test_multiple_seeds_valid() -> void:
	for s in [1, 2, 3, 7, 42, 1234, 99991, 31337]:
		var m := CityGenerator.generate(s, {"size": "small" if s % 2 == 0 else "medium"})
		var errs := []
		for i in m.issues:
			if i.severity == "error":
				errs.append("%s @%s: %s" % [i.code, str(i.pos), i.msg])
		runner.check(errs.is_empty(), "Seed %d: %d Fehler nach Reparatur: %s" % [s, errs.size(), str(errs.slice(0, 5))])
		runner.check(m.buildings.size() > 150, "Seed %d: zu wenige Gebaeude (%d)" % [s, m.buildings.size()])
		print("    seed %d: %d Gebaeude, %d Requisiten, %d Knoten, %d Haltestellen, %d Linien, %d Stadtteile, %d ms, Runden %s" % [
			s, m.buildings.size(), m.props.size(), m.nodes.size(), m.bus_stops.size(), m.bus_lines.size(),
			m.districts.size(), m.stats.generation_ms, str(m.stats.validation.rounds)])


func test_reproducible() -> void:
	var a := CityGenerator.generate(777, {"size": "medium"})
	var b := CityGenerator.generate(777, {"size": "medium"})
	runner.check_eq(a.layout_hash(), b.layout_hash(), "Gleicher Seed muss gleiches Layout erzeugen")
	var c := CityGenerator.generate(778, {"size": "medium"})
	runner.check(a.layout_hash() != c.layout_hash(), "Anderer Seed sollte anderes Layout erzeugen")
