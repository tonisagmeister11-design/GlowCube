extends Mission
## Auftrag "Geldtransporter": stop an armoured cash van (shoot the engine or ram it until
## it breaks down), deal with the two guards, grab the money bags from the back and get
## away. Checkpoint after the bags are yours.

var van: Vehicle


func run() -> void:
	if stage < 1:
		var start := road_point(player.global_position, 250.0, 450.0)
		if start.is_empty():
			fail("Der Transporter ist nicht aufzufinden.")
			return
		var goal := road_point(start["pos"], 900.0, 1400.0)
		van = spawn_vehicle("delivery", start["pos"], start["dir"], Color(0.2, 0.22, 0.2))
		van.upgrades["armor"] = 3
		van.upgrades["tires"] = 1
		van.set_kinematic(false)
		var drv := drive(van)
		if drv:
			drv.respond_to(goal["pos"] if not goal.is_empty() else start["pos"] + start["dir"] * 1000.0)
			drv.cruise_factor = 0.8
		objective("Halte den Geldtransporter auf – schieß auf den Motor oder ramm ihn!")
		var b := add_blip(van, Color(1.0, 0.3, 0.3), "", true)
		var ok := await until(func():
			if is_instance_valid(van):
				Events.waypoint_set.emit(van.global_position)
			return is_instance_valid(van) and (van.engine_health < 150.0 or van.destroyed or van.ai_driver == null))
		if not ok:
			return
		remove_blip(b)
		if world.police:
			world.police.call("set_wanted", maxi(2, int(world.police.get("wanted_level"))))
		# guards jump out and fight
		var guards := []
		if drv and van.ai_driver != null:
			drv.abandon_vehicle(player)
		for i in 2:
			var g := spawn_npc("business", van.get_exit_point() + Vector3(1.5 * i, 0.1, 1.0), "smg")
			g.hostile = true
			g.health.max_health = 160.0
			g.health.health = 160.0
			g.engage(player)
			guards.append(g)
		objective("Schalte die Wachleute aus.")
		if not await until(func(): return guards.all(func(g): return not is_instance_valid(g) or g.is_dead())):
			return
		objective("Hol dir die Geldsäcke aus dem Transporter.")
		var back := van.global_position + van.global_basis.z * 3.4
		for i in 4:
			Pickup.spawn(world, back + van.global_basis.x * (-1.2 + i * 0.8), "money", randi_range(12000, 20000))
		var money0 := Game.player_data.money
		if not await until(func(): return Game.player_data.money >= money0 + 40000, 90.0):
			return
		if world.police:
			world.police.call("set_wanted", 3)
		set_checkpoint(1, back + van.global_basis.x * 6.0, "sports")
	else:
		if world.police:
			world.police.call("set_wanted", 3)
	objective("Häng die Polizei ab!")
	if not await until(func(): return world.police == null or int(world.police.get("wanted_level")) == 0):
		return
	Events.big_message.emit("SAUBER", "Das Geld gehört dir.", 3.0)
	complete()
