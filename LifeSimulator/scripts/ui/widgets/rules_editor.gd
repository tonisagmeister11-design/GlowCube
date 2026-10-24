class_name UIRulesEditor
extends VBoxContainer
## Editor fuer Sandbox-Regeln, automatisch erzeugt aus GameRules.SCHEMA.
## Jede Regel bekommt passend zum Typ einen Regler, ein Zahlenfeld, einen
## Schalter oder eine Auswahlliste mit deutscher Beschriftung.

signal changed(values: Dictionary)

## Kurze Erklaerungen je Regel (Transparenz fuer Spieler).
const HINTS := {
	"start_money": "Kontostand einer erwachsenen Figur zu Spielbeginn.",
	"days_per_year": "Wie viele Spieltage ein Lebensjahr dauert.",
	"aging": "Figuren werden älter und durchlaufen Lebensphasen.",
	"needs_decay": "Wie schnell Hunger, Energie, Hygiene usw. sinken.",
	"needs_can_harm": "Unversorgte Bedürfnisse senken die Gesundheit.",
	"price_level": "Faktor auf alle Preise in Geschäften.",
	"wage_level": "Faktor auf alle Löhne.",
	"rent_required": "Miete wird wöchentlich eingezogen; Rückstände führen zur Kündigung.",
	"crime_rate": "Wie häufig NPCs Straftaten begehen.",
	"police_strictness": "Wie konsequent die Polizei Straftaten verfolgt.",
	"death": "Was passiert, wenn deine Figur stirbt.",
	"illness": "Figuren können krank werden.",
	"lifespan": "Durchschnittliches Sterbealter bei natürlichem Tod.",
	"population": "Anzahl der simulierten Einwohner.",
	"city_size": "Fläche und Anzahl der Stadtteile.",
}
const PERCENT_KEYS := ["needs_decay", "price_level", "wage_level", "crime_rate", "police_strictness"]
const ENUM_LABELS := {
	"city_size": {"small": "Klein", "medium": "Mittel", "large": "Groß"},
}

var values: Dictionary = {}
var exclude: Array = ["city_size", "population"]
var _setters := {}


func _init(initial: Dictionary = {}, excluded: Array = ["city_size", "population"]) -> void:
	exclude = excluded
	values = GameRules.PRESETS["sandbox"].duplicate()
	for k in initial:
		values[k] = initial[k]
	add_theme_constant_override("separation", 14)
	_build()


func _build() -> void:
	var presets := UIKit.hbox(10)
	presets.add_child(UIKit.label("Vorlage übernehmen:", "CaptionLabel"))
	for m in ["life", "free", "sandbox"]:
		var b := UIKit.chip(UIKit.mode_label(m) if m != "sandbox" else "Sandbox-Standard", UIKit.MODE_ICONS[m], false)
		b.pressed.connect(apply_preset.bind(m))
		presets.add_child(b)
	add_child(presets)
	var grid := GridContainer.new()
	grid.columns = 2
	grid.add_theme_constant_override("h_separation", 14)
	grid.add_theme_constant_override("v_separation", 14)
	add_child(grid)
	for k in GameRules.SCHEMA:
		if k in exclude:
			continue
		grid.add_child(_row(k))


