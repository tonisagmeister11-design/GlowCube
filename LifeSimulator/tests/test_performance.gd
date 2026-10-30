extends RefCounted
## Prueft, dass die Leistungsstufen wirklich gestaffelt wirken und dass der
## Leistungsmodus ueberall sparsamer ist als die Standardstufe.

var runner


func _perf():
	var loop := Engine.get_main_loop()
	return loop.root.get_node_or_null("Perf") if loop is SceneTree else null


func test_levels_are_ordered() -> void:
	var p = _perf()
	if not runner.check(p != null, "Leistungsmanager vorhanden"):
		return
	var keep := int(p.level)
	# Jeder Wert muss von Stufe 0 bis 4 monoton steigen (bzw. fallen)
	for key in ["view_distance", "detail_range", "max_peds", "max_vehicles", "shadow_distance"]:
		var prev := -1.0
		for lvl in p.LEVELS:
			var val := float(p.TABLE[key][lvl])
			runner.check(val >= prev, "%s steigt mit der Stufe (Stufe %d: %.1f < %.1f)" % [key, lvl, val, prev])
			prev = val
	# Leistungsmodus muss deutlich sparsamer sein als Mittel
	runner.check(float(p.TABLE.view_distance[0]) <= float(p.TABLE.view_distance[2]) * 0.5,
		"Sichtweite im Leistungsmodus hoechstens halb so gross")
	runner.check(int(p.TABLE.max_peds[0]) <= int(p.TABLE.max_peds[2]) / 3,
		"Hoechstens ein Drittel der Passanten")
	runner.check(int(p.TABLE.max_vehicles[0]) <= int(p.TABLE.max_vehicles[2]) / 3,
		"Hoechstens ein Drittel der Fahrzeuge")
	runner.check(float(p.TABLE.render_scale[0]) <= 0.65, "Renderauflösung deutlich reduziert")
	runner.check(int(p.TABLE.shadows[0]) == 0, "Keine Schatten im Leistungsmodus")
	runner.check(not bool(p.TABLE.far_lod[0]) == false, "Fernschicht im Leistungsmodus noch aktiv oder bewusst aus")
	p.set_level(keep, false)


func test_level_switch_applies() -> void:
	var p = _perf()
	if p == null:
		return
	var keep := int(p.level)
	p.set_level(0, false)
	var vd0: float = p.view_distance()
	var peds0: int = p.max_peds()
	var veh0: int = p.max_vehicles()
	runner.check(not p.shadows_enabled(), "Leistungsmodus ohne Schatten")
	p.set_level(2, false)
	runner.check(p.view_distance() > vd0, "Sichtweite steigt: %.0f -> %.0f" % [vd0, p.view_distance()])
	runner.check(p.max_peds() > peds0, "Mehr Passanten: %d -> %d" % [peds0, p.max_peds()])
	runner.check(p.max_vehicles() > veh0, "Mehr Fahrzeuge: %d -> %d" % [veh0, p.max_vehicles()])
	p.set_level(keep, false)


func test_dynamic_scale_reduces_load() -> void:
	var p = _perf()
	if p == null:
		return
	var keep := int(p.level)
	p.set_level(2, false)
	var full_peds: int = p.max_peds()
	var full_vd: float = p.view_distance()
	p.dynamic_scale = 0.5
	runner.check(p.max_peds() < full_peds, "Nachregelung senkt Passanten")
	runner.check(p.view_distance() < full_vd, "Nachregelung senkt Sichtweite")
	runner.check(p.view_distance() >= 120.0, "Sichtweite bleibt spielbar")
	p.dynamic_scale = 1.0
	p.set_level(keep, false)


func test_suggestion_for_weak_hardware() -> void:
	var p = _perf()
	if p == null:
		return
	var keep: String = p.adapter
	for weak in ["llvmpipe (LLVM 20.1.2, 256 bits)", "Intel(R) HD Graphics 4000", "Microsoft Basic Render Driver"]:
		p.adapter = weak
		runner.check_eq(p.suggest_level(), 0, "Schwache Hardware erkannt: " + weak)
	p.adapter = "NVIDIA GeForce RTX 4070"
	runner.check(p.suggest_level() >= 2, "Starke Hardware bekommt hoehere Stufe")
	p.adapter = keep


func test_all_levels_have_every_key() -> void:
	var p = _perf()
	if p == null:
		return
	for key in p.TABLE:
		runner.check_eq((p.TABLE[key] as Array).size(), p.LEVELS, "Schluessel '%s' deckt alle Stufen ab" % key)
	runner.check_eq(p.NAMES.size(), p.LEVELS, "Namen fuer alle Stufen")
	runner.check_eq(p.DESCRIPTIONS.size(), p.LEVELS, "Beschreibungen fuer alle Stufen")
