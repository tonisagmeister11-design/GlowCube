class_name TrafficManager
extends Node3D
## Sichtbarer Stadtverkehr rund um den Spieler. Wird von world_root als Kind
## von "Actors" erzeugt: setup(world, city_root).
##
## * KI-Verkehr (TrafficSim) im Umkreis von ~320 m um den Fokus (Spieler bzw.
##   aktive Kamera), Dichte aus Settings gameplay/traffic_density.
## * Busse nach Fahrplan (Transit): sichtbar, wo der Fahrplan sie gerade hat;
##   Haltestellen sind interagierbar (Abfahrten, Einsteigen, Fahrt).
## * Geparkte Autos an Strassenparkplaetzen/Parkplaetzen (MultiMesh), nahe
##   Fahrzeuge mit Kollision; abgeschlossen (Diebstahl ueber set_locked/unlock_parked).
## * Eigene Fahrzeuge des Spielers (world.player.vehicles) als PlayerVehicle.
## * Einsatzfahrzeuge: dispatch(kind, target) und Polizeieinsaetze aus
##   world.crime.take_dispatches().

const SPAWN_R := 320.0
const DESPAWN_R := 390.0
const SPAWN_MIN := 140.0
const BASE_COUNT := 60
const BUS_R := 360.0
const PARK_R := 150.0
const PROXY_R := 45.0
const MAX_POLICE := 3

var world: WorldState
var city: CityModel
var city_root: Node3D
var sim: TrafficSim
var transit: Transit
var rng := RandomNumberGenerator.new()
var visuals: Dictionary = {}          # VehicleAI -> VehicleVisual
var buses: Dictionary = {}            # Vector2i(line, idx) -> VehicleAI
var owned: Dictionary = {}            # vehicle_id -> PlayerVehicle
var stops: Array = []                 # BusStopPoint
var emergency: Array = []             # [{ai, visual, release_at}]
var _focus := Vector2.ZERO
var _first_fill := true
var _bus_timer := 0.0
var _crime_timer := 0.0
var _owned_timer := 0.0
var _parked_center := Vector2.INF
var _parked_mm: Dictionary = {}       # type -> [MultiMeshInstance3D Karosserie, Raeder]
var _parked_spots: Array = []         # [{key, pos, rot, type, color}]
var _proxies: Array = []
var _taken_spots: Dictionary = {}     # Parkplaetze, deren Auto zum PlayerVehicle wurde
var _riding := false
var stats := {"step_ms": 0.0, "vehicles": 0}


func setup(p_world: WorldState, p_city_root: Node3D) -> void:
	world = p_world
	city = world.city
	city_root = p_city_root
	name = "TrafficManager"
	add_to_group("traffic_manager")
	rng.seed = hash("traffic_view:%d" % world.seed)
	sim = TrafficSim.create(city, world, world.seed)
	transit = sim.transit
	for st in city.bus_stops:
		var bp := BusStopPoint.new()
		bp.setup(self, int(st.id))
		add_child(bp)
		stops.append(bp)


func _density() -> float:
	var s := get_node_or_null("/root/Settings")
	if s:
		return clampf(float(s.get_value("gameplay", "traffic_density")), 0.0, 3.0)
	return 1.0


func focus_node() -> Node3D:
	var p := get_tree().get_first_node_in_group("player")
	if p is Node3D:
		var v = p.get("vehicle") if "vehicle" in p else null
		if v is Node3D and is_instance_valid(v):
			return v
		return p
	return get_viewport().get_camera_3d()


func signal_time() -> float:
	return Time.get_ticks_msec() / 1000.0


func is_night() -> bool:
	var h := world.get_hour_f()
	return h < 6.5 or h > 19.6


