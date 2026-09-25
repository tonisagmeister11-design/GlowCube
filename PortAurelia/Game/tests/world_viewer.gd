extends Node
## Development test: boots the full streamed world at a camera position and saves a
## screenshot. Args: --cam x,y,z --look x,y,z --hour 15 --out file.png [--frames N] [--wet 0..1]

var args := {}
var frames := 0
var world: GameWorld
var cam: Camera3D


func _ready() -> void:
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		args[a[i].trim_prefix("--")] = a[i + 1]
	world = GameWorld.new()
	add_child(world)
	cam = Camera3D.new()
	cam.far = 5000.0
	cam.fov = float(args.get("fov", "65"))
	add_child(cam)
	var cp := _v(args.get("cam", "-150,2,50"))
	cam.look_at_from_position(cp, _v(args.get("look", "0,5,50")))
	cam.current = true
	var t0 := Time.get_ticks_msec()
	world.initialize(cp)
	print("world init ms=", Time.get_ticks_msec() - t0, " chunks=", world.streaming.loaded_chunks().size())
	world.sky.set_time(float(args.get("hour", "15")))
	ShaderGlobals.set_value("wetness", float(args.get("wet", "0")))


func _v(s: String) -> Vector3:
	var p := s.split(",")
	return Vector3(float(p[0]), float(p[1]), float(p[2]))


func _process(_d: float) -> void:
	frames += 1
	if frames == int(args.get("frames", "20")):
		var img := get_viewport().get_texture().get_image()
		img.save_png(args.get("out", "res://shot.png"))
		print("saved ", args.get("out"), " draws=",
			RenderingServer.get_rendering_info(RenderingServer.RENDERING_INFO_TOTAL_DRAW_CALLS_IN_FRAME),
			" prims=", RenderingServer.get_rendering_info(RenderingServer.RENDERING_INFO_TOTAL_PRIMITIVES_IN_FRAME),
			" objs=", RenderingServer.get_rendering_info(RenderingServer.RENDERING_INFO_TOTAL_OBJECTS_IN_FRAME),
			" vram=", RenderingServer.get_rendering_info(RenderingServer.RENDERING_INFO_VIDEO_MEM_USED) / 1048576)
		get_tree().quit()
