class_name CityGenerator
extends RefCounted
## Mehrstufiger, deterministischer Stadtgenerator.
##
##  1. Strassenlinien (Raster mit variablen Abstaenden), Fluss mit Uferstrassen
##  2. Zonierung der Rasterzellen (Stadtteiltypen)
##  3. Strassengraph inkl. Bruecken; Zellen werden teils zu Grossbloecken verschmolzen
##  4. Bloecke mit Bordstein- und Grundstuecksgrenzen, Promenaden am Fluss
##  5. Stadtteile (zusammenhaengende Zonen) mit Namen
##  6. Knotentypen: Ampel, Vorfahrt, Rechts-vor-links, Kreisverkehr
##  7. Zebrastreifen, Buslinien und Haltestellen (Bordstein-Reservierung)
##  8. Grundstuecksreihen, Sondereinrichtungen, Grundstuecke
##  9. Gebaeude mit Eingang, Gartenweg, Zufahrt, Parkplatz
## 10. Geschaefte und Maerkte mit fiktiven Namen
## 11. Strassen- und Grundstuecksrequisiten, Strassenparkplaetze, Startpunkte
## 12. Indizes; danach prueft CityValidator und repariert
##
## Gleicher Seed + gleiche Parameter => identisches Layout.

const SIZES := {"small": 1200.0, "medium": 1800.0, "large": 2400.0}

var m: CityModel
var rng := RandomNumberGenerator.new()
var noise := FastNoiseLite.new()
var center := Vector2.ZERO
var _rows: Array = []           # Grundstuecksreihen
var _cell_zone: Dictionary = {} # Vector2i -> zone
var _merged: Dictionary = {}    # Vector2i -> true
var _removed_edges: Dictionary = {}  # "x:i:j" / "z:i:j" -> true
var _node_lost: Dictionary = {} # Vector2i -> true
var _street_names: Array = []
var _used_names: Dictionary = {}
var _sector := {}


## Haupteinstieg. params: size ("small"|"medium"|"large").
static func generate(seed_value: int, params := {}) -> CityModel:
	var g := CityGenerator.new()
	return g.run(seed_value, params)


func run(seed_value: int, params: Dictionary) -> CityModel:
	var t0 := Time.get_ticks_msec()
	m = CityModel.new()
	m.seed = seed_value
	m.params = params.duplicate()
	m.size = SIZES.get(params.get("size", "medium"), 1800.0)
	rng.seed = hash("city:%d" % seed_value)
	noise.seed = seed_value
	noise.frequency = 1.0 / 450.0
	noise.noise_type = FastNoiseLite.TYPE_SIMPLEX_SMOOTH
	_street_names = CityCatalog.STREET_NAMES.duplicate()
	_shuffle(_street_names)

	_make_lines()
	_make_zoning()
	_make_graph()
	_make_blocks()
	_make_districts()
	_classify_nodes()
	_make_crosswalks()
	m.build_indices()   # vorlaeufig fuer Routen der Buslinien
	_make_bus_network()
	_make_rows()
	_place_facilities()
	_make_lots_and_buildings()
	_assign_businesses()
	_make_parks()
	_make_street_props()
	_make_lot_props()
	_make_street_parking()
	m.build_indices()
	_make_spawn_points()

	if not params.get("validate", true):
		return m
	var report := CityValidator.validate_and_repair(m)
	m.build_indices()
	m.stats["generation_ms"] = Time.get_ticks_msec() - t0
	m.stats["validation"] = report
	m.stats["buildings"] = m.buildings.size()
	m.stats["props"] = m.props.size()
	m.stats["nodes"] = m.nodes.size()
	m.stats["edges"] = m.edges.size()
	return m


# ============================================================ Hilfen

func _shuffle(a: Array) -> void:
	for i in range(a.size() - 1, 0, -1):
		var j := rng.randi_range(0, i)
		var t = a[i]
		a[i] = a[j]
		a[j] = t


func _pick_weighted(weights: Dictionary) -> String:
	var total := 0.0
	for k in weights:
		total += float(weights[k])
	if total <= 0.0:
		return ""
	var r := rng.randf() * total
	for k in weights:
		r -= float(weights[k])
		if r <= 0.0:
			return k
	return weights.keys()[-1]


func _street_name() -> String:
	if _street_names.is_empty():
		_street_names = CityCatalog.STREET_NAMES.duplicate()
		_shuffle(_street_names)
		for i in _street_names.size():
			_street_names[i] = "Neue " + _street_names[i] if not _street_names[i].begins_with("Neue") else _street_names[i] + " II"
	return _street_names.pop_back()


func _angle_dist(a: float, b: float) -> float:
	return absf(wrapf(a - b, -PI, PI))


# ============================================================ 1. Linien

func _make_lines() -> void:
	var half := m.size * 0.5
	var xs := _gen_positions(half)
	var zs := _gen_positions(half)

	# Fluss: Band in x-Richtung, nicht exakt in der Mitte
	var sgn := 1.0 if rng.randf() < 0.5 else -1.0
	var zr := sgn * rng.randf_range(0.07, 0.16) * m.size
	var w := rng.randf_range(46.0, 62.0)
	var prof: Dictionary = CityModel.PROFILES["embankment"]
	var hw: float = prof.lanes * prof.lane_w + prof.bike_w + prof.park_w
	var promenade := 11.0
	var off := w * 0.5 + promenade + hw
	var zn := zr - off
	var zsouth := zr + off
	var filtered := []
	for z in zs:
		if z < zn - 62.0 or z > zsouth + 62.0:
			filtered.append(z)
	filtered.append(zn)
	filtered.append(zsouth)
	filtered.sort()
	zs = _fix_gaps(filtered, [zn, zsouth, -half, half], half)
	m.river = {"z": zr, "width": w, "z_north": zr - w * 0.5, "z_south": zr + w * 0.5,
		"x0": -half - 60.0, "x1": half + 60.0, "emb_north": zn, "emb_south": zsouth}
	center = Vector2(rng.randf_range(-0.06, 0.06) * m.size, zr - sgn * rng.randf_range(0.10, 0.16) * m.size)

	m.x_lines = _classify_lines(xs, false)
	m.z_lines = _classify_lines(zs, true)


func _gen_positions(half: float) -> Array:
	var out := [-half]
	var p := -half
	while true:
		var rel := absf(p) / half
		var lo := lerpf(88.0, 120.0, rel)
		var hi := lerpf(118.0, 168.0, rel)
		p += rng.randf_range(lo, hi)
		if p > half - 70.0:
			break
		out.append(p)
	out.append(half)
	return out


func _fix_gaps(a: Array, fixed: Array, _half: float) -> Array:
	var changed := true
	var guard := 0
	while changed and guard < 50:
		guard += 1
		changed = false
		for i in range(a.size() - 1):
			var gap: float = a[i + 1] - a[i]
			if gap > 178.0:
				a.insert(i + 1, a[i] + gap * 0.5)
				changed = true
				break
			if gap < 60.0:
				# den nicht fixierten der beiden entfernen
				var rm := i + 1 if not _is_fixed(a[i + 1], fixed) else i
				if _is_fixed(a[rm], fixed):
					continue
				a.remove_at(rm)
				changed = true
				break
	return a


func _is_fixed(v: float, fixed: Array) -> bool:
	for f in fixed:
		if absf(v - f) < 0.01:
			return true
	return false


func _classify_lines(pos: Array, is_z: bool) -> Array:
	var lines := []
	var n := pos.size()
	# Arterien: Linie naechst zur Mitte plus etwa jede dritte/vierte
	var c := center.x if not is_z else center.y
	var ci := 1
	var bd := INF
	for i in range(1, n - 1):
		if absf(pos[i] - c) < bd:
			bd = absf(pos[i] - c)
			ci = i
	var arterial := {}
	arterial[ci] = true
	var step := rng.randi_range(3, 4)
	var i2: int = ci + step
	while i2 < n - 2:
		arterial[i2] = true
		i2 += rng.randi_range(3, 4)
	i2 = ci - step
	while i2 > 1:
		arterial[i2] = true
		i2 -= rng.randi_range(3, 4)
	var art_names := CityCatalog.ARTERIAL_NAMES.duplicate()
	_shuffle(art_names)
	for i in n:
		var cls := "street"
		var name := ""
		var p: float = pos[i]
		if i == 0 or i == n - 1:
			cls = "ring"
			if is_z:
				name = CityCatalog.RING_NAMES[0] if i == 0 else CityCatalog.RING_NAMES[2]
			else:
				name = CityCatalog.RING_NAMES[3] if i == 0 else CityCatalog.RING_NAMES[1]
		elif is_z and (absf(p - m.river.emb_north) < 0.01 or absf(p - m.river.emb_south) < 0.01):
			cls = "embankment"
			name = CityCatalog.EMBANKMENT_NAMES[0] if p < m.river.z else CityCatalog.EMBANKMENT_NAMES[1]
		elif arterial.has(i):
			cls = "arterial"
			name = art_names.pop_back() if not art_names.is_empty() else _street_name()
		else:
			name = _street_name()
		lines.append({"pos": p, "cls": cls, "name": name})
	# Arterien direkt neben Uferstrassen vermeiden (zu dichte Hauptstrassen)
	return lines


func _profile_of(line: Dictionary) -> Dictionary:
	return CityModel.PROFILES[line.cls]


func _line_half_width(line: Dictionary) -> float:
	var p := _profile_of(line)
	return p.lanes * p.lane_w + p.bike_w + p.park_w


# ============================================================ 2. Zonierung

func _river_band_row(gz: int) -> bool:
	return absf(m.z_lines[gz].pos - m.river.emb_north) < 0.01


func _cell_rect(gx: int, gz: int) -> Rect2:
	var x0: float = m.x_lines[gx].pos
	var x1: float = m.x_lines[gx + 1].pos
	var z0: float = m.z_lines[gz].pos
	var z1: float = m.z_lines[gz + 1].pos
	return Rect2(Vector2(x0, z0), Vector2(x1 - x0, z1 - z0))


