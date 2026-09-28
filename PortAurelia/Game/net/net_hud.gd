class_name NetHud
extends CanvasLayer
## Multiplayer overlay in the world: player count, chat (Enter), kill feed and the player list (O)
## with "send money" and, for the host, "kick".

var _count: Label
var _code: Label
var _timer: Label
var _log: RichTextLabel
var _input: LineEdit
var _typing := false
var _coop_left := 0.0
var _coop_stamp := 0
var _log_alpha := 0.0


func _ready() -> void:
	layer = 15
	process_mode = Node.PROCESS_MODE_ALWAYS
	_count = Label.new()
	_count.anchor_left = 1.0
	_count.anchor_right = 1.0
	_count.offset_left = -360
	_count.offset_right = -24
	_count.offset_top = 150
	_count.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	_count.add_theme_font_size_override("font_size", 17)
	_count.add_theme_color_override("font_outline_color", Color(0, 0, 0, 0.9))
	_count.add_theme_constant_override("outline_size", 5)
	add_child(_count)
	# the lobby code stays on screen the whole time (to send it to friends)
	_code = Label.new()
	_code.anchor_left = 1.0
	_code.anchor_right = 1.0
	_code.offset_left = -360
	_code.offset_right = -24
	_code.offset_top = 174
	_code.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	_code.add_theme_font_size_override("font_size", 22)
	_code.add_theme_color_override("font_color", Color(1.0, 0.85, 0.3))
	_code.add_theme_color_override("font_outline_color", Color(0, 0, 0, 0.9))
	_code.add_theme_constant_override("outline_size", 6)
	add_child(_code)
	_timer = Label.new()
	_timer.anchor_left = 0.5
	_timer.anchor_right = 0.5
	_timer.offset_left = -120
	_timer.offset_right = 120
	_timer.offset_top = 70
	_timer.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_timer.add_theme_font_size_override("font_size", 30)
	_timer.add_theme_color_override("font_outline_color", Color(0, 0, 0, 0.9))
	_timer.add_theme_constant_override("outline_size", 7)
	add_child(_timer)
	_log = RichTextLabel.new()
	_log.bbcode_enabled = true
	_log.scroll_active = false
	_log.fit_content = true
	_log.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_log.anchor_top = 0.35
	_log.anchor_bottom = 0.35
	_log.offset_left = 24
	_log.offset_right = 560
	_log.add_theme_font_size_override("normal_font_size", 17)
	_log.add_theme_color_override("font_outline_color", Color(0, 0, 0, 0.9))
	_log.add_theme_constant_override("outline_size", 5)
	add_child(_log)
	_input = LineEdit.new()
	_input.placeholder_text = "Nachricht an alle – Enter senden, ESC abbrechen"
	_input.max_length = 120
	_input.anchor_top = 0.35
	_input.anchor_bottom = 0.35
	_input.offset_left = 24
	_input.offset_right = 560
	_input.offset_top = -44
	_input.offset_bottom = -8
	_input.visible = false
	_input.text_submitted.connect(_on_submit)
	add_child(_input)
	Net.chat_received.connect(func(_s, _t): _refresh_log(); _log_alpha = 10.0)
	Net.roster_changed.connect(_refresh_count)
	_refresh_count()
	_refresh_log()


func _process(delta: float) -> void:
	if not Net.is_online():
		queue_free()
		return
	_log_alpha = maxf(0.0, _log_alpha - delta)
	_log.modulate.a = 1.0 if _typing else clampf(_log_alpha, 0.0, 1.0)
	_code.text = "Lobby-Code: %s" % Net.lobby_code if Net.lobby_code != "" else ""
	var st := Net.coop_state
	if bool(st.get("active", false)) and st.has("timer"):
		_coop_left = float(st["timer"]) if _coop_stamp != st.hash() else maxf(0.0, _coop_left - delta)
		_coop_stamp = st.hash()
		_timer.text = "%d:%02d" % [int(_coop_left) / 60, int(_coop_left) % 60]
		_timer.modulate = Color(1, 0.35, 0.3) if _coop_left < 30.0 else Color(1, 1, 1)
	else:
		_timer.text = ""


