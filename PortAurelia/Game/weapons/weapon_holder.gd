class_name WeaponHolder
extends Node
## Weapon inventory and firing for the player and armed NPCs.
##
## Player: aims with the camera ray, fires on "fire", reloads on "reload",
## switches with 1-9 / mouse wheel / weapon wheel. NPCs call fire_at().

signal weapon_changed(id: String)
signal fired(id: String)

const WEAPONS_SCENE := "res://assets/generated/weapons/weapons.glb"
const META_PATH := "res://assets/generated/weapons/weapons_meta.json"

static var _scene: PackedScene
static var _meta := {}

var owner_body: Node3D
var model: CharacterModel
var owned := {"unarmed": {"clip": 0, "reserve": 0}}
var current := "unarmed"
var is_player := true
var accuracy := 1.0          # NPC skill (1 = perfect)
var _cooldown := 0.0
var _reloading := 0.0
var _fire_hold := 0.0
var _visual: Node3D
var _attach: BoneAttachment3D
var _spread_bloom := 0.0
var _spin := 0.0             # minigun barrel spin-up (0..spinup seconds)


func setup(body: Node3D, m: CharacterModel, player := true) -> void:
	owner_body = body
	model = m
	is_player = player
	if _scene == null and ResourceLoader.exists(WEAPONS_SCENE):
		_scene = load(WEAPONS_SCENE)
		var j = WorldData._read_json(META_PATH)
		if j:
			_meta = j


func _process(delta: float) -> void:
	_cooldown = maxf(0.0, _cooldown - delta)
	_spread_bloom = maxf(0.0, _spread_bloom - delta * 3.0)
	if _reloading > 0.0:
		_reloading -= delta
		if _reloading <= 0.0:
			_finish_reload()
	_fire_hold = maxf(0.0, _fire_hold - delta)


# ------------------------------------------------------------------ inventory
func give(id: String, ammo := -1) -> void:
	var d := WeaponData.get_def(id)
	if not owned.has(id):
		owned[id] = {"clip": int(d.get("mag", 0)), "reserve": 0}
	if ammo < 0:
		ammo = int(d.get("mag", 0)) * 3
	add_ammo(id, ammo)
	if is_player:
		Events.notify.emit("%s erhalten" % d["name"], 2.0)


func add_ammo(id: String, amount: int) -> void:
	if not owned.has(id):
		return
	var d := WeaponData.get_def(id)
	var mx := int(d.get("reserve", 0))
	owned[id]["reserve"] = mini(mx, int(owned[id]["reserve"]) + amount)
	_emit_ammo()


func has_weapon(id: String) -> bool:
	return owned.has(id)


func current_id() -> String:
	return current


func current_is_ranged() -> bool:
	return WeaponData.is_ranged(current)


## True if any owned weapon is a firearm (not necessarily the equipped one).
func has_ranged() -> bool:
	for id in owned:
		if WeaponData.is_ranged(id):
			return true
	return false


func upper_anim() -> String:
	return String(WeaponData.get_def(current).get("anim", ""))


func is_firing() -> bool:
	return _fire_hold > 0.0


func is_reloading() -> bool:
	return _reloading > 0.0


func equip(id: String) -> void:
	if not owned.has(id) or id == current:
		return
	current = id
	_reloading = 0.0
	_update_visual()
	weapon_changed.emit(id)
	if is_player:
		Events.weapon_changed.emit(id)
		_emit_ammo()


func holster() -> void:
	if current != "unarmed":
		equip("unarmed")


func cycle(step: int) -> void:
	var ids := owned_sorted()
	if ids.is_empty():
		return
	var i := ids.find(current)
	equip(ids[(i + step + ids.size()) % ids.size()])


func owned_sorted() -> Array:
	var ids := owned.keys()
	ids.sort_custom(func(a, b): return int(WeaponData.get_def(a)["slot"]) * 10 + WeaponData.WEAPONS.keys().find(a) \
		< int(WeaponData.get_def(b)["slot"]) * 10 + WeaponData.WEAPONS.keys().find(b))
	return ids


