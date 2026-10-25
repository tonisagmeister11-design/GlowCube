extends Control
## Spieloberflaeche: HUD (Geld, Zeit, Alter, Beduerfnisse, Hinweise),
## Gebaeude-Fenster (Einkaufen, Zuhause, Arbeit, Jobcenter, Makler,
## Notunterkunft), Inventar und Pausemenue. Erzeugt ausserdem die
## Eingangs-Interaktionen der Gebaeude in der Naehe des Spielers.

const NEEDS := [["hunger", "Hunger", "hunger"], ["thirst", "Durst", "thirst"], ["energy", "Energie", "energy"],
	["hygiene", "Hygiene", "hygiene"], ["social", "Soziales", "social"], ["fun", "Spaß", "fun"],
	["health", "Gesundheit", "health"]]
const DOOR_RADIUS := 45.0

var world: WorldState
var _money_lbl: Label
var _name_lbl: Label
var _age_lbl: Label
var _time_lbl: Label
var _date_lbl: Label
var _speed_lbl: Label
var _job_lbl: Label
var _bars := {}
var _prompt: PanelContainer
var _prompt_lbl: Label
var _toasts: VBoxContainer
var _panel: PanelContainer
var _panel_kind := ""
var _doors := {}
var _door_root: Node3D
var _door_timer := 0.0


func _ready() -> void:
	set_anchors_preset(PRESET_FULL_RECT)
	mouse_filter = MOUSE_FILTER_IGNORE
	world = Game.world
	_build_hud()
	EventBus.notify.connect(_toast)
	EventBus.interaction_available.connect(func(t): _prompt_lbl.text = "[%s]  %s" % [Settings.binding_label("interact"), t]; _prompt.visible = true)
	EventBus.interaction_cleared.connect(func(): _prompt.visible = false)
	EventBus.open_panel.connect(_on_open_panel)
	EventBus.player_died.connect(_on_died)
	_door_root = Node3D.new()
	_door_root.name = "BuildingDoors"
	var scene := get_tree().current_scene
	if scene and scene.has_node("Actors"):
		scene.get_node("Actors").add_child.call_deferred(_door_root)
	else:
		scene.add_child.call_deferred(_door_root)


func _icon(name: String, size := 20) -> TextureRect:
	var t := TextureRect.new()
	var path := "res://assets/icons/%s.svg" % name
	if ResourceLoader.exists(path):
		t.texture = load(path)
	t.custom_minimum_size = Vector2(size, size)
	t.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	t.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	return t


func _label(text: String, variation := "") -> Label:
	var l := Label.new()
	l.text = text
	if variation != "":
		l.theme_type_variation = variation
	return l


func _glass() -> PanelContainer:
	var p := PanelContainer.new()
	p.theme_type_variation = "GlassPanel"
	p.mouse_filter = MOUSE_FILTER_IGNORE
	return p


# ============================================================ HUD

