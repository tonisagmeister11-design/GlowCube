class_name RemoteEntity
extends CharacterBody3D
## Client copy of a character simulated by the host (co-op mission enemies): same position,
## animation, weapon and death for everybody. Hits by the local player are reported to the host,
## who applies them to the real enemy.

const INTERP_MS := 120.0

var eid := 0
var outfit := {}
var model: CharacterModel
var _snaps: Array = []
var _delays: Array = []
var _offset := 0.0
var _dead := false
var _weapon := ""
var _weapon_vis: WeaponHolder
var _col: CollisionShape3D


func _ready() -> void:
	add_to_group("net_entities")
	collision_layer = 1 << 3
	collision_mask = 0
	_col = CollisionShape3D.new()
	var cap := CapsuleShape3D.new()
	cap.radius = 0.3
	cap.height = 1.78
	_col.shape = cap
	_col.position.y = 0.89
	add_child(_col)
	model = CharacterModel.new()
	model.prune_hidden = true
	model.outfit = outfit
	add_child(model)
	_weapon_vis = WeaponHolder.new()
	add_child(_weapon_vis)
	_weapon_vis.setup(self, model, false)


## s = [eid, pos, yaw, anim snapshot, weapon, dead, sender ms]
func push(s: Array) -> void:
	var sent := int(s[6])
	_delays.append(float(Time.get_ticks_msec() - sent))
	if _delays.size() > 60:
		_delays.pop_front()
	_offset = _delays.min()
	if not _snaps.is_empty() and sent <= int(_snaps[-1][0]):
		return
	_snaps.append([sent, s])
	while _snaps.size() > 30:
		_snaps.pop_front()
	if _snaps.size() == 1:
		global_position = s[1]
		rotation.y = s[2]


func _physics_process(_delta: float) -> void:
	if _snaps.is_empty():
		return
	var rt := float(Time.get_ticks_msec()) - _offset - INTERP_MS
	var a: Array = _snaps[0]
	var b: Array = _snaps[0]
	for i in _snaps.size():
		if float(_snaps[i][0]) <= rt:
			a = _snaps[i]
			b = _snaps[mini(i + 1, _snaps.size() - 1)]
	var t := 0.0
	if b[0] != a[0]:
		t = clampf((rt - float(a[0])) / float(int(b[0]) - int(a[0])), 0.0, 1.0)
	var sa: Array = a[1]
	var sb: Array = b[1]
	global_position = (sa[1] as Vector3).lerp(sb[1], t)
	rotation.y = lerp_angle(float(sa[2]), float(sb[2]), t)
	var w := String(sa[4])
	if w != _weapon:
		_weapon = w
		if w != "":
			if not _weapon_vis.owned.has(w):
				_weapon_vis.give(w, 0)
			_weapon_vis.equip(w)
	var dead := bool(sa[5])
	if dead != _dead:
		_dead = dead
		_col.disabled = dead
		if dead:
			model.start_ragdoll(Vector3.UP * 8.0)
	if not _dead:
		model.net_apply(sa[3])


func is_dead() -> bool:
	return _dead


func is_head_hit(p: Vector3) -> bool:
	return not _dead and p.y > global_position.y + 1.52


func on_hit(damage: float, source: Node, _pos: Vector3, _dir: Vector3) -> void:
	if _dead:
		return
	if source is Player or (source is Vehicle and (source as Vehicle).driver is Player) \
			or (source is Projectile and (source as Projectile).shooter is Player):
		Net.send_entity_hit(eid, damage)


func on_vehicle_impact(v: Node3D, rel_speed: float) -> void:
	if not _dead and rel_speed > 4.0 and v.get("driver") is Player:
		Net.send_entity_hit(eid, (rel_speed - 3.0) * 9.0)
