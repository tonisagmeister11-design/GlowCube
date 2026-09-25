extends Mission
## Taxi-Schicht: pick up passengers and drive them to their destination. The fare grows
## with distance, fast rides earn a tip. The shift ends when you leave the taxi.

var earned := 0
var fares := 0


func run() -> void:
	var taxi: Vehicle = player.vehicle if player.vehicle is Vehicle and (player.vehicle as Vehicle).type_id == "taxi" else null
	if taxi == null:
		var rp := road_point(player.global_position, 8.0, 50.0)
		taxi = spawn_vehicle("taxi", rp["pos"], rp["dir"])
		add_blip(taxi, Color(0.95, 0.85, 0.2), "", true)
		objective("Steig in das Taxi.")
		if not await until(func(): return player.vehicle == taxi):
			return
		blip_list.clear()
	fail_if(func(): return taxi.destroyed, "Das Taxi ist Schrott.")
	var st := {"out": 0.0}
	fail_if(func():
		if player.vehicle != taxi:
			st["out"] += get_process_delta_time()
		else:
			st["out"] = 0.0
		return st["out"] > 12.0, "Schicht beendet.")
	while active:
		var sp := (world.peds as PedManager).ped_graph.random_point(player.global_position, 60.0, 220.0, RandomNumberGenerator.new())
		if sp.is_empty():
			await wait(1.0)
			continue
		var fare_npc := spawn_npc("civilian", sp["pos"])
		fare_npc._set_state(NPC.S.IDLE)
		fare_npc._timer = 9999.0
		var b := add_blip(fare_npc, Color(0.3, 0.8, 1.0), "", true)
		Events.waypoint_set.emit(fare_npc.global_position)
		objective("Fahrgast abholen (Fahrgast %d)." % (fares + 1))
		if not await until(func():
				return is_instance_valid(fare_npc) and taxi.global_position.distance_to(fare_npc.global_position) < 8.0 \
					and taxi.speed() < 1.5 and player.vehicle == taxi):
			return
		remove_blip(b)
		var outfit := fare_npc.outfit
		fare_npc.queue_free()
		var dests := world.data.pois.filter(func(p): return (p["entrance_v"] as Vector3).distance_to(taxi.global_position) > 350.0 \
			and (p["entrance_v"] as Vector3).distance_to(taxi.global_position) < 1600.0)
		if dests.is_empty():
			continue
		var d: Dictionary = dests[randi() % dests.size()]
		var dist := taxi.global_position.distance_to(d["entrance_v"])
		time_left = 30.0 + dist / 10.0
		var start_t := time_left
		objective("Bring den Fahrgast zu: %s" % d["name"])
		if not await reach(poi_pos(d, 6.0), 9.0, true):
			return
		var fare := 25 + int(dist / 12.0)
		var tip := int(fare * clampf(time_left / start_t, 0.0, 0.6))
		time_left = -1.0
		Game.player_data.add_money(fare + tip, "taxi")
		earned += fare + tip
		fares += 1
		Events.notify.emit("Fahrpreis $%d + Trinkgeld $%d" % [fare, tip], 3.0)
		var drop := spawn_npc("civilian", taxi.get_exit_point(), "", outfit)
		drop.persistent = false
		drop.start_walking()
		if fares % 5 == 0:
			Game.player_data.add_money(250, "taxi_bonus")
			Events.notify.emit("Bonus für 5 Fahrten: $250", 3.0)


func fail(reason := "") -> void:
	# leaving the taxi simply ends the shift – that's a success if anything was earned
	if fares > 0 and reason == "Schicht beendet.":
		reward = 0
		complete()
		Events.notify.emit("Taxi-Schicht beendet: %d Fahrten, $%d verdient." % [fares, earned], 5.0)
		return
	super(reason)
