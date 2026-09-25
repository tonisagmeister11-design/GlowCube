class_name VFX
extends RefCounted
## Pooled visual effects: muzzle flash, tracers, bullet impacts, blood, sparks,
## smoke, explosions, fire, water splashes, tyre smoke and debris.
## All effects are built in code (no external assets) and recycled.

static var _root: Node3D
static var _pools := {}
static var _mats := {}


static func root() -> Node3D:
	if _root == null or not is_instance_valid(_root):
		var tree := Engine.get_main_loop() as SceneTree
		_root = Node3D.new()
		_root.name = "VFX"
		tree.root.add_child(_root)
		_pools.clear()
	return _root


static func _soft_texture() -> Texture2D:
	if _mats.has("soft_tex"):
		return _mats["soft_tex"]
	var img := Image.create(64, 64, false, Image.FORMAT_RGBA8)
	for y in 64:
		for x in 64:
			var d := Vector2(x - 31.5, y - 31.5).length() / 32.0
			var a := clampf(1.0 - d, 0.0, 1.0)
			img.set_pixel(x, y, Color(1, 1, 1, a * a))
	var t := ImageTexture.create_from_image(img)
	_mats["soft_tex"] = t
	return t


static func _particle_material(color: Color, additive: bool, soft := true) -> StandardMaterial3D:
	var key := "pm_%s_%s_%s" % [color.to_html(), additive, soft]
	if _mats.has(key):
		return _mats[key]
	var m := StandardMaterial3D.new()
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED if additive else BaseMaterial3D.SHADING_MODE_PER_PIXEL
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.blend_mode = BaseMaterial3D.BLEND_MODE_ADD if additive else BaseMaterial3D.BLEND_MODE_MIX
	m.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
	m.vertex_color_use_as_albedo = true
	m.albedo_color = color
	if soft:
		m.albedo_texture = _soft_texture()
	m.cull_mode = BaseMaterial3D.CULL_DISABLED
	_mats[key] = m
	return m


## Generic one-shot particle burst built with CPUParticles3D (robust on all renderers).
static func burst(pos: Vector3, normal: Vector3, kind: String) -> void:
	var p := _take(kind)
	if p == null:
		p = CPUParticles3D.new()
		p.one_shot = true
		p.emitting = false
		p.local_coords = false
		var quad := QuadMesh.new()
		p.mesh = quad
		match kind:
			"dust":
				quad.size = Vector2(0.35, 0.35)
				p.amount = 10
				p.lifetime = 0.9
				p.explosiveness = 0.95
				p.spread = 35.0
				p.initial_velocity_min = 0.6
				p.initial_velocity_max = 2.2
				p.gravity = Vector3(0, -1.0, 0)
				p.scale_amount_min = 0.5
				p.scale_amount_max = 1.4
				quad.material = _particle_material(Color(0.62, 0.58, 0.52, 0.55), false)
			"sparks":
				quad.size = Vector2(0.05, 0.05)
				p.amount = 14
				p.lifetime = 0.45
				p.explosiveness = 1.0
				p.spread = 50.0
				p.initial_velocity_min = 3.0
				p.initial_velocity_max = 8.0
				p.gravity = Vector3(0, -9.8, 0)
				quad.material = _particle_material(Color(1.0, 0.75, 0.3, 1.0), true)
			"blood":
				quad.size = Vector2(0.14, 0.14)
				p.amount = 12
				p.lifetime = 0.5
				p.explosiveness = 1.0
				p.spread = 30.0
				p.initial_velocity_min = 1.0
				p.initial_velocity_max = 3.0
				p.gravity = Vector3(0, -9.8, 0)
				quad.material = _particle_material(Color(0.45, 0.02, 0.02, 0.9), false)
			"splash":
				quad.size = Vector2(0.3, 0.3)
				p.amount = 18
				p.lifetime = 0.8
				p.explosiveness = 1.0
				p.spread = 25.0
				p.initial_velocity_min = 2.0
				p.initial_velocity_max = 5.0
				p.gravity = Vector3(0, -9.8, 0)
				quad.material = _particle_material(Color(0.85, 0.92, 1.0, 0.7), false)
			"glass":
				quad.size = Vector2(0.06, 0.06)
				p.amount = 24
				p.lifetime = 1.2
				p.explosiveness = 1.0
				p.spread = 70.0
				p.initial_velocity_min = 1.0
				p.initial_velocity_max = 4.0
				p.gravity = Vector3(0, -9.8, 0)
				quad.material = _particle_material(Color(0.8, 0.9, 0.95, 0.9), true, false)
			"smoke":
				quad.size = Vector2(1.2, 1.2)
				p.amount = 16
				p.lifetime = 2.5
				p.explosiveness = 0.3
				p.spread = 20.0
				p.direction = Vector3.UP
				p.initial_velocity_min = 0.5
				p.initial_velocity_max = 1.5
				p.gravity = Vector3(0, 0.6, 0)
				p.scale_amount_min = 0.8
				p.scale_amount_max = 2.5
				quad.material = _particle_material(Color(0.25, 0.25, 0.25, 0.5), false)
			"explosion":
				quad.size = Vector2(2.2, 2.2)
				p.amount = 40
				p.lifetime = 1.1
				p.explosiveness = 1.0
				p.spread = 180.0
				p.initial_velocity_min = 4.0
				p.initial_velocity_max = 12.0
				p.gravity = Vector3(0, 2.0, 0)
				p.damping_min = 6.0
				p.damping_max = 9.0
				p.scale_amount_min = 1.0
				p.scale_amount_max = 3.0
				quad.material = _particle_material(Color(1.0, 0.55, 0.15, 1.0), true)
			"tire_smoke":
				quad.size = Vector2(1.0, 1.0)
				p.amount = 8
				p.lifetime = 1.8
				p.explosiveness = 0.0
				p.spread = 30.0
				p.initial_velocity_min = 0.2
				p.initial_velocity_max = 1.0
				p.gravity = Vector3(0, 0.4, 0)
				p.scale_amount_min = 1.0
				p.scale_amount_max = 3.0
				quad.material = _particle_material(Color(0.85, 0.85, 0.85, 0.35), false)
		root().add_child(p)
	p.global_position = pos
	if normal.length() > 0.01:
		p.direction = Vector3(0, 0, 1)
		var up := Vector3.UP if absf(normal.dot(Vector3.UP)) < 0.95 else Vector3.RIGHT
		p.global_transform = Transform3D(Basis.looking_at(-normal, up), pos)
	p.restart()
	p.emitting = true
	_release_later(kind, p, p.lifetime + 0.3)


