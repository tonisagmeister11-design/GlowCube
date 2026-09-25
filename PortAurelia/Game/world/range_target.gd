class_name RangeTarget
extends StaticBody3D
## Pop-up target board of the gun shop's shooting range. Lights up when active;
## bullet hits are reported to the InteriorManager.

var manager: Node
var lit := false
var _glow: MeshInstance3D


func _ready() -> void:
	collision_layer = 1
	collision_mask = 0
	var cs := CollisionShape3D.new()
	var b := BoxShape3D.new()
	b.size = Vector3(0.9, 1.3, 0.1)
	cs.shape = b
	add_child(cs)
	_glow = MeshInstance3D.new()
	var q := QuadMesh.new()
	q.size = Vector2(0.62, 0.62)
	var m := StandardMaterial3D.new()
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	m.albedo_color = Color(1.0, 0.85, 0.2)
	q.material = m
	_glow.mesh = q
	_glow.position = Vector3(0, 0, 0.07)
	_glow.visible = false
	add_child(_glow)


func set_lit(on: bool) -> void:
	lit = on
	_glow.visible = on


func on_hit(_damage: float, _source: Node, _pos: Vector3, _dir: Vector3) -> void:
	if manager:
		manager.call("range_hit", self)
