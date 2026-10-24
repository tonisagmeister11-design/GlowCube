extends RefCounted
## Testdaten und Steuerung fuer visuelle Pruefungen (tests/visual/shot.gd).
## Nur fuer Screenshots - nicht Teil des Spiels.

const SAMPLE_SLOTS := ["vis_alex", "vis_mara", "quicksave_vis"]


static func make_sample_saves() -> void:
	DirAccess.make_dir_recursive_absolute(SaveSystem.DIR)
	var now := Time.get_unix_time_from_system()
	var metas := [
		{"slot": "vis_alex", "character_name": "Alex Berger", "age": 24.6, "day": 12, "mode": "life",
			"play_time": 3.4 * 3600.0, "saved_unix": now - 1800, "money": 2340.5, "job_title": "Bäckereiverkäufer"},
		{"slot": "vis_mara", "character_name": "Mara Yilmaz", "age": 41.2, "day": 87, "mode": "sandbox",
			"play_time": 12.75 * 3600.0, "saved_unix": now - 86400 * 2, "money": 182400.0, "job_title": "Ärztin"},
		{"slot": "quicksave_vis", "character_name": "Jonas Weber", "age": 16.0, "day": 3, "mode": "free",
			"play_time": 0.6 * 3600.0, "saved_unix": now - 86400 * 9, "money": 25000.0},
	]
	for m in metas:
		var f := FileAccess.open(SaveSystem.slot_path(m.slot, "meta"), FileAccess.WRITE)
		f.store_string(JSON.stringify(m))
		f.close()
		var s := FileAccess.open(SaveSystem.slot_path(m.slot), FileAccess.WRITE)
		s.store_string("x")
		s.close()


static func remove_sample_saves() -> void:
	for s in SAMPLE_SLOTS:
		SaveSystem.delete(s)


static func drive_menu(menu: Control, c: String) -> void:
	match c:
		"mode":
			menu.open_screen("new_game", "mode", false)
		"sandbox":
			var w = menu.open_screen("new_game", "mode", false)
			w.mode = "sandbox"
			w.go_to(0)
		"city", "character", "start", "summary":
			menu.open_screen("new_game", c, false)
		"load":
			menu.open_screen("load", null, false)
		"settings_graphics", "settings_audio", "settings_controls", "settings_gameplay", "settings_ai":
			menu.open_screen("settings", c.substr(9), false)
		"controls", "credits":
			menu.open_screen(c, null, false)
		"quit":
			menu._on_quit()
		"rebind":
			var s = menu.open_screen("settings", "controls", false)
			s.panel.capture_binding("interact")


static func make_world() -> WorldState:
	return null


static func make_3d_backdrop() -> Node3D:
	return Node3D.new()


static func drive_game_ui(_ui: Control, _c: String, _w: WorldState) -> int:
	return 30
