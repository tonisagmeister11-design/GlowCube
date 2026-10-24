class_name BuildingBuilder
extends RefCounted
## Erzeugt die Geometrie eines Gebaeudes aus den Stadtdaten (reine Daten,
## thread-sicher). Fassaden nutzen den Fassaden-Shader (prozedurale Fenster),
## dazu kommen echte Bauteile: Sockel, Gesimse, Tueren, Vordaecher,
## Schaufenster-Schilder, Markisen, Balkone, Dachformen und Dachaufbauten,
## Sonderbauten (Rathaus mit Turm, Regierungssitz mit Kuppel, Bahnhof mit
## Gewoelbe, Feuerwache mit Toren, Tankstelle mit Dach, Marktstaende, Zelte).
##
## Ausgabe:
##   mb   - Hauptgeometrie (immer sichtbar, solange der Chunk geladen ist)
##   det  - Detailgeometrie (nur in der Naehe sichtbar)
##   out  - {"labels": [...], "shapes": [...], "lights": [...]}
##
## Lagen: Grundstueck/Gehweg liegen bei y = 0.15; Waende beginnen bei y = -0.3.

const GROUND := 0.15
const SIDE_NAMES := ["s", "e", "n", "w"]

const FACADE_KEYS := {
	"plaster": "fac_plaster", "brick": "fac_brick", "stone": "fac_stone", "concrete": "fac_concrete",
	"glass": "fac_glass", "metal": "fac_metal", "wood": "fac_wood", "steel": "fac_steel",
}

## Glastoenungen fuer Glasfassaden (blau, petrol, gruen, bronze, silber, anthrazit)
const GLASS_TINTS := [Color(0.45, 0.62, 0.80), Color(0.38, 0.62, 0.66), Color(0.50, 0.66, 0.58),
	Color(0.70, 0.58, 0.44), Color(0.72, 0.76, 0.80), Color(0.34, 0.38, 0.44)]
const STALL_COLORS := [Color(0.86, 0.22, 0.20), Color(0.95, 0.75, 0.15), Color(0.20, 0.55, 0.85),
	Color(0.25, 0.65, 0.35), Color(0.95, 0.95, 0.92), Color(0.75, 0.35, 0.65)]
const TENT_COLORS := [Color(0.25, 0.42, 0.30), Color(0.30, 0.40, 0.62), Color(0.62, 0.40, 0.22),
	Color(0.55, 0.55, 0.52), Color(0.70, 0.30, 0.25)]
const AWNING_TYPES := ["cafe", "restaurant", "bakery", "produce", "butcher", "grocery", "fastfood", "bar", "luxury"]

const FLAG_STYLE := 1
const MODE_WALL := 0
const MODE_WINDOWS := 1
const MODE_SHOP := 2
const MODE_CURTAIN := 3
const MODE_HALL := 4
const MODE_BOARDED := 5
const MODE_GLASS := 6


static func make_rng(city: CityModel, b: Dictionary) -> RandomNumberGenerator:
	var r := RandomNumberGenerator.new()
	r.seed = hash("bld:%d:%d" % [b.id, city.seed])
	return r


static func flags(style: int, mode: int, ikind: int) -> float:
	return float(style + 8 * mode + 64 * ikind)


static func cus(fh: float, bay: float, seed: float, fl: float) -> Color:
	return Color(fh, bay, seed, fl)


# ============================================================ Profil

## Leitet alle Darstellungsparameter eines Gebaeudes ab (deterministisch).
static func profile(city: CityModel, b: Dictionary, rng: RandomNumberGenerator) -> Dictionary:
	var st: Dictionary = b.style
	var kind: String = b.kind
	var facade: String = st.get("facade", "plaster")
	var win: int = int(st.get("window", 0))
	var p := {
		"fac": FACADE_KEYS.get(facade, "fac_plaster"), "facade": facade,
		"tint": st.get("color", Color(0.85, 0.82, 0.76)), "cond": float(st.get("condition", 0.6)),
		"trim": st.get("trim", Color(0.6, 0.58, 0.55)), "roof": str(st.get("roof", "flat")),
		"roof_col": st.get("roof_color", Color(0.4, 0.25, 0.2)),
		"bay": 3.0, "wstyle": 0, "mode": MODE_WINDOWS, "ikind": 0, "ground_mode": MODE_WINDOWS,
		"seed": rng.randf(), "storefront": false, "plinth": true, "cornice": false, "string": false,
	}
	var fh: float = b.floor_h
	match kind:
		"house", "villa", "estate":
			p.bay = rng.randf_range(2.7, 3.3)
			p.wstyle = [0, 2, 1, 7][win]
		"townhouse":
			p.bay = rng.randf_range(2.3, 2.7)
			p.wstyle = [0, 2, 0, 1][win]
			p.string = true
		"apartment":
			p.bay = rng.randf_range(3.0, 3.5)
			p.wstyle = [0, 1, 2, 6][win]
			p.cornice = facade != "concrete"
		"mixed":
			p.bay = rng.randf_range(2.8, 3.4)
			p.wstyle = [0, 1, 2, 6][win]
			p.cornice = true
			p.string = true
		"tenement":
			p.bay = rng.randf_range(2.5, 2.9)
			p.wstyle = [5, 0, 2, 0][win]
			p.cornice = true
			p.string = true
		"highrise_res":
			p.bay = rng.randf_range(3.0, 3.6)
			p.wstyle = [1, 6, 3, 1][win]
		"office":
			p.bay = rng.randf_range(2.4, 3.0)
			p.wstyle = [3, 1, 6, 3][win]
			p.ikind = 1
		"tower":
			p.bay = rng.randf_range(1.5, 1.9)
			p.wstyle = [3, 6, 3, 1][win]
			p.ikind = 1
		"shop", "restaurant":
			p.bay = rng.randf_range(2.6, 3.2)
			p.wstyle = [6, 1, 0, 6][win]
		"supermarket", "gas_station":
			p.bay = 4.0
			p.mode = MODE_WALL
			p.plinth = false
		"market_hall":
			p.bay = rng.randf_range(4.5, 5.5)
			p.wstyle = 4
			p.ikind = 2
			p.cornice = true
		"warehouse", "factory", "bus_depot":
			p.bay = rng.randf_range(5.0, 6.5)
			p.mode = MODE_HALL
			p.ikind = 1
			p.plinth = false
		"school", "clinic", "shelter", "police", "fire_station":
			p.bay = rng.randf_range(2.8, 3.4)
			p.wstyle = [1, 6, 0, 1][win]
			p.ikind = 1
			p.string = kind == "school"
		"hospital":
			p.bay = 3.0
			p.wstyle = [3, 1, 3, 6][win]
			p.ikind = 1
		"university", "city_hall", "parliament", "station":
			p.bay = rng.randf_range(3.6, 4.4)
			p.wstyle = 4 if kind != "university" or win < 2 else 2
			p.ikind = 1
			p.cornice = true
			p.string = true
		"abandoned":
			p.bay = 2.8
			p.mode = MODE_BOARDED
			p.cond = minf(p.cond, 0.15)
	if facade == "glass" and kind in ["office", "tower", "villa"]:
		p.mode = MODE_CURTAIN if (kind != "villa" and win != 1) else MODE_GLASS
		p.bay = rng.randf_range(1.4, 1.9) if kind != "villa" else 2.4
		p.tint = GLASS_TINTS[rng.randi_range(0, GLASS_TINTS.size() - 1)]
		p.plinth = false
	elif facade == "glass":
		# Glas fuer Bauarten ohne Glasfassade -> Beton
		p.fac = "fac_concrete"
	if facade == "steel" and kind == "tower":
		p.wstyle = 3
	if b.business_type != "" and kind in ["mixed", "shop", "restaurant", "supermarket", "gas_station", "market_hall"]:
		p.storefront = true
		p.ground_mode = MODE_SHOP
	else:
		p.ground_mode = p.mode
	if style_balconies(b):
		p.wstyle = 2
	if fh >= 5.5 and p.mode == MODE_WINDOWS and p.wstyle != 4:
		# hohe Hallengeschosse: hohe Fenster
		p.wstyle = 2
	return p


static func style_balconies(b: Dictionary) -> bool:
	return bool(b.style.get("balconies", false)) and b.kind in ["apartment", "highrise_res", "mixed"]


static func ikind_for_business(bt: String) -> int:
	if bt in ["restaurant", "cafe", "bar", "fastfood"]:
		return 3
	if bt in ["bank", "real_estate", "job_center"]:
		return 1
	return 2


# ============================================================ Hilfen

static func rect_corners(r: Rect2) -> Array:
	# A(x0,z1) B(x1,z1) C(x1,z0) D(x0,z0): gegen den Uhrzeigersinn von oben,
	# Seiten s, e, n, w mit Aussennormalen +z, +x, -z, -x.
	return [Vector2(r.position.x, r.end.y), Vector2(r.end.x, r.end.y),
		Vector2(r.end.x, r.position.y), Vector2(r.position.x, r.position.y)]


static func side_index(front: String) -> int:
	return maxi(0, SIDE_NAMES.find(front))


static func side_normal(i: int) -> Vector2:
	return [Vector2(0, 1), Vector2(1, 0), Vector2(0, -1), Vector2(-1, 0)][i]


## Fassadenband mit an die Laenge angepasstem Achsabstand.
static func band(mb: MeshBatch, key: String, a: Vector2, b2: Vector2, y0: float, y1: float, col: Color,
		fh: float, bay: float, seed: float, fl: float, v0 := 0.0) -> void:
	var ln := a.distance_to(b2)
	if ln < 0.01:
		return
	var n := maxi(1, roundi(ln / bay))
	var bay_adj := ln / n
	mb.wall(key, a, b2, y0, y1, col, cus(fh, bay_adj, seed, fl), 0.0, v0)


## Kastenkranz um ein Rechteck (Gesims, Sockel, Attika-Abdeckung).
static func ring(mb: MeshBatch, key: String, r: Rect2, y0: float, y1: float, out_off: float, width: float, col: Color) -> void:
	var o := r.grow(out_off)
	var h := y1 - y0
	var yc := (y0 + y1) * 0.5
	var w := width
	mb.box(key, Vector3(o.get_center().x, yc, o.position.y + w * 0.5), Vector3(o.size.x, h, w), col)
	mb.box(key, Vector3(o.get_center().x, yc, o.end.y - w * 0.5), Vector3(o.size.x, h, w), col)
	mb.box(key, Vector3(o.position.x + w * 0.5, yc, o.get_center().y), Vector3(w, h, o.size.y - 2.0 * w), col, MeshBatch.NO_CUSTOM, 1 | 2 | 4 | 8)
	mb.box(key, Vector3(o.end.x - w * 0.5, yc, o.get_center().y), Vector3(w, h, o.size.y - 2.0 * w), col, MeshBatch.NO_CUSTOM, 1 | 2 | 4 | 8)


static func label(out: Dictionary, text: String, pos: Vector3, yaw: float, height: float, col: Color,
		max_w := 0.0, outline := Color(0, 0, 0, 0.6), range_end := 160.0, emissive := false) -> void:
	if text == "":
		return
	out["labels"].append({"text": text, "pos": pos, "yaw": yaw, "h": height, "color": col,
		"max_w": max_w, "outline": outline, "range": range_end, "emissive": emissive})


static func shape_box(out: Dictionary, center: Vector3, size: Vector3, yaw := 0.0) -> void:
	out["shapes"].append({"xf": Transform3D(Basis(Vector3.UP, yaw), center), "size": size})


## Gierwinkel, sodass lokales +z in Richtung dir (Vector2 x,z) zeigt.
static func yaw_of(dir: Vector2) -> float:
	return atan2(dir.x, dir.y)


static func luminance(c: Color) -> float:
	return c.r * 0.3 + c.g * 0.59 + c.b * 0.11


# ============================================================ Haupteinstieg

static func build(city: CityModel, b: Dictionary, mb: MeshBatch, det: MeshBatch, out: Dictionary) -> void:
	var rng := make_rng(city, b)
	var kind: String = b.kind
	if kind == "market_square":
		_market_square(city, b, rng, mb, det, out)
		return
	if kind == "homeless_camp":
		_camp(city, b, rng, mb, det, out)
		return
	var p := profile(city, b, rng)
	if b.business_type != "":
		p.ikind = ikind_for_business(b.business_type) if p.storefront else p.ikind
	var fp: Rect2 = b.footprint
	match kind:
		"tower":
			_tower(city, b, p, rng, mb, det, out)
		"station":
			_station(city, b, p, rng, mb, det, out)
		_:
			_standard(city, b, p, rng, fp, mb, det, out)
	_entrance(city, b, p, rng, mb, det, out)
	if p.storefront:
		_shopfront(city, b, p, rng, mb, det, out)
	match kind:
		"city_hall":
			_city_hall_tower(city, b, p, rng, mb, det, out)
		"parliament":
			_portico(city, b, p, rng, mb, det, out, true)
		"university":
			_portico(city, b, p, rng, mb, det, out, false)
		"fire_station":
			_fire_station(city, b, p, rng, mb, det, out)
		"police":
			_police(city, b, p, rng, mb, det, out)
		"hospital":
			_hospital(city, b, p, rng, mb, det, out)
		"gas_station":
			_gas_station(city, b, p, rng, mb, det, out)
		"warehouse", "factory", "bus_depot":
			_industrial(city, b, p, rng, mb, det, out)
		"house", "villa", "estate":
			_house_extras(city, b, p, rng, mb, det, out)
		"school", "clinic", "shelter":
			_civic_sign(city, b, p, out)
	if b.style.has("pool"):
		_pool(b, mb, det)
	if style_balconies(b):
		_balconies(city, b, p, rng, det)
	var h_coll: float = b.height if kind != "tower" else b.height
	if b.floors > 0:
		shape_box(out, Vector3(fp.get_center().x, (h_coll + GROUND) * 0.5, fp.get_center().y), Vector3(fp.size.x, h_coll + GROUND, fp.size.y))


# ============================================================ Standardgebaeude