func _make_zoning() -> void:
	var half := m.size * 0.5
	_sector["rich"] = rng.randf_range(-PI, PI)
	_sector["industrial"] = wrapf(_sector.rich + PI + rng.randf_range(-0.5, 0.5), -PI, PI)
	_sector["poor"] = wrapf(_sector.industrial + (0.75 if rng.randf() < 0.5 else -0.75), -PI, PI)
	_sector["university"] = wrapf(_sector.rich + (PI * 0.5 if rng.randf() < 0.5 else -PI * 0.5) + rng.randf_range(-0.3, 0.3), -PI, PI)
	_sector["highrise"] = wrapf(_sector.poor + PI * 0.5 + rng.randf_range(-0.4, 0.4), -PI, PI)
	_sector["oldtown"] = rng.randf_range(-PI, PI)
	var nx := m.x_lines.size() - 1
	var nz := m.z_lines.size() - 1
	for gx in nx:
		for gz in nz:
			if _river_band_row(gz):
				continue
			var c := _cell_rect(gx, gz).get_center()
			var rel := c - center
			var d := rel.length() / half
			var ang := atan2(rel.y, rel.x)
			var nv := noise.get_noise_2d(c.x, c.y)
			var sc := {}
			sc["downtown"] = 3.2 - d / 0.11
			sc["business"] = 1.7 - absf(d - 0.21) / 0.07
			sc["oldtown"] = 1.9 - absf(d - 0.24) / 0.09 - _angle_dist(ang, _sector.oldtown) / 0.55
			sc["commercial"] = 1.15 - absf(d - 0.34) / 0.16 + nv * 0.35
			sc["apartments"] = 1.0 - absf(d - 0.48) / 0.26 - nv * 0.25
			sc["highrise"] = 1.25 - absf(d - 0.42) / 0.16 - _angle_dist(ang, _sector.highrise) / 0.6
			sc["residential"] = 0.95 - absf(d - 0.68) / 0.30 + nv * 0.2
			sc["suburb"] = 0.9 - absf(d - 1.0) / 0.25
			sc["rich"] = 1.55 - _angle_dist(ang, _sector.rich) / 0.55 - absf(d - 0.55) / 0.30
			sc["estates"] = 1.45 - _angle_dist(ang, _sector.rich) / 0.42 - absf(d - 0.88) / 0.18
			sc["industrial"] = 1.75 - _angle_dist(ang, _sector.industrial) / 0.5 - absf(d - 0.82) / 0.30
			sc["poor"] = 1.45 - _angle_dist(ang, _sector.poor) / 0.5 - absf(d - 0.52) / 0.24
			sc["slum"] = 1.35 - _angle_dist(ang, _sector.poor) / 0.32 - absf(d - 0.66) / 0.12
			sc["university"] = 1.4 - _angle_dist(ang, _sector.university) / 0.33 - absf(d - 0.5) / 0.11
			var best := "residential"
			var bv := -INF
			for k in sc:
				if sc[k] > bv:
					bv = sc[k]
					best = k
			_cell_zone[Vector2i(gx, gz)] = best
	# Civic: zwei Zellen nahe der Innenstadt, Parks verteilen
	var cells := _cell_zone.keys()
	cells.sort_custom(func(a, b): return _cell_rect(a.x, a.y).get_center().distance_to(center) < _cell_rect(b.x, b.y).get_center().distance_to(center))
	var civic := 0
	for c in cells:
		if civic >= 2:
			break
		if _cell_zone[c] in ["business", "downtown", "oldtown"] and _cell_rect(c.x, c.y).get_center().distance_to(center) > m.size * 0.08:
			_cell_zone[c] = "civic"
			civic += 1
	# Grosser Stadtpark nahe Zentrum
	for c in cells:
		if _cell_zone[c] in ["business", "commercial", "apartments", "oldtown"] and _cell_rect(c.x, c.y).get_center().distance_to(center) > m.size * 0.12:
			_cell_zone[c] = "park"
			break
	var park_w := {"rich": 0.16, "residential": 0.08, "suburb": 0.06, "apartments": 0.07, "highrise": 0.08,
		"commercial": 0.03, "university": 0.12, "poor": 0.04, "slum": 0.05, "estates": 0.05, "oldtown": 0.04}
	for c in cells:
		var z: String = _cell_zone[c]
		if park_w.has(z) and rng.randf() < park_w[z]:
			_cell_zone[c] = "park"


# ============================================================ 3. Graph

func _make_graph() -> void:
	var nx := m.x_lines.size()
	var nz := m.z_lines.size()
	# Bruecken: Ringlinien, Arterien und einige Strassen
	var bridge := {}
	for i in nx:
		var cls: String = m.x_lines[i].cls
		if cls == "ring" or cls == "arterial":
			bridge[i] = true
		elif rng.randf() < 0.22:
			bridge[i] = true
	# maximaler Abstand zwischen Bruecken
	var last := -1
	for i in nx:
		if bridge.has(i):
			if last >= 0 and m.x_lines[i].pos - m.x_lines[last].pos > 420.0:
				var mid := (last + i) / 2
				if mid != last:
					bridge[mid] = true
			last = i
	# Zellen verschmelzen
	_plan_merges()
	# Knoten
	for gz in nz:
		for gx in nx:
			var id := m.nodes.size()
			m.nodes.append({"id": id, "pos": Vector2(m.x_lines[gx].pos, m.z_lines[gz].pos),
				"gx": gx, "gz": gz, "edges": [], "kind": "pass", "signal": {}, "roundabout": {},
				"priority_axis": ""})
	# Kanten entlang x (auf z-Linien)
	for gz in nz:
		for gx in range(nx - 1):
			if _removed_edges.has("z:%d:%d" % [gx, gz]):
				continue
			_add_edge(gz * nx + gx, gz * nx + gx + 1, "x", m.z_lines[gz], false)
	# Kanten entlang z (auf x-Linien)
	for gx in nx:
		for gz in range(nz - 1):
			if _removed_edges.has("x:%d:%d" % [gx, gz]):
				continue
			var is_bridge := _river_band_row(gz)
			if is_bridge and not bridge.has(gx):
				continue
			_add_edge(gz * nx + gx, (gz + 1) * nx + gx, "z", m.x_lines[gx], is_bridge)
	# Bruecken nach Ringlinien benennen
	for e in m.edges:
		if e.bridge:
			e.name = e.name + "-Brücke" if not e.name.ends_with("Brücke") else e.name


func _add_edge(a: int, b: int, axis: String, line: Dictionary, is_bridge: bool) -> void:
	var p := _profile_of(line)
	var id := m.edges.size()
	var width: float = 2.0 * (p.lanes * p.lane_w + p.bike_w + p.park_w)
	var sw: float = p.sidewalk
	var sidewalk := [sw, sw]
	if line.cls == "ring":
		# Aussenseite des Rings ohne Gehweg (Gruenstreifen)
		var pa: Vector2 = m.nodes[a].pos
		var half := m.size * 0.5
		if axis == "x":
			# rechts von +x ist +z
			sidewalk = [sw, 0.0] if pa.y >= half - 0.01 else ([0.0, sw] if pa.y <= -half + 0.01 else [sw, sw])
		else:
			# rechts von +z ist -x
			sidewalk = [0.0, sw] if pa.x >= half - 0.01 else ([sw, 0.0] if pa.x <= -half + 0.01 else [sw, sw])
	var e := {"id": id, "a": a, "b": b, "axis": axis, "line": line.name, "cls": line.cls,
		"lanes": p.lanes, "lane_w": p.lane_w, "bike_w": p.bike_w, "park_w": p.park_w,
		"width": width, "sidewalk": sidewalk, "bridge": is_bridge, "name": line.name,
		"speed": p.speed, "length": m.nodes[a].pos.distance_to(m.nodes[b].pos)}
	m.edges.append(e)
	m.nodes[a].edges.append(id)
	m.nodes[b].edges.append(id)


func _plan_merges() -> void:
	var nx := m.x_lines.size() - 1
	var nz := m.z_lines.size() - 1
	var want := {"industrial": 0.55, "estates": 0.7, "university": 0.6, "civic": 0.5, "park": 0.35,
		"suburb": 0.15, "rich": 0.25, "residential": 0.10}
	var keys := _cell_zone.keys()
	keys.sort()
	for c in keys:
		if _merged.has(c):
			continue
		var z: String = _cell_zone[c]
		if not want.has(z) or rng.randf() > want[z]:
			continue
		var dirs := [Vector2i(1, 0), Vector2i(0, 1)]
		if rng.randf() < 0.5:
			dirs.reverse()
		for d in dirs:
			var o: Vector2i = c + d
			if o.x >= nx or o.y >= nz or not _cell_zone.has(o) or _merged.has(o):
				continue
			var oz: String = _cell_zone[o]
			if oz != z and not (oz == "park" or z == "park"):
				continue
			var key := ""
			var na := Vector2i.ZERO
			var nb := Vector2i.ZERO
			if d.x == 1:
				var line: Dictionary = m.x_lines[o.x]
				if line.cls != "street":
					continue
				key = "x:%d:%d" % [o.x, c.y]
				na = Vector2i(o.x, c.y)
				nb = Vector2i(o.x, c.y + 1)
			else:
				var line2: Dictionary = m.z_lines[o.y]
				if line2.cls != "street":
					continue
				key = "z:%d:%d" % [c.x, o.y]
				na = Vector2i(c.x, o.y)
				nb = Vector2i(c.x + 1, o.y)
			if _node_lost.has(na) or _node_lost.has(nb):
				continue
			var r1 := _cell_rect(c.x, c.y)
			var r2 := _cell_rect(o.x, o.y)
			if r1.merge(r2).size.x > 330.0 or r1.merge(r2).size.y > 330.0:
				continue
			_removed_edges[key] = true
			_node_lost[na] = true
			_node_lost[nb] = true
			_merged[c] = o
			_merged[o] = c
			if oz == "park" and z != "park":
				_cell_zone[c] = "park"
			elif z == "park" and oz != "park":
				_cell_zone[o] = "park"
			break


# ============================================================ 4. Bloecke

func _make_blocks() -> void:
	var nx := m.x_lines.size() - 1
	var nz := m.z_lines.size() - 1
	var done := {}
	for gz in nz:
		for gx in nx:
			var c := Vector2i(gx, gz)
			if done.has(c) or not _cell_zone.has(c):
				continue
			var gx0 := gx
			var gz0 := gz
			var gx1 := gx + 1
			var gz1 := gz + 1
			if _merged.has(c):
				var o: Vector2i = _merged[c]
				gx0 = mini(c.x, o.x)
				gz0 = mini(c.y, o.y)
				gx1 = maxi(c.x, o.x) + 1
				gz1 = maxi(c.y, o.y) + 1
				done[o] = true
			done[c] = true
			_add_block(gx0, gz0, gx1, gz1, _cell_zone[c])
	_make_promenades()


func _add_block(gx0: int, gz0: int, gx1: int, gz1: int, zone: String) -> void:
	var lx0: Dictionary = m.x_lines[gx0]
	var lx1: Dictionary = m.x_lines[gx1]
	var lz0: Dictionary = m.z_lines[gz0]
	var lz1: Dictionary = m.z_lines[gz1]
	var cell := Rect2(Vector2(lx0.pos, lz0.pos), Vector2(lx1.pos - lx0.pos, lz1.pos - lz0.pos))
	var l := _line_half_width(lx0)
	var r := _line_half_width(lx1)
	var t := _line_half_width(lz0)
	var b := _line_half_width(lz1)
	var curb := Rect2(Vector2(cell.position.x + l, cell.position.y + t),
		Vector2(cell.size.x - l - r, cell.size.y - t - b))
	var sl: float = _profile_of(lx0).sidewalk
	var sr: float = _profile_of(lx1).sidewalk
	var st: float = _profile_of(lz0).sidewalk
	var sb: float = _profile_of(lz1).sidewalk
	var inner := Rect2(Vector2(curb.position.x + sl, curb.position.y + st),
		Vector2(curb.size.x - sl - sr, curb.size.y - st - sb))
	var id := m.blocks.size()
	var blk := {"id": id, "gx0": gx0, "gz0": gz0, "gx1": gx1, "gz1": gz1, "cell": cell,
		"curb": curb, "inner": inner, "zone": zone, "district": -1,
		"kind": "park" if zone == "park" else "normal", "special": ""}
	m.blocks.append(blk)
	m.register_block_cells(blk)


