class_name UISettingsPanel
extends VBoxContainer
## Einstellungen (Grafik, Audio, Steuerung, Gameplay, KI). Wird im
## Hauptmenue und im Pausenmenue verwendet. Jede Aenderung wird sofort ueber
## Settings.set_value gespeichert und angewendet (apply_graphics/apply_audio).

const TABS := [["graphics", "Grafik", "display"], ["audio", "Audio", "volume"], ["controls", "Steuerung", "keyboard"],
	["gameplay", "Gameplay", "sliders"], ["ai", "KI", "sparkle"]]
const RESOLUTIONS := [Vector2i(1280, 720), Vector2i(1366, 768), Vector2i(1600, 900), Vector2i(1920, 1080),
	Vector2i(2560, 1440), Vector2i(3440, 1440), Vector2i(3840, 2160)]
const QUALITY_LABELS := ["Leistungsmodus", "Niedrig", "Mittel", "Hoch", "Ultra"]
## Werte je Qualitaetsstufe: [Sichtweite, Schatten, MSAA, FXAA, Renderskalierung]
## Zu jeder Stufe passende Einzelwerte (Sichtweite, Schatten, MSAA, FXAA,
## Renderauflösung). Der Leistungsmanager setzt zusätzlich Details, Passanten,
## Verkehr, Lichter und Oberflächen.
const QUALITY_PRESETS := [[220.0, 0, 0, false, 0.58], [340.0, 1, 0, true, 0.75],
	[600.0, 2, 1, true, 1.0], [850.0, 2, 1, true, 1.0], [1200.0, 3, 2, true, 1.0]]
const FPS_CAPS := [0, 30, 60, 120, 144, 240]
const AUTOSAVE := [0, 5, 10, 15, 30]

const BIND_GROUPS := [
	["Zu Fuß", "walk", ["move_forward", "move_back", "move_left", "move_right", "sprint", "jump", "interact", "camera_toggle"]],
	["Fahrzeug", "car", ["vehicle", "handbrake", "horn"]],
	["Oberfläche", "phone", ["map", "phone", "inventory", "help", "pause"]],
	["Zeit und Speichern", "clock", ["time_faster", "time_slower", "quick_save", "quick_load"]],
	["Maus", "mouse", ["primary", "secondary"]],
	["Sonstiges", "camera", ["screenshot", "debug_overlay", "debug_city"]],
]
## Kontexte fuer die Konfliktpruefung: gleiche Taste nur in gleichem Kontext problematisch.
const CONTEXTS := {
	"move_forward": ["foot", "vehicle"], "move_back": ["foot", "vehicle"], "move_left": ["foot", "vehicle"],
	"move_right": ["foot", "vehicle"], "sprint": ["foot"], "jump": ["foot"], "interact": ["foot"],
	"camera_toggle": ["foot", "vehicle"], "vehicle": ["foot", "vehicle"], "handbrake": ["vehicle"], "horn": ["vehicle"],
}

var tab := "graphics"
var in_game := false
var _tabs: HBoxContainer
var _body: VBoxContainer
var _tab_buttons := {}
var _quality_chips: Array = []
var _refreshers: Array[Callable] = []


func _init(start_tab := "graphics", ingame := false) -> void:
	tab = start_tab
	in_game = ingame
	add_theme_constant_override("separation", 18)
	size_flags_vertical = SIZE_EXPAND_FILL
	size_flags_horizontal = SIZE_EXPAND_FILL
	_tabs = UIKit.hbox(8)
	add_child(_tabs)
	for t in TABS:
		var b := UIKit.chip(t[1], t[2])
		b.custom_minimum_size.y = 44
		b.pressed.connect(show_tab.bind(t[0]))
		_tab_buttons[t[0]] = b
		_tabs.add_child(b)
	_body = UIKit.vbox(10)
	add_child(UIKit.scroll(_body))
	show_tab(tab)


func show_tab(t: String) -> void:
	tab = t
	for k in _tab_buttons:
		(_tab_buttons[k] as Button).set_pressed_no_signal(k == t)
	UIKit.clear(_body)
	_refreshers.clear()
	_quality_chips.clear()
	match t:
		"graphics": _graphics()
		"audio": _audio()
		"controls": _controls()
		"gameplay": _gameplay()
		"ai": _ai()
	UIKit.stagger(_body, 0.02)


