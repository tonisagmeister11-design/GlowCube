class_name CharacterCreator
extends Control
## Charaktereditor: 3D-Vorschau links, Optionen in Reitern rechts.
## Wird vom Neues-Spiel-Assistenten eingebettet (scenes/menu/character_creator.tscn).
## Zeigt ausschliesslich Optionen aus `Appearance`, die `Humanoid` darstellt,
## und blendet altersabhaengig nicht verfuegbare Optionen aus.
##
## API: `get_character() -> {name, body, age, appearance}`, `set_character(d)`,
## Signal `changed(character)` bei jeder Aenderung durch den Spieler.

signal changed(character: Dictionary)

const PREVIEW_SCENE := "res://scenes/character/character_preview.tscn"

var _name := ""
var _body := "female"
var _age := 24.0
var _app: Dictionary = {}
var _rng := RandomNumberGenerator.new()

var preview: CharacterPreview
var tabs: TabContainer
var _name_edit: LineEdit
var _age_slider: HSlider
var _years: SpinBox
var _months: SpinBox
var _months_box: Control
var _stage_label: Label
var _stage_buttons := {}
var _body_buttons := {}
var _pages := {}
var _updating := false
var _last_stage := ""
var _focus_buttons := {}


func _ready() -> void:
	_rng.randomize()
	if _app.is_empty():
		_body = "female" if _rng.randf() < 0.5 else "male"
		_app = Appearance.random(_rng, _body, _age)
		_name = Appearance.random_name(_rng, _body)
	_build_ui()
	_refresh_all()


# ================================================================== API

func get_character() -> Dictionary:
	return {"name": _name.strip_edges() if _name.strip_edges() != "" else "Namenlos", "body": _body, "age": _age,
		"appearance": Appearance.validate(_app, _age)}


func set_character(d: Dictionary) -> void:
	_name = str(d.get("name", _name))
	_body = "male" if str(d.get("body", _body)) == "male" else "female"
	_age = clampf(float(d.get("age", _age)), Appearance.MIN_AGE, Appearance.MAX_AGE)
	_app = Appearance.validate(d.get("appearance", _app), _age)
	if is_inside_tree() and tabs:
		_refresh_all()


# ================================================================== Aufbau

func _icon(icon_name: String) -> Texture2D:
	var path := "res://assets/icons/%s.svg" % icon_name
	if ResourceLoader.exists(path):
		return load(path)
	return null


func _label(text: String, variation := "") -> Label:
	var l := Label.new()
	l.text = text
	if variation != "":
		l.theme_type_variation = variation
	return l


func _button(text: String, variation: String, icon_name := "") -> Button:
	var b := Button.new()
	b.text = text
	b.theme_type_variation = variation
	var ic := _icon(icon_name) if icon_name != "" else null
	if ic:
		b.icon = ic
		b.expand_icon = false
	return b


