extends Mission
## 8 · Der große Coup – storm the Meridian Bank: deal with the guards in the lobby,
## hold the vault until the crew has cracked it, grab the money bags, get out, escape
## the police and bring the rest of the loot to the safehouse.


func run() -> void:
	var bank := poi("bank")
	var door := poi_pos(bank, 2.0)
	objective("Triff die Crew vor der %s. Bring eine Schusswaffe mit." % bank["name"])
	if not await reach(door, 4.0):
		return
	if not player.weapons.has_ranged():
		Events.notify.emit("Marcos Crew gibt dir eine MP-Vector.", 3.0)
	player.weapons.give("smg", 180)
	player.weapons.equip("smg")
	var im := InteriorManager.get_manager()
	var inside := im != null and im.has_interior("bank")
	var vault := door
	if inside:
		await im.enter(bank, true)
		if not active:
			return
		vault = im.point("vault")
	if world.police:
		world.police.call("set_wanted", 3)
	Events.subtitle.emit("Marco: \"Los, los, los! Alle auf den Boden!\"", 3.0)
	# guards
	for i in 3:
		var gp: Vector3 = im.point("guard_%d" % i) if inside else door + Vector3(i * 2.0 - 2.0, 0.2, 0)
		var g := spawn_npc("gang", gp + Vector3.UP * 0.1, ["pistol", "shotgun", "pistol"][i],
			Outfits.random("worker", RandomNumberGenerator.new()))
		g.health.max_health = 120.0
		g.health.health = 120.0
		g.engage(player)
	fail_if(func(): return inside and not im.is_inside() and not _has_bags(), "Du hast die Bank ohne Beute verlassen.")
	var st := {"hold": 0.0}
	objective("Halte die Stellung am Tresor, bis die Crew ihn geöffnet hat.")
	var mk := checkpoint(vault, 3.0, Color(0.95, 0.3, 0.3))
	var ok := await until(func():
		if player.global_position.distance_to(vault) < 6.0:
			st["hold"] += get_process_delta_time()
		Events.subtitle.emit("Tresor: %d%%" % int(clampf(st["hold"] / 40.0, 0.0, 1.0) * 100.0), 0.3)
		return st["hold"] >= 40.0)
	if not ok:
		return
	mk.queue_free()
	AudioManager.play_3d("explosion", vault, -6.0)
	var bags := []
	for i in 3:
		var pk := Pickup.spawn(world, vault + Vector3(i * 0.9 - 0.9, 0.05, 0.8), "money", 12000)
		pk.lifetime = 900.0
		bags.append(pk)
	st["bags"] = bags
	objective("Schnapp dir die Geldsäcke!")
	if not await until(func(): return bags.all(func(b): return not is_instance_valid(b))):
		return
	_fail_checks.clear()
	if inside:
		objective("Verlasse die Bank!")
		if not await until(func(): return not im.is_inside()):
			return
	if world.police:
		world.police.call("set_wanted", 4)
	objective("Entkomme der Polizei!")
	if not await until(func(): return world.police == null or int(world.police.get("wanted_level")) == 0):
		return
	var sh := poi("safehouse")
	objective("Bring den Rest der Beute zum Safehouse.")
	if not await reach(poi_pos(sh, 2.0), 4.0):
		return
	Events.subtitle.emit("Marco: \"Das war's. Port Aurelia gehört uns.\"", 5.0)
	complete()


func _has_bags() -> bool:
	return false
