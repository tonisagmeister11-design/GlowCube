class_name JailScreen
extends CanvasLayer
## Black "county jail" screen after an arrest or surrender: the sentence ticks by day by day,
## then the summary (time served, fine, debt) before the player walks out of the station.

signal done


static func play(parent: Node, weeks: int, fine: int, surrendered: bool) -> JailScreen:
	var j := JailScreen.new()
	j.layer = 40
	j.process_mode = Node.PROCESS_MODE_ALWAYS
	parent.add_child(j)
	j._run(weeks, fine, surrendered)
	return j


func _label(root: Control, size: int, col: Color, y: float) -> Label:
	var l := Label.new()
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", col)
	l.add_theme_color_override("font_outline_color", Color(0, 0, 0))
	l.add_theme_constant_override("outline_size", 6)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.anchor_left = 0.0
	l.anchor_right = 1.0
	l.anchor_top = y
	l.anchor_bottom = y
	l.offset_top = -size
	l.offset_bottom = size
	root.add_child(l)
	return l


func _run(weeks: int, fine: int, surrendered: bool) -> void:
	var root := Control.new()
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(root)
	var bg := ColorRect.new()
	bg.color = Color(0.02, 0.02, 0.03)
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.add_child(bg)
	# a few cell bars
	for i in 9:
		var bar := ColorRect.new()
		bar.color = Color(0.12, 0.12, 0.14)
		bar.anchor_left = 0.1 + i * 0.1
		bar.anchor_right = bar.anchor_left
		bar.anchor_bottom = 1.0
		bar.offset_left = -6
		bar.offset_right = 6
		root.add_child(bar)
	var head := _label(root, 30, Color(0.85, 0.85, 0.9), 0.24)
	head.text = "PORT AURELIA COUNTY JAIL"
	var title := _label(root, 64, Color(1.0, 0.85, 0.3), 0.36)
	title.text = ("ERGEBEN" if surrendered else "VERHAFTET") + "  –  %d WOCHEN HAFT" % weeks
	var days := _label(root, 44, Color(1, 1, 1), 0.52)
	var summary := _label(root, 28, Color(0.9, 0.9, 0.9), 0.66)
	var money := _label(root, 32, Color(0.95, 0.35, 0.3), 0.74)
	root.modulate.a = 0.0
	var tw := create_tween()
	tw.tween_property(root, "modulate:a", 1.0, 0.5)
	await tw.finished
	var total := weeks * 7
	for d in range(1, total + 1):
		days.text = "Tag %d / %d" % [d, total]
		await get_tree().create_timer(2.2 / total).timeout
	summary.text = "Du hast %d Wochen (%d Tage) im Knast gesessen." % [weeks, total]
	var m: int = Game.player_data.money
	money.text = "Strafe: -$%d" % fine + (("   ·   Schulden: -$%d" % -m) if m < 0 else "")
	if not surrendered:
		money.text += "   ·   Waffen beschlagnahmt"
	await get_tree().create_timer(3.2).timeout
	var tw2 := create_tween()
	tw2.tween_property(root, "modulate:a", 0.0, 0.6)
	await tw2.finished
	done.emit()
	queue_free()
