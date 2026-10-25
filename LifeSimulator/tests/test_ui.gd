extends RefCounted
## Tests der Benutzeroberflaeche: Theme, Emoji-Freiheit, Einstellungen,
## KI-Schluessel, Menues und Spieloberflaeche (Smartphone, Karte) mit
## einer kleinen echten Welt.

var runner

const SCAN_DIRS := ["res://scripts/ui", "res://scenes/menu", "res://scenes/ui", "res://scripts/ai"]


func test_theme_variations() -> void:
	var t: Theme = load(UITheme.THEME_PATH)
	runner.check(t != null, "theme.tres ladbar")
	for v in ["HeaderLabel", "TitleLabel", "SubtitleLabel", "CaptionLabel", "ValueLabel", "CardPanel", "GlassPanel",
			"AccentButton", "GhostButton", "ChipButton", "DangerButton"]:
		runner.check(t.is_type_variation(v, UITheme.VARIATIONS[v]), "Typvariante fehlt: " + v)
	runner.check(t.has_stylebox("panel", "TooltipPanel"), "Tooltip-Stil")
	runner.check(t.default_font != null, "Standardschrift")


func test_icons_present() -> void:
	var n := 0
	for f in DirAccess.get_files_at("res://assets/icons"):
		if f.ends_with(".svg"):
			n += 1
	runner.check(n >= 60, "Mindestens 60 Icons (gefunden %d)" % n)
	for name in ["money", "hospital", "police", "bus", "map", "phone", "gear", "star", "route"]:
		runner.check(UIKit.has_icon(name), "Icon fehlt: " + name)


func test_no_emoji() -> void:
	var bad := []
	for d in SCAN_DIRS:
		_scan(d, bad)
	runner.check(bad.is_empty(), "Emoji gefunden: %s" % str(bad))


func _scan(dir: String, bad: Array) -> void:
	if not DirAccess.dir_exists_absolute(dir):
		return
	for f in DirAccess.get_files_at(dir):
		if not (f.ends_with(".gd") or f.ends_with(".tscn")):
			continue
		var txt := FileAccess.get_file_as_string(dir + "/" + f)
		for i in txt.length():
			var c := txt.unicode_at(i)
			if (c >= 0x1F000 and c <= 0x1FAFF) or (c >= 0x2600 and c <= 0x27BF):
				bad.append("%s/%s" % [dir, f])
				break
	for sub in DirAccess.get_directories_at(dir):
		_scan(dir + "/" + sub, bad)


func test_formatting() -> void:
	runner.check_eq(UIKit.money(1234.5), "1.234,50 €", "Geldformat")
	runner.check_eq(UIKit.money(-5.0, 0), "-5 €", "Negativ")
	runner.check_eq(UIKit.group(1234567), "1.234.567", "Tausender")
	runner.check_eq(UIKit.play_time(3725.0), "1:02 Std.", "Spielzeit")
	runner.check_eq(UIKit.life_stage_for_age(16.0), "teen", "Lebensphase")


func test_settings_roundtrip() -> void:
	var old = Settings.get_value("audio", "music")
	Settings.set_value("audio", "music", 0.33)
	var cfg := ConfigFile.new()
	runner.check_eq(cfg.load(Settings.PATH), OK, "settings.cfg lesbar")
	runner.check_near(float(cfg.get_value("audio", "music", -1.0)), 0.33, 0.001, "Wert gespeichert")
	Settings.set_value("audio", "music", old)
	# Konflikterkennung der Tastenbelegung: Standard hat keine echten Konflikte
	runner.check(UISettingsPanel.find_conflicts().is_empty(), "Standardbelegung ohne Konflikte: %s" % str(UISettingsPanel.find_conflicts()))