func _process(delta: float) -> void:
	if sim == null:
		return
	var f := focus_node()
	if f:
		_focus = Vector2(f.global_position.x, f.global_position.z)
	delta = minf(delta, 0.05)
	_collect_obstacles()
	var t0 := Time.get_ticks_usec()
	sim.step(delta, signal_time())
	stats.step_ms = (Time.get_ticks_usec() - t0) / 1000.0
	_manage_ambient()
	_bus_timer -= delta
	if _bus_timer <= 0.0:
		_bus_timer = 0.5
		_manage_buses()
	_crime_timer -= delta
	if _crime_timer <= 0.0:
		_crime_timer = 1.5
		_poll_crime()
	_owned_timer -= delta
	if _owned_timer <= 0.0:
		_owned_timer = 2.0
		sync_owned_vehicles()
	_manage_emergency()
	if _parked_center == Vector2.INF or _parked_center.distance_to(_focus) > 35.0:
		_rebuild_parked()
	_update_proxies()
	var night := is_night()
	for v in visuals:
		var vis: VehicleVisual = visuals[v]
		vis.sync_ai(night, delta)
	stats.vehicles = sim.vehicles.size()


# ============================================================ KI-Verkehr

func _collect_obstacles() -> void:
	var obs := []
	for p in get_tree().get_nodes_in_group("player"):
		if p is Node3D and p.visible:
			var pv: Vector3 = p.get("velocity") if p.get("velocity") != null else Vector3.ZERO
			var in_car = p.get("vehicle") if "vehicle" in p else null
			if in_car == null:
				obs.append({"pos": Vector2(p.global_position.x, p.global_position.z), "r": 0.5, "vel": Vector2(pv.x, pv.z)})
	for pv2 in owned.values():
		if is_instance_valid(pv2):
			obs.append(pv2.obstacle())
	for v in get_tree().get_nodes_in_group("vehicle"):
		if v is PlayerVehicle and not v in owned.values() and is_instance_valid(v):
			obs.append(v.obstacle())
	var crowd := get_tree().get_first_node_in_group("crowd_manager")
	if crowd and crowd.has_method("get_road_obstacles"):
		obs.append_array(crowd.get_road_obstacles())
	sim.obstacles = obs


func _manage_ambient() -> void:
	var perf := get_node_or_null("/root/Perf")
	var target: int = perf.max_vehicles() if perf != null else int(round(BASE_COUNT * _density()))
	var ambient := 0
	for v in sim.vehicles.duplicate():
		if v.role != "ambient":
			continue
		var d: float = v.pos.distance_to(_focus)
		if d > DESPAWN_R or (v.ghost_time > 0.0 and d > 90.0):
			_remove(v)
			continue
		ambient += 1
	var tries := 0
	var spawned := 0
	var max_spawn := 40 if _first_fill else 2
	var rmin := 25.0 if _first_fill else SPAWN_MIN
	while ambient < target and tries < 30 and spawned < max_spawn:
		tries += 1
		var ang := rng.randf() * TAU
		var r := rng.randf_range(rmin, SPAWN_R)
		var p := _focus + Vector2(cos(ang), sin(ang)) * r
		var nl := sim.graph.nearest_lane(p)
		if nl.seg < 0 or nl.dist > 30.0:
			continue
		var typ := VehicleCatalog.random_ai_type(rng)
		var len_v: float = VehicleCatalog.type_info(typ).length
		var s: float = clampf(nl.s, len_v + 1.0, sim.graph.seg_len[nl.seg] - 1.0)
		if not sim.can_place(nl.seg, s, len_v, 10.0):
			continue
		var v2 := sim.spawn(typ, nl.seg, s)
		_make_visual(v2)
		ambient += 1
		spawned += 1
	_first_fill = false


func _make_visual(v: VehicleAI) -> VehicleVisual:
	var vis := VehicleVisual.new()
	var opts := {"collider": true}
	vis.build(v.type, v.color, v.variant, opts)
	vis.ai = v
	add_child(vis)
	visuals[v] = vis
	vis.sync_ai(is_night(), 0.016)
	return vis


func _remove(v: VehicleAI) -> void:
	sim.remove(v)
	if visuals.has(v):
		visuals[v].queue_free()
		visuals.erase(v)


# ============================================================ Busse

