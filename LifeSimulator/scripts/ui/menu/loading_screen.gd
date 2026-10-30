extends Control
## Ladebildschirm: Titelbild, Fortschrittsbalken (Game.loading_progress),
## Statuszeile und wechselnde Spieltipps.

const IMAGE := "res://assets/branding/splash.png"
const TIPS := [
	"Halte deine Bedürfnisse im Blick: Hunger, Durst und Energie sinken mit der Zeit.",
	"Über das Smartphone (%s) findest du Jobs, Wohnungen, dein Konto und Nachrichten.",
	"Die Stadtkarte (%s) zeigt Geschäfte, Haltestellen und plant Fußwege zu deinem Ziel.",
	"Mit %s und %s beschleunigst oder verlangsamst du die Zeit.",
	"Märkte und Discounter sind günstiger als Kioske – vergleiche Preise.",
	"Miete wird jede Woche fällig. Rückstände führen zur Kündigung.",
	"Gute Ausbildung öffnet besser bezahlte Berufe. Schule und Studium lohnen sich.",
	"Busse verbinden alle Stadtteile. Die Linien siehst du auf der Stadtkarte.",
	"Freundschaften pflegen hebt die Stimmung – sprich mit Menschen in der Stadt.",
	"Wer gegen Gesetze verstößt, wird gesucht. Die Polizei vergisst nicht so schnell.",
	"Jeder Stadtteil hat seinen Charakter: Mieten, Preise und Kriminalität unterscheiden sich.",
	"Das Spiel speichert automatisch. Mit %s speicherst du jederzeit schnell.",
]

var _bar: ProgressBar
var _status: Label
var _pct: Label
var _tip: Label
var _target := 0.05
var _shown := 0.0
var _tip_index := 0
var _tip_timer := 0.0
var _t := 0.0
var _bg: TextureRect
var _spinner: Control


func _ready() -> void:
	set_anchors_preset(PRESET_FULL_RECT)
	Input.mouse_mode = Input.MOUSE_MODE_VISIBLE
	var dark := ColorRect.new()
	dark.color = UITheme.BG
	dark.set_anchors_preset(PRESET_FULL_RECT)
	add_child(dark)
	_bg = TextureRect.new()
	_bg.texture = load(IMAGE)
	_bg.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_bg.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
	_bg.set_anchors_preset(PRESET_FULL_RECT)
	_bg.pivot_offset = Vector2(960, 540)
	add_child(_bg)
	var bottom := UIKit.gradient_rect(
		PackedColorArray([Color(0.01, 0.02, 0.05, 0.0), Color(0.01, 0.02, 0.05, 0.86), Color(0.01, 0.02, 0.05, 0.96)]),
		PackedFloat32Array([0.0, 0.55, 1.0]), Vector2(0, 0), Vector2(0, 1))
	bottom.anchor_top = 0.62
	bottom.anchor_right = 1.0
	bottom.anchor_bottom = 1.0
	add_child(bottom)
	# Unterer Bereich: Tipp links, Fortschritt rechts
	var panel := MarginContainer.new()
	panel.anchor_top = 1.0
	panel.anchor_right = 1.0
	panel.anchor_bottom = 1.0
	panel.offset_top = -250
	panel.add_theme_constant_override("margin_left", 110)
	panel.add_theme_constant_override("margin_right", 110)
	panel.add_theme_constant_override("margin_bottom", 70)
	add_child(panel)
	var row := UIKit.hbox(60)
	row.alignment = BoxContainer.ALIGNMENT_END
	panel.add_child(row)
	var tipbox := UIKit.vbox(8)
	tipbox.size_flags_horizontal = SIZE_EXPAND_FILL
	tipbox.size_flags_vertical = SIZE_SHRINK_END
	var th := UIKit.hbox(10)
	th.add_child(UIKit.icon_rect("sparkle", 20, UITheme.WARM))
	var tt := UIKit.eyebrow("Tipp")
	tt.add_theme_color_override("font_color", UITheme.WARM)
	th.add_child(tt)
	tipbox.add_child(th)
	_tip = UIKit.label("", "", true)
	_tip.add_theme_font_override("font", UITheme.font(500))
	_tip.add_theme_font_size_override("font_size", 22)
	_tip.custom_minimum_size.x = 700
	tipbox.add_child(_tip)
	row.add_child(tipbox)
	var prog := UIKit.vbox(10)
	prog.custom_minimum_size.x = 620
	prog.size_flags_vertical = SIZE_SHRINK_END
	var sh := UIKit.hbox(12)
	_spinner = Control.new()
	_spinner.custom_minimum_size = Vector2(22, 22)
	_spinner.draw.connect(_draw_spinner)
	sh.add_child(_spinner)
	_status = UIKit.label("Spiel wird vorbereitet ...", "")
	_status.add_theme_font_override("font", UITheme.font(600))
	_status.size_flags_horizontal = SIZE_EXPAND_FILL
	sh.add_child(_status)
	_pct = UIKit.label("0 %", "ValueLabel")
	sh.add_child(_pct)
	prog.add_child(sh)
	_bar = ProgressBar.new()
	_bar.show_percentage = false
	_bar.max_value = 1.0
	_bar.custom_minimum_size.y = 8
	var f := StyleBoxFlat.new()
	f.bg_color = UITheme.ACCENT
	f.set_corner_radius_all(999)
	f.shadow_color = Color(UITheme.ACCENT, 0.5)
	f.shadow_size = 8
	_bar.add_theme_stylebox_override("fill", f)
	prog.add_child(_bar)
	prog.add_child(UIKit.label("Havenbrück wird für dich aufgebaut", "CaptionLabel"))
	row.add_child(prog)
	var rng := RandomNumberGenerator.new()
	rng.randomize()
	_tip_index = rng.randi() % TIPS.size()
	_show_tip()
	if not Game.loading_progress.is_connected(_on_progress):
		Game.loading_progress.connect(_on_progress)
	modulate.a = 0.0
	create_tween().tween_property(self, "modulate:a", 1.0, 0.4)


