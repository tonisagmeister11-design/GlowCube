extends Mission
## Job "Autos auf Bestellung" (illegal, big money): the chop shop wants three specific car
## types. Find them in traffic, take them and drop them at the garage. Better cars pay more.

const PAY := {"compact": 2500, "sedan": 3500, "taxi": 3000, "suv": 5000, "pickup": 4500, "van": 3500,
	"luxury": 9000, "sports": 14000, "muscle": 15000, "supercar": 25000, "hypercar": 45000}


func run() -> void:
	var shop := poi("property_garage")
	if shop.is_empty():
		shop = poi("mechanic")
	var drop := poi_pos(shop, 7.0)
	var wanted: Array = []
	var pool := ["sedan", "compact", "suv", "pickup", "luxury", "taxi", "van"]
	pool.shuffle()
	wanted = pool.slice(0, 3)
	var list: String = ", ".join(wanted.map(func(t): return VehicleDefs.display_name(t)))
	Events.big_message.emit("BESTELLUNG", list, 4.0)
	var total := 0
	while not wanted.is_empty():
		objective("Besorge: %s  →  bring sie zur %s" % [", ".join(wanted.map(func(t): return VehicleDefs.display_name(t))),
			shop.get("name", "Werkstatt")])
		var mk := checkpoint(drop, 7.0)
		var b := add_blip(drop, Color(1.0, 0.85, 0.2), "$", true)
		var ok := await until(func():
			var v := player.vehicle as Vehicle
			return v != null and v.type_id in wanted and not v.player_owned and v.global_position.distance_to(drop) < 9.0)
		mk.queue_free()
		remove_blip(b)
		if not ok:
			return
		var v := player.vehicle as Vehicle
		if world.police and int(world.police.get("wanted_level")) > 0:
			Events.notify.emit("Nicht mit der Polizei im Nacken!", 2.5)
			await wait(2.0)
			continue
		var pay := int(PAY.get(v.type_id, 3000) * clampf(v.body_health / 1000.0, 0.4, 1.0))
		wanted.erase(v.type_id)
		total += pay
		player.exit_vehicle()
		await wait(0.4)
		v.queue_free()
		Game.player_data.add_money(pay, "job_chop")
		Events.big_message.emit("ABGELIEFERT", "%s · $%d" % [VehicleDefs.display_name(v.type_id), pay], 2.5)
	Events.big_message.emit("BESTELLUNG ERLEDIGT", "Verdient: $%d" % total, 3.5)
	complete()
