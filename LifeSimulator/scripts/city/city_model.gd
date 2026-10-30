class_name CityModel
extends RefCounted
## Reines Datenmodell der Stadt. Wird von CityGenerator deterministisch aus
## einem Seed erzeugt und von CityValidator geprueft. Alle Darstellungs-,
## Verkehrs-, NPC- und Wirtschaftssysteme lesen ausschliesslich diese Daten.
##
## Koordinaten: Vector2(x, z) in Metern, Boden bei y = 0, Stadtmitte (0, 0).
## "Norden" = -z. Rechtsverkehr. Siehe docs/ARCHITECTURE.md fuer alle Felder.

const CHUNK_SIZE := 200.0
const RIVER_DEPTH := 4.0           # Wasserspiegel liegt bei y = -RIVER_DEPTH + 0.8
const SIGNAL_CYCLE := 34.0         # Sekunden Echtzeit fuer einen Ampelumlauf
const SIGNAL_YELLOW := 3.0
const SIGNAL_ALL_RED := 1.0

## Strassenprofile je Linienklasse. lane_w = Fahrstreifenbreite,
## lanes = Fahrstreifen pro Richtung, bike_w / park_w = Radweg / Parkstreifen
## je Seite, sidewalk = Gehwegbreite je Seite, speed in m/s.
const PROFILES := {
	"ring":       {"lanes": 2, "lane_w": 3.5, "bike_w": 0.0, "park_w": 0.0, "sidewalk": 3.6, "speed": 16.0, "rank": 3},
	"arterial":   {"lanes": 2, "lane_w": 3.4, "bike_w": 1.6, "park_w": 0.0, "sidewalk": 4.6, "speed": 14.0, "rank": 3},
	"embankment": {"lanes": 1, "lane_w": 3.3, "bike_w": 1.5, "park_w": 0.0, "sidewalk": 3.4, "speed": 11.0, "rank": 2},
	"street":     {"lanes": 1, "lane_w": 3.2, "bike_w": 0.0, "park_w": 2.2, "sidewalk": 3.2, "speed": 9.0,  "rank": 1},
}

var seed: int = 0
var size: float = 1800.0
var params: Dictionary = {}

## Strassenlinien. x_lines verlaufen in z-Richtung (x konstant),
## z_lines in x-Richtung (z konstant). {pos, cls, name}
var x_lines: Array = []
var z_lines: Array = []
## Fluss als Band in x-Richtung: {z, width, z_north, z_south, x0, x1}
var river: Dictionary = {}

## Knoten {id, pos, gx, gz, edges:[ids], kind:"signal|stop|yield|roundabout|corner|pass",
##         signal:{offset, x_first}, roundabout:{r_outer, r_inner}, priority_axis:"x|z|"}
var nodes: Array = []
## Kanten {id, a, b, axis:"x|z", line, cls, lanes, lane_w, bike_w, park_w, width,
##         sidewalk:[links, rechts], bridge, name, speed, length}
## axis "x": Kante verlaeuft entlang x (liegt auf einer z_line).
var edges: Array = []
## Bloecke {id, gx0, gz0, gx1, gz1, cell:Rect2, curb:Rect2, inner:Rect2, zone, district,
##          kind:"normal|promenade|park|facility", special}
var blocks: Array = []
## Grundstuecke {id, block, rect, front:"n|s|e|w", edge, side, zone, usage}
var lots: Array = []
## Gebaeude, siehe CityGenerator._make_building()
var buildings: Array = []
## Requisiten {id, kind, pos, rot, r, layer:"street|lot|park", ref}
var props: Array = []
## Parks {id, rect, name, block, paths:[Rect2], features:[{kind,pos,r}]}
var parks: Array = []
## Parkplaetze {id, rect, building, spots:[{pos, rot}], access:Rect2}
var parking_areas: Array = []
## Strassenparkplaetze {edge, side, pos, rot}
var street_parking: Array = []
## Zebrastreifen {node, edge, center, axis, length, width}
var crosswalks: Array = []
## Bushaltestellen {id, edge, side, t, pos, dir, name, lines:[]}
var bus_stops: Array = []
## Buslinien {id, name, color, loop, nodes:[node ids], stops:[stop ids], length}
var bus_lines: Array = []
## Stadtteile {id, name, zone, blocks:[], center, bounds:Rect2, wealth, crime}
var districts: Array = []
## Sichere Startpunkte auf Gehwegen {pos, district, label}
var spawn_points: Array = []
## Bordstein-Reservierungen je "edge:side" -> [[t0, t1, kind], ...]
var curb_reservations: Dictionary = {}
## Ergebnis der Validierung {code, severity, pos, msg, ref}
var issues: Array = []
var stats: Dictionary = {}

