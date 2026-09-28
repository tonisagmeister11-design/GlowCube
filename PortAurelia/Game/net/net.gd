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
const PROTOCOL := 5
const STATE_HZ := 20.0
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
var public_ip := ""
var internet_state := ""       # "ok", "manual", "cgnat" (host, after the internet check)

var _peer: ENetMultiplayerPeer
var _secret := 0
var _host_ip := ""
var _rdv: Rendezvous
var _nat_mapped: Array = []
var _internet_t0 := 0
var lobby_ready := true         # host: the lobby code is final (after the internet check)
var link_kind := ""             # client: "LAN", "direkt" or "Relay"
var _attempts: Array = []       # client: connection attempts still to try
var _attempt_deadline := 0
var _attempt_kind := ""
var _status_t := 0.0
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
# host-simulated entities (co-op mission enemies and vehicles), replicated to every client
var _ents := {}                 # eid -> {node, kind, data}   (host)
var _ent_proxies := {}          # eid -> Node3D               (client)
var _next_eid := 1
var _ent_t := 0.0
# co-op mission state from the host (objective, markers, timer) - shown by every client
var coop_state := {}
var coop: Node = null           # host: running CoopMissions node
signal coop_changed


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	multiplayer.peer_connected.connect(_on_peer_connected)
	multiplayer.peer_disconnected.connect(_on_peer_disconnected)
	multiplayer.connected_to_server.connect(_on_connected)
	multiplayer.connection_failed.connect(_on_connection_failed)
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
	# reserve the game port first: the internet check learns the router's mapping of exactly
	# this port, then the game server takes it over
	var probe := PacketPeerUDP.new()
	if probe.bind(PORT, bind_ip()) != OK:
		_set_status("Port %d ist belegt – läuft das Spiel schon ein zweites Mal?" % PORT)
		return false
	mode = Mode.HOST
	in_game = false
	_secret = randi_range(1, 65535)
	players = {1: {"name": my_name, "color": COLORS[0], "world": false, "outfit": {}, "st": []}}
	_host_ip = _lan_ip()
	lobby_code = encode_code(_host_ip, _secret)
	internet_state = ""
	public_ip = ""
	_internet_t0 = Time.get_ticks_msec()
	_disc_send = PacketPeerUDP.new()
	_disc_send.set_broadcast_enabled(true)
	if internet_enabled():
		lobby_ready = false
		_set_status("Lobby wird erstellt ...")
		_host_internet(probe)
	else:
		probe.close()
		if not _host_server():
			return false
		lobby_ready = true
		_set_status("Lobby offen (nur lokales Netzwerk).")
	roster_changed.emit()
	return mode == Mode.HOST


## Internet features on? (off only in the automatic tests that run on one machine)
static func internet_enabled() -> bool:
	return OS.has_environment("HH_STUN") or not (OS.has_environment("HH_NO_UPNP") or "--no-upnp" in OS.get_cmdline_user_args())


static func bind_ip() -> String:
	return OS.get_environment("HH_BIND_IP") if OS.has_environment("HH_BIND_IP") else "*"


func _host_server() -> bool:
	_peer = ENetMultiplayerPeer.new()
	if bind_ip() != "*":
		_peer.set_bind_ip(bind_ip())
	var err := _peer.create_server(PORT, MAX_PLAYERS - 1)
	if err != OK:
		_peer = null
		_fail("Port %d ist belegt – läuft das Spiel schon ein zweites Mal?" % PORT)
		return false
	_setup_multiplayer()
	multiplayer.multiplayer_peer = _peer
	return true


func _host_internet(probe: PacketPeerUDP) -> void:
	var st := await stun_phase(probe, 1600)
	probe.close()
	if mode != Mode.HOST:
		return
	if not _host_server():
		return
	_nat_mapped = st["mapped"]
	if not _nat_mapped.is_empty() and not is_private_ip(String(_nat_mapped[0])):
		public_ip = String(_nat_mapped[0])
		lobby_code = encode_code(public_ip, _secret)
	_rdv = Rendezvous.new()
	add_child(_rdv)
	_rdv.enet = _peer.host
	if not (st["server"] as Array).is_empty():
		_rdv.set_stun_keepalive(st["server"])
	_rdv.host_begin(lobby_code, host_cands())
	lobby_ready = true
	_set_status("Lobby offen. Internet wird eingerichtet ...")
	if not OS.has_environment("HH_NO_UPNP"):
		_upnp_thread = Thread.new()
		_upnp_thread.start(_upnp_setup)
	roster_changed.emit()


## Addresses the joining players can try, best first.
func host_cands() -> Array:
	var out := []
	if not _nat_mapped.is_empty():
		out.append([String(_nat_mapped[0]), int(_nat_mapped[1])])
	if upnp_ok and public_ip != "" and not out.has([public_ip, PORT]):
		out.append([public_ip, PORT])
	for c in Rendezvous.local_cands(PORT):
		if not out.has(c):
			out.append(c)
	return out.slice(0, 10)


