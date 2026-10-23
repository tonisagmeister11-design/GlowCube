class_name CityValidator
extends RefCounted
## Plausibilitaetspruefung und Reparatur der generierten Stadt.
## Fehler (severity "error") werden repariert: ungueltige Gebaeude werden durch
## eine sichere Ersatzflaeche (Gruenflaeche) ersetzt, ungueltige Requisiten,
## Zufahrten, Parkplaetze und Haltestellen entfernt. Danach wird erneut
## geprueft; das Endergebnis steht in model.issues (sollte keine Fehler enthalten).

const EPS := 0.05
const CELL := 50.0

var m: CityModel
var issues: Array = []
var _bgrid: Dictionary = {}
var _egrid: Dictionary = {}
var _pgrid: Dictionary = {}
var _main_nodes: Dictionary = {}
var _main_ped: Dictionary = {}


static func validate_and_repair(model: CityModel, max_rounds := 3) -> Dictionary:
	var report := {"rounds": [], "final_errors": 0, "final_warnings": 0}
	for round_i in max_rounds:
		var v := CityValidator.new()
		v.m = model
		var found := v.run()
		var errs := 0
		var codes := {}
		for i in found:
			if i.severity == "error":
				errs += 1
			codes[i.code] = codes.get(i.code, 0) + 1
		report.rounds.append({"errors": errs, "total": found.size(), "codes": codes})
		model.issues = found
		if errs == 0:
			break
		v.repair(found)
		model.build_indices()
	var final := CityValidator.new()
	final.m = model
	model.issues = final.run()
	for i in model.issues:
		if i.severity == "error":
			report.final_errors += 1
		else:
			report.final_warnings += 1
	return report


static func validate(model: CityModel) -> Array:
	var v := CityValidator.new()
	v.m = model
	return v.run()


func _issue(code: String, severity: String, pos: Vector2, msg: String, ref := {}) -> void:
	issues.append({"code": code, "severity": severity, "pos": pos, "msg": msg, "ref": ref})


func run() -> Array:
	issues.clear()
	_build_grids()
	_check_graph()
	_check_blocks_lots()
	_check_buildings()
	_check_props()
	_check_parking()
	_check_bus()
	_check_spawns()
	_check_ped_reachability()
	return issues


# ------------------------------------------------------------ Raster

func _cells_for_rect(r: Rect2) -> Array:
	var out := []
	var x0 := floori(r.position.x / CELL)
	var x1 := floori(r.end.x / CELL)
	var z0 := floori(r.position.y / CELL)
	var z1 := floori(r.end.y / CELL)
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			out.append(Vector2i(x, z))
	return out


func _grid_add(g: Dictionary, r: Rect2, id: int) -> void:
	for c in _cells_for_rect(r):
		if not g.has(c):
			g[c] = []
		g[c].append(id)


func _grid_query(g: Dictionary, r: Rect2) -> Array:
	var out := {}
	for c in _cells_for_rect(r):
		for id in g.get(c, []):
			out[id] = true
	return out.keys()


func _build_grids() -> void:
	_bgrid.clear()
	_egrid.clear()
	_pgrid.clear()
	for b in m.buildings:
		if b.get("removed", false):
			continue
		_grid_add(_bgrid, b.footprint, b.id)
	for e in m.edges:
		_grid_add(_egrid, m.edge_road_rect(e), e.id)
	for p in m.props:
		if p.get("removed", false):
			continue
		_grid_add(_pgrid, _prop_rect(p), p.id)


func _prop_rect(p: Dictionary) -> Rect2:
	if p.has("rect"):
		return p.rect
	return Rect2(p.pos - Vector2(p.r, p.r), Vector2(p.r, p.r) * 2.0)


static func _rect_overlap(a: Rect2, b: Rect2, eps := EPS) -> bool:
	return a.position.x < b.end.x - eps and b.position.x < a.end.x - eps \
		and a.position.y < b.end.y - eps and b.position.y < a.end.y - eps


static func _rect_inside(inner: Rect2, outer: Rect2, eps := EPS) -> bool:
	return inner.position.x >= outer.position.x - eps and inner.position.y >= outer.position.y - eps \
		and inner.end.x <= outer.end.x + eps and inner.end.y <= outer.end.y + eps


