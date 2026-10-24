class_name InteriorBuilder
extends RefCounted
## Prozedurale, moeblierte Innenraeume mit Licht, Kollision und Ankerpunkten.
##
##   var room := InteriorBuilder.build("home_apartment", {"seed": 4})
##   room.position = Vector3(0, -2000, 0); add_child(room)
##   var a := room.get_anchors()   # {"spawn": Transform3D, "bed": ..., ...}
##
## Arten: home_room, home_apartment, home_house, home_villa, home_estate,
## shelter, shop (params.business_type), supermarket, restaurant, market_hall,
## office, school, university, hospital, clinic, police, city_hall,
## fire_station, factory, warehouse.
## Raum liegt bei x 0..W, z 0..D, Boden y = 0; Ausgang an der Suedwand (+z).
## Anker zeigen mit -Z auf das Objekt; "spawn" steht hinter der Tuer.

const KINDS := ["home_room", "home_apartment", "home_house", "home_villa", "home_estate", "shelter", "shop",
	"supermarket", "restaurant", "market_hall", "office", "school", "university", "hospital", "clinic",
	"police", "city_hall", "fire_station", "factory", "warehouse"]

static var _window_mat: StandardMaterial3D


class Ctx:
	var mb := MeshBatch.new()
	var root: InteriorRoot
	var body: StaticBody3D
	var rng := RandomNumberGenerator.new()
	var n_lights := 0
	var counters := {}

	func next(prefix: String) -> String:
		var i: int = counters.get(prefix, 0)
		counters[prefix] = i + 1
		return "%s_%d" % [prefix, i]


static func build(kind: String, params := {}) -> Node3D:
	var root := InteriorRoot.new()
	root.name = "Interior_" + kind
	root.kind = kind
	root.params = params
	var c := Ctx.new()
	c.root = root
	c.rng.seed = hash("interior:%s:%s" % [kind, str(params.get("seed", 0))])
	c.body = StaticBody3D.new()
	c.body.name = "Collision"
	root.add_child(c.body)
	match kind:
		"home_room": _home(c, 5.0, 4.4, 1)
		"home_apartment": _home(c, 9.0, 7.5, 2)
		"home_house": _home(c, 12.0, 9.0, 3)
		"home_villa": _home(c, 16.0, 12.0, 4)
		"home_estate": _home(c, 20.0, 14.0, 5)
		"shelter": _shelter(c)
		"shop": _shop(c, str(params.get("business_type", "grocery")))
		"supermarket": _supermarket(c)
		"restaurant": _restaurant(c)
		"market_hall": _market_hall(c)
		"office": _office(c)
		"school": _school(c, false)
		"university": _school(c, true)
		"hospital": _hospital(c, true)
		"clinic": _hospital(c, false)
		"police": _police(c)
		"city_hall": _city_hall(c)
		"fire_station": _fire_station(c)
		"factory": _factory(c)
		"warehouse": _warehouse(c)
		_: _home(c, 9.0, 7.5, 2)
	var mi := MeshInstance3D.new()
	mi.name = "Mesh"
	mi.mesh = MeshBatch.build_mesh(c.mb.to_data(), func(k): return _mat(k))
	root.add_child(mi)
	return root


static func _mat(k: String) -> Material:
	if k == "int_window":
		if _window_mat == null:
			_window_mat = StandardMaterial3D.new()
			_window_mat.albedo_color = Color(0.75, 0.85, 0.95)
			_window_mat.emission_enabled = true
			_window_mat.emission = Color(0.75, 0.85, 0.95)
			_window_mat.emission_energy_multiplier = 1.4
		return _window_mat
	return CityMaterials.get_mat(k)


# ============================================================ Grundbausteine

static func _box(c: Ctx, key: String, center: Vector3, size: Vector3, col := Color.WHITE, solid := false, yaw := 0.0) -> void:
	if yaw == 0.0:
		c.mb.box(key, center, size, col)
	else:
		c.mb.box_rot(key, center, size, yaw, col)
	if solid:
		var cs := CollisionShape3D.new()
		var bs := BoxShape3D.new()
		bs.size = size
		cs.shape = bs
		cs.transform = Transform3D(Basis(Vector3.UP, yaw), center)
		c.body.add_child(cs)


static func _anchor(c: Ctx, name: String, pos: Vector3, look: Vector3) -> void:
	var t := Transform3D(Basis(), pos)
	var d := look - pos
	d.y = 0.0
	if d.length() > 0.01:
		t = t.looking_at(Vector3(look.x, pos.y, look.z), Vector3.UP)
	c.root.anchors[name] = t


static func _light(c: Ctx, pos: Vector3, energy := 1.2, rng := 9.0, col := Color(1.0, 0.9, 0.78)) -> void:
	_box(c, "lamp_glow", pos + Vector3(0, 0.02, 0), Vector3(0.5, 0.04, 0.5), col)
	if c.n_lights >= 8:
		return
	c.n_lights += 1
	var l := OmniLight3D.new()
	l.position = pos - Vector3(0, 0.3, 0)
	l.omni_range = rng
	l.light_energy = energy
	l.light_color = col
	l.omni_attenuation = 0.8
	l.shadow_enabled = false
	c.root.add_child(l)


## Raumhuelle: Boden, Decke, Waende mit Tuer (Sued) und Fenstern (Nord, Ost, West).
static func _shell(c: Ctx, W: float, D: float, H: float, wall: Color, floor_key: String, floor_col: Color, door_x := -1.0) -> void:
	c.root.size = Vector3(W, H, D)
	var t := 0.2
	if door_x < 0.0:
		door_x = W * 0.5
	_box(c, floor_key, Vector3(W * 0.5, -0.1, D * 0.5), Vector3(W + 2 * t, 0.2, D + 2 * t), floor_col, true)
	_box(c, "u_plaster", Vector3(W * 0.5, H + 0.1, D * 0.5), Vector3(W + 2 * t, 0.2, D + 2 * t), Color(0.95, 0.95, 0.93), true)
	_box(c, "u_plaster", Vector3(W * 0.5, H * 0.5, -t * 0.5), Vector3(W + 2 * t, H, t), wall, true)
	_box(c, "u_plaster", Vector3(-t * 0.5, H * 0.5, D * 0.5), Vector3(t, H, D), wall, true)
	_box(c, "u_plaster", Vector3(W + t * 0.5, H * 0.5, D * 0.5), Vector3(t, H, D), wall, true)
	# Suedwand mit Tueroeffnung 1.2 m
	var dw := 1.3
	var l_len := door_x - dw * 0.5
	var r_len := W - door_x - dw * 0.5
	if l_len > 0.01:
		_box(c, "u_plaster", Vector3(l_len * 0.5, H * 0.5, D + t * 0.5), Vector3(l_len, H, t), wall, true)
	if r_len > 0.01:
		_box(c, "u_plaster", Vector3(door_x + dw * 0.5 + r_len * 0.5, H * 0.5, D + t * 0.5), Vector3(r_len, H, t), wall, true)
	_box(c, "u_plaster", Vector3(door_x, (H + 2.3) * 0.5, D + t * 0.5), Vector3(dw, H - 2.3, t), wall, true)
	# geschlossene Tuer (Ausgang)
	_box(c, "u_wood", Vector3(door_x, 1.15, D + t * 0.6), Vector3(dw - 0.1, 2.3, 0.06), Color(0.6, 0.45, 0.32), true)
	_box(c, "paint", Vector3(door_x, 1.18, D + t * 0.5 - 0.06), Vector3(dw + 0.12, 2.42, 0.06), Color(0.92, 0.92, 0.9))
	_box(c, "chrome", Vector3(door_x + dw * 0.3, 1.05, D + t * 0.5 - 0.1), Vector3(0.14, 0.03, 0.05), Color.WHITE)
	# Sockelleiste
	_box(c, "paint", Vector3(W * 0.5, 0.05, 0.02), Vector3(W, 0.1, 0.04), wall.darkened(0.3))
	# Fenster (leuchtende Scheiben = Tageslicht von aussen)
	var nwin := int(W / 3.2)
	for k in nwin:
		var x := (k + 0.5) * W / nwin
		_window(c, Vector3(x, 1.55, 0.01), 0.0, minf(1.6, W / nwin - 0.6), H)
	var nside := int(D / 3.6)
	for k in nside:
		var z := (k + 0.5) * D / nside
		if z > D - 1.2:
			continue
		_window(c, Vector3(0.01, 1.55, z), PI * 0.5, 1.2, H)
		_window(c, Vector3(W - 0.01, 1.55, z), -PI * 0.5, 1.2, H)
	_anchor(c, "exit", Vector3(door_x, 0.0, D - 0.6), Vector3(door_x, 0.0, D + 1.0))
	_anchor(c, "spawn", Vector3(door_x, 0.0, D - 1.0), Vector3(door_x, 0.0, D * 0.5 - 2.0))


