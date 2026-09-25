extends Control
## Game logo drawn in code: "PORT AURELIA" with a sun emblem and wave underline.

func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE


func _draw() -> void:
	var w := size.x
	var c := Vector2(w * 0.5, 62)
	draw_circle(c, 46, Color(1.0, 0.65, 0.28))
	draw_circle(c + Vector2(0, 8), 46, Color(1.0, 0.45, 0.3, 0.5))
	for i in 5:
		var y := 70.0 + i * 8.0
		draw_line(Vector2(c.x - 60 + i * 6, y), Vector2(c.x + 60 - i * 6, y), Color(0.1, 0.12, 0.3, 0.85), 4.0)
	var font := ThemeDB.fallback_font
	var title := "PORT AURELIA"
	var fs := 64
	var tw := font.get_string_size(title, HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x
	draw_string(font, Vector2((w - tw) * 0.5 + 3, 168 + 3), title, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, Color(0, 0, 0, 0.5))
	draw_string(font, Vector2((w - tw) * 0.5, 168), title, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, Color(1, 1, 1))
	var sub := "O P E N   C I T Y"
	var sw := font.get_string_size(sub, HORIZONTAL_ALIGNMENT_LEFT, -1, 20).x
	draw_string(font, Vector2((w - sw) * 0.5, 198), sub, HORIZONTAL_ALIGNMENT_LEFT, -1, 20, Color(1.0, 0.75, 0.45))