static func _circle_rect(c: Vector2, r: float, rect: Rect2) -> bool:
	var q := Vector2(clampf(c.x, rect.position.x, rect.end.x), clampf(c.y, rect.position.y, rect.end.y))
	return q.distance_to(c) < r - 0.01


func _prop_hits_rect(p: Dictionary, rect: Rect2) -> bool:
	if p.has("rect"):
		return _rect_overlap(p.rect, rect)
	return _circle_rect(p.pos, p.r, rect)


# ------------------------------------------------------------ Graph

func _check_graph() -> void:
	_main_nodes.clear()
	if m.nodes.is_empty():
		_issue("graph_empty", "error", Vector2.ZERO, "Kein Strassennetz")
		return
	# Groesste Zusammenhangskomponente
	var comp_of := {}
	var best_comp := []
	for n in m.nodes:
		if comp_of.has(n.id) or n.edges.is_empty():
			continue
		var comp := []
		var stack := [n.id]
		comp_of[n.id] = true
		while not stack.is_empty():
			var cur: int = stack.pop_back()
			comp.append(cur)
			for eid in m.nodes[cur].edges:
				var o := m.edge_other(m.edges[eid], cur)
				if not comp_of.has(o):
					comp_of[o] = true
					stack.append(o)
		if comp.size() > best_comp.size():
			best_comp = comp
	for id in best_comp:
		_main_nodes[id] = true
	for n in m.nodes:
		if n.edges.is_empty():
			continue
		if not _main_nodes.has(n.id):
			_issue("node_unreachable", "error", n.pos, "Kreuzung nicht mit dem Strassennetz verbunden", {"node": n.id})
		if n.edges.size() < 2:
			_issue("dead_end", "warning", n.pos, "Sackgasse", {"node": n.id})
		if n.kind == "signal" and n.edges.size() < 3:
			_issue("signal_degree", "error", n.pos, "Ampel an Knoten mit weniger als drei Armen", {"node": n.id})
		if n.kind == "roundabout":
			if n.edges.size() != 4:
				_issue("roundabout_arms", "error", n.pos, "Kreisverkehr ohne vier Zufahrten", {"node": n.id})
			for sx in [-1, 1]:
				for sz in [-1, 1]:
					var bid := m.block_at_point(n.pos + Vector2(sx, sz) * 15.0, true)
					if bid < 0:
						continue
					var inner: Rect2 = m.blocks[bid].inner
					if _circle_rect(n.pos, n.roundabout.r_outer, inner):
						_issue("roundabout_clearance", "error", n.pos, "Kreisverkehr ragt in ein Grundstück", {"node": n.id})
	for e in m.edges:
		if e.length <= 1.0:
			_issue("edge_degenerate", "error", m.nodes[e.a].pos, "Strassenstück ohne Länge", {"edge": e.id})
		if e.bridge and (not _main_nodes.has(e.a) or not _main_nodes.has(e.b)):
			_issue("bridge_unreachable", "error", m.nodes[e.a].pos, "Brücke ohne erreichbare Anschlussstraße", {"edge": e.id})


# ------------------------------------------------------------ Bloecke/Grundstuecke

func _check_blocks_lots() -> void:
	for b in m.blocks:
		if not _rect_inside(b.inner, b.curb):
			_issue("block_inner", "error", b.curb.get_center(), "Grundstücksfläche größer als Block", {"block": b.id})
		for eid in _grid_query(_egrid, b.curb):
			if _rect_overlap(b.curb, m.edge_road_rect(m.edges[eid]), 0.2):
				_issue("block_on_road", "error", b.curb.get_center(), "Block überlappt Fahrbahn", {"block": b.id, "edge": eid})
	var lgrid := {}
	for l in m.lots:
		if not _rect_inside(l.rect, m.blocks[l.block].inner):
			_issue("lot_outside_block", "error", l.rect.get_center(), "Grundstück außerhalb des Blocks", {"lot": l.id})
		for other in _grid_query(lgrid, l.rect):
			if _rect_overlap(l.rect, m.lots[other].rect, 0.05):
				_issue("lot_overlap", "error", l.rect.get_center(), "Grundstücke überlappen", {"lot": l.id, "other": other})
		_grid_add(lgrid, l.rect, l.id)


