class_name SettingsMenu
extends Control
## Settings screen (used by the main menu and the pause menu). Every change is
## applied immediately; "Zurück" writes Config/settings.cfg.

signal back

const RESOLUTIONS := [Vector2i(1280, 720), Vector2i(1366, 768), Vector2i(1600, 900), Vector2i(1920, 1080),
	Vector2i(2560, 1440), Vector2i(3840, 2160)]


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	set_anchors_preset(Control.PRESET_FULL_RECT)
	var dim := ColorRect.new()
	dim.color = Color(0, 0, 0, 0.75)
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(dim)
	var panel := PanelContainer.new()
	panel.set_anchors_preset(Control.PRESET_CENTER)
	panel.custom_minimum_size = Vector2(980, 700)
	panel.position = -panel.custom_minimum_size * 0.5
	panel.anchor_left = 0.5
	panel.anchor_top = 0.5
	panel.anchor_right = 0.5
	panel.anchor_bottom = 0.5
	panel.offset_left = -490
	panel.offset_top = -350
	panel.offset_right = 490
	panel.offset_bottom = 350
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.05, 0.06, 0.08, 0.96)
	sb.border_color = Color(0.95, 0.75, 0.25)
	sb.border_width_top = 5
	sb.set_content_margin_all(24)
	panel.add_theme_stylebox_override("panel", sb)
	add_child(panel)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 12)
	panel.add_child(v)
	var title := Label.new()
	title.text = "EINSTELLUNGEN"
	title.add_theme_font_size_override("font_size", 36)
	v.add_child(title)
	var tabs := TabContainer.new()
	tabs.size_flags_vertical = Control.SIZE_EXPAND_FILL
	v.add_child(tabs)
	tabs.add_child(_graphics_tab())
	tabs.add_child(_audio_tab())
	tabs.add_child(_controls_tab())
	tabs.add_child(_gameplay_tab())
	var row := HBoxContainer.new()
	row.alignment = BoxContainer.ALIGNMENT_END
	v.add_child(row)
	var reset := Button.new()
	reset.text = "Standard wiederherstellen"
	reset.pressed.connect(_reset)
	row.add_child(reset)
	var b := Button.new()
	b.text = "Zurück"
	b.custom_minimum_size = Vector2(180, 44)
	b.pressed.connect(_close)
	row.add_child(b)
	b.call_deferred("grab_focus")


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel"):
		get_viewport().set_input_as_handled()
		_close()


func _close() -> void:
	Settings.save_settings()
	AudioManager.play_ui("back")
	back.emit()
	queue_free()


func _reset() -> void:
	Settings.data = Settings.DEFAULTS.duplicate(true)
	Settings.apply_all()
	Settings.save_settings()
	var p := get_parent()
	var again := SettingsMenu.new()
	again.back.connect(func(): back.emit())
	p.add_child(again)
	queue_free()


# ------------------------------------------------------------------ tabs
func _grid(tab_name: String) -> GridContainer:
	var sc := ScrollContainer.new()
	sc.name = tab_name
	var g := GridContainer.new()
	g.columns = 2
	g.add_theme_constant_override("h_separation", 40)
	g.add_theme_constant_override("v_separation", 10)
	g.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	sc.add_child(g)
	return g


func _row_label(g: GridContainer, text: String) -> void:
	var l := Label.new()
	l.text = text
	l.custom_minimum_size = Vector2(360, 0)
	l.add_theme_font_size_override("font_size", 20)
	g.add_child(l)


func _option(g: GridContainer, text: String, section: String, key: String, opts: Array) -> void:
	_row_label(g, text)
	var o := OptionButton.new()
	for s in opts:
		o.add_item(s)
	o.custom_minimum_size = Vector2(420, 38)
	o.selected = clampi(int(Settings.get_value(section, key)), 0, opts.size() - 1)
	o.item_selected.connect(func(i): Settings.set_value(section, key, i))
	g.add_child(o)


func _check(g: GridContainer, text: String, section: String, key: String) -> void:
	_row_label(g, text)
	var c := CheckButton.new()
	c.button_pressed = bool(Settings.get_value(section, key))
	c.toggled.connect(func(on): Settings.set_value(section, key, on))
	g.add_child(c)


func _slider(g: GridContainer, text: String, section: String, key: String, lo := 0.0, hi := 1.0, step := 0.05) -> void:
	_row_label(g, text)
	var h := HBoxContainer.new()
	var s := HSlider.new()
	s.min_value = lo
	s.max_value = hi
	s.step = step
	s.value = float(Settings.get_value(section, key))
	s.custom_minimum_size = Vector2(340, 32)
	var val := Label.new()
	val.custom_minimum_size = Vector2(70, 0)
	val.text = _fmt(s.value, hi)
	s.value_changed.connect(func(x):
		val.text = _fmt(x, hi)
		Settings.set_value(section, key, x))
	h.add_child(s)
	h.add_child(val)
	g.add_child(h)


