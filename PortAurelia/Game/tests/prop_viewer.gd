extends Node3D
## Renders props from the generated library side by side for visual checks.
## Args: --props lamp_street,lamp_plaza --hour 15 --cam x,y,z --look x,y,z --out file.png

var args := {}
var frames := 0


func _ready() -> void:
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		args[a[i].trim_prefix("--")] = a[i + 1]
	var sky := SkyEnvironment.new()
	add_child(sky)
	sky.set_time(float(args.get("hour", "15")))
	var ground := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(60, 60)
	ground.mesh = pm
	ground.material_override = load("res://assets/materials/sidewalk.tres")
	add_child(ground)
	var lib: Node = (load("res://assets/generated/props/props_library.glb") as PackedScene).instantiate()
	var names := String(args.get("props", "lamp_street")).split(",")
	var x := -float(names.size() - 1) * 2.5
	for n in names:
		var src := lib.find_child(n, true, false)
		if src == null:
			push_warning("missing prop " + n)
			continue
		var dup := src.duplicate() as Node3D
		add_child(dup)
		dup.transform = Transform3D(Basis(Vector3.UP, deg_to_rad(float(args.get("yaw", "0")))), Vector3(x, 0, 0))
		if float(args.get("hour", "15")) > 19.0 or float(args.get("hour", "15")) < 6.0:
			var l := OmniLight3D.new()
			l.position = Vector3(x, 7.5, -2.0)
			l.light_color = Color(1.0, 0.85, 0.65)
			l.light_energy = 3.0
			l.omni_range = 14.0
			add_child(l)
		x += 5.0
	var cam := Camera3D.new()
	add_child(cam)
	cam.fov = float(args.get("fov", "45"))
	var c := String(args.get("cam", "6,5,14")).split_floats(",")
	var l2 := String(args.get("look", "0,4.5,0")).split_floats(",")
	cam.look_at_from_position(Vector3(c[0], c[1], c[2]), Vector3(l2[0], l2[1], l2[2]))
	cam.current = true


func _process(_d: float) -> void:
	frames += 1
	if frames == 10:
		get_viewport().get_texture().get_image().save_png(args.get("out", "res://props.png"))
		get_tree().quit()
