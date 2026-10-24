class_name UIModal
extends Control
## Modaler Dialog (Bestaetigung, Hinweis, eigene Inhalte) ueber dem aktuellen
## Bildschirm. Esc bzw. Abbrechen schliesst ihn.
##   var m := UIModal.confirm(self, "Spiel beenden?", "Ungespeicherter Fortschritt geht verloren.", "Beenden", true)
##   m.answered.connect(func(ok): ...)

signal answered(ok: bool)

var card: PanelContainer
var body: VBoxContainer
var buttons: HBoxContainer
var _done := false


static func confirm(host: Node, title: String, text: String, ok_text := "Bestätigen",
		danger := false, icon_name := "warning", cancel_text := "Abbrechen") -> UIModal:
	var m := UIModal.new()
	m._build(title, text, icon_name, UITheme.DANGER if danger else UITheme.ACCENT)
	if cancel_text != "":
		var c := UIKit.button(cancel_text, "", "GhostButton", 140)
		c.pressed.connect(m._answer.bind(false))
		m.buttons.add_child(c)
	var ok := UIKit.button(ok_text, "", "DangerButton" if danger else "AccentButton", 160)
	ok.pressed.connect(m._answer.bind(true))
	m.buttons.add_child(ok)
	host.add_child(m)
	ok.call_deferred("grab_focus")
	return m


## Reiner Hinweis mit einer Schaltflaeche.
static func info(host: Node, title: String, text: String, icon_name := "info") -> UIModal:
	return confirm(host, title, text, "Verstanden", false, icon_name, "")


## Leerer Dialog fuer eigene Inhalte (body/buttons befuellen).
static func custom(host: Node, title: String, icon_name := "info", width := 560.0) -> UIModal:
	var m := UIModal.new()
	m._build(title, "", icon_name, UITheme.ACCENT)
	m.card.custom_minimum_size.x = width
	host.add_child(m)
	return m


func _build(title: String, text: String, icon_name: String, color: Color) -> void:
	set_anchors_preset(PRESET_FULL_RECT)
	mouse_filter = MOUSE_FILTER_STOP
	z_index = 50
	add_child(UIKit.dim(0.62))
	var center := CenterContainer.new()
	center.set_anchors_preset(PRESET_FULL_RECT)
	add_child(center)
	card = PanelContainer.new()
	card.theme_type_variation = "SurfacePanel"
	card.custom_minimum_size = Vector2(520, 0)
	center.add_child(card)
	var v := UIKit.vbox(18)
	card.add_child(UIKit.margin(v, 10, 8, 10, 6))
	var head := UIKit.hbox(16)
	head.add_child(UIKit.icon_badge(icon_name, color, 52, 0.5))
	var t := UIKit.label(title, "TitleLabel", true)
	t.size_flags_horizontal = SIZE_EXPAND_FILL
	head.add_child(t)
	v.add_child(head)
	body = UIKit.vbox(12)
	v.add_child(body)
	if text != "":
		var l := UIKit.label(text, "SubtitleLabel", true)
		l.custom_minimum_size.x = 440
		body.add_child(l)
	buttons = UIKit.hbox(12)
	buttons.alignment = BoxContainer.ALIGNMENT_END
	v.add_child(buttons)


func _ready() -> void:
	card.pivot_offset = card.size * 0.5
	card.scale = Vector2(0.96, 0.96)
	modulate.a = 0.0
	var tw := create_tween().set_parallel(true).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	tw.tween_property(self, "modulate:a", 1.0, 0.18)
	tw.tween_property(card, "scale", Vector2.ONE, 0.22)


func _input(event: InputEvent) -> void:
	# Modale blockieren Spieltasten (pause, phone ...) solange sie offen sind
	if event is InputEventKey and event.pressed and not event.echo and event.keycode == KEY_ESCAPE:
		get_viewport().set_input_as_handled()
		_answer(false)


func _answer(ok: bool) -> void:
	if _done:
		return
	_done = true
	answered.emit(ok)
	var tw := create_tween()
	tw.tween_property(self, "modulate:a", 0.0, 0.12)
	tw.tween_callback(queue_free)


## Schliesst den Dialog von aussen (ok = false).
func dismiss() -> void:
	_answer(false)
