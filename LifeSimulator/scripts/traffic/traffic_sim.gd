class_name TrafficSim
extends RefCounted
## Verkehrssimulation ohne Szenenbaum: verwaltet KI-Fahrzeuge (VehicleAI)
## auf dem LaneGraph, Belegung je Segment, Kreuzungsreservierungen,
## Vorfahrt/Ampeln, Hindernisse (Spieler, Fussgaenger), Zebrastreifen,
## Busse nach Fahrplan (Transit) und Einsatzfahrzeuge.
##
## Ablauf je Schritt: Belegung aufbauen -> Spurwechsel/Einsatzlogik ->
## think() aller Fahrzeuge -> move() aller Fahrzeuge -> Verklemmungen loesen.
## Der TrafficManager ruft step() jedes Frame auf; Tests rufen step() mit
## festem Zeitschritt.

const APPROACH_RANGE := 80.0
const STUCK_GHOST := 45.0        # Sekunden Stillstand bis zur Notaufloesung
const GHOST_TIME := 6.0
const PRIORITY_GAP := 4.0        # Zeitluecke beim Einfahren (Vorfahrt achten)

var city: CityModel
var graph: LaneGraph
var transit: Transit
var world                         # WorldState (optional; Uhrzeit fuer Busse)
var rng := RandomNumberGenerator.new()
var vehicles: Array = []
var dt := 0.05
var time := 0.0                   # Simulationssekunden
var sig_t := 0.0                  # Ampeluhr (Sekunden)

var occ: Dictionary = {}          # Segment -> Array[[s_equiv, VehicleAI]] (aufsteigend)
var approach: Dictionary = {}     # Knoten -> Array[VehicleAI] auf ankommenden Spuren
var holders: Dictionary = {}      # Knoten -> Array[VehicleAI] mit Reservierung
var reserved: Dictionary = {}     # Abbieger -> Anzahl Halter
var into_lane: Dictionary = {}    # Spur -> Array[VehicleAI] mit Abbieger in diese Spur
var obstacles: Array = []         # [{pos: Vector2, r: float, vel: Vector2}]
var crosswalk_busy: Dictionary = {}  # Zebrastreifen-Index -> Ablaufzeit
var curb_free_spots: Dictionary = {} # edge*2+(side>0) -> Array[float t] freie Parkbuchten

var stats := {"ghosts": 0, "red_entries": 0, "entries": 0, "bus_stops": 0, "trips": 0}
## Testhaken: Callable(v: VehicleAI, conn: int, state: String)
var on_connector_entered: Callable
var _next_id := 1
var _bus_stop_cache: Dictionary = {}   # line -> leg -> Array[{stop, t, cycle_s}]


static func create(model: CityModel, p_world = null, seed_value := 1) -> TrafficSim:
	var t := TrafficSim.new()
	t.city = model
	t.world = p_world
	t.rng.seed = hash("traffic:%d" % seed_value)
	t.graph = LaneGraph.build(model)
	t.transit = Transit.new(model)
	t._index_curb()
	return t


func _index_curb() -> void:
	for i in city.street_parking.size():
		var sp: Dictionary = city.street_parking[i]
		if spot_occupied(i):
			continue
		var k: int = int(sp.edge) * 2 + (1 if int(sp.side) > 0 else 0)
		if not curb_free_spots.has(k):
			curb_free_spots[k] = []
		curb_free_spots[k].append(float(sp.t))


## Deterministische Belegung der Strassenparkplaetze (auch fuer die
## Darstellung der geparkten Autos).
func spot_occupied(i: int) -> bool:
	return (hash("park:%d:%d" % [city.seed, i]) % 100) < 62


# ============================================================ Fahrzeuge

## Prueft, ob auf einer Spur bei s (Front) Platz fuer ein Fahrzeug ist.
func can_place(lane: int, s: float, length: float, margin := 6.0) -> bool:
	if s - length < 0.5 or s > graph.seg_len[lane] - 0.5:
		return false
	for e in occ.get(lane, []):
		var w: VehicleAI = e[1]
		var ws: float = e[0]
		if ws > s - length - margin and ws - w.length < s + margin:
			return false
	for w in vehicles:
		if w.seg == lane and w.s > s - length - margin and w.s - w.length < s + margin:
			return false
	return true


func spawn(type_id: String, lane: int, s: float, role := "ambient") -> VehicleAI:
	var v := VehicleAI.new()
	v.id = _next_id
	_next_id += 1
	v.setup_type(type_id, rng)
	v.role = role
	v.seg = lane
	v.s = s
	v.v = minf(graph.speed[lane] * 0.7, v.v_max)
	vehicles.append(v)
	_add_occ(v)
	v.update_pose(graph)
	_plan_lane(v)
	return v


func remove(v: VehicleAI) -> void:
	for c in v.held.duplicate():
		release(v, c)
	vehicles.erase(v)
	for k in occ:
		var lst: Array = occ[k]
		for i in range(lst.size() - 1, -1, -1):
			if lst[i][1] == v:
				lst.remove_at(i)