var _astar_road := AStar2D.new()
var _astar_ped := AStar2D.new()
var ped_points: Array = []          # Vector2 je Fussgaengerknoten
var ped_links: Array = []           # [a, b, kind] kind: "side" | "crosswalk"
var _block_ring_ids: Dictionary = {} # block id -> [4 ped ids]  (NW, NE, SE, SW)
var _cell_block: Dictionary = {}     # Vector2i(gx, gz) -> block id
var _node_grid: Dictionary = {}      # Vector2i(gx, gz) -> node id
var chunks: Dictionary = {}          # Vector2i -> {buildings:[], props:[], blocks:[], edges:[], parks:[]}
var _tmp_ped_id := 1_000_000


# ============================================================ Grundgeometrie

static func v3(p: Vector2, y := 0.0) -> Vector3:
	return Vector3(p.x, y, p.y)


static func v2(p: Vector3) -> Vector2:
	return Vector2(p.x, p.z)


static func right_of(dir: Vector2) -> Vector2:
	return Vector2(-dir.y, dir.x)


func node_pos(id: int) -> Vector2:
	return nodes[id].pos


func edge_dir(e: Dictionary) -> Vector2:
	return (nodes[e.b].pos - nodes[e.a].pos).normalized()


func edge_right(e: Dictionary) -> Vector2:
	return right_of(edge_dir(e))


func edge_other(e: Dictionary, node_id: int) -> int:
	return e.b if e.a == node_id else e.a


func edge_between(a: int, b: int) -> int:
	for eid in nodes[a].edges:
		if edge_other(edges[eid], a) == b:
			return eid
	return -1


## Halbe Breite der Fahrbahn (Bordstein zu Bordstein).
func half_width(e: Dictionary) -> float:
	return e.width * 0.5


## Halbe Ausdehnung der Kreuzungsflaeche eines Knotens: x = halbe Breite der
## Strasse in z-Richtung, y = halbe Breite der Strasse in x-Richtung.
func node_box(id: int) -> Vector2:
	var n: Dictionary = nodes[id]
	var hx := 0.0
	var hz := 0.0
	for eid in n.edges:
		var e: Dictionary = edges[eid]
		if e.axis == "z":
			hx = maxf(hx, e.width * 0.5)
		else:
			hz = maxf(hz, e.width * 0.5)
	return Vector2(hx, hz)


## Abstand vom Knotenmittelpunkt, ab dem die Kante "frei" ist (hinter der
## Kreuzung inkl. Gehweg der Querstrasse bzw. Kreisverkehr).
func edge_end_clearance(e: Dictionary, node_id: int) -> float:
	var n: Dictionary = nodes[node_id]
	var box := node_box(node_id)
	var cross_half: float = box.x if e.axis == "x" else box.y
	var cross_sw := 0.0
	for eid in n.edges:
		var o: Dictionary = edges[eid]
		if o.axis != e.axis:
			cross_sw = maxf(cross_sw, o.sidewalk[0])
	var d := cross_half + cross_sw
	if n.kind == "roundabout":
		d = maxf(d, n.roundabout.r_outer + 1.0)
	return d


## Punkt auf der Kante: t = Meter ab Knoten a, lateral = Meter nach rechts
## (bezogen auf Richtung a -> b).
func edge_point(e: Dictionary, t: float, lateral := 0.0) -> Vector2:
	var pa: Vector2 = nodes[e.a].pos
	var d := edge_dir(e)
	return pa + d * t + right_of(d) * lateral


## Seitlicher Versatz der Fahrstreifenmitte (positiv = rechts von a->b).
## forward = true: Fahrtrichtung a -> b. lane 0 = innerer Fahrstreifen.
func lane_offset(e: Dictionary, forward: bool, lane: int) -> float:
	var off: float = (lane + 0.5) * e.lane_w
	return off if forward else -off


## Versatz der Gehwegmitte. side +1 = rechts von a->b, -1 = links.
func sidewalk_offset(e: Dictionary, side: int) -> float:
	var sw: float = e.sidewalk[1] if side > 0 else e.sidewalk[0]
	return side * (e.width * 0.5 + sw * 0.5)