func _row(k: String) -> Control:
	var s: Dictionary = GameRules.SCHEMA[k]
	var card := UIKit.card()
	card.size_flags_horizontal = SIZE_EXPAND_FILL
	var v := UIKit.vbox(8)
	card.add_child(v)
	var top := UIKit.hbox(10)
	var title_l := UIKit.label(s.label, "", true)
	title_l.add_theme_font_override("font", UITheme.font(620))
	title_l.size_flags_horizontal = SIZE_EXPAND_FILL
	top.add_child(title_l)
	var value_label := UIKit.label("", "ValueLabel")
	value_label.add_theme_color_override("font_color", UITheme.ACCENT_HOVER)
	top.add_child(value_label)
	v.add_child(top)
	var hint := UIKit.label(HINTS.get(k, ""), "CaptionLabel", true)
	v.add_child(hint)
	var ctrl: Control
	match s.type:
		"float", "int":
			if k == "start_money":
				var sp := SpinBox.new()
				sp.min_value = s.min
				sp.max_value = s.max
				sp.step = s.step
				sp.suffix = "€"
				sp.custom_arrow_step = 500
				sp.select_all_on_focus = true
				sp.value = float(values[k])
				sp.size_flags_horizontal = SIZE_EXPAND_FILL
				sp.value_changed.connect(func(x: float) -> void:
					_set_rule(k, x)
					value_label.text = _fmt(k, x))
				_setters[k] = func(x) -> void: sp.set_value_no_signal(float(x))
				ctrl = sp
			else:
				var sl := HSlider.new()
				sl.min_value = s.min
				sl.max_value = s.max
				sl.step = s.step
				sl.value = float(values[k])
				sl.size_flags_horizontal = SIZE_EXPAND_FILL
				sl.custom_minimum_size.y = 28
				sl.value_changed.connect(func(x: float) -> void:
					_set_rule(k, int(x) if s.type == "int" else x)
					value_label.text = _fmt(k, x))
				_setters[k] = func(x) -> void: sl.set_value_no_signal(float(x))
				ctrl = sl
			value_label.text = _fmt(k, values[k])
		"bool":
			var cb := CheckButton.new()
			cb.text = "Aktiv"
			cb.button_pressed = bool(values[k])
			cb.toggled.connect(func(on: bool) -> void:
				_set_rule(k, on)
				cb.text = "Aktiv" if on else "Aus"
				value_label.text = "Ja" if on else "Nein")
			cb.text = "Aktiv" if cb.button_pressed else "Aus"
			value_label.text = "Ja" if cb.button_pressed else "Nein"
			_setters[k] = func(x) -> void:
				cb.set_pressed_no_signal(bool(x))
				cb.text = "Aktiv" if bool(x) else "Aus"
			ctrl = cb
		"enum":
			var ob := OptionButton.new()
			var opts: Array = s.options
			for i in opts.size():
				ob.add_item(_enum_label(k, opts[i]), i)
			ob.selected = maxi(0, opts.find(values[k]))
			ob.size_flags_horizontal = SIZE_EXPAND_FILL
			ob.item_selected.connect(func(i: int) -> void:
				_set_rule(k, opts[i])
				value_label.text = "")
			_setters[k] = func(x) -> void: ob.select(maxi(0, opts.find(x)))
			ctrl = ob
	if ctrl:
		v.add_child(ctrl)
	_setters[k + ":label"] = func(x) -> void:
		value_label.text = "" if s.type == "enum" else _fmt(k, x)
	return card


func _enum_label(k: String, v: String) -> String:
	if k == "death":
		return GameRules.DEATH_LABELS.get(v, v)
	return ENUM_LABELS.get(k, {}).get(v, v)


func _fmt(k: String, x) -> String:
	var s: Dictionary = GameRules.SCHEMA[k]
	if k in PERCENT_KEYS:
		return "%d %%" % roundi(float(x) * 100.0)
	if k == "start_money":
		return UIKit.money(float(x), 0)
	if k == "days_per_year":
		return "%d %s" % [int(x), "Tag" if int(x) == 1 else "Tage"]
	if k == "lifespan":
		return "%d Jahre" % int(x)
	if s.type == "int":
		return UIKit.group(int(x))
	return UIKit.decimal(float(x), 1)


func _set_rule(k: String, v) -> void:
	values[k] = v
	changed.emit(values)


func set_values(d: Dictionary) -> void:
	for k in d:
		if not GameRules.SCHEMA.has(k):
			continue
		values[k] = d[k]
		if _setters.has(k):
			_setters[k].call(d[k])
		if _setters.has(k + ":label"):
			_setters[k + ":label"].call(d[k])
	changed.emit(values)


func apply_preset(mode: String) -> void:
	var p: Dictionary = GameRules.PRESETS[mode].duplicate()
	for k in exclude:
		p.erase(k)
	set_values(p)
