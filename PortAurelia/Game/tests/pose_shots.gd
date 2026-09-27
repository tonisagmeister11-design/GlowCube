extends Node3D
## Armed poses as seen in game (third-person view from behind):
##   godot --path Game res://tests/pose_shots.tscn -- --out dir

var out := "user://"


func _ready() -> void:
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		if a[i] == "--out":
			out = a[i + 1]
	var sky := SkyEnvironment.new()
	add_child(sky)
	sky.set_time(14.0)
	var g := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(30, 30)
	g.mesh = pm
	g.material_override = load("res://assets/materials/sidewalk.tres")
	add_child(g)
	var weapons: Node = (load("res://assets/generated/weapons/weapons.glb") as PackedScene).instantiate()
	var cases := [["pistol", "ready_pistol"], ["pistol", "aim_pistol"], ["rifle", "ready_rifle"], ["rifle", "aim_rifle"]]
	var models := []
	for i in cases.size():
		var m := CharacterModel.new()
		m.outfit = {"body": "M", "skin": Color(0.78, 0.58, 0.44), "hair": "Short", "hair_color": Color(0.1, 0.07, 0.05),
			"top": "TShirt", "top_color": Color(0.95, 0.95, 0.93), "bottom": "Jeans", "bottom_color": Color(0.2, 0.3, 0.5),
			"shoes": "Sneakers"}
		add_child(m)
		m.position = Vector3(-4.5 + i * 3.0, 0, 0)
		models.append(m)
	await get_tree().process_frame
	for i in cases.size():
		var m: CharacterModel = models[i]
		var src := weapons.find_child(cases[i][0], true, false) as MeshInstance3D
		var mi := MeshInstance3D.new()
		mi.mesh = src.mesh
		var off := Transform3D(Basis(Vector3(0, 0, 1), deg_to_rad(90)) * Basis(Vector3(1, 0, 0), deg_to_rad(-90)) * Basis(Vector3(0, 0, 1), PI), Vector3(0, 0.11, -0.07))
		m.attach(mi, "Hand.R", off)
		m.set_upper(cases[i][1], 1.0)
	var cam := Camera3D.new()
	add_child(cam)
	cam.current = true
	cam.fov = 50
	for i in cases.size():
		var p: Vector3 = (models[i] as Node3D).position
		cam.look_at_from_position(p + Vector3(1.2, 1.9, 3.0), p + Vector3(0, 1.2, -1.0))
		for f in 12:
			await get_tree().process_frame
		await RenderingServer.frame_post_draw
		get_viewport().get_texture().get_image().save_png(out.path_join("pose_%s.png" % cases[i][1]))
		print("shot ", cases[i][1])
	get_tree().quit()
