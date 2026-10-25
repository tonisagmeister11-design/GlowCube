class_name LaneGraph
extends RefCounted
## Vorberechnetes Fahrspurnetz aus dem CityModel.
##
## Segmente sind entweder Fahrstreifen (KIND_LANE: je Kante, Richtung und
## Spur ein gerades Stueck zwischen den Haltelinien) oder Abbieger
## (KIND_CONN: Kurve durch einen Knoten von einer ankommenden Spur zu einer
## abgehenden Spur). Beide teilen sich die IDs und werden als Polylinien mit
## kumulierter Laenge gespeichert. Fahrzeuge bewegen sich ausschliesslich
## auf diesen Polylinien und koennen die Fahrbahn daher nicht verlassen.
##
## Rechtsverkehr: Spur 0 ist die innere (Abbiegen links), die aeusserste Spur
## biegt rechts ab. Kreisverkehre werden gegen den Uhrzeigersinn (Draufsicht,
## Norden oben) auf dem Radius (r_inner + r_outer) / 2 befahren.
## Konfliktdaten (welche Abbieger sich kreuzen oder zusammenlaufen) werden je
## Knoten bei der ersten Abfrage berechnet.

const KIND_LANE := 0
const KIND_CONN := 1
const TURN_STRAIGHT := 0
const TURN_RIGHT := 1
const TURN_LEFT := 2
const TURN_UTURN := 3
const TURN_NAMES := ["geradeaus", "rechts", "links", "wenden"]

const STOP_MARGIN := 0.8      # Haltelinie hinter dem Zebrastreifen
const CONFLICT_CELL := 1.4    # Rastergroesse fuer Konfliktbestimmung
const GRID := 50.0            # Raster fuer Spursuche
const LAT_ACCEL := 2.6        # zulaessige Querbeschleunigung in Kurven

var city: CityModel
var pts: Array = []                   # PackedVector2Array je Segment
var cum: Array = []                   # PackedFloat32Array je Segment
var seg_len := PackedFloat32Array()
var kind := PackedByteArray()
var speed := PackedFloat32Array()
var next: Array = []                  # PackedInt32Array: Lane -> Abbieger, Abbieger -> [Lane]
var node := PackedInt32Array()        # Lane: Endknoten, Abbieger: Knoten
var from_node := PackedInt32Array()   # Lane: Startknoten, Abbieger: Knoten
var edge := PackedInt32Array()        # Lane: Kante, Abbieger: Ausgangskante
var fwd := PackedByteArray()          # Lane: 1 = Richtung a -> b
var lane_idx := PackedInt32Array()    # Lane: Spurindex, Abbieger: Spurindex der Zielspur
var conn_in := PackedInt32Array()     # Abbieger: Eingangsspur (sonst -1)
var conn_out := PackedInt32Array()    # Abbieger: Zielspur (sonst -1)
var turn := PackedByteArray()         # Abbieger: TURN_*
var t_start := PackedFloat32Array()   # Lane: t auf der Kante (ab Knoten a) bei s = 0
var t_sign := PackedFloat32Array()    # Lane: +1 vorwaerts, -1 rueckwaerts

var lane_key: Dictionary = {}         # Vector3i(edge, fwd, lane) -> Segment
var node_conns: Array = []            # Knoten -> Array[int] Abbieger
var node_in_lanes: Array = []         # Knoten -> Array[int] ankommende Spuren
var node_out_lanes: Array = []        # Knoten -> Array[int] abgehende Spuren
var conflicts: Dictionary = {}        # Abbieger -> PackedInt32Array (lazy)
var ring: Dictionary = {}             # Abbieger am Kreisel -> {th_in, th_out, rr}
var uturn_nodes: Dictionary = {}      # Knoten mit Wendeschleifen
var crosswalk_at: Dictionary = {}     # node * 8192 + edge -> Index in city.crosswalks
var _grid: Dictionary = {}            # Vector2i -> Array[int] Spuren
var _conf_done: Dictionary = {}


static func build(model: CityModel, extra_uturn_nodes := []) -> LaneGraph:
	var g := LaneGraph.new()
	g.city = model
	for n in extra_uturn_nodes:
		g.uturn_nodes[int(n)] = true
	g._build()
	return g


func segment_count() -> int:
	return seg_len.size()


func is_lane(seg: int) -> bool:
	return kind[seg] == KIND_LANE


# ============================================================ Aufbau

