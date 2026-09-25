extends Mission
## 4 · Verfolgungsjagd – a courier flees in a sports car. Stop him and grab the package.


func run() -> void:
	var gar := poi("parking_garage")
	objective("Fahr zum Parkhaus %s." % gar["name"])
	if not await reach(poi_pos(gar, 8.0), 25.0):
		return
	var rp := road_point(player.global_position, 30.0, 90.0)
	var car := spawn_vehicle("sports", rp["pos"], rp["dir"], Color(0.95, 0.75, 0.05))
	var drv := drive(car, Outfits.random("gang", RandomNumberGenerator.new()))
	if drv == null:
		fail("Kein Fluchtweg gefunden.")
		return
	drv.mode = TrafficDriver.Mode.FLEE
	drv._mode_timer = 99999.0
	drv.aggression = 1.35
	add_blip(car, Color(0.95, 0.25, 0.25), "", true)
	fail_if(func(): return is_instance_valid(car) and car.global_position.distance_to(player.global_position) > 420.0,
		"Der Kurier ist entkommen.")
	Events.subtitle.emit("Marco: \"Der gelbe Wagen! Er hat unser Paket. Halt ihn auf!\"", 5.0)
	objective("Halte den gelben Sportwagen auf.")
	var ok := await until(func():
		if drv.mode != TrafficDriver.Mode.FLEE and drv.mode != TrafficDriver.Mode.ABANDONED:
			drv.mode = TrafficDriver.Mode.FLEE
			drv._mode_timer = 99999.0
		return car.destroyed or car.engine_health < 300.0 or car.body_health < 350.0 or drv.mode == TrafficDriver.Mode.ABANDONED)
	if not ok:
		return
	_fail_checks.clear()
	blip_list.clear()
	if drv.mode != TrafficDriver.Mode.ABANDONED and not car.destroyed:
		drv.abandon_vehicle(player)
	var pk := car.global_position + car.global_basis.x * 2.5
	objective("Nimm das Paket an dich.")
	if not await reach(pk, 2.5):
		return
	complete()
