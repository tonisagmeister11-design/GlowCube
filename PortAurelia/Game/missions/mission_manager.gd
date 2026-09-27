class_name MissionManager
extends Node
## Mission flow: the story chain (one giver marker for the next story mission), side
## jobs (started from the phone or markers), collectibles (25 hidden postcards),
## rewards, failure handling (death / arrest) and HUD blips.

const STORY := [
	{"id": "m01", "title": "Neuanfang", "script": "res://missions/story/m01_new_start.gd", "giver": "safehouse", "reward": 15000,
		"desc": "Marco hat einen Wagen für dich."},
	{"id": "m02", "title": "Lieferservice", "script": "res://missions/story/m02_delivery.gd", "giver": "diner", "reward": 25000,
		"desc": "Eine eilige Lieferung aus dem Hafen."},
	{"id": "m03", "title": "Schuldeneintreiber", "script": "res://missions/story/m03_debt.gd", "giver": "diner", "reward": 35000,
		"desc": "Vince schuldet Marco Geld."},
	{"id": "m04", "title": "Verfolgungsjagd", "script": "res://missions/story/m04_chase.gd", "giver": "diner", "reward": 50000,
		"desc": "Ein Kurier hat Marcos Paket gestohlen."},
	{"id": "m05", "title": "Eskorte", "script": "res://missions/story/m05_escort.gd", "giver": "landmark_grand_hotel", "reward": 80000,
		"desc": "Ein wichtiger Gast muss sicher zum Flughafen."},
	{"id": "m06", "title": "Auftragsmord", "script": "res://missions/story/m06_hit.gd", "giver": "diner", "reward": 120000,
		"desc": "Viktor Sorel steht Marco im Weg."},
	{"id": "m07", "title": "Gebrauchtwagen", "script": "res://missions/story/m07_steal.gd", "giver": "car_dealer", "reward": 150000,
		"desc": "Ein Kunde wünscht sich einen ganz bestimmten Sportwagen."},
	{"id": "m08", "title": "Der große Coup", "script": "res://missions/story/m08_heist.gd", "giver": "property_business", "reward": 750000,
		"desc": "Die Meridian Bank. Alles oder nichts."},
]
const SIDE := {
	"taxi": {"title": "Taxi-Schicht", "script": "res://missions/side/taxi.gd", "reward": 0, "desc": "Fahrgäste befördern."},
	"courier": {"title": "Kurierjob", "script": "res://missions/side/courier.gd", "reward": 0, "desc": "4 Pakete gegen die Uhr."},
	"vigilante": {"title": "Bürgerwehr", "script": "res://missions/side/vigilante.gd", "reward": 0, "desc": "Flüchtige Verdächtige stoppen."},
	"race": {"title": "Straßenrennen", "script": "res://missions/side/street_race.gd", "reward": 0, "desc": "Drei Klassen, Preisgeld bis $140.000."},
	"jewel_heist": {"title": "Juwelen für Dante", "script": "res://missions/contracts/jewel_heist.gd", "reward": 90000,
		"desc": "Raube einen Juwelier aus und bring Dante die Steine. Er zahlt $90.000 – die Beute behältst du.",
		"giver": "jewelry"},
	"car_heist": {"title": "Heiße Ware", "script": "res://missions/contracts/car_heist.gd", "reward": 0,
		"desc": "Ein nagelneuer Zenith R wird zum Händler geliefert. Klau ihn – der Käufer zahlt $200.000.",
		"giver": "car_dealer"},
	"pizza": {"title": "Job: Pizzabote", "script": "res://missions/jobs/pizza.gd", "reward": 0,
		"desc": "Legal · 5 Pizzen mit dem Roller ausliefern. Ca. $250-350 pro Lieferung.", "giver": "diner", "slot": 1},
	"car_sales": {"title": "Job: Autoverkäufer", "script": "res://missions/jobs/car_sales.gd", "reward": 0,
		"desc": "Legal · Kunden beraten und Autos verkaufen. Provision je nach Verhandlung.", "giver": "car_dealer", "slot": 1},
	"smuggling": {"title": "Job: Schmuggel", "script": "res://missions/jobs/smuggling.gd", "reward": 0,
		"desc": "Illegal · Heißes Paket vom Hafen zum Käufer bringen. $18.000 – Polizeistreifen unterwegs.",
		"giver": "property_business"},
	"chop_shop": {"title": "Job: Autos auf Bestellung", "script": "res://missions/jobs/chop_shop.gd", "reward": 0,
		"desc": "Illegal · Drei bestimmte Autos klauen und zur Werkstatt bringen. $2.500-45.000 pro Auto.",
		"giver": "mechanic"},
	"armored_truck": {"title": "Geldtransporter", "script": "res://missions/contracts/armored_truck.gd", "reward": 25000,
		"desc": "Überfalle einen gepanzerten Geldtransporter. Beute ca. $60.000 + $25.000 Bonus.",
		"giver": "bank"},
}
const POSTCARDS := 25

