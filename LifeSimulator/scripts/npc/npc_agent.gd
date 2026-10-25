class_name NpcAgent
extends Node3D
## Sichtbarer Fussgaenger (Humanoid mit LOD). Laeuft auf Gehwegen entlang
## einer PedPaths-Route, quert nur an Zebrastreifen/Ampelfurten (wartet bei
## Rot bzw. bis die Fahrbahn frei ist), sitzt, schlaeft (Obdachlose), wartet an
## Haltestellen. Interagierbar: "Ansprechen: <Name>" oeffnet ein Gespraech.

var crowd                           # CrowdManager
var person_id := -1                 # -1 = Passant ohne Datensatz
var display_name := "Passant"
var body: Node3D
var lod := -1
var path := PackedVector2Array()
var flags: Array = []
var idx := 0                        # aktuelles Teilstueck path[idx] -> path[idx + 1]
var pos2 := Vector2.ZERO
var dir2 := Vector2(0, 1)
var speed := 1.3
var state := "walk"                 # walk | wait_cross | idle | sit | sleep | wait_bus | talk | done
var state_t := 0.0
var end_action := "enter"           # enter | idle | sit | bus | despawn
var target_door := Vector2.INF
var bus_stop := -1
var side_off := 0.0
var homeless := false
var on_road := false
var _cw_wait := 0.0
var _update_acc := 0.0


func setup(p_crowd, appearance: Dictionary, age: float, body_kind: String, level: int) -> void:
	crowd = p_crowd
	lod = level
	if ResourceLoader.exists("res://scripts/character/humanoid.gd"):
		var hs: Script = load("res://scripts/character/humanoid.gd")
		if hs.has_method("create_lod"):
			body = hs.create_lod(appearance, age, body_kind, level)
		else:
			body = hs.new()
			body.setup(appearance, age, body_kind)
	else:
		body = _fallback_figure(appearance)
	add_child(body)
	add_to_group("interactable")
	add_to_group("npc")


func set_lod(level: int) -> void:
	if level == lod or body == null:
		return
	lod = level
	if body.has_method("set_lod"):
		body.set_lod(level)


func set_route(r: Dictionary, end: String) -> void:
	path = r.points
	flags = r.crossings
	idx = 0
	end_action = end
	if path.size() >= 2:
		state = "walk"
		pos2 = path[0]
	else:
		state = "idle"


func get_prompt(_player) -> String:
	if person_id < 0:
		return ""
	return "Ansprechen: %s" % display_name


func interact(player) -> void:
	if person_id < 0:
		return
	var prev := state
	state = "talk"
	if player is Node3D:
		var d := Vector2(player.global_position.x, player.global_position.z) - pos2
		if d.length() > 0.1:
			dir2 = d.normalized()
	_apply_pose(0.0)
	NpcDialogPanel.open(self, crowd.world, person_id, func(): state = prev if prev != "talk" else "idle")


func _fallback_figure(app: Dictionary) -> Node3D:
	var root := Node3D.new()
	var mi := MeshInstance3D.new()
	var cm := CapsuleMesh.new()
	cm.radius = 0.22
	cm.height = 1.7
	mi.mesh = cm
	mi.position.y = 0.85
	var m := StandardMaterial3D.new()
	var top = app.get("top", {})
	m.albedo_color = top.get("color", Color(0.4, 0.4, 0.5)) if typeof(top) == TYPE_DICTIONARY else Color(0.4, 0.4, 0.5)
	mi.material_override = m
	root.add_child(mi)
	return root


## Ein Schritt der Laufsimulation. far = guenstigere Aktualisierung.
func tick(delta: float) -> void:
	state_t += delta
	match state:
		"walk":
			_walk(delta)
		"wait_cross":
			_wait_cross(delta)
		"wait_bus":
			if crowd.traffic and bus_stop >= 0 and crowd.traffic.bus_at_stop(bus_stop) != null and state_t > 2.0:
				var b: VehicleAI = crowd.traffic.bus_at_stop(bus_stop)
				target_door = b.pos + CityModel.right_of(b.heading) * 1.6
				path = PackedVector2Array([pos2, target_door])
				flags = [-1]
				idx = 0
				end_action = "despawn"
				state = "walk"
		"sit", "sleep", "idle":
			if not homeless and end_action != "idle" and state_t > float(get_meta("dur", 30.0)):
				state = "done"
	_update_acc += delta
	_apply_pose(delta)