## STUN on a socket (without blocking the game): the public address and port the router gives
## this socket, and whether the router hands out a different port per destination ("symmetric").
func stun_phase(udp: PacketPeerUDP, timeout_ms: int) -> Dictionary:
	var res := {"mapped": [], "server": [], "symmetric": false}
	var rs := Rendezvous.Resolver.new()
	rs.start(Rendezvous.stun_servers())
	var t0 := Time.get_ticks_msec()
	var sent := {}      # "ip:port" -> [last send ms, tries, request]
	var got := {}       # "ip:port" -> [ip, port]
	while Time.get_ticks_msec() - t0 < timeout_ms:
		await get_tree().process_frame
		rs.poll()
		for sv in rs.servers():
			var k := "%s:%d" % sv
			if got.has(k):
				continue
			var e: Array = sent.get(k, [0, 0, PackedByteArray()])
			if int(e[1]) < 4 and Time.get_ticks_msec() - int(e[0]) > 350:
				var req := Rendezvous.stun_request()
				udp.set_dest_address(String(sv[0]), int(sv[1]))
				udp.put_packet(req)
				sent[k] = [Time.get_ticks_msec(), int(e[1]) + 1, req]
		while udp.get_available_packet_count() > 0:
			var pkt := udp.get_packet()
			var k := "%s:%d" % [udp.get_packet_ip(), udp.get_packet_port()]
			if sent.has(k) and pkt.size() >= 20 and pkt.slice(8, 20) == (sent[k][2] as PackedByteArray).slice(8, 20):
				var m := Rendezvous.parse_stun(pkt)
				if not m.is_empty():
					got[k] = m
					if (res["server"] as Array).is_empty():
						res["server"] = [udp.get_packet_ip(), udp.get_packet_port()]
						res["mapped"] = m
		if got.size() >= 2 or (got.size() == 1 and Time.get_ticks_msec() - t0 > 900):
			break
	var ports := {}
	for k in got:
		ports[int(got[k][1])] = true
	res["symmetric"] = ports.size() > 1
	if OS.has_environment("HH_NET_DEBUG"):
		print("NETDBG stun ", res, " all ", got)
	return res


func _setup_multiplayer() -> void:
	var sm := multiplayer as SceneMultiplayer
	sm.server_relay = false            # clients only ever talk to the host
	sm.allow_object_decoding = false   # never decode objects from the network
	sm.auth_callback = _auth_received
	sm.auth_timeout = 5.0


## Router port opening via UPnP (worker thread). Optional: the meeting point + hole punching
## works without it, but an opened port makes the direct connection even more likely.
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
	if ok:
		_upnp = u
	upnp_ok = ok and not is_private_ip(ext)
	if upnp_ok and public_ip == "":
		public_ip = ext
		lobby_code = encode_code(public_ip, _secret)
	if _rdv:
		_rdv.host_update(lobby_code, host_cands())
	_update_internet_status()
	roster_changed.emit()


## Host: lobby status line about the internet readiness.
func _update_internet_status() -> void:
	if mode != Mode.HOST or not lobby_ready or not internet_enabled():
		return
	var links := _rdv.ready_links() if _rdv else 0
	var waited := Time.get_ticks_msec() - _internet_t0
	var st := ""
	var text := ""
	if links > 0:
		st = "ok"
		text = "✔ Internet bereit: Dein Freund kann mit dem Code von überall beitreten – auch aus einem anderen WLAN. Keine Router-Einstellungen nötig."
	elif upnp_ok:
		st = "ok"
		text = "✔ Internet bereit: Der Router wurde automatisch geöffnet. Der Code funktioniert auch aus einem anderen WLAN."
	elif waited < 12000:
		st = ""
		text = "Lobby offen. Internet wird eingerichtet ..."
	else:
		st = "offline"
		text = "⚠ Der Online-Vermittlungsdienst ist nicht erreichbar (Internet oder Firewall?). Im gleichen WLAN klappt der Code. Prüfe die Internetverbindung – das Spiel versucht es automatisch weiter."
	if st != internet_state or text != status:
		internet_state = st
		_set_status(text)


static func is_private_ip(ip: String) -> bool:
	if OS.has_environment("HH_TEST_PUBLIC") and ip.begins_with(OS.get_environment("HH_TEST_PUBLIC")):
		return false   # tests: the simulated internet uses loopback addresses
	var p := ip.split(".")
	if p.size() != 4:
		return true
	var a := int(p[0])
	var b := int(p[1])
	return a == 10 or a == 127 or (a == 172 and b >= 16 and b <= 31) or (a == 192 and b == 168) \
		or (a == 100 and b >= 64 and b <= 127) or (a == 169 and b == 254) or a == 0


## Extra codes for the other network adapters (VPNs like Radmin VPN, ZeroTier, Hamachi, Tailscale
## and the home network): [label, code].
func extra_codes() -> Array:
	var out := []
	if mode != Mode.HOST:
		return out
	for a in IP.get_local_addresses():
		if a.count(".") != 3 or a.begins_with("127.") or a.begins_with("169.254."):
			continue
		var c := encode_code(a, _secret)
		if c == lobby_code:
			continue
		var label := "Heimnetz"
		if a.begins_with("26."):
			label = "Radmin VPN"
		elif a.begins_with("25."):
			label = "Hamachi"
		elif a.begins_with("100.") and int(a.split(".")[1]) >= 64 and int(a.split(".")[1]) <= 127:
			label = "Tailscale"
		elif not is_private_ip(a):
			label = "VPN / Netzwerk"
		elif a.begins_with("10.") or a.begins_with("172."):
			label = "VPN / Heimnetz"
		out.append([label, c, a])
	return out


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
	link_kind = ""
	_attempts.clear()
	if OS.has_environment("HH_JOIN_ADDR"):   # tests: route through a latency/loss simulator
		var hp := OS.get_environment("HH_JOIN_ADDR").split(":")
		_attempts = [{"kind": "direkt", "ip": hp[0], "port": int(hp[1]) if hp.size() > 1 else PORT, "local": 0, "t": 12000}]
		_set_status("Verbinde mit Lobby ...")
		_next_attempt()
		return
	_set_status("Suche Lobby ...")
	_join_internet(code)


