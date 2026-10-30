class_name RpgLauncher
extends Node3D
## Raketenwerfer, Rakete und Einschlag fuer inszenierte Abschnitte.
##
## Die Form folgt der Vorlage aus der Filmszene „Hinterhalt Ocean Avenue":
## Rohr mit Holzgriffen und Ringen, trichterfoermige Muendung hinten, Visier,
## Gefechtskopf vorn. Alles wird prozedural erzeugt, damit keine fremden
## Dateien noetig sind.

const TUBE_LEN := 1.0
const TUBE_R := 0.034

static var _mesh_cache: Dictionary = {}


## Baut das Werfer-Modell (einmalig erzeugt, danach geteilt).
static func build_launcher() -> Node3D:
	var root := Node3D.new()
	root.name = "RpgLauncher"
	var met := _metal()
	var wood := _wood()
	var tube := MeshInstance3D.new()
	var cyl := CylinderMesh.new()
	cyl.top_radius = TUBE_R
	cyl.bottom_radius = TUBE_R
	cyl.height = TUBE_LEN
	cyl.radial_segments = 20
	tube.mesh = cyl
	tube.material_override = met
	tube.rotation.x = PI * 0.5
	root.add_child(tube)
	# Holzschalen am Rohr
	for z in [-0.01, 0.25]:
		var sl := MeshInstance3D.new()
		var c2 := CylinderMesh.new()
		c2.top_radius = 0.05
		c2.bottom_radius = 0.05
		c2.height = 0.22
		c2.radial_segments = 20
		sl.mesh = c2
		sl.material_override = wood
		sl.rotation.x = PI * 0.5
		sl.position.z = z
		root.add_child(sl)
	# Verstaerkungsringe
	for z in [-0.18, 0.42, 0.5]:
		var ring := MeshInstance3D.new()
		var tor := TorusMesh.new()
		tor.inner_radius = 0.030
		tor.outer_radius = 0.042
		tor.rings = 6
		tor.ring_segments = 20
		ring.mesh = tor
		ring.material_override = met
		ring.position.z = z
		ring.rotation.x = PI * 0.5
		root.add_child(ring)
	# Trichter hinten
	var ven := MeshInstance3D.new()
	var vm := CylinderMesh.new()
	vm.top_radius = 0.034
	vm.bottom_radius = 0.068
	vm.height = 0.2
	vm.radial_segments = 20
	ven.mesh = vm
	ven.material_override = met
	ven.rotation.x = PI * 0.5
	ven.position.z = -0.6
	root.add_child(ven)
	# Griff, Abzugsbuegel, Vordergriff
	var grip := MeshInstance3D.new()
	var gb := BoxMesh.new()
	gb.size = Vector3(0.032, 0.13, 0.05)
	grip.mesh = gb
	grip.material_override = wood
	grip.position = Vector3(0, -0.1, 0.1)
	grip.rotation.x = -0.25
	root.add_child(grip)
	var fg := MeshInstance3D.new()
	var fb := BoxMesh.new()
	fb.size = Vector3(0.03, 0.11, 0.045)
	fg.mesh = fb
	fg.material_override = wood
	fg.position = Vector3(0, -0.09, 0.3)
	root.add_child(fg)
	# Visier: Halter auf dem Rohr, davor ein Visierblatt
	var post := MeshInstance3D.new()
	var pbm := BoxMesh.new()
	pbm.size = Vector3(0.012, 0.055, 0.03)
	post.mesh = pbm
	post.material_override = met
	post.position = Vector3(0.0, 0.055, 0.1)
	root.add_child(post)
	var leaf := MeshInstance3D.new()
	var lbm := BoxMesh.new()
	lbm.size = Vector3(0.006, 0.075, 0.05)
	leaf.mesh = lbm
	leaf.material_override = met
	leaf.position = Vector3(0.0, 0.095, 0.1)
	root.add_child(leaf)
	# Optisches Visier seitlich, wie in der Vorlage
	var scope := MeshInstance3D.new()
	var scm := CylinderMesh.new()
	scm.top_radius = 0.018
	scm.bottom_radius = 0.018
	scm.height = 0.17
	scm.radial_segments = 12
	scope.mesh = scm
	scope.material_override = met
	scope.rotation.x = PI * 0.5
	scope.position = Vector3(-0.055, 0.052, 0.12)
	root.add_child(scope)
	# Gefechtskopf vorn
	var head := MeshInstance3D.new()
	head.mesh = _warhead_mesh()
	head.material_override = _olive()
	head.position.z = 0.62
	root.add_child(head)
	return root


## Rakete im Flug (schlanker Koerper mit Leitwerk und Glut am Heck).
static func build_rocket() -> Node3D:
	var root := Node3D.new()
	root.name = "Rocket"
	var body := MeshInstance3D.new()
	var c := CylinderMesh.new()
	c.top_radius = 0.035
	c.bottom_radius = 0.035
	c.height = 0.45
	c.radial_segments = 12
	body.mesh = c
	body.material_override = _olive()
	body.rotation.x = PI * 0.5
	root.add_child(body)
	var tip := MeshInstance3D.new()
	tip.mesh = _warhead_mesh()
	tip.material_override = _olive()
	tip.position.z = 0.2
	root.add_child(tip)
	for i in 4:
		var fin := MeshInstance3D.new()
		var fb := BoxMesh.new()
		fb.size = Vector3(0.005, 0.1, 0.09)
		fin.mesh = fb
		fin.material_override = _metal()
		fin.position.z = -0.2
		fin.rotation.z = PI * 0.5 * i
		fin.position += Vector3(sin(PI * 0.5 * i), cos(PI * 0.5 * i), 0) * 0.055
		root.add_child(fin)
	# Antriebsglut
	var glow := OmniLight3D.new()
	glow.light_color = Color(1.0, 0.62, 0.28)
	glow.light_energy = 4.0
	glow.omni_range = 9.0
	glow.position.z = -0.28
	root.add_child(glow)
	var flame := MeshInstance3D.new()
	var fm := CylinderMesh.new()
	fm.top_radius = 0.001
	fm.bottom_radius = 0.045
	fm.height = 0.5
	fm.radial_segments = 10
	flame.mesh = fm
	flame.rotation.x = -PI * 0.5
	var fmat := StandardMaterial3D.new()
	fmat.emission_enabled = true
	fmat.emission = Color(1.0, 0.58, 0.22)
	fmat.emission_energy_multiplier = 6.0
	fmat.albedo_color = Color(1.0, 0.7, 0.35)
	fmat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	flame.material_override = fmat
	flame.position.z = -0.48
	root.add_child(flame)
	return root