func _manage_buses() -> void:
	var want := {}
	for lid in transit.line_count():
		var n := transit.bus_count(world, lid)
		for i in n:
			var bp := transit.bus_position(world, lid, i)
			var key := Vector2i(lid, i)
			var near: bool = bp.pos.distance_to(_focus) < BUS_R
			if near:
				want[key] = true
			if buses.has(key):
				var v: VehicleAI = buses[key]
				var far: bool = v.pos.distance_to(_focus) > BUS_R + 60.0
				var off := absf(v.schedule_err) > 220.0 and v.pos.distance_to(_focus) > 110.0
				if (far and not near) or off or v.ghost_time > 0.0 and v.pos.distance_to(_focus) > 90.0:
					_remove(v)
					buses.erase(key)
			elif near and not _riding:
				var nb := sim.spawn_bus(lid, i)
				_make_visual(nb)
				buses[key] = nb
	for key in buses.keys():
		if not want.has(key) and buses[key].pos.distance_to(_focus) > BUS_R + 60.0:
			_remove(buses[key])
			buses.erase(key)
	for key in buses:
		var bv: VehicleAI = buses[key]
		var lid2: int = key.x
		var bl: Dictionary = city.bus_lines[lid2]
		var dest := transit.destination_name(lid2, sim.bus_cycle_s(bv))
		visuals[bv].set_display("%d  %s" % [int(bl.number), dest], bl.color)


## Bus, der gerade an der Haltestelle steht (oder null).
func bus_at_stop(stop_id: int) -> VehicleAI:
	var st: Dictionary = city.bus_stops[stop_id]
	for key in buses:
		var v: VehicleAI = buses[key]
		if v.dwelling and v.stop_log.size() > 0 and v.stop_log[-1] == stop_id:
			return v
		if v.dwelling and v.pos.distance_to(st.curb_pos) < 14.0:
			return v
	return null


## Fahrt mit einem Bus: Fahrpreis, Kamerafahrt, Zeitsprung, Ankunft.
func ride_bus(player: Node, bus: VehicleAI, from_stop: int, to_stop: int) -> Dictionary:
	if _riding:
		return {"ok": false, "reason": "Du fährst bereits."}
	var r := transit.ride(world, bus.line, from_stop, to_stop)
	if not r.ok:
		return r
	var fare: float = r.fare
	var L := world.ledger
	if fare > 0.0:
		var to_acc := -1
		if world.government != null and world.government.get("account") != null:
			to_acc = int(world.government.account)
		var paid := false
		if to_acc >= 0 and L.has_account(to_acc):
			paid = L.transfer(L.player_account, to_acc, fare, "Busfahrt")
		elif L.can_pay(L.player_account, fare):
			paid = L.burn(L.player_account, fare, "Busfahrt")
		if not paid:
			_notify("Nicht genug Geld für die Busfahrt (%s)." % _money(fare), "warning")
			return {"ok": false, "reason": "Nicht genug Geld"}
	_riding = true
	_ride_sequence(player, bus, to_stop, float(r.minutes))
	return {"ok": true, "minutes": r.minutes, "fare": fare}