## Finds the host: same network (broadcast), otherwise over the internet via the meeting point
## with hole punching; relay as the last resort.
func _join_internet(code: String) -> void:
	var lan: PacketPeerUDP = null
	if not OS.has_environment("HH_NO_LAN"):
		lan = PacketPeerUDP.new()
		if lan.bind(DISCOVERY_PORT) != OK:
			lan = null
	var udp := PacketPeerUDP.new()
	if udp.bind(0, bind_ip()) != OK:
		_fail("Netzwerkfehler: kein freier Port.")
		return
	var lport := udp.get_local_port()
	var internet := internet_enabled()
	if internet:
		_rdv = Rendezvous.new()
		add_child(_rdv)
		_rdv.client_begin(code)
	var rs := Rendezvous.Resolver.new()
	if internet:
		rs.start(Rendezvous.stun_servers())
	var stun_sent := {}
	var mapped: Array = []
	var published := not internet
	var direct: Array = []
	var lan_ip := ""
	var probe_t := 0
	var t0 := Time.get_ticks_msec()
	var code_cand := [_join_target, PORT]
	while true:
		await get_tree().process_frame
		if mode != Mode.CLIENT:
			udp.close()
			if lan:
				lan.close()
			return
		var now := Time.get_ticks_msec()
		var el := now - t0
		# 1. same network?
		if lan:
			while lan.get_available_packet_count() > 0:
				var parts := lan.get_packet().get_string_from_ascii().split("|")
				if parts.size() >= 2 and parts[0] == "HH%d" % PROTOCOL and parts[1].is_valid_int() and int(parts[1]) == _join_secret:
					lan_ip = lan.get_packet_ip()
		if lan_ip != "":
			break
		# 2. our public address (STUN) -> tell the host where to punch
		if internet:
			rs.poll()
			for sv in rs.servers():
				var k := "%s:%d" % sv
				var e: Array = stun_sent.get(k, [0, 0, PackedByteArray()])
				if mapped.is_empty() and int(e[1]) < 4 and now - int(e[0]) > 350:
					var req := Rendezvous.stun_request()
					udp.set_dest_address(String(sv[0]), int(sv[1]))
					udp.put_packet(req)
					stun_sent[k] = [now, int(e[1]) + 1, req]
		if not published and (not mapped.is_empty() or el > 1600):
			published = true
			var cands := []
			if not mapped.is_empty():
				cands.append(mapped)
			for c in Rendezvous.local_cands(lport):
				if not cands.has(c):
					cands.append(c)
			_rdv.client_publish_join(cands)
		# 3. probe every known host address; the host's punch packet proves a direct path
		if now - probe_t > 150:
			probe_t = now
			var tries := [code_cand]
			if _rdv:
				for c in _rdv.found_cands:
					if not tries.has(c):
						tries.append(c)
			var pkt := ("HHQ" + (_rdv.nonce if _rdv else "")).to_ascii_buffer()
			for c in tries:
				udp.set_dest_address(String(c[0]), int(c[1]))
				udp.put_packet(pkt)
		while udp.get_available_packet_count() > 0:
			var pkt := udp.get_packet()
			var src := [udp.get_packet_ip(), udp.get_packet_port()]
			if _rdv and pkt.size() == 19 and pkt.slice(0, 3).get_string_from_ascii() == "HHP" \
					and pkt.slice(3).get_string_from_ascii() == _rdv.nonce:
				direct = src
			elif mapped.is_empty() and stun_sent.has("%s:%d" % src):
				var req: PackedByteArray = stun_sent["%s:%d" % src][2]
				if pkt.size() >= 20 and pkt.slice(8, 20) == req.slice(8, 20):
					mapped = Rendezvous.parse_stun(pkt)
		if not direct.is_empty():
			break
		if _rdv:
			if _rdv.host_protocol > 0 and _rdv.host_protocol != PROTOCOL:
				udp.close()
				if lan:
					lan.close()
				_fail("Der Host hat eine andere Spielversion – ihr braucht beide dieselbe Version von Harbor Heat.")
				return
			if _rdv.host_alive:
				_set_status("Host gefunden – baue direkte Verbindung auf ...")
			elif _rdv.ready_links() > 0:
				_set_status("Suche Lobby über das Internet ...")
		# give up on hole punching: host answered but no punch arrived, or nobody answered at all
		if _rdv and _rdv.host_alive and el > (4500 if _rdv.host_alive_ms > 0 and now - _rdv.host_alive_ms > 3500 else 9000):
			break
		if el > (9000 if (_rdv and _rdv.contact_slot >= 0) else 14000):
			break
	udp.close()
	if lan:
		lan.close()
	if OS.has_environment("HH_NET_DEBUG"):
		print("NETDBG join: lan=%s direct=%s mapped=%s found=%s alive=%s slot=%d" % [lan_ip, str(direct), str(mapped),
			str(_rdv.found_cands if _rdv else []), _rdv.host_alive if _rdv else false, _rdv.contact_slot if _rdv else -1])
	_attempts.clear()
	if lan_ip != "":
		_attempts.append({"kind": "LAN", "ip": lan_ip, "port": PORT, "local": 0, "t": 8000})
	elif not direct.is_empty():
		_attempts.append({"kind": "direkt", "ip": String(direct[0]), "port": int(direct[1]), "local": lport, "t": 8000})
	else:
		# no punch came back: try the host's public addresses directly (works when only our side
		# is strict), then the relay
		var tries := []
		if _rdv:
			for c in _rdv.found_cands:
				if not is_private_ip(String(c[0])) and tries.size() < 1:
					tries.append(c)
		if not tries.has(code_cand) and not is_private_ip(String(code_cand[0])):
			tries.append(code_cand)
		for c in tries.slice(0, 1 if (_rdv and _rdv.contact_slot >= 0) else 2):
			_attempts.append({"kind": "direkt", "ip": String(c[0]), "port": int(c[1]), "local": lport, "t": 3000})
		if _rdv and _rdv.contact_slot >= 0:
			_attempts.append({"kind": "Relay", "t": 15000})
		if _attempts.is_empty():
			_attempts.append({"kind": "direkt", "ip": String(code_cand[0]), "port": PORT, "local": lport, "t": 5000})
	_next_attempt()