func _build_hud() -> void:
	var tl := _glass()
	tl.position = Vector2(24, 24)
	add_child(tl)
	var v := VBoxContainer.new()
	tl.add_child(v)
	_name_lbl = _label("", "TitleLabel")
	v.add_child(_name_lbl)
	_age_lbl = _label("", "CaptionLabel")
	v.add_child(_age_lbl)
	var mrow := HBoxContainer.new()
	mrow.add_child(_icon("wallet"))
	_money_lbl = _label("", "ValueLabel")
	mrow.add_child(_money_lbl)
	v.add_child(mrow)
	_job_lbl = _label("", "CaptionLabel")
	v.add_child(_job_lbl)

	var tr := _glass()
	tr.set_anchors_preset(PRESET_TOP_RIGHT)
	tr.position = Vector2(-260, 24)
	tr.custom_minimum_size = Vector2(236, 0)
	add_child(tr)
	var tv := VBoxContainer.new()
	tr.add_child(tv)
	var trow := HBoxContainer.new()
	trow.add_child(_icon("clock", 24))
	_time_lbl = _label("", "HeaderLabel")
	trow.add_child(_time_lbl)
	tv.add_child(trow)
	_date_lbl = _label("", "SubtitleLabel")
	tv.add_child(_date_lbl)
	_speed_lbl = _label("", "CaptionLabel")
	tv.add_child(_speed_lbl)

	var bl := _glass()
	bl.set_anchors_preset(PRESET_BOTTOM_LEFT)
	bl.position = Vector2(24, -300)
	add_child(bl)
	var bv := VBoxContainer.new()
	bv.add_theme_constant_override("separation", 4)
	bl.add_child(bv)
	for n in NEEDS:
		var row := HBoxContainer.new()
		row.add_child(_icon(n[2], 18))
		var l := _label(n[1], "CaptionLabel")
		l.custom_minimum_size = Vector2(86, 0)
		row.add_child(l)
		var bar := ProgressBar.new()
		bar.custom_minimum_size = Vector2(150, 12)
		bar.show_percentage = false
		bar.size_flags_vertical = SIZE_SHRINK_CENTER
		row.add_child(bar)
		bv.add_child(row)
		_bars[n[0]] = bar
	var hint := _label("[%s] Inventar   [%s] Pause   [%s/%s] Zeit" % [Settings.binding_label("inventory"),
		Settings.binding_label("pause"), Settings.binding_label("time_slower"), Settings.binding_label("time_faster")], "CaptionLabel")
	bv.add_child(hint)

	_prompt = _glass()
	_prompt.set_anchors_preset(PRESET_CENTER_BOTTOM)
	_prompt.position = Vector2(-220, -140)
	_prompt.custom_minimum_size = Vector2(440, 0)
	_prompt.visible = false
	add_child(_prompt)
	_prompt_lbl = _label("", "TitleLabel")
	_prompt_lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_prompt.add_child(_prompt_lbl)

	_toasts = VBoxContainer.new()
	_toasts.set_anchors_preset(PRESET_TOP_RIGHT)
	_toasts.position = Vector2(-440, 170)
	_toasts.custom_minimum_size = Vector2(416, 0)
	_toasts.mouse_filter = MOUSE_FILTER_IGNORE
	add_child(_toasts)


func _process(delta: float) -> void:
	if world == null:
		world = Game.world
		return
	var p = world.player
	if p:
		_name_lbl.text = str(p.name)
		var age: float = p.age(world)
		_age_lbl.text = "%d Jahre · %s" % [int(age), LifeStages.label(p.life_stage())]
		_money_lbl.text = Economy.fmt_money(world.ledger.balance(world.ledger.player_account))
		var job: Dictionary = world.jobs.job_of("player", 0, world) if world.jobs else {}
		var act: String = str(p.activity.get("name", "")) if p.activity is Dictionary else ""
		_job_lbl.text = (str(job.get("title", "Ohne Arbeit")) if not job.is_empty() else "Ohne Arbeit") + \
			("  ·  %s" % _act_label(act) if act != "" else "")
		for k in _bars:
			_bars[k].value = float(p.needs.get(k, 100.0))
			_bars[k].modulate = Color(1, 0.45, 0.4) if _bars[k].value < 25.0 else Color.WHITE
	_time_lbl.text = world.time_string()
	_date_lbl.text = world.date_string()
	_speed_lbl.text = "Tempo: %s" % Game.SPEED_LABELS[Game.speed_index]
	_door_timer -= delta
	if _door_timer <= 0.0:
		_door_timer = 0.7
		_update_doors()


func _act_label(a: String) -> String:
	return {"sleep": "Schläft", "work": "Arbeitet", "rest": "Ruht sich aus", "study": "Lernt"}.get(a, a)


