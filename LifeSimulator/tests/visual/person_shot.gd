extends Node3D
## Zeigt ein aufbereitetes Personenmodell aus mehreren Blickwinkeln.
func _ready() -> void:
	var out := "/tmp"
	var path := "res://assets/models/people/mann.scn"
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
		elif a.begins_with("--model="):
			path = a.substr(8)
	var env := WorldEnvironment.new()
	var e := Environment.new()
	e.background_mode = Environment.BG_SKY
	var sky := Sky.new()
	var psm := ProceduralSkyMaterial.new()
	psm.sky_horizon_color = Color(0.62, 0.66, 0.72)
	psm.ground_horizon_color = Color(0.35, 0.36, 0.38)
	sky.sky_material = psm
	e.sky = sky
	e.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
	e.ambient_light_energy = 1.2
	e.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.environment = e
	add_child(env)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-38, 140, 0)
	sun.light_energy = 2.2
	sun.shadow_enabled = true
	add_child(sun)
	var ground := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(30, 30)
	ground.mesh = pm
	var gmat := StandardMaterial3D.new()
	gmat.albedo_color = Color(0.24, 0.24, 0.26)
	gmat.roughness = 0.9
	ground.material_override = gmat
	add_child(ground)
	if not ResourceLoader.exists(path):
		print("MODELL FEHLT: ", path)
		get_tree().quit(1)
		return
	var person: Node3D = (load(path) as PackedScene).instantiate()
	add_child(person)
	print("Dreiecke: ", person.get_meta("triangles", 0), "  Hoehe: ", person.get_meta("height", 0.0),
		"  Knochen: ", person.get_meta("bone_roles", {}).size())
	var cam := Camera3D.new()
	add_child(cam)
	cam.current = true
	cam.fov = 40.0
	var views := [
		["ganz", Vector3(0.0, 1.0, 3.4), Vector3(0, 0.95, 0)],
		["kopf", Vector3(0.25, 1.62, 0.85), Vector3(0, 1.58, 0)],
		["seite", Vector3(3.0, 1.0, 0.2), Vector3(0, 0.95, 0)],
		["hinten", Vector3(0.0, 1.0, -3.0), Vector3(0, 0.95, 0)],
	]
	for v in views:
		cam.position = v[1]
		cam.look_at(v[2])
		for i in 8:
			await get_tree().process_frame
		get_viewport().get_texture().get_image().save_png("%s/person_%s.png" % [out, v[0]])
	get_tree().quit()