## Bus einer Linie an seiner Fahrplanposition einsetzen.
func spawn_bus(lid: int, idx: int) -> VehicleAI:
	var bp := transit.bus_position(world, lid, idx)
	var e: Dictionary = city.edges[bp.edge]
	var lane: int = graph.lane_key[Vector3i(bp.edge, 1 if bp.fwd else 0, int(e.lanes) - 1)]
	var len_bus: float = VehicleCatalog.type_info("bus").length
	var s: float = graph.lane_s_for_t(lane, bp.t) + len_bus * 0.5
	s = clampf(s, len_bus + 0.6, graph.seg_len[lane] - 0.6)
	var v := VehicleAI.new()
	v.id = _next_id
	_next_id += 1
	v.setup_type("bus", rng)
	v.v_factor = 1.0
	v.role = "bus"
	v.line = lid
	v.bus_index = idx
	v.leg = bp.leg
	v.seg = lane
	v.s = s
	v.v = 0.0 if bp.at_stop >= 0 else 6.0
	if bp.at_stop >= 0:
		v.served_stop = -1
	else:
		# bereits passierte Haltestellen dieses Abschnitts nicht erneut anfahren
		for st in _stops_on_leg(lid, bp.leg):
			if graph.lane_s_for_t(lane, st.t) + len_bus * 0.5 < s - 1.0:
				v.served_stop = st.stop
	vehicles.append(v)
	_add_occ(v)
	v.update_pose(graph)
	_plan_lane(v)
	return v


## Einsatzfahrzeug, das zu einem Zielpunkt faehrt.
func spawn_emergency(type_id: String, lane: int, s: float, target: Vector2) -> VehicleAI:
	var v := spawn(type_id, lane, s, "emergency")
	v.siren = true
	set_target(v, target)
	return v


func set_target(v: VehicleAI, target: Vector2) -> void:
	var nl := graph.nearest_lane(target)
	v.target_lane = nl.seg
	v.target_s = clampf(nl.s + v.length * 0.5, v.length + 2.0, graph.seg_len[nl.seg] - 3.0)
	v.arrived = false
	v.route = PackedInt64Array()
	if v.seg >= 0 and graph.is_lane(v.seg) and not v.committed:
		_plan_lane(v)


## Einsatz beenden: Fahrzeug faehrt normal weiter.
func release_target(v: VehicleAI) -> void:
	v.target_lane = -1
	v.target_s = -1.0
	v.arrived = false
	v.siren = false
	v.role = "ambient"
	v.lat_target = 0.0
	if graph.is_lane(v.seg) and not v.committed:
		_plan_lane(v)


# ============================================================ Schritt

func step(delta: float, signal_t := -1.0) -> void:
	dt = delta
	time += delta
	sig_t = signal_t if signal_t >= 0.0 else time
	_rebuild_occ()
	_pre_pass()
	for v in vehicles:
		v.think(self, dt)
	for v in vehicles:
		v.move(self, dt)
	_resolve_stuck()


func _add_occ(v: VehicleAI) -> void:
	if absf(v.lat) >= 2.0 and (v.pulled_over or v.dwelling or v.pull_over_time > 0.0):
		return
	if not occ.has(v.seg):
		occ[v.seg] = []
	occ[v.seg].append([v.s, v])
	# Heck auf vorherigen Segmenten
	if v.s - v.length < 0.0 and v.prev_seg >= 0:
		var lp: float = graph.seg_len[v.prev_seg]
		if not occ.has(v.prev_seg):
			occ[v.prev_seg] = []
		occ[v.prev_seg].append([lp + v.s, v])
		if lp + v.s - v.length < 0.0 and v.prev2_seg >= 0:
			if not occ.has(v.prev2_seg):
				occ[v.prev2_seg] = []
			occ[v.prev2_seg].append([graph.seg_len[v.prev2_seg] + lp + v.s, v])


func _rebuild_occ() -> void:
	occ.clear()
	approach.clear()
	for v in vehicles:
		_add_occ(v)
		if graph.kind[v.seg] == LaneGraph.KIND_LANE:
			var rem: float = graph.seg_len[v.seg] - v.s
			if rem < APPROACH_RANGE:
				var n: int = graph.node[v.seg]
				if not approach.has(n):
					approach[n] = []
				approach[n].append(v)
	for k in occ:
		var lst: Array = occ[k]
		if lst.size() > 1:
			lst.sort_custom(func(a, b): return a[0] < b[0])


func _pre_pass() -> void:
	for v in vehicles:
		# Einsatzfahrzeug-Rueckmeldung zuruecksetzen
		if v.yielding:
			v.meta["yield_t"] = float(v.meta.get("yield_t", 0.0)) - dt
			if v.meta["yield_t"] <= 0.0:
				v.yielding = false
				if not v.pulled_over and not v.dwelling:
					v.lat_target = 0.0
		# Spurwechsel fuer den naechsten Abbieger
		if v.want_lane >= 0 and graph.is_lane(v.seg) and not v.committed:
			_try_lane_change(v)
		# Busse: Fahrplanabweichung
		if v.role == "bus":
			_bus_schedule(v)
		if v.siren and not v.arrived:
			_clear_way(v)


