extends Node
## Headless test for update 2.4: combat balance (player health, damage, cops missing), save on
## exit, T summon, kerbs, reverse, drive-by weapon switching/reload, parked cars at night and
## pedestrians talking / picking fights.
##   godot --headless --path Game res://tests/update4_test.tscn

var world: GameWorld
var results := []


func _ready() -> void:
	Game.player_data = PlayerData.new()
	Game.pending_slot = -2
	world = load("res://scenes/world.tscn").instantiate()
	add_child(world)
	await Events.world_ready
	await wait(1.0)
	await _run()
	var failed := results.filter(func(r): return not r[1]).size()
	print("=== %d checks, %d failed ===" % [results.size(), failed])
	get_tree().quit(1 if failed > 0 else 0)


func check(n: String, ok: bool, info := "") -> void:
	results.append([n, ok])
	print(("PASS " if ok else "FAIL ") + n + ("  (" + info + ")" if info != "" else ""))


func wait(t: float) -> void:
	await get_tree().create_timer(t).timeout


func _floor(center: Vector3, size: Vector3) -> StaticBody3D:
	var b := StaticBody3D.new()
	var cs := CollisionShape3D.new()
	var bs := BoxShape3D.new()
	bs.size = size
	cs.shape = bs
	b.add_child(cs)
	add_child(b)
	b.global_position = center - Vector3(0, size.y * 0.5, 0)
	return b


func _run() -> void:
	var p := world.player as Player
	await _test_balance(p)
	p.health.invulnerable = true
	await _test_cops_miss(p)
	await _test_kerb_and_reverse(p)
	await _test_driveby(p)
	await _test_summon_key(p)
	await _test_save_on_exit(p)
	await _test_parked_night(p)
	await _test_street_life(p)
	await _test_saving_always(p)
	await _test_chase_music(p)


func _test_balance(p: Player) -> void:
	check("player has 250 health", is_equal_approx(p.health.max_health, 250.0) and p.health.health > 240.0,
		"%.0f/%.0f" % [p.health.health, p.health.max_health])
	check("weapons weaker", float(WeaponData.get_def("pistol")["damage"]) < 20.0 and float(WeaponData.get_def("rifle")["damage"]) < 25.0)


func _test_cops_miss(p: Player) -> void:
	var base := p.global_position + Vector3(0, 60, 0)
	var fl := _floor(base, Vector3(60, 1, 60))
	p.teleport(base + Vector3(0, 0.1, 0))
	await wait(0.5)
	var cop: NPC = world.peds.call("spawn_npc", base + Vector3(0, 0.1, -15), "cop", {}, true)
	cop._set_state(NPC.S.IDLE)
	cop._timer = 100.0
	cop.weapons.give("rifle", 300)
	cop.weapons.equip("rifle")
	cop.weapons.accuracy = 0.9
	await wait(0.5)
	p.health.invulnerable = false
	p.health.health = 250.0
	var hits := 0
	var shots := 60
	var last := p.health.health
	for i in shots:
		cop.weapons._cooldown = 0.0
		cop.weapons.owned["rifle"]["clip"] = 30
		cop.weapons._reloading = 0.0
		cop.weapons._spread_bloom = 0.0
		cop.weapons.fire_at(p.global_position + Vector3.UP * 1.2, true)
		await get_tree().physics_frame
		await get_tree().physics_frame
		if p.health.health < last - 0.1:
			hits += 1
		last = p.health.health
		p.health.health = 250.0
	p.health.invulnerable = true
	check("cops miss a good share of their shots", hits > 3 and hits < int(shots * 0.8), "%d/%d hits" % [hits, shots])
	cop.queue_free()
	fl.queue_free()
	await wait(0.3)


func _test_kerb_and_reverse(p: Player) -> void:
	var base := p.global_position + Vector3(120, 60, 0)
	var fl := _floor(base, Vector3(40, 1, 140))
	var kerb := _floor(base + Vector3(0, 0.16, -20.0), Vector3(40, 0.16, 60))   # 16 cm kerb from z=-50..+10
	var car := Vehicle.create("supercar")
	world.add_child(car)
	car.global_transform = Transform3D(Basis.IDENTITY, base + Vector3(0, 0.6, 40))   # faces -Z
	await wait(1.0)
	p.enter_vehicle(car)
	await wait(0.5)
	Input.action_press("accelerate")
	var slowest_after := 999.0
	var reached := false
	for i in 80:
		await wait(0.05)
		var z := car.global_position.z - base.z
		if z < 14.0 and z > 4.0:
			reached = true
		if reached and z < 8.0:
			slowest_after = minf(slowest_after, car.speed_kmh)
		if z < 0.0:
			break
	Input.action_release("accelerate")
	var z_end := car.global_position.z - base.z
	check("car drives up onto the kerb", z_end < 6.0 and car.global_position.y > base.y + 0.1,
		"z %.1f y %.2f" % [z_end, car.global_position.y - base.y])
	check("kerb doesn't stop the car dead", slowest_after > 12.0 and slowest_after < 998.0, "slowest %.0f km/h" % slowest_after)
	# reverse from standstill
	Input.action_press("brake")
	for i in 100:
		await wait(0.05)
		if absf(car.speed_kmh) < 1.0:
			break
	await wait(2.0)
	var sp := car.speed_kmh
	await wait(3.0)
	var sp2 := car.speed_kmh
	Input.action_release("brake")
	check("reverse accelerates quickly", sp < -18.0, "%.0f km/h" % sp)
	check("reverse top speed about 50 km/h", sp2 > -58.0 and sp2 < -35.0, "%.0f km/h" % sp2)
	p.exit_vehicle()
	await wait(0.5)
	car.queue_free()
	kerb.queue_free()
	fl.queue_free()