func _build_ui() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	var root := HBoxContainer.new()
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.add_theme_constant_override("separation", 16)
	add_child(root)
	# ---------------------------------------- Vorschau
	var left := VBoxContainer.new()
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	left.size_flags_stretch_ratio = 0.9
	root.add_child(left)
	var pv_card := PanelContainer.new()
	pv_card.theme_type_variation = "CardPanel"
	pv_card.size_flags_vertical = Control.SIZE_EXPAND_FILL
	left.add_child(pv_card)
	preview = load(PREVIEW_SCENE).instantiate()
	preview.size_flags_vertical = Control.SIZE_EXPAND_FILL
	pv_card.add_child(preview)
	var pbar := HBoxContainer.new()
	pbar.alignment = BoxContainer.ALIGNMENT_CENTER
	pbar.add_theme_constant_override("separation", 8)
	left.add_child(pbar)
	var fg := ButtonGroup.new()
	for f in [["body", "Ganzkörper", "person"], ["face", "Gesicht", "face"]]:
		var b := _button(f[1], "ChipButton", f[2])
		b.toggle_mode = true
		b.button_group = fg
		b.button_pressed = f[0] == "body"
		b.pressed.connect(func(): preview.focus(f[0]))
		pbar.add_child(b)
		_focus_buttons[f[0]] = b
	var turn := _button("Drehen", "ChipButton", "rotate")
	turn.toggle_mode = true
	turn.toggled.connect(func(on: bool): preview.set_auto_turn(on))
	pbar.add_child(turn)
	var anim := OptionButton.new()
	for a in [["idle", "Stehen"], ["walk", "Gehen"], ["wave", "Winken"], ["talk", "Sprechen"], ["sit", "Sitzen"]]:
		anim.add_item(a[1])
		anim.set_item_metadata(anim.item_count - 1, a[0])
	anim.item_selected.connect(func(i: int): preview.set_action(str(anim.get_item_metadata(i))))
	pbar.add_child(anim)
	var hint := _label("Ziehen zum Drehen, Mausrad zum Zoomen", "CaptionLabel")
	hint.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	left.add_child(hint)
	# ---------------------------------------- Optionen
	var right := VBoxContainer.new()
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	right.add_theme_constant_override("separation", 10)
	root.add_child(right)
	var head := HBoxContainer.new()
	right.add_child(head)
	var title := _label("Charakter gestalten", "TitleLabel")
	title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	head.add_child(title)
	var rnd := _button("Zufälliges Aussehen", "GhostButton", "shuffle")
	rnd.pressed.connect(_on_randomize)
	head.add_child(rnd)
	tabs = TabContainer.new()
	tabs.size_flags_vertical = Control.SIZE_EXPAND_FILL
	right.add_child(tabs)
	for t in [["identity", "Identität"], ["looks", "Aussehen"], ["hair", "Haare"], ["clothes", "Kleidung"], ["acc", "Accessoires"]]:
		var sc := ScrollContainer.new()
		sc.name = t[1]
		sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
		var vb := VBoxContainer.new()
		vb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		vb.add_theme_constant_override("separation", 12)
		sc.add_child(vb)
		tabs.add_child(sc)
		_pages[t[0]] = vb


func _card(page: String, title: String) -> VBoxContainer:
	var card := PanelContainer.new()
	card.theme_type_variation = "CardPanel"
	card.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var vb := VBoxContainer.new()
	vb.add_theme_constant_override("separation", 8)
	card.add_child(vb)
	vb.add_child(_label(title, "HeaderLabel" if false else "TitleLabel"))
	(_pages[page] as VBoxContainer).add_child(card)
	return vb


func _clear_pages(keep_identity := false) -> void:
	for k in _pages:
		if keep_identity and k == "identity":
			continue
		for c in (_pages[k] as Node).get_children():
			c.queue_free()


# ---------------------------------------------------------------- Bausteine

func _chips(parent: Control, options: Array, current, on_pick: Callable) -> void:
	var flow := HFlowContainer.new()
	flow.add_theme_constant_override("h_separation", 6)
	flow.add_theme_constant_override("v_separation", 6)
	parent.add_child(flow)
	var group := ButtonGroup.new()
	for o in options:
		var b := _button(str(o.label), "ChipButton")
		b.toggle_mode = true
		b.button_group = group
		b.button_pressed = o.id == current
		var oid = o.id
		b.pressed.connect(func(): on_pick.call(oid))
		flow.add_child(b)


func _swatches(parent: Control, palette: Array, current: Color, on_pick: Callable, allow_custom := true) -> void:
	var flow := HFlowContainer.new()
	flow.add_theme_constant_override("h_separation", 6)
	flow.add_theme_constant_override("v_separation", 6)
	parent.add_child(flow)
	var group := ButtonGroup.new()
	for e in palette:
		var c: Color = e.color
		var b := Button.new()
		b.theme_type_variation = "ChipButton"
		b.toggle_mode = true
		b.button_group = group
		b.custom_minimum_size = Vector2(38, 38)
		b.tooltip_text = str(e.label)
		b.button_pressed = _close(c, current)
		var sw := ColorRect.new()
		sw.color = c
		sw.mouse_filter = Control.MOUSE_FILTER_IGNORE
		sw.set_anchors_preset(Control.PRESET_FULL_RECT)
		sw.offset_left = 6
		sw.offset_top = 6
		sw.offset_right = -6
		sw.offset_bottom = -6
		b.add_child(sw)
		b.pressed.connect(func(): on_pick.call(c))
		flow.add_child(b)
	if allow_custom:
		var cp := ColorPickerButton.new()
		cp.custom_minimum_size = Vector2(38, 38)
		cp.color = current
		cp.edit_alpha = false
		cp.tooltip_text = "Eigene Farbe"
		cp.color_changed.connect(func(c: Color): on_pick.call(c))
		flow.add_child(cp)


static func _close(a: Color, b: Color) -> bool:
	return absf(a.r - b.r) + absf(a.g - b.g) + absf(a.b - b.b) < 0.09


