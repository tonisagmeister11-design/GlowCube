extends Node
## Headless test for the big update: auto-step, rocket launcher (cars fly, cops one-shot),
## pedestrians fleeing from a near miss, and further systems added below.
##   godot --headless --path Game res://tests/update_test.tscn

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


func _flat_spot(p: Player) -> Vector3:
	# a patch high above everything so nothing gets in the way
	return p.global_position + Vector3(0, 60, 0)


func _run() -> void:
	var p := world.player as Player
	p.health.invulnerable = true
	await _test_shop_menus(p)
	await _test_radio(p)
	await _test_claim_summon(p)
	await _test_auto_step(p)
	await _test_rocket(p)
	await _test_near_miss(p)
	await _test_race(p)
	await _test_knock_lamp(p)
	await _test_nitro(p)
	await _test_creative(p)
	await _test_surrender(p)
	await _test_car_heist(p)
	await _test_contracts_start(p)


func _test_auto_step(p: Player) -> void:
	var base := _flat_spot(p)
	var floor_body := StaticBody3D.new()
	var cs := CollisionShape3D.new()
	var bs := BoxShape3D.new()
	bs.size = Vector3(20, 1, 20)
	cs.shape = bs
	floor_body.add_child(cs)
	add_child(floor_body)
	floor_body.global_position = base - Vector3(0, 0.5, 0)
	var curb := StaticBody3D.new()
	var cs2 := CollisionShape3D.new()
	var bs2 := BoxShape3D.new()
	bs2.size = Vector3(8, 0.25, 3)
	cs2.shape = bs2
	curb.add_child(cs2)
	add_child(curb)
	curb.global_position = base + Vector3(0, 0.125, -3.0)
	p.teleport(base + Vector3(0, 0.1, 0))
	p.cam.yaw = 0.0   # forward = -Z
	await wait(0.5)
	var y0 := p.global_position.y
	Input.action_press("move_forward")
	var y1 := y0
	var on_top := 0
	for i in 16:
		await wait(0.1)
		y1 = maxf(y1, p.global_position.y)
		var z := p.global_position.z - base.z
		if z < -2.0 and z > -4.0 and p.global_position.y > y0 + 0.2:
			on_top += 1
	Input.action_release("move_forward")
	check("auto-step onto a curb", y1 > y0 + 0.2 and on_top > 0 and p.global_position.z < base.z - 4.5,
		"max y +%.2f, frames on top %d, z %.1f" % [y1 - y0, on_top, p.global_position.z - base.z])
	floor_body.queue_free()
	curb.queue_free()


func _test_rocket(p: Player) -> void:
	var base := _flat_spot(p) + Vector3(40, 0, 0)
	var floor_body := StaticBody3D.new()
	var cs := CollisionShape3D.new()
	var bs := BoxShape3D.new()
	bs.size = Vector3(60, 1, 60)
	cs.shape = bs
	floor_body.add_child(cs)
	add_child(floor_body)
	floor_body.global_position = base - Vector3(0, 0.5, 0)
	p.teleport(base + Vector3(0, 0.1, 12))
	var car := Vehicle.create("sedan")
	world.add_child(car)
	car.global_position = base + Vector3(0, 0.8, -10)
	var cop: NPC = world.peds.call("spawn_npc", base + Vector3(3.5, 0.1, -10), "cop", {}, true)
	cop.health.max_health = 400.0
	cop.health.health = 400.0
	await wait(1.0)
	var y_car := car.global_position.y
	p.weapons.give("rpg", 5)
	p.weapons.equip("rpg")
	var ok := p.weapons.fire_at(car.global_position + Vector3.UP * 0.5, true)
	var peak := y_car
	for i in 40:
		await wait(0.05)
		if is_instance_valid(car):
			peak = maxf(peak, car.global_position.y)
	check("rocket fired", ok)
	check("rocket blows the car up", is_instance_valid(car) and car.destroyed and peak > y_car + 1.5,
		"destroyed %s, rise %.1f m" % [car.destroyed if is_instance_valid(car) else "freed", peak - y_car])
	check("cop one-shot by the rocket", cop.is_dead(), "hp %.0f" % cop.health.health)
	# endless rockets: the launcher reloads on its own and never runs dry
	var shots := 0
	for i in 4:
		await wait(2.6)
		if p.weapons.fire_at(base + Vector3(0, 0, -25), true):
			shots += 1
	check("rocket launcher: auto reload, endless ammo", shots == 4, "%d/4 shots" % shots)
	world.police.call("clear_wanted")
	floor_body.queue_free()