static func _standard(city: CityModel, b: Dictionary, p: Dictionary, rng: RandomNumberGenerator, fp: Rect2,
		mb: MeshBatch, det: MeshBatch, out: Dictionary) -> void:
	var fh: float = b.floor_h
	var floors: int = maxi(1, b.floors)
	var eave := GROUND + floors * fh
	var roof: String = p.roof
	if b.kind == "parliament":
		roof = "dome"
	if b.kind == "market_hall":
		roof = "vault" if rng.randf() < 0.5 else "gable"
	var top := GROUND + float(b.height)
	var pitched: bool = roof in ["gable", "hip", "mansard"]
	if not pitched and roof not in ["vault", "sawtooth"]:
		top = maxf(top, eave + 0.9)
	var wall_top := eave if (pitched or roof in ["vault", "sawtooth"]) else top
	var corners := rect_corners(fp)
	var front_i := side_index(b.front)
	var col: Color = p.tint
	col.a = p.cond
	var seed: float = p.seed
	var fl_up := flags(p.wstyle, p.mode, p.ikind)
	for i in 4:
		var a: Vector2 = corners[i]
		var c: Vector2 = corners[(i + 1) % 4]
		var side_len := a.distance_to(c)
		var gmode: int = p.ground_mode if i == front_i else p.mode
		if p.storefront and i != front_i:
			gmode = p.mode if b.kind in ["mixed"] else MODE_WALL
		if b.kind in ["supermarket", "gas_station"] and i == front_i:
			gmode = MODE_SHOP
		var short_side := side_len < 3.0
		var fl_g := flags(p.wstyle, MODE_WALL if short_side else gmode, p.ikind)
		var fl_u := flags(p.wstyle, MODE_WALL if short_side else p.mode, p.ikind)
		# Erdgeschoss inkl. Fundamentstreifen
		var g_top := GROUND + fh
		if i == front_i and not p.storefront and p.mode == MODE_WINDOWS and floors >= 1:
			_ground_with_door(mb, p.fac, a, c, b.door, g_top, col, fh, p.bay, seed, fl_g)
		else:
			band(mb, p.fac, a, c, -0.3, g_top, col, fh, p.bay, seed, fl_g, -0.3 - GROUND)
		if floors > 1:
			band(mb, p.fac, a, c, GROUND + fh, eave, col, fh, p.bay, seed, fl_u, fh)
		if wall_top > eave + 0.01 and floors >= 1:
			band(mb, p.fac, a, c, maxf(eave, g_top), wall_top, col, fh, p.bay, seed, flags(0, MODE_WALL, 0), 0.0)
	# Sockel, Gurtgesims, Traufgesims
	var trim: Color = p.trim
	if p.plinth:
		ring(mb, "u_concrete" if p.facade != "stone" else "u_stone", fp, -0.2, GROUND + 0.32, 0.04, 0.3, trim.lightened(0.15))
	if p.string and floors >= 2:
		ring(mb, "paint_rough", fp, GROUND + fh - 0.12, GROUND + fh + 0.04, 0.06, 0.2, trim.lightened(0.35))
	match roof:
		"gable", "hip":
			_roof_pitched(mb, det, fp, eave, roof, p, rng, b)
		"mansard":
			_roof_mansard(mb, det, fp, eave, p, rng, b)
		"sawtooth":
			_roof_sawtooth(mb, fp, eave, p, rng)
		"vault":
			_roof_vault(mb, fp, eave, p, rng)
		_:
			_roof_flat(mb, det, fp, eave, top, p, rng, b)
			if roof == "dome":
				_dome(mb, fp, top, p, rng)


## Erdgeschoss der Eingangsseite: Fensterachse an der Tuer bleibt Wand.
static func _ground_with_door(mb: MeshBatch, key: String, a: Vector2, c: Vector2, door: Vector2, g_top: float,
		col: Color, fh: float, bay: float, seed: float, fl: float) -> void:
	var ln := a.distance_to(c)
	var n := maxi(1, roundi(ln / bay))
	var bay_adj := ln / n
	var dir := (c - a) / ln
	var u_door := clampf((door - a).dot(dir), 0.0, ln)
	var k0 := floori(maxf(u_door - 0.9, 0.0) / bay_adj)
	var k1 := floori(minf(u_door + 0.9, ln - 0.001) / bay_adj)
	var u0 := k0 * bay_adj
	var u1 := minf((k1 + 1) * bay_adj, ln)
	var cu := cus(fh, bay_adj, seed, fl)
	var cw := cus(fh, bay_adj, seed, flags(0, MODE_WALL, 0))
	if u0 > 0.01:
		mb.wall(key, a, a + dir * u0, -0.3, g_top, col, cu, 0.0, -0.3 - GROUND)
	mb.wall(key, a + dir * u0, a + dir * u1, -0.3, g_top, col, cw, u0, -0.3 - GROUND)
	if u1 < ln - 0.01:
		mb.wall(key, a + dir * u1, c, -0.3, g_top, col, cu, u1, -0.3 - GROUND)


# ============================================================ Daecher

static func _roof_flat(mb: MeshBatch, det: MeshBatch, fp: Rect2, eave: float, top: float, p: Dictionary,
		rng: RandomNumberGenerator, b: Dictionary) -> void:
	var deck := eave + 0.25
	var inner := fp.grow(-0.25)
	var rc := Color(0.62, 0.62, 0.60) if rng.randf() < 0.7 else Color(0.42, 0.42, 0.44)
	mb.flat_rect("u_roof_flat", inner, deck, rc)
	# Attika innen
	var col: Color = p.tint
	var ic := rect_corners(inner)
	for i in 4:
		# Innenwand zeigt nach innen: Richtung umkehren
		mb.wall("u_concrete", ic[(i + 1) % 4], ic[i], deck, top, col.darkened(0.1))
	var cap: Color = p.trim.lightened(0.25) if p.cornice else Color(0.72, 0.72, 0.70)
	if p.cornice:
		ring(mb, "u_stone" if p.facade in ["stone", "brick"] else "paint_rough", fp, top - 0.35, top, 0.16, 0.45, cap)
	else:
		ring(mb, "paint_metal", fp, top - 0.06, top + 0.04, 0.03, 0.3, Color(0.62, 0.63, 0.64))
	# Dachaufbauten
	var area := fp.get_area()
	var n_units := clampi(int(area / 140.0), 0, 6)
	var placed: Array[Rect2] = []
	if area > 80.0 and b.kind not in ["house", "townhouse", "villa", "estate", "shop", "restaurant", "gas_station"]:
		# Treppenhaus
		var sw := 3.2
		var sd := 4.0
		var c := Vector2(rng.randf_range(inner.position.x + sw, inner.end.x - sw), rng.randf_range(inner.position.y + sd, inner.end.y - sd))
		var r := Rect2(c - Vector2(sw, sd) * 0.5, Vector2(sw, sd))
		if inner.encloses(r):
			var hc := col.darkened(0.05)
			hc.a = p.cond
			var cc := rect_corners(r)
			for i in 4:
				mb.wall(p.fac, cc[i], cc[(i + 1) % 4], deck, deck + 2.7, hc, cus(3.0, 3.0, p.seed, flags(0, MODE_WALL, 0)))
			mb.flat_rect("u_roof_flat", r.grow(0.1), deck + 2.7, rc.darkened(0.1))
			placed.append(r.grow(1.0))
	for i in n_units:
		var s := Vector3(rng.randf_range(1.2, 2.4), rng.randf_range(0.8, 1.5), rng.randf_range(1.0, 2.0))
		var c2 := Vector2(rng.randf_range(inner.position.x + 1.5, inner.end.x - 1.5), rng.randf_range(inner.position.y + 1.5, inner.end.y - 1.5))
		var r2 := Rect2(Vector2(c2.x - s.x * 0.5, c2.y - s.z * 0.5), Vector2(s.x, s.z))
		var ok := inner.grow(-0.5).encloses(r2)
		for q in placed:
			if q.intersects(r2):
				ok = false
		if not ok:
			continue
		placed.append(r2.grow(0.6))
		det.box("paint_metal", Vector3(c2.x, deck + s.y * 0.5, c2.y), s, Color(0.70, 0.71, 0.72))
		det.box("paint_metal", Vector3(c2.x, deck + s.y + 0.05, c2.y), Vector3(s.x * 0.6, 0.1, s.z * 0.6), Color(0.3, 0.31, 0.32))
	# Solarmodule auf manchen Daechern
	if area > 150.0 and rng.randf() < 0.35 and b.kind in ["apartment", "office", "school", "mixed", "supermarket", "warehouse"]:
		var rows := int((inner.size.y - 3.0) / 2.6)
		var cols := int((inner.size.x - 3.0) / 1.9)
		for j in clampi(rows, 0, 8):
			for k in clampi(cols, 0, 12):
				var pc := Vector2(inner.position.x + 1.5 + k * 1.9 + 0.85, inner.position.y + 1.5 + j * 2.6 + 0.9)
				var pr := Rect2(pc - Vector2(0.9, 0.9), Vector2(1.8, 1.8))
				var free := true
				for q in placed:
					if q.intersects(pr):
						free = false
				if not free:
					continue
				var xf := Transform3D(Basis(Vector3.RIGHT, -0.35), Vector3(pc.x, deck + 0.45, pc.y))
				det.box_xf("glass", xf, Vector3(1.7, 0.05, 1.0), Color.WHITE)
	# Wassertank bei Mietshaeusern
	if b.kind in ["tenement", "apartment"] and rng.randf() < 0.3:
		var tc := Vector2(rng.randf_range(inner.position.x + 2, inner.end.x - 2), rng.randf_range(inner.position.y + 2, inner.end.y - 2))
		var free2 := true
		for q in placed:
			if q.has_point(tc):
				free2 = false
		if free2:
			for lg in 4:
				var o := Vector2(0.7 if lg % 2 == 0 else -0.7, 0.7 if lg < 2 else -0.7)
				det.box("paint_metal", Vector3(tc.x + o.x, deck + 0.8, tc.y + o.y), Vector3(0.12, 1.6, 0.12), Color(0.3, 0.3, 0.3))
			det.cylinder("u_wood", Vector3(tc.x, deck + 1.6, tc.y), 1.1, 1.1, 2.2, 10, Color(0.75, 0.62, 0.48))


static func _roof_pitched(mb: MeshBatch, det: MeshBatch, fp: Rect2, eave: float, roof: String, p: Dictionary,
		rng: RandomNumberGenerator, b: Dictionary) -> void:
	var ov := 0.45
	var r := fp.grow(ov)
	var along_x := fp.size.x >= fp.size.y
	var span := (r.size.y if along_x else r.size.x) * 0.5
	var pitch := deg_to_rad(rng.randf_range(32.0, 44.0))
	var rise := (span - ov) * tan(pitch)
	var y0 := eave - ov * tan(pitch)
	var ridge_y := eave + rise
	var rc: Color = p.roof_col
	var col: Color = p.tint
	col.a = p.cond
	var hip_in := 0.0
	if roof == "hip":
		hip_in = minf(span, (r.size.x if along_x else r.size.y) * 0.5)
	# Lokale Achsen: L entlang First, S quer
	var c := r.get_center()
	var L := (r.size.x if along_x else r.size.y) * 0.5
	var S := span
	var ax := Vector2(1, 0) if along_x else Vector2(0, 1)
	var sx := Vector2(0, 1) if along_x else Vector2(1, 0)
	var to3 := func(l: float, s: float, y: float) -> Vector3:
		var q: Vector2 = c + ax * l + sx * s
		return Vector3(q.x, y, q.y)
	var e0: Vector3 = to3.call(-L, -S, y0)
	var e1: Vector3 = to3.call(L, -S, y0)
	var e2: Vector3 = to3.call(L, S, y0)
	var e3: Vector3 = to3.call(-L, S, y0)
	var r0: Vector3 = to3.call(-L + hip_in, 0.0, ridge_y)
	var r1: Vector3 = to3.call(L - hip_in, 0.0, ridge_y)
	var slope_len := sqrt(S * S + (ridge_y - y0) * (ridge_y - y0))
	var s := mb.surf("u_roof_tiles")
	# Seite -S und +S (Trapez bzw. Rechteck)
	for sgn in [-1.0, 1.0]:
		var ea: Vector3 = e0 if sgn < 0 else e2
		var eb: Vector3 = e1 if sgn < 0 else e3
		var ra: Vector3 = r0 if sgn < 0 else r1
		var rb: Vector3 = r1 if sgn < 0 else r0
		var nrm := (eb - ea).cross(ra - ea).normalized()
		if nrm.y < 0:
			nrm = -nrm
		var tan := (eb - ea).normalized()
		var ln := ea.distance_to(eb)
		var off_a := (ra - ea).dot(tan)
		var off_b := (rb - ea).dot(tan)
		# v nach unten: Traufe = 0, First = -slope_len
		_quad_ccw_up(s, ea, eb, rb, ra, nrm, tan, Vector2(0, 0), Vector2(ln, 0), Vector2(off_b, -slope_len), Vector2(off_a, -slope_len), rc)
	if roof == "hip":
		for sgn2 in [-1.0, 1.0]:
			var ea2: Vector3 = e3 if sgn2 < 0 else e1
			var eb2: Vector3 = e0 if sgn2 < 0 else e2
			var rr: Vector3 = r0 if sgn2 < 0 else r1
			var nrm2 := (eb2 - ea2).cross(rr - ea2).normalized()
			if nrm2.y < 0:
				nrm2 = -nrm2
			var tan2 := (eb2 - ea2).normalized()
			var ln2 := ea2.distance_to(eb2)
			var hl := sqrt(hip_in * hip_in + (ridge_y - y0) * (ridge_y - y0))
			_tri_ccw_up(s, ea2, eb2, rr, nrm2, tan2, Vector2(0, 0), Vector2(ln2, 0), Vector2(ln2 * 0.5, -hl), rc)
	else:
		# Giebelwaende (Dreiecke) mit Fassadenmaterial
		var fs := mb.surf(p.fac)
		for sgn3 in [-1.0, 1.0]:
			var lx: float = (L - ov) * sgn3
			var a3: Vector3 = to3.call(lx, -(S - ov) * sgn3, eave)
			var b3: Vector3 = to3.call(lx, (S - ov) * sgn3, eave)
			var t3: Vector3 = to3.call(lx, 0.0, ridge_y - 0.05)
			var n3 := (b3 - a3).cross(t3 - a3).normalized()
			# Aussen zeigt weg vom Zentrum
			var outward = Vector3(ax.x, 0, ax.y) * sgn3
			if n3.dot(outward) < 0:
				var tmp := a3
				a3 = b3
				b3 = tmp
			var tn := (b3 - a3).normalized()
			var w := a3.distance_to(b3)
			var cu := cus(3.0, 3.0, p.seed, flags(0, MODE_WALL, 0))
			fs.tri(a3, b3, t3, outward, tn, Vector2(0, 0), Vector2(w, 0), Vector2(w * 0.5, -(ridge_y - eave)), col, cu)
			# Ortgangbrett
			var vb := Color(0.92, 0.92, 0.9) if p.cond > 0.4 else Color(0.4, 0.32, 0.25)
			var ea3: Vector3 = to3.call(lx + sgn3 * ov, -S * sgn3, y0)
			var eb3: Vector3 = to3.call(lx + sgn3 * ov, S * sgn3, y0)
			var top3: Vector3 = to3.call(lx + sgn3 * ov, 0.0, ridge_y)
			_board(det, ea3, top3, vb)
			_board(det, eb3, top3, vb)
	# Dachuntersicht (verhindert Durchblick) und Traufbretter
	var under := mb.surf("paint_rough")
	var uc := Color(0.85, 0.84, 0.80)
	var f0 := Vector3(e0.x, y0, e0.z)
	var f1 := Vector3(e1.x, y0, e1.z)
	var f2 := Vector3(e2.x, y0, e2.z)
	var f3 := Vector3(e3.x, y0, e3.z)
	# Unterseite: von unten gegen den Uhrzeigersinn = von oben im Uhrzeigersinn
	under.quad(f0, f1, f2, f3, Vector3.DOWN, Vector3.RIGHT, Vector2.ZERO, Vector2.ONE, Vector2.ONE, Vector2.ZERO, uc, MeshBatch.NO_CUSTOM)
	_fix_down(under)
	var fc := Color(0.9, 0.9, 0.88) if p.cond > 0.4 else Color(0.45, 0.4, 0.35)
	mb.box("paint", Vector3((e0.x + e1.x) * 0.5, y0 - 0.1, (e0.z + e1.z) * 0.5), Vector3(absf(e1.x - e0.x) + 0.04, 0.22, absf(e1.z - e0.z) + 0.04), fc)
	mb.box("paint", Vector3((e2.x + e3.x) * 0.5, y0 - 0.1, (e2.z + e3.z) * 0.5), Vector3(absf(e2.x - e3.x) + 0.04, 0.22, absf(e2.z - e3.z) + 0.04), fc)
	# Firstkappe
	if r0.distance_to(r1) > 0.1:
		var rm := (r0 + r1) * 0.5
		var rl := r0.distance_to(r1)
		mb.box("u_roof_tiles", rm + Vector3(0, 0.04, 0), Vector3(rl if along_x else 0.3, 0.12, 0.3 if along_x else rl), rc.darkened(0.15))
	# Schornstein
	if b.kind in ["house", "townhouse", "villa", "estate", "mixed", "tenement"] and rng.randf() < 0.75:
		var cl := rng.randf_range(-L * 0.6, L * 0.6)
		var cs := rng.randf_range(0.25, 0.6) * S * (1.0 if rng.randf() < 0.5 else -1.0)
		var base_y := ridge_y - absf(cs) / S * (ridge_y - y0)
		var cpos: Vector3 = to3.call(cl, cs, 0.0)
		var ch := ridge_y + 0.9 - base_y + 0.6
		mb.box("u_brick", Vector3(cpos.x, base_y - 0.6 + ch * 0.5, cpos.z), Vector3(0.6, ch, 0.6), Color(0.7, 0.4, 0.32))
		mb.box("u_concrete", Vector3(cpos.x, base_y - 0.6 + ch + 0.05, cpos.z), Vector3(0.75, 0.1, 0.75), Color(0.5, 0.5, 0.5))
	# Gauben auf grossen Daechern
	if (b.kind in ["villa", "estate", "mixed", "tenement"] or (b.kind == "house" and rng.randf() < 0.3)) and roof == "gable" and L > 4.0:
		var nd := clampi(int((L * 2.0 - 3.0) / 3.5), 1, 5)
		for k in nd:
			var lpos := -L + 2.0 + (L * 2.0 - 4.0) * (k + 0.5) / nd
			for sg in [-1.0, 1.0]:
				_dormer(det, to3, lpos, sg, S, ov, y0, ridge_y, eave, p, rc)