var world: GameWorld
var current: Mission = null
var current_id := ""
var _giver_marker: InteractMarker
var _giver_mission := {}
var _race_markers: Array = []
var _postcards: Array = []        # [{pos, node}]
var _pc_timer := 0.0
var _contract_markers: Array = []
var _last_failed := {}            # {id, story, params, checkpoint} for "retry" after a failed mission
var _aborting := false


func _ready() -> void:
	name = "Missions"
	world = GameWorld.instance
	world.missions = self
	Events.player_died.connect(func(): _fail_current("Du bist gestorben."))
	Events.player_busted.connect(func(): _fail_current("Du wurdest verhaftet."))
	_refresh_giver()
	_setup_races()
	_setup_contracts()
	_setup_postcards()
	if Game.pending_mission != "":
		var mid := Game.pending_mission
		Game.pending_mission = ""
		await Events.world_ready
		await get_tree().create_timer(2.5).timeout
		if SIDE.has(mid):
			start_side(mid)
		else:
			start_story(mid)


func is_active() -> bool:
	return current != null and is_instance_valid(current) and current.active


func current_time_left() -> float:
	return current.time_left if is_active() else -1.0


# ------------------------------------------------------------------ story chain
func next_story() -> Dictionary:
	for m in STORY:
		if Game.player_data.missions.get(m["id"], "") != "done":
			return m
	return {}


func story_progress() -> Vector2i:
	var done := 0
	for m in STORY:
		if Game.player_data.missions.get(m["id"], "") == "done":
			done += 1
	return Vector2i(done, STORY.size())


func _refresh_giver() -> void:
	if is_instance_valid(_giver_marker):
		_giver_marker.queue_free()
	_giver_mission = next_story()
	if _giver_mission.is_empty():
		return
	var p := world.data.nearest_poi(_giver_mission["giver"], world.data.spawn)
	if p.is_empty():
		return
	_giver_marker = InteractMarker.new()
	_giver_marker.prompt = "E: Mission starten – %s" % _giver_mission["title"]
	_giver_marker.color = Color(1.0, 0.85, 0.2)
	_giver_marker.interact_radius = 2.0
	var m := _giver_mission
	_giver_marker.on_interact = func(_pl): _offer(m)
	_giver_marker.condition = func(_pl): return not is_active()
	world.add_child(_giver_marker)
	_giver_marker.global_position = (p["entrance_v"] as Vector3) + (p["facing_v"] as Vector3) * 3.0 \
		+ (p["facing_v"] as Vector3).cross(Vector3.UP) * 2.0


func _offer(m: Dictionary) -> void:
	MenuPanel.open(m["title"], [
		{"label": "Mission starten", "action": func(): start_story(m["id"])},
		{"label": "Später", "action": func(): pass},
	], m["desc"] + "\nBelohnung: $%d" % int(m["reward"]))


func start_story(mid: String) -> bool:
	for m in STORY:
		if m["id"] == mid:
			return _start(mid, m["script"], m["title"], int(m["reward"]))
	return false


