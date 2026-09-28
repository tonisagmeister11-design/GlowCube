class_name WorldShare
extends Node
## Host: friends near the host see the host's world - the same traffic, parked cars,
## pedestrians and police - instead of their own. The host simulates the city around every
## friend in range (see Net.share_centers, used by the traffic and pedestrian managers) and sends
## each friend the things within his view, closer ones more often. Compact binary records keep
## this small enough for home internet and the relay.

const SHARE_ENTER := 250.0
const SHARE_EXTRA := 80.0       # hysteresis: leave only beyond enter + this
const VIEW_R := 210.0
const TICK := 0.1
const MAX_SPAWNS := 7           # new things per friend and tick (spreads bursts)
const MAX_NPC_SPAWNS := 3       # characters are the expensive part for the friend's game
const MAX_PACKET := 1000

var _clients := {}              # peer id -> {"shared": bool, "known": {eid: {t, sig, snap, w}}}
var _t := 0.0
var _gone: Array = []           # eids freed since the last tick


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS


func forget(id: int) -> void:
	_clients.erase(id)


func is_shared(id: int) -> bool:
	return bool(_clients.get(id, {}).get("shared", false))


func _process(delta: float) -> void:
	_t -= delta
	if _t > 0.0:
		return
	_t = TICK
	var w := GameWorld.instance
	if not Net.is_host() or w == null or w.player == null or not is_instance_valid(w.player) or w.traffic == null or w.peds == null:
		Net.share_centers = []
		return
	var host_pos := w.player.global_position
	var enter := clampf(w.streaming.load_radius - 150.0, 120.0, SHARE_ENTER)
	var centers := []
	for id in Net.players:
		if id == 1:
			continue
		if not Net.players[id]["world"]:
			_clients.erase(id)
			continue
		var st: Array = Net.players[id]["st"]
		if st.size() != Net.STATE_SIZE:
			continue
		var pos: Vector3 = st[6] if String(st[5]) != "" else st[0]
		var c: Dictionary = _clients.get(id, {"shared": false, "known": {}})
		_clients[id] = c
		var d := pos.distance_to(host_pos)
		var shared: bool = d < (enter + SHARE_EXTRA if c["shared"] else enter)
		if shared != c["shared"]:
			c["shared"] = shared
			Net._share_mode.rpc_id(id, shared)
			if not shared:
				for eid in c["known"]:
					Net._ent_remove.rpc_id(id, eid)
				c["known"] = {}
		if shared:
			centers.append([pos, float(st[1])])
	Net.share_centers = centers
	for eid in _gone:
		for id in _clients:
			if (_clients[id]["known"] as Dictionary).has(eid):
				(_clients[id]["known"] as Dictionary).erase(eid)
				Net._ent_remove.rpc_id(id, eid)
	_gone.clear()
	if centers.is_empty():
		return
	var ents := _eligible(w)
	for id in _clients:
		if _clients[id]["shared"]:
			var st: Array = Net.players[id]["st"]
			var pos: Vector3 = st[6] if String(st[5]) != "" else st[0]
			_update_client(id, _clients[id], pos, ents)


## Everything that belongs to the shared world (not the players themselves, not mission things).
func _eligible(w: GameWorld) -> Array:
	var out := []
	for v in w.traffic.vehicles:
		if not is_instance_valid(v) or not (v as Node).is_inside_tree():
			continue
		var veh := v as Vehicle
		if veh.has_meta("net_proxy") or veh.has_meta("net_entity"):
			continue
		if veh.driver is Player:
			continue   # the host's own car is part of his player state
		if veh.has_meta("net_eid") and not veh.has_meta("net_amb"):
			continue   # co-op mission vehicle: sent to everybody by Net
		out.append(veh)
	for n in w.peds.peds:
		if not is_instance_valid(n) or not (n as Node).is_inside_tree():
			continue
		if (n as Node).has_meta("net_eid") and not (n as Node).has_meta("net_amb"):
			continue
		out.append(n)
	return out


