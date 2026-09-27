extends Mission
## Auftrag "Juwelen für Dante": rob the jewelry store (threaten the jeweler or smash the
## display cases), shake off the police and bring the stones to the fence Dante, who pays
## the contract fee on top of what you grabbed.
## Checkpoint after the robbery (restart outside the store with a getaway car).

var store := {}


func run() -> void:
	store = poi("jewelry")
	if store.is_empty():
		fail("Kein Juwelier gefunden.")
		return
	var im := InteriorManager.get_manager()
	var sid := str(int(store["id"]))
	if stage < 1:
		# fresh stones: the store has restocked for this job
		var robbed: Dictionary = Game.player_data.world_state.get("robbed", {})
		robbed.erase(sid)
		objective("Raube %s aus. Bedrohe den Juwelier oder zerschlag die Vitrinen." % store["name"])
		var b := add_blip(poi_pos(store, 2.0), Color(0.75, 0.85, 1.0), "J", true)
		Events.waypoint_set.emit(poi_pos(store, 2.0))
		var ok := await until(func():
			var r: Dictionary = Game.player_data.world_state.get("robbed", {})
			if not r.has(sid):
				return false
			# done once the till is empty / the cases are smashed and you are back outside
			return im == null or not im.is_inside() or (not im._rob.is_empty() and bool(im._rob.get("done", false))))
		if not ok:
			return
		remove_blip(b)
		if im and im.is_inside():
			objective("Schnapp dir die Beute und raus aus dem Laden!")
			if not await until(func(): return not im.is_inside()):
				return
		var out := poi_pos(store, 6.0)
		set_checkpoint(1, out, "sports", atan2(-(store["facing_v"] as Vector3).x, -(store["facing_v"] as Vector3).z))
	else:
		if world.police:
			world.police.call("set_wanted", 3)
	# stage 1: deliver to the fence
	var dest_poi := poi("property_garage", poi_pos(store) + Vector3(900, 0, 600))
	var dest := poi_pos(dest_poi, 6.0) if not dest_poi.is_empty() else poi_pos(store) + Vector3(600, 0, 0)
	var dante := spawn_npc("business", dest + Vector3(2, 0.1, 0))
	dante._set_state(NPC.S.WORK)
	dante._timer = 1e9
	var car := spawn_vehicle("luxury", dest + Vector3(-3, 0, 2), Vector3.FORWARD, Color(0.05, 0.05, 0.06))
	car.is_parked = true
	objective("Bring die Juwelen zu Dante (%s). Häng vorher die Polizei ab!" % (dest_poi.get("name", "Treffpunkt")))
	var mk := checkpoint(dest, 6.0)
	var bd := add_blip(dest, Color(1.0, 0.85, 0.2), "$", true)
	Events.waypoint_set.emit(dest)
	fail_if(func(): return not is_instance_valid(dante) or dante.is_dead(), "Dante ist tot – kein Käufer mehr.")
	var ok2 := await until(func():
		var near := player.global_position.distance_to(dest) < 7.0
		if near and world.police and int(world.police.get("wanted_level")) > 0:
			Events.subtitle.emit("Dante: \"Nicht mit den Bullen im Schlepptau!\"", 0.5)
			return false
		return near)
	if not ok2:
		return
	mk.queue_free()
	remove_blip(bd)
	dante.say("buyer")
	Events.big_message.emit("DEAL", "Dante zahlt für die Juwelen.", 3.0)
	complete()