# ================================================================ Bausteine

func _row(title: String, desc: String, control: Control, icon_name := "") -> PanelContainer:
	var p := PanelContainer.new()
	var n := UITheme.box(Color(1, 1, 1, 0.035), UITheme.RADIUS_M, Color(0, 0, 0, 0), 0, 20, 12)
	var h_sb := UITheme.box(Color(1, 1, 1, 0.065), UITheme.RADIUS_M, Color(0, 0, 0, 0), 0, 20, 12)
	p.add_theme_stylebox_override("panel", n)
	p.mouse_entered.connect(func() -> void: p.add_theme_stylebox_override("panel", h_sb))
	p.mouse_exited.connect(func() -> void: p.add_theme_stylebox_override("panel", n))
	var h := UIKit.hbox(16)
	if icon_name != "":
		h.add_child(UIKit.icon_rect(icon_name, 22, UITheme.TEXT_MUTED))
	var v := UIKit.vbox(2)
	v.size_flags_horizontal = SIZE_EXPAND_FILL
	var tl := UIKit.label(title)
	tl.add_theme_font_override("font", UITheme.font(600))
	v.add_child(tl)
	if desc != "":
		v.add_child(UIKit.label(desc, "CaptionLabel", true))
	h.add_child(v)
	control.custom_minimum_size.x = maxf(control.custom_minimum_size.x, 440)
	control.size_flags_vertical = SIZE_SHRINK_CENTER
	h.add_child(control)
	p.add_child(h)
	return p


func _heading(text: String, icon_name: String) -> Control:
	var m := UIKit.margin(UIKit.section(text, icon_name), 4, 14, 0, 2)
	return m


func _toggle(section: String, key: String, apply: Callable = Callable()) -> CheckButton:
	var cb := CheckButton.new()
	cb.button_pressed = bool(Settings.get_value(section, key))
	cb.text = "Ein" if cb.button_pressed else "Aus"
	cb.alignment = HORIZONTAL_ALIGNMENT_RIGHT
	cb.toggled.connect(func(on: bool) -> void:
		cb.text = "Ein" if on else "Aus"
		Settings.set_value(section, key, on)
		if apply.is_valid():
			apply.call())
	return cb


func _options(section: String, key: String, labels: Array, values: Array, apply: Callable = Callable()) -> OptionButton:
	var ob := OptionButton.new()
	for i in labels.size():
		ob.add_item(labels[i], i)
	var cur = Settings.get_value(section, key)
	ob.selected = maxi(0, values.find(cur))
	ob.item_selected.connect(func(i: int) -> void:
		Settings.set_value(section, key, values[i])
		if apply.is_valid():
			apply.call())
	return ob


## Regler mit Wertanzeige. fmt: Callable(float) -> String.
func _slider(section: String, key: String, mn: float, mx: float, st: float, fmt: Callable,
		apply: Callable = Callable(), on_release := false) -> HBoxContainer:
	var h := UIKit.hbox(14)
	var sl := HSlider.new()
	sl.min_value = mn
	sl.max_value = mx
	sl.step = st
	sl.value = float(Settings.get_value(section, key))
	sl.size_flags_horizontal = SIZE_EXPAND_FILL
	sl.custom_minimum_size.y = 28
	var val := UIKit.label(fmt.call(sl.value), "ValueLabel")
	val.custom_minimum_size.x = 84
	val.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	sl.value_changed.connect(func(x: float) -> void:
		val.text = fmt.call(x)
		if not on_release:
			Settings.set_value(section, key, x, false)
			if apply.is_valid():
				apply.call())
	sl.drag_ended.connect(func(_c: bool) -> void:
		Settings.set_value(section, key, sl.value)
		if on_release and apply.is_valid():
			apply.call())
	if on_release:
		# Tastatur-/Klickaenderungen ohne Ziehen ebenfalls uebernehmen
		sl.gui_input.connect(func(ev: InputEvent) -> void:
			if (ev is InputEventKey and not ev.pressed) or (ev is InputEventMouseButton and not ev.pressed):
				if absf(float(Settings.get_value(section, key)) - sl.value) > 0.0001:
					Settings.set_value(section, key, sl.value)
					if apply.is_valid():
						apply.call())
	else:
		sl.mouse_exited.connect(func() -> void: Settings.save())
	h.add_child(sl)
	h.add_child(val)
	h.set_meta("slider", sl)
	return h


