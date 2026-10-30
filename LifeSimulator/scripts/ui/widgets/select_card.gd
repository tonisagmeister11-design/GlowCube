class_name UISelectCard
extends PanelContainer
## Klickbare Karte mit beliebigem Inhalt (Spielmodus, Startsituation,
## Spielstand ...). Zustand normal / hover / ausgewaehlt, per Tastatur
## fokussierbar (Eingabe/Leertaste waehlt aus).

signal pressed()
signal double_pressed()

var selected := false: set = set_selected
var disabled := false: set = set_disabled
var accent := UITheme.ACCENT
var _hover := false
var _sb_normal: StyleBoxFlat
var _sb_hover: StyleBoxFlat
var _sb_sel: StyleBoxFlat


func _init(radius := UITheme.RADIUS_L, pad := 20.0) -> void:
	focus_mode = FOCUS_ALL
	mouse_filter = MOUSE_FILTER_STOP
	mouse_default_cursor_shape = CURSOR_POINTING_HAND
	_sb_normal = UITheme.box(UITheme.CARD, radius, UITheme.BORDER, 1, pad, pad * 0.85)
	_sb_hover = UITheme.box(UITheme.CARD_HOVER, radius, Color(accent, 0.55), 1, pad, pad * 0.85)
	_sb_sel = UITheme.box(Color(accent, 0.14), radius, accent, 2, pad, pad * 0.85)
	_sb_sel.shadow_color = Color(accent, 0.22)
	_sb_sel.shadow_size = 16
	mouse_entered.connect(func() -> void:
		_hover = true
		UIKit.sound("hover")
		_refresh())
	mouse_exited.connect(func() -> void:
		_hover = false
		_refresh())
	focus_entered.connect(_refresh)
	focus_exited.connect(_refresh)
	_refresh()


func set_accent(c: Color) -> void:
	accent = c
	_sb_hover.border_color = Color(c, 0.55)
	_sb_sel.bg_color = Color(c, 0.14)
	_sb_sel.border_color = c
	_sb_sel.shadow_color = Color(c, 0.22)
	_refresh()


func set_selected(v: bool) -> void:
	selected = v
	_refresh()


func set_disabled(v: bool) -> void:
	disabled = v
	modulate.a = 0.45 if v else 1.0
	mouse_default_cursor_shape = CURSOR_ARROW if v else CURSOR_POINTING_HAND
	_refresh()


func _refresh() -> void:
	var sb: StyleBox = _sb_normal
	if selected:
		sb = _sb_sel
	elif (_hover or has_focus()) and not disabled:
		sb = _sb_hover
	add_theme_stylebox_override("panel", sb)


func _gui_input(event: InputEvent) -> void:
	if disabled:
		return
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		accept_event()
		UIKit.sound("click")
		if event.double_click:
			double_pressed.emit()
		else:
			pressed.emit()
	elif event.is_action_pressed("ui_accept"):
		accept_event()
		UIKit.sound("click")
		pressed.emit()


## Alle Kinder ignorieren die Maus, damit die Karte die Klicks bekommt.
func _notification(what: int) -> void:
	if what == NOTIFICATION_CHILD_ORDER_CHANGED and is_inside_tree():
		_ignore_mouse(self)


func _ready() -> void:
	_ignore_mouse(self)


func _ignore_mouse(n: Node) -> void:
	for c in n.get_children():
		if c is Control and not (c is BaseButton) and not (c is LineEdit) and not (c is Range):
			c.mouse_filter = MOUSE_FILTER_IGNORE
		_ignore_mouse(c)
