extends Mission
## 1 · Neuanfang – tutorial: get a car, visit the mechanic, meet Marco at the diner.


func run() -> void:
	var rp := road_point(player.global_position, 12.0, 70.0)
	if rp.is_empty():
		rp = {"pos": player.global_position + Vector3(6, 0, 0), "dir": Vector3.FORWARD}
	var car := spawn_vehicle("sedan", rp["pos"], rp["dir"], Color(0.1, 0.25, 0.5))
	add_blip(car, Color(0.3, 0.7, 1.0), "", true)
	fail_if(func(): return not is_instance_valid(car) or car.destroyed, "Der Wagen wurde zerstört.")
	Events.subtitle.emit("Marco: \"Willkommen zurück in Port Aurelia. Ich hab dir einen Wagen hingestellt.\"", 5.0)
	objective("Steig in den blauen Wagen (F).")
	if not await until(func(): return player.vehicle == car):
		return
	blip_list.clear()
	var mech := poi("mechanic")
	objective("Fahr zur Werkstatt %s." % mech["name"])
	if not await reach(poi_pos(mech, 7.0), 7.0, true):
		return
	Events.notify.emit("In Werkstätten kannst du reparieren, neu lackieren (verliert Fahndung) und tunen.", 6.0)
	if not await wait(2.0):
		return
	var diner := poi("diner")
	objective("Triff Marco im %s." % diner["name"])
	if not await reach(poi_pos(diner, 3.0), 4.0):
		return
	Events.subtitle.emit("Marco: \"Gut siehst du aus. Ich hab Arbeit für dich – ruf mich an, wenn du bereit bist.\"", 6.0)
	await wait(2.0)
	complete()
