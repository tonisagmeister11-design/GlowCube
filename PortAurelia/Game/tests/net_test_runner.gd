extends Node

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
	get_tree().create_timer(240.0).timeout.connect(func():
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


func _finish() -> void:
	var failed := results.filter(func(r): return not r[1]).size()
	print("[%s] === %d checks, %d failed ===" % [role, results.size(), failed])
	Net.leave("")
	Game.stop_world()
	get_tree().quit(1 if failed > 0 else 0)


func _player() -> Player:
	return GameWorld.instance.player as Player if GameWorld.instance else null


func _heard(text: String) -> bool:
	for c in chats:
		if String(c[1]) == text:
			return true
	return false


# ------------------------------------------------------------------ host
func _host() -> void:
	Game.player_data = PlayerData.new()
	check("lobby created", Net.host("HostHans"))
	var f := FileAccess.open(args["file"], FileAccess.WRITE)
	f.store_string(Net.lobby_code)
	f.close()
	print("[host] code ", Net.lobby_code)
	var joined: bool = await wait_until(func(): return Net.players.size() == 2, 90.0)
	check("client joined the lobby", joined, "%d players, mode %d" % [Net.players.size(), Net.mode])
	await wait(1.0)
	Net.start_session()
	await wait_until(func(): return _player() != null and Game.state == Game.State.PLAYING, 90.0)
	await wait(2.0)
	var cid := 0
	for id in Net.players:
		if id != 1:
			cid = id
	check("other player visible in the world", await wait_until(func(): return Net.proxies().has(cid), 90.0))
	check("client lands next to the host", await wait_until(func():
		var pr = Net.proxies().get(cid)
		return pr and is_instance_valid(pr) and (pr as Node3D).global_position.distance_to(_player().global_position) < 12.0, 30.0))
	var blips := Net.map_blips()
	check("other player on the map", blips.size() == 1 and String(blips[0]["name"]) == Net.player_name(cid), str(blips.size()))
	await wait_until(func(): return _heard("ready"), 30.0)
	Net.send_hit(cid, 40.0)
	await wait(1.0)
	Game.player_data.money = 5000
	check("send money", Net.send_money(cid, 500) and Game.player_data.money == 4500)
	check("chat from the client arrives", await wait_until(func(): return _heard("hello from client"), 30.0))
	# security: the client now sends a huge hit and spams chat
	_player().health.health = 250.0
	var hp0 := _player().health.health
	await wait_until(func(): return _heard("attack sent"), 20.0)
	await wait(1.5)
	var lost := hp0 - _player().health.health
	check("damage from a client is capped to his weapon", lost > 0.0 and lost <= 12.0 * Combat.HEADSHOT_MULT * 1.1 + 0.1 and not _player().health.dead,
		"lost %.0f" % lost)
	var spam := chats.filter(func(c): return String(c[1]).begins_with("spam")).size()
	check("chat spam is rate limited", spam > 0 and spam <= 3, "%d of 20 arrived" % spam)
	GameWorld.instance.day_night.call("set_hour", 3.0)
	# real gunfire at the other player
	await wait_until(func(): return _heard("shoot me"), 30.0)
	var me := _player()
	me.weapons.give("pistol", 60)
	me.weapons.equip("pistol")
	for i in 4:
		var pr: Node3D = Net.proxies()[cid]
		me.weapons._cooldown = 0.0
		me.weapons._spread_bloom = 0.0
		me.weapons.owned["pistol"]["clip"] = 10
		var from := me.weapons.muzzle_position()
		me.weapons.fire_at(pr.global_position + Vector3.UP * 1.1 + (pr.global_position - from).normalized() * 0.0, true)
		await wait(0.3)
	Net.send_chat("shots fired")
	# the client drives away in a car
	check("other player's car is shown", await wait_until(func():
		var pr = Net.proxies().get(cid)
		return pr and is_instance_valid(pr) and (pr as RemotePlayer).car != null, 30.0))
	check("client finished", await wait_until(func(): return _heard("done"), 40.0))
	await wait(1.0)


# ------------------------------------------------------------------ client
func _client() -> void:
	Game.player_data = PlayerData.new()
	await wait_until(func(): return FileAccess.file_exists(args["file"]) and FileAccess.get_file_as_string(args["file"]).length() >= 10, 60.0)
	await wait(1.0)
	var code := FileAccess.get_file_as_string(args["file"]).strip_edges()
	var d := Net.decode_code(code)
	# wrong key first
	var wrong := Net.encode_code(String(d["ip"]), (int(d["secret"]) + 7) % 65536)
	var reason := [""]
	var conn := func(r): reason[0] = r
	Net.left.connect(conn)
	Net.join("Kai", wrong)
	await wait_until(func(): return reason[0] != "", 20.0)
	check("wrong lobby code is rejected", String(reason[0]).contains("Falscher"), reason[0])
	Net.left.disconnect(conn)
	await wait(1.0)
	Net.join("Kai", code)
	check("joined the lobby", await wait_until(func(): return Net.players.size() == 2, 30.0), Net.status)
	check("world loaded after the host started", await wait_until(func(): return _player() != null and Game.state == Game.State.PLAYING, 120.0))
	check("host visible in the world", await wait_until(func(): return Net.proxies().has(1), 60.0))
	await wait(3.0)
	var p := _player()
	var hp0 := p.health.health + p.health.armor
	var m0 := Game.player_data.money
	Net.send_chat("ready")
	check("hit by the other player", await wait_until(func(): return p.health.health + p.health.armor < hp0 - 30.0, 20.0),
		"%.0f -> %.0f" % [hp0, p.health.health + p.health.armor])
	check("money received from the other player", await wait_until(func(): return Game.player_data.money == m0 + 500, 20.0),
		"%d -> %d" % [m0, Game.player_data.money])
	Net.send_chat("hello from client")
	await wait(1.0)
	# a cheating client: huge damage, chat spam
	Net._hit.rpc_id(1, 1, 99999.0)
	for i in 20:
		Net._chat.rpc_id(1, "spam %d" % i)
	await wait(1.2)
	Net.send_chat("attack sent")
	check("host's clock is synced", await wait_until(func():
		var h := float(GameWorld.instance.day_night.get("hour"))
		return absf(h - 3.0) < 0.3, 20.0), "%.2f" % float(GameWorld.instance.day_night.get("hour")))
	# stand still and get shot with a real gun
	var hp1 := p.health.health + p.health.armor
	Net.send_chat("shoot me")
	await wait_until(func(): return _heard("shots fired"), 20.0)
	await wait(0.8)
	check("hit by real gunfire from the other player", p.health.health + p.health.armor < hp1 - 5.0,
		"%.0f -> %.0f" % [hp1, p.health.health + p.health.armor])
	var car := Vehicle.create("sports")
	GameWorld.instance.add_child(car)
	car.global_position = p.global_position + Vector3(4, 0.8, 0)
	await wait(0.8)
	p.enter_vehicle(car)
	await wait(3.0)
	Net.send_chat("done")
	await wait(2.0)
