class_name UIPhone
extends Control
## Smartphone (Taste "phone"): App-Raster mit Status, Inventar, Jobs,
## Wohnen, Bank, Bildung, Karte, Nachrichten, Kontakte, Polizei, Gang,
## Politik und Regeln. Alle Apps rufen die Systeme defensiv auf und zeigen
## einen leeren Zustand, wenn ein System fehlt.

signal closed()
signal open_map()

## [id, Titel, Icon, Farbe]
const APPS := [
	["status", "Status", "health", Color("#ff5d6c")], ["inventory", "Inventar", "bag", Color("#ff9f43")],
	["jobs", "Jobs", "briefcase", Color("#2fa8ff")], ["housing", "Wohnen", "home", Color("#3ddc97")],
	["bank", "Bank", "bank", Color("#6fe3a3")], ["education", "Bildung", "graduation", Color("#b388ff")],
	["map", "Karte", "map", Color("#4cc9f0")], ["news", "Nachrichten", "news", Color("#ffd166")],
	["contacts", "Kontakte", "contacts", Color("#f78fb3")], ["police", "Polizei", "police", Color("#5c8dff")],
	["gang", "Gang", "mask", Color("#a0a8b8")], ["politics", "Politik", "capitol", Color("#ff7a45")],
	["rules", "Regeln", "book", Color("#c9d3e3")],
]
const KIND_LABELS := {"room": "Zimmer", "apartment": "Wohnung", "house": "Haus", "villa": "Villa", "estate": "Anwesen", "shelter_bed": "Schlafplatz"}

var world: WorldState
## Callable(result: Dictionary, ok_text: String) aus der Spieloberflaeche.
var result_cb: Callable
var app := ""
var _frame: PanelContainer
var _screen: VBoxContainer
var _clock: Label
var _housing_mode := "rent"


func _ready() -> void:
	set_anchors_and_offsets_preset(PRESET_FULL_RECT)
	mouse_filter = MOUSE_FILTER_STOP
	world = Game.world
	var dim := UIKit.dim(0.35)
	dim.gui_input.connect(func(ev: InputEvent) -> void:
		if ev is InputEventMouseButton and ev.pressed:
			closed.emit())
	add_child(dim)
	_frame = PanelContainer.new()
	var sb := UITheme.box(Color("#05080f"), 46, Color(1, 1, 1, 0.16), 2, 14, 14)
	sb.shadow_color = Color(0, 0, 0, 0.6)
	sb.shadow_size = 40
	_frame.add_theme_stylebox_override("panel", sb)
	_frame.anchor_left = 1.0
	_frame.anchor_right = 1.0
	_frame.anchor_top = 0.5
	_frame.anchor_bottom = 0.5
	_frame.offset_left = -560
	_frame.offset_right = -60
	_frame.offset_top = -450
	_frame.offset_bottom = 450
	add_child(_frame)
	var inner := PanelContainer.new()
	var isb := UITheme.box(Color("#0c1322"), 36, Color(0, 0, 0, 0), 0, 18, 14)
	inner.add_theme_stylebox_override("panel", isb)
	_frame.add_child(inner)
	var v := UIKit.vbox(10)
	inner.add_child(v)
	var bar := UIKit.hbox(8)
	_clock = UIKit.label("", "ValueLabel")
	_clock.add_theme_font_size_override("font_size", 16)
	bar.add_child(_clock)
	bar.add_child(UIKit.spacer())
	var close := UIKit.icon_button("close", "Schließen (%s)" % Settings.binding_label("phone"), 34)
	close.pressed.connect(func() -> void: closed.emit())
	bar.add_child(close)
	v.add_child(bar)
	_screen = UIKit.vbox(10)
	_screen.size_flags_vertical = SIZE_EXPAND_FILL
	v.add_child(_screen)
	show_app("")
	_frame.position.x += 80
	_frame.modulate.a = 0.0
	var tw := create_tween().set_parallel(true).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	tw.tween_property(_frame, "position:x", _frame.position.x - 80, 0.28)
	tw.tween_property(_frame, "modulate:a", 1.0, 0.2)


func _process(_d: float) -> void:
	if world:
		_clock.text = "%s  ·  %s" % [world.time_string(), world.date_string()]


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("phone"):
		get_viewport().set_input_as_handled()
		closed.emit()
	elif event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE:
		get_viewport().set_input_as_handled()
		if app != "":
			show_app("")
		else:
			closed.emit()


# ================================================================ Navigation