# ============================================================ Planung

## Beim Einfahren in eine Spur: naechsten Abbieger waehlen.
func _plan_lane(v: VehicleAI) -> void:
	v.next_conn = -1
	v.want_lane = -1
	v.committed = false
	var lane := v.seg
	var m: int = graph.node[lane]
	var came: int = graph.from_node[lane]
	var out_edge := -1
	if v.role == "bus" and v.line >= 0:
		var legs: Array = transit.lines[v.line].legs
		out_edge = legs[(v.leg + 1) % legs.size()].edge
	elif v.target_lane >= 0:
		out_edge = _next_edge_target(v, m, came)
	else:
		out_edge = _next_edge_ambient(v, m, came)
	if out_edge < 0 or not _choose_conn(v, out_edge):
		# Notfall: irgendein Abbieger dieser Spur (bevorzugt geradeaus)
		_choose_any(v)
		v.route = PackedInt64Array()
	_maybe_pull_over(v)


func _next_edge_ambient(v: VehicleAI, m: int, came: int) -> int:
	if v.route.size() > 0 and v.route_i >= 0 and v.route_i + 1 < v.route.size() and int(v.route[v.route_i]) == m:
		var nn := int(v.route[v.route_i + 1])
		v.route_i += 1
		return city.edge_between(m, nn)
	if v.route.size() > 0 and int(v.route[v.route.size() - 1]) == m:
		v.trips += 1
		stats.trips += 1
	# neues Ziel oder Neuplanung zum alten Ziel
	var keep := v.dest_node >= 0 and v.dest_node != m and v.route.size() == 0
	for attempt in 6:
		var dest := v.dest_node if keep and attempt == 0 else rng.randi() % city.nodes.size()
		if dest == m or (not keep and city.nodes[dest].pos.distance_to(city.nodes[m].pos) < 250.0):
			continue
		var r := city.road_route_between_nodes(m, dest)
		if r.size() < 2 or int(r[1]) == came:
			continue
		v.route = r
		v.route_i = 1
		v.dest_node = dest
		return city.edge_between(m, int(r[1]))
	v.route = PackedInt64Array()
	v.dest_node = -1
	return _random_edge(m, graph.edge[v.seg])


func _random_edge(m: int, not_edge: int) -> int:
	var opts := []
	for eid in city.nodes[m].edges:
		if eid != not_edge:
			opts.append(eid)
	if opts.is_empty():
		return not_edge
	return opts[rng.randi() % opts.size()]


func _next_edge_target(v: VehicleAI, m: int, came: int) -> int:
	var tl := v.target_lane
	var goal: int = graph.from_node[tl]
	if m == goal:
		if came != graph.node[tl] or graph.edge[tl] != graph.edge[v.seg]:
			return graph.edge[tl]
		return _random_edge(m, graph.edge[v.seg])
	var r := city.road_route_between_nodes(m, goal)
	if r.size() >= 2 and int(r[1]) != came:
		return city.edge_between(m, int(r[1]))
	return _random_edge(m, graph.edge[v.seg])


func _choose_conn(v: VehicleAI, out_edge: int) -> bool:
	var lane := v.seg
	var own := []
	var other := []
	for l2 in graph.lanes_of(graph.edge[lane], graph.fwd[lane] == 1):
		for c in graph.next[l2]:
			if graph.edge[c] != out_edge:
				continue
			if l2 == lane:
				own.append(c)
			else:
				other.append(c)
	if own.is_empty() and other.is_empty():
		return false
	if not own.is_empty():
		v.next_conn = own[0]
		if own.size() > 1 and v.role == "bus":
			for c in own:
				if graph.lane_idx[c] > graph.lane_idx[v.next_conn]:
					v.next_conn = c
		return true
	v.want_lane = graph.conn_in[other[0]]
	return true


func _choose_any(v: VehicleAI) -> void:
	var opts: PackedInt32Array = graph.next[v.seg]
	if opts.is_empty():
		return
	v.next_conn = opts[0]
	for c in opts:
		if graph.turn[c] == LaneGraph.TURN_STRAIGHT:
			v.next_conn = c


