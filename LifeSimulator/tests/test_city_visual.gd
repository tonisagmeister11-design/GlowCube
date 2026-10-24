extends Node
## Tests fuer die 3D-Darstellung der Stadt: Materialien, Chunk-Geometrie,
## Gebaeudearten, Innenraeume, Streaming-Knoten, Weltszene, Menue-Hintergrund.

var runner
var _city: CityModel


func _small_city() -> CityModel:
	if _city == null:
		_city = CityGenerator.generate(3, {"size": "small"})
	return _city


func test_materials() -> void:
	CityMaterials.warmup()
	for k in ["fac_plaster", "fac_glass", "g_asphalt", "u_roof_tiles", "foliage", "water", "marking", "signal", "lamp_glow", "signs"]:
		runner.check(CityMaterials.get_mat(k) != null, "Material %s fehlt" % k)
	CityMaterials.set_globals({"night": 1.0})
	runner.check_near(CityMaterials.get_global("night"), 1.0, 0.001, "Globaler Nachtwert")
	CityMaterials.set_globals({"night": 0.0})
	for t in ["asphalt_albedo", "asphalt_normal", "brick_albedo", "grass_albedo", "interior_atlas", "signs_atlas", "noise"]:
		runner.check(CityMaterials.tex(t) != null, "Textur %s fehlt" % t)


func test_all_building_kinds_build() -> void:
	var c := _small_city()
	var seen := {}
	for b in c.buildings:
		if seen.has(b.kind):
			continue
		seen[b.kind] = true
		var mb := MeshBatch.new()
		var det := MeshBatch.new()
		var out := {"labels": [], "shapes": [], "lights": [], "inst": {}, "lamps": [], "signals": []}
		BuildingBuilder.build(c, b, mb, det, out)
		runner.check(mb.vertex_count() > 0, "Gebaeudeart %s erzeugt keine Geometrie" % b.kind)
		if b.floors > 0:
			runner.check(out.shapes.size() > 0, "Gebaeudeart %s ohne Kollision" % b.kind)
		var far := MeshBatch.new()
		BuildingBuilder.build_far(c, b, far)
		if b.floors > 0:
			runner.check(far.vertex_count() > 0, "Fernansicht %s leer" % b.kind)
		if b.business_type != "" and b.kind in ["shop", "mixed", "supermarket"]:
			var found := false
			for l in out.labels:
				if l.text == b.name:
					found = true
			runner.check(found, "Ladenschild mit Namen fehlt (%s)" % b.kind)
	print("    Gebaeudearten geprueft: %d" % seen.size())


func test_chunk_generation_and_timing() -> void:
	var c := _small_city()
	var cb := CityBuilder.new()
	cb.city = c
	cb._build_index()
	var total := 0.0
	var worst := 0.0
	var n := 0
	var verts := 0
	for k in cb.all_chunk_keys():
		var d := CityBuilder.gen_chunk(c, k, cb._index, cb._lots_by_block)
		total += d.ms
		worst = maxf(worst, d.ms)
		verts += d.verts
		n += 1
		runner.check(d.main is Dictionary, "Chunkdaten unvollstaendig")
	runner.check(n > 10, "Zu wenige Chunks")
	runner.check(verts > 10000, "Zu wenig Geometrie")
	print("    %d Chunks: Ø %.1f ms, max %.1f ms, %d Vertices" % [n, total / n, worst, verts])
	cb.free()


func test_no_marking_below_road() -> void:
	# Markierungen liegen ueber der Fahrbahn, Gehwege bei 0.15 (kein Z-Fighting)
	var c := _small_city()
	var mb := MeshBatch.new()
	for e in c.edges.slice(0, 40):
		RoadBuilder.build_edge_markings(c, e, mb)
	var s: MeshBatch.Surf = mb.surfaces.get("marking")
	runner.check(s != null and s.v.size() > 0, "Keine Markierungen erzeugt")
	if s:
		for v in s.v:
			if v.y < 0.005:
				runner.check(false, "Markierung zu tief: %s" % str(v))
				break


