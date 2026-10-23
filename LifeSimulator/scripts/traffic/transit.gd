class_name Transit
extends RefCounted
## Deterministischer Busfahrplan aus city.bus_lines (reine Daten, ohne
## Szenenbaum). Jede Linie wird als Umlauf (Ringlinie: einmal herum;
## sonst hin und zurueck) entlang der Kantenmittellinien beschrieben.
## Die Position jedes Busses ergibt sich allein aus der Spielzeit
## (world.time_minutes); sichtbare Busse in der Naehe des Spielers fahren
## diesem Fahrplan hinterher (TrafficManager), sodass Daten und Darstellung
## uebereinstimmen.
##
## Zeitmassstab: Fahrzeuge bewegen sich in Echtzeit, die Spieluhr laeuft
## bei Tempo 1 eine Spielminute pro Echtzeitsekunde. Der Fahrplan rechnet
## deshalb mit BUS_SPEED Metern pro Spielminute (entspricht m/s bei Tempo 1).

const BUS_SPEED := 7.5          # Meter je Spielminute (Fahrplan, inkl. Ampeln)
const DWELL := 0.35             # Haltezeit an Haltestellen (Spielminuten)
const LAYOVER := 1.0            # Wendezeit an Endhaltestellen
const BASE_HEADWAY := 30.0      # Takt in Spielminuten bei Standardbudget
const MIN_BUSES := 2
const DEFAULT_FARE := 2.50

var city: CityModel
## lid -> {legs: [{edge, fwd, a, b, len, s0}], length, stops: [{stop, s, leg, t_arr}],
##         keys: [[t, s, stop_index_or_-1]], cycle, offset}
var lines: Array = []


func _init(model: CityModel = null) -> void:
	if model != null:
		setup_city(model)


func setup_city(model: CityModel) -> void:
	city = model
	lines.clear()
	for l in city.bus_lines:
		lines.append(_build_line(l))


func _ensure(world) -> void:
	if city == null and world != null:
		setup_city(world.city)


func line_count() -> int:
	return lines.size()


func _build_line(l: Dictionary) -> Dictionary:
	var seq: Array = l.nodes.duplicate()
	if l.loop:
		seq.append(l.nodes[0])
	else:
		for i in range(l.nodes.size() - 2, -1, -1):
			seq.append(l.nodes[i])
	var legs := []
	var s0 := 0.0
	for i in range(seq.size() - 1):
		var a: int = seq[i]
		var b: int = seq[i + 1]
		var eid := city.edge_between(a, b)
		var e: Dictionary = city.edges[eid]
		legs.append({"edge": eid, "fwd": e.a == a, "a": a, "b": b, "len": float(e.length), "s0": s0})
		s0 += e.length
	var total := s0
	var half_leg: int = legs.size() if l.loop else l.nodes.size() - 1
	# Haltestellen in Fahrtreihenfolge
	var stops := []
	var all_ids: Array = l.stops.duplicate()
	if not l.loop:
		all_ids.append_array(l.stops_back)
	for sid in all_ids:
		var st: Dictionary = city.bus_stops[sid]
		var want_fwd: bool = int(st.side) > 0
		for li in legs.size():
			var lg: Dictionary = legs[li]
			if lg.edge != st.edge or lg.fwd != want_fwd:
				continue
			var along: float = st.t if lg.fwd else lg.len - st.t
			stops.append({"stop": sid, "s": lg.s0 + along, "leg": li, "t_arr": 0.0, "back": li >= half_leg})
			break
	stops.sort_custom(func(x, y): return x.s < y.s)
	# Stuetzpunkte (Zeit, Strecke) des Umlaufs
	var keys := []
	var t := 0.0
	var s := 0.0
	keys.append([0.0, 0.0, -1])
	var turn_s: float = legs[half_leg - 1].s0 + legs[half_leg - 1].len if not l.loop else -1.0
	var turned := false
	for k in stops.size():
		var st2: Dictionary = stops[k]
		if not turned and turn_s > 0.0 and st2.s > turn_s:
			t += (turn_s - s) / BUS_SPEED
			s = turn_s
			keys.append([t, s, -1])
			t += LAYOVER
			keys.append([t, s, -1])
			turned = true
		t += (st2.s - s) / BUS_SPEED
		s = st2.s
		st2.t_arr = t
		keys.append([t, s, k])
		t += DWELL
		keys.append([t, s, k])
	if not turned and turn_s > 0.0:
		t += (turn_s - s) / BUS_SPEED
		s = turn_s
		keys.append([t, s, -1])
		t += LAYOVER
		keys.append([t, s, -1])
	t += (total - s) / BUS_SPEED
	keys.append([t, total, -1])
	t += LAYOVER if not l.loop else LAYOVER * 0.5
	keys.append([t, total, -1])
	var offset := float(hash("transit:%d:%d" % [city.seed, l.id]) % 1000) / 1000.0
	return {"id": l.id, "legs": legs, "length": total, "stops": stops, "keys": keys,
		"cycle": t, "offset_frac": offset, "loop": l.loop, "half_leg": half_leg}