static func _window(c: Ctx, pos: Vector3, yaw: float, w: float, H: float) -> void:
	var h := minf(1.5, H - 1.4)
	var bas := Basis(Vector3.UP, yaw)
	c.mb.box_xf("int_window", Transform3D(bas, pos), Vector3(w, h, 0.02), Color.WHITE)
	c.mb.box_xf("paint", Transform3D(bas, pos + bas * Vector3(0, 0, 0.02)), Vector3(0.05, h, 0.03), Color(0.95, 0.95, 0.95))
	c.mb.box_xf("paint", Transform3D(bas, pos + bas * Vector3(0, -h * 0.5 - 0.04, 0.08)), Vector3(w + 0.2, 0.05, 0.18), Color(0.95, 0.95, 0.95))
	c.mb.box_xf("paint", Transform3D(bas, pos + bas * Vector3(0, 0, 0.015)), Vector3(w + 0.1, h + 0.1, 0.01) * Vector3(1, 1, 1), Color(0.95, 0.95, 0.95))
	c.mb.box_xf("int_window", Transform3D(bas, pos + bas * Vector3(0, 0, 0.03)), Vector3(w - 0.08, h - 0.08, 0.005), Color.WHITE)


## Innenwand von a nach b (x/z) mit optionaler Tuer bei Anteil gap (0..1).
static func _partition(c: Ctx, a: Vector2, b: Vector2, H: float, col: Color, gap := 0.5, gap_w := 1.0) -> void:
	var ln := a.distance_to(b)
	var dir := (b - a) / ln
	var yaw := atan2(dir.x, dir.y) + PI * 0.5
	var g0 := ln * gap - gap_w * 0.5
	var g1 := ln * gap + gap_w * 0.5
	for seg in [[0.0, g0], [g1, ln]]:
		var s0: float = seg[0]
		var s1: float = seg[1]
		if s1 - s0 < 0.05:
			continue
		var m := a + dir * ((s0 + s1) * 0.5)
		_box(c, "u_plaster", Vector3(m.x, H * 0.5, m.y), Vector3(s1 - s0, H, 0.12), col, true, yaw)
	if gap > 0.0:
		var m2 := a + dir * (ln * gap)
		_box(c, "u_plaster", Vector3(m2.x, (H + 2.2) * 0.5, m2.y), Vector3(gap_w, H - 2.2, 0.12), col, true, yaw)


# ============================================================ Moebel

static func _bed(c: Ctx, p: Vector3, yaw: float, w := 1.6, name := "bed") -> void:
	var bas := Basis(Vector3.UP, yaw)
	var cols := [Color(0.75, 0.82, 0.9), Color(0.9, 0.85, 0.75), Color(0.75, 0.88, 0.78), Color(0.92, 0.78, 0.78)]
	var bc: Color = cols[c.rng.randi_range(0, 3)]
	_box(c, "u_wood", p + bas * Vector3(0, 0.22, 0), Vector3(w + 0.1, 0.44, 2.1), Color(0.55, 0.4, 0.28), true, yaw)
	_box(c, "paint_rough", p + bas * Vector3(0, 0.5, 0.05), Vector3(w, 0.18, 1.95), Color(0.95, 0.95, 0.93), false, yaw)
	_box(c, "paint_rough", p + bas * Vector3(0, 0.6, 0.35), Vector3(w + 0.04, 0.06, 1.3), bc, false, yaw)
	_box(c, "paint_rough", p + bas * Vector3(0, 0.66, -0.75), Vector3(w * 0.8, 0.14, 0.4), Color(1, 1, 1), false, yaw)
	_box(c, "u_wood", p + bas * Vector3(0, 0.6, -1.03), Vector3(w + 0.1, 1.2, 0.08), Color(0.5, 0.36, 0.25), false, yaw)
	_anchor(c, name, p + bas * Vector3(w * 0.5 + 0.5, 0, 0.3) - Vector3(0, p.y, 0), p)


static func _sofa(c: Ctx, p: Vector3, yaw: float, w := 2.2, name := "sofa") -> void:
	var bas := Basis(Vector3.UP, yaw)
	var col: Color = [Color(0.3, 0.38, 0.5), Color(0.55, 0.32, 0.28), Color(0.45, 0.45, 0.42), Color(0.3, 0.45, 0.38)][c.rng.randi_range(0, 3)]
	_box(c, "paint_rough", p + bas * Vector3(0, 0.22, 0), Vector3(w, 0.44, 0.9), col, true, yaw)
	_box(c, "paint_rough", p + bas * Vector3(0, 0.62, -0.35), Vector3(w, 0.5, 0.2), col.darkened(0.08), false, yaw)
	for s in [-1.0, 1.0]:
		_box(c, "paint_rough", p + bas * Vector3(s * (w * 0.5 - 0.1), 0.55, 0), Vector3(0.2, 0.3, 0.9), col.darkened(0.1), false, yaw)
	_anchor(c, name, p + bas * Vector3(0, 0, 0.9), p)


static func _table(c: Ctx, p: Vector3, size: Vector2, h := 0.75, col := Color(0.55, 0.4, 0.28)) -> void:
	_box(c, "u_wood", p + Vector3(0, h - 0.03, 0), Vector3(size.x, 0.06, size.y), col, true)
	for sx in [-1.0, 1.0]:
		for sz in [-1.0, 1.0]:
			_box(c, "paint_metal", p + Vector3(sx * (size.x * 0.5 - 0.06), (h - 0.06) * 0.5, sz * (size.y * 0.5 - 0.06)), Vector3(0.05, h - 0.06, 0.05), Color(0.2, 0.2, 0.2))


