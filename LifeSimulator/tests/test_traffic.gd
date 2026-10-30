extends RefCounted
## Tests fuer Verkehr, Busse und Fussgaengerwege (headless, ohne Rendering).
## Simuliert KI-Fahrzeuge mit festem Zeitschritt auf einer generierten Stadt
## und prueft Fahrbahntreue, Ampeln, Abbiegespuren, Kollisionen,
## Verklemmungen, Endlosschleifen, Busfahrplan und Gehwegrouten.

var runner

const SEED := 4242
const DT := 0.1

var _city: CityModel


func _get_city() -> CityModel:
	if _city == null:
		_city = CityGenerator.generate(SEED, {"size": "medium"})
	return _city


func _make_world(m: CityModel) -> WorldState:
	var w := WorldState.new()
	w.city = m
	w.seed = SEED
	w.time_minutes = 8.0 * 60.0
	w.ledger = Ledger.new()
	return w


# ============================================================ Hilfen

## Liegt p auf der Fahrbahn (Kante, Kreuzungsflaeche, Kreisel)?
func _on_road(m: CityModel, p: Vector2, tol := 0.6) -> bool:
	var k := m.chunk_key(p)
	for dx in [-1, 0, 1]:
		for dz in [-1, 0, 1]:
			var c = m.chunks.get(k + Vector2i(dx, dz))
			if c == null:
				continue
			for eid in c.edges:
				if m.edge_road_rect(m.edges[eid]).grow(tol).has_point(p):
					return true
	var nid := m.nearest_node(p)
	var n: Dictionary = m.nodes[nid]
	var box := m.node_box(nid)
	if Rect2(n.pos - box, box * 2.0).grow(tol).has_point(p):
		return true
	if n.kind == "roundabout" and p.distance_to(n.pos) <= float(n.roundabout.r_outer) + tol:
		return true
	return false


func _corners(v: VehicleAI, shrink: float) -> PackedVector2Array:
	var h := v.heading
	var r := CityModel.right_of(h)
	var hl := v.length * 0.5 - shrink
	var hw := v.width * 0.5 - shrink
	return PackedVector2Array([v.pos + h * hl + r * hw, v.pos + h * hl - r * hw,
		v.pos - h * hl - r * hw, v.pos - h * hl + r * hw])


func _obb_overlap(a: VehicleAI, b: VehicleAI, shrink := 0.25) -> bool:
	var ca := _corners(a, shrink)
	var cb := _corners(b, shrink)
	for axis in [a.heading, CityModel.right_of(a.heading), b.heading, CityModel.right_of(b.heading)]:
		var amin := INF
		var amax := -INF
		var bmin := INF
		var bmax := -INF
		for p in ca:
			var d: float = p.dot(axis)
			amin = minf(amin, d)
			amax = maxf(amax, d)
		for p in cb:
			var d2: float = p.dot(axis)
			bmin = minf(bmin, d2)
			bmax = maxf(bmax, d2)
		if amax < bmin or bmax < amin:
			return false
	return true


func _fill(sim: TrafficSim, rng: RandomNumberGenerator, count: int) -> void:
	var lanes := []
	for i in sim.graph.segment_count():
		if sim.graph.is_lane(i):
			lanes.append(i)
	var tries := 0
	var target := sim.vehicles.size() + count
	while sim.vehicles.size() < target and tries < 20000:
		tries += 1
		var l: int = lanes[rng.randi() % lanes.size()]
		var typ := VehicleCatalog.random_ai_type(rng)
		var len_v: float = VehicleCatalog.type_info(typ).length
		var s := rng.randf_range(len_v + 1.0, sim.graph.seg_len[l] - 1.0)
		if sim.can_place(l, s, len_v, 8.0):
			sim.spawn(typ, l, s)


# ============================================================ Spurgraph

