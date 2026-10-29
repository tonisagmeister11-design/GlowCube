extends Node3D
## Zeigt das aufbereitete Fahrzeug aus mehreren Blickwinkeln.
func _ready() -> void:
	var out := "/tmp"
	var path := "res://assets/models/vehicles/sportwagen.scn"
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
	psm.sky_horizon_color = Color(0.6, 0.65, 0.72)
	psm.ground_horizon_color = Color(0.35, 0.36, 0.38)
	sky.sky_material = psm
	e.sky = sky
	e.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
	e.ambient_light_energy = 1.1
	e.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.environment = e
	add_child(env)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-42, 125, 0)
	sun.light_energy = 2.4
	sun.shadow_enabled = true
	add_child(sun)
	var ground := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(60, 60)
	ground.mesh = pm
	var gmat := StandardMaterial3D.new()
	gmat.albedo_color = Color(0.22, 0.22, 0.24)
	gmat.roughness = 0.85
	ground.material_override = gmat
	add_child(ground)
	if not ResourceLoader.exists(path):
		print("MODELL FEHLT: ", path)
		get_tree().quit(1)
		return
	var car: Node3D = (load(path) as PackedScene).instantiate()
	add_child(car)
	print("Kinder: ", car.get_child_count(), " Groesse: ", car.get_meta("size", Vector3.ZERO),
		" Dreiecke: ", car.get_meta("triangles", 0))
	var cam := Camera3D.new()
	add_child(cam)
	cam.current = true
	cam.fov = 38.0
	var views := [
		["front", Vector3(3.4, 1.3, 5.2), Vector3(0, 0.6, 0.6)],
		["seite", Vector3(7.5, 1.1, 0.0), Vector3(0, 0.6, 0)],
		["heck", Vector3(-3.2, 1.5, -5.0), Vector3(0, 0.6, -0.8)],
		["oben", Vector3(4.5, 4.5, 4.5), Vector3(0, 0.3, 0)],
	]
	for v in views:
		cam.position = v[1]
		cam.look_at(v[2])
		for i in 6:
			await get_tree().process_frame
		get_viewport().get_texture().get_image().save_png("%s/car_%s.png" % [out, v[0]])
	get_tree().quit()
