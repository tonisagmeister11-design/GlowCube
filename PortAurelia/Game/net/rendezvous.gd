class_name Rendezvous
extends Node
## Internet connection helper, so a friend on a completely different network can join with only
## the lobby code - no router settings needed:
##  1. Meeting point: both games connect to public MQTT brokers (three independent ones, for
##     reliability) and exchange their addresses on a topic derived from the lobby code. The
##     messages are encrypted and signed with a key derived from the code.
##  2. UDP hole punching: both sides send packets to each other at the same time, which opens
##     the home routers for a direct connection (the technique video calls use). The host sends
##     from the game port itself (ENetConnection.socket_send), so the opened path is the game's.
##  3. Relay: if the routers stay closed (strict/mobile networks), the game traffic is tunnelled
##     through the broker instead. Slower, but it always works.

const BROKERS := [
	["wss://broker.hivemq.com:8884/mqtt", "ws://broker.hivemq.com:8000/mqtt"],
	["wss://broker.emqx.io:8084/mqtt", "ws://broker.emqx.io:8083/mqtt"],
	["wss://test.mosquitto.org:8081/mqtt", "ws://test.mosquitto.org:8080/mqtt"],
]
const STUN_SERVERS := [["stun.l.google.com", 19302], ["stun.cloudflare.com", 3478], ["stun1.l.google.com", 19302]]
const TOPIC_ROOT := "harborheat5"
const RELAY_FLUSH_MS := 20
const MAX_BRIDGES := 8

var room := ""
var _key := PackedByteArray()
var _mac := PackedByteArray()
var _slots: Array = []          # [{urls, idx, link, retry_at}]
var _is_host := false
var _crypto := Crypto.new()

# host
var host_cands: Array = []      # [[ip, port], ...] announced to joiners
var enet: ENetConnection        # the host's game socket (for punching and keep-alive)
var _punch: Dictionary = {}     # nonce -> {cands, until}
var _punch_t := 0
var _joins_ms: Array = []
var _bridges: Dictionary = {}   # "slot:nonce" -> {udp, slot, nonce, last, out, flush}
var _stun_keep: Array = []      # [ip, port] of the STUN server that saw our mapping
var _keep_t := 0

# client
var nonce := ""
var found_cands: Array = []     # host addresses learned from the offer / answer
var contact_slot := -1          # first broker on which the host answered
var host_alive := false         # the host answered us (not only an old stored offer)
var host_alive_ms := 0          # when the first answer arrived
var host_protocol := -1
var _join_msg := PackedByteArray()
var _join_t := 0
var _relay: PacketPeerUDP
var _relay_slot := -1
var _relay_enet_port := 0
var _relay_out := PackedByteArray()
var _relay_flush := 0


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS


static func broker_urls() -> Array:
	if OS.has_environment("HH_MQTT"):
		var out := []
		for u in OS.get_environment("HH_MQTT").split(",", false):
			out.append([u])
		return out
	return BROKERS


static func stun_servers() -> Array:
	if OS.has_environment("HH_STUN"):
		var out := []
		for s in OS.get_environment("HH_STUN").split(",", false):
			var hp := s.split(":")
			out.append([hp[0], int(hp[1]) if hp.size() > 1 else 3478])
		return out
	return STUN_SERVERS


static func norm_code(code: String) -> String:
	var out := ""
	for ch in code.to_upper():
		if (ch >= "A" and ch <= "Z") or (ch >= "0" and ch <= "9"):
			out += ch
	return out


func set_code(code: String) -> void:
	var c := norm_code(code)
	room = ("hh5room|" + c).sha256_text().substr(0, 24)
	_key = ("hh5key|" + c).sha256_buffer()
	_mac = ("hh5mac|" + c).sha256_buffer()


func topic(sub: String) -> String:
	return "%s/%s/%s" % [TOPIC_ROOT, room, sub]


# ================================================================== broker links
func open_links() -> void:
	close_links()
	for urls in broker_urls():
		var s := {"urls": urls, "idx": 0, "link": null, "retry_at": 0, "fails": 0}
		_slots.append(s)
		_open_slot(s)


func close_links() -> void:
	for s in _slots:
		if s["link"]:
			(s["link"] as MqttLink).close()
	_slots.clear()


func ready_links() -> int:
	var n := 0
	for s in _slots:
		if s["link"] and (s["link"] as MqttLink).is_ready():
			n += 1
	return n