func _update_visual() -> void:
	if _attach:
		_attach.queue_free()
		_attach = null
		_visual = null
	var d := WeaponData.get_def(current)
	var mname: String = d.get("model", "")
	if mname == "" or _scene == null or model == null or model.skeleton == null:
		return
	var inst := _scene.instantiate()
	var mesh_node := inst.find_child(mname, true, false) as MeshInstance3D
	if mesh_node == null:
		inst.free()
		return
	var mi := MeshInstance3D.new()
	mi.mesh = mesh_node.mesh
	inst.free()
	# the hand bone points along the arm (+Y); the weapon barrel should point forward from the fist
	var off := Transform3D(Basis(Vector3(0, 0, 1), deg_to_rad(90)) * Basis(Vector3(1, 0, 0), deg_to_rad(-90)) * Basis(Vector3(0, 0, 1), PI), Vector3(0, 0.11, -0.07))
	_attach = model.attach(mi, "Hand.R", off)
	_visual = mi


func muzzle_position() -> Vector3:
	if _visual:
		var m: Array = _meta.get(WeaponData.get_def(current).get("model", ""), {}).get("muzzle", [0, 0, -0.3])
		return _visual.global_transform * Vector3(m[0], m[1], m[2])
	return owner_body.global_position + Vector3(0, 1.4, 0) - owner_body.global_basis.z * 0.5


# ------------------------------------------------------------------ player input
func handle_input(aiming: bool) -> void:
	for i in range(1, 10):
		if Input.is_action_just_pressed("weapon_%d" % i):
			for id in owned_sorted():
				if int(WeaponData.get_def(id)["slot"]) == i:
					equip(id)
					break
	# while looking through a scope the mouse wheel zooms instead of switching weapons
	var scoped := aiming and bool(WeaponData.get_def(current).get("scope", false))
	if Input.is_action_just_pressed("weapon_next") and not scoped:
		cycle(1)
	elif Input.is_action_just_pressed("weapon_prev") and not scoped:
		cycle(-1)
	if Input.is_action_just_pressed("reload"):
		reload()
	var d := WeaponData.get_def(current)
	if d["kind"] == "melee":
		if Input.is_action_just_pressed("fire"):
			melee()
		return
	var want := Input.is_action_pressed("fire") if d.get("auto", false) else Input.is_action_just_pressed("fire")
	if d.has("spinup"):
		# the minigun has to spin up before it fires
		var dt := get_process_delta_time()
		_spin = clampf(_spin + (dt if Input.is_action_pressed("fire") or aiming else -dt * 2.0), 0.0, float(d["spinup"]))
		if want and _spin < float(d["spinup"]):
			want = false
			_fire_hold = 0.35
	if want:
		var player := owner_body as Player
		var cam := player.cam if player else null
		if cam:
			var hit := cam.aim_hit(float(d.get("range", 100.0)), [owner_body.get_rid()])
			fire_at(hit["position"], aiming)


const DRIVEBY_KINDS := ["pistol", "smg", "rifle", "shotgun"]


func _driveby_weapons() -> Array:
	return owned_sorted().filter(func(id): return String(WeaponData.get_def(id)["kind"]) in DRIVEBY_KINDS)


func handle_vehicle_input() -> void:
	# in the car: number keys / mouse wheel pick a drive-by weapon, R reloads it, aim + fire shoots
	var list := _driveby_weapons()
	if list.is_empty():
		return
	var pick := ""
	for i in range(1, 10):
		if Input.is_action_just_pressed("weapon_%d" % i):
			for id in list:
				if int(WeaponData.get_def(id)["slot"]) == i:
					pick = id
					break
	var step := 0
	if Input.is_action_just_pressed("weapon_next"):
		step = 1
	elif Input.is_action_just_pressed("weapon_prev"):
		step = -1
	if step != 0:
		var i := list.find(current)
		pick = list[posmod(i + step, list.size())] if i >= 0 else list[0]
	if pick != "" and pick != current:
		equip(pick)
		Events.notify.emit("Waffe: %s" % String(WeaponData.get_def(pick)["name"]), 1.5)
	var usable := current in list
	if Input.is_action_just_pressed("reload"):
		if not usable:
			equip(list[0])
			usable = true
		reload()
	if not Input.is_action_pressed("aim"):
		return
	if not usable:
		equip(list[0])
	var d := WeaponData.get_def(current)
	if not WeaponData.is_ranged(current):
		return
	var want := Input.is_action_pressed("fire") if d.get("auto", false) else Input.is_action_just_pressed("fire")
	if want:
		var player := owner_body as Player
		if player and player.cam:
			var ex := [owner_body.get_rid()]
			if player.vehicle is CollisionObject3D:
				ex.append((player.vehicle as CollisionObject3D).get_rid())
			var hit := player.cam.aim_hit(float(d.get("range", 100.0)), ex)
			fire_at(hit["position"], true)