static func _take(kind: String) -> Node:
	var arr: Array = _pools.get(kind, [])
	while not arr.is_empty():
		var n = arr.pop_back()
		if is_instance_valid(n):
			return n
	return null


static func _release_later(kind: String, node: Node, t: float) -> void:
	var tree := Engine.get_main_loop() as SceneTree
	tree.create_timer(t).timeout.connect(func():
		if is_instance_valid(node):
			if not _pools.has(kind):
				_pools[kind] = []
			_pools[kind].append(node))


static func muzzle_flash(pos: Vector3, dir: Vector3, size := 1.0) -> void:
	var n: Node3D = _take("flash")
	if n == null:
		n = Node3D.new()
		var mi := MeshInstance3D.new()
		var q := QuadMesh.new()
		q.size = Vector2(0.35, 0.35)
		var m := _particle_material(Color(1.0, 0.8, 0.45, 1.0), true)
		m = m.duplicate()
		m.billboard_mode = BaseMaterial3D.BILLBOARD_ENABLED
		q.material = m
		mi.mesh = q
		mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		n.add_child(mi)
		var l := OmniLight3D.new()
		l.light_color = Color(1.0, 0.75, 0.4)
		l.light_energy = 3.0
		l.omni_range = 5.0
		l.shadow_enabled = false
		n.add_child(l)
		root().add_child(n)
	n.global_position = pos + dir * 0.05
	n.scale = Vector3.ONE * size * randf_range(0.8, 1.2)
	n.visible = true
	var tree := Engine.get_main_loop() as SceneTree
	tree.create_timer(0.05).timeout.connect(func():
		if is_instance_valid(n):
			n.visible = false
			if not _pools.has("flash"):
				_pools["flash"] = []
			_pools["flash"].append(n))


static func tracer(from: Vector3, to: Vector3) -> void:
	var mi: MeshInstance3D = _take("tracer")
	if mi == null:
		mi = MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(0.018, 0.018, 1.0)
		var m := StandardMaterial3D.new()
		m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		m.albedo_color = Color(1.0, 0.85, 0.55)
		m.emission_enabled = true
		m.emission = Color(1.0, 0.8, 0.5)
		m.emission_energy_multiplier = 4.0
		bm.material = m
		mi.mesh = bm
		mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		root().add_child(mi)
	var d := from.distance_to(to)
	if d < 0.5:
		return
	var mid := from.lerp(to, 0.5)
	var up := Vector3.UP if absf((to - from).normalized().y) < 0.99 else Vector3.RIGHT
	mi.global_transform = Transform3D(Basis.looking_at(to - from, up).scaled(Vector3(1, 1, minf(d, 40.0))), mid)
	mi.visible = true
	var tree := Engine.get_main_loop() as SceneTree
	tree.create_timer(0.04).timeout.connect(func():
		if is_instance_valid(mi):
			mi.visible = false
			if not _pools.has("tracer"):
				_pools["tracer"] = []
			_pools["tracer"].append(mi))