func show_app(id: String) -> void:
	app = id
	UIKit.clear(_screen)
	if id == "":
		_home()
		return
	if id == "map":
		open_map.emit()
		return
	var head := UIKit.hbox(8)
	var back := UIKit.icon_button("chevron_left", "Zurück", 38)
	back.pressed.connect(show_app.bind(""))
	head.add_child(back)
	var info: Array = []
	for a in APPS:
		if a[0] == id:
			info = a
	head.add_child(UIKit.icon_badge(info[2], info[3], 38, 0.5, true))
	head.add_child(UIKit.label(info[1], "TitleLabel"))
	_screen.add_child(head)
	var body := UIKit.vbox(10)
	_screen.add_child(UIKit.scroll(body))
	if world == null:
		body.add_child(UIKit.empty_state("warning", "Kein Spiel geladen"))
		return
	call("_app_" + id, body)
	UIKit.stagger(body, 0.02)


func _home() -> void:
	var p = world.player if world else null
	var hello := UIKit.label("Hallo, %s" % str(UIKit.pget(p, "name", "")).split(" ")[0], "TitleLabel")
	_screen.add_child(UIKit.margin(hello, 6, 6, 0, 0))
	if world and world.ledger:
		_screen.add_child(UIKit.margin(UIKit.label("Kontostand " + UIKit.money(world.ledger.balance(world.ledger.player_account)), "SubtitleLabel"), 6, 0, 0, 8))
	var grid := GridContainer.new()
	grid.columns = 3
	grid.add_theme_constant_override("h_separation", 10)
	grid.add_theme_constant_override("v_separation", 14)
	for a in APPS:
		var b := UISelectCard.new(UITheme.RADIUS_L, 8)
		b.size_flags_horizontal = SIZE_EXPAND_FILL
		var v := UIKit.vbox(6)
		v.add_child(UIKit.icon_badge(a[2], a[3], 58, 0.5, true))
		var l := UIKit.label(a[1], "CaptionLabel")
		l.add_theme_color_override("font_color", UITheme.TEXT)
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		v.add_child(l)
		b.add_child(v)
		b.pressed.connect(show_app.bind(a[0]))
		grid.add_child(b)
	_screen.add_child(grid)
	UIKit.stagger(grid, 0.015)


# ================================================================ Bausteine

## Karte mit Titel, Untertitel und optionaler Aktion.
func _item(parent: Control, title: String, sub := "", btn := "", cb: Callable = Callable(), enabled := true,
		icon_name := "", color := UITheme.ACCENT) -> PanelContainer:
	var c := UIKit.card()
	var h := UIKit.hbox(10)
	if icon_name != "":
		h.add_child(UIKit.icon_badge(icon_name, color, 36, 0.5))
	var v := UIKit.vbox(2)
	v.size_flags_horizontal = SIZE_EXPAND_FILL
	var t := UIKit.label(title, "", true)
	t.add_theme_font_override("font", UITheme.font(620))
	t.add_theme_font_size_override("font_size", 17)
	v.add_child(t)
	if sub != "":
		v.add_child(UIKit.label(sub, "CaptionLabel", true))
	h.add_child(v)
	if btn != "":
		var b := UIKit.button(btn, "", "AccentButton" if enabled else "")
		b.disabled = not enabled
		b.add_theme_font_size_override("font_size", 15)
		b.size_flags_vertical = SIZE_SHRINK_CENTER
		b.pressed.connect(cb)
		h.add_child(b)
	c.add_child(h)
	parent.add_child(c)
	return c


func _section(parent: Control, text: String) -> void:
	parent.add_child(UIKit.margin(UIKit.eyebrow(text), 2, 8, 0, 0))


func _empty(parent: Control, icon_name: String, title: String, text := "") -> void:
	var e := UIKit.empty_state(icon_name, title, text)
	e.custom_minimum_size.y = 260
	parent.add_child(e)


## Ergebnis eines Spieleraktion (mit Zeitsprung ueber die Spieloberflaeche).
func _res(r, ok_text := "") -> void:
	if not (r is Dictionary):
		return
	if result_cb.is_valid():
		result_cb.call(r, ok_text)
	else:
		EventBus.notify.emit(str(r.get("text", r.get("reason", ok_text))), "success" if r.get("ok", false) else "warning")
	show_app(app)


## Ergebnis eines Systems, das die Zeit selbst vorspult (nur Meldung).
func _say(r, ok_text := "") -> void:
	if not (r is Dictionary):
		return
	var ok: bool = r.get("ok", false)
	var txt := str(r.get("text", ""))
	if txt == "":
		txt = ok_text if ok else str(r.get("reason", "Nicht möglich."))
	EventBus.notify.emit(txt, "success" if ok else "warning")
	show_app(app)


func _requirements(parent: Control, reqs: Array) -> void:
	for r in reqs:
		var h := UIKit.hbox(8)
		var met: bool = r.get("met", false)
		h.add_child(UIKit.icon_rect("check_circle" if met else "close", 18, UITheme.SUCCESS if met else UITheme.DANGER))
		var l := UIKit.label(str(r.get("label", "")), "CaptionLabel", true)
		l.size_flags_horizontal = SIZE_EXPAND_FILL
		h.add_child(l)
		parent.add_child(h)


