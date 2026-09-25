class_name SaveLoadMenu
extends Control
## Save / load slot list (slots 1-3 + autosave for loading). Used by the main menu and
## the pause menu.

signal back

var mode := "load"     # "load" | "save"
var _list: VBoxContainer


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	set_anchors_preset(Control.PRESET_FULL_RECT)
	var dim := ColorRect.new()
	dim.color = Color(0, 0, 0, 0.75)
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(dim)
	var center := CenterContainer.new()
	center.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(center)
	var panel := PanelContainer.new()
	panel.custom_minimum_size = Vector2(840, 600)
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.05, 0.06, 0.08, 0.96)
	sb.border_color = Color(0.95, 0.75, 0.25)
	sb.border_width_top = 5
	sb.set_content_margin_all(24)
	panel.add_theme_stylebox_override("panel", sb)
	center.add_child(panel)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 10)
	panel.add_child(v)
	var t := Label.new()
	t.text = "SPIEL LADEN" if mode == "load" else "SPIEL SPEICHERN"
	t.add_theme_font_size_override("font_size", 34)
	v.add_child(t)
	_list = VBoxContainer.new()
	_list.add_theme_constant_override("separation", 8)
	_list.size_flags_vertical = Control.SIZE_EXPAND_FILL
	v.add_child(_list)
	var b := Button.new()
	b.text = "Zurück"
	b.custom_minimum_size = Vector2(0, 44)
	b.pressed.connect(_close)
	v.add_child(b)
	_fill()


func _fill() -> void:
	for c in _list.get_children():
		c.queue_free()
	var slots := [0, 1, 2]
	if mode == "load":
		slots.push_front(SaveManager.AUTOSAVE)
	var first: Button = null
	for slot in slots:
		var info := SaveManager.slot_info(slot)
		var b := Button.new()
		b.alignment = HORIZONTAL_ALIGNMENT_LEFT
		b.custom_minimum_size = Vector2(0, 70)
		b.add_theme_font_size_override("font_size", 20)
		var name := "Automatische Speicherung" if slot == SaveManager.AUTOSAVE else "Speicherplatz %d" % (slot + 1)
		if info.is_empty():
			b.text = "%s\n– leer –" % name
			b.disabled = mode == "load"
		else:
			var mins := int(float(info["play_time"]) / 60.0)
			b.text = "%s   ·   %s\n%s   ·   $%s   ·   %d Missionen   ·   %d min Spielzeit" % [name,
				String(info["time"]).replace("T", " "), info["district"], str(int(info["money"])), int(info["missions"]), mins]
		b.pressed.connect(_choose.bind(slot))
		_list.add_child(b)
		if first == null and not b.disabled:
			first = b
	if first:
		first.call_deferred("grab_focus")


func _choose(slot: int) -> void:
	AudioManager.play_ui("select")
	if mode == "load":
		Game.load_game(slot)
	else:
		if SaveManager.save_slot(slot):
			Events.notify.emit("Spiel gespeichert (Platz %d)." % (slot + 1), 3.0)
		_fill()


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel"):
		get_viewport().set_input_as_handled()
		_close()


func _close() -> void:
	AudioManager.play_ui("back")
	back.emit()
	queue_free()