static func _dormer(det: MeshBatch, to3: Callable, l: float, sg: float, S: float, ov: float, y0: float, ridge_y: float,
		eave: float, p: Dictionary, rc: Color) -> void:
	var s_front := (S - ov) * 0.55 * sg
	var t := absf(s_front) / S
	var y_at := y0 + (1.0 - t) * (ridge_y - y0) * 1.0
	y_at = ridge_y - absf(s_front) / S * (ridge_y - y0)
	var h := 1.5
	if y_at + h > ridge_y - 0.2:
		return
	var center: Vector3 = to3.call(l, s_front, y_at + h * 0.5)
	var back: Vector3 = to3.call(l, s_front * 0.4, y_at + h * 0.5)
	var dir := (center - back).normalized()
	var yaw := atan2(dir.x, dir.z)
	var col: Color = p.tint
	col.a = p.cond
	var depth := center.distance_to(back) + 0.4
	var mid := (center + back) * 0.5
	var xf := Transform3D(Basis(Vector3.UP, yaw), mid)
	det.box_xf("paint_rough", xf, Vector3(1.4, h, depth), col)
	# Fensterflaeche
	var wxf := Transform3D(Basis(Vector3.UP, yaw), center + dir * (0.21 + 0.0) + Vector3(0, 0.05, 0))
	det.box_xf("glass", wxf, Vector3(0.9, 0.95, 0.04), Color.WHITE)
	det.box_xf("paint", Transform3D(Basis(Vector3.UP, yaw), center + dir * 0.2 + Vector3(0, 0.05, 0)), Vector3(1.05, 1.1, 0.02), Color(0.95, 0.95, 0.93))
	# kleines Satteldach
	det.box_xf("u_roof_tiles", Transform3D(Basis(Vector3.UP, yaw), mid + Vector3(0, h * 0.5 + 0.08, 0)), Vector3(1.7, 0.16, depth + 0.3), rc)


static func _board(det: MeshBatch, a: Vector3, b2: Vector3, col: Color) -> void:
	var mid := (a + b2) * 0.5
	var d := b2 - a
	var ln := d.length()
	var basis := Basis()
	var fwd := d / ln
	var side := fwd.cross(Vector3.UP)
	if side.length_squared() < 1e-6:
		side = Vector3.RIGHT
	side = side.normalized()
	var up := side.cross(fwd).normalized()
	basis = Basis(side, up, fwd)
	det.box_xf("paint", Transform3D(basis, mid), Vector3(0.06, 0.22, ln), col)


## Hilfsfunktionen fuer Dachflaechen: Umlaufsinn automatisch anhand der Normale.
static func _quad_ccw_up(s: MeshBatch.Surf, p0: Vector3, p1: Vector3, p2: Vector3, p3: Vector3, nrm: Vector3, tan: Vector3,
		uv0: Vector2, uv1: Vector2, uv2: Vector2, uv3: Vector2, col: Color) -> void:
	var geo_n := (p1 - p0).cross(p2 - p0)
	if geo_n.dot(nrm) >= 0.0:
		s.quad(p0, p1, p2, p3, nrm, tan, uv0, uv1, uv2, uv3, col, MeshBatch.NO_CUSTOM)
	else:
		s.quad(p0, p3, p2, p1, nrm, tan, uv0, uv3, uv2, uv1, col, MeshBatch.NO_CUSTOM)


static func _tri_ccw_up(s: MeshBatch.Surf, p0: Vector3, p1: Vector3, p2: Vector3, nrm: Vector3, tan: Vector3,
		uv0: Vector2, uv1: Vector2, uv2: Vector2, col: Color) -> void:
	var geo_n := (p1 - p0).cross(p2 - p0)
	if geo_n.dot(nrm) >= 0.0:
		s.tri(p0, p1, p2, nrm, tan, uv0, uv1, uv2, col, MeshBatch.NO_CUSTOM)
	else:
		s.tri(p0, p2, p1, nrm, tan, uv0, uv2, uv1, col, MeshBatch.NO_CUSTOM)


## Letztes Viereck so umdrehen, dass es nach unten zeigt (Normale DOWN).
static func _fix_down(s: MeshBatch.Surf) -> void:
	var n := s.idx.size()
	var p0: Vector3 = s.v[s.idx[n - 6]]
	var p1: Vector3 = s.v[s.idx[n - 5]]
	var p2: Vector3 = s.v[s.idx[n - 4]]
	# Godot: Vorderseite im Uhrzeigersinn; Normale der Vorderseite = (p2-p0)x(p1-p0)
	var geo := (p2 - p0).cross(p1 - p0)
	if geo.y > 0.0:
		for k in range(n - 6, n, 3):
			var t := s.idx[k + 1]
			s.idx[k + 1] = s.idx[k + 2]
			s.idx[k + 2] = t


static func _roof_mansard(mb: MeshBatch, det: MeshBatch, fp: Rect2, eave: float, p: Dictionary,
		rng: RandomNumberGenerator, b: Dictionary) -> void:
	var lower_h := 2.6
	var inset := lower_h / tan(deg_to_rad(72.0))
	var r0 := fp.grow(0.2)
	var r1 := fp.grow(-inset + 0.2)
	var rc: Color = p.roof_col.darkened(0.25)
	var c0 := rect_corners(r0)
	var c1 := rect_corners(r1)
	var s := mb.surf("u_roof_tiles")
	for i in 4:
		var a0: Vector2 = c0[i]
		var b0: Vector2 = c0[(i + 1) % 4]
		var a1: Vector2 = c1[i]
		var b1: Vector2 = c1[(i + 1) % 4]
		var pa0 := Vector3(a0.x, eave, a0.y)
		var pb0 := Vector3(b0.x, eave, b0.y)
		var pa1 := Vector3(a1.x, eave + lower_h, a1.y)
		var pb1 := Vector3(b1.x, eave + lower_h, b1.y)
		var nrm := (pb0 - pa0).cross(pa1 - pa0).normalized()
		var sn := side_normal(i)
		if nrm.dot(Vector3(sn.x, 0, sn.y)) < 0:
			nrm = -nrm
		var tan := (pb0 - pa0).normalized()
		var ln := pa0.distance_to(pb0)
		var sl := pa0.distance_to(pa1)
		_quad_ccw_up(s, pa0, pb0, pb1, pa1, nrm, tan, Vector2(0, 0), Vector2(ln, 0), Vector2(ln - inset, -sl), Vector2(inset, -sl), rc)
		# Gauben
		var dir := (b0 - a0).normalized()
		var nd := int((ln - 2.0) / max(p.bay, 2.5))
		for k in clampi(nd, 0, 12):
			var u := (k + 0.5) * (ln / maxf(nd, 1))
			if u < 1.2 or u > ln - 1.2:
				continue
			var base2 := a0 + dir * u - sn * (inset * 0.45)
			var yaw := yaw_of(sn)
			var col: Color = p.tint
			det.box_xf("paint_rough", Transform3D(Basis(Vector3.UP, yaw), Vector3(base2.x, eave + 1.0, base2.y)), Vector3(1.2, 1.4, 0.9), col.lightened(0.1))
			det.box_xf("glass", Transform3D(Basis(Vector3.UP, yaw), Vector3(base2.x, eave + 1.0, base2.y) + Vector3(sn.x, 0, sn.y) * 0.46), Vector3(0.75, 0.9, 0.03), Color.WHITE)
			det.box_xf("u_roof_tiles", Transform3D(Basis(Vector3.UP, yaw), Vector3(base2.x, eave + 1.78, base2.y)), Vector3(1.4, 0.16, 1.1), rc)
	mb.flat_rect("u_roof_flat", r1, eave + lower_h, Color(0.45, 0.45, 0.47))
	ring(mb, "paint_rough", fp, eave - 0.3, eave, 0.15, 0.35, p.trim.lightened(0.3))


static func _roof_sawtooth(mb: MeshBatch, fp: Rect2, eave: float, p: Dictionary, rng: RandomNumberGenerator) -> void:
	var along_x := fp.size.x >= fp.size.y
	var L := fp.size.x if along_x else fp.size.y
	var teeth := maxi(2, int(L / 7.0))
	var tl := L / teeth
	var h := 3.0
	var W := fp.size.y if along_x else fp.size.x
	var rc := Color(0.55, 0.56, 0.58)
	var col: Color = p.tint
	col.a = p.cond
	for k in teeth:
		var l0 := k * tl
		var l1 := (k + 1) * tl
		var pts := []
		for q in [[l0, 0.0], [l1, 0.0], [l1, W], [l0, W]]:
			var v: Vector2
			if along_x:
				v = Vector2(fp.position.x + q[0], fp.position.y + q[1])
			else:
				v = Vector2(fp.position.x + q[1], fp.position.y + q[0])
			pts.append(v)
		# Schraege von l0 (unten) nach l1 (oben), senkrechte Glasflaeche bei l1
		var a := Vector3(pts[0].x, eave, pts[0].y)
		var b2 := Vector3(pts[1].x, eave + h, pts[1].y)
		var c := Vector3(pts[2].x, eave + h, pts[2].y)
		var d := Vector3(pts[3].x, eave, pts[3].y)
		var nrm := (b2 - a).cross(d - a).normalized()
		if nrm.y < 0:
			nrm = -nrm
		var sl := a.distance_to(b2)
		_quad_ccw_up(mb.surf("u_metal"), a, d, c, b2, nrm, (d - a).normalized(), Vector2(0, 0), Vector2(W, 0), Vector2(W, -sl), Vector2(0, -sl), rc)
		# Glasband
		var g0 := Vector2(pts[1].x, pts[1].y)
		var g1 := Vector2(pts[2].x, pts[2].y)
		var fwd = (pts[1] - pts[0]).normalized()
		var wa := g0
		var wb := g1
		if (wb - wa).normalized().dot(Vector2(fwd.y, -fwd.x)) < 0:
			var t := wa
			wa = wb
			wb = t
		mb.wall("glass", wb, wa, eave, eave + h, Color.WHITE)
		mb.wall("glass", wa, wb, eave, eave + h, Color.WHITE)
	# Seitenwaende der Zaehne (Dreiecke) an den Laengsseiten
	var s := mb.surf(p.fac)
	for k in teeth:
		for side in [0.0, W]:
			var l0 := k * tl
			var l1 := (k + 1) * tl
			var q0: Vector2
			var q1: Vector2
			if along_x:
				q0 = Vector2(fp.position.x + l0, fp.position.y + side)
				q1 = Vector2(fp.position.x + l1, fp.position.y + side)
			else:
				q0 = Vector2(fp.position.x + side, fp.position.y + l0)
				q1 = Vector2(fp.position.x + side, fp.position.y + l1)
			var a3 := Vector3(q0.x, eave, q0.y)
			var b3 := Vector3(q1.x, eave, q1.y)
			var t3 := Vector3(q1.x, eave + h, q1.y)
			var outward := Vector3(0, 0, -1 if side == 0.0 else 1) if along_x else Vector3(-1 if side == 0.0 else 1, 0, 0)
			_tri_ccw_up(s, a3, b3, t3, outward, (b3 - a3).normalized(), Vector2(0, 0), Vector2(tl, 0), Vector2(tl, -h), col)