func _next_attempt() -> void:
	if mode != Mode.CLIENT:
		return
	multiplayer.multiplayer_peer = OfflineMultiplayerPeer.new()
	if _peer:
		_peer.close()
		_peer = null
	if _rdv and _rdv.relay_active():
		_rdv.stop_relay()
	if _attempts.is_empty():
		_attempt_deadline = 0
		_fail("Keine Verbindung zum Host. Ist der Code richtig und die Lobby noch offen? Beide brauchen Internet und dieselbe Spielversion. Tipp: Beim Host muss in der Lobby „Internet bereit“ stehen.")
		return
	var a: Dictionary = _attempts.pop_front()
	_attempt_kind = String(a["kind"])
	_peer = ENetMultiplayerPeer.new()
	var err := OK
	if _attempt_kind == "Relay":
		var port := _rdv.start_relay()
		if port == 0:
			_next_attempt.call_deferred()
			return
		_set_status("Direkte Verbindung blockiert – verbinde über den Relay-Server ...")
		err = _peer.create_client("127.0.0.1", port)
	else:
		if bind_ip() != "*":
			_peer.set_bind_ip(bind_ip())
		_set_status("Verbinde mit Lobby%s ..." % (" im WLAN" if _attempt_kind == "LAN" else ""))
		err = _peer.create_client(String(a["ip"]), int(a["port"]), 0, 0, 0, int(a["local"]))
	if err != OK:
		_next_attempt.call_deferred()
		return
	if OS.has_environment("HH_NET_DEBUG"):
		print("NETDBG attempt ", a)
	_setup_multiplayer()
	multiplayer.multiplayer_peer = _peer
	_attempt_deadline = Time.get_ticks_msec() + int(a["t"])


func _on_connection_failed() -> void:
	if mode == Mode.CLIENT and _attempt_deadline > 0 and not _attempts.is_empty():
		_next_attempt.call_deferred()
		return
	_attempt_deadline = 0
	_attempts.clear()
	_fail.call_deferred("Keine Verbindung zum Host. Ist der Code richtig und die Lobby noch offen? Beide brauchen Internet und dieselbe Spielversion. Tipp: Beim Host muss in der Lobby „Internet bereit“ stehen.")


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
	_attempt_deadline = 0
	_attempts.clear()
	link_kind = _attempt_kind
	if _rdv:
		_rdv.stop_join_repeats()
		if link_kind != "Relay":
			_rdv.client_end()   # the meeting point is not needed any more
	_set_status("Verbunden (%s) – warte auf den Host ..." % link_kind)


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
	for eid in _ent_proxies.keys():
		var n = _ent_proxies[eid]
		if n and is_instance_valid(n):
			n.queue_free()
	_ent_proxies.clear()
	_ents.clear()
	if coop and is_instance_valid(coop):
		coop.queue_free()
	coop = null
	coop_state = {}
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
	if _rdv:
		if mode == Mode.HOST:
			_rdv.host_end()
		else:
			_rdv.client_end()
		_rdv.queue_free()
		_rdv = null
	_attempts.clear()
	_attempt_deadline = 0
	_nat_mapped = []
	lobby_ready = true
	link_kind = ""
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
	if mode == Mode.CLIENT and _attempt_deadline > 0 and Time.get_ticks_msec() > _attempt_deadline:
		_attempt_deadline = 0
		if OS.has_environment("HH_NET_DEBUG"):
			print("NETDBG attempt timed out: ", _attempt_kind)
		if _attempts.is_empty():
			_on_connection_failed()
		else:
			_next_attempt()
	_status_t -= delta
	if mode == Mode.HOST and _status_t <= 0.0:
		_status_t = 1.0
		_update_internet_status()
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
		_ent_t -= delta
		if _ent_t <= 0.0 and not _ents.is_empty():
			_ent_t = 1.0 / 15.0
			_send_entity_states()


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


