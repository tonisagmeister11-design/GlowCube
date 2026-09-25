class_name WeaponWheel
extends Control
## Hold TAB (LB on a controller): radial weapon selection with slow motion.
## Mouse / right stick direction picks a weapon, releasing TAB equips it.

var world: GameWorld
var _open := false
var _ids: Array = []
var _sel := -1
var _dir := Vector2.ZERO


func _ready() -> void:
	world = GameWorld.instance
	set_anchors_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	visible = false


func _process(_delta: float) -> void:
	var p := world.player as Player
	if p == null:
		return
	var can := p.input_enabled and p.state in [Player.State.GROUND, Player.State.AIR, Player.State.VEHICLE] \
		and not get_tree().paused
	if Input.is_action_pressed("weapon_wheel") and can:
		if not _open:
			_open = true
			visible = true
			_ids = p.weapons.owned_sorted()
			_sel = _ids.find(p.weapons.current_id())
			_dir = Vector2.ZERO
			Engine.time_scale = 0.3
			Input.mouse_mode = Input.MOUSE_MODE_CAPTURED
		var stick := Vector2(Input.get_joy_axis(0, JOY_AXIS_RIGHT_X), Input.get_joy_axis(0, JOY_AXIS_RIGHT_Y))
		if stick.length() > 0.5:
			_dir = stick
		if _dir.length() > 0.3 and not _ids.is_empty():
			var ang := fposmod(_dir.angle() + PI * 0.5, TAU)
			_sel = int(round(ang / TAU * _ids.size())) % _ids.size()
		queue_redraw()
	elif _open:
		_open = false
		visible = false
		Engine.time_scale = 1.0
		if _sel >= 0 and _sel < _ids.size() and p.weapons.current_id() != _ids[_sel]:
			p.weapons.equip(_ids[_sel])
			AudioManager.play_ui("select", -8.0)


func _input(event: InputEvent) -> void:
	if _open and event is InputEventMouseMotion:
		_dir += (event as InputEventMouseMotion).relative * 0.02
		_dir = _dir.limit_length(1.5)
		get_viewport().set_input_as_handled()


func _draw() -> void:
	if not _open or _ids.is_empty():
		return
	var c := size * 0.5
	var r := 190.0
	draw_circle(c, r + 40.0, Color(0, 0, 0, 0.55))
	var font := ThemeDB.fallback_font
	var p := world.player as Player
	for i in _ids.size():
		var a := TAU * i / _ids.size() - PI * 0.5
		var pos := c + Vector2(cos(a), sin(a)) * r
		var selected := i == _sel
		draw_circle(pos, 52.0 if selected else 44.0, Color(0.95, 0.75, 0.25, 0.95) if selected else Color(0.12, 0.13, 0.16, 0.9))
		var d := WeaponData.get_def(_ids[i])
		var txt: String = d["name"]
		var fs := 15
		var w := font.get_string_size(txt, HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x
		draw_string(font, pos + Vector2(-w * 0.5, -2), txt, HORIZONTAL_ALIGNMENT_LEFT, -1, fs,
			Color.BLACK if selected else Color.WHITE)
		if WeaponData.is_ranged(_ids[i]):
			var st: Dictionary = p.weapons.owned[_ids[i]]
			var am := "%d / %d" % [st["clip"], st["reserve"]]
			var aw := font.get_string_size(am, HORIZONTAL_ALIGNMENT_LEFT, -1, 13).x
			draw_string(font, pos + Vector2(-aw * 0.5, 16), am, HORIZONTAL_ALIGNMENT_LEFT, -1, 13,
				Color(0.1, 0.1, 0.1) if selected else Color(0.8, 0.8, 0.8))
	if _sel >= 0:
		var name: String = WeaponData.get_def(_ids[_sel])["name"]
		var nw := font.get_string_size(name, HORIZONTAL_ALIGNMENT_LEFT, -1, 26).x
		draw_string(font, c + Vector2(-nw * 0.5, 10), name, HORIZONTAL_ALIGNMENT_LEFT, -1, 26, Color.WHITE)