func _refresh_count() -> void:
	if _count == null:
		return
	var n := Net.players.size()
	_count.text = "ONLINE: %d Spieler%s   (O: Spieler & Koop-Missionen, Enter: Chat)" % [n, "  ·  Host" if Net.is_host() else ""]


func _refresh_log() -> void:
	var lines: Array = Net.chat_log().slice(-7)
	var t := ""
	for l in lines:
		var col: Color = l[2]
		if String(l[0]) == "":
			t += "[color=#%s]%s[/color]\n" % [col.to_html(false), l[1]]
		else:
			t += "[color=#%s][b]%s:[/b][/color] %s\n" % [col.to_html(false), l[0], l[1]]
	_log.text = t


func _unhandled_input(event: InputEvent) -> void:
	if _typing:
		if event.is_action_pressed("ui_cancel"):
			get_viewport().set_input_as_handled()
			_close_chat()
		return
	if Game.state != Game.State.PLAYING or MenuPanel.is_open():
		return
	if event.is_action_pressed("chat"):
		get_viewport().set_input_as_handled()
		_open_chat()
	elif event.is_action_pressed("players_menu"):
		get_viewport().set_input_as_handled()
		open_players_menu()


func _open_chat() -> void:
	_typing = true
	_input.visible = true
	_input.text = ""
	_input.grab_focus()
	Input.mouse_mode = Input.MOUSE_MODE_VISIBLE
	var p := GameWorld.instance.player as Player if GameWorld.instance else null
	if p:
		p.input_enabled = false


func _close_chat() -> void:
	_typing = false
	_input.visible = false
	_input.release_focus()
	Input.mouse_mode = Input.MOUSE_MODE_CAPTURED
	var p := GameWorld.instance.player as Player if GameWorld.instance else null
	if p:
		p.input_enabled = true
	_log_alpha = 6.0


func _on_submit(t: String) -> void:
	Net.send_chat(t)
	_close_chat()


func open_players_menu() -> void:
	var items := []
	if Net.lobby_code != "":
		items.append({"label": "Lobby-Code: %s" % Net.lobby_code, "desc": "Code in die Zwischenablage kopieren",
			"action": func(): DisplayServer.clipboard_set(Net.lobby_code); Events.notify.emit("Code kopiert.", 2.0),
			"keep_open": true})
	if Net.is_host() and Game.player_data.is_creative():
		# only the host sees this - the others never notice the creative mode
		items.append({"label": "Geld nehmen", "right": "Kreativ", "desc": "Nur für dich sichtbar", "action": _take_money_menu})
	items.append({"label": "Geld fallen lassen", "right": "$%d" % Game.player_data.money,
		"desc": "Jeder kann es aufheben", "action": _drop_money_menu})
	items.append({"label": "Waffe fallen lassen", "desc": "Jeder kann sie aufheben", "action": _drop_weapon_menu})
	if Net.is_host():
		if Net.coop and is_instance_valid(Net.coop):
			items.append({"label": "Koop-Mission abbrechen", "right": String(CoopMissions.MISSIONS[Net.coop.mission_id]["title"]),
				"action": func(): Net.coop.cancel("Der Host hat die Mission abgebrochen.")})
		else:
			items.append({"label": "Koop-Mission starten", "right": "%d Spieler" % Net.players.size(),
				"action": _coop_menu})
	elif bool(Net.coop_state.get("active", false)):
		items.append({"label": "Koop-Mission läuft", "right": String(Net.coop_state.get("title", "")), "enabled": false,
			"action": func(): pass})
	for id in Net.players:
		var pid: int = id
		if pid == Net.my_id():
			items.append({"label": "%s (du)" % Net.player_name(pid), "enabled": false, "action": func(): pass})
			continue
		var d := ""
		var pr = Net.proxies().get(pid)
		var me := GameWorld.instance.player as Node3D if GameWorld.instance else null
		if pr and is_instance_valid(pr) and me:
			d = "%d m entfernt" % int((pr as Node3D).global_position.distance_to(me.global_position))
		var link := Net.peer_link_text(pid)
		if link != "":
			d = (d + "  ·  " if d != "" else "") + link
		items.append({"label": Net.player_name(pid), "right": "Geld senden", "desc": d,
			"action": func(): _money_menu(pid)})
		items.append({"label": "   %s eine Waffe geben" % Net.player_name(pid), "action": func(): _weapon_menu(pid)})
		if Net.is_host():
			items.append({"label": "   %s rauswerfen" % Net.player_name(pid), "action": func():
				Net.kick(pid)
				Events.notify.emit("%s wurde entfernt." % Net.player_name(pid), 2.5)})
	MenuPanel.open("Online-Spieler", items, "Geld und Waffen teilen, Spieler finden (siehe Karte M)")