func _slider(parent: Control, text: String, value: float, on_change: Callable, left_text := "", right_text := "") -> void:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 10)
	parent.add_child(row)
	var l := _label(text)
	l.custom_minimum_size.x = 150
	row.add_child(l)
	if left_text != "":
		row.add_child(_label(left_text, "CaptionLabel"))
	var s := HSlider.new()
	s.min_value = 0.0
	s.max_value = 1.0
	s.step = 0.01
	s.value = value
	s.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	s.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	s.value_changed.connect(func(v: float): on_change.call(v))
	row.add_child(s)
	if right_text != "":
		row.add_child(_label(right_text, "CaptionLabel"))


# ================================================================== Seiten

func _refresh_all() -> void:
	_rebuild_pages(true)


## Baut nur die Optionsseiten neu (Identitaet bleibt, z. B. waehrend der
## Altersregler gezogen wird).
func _refresh_options() -> void:
	_rebuild_pages(false)


func _rebuild_pages(full: bool) -> void:
	_updating = true
	_app = Appearance.validate(_app, _age)
	_last_stage = Appearance.life_stage(_age)
	_clear_pages(not full)
	if full:
		_page_identity()
	else:
		_sync_age_controls()
	_page_looks()
	_page_hair()
	_page_clothes()
	_page_acc()
	_updating = false
	_update_preview()


func _page_identity() -> void:
	var c := _card("identity", "Name")
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 8)
	c.add_child(row)
	_name_edit = LineEdit.new()
	_name_edit.text = _name
	_name_edit.placeholder_text = "Vor- und Nachname"
	_name_edit.max_length = 40
	_name_edit.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_name_edit.text_changed.connect(func(t: String):
		_name = t
		_emit())
	row.add_child(_name_edit)
	var rn := _button("Zufälliger Name", "GhostButton", "shuffle")
	rn.pressed.connect(func():
		_name = Appearance.random_name(_rng, _body)
		_name_edit.text = _name
		_emit())
	row.add_child(rn)
	var cb := _card("identity", "Körperdarstellung")
	_chips(cb, Appearance.BODIES, _body, func(id):
		_body = str(id)
		call_deferred("_refresh_options")
		_emit())
	var ca := _card("identity", "Alter")
	var flow := HFlowContainer.new()
	flow.add_theme_constant_override("h_separation", 6)
	flow.add_theme_constant_override("v_separation", 6)
	ca.add_child(flow)
	var group := ButtonGroup.new()
	_stage_buttons.clear()
	for st in Appearance.LIFE_STAGES:
		var b := _button(str(st.label), "ChipButton")
		b.toggle_mode = true
		b.button_group = group
		b.tooltip_text = "%s bis %s Jahre" % [str(int(st.min)), str(int(st.max))] if st.id != "senior" else "ab 65 Jahren"
		var def: float = st.default
		b.pressed.connect(func(): _set_age(def, true))
		flow.add_child(b)
		_stage_buttons[st.id] = b
	var srow := HBoxContainer.new()
	srow.add_theme_constant_override("separation", 10)
	ca.add_child(srow)
	_age_slider = HSlider.new()
	_age_slider.min_value = Appearance.MIN_AGE
	_age_slider.max_value = Appearance.MAX_AGE
	_age_slider.step = 1.0 / 12.0
	_age_slider.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_age_slider.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	_age_slider.value_changed.connect(func(v: float): _set_age(v, false))
	srow.add_child(_age_slider)
	var nrow := HBoxContainer.new()
	nrow.add_theme_constant_override("separation", 8)
	ca.add_child(nrow)
	nrow.add_child(_label("Genaues Alter:"))
	_years = SpinBox.new()
	_years.min_value = 0
	_years.max_value = 100
	_years.suffix = "Jahre"
	_years.value_changed.connect(func(_v): _from_spin())
	nrow.add_child(_years)
	var mb := HBoxContainer.new()
	mb.add_theme_constant_override("separation", 8)
	_months = SpinBox.new()
	_months.min_value = 0
	_months.max_value = 11
	_months.suffix = "Monate"
	_months.value_changed.connect(func(_v): _from_spin())
	mb.add_child(_months)
	nrow.add_child(mb)
	_months_box = mb
	_stage_label = _label("", "SubtitleLabel")
	ca.add_child(_stage_label)
	ca.add_child(_label("Das Alter bestimmt Körpergröße, Proportionen, Haltung, Gang, Tempo und das Ergrauen der Haare.", "CaptionLabel"))
	(ca.get_child(ca.get_child_count() - 1) as Label).autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_sync_age_controls()


