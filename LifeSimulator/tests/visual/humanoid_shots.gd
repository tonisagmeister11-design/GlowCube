extends Node3D
## Visuelle Pruefung der Figuren. Start (Xvfb, OpenGL):
##   godot --rendering-driver opengl3 --path . res://tests/visual/humanoid_shots.tscn -- --out=/pfad
## Erzeugt: lineup.png (Altersreihe beider Koerper), faces.png (Gesichter mit
## Reglervarianten), outfits.png (Kleidung/Frisuren), poses.png (Gehen, Sitzen,
## Schlafen, Winken, Arbeiten, Krabbeln).

var out_dir := "user://shots"
var cam: Camera3D
var _items: Array = []


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out_dir = a.substr(6)
	DirAccess.make_dir_recursive_absolute(out_dir)
	get_window().size = Vector2i(1600, 900)
	_env()
	cam = Camera3D.new()
	cam.fov = 30.0
	add_child(cam)
	await _lineup()
	await _faces()
	await _outfits()
	await _poses()
	get_tree().quit()


func _env() -> void:
	var we := WorldEnvironment.new()
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.62, 0.66, 0.72)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.75, 0.78, 0.85)
	env.ambient_light_energy = 0.45
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	we.environment = env
	add_child(we)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-40, 30, 0)
	sun.light_energy = 1.0
	sun.shadow_enabled = true
	add_child(sun)
	var fill := DirectionalLight3D.new()
	fill.rotation_degrees = Vector3(-15, -140, 0)
	fill.light_energy = 0.35
	add_child(fill)
	var floor_mi := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(60, 60)
	floor_mi.mesh = pm
	var fm := StandardMaterial3D.new()
	fm.albedo_color = Color(0.3, 0.31, 0.33)
	floor_mi.material_override = fm
	add_child(floor_mi)


func _clear() -> void:
	for n in _items:
		n.queue_free()
	_items.clear()


func _add(app: Dictionary, age: float, bodyk: String, pos: Vector3, yaw := 0.0, action := "idle", speed := 0.0, lod := 0) -> Humanoid:
	var h := Humanoid.new()
	h.lod_level = lod
	add_child(h)
	h.setup(app, age, bodyk)
	h.position = pos
	h.rotation.y = yaw
	h.set_action(action)
	h.set_motion(speed)
	_items.append(h)
	return h


func _shot(name: String, frames := 40) -> void:
	for i in frames:
		await get_tree().process_frame
	var img := get_viewport().get_texture().get_image()
	img.save_png(out_dir.path_join(name))
	print("saved ", out_dir.path_join(name))


func _lineup() -> void:
	var ages := [1.0, 3.0, 7.0, 11.0, 15.0, 20.0, 35.0, 55.0, 75.0, 90.0]
	var rng := RandomNumberGenerator.new()
	rng.seed = 7
	var x := -7.2
	for i in ages.size():
		for bi in 2:
			var bodyk := "female" if bi == 0 else "male"
			var app := Appearance.random(rng, bodyk, ages[i])
			_add(app, ages[i], bodyk, Vector3(x, 0, bi * -0.0 + (0.0 if bi == 0 else 0.0)) + Vector3(bi * 0.62, 0, 0), 0.25)
		x += 1.55
	cam.position = Vector3(0.0, 1.0, 17.0)
	cam.look_at(Vector3(0.0, 0.85, 0.0))
	await _shot("lineup.png")
	_clear()