func _coop_menu() -> void:
	var items := []
	for k in CoopMissions.MISSIONS:
		var id: String = k
		var m: Dictionary = CoopMissions.MISSIONS[id]
		items.append({"label": m["title"], "right": "$%d pro Spieler" % m["reward"], "desc": m["desc"],
			"enabled": Net.players.size() >= 2 or OS.has_environment("HH_COOP_SOLO"),
			"action": func(): CoopMissions.start(id)})
	MenuPanel.open("Koop-Missionen", items, "Nur zusammen zu schaffen – alle bekommen die Belohnung.")


static func _amounts() -> Array:
	var out := [100, 500, 1000, 5000, 10000, 50000]
	if Net.is_host():
		out.append_array([100000, 1000000, 10000000])
	return out


func _take_money_menu() -> void:
	CreativeMode.open_money_menu()


func _drop_money_menu() -> void:
	var items := []
	for a in _amounts():
		var amount: int = a
		items.append({"label": "$%d" % amount, "enabled": Game.player_data.money >= amount,
			"action": func(): Net.drop_money(amount)})
	MenuPanel.open("Geld fallen lassen", items, "Du hast $%d – jeder kann es aufheben" % Game.player_data.money)


func _weapon_items(cb: Callable) -> Array:
	var items := []
	var p := GameWorld.instance.player as Player if GameWorld.instance else null
	if p == null:
		return items
	for id in p.weapons.owned_sorted():
		var wid: String = id
		if wid == "unarmed":
			continue
		var d := WeaponData.get_def(wid)
		items.append({"label": String(d.get("name", wid)),
			"right": ("%d Schuss" % p.weapons.ammo_of(wid)) if WeaponData.is_ranged(wid) else "",
			"action": func(): cb.call(wid)})
	if items.is_empty():
		items.append({"label": "Du hast keine Waffe", "enabled": false, "action": func(): pass})
	return items


func _drop_weapon_menu() -> void:
	MenuPanel.open("Waffe fallen lassen", _weapon_items(func(wid): Net.drop_weapon(wid)), "Mit Munition – jeder kann sie aufheben")


func _weapon_menu(pid: int) -> void:
	MenuPanel.open("Waffe an %s" % Net.player_name(pid), _weapon_items(func(wid): Net.send_weapon(pid, wid)), "Mit der ganzen Munition")


func _money_menu(pid: int) -> void:
	var items := []
	for a in _amounts():
		var amount: int = a
		items.append({"label": "$%d" % amount, "enabled": Game.player_data.money >= amount,
			"action": func(): Net.send_money(pid, amount)})
	MenuPanel.open("Geld an %s" % Net.player_name(pid), items, "Du hast $%d" % Game.player_data.money)