func _try_lane_change(v: VehicleAI) -> void:
	var rem: float = graph.seg_len[v.seg] - v.s
	if v.role == "bus" and not bus_stop_on_lane(v).is_empty() and rem > 14.0:
		return
	if rem < 14.0:
		# Spurwechsel nicht geschafft: anderer Abbieger, spaeter neu planen
		v.want_lane = -1
		_choose_any(v)
		v.route = PackedInt64Array()
		return
	if v.s < v.length + 1.0 or not _lane_free(v.want_lane, v):
		return
	var old := v.seg
	var d_lane: int = graph.lane_idx[v.want_lane] - graph.lane_idx[old]
	var e: Dictionary = city.edges[graph.edge[old]]
	v.seg = v.want_lane
	v.prev_seg = -1
	v.prev2_seg = -1
	v.lat -= d_lane * float(e.lane_w)
	v.lat_target = 0.0
	v.blinker = 1 if d_lane > 0 else -1
	v.meta["blink_t"] = 2.5
	v.want_lane = -1
	var out_edge := -1
	if v.role == "bus":
		var legs: Array = transit.lines[v.line].legs
		out_edge = legs[(v.leg + 1) % legs.size()].edge
	elif v.target_lane >= 0:
		out_edge = _next_edge_target(v, graph.node[v.seg], graph.from_node[v.seg])
	elif v.route.size() > 0 and v.route_i < v.route.size():
		out_edge = city.edge_between(graph.node[v.seg], int(v.route[v.route_i]))
	if out_edge < 0 or not _choose_conn(v, out_edge) or v.next_conn < 0:
		v.want_lane = -1
		_choose_any(v)
	_rebuild_occ()


func _lane_free(lane: int, v: VehicleAI) -> bool:
	for e in occ.get(lane, []):
		var w: VehicleAI = e[1]
		var ws: float = e[0]
		var ahead_gap := ws - w.length - v.s
		var behind_gap := v.s - v.length - ws
		if ahead_gap > 4.0 + v.v * 0.4:
			continue
		if behind_gap > 5.0 + maxf(0.0, w.v - v.v) * 2.0 + w.v * 0.6:
			continue
		return false
	return true


func _maybe_pull_over(v: VehicleAI) -> void:
	if v.role != "ambient" or not (v.kind in ["van", "taxi"]) or rng.randf() > 0.18:
		return
	var lane := v.seg
	var e: Dictionary = city.edges[graph.edge[lane]]
	if float(e.park_w) <= 0.0 or graph.lane_idx[lane] != int(e.lanes) - 1:
		return
	var side := 1 if graph.fwd[lane] == 1 else -1
	var spots: Array = curb_free_spots.get(int(e.id) * 2 + (1 if side > 0 else 0), [])
	var cands := []
	for t in spots:
		var ls := graph.lane_s_for_t(lane, t) + v.length * 0.5
		if ls > v.s + 25.0 and ls < graph.seg_len[lane] - 25.0:
			cands.append(ls)
	if cands.is_empty():
		return
	v.meta["pull_s"] = cands[rng.randi() % cands.size()]
	v.pull_over_time = rng.randf_range(12.0, 30.0)


# ============================================================ Ereignisse

func on_enter_connector(v: VehicleAI) -> void:
	stats.entries += 1
	var c := v.seg
	var lane_in: int = graph.conn_in[c]
	var axis: String = city.edges[graph.edge[lane_in]].axis
	var st := city.signal_state(graph.node[c], axis, sig_t)
	if graph.turn[c] == LaneGraph.TURN_LEFT:
		v.blinker = -1
	elif graph.turn[c] == LaneGraph.TURN_RIGHT:
		v.blinker = 1
	if on_connector_entered.is_valid():
		on_connector_entered.call(v, c, st)


func on_enter_lane(v: VehicleAI) -> void:
	v.stop_done = false
	v.stop_timer = 0.0
	v.wait_time = 0.0
	if v.blinker != 2:
		v.blinker = 0
	v.visited(graph.from_node[v.seg])
	if v.role == "bus" and v.line >= 0:
		var legs: Array = transit.lines[v.line].legs
		var e_id: int = graph.edge[v.seg]
		var f: bool = graph.fwd[v.seg] == 1
		for k in range(1, legs.size() + 1):
			var li: int = (v.leg + k) % legs.size()
			if legs[li].edge == e_id and legs[li].fwd == f:
				v.leg = li
				break
	_plan_lane(v)


func on_bus_arrived(v: VehicleAI, stop_id: int) -> void:
	stats.bus_stops += 1


func on_target_reached(_v: VehicleAI) -> void:
	pass


# ============================================================ Reservierungen

func reserve(v: VehicleAI, c: int) -> void:
	if c in v.held:
		return
	var axis: String = city.edges[graph.edge[graph.conn_in[c]]].axis
	var st := city.signal_state(graph.node[c], axis, sig_t)
	v.meta["commit_state"] = st
	if st == "red" and not v.siren and v.ghost_time <= 0.0:
		stats.red_entries += 1
	v.held.append(c)
	reserved[c] = int(reserved.get(c, 0)) + 1
	var n: int = graph.node[c]
	if not holders.has(n):
		holders[n] = []
	if not v in holders[n]:
		holders[n].append(v)
	var out: int = graph.conn_out[c]
	if not into_lane.has(out):
		into_lane[out] = []
	into_lane[out].append(v)


func release(v: VehicleAI, c: int) -> void:
	if not c in v.held:
		return
	v.held.erase(c)
	reserved[c] = maxi(0, int(reserved.get(c, 0)) - 1)
	var n: int = graph.node[c]
	var still := false
	for c2 in v.held:
		if graph.node[c2] == n:
			still = true
	if not still and holders.has(n):
		holders[n].erase(v)
	var out: int = graph.conn_out[c]
	if into_lane.has(out):
		into_lane[out].erase(v)