func _route_to(pos: Vector2, label: String) -> void:
	UIMapData.set_route(world, UIMapData.player_pos(get_tree()), pos, label)
	EventBus.notify.emit("Route zu %s geplant." % label, "info")


# ================================================================ Apps

func _app_status(b: VBoxContainer) -> void:
	var p = world.player
	if p == null:
		_empty(b, "user", "Keine Spielfigur")
		return
	var st: Dictionary = p.status(world) if p.has_method("status") else {}
	var c := UIKit.card()
	var v := UIKit.vbox(4)
	v.add_child(UIKit.label(str(p.name), "TitleLabel"))
	v.add_child(UIKit.label("%s · %s" % [UIKit.years(float(st.get("age", 0))), str(st.get("stage_label", ""))], "SubtitleLabel"))
	v.add_child(UIKit.label("Stimmung: %s" % str(st.get("mood_label", "")), "CaptionLabel"))
	var ill: Dictionary = st.get("illness", {})
	if not ill.is_empty():
		v.add_child(UIKit.pill("Krank: %s" % str(ill.get("name", "")), UITheme.DANGER, "pharmacy"))
	c.add_child(v)
	b.add_child(c)
	if world.goals:
		_section(b, "Heute")
		for line in world.goals.today_lines(world):
			var row := UIKit.hbox(8)
			row.add_child(UIKit.icon_rect("clock", 16, UITheme.TEXT_MUTED))
			row.add_child(UIKit.label(str(line), "CaptionLabel", true))
			b.add_child(row)
		var active: Array = world.goals.active(world)
		if not active.is_empty():
			_section(b, "Ziele")
			for g in active:
				var entry: Dictionary = g
				var sub: String = str(entry.hint)
				if str(entry.place_label) != "":
					sub += " (%s)" % str(entry.place_label)
				_item(b, str(entry.title), sub, "Karte" if bool(entry.has_pos) else "",
					func() -> void: _route_to(entry.pos, str(entry.place_label)), bool(entry.has_pos),
					str(entry.icon))
	_section(b, "Bedürfnisse")
	for k in ["health", "hunger", "thirst", "energy", "hygiene", "social", "fun"]:
		var h := UIKit.hbox(8)
		h.add_child(UIKit.icon_rect(UITheme.NEED_ICONS[k], 18, UITheme.NEED_COLORS[k]))
		var l := UIKit.label(UITheme.NEED_LABELS[k], "CaptionLabel")
		l.custom_minimum_size.x = 96
		h.add_child(l)
		var val := float(p.needs.get(k, 0.0))
		h.add_child(UIKit.bar(val, UITheme.NEED_COLORS[k], 10))
		var n := UIKit.label("%d" % int(val), "ValueLabel")
		n.custom_minimum_size.x = 34
		n.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
		h.add_child(n)
		b.add_child(h)
	_section(b, "Bildung und Beruf")
	b.add_child(UIKit.stat_row("Abschluss", UIKit.EDUCATION_LABELS.get(str(p.education), str(p.education)), "graduation"))
	var job: Dictionary = st.get("job", {})
	b.add_child(UIKit.stat_row("Beruf", str(job.get("title", "Ohne Arbeit")), "briefcase"))
	_section(b, "Fähigkeiten")
	var any := false
	var skills: Dictionary = p.skills if p.skills is Dictionary else {}
	for k in skills:
		if float(skills[k]) < 1.0:
			continue
		any = true
		var h2 := UIKit.hbox(8)
		var l2 := UIKit.label(Skills.label(k), "CaptionLabel")
		l2.custom_minimum_size.x = 140
		h2.add_child(l2)
		h2.add_child(UIKit.bar(float(skills[k]), UITheme.ACCENT, 8))
		b.add_child(h2)
	if not any:
		b.add_child(UIKit.label("Noch keine nennenswerten Fähigkeiten.", "CaptionLabel"))


func _app_inventory(b: VBoxContainer) -> void:
	var inv: Dictionary = world.player.inventory if world.player else {}
	if inv.is_empty():
		_empty(b, "bag", "Inventar leer", "Kaufe Lebensmittel und Gegenstände in Geschäften.")
		return
	for gid in inv.keys():
		var g: Dictionary = GoodsCatalog.get_good(gid)
		if g.is_empty():
			continue
		var id: String = gid
		var eff := PackedStringArray()
		var needs: Dictionary = g.get("needs", {})
		for k in needs:
			if float(needs[k]) != 0.0:
				eff.append("%s %+d" % [UITheme.NEED_LABELS.get(k, k), int(needs[k])])
		_item(b, "%s  ×%d" % [g.name, int(inv[gid])], ", ".join(eff), "Benutzen",
			func() -> void: _res(world.player.use_item(world, id)), bool(g.get("consumable", false)), "box", UITheme.WARM)