func _page_looks() -> void:
	var c := _card("looks", "Hautton")
	_swatches(c, Appearance.SKIN_TONES, _app.skin, func(col: Color): _set_app("skin", col))
	var ce := _card("looks", "Augenfarbe")
	_swatches(ce, Appearance.EYE_COLORS, _app.eye_color, func(col: Color): _set_app("eye_color", col))
	var cf := _card("looks", "Gesichtszüge")
	for f in [["jaw", "Kiefer und Kinn", "schmal", "kräftig"], ["nose", "Nase", "klein", "groß"], ["brows", "Augenbrauen", "fein", "buschig"], ["cheeks", "Wangen", "flach", "voll"]]:
		var key: String = f[0]
		_slider(cf, f[1], _app.face[key], func(v: float): _set_sub("face", key, v), f[2], f[3])
	var cb := _card("looks", "Körperbau")
	for f in [["height", "Größe", "klein", "groß"], ["weight", "Gewicht", "schlank", "kräftig"], ["muscle", "Muskeln", "wenig", "viel"]]:
		var key: String = f[0]
		_slider(cb, f[1], _app.build[key], func(v: float): _set_sub("build", key, v), f[2], f[3])
	var cx := _card("looks", "Weitere Merkmale")
	_slider(cx, "Sommersprossen", _app.freckles, func(v: float): _set_app("freckles", v), "keine", "viele")
	if _age >= 13.0:
		cx.add_child(_label("Make-up"))
		_chips(cx, Appearance.MAKEUP, int(_app.makeup), func(id): _set_app("makeup", int(id)))


func _page_hair() -> void:
	var c := _card("hair", "Frisur")
	_chips(c, Appearance.options_for(Appearance.HAIR_STYLES, _age), int(_app.hair_style), func(id): _set_app("hair_style", int(id), true))
	var cc := _card("hair", "Haarfarbe")
	_swatches(cc, Appearance.HAIR_COLORS, _app.hair_color, func(col: Color): _set_app("hair_color", col))
	var cap := _label("Graue und weiße Strähnen entstehen mit zunehmendem Alter automatisch.", "CaptionLabel")
	cap.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	cc.add_child(cap)
	var beards := Appearance.options_for(Appearance.BEARDS, _age)
	if beards.size() > 1:
		var cb := _card("hair", "Bart")
		_chips(cb, beards, int(_app.beard), func(id): _set_app("beard", int(id)))


func _page_clothes() -> void:
	var tops := Appearance.options_for(Appearance.TOPS, _age)
	if Appearance.forces_onesie(_age):
		tops = [Appearance.find_option(Appearance.TOPS, Appearance.id_of_key(Appearance.TOPS, "onesie"))]
	var ct := _card("clothes", "Oberteil")
	_chips(ct, tops, int(_app.top.style), func(id): _set_piece("top", "style", int(id), true))
	_swatches(ct, Appearance.CLOTH_COLORS, _app.top.color, func(col: Color): _set_piece("top", "color", col))
	if Appearance.forces_onesie(_age):
		var cap := _label("Babys tragen automatisch einen Strampler.", "CaptionLabel")
		ct.add_child(cap)
	var top_opt := Appearance.find_option(Appearance.TOPS, _app.top.style)
	var cbm := _card("clothes", "Unterteil")
	if top_opt.get("dress", false):
		cbm.add_child(_label("Das Kleid bildet zugleich das Unterteil.", "CaptionLabel"))
	elif top_opt.get("covers_legs", false):
		cbm.add_child(_label("Der Strampler bedeckt auch die Beine.", "CaptionLabel"))
	else:
		_chips(cbm, Appearance.options_for(Appearance.BOTTOMS, _age), int(_app.bottom.style), func(id): _set_piece("bottom", "style", int(id)))
		_swatches(cbm, Appearance.CLOTH_COLORS, _app.bottom.color, func(col: Color): _set_piece("bottom", "color", col))
	var cs := _card("clothes", "Schuhe")
	var shoes := Appearance.options_for(Appearance.SHOES, _age)
	if Appearance.forces_onesie(_age):
		shoes = shoes.filter(func(o): return o.key in ["booties", "barefoot"])
	_chips(cs, shoes, int(_app.shoes.style), func(id): _set_piece("shoes", "style", int(id)))
	_swatches(cs, Appearance.CLOTH_COLORS, _app.shoes.color, func(col: Color): _set_piece("shoes", "color", col))


