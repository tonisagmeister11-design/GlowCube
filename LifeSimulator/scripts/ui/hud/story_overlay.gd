extends Control
## Darstellung des Story-Modus im Spiel: Untertitel mit Sprechernamen,
## Auftragsanzeige und die grosse Handlungsaufforderung (zum Beispiel
## „SPRINGEN" beim Hinterhalt).
##
## Sprachaufnahmen: Wenn unter assets/audio/voice/voice_<sprecher>.wav eine
## eigene Aufnahme liegt, spielt der Story-Runner sie ab. Ohne Aufnahme
## erscheint nur der Untertitel – es wird bewusst keine kuenstliche Stimme
## erzeugt.

const SUB_SECONDS_BASE := 2.2
const SUB_SECONDS_PER_CHAR := 0.045

var _sub_box: PanelContainer
var _sub_speaker: Label
var _sub_line: Label
var _sub_timer := 0.0
var _queue: Array = []
var _prompt_box: PanelContainer
var _prompt_label: Label
var _prompt_hint: Label
var _prompt_bar: ProgressBar
var _prompt_time := 0.0
var _prompt_total := 0.0
var _mission_box: PanelContainer
var _mission_title: Label
var _mission_step: Label
var _bar_top: ColorRect
var _bar_bottom: ColorRect


func _ready() -> void:
	set_anchors_preset(PRESET_FULL_RECT)
	mouse_filter = MOUSE_FILTER_IGNORE
	_build()
	EventBus.story_line.connect(_on_line)
	EventBus.story_scripted_started.connect(func(_k): _set_letterbox(true))
	EventBus.story_scripted_finished.connect(func(_k, _s): _set_letterbox(false))
	EventBus.story_prompt.connect(_on_prompt)
	EventBus.story_prompt_cleared.connect(_clear_prompt)
	EventBus.story_mission_started.connect(func(_id): _refresh_mission())
	EventBus.story_step_changed.connect(func(_id, _s): _refresh_mission())
	EventBus.story_mission_completed.connect(func(_id, _m): _refresh_mission())
	EventBus.story_mission_failed.connect(func(_id, _t): _refresh_mission())
	_refresh_mission()


func _build() -> void:
	# Kinobalken oben und unten, fahren waehrend inszenierter Abschnitte ein
	_bar_top = ColorRect.new()
	_bar_top.color = Color(0, 0, 0, 1)
	_bar_top.set_anchors_preset(PRESET_TOP_WIDE)
	_bar_top.custom_minimum_size = Vector2(0, 0)
	_bar_top.size.y = 0.0
	_bar_top.mouse_filter = MOUSE_FILTER_IGNORE
	add_child(_bar_top)
	_bar_bottom = ColorRect.new()
	_bar_bottom.color = Color(0, 0, 0, 1)
	_bar_bottom.set_anchors_preset(PRESET_BOTTOM_WIDE)
	_bar_bottom.size.y = 0.0
	_bar_bottom.mouse_filter = MOUSE_FILTER_IGNORE
	add_child(_bar_bottom)

	# Untertitel unten mittig, im Stil eines Films
	_sub_box = PanelContainer.new()
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.02, 0.03, 0.05, 0.86)
	sb.set_corner_radius_all(8)
	sb.set_content_margin_all(14)
	sb.border_color = Color(1.0, 0.55, 0.24, 0.5)
	sb.border_width_left = 3
	_sub_box.add_theme_stylebox_override("panel", sb)
	_sub_box.set_anchors_preset(PRESET_CENTER_BOTTOM)
	_sub_box.grow_horizontal = GROW_DIRECTION_BOTH
	_sub_box.position = Vector2(0, -210)
	_sub_box.custom_minimum_size = Vector2(760, 0)
	_sub_box.visible = false
	_sub_box.mouse_filter = MOUSE_FILTER_IGNORE
	add_child(_sub_box)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 2)
	_sub_box.add_child(v)
	_sub_speaker = Label.new()
	_sub_speaker.theme_type_variation = "CaptionLabel"
	v.add_child(_sub_speaker)
	_sub_line = Label.new()
	_sub_line.theme_type_variation = "TitleLabel"
	_sub_line.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_sub_line.custom_minimum_size = Vector2(730, 0)
	v.add_child(_sub_line)

	# Grosse Handlungsaufforderung in der Bildmitte
	_prompt_box = PanelContainer.new()
	var pb := StyleBoxFlat.new()
	pb.bg_color = Color(0.5, 0.06, 0.04, 0.9)
	pb.set_corner_radius_all(12)
	pb.set_content_margin_all(18)
	pb.border_color = Color(1.0, 0.85, 0.4, 0.9)
	pb.set_border_width_all(2)
	_prompt_box.add_theme_stylebox_override("panel", pb)
	_prompt_box.set_anchors_preset(PRESET_CENTER)
	_prompt_box.grow_horizontal = GROW_DIRECTION_BOTH
	_prompt_box.grow_vertical = GROW_DIRECTION_BOTH
	_prompt_box.position = Vector2(0, -120)
	_prompt_box.visible = false
	_prompt_box.mouse_filter = MOUSE_FILTER_IGNORE
	add_child(_prompt_box)
	var pv := VBoxContainer.new()
	pv.add_theme_constant_override("separation", 6)
	_prompt_box.add_child(pv)
	_prompt_label = Label.new()
	_prompt_label.theme_type_variation = "HeaderLabel"
	_prompt_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	pv.add_child(_prompt_label)
	_prompt_hint = Label.new()
	_prompt_hint.theme_type_variation = "SubtitleLabel"
	_prompt_hint.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	pv.add_child(_prompt_hint)
	_prompt_bar = ProgressBar.new()
	_prompt_bar.show_percentage = false
	_prompt_bar.custom_minimum_size = Vector2(320, 10)
	pv.add_child(_prompt_bar)

	# Auftragsanzeige oben mittig
	_mission_box = PanelContainer.new()
	_mission_box.theme_type_variation = "GlassPanel"
	_mission_box.set_anchors_preset(PRESET_CENTER_TOP)
	_mission_box.grow_horizontal = GROW_DIRECTION_BOTH
	_mission_box.position = Vector2(0, 18)
	_mission_box.visible = false
	_mission_box.mouse_filter = MOUSE_FILTER_IGNORE
	add_child(_mission_box)
	var mv := VBoxContainer.new()
	mv.add_theme_constant_override("separation", 1)
	_mission_box.add_child(mv)
	_mission_title = Label.new()
	_mission_title.theme_type_variation = "TitleLabel"
	_mission_title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	mv.add_child(_mission_title)
	_mission_step = Label.new()
	_mission_step.theme_type_variation = "CaptionLabel"
	_mission_step.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	mv.add_child(_mission_step)


