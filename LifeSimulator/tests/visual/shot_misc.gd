extends Node3D
## Screenshot-Hilfsszene: Menue-Hintergrund und Innenraeume.
## Aufruf: ... res://tests/visual/shot_misc.tscn -- --out=/tmp/shots

var out_dir := "/tmp"


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out_dir = a.substr(6)
	DirAccess.make_dir_recursive_absolute(out_dir)
	_run.call_deferred()


func _shot(name: String) -> void:
	for i in 12:
		await get_tree().process_frame
	get_viewport().get_texture().get_image().save_png("%s/%s.png" % [out_dir, name])
	print("gespeichert: ", name)


func _run() -> void:
	var mb: Node3D = load("res://scenes/menu/menu_backdrop.tscn").instantiate()
	add_child(mb)
	print("Menue-Hintergrund: %d ms" % mb.build_ms)
	await _shot("menu")
	mb.free()
	var env := DayNight.new()
	add_child(env)
	env.set_hour(14.0)
	var cam := Camera3D.new()
	add_child(cam)
	cam.make_current()
	for kind in ["home_apartment", "supermarket", "restaurant"]:
		var room: InteriorRoot = InteriorBuilder.build(kind, {"seed": 2})
		room.position = Vector3(0, -2000, 0)
		add_child(room)
		var sz: Vector3 = room.size
		cam.global_position = room.global_position + Vector3(sz.x * 0.85, 1.7, sz.z - 0.6)
		cam.look_at(room.global_position + Vector3(sz.x * 0.3, 1.0, sz.z * 0.3), Vector3.UP)
		cam.fov = 75.0
		await _shot("interior_" + kind)
		room.free()
	get_tree().quit()