static func _warhead_mesh() -> Mesh:
	if _mesh_cache.has("warhead"):
		return _mesh_cache["warhead"]
	# Profil wie in der Vorlage, als Rotationskoerper
	var profile := [Vector2(0.0, 0.30), Vector2(0.005, 0.29), Vector2(0.010, 0.25), Vector2(0.020, 0.21),
		Vector2(0.034, 0.15), Vector2(0.046, 0.09), Vector2(0.052, 0.04), Vector2(0.052, -0.01),
		Vector2(0.046, -0.05), Vector2(0.034, -0.08), Vector2(0.028, -0.11), Vector2(0.0, -0.11)]
	var mesh := _lathe(profile, 20)
	_mesh_cache["warhead"] = mesh
	return mesh


## Rotationskoerper aus einem Profil (x = Radius, y = Hoehe entlang -Z).
static func _lathe(profile: Array, segments: int) -> ArrayMesh:
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	for s in segments:
		var a0 := TAU * float(s) / float(segments)
		var a1 := TAU * float(s + 1) / float(segments)
		for i in range(profile.size() - 1):
			var p0: Vector2 = profile[i]
			var p1: Vector2 = profile[i + 1]
			var v := [
				Vector3(cos(a0) * p0.x, sin(a0) * p0.x, p0.y),
				Vector3(cos(a1) * p0.x, sin(a1) * p0.x, p0.y),
				Vector3(cos(a1) * p1.x, sin(a1) * p1.x, p1.y),
				Vector3(cos(a0) * p1.x, sin(a0) * p1.x, p1.y),
			]
			for idx in [0, 1, 2, 0, 2, 3]:
				st.add_vertex(v[idx])
	st.generate_normals()
	return st.commit()


static func _metal() -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.24, 0.24, 0.26)
	m.metallic = 0.85
	m.roughness = 0.42
	return m


static func _wood() -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.48, 0.30, 0.16)
	m.roughness = 0.6
	return m


static func _olive() -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.30, 0.33, 0.20)
	m.roughness = 0.6
	m.metallic = 0.15
	return m


## Explosion am Einschlagsort: Lichtblitz, Feuerball, Rauch, Truemmer.
## Gibt den erzeugten Knoten zurueck, der sich selbst wieder entfernt.
static func spawn_explosion(parent: Node3D, pos: Vector3, scale_f := 1.0) -> Node3D:
	var root := Node3D.new()
	root.name = "Explosion"
	root.position = pos
	parent.add_child(root)
	var light := OmniLight3D.new()
	light.light_color = Color(1.0, 0.66, 0.3)
	light.light_energy = 14.0 * scale_f
	light.omni_range = 34.0 * scale_f
	root.add_child(light)
	var ball := MeshInstance3D.new()
	var sm := SphereMesh.new()
	sm.radius = 1.2 * scale_f
	sm.height = 2.4 * scale_f
	ball.mesh = sm
	var bm := StandardMaterial3D.new()
	bm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	bm.emission_enabled = true
	bm.emission = Color(1.0, 0.72, 0.34)
	bm.emission_energy_multiplier = 8.0
	bm.albedo_color = Color(1.0, 0.8, 0.5)
	bm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	ball.material_override = bm
	root.add_child(ball)
	var smoke := GPUParticles3D.new()
	smoke.amount = 48
	smoke.lifetime = 2.6
	smoke.one_shot = true
	smoke.explosiveness = 0.85
	var pm := ParticleProcessMaterial.new()
	pm.direction = Vector3(0, 1, 0)
	pm.spread = 55.0
	pm.initial_velocity_min = 3.0 * scale_f
	pm.initial_velocity_max = 10.0 * scale_f
	pm.gravity = Vector3(0, 1.2, 0)
	pm.scale_min = 1.5 * scale_f
	pm.scale_max = 4.0 * scale_f
	pm.color = Color(0.22, 0.20, 0.19, 0.8)
	smoke.process_material = pm
	var qm := QuadMesh.new()
	qm.size = Vector2(2.2, 2.2) * scale_f
	var smat := StandardMaterial3D.new()
	smat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	smat.albedo_color = Color(0.2, 0.19, 0.18, 0.55)
	smat.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
	smat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	qm.material = smat
	smoke.draw_pass_1 = qm
	smoke.emitting = true
	root.add_child(smoke)
	# Ausblenden und aufraeumen
	var tw := root.create_tween()
	tw.set_parallel(true)
	tw.tween_property(ball, "scale", Vector3.ONE * 3.2, 0.5).set_trans(Tween.TRANS_EXPO).set_ease(Tween.EASE_OUT)
	tw.tween_property(bm, "albedo_color:a", 0.0, 0.6)
	tw.tween_property(light, "light_energy", 0.0, 0.9)
	tw.chain().tween_interval(2.8)
	tw.chain().tween_callback(root.queue_free)
	return root