func _test_driveby(p: Player) -> void:
	var car := Vehicle.create("sedan")
	world.add_child(car)
	car.global_position = p.global_position + Vector3(4, 0.8, 0)
	await wait(0.6)
	p.weapons.give("pistol", 60)
	p.weapons.give("smg", 120)
	p.weapons.give("rpg", 4)
	p.enter_vehicle(car)
	await wait(0.5)
	var w := p.weapons
	var seen := {}
	for i in 4:
		Input.action_press("weapon_next")
		await get_tree().process_frame
		await get_tree().process_frame
		Input.action_release("weapon_next")
		await get_tree().process_frame
		seen[w.current_id()] = true
	check("weapon switching in the car", seen.size() >= 2 and not seen.has("rpg"), str(seen.keys()))
	w.owned[w.current_id()]["clip"] = 1
	Input.action_press("reload")
	await get_tree().process_frame
	await get_tree().process_frame
	Input.action_release("reload")
	await wait(3.5)
	check("reload in the car", int(w.owned[w.current_id()]["clip"]) > 1, "clip %d" % int(w.owned[w.current_id()]["clip"]))
	p.exit_vehicle()
	await wait(0.5)
	car.queue_free()


func _test_summon_key(p: Player) -> void:
	world.police.call("clear_wanted")
	var entry := {"id": "veh_test_t", "type": "sports", "color": "ff2020", "upgrades": {}, "livery": {"t": 2, "f": 0, "c2": "ffffff", "s": 0, "n": 16, "s2": 1}}
	Game.player_data.owned_vehicles.append(entry)
	var ev := InputEventAction.new()
	ev.action = "summon_vehicle"
	ev.pressed = true
	Input.parse_input_event(ev)
	await get_tree().process_frame
	await get_tree().process_frame
	var ev2 := InputEventAction.new()
	ev2.action = "summon_vehicle"
	ev2.pressed = false
	Input.parse_input_event(ev2)
	var m := MenuPanel.current
	check("T opens the car menu while playing", m != null and is_instance_valid(m) and m.title == "Fahrzeug rufen")
	if m and is_instance_valid(m):
		var idx := -1
		for i in m.items.size():
			if String(m.items[i]["label"]) == VehicleDefs.display_name("sports"):
				idx = i
		(m.items[idx]["action"] as Callable).call()
		m.close()
	await wait(1.0)
	var found: Vehicle = null
	for v in get_tree().get_nodes_in_group("vehicles"):
		if (v as Vehicle).owned_id == "veh_test_t":
			found = v
	check("summoned car appears near the player", found != null and found.global_position.distance_to(p.global_position) < 45.0,
		"d=%.1f" % (found.global_position.distance_to(p.global_position) if found else -1.0))


func _test_save_on_exit(p: Player) -> void:
	var pos := p.global_position + Vector3(30, 0, 12)
	p.teleport(pos)
	await wait(0.5)
	Game.state = Game.State.PLAYING
	var n_cars: int = Game.player_data.owned_vehicles.size()
	var ok := SaveManager.save_on_exit()
	var pd := SaveManager.load_slot(SaveManager.AUTOSAVE)
	check("leaving the game saves automatically", ok and pd != null)
	if pd:
		check("save keeps the position", pd.position.distance_to(p.global_position) < 3.0, "%.1f m" % pd.position.distance_to(p.global_position))
		check("save keeps the owned cars", pd.owned_vehicles.size() == n_cars and (pd.world_state.get("vehicles_parked", {}) as Dictionary).has("veh_test_t"),
			"%d cars" % pd.owned_vehicles.size())
		check("save keeps the livery", String((pd.owned_vehicles[-1] as Dictionary).get("livery", {}).get("c2", "")) == "ffffff")