func _page_acc() -> void:
	var c := _card("acc", "Accessoires")
	var flow := HFlowContainer.new()
	flow.add_theme_constant_override("h_separation", 6)
	flow.add_theme_constant_override("v_separation", 6)
	c.add_child(flow)
	for o in Appearance.options_for(Appearance.ACCESSORIES, _age):
		var b := _button(str(o.label), "ChipButton")
		b.toggle_mode = true
		b.button_pressed = _app.accessories.has(o.id)
		var oid: String = o.id
		var slot: String = o.slot
		b.toggled.connect(func(on: bool): _toggle_acc(oid, slot, on))
		flow.add_child(b)
	var cap := _label("Teile mit gleichem Platz (z. B. Kopfbedeckungen oder Brillen) ersetzen sich gegenseitig.", "CaptionLabel")
	cap.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	c.add_child(cap)
	var cc := _card("acc", "Farbe für Mütze, Hut, Schal und Rucksack")
	_swatches(cc, Appearance.CLOTH_COLORS, _app.accessory_color, func(col: Color): _set_app("accessory_color", col))


# ================================================================== Aenderungen

func _set_app(key: String, value, rebuild_ui := false) -> void:
	if _updating:
		return
	_app[key] = value
	_changed(rebuild_ui)


func _set_sub(group: String, key: String, value) -> void:
	if _updating:
		return
	var d: Dictionary = _app[group]
	d[key] = value
	_changed(false)


func _set_piece(group: String, key: String, value, rebuild_ui := false) -> void:
	if _updating:
		return
	var d: Dictionary = _app[group]
	var was_dress: bool = Appearance.find_option(Appearance.TOPS, _app.top.style).get("dress", false)
	d[key] = value
	if group == "top" and key == "style":
		var is_dress: bool = Appearance.find_option(Appearance.TOPS, int(value)).get("dress", false)
		if was_dress and not is_dress:
			_app.bottom.style = 0
		rebuild_ui = true
	_changed(rebuild_ui)


func _toggle_acc(id: String, slot: String, on: bool) -> void:
	if _updating:
		return
	var acc: Array = _app.accessories
	if on:
		for a in acc.duplicate():
			if Appearance.find_option(Appearance.ACCESSORIES, a).get("slot", "") == slot:
				acc.erase(a)
		acc.append(id)
	else:
		acc.erase(id)
	_changed(true)


func _changed(rebuild_ui: bool) -> void:
	_app = Appearance.validate(_app, _age)
	if rebuild_ui:
		call_deferred("_refresh_options")
	else:
		_update_preview()
	_emit()


func _emit() -> void:
	changed.emit(get_character())


func _set_age(v: float, from_stage: bool) -> void:
	if _updating:
		return
	_age = clampf(snappedf(v, 1.0 / 12.0), Appearance.MIN_AGE, Appearance.MAX_AGE)
	var stage := Appearance.life_stage(_age)
	if stage != _last_stage:
		# Verfuegbarkeit von Optionen haengt von der Lebensphase ab
		_last_stage = stage
		call_deferred("_refresh_options")
	else:
		_app = Appearance.validate(_app, _age)
		_sync_age_controls()
		_update_preview()
	_emit()


func _from_spin() -> void:
	if _updating:
		return
	var v := float(_years.value) + (float(_months.value) / 12.0 if _months_box.visible else 0.0)
	_set_age(maxf(v, Appearance.MIN_AGE), false)


func _sync_age_controls() -> void:
	var was := _updating
	_updating = true
	if _age_slider:
		_age_slider.value = _age
		_years.value = floor(_age)
		_months.value = int(round((_age - floor(_age)) * 12.0)) % 12
		_months_box.visible = _age < 4.0
		var stage := Appearance.life_stage(_age)
		for k in _stage_buttons:
			(_stage_buttons[k] as Button).button_pressed = k == stage
		_stage_label.text = "%s, %s" % [Appearance.life_stage_label(stage), Appearance.age_text(_age)]
	_updating = was


func _update_preview() -> void:
	if preview:
		preview.set_character(_app, _age, _body)


func _on_randomize() -> void:
	_app = Appearance.random(_rng, _body, _age)
	_refresh_options()
	_emit()