func _test_near_miss(p: Player) -> void:
	var base := _flat_spot(p) + Vector3(-60, 0, 0)
	var floor_body := StaticBody3D.new()
	var cs := CollisionShape3D.new()
	var bs := BoxShape3D.new()
	bs.size = Vector3(120, 1, 30)
	cs.shape = bs
	floor_body.add_child(cs)
	add_child(floor_body)
	floor_body.global_position = base - Vector3(0, 0.5, 0)
	var ped: NPC = world.peds.call("spawn_npc", base + Vector3(0, 0.1, 1.5), "civilian", {}, true)
	ped._set_state(NPC.S.IDLE)
	ped._timer = 100.0
	var car := Vehicle.create("sports")
	world.add_child(car)
	car.global_position = base + Vector3(-40, 0.8, 0)
	car.rotation.y = -PI / 2   # facing +X
	await wait(0.6)
	p.enter_vehicle(car)
	await wait(0.8)
	car.linear_velocity = Vector3(16, 0, 0)
	var wanted0 := int(world.police.wanted_level)
	var fled := false
	for i in 40:
		car.linear_velocity = Vector3(16, car.linear_velocity.y, 0)
		await wait(0.05)
		if ped.state == NPC.S.FLEE:
			fled = true
	check("pedestrian flees from near miss", fled, "state %d" % ped.state)
	check("near miss gives no wanted star", int(world.police.wanted_level) == wanted0, "wanted %d" % world.police.wanted_level)
	p.exit_vehicle()
	await wait(0.5)
	floor_body.queue_free()


func _test_race(p: Player) -> void:
	p.teleport(world.data.spawn + Vector3.UP * 0.5)
	await wait(1.0)
	var lane := world.graph.closest_lane(p.global_position, 200.0)
	var car := Vehicle.create("supercar")
	world.add_child(car)
	car.global_position = world.graph.lanes[lane["lane"]].point_at(lane["s"]) + Vector3.UP * 0.8
	await wait(0.5)
	p.enter_vehicle(car)
	await wait(0.8)
	Game.player_data.money = 50000
	var why := [""]
	Events.mission_failed.connect(func(_id, r): why[0] = r)
	var ok: bool = world.missions.start_side("race", {"tier": 1})
	await wait(0.5)
	var m = world.missions.current
	check("race mission starts", ok and m != null and m.line.size() > 30, "line %d %s" % [m.line.size() if m else 0, why[0]])
	if m == null:
		return
	# drive to the start line instantly
	car.global_position = m.line[6] + Vector3.UP * 0.8
	for i in 80:
		await wait(0.1)
		if m.racers.size() == 3:
			break
	check("three AI racers on the grid", m.racers.size() == 3)
	await wait(4.5)   # countdown
	# the test player does not drive: park the car above the track so it blocks nobody
	car.freeze = true
	car.global_position += Vector3.UP * 30.0
	var idx0: Array = m.racers.map(func(r): return (r[1] as RaceDriver).idx)
	await wait(20.0)
	var gains: Array = []
	for i in m.racers.size():
		gains.append((m.racers[i][1] as RaceDriver).idx - int(idx0[i]))
	var avg := 0.0
	for g in gains:
		avg += float(g)
	avg /= maxf(gains.size(), 1.0)
	# 8 m per line point: 20 s at a decent race pace covers well over 400 m
	check("AI racers race along the route", avg * 8.0 > 320.0 and gains.min() * 8.0 > 120.0,
		"progress m: %s, resets %s" % [str(gains.map(func(g): return g * 8)), str(m.racers.map(func(r): return r[1].resets))])
	world.missions.abort_current()
	await wait(0.5)
	check("race can be aborted", not world.missions.is_active())


