extends Node
## Multiplayer (autoload "Net").
##
## The player who creates a lobby is the server (host): his game opens UDP port 7777 (automatically
## through the router with UPnP when possible) and every message of the other players goes
## through his game, where it is checked before it is passed on:
##   * joining needs the secret key inside the lobby code (strangers who find the port can't join),
##     the same game version, a free slot (max 8) and a clean name; the host can kick players
##   * clients cannot talk to each other directly (no relay), only to the host
##   * every message type has a rate limit; numbers must be finite and in range, damage is capped
##     and needs the attacker near the victim, money transfers are capped, chat is cleaned
##   * no objects are ever decoded from the network, only plain values
## Every player simulates traffic, pedestrians and police in his own game; only the players
## (position, animation, car, shots, hits, money, chat) and the host's clock/weather are shared,
## so the host's laptop has little extra work.
##
## Lobby code: 10 characters = host IP address (32 bits) + secret key (16 bits). In the same
## network the host is also found by a LAN broadcast carrying the key, so the code works there
## even if the router doesn't support UPnP.

signal roster_changed
signal status_changed(text: String)
signal chat_received(sender: String, text: String)
signal left(reason: String)

const PORT := 7777
const DISCOVERY_PORT := 7778
const MAX_PLAYERS := 8
const PROTOCOL := 3
const STATE_HZ := 15.0
const MAX_HIT := 260.0          # one hit never takes more than this (a rocket still kills a 250 hp player)
const MAX_HIT_RANGE := 260.0
const MAX_TRANSFER := 50000
const ALPHABET := "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
const COLORS := [Color(0.2, 0.75, 1.0), Color(1.0, 0.55, 0.1), Color(0.55, 1.0, 0.3), Color(1.0, 0.3, 0.75),
	Color(1.0, 0.95, 0.25), Color(0.7, 0.45, 1.0), Color(0.25, 1.0, 0.85), Color(1.0, 0.35, 0.3)]

enum Mode { OFFLINE, HOST, CLIENT }

var mode := Mode.OFFLINE
var my_name := "Spieler"
var lobby_code := ""
var status := ""
var in_game := false            # the host has started the session (late joiners go straight in)
var players := {}               # peer id -> {name, color, world(bool), outfit, st(Array)}
var upnp_ok := false

var _peer: ENetMultiplayerPeer
var _secret := 0
var _host_ip := ""
var _upnp: UPNP
var _upnp_thread: Thread
var _disc_send: PacketPeerUDP
var _disc_t := 0.0
var _rates := {}                # "id:kind" -> [window start msec, count]
var _send_t := 0.0
var _clock_t := 0.0
var _proxies := {}              # peer id -> RemotePlayer
var _last_attacker := -1
var _last_attack_ms := 0
var _world_connected := false
var _pending_join := {}         # id -> name (authenticated, waiting for the connection)
var _join_secret := 0
var _join_target := ""
var _chat_log: Array = []       # [[name, text, color]]


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	multiplayer.peer_connected.connect(_on_peer_connected)
	multiplayer.peer_disconnected.connect(_on_peer_disconnected)
	multiplayer.connected_to_server.connect(_on_connected)
	multiplayer.connection_failed.connect(func(): _fail.call_deferred("Keine Verbindung zum Host. Code richtig? Läuft die Lobby noch?"))
	multiplayer.server_disconnected.connect(func(): _fail.call_deferred("Die Verbindung zum Host wurde getrennt."))
	multiplayer.peer_authenticating.connect(_on_authenticating)
	multiplayer.peer_authentication_failed.connect(func(_id): if mode == Mode.CLIENT: _fail.call_deferred("Beitritt abgelehnt."))
	Events.world_ready.connect(_on_world_ready)
	Events.player_died.connect(_on_local_death)


func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_CLOSE_REQUEST:
		leave("")   # closes the router port again


func _exit_tree() -> void:
	if _upnp_thread and _upnp_thread.is_started():
		_upnp_thread.wait_to_finish()


func is_online() -> bool:
	return mode != Mode.OFFLINE


func is_host() -> bool:
	return mode == Mode.HOST


func my_id() -> int:
	return multiplayer.get_unique_id() if is_online() else 1


func _set_status(t: String) -> void:
	status = t
	status_changed.emit(t)