func test_lane_graph_geometry() -> void:
	var m := _get_city()
	var g := LaneGraph.build(m)
	var bad_end := 0
	var no_conn := 0
	var wrong_lane := 0
	var off_road := 0
	var in_building := 0
	var turns := {0: 0, 1: 0, 2: 0, 3: 0}
	for id in g.segment_count():
		if g.is_lane(id):
			if (g.next[id] as PackedInt32Array).is_empty():
				no_conn += 1
			continue
		var p: PackedVector2Array = g.pts[id]
		var lin: int = g.conn_in[id]
		var lout: int = g.conn_out[id]
		var pin: PackedVector2Array = g.pts[lin]
		var pout: PackedVector2Array = g.pts[lout]
		if p[0].distance_to(pin[pin.size() - 1]) > 0.01 or p[p.size() - 1].distance_to(pout[0]) > 0.01:
			bad_end += 1
		var t: int = g.turn[id]
		turns[t] += 1
		var nd: Dictionary = m.nodes[g.node[id]]
		var e_in: Dictionary = m.edges[g.edge[lin]]
		if nd.kind != "roundabout" and nd.edges.size() > 2:
			if t == LaneGraph.TURN_RIGHT and g.lane_idx[lin] != int(e_in.lanes) - 1:
				wrong_lane += 1
			if t == LaneGraph.TURN_LEFT and g.lane_idx[lin] != 0:
				wrong_lane += 1
			if t == LaneGraph.TURN_RIGHT and g.lane_idx[lout] != int(m.edges[g.edge[lout]].lanes) - 1:
				wrong_lane += 1
		# Abbieger bleiben auf der Fahrbahn und ausserhalb von Gebaeuden
		var sm := 0.0
		while sm <= g.seg_len[id]:
			var q := g.pos_at(id, sm)
			if not _on_road(m, q, 0.3):
				off_road += 1
			if m.building_at(q) >= 0:
				in_building += 1
			sm += 1.0
		# Ein- und Ausfahrtsrichtung passen zu den Spuren
		if g.dir_at(id, 0.01).dot(g.lane_dir(lin)) < 0.8 or g.dir_at(id, g.seg_len[id] - 0.01).dot(g.lane_dir(lout)) < 0.8:
			bad_end += 1
	print("    Segmente %d, Abbieger: gerade %d, rechts %d, links %d, wenden %d" % [g.segment_count(), turns[0], turns[1], turns[2], turns[3]])
	runner.check_eq(no_conn, 0, "Spuren ohne Abbieger")
	runner.check_eq(bad_end, 0, "Abbieger mit falschen Anschluessen")
	runner.check_eq(wrong_lane, 0, "Abbieger aus falscher Spur")
	runner.check_eq(off_road, 0, "Abbiegerpunkte neben der Fahrbahn")
	runner.check_eq(in_building, 0, "Abbiegerpunkte in Gebaeuden")
	runner.check(turns[1] > 100 and turns[2] > 100, "Zu wenige Abbieger")


func test_conflicts_symmetric() -> void:
	var m := _get_city()
	var g := LaneGraph.build(m)
	var asym := 0
	var crossing_found := 0
	for n in m.nodes:
		if n.kind != "signal":
			continue
		for c in g.node_conns[n.id]:
			for c2 in g.conflicts_of(c):
				if not g.is_conflict(c2, c):
					asym += 1
				crossing_found += 1
	runner.check_eq(asym, 0, "Konflikte nicht symmetrisch")
	runner.check(crossing_found > 0, "Keine Konflikte gefunden")
	# Links abbiegen kreuzt den Gegenverkehr geradeaus
	var checked := 0
	for n in m.nodes:
		if n.kind != "signal" or n.edges.size() != 4 or checked > 10:
			continue
		for c in g.node_conns[n.id]:
			if g.turn[c] != LaneGraph.TURN_LEFT:
				continue
			var opp := -1
			var hin := g.lane_dir(g.conn_in[c])
			for c2 in g.node_conns[n.id]:
				if g.turn[c2] == LaneGraph.TURN_STRAIGHT and g.lane_dir(g.conn_in[c2]).dot(hin) < -0.9:
					opp = c2
			if opp >= 0:
				runner.check(g.is_conflict(c, opp), "Linksabbieger kreuzt Gegenverkehr nicht (Knoten %d)" % n.id)
				checked += 1
	runner.check(checked > 0, "Keine Linksabbieger geprueft")