static func _chair(c: Ctx, p: Vector3, yaw: float, col := Color(0.5, 0.36, 0.26)) -> void:
	var bas := Basis(Vector3.UP, yaw)
	_box(c, "u_wood", p + Vector3(0, 0.45, 0), Vector3(0.45, 0.05, 0.45), col, false, yaw)
	_box(c, "u_wood", p + bas * Vector3(0, 0.72, -0.2), Vector3(0.45, 0.5, 0.04), col, false, yaw)
	for sx in [-1.0, 1.0]:
		for sz in [-1.0, 1.0]:
			_box(c, "paint_metal", p + bas * Vector3(sx * 0.19, 0.22, sz * 0.19), Vector3(0.04, 0.44, 0.04), Color(0.2, 0.2, 0.2), false, yaw)


static func _desk(c: Ctx, p: Vector3, yaw: float, name: String, computer := true) -> void:
	var bas := Basis(Vector3.UP, yaw)
	_box(c, "paint", p + bas * Vector3(0, 0.73, 0), Vector3(1.4, 0.04, 0.7), Color(0.9, 0.9, 0.88), true, yaw)
	for sx in [-1.0, 1.0]:
		_box(c, "paint_metal", p + bas * Vector3(sx * 0.65, 0.36, 0), Vector3(0.04, 0.72, 0.65), Color(0.3, 0.3, 0.32), false, yaw)
	if computer:
		_box(c, "paint_metal", p + bas * Vector3(0, 1.0, -0.2), Vector3(0.6, 0.38, 0.03), Color(0.08, 0.08, 0.09), false, yaw)
		_box(c, "window_glow", p + bas * Vector3(0, 1.0, -0.18), Vector3(0.56, 0.33, 0.01), Color(0.4, 0.6, 0.85), false, yaw)
		_box(c, "paint_metal", p + bas * Vector3(0, 0.78, 0.05), Vector3(0.45, 0.02, 0.15), Color(0.15, 0.15, 0.16), false, yaw)
	_chair(c, p + bas * Vector3(0, 0, 0.6), yaw + PI, Color(0.2, 0.2, 0.22))
	_anchor(c, name, p + bas * Vector3(0, 0, 0.75), p)


static func _counter(c: Ctx, p: Vector3, yaw: float, w: float, col := Color(0.85, 0.85, 0.83), name := "counter") -> void:
	var bas := Basis(Vector3.UP, yaw)
	_box(c, "paint", p + Vector3(0, 0.5, 0), Vector3(w, 1.0, 0.7) if absf(sin(yaw)) < 0.5 else Vector3(0.7, 1.0, w), col, true)
	_box(c, "u_stone", p + Vector3(0, 1.02, 0), Vector3(w + 0.04, 0.04, 0.74) if absf(sin(yaw)) < 0.5 else Vector3(0.74, 0.04, w + 0.04), Color(0.3, 0.3, 0.32))
	if name != "":
		_anchor(c, name, p + bas * Vector3(0, 0, 0.8), p)


static func _shelf(c: Ctx, p: Vector3, yaw: float, w := 2.0, h := 2.0, goods := true, name := "") -> void:
	var bas := Basis(Vector3.UP, yaw)
	_box(c, "paint_metal", p + bas * Vector3(0, h * 0.5, -0.25), Vector3(w, h, 0.05), Color(0.7, 0.72, 0.74), true, yaw)
	for sx in [-1.0, 1.0]:
		_box(c, "paint_metal", p + bas * Vector3(sx * w * 0.5, h * 0.5, 0), Vector3(0.04, h, 0.5), Color(0.6, 0.62, 0.64), false, yaw)
	var levels := int(h / 0.45)
	for k in levels:
		var y := 0.15 + k * 0.45
		_box(c, "paint_metal", p + bas * Vector3(0, y, 0), Vector3(w, 0.03, 0.5), Color(0.75, 0.76, 0.78), false, yaw)
		if goods:
			var x := -w * 0.5 + 0.08
			while x < w * 0.5 - 0.15:
				var gw := c.rng.randf_range(0.1, 0.25)
				var gh := c.rng.randf_range(0.12, 0.32)
				var gc := Color.from_hsv(c.rng.randf(), c.rng.randf_range(0.4, 0.8), c.rng.randf_range(0.5, 0.95))
				c.mb.box_rot("paint", p + bas * Vector3(x + gw * 0.5, y + 0.015 + gh * 0.5, 0.02), Vector3(gw, gh, 0.32), yaw, gc)
				x += gw + 0.03
	if name != "":
		_anchor(c, name, p + bas * Vector3(0, 0, 0.9), p)


static func _kitchen(c: Ctx, p: Vector3, w: float, yaw := 0.0) -> void:
	# Kuechenzeile entlang lokaler x-Achse, Rueckseite an der Wand (lokal -z)
	var bas := Basis(Vector3.UP, yaw)
	_box(c, "paint", p + bas * Vector3(0, 0.45, 0), Vector3(w, 0.9, 0.62), Color(0.92, 0.92, 0.9), true, yaw)
	_box(c, "u_stone", p + bas * Vector3(0, 0.92, 0), Vector3(w, 0.04, 0.64), Color(0.35, 0.33, 0.3), false, yaw)
	# Herd
	var sx := -w * 0.5 + 0.9
	_box(c, "paint_metal", p + bas * Vector3(sx, 0.95, 0.0), Vector3(0.6, 0.02, 0.55), Color(0.08, 0.08, 0.08), false, yaw)
	_anchor(c, "stove", p + bas * Vector3(sx, -p.y, 0.8), p + bas * Vector3(sx, 0, 0))
	# Spuele
	_box(c, "chrome", p + bas * Vector3(0.3, 0.94, 0.0), Vector3(0.5, 0.02, 0.4), Color.WHITE, false, yaw)
	_anchor(c, "sink", p + bas * Vector3(0.3, -p.y, 0.8), p + bas * Vector3(0.3, 0, 0))
	_anchor(c, "counter", p + bas * Vector3(-0.4, -p.y, 0.8), p + bas * Vector3(-0.4, 0, 0))
	# Kuehlschrank
	var fx := w * 0.5 + 0.35
	_box(c, "paint", p + bas * Vector3(fx, 0.95, 0.0), Vector3(0.68, 1.9, 0.65), Color(0.95, 0.95, 0.95), true, yaw)
	_box(c, "chrome", p + bas * Vector3(fx - 0.25, 1.2, 0.34), Vector3(0.03, 0.5, 0.03), Color.WHITE, false, yaw)
	_anchor(c, "fridge", p + bas * Vector3(fx, -p.y, 0.9), p + bas * Vector3(fx, 0, 0))