func _toast(text: String, kind := "info") -> void:
	var p := _glass()
	var l := _label(text, "SubtitleLabel")
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.custom_minimum_size = Vector2(380, 0)
	var col := {"success": Color(0.55, 1, 0.6), "warning": Color(1, 0.85, 0.4), "danger": Color(1, 0.5, 0.45), "money": Color(0.6, 0.9, 1)}
	l.modulate = col.get(kind, Color.WHITE)
	p.add_child(l)
	_toasts.add_child(p)
	if _toasts.get_child_count() > 5:
		_toasts.get_child(0).queue_free()
	var tw := create_tween()
	tw.tween_interval(4.5)
	tw.tween_property(p, "modulate:a", 0.0, 0.6)
	tw.tween_callback(p.queue_free)


# ============================================================ Eingaenge

func _player_pos() -> Vector3:
	var pl := get_tree().get_first_node_in_group("player")
	return pl.global_position if pl is Node3D else Vector3.INF


func _home_building() -> int:
	if world.player == null or world.housing == null or int(world.player.home) < 0:
		return -1
	return int(world.housing.get_property(int(world.player.home)).get("building", -1))


func _work_building() -> int:
	if world.jobs == null:
		return -1
	var pid: int = world.jobs.position_of("player", 0)
	return int(world.jobs.positions[pid].building) if pid >= 0 else -1


func _door_label(b: Dictionary) -> String:
	var bid: int = b.id
	if bid == _home_building():
		return "Nach Hause gehen"
	if bid == _work_building():
		return "Zur Arbeit: %s" % (b.name if b.name != "" else b.label)
	if b.business_type != "":
		return "Betreten: %s" % b.name
	match b.kind:
		"shelter": return "Notunterkunft betreten"
		"hospital", "clinic": return "Betreten: %s" % b.name
	if b.kind in CityCatalog.CIVIC_KINDS:
		return "%s" % b.name
	return ""


func _update_doors() -> void:
	var pp := _player_pos()
	if pp == Vector3.INF or not is_instance_valid(_door_root) or not _door_root.is_inside_tree():
		return
	var p2 := Vector2(pp.x, pp.z)
	var keep := {}
	var c := world.city
	var k := c.chunk_key(p2)
	for dx in [-1, 0, 1]:
		for dz in [-1, 0, 1]:
			var ch = c.chunks.get(k + Vector2i(dx, dz))
			if ch == null:
				continue
			for bid in ch.buildings:
				var b: Dictionary = c.buildings[bid]
				if b.door.distance_to(p2) > DOOR_RADIUS:
					continue
				var lbl := _door_label(b)
				if lbl == "":
					continue
				keep[bid] = true
				if _doors.has(bid):
					_doors[bid].label = lbl
					continue
				var d := DoorInteractable.new()
				d.setup(b, lbl)
				_door_root.add_child(d)
				_doors[bid] = d
	for bid in _doors.keys():
		if not keep.has(bid):
			_doors[bid].queue_free()
			_doors.erase(bid)


# ============================================================ Fenster

func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("pause"):
		if _panel:
			_close_panel()
		else:
			_open_pause()
		get_viewport().set_input_as_handled()
	elif event.is_action_pressed("inventory") and _panel == null:
		_open_inventory()
		get_viewport().set_input_as_handled()
	elif event.is_action_pressed("time_faster"):
		Game.faster()
	elif event.is_action_pressed("time_slower"):
		Game.slower()
	elif event.is_action_pressed("quick_save"):
		Game.save_game(SaveSystem.QUICKSAVE_SLOT)


func _on_open_panel(kind: String, ctx := {}) -> void:
	if kind == "building":
		_open_building(int(ctx.get("building", -1)))


