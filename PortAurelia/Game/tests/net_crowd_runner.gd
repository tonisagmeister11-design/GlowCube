extends Node
## Crowd test: one host + several clients (separate processes). Everybody must see everybody,
## chat must reach all, the host kicks one player, a player leaves and joins again while the game
## is running (late join).
##   --role host --n 5 --file code.txt      /  --role client --name P2 --file code.txt [--kickme 1]

var args := {}
var results := []
var role := ""
var chats: Array = []
var left_reason := ""


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		args[a[i].trim_prefix("--")] = a[i + 1]
	role = args.get("role", "host") + ":" + args.get("name", "Host")
	Net.chat_received.connect(func(s, t): chats.append([s, t]))
	Net.left.connect(func(r): left_reason = r)
	get_tree().create_timer(420.0).timeout.connect(func():
		print("[%s] TIMEOUT" % role)
		_finish())
	if args.get("role", "host") == "host":
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
	Game.stop_world()
	get_tree().quit(1 if failed > 0 else 0)


func _in_world() -> bool:
	return GameWorld.instance != null and GameWorld.instance.player != null and Game.state == Game.State.PLAYING


func _host() -> void:
	var n := int(args.get("n", "5"))
	Game.player_data = PlayerData.new()
	Net.host("Host")
	var f := FileAccess.open(args["file"], FileAccess.WRITE)
	f.store_string(Net.lobby_code)
	f.close()
	check("%d players in the lobby" % n, await wait_until(func(): return Net.players.size() == n, 150.0), str(Net.players.size()))
	Net.start_session()
	check("everybody in the world", await wait_until(func():
		return _in_world() and Net.players.values().all(func(p): return p["world"]), 240.0))
	check("host sees all %d other players" % (n - 1), await wait_until(func(): return Net.proxies().size() == n - 1, 60.0),
		str(Net.proxies().size()))
	Net.send_chat("@all_in")
	check("chat from every player arrives", await wait_until(func():
		return chats.filter(func(c): return String(c[1]).begins_with("hi from")).size() >= n - 1, 60.0))
	# kick the player that asked for it
	var victim := 0
	for id in Net.players:
		if Net.player_name(id) == "P%d" % n:
			victim = id
	Net.kick(victim)
	check("kicked player is gone", await wait_until(func(): return not Net.players.has(victim), 20.0))
	check("his copy disappears", await wait_until(func(): return not Net.proxies().has(victim), 10.0))
	# P2 leaves and comes back (late join into the running game)
	Net.send_chat("@rejoin")
	check("player rejoins the running game", await wait_until(func():
		for id in Net.players:
			if Net.player_name(id) == "P2" and Net.players[id]["world"] and Net.proxies().has(id):
				return heard("back again")
		return false, 150.0))
	Net.send_chat("@bye")
	await wait(3.0)


func _client() -> void:
	var nm: String = args.get("name", "P")
	var n := int(args.get("n", "5"))
	Game.player_data = PlayerData.new()
	await wait_until(func(): return FileAccess.file_exists(args["file"]) and FileAccess.get_file_as_string(args["file"]).length() >= 10, 60.0)
	var code := FileAccess.get_file_as_string(args["file"]).strip_edges()
	Net.join(nm, code)
	check("joined", await wait_until(func(): return Net.players.has(Net.my_id()) and Net.players.size() >= 2, 60.0), Net.status)
	check("in the world", await wait_until(_in_world, 240.0))
	check("sees all %d other players" % (n - 1), await wait_until(func(): return Net.proxies().size() == n - 1, 90.0),
		str(Net.proxies().size()))
	await wait_until(func(): return heard("@all_in"), 60.0)
	Net.send_chat("hi from " + nm)
	check("hears every other player", await wait_until(func():
		return chats.filter(func(c): return String(c[1]).begins_with("hi from")).size() >= n - 1, 60.0),
		str(chats.filter(func(c): return String(c[1]).begins_with("hi from")).size()))
	if nm == "P%d" % n:
		check("kicked by the host with a message", await wait_until(func(): return left_reason.contains("entfernt"), 30.0), left_reason)
		return
	check("sees the kicked player leave", await wait_until(func(): return Net.proxies().size() == n - 2, 40.0), str(Net.proxies().size()))
	if nm == "P2":
		await wait_until(func(): return heard("@rejoin"), 60.0)
		Net.leave("")
		await wait(2.0)
		Game.to_main_menu()
		await wait(4.0)
		Net.join(nm, code)
		check("rejoined the running game", await wait_until(func(): return _in_world() and Net.proxies().size() == n - 2, 180.0),
			str(Net.proxies().size()))
		await wait(1.0)
		Net.send_chat("back again")
	await wait_until(func(): return heard("@bye"), 120.0)