func _app_jobs(b: VBoxContainer) -> void:
	if world.jobs == null:
		_empty(b, "briefcase", "Arbeitsmarkt nicht verfügbar")
		return
	var job: Dictionary = world.jobs.job_of("player", 0, world)
	if not job.is_empty():
		_section(b, "Aktuelle Stelle")
		_item(b, str(job.get("title", "")), "%s/Std." % UIKit.money(float(job.get("wage", 0.0))), "Kündigen",
			func() -> void:
				var m := UIModal.confirm(UIKit.modal_host(self), "Stelle kündigen?", "Du verlierst dein Einkommen aus dieser Stelle.", "Kündigen", true)
				m.answered.connect(func(ok: bool) -> void:
					if ok:
						_res(world.player.quit_job(world))), true, "briefcase")
	_section(b, "Offene Stellen")
	var openings: Array = world.jobs.list_openings(world, "player", 0)
	openings.sort_custom(func(a, c): return int(a.get("eligible", false)) > int(c.get("eligible", false)))
	if openings.is_empty():
		_empty(b, "briefcase", "Keine offenen Stellen")
	for i in mini(openings.size(), 40):
		var o: Dictionary = openings[i]
		var sh = o.get("shift", [8.0, 8.0])
		var sub := "%s · %s/Std. · ab %02d Uhr, %d Std." % [str(o.get("employer", o.get("workplace", ""))), UIKit.money(float(o.get("wage", 0.0))), int(sh[0]), int(sh[1])]
		var miss = o.get("missing", [])
		if not o.get("eligible", false) and miss is Array and not miss.is_empty():
			sub += "\nFehlt: " + ", ".join(PackedStringArray(miss.map(func(x): return str(x))))
		var pos := int(o.get("position", -1))
		_item(b, str(o.get("title", "Stelle")), sub, "Bewerben",
			func() -> void: _res(world.player.apply_job(world, pos), "Du hast die Stelle bekommen!"), bool(o.get("eligible", true)))


func _app_housing(b: VBoxContainer) -> void:
	if world.housing == null:
		_empty(b, "home", "Immobilienmarkt nicht verfügbar")
		return
	var home := int(world.player.home) if world.player else -1
	if home >= 0:
		var pr: Dictionary = world.housing.get_property(home)
		var bld: Dictionary = world.city.buildings[int(pr.get("building", 0))]
		_section(b, "Dein Zuhause")
		_item(b, str(bld.address), "%s · %d m²" % [KIND_LABELS.get(str(pr.get("kind", "")), ""), int(pr.get("size_m2", 0))],
			"Route", func() -> void: _route_to(bld.sidewalk_point, "Zuhause"), true, "home", UITheme.SUCCESS)
	var tabs := UIKit.hbox(8)
	for m in [["rent", "Mieten"], ["buy", "Kaufen"]]:
		var c := UIKit.chip(m[1])
		c.set_pressed_no_signal(_housing_mode == m[0])
		c.pressed.connect(func() -> void:
			_housing_mode = m[0]
			show_app("housing"))
		tabs.add_child(c)
	b.add_child(tabs)
	var props: Array = world.housing.list_available({"mode": _housing_mode, "limit": 30}, world)
	if props.is_empty():
		_empty(b, "key", "Keine Angebote")
	for pr in props:
		var pid := int(pr.id)
		var bld2: Dictionary = world.city.buildings[int(pr.building)]
		var dist := int(bld2.get("district", -1))
		var dname: String = world.city.districts[dist].name if dist >= 0 else ""
		var price := "%s/Woche" % UIKit.money(float(pr.get("rent_per_week", 0.0)), 0) if _housing_mode == "rent" else UIKit.money(float(pr.get("price", 0.0)), 0)
		var sub := "%s · %s · %d Zi. · %d m² · %s" % [KIND_LABELS.get(str(pr.kind), str(pr.kind)), dname, int(pr.get("rooms", 1)), int(pr.get("size_m2", 0)), price]
		var card := _item(b, str(bld2.address), sub, "Mieten" if _housing_mode == "rent" else "Kaufen", func() -> void:
			if _housing_mode == "rent":
				_res(world.player.rent_home(world, pid), "Mietvertrag unterschrieben!")
			else:
				_ask_buy(pid), true)
		var route := UIKit.icon_button("route", "Auf der Karte zeigen", 34)
		var sp: Vector2 = bld2.sidewalk_point
		route.pressed.connect(func() -> void: _route_to(sp, str(bld2.address)))
		(card.get_child(0) as HBoxContainer).add_child(route)