func _make_promenades() -> void:
	# Zwischen zwei Bruecken liegt je Ufer ein Promenadenstreifen.
	var nx := m.x_lines.size()
	var gzn := -1
	for gz in m.z_lines.size():
		if absf(m.z_lines[gz].pos - m.river.emb_north) < 0.01:
			gzn = gz
	if gzn < 0:
		return
	var bridge_lines := []
	for e in m.edges:
		if e.bridge:
			bridge_lines.append(m.nodes[e.a].gx)
	bridge_lines.sort()
	var lzn: Dictionary = m.z_lines[gzn]
	var lzs: Dictionary = m.z_lines[gzn + 1]
	for k in range(bridge_lines.size() - 1):
		var ga: int = bridge_lines[k]
		var gb: int = bridge_lines[k + 1]
		var la: Dictionary = m.x_lines[ga]
		var lb: Dictionary = m.x_lines[gb]
		var x0: float = la.pos + _line_half_width(la)
		var x1: float = lb.pos - _line_half_width(lb)
		for north in [true, false]:
			var z0: float
			var z1: float
			if north:
				z0 = lzn.pos + _line_half_width(lzn)
				z1 = m.river.z_north
			else:
				z0 = m.river.z_south
				z1 = lzs.pos - _line_half_width(lzs)
			var curb := Rect2(Vector2(x0, z0), Vector2(x1 - x0, z1 - z0))
			var cell := Rect2(Vector2(la.pos, z0 if north else z0), Vector2(lb.pos - la.pos, z1 - z0))
			if north:
				cell = Rect2(Vector2(la.pos, lzn.pos), Vector2(lb.pos - la.pos, z1 - lzn.pos))
			else:
				cell = Rect2(Vector2(la.pos, z0), Vector2(lb.pos - la.pos, lzs.pos - z0))
			var id := m.blocks.size()
			m.blocks.append({"id": id, "gx0": ga, "gz0": gzn, "gx1": gb, "gz1": gzn + 1, "cell": cell,
				"curb": curb, "inner": curb, "zone": "park", "district": -1, "kind": "promenade",
				"special": "", "north_bank": north})


# ============================================================ 5. Stadtteile

func _make_districts() -> void:
	var used_names := {}
	var visited := {}
	for b in m.blocks:
		if visited.has(b.id) or b.kind == "promenade":
			continue
		# Flood-Fill ueber Nachbarzellen gleicher Zone
		var comp := []
		var stack := [b.id]
		visited[b.id] = true
		while not stack.is_empty():
			var cur: Dictionary = m.blocks[stack.pop_back()]
			comp.append(cur.id)
			for nb in _block_neighbors(cur):
				if visited.has(nb):
					continue
				var o: Dictionary = m.blocks[nb]
				if o.kind == "promenade":
					continue
				var same: bool = o.zone == cur.zone or (_zone_family(o.zone) == _zone_family(cur.zone) and o.zone != "park" and cur.zone != "park")
				if same:
					visited[nb] = true
					stack.append(nb)
		var zone: String = b.zone
		var names: Array = CityCatalog.DISTRICT_NAMES.get(zone, ["Viertel"])
		var name: String = ""
		for nm in names:
			if not used_names.has(nm):
				name = nm
				break
		if name == "":
			var base: String = names[rng.randi_range(0, names.size() - 1)]
			var n := 2
			while used_names.has("%s %s" % [base, _roman(n)]):
				n += 1
			name = "%s %s" % [base, _roman(n)]
		used_names[name] = true
		var bounds: Rect2 = m.blocks[comp[0]].cell
		var csum := Vector2.ZERO
		for bid in comp:
			bounds = bounds.merge(m.blocks[bid].cell)
			csum += m.blocks[bid].cell.get_center()
		var zi: Dictionary = CityCatalog.ZONES[zone]
		var did := m.districts.size()
		m.districts.append({"id": did, "name": name, "zone": zone, "label": zi.label, "blocks": comp,
			"center": csum / comp.size(), "bounds": bounds, "wealth": zi.wealth, "crime": zi.crime})
		for bid in comp:
			m.blocks[bid].district = did
	# Promenaden dem naechsten Stadtteil zuordnen
	for b in m.blocks:
		if b.kind == "promenade":
			var best := 0
			var bd := INF
			for d in m.districts:
				var dd: float = d.center.distance_to(b.curb.get_center())
				if dd < bd:
					bd = dd
					best = d.id
			b.district = best


func _zone_family(z: String) -> String:
	match z:
		"rich", "estates": return "rich"
		"poor", "slum": return "poor"
		"downtown", "business": return "core"
	return z


func _roman(n: int) -> String:
	return ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"][clampi(n, 0, 10)] if n <= 10 else str(n)


func _block_neighbors(b: Dictionary) -> Array:
	var out := []
	for gx in range(b.gx0 - 1, b.gx1 + 1):
		for gz in range(b.gz0 - 1, b.gz1 + 1):
			if gx >= b.gx0 and gx < b.gx1 and gz >= b.gz0 and gz < b.gz1:
				continue
			if (gx < b.gx0 or gx >= b.gx1) and (gz < b.gz0 or gz >= b.gz1):
				continue  # keine Diagonalen
			var o := m.block_at_cell(gx, gz)
			if o >= 0 and o != b.id and not out.has(o):
				out.append(o)
	return out


# ============================================================ 6. Knotentypen

func _make_node_kind(n: Dictionary) -> void:
	var deg: int = n.edges.size()
	var max_rank := 0
	var min_rank := 9
	var axes := {}
	for eid in n.edges:
		var e: Dictionary = m.edges[eid]
		var r: int = CityModel.PROFILES[e.cls].rank
		max_rank = maxi(max_rank, r)
		min_rank = mini(min_rank, r)
		axes[e.axis] = maxi(axes.get(e.axis, 0), r)
	if deg <= 2:
		n.kind = "corner" if axes.size() == 2 else "pass"
		return
	var ring_involved := false
	for eid in n.edges:
		if m.edges[eid].cls == "ring":
			ring_involved = true
	var rank_x: int = axes.get("x", 0)
	var rank_z: int = axes.get("z", 0)
	if max_rank >= 3 and min_rank >= 2:
		n.kind = "signal"
	elif max_rank >= 3 and not ring_involved:
		n.kind = "signal"
	elif rank_x != rank_z:
		n.kind = "yield"
		n.priority_axis = "x" if rank_x > rank_z else "z"
	elif deg == 4 and max_rank == 1:
		n.kind = "stop"
	else:
		n.kind = "yield"
		# Bei T-Kreuzung hat die durchgehende Strasse Vorfahrt
		var cnt := {"x": 0, "z": 0}
		for eid in n.edges:
			cnt[m.edges[eid].axis] += 1
		n.priority_axis = "x" if cnt.x >= cnt.z else "z"
	if n.kind == "signal":
		n.signal = {"offset": rng.randf_range(0.0, CityModel.SIGNAL_CYCLE), "x_first": rng.randf() < 0.5}


func _classify_nodes() -> void:
	for n in m.nodes:
		_make_node_kind(n)
	# Kreisverkehre an ruhigen Kreuzungen in Wohngebieten
	for n in m.nodes:
		if n.kind != "stop" or n.edges.size() != 4:
			continue
		var zone := _zone_near(n.pos)
		if not zone in ["residential", "suburb", "rich", "apartments", "university"]:
			continue
		if rng.randf() > 0.28:
			continue
		var clear := INF
		for sx in [-1, 1]:
			for sz in [-1, 1]:
				var bid := m.block_at_point(n.pos + Vector2(sx, sz) * 15.0, true)
				if bid < 0:
					clear = 0.0
					continue
				var inner: Rect2 = m.blocks[bid].inner
				var corner := Vector2(inner.position.x if sx > 0 else inner.end.x, inner.position.y if sz > 0 else inner.end.y)
				clear = minf(clear, corner.distance_to(n.pos))
		var box := m.node_box(n.id)
		var r_outer := minf(clear - 1.5, maxf(box.x, box.y) + 6.0)
		if r_outer < maxf(box.x, box.y) + 3.0 or r_outer - 6.5 < 3.0:
			continue
		n.kind = "roundabout"
		n.roundabout = {"r_outer": r_outer, "r_inner": r_outer - 6.5}


func _zone_near(p: Vector2) -> String:
	var counts := {}
	for sx in [-1, 1]:
		for sz in [-1, 1]:
			var bid := m.block_at_point(p + Vector2(sx, sz) * 15.0, true)
			if bid >= 0:
				var z: String = m.blocks[bid].zone
				counts[z] = counts.get(z, 0) + 1
	var best := ""
	var bc := 0
	for z in counts:
		if counts[z] > bc:
			bc = counts[z]
			best = z
	return best


# ============================================================ 7. Zebrastreifen / Busse

func _make_crosswalks() -> void:
	for n in m.nodes:
		if n.kind in ["pass", "corner"]:
			# Endbereiche trotzdem freihalten
			for eid in n.edges:
				var e0: Dictionary = m.edges[eid]
				_reserve_end(e0, n.id, m.edge_end_clearance(e0, n.id) + 2.0)
			continue
		for eid in n.edges:
			var e: Dictionary = m.edges[eid]
			var clr := m.edge_end_clearance(e, n.id)
			var dist: float = clr - 1.6 if n.kind != "roundabout" else float(n.roundabout.r_outer) + 2.2
			var from_a: bool = e.a == n.id
			var t: float = dist if from_a else e.length - dist
			var c := m.edge_point(e, t)
			m.crosswalks.append({"node": n.id, "edge": e.id, "center": c, "axis": e.axis,
				"length": e.width, "width": 3.0, "signal": n.kind == "signal"})
			_reserve_end(e, n.id, clr + 4.0)


func _reserve_end(e: Dictionary, node_id: int, d: float) -> void:
	for side in [-1, 1]:
		if e.a == node_id:
			m.reserve_curb(e.id, side, 0.0, d, "end")
		else:
			m.reserve_curb(e.id, side, e.length - d, e.length, "end")


