extends RefCounted
## Prueft die Fahrzeugmarke KALDRA: Emblem als Mesh und als Bild, Namen.

var runner

## Echte Hersteller, die in keinem Markennamen vorkommen duerfen.
const REAL_BRANDS := ["lamborghini", "audi", "jeep", "bmw", "mercedes", "porsche",
	"ferrari", "volkswagen", "toyota", "ford", "renault", "opel", "skoda", "tesla"]


func test_name_is_invented() -> void:
	runner.check_eq(BrandKaldra.NAME, "KALDRA", "Markenname")
	for b in REAL_BRANDS:
		runner.check(not BrandKaldra.NAME.to_lower().contains(b), "Kein echter Hersteller: %s" % b)
	for id in BrandKaldra.MODEL_NAMES:
		var n := str(BrandKaldra.MODEL_NAMES[id])
		runner.check(n.begins_with("KALDRA "), "Modellname traegt die Marke: %s" % n)
		for b in REAL_BRANDS:
			runner.check(not n.to_lower().contains(b), "Kein echter Hersteller in '%s'" % n)


func test_renamed_ids_still_resolve() -> void:
	# Aeltere Spielstaende kennen die Kennungen von vor dem Markenwechsel.
	for old in BrandKaldra.RENAMED:
		var new_id := str(BrandKaldra.RENAMED[old])
		runner.check(ImportedVehicles.has_model(new_id), "Neue Kennung vorhanden: %s" % new_id)
		runner.check(ImportedVehicles.has_model(str(old)), "Alte Kennung findet noch: %s" % old)
		runner.check_eq(str(ImportedVehicles.info(str(old)).get("name", "")),
			str(ImportedVehicles.info(new_id).get("name", "")), "Beide Kennungen meinen dasselbe")


func test_badge_mesh() -> void:
	var mesh := BrandKaldra.badge_mesh(0.09)
	runner.check(mesh != null, "Emblem wird gebaut")
	runner.check_eq(mesh.get_surface_count(), 2, "Zwei Oberflaechen (Chrom und dunkle Flaeche)")
	var tris := 0
	for s in mesh.get_surface_count():
		var idx: PackedInt32Array = mesh.surface_get_arrays(s)[Mesh.ARRAY_INDEX]
		tris += idx.size() / 3 if idx.size() > 0 else mesh.surface_get_array_len(s) / 3
	# An jedem Fahrzeug haengt das Zeichen zweimal, es darf also kaum kosten.
	runner.check(tris > 30 and tris < 400, "Wenige Dreiecke: %d" % tris)
	var box := mesh.get_aabb()
	runner.check(absf(box.size.y - 0.09) < 0.004, "Hoehe wie angegeben: %.3f m" % box.size.y)
	runner.check(box.size.x > box.size.y, "Liegendes Sechseck: %.3f breit, %.3f hoch" % [box.size.x, box.size.y])
	runner.check(box.size.z < 0.03, "Flach: %.3f m" % box.size.z)


func test_badge_node_has_materials() -> void:
	var node := BrandKaldra.badge_node(0.09)
	runner.check(node != null, "Knoten wird gebaut")
	var chrome := node.get_surface_override_material(0) as BaseMaterial3D
	var dark := node.get_surface_override_material(1) as BaseMaterial3D
	runner.check(chrome != null and dark != null, "Beide Materialien gesetzt")
	if chrome != null and dark != null:
		runner.check(chrome.metallic > 0.8, "Chrom ist metallisch: %.2f" % chrome.metallic)
		runner.check(dark.albedo_color.v < 0.1, "Innenflaeche ist dunkel: %.3f" % dark.albedo_color.v)
	node.free()


func test_badge_image_is_readable() -> void:
	# Das Bild muss drei Bereiche haben: durchsichtig aussen, dunkle
	# Innenflaeche, helles K. Sonst ist auf dem Kuehlergrill nichts zu erkennen.
	var img := BrandKaldra.badge_image(128)
	runner.check_eq(img.get_width(), 128, "Breite")
	var clear := 0
	var dark := 0
	var light := 0
	for y in img.get_height():
		for x in img.get_width():
			var c := img.get_pixel(x, y)
			if c.a < 0.5:
				clear += 1
			elif c.v < 0.3:
				dark += 1
			else:
				light += 1
	runner.check(clear > 200, "Durchsichtiger Rand: %d Punkte" % clear)
	runner.check(dark > 200, "Dunkle Innenflaeche: %d Punkte" % dark)
	runner.check(light > 200, "Helles Zeichen: %d Punkte" % light)
