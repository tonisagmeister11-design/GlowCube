extends Node3D
## Renders all generated vehicles in a row for a visual check.

var frames := 0
var out := "res://veh.png"


func _ready() -> void:
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		if a[i] == "--out":
			out = a[i + 1]
	var sky := SkyEnvironment.new()
	add_child(sky)
	sky.set_time(15.5)
	var g := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(120, 60)
	g.mesh = pm
	g.material_override = load("res://assets/materials/road_asphalt.tres")
	add_child(g)
	var ids := ["compact", "sedan", "luxury", "sports", "supercar", "suv", "pickup", "van", "taxi", "police",
		"ambulance", "delivery", "truck", "fire_truck", "bus", "motorcycle"]
	var colors := [Color(0.7, 0.1, 0.1), Color(0.15, 0.25, 0.5), Color(0.08, 0.08, 0.09), Color(0.95, 0.75, 0.1),
		Color(0.9, 0.35, 0.05), Color(0.5, 0.55, 0.52), Color(0.2, 0.3, 0.2), Color(0.9, 0.9, 0.9)]
	var x := -30.0
	for i in ids.size():
		var s: PackedScene = load("res://assets/generated/vehicles/%s.glb" % ids[i])
		var v := s.instantiate()
		add_child(v)
		var row := i / 8
		v.position = Vector3(-24.0 + (i % 8) * 6.5, 0, row * 14.0)
		v.rotation.y = deg_to_rad(210)
		for mi in v.find_children("*", "MeshInstance3D", true, false):
			mi.set_instance_shader_parameter("paint", colors[i % colors.size()])
			if String(mi.name).begins_with("Light"):
				mi.set_instance_shader_parameter("intensity", 1.0 if mi.name == "LightHead" else 0.0)
			if String(mi.name) == "Siren":
				mi.set_instance_shader_parameter("active", 1.0)
	var cam := Camera3D.new()
	add_child(cam)
	cam.fov = 50
	cam.look_at_from_position(Vector3(4, 9, -20), Vector3(0, 0.5, 6))
	cam.current = true


func _process(_d: float) -> void:
	frames += 1
	if frames == 8:
		get_viewport().get_texture().get_image().save_png(out)
		get_tree().quit()