func _new_panel(title: String, subtitle := "") -> VBoxContainer:
	if _panel:
		_panel.queue_free()
	else:
		Game.push_ui_pause()
	_panel = PanelContainer.new()
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.07, 0.09, 0.13, 0.97)
	sb.border_color = Color(0.18, 0.66, 1.0, 0.6)
	sb.set_border_width_all(1)
	sb.set_corner_radius_all(14)
	sb.set_content_margin_all(22)
	sb.shadow_color = Color(0, 0, 0, 0.5)
	sb.shadow_size = 18
	_panel.add_theme_stylebox_override("panel", sb)
	_panel.custom_minimum_size = Vector2(680, 0)
	_panel.set_anchors_preset(PRESET_CENTER)
	_panel.grow_horizontal = GROW_DIRECTION_BOTH
	_panel.grow_vertical = GROW_DIRECTION_BOTH
	add_child(_panel)
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 10)
	_panel.add_child(v)
	var head := HBoxContainer.new()
	var hv := VBoxContainer.new()
	hv.size_flags_horizontal = SIZE_EXPAND_FILL
	hv.add_child(_label(title, "HeaderLabel"))
	if subtitle != "":
		hv.add_child(_label(subtitle, "SubtitleLabel"))
	head.add_child(hv)
	var close := Button.new()
	close.text = "Schließen"
	close.theme_type_variation = "GhostButton"
	close.pressed.connect(_close_panel)
	head.add_child(close)
	v.add_child(head)
	return v


func _close_panel() -> void:
	if _panel:
		_panel.queue_free()
		_panel = null
		Game.pop_ui_pause()
		EventBus.close_panels.emit()


func _scroll_list(parent: VBoxContainer, height := 380.0) -> VBoxContainer:
	var sc := ScrollContainer.new()
	sc.custom_minimum_size = Vector2(0, height)
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	parent.add_child(sc)
	var list := VBoxContainer.new()
	list.size_flags_horizontal = SIZE_EXPAND_FILL
	sc.add_child(list)
	return list


func _row(list: VBoxContainer, text: String, sub: String, button: String, cb: Callable, enabled := true) -> void:
	var row := HBoxContainer.new()
	var tv := VBoxContainer.new()
	tv.size_flags_horizontal = SIZE_EXPAND_FILL
	var l := _label(text, "TitleLabel")
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	tv.add_child(l)
	if sub != "":
		var s := _label(sub, "CaptionLabel")
		s.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		tv.add_child(s)
	row.add_child(tv)
	var b := Button.new()
	b.text = button
	b.theme_type_variation = "AccentButton"
	b.disabled = not enabled
	b.custom_minimum_size = Vector2(130, 0)
	b.pressed.connect(cb)
	row.add_child(b)
	list.add_child(row)
	list.add_child(HSeparator.new())


func _result(r: Dictionary, ok_text := "") -> void:
	if not r.get("ok", false):
		_toast(str(r.get("reason", "Nicht möglich.")), "warning")
		return
	if ok_text != "":
		_toast(ok_text, "success")
	elif str(r.get("reason", "")) != "":
		_toast(str(r.reason), "info")
	var minutes: float = float(r.get("minutes", 0.0))
	if minutes > 0.0:
		Game.skip_time(minutes)


func _open_building(bid: int) -> void:
	if bid < 0 or world == null:
		return
	var b: Dictionary = world.city.buildings[bid]
	var title: String = b.name if b.name != "" else b.label
	if bid == _home_building():
		_open_home(b)
		return
	if bid == _work_building() and b.business_type == "":
		_open_work(b)
		return
	match b.kind:
		"shelter":
			var v := _new_panel(title, "Kostenloser Schlafplatz für Menschen ohne Wohnung")
			var list := _scroll_list(v, 160)
			_row(list, "Hier übernachten", "8 Stunden schlafen", "Schlafen", func(): _close_panel(); _result(world.player.sleep(world, 8.0)))
			_row(list, "Duschen", "Gemeinschaftsdusche", "Duschen", func(): world.player.needs.hygiene = 100.0; _toast("Frisch geduscht.", "success"))
			_row(list, "Suppenküche", "Eine warme Mahlzeit", "Essen", func(): world.player.needs.hunger = minf(100.0, float(world.player.needs.hunger) + 40.0); _result({"ok": true, "minutes": 30.0}, "Du hast gegessen."))
			return
		"hospital", "clinic":
			var v2 := _new_panel(title, "Medizinische Versorgung")
			var list2 := _scroll_list(v2, 120)
			_row(list2, "Behandlung", "Untersuchung und Behandlung (kostenpflichtig)", "Behandeln", func(): _close_panel(); _result(world.player.see_doctor(world, bid)))
			return
	if b.business_type == "":
		_toast(title, "info")
		return
	var biz: int = world.economy.business_for_building(bid) if world.economy else -1
	if biz < 0:
		_toast("%s ist derzeit geschlossen." % title, "info")
		return
	var bz: Dictionary = world.economy.get_business(biz)
	var hours: String = world.economy.hours_text(bz)
	if b.business_type == "job_center":
		_open_jobcenter(title)
		return
	if b.business_type == "real_estate":
		_open_realestate(title)
		return
	if not world.economy.is_open(world, biz):
		_toast("%s hat geschlossen. Öffnungszeiten: %s" % [title, hours], "warning")
		return
	if b.business_type == "car_dealer":
		_open_car_dealer(biz, title, hours, bid)
		return
	_open_shop(biz, title, hours, bid)


