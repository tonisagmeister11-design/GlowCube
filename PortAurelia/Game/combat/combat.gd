class_name Combat
extends RefCounted
## Shared hit resolution for bullets, melee, explosions and vehicle impacts.
## A target is anything with an `on_hit(damage, source, pos, dir)` method or a
## Health child somewhere up its node hierarchy.

const HEADSHOT_MULT := 2.5


static func find_hittable(n: Object) -> Node:
	var cur := n as Node
	var depth := 0
	while cur and depth < 8:
		if cur.has_method("on_hit"):
			return cur
		var h := cur.get_node_or_null("Health")
		if h is Health:
			return cur
		cur = cur.get_parent()
		depth += 1
	return null


static func apply_damage(collider: Object, damage: float, source: Node, pos: Vector3, dir: Vector3) -> bool:
	var t := find_hittable(collider)
	if t == null:
		return false
	# headshots on characters
	if t.has_method("is_head_hit") and t.call("is_head_hit", pos):
		damage *= HEADSHOT_MULT
	if t.has_method("on_hit"):
		t.call("on_hit", damage, source, pos, dir)
	else:
		var h: Health = t.get_node("Health")
		h.take_damage(damage, source, pos, dir)
	return true


static func surface_of(collider: Object) -> String:
	if collider == null:
		return "concrete"
	if collider.has_meta("surface"):
		return String(collider.get_meta("surface"))
	var t := find_hittable(collider)
	if t:
		if t.is_in_group("vehicles"):
			return "metal"
		return "flesh"
	return "concrete"


static func impact_fx(pos: Vector3, normal: Vector3, surface: String) -> void:
	match surface:
		"flesh":
			VFX.burst(pos, normal, "blood")
		"metal", "building":
			VFX.burst(pos, normal, "sparks")
			VFX.bullet_hole(pos, normal)
		"grass", "sand", "dirt":
			VFX.burst(pos, normal, "dust")
		"water":
			VFX.burst(pos, Vector3.UP, "splash")
		_:
			VFX.burst(pos, normal, "dust")
			VFX.bullet_hole(pos, normal)
	Events.bullet_impact.emit(pos, normal, surface)


## Radial damage (explosions).
static func explode(world: World3D, pos: Vector3, radius: float, damage: float, source: Node) -> void:
	VFX.explosion(pos, radius)
	AudioManager.play_3d("explosion", pos, 6.0)
	Events.explosion.emit(pos, radius, source)
	var q := PhysicsShapeQueryParameters3D.new()
	var s := SphereShape3D.new()
	s.radius = radius
	q.shape = s
	q.transform = Transform3D(Basis.IDENTITY, pos)
	q.collision_mask = (1 << 1) | (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5)
	var hits := world.direct_space_state.intersect_shape(q, 64)
	var done := {}
	for h in hits:
		var c: Object = h["collider"]
		var t := find_hittable(c)
		var key: Object = t if t else c
		if done.has(key):
			continue
		done[key] = true
		var cp: Vector3 = (c as Node3D).global_position if c is Node3D else pos
		var d := cp.distance_to(pos)
		var f := clampf(1.0 - d / radius, 0.0, 1.0)
		if t:
			apply_damage(c, damage * f, source, cp, (cp - pos).normalized())
		if c is RigidBody3D:
			(c as RigidBody3D).apply_central_impulse((cp - pos).normalized() * 900.0 * f + Vector3.UP * 500.0 * f)
