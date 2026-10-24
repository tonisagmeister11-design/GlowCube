extends MenuScreen
## Assistent fuer ein neues Spiel: Spielmodus -> Stadt -> Charakter ->
## Startsituation -> Zusammenfassung -> Game.start_new_game(config).
## arg: Name des Startschritts ("mode", "city", "character", "start", "summary").

const STEPS := ["mode", "city", "character", "start", "summary"]
const STEP_LABELS := ["Spielmodus", "Stadt", "Charakter", "Startsituation", "Übersicht"]
const CREATOR_SCENE := "res://scenes/menu/character_creator.tscn"
const SIZE_INFO := {
	"small": ["Klein", "ca. 1,2 × 1,2 km", "Schnell erkundet, kürzere Ladezeit"],
	"medium": ["Mittel", "ca. 1,8 × 1,8 km", "Ausgewogen – empfohlen"],
	"large": ["Groß", "ca. 2,4 × 2,4 km", "Viele Stadtteile, längere Ladezeit"],
}
const SITUATIONS := {
	"auto": ["Automatisch", "sparkle", "Passend zu Alter und Spielmodus: Kinder leben bei den Eltern, Erwachsene starten in einer kleinen Wohnung."],
	"with_parents": ["Bei den Eltern", "users", "Du wohnst im Elternhaus. Deine Familie trägt Miete und Grundkosten."],
	"own_apartment": ["Eigene Wohnung", "key", "Eine kleine Mietwohnung. Die Miete ist jede Woche fällig."],
	"shared_room": ["WG-Zimmer", "home", "Günstig wohnen in einer Wohngemeinschaft – wenig Platz, wenig Kosten."],
	"homeless": ["Obdachlos", "warning", "Kein Dach über dem Kopf und kaum Geld. Eine harte Herausforderung."],
	"wealthy": ["Wohlhabend", "trophy", "Großes Startvermögen und ein eigenes Haus in guter Lage."],
}
const POP_DEFAULTS := {"life": 2400, "sandbox": 2400, "free": 2000}

var step := 0
var mode := "life"
var sandbox_values: Dictionary = GameRules.PRESETS["sandbox"].duplicate()
var seed_value := 0
var city_size := "medium"
var population := 2400
var situation := "auto"
var money_override := false
var money_value := 5000.0
var character: Dictionary = {}

var _stepper: HBoxContainer
var _page: VBoxContainer
var _creator: Control
var _creator_is_fallback := false
var _next_btn: Button
var _prev_btn: Button
var _error: Label
var _preview_rect: TextureRect
var _preview_info: Label
var _preview_task := -1
var _preview_result: Dictionary = {}
var _seed_edit: LineEdit


func on_open() -> void:
	var rng := RandomNumberGenerator.new()
	rng.randomize()
	seed_value = rng.randi_range(1000, 999999)
	setup_frame("Neues Leben", "Lege fest, wie dein Leben in Havenbrück beginnt.", "Neues Spiel", "play")
	_stepper = UIKit.hbox(0)
	content.add_child(_stepper)
	_page = UIKit.vbox(16)
	_page.size_flags_vertical = SIZE_EXPAND_FILL
	content.add_child(_page)
	var cancel := UIKit.button("Abbrechen", "", "GhostButton")
	cancel.pressed.connect(func() -> void: back_requested.emit())
	footer.add_child(cancel)
	_error = UIKit.label("", "CaptionLabel")
	_error.add_theme_color_override("font_color", UITheme.DANGER)
	footer.add_child(_error)
	footer.add_child(UIKit.spacer())
	_prev_btn = UIKit.button("Zurück", "chevron_left", "", 150)
	_prev_btn.pressed.connect(func() -> void: go_to(step - 1))
	footer.add_child(_prev_btn)
	_next_btn = UIKit.button("Weiter", "chevron_right", "AccentButton", 220)
	_next_btn.icon_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	_next_btn.pressed.connect(_on_next)
	footer.add_child(_next_btn)
	_make_creator()
	var start_step := 0
	if arg is String and str(arg) in STEPS:
		start_step = STEPS.find(str(arg))
	go_to(start_step)


func _exit_tree() -> void:
	if _preview_task >= 0:
		WorkerThreadPool.wait_for_task_completion(_preview_task)
		_preview_task = -1
	if _creator and not _creator.is_inside_tree():
		_creator.free()


func can_go_back() -> bool:
	if step > 0:
		go_to(step - 1)
		return false
	return true


# ================================================================ Schritte

