extends Control
## Visuelle Pruefung: baut einen Bildschirm auf, wartet einige Frames und
## speichert einen Screenshot. Aufruf (siehe docs):
##   xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
##     --path . res://tests/visual/shot.tscn -- --case=main_menu --out=/tmp/shots
## Faelle siehe CASES. Ohne --case werden alle Faelle nacheinander erzeugt.

const CASES := ["icons", "main_menu", "mode", "sandbox", "city", "character", "start", "summary",
	"load", "settings_graphics", "settings_audio", "settings_controls", "settings_gameplay", "settings_ai",
	"controls", "credits", "quit", "loading", "hud", "phone", "phone_status", "phone_inventory", "phone_jobs",
	"phone_housing", "phone_bank", "phone_bus", "phone_news", "phone_rules", "map", "map_route", "shop",
	"dialog", "pause", "pause_save", "death", "rebind"]

var out_dir := "/tmp"
var suffix := ""
var _world: WorldState


func _ready() -> void:
	var case_name := ""
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--case="):
			case_name = a.substr(7)
		elif a.begins_with("--out="):
			out_dir = a.substr(6)
		elif a.begins_with("--res="):
			suffix = "_" + a.substr(6)
	set_anchors_preset(PRESET_FULL_RECT)
	if suffix != "":
		var parts := suffix.substr(1).split("x")
		DisplayServer.window_set_size(Vector2i(int(parts[0]), int(parts[1])))
		for i in 3:
			await get_tree().process_frame
	var cases: Array = Array(case_name.split(",")) if case_name != "" else CASES
	for c in cases:
		await _run_case(c)
	get_tree().quit()


func _run_case(c: String) -> void:
	var holder := Control.new()
	holder.set_anchors_preset(PRESET_FULL_RECT)
	add_child(holder)
	var wait := 30
	var mocks := preload("res://tests/visual/visual_mocks.gd")
	match c:
		"icons":
			holder.add_child(_icon_sheet())
		"main_menu", "mode", "sandbox", "city", "character", "start", "summary", "load", "settings_graphics", \
				"settings_audio", "settings_controls", "settings_gameplay", "settings_ai", "controls", "credits", "quit", "rebind":
			if c == "load":
				mocks.make_sample_saves()
			var menu: Control = load("res://scenes/menu/main_menu.tscn").instantiate()
			holder.add_child(menu)
			await get_tree().process_frame
			await get_tree().process_frame
			mocks.drive_menu(menu, c)
			wait = 70
		"loading":
			var ls: Control = load("res://scenes/menu/loading_screen.tscn").instantiate()
			holder.add_child(ls)
			await get_tree().process_frame
			ls.call("_on_progress", 0.42, "Stadt wird generiert ...")
			wait = 90
		_:
			if _world == null:
				_world = mocks.make_world()
			Game.world = _world
			var scene: Node3D = mocks.make_3d_backdrop()
			holder.add_child(scene)
			var ui: Control = load("res://scenes/ui/game_ui.tscn").instantiate()
			holder.add_child(ui)
			await get_tree().process_frame
			await get_tree().process_frame
			wait = await mocks.drive_game_ui(ui, c, _world)
	for i in wait:
		await get_tree().process_frame
	var img := get_viewport().get_texture().get_image()
	var path := "%s/%s%s.png" % [out_dir, c, suffix]
	img.save_png(path)
	print("saved ", path)
	holder.queue_free()
	await get_tree().process_frame
	await get_tree().process_frame


func _icon_sheet() -> Control:
	var bg := ColorRect.new()
	bg.color = Color(0.05, 0.07, 0.12)
	bg.set_anchors_preset(PRESET_FULL_RECT)
	var grid := GridContainer.new()
	grid.columns = 12
	grid.position = Vector2(30, 20)
	grid.add_theme_constant_override("h_separation", 18)
	grid.add_theme_constant_override("v_separation", 6)
	bg.add_child(grid)
	var dir := DirAccess.open("res://assets/icons")
	var names: Array = []
	for f in dir.get_files():
		if f.ends_with(".svg"):
			names.append(f.get_basename())
	names.sort()
	for n in names:
		var v := VBoxContainer.new()
		v.custom_minimum_size = Vector2(138, 0)
		var h := HBoxContainer.new()
		for s in [20, 32, 48]:
			var t := TextureRect.new()
			t.texture = load("res://assets/icons/%s.svg" % n)
			t.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
			t.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
			t.custom_minimum_size = Vector2(s, s)
			t.size_flags_vertical = Control.SIZE_SHRINK_CENTER
			h.add_child(t)
		v.add_child(h)
		var l := Label.new()
		l.text = n
		l.add_theme_font_size_override("font_size", 13)
		v.add_child(l)
		grid.add_child(v)
	return bg
