extends Node
## Full multiplayer scenario test over a simulated internet connection (Tools/Net/netsim.py).
## Roles: host, client. Coordination through chat messages ("@step").
##   host:   walks, crouches, drives (real input), gets crashed into, runs the co-op missions
##   client: measures how exactly it sees the host (position error at the best time lag,
##           animation mirroring, car), crashes into the host's car, plays the co-op missions

var args := {}
var results := []
var role := ""
var chats: Array = []
var truth: Array = []          # host: [ms, pos, car pos] each physics frame
var dir := ""


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		args[a[i].trim_prefix("--")] = a[i + 1]
	role = args.get("role", "host")
	dir = args.get("dir", "user://")
	Net.chat_received.connect(func(s, t): chats.append([s, t]))
	get_tree().create_timer(600.0).timeout.connect(func():
		print("[%s] TIMEOUT" % role)
		_finish())
	if role == "host":
		await _host()
	else:
		await _client()
	_finish()


func check(n: String, ok: bool, info := "") -> void:
	results.append([n, ok])
	print("[%s] %s %s%s" % [role, "PASS" if ok else "FAIL", n, ("  (" + info + ")") if info != "" else ""])


func wait(t: float) -> void:
	await get_tree().create_timer(t, true).timeout


func wait_until(cond: Callable, timeout: float) -> bool:
	var t := 0.0
	while t < timeout:
		if cond.call():
			return true
		await wait(0.1)
		t += 0.1
	return cond.call()


func heard(text: String) -> bool:
	for c in chats:
		if String(c[1]) == text:
			return true
	return false


func step(name: String) -> void:
	Net.send_chat("@" + name)


func await_step(name: String, timeout := 60.0) -> bool:
	return await wait_until(func(): return heard("@" + name), timeout)


func _finish() -> void:
	var failed := results.filter(func(r): return not r[1]).size()
	print("[%s] === %d checks, %d failed ===" % [role, results.size(), failed])
	Net.leave("")
	get_tree().quit(1 if failed > 0 else 0)


static func wall_ms() -> int:
	return int(Time.get_unix_time_from_system() * 1000.0)


## "@name <var>" payload of the newest message with that step name (or null).
func step_arg(name: String):
	for i in range(chats.size() - 1, -1, -1):
		var t := String(chats[i][1])
		if t.begins_with("@" + name + " "):
			return str_to_var(t.substr(name.length() + 2))
	return null


func await_arg(name: String, timeout := 90.0):
	await wait_until(func(): return step_arg(name) != null, timeout)
	return step_arg(name)


func me() -> Player:
	return GameWorld.instance.player as Player if GameWorld.instance else null


func other_id() -> int:
	for id in Net.players:
		if id != Net.my_id():
			return id
	return 0


func _flat(center: Vector3, size: Vector3) -> StaticBody3D:
	var b := StaticBody3D.new()
	var cs := CollisionShape3D.new()
	var bs := BoxShape3D.new()
	bs.size = size
	cs.shape = bs
	b.add_child(cs)
	GameWorld.instance.add_child(b)
	b.global_position = center - Vector3(0, size.y * 0.5, 0)
	return b