func _open_slot(s: Dictionary) -> void:
	var l := MqttLink.new()
	var url: String = s["urls"][s["idx"]]
	l.label = url
	var slot := _slots.find(s)
	l.connected.connect(func(): _on_link_ready(slot))
	l.message.connect(func(t, p): _on_message(slot, t, p))
	var tls: TLSOptions = null
	if url.begins_with("wss://") and OS.has_environment("HH_MQTT_CA"):
		var cert := X509Certificate.new()
		if cert.load(OS.get_environment("HH_MQTT_CA")) == OK:
			tls = TLSOptions.client(cert)
	l.open(url, tls)
	s["link"] = l
	_resubscribe(s)


func _resubscribe(s: Dictionary) -> void:
	var l: MqttLink = s["link"]
	if room == "":
		return
	if _is_host:
		l.subscribe(topic("j"))
		l.subscribe(topic("r/+/u"))
	elif nonce != "":
		l.subscribe(topic("o"))
		l.subscribe(topic("a/" + nonce))
		l.subscribe(topic("r/%s/d" % nonce))


func _poll_links() -> void:
	var now := Time.get_ticks_msec()
	for s in _slots:
		var l: MqttLink = s["link"]
		if l == null:
			if now >= int(s["retry_at"]):
				_open_slot(s)
			continue
		l.poll()
		if l.state == MqttLink.S.CLOSED:
			# encrypted websocket failed -> try the plain one; then retry later
			s["fails"] = int(s["fails"]) + 1
			s["idx"] = (int(s["idx"]) + 1) % (s["urls"] as Array).size()
			s["link"] = null
			s["retry_at"] = now + (0 if int(s["idx"]) != 0 else mini(4000 * int(s["fails"]), 20000))


func _publish(slot: int, t: String, payload: PackedByteArray, retain := false) -> void:
	if slot < 0 or slot >= _slots.size():
		return
	var l: MqttLink = _slots[slot]["link"]
	if l and l.is_ready():
		l.publish(t, payload, retain)


func _publish_all(t: String, payload: PackedByteArray, retain := false) -> void:
	for i in _slots.size():
		_publish(i, t, payload, retain)


func _on_link_ready(slot: int) -> void:
	if _is_host:
		if not host_cands.is_empty():
			_publish(slot, topic("o"), seal({"v": Net.PROTOCOL, "c": host_cands}), true)
	elif not _join_msg.is_empty():
		_publish(slot, topic("j"), _join_msg)


# ================================================================== sealed messages
func seal(d: Dictionary) -> PackedByteArray:
	var plain := JSON.stringify(d).to_utf8_buffer()
	var pad := 16 - plain.size() % 16
	for i in pad:
		plain.append(pad)
	var iv := _crypto.generate_random_bytes(16)
	var aes := AESContext.new()
	aes.start(AESContext.MODE_CBC_ENCRYPT, _key, iv)
	var ct := aes.update(plain)
	aes.finish()
	var out := iv
	out.append_array(ct)
	out.append_array(_crypto.hmac_digest(HashingContext.HASH_SHA256, _mac, out))
	return out


func open_sealed(b: PackedByteArray) -> Dictionary:
	if b.size() < 16 + 16 + 32 or (b.size() - 48) % 16 != 0 or b.size() > 8192:
		return {}
	var body := b.slice(0, b.size() - 32)
	var mac := b.slice(b.size() - 32)
	if not _crypto.constant_time_compare(_crypto.hmac_digest(HashingContext.HASH_SHA256, _mac, body), mac):
		return {}
	var aes := AESContext.new()
	aes.start(AESContext.MODE_CBC_DECRYPT, _key, body.slice(0, 16))
	var plain := aes.update(body.slice(16))
	aes.finish()
	var pad := plain[plain.size() - 1]
	if pad < 1 or pad > 16 or pad > plain.size():
		return {}
	var d = JSON.parse_string(plain.slice(0, plain.size() - pad).get_string_from_utf8())
	return d if d is Dictionary else {}


static func valid_cands(c) -> Array:
	var out := []
	if not c is Array:
		return out
	for e in c:
		if out.size() >= 10:
			break
		if e is Array and e.size() == 2 and e[0] is String and (e[0] as String).is_valid_ip_address() \
				and (e[1] is float or e[1] is int) and int(e[1]) > 0 and int(e[1]) < 65536:
			var a := [String(e[0]), int(e[1])]
			if not out.has(a):
				out.append(a)
	return out


