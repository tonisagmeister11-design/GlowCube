class_name VehicleAI
extends RefCounted
## Fahrlogik eines KI-Fahrzeugs (ohne Szenenbaum, laeuft headless).
##
## Ein Fahrzeug bewegt sich kinematisch auf den Segmenten des LaneGraph
## (Fahrstreifen und Abbieger). `s` ist die Position der Fahrzeugfront auf
## dem aktuellen Segment. Die Beschleunigung folgt dem Intelligent Driver
## Model (IDM) mit mehreren Einschraenkungen, von denen jeweils die
## strengste gilt: Vordermann (auch ueber Segmentgrenzen und Knoten),
## Haltelinie (Ampel, Stoppschild, Vorfahrt, Kreisverkehr, Zebrastreifen,
## Platz hinter der Kreuzung), Kurvengeschwindigkeit, Hindernisse (Spieler,
## Fussgaenger) und Haltestellen.
##
## Kreuzungen werden ueber Reservierungen von Abbiegern geregelt: Wer in
## einen Abbieger einfahren will, braucht freie Konfliktabbieger und
## genuegend Platz auf der Zielspur ("Kreuzung nicht blockieren"). Ab dem
## Punkt, an dem nicht mehr komfortabel gehalten werden kann, ist das
## Fahrzeug festgelegt (committed) und haelt die Reservierung bis das Heck
## die Kreuzung verlassen hat.

const S0 := 2.0              # Mindestabstand im Stand
const T_HEADWAY := 1.3       # Zeitluecke
const B_MAX := 8.0           # maximale Verzoegerung (Notbremsung)
const CTRL_RANGE := 45.0     # ab dieser Entfernung wird die Kreuzung geprueft
const LOOKAHEAD := 70.0

var id := 0
var type := "sedan"          # Bauform (VehicleCatalog.TYPES)
var kind := "car"            # Regelgruppe
var role := "ambient"        # ambient | bus | emergency
var length := 4.6
var width := 1.8
var a_max := 2.5
var b_comf := 3.5
var v_max := 40.0
var v_factor := 1.0          # individuelle Wunschgeschwindigkeit
var color := Color.WHITE
var variant := 0             # Zufallswert fuer Darstellung (Aufschrift usw.)

# Zustand auf dem Graphen
var seg := -1
var s := 0.0
var prev_seg := -1
var prev2_seg := -1
var v := 0.0
var acc := 0.0
var pos := Vector2.ZERO      # Fahrzeugmitte
var heading := Vector2(0, -1)
var lat := 0.0               # seitlicher Versatz (Spurwechsel, Rechts ranfahren)
var lat_target := 0.0

# Planung
var route := PackedInt64Array()
var route_i := 0             # Index des Endknotens der aktuellen Spur in route
var dest_node := -1
var next_conn := -1
var want_lane := -1          # gewuenschte Spur fuer den naechsten Abbieger
var committed := false
var held: Array = []         # gehaltene Abbieger-Reservierungen

# Kreuzungszustand
var stop_done := false
var stop_timer := 0.0
var arrive_time := 0.0
var wait_time := 0.0         # Wartezeit an der aktuellen Haltelinie
var wait_reason := ""
var stuck_time := 0.0        # Zeit im Stillstand ohne erkennbaren Grund
var ghost_time := 0.0        # Notfall: Konflikte ignorieren (Verklemmung loesen)

# Anzeige
var blinker := 0             # -1 links, 0 aus, 1 rechts, 2 Warnblinker
var braking := false
var siren := false
var doors_open := false
var yielding := false        # macht Platz fuer Einsatzfahrzeug

# Bus
var line := -1
var bus_index := -1
var leg := 0                 # Leg-Index im Umlauf der Linie
var dwell := 0.0
var dwelling := false
var served_stop := -1        # zuletzt bediente Haltestelle (Stop-ID)
var stop_log: Array = []     # bediente Haltestellen (Tests)
var schedule_err := 0.0      # Abstand zum Fahrplan in Metern (+ = zu spaet)