# ================================================================== lobby: host
func host(player_name: String) -> bool:
	leave("")
	my_name = clean_name(player_name)
	_peer = ENetMultiplayerPeer.new()
	var err := _peer.create_server(PORT, MAX_PLAYERS - 1)
	if err != OK:
		_set_status("Port %d ist belegt – läuft das Spiel schon ein zweites Mal?" % PORT)
		_peer = null
		return false
	_setup_multiplayer()
	multiplayer.multiplayer_peer = _peer
	mode = Mode.HOST
	in_game = false
	_secret = randi_range(1, 65535)
	players = {1: {"name": my_name, "color": COLORS[0], "world": false, "outfit": {}, "st": []}}
	_host_ip = _lan_ip()
	lobby_code = encode_code(_host_ip, _secret)
	_set_status("Lobby offen. Im gleichen WLAN funktioniert der Code sofort. Internet wird eingerichtet ...")
	_disc_send = PacketPeerUDP.new()
	_disc_send.set_broadcast_enabled(true)
	if not OS.has_environment("HH_NO_UPNP") and not "--no-upnp" in OS.get_cmdline_user_args():
		_upnp_thread = Thread.new()
		_upnp_thread.start(_upnp_setup)
	else:
		_set_status("Lobby offen (nur lokales Netzwerk).")
	roster_changed.emit()
	return true


func _setup_multiplayer() -> void:
	var sm := multiplayer as SceneMultiplayer
	sm.server_relay = false            # clients only ever talk to the host
	sm.allow_object_decoding = false   # never decode objects from the network
	sm.auth_callback = _auth_received
	sm.auth_timeout = 5.0


func _upnp_setup() -> void:
	var u := UPNP.new()
	var ok := false
	var ext := ""
	if u.discover(1500, 2) == UPNP.UPNP_RESULT_SUCCESS and u.get_gateway() and u.get_gateway().is_valid_gateway():
		if u.add_port_mapping(PORT, PORT, "Harbor Heat", "UDP", 0) == UPNP.UPNP_RESULT_SUCCESS:
			ext = u.query_external_address()
			ok = ext != "" and ext.count(".") == 3
	call_deferred("_upnp_done", u, ok, ext)


func _upnp_done(u: UPNP, ok: bool, ext: String) -> void:
	if _upnp_thread:
		_upnp_thread.wait_to_finish()
		_upnp_thread = null
	if mode != Mode.HOST:
		if ok:
			u.delete_port_mapping(PORT, "UDP")
		return
	upnp_ok = ok
	if ok:
		_upnp = u
		lobby_code = encode_code(ext, _secret)
		_set_status("Lobby offen – der Code funktioniert im Internet und im gleichen WLAN.")
	else:
		_set_status("Lobby offen – der Code funktioniert im gleichen WLAN. Für Internet: im Router Port %d (UDP) freigeben oder ein VPN wie Radmin VPN / ZeroTier benutzen." % PORT)
	roster_changed.emit()


## Host: start the game for everyone in the lobby.
func start_session() -> void:
	if mode != Mode.HOST:
		return
	in_game = true
	for id in players:
		if id != 1:
			_start_session.rpc_id(id)
	_begin_game()


func kick(id: int) -> void:
	if mode != Mode.HOST or not players.has(id) or id == 1:
		return
	_kicked.rpc_id(id, "Du wurdest vom Host aus der Lobby entfernt.")
	get_tree().create_timer(0.3).timeout.connect(func():
		if _peer and mode == Mode.HOST:
			_peer.disconnect_peer(id))


# ================================================================== lobby: client
func join(player_name: String, code: String) -> void:
	leave("")
	my_name = clean_name(player_name)
	var d := decode_code(code)
	if d.is_empty():
		_set_status("Ungültiger Code. Er hat 10 Zeichen, z. B. ABCDE-FGH23.")
		return
	_join_secret = int(d["secret"])
	_join_target = String(d["ip"])
	mode = Mode.CLIENT
	_set_status("Suche Lobby ...")
	# same network? the host announces its key by broadcast
	var lan := await _discover(_join_secret, 1.6)
	if mode != Mode.CLIENT:
		return
	if lan != "":
		_join_target = lan
	_set_status("Verbinde mit %s ..." % ("Lobby im WLAN" if lan != "" else "Lobby"))
	_peer = ENetMultiplayerPeer.new()
	if _peer.create_client(_join_target, PORT) != OK:
		_fail("Verbindung konnte nicht gestartet werden.")
		return
	_setup_multiplayer()
	multiplayer.multiplayer_peer = _peer


func _discover(secret: int, timeout: float) -> String:
	var udp := PacketPeerUDP.new()
	if udp.bind(DISCOVERY_PORT) != OK:
		return ""
	var t := 0.0
	var found := ""
	while t < timeout and found == "":
		await get_tree().process_frame
		t += get_process_delta_time()
		while udp.get_available_packet_count() > 0:
			var pkt := udp.get_packet().get_string_from_ascii()
			var parts := pkt.split("|")
			if parts.size() >= 2 and parts[0] == "HH%d" % PROTOCOL and parts[1].is_valid_int() and int(parts[1]) == secret:
				found = udp.get_packet_ip()
	udp.close()
	return found


func _on_authenticating(id: int) -> void:
	if mode == Mode.CLIENT and id == 1:
		var hello := {"p": PROTOCOL, "k": _join_secret, "n": my_name}
		multiplayer.send_auth(1, var_to_bytes(hello))
		multiplayer.complete_auth(1)