## Freier Platz am Anfang einer Spur (abzueglich Fahrzeugen, die gerade
## in diese Spur abbiegen).
func lane_space(lane: int, me: VehicleAI) -> float:
	var space: float = graph.seg_len[lane]
	var lst: Array = occ.get(lane, [])
	if not lst.is_empty():
		var w: VehicleAI = lst[0][1]
		space = minf(space, lst[0][0] - w.length)
	for w2 in into_lane.get(lane, []):
		if w2 != me and w2.seg != lane:
			space -= w2.length + 2.5
	return space


# ============================================================ Kreuzungsregeln

func _rank(v: VehicleAI, c: int, nd: Dictionary) -> int:
	var t: int = graph.turn[c]
	var r := 2 if (t == LaneGraph.TURN_STRAIGHT or t == LaneGraph.TURN_RIGHT) else (1 if t == LaneGraph.TURN_LEFT else 0)
	if nd.kind == "yield":
		var axis: String = city.edges[graph.edge[graph.conn_in[c]]].axis
		if axis == nd.priority_axis:
			r += 10
	if v.siren:
		r += 100
	return r


## Darf v in Abbieger c einfahren? "" = ja, sonst Grund.
func may_enter(v: VehicleAI, c: int, dist: float) -> String:
	var n: int = graph.node[c]
	var nd: Dictionary = city.nodes[n]
	var lane_in: int = graph.conn_in[c]
	var axis: String = city.edges[graph.edge[lane_in]].axis
	var ghost := v.ghost_time > 0.0
	match nd.kind:
		"signal":
			var st := city.signal_state(n, axis, sig_t)
			if v.siren:
				if st != "green" and v.v > 4.0 and dist > 6.0:
					return "rot"
			elif st == "red":
				return "rot"
			elif st == "yellow" and dist > v.v * v.v / (2.0 * 3.2) + 1.5:
				return "rot"
		"stop":
			if not v.siren:
				if not v.stop_done:
					if dist < 3.0 and v.v < 0.4:
						v.stop_timer += dt
						if v.stop_timer > 0.7:
							v.stop_done = true
							v.arrive_time = time
					return "stopp"
				if not ghost and v.wait_time < 25.0:
					for w in approach.get(n, []):
						if w == v or not w.stop_done or w.committed or w.next_conn < 0:
							continue
						if w.arrive_time < v.arrive_time and graph.is_conflict(c, w.next_conn):
							return "stopp"
		"roundabout":
			if not ghost and not _ring_clear(v, c, n):
				return "kreisel"
	if ghost:
		return ""
	# belegte Konfliktabbieger
	for c2 in graph.conflicts_of(c):
		if int(reserved.get(c2, 0)) > 0:
			return "konflikt"
	if int(reserved.get(c, 0)) > 0 and nd.kind != "roundabout":
		# derselbe Abbieger wird noch von einem Vorausfahrenden gehalten:
		# folgen ist erlaubt (Abstand regelt das Folgefahren)
		pass
	# Vorfahrt: hoeherrangige Fahrzeuge, die sich naehern
	if nd.kind == "signal" or nd.kind == "yield":
		if _must_yield(v, c, n, nd):
			return "vorfahrt"
	# Fussgaenger auf Zebrastreifen
	for cw in [graph.crosswalk_of(n, graph.edge[lane_in]), graph.crosswalk_of(n, graph.edge[c])]:
		if cw >= 0 and float(crosswalk_busy.get(cw, -1.0)) > time:
			return "zebra"
	# Kreuzung nicht blockieren
	if lane_space(graph.conn_out[c], v) < v.length + 1.5:
		return "platz"
	return ""


func _must_yield(v: VehicleAI, c: int, n: int, nd: Dictionary) -> bool:
	var my_rank := _rank(v, c, nd)
	var relaxed := v.wait_time > 12.0
	for w in approach.get(n, []):
		if w == v or w.committed or w.next_conn < 0:
			continue
		if nd.kind == "signal":
			var aw: String = city.edges[graph.edge[w.seg]].axis
			if city.signal_state(n, aw, sig_t) == "red" and not w.siren:
				continue
		if _rank(w, w.next_conn, nd) <= my_rank:
			continue
		if not graph.is_conflict(c, w.next_conn):
			continue
		var dw: float = graph.seg_len[w.seg] - w.s
		if w.v < 0.5:
			if dw < 5.0 and v.wait_time < 20.0:
				return true
			continue
		var gap_t := 1.5 if relaxed else PRIORITY_GAP
		if dw < w.v * gap_t + 4.0 or dw < w.v * w.v / (2.0 * 3.0) + 3.0:
			return true
	return false


