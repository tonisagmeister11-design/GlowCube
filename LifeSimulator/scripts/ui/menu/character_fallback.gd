class_name CharacterFallback
extends VBoxContainer
## Einfaches Charakterformular, falls der Charaktereditor
## (scenes/menu/character_creator.tscn) nicht vorhanden ist. Gleicher
## Vertrag: get_character(), set_character(), Signal changed.

signal changed(character: Dictionary)

const STAGE_AGES := [["baby", 1.0], ["toddler", 3.0], ["child", 8.0], ["teen", 15.0],
	["young_adult", 24.0], ["adult", 38.0], ["older_adult", 56.0], ["senior", 72.0]]

var _name: LineEdit
var _age: SpinBox
var _body := "female"
var _body_chips := {}
var _stage_chips := {}
var _appearance: Dictionary = {}
var _rng := RandomNumberGenerator.new()


func _init() -> void:
	add_theme_constant_override("separation", 18)
	_rng.randomize()
	var note := UIKit.pill("Vereinfachte Charaktererstellung – Aussehen wird passend zufällig erzeugt", UITheme.WARM, "info")
	add_child(note)
	var grid := GridContainer.new()
	grid.columns = 2
	grid.add_theme_constant_override("h_separation", 18)
	grid.add_theme_constant_override("v_separation", 18)
	add_child(grid)
	# Name
	var c1 := UIKit.card()
	c1.size_flags_horizontal = SIZE_EXPAND_FILL
	var v1 := UIKit.vbox(10)
	v1.add_child(UIKit.section("Name", "user"))
	var nh := UIKit.hbox(10)
	_name = LineEdit.new()
	_name.placeholder_text = "Vor- und Nachname"
	_name.max_length = 40
	_name.size_flags_horizontal = SIZE_EXPAND_FILL
	_name.text_changed.connect(func(_t: String) -> void: changed.emit(get_character()))
	nh.add_child(_name)
	var dice := UIKit.icon_button("dice", "Zufälliger Name")
	dice.pressed.connect(_random_name)
	nh.add_child(dice)
	v1.add_child(nh)
	v1.add_child(UIKit.label("Körperdarstellung", "CaptionLabel"))
	var bh := UIKit.hbox(8)
	for b in ["female", "male"]:
		var chip := UIKit.chip(UIKit.BODY_LABELS[b], "user")
		chip.pressed.connect(_set_body.bind(b))
		_body_chips[b] = chip
		bh.add_child(chip)
	v1.add_child(bh)
	c1.add_child(v1)
	grid.add_child(c1)
	# Alter
	var c2 := UIKit.card()
	c2.size_flags_horizontal = SIZE_EXPAND_FILL
	var v2 := UIKit.vbox(10)
	v2.add_child(UIKit.section("Startalter", "cake"))
	var ah := UIKit.hbox(10)
	_age = SpinBox.new()
	_age.min_value = 0.5
	_age.max_value = 100
	_age.step = 0.5
	_age.suffix = "Jahre"
	_age.value = 24
	_age.custom_minimum_size.x = 200
	_age.value_changed.connect(func(_v: float) -> void:
		_sync_stage()
		changed.emit(get_character()))
	ah.add_child(_age)
	v2.add_child(ah)
	v2.add_child(UIKit.label("Lebensphase", "CaptionLabel"))
	var flow := HFlowContainer.new()
	for sa in STAGE_AGES:
		var chip := UIKit.chip(UIKit.life_stage_label(sa[0]))
		chip.pressed.connect(func() -> void: _age.value = sa[1])
		_stage_chips[sa[0]] = chip
		flow.add_child(chip)
	v2.add_child(flow)
	c2.add_child(v2)
	grid.add_child(c2)
	_random_name()
	_set_body("female")
	_sync_stage()


func _random_name() -> void:
	var first: Array = CityCatalog.FIRST_NAMES_F if _body == "female" else CityCatalog.FIRST_NAMES_M
	_name.text = "%s %s" % [first[_rng.randi() % first.size()], CityCatalog.LAST_NAMES[_rng.randi() % CityCatalog.LAST_NAMES.size()]]
	changed.emit(get_character())


func _set_body(b: String) -> void:
	_body = b
	for k in _body_chips:
		(_body_chips[k] as Button).set_pressed_no_signal(k == b)
	changed.emit(get_character())


func _sync_stage() -> void:
	var st := UIKit.life_stage_for_age(_age.value)
	for k in _stage_chips:
		(_stage_chips[k] as Button).set_pressed_no_signal(k == st)


func get_character() -> Dictionary:
	return {"name": _name.text.strip_edges(), "body": _body, "age": float(_age.value), "appearance": _appearance.duplicate(true)}


func set_character(d: Dictionary) -> void:
	_name.text = str(d.get("name", _name.text))
	_age.set_value_no_signal(float(d.get("age", _age.value)))
	_appearance = d.get("appearance", {})
	_set_body(str(d.get("body", _body)))
	_sync_stage()