## Autohaus: Fahrzeuge (Waren des Geschaefts) mit Fahrdaten aus VehicleCatalog;
## nach dem Kauf steht das Fahrzeug vor dem Autohaus (TrafficManager).
func _open_car_dealer(biz: int, title: String, hours: String, bid: int) -> void:
	var v := _new_panel(title, "Autohaus · %s · Guthaben %s" % [hours, Economy.fmt_money(world.ledger.balance(world.ledger.player_account))])
	var owned: Array = world.player.vehicles if world.player.get("vehicles") != null else []
	if not owned.is_empty():
		var ol := _scroll_list(v, 110)
		for i in owned.size():
			var e: Dictionary = owned[i]
			var vid := i
			_row(ol, "Dein Fahrzeug: %s" % str(e.get("name", "Fahrzeug")), "Gekauft an Tag %d" % (int(e.get("bought_day", 0)) + 1), "Herbringen", func():
				var tm := get_tree().get_first_node_in_group("traffic_manager")
				if tm and tm.has_method("deliver_owned"):
					tm.deliver_owned(vid, world.city.buildings[bid].sidewalk_point)
					_toast("Dein Fahrzeug steht vor dem Autohaus.", "success"))
	var list := _scroll_list(v, 380)
	for it in world.economy.shop_items(biz):
		if not it.get("vehicle", false):
			continue
		var gid: String = it.good
		var typ := VehicleCatalog.type_for_good(gid)
		var spec: Dictionary = GoodsCatalog.VEHICLE_SPECS.get(gid, {})
		var tinfo := VehicleCatalog.type_info(typ)
		var sub := "%s · %s · bis %d km/h · %d Sitze · Bestand %d" % [Economy.fmt_money(float(it.price)), tinfo.label,
			int(float(spec.get("top_speed", tinfo.vmax)) * 3.6), int(spec.get("seats", 4)), int(it.stock)]
		_row(list, str(it.name), sub, "Kaufen", func():
			var r: Dictionary = world.player.buy(world, biz, gid, 1)
			if r.get("ok", false):
				var tm := get_tree().get_first_node_in_group("traffic_manager")
				if tm and tm.has_method("deliver_owned"):
					tm.deliver_owned(world.player.vehicles.size() - 1, world.city.buildings[bid].sidewalk_point)
				_toast("%s gekauft – es steht vor dem Autohaus bereit." % it.name, "money")
				_open_car_dealer(biz, title, hours, bid)
			else:
				_toast(str(r.get("reason", "Kauf nicht möglich.")), "warning"), int(it.stock) > 0)


