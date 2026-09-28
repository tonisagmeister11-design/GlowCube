class_name MultiplayerMenu
extends Control
## Main menu > MULTIPLAYER: create a lobby (your laptop is the server) or join one with a code.
## The lobby shows the code, the players and (host) the START button.

signal back

var _pick: VBoxContainer
var _lobby: VBoxContainer
var _name: LineEdit
var _code_in: LineEdit
var _code_label: Label
var _status: Label
var _list: Label
var _start: Button
var _msg: Label
var _extra: Label


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var shade := ColorRect.new()
	shade.color = Color(0, 0, 0, 0.55)
	shade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	add_child(shade)
	_pick = _panel()
	_title(_pick, "MULTIPLAYER")
	_text(_pick, "Wer die Lobby erstellt, ist der Server: sein Laptop verbindet alle Spieler und prüft jede Nachricht. Freunde treten mit dem Code bei – im gleichen WLAN oder von überall übers Internet (max. 8 Spieler).", 16, Color(1, 1, 1, 0.8))
	_text(_pick, "DEIN NAME", 15, Color(1, 0.3, 0.6))
	_name = LineEdit.new()
	_name.max_length = 16
	_name.text = String(Settings.get_value("multiplayer", "name"))
	if _name.text == "":
		_name.text = "Spieler%d" % randi_range(10, 99)
	_name.custom_minimum_size = Vector2(0, 44)
	_name.add_theme_font_size_override("font_size", 22)
	_pick.add_child(_name)
	_button(_pick, "LOBBY ERSTELLEN", _create)
	_text(_pick, "LOBBY-CODE", 15, Color(1, 0.3, 0.6))
	_code_in = LineEdit.new()
	_code_in.placeholder_text = "z. B. ABCDE-FGH23"
	_code_in.max_length = 16
	_code_in.custom_minimum_size = Vector2(0, 44)
	_code_in.add_theme_font_size_override("font_size", 22)
	_code_in.text_submitted.connect(func(_t): _join())
	_pick.add_child(_code_in)
	_button(_pick, "BEITRETEN", _join)
	_msg = _text(_pick, "", 16, Color(1, 0.8, 0.4))
	_button(_pick, "ZURÜCK", _back)
	_lobby = _panel()
	_lobby.get_parent().get_parent().visible = false
	_title(_lobby, "LOBBY")
	_code_label = _text(_lobby, "", 38, Color(1, 1, 1))
	_button(_lobby, "CODE KOPIEREN", func():
		if Net.lobby_code == "" or (Net.is_host() and not Net.lobby_ready):
			return
		DisplayServer.clipboard_set(Net.lobby_code)
		_status.text = "Code kopiert – schick ihn deinen Freunden (z. B. per WhatsApp).")
	_status = _text(_lobby, "", 16, Color(1, 0.85, 0.5))
	_extra = _text(_lobby, "", 14, Color(1, 1, 1, 0.7))
	_text(_lobby, "SPIELER", 15, Color(1, 0.3, 0.6))
	_list = _text(_lobby, "", 20, Color(1, 1, 1))
	_start = _button(_lobby, "SPIEL STARTEN", func(): Net.start_session())
	_button(_lobby, "LOBBY VERLASSEN", func():
		Net.leave("")
		_show_pick())
	Net.roster_changed.connect(_refresh)
	Net.status_changed.connect(func(t): _status.text = t; _msg.text = t if not _lobby.is_visible_in_tree() else _msg.text)
	Net.left.connect(func(r): _show_pick(); _msg.text = r)
	if Net.is_online():
		_show_lobby()
	else:
		_name.call_deferred("grab_focus")


func _panel() -> VBoxContainer:
	var center := CenterContainer.new()
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	add_child(center)
	var pc := PanelContainer.new()
	pc.custom_minimum_size = Vector2(680, 0)
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.03, 0.03, 0.06, 0.94)
	sb.border_width_left = 4
	sb.border_color = Color(1.0, 0.12, 0.45)
	sb.set_corner_radius_all(4)
	sb.content_margin_left = 28
	sb.content_margin_right = 28
	sb.content_margin_top = 22
	sb.content_margin_bottom = 22
	pc.add_theme_stylebox_override("panel", sb)
	center.add_child(pc)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 10)
	pc.add_child(v)
	return v