# ------------------------------------------------------------ Gebaeude

func _check_buildings() -> void:
	for b in m.buildings:
		if b.get("removed", false):
			continue
		var fp: Rect2 = b.footprint
		var lot: Dictionary = m.lots[b.lot]
		var blk: Dictionary = m.blocks[b.block]
		if fp.size.x < 2.0 or fp.size.y < 2.0:
			_issue("building_tiny", "error", fp.get_center(), "Gebäude zu klein", {"building": b.id})
		if not _rect_inside(fp, lot.rect):
			_issue("building_outside_lot", "error", fp.get_center(), "Gebäude ragt aus dem Grundstück", {"building": b.id})
		if not _rect_inside(fp, blk.inner):
			_issue("building_on_sidewalk", "error", fp.get_center(), "Gebäude steht auf Gehweg oder Straße", {"building": b.id})
		for oid in _grid_query(_bgrid, fp):
			if oid <= b.id:
				continue
			var o: Dictionary = m.buildings[oid]
			if o.get("removed", false):
				continue
			if _rect_overlap(fp, o.footprint):
				_issue("building_overlap", "error", fp.get_center(), "Gebäude durchdringen sich", {"building": oid, "other": b.id})
		for eid in _grid_query(_egrid, fp):
			if _rect_overlap(fp, m.edge_road_rect(m.edges[eid]), 0.0):
				_issue("building_on_road", "error", fp.get_center(), "Straße verläuft durch Gebäude", {"building": b.id, "edge": eid})
		# Eingang
		var door: Vector2 = b.door
		var on_facade := _point_on_rect_border(door, fp, 0.15)
		if b.open_air:
			on_facade = fp.grow(0.2).has_point(door)
		if not on_facade:
			_issue("door_not_on_facade", "error", door, "Eingang liegt nicht an der Fassade", {"building": b.id})
		if b.edge < 0 or b.edge >= m.edges.size():
			_issue("entrance_no_street", "error", door, "Eingang ohne Straßenanbindung", {"building": b.id})
		elif not _main_nodes.has(m.edges[b.edge].a):
			_issue("entrance_unreachable", "error", door, "Eingang an nicht erreichbarer Straße", {"building": b.id})
		var walk: Rect2 = b.walkway
		var sp: Vector2 = b.sidewalk_point
		var probe := Rect2(sp - Vector2(0.3, 0.3), Vector2(0.6, 0.6))
		for oid in _grid_query(_bgrid, walk.merge(probe) if walk.has_area() else probe):
			var o2: Dictionary = m.buildings[oid]
			if o2.get("removed", false):
				continue
			if oid != b.id and walk.has_area() and _rect_overlap(walk, o2.footprint):
				_issue("entrance_blocked", "error", door, "Zugang zum Eingang durch Gebäude blockiert", {"building": b.id})
			if o2.footprint.has_point(sp) and oid != b.id:
				_issue("entrance_blocked", "error", sp, "Gehwegpunkt liegt in einem Gebäude", {"building": b.id})
		if blk.inner.grow(-0.05).has_point(sp):
			_issue("sidewalk_point_in_lot", "error", sp, "Gehwegpunkt liegt nicht auf dem Gehweg", {"building": b.id})
		if not blk.curb.grow(0.1).has_point(sp):
			_issue("sidewalk_point_on_road", "error", sp, "Gehwegpunkt liegt auf der Fahrbahn", {"building": b.id})
		if b.driveway.has_area():
			if not _rect_inside(b.driveway, lot.rect, 0.1):
				_issue("driveway_outside", "error", b.driveway.get_center(), "Zufahrt außerhalb des Grundstücks", {"building": b.id, "fix": "driveway"})
			if _rect_overlap(b.driveway, fp, 0.05):
				_issue("driveway_blocked", "error", b.driveway.get_center(), "Zufahrt endet im Gebäude", {"building": b.id, "fix": "driveway"})


func _point_on_rect_border(p: Vector2, r: Rect2, tol: float) -> bool:
	if not r.grow(tol).has_point(p):
		return false
	return not r.grow(-tol).has_point(p)