func _open_shop(biz: int, title: String, hours: String, bid: int) -> void:
	var cat: String = CityCatalog.BUSINESS_TYPES.get(world.city.buildings[bid].business_type, {}).get("label", "")
	var v := _new_panel(title, "%s · %s · Guthaben %s" % [cat, hours, Economy.fmt_money(world.ledger.balance(world.ledger.player_account))])
	if bid == _work_building():
		var wl := _scroll_list(v, 70)
		_row(wl, "Deine Arbeitsstelle", "Schicht beginnen", "Arbeiten", func(): _close_panel(); _result(world.player.work_shift(world, bid)))
	var list := _scroll_list(v, 420)
	var items: Array = world.economy.shop_items(biz)
	if items.is_empty():
		list.add_child(_label("Das Sortiment ist ausverkauft.", "SubtitleLabel"))
	for it in items:
		var gid: String = it.good
		var stock: int = int(it.stock)
		var sub := "%s · Bestand %d" % [Economy.fmt_money(float(it.price)), stock]
		_row(list, str(it.name), sub, "Kaufen", func():
			var r: Dictionary = world.player.buy(world, biz, gid, 1)
			if r.get("ok", false):
				_toast("%s gekauft (%s)" % [it.name, Economy.fmt_money(float(r.get("total", it.price)))], "money")
				_open_shop(biz, title, hours, bid)
			else:
				_toast(str(r.get("reason", "Kauf nicht möglich.")), "warning"), stock > 0)


func _open_home(b: Dictionary) -> void:
	var v := _new_panel("Zuhause", str(b.address))
	var list := _scroll_list(v, 360)
	_row(list, "Schlafen", "8 Stunden – stellt Energie wieder her", "Schlafen", func(): _close_panel(); _result(world.player.sleep(world, 8.0)))
	_row(list, "Nickerchen", "2 Stunden", "Ruhen", func(): _close_panel(); _result(world.player.sleep(world, 2.0)))
	_row(list, "Duschen", "Hygiene auffüllen", "Duschen", func(): _result(world.player.shower(world), "Frisch geduscht."))
	_row(list, "Essen", "Aus dem Vorrat essen", "Essen", func(): _result(world.player.eat_at_home(world), "Du hast gegessen."))
	_row(list, "Wasser trinken", "Leitungswasser", "Trinken", func(): _result(world.player.drink(world, ""), "Durst gelöscht."))
	_row(list, "Fernsehen", "1 Stunde entspannen", "Entspannen", func(): world.player.needs.fun = minf(100.0, float(world.player.needs.fun) + 25.0); _close_panel(); _result(world.player.rest(world, 1.0)))


func _open_work(b: Dictionary) -> void:
	var v := _new_panel(b.name if b.name != "" else b.label, "Deine Arbeitsstelle")
	var list := _scroll_list(v, 90)
	_row(list, "Schicht arbeiten", "Lohn wird nach der Schicht ausgezahlt", "Arbeiten", func(): _close_panel(); _result(world.player.work_shift(world, int(b.id))))


func _open_jobcenter(title: String) -> void:
	var v := _new_panel(title, "Offene Stellen in Havenbrück")
	var list := _scroll_list(v, 440)
	var openings: Array = world.jobs.list_openings(world, "player", 0) if world.jobs else []
	openings.sort_custom(func(a, c): return int(a.get("eligible", false)) > int(c.get("eligible", false)))
	var n := 0
	for o in openings:
		if n >= 40:
			break
		n += 1
		var miss = o.get("missing", [])
		var sh = o.get("shift", [8.0, 8.0])
		var sub := "%s · %s/Std. · ab %02d:%02d Uhr, %d Std." % [str(o.get("employer", o.get("workplace", ""))), Economy.fmt_money(float(o.get("wage", 0.0))),
			int(sh[0]), int(fmod(float(sh[0]), 1.0) * 60.0), int(sh[1])]
		if not o.get("eligible", false) and miss is Array and not miss.is_empty():
			sub += "\nFehlt: " + ", ".join(PackedStringArray(miss.map(func(x): return str(x))))
		var op: Dictionary = o
		_row(list, str(o.get("title", "Stelle")), sub, "Bewerben", func():
			var r: Dictionary = world.player.apply_job(world, int(op.get("position", -1)))
			_result(r, "Du hast die Stelle bekommen!")
			if r.get("ok", false):
				_close_panel(), bool(o.get("eligible", true)))
	if n == 0:
		list.add_child(_label("Zurzeit keine offenen Stellen.", "SubtitleLabel"))