# ============================================================ Simulation

func test_traffic_simulation() -> void:
	var m := _get_city()
	var w := _make_world(m)
	var sim := TrafficSim.create(m, w, SEED)
	var rng := RandomNumberGenerator.new()
	rng.seed = 99
	for lid in sim.transit.line_count():
		for i in sim.transit.bus_count(w, lid):
			sim.spawn_bus(lid, i)
	var n_bus := sim.vehicles.size()
	_fill(sim, rng, 180)
	var n_total := sim.vehicles.size()
	runner.check(n_total - n_bus >= 150, "Zu wenige Fahrzeuge erzeugt (%d)" % (n_total - n_bus))
	var red_wrong := 0
	var entries_on_red := []
	sim.on_connector_entered = func(v: VehicleAI, _c: int, _st: String) -> void:
		var cs: String = v.meta.get("commit_state", "green")
		if cs == "red" and not v.siren and v.ghost_time <= 0.0:
			entries_on_red.append(v.id)
	var steps := int(240.0 / DT)
	var off_road := 0
	var in_building := 0
	var collisions := 0
	var collision_pairs := {}
	var max_conn_time := 0.0
	var conn_time := {}
	var red_waits_checked := 0
	var red_wait_bad := 0
	var max_stuck := 0.0
	var loops := 0
	var t0 := Time.get_ticks_msec()
	for step in steps:
		w.time_minutes += DT
		sim.step(DT, step * DT)
		for v in sim.vehicles:
			if not sim.graph.is_lane(v.seg):
				conn_time[v.id] = float(conn_time.get(v.id, 0.0)) + DT
				max_conn_time = maxf(max_conn_time, conn_time[v.id])
			else:
				conn_time[v.id] = 0.0
			max_stuck = maxf(max_stuck, v.stuck_time)
		if step % 10 != 0:
			continue
		var grid := {}
		for v in sim.vehicles:
			if not _on_road(m, v.pos, 0.8):
				off_road += 1
				if off_road < 4:
					print("    neben Fahrbahn: ", v.to_debug(), " pos ", v.pos)
			if m.building_at(v.pos) >= 0 or m.building_at(v.pos + v.heading * v.length * 0.5) >= 0:
				in_building += 1
			var k := Vector2i(floori(v.pos.x / 15.0), floori(v.pos.y / 15.0))
			if not grid.has(k):
				grid[k] = []
			grid[k].append(v)
			# Wartende an roter Ampel stehen vor der Haltelinie
			if v.wait_reason == "rot" and v.v < 0.1 and sim.graph.is_lane(v.seg):
				red_waits_checked += 1
				if v.s > sim.graph.seg_len[v.seg] + 0.01:
					red_wait_bad += 1
		for k in grid:
			var lst: Array = grid[k]
			for dx in [-1, 0, 1]:
				for dz in [-1, 0, 1]:
					var o = grid.get(k + Vector2i(dx, dz))
					if o == null:
						continue
					for a in lst:
						for b in o:
							if a.id >= b.id or a.ghost_time > 0.0 or b.ghost_time > 0.0:
								continue
							if absf(a.lat) > 1.5 or absf(b.lat) > 1.5:
								continue
							if a.pos.distance_to(b.pos) < (a.length + b.length) * 0.5 and _obb_overlap(a, b):
								var key := "%d:%d" % [a.id, b.id]
								if not collision_pairs.has(key):
									collision_pairs[key] = true
									collisions += 1
									if collisions < 6:
										print("    Kollision: ", a.to_debug(), " | ", b.to_debug())
	var ms := Time.get_ticks_msec() - t0
	for v in sim.vehicles:
		if v.role == "ambient":
			for nid in v.visits:
				if int(v.visits[nid]) > 4:
					loops += 1
					break
	var turns_l := 0
	print("    %d Fahrzeuge (%d Busse), %d s simuliert in %d ms (%.1f us je Fahrzeugschritt)" % [
		n_total, n_bus, int(steps * DT), ms, ms * 1000.0 / float(steps * n_total)])
	print("    Statistik: ", sim.stats, " max. Stillstand %.1f s, max. in Kreuzung %.1f s, rote Wartende geprueft %d" % [
		max_stuck, max_conn_time, red_waits_checked])
	runner.check_eq(off_road, 0, "Fahrzeugpositionen neben der Fahrbahn")
	runner.check_eq(in_building, 0, "Fahrzeuge in Gebaeuden")
	runner.check_eq(entries_on_red.size(), 0, "Einfahrten bei Rot")
	runner.check_eq(red_wait_bad, 0, "Wartende hinter der Haltelinie")
	runner.check(red_waits_checked > 50, "Kaum Fahrzeuge an roten Ampeln beobachtet")
	runner.check_eq(collisions, 0, "Kollisionen zwischen Fahrzeugen")
	runner.check(max_stuck < 60.0, "Fahrzeug dauerhaft blockiert (%.1f s)" % max_stuck)
	runner.check(sim.stats.ghosts <= int(n_total * 0.03), "Zu viele Notaufloesungen (%d)" % sim.stats.ghosts)
	runner.check(max_conn_time < 40.0, "Fahrzeug blockiert eine Kreuzung (%.1f s)" % max_conn_time)
	runner.check_eq(loops, 0, "Fahrzeuge in Endlosschleifen")
	runner.check(sim.stats.trips > 20, "Kaum Fahrten beendet (%d)" % sim.stats.trips)
	runner.check(sim.stats.entries > 2000, "Zu wenig Kreuzungsverkehr (%d)" % sim.stats.entries)
	# Busse bedienen ihre Haltestellen in Reihenfolge, ohne Luecken
	var bus_ok := 0
	var bus_bad := 0
	for v in sim.vehicles:
		if v.role != "bus" or v.stop_log.size() < 2:
			continue
		var seq := []
		for st in sim.transit.lines[v.line].stops:
			seq.append(st.stop)
		var idx: int = seq.find(v.stop_log[0])
		var good := idx >= 0
		for k in range(1, v.stop_log.size()):
			idx = (idx + 1) % seq.size()
			if seq[idx] != v.stop_log[k]:
				good = false
		if good:
			bus_ok += 1
		else:
			bus_bad += 1
			if bus_bad < 4:
				print("    Bus Linie %d: %s, Soll %s" % [v.line, str(v.stop_log), str(seq)])
	print("    Busse mit geprueften Halten: %d" % bus_ok)
	runner.check_eq(bus_bad, 0, "Busse ueberspringen Haltestellen")
	runner.check(bus_ok > n_bus / 2, "Zu wenige Busse haben mehrere Haltestellen bedient")
	# Busse bleiben nahe am Fahrplan
	var late := 0
	for v in sim.vehicles:
		if v.role == "bus" and absf(v.schedule_err) > 250.0:
			late += 1
	runner.check(late <= n_bus / 10, "Busse weichen stark vom Fahrplan ab (%d)" % late)