static func _bathroom(c: Ctx, origin: Vector3, w: float, d: float) -> void:
	# Duschecke, WC und Waschbecken in einem Raum (origin = Ecke x0/z0)
	var tile := Color(0.85, 0.9, 0.92)
	_box(c, "g_plaza", origin + Vector3(w * 0.5, 0.005, d * 0.5), Vector3(w, 0.01, d), Color(0.9, 0.92, 0.95))
	var sh := origin + Vector3(0.55, 0, 0.55)
	_box(c, "paint", sh + Vector3(0, 0.05, 0), Vector3(0.95, 0.1, 0.95), Color(0.96, 0.96, 0.96), true)
	_box(c, "glass_clear", sh + Vector3(0.48, 1.0, 0), Vector3(0.02, 1.9, 0.95), Color.WHITE)
	_box(c, "chrome", origin + Vector3(0.15, 2.0, 0.55), Vector3(0.2, 0.04, 0.2), Color.WHITE)
	_anchor(c, "shower", sh + Vector3(0, 0, 0), sh + Vector3(-1, 0, 0))
	var wc := origin + Vector3(w - 0.45, 0, 0.45)
	_box(c, "paint", wc + Vector3(0, 0.22, 0.05), Vector3(0.4, 0.44, 0.6), Color(0.97, 0.97, 0.97), true)
	_box(c, "paint", wc + Vector3(0, 0.6, -0.22), Vector3(0.45, 0.4, 0.18), Color(0.97, 0.97, 0.97))
	_anchor(c, "toilet", wc + Vector3(0, 0, 0.75), wc)
	var sk := origin + Vector3(w * 0.5, 0, 0.25)
	_box(c, "paint", sk + Vector3(0, 0.82, 0), Vector3(0.6, 0.12, 0.45), Color(0.97, 0.97, 0.97), true)
	_box(c, "chrome", sk + Vector3(0, 1.5, -0.2), Vector3(0.6, 0.7, 0.02), Color.WHITE)
	_anchor(c, "washbasin", sk + Vector3(0, 0, 0.7), sk)


static func _plant(c: Ctx, p: Vector3) -> void:
	c.mb.cylinder("paint", p, 0.2, 0.25, 0.4, 10, Color(0.75, 0.45, 0.3))
	PropBuilder._blob(c.mb, p + Vector3(0, 0.75, 0), Vector3(0.35, 0.45, 0.35), 8, 5, c.rng, Color(0.8, 1.0, 0.8), 0.0)


static func _rug(c: Ctx, p: Vector3, size: Vector2, col: Color) -> void:
	_box(c, "u_fabric", p + Vector3(0, 0.006, 0), Vector3(size.x, 0.012, size.y), col)


# ============================================================ Wohnungen

static func _home(c: Ctx, W: float, D: float, level: int) -> void:
	var H := 2.7 if level < 4 else 3.2
	var walls := [Color(0.93, 0.91, 0.86), Color(0.86, 0.9, 0.92), Color(0.94, 0.88, 0.8), Color(0.9, 0.92, 0.86)]
	var wall: Color = walls[c.rng.randi_range(0, 3)]
	if level >= 4:
		wall = Color(0.96, 0.95, 0.92)
	_shell(c, W, D, H, wall, "u_wood", Color(0.75, 0.58, 0.42) if level < 4 else Color(0.85, 0.82, 0.78), W * 0.75)
	if level == 1:
		# Einzelzimmer in einer WG: Bett, Schreibtisch, Schrank, Mini-Kueche, Bad-Nische
		_bed(c, Vector3(1.0, 0, 1.4), PI * 0.5, 1.0)
		_desk(c, Vector3(W - 1.0, 0, 0.5), 0.0, "desk")
		_box(c, "u_wood", Vector3(W - 0.35, 1.0, 2.4), Vector3(0.6, 2.0, 1.4), Color(0.85, 0.82, 0.76), true)
		_anchor(c, "wardrobe", Vector3(W - 1.2, 0, 2.4), Vector3(W - 0.35, 0, 2.4))
		_kitchen(c, Vector3(1.4, 0, D - 0.4), 1.2, PI)
		_bathroom(c, Vector3(0.0, 0, 2.6), 1.8, 1.0)
		_light(c, Vector3(W * 0.5, H, D * 0.5), 1.0, 7.0)
		_anchor(c, "tv", Vector3(1.0, 0, 3.2), Vector3(1.0, 0, 2.2))
		return
	# Trennwand: Schlafbereich und Bad im Norden
	var split := D * 0.45
	_partition(c, Vector2(0, split), Vector2(W * 0.6, split), H, wall, 0.75, 1.0)
	_partition(c, Vector2(W * 0.6, 0), Vector2(W * 0.6, split), H, wall, 0.7, 0.9)
	# Schlafzimmer (Nordwest)
	_bed(c, Vector3(W * 0.3, 0, 1.15), 0.0, 1.6 if level < 4 else 2.0)
	_box(c, "u_wood", Vector3(0.4, 1.05, split - 0.8), Vector3(0.6, 2.1, 1.4), Color(0.88, 0.85, 0.8), true)
	_anchor(c, "wardrobe", Vector3(1.3, 0, split - 0.8), Vector3(0.4, 0, split - 0.8))
	_box(c, "u_wood", Vector3(W * 0.3 + 1.3, 0.25, 0.35), Vector3(0.45, 0.5, 0.4), Color(0.55, 0.4, 0.28), true)
	_light(c, Vector3(W * 0.3, H, split * 0.5), 0.8, 6.0)
	# Bad (Nordost)
	_bathroom(c, Vector3(W * 0.6 + 0.1, 0, 0.0), W * 0.4 - 0.1, split)
	_light(c, Vector3(W * 0.8, H, split * 0.5), 0.7, 5.0, Color(0.95, 0.97, 1.0))
	# Wohnbereich (Sueden): Sofa, TV, Couchtisch, Esstisch, Kueche
	var ly := split + (D - split) * 0.5
	_sofa(c, Vector3(W * 0.28, 0, ly + 0.6), PI, 2.2 + level * 0.2)
	_rug(c, Vector3(W * 0.28, 0, ly - 0.3), Vector2(2.6, 1.8), [Color(0.6, 0.3, 0.25), Color(0.3, 0.35, 0.5), Color(0.75, 0.7, 0.6)][c.rng.randi_range(0, 2)])
	_table(c, Vector3(W * 0.28, 0, ly - 0.3), Vector2(1.0, 0.6), 0.42, Color(0.35, 0.28, 0.22))
	var tv_p := Vector3(W * 0.28, 0, split + 0.35)
	_box(c, "u_wood", tv_p + Vector3(0, 0.25, 0), Vector3(1.8, 0.5, 0.45), Color(0.3, 0.25, 0.2), true)
	_box(c, "paint_metal", tv_p + Vector3(0, 1.05, -0.05), Vector3(1.3, 0.75, 0.05), Color(0.05, 0.05, 0.06))
	_box(c, "window_glow", tv_p + Vector3(0, 1.05, -0.02), Vector3(1.24, 0.69, 0.01), Color(0.3, 0.45, 0.7))
	_anchor(c, "tv", Vector3(W * 0.28, 0, ly + 0.6), tv_p)
	_kitchen(c, Vector3(W * 0.62, 0, split + 0.45), minf(3.0, W * 0.3), 0.0)
	_table(c, Vector3(W * 0.68, 0, D - 1.6), Vector2(1.4, 0.9))
	_anchor(c, "dining", Vector3(W * 0.68, 0, D - 0.8), Vector3(W * 0.68, 0, D - 1.6))
	for k in 4:
		var off := Vector3(-0.45 + (k % 2) * 0.9, 0, -0.75 if k < 2 else 0.75)
		_chair(c, Vector3(W * 0.68, 0, D - 1.6) + off, 0.0 if k >= 2 else PI)
	_plant(c, Vector3(0.5, 0, D - 0.5))
	_light(c, Vector3(W * 0.28, H, ly), 1.1, 8.0)
	_light(c, Vector3(W * 0.7, H, ly), 1.0, 8.0)
	if level >= 3:
		_desk(c, Vector3(0.9, 0, ly + 0.2), PI * 0.5, "desk")
		_shelf(c, Vector3(0.3, 0, ly - 1.6), PI * 0.5, 1.6, 2.0, true, "bookshelf")
	if level >= 4:
		# Fluegel und zweites Sofa im Villenwohnzimmer
		_box(c, "paint", Vector3(W * 0.48, 0.5, ly + 1.0), Vector3(1.5, 0.3, 1.8), Color(0.05, 0.05, 0.06), true)
		_box(c, "paint", Vector3(W * 0.48, 0.25, ly + 1.0), Vector3(0.1, 0.5, 0.1), Color(0.05, 0.05, 0.06))
		_anchor(c, "piano", Vector3(W * 0.48, 0, ly + 2.1), Vector3(W * 0.48, 0, ly + 1.0))
		_sofa(c, Vector3(W * 0.12, 0, ly - 0.3), PI * 0.5, 2.0, "sofa_2")
		_light(c, Vector3(W * 0.48, H, ly + 1.0), 1.0, 8.0)
	if level >= 5:
		_box(c, "pool_water", Vector3(W * 0.85, 0.02, D - 2.0), Vector3(2.4, 0.02, 1.6), Color.WHITE)
		_plant(c, Vector3(W - 0.5, 0, D - 0.5))


