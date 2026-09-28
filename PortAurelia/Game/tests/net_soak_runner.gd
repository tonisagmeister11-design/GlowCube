extends Node
## Long session: host and friend travel through the city together (sometimes apart), shoot, drive.
## Watches for leaks (nodes, memory, replicated things), disconnects and bandwidth.
##   --role host|client --file code.txt --secs 480

var args := {}
var results := []
var role := ""
var chats: Array = []
var secs := 480.0


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		args[a[i].trim_prefix("--")] = a[i + 1]
	role = args.get("role", "host")
	secs = float(args.get("secs", "480"))
	Net.chat_received.connect(func(s, t): chats.append([s, t]))
	get_tree().create_timer(secs + 400.0).timeout.connect(func():
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
		await wait(0.2)
		t += 0.2
	return cond.call()


func heard(text: String) -> bool:
	return chats.any(func(c): return String(c[1]) == text)


func _finish() -> void:
	var failed := results.filter(func(r): return not r[1]).size()
	print("[%s] === %d checks, %d failed ===" % [role, results.size(), failed])
	Net.leave("")
	get_tree().quit(1 if failed > 0 else 0)


func me() -> Player:
	return GameWorld.instance.player as Player if GameWorld.instance else null


func _nodes() -> int:
	return int(Performance.get_monitor(Performance.OBJECT_NODE_COUNT))


func _mem() -> float:
	return Performance.get_monitor(Performance.MEMORY_STATIC) / 1048576.0


func _road_point(near: Vector3, rmin: float, rmax: float) -> Vector3:
	var w := GameWorld.instance
	var ids := w.graph.lanes_near(near, rmax)
	for attempt in 30:
		if ids.is_empty():
			break
		var l: RoadGraph.Lane = w.graph.lanes[ids[randi() % ids.size()]]
		var p := l.point_at(randf() * l.length)
		var d := p.distance_to(near)
		if d >= rmin and d <= rmax:
			return p
	return near


# ================================================================== host
func _host() -> void:
	Game.player_data = PlayerData.new()
	Net.host("Toni")
	await wait_until(func(): return Net.lobby_ready, 20.0)   # internet mode: code final after the check
	var f := FileAccess.open(args["file"], FileAccess.WRITE)
	f.store_string(Net.lobby_code)
	f.close()
	await wait_until(func(): return Net.players.size() == 2, 120.0)
	await wait(0.5)
	Net.start_session()
	await wait_until(func(): return me() != null and Game.state == Game.State.PLAYING, 120.0)
	var p := me()
	p.health.invulnerable = true
	await wait_until(func(): return Net.proxies().size() == 1, 90.0)
	var cid: int = Net.proxies().keys()[0]
	await wait(10.0)
	var n0 := _nodes()
	var m0 := _mem()
	var home := p.global_position
	var t0 := Time.get_ticks_msec()
	var max_ents := 0
	var max_kbs := 0.0
	var disconnects := 0
	var last_stat := 0
	Net._peer.host.pop_statistic(ENetConnection.HOST_TOTAL_SENT_DATA)
	var hop := 0
	while (Time.get_ticks_msec() - t0) / 1000.0 < secs:
		hop += 1
		# travel: a new place on the road not too far from home, sometimes drive there
		var dst := _road_point(home, 80.0, 700.0)
		GameWorld.instance.streaming.load_area_blocking(dst, 160.0)
		if p.is_in_vehicle():
			p.exit_vehicle()
			await wait(0.3)
		p.teleport(dst + Vector3.UP * 0.8)
		Net.send_chat("@at " + var_to_str(dst))
		await wait(1.5)
		if hop % 3 == 0:
			var car := Vehicle.create("sedan")
			GameWorld.instance.add_child(car)
			car.global_transform = Transform3D(Basis.IDENTITY, p.global_position + Vector3(3, 0.8, 0))
			await wait(0.5)
			p.enter_vehicle(car)
			Input.action_press("accelerate")
			await wait(3.0)
			Input.action_release("accelerate")
		if hop % 4 == 1:
			p.weapons.give("pistol", 50)
			p.weapons.equip("pistol")
			for i in 3:
				p.weapons._cooldown = 0.0
				p.weapons.fire_at(p.global_position + Vector3(10, 1, 3), true)
				await wait(0.3)
		await wait(4.0)
		max_ents = maxi(max_ents, Net._ents.size())
		if not Net.players.has(cid):
			disconnects += 1
		var now := Time.get_ticks_msec()
		if now - last_stat > 30000:
			var dt := (now - last_stat) / 1000.0 if last_stat > 0 else (now - t0) / 1000.0
			last_stat = now
			var kbs := Net._peer.host.pop_statistic(ENetConnection.HOST_TOTAL_SENT_DATA) / maxf(dt, 1.0) / 1024.0
			max_kbs = maxf(max_kbs, kbs)
			print("[host] t=%3ds nodes %d mem %.0f MB ents %d cars %d peds %d upload %.1f KB/s shared %s" % [
				(now - t0) / 1000, _nodes(), _mem(), Net._ents.size(), GameWorld.instance.traffic.vehicles.size(),
				GameWorld.instance.peds.peds.size(), kbs, Net._world_share.is_shared(cid) if Net._world_share else false])
	if p.is_in_vehicle():
		p.exit_vehicle()
	Net.send_chat("@end")
	await wait_until(func(): return heard("@client_done"), 60.0)
	check("friend stayed connected the whole time", disconnects == 0 and Net.players.has(cid), "%d drops" % disconnects)
	check("no leak of nodes on the host", _nodes() < n0 * 1.5 + 400, "%d -> %d" % [n0, _nodes()])
	check("no memory leak on the host", _mem() < m0 * 1.35 + 60.0, "%.0f -> %.0f MB" % [m0, _mem()])
	check("replicated things stay bounded", max_ents < 400, "max %d" % max_ents)
	check("upload stays small", max_kbs < 60.0, "max %.1f KB/s" % max_kbs)


# ================================================================== client
func _client() -> void:
	Game.player_data = PlayerData.new()
	await wait_until(func(): return FileAccess.file_exists(args["file"]) and FileAccess.get_file_as_string(args["file"]).length() >= 10, 60.0)
	Net.join("Kai", FileAccess.get_file_as_string(args["file"]).strip_edges())
	check("joined", await wait_until(func(): return Net.players.size() == 2, 60.0), Net.status)
	await wait_until(func(): return me() != null and Game.state == Game.State.PLAYING, 150.0)
	var p := me()
	p.health.invulnerable = true
	await wait_until(func(): return Net.proxies().has(1), 60.0)
	await wait(10.0)
	var n0 := _nodes()
	var m0 := _mem()
	var seen_at := -1
	var max_proxies := 0
	var shared_flips := 0
	var was_shared := Net.world_shared
	var n := 0
	while not heard("@end"):
		await wait(0.5)
		# follow the host to each new place (sometimes stay behind a while)
		var at := -1
		for i in range(chats.size() - 1, -1, -1):
			if String(chats[i][1]).begins_with("@at "):
				at = i
				break
		if at != seen_at and at >= 0:
			seen_at = at
			n += 1
			var dst: Vector3 = str_to_var(String(chats[at][1]).substr(4))
			if n % 5 != 0:
				GameWorld.instance.streaming.load_area_blocking(dst, 160.0)
				if p.is_in_vehicle():
					p.exit_vehicle()
				p.teleport(dst + Vector3(4, 0.8, 4))
		max_proxies = maxi(max_proxies, Net._ent_proxies.size())
		if Net.world_shared != was_shared:
			was_shared = Net.world_shared
			shared_flips += 1
		if not Net.is_online():
			break
	await wait(2.0)
	check("stayed connected the whole session", Net.is_online() and Net.proxies().has(1), Net.status)
	check("shared world switched on/off as we moved", shared_flips >= 2, "%d switches" % shared_flips)
	check("no leak of nodes on the friend's side", _nodes() < n0 * 1.5 + 400, "%d -> %d" % [n0, _nodes()])
	check("no memory leak on the friend's side", _mem() < m0 * 1.35 + 60.0, "%.0f -> %.0f MB" % [m0, _mem()])
	check("host's things on my side stay bounded", max_proxies < 250, "max %d" % max_proxies)
	Net.send_chat("@client_done")
	await wait(1.0)
