extends Node3D
## Zeigt die Grundhaltung, mehrere Gehphasen und die Sitzhaltung.
func _ready() -> void:
	var out := "/tmp"
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
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
	sun.rotation_degrees = Vector3(-38, 150, 0)
	sun.light_energy = 2.2
	sun.shadow_enabled = true
	add_child(sun)
	var ground := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(30, 30)
	ground.mesh = pm
	var gm := StandardMaterial3D.new()
	gm.albedo_color = Color(0.24, 0.24, 0.26)
	gm.roughness = 0.9
	ground.material_override = gm
	add_child(ground)
	var person: Node3D = (load("res://assets/models/people/mann.scn") as PackedScene).instantiate()
	add_child(person)
	var anim := BodyAnimator.new()
	anim.paused = true
	add_child(anim)
	var ok := anim.setup(person)
	print("Bewegungsablauf eingerichtet: ", ok, "  Knochen: ", anim._idx.size())
	var cam := Camera3D.new()
	add_child(cam)
	cam.current = true
	cam.fov = 42.0
	cam.position = Vector3(2.6, 1.05, 2.0)
	cam.look_at(Vector3(0, 0.95, 0))
	# Grundhaltung
	anim.apply_rest_pose()
	await _shot(out, "stand")
	# Vier Gehphasen
	anim.speed = 1.45
	anim._blend = 1.0
	for i in 4:
		anim._phase = TAU * float(i) / 4.0
		anim.step(0.0)
		await _shot(out, "gehen%d" % i)
	# Sitzen
	anim.seated = true
	anim.step(0.0)
	await _shot(out, "sitzen")
	get_tree().quit()


func _shot(out: String, tag: String) -> void:
	for i in 6:
		await get_tree().process_frame
	get_viewport().get_texture().get_image().save_png("%s/pose_%s.png" % [out, tag])
