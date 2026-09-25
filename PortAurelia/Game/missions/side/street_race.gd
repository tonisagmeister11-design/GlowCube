extends Mission
## Straßenrennen: procedural sprint race over the road network against three AI drivers.
## Entry fee $500, prize money for the top three.

const FEE := 500
var racers: Array = []
var checkpoints := PackedVector3Array()
var _progress := {}
var _cur_mk: InteractMarker = null
var _cur_idx := -1


func run() -> void:
	var start := road_point(player.global_position, 20.0, 80.0)
	if start.is_empty():
		fail("Keine Rennstrecke gefunden.")
		return
	# route to a far point
	var goal := road_point(start["pos"], 900.0, 1500.0)
	if goal.is_empty():
		fail("Keine Rennstrecke gefunden.")
		return
	var ids := world.graph.route(start["lane"], goal["pos"], 6000)
	var pts := world.graph.route_points(ids, 10.0)
	var acc := 0.0
	for i in range(1, pts.size()):
		acc += pts[i].distance_to(pts[i - 1])
		if acc > 140.0:
			checkpoints.append(pts[i])
			acc = 0.0
	checkpoints.append(pts[pts.size() - 1])
	if checkpoints.size() < 3:
		fail("Keine Rennstrecke gefunden.")
		return
	objective("Fahr zur Startlinie.")
	var sp: Vector3 = start["pos"]
	var sd: Vector3 = start["dir"]
	if not await reach(sp, 8.0, true):
		return
	if not Game.player_data.add_money(-FEE, "race_fee"):
		fail("Du kannst dir das Startgeld ($%d) nicht leisten." % FEE)
		return
	# grid: opponents next to/behind the player
	var right := sd.cross(Vector3.UP).normalized()
	for i in 3:
		var p := sp + right * (3.5 if i == 0 else -3.5 if i == 1 else 0.0) - sd * (8.0 if i == 2 else 0.0)
		var v := spawn_vehicle(["sports", "supercar", "sports"][i], p, sd)
		v.set_kinematic(false)
		var drv := drive(v, Outfits.random("civilian", RandomNumberGenerator.new()))
		racers.append([v, drv])
		_progress[v] = 0
		if drv:
			drv.hold()
	_progress[player] = 0
	var car := player.vehicle as Vehicle
	fail_if(func(): return player.vehicle != car, "Du hast dein Fahrzeug verlassen.")
	for n in [3, 2, 1]:
		Events.big_message.emit(str(n), "", 0.9)
		AudioManager.play_ui("hover", 0.0)
		car.linear_velocity = Vector3.ZERO
		if not await wait(1.0):
			return
	Events.big_message.emit("LOS!", "", 1.0)
	AudioManager.play_ui("select", 0.0)
	for r in racers:
		if r[1]:
			(r[1] as TrafficDriver).respond_to(checkpoints[0])
			(r[1] as TrafficDriver).aggression = randf_range(1.0, 1.15)
	var finish_order: Array = []
	var ok := await until(func():
		# player checkpoints
		var pi: int = _progress[player]
		if pi != _cur_idx:
			_cur_idx = pi
			if is_instance_valid(_cur_mk):
				_cur_mk.queue_free()
			blip_list.clear()
			if pi < checkpoints.size():
				_cur_mk = checkpoint(checkpoints[pi], 10.0, Color(1.0, 0.85, 0.2), true)
				add_blip(checkpoints[pi], Color(1.0, 0.85, 0.2), "", true)
				Events.waypoint_set.emit(checkpoints[pi])
		if pi < checkpoints.size() and player.global_position.distance_to(checkpoints[pi]) < 12.0:
			_progress[player] = pi + 1
			AudioManager.play_ui("checkpoint", -4.0)
			if pi + 1 >= checkpoints.size():
				finish_order.append(player)
		# AI checkpoints
		for r in racers:
			var v: Vehicle = r[0]
			if not is_instance_valid(v) or v.destroyed or finish_order.has(v):
				continue
			var ai: int = _progress[v]
			if ai < checkpoints.size() and v.global_position.distance_to(checkpoints[ai]) < 18.0:
				_progress[v] = ai + 1
				if ai + 1 >= checkpoints.size():
					finish_order.append(v)
				elif r[1]:
					(r[1] as TrafficDriver).respond_to(checkpoints[ai + 1])
		var place := finish_order.find(player)
		Events.subtitle.emit("Checkpoint %d/%d · Platz %d" % [mini(_progress[player] + 1, checkpoints.size()), checkpoints.size(), _position()], 0.3)
		return place >= 0)
	if not ok:
		return
	var place := finish_order.find(player) + 1
	var prize: int = [0, 3000, 1500, 750][place] if place <= 3 else 0
	reward = prize
	Events.big_message.emit("PLATZ %d" % place, "Preisgeld: $%d" % prize if prize > 0 else "Kein Preisgeld", 4.0)
	for r in racers:
		if is_instance_valid(r[0]) and r[1]:
			(r[1] as TrafficDriver).resume_traffic()
	complete()


func _position() -> int:
	var mine: int = _progress[player]
	var d := player.global_position.distance_to(checkpoints[mini(mine, checkpoints.size() - 1)])
	var pos := 1
	for r in racers:
		var v: Vehicle = r[0]
		if not is_instance_valid(v):
			continue
		var ai: int = _progress[v]
		var ad := v.global_position.distance_to(checkpoints[mini(ai, checkpoints.size() - 1)])
		if ai > mine or (ai == mine and ad < d):
			pos += 1
	return pos