static func _roof_vault(mb: MeshBatch, fp: Rect2, eave: float, p: Dictionary, rng: RandomNumberGenerator) -> void:
	var along_x := fp.size.x >= fp.size.y
	var W := (fp.size.y if along_x else fp.size.x)
	var L := (fp.size.x if along_x else fp.size.y)
	var rise := W * 0.28
	var seg := 12
	var c := fp.get_center()
	var col: Color = p.tint
	col.a = p.cond
	var pts: Array[Vector2] = []   # (quer, hoehe)
	for i in seg + 1:
		var t := float(i) / seg
		var x := -W * 0.5 + W * t
		var y := rise * sin(PI * t)
		pts.append(Vector2(x, y))
	var to3 := func(l: float, s: float, y: float) -> Vector3:
		if along_x:
			return Vector3(c.x + l, eave + y, c.y + s)
		return Vector3(c.x + s, eave + y, c.y + l)
	for i in seg:
		var a: Vector2 = pts[i]
		var b2: Vector2 = pts[i + 1]
		var key := "glass" if (i % 3 == 1 and i > 0 and i < seg - 1) else "u_metal"
		var p0: Vector3 = to3.call(-L * 0.5 - 0.3, a.x, a.y)
		var p1: Vector3 = to3.call(L * 0.5 + 0.3, a.x, a.y)
		var p2: Vector3 = to3.call(L * 0.5 + 0.3, b2.x, b2.y)
		var p3: Vector3 = to3.call(-L * 0.5 - 0.3, b2.x, b2.y)
		var mid := Vector2((a.x + b2.x) * 0.5, (a.y + b2.y) * 0.5)
		var nl := Vector2(mid.x / (W * 0.5) * rise, W * 0.5)
		var nrm: Vector3 = (to3.call(0.0, nl.x * 0.3, 0.0) - to3.call(0.0, 0.0, 0.0))
		nrm = (nrm + Vector3(0, nl.y * 0.3, 0)).normalized()
		var sl := a.distance_to(b2)
		_quad_ccw_up(mb.surf(key), p0, p1, p2, p3, nrm, (p1 - p0).normalized(), Vector2(0, 0), Vector2(L, 0), Vector2(L, -sl), Vector2(0, -sl), Color(0.55, 0.6, 0.62))
	# Stirnflaechen (Halbkreis) aus Glas mit Rahmen
	for sg in [-1.0, 1.0]:
		var s := mb.surf("glass")
		var cidx := s.vert(to3.call(sg * L * 0.5, 0.0, 0.0), Vector3.ZERO, Vector3.RIGHT, Vector2.ZERO, Color.WHITE, MeshBatch.NO_CUSTOM)
		var outward: Vector3 = (to3.call(sg, 0.0, 0.0) - to3.call(0.0, 0.0, 0.0)).normalized()
		s.n[cidx] = outward
		var prev := -1
		for i in seg + 1:
			var vi := s.vert(to3.call(sg * L * 0.5, pts[i].x, pts[i].y), outward, Vector3.RIGHT, Vector2(pts[i].x, -pts[i].y), Color.WHITE, MeshBatch.NO_CUSTOM)
			if prev >= 0:
				var p0: Vector3 = s.v[cidx]
				var geo := (s.v[prev] - p0).cross(s.v[vi] - p0)
				if geo.dot(outward) > 0:
					s.tri_idx(cidx, prev, vi)
				else:
					s.tri_idx(cidx, vi, prev)
			prev = vi
		for i in range(0, seg + 1, 2):
			var q: Vector3 = to3.call(sg * (L * 0.5 + 0.05), pts[i].x, 0.0)
			mb.box("paint_metal", q + Vector3(0, pts[i].y * 0.5, 0), Vector3(0.12, maxf(pts[i].y, 0.1), 0.12), Color(0.3, 0.32, 0.34))
	ring(mb, "u_stone", fp, eave - 0.4, eave + 0.05, 0.2, 0.4, Color(0.8, 0.77, 0.7))


static func _dome(mb: MeshBatch, fp: Rect2, top: float, p: Dictionary, rng: RandomNumberGenerator) -> void:
	var c := fp.get_center()
	var r := minf(fp.size.x, fp.size.y) * 0.24
	var col: Color = p.tint
	col.a = p.cond
	# Tambour mit Saeulen-Anmutung
	mb.cylinder("u_stone", Vector3(c.x, top - 0.5, c.y), r * 1.05, r * 1.05, 4.5, 24, Color(0.85, 0.82, 0.75), MeshBatch.NO_CUSTOM, false)
	for i in 16:
		var a := TAU * i / 16.0
		var q := Vector3(c.x + cos(a) * r * 1.1, top + 1.8, c.y + sin(a) * r * 1.1)
		mb.box("u_stone", q, Vector3(0.45, 4.0, 0.45), Color(0.9, 0.88, 0.82))
	mb.cylinder("u_stone", Vector3(c.x, top + 3.9, c.y), r * 1.18, r * 1.18, 0.4, 24, Color(0.82, 0.8, 0.74), MeshBatch.NO_CUSTOM, false)
	_hemisphere(mb, "paint_metal", Vector3(c.x, top + 4.2, c.y), r, r * 0.95, Color(0.36, 0.58, 0.50))
	mb.cylinder("u_stone", Vector3(c.x, top + 4.2 + r * 0.92, c.y), r * 0.14, r * 0.14, 1.6, 10, Color(0.9, 0.88, 0.82))
	_hemisphere(mb, "paint_metal", Vector3(c.x, top + 5.8 + r * 0.92, c.y), r * 0.16, r * 0.18, Color(0.36, 0.58, 0.50))
	mb.cylinder("chrome", Vector3(c.x, top + 5.8 + r * 1.1, c.y), 0.06, 0.02, 2.5, 6, Color(0.95, 0.8, 0.4))


static func _hemisphere(mb: MeshBatch, key: String, base: Vector3, r: float, h: float, col: Color, rings := 8, seg := 24) -> void:
	var s := mb.surf(key)
	var rows: Array = []
	for j in rings + 1:
		var phi := PI * 0.5 * float(j) / rings
		var row := []
		for i in seg + 1:
			var th := TAU * float(i) / seg
			var dirv := Vector3(cos(th) * cos(phi), sin(phi), sin(th) * cos(phi))
			var pos := base + Vector3(dirv.x * r, dirv.y * h, dirv.z * r)
			var nrm := Vector3(dirv.x / r, dirv.y / h, dirv.z / r).normalized()
			row.append(s.vert(pos, nrm, Vector3(-sin(th), 0, cos(th)), Vector2(th * r, -phi * h), col, MeshBatch.NO_CUSTOM))
		rows.append(row)
	for j in rings:
		for i in seg:
			var a: int = rows[j][i]
			var b2: int = rows[j][i + 1]
			var c: int = rows[j + 1][i + 1]
			var d: int = rows[j + 1][i]
			# von aussen: a (unten, th), b (unten, th+), c (oben, th+), d (oben, th)
			# th waechst von +x nach +z -> von aussen gesehen nach links => a,d,c,b gegen den Uhrzeigersinn
			s.tri_idx(a, d, c)
			s.tri_idx(a, c, b2)


# ============================================================ Hilfen Front

## Endpunkte der Eingangsseite (von aussen gesehen links -> rechts) und Normale.
static func front_side(b: Dictionary) -> Dictionary:
	var fp: Rect2 = b.footprint
	var i := side_index(b.front)
	var cs := rect_corners(fp)
	var a: Vector2 = cs[i]
	var c: Vector2 = cs[(i + 1) % 4]
	return {"a": a, "c": c, "dir": (c - a).normalized(), "n": side_normal(i), "len": a.distance_to(c), "i": i}


static func _split_front(fp: Rect2, front: String, depth: float) -> Array:
	match front:
		"s": return [Rect2(fp.position.x, fp.end.y - depth, fp.size.x, depth), Rect2(fp.position.x, fp.position.y, fp.size.x, fp.size.y - depth)]
		"n": return [Rect2(fp.position.x, fp.position.y, fp.size.x, depth), Rect2(fp.position.x, fp.position.y + depth, fp.size.x, fp.size.y - depth)]
		"e": return [Rect2(fp.end.x - depth, fp.position.y, depth, fp.size.y), Rect2(fp.position.x, fp.position.y, fp.size.x - depth, fp.size.y)]
	return [Rect2(fp.position.x, fp.position.y, depth, fp.size.y), Rect2(fp.position.x + depth, fp.position.y, fp.size.x - depth, fp.size.y)]


## Schild aus dem Verkehrszeichen-Atlas (Zelle 0..7), Vorderseite zeigt nach yaw.
static func sign_quad(mb: MeshBatch, center: Vector3, yaw: float, size: Vector2, cell: int, back := true) -> void:
	var s := mb.surf("signs")
	var bas := Basis(Vector3.UP, yaw)
	var r := bas * Vector3.RIGHT
	var u := Vector3.UP
	var n := bas * Vector3.BACK
	var hx := size.x * 0.5
	var hy := size.y * 0.5
	var u0 := float(cell % 4) / 4.0
	var v0 := float(cell / 4) / 2.0
	var p0 := center - r * hx - u * hy + n * 0.01
	var p1 := center + r * hx - u * hy + n * 0.01
	var p2 := center + r * hx + u * hy + n * 0.01
	var p3 := center - r * hx + u * hy + n * 0.01
	s.quad(p0, p1, p2, p3, n, r, Vector2(u0, v0 + 0.5), Vector2(u0 + 0.25, v0 + 0.5), Vector2(u0 + 0.25, v0), Vector2(u0, v0), Color.WHITE, MeshBatch.NO_CUSTOM)
	if back:
		var q0 := center + r * hx - u * hy - n * 0.01
		var q1 := center - r * hx - u * hy - n * 0.01
		var q2 := center - r * hx + u * hy - n * 0.01
		var q3 := center + r * hx + u * hy - n * 0.01
		s.quad(q0, q1, q2, q3, -n, -r, Vector2(0.76, 0.99), Vector2(0.99, 0.99), Vector2(0.99, 0.51), Vector2(0.76, 0.51), Color.WHITE, MeshBatch.NO_CUSTOM)


# ============================================================ Eingang

static func _entrance(city: CityModel, b: Dictionary, p: Dictionary, rng: RandomNumberGenerator, mb: MeshBatch,
		det: MeshBatch, out: Dictionary) -> void:
	if p.storefront or b.floors <= 0:
		return
	var sn: Vector2 = b.front_dir
	var door: Vector2 = b.door + sn * float(p.get("door_off", 0.0))
	var yaw := yaw_of(sn)
	var kind: String = b.kind
	var w := 1.6
	var h := 2.5
	var panel := "glass"
	var pcol := Color.WHITE
	var canopy := false
	var fcol := Color(0.22, 0.23, 0.24)
	match kind:
		"house", "townhouse", "villa", "estate":
			w = 1.1 if kind != "estate" else 1.8
			h = 2.25 if kind != "estate" else 2.8
			panel = "u_wood" if rng.randf() < 0.6 else "paint"
			pcol = [Color(0.55, 0.36, 0.22), Color(0.2, 0.3, 0.45), Color(0.6, 0.15, 0.15), Color(0.25, 0.4, 0.3), Color(0.95, 0.95, 0.93)][rng.randi_range(0, 4)]
			fcol = Color(0.94, 0.94, 0.92)
		"office", "tower":
			w = 3.2
			h = 3.0
			canopy = kind == "office"
			fcol = Color(0.3, 0.31, 0.33)
		"warehouse", "factory", "bus_depot":
			w = 1.1
			h = 2.2
			panel = "paint_metal"
			pcol = Color(0.45, 0.48, 0.5)
		"abandoned":
			w = 1.2
			h = 2.3
			panel = "u_wood"
			pcol = Color(0.4, 0.3, 0.22)
		"school", "university", "hospital", "police", "fire_station", "city_hall", "parliament", "station", "market_hall":
			w = 2.8
			h = 3.2
			canopy = kind in ["school", "hospital", "police"]
		_:
			canopy = kind in ["apartment", "highrise_res", "clinic", "shelter"] and rng.randf() < 0.8
	var base := Vector3(door.x, GROUND, door.y)
	var n3 := Vector3(sn.x, 0, sn.y)
	var bas := Basis(Vector3.UP, yaw)
	mb.box_xf("paint", Transform3D(bas, base + n3 * 0.06 + Vector3(0, h * 0.5 + 0.04, 0)), Vector3(w + 0.3, h + 0.16, 0.12), fcol)
	mb.box_xf(panel, Transform3D(bas, base + n3 * 0.1 + Vector3(0, h * 0.5, 0)), Vector3(w, h, 0.05), pcol)
	if panel == "glass":
		det.box_xf("paint_metal", Transform3D(bas, base + n3 * 0.13 + Vector3(0, h * 0.5, 0)), Vector3(0.06, h, 0.02), fcol)
		det.box_xf("chrome", Transform3D(bas, base + n3 * 0.15 + (bas * Vector3.RIGHT) * 0.12 + Vector3(0, 1.05, 0)), Vector3(0.03, 0.9, 0.03), Color.WHITE)
	else:
		det.box_xf("chrome", Transform3D(bas, base + n3 * 0.14 + (bas * Vector3.RIGHT) * (w * 0.38) + Vector3(0, 1.05, 0)), Vector3(0.14, 0.03, 0.05), Color.WHITE)
	if canopy:
		var cw := w + 1.6
		mb.box_xf("paint_metal", Transform3D(bas, base + n3 * 0.8 + Vector3(0, h + 0.45, 0)), Vector3(cw, 0.16, 1.6), Color(0.35, 0.36, 0.38))
		det.box_xf("lamp_glow", Transform3D(bas, base + n3 * 0.8 + Vector3(0, h + 0.36, 0)), Vector3(cw * 0.6, 0.03, 0.2), Color(1.0, 0.92, 0.78))
		out["lights"].append({"pos": base + n3 * 1.0 + Vector3(0, h + 0.2, 0), "kind": "entrance"})
	elif kind in ["house", "townhouse", "villa", "estate", "apartment", "tenement", "mixed"]:
		var rr := bas * Vector3.RIGHT
		det.box_xf("paint_metal", Transform3D(bas, base + n3 * 0.1 + rr * (w * 0.5 + 0.35) + Vector3(0, 2.0, 0)), Vector3(0.16, 0.26, 0.12), Color(0.15, 0.15, 0.15))
		det.box_xf("lamp_glow", Transform3D(bas, base + n3 * 0.17 + rr * (w * 0.5 + 0.35) + Vector3(0, 2.0, 0)), Vector3(0.11, 0.18, 0.02), Color(1.0, 0.85, 0.6))
	if kind in ["apartment", "tenement", "highrise_res", "mixed"]:
		var rr2 := bas * Vector3.RIGHT
		det.box_xf("chrome", Transform3D(bas, base + n3 * 0.13 - rr2 * (w * 0.5 + 0.3) + Vector3(0, 1.3, 0)), Vector3(0.18, 0.35, 0.03), Color.WHITE)


# ============================================================ Laden