func _walk(delta: float) -> void:
	if idx >= path.size() - 1:
		_arrive()
		return
	var a: Vector2 = path[idx]
	var b: Vector2 = path[idx + 1]
	var f: int = flags[idx] if idx < flags.size() else -1
	# Vor einer Querung warten
	if f != -1 and pos2.distance_to(a) < 0.3 and not on_road:
		if not _may_cross(f, b):
			state = "wait_cross"
			_cw_wait = 0.0
			return
		on_road = true
	var to_b := b - pos2
	var d := to_b.length()
	var step := speed * delta
	if d <= step:
		pos2 = b
		idx += 1
		on_road = false
		if idx < flags.size() and int(flags[idx]) != -1:
			on_road = false
	else:
		dir2 = to_b / d
		pos2 += dir2 * step
	if on_road and f >= 0 and crowd.traffic:
		crowd.traffic.sim.set_crosswalk_busy(f, 1.2)


func _may_cross(f: int, b: Vector2) -> bool:
	var city: CityModel = crowd.world.city
	var tr = crowd.traffic
	if f >= 0:
		var cw: Dictionary = city.crosswalks[f]
		if cw.signal:
			if not city.ped_signal_green(int(cw.node), str(cw.axis), crowd.signal_time()):
				return false
		elif tr:
			tr.sim.set_crosswalk_busy(f, 1.5)
		if tr and not tr.sim.crosswalk_clear(f):
			return false
		return true
	# ungeregelt: nur bei freier Fahrbahn
	if tr:
		for v in tr.sim.vehicles_near((pos2 + b) * 0.5, 28.0):
			if v.v > 0.5:
				return false
	return true


func _wait_cross(delta: float) -> void:
	_cw_wait += delta
	var b: Vector2 = path[idx + 1]
	var f: int = flags[idx]
	var a: Vector2 = path[idx]
	if f != -1:
		var d := (b - a).normalized()
		if d != Vector2.ZERO:
			dir2 = d
	if _may_cross(f, b) or _cw_wait > 90.0:
		state = "walk"
		on_road = true


func _arrive() -> void:
	match end_action:
		"enter", "despawn":
			if target_door != Vector2.INF and pos2.distance_to(target_door) > 0.4:
				path = PackedVector2Array([pos2, target_door])
				flags = [-1]
				idx = 0
				target_door = Vector2.INF
				return
			state = "done"
		"sit":
			state = "sit"
			state_t = 0.0
		"bus":
			state = "wait_bus"
			state_t = 0.0
		_:
			state = "idle"
			state_t = 0.0


func _apply_pose(_delta: float) -> void:
	position = Vector3(pos2.x, 0.0, pos2.y) + Vector3(CityModel.right_of(dir2).x, 0, CityModel.right_of(dir2).y) * side_off
	rotation.y = atan2(dir2.x, dir2.y)
	if body == null:
		return
	var moving := state == "walk"
	if body.has_method("set_motion"):
		body.set_motion(speed if moving else 0.0)
	if body.has_method("set_action"):
		var act := "walk" if moving else "idle"
		match state:
			"sit":
				act = "sit"
			"sleep":
				act = "sleep"
			"talk":
				act = "talk"
		body.set_action(act)


## Hindernis fuer den Verkehr, solange der Fussgaenger auf der Fahrbahn ist.
func road_obstacle() -> Dictionary:
	if not on_road:
		return {}
	var v := dir2 * (speed if state == "walk" else 0.0)
	return {"pos": pos2, "r": 0.45, "vel": v}