func _make_bus_network() -> void:
	var nx := m.x_lines.size()
	var nz := m.z_lines.size()
	var routes := []
	var colors := [Color(0.85, 0.20, 0.25), Color(0.15, 0.50, 0.85), Color(0.20, 0.65, 0.30),
		Color(0.95, 0.60, 0.10), Color(0.55, 0.30, 0.75), Color(0.10, 0.65, 0.65)]
	# Arterien entlang x-Linien (Nord-Sued, ueber den Fluss)
	var xart := []
	for i in nx:
		if m.x_lines[i].cls == "arterial":
			xart.append(i)
	var zart := []
	for j in nz:
		if m.z_lines[j].cls == "arterial":
			zart.append(j)
	xart.sort_custom(func(a, b): return absf(m.x_lines[a].pos - center.x) < absf(m.x_lines[b].pos - center.x))
	zart.sort_custom(func(a, b): return absf(m.z_lines[a].pos - center.y) < absf(m.z_lines[b].pos - center.y))
	for k in mini(2, xart.size()):
		var gx: int = xart[k]
		var nodes_seq := []
		for gz in nz:
			nodes_seq.append(m.node_at_grid(gx, gz))
		routes.append({"nodes": nodes_seq, "loop": false, "via": m.x_lines[gx].name})
	for k in mini(2, zart.size()):
		var gz: int = zart[k]
		var nodes_seq2 := []
		for gx in nx:
			nodes_seq2.append(m.node_at_grid(gx, gz))
		routes.append({"nodes": nodes_seq2, "loop": false, "via": m.z_lines[gz].name})
	# Ringlinie im Uhrzeigersinn
	var ring := []
	for gx in nx:
		ring.append(m.node_at_grid(gx, 0))
	for gz in range(1, nz):
		ring.append(m.node_at_grid(nx - 1, gz))
	for gx in range(nx - 2, -1, -1):
		ring.append(m.node_at_grid(gx, nz - 1))
	for gz in range(nz - 2, 0, -1):
		ring.append(m.node_at_grid(0, gz))
	routes.append({"nodes": ring, "loop": true, "via": "Ring"})
	# Uferlinie entlang der Uferstrassen (Nord hin, Sued zurueck)
	var gzn := -1
	for gz in nz:
		if m.z_lines[gz].cls == "embankment" and m.z_lines[gz].pos < m.river.z:
			gzn = gz
	if gzn >= 0:
		var emb := []
		for gx in nx:
			emb.append(m.node_at_grid(gx, gzn))
		routes.append({"nodes": emb, "loop": false, "via": "Ufer"})

	for r in routes:
		# Pruefen, dass alle Kanten existieren
		var ok := true
		for i in range(r.nodes.size() - 1):
			if m.edge_between(r.nodes[i], r.nodes[i + 1]) < 0:
				ok = false
				break
		if r.loop and ok and m.edge_between(r.nodes[-1], r.nodes[0]) < 0:
			ok = false
		if not ok:
			continue
		var lid := m.bus_lines.size()
		var line := {"id": lid, "name": "Linie %d" % (lid + 1), "number": lid + 1,
			"color": colors[lid % colors.size()], "loop": r.loop, "nodes": r.nodes,
			"via": r.via, "stops": [], "stops_back": [], "length": 0.0}
		line.stops = _place_stops(line, r.nodes, lid)
		if not r.loop:
			var back: Array = r.nodes.duplicate()
			back.reverse()
			line.stops_back = _place_stops(line, back, lid)
		var length := 0.0
		for i in range(r.nodes.size() - 1):
			length += m.nodes[r.nodes[i]].pos.distance_to(m.nodes[r.nodes[i + 1]].pos)
		line.length = length * (1.0 if r.loop else 2.0)
		m.bus_lines.append(line)


func _place_stops(line: Dictionary, seq: Array, lid: int) -> Array:
	var stops := []
	var acc := 220.0  # erste Haltestelle frueh
	for i in range(seq.size() - 1):
		var a: int = seq[i]
		var b: int = seq[i + 1]
		var eid := m.edge_between(a, b)
		var e: Dictionary = m.edges[eid]
		acc += e.length
		if acc < 260.0 or e.length < 60.0 or e.bridge:
			continue
		var forward: bool = e.a == a
		var side := 1 if forward else -1
		if e.sidewalk[1 if side > 0 else 0] <= 0.0:
			continue
		# Haltestelle kurz vor dem Ende der Kante in Fahrtrichtung
		var t_travel: float = e.length * 0.55
		var t: float = t_travel if forward else e.length - t_travel
		var t0 := t - 11.0
		var t1 := t + 11.0
		# vorhandene Haltestelle an derselben Stelle wiederverwenden
		var existing := -1
		for s in m.bus_stops:
			if s.edge == eid and s.side == side:
				existing = s.id
		if existing >= 0:
			if not lid in m.bus_stops[existing].lines:
				m.bus_stops[existing].lines.append(lid)
			stops.append(existing)
			acc = 0.0
			continue
		if not m.reserve_curb(eid, side, t0, t1, "bus_stop"):
			continue
		var cross_name := ""
		for oe in m.nodes[b].edges:
			if m.edges[oe].axis != e.axis:
				cross_name = m.edges[oe].name
		var sid := m.bus_stops.size()
		var off := m.sidewalk_offset(e, side)
		var stop := {"id": sid, "edge": eid, "side": side, "t": t,
			"pos": m.edge_point(e, t, off), "curb_pos": m.edge_point(e, t, m.curb_offset(e, side)),
			"dir": (m.nodes[b].pos - m.nodes[a].pos).normalized(),
			"name": cross_name if cross_name != "" else e.name, "lines": [lid]}
		m.bus_stops.append(stop)
		stops.append(sid)
		acc = 0.0
	return stops


# ============================================================ 8. Reihen / Einrichtungen

## Eine Reihe ist ein Streifen eines Blocks, dessen Grundstuecke zur selben
## Strasse zeigen. Einrichtungen beanspruchen Abschnitte der Reihe.
func _make_rows() -> void:
	for b in m.blocks:
		if b.kind != "normal":
			continue
		var r: Rect2 = b.inner
		if r.size.x < 12.0 or r.size.y < 12.0:
			b.kind = "park"
			continue
		var horizontal := r.size.x >= r.size.y  # Fronten nach Norden/Sueden
		var depth_total: float = r.size.y if horizontal else r.size.x
		var fronts := []
		if depth_total < 44.0:
			# Nur eine Reihe zur wichtigeren Strasse
			var rank_a := _side_rank(b, "n" if horizontal else "w")
			var rank_b := _side_rank(b, "s" if horizontal else "e")
			fronts.append(("n" if horizontal else "w") if rank_a >= rank_b else ("s" if horizontal else "e"))
		else:
			fronts = ["n", "s"] if horizontal else ["w", "e"]
		for f in fronts:
			var rr := r
			if fronts.size() == 2:
				var hd := depth_total * 0.5
				match f:
					"n": rr = Rect2(r.position, Vector2(r.size.x, hd))
					"s": rr = Rect2(Vector2(r.position.x, r.position.y + hd), Vector2(r.size.x, hd))
					"w": rr = Rect2(r.position, Vector2(hd, r.size.y))
					"e": rr = Rect2(Vector2(r.position.x + hd, r.position.y), Vector2(hd, r.size.y))
			var length: float = rr.size.x if f in ["n", "s"] else rr.size.y
			_rows.append({"block": b.id, "rect": rr, "front": f, "length": length,
				"depth": rr.size.y if f in ["n", "s"] else rr.size.x,
				"zone": b.zone, "free0": 0.0, "free1": length, "claims": [],
				"rank": _side_rank(b, f)})


func _side_rank(b: Dictionary, f: String) -> int:
	var line: Dictionary
	match f:
		"n": line = m.z_lines[b.gz0]
		"s": line = m.z_lines[b.gz1]
		"w": line = m.x_lines[b.gx0]
		_: line = m.x_lines[b.gx1]
	return CityModel.PROFILES[line.cls].rank


## Ganzer Block fuer eine Einrichtung.
func _claim_block(kind: String, zones: Array, near: Vector2, min_dist_same := 0.0) -> bool:
	var spec: Dictionary = CityCatalog.BUILDING_KINDS[kind]
	var best := -1
	var bd := INF
	for b in m.blocks:
		if b.kind != "normal" or b.special != "" or not b.zone in zones:
			continue
		var need_w: float = spec.footprint[0] + 2.0 * spec.setback[1]
		var need_d: float = spec.footprint[2] + spec.setback[0] + spec.setback[2]
		var r: Rect2 = b.inner
		if not ((r.size.x >= need_w and r.size.y >= need_d) or (r.size.y >= need_w and r.size.x >= need_d)):
			continue
		var c: Vector2 = r.get_center()
		if min_dist_same > 0.0 and _too_close(kind, c, min_dist_same):
			continue
		var d := c.distance_to(near)
		if d < bd:
			bd = d
			best = b.id
	if best < 0:
		return false
	var blk: Dictionary = m.blocks[best]
	blk.kind = "facility"
	blk.special = kind
	# Reihen dieses Blocks entfernen, eine Ganzblock-Reihe einsetzen
	var front := "n"
	var rank := -1
	for f in ["n", "s", "w", "e"]:
		var rk := _side_rank(blk, f)
		if rk > rank:
			rank = rk
			front = f
	var kept := []
	for row in _rows:
		if row.block != best:
			kept.append(row)
	_rows = kept
	var r2: Rect2 = blk.inner
	var length: float = r2.size.x if front in ["n", "s"] else r2.size.y
	_rows.append({"block": best, "rect": r2, "front": front, "length": length,
		"depth": r2.size.y if front in ["n", "s"] else r2.size.x, "zone": blk.zone,
		"free0": length, "free1": length, "claims": [{"t0": 0.0, "t1": length, "kind": kind, "extra": {}}],
		"rank": rank})
	return true


var _facility_pos: Dictionary = {}  # kind -> [Vector2]


func _too_close(kind: String, p: Vector2, d: float) -> bool:
	for q in _facility_pos.get(kind, []):
		if q.distance_to(p) < d:
			return true
	return false


func _note_facility(kind: String, p: Vector2) -> void:
	if not _facility_pos.has(kind):
		_facility_pos[kind] = []
	_facility_pos[kind].append(p)


## Abschnitt einer Reihe beanspruchen. Gibt true bei Erfolg zurueck.
func _claim_row(kind: String, zones: Array, near := Vector2.INF, min_dist_same := 0.0,
		extra := {}, min_rank := 0, width_override := 0.0) -> bool:
	var spec: Dictionary = CityCatalog.BUILDING_KINDS[kind]
	var need_w: float = width_override if width_override > 0.0 else spec.footprint[0] + 2.0 * spec.setback[1]
	var need_d: float = spec.footprint[2] + spec.setback[0] + spec.setback[2]
	var cands := []
	for i in _rows.size():
		var row: Dictionary = _rows[i]
		if not row.zone in zones or row.rank < min_rank:
			continue
		if row.depth < need_d or row.free1 - row.free0 < need_w:
			continue
		cands.append(i)
	if cands.is_empty():
		return false
	var pick := -1
	if near != Vector2.INF:
		var bd := INF
		for i in cands:
			var c: Vector2 = _rows[i].rect.get_center()
			if min_dist_same > 0.0 and _too_close(kind, c, min_dist_same):
				continue
			var d := c.distance_to(near)
			if d < bd:
				bd = d
				pick = i
	else:
		_shuffle(cands)
		for i in cands:
			var c2: Vector2 = _rows[i].rect.get_center()
			if min_dist_same > 0.0 and _too_close(kind, c2, min_dist_same):
				continue
			pick = i
			break
	if pick < 0:
		return false
	var row2: Dictionary = _rows[pick]
	var w: float = minf(need_w + rng.randf_range(0.0, 10.0), row2.free1 - row2.free0)
	if row2.free1 - row2.free0 - w < 10.0:
		w = row2.free1 - row2.free0  # Rest nicht zu klein lassen
	var from_start := rng.randf() < 0.5
	var t0: float
	if from_start:
		t0 = row2.free0
		row2.free0 += w
	else:
		t0 = row2.free1 - w
		row2.free1 -= w
	row2.claims.append({"t0": t0, "t1": t0 + w, "kind": kind, "extra": extra})
	_note_facility(kind, row2.rect.get_center())
	return true