static func _shopfront(city: CityModel, b: Dictionary, p: Dictionary, rng: RandomNumberGenerator, mb: MeshBatch,
		det: MeshBatch, out: Dictionary) -> void:
	var fs := front_side(b)
	var sn: Vector2 = fs.n
	var n3 := Vector3(sn.x, 0, sn.y)
	var dir: Vector2 = fs.dir
	var d3 := Vector3(dir.x, 0, dir.y)
	var yaw := yaw_of(sn)
	var bas := Basis(Vector3.UP, yaw)
	var fh: float = b.floor_h
	var ln: float = fs.len
	var mid: Vector2 = (fs.a + fs.c) * 0.5
	var brand: Color = b.brand_color
	var big: bool = b.kind in ["supermarket", "market_hall"]
	var sw := minf(ln - 1.0, maxf(4.0, ln * 0.72))
	var sh := 0.62
	var sy := GROUND + fh - 0.42
	if big:
		sw = minf(ln * 0.6, 22.0)
		sh = 1.7
		sy = GROUND + fh + (0.0 if b.kind == "supermarket" else -1.6)
	if b.kind == "gas_station":
		sh = 0.8
		sy = GROUND + fh + 0.3
	var sc := Vector3(mid.x, sy, mid.y) + n3 * 0.1
	mb.box_xf("sign_glow", Transform3D(bas, sc), Vector3(sw, sh, 0.18), brand)
	mb.box_xf("paint_metal", Transform3D(bas, sc + Vector3(0, -sh * 0.5 - 0.04, 0)), Vector3(sw + 0.1, 0.08, 0.22), Color(0.2, 0.2, 0.22))
	var txt_col := Color(1, 1, 1) if luminance(brand) < 0.62 else Color(0.08, 0.08, 0.1)
	label(out, str(b.name), sc + n3 * 0.1, yaw, sh * 0.6, txt_col, sw * 0.92, Color(0, 0, 0, 0.0), 220.0 if big else 140.0, true)
	# Glastuer
	var door: Vector2 = b.door
	var dbase := Vector3(door.x, GROUND, door.y)
	var dw := 1.9 if not big else 3.2
	mb.box_xf("paint_metal", Transform3D(bas, dbase + n3 * 0.05 + Vector3(0, 1.32, 0)), Vector3(dw + 0.16, 2.64, 0.1), Color(0.18, 0.18, 0.2))
	mb.box_xf("glass", Transform3D(bas, dbase + n3 * 0.09 + Vector3(0, 1.25, 0)), Vector3(dw, 2.45, 0.04), Color.WHITE)
	det.box_xf("chrome", Transform3D(bas, dbase + n3 * 0.13 + Vector3(0, 1.1, 0)), Vector3(0.04, 1.2, 0.04), Color.WHITE)
	# Markise
	var bt: String = b.business_type
	if bt in AWNING_TYPES and not big and rng.randf() < 0.85:
		var aw := minf(ln - 0.8, maxf(3.0, ln * 0.8))
		var top_y := GROUND + fh - 0.8
		var proj := 1.6
		var drop := 0.6
		var striped := rng.randf() < 0.55
		var nseg := maxi(1, int(aw / 0.5)) if striped else 1
		var seg_w := aw / nseg
		var cream := Color(0.95, 0.93, 0.86)
		var s := mb.surf("u_fabric")
		var m3 := Vector3(mid.x, 0, mid.y)
		for k in nseg:
			var u0 := -aw * 0.5 + k * seg_w
			var u1 := u0 + seg_w
			var col: Color = brand if (k % 2 == 0 or not striped) else cream
			var p0 := m3 + d3 * u0 + n3 * 0.05 + Vector3(0, top_y, 0)
			var p1 := m3 + d3 * u1 + n3 * 0.05 + Vector3(0, top_y, 0)
			var p2 := m3 + d3 * u1 + n3 * proj + Vector3(0, top_y - drop, 0)
			var p3 := m3 + d3 * u0 + n3 * proj + Vector3(0, top_y - drop, 0)
			var nrm := (n3 * drop + Vector3(0, proj, 0)).normalized()
			_quad_ccw_up(s, p3, p2, p1, p0, nrm, d3, Vector2(u0, 0), Vector2(u1, 0), Vector2(u1, -1.7), Vector2(u0, -1.7), col)
			_quad_ccw_up(s, p3, p2, p1, p0, -nrm, d3, Vector2(u0, 0), Vector2(u1, 0), Vector2(u1, -1.7), Vector2(u0, -1.7), col.darkened(0.25))
			mb.wall("u_fabric", Vector2(p3.x, p3.z), Vector2(p2.x, p2.z), top_y - drop - 0.25, top_y - drop, col, MeshBatch.NO_CUSTOM, u0)
		for e in [-1.0, 1.0]:
			var arm0 = Vector3(mid.x, top_y, mid.y) + d3 * (aw * 0.5 * e)
			_board(det, arm0 + n3 * 0.05, arm0 + n3 * proj + Vector3(0, -drop, 0), Color(0.2, 0.2, 0.2))
		if bt in ["cafe", "restaurant", "bar", "bakery"]:
			_terrace(city, b, fs, rng, det, out, brand)


static func _terrace(city: CityModel, b: Dictionary, fs: Dictionary, rng: RandomNumberGenerator, det: MeshBatch,
		out: Dictionary, brand: Color) -> void:
	var lot: Dictionary = city.lots[b.lot]
	var lr: Rect2 = lot.rect
	var sn: Vector2 = fs.n
	var dir: Vector2 = fs.dir
	var depth_avail := 0.0
	var probe: Vector2 = (fs.a + fs.c) * 0.5
	for k in 40:
		var q := probe + sn * (k * 0.25)
		if not lr.has_point(q):
			break
		depth_avail = k * 0.25
	if depth_avail < 2.2:
		return
	var ln: float = fs.len
	var u := -ln * 0.5 + 1.2
	var d3 := Vector3(dir.x, 0, dir.y)
	while u < ln * 0.5 - 1.0:
		var c2: Vector2 = (fs.a + fs.c) * 0.5 + dir * u + sn * minf(1.5, depth_avail - 0.7)
		if c2.distance_to(b.door + sn * 1.5) > 2.0:
			var c3 := Vector3(c2.x, GROUND, c2.y)
			det.cylinder("paint_metal", c3, 0.04, 0.04, 0.72, 6, Color(0.2, 0.2, 0.2), MeshBatch.NO_CUSTOM, false)
			det.cylinder("paint", c3 + Vector3(0, 0.72, 0), 0.38, 0.38, 0.03, 12, Color(0.92, 0.92, 0.9))
			for sgn in [-1.0, 1.0]:
				var ch = c3 + d3 * (0.6 * sgn)
				det.box("u_wood", ch + Vector3(0, 0.45, 0), Vector3(0.42, 0.05, 0.42), Color(0.6, 0.45, 0.3))
				det.box("u_wood", ch + d3 * (0.2 * sgn) + Vector3(0, 0.7, 0), Vector3(0.42 if dir.x == 0 else 0.04, 0.5, 0.04 if dir.x == 0 else 0.42), Color(0.6, 0.45, 0.3))
				det.box("paint_metal", ch + Vector3(0, 0.22, 0), Vector3(0.05, 0.44, 0.05), Color(0.2, 0.2, 0.2))
			if depth_avail > 3.0 and rng.randf() < 0.6:
				det.cylinder("paint_metal", c3 + Vector3(0, 0.75, 0), 0.025, 0.025, 1.6, 6, Color(0.85, 0.85, 0.85), MeshBatch.NO_CUSTOM, false)
				det.cylinder("u_fabric", c3 + Vector3(0, 2.0, 0), 1.25, 0.05, 0.45, 10, brand.lightened(0.15), MeshBatch.NO_CUSTOM, false)
		u += 2.6


# ============================================================ Hochhaus

static func _tower(city: CityModel, b: Dictionary, p: Dictionary, rng: RandomNumberGenerator, mb: MeshBatch,
		det: MeshBatch, out: Dictionary) -> void:
	var fp: Rect2 = b.footprint
	var floors: int = b.floors
	var fh: float = b.floor_h
	var tiers: Array = []
	var m := minf(fp.size.x, fp.size.y)
	if b.style.get("setback_top", false):
		var f1 := int(floors * 0.6)
		var f2 := int(floors * 0.84)
		tiers = [[0, f1, 0.0], [f1, f2, m * 0.13], [f2, floors, m * 0.26]]
	else:
		var pod := mini(3, floors - 1)
		tiers = [[0, pod, 0.0], [pod, floors, 1.4]]
	var col: Color = p.tint
	col.a = p.cond
	for ti in tiers.size():
		var t: Array = tiers[ti]
		var r := fp.grow(-float(t[2]))
		var y0 := GROUND + int(t[0]) * fh
		var y1 := GROUND + int(t[1]) * fh
		var cs := rect_corners(r)
		for i in 4:
			var a: Vector2 = cs[i]
			var c: Vector2 = cs[(i + 1) % 4]
			if ti == 0:
				band(mb, p.fac, a, c, -0.3, GROUND + fh, col, fh, 3.0, p.seed, flags(0, MODE_GLASS, 1), -0.3 - GROUND)
				if int(t[1]) > 1:
					band(mb, p.fac, a, c, GROUND + fh, y1, col, fh, p.bay, p.seed, flags(p.wstyle, p.mode, 1), fh)
			else:
				band(mb, p.fac, a, c, y0, y1, col, fh, p.bay, p.seed, flags(p.wstyle, p.mode, 1), int(t[0]) * fh)
		var is_top := ti == tiers.size() - 1
		var crown := 3.2 if is_top else 1.0
		for i in 4:
			band(mb, p.fac, cs[i], cs[(i + 1) % 4], y1, y1 + crown, col, fh, p.bay, p.seed,
				flags(0, MODE_WALL if not is_top else MODE_GLASS, 1), 0.0)
		mb.flat_rect("u_roof_flat", r.grow(-0.2), y1 + 0.3, Color(0.5, 0.5, 0.52))
		var ic := rect_corners(r.grow(-0.2))
		for i in 4:
			mb.wall("u_concrete", ic[(i + 1) % 4], ic[i], y1 + 0.3, y1 + crown, Color(0.6, 0.6, 0.6))
		ring(mb, "paint_metal", r, y1 + crown - 0.05, y1 + crown + 0.08, 0.04, 0.3, Color(0.55, 0.57, 0.6))
		if p.mode == MODE_CURTAIN and ti > 0:
			for i in 4:
				var a2: Vector2 = cs[i]
				var c2: Vector2 = cs[(i + 1) % 4]
				var ln := a2.distance_to(c2)
				var nf := int(ln / 4.5)
				var sn := side_normal(i)
				for k in range(1, nf):
					var q := a2.lerp(c2, float(k) / nf) + sn * 0.12
					mb.box("paint_metal", Vector3(q.x, (y0 + y1 + crown) * 0.5, q.y), Vector3(0.22 if sn.x == 0 else 0.25, y1 + crown - y0, 0.25 if sn.x == 0 else 0.22), Color(0.6, 0.62, 0.65))
		if is_top:
			var c3 := r.get_center()
			var mr := r.grow(-r.size.x * 0.25)
			if mr.size.x > 3 and mr.size.y > 3:
				mb.box("paint_metal", Vector3(c3.x, y1 + 0.3 + 1.5, c3.y), Vector3(mr.size.x, 3.0, mr.size.y), Color(0.45, 0.46, 0.48))
			if floors >= 30:
				var sp := 8.0 + floors * 0.4
				mb.cylinder("chrome", Vector3(c3.x, y1 + 3.3, c3.y), 0.5, 0.12, sp, 8, Color(0.8, 0.82, 0.85))
				det.box("lamp_glow", Vector3(c3.x, y1 + 3.3 + sp, c3.y), Vector3(0.35, 0.35, 0.35), Color(1.0, 0.15, 0.1))
		elif r.size.x - fp.grow(-float(tiers[ti + 1][2])).size.x > 2.0:
			ring(det, "glass_clear", r.grow(-0.1), y1 + crown, y1 + crown + 1.0, 0.0, 0.05, Color.WHITE)
	var fs := front_side(b)
	var sn2: Vector2 = fs.n
	var dc: Vector2 = b.door
	var bas := Basis(Vector3.UP, yaw_of(sn2))
	mb.box_xf("paint_metal", Transform3D(bas, Vector3(dc.x, GROUND + 3.6, dc.y) + Vector3(sn2.x, 0, sn2.y) * 1.8), Vector3(8.0, 0.25, 3.6), Color(0.3, 0.31, 0.33))
	det.box_xf("lamp_glow", Transform3D(bas, Vector3(dc.x, GROUND + 3.47, dc.y) + Vector3(sn2.x, 0, sn2.y) * 1.8), Vector3(6.0, 0.02, 2.6), Color(1.0, 0.93, 0.8))


# ============================================================ Bahnhof

static func _station(city: CityModel, b: Dictionary, p: Dictionary, rng: RandomNumberGenerator, mb: MeshBatch,
		det: MeshBatch, out: Dictionary) -> void:
	var fp: Rect2 = b.footprint
	var parts := _split_front(fp, b.front, 13.0)
	var head := b.duplicate()
	head.footprint = parts[0]
	var ph := p.duplicate()
	ph.roof = "flat"
	ph.cornice = true
	_standard(city, head, ph, rng, parts[0], mb, det, out)
	var hall := b.duplicate()
	hall.footprint = parts[1]
	hall.floors = 1
	hall.floor_h = 10.0
	hall.height = 10.0
	hall.front = _opposite(b.front)
	var pv := p.duplicate()
	pv.roof = "vault"
	pv.fac = "fac_brick"
	pv.tint = Color(0.8, 0.55, 0.45)
	pv.wstyle = 4
	pv.bay = 5.0
	pv.plinth = true
	_standard(city, hall, pv, rng, parts[1], mb, det, out)
	var fs := front_side(b)
	var sn: Vector2 = fs.n
	var mid: Vector2 = (fs.a + fs.c) * 0.5
	var yaw := yaw_of(sn)
	var top := GROUND + float(b.height)
	label(out, "Hauptbahnhof", Vector3(mid.x, top - 2.2, mid.y) + Vector3(sn.x, 0, sn.y) * 0.25, yaw, 1.5, Color(0.95, 0.95, 0.92), fs.len * 0.6, Color(0, 0, 0, 0.5), 400.0, true)
	mb.box_xf(p.fac, Transform3D(Basis(Vector3.UP, yaw), Vector3(mid.x, top + 2.0, mid.y) - Vector3(sn.x, 0, sn.y) * 0.3), Vector3(5.0, 4.6, 0.6), Color(0.86, 0.82, 0.74, p.cond), cus(3.0, 3.0, p.seed, flags(0, MODE_WALL, 0)))
	_clock(mb, det, Vector3(mid.x, top + 2.2, mid.y), sn, 1.6)


static func _opposite(f: String) -> String:
	return {"n": "s", "s": "n", "e": "w", "w": "e"}.get(f, "n")


static func _clock(mb: MeshBatch, det: MeshBatch, c: Vector3, sn: Vector2, r: float) -> void:
	var yaw := yaw_of(sn)
	var n3 := Vector3(sn.x, 0, sn.y)
	var s := mb.surf("window_glow")
	var seg := 24
	var right := Basis(Vector3.UP, yaw) * Vector3.RIGHT
	var face := Color(0.96, 0.94, 0.86)
	var ctr := s.vert(c + n3 * 0.12, n3, right, Vector2.ZERO, face, MeshBatch.NO_CUSTOM)
	var prev := -1
	for i in seg + 1:
		var a := TAU * i / seg
		var q := c + n3 * 0.12 + (right * cos(a) + Vector3.UP * sin(a)) * r
		var vi := s.vert(q, n3, right, Vector2(cos(a), sin(a)), face, MeshBatch.NO_CUSTOM)
		if prev >= 0:
			var geo := (s.v[prev] - s.v[ctr]).cross(q - s.v[ctr])
			if geo.dot(n3) > 0:
				s.tri_idx(ctr, prev, vi)
			else:
				s.tri_idx(ctr, vi, prev)
		prev = vi
	mb.box_xf("paint_metal", Transform3D(Basis(Vector3.UP, yaw), c + n3 * 0.06), Vector3(r * 2.2, r * 2.2, 0.1), Color(0.2, 0.2, 0.22))
	var hb := Basis(Vector3.UP, yaw)
	var b1 := hb * Basis(Vector3.BACK, 0.5)
	var b2 := hb * Basis(Vector3.BACK, -2.1)
	det.box_xf("paint", Transform3D(b1, c + n3 * 0.15 + (b1 * Vector3.UP) * r * 0.3), Vector3(0.1, r * 0.6, 0.03), Color(0.08, 0.08, 0.08))
	det.box_xf("paint", Transform3D(b2, c + n3 * 0.16 + (b2 * Vector3.UP) * r * 0.42), Vector3(0.07, r * 0.84, 0.03), Color(0.08, 0.08, 0.08))