## Versatz der Bordsteinkante (Fahrbahnrand).
func curb_offset(e: Dictionary, side: int) -> float:
	return side * e.width * 0.5


## Rechteck der Fahrbahn einer Kante zwischen den Knotenmittelpunkten.
func edge_road_rect(e: Dictionary) -> Rect2:
	var pa: Vector2 = nodes[e.a].pos
	var pb: Vector2 = nodes[e.b].pos
	var hw: float = e.width * 0.5
	if e.axis == "x":
		return Rect2(Vector2(minf(pa.x, pb.x), pa.y - hw), Vector2(absf(pb.x - pa.x), e.width))
	return Rect2(Vector2(pa.x - hw, minf(pa.y, pb.y)), Vector2(e.width, absf(pb.y - pa.y)))


## Ampelzustand fuer Verkehr entlang einer Achse ("x" oder "z") zur Echtzeit
## t_seconds. Rueckgabe: "green" | "yellow" | "red". Fuer Knoten ohne Ampel
## immer "green".
func signal_state(node_id: int, axis: String, t_seconds: float) -> String:
	var n: Dictionary = nodes[node_id]
	if n.kind != "signal":
		return "green"
	var half := SIGNAL_CYCLE * 0.5
	var ph := fposmod(t_seconds + n.signal.offset, SIGNAL_CYCLE)
	var first: String = "x" if n.signal.x_first else "z"
	var local := ph if axis == first else fposmod(ph - half, SIGNAL_CYCLE)
	if local < half - SIGNAL_YELLOW - SIGNAL_ALL_RED:
		return "green"
	if local < half - SIGNAL_ALL_RED:
		return "yellow"
	return "red"


## Fussgaengerampel: gruen, wenn der parallel laufende Verkehr gruen hat.
func ped_signal_green(node_id: int, crossing_edge_axis: String, t_seconds: float) -> bool:
	# Wer eine Strasse entlang Achse A ueberquert, laeuft parallel zur anderen Achse.
	var parallel := "z" if crossing_edge_axis == "x" else "x"
	return signal_state(node_id, parallel, t_seconds) == "green"


# ============================================================ Indizes

## Nach Generierung/Reparatur aufrufen: Wegfindung und Chunks aufbauen.
func build_indices() -> void:
	_node_grid.clear()
	for n in nodes:
		_node_grid[Vector2i(n.gx, n.gz)] = n.id
	_build_road_astar()
	_build_ped_graph()
	_build_chunks()


func _build_road_astar() -> void:
	_astar_road.clear()
	for n in nodes:
		_astar_road.add_point(n.id, n.pos)
	for e in edges:
		_astar_road.connect_points(e.a, e.b, true)


func node_at_grid(gx: int, gz: int) -> int:
	return _node_grid.get(Vector2i(gx, gz), -1)


func block_at_cell(gx: int, gz: int) -> int:
	return _cell_block.get(Vector2i(gx, gz), -1)


func register_block_cells(b: Dictionary) -> void:
	for gx in range(b.gx0, b.gx1):
		for gz in range(b.gz0, b.gz1):
			_cell_block[Vector2i(gx, gz)] = b.id


## Rechteck, auf dem die Gehwegmitte des Blocks verlaeuft.
func block_ring_rect(b: Dictionary) -> Rect2:
	if b.kind == "promenade":
		return b.curb.grow(-1.6)
	var c: Rect2 = b.curb
	var i: Rect2 = b.inner
	var l := (i.position.x - c.position.x) * 0.5
	var t := (i.position.y - c.position.y) * 0.5
	var r := (c.end.x - i.end.x) * 0.5
	var bt := (c.end.y - i.end.y) * 0.5
	return Rect2(Vector2(c.position.x + l, c.position.y + t),
		Vector2(c.size.x - l - r, c.size.y - t - bt))