# Einsatz / Ziel
var target_lane := -1
var target_s := -1.0
var arrived := false
var pull_over_time := 0.0    # Zeit am Fahrbahnrand (Lieferwagen, Taxi)
var pulled_over := false

# Statistik
var visits: Dictionary = {}  # Knoten -> Anzahl (Schleifenerkennung)
var trips := 0
var age := 0.0
var meta: Dictionary = {}


func setup_type(type_id: String, rng: RandomNumberGenerator) -> void:
	type = type_id
	var t := VehicleCatalog.type_info(type_id)
	kind = t.kind
	length = t.length
	width = t.width
	a_max = t.accel * rng.randf_range(0.85, 1.15)
	b_comf = t.decel
	v_max = t.vmax
	v_factor = rng.randf_range(0.88, 1.08)
	if kind == "bike":
		v_factor = rng.randf_range(0.7, 1.0)
	color = VehicleCatalog.paint_for(type_id, rng)
	variant = rng.randi()


# ============================================================ Abfragen

func is_on_lane(g: LaneGraph) -> bool:
	return seg >= 0 and g.kind[seg] == LaneGraph.KIND_LANE


func rear_s() -> float:
	return s - length


## Desired speed on the current segment (Wunschgeschwindigkeit).
func desired_speed(g: LaneGraph) -> float:
	var vs: float = g.speed[seg] * v_factor
	if kind == "bike":
		vs = minf(vs, v_max * v_factor)
	if siren:
		vs *= 1.3
	return minf(vs, v_max)


## Fahrzeugmitte und Richtung aus Vorder- und Hinterachse (Heck folgt der
## Spur, dadurch schwenken lange Fahrzeuge in Kurven realistisch aus).
func update_pose(g: LaneGraph) -> void:
	var front := _path_point(g, s - length * 0.18)
	var back := _path_point(g, s - length * 0.82)
	var d := front - back
	if d.length_squared() > 1e-6:
		heading = d.normalized()
	pos = (front + back) * 0.5
	if absf(lat) > 0.001:
		pos += CityModel.right_of(heading) * lat


## Punkt bei Bogenlaenge q relativ zum aktuellen Segment (q < 0 liegt auf
## den vorherigen Segmenten).
func _path_point(g: LaneGraph, q: float) -> Vector2:
	if q >= 0.0:
		return g.pos_at(seg, q)
	if prev_seg < 0:
		return g.pos_at(seg, 0.0) + g.dir_at(seg, 0.0) * q
	var lp: float = g.seg_len[prev_seg]
	var q2 := lp + q
	if q2 >= 0.0 or prev2_seg < 0:
		if q2 < 0.0:
			return g.pos_at(prev_seg, 0.0) + g.dir_at(prev_seg, 0.0) * q2
		return g.pos_at(prev_seg, q2)
	var lp2: float = g.seg_len[prev2_seg]
	return g.pos_at(prev2_seg, maxf(lp2 + q2, 0.0))


## Naechstes Segment nach `sg` laut Plan (-1 = unbekannt).
func planned_next(g: LaneGraph, sg: int) -> int:
	if sg == seg:
		if g.kind[sg] == LaneGraph.KIND_LANE:
			return next_conn
		return g.conn_out[sg]
	if g.kind[sg] == LaneGraph.KIND_CONN:
		return g.conn_out[sg]
	return -1


# ============================================================ IDM

func _idm(v0: float, gap: float, v_lead: float, s0 := S0) -> float:
	var vv := maxf(v, 0.0)
	var free := 1.0 - pow(vv / maxf(v0, 0.1), 4.0)
	var dv := vv - v_lead
	var s_star := s0 + maxf(0.0, vv * T_HEADWAY + vv * dv / (2.0 * sqrt(a_max * b_comf)))
	var g := maxf(gap, 0.05)
	return a_max * (free - (s_star / g) * (s_star / g))