# ============================================================ Sonderbauten

static func _city_hall_tower(city: CityModel, b: Dictionary, p: Dictionary, rng: RandomNumberGenerator, mb: MeshBatch,
		det: MeshBatch, out: Dictionary) -> void:
	var sn: Vector2 = b.front_dir
	var door: Vector2 = b.door
	var c := door - sn * 3.1
	var tr := Rect2(c - Vector2(3.5, 3.5), Vector2(7, 7))
	var top := GROUND + float(b.height) + 14.0
	var col: Color = p.tint
	col.a = p.cond
	var cs := rect_corners(tr)
	for i in 4:
		band(mb, p.fac, cs[i], cs[(i + 1) % 4], -0.3, top, col, 4.5, 3.5, p.seed, flags(4, MODE_WINDOWS, 1), -0.3 - GROUND)
	ring(mb, "u_stone", tr, top - 0.4, top, 0.25, 0.5, Color(0.85, 0.82, 0.75))
	var pp := p.duplicate()
	pp.roof_col = Color(0.32, 0.52, 0.46)
	var r2 := RandomNumberGenerator.new()
	r2.seed = 7
	_roof_pitched(mb, det, tr.grow(-0.2), top, "hip", pp, r2, {"kind": "tower_roof"})
	for i in 4:
		var n := side_normal(i)
		_clock(mb, det, Vector3(c.x + n.x * 3.5, top - 3.0, c.y + n.y * 3.5), n, 1.4)
	var right := Vector2(-sn.y, sn.x)
	for k in [-1.0, 1.0]:
		var fpos = door + sn * 8.0 + right * (6.0 * k)
		det.cylinder("chrome", Vector3(fpos.x, GROUND, fpos.y), 0.06, 0.05, 9.0, 6, Color.WHITE, MeshBatch.NO_CUSTOM)
		var fc := Color(0.15, 0.35, 0.65) if k < 0 else Color(0.85, 0.75, 0.2)
		det.box("u_fabric", Vector3(fpos.x, GROUND + 8.0, fpos.y) + Vector3(right.x, 0, right.y) * 0.9, Vector3(absf(right.x) * 1.8 + 0.02, 1.1, absf(right.y) * 1.8 + 0.02), fc)
		shape_box(out, Vector3(fpos.x, GROUND + 4.5, fpos.y), Vector3(0.15, 9.0, 0.15))
	p["door_off"] = 0.4
	label(out, str(b.name), Vector3(door.x, GROUND + 4.6, door.y) + Vector3(sn.x, 0, sn.y) * 0.55, yaw_of(sn), 0.7, Color(0.15, 0.13, 0.1), 7.0, Color(1, 1, 1, 0.0), 220.0)
	shape_box(out, Vector3(c.x, top * 0.5, c.y), Vector3(7, top, 7))


static func _portico(city: CityModel, b: Dictionary, p: Dictionary, rng: RandomNumberGenerator, mb: MeshBatch,
		det: MeshBatch, out: Dictionary, pediment: bool) -> void:
	var sn: Vector2 = b.front_dir
	var right := Vector2(-sn.y, sn.x)
	var r3 := Vector3(right.x, 0, right.y)
	var n3 := Vector3(sn.x, 0, sn.y)
	var door: Vector2 = b.door
	var d3 := Vector3(door.x, 0, door.y)
	var ncol := 8 if pediment else 6
	var spacing := 3.2
	var width := (ncol - 1) * spacing
	var fh: float = b.floor_h
	var ch := minf(fh * 2.0, float(b.height) - 0.5)
	var depth := 4.0
	var stone := Color(0.9, 0.88, 0.82)
	for k in ncol:
		var q := door + sn * (depth - 0.6) + right * (-width * 0.5 + k * spacing)
		mb.cylinder("u_stone", Vector3(q.x, GROUND + 0.6, q.y), 0.45, 0.4, ch - 0.6, 12, stone, MeshBatch.NO_CUSTOM, false)
		mb.box("u_stone", Vector3(q.x, GROUND + 0.75, q.y), Vector3(1.1, 0.3, 1.1), stone)
		mb.box("u_stone", Vector3(q.x, GROUND + ch - 0.12, q.y), Vector3(1.0, 0.24, 1.0), stone)
		shape_box(out, Vector3(q.x, GROUND + ch * 0.5, q.y), Vector3(0.9, ch, 0.9))
	var bas := Basis(Vector3.UP, yaw_of(sn))
	var ec := d3 + n3 * (depth * 0.5 - 0.3)
	mb.box_xf("u_stone", Transform3D(bas, ec + Vector3(0, GROUND + ch + 0.6, 0)), Vector3(width + 2.0, 1.2, depth + 0.6), stone.darkened(0.05))
	for st in 3:
		var sd := depth + 1.5 - st * 0.6
		mb.box_xf("u_stone", Transform3D(bas, d3 + n3 * (sd * 0.5) + Vector3(0, GROUND + 0.1 + st * 0.2, 0)), Vector3(width + 2.5 - st * 0.4, 0.2, sd), stone.darkened(0.08))
	if pediment:
		var s := mb.surf("u_stone")
		var base_y := GROUND + ch + 1.2
		var front := d3 + n3 * (depth - 0.3 + 0.3)
		var a := front - r3 * (width * 0.5 + 1.0) + Vector3(0, base_y, 0)
		var c := front + r3 * (width * 0.5 + 1.0) + Vector3(0, base_y, 0)
		var t := front + Vector3(0, base_y + 3.2, 0)
		_tri_ccw_up(s, a, c, t, n3, r3, Vector2(0, 0), Vector2(width, 0), Vector2(width * 0.5, -3.2), stone)
		var back := d3 - n3 * 0.5
		var a2 := back - r3 * (width * 0.5 + 1.0) + Vector3(0, base_y, 0)
		var c2 := back + r3 * (width * 0.5 + 1.0) + Vector3(0, base_y, 0)
		var t2 := back + Vector3(0, base_y + 3.2, 0)
		for pair in [[a, t, a2, t2], [t, c, t2, c2]]:
			var p0: Vector3 = pair[0]
			var p1: Vector3 = pair[1]
			var p2: Vector3 = pair[2]
			var p3: Vector3 = pair[3]
			var n := (p1 - p0).cross(p2 - p0).normalized()
			if n.y < 0:
				n = -n
			_quad_ccw_up(mb.surf("paint_metal"), p0, p1, p3, p2, n, (p1 - p0).normalized(), Vector2.ZERO, Vector2(1, 0), Vector2(1, -1), Vector2(0, -1), Color(0.36, 0.55, 0.48))
	label(out, str(b.name), ec + Vector3(0, GROUND + ch + 0.6, 0) + n3 * (depth * 0.5 + 0.32), yaw_of(sn), 0.6, Color(0.2, 0.18, 0.15), width, Color(1, 1, 1, 0), 220.0)


static func _civic_sign(city: CityModel, b: Dictionary, p: Dictionary, out: Dictionary) -> void:
	var sn: Vector2 = b.front_dir
	var door: Vector2 = b.door
	var fh: float = b.floor_h
	var y := GROUND + minf(fh + 0.5, float(b.height) - 0.6)
	if b.floors <= 1:
		y = GROUND + 3.0
	label(out, str(b.name), Vector3(door.x, y, door.y) + Vector3(sn.x, 0, sn.y) * 0.08, yaw_of(sn), 0.45, Color(0.12, 0.12, 0.14), 9.0, Color(1, 1, 1, 0.55), 150.0)


static func _fire_station(city: CityModel, b: Dictionary, p: Dictionary, rng: RandomNumberGenerator, mb: MeshBatch,
		det: MeshBatch, out: Dictionary) -> void:
	var fs := front_side(b)
	var sn: Vector2 = fs.n
	var n3 := Vector3(sn.x, 0, sn.y)
	var dir: Vector2 = fs.dir
	var bas := Basis(Vector3.UP, yaw_of(sn))
	var ln: float = fs.len
	var gw := 3.9
	var n := clampi(int((ln - 4.0) / (gw + 1.0)), 2, 4)
	var u_door = (b.door - fs.a).dot(dir)
	var placed := 0
	var u := 1.0 + gw * 0.5
	while u < ln - gw * 0.5 - 0.5 and placed < n:
		if absf(u - u_door) > gw * 0.5 + 1.2:
			var q: Vector2 = fs.a + dir * u
			mb.box_xf("paint", Transform3D(bas, Vector3(q.x, GROUND + 2.1, q.y) + n3 * 0.06), Vector3(gw + 0.3, 4.3, 0.12), Color(0.9, 0.9, 0.88))
			mb.box_xf("u_metal", Transform3D(bas * Basis(Vector3.BACK, PI * 0.5), Vector3(q.x, GROUND + 2.0, q.y) + n3 * 0.11), Vector3(4.0, gw, 0.05), Color(0.78, 0.12, 0.10))
			label(out, str(placed + 1), Vector3(q.x, GROUND + 4.45, q.y) + n3 * 0.14, yaw_of(sn), 0.35, Color(0.1, 0.1, 0.1), 1.0, Color(1, 1, 1, 0), 80.0)
			placed += 1
			u += gw + 1.0
		else:
			u += 0.5
	var mid: Vector2 = (fs.a + fs.c) * 0.5
	var fh: float = b.floor_h
	var sw := minf(ln * 0.5, 9.0)
	mb.box_xf("sign_glow", Transform3D(bas, Vector3(mid.x, GROUND + fh + 0.7, mid.y) + n3 * 0.1), Vector3(sw, 0.8, 0.14), Color(0.8, 0.1, 0.08))
	label(out, "FEUERWEHR", Vector3(mid.x, GROUND + fh + 0.7, mid.y) + n3 * 0.19, yaw_of(sn), 0.55, Color(1, 1, 1), sw * 0.9, Color(0, 0, 0, 0), 200.0, true)
	var fp: Rect2 = b.footprint
	var back := fp.get_center() - sn * (minf(fp.size.x, fp.size.y) * 0.5 - 2.2) + Vector2(-sn.y, sn.x) * (maxf(fp.size.x, fp.size.y) * 0.5 - 2.2)
	if fp.grow(-1.0).has_point(back):
		var tr := Rect2(back - Vector2(2, 2), Vector2(4, 4))
		var th := GROUND + float(b.height) + 9.0
		var col := Color(0.75, 0.3, 0.25, p.cond)
		var cs := rect_corners(tr)
		for i in 4:
			band(mb, "fac_brick", cs[i], cs[(i + 1) % 4], -0.3, th, col, 3.0, 2.0, p.seed, flags(5, MODE_WINDOWS, 1), -0.3 - GROUND)
		mb.flat_rect("u_roof_flat", tr.grow(0.2), th, Color(0.4, 0.4, 0.4))
		shape_box(out, Vector3(back.x, th * 0.5, back.y), Vector3(4, th, 4))


static func _police(city: CityModel, b: Dictionary, p: Dictionary, rng: RandomNumberGenerator, mb: MeshBatch,
		det: MeshBatch, out: Dictionary) -> void:
	var fp: Rect2 = b.footprint
	var fh: float = b.floor_h
	ring(mb, "paint", fp, GROUND + fh - 0.32, GROUND + fh - 0.02, 0.05, 0.2, Color(0.12, 0.32, 0.68))
	var sn: Vector2 = b.front_dir
	var n3 := Vector3(sn.x, 0, sn.y)
	var door: Vector2 = b.door
	var bas := Basis(Vector3.UP, yaw_of(sn))
	var c := Vector3(door.x, GROUND + fh + 0.55, door.y) + n3 * 0.12
	mb.box_xf("sign_glow", Transform3D(bas, c), Vector3(4.2, 0.7, 0.14), Color(0.08, 0.28, 0.62))
	label(out, "POLIZEI", c + n3 * 0.08, yaw_of(sn), 0.5, Color(1, 1, 1), 3.8, Color(0, 0, 0, 0), 220.0, true)
	det.box_xf("neon", Transform3D(bas, c + bas * Vector3(2.4, 0, 0)), Vector3(0.3, 0.3, 0.3), Color(0.2, 0.4, 1.0))
	var right := Vector2(-sn.y, sn.x)
	var q := door + right * 2.4
	label(out, str(b.name), Vector3(q.x, GROUND + 1.9, q.y) + n3 * 0.06, yaw_of(sn), 0.16, Color(0.1, 0.1, 0.12), 2.0, Color(1, 1, 1, 0.8), 40.0)


static func _hospital(city: CityModel, b: Dictionary, p: Dictionary, rng: RandomNumberGenerator, mb: MeshBatch,
		det: MeshBatch, out: Dictionary) -> void:
	var fp: Rect2 = b.footprint
	var top := GROUND + float(b.height)
	var sn: Vector2 = b.front_dir
	var n3 := Vector3(sn.x, 0, sn.y)
	var fs := front_side(b)
	var mid: Vector2 = (fs.a + fs.c) * 0.5
	var yaw := yaw_of(sn)
	var hs := Vector3(fs.a.x, 0, fs.a.y) + Vector3(fs.dir.x, 0, fs.dir.y) * 3.0 + n3 * 0.08 + Vector3(0, top - 2.6, 0)
	sign_quad(mb, hs, yaw, Vector2(2.6, 2.6), 4, false)
	label(out, str(b.name), Vector3(mid.x, top - 2.6, mid.y) + n3 * 0.1, yaw, 1.1, Color(0.1, 0.3, 0.62), fs.len * 0.55, Color(1, 1, 1, 0.9), 350.0)
	# Hubschrauberlandeplatz auf dem Dach
	var c := fp.get_center() - sn * (minf(fp.size.x, fp.size.y) * 0.18)
	var deck := top - 1.2 + 0.25 + 0.02
	var hp := Rect2(c - Vector2(7, 7), Vector2(14, 14))
	if fp.grow(-1.0).encloses(hp):
		det.box("paint_rough", Vector3(c.x, deck + 0.1, c.y), Vector3(14, 0.2, 14), Color(0.25, 0.27, 0.28))
		for i in 24:
			var a := TAU * i / 24.0
			var q := c + Vector2(cos(a), sin(a)) * 5.2
			det.box_rot("paint", Vector3(q.x, deck + 0.21, q.y), Vector3(1.3, 0.02, 0.3), -a + PI * 0.5, Color(0.95, 0.8, 0.1))
		det.box("paint", Vector3(c.x - 1.2, deck + 0.21, c.y), Vector3(0.45, 0.02, 3.6), Color(0.96, 0.96, 0.96))
		det.box("paint", Vector3(c.x + 1.2, deck + 0.21, c.y), Vector3(0.45, 0.02, 3.6), Color(0.96, 0.96, 0.96))
		det.box("paint", Vector3(c.x, deck + 0.21, c.y), Vector3(2.0, 0.02, 0.45), Color(0.96, 0.96, 0.96))
	# Notaufnahme mit Vordach
	var right := Vector2(-sn.y, sn.x)
	var r3 := Vector3(right.x, 0, right.y)
	var ec = Vector3(mid.x, 0, mid.y) + r3 * (fs.len * 0.5 - 5.0) + n3 * 3.0
	var bas := Basis(Vector3.UP, yaw)
	mb.box_xf("paint_metal", Transform3D(bas, ec + Vector3(0, GROUND + 4.2, 0)), Vector3(7.5, 0.35, 6.0), Color(0.85, 0.86, 0.88))
	for k in [-1.0, 1.0]:
		var pc = ec + r3 * (3.4 * k) + n3 * 2.7
		mb.box("paint_metal", pc + Vector3(0, GROUND + 2.0, 0), Vector3(0.25, 4.0, 0.25), Color(0.85, 0.86, 0.88))
		shape_box(out, pc + Vector3(0, GROUND + 2.0, 0), Vector3(0.3, 4.0, 0.3))
	mb.box_xf("sign_glow", Transform3D(bas, ec + Vector3(0, GROUND + 4.75, 0) + n3 * 3.0), Vector3(5.0, 0.6, 0.12), Color(0.85, 0.12, 0.1))
	label(out, "Notaufnahme", ec + Vector3(0, GROUND + 4.75, 0) + n3 * 3.07, yaw, 0.42, Color(1, 1, 1), 4.6, Color(0, 0, 0, 0), 200.0, true)
	det.box_xf("lamp_glow", Transform3D(bas, ec + Vector3(0, GROUND + 4.0, 0)), Vector3(5.0, 0.03, 3.0), Color(1.0, 0.97, 0.9))


