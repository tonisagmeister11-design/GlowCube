class_name RemotePlayer
extends CharacterBody3D
## Another player in a multiplayer session: character model with name tag, smooth movement from
## the network states, his car (a kinematic copy you can crash into), weapon in hand.
## Hitting him (bullets, fists, explosions, running him over) is reported to his game through the
## host; he applies the damage himself.

var peer_id := 0
var display_name := ""
var color := Color.WHITE
var outfit := {}
var model: CharacterModel
var car: Vehicle = null

var _target_pos := Vector3.ZERO
var _target_yaw := 0.0
var _anim := 0
var _speed := 0.0
var _weapon := ""
var _hp := 250.0
var _max_hp := 250.0
var _car_type := ""
var _car_pos := Vector3.ZERO
var _car_rot := Quaternion.IDENTITY
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


## st = Net.pack_state(): [pos, yaw, anim, speed, weapon, vehicle type, vehicle pos, vehicle rot,
## paint, health, max health, livery]
func apply_state(st: Array, snap := false) -> void:
	_target_pos = st[0]
	_target_yaw = st[1]
	_anim = int(st[2])
	_speed = float(st[3])
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
		_set_car(vt, st[8], st[11])
	_car_pos = st[6]
	_car_rot = st[7]
	if snap:
		global_position = _target_pos
		rotation.y = _target_yaw
		if car:
			car.global_transform = Transform3D(Basis(_car_rot), _car_pos)
	_update_label()


func _set_car(vt: String, paint: Color, livery: Dictionary) -> void:
	if car and is_instance_valid(car):
		car.queue_free()
	car = null
	_car_type = vt
	if vt == "":
		return
	car = Vehicle.create(vt, paint)
	car.livery = livery.duplicate()
	car.set_meta("net_proxy", true)
	car.freeze = true
	get_parent().add_child(car)
	car.global_transform = Transform3D(Basis(_car_rot), _car_pos)
	car.set_kinematic(true)


func _physics_process(delta: float) -> void:
	var k := clampf(delta * 12.0, 0.0, 1.0)
	if _car_type != "" and car and is_instance_valid(car):
		var t := Transform3D(Basis(_car_rot), _car_pos)
		if car.global_position.distance_to(_car_pos) > 25.0:
			car.global_transform = t
		else:
			car.global_transform = car.global_transform.interpolate_with(t, k)
		global_transform = car.global_transform * car.driver_seat_transform()
		_col.disabled = true
		model.play_loop("drive")
	else:
		_col.disabled = _dead
		if global_position.distance_to(_target_pos) > 20.0:
			global_position = _target_pos
		else:
			global_position = global_position.lerp(_target_pos, k)
		rotation.y = lerp_angle(rotation.y, _target_yaw, k)
		if not _dead:
			model.set_mode("ground")
			model.set_locomotion(_speed)
	var dead := _anim == 3
	if dead != _dead:
		_dead = dead
		if dead:
			model.start_ragdoll(Vector3.UP * 10.0)
		else:
			model.stop_ragdoll()


func on_fired() -> void:
	if model and _weapon != "":
		var d := WeaponData.get_def(_weapon)
		model.play_oneshot("shoot_rifle" if d.get("anim", "") == "aim_rifle" else "shoot_pistol", 0.02)


func is_head_hit(p: Vector3) -> bool:
	return not _dead and _car_type == "" and p.y > global_position.y + 1.52


## Bullets, fists and explosions. Only the local player's own attacks are reported.
func on_hit(damage: float, source: Node, _pos: Vector3, _dir: Vector3) -> void:
	if _dead or not _is_local_attacker(source):
		return
	Net.send_hit(peer_id, damage)


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