## State of the local player, 20 times a second:
##  0 pos  1 yaw  2 state (0 ground 1 air 2 vehicle 3 dead 4 swim)  3 animation snapshot (CharacterModel)
##  4 weapon  5 vehicle type  6 vehicle pos  7 vehicle rotation  8 paint  9 health  10 max health
##  11 livery  12 steering  13 vehicle km/h  14 vehicle flags (1 lights 2 brake 4 siren 8 throttle 16 reverse)
##  15 sender clock (ms)  16 velocity  17 armor
const STATE_SIZE := 18


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
	var steer := 0.0
	var kmh := 0.0
	var flags := 0
	var vel := p.velocity
	if p.is_in_vehicle() and p.vehicle is Vehicle:
		var v := p.vehicle as Vehicle
		vt = v.type_id
		vp = v.global_position
		vq = v.global_basis.get_rotation_quaternion()
		paint = v.paint
		liv = v.livery
		steer = v.net_steer()
		kmh = v.speed_kmh
		vel = v.linear_velocity
		flags = (1 if v.headlights_on else 0) | (2 if v.brake_input > 0.1 else 0) | (4 if v.siren_on else 0) \
			| (8 if v.throttle > 0.1 else 0) | (16 if v.throttle < -0.1 else 0)
	return [p.global_position, p.rotation.y, anim, p.model.net_snapshot(), p.weapons.current_id(), vt, vp, vq, paint,
		p.health.health, p.health.max_health, liv, steer, kmh, flags, Time.get_ticks_msec(), vel, p.health.armor]


static func _finite_vec(v: Vector3, lim: float) -> bool:
	return v.is_finite() and absf(v.x) < lim and absf(v.y) < lim and absf(v.z) < lim


static func valid_state(st) -> bool:
	if not st is Array or st.size() != STATE_SIZE:
		return false
	var types := [TYPE_VECTOR3, TYPE_FLOAT, TYPE_INT, TYPE_ARRAY, TYPE_STRING, TYPE_STRING, TYPE_VECTOR3,
		TYPE_QUATERNION, TYPE_COLOR, TYPE_FLOAT, TYPE_FLOAT, TYPE_DICTIONARY, TYPE_FLOAT, TYPE_FLOAT, TYPE_INT,
		TYPE_INT, TYPE_VECTOR3, TYPE_FLOAT]
	for i in types.size():
		if typeof(st[i]) != types[i]:
			return false
	if not _finite_vec(st[0], 20000.0) or not _finite_vec(st[6], 20000.0) or not _finite_vec(st[16], 400.0):
		return false
	for k in [1, 9, 10, 12, 13, 17]:
		if not is_finite(st[k]) or absf(st[k]) > 100000.0:
			return false
	if not (st[7] as Quaternion).is_finite():
		return false
	var a: Array = st[3]
	if a.size() != 8:
		return false
	var at := [TYPE_STRING, TYPE_STRING, TYPE_FLOAT, TYPE_STRING, TYPE_FLOAT, TYPE_STRING, TYPE_INT, TYPE_FLOAT]
	for i in at.size():
		if typeof(a[i]) != at[i] or (at[i] == TYPE_STRING and String(a[i]).length() > 32):
			return false
		if at[i] == TYPE_FLOAT and not is_finite(a[i]):
			return false
	if String(st[4]).length() > 24 or (String(st[4]) != "" and WeaponData.get_def(st[4]).is_empty()):
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
	for eid in _ents:
		var e: Dictionary = _ents[eid]
		_ent_spawn.rpc_id(id, eid, e["kind"], e["data"])
	if not coop_state.is_empty():
		_coop.rpc_id(id, coop_state)
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
	if a.size() == STATE_SIZE and b.size() == STATE_SIZE and (a[0] as Vector3).distance_to(b[0]) > MAX_HIT_RANGE:
		return
	if target == 1:
		_receive_hit(id, dmg)
	else:
		_take_hit.rpc_id(target, id, dmg)


## Highest damage one hit of this attacker can do, from the weapon he holds (last state):
## bullets up to a headshot, launchers and cars up to MAX_HIT.
func _max_hit_for(id: int) -> float:
	var st: Array = players.get(id, {}).get("st", [])
	if st.size() != STATE_SIZE:
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
func _car_bump(target: int, impulse: Vector3, at: Vector3) -> void:
	var id := _sender_ok()
	if OS.has_environment("HH_NET_DEBUG"):
		print("NETDBG car bump from %d to %d imp %.0f at %s" % [id, target, impulse.length(), at])
	if id == 0 or target == id or not players.has(target) or not _allow(id, "bump", 6):
		return
	if not impulse.is_finite() or not at.is_finite() or impulse.length() > 200000.0:
		return
	var a: Array = players[id]["st"]
	if a.size() == STATE_SIZE and (a[6] as Vector3).distance_to(at) > 12.0:
		return   # must be next to his own car
	if target == 1:
		_apply_bump(impulse, at)
	else:
		_car_bump_in.rpc_id(target, id, impulse, at)


