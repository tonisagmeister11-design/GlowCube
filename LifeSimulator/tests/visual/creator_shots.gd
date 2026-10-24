extends Control
## Screenshots des Charaktereditors in 1920x1080 und 1280x720.
##   godot --rendering-driver opengl3 --path . res://tests/visual/creator_shots.tscn -- --out=/pfad

var out_dir := "user://shots"


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out_dir = a.substr(6)
	DirAccess.make_dir_recursive_absolute(out_dir)
	var bg := ColorRect.new()
	bg.color = Color(0.06, 0.08, 0.12)
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(bg)
	var margin := MarginContainer.new()
	margin.set_anchors_preset(Control.PRESET_FULL_RECT)
	for side in ["left", "right", "top", "bottom"]:
		margin.add_theme_constant_override("margin_" + side, 24)
	add_child(margin)
	var cc: CharacterCreator = load("res://scenes/menu/character_creator.tscn").instantiate()
	margin.add_child(cc)
	var rng := RandomNumberGenerator.new()
	rng.seed = 3
	var app := Appearance.random(rng, "female", 34.0)
	app.hair_style = 13
	app.top = {"style": 4, "color": Appearance.CLOTH_COLORS[12].color}
	app.accessories = ["earrings", "necklace"]
	cc.set_character({"name": "Lena Hoffmann", "body": "female", "age": 34.0, "appearance": app})
	await _shot(Vector2i(1920, 1080), "creator_1920.png")
	cc.tabs.current_tab = 3
	cc.preview.focus("face")
	await _shot(Vector2i(1280, 720), "creator_1280_face.png")
	cc.set_character({"name": "Theo Berger", "body": "male", "age": 6.0, "appearance": Appearance.random(rng, "male", 6.0)})
	cc.tabs.current_tab = 0
	cc.preview.focus("body")
	await _shot(Vector2i(1280, 720), "creator_1280_child.png")
	get_tree().quit()


func _shot(sz: Vector2i, file: String) -> void:
	get_window().size = sz
	get_window().content_scale_size = sz
	for i in 45:
		await get_tree().process_frame
	get_viewport().get_texture().get_image().save_png(out_dir.path_join(file))
	print("saved ", file)
