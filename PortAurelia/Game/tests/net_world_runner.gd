extends Node
## Shared world: the friend near the host sees the host's traffic, parked cars and pedestrians
## (same positions), can take a car and shoot a pedestrian of that world, gets his own traffic
## again when far away and the shared world back when he returns. --role host|client --file --dir

var args := {}
var results := []
var role := ""
var chats: Array = []


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		args[a[i].trim_prefix("--")] = a[i + 1]
	role = args.get("role", "host")
	Net.chat_received.connect(func(s, t): chats.append([s, t]))
	get_tree().create_timer(420.0).timeout.connect(func():
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


func step(name: String) -> void:
	Net.send_chat("@" + name)


func step_arg(name: String):
	for i in range(chats.size() - 1, -1, -1):
		var t := String(chats[i][1])
		if t == "@" + name:
			return true
		if t.begins_with("@" + name + " "):
			return str_to_var(t.substr(name.length() + 2))
	return null


func await_arg(name: String, timeout := 90.0):
	await wait_until(func(): return step_arg(name) != null, timeout)
	return step_arg(name)


func _finish() -> void:
	var failed := results.filter(func(r): return not r[1]).size()
	print("[%s] === %d checks, %d failed ===" % [role, results.size(), failed])
	Net.leave("")
	get_tree().quit(1 if failed > 0 else 0)


func me() -> Player:
	return GameWorld.instance.player as Player if GameWorld.instance else null


func _amb_cars() -> Array:
	return Net._ent_proxies.values().filter(func(n): return n is Vehicle and is_instance_valid(n) and not (n as Vehicle).destroyed)


func _amb_peds() -> Array:
	return Net._ent_proxies.values().filter(func(n): return n is RemoteEntity and is_instance_valid(n) and n.amb and not n.is_dead())


func _local_ambient() -> int:
	var w := GameWorld.instance
	var c := (w.traffic as TrafficManager).drivers.size() + (w.traffic as TrafficManager).parked.size()
	for n in (w.peds as PedManager).peds:
		if is_instance_valid(n) and not (n as NPC).persistent:
			c += 1
	return c


# ================================================================== host
func _host() -> void:
	Game.player_data = PlayerData.new()
	check("lobby created", Net.host("Toni"))
	var f := FileAccess.open(args["file"], FileAccess.WRITE)
	f.store_string(Net.lobby_code)
	f.close()
	var booms := [0]
	Events.explosion.connect(func(_p, _r, src): if src is RemotePlayer: booms[0] += 1)
	await wait_until(func(): return Net.players.size() == 2, 120.0)
	await wait(0.5)
	Net.start_session()
	await wait_until(func(): return me() != null and Game.state == Game.State.PLAYING, 120.0)
	me().health.invulnerable = true
	await wait_until(func(): return Net.proxies().size() == 1, 120.0)
	var cid := 0
	for id in Net.players:
		if id != 1:
			cid = id
	check("friend sees my world (shared)", await wait_until(func(): return Net._world_share and Net._world_share.is_shared(cid), 30.0))
	var tm := GameWorld.instance.traffic as TrafficManager
	await wait_until(func(): return tm.parked.size() >= 3 and tm.drivers.size() >= 3, 60.0)
	await wait(6.0)
	# where my parked cars really are
	var lines := []
	for v in tm.parked:
		if is_instance_valid(v) and (v as Node).has_meta("net_eid"):
			var p: Vector3 = (v as Node3D).global_position
			lines.append("%d %f %f %f" % [int(v.get_meta("net_eid")), p.x, p.y, p.z])
	var pf := FileAccess.open(args["dir"].path_join("parked.txt"), FileAccess.WRITE)
	pf.store_string("\n".join(lines))
	pf.close()
	# bandwidth of the shared world
	Net._peer.host.pop_statistic(ENetConnection.HOST_TOTAL_SENT_DATA)
	await wait(10.0)
	var kbs := Net._peer.host.pop_statistic(ENetConnection.HOST_TOTAL_SENT_DATA) / 10.0 / 1024.0
	check("upload to the friend stays small", kbs < 60.0, "%.1f KB/s with %d cars, %d pedestrians" % [kbs, tm.vehicles.size(), GameWorld.instance.peds.peds.size()])
	# reference: do the cars jump in my own game, too?
	var last := {}
	var hj := 0
	var t_last := Time.get_ticks_usec()
	for i in 180:
		await get_tree().physics_frame
		var t_now := Time.get_ticks_usec()
		var dt := maxf((t_now - t_last) / 1000000.0, 0.004)
		t_last = t_now
		var now := {}
		for v in tm.vehicles:
			if is_instance_valid(v) and (v as Node).has_meta("net_eid"):
				now[v] = (v as Node3D).global_position
				if last.has(v):
					var d: float = (now[v] as Vector3).distance_to(last[v])
					if d > 0.8 and d / dt > 70.0:
						hj += 1
						print("[host] own car jump %.1f m, %.0f m from me, %.0f m from friend, kinematic %s" % [d,
							(v as Node3D).global_position.distance_to(me().global_position),
							(v as Node3D).global_position.distance_to((Net.proxies()[cid] as Node3D).global_position), (v as Vehicle).kinematic_mode])
		last = now
	print("[host] own traffic jumps: %d" % hj)
	step("parked_ready")
	# the friend takes one of my cars
	var took = await await_arg("took", 90.0)
	if took is int:
		check("the car he took is gone from my world", await wait_until(func(): return not Net._ents.has(took), 5.0))
		check("I see him driving it", await wait_until(func():
			var pr = Net.proxies().get(cid)
			return pr and is_instance_valid(pr) and (pr as RemotePlayer).car != null, 10.0))
	var left = await await_arg("left_car", 60.0)
	if left is Vector3:
		check("I see the car he left standing there", GameWorld.instance.traffic.vehicles.any(func(v):
			return is_instance_valid(v) and (v as Node3D).global_position.distance_to(left) < 1.5 and (v as Node).has_meta("net_ret")))
	# he shoots one of my pedestrians
	var shot = await await_arg("shot_ped", 90.0)
	if shot is int:
		check("the pedestrian he shot is hit in my world", await wait_until(func():
			var e: Dictionary = Net._ents.get(shot, {})
			var n = e.get("node")
			return n == null or not is_instance_valid(n) or (n as NPC).is_dead() or (n as NPC).health.health < (n as NPC).health.max_health, 5.0))
	var rk = await await_arg("rocket", 90.0)
	if rk is int and rk > 0:
		check("his rocket destroyed my car", await wait_until(func():
			var e: Dictionary = Net._ents.get(rk, {})
			var n = e.get("node")
			return n == null or not is_instance_valid(n) or (n as Vehicle).destroyed, 5.0))
		check("I saw his rocket explode", booms[0] > 0)
	# he drives far away and comes back
	await await_arg("far", 90.0)
	check("far friend: no longer shared", await wait_until(func(): return not Net._world_share.is_shared(cid), 10.0))
	await await_arg("back", 120.0)
	check("friend back: shared again", await wait_until(func(): return Net._world_share.is_shared(cid), 20.0),
		"friend %.0f m away" % me().global_position.distance_to((Net.proxies()[cid] as Node3D).global_position) if Net.proxies().has(cid) else "gone")
	await await_arg("done", 120.0)


# ================================================================== client
func _client() -> void:
	Game.player_data = PlayerData.new()
	await wait_until(func(): return FileAccess.file_exists(args["file"]) and FileAccess.get_file_as_string(args["file"]).length() >= 10, 60.0)
	Net.join("Kai", FileAccess.get_file_as_string(args["file"]).strip_edges())
	check("joined", await wait_until(func(): return Net.players.size() == 2, 60.0), Net.status)
	check("world loaded", await wait_until(func(): return me() != null and Game.state == Game.State.PLAYING, 150.0))
	var p := me()
	p.health.invulnerable = true
	check("sees the host", await wait_until(func(): return Net.proxies().has(1), 60.0))
	check("sees the host's world", await wait_until(func(): return Net.world_shared, 30.0))
	check("host's cars around me", await wait_until(func(): return _amb_cars().size() >= 3, 60.0), str(_amb_cars().size()))
	check("host's pedestrians around me", await wait_until(func(): return _amb_peds().size() >= 2, 60.0), str(_amb_peds().size()))
	check("my own traffic and pedestrians are off", await wait_until(func(): return _local_ambient() == 0, 20.0), str(_local_ambient()))
	# parked cars stand exactly where the host has them
	await await_arg("parked_ready", 90.0)
	var worst := 0.0
	var compared := 0
	for line in FileAccess.get_file_as_string(args["dir"].path_join("parked.txt")).split("\n", false):
		var v := line.split(" ")
		var pr = Net._ent_proxies.get(int(v[0]))
		if pr is Vehicle and is_instance_valid(pr):
			compared += 1
			worst = maxf(worst, (pr as Node3D).global_position.distance_to(Vector3(float(v[1]), float(v[2]), float(v[3]))))
	check("parked cars stand exactly where the host has them", compared >= 2 and worst < 0.1, "%d compared, worst %.3f m" % [compared, worst])
	# every visible car of the host glides without jumps (3 s, all cars)
	# (speed against real time: a slow frame of this busy test machine is not a jump)
	var last := {}
	var moved := 0.0
	var jumps := 0
	var biggest := 0.0
	var t_last := Time.get_ticks_usec()
	for i in 180:
		await get_tree().physics_frame
		var t_now := Time.get_ticks_usec()
		var dt := maxf((t_now - t_last) / 1000000.0, 0.004)
		t_last = t_now
		var now := {}
		for c in _amb_cars():
			if (c as Vehicle).visible:
				now[c] = (c as Node3D).global_position
				if last.has(c):
					var d: float = (now[c] as Vector3).distance_to(last[c])
					moved += d
					biggest = maxf(biggest, d / dt)
					if d > 0.8 and d / dt > 70.0:   # faster than 250 km/h = a jump
						jumps += 1
		last = now
	var path_jumps := 0
	for c in _amb_cars():
		var mm := (c as Vehicle).get_node_or_null("NetMotion") as RemoteCarMotion
		if mm:
			path_jumps += mm.debug_jumps
	print("[client] path jumps (host time): %d" % path_jumps)
	check("the host's traffic drives smoothly for me", moved > 5.0 and jumps == 0, "%.1f m driven, %d jumps, fastest %.0f km/h" % [moved, jumps, biggest * 3.6])
	# take one of the host's cars
	var target: Vehicle = null
	var bd := INF
	for c in _amb_cars():
		var d := (c as Node3D).global_position.distance_to(p.global_position)
		if d < bd and absf((c as Vehicle).speed_kmh) < 3.0:
			bd = d
			target = c
	if target:
		var eid := int(target.get_meta("net_entity"))
		var vt := target.type_id
		p.teleport(target.get_entry_point() + Vector3.UP * 0.2)
		await wait(0.6)
		p.teleport(target.get_entry_point() + Vector3.UP * 0.2)
		await get_tree().physics_frame
		p._try_enter_vehicle()
		await wait(0.5)
		print("[client] take debug: in car %s, car %s, entity %s, dist %.1f" % [p.is_in_vehicle(), p.vehicle,
			(p.vehicle as Node).has_meta("net_entity") if p.vehicle else "-", bd])
		check("I can take a car of the host's world", p.is_in_vehicle() and p.vehicle is Vehicle
			and not (p.vehicle as Node).has_meta("net_entity") and (p.vehicle as Vehicle).type_id == vt, vt)
		step("took %d" % eid)
		await wait(3.0)
		var mine := p.vehicle as Vehicle
		var where := mine.global_position if mine else Vector3.ZERO
		p.exit_vehicle()
		# the car stays in the host's world: our copy is swapped for his
		check("the car I leave stays in the host's world", await wait_until(func():
			if is_instance_valid(mine):
				return false
			for c in _amb_cars():
				if (c as Node3D).global_position.distance_to(where) < 1.0 and (c as Vehicle).type_id == vt:
					return true
			return false, 6.0), "type %s" % vt)
		step("left_car " + var_to_str(where))
		await wait(1.0)
	else:
		check("I can take a car of the host's world", false, "no parked car")
		step("took -1")
	# shoot one of the host's pedestrians
	p.weapons.give("rifle", 300)
	p.weapons.equip("rifle")
	var shot := -1
	for attempt in 3:
		var peds := _amb_peds()
		if peds.is_empty():
			await wait(2.0)
			continue
		var e: RemoteEntity = peds[0]
		var best := INF
		for c in peds:
			var dd := (c as Node3D).global_position.distance_to(p.global_position)
			if dd < best:
				best = dd
				e = c
		p.teleport(e.global_position + Vector3(2.0, 0.3, 0.0))
		await wait(0.3)
		for i in 10:
			if not is_instance_valid(e) or e.is_dead():
				break
			p.weapons._cooldown = 0.0
			p.weapons._spread_bloom = 0.0
			p.weapons.owned["rifle"]["clip"] = 30
			p.weapons.fire_at(e.global_position + Vector3.UP * 1.2, true)
			await wait(0.15)
		if is_instance_valid(e):
			shot = e.eid
			break
	check("I hit a pedestrian of the host's world", shot > 0 and await wait_until(func():
		var r = Net._ent_proxies.get(shot)
		return r == null or (r as RemoteEntity).is_dead(), 5.0))
	step("shot_ped %d" % shot)
	# a rocket at one of the host's cars
	var rc: Vehicle = null
	var rbd := INF
	for c in _amb_cars():
		var dd := (c as Node3D).global_position.distance_to(p.global_position)
		if dd > 15.0 and dd < rbd:
			rbd = dd
			rc = c
	if rc:
		var reid := int(rc.get_meta("net_entity"))
		# from above, so nothing is in the way
		p.teleport(rc.global_position + Vector3(0, 9.0, 4.0))
		await wait(0.6)
		p.weapons.give("rpg", 5)
		p.weapons.equip("rpg")
		await wait(0.8)
		p.teleport(rc.global_position + Vector3(0, 9.0, 4.0))
		await get_tree().physics_frame
		p.weapons._cooldown = 0.0
		p.weapons.owned["rpg"]["clip"] = 1
		Events.explosion.connect(func(ep, _r, _s): print("[client] explosion at %s, car at %s" % [ep, rc.global_position if is_instance_valid(rc) else "gone"]), CONNECT_ONE_SHOT)
		p.weapons.fire_at(rc.global_position + Vector3.UP * 0.6, true)
		check("my rocket destroys the host's car (seen)", await wait_until(func():
			var r = Net._ent_proxies.get(reid)
			return r == null or not is_instance_valid(r) or (r as Vehicle).destroyed, 8.0))
		step("rocket %d" % reid)
		p.weapons.equip("rifle")
	else:
		step("rocket -1")
	# far away: my own traffic again
	var home := p.global_position
	var far := Vector3.ZERO
	var w := GameWorld.instance
	for l in w.graph.lanes:
		var q: Vector3 = (l as RoadGraph.Lane).point_at(0.0)
		var dd := q.distance_to(home)
		if dd > 750.0 and dd < 1100.0:
			far = q
			break
	w.streaming.load_area_blocking(far, 220.0)
	p.teleport(far + Vector3.UP * 1.0)
	step("far")
	check("far away: host's world switches off", await wait_until(func(): return not Net.world_shared, 10.0))
	check("far away: host's cars disappear", await wait_until(func(): return _amb_cars().is_empty() and _amb_peds().is_empty(), 10.0),
		"%d cars, %d peds" % [_amb_cars().size(), _amb_peds().size()])
	check("far away: my own traffic comes back", await wait_until(func(): return _local_ambient() > 3, 60.0), str(_local_ambient()))
	# and back to the host
	var hp: Vector3 = (Net.proxies()[1] as Node3D).global_position
	w.streaming.load_area_blocking(hp, 220.0)
	p.teleport(hp + Vector3(3, 0.5, 3))
	step("back")
	check("back at the host: his world again", await wait_until(func(): return Net.world_shared and _amb_cars().size() >= 2, 30.0),
		str(_amb_cars().size()))
	check("back at the host: my own traffic is gone again", await wait_until(func(): return _local_ambient() == 0, 20.0), str(_local_ambient()))
	step("done")
	await wait(1.0)