func _place_facilities() -> void:
	var f := pow(m.size / 1800.0, 2.0)
	var civic_like := ["civic", "downtown", "business", "oldtown"]
	_claim_block("city_hall", ["civic"] + civic_like, center)
	_claim_block("parliament", ["civic"] + civic_like, center)
	_claim_block("station", ["downtown", "business", "commercial"], center)
	var uni_center := center + Vector2(cos(_sector.university), sin(_sector.university)) * m.size * 0.25
	_claim_block("university", ["university"], uni_center)
	if f > 0.6:
		_claim_block("university", ["university", "apartments"], uni_center, 120.0)
	# Krankenhaeuser auf beiden Flussseiten
	for side in [-1.0, 1.0]:
		var p := Vector2(center.x + side * m.size * 0.2, m.river.z + side * m.size * 0.18)
		if not _claim_row("hospital", ["apartments", "residential", "civic", "commercial", "highrise", "university", "poor"], p, 400.0):
			_claim_block("hospital", ["apartments", "residential", "civic", "commercial", "highrise"], p, 400.0)
	var everywhere := ["downtown", "business", "commercial", "oldtown", "apartments", "highrise",
		"residential", "suburb", "poor", "slum", "industrial", "university", "civic", "rich"]
	var resid := ["apartments", "residential", "suburb", "highrise", "poor", "rich", "university"]
	for i in maxi(3, roundi(5 * f)):
		_claim_row("police", everywhere, _spread_point(i, maxi(3, roundi(5 * f))), 300.0, {}, 1)
	for i in maxi(2, roundi(3 * f)):
		_claim_row("fire_station", everywhere, _spread_point(i + 7, maxi(2, roundi(3 * f))), 350.0, {}, 1)
	for i in maxi(3, roundi(7 * f)):
		_claim_row("school", resid, _spread_point(i + 3, maxi(3, roundi(7 * f))), 250.0)
	for i in maxi(3, roundi(6 * f)):
		_claim_row("clinic", resid + ["commercial", "downtown"], Vector2.INF, 180.0)
	_claim_row("bus_depot", ["industrial", "poor"], Vector2.INF, 0.0, {}, 1)
	for i in maxi(1, roundi(2 * f)):
		_claim_row("shelter", ["poor", "slum", "industrial"], Vector2.INF, 200.0)
	for i in maxi(2, roundi(3 * f)):
		_claim_row("homeless_camp", ["slum", "poor", "industrial"], Vector2.INF, 150.0)
	for i in maxi(4, roundi(9 * f)):
		_claim_row("supermarket", ["apartments", "residential", "commercial", "highrise", "poor", "suburb"], Vector2.INF, 220.0, {"business": "supermarket"})
	for i in maxi(2, roundi(4 * f)):
		_claim_row("gas_station", ["suburb", "commercial", "industrial", "residential"], Vector2.INF, 300.0, {"business": "gas_station"}, 3)
	# Benannte Maerkte
	var markets := CityCatalog.NAMED_MARKETS.duplicate()
	var max_markets := clampi(roundi(16 * f), 10, 16)
	var placed := 0
	for mk in markets:
		if placed >= max_markets:
			break
		var ok := _claim_row(mk.kind, mk.zones, Vector2.INF, 120.0,
			{"business": mk.type, "name": mk.name, "color": mk.color})
		if not ok:
			ok = _claim_row(mk.kind, ["commercial", "apartments", "downtown", "oldtown", "poor", "residential", "highrise"], Vector2.INF, 120.0,
				{"business": mk.type, "name": mk.name, "color": mk.color})
		if ok:
			placed += 1
	# Einzelhandel-Ankergeschaefte
	for t in ["furniture", "hardware", "electronics", "car_dealer", "cinema", "gym"]:
		for i in maxi(1, roundi(1.5 * f)):
			_claim_row("shop", ["commercial", "industrial", "downtown", "apartments"], Vector2.INF, 150.0, {"business": t})
	_claim_row("shop", ["civic", "downtown", "business", "commercial"], center, 0.0, {"business": "job_center"})
	for i in maxi(2, roundi(3 * f)):
		_claim_row("shop", ["downtown", "business", "commercial", "rich"], Vector2.INF, 150.0, {"business": "real_estate"})


func _spread_point(i: int, n: int) -> Vector2:
	var a := TAU * float(i) / float(maxi(n, 1)) + rng.randf_range(-0.3, 0.3)
	var r := m.size * (0.18 + 0.22 * float(i % 2))
	return center + Vector2(cos(a), sin(a)) * r


# ============================================================ 9. Grundstuecke und Gebaeude

const LOT_WIDTH := {
	"downtown": [28.0, 44.0], "business": [26.0, 40.0], "commercial": [15.0, 26.0], "oldtown": [10.0, 17.0],
	"apartments": [20.0, 32.0], "highrise": [28.0, 40.0], "residential": [14.0, 20.0], "suburb": [16.0, 24.0],
	"rich": [30.0, 44.0], "estates": [52.0, 80.0], "poor": [12.0, 22.0], "slum": [12.0, 22.0],
	"industrial": [38.0, 66.0], "university": [24.0, 40.0], "civic": [28.0, 44.0], "park": [20.0, 30.0],
}

const ZONE_KINDS := {
	"downtown":    {"tower": 4.0, "office": 3.0, "mixed": 3.0, "highrise_res": 1.0, "restaurant": 0.5},
	"business":    {"office": 5.0, "tower": 2.0, "mixed": 2.0, "restaurant": 0.5},
	"commercial":  {"mixed": 5.0, "shop": 3.0, "restaurant": 1.0, "office": 1.0, "apartment": 1.0},
	"oldtown":     {"mixed": 6.0, "townhouse": 2.0, "restaurant": 1.0, "shop": 1.0},
	"apartments":  {"apartment": 6.0, "mixed": 2.0, "townhouse": 1.0},
	"highrise":    {"highrise_res": 5.0, "apartment": 2.0, "mixed": 1.0},
	"residential": {"house": 6.0, "townhouse": 2.0, "apartment": 1.0},
	"suburb":      {"house": 8.0, "townhouse": 1.0},
	"rich":        {"villa": 8.0, "house": 1.0},
	"estates":     {"estate": 1.0},
	"poor":        {"tenement": 5.0, "townhouse": 1.0, "mixed": 2.0, "abandoned": 0.6, "shop": 0.5},
	"slum":        {"tenement": 4.0, "abandoned": 2.0, "mixed": 1.0},
	"industrial":  {"warehouse": 5.0, "factory": 4.0},
	"university":  {"apartment": 2.0, "mixed": 2.0, "office": 1.0},
	"civic":       {"office": 3.0, "mixed": 1.0},
}

const FALLBACK_KINDS := ["mixed", "apartment", "townhouse", "shop", "house", "tenement"]


func _make_lots_and_buildings() -> void:
	for row in _rows:
		var segs := []
		for c in row.claims:
			segs.append(c)
		# freie Abschnitte in normale Grundstuecke teilen
		var lw: Array = LOT_WIDTH.get(row.zone, [16.0, 26.0])
		var t: float = row.free0
		while row.free1 - t >= lw[0]:
			var w := rng.randf_range(lw[0], lw[1])
			if row.free1 - t - w < lw[0]:
				w = row.free1 - t
			segs.append({"t0": t, "t1": t + w, "kind": "", "extra": {}})
			t += w
		if row.free1 - t > 0.5:
			# Restflaeche als Gruenflaeche
			segs.append({"t0": t, "t1": row.free1, "kind": "green", "extra": {}})
		segs.sort_custom(func(a, b): return a.t0 < b.t0)
		for s in segs:
			_make_lot(row, s)


func _lot_rect(row: Dictionary, t0: float, t1: float) -> Rect2:
	var r: Rect2 = row.rect
	if row.front in ["n", "s"]:
		return Rect2(Vector2(r.position.x + t0, r.position.y), Vector2(t1 - t0, r.size.y))
	return Rect2(Vector2(r.position.x, r.position.y + t0), Vector2(r.size.x, t1 - t0))


## Kante und Seite, an der ein Punkt der Blockfront liegt.
func _front_edge(b: Dictionary, front: String, lateral_pos: float) -> Dictionary:
	var gx: int
	var gz: int
	var axis: String
	match front:
		"n":
			gz = b.gz0
			axis = "x"
		"s":
			gz = b.gz1
			axis = "x"
		"w":
			gx = b.gx0
			axis = "z"
		_:
			gx = b.gx1
			axis = "z"
	if axis == "x":
		for i in range(b.gx0, b.gx1):
			var x0: float = m.x_lines[i].pos
			var x1: float = m.x_lines[i + 1].pos
			if lateral_pos >= x0 - 0.01 and lateral_pos <= x1 + 0.01:
				var eid := m.edge_between(m.node_at_grid(i, gz), m.node_at_grid(i + 1, gz))
				# Block noerdlich der Strasse (front s) liegt links (-1)
				return {"edge": eid, "side": -1 if front == "s" else 1}
	else:
		for j in range(b.gz0, b.gz1):
			var z0: float = m.z_lines[j].pos
			var z1: float = m.z_lines[j + 1].pos
			if lateral_pos >= z0 - 0.01 and lateral_pos <= z1 + 0.01:
				var eid2 := m.edge_between(m.node_at_grid(gx, j), m.node_at_grid(gx, j + 1))
				# Kante a->b zeigt nach +z, rechts davon ist -x: Block westlich (front e) liegt rechts
				return {"edge": eid2, "side": 1 if front == "e" else -1}
	return {"edge": -1, "side": 1}


func _make_lot(row: Dictionary, seg: Dictionary) -> void:
	var b: Dictionary = m.blocks[row.block]
	var rect := _lot_rect(row, seg.t0, seg.t1)
	var lid := m.lots.size()
	var lat := rect.get_center().x if row.front in ["n", "s"] else rect.get_center().y
	var fe := _front_edge(b, row.front, lat)
	var lot := {"id": lid, "block": b.id, "rect": rect, "front": row.front, "edge": fe.edge,
		"side": fe.side, "zone": row.zone, "usage": "building", "building": -1}
	m.lots.append(lot)
	if seg.kind == "green" or b.kind == "park":
		lot.usage = "green"
		return
	var kind: String = seg.kind
	var extra: Dictionary = seg.extra
	if kind == "":
		kind = _pick_weighted(ZONE_KINDS.get(row.zone, {"house": 1.0}))
	var bld := _make_building(lot, kind, extra)
	if bld.is_empty():
		# Ersatz: kleinere Bauform, sonst Gruenflaeche
		for k in FALLBACK_KINDS:
			if k == kind:
				continue
			if not _kind_allowed_in_zone(k, row.zone):
				continue
			bld = _make_building(lot, k, {})
			if not bld.is_empty():
				break
	if bld.is_empty():
		lot.usage = "green"


func _kind_allowed_in_zone(k: String, zone: String) -> bool:
	if zone in ["rich", "estates", "suburb", "residential"]:
		return k in ["house", "townhouse", "villa"]
	if zone == "industrial":
		return k in ["warehouse", "shop"]
	return true


## Lokales Rahmenwerk eines Grundstuecks: Front-Richtung (zur Strasse),
## Laengsrichtung, Breite und Tiefe.
func _lot_frame(lot: Dictionary) -> Dictionary:
	var r: Rect2 = lot.rect
	match lot.front:
		"n": return {"out": Vector2(0, -1), "along": Vector2(1, 0), "w": r.size.x, "d": r.size.y,
			"front_mid": Vector2(r.get_center().x, r.position.y)}
		"s": return {"out": Vector2(0, 1), "along": Vector2(1, 0), "w": r.size.x, "d": r.size.y,
			"front_mid": Vector2(r.get_center().x, r.end.y)}
		"w": return {"out": Vector2(-1, 0), "along": Vector2(0, 1), "w": r.size.y, "d": r.size.x,
			"front_mid": Vector2(r.position.x, r.get_center().y)}
	return {"out": Vector2(1, 0), "along": Vector2(0, 1), "w": r.size.y, "d": r.size.x,
		"front_mid": Vector2(r.end.x, r.get_center().y)}


