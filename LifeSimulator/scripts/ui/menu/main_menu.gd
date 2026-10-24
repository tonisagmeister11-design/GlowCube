class_name MainMenu
extends Control
## Hauptmenue: Hintergrund (Live-Kulisse oder Titelbild), Logo, Navigation
## und animierte Unterbildschirme (Neues Spiel, Laden, Einstellungen,
## Steuerung, Credits).

const SCREENS := {
	"new_game": "res://scripts/ui/menu/new_game_wizard.gd",
	"load": "res://scripts/ui/menu/load_screen.gd",
	"settings": "res://scripts/ui/menu/settings_screen.gd",
	"controls": "res://scripts/ui/menu/controls_screen.gd",
	"credits": "res://scripts/ui/menu/credits_screen.gd",
}

## Beim naechsten Oeffnen direkt anzuzeigender Bildschirm, z.B. nach dem Tod
## der Spielfigur: {"screen": "new_game", "arg": "character"}.
static var next_screen: Dictionary = {}

var background: MenuBackground
var home: Control
var nav: VBoxContainer
var continue_card: UISelectCard
var screen_layer: Control
var blur: ColorRect
var current: MenuScreen
var _latest: Dictionary = {}
var _busy := false


func _ready() -> void:
	set_anchors_preset(PRESET_FULL_RECT)
	add_to_group("ui_root")
	background = MenuBackground.new()
	add_child(background)
	_build_overlays()
	home = Control.new()
	home.set_anchors_preset(PRESET_FULL_RECT)
	home.mouse_filter = MOUSE_FILTER_IGNORE
	add_child(home)
	_build_home()
	blur = UIKit.blur_rect(9.0, Color(0.02, 0.035, 0.075, 0.62))
	blur.modulate.a = 0.0
	blur.visible = false
	add_child(blur)
	screen_layer = Control.new()
	screen_layer.set_anchors_preset(PRESET_FULL_RECT)
	screen_layer.mouse_filter = MOUSE_FILTER_IGNORE
	add_child(screen_layer)
	Input.mouse_mode = Input.MOUSE_MODE_VISIBLE
	_intro()
	if not next_screen.is_empty():
		var ns: Dictionary = next_screen
		next_screen = {}
		open_screen(str(ns.get("screen", "")), ns.get("arg"), false)


func _build_overlays() -> void:
	# Links dunkler Verlauf hinter der Navigation, unten Verlauf fuer die Fusszeile
	var left := UIKit.gradient_rect(
		PackedColorArray([Color(0.015, 0.03, 0.07, 0.92), Color(0.015, 0.03, 0.07, 0.72), Color(0.015, 0.03, 0.07, 0.0)]),
		PackedFloat32Array([0.0, 0.45, 1.0]))
	left.anchor_right = 0.62
	left.anchor_bottom = 1.0
	add_child(left)
	var bottom := UIKit.gradient_rect(
		PackedColorArray([Color(0.01, 0.02, 0.05, 0.0), Color(0.01, 0.02, 0.05, 0.75)]),
		PackedFloat32Array([0.0, 1.0]), Vector2(0, 0), Vector2(0, 1))
	bottom.anchor_top = 0.72
	bottom.anchor_right = 1.0
	bottom.anchor_bottom = 1.0
	add_child(bottom)
	var top := UIKit.gradient_rect(
		PackedColorArray([Color(0.01, 0.02, 0.05, 0.55), Color(0.01, 0.02, 0.05, 0.0)]),
		PackedFloat32Array([0.0, 1.0]), Vector2(0, 0), Vector2(0, 1))
	top.anchor_right = 1.0
	top.anchor_bottom = 0.22
	add_child(top)