func _ask_buy(pid: int) -> void:
	var m := UIModal.custom(UIKit.modal_host(self), "Immobilie kaufen", "key", 520)
	m.body.add_child(UIKit.label("Möchtest du bar bezahlen oder eine Hypothek bei der Bank aufnehmen?", "SubtitleLabel", true))
	var cancel := UIKit.button("Abbrechen", "", "GhostButton")
	cancel.pressed.connect(m.dismiss)
	m.buttons.add_child(cancel)
	var mort := UIKit.button("Mit Hypothek", "bank")
	mort.pressed.connect(func() -> void:
		m.dismiss()
		_res(world.player.buy_home(world, pid, true), "Kaufvertrag unterschrieben!"))
	m.buttons.add_child(mort)
	var cash := UIKit.button("Bar bezahlen", "money", "AccentButton")
	cash.pressed.connect(func() -> void:
		m.dismiss()
		_res(world.player.buy_home(world, pid, false), "Kaufvertrag unterschrieben!"))
	m.buttons.add_child(cash)


func _app_bank(b: VBoxContainer) -> void:
	var l := world.ledger
	var c := UIKit.card()
	var v := UIKit.vbox(2)
	v.add_child(UIKit.label("Girokonto", "CaptionLabel"))
	var bal := l.balance(l.player_account)
	var bl := UIKit.label(UIKit.money(bal), "BigValueLabel")
	if bal < 0:
		bl.add_theme_color_override("font_color", UITheme.DANGER)
	v.add_child(bl)
	c.add_child(v)
	b.add_child(c)
	var bank = world.economy.get("bank") if world.economy else null
	if bank != null:
		_section(b, "Kredite")
		var loans: Array = bank.loans_of("player", 0)
		for ln in loans:
			_item(b, "Kredit (%s)" % str(ln.get("purpose", "")), "Restschuld %s · Rate %s/Woche" % [UIKit.money(float(ln.get("remaining", 0.0))), UIKit.money(float(ln.get("payment_week", ln.get("payment", 0.0))))], "", Callable(), true, "bank", UITheme.WARNING)
		var lim: Dictionary = bank.credit_limit(world, "player", 0, "consumer")
		var maxl := float(lim.get("max_loan", 0.0))
		if bool(lim.get("blocked", false)):
			b.add_child(UIKit.label("Die Bank gewährt dir derzeit keinen Kredit.", "CaptionLabel", true))
		elif maxl < 100.0:
			b.add_child(UIKit.label("Für einen Kredit brauchst du ein regelmäßiges Einkommen.", "CaptionLabel", true))
		else:
			for amt in [maxl * 0.25, maxl * 0.5, maxl]:
				var a := floorf(amt / 100.0) * 100.0
				if a < 100.0:
					continue
				_item(b, "Kredit über %s" % UIKit.money(a, 0), "Zins %s %% pro Woche · Laufzeit bis %d Wochen" % [UIKit.decimal(float(lim.get("rate_week", 0.0)) * 100.0, 2), int(lim.get("weeks", 0))],
					"Beantragen", func() -> void: _say(bank.request_loan(world, "player", 0, l.player_account, a, "consumer"), "Kredit ausgezahlt."))
	_section(b, "Kontoauszug")
	if l.player_log.is_empty():
		b.add_child(UIKit.label("Noch keine Buchungen.", "CaptionLabel"))
	for e in l.player_log.slice(0, 40):
		var amt2 := float(e.get("amount", 0.0))
		b.add_child(UIKit.stat_row("%s · %s" % [str(e.get("reason", "")), str(e.get("other", ""))], ("+" if amt2 > 0 else "") + UIKit.money(amt2), "", UITheme.MONEY if amt2 > 0 else UITheme.DANGER))


func _app_education(b: VBoxContainer) -> void:
	var ed = world.education
	if ed == null:
		_empty(b, "graduation", "Bildung nicht verfügbar")
		return
	var en: Dictionary = ed.enrollment_of("player", 0)
	if not en.is_empty():
		_section(b, "Eingeschrieben")
		_item(b, str(en.get("name", en.get("program", ""))), "Fortschritt %d %%" % int(float(en.get("progress", 0.0)) * 100.0 if float(en.get("progress", 0.0)) <= 1.0 else float(en.get("progress", 0.0))),
			"Lernen (2 Std.)", func() -> void: _res(world.player.study(world, 2.0)), true, "graduation", UITheme.SUCCESS)
	_section(b, "Studiengänge und Kurse")
	for pr in ed.list_programs(world, "player", 0):
		var pid := str(pr.id)
		var fee := "%s/Woche" % UIKit.money(float(pr.get("fee_week", 0.0)), 0) if pr.get("type", "") == "program" else UIKit.money(float(pr.get("fee", 0.0)), 0)
		var sub := "%s · %d Std." % [fee, int(pr.get("hours", 0))]
		if not pr.get("eligible", true):
			sub += "\n" + str(pr.get("reason", ""))
		var fields: Array = pr.get("fields", [])
		var field := str(fields[0].id) if not fields.is_empty() else ""
		_item(b, str(pr.name), sub, "Einschreiben", func() -> void: _res(world.player.enroll(world, pid, field), "Eingeschrieben."), bool(pr.get("eligible", true)))