func start_side(key: String, params := {}) -> bool:
	var s: Dictionary = SIDE.get(key, {})
	if s.is_empty():
		return false
	return _start(key, s["script"], s["title"], int(s["reward"]), params)


func _start(mid: String, script_path: String, t: String, rew: int, params := {}) -> bool:
	if is_active():
		Events.notify.emit("Du bist bereits in einer Mission.", 3.0)
		return false
	var scr: GDScript = load(script_path)
	var m: Mission = scr.new()
	m.id = mid
	m.title = t
	m.reward = rew
	m.name = "Mission_" + mid
	add_child(m)
	m.setup(self, params)
	m.finished.connect(_on_finished.bind(m))
	current = m
	current_id = mid
	Game.player_data.mission_current = mid
	if is_instance_valid(_giver_marker):
		_giver_marker.visible = false
	m.begin()
	return true


func _fail_current(reason: String) -> void:
	if is_active():
		current.fail(reason)


func abort_current() -> void:
	if is_active():
		_aborting = true
		current.fail("Mission abgebrochen.")
		_aborting = false


func _on_finished(ok: bool, m: Mission) -> void:
	var pd := Game.player_data
	pd.mission_current = ""
	if ok:
		var is_story := m.id.begins_with("m0")
		if m.reward > 0:
			pd.add_money(m.reward, "mission")
		if is_story:
			pd.missions[m.id] = "done"
			pd.stat_add("missions_passed", 1)
		Events.mission_completed.emit(m.id, m.reward)
		Events.big_message.emit("MISSION BESTANDEN", ("+$%d" % m.reward) if m.reward > 0 else m.title, 4.0)
		AudioManager.play_ui("mission_passed")
		if is_story and next_story().is_empty():
			get_tree().create_timer(5.0).timeout.connect(func():
				Events.big_message.emit("HARBOR HEAT", "Story abgeschlossen – die Stadt gehört dir.", 6.0))
		get_tree().create_timer(4.5).timeout.connect(func(): SaveManager.autosave())
	else:
		AudioManager.play_ui("mission_failed")
		if not _aborting:
			_last_failed = {"id": m.id, "story": m.id.begins_with("m0"), "params": m._params.duplicate(),
				"checkpoint": m.checkpoint_data.duplicate()}
			get_tree().create_timer(5.5 if player_dead_or_busted() else 3.0).timeout.connect(_offer_retry)
	current = null
	current_id = ""
	m.queue_free()
	_refresh_giver()


func player_dead_or_busted() -> bool:
	var p := world.player as Player
	return p != null and p.state in [Player.State.DEAD, Player.State.BUSTED]


## After a failed mission: restart from the last checkpoint (or from the start).
func _offer_retry() -> void:
	if _last_failed.is_empty() or is_active():
		return
	var lf := _last_failed
	var items := []
	var cp: Dictionary = lf["checkpoint"]
	if not cp.is_empty():
		items.append({"label": "Ab Checkpoint neu starten", "action": func(): retry(true)})
	items.append({"label": "Mission neu starten", "action": func(): retry(false)})
	items.append({"label": "Beenden", "action": func(): _last_failed = {}})
	MenuPanel.open("MISSION FEHLGESCHLAGEN", items, "Nochmal versuchen?")


func retry(from_checkpoint: bool) -> void:
	var lf := _last_failed
	_last_failed = {}
	if lf.is_empty():
		return
	var params: Dictionary = lf["params"].duplicate()
	var cp: Dictionary = lf["checkpoint"]
	if from_checkpoint and not cp.is_empty():
		params["stage"] = int(cp["stage"])
		var p := world.player as Player
		var pos: Vector3 = cp["pos"]
		world.streaming.load_area_blocking(pos, 250.0)
		if p.vehicle:
			p.exit_vehicle()
		p.teleport(pos + Vector3.UP * 0.3)
		if String(cp["vehicle"]) != "" and world.economy:
			var dir := Vector3(sin(float(cp["yaw"])), 0, cos(float(cp["yaw"]))) * -1.0
			var v := Vehicle.create(String(cp["vehicle"]))
			v.transform = Transform3D(Basis.looking_at(dir if dir.length() > 0.1 else Vector3.FORWARD, Vector3.UP),
				pos + Vector3.UP * 0.8)
			world.add_child(v)
			params["checkpoint_vehicle"] = v
			p.enter_vehicle(v)
	if bool(lf["story"]):
		for m in STORY:
			if m["id"] == lf["id"]:
				_start(m["id"], m["script"], m["title"], int(m["reward"]), params)
	else:
		start_side(lf["id"], params)