func _build() -> void:
	var nn := city.nodes.size()
	node_conns.resize(nn)
	node_in_lanes.resize(nn)
	node_out_lanes.resize(nn)
	for i in nn:
		node_conns[i] = []
		node_in_lanes[i] = []
		node_out_lanes[i] = []
		if city.nodes[i].edges.size() <= 1:
			uturn_nodes[i] = true
	# Endpunkte der Buslinien bekommen Wendeschleifen
	for l in city.bus_lines:
		if not l.loop and l.nodes.size() >= 2:
			uturn_nodes[int(l.nodes[0])] = true
			uturn_nodes[int(l.nodes[-1])] = true
	for i in city.crosswalks.size():
		var cw: Dictionary = city.crosswalks[i]
		crosswalk_at[int(cw.node) * 8192 + int(cw.edge)] = i
	# Fahrstreifen
	for e in city.edges:
		var d_a := stop_distance(e, e.a)
		var d_b := stop_distance(e, e.b)
		for f in [true, false]:
			for li in int(e.lanes):
				var lat := city.lane_offset(e, f, li)
				var t0: float = d_a if f else e.length - d_b
				var t1: float = e.length - d_b if f else d_a
				var p := PackedVector2Array([city.edge_point(e, t0, lat), city.edge_point(e, t1, lat)])
				var id := _add_segment(p, KIND_LANE, e.speed)
				node[id] = e.b if f else e.a
				from_node[id] = e.a if f else e.b
				edge[id] = e.id
				fwd[id] = 1 if f else 0
				lane_idx[id] = li
				t_start[id] = t0
				t_sign[id] = 1.0 if f else -1.0
				lane_key[Vector3i(e.id, 1 if f else 0, li)] = id
				node_in_lanes[node[id]].append(id)
				node_out_lanes[from_node[id]].append(id)
				_grid_add(id)
	# Abbieger
	for n in city.nodes:
		_build_node_connectors(n)
	# Spuren ohne Abbieger notduerftig verbinden (sollte nicht vorkommen)
	for id in seg_len.size():
		if kind[id] == KIND_LANE and (next[id] as PackedInt32Array).is_empty():
			_fallback_connectors(id)


func _add_segment(p: PackedVector2Array, k: int, spd: float) -> int:
	var id := seg_len.size()
	var c := PackedFloat32Array()
	c.resize(p.size())
	var acc := 0.0
	c[0] = 0.0
	for i in range(1, p.size()):
		acc += p[i - 1].distance_to(p[i])
		c[i] = acc
	pts.append(p)
	cum.append(c)
	seg_len.append(acc)
	kind.append(k)
	speed.append(spd)
	next.append(PackedInt32Array())
	node.append(-1)
	from_node.append(-1)
	edge.append(-1)
	fwd.append(0)
	lane_idx.append(0)
	conn_in.append(-1)
	conn_out.append(-1)
	turn.append(0)
	t_start.append(0.0)
	t_sign.append(1.0)
	return id


## Abstand der Haltelinie vom Knotenmittelpunkt fuer eine Kante.
func stop_distance(e: Dictionary, node_id: int) -> float:
	var n: Dictionary = city.nodes[node_id]
	match n.kind:
		"pass":
			return 2.5
		"corner":
			return city.edge_end_clearance(e, node_id) * 0.6
		"roundabout":
			return float(n.roundabout.r_outer) + 4.7
	if n.edges.size() <= 1:
		return 4.0
	return city.edge_end_clearance(e, node_id) + STOP_MARGIN


func lanes_of(edge_id: int, forward: bool) -> Array:
	var e: Dictionary = city.edges[edge_id]
	var out := []
	for li in int(e.lanes):
		out.append(lane_key[Vector3i(edge_id, 1 if forward else 0, li)])
	return out


## Ankommende Spuren einer Kante am Knoten.
func in_lanes(edge_id: int, node_id: int) -> Array:
	return lanes_of(edge_id, city.edges[edge_id].b == node_id)


## Abgehende Spuren einer Kante am Knoten.
func out_lanes(edge_id: int, node_id: int) -> Array:
	return lanes_of(edge_id, city.edges[edge_id].a == node_id)


func lane_dir(seg: int) -> Vector2:
	var p: PackedVector2Array = pts[seg]
	return (p[p.size() - 1] - p[0]).normalized()