func _app_news(b: VBoxContainer) -> void:
	if world.news.is_empty():
		_empty(b, "news", "Keine Nachrichten")
		return
	for n in world.news.slice(0, 50):
		var c := UIKit.card()
		var v := UIKit.vbox(4)
		v.add_child(UIKit.label("%s · Tag %d, %02d:00" % [str(n.get("category", "")).capitalize(), int(n.get("day", 0)) + 1, int(n.get("hour", 0))], "EyebrowLabel"))
		v.add_child(UIKit.label(str(n.get("text", "")), "SubtitleLabel", true))
		c.add_child(v)
		b.add_child(c)


func _app_contacts(b: VBoxContainer) -> void:
	var rel: Dictionary = world.player.relationships if world.player else {}
	if rel.is_empty() or world.population == null:
		_empty(b, "contacts", "Noch keine Kontakte", "Lerne Menschen in der Stadt kennen.")
		return
	var ids := rel.keys()
	ids.sort_custom(func(a, c): return float(rel[a]) > float(rel[c]))
	for id in ids.slice(0, 60):
		var person: Dictionary = world.population.get_person(int(id))
		if person.is_empty():
			continue
		var v := float(rel[id])
		var lbl := "Sehr eng" if v > 70 else ("Freundschaft" if v > 35 else ("Bekannt" if v > 0 else "Angespannt"))
		var partner := int(world.player.partner) == int(id)
		_item(b, "%s %s" % [person.get("first", ""), person.get("last", "")], "%s%s · %d" % ["Partner/in · " if partner else "", lbl, int(v)],
			"", Callable(), true, "user", UITheme.SUCCESS if v > 0 else UITheme.DANGER)


func _app_rules(b: VBoxContainer) -> void:
	b.add_child(UIKit.pill(UIKit.mode_label(world.rules.mode), UITheme.ACCENT, UIKit.MODE_ICONS.get(world.rules.mode, "info")))
	b.add_child(UIRulesSummary.build(world.rules, 1, true))


func _app_police(b: VBoxContainer) -> void:
	var cr = world.crime
	if cr == null:
		_empty(b, "police", "Nicht verfügbar")
		return
	var st: Dictionary = cr.player_status(world)
	_section(b, "Dein Status")
	var w := int(st.get("wanted", 0))
	b.add_child(UIKit.stat_row("Fahndungsstufe", "%d von 5" % w if w > 0 else "Nicht gesucht", "star", UITheme.DANGER if w > 0 else UITheme.SUCCESS))
	b.add_child(UIKit.stat_row("Einträge im Strafregister", str((st.get("record", []) as Array).size()), "book"))
	if bool(st.get("in_jail", false)):
		b.add_child(UIKit.stat_row("Haft verbleibend", "%s Std." % UIKit.decimal(float(st.get("jail_remaining_hours", 0.0)), 1), "lock", UITheme.DANGER))
		_item(b, "Kaution zahlen", UIKit.money(float(st.get("bail", 0.0))), "Zahlen", func() -> void: _say(cr.post_bail(world)), true, "money")
		_item(b, "Strafe absitzen", "Die Zeit läuft bis zur Entlassung.", "Absitzen", func() -> void: _say(cr.serve_sentence(world, true)), true, "hourglass")
	elif w > 0:
		_item(b, "Untertauchen (4 Std.)", "Senkt mit der Zeit die Fahndungsstufe.", "Verstecken", func() -> void: _say(cr.hide(world, 4.0)), true, "eye_off")
		_item(b, "Selbst stellen", "Mildere Strafe als bei einer Festnahme.", "Stellen", func() -> void: _say(cr.turn_yourself_in(world)), true, "handcuffs", UITheme.WARNING)
	var pol = cr.get("police")
	if pol == null:
		return
	var ps: Dictionary = pol.status(world)
	_section(b, "Polizeidienst")
	if not bool(ps.get("member", false)):
		var reqs: Array = pol.join_requirements(world)
		var ok := true
		for r in reqs:
			ok = ok and bool(r.met)
		_item(b, "Der Polizei beitreten", "Sicheres Einkommen, Streifen und Ermittlungen.", "Beitreten", func() -> void: _say(pol.join(world), "Willkommen im Polizeidienst."), ok, "police")
		_requirements(b, reqs)
		return
	b.add_child(UIKit.stat_row("Dienstgrad", str(ps.get("title", "")), "shield"))
	b.add_child(UIKit.stat_row("Gelöste Fälle", str(ps.get("cases_solved", 0)), "check"))
	b.add_child(UIKit.stat_row("Lohn", "%s/Std." % UIKit.money(float(ps.get("wage_per_hour", 0.0))), "money"))
	_item(b, "Streife fahren (4 Std.)", "Im Stadtteil mit der höchsten Kriminalität.", "Streife", func() -> void: _say(pol.patrol(world, 4.0)), true, "car")
	var nxt: Dictionary = ps.get("next", {})
	if nxt.has("requirements"):
		_section(b, "Nächste Beförderung")
		_requirements(b, nxt.requirements)
	_section(b, "Offene Fälle")
	var cases: Array = cr.open_cases(world)
	if cases.is_empty():
		b.add_child(UIKit.label("Keine offenen Fälle.", "CaptionLabel"))
	for cs in cases.slice(0, 15):
		var cid := int(cs.id)
		var assigned := str(cs.get("status", "")) == "assigned"
		var sub := "Beweislage %d %% · Verdacht: %s" % [int(cs.get("evidence_percent", 0)), str(cs.get("suspect_name", "Unbekannt"))]
		var card := _item(b, str(cs.get("label", cs.get("type", "Fall"))), sub, "Ermitteln" if assigned else "Übernehmen", func() -> void:
			if assigned:
				_say(pol.investigate(world, cid))
			else:
				_say(pol.take_case(world, cid)), true, "search")
		if assigned and int(cs.get("evidence_percent", 0)) >= 60:
			var arr := UIKit.button("Festnehmen", "handcuffs", "DangerButton")
			arr.pressed.connect(func() -> void: _say(pol.arrest_suspect(world, cid)))
			(card.get_child(0) as HBoxContainer).add_child(arr)
	var quit := UIKit.button("Polizeidienst verlassen", "door", "DangerButton")
	quit.pressed.connect(func() -> void: _say(pol.quit(world)))
	b.add_child(quit)