func _ensure_eid(n: Node3D) -> int:
	if n.has_meta("net_eid"):
		return int(n.get_meta("net_eid"))
	var eid := Net.host_register_ambient(n, "npc" if n is NPC else "vehicle")
	n.tree_exiting.connect(func(): _gone.append(eid))
	return eid


func _spawn_data(n: Node3D, for_id: int) -> Dictionary:
	if n is NPC:
		return {"outfit": (n as NPC).outfit, "amb": true, "pos": n.global_position, "yaw": n.rotation.y}
	var v := n as Vehicle
	# the spawn message is reliable: the car stands in the right place even if updates get lost
	var d := {"type": v.type_id, "paint": v.paint, "livery": v.livery, "amb": true,
		"pos": v.global_position, "rot": v.global_basis.get_rotation_quaternion(), "mv": absf(v.speed_kmh) > 0.5}
	var ret = v.get_meta("net_ret", [])
	if ret is Array and ret.size() == 2 and int(ret[0]) == for_id:
		d["ret"] = int(ret[1])   # the car he just got out of: he swaps his copy for ours
	var tm := GameWorld.instance.traffic as TrafficManager
	if tm and tm.drivers.has(v):
		var drv: TrafficDriver = tm.drivers[v]
		if drv.mode != TrafficDriver.Mode.ABANDONED:
			d["drv"] = drv.npc_outfit
	return d


func _update_client(id: int, c: Dictionary, pos: Vector3, ents: Array) -> void:
	var known: Dictionary = c["known"]
	var now := Time.get_ticks_msec()
	var seen := {}
	var spawns := 0
	var npc_spawns := 0
	var buf := StreamPeerBuffer.new()
	buf.put_u32(now)
	# nearest first: close things appear first, far ones a moment later (no hitch on arrival)
	var near: Array = []
	for n in ents:
		var node := n as Node3D
		var d := node.global_position.distance_to(pos)
		if d <= VIEW_R + (30.0 if known.has(int(node.get_meta("net_eid", 0))) else 0.0):
			near.append([d, node])
	near.sort_custom(func(x, y): return x[0] < y[0])
	for e in near:
		var node: Node3D = e[1]
		var d: float = e[0]
		var eid := int(node.get_meta("net_eid", 0))
		if eid == 0:
			eid = _ensure_eid(node)
		if not known.has(eid):
			if spawns >= MAX_SPAWNS or (node is NPC and npc_spawns >= MAX_NPC_SPAWNS):
				continue
			spawns += 1
			if node is NPC:
				npc_spawns += 1
			Net._ent_spawn.rpc_id(id, eid, "npc" if node is NPC else "vehicle", _spawn_data(node, id))
			known[eid] = {"t": -100000, "sig": null, "snap": null, "w": null}
		seen[eid] = true
		var k: Dictionary = known[eid]
		var interval := 95 if d < 50.0 else (150 if d < 110.0 else 300)
		if now - int(k["t"]) < interval:
			continue
		if node is NPC:
			_put_npc(buf, eid, node as NPC, k, now)
		else:
			if not _put_car(buf, eid, node as Vehicle, k, now):
				continue
		k["t"] = now
		if buf.get_size() > MAX_PACKET:
			Net._amb_states.rpc_id(id, buf.data_array)
			buf = StreamPeerBuffer.new()
			buf.put_u32(now)
	if buf.get_size() > 4:
		Net._amb_states.rpc_id(id, buf.data_array)
	for eid in known.keys():
		if not seen.has(eid):
			known.erase(eid)
			Net._ent_remove.rpc_id(id, eid)


