class_name MapScreen
extends CanvasLayer
## Full-screen city map (M). Pauses the game. Mouse wheel zooms, drag pans,
## click sets / clears the GPS waypoint. Shows districts, points of interest,
## mission markers, the player and the current route.

const MAP_PATH := "res://assets/ui/generated/map.png"

var world: GameWorld
var is_open := false
var _canvas: Control
var _tex: Texture2D
var _zoom := 1.0              # pixels per metre on screen
var _center := Vector2.ZERO    # world xz at screen centre
var _drag := false
var _drag_moved := 0.0
var _legend: Label


func _ready() -> void:
	name = "MapScreen"
	layer = 15
	process_mode = Node.PROCESS_MODE_ALWAYS
	world = GameWorld.instance
	if ResourceLoader.exists(MAP_PATH):
		_tex = load(MAP_PATH)
	var bg := ColorRect.new()
	bg.color = Color(0.1, 0.2, 0.3)
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(bg)
	_canvas = Control.new()
	_canvas.set_anchors_preset(Control.PRESET_FULL_RECT)
	_canvas.draw.connect(_draw_map)
	_canvas.gui_input.connect(_on_input)
	_canvas.mouse_filter = Control.MOUSE_FILTER_STOP
	add_child(_canvas)
	var head := Label.new()
	head.text = "KARTE  ·  Mausrad: Zoom  ·  Ziehen: Verschieben  ·  Klick: Wegpunkt  ·  M/ESC: Schließen"
	head.add_theme_font_size_override("font_size", 18)
	head.add_theme_color_override("font_outline_color", Color.BLACK)
	head.add_theme_constant_override("outline_size", 6)
	head.position = Vector2(30, 18)
	add_child(head)
	_legend = Label.new()
	_legend.add_theme_font_size_override("font_size", 16)
	_legend.add_theme_color_override("font_outline_color", Color.BLACK)
	_legend.add_theme_constant_override("outline_size", 6)
	_legend.anchor_top = 1.0
	_legend.anchor_bottom = 1.0
	_legend.offset_top = -230
	_legend.offset_left = 30
	_legend.text = "H Safehouse   M Mission   R Rennen   $ Laden   W Waffen   K Kleidung\nA Autohändler   G Tankstelle/Parkhaus   + Krankenhaus   P Polizei   € Immobilie   B Bank"
	add_child(_legend)
	visible = false


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("map"):
		if is_open:
			close()
		elif Game.state == Game.State.PLAYING and not MenuPanel.is_open():
			open()
		get_viewport().set_input_as_handled()
	elif is_open and (event.is_action_pressed("ui_cancel") or event.is_action_pressed("pause")):
		close()
		get_viewport().set_input_as_handled()


func open() -> void:
	is_open = true
	visible = true
	Game.set_paused(true)
	var p := world.player.global_position
	_center = Vector2(p.x, p.z)
	var vp := _canvas.get_viewport_rect().size
	_zoom = vp.y / 900.0
	_update_legend()
	_canvas.queue_redraw()
	AudioManager.play_ui("select", -6.0)


func close() -> void:
	is_open = false
	visible = false
	Game.set_paused(false)
	AudioManager.play_ui("back", -6.0)


func _update_legend() -> void:
	var mm := world.missions
	var story: Vector2i = mm.call("story_progress") if mm else Vector2i.ZERO
	var pcs: int = mm.call("postcards_found") if mm else 0
	_legend.text = "H Safehouse   M Mission   R Rennen   $ Laden   W Waffen   K Kleidung   A Autohändler\n" + \
		"G Tankstelle   + Krankenhaus   P Polizei   € Immobilie   B Bank   J Juwelier\n\n" + \
		"Story: %d/%d   ·   Postkarten: %d/%d   ·   Geld: $%d" % [story.x, story.y, pcs, 25, Game.player_data.money]


# ------------------------------------------------------------------ coordinates
func _w2s(w: Vector2) -> Vector2:
	var vp := _canvas.size
	return vp * 0.5 + (w - _center) * _zoom


func _s2w(s: Vector2) -> Vector2:
	var vp := _canvas.size
	return _center + (s - vp * 0.5) / _zoom


func _on_input(ev: InputEvent) -> void:
	if ev is InputEventMouseButton:
		var mb := ev as InputEventMouseButton
		if mb.button_index == MOUSE_BUTTON_WHEEL_UP and mb.pressed:
			_zoom_at(mb.position, 1.2)
		elif mb.button_index == MOUSE_BUTTON_WHEEL_DOWN and mb.pressed:
			_zoom_at(mb.position, 1.0 / 1.2)
		elif mb.button_index == MOUSE_BUTTON_LEFT:
			if mb.pressed:
				_drag = true
				_drag_moved = 0.0
			else:
				_drag = false
				if _drag_moved < 6.0:
					_set_waypoint(_s2w(mb.position))
		elif mb.button_index == MOUSE_BUTTON_RIGHT and mb.pressed:
			Events.waypoint_set.emit(Vector3.INF)
			_canvas.queue_redraw()
	elif ev is InputEventMouseMotion and _drag:
		var mm := ev as InputEventMouseMotion
		_center -= mm.relative / _zoom
		_drag_moved += mm.relative.length()
		_canvas.queue_redraw()