func go_to(i: int) -> void:
	i = clampi(i, 0, STEPS.size() - 1)
	if i > step and not _validate(step):
		return
	_capture_character()
	step = i
	_error.text = ""
	_build_stepper()
	if _creator and _creator.get_parent():
		_creator.get_parent().remove_child(_creator)
	UIKit.clear(_page)
	match STEPS[step]:
		"mode": _page_mode()
		"city": _page_city()
		"character": _page_character()
		"start": _page_start()
		"summary": _page_summary()
	_prev_btn.visible = step > 0
	var last := step == STEPS.size() - 1
	_next_btn.text = "Leben beginnen" if last else "Weiter"
	_next_btn.icon = UIKit.icon("play" if last else "chevron_right")
	UIKit.stagger(_page, 0.04)


func _on_next() -> void:
	if step == STEPS.size() - 1:
		_start_game()
	else:
		go_to(step + 1)


func _validate(s: int) -> bool:
	if STEPS[s] == "character":
		_capture_character()
		if str(character.get("name", "")).strip_edges() == "":
			_error.text = "Bitte gib deiner Figur einen Namen."
			return false
	if STEPS[s] == "city" and _seed_edit and not _seed_edit.text.strip_edges().is_valid_int():
		_error.text = "Der Seed muss eine ganze Zahl sein."
		return false
	return true


func _build_stepper() -> void:
	UIKit.clear(_stepper)
	for i in STEPS.size():
		var done := i < step
		var active := i == step
		var item := UIKit.hbox(10)
		var circle := PanelContainer.new()
		var sb := StyleBoxFlat.new()
		sb.set_corner_radius_all(16)
		sb.bg_color = UITheme.ACCENT if (done or active) else Color(1, 1, 1, 0.06)
		sb.border_color = UITheme.ACCENT if active else Color(1, 1, 1, 0.16)
		sb.set_border_width_all(0 if done else 1)
		if active:
			sb.shadow_color = Color(UITheme.ACCENT, 0.45)
			sb.shadow_size = 10
		circle.add_theme_stylebox_override("panel", sb)
		circle.custom_minimum_size = Vector2(32, 32)
		circle.mouse_filter = MOUSE_FILTER_IGNORE
		if done:
			circle.add_child(UIKit.icon_rect("check", 18))
		else:
			var n := UIKit.label(str(i + 1), "ValueLabel")
			n.add_theme_font_size_override("font_size", 16)
			n.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
			n.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
			circle.add_child(n)
		item.add_child(circle)
		var l := UIKit.label(STEP_LABELS[i], "")
		l.add_theme_font_override("font", UITheme.font(620 if active else 500))
		l.add_theme_color_override("font_color", Color.WHITE if active else (UITheme.TEXT_MUTED if done else UITheme.TEXT_FAINT))
		item.add_child(l)
		# Klickbar fuer bereits erreichte Schritte
		if i < step:
			item.mouse_filter = MOUSE_FILTER_STOP
			item.mouse_default_cursor_shape = CURSOR_POINTING_HAND
			item.tooltip_text = "Zu „%s“ zurückkehren" % STEP_LABELS[i]
			item.gui_input.connect(func(ev: InputEvent) -> void:
				if ev is InputEventMouseButton and ev.pressed and ev.button_index == MOUSE_BUTTON_LEFT:
					go_to(i))
		_stepper.add_child(item)
		if i < STEPS.size() - 1:
			var line := ColorRect.new()
			line.color = UITheme.ACCENT if i < step else Color(1, 1, 1, 0.12)
			line.custom_minimum_size = Vector2(40, 2)
			line.size_flags_horizontal = SIZE_EXPAND_FILL
			line.size_flags_vertical = SIZE_SHRINK_CENTER
			var lm := UIKit.margin(line, 14, 0, 14, 0)
			lm.size_flags_horizontal = SIZE_EXPAND_FILL
			lm.size_flags_vertical = SIZE_SHRINK_CENTER
			_stepper.add_child(lm)


# ---------------------------------------------------------------- 1 Modus

