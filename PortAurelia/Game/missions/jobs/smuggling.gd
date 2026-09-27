extends Mission
## Job "Schmuggel" (illegal, big money): pick up a hot package at the harbour and drive it
## to a buyer across town. Police patrols along the way: if a patrol spots you with the
## goods you are wanted - lose them before the handover.

const PAY := 18000


func run() -> void:
	var pick := district_point("harbor")
	if pick.is_empty():
		pick = road_point(player.global_position, 300.0, 700.0)
	var ppos: Vector3 = pick["pos"]
	objective("Hol das Paket am Hafen ab.")
	var pkg := Pickup.spawn(world, ppos + (pick["dir"] as Vector3).cross(Vector3.UP) * 4.0, "armor", 0)
	pkg.lifetime = 9999.0
	_spawned.append(pkg)
	if not await reach(ppos, 8.0):
		return
	if is_instance_valid(pkg):
		pkg.queue_free()
	Events.notify.emit("Paket an Bord. Fahr vorsichtig – die Polizei kontrolliert.", 3.0)
	var drop := road_point(ppos, 1200.0, 1900.0)
	if drop.is_empty():
		drop = road_point(ppos, 600.0, 1200.0)
	var dpos: Vector3 = drop["pos"]
	# two patrols along the route
	for i in 2:
		var mid := ppos.lerp(dpos, 0.35 + i * 0.3)
		var rp := road_point(mid, 0.0, 120.0)
		if rp.is_empty():
			continue
		var cop_car := spawn_vehicle("police", rp["pos"], rp["dir"])
		cop_car.is_parked = true
		var cop := spawn_npc("cop", rp["pos"] + (rp["dir"] as Vector3).cross(Vector3.UP) * 3.0, "pistol")
		cop._set_state(NPC.S.IDLE)
		cop._timer = 1e9
		cop.set_meta("patrol", true)
	objective("Bring das Paket zum Käufer. Keine Polizei beim Treffen!")
	var mk := checkpoint(dpos, 7.0)
	var bd := add_blip(dpos, Color(1.0, 0.85, 0.2), "$", true)
	Events.waypoint_set.emit(dpos)
	var ok := await until(func():
		# a patrol that sees you nearby calls it in
		for n in _spawned:
			if n is NPC and is_instance_valid(n) and (n as NPC).has_meta("patrol") and not (n as NPC).is_dead():
				if (n as Node3D).global_position.distance_to(player.global_position) < 22.0 and world.police \
						and int(world.police.get("wanted_level")) == 0:
					(n as NPC).remove_meta("patrol")
					world.police.call("set_wanted", 2)
					Events.notify.emit("Eine Streife hat dich erkannt!", 2.5)
		var near := player.global_position.distance_to(dpos) < 8.0
		if near and world.police and int(world.police.get("wanted_level")) > 0:
			Events.subtitle.emit("Käufer: \"Erst die Bullen loswerden!\"", 0.5)
			return false
		return near)
	if not ok:
		return
	mk.queue_free()
	remove_blip(bd)
	reward = PAY
	complete()