static func bullet_hole(pos: Vector3, normal: Vector3) -> void:
	var d: Decal = _take("hole")
	if d == null:
		d = Decal.new()
		d.size = Vector3(0.12, 0.2, 0.12)
		d.texture_albedo = _hole_texture()
		d.cull_mask = 1
		d.upper_fade = 0.1
		d.lower_fade = 0.1
		root().add_child(d)
	var up := Vector3.UP if absf(normal.dot(Vector3.UP)) < 0.95 else Vector3.RIGHT
	d.global_transform = Transform3D(Basis.looking_at(-normal, up).rotated(normal, randf() * TAU) * Basis(Vector3.RIGHT, -PI * 0.5), pos)
	d.visible = true
	_release_later("hole", d, 25.0)


static func _hole_texture() -> Texture2D:
	if _mats.has("hole"):
		return _mats["hole"]
	var img := Image.create(32, 32, false, Image.FORMAT_RGBA8)
	for y in 32:
		for x in 32:
			var r := Vector2(x - 15.5, y - 15.5).length() / 16.0
			var a := clampf(1.0 - r * 1.3, 0.0, 1.0)
			var core := 1.0 if r < 0.25 else 0.0
			img.set_pixel(x, y, Color(0.05, 0.05, 0.05, maxf(core, a * a * 0.8)))
	var t := ImageTexture.create_from_image(img)
	_mats["hole"] = t
	return t


static func explosion(pos: Vector3, radius := 6.0) -> void:
	burst(pos, Vector3.UP, "explosion")
	burst(pos + Vector3.UP, Vector3.UP, "smoke")
	burst(pos, Vector3.UP, "sparks")
	var l := OmniLight3D.new()
	l.light_color = Color(1.0, 0.6, 0.25)
	l.light_energy = 12.0
	l.omni_range = radius * 3.0
	root().add_child(l)
	l.global_position = pos + Vector3.UP
	var tw := l.create_tween()
	tw.tween_property(l, "light_energy", 0.0, 0.6)
	tw.tween_callback(l.queue_free)


## Continuous fire (burning vehicle): returns the node, caller frees it.
static func fire(parent: Node3D, offset := Vector3.ZERO) -> Node3D:
	var p := CPUParticles3D.new()
	var q := QuadMesh.new()
	q.size = Vector2(0.9, 0.9)
	q.material = _particle_material(Color(1.0, 0.5, 0.12, 1.0), true)
	p.mesh = q
	p.amount = 24
	p.lifetime = 0.8
	p.direction = Vector3.UP
	p.spread = 15.0
	p.initial_velocity_min = 1.0
	p.initial_velocity_max = 2.5
	p.gravity = Vector3(0, 2.0, 0)
	p.scale_amount_min = 0.6
	p.scale_amount_max = 1.6
	p.emission_shape = CPUParticles3D.EMISSION_SHAPE_SPHERE
	p.emission_sphere_radius = 0.6
	parent.add_child(p)
	p.position = offset
	var l := OmniLight3D.new()
	l.light_color = Color(1.0, 0.55, 0.2)
	l.light_energy = 3.0
	l.omni_range = 9.0
	p.add_child(l)
	return p


static func engine_smoke(parent: Node3D, offset := Vector3.ZERO, dark := false) -> CPUParticles3D:
	var p := CPUParticles3D.new()
	var q := QuadMesh.new()
	q.size = Vector2(0.8, 0.8)
	q.material = _particle_material(Color(0.2, 0.2, 0.2, 0.45) if dark else Color(0.8, 0.8, 0.8, 0.3), false)
	p.mesh = q
	p.amount = 14
	p.lifetime = 2.0
	p.direction = Vector3.UP
	p.spread = 12.0
	p.initial_velocity_min = 0.8
	p.initial_velocity_max = 1.6
	p.gravity = Vector3(0, 0.5, 0)
	p.scale_amount_min = 0.8
	p.scale_amount_max = 2.4
	p.local_coords = false
	parent.add_child(p)
	p.position = offset
	return p