func _build_ped_graph() -> void:
	_astar_ped.clear()
	ped_points.clear()
	ped_links.clear()
	_block_ring_ids.clear()
	for b in blocks:
		var r := block_ring_rect(b)
		var corners := [r.position, Vector2(r.end.x, r.position.y), r.end, Vector2(r.position.x, r.end.y)]
		var ids := []
		for c in corners:
			var pid := ped_points.size()
			ped_points.append(c)
			_astar_ped.add_point(pid, c)
			ids.append(pid)
		_block_ring_ids[b.id] = ids
		for k in 4:
			_astar_ped.connect_points(ids[k], ids[(k + 1) % 4])
			ped_links.append([ids[k], ids[(k + 1) % 4], "side"])
	# Zebrastreifen: um jeden Knoten die vier Quadrantenbloecke verbinden,
	# sofern der dazwischenliegende Strassenarm existiert.
	for n in nodes:
		var q := {
			"nw": quadrant_block(n.id, -1, -1), "ne": quadrant_block(n.id, 1, -1),
			"se": quadrant_block(n.id, 1, 1), "sw": quadrant_block(n.id, -1, 1),
		}
		var arms := _node_arms(n)
		_link_blocks(q.nw, q.ne, arms.get("n", -1), n.pos, "crosswalk")
		_link_blocks(q.ne, q.se, arms.get("e", -1), n.pos, "crosswalk")
		_link_blocks(q.se, q.sw, arms.get("s", -1), n.pos, "crosswalk")
		_link_blocks(q.sw, q.nw, arms.get("w", -1), n.pos, "crosswalk")
	# Entlang jeder Kante: unterschiedliche Bloecke an beiden Enden derselben
	# Seite (Bruecken) ueber den Bruecken-Gehweg verbinden.
	for e in edges:
		var na: Dictionary = nodes[e.a]
		var nb: Dictionary = nodes[e.b]
		var d := edge_dir(e)
		var r := right_of(d)
		for side in [-1, 1]:
			if (e.sidewalk[1] if side > 0 else e.sidewalk[0]) <= 0.0:
				continue
			var qa := quadrant_block(e.a, signi(roundi(d.x + r.x * side)), signi(roundi(d.y + r.y * side)))
			var qb := quadrant_block(e.b, signi(roundi(-d.x + r.x * side)), signi(roundi(-d.y + r.y * side)))
			_link_blocks(qa, qb, e.id, (na.pos + nb.pos) * 0.5, "bridge" if e.bridge else "side", true)


## Verbindet die naechstgelegenen Ringecken zweier Bloecke. closest_pair:
## das Eckenpaar mit minimalem Abstand statt der Ecken naechst zu p.
func _link_blocks(ba: int, bb: int, arm_edge: int, p: Vector2, kind: String, closest_pair := false) -> void:
	if ba < 0 or bb < 0 or ba == bb or arm_edge < 0:
		return
	var ia := -1
	var ib := -1
	if closest_pair:
		var bd := INF
		for x in _block_ring_ids.get(ba, []):
			for y in _block_ring_ids.get(bb, []):
				var dd: float = ped_points[x].distance_squared_to(ped_points[y])
				if dd < bd:
					bd = dd
					ia = x
					ib = y
	else:
		ia = _nearest_ring_corner(ba, p)
		ib = _nearest_ring_corner(bb, p)
	if ia < 0 or ib < 0 or _astar_ped.are_points_connected(ia, ib):
		return
	_astar_ped.connect_points(ia, ib)
	ped_links.append([ia, ib, kind])


func _nearest_ring_corner(block_id: int, p: Vector2) -> int:
	var ids: Array = _block_ring_ids.get(block_id, [])
	var best := -1
	var bd := INF
	for pid in ids:
		var d: float = ped_points[pid].distance_squared_to(p)
		if d < bd:
			bd = d
			best = pid
	return best


## Block im Quadranten (sx, sz) eines Knotens; Sonde knapp hinter der
## Kreuzungsflaeche, damit auch schmale Promenaden getroffen werden.
func quadrant_block(node_id: int, sx: int, sz: int) -> int:
	if sx == 0 or sz == 0:
		return -1
	var n: Dictionary = nodes[node_id]
	var box := node_box(node_id)
	return block_at_point(n.pos + Vector2(sx * (box.x + 1.5), sz * (box.y + 1.5)))


## Arme eines Knotens: Himmelsrichtung -> Kanten-ID.
func _node_arms(n: Dictionary) -> Dictionary:
	var arms := {}
	for eid in n.edges:
		var o: Vector2 = nodes[edge_other(edges[eid], n.id)].pos - n.pos
		if absf(o.x) > absf(o.y):
			arms["e" if o.x > 0 else "w"] = eid
		else:
			arms["s" if o.y > 0 else "n"] = eid
	return arms


func node_arms(node_id: int) -> Dictionary:
	return _node_arms(nodes[node_id])