static func _gas_station(city: CityModel, b: Dictionary, p: Dictionary, rng: RandomNumberGenerator, mb: MeshBatch,
		det: MeshBatch, out: Dictionary) -> void:
	var fs := front_side(b)
	var sn: Vector2 = fs.n
	var n3 := Vector3(sn.x, 0, sn.y)
	var right := Vector2(-sn.y, sn.x)
	var mid: Vector2 = (fs.a + fs.c) * 0.5
	var c := mid + sn * 7.2
	var yaw := yaw_of(sn)
	var bas := Basis(Vector3.UP, yaw)
	var cw := minf(fs.len + 8.0, 20.0)
	var cd := 9.0
	var brand: Color = b.brand_color
	var c3 := Vector3(c.x, 0, c.y)
	mb.box_xf("paint", Transform3D(bas, c3 + Vector3(0, GROUND + 5.2, 0)), Vector3(cw, 0.5, cd), Color(0.92, 0.92, 0.92))
	mb.box_xf("sign_glow", Transform3D(bas, c3 + Vector3(0, GROUND + 5.3, 0)), Vector3(cw + 0.1, 0.5, cd + 0.1), brand)
	det.box_xf("lamp_glow", Transform3D(bas, c3 + Vector3(0, GROUND + 4.94, 0)), Vector3(cw - 2.0, 0.02, cd - 2.0), Color(1.0, 0.98, 0.92))
	for k in 4:
		var u := -cw * 0.3 + (cw * 0.6) * (k % 2)
		var v := -cd * 0.28 + cd * 0.56 * (k / 2)
		var pc := c + right * u + sn * v
		mb.box("paint", Vector3(pc.x, GROUND + 2.5, pc.y), Vector3(0.45, 5.0, 0.45), Color(0.9, 0.9, 0.9))
		shape_box(out, Vector3(pc.x, GROUND + 2.5, pc.y), Vector3(0.45, 5.0, 0.45))
	for row in [-1.0, 1.0]:
		var ic = c + sn * (row * 2.3)
		mb.box_xf("u_concrete", Transform3D(bas, Vector3(ic.x, GROUND + 0.1, ic.y)), Vector3(5.0, 0.2, 1.0), Color(0.8, 0.8, 0.78))
		for k in [-1.0, 1.0]:
			var pp = ic + right * (1.4 * k)
			mb.box_xf("paint", Transform3D(bas, Vector3(pp.x, GROUND + 1.05, pp.y)), Vector3(0.8, 1.7, 0.5), Color(0.92, 0.92, 0.92))
			mb.box_xf("sign_glow", Transform3D(bas, Vector3(pp.x, GROUND + 1.7, pp.y)), Vector3(0.82, 0.25, 0.52), brand)
			det.box_xf("window_glow", Transform3D(bas, Vector3(pp.x, GROUND + 1.25, pp.y) + n3 * 0.26), Vector3(0.4, 0.3, 0.02), Color(0.6, 0.85, 1.0))
			shape_box(out, Vector3(pp.x, GROUND + 0.85, pp.y), Vector3(0.8, 1.7, 0.5), yaw)
	out["lights"].append({"pos": c3 + Vector3(0, GROUND + 4.8, 0), "kind": "canopy"})
	var lot: Dictionary = city.lots[b.lot]
	var lr: Rect2 = lot.rect
	var pm := c + sn * 6.0 + right * (cw * 0.5 + 1.0)
	if lr.grow(-0.5).has_point(pm):
		mb.box_xf("paint", Transform3D(bas, Vector3(pm.x, GROUND + 3.0, pm.y)), Vector3(1.6, 6.0, 0.4), Color(0.95, 0.95, 0.95))
		mb.box_xf("sign_glow", Transform3D(bas, Vector3(pm.x, GROUND + 5.2, pm.y)), Vector3(1.7, 1.2, 0.45), brand)
		label(out, str(b.name), Vector3(pm.x, GROUND + 5.2, pm.y) + n3 * 0.24, yaw, 0.3, Color(1, 1, 1), 1.5, Color(0, 0, 0, 0), 200.0, true)
		label(out, "Super  1,79\nDiesel 1,69", Vector3(pm.x, GROUND + 3.6, pm.y) + n3 * 0.21, yaw, 0.22, Color(0.1, 0.1, 0.1), 1.4, Color(1, 1, 1, 0), 60.0)
		shape_box(out, Vector3(pm.x, GROUND + 3.0, pm.y), Vector3(1.6, 6.0, 0.4), yaw)


static func _industrial(city: CityModel, b: Dictionary, p: Dictionary, rng: RandomNumberGenerator, mb: MeshBatch,
		det: MeshBatch, out: Dictionary) -> void:
	var fs := front_side(b)
	var sn: Vector2 = fs.n
	var n3 := Vector3(sn.x, 0, sn.y)
	var dir: Vector2 = fs.dir
	var bas := Basis(Vector3.UP, yaw_of(sn))
	var ln: float = fs.len
	var gw := 4.6 if b.kind != "bus_depot" else 4.4
	var n := clampi(int(ln / 9.0), 1, 6) if b.kind != "bus_depot" else clampi(int(ln / 6.0), 2, 8)
	var u_door = (b.door - fs.a).dot(dir)
	var stp := ln / (n + 1)
	var dock: bool = b.kind == "warehouse"
	var dcol: Color = [Color(0.62, 0.64, 0.66), Color(0.2, 0.36, 0.55), Color(0.85, 0.55, 0.15), Color(0.55, 0.57, 0.6)][int(p.seed * 4.0) % 4]
	for k in n:
		var u := stp * (k + 1)
		if absf(u - u_door) < gw * 0.5 + 1.0:
			continue
		var q: Vector2 = fs.a + dir * u
		var gh := 5.0
		mb.box_xf("paint", Transform3D(bas, Vector3(q.x, GROUND + gh * 0.5, q.y) + n3 * 0.05), Vector3(gw + 0.4, gh + 0.2, 0.1), Color(0.3, 0.32, 0.34))
		mb.box_xf("u_metal", Transform3D(bas * Basis(Vector3.BACK, PI * 0.5), Vector3(q.x, GROUND + gh * 0.5 - 0.05, q.y) + n3 * 0.1), Vector3(gh, gw, 0.04), dcol)
		if dock:
			mb.box_xf("u_concrete", Transform3D(bas, Vector3(q.x, GROUND + 0.5, q.y) + n3 * 1.25), Vector3(gw + 1.0, 1.0, 2.4), Color(0.7, 0.7, 0.68))
			det.box_xf("paint", Transform3D(bas, Vector3(q.x, GROUND + 0.45, q.y) + n3 * 2.47), Vector3(gw * 0.8, 0.5, 0.08), Color(0.1, 0.1, 0.1))
			shape_box(out, Vector3(q.x, GROUND + 0.5, q.y) + n3 * 1.25, Vector3(gw + 1.0, 1.0, 2.4), yaw_of(sn))
		det.box_xf("lamp_glow", Transform3D(bas, Vector3(q.x, GROUND + gh + 0.6, q.y) + n3 * 0.25), Vector3(0.5, 0.15, 0.3), Color(1.0, 0.9, 0.7))
	var fp: Rect2 = b.footprint
	var top := GROUND + float(b.height)
	if b.kind == "factory":
		var nchim := 1 + int(p.seed * 2.5)
		for k in nchim:
			var q2 := fp.get_center() - sn * (minf(fp.size.x, fp.size.y) * 0.3) + Vector2(-sn.y, sn.x) * (fp.size.x * 0.25 * (k - 0.5 * (nchim - 1)))
			var ch := rng.randf_range(24.0, 38.0)
			var r := rng.randf_range(0.9, 1.4)
			mb.cylinder("u_brick", Vector3(q2.x, top - 1.0, q2.y), r * 1.2, r, ch, 12, Color(0.7, 0.42, 0.34), MeshBatch.NO_CUSTOM, true)
			for band_i in 2:
				var by := top - 1.0 + ch - 3.0 - band_i * 3.0
				mb.cylinder("paint", Vector3(q2.x, by, q2.y), r * 1.03, r * 1.03, 1.2, 12, Color(0.85, 0.15, 0.12) if band_i == 0 else Color(0.95, 0.95, 0.95), MeshBatch.NO_CUSTOM, false)
			shape_box(out, Vector3(q2.x, (top + ch) * 0.5, q2.y), Vector3(r * 2.2, top + ch, r * 2.2))
		var a2: Vector2 = fs.a - sn * 2.5
		var c2: Vector2 = fs.c - sn * 2.5
		var mid2 := (a2 + c2) * 0.5
		det.box_xf("paint_metal", Transform3D(bas, Vector3(mid2.x, top + 0.6, mid2.y)), Vector3(ln * 0.8, 0.5, 0.5), Color(0.55, 0.5, 0.42))
	if b.name != "":
		var mid: Vector2 = (fs.a + fs.c) * 0.5
		label(out, str(b.name), Vector3(mid.x, top - 1.6, mid.y) + n3 * 0.08, yaw_of(sn), 0.9, Color(0.95, 0.95, 0.95), ln * 0.6, Color(0, 0, 0, 0.6), 250.0)


static func _house_extras(city: CityModel, b: Dictionary, p: Dictionary, rng: RandomNumberGenerator, mb: MeshBatch,
		det: MeshBatch, out: Dictionary) -> void:
	var dw: Rect2 = b.driveway
	var sn: Vector2 = b.front_dir
	if dw.has_area() and rng.randf() < 0.65:
		var glen := 6.0
		var garage := Rect2()
		if sn.y != 0.0:
			if dw.size.y >= glen + 3.0:
				garage = Rect2(dw.position.x, (dw.position.y if sn.y > 0 else dw.end.y - glen), dw.size.x, glen)
		elif dw.size.x >= glen + 3.0:
			garage = Rect2((dw.position.x if sn.x > 0 else dw.end.x - glen), dw.position.y, glen, dw.size.y)
		if garage.has_area():
			garage = garage.grow(-0.15)
			var col: Color = p.tint
			col.a = p.cond
			var cs := rect_corners(garage)
			for i in 4:
				mb.wall(p.fac, cs[i], cs[(i + 1) % 4], -0.3, GROUND + 2.7, col, cus(3.0, 3.0, p.seed, flags(0, MODE_WALL, 0)))
			mb.flat_rect("u_roof_flat", garage.grow(0.15), GROUND + 2.86, Color(0.4, 0.4, 0.42))
			ring(mb, "paint", garage, GROUND + 2.6, GROUND + 2.85, 0.15, 0.2, Color(0.9, 0.9, 0.88))
			var half := garage.size.y * 0.5 if sn.y != 0.0 else garage.size.x * 0.5
			var fc := garage.get_center() + sn * half
			var bas := Basis(Vector3.UP, yaw_of(sn))
			mb.box_xf("u_metal", Transform3D(bas * Basis(Vector3.BACK, PI * 0.5), Vector3(fc.x, GROUND + 1.15, fc.y) + Vector3(sn.x, 0, sn.y) * 0.03), Vector3(2.2, 2.5, 0.04), Color(0.88, 0.88, 0.86))
			shape_box(out, Vector3(garage.get_center().x, 1.4, garage.get_center().y), Vector3(garage.size.x, 2.8, garage.size.y))
	var lot: Dictionary = city.lots[b.lot]
	var lr: Rect2 = lot.rect
	var fp: Rect2 = b.footprint
	var half_d := fp.size.y * 0.5 if sn.y != 0.0 else fp.size.x * 0.5
	var back_center := fp.get_center() - sn * (half_d + 3.6)
	var right := Vector2(-sn.y, sn.x)
	var lot_w := lr.size.x if sn.y != 0.0 else lr.size.y
	var shed_c := back_center + right * (lot_w * 0.3)
	var shed := Rect2(shed_c - Vector2(1.2, 1.2), Vector2(2.4, 2.4))
	if lr.grow(-0.8).encloses(shed) and not shed.grow(0.5).intersects(fp) and rng.randf() < 0.5:
		det.box("u_wood", Vector3(shed_c.x, GROUND + 1.05, shed_c.y), Vector3(2.4, 2.1, 2.4), Color(0.62, 0.48, 0.34))
		det.box("u_roof_flat", Vector3(shed_c.x, GROUND + 2.16, shed_c.y), Vector3(2.8, 0.12, 2.8), Color(0.3, 0.3, 0.32))
		shape_box(out, Vector3(shed_c.x, GROUND + 1.05, shed_c.y), Vector3(2.4, 2.1, 2.4))


static func _pool(b: Dictionary, mb: MeshBatch, det: MeshBatch) -> void:
	var pr: Rect2 = b.style.pool
	if not pr.has_area():
		return
	mb.box("u_wood", Vector3(pr.get_center().x, GROUND + 0.02, pr.get_center().y), Vector3(pr.size.x + 4.0, 0.06, pr.size.y + 4.0), Color(0.62, 0.48, 0.36))
	ring(mb, "u_stone", pr, GROUND + 0.0, GROUND + 0.12, 0.3, 0.35, Color(0.95, 0.94, 0.9))
	mb.flat_rect("pool_water", pr, GROUND + 0.08)
	for k in 2:
		var q := Vector2(pr.end.x + 1.2, pr.position.y + 1.0 + k * 1.6)
		var sz := Vector3(1.9, 0.12, 0.7)
		if pr.size.x < pr.size.y:
			q = Vector2(pr.position.x + 1.0 + k * 1.6, pr.end.y + 1.2)
			sz = Vector3(0.7, 0.12, 1.9)
		det.box("paint", Vector3(q.x, GROUND + 0.3, q.y), sz, Color(0.95, 0.95, 0.95))