# ================================================================== host
func _host() -> void:
	Game.player_data = PlayerData.new()
	check("lobby created", Net.host("Toni"))
	await wait_until(func(): return Net.lobby_ready, 20.0)   # internet mode: code final after the check
	var f := FileAccess.open(args["file"], FileAccess.WRITE)
	f.store_string(Net.lobby_code)
	f.close()
	check("friend joined over the (simulated) internet", await wait_until(func(): return Net.players.size() == 2, 120.0))
	await wait(0.5)
	Net.start_session()
	await wait_until(func(): return me() != null and Game.state == Game.State.PLAYING, 120.0)
	var cid := 0
	await wait_until(func():
		cid = other_id()
		return cid != 0 and Net.proxies().has(cid), 120.0)
	var p := me()
	p.health.invulnerable = true
	# a private test ground high above the city
	var base := p.global_position + Vector3(0, 120, 0)
	_flat(base, Vector3(300, 1, 300))
	p.teleport(base + Vector3(0, 0.2, 0))
	await wait(1.0)
	step("ground " + var_to_str(base))
	await await_step("ready_walk")
	# ---- walking a curve with real input, logging the true position
	var rec := {"on": true}
	var logger := func():
		while rec["on"]:
			var car_pos := Vector3.ZERO
			if p.is_in_vehicle():
				car_pos = (p.vehicle as Node3D).global_position
			truth.append([wall_ms(), p.global_position, car_pos, p.model.mode])
			await get_tree().physics_frame
	logger.call()
	step("walk_start")
	p.cam.yaw = 0.0
	Input.action_press("move_forward")
	for i in 60:
		p.cam.yaw += 0.03
		await wait(0.05)
	Input.action_press("sprint")
	for i in 40:
		p.cam.yaw -= 0.025
		await wait(0.05)
	Input.action_release("sprint")
	Input.action_release("move_forward")
	await wait(0.6)
	# crouch
	Input.action_press("crouch")
	await wait(0.15)
	Input.action_release("crouch")
	await wait(1.2)
	step("crouched")
	await wait(1.2)
	Input.action_press("crouch")
	await wait(0.15)
	Input.action_release("crouch")
	await wait(0.8)
	# ---- driving
	var car := Vehicle.create("sports", Color(0.9, 0.1, 0.1))
	GameWorld.instance.add_child(car)
	car.global_transform = Transform3D(Basis.IDENTITY, p.global_position + Vector3(4, 0.8, 0))
	await wait(1.0)
	p.enter_vehicle(car)
	await wait(0.8)
	step("drive_start")
	Input.action_press("accelerate")
	await wait(2.5)
	Input.action_press("steer_left")
	await wait(1.6)
	Input.action_release("steer_left")
	await wait(1.5)
	Input.action_release("accelerate")
	Input.action_press("brake")
	await wait(2.0)
	Input.action_release("brake")
	Input.action_press("handbrake")
	await wait(1.0)
	rec["on"] = false
	await wait(0.1)
	_save_truth()
	step("drive_end")
	# ---- the friend crashes into my car
	var v0 := car.linear_velocity.length()
	var hp0 := car.body_health
	await wait(0.5)
	step("crash_me " + var_to_str(car.global_position))
	var hit := await wait_until(func(): return car.body_health < hp0 - 1.0 or car.linear_velocity.length() > v0 + 2.0, 25.0)
	Input.action_release("handbrake")
	check("friend's crash pushes/damages my car", hit, "hp %.0f -> %.0f, v %.1f" % [hp0, car.body_health, car.linear_velocity.length()])
	await await_step("crash_done", 30.0)
	p.exit_vehicle()
	await wait(0.8)
	# ---- co-op: double delivery
	p.teleport(base + Vector3(0, 0.2, 0))
	await wait(0.5)
	check("co-op mission starts", CoopMissions.start("deliver"))
	await wait(1.0)
	await wait_until(func(): return _first_marker() != Vector3.ZERO, 20.0)
	var pick := _first_marker()
	p.teleport(pick + Vector3(1, 0.3, 0))
	step("go_pick " + var_to_str(pick))
	check("host gets his own delivery target", await wait_until(func():
		return ((Net.coop_state.get("per_player", {}) as Dictionary).get(1, {}) as Dictionary).has("gps"), 60.0))
	var mine: Dictionary = (Net.coop_state.get("per_player", {}) as Dictionary).get(1, {})
	if mine.has("gps"):
		p.teleport((mine["gps"] as Vector3) + Vector3(1, 0.3, 0))
	check("co-op delivery completed on the host", await wait_until(func(): return not bool(Net.coop_state.get("active", false)), 60.0))
	await await_step("deliver_done", 40.0)
	# ---- co-op: street war (shared enemies)
	p.teleport(base + Vector3(0, 0.2, 0))
	await wait(0.5)
	CoopMissions.start("war")
	await wait(1.0)
	await wait_until(func(): return _first_marker() != Vector3.ZERO, 20.0)
	var zone: Vector3 = _first_marker()
	p.teleport(zone + Vector3(1, 0.3, 0))
	step("go_zone " + var_to_str(zone))
	for wave in 3:
		await wait_until(func(): return _alive_ents() > 0, 60.0)
		await wait(4.0)
		step("wave_%d %d" % [wave, _alive_ents()])
		await await_step("shot_%d" % wave, 60.0)
		await wait(1.0)
		# the host finishes the wave off
		for e in Net._ents.values():
			if e.get("amb", false):
				continue
			var n = e["node"]
			if n is NPC and is_instance_valid(n) and not (n as NPC).is_dead():
				(n as NPC).health.take_damage(9999.0, p, (n as Node3D).global_position, Vector3.FORWARD)
		await wait_until(func(): return _alive_ents() == 0, 20.0)
		if wave < 2:
			await wait_until(func(): return _alive_ents() > 0, 30.0)
	check("street war won", await wait_until(func(): return not bool(Net.coop_state.get("active", false)), 60.0))
	await await_step("war_done", 40.0)
	# ---- co-op heist: shared van (host AI drives it) and guards
	p.teleport(base + Vector3(0, 0.2, 0))
	await wait(0.5)
	p.health.invulnerable = true
	CoopMissions.start("heist")
	await wait(1.0)
	await wait_until(func(): return _first_marker() != Vector3.ZERO, 20.0)
	var meet := _first_marker()
	p.teleport(meet + Vector3(1, 0.3, 0))
	step("go_meet " + var_to_str(meet))
	var holder := {"van": null}
	await wait_until(func():
		for e in Net._ents.values():
			if e["node"] is Vehicle and is_instance_valid(e["node"]) and not e.get("amb", false):
				holder["van"] = e["node"]
		return holder["van"] != null, 60.0)
	var van: Vehicle = holder["van"]
	check("heist: the van is on the road", van != null)
	if van:
		await wait(4.0)
		step("van_eid %d" % int(van.get_meta("net_eid")))
		await await_step("van_shot", 60.0)
		check("heist: friend's shots damage the van on the host", van.engine_health < 1000.0 or van.body_health < 1000.0,
			"engine %.0f body %.0f" % [van.engine_health, van.body_health])
		van.engine_health = 100.0
	# guards
	await wait_until(func(): return _alive_ents() > 0, 30.0)
	step("guards %d" % _alive_ents())
	await await_step("guards_seen", 40.0)
	for e in Net._ents.values():
		if e.get("amb", false):
			continue
		var n = e["node"]
		if n is NPC and is_instance_valid(n) and not (n as NPC).is_dead():
			(n as NPC).health.take_damage(9999.0, p, (n as Node3D).global_position, Vector3.FORWARD)
	# bags then hideout
	await wait_until(func(): return (Net.coop_state.get("markers", []) as Array).size() == 2, 30.0)
	var bags: Array = (Net.coop_state["markers"] as Array).duplicate(true)
	p.teleport((bags[0][0] as Vector3) + Vector3(0, 0.3, 0))
	step("bag " + var_to_str(bags[1][0]))
	await wait_until(func():
		var mk: Array = Net.coop_state.get("markers", [])
		return mk.size() == 1 and float(mk[0][1]) > 10.0, 60.0)
	var hide := _first_marker()
	p.teleport(hide + Vector3(1, 0.3, 0))
	step("go_hide " + var_to_str(hide))
	check("heist completed", await wait_until(func(): return not bool(Net.coop_state.get("active", false)), 60.0))
	await await_step("heist_done", 40.0)
	step("bye")
	await wait(2.0)


