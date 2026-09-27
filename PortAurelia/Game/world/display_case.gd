class_name DisplayCase
extends StaticBody3D
## Jewelry display case: dark wooden plinth with a glass box full of gold and gems.
## Shooting or hitting the glass smashes it and scatters the jewellery as pickups.

signal smashed(c: Node)

var value := 2500
var broken := false
var _glass: MeshInstance3D
var _items: Node3D
var _hp := 25.0

static var _mats := {}


static func _mat(key: String) -> StandardMaterial3D:
	if _mats.has(key):
		return _mats[key]
	var m := StandardMaterial3D.new()
	match key:
		"wood":
			m.albedo_color = Color(0.1, 0.07, 0.07)
			m.roughness = 0.35
		"glass":
			m.albedo_color = Color(0.85, 0.92, 0.95, 0.18)
			m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
			m.roughness = 0.02
			m.metallic_specular = 0.9
		"velvet":
			m.albedo_color = Color(0.3, 0.03, 0.08)
			m.roughness = 0.9
		"gold":
			m.albedo_color = Color(1.0, 0.78, 0.3)
			m.metallic = 1.0
			m.roughness = 0.18
		"gem":
			m.albedo_color = Color(0.75, 0.9, 1.0)
			m.emission_enabled = true
			m.emission = Color(0.6, 0.85, 1.0)
			m.emission_energy_multiplier = 0.8
			m.roughness = 0.05
	_mats[key] = m
	return m


func _box(size: Vector3, pos: Vector3, mat: String, parent: Node3D = self) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.material_override = _mat(mat)
	mi.position = pos
	parent.add_child(mi)
	return mi


func _ready() -> void:
	collision_layer = 1
	set_meta("surface", "wood")
	var cs := CollisionShape3D.new()
	var sh := BoxShape3D.new()
	sh.size = Vector3(1.6, 1.25, 0.8)
	cs.shape = sh
	cs.position.y = 0.625
	add_child(cs)
	_box(Vector3(1.6, 0.9, 0.8), Vector3(0, 0.45, 0), "wood")
	_box(Vector3(1.5, 0.02, 0.7), Vector3(0, 0.91, 0), "velvet")
	_items = Node3D.new()
	add_child(_items)
	for i in 6:
		var x := -0.55 + i * 0.22
		var ring := MeshInstance3D.new()
		var tm := TorusMesh.new()
		tm.inner_radius = 0.035
		tm.outer_radius = 0.05
		ring.mesh = tm
		ring.material_override = _mat("gold")
		ring.position = Vector3(x, 0.95, -0.12)
		ring.rotation.x = PI / 2
		_items.add_child(ring)
		var gem := MeshInstance3D.new()
		var sm := SphereMesh.new()
		sm.radius = 0.025
		sm.height = 0.04
		sm.radial_segments = 6
		sm.rings = 3
		gem.mesh = sm
		gem.material_override = _mat("gem")
		gem.position = Vector3(x, 1.0, -0.12)
		_items.add_child(gem)
		_box(Vector3(0.12, 0.015, 0.12), Vector3(x, 0.93, 0.15), "gold", _items)
	_glass = _box(Vector3(1.5, 0.34, 0.7), Vector3(0, 1.08, 0), "glass")
	var l := OmniLight3D.new()
	l.position = Vector3(0, 1.4, 0)
	l.omni_range = 1.4
	l.light_energy = 0.6
	l.light_color = Color(1.0, 0.95, 0.85)
	l.shadow_enabled = false
	add_child(l)


func on_hit(damage: float, _source: Node, _pos: Vector3, _dir: Vector3) -> void:
	if broken:
		return
	_hp -= damage
	if _hp <= 0.0:
		smash()


func smash() -> void:
	if broken:
		return
	broken = true
	_glass.visible = false
	_items.visible = false
	AudioManager.play_3d("glass_break", global_position + Vector3.UP, 2.0)
	VFX.burst(global_position + Vector3.UP * 1.1, Vector3.UP, "glass")
	if value > 0:
		var parts := 3
		for i in parts:
			var off := global_basis * Vector3(-0.45 + i * 0.45, 0.0, 0.75)
			Pickup.spawn(get_parent(), global_position + off, "jewels", value / parts)
	smashed.emit(self)
