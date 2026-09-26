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
				quad.size = Vector2(2.6, 2.6)
				p.amount = 48
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
			"sparks_big":
				quad.size = Vector2(0.07, 0.07)
				p.amount = 70
				p.lifetime = 1.3
				p.explosiveness = 1.0
				p.spread = 180.0
				p.initial_velocity_min = 6.0
				p.initial_velocity_max = 20.0
				p.gravity = Vector3(0, -9.8, 0)
				p.damping_min = 0.5
				p.damping_max = 2.0
				quad.material = _particle_material(Color(1.0, 0.7, 0.3, 1.0), true)
			"embers":
				quad.size = Vector2(0.12, 0.12)
				p.amount = 40
				p.lifetime = 3.5
				p.explosiveness = 0.85
				p.spread = 60.0
				p.initial_velocity_min = 2.0
				p.initial_velocity_max = 9.0
				p.gravity = Vector3(0, -2.5, 0)
				p.damping_min = 1.0
				p.damping_max = 3.0
				p.scale_amount_min = 0.3
				p.scale_amount_max = 1.0
				quad.material = _particle_material(Color(1.0, 0.45, 0.1, 1.0), true)
			"dust_ring":
				quad.size = Vector2(1.6, 1.6)
				p.amount = 36
				p.lifetime = 2.2
				p.explosiveness = 1.0
				p.emission_shape = CPUParticles3D.EMISSION_SHAPE_RING
				p.emission_ring_axis = Vector3(0, 0, 1)      # local Z = world up (burst faces the normal)
				p.emission_ring_radius = 1.2
				p.emission_ring_inner_radius = 0.8
				p.emission_ring_height = 0.1
				p.spread = 180.0
				p.initial_velocity_min = 0.5
				p.initial_velocity_max = 1.5
				p.radial_accel_min = 18.0
				p.radial_accel_max = 30.0
				p.damping_min = 8.0
				p.damping_max = 12.0
				p.gravity = Vector3(0, 0.3, 0)
				p.scale_amount_min = 1.0
				p.scale_amount_max = 3.0
				quad.material = _particle_material(Color(0.55, 0.5, 0.44, 0.45), false)
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