## Authentication data: on the host the joiner's hello, on the client the host's answer.
func _auth_received(id: int, data: PackedByteArray) -> void:
	if mode == Mode.HOST:
		var reason := ""
		var hello = bytes_to_var(data) if data.size() < 512 else null
		if not hello is Dictionary:
			reason = "Ungültige Anfrage."
		elif int(hello.get("p", 0)) != PROTOCOL:
			reason = "Andere Spielversion – bitte dieselbe Version wie der Host benutzen."
		elif int(hello.get("k", -1)) != _secret:
			reason = "Falscher Lobby-Code."
		elif players.size() + _pending_join.size() >= MAX_PLAYERS:
			reason = "Die Lobby ist voll (%d Spieler)." % MAX_PLAYERS
		if reason != "":
			# the joiner disconnects after reading the reason; the auth timeout drops it otherwise
			multiplayer.send_auth(id, var_to_bytes({"ok": false, "r": reason}))
			return
		_pending_join[id] = _unique_name(clean_name(String(hello.get("n", "Spieler"))))
		multiplayer.send_auth(id, var_to_bytes({"ok": true}))
		multiplayer.complete_auth(id)
	elif mode == Mode.CLIENT:
		var ans = bytes_to_var(data) if data.size() < 1024 else null
		if ans is Dictionary and not bool(ans.get("ok", false)):
			# never tear the peer down inside the multiplayer poll: do it next frame
			_fail.call_deferred(String(ans.get("r", "Beitritt abgelehnt.")))


func _on_connected() -> void:
	_set_status("Verbunden – warte auf den Host ...")


func _on_peer_connected(id: int) -> void:
	if mode != Mode.HOST:
		return
	if not _pending_join.has(id):
		_peer.disconnect_peer(id)
		return
	var used := []
	for p in players.values():
		used.append(p["color"])
	var col: Color = COLORS[players.size() % COLORS.size()]
	for c in COLORS:
		if not c in used:
			col = c
			break
	players[id] = {"name": _pending_join[id], "color": col, "world": false, "outfit": {}, "st": []}
	_pending_join.erase(id)
	_broadcast_roster()
	if in_game:
		_start_session.rpc_id(id)
	_system_message("%s ist der Lobby beigetreten." % players[id]["name"])


func _on_peer_disconnected(id: int) -> void:
	if mode != Mode.HOST:
		return
	_pending_join.erase(id)
	if players.has(id):
		var n: String = players[id]["name"]
		players.erase(id)
		_remove_proxy(id)
		for other in players:
			if other != 1:
				_player_left.rpc_id(other, id)
		_broadcast_roster()
		_system_message("%s hat das Spiel verlassen." % n)


func _broadcast_roster() -> void:
	var r := _roster_data()
	for id in players:
		if id != 1:
			_roster.rpc_id(id, r, in_game, lobby_code)
	roster_changed.emit()


func _roster_data() -> Dictionary:
	var r := {}
	for id in players:
		r[id] = {"name": players[id]["name"], "color": players[id]["color"], "world": players[id]["world"]}
	return r


## Leave the lobby / session (also called when going back to the main menu).
func leave(reason := "") -> void:
	if mode == Mode.OFFLINE:
		return
	for id in _proxies.keys():
		_remove_proxy(id)
	if _upnp:
		_upnp.delete_port_mapping(PORT, "UDP")
		_upnp = null
	if _disc_send:
		_disc_send.close()
		_disc_send = null
	multiplayer.multiplayer_peer = OfflineMultiplayerPeer.new()
	if _peer:
		_peer.close()
		_peer = null
	mode = Mode.OFFLINE
	players.clear()
	_pending_join.clear()
	in_game = false
	lobby_code = ""
	_world_connected = false
	roster_changed.emit()
	if reason != "":
		left.emit(reason)


func _fail(reason: String) -> void:
	if mode == Mode.OFFLINE and reason == status:
		return
	var was_game := _world_connected
	leave(reason)
	_set_status(reason)
	if was_game:
		Events.big_message.emit("OFFLINE", reason, 4.0)
		Events.notify.emit(reason + " Du spielst alleine weiter.", 5.0)


# ================================================================== session
func _begin_game() -> void:
	# every player keeps his own savegame (money, weapons, cars) in multiplayer
	var slot := SaveManager.latest_slot()
	if slot != -99:
		Game.load_game(slot)
	else:
		Game.start_free_roam()
	Game.free_roam = true


func _on_world_ready() -> void:
	if not is_online():
		return
	_world_connected = true
	var p := _local_player()
	if p:
		p.health.damaged.connect(_on_local_damaged)
	var outfit: Dictionary = p.model.outfit if p else {}
	if is_host():
		players[1]["world"] = true
		players[1]["outfit"] = outfit
		for id in players:
			if id != 1:
				_in_world.rpc_id(id, 1, outfit)
		_broadcast_roster()
	else:
		_hello_world.rpc_id(1, outfit)
	_ensure_hud()


