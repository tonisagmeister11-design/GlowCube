class_name MenuScreen
extends Control
## Basis fuer Unterbildschirme des Hauptmenues (und des Pausenmenues):
## Kopfzeile mit Zurueck-Schaltflaeche, Titelblock, Inhaltsbereich und
## Fusszeile fuer Aktionen. Unterklassen bauen ihren Inhalt in build().

signal back_requested()

## Vom Gastgeber gesetzt (MainMenu oder PauseMenu); bietet open_screen().
var host: Node
var arg = null
var header_actions: HBoxContainer
var content: VBoxContainer
var footer: HBoxContainer
var title_label: Label
var subtitle_label: Label
var back_button: Button
var max_content_width := 1560.0


func _init() -> void:
	set_anchors_preset(PRESET_FULL_RECT)
	mouse_filter = MOUSE_FILTER_STOP


## Titel, Untertitel, Bereichsname ueber dem Titel, Icon.
func setup_frame(title: String, subtitle := "", eyebrow_text := "Hauptmenü", icon_name := "") -> void:
	var outer := MarginContainer.new()
	outer.set_anchors_preset(PRESET_FULL_RECT)
	outer.add_theme_constant_override("margin_left", 96)
	outer.add_theme_constant_override("margin_right", 96)
	outer.add_theme_constant_override("margin_top", 44)
	outer.add_theme_constant_override("margin_bottom", 40)
	add_child(outer)
	var center := HBoxContainer.new()
	center.alignment = BoxContainer.ALIGNMENT_CENTER
	outer.add_child(center)
	var col := UIKit.vbox(22)
	col.size_flags_horizontal = SIZE_EXPAND_FILL
	col.custom_minimum_size.x = 0
	center.add_child(col)
	_limit = col
	# Kopf
	var top := UIKit.hbox(12)
	back_button = UIKit.button("Zurück", "arrow_left", "GhostButton")
	back_button.pressed.connect(func() -> void: back_requested.emit())
	top.add_child(back_button)
	top.add_child(UIKit.spacer())
	header_actions = UIKit.hbox(10)
	top.add_child(header_actions)
	col.add_child(top)
	var title_row := UIKit.hbox(18)
	if icon_name != "":
		title_row.add_child(UIKit.icon_badge(icon_name, UITheme.ACCENT, 64, 0.48))
	var tv := UIKit.vbox(2)
	tv.size_flags_horizontal = SIZE_EXPAND_FILL
	if eyebrow_text != "":
		tv.add_child(UIKit.eyebrow(eyebrow_text))
	title_label = UIKit.label(title, "HeaderLabel")
	tv.add_child(title_label)
	subtitle_label = UIKit.label(subtitle, "SubtitleLabel", true)
	subtitle_label.visible = subtitle != ""
	tv.add_child(subtitle_label)
	title_row.add_child(tv)
	col.add_child(title_row)
	content = UIKit.vbox(16)
	content.size_flags_vertical = SIZE_EXPAND_FILL
	col.add_child(content)
	footer = UIKit.hbox(12)
	footer.alignment = BoxContainer.ALIGNMENT_END
	col.add_child(footer)
	call_deferred("_apply_limit")


var _limit: Control


func _notification(what: int) -> void:
	if what == NOTIFICATION_RESIZED:
		_apply_limit()


func _apply_limit() -> void:
	if _limit == null:
		return
	_limit.custom_minimum_size.x = minf(size.x - 192.0, max_content_width)
	_limit.size_flags_horizontal = SIZE_SHRINK_CENTER if size.x - 192.0 > max_content_width else SIZE_EXPAND_FILL


## Wird nach dem Einhaengen aufgerufen (arg = optionaler Startparameter).
func on_open() -> void:
	pass


## Esc / Zurueck: true = Bildschirm darf geschlossen werden.
func can_go_back() -> bool:
	return true


func _unhandled_input(event: InputEvent) -> void:
	if not visible:
		return
	if event is InputEventKey and event.pressed and not event.echo and event.keycode == KEY_ESCAPE:
		get_viewport().set_input_as_handled()
		if can_go_back():
			back_requested.emit()


func focus_first() -> void:
	var c := _find_focusable(content)
	if c:
		c.grab_focus()
	elif back_button:
		back_button.grab_focus()


func _find_focusable(n: Node) -> Control:
	for ch in n.get_children():
		if ch is Control and ch.visible and ch.focus_mode == FOCUS_ALL and not (ch is ScrollContainer):
			return ch
		var r := _find_focusable(ch)
		if r:
			return r
	return null
