class_name MqttLink
extends RefCounted
## Minimal MQTT 3.1.1 client over a WebSocket (QoS 0 only). Used to find the other players over
## the internet (public MQTT brokers act as the "meeting point") and, if no direct connection is
## possible, to tunnel the game traffic. Call poll() every frame.

signal connected
signal message(topic: String, payload: PackedByteArray)
signal closed

enum S { IDLE, OPENING, WAIT_CONNACK, READY, CLOSED }

var url := ""
var state := S.IDLE
var label := ""
var _ws: WebSocketPeer
var _buf := PackedByteArray()
var _t0 := 0
var _ping_ms := 0
var _last_rx := 0
var _next_id := 1
var _subs: Array = []           # topics to (re)subscribe once connected
var _tls: TLSOptions


func open(p_url: String, tls: TLSOptions = null) -> void:
	url = p_url
	_tls = tls
	_ws = WebSocketPeer.new()
	_ws.supported_protocols = PackedStringArray(["mqtt"])
	_ws.inbound_buffer_size = 1 << 20
	_ws.outbound_buffer_size = 1 << 20
	_ws.max_queued_packets = 4096
	_buf.clear()
	_t0 = Time.get_ticks_msec()
	var err := _ws.connect_to_url(url, _tls) if _tls else _ws.connect_to_url(url)
	state = S.OPENING if err == OK else S.CLOSED


func is_ready() -> bool:
	return state == S.READY


func age_ms() -> int:
	return Time.get_ticks_msec() - _t0


func close() -> void:
	if _ws and state == S.READY:
		_ws.put_packet(PackedByteArray([0xE0, 0x00]))   # DISCONNECT
	if _ws:
		_ws.close()
	if state != S.CLOSED:
		state = S.CLOSED
		closed.emit()


func subscribe(topic: String) -> void:
	if not _subs.has(topic):
		_subs.append(topic)
	if state == S.READY:
		_send_subscribe(topic)


func publish(topic: String, payload: PackedByteArray, retain := false) -> bool:
	if state != S.READY:
		return false
	var body := _str(topic)
	body.append_array(payload)
	var pkt := PackedByteArray([0x30 | (0x01 if retain else 0x00)])
	pkt.append_array(_varlen(body.size()))
	pkt.append_array(body)
	return _ws.put_packet(pkt) == OK


func poll() -> void:
	if _ws == null or state == S.CLOSED or state == S.IDLE:
		return
	_ws.poll()
	var ws_state := _ws.get_ready_state()
	if ws_state == WebSocketPeer.STATE_CLOSED:
		state = S.CLOSED
		closed.emit()
		return
	var now := Time.get_ticks_msec()
	if state == S.OPENING:
		if ws_state == WebSocketPeer.STATE_OPEN:
			_send_connect()
			state = S.WAIT_CONNACK
		elif now - _t0 > 8000:
			close()
		return
	while _ws.get_available_packet_count() > 0:
		_buf.append_array(_ws.get_packet())
		_last_rx = now
	_parse()
	if state == S.WAIT_CONNACK and now - _t0 > 10000:
		close()
		return
	if state == S.READY:
		if now - _ping_ms > 20000:
			_ping_ms = now
			_ws.put_packet(PackedByteArray([0xC0, 0x00]))   # PINGREQ
		if now - _last_rx > 65000:   # broker silent (keepalive 30 s -> it answers pings)
			close()


func _send_connect() -> void:
	var cid := "hh"
	for i in 10:
		cid += "%02x" % (randi() % 256)
	var body := _str("MQTT")
	body.append_array(PackedByteArray([0x04, 0x02, 0x00, 30]))   # level 4, clean session, keepalive 30 s
	body.append_array(_str(cid))
	var pkt := PackedByteArray([0x10])
	pkt.append_array(_varlen(body.size()))
	pkt.append_array(body)
	_ws.put_packet(pkt)


func _send_subscribe(topic: String) -> void:
	var body := PackedByteArray([(_next_id >> 8) & 0xFF, _next_id & 0xFF])
	_next_id = _next_id % 65000 + 1
	body.append_array(_str(topic))
	body.append(0x00)   # QoS 0
	var pkt := PackedByteArray([0x82])
	pkt.append_array(_varlen(body.size()))
	pkt.append_array(body)
	_ws.put_packet(pkt)


func _parse() -> void:
	while _buf.size() >= 2:
		# remaining length (1-4 bytes)
		var mult := 1
		var ln := 0
		var i := 1
		var complete := false
		while i < _buf.size() and i <= 4:
			var b := _buf[i]
			ln += (b & 0x7F) * mult
			mult *= 128
			i += 1
			if b & 0x80 == 0:
				complete = true
				break
		if not complete:
			if i > 4:
				close()   # malformed
			return
		if _buf.size() < i + ln:
			return
		var kind := _buf[0] >> 4
		var flags := _buf[0] & 0x0F
		var body := _buf.slice(i, i + ln)
		_buf = _buf.slice(i + ln)
		match kind:
			2:   # CONNACK
				if body.size() >= 2 and body[1] == 0:
					state = S.READY
					_ping_ms = Time.get_ticks_msec()
					_last_rx = _ping_ms
					for t in _subs:
						_send_subscribe(t)
					connected.emit()
				else:
					close()
					return
			3:   # PUBLISH
				if body.size() < 2:
					continue
				var tl := (body[0] << 8) | body[1]
				if body.size() < 2 + tl:
					continue
				var topic := body.slice(2, 2 + tl).get_string_from_utf8()
				var p := 2 + tl
				var qos := (flags >> 1) & 0x03
				if qos > 0:
					p += 2   # packet id (brokers downgrade to our QoS 0, but be safe)
					if qos == 1 and body.size() >= p:   # PUBACK
						_ws.put_packet(PackedByteArray([0x40, 0x02, body[p - 2], body[p - 1]]))
				if p <= body.size():
					message.emit(topic, body.slice(p))
			_:
				pass   # SUBACK, PINGRESP, ...


static func _str(s: String) -> PackedByteArray:
	var b := s.to_utf8_buffer()
	var out := PackedByteArray([(b.size() >> 8) & 0xFF, b.size() & 0xFF])
	out.append_array(b)
	return out


static func _varlen(n: int) -> PackedByteArray:
	var out := PackedByteArray()
	while true:
		var d := n % 128
		n = n / 128
		if n > 0:
			d |= 0x80
		out.append(d)
		if n == 0:
			break
	return out