static func _pct(x: float) -> String:
	return "%d %%" % roundi(x * 100.0)


func _apply_gfx() -> void:
	if DisplayServer.get_name() != "headless":
		Settings.apply_graphics()


func _apply_audio() -> void:
	Settings.apply_audio()


# ================================================================ Grafik

func _graphics() -> void:
	_body.add_child(_heading("Anzeige", "display"))
	var fs := _options("graphics", "fullscreen", ["Fenster", "Vollbild"], [false, true], func() -> void:
		_apply_gfx()
		show_tab("graphics"))
	_body.add_child(_row("Anzeigemodus", "Vollbild verwendet die Auflösung deines Bildschirms.", fs))
	var screen := DisplayServer.screen_get_size() if DisplayServer.get_name() != "headless" else Vector2i(3840, 2160)
	var res_labels: Array = []
	var res_values: Array = []
	for r in RESOLUTIONS:
		if r.x <= screen.x and r.y <= screen.y:
			res_labels.append("%d × %d" % [r.x, r.y])
			res_values.append(r)
	var cur_res: Vector2i = Settings.get_value("graphics", "resolution")
	if not cur_res in res_values:
		res_values.append(cur_res)
		res_labels.append("%d × %d" % [cur_res.x, cur_res.y])
	var res := _options("graphics", "resolution", res_labels, res_values, _apply_gfx)
	res.disabled = bool(Settings.get_value("graphics", "fullscreen"))
	_body.add_child(_row("Auflösung", "Fenstergröße im Fenstermodus." if not res.disabled else "Im Vollbild wird die Bildschirmauflösung verwendet.", res))
	_body.add_child(_row("V-Sync", "Synchronisiert die Bildrate mit dem Monitor und verhindert Bildreißen.", _toggle("graphics", "vsync", _apply_gfx)))
	_body.add_child(_row("Bildratenbegrenzung", "Obergrenze für Bilder pro Sekunde.",
		_options("graphics", "max_fps", ["Unbegrenzt", "30", "60", "120", "144", "240"], FPS_CAPS, _apply_gfx)))
	_body.add_child(_row("Bildrate anzeigen", "Zeigt die aktuelle Bildrate im Spiel an.", _toggle("graphics", "show_fps")))
	_body.add_child(_heading("Qualität", "sparkle"))
	var qh := UIKit.hbox(8)
	var q := int(Settings.get_value("graphics", "quality"))
	for i in QUALITY_LABELS.size():
		var c := UIKit.chip(QUALITY_LABELS[i])
		c.set_pressed_no_signal(i == q and _matches_preset(i))
		c.pressed.connect(_set_quality.bind(i))
		qh.add_child(c)
		_quality_chips.append(c)
	_body.add_child(_row("Qualitätsstufe", "Setzt Sichtweite, Details, Schatten, Passanten, Verkehr und Renderauflösung auf einmal.", qh))
	var perf := get_node_or_null("/root/Perf")
	if perf != null:
		var lvl: int = int(perf.level)
		var info := UIKit.label(perf.DESCRIPTIONS[clampi(lvl, 0, 4)], "CaptionLabel")
		info.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		info.custom_minimum_size = Vector2(520, 0)
		_body.add_child(info)
		_body.add_child(_row("Automatisch anpassen",
			"Senkt Sichtweite und Belebung selbstständig, wenn die Bildrate einbricht, und hebt sie wieder an.",
			_toggle("graphics", "auto_adjust")))
		var hw := UIKit.label("Grafikkarte: %s · Renderer: %s%s" % [str(perf.adapter), perf.renderer_label(),
			("" if perf.measured_fps <= 0.0 else " · gemessen %d Bilder/s" % int(perf.measured_fps))], "CaptionLabel")
		hw.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		hw.custom_minimum_size = Vector2(520, 0)
		_body.add_child(hw)
		if lvl <= 1:
			var rec: Vector2i = perf.recommended_resolution()
			var rbtn := UIKit.button("Auf %d × %d stellen" % [rec.x, rec.y], "", "GhostButton")
			rbtn.pressed.connect(func() -> void:
				Settings.set_value("graphics", "fullscreen", false, false)
				Settings.set_value("graphics", "resolution", rec)
				Settings.apply_graphics()
				show_tab("graphics"))
			_body.add_child(_row("Kleinere Bildfläche",
				"Weniger Bildpunkte bringen auf schwacher Grafik am meisten. Empfohlen für den Leistungsmodus.",
				rbtn))
		if OS.has_feature("standalone"):
			var rb := UIKit.button("Auf Kompatibilität umstellen (Neustart)" if not perf.is_compatibility()
				else "Auf Standard umstellen (Neustart)", "", "GhostButton")
			rb.pressed.connect(func() -> void:
				perf.restart_with_renderer("forward_plus" if perf.is_compatibility() else "gl_compatibility"))
			_body.add_child(_row("Renderer",
				"Kompatibilität (OpenGL) läuft ohne Vulkan und ist auf älteren Rechnern und Laptops deutlich schneller. Das Spiel startet dafür neu; dein Spielstand wird vorher gesichert.",
				rb))
	var vd := _slider("graphics", "view_distance", 200, 1500, 50, func(x: float) -> String: return "%d m" % int(x), _custom_changed)
	_body.add_child(_row("Sichtweite", "Wie weit Gebäude, Fahrzeuge und Personen dargestellt werden.", vd))
	_body.add_child(_row("Schattenqualität", "Auflösung der Schatten von Sonne und Lampen.",
		_options("graphics", "shadows", ["Niedrig", "Mittel", "Hoch", "Sehr hoch"], [0, 1, 2, 3], _custom_changed)))
	_body.add_child(_row("Kantenglättung (MSAA)", "Glättet Kanten von Gebäuden und Fahrzeugen.",
		_options("graphics", "msaa", ["Aus", "2×", "4×"], [0, 1, 2], _custom_changed)))
	_body.add_child(_row("FXAA", "Schnelle zusätzliche Kantenglättung als Nachbearbeitung.", _toggle("graphics", "fxaa", _custom_changed)))
	var rs := _slider("graphics", "render_scale", 0.5, 1.0, 0.05, _pct, _custom_changed)
	_body.add_child(_row("Renderauflösung", "Unter 100 % wird die 3D-Szene kleiner berechnet und hochskaliert (mehr Leistung).", rs))