func _fmt(x: float, hi: float) -> String:
	return "%d%%" % int(round(x * 100.0)) if hi <= 1.0 else ("%.2f" % x if hi <= 5.0 else str(int(x)))


func _graphics_tab() -> Control:
	var g := _grid("Grafik")
	_row_label(g, "Auflösung")
	var o := OptionButton.new()
	var cur: Vector2i = Settings.get_value("graphics", "resolution")
	for i in RESOLUTIONS.size():
		var r: Vector2i = RESOLUTIONS[i]
		o.add_item("%d × %d" % [r.x, r.y])
		if r == cur:
			o.selected = i
	o.item_selected.connect(func(i): Settings.set_value("graphics", "resolution", RESOLUTIONS[i]))
	o.custom_minimum_size = Vector2(420, 38)
	g.add_child(o)
	_option(g, "Anzeigemodus", "graphics", "window_mode", ["Vollbild", "Fenster", "Randloses Fenster"])
	_check(g, "V-Sync", "graphics", "vsync")
	_option(g, "Grafikqualität", "graphics", "quality", ["Niedrig", "Mittel", "Hoch", "Ultra"])
	_option(g, "Texturqualität", "graphics", "texture_quality", ["Niedrig", "Mittel", "Hoch"])
	_option(g, "Schatten", "graphics", "shadow_quality", ["Niedrig", "Mittel", "Hoch", "Ultra"])
	_option(g, "Sichtweite (nach Neustart der Welt)", "graphics", "view_distance", ["Nah", "Normal", "Weit"])
	_option(g, "Effekte (SSAO, Reflexionen)", "graphics", "effects", ["Niedrig", "Mittel", "Hoch"])
	_option(g, "Kantenglättung", "graphics", "anti_aliasing", ["Aus", "FXAA", "TAA", "MSAA 4x"])
	_row_label(g, "FPS-Limit")
	var f := OptionButton.new()
	var limits := [0, 30, 60, 120, 144]
	for l in limits:
		f.add_item("Unbegrenzt" if l == 0 else str(l))
	f.selected = maxi(0, limits.find(int(Settings.get_value("graphics", "fps_limit"))))
	f.item_selected.connect(func(i): Settings.set_value("graphics", "fps_limit", limits[i]))
	g.add_child(f)
	_slider(g, "Sichtfeld (FOV)", "graphics", "fov", 55.0, 95.0, 1.0)
	return g.get_parent()


func _audio_tab() -> Control:
	var g := _grid("Audio")
	_slider(g, "Gesamtlautstärke", "audio", "master")
	_slider(g, "Effekte", "audio", "sfx")
	_slider(g, "Waffen", "audio", "weapons")
	_slider(g, "Fahrzeuge", "audio", "vehicles")
	_slider(g, "Umgebung", "audio", "environment")
	_slider(g, "Stimmen", "audio", "voice")
	_slider(g, "Oberfläche", "audio", "ui")
	_check(g, "Musik aktivieren (eigene Dateien in Audio/Music)", "audio", "music_enabled")
	_slider(g, "Musik", "audio", "music")
	var hint := Label.new()
	hint.text = "Das Spiel enthält keine Musik. Lege eigene .ogg/.mp3/.wav-Dateien\nin den Ordner Audio/Music neben StartGame.exe."
	hint.add_theme_color_override("font_color", Color(0.7, 0.7, 0.75))
	g.add_child(hint)
	return g.get_parent()


func _controls_tab() -> Control:
	var g := _grid("Steuerung")
	_slider(g, "Mausempfindlichkeit", "controls", "mouse_sensitivity", 0.2, 3.0, 0.05)
	_check(g, "Maus Y invertieren", "controls", "invert_y")
	_check(g, "Controller aktiviert", "controls", "controller_enabled")
	_slider(g, "Controller-Empfindlichkeit", "controls", "controller_sensitivity", 0.2, 3.0, 0.05)
	_check(g, "Vibration", "controls", "vibration")
	_row_label(g, "Tastenbelegung")
	var t := Label.new()
	t.text = ControlsHelp.TEXT
	t.add_theme_font_size_override("font_size", 16)
	g.add_child(t)
	return g.get_parent()


func _gameplay_tab() -> Control:
	var g := _grid("Spiel")
	_check(g, "Untertitel", "gameplay", "subtitles")
	_check(g, "Radar dreht mit der Kamera", "gameplay", "minimap_rotate")
	_option(g, "Geschwindigkeit", "gameplay", "speed_units", ["km/h", "mph"])
	_slider(g, "HUD-Größe", "gameplay", "hud_scale", 0.7, 1.4, 0.05)
	return g.get_parent()