func _alive_ents() -> int:
	var k := 0
	for e in Net._ents.values():
		if e.get("amb", false):
			continue
		var n = e["node"]
		if n is NPC and is_instance_valid(n) and not (n as NPC).is_dead():
			k += 1
	return k


func _first_marker() -> Vector3:
	var mk: Array = Net.coop_state.get("markers", [])
	return mk[0][0] if mk.size() > 0 else Vector3.ZERO


func _coop_walk_markers(p: Player, _id: String) -> void:
	# pick-up point: both players go there
	await wait_until(func(): return _first_marker() != Vector3.ZERO, 20.0)
	var pick := _first_marker()
	p.teleport(pick + Vector3(1, 0.3, 0))
	step("go " + var_to_str(pick))
	# then my own destination
	await wait_until(func():
		var mine: Dictionary = (Net.coop_state.get("per_player", {}) as Dictionary).get(1, {})
		return mine.has("gps"), 60.0)
	var d: Vector3 = (Net.coop_state["per_player"][1])["gps"]
	p.teleport(d + Vector3(1, 0.3, 0))


func _save_truth() -> void:
	var f := FileAccess.open(dir.path_join("truth.txt"), FileAccess.WRITE)
	for t in truth:
		f.store_line("%d %f %f %f %f %f %f %s" % [t[0], t[1].x, t[1].y, t[1].z, t[2].x, t[2].y, t[2].z, t[3]])
	f.close()