## Rechteck im Grundstuecksrahmen: lateral a0..a1 (ab Mitte), Tiefe d0..d1 (ab Front).
func _frame_rect(fr: Dictionary, a0: float, a1: float, d0: float, d1: float) -> Rect2:
	var p1: Vector2 = fr.front_mid + fr.along * a0 - fr.out * d0
	var p2: Vector2 = fr.front_mid + fr.along * a1 - fr.out * d1
	var mn := Vector2(minf(p1.x, p2.x), minf(p1.y, p2.y))
	var mx := Vector2(maxf(p1.x, p2.x), maxf(p1.y, p2.y))
	return Rect2(mn, mx - mn)


func _make_building(lot: Dictionary, kind: String, extra: Dictionary) -> Dictionary:
	var spec: Dictionary = CityCatalog.BUILDING_KINDS[kind]
	var fr := _lot_frame(lot)
	var sb: Array = spec.setback
	var fp: Array = spec.footprint
	var avail_w: float = fr.w - 2.0 * sb[1]
	var avail_d: float = fr.d - sb[0] - sb[2]
	var want_drive: bool = spec.get("driveway", false)
	var drive_w := 3.4
	if want_drive and avail_w - drive_w - 1.0 >= fp[0]:
		avail_w -= drive_w + 1.0
	else:
		want_drive = false
	if avail_w < fp[0] or avail_d < fp[2]:
		return {}
	var continuous: bool = kind in ["townhouse", "mixed", "tenement", "office", "tower", "shop", "restaurant"]
	var w: float = minf(fp[1], avail_w)
	if not continuous:
		w = maxf(fp[0], w * rng.randf_range(0.82, 1.0))
	var d: float = minf(fp[3], avail_d)
	d = maxf(fp[2], d * rng.randf_range(0.8, 1.0))
	var a_center := 0.0
	var drive_sign := 1.0 if rng.randf() < 0.5 else -1.0
	if want_drive:
		a_center = -drive_sign * (drive_w + 1.0) * 0.5
	var foot := _frame_rect(fr, a_center - w * 0.5, a_center + w * 0.5, sb[0], sb[0] + d)
	var floors: int = rng.randi_range(spec.floors[0], spec.floors[1])
	var zone: String = lot.zone
	if kind == "tower" and zone == "downtown":
		var dc := foot.get_center().distance_to(center) / (m.size * 0.15)
		floors = int(lerpf(float(spec.floors[1]), float(spec.floors[0]), clampf(dc, 0.0, 1.0))) + rng.randi_range(-3, 3)
		floors = clampi(floors, spec.floors[0], spec.floors[1])
	var height: float = floors * spec.floor_h + (1.2 if floors > 0 else 0.0)
	var bid := m.buildings.size()
	var door: Vector2 = fr.front_mid + fr.along * a_center - fr.out * sb[0]
	var walk_len: float = sb[0]
	var walkway := _frame_rect(fr, a_center - 0.9, a_center + 0.9, 0.0, walk_len) if walk_len > 0.05 else Rect2()
	var fe := {"edge": lot.edge, "side": lot.side}
	var sidewalk_point: Vector2 = door + fr.out * walk_len
	if fe.edge >= 0:
		var e: Dictionary = m.edges[fe.edge]
		var sw: float = e.sidewalk[1] if fe.side > 0 else e.sidewalk[0]
		sidewalk_point = fr.front_mid + fr.along * a_center + fr.out * (sw * 0.5)
	var driveway := Rect2()
	if want_drive:
		var a0: float = a_center + drive_sign * (w * 0.5 + 0.5)
		var a1: float = a0 + drive_sign * drive_w
		driveway = _frame_rect(fr, minf(a0, a1), maxf(a0, a1), 0.0, sb[0] + d * 0.6)
	var parking := Rect2()
	if spec.get("parking_front", false) and sb[0] >= 12.0:
		parking = _frame_rect(fr, a_center - w * 0.5, a_center + w * 0.5, 0.8, sb[0] - 1.2)
	var style := _make_style(kind, zone, floors, foot)
	var bld := {"id": bid, "lot": lot.id, "block": lot.block, "kind": kind, "label": spec.label,
		"footprint": foot, "floors": floors, "height": height, "floor_h": spec.floor_h,
		"front": lot.front, "front_dir": fr.out, "door": door, "sidewalk_point": sidewalk_point,
		"walkway": walkway, "driveway": driveway, "parking": parking,
		"edge": fe.edge, "side": fe.side, "zone": zone, "district": m.blocks[lot.block].district,
		"style": style, "business_type": str(extra.get("business", "")), "name": str(extra.get("name", "")),
		"brand_color": extra.get("color", Color.WHITE), "res_units": 0, "biz_units": int(spec.get("biz", 0)),
		"open_air": spec.get("open_air", false), "address": ""}
	if spec.get("res_fixed", 0) > 0:
		bld.res_units = spec.res_fixed
	elif spec.res_units > 0:
		var res_floors: int = floors - (1 if bld.biz_units > 0 else 0)
		bld.res_units = maxi(1, res_floors * spec.res_units)
	if kind == "market_square":
		bld.style["stalls"] = _make_stalls(foot, fr)
	if kind == "homeless_camp":
		bld.style["tents"] = _make_tents(foot)
	if fe.edge >= 0:
		bld.address = "%s %d" % [m.edges[fe.edge].name, 1 + bid % 97]
	m.buildings.append(bld)
	lot.building = bid
	# Zufahrt / Parkplatz am Bordstein reservieren
	if fe.edge >= 0:
		var e2: Dictionary = m.edges[fe.edge]
		var tw := _edge_t(e2, door)
		m.reserve_curb(fe.edge, fe.side, tw - 1.2, tw + 1.2, "entrance")
		if want_drive:
			var td := _edge_t(e2, driveway.get_center())
			if not m.reserve_curb(fe.edge, fe.side, td - 2.4, td + 2.4, "driveway"):
				bld.driveway = Rect2()
		if parking.has_area():
			var tp := _edge_t(e2, parking.get_center())
			var access_ok := false
			for off in [w * 0.5 - 4.0, -(w * 0.5 - 4.0), 0.0]:
				if m.reserve_curb(fe.edge, fe.side, tp + off - 3.2, tp + off + 3.2, "driveway"):
					bld["parking_access_t"] = tp + off
					access_ok = true
					break
			if access_ok:
				_make_parking_area(bld, parking, fr)
			else:
				bld.parking = Rect2()
	return bld


func _edge_t(e: Dictionary, p: Vector2) -> float:
	var pa: Vector2 = m.nodes[e.a].pos
	return (p - pa).dot(m.edge_dir(e))


func _make_style(kind: String, zone: String, floors: int, foot: Rect2) -> Dictionary:
	var wealth: float = CityCatalog.ZONES[zone].wealth
	var facade := "plaster"
	var roof := "flat"
	match kind:
		"house", "villa", "estate", "townhouse":
			facade = ["plaster", "brick", "wood", "stone"][rng.randi_range(0, 3)] if wealth > 0.5 else ["plaster", "brick"][rng.randi_range(0, 1)]
			roof = ["gable", "hip", "gable", "flat"][rng.randi_range(0, 3)]
			if kind == "villa" and rng.randf() < 0.5:
				roof = "flat"
				facade = ["plaster", "stone", "glass"][rng.randi_range(0, 2)]
		"tower", "office":
			facade = ["glass", "glass", "concrete", "steel"][rng.randi_range(0, 3)]
		"highrise_res":
			facade = ["concrete", "plaster", "brick"][rng.randi_range(0, 2)]
		"tenement", "abandoned":
			facade = ["brick", "plaster", "concrete"][rng.randi_range(0, 2)]
		"mixed", "apartment":
			facade = ["plaster", "brick", "stone", "plaster"][rng.randi_range(0, 3)]
			if zone == "oldtown":
				roof = "gable" if rng.randf() < 0.6 else "mansard"
		"warehouse", "factory", "bus_depot":
			facade = ["metal", "concrete", "brick"][rng.randi_range(0, 2)]
			roof = "sawtooth" if kind == "factory" and rng.randf() < 0.5 else "flat"
		"city_hall", "parliament", "station", "university":
			facade = "stone"
			roof = ["flat", "hip", "dome"][rng.randi_range(0, 2)] if kind != "station" else "vault"
		"school", "hospital", "police", "fire_station", "clinic", "shelter":
			facade = ["plaster", "brick", "concrete"][rng.randi_range(0, 2)]
	# Farbpaletten nach Wohlstand
	var pal_rich := [Color(0.93, 0.91, 0.86), Color(0.86, 0.82, 0.74), Color(0.95, 0.94, 0.92), Color(0.78, 0.74, 0.68)]
	var pal_mid := [Color(0.88, 0.80, 0.66), Color(0.80, 0.70, 0.60), Color(0.72, 0.78, 0.80), Color(0.90, 0.86, 0.78), Color(0.76, 0.62, 0.52), Color(0.84, 0.74, 0.56)]
	var pal_poor := [Color(0.62, 0.58, 0.52), Color(0.56, 0.52, 0.50), Color(0.66, 0.60, 0.50), Color(0.58, 0.60, 0.58)]
	var pal: Array = pal_rich if wealth > 0.8 else (pal_poor if wealth < 0.3 else pal_mid)
	var col: Color = pal[rng.randi_range(0, pal.size() - 1)]
	col = col.lightened(rng.randf_range(-0.06, 0.06))
	return {"facade": facade, "roof": roof, "color": col,
		"trim": col.darkened(0.25), "roof_color": [Color(0.45, 0.22, 0.16), Color(0.30, 0.30, 0.32), Color(0.55, 0.30, 0.20), Color(0.25, 0.27, 0.30)][rng.randi_range(0, 3)],
		"window": rng.randi_range(0, 3), "balconies": rng.randf() < (0.5 if kind in ["apartment", "highrise_res", "mixed"] else 0.0),
		"condition": clampf(wealth + rng.randf_range(-0.15, 0.15), 0.0, 1.0),
		"variant": rng.randi_range(0, 7), "setback_top": floors > 14 and rng.randf() < 0.5}


func _make_stalls(foot: Rect2, fr: Dictionary) -> Array:
	var stalls := []
	var sw := 3.2
	var sd := 2.6
	var gap := 3.0
	var cols := int((foot.size.x - 2.0) / (sw + 1.2))
	var rows := int((foot.size.y - 2.0) / (sd + gap))
	for i in maxi(cols, 0):
		for j in maxi(rows, 0):
			var p := foot.position + Vector2(1.0 + i * (sw + 1.2) + sw * 0.5, 1.0 + j * (sd + gap) + sd * 0.5)
			stalls.append({"pos": p, "size": Vector2(sw, sd), "color_idx": rng.randi_range(0, 5),
				"rot": 0.0 if fr.out.y != 0.0 else PI * 0.5})
	return stalls


func _make_tents(foot: Rect2) -> Array:
	var tents := []
	var n := rng.randi_range(4, 9)
	for i in n * 3:
		if tents.size() >= n:
			break
		var p := Vector2(rng.randf_range(foot.position.x + 1.5, foot.end.x - 1.5), rng.randf_range(foot.position.y + 1.5, foot.end.y - 1.5))
		var ok := true
		for t in tents:
			if t.pos.distance_to(p) < 3.2:
				ok = false
		if ok:
			tents.append({"pos": p, "rot": rng.randf_range(0, TAU), "color_idx": rng.randi_range(0, 4)})
	return tents


