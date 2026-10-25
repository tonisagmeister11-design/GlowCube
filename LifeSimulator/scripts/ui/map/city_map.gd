class_name UICityMap
extends Control
## Vollbild-Stadtkarte: Ziehen zum Verschieben, Mausrad zum Zoomen,
## Kategorie-Filter, Markierungen mit Tooltip, Detailkarte per Klick,
## Fussweg-Route (city.ped_route), Buslinien, Stadtteilnamen, Spielerpfeil.

signal closed()

const MIN_ZOOM := 0.35
const MAX_ZOOM := 4.0

var world: WorldState
var zoom := 1.0
var pan := Vector2.ZERO
var filters := {}
var show_bus := true
var show_districts := true
var _markers: Array = []
var _hover := -1
var _selected := -1
var _drag := false
var _drag_moved := 0.0
var _canvas: Control
var _detail: PanelContainer
var _detail_box: VBoxContainer
var _tip: PanelContainer
var _tip_label: Label
var _search: LineEdit
var _counts := {}
var _icons := {}


func _ready() -> void:
	set_anchors_preset(PRESET_FULL_RECT)
	mouse_filter = MOUSE_FILTER_STOP
	world = Game.world
	for k in UIMapData.CATEGORIES:
		filters[k] = k != "housing"
		_icons[k] = UIKit.icon(UIMapData.CATEGORIES[k][1])
	var bg := ColorRect.new()
	bg.color = Color(0.03, 0.05, 0.09, 0.97)
	bg.set_anchors_preset(PRESET_FULL_RECT)
	bg.mouse_filter = MOUSE_FILTER_IGNORE
	add_child(bg)
	_canvas = Control.new()
	_canvas.set_anchors_preset(PRESET_FULL_RECT)
	_canvas.mouse_filter = MOUSE_FILTER_IGNORE
	_canvas.draw.connect(_draw_map)
	add_child(_canvas)
	_markers = UIMapData.markers(world)
	for m in _markers:
		_counts[m.cat] = int(_counts.get(m.cat, 0)) + 1
	_build_sidebar()
	_build_topbar()
	_build_detail()
	_tip = PanelContainer.new()
	_tip.theme_type_variation = "ToastPanel"
	_tip.visible = false
	_tip.mouse_filter = MOUSE_FILTER_IGNORE
	_tip_label = UIKit.label("")
	_tip.add_child(_tip_label)
	add_child(_tip)
	call_deferred("center_on_player")
	UIKit.appear(self, 0.0, Vector2.ZERO, 0.2)


func _build_sidebar() -> void:
	var side := PanelContainer.new()
	side.theme_type_variation = "GlassPanel"
	side.position = Vector2(28, 100)
	side.custom_minimum_size = Vector2(300, 0)
	add_child(side)
	var v := UIKit.vbox(6)
	side.add_child(v)
	v.add_child(UIKit.eyebrow("Filter"))
	for k in UIMapData.CATEGORIES:
		var c: Array = UIMapData.CATEGORIES[k]
		var b := UIKit.chip("%s  (%d)" % [c[0], int(_counts.get(k, 0))], c[1])
		b.alignment = HORIZONTAL_ALIGNMENT_LEFT
		b.button_pressed = filters[k]
		b.add_theme_color_override("icon_normal_color", c[2])
		b.add_theme_color_override("icon_pressed_color", c[2])
		b.add_theme_color_override("icon_hover_color", c[2])
		b.toggled.connect(func(on: bool) -> void:
			filters[k] = on
			_canvas.queue_redraw())
		v.add_child(b)
	v.add_child(UIKit.separator())
	var bus := CheckButton.new()
	bus.text = "Buslinien"
	bus.button_pressed = show_bus
	bus.toggled.connect(func(on: bool) -> void:
		show_bus = on
		_canvas.queue_redraw())
	v.add_child(bus)
	var dn := CheckButton.new()
	dn.text = "Stadtteilnamen"
	dn.button_pressed = show_districts
	dn.toggled.connect(func(on: bool) -> void:
		show_districts = on
		_canvas.queue_redraw())
	v.add_child(dn)


