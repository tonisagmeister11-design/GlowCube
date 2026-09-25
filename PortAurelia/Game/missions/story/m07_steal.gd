extends Mission
## 7 · Gebrauchtwagen – steal a Stratos Vento supercar from the marina and deliver it
## to the Ironworks garage in good condition.


func run() -> void:
	var mp := district_point("marina")
	var car := spawn_vehicle("supercar", mp["pos"] + mp["dir"].cross(Vector3.UP) * 3.5, mp["dir"], Color(0.8, 0.05, 0.05))
	car.is_parked = true
	var b := add_blip(car, Color(0.3, 0.7, 1.0), "", true)
	objective("Stiehl den roten Stratos Vento an der Marina.")
	fail_if(func(): return not is_instance_valid(car) or car.destroyed, "Der Wagen wurde zerstört.")
	if not await until(func(): return player.vehicle == car):
		return
	remove_blip(b)
	# the alarm goes off
	car.honk(2.5)
	if world.police:
		world.police.call("set_wanted", maxi(1, int(world.police.get("wanted_level"))))
	fail_if(func(): return car.body_health < 500.0, "Der Wagen ist zu stark beschädigt.")
	var gar := poi("property_garage")
	objective("Bring den Wagen unbeschädigt zur %s. Häng vorher die Polizei ab!" % gar["name"])
	var dest := poi_pos(gar, 7.0)
	var mk := checkpoint(dest, 7.0)
	var bd := add_blip(dest, Color(1.0, 0.85, 0.2), "", true)
	Events.waypoint_set.emit(dest)
	var ok := await until(func():
		var near := player.vehicle == car and player.global_position.distance_to(dest) < 8.0
		if near and world.police and int(world.police.get("wanted_level")) > 0:
			Events.subtitle.emit("Nicht mit der Polizei im Nacken!", 0.5)
			return false
		return near)
	if not ok:
		return
	mk.queue_free()
	remove_blip(bd)
	complete()