func _build_home() -> void:
	var col := UIKit.vbox(0)
	col.position = Vector2(104, 70)
	col.custom_minimum_size = Vector2(560, 0)
	home.add_child(col)
	var logo := TextureRect.new()
	logo.texture = load("res://assets/branding/logo_compact.svg")
	logo.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	logo.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT
	logo.custom_minimum_size = Vector2(520, 205)
	logo.mouse_filter = MOUSE_FILTER_IGNORE
	col.add_child(logo)
	var tag := UIKit.label("DEIN LEBEN. DEINE ENTSCHEIDUNGEN. DEINE WELT.", "EyebrowLabel")
	tag.add_theme_color_override("font_color", Color(1, 1, 1, 0.82))
	tag.add_theme_font_size_override("font_size", 15)
	tag.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	tag.custom_minimum_size.x = 520
	tag.size_flags_horizontal = SIZE_SHRINK_BEGIN
	col.add_child(tag)
	col.add_child(UIKit.spacer(false, false, Vector2(0, 46)))
	# Fortsetzen-Karte
	continue_card = UISelectCard.new(UITheme.RADIUS_L, 18)
	continue_card.set_accent(UITheme.WARM)
	continue_card.custom_minimum_size = Vector2(520, 0)
	continue_card.size_flags_horizontal = SIZE_SHRINK_BEGIN
	continue_card.pressed.connect(_on_continue)
	col.add_child(continue_card)
	col.add_child(UIKit.spacer(false, false, Vector2(0, 18)))
	nav = UIKit.vbox(2)
	nav.custom_minimum_size.x = 440
	nav.size_flags_horizontal = SIZE_SHRINK_BEGIN
	col.add_child(nav)
	_add_item("Neues Spiel", "play", func() -> void: open_screen("new_game", "mode"))
	_add_item("Spiel laden", "folder", func() -> void: open_screen("load"))
	_add_item("Charakter erstellen", "user", func() -> void: open_screen("new_game", "character"))
	_add_item("Spielmodus wählen", "sliders", func() -> void: open_screen("new_game", "mode"))
	_add_item("Einstellungen", "gear", func() -> void: open_screen("settings"))
	_add_item("Steuerung", "keyboard", func() -> void: open_screen("controls"))
	_add_item("Credits", "star", func() -> void: open_screen("credits"))
	_add_item("Beenden", "door", _on_quit)
	_refresh_continue()
	# Fusszeile
	var foot := UIKit.hbox(16)
	foot.anchor_top = 1.0
	foot.anchor_bottom = 1.0
	foot.offset_left = 108
	foot.offset_top = -64
	foot.offset_bottom = -34
	foot.offset_right = 900
	foot.add_child(UIKit.label("Version %s" % ProjectSettings.get_setting("application/config/version", "0.1"), "CaptionLabel"))
	foot.add_child(UIKit.label("·", "CaptionLabel"))
	foot.add_child(UIKit.label("Alle Namen, Firmen und Marken sind frei erfunden.", "CaptionLabel"))
	home.add_child(foot)
	var hint := UIKit.hbox(8)
	hint.anchor_left = 1.0
	hint.anchor_right = 1.0
	hint.anchor_top = 1.0
	hint.anchor_bottom = 1.0
	hint.offset_left = -560
	hint.offset_right = -96
	hint.offset_top = -66
	hint.offset_bottom = -34
	hint.alignment = BoxContainer.ALIGNMENT_END
	hint.add_child(UIKit.keycap("Pfeil oben"))
	hint.add_child(UIKit.keycap("Pfeil unten"))
	hint.add_child(UIKit.label("Auswählen", "CaptionLabel"))
	hint.add_child(UIKit.spacer(false, false, Vector2(10, 0)))
	hint.add_child(UIKit.keycap("Eingabe"))
	hint.add_child(UIKit.label("Bestätigen", "CaptionLabel"))
	home.add_child(hint)


func _add_item(text: String, icon_name: String, cb: Callable) -> Button:
	var b := UIKit.button(text, icon_name, "MainMenuButton")
	b.alignment = HORIZONTAL_ALIGNMENT_LEFT
	b.custom_minimum_size = Vector2(440, 56)
	b.pressed.connect(cb)
	var base_x := 0.0
	b.mouse_entered.connect(func() -> void: b.grab_focus())
	b.focus_entered.connect(func() -> void:
		var tw := b.create_tween().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
		tw.tween_property(b, "theme_override_constants/h_separation", 24, 0.15))
	b.focus_exited.connect(func() -> void:
		var tw := b.create_tween().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
		tw.tween_property(b, "theme_override_constants/h_separation", 18, 0.15))
	nav.add_child(b)
	return b