func _turn_type(h_in: Vector2, h_out: Vector2) -> int:
	var d := h_in.dot(h_out)
	if d < -0.7:
		return TURN_UTURN
	if d > 0.7:
		return TURN_STRAIGHT
	var cr := h_in.x * h_out.y - h_in.y * h_out.x
	return TURN_RIGHT if cr > 0.0 else TURN_LEFT


func _build_node_connectors(n: Dictionary) -> void:
	var nid: int = n.id
	var deg: int = n.edges.size()
	var is_rb: bool = n.kind == "roundabout"
	for ein in n.edges:
		var lanes_in := in_lanes(ein, nid)
		var e_in: Dictionary = city.edges[ein]
		for eout in n.edges:
			var e_out: Dictionary = city.edges[eout]
			var lanes_out := out_lanes(eout, nid)
			var h_in := lane_dir(lanes_in[0])
			var h_out := lane_dir(lanes_out[0])
			var tt := TURN_UTURN if eout == ein else _turn_type(h_in, h_out)
			if tt == TURN_UTURN and not uturn_nodes.has(nid):
				continue
			var nin: int = lanes_in.size()
			var nout: int = lanes_out.size()
			for li in nin:
				var lo := -1
				if is_rb or deg <= 2:
					lo = mini(li, nout - 1)
				else:
					match tt:
						TURN_STRAIGHT:
							lo = mini(li, nout - 1)
						TURN_RIGHT:
							if li == nin - 1:
								lo = nout - 1
						TURN_LEFT, TURN_UTURN:
							if li == 0:
								lo = 0
				if lo < 0:
					continue
				_add_connector(n, lanes_in[li], lanes_out[lo], tt, e_in, e_out)


func _fallback_connectors(lane: int) -> void:
	var nid: int = node[lane]
	var n: Dictionary = city.nodes[nid]
	for eout in n.edges:
		if eout == edge[lane] and n.edges.size() > 1:
			continue
		var lanes_out := out_lanes(eout, nid)
		var lo := mini(lane_idx[lane], lanes_out.size() - 1)
		var tt := _turn_type(lane_dir(lane), lane_dir(lanes_out[lo]))
		if eout == edge[lane]:
			tt = TURN_UTURN
		_add_connector(n, lane, lanes_out[lo], tt, city.edges[edge[lane]], city.edges[eout])


func _add_connector(n: Dictionary, lin: int, lout: int, tt: int, e_in: Dictionary, e_out: Dictionary) -> void:
	var pin: PackedVector2Array = pts[lin]
	var pout: PackedVector2Array = pts[lout]
	var p0: Vector2 = pin[pin.size() - 1]
	var p1: Vector2 = pout[0]
	var h0 := lane_dir(lin)
	var h1 := lane_dir(lout)
	var poly := PackedVector2Array()
	var info := {}
	if n.kind == "roundabout":
		info = _roundabout_path(n, p0, h0, p1, h1, poly)
	else:
		var chord := p0.distance_to(p1)
		var k := chord / 3.0
		if tt == TURN_LEFT or tt == TURN_RIGHT:
			k = chord * 0.39
		elif tt == TURN_UTURN:
			var box := city.node_box(n.id)
			k = clampf(maxf(box.x, box.y) * 1.6, 7.0, 16.0)
		var steps := clampi(int(chord / 1.2) + 6, 6, 26)
		if tt == TURN_STRAIGHT:
			steps = 2
		_cubic(p0, p0 + h0 * k, p1 - h1 * k, p1, steps, poly, true)
	var spd: float = minf(e_in.speed, e_out.speed)
	if tt != TURN_STRAIGHT:
		spd = minf(spd, sqrt(LAT_ACCEL * _min_radius(poly)))
	spd = maxf(spd, 3.0)
	var id := _add_segment(poly, KIND_CONN, spd)
	node[id] = n.id
	from_node[id] = n.id
	edge[id] = e_out.id
	lane_idx[id] = lane_idx[lout]
	conn_in[id] = lin
	conn_out[id] = lout
	turn[id] = tt
	next[id] = PackedInt32Array([lout])
	var nx: PackedInt32Array = next[lin]
	nx.append(id)
	next[lin] = nx
	node_conns[n.id].append(id)
	if not info.is_empty():
		ring[id] = info


func _cubic(a: Vector2, b: Vector2, c: Vector2, d: Vector2, steps: int, out: PackedVector2Array, include_first: bool) -> void:
	for i in range(0 if include_first else 1, steps + 1):
		var t := float(i) / steps
		var u := 1.0 - t
		out.append(a * (u * u * u) + b * (3.0 * u * u * t) + c * (3.0 * u * t * t) + d * (t * t * t))