func _matches_preset(i: int) -> bool:
	var p: Array = QUALITY_PRESETS[i]
	return absf(float(Settings.get_value("graphics", "view_distance")) - p[0]) < 1.0 \
		and int(Settings.get_value("graphics", "shadows")) == p[1] \
		and int(Settings.get_value("graphics", "msaa")) == p[2] \
		and bool(Settings.get_value("graphics", "fxaa")) == p[3] \
		and absf(float(Settings.get_value("graphics", "render_scale")) - p[4]) < 0.01


func _set_quality(i: int) -> void:
	var p: Array = QUALITY_PRESETS[i]
	var perf := get_node_or_null("/root/Perf")
	if perf != null:
		perf.set_level(i, false)
	Settings.set_value("graphics", "quality", i, false)
	Settings.set_value("graphics", "view_distance", p[0], false)
	Settings.set_value("graphics", "shadows", p[1], false)
	Settings.set_value("graphics", "msaa", p[2], false)
	Settings.set_value("graphics", "fxaa", p[3], false)
	Settings.set_value("graphics", "render_scale", p[4])
	_apply_gfx()
	show_tab("graphics")


func _custom_changed() -> void:
	_apply_gfx()
	var q := int(Settings.get_value("graphics", "quality"))
	for i in _quality_chips.size():
		(_quality_chips[i] as Button).set_pressed_no_signal(i == q and _matches_preset(i))


