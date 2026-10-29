extends RefCounted
## Prueft zugelieferte Fahrzeugmodelle: Aufbereitung, Raeder, Lack,
## Detailstufen und die Verbindung zu Ware und Katalog.

var runner


func test_model_available_and_sane() -> void:
	var ids: Array = ImportedVehicles.available()
	runner.check(ids.size() >= 1, "Mindestens ein aufbereitetes Modell vorhanden: %s" % str(ids))
	for id in ids:
		var d: Dictionary = ImportedVehicles.info(str(id))
		runner.check(str(d.get("name", "")) != "", "Modell '%s' hat einen Namen" % id)
		runner.check(not str(d.name).to_lower().contains("lamborghini"), "Kein echter Markenname: %s" % str(d.name))
		runner.check(str(d.get("license", "")) != "", "Lizenzangabe vorhanden")
		var tris: int = ImportedVehicles.triangles(str(id))
		runner.check(tris > 1000 and tris < 400000, "Dreiecke im brauchbaren Rahmen: %d" % tris)


func test_build_has_wheels_and_body() -> void:
	for id in ImportedVehicles.available():
		var node: Node3D = ImportedVehicles.build(str(id), Color(0.2, 0.4, 0.9))
		runner.check(node != null, "Modell '%s' baut" % id)
		if node == null:
			continue
		var body := node.get_node_or_null("Body")
		runner.check(body != null, "Karosserie vorhanden")
		var wheels := 0
		for slot in ["WheelFL", "WheelFR", "WheelRL", "WheelRR"]:
			if node.get_node_or_null(slot) != null:
				wheels += 1
		runner.check_eq(wheels, 4, "Vier drehbare Raeder")
		var size: Vector3 = node.get_meta("size", Vector3.ZERO)
		runner.check(size.z > 3.0 and size.z < 6.5, "Fahrzeuglaenge plausibel: %.2f m" % size.z)
		runner.check(size.x > 1.4 and size.x < 2.6, "Fahrzeugbreite plausibel: %.2f m" % size.x)
		node.free()


func test_catalog_and_good_linked() -> void:
	# Die Ware im Autohaus muss auf das Modell zeigen
	var good := "car_hyper"
	var g: Dictionary = GoodsCatalog.get_good(good)
	runner.check(not g.is_empty(), "Ware '%s' existiert" % good)
	runner.check_eq(str(g.get("category", "")), "vehicle", "Ware ist ein Fahrzeug")
	var model := VehicleCatalog.imported_for_good(good)
	runner.check(ImportedVehicles.has_model(model), "Ware zeigt auf ein Modell: %s" % model)
	runner.check_eq(VehicleCatalog.type_for_good(good), "sports", "Fahrzeugtyp zugeordnet")
	var m: Dictionary = VehicleCatalog.get_model(model)
	runner.check(not m.is_empty(), "Modell im Fahrzeugkatalog")
	runner.check(float(m.get("price", 0.0)) > 0.0, "Preis gesetzt")


func test_visual_uses_imported_model() -> void:
	var v := VehicleVisual.new()
	v.build("sports", Color(1, 0.7, 0), 0, {"model_id": "vanari_furio"})
	runner.check(v.get_node_or_null("ImportedBody") != null, "Fremdmodell wird verwendet")
	runner.check_eq(v.wheels.size(), 4, "Raeder uebernommen")
	for w in v.wheels:
		runner.check(float(w.r) > 0.2 and float(w.r) < 0.6, "Radradius plausibel: %.2f" % float(w.r))
	runner.check(v.wheels[0].steer or v.wheels[1].steer, "Vorderraeder lenken")
	v.free()


func test_fallback_without_model() -> void:
	# Ohne hinterlegtes Modell muss weiterhin das einfache Fahrzeug gebaut werden
	var v := VehicleVisual.new()
	v.build("sedan", Color(0.3, 0.3, 0.3), 0, {"model_id": "gibt_es_nicht"})
	runner.check(v.get_node_or_null("ImportedBody") == null, "Kein Fremdmodell")
	runner.check(v.get_node_or_null("Body") != null, "Einfaches Modell gebaut")
	v.free()
