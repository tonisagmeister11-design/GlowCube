extends Mission
## Straßenrennen: sprint race over the road network against three AI racers (RaceDriver).
## Three classes, picked by the start marker (params "tier"):
##   0 Street (sports / muscle), 1 Pro (supercars), 2 Hypercar league.
## Entry fee up front, prize money for the top three.

const TIERS := [
	{"name": "Street", "fee": 1000, "prizes": [9000, 4000, 2000], "dist": [900.0, 1400.0], "cars": ["sports", "muscle", "sports"],
		"skill": [0.93, 1.0]},
	{"name": "Pro", "fee": 5000, "prizes": [35000, 15000, 7000], "dist": [1500.0, 2200.0], "cars": ["supercar", "muscle", "supercar"],
		"skill": [0.97, 1.04]},
	{"name": "Hypercar-Liga", "fee": 20000, "prizes": [140000, 60000, 25000], "dist": [2000.0, 3000.0],
		"cars": ["hypercar", "supercar", "hypercar"], "skill": [1.0, 1.08]},
]

var tier := 0
var racers: Array = []           # [Vehicle, RaceDriver]
var line := PackedVector3Array()
var checkpoints := PackedVector3Array()
var cp_idx := PackedInt32Array()  # line index of every checkpoint
var _player_cp := 0
var _player_idx := 0
var _cur_mk: InteractMarker = null


func run() -> void:
	tier = clampi(int(_params.get("tier", 0)), 0, TIERS.size() - 1)
	var t: Dictionary = TIERS[tier]
	var d: Array = t["dist"]
	for attempt in 8:
		var start := road_point(player.global_position, 10.0, 90.0)
		if start.is_empty():
			continue
		var goal := road_point(start["pos"], d[0], d[1])
		if goal.is_empty():
			continue
		var ids := world.graph.route(start["lane"], goal["pos"], 8000)
		line = world.graph.route_points(ids, 8.0)
		if line.size() >= 60:
			break
	if line.size() < 30:
		fail("Keine Rennstrecke gefunden.")
		return
	var acc := 0.0
	for i in range(1, line.size()):
		acc += line[i].distance_to(line[i - 1])
		if acc > 160.0:
			checkpoints.append(line[i])
			cp_idx.append(i)
			acc = 0.0
	checkpoints.append(line[line.size() - 1])
	cp_idx.append(line.size() - 1)
	objective("Fahr zur Startlinie (%s-Rennen)." % t["name"])
	var sp: Vector3 = line[6]
	var sd: Vector3 = (line[8] - line[6]).normalized()
	if not await reach(sp, 8.0, true):
		return
	if not Game.player_data.add_money(-int(t["fee"]), "race_fee"):
		fail("Du kannst dir das Startgeld ($%d) nicht leisten." % int(t["fee"]))
		return
	var car := player.vehicle as Vehicle
	# 2 x 2 grid along the lane: one racer beside the player, two in the second row
	var right := sd.cross(Vector3.UP).normalized()
	var row2: Vector3 = line[3] - sp
	car.global_transform = Transform3D(Basis.looking_at(sd, Vector3.UP), sp - right * 1.6 + Vector3.UP * 0.6)
	car.linear_velocity = Vector3.ZERO
	var slots := [right * 1.6, row2 + right * 1.6, row2 - right * 1.6]
	var sk: Array = t["skill"]
	for i in 3:
		var v := spawn_vehicle(t["cars"][i], sp + slots[i], sd)
		v.set_kinematic(false)
		var tdrv := drive(v, Outfits.random("civilian", RandomNumberGenerator.new()))
		if tdrv:
			(world.traffic as TrafficManager).drivers.erase(v)   # the race AI takes over
		var rd := RaceDriver.new()
		v.add_child(rd)
		rd.setup(v, line, randf_range(sk[0], sk[1]))
		rd.rival = player
		racers.append([v, rd])
	# thinner traffic while the race is on
	(world.traffic as TrafficManager).event_density = 0.45
	tree_exiting.connect(func(): if world.traffic: (world.traffic as TrafficManager).event_density = 1.0)
	fail_if(func(): return player.vehicle != car, "Du hast dein Fahrzeug verlassen.")
	fail_if(func(): return player.global_position.distance_to(line[_player_idx]) > 260.0, "Du hast die Strecke verlassen.")
	AudioManager.play_line("racer", false, racers[0][0].global_position)
	for n in [3, 2, 1]:
		Events.big_message.emit(str(n), "", 0.9)
		AudioManager.play_ui("hover", 0.0)
		for k in 10:
			car.linear_velocity = Vector3.ZERO
			if not await wait(0.1):
				return
	Events.big_message.emit("LOS!", "", 1.0)
	AudioManager.play_ui("select", 0.0)
	for r in racers:
		(r[1] as RaceDriver).holding = false
	var finish_order: Array = []
	_show_checkpoint()
	var ok := await until(func():
		_player_idx = _closest(player.global_position, _player_idx)
		if _player_cp < checkpoints.size() and player.global_position.distance_to(checkpoints[_player_cp]) < 14.0:
			_player_cp += 1
			AudioManager.play_ui("checkpoint", -4.0)
			if _player_cp >= checkpoints.size():
				finish_order.append(player)
			else:
				_show_checkpoint()
		for r in racers:
			var v: Vehicle = r[0]
			var rd: RaceDriver = r[1]
			rd.rival_idx = _player_idx
			if not is_instance_valid(v) or v.destroyed or finish_order.has(v):
				continue
			if rd.idx >= line.size() - 3:
				finish_order.append(v)
				rd.finished = true
		Events.subtitle.emit("Checkpoint %d/%d   ·   Platz %d/4" % [mini(_player_cp + 1, checkpoints.size()),
			checkpoints.size(), _position()], 0.3)
		return finish_order.has(player))
	if not ok:
		return
	var place := finish_order.find(player) + 1
	var prizes: Array = t["prizes"]
	var prize: int = prizes[place - 1] if place <= 3 else 0
	reward = prize
	Events.big_message.emit("PLATZ %d" % place, ("Preisgeld: $%d" % prize) if prize > 0 else "Kein Preisgeld", 4.0)
	Game.player_data.stat_add("races_won" if place == 1 else "races_finished", 1)
	complete()


func _show_checkpoint() -> void:
	if is_instance_valid(_cur_mk):
		_cur_mk.queue_free()
	blip_list.clear()
	var last := _player_cp == checkpoints.size() - 1
	var col := Color(1.0, 0.25, 0.2) if last else Color(1.0, 0.85, 0.2)
	_cur_mk = checkpoint(checkpoints[_player_cp], 12.0, col, true)
	add_blip(checkpoints[_player_cp], col, "", true)
	if _player_cp + 1 < checkpoints.size():
		add_blip(checkpoints[_player_cp + 1], Color(1.0, 0.85, 0.2, 0.5), "", false)
	Events.waypoint_set.emit(checkpoints[_player_cp])


func _closest(p: Vector3, from: int) -> int:
	var best := from
	var bd := INF
	for i in range(maxi(from - 3, 0), mini(from + 20, line.size())):
		var d := line[i].distance_squared_to(p)
		if d < bd:
			bd = d
			best = i
	return best


func _position() -> int:
	var pos := 1
	for r in racers:
		var v: Vehicle = r[0]
		if is_instance_valid(v) and not v.destroyed and (r[1] as RaceDriver).idx > _player_idx:
			pos += 1
	return pos