func _build_chunks() -> void:
	chunks.clear()
	for b in buildings:
		_chunk_add(b.footprint.get_center(), "buildings", b.id)
	for p in props:
		_chunk_add(p.pos, "props", p.id)
	for bl in blocks:
		_chunk_add(bl.curb.get_center(), "blocks", bl.id)
	for e in edges:
		_chunk_add((nodes[e.a].pos + nodes[e.b].pos) * 0.5, "edges", e.id)
	for pk in parks:
		_chunk_add(pk.rect.get_center(), "parks", pk.id)


func chunk_key(p: Vector2) -> Vector2i:
	return Vector2i(floori(p.x / CHUNK_SIZE), floori(p.y / CHUNK_SIZE))


func _chunk_add(p: Vector2, list: String, id: int) -> void:
	var k := chunk_key(p)
	if not chunks.has(k):
		chunks[k] = {"buildings": [], "props": [], "blocks": [], "edges": [], "parks": []}
	chunks[k][list].append(id)


func chunk_rect(k: Vector2i) -> Rect2:
	return Rect2(Vector2(k) * CHUNK_SIZE, Vector2(CHUNK_SIZE, CHUNK_SIZE))


# ============================================================ Abfragen

func block_at_point(p: Vector2, use_cell := false) -> int:
	var gx := _line_index(x_lines, p.x)
	var gz := _line_index(z_lines, p.y)
	if gx >= 0 and gz >= 0:
		var bid := block_at_cell(gx, gz)
		if bid >= 0:
			var b: Dictionary = blocks[bid]
			if use_cell or b.curb.has_point(p):
				return bid
	# Promenaden liegen ausserhalb des Zellrasters
	for b in blocks:
		if b.kind == "promenade" and (b.curb.has_point(p) or (use_cell and b.cell.has_point(p))):
			return b.id
	return -1


func _line_index(lines: Array, v: float) -> int:
	for i in range(lines.size() - 1):
		if v >= lines[i].pos and v < lines[i + 1].pos:
			return i
	return -1


func district_at(p: Vector2) -> Dictionary:
	var bid := block_at_point(p, true)
	if bid >= 0 and blocks[bid].district >= 0:
		return districts[blocks[bid].district]
	var best := {}
	var bd := INF
	for d in districts:
		var dd: float = d.center.distance_squared_to(p)
		if dd < bd:
			bd = dd
			best = d
	return best


func building_at(p: Vector2) -> int:
	var k := chunk_key(p)
	for dx in [-1, 0, 1]:
		for dz in [-1, 0, 1]:
			var c = chunks.get(k + Vector2i(dx, dz))
			if c == null:
				continue
			for bid in c.buildings:
				if buildings[bid].footprint.has_point(p):
					return bid
	return -1


func buildings_of_kind(kind: String) -> Array:
	var out := []
	for b in buildings:
		if b.kind == kind:
			out.append(b)
	return out


func buildings_with_business(btype := "") -> Array:
	var out := []
	for b in buildings:
		if b.business_type != "" and (btype == "" or b.business_type == btype):
			out.append(b)
	return out


## Naechste Kante zu einem Punkt: {edge, t, dist, side}
func nearest_edge(p: Vector2) -> Dictionary:
	var best := {"edge": -1, "t": 0.0, "dist": INF, "side": 1}
	for e in edges:
		var pa: Vector2 = nodes[e.a].pos
		var d := edge_dir(e)
		var t := clampf((p - pa).dot(d), 0.0, e.length)
		var q := pa + d * t
		var dist := q.distance_to(p)
		if dist < best.dist:
			var side := 1 if (p - q).dot(right_of(d)) >= 0.0 else -1
			best = {"edge": e.id, "t": t, "dist": dist, "side": side}
	return best


func nearest_node(p: Vector2) -> int:
	return _astar_road.get_closest_point(p)


## Strassenroute (Knotenfolge) zwischen zwei Weltpunkten fuer Fahrzeuge.
func road_route_nodes(from: Vector2, to: Vector2) -> PackedInt64Array:
	var a := nearest_node(from)
	var b := nearest_node(to)
	return _astar_road.get_id_path(a, b)


func road_route_between_nodes(a: int, b: int) -> PackedInt64Array:
	return _astar_road.get_id_path(a, b)