func _app_gang(b: VBoxContainer) -> void:
	var cr = world.crime
	var gs = cr.get("gangs") if cr != null else null
	if gs == null:
		_empty(b, "mask", "Nicht verfügbar")
		return
	var po: Dictionary = gs.player_overview(world)
	if bool(po.get("member", false)):
		var gid := int(po.gang)
		_section(b, str(po.get("gang_name", "")))
		b.add_child(UIKit.stat_row("Rang", str(po.get("title", "")), "star"))
		b.add_child(UIKit.stat_row("Ruf", str(int(po.get("rep", 0))), "trophy"))
		b.add_child(UIKit.stat_row("Aufträge erledigt", str(po.get("missions_done", 0)), "check"))
		var rr: Dictionary = po.get("requirements", {})
		if str(rr.get("next_title", "")) != "":
			_section(b, "Aufstieg zu: %s" % str(rr.next_title))
			_requirements(b, rr.get("requirements", []))
			if bool(rr.get("can_promote", false)):
				_item(b, "Beförderung verlangen", "", "Verlangen", func() -> void: _say(gs.request_promotion(world)), true, "trophy")
			for o in rr.get("boss_options", []):
				var oid := str(o.id)
				_item(b, str(o.label), str(o.get("description", "")), "Wählen", func() -> void: _say(gs.boss_decision(world, oid)), bool(o.get("available", false)), "flag", UITheme.WARM)
		_section(b, "Aufträge")
		var ms: Array = gs.available_missions(world)
		if ms.is_empty():
			b.add_child(UIKit.label("Zurzeit keine Aufträge.", "CaptionLabel"))
		for m in ms:
			var mid := int(m.id)
			var active := str(m.get("status", "")) == "active"
			var sub := "Risiko %s · %s" % [str(m.get("risk_label", "")), ("Nächster Schritt: " + str(m.get("next_step", ""))) if active else ("Ab Rang " + str(m.get("min_rank_title", "")))]
			var btn := "Annehmen"
			var cb := func() -> void: _say(gs.accept_mission(world, mid), "Auftrag angenommen.")
			if active and bool(m.get("ready", false)):
				btn = "Abschließen"
				cb = func() -> void: _say(gs.complete_mission(world, mid), "Auftrag erledigt.")
			elif active:
				btn = "Route"
				var tp: Vector2 = m.get("target_pos", Vector2.ZERO)
				cb = func() -> void: _route_to(tp, str(m.get("next_step", "Auftrag")))
			_item(b, str(m.get("title", m.get("label", "Auftrag"))), sub, btn, cb, active or bool(m.get("rank_ok", true)), "mask", UITheme.WARM)
		var leave := UIKit.button("Bande verlassen", "door", "DangerButton")
		leave.pressed.connect(func() -> void: _say(gs.leave_gang(world)))
		b.add_child(leave)
		return
	b.add_child(UIKit.label("Kriminelle Banden kontrollieren Teile der Stadt. Ein Beitritt bringt Geld und Macht – aber auch Polizei und Rivalen.", "CaptionLabel", true))
	for g in gs.overview(world):
		var gid2 := int(g.id)
		var sub := "%s · %d Mitglieder · Ruf %d\nGebiet: %s" % [str(g.get("kind", "")), int(g.get("members", 0)), int(g.get("player_rep", 0)), ", ".join(PackedStringArray(g.get("territory_names", [])))]
		var card := _item(b, str(g.name), sub, "Kontakt", func() -> void: _say(gs.contact(world, gid2)), true, "mask", UITheme.WARM)
		var hp = g.get("hideout_pos", null)
		if hp is Vector2:
			var r := UIKit.icon_button("route", "Route zum Versteck", 34)
			var hpv: Vector2 = hp
			r.pressed.connect(func() -> void: _route_to(hpv, str(g.get("hideout_name", g.name))))
			(card.get_child(0) as HBoxContainer).add_child(r)


