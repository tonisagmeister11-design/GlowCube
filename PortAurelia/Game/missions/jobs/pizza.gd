extends Mission
## Job "Pizzabote" (legal, small money): grab the scooter at the diner and bring 5 hot pizzas
## to the doors in town. The faster, the bigger the tip.


func run() -> void:
	var diner := poi("diner")
	var start := poi_pos(diner, 6.0) if not diner.is_empty() else player.global_position
	var v: Vehicle = player.vehicle if player.vehicle is Vehicle and (player.vehicle as Vehicle).type_id == "motorcycle" else null
	if v == null:
		var rp := road_point(start, 4.0, 40.0)
		v = spawn_vehicle("motorcycle", rp["pos"] if not rp.is_empty() else start, rp.get("dir", Vector3.FORWARD), Color(0.85, 0.1, 0.08))
		var b := add_blip(v, Color(0.3, 0.7, 1.0), "", true)
		Events.waypoint_set.emit(v.global_position)
		objective("Hol den Pizza-Roller am Diner.")
		if not await until(func(): return player.vehicle == v):
			return
		remove_blip(b)
	var total := 0
	for i in 5:
		var sp := (world.peds as PedManager).ped_graph.random_point(player.global_position, 200.0, 600.0, RandomNumberGenerator.new())
		if sp.is_empty():
			continue
		var dest: Vector3 = sp["pos"]
		time_left = 25.0 + player.global_position.distance_to(dest) / 11.0
		var t0 := time_left
		objective("Pizza %d/5 ausliefern – heiß bleibt sie nicht lange!" % (i + 1))
		if not await reach(dest, 5.0):
			return
		var tip := int(120.0 * clampf(time_left / t0, 0.0, 1.0))
		var pay := 220 + tip
		time_left = -1.0
		total += pay
		Game.player_data.add_money(pay, "job_pizza")
		Events.notify.emit("Geliefert! $220 + $%d Trinkgeld" % tip, 2.5)
	Events.big_message.emit("SCHICHT VORBEI", "Verdient: $%d" % total, 3.5)
	complete()
