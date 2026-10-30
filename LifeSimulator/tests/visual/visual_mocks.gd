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
	var w := WorldState.create_new({"seed": 5, "mode": "life", "rules": {"city_size": "small", "population": 400},
		"character": {"name": "Alex Berger", "body": "female", "age": 24.0, "appearance": {}}, "start": {"situation": "auto"}})
	w.silent = false
	w.post_news("wirtschaft", "Die Havenbrücker Sparkasse senkt die Zinsen für Baukredite.")
	w.post_news("politik", "Der Stadtrat berät über günstigere Bustickets für Schüler.")
	return w


## Schlichte 3D-Kulisse (Himmel, Boden, Spieler-Platzhalter) hinter dem HUD.
static func make_3d_backdrop() -> Node3D:
	var root := Node3D.new()
	var env := WorldEnvironment.new()
	var e := Environment.new()
	e.background_mode = Environment.BG_SKY
	var sky := Sky.new()
	var mat := ProceduralSkyMaterial.new()
	mat.sky_top_color = Color(0.25, 0.45, 0.8)
	mat.sky_horizon_color = Color(0.95, 0.7, 0.5)
	mat.ground_horizon_color = Color(0.5, 0.45, 0.4)
	sky.sky_material = mat
	e.sky = sky
	env.environment = e
	root.add_child(env)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-30, 40, 0)
	root.add_child(sun)
	var ground := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(400, 400)
	ground.mesh = pm
	root.add_child(ground)
	for i in 12:
		var b := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(10, 8 + (i * 7) % 30, 10)
		b.mesh = bm
		b.position = Vector3(-60 + i * 11, bm.size.y * 0.5, -40 - (i % 3) * 18)
		root.add_child(b)
	var pl := Node3D.new()
	pl.add_to_group("player")
	root.add_child(pl)
	var cam := Camera3D.new()
	cam.position = Vector3(0, 3, 8)
	cam.current = true
	root.add_child(cam)
	return root


static func drive_game_ui(ui: Control, c: String, w: WorldState) -> int:
	var tree := ui.get_tree()
	var pl := tree.get_first_node_in_group("player") as Node3D
	if pl:
		var sp: Vector2 = w.city.spawn_points[0].pos if not w.city.spawn_points.is_empty() else Vector2.ZERO
		pl.global_position = CityModel.v3(sp)
	UIMapData.texture_now(w)
	match c:
		"hud":
			EventBus.notify.emit("Willkommen in Havenbrück!", "info")
			EventBus.notify.emit("Gehalt erhalten: 412,00 €", "money")
			EventBus.interaction_available.emit("Betreten: Bäckerei Morgenbrot")
			if w.crime:
				w.crime.wanted = 2
		"phone":
			ui.open_phone()
		"phone_status", "phone_jobs", "phone_housing", "phone_bank", "phone_news", "phone_rules", "phone_inventory", \
				"phone_police", "phone_gang", "phone_politics", "phone_education":
			ui.open_phone(c.substr(6))
		"map":
			ui.open_map()
		"map_route":
			var m = ui.open_map()
			var tree2 := ui.get_tree()
			await tree2.process_frame
			for i in m._markers.size():
				if m._markers[i].cat == "food":
					UIMapData.set_route(w, UIMapData.player_pos(tree2), m._markers[i].pos, m._markers[i].name, false)
					m._select(i)
					break
		"pause":
			ui._open_pause()
		"shop":
			for b in w.city.buildings:
				if b.business_type in ["supermarket", "bakery", "grocery"]:
					var biz: int = w.economy.business_for_building(b.id)
					if biz >= 0:
						ui._open_shop(biz, b.name, w.economy.hours_text(w.economy.get_business(biz)), b.id)
						break
		"death":
			ui._on_died("Herzversagen")
	return 40