func _make_parking_area(bld: Dictionary, rect: Rect2, fr: Dictionary) -> void:
	var spots := []
	var horizontal_front: bool = fr.out.y != 0.0
	# Reihen parallel zur Strasse, Stellplaetze 2.6 x 5.0, Fahrgasse 6 m
	var along_len: float = rect.size.x if horizontal_front else rect.size.y
	var depth_len: float = rect.size.y if horizontal_front else rect.size.x
	var n_along := int((along_len - 1.0) / 2.6)
	var rows_possible := int((depth_len - 6.0) / 5.0)
	rows_possible = clampi(rows_possible, 0, 2)
	for r in rows_possible:
		for i in n_along:
			var a := -along_len * 0.5 + 0.5 + 1.3 + i * 2.6
			var dd: float = (2.5 if r == 0 else depth_len - 2.5)
			var c: Vector2
			if horizontal_front:
				c = Vector2(rect.get_center().x + a, rect.position.y + dd)
			else:
				c = Vector2(rect.position.x + dd, rect.get_center().y + a)
			var rot := 0.0 if horizontal_front else PI * 0.5
			spots.append({"pos": c, "rot": rot})
	var pid := m.parking_areas.size()
	m.parking_areas.append({"id": pid, "rect": rect, "building": bld.id, "spots": spots})


# ============================================================ 10. Geschaefte

func _assign_businesses() -> void:
	var used := {}
	var color_i := 0
	for b in m.buildings:
		if b.biz_units <= 0:
			continue
		if b.business_type == "":
			var mix: Dictionary = CityCatalog.ZONE_BUSINESS_MIX.get(b.zone, {"convenience": 1.0})
			if b.kind == "restaurant":
				mix = {"restaurant": 3.0, "fastfood": 1.0, "cafe": 1.0}
			b.business_type = _pick_weighted(mix)
		var bt: String = b.business_type
		if b.name == "":
			var names: Array = CityCatalog.BUSINESS_NAMES.get(bt, ["Laden"])
			var nm: String = names[rng.randi_range(0, names.size() - 1)]
			var count: int = used.get(nm, 0)
			used[nm] = count + 1
			if count > 0:
				var dist: String = m.districts[b.district].name if b.district >= 0 else ""
				nm = "%s %s" % [nm, dist] if dist != "" else "%s %d" % [nm, count + 1]
			b.name = nm
			b.brand_color = CityCatalog.BRAND_COLORS[(nm.hash() & 0x7fffffff) % CityCatalog.BRAND_COLORS.size()]
			color_i += 1
	# Einrichtungen benennen
	var counters := {}
	for b in m.buildings:
		if b.name != "" or not (b.kind in CityCatalog.CIVIC_KINDS):
			continue
		var dist2: String = m.districts[b.district].name if b.district >= 0 else ""
		counters[b.kind] = counters.get(b.kind, 0) + 1
		match b.kind:
			"school": b.name = "%s-Schule" % dist2 if dist2 != "" else "Gesamtschule %d" % counters[b.kind]
			"university": b.name = "Universität Havenbrück" if counters[b.kind] == 1 else "Technische Hochschule Havenbrück"
			"hospital": b.name = "Klinikum %s" % dist2
			"clinic": b.name = "Praxis %s" % CityCatalog.LAST_NAMES[rng.randi_range(0, CityCatalog.LAST_NAMES.size() - 1)]
			"police": b.name = "Polizeiwache %s" % dist2
			"fire_station": b.name = "Feuerwache %s" % dist2
			"city_hall": b.name = "Rathaus Havenbrück"
			"parliament": b.name = "Regierungspalast"
			"station": b.name = "Havenbrück Hauptbahnhof"
			"bus_depot": b.name = "Busbetriebshof Havenbrück"
			"shelter": b.name = "Notunterkunft %s" % dist2
		b.brand_color = Color(0.2, 0.35, 0.6)


# ============================================================ 11. Requisiten

const PROP_CELL := 8.0
var _prop_grid: Dictionary = {}


func _add_prop(kind: String, pos: Vector2, rot: float, r: float, layer: String, ref := -1, extra := {}) -> int:
	var id := m.props.size()
	var p := {"id": id, "kind": kind, "pos": pos, "rot": rot, "r": r, "layer": layer, "ref": ref}
	for k in extra:
		p[k] = extra[k]
	m.props.append(p)
	if not p.has("rect"):
		var c := Vector2i(floori(pos.x / PROP_CELL), floori(pos.y / PROP_CELL))
		if not _prop_grid.has(c):
			_prop_grid[c] = []
		_prop_grid[c].append(id)
	return id


## true, wenn an pos mit Radius r kein anderes punktfoermiges Objekt steht.
func _prop_clear(pos: Vector2, r: float) -> bool:
	var c := Vector2i(floori(pos.x / PROP_CELL), floori(pos.y / PROP_CELL))
	for dx in [-1, 0, 1]:
		for dz in [-1, 0, 1]:
			for oid in _prop_grid.get(c + Vector2i(dx, dz), []):
				var o: Dictionary = m.props[oid]
				if o.pos.distance_to(pos) < o.r + r + 0.1:
					return false
	return true


func _make_parks() -> void:
	for b in m.blocks:
		var rect: Rect2
		var name := ""
		if b.kind == "park":
			rect = b.inner
			name = m.districts[b.district].name if b.district >= 0 and m.districts[b.district].zone == "park" else "Park %s" % (m.districts[b.district].name if b.district >= 0 else "")
		elif b.kind == "promenade":
			rect = b.inner
			name = "Uferpromenade"
		else:
			continue
		var pid := m.parks.size()
		var park := {"id": pid, "rect": rect, "name": name, "block": b.id, "paths": [], "features": [],
			"promenade": b.kind == "promenade"}
		m.parks.append(park)
		if b.kind == "promenade":
			_promenade_props(b, park)
			continue
		var c := rect.get_center()
		var pw := 3.0
		park.paths.append(Rect2(Vector2(rect.position.x, c.y - pw * 0.5), Vector2(rect.size.x, pw)))
		park.paths.append(Rect2(Vector2(c.x - pw * 0.5, rect.position.y), Vector2(pw, rect.size.y)))
		if rect.size.x > 40.0 and rect.size.y > 40.0:
			park.features.append({"kind": "fountain", "pos": c, "r": 4.5})
			_add_prop("fountain", c, 0.0, 4.5, "park", pid)
		# Spielplatz in einem Quadranten
		var q := Vector2(rect.size.x * 0.25, rect.size.y * 0.25)
		if rect.size.x > 50.0 and rect.size.y > 40.0:
			var pg := c + Vector2(-q.x, q.y)
			park.features.append({"kind": "playground", "pos": pg, "r": 7.0})
			_add_prop("playground", pg, 0.0, 7.0, "park", pid)
		# Baenke entlang der Wege
		for k in range(1, 4):
			var bx := rect.position.x + rect.size.x * k / 4.0
			_try_prop("bench", Vector2(bx, c.y - pw * 0.5 - 0.8), 0.0, 0.9, "park", pid)
			var bz := rect.position.y + rect.size.y * k / 4.0
			_try_prop("bench", Vector2(c.x + pw * 0.5 + 0.8, bz), PI * 0.5, 0.9, "park", pid)
		_try_prop("bin", Vector2(c.x + pw * 0.5 + 1.0, c.y + pw * 0.5 + 1.0), 0.0, 0.4, "park", pid)
		# Baeume streuen
		var n := int(rect.get_area() / 160.0)
		for i in n:
			var p := Vector2(rng.randf_range(rect.position.x + 2.0, rect.end.x - 2.0), rng.randf_range(rect.position.y + 2.0, rect.end.y - 2.0))
			var blocked := false
			for path in park.paths:
				if path.grow(2.0).has_point(p):
					blocked = true
			for f in park.features:
				if f.pos.distance_to(p) < f.r + 2.5:
					blocked = true
			if blocked:
				continue
			_try_prop("tree", p, rng.randf_range(0, TAU), 1.6, "park", pid, {"variant": rng.randi_range(0, 3), "scale": rng.randf_range(0.8, 1.3)})


func _promenade_props(b: Dictionary, park: Dictionary) -> void:
	var r: Rect2 = b.inner
	var north: bool = b.north_bank
	# Promenade beginnt am Bordstein der Uferstrasse; Gehweg freilassen
	var sw: float = CityModel.PROFILES["embankment"].sidewalk
	var tree_z: float = r.position.y + sw + 2.0 if north else r.end.y - sw - 2.0
	var rail_z: float = r.end.y - 0.4 if north else r.position.y + 0.4
	var x := r.position.x + 6.0
	while x < r.end.x - 6.0:
		_try_prop("tree", Vector2(x, tree_z), rng.randf_range(0, TAU), 1.4, "park", park.id, {"variant": rng.randi_range(0, 3), "scale": rng.randf_range(0.9, 1.2)})
		_try_prop("bench", Vector2(x + 6.0, rail_z + (-1.6 if north else 1.6)), 0.0 if north else PI, 0.9, "park", park.id)
		_try_prop("lamp", Vector2(x + 3.0, tree_z), 0.0, 0.3, "park", park.id)
		x += 14.0
	park.features.append({"kind": "railing", "from": Vector2(r.position.x, rail_z), "to": Vector2(r.end.x, rail_z), "r": 0.2})


func _try_prop(kind: String, pos: Vector2, rot: float, r: float, layer: String, ref := -1, extra := {}) -> int:
	if not _prop_clear(pos, r):
		return -1
	# Schneller Vorabtest gegen Gebaeude und Wege in der Naehe; endgueltig prueft der Validator.
	for b in _buildings_near(pos, 40.0):
		if b.footprint.grow(r + 0.3).has_point(pos):
			return -1
		if b.walkway.has_area() and b.walkway.grow(r + 0.3).has_point(pos):
			return -1
		if b.driveway.has_area() and b.driveway.grow(r + 0.3).has_point(pos):
			return -1
		if b.parking.has_area() and b.parking.grow(r + 0.2).has_point(pos):
			return -1
	return _add_prop(kind, pos, rot, r, layer, ref, extra)


var _bgrid: Dictionary = {}


func _buildings_near(p: Vector2, radius: float) -> Array:
	if _bgrid.is_empty() and not m.buildings.is_empty():
		for b in m.buildings:
			var k := Vector2i(floori(b.footprint.get_center().x / 100.0), floori(b.footprint.get_center().y / 100.0))
			if not _bgrid.has(k):
				_bgrid[k] = []
			_bgrid[k].append(b)
	var out := []
	var k0 := Vector2i(floori(p.x / 100.0), floori(p.y / 100.0))
	var span := int(ceil(radius / 100.0)) + 1
	for dx in range(-span, span + 1):
		for dz in range(-span, span + 1):
			for b in _bgrid.get(k0 + Vector2i(dx, dz), []):
				out.append(b)
	return out