# ================================================================== host
func host_begin(code: String, cands: Array) -> void:
	_is_host = true
	set_code(code)
	host_cands = cands
	if _slots.is_empty():
		open_links()
	else:
		for s in _slots:
			if s["link"]:
				_resubscribe(s)
	_publish_all(topic("o"), seal({"v": Net.PROTOCOL, "c": host_cands}), true)


## The code changed (public address found later): move to the new meeting point.
func host_update(code: String, cands: Array) -> void:
	var old_offer := topic("o")
	var old_room := room
	set_code(code)
	host_cands = cands
	if room != old_room:
		_publish_all(old_offer, PackedByteArray(), true)   # remove the old stored offer
		for s in _slots:
			if s["link"]:
				_resubscribe(s)
	_publish_all(topic("o"), seal({"v": Net.PROTOCOL, "c": host_cands}), true)


func host_end() -> void:
	if _is_host and room != "":
		_publish_all(topic("o"), PackedByteArray(), true)
		for s in _slots:
			if s["link"] and (s["link"] as MqttLink).is_ready():
				(s["link"] as MqttLink).poll()
	for k in _bridges:
		(_bridges[k]["udp"] as PacketPeerUDP).close()
	_bridges.clear()
	close_links()


func set_stun_keepalive(server: Array) -> void:
	_stun_keep = server


func _host_join(slot: int, payload: PackedByteArray) -> void:
	var now := Time.get_ticks_msec()
	_joins_ms = _joins_ms.filter(func(t): return now - int(t) < 60000)
	if _joins_ms.size() > 40:
		return
	var d := open_sealed(payload)
	var n := String(d.get("n", ""))
	if n.length() != 16 or not n.is_valid_hex_number():
		return
	_joins_ms.append(now)
	var cands := valid_cands(d.get("c", []))
	if not _punch.has(n):
		_punch[n] = {"cands": cands, "until": now + 15000}
	else:
		for c in cands:
			if not (_punch[n]["cands"] as Array).has(c):
				(_punch[n]["cands"] as Array).append(c)
		_punch[n]["until"] = now + 15000
	_publish(slot, topic("a/" + n), seal({"v": Net.PROTOCOL, "c": host_cands}))
	if OS.has_environment("HH_NET_DEBUG"):
		print("NETDBG join request %s via %s cands %s" % [n, _slots[slot]["link"].label, str(cands)])


func _host_punch() -> void:
	var now := Time.get_ticks_msec()
	if enet == null:
		return
	if now - _punch_t >= 100:
		_punch_t = now
		for n in _punch.keys():
			var p: Dictionary = _punch[n]
			if now > int(p["until"]):
				_punch.erase(n)
				continue
			var pkt := ("HHP" + String(n)).to_ascii_buffer()
			for c in p["cands"]:
				enet.socket_send(String(c[0]), int(c[1]), pkt)
	# keep the router's mapping of the game port alive (the answer is ignored by ENet)
	if not _stun_keep.is_empty() and now - _keep_t > 15000:
		_keep_t = now
		enet.socket_send(String(_stun_keep[0]), int(_stun_keep[1]), stun_request())


## Host side of the relay: every relayed player gets a local socket that talks to our own game
## port, so the game sees him like any other connection.
func _host_relay_in(slot: int, n: String, payload: PackedByteArray) -> void:
	if n.length() != 16 or not n.is_valid_hex_number():
		return
	var k := "%d:%s" % [slot, n]
	if not _bridges.has(k):
		if _bridges.size() >= MAX_BRIDGES:
			return
		var udp := PacketPeerUDP.new()
		var ip := local_game_ip()
		if udp.bind(0, ip) != OK:
			return
		udp.set_dest_address(ip, Net.PORT)
		_bridges[k] = {"udp": udp, "slot": slot, "nonce": n, "last": 0, "out": PackedByteArray(), "flush": 0}
		if OS.has_environment("HH_NET_DEBUG"):
			print("NETDBG relay bridge for %s via %s" % [n, _slots[slot]["link"].label])
	var b: Dictionary = _bridges[k]
	b["last"] = Time.get_ticks_msec()
	for dg in unbatch(payload):
		(b["udp"] as PacketPeerUDP).put_packet(dg)