@rpc("authority", "call_remote", "reliable")
func _car_bump_in(_from: int, impulse: Vector3, at: Vector3) -> void:
	if not _from_host() or not impulse.is_finite() or not at.is_finite():
		return
	_apply_bump(impulse, at)


func _apply_bump(impulse: Vector3, at: Vector3) -> void:
	var p := _local_player()
	if OS.has_environment("HH_NET_DEBUG"):
		print("NETDBG apply bump: in car %s dist %.1f" % [p.is_in_vehicle() if p else false,
			(p.vehicle as Node3D).global_position.distance_to(at) if p and p.is_in_vehicle() else -1.0])
	if p and p.is_in_vehicle() and p.vehicle is Vehicle:
		var v := p.vehicle as Vehicle
		if v.global_position.distance_to(at) < 8.0:
			v.net_bump(impulse, at)


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


## Our car crashed into another player's car copy: push his real car in his game.
func send_car_bump(target: int, impulse: Vector3, at: Vector3) -> void:
	if not _world_connected or not players.has(target) or target == my_id():
		return
	if is_host():
		_car_bump_in.rpc_id(target, 1, impulse, at)
	else:
		_car_bump.rpc_id(1, target, impulse, at)


## Host only: a host-simulated NPC (mission enemy) hit a remote player.
func host_npc_hit(target: int, dmg: float) -> void:
	if not is_host() or not players.has(target) or target == 1:
		return
	_take_hit.rpc_id(target, 0, minf(dmg, MAX_HIT))


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


## Connection type and ping of a player, e.g. "Internet · 34 ms" ("" for yourself / unknown).
func peer_link_text(id: int) -> String:
	if _peer == null or id == my_id():
		return ""
	var pp: ENetPacketPeer = null
	var kind := ""
	if is_host() and id != 1:
		pp = _peer.get_peer(id)
		if pp:
			var addr := pp.get_remote_address()
			if addr == Rendezvous.local_game_ip() or (not OS.has_environment("HH_BIND_IP") and (addr.begins_with("127.") or addr == "::1")):
				kind = "Relay"
			elif is_private_ip(addr) or addr.begins_with("fe80") or addr.begins_with("fd"):
				kind = "WLAN"
			else:
				kind = "Internet"
	elif not is_host() and id == 1:
		pp = _peer.get_peer(1)
		kind = {"LAN": "WLAN", "direkt": "Internet", "Relay": "Relay"}.get(link_kind, link_kind)
	if pp == null:
		return ""
	return "%s · %d ms" % [kind, int(pp.get_statistic(ENetPacketPeer.PEER_ROUND_TRIP_TIME))]


func player_color(id: int) -> Color:
	return players.get(id, {}).get("color", Color.WHITE)


func chat_log() -> Array:
	return _chat_log


# ------------------------------------------------------------------ receiving
func _receive_hit(attacker: int, dmg: float) -> void:
	var p := _local_player()
	if p == null or p.health.dead or p.health.invulnerable:
		return
	var src: Node3D = _proxies.get(attacker) if attacker != 0 else null
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


# ================================================================== replicated entities
## Host: make a node (mission NPC or vehicle) visible to every client. kind "npc" data {outfit},
## kind "vehicle" data {type, paint, livery}. Returns the entity id.
func host_add_entity(node: Node3D, kind: String, data: Dictionary) -> int:
	if not is_host():
		return 0
	var eid := _next_eid
	_next_eid += 1
	_ents[eid] = {"node": node, "kind": kind, "data": data}
	node.set_meta("net_eid", eid)
	for id in players:
		if id != 1 and players[id]["world"]:
			_ent_spawn.rpc_id(id, eid, kind, data)
	node.tree_exiting.connect(func(): host_remove_entity(eid))
	return eid


func host_remove_entity(eid: int) -> void:
	if not _ents.has(eid):
		return
	_ents.erase(eid)
	if is_host():
		for id in players:
			if id != 1 and players[id]["world"]:
				_ent_remove.rpc_id(id, eid)


func _send_entity_states() -> void:
	var npcs := []
	var cars := []
	var now := Time.get_ticks_msec()
	for eid in _ents:
		var e: Dictionary = _ents[eid]
		var n = e["node"]
		if n == null or not is_instance_valid(n):
			continue
		if e["kind"] == "npc":
			var npc := n as NPC
			npcs.append([eid, npc.global_position, npc.rotation.y, npc.model.net_snapshot(), npc.weapons.current_id(),
				npc.is_dead(), now])
		else:
			var v := n as Vehicle
			cars.append([eid, v.global_position, v.global_basis.get_rotation_quaternion(), v.net_steer(), v.speed_kmh,
				(1 if v.headlights_on else 0) | (4 if v.siren_on else 0), v.destroyed, now, v.body_health])
	# small packets (under the ~1400 byte MTU): 4 characters or 6 cars per message
	var chunks := []
	for i in range(0, npcs.size(), 4):
		chunks.append([npcs.slice(i, i + 4), []])
	for i in range(0, cars.size(), 6):
		chunks.append([[], cars.slice(i, i + 6)])
	for id in players:
		if id != 1 and players[id]["world"]:
			for c in chunks:
				_ent_states.rpc_id(id, c[0], c[1])