func _ride_sequence(player: Node, bus: VehicleAI, to_stop: int, minutes: float) -> void:
	var vis: VehicleVisual = visuals.get(bus)
	var cam := Camera3D.new()
	cam.fov = 60.0
	add_child(cam)
	var prev_cam := get_viewport().get_camera_3d()
	if player is Node3D:
		player.visible = false
	if player.has_method("set_controls_enabled"):
		player.set_controls_enabled(false)
	cam.make_current()
	var layer := CanvasLayer.new()
	layer.layer = 30
	var fade := ColorRect.new()
	fade.color = Color(0, 0, 0, 0)
	fade.set_anchors_preset(Control.PRESET_FULL_RECT)
	fade.mouse_filter = Control.MOUSE_FILTER_IGNORE
	layer.add_child(fade)
	var lbl := Label.new()
	lbl.theme_type_variation = "HeaderLabel"
	lbl.text = "Fahrt nach %s" % city.bus_stops[to_stop].name
	lbl.set_anchors_preset(Control.PRESET_CENTER)
	lbl.modulate.a = 0.0
	layer.add_child(lbl)
	add_child(layer)
	var t := 0.0
	while t < 3.0:
		await get_tree().process_frame
		var dt := get_process_delta_time()
		t += dt
		if is_instance_valid(vis):
			var back: Vector3 = vis.global_transform.basis.z
			var target := vis.global_position + back * 16.0 + Vector3(0, 7.0, 0) + vis.global_transform.basis.x * 6.0
			cam.global_position = cam.global_position.lerp(target, 0.08) if t > 0.05 else target
			cam.look_at(vis.global_position + Vector3(0, 1.5, 0))
		if t > 2.0:
			fade.color.a = clampf((t - 2.0), 0.0, 1.0)
			lbl.modulate.a = fade.color.a
	# Zeitsprung: Simulation laeuft weiter, Busse springen an ihre Fahrplanposition
	var game := get_node_or_null("/root/Game")
	if game and game.world == world and game.has_method("skip_time"):
		game.skip_time(minutes)
	else:
		world.advance(minutes)
		world.time_minutes = world.time_minutes
	for key in buses.keys():
		_remove(buses[key])
	buses.clear()
	var st: Dictionary = city.bus_stops[to_stop]
	var dst: Vector2 = st.pos
	_focus = dst
	if player.has_method("teleport"):
		var dir: Vector2 = st.dir
		player.teleport(Vector3(dst.x, 0.2, dst.y), atan2(dir.x, dir.y))
	elif player is Node3D:
		player.global_position = Vector3(dst.x, 0.2, dst.y)
	_riding = false
	_manage_buses()
	if player is Node3D:
		player.visible = true
	if player.has_method("set_controls_enabled"):
		player.set_controls_enabled(true)
	if is_instance_valid(prev_cam):
		prev_cam.make_current()
	cam.queue_free()
	var t2 := 0.0
	while t2 < 0.6:
		await get_tree().process_frame
		t2 += get_process_delta_time()
		fade.color.a = 1.0 - t2 / 0.6
		lbl.modulate.a = fade.color.a
	layer.queue_free()
	_notify("Angekommen: %s" % st.name, "success")


# ============================================================ Einsatzfahrzeuge

## Schickt ein Einsatzfahrzeug ("police", "ambulance", "fire") mit Blaulicht
## zu einem Zielpunkt. Liefert den VehicleVisual-Knoten (Signal `arrived`)
## oder null. release_vehicle(node) beendet den Einsatz.
func dispatch(kind: String, target: Vector3) -> Node3D:
	var typ := "police"
	match kind:
		"ambulance", "rettung":
			typ = "ambulance"
		"fire", "feuerwehr":
			typ = "fire"
	var t2 := Vector2(target.x, target.z)
	var best := {}
	for k in 24:
		var ang := rng.randf() * TAU
		var p := t2 + Vector2(cos(ang), sin(ang)) * rng.randf_range(140.0, 260.0)
		var nl := sim.graph.nearest_lane(p)
		if nl.seg < 0 or nl.dist > 30.0:
			continue
		var len_v: float = VehicleCatalog.type_info(typ).length
		var s: float = clampf(nl.s, len_v + 1.0, sim.graph.seg_len[nl.seg] - 1.0)
		if sim.can_place(nl.seg, s, len_v, 8.0):
			best = {"seg": nl.seg, "s": s}
			break
	if best.is_empty():
		return null
	var v := sim.spawn_emergency(typ, best.seg, best.s, t2)
	var vis := _make_visual(v)
	var audio := get_node_or_null("/root/Audio")
	if audio and audio.has_method("attach_loop"):
		audio.attach_loop("siren", vis)
	emergency.append({"ai": v, "visual": vis, "release_at": -1.0, "notified": false})
	return vis


func release_vehicle(node: Node3D) -> void:
	for e in emergency:
		if e.visual == node:
			sim.release_target(e.ai)
			for c in node.get_children():
				if c is AudioStreamPlayer3D:
					c.queue_free()
	emergency = emergency.filter(func(e): return e.visual != node)


