class_name PauseMenu
extends CanvasLayer
## ESC pause menu: resume, map, save, load, settings, controls, main menu, quit.

var world: GameWorld
var _root: Control
var _box: VBoxContainer
var _code: Label
var _sub: Control = null


func _ready() -> void:
	name = "PauseMenu"
	layer = 30
	process_mode = Node.PROCESS_MODE_ALWAYS
	world = GameWorld.instance
	_root = Control.new()
	_root.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(_root)
	var dim := ColorRect.new()
	dim.color = Color(0.02, 0.03, 0.05, 0.72)
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)
	_root.add_child(dim)
	var logo := preload("res://ui/logo.gd").new()
	logo.position = Vector2(60, 60)
	logo.size = Vector2(460, 220)
	_root.add_child(logo)
	_box = VBoxContainer.new()
	_box.position = Vector2(90, 300)
	_box.custom_minimum_size = Vector2(420, 0)
	_box.add_theme_constant_override("separation", 8)
	_root.add_child(_box)
	var items := [["FORTSETZEN", _resume], ["KARTE", _map], ["MISSION ABBRECHEN", _abort_mission],
			["KREATIVMODUS AN/AUS", _toggle_creative], ["SPIEL SPEICHERN", _save], ["SPIEL LADEN", _load],
			["EINSTELLUNGEN", _settings], ["STEUERUNG", _controls], ["HAUPTMENÜ", _main_menu], ["SPIEL BEENDEN", _quit]]
	if not CreativeMode.allowed():
		items.remove_at(3)   # in someone else's lobby: only the host has the creative mode
	# multiplayer: the lobby code stays visible here too
	_code = Label.new()
	_code.position = Vector2(560, 90)
	_code.add_theme_font_size_override("font_size", 30)
	_code.add_theme_color_override("font_color", Color(1.0, 0.85, 0.3))
	_code.add_theme_color_override("font_outline_color", Color(0, 0, 0, 0.9))
	_code.add_theme_constant_override("outline_size", 6)
	_root.add_child(_code)
	for it in items:
		var b := Button.new()
		b.text = it[0]
		b.alignment = HORIZONTAL_ALIGNMENT_LEFT
		b.custom_minimum_size = Vector2(420, 52)
		b.add_theme_font_size_override("font_size", 24)
		b.pressed.connect(it[1])
		b.mouse_entered.connect(func(): AudioManager.play_ui("hover", -14.0))
		_box.add_child(b)
	visible = false


func _unhandled_input(event: InputEvent) -> void:
	if not event.is_action_pressed("pause"):
		return
	if MenuPanel.is_open():
		return
	var ms = world.get_node_or_null("MapScreen")
	if ms and ms.is_open:
		return
	if _sub != null and is_instance_valid(_sub):
		return
	get_viewport().set_input_as_handled()
	if visible:
		_resume()
	elif Game.state == Game.State.PLAYING and (world.player as Player).state != Player.State.DEAD:
		_open()


func _open() -> void:
	visible = true
	_code.text = ("Lobby-Code: %s" % Net.lobby_code) if Net.is_online() and Net.lobby_code != "" else ""
	Game.set_paused(true)
	AudioManager.play_ui("select", -6.0)
	(_box.get_child(0) as Button).grab_focus()


func _resume() -> void:
	visible = false
	Game.set_paused(false)
	AudioManager.play_ui("back", -6.0)


func _abort_mission() -> void:
	var mm = world.missions if world else null
	if mm == null or not mm.call("is_active"):
		Events.notify.emit("Gerade läuft keine Mission.", 2.0)
		return
	_confirm("Mission wirklich abbrechen?", func():
		_resume()
		mm.call("abort_current"))


func _toggle_creative() -> void:
	_resume()
	if not CreativeMode.allowed():
		return
	CreativeMode.set_enabled(not Game.player_data.is_creative())


func _map() -> void:
	visible = false
	Game.set_paused(false)
	var ms = world.get_node_or_null("MapScreen")
	if ms:
		ms.call("open")


func _save() -> void:
	if world.missions and world.missions.call("is_active"):
		Events.notify.emit("Während einer Mission kann nicht gespeichert werden.", 3.0)
		return
	if world.police and int(world.police.get("wanted_level")) > 0:
		Events.notify.emit("Du wirst gesucht – Speichern nicht möglich.", 3.0)
		return
	var m := SaveLoadMenu.new()
	m.mode = "save"
	_open_sub(m)


func _load() -> void:
	var m := SaveLoadMenu.new()
	m.mode = "load"
	_open_sub(m)


func _settings() -> void:
	_open_sub(SettingsMenu.new())


func _controls() -> void:
	var c := Control.new()
	c.set_anchors_preset(Control.PRESET_FULL_RECT)
	c.process_mode = Node.PROCESS_MODE_ALWAYS
	var dim := ColorRect.new()
	dim.color = Color(0, 0, 0, 0.8)
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)
	c.add_child(dim)
	var l := Label.new()
	l.text = "STEUERUNG\n\n" + ControlsHelp.TEXT + "\n\n[ESC / Klick] Zurück"
	l.add_theme_font_size_override("font_size", 22)
	l.position = Vector2(120, 100)
	c.add_child(l)
	c.gui_input.connect(func(e): if e is InputEventMouseButton and e.pressed: _close_sub())
	c.set_meta("simple", true)
	_open_sub(c)


func _open_sub(c: Control) -> void:
	_box.visible = false
	_sub = c
	if c.has_signal("back"):
		c.connect("back", _on_sub_back)
	_root.add_child(c)


func _on_sub_back() -> void:
	_sub = null
	_box.visible = true
	(_box.get_child(0) as Button).grab_focus()


func _close_sub() -> void:
	if _sub and is_instance_valid(_sub):
		_sub.queue_free()
	_on_sub_back()


func _input(event: InputEvent) -> void:
	# simple overlays (controls help) close with ESC
	if visible and _sub and is_instance_valid(_sub) and _sub.has_meta("simple") and event.is_action_pressed("ui_cancel"):
		get_viewport().set_input_as_handled()
		_close_sub()


func _main_menu() -> void:
	_confirm("Zum Hauptmenü? Dein Spiel wird automatisch gespeichert (Ort, Geld, Autos).", func(): Game.to_main_menu())


func _quit() -> void:
	_confirm("Spiel wirklich beenden? Dein Spiel wird automatisch gespeichert.", func(): Game.quit())


func _confirm(text: String, yes: Callable) -> void:
	var d := ConfirmationDialog.new()
	d.dialog_text = text
	d.ok_button_text = "Ja"
	d.cancel_button_text = "Nein"
	d.process_mode = Node.PROCESS_MODE_ALWAYS
	d.confirmed.connect(yes)
	add_child(d)
	d.popup_centered()