func _local_player() -> Player:
	var w := GameWorld.instance
	if w and is_instance_valid(w) and w.player:
		return w.player as Player
	return null


func _process(delta: float) -> void:
	if mode == Mode.OFFLINE:
		return
	if mode == Mode.HOST and _disc_send:
		_disc_t -= delta
		if _disc_t <= 0.0:
			_disc_t = 1.0
			_disc_send.set_dest_address("255.255.255.255", DISCOVERY_PORT)
			_disc_send.put_packet(("HH%d|%d|%d" % [PROTOCOL, _secret, PORT]).to_ascii_buffer())
	if not _world_connected or Game.state == Game.State.LOADING:
		return
	_send_t -= delta
	if _send_t <= 0.0:
		_send_t = 1.0 / STATE_HZ
		_send_state()
	if is_host():
		_clock_t -= delta
		if _clock_t <= 0.0:
			_clock_t = 5.0
			_broadcast_clock()


func _send_state() -> void:
	var p := _local_player()
	if p == null:
		return
	var st := pack_state(p)
	if is_host():
		players[1]["st"] = st
		for id in players:
			if id != 1 and players[id]["world"]:
				_state_fw.rpc_id(id, 1, st)
	else:
		_state.rpc_id(1, st)


## [pos, yaw, anim, speed, weapon, vehicle type, vehicle pos, vehicle rot (quat), paint, health, max health, livery]
static func pack_state(p: Player) -> Array:
	var anim := 0
	match p.state:
		Player.State.AIR:
			anim = 1
		Player.State.VEHICLE:
			anim = 2
		Player.State.DEAD, Player.State.BUSTED:
			anim = 3
		Player.State.SWIM:
			anim = 4
	var vt := ""
	var vp := Vector3.ZERO
	var vq := Quaternion.IDENTITY
	var paint := Color.WHITE
	var liv := {}
	if p.is_in_vehicle() and p.vehicle is Vehicle:
		var v := p.vehicle as Vehicle
		vt = v.type_id
		vp = v.global_position
		vq = v.global_basis.get_rotation_quaternion()
		paint = v.paint
		liv = v.livery
	var sp := Vector2(p.velocity.x, p.velocity.z).length()
	return [p.global_position, p.rotation.y, anim, sp, p.weapons.current_id(), vt, vp, vq, paint,
		p.health.health, p.health.max_health, liv]


static func valid_state(st) -> bool:
	if not st is Array or st.size() != 12:
		return false
	var types := [TYPE_VECTOR3, TYPE_FLOAT, TYPE_INT, TYPE_FLOAT, TYPE_STRING, TYPE_STRING, TYPE_VECTOR3,
		TYPE_QUATERNION, TYPE_COLOR, TYPE_FLOAT, TYPE_FLOAT, TYPE_DICTIONARY]
	for i in types.size():
		if typeof(st[i]) != types[i]:
			return false
	for v in [st[0], st[6]]:
		var vv: Vector3 = v
		if not vv.is_finite() or absf(vv.x) > 20000.0 or absf(vv.y) > 5000.0 or absf(vv.z) > 20000.0:
			return false
	if not is_finite(st[1]) or not is_finite(st[3]) or not is_finite(st[9]) or not is_finite(st[10]):
		return false
	if not (st[7] as Quaternion).is_finite():
		return false
	if String(st[4]).length() > 24 or (String(st[4]) != "" and WeaponData.get_def(st[4]).is_empty()):
		return false
	if String(st[5]) != "" and not VehicleDefs.meta(st[5]) is Dictionary:
		return false
	if String(st[5]) != "" and (VehicleDefs.meta(st[5]) as Dictionary).is_empty():
		return false
	if (st[11] as Dictionary).size() > 8:
		return false
	return true


func _broadcast_clock() -> void:
	var w := GameWorld.instance
	if w == null or w.day_night == null:
		return
	var h := float(w.day_night.get("hour"))
	var weather := String(w.weather.get("state")) if w.weather else ""
	for id in players:
		if id != 1 and players[id]["world"]:
			_clock.rpc_id(id, h, weather)


# ------------------------------------------------------------------ rate limiting (host)
func _allow(id: int, kind: String, per_sec: int) -> bool:
	var k := "%d:%s" % [id, kind]
	var now := Time.get_ticks_msec()
	var r: Array = _rates.get(k, [now, 0])
	if now - int(r[0]) > 1000:
		r = [now, 0]
	r[1] = int(r[1]) + 1
	_rates[k] = r
	return int(r[1]) <= per_sec


func _sender_ok() -> int:
	## host side: id of a known player who sent this RPC, or 0
	var id := multiplayer.get_remote_sender_id()
	if mode != Mode.HOST or not players.has(id):
		return 0
	return id