static func _shelter(c: Ctx) -> void:
	var W := 14.0
	var D := 10.0
	_shell(c, W, D, 3.0, Color(0.86, 0.86, 0.82), "u_concrete", Color(0.7, 0.7, 0.68), W * 0.5)
	for k in 6:
		var x := 1.2 + k * 2.1
		var p := Vector3(x, 0, 1.3)
		_box(c, "paint_metal", p + Vector3(0, 0.4, 0), Vector3(0.95, 0.06, 2.0), Color(0.3, 0.35, 0.4), true)
		_box(c, "paint_rough", p + Vector3(0, 0.5, 0), Vector3(0.9, 0.14, 1.9), Color(0.6, 0.65, 0.7))
		_box(c, "paint_metal", p + Vector3(0, 1.5, 0), Vector3(0.95, 0.06, 2.0), Color(0.3, 0.35, 0.4))
		_box(c, "paint_rough", p + Vector3(0, 1.6, 0), Vector3(0.9, 0.14, 1.9), Color(0.55, 0.6, 0.5))
		for sx in [-1.0, 1.0]:
			for sz in [-1.0, 1.0]:
				_box(c, "paint_metal", p + Vector3(sx * 0.45, 0.95, sz * 0.95), Vector3(0.05, 1.9, 0.05), Color(0.3, 0.35, 0.4))
		_anchor(c, c.next("bed"), p + Vector3(0, 0, 1.4), p)
		if k == 0:
			c.root.anchors["bed"] = c.root.anchors["bed_0"]
	_table(c, Vector3(4.0, 0, 6.5), Vector2(3.0, 1.0))
	for k in 6:
		_chair(c, Vector3(2.9 + (k % 3) * 1.1, 0, 5.8 if k < 3 else 7.2), 0.0 if k >= 3 else PI)
		_anchor(c, c.next("seat"), Vector3(2.9 + (k % 3) * 1.1, 0, 5.6 if k < 3 else 7.4), Vector3(2.9 + (k % 3) * 1.1, 0, 6.5))
	_counter(c, Vector3(11.0, 0, 6.0), 0.0, 3.0, Color(0.8, 0.8, 0.78), "counter")
	_bathroom(c, Vector3(W - 3.0, 0, 0.0), 3.0, 2.6)
	for k in 3:
		_light(c, Vector3(2.0 + k * 5.0, 3.0, 5.0), 1.0, 9.0, Color(0.95, 0.97, 1.0))


# ============================================================ Gewerbe

static func _shop(c: Ctx, bt: String) -> void:
	if bt in ["restaurant", "cafe", "bar", "fastfood"]:
		_restaurant(c)
		return
	var W := 10.0
	var D := 9.0
	var wall := Color(0.95, 0.95, 0.93)
	if bt in ["luxury", "clothing"]:
		wall = Color(0.92, 0.9, 0.86)
	_shell(c, W, D, 3.4, wall, "g_plaza" if bt != "hardware" else "u_concrete", Color(0.9, 0.88, 0.85), W * 0.5)
	_counter(c, Vector3(W - 2.0, 0, D - 2.6), 0.0, 2.4, Color(0.3, 0.3, 0.32), "register")
	c.root.anchors["counter"] = c.root.anchors["register"]
	_box(c, "paint_metal", Vector3(W - 2.4, 1.15, D - 2.6), Vector3(0.35, 0.25, 0.3), Color(0.15, 0.15, 0.16))
	_anchor(c, "clerk", Vector3(W - 2.0, 0, D - 3.4), Vector3(W - 2.0, 0, D - 2.6))
	if bt in ["clothing", "luxury", "secondhand"]:
		for k in 4:
			var p := Vector3(1.5 + k * 2.0, 0, 3.0 + (k % 2) * 2.0)
			_box(c, "chrome", p + Vector3(0, 1.4, 0), Vector3(1.4, 0.04, 0.04), Color.WHITE, true)
			for j in 9:
				var gc := Color.from_hsv(c.rng.randf(), 0.5, c.rng.randf_range(0.4, 0.9))
				_box(c, "u_fabric", p + Vector3(-0.6 + j * 0.15, 0.95, 0), Vector3(0.03, 0.8, 0.45), gc)
			_anchor(c, c.next("shelf"), p + Vector3(0, 0, 0.8), p)
		_box(c, "chrome", Vector3(0.3, 1.2, 1.0), Vector3(0.05, 1.8, 1.0), Color.WHITE)
	else:
		for k in 3:
			_shelf(c, Vector3(1.5 + k * 2.4, 0, 0.45), 0.0, 2.2, 2.0, true, c.next("shelf"))
		for k in 2:
			_shelf(c, Vector3(2.5 + k * 3.0, 0, 3.6), PI, 2.2, 1.6, true, c.next("shelf"))
	_light(c, Vector3(W * 0.3, 3.4, D * 0.4), 1.3, 9.0, Color(1, 0.97, 0.9))
	_light(c, Vector3(W * 0.7, 3.4, D * 0.6), 1.3, 9.0, Color(1, 0.97, 0.9))