## Weg durch einen Kreisverkehr: Einfahrt (Bezier), Kreisbogen, Ausfahrt.
## Umlaufrichtung: abnehmender Winkel in (x, z) = gegen den Uhrzeigersinn
## in der Draufsicht mit Norden oben.
func _roundabout_path(n: Dictionary, p0: Vector2, h0: Vector2, p1: Vector2, h1: Vector2, poly: PackedVector2Array) -> Dictionary:
	var c: Vector2 = n.pos
	var rr: float = (float(n.roundabout.r_inner) + float(n.roundabout.r_outer)) * 0.5
	var arm_in := (p0 - c).normalized()
	var arm_out := (p1 - c).normalized()
	var delta := 0.62
	var th_in := atan2(arm_in.y, arm_in.x) - delta
	var th_out := atan2(arm_out.y, arm_out.x) + delta
	while th_out >= th_in - 0.05:
		th_out -= TAU
	while th_out < th_in - TAU - 0.05:
		th_out += TAU
	var q_in := c + Vector2(cos(th_in), sin(th_in)) * rr
	var t_in := Vector2(sin(th_in), -cos(th_in))
	var q_out := c + Vector2(cos(th_out), sin(th_out)) * rr
	var t_out := Vector2(sin(th_out), -cos(th_out))
	var k_in := p0.distance_to(q_in) * 0.42
	_cubic(p0, p0 + h0 * k_in, q_in - t_in * k_in, q_in, 6, poly, true)
	var arc := th_in - th_out
	var steps := maxi(3, int(arc * rr / 1.5))
	for i in range(1, steps):
		var th := th_in - arc * float(i) / steps
		poly.append(c + Vector2(cos(th), sin(th)) * rr)
	var k_out := p1.distance_to(q_out) * 0.42
	_cubic(q_out, q_out + t_out * k_out, p1 - h1 * k_out, p1, 6, poly, true)
	return {"th_in": th_in, "th_out": th_out, "rr": rr}


func _min_radius(p: PackedVector2Array) -> float:
	var r := 1e6
	for i in range(1, p.size() - 1):
		var a := p[i - 1]
		var b := p[i]
		var c := p[i + 1]
		var ab := a.distance_to(b)
		var bc := b.distance_to(c)
		var ca := c.distance_to(a)
		var cr := absf((b - a).cross(c - a))
		if cr < 1e-5 or ab < 0.05 or bc < 0.05:
			continue
		r = minf(r, ab * bc * ca / (2.0 * cr))
	return r


# ============================================================ Konflikte

## Abbieger, die sich mit `conn` kreuzen oder in dieselbe Spur muenden.
## Abbieger aus derselben Eingangsspur gelten nicht als Konflikt (sie werden
## ueber das Folgefahren geordnet). Am Kreisverkehr leer (eigene Regeln).
func conflicts_of(conn: int) -> PackedInt32Array:
	var nid: int = node[conn]
	if not _conf_done.has(nid):
		_compute_conflicts(nid)
	return conflicts.get(conn, PackedInt32Array())


func _compute_conflicts(nid: int) -> void:
	_conf_done[nid] = true
	var list: Array = node_conns[nid]
	if city.nodes[nid].kind == "roundabout":
		return
	var cells_of := []
	var own := {}
	for bi in list.size():
		var c: int = list[bi]
		var cells := {}
		var p: PackedVector2Array = pts[c]
		var cm: PackedFloat32Array = cum[c]
		var total: float = seg_len[c]
		var s := 0.0
		while s <= total:
			var q := _pos_on(p, cm, s)
			cells[Vector2i(floori(q.x / CONFLICT_CELL), floori(q.y / CONFLICT_CELL))] = true
			s += 0.6
		cells_of.append(cells)
		for k in cells:
			own[k] = own.get(k, 0) | (1 << bi)
	for bi in list.size():
		var mask := 0
		for k in cells_of[bi]:
			for dx in [-1, 0, 1]:
				for dz in [-1, 0, 1]:
					mask |= own.get(k + Vector2i(dx, dz), 0)
		var out := PackedInt32Array()
		var c: int = list[bi]
		for bj in list.size():
			if bj == bi or (mask & (1 << bj)) == 0:
				continue
			var o: int = list[bj]
			if conn_in[o] == conn_in[c]:
				continue
			out.append(o)
		conflicts[c] = out