# ================================================================ Audio

func _audio() -> void:
	_body.add_child(_heading("Lautstärke", "volume"))
	for a in [["master", "Gesamtlautstärke", "Regelt alle Klänge des Spiels."], ["music", "Musik", "Hintergrundmusik in Menüs und im Spiel."],
			["sfx", "Effekte", "Schritte, Fahrzeuge, Oberflächenklänge."], ["ambient", "Umgebung", "Stadtgeräusche, Wetter, Menschenmengen."]]:
		_body.add_child(_row(a[1], a[2], _slider("audio", a[0], 0.0, 1.0, 0.01, _pct, _apply_audio), "volume"))


# ================================================================ Steuerung

func _controls() -> void:
	_body.add_child(_heading("Maus und Kamera", "mouse"))
	_body.add_child(_row("Mausempfindlichkeit", "Wie schnell sich die Kamera mit der Maus dreht.",
		_slider("controls", "mouse_sensitivity", 0.05, 1.0, 0.01, func(x: float) -> String: return UIKit.decimal(x, 2))))
	_body.add_child(_row("Maus-Y-Achse umkehren", "Maus nach vorne schieben lässt die Kamera nach unten blicken.", _toggle("controls", "invert_y")))
	_body.add_child(_row("Kameraperspektive", "Startperspektive; im Spiel wechselbar mit %s." % Settings.binding_label("camera_toggle"),
		_options("gameplay", "camera_mode", ["Third-Person (hinter der Figur)", "Ego-Perspektive"], ["third", "first"])))
	var head := UIKit.hbox(12)
	head.add_child(_heading("Tastenbelegung", "keyboard"))
	head.add_child(UIKit.spacer())
	var reset := UIKit.button("Standard wiederherstellen", "refresh", "GhostButton")
	reset.pressed.connect(func() -> void:
		var m := UIModal.confirm(UIKit.modal_host(self), "Tastenbelegung zurücksetzen?",
			"Alle Tasten werden auf die Standardbelegung zurückgesetzt.", "Zurücksetzen", true, "refresh")
		m.answered.connect(func(ok: bool) -> void:
			if ok:
				Settings.reset_bindings()
				show_tab("controls")))
	head.add_child(UIKit.margin(reset, 0, 10, 0, 0))
	_body.add_child(head)
	_body.add_child(UIKit.label("Klicke auf eine Taste und drücke anschließend die neue Taste oder Maustaste. Esc bricht ab.", "CaptionLabel"))
	var conflicts := find_conflicts()
	var grid := GridContainer.new()
	grid.columns = 2
	grid.add_theme_constant_override("h_separation", 16)
	grid.add_theme_constant_override("v_separation", 16)
	for g in BIND_GROUPS:
		var card := UIKit.card()
		card.size_flags_horizontal = SIZE_EXPAND_FILL
		var v := UIKit.vbox(6)
		var gh := UIKit.hbox(10)
		gh.add_child(UIKit.icon_rect(g[1], 20, UITheme.ACCENT))
		gh.add_child(UIKit.eyebrow(g[0]))
		v.add_child(gh)
		for action in g[2]:
			v.add_child(_bind_row(action, conflicts.get(action, "")))
		card.add_child(v)
		grid.add_child(card)
	_body.add_child(grid)


func _bind_row(action: String, conflict: String) -> Control:
	var h := UIKit.hbox(10)
	var l := UIKit.label(Settings.ACTION_LABELS.get(action, action))
	l.size_flags_horizontal = SIZE_EXPAND_FILL
	h.add_child(l)
	if conflict != "":
		var w := UIKit.icon_rect("warning", 18, UITheme.WARNING)
		w.mouse_filter = MOUSE_FILTER_STOP
		w.tooltip_text = "Gleiche Taste wie „%s“" % conflict
		h.add_child(w)
	var b := Button.new()
	b.text = UIKit._key_text(Settings.binding_label(action))
	b.custom_minimum_size = Vector2(190, 40)
	b.add_theme_font_override("font", UITheme.font(650, false, true))
	b.add_theme_font_size_override("font_size", 16)
	b.tooltip_text = "Neu belegen"
	UIKit.hover_fx(b)
	b.pressed.connect(capture_binding.bind(action))
	h.add_child(b)
	return h


