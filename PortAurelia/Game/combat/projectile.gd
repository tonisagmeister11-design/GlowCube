class_name Projectile
extends Node3D
## Rockets (straight, fast, smoke trail) and 40 mm grenades (ballistic arc). Swept ray
## collision each physics frame; on impact a lethal explosion: people inside the core
## radius die instantly (police included) and vehicles blow up and are thrown in the air.

var kind := "rocket"
var shooter: Node3D
var velocity := Vector3.ZERO
var damage := 900.0
var blast := 7.5
var _life := 6.0
var _trail_t := 0.0
var _exclude: Array[RID] = []


static func launch(from_body: Node3D, pos: Vector3, dir: Vector3, k: String, dmg: float, radius: float) -> Projectile:
	var p := Projectile.new()
	p.kind = k
	p.shooter = from_body
	p.damage = dmg
	p.blast = radius
	p.velocity = dir * (75.0 if k == "rocket" else 34.0)
	if k == "grenade":
		p.velocity += Vector3.UP * 2.5
	if from_body is CollisionObject3D:
		p._exclude.append((from_body as CollisionObject3D).get_rid())
	var veh = from_body.get("vehicle")
	if veh is CollisionObject3D:
		p._exclude.append((veh as CollisionObject3D).get_rid())
	var parent := GameWorld.instance if GameWorld.instance else from_body.get_tree().current_scene
	parent.add_child(p)
	p.global_position = pos
	p.look_at(pos + dir, Vector3.UP if absf(dir.y) < 0.99 else Vector3.FORWARD)
	return p


func _ready() -> void:
	var mi := MeshInstance3D.new()
	var mat := StandardMaterial3D.new()
	if kind == "rocket":
		var cm := CylinderMesh.new()
		cm.top_radius = 0.0
		cm.bottom_radius = 0.055
		cm.height = 0.5
		mi.mesh = cm
		mi.rotation.x = -PI / 2
		mat.albedo_color = Color(0.55, 0.5, 0.3)
	else:
		var sm := SphereMesh.new()
		sm.radius = 0.045
		sm.height = 0.1
		mi.mesh = sm
		mat.albedo_color = Color(0.3, 0.35, 0.2)
	mi.material_override = mat
	add_child(mi)
	if kind == "rocket":
		var flame := OmniLight3D.new()
		flame.light_color = Color(1.0, 0.6, 0.25)
		flame.light_energy = 3.0
		flame.omni_range = 5.0
		flame.position = Vector3(0, 0, 0.35)
		flame.shadow_enabled = false
		add_child(flame)


func _physics_process(delta: float) -> void:
	_life -= delta
	if kind == "grenade":
		velocity += Vector3.DOWN * 9.8 * delta
	var from := global_position
	var to := from + velocity * delta
	var q := PhysicsRayQueryParameters3D.create(from, to)
	q.exclude = _exclude
	q.collision_mask = 1 | (1 << 1) | (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5)
	var r := get_world_3d().direct_space_state.intersect_ray(q)
	if not r.is_empty():
		_detonate(r["position"] - velocity.normalized() * 0.2)
		return
	global_position = to
	if velocity.length() > 0.1:
		look_at(to + velocity, Vector3.UP if absf(velocity.normalized().y) < 0.99 else Vector3.FORWARD)
	_trail_t -= delta
	if kind == "rocket" and _trail_t <= 0.0 and not Settings.ultra():
		_trail_t = 0.035
		VFX.burst(global_position + global_basis.z * 0.3, global_basis.z, "smoke")
	var sea := GameWorld.instance.sea_level() if GameWorld.instance else -100.0
	if _life <= 0.0 or global_position.y < sea - 0.5:
		_detonate(global_position)


func _detonate(pos: Vector3) -> void:
	set_physics_process(false)
	Combat.explode(get_world_3d(), pos, blast, damage, shooter, true)
	queue_free()
