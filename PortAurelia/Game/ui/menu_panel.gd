class_name MenuPanel
extends CanvasLayer
## Generic in-world list menu (shops, mechanic, dealer, safehouse, bus, phone apps).
## items: Array of {label, price (int, optional), desc, enabled (bool), action: Callable,
##                  keep_open (bool)}. Mouse and keyboard/controller navigation.
## While open the player's input is locked; ESC / back closes it.

signal closed

static var current: MenuPanel

var title := ""
var phone_style := false
var subtitle := ""
var items: Array = []
var _list: VBoxContainer
var _desc: Label
var _money: Label
var _title: Label
var _sub: Label
var _player: Player
var _prev_mouse := Input.MOUSE_MODE_CAPTURED


static func open(t: String, its: Array, sub := "", phone := false) -> MenuPanel:
	if current and is_instance_valid(current):
		current.close()
	var m := MenuPanel.new()
	m.phone_style = phone
	m.title = t
	m.items = its
	m.subtitle = sub
	var w := GameWorld.instance
	(w if w else Engine.get_main_loop().root).add_child(m)
	current = m
	return m


func _ready() -> void:
	layer = 20
	process_mode = Node.PROCESS_MODE_ALWAYS
	var w := GameWorld.instance
	_player = w.player as Player if w else null
	if _player:
		_player.input_enabled = false
		_player.velocity = Vector3.ZERO
	_prev_mouse = Input.mouse_mode
	Input.mouse_mode = Input.MOUSE_MODE_VISIBLE
	var panel := PanelContainer.new()
	var sb := StyleBoxFlat.new()
	if phone_style:
		panel.anchor_left = 1.0
		panel.anchor_right = 1.0
		panel.anchor_top = 1.0
		panel.anchor_bottom = 1.0
		panel.offset_left = -460
		panel.offset_right = -60
		panel.offset_top = -760
		panel.offset_bottom = -40
		sb.bg_color = Color(0.06, 0.07, 0.1, 0.96)
		sb.border_color = Color(0.2, 0.22, 0.26)
		sb.set_border_width_all(10)
		sb.set_corner_radius_all(28)
	else:
		panel.offset_left = 60
		panel.offset_top = 90
		panel.custom_minimum_size = Vector2(560, 0)
		sb.bg_color = Color(0.04, 0.05, 0.07, 0.88)
		sb.border_color = Color(0.95, 0.75, 0.25)
		sb.border_width_top = 5
		sb.set_corner_radius_all(4)
	sb.content_margin_left = 22
	sb.content_margin_right = 22
	sb.content_margin_top = 16
	sb.content_margin_bottom = 18
	panel.add_theme_stylebox_override("panel", sb)
	add_child(panel)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 6)
	panel.add_child(v)
	var head := HBoxContainer.new()
	v.add_child(head)
	_title = Label.new()
	_title.text = title
	_title.add_theme_font_size_override("font_size", 34)
	_title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	head.add_child(_title)
	_money = Label.new()
	_money.add_theme_font_size_override("font_size", 24)
	_money.add_theme_color_override("font_color", Color(0.45, 0.9, 0.45))
	head.add_child(_money)
	_sub = Label.new()
	_sub.text = subtitle
	_sub.visible = subtitle != ""
	_sub.add_theme_color_override("font_color", Color(0.75, 0.75, 0.78))
	_sub.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	v.add_child(_sub)
	var scroll := ScrollContainer.new()
	scroll.custom_minimum_size = Vector2(0, 420 if not phone_style else 470)
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	v.add_child(scroll)
	_list = VBoxContainer.new()
	_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_list.add_theme_constant_override("separation", 3)
	scroll.add_child(_list)
	_desc = Label.new()
	_desc.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_desc.custom_minimum_size = Vector2(0, 48)
	_desc.add_theme_color_override("font_color", Color(0.85, 0.85, 0.88))
	v.add_child(_desc)
	var hint := Label.new()
	hint.text = "Enter/Klick: Auswählen    ESC: Schließen"
	hint.add_theme_font_size_override("font_size", 15)
	hint.add_theme_color_override("font_color", Color(0.6, 0.6, 0.65))
	v.add_child(hint)
	rebuild()


func set_items(its: Array) -> void:
	items = its
	rebuild()


func rebuild() -> void:
	for c in _list.get_children():
		c.queue_free()
	_money.text = "$%d" % Game.player_data.money if Game.player_data else ""
	var first: Button = null
	for it in items:
		var b := Button.new()
		b.alignment = HORIZONTAL_ALIGNMENT_LEFT
		b.custom_minimum_size = Vector2(0, 40)
		b.add_theme_font_size_override("font_size", 20)
		var price := int(it.get("price", -1))
		var txt: String = it["label"]
		if price > 0:
			txt += "    $%d" % price
		elif it.has("right"):
			txt += "    " + String(it["right"])
		b.text = txt
		var affordable := price <= 0 or (Game.player_data and Game.player_data.money >= price)
		b.disabled = not it.get("enabled", true) or not affordable
		b.pressed.connect(_choose.bind(it))
		b.focus_entered.connect(func(): _desc.text = it.get("desc", ""))
		b.mouse_entered.connect(func(): _desc.text = it.get("desc", ""); AudioManager.play_ui("hover", -14.0))
		_list.add_child(b)
		if first == null and not b.disabled:
			first = b
	if first:
		first.call_deferred("grab_focus")


func _choose(it: Dictionary) -> void:
	var price := int(it.get("price", -1))
	if price > 0:
		if not Game.player_data.add_money(-price, "purchase"):
			AudioManager.play_ui("back")
			return
	AudioManager.play_ui("select")
	var keep: bool = it.get("keep_open", false)
	if not keep:
		close()
	var cb: Callable = it.get("action", Callable())
	if cb.is_valid():
		cb.call()
	if keep and is_instance_valid(self):
		rebuild()


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel") or event.is_action_pressed("pause"):
		get_viewport().set_input_as_handled()
		AudioManager.play_ui("back")
		close()


func close() -> void:
	if is_queued_for_deletion():
		return
	if _player and is_instance_valid(_player) and _player.state != Player.State.DEAD:
		_player.input_enabled = true
	Input.mouse_mode = _prev_mouse if _prev_mouse != Input.MOUSE_MODE_VISIBLE else Input.MOUSE_MODE_CAPTURED
	if current == self:
		current = null
	closed.emit()
	queue_free()


static func is_open() -> bool:
	return current != null and is_instance_valid(current)