func _on_progress(fraction: float, text: String) -> void:
	_target = clampf(fraction, 0.0, 1.0)
	if text != "":
		_status.text = text


func _process(delta: float) -> void:
	_t += delta
	# Sanft auffuellen; zwischen gemeldeten Stufen langsam weiterkriechen
	var creep := minf(_target + 0.12, 0.97)
	if _shown < _target:
		_shown = move_toward(_shown, _target, delta * 0.6)
	elif _shown < creep:
		_shown = move_toward(_shown, creep, delta * 0.012)
	_bar.value = _shown
	_pct.text = "%d %%" % int(_shown * 100.0)
	var s := 1.04 + 0.02 * sin(_t * 0.15)
	_bg.pivot_offset = _bg.size * 0.5
	_bg.scale = Vector2(s, s)
	_tip_timer += delta
	if _tip_timer > 7.0:
		_tip_timer = 0.0
		_tip_index = (_tip_index + 1) % TIPS.size()
		var tw := create_tween()
		tw.tween_property(_tip, "modulate:a", 0.0, 0.3)
		tw.tween_callback(_show_tip)
		tw.tween_property(_tip, "modulate:a", 1.0, 0.4)
	_spinner.queue_redraw()


func _show_tip() -> void:
	var t: String = TIPS[_tip_index]
	var keys := {
		1: [Settings.binding_label("phone")], 2: [Settings.binding_label("map")],
		3: [Settings.binding_label("time_faster"), Settings.binding_label("time_slower")],
		11: [Settings.binding_label("quick_save")],
	}
	if keys.has(_tip_index):
		var args: Array = []
		for k in keys[_tip_index]:
			args.append("„%s“" % UIKit._key_text(k))
		t = t % args
	_tip.text = t


func _draw_spinner() -> void:
	var c := _spinner.size * 0.5
	var r := 9.0
	_spinner.draw_arc(c, r, 0, TAU, 32, Color(1, 1, 1, 0.15), 2.5, true)
	var a := _t * 5.0
	_spinner.draw_arc(c, r, a, a + 1.8, 24, UITheme.ACCENT, 2.5, true)