func _test_knock_lamp(p: Player) -> void:
	# find a street lamp in a loaded chunk and drive a car through it
	var target := []
	for c in world.streaming.loaded_chunks():
		var raw: Array = world.data.props_by_chunk.get("%d_%d" % [c.x, c.y], [])
		for i in raw.size():
			if String(raw[i][0]) == "lamp_street":
				target = [c, i, Vector3(raw[i][1], raw[i][2], raw[i][3])]
				break
		if not target.is_empty():
			break
	check("found a street lamp", not target.is_empty())
	if target.is_empty():
		return
	var lp: Vector3 = target[2]
	if p.vehicle:
		p.exit_vehicle()
		await wait(0.5)
	var car := Vehicle.create("sedan")
	world.add_child(car)
	# approach along the street direction (lamps stand on the sidewalk next to the lanes)
	var cl := world.graph.closest_lane(lp, 60.0)
	var dir := Vector3(1, 0, 0)
	if not cl.is_empty():
		dir = (world.graph.lanes[cl["lane"]] as RoadGraph.Lane).dir_at(cl["s"])
		dir.y = 0.0
		dir = dir.normalized()
	car.global_transform = Transform3D(Basis.looking_at(dir, Vector3.UP), lp - dir * 14.0 + Vector3.UP * 1.0)
	await wait(0.6)
	p.enter_vehicle(car)
	await wait(0.6)
	var debris0 := world.get_children().filter(func(n): return n is RigidBody3D and not n is Vehicle).size()
	for i in 30:
		car.linear_velocity = dir * 15.0 + Vector3.UP * car.linear_velocity.y
		await wait(0.05)
	var broken: bool = world.streaming.broken_props.get(target[0], {}).has(target[1])
	var debris1 := world.get_children().filter(func(n): return n is RigidBody3D and not n is Vehicle).size()
	check("car knocks the lamp over", broken and debris1 > debris0, "broken %s, debris %d -> %d" % [broken, debris0, debris1])
	p.exit_vehicle()
	await wait(0.3)


func _test_nitro(p: Player) -> void:
	var car := Vehicle.create("hypercar")
	world.add_child(car)
	car.global_position = _flat_spot(p) + Vector3(15, 0, 0)
	var fl := StaticBody3D.new()
	var cs := CollisionShape3D.new()
	var bs := BoxShape3D.new()
	bs.size = Vector3(40, 1, 200)
	cs.shape = bs
	fl.add_child(cs)
	add_child(fl)
	fl.global_position = car.global_position - Vector3(0, 0.5, 0)
	car.global_position += Vector3(0, 0.8, 60)
	await wait(0.8)
	var n0 := car.nitro
	car.ai_driver = Node.new()
	car.throttle = 1.0
	car.handbrake = false
	car.brake_input = 0.0
	await wait(0.5)
	var va := car.speed_kmh
	await wait(0.5)
	var gain_plain := car.speed_kmh - va
	var v0 := car.speed_kmh
	var fired := car.fire_nitro()
	await wait(0.5)
	var gain_nitro := car.speed_kmh - v0
	check("nitro boost", fired and n0 == 3 and car.nitro == 2 and gain_nitro > gain_plain * 1.5,
		"charges %d -> %d, +%.0f km/h vs +%.0f km/h per 0.5 s" % [n0, car.nitro, gain_nitro, gain_plain])
	car.ai_driver.free()
	car.ai_driver = null
	car.queue_free()
	fl.queue_free()


func _test_creative(p: Player) -> void:
	CreativeMode.set_enabled(true)
	await wait(0.8)
	var all := true
	for id in WeaponData.WEAPONS:
		if not p.weapons.has_weapon(id):
			all = false
	p.health.take_damage(500.0, null)
	check("creative mode: all weapons + invincible", all and not p.health.dead and p.health.invulnerable,
		"weapons %d/%d" % [p.weapons.owned.size(), WeaponData.WEAPONS.size()])
	CreativeMode.set_enabled(false)
	p.health.invulnerable = false


