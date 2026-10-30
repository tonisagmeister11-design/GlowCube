class_name UIMapData
extends RefCounted
## Gemeinsame Kartendaten fuer Stadtkarte und Minikarte: Kartenbild
## (einmal pro Stadt gerendert, im Hintergrund), Markierungen nach
## Kategorien und die aktuelle Fussweg-Route.

const PPM := 0.6   # Pixel pro Meter des Kartenbilds

## Kategorie -> [Bezeichnung, Icon, Farbe]
const CATEGORIES := {
	"food": ["Essen & Trinken", "food", Color("#ff9f43")],
	"shopping": ["Einkaufen", "shopping", Color("#b388ff")],
	"market": ["Märkte", "market", Color("#ffd166")],
	"health": ["Gesundheit", "hospital", Color("#ff5d6c")],
	"leisure": ["Freizeit", "leisure", Color("#f78fb3")],
	"vehicles": ["Fahrzeuge", "car", Color("#4cc9f0")],
	"mycars": ["Deine Fahrzeuge", "car", Color("#ffd166")],
	"services": ["Dienstleistungen", "services", Color("#7bdff2")],
	"education": ["Schulen & Universität", "school", Color("#3ddc97")],
	"police": ["Polizei", "police", Color("#5c8dff")],
	"fire": ["Feuerwehr", "flame", Color("#ff7a45")],
	"government": ["Verwaltung", "capitol", Color("#c9d3e3")],
	"transit": ["Bushaltestellen", "bus_stop", Color("#2fa8ff")],
	"housing": ["Immobilien", "key", Color("#6fe3a3")],
}
const CIVIC_CATEGORY := {
	"school": "education", "university": "education", "hospital": "health", "clinic": "health",
	"police": "police", "fire_station": "fire", "city_hall": "government", "parliament": "government",
	"shelter": "government", "station": "transit", "bus_depot": "transit",
}

static var _tex_seed := -1
static var _tex: Texture2D
static var _task := -1
static var _img: Image

## Aktuelle Route (Weltkoordinaten x/z) und Ziel.
static var route := PackedVector2Array()
static var route_label := ""
static var route_target := Vector2.INF
static var route_length := 0.0
static var route_reliable := true


## Kartenbild; null solange es noch im Hintergrund entsteht.
static func texture(world: WorldState) -> Texture2D:
	if world == null or world.city == null:
		return null
	if _tex_seed == world.seed and _tex != null:
		return _tex
	if _task >= 0:
		if WorkerThreadPool.is_task_completed(_task):
			WorkerThreadPool.wait_for_task_completion(_task)
			_task = -1
			if _img:
				_tex = ImageTexture.create_from_image(_img)
				_img = null
				_tex_seed = world.seed
			return _tex
		return null
	_tex = null
	var city := world.city
	_task = WorkerThreadPool.add_task(func() -> void: _img = CityMapRenderer.render(city, PPM))
	return null


## Synchron rendern (Tests, Screenshots).
static func texture_now(world: WorldState) -> Texture2D:
	var t := texture(world)
	if t == null and _task >= 0:
		WorkerThreadPool.wait_for_task_completion(_task)
		_task = -1
		_tex = ImageTexture.create_from_image(_img)
		_img = null
		_tex_seed = world.seed
		t = _tex
	return t


static func to_px(world: WorldState, p: Vector2) -> Vector2:
	return CityMapRenderer.world_to_px(world.city, p, PPM)


static func to_world(world: WorldState, px: Vector2) -> Vector2:
	return CityMapRenderer.px_to_world(world.city, px, PPM)