func _on_failed_reason(_mid: String, reason: String) -> void:
	Events.big_message.emit("MISSION FEHLGESCHLAGEN", reason, 4.0)


# ------------------------------------------------------------------ races (markers)
const RACE_TIER_NAMES := ["Street", "Pro", "Hypercar-Liga"]
const RACE_SPOTS := 7


func _setup_races() -> void:
	Events.mission_failed.connect(_on_failed_reason)
	# spread the start lines over the whole map (farthest-point sampling over the POIs)
	var cands: Array = []
	for p in world.data.pois:
		if String(p["type"]) in ["safehouse", "police_station", "hospital", "airport_terminal"]:
			continue
		cands.append(p)
	if cands.is_empty():
		return
	var chosen: Array = [world.data.nearest_poi("landmark_stadium", world.data.spawn)]
	if chosen[0].is_empty():
		chosen = [cands[0]]
	while chosen.size() < RACE_SPOTS and chosen.size() < cands.size():
		var best: Dictionary = {}
		var bd := -1.0
		for c in cands:
			var dmin := INF
			for q in chosen:
				dmin = minf(dmin, (c["entrance_v"] as Vector3).distance_to(q["entrance_v"]))
			if dmin > bd:
				bd = dmin
				best = c
		chosen.append(best)
	for i in chosen.size():
		var p: Dictionary = chosen[i]
		var tier := i % 3
		var tt: Dictionary = preload("res://missions/side/street_race.gd").TIERS[tier]
		var m := InteractMarker.new()
		m.prompt = "E: Straßenrennen %s  (Einsatz $%d, Sieg $%d)" % [RACE_TIER_NAMES[tier], int(tt["fee"]), int(tt["prizes"][0])]
		m.color = [Color(0.3, 0.9, 1.0), Color(1.0, 0.55, 0.15), Color(1.0, 0.2, 0.6)][tier]
		m.interact_radius = 3.0
		m.on_interact = func(_pl): start_side("race", {"tier": tier})
		m.condition = func(_pl): return not is_active()
		world.add_child(m)
		m.global_position = (p["entrance_v"] as Vector3) + (p["facing_v"] as Vector3) * 5.0
		m.set_meta("tier", tier)
		_race_markers.append(m)


# ------------------------------------------------------------------ contracts (markers)
func _setup_contracts() -> void:
	for key in SIDE:
		var sd: Dictionary = SIDE[key]
		if not sd.has("giver"):
			continue
		var p := world.data.nearest_poi(sd["giver"], world.data.spawn)
		if p.is_empty():
			continue
		var k: String = key
		var m := InteractMarker.new()
		m.prompt = "E: Auftrag – %s" % sd["title"]
		m.color = Color(0.4, 1.0, 0.45)
		m.interact_radius = 2.2
		m.on_interact = func(_pl): MenuPanel.open(sd["title"], [
			{"label": "Auftrag annehmen", "action": func(): start_side(k)},
			{"label": "Später", "action": func(): pass}], sd["desc"])
		m.condition = func(_pl): return not is_active()
		world.add_child(m)
		var f: Vector3 = p["facing_v"]
		var slot := int(sd.get("slot", 0))
		m.global_position = (p["entrance_v"] as Vector3) + f * 4.0 - f.cross(Vector3.UP) * (2.5 - slot * 5.0)
		_contract_markers.append(m)


