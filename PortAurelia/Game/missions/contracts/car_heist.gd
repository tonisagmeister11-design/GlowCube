extends Mission
## Auftrag "Heiße Ware": a brand-new Zenith R hypercar is being delivered to a dealer - a
## truck leads the convoy, a security SUV follows. Intercept it, pull the driver out and
## bring the car to a buyer in the hills who pays $200,000 for it (less if it is damaged).
## Checkpoint after the car is yours (restart in the hypercar).

const PRICE := 200000
var car: Vehicle


func run() -> void:
	if stage < 1:
		var start := road_point(player.global_position, 280.0, 520.0)
		if start.is_empty():
			fail("Der Transport ist nicht aufzufinden.")
			return
		var goal := road_point(start["pos"], 900.0, 1500.0)
		var sp: Vector3 = start["pos"]
		var sd: Vector3 = start["dir"]
		var truck := spawn_vehicle("truck", sp + sd * 14.0, sd)
		car = spawn_vehicle("hypercar", sp, sd, Color(0.95, 0.95, 0.97))
		var escort := spawn_vehicle("suv", sp - sd * 12.0, sd, Color(0.05, 0.05, 0.06))
		for v in [truck, car, escort]:
			v.set_kinematic(false)
		var d_truck := drive(truck)
		var d_car := drive(car)
		var d_esc := drive(escort)
		var dest: Vector3 = goal["pos"] if not goal.is_empty() else sp + sd * 1200.0
		for d in [d_truck, d_car, d_esc]:
			if d:
				d.respond_to(dest)
				d.cruise_factor = 0.75
		objective("Fang den weißen Zenith R ab, bevor er beim Händler ankommt!")
		var b := add_blip(car, Color(1.0, 0.3, 0.3), "", true)
		fail_if(func(): return not is_instance_valid(car) or car.destroyed, "Der Zenith R wurde zerstört.")
		var ok := await until(func():
			if is_instance_valid(car):
				Events.waypoint_set.emit(car.global_position)
			if car.global_position.distance_to(dest) < 25.0 and player.vehicle != car:
				fail("Der Wagen ist beim Händler angekommen.")
				return false
			return player.vehicle == car)
		if not ok:
			return
		remove_blip(b)
		# the security team comes after you
		if d_esc and is_instance_valid(escort):
			d_esc.pursue(player)
			for i in 2:
				var g := spawn_npc("business", escort.global_position + Vector3(2.0 * i, 0.1, 2.0), "pistol")
				g.hostile = true
				g.engage(player)
		if world.police:
			world.police.call("set_wanted", maxi(2, int(world.police.get("wanted_level"))))
		set_checkpoint(1, car.global_position, "hypercar", car.global_rotation.y)
	else:
		car = _params.get("checkpoint_vehicle") if _params.get("checkpoint_vehicle") is Vehicle else null
		if car == null:
			fail("Kein Wagen.")
			return
		car.set_paint(Color(0.95, 0.95, 0.97))
		if world.police:
			world.police.call("set_wanted", 2)
	fail_if(func(): return not is_instance_valid(car) or car.destroyed, "Der Zenith R wurde zerstört.")
	fail_if(func(): return car.body_health < 250.0, "Der Wagen ist Schrott – der Käufer springt ab.")
	var villa := poi("property_villa", car.global_position + Vector3(0, 0, -1500))
	var dest2 := poi_pos(villa, 8.0) if not villa.is_empty() else car.global_position + Vector3(1200, 0, 0)
	var buyer := spawn_npc("business", dest2 + Vector3(3, 0.1, 0))
	buyer._set_state(NPC.S.WORK)
	buyer._timer = 1e9
	objective("Bring den Zenith R zum Käufer (%s). Er zahlt bis zu $%d – aber nur ohne Polizei." %
		[villa.get("name", "Hills"), PRICE])
	var mk := checkpoint(dest2, 8.0)
	var bd := add_blip(dest2, Color(1.0, 0.85, 0.2), "$", true)
	Events.waypoint_set.emit(dest2)
	var ok2 := await until(func():
		var near := player.vehicle == car and car.global_position.distance_to(dest2) < 9.0
		if near and world.police and int(world.police.get("wanted_level")) > 0:
			Events.subtitle.emit("Käufer: \"Erst die Polizei abschütteln!\"", 0.5)
			return false
		return near)
	if not ok2:
		return
	mk.queue_free()
	remove_blip(bd)
	var cond := clampf(car.body_health / 1000.0, 0.5, 1.0)
	reward = int(round(PRICE * cond / 1000.0)) * 1000
	buyer.say("buyer")
	Events.big_message.emit("VERKAUFT", "Zustand %d %% – $%d" % [int(cond * 100.0), reward], 4.0)
	player.exit_vehicle()
	complete()