func _page_mode() -> void:
	var scroll_v := UIKit.vbox(22)
	var cards := UIKit.hbox(18)
	for m in ["life", "sandbox", "free"]:
		cards.add_child(_mode_card(m))
	scroll_v.add_child(cards)
	if mode == "sandbox":
		scroll_v.add_child(UIKit.section("Regeln einstellen", "sliders"))
		var ed := UIRulesEditor.new(sandbox_values)
		ed.changed.connect(func(v: Dictionary) -> void: sandbox_values = v.duplicate())
		scroll_v.add_child(ed)
	else:
		var head := UIKit.hbox(12)
		head.add_child(UIKit.section("Regeln von „%s“ im Überblick" % UIKit.mode_label(mode), "book"))
		head.add_child(UIKit.spacer())
		head.add_child(UIKit.label("Diese Regeln gelten für den gesamten Spielstand.", "CaptionLabel"))
		scroll_v.add_child(head)
		scroll_v.add_child(UIRulesSummary.build(GameRules.make(mode, {"city_size": city_size, "population": population}), 3))
	_page.add_child(UIKit.scroll(scroll_v))


func _mode_card(m: String) -> Control:
	var card := UISelectCard.new(UITheme.RADIUS_L, 24)
	card.size_flags_horizontal = SIZE_EXPAND_FILL
	card.selected = m == mode
	var accent: Color = {"life": UITheme.ACCENT, "sandbox": UITheme.WARM, "free": UITheme.SUCCESS}[m]
	card.set_accent(accent)
	var v := UIKit.vbox(10)
	var top := UIKit.hbox(14)
	top.add_child(UIKit.icon_badge(UIKit.MODE_ICONS[m], accent, 56, 0.48))
	var tv := UIKit.vbox(0)
	tv.add_child(UIKit.label(GameRules.MODES[m].label, "TitleLabel"))
	var tags := {"life": "Volle Simulation", "sandbox": "Deine Regeln", "free": "Entspannt spielen"}
	tv.add_child(UIKit.label(tags[m], "CaptionLabel"))
	tv.size_flags_horizontal = SIZE_EXPAND_FILL
	top.add_child(tv)
	if m == mode:
		top.add_child(UIKit.icon_rect("check_circle", 26, accent))
	v.add_child(top)
	var d := UIKit.label(GameRules.MODES[m].desc, "SubtitleLabel", true)
	v.add_child(d)
	v.add_child(UIKit.separator())
	for hl in UIRulesSummary.highlights(m):
		var h := UIKit.hbox(10)
		h.add_child(UIKit.icon_rect(hl[0], 18, accent))
		var l := UIKit.label(hl[1], "", true)
		l.add_theme_font_size_override("font_size", 16)
		l.add_theme_color_override("font_color", UITheme.TEXT_MUTED)
		l.size_flags_horizontal = SIZE_EXPAND_FILL
		h.add_child(l)
		v.add_child(h)
	card.add_child(v)
	card.pressed.connect(func() -> void:
		if mode != m:
			if population == POP_DEFAULTS[mode]:
				population = POP_DEFAULTS[m]
			mode = m
			go_to(step))
	card.double_pressed.connect(func() -> void:
		mode = m
		go_to(step + 1))
	return card


# ---------------------------------------------------------------- 2 Stadt