## Big explosion (vehicles, gas tanks, missions): white-hot flash, noise-displaced fireball
## that burns out into flame tongues, billowing smoke column, ground shockwave with a dust
## ring, sparks, burning debris chunks with physics, scorch mark, flickering light.
## Detail scales with the effects setting (performance mode keeps only the essentials).
static func explosion(pos: Vector3, radius := 6.0) -> void:
	var fx: int = Settings.effects()
	var s := clampf(radius / 7.0, 0.4, 2.0)
	var r := root()
	var rng := RandomNumberGenerator.new()
	rng.randomize()
	# ---------------------------------------------------------------- flash + light
	var flash := MeshInstance3D.new()
	var fm := SphereMesh.new()
	fm.radius = 1.0
	fm.height = 2.0
	fm.radial_segments = 16
	fm.rings = 8
	var flash_mat := _particle_material(Color(1.0, 0.95, 0.8, 1.0), true, false).duplicate() as StandardMaterial3D
	flash_mat.billboard_mode = BaseMaterial3D.BILLBOARD_DISABLED
	flash_mat.emission_enabled = true
	fm.material = flash_mat
	flash.mesh = fm
	flash.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	r.add_child(flash)
	flash.global_position = pos + Vector3.UP * 0.6 * s
	flash.scale = Vector3.ONE * 0.2
	var ft := flash.create_tween()
	ft.tween_property(flash, "scale", Vector3.ONE * 3.2 * s, 0.09)
	ft.parallel().tween_property(flash_mat, "albedo_color:a", 0.0, 0.18).set_delay(0.04)
	ft.tween_callback(flash.queue_free)
	var l := OmniLight3D.new()
	l.light_color = Color(1.0, 0.72, 0.4)
	l.light_energy = 32.0
	l.omni_range = radius * 5.0
	l.shadow_enabled = fx >= 2
	r.add_child(l)
	l.global_position = pos + Vector3.UP * 2.0 * s
	var lt := l.create_tween()
	lt.tween_property(l, "light_energy", 9.0, 0.25)
	lt.tween_property(l, "light_color", Color(1.0, 0.45, 0.15), 0.4)
	lt.tween_property(l, "light_energy", 0.0, 1.6)
	lt.tween_callback(l.queue_free)
	# ---------------------------------------------------------------- fireball
	var fire_mat := _shader_material("fireball")
	var n_fire := 6 if fx >= 1 else 3
	for i in n_fire:
		var fb := MeshInstance3D.new()
		var sm := SphereMesh.new()
		sm.radius = 1.0
		sm.height = 2.0
		sm.radial_segments = 32 if fx >= 2 else 16
		sm.rings = 16 if fx >= 2 else 8
		fb.mesh = sm
		fb.material_override = fire_mat
		fb.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		r.add_child(fb)
		var off := Vector3(rng.randf_range(-1, 1), rng.randf_range(0.0, 0.8), rng.randf_range(-1, 1)) * 1.3 * s
		if i == 0:
			off = Vector3(0, 0.5 * s, 0)
		fb.global_position = pos + off
		fb.scale = Vector3.ONE * 0.3 * s
		fb.set_instance_shader_parameter("seed", rng.randf() * 50.0)
		fb.set_instance_shader_parameter("age", 0.0)
		var size := rng.randf_range(2.2, 3.4) * s * (1.25 if i == 0 else 1.0)
		var life := rng.randf_range(1.1, 1.7)
		var tw := fb.create_tween()
		tw.set_parallel(true)
		tw.tween_property(fb, "scale", Vector3.ONE * size, 0.35).set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_EXPO)
		tw.tween_property(fb, "global_position", fb.global_position + Vector3.UP * (1.5 + rng.randf() * 1.5) * s, life)\
			.set_ease(Tween.EASE_OUT)
		tw.tween_method(func(v: float): fb.set_instance_shader_parameter("age", v), 0.0, 1.0, life)
		tw.chain().tween_callback(fb.queue_free)
	# ---------------------------------------------------------------- smoke column (lit puffs)
	var smoke_mat := _shader_material("smoke_puff")
	var n_smoke: int = [4, 8, 12][clampi(fx, 0, 2)]
	for i in n_smoke:
		var sp := MeshInstance3D.new()
		var sm2 := SphereMesh.new()
		sm2.radius = 1.0
		sm2.height = 2.0
		sm2.radial_segments = 20 if fx >= 2 else 12
		sm2.rings = 10 if fx >= 2 else 6
		sp.mesh = sm2
		sp.material_override = smoke_mat
		sp.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		r.add_child(sp)
		var k := float(i) / maxf(1.0, n_smoke - 1.0)
		sp.global_position = pos + Vector3(rng.randf_range(-1, 1) * 1.2, 0.8 + k * 1.5, rng.randf_range(-1, 1) * 1.2) * s
		sp.scale = Vector3.ONE * 0.5 * s
		sp.set_instance_shader_parameter("seed", rng.randf() * 50.0)
		sp.set_instance_shader_parameter("age", 1.0)
		sp.set_instance_shader_parameter("glow", 1.0)
		var delay := 0.15 + k * 0.35
		var life2 := rng.randf_range(5.0, 8.0)
		var rise := Vector3(rng.randf_range(-1.5, 1.5), 7.0 + k * 9.0, rng.randf_range(-1.5, 1.5)) * s
		var tw2 := sp.create_tween()
		tw2.tween_interval(delay)
		tw2.tween_callback(func(): sp.set_instance_shader_parameter("age", 0.0))
		tw2.set_parallel(true)
		tw2.tween_property(sp, "scale", Vector3.ONE * rng.randf_range(2.6, 4.2) * s * (1.0 + k * 0.6), life2)\
			.set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_CUBIC)
		tw2.tween_property(sp, "global_position", sp.global_position + rise, life2).set_ease(Tween.EASE_OUT)\
			.set_trans(Tween.TRANS_QUAD)
		tw2.tween_method(func(v: float): sp.set_instance_shader_parameter("glow", v), 1.0, 0.0, 1.2)
		tw2.tween_method(func(v: float): sp.set_instance_shader_parameter("age", v), 0.0, 1.0, life2)\
			.set_ease(Tween.EASE_IN)
		tw2.chain().tween_callback(sp.queue_free)
	# ---------------------------------------------------------------- particles
	burst(pos + Vector3.UP * 0.5, Vector3.UP, "explosion")
	burst(pos + Vector3.UP * 0.5, Vector3.UP, "sparks_big")
	if fx >= 1:
		burst(pos + Vector3.UP * 0.3, Vector3.UP, "embers")
		burst(pos + Vector3.UP * 0.2, Vector3.UP, "dust_ring")
	burst(pos + Vector3.UP, Vector3.UP, "smoke")
	# ---------------------------------------------------------------- shockwave on the ground
	var ring := MeshInstance3D.new()
	var qm := QuadMesh.new()
	qm.size = Vector2(2, 2)
	qm.orientation = PlaneMesh.FACE_Y
	ring.mesh = qm
	ring.material_override = _shader_material("shockwave")
	ring.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	r.add_child(ring)
	ring.global_position = pos + Vector3.UP * 0.12
	ring.scale = Vector3.ONE * 0.5
	var rt := ring.create_tween()
	rt.set_parallel(true)
	rt.tween_property(ring, "scale", Vector3.ONE * radius * 2.6, 0.55).set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_CUBIC)
	rt.tween_method(func(v: float): ring.set_instance_shader_parameter("age", v), 0.0, 1.0, 0.6)
	rt.chain().tween_callback(ring.queue_free)
	# ---------------------------------------------------------------- scorch mark
	var dec := Decal.new()
	dec.size = Vector3(radius * 1.3, 3.0, radius * 1.3)
	dec.texture_albedo = _scorch_texture()
	dec.modulate = Color(1, 1, 1, 0.95)
	dec.cull_mask = 1
	r.add_child(dec)
	dec.global_position = pos
	var dt := dec.create_tween()
	dt.tween_interval(45.0)
	dt.tween_property(dec, "modulate:a", 0.0, 8.0)
	dt.tween_callback(dec.queue_free)
	# ---------------------------------------------------------------- debris chunks
	var n_debris: int = [3, 7, 12][clampi(fx, 0, 2)]
	for i in n_debris:
		_debris(pos + Vector3.UP * 0.8, s, rng, fx >= 1 and i % 3 == 0)