func _test_surrender(p: Player) -> void:
	Game.player_data.money = 200
	world.police.call("set_wanted", 2)
	await wait(0.3)
	var day0: int = world.day_night.day
	var ok := p.surrender()
	check("surrender while wanted", ok and p.state == Player.State.BUSTED)
	await wait(12.0)
	var days: int = world.day_night.day - day0
	check("jail skips weeks of time", days >= 14, "%d days" % days)
	check("fine can put you into debt", Game.player_data.money == -300, "$%d" % Game.player_data.money)
	check("released at the police station, wanted cleared", p.state == Player.State.GROUND and int(world.police.wanted_level) == 0,
		"state %d, wanted %d" % [p.state, world.police.wanted_level])
	check("surrender keeps weapons", p.weapons.owned.size() > 1, str(p.weapons.owned.keys()))
	Game.player_data.add_money(1000, "test")
	check("income pays the debt off", Game.player_data.money == 700, "$%d" % Game.player_data.money)


func _test_car_heist(p: Player) -> void:
	world.police.call("clear_wanted")
	p.teleport(world.data.spawn + Vector3.UP * 0.5)
	await wait(1.0)
	var ok: bool = world.missions.start_side("car_heist")
	await wait(1.5)
	var m = world.missions.current
	check("car heist starts with a convoy", ok and m != null and is_instance_valid(m.car), "")
	if m == null:
		return
	var car: Vehicle = m.car
	var p0 := car.global_position
	await wait(4.0)
	check("convoy drives", car.global_position.distance_to(p0) > 10.0, "moved %.1f m" % car.global_position.distance_to(p0))
	# steal it
	if car.ai_driver and car.ai_driver.has_method("abandon_vehicle"):
		car.ai_driver.abandon_vehicle(p)
	p.teleport(car.global_position + Vector3(3, 0.5, 0))
	await wait(0.3)
	p.enter_vehicle(car)
	await wait(1.5)
	check("checkpoint after stealing the car", m.stage == 1 and not m.checkpoint_data.is_empty(), "stage %d" % m.stage)
	# fail and restart from the checkpoint
	m.fail("test")
	await wait(0.5)
	world.missions.retry(true)
	await wait(1.5)
	var m2 = world.missions.current
	check("restart from checkpoint", m2 != null and m2.stage == 1 and is_instance_valid(m2.car) and p.vehicle == m2.car,
		"stage %s" % (str(m2.stage) if m2 else "none"))
	if m2 == null:
		return
	world.police.call("clear_wanted")
	var money0 := Game.player_data.money
	var dest: Vector3 = m2._markers[-1].global_position
	world.streaming.load_area_blocking(dest, 200.0)
	m2.car.global_position = dest + Vector3.UP * 0.8
	m2.car.linear_velocity = Vector3.ZERO
	for i in 40:
		await wait(0.1)
		world.police.call("clear_wanted")
		if not world.missions.is_active():
			break
	check("buyer pays for the hypercar", Game.player_data.money >= money0 + 100000, "+$%d" % (Game.player_data.money - money0))


func _test_contracts_start(p: Player) -> void:
	for key in ["jewel_heist", "armored_truck", "pizza", "car_sales", "smuggling", "chop_shop"]:
		world.police.call("clear_wanted")
		if p.vehicle:
			p.exit_vehicle()
		p.teleport(world.data.spawn + Vector3.UP * 0.5)
		await wait(0.8)
		var ok: bool = world.missions.start_side(key)
		await wait(3.0)
		var active: bool = world.missions.is_active()
		var blips: int = world.missions.current.blip_list.size() if active else 0
		var has_goal: bool = blips > 0 or (active and world.missions.current._markers.size() > 0)
		check("contract/job %s runs with a marked target" % key, ok and active and has_goal, "blips %d" % blips)
		world.missions.abort_current()
		await wait(0.5)