static func _supermarket(c: Ctx) -> void:
	var W := 30.0
	var D := 22.0
	_shell(c, W, D, 5.0, Color(0.94, 0.94, 0.92), "g_plaza", Color(0.92, 0.92, 0.9), W * 0.8)
	for row in 5:
		for k in 3:
			var p := Vector3(3.0 + k * 7.0, 0, 3.0 + row * 2.8)
			_shelf(c, p, 0.0, 6.0, 2.0, true, c.next("shelf"))
			_shelf(c, p + Vector3(0, 0, -0.55), PI, 6.0, 2.0, true, "")
	# Kuehlregale an der Ostwand
	for k in 4:
		var p2 := Vector3(W - 0.6, 0, 2.0 + k * 3.2)
		_box(c, "paint", p2 + Vector3(0, 1.0, 0), Vector3(0.9, 2.0, 3.0), Color(0.92, 0.92, 0.94), true)
		_box(c, "glass", p2 + Vector3(-0.46, 1.1, 0), Vector3(0.02, 1.6, 2.8), Color.WHITE)
		_box(c, "window_glow", p2 + Vector3(-0.4, 1.1, 0), Vector3(0.02, 1.5, 2.7), Color(0.75, 0.85, 1.0))
		_anchor(c, c.next("fridge"), p2 + Vector3(-1.3, -p2.y, 0), p2)
	c.root.anchors["fridge"] = c.root.anchors["fridge_0"]
	for k in 4:
		var cp := Vector3(4.0 + k * 3.5, 0, D - 4.0)
		_counter(c, cp, 0.0, 2.2, Color(0.25, 0.3, 0.35), c.next("register"))
		_box(c, "paint_metal", cp + Vector3(0.6, 1.15, 0), Vector3(0.35, 0.25, 0.3), Color(0.15, 0.15, 0.16))
	c.root.anchors["register"] = c.root.anchors["register_0"]
	c.root.anchors["counter"] = c.root.anchors["register_0"]
	for k in 6:
		_light(c, Vector3(5.0 + (k % 3) * 10.0, 5.0, 6.0 + (k / 3) * 10.0), 2.0, 14.0, Color(1, 0.98, 0.94))


static func _restaurant(c: Ctx) -> void:
	var W := 14.0
	var D := 11.0
	_shell(c, W, D, 3.2, Color(0.78, 0.62, 0.48), "u_wood", Color(0.5, 0.36, 0.25), W * 0.5)
	_partition(c, Vector2(0, 3.2), Vector2(W * 0.45, 3.2), 3.2, Color(0.9, 0.9, 0.88), 0.8, 1.0)
	_kitchen(c, Vector3(2.5, 0, 0.4), 3.0, 0.0)
	_counter(c, Vector3(W - 3.0, 0, 2.0), 0.0, 4.0, Color(0.35, 0.22, 0.15), "counter")
	c.root.anchors["register"] = c.root.anchors["counter"]
	_shelf(c, Vector3(W - 3.0, 0, 0.35), 0.0, 4.0, 1.8, true, "bar_shelf")
	var n := 0
	for row in 2:
		for k in 3:
			var tp := Vector3(2.5 + k * 3.6, 0, 5.5 + row * 3.0)
			_table(c, tp, Vector2(1.0, 1.0), 0.75, Color(0.4, 0.28, 0.2))
			c.mb.box("paint", tp + Vector3(0, 0.8, 0), Vector3(0.4, 0.02, 0.4), Color(0.95, 0.95, 0.93))
			for s in [-1.0, 1.0]:
				_chair(c, tp + Vector3(0, 0, s * 0.75), 0.0 if s > 0 else PI)
				_anchor(c, "seat_%d" % n, tp + Vector3(0, 0, s * 0.75), tp)
				n += 1
			_light(c, tp + Vector3(0, 3.2, 0), 0.7, 5.0, Color(1.0, 0.8, 0.55))
	_plant(c, Vector3(0.5, 0, D - 0.5))


static func _market_hall(c: Ctx) -> void:
	var W := 30.0
	var D := 20.0
	_shell(c, W, D, 7.0, Color(0.8, 0.6, 0.5), "g_plaza", Color(0.8, 0.75, 0.7), W * 0.5)
	var cols := BuildingBuilder.STALL_COLORS
	for k in 8:
		var p := Vector3(4.0 + (k % 4) * 7.0, 0, 4.0 + (k / 4) * 8.0)
		var col: Color = cols[k % cols.size()]
		_counter(c, p, 0.0, 4.0, Color(0.55, 0.4, 0.28), c.next("counter"))
		for j in 8:
			_box(c, "paint_rough", p + Vector3(-1.75 + j * 0.5, 1.12, 0), Vector3(0.42, 0.18, 0.5), Color.from_hsv(c.rng.randf(), 0.7, 0.85))
		for sx in [-1.0, 1.0]:
			_box(c, "paint_metal", p + Vector3(sx * 2.0, 1.3, -0.3), Vector3(0.06, 2.6, 0.06), Color(0.3, 0.3, 0.3))
		_box(c, "u_fabric", p + Vector3(0, 2.6, -0.1), Vector3(4.4, 0.05, 1.4), col)
		_anchor(c, c.next("shelf"), p + Vector3(0, 0, 0.9), p)
	c.root.anchors["counter"] = c.root.anchors["counter_0"]
	c.root.anchors["register"] = c.root.anchors["counter_0"]
	for k in 6:
		_light(c, Vector3(5.0 + (k % 3) * 10.0, 7.0, 5.0 + (k / 3) * 10.0), 2.2, 15.0)


static func _office(c: Ctx) -> void:
	var W := 16.0
	var D := 12.0
	_shell(c, W, D, 3.0, Color(0.9, 0.91, 0.92), "u_concrete", Color(0.55, 0.57, 0.6), W * 0.5)
	var n := 0
	for row in 2:
		for k in 4:
			var p := Vector3(2.0 + k * 3.3, 0, 1.4 + row * 3.4)
			_desk(c, p, 0.0, "work_%d" % n)
			n += 1
	_table(c, Vector3(W - 3.0, 0, D - 3.0), Vector2(3.0, 1.4), 0.75, Color(0.9, 0.9, 0.88))
	for k in 6:
		_chair(c, Vector3(W - 4.0 + (k % 3) * 1.0, 0, D - 3.9 if k < 3 else D - 2.1), 0.0 if k >= 3 else PI, Color(0.2, 0.2, 0.22))
	_anchor(c, "meeting", Vector3(W - 3.0, 0, D - 1.6), Vector3(W - 3.0, 0, D - 3.0))
	_kitchen(c, Vector3(1.8, 0, D - 0.4), 1.6, PI)
	c.root.anchors["desk"] = c.root.anchors["work_0"]
	_plant(c, Vector3(W - 0.5, 0, 0.5))
	for k in 4:
		_light(c, Vector3(3.0 + k * 3.5, 3.0, 4.0), 1.1, 8.0, Color(0.97, 0.98, 1.0))