static func _shader_material(name: String) -> ShaderMaterial:
	var key := "sh_" + name
	if _mats.has(key):
		return _mats[key]
	var m := ShaderMaterial.new()
	m.shader = load("res://assets/shaders/%s.gdshader" % name)
	_mats[key] = m
	return m


static func _scorch_texture() -> Texture2D:
	if _mats.has("scorch"):
		return _mats["scorch"]
	var img := Image.create(128, 128, false, Image.FORMAT_RGBA8)
	var noise := FastNoiseLite.new()
	noise.frequency = 0.06
	for y in 128:
		for x in 128:
			var v := Vector2(x - 63.5, y - 63.5)
			var d := v.length() / 64.0
			var n := noise.get_noise_2d(x, y) * 0.5 + 0.5
			var ang := noise.get_noise_2d(cos(v.angle()) * 40.0, sin(v.angle()) * 40.0) * 0.25
			var a := clampf((1.0 - d + ang) * 1.6 - 0.25, 0.0, 1.0) * (0.75 + n * 0.25)
			img.set_pixel(x, y, Color(0.03 + n * 0.03, 0.028 + n * 0.02, 0.025, a))
	var t := ImageTexture.create_from_image(img)
	_mats["scorch"] = t
	return t


static func _debris(pos: Vector3, s: float, rng: RandomNumberGenerator, burning: bool) -> void:
	var rb := RigidBody3D.new()
	rb.mass = rng.randf_range(4.0, 15.0)
	rb.collision_layer = 1 << 4
	rb.collision_mask = 1 | (1 << 2)
	var size := Vector3(rng.randf_range(0.12, 0.55), rng.randf_range(0.04, 0.2), rng.randf_range(0.12, 0.6)) * clampf(s, 0.6, 1.4)
	var cs := CollisionShape3D.new()
	var bs := BoxShape3D.new()
	bs.size = size
	cs.shape = bs
	rb.add_child(cs)
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	if not _mats.has("debris"):
		var dm := StandardMaterial3D.new()
		dm.albedo_color = Color(0.05, 0.045, 0.04)
		dm.metallic = 0.6
		dm.roughness = 0.75
		_mats["debris"] = dm
	mi.material_override = _mats["debris"]
	rb.add_child(mi)
	root().add_child(rb)
	rb.global_position = pos + Vector3(rng.randf_range(-0.6, 0.6), rng.randf_range(0, 0.6), rng.randf_range(-0.6, 0.6))
	var dir := Vector3(rng.randf_range(-1, 1), rng.randf_range(0.6, 1.6), rng.randf_range(-1, 1)).normalized()
	rb.linear_velocity = dir * rng.randf_range(7.0, 17.0) * sqrt(s)
	rb.angular_velocity = Vector3(rng.randf_range(-12, 12), rng.randf_range(-12, 12), rng.randf_range(-12, 12))
	if burning:
		var f := fire(rb, Vector3.ZERO, 0.35)
		var ftw := f.create_tween()
		ftw.tween_interval(rng.randf_range(2.5, 5.0))
		ftw.tween_callback(func(): f.emitting = false)
	var tree := Engine.get_main_loop() as SceneTree
	tree.create_timer(14.0).timeout.connect(rb.queue_free)


## Continuous fire (burning vehicle): returns the node, caller frees it.
static func fire(parent: Node3D, offset := Vector3.ZERO, size := 1.0) -> Node3D:
	var p := CPUParticles3D.new()
	var q := QuadMesh.new()
	q.size = Vector2(0.9, 0.9) * size
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
	p.emission_sphere_radius = 0.6 * size
	p.initial_velocity_min *= sqrt(size)
	p.initial_velocity_max *= sqrt(size)
	p.color_ramp = _fire_ramp()
	parent.add_child(p)
	p.position = offset
	var l := OmniLight3D.new()
	l.light_color = Color(1.0, 0.55, 0.2)
	l.light_energy = 3.0 * minf(size, 1.6)
	l.omni_range = 9.0 * sqrt(size)
	p.add_child(l)
	return p


static func _fire_ramp() -> Gradient:
	if _mats.has("fire_ramp"):
		return _mats["fire_ramp"]
	var g := Gradient.new()
	g.set_color(0, Color(1.0, 0.95, 0.7, 1.0))
	g.set_color(1, Color(0.6, 0.1, 0.02, 0.0))
	g.add_point(0.35, Color(1.0, 0.55, 0.12, 0.95))
	g.add_point(0.7, Color(0.85, 0.22, 0.04, 0.6))
	_mats["fire_ramp"] = g
	return g


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