func _page_city() -> void:
	var cols := UIKit.hbox(20)
	cols.size_flags_vertical = SIZE_EXPAND_FILL
	var left := UIKit.vbox(16)
	left.size_flags_horizontal = SIZE_EXPAND_FILL
	left.size_flags_stretch_ratio = 1.15
	cols.add_child(left)
	# Seed
	var c1 := UIKit.card()
	var v1 := UIKit.vbox(10)
	v1.add_child(UIKit.section("Stadt-Seed", "dice"))
	v1.add_child(UIKit.label("Der Seed bestimmt Straßen, Gebäude und Bewohner. Gleicher Seed – gleiche Stadt.", "CaptionLabel", true))
	var sh := UIKit.hbox(10)
	_seed_edit = LineEdit.new()
	_seed_edit.text = str(seed_value)
	_seed_edit.size_flags_horizontal = SIZE_EXPAND_FILL
	_seed_edit.max_length = 10
	_seed_edit.add_theme_font_override("font", UITheme.font(600, false, true))
	_seed_edit.text_changed.connect(func(t: String) -> void:
		if t.strip_edges().is_valid_int():
			seed_value = int(t.strip_edges())
			_error.text = "")
	sh.add_child(_seed_edit)
	var dice := UIKit.button("Zufällig", "dice")
	dice.pressed.connect(func() -> void:
		var r := RandomNumberGenerator.new()
		r.randomize()
		seed_value = r.randi_range(1000, 999999)
		_seed_edit.text = str(seed_value)
		_clear_preview())
	sh.add_child(dice)
	v1.add_child(sh)
	c1.add_child(v1)
	left.add_child(c1)
	# Groesse
	var c2 := UIKit.card()
	var v2 := UIKit.vbox(10)
	v2.add_child(UIKit.section("Stadtgröße", "city"))
	var sizes := UIKit.hbox(12)
	for s in ["small", "medium", "large"]:
		var sc := UISelectCard.new(UITheme.RADIUS_M, 16)
		sc.size_flags_horizontal = SIZE_EXPAND_FILL
		sc.selected = s == city_size
		var sv := UIKit.vbox(2)
		sv.add_child(UIKit.label(SIZE_INFO[s][0], "TitleLabel"))
		sv.add_child(UIKit.label(SIZE_INFO[s][1], "ValueLabel"))
		sv.add_child(UIKit.label(SIZE_INFO[s][2], "CaptionLabel", true))
		sc.add_child(sv)
		sc.pressed.connect(func() -> void:
			city_size = s
			_clear_preview()
			go_to(step))
		sizes.add_child(sc)
	v2.add_child(sizes)
	c2.add_child(v2)
	left.add_child(c2)
	# Einwohner
	var c3 := UIKit.card()
	var v3 := UIKit.vbox(10)
	var ph := UIKit.hbox(10)
	var ps := UIKit.section("Einwohner", "users")
	ps.size_flags_horizontal = SIZE_EXPAND_FILL
	ph.add_child(ps)
	var pv := UIKit.label(UIKit.group(population), "BigValueLabel")
	ph.add_child(pv)
	v3.add_child(ph)
	v3.add_child(UIKit.label("Alle Bewohner werden simuliert: Wohnung, Arbeit, Einkäufe und Beziehungen. Mehr Einwohner beleben die Stadt, benötigen aber mehr Rechenleistung.", "CaptionLabel", true))
	var s: Dictionary = GameRules.SCHEMA["population"]
	var sl := HSlider.new()
	sl.min_value = s.min
	sl.max_value = s.max
	sl.step = s.step
	sl.value = population
	sl.custom_minimum_size.y = 30
	sl.value_changed.connect(func(x: float) -> void:
		population = int(x)
		pv.text = UIKit.group(population))
	v3.add_child(sl)
	c3.add_child(v3)
	left.add_child(c3)
	# Vorschau
	var right := UIKit.card()
	right.size_flags_horizontal = SIZE_EXPAND_FILL
	right.size_flags_vertical = SIZE_EXPAND_FILL
	var rv := UIKit.vbox(12)
	var rh := UIKit.hbox(10)
	var title := UIKit.section(CityCatalog.CITY_NAME_DISPLAY, "map")
	title.size_flags_horizontal = SIZE_EXPAND_FILL
	rh.add_child(title)
	var gen := UIKit.button("Vorschau erzeugen", "refresh")
	gen.pressed.connect(_make_preview)
	rh.add_child(gen)
	rv.add_child(rh)
	var frame := PanelContainer.new()
	frame.add_theme_stylebox_override("panel", UITheme.box(Color(0, 0, 0, 0.3), UITheme.RADIUS_M, UITheme.BORDER, 1, 0, 0))
	frame.size_flags_vertical = SIZE_EXPAND_FILL
	frame.clip_contents = true
	_preview_rect = TextureRect.new()
	_preview_rect.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_preview_rect.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	_preview_rect.custom_minimum_size = Vector2(300, 300)
	frame.add_child(_preview_rect)
	_preview_info = UIKit.label("Erzeuge eine Vorschau, um den Stadtplan dieses Seeds zu sehen.", "CaptionLabel", true)
	_preview_info.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_preview_info.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	frame.add_child(_preview_info)
	rv.add_child(frame)
	right.add_child(rv)
	cols.add_child(right)
	_page.add_child(cols)
	if _preview_result.get("seed", -1) == seed_value and _preview_result.get("size", "") == city_size:
		_show_preview()


func _clear_preview() -> void:
	_preview_result = {}
	if _preview_rect:
		_preview_rect.texture = null
		_preview_info.text = "Erzeuge eine Vorschau, um den Stadtplan dieses Seeds zu sehen."
		_preview_info.visible = true


func _make_preview() -> void:
	if _preview_task >= 0:
		return
	_preview_info.visible = true
	_preview_info.text = "Stadtplan wird erzeugt ..."
	var sd := seed_value
	var sz := city_size
	_preview_task = WorkerThreadPool.add_task(func() -> void:
		var m := CityGenerator.generate(sd, {"size": sz})
		var img := CityMapRenderer.render(m, 0.32)
		var info := "%d Stadtteile · %d Gebäude · %d Buslinien" % [m.districts.size(), m.buildings.size(), m.bus_lines.size()]
		call_deferred("_preview_done", sd, sz, img, info))


