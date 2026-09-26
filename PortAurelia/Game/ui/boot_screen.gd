extends Control
## Intro loading screen: the cover artwork with a loading bar that fills over 10 seconds,
## then the main menu. (Shaders and menu resources are warmed up meanwhile.)

const DURATION := 10.0
const MENU := "res://scenes/main_menu.tscn"
const STEPS := ["Initializing engine", "Loading Port Aurelia", "Preparing vehicles", "Loading weapons",
	"Waking up the city", "Almost there"]

var _t := 0.0
var _bar: ProgressBar
var _label: Label
var _done := false


func _ready() -> void:
	Input.mouse_mode = Input.MOUSE_MODE_HIDDEN
	set_anchors_preset(Control.PRESET_FULL_RECT)
	var bg := ColorRect.new()
	bg.color = Color.BLACK
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(bg)
	CoverArt.add_background(self, 0.85)
	var box := VBoxContainer.new()
	box.anchor_left = 0.12
	box.anchor_right = 0.88
	box.anchor_top = 1.0
	box.anchor_bottom = 1.0
	box.offset_top = -110
	box.offset_bottom = -40
	box.add_theme_constant_override("separation", 10)
	add_child(box)
	_label = Label.new()
	_label.add_theme_font_size_override("font_size", 20)
	_label.add_theme_color_override("font_color", Color(1, 1, 1, 0.9))
	_label.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.8))
	box.add_child(_label)
	_bar = ProgressBar.new()
	_bar.custom_minimum_size = Vector2(0, 12)
	_bar.show_percentage = false
	var fill := StyleBoxFlat.new()
	fill.bg_color = Color(1.0, 0.16, 0.5)
	fill.set_corner_radius_all(6)
	var back := StyleBoxFlat.new()
	back.bg_color = Color(1, 1, 1, 0.15)
	back.set_corner_radius_all(6)
	_bar.add_theme_stylebox_override("fill", fill)
	_bar.add_theme_stylebox_override("background", back)
	box.add_child(_bar)
	modulate.a = 0.0
	create_tween().tween_property(self, "modulate:a", 1.0, 0.8)
	ResourceLoader.load_threaded_request(MENU)


func _process(delta: float) -> void:
	_t += delta
	var p := clampf(_t / DURATION, 0.0, 1.0)
	# eased so it feels like real loading (quick start, small pauses, steady finish)
	var shown := p + sin(p * TAU * 3.0) * 0.015 * (1.0 - p)
	_bar.value = clampf(shown, 0.0, 1.0) * 100.0
	var dots := ".".repeat(int(_t * 2.0) % 4)
	_label.text = "%s%s   %d%%" % [STEPS[mini(int(p * STEPS.size()), STEPS.size() - 1)], dots, int(p * 100.0)]
	if p >= 1.0 and not _done:
		_done = true
		_to_menu()


func _to_menu() -> void:
	# the smoke test / a direct new game may already have left the boot screen
	if Game.state != Game.State.MENU or get_tree().current_scene != self:
		return
	var tw := create_tween()
	tw.tween_property(self, "modulate:a", 0.0, 0.5)
	tw.tween_callback(func():
		var packed: PackedScene = ResourceLoader.load_threaded_get(MENU)
		get_tree().change_scene_to_packed(packed))
