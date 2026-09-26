extends Node3D
## Renders the weapon models for a visual check:
##   godot --path Game res://tests/weapon_viewer.tscn -- --out dir [--only rifle,pistol]
## Writes weapons_all.png (all eight on a table) and a side + three-quarter product shot per weapon.

var out := "user://"
var only: PackedStringArray = []


func _ready() -> void:
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		if a[i] == "--out":
			out = a[i + 1]
		elif a[i] == "--only":
			only = a[i + 1].split(",")
	var sky := SkyEnvironment.new()
	add_child(sky)
	sky.set_time(14.0)
	var table := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = Vector3(1.6, 0.04, 1.2)
	table.mesh = bm
	table.material_override = load("res://assets/materials/concrete.tres")
	table.position = Vector3(0, -0.02, 0)
	add_child(table)
	var key := OmniLight3D.new()
	key.position = Vector3(0.4, 0.8, 0.6)
	key.omni_range = 3.0
	key.light_energy = 1.2
	add_child(key)
	var scn: PackedScene = load("res://assets/generated/weapons/weapons.glb")
	var inst := scn.instantiate()
	var names := ["pistol", "revolver", "smg", "knife", "rifle", "shotgun", "sniper", "bat"]
	var spots := [Vector3(-0.5, 0.02, -0.42), Vector3(-0.16, 0.02, -0.42), Vector3(0.22, 0.02, -0.42),
		Vector3(0.58, 0.02, -0.42), Vector3(0.05, 0.02, -0.16), Vector3(0.05, 0.02, 0.08), Vector3(0.05, 0.02, 0.32),
		Vector3(0.05, 0.02, 0.52)]
	var models := {}
	for i in names.size():
		var src := inst.find_child(names[i], true, false) as MeshInstance3D
		var mi := MeshInstance3D.new()
		mi.mesh = src.mesh
		add_child(mi)
		# lay the guns on their left side, barrel pointing to +X
		mi.position = spots[i]
		mi.rotation = Vector3(0, deg_to_rad(90), deg_to_rad(90))
		if names[i] in ["bat"]:
			mi.position.y = 0.035
		models[names[i]] = mi
	inst.free()
	var cam := Camera3D.new()
	add_child(cam)
	cam.current = true
	cam.fov = 40
	cam.near = 0.01
	if only.is_empty():
		cam.look_at_from_position(Vector3(0.05, 1.4, 0.6), Vector3(0.05, 0.0, 0.06))
		await _settle()
		await shot("weapons_all")
	# product shots: one weapon upright above the table, the rest hidden
	table.position.y = -0.25
	for n in names:
		if not only.is_empty() and not n in only:
			continue
		for k in models:
			models[k].visible = k == n
		var m: MeshInstance3D = models[n]
		m.rotation = Vector3.ZERO
		m.position = Vector3.ZERO
		var aabb: AABB = m.mesh.get_aabb()
		var c: Vector3 = aabb.get_center()
		var size: float = aabb.size.length()
		cam.fov = 30
		cam.look_at_from_position(c + Vector3(1.9 * size, 0.12 * size, 0.0), c)
		key.position = c + Vector3(0.8, 0.9, 0.5) * size
		await _settle()
		await shot("w_%s_side" % n)
		cam.look_at_from_position(c + Vector3(0.95 * size, 0.55 * size, -1.1 * size), c)
		await _settle()
		await shot("w_%s_front" % n)
	get_tree().quit()


func _settle() -> void:
	for i in 6:
		await get_tree().process_frame


func shot(n: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png(out.path_join(n + ".png"))
	print("shot ", n)