func _host_relay_pump() -> void:
	var now := Time.get_ticks_msec()
	for k in _bridges.keys():
		var b: Dictionary = _bridges[k]
		var udp: PacketPeerUDP = b["udp"]
		while udp.get_available_packet_count() > 0:
			batch_add(b["out"], udp.get_packet())
		if (b["out"] as PackedByteArray).size() > 0 and (now - int(b["flush"]) >= RELAY_FLUSH_MS or (b["out"] as PackedByteArray).size() > 6000):
			_publish(int(b["slot"]), topic("r/%s/d" % b["nonce"]), b["out"])
			b["out"] = PackedByteArray()
			b["flush"] = now
		if now - int(b["last"]) > 30000:
			udp.close()
			_bridges.erase(k)


static func local_game_ip() -> String:
	return OS.get_environment("HH_BIND_IP") if OS.has_environment("HH_BIND_IP") else "127.0.0.1"


# ================================================================== client
func client_begin(code: String) -> void:
	_is_host = false
	set_code(code)
	nonce = ""
	var b := _crypto.generate_random_bytes(8)
	nonce = b.hex_encode()
	found_cands.clear()
	contact_slot = -1
	host_alive = false
	host_alive_ms = 0
	host_protocol = -1
	_join_msg = PackedByteArray()
	open_links()


func client_publish_join(cands: Array) -> void:
	_join_msg = seal({"v": Net.PROTOCOL, "n": nonce, "c": cands})
	_join_t = Time.get_ticks_msec()
	_publish_all(topic("j"), _join_msg)


func _client_msg(slot: int, t: String, payload: PackedByteArray) -> void:
	if t == topic("r/%s/d" % nonce):
		if _relay and _relay_enet_port > 0:
			for dg in unbatch(payload):
				_relay.put_packet(dg)
		return
	var d := open_sealed(payload)
	if d.is_empty():
		return
	host_protocol = int(d.get("v", -1))
	for c in valid_cands(d.get("c", [])):
		if not found_cands.has(c):
			found_cands.append(c)
	if t == topic("a/" + nonce):
		if not host_alive:
			host_alive_ms = Time.get_ticks_msec()
		host_alive = true
		if contact_slot < 0:
			contact_slot = slot
	elif contact_slot < 0 and _slots[slot]["link"] != null:
		contact_slot = slot   # stored offer: probably alive, the answer confirms it


## Client side of the relay: a local socket that plays "host" for our own game.
func start_relay() -> int:
	if contact_slot < 0:
		return 0
	_relay = PacketPeerUDP.new()
	if _relay.bind(0, "127.0.0.1") != OK:
		_relay = null
		return 0
	_relay_slot = contact_slot
	_relay_enet_port = 0
	return _relay.get_local_port()


func relay_active() -> bool:
	return _relay != null


func stop_relay() -> void:
	if _relay:
		_relay.close()
	_relay = null
	_relay_enet_port = 0


func _client_relay_pump() -> void:
	if _relay == null:
		return
	var now := Time.get_ticks_msec()
	while _relay.get_available_packet_count() > 0:
		var dg := _relay.get_packet()
		if _relay_enet_port == 0:
			_relay_enet_port = _relay.get_packet_port()
			_relay.set_dest_address("127.0.0.1", _relay_enet_port)
		batch_add(_relay_out, dg)
	if _relay_out.size() > 0 and (now - _relay_flush >= RELAY_FLUSH_MS or _relay_out.size() > 6000):
		_publish(_relay_slot, topic("r/%s/u" % nonce), _relay_out)
		_relay_out = PackedByteArray()
		_relay_flush = now


func client_end() -> void:
	stop_relay()
	close_links()


# ================================================================== frame
func _process(_delta: float) -> void:
	_poll_links()
	if _is_host:
		_host_punch()
		_host_relay_pump()
	else:
		_client_relay_pump()
		if not _join_msg.is_empty() and Time.get_ticks_msec() - _join_t > 2000 and _relay == null:
			_join_t = Time.get_ticks_msec()
			_publish_all(topic("j"), _join_msg)


func stop_join_repeats() -> void:
	_join_msg = PackedByteArray()