static func _balconies(city: CityModel, b: Dictionary, p: Dictionary, rng: RandomNumberGenerator, det: MeshBatch) -> void:
	var fp: Rect2 = b.footprint
	var fh: float = b.floor_h
	var floors: int = b.floors
	var cs := rect_corners(fp)
	var front_i := side_index(b.front)
	var glass := rng.randf() < 0.5
	var every := 1 if b.kind == "highrise_res" else 2
	var phase := rng.randi_range(0, 1)
	var rail_col := Color(0.25, 0.26, 0.28) if rng.randf() < 0.5 else Color(0.92, 0.92, 0.9)
	for i in [front_i, (front_i + 2) % 4]:
		var a: Vector2 = cs[i]
		var c: Vector2 = cs[(i + 1) % 4]
		var ln := a.distance_to(c)
		var nb := maxi(1, roundi(ln / float(p.bay)))
		var bay := ln / nb
		var dir := (c - a) / ln
		var d3 := Vector3(dir.x, 0, dir.y)
		var sn := side_normal(i)
		var n3 := Vector3(sn.x, 0, sn.y)
		var bas := Basis(Vector3.UP, yaw_of(sn))
		for f in range(1, floors):
			for k in nb:
				if (k + phase) % every != 0:
					continue
				var q := a + dir * (bay * (k + 0.5))
				var y := GROUND + f * fh
				var w := bay * 0.82
				var base := Vector3(q.x, y, q.y) + n3 * 0.65
				det.box_xf("u_concrete", Transform3D(bas, base), Vector3(w, 0.18, 1.3), Color(0.85, 0.85, 0.83))
				if glass:
					det.box_xf("glass_clear", Transform3D(bas, base + n3 * 0.62 + Vector3(0, 0.55, 0)), Vector3(w, 0.95, 0.03), Color.WHITE)
				else:
					det.box_xf("paint", Transform3D(bas, base + n3 * 0.62 + Vector3(0, 0.45, 0)), Vector3(w, 0.75, 0.04), rail_col)
				det.box_xf("paint_metal", Transform3D(bas, base + n3 * 0.62 + Vector3(0, 1.05, 0)), Vector3(w, 0.05, 0.06), Color(0.3, 0.3, 0.32))
				for e in [-1.0, 1.0]:
					var sp = base + d3 * (w * 0.5 * e) + Vector3(0, 0.55, 0)
					det.box_xf("glass_clear" if glass else "paint", Transform3D(bas, sp), Vector3(0.03, 0.95, 1.25), rail_col)


# ============================================================ Freiluft

static func _market_square(city: CityModel, b: Dictionary, rng: RandomNumberGenerator, mb: MeshBatch, det: MeshBatch,
		out: Dictionary) -> void:
	var fp: Rect2 = b.footprint
	mb.flat_rect("g_plaza_ov", fp, GROUND + 0.004)
	var goods := [Color(0.85, 0.2, 0.15), Color(0.95, 0.65, 0.1), Color(0.35, 0.65, 0.2), Color(0.9, 0.85, 0.3), Color(0.6, 0.3, 0.5), Color(0.75, 0.55, 0.35)]
	for st in b.style.get("stalls", []):
		var pos: Vector2 = st.pos
		var sz: Vector2 = st.size
		var yaw: float = st.rot
		var col: Color = STALL_COLORS[int(st.color_idx) % STALL_COLORS.size()]
		var bas := Basis(Vector3.UP, yaw)
		var c3 := Vector3(pos.x, GROUND, pos.y)
		var hx := sz.x * 0.5
		var hz := sz.y * 0.5
		for cx in [-1.0, 1.0]:
			for cz in [-1.0, 1.0]:
				var pp := c3 + bas * Vector3(cx * (hx - 0.1), 0, cz * (hz - 0.1))
				var ph := 2.4 + (0.35 if cz < 0 else 0.0)
				mb.box("paint_metal", pp + Vector3(0, ph * 0.5, 0), Vector3(0.07, ph, 0.07), Color(0.35, 0.35, 0.36))
		var roof_b := bas * Basis(Vector3.RIGHT, 0.12)
		mb.box_xf("u_fabric", Transform3D(roof_b, c3 + Vector3(0, 2.62, 0)), Vector3(sz.x + 0.3, 0.05, sz.y + 0.4), col)
		var stripes := maxi(2, int(sz.x / 0.55))
		for k in stripes:
			if k % 2 == 1:
				continue
			var off := -sz.x * 0.5 + (k + 0.5) * (sz.x / stripes)
			det.box_xf("u_fabric", Transform3D(roof_b, c3 + Vector3(0, 2.62, 0) + roof_b * Vector3(off, 0.035, 0)), Vector3(sz.x / stripes, 0.02, sz.y + 0.4), Color(0.96, 0.95, 0.9))
		mb.box_xf("u_fabric", Transform3D(bas, c3 + bas * Vector3(0, 0, hz + 0.2) + Vector3(0, 2.32, 0)), Vector3(sz.x + 0.3, 0.3, 0.03), col.darkened(0.15))
		mb.box_xf("u_wood", Transform3D(bas, c3 + bas * Vector3(0, 0, hz * 0.4) + Vector3(0, 0.85, 0)), Vector3(sz.x - 0.2, 0.08, sz.y * 0.45), Color(0.6, 0.45, 0.3))
		mb.box_xf("u_wood", Transform3D(bas, c3 + bas * Vector3(0, 0, hz * 0.62) + Vector3(0, 0.42, 0)), Vector3(sz.x - 0.2, 0.8, 0.04), Color(0.55, 0.4, 0.28))
		for g in int(sz.x / 0.5):
			var gx := -sz.x * 0.5 + 0.35 + g * 0.5
			var gc: Color = goods[rng.randi_range(0, goods.size() - 1)]
			det.box_xf("paint_rough", Transform3D(bas, c3 + bas * Vector3(gx, 0, hz * 0.4) + Vector3(0, 0.97, 0)), Vector3(0.42, 0.16, sz.y * 0.36), gc)
		shape_box(out, c3 + bas * Vector3(0, 0, hz * 0.4) + Vector3(0, 0.45, 0), Vector3(sz.x, 0.9, sz.y * 0.45), yaw)
		out["lights"].append({"pos": c3 + Vector3(0, 2.3, 0), "kind": "stall"})
		det.box_xf("lamp_glow", Transform3D(bas, c3 + Vector3(0, 2.38, 0)), Vector3(sz.x * 0.6, 0.04, 0.1), Color(1.0, 0.85, 0.55))
	var sn: Vector2 = b.front_dir
	var right := Vector2(-sn.y, sn.x)
	var fs := front_side(b)
	var mid: Vector2 = (fs.a + fs.c) * 0.5 - sn * 0.6
	var bas2 := Basis(Vector3.UP, yaw_of(sn))
	var pw := minf(fs.len * 0.5, 9.0)
	for k in [-1.0, 1.0]:
		var pp = mid + right * (pw * 0.5 * k)
		mb.box("paint_metal", Vector3(pp.x, GROUND + 2.0, pp.y), Vector3(0.2, 4.0, 0.2), Color(0.2, 0.2, 0.22))
		shape_box(out, Vector3(pp.x, GROUND + 2.0, pp.y), Vector3(0.2, 4.0, 0.2))
	var brand: Color = b.brand_color
	var tc := Color(1, 1, 1) if luminance(brand) < 0.6 else Color(0.1, 0.1, 0.1)
	mb.box_xf("sign_glow", Transform3D(bas2, Vector3(mid.x, GROUND + 4.2, mid.y)), Vector3(pw + 0.4, 0.9, 0.15), brand)
	label(out, str(b.name), Vector3(mid.x, GROUND + 4.2, mid.y) + Vector3(sn.x, 0, sn.y) * 0.09, yaw_of(sn), 0.55, tc, pw * 0.95, Color(0, 0, 0, 0), 220.0, true)
	label(out, str(b.name), Vector3(mid.x, GROUND + 4.2, mid.y) - Vector3(sn.x, 0, sn.y) * 0.09, yaw_of(-sn), 0.55, tc, pw * 0.95, Color(0, 0, 0, 0), 220.0, true)


static func _camp(city: CityModel, b: Dictionary, rng: RandomNumberGenerator, mb: MeshBatch, det: MeshBatch, out: Dictionary) -> void:
	var fp: Rect2 = b.footprint
	mb.flat_rect("g_dirt_ov", fp, GROUND + 0.004)
	for t in b.style.get("tents", []):
		var pos: Vector2 = t.pos
		var yaw: float = t.rot
		var col: Color = TENT_COLORS[int(t.color_idx) % TENT_COLORS.size()]
		var bas := Basis(Vector3.UP, yaw)
		var c3 := Vector3(pos.x, GROUND, pos.y)
		var hw := 0.85
		var h := 1.15
		var L := 2.2
		var s := mb.surf("u_fabric")
		for sg in [-1.0, 1.0]:
			var p0 := c3 + bas * Vector3(sg * hw, 0, -L * 0.5)
			var p1 := c3 + bas * Vector3(sg * hw, 0, L * 0.5)
			var p2 := c3 + bas * Vector3(0, h, L * 0.5)
			var p3 := c3 + bas * Vector3(0, h, -L * 0.5)
			var n := (bas * Vector3(sg * h, hw, 0)).normalized()
			_quad_ccw_up(s, p0, p1, p2, p3, n, (p1 - p0).normalized(), Vector2(0, 0), Vector2(L, 0), Vector2(L, -1.4), Vector2(0, -1.4), col)
		for e in [-1.0, 1.0]:
			var q0 := c3 + bas * Vector3(-hw, 0, e * L * 0.5)
			var q1 := c3 + bas * Vector3(hw, 0, e * L * 0.5)
			var q2 := c3 + bas * Vector3(0, h, e * L * 0.5)
			_tri_ccw_up(s, q0, q1, q2, bas * Vector3(0, 0, e), (q1 - q0).normalized(), Vector2(0, 0), Vector2(1.7, 0), Vector2(0.85, -1.2), col.darkened(0.2 if e > 0 else 0.0))
		shape_box(out, c3 + Vector3(0, 0.6, 0), Vector3(1.7, 1.2, 2.2), yaw)
	for k in rng.randi_range(3, 6):
		var q := Vector2(rng.randf_range(fp.position.x + 1, fp.end.x - 1), rng.randf_range(fp.position.y + 1, fp.end.y - 1))
		var kind := rng.randi_range(0, 2)
		if kind == 0:
			det.box_rot("u_wood", Vector3(q.x, GROUND + 0.25, q.y), Vector3(0.6, 0.5, 0.45), rng.randf() * TAU, Color(0.6, 0.48, 0.32))
		elif kind == 1:
			det.box_rot("paint_rough", Vector3(q.x, GROUND + 0.3, q.y), Vector3(1.1, 0.6, 0.7), rng.randf() * TAU, Color(0.3, 0.4, 0.55))
		else:
			det.cylinder("paint_metal", Vector3(q.x, GROUND, q.y), 0.3, 0.3, 0.85, 10, Color(0.45, 0.25, 0.15))
			det.cylinder("lamp_glow", Vector3(q.x, GROUND + 0.85, q.y), 0.26, 0.15, 0.25, 8, Color(1.0, 0.45, 0.1))
			out["lights"].append({"pos": Vector3(q.x, GROUND + 1.2, q.y), "kind": "fire"})


# ============================================================ Fernansicht

## Vereinfachte Huelle fuer die Fernansicht (gleiche Fassadenmaterialien).
static func build_far(city: CityModel, b: Dictionary, mb: MeshBatch) -> void:
	if b.floors <= 0:
		return
	var rng := make_rng(city, b)
	var p := profile(city, b, rng)
	var fp: Rect2 = b.footprint
	var fh: float = b.floor_h
	var eave = GROUND + b.floors * fh
	var top := GROUND + float(b.height)
	var col: Color = p.tint
	col.a = p.cond
	var roof: String = p.roof
	if b.kind == "tower":
		var tiers := [[0, b.floors, 0.0]]
		if b.style.get("setback_top", false):
			var m := minf(fp.size.x, fp.size.y)
			tiers = [[0, int(b.floors * 0.6), 0.0], [int(b.floors * 0.6), int(b.floors * 0.84), m * 0.13], [int(b.floors * 0.84), b.floors, m * 0.26]]
		for ti in tiers.size():
			var t: Array = tiers[ti]
			var r := fp.grow(-float(t[2]))
			var y0 := GROUND + int(t[0]) * fh - (0.3 if ti == 0 else 0.0)
			var y1 := GROUND + int(t[1]) * fh + (3.2 if ti == tiers.size() - 1 else 1.0)
			var cs := rect_corners(r)
			for i in 4:
				band(mb, p.fac, cs[i], cs[(i + 1) % 4], y0, y1, col, fh, p.bay, p.seed, flags(p.wstyle, p.mode, 1), int(t[0]) * fh - (0.45 if ti == 0 else 0.0))
			mb.flat_rect("u_roof_flat", r, y1, Color(0.5, 0.5, 0.52))
		return
	var pitched: bool = roof in ["gable", "hip", "mansard"] and b.kind != "station"
	var wall_top = eave if pitched else maxf(top, eave + 0.9)
	var cs2 := rect_corners(fp)
	var fl := flags(p.wstyle, p.mode, p.ikind)
	for i in 4:
		band(mb, p.fac, cs2[i], cs2[(i + 1) % 4], -0.3, wall_top, col, fh, p.bay, p.seed, fl, -0.3 - GROUND)
	if pitched:
		var along_x := fp.size.x >= fp.size.y
		var span := (fp.size.y if along_x else fp.size.x) * 0.5
		var rise := span * 0.8
		var c := fp.get_center()
		var ax := Vector3(1, 0, 0) if along_x else Vector3(0, 0, 1)
		var sx := Vector3(0, 0, 1) if along_x else Vector3(1, 0, 0)
		var L := (fp.size.x if along_x else fp.size.y) * 0.5
		var c3 := Vector3(c.x, eave, c.y)
		var s := mb.surf("u_roof_tiles")
		var rc: Color = p.roof_col
		for sg in [-1.0, 1.0]:
			var e0 = c3 - ax * L + sx * span * sg
			var e1 = c3 + ax * L + sx * span * sg
			var r1 := c3 + ax * L + Vector3(0, rise, 0)
			var r0 := c3 - ax * L + Vector3(0, rise, 0)
			var n = (sx * sg * rise + Vector3(0, span, 0)).normalized()
			_quad_ccw_up(s, e0, e1, r1, r0, n, ax, Vector2(0, 0), Vector2(L * 2, 0), Vector2(L * 2, -span), Vector2(0, -span), rc)
		var fsurf := mb.surf(p.fac)
		var cw := cus(3.0, 3.0, p.seed, flags(0, MODE_WALL, 0))
		for sg2 in [-1.0, 1.0]:
			var a = c3 + ax * L * sg2 - sx * span
			var bb = c3 + ax * L * sg2 + sx * span
			var t = c3 + ax * L * sg2 + Vector3(0, rise, 0)
			var geo = (bb - a).cross(t - a)
			if geo.dot(ax * sg2) >= 0.0:
				fsurf.tri(a, bb, t, ax * sg2, sx, Vector2(0, 0), Vector2(span * 2, 0), Vector2(span, -rise), col, cw)
			else:
				fsurf.tri(bb, a, t, ax * sg2, -sx, Vector2(0, 0), Vector2(span * 2, 0), Vector2(span, -rise), col, cw)
	else:
		mb.flat_rect("u_roof_flat", fp, wall_top - 0.02, Color(0.55, 0.55, 0.56))
