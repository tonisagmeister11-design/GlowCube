extends Node
## Creative mode only for the lobby owner (silent), money/weapon drops and gifts.
##   --role host|client --file code.txt

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
		await wait(0.1)
		t += 0.1
	return cond.call()


func step(name: String) -> void:
	await wait(1.2)   # the host limits chat messages per second
	Net.send_chat("@" + name)


func step_arg(name: String):
	for i in range(chats.size() - 1, -1, -1):
		var t := String(chats[i][1])
		if t == "@" + name:
			return true
		if t.begins_with("@" + name + " "):
			return str_to_var(t.substr(name.length() + 2))
	return null


func await_arg(name: String, timeout := 60.0):
	await wait_until(func(): return step_arg(name) != null, timeout)
	return step_arg(name)


func _finish() -> void:
	var failed := results.filter(func(r): return not r[1]).size()
	print("[%s] === %d checks, %d failed ===" % [role, results.size(), failed])
	Net.leave("")
	Game.stop_world()
	get_tree().quit(1 if failed > 0 else 0)


func me() -> Player:
	return GameWorld.instance.player as Player if GameWorld.instance else null


func _drop_near(pos: Vector3) -> Pickup:
	for d in Net._drops.values():
		if is_instance_valid(d) and (d as Node3D).global_position.distance_to(pos) < 6.0:
			return d
	return null


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
	await wait_until(func(): return Net.proxies().size() == 1, 90.0)
	var cid: int = Net.proxies().keys()[0]
	await await_arg("ready", 60.0)
	# the friend gives me a weapon
	var got = await await_arg("gave_weapon", 30.0)
	check("I receive the weapon the friend gives me", await wait_until(func(): return p.weapons.has_weapon("shotgun"), 10.0))
	# the friend drops money, I pick it up
	var dpos = await await_arg("dropped", 30.0)
	var m0 := Game.player_data.money
	await wait_until(func(): return _drop_near(dpos) != null, 10.0)
	var dr: Pickup = _drop_near(dpos)
	var dl := []
	for d in Net._drops.values():
		if is_instance_valid(d):
			dl.append((d as Node3D).global_position)
	check("I see the money the friend dropped", dr != null, "at %s, drops %s, info %s" % [dpos, dl, Net._drop_info])
	if dr:
		p.teleport(dr.global_position + Vector3.UP * 0.3)
	check("I pick up the friend's money", await wait_until(func(): return Game.player_data.money == m0 + 3000, 10.0),
		"%d -> %d" % [m0, Game.player_data.money])
	check("the money is gone from the ground", await wait_until(func(): return _drop_near(dpos) == null, 5.0))
	await step("host_picked")
	# creative mode (only the owner can), silent for the friend
	CreativeMode.set_enabled(true)
	check("I (the lobby owner) can use the creative mode", Game.player_data.is_creative())
	await wait(1.0)
	await step("creative_on")
	# creative: take a lot of money and gift it
	Game.player_data.add_money(5000000, "creative")
	check("gift of $1,000,000 to the friend", Net.send_money(cid, 1000000))
	# drop money and a weapon for him
	Net.drop_money(20000)
	Net.drop_weapon("rpg")
	check("in creative I keep the dropped weapon", p.weapons.has_weapon("rpg"))
	await step("drops " + var_to_str(p.global_position))
	await await_arg("got_drops", 40.0)
	await await_arg("shot_host", 30.0)
	await wait(1.0)
	check("I really lose no health in creative", p.health.health >= p.health.max_health - 0.1, "%.0f" % p.health.health)
	await step("done")
	await await_arg("bye", 30.0)