func _from_host() -> bool:
	return mode == Mode.CLIENT and multiplayer.get_remote_sender_id() == 1


# ================================================================== RPCs: client -> host
@rpc("any_peer", "call_remote", "reliable")
func _hello_world(outfit: Dictionary) -> void:
	var id := _sender_ok()
	if id == 0 or not _allow(id, "hello", 2):
		return
	players[id]["world"] = true
	players[id]["outfit"] = _clean_outfit(outfit)
	# tell everybody about the new player, and the new player about everybody
	for other in players:
		if other == id or not players[other]["world"]:
			continue
		if other != 1:
			_in_world.rpc_id(other, id, players[id]["outfit"])
		_in_world.rpc_id(id, other, players[other]["outfit"])
	_spawn_proxy(id, players[id]["outfit"])
	_broadcast_roster()
	var p := _local_player()
	if p:
		_meet.rpc_id(id, p.global_position)
	_system_message("%s ist in Port Aurelia angekommen." % players[id]["name"])


@rpc("any_peer", "call_remote", "unreliable_ordered", 1)
func _state(st: Array) -> void:
	var id := _sender_ok()
	if id == 0 or not players[id]["world"] or not _allow(id, "st", 40) or not valid_state(st):
		return
	players[id]["st"] = st
	_apply_state(id, st)
	for other in players:
		if other != 1 and other != id and players[other]["world"]:
			_state_fw.rpc_id(other, id, st)


@rpc("any_peer", "call_remote", "unreliable_ordered", 2)
func _shot(from: Vector3, to: Vector3, weapon: String) -> void:
	var id := _sender_ok()
	if id == 0 or not _allow(id, "shot", 30) or not from.is_finite() or not to.is_finite() or from.distance_to(to) > 2000.0:
		return
	if WeaponData.get_def(weapon).is_empty():
		return
	_show_shot(id, from, to, weapon)
	for other in players:
		if other != 1 and other != id and players[other]["world"]:
			_shot_fw.rpc_id(other, id, from, to, weapon)


@rpc("any_peer", "call_remote", "reliable")
func _hit(target: int, dmg: float) -> void:
	var id := _sender_ok()
	if id == 0 or target == id or not players.has(target) or not _allow(id, "hit", 20):
		return
	if not is_finite(dmg) or dmg <= 0.0:
		return
	dmg = minf(dmg, _max_hit_for(id))
	# damage budget per attacker and second (no machine-gun cheats)
	var budget_key := "%d:dmg" % id
	var now := Time.get_ticks_msec()
	var bud: Array = _rates.get(budget_key, [now, 0.0])
	if now - int(bud[0]) > 1000:
		bud = [now, 0.0]
	if float(bud[1]) + dmg > 420.0:
		return
	bud[1] = float(bud[1]) + dmg
	_rates[budget_key] = bud
	# attacker must be near the victim (last known positions)
	var a: Array = players[id]["st"]
	var b: Array = players[target]["st"]
	if a.size() == 12 and b.size() == 12 and (a[0] as Vector3).distance_to(b[0]) > MAX_HIT_RANGE:
		return
	if target == 1:
		_receive_hit(id, dmg)
	else:
		_take_hit.rpc_id(target, id, dmg)


## Highest damage one hit of this attacker can do, from the weapon he holds (last state):
## bullets up to a headshot, launchers and cars up to MAX_HIT.
func _max_hit_for(id: int) -> float:
	var st: Array = players.get(id, {}).get("st", [])
	if st.size() != 12:
		return 60.0
	if String(st[5]) != "" or int(st[2]) == 2:
		return MAX_HIT                      # in a car: running someone over
	var d := WeaponData.get_def(String(st[4]))
	if d.is_empty():
		return 30.0
	if String(d.get("kind", "")) == "launcher" or d.has("projectile"):
		return MAX_HIT
	return minf(float(d.get("damage", 20.0)) * Combat.HEADSHOT_MULT * 1.1, MAX_HIT)


@rpc("any_peer", "call_remote", "reliable")
func _died(killer: int) -> void:
	var id := _sender_ok()
	if id == 0 or not _allow(id, "died", 2):
		return
	_announce_kill(id, killer if players.has(killer) else 0)


@rpc("any_peer", "call_remote", "reliable")
func _money(target: int, amount: int) -> void:
	var id := _sender_ok()
	if id == 0 or target == id or not players.has(target) or not _allow(id, "money", 2):
		return
	if amount <= 0 or amount > MAX_TRANSFER:
		return
	if target == 1:
		_receive_money(id, amount)
	else:
		_money_in.rpc_id(target, id, amount)


@rpc("any_peer", "call_remote", "reliable")
func _chat(text: String) -> void:
	var id := _sender_ok()
	if id == 0 or not _allow(id, "chat", 2):
		return
	text = clean_text(text)
	if text == "":
		return
	_show_chat(id, text)
	for other in players:
		if other != 1 and other != id:
			_chat_fw.rpc_id(other, id, text)