func test_signal_stops_vehicle() -> void:
	# Ein einzelnes Fahrzeug faehrt auf eine rote Ampel zu und haelt an der Linie
	var m := _get_city()
	var sim := TrafficSim.create(m, null, SEED)
	var lane := -1
	for id in sim.graph.segment_count():
		if sim.graph.is_lane(id) and m.nodes[sim.graph.node[id]].kind == "signal" and sim.graph.seg_len[id] > 80.0:
			lane = id
			break
	runner.check(lane >= 0, "Keine Ampelzufahrt gefunden")
	if lane < 0:
		return
	var nid: int = sim.graph.node[lane]
	var axis: String = m.edges[sim.graph.edge[lane]].axis
	# Startzeit: Beginn der Rotphase dieser Achse
	var t := 0.0
	while m.signal_state(nid, axis, t) != "red" or m.signal_state(nid, axis, t - 0.5) == "red":
		t += 0.25
	var v := sim.spawn("sedan", lane, sim.graph.seg_len[lane] - 40.0)
	var stopped_at := -1.0
	var entered := []
	sim.on_connector_entered = func(_v: VehicleAI, _c: int, st: String) -> void:
		entered.append(st)
	for i in 400:
		sim.step(0.1, t + i * 0.1)
		if v.seg == lane and v.v < 0.05 and stopped_at < 0.0:
			stopped_at = sim.graph.seg_len[lane] - v.s
	runner.check(stopped_at >= 0.0 and stopped_at < 3.0, "Fahrzeug haelt nicht an der Haltelinie (Abstand %.2f)" % stopped_at)
	var first: String = entered[0] if not entered.is_empty() else ""
	runner.check(first == "green" or first == "yellow", "Fahrzeug faehrt nicht bei Gruen (%s)" % first)