# ------------------------------------------------------------------ firing
func fire_at(target: Vector3, aimed := true) -> bool:
	var d := WeaponData.get_def(current)
	if _cooldown > 0.0 or _reloading > 0.0 or not WeaponData.is_ranged(current):
		return false
	var st: Dictionary = owned[current]
	if int(st["clip"]) <= 0:
		reload()
		if is_player:
			AudioManager.play_3d("dry_fire", muzzle_position(), -6.0)
		return false
	st["clip"] = int(st["clip"]) - 1
	_cooldown = float(d["rate"])
	_fire_hold = 0.35
	var from := muzzle_position()
	var spread := float(d.get("aim_spread" if aimed else "spread", 1.0)) + _spread_bloom
	if not is_player:
		spread += (1.0 - accuracy) * 6.0
	var pellets := int(d.get("pellets", 1))
	if not is_player and _aims_at_player(target):
		# NPCs and cops don't hit every shot: many bullets go past the player
		var miss := clampf(0.22 + (1.0 - accuracy) * 0.4 + from.distance_to(target) / 150.0, 0.22, 0.7)
		if randf() < miss:
			var side := (target - from).cross(Vector3.UP).normalized()
			target += side * randf_range(0.7, 2.2) * (1.0 if randf() < 0.5 else -1.0) + Vector3.UP * randf_range(-0.6, 1.2)
	var base_dir := (target - from).normalized()
	if d.has("projectile"):
		Projectile.launch(owner_body, from, _spread_dir(base_dir, spread), String(d["projectile"]), float(d["damage"]),
			float(d.get("blast", 6.0)))
		pellets = 0
	var space := owner_body.get_world_3d().direct_space_state
	var ex := [owner_body.get_rid()]
	var vehicle = owner_body.get("vehicle")
	if vehicle is CollisionObject3D:
		ex.append((vehicle as CollisionObject3D).get_rid())
	for p in pellets:
		var dir := _spread_dir(base_dir, spread)
		var to := from + dir * float(d.get("range", 100.0))
		var q := PhysicsRayQueryParameters3D.create(from, to)
		q.exclude = ex
		q.collision_mask = 1 | (1 << 1) | (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5)
		var r := space.intersect_ray(q)
		var end := to
		if not r.is_empty():
			end = r["position"]
			var c: Object = r["collider"]
			var dmg := float(d["damage"]) * (1.0 if is_player else 0.4)
			Combat.apply_damage(c, dmg, owner_body, end, dir)
			Combat.impact_fx(end, r["normal"], Combat.surface_of(c))
			if c is RigidBody3D:
				(c as RigidBody3D).apply_impulse(dir * float(d["damage"]) * 0.6, end - (c as RigidBody3D).global_position)
		if p == 0 or randf() < 0.3:
			VFX.tracer(from, end)
		if p == 0 and is_player and Net.is_online():
			Net.local_shot(from, end, current)
		elif p == 0 and Net.is_host() and owner_body.has_meta("net_eid"):
			Net.host_entity_shot(int(owner_body.get_meta("net_eid")), from, end, current)
	VFX.muzzle_flash(from, base_dir, 1.4 if d["kind"] in ["shotgun", "sniper", "rifle", "launcher", "heavy"] else 1.0)
	AudioManager.play_weapon(String(d.get("sound", "pistol")), from, is_player)
	Events.gunshot.emit(from, owner_body, 1.0)
	_spread_bloom = minf(_spread_bloom + float(d.get("recoil", 1.0)) * 0.35, 4.0)
	if model:
		model.play_oneshot("shoot_rifle" if d["anim"] == "aim_rifle" else "shoot_pistol", 0.02)
	if is_player:
		var player := owner_body as Player
		if player and player.cam:
			player.cam.pitch = minf(player.cam.pitch + deg_to_rad(float(d.get("recoil", 1.0))) * 0.35, CameraRig.PITCH_MAX)
			player.cam.yaw += deg_to_rad(randf_range(-1.0, 1.0) * float(d.get("recoil", 1.0)) * 0.12)
			player.cam.add_shake(float(d.get("recoil", 1.0)) * 0.05)
			InputSetup.rumble(0.3, clampf(float(d.get("recoil", 1.0)) * 0.1, 0.05, 0.7), 0.08)
		_emit_ammo()
	fired.emit(current)
	# empty magazine: reload on its own
	if int(st["clip"]) <= 0:
		reload()
	return true


