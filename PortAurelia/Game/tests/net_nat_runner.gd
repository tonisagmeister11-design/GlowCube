extends Node
## Host and friend on different (simulated) home networks:
##   --role host --file code.txt --dir out
##   --role client --file code.txt --dir out --expect direkt|Relay [--wrong 1]

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
	get_tree().create_timer(300.0).timeout.connect(func():
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
	await wait(0.5)
	get_tree().quit(1 if failed > 0 else 0)


func _player() -> Player:
	return GameWorld.instance.player as Player if GameWorld.instance else null


func _write(name: String, text: String) -> void:
	var f := FileAccess.open(args["dir"].path_join(name), FileAccess.WRITE)
	f.store_string(text)
	f.close()


func _read(name: String) -> String:
	var p: String = args["dir"].path_join(name)
	return FileAccess.get_file_as_string(p) if FileAccess.file_exists(p) else ""


# ------------------------------------------------------------------ host
func _host() -> void:
	Game.player_data = PlayerData.new()
	check("lobby created", Net.host("Host"))
	check("lobby code ready", await wait_until(func(): return Net.lobby_ready, 15.0), Net.lobby_code)
	_write("code.txt", Net.lobby_code)
	print("[host] code %s  public %s  cands %s" % [Net.lobby_code, Net.public_ip, str(Net.host_cands())])
	if not OS.has_environment("HH_EXPECT_OFFLINE"):
		check("lobby says the internet is ready", await wait_until(func(): return Net.internet_state == "ok", 20.0), Net.status)
	var n := int(args.get("n", "2"))
	check("friend joined from another network", await wait_until(func(): return Net.players.size() == n, 120.0),
		"%d players" % Net.players.size())
	var cid := 0
	for id in Net.players:
		if id != 1:
			cid = id
			if Net._peer and Net._peer.get_peer(id):
				print("[host] %s connected from %s:%d  (%s)" % [Net.player_name(id), Net._peer.get_peer(id).get_remote_address(),
					Net._peer.get_peer(id).get_remote_port(), Net.peer_link_text(id)])
	await wait(1.0)
	Net.start_session()
	await wait_until(func(): return _player() != null and Game.state == Game.State.PLAYING, 120.0)
	check("friends visible in the world", await wait_until(func(): return Net.proxies().size() == n - 1, 90.0))
	await wait_until(func(): return chats.filter(func(c): return String(c[1]) == "ready").size() >= n - 1, 60.0)
	# walk a circle, then stand still and tell the friend where we are
	var p := _player()
	var c0 := p.global_position
	for i in 150:
		var a := i / 150.0 * TAU
		p.global_position = c0 + Vector3(cos(a) * 6.0 - 6.0, 0, sin(a) * 6.0)
		await get_tree().physics_frame
	await wait(0.6)
	var fp := p.global_position
	_write("hostpos.txt", "%f %f %f" % [fp.x, fp.y, fp.z])
	Net.send_chat("stopped")
	check("chat from the friend arrives", await wait_until(func(): return heard("hello host"), 30.0))
	# stability: stay together for a while
	await wait_until(func(): return chats.filter(func(c): return String(c[1]) == "bye").size() >= n - 1, 90.0)
	check("friends still connected at the end", Net.players.size() == n, "%d players" % Net.players.size())


# ------------------------------------------------------------------ client
func _client() -> void:
	Game.player_data = PlayerData.new()
	await wait_until(func(): return _read("code.txt").length() >= 10, 60.0)
	var code := _read("code.txt").strip_edges()
	if args.get("wrong", "") == "1":
		var d := Net.decode_code(code)
		var reason := [""]
		var conn := func(r): reason[0] = r
		Net.left.connect(conn)
		var t0 := Time.get_ticks_msec()
		Net.join("Kai", Net.encode_code(String(d["ip"]), (int(d["secret"]) + 11) % 65536))
		await wait_until(func(): return reason[0] != "", 60.0)
		check("wrong code fails with a message", String(reason[0]) != "", "%s (%.0f s)" % [reason[0], (Time.get_ticks_msec() - t0) / 1000.0])
		Net.left.disconnect(conn)
		await wait(1.0)
	var t1 := Time.get_ticks_msec()
	Net.join("Kai", code)
	var n := int(args.get("n", "2"))
	check("joined the lobby from another network", await wait_until(func(): return Net.players.size() >= 2, 90.0), Net.status)
	var secs := (Time.get_ticks_msec() - t1) / 1000.0
	print("[client] connected via %s after %.1f s" % [Net.link_kind, secs])
	if args.has("expect"):
		check("connection type is %s" % args["expect"], Net.link_kind == args["expect"], Net.link_kind)
	check("world loaded", await wait_until(func(): return _player() != null and Game.state == Game.State.PLAYING, 120.0))
	check("host visible in the world", await wait_until(func(): return Net.proxies().has(1), 60.0))
	check("all other players visible", await wait_until(func(): return Net.proxies().size() == n - 1, 60.0), str(Net.proxies().size()))
	await wait(2.0)
	var pr: Node3D = Net.proxies()[1]
	var start := pr.global_position
	Net.send_chat("ready")
	var moved := [0.0]
	var t := 0.0
	while not heard("stopped") and t < 30.0:
		moved[0] = maxf(moved[0], pr.global_position.distance_to(start))
		await wait(0.05)
		t += 0.05
	check("sees the host walking", moved[0] > 5.0, "%.1f m" % moved[0])
	await wait(1.0)
	var v := _read("hostpos.txt").split(" ")
	var hp := Vector3(float(v[0]), float(v[1]), float(v[2])) if v.size() == 3 else Vector3.INF
	await wait(0.5)
	var err := pr.global_position.distance_to(hp)
	check("host stands exactly where he really is", err < 0.3, "%.2f m off" % err)
	var rtt := -1
	if Net._peer and Net._peer.get_peer(1):
		rtt = int(Net._peer.get_peer(1).get_statistic(ENetPacketPeer.PEER_ROUND_TRIP_TIME))
	print("[client] ping %d ms" % rtt)
	Net.send_chat("hello host")
	# stay connected (keep-alives through the routers / relay)
	await wait(20.0)
	check("still connected after 20 s", Net.is_online() and Net.proxies().has(1), Net.status)
	Net.send_chat("bye")
	await wait(2.0)