## Kreisverkehr: Einfahrt nur, wenn kein Fahrzeug im Kreis die Einfahrt
## gleich passiert.
func _ring_clear(v: VehicleAI, c: int, n: int) -> bool:
	var info: Dictionary = graph.ring.get(c, {})
	if info.is_empty():
		return true
	var th_in: float = info.th_in
	var rr: float = info.rr
	var q_in: Vector2 = city.nodes[n].pos + Vector2(cos(th_in), sin(th_in)) * rr
	for w in holders.get(n, []):
		if w == v:
			continue
		var wc := -1
		for c2 in w.held:
			if graph.node[c2] == n:
				wc = c2
		if wc < 0:
			continue
		var wi: Dictionary = graph.ring.get(wc, {})
		if wi.is_empty():
			continue
		if w.pos.distance_to(q_in) < 7.5:
			return false
		var th_w: float = wi.th_in
		if w.seg == wc:
			if w.s > graph.seg_len[wc] * 0.82:
				continue   # faehrt gerade aus
			th_w = float(wi.th_out) + fposmod(graph.ring_angle(n, w.pos) - float(wi.th_out), TAU)
			if th_w > float(wi.th_in) + 0.5:
				th_w = wi.th_in
		elif graph.is_lane(w.seg) and graph.node[w.seg] != n:
			continue   # hat den Kreisel schon verlassen
		var ang := fposmod(th_w - th_in, TAU)
		var remaining := th_w - float(wi.th_out)
		if ang < 2.0 and remaining > ang - 0.1:
			return false
	return true


# ============================================================ Folgen / Hindernisse

## Vordermann entlang des Plans: [Luecke, Geschwindigkeit, Fahrzeug]
func find_leader(v: VehicleAI) -> Array:
	var lst: Array = occ.get(v.seg, [])
	for e in lst:
		var w: VehicleAI = e[1]
		if w == v:
			continue
		var ws: float = e[0]
		if ws > v.s + 0.001 or (absf(ws - v.s) <= 0.001 and w.id < v.id):
			if v.siren and w.yielding and w.v < 1.5 and graph.seg_len[v.seg] - ws > 12.0:
				continue
			return [ws - w.length - v.s, w.v, w]
	var dist: float = graph.seg_len[v.seg] - v.s
	var sg := v.seg
	for depth in 3:
		var nx := v.planned_next(graph, sg)
		if nx < 0 or dist > VehicleAI.LOOKAHEAD:
			break
		var l2: Array = occ.get(nx, [])
		for e in l2:
			var w2: VehicleAI = e[1]
			if w2 == v:
				continue
			return [dist + float(e[0]) - w2.length, w2.v, w2]
		dist += graph.seg_len[nx]
		sg = nx
	return [INF, 0.0, null]


## Hindernisse und Fahrzeuge in Kreuzungen im Sichtkegel: [Luecke, Geschwindigkeit]
func cone_check(v: VehicleAI) -> Array:
	var best := INF
	var best_v := 0.0
	var h := v.heading
	var front := v.pos + h * (v.length * 0.5)
	for o in obstacles:
		var op: Vector2 = o.pos
		var rel := op - front
		if rel.length_squared() > 1600.0:
			continue
		var along := rel.dot(h)
		if along < -0.5:
			continue
		var r: float = o.r
		var latd := absf(rel.x * h.y - rel.y * h.x)
		# Kurven: Blick auf den Pfad voraus
		var path_lat := latd
		if not graph.is_lane(v.seg) or graph.seg_len[v.seg] - v.s < 15.0:
			var ahead := _path_ahead(v, maxf(along, 0.0))
			path_lat = minf(latd, ahead.distance_to(op))
		if path_lat > v.width * 0.5 + r + 0.4:
			continue
		var gap := along - r
		if gap < best:
			best = gap
			var ov: Vector2 = o.get("vel", Vector2.ZERO)
			best_v = maxf(0.0, ov.dot(h))
	# Fahrzeuge an Kreuzungen (Kreisel, zusammenlaufende Abbieger)
	var n := -1
	if not graph.is_lane(v.seg):
		n = graph.node[v.seg]
	elif v.committed and graph.seg_len[v.seg] - v.s < 20.0:
		n = graph.node[v.next_conn]
	if n >= 0 and v.ghost_time <= 0.0:
		for w in holders.get(n, []):
			if w == v or w.seg == v.seg:
				continue
			var hit := _cone_vehicle(v, w)
			if hit[0] < best:
				# gegenseitige Blockade: das Fahrzeug mit kleinerer ID faehrt
				if v.v < 0.2 and w.v < 0.2 and v.wait_time > 3.0 and w.wait_time > 3.0:
					var back := _cone_vehicle(w, v)
					if back[0] < INF and v.id < w.id:
						continue
				best = hit[0]
				best_v = hit[1]
	return [best, best_v]