# ============================================================ Untertitel

func _on_line(speaker: String, line: String, radio: bool) -> void:
	_queue.append([speaker, line, radio])
	if not _sub_box.visible:
		_next_line()


func _next_line() -> void:
	if _queue.is_empty():
		_sub_box.visible = false
		return
	var e: Array = _queue.pop_front()
	var sp: Dictionary = StoryCatalog.speaker(str(e[0]))
	var name: String = str(sp.name)
	if name == "" and Game.world != null and Game.world.player != null:
		name = str(Game.world.player.name)
	_sub_speaker.text = ("%s  ·  Funk" % name) if bool(e[2]) else name
	_sub_speaker.modulate = sp.get("color", Color.WHITE)
	_sub_line.text = str(e[1])
	_sub_box.visible = true
	_sub_box.modulate.a = 0.0
	var tw := create_tween()
	tw.tween_property(_sub_box, "modulate:a", 1.0, 0.18)
	_sub_timer = SUB_SECONDS_BASE + str(e[1]).length() * SUB_SECONDS_PER_CHAR


func _process(delta: float) -> void:
	if _sub_box.visible:
		_sub_timer -= delta
		if _sub_timer <= 0.0:
			_next_line()
	if _prompt_box.visible:
		_prompt_time -= delta
		_prompt_bar.value = 100.0 * clampf(_prompt_time / maxf(_prompt_total, 0.01), 0.0, 1.0)
		# Pulsieren, damit die Aufforderung ins Auge faellt
		var pulse := 1.0 + sin(Time.get_ticks_msec() * 0.012) * 0.04
		_prompt_box.scale = Vector2(pulse, pulse)
		_prompt_box.pivot_offset = _prompt_box.size * 0.5
		if _prompt_time <= 0.0:
			_clear_prompt()


# ============================================================ Aufforderung

func _on_prompt(label: String, hint: String) -> void:
	var key := Settings.binding_label("jump")
	_prompt_label.text = "%s   [%s]" % [label, key]
	_prompt_hint.text = hint
	_prompt_box.visible = true
	_prompt_total = 4.5
	_prompt_time = _prompt_total
	var audio := get_node_or_null("/root/Audio")
	if audio != null and audio.has_method("play_ui"):
		audio.play_ui("error")


func _clear_prompt() -> void:
	_prompt_box.visible = false
	_prompt_box.scale = Vector2.ONE


# ============================================================ Auftrag

func _refresh_mission() -> void:
	var w := Game.world
	if w == null or w.get("story") == null or not w.story.is_active():
		_mission_box.visible = false
		return
	var d: Dictionary = w.story.mission_def()
	var st: Dictionary = w.story.current_step()
	_mission_title.text = str(d.get("title", ""))
	_mission_step.text = "Schritt %d von %d · %s" % [int(w.story.step) + 1, w.story.steps_total(), str(st.get("text", ""))]
	_mission_box.visible = true


## Kinobalken ein- und ausfahren (wie in der Filmvorlage).
func _set_letterbox(on: bool) -> void:
	var h: float = size.y * 0.10 if on else 0.0
	for bar in [_bar_top, _bar_bottom]:
		if bar == null:
			continue
		var tw := create_tween()
		tw.tween_property(bar, "size:y", h, 1.0).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
		if bar == _bar_bottom:
			tw.parallel().tween_property(bar, "position:y", size.y - h, 1.0).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	# Untertitel oberhalb des unteren Balkens halten
	if _sub_box != null:
		_sub_box.position.y = -210.0 - h