func _idm_free(v0: float) -> float:
	var vv := maxf(v, 0.0)
	if vv > v0:
		return -b_comf * minf((vv - v0) / maxf(v0, 1.0) * 2.0 + 0.3, 1.0)
	return a_max * (1.0 - pow(vv / maxf(v0, 0.1), 4.0))


# ============================================================ Schritt

## Berechnet die Beschleunigung fuer diesen Schritt (Zustand anderer
## Fahrzeuge wird nur gelesen; Reservierungen werden sofort gebucht).
func think(sim, dt: float) -> void:
	var g: LaneGraph = sim.graph
	age += dt
	if ghost_time > 0.0:
		ghost_time -= dt
	var v0 := desired_speed(g)
	var a := 1e9
	var reason := ""
	var on_lane := is_on_lane(g)
	var remaining: float = g.seg_len[seg] - s
	# Kurve / Abbieger voraus: rechtzeitig verlangsamen
	var nx := planned_next(g, seg)
	if nx >= 0:
		var vc: float = g.speed[nx] * (1.0 if kind != "bike" else 0.8)
		v0 = minf(v0, sqrt(vc * vc + 2.0 * b_comf * 0.8 * maxf(remaining, 0.0)))
		if not on_lane:
			pass
	elif on_lane:
		# ohne Plan an der Haltelinie halten
		a = minf(a, _idm(v0, remaining, 0.0, 0.4))
	if siren and not on_lane:
		v0 = minf(v0, 7.0)
	# Einsatzfahrzeug: Platz machen
	if yielding:
		v0 = minf(v0, 1.5)
	a = minf(a, _idm_free(v0))
	# Vordermann
	var ld: Array = sim.find_leader(self)
	if ld[0] < LOOKAHEAD:
		var la := _idm(v0, ld[0], ld[1])
		if la < a:
			a = la
			reason = "folgen"
	# Kreuzung / Haltelinie
	if on_lane:
		if role == "bus":
			var ab := _bus_stop_control(sim, v0)
			if ab < a:
				a = ab
				reason = "haltestelle"
		if target_lane == seg and target_s >= 0.0:
			var at := _target_control(sim, v0)
			if at < a:
				a = at
				reason = "ziel"
		if pulled_over or pull_over_time > 0.0:
			var ap := _pull_over_control(sim, v0, dt)
			if ap < a:
				a = ap
				reason = "parken"
		if remaining < maxf(CTRL_RANGE, v * v / (2.0 * b_comf) + 15.0):
			if not committed and next_conn >= 0:
				var res: String = sim.may_enter(self, next_conn, remaining)
				if res == "":
					var commit_d := v * v / (2.0 * b_comf) + 4.0 + v * 0.5
					if remaining <= commit_d:
						sim.reserve(self, next_conn)
						committed = true
				else:
					var stop_a := _idm(v0, remaining - 0.3, 0.0, 0.3)
					if stop_a < a:
						a = stop_a
						reason = res
			elif next_conn < 0:
				reason = "plan"
	# Hindernisse (Spieler, Fussgaenger) und Knotenverkehr im Sichtkegel
	var ob: Array = sim.cone_check(self)
	if ob[0] < 40.0:
		var ao := _idm(v0, ob[0], ob[1], 1.5)
		if ao < a:
			a = ao
			reason = "hindernis"
	acc = clampf(a, -B_MAX, a_max)
	braking = acc < -0.6 or (v < 0.2 and reason != "")
	wait_reason = reason
	# Warte- und Stillstandszeiten
	if v < 0.3:
		wait_time += dt
		var excused := reason == "rot" or reason == "haltestelle" or reason == "parken" or reason == "ziel" \
			or (role == "emergency" and arrived)
		if not excused:
			stuck_time += dt
	else:
		wait_time = 0.0 if v > 2.0 else wait_time
		stuck_time = maxf(0.0, stuck_time - dt * 4.0)


