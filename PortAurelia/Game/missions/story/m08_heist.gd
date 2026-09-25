extends Mission
## 8 · Der große Coup – rob the Meridian Bank: hold out until the vault crew is done,
## grab the money bags, escape the police and bring the loot to the safehouse.


func run() -> void:
	var bank := poi("bank")
	var pos := poi_pos(bank, 2.0)
	objective("Triff die Crew vor der %s. Bring eine Schusswaffe mit." % bank["name"])
	if not await reach(pos, 5.0):
		return
	if not player.weapons.has_ranged():
		player.weapons.give("smg", 180)
		Events.notify.emit("Marcos Crew gibt dir eine MP-Vector.", 3.0)
	player.weapons.give("smg", 120)
	if world.police:
		world.police.call("set_wanted", 3)
	# guards inside the lobby come out
	for i in 3:
		var g := spawn_npc("gang", pos + (bank["facing_v"] as Vector3) * -1.0 + Vector3(i - 1.0, 0.3, 0), "pistol",
			Outfits.random("worker", RandomNumberGenerator.new()))
		g.role = "gang"
		g.engage(player)
	time_left = -1.0
	var st := {"hold": 0.0}
	objective("Halte die Stellung am Eingang, bis der Tresor offen ist.")
	var mk := checkpoint(pos, 8.0, Color(0.95, 0.3, 0.3))
	var ok := await until(func():
		if player.global_position.distance_to(pos) < 12.0:
			st["hold"] += get_process_delta_time()
		Events.subtitle.emit("Tresor: %d%%" % int(clampf(st["hold"] / 45.0, 0.0, 1.0) * 100.0), 0.3)
		return st["hold"] >= 45.0)
	if not ok:
		return
	mk.queue_free()
	# money bags
	var bags := []
	for i in 3:
		var bp := pos + (bank["facing_v"] as Vector3) * 2.0 + Vector3(i * 1.4 - 1.4, 0, 0)
		var pk := Pickup.spawn(world, bp, "money", 12000)
		pk.lifetime = 600.0
		bags.append(pk)
	objective("Schnapp dir die Geldsäcke!")
	if not await until(func(): return bags.all(func(b): return not is_instance_valid(b))):
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