func _test_parked_night(p: Player) -> void:
	var tm := world.traffic as TrafficManager
	world.day_night.call("set_hour", 13.0)
	await wait(0.2)
	var day := _want_parked(tm, p)
	world.day_night.call("set_hour", 23.5)
	await wait(0.2)
	var night := _want_parked(tm, p)
	check("more parked cars at night", night > day, "day %d night %d" % [day, night])
	world.day_night.call("set_hour", 14.0)


func _want_parked(tm: TrafficManager, p: Player) -> int:
	var h := float(world.day_night.get("hour"))
	var night := 1.6 if (h < 6.0 or h > 21.0) else (1.3 if (h < 7.5 or h > 18.5) else 1.0)
	var district := float(world.data.district_at(p.global_position).get("traffic", 0.6))
	return int(tm.target_parked * clampf(district + 0.35, 0.45, 1.0) * night)


func _test_street_life(p: Player) -> void:
	var base := p.global_position + Vector3(-120, 60, 0)
	var fl := _floor(base, Vector3(40, 1, 40))
	p.teleport(base + Vector3(0, 0.1, 0))
	p.weapons.equip("unarmed")
	await wait(0.5)
	# a friendly passer-by talks to you
	var ped: NPC = world.peds.call("spawn_npc", base + Vector3(0, 0.1, -2.0), "civilian", {}, true)
	ped.troublemaker = false
	ped._set_state(NPC.S.IDLE)
	ped._timer = 100.0
	ped._social_cd = 0.0
	NPC._last_street_line = 0
	var talked := false
	for i in 40:
		await wait(0.1)
		if ped._bubble and ped._bubble.visible:
			talked = true
			break
		if ped._social_cd > 0.0 and not talked:
			ped._social_cd = 0.0
			NPC._last_street_line = 0
	check("passer-by talks to the player", talked, ped._bubble.text if ped._bubble else "")
	ped.queue_free()
	# a troublemaker insults and picks a fight; hitting back gives no stars
	world.police.call("clear_wanted")
	var tm: NPC = world.peds.call("spawn_npc", base + Vector3(0, 0.1, -3.0), "civilian", {}, true)
	tm.troublemaker = true
	tm._set_state(NPC.S.IDLE)
	tm._timer = 100.0
	tm._social_cd = 0.0
	NPC._last_street_line = 0
	var insulted := false
	for i in 60:
		await wait(0.1)
		if tm._provoke >= 1:
			insulted = true
			break
	check("troublemaker insults the player", insulted, "stage %d" % tm._provoke)
	tm.start_brawl(p)
	await wait(0.2)
	check("troublemaker starts a fist fight", tm.state == NPC.S.FIGHT and not tm.weapons.current_is_ranged())
	for i in 4:
		Combat.apply_damage(tm, 8.0, p, tm.global_position + Vector3.UP, Vector3.FORWARD)
		await wait(0.2)
	check("fighting back gives no wanted level", int(world.police.wanted_level) == 0, "wanted %d" % world.police.wanted_level)
	tm.queue_free()
	fl.queue_free()


func _test_saving_always(p: Player) -> void:
	Game.state = Game.State.PLAYING
	world.police.call("set_wanted", 2)
	var ok := SaveManager.autosave(false)
	check("saving works while wanted", ok)
	world.police.call("clear_wanted")
	Game.player_data.add_money(1234, "test")
	var want := Game.player_data.money
	await wait(4.5)
	var pd := SaveManager.load_slot(SaveManager.AUTOSAVE)
	check("earned money is saved automatically", pd != null and pd.money == want, "%d vs %d" % [pd.money if pd else -1, want])


func _test_chase_music(p: Player) -> void:
	var r: CarRadio = AudioManager.radio
	var car := Vehicle.create("sedan")
	world.add_child(car)
	car.global_position = p.global_position + Vector3(4, 0.8, 0)
	await wait(0.6)
	p.enter_vehicle(car)
	await wait(0.8)
	var radio_on := r.radio.playing
	r.start_chase()
	await wait(0.2)
	check("car radio pauses during the chase track", r.is_chasing() and (not radio_on or r.radio.stream_paused))
	p.exit_vehicle()
	await wait(0.5)
	# still wanted, but no cop anywhere near: the chase track fades out
	world.police.call("set_wanted", 1)
	for u in world.police.units:
		for c in u["cops"]:
			if is_instance_valid(c):
				(c as Node3D).global_position += Vector3(0, -500, 0)
	await wait(7.0)
	check("chase music stops once no cop is after you", not r.is_chasing(), "wanted %d" % world.police.wanted_level)
	check("radio resumes after the chase", not r.radio.stream_paused)
	world.police.call("clear_wanted")
	car.queue_free()