func _cone_vehicle(v: VehicleAI, w: VehicleAI) -> Array:
	var h := v.heading
	var rel := w.pos - v.pos
	if rel.length_squared() > 900.0:
		return [INF, 0.0]
	var along := rel.dot(h)
	if along <= 0.0:
		return [INF, 0.0]
	var cr := absf(w.heading.x * h.y - w.heading.y * h.x)
	var dt_ := absf(w.heading.dot(h))
	var ext_along := dt_ * w.length * 0.5 + cr * w.width * 0.5
	var ext_lat := cr * w.length * 0.5 + dt_ * w.width * 0.5
	var latd := absf(rel.x * h.y - rel.y * h.x)
	var ahead := _path_ahead(v, maxf(along - v.length * 0.5, 0.0))
	var lat_path := minf(latd, ahead.distance_to(w.pos) if along < 25.0 else latd)
	if lat_path > v.width * 0.5 + ext_lat + 0.25:
		return [INF, 0.0]
	return [along - v.length * 0.5 - ext_along, maxf(0.0, w.v * w.heading.dot(h))]


## Punkt auf dem eigenen Pfad d Meter vor der Front.
func _path_ahead(v: VehicleAI, d: float) -> Vector2:
	var q := v.s + d
	var sg := v.seg
	for i in 3:
		var l: float = graph.seg_len[sg]
		if q <= l:
			return graph.pos_at(sg, q)
		var nx := v.planned_next(graph, sg)
		if nx < 0:
			return graph.pos_at(sg, l)
		q -= l
		sg = nx
	return graph.pos_at(sg, minf(q, graph.seg_len[sg]))


# ============================================================ Busse

func _stops_on_leg(lid: int, leg_i: int) -> Array:
	if not _bus_stop_cache.has(lid):
		var per := {}
		for st in transit.lines[lid].stops:
			var lg: Dictionary = transit.lines[lid].legs[st.leg]
			var s_stop: Dictionary = city.bus_stops[st.stop]
			if not per.has(st.leg):
				per[st.leg] = []
			per[st.leg].append({"stop": st.stop, "t": float(s_stop.t), "cycle_s": float(st.s), "edge": lg.edge})
		_bus_stop_cache[lid] = per
	return _bus_stop_cache[lid].get(leg_i, [])


## Naechste noch nicht bediente Haltestelle auf der aktuellen Spur:
## {stop, s (Ziel der Front), lat, cycle_s} oder {}.
func bus_stop_on_lane(v: VehicleAI) -> Dictionary:
	if v.line < 0 or not graph.is_lane(v.seg):
		return {}
	var e_id: int = graph.edge[v.seg]
	for st in _stops_on_leg(v.line, v.leg):
		if st.edge != e_id or st.stop == v.served_stop:
			continue
		var target := graph.lane_s_for_t(v.seg, st.t) + v.length * 0.5
		if target < v.s - 3.0:
			continue
		target = minf(target, graph.seg_len[v.seg] - 1.0)
		var e: Dictionary = city.edges[e_id]
		var outer: bool = graph.lane_idx[v.seg] == int(e.lanes) - 1
		var latv := 0.0
		if outer:
			latv = float(e.lane_w) * 0.5 + float(e.park_w) * 0.5 if float(e.park_w) > 0.0 else 0.45
		return {"stop": st.stop, "s": target, "lat": latv, "cycle_s": st.cycle_s}
	return {}


func bus_may_depart(v: VehicleAI, st: Dictionary) -> bool:
	if v.dwell < 5.0:
		return false
	if absf(v.lat) > 1.0 and lane_gap_behind(v) < 14.0:
		return false
	if v.dwell > 60.0:
		return true
	if world == null:
		return v.dwell > 8.0
	var bp := transit.bus_position(world, v.line, v.bus_index)
	var L: float = transit.lines[v.line].length
	var diff := wrapf(float(bp.s) - float(st.cycle_s), -L * 0.5, L * 0.5)
	if bp.at_stop == st.stop:
		return false
	return diff > -1.0


## Abweichung vom Fahrplan in Metern und daraus Wunschgeschwindigkeit.
func bus_cycle_s(v: VehicleAI) -> float:
	var ld: Dictionary = transit.lines[v.line]
	var legs: Array = ld.legs
	if graph.is_lane(v.seg):
		var lg: Dictionary = legs[v.leg]
		var t := graph.lane_t_for_s(v.seg, v.s - v.length * 0.5)
		var along: float = t if lg.fwd else lg.len - t
		return lg.s0 + clampf(along, 0.0, lg.len)
	return legs[(v.leg + 1) % legs.size()].s0


func _bus_schedule(v: VehicleAI) -> void:
	if world == null:
		v.v_factor = 1.0
		return
	var bp := transit.bus_position(world, v.line, v.bus_index)
	var L: float = transit.lines[v.line].length
	v.schedule_err = wrapf(float(bp.s) - bus_cycle_s(v), -L * 0.5, L * 0.5)
	v.v_factor = clampf(1.0 + v.schedule_err / 120.0, 0.55, 1.35)


# ============================================================ Einsatzfahrzeuge

