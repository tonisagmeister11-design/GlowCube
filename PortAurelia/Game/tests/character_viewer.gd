extends Node3D
## Renders the generated human with an outfit and animation for visual checks.
## Args: --anim walk --time 0.3 --out file.png --outfit m|f --yaw 0

var args := {}
var frames := 0


func _ready() -> void:
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		args[a[i].trim_prefix("--")] = a[i + 1]
	var sky := SkyEnvironment.new()
	add_child(sky)
	sky.set_time(15.0)
	var ground := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(20, 20)
	ground.mesh = pm
	ground.material_override = load("res://assets/materials/sidewalk.tres")
	add_child(ground)
	var s: PackedScene = load("res://assets/generated/characters/human.glb")
	var outfits := {
		"m": ["Body_M_Base", "Body_M_Neck", "Body_M_Arms", "Head_M", "Eyes_M", "Brows_M", "Hair_Short", "Top_TShirt_M", "Bottom_Jeans_M", "Shoes_Sneakers_M"],
		"f": ["Body_F_Base", "Body_F_Neck", "Body_F_Thighs", "Body_F_Shins", "Body_F_Ankles", "Head_F", "Eyes_F", "Brows_F", "Hair_Long", "Top_Jacket_F", "Bottom_Skirt_F", "Shoes_Sneakers_F"],
		"cop": ["Body_M_Base", "Body_M_Neck", "Head_M", "Eyes_M", "Brows_M", "Hair_Buzz", "Top_LongSleeve_M", "Top_Vest_M", "Bottom_Jeans_M", "Shoes_Boots_M", "Hat_Police"],
	}
	var tints := {"Top_TShirt_M": Color(0.95, 0.95, 0.93), "Bottom_Jeans_M": Color(0.2, 0.3, 0.5), "Hair_Short": Color(0.12, 0.08, 0.05),
		"Top_Jacket_F": Color(0.7, 0.2, 0.25), "Bottom_Skirt_F": Color(0.15, 0.15, 0.18), "Hair_Long": Color(0.45, 0.28, 0.12),
		"Top_LongSleeve_M": Color(0.12, 0.16, 0.3), "Top_Vest_M": Color(0.08, 0.08, 0.1), "Hat_Police": Color(0.1, 0.12, 0.25),
		"Shoes_Sneakers_M": Color(0.9, 0.9, 0.9), "Shoes_Sneakers_F": Color(0.95, 0.95, 0.95), "Shoes_Boots_M": Color(0.1, 0.08, 0.06),
		"Hair_Buzz": Color(0.1, 0.08, 0.06), "Body_M": Color(0.82, 0.62, 0.48), "Head_M": Color(0.82, 0.62, 0.48),
		"Body_F": Color(0.9, 0.72, 0.58), "Head_F": Color(0.9, 0.72, 0.58), "Brows_M": Color(0.12, 0.08, 0.05),
		"Brows_F": Color(0.45, 0.28, 0.12)}
	var xs := [-1.2, 0.0, 1.2]
	var anims := String(args.get("anim", "idle,walk,run")).split(",")
	var keys := ["m", "f", "cop"]
	for i in 3:
		var inst := s.instantiate()
		add_child(inst)
		inst.position = Vector3(xs[i], 0, 0)
		inst.rotation.y = deg_to_rad(float(args.get("yaw", "0")))
		for mi in inst.find_children("*", "MeshInstance3D", true, false):
			mi.visible = String(mi.name) in outfits[keys[i]]
			var tk := String(mi.name)
			if tk.begins_with("Body_"):
				tk = tk.substr(0, 6)
			if tints.has(tk):
				mi.set_instance_shader_parameter("tint", tints[tk])
		var ap: AnimationPlayer = inst.find_children("*", "AnimationPlayer", true, false)[0]
		var an: String = anims[min(i, anims.size() - 1)]
		ap.play(an)
		ap.seek(float(args.get("time", "0.3")), true)
		ap.pause()
		if i == 0:
			print("anims: ", ap.get_animation_list())
	var cam := Camera3D.new()
	add_child(cam)
	cam.fov = float(args.get("fov", "40"))
	var cp := Vector3(0.4, 1.4, 5.2)
	var lp := Vector3(0, 0.95, 0)
	if args.has("cam"):
		var c := String(args["cam"]).split_floats(",")
		cp = Vector3(c[0], c[1], c[2])
	if args.has("look"):
		var l := String(args["look"]).split_floats(",")
		lp = Vector3(l[0], l[1], l[2])
	cam.near = 0.02
	cam.look_at_from_position(cp, lp)
	cam.current = true


func _process(_d: float) -> void:
	frames += 1
	if frames == 8:
		get_viewport().get_texture().get_image().save_png(args.get("out", "res://char.png"))
		get_tree().quit()