func _faces() -> void:
	var variants := [
		[{}, 30.0, "female"], [{"face": {"jaw": 1.0, "nose": 1.0, "brows": 1.0, "cheeks": 0.0}}, 30.0, "male"],
		[{"face": {"jaw": 0.0, "nose": 0.0, "brows": 0.0, "cheeks": 1.0}}, 30.0, "female"],
		[{"beard": 4, "hair_style": 2}, 45.0, "male"], [{}, 2.0, "female"], [{"hair_style": 11, "beard": 2}, 78.0, "male"],
		[{"hair_style": 8, "skin": Appearance.SKIN_TONES[9].color, "eye_color": Appearance.EYE_COLORS[0].color}, 25.0, "female"],
		[{"hair_style": 4, "accessories": ["glasses", "earrings"], "freckles": 0.8, "hair_color": Appearance.HAIR_COLORS[8].color}, 13.0, "female"],
	]
	for i in variants.size():
		var v: Array = variants[i]
		var bodyk: String = v[2]
		var app := Appearance.defaults(bodyk, v[1])
		app.merge(v[0], true)
		app.eye_color = app.get("eye_color", Appearance.EYE_COLORS[6].color)
		var h := _add(app, v[1], bodyk, Vector3(0, 0, 0), 0.0)
		h.position = Vector3((i - 3.5) * 0.36, 1.6 - h.eye_height(), 0)
	cam.position = Vector3(0, 1.55, 3.6)
	cam.look_at(Vector3(0, 1.52, 0))
	cam.fov = 26.0
	await _shot("faces.png")
	_clear()


func _outfits() -> void:
	var tops := Appearance.TOPS
	var rng := RandomNumberGenerator.new()
	rng.seed = 11
	var n := 0
	for i in 12:
		var bodyk := "female" if i % 2 == 0 else "male"
		var age := 26.0 if i < 10 else (5.0 if i == 10 else 0.9)
		var app := Appearance.random(rng, bodyk, age)
		if i < 10:
			app.top.style = [0, 1, 2, 3, 4, 5, 6, 7, 8, 10][i]
			app.hair_style = [3, 0, 4, 10, 5, 13, 2, 8, 6, 7][i]
			app.bottom.style = [3, 0, 6, 1, 4, 0, 1, 2, 5, 0][i]
			app.shoes.style = [4, 0, 3, 1, 4, 2, 2, 0, 0, 1][i]
			app.accessories = [["cap"], ["sunglasses", "watch"], ["hat"], ["backpack"], ["necklace", "earrings"], ["scarf"], [], ["beanie"], ["glasses"], ["backpack", "cap"]][i]
		_add(app, age, bodyk, Vector3(-6.0 + n * 1.1, 0, 0), 0.35 if i % 3 != 2 else PI + 0.4)
		n += 1
	cam.fov = 30.0
	cam.position = Vector3(0, 1.0, 14.0)
	cam.look_at(Vector3(0, 0.85, 0))
	await _shot("outfits.png")
	_clear()


func _poses() -> void:
	var a1 := Appearance.defaults("female", 28.0)
	var a2 := Appearance.defaults("male", 40.0)
	_add(a1, 28.0, "female", Vector3(-3.6, 0, 0), PI * 0.5, "walk", 1.4)
	_add(a2, 40.0, "male", Vector3(-2.2, 0, 0), PI * 0.5, "run", 4.5)
	_add(a2, 80.0, "male", Vector3(-0.9, 0, 0), PI * 0.5, "walk", 0.9)
	_add(a1, 4.0, "female", Vector3(0.1, 0, 0), PI * 0.5, "walk", 1.1)
	_add(a1, 28.0, "female", Vector3(1.2, 0, 0), 0.4, "sit")
	_add(a2, 40.0, "male", Vector3(2.4, 0, 0), 0.0, "wave")
	_add(a1, 33.0, "female", Vector3(3.6, 0, 0), 0.3, "work")
	_add(Appearance.defaults("male", 0.7), 0.7, "male", Vector3(4.6, 0, 0.3), PI * 0.5, "idle", 0.35)
	_add(a2, 50.0, "male", Vector3(0.0, 0, 2.6), PI * 0.5, "sleep")
	cam.position = Vector3(0, 2.0, 9.0)
	cam.look_at(Vector3(0.4, 0.6, 0.8))
	await _shot("poses.png", 50)
	_clear()