# ============================================================ Takt / Preis

func _gov_modifier(world, name: String, default := 1.0) -> float:
	if world == null:
		return default
	var gov = world.get("government")
	if gov != null and gov.has_method("modifier"):
		var v = gov.modifier(name)
		if v != null and float(v) > 0.0:
			return float(v)
	return default


## Fahrpreis in Euro (Staatsmodifikator `bus_fare`, sonst 2,50 €).
func fare(world) -> float:
	if world != null:
		var gov = world.get("government")
		if gov != null and gov.has_method("modifier"):
			var v = gov.modifier("bus_fare")
			if v != null and float(v) >= 0.0 and float(v) < 100.0:
				return snappedf(float(v), 0.01)
	return DEFAULT_FARE


## Anzahl Busse im Umlauf einer Linie (haengt vom Verkehrsbudget ab).
func bus_count(world, lid: int) -> int:
	_ensure(world)
	var ld: Dictionary = lines[lid]
	var freq := clampf(_gov_modifier(world, "bus_frequency"), 0.25, 4.0)
	var hw := BASE_HEADWAY / freq
	return maxi(MIN_BUSES, int(round(ld.cycle / hw)))


func headway(world, lid: int) -> float:
	_ensure(world)
	return lines[lid].cycle / float(bus_count(world, lid))


func cycle_minutes(lid: int) -> float:
	return lines[lid].cycle


## Zeit im Umlauf (0 .. cycle) eines Busses zur aktuellen Spielzeit.
func cycle_time(world, lid: int, bus_index: int, time_minutes := -1.0) -> float:
	_ensure(world)
	var ld: Dictionary = lines[lid]
	var now: float = time_minutes if time_minutes >= 0.0 else float(world.time_minutes)
	var hw := headway(world, lid)
	return fposmod(now - ld.offset_frac * hw - bus_index * hw, ld.cycle)


## Strecke (Meter im Umlauf) und Haltestellenzustand zu einer Umlaufzeit.
func _eval(ld: Dictionary, tc: float) -> Dictionary:
	var keys: Array = ld.keys
	var lo := 0
	var hi := keys.size() - 1
	while hi - lo > 1:
		var mid := (lo + hi) >> 1
		if keys[mid][0] <= tc:
			lo = mid
		else:
			hi = mid
	var k0: Array = keys[lo]
	var k1: Array = keys[hi]
	var span: float = k1[0] - k0[0]
	var f: float = 0.0 if span <= 1e-6 else clampf((tc - k0[0]) / span, 0.0, 1.0)
	var s: float = lerpf(k0[1], k1[1], f)
	var at_stop := -1
	if k0[2] >= 0 and k0[2] == k1[2]:
		at_stop = k0[2]
	var dwell_left := 0.0
	if at_stop >= 0:
		dwell_left = k1[0] - tc
	var next_k := 0
	for i in ld.stops.size():
		if ld.stops[i].s > s + 0.01 or (at_stop == i):
			next_k = i
			break
		next_k = i + 1
	if next_k >= ld.stops.size():
		next_k = 0
	return {"s": s, "at_stop_index": at_stop, "next_stop_index": next_k, "dwell_left": dwell_left,
		"moving": span > 1e-6 and k1[1] != k0[1]}