func _manage_emergency() -> void:
	for e in emergency.duplicate():
		var v: VehicleAI = e.ai
		if not is_instance_valid(e.visual):
			emergency.erase(e)
			continue
		if v.arrived and not e.notified:
			e.notified = true
			e.release_at = sim.time + 45.0
			(e.visual as VehicleVisual).arrived.emit()
		if e.release_at > 0.0 and sim.time > e.release_at:
			release_vehicle(e.visual)
		elif v.stuck_time > 40.0 and v.pos.distance_to(_focus) > 120.0:
			release_vehicle(e.visual)


func _poll_crime() -> void:
	var crime = world.get("crime")
	if crime == null or not crime.has_method("take_dispatches"):
		return
	var active := 0
	for e in emergency:
		if (e.ai as VehicleAI).type == "police":
			active += 1
	if active >= MAX_POLICE:
		return
	for d in crime.take_dispatches(MAX_POLICE - active):
		var p: Vector2 = d.get("pos", Vector2.INF)
		if p == Vector2.INF or p.distance_to(_focus) > 600.0:
			continue
		dispatch("police", Vector3(p.x, 0.0, p.y))


# ============================================================ Geparkte Autos

func _parked_type(key: int) -> String:
	var h := absi(hash("ptype:%d" % key))
	var opts := ["sedan", "hatchback", "hatchback", "suv", "sedan", "luxury", "van", "sports", "hatchback", "sedan", "suv"]
	return opts[h % opts.size()]


func _parked_color(key: int) -> Color:
	var r := RandomNumberGenerator.new()
	r.seed = hash("pcol:%d" % key)
	return VehicleCatalog.random_paint(r)


## Blendet alle geparkten Fahrzeuge aus (Leistungsmodus).
func _clear_parked_meshes() -> void:
	for typ in _parked_mm:
		for mmi in _parked_mm[typ]:
			mmi.multimesh.instance_count = 0


func _rebuild_parked() -> void:
	_parked_center = _focus
	_parked_spots.clear()
	# Anteil der sichtbaren Parkplaetze nach Leistungsstufe
	var perf := get_node_or_null("/root/Perf")
	var keep: float = perf.f("parked_cars") if perf != null else 1.0
	if keep <= 0.01:
		_clear_parked_meshes()
		return
	var r2: float = PARK_R * PARK_R * clampf(keep, 0.2, 1.0)
	for i in city.street_parking.size():
		var sp: Dictionary = city.street_parking[i]
		if not sim.spot_occupied(i) or _taken_spots.has(i):
			continue
		if keep < 1.0 and float(absi(hash("pk:%d" % i)) % 100) >= keep * 100.0:
			continue
		var p: Vector2 = sp.pos
		if p.distance_squared_to(_focus) > r2:
			continue
		_parked_spots.append({"key": i, "pos": p, "rot": float(sp.rot), "type": _parked_type(i), "color": _parked_color(i)})
	for pa in city.parking_areas:
		if pa.rect.get_center().distance_squared_to(_focus) > r2 * 1.5:
			continue
		for j in pa.spots.size():
			var key: int = 1000000 + int(pa.id) * 200 + j
			if _taken_spots.has(key) or absi(hash("pa:%d" % key)) % 100 > 55:
				continue
			var sp2: Dictionary = pa.spots[j]
			var flip := PI if absi(hash("pf:%d" % key)) % 2 == 0 else 0.0
			_parked_spots.append({"key": key, "pos": sp2.pos, "rot": float(sp2.rot) + flip, "type": _parked_type(key), "color": _parked_color(key)})
	# Multimeshes je Typ
	var by_type := {}
	for s in _parked_spots:
		if not by_type.has(s.type):
			by_type[s.type] = []
		by_type[s.type].append(s)
	for typ in _parked_mm:
		for mmi in _parked_mm[typ]:
			mmi.multimesh.instance_count = 0
	for typ in by_type:
		var list: Array = by_type[typ]
		var model := VehicleMeshBuilder.get_model(typ)
		if not _parked_mm.has(typ):
			var a := MultiMeshInstance3D.new()
			var mm := MultiMesh.new()
			mm.transform_format = MultiMesh.TRANSFORM_3D
			mm.use_colors = true
			mm.mesh = _parked_mesh(typ)
			a.multimesh = mm
			add_child(a)
			var b := MultiMeshInstance3D.new()
			var mm2 := MultiMesh.new()
			mm2.transform_format = MultiMesh.TRANSFORM_3D
			mm2.mesh = model.wheel_mesh
			b.multimesh = mm2
			add_child(b)
			_parked_mm[typ] = [a, b]
		var mm_body: MultiMesh = _parked_mm[typ][0].multimesh
		var mm_wheel: MultiMesh = _parked_mm[typ][1].multimesh
		mm_body.instance_count = list.size()
		mm_wheel.instance_count = list.size() * model.wheels.size()
		var wi := 0
		for k in list.size():
			var s: Dictionary = list[k]
			# Strassen-rot: Blickrichtung Vector2(sin, cos) = +Z vorne; Modelle schauen nach -Z
			var tr := Transform3D(Basis(Vector3.UP, float(s.rot) + PI), Vector3(s.pos.x, 0.0, s.pos.y))
			mm_body.set_instance_transform(k, tr)
			mm_body.set_instance_color(k, s.color)
			for wd in model.wheels:
				mm_wheel.set_instance_transform(wi, tr * Transform3D(Basis(), wd.pos))
				wi += 1