func is_conflict(a: int, b: int) -> bool:
	if a == b:
		return true
	return conflicts_of(a).has(b)


# ============================================================ Abfragen

func _pos_on(p: PackedVector2Array, cm: PackedFloat32Array, s: float) -> Vector2:
	var n := p.size()
	if s <= 0.0:
		return p[0]
	if s >= cm[n - 1]:
		return p[n - 1]
	var i := cm.bsearch(s)
	if i <= 0:
		i = 1
	var a := cm[i - 1]
	var b := cm[i]
	var f := (s - a) / maxf(b - a, 1e-6)
	return p[i - 1].lerp(p[i], f)


## Punkt auf einem Segment bei Bogenlaenge s (wird ausserhalb geklemmt).
func pos_at(seg: int, s: float) -> Vector2:
	var p: PackedVector2Array = pts[seg]
	if p.size() == 2:
		var l: float = seg_len[seg]
		if l <= 0.0:
			return p[0]
		return p[0].lerp(p[1], clampf(s / l, 0.0, 1.0))
	return _pos_on(p, cum[seg], s)


## Fahrtrichtung auf einem Segment bei Bogenlaenge s.
func dir_at(seg: int, s: float) -> Vector2:
	var p: PackedVector2Array = pts[seg]
	if p.size() == 2:
		return (p[1] - p[0]).normalized()
	var cm: PackedFloat32Array = cum[seg]
	var i := clampi(cm.bsearch(clampf(s, 0.0, seg_len[seg])), 1, p.size() - 1)
	var d := p[i] - p[i - 1]
	if d.length_squared() < 1e-8:
		return lane_dir(conn_in[seg]) if conn_in[seg] >= 0 else Vector2(0, -1)
	return d.normalized()


## Spur-Parameter s fuer eine Kantenposition t (Meter ab Knoten a).
func lane_s_for_t(lane: int, t: float) -> float:
	return (t - t_start[lane]) * t_sign[lane]


func lane_t_for_s(lane: int, s: float) -> float:
	return t_start[lane] + s * t_sign[lane]


func _grid_add(lane: int) -> void:
	var p: PackedVector2Array = pts[lane]
	var r := Rect2(p[0], Vector2.ZERO).expand(p[1])
	var k0 := Vector2i(floori(r.position.x / GRID), floori(r.position.y / GRID))
	var k1 := Vector2i(floori(r.end.x / GRID), floori(r.end.y / GRID))
	for x in range(k0.x, k1.x + 1):
		for z in range(k0.y, k1.y + 1):
			var k := Vector2i(x, z)
			if not _grid.has(k):
				_grid[k] = []
			_grid[k].append(lane)


## Spuren, deren Achse durch das Rasterfeld um p verlaeuft (3x3 Felder).
func lanes_near(p: Vector2) -> Array:
	var out := []
	var k := Vector2i(floori(p.x / GRID), floori(p.y / GRID))
	for dx in [-1, 0, 1]:
		for dz in [-1, 0, 1]:
			for id in _grid.get(k + Vector2i(dx, dz), []):
				if not id in out:
					out.append(id)
	return out


## Naechste Fahrspur zu einem Punkt: {seg, s, dist}. heading (optional)
## bevorzugt Spuren in passender Richtung.
func nearest_lane(p: Vector2, heading := Vector2.ZERO) -> Dictionary:
	var best := {"seg": -1, "s": 0.0, "dist": INF}
	var cand := lanes_near(p)
	if cand.is_empty():
		for i in seg_len.size():
			if kind[i] == KIND_LANE:
				cand.append(i)
	for id in cand:
		var a: Vector2 = pts[id][0]
		var d := lane_dir(id)
		var s := clampf((p - a).dot(d), 0.0, seg_len[id])
		var q := a + d * s
		var dist := q.distance_to(p)
		if heading != Vector2.ZERO and d.dot(heading) < 0.0:
			dist += 6.0
		if dist < best.dist:
			best = {"seg": id, "s": s, "dist": dist}
	return best


## Kreuzungswinkel eines Punktes relativ zum Kreiselmittelpunkt.
func ring_angle(nid: int, p: Vector2) -> float:
	var c: Vector2 = city.nodes[nid].pos
	return atan2(p.y - c.y, p.x - c.x)


## Zebrastreifen eines Knotenarms (Index in city.crosswalks oder -1).
func crosswalk_of(nid: int, edge_id: int) -> int:
	return crosswalk_at.get(nid * 8192 + edge_id, -1)