func _on_message(slot: int, t: String, payload: PackedByteArray) -> void:
	if _is_host:
		if t == topic("j"):
			_host_join(slot, payload)
		elif t.begins_with(topic("r/")) and t.ends_with("/u"):
			var n := t.trim_prefix(topic("r/")).trim_suffix("/u")
			_host_relay_in(slot, n, payload)
	else:
		_client_msg(slot, t, payload)


# ================================================================== helpers
static func batch_add(buf: PackedByteArray, dg: PackedByteArray) -> void:
	if dg.size() > 4096:
		return
	buf.append((dg.size() >> 8) & 0xFF)
	buf.append(dg.size() & 0xFF)
	buf.append_array(dg)


static func unbatch(b: PackedByteArray) -> Array:
	var out := []
	var p := 0
	while p + 2 <= b.size():
		var n := (b[p] << 8) | b[p + 1]
		p += 2
		if n == 0 or p + n > b.size():
			break
		out.append(b.slice(p, p + n))
		p += n
	return out


static func stun_request() -> PackedByteArray:
	var req := PackedByteArray([0x00, 0x01, 0x00, 0x00, 0x21, 0x12, 0xA4, 0x42])
	for i in 12:
		req.append(randi() % 256)
	return req


## STUN binding response -> [ip, port] (XOR-MAPPED-ADDRESS, else MAPPED-ADDRESS), [] if invalid.
static func parse_stun(r: PackedByteArray) -> Array:
	if r.size() < 20 or r[0] != 0x01 or r[1] != 0x01 or r[4] != 0x21 or r[5] != 0x12 or r[6] != 0xA4 or r[7] != 0x42:
		return []
	var pos := 20
	var mapped := []
	while pos + 4 <= r.size():
		var t := (r[pos] << 8) | r[pos + 1]
		var ln := (r[pos + 2] << 8) | r[pos + 3]
		var v := pos + 4
		if v + ln > r.size():
			break
		if (t == 0x0020 or t == 0x0001) and ln >= 8 and r[v + 1] == 0x01:
			var port := (r[v + 2] << 8) | r[v + 3]
			var a := [r[v + 4], r[v + 5], r[v + 6], r[v + 7]]
			if t == 0x0020:
				port ^= 0x2112
				a = [a[0] ^ 0x21, a[1] ^ 0x12, a[2] ^ 0xA4, a[3] ^ 0x42]
			var got := ["%d.%d.%d.%d" % a, port]
			if t == 0x0020:
				return got
			mapped = got
		pos = v + ln + ((4 - ln % 4) % 4)
	return mapped


## Our own addresses for the other side to try: public (STUN), home network and IPv6.
static func local_cands(port: int) -> Array:
	var out := []
	if OS.has_environment("HH_NO_LAN") or OS.has_environment("HH_BIND_IP"):
		return out
	var v6 := 0
	for a in IP.get_local_addresses():
		if a.count(".") == 3:
			if a.begins_with("127.") or a.begins_with("169.254.") or a.begins_with("0."):
				continue
			out.append([a, port])
		elif a.contains(":") and v6 < 3:
			var l := a.to_lower()
			# global unicast only (2000::/3), no link-local / unique-local / loopback
			if (l.begins_with("2") or l.begins_with("3")) and not l.contains("%"):
				out.append([a, port])
				v6 += 1
	return out.slice(0, 8)


## Resolves the STUN servers without blocking the game. Returns [[ip, port], ...] once ready.
class Resolver:
	var items: Array = []    # [queue id or -1, host, port, ip]

	func start(list: Array) -> void:
		for s in list:
			var h := String(s[0])
			if h.is_valid_ip_address():
				items.append([-1, h, int(s[1]), h])
			else:
				items.append([IP.resolve_hostname_queue_item(h, IP.TYPE_IPV4), h, int(s[1]), ""])

	func poll() -> bool:
		var done := true
		for it in items:
			if int(it[0]) < 0:
				continue
			var st := IP.get_resolve_item_status(int(it[0]))
			if st == IP.RESOLVER_STATUS_WAITING:
				done = false
			else:
				if st == IP.RESOLVER_STATUS_DONE:
					it[3] = IP.get_resolve_item_address(int(it[0]))
				IP.erase_resolve_item(int(it[0]))
				it[0] = -1
		return done

	func servers() -> Array:
		var out := []
		for it in items:
			if String(it[3]).is_valid_ip_address():
				out.append([String(it[3]), int(it[2])])
		return out