func _preview_done(sd: int, sz: String, img: Image, info: String) -> void:
	if _preview_task >= 0:
		WorkerThreadPool.wait_for_task_completion(_preview_task)
		_preview_task = -1
	_preview_result = {"seed": sd, "size": sz, "image": img, "info": info}
	if is_inside_tree() and STEPS[step] == "city":
		_show_preview()


func _show_preview() -> void:
	if _preview_rect == null or not is_instance_valid(_preview_rect):
		return
	_preview_rect.texture = ImageTexture.create_from_image(_preview_result.image)
	_preview_info.text = str(_preview_result.info)
	_preview_info.vertical_alignment = VERTICAL_ALIGNMENT_BOTTOM
	UIKit.appear(_preview_rect, 0.0, Vector2.ZERO)


# ---------------------------------------------------------------- 3 Charakter

func _make_creator() -> void:
	if ResourceLoader.exists(CREATOR_SCENE):
		var ps: PackedScene = load(CREATOR_SCENE)
		if ps:
			_creator = ps.instantiate()
	if _creator == null or not _creator.has_method("get_character"):
		if _creator:
			_creator.free()
		_creator = CharacterFallback.new()
		_creator_is_fallback = true
	_creator.size_flags_vertical = SIZE_EXPAND_FILL
	_creator.size_flags_horizontal = SIZE_EXPAND_FILL
	if _creator.has_signal("changed"):
		_creator.connect("changed", func(c) -> void:
			if c is Dictionary:
				character = c.duplicate(true))
	_capture_character()


func _capture_character() -> void:
	if _creator and _creator.has_method("get_character"):
		var c = _creator.call("get_character")
		if c is Dictionary:
			character = c.duplicate(true)


func _page_character() -> void:
	if _creator_is_fallback:
		_page.add_child(UIKit.scroll(_creator))
		# ScrollContainer wird beim naechsten Schritt geleert; Editor bleibt erhalten
	else:
		_page.add_child(_creator)


# ---------------------------------------------------------------- 4 Start

func _allowed_situations() -> Array:
	var age := float(character.get("age", 24.0))
	if age < 18.0:
		return ["auto", "with_parents"]
	return ["auto", "with_parents", "own_apartment", "shared_room", "homeless", "wealthy"]


func _page_start() -> void:
	var allowed := _allowed_situations()
	if not situation in allowed:
		situation = "auto"
	var v := UIKit.vbox(18)
	var age := float(character.get("age", 24.0))
	if age < 18.0:
		v.add_child(UIKit.pill("Minderjährige Figuren beginnen bei ihren Eltern.", UITheme.ACCENT, "info"))
	var grid := GridContainer.new()
	grid.columns = 3
	grid.add_theme_constant_override("h_separation", 16)
	grid.add_theme_constant_override("v_separation", 16)
	for k in allowed:
		var info: Array = SITUATIONS[k]
		var c := UISelectCard.new(UITheme.RADIUS_L, 20)
		c.size_flags_horizontal = SIZE_EXPAND_FILL
		c.selected = k == situation
		var col: Color = UITheme.DANGER if k == "homeless" else (UITheme.WARM if k == "wealthy" else UITheme.ACCENT)
		c.set_accent(col)
		var cv := UIKit.vbox(10)
		var top := UIKit.hbox(12)
		top.add_child(UIKit.icon_badge(info[1], col, 46, 0.48))
		var t := UIKit.label(info[0], "TitleLabel")
		t.size_flags_horizontal = SIZE_EXPAND_FILL
		top.add_child(t)
		if k == situation:
			top.add_child(UIKit.icon_rect("check_circle", 24, col))
		cv.add_child(top)
		cv.add_child(UIKit.label(info[2], "SubtitleLabel", true))
		if k == "homeless":
			cv.add_child(UIKit.pill("Schwierig", UITheme.DANGER))
		elif k == "auto":
			cv.add_child(UIKit.pill("Empfohlen", UITheme.SUCCESS))
		c.add_child(cv)
		c.custom_minimum_size.y = 170
		c.pressed.connect(func() -> void:
			situation = k
			go_to(step))
		grid.add_child(c)
	v.add_child(grid)
	if mode == "sandbox":
		var mc := UIKit.card()
		var mv := UIKit.vbox(10)
		var mh := UIKit.hbox(12)
		var st := UIKit.section("Eigenes Startgeld", "money")
		st.size_flags_horizontal = SIZE_EXPAND_FILL
		mh.add_child(st)
		var tg := CheckButton.new()
		tg.button_pressed = money_override
		mh.add_child(tg)
		mv.add_child(mh)
		mv.add_child(UIKit.label("Überschreibt das Startgeld der Regeln nur für diese Figur.", "CaptionLabel"))
		var sp := SpinBox.new()
		sp.min_value = 0
		sp.max_value = 1000000
		sp.step = 100
		sp.suffix = "€"
		sp.value = money_value
		sp.editable = money_override
		sp.custom_minimum_size.x = 260
		sp.size_flags_horizontal = SIZE_SHRINK_BEGIN
		sp.value_changed.connect(func(x: float) -> void: money_value = x)
		tg.toggled.connect(func(on: bool) -> void:
			money_override = on
			sp.editable = on)
		mv.add_child(sp)
		mc.add_child(mv)
		v.add_child(mc)
	_page.add_child(UIKit.scroll(v))