func _menu_labels() -> Array:
	var out := []
	if MenuPanel.current:
		for it in MenuPanel.current.items:
			out.append(String(it.get("label", "")))
		MenuPanel.current.close()
	await get_tree().process_frame
	return out


func _test_shop_menus(p: Player) -> void:
	var eco := world.economy as PoiManager
	var food := ["Snack & Limo", "Sandwich", "Brot & Aufschnitt"]
	var results := {}
	for t in ["electronics", "gas_station", "diner", "jewelry", "shop_convenience"]:
		var poi := world.data.nearest_poi(t, p.global_position)
		eco._open_store(p, poi)
		await get_tree().process_frame
		results[t] = await _menu_labels()
	eco._open_weapons(p, world.data.nearest_poi("shop_weapons", p.global_position))
	await get_tree().process_frame
	results["shop_weapons"] = await _menu_labels()
	eco._open_dealer(p, world.data.nearest_poi("car_dealer", p.global_position))
	await get_tree().process_frame
	results["car_dealer"] = await _menu_labels()
	var no_food := true
	for t in ["electronics", "jewelry", "shop_weapons", "car_dealer"]:
		for f in food:
			if f in results[t]:
				no_food = false
	check("no food at electronics / jeweler / gun shop / car dealer", no_food, str(results["electronics"]))
	check("gas station sells car stuff", "Nitro-Kartusche" in results["gas_station"] and "Reparaturset" in results["gas_station"])
	check("diner serves meals", "Burger & Pommes" in results["diner"])
	check("convenience store sells bread", "Brot & Aufschnitt" in results["shop_convenience"])


func _test_radio(p: Player) -> void:
	var r: CarRadio = AudioManager.radio
	var car := Vehicle.create("sedan")
	world.add_child(car)
	car.global_position = p.global_position + Vector3(4, 0.8, 0)
	await wait(0.5)
	p.enter_vehicle(car)
	await wait(1.5)
	var playing := r.radio.playing
	var track := r._track
	check("radio plays a song when getting in", playing and track >= 0, "track %d" % track)
	p.exit_vehicle()
	await wait(0.5)
	var pos_out := r.radio.get_playback_position()
	check("radio stops when getting out", not r.radio.playing)
	p.enter_vehicle(car)
	await wait(0.8)
	check("same car resumes the same song", r.radio.playing and r._track == track, "track %d" % r._track)
	p.exit_vehicle()
	await wait(0.5)
	car.queue_free()
	# chase track
	world.police.call("set_wanted", 2)
	r.start_chase()
	await wait(0.5)
	check("chase track starts at 0:10", r.is_chasing() and r.chase.get_playback_position() >= 10.0 and not r.radio.playing,
		"pos %.1f" % r.chase.get_playback_position())
	world.police.call("clear_wanted")
	await wait(2.2)
	check("chase track stops after escaping", not r.is_chasing())


func _test_claim_summon(p: Player) -> void:
	world.police.call("clear_wanted")
	var n0: int = Game.player_data.owned_vehicles.size()
	var car := Vehicle.create("muscle")
	world.add_child(car)
	car.global_position = p.global_position + Vector3(4, 0.8, 0)
	await wait(0.5)
	p.enter_vehicle(car)
	await wait(0.5)
	(world.economy as PoiManager).claim_vehicle(car)
	check("stolen car claimed as own", car.player_owned and Game.player_data.owned_vehicles.size() == n0 + 1)
	p.exit_vehicle()
	await wait(0.5)
	p.teleport(p.global_position + Vector3(250, 0, 150))
	await wait(1.5)
	var entry: Dictionary = Game.player_data.owned_vehicles[-1]
	var v: Vehicle = (world.economy as PoiManager).summon_vehicle(entry)
	await wait(0.5)
	check("owned car summoned next to the player", v != null and v.global_position.distance_to(p.global_position) < 45.0
		and not is_instance_valid(car), "d=%.1f" % (v.global_position.distance_to(p.global_position) if v else -1.0))
