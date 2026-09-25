extends Mission
## Kurierjob: deliver parcels to four addresses in a row, each against the clock.


func run() -> void:
	var v: Vehicle = player.vehicle if player.vehicle is Vehicle else null
	if v == null:
		var rp := road_point(player.global_position, 8.0, 50.0)
		v = spawn_vehicle("motorcycle" if randf() < 0.5 else "delivery", rp["pos"], rp["dir"], Color(0.95, 0.45, 0.1))
		add_blip(v, Color(0.3, 0.7, 1.0), "", true)
		objective("Steig in das Kurierfahrzeug.")
		if not await until(func(): return player.vehicle == v):
			return
		blip_list.clear()
	var pay := 0
	for i in 4:
		var sp := (world.peds as PedManager).ped_graph.random_point(player.global_position, 250.0, 700.0, RandomNumberGenerator.new())
		if sp.is_empty():
			continue
		var dest: Vector3 = sp["pos"]
		time_left = 25.0 + player.global_position.distance_to(dest) / 12.0
		objective("Paket %d/4 zustellen." % (i + 1))
		if not await reach(dest, 6.0):
			return
		pay += 150 + int(time_left * 4.0)
		time_left = -1.0
		Events.notify.emit("Zugestellt! Bisher: $%d" % pay, 2.0)
	reward = pay
	complete()