# ================================================================== RPCs: host -> client
@rpc("authority", "call_remote", "reliable")
func _roster(r: Dictionary, started: bool, code: String) -> void:
	if not _from_host():
		return
	var keep := {}
	for id in r:
		if not (typeof(id) == TYPE_INT and r[id] is Dictionary):
			continue
		var e: Dictionary = r[id]
		var old: Dictionary = players.get(id, {})
		keep[id] = {"name": clean_name(String(e.get("name", "?"))), "color": e.get("color", Color.WHITE) if e.get("color") is Color else Color.WHITE,
			"world": bool(e.get("world", false)), "outfit": old.get("outfit", {}), "st": old.get("st", [])}
	players = keep
	in_game = started
	lobby_code = code.substr(0, 16)
	if not _world_connected:
		_set_status("In der Lobby – %s" % ("das Spiel läuft, du wirst gleich hineingeladen ..." if started else "warte, bis der Host das Spiel startet."))
	roster_changed.emit()


@rpc("authority", "call_remote", "reliable")
func _start_session() -> void:
	if not _from_host() or _world_connected:
		return
	in_game = true
	_begin_game()


@rpc("authority", "call_remote", "reliable")
func _in_world(id: int, outfit: Dictionary) -> void:
	if not _from_host() or id == my_id():
		return
	if players.has(id):
		players[id]["outfit"] = _clean_outfit(outfit)
		players[id]["world"] = true
	_spawn_proxy(id, _clean_outfit(outfit))


@rpc("authority", "call_remote", "reliable")
func _player_left(id: int) -> void:
	if not _from_host():
		return
	_remove_proxy(id)


@rpc("authority", "call_remote", "reliable")
func _meet(pos: Vector3) -> void:
	if not _from_host() or not pos.is_finite():
		return
	var p := _local_player()
	if p and p.state == Player.State.GROUND:
		p.teleport(pos + Vector3(2.5, 0.3, 2.5))
		Events.notify.emit("Du bist beim Host gelandet.", 3.0)


@rpc("authority", "call_remote", "unreliable_ordered", 1)
func _state_fw(id: int, st: Array) -> void:
	if not _from_host() or not valid_state(st):
		return
	if players.has(id):
		players[id]["st"] = st
	_apply_state(id, st)


@rpc("authority", "call_remote", "unreliable_ordered", 2)
func _shot_fw(id: int, from: Vector3, to: Vector3, weapon: String) -> void:
	if not _from_host() or not from.is_finite() or not to.is_finite() or WeaponData.get_def(weapon).is_empty():
		return
	_show_shot(id, from, to, weapon)


@rpc("authority", "call_remote", "reliable")
func _take_hit(attacker: int, dmg: float) -> void:
	if not _from_host() or not is_finite(dmg):
		return
	_receive_hit(attacker, minf(dmg, MAX_HIT))


@rpc("authority", "call_remote", "reliable")
func _money_in(from: int, amount: int) -> void:
	if not _from_host() or amount <= 0 or amount > MAX_TRANSFER:
		return
	_receive_money(from, amount)


@rpc("authority", "call_remote", "reliable")
func _chat_fw(id: int, text: String) -> void:
	if not _from_host():
		return
	_show_chat(id, clean_text(text))


@rpc("authority", "call_remote", "reliable")
func _feed(text: String) -> void:
	if not _from_host():
		return
	Events.notify.emit(clean_text(text), 4.0)
	_chat_log.append(["", clean_text(text), Color(1, 0.85, 0.4)])
	chat_received.emit("", clean_text(text))


@rpc("authority", "call_remote", "reliable")
func _clock(hour: float, weather: String) -> void:
	if not _from_host() or not is_finite(hour):
		return
	var w := GameWorld.instance
	if w == null:
		return
	if w.day_night:
		var cur := float(w.day_night.get("hour"))
		if absf(angle_difference(cur / 24.0 * TAU, hour / 24.0 * TAU)) > 0.05:
			w.day_night.call("set_hour", fposmod(hour, 24.0))
	if w.weather and weather.length() < 24 and weather != "" and String(w.weather.get("state")) != weather:
		w.weather.call("set_weather", weather)


@rpc("authority", "call_remote", "reliable")
func _kicked(reason: String) -> void:
	if not _from_host():
		return
	_fail.call_deferred(clean_text(reason))


# ================================================================== local actions
## Called by the weapon holder when the local player fires (tracer for the others).
func local_shot(from: Vector3, to: Vector3, weapon: String) -> void:
	if not _world_connected:
		return
	if is_host():
		for id in players:
			if id != 1 and players[id]["world"]:
				_shot_fw.rpc_id(id, 1, from, to, weapon)
	else:
		_shot.rpc_id(1, from, to, weapon)