func _refresh_continue() -> void:
	UIKit.clear(continue_card)
	var saves := SaveSystem.list_saves()
	_latest = saves[0] if not saves.is_empty() else {}
	continue_card.visible = not _latest.is_empty()
	if _latest.is_empty():
		return
	var h := UIKit.hbox(16)
	h.add_child(UIKit.icon_badge("play", UITheme.WARM, 52, 0.42, true))
	var v := UIKit.vbox(2)
	v.size_flags_horizontal = SIZE_EXPAND_FILL
	var t := UIKit.label("Fortsetzen", "TitleLabel")
	v.add_child(t)
	var parts: Array[String] = [str(_latest.get("character_name", "Unbekannt"))]
	if _latest.has("age"):
		parts.append(UIKit.years(float(_latest.age)))
	parts.append("Tag %d" % int(_latest.get("day", 1)))
	var l := UIKit.label(" · ".join(parts), "SubtitleLabel")
	l.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	v.add_child(l)
	v.add_child(UIKit.label("Gespeichert %s · %s gespielt" % [UIKit.ago(float(_latest.get("saved_unix", 0))),
		UIKit.play_time(float(_latest.get("play_time", 0)))], "CaptionLabel"))
	h.add_child(v)
	h.add_child(UIKit.icon_rect("chevron_right", 22, UITheme.TEXT_MUTED))
	continue_card.add_child(h)


func _intro() -> void:
	var i := 0
	for c in [continue_card] + nav.get_children():
		if c is Control and c.visible:
			c.modulate.a = 0.0
			var tw: Tween = c.create_tween().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
			tw.tween_interval(0.15 + i * 0.045)
			tw.tween_property(c, "modulate:a", 1.0, 0.35)
			i += 1
	home.modulate.a = 0.0
	create_tween().tween_property(home, "modulate:a", 1.0, 0.6)
	await get_tree().process_frame
	if continue_card.visible:
		continue_card.grab_focus()
	elif nav.get_child_count() > 0:
		(nav.get_child(0) as Control).grab_focus()


# ================================================================ Navigation

func open_screen(name: String, a = null, animate := true) -> MenuScreen:
	if not SCREENS.has(name) or _busy:
		return null
	var scr: MenuScreen = load(SCREENS[name]).new()
	scr.host = self
	scr.arg = a
	scr.back_requested.connect(close_screen)
	if current:
		current.queue_free()
		current = null
	screen_layer.add_child(scr)
	current = scr
	scr.on_open()
	blur.visible = true
	home.mouse_filter = MOUSE_FILTER_IGNORE
	_set_home_input(false)
	if animate:
		_busy = true
		var tw := create_tween().set_parallel(true).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
		tw.tween_property(home, "modulate:a", 0.0, 0.22)
		tw.tween_property(home, "position:x", -60.0, 0.3)
		tw.tween_property(blur, "modulate:a", 1.0, 0.3)
		scr.modulate.a = 0.0
		scr.position.x = 70.0
		tw.tween_property(scr, "modulate:a", 1.0, 0.32).set_delay(0.08)
		tw.tween_property(scr, "position:x", 0.0, 0.38).set_delay(0.08)
		tw.chain().tween_callback(func() -> void: _busy = false)
	else:
		home.modulate.a = 0.0
		blur.modulate.a = 1.0
	scr.call_deferred("focus_first")
	return scr


func close_screen() -> void:
	if current == null or _busy:
		return
	var scr := current
	current = null
	_busy = true
	_refresh_continue()
	var tw := create_tween().set_parallel(true).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	tw.tween_property(scr, "modulate:a", 0.0, 0.2)
	tw.tween_property(scr, "position:x", 70.0, 0.26)
	tw.tween_property(blur, "modulate:a", 0.0, 0.3)
	tw.tween_property(home, "modulate:a", 1.0, 0.32).set_delay(0.06)
	tw.tween_property(home, "position:x", 0.0, 0.36).set_delay(0.06)
	tw.chain().tween_callback(func() -> void:
		scr.queue_free()
		blur.visible = false
		_busy = false
		_set_home_input(true)
		if continue_card.visible:
			continue_card.grab_focus()
		elif nav.get_child_count() > 0:
			(nav.get_child(0) as Control).grab_focus())


func _set_home_input(on: bool) -> void:
	for b in nav.get_children():
		if b is Button:
			(b as Button).disabled = not on
			(b as Button).focus_mode = FOCUS_ALL if on else FOCUS_NONE
	continue_card.disabled = not on
	continue_card.modulate.a = 1.0
	continue_card.focus_mode = FOCUS_ALL if on else FOCUS_NONE


func _on_continue() -> void:
	if _latest.is_empty():
		return
	UIKit.sound("confirm")
	Game.load_game(str(_latest.slot))


func _on_quit() -> void:
	var m := UIModal.confirm(self, "Spiel beenden?", "Möchtest du Life Simulator wirklich beenden?", "Beenden", true, "door")
	m.answered.connect(func(ok: bool) -> void:
		if ok:
			get_tree().quit())