# ================================================================== client
func _client() -> void:
	Game.player_data = PlayerData.new()
	await wait_until(func(): return FileAccess.file_exists(args["file"]) and FileAccess.get_file_as_string(args["file"]).length() >= 10, 60.0)
	var code := FileAccess.get_file_as_string(args["file"]).strip_edges()
	Net.join("Kai", code)
	check("joined with the code", await wait_until(func(): return Net.players.size() == 2, 60.0), Net.status)
	check("world loaded", await wait_until(func(): return me() != null and Game.state == Game.State.PLAYING, 150.0))
	check("sees the host", await wait_until(func(): return Net.proxies().has(1), 60.0))
	var p := me()
	p.health.invulnerable = true
	# go to the test ground next to the host
	await wait_until(func(): return chats.any(func(c): return String(c[1]).begins_with("@ground ")), 60.0)
	var base: Vector3 = Vector3.ZERO
	for c in chats:
		if String(c[1]).begins_with("@ground "):
			base = str_to_var(String(c[1]).substr(8))
	_flat(base, Vector3(300, 1, 300))
	p.teleport(base + Vector3(-6, 0.2, 10))
	await wait(1.0)
	step("ready_walk")
	await await_step("walk_start")
	var host_pr: RemotePlayer = Net.proxies()[1]
	var samples: Array = []
	var modes := {}
	var fl := {"loco": 0.0, "car": false, "spin": false, "on": true}
	var sampler := func():
		while fl["on"]:
			var pr: RemotePlayer = Net.proxies().get(1)
			if pr and is_instance_valid(pr):
				var cp := Vector3.ZERO
				if pr.car and is_instance_valid(pr.car):
					cp = pr.car.global_position
					fl["car"] = true
					for w in pr.car._wheels:
						if absf(float(w["spin"])) > 1.0:
							fl["spin"] = true
				samples.append([wall_ms(), pr.global_position, cp])
				modes[pr.model.mode] = true
				fl["loco"] = maxf(float(fl["loco"]), pr.model._net_loco)
			await get_tree().physics_frame
	sampler.call()
	await await_step("drive_end", 90.0)
	fl["on"] = false
	await wait(0.3)
	check("host's walk/run animation is mirrored", float(fl["loco"]) > 3.0 and modes.has("ground"),
		"max speed blend %.1f, modes %s" % [fl["loco"], modes.keys()])
	check("host's crouch is mirrored", modes.has("crouch"))
	check("host's car is shown (with rolling wheels)", fl["car"] and fl["spin"])
	# accuracy against the host's true positions (same machine clock), at the best time lag
	var tru: Array = await _load_truth()
	var err := _best_lag_error(samples, tru, 1)
	check("walking: position matches the host within 25 cm", err[0] < 0.25, "mean error %.3f m at %d ms delay" % [err[0], err[1]])
	var errc := _best_lag_error(samples, tru, 2)
	check("driving: car position matches within 60 cm", errc[0] < 0.6, "mean error %.3f m at %d ms delay" % [errc[0], errc[1]])
	check("delay stays small (< 250 ms incl. smoothing)", err[1] < 250, "%d ms" % err[1])
	# ---- crash into the host's car
	await wait_until(func(): return chats.any(func(c): return String(c[1]).begins_with("@crash_me ")), 30.0)
	var target := Vector3.ZERO
	for c in chats:
		if String(c[1]).begins_with("@crash_me "):
			target = str_to_var(String(c[1]).substr(10))
	var mycar := Vehicle.create("muscle", Color(0.1, 0.3, 0.9))
	GameWorld.instance.add_child(mycar)
	var start := target + Vector3(0, 0.8, 18)
	mycar.global_transform = Transform3D(Basis.IDENTITY, start)   # faces -Z towards the host's car
	await wait(0.8)
	p.enter_vehicle(mycar)
	await wait(0.5)
	for i in 60:
		# steer at where I see his car right now, like a real driver
		var pr_now: RemotePlayer = Net.proxies().get(1)
		var aim := target
		if pr_now and pr_now.car and is_instance_valid(pr_now.car):
			aim = pr_now.car.global_position
		var to := aim - mycar.global_position
		to.y = 0.0
		mycar.linear_velocity = to.normalized() * 16.0
		await wait(0.05)
		if to.length() < 2.5:
			break
	await wait(1.5)
	var hp_pr: RemotePlayer = Net.proxies().get(1)
	print("[client] crash debug: my car %s, host car copy %s, target %s" % [mycar.global_position,
		hp_pr.car.global_position if hp_pr and hp_pr.car else "none", target])
	step("crash_done")
	p.exit_vehicle()
	await wait(0.5)
	# ---- co-op delivery
	check("co-op objective shown to the friend", await wait_until(func(): return bool(Net.coop_state.get("active", false)), 30.0),
		String(Net.coop_state.get("objective", "")))
	var money0 := Game.player_data.money
	var pick: Vector3 = await await_arg("go_pick")
	p.teleport(pick + Vector3(-1, 0.3, 1))
	check("friend gets his own delivery target", await wait_until(func():
		var mine: Dictionary = (Net.coop_state.get("per_player", {}) as Dictionary).get(Net.my_id(), {})
		return mine.has("gps"), 60.0))
	var d: Vector3 = (Net.coop_state["per_player"][Net.my_id()])["gps"]
	p.teleport(d + Vector3(1, 0.3, 0))
	check("co-op reward received", await wait_until(func(): return Game.player_data.money >= money0 + 15000, 60.0),
		"%d -> %d" % [money0, Game.player_data.money])
	step("deliver_done")
	# ---- street war: shared enemies
	var zone: Vector3 = await await_arg("go_zone")
	p.teleport(zone + Vector3(-1, 0.3, 1))
	var hurt_by_enemies := false
	var hp_start := p.health.health
	p.health.invulnerable = false
	p.health.max_health = 5000.0
	p.health.health = 5000.0
	for wave in 3:
		await await_arg("wave_%d" % wave, 120.0)
		var ents := Net._ent_proxies.values().filter(func(n): return n is RemoteEntity and is_instance_valid(n) and not n.is_dead() and not n.amb)
		check("wave %d: friend sees the host's enemies" % (wave + 1), ents.size() > 0, "%d enemies" % ents.size())
		if ents.size() > 0:
			p.weapons.give("rifle", 200)
			p.weapons.equip("rifle")
			var killed := false
			var e: RemoteEntity = ents[0]
			for i in 30:
				if i % 6 == 0 or not is_instance_valid(e) or e.is_dead():
					# the nearest enemy in clear view (others hide behind houses and cars)
					ents = Net._ent_proxies.values().filter(func(n): return n is RemoteEntity and is_instance_valid(n) and not n.is_dead() and not n.amb)
					if ents.is_empty():
						break
					e = _visible_nearest(p, ents)
				p.weapons._cooldown = 0.0
				p.weapons._spread_bloom = 0.0
				p.weapons.owned["rifle"]["clip"] = 30
				p.weapons.fire_at(e.global_position + Vector3.UP * 1.2, true)
				await wait(0.12)
				if is_instance_valid(e) and e.is_dead():
					killed = true
					break
			if wave == 0:
				check("friend's bullets kill a shared enemy (seen dead)", killed or await wait_until(func(): return is_instance_valid(e) and e.is_dead(), 3.0))
		if p.health.health < 5000.0:
			hurt_by_enemies = true
		step("shot_%d" % wave)
	check("enemies simulated by the host shoot the friend", hurt_by_enemies or p.health.health < 5000.0, "hp %.0f" % p.health.health)
	p.health.invulnerable = true
	check("street war reward", await wait_until(func(): return not bool(Net.coop_state.get("active", false)), 60.0))
	step("war_done")
	# ---- heist
	var m1 := Game.player_data.money
	var meet: Vector3 = await await_arg("go_meet")
	p.teleport(meet + Vector3(-1, 0.3, 1))
	var eid: int = await await_arg("van_eid", 90.0)
	var vproxy = Net._ent_proxies.get(eid)
	check("heist: friend sees the van driving", vproxy is Vehicle and is_instance_valid(vproxy))
	if vproxy is Vehicle:
		var vp0: Vector3 = (vproxy as Vehicle).global_position
		await wait(1.5)
		check("heist: the van copy moves", (vproxy as Vehicle).global_position.distance_to(vp0) > 2.0,
			"%.1f m" % (vproxy as Vehicle).global_position.distance_to(vp0))
		# shoot it from close by
		for i in 12:
			# from right above it (next to it there may be walls or parked cars in the way)
			p.teleport((vproxy as Vehicle).global_position + Vector3(0, 7.0, 0))
			await get_tree().physics_frame
			p.weapons._cooldown = 0.0
			p.weapons._spread_bloom = 0.0
			p.weapons.owned["rifle"]["clip"] = 30
			p.weapons.fire_at((vproxy as Vehicle).global_position + Vector3.UP * 0.8, true)
			await wait(0.1)
	step("van_shot")
	var ng: int = await await_arg("guards", 60.0)
	check("heist: friend sees the guards", await wait_until(func():
		return Net._ent_proxies.values().filter(func(n): return n is RemoteEntity and is_instance_valid(n) and not n.is_dead() and not n.amb).size() > 0, 20.0),
		"host has %d" % ng)
	step("guards_seen")
	var bag: Vector3 = await await_arg("bag", 60.0)
	p.teleport(bag + Vector3(0, 0.3, 0))
	var hide: Vector3 = await await_arg("go_hide", 90.0)
	p.teleport(hide + Vector3(-1, 0.3, 1))
	check("heist reward received", await wait_until(func(): return Game.player_data.money >= m1 + 40000, 60.0),
		"%d -> %d" % [m1, Game.player_data.money])
	step("heist_done")
	await await_step("bye", 30.0)