## Leg-Index und Position auf der Kante fuer eine Umlaufstrecke s.
func leg_at(lid: int, s: float) -> Dictionary:
	var legs: Array = lines[lid].legs
	var lo := 0
	var hi := legs.size() - 1
	while lo < hi:
		var mid := (lo + hi + 1) >> 1
		if legs[mid].s0 <= s:
			lo = mid
		else:
			hi = mid - 1
	var lg: Dictionary = legs[lo]
	var along := clampf(s - lg.s0, 0.0, lg.len)
	var t: float = along if lg.fwd else lg.len - along
	return {"leg": lo, "edge": lg.edge, "fwd": lg.fwd, "t": t, "along": along}


## Position eines Busses laut Fahrplan:
## {pos: Vector2, heading: Vector2, s, leg, edge, fwd, t, next_stop (Stop-ID),
##  at_stop (Stop-ID oder -1), dwell_left, moving}
func bus_position(world, lid: int, bus_index: int) -> Dictionary:
	_ensure(world)
	var ld: Dictionary = lines[lid]
	var ev := _eval(ld, cycle_time(world, lid, bus_index))
	var la := leg_at(lid, ev.s)
	var e: Dictionary = city.edges[la.edge]
	var lat := city.lane_offset(e, la.fwd, int(e.lanes) - 1)
	var pos := city.edge_point(e, la.t, lat)
	var dir := city.edge_dir(e) * (1.0 if la.fwd else -1.0)
	var stops: Array = ld.stops
	var nxt: int = stops[ev.next_stop_index].stop if not stops.is_empty() else -1
	var at: int = stops[ev.at_stop_index].stop if ev.at_stop_index >= 0 else -1
	return {"pos": pos, "heading": dir, "s": ev.s, "leg": la.leg, "edge": la.edge, "fwd": la.fwd,
		"t": la.t, "next_stop": nxt, "at_stop": at, "dwell_left": ev.dwell_left, "moving": ev.moving}


func _stop_entry(lid: int, stop_id: int) -> Dictionary:
	for st in lines[lid].stops:
		if st.stop == stop_id:
			return st
	return {}


## Linien, die eine Haltestelle bedienen (nur Fahrtrichtung dieser Seite).
func lines_at_stop(stop_id: int) -> Array:
	var out := []
	for ld in lines:
		if not _stop_entry(ld.id, stop_id).is_empty():
			out.append(ld.id)
	return out


## Naechste Ankuenfte an einer Haltestelle: [{line, number, name, color,
## minutes, bus, destination}] aufsteigend nach Minuten.
func next_arrivals(world, stop_id: int, count := 4) -> Array:
	_ensure(world)
	var out := []
	for lid in lines_at_stop(stop_id):
		var st := _stop_entry(lid, stop_id)
		var n := bus_count(world, lid)
		var cyc: float = lines[lid].cycle
		var per := []
		for i in n:
			var tc := cycle_time(world, lid, i)
			var wait := fposmod(st.t_arr - tc, cyc)
			# Bus steht gerade an der Haltestelle
			if tc >= st.t_arr and tc < st.t_arr + DWELL:
				wait = 0.0
			per.append([wait, i])
		per.sort_custom(func(a, b): return a[0] < b[0])
		var bl: Dictionary = city.bus_lines[lid]
		for k in mini(count, per.size()):
			out.append({"line": lid, "number": int(bl.number), "name": str(bl.name), "color": bl.color,
				"minutes": per[k][0], "bus": per[k][1], "destination": destination_name(lid, st.s)})
	out.sort_custom(func(a, b): return a.minutes < b.minutes)
	return out.slice(0, count)


