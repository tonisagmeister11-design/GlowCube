extends Control
## Start screen: logo, SPIEL STARTEN / FORTSETZEN / LADEN / EINSTELLUNGEN / BEENDEN.
## No music is played (the player can add their own in Audio/Music).

var _box: VBoxContainer
var _sub: Control


func _ready() -> void:
	Game.state = Game.State.MENU
	get_tree().paused = false
	Engine.time_scale = 1.0
	Input.mouse_mode = Input.MOUSE_MODE_VISIBLE
	set_anchors_preset(Control.PRESET_FULL_RECT)
	var bg := ColorRect.new()
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	var mat := ShaderMaterial.new()
	mat.shader = load("res://ui/shaders/menu_bg.gdshader")
	bg.material = mat
	add_child(bg)
	var logo := preload("res://ui/logo.gd").new()
	logo.anchor_left = 0.5
	logo.anchor_right = 0.5
	logo.offset_left = -330
	logo.offset_right = 330
	logo.offset_top = 70
	logo.offset_bottom = 300
	add_child(logo)
	_box = VBoxContainer.new()
	_box.anchor_left = 0.5
	_box.anchor_right = 0.5
	_box.anchor_top = 0.5
	_box.anchor_bottom = 0.5
	_box.offset_left = -220
	_box.offset_right = 220
	_box.offset_top = -40
	_box.add_theme_constant_override("separation", 10)
	add_child(_box)
	var has_save := SaveManager.latest_slot() != -99
	_button("SPIEL STARTEN", _new_game)
	var cont := _button("FORTSETZEN", func(): Game.continue_game())
	cont.disabled = not has_save
	var load_b := _button("LADEN", _load)
	load_b.disabled = not has_save
	_button("EINSTELLUNGEN", _settings)
	_button("BEENDEN", func(): Game.quit())
	var ver := Label.new()
	ver.text = "Harbor Heat · v%s · Eigene Stadt, eigene Figuren, eigene Fahrzeuge" % ProjectSettings.get_setting("application/config/version", "1.0")
	ver.anchor_top = 1.0
	ver.anchor_bottom = 1.0
	ver.offset_top = -40
	ver.offset_left = 24
	ver.add_theme_color_override("font_color", Color(1, 1, 1, 0.6))
	add_child(ver)
	(_box.get_child(1 if has_save else 0) as Button).call_deferred("grab_focus")


func _button(text: String, cb: Callable) -> Button:
	var b := Button.new()
	b.text = text
	b.custom_minimum_size = Vector2(440, 56)
	b.add_theme_font_size_override("font_size", 26)
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.05, 0.06, 0.1, 0.72)
	sb.set_corner_radius_all(6)
	var sbh := sb.duplicate() as StyleBoxFlat
	sbh.bg_color = Color(0.95, 0.6, 0.25, 0.9)
	b.add_theme_stylebox_override("normal", sb)
	b.add_theme_stylebox_override("hover", sbh)
	b.add_theme_stylebox_override("focus", sbh)
	b.add_theme_stylebox_override("pressed", sbh)
	b.pressed.connect(func(): AudioManager.play_ui("select"); cb.call())
	b.mouse_entered.connect(func(): AudioManager.play_ui("hover", -12.0))
	_box.add_child(b)
	return b


func _new_game() -> void:
	Game.new_game()


func _load() -> void:
	var m := SaveLoadMenu.new()
	m.mode = "load"
	_open_sub(m)


func _settings() -> void:
	_open_sub(SettingsMenu.new())


func _open_sub(c: Control) -> void:
	_box.visible = false
	_sub = c
	c.connect("back", func():
		_box.visible = true
		(_box.get_child(0) as Button).grab_focus())
	add_child(c)
