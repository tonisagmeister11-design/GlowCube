class_name UISaveList
extends VBoxContainer
## Liste der Spielstaende (SaveSystem.list_saves()) mit Name, Alter, Tag,
## Modus, Spielzeit und Speicherzeitpunkt. Modus "load": Laden/Loeschen;
## Modus "save": zusaetzlich neuer Slot und Ueberschreiben.

signal load_requested(slot: String)
signal saved(slot: String)

const SLOT_LABELS := {"autosave": "Automatisch", "quicksave": "Schnellspeicher"}

var save_mode := false
var _list: VBoxContainer
var _name_edit: LineEdit


func _init(for_saving := false) -> void:
	save_mode = for_saving
	add_theme_constant_override("separation", 14)
	size_flags_vertical = SIZE_EXPAND_FILL
	size_flags_horizontal = SIZE_EXPAND_FILL
	if save_mode:
		var c := UIKit.card()
		var h := UIKit.hbox(12)
		h.add_child(UIKit.icon_badge("save", UITheme.ACCENT, 44, 0.5))
		_name_edit = LineEdit.new()
		_name_edit.placeholder_text = "Name des neuen Spielstands"
		_name_edit.max_length = 32
		_name_edit.size_flags_horizontal = SIZE_EXPAND_FILL
		_name_edit.text_submitted.connect(func(_t: String) -> void: _save_new())
		h.add_child(_name_edit)
		var b := UIKit.button("Neu speichern", "plus", "AccentButton")
		b.pressed.connect(_save_new)
		h.add_child(b)
		c.add_child(h)
		add_child(c)
	_list = UIKit.vbox(10)
	add_child(UIKit.scroll(_list))
	refresh()


func refresh() -> void:
	UIKit.clear(_list)
	var saves := SaveSystem.list_saves()
	if saves.is_empty():
		var e := UIKit.empty_state("folder", "Noch keine Spielstände",
			"Starte ein neues Leben – das Spiel speichert automatisch in regelmäßigen Abständen." if not save_mode
			else "Gib oben einen Namen ein, um deinen ersten Spielstand anzulegen.")
		e.custom_minimum_size.y = 360
		_list.add_child(e)
		return
	for s in saves:
		_list.add_child(_row(s))
	UIKit.stagger(_list, 0.03)