## Host: an entity (mission enemy) fired - tracers for the clients.
func host_entity_shot(eid: int, from: Vector3, to: Vector3, weapon: String) -> void:
	for id in players:
		if id != 1 and players[id]["world"]:
			_ent_shot.rpc_id(id, eid, from, to, weapon)


## Client: the local player hit a host entity.
func send_entity_hit(eid: int, dmg: float) -> void:
	if mode == Mode.CLIENT and _world_connected:
		_ent_hit.rpc_id(1, eid, dmg)


@rpc("authority", "call_remote", "reliable")
func _ent_spawn(eid: int, kind: String, data: Dictionary) -> void:
	if not _from_host() or not _world_connected or _ent_proxies.has(eid):
		return
	var w := GameWorld.instance
	if w == null:
		return
	if kind == "npc":
		var r := RemoteEntity.new()
		r.eid = eid
		r.outfit = _clean_outfit(data.get("outfit", {}) if data.get("outfit") is Dictionary else {})
		w.add_child(r)
		r.global_position = Vector3(0, -400, 0)
		_ent_proxies[eid] = r
	elif kind == "vehicle":
		var vt := String(data.get("type", "sedan"))
		if (VehicleDefs.meta(vt) as Dictionary).is_empty():
			return
		var col = data.get("paint", Color.WHITE)
		var v := Vehicle.create(vt, col if col is Color else Color.WHITE)
		v.set_meta("net_proxy", 0)
		v.set_meta("net_entity", eid)
		v.freeze = true
		w.add_child(v)
		v.global_position = Vector3(0, -400, 0)
		v.set_kinematic(true)
		_ent_proxies[eid] = v


@rpc("authority", "call_remote", "reliable")
func _ent_remove(eid: int) -> void:
	if not _from_host():
		return
	var n = _ent_proxies.get(eid)
	if n and is_instance_valid(n):
		n.queue_free()
	_ent_proxies.erase(eid)


@rpc("authority", "call_remote", "unreliable_ordered", 3)
func _ent_states(npcs: Array, cars: Array) -> void:
	if not _from_host() or npcs.size() > 64 or cars.size() > 32:
		return
	for s in npcs:
		if not (s is Array and s.size() == 7 and typeof(s[0]) == TYPE_INT and typeof(s[1]) == TYPE_VECTOR3
				and typeof(s[3]) == TYPE_ARRAY and typeof(s[6]) == TYPE_INT and (s[1] as Vector3).is_finite()):
			continue
		var r = _ent_proxies.get(s[0])
		if r is RemoteEntity and is_instance_valid(r):
			(r as RemoteEntity).push(s)
	for s in cars:
		if not (s is Array and s.size() == 9 and typeof(s[1]) == TYPE_VECTOR3 and typeof(s[2]) == TYPE_QUATERNION
				and (s[1] as Vector3).is_finite() and (s[2] as Quaternion).is_finite()):
			continue
		var v = _ent_proxies.get(s[0])
		if v is Vehicle and is_instance_valid(v):
			var veh := v as Vehicle
			var target := Transform3D(Basis(s[2] as Quaternion), s[1])
			if veh.global_position.distance_to(s[1]) > 20.0:
				veh.global_transform = target
			else:
				veh.global_transform = veh.global_transform.interpolate_with(target, 0.5)
			veh.net_visual(float(s[3]), float(s[4]), int(s[5]), 1.0 / 15.0)
			veh.body_health = float(s[8])
			if bool(s[6]) and not veh.destroyed:
				veh.call("explode")


@rpc("authority", "call_remote", "unreliable_ordered", 2)
func _ent_shot(eid: int, from: Vector3, to: Vector3, weapon: String) -> void:
	if not _from_host() or not from.is_finite() or not to.is_finite() or WeaponData.get_def(weapon).is_empty():
		return
	var d := WeaponData.get_def(weapon)
	VFX.tracer(from, to)
	VFX.muzzle_flash(from, (to - from).normalized(), 1.0)
	AudioManager.play_weapon(String(d.get("sound", "pistol")), from, false)


@rpc("any_peer", "call_remote", "reliable")
func _ent_hit(eid: int, dmg: float) -> void:
	var id := _sender_ok()
	if OS.has_environment("HH_NET_DEBUG"):
		print("NETDBG entity hit from %d on %d dmg %.0f (known %s)" % [id, eid, dmg, _ents.has(eid)])
	if id == 0 or not _ents.has(eid) or not _allow(id, "ehit", 20) or not is_finite(dmg) or dmg <= 0.0:
		return
	dmg = minf(dmg, _max_hit_for(id))
	var n = _ents[eid]["node"]
	if n == null or not is_instance_valid(n):
		return
	var a: Array = players[id]["st"]
	if a.size() == STATE_SIZE and (a[0] as Vector3).distance_to((n as Node3D).global_position) > MAX_HIT_RANGE:
		return
	var src: Node = _proxies.get(id)
	var pos := (n as Node3D).global_position + Vector3.UP
	var dir := Vector3.ZERO
	if src and is_instance_valid(src):
		dir = ((n as Node3D).global_position - (src as Node3D).global_position).normalized()
	if n is Vehicle:
		(n as Vehicle).on_hit(dmg, src, pos, dir)
	else:
		Combat.apply_damage(n, dmg, src, pos, dir)