func _follow_go() -> void:
	var n0 := chats.filter(func(c): return String(c[1]).begins_with("@go ")).size()
	await wait_until(func(): return chats.filter(func(c): return String(c[1]).begins_with("@go ")).size() > n0, 90.0)
	var gos := chats.filter(func(c): return String(c[1]).begins_with("@go "))
	var pos: Vector3 = str_to_var(String(gos[-1][1]).substr(4))
	me().teleport(pos + Vector3(-1, 0.3, 1))
	await wait(0.5)


func _load_truth() -> Array:
	var out := []
	var path := dir.path_join("truth.txt")
	await wait_until(func(): return FileAccess.file_exists(path), 10.0)
	for line in FileAccess.get_file_as_string(path).split("\n"):
		var p := line.split(" ")
		if p.size() >= 7:
			out.append([int(p[0]), Vector3(float(p[1]), float(p[2]), float(p[3])), Vector3(float(p[4]), float(p[5]), float(p[6]))])
	return out


## Mean distance between what the client saw and the host's truth `lag` ms earlier, for the best
## lag in 0..400 ms. idx 1 = character, 2 = car. Only samples where the truth moves are used.
func _best_lag_error(samples: Array, tru: Array, idx: int) -> Array:
	var best := [INF, 0]
	if tru.is_empty():
		return best
	for lag in range(0, 401, 10):
		var sum := 0.0
		var n := 0
		var j := 0
		for s in samples:
			var t: int = int(s[0]) - lag
			while j < tru.size() - 1 and int(tru[j + 1][0]) <= t:
				j += 1
			if j >= tru.size() - 1 or int(tru[j][0]) > t:
				continue
			var a: Array = tru[j]
			var b: Array = tru[j + 1]
			if (a[idx] as Vector3) == Vector3.ZERO or (s[idx] as Vector3) == Vector3.ZERO:
				continue
			var k := clampf(float(t - int(a[0])) / maxf(float(int(b[0]) - int(a[0])), 1.0), 0.0, 1.0)
			var tp: Vector3 = (a[idx] as Vector3).lerp(b[idx], k)
			sum += tp.distance_to(s[idx])
			n += 1
		if n > 20 and sum / n < best[0]:
			best = [sum / n, lag]
	return best


func _visible_nearest(p: Node3D, ents: Array) -> RemoteEntity:
	var e: RemoteEntity = ents[0]
	var best := INF
	var space := p.get_world_3d().direct_space_state
	for c in ents:
		var q := PhysicsRayQueryParameters3D.create(p.global_position + Vector3.UP * 1.5, (c as Node3D).global_position + Vector3.UP * 1.2)
		q.exclude = [p.get_rid()]
		q.collision_mask = 1 | (1 << 2) | (1 << 3)
		var r := space.intersect_ray(q)
		var dist := p.global_position.distance_to((c as Node3D).global_position)
		if (r.is_empty() or r["collider"] == c) and dist < best:
			best = dist
			e = c
	return e