func _build_topbar() -> void:
	var top := UIKit.hbox(12)
	top.position = Vector2(28, 28)
	top.custom_minimum_size = Vector2(0, 52)
	add_child(top)
	top.add_child(UIKit.icon_badge("map", UITheme.ACCENT, 46, 0.5))
	var t := UIKit.label("Stadtkarte %s" % CityCatalog.CITY_NAME_DISPLAY, "TitleLabel")
	top.add_child(t)
	top.add_child(UIKit.spacer(false, false, Vector2(24, 0)))
	_search = LineEdit.new()
	_search.placeholder_text = "Ort oder Geschäft suchen ..."
	_search.custom_minimum_size = Vector2(360, 0)
	_search.right_icon = UIKit.icon("search")
	_search.text_submitted.connect(_on_search)
	top.add_child(_search)
	var right := UIKit.hbox(10)
	right.anchor_left = 1.0
	right.anchor_right = 1.0
	right.offset_left = -560
	right.offset_right = -28
	right.offset_top = 28
	right.alignment = BoxContainer.ALIGNMENT_END
	add_child(right)
	var me := UIKit.icon_button("target", "Auf mich zentrieren", 46)
	me.pressed.connect(center_on_player)
	right.add_child(me)
	var zi := UIKit.icon_button("plus", "Vergrößern", 46)
	zi.pressed.connect(func() -> void: _zoom_at(size * 0.5, 1.4))
	right.add_child(zi)
	var zo := UIKit.icon_button("minus", "Verkleinern", 46)
	zo.pressed.connect(func() -> void: _zoom_at(size * 0.5, 1.0 / 1.4))
	right.add_child(zo)
	var close := UIKit.button("Schließen", "close", "", 0)
	close.pressed.connect(func() -> void: closed.emit())
	right.add_child(close)
	right.add_child(UIKit.action_keycap("map"))
	# Legende unten
	var legend := UIKit.hbox(14)
	legend.anchor_top = 1.0
	legend.anchor_bottom = 1.0
	legend.offset_left = 360
	legend.offset_top = -56
	legend.offset_bottom = -24
	legend.add_child(UIKit.label("Ziehen: verschieben", "CaptionLabel"))
	legend.add_child(UIKit.label("Mausrad: zoomen", "CaptionLabel"))
	legend.add_child(UIKit.label("Klick auf Markierung: Details und Route", "CaptionLabel"))
	add_child(legend)


func _build_detail() -> void:
	_detail = PanelContainer.new()
	_detail.theme_type_variation = "SurfacePanel"
	_detail.anchor_left = 1.0
	_detail.anchor_right = 1.0
	_detail.offset_left = -440
	_detail.offset_right = -28
	_detail.offset_top = 100
	_detail.visible = false
	add_child(_detail)
	_detail_box = UIKit.vbox(10)
	_detail.add_child(_detail_box)


# ================================================================ Koordinaten

func _w2s(p: Vector2) -> Vector2:
	return UIMapData.to_px(world, p) * zoom + pan


func _s2w(s: Vector2) -> Vector2:
	return UIMapData.to_world(world, (s - pan) / zoom)


func center_on(p: Vector2) -> void:
	pan = size * 0.5 - UIMapData.to_px(world, p) * zoom
	_canvas.queue_redraw()


func center_on_player() -> void:
	if world == null:
		return
	zoom = maxf(zoom, 1.0)
	var p := UIMapData.player_pos(get_tree())
	center_on(p if p != Vector2.INF else Vector2.ZERO)


func _zoom_at(screen: Vector2, f: float) -> void:
	var before := _s2w(screen)
	zoom = clampf(zoom * f, MIN_ZOOM, MAX_ZOOM)
	pan = screen - UIMapData.to_px(world, before) * zoom
	_canvas.queue_redraw()


# ================================================================ Eingabe

func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton:
		if event.button_index == MOUSE_BUTTON_WHEEL_UP and event.pressed:
			_zoom_at(event.position, 1.15)
		elif event.button_index == MOUSE_BUTTON_WHEEL_DOWN and event.pressed:
			_zoom_at(event.position, 1.0 / 1.15)
		elif event.button_index == MOUSE_BUTTON_LEFT:
			if event.pressed:
				_drag = true
				_drag_moved = 0.0
			else:
				_drag = false
				if _drag_moved < 6.0:
					_select(_marker_at(event.position))
		accept_event()
	elif event is InputEventMouseMotion:
		if _drag:
			pan += event.relative
			_drag_moved += event.relative.length()
			_canvas.queue_redraw()
		var h := _marker_at(event.position)
		if h != _hover:
			_hover = h
			_canvas.queue_redraw()
		_tip.visible = h >= 0
		if h >= 0:
			_tip_label.text = "%s · %s" % [_markers[h].name, _markers[h].type_label]
			_tip.position = event.position + Vector2(18, 14)
			_tip.reset_size()