## Signatur der ersten Belegung (fuer Vergleiche).
static func binding_signature(action: String) -> String:
	if not InputMap.has_action(action):
		return ""
	var evs := InputMap.action_get_events(action)
	if evs.is_empty():
		return ""
	return event_signature(evs[0])


static func event_signature(ev: InputEvent) -> String:
	if ev is InputEventKey:
		return "k%d" % (ev.physical_keycode if ev.physical_keycode != 0 else ev.keycode)
	if ev is InputEventMouseButton:
		return "m%d" % ev.button_index
	return ""


static func _contexts(action: String) -> Array:
	return CONTEXTS.get(action, ["global"])


static func _contexts_clash(a: String, b: String) -> bool:
	var ca := _contexts(a)
	var cb := _contexts(b)
	if "global" in ca or "global" in cb:
		return true
	for c in ca:
		if c in cb:
			return true
	return false


## action -> Anzeigename der kollidierenden Aktion.
static func find_conflicts() -> Dictionary:
	var out := {}
	var actions: Array = Settings.ACTION_LABELS.keys()
	for a in actions:
		var sa := binding_signature(a)
		if sa == "":
			continue
		for b in actions:
			if a != b and binding_signature(b) == sa and _contexts_clash(a, b):
				out[a] = Settings.ACTION_LABELS.get(b, b)
	return out


## Aktionen, die mit einer neuen Belegung kollidieren wuerden.
static func conflicts_for(action: String, ev: InputEvent) -> Array:
	var sig := event_signature(ev)
	var out := []
	for b in Settings.ACTION_LABELS.keys():
		if b != action and binding_signature(b) == sig and _contexts_clash(action, b):
			out.append(Settings.ACTION_LABELS.get(b, b))
	return out


func capture_binding(action: String) -> UIModal:
	var m := UIModal.custom(UIKit.modal_host(self), "Taste belegen: %s" % Settings.ACTION_LABELS.get(action, action), "keyboard", 600)
	var info := UIKit.label("Drücke jetzt die gewünschte Taste oder Maustaste.", "SubtitleLabel", true)
	m.body.add_child(info)
	var cur := UIKit.hbox(10)
	cur.add_child(UIKit.label("Aktuell:", "CaptionLabel"))
	cur.add_child(UIKit.action_keycap(action))
	m.body.add_child(cur)
	var cancel := UIKit.button("Abbrechen", "", "GhostButton", 140)
	cancel.focus_mode = FOCUS_NONE
	cancel.pressed.connect(m.dismiss)
	m.buttons.add_child(cancel)
	var catcher := _KeyCatcher.new()
	catcher.cancel_button = cancel
	catcher.captured.connect(func(ev: InputEvent) -> void:
		var clash := conflicts_for(action, ev)
		if clash.is_empty():
			Settings.rebind(action, ev)
			m.dismiss()
			show_tab("controls")
			return
		catcher.queue_free()
		info.text = "Diese Taste ist bereits belegt: %s. Trotzdem zuweisen?" % ", ".join(clash)
		info.add_theme_color_override("font_color", UITheme.WARNING)
		var ok := UIKit.button("Trotzdem belegen", "warning", "AccentButton")
		ok.pressed.connect(func() -> void:
			Settings.rebind(action, ev)
			m.dismiss()
			show_tab("controls"))
		m.buttons.add_child(ok))
	m.add_child(catcher)
	return m