func test_interiors() -> void:
	var expect := {
		"home_room": ["bed", "desk", "fridge", "shower", "toilet"],
		"home_apartment": ["bed", "sofa", "tv", "fridge", "stove", "shower", "toilet"],
		"home_house": ["bed", "sofa", "tv", "fridge", "stove", "desk"],
		"home_villa": ["bed", "sofa", "piano"],
		"home_estate": ["bed", "sofa"],
		"shelter": ["bed", "seat_0", "shower"],
		"shop": ["register", "shelf_0"],
		"supermarket": ["register", "shelf_0", "fridge"],
		"restaurant": ["counter", "seat_0", "stove"],
		"market_hall": ["counter", "shelf_0"],
		"office": ["work_0", "desk"],
		"school": ["seat_0", "work_0"],
		"university": ["seat_0"],
		"hospital": ["bed", "counter", "work_0"],
		"clinic": ["bed", "counter"],
		"police": ["counter", "work_0", "cell"],
		"city_hall": ["counter", "seat_0"],
		"fire_station": ["truck", "work_0"],
		"factory": ["work_0"],
		"warehouse": ["shelf_0", "work_0"],
	}
	for kind in InteriorBuilder.KINDS:
		var room := InteriorBuilder.build(kind, {"seed": 1, "business_type": "clothing"})
		runner.check(room != null, "Innenraum %s fehlt" % kind)
		var a: Dictionary = room.get_anchors()
		for need in ["spawn", "exit"] + expect.get(kind, []):
			runner.check(a.has(need), "Innenraum %s: Anker '%s' fehlt" % [kind, need])
		var body := room.get_node("Collision")
		runner.check(body.get_child_count() > 5, "Innenraum %s: zu wenig Kollision" % kind)
		var mesh: MeshInstance3D = room.get_node("Mesh")
		runner.check(mesh.mesh != null, "Innenraum %s: Mesh leer" % kind)
		var lights := 0
		for ch in room.get_children():
			if ch is OmniLight3D:
				lights += 1
		runner.check(lights >= 1 and lights <= 8, "Innenraum %s: %d Lichter" % [kind, lights])
		# Anker liegen im Raum
		var sz: Vector3 = room.size
		for k in a:
			var p: Vector3 = a[k].origin
			if p.x < -0.5 or p.z < -0.5 or p.x > sz.x + 0.5 or p.z > sz.z + 0.5:
				runner.check(false, "Innenraum %s: Anker %s ausserhalb %s" % [kind, k, str(p)])
		room.free()


func test_city_builder_streaming() -> void:
	var c := _small_city()
	var cb := CityBuilder.new()
	add_child(cb)
	var focus := Node3D.new()
	add_child(focus)
	var center := Vector3(0, 0, 0)
	if not c.spawn_points.is_empty():
		var sp: Vector2 = c.spawn_points[0].pos
		center = Vector3(sp.x, 0, sp.y)
	focus.global_position = center
	var t0 := Time.get_ticks_msec()
	cb.setup(c, focus)
	cb.build_area_now(center, 250.0)
	var ms := Time.get_ticks_msec() - t0
	var st := cb.get_stats()
	runner.check(st.loaded > 0, "Keine Chunks geladen")
	runner.check(cb.is_area_ready(center, 250.0), "Bereich nicht fertig")
	runner.check(cb.has_collision_at(center), "Keine Kollision am Fokus")
	runner.check(st.far > 0, "Keine Fernebene")
	print("    Streaming: %d Chunks in %d ms, %s" % [st.loaded, ms, str(st)])
	# Weit wegbewegen -> Entladen
	cb.view_distance = 300.0
	focus.global_position = center + Vector3(5000, 0, 0)
	cb._update_streaming()
	runner.check(cb.get_stats().loaded < st.loaded, "Ferne Chunks werden nicht entladen")
	cb.set_debug_overlay(true)
	runner.check(cb.is_debug_overlay(), "Debugansicht laesst sich nicht einschalten")
	cb.set_debug_overlay(false)
	cb.free()
	focus.free()


func test_menu_backdrop() -> void:
	var t0 := Time.get_ticks_msec()
	var scn: PackedScene = load("res://scenes/menu/menu_backdrop.tscn")
	var mb: Node3D = scn.instantiate()
	add_child(mb)
	var ms := Time.get_ticks_msec() - t0
	print("    Menue-Hintergrund: %d ms" % ms)
	runner.check(mb.get_node("Camera") is Camera3D, "Kamera fehlt")
	runner.check(mb.builder.get_stats().loaded > 3, "Menue-Stadt nicht aufgebaut")
	runner.check(ms < 15000, "Menue-Hintergrund zu langsam (%d ms)" % ms)
	mb.free()


func test_world_scene() -> void:
	var game := get_node_or_null("/root/Game")
	var prev_world = game.world if game else null
	var scn: PackedScene = load("res://scenes/world/world.tscn")
	var w: Node3D = scn.instantiate()
	add_child(w)
	runner.check(w.get_node("City").get_stats().loaded > 0, "Weltszene ohne Stadt")
	runner.check(w.get_node("Actors").get_child_count() > 0, "Weltszene ohne Spieler/Kamera")
	runner.check(w.get_node("Environment").environment != null, "Weltszene ohne Umgebung")
	runner.check(w.get_node("UI") is CanvasLayer, "UI-Ebene fehlt")
	w.free()
	if game:
		game.world = prev_world
		game.playing = false


func test_day_night() -> void:
	var dn := DayNight.new()
	add_child(dn)
	dn.set_weather("clear", true)
	dn.set_hour(13.0)
	runner.check(CityMaterials.get_global("night") < 0.05, "Mittags ist es Nacht")
	runner.check(dn.sun.light_energy > 0.8, "Sonne zu schwach am Mittag")
	dn.set_hour(23.5)
	runner.check(CityMaterials.get_global("night") > 0.9, "Nachts ist es hell")
	runner.check(CityMaterials.get_global("lit_fraction") > 0.2, "Nachts keine beleuchteten Fenster")
	dn.set_weather("rain", true)
	dn.set_hour(12.0)
	runner.check(CityMaterials.get_global("wet") > 0.5, "Regen macht nicht nass")
	dn.free()
	CityMaterials.set_globals({"night": 0.0, "wet": 0.0})