func _open_realestate(title: String) -> void:
	var v := _new_panel(title, "Wohnungen zur Miete")
	var list := _scroll_list(v, 440)
	var props: Array = world.housing.list_available({"mode": "rent", "limit": 30}, world) if world.housing else []
	for pr in props:
		var pid: int = int(pr.id)
		var dname := ""
		var bld: Dictionary = world.city.buildings[int(pr.building)]
		if int(bld.district) >= 0:
			dname = world.city.districts[int(bld.district)].name
		var kinds := {"room": "Zimmer", "apartment": "Wohnung", "house": "Haus", "villa": "Villa", "estate": "Anwesen", "shelter_bed": "Schlafplatz"}
		var sub := "%s · %s · %d Zimmer · %d m² · %s/Woche" % [kinds.get(str(pr.kind), str(pr.kind)), dname, int(pr.get("rooms", 1)),
			int(pr.get("size_m2", 0)), Economy.fmt_money(float(pr.get("rent_per_week", 0.0)))]
		_row(list, str(bld.address), sub, "Mieten", func():
			var r: Dictionary = world.player.rent_home(world, pid)
			_result(r, "Mietvertrag unterschrieben – dein neues Zuhause!")
			if r.get("ok", false):
				_close_panel())
	if props.is_empty():
		list.add_child(_label("Keine freien Wohnungen.", "SubtitleLabel"))


func _open_inventory() -> void:
	var v := _new_panel("Inventar", "Gegenstände benutzen, essen und trinken")
	var list := _scroll_list(v, 400)
	var inv: Dictionary = world.player.inventory
	if inv.is_empty():
		list.add_child(_label("Dein Inventar ist leer. Kaufe etwas in einem Geschäft.", "SubtitleLabel"))
	for gid in inv.keys():
		var g: Dictionary = GoodsCatalog.get_good(gid)
		if g.is_empty():
			continue
		var id: String = gid
		_row(list, "%s  ×%d" % [g.name, int(inv[gid])], str(g.get("category", "")), "Benutzen", func():
			_result(world.player.use_item(world, id))
			_open_inventory(), bool(g.get("consumable", false)))


func _open_pause() -> void:
	var v := _new_panel("Pause", "%s · %s" % [world.date_string(), world.time_string()])
	for entry in [["Weiterspielen", _close_panel], ["Speichern", func(): Game.save_game("spielstand_%d" % (Time.get_unix_time_from_system() as int))],
			["Schnellspeichern", func(): Game.save_game(SaveSystem.QUICKSAVE_SLOT)],
			["Hauptmenü", func(): Game.save_game(SaveSystem.AUTOSAVE_SLOT, true); Game.goto_main_menu()],
			["Spiel beenden", func(): Game.quit_game()]]:
		var b := Button.new()
		b.text = entry[0]
		b.theme_type_variation = "AccentButton" if entry[0] == "Weiterspielen" else "GhostButton"
		b.custom_minimum_size = Vector2(0, 44)
		b.pressed.connect(entry[1])
		v.add_child(b)


func _on_died(cause: String) -> void:
	var v := _new_panel("Dein Leben ist zu Ende", "Todesursache: %s" % cause)
	var heirs: Array = world.player.heirs(world) if world.player.has_method("heirs") else []
	if not heirs.is_empty() and str(world.rules.get_rule("death")) == "continue_as_heir":
		var b0 := Button.new()
		b0.text = "Als Erbe weiterspielen"
		b0.theme_type_variation = "AccentButton"
		b0.pressed.connect(func(): world.player.continue_as(world, int(heirs[0].get("id", heirs[0]) if heirs[0] is Dictionary else heirs[0])); _close_panel())
		v.add_child(b0)
	var b := Button.new()
	b.text = "Zum Hauptmenü"
	b.theme_type_variation = "GhostButton"
	b.pressed.connect(func(): Game.goto_main_menu())
	v.add_child(b)
