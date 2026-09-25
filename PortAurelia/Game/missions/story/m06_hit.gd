extends Mission
## 6 · Auftragsmord – eliminate Viktor Sorel at the Crestline villa, guarded by his crew,
## then lose the police.


func run() -> void:
	var villa := poi("property_villa")
	var pos := poi_pos(villa, 4.0)
	objective("Fahr zur Villa in Crestline.")
	if not await reach(pos, 60.0):
		return
	var rng := RandomNumberGenerator.new()
	rng.randomize()
	var target := spawn_npc("business", pos + Vector3(1.5, 0.3, 0.5), "pistol")
	target.bravery = 0.0
	target.health.max_health = 120.0
	target.health.health = 120.0
	add_blip(target, Color(0.95, 0.2, 0.2), "", true)
	var guards := []
	for i in 4:
		var a := TAU * i / 4.0
		var g := spawn_npc("gang", pos + Vector3(cos(a) * 6.0, 0.3, sin(a) * 6.0), ["pistol", "smg", "pistol", "shotgun"][i])
		g.health.max_health = 110.0
		g.health.health = 110.0
		guards.append(g)
	objective("Schalte Viktor Sorel aus.")
	fail_if(func(): return is_instance_valid(target) and not target.is_dead() \
		and target.global_position.distance_to(player.global_position) > 250.0, "Sorel ist entkommen.")
	var st := {"alerted": false}
	var ok := await until(func():
		if not st["alerted"] and player.global_position.distance_to(pos) < 30.0:
			st["alerted"] = true
			for g in guards:
				if is_instance_valid(g):
					(g as NPC).engage(player)
		return not is_instance_valid(target) or target.is_dead())
	if not ok:
		return
	_fail_checks.clear()
	blip_list.clear()
	if world.police:
		world.police.call("set_wanted", maxi(2, int(world.police.get("wanted_level"))))
	objective("Hänge die Polizei ab.")
	if not await until(func(): return world.police == null or int(world.police.get("wanted_level")) == 0):
		return
	complete()