static var _pmesh: Dictionary = {}


## Karosserie mit Lack ueber Instanzfarbe (MultiMesh).
static func _parked_mesh(typ: String) -> ArrayMesh:
	if _pmesh.has(typ):
		return _pmesh[typ]
	var model := VehicleMeshBuilder.get_model(typ)
	var src: ArrayMesh = model.mesh
	var mesh: ArrayMesh = src.duplicate()
	var pm := StandardMaterial3D.new()
	pm.vertex_color_use_as_albedo = true
	pm.albedo_color = Color.WHITE
	pm.metallic = 0.45
	pm.roughness = 0.32
	if model.surf.has("paint"):
		mesh.surface_set_material(model.surf.paint, pm)
	for k in ["cargo", "stripe"]:
		if model.surf.has(k):
			mesh.surface_set_material(model.surf[k], pm)
	_pmesh[typ] = mesh
	return mesh


func _update_proxies() -> void:
	var near := []
	for s in _parked_spots:
		if s.pos.distance_to(_focus) < PROXY_R:
			near.append(s)
	while _proxies.size() < near.size():
		var px := ParkedCarProxy.new()
		px.manager = self
		add_child(px)
		_proxies.append(px)
	for i in _proxies.size():
		var px: ParkedCarProxy = _proxies[i]
		if i < near.size():
			px.assign(near[i])
		else:
			px.assign({})


## Geparktes Auto in ein fahrbares Fahrzeug umwandeln (z.B. Autodiebstahl).
func claim_parked(spot: Dictionary, unlocked := true) -> PlayerVehicle:
	if spot.is_empty():
		return null
	_taken_spots[int(spot.key)] = true
	var pv := PlayerVehicle.new()
	pv.setup(spot.type, spot.color, "", world)
	pv.set_locked(not unlocked)
	add_child(pv)
	pv.global_position = Vector3(spot.pos.x, 0.05, spot.pos.y)
	pv.rotation.y = float(spot.rot) + PI
	_parked_center = Vector2.INF
	return pv


# ============================================================ Eigene Fahrzeuge

## Erzeugt PlayerVehicle-Knoten fuer world.player.vehicles (in der Naehe des
## Zuhauses bzw. des Spielers, Position wird im Eintrag gespeichert).
func sync_owned_vehicles() -> void:
	if world.player == null or world.player.get("vehicles") == null:
		return
	var vs: Array = world.player.vehicles
	for i in vs.size():
		if owned.has(i) and is_instance_valid(owned[i]):
			continue
		var entry = vs[i]
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		var gid: String = str(entry.get("good", ""))
		var typ := VehicleCatalog.type_for_good(gid)
		var pos := Vector2.INF
		var yaw := 0.0
		var stored = entry.get("pos")
		if stored is Array and stored.size() >= 3:
			pos = Vector2(float(stored[0]), float(stored[1]))
			yaw = float(stored[2])
		else:
			var spot := _free_spot_near(_home_point())
			if spot.is_empty():
				continue
			pos = spot.pos
			yaw = float(spot.rot) + PI
			entry["pos"] = [pos.x, pos.y, yaw]
		var r := RandomNumberGenerator.new()
		r.seed = hash("own:%d:%s" % [i, gid])
		var col := VehicleCatalog.random_paint(r)
		if entry.has("color"):
			col = Color(str(entry.color))
		else:
			entry["color"] = col.to_html(false)
		var pv := PlayerVehicle.new()
		pv.vehicle_id = i
		pv.setup(typ, col, gid, world)
		add_child(pv)
		pv.global_position = Vector3(pos.x, 0.05, pos.y)
		pv.rotation.y = yaw
		owned[i] = pv