## Faengt die naechste Taste/Maustaste ab (Esc = Abbruch ueber den Dialog).
class _KeyCatcher extends Node:
	signal captured(ev: InputEvent)
	var cancel_button: Control
	var _armed := false

	func _ready() -> void:
		# Den Klick, der den Dialog geoeffnet hat, nicht mitzaehlen
		await get_tree().create_timer(0.15).timeout
		_armed = true

	func _input(event: InputEvent) -> void:
		if not _armed:
			return
		if event is InputEventKey and event.pressed and not event.echo:
			if event.keycode == KEY_ESCAPE:
				return
			get_viewport().set_input_as_handled()
			_armed = false
			captured.emit(event)
		elif event is InputEventMouseButton and event.pressed:
			if event.button_index in [MOUSE_BUTTON_WHEEL_UP, MOUSE_BUTTON_WHEEL_DOWN]:
				return
			if cancel_button and cancel_button.get_global_rect().has_point(cancel_button.get_global_mouse_position()):
				return
			get_viewport().set_input_as_handled()
			_armed = false
			captured.emit(event)


# ================================================================ Gameplay

func _gameplay() -> void:
	_body.add_child(_heading("Oberfläche", "display"))
	_body.add_child(_row("UI-Skalierung", "Größe aller Menüs und Anzeigen. Wird beim Loslassen übernommen.",
		_slider("gameplay", "ui_scale", 0.75, 1.5, 0.05, _pct, _apply_gfx, true)))
	_body.add_child(_row("Dialogtexte und Untertitel", "Zeigt gesprochene Texte von Personen als Text an.", _toggle("gameplay", "subtitles")))
	_body.add_child(_row("Automatisch speichern", "Intervall in Echtzeit-Minuten.",
		_options("gameplay", "autosave_minutes", ["Aus", "Alle 5 Minuten", "Alle 10 Minuten", "Alle 15 Minuten", "Alle 30 Minuten"], AUTOSAVE)))
	_body.add_child(_heading("Simulation", "city"))
	_body.add_child(_row("Spielgeschwindigkeit", "Spielminuten pro Echtzeitsekunde bei Stufe 1×.",
		_slider("gameplay", "time_scale", 0.25, 4.0, 0.25, func(x: float) -> String: return UIKit.decimal(x, 2) + "×")))
	_body.add_child(_row("Personendichte", "Anteil der Bewohner, die sichtbar auf den Straßen unterwegs sind.",
		_slider("gameplay", "population_density", 0.25, 1.5, 0.05, _pct)))
	_body.add_child(_row("Verkehrsdichte", "Anzahl der Fahrzeuge auf den Straßen.",
		_slider("gameplay", "traffic_density", 0.25, 1.5, 0.05, _pct)))
	_body.add_child(_heading("Sandbox-Regeln", "sliders"))
	var w = Game.world
	if w != null and w.rules != null and w.rules.mode == "sandbox":
		_body.add_child(UIKit.label("Änderungen gelten sofort für den laufenden Spielstand. Stadtgröße und Einwohnerzahl sind beim Start festgelegt.", "CaptionLabel", true))
		var ed := UIRulesEditor.new(w.rules.values)
		ed.changed.connect(func(vals: Dictionary) -> void:
			for k in vals:
				if k in ["city_size", "population"]:
					continue
				w.rules.values[k] = GameRules._clamp(k, vals[k]))
		_body.add_child(ed)
	else:
		var txt := "Die Sandbox-Regeln (Startgeld, Alterung, Bedürfnisse, Wirtschaft, Kriminalität, Tod …) werden beim Start eines Sandbox-Spiels im Assistenten „Neues Spiel“ festgelegt und können während eines Sandbox-Spiels hier angepasst werden."
		if w != null:
			txt = "Dieser Spielstand verwendet den Modus „%s“. Regeln sind nur im Sandbox-Modus während des Spiels veränderbar." % UIKit.mode_label(w.rules.mode)
		var c := UIKit.card()
		var h := UIKit.hbox(14)
		h.add_child(UIKit.icon_rect("info", 22, UITheme.ACCENT))
		var l := UIKit.label(txt, "SubtitleLabel", true)
		l.size_flags_horizontal = SIZE_EXPAND_FILL
		h.add_child(l)
		c.add_child(h)
		_body.add_child(c)


# ================================================================ KI