static func _school(c: Ctx, uni: bool) -> void:
	var W := 12.0 if not uni else 18.0
	var D := 10.0 if not uni else 14.0
	_shell(c, W, D, 3.4 if not uni else 5.0, Color(0.95, 0.92, 0.82), "u_wood", Color(0.7, 0.55, 0.4), W * 0.85)
	# Tafel an der Nordwand, Lehrerpult
	_box(c, "paint", Vector3(W * 0.5, 1.6, 0.06), Vector3(4.5, 1.3, 0.06), Color(0.12, 0.22, 0.18))
	_desk(c, Vector3(W * 0.5 + 3.0, 0, 1.4), PI, "work_0", false)
	_anchor(c, "board", Vector3(W * 0.5, 0, 1.6), Vector3(W * 0.5, 0, 0.0))
	var n := 0
	var rows := 4 if not uni else 6
	var cols := 4 if not uni else 6
	for r in rows:
		for k in cols:
			var p := Vector3(W * 0.5 - (cols - 1) * 1.3 * 0.5 + k * 1.3, 0, 3.4 + r * 1.35 * (1.0 if not uni else 1.4))
			if uni:
				_box(c, "u_wood", p + Vector3(0, 0.1 + r * 0.25, 0.3), Vector3(1.3, 0.2 + r * 0.5, 1.6), Color(0.6, 0.45, 0.32), true)
				p.y = 0.2 + r * 0.5
			_box(c, "u_wood", p + Vector3(0, 0.72, -0.2), Vector3(1.1, 0.04, 0.5), Color(0.75, 0.6, 0.42), true)
			_chair(c, p + Vector3(0, 0, 0.35), PI, Color(0.3, 0.4, 0.6))
			_anchor(c, "seat_%d" % n, p + Vector3(0, 0, 0.35), p + Vector3(0, 0, -1.0))
			n += 1
	c.root.anchors["desk"] = c.root.anchors["seat_0"]
	for k in 4:
		_light(c, Vector3(W * (0.25 + 0.5 * (k % 2)), 3.4 if not uni else 5.0, D * (0.3 + 0.4 * (k / 2))), 1.2, 9.0, Color(0.97, 0.98, 1.0))


static func _hospital(c: Ctx, big: bool) -> void:
	var W := 16.0 if big else 10.0
	var D := 12.0 if big else 9.0
	_shell(c, W, D, 3.0, Color(0.94, 0.96, 0.97), "g_plaza", Color(0.85, 0.9, 0.9), W * 0.5)
	_counter(c, Vector3(W * 0.5, 0, D - 3.0), 0.0, 3.0, Color(0.85, 0.9, 0.95), "counter")
	c.root.anchors["reception"] = c.root.anchors["counter"]
	_desk(c, Vector3(W * 0.5, 0, D - 4.4), PI, "work_0")
	var nb := 4 if big else 1
	for k in nb:
		var p := Vector3(1.6 + k * 3.6, 0, 1.4)
		_box(c, "paint_metal", p + Vector3(0, 0.35, 0), Vector3(1.0, 0.1, 2.1), Color(0.8, 0.82, 0.85), true)
		_box(c, "paint_rough", p + Vector3(0, 0.5, 0.05), Vector3(0.95, 0.16, 1.95), Color(0.95, 0.97, 1.0))
		for sx in [-1.0, 1.0]:
			_box(c, "paint_metal", p + Vector3(sx * 0.45, 0.2, 0), Vector3(0.04, 0.4, 2.0), Color(0.6, 0.62, 0.65))
		_box(c, "u_fabric", p + Vector3(1.4, 1.1, 0.2), Vector3(0.02, 2.0, 2.4), Color(0.6, 0.78, 0.82))
		_box(c, "paint", p + Vector3(-0.9, 0.7, -0.8), Vector3(0.4, 1.4, 0.4), Color(0.85, 0.87, 0.9))
		_box(c, "window_glow", p + Vector3(-0.9, 1.25, -0.59), Vector3(0.3, 0.2, 0.01), Color(0.3, 0.9, 0.5))
		_anchor(c, "bed_%d" % k, p + Vector3(0.9, 0, 0.6), p)
	c.root.anchors["bed"] = c.root.anchors["bed_0"]
	for k in 4:
		_chair(c, Vector3(1.0 + k * 0.7, 0, D - 1.0), PI, Color(0.3, 0.5, 0.6))
		_anchor(c, "seat_%d" % k, Vector3(1.0 + k * 0.7, 0, D - 1.0), Vector3(1.0 + k * 0.7, 0, D - 3.0))
	_bathroom(c, Vector3(W - 2.6, 0, D - 2.6), 2.6, 2.6)
	for k in 4:
		_light(c, Vector3(W * (0.25 + 0.5 * (k % 2)), 3.0, D * (0.3 + 0.4 * (k / 2))), 1.2, 9.0, Color(0.97, 0.99, 1.0))


static func _police(c: Ctx) -> void:
	var W := 14.0
	var D := 11.0
	_shell(c, W, D, 3.0, Color(0.88, 0.9, 0.93), "u_concrete", Color(0.5, 0.52, 0.56), W * 0.5)
	_counter(c, Vector3(W * 0.5, 0, D - 3.2), 0.0, 4.0, Color(0.15, 0.3, 0.55), "counter")
	c.root.anchors["reception"] = c.root.anchors["counter"]
	for k in 4:
		_desk(c, Vector3(2.0 + k * 3.0, 0, 1.4), 0.0, "work_%d" % k)
	c.root.anchors["desk"] = c.root.anchors["work_0"]
	# Zelle mit Gitter (Suedwest)
	var cell := Vector3(1.8, 0, D - 2.2)
	for k in 12:
		_box(c, "paint_metal", Vector3(0.2 + k * 0.28, 1.4, D - 3.9), Vector3(0.04, 2.8, 0.04), Color(0.25, 0.25, 0.27))
	var cs := CollisionShape3D.new()
	var bs := BoxShape3D.new()
	bs.size = Vector3(3.4, 2.8, 0.1)
	cs.shape = bs
	cs.position = Vector3(1.8, 1.4, D - 3.9)
	c.body.add_child(cs)
	_box(c, "paint_metal", cell + Vector3(-0.8, 0.4, 0.6), Vector3(0.8, 0.1, 1.9), Color(0.4, 0.4, 0.42), true)
	_anchor(c, "cell", cell, cell + Vector3(-0.8, 0, 0.6))
	for k in 4:
		_light(c, Vector3(W * (0.25 + 0.5 * (k % 2)), 3.0, D * (0.3 + 0.4 * (k / 2))), 1.1, 9.0, Color(0.97, 0.99, 1.0))


static func _city_hall(c: Ctx) -> void:
	var W := 20.0
	var D := 14.0
	_shell(c, W, D, 5.5, Color(0.92, 0.88, 0.8), "u_stone", Color(0.85, 0.82, 0.76), W * 0.5)
	for k in 5:
		var p := Vector3(2.5 + k * 3.8, 0, 2.2)
		_counter(c, p, 0.0, 2.6, Color(0.55, 0.4, 0.28), "counter_%d" % k)
		_box(c, "glass_clear", p + Vector3(0, 1.6, -0.1), Vector3(2.4, 1.0, 0.02), Color.WHITE)
		_desk(c, p + Vector3(0, 0, -1.3), 0.0, "work_%d" % k)
	c.root.anchors["counter"] = c.root.anchors["counter_0"]
	for r in 3:
		for k in 6:
			var sp := Vector3(5.0 + k * 1.8, 0, 7.0 + r * 1.6)
			_chair(c, sp, PI, Color(0.3, 0.35, 0.45))
			_anchor(c, "seat_%d" % (r * 6 + k), sp, sp + Vector3(0, 0, -2.0))
	_box(c, "u_fabric", Vector3(W * 0.5, 2.5, 0.05), Vector3(2.0, 3.0, 0.02), Color(0.15, 0.35, 0.65))
	for k in 6:
		_light(c, Vector3(4.0 + (k % 3) * 6.0, 5.5, 4.0 + (k / 3) * 6.0), 1.6, 12.0)