func _put_npc(buf: StreamPeerBuffer, eid: int, n: NPC, k: Dictionary, now: int) -> void:
	var snap: Array = n.model.net_snapshot()
	var wid := n.weapons.current_id()
	var flags := (1 if n.is_dead() else 0)
	var snap_b := PackedByteArray()
	# animation and weapon only when they change (and every 2 s, in case a packet was lost)
	var refresh := now - int(k.get("st", 0)) > 2000
	if k["snap"] != snap or refresh:
		snap_b = var_to_bytes(snap)
		if snap_b.size() < 400:
			flags |= 2
			k["snap"] = snap.duplicate()
	if k["w"] != wid or refresh:
		flags |= 4
		k["w"] = wid
	if refresh:
		k["st"] = now
	buf.put_u8(1)
	buf.put_u32(eid)
	var p := n.global_position
	buf.put_float(p.x)
	buf.put_float(p.y)
	buf.put_float(p.z)
	buf.put_16(int(wrapf(n.rotation.y, -PI, PI) / PI * 32767.0))
	buf.put_u8(flags)
	if flags & 2:
		buf.put_u16(snap_b.size())
		buf.put_data(snap_b)
	if flags & 4:
		var wb := wid.to_utf8_buffer()
		buf.put_u8(mini(wb.size(), 40))
		buf.put_data(wb.slice(0, 40))


## false = nothing new (a parked car that did not move)
func _put_car(buf: StreamPeerBuffer, eid: int, v: Vehicle, k: Dictionary, now: int) -> bool:
	var p := v.global_position
	var q := v.global_basis.get_rotation_quaternion()
	var moving := v.speed_kmh > 0.5 or v.siren_on
	var sig := [p.snapped(Vector3.ONE * 0.02), v.destroyed, v.headlights_on, int(v.body_health / 25.0)]
	k["n"] = int(k.get("n", 0)) + 1
	if not moving and k["sig"] == sig and now - int(k["t"]) < 2500 and int(k["n"]) > 3:
		return false
	k["sig"] = sig
	var tm := GameWorld.instance.traffic as TrafficManager
	var driven := tm != null and tm.drivers.has(v) and (tm.drivers[v] as TrafficDriver).mode != TrafficDriver.Mode.ABANDONED
	var flags := (1 if v.headlights_on else 0) | (2 if v.brake_input > 0.1 else 0) | (4 if v.siren_on else 0) \
		| (8 if v.destroyed else 0) | (16 if driven or v.driver != null else 0)
	buf.put_u8(2)
	buf.put_u32(eid)
	buf.put_float(p.x)
	buf.put_float(p.y)
	buf.put_float(p.z)
	buf.put_16(int(clampf(q.x, -1, 1) * 32767.0))
	buf.put_16(int(clampf(q.y, -1, 1) * 32767.0))
	buf.put_16(int(clampf(q.z, -1, 1) * 32767.0))
	buf.put_16(int(clampf(q.w, -1, 1) * 32767.0))
	buf.put_8(int(clampf(v.net_steer(), -1.0, 1.0) * 100.0))
	buf.put_16(int(clampf(v.speed_kmh, -400.0, 400.0) * 10.0))
	buf.put_u8(flags)
	buf.put_u16(int(clampf(v.body_health, 0.0, 65000.0)))
	return true


## Host: a friend takes one of the shared cars - the driver flees, the car becomes his.
func host_take_vehicle(v: Vehicle, by_id: int) -> void:
	var w := GameWorld.instance
	var tm := w.traffic as TrafficManager
	var drv = tm.drivers.get(v)
	if drv and (drv as TrafficDriver).mode != TrafficDriver.Mode.ABANDONED:
		var by: Node = Net.proxies().get(by_id)
		var side := v.global_basis.x * -2.2
		w.peds.spawn_fleeing_driver(v.global_position + side + Vector3.UP * 0.2, (drv as TrafficDriver).npc_outfit,
			by if by else v)
	if tm.parked.has(v):
		w.data.parking[tm.parked[v]]["occupied"] = false
	tm.keep.erase(v)
	v.queue_free()
