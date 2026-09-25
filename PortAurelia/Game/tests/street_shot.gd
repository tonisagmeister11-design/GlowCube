extends Node
## Boots the world, waits for traffic/pedestrians and saves screenshots from the player camera.
##   godot --path Game res://tests/street_shot.tscn -- --out dir [--wait 30] [--hour 15]

var args := {}


func _ready() -> void:
	var a := OS.get_cmdline_user_args()
	for i in range(0, a.size() - 1, 2):
		args[a[i].trim_prefix("--")] = a[i + 1]
	Game.player_data = PlayerData.new()
	Game.pending_slot = -2
	var world: GameWorld = load("res://scenes/world.tscn").instantiate()
	add_child(world)
	await Events.world_ready
	if world.day_night and args.has("hour"):
		world.day_night.call("set_hour", float(args["hour"]))
	if world.weather and args.has("weather"):
		world.weather.call("set_weather", args["weather"], true)
	if args.has("waypoint"):
		var poi := world.data.nearest_poi(args["waypoint"], world.player.global_position)
		Events.waypoint_set.emit(poi.get("entrance_v", Vector3.ZERO))
	if args.has("wanted"):
		world.police.call("set_wanted", int(args["wanted"]))
	var out: String = args.get("out", "user://")
	var p := world.player as Player
	var home := world.data.nearest_poi("safehouse", p.global_position)
	var f: Vector3 = home.get("facing_v", Vector3.FORWARD)
	p.cam.yaw = atan2(-f.x, -f.z) + float(args.get("yaw", "0"))
	await get_tree().create_timer(float(args.get("wait", "30"))).timeout
	for i in 3:
		await get_tree().process_frame
		get_viewport().get_texture().get_image().save_png(out.path_join("street_%d.png" % i))
		print("shot ", i, " peds ", world.peds.call("count") if world.peds else 0, " cars ", world.traffic.drivers.size() if world.traffic else 0)
		p.cam.yaw += PI * 0.5
		await get_tree().create_timer(4.0).timeout
	get_tree().quit()