static func _fire_station(c: Ctx) -> void:
	var W := 18.0
	var D := 14.0
	_shell(c, W, D, 6.0, Color(0.88, 0.86, 0.82), "u_concrete", Color(0.55, 0.55, 0.55), W * 0.85)
	# Loeschfahrzeug
	var t := Vector3(5.0, 0, 6.0)
	_box(c, "paint", t + Vector3(0, 1.6, 0), Vector3(2.5, 2.4, 8.5), Color(0.78, 0.1, 0.08), true)
	_box(c, "glass", t + Vector3(0, 2.1, 4.26), Vector3(2.2, 0.9, 0.02), Color.WHITE)
	for z in [-2.8, 2.8]:
		for sx in [-1.0, 1.0]:
			c.mb.cylinder("paint_rough", t + Vector3(sx * 1.25, 0.5, z), 0.5, 0.5, 0.35, 12, Color(0.08, 0.08, 0.08))
	_box(c, "neon", t + Vector3(0, 2.9, 3.5), Vector3(1.6, 0.15, 0.3), Color(0.2, 0.4, 1.0))
	_anchor(c, "truck", t + Vector3(1.9, 0, 3.0), t)
	# Spinde und Arbeitsplaetze
	for k in 8:
		var lp := Vector3(W - 0.4, 0, 1.0 + k * 0.7)
		_box(c, "paint_metal", lp + Vector3(0, 0.95, 0), Vector3(0.6, 1.9, 0.65), Color(0.6, 0.62, 0.66), true)
	_anchor(c, "locker", Vector3(W - 1.5, 0, 3.5), Vector3(W - 0.4, 0, 3.5))
	for k in 3:
		_desk(c, Vector3(10.0 + k * 2.0, 0, 1.3), 0.0, "work_%d" % k)
	for k in 6:
		_light(c, Vector3(3.0 + (k % 3) * 6.0, 6.0, 4.0 + (k / 3) * 6.0), 1.8, 13.0, Color(0.97, 0.99, 1.0))


static func _factory(c: Ctx) -> void:
	var W := 26.0
	var D := 18.0
	_shell(c, W, D, 8.0, Color(0.75, 0.76, 0.74), "u_concrete", Color(0.5, 0.5, 0.48), W * 0.5)
	# Foerderband
	_box(c, "paint_metal", Vector3(W * 0.5, 0.9, D * 0.5), Vector3(W - 6.0, 0.2, 1.2), Color(0.2, 0.2, 0.22), true)
	_box(c, "paint_rough", Vector3(W * 0.5, 1.01, D * 0.5), Vector3(W - 6.2, 0.02, 1.0), Color(0.08, 0.08, 0.08))
	for k in 5:
		var mp := Vector3(4.0 + k * 4.5, 0, D * 0.5 - 3.0)
		_box(c, "paint_metal", mp + Vector3(0, 1.2, 0), Vector3(2.4, 2.4, 2.0), [Color(0.2, 0.45, 0.65), Color(0.85, 0.6, 0.15), Color(0.3, 0.55, 0.35)][k % 3], true)
		_box(c, "window_glow", mp + Vector3(0.6, 1.6, 1.01), Vector3(0.6, 0.4, 0.01), Color(0.3, 0.8, 0.5))
		_anchor(c, "work_%d" % k, mp + Vector3(0, 0, 1.8), mp)
		c.mb.box("paint_rough", mp + Vector3(0, 1.03, 3.0), Vector3(0.6, 0.06, 0.4), Color(0.6, 0.5, 0.3))
	c.root.anchors["desk"] = c.root.anchors["work_0"]
	# Gelbe Sicherheitslinien
	_box(c, "paint", Vector3(W * 0.5, 0.005, D * 0.5 + 1.2), Vector3(W - 4.0, 0.01, 0.12), Color(0.95, 0.8, 0.1))
	_box(c, "paint", Vector3(W * 0.5, 0.005, D * 0.5 - 4.5), Vector3(W - 4.0, 0.01, 0.12), Color(0.95, 0.8, 0.1))
	for k in 6:
		_light(c, Vector3(5.0 + (k % 3) * 8.0, 8.0, 4.0 + (k / 3) * 9.0), 2.5, 16.0, Color(0.95, 0.97, 1.0))


static func _warehouse(c: Ctx) -> void:
	var W := 28.0
	var D := 20.0
	_shell(c, W, D, 9.0, Color(0.78, 0.78, 0.76), "u_concrete", Color(0.52, 0.52, 0.5), W * 0.5)
	for row in 4:
		for k in 3:
			var p := Vector3(4.0 + k * 8.5, 0, 2.0 + row * 4.0)
			# Schwerlastregal mit Paletten
			for sx in [-1.0, 1.0]:
				_box(c, "paint_metal", p + Vector3(sx * 3.4, 2.5, 0), Vector3(0.12, 5.0, 1.2), Color(0.15, 0.35, 0.65), true)
			for lv in 3:
				_box(c, "paint_metal", p + Vector3(0, 0.1 + lv * 1.7, 0), Vector3(6.8, 0.1, 1.2), Color(0.9, 0.5, 0.1))
				for j in 4:
					_box(c, "u_wood", p + Vector3(-2.5 + j * 1.65, 0.2 + lv * 1.7, 0), Vector3(1.2, 0.12, 1.0), Color(0.72, 0.6, 0.42))
					_box(c, "paint_rough", p + Vector3(-2.5 + j * 1.65, 0.75 + lv * 1.7, 0), Vector3(1.1, 1.0, 0.9), Color(0.75, 0.66, 0.5))
			_anchor(c, c.next("shelf"), p + Vector3(0, 0, 1.6), p)
	var f := Vector3(W - 4.0, 0, D - 4.0)
	_box(c, "paint", f + Vector3(0, 0.9, 0), Vector3(1.2, 1.2, 2.2), Color(0.95, 0.75, 0.1), true)
	_box(c, "paint_metal", f + Vector3(0, 1.6, 1.3), Vector3(0.8, 2.6, 0.1), Color(0.2, 0.2, 0.2))
	_anchor(c, "work_0", f + Vector3(1.2, 0, 0), f)
	c.root.anchors["desk"] = c.root.anchors["work_0"]
	for k in 6:
		_light(c, Vector3(5.0 + (k % 3) * 9.0, 9.0, 4.0 + (k / 3) * 10.0), 2.8, 18.0, Color(0.95, 0.97, 1.0))