# ================================================================== co-op missions (state from the host)
## Host: publish the co-op mission state to everybody (also applied locally).
func host_coop_state(st: Dictionary) -> void:
	coop_state = st
	for id in players:
		if id != 1 and players[id]["world"]:
			_coop.rpc_id(id, st)
	_apply_coop(st)


func host_coop_reward(amount: int, text: String) -> void:
	for id in players:
		if id != 1 and players[id]["world"]:
			_coop_reward.rpc_id(id, amount, text)
	_receive_coop_reward(amount, text)


func host_coop_wanted(level: int) -> void:
	for id in players:
		if id != 1 and players[id]["world"]:
			_coop_wanted.rpc_id(id, level)
	_set_local_wanted(level)


@rpc("authority", "call_remote", "reliable")
func _coop(st: Dictionary) -> void:
	if not _from_host() or var_to_bytes(st).size() > 8000:
		return
	coop_state = st
	_apply_coop(st)


@rpc("authority", "call_remote", "reliable")
func _coop_reward(amount: int, text: String) -> void:
	if not _from_host() or amount < 0 or amount > 250000:
		return
	_receive_coop_reward(amount, clean_text(text))


@rpc("authority", "call_remote", "reliable")
func _coop_wanted(level: int) -> void:
	if _from_host():
		_set_local_wanted(clampi(level, 0, 5))


func _set_local_wanted(level: int) -> void:
	var w := GameWorld.instance
	if w and w.police:
		if level <= 0:
			w.police.call("clear_wanted")
		else:
			w.police.call("set_wanted", maxi(level, int(w.police.get("wanted_level"))))


func _receive_coop_reward(amount: int, text: String) -> void:
	if amount > 0:
		Game.player_data.add_money(amount, "coop")
	Events.big_message.emit("KOOP-MISSION GESCHAFFT" if amount > 0 else "KOOP-MISSION GESCHEITERT", text, 5.0)
	AudioManager.play_ui("mission_passed" if amount > 0 else "wasted", -4.0)


var _coop_markers: Array = []
var _coop_last_gps := Vector3.INF


func _apply_coop(st: Dictionary) -> void:
	for m in _coop_markers:
		if is_instance_valid(m):
			m.queue_free()
	_coop_markers.clear()
	var w := GameWorld.instance
	if w == null:
		return
	if st.is_empty() or not bool(st.get("active", false)):
		Events.mission_objective.emit("")
		if _coop_last_gps != Vector3.INF:
			Events.waypoint_set.emit(Vector3.INF)
			_coop_last_gps = Vector3.INF
		coop_changed.emit()
		return
	var obj := clean_text(String(st.get("objective", "")))
	var mine: Dictionary = (st.get("per_player", {}) as Dictionary).get(my_id(), {}) if st.get("per_player") is Dictionary else {}
	if mine.has("objective"):
		obj = clean_text(String(mine["objective"]))
	Events.mission_objective.emit("[KOOP] " + obj)
	var markers: Array = st.get("markers", []) if st.get("markers") is Array else []
	if mine.has("markers") and mine["markers"] is Array:
		markers = markers + mine["markers"]
	for mk in markers.slice(0, 12):
		if not (mk is Array and mk.size() >= 2 and mk[0] is Vector3 and (mk[0] as Vector3).is_finite()):
			continue
		var m := InteractMarker.new()
		m.vehicle_marker = true
		m.interact_radius = clampf(float(mk[1]), 1.0, 30.0)
		m.color = mk[2] if mk.size() > 2 and mk[2] is Color else Color(0.3, 0.8, 1.0)
		m.prompt = ""
		w.add_child(m)
		m.global_position = mk[0]
		_coop_markers.append(m)
	var gps = mine.get("gps", st.get("gps", null))
	if gps is Vector3 and (gps as Vector3).is_finite():
		if _coop_last_gps == Vector3.INF or (gps as Vector3).distance_to(_coop_last_gps) > 8.0:
			Events.waypoint_set.emit(gps)
			_coop_last_gps = gps
	coop_changed.emit()


## Map / minimap blips for the co-op mission (markers and the enemies).
func coop_blips() -> Array:
	var out := []
	if coop_state.is_empty() or not bool(coop_state.get("active", false)):
		return out
	for m in _coop_markers:
		if is_instance_valid(m):
			out.append({"pos": (m as Node3D).global_position, "icon": "", "color": Color(0.3, 0.8, 1.0), "size": 9.0, "edge": true})
	var ents: Array = _ent_proxies.values() if not is_host() else _ents.values().map(func(e): return e["node"])
	for n in ents:
		if n and is_instance_valid(n) and n is Node3D and not (n.has_method("is_dead") and n.call("is_dead")):
			if n is Vehicle and (n as Vehicle).destroyed:
				continue
			out.append({"pos": (n as Node3D).global_position, "icon": "", "color": Color(1.0, 0.25, 0.25), "size": 7.0,
				"edge": n is Vehicle})
	return out


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
	if st.size() == STATE_SIZE:
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


## Blips for the minimap / map: every other player in his colour, plus the co-op mission.
func map_blips() -> Array:
	var out := coop_blips()
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