# ------------------------------------------------------------ Requisiten

func _check_props() -> void:
	for p in m.props:
		if p.get("removed", false):
			continue
		var pr := _prop_rect(p)
		for bid in _grid_query(_bgrid, pr):
			var b: Dictionary = m.buildings[bid]
			if b.get("removed", false):
				continue
			if _prop_hits_rect(p, b.footprint):
				_issue("prop_in_building", "error", p.pos, "Objekt steckt in einem Gebäude (%s)" % p.kind, {"prop": p.id})
				break
			if b.walkway.has_area() and _prop_hits_rect(p, b.walkway):
				_issue("prop_blocks_entrance", "error", p.pos, "Objekt blockiert Eingangsweg (%s)" % p.kind, {"prop": p.id})
				break
			if b.driveway.has_area() and _prop_hits_rect(p, b.driveway):
				_issue("prop_blocks_driveway", "error", p.pos, "Objekt blockiert Zufahrt (%s)" % p.kind, {"prop": p.id})
				break
			if b.parking.has_area() and _prop_hits_rect(p, b.parking):
				_issue("prop_on_parking", "error", p.pos, "Objekt auf Parkplatz (%s)" % p.kind, {"prop": p.id})
				break
		if p.layer == "street" or p.layer == "lot" or p.layer == "park":
			for eid in _grid_query(_egrid, pr):
				var e: Dictionary = m.edges[eid]
				if _prop_hits_rect(p, m.edge_road_rect(e)):
					_issue("prop_on_road", "error", p.pos, "Objekt steht auf der Fahrbahn (%s)" % p.kind, {"prop": p.id})
					break
		if p.layer == "lot" and p.ref >= 0 and p.ref < m.buildings.size():
			var lot: Dictionary = m.lots[m.buildings[p.ref].lot]
			if not _rect_inside(pr, lot.rect, 0.1):
				_issue("prop_outside_lot", "error", p.pos, "Objekt außerhalb des Grundstücks (%s)" % p.kind, {"prop": p.id})
		# Ueberlappung mit anderen Requisiten (nur feste Objekte)
		if p.kind in ["tree", "street_tree", "lamp", "container", "pallets", "bench", "bus_shelter", "fountain", "playground"]:
			for oid in _grid_query(_pgrid, pr):
				if oid <= p.id:
					continue
				var o: Dictionary = m.props[oid]
				if o.get("removed", false) or o.has("rect") or p.has("rect"):
					continue
				if not o.kind in ["tree", "street_tree", "lamp", "container", "pallets", "bench", "bus_shelter", "fountain", "playground", "traffic_light", "hydrant", "bin"]:
					continue
				if o.pos.distance_to(p.pos) < (o.r + p.r) * 0.9:
					_issue("prop_overlap", "error", o.pos, "Objekte überlappen (%s/%s)" % [p.kind, o.kind], {"prop": oid})


# ------------------------------------------------------------ Parkplaetze

func _check_parking() -> void:
	for pa in m.parking_areas:
		if pa.get("removed", false):
			continue
		var b: Dictionary = m.buildings[pa.building]
		if b.get("removed", false):
			_issue("parking_orphan", "error", pa.rect.get_center(), "Parkplatz ohne Gebäude", {"parking": pa.id})
			continue
		if _rect_overlap(pa.rect, b.footprint):
			_issue("parking_in_building", "error", pa.rect.get_center(), "Parkplatz überlappt Gebäude", {"parking": pa.id})
		if not _rect_inside(pa.rect, m.lots[b.lot].rect, 0.1):
			_issue("parking_outside_lot", "error", pa.rect.get_center(), "Parkplatz außerhalb des Grundstücks", {"parking": pa.id})
	for i in m.street_parking.size():
		var sp: Dictionary = m.street_parking[i]
		var probe := Rect2(sp.pos - Vector2(2.0, 2.0), Vector2(4.0, 4.0))
		for bid in _grid_query(_bgrid, probe):
			var b2: Dictionary = m.buildings[bid]
			if not b2.get("removed", false) and b2.driveway.has_area() and _circle_rect(sp.pos, 1.5, b2.driveway.grow(0.3)):
				_issue("street_parking_driveway", "error", sp.pos, "Parkbucht blockiert Zufahrt", {"street_parking": i})


