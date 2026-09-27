extends Node3D
## Renders real Vehicle nodes with different liveries / finishes for visual checks.
## Args: --out file.png --cam x,y,z --look x,y,z --hour 15 --set "type:t:f:c2:s:n:s2;..."

var args := {}
var frames := 0


func _ready() -> void:
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		args[a[i].trim_prefix("--")] = a[i + 1]
	var sky := SkyEnvironment.new()
	add_child(sky)
	sky.set_time(float(args.get("hour", "15")))
	var g := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(120, 60)
	g.mesh = pm
	g.material_override = load("res://assets/materials/road_asphalt.tres")
	add_child(g)
	var sets := String(args.get("set", "sports:2:0:ffffff:0:16:1;supercar:4:3:111111:4:20:6;hypercar:6:1:2020a0:0:17:2;muscle:1:2:0a0a0a:0:18:3;van:5:0:ffffff:8:19:1;sedan:3:0:ffffff:0:16:0")).split(";")
	var cols := [Color(0.85, 0.05, 0.05), Color(1.0, 0.8, 0.0), Color(0.9, 0.1, 0.5), Color(0.1, 0.5, 0.9),
		Color(0.95, 0.95, 0.95), Color(0.08, 0.08, 0.09), Color(0.1, 0.6, 0.2), Color(1.0, 0.45, 0.0)]
	var i := 0
	for st in sets:
		var p := String(st).split(":")
		var v := Vehicle.create(p[0], cols[i % cols.size()])
		v.livery = {"t": int(p[1]), "f": int(p[2]), "c2": p[3], "s": int(p[4]), "n": int(p[5]), "s2": int(p[6])}
		v.freeze = true
		add_child(v)
		v.position = Vector3(-float(sets.size() - 1) * 3.2 + i * 6.4, 0.0, 0.0)
		v.rotation.y = deg_to_rad(float(args.get("yaw", "120")))
		i += 1
	var cam := Camera3D.new()
	add_child(cam)
	cam.fov = float(args.get("fov", "40"))
	var c := String(args.get("cam", "4,6,-16")).split_floats(",")
	var l := String(args.get("look", "0,0.6,0")).split_floats(",")
	cam.look_at_from_position(Vector3(c[0], c[1], c[2]), Vector3(l[0], l[1], l[2]))
	cam.current = true


func _process(_d: float) -> void:
	frames += 1
	if frames == 12:
		get_viewport().get_texture().get_image().save_png(args.get("out", "res://liv.png"))
		get_tree().quit()
