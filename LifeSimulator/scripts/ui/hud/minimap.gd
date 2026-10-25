class_name UIMinimap
extends PanelContainer
## Minikarte im HUD: zentriert auf den Spieler, dreht mit der Blickrichtung
## (Kamera), zeigt die geplante Route und den Stadtteil.

const VIEW_SIZE := 250.0
const SCALE := 1.4   # Bildschirmpixel pro Kartenpixel

var world: WorldState
var _view: Control
var _district: Label
var _route_lbl: Label
var _t := 0.0


func _ready() -> void:
	theme_type_variation = "GlassPanel"
	mouse_filter = MOUSE_FILTER_IGNORE
	var v := UIKit.vbox(6)
	add_child(v)
	_view = Control.new()
	_view.custom_minimum_size = Vector2(VIEW_SIZE, VIEW_SIZE)
	_view.clip_contents = true
	_view.mouse_filter = MOUSE_FILTER_IGNORE
	_view.draw.connect(_draw_view)
	v.add_child(_view)
	_district = UIKit.label("", "CaptionLabel")
	_district.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_district.custom_minimum_size.x = VIEW_SIZE
	_district.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	v.add_child(_district)
	_route_lbl = UIKit.label("", "CaptionLabel")
	_route_lbl.add_theme_color_override("font_color", UITheme.ACCENT_HOVER)
	_route_lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_route_lbl.custom_minimum_size.x = VIEW_SIZE
	_route_lbl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	v.add_child(_route_lbl)


func _process(delta: float) -> void:
	world = Game.world
	_view.queue_redraw()
	_t += delta
	if _t < 0.5 or world == null:
		return
	_t = 0.0
	var pp := UIMapData.player_pos(get_tree())
	if pp != Vector2.INF:
		var d: Dictionary = world.city.district_at(pp)
		_district.text = str(d.get("name", CityCatalog.CITY_NAME_DISPLAY))
		if UIMapData.route_target != Vector2.INF:
			var left := pp.distance_to(UIMapData.route_target)
			if left < 12.0:
				EventBus.notify.emit("Ziel erreicht: %s" % UIMapData.route_label, "success")
				UIMapData.clear_route()
			else:
				_route_lbl.text = "%s · %s" % [UIMapData.route_label, UIKit.distance(left)]
	_route_lbl.visible = UIMapData.route_target != Vector2.INF


func _draw_view() -> void:
	var c := _view.size * 0.5
	_view.draw_rect(Rect2(Vector2.ZERO, _view.size), Color(0.05, 0.07, 0.12))
	if world == null:
		return
	var tex := UIMapData.texture(world)
	var pp := UIMapData.player_pos(get_tree())
	if pp == Vector2.INF:
		pp = Vector2.ZERO
	var hd := UIMapData.heading(get_tree())
	var rot := -PI * 0.5 - hd.angle()
	var ppx := UIMapData.to_px(world, pp)
	_view.draw_set_transform(c, rot, Vector2(SCALE, SCALE))
	if tex:
		_view.draw_texture(tex, -ppx, Color(0.85, 0.88, 0.95))
	if UIMapData.route.size() > 1:
		var rp := PackedVector2Array()
		for p in UIMapData.route:
			rp.append(UIMapData.to_px(world, p) - ppx)
		_view.draw_polyline(rp, UITheme.ACCENT, 3.5, true)
	_view.draw_set_transform(Vector2.ZERO, 0.0, Vector2.ONE)
	# Nordmarke
	var n := c + Vector2(0, -1).rotated(rot) * (VIEW_SIZE * 0.5 - 14)
	_view.draw_circle(n, 10, Color(0, 0, 0, 0.6))
	var f := UITheme.font(800, true)
	_view.draw_string(f, n + Vector2(-5, 5), "N", HORIZONTAL_ALIGNMENT_LEFT, -1, 14, UITheme.WARM)
	# Spielerpfeil (zeigt immer nach oben)
	var tri := PackedVector2Array([c + Vector2(0, -11), c + Vector2(8, 8), c + Vector2(0, 4), c + Vector2(-8, 8)])
	_view.draw_circle(c, 16, Color(UITheme.ACCENT, 0.22))
	_view.draw_colored_polygon(tri, Color.WHITE)