# ------------------------------------------------------------ Bus

func _check_bus() -> void:
	for s in m.bus_stops:
		if s.get("removed", false):
			continue
		var probe := Rect2(s.pos - Vector2(1, 1), Vector2(2, 2))
		for bid in _grid_query(_bgrid, probe):
			var b: Dictionary = m.buildings[bid]
			if not b.get("removed", false) and b.footprint.has_point(s.pos):
				_issue("bus_stop_in_building", "error", s.pos, "Haltestelle in Gebäude", {"stop": s.id})
		if not _main_nodes.has(m.edges[s.edge].a):
			_issue("bus_stop_unreachable", "error", s.pos, "Haltestelle an unerreichbarer Straße", {"stop": s.id})
	for l in m.bus_lines:
		for i in range(l.nodes.size() - 1):
			if m.edge_between(l.nodes[i], l.nodes[i + 1]) < 0:
				_issue("bus_line_broken", "error", m.nodes[l.nodes[i]].pos, "Buslinie %s unterbrochen" % l.name, {"line": l.id})
				break
		if l.stops.size() < 2:
			_issue("bus_line_few_stops", "warning", m.nodes[l.nodes[0]].pos, "Buslinie %s hat wenige Haltestellen" % l.name, {"line": l.id})


# ------------------------------------------------------------ Startpunkte

func _check_spawns() -> void:
	for i in m.spawn_points.size():
		var sp: Dictionary = m.spawn_points[i]
		var probe := Rect2(sp.pos - Vector2(1, 1), Vector2(2, 2))
		for bid in _grid_query(_bgrid, probe):
			var b: Dictionary = m.buildings[bid]
			if not b.get("removed", false) and b.footprint.grow(0.3).has_point(sp.pos):
				_issue("spawn_in_building", "error", sp.pos, "Startpunkt in Gebäude", {"spawn": i})
		for eid in _grid_query(_egrid, probe):
			if m.edge_road_rect(m.edges[eid]).has_point(sp.pos):
				_issue("spawn_on_road", "error", sp.pos, "Startpunkt auf der Fahrbahn", {"spawn": i})


# ------------------------------------------------------------ Fusswege

func _check_ped_reachability() -> void:
	if m.ped_points.is_empty():
		return
	var adj := {}
	for l in m.ped_links:
		if not adj.has(l[0]):
			adj[l[0]] = []
		if not adj.has(l[1]):
			adj[l[1]] = []
		adj[l[0]].append(l[1])
		adj[l[1]].append(l[0])
	var seen := {}
	var best := []
	for start in range(m.ped_points.size()):
		if seen.has(start):
			continue
		var comp := []
		var stack := [start]
		seen[start] = true
		while not stack.is_empty():
			var c: int = stack.pop_back()
			comp.append(c)
			for o in adj.get(c, []):
				if not seen.has(o):
					seen[o] = true
					stack.append(o)
		if comp.size() > best.size():
			best = comp
	for id in best:
		_main_ped[id] = true
	var lost := m.ped_points.size() - best.size()
	if lost > 0:
		_issue("ped_isolated", "warning", m.ped_points[0], "%d Gehwegknoten nicht zu Fuß erreichbar" % lost)
	# Geschaefte und Einrichtungen muessen zu Fuss erreichbar sein
	for b in m.buildings:
		if b.get("removed", false):
			continue
		if b.business_type == "" and not b.kind in CityCatalog.CIVIC_KINDS:
			continue
		var ring: Array = m._block_ring_ids.get(b.block, [])
		var ok := false
		for pid in ring:
			if _main_ped.has(pid):
				ok = true
		if not ok:
			_issue("business_unreachable", "error", b.door, "%s ist zu Fuß nicht erreichbar" % b.name, {"building": b.id})


# ============================================================ Reparatur