## Integration (nach think() aller Fahrzeuge).
func move(sim, dt: float) -> void:
	var g: LaneGraph = sim.graph
	v = maxf(0.0, v + acc * dt)
	if v < 0.02 and acc <= 0.0:
		v = 0.0
	s += v * dt
	# Segmentwechsel
	var guard := 0
	while s > g.seg_len[seg] and guard < 4:
		guard += 1
		if g.kind[seg] == LaneGraph.KIND_LANE:
			if not committed or next_conn < 0:
				s = g.seg_len[seg]
				v = 0.0
				break
			var over: float = s - g.seg_len[seg]
			_enter_segment(sim, next_conn, over)
			sim.on_enter_connector(self)
		else:
			var over2: float = s - g.seg_len[seg]
			_enter_segment(sim, g.conn_out[seg], over2)
			sim.on_enter_lane(self)
	# Reservierungen freigeben, sobald das Heck die Kreuzung verlassen hat
	if not held.is_empty():
		for c in held.duplicate():
			if c == seg or c == next_conn:
				continue
			if g.kind[seg] == LaneGraph.KIND_LANE and (s - length > 1.0 or prev_seg != c):
				sim.release(self, c)
	# seitlicher Versatz gleitet zum Ziel
	if absf(lat - lat_target) > 0.001:
		lat = move_toward(lat, lat_target, dt * 1.6)
	update_pose(g)


func _enter_segment(sim, nseg: int, over: float) -> void:
	prev2_seg = prev_seg
	prev_seg = seg
	seg = nseg
	s = over


# ============================================================ Haltestellen / Ziele

func _bus_stop_control(sim, v0: float) -> float:
	var st: Dictionary = sim.bus_stop_on_lane(self)
	if st.is_empty():
		dwelling = false
		doors_open = false
		return 1e9
	var stop_front: float = st.s
	var gap := stop_front - s
	if dwelling:
		dwell += sim.dt
		doors_open = dwell > 1.0
		if sim.bus_may_depart(self, st):
			dwelling = false
			doors_open = false
			served_stop = st.stop
			dwell = 0.0
			lat_target = 0.0
			return 1e9
		return -B_MAX
	if gap < 0.8 and v < 0.4:
		dwelling = true
		dwell = 0.0
		stop_log.append(st.stop)
		sim.on_bus_arrived(self, st.stop)
		return -B_MAX
	if gap < 60.0:
		lat_target = st.lat
	return _idm(v0, gap + 0.3, 0.0, 0.3)


func _target_control(sim, v0: float) -> float:
	var gap := target_s - s
	if arrived:
		return -B_MAX
	if gap < 0.8 and v < 0.5:
		arrived = true
		sim.on_target_reached(self)
		return -B_MAX
	if gap < 40.0:
		lat_target = sim.curb_lat(seg) if kind != "bike" else lat_target
	return _idm(v0, gap + 0.3, 0.0, 0.3)


func _pull_over_control(sim, v0: float, dt: float) -> float:
	if pulled_over:
		pull_over_time -= dt
		blinker = 2
		if pull_over_time <= 0.0 and sim.lane_gap_behind(self) > 18.0:
			pulled_over = false
			pull_over_time = 0.0
			lat_target = 0.0
			blinker = 0
			return 1e9
		return -B_MAX
	var gap: float = meta.get("pull_s", s + 30.0) - s
	if gap < 0.8 and v < 0.5:
		pulled_over = true
		return -B_MAX
	if gap < 35.0:
		lat_target = sim.curb_lat(seg)
		blinker = 1
	return _idm(v0, gap + 0.3, 0.0, 0.3)


func visited(node_id: int) -> void:
	visits[node_id] = int(visits.get(node_id, 0)) + 1


func to_debug() -> String:
	return "#%d %s seg=%d s=%.1f v=%.1f %s wait=%.1f stuck=%.1f" % [id, type, seg, s, v, wait_reason, wait_time, stuck_time]