## Fahrtzeit und Preis zwischen zwei Haltestellen einer Linie (in
## Fahrtrichtung, ggf. ueber die Endhaltestelle hinweg).
## {ok, minutes, fare, reason}
func ride(world, lid: int, from_stop: int, to_stop: int) -> Dictionary:
	_ensure(world)
	if lid < 0 or lid >= lines.size():
		return {"ok": false, "minutes": 0.0, "fare": 0.0, "reason": "Unbekannte Linie"}
	var a := _stop_entry(lid, from_stop)
	var b := _stop_entry(lid, to_stop)
	if a.is_empty() or b.is_empty() or from_stop == to_stop:
		return {"ok": false, "minutes": 0.0, "fare": 0.0, "reason": "Haltestelle wird von dieser Linie nicht bedient"}
	var cyc: float = lines[lid].cycle
	var minutes := fposmod(b.t_arr - (a.t_arr + DWELL), cyc)
	return {"ok": true, "minutes": minutes, "fare": fare(world), "reason": ""}


## Haltestellen einer Linie in Fahrtreihenfolge: [{stop, s, t_arr, name}]
func stop_sequence(lid: int) -> Array:
	var out := []
	for st in lines[lid].stops:
		out.append({"stop": st.stop, "s": st.s, "t_arr": st.t_arr, "name": city.bus_stops[st.stop].name})
	return out


## Ziele ab einer Haltestelle in Fahrtrichtung (fuer die Fahrtwahl):
## [{stop, name, minutes}] bis einschliesslich zur Rueckkehr vor die Starthaltestelle.
func destinations_from(world, lid: int, from_stop: int) -> Array:
	var out := []
	var seq := stop_sequence(lid)
	var idx := -1
	for i in seq.size():
		if seq[i].stop == from_stop:
			idx = i
	if idx < 0:
		return out
	for k in range(1, seq.size()):
		var st: Dictionary = seq[(idx + k) % seq.size()]
		var r := ride(world, lid, from_stop, st.stop)
		out.append({"stop": st.stop, "name": st.name, "minutes": r.minutes})
	return out


## Zielanzeige: Name der Endhaltestelle der aktuellen Fahrtrichtung.
func destination_name(lid: int, s: float) -> String:
	var ld: Dictionary = lines[lid]
	var bl: Dictionary = city.bus_lines[lid]
	if ld.loop:
		return "Ringlinie"
	var first: Array = bl.stops
	var back: Array = bl.stops_back
	var half_s: float = ld.legs[ld.half_leg - 1].s0 + ld.legs[ld.half_leg - 1].len
	if s < half_s:
		if not first.is_empty():
			return city.bus_stops[first[-1]].name
	elif not back.is_empty():
		return city.bus_stops[back[-1]].name
	return str(bl.via)


## Linienverlauf als Polylinie (Knotenpositionen) fuer Karten.
func line_polyline(lid: int) -> PackedVector2Array:
	var out := PackedVector2Array()
	var bl: Dictionary = city.bus_lines[lid]
	for nid in bl.nodes:
		out.append(city.nodes[nid].pos)
	if bl.loop and not bl.nodes.is_empty():
		out.append(city.nodes[bl.nodes[0]].pos)
	return out


## Alle Busse aller Linien mit Fahrplanposition (fuer Kartenmarker).
func all_bus_positions(world) -> Array:
	_ensure(world)
	var out := []
	for lid in lines.size():
		for i in bus_count(world, lid):
			var bp := bus_position(world, lid, i)
			bp["line"] = lid
			bp["bus"] = i
			bp["color"] = city.bus_lines[lid].color
			out.append(bp)
	return out