func _app_politics(b: VBoxContainer) -> void:
	var pol = world.get("politics")
	if pol == null:
		_empty(b, "capitol", "Nicht verfügbar")
		return
	_section(b, "Parteien")
	for p in pol.party_list():
		var col: Color = p.color if p.get("color") is Color else UITheme.ACCENT
		_item(b, "%s (%s)%s" % [str(p.name), str(p.short), "  · deine Partei" if p.get("is_player_party", false) else ""],
			"Beliebtheit %d %% · Stadtrat %d · Parlament %d" % [roundi(float(p.popularity) * (100.0 if float(p.popularity) <= 1.0 else 1.0)), int(p.council_seats), int(p.parliament_seats)],
			"", Callable(), true, "flag", col)
	var el: Array = pol.upcoming_elections(world)
	if not el.is_empty():
		_section(b, "Nächste Wahlen")
		for e in el:
			b.add_child(UIKit.stat_row(str(e.title), "in %d Tagen%s" % [int(e.in_days), " · Kandidatur" if e.get("player_candidate", false) else ""], "vote"))
	_section(b, "Handeln und Entscheiden")
	for a in pol.available_actions(world):
		var aid := str(a.id)
		if str(a.get("kind", "")) == "lever":
			_lever(b, pol, a)
			continue
		var needs_value := aid in ["campaign", "donate"]
		_item(b, str(a.label), str(a.get("effect_preview", "")), "Ausführen", func() -> void:
			if needs_value:
				_ask_amount(pol, aid, str(a.label))
			else:
				_say(pol.apply_decision(world, aid, 0.0)), bool(a.get("available", true)), "vote", UITheme.WARM)


func _lever(b: VBoxContainer, pol, a: Dictionary) -> void:
	var c := UIKit.card()
	var v := UIKit.vbox(6)
	var t := UIKit.label(str(a.label), "", true)
	t.add_theme_font_override("font", UITheme.font(620))
	v.add_child(t)
	v.add_child(UIKit.label(str(a.get("effect_preview", a.get("description", ""))), "CaptionLabel", true))
	var h := UIKit.hbox(8)
	var sl := HSlider.new()
	sl.min_value = float(a.get("min", 0.0))
	sl.max_value = float(a.get("max", 1.0))
	sl.step = maxf(0.0001, float(a.get("step", 0.01)))
	sl.value = float(a.get("current", 0.0))
	sl.size_flags_horizontal = SIZE_EXPAND_FILL
	h.add_child(sl)
	var val := UIKit.label(_fmt_lever(sl.value, str(a.get("unit", ""))), "ValueLabel")
	val.custom_minimum_size.x = 80
	h.add_child(val)
	sl.value_changed.connect(func(x: float) -> void: val.text = _fmt_lever(x, str(a.get("unit", ""))))
	var ok := UIKit.button("Beschließen", "", "AccentButton")
	var aid := str(a.id)
	ok.pressed.connect(func() -> void: _say(pol.apply_decision(world, aid, sl.value)))
	h.add_child(ok)
	v.add_child(h)
	c.add_child(v)
	b.add_child(c)


func _fmt_lever(x: float, unit: String) -> String:
	match unit:
		"percent", "%": return "%s %%" % UIKit.decimal(x * 100.0 if x <= 1.0 else x, 1)
		"money", "€": return UIKit.money(x, 0)
	return UIKit.decimal(x, 2)


func _ask_amount(pol, aid: String, label: String) -> void:
	var m := UIModal.custom(UIKit.modal_host(self), label, "money", 480)
	var sp := SpinBox.new()
	sp.min_value = 0
	sp.max_value = 1000000
	sp.step = 50
	sp.value = 200
	sp.suffix = "€"
	m.body.add_child(UIKit.label("Betrag festlegen:", "SubtitleLabel"))
	m.body.add_child(sp)
	var cancel := UIKit.button("Abbrechen", "", "GhostButton")
	cancel.pressed.connect(m.dismiss)
	m.buttons.add_child(cancel)
	var ok := UIKit.button("Ausführen", "", "AccentButton")
	ok.pressed.connect(func() -> void:
		m.dismiss()
		_say(pol.apply_decision(world, aid, sp.value)))
	m.buttons.add_child(ok)