func _title(parent: Control, t: String) -> void:
	var l := Label.new()
	l.text = t
	l.add_theme_font_size_override("font_size", 34)
	l.add_theme_color_override("font_color", Color(1.0, 0.2, 0.55))
	parent.add_child(l)


func _text(parent: Control, t: String, size: int, col: Color) -> Label:
	var l := Label.new()
	l.text = t
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.custom_minimum_size = Vector2(600, 0)
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", col)
	parent.add_child(l)
	return l


func _button(parent: Control, t: String, cb: Callable) -> Button:
	var b := Button.new()
	b.text = "  " + t
	b.alignment = HORIZONTAL_ALIGNMENT_LEFT
	b.custom_minimum_size = Vector2(0, 46)
	b.add_theme_font_size_override("font_size", 22)
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(1, 1, 1, 0.06)
	var sbh := StyleBoxFlat.new()
	sbh.bg_color = Color(1.0, 0.12, 0.45, 0.88)
	b.add_theme_stylebox_override("normal", sb)
	b.add_theme_stylebox_override("hover", sbh)
	b.add_theme_stylebox_override("focus", sbh)
	b.add_theme_stylebox_override("pressed", sbh)
	b.pressed.connect(func(): AudioManager.play_ui("select"); cb.call())
	parent.add_child(b)
	return b


func _save_name() -> String:
	var n := Net.clean_name(_name.text)
	Settings.set_value("multiplayer", "name", n, false)
	Settings.save_settings()
	return n


func _create() -> void:
	if Net.host(_save_name()):
		_show_lobby()
	else:
		_msg.text = Net.status


func _join() -> void:
	if _code_in.text.strip_edges() == "":
		_msg.text = "Gib den Lobby-Code ein, den dir der Host geschickt hat."
		return
	_msg.text = "Suche Lobby ..."
	var n := _save_name()
	_show_lobby()
	Net.join(n, _code_in.text)


func _show_lobby() -> void:
	_pick.get_parent().get_parent().visible = false
	_lobby.get_parent().get_parent().visible = true
	_refresh()


func _show_pick() -> void:
	_lobby.get_parent().get_parent().visible = false
	_pick.get_parent().get_parent().visible = true


func _refresh() -> void:
	if not is_inside_tree():
		return
	if Net.is_host() and not Net.lobby_ready:
		_code_label.text = "Code wird erstellt ..."
	else:
		_code_label.text = Net.lobby_code if Net.lobby_code != "" else "—"
	var ex := ""
	if Net.lobby_ready:
		for e in Net.extra_codes():
			ex += "%s:  %s\n" % [e[0], e[1]]
	_extra.text = ("Andere Codes (nur nötig, wenn ihr ein VPN benutzt):\n" + ex) if ex != "" else ""
	if Net.is_host() and Net.status != "":
		_status.text = Net.status
	var t := ""
	for id in Net.players:
		var info := Net.peer_link_text(id)
		t += "●  %s%s%s\n" % [Net.player_name(id), "  (Host)" if id == 1 else "", ("   · " + info) if info != "" else ""]
	_list.text = t if t != "" else "..."
	_start.visible = Net.is_host()
	_start.disabled = not Net.is_host()
	if _status.text == "":
		_status.text = Net.status


var _refresh_t := 0.0


func _process(delta: float) -> void:
	_refresh_t -= delta
	if _refresh_t <= 0.0 and _lobby.is_visible_in_tree():
		_refresh_t = 1.0
		_refresh()   # ping and connection type


func _back() -> void:
	Net.leave("")
	back.emit()
	queue_free()


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel"):
		get_viewport().set_input_as_handled()
		if _lobby.is_visible_in_tree():
			Net.leave("")
			_show_pick()
		else:
			_back()