func _aims_at_player(target: Vector3) -> bool:
	var pl := owner_body.get_tree().get_first_node_in_group("player") as Node3D
	if pl == null:
		return false
	var pv = pl.get("vehicle")
	var ref: Vector3 = (pv as Node3D).global_position if pv is Node3D else pl.global_position
	return target.distance_to(ref) < 4.0


func _spread_dir(dir: Vector3, deg: float) -> Vector3:
	if deg <= 0.001:
		return dir
	var a := deg_to_rad(deg) * sqrt(randf())
	var t := randf() * TAU
	var side := dir.cross(Vector3.UP).normalized()
	if side.length() < 0.1:
		side = Vector3.RIGHT
	var up := side.cross(dir).normalized()
	return (dir * cos(a) + (side * cos(t) + up * sin(t)) * sin(a)).normalized()


func reload() -> void:
	if not WeaponData.is_ranged(current) or _reloading > 0.0:
		return
	var d := WeaponData.get_def(current)
	var st: Dictionary = owned[current]
	if int(st["clip"]) >= int(d["mag"]) or (int(st["reserve"]) <= 0 and not d.get("infinite", false)):
		return
	_reloading = float(d["reload"])
	if model:
		model.play_oneshot("reload_rifle" if d["anim"] == "aim_rifle" else "reload_pistol")
	AudioManager.play_3d("reload", owner_body.global_position + Vector3.UP, -4.0)


func _finish_reload() -> void:
	var d := WeaponData.get_def(current)
	var st: Dictionary = owned[current]
	var need := int(d["mag"]) - int(st["clip"])
	if d.get("infinite", false):
		st["clip"] = int(d["mag"])     # endless ammo (rocket launcher)
		_emit_ammo()
		return
	var take := mini(need, int(st["reserve"]))
	st["clip"] = int(st["clip"]) + take
	st["reserve"] = int(st["reserve"]) - take
	_emit_ammo()


func melee() -> void:
	if _cooldown > 0.0:
		return
	var d := WeaponData.get_def(current)
	_cooldown = float(d["rate"])
	var anim: String = d.get("anim", "punch")
	if current == "unarmed" and randf() < 0.3:
		anim = "kick"
	if model:
		model.play_oneshot(anim)
	var tree := owner_body.get_tree()
	await tree.create_timer(0.14).timeout
	if not is_instance_valid(owner_body):
		return
	var fwd := -owner_body.global_basis.z
	var center := owner_body.global_position + Vector3.UP * 1.1 + fwd * float(d["range"]) * 0.6
	var q := PhysicsShapeQueryParameters3D.new()
	var s := SphereShape3D.new()
	s.radius = 0.7
	q.shape = s
	q.transform = Transform3D(Basis.IDENTITY, center)
	q.collision_mask = (1 << 1) | (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5)
	q.exclude = [owner_body.get_rid()]
	var hits := owner_body.get_world_3d().direct_space_state.intersect_shape(q, 8)
	var done := {}
	for h in hits:
		var t := Combat.find_hittable(h["collider"])
		if t == null or done.has(t):
			continue
		done[t] = true
		Combat.apply_damage(h["collider"], float(d["damage"]), owner_body, center, fwd)
		AudioManager.play_3d("punch_hit", center, -2.0)
	if hits.is_empty():
		AudioManager.play_3d("swing", center, -8.0)
	if not done.is_empty() and is_player:
		Events.crime_committed.emit("assault", center, 1, owner_body)


func _emit_ammo() -> void:
	if not is_player:
		return
	var st: Dictionary = owned.get(current, {"clip": 0, "reserve": 0})
	Events.ammo_changed.emit(current, int(st["clip"]), int(st["reserve"]))


func serialize() -> Dictionary:
	return {"owned": owned.duplicate(true), "current": current}


func deserialize(d: Dictionary) -> void:
	owned = d.get("owned", owned)
	var c: String = d.get("current", "unarmed")
	current = "unarmed"
	equip(c)
