class_name RemotePlayer
extends CharacterBody3D
## Another player in a multiplayer session: his character model mirrors his animation tree exactly
## (walk / run / sprint / jump / crouch / swim / aim / shoot / punch / reload / drive), his weapon,
## a name tag with a health bar, and his car (a kinematic copy with steering, rolling wheels,
## lights and engine sound that you can crash into).
##
## Smoothness over the internet: every state carries the sender's clock. The copy is drawn
## INTERP_MS in the past, between the two states around that moment (snapshot interpolation), so
## jitter and small packet loss never show; if states stop arriving it extrapolates briefly.
##
## Hitting him (bullets, fists, explosions, running him over) is reported to his game through the
## host; he applies the damage himself.

const INTERP_MS := 110.0
const MAX_EXTRAP_MS := 220.0

var peer_id := 0
var display_name := ""
var color := Color.WHITE
var outfit := {}
var model: CharacterModel
var car: Vehicle = null

var _snaps: Array = []          # [sender ms, state] oldest first
var _offset := 0.0              # min(receive - send) = best one-way delay + clock difference
var _delays: Array = []
var _cur: Array = []            # discrete fields come from the newest state at or before the render time
var _weapon := ""
var _car_type := ""
var _state := 0
var _hp := 250.0
var _max_hp := 250.0
var _label: Label3D
var _col: CollisionShape3D
var _weapon_vis: WeaponHolder
var _dead := false
var _recent_hit := 0


func _ready() -> void:
	add_to_group("net_players")
	collision_layer = (1 << 1) | (1 << 3)   # hit by bullets / fists / cars, the local player bumps into him
	collision_mask = 0
	_col = CollisionShape3D.new()
	var cap := CapsuleShape3D.new()
	cap.radius = 0.32
	cap.height = 1.8
	_col.shape = cap
	_col.position.y = 0.9
	add_child(_col)
	model = CharacterModel.new()
	model.name = "Model"
	model.outfit = outfit if not outfit.is_empty() else Player.default_outfit()
	add_child(model)
	_weapon_vis = WeaponHolder.new()
	_weapon_vis.name = "Weapons"
	add_child(_weapon_vis)
	_weapon_vis.setup(self, model, false)
	_label = Label3D.new()
	_label.billboard = BaseMaterial3D.BILLBOARD_ENABLED
	_label.no_depth_test = true
	_label.fixed_size = true
	_label.pixel_size = 0.0011
	_label.font_size = 34
	_label.outline_size = 10
	_label.modulate = color
	_label.outline_modulate = Color(0, 0, 0, 0.9)
	_label.position = Vector3(0, 2.25, 0)
	add_child(_label)
	_update_label()


## A new network state (see Net.pack_state).
func apply_state(st: Array, snap := false) -> void:
	var sent := int(st[15])
	var now := Time.get_ticks_msec()
	# clock offset: the least delayed packet of the last ~5 seconds (one-way delay + clock difference)
	_delays.append(float(now - sent))
	if _delays.size() > 100:
		_delays.pop_front()
	_offset = _delays.min()
	if not _snaps.is_empty() and sent <= int(_snaps[-1][0]):
		return   # old or duplicate (unreliable channel)
	_snaps.append([sent, st])
	while _snaps.size() > 40:
		_snaps.pop_front()
	if snap or _cur.is_empty():
		_apply_discrete(st)
		global_position = st[0]
		rotation.y = st[1]
		if car:
			car.global_transform = Transform3D(Basis(st[7] as Quaternion), st[6])


func _apply_discrete(st: Array) -> void:
	_cur = st
	_state = int(st[2])
	_hp = float(st[9])
	_max_hp = maxf(float(st[10]), 1.0)
	var w := String(st[4])
	if w != _weapon:
		_weapon = w
		if w != "" and not _weapon_vis.owned.has(w):
			_weapon_vis.give(w, 0)
		if w != "":
			_weapon_vis.equip(w)
	var vt := String(st[5])
	if vt != _car_type:
		_set_car(vt, st[8], st[11], st[6], st[7])
	var dead := _state == 3
	if dead != _dead:
		_dead = dead
		if dead:
			model.start_ragdoll(Vector3.UP * 10.0)
		else:
			model.stop_ragdoll()
	if not _dead:
		if vt == "":
			model.net_apply(st[3])
		else:
			model.play_loop("drive")
	_update_label()


