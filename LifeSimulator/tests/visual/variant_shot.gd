extends Node3D
## Stellt alle Spielarten eines Personenmodells nebeneinander.
func _ready() -> void:
	var out := "/tmp"
	var model := "bruno"
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
		elif a.begins_with("--model="):
			model = a.substr(8)
	var env := WorldEnvironment.new()
	var e := Environment.new()
	e.background_mode = Environment.BG_SKY
	var sky := Sky.new()
	var psm := ProceduralSkyMaterial.new()
	psm.sky_horizon_color = Color(0.62, 0.66, 0.72)
	sky.sky_material = psm
	e.sky = sky
	e.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
	e.ambient_light_energy = 1.2
	e.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.environment = e
	add_child(env)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-32, 160, 0)
	sun.light_energy = 2.2
	sun.shadow_enabled = true
	add_child(sun)
	var ground := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(40, 40)
	ground.mesh = pm
	var gm := StandardMaterial3D.new()
	gm.albedo_color = Color(0.25, 0.25, 0.27)
	gm.roughness = 0.9
	ground.material_override = gm
	add_child(ground)

	var list: Array = PersonModels.variants(model)
	print("Spielarten: ", list.size())
	var step := 0.85
	var x0 := -step * (list.size() - 1) * 0.5
	for i in list.size():
		var p: DetailedPerson = PersonModels.build(model, false, i)
		if p == null:
			print("FEHLER: Spielart ", i, " baut nicht")
			continue
		p.position = Vector3(x0 + step * i, 0.0, 0.0)
		add_child(p)
		if p.animator != null:
			p.animator.paused = true
			p.animator.speed = 1.45
			p.animator._blend = 1.0
			p.animator._phase = TAU * float(i) / float(maxi(1, list.size()))
			p.animator.step(0.0)
		print("  %d: %s  Hoehe %.2f m" % [i, p.person_name, p.height()])

	var cam := Camera3D.new()
	add_child(cam)
	cam.current = true
	cam.fov = 40.0
	cam.position = Vector3(0.0, 1.05, 4.4)
	cam.look_at(Vector3(0, 0.95, 0))
	for i in 10:
		await get_tree().process_frame
	get_viewport().get_texture().get_image().save_png("%s/varianten.png" % out)
	get_tree().quit()
