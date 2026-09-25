extends Node3D
## Development test: loads a few world chunks, renders them at a given time of day
## and saves screenshots. Run:
##   godot --path Game res://tests/chunk_viewer.tscn -- --chunks 8_7,7_8 --cam x,y,z --look x,y,z --hour 15 --out shot.png

var frames := 0
var args := {}
var sky: SkyEnvironment


func _ready() -> void:
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		args[a[i].trim_prefix("--")] = a[i + 1]
	sky = SkyEnvironment.new()
	add_child(sky)
	sky.set_time(float(args.get("hour", "15")))
	var chunks: String = args.get("chunks", "8_7,7_8")
	for c in chunks.split(","):
		var p := "res://assets/generated/city/chunk_%s.glb" % c
		if ResourceLoader.exists(p):
			var s: PackedScene = load(p)
			add_child(s.instantiate())
		else:
			push_warning("missing " + p)
	if args.has("far"):
		var f: PackedScene = load("res://assets/generated/city/city_far.glb")
		add_child(f.instantiate())
	var ocean := preload("res://world/ocean.gd").new()
	add_child(ocean)
	var cam := Camera3D.new()
	cam.far = 4000.0
	cam.fov = 65.0
	add_child(cam)
	var cp := _v(args.get("cam", "60,40,-60"))
	var lk := _v(args.get("look", "0,20,-200"))
	cam.look_at_from_position(cp, lk)
	cam.current = true


func _v(s: String) -> Vector3:
	var p := s.split(",")
	return Vector3(float(p[0]), float(p[1]), float(p[2]))


func _process(_d: float) -> void:
	frames += 1
	if frames == int(args.get("frames", "12")):
		var img := get_viewport().get_texture().get_image()
		img.save_png(args.get("out", "res://shot.png"))
		print("saved ", args.get("out", "res://shot.png"), " fps=", Engine.get_frames_per_second(),
			" draws=", RenderingServer.get_rendering_info(RenderingServer.RENDERING_INFO_TOTAL_DRAW_CALLS_IN_FRAME),
			" prims=", RenderingServer.get_rendering_info(RenderingServer.RENDERING_INFO_TOTAL_PRIMITIVES_IN_FRAME))
		get_tree().quit()