func repair(found: Array) -> void:
	var remove_buildings := {}
	var remove_props := {}
	var remove_parking := {}
	var remove_street_parking := {}
	var remove_spawns := {}
	var remove_stops := {}
	for i in found:
		if i.severity != "error":
			continue
		var ref: Dictionary = i.ref
		match i.code:
			"driveway_outside", "driveway_blocked":
				m.buildings[ref.building].driveway = Rect2()
			"signal_degree":
				m.nodes[ref.node].kind = "yield"
			"roundabout_arms", "roundabout_clearance":
				m.nodes[ref.node].kind = "stop"
				m.nodes[ref.node].roundabout = {}
			_:
				if ref.has("prop"):
					remove_props[ref.prop] = true
				elif ref.has("parking"):
					remove_parking[ref.parking] = true
				elif ref.has("street_parking"):
					remove_street_parking[ref.street_parking] = true
				elif ref.has("spawn"):
					remove_spawns[ref.spawn] = true
				elif ref.has("stop"):
					remove_stops[ref.stop] = true
				elif ref.has("building"):
					remove_buildings[ref.building] = true
	for bid in remove_buildings:
		var b: Dictionary = m.buildings[bid]
		b["removed"] = true
		var lot: Dictionary = m.lots[b.lot]
		lot.usage = "green"
		lot.building = -1
		for p in m.props:
			if p.layer == "lot" and p.ref == bid:
				remove_props[p.id] = true
		for pa in m.parking_areas:
			if pa.building == bid:
				remove_parking[pa.id] = true
	for pid in remove_props:
		m.props[pid]["removed"] = true
	for paid in remove_parking:
		m.parking_areas[paid]["removed"] = true
		m.buildings[m.parking_areas[paid].building].parking = Rect2()
	for sid in remove_stops:
		m.bus_stops[sid]["removed"] = true
		for l in m.bus_lines:
			l.stops.erase(sid)
			l.stops_back.erase(sid)
	if not remove_street_parking.is_empty():
		var kept := []
		for i in m.street_parking.size():
			if not remove_street_parking.has(i):
				kept.append(m.street_parking[i])
		m.street_parking = kept
	if not remove_spawns.is_empty():
		var kept2 := []
		for i in m.spawn_points.size():
			if not remove_spawns.has(i):
				kept2.append(m.spawn_points[i])
		m.spawn_points = kept2
	_compact()


## Entfernte Elemente physisch loeschen und IDs neu vergeben, damit alle
## anderen Systeme mit dichten, gueltigen Arrays arbeiten.
func _compact() -> void:
	# Gebaeude
	var bmap := {}
	var nb := []
	for b in m.buildings:
		if b.get("removed", false):
			continue
		bmap[b.id] = nb.size()
		b.id = nb.size()
		nb.append(b)
	m.buildings = nb
	for l in m.lots:
		l.building = bmap.get(l.building, -1)
	# Requisiten
	var np := []
	for p in m.props:
		if p.get("removed", false):
			continue
		if p.layer == "lot" and p.ref >= 0:
			if not bmap.has(p.ref):
				continue
			p.ref = bmap[p.ref]
		p.id = np.size()
		np.append(p)
	m.props = np
	# Parkplaetze
	var npa := []
	for pa in m.parking_areas:
		if pa.get("removed", false) or not bmap.has(pa.building):
			continue
		pa.building = bmap[pa.building]
		pa.id = npa.size()
		npa.append(pa)
	m.parking_areas = npa
	# Haltestellen
	var smap := {}
	var ns := []
	for s in m.bus_stops:
		if s.get("removed", false):
			continue
		smap[s.id] = ns.size()
		s.id = ns.size()
		ns.append(s)
	m.bus_stops = ns
	for l in m.bus_lines:
		var a := []
		for sid in l.stops:
			if smap.has(sid):
				a.append(smap[sid])
		l.stops = a
		var bb := []
		for sid in l.stops_back:
			if smap.has(sid):
				bb.append(smap[sid])
		l.stops_back = bb
	for p in m.props:
		if p.kind == "bus_shelter":
			p.ref = smap.get(p.get("stop", -1), -1)
			p["stop"] = p.ref
	var np2 := []
	for p in m.props:
		if p.kind == "bus_shelter" and p.ref < 0:
			continue
		p.id = np2.size()
		np2.append(p)
	m.props = np2
	for sp in m.spawn_points:
		if sp.get("building", -1) >= 0:
			sp.building = bmap.get(sp.building, -1)