func _process(delta: float) -> void:
	if not is_open:
		return
	var pan := Input.get_vector("move_left", "move_right", "move_forward", "move_back")
	if pan.length() > 0.1:
		_center += pan * delta * 900.0 / _zoom
		_canvas.queue_redraw()


func _zoom_at(screen: Vector2, f: float) -> void:
	var before := _s2w(screen)
	_zoom = clampf(_zoom * f, 0.15, 6.0)
	var after := _s2w(screen)
	_center += before - after
	_canvas.queue_redraw()


func _set_waypoint(w: Vector2) -> void:
	var hud := world.hud as HUD
	if hud and hud.gps.active() and Vector2(hud.gps.target.x, hud.gps.target.z).distance_to(w) < 20.0 / _zoom:
		Events.waypoint_set.emit(Vector3.INF)
	else:
		var y := 0.0
		var c := world.graph.closest_lane(Vector3(w.x, 0, w.y), 150.0)
		var pos := Vector3(w.x, y, w.y)
		if not c.is_empty():
			pos = c["pos"]
		Events.waypoint_set.emit(pos)
		if hud:
			var pl := world.player as Player
			var head := -(pl.vehicle as Node3D).global_basis.z if pl.is_in_vehicle() else -pl.global_basis.z
			hud.gps.update(0.0, pl.global_position, head)
		AudioManager.play_ui("click", -6.0)
	_canvas.queue_redraw()


# ------------------------------------------------------------------ drawing
func _draw_map() -> void:
	var c := _canvas
	var wmin := world.data.world_min
	var ws := world.data.world_size
	if _tex:
		var tl := _w2s(Vector2(wmin, wmin))
		c.draw_texture_rect(_tex, Rect2(tl, Vector2(ws, ws) * _zoom), false)
	var font := ThemeDB.fallback_font
	# district names
	for d in world.data.districts:
		if d["id"] in ["sea"]:
			continue
		var r: Array = d["rect"]
		var ctr := _w2s(Vector2((r[0] + r[2]) * 0.5, (r[1] + r[3]) * 0.5))
		var fs := int(clampf(14.0 * _zoom * 1.6, 12.0, 30.0))
		var txt: String = String(d["name"]).to_upper()
		var w := font.get_string_size(txt, HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x
		c.draw_string_outline(font, ctr - Vector2(w * 0.5, 0), txt, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, 5, Color(1, 1, 1, 0.8))
		c.draw_string(font, ctr - Vector2(w * 0.5, 0), txt, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, Color(0.15, 0.15, 0.2, 0.9))
	# route
	var hud := world.hud as HUD
	if hud and hud.gps.active():
		var pts := PackedVector2Array()
		for q in hud.gps.points:
			pts.append(_w2s(Vector2(q.x, q.z)))
		if pts.size() > 1:
			c.draw_polyline(pts, Color(0.75, 0.3, 0.95), 4.0, true)
		var wp := _w2s(Vector2(hud.gps.target.x, hud.gps.target.z))
		c.draw_circle(wp, 9.0, Color.BLACK)
		c.draw_circle(wp, 7.0, Color(0.8, 0.35, 1.0))
	# POIs
	if world.economy:
		for b in world.economy.call("map_pois"):
			_icon(c, font, _w2s(Vector2(b["pos"].x, b["pos"].z)), b["icon"], b["color"])
	if world.missions:
		for b in world.missions.call("blips"):
			_icon(c, font, _w2s(Vector2(b["pos"].x, b["pos"].z)), b.get("icon", ""), b["color"])
	# player
	var p := world.player
	var pp := _w2s(Vector2(p.global_position.x, p.global_position.z))
	var yaw: float = p.rotation.y
	if (p as Player).is_in_vehicle():
		yaw = (p as Player).vehicle.global_rotation.y
	var a := -yaw
	var o := pp
	var tri := PackedVector2Array([o + Vector2(0, -12).rotated(a), o + Vector2(8, 9).rotated(a), o + Vector2(-8, 9).rotated(a)])
	c.draw_colored_polygon(tri, Color.WHITE)
	c.draw_polyline(PackedVector2Array([tri[0], tri[1], tri[2], tri[0]]), Color.BLACK, 2.0)


func _icon(c: Control, font: Font, s: Vector2, icon: String, col: Color) -> void:
	c.draw_circle(s, 10.0, Color(0, 0, 0, 0.85))
	c.draw_circle(s, 8.5, col)
	if icon != "":
		var fs := 14
		var w := font.get_string_size(icon, HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x
		c.draw_string(font, s + Vector2(-w * 0.5, 5), icon, HORIZONTAL_ALIGNMENT_LEFT, -1, fs,
			Color.BLACK if col.get_luminance() > 0.55 else Color.WHITE)