func test_emergency_dispatch() -> void:
	var m := _get_city()
	var sim := TrafficSim.create(m, null, SEED)
	var rng := RandomNumberGenerator.new()
	rng.seed = 3
	_fill(sim, rng, 60)
	var start := sim.graph.nearest_lane(m.nodes[0].pos + Vector2(60, 0))
	var target: Vector2 = Vector2.ZERO
	for b in m.buildings:
		var dd: float = b.sidewalk_point.distance_to(m.nodes[0].pos)
		if dd > 500.0 and dd < 800.0:
			target = b.sidewalk_point
			break
	var v := sim.spawn_emergency("ambulance", start.seg, clampf(start.s, 8.0, sim.graph.seg_len[start.seg] - 1.0), target)
	var reached := false
	for i in 5000:
		sim.step(0.1, i * 0.1)
		if v.arrived:
			reached = true
			break
	runner.check(reached, "Rettungswagen erreicht das Ziel nicht")
	if reached:
		runner.check(v.pos.distance_to(target) < 40.0, "Rettungswagen haelt zu weit entfernt (%.1f m)" % v.pos.distance_to(target))


# ============================================================ Fahrplan

func test_transit_timetable() -> void:
	var m := _get_city()
	var w := _make_world(m)
	var tr := Transit.new(m)
	runner.check(tr.line_count() >= 3, "Zu wenige Buslinien")
	runner.check_near(tr.fare(w), 2.5, 0.001, "Standardfahrpreis")
	for lid in tr.line_count():
		var ld: Dictionary = tr.lines[lid]
		runner.check(ld.cycle > 0.0, "Umlaufzeit Linie %d" % lid)
		var prev := -1.0
		for st in ld.stops:
			runner.check(st.s > prev, "Haltestellen Linie %d nicht aufsteigend" % lid)
			prev = st.s
			var bs: Dictionary = m.bus_stops[st.stop]
			runner.check(lid in bs.lines, "Haltestelle %d kennt Linie %d nicht" % [st.stop, lid])
		var n := tr.bus_count(w, lid)
		var hw := tr.headway(w, lid)
		runner.check(n >= 2 and hw > 5.0, "Takt Linie %d" % lid)
		# Bus i+1 ist genau einen Takt hinter Bus i
		var t0: float = w.time_minutes
		var a := tr.bus_position(w, lid, 0)
		w.time_minutes = t0 + hw
		var b := tr.bus_position(w, lid, 1)
		w.time_minutes = t0
		runner.check(a.pos.distance_to(b.pos) < 0.5, "Linie %d: Busse nicht im Takt" % lid)
		# Position laeuft monoton und folgt der Strecke
		var last_s := -1.0
		var jumps := 0
		for k in 60:
			w.time_minutes = t0 + k * 0.5
			var p := tr.bus_position(w, lid, 0)
			if last_s >= 0.0 and p.s < last_s - 0.01 and last_s - p.s < ld.length * 0.5:
				jumps += 1
			last_s = p.s
		w.time_minutes = t0
		runner.check_eq(jumps, 0, "Linie %d: Bus faehrt rueckwaerts im Fahrplan" % lid)
		# Ankuenfte und Fahrten
		if ld.stops.size() >= 3:
			var s0: int = ld.stops[0].stop
			var s2: int = ld.stops[2].stop
			var arr := tr.next_arrivals(w, s0, 3)
			runner.check(not arr.is_empty(), "Keine Ankuenfte an Haltestelle %d" % s0)
			var lastm := -1.0
			for e in arr:
				runner.check(e.minutes >= lastm, "Ankuenfte nicht sortiert")
				lastm = e.minutes
			var mine := []
			for e2 in arr:
				if e2.line == lid:
					mine.append(e2)
			runner.check(not mine.is_empty() and mine[0].minutes <= hw + 0.01, "Wartezeit groesser als Takt")
			var r := tr.ride(w, lid, s0, s2)
			runner.check(r.ok and r.minutes > 0.0 and r.minutes < ld.cycle, "Fahrtzeit Linie %d" % lid)
			var expect: float = ld.stops[2].t_arr - ld.stops[0].t_arr - Transit.DWELL
			runner.check_near(r.minutes, expect, 0.01, "Fahrtzeit passt zum Fahrplan")
			# Der Bus steht laut Fahrplan an der Haltestelle, wenn er ankommt
			if not mine.is_empty():
				w.time_minutes = t0 + mine[0].minutes + 0.05
				var bp := tr.bus_position(w, lid, mine[0].bus)
				w.time_minutes = t0
				runner.check_eq(bp.at_stop, s0, "Bus steht bei Ankunft nicht an der Haltestelle")


