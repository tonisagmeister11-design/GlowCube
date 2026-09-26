extends Node3D
## Renders the generated vehicles for a visual check.
##   --out file.png                     overview of all 16 vehicles
##   --closeup sedan,sports --dir path  front 3/4, rear 3/4 and side shots per vehicle

var out := "res://veh.png"
var closeup: PackedStringArray = []
var dir := "user://"
var hide: PackedStringArray = []
var colors := [Color(0.7, 0.1, 0.1), Color(0.15, 0.25, 0.5), Color(0.08, 0.08, 0.09), Color(0.95, 0.75, 0.1),
	Color(0.9, 0.35, 0.05), Color(0.5, 0.55, 0.52), Color(0.2, 0.3, 0.2), Color(0.9, 0.9, 0.9)]
var ids := ["compact", "sedan", "luxury", "sports", "supercar", "suv", "pickup", "van", "taxi", "police",
	"ambulance", "delivery", "truck", "fire_truck", "bus", "motorcycle"]


func _ready() -> void:
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		match a[i]:
			"--out":
				out = a[i + 1]
			"--closeup":
				closeup = a[i + 1].split(",")
			"--dir":
				dir = a[i + 1]
			"--hide":
				hide = a[i + 1].split(",")
	var sky := SkyEnvironment.new()
	add_child(sky)
	sky.set_time(15.5)
	var g := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(120, 60)
	g.mesh = pm
	g.material_override = load("res://assets/materials/road_asphalt.tres")
	add_child(g)
	var cam := Camera3D.new()
	add_child(cam)
	cam.current = true
	if closeup.is_empty():
		for i in ids.size():
			var v := _spawn(ids[i], i)
			var row := i / 8
			v.position = Vector3(-24.0 + (i % 8) * 6.5, 0, row * 14.0)
			v.rotation.y = deg_to_rad(210)
		cam.fov = 50
		cam.look_at_from_position(Vector3(4, 9, -20), Vector3(0, 0.5, 6))
		await _settle()
		await _shot(out)
		get_tree().quit()
		return
	for n in closeup:
		var v := _spawn(n, ids.find(n))
		var l := 4.6
		for mi in v.find_children("*", "MeshInstance3D", true, false):
			l = maxf(l, (mi as MeshInstance3D).get_aabb().size.z)
		cam.fov = 32
		cam.look_at_from_position(Vector3(-0.62, 0.3, -1.0) * l * 1.2 + Vector3(0, 0.8, 0), Vector3(0, 0.6, 0))
		await _settle()
		await _shot(dir.path_join(n + "_front.png"))
		cam.look_at_from_position(Vector3(0.7, 0.35, 0.95) * l * 1.2 + Vector3(0, 0.8, 0), Vector3(0, 0.6, 0))
		await _settle()
		await _shot(dir.path_join(n + "_rear.png"))
		cam.look_at_from_position(Vector3(1.0, 0.12, -0.05) * l * 1.35 + Vector3(0, 0.6, 0), Vector3(0, 0.6, 0))
		await _settle()
		await _shot(dir.path_join(n + "_side.png"))
		v.queue_free()
	get_tree().quit()


func _spawn(n: String, i: int) -> Node3D:
	var s: PackedScene = load("res://assets/generated/vehicles/%s.glb" % n)
	var v := s.instantiate()
	add_child(v)
	for mi in v.find_children("*", "MeshInstance3D", true, false):
		mi.visible = not String(mi.name) in hide
		mi.set_instance_shader_parameter("paint", colors[maxi(i, 0) % colors.size()])
		mi.set_instance_shader_parameter("dirt", 0.05)
		if String(mi.name).begins_with("Light"):
			var col := Color(1.0, 0.95, 0.85)
			match String(mi.name):
				"LightBrake":
					col = Color(1.0, 0.06, 0.03)
				"LightTurnL", "LightTurnR":
					col = Color(1.0, 0.55, 0.05)
			mi.set_instance_shader_parameter("lamp_color", col)
			mi.set_instance_shader_parameter("intensity", 0.0)
		if String(mi.name) == "Siren":
			mi.set_instance_shader_parameter("active", 1.0)
	return v


func _settle() -> void:
	for i in 8:
		await get_tree().process_frame


func _shot(path: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png(path)
	print("shot ", path)
