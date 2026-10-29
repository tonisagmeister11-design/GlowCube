extends Node3D
## Zeigt Werfer und Rakete aus mehreren Blickwinkeln zur Begutachtung.
func _ready() -> void:
	var out := "/tmp"
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
	var env := WorldEnvironment.new()
	var e := Environment.new()
	e.background_mode = Environment.BG_COLOR
	e.background_color = Color(0.16, 0.17, 0.2)
	e.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	e.ambient_light_color = Color(0.55, 0.57, 0.62)
	e.ambient_light_energy = 1.0
	env.environment = e
	add_child(env)
	var l := DirectionalLight3D.new()
	l.rotation_degrees = Vector3(-35, 125, 0)
	l.light_energy = 2.2
	add_child(l)
	var l2 := DirectionalLight3D.new()
	l2.rotation_degrees = Vector3(-15, -60, 0)
	l2.light_energy = 0.8
	add_child(l2)
	# Werfer waagerecht, Rakete darunter
	var launcher := RpgLauncher.build_launcher()
	launcher.position = Vector3(0, 0.35, 0)
	add_child(launcher)
	var rocket := RpgLauncher.build_rocket()
	rocket.position = Vector3(0, -0.3, 0)
	add_child(rocket)
	var cam := Camera3D.new()
	add_child(cam)
	cam.current = true
	cam.fov = 40.0
	# Das Rohr liegt entlang Z, also muss die Kamera seitlich stehen
	var views := [
		["seite", Vector3(2.6, 0.45, 0.05), Vector3(0, 0.15, 0)],
		["schraeg", Vector3(1.9, 1.0, 1.4), Vector3(0, 0.1, 0.05)],
		["hinten", Vector3(1.1, 0.6, -1.6), Vector3(0, 0.25, -0.3)],
	]
	for v in views:
		cam.position = v[1]
		cam.look_at(v[2])
		for i in 5:
			await get_tree().process_frame
		get_viewport().get_texture().get_image().save_png("%s/rpg_%s.png" % [out, v[0]])
	get_tree().quit()
