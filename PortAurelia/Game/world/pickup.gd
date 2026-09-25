class_name Pickup
extends Area3D
## Collectable item lying in the world: money, weapon (with ammo), health, armor.
## Dropped by dead NPCs and placed by missions/shops. Collected by the player on contact.

var kind := "money"          # money | weapon | health | armor
var amount := 50
var weapon_id := ""
var lifetime := 90.0
var _visual: Node3D
var _t := 0.0

static var _weapons_scene: PackedScene


static func spawn(parent: Node, pos: Vector3, k: String, amt: int, weapon := "") -> Pickup:
	var p := Pickup.new()
	p.kind = k
	p.amount = amt
	p.weapon_id = weapon
	parent.add_child(p)
	p.global_position = pos + Vector3.UP * 0.1
	return p


func _ready() -> void:
	collision_layer = 1 << 6
	collision_mask = 1 << 1
	monitoring = true
	var cs := CollisionShape3D.new()
	var sp := SphereShape3D.new()
	sp.radius = 0.8
	cs.shape = sp
	cs.position.y = 0.5
	add_child(cs)
	body_entered.connect(_on_body)
	_visual = Node3D.new()
	add_child(_visual)
	_visual.position.y = 0.35
	match kind:
		"weapon":
			_build_weapon()
		"health":
			_build_box(Color(0.9, 0.95, 0.9), Vector3(0.35, 0.25, 0.25), Color(0.9, 0.1, 0.1))
		"armor":
			_build_box(Color(0.15, 0.25, 0.5), Vector3(0.35, 0.4, 0.1), Color(0.3, 0.5, 1.0))
		_:
			_build_box(Color(0.25, 0.55, 0.25), Vector3(0.22, 0.06, 0.12), Color(0.3, 1.0, 0.4))
	var glow := OmniLight3D.new()
	glow.light_color = Color(0.4, 1.0, 0.5) if kind == "money" else Color(1.0, 0.9, 0.5)
	glow.light_energy = 0.6
	glow.omni_range = 1.6
	glow.shadow_enabled = false
	_visual.add_child(glow)


func _build_box(col: Color, size: Vector3, emit: Color) -> void:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	var m := StandardMaterial3D.new()
	m.albedo_color = col
	m.emission_enabled = true
	m.emission = emit
	m.emission_energy_multiplier = 0.35
	bm.material = m
	mi.mesh = bm
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	_visual.add_child(mi)


func _build_weapon() -> void:
	if _weapons_scene == null:
		_weapons_scene = load(WeaponHolder.WEAPONS_SCENE)
	var mname: String = WeaponData.get_def(weapon_id).get("model", "")
	if _weapons_scene and mname != "":
		var inst := _weapons_scene.instantiate()
		var src := inst.find_child(mname, true, false) as MeshInstance3D
		if src:
			var mi := MeshInstance3D.new()
			mi.mesh = src.mesh
			_visual.add_child(mi)
		inst.free()
	if _visual.get_child_count() == 0:
		_build_box(Color(0.2, 0.2, 0.2), Vector3(0.4, 0.12, 0.08), Color(1, 0.8, 0.3))


func _process(delta: float) -> void:
	_t += delta
	_visual.rotation.y = _t * 2.0
	_visual.position.y = 0.35 + sin(_t * 3.0) * 0.06
	lifetime -= delta
	if lifetime <= 0.0:
		queue_free()


func _on_body(b: Node) -> void:
	if not b is Player:
		return
	var p := b as Player
	if p.state == Player.State.DEAD:
		return
	match kind:
		"money":
			Game.player_data.add_money(amount)
			AudioManager.play_ui("money", -4.0)
			Events.notify.emit("+$%d" % amount, 1.5)
		"weapon":
			if p.weapons.has_weapon(weapon_id):
				p.weapons.add_ammo(weapon_id, amount)
			else:
				p.weapons.give(weapon_id, amount)
			AudioManager.play_ui("select", -4.0)
			Events.notify.emit("%s aufgenommen" % WeaponData.get_def(weapon_id)["name"], 2.0)
		"health":
			if p.health.health >= p.health.max_health:
				return
			p.health.heal(float(amount))
			AudioManager.play_ui("select", -4.0)
		"armor":
			if p.health.armor >= 100.0:
				return
			p.health.add_armor(float(amount))
			AudioManager.play_ui("select", -4.0)
	queue_free()