func test_ai_config() -> void:
	if AIConfig.key_source() == "file":
		return  # vorhandenen Schluessel des Nutzers nicht anfassen
	runner.check(not AIConfig.set_key("kurz"), "Ungueltiger Schluessel abgelehnt")
	runner.check(AIConfig.set_key("gsk_TESTKEY_0123456789abcdef"), "Schluessel speichern")
	AIConfig.reset_cache()
	runner.check(AIConfig.has_key(), "Schluessel vorhanden")
	runner.check(not AIConfig.masked_key().contains("TESTKEY"), "Maskiert")
	runner.check(not FileAccess.get_file_as_string(AIConfig.PATH).contains("TESTKEY"), "Nicht im Klartext")
	AIConfig.clear_key()
	runner.check(AIConfig.key_source() != "file", "Schluessel entfernt")


func test_main_menu_screens() -> void:
	var menu: Control = load("res://scenes/menu/main_menu.tscn").instantiate()
	runner.add_child(menu)
	await runner.get_tree().process_frame
	for s in ["load", "settings", "controls", "credits"]:
		var scr = menu.open_screen(s, null, false)
		runner.check(scr != null, "Bildschirm %s" % s)
		await runner.get_tree().process_frame
	var sp = menu.open_screen("settings", "graphics", false)
	for tab in ["graphics", "audio", "controls", "gameplay", "ai"]:
		sp.panel.show_tab(tab)
	var w = menu.open_screen("new_game", "mode", false)
	await runner.get_tree().process_frame
	w.mode = "sandbox"
	for i in 5:
		w.go_to(i)
		await runner.get_tree().process_frame
	var cfg: Dictionary = w.build_config()
	for k in ["seed", "mode", "rules", "character", "start"]:
		runner.check(cfg.has(k), "Konfiguration enthaelt %s" % k)
	runner.check_eq(cfg.mode, "sandbox", "Modus")
	runner.check(cfg.rules.has("city_size") and cfg.rules.has("population"), "Stadtregeln")
	runner.check(str(cfg.character.get("name", "")) != "", "Charaktername")
	runner.check(cfg.start.has("situation"), "Startsituation")
	menu.queue_free()
	var ls: Control = load("res://scenes/menu/loading_screen.tscn").instantiate()
	runner.add_child(ls)
	ls.call("_on_progress", 0.5, "Test")
	await runner.get_tree().process_frame
	ls.queue_free()


func test_game_ui_with_world() -> void:
	var w := WorldState.create_new({"seed": 5, "mode": "life", "rules": {"city_size": "small", "population": 400},
		"character": {"name": "Test Person", "body": "female", "age": 25.0, "appearance": {}}, "start": {"situation": "auto"}})
	runner.check(w != null and w.city != null, "Welt erzeugt")
	Game.world = w
	var ui: Control = load("res://scenes/ui/game_ui.tscn").instantiate()
	runner.add_child(ui)
	await runner.get_tree().process_frame
	await runner.get_tree().process_frame
	var ph = ui.open_phone()
	runner.check(ph != null, "Smartphone oeffnet")
	for a in UIPhone.APPS:
		if a[0] != "map":
			ph.show_app(a[0])
			await runner.get_tree().process_frame
	var m = ui.open_map()
	runner.check(m != null, "Karte oeffnet")
	UIMapData.texture_now(w)
	await runner.get_tree().process_frame
	runner.check(UIMapData.markers(w).size() > 10, "Kartenmarkierungen")
	var b: Dictionary = w.city.buildings[0]
	var start: Vector2 = w.city.buildings[w.city.buildings.size() - 1].sidewalk_point
	UIMapData.set_route(w, start, b.sidewalk_point, "Test", false)
	runner.check(UIMapData.route.size() >= 2 and UIMapData.route_length > 0.0, "Route berechnet")
	m._select(0)
	ui._close_overlay()
	runner.check_eq(Game.paused_by_ui, 0, "Pause-Zaehler zurueckgesetzt")
	EventBus.notify.emit("Testmeldung", "success")
	await runner.get_tree().process_frame
	UIMapData.clear_route()
	ui.queue_free()
	Game.world = null
	await runner.get_tree().process_frame