## Fussweg zwischen zwei Punkten, die auf oder nahe einem Gehweg liegen
## (z.B. building.sidewalk_point). Liefert Punkte auf Gehwegmitten und
## Zebrastreifen; Start und Ziel sind enthalten.
func ped_route(from: Vector2, to: Vector2) -> PackedVector2Array:
	var out := PackedVector2Array()
	var sa := _attach_ped(from)
	var sb := _attach_ped(to)
	if sa.is_empty() or sb.is_empty():
		out.append(from)
		out.append(to)
		return out
	# Gleiche Blockseite: direkt
	if sa.block == sb.block and sa.a == sb.a and sa.b == sb.b:
		out.append(from)
		out.append(sa.p)
		out.append(sb.p)
		out.append(to)
		return out
	var ta := _tmp_ped_id
	var tb := _tmp_ped_id + 1
	_astar_ped.add_point(ta, sa.p)
	_astar_ped.add_point(tb, sb.p)
	_astar_ped.connect_points(ta, sa.a)
	_astar_ped.connect_points(ta, sa.b)
	_astar_ped.connect_points(tb, sb.a)
	_astar_ped.connect_points(tb, sb.b)
	var path := _astar_ped.get_point_path(ta, tb)
	_astar_ped.remove_point(ta)
	_astar_ped.remove_point(tb)
	out.append(from)
	out.append_array(path)
	out.append(to)
	return out


## Projektion eines Punktes auf die naechste Gehweg-Ringseite.
func _attach_ped(p: Vector2) -> Dictionary:
	var best := {}
	var bd := INF
	for bid in _candidate_blocks(p):
		var ids: Array = _block_ring_ids.get(bid, [])
		if ids.size() != 4:
			continue
		for k in 4:
			var a: Vector2 = ped_points[ids[k]]
			var b: Vector2 = ped_points[ids[(k + 1) % 4]]
			var q := Geometry2D.get_closest_point_to_segment(p, a, b)
			var d := q.distance_squared_to(p)
			if d < bd:
				bd = d
				best = {"block": bid, "a": ids[k], "b": ids[(k + 1) % 4], "p": q}
	return best


func _candidate_blocks(p: Vector2) -> Array:
	var out := []
	var k := chunk_key(p)
	for dx in [-1, 0, 1]:
		for dz in [-1, 0, 1]:
			var c = chunks.get(k + Vector2i(dx, dz))
			if c != null:
				out.append_array(c.blocks)
	if out.is_empty():
		for b in blocks:
			out.append(b.id)
	return out


## Laenge einer Polyline.
static func polyline_length(pts: PackedVector2Array) -> float:
	var l := 0.0
	for i in range(1, pts.size()):
		l += pts[i - 1].distance_to(pts[i])
	return l


## Bordstein-Reservierung (vom Generator genutzt, fuer Validierung lesbar).
func is_curb_free(edge_id: int, side: int, t0: float, t1: float, kind: String) -> bool:
	var key := "%d:%d" % [edge_id, side]
	for r in curb_reservations.get(key, []):
		if t1 > r[0] and t0 < r[1] and not _compatible(kind, r[2]):
			return false
	return true


func reserve_curb(edge_id: int, side: int, t0: float, t1: float, kind: String) -> bool:
	if not is_curb_free(edge_id, side, t0, t1, kind):
		return false
	var key := "%d:%d" % [edge_id, side]
	if not curb_reservations.has(key):
		curb_reservations[key] = []
	curb_reservations[key].append([t0, t1, kind])
	return true


static func _compatible(a: String, b: String) -> bool:
	var soft := ["entrance", "walk"]
	if a in soft and b in soft:
		return true
	if (a == "entrance" and b == "bus_stop") or (a == "bus_stop" and b == "entrance"):
		return true
	return false


## Eindeutiger Fingerabdruck des Layouts (Reproduzierbarkeitstest).
func layout_hash() -> int:
	var parts := PackedStringArray()
	for n in nodes:
		parts.append("%d:%.2f,%.2f:%s" % [n.id, n.pos.x, n.pos.y, n.kind])
	for e in edges:
		parts.append("%d:%d-%d:%s" % [e.id, e.a, e.b, e.cls])
	for b in buildings:
		parts.append("%d:%s:%s:%d:%s" % [b.id, b.kind, str(b.footprint), b.floors, b.name])
	parts.append("props:%d" % props.size())
	return "|".join(parts).hash()


func zone_info(zone: String) -> Dictionary:
	return CityCatalog.ZONES.get(zone, CityCatalog.ZONES["residential"])