func _row(s: Dictionary) -> Control:
	var slot := str(s.get("slot", ""))
	var card := UISelectCard.new(UITheme.RADIUS_L, 18)
	card.focus_mode = FOCUS_ALL
	var h := UIKit.hbox(18)
	# Avatar mit Initialen
	var av := PanelContainer.new()
	var sb := StyleBoxFlat.new()
	sb.set_corner_radius_all(30)
	sb.bg_color = Color(UITheme.ACCENT, 0.2)
	sb.border_color = Color(UITheme.ACCENT, 0.6)
	sb.set_border_width_all(1)
	av.add_theme_stylebox_override("panel", sb)
	av.custom_minimum_size = Vector2(60, 60)
	av.size_flags_vertical = SIZE_SHRINK_CENTER
	var ini := UIKit.label(UIKit.initials(str(s.get("character_name", "?"))), "TitleLabel")
	ini.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	ini.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	av.add_child(ini)
	h.add_child(av)
	var v := UIKit.vbox(4)
	v.size_flags_horizontal = SIZE_EXPAND_FILL
	var th := UIKit.hbox(10)
	th.add_child(UIKit.label(str(s.get("character_name", "Unbekannt")), "TitleLabel"))
	var mode := str(s.get("mode", "life"))
	th.add_child(UIKit.pill(UIKit.mode_label(mode), {"life": UITheme.ACCENT, "sandbox": UITheme.WARM, "free": UITheme.SUCCESS}.get(mode, UITheme.ACCENT), UIKit.MODE_ICONS.get(mode, "info")))
	if SLOT_LABELS.has(slot):
		th.add_child(UIKit.pill(SLOT_LABELS[slot], UITheme.TEXT_MUTED))
	v.add_child(th)
	var meta := UIKit.hbox(18)
	if s.has("age"):
		meta.add_child(_meta("cake", UIKit.years(float(s.age))))
	meta.add_child(_meta("calendar", "Tag %d" % int(s.get("day", 1))))
	meta.add_child(_meta("clock", UIKit.play_time(float(s.get("play_time", 0.0))) + " gespielt"))
	if s.has("job_title") and str(s.job_title) != "":
		meta.add_child(_meta("briefcase", str(s.job_title)))
	if s.has("money"):
		meta.add_child(_meta("wallet", UIKit.money_short(float(s.money))))
	v.add_child(meta)
	h.add_child(v)
	var rv := UIKit.vbox(2)
	rv.size_flags_vertical = SIZE_SHRINK_CENTER
	var when := UIKit.label(UIKit.datetime(float(s.get("saved_unix", 0))), "ValueLabel")
	when.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	rv.add_child(when)
	var ago := UIKit.label(UIKit.ago(float(s.get("saved_unix", 0))), "CaptionLabel")
	ago.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	rv.add_child(ago)
	h.add_child(rv)
	var bh := UIKit.hbox(8)
	bh.size_flags_vertical = SIZE_SHRINK_CENTER
	if save_mode:
		if slot != SaveSystem.AUTOSAVE_SLOT:
			var ow := UIKit.button("Überschreiben", "save")
			ow.pressed.connect(_overwrite.bind(slot))
			bh.add_child(ow)
	else:
		var lb := UIKit.button("Laden", "play", "AccentButton")
		lb.pressed.connect(func() -> void: load_requested.emit(slot))
		bh.add_child(lb)
	var del := UIKit.icon_button("trash", "Spielstand löschen")
	del.theme_type_variation = "IconButton"
	del.pressed.connect(_delete.bind(slot, str(s.get("character_name", ""))))
	bh.add_child(del)
	h.add_child(bh)
	card.add_child(h)
	if not save_mode:
		card.double_pressed.connect(func() -> void: load_requested.emit(slot))
	return card


func _meta(icon_name: String, text: String) -> HBoxContainer:
	var h := UIKit.hbox(6)
	h.add_child(UIKit.icon_rect(icon_name, 16, UITheme.TEXT_FAINT))
	var l := UIKit.label(text, "CaptionLabel")
	l.add_theme_color_override("font_color", UITheme.TEXT_MUTED)
	h.add_child(l)
	return h


func _delete(slot: String, who: String) -> void:
	var m := UIModal.confirm(UIKit.modal_host(self),
		"Spielstand löschen?", "„%s“ (%s) wird endgültig gelöscht. Dies kann nicht rückgängig gemacht werden." % [who, slot],
		"Löschen", true, "trash")
	m.answered.connect(func(ok: bool) -> void:
		if ok:
			SaveSystem.delete(slot)
			refresh())


func _save_new() -> void:
	var n := _name_edit.text.strip_edges()
	if n == "":
		n = "Spielstand %s" % Time.get_datetime_string_from_system(false, true).replace(":", "-")
	var slot := SaveSystem._sanitize(n)
	if SaveSystem.exists(slot):
		_overwrite(slot)
		return
	_do_save(slot)


func _overwrite(slot: String) -> void:
	var m := UIModal.confirm(UIKit.modal_host(self),
		"Spielstand überschreiben?", "Der Spielstand „%s“ wird durch den aktuellen Stand ersetzt." % slot, "Überschreiben", false, "save")
	m.answered.connect(func(ok: bool) -> void:
		if ok:
			_do_save(slot))


func _do_save(slot: String) -> void:
	if Game.save_game(slot):
		if _name_edit:
			_name_edit.text = ""
		saved.emit(slot)
	refresh()