## The local player hit another player (bullet, fist, car, explosion).
func send_hit(target: int, dmg: float) -> void:
	if not _world_connected or not players.has(target) or target == my_id():
		return
	if is_host():
		_take_hit.rpc_id(target, 1, minf(dmg, MAX_HIT))
	else:
		_hit.rpc_id(1, target, dmg)


func send_money(target: int, amount: int) -> bool:
	if not _world_connected or not players.has(target) or target == my_id():
		return false
	amount = clampi(amount, 0, MAX_TRANSFER)
	if amount <= 0 or Game.player_data.money < amount:
		Events.notify.emit("Nicht genug Geld.", 2.5)
		return false
	Game.player_data.charge(amount, "transfer")
	if is_host():
		_money_in.rpc_id(target, 1, amount)
	else:
		_money.rpc_id(1, target, amount)
	Events.notify.emit("$%d an %s geschickt." % [amount, player_name(target)], 3.0)
	AudioManager.play_ui("buy", -4.0)
	return true


func send_chat(text: String) -> void:
	text = clean_text(text)
	if text == "" or not is_online():
		return
	if is_host():
		_show_chat(1, text)
		for id in players:
			if id != 1:
				_chat_fw.rpc_id(id, 1, text)
	else:
		_show_chat(my_id(), text)
		_chat.rpc_id(1, text)


func player_name(id: int) -> String:
	return String(players.get(id, {}).get("name", "?"))


func player_color(id: int) -> Color:
	return players.get(id, {}).get("color", Color.WHITE)


func chat_log() -> Array:
	return _chat_log


# ------------------------------------------------------------------ receiving
func _receive_hit(attacker: int, dmg: float) -> void:
	var p := _local_player()
	if p == null or p.health.dead:
		return
	var src: Node3D = _proxies.get(attacker)
	var pos := p.global_position + Vector3.UP * 1.2
	var dir := Vector3.ZERO
	if src and is_instance_valid(src):
		dir = (p.global_position - src.global_position).normalized()
	_last_attacker = attacker
	_last_attack_ms = Time.get_ticks_msec()
	p.health.take_damage(dmg, src if src and is_instance_valid(src) else null, pos, dir)
	Combat.impact_fx(pos, -dir if dir != Vector3.ZERO else Vector3.UP, "flesh")


func _receive_money(from: int, amount: int) -> void:
	Game.player_data.add_money(amount, "transfer")
	Events.notify.emit("%s hat dir $%d geschickt!" % [player_name(from), amount], 4.0)
	AudioManager.play_ui("mission_passed", -8.0)


func _on_local_damaged(_amount: float, source: Node, _p: Vector3, _d: Vector3) -> void:
	if source is RemotePlayer:
		_last_attacker = (source as RemotePlayer).peer_id
		_last_attack_ms = Time.get_ticks_msec()


func _on_local_death() -> void:
	if not _world_connected:
		return
	var killer := _last_attacker if Time.get_ticks_msec() - _last_attack_ms < 4000 else 0
	if is_host():
		_announce_kill(1, killer)
	else:
		_died.rpc_id(1, killer)


func _announce_kill(victim: int, killer: int) -> void:
	var text := "%s wurde von %s ausgeschaltet." % [player_name(victim), player_name(killer)] if killer != 0 \
		else "%s ist gestorben." % player_name(victim)
	Events.notify.emit(text, 4.0)
	_chat_log.append(["", text, Color(1, 0.85, 0.4)])
	chat_received.emit("", text)
	for id in players:
		if id != 1:
			_feed.rpc_id(id, text)


func _system_message(text: String) -> void:
	_chat_log.append(["", text, Color(1, 0.85, 0.4)])
	chat_received.emit("", text)
	if _world_connected:
		Events.notify.emit(text, 3.0)
	for id in players:
		if id != 1 and mode == Mode.HOST:
			_feed.rpc_id(id, text)


func _show_chat(id: int, text: String) -> void:
	_chat_log.append([player_name(id), text, player_color(id)])
	if _chat_log.size() > 40:
		_chat_log.pop_front()
	chat_received.emit(player_name(id), text)


func _show_shot(id: int, from: Vector3, to: Vector3, weapon: String) -> void:
	var pr: RemotePlayer = _proxies.get(id)
	if pr == null or not is_instance_valid(pr) or not _world_connected:
		return
	var d := WeaponData.get_def(weapon)
	VFX.tracer(from, to)
	VFX.muzzle_flash(from, (to - from).normalized(), 1.0)
	AudioManager.play_weapon(String(d.get("sound", "pistol")), from, false)
	pr.on_fired()


