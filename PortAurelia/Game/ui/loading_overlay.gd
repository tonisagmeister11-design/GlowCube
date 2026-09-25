extends CanvasLayer
## Loading screen shown while the world boots: artwork gradient, logo, progress bar,
## current loading step and rotating gameplay tips.

const TIPS := [
	"Tipp: Drücke F in der Nähe eines Autos, um einzusteigen.",
	"Tipp: Mit TAB öffnest du das Waffenrad.",
	"Tipp: Die Polizei sucht dich im markierten Bereich. Bleib außer Sicht, um sie abzuschütteln.",
	"Tipp: In der Werkstatt (M auf dem Radar) kannst du Fahrzeuge reparieren, lackieren und tunen.",
	"Tipp: Mit ↑ öffnest du dein Handy: Kontakte, Jobs, Nachrichten und Statistik.",
	"Tipp: Leg eigene Musik in den Ordner Audio/Music und aktiviere sie in den Einstellungen.",
	"Tipp: Safehouses sind Speicherpunkte. Kaufe Immobilien, um neue freizuschalten.",
	"Tipp: Im Regen verlieren die Reifen an Haftung – bremse früher.",
	"Tipp: Taxi-, Kurier- und Bürgerwehrjobs startest du im Handy, Rennen an den R-Markern.",
	"Tipp: Halte die rechte Maustaste zum Zielen, die linke zum Schießen.",
	"Tipp: Mit Q gehst du hinter Mauern und Autos in Deckung.",
	"Tipp: F5 erstellt eine Schnellspeicherung (nicht während Missionen).",
	"Tipp: 25 Postkarten sind in der Stadt versteckt. Jede bringt $100.",
]

var bar: ProgressBar
var label: Label
var tip: Label
var _tip_timer := 0.0
var _root: Control


func _ready() -> void:
	layer = 100
	process_mode = Node.PROCESS_MODE_ALWAYS
	_root = Control.new()
	_root.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(_root)
	var bg := ColorRect.new()
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	bg.material = ShaderMaterial.new()
	bg.material.shader = load("res://ui/shaders/menu_bg.gdshader")
	_root.add_child(bg)
	var logo := preload("res://ui/logo.gd").new()
	logo.set_anchors_preset(Control.PRESET_CENTER_TOP)
	logo.position = Vector2(-300, 120)
	logo.size = Vector2(600, 200)
	_root.add_child(logo)
	var box := VBoxContainer.new()
	box.set_anchors_preset(Control.PRESET_CENTER_BOTTOM)
	box.offset_left = -420
	box.offset_right = 420
	box.offset_top = -200
	box.offset_bottom = -60
	box.add_theme_constant_override("separation", 12)
	_root.add_child(box)
	label = Label.new()
	label.add_theme_font_size_override("font_size", 20)
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	box.add_child(label)
	bar = ProgressBar.new()
	bar.custom_minimum_size = Vector2(840, 14)
	bar.show_percentage = false
	var fill := StyleBoxFlat.new()
	fill.bg_color = Color(1.0, 0.62, 0.25)
	fill.set_corner_radius_all(7)
	var back := StyleBoxFlat.new()
	back.bg_color = Color(1, 1, 1, 0.12)
	back.set_corner_radius_all(7)
	bar.add_theme_stylebox_override("fill", fill)
	bar.add_theme_stylebox_override("background", back)
	box.add_child(bar)
	tip = Label.new()
	tip.add_theme_font_size_override("font_size", 18)
	tip.add_theme_color_override("font_color", Color(1, 1, 1, 0.75))
	tip.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	tip.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	box.add_child(tip)
	tip.text = TIPS[randi() % TIPS.size()]


func _process(delta: float) -> void:
	_tip_timer += delta
	if _tip_timer > 6.0:
		_tip_timer = 0.0
		tip.text = TIPS[randi() % TIPS.size()]


func set_progress(v: float, text: String) -> void:
	bar.value = v * 100.0
	label.text = "%s  –  %d%%" % [text, int(v * 100.0)]


func finish() -> void:
	var tw := create_tween()
	tw.tween_property(_root, "modulate:a", 0.0, 0.6)
	tw.tween_callback(queue_free)