# ============================================================ Fussgaenger

func test_ped_routes_on_sidewalks() -> void:
	var m := _get_city()
	var rng := RandomNumberGenerator.new()
	rng.seed = 11
	var bad := 0
	var crossings := 0
	var routes := 0
	for i in 400:
		if routes >= 70:
			break
		var a: Dictionary = m.buildings[rng.randi() % m.buildings.size()]
		var b: Dictionary = m.buildings[rng.randi() % m.buildings.size()]
		if a.sidewalk_point.distance_to(b.sidewalk_point) > 600.0:
			continue
		var route := PedPaths.route(m, a.sidewalk_point, b.sidewalk_point)
		routes += 1
		for k in range(route.points.size() - 1):
			var p0: Vector2 = route.points[k]
			var p1: Vector2 = route.points[k + 1]
			var cw: int = route.crossings[k]
			if cw != -1:
				crossings += 1
			var len_s := p0.distance_to(p1)
			var steps := maxi(1, int(len_s / 1.5))
			for j in range(steps + 1):
				var q := p0.lerp(p1, float(j) / steps)
				if not PedPaths.is_walkable(m, q, cw):
					bad += 1
					if bad < 5:
						print("    nicht begehbar: ", q, " Segment ", k, " Querung ", cw)
	print("    %d Routen, %d Querungen" % [routes, crossings])
	runner.check(routes > 40, "Zu wenige Routen geprueft")
	runner.check(crossings > 20, "Kaum Strassenquerungen")
	runner.check_eq(bad, 0, "Fussgaengerwege neben Gehweg/Zebrastreifen")


# ============================================================ Szene (ohne Rendering)