# ------------------------------------------------------------------ proxies
func _spawn_proxy(id: int, outfit: Dictionary) -> void:
	var w := GameWorld.instance
	if w == null or not _world_connected or id == my_id():
		return
	if _proxies.has(id) and is_instance_valid(_proxies[id]):
		return
	var pr := RemotePlayer.new()
	pr.peer_id = id
	pr.display_name = player_name(id)
	pr.color = player_color(id)
	pr.outfit = outfit
	w.add_child(pr)
	var st: Array = players.get(id, {}).get("st", [])
	if st.size() == 12:
		pr.global_position = st[0]
		pr.apply_state(st, true)
	else:
		pr.global_position = Vector3(0, -400, 0)
	_proxies[id] = pr


func _remove_proxy(id: int) -> void:
	var pr = _proxies.get(id)
	if pr and is_instance_valid(pr):
		(pr as RemotePlayer).cleanup()
		pr.queue_free()
	_proxies.erase(id)


func _apply_state(id: int, st: Array) -> void:
	if not _world_connected:
		return
	if not _proxies.has(id) or not is_instance_valid(_proxies[id]):
		if players.has(id) and players[id]["world"]:
			_spawn_proxy(id, players[id].get("outfit", {}))
		else:
			return
	(_proxies[id] as RemotePlayer).apply_state(st)


func proxies() -> Dictionary:
	return _proxies


## Blips for the minimap / map: every other player in his colour.
func map_blips() -> Array:
	var out := []
	for id in _proxies:
		var pr = _proxies[id]
		if pr and is_instance_valid(pr) and (pr as Node3D).global_position.y > -100.0:
			out.append({"pos": (pr as Node3D).global_position, "icon": "", "color": player_color(id), "size": 8.0,
				"edge": true, "name": player_name(id)})
	return out


# ------------------------------------------------------------------ HUD
func _ensure_hud() -> void:
	var w := GameWorld.instance
	if w == null or w.get_node_or_null("NetHud"):
		return
	var h := NetHud.new()
	h.name = "NetHud"
	w.add_child(h)


# ================================================================== helpers
static func clean_name(n: String) -> String:
	var out := ""
	for c in n.strip_edges():
		var u := c.unicode_at(0)
		if (u >= 48 and u <= 57) or (u >= 65 and u <= 90) or (u >= 97 and u <= 122) or c in " _-.äöüÄÖÜß":
			out += c
	out = out.strip_edges().substr(0, 16)
	return out if out != "" else "Spieler"


static func clean_text(t: String) -> String:
	var out := ""
	for c in t:
		var u := c.unicode_at(0)
		if u >= 32 and c != "[" and c != "]":
			out += c
	return out.strip_edges().substr(0, 120)


static func _clean_outfit(o: Dictionary) -> Dictionary:
	var out := {}
	if o.size() > 24:
		return out
	for k in o:
		if typeof(k) != TYPE_STRING or String(k).length() > 24:
			continue
		var v = o[k]
		if typeof(v) in [TYPE_STRING, TYPE_INT, TYPE_FLOAT, TYPE_BOOL, TYPE_COLOR]:
			if typeof(v) == TYPE_STRING and String(v).length() > 40:
				continue
			out[k] = v
	return out


func _unique_name(n: String) -> String:
	var names := []
	for p in players.values():
		names.append(String(p["name"]).to_lower())
	for p in _pending_join.values():
		names.append(String(p).to_lower())
	if not n.to_lower() in names:
		return n
	for i in range(2, 20):
		var c := "%s %d" % [n.substr(0, 13), i]
		if not c.to_lower() in names:
			return c
	return n + "?"


static func _lan_ip() -> String:
	var best := ""
	for a in IP.get_local_addresses():
		if a.count(".") != 3 or a.begins_with("127.") or a.begins_with("169.254."):
			continue
		if a.begins_with("192.168.") or a.begins_with("10."):
			return a
		if a.begins_with("172."):
			var second := int(a.split(".")[1])
			if second >= 16 and second <= 31:
				return a
		if best == "":
			best = a
	return best if best != "" else "127.0.0.1"


## 10-character lobby code from an IPv4 address and a 16-bit secret.
static func encode_code(ip: String, secret: int) -> String:
	var parts := ip.split(".")
	if parts.size() != 4:
		return ""
	var v := 0
	for p in parts:
		v = (v << 8) | (int(p) & 255)
	v = (v << 16) | (secret & 0xFFFF)
	var s := ""
	for i in 10:
		s = ALPHABET[v & 31] + s
		v >>= 5
	return s.substr(0, 5) + "-" + s.substr(5)


static func decode_code(code: String) -> Dictionary:
	var c := code.to_upper().replace("-", "").replace(" ", "").strip_edges()
	if c.length() != 10:
		return {}
	var v := 0
	for ch in c:
		var i := ALPHABET.find(ch)
		if i < 0:
			return {}
		v = (v << 5) | i
	var secret := v & 0xFFFF
	var ipv := (v >> 16) & 0xFFFFFFFF
	var ip := "%d.%d.%d.%d" % [(ipv >> 24) & 255, (ipv >> 16) & 255, (ipv >> 8) & 255, ipv & 255]
	return {"ip": ip, "secret": secret}