func _home_point() -> Vector2:
	if world.player and world.housing and world.player.get("home") != null:
		var hid := int(world.player.home)
		if hid >= 0 and world.housing.has_method("get_property"):
			var prop: Dictionary = world.housing.get_property(hid)
			if int(prop.get("building", -1)) >= 0:
				return city.buildings[int(prop.building)].sidewalk_point
	return _focus


## Freier Strassenparkplatz nahe p: {pos, rot} oder {}.
func _free_spot_near(p: Vector2) -> Dictionary:
	var best := {}
	var bd := INF
	for i in city.street_parking.size():
		var sp: Dictionary = city.street_parking[i]
		var d: float = sp.pos.distance_squared_to(p)
		if d < bd and not sim.spot_occupied(i) and not _taken_spots.has(i):
			bd = d
			best = {"pos": sp.pos, "rot": float(sp.rot), "key": i}
	if not best.is_empty():
		_taken_spots[int(best.key)] = true
	return best


## Holt ein eigenes Fahrzeug an einen Ort (z.B. nach dem Kauf zum Autohaus).
func deliver_owned(vehicle_id: int, near: Vector2) -> PlayerVehicle:
	sync_owned_vehicles()
	var pv: PlayerVehicle = owned.get(vehicle_id)
	if pv == null:
		return null
	var nl := sim.graph.nearest_lane(near)
	var spot := _free_spot_near(near)
	if not spot.is_empty() and spot.pos.distance_to(near) < 80.0:
		pv.global_position = Vector3(spot.pos.x, 0.05, spot.pos.y)
		pv.rotation.y = float(spot.rot) + PI
	elif nl.seg >= 0:
		var q := sim.graph.pos_at(nl.seg, nl.s)
		var d := sim.graph.lane_dir(nl.seg)
		pv.global_position = Vector3(q.x, 0.05, q.y) + Vector3(CityModel.right_of(d).x, 0, CityModel.right_of(d).y) * 1.5
		pv.rotation.y = atan2(-d.x, -d.y)
	pv._store_position()
	return pv


# ============================================================ Hilfen

## Live-Busse fuer Kartenmarker: [{pos, heading, line, color, number}]
func get_bus_markers() -> Array:
	var out := []
	for bp in transit.all_bus_positions(world):
		var bl: Dictionary = city.bus_lines[bp.line]
		out.append({"pos": bp.pos, "heading": bp.heading, "line": bp.line, "color": bl.color, "number": int(bl.number)})
	return out


func _notify(text: String, kind := "info") -> void:
	var bus := get_node_or_null("/root/EventBus")
	if bus:
		bus.notify.emit(text, kind)
	else:
		world.notify(text, kind)


static func _money(v: float) -> String:
	return ("%.2f €" % v).replace(".", ",")


## Autodiebstahl (Kriminalitaetssystem): naechstes geparktes Auto im Umkreis
## entriegeln. Liefert true, wenn eines gefunden wurde.
func unlock_nearest_parked(pos: Vector3, radius := 6.0) -> bool:
	var p := Vector2(pos.x, pos.z)
	var best: ParkedCarProxy = null
	var bd := radius
	for px in _proxies:
		if px.spot.is_empty():
			continue
		var d: float = px.spot.pos.distance_to(p)
		if d < bd:
			bd = d
			best = px
	if best:
		best.set_locked(false)
		return true
	return false
