class_name MissionManager
extends Node
## Mission flow: the story chain (one giver marker for the next story mission), side
## jobs (started from the phone or markers), collectibles (25 hidden postcards),
## rewards, failure handling (death / arrest) and HUD blips.

const STORY := [
	{"id": "m01", "title": "Neuanfang", "script": "res://missions/story/m01_new_start.gd", "giver": "safehouse", "reward": 1000,
		"desc": "Marco hat einen Wagen für dich."},
	{"id": "m02", "title": "Lieferservice", "script": "res://missions/story/m02_delivery.gd", "giver": "diner", "reward": 1500,
		"desc": "Eine eilige Lieferung aus dem Hafen."},
	{"id": "m03", "title": "Schuldeneintreiber", "script": "res://missions/story/m03_debt.gd", "giver": "diner", "reward": 2000,
		"desc": "Vince schuldet Marco Geld."},
	{"id": "m04", "title": "Verfolgungsjagd", "script": "res://missions/story/m04_chase.gd", "giver": "diner", "reward": 3000,
		"desc": "Ein Kurier hat Marcos Paket gestohlen."},
	{"id": "m05", "title": "Eskorte", "script": "res://missions/story/m05_escort.gd", "giver": "landmark_grand_hotel", "reward": 4500,
		"desc": "Ein wichtiger Gast muss sicher zum Flughafen."},
	{"id": "m06", "title": "Auftragsmord", "script": "res://missions/story/m06_hit.gd", "giver": "diner", "reward": 6000,
		"desc": "Viktor Sorel steht Marco im Weg."},
	{"id": "m07", "title": "Gebrauchtwagen", "script": "res://missions/story/m07_steal.gd", "giver": "car_dealer", "reward": 5000,
		"desc": "Ein Kunde wünscht sich einen ganz bestimmten Sportwagen."},
	{"id": "m08", "title": "Der große Coup", "script": "res://missions/story/m08_heist.gd", "giver": "property_business", "reward": 25000,
		"desc": "Die Meridian Bank. Alles oder nichts."},
]
const SIDE := {
	"taxi": {"title": "Taxi-Schicht", "script": "res://missions/side/taxi.gd", "reward": 0, "desc": "Fahrgäste befördern."},
	"courier": {"title": "Kurierjob", "script": "res://missions/side/courier.gd", "reward": 0, "desc": "4 Pakete gegen die Uhr."},
	"vigilante": {"title": "Bürgerwehr", "script": "res://missions/side/vigilante.gd", "reward": 0, "desc": "Flüchtige Verdächtige stoppen."},
	"race": {"title": "Straßenrennen", "script": "res://missions/side/street_race.gd", "reward": 0, "desc": "Startgeld $500, Preisgeld bis $3000."},
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


func _ready() -> void:
	name = "Missions"
	world = GameWorld.instance
	world.missions = self
	Events.player_died.connect(func(): _fail_current("Du bist gestorben."))
	Events.player_busted.connect(func(): _fail_current("Du wurdest verhaftet."))
	_refresh_giver()
	_setup_races()
	_setup_postcards()


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


func start_side(key: String) -> bool:
	var s: Dictionary = SIDE.get(key, {})
	if s.is_empty():
		return false
	return _start(key, s["script"], s["title"], int(s["reward"]))


func _start(mid: String, script_path: String, t: String, rew: int) -> bool:
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
	m.setup(self)
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
		current.fail("Mission abgebrochen.")


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
	current = null
	current_id = ""
	m.queue_free()
	_refresh_giver()


func _on_failed_reason(_mid: String, reason: String) -> void:
	Events.big_message.emit("MISSION FEHLGESCHLAGEN", reason, 4.0)


# ------------------------------------------------------------------ races (markers)
func _setup_races() -> void:
	Events.mission_failed.connect(_on_failed_reason)
	for t in ["landmark_stadium", "landmark_convention", "fire_station"]:
		var p := world.data.nearest_poi(t, world.data.spawn)
		if p.is_empty():
			continue
		var m := InteractMarker.new()
		m.prompt = "E: Straßenrennen (Startgeld $500)"
		m.color = Color(0.3, 0.9, 1.0)
		m.interact_radius = 2.5
		m.on_interact = func(_pl): start_side("race")
		m.condition = func(_pl): return not is_active()
		world.add_child(m)
		m.global_position = (p["entrance_v"] as Vector3) + (p["facing_v"] as Vector3) * 5.0
		_race_markers.append(m)


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
			if is_instance_valid(m) and m.global_position.distance_to(pp) < 500.0:
				out.append({"pos": m.global_position, "icon": "R", "color": Color(0.3, 0.9, 1.0), "size": 8.0})
	return out


func serialize() -> Dictionary:
	return {"current": current_id}
