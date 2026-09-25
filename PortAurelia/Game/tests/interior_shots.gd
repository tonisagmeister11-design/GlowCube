extends Node
## Screenshots of every interior:  godot --path Game res://tests/interior_shots.tscn -- --out dir

func _ready() -> void:
	var out := "user://"
	var a := OS.get_cmdline_user_args()
	if a.size() >= 2:
		out = a[1]
	Game.player_data = PlayerData.new()
	Game.pending_slot = -2
	var world: GameWorld = load("res://scenes/world.tscn").instantiate()
	add_child(world)
	await Events.world_ready
	var im := InteriorManager.get_manager()
	var p := world.player as Player
	for t in ["safehouse", "shop_convenience", "shop_weapons", "bank"]:
		var poi := world.data.nearest_poi(t, p.global_position)
		await im.enter(poi, true)
		await get_tree().create_timer(6.0).timeout
		p.cam.pitch = -0.15
		await get_tree().create_timer(2.0).timeout
		await RenderingServer.frame_post_draw
		get_viewport().get_texture().get_image().save_png(out.path_join("int_%s.png" % t))
		print("shot ", t)
		await im.leave()
	get_tree().quit()