func _marker_at(p: Vector2) -> int:
	var best := -1
	var bd := 16.0
	for i in _markers.size():
		var m: Dictionary = _markers[i]
		if not filters.get(m.cat, false):
			continue
		var d := _w2s(m.pos).distance_to(p)
		if d < bd:
			bd = d
			best = i
	return best


func _on_search(t: String) -> void:
	t = t.strip_edges().to_lower()
	if t == "":
		return
	for i in _markers.size():
		var m: Dictionary = _markers[i]
		if str(m.name).to_lower().contains(t) or str(m.type_label).to_lower().contains(t):
			filters[m.cat] = true
			zoom = maxf(zoom, 1.6)
			center_on(m.pos)
			_select(i)
			return
	EventBus.notify.emit("Nichts gefunden für „%s“." % t, "info")


# ================================================================ Details / Route

func _select(i: int) -> void:
	_selected = i
	_canvas.queue_redraw()
	UIKit.clear(_detail_box)
	_detail.visible = i >= 0
	if i < 0:
		return
	var m: Dictionary = _markers[i]
	var c: Array = UIMapData.CATEGORIES[m.cat]
	var h := UIKit.hbox(12)
	h.add_child(UIKit.icon_badge(c[1], c[2], 52, 0.5))
	var tv := UIKit.vbox(0)
	tv.size_flags_horizontal = SIZE_EXPAND_FILL
	tv.add_child(UIKit.label(str(m.name), "TitleLabel", true))
	tv.add_child(UIKit.label(str(m.type_label), "CaptionLabel"))
	h.add_child(tv)
	var x := UIKit.icon_button("close", "Schließen", 36)
	x.pressed.connect(_select.bind(-1))
	h.add_child(x)
	_detail_box.add_child(h)
	_detail_box.add_child(UIKit.separator())
	if str(m.address) != "":
		_detail_box.add_child(UIKit.stat_row("Adresse", str(m.address), "pin"))
	if str(m.district) != "":
		_detail_box.add_child(UIKit.stat_row("Stadtteil", str(m.district), "city"))
	if str(m.open) != "":
		var is_open := ""
		if world.economy and int(m.building) >= 0:
			var biz: int = world.economy.business_for_building(int(m.building))
			if biz >= 0:
				is_open = " · jetzt geöffnet" if world.economy.is_open(world, biz) else " · jetzt geschlossen"
		_detail_box.add_child(UIKit.stat_row("Öffnungszeiten", str(m.open) + is_open, "clock"))
	var pp := UIMapData.player_pos(get_tree())
	if pp != Vector2.INF:
		var r: PackedVector2Array = world.city.ped_route(pp, m.pos)
		var plen := CityModel.polyline_length(r)
		var txt := "%s · ca. %d Min. zu Fuß" % [UIKit.distance(plen), maxi(1, int(plen / 1.35 / 60.0))] if r.size() > 2 \
			else "Luftlinie %s" % UIKit.distance(pp.distance_to(m.pos))
		_detail_box.add_child(UIKit.stat_row("Fußweg", txt, "walk"))
	var bh := UIKit.hbox(10)
	var go := UIKit.button("Route planen", "route", "AccentButton")
	go.size_flags_horizontal = SIZE_EXPAND_FILL
	go.pressed.connect(func() -> void:
		UIMapData.set_route(world, UIMapData.player_pos(get_tree()), m.pos, str(m.name))
		_canvas.queue_redraw()
		EventBus.notify.emit("Route zu %s geplant." % m.name, "info"))
	bh.add_child(go)
	if UIMapData.route.size() > 0:
		var cl := UIKit.button("Route löschen", "close", "GhostButton")
		cl.pressed.connect(func() -> void:
			UIMapData.clear_route()
			_select(_selected))
		bh.add_child(cl)
	_detail_box.add_child(bh)


# ================================================================ Zeichnen

