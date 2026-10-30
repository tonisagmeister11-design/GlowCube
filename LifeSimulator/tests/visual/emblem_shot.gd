extends Node3D
## Nahaufnahme von Bug und Heck eines Fahrzeugs - damit laesst sich pruefen,
## ob ein fremdes Markenzeichen wirklich weg ist und das eigene richtig sitzt.
func _ready() -> void:
	var out := "/tmp"
	var path := "res://assets/models/vehicles/sportwagen.scn"
	var tag := "auto"
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
		elif a.begins_with("--model="):
			path = a.substr(8)
		elif a.begins_with("--tag="):
			tag = a.substr(6)
	var env := WorldEnvironment.new()
	var e := Environment.new()
	e.background_mode = Environment.BG_COLOR
	e.background_color = Color(0.18, 0.19, 0.22)
	e.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	e.ambient_light_color = Color(0.62, 0.65, 0.70)
	e.ambient_light_energy = 1.1
	e.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.environment = e
	add_child(env)
	var key := DirectionalLight3D.new()
	key.rotation_degrees = Vector3(-26, 16, 0)
	key.light_energy = 2.4
	add_child(key)
	var fill := DirectionalLight3D.new()
	fill.rotation_degrees = Vector3(-8, -150, 0)
	fill.light_energy = 1.1
	add_child(fill)

	if not ResourceLoader.exists(path):
		print("MODELL FEHLT: ", path)
		get_tree().quit(1)
		return
	var car: Node3D = (load(path) as PackedScene).instantiate()
	add_child(car)
	var size: Vector3 = car.get_meta("size", Vector3(1.9, 1.4, 4.5))
	print("%s: Groesse %.2f x %.2f x %.2f  Dreiecke %d  Emblem vorn %s hinten %s" % [
		tag, size.x, size.y, size.z, int(car.get_meta("triangles", 0)),
		str(car.get_node_or_null("BadgeFront") != null),
		str(car.get_node_or_null("BadgeRear") != null)])

	var cam := Camera3D.new()
	add_child(cam)
	cam.current = true
	cam.fov = 34.0

	# Bug: dicht vor der Front, auf Kuehlerhoehe
	cam.position = Vector3(0.0, size.y * 0.45, -size.z * 0.5 - 1.9)
	cam.look_at(Vector3(0, size.y * 0.42, -size.z * 0.5))
	await _shot(out, tag, "bug")
	# Heck
	cam.position = Vector3(0.0, size.y * 0.55, size.z * 0.5 + 1.9)
	cam.look_at(Vector3(0, size.y * 0.52, size.z * 0.5))
	await _shot(out, tag, "heck")
	get_tree().quit()


func _shot(out: String, tag: String, view: String) -> void:
	for i in 6:
		await get_tree().process_frame
	get_viewport().get_texture().get_image().save_png("%s/%s_%s.png" % [out, tag, view])