func _set_car(vt: String, paint: Color, livery: Dictionary, pos: Vector3, rot: Quaternion) -> void:
	if car and is_instance_valid(car):
		car.queue_free()
	car = null
	_car_type = vt
	if vt == "":
		return
	car = Vehicle.create(vt, paint)
	car.livery = livery.duplicate()
	car.set_meta("net_proxy", peer_id)
	car.freeze = true
	get_parent().add_child(car)
	car.global_transform = Transform3D(Basis(rot), pos)
	car.set_kinematic(true)


func _physics_process(delta: float) -> void:
	if _snaps.is_empty():
		return
	var render_t := float(Time.get_ticks_msec()) - _offset - INTERP_MS
	# find the states around the render time
	var a: Array = _snaps[0]
	var b: Array = _snaps[0]
	for i in _snaps.size():
		if float(_snaps[i][0]) <= render_t:
			a = _snaps[i]
			b = _snaps[mini(i + 1, _snaps.size() - 1)]
		else:
			if i == 0:
				a = _snaps[0]
				b = _snaps[0]
			break
	var sa: Array = a[1]
	var sb: Array = b[1]
	var t := 0.0
	if b[0] != a[0]:
		t = clampf((render_t - float(a[0])) / float(int(b[0]) - int(a[0])), 0.0, 1.0)
	if sa != _cur:
		_apply_discrete(sa)
	# positions
	var pos: Vector3 = (sa[0] as Vector3).lerp(sb[0], t)
	var yaw := lerp_angle(float(sa[1]), float(sb[1]), t)
	var cpos: Vector3 = (sa[6] as Vector3).lerp(sb[6], t)
	var crot: Quaternion = (sa[7] as Quaternion).slerp(sb[7], t)
	var newest: Array = _snaps[-1]
	if render_t > float(newest[0]):
		# no newer state yet: extrapolate a little with the last velocity
		var ex := clampf(render_t - float(newest[0]), 0.0, MAX_EXTRAP_MS) / 1000.0
		var v: Vector3 = newest[1][16]
		pos = (newest[1][0] as Vector3) + v * ex
		cpos = (newest[1][6] as Vector3) + v * ex
		crot = newest[1][7]
		yaw = newest[1][1]
	if _car_type != "" and car and is_instance_valid(car):
		car.global_transform = Transform3D(Basis(crot), cpos)
		car.net_visual(lerpf(float(sa[12]), float(sb[12]), t), lerpf(float(sa[13]), float(sb[13]), t), int(sa[14]), delta)
		global_transform = car.global_transform * car.driver_seat_transform()
		_col.disabled = true
	else:
		_col.disabled = _dead
		global_position = pos
		rotation.y = yaw


func on_fired() -> void:
	pass   # the shooting animation arrives with the animation snapshot


func is_dead() -> bool:
	return _dead


func is_head_hit(p: Vector3) -> bool:
	return not _dead and _car_type == "" and p.y > global_position.y + 1.52


## Bullets, fists and explosions. The local player's attacks are reported to that player; on the
## host, mission enemies (host NPCs) shooting this player hurt him too.
func on_hit(damage: float, source: Node, _pos: Vector3, _dir: Vector3) -> void:
	if _dead:
		return
	if _is_local_attacker(source):
		Net.send_hit(peer_id, damage)
	elif Net.is_host() and source is NPC:
		Net.host_npc_hit(peer_id, damage)


func on_vehicle_impact(v: Node3D, rel_speed: float) -> void:
	if _dead or rel_speed < 4.0 or not _is_local_attacker(v.get("driver")):
		return
	var now := Time.get_ticks_msec()
	if now < _recent_hit:
		return
	_recent_hit = now + 1200
	Net.send_hit(peer_id, (rel_speed - 3.0) * 9.0)


func _is_local_attacker(source) -> bool:
	if source is Player:
		return true
	if source is Vehicle and (source as Vehicle).driver is Player:
		return true
	if source is Projectile:
		return (source as Projectile).shooter is Player
	return false


func _update_label() -> void:
	if _label == null:
		return
	var bars := int(round(clampf(_hp / _max_hp, 0.0, 1.0) * 10.0))
	_label.text = "%s\n%s" % [display_name, "▮".repeat(bars) + "▯".repeat(10 - bars)]
	_label.modulate = color if not _dead else Color(0.6, 0.6, 0.6)


func cleanup() -> void:
	if car and is_instance_valid(car):
		car.queue_free()
	car = null
