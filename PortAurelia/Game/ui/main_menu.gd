extends Control
## Start screen on the HARBOR HEAT cover: CONTINUE / START GAME / FREE ROAM / MISSIONS /
## LOAD GAME / SETTINGS / QUIT. No music is played (players can add their own in Audio/Music).

const STORY_EN := {"m01": "A New Start", "m02": "Special Delivery", "m03": "Debt Collector", "m04": "The Chase",
	"m05": "Escort", "m06": "The Hit", "m07": "Pre-Owned", "m08": "The Big Score"}
const SIDE_EN := {"taxi": "Taxi Shift", "courier": "Courier Run", "vigilante": "Vigilante", "race": "Street Race"}

var _box: VBoxContainer
var _missions: Control
var _sub: Control


func _ready() -> void:
	Game.state = Game.State.MENU
	get_tree().paused = false
	Engine.time_scale = 1.0
	Input.mouse_mode = Input.MOUSE_MODE_VISIBLE
	set_anchors_preset(Control.PRESET_FULL_RECT)
	var black := ColorRect.new()
	black.color = Color.BLACK
	black.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(black)
	CoverArt.add_background(self, 0.7, 0.72)
	_box = _column()
	var has_save := SaveManager.latest_slot() != -99
	if has_save:
		_button(_box, "CONTINUE", func(): Game.continue_game())
	_button(_box, "START GAME", func(): Game.new_game())
	_button(_box, "FREE ROAM", func(): Game.start_free_roam())
	_button(_box, "MISSIONS", _show_missions)
	var load_b := _button(_box, "LOAD GAME", _load)
	load_b.disabled = not has_save
	_button(_box, "SETTINGS", _settings)
	_button(_box, "QUIT", func(): Game.quit())
	_missions = _mission_list()
	_missions.visible = false
	var ver := Label.new()
	ver.text = "HARBOR HEAT  ·  v%s" % ProjectSettings.get_setting("application/config/version", "1.0")
	ver.anchor_left = 1.0
	ver.anchor_right = 1.0
	ver.anchor_top = 1.0
	ver.anchor_bottom = 1.0
	ver.offset_left = -300
	ver.offset_right = -24
	ver.offset_top = -40
	ver.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	ver.add_theme_color_override("font_color", Color(1, 1, 1, 0.6))
	add_child(ver)
	modulate.a = 0.0
	create_tween().tween_property(self, "modulate:a", 1.0, 0.6)
	(_box.get_child(0) as Button).call_deferred("grab_focus")


func _column() -> VBoxContainer:
	var b := VBoxContainer.new()
	b.anchor_top = 1.0
	b.anchor_bottom = 1.0
	b.offset_left = 60
	b.offset_right = 480
	b.grow_vertical = Control.GROW_DIRECTION_BEGIN
	b.offset_top = -80
	b.offset_bottom = -60
	b.add_theme_constant_override("separation", 8)
	add_child(b)
	return b


func _button(parent: Container, text: String, cb: Callable, small := false) -> Button:
	var b := Button.new()
	b.text = "  " + text
	b.alignment = HORIZONTAL_ALIGNMENT_LEFT
	b.custom_minimum_size = Vector2(420, 40 if small else 52)
	b.add_theme_font_size_override("font_size", 19 if small else 26)
	b.add_theme_color_override("font_color", Color(1, 1, 1))
	b.add_theme_color_override("font_hover_color", Color(1, 1, 1))
	b.add_theme_color_override("font_focus_color", Color(1, 1, 1))
	b.add_theme_color_override("font_disabled_color", Color(1, 1, 1, 0.3))
	b.add_theme_color_override("font_outline_color", Color(0, 0, 0, 0.8))
	b.add_theme_constant_override("outline_size", 4)
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.02, 0.02, 0.05, 0.55)
	sb.border_width_left = 4
	sb.border_color = Color(1, 1, 1, 0.25)
	sb.set_corner_radius_all(3)
	var sbh := sb.duplicate() as StyleBoxFlat
	sbh.bg_color = Color(1.0, 0.12, 0.45, 0.88)
	sbh.border_color = Color(1, 1, 1, 0.95)
	b.add_theme_stylebox_override("normal", sb)
	b.add_theme_stylebox_override("disabled", sb)
	b.add_theme_stylebox_override("hover", sbh)
	b.add_theme_stylebox_override("focus", sbh)
	b.add_theme_stylebox_override("pressed", sbh)
	b.pressed.connect(func(): AudioManager.play_ui("select"); cb.call())
	b.mouse_entered.connect(func(): AudioManager.play_ui("hover", -12.0); b.grab_focus())
	parent.add_child(b)
	return b


func _mission_list() -> Control:
	var col := _column()
	var head := Label.new()
	head.text = "MISSIONS"
	head.add_theme_font_size_override("font_size", 34)
	head.add_theme_color_override("font_color", Color(1.0, 0.2, 0.55))
	head.add_theme_color_override("font_outline_color", Color(0, 0, 0))
	head.add_theme_constant_override("outline_size", 6)
	col.add_child(head)
	var done := {}
	var latest := SaveManager.latest_slot()
	if latest != -99:
		var sp := SaveManager.load_slot(latest)
		if sp:
			done = sp.missions
	for m in MissionManager.STORY:
		var mid: String = m["id"]
		var mark := "✔ " if done.get(mid, "") == "done" else ""
		_button(col, "%s%s. %s" % [mark, mid.substr(1).to_int(), STORY_EN.get(mid, m["title"])],
			func(): Game.play_mission(mid), true)
	var side := Label.new()
	side.text = "SIDE JOBS"
	side.add_theme_font_size_override("font_size", 22)
	side.add_theme_color_override("font_color", Color(1, 1, 1, 0.8))
	col.add_child(side)
	for k in MissionManager.SIDE:
		var key: String = k
		_button(col, SIDE_EN.get(key, key), func(): Game.play_mission(key), true)
	_button(col, "BACK", _hide_missions, true)
	return col


func _show_missions() -> void:
	_box.visible = false
	_missions.visible = true
	(_missions.get_child(1) as Button).grab_focus()


func _hide_missions() -> void:
	_missions.visible = false
	_box.visible = true
	(_box.get_child(0) as Button).grab_focus()


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel") and _missions.visible:
		get_viewport().set_input_as_handled()
		_hide_missions()


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