func _draw_map() -> void:
	if world == null:
		return
	var tex := UIMapData.texture(world)
	if tex == null:
		_canvas.draw_string(UITheme.font(600), size * 0.5 - Vector2(120, 0), "Karte wird erstellt ...", HORIZONTAL_ALIGNMENT_LEFT, -1, 22, UITheme.TEXT_MUTED)
		get_tree().create_timer(0.2).timeout.connect(_canvas.queue_redraw)
		return
	_canvas.draw_texture_rect(tex, Rect2(pan, tex.get_size() * zoom), false, Color(0.88, 0.9, 0.96))
	var c := world.city
	if show_bus:
		for line in c.bus_lines:
			var pts := PackedVector2Array()
			for nid in line.nodes:
				pts.append(_w2s(c.nodes[nid].pos))
			if line.loop and pts.size() > 1:
				pts.append(pts[0])
			if pts.size() > 1:
				var col: Color = line.color if line.color is Color else UITheme.ACCENT
				_canvas.draw_polyline(pts, Color(col, 0.75), maxf(2.0, 3.0 * zoom), true)
	if show_districts and zoom < 2.2:
		var f := UITheme.font(750, true)
		for d in c.districts:
			var center: Vector2 = d.center
			var s := _w2s(center)
			var dname := str(d.name).to_upper()
			var w := f.get_string_size(dname, HORIZONTAL_ALIGNMENT_LEFT, -1, 15).x
			_canvas.draw_string_outline(f, s - Vector2(w * 0.5, 0), dname, HORIZONTAL_ALIGNMENT_LEFT, -1, 15, 5, Color(0, 0, 0, 0.6))
			_canvas.draw_string(f, s - Vector2(w * 0.5, 0), dname, HORIZONTAL_ALIGNMENT_LEFT, -1, 15, Color(1, 1, 1, 0.85))
	# Route
	if UIMapData.route.size() > 1:
		var rp := PackedVector2Array()
		for p in UIMapData.route:
			rp.append(_w2s(p))
		_canvas.draw_polyline(rp, Color(0, 0, 0, 0.45), 9.0, true)
		_canvas.draw_polyline(rp, UITheme.ACCENT, 5.0, true)
		var end := rp[rp.size() - 1]
		_canvas.draw_circle(end, 9.0, Color.WHITE)
		_canvas.draw_circle(end, 6.0, UITheme.ACCENT)
	# Markierungen
	var r := 9.0 if zoom < 0.8 else 13.0
	for i in _markers.size():
		var m: Dictionary = _markers[i]
		if not filters.get(m.cat, false):
			continue
		var s := _w2s(m.pos)
		if s.x < -20 or s.y < -20 or s.x > size.x + 20 or s.y > size.y + 20:
			continue
		var col: Color = UIMapData.CATEGORIES[m.cat][2]
		var rr := r * (1.3 if i == _hover or i == _selected else 1.0)
		_canvas.draw_circle(s + Vector2(0, 1.5), rr + 1.5, Color(0, 0, 0, 0.45))
		_canvas.draw_circle(s, rr + 1.5, Color.WHITE if i == _selected else Color(0.05, 0.08, 0.14))
		_canvas.draw_circle(s, rr, col.darkened(0.15))
		if rr >= 12.0:
			var isz := rr * 1.2
			_canvas.draw_texture_rect(_icons[m.cat], Rect2(s - Vector2(isz, isz) * 0.5, Vector2(isz, isz)), false)
	# Spieler
	var pp := UIMapData.player_pos(get_tree())
	if pp != Vector2.INF:
		var s2 := _w2s(pp)
		var hd := UIMapData.heading(get_tree())
		var ang := hd.angle() + PI * 0.5
		var tri := PackedVector2Array([Vector2(0, -15), Vector2(10, 10), Vector2(0, 5), Vector2(-10, 10)])
		var xf := Transform2D(ang, s2)
		_canvas.draw_circle(s2, 22.0, Color(UITheme.ACCENT, 0.2))
		_canvas.draw_colored_polygon(xf * tri, Color.WHITE)
		var inner := PackedVector2Array([Vector2(0, -10), Vector2(6, 6), Vector2(0, 3), Vector2(-6, 6)])
		_canvas.draw_colored_polygon(xf * inner, UITheme.ACCENT)


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("map") or (event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE):
		get_viewport().set_input_as_handled()
		closed.emit()