# ================================================================== client
func _client() -> void:
	Game.player_data = PlayerData.new()
	Game.player_data.world_state["creative"] = true   # even a creative save: in someone's lobby you play normally
	await wait_until(func(): return FileAccess.file_exists(args["file"]) and FileAccess.get_file_as_string(args["file"]).length() >= 10, 60.0)
	Net.join("Kai", FileAccess.get_file_as_string(args["file"]).strip_edges())
	check("joined", await wait_until(func(): return Net.players.size() == 2, 60.0), Net.status)
	await wait_until(func(): return me() != null and Game.state == Game.State.PLAYING, 150.0)
	var p := me()
	await wait_until(func(): return Net.proxies().has(1), 60.0)
	await wait(2.0)
	check("I (joined) am not in creative mode", not Game.player_data.is_creative() and not p.health.invulnerable)
	CreativeMode.set_enabled(true)
	await wait(0.6)
	check("I (joined) cannot switch the creative mode on", not Game.player_data.is_creative() and not p.health.invulnerable)
	var pm := GameWorld.instance.find_child("PauseMenu", true, false)
	var has_btn := false
	if pm:
		for b in pm.find_children("*", "Button", true, false):
			if String((b as Button).text).contains("KREATIV"):
				has_btn = true
	check("no creative button in my pause menu", pm != null and not has_btn, "menu %s" % pm)
	check("the lobby code is shown in my game", Net.lobby_code.length() >= 10, Net.lobby_code)
	Game.player_data.money = 10000
	await step("ready")
	# give the host a weapon (I lose it)
	p.weapons.give("shotgun", 20)
	check("I can give the host a weapon", Net.send_weapon(1, "shotgun") and not p.weapons.has_weapon("shotgun"))
	await step("gave_weapon")
	# drop money for him
	check("I can drop money", Net.drop_money(3000) and Game.player_data.money == 7000, "$%d" % Game.player_data.money)
	await wait(0.3)
	var myd := _drop_near(p.global_position)
	check("I see my own dropped money", myd != null)
	await step("dropped " + var_to_str(myd.global_position if myd else p.global_position))
	await await_arg("host_picked", 30.0)
	check("money picked up by the host is gone for me too", await wait_until(func(): return _drop_near(p.global_position) == null, 5.0))
	var chats_before := chats.size()
	var m0 := Game.player_data.money
	await await_arg("creative_on", 30.0)
	await wait(1.5)
	var hint := false
	for i in range(chats_before, chats.size()):
		var ct := String(chats[i][1]).to_lower()
		if not ct.begins_with("@") and (ct.contains("kreativ") or ct.contains("creative")):
			hint = true
	check("nothing in the chat when the host goes creative", not hint)
	check("I receive the host's $1,000,000 gift", await wait_until(func(): return Game.player_data.money == m0 + 1000000, 10.0),
		"%d -> %d" % [m0, Game.player_data.money])
	var hp = await await_arg("drops", 30.0)
	await wait(0.8)
	var md := _drop_near(hp)
	var mw: Pickup = null
	for d in Net._drops.values():
		if is_instance_valid(d) and (d as Pickup).kind == "weapon":
			mw = d
	check("I see the money and the weapon the host dropped", md != null and mw != null)
	var m1 := Game.player_data.money
	if mw:
		p.teleport(mw.global_position + Vector3.UP * 0.3)
		await wait(1.0)
	for d in Net._drops.values():
		if is_instance_valid(d) and (d as Pickup).kind == "money":
			p.teleport((d as Node3D).global_position + Vector3.UP * 0.3)
			await wait(1.0)
	check("I pick up the host's money", await wait_until(func(): return Game.player_data.money == m1 + 20000, 8.0),
		"%d -> %d" % [m1, Game.player_data.money])
	check("I pick up the host's rocket launcher", await wait_until(func(): return p.weapons.has_weapon("rpg"), 8.0))
	await step("got_drops")
	# shoot the (creative) host: his health bar goes down like normal
	var pr: RemotePlayer = Net.proxies()[1]
	var before: float = pr._hp
	for i in 3:
		Net.send_hit(1, 30.0)
		await wait(0.3)
	check("the creative host looks hurt when I hit him", await wait_until(func(): return pr._hp < before - 20.0, 5.0), "%.0f -> %.0f" % [before, pr._hp])
	await step("shot_host")
	await await_arg("done", 30.0)
	await step("bye")
	await wait(1.0)