func _ai() -> void:
	_body.add_child(_heading("Groq-KI (optional)", "sparkle"))
	var intro := UIKit.card()
	var iv := UIKit.hbox(14)
	iv.add_child(UIKit.icon_rect("info", 22, UITheme.ACCENT))
	var il := UIKit.label("Mit einem eigenen Groq-API-Schlüssel können Personen abwechslungsreicher sprechen und Nachrichten lebendiger formuliert werden. Ohne Schlüssel funktioniert das Spiel vollständig mit eingebauten Texten. Der Schlüssel wird nur lokal auf diesem Gerät gespeichert – nie in Spielständen oder Protokollen.", "SubtitleLabel", true)
	il.size_flags_horizontal = SIZE_EXPAND_FILL
	iv.add_child(il)
	intro.add_child(iv)
	_body.add_child(intro)
	# Status
	var status_card := UIKit.card()
	var sh := UIKit.hbox(14)
	var active := AIConfig.is_active()
	var col := UITheme.SUCCESS if active else (UITheme.WARNING if AIConfig.has_key() or bool(Settings.get_value("ai", "groq_enabled")) else UITheme.TEXT_MUTED)
	sh.add_child(UIKit.icon_badge("check_circle" if active else "info", col, 44, 0.5))
	var sv := UIKit.vbox(2)
	sv.size_flags_horizontal = SIZE_EXPAND_FILL
	sv.add_child(UIKit.label("Status", "CaptionLabel"))
	var st := UIKit.label(AIConfig.status_text(), "TitleLabel", true)
	st.add_theme_font_size_override("font_size", 20)
	sv.add_child(st)
	if AIConfig.has_key():
		sv.add_child(UIKit.label("Schlüssel: " + AIConfig.masked_key(), "ValueLabel"))
	sh.add_child(sv)
	status_card.add_child(sh)
	_body.add_child(status_card)
	_body.add_child(_row("KI verwenden", "Nur wirksam, wenn ein Schlüssel hinterlegt ist.", _toggle("ai", "groq_enabled", func() -> void: show_tab("ai"))))
	var models: Array = AIConfig.MODELS.keys()
	var labels: Array = []
	for m in models:
		labels.append("%s – %s" % [m, AIConfig.MODELS[m]])
	_body.add_child(_row("Modell", "Sprachmodell bei Groq.", _options("ai", "groq_model", labels, models)))
	# Schluessel eingeben
	var kv := UIKit.vbox(10)
	var kh := UIKit.hbox(10)
	var edit := LineEdit.new()
	edit.secret = true
	edit.secret_character = "•"
	edit.placeholder_text = "API-Schlüssel einfügen (gsk_…)"
	edit.size_flags_horizontal = SIZE_EXPAND_FILL
	edit.context_menu_enabled = true
	kh.add_child(edit)
	var save := UIKit.button("Speichern", "save", "AccentButton")
	kh.add_child(save)
	kv.add_child(kh)
	var msg := UIKit.label("", "CaptionLabel", true)
	kv.add_child(msg)
	save.pressed.connect(func() -> void:
		if AIConfig.set_key(edit.text):
			edit.text = ""
			show_tab("ai")
		else:
			msg.text = "Ungültiger Schlüssel – bitte vollständig einfügen."
			msg.add_theme_color_override("font_color", UITheme.DANGER))
	edit.text_submitted.connect(func(_t: String) -> void: save.pressed.emit())
	_body.add_child(_row("API-Schlüssel", "Wird verschlüsselt in den lokalen Benutzerdaten abgelegt. Alternativ: Umgebungsvariable GROQ_API_KEY.", kv, "key"))
	if AIConfig.key_source() == "file":
		var rm := UIKit.button("Schlüssel entfernen", "trash", "DangerButton")
		rm.size_flags_horizontal = SIZE_SHRINK_END
		rm.pressed.connect(func() -> void:
			var m := UIModal.confirm(UIKit.modal_host(self), "Schlüssel entfernen?",
				"Der gespeicherte Groq-API-Schlüssel wird von diesem Gerät gelöscht.", "Entfernen", true, "trash")
			m.answered.connect(func(ok: bool) -> void:
				if ok:
					AIConfig.clear_key()
					show_tab("ai")))
		_body.add_child(_row("Gespeicherten Schlüssel löschen", "Entfernt den Schlüssel dauerhaft von diesem Gerät.", rm, "trash"))
