extends Mission
## 3 · Schuldeneintreiber – find Vince in Old Town, chase him down on foot and make him pay.


func run() -> void:
	var op := district_point("oldtown")
	var spot := sidewalk_near(op["pos"])
	objective("Finde Vince in der Altstadt.")
	if not await reach(spot, 30.0):
		return
	var vince := spawn_npc("civilian", spot + Vector3(2, 0.2, 2), "", Outfits.random("gang", RandomNumberGenerator.new()))
	vince.bravery = 0.0
	vince.health.max_health = 90.0
	vince.health.health = 90.0
	add_blip(vince, Color(0.95, 0.25, 0.25), "", true)
	fail_if(func(): return not is_instance_valid(vince) or vince.is_dead(), "Tote zahlen keine Schulden.")
	fail_if(func(): return is_instance_valid(vince) and vince.global_position.distance_to(player.global_position) > 160.0,
		"Vince ist entkommen.")
	Events.subtitle.emit("Vince: \"Oh nein... nicht du!\"", 3.0)
	objective("Schnapp dir Vince und prügel das Geld aus ihm heraus.")
	var ok := await until(func():
		if vince.state != NPC.S.KNOCKED and vince.state != NPC.S.FLEE and vince.health.health > 45.0:
			vince.flee_from(player.global_position, 30.0)
		return vince.health.health <= 45.0 or vince.state == NPC.S.KNOCKED)
	if not ok:
		return
	blip_list.clear()
	vince.hands_up(20.0)
	Events.subtitle.emit("Vince: \"Schon gut, schon gut! Hier ist alles!\"", 4.0)
	Pickup.spawn(world, vince.global_position + Vector3(0.8, 0, 0), "money", 800)
	await wait(2.0)
	complete()