# ------------------------------------------------------------------ collectibles
func _setup_postcards() -> void:
	var pg: PedGraph = (world.peds as PedManager).ped_graph if world.peds else null
	if pg == null or pg.node_pos.is_empty():
		return
	var rng := RandomNumberGenerator.new()
	rng.seed = 4242
	var chosen: Array = []
	var tries := 0
	while chosen.size() < POSTCARDS and tries < 5000:
		tries += 1
		var e := rng.randi() % pg.edge_len.size()
		if pg.edge_kind[e] != PedGraph.Kind.WALK or pg.edge_len[e] < 10.0:
			continue
		var p := pg.point_on(e, pg.edge_len[e] * rng.randf_range(0.2, 0.8))
		var ok := true
		for c in chosen:
			if (c as Vector3).distance_to(p) < 220.0:
				ok = false
				break
		if ok:
			chosen.append(p)
	for i in chosen.size():
		_postcards.append({"id": "pc%02d" % i, "pos": chosen[i], "node": null})


func postcards_found() -> int:
	return Game.player_data.collectibles.size()


func _process(delta: float) -> void:
	_pc_timer -= delta
	if _pc_timer > 0.0 or world.player == null:
		return
	_pc_timer = 0.5
	var pp := world.player.global_position
	for pc in _postcards:
		if Game.player_data.collectibles.has(pc["id"]):
			continue
		var d := (pc["pos"] as Vector3).distance_to(pp)
		if d < 120.0 and pc["node"] == null:
			pc["node"] = _make_postcard(pc)
		elif d > 150.0 and pc["node"] != null:
			if is_instance_valid(pc["node"]):
				pc["node"].queue_free()
			pc["node"] = null


func _make_postcard(pc: Dictionary) -> Node3D:
	var a := Area3D.new()
	a.collision_layer = 1 << 6
	a.collision_mask = 1 << 1
	var cs := CollisionShape3D.new()
	var sp := SphereShape3D.new()
	sp.radius = 1.0
	cs.shape = sp
	cs.position.y = 0.8
	a.add_child(cs)
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = Vector3(0.45, 0.3, 0.02)
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(1.0, 0.85, 0.4)
	mat.emission_enabled = true
	mat.emission = Color(1.0, 0.7, 0.2)
	mat.emission_energy_multiplier = 1.2
	bm.material = mat
	mi.mesh = bm
	mi.position.y = 1.0
	a.add_child(mi)
	var tw := mi.create_tween().set_loops()
	tw.tween_property(mi, "rotation:y", TAU, 2.5).from(0.0)
	world.add_child(a)
	a.global_position = pc["pos"]
	a.body_entered.connect(func(b):
		if b is Player:
			Game.player_data.collectibles.append(pc["id"])
			var n := postcards_found()
			Game.player_data.add_money(100, "collectible")
			AudioManager.play_ui("money", -2.0)
			Events.notify.emit("Postkarte gefunden! %d/%d" % [n, POSTCARDS], 4.0)
			if n >= POSTCARDS:
				Game.player_data.add_money(10000, "collectible_bonus")
				Events.big_message.emit("ALLE POSTKARTEN", "+$10.000", 4.0)
			a.queue_free()
			pc["node"] = null)
	return a


# ------------------------------------------------------------------ HUD
func blips() -> Array:
	var out := []
	if is_active():
		out.append_array(current.blips())
	elif is_instance_valid(_giver_marker):
		out.append({"pos": _giver_marker.global_position, "icon": "M", "color": Color(1.0, 0.85, 0.2), "size": 9.0, "edge": true})
	if not is_active():
		var pp := world.player.global_position
		for m in _race_markers:
			if is_instance_valid(m) and m.global_position.distance_to(pp) < 900.0:
				out.append({"pos": m.global_position, "icon": "R", "color": m.color, "size": 8.0})
		for m in _contract_markers:
			if is_instance_valid(m) and m.global_position.distance_to(pp) < 1500.0:
				out.append({"pos": m.global_position, "icon": "$", "color": Color(0.4, 1.0, 0.45), "size": 9.0})
	return out


func serialize() -> Dictionary:
	return {"current": current_id}