## Alle Markierungen: [{pos, cat, name, type_label, building, address, district, open}]
static func markers(world: WorldState) -> Array:
	var out := []
	if world == null or world.city == null:
		return out
	var c := world.city
	for b in c.buildings:
		var cat := ""
		var type_label := ""
		var open_txt := ""
		if b.business_type != "":
			var bt: Dictionary = CityCatalog.BUSINESS_TYPES.get(b.business_type, {})
			cat = str(bt.get("map_category", "services"))
			type_label = str(bt.get("label", ""))
			var o: Array = bt.get("open", [])
			if o.size() == 2:
				open_txt = "Täglich 0–24 Uhr" if int(o[0]) == 0 and int(o[1]) == 24 else "%d–%d Uhr" % [int(o[0]), int(o[1]) % 24]
		elif CIVIC_CATEGORY.has(b.kind):
			cat = CIVIC_CATEGORY[b.kind]
			type_label = str(CityCatalog.BUILDING_KINDS.get(b.kind, {}).get("label", b.kind))
		if cat == "":
			continue
		var nm: String = b.name if str(b.name) != "" else type_label
		out.append({"pos": b.sidewalk_point, "cat": cat, "name": nm, "type_label": type_label, "building": b.id,
			"address": str(b.address), "district": _district_name(c, b), "open": open_txt})
	for s in c.bus_stops:
		var lines := PackedStringArray()
		for lid in s.lines:
			lines.append(str(c.bus_lines[lid].number) if lid < c.bus_lines.size() else str(lid))
		out.append({"pos": s.pos, "cat": "transit", "name": "Haltestelle %s" % s.name, "type_label": "Bushaltestelle",
			"building": -1, "address": "Linien " + ", ".join(lines), "district": str(c.district_at(s.pos).get("name", "")), "open": ""})
	# Eigene Fahrzeuge: damit man ein gekauftes Auto wiederfindet
	var tree := Engine.get_main_loop() as SceneTree
	var tmn = tree.get_first_node_in_group("traffic_manager") if tree != null else null
	if tmn != null and world.player != null:
		for i in world.player.vehicles.size():
			var vp: Vector2 = tmn.owned_position(i)
			if vp == Vector2.INF:
				continue
			var ve: Dictionary = world.player.vehicles[i]
			out.append({"pos": vp, "cat": "mycars", "name": "Dein Auto: %s" % str(ve.get("name", "Fahrzeug")),
				"type_label": "Eigenes Fahrzeug", "building": -1,
				"address": "Mit C zu dir rufen", "district": str(c.district_at(vp).get("name", "")), "open": ""})
	# Immobilienangebote (gruppiert nach Gebaeude)
	if world.housing != null and world.housing.has_method("list_available"):
		var seen := {}
		for pr in world.housing.list_available({"limit": 80}, world):
			var bid := int(pr.get("building", -1))
			if bid < 0 or seen.has(bid) or bid >= c.buildings.size():
				continue
			seen[bid] = true
			var bb: Dictionary = c.buildings[bid]
			var price := "Miete %s/Woche" % UIKit.money(float(pr.get("rent_per_week", 0.0)), 0) if bool(pr.get("for_rent", false)) \
				else "Kaufpreis %s" % UIKit.money(float(pr.get("price", 0.0)), 0)
			out.append({"pos": bb.sidewalk_point, "cat": "housing", "name": str(bb.address), "type_label": "Immobilienangebot",
				"building": bid, "address": price, "district": _district_name(c, bb), "open": ""})
	return out


static func _district_name(c: CityModel, b: Dictionary) -> String:
	var d := int(b.get("district", -1))
	return str(c.districts[d].name) if d >= 0 and d < c.districts.size() else ""


static func player_pos(tree: SceneTree) -> Vector2:
	var pl := tree.get_first_node_in_group("player") if tree else null
	if pl is Node3D:
		return Vector2(pl.global_position.x, pl.global_position.z)
	return Vector2.INF


## Blickrichtung (Kamera, sonst Spieler) als Winkel fuer die Karte.
static func heading(tree: SceneTree) -> Vector2:
	if tree == null:
		return Vector2(0, -1)
	var cam := tree.root.get_viewport().get_camera_3d()
	var basis: Basis
	if cam:
		basis = cam.global_transform.basis
	else:
		var pl := tree.get_first_node_in_group("player")
		if not (pl is Node3D):
			return Vector2(0, -1)
		basis = pl.global_transform.basis
	var f := -basis.z
	var d := Vector2(f.x, f.z)
	return d.normalized() if d.length() > 0.01 else Vector2(0, -1)


## Fussweg vom Spieler zum Ziel berechnen (nur ueber Gehwege/Zebrastreifen).
static func set_route(world: WorldState, from: Vector2, target: Vector2, label: String, emit := true) -> void:
	route_target = target
	route_label = label
	route = PackedVector2Array()
	route_length = 0.0
	route_reliable = false
	if world == null or world.city == null or from == Vector2.INF:
		return
	route = world.city.ped_route(from, target)
	route_length = CityModel.polyline_length(route)
	# Nur 2 Punkte = keine Gehweg-Anbindung gefunden -> Entfernung unsicher
	route_reliable = route.size() > 2 or from.distance_to(target) < 60.0
	if emit:
		EventBus.request_route.emit(CityModel.v3(target), label)


static func clear_route() -> void:
	route = PackedVector2Array()
	route_label = ""
	route_target = Vector2.INF
	route_length = 0.0
	EventBus.route_cleared.emit()