func _clear_way(e: VehicleAI) -> void:
	var eid: int = graph.edge[e.seg] if graph.is_lane(e.seg) else graph.edge[graph.conn_out[e.seg]]
	var ed: Dictionary = city.edges[eid]
	var nl: int = int(ed.lanes)
	for f in [1, 0]:
		for li in nl:
			var lane: int = graph.lane_key[Vector3i(eid, f, li)]
			for entry in occ.get(lane, []):
				var w: VehicleAI = entry[1]
				if w == e or w.siren or w.role == "bus" and w.dwelling:
					continue
				var rel := w.pos - e.pos
				var along := rel.dot(e.heading)
				if along < 0.0 or along > 55.0:
					continue
				w.yielding = true
				w.meta["yield_t"] = 1.5
				var pull := 0.8 if float(ed.park_w) > 0.0 else 1.0
				if nl > 1 and li == 0:
					pull = -1.0
				w.lat_target = pull
	if graph.is_lane(e.seg):
		var li_e: int = graph.lane_idx[e.seg]
		var lw: float = ed.lane_w
		e.lat_target = (-lw * 0.55) if nl == 1 else ((lw * 0.5) if li_e == 0 else (-lw * 0.5))
	else:
		e.lat_target = 0.0


# ============================================================ Hilfen

## Seitlicher Versatz von der Spurmitte bis zum Fahrbahnrand (Parkstreifen).
func curb_lat(seg: int) -> float:
	var lane := seg if graph.is_lane(seg) else graph.conn_out[seg]
	var e: Dictionary = city.edges[graph.edge[lane]]
	var outer: int = int(e.lanes) - 1 - graph.lane_idx[lane]
	var base: float = outer * float(e.lane_w)
	if float(e.park_w) > 0.0:
		return base + float(e.lane_w) * 0.5 + float(e.park_w) * 0.5
	if float(e.bike_w) > 0.0:
		return base + float(e.lane_w) * 0.5 + 0.2
	return base + 0.6


## Freier Abstand hinter dem Heck auf der eigenen Spur.
func lane_gap_behind(v: VehicleAI) -> float:
	var best := INF
	for e in occ.get(v.seg, []):
		var w: VehicleAI = e[1]
		if w == v or float(e[0]) > v.s:
			continue
		best = minf(best, v.s - v.length - float(e[0]))
	# Fahrzeuge auf Abbiegern in diese Spur
	for w2 in into_lane.get(v.seg, []):
		if w2 != v and w2.seg != v.seg:
			best = minf(best, v.s - v.length)
	return best


## Fussgaenger meldet sich am Zebrastreifen an (Fahrzeuge warten).
func set_crosswalk_busy(cw: int, seconds := 1.0) -> void:
	crosswalk_busy[cw] = maxf(float(crosswalk_busy.get(cw, 0.0)), time + seconds)


## Ist der Zebrastreifen frei von Fahrzeugen (stehend oder festgelegt)?
func crosswalk_clear(cw: int) -> bool:
	var c: Dictionary = city.crosswalks[cw]
	var center: Vector2 = c.center
	var e: Dictionary = city.edges[c.edge]
	var along_dir := city.edge_dir(e)
	var half_len: float = float(c.length) * 0.5 + 0.5
	var half_w: float = float(c.width) * 0.5 + 1.2
	var n: int = c.node
	for w in holders.get(n, []):
		for hc in w.held:
			if graph.edge[graph.conn_in[hc]] == c.edge or graph.edge[hc] == c.edge:
				if w.pos.distance_to(center) < 22.0:
					return false
	for lane in graph.in_lanes(c.edge, n) + graph.out_lanes(c.edge, n):
		for entry in occ.get(lane, []):
			var w2: VehicleAI = entry[1]
			if _veh_in_rect(w2, center, along_dir, half_w, half_len):
				return false
	for w3 in holders.get(n, []):
		if _veh_in_rect(w3, center, along_dir, half_w, half_len):
			return false
	return true


func _veh_in_rect(w: VehicleAI, center: Vector2, along_dir: Vector2, half_w: float, half_len: float) -> bool:
	for f in [-0.5, 0.0, 0.5]:
		var p: Vector2 = w.pos + w.heading * (w.length * f) - center
		if absf(p.dot(along_dir)) < half_w and absf(p.x * along_dir.y - p.y * along_dir.x) < half_len:
			return true
	return false


func _resolve_stuck() -> void:
	for v in vehicles:
		if v.meta.has("blink_t"):
			v.meta["blink_t"] = float(v.meta["blink_t"]) - dt
			if v.meta["blink_t"] <= 0.0:
				v.meta.erase("blink_t")
				if v.blinker != 2 and graph.is_lane(v.seg):
					v.blinker = 0
		if v.wait_reason == "folgen":
			if v.leader != null:
				v.stuck_time = minf(v.stuck_time, v.leader.stuck_time)
			continue
		if v.stuck_time > STUCK_GHOST and v.ghost_time <= 0.0:
			v.ghost_time = GHOST_TIME
			v.stuck_time = 0.0
			stats.ghosts += 1


## Fahrzeuge in einem Umkreis (fuer Darstellung/Verwaltung).
func vehicles_near(p: Vector2, radius: float) -> Array:
	var out := []
	var r2 := radius * radius
	for v in vehicles:
		if v.pos.distance_squared_to(p) <= r2:
			out.append(v)
	return out
