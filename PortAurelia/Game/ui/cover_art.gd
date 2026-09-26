class_name CoverArt
extends RefCounted
## The HARBOR HEAT cover artwork (assets/ui/cover.png) as a full-screen background with a
## dark gradient so text and buttons stay readable. Used by the boot screen, the main menu
## and the world loading overlay.

const COVER := "res://assets/ui/cover.png"


static func add_background(parent: Control, bottom_shade := 0.75, left_shade := 0.0) -> TextureRect:
	var tr := TextureRect.new()
	tr.texture = load(COVER)
	tr.set_anchors_preset(Control.PRESET_FULL_RECT)
	tr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	tr.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
	tr.mouse_filter = Control.MOUSE_FILTER_IGNORE
	parent.add_child(tr)
	if bottom_shade > 0.0:
		parent.add_child(_gradient(Vector2(0, 0), Vector2(0, 1), bottom_shade, 0.55))
	if left_shade > 0.0:
		parent.add_child(_gradient(Vector2(1, 0), Vector2(0, 0), left_shade, 0.6))
	return tr


static func _gradient(from: Vector2, to: Vector2, strength: float, start: float) -> TextureRect:
	var g := Gradient.new()
	g.set_color(0, Color(0, 0, 0, 0))
	g.set_color(1, Color(0, 0, 0, strength))
	g.add_point(start, Color(0, 0, 0, 0))
	var gt := GradientTexture2D.new()
	gt.gradient = g
	gt.fill_from = from
	gt.fill_to = to
	gt.width = 64
	gt.height = 64
	var tr := TextureRect.new()
	tr.texture = gt
	tr.set_anchors_preset(Control.PRESET_FULL_RECT)
	tr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	tr.stretch_mode = TextureRect.STRETCH_SCALE
	tr.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return tr