func test_managers_in_scene() -> void:
	var Fix = load("res://tests/sim_fixture.gd")
	var w: WorldState = Fix.medium()
	var root := Node3D.new()
	runner.add_child(root)
	var focus := Node3D.new()
	focus.add_to_group("player")
	root.add_child(focus)
	var sp: Vector2 = w.city.spawn_points[0].pos
	focus.position = Vector3(sp.x, 0, sp.y)
	var tm := TrafficManager.new()
	root.add_child(tm)
	tm.setup(w, null)
	var cm := CrowdManager.new()
	root.add_child(cm)
	cm.setup(w, null)
	for i in 120:
		tm._process(0.05)
		cm._process(0.05 if i % 10 else 0.7)
	runner.check(tm.sim.vehicles.size() >= 30, "Zu wenig Verkehr um den Spieler (%d)" % tm.sim.vehicles.size())
	runner.check(tm._parked_spots.size() > 10, "Keine geparkten Autos")
	runner.check(cm.visible_count() > 0, "Keine Fussgaenger sichtbar")
	print("    Szene: %d Fahrzeuge, %d Parkende, %d Fussgaenger, Schritt %.2f ms" % [tm.sim.vehicles.size(), tm._parked_spots.size(), cm.visible_count(), tm.stats.step_ms])
	for v in tm.sim.vehicles:
		runner.check(tm.visuals.has(v), "Fahrzeug ohne Darstellung")
		break
	var stop: BusStopPoint = tm.stops[0]
	runner.check(stop.get_prompt(null) != "", "Haltestelle ohne Hinweis")
	var target := Vector3(sp.x + 60.0, 0, sp.y)
	var police := tm.dispatch("police", target)
	runner.check(police != null, "Polizeiwagen konnte nicht entsendet werden")
	var vs: Array = w.player.vehicles
	vs.append({"good": "car_compact", "name": "Kompaktwagen", "bought_day": 0})
	tm.sync_owned_vehicles()
	var pv: PlayerVehicle = tm.owned.get(vs.size() - 1)
	runner.check(pv != null, "Eigenes Fahrzeug nicht erzeugt")
	if pv:
		runner.check(pv.can_enter(focus), "Eigenes Fahrzeug nicht zugaenglich")
		pv.enter(focus)
		for i in 60:
			pv.drive(1.0 / 60.0, 1.0, 0.0, false)
		runner.check(pv.speed > 3.0, "Spielerfahrzeug beschleunigt nicht (%.1f)" % pv.speed)
		var ex := pv.exit()
		runner.check(ex.distance_to(pv.global_position) < 15.0, "Ausstiegspunkt zu weit entfernt")
		runner.check(vs[vs.size() - 1].has("pos"), "Parkposition nicht gespeichert")
		vs.pop_back()
	for px in tm._proxies:
		if not px.spot.is_empty():
			runner.check(not px.can_enter(focus), "Geparktes Auto nicht abgeschlossen")
			break
	root.queue_free()


func test_call_owned_vehicle_next_to_player() -> void:
	# Ein gekauftes Auto laesst sich direkt neben den Spieler rufen. Mehrfaches
	# Rufen setzt dasselbe Auto um und erzeugt kein zweites.
	var Fix = load("res://tests/sim_fixture.gd")
	var w: WorldState = Fix.medium()
	var root := Node3D.new()
	runner.add_child(root)
	var focus := Node3D.new()
	focus.add_to_group("player")
	root.add_child(focus)
	var sp: Vector2 = w.city.spawn_points[0].pos
	focus.position = Vector3(sp.x, 0, sp.y)
	w.player.vehicles.append({"good": "car_small", "name": "Testauto", "bought_day": 0})
	var tm := TrafficManager.new()
	root.add_child(tm)
	tm.setup(w, null)
	# Spieler weit vom Zuhause weg stellen
	var far := Vector2(sp.x + 150.0, sp.y)
	var r: Dictionary = tm.call_owned_to(0, far)
	runner.check(bool(r.get("ok", false)), "Rufen gelingt: %s" % str(r))
	var p1: Vector2 = tm.owned_position(0)
	runner.check(p1.distance_to(far) < 30.0, "Auto steht neben dem Spieler: %.1f m" % p1.distance_to(far))
	var near2 := Vector2(sp.x - 120.0, sp.y + 40.0)
	tm.call_owned_to(0, near2)
	runner.check_eq(tm.owned.size(), 1, "Nur ein Auto, keine Kopie")
	runner.check(tm.owned_position(0).distance_to(near2) < 30.0, "Zweiter Ruf holt dasselbe Auto")
	runner.check(not bool(tm.call_owned_to(5, far).get("ok", true)), "Unbekanntes Fahrzeug wird abgelehnt")
	root.queue_free()
