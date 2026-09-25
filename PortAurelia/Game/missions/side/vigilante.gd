extends Mission
## Bürgerwehr: in a police car, hunt down fleeing criminals (level after level).

var level := 0


func run() -> void:
	var car: Vehicle = player.vehicle if player.vehicle is Vehicle and (player.vehicle as Vehicle).is_police else null
	if car == null:
		var rp := road_point(player.global_position, 8.0, 50.0)
		car = spawn_vehicle("police", rp["pos"], rp["dir"])
		add_blip(car, Color(0.3, 0.45, 1.0), "", true)
		objective("Steig in den Streifenwagen.")
		if not await until(func(): return player.vehicle == car):
			return
		blip_list.clear()
	car.set_siren(true)
	var total := 0
	while active:
		level += 1
		var rp := road_point(player.global_position, 120.0, 260.0)
		if rp.is_empty():
			await wait(1.0)
			continue
		var crook := spawn_vehicle(["sedan", "sports", "suv", "compact"][randi() % 4], rp["pos"], rp["dir"])
		var drv := drive(crook, Outfits.random("gang", RandomNumberGenerator.new()))
		if drv == null:
			continue
		drv.mode = TrafficDriver.Mode.FLEE
		drv._mode_timer = 99999.0
		drv.aggression = 1.1 + level * 0.05
		var b := add_blip(crook, Color(0.95, 0.2, 0.2), "", true)
		objective("Stufe %d: Stoppe den flüchtigen Verdächtigen." % level)
		time_left = 150.0
		var ok := await until(func():
			if player.vehicle != car and player.global_position.distance_to(car.global_position) > 60.0:
				return false
			return crook.destroyed or crook.engine_health < 300.0 or crook.body_health < 350.0 \
				or drv.mode == TrafficDriver.Mode.ABANDONED)
		if not ok:
			return
		time_left = -1.0
		remove_blip(b)
		if drv.mode != TrafficDriver.Mode.ABANDONED and not crook.destroyed:
			drv.abandon_vehicle(player)
		var pay := 200 * level
		total += pay
		Game.player_data.add_money(pay, "vigilante")
		Events.notify.emit("Verdächtiger gestoppt! +$%d" % pay, 3.0)
		if not await wait(3.0):
			return