func _make_street_props() -> void:
	# Ampeln, Schilder an Knoten
	for n in m.nodes:
		var arms := m.node_arms(n.id)
		for dirname in arms:
			var e: Dictionary = m.edges[arms[dirname]]
			var into_dir: Vector2 = (n.pos - m.nodes[m.edge_other(e, n.id)].pos).normalized()
			var right := CityModel.right_of(into_dir)
			var clr := m.edge_end_clearance(e, n.id)
			# Mast rechts der ankommenden Fahrbahn, vor dem Zebrastreifen
			var p: Vector2 = n.pos - into_dir * (clr + 0.6) + right * (e.width * 0.5 + 0.7)
			var sw: float = e.sidewalk[1] if (e.a != n.id) else e.sidewalk[0]
			if sw <= 0.0:
				continue
			var rot := atan2(-into_dir.x, -into_dir.y)
			match n.kind:
				"signal":
					_add_prop("traffic_light", p, rot, 0.35, "street", n.id, {"axis": e.axis, "edge": e.id, "arm_width": e.width})
				"stop":
					_add_prop("sign_stop", p, rot, 0.25, "street", n.id, {"edge": e.id})
				"yield":
					if e.axis != n.priority_axis:
						_add_prop("sign_yield", p, rot, 0.25, "street", n.id, {"edge": e.id})
				"roundabout":
					_add_prop("sign_roundabout", p, rot, 0.25, "street", n.id, {"edge": e.id})
		if n.kind != "pass":
			# Strassenschild an einer Ecke
			var q := m.quadrant_block(n.id, 1, 1)
			if q >= 0 and m.blocks[q].kind != "promenade":
				var curb: Rect2 = m.blocks[q].curb
				var cp := curb.position + Vector2(0.8, 0.8)
				if cp.distance_to(n.pos) < 30.0:
					var names := []
					for eid in n.edges:
						if not m.edges[eid].name in names:
							names.append(m.edges[eid].name)
					_add_prop("street_sign", cp, 0.0, 0.2, "street", n.id, {"names": names})
	# Bushaltestellen-Haeuschen
	for s in m.bus_stops:
		var e: Dictionary = m.edges[s.edge]
		var sw: float = e.sidewalk[1] if s.side > 0 else e.sidewalk[0]
		var off: float = s.side * (e.width * 0.5 + minf(1.4, sw * 0.45))
		var p := m.edge_point(e, s.t, off)
		var rot := atan2(-s.dir.x, -s.dir.y)
		_add_prop("bus_shelter", p, rot, 1.2, "street", s.id, {"stop": s.id, "dir": s.dir, "side": s.side})
	# Laternen, Baeume, Hydranten entlang der Kanten
	for e in m.edges:
		for side in [-1, 1]:
			var sw: float = e.sidewalk[1] if side > 0 else e.sidewalk[0]
			if sw <= 0.0:
				continue
			var lamp_off: float = side * (e.width * 0.5 + 0.55)
			var tree_off: float = side * (e.width * 0.5 + 1.25)
			var spacing := 30.0 if e.cls in ["arterial", "ring"] else 34.0
			var t := m.edge_end_clearance(e, e.a) + 6.0
			var t_end: float = e.length - m.edge_end_clearance(e, e.b) - 6.0
			var phase := 0.0 if side > 0 else spacing * 0.5
			t += phase
			while t < t_end:
				if m.reserve_curb(e.id, side, t - 0.5, t + 0.5, "lamp"):
					var p := m.edge_point(e, t, lamp_off)
					var dir := m.edge_dir(e)
					var face: Vector2 = CityModel.right_of(dir) * -float(side)
					_add_prop("lamp", p, atan2(face.x, face.y), 0.25, "street", e.id)
				t += spacing
			if sw >= 4.0 and not e.bridge:
				var tt := m.edge_end_clearance(e, e.a) + 12.0
				while tt < t_end - 4.0:
					if m.reserve_curb(e.id, side, tt - 1.0, tt + 1.0, "tree"):
						_add_prop("street_tree", m.edge_point(e, tt, tree_off), rng.randf_range(0, TAU), 0.8, "street", e.id,
							{"variant": rng.randi_range(0, 3), "scale": rng.randf_range(0.85, 1.15)})
					tt += rng.randf_range(11.0, 15.0)
			# Hydranten und Muelleimer sparsam
			var th := rng.randf_range(20.0, 60.0) + m.edge_end_clearance(e, e.a)
			while th < t_end:
				var kind := "hydrant" if rng.randf() < 0.5 else "bin"
				if m.reserve_curb(e.id, side, th - 0.4, th + 0.4, kind):
					_add_prop(kind, m.edge_point(e, th, side * (e.width * 0.5 + 0.5)), 0.0, 0.3, "street", e.id)
				th += rng.randf_range(70.0, 130.0)


func _make_lot_props() -> void:
	for b in m.buildings:
		var lot: Dictionary = m.lots[b.lot]
		var lr: Rect2 = lot.rect
		var fp: Rect2 = b.footprint
		match b.kind:
			"house", "villa", "estate":
				var n := 2 if b.kind == "house" else 5
				for i in n * 3:
					if n <= 0:
						break
					var p := Vector2(rng.randf_range(lr.position.x + 1.5, lr.end.x - 1.5), rng.randf_range(lr.position.y + 1.5, lr.end.y - 1.5))
					if _try_lot_prop("tree", p, 1.6, b, {"variant": rng.randi_range(0, 3), "scale": rng.randf_range(0.7, 1.1)}) >= 0:
						n -= 1
				# Hecke an der Grundstuecksfront mit Luecken fuer Weg und Zufahrt
				_front_hedge(b, lot)
				if b.kind != "house":
					_pool(b, lot)
			"apartment", "highrise_res", "school", "university", "hospital", "city_hall", "parliament":
				for i in 4:
					var p2 := Vector2(rng.randf_range(lr.position.x + 1.5, lr.end.x - 1.5), rng.randf_range(lr.position.y + 1.5, lr.end.y - 1.5))
					_try_lot_prop("tree", p2, 1.5, b, {"variant": rng.randi_range(0, 3), "scale": rng.randf_range(0.8, 1.2)})
			"warehouse", "factory":
				for i in 6:
					var p3 := Vector2(rng.randf_range(lr.position.x + 2.0, lr.end.x - 2.0), rng.randf_range(lr.position.y + 2.0, lr.end.y - 2.0))
					_try_lot_prop("container" if rng.randf() < 0.6 else "pallets", p3, 2.4, b, {"color_idx": rng.randi_range(0, 4)})
			"homeless_camp":
				for t in b.style.get("tents", []):
					pass  # Zelte sind Teil des Gebaeudes
	# Gruenflaechen-Grundstuecke mit Baeumen
	for lot in m.lots:
		if lot.usage != "green":
			continue
		var r: Rect2 = lot.rect
		var n2 := clampi(int(r.get_area() / 120.0), 0, 8)
		for i in n2:
			var p4 := Vector2(rng.randf_range(r.position.x + 1.5, r.end.x - 1.5), rng.randf_range(r.position.y + 1.5, r.end.y - 1.5))
			_try_prop("tree", p4, rng.randf_range(0, TAU), 1.5, "lot", -1, {"variant": rng.randi_range(0, 3), "scale": rng.randf_range(0.7, 1.2)})


var _lot_props: Dictionary = {}  # building id -> [prop ids]


func _try_lot_prop(kind: String, p: Vector2, r: float, b: Dictionary, extra := {}) -> int:
	var lr: Rect2 = m.lots[b.lot].rect
	if not lr.grow(-r).has_point(p):
		return -1
	# Sichtachse vor dem Eingang freihalten
	if p.distance_to(b.door) < r + 2.0:
		return -1
	for oid in _lot_props.get(b.id, []):
		var other: Dictionary = m.props[oid]
		if other.pos.distance_to(p) < other.r + r:
			return -1
	var id := _try_prop(kind, p, rng.randf_range(0, TAU), r, "lot", b.id, extra)
	if id >= 0:
		if not _lot_props.has(b.id):
			_lot_props[b.id] = []
		_lot_props[b.id].append(id)
	return id


func _front_hedge(b: Dictionary, lot: Dictionary) -> void:
	# Hecke als Segmente entlang der Grundstuecksfront, Luecken fuer Weg und Zufahrt.
	var fr := _lot_frame(lot)
	var half_w: float = fr.w * 0.5
	var gaps := []
	var dl := _lat(fr, b.door)
	gaps.append([dl - 1.4, dl + 1.4])
	if b.driveway.has_area():
		var dc: float = _lat(fr, b.driveway.get_center())
		gaps.append([dc - 2.4, dc + 2.4])
	gaps.sort_custom(func(x, y): return x[0] < y[0])
	var segs := []
	var a := -half_w + 0.4
	for g in gaps:
		if g[0] - a > 1.0:
			segs.append([a, g[0]])
		a = maxf(a, g[1])
	if half_w - 0.4 - a > 1.0:
		segs.append([a, half_w - 0.4])
	for sgm in segs:
		var rect := _frame_rect(fr, sgm[0], sgm[1], 0.15, 0.85)
		var mid: Vector2 = rect.get_center()
		var rot := atan2(fr.along.x, fr.along.y)
		var id := _add_prop("hedge", mid, rot, 0.35, "lot", b.id, {"rect": rect, "length": sgm[1] - sgm[0]})
		if not _lot_props.has(b.id):
			_lot_props[b.id] = []
		_lot_props[b.id].append(id)


func _lat(fr: Dictionary, p: Vector2) -> float:
	return (p - fr.front_mid).dot(fr.along)


func _pool(b: Dictionary, lot: Dictionary) -> void:
	var fr := _lot_frame(lot)
	var fp: Rect2 = b.footprint
	# Hinter dem Haus
	var back_depth: float = fr.d - (fp.size.y if fr.out.y != 0.0 else fp.size.x) - CityCatalog.BUILDING_KINDS[b.kind].setback[0]
	if back_depth < 9.0:
		return
	var a := _lat(fr, fp.get_center())
	var d0: float = fr.d - back_depth + 2.0
	var pool := _frame_rect(fr, a - 4.0, a + 4.0, d0, d0 + minf(5.0, back_depth - 3.0))
	b.style["pool"] = pool


func _make_street_parking() -> void:
	for e in m.edges:
		if e.park_w <= 0.0 or e.bridge:
			continue
		for side in [-1, 1]:
			var off: float = side * (e.width * 0.5 - e.park_w * 0.5)
			var t := m.edge_end_clearance(e, e.a) + 8.0
			var t_end: float = e.length - m.edge_end_clearance(e, e.b) - 8.0
			while t + 5.5 < t_end:
				# Parkbucht darf keine Zufahrt, Haltestelle oder Hydranten blockieren
				var ok := true
				var key := "%d:%d" % [e.id, side]
				for r in m.curb_reservations.get(key, []):
					if r[2] in ["driveway", "bus_stop", "hydrant", "end"] and t + 5.5 > r[0] and t < r[1]:
						ok = false
				if ok and rng.randf() < 0.85:
					var dir := m.edge_dir(e)
					m.street_parking.append({"edge": e.id, "side": side, "t": t + 2.75,
						"pos": m.edge_point(e, t + 2.75, off), "rot": atan2(dir.x, dir.y)})
				t += 6.0


func _make_spawn_points() -> void:
	for b in m.buildings:
		if b.kind in ["station", "shelter", "homeless_camp", "city_hall"]:
			m.spawn_points.append({"pos": b.sidewalk_point, "building": b.id, "label": b.kind})
	for s in m.bus_stops:
		m.spawn_points.append({"pos": s.pos, "building": -1, "label": "bus_stop"})
