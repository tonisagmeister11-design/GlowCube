extends Node3D
## Blows up a car and captures the explosion in slow motion:
##   godot --path Game res://tests/explosion_shots.tscn -- --out dir

var out := "user://"


func _ready() -> void:
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		if a[i] == "--out":
			out = a[i + 1]
	var sky := SkyEnvironment.new()
	add_child(sky)
	sky.set_time(17.5)
	var ground := StaticBody3D.new()
	var cs := CollisionShape3D.new()
	var bs := BoxShape3D.new()
	bs.size = Vector3(200, 1, 200)
	cs.shape = bs
	cs.position.y = -0.5
	ground.add_child(cs)
	var gm := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(200, 200)
	gm.mesh = pm
	gm.material_override = load("res://assets/materials/road_asphalt.tres")
	ground.add_child(gm)
	add_child(ground)
	for i in 3:
		var parked := Vehicle.create(["compact", "suv", "sports"][i], Color(0.2 + i * 0.3, 0.25, 0.5 - i * 0.12))
		add_child(parked)
		parked.global_position = Vector3(-7.0 + i * 7.0, 0.6, 9.0)
	var car := Vehicle.create("sedan", Color(0.7, 0.08, 0.06))
	add_child(car)
	car.global_position = Vector3(0, 0.6, 0)
	car.rotation.y = 0.6
	var cam := Camera3D.new()
	add_child(cam)
	cam.current = true
	cam.fov = 55
	cam.look_at_from_position(Vector3(9, 3.2, -12), Vector3(0, 2.2, 0))
	await wait(2.0)
	await shot("expl_0_before")
	Engine.time_scale = 0.06
	car.explode()
	var marks := [0.05, 0.2, 0.45, 0.9, 1.8, 4.0]
	var t := 0.0
	var k := 0
	while k < marks.size():
		await get_tree().process_frame
		t += get_process_delta_time()
		if t >= marks[k]:
			await shot("expl_%d_t%.2f" % [k + 1, marks[k]])
			k += 1
	Engine.time_scale = 1.0
	get_tree().quit()


func wait(s: float) -> void:
	await get_tree().create_timer(s, true, false, true).timeout


func shot(n: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png(out.path_join(n + ".png"))
	print("shot ", n)