# ---------------------------------------------------------------- 5 Uebersicht

func _page_summary() -> void:
	var v := UIKit.vbox(20)
	var row := UIKit.hbox(16)
	var age := float(character.get("age", 24.0))
	row.add_child(_summary_card("Spielmodus", UIKit.MODE_ICONS[mode], UIKit.mode_label(mode),
		GameRules.MODES[mode].desc, 0))
	row.add_child(_summary_card("Stadt", "city", CityCatalog.CITY_NAME_DISPLAY,
		"Seed %d · %s · %s Einwohner" % [seed_value, SIZE_INFO[city_size][0], UIKit.group(population)], 2))
	row.add_child(_summary_card("Charakter", "user", str(character.get("name", "")),
		"%s · %s · %s" % [UIKit.years(age), UIKit.life_stage_label(UIKit.life_stage_for_age(age)),
			UIKit.BODY_LABELS.get(character.get("body", ""), "")], 3))
	var start_txt: String = SITUATIONS[situation][0]
	if mode == "sandbox" and money_override:
		start_txt += " · " + UIKit.money(money_value, 0)
	row.add_child(_summary_card("Startsituation", SITUATIONS[situation][1], start_txt, SITUATIONS[situation][2], 4))
	v.add_child(row)
	var head := UIKit.hbox(12)
	head.add_child(UIKit.section("Regeln dieses Spielstands", "book"))
	v.add_child(head)
	var rules := GameRules.make(mode, build_config().rules)
	v.add_child(UIRulesSummary.build(rules, 3))
	_page.add_child(UIKit.scroll(v))


func _summary_card(title: String, icon_name: String, value: String, desc: String, edit_step: int) -> Control:
	var c := UIKit.card()
	c.size_flags_horizontal = SIZE_EXPAND_FILL
	var v := UIKit.vbox(8)
	var h := UIKit.hbox(10)
	h.add_child(UIKit.icon_badge(icon_name, UITheme.ACCENT, 40, 0.5))
	var e := UIKit.eyebrow(title)
	e.size_flags_horizontal = SIZE_EXPAND_FILL
	h.add_child(e)
	var edit := UIKit.icon_button("edit", "Ändern", 36)
	edit.pressed.connect(go_to.bind(edit_step))
	h.add_child(edit)
	v.add_child(h)
	var val := UIKit.label(value, "TitleLabel", true)
	v.add_child(val)
	v.add_child(UIKit.label(desc, "CaptionLabel", true))
	c.add_child(v)
	return c


# ================================================================ Start

## Konfiguration im Format von ARCHITECTURE.md (Game.start_new_game).
func build_config() -> Dictionary:
	_capture_character()
	var rules := {}
	if mode == "sandbox":
		rules = sandbox_values.duplicate()
	rules["city_size"] = city_size
	rules["population"] = population
	var ch := character.duplicate(true)
	if not ch.has("appearance"):
		ch["appearance"] = {}
	ch["age"] = clampf(float(ch.get("age", 24.0)), 0.5, 100.0)
	var start := {"situation": situation}
	if mode == "sandbox" and money_override:
		start["money"] = money_value
	return {"seed": seed_value, "mode": mode, "rules": rules, "character": ch, "start": start}


func _start_game() -> void:
	if not _validate(STEPS.find("character")):
		go_to(STEPS.find("character"))
		return
	_next_btn.disabled = true
	UIKit.sound("confirm")
	Game.start_new_game(build_config())
