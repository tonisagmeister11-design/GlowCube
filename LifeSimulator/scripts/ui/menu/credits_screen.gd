extends MenuScreen
## Credits: Team-Rollen, verwendete Technik und Lizenzen, Hinweis auf
## fiktive Namen.

const ROLES := [
	["Spielkonzept und Leitung", "star", "Vision, Spielregeln, Abstimmung aller Bereiche"],
	["Stadt und Welt", "city", "Stadtgenerator, Straßennetz, Gebäude, Tag und Nacht"],
	["Simulation", "chart", "Wirtschaft, Arbeitsmarkt, Immobilien, Bevölkerung, Politik"],
	["Figuren und Animation", "user", "Prozedurale Menschen, Charaktereditor, Bewegung"],
	["Verkehr", "bus", "Fahrzeuge, Ampeln, Buslinien, Fußgänger"],
	["Benutzeroberfläche", "display", "Design-System, Menüs, HUD, Smartphone, Stadtkarte, Symbole"],
]
const FONT_LICENSES := [
	["Inter", "© The Inter Project Authors", "res://assets/fonts/Inter-OFL.txt"],
	["Montserrat", "© The Montserrat Project Authors", "res://assets/fonts/Montserrat-OFL.txt"],
]


func on_open() -> void:
	setup_frame("Credits", "Die Menschen und Werkzeuge hinter Life Simulator.", "Hauptmenü", "star")
	var cols := UIKit.hbox(22)
	cols.size_flags_vertical = SIZE_EXPAND_FILL
	# Team
	var left := UIKit.vbox(14)
	left.size_flags_horizontal = SIZE_EXPAND_FILL
	var logo := TextureRect.new()
	logo.texture = load("res://assets/branding/logo.svg")
	logo.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	logo.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	logo.custom_minimum_size = Vector2(0, 190)
	left.add_child(logo)
	left.add_child(UIKit.section("Entwickelt vom Life-Simulator-Team", "users"))
	var grid := GridContainer.new()
	grid.columns = 2
	for r in ROLES:
		var c := UIKit.card()
		c.size_flags_horizontal = SIZE_EXPAND_FILL
		var h := UIKit.hbox(12)
		h.add_child(UIKit.icon_badge(r[1], UITheme.ACCENT, 40, 0.5))
		var v := UIKit.vbox(0)
		v.size_flags_horizontal = SIZE_EXPAND_FILL
		var t := UIKit.label(r[0])
		t.add_theme_font_override("font", UITheme.font(650))
		v.add_child(t)
		v.add_child(UIKit.label(r[2], "CaptionLabel", true))
		h.add_child(v)
		c.add_child(h)
		grid.add_child(c)
	left.add_child(grid)
	cols.add_child(UIKit.scroll(left))
	# Technik und Lizenzen
	var right := UIKit.vbox(14)
	right.size_flags_horizontal = SIZE_EXPAND_FILL
	right.add_child(UIKit.section("Technik und Lizenzen", "book"))
	var gc := UIKit.card()
	var gv := UIKit.vbox(8)
	var gh := UIKit.hbox(12)
	gh.add_child(UIKit.icon_badge("gear", UITheme.ACCENT, 40, 0.5))
	var gt := UIKit.vbox(0)
	gt.size_flags_horizontal = SIZE_EXPAND_FILL
	var gname := UIKit.label("Godot Engine %s" % Engine.get_version_info().get("string", ""))
	gname.add_theme_font_override("font", UITheme.font(650))
	gt.add_child(gname)
	gt.add_child(UIKit.label("godotengine.org · MIT-Lizenz", "CaptionLabel"))
	gh.add_child(gt)
	var gb := UIKit.button("Lizenz", "book", "GhostButton")
	gb.pressed.connect(func() -> void: _show_text("Godot Engine – MIT-Lizenz", Engine.get_license_text()))
	gh.add_child(gb)
	gv.add_child(gh)
	gv.add_child(UIKit.label("Dieses Spiel verwendet die Godot Engine. Copyright (c) 2014-present Godot Engine contributors, (c) 2007-2014 Juan Linietsky, Ariel Manzur. Die Engine enthält weitere Open-Source-Komponenten.", "CaptionLabel", true))
	var tb := UIKit.button("Drittanbieter-Komponenten der Engine", "layers", "GhostButton")
	tb.pressed.connect(_show_components)
	gv.add_child(tb)
	gc.add_child(gv)
	right.add_child(gc)
	for f in FONT_LICENSES:
		var fc := UIKit.card()
		var fh := UIKit.hbox(12)
		fh.add_child(UIKit.icon_badge("edit", UITheme.WARM, 40, 0.5))
		var fv := UIKit.vbox(0)
		fv.size_flags_horizontal = SIZE_EXPAND_FILL
		var fl := UIKit.label("Schrift „%s“" % f[0])
		fl.add_theme_font_override("font", UITheme.font(650))
		fv.add_child(fl)
		fv.add_child(UIKit.label("%s · SIL Open Font License 1.1" % f[1], "CaptionLabel", true))
		fh.add_child(fv)
		var fb := UIKit.button("Lizenz", "book", "GhostButton")
		var path: String = f[2]
		var title: String = f[0]
		fb.pressed.connect(func() -> void: _show_text("%s – SIL Open Font License 1.1" % title, FileAccess.get_file_as_string(path)))
		fh.add_child(fb)
		fc.add_child(fh)
		right.add_child(fc)
	var dc := UIKit.card()
	var dh := UIKit.hbox(12)
	dh.add_child(UIKit.icon_badge("info", UITheme.TEXT_MUTED, 40, 0.5))
	var dl := UIKit.label("Alle Personen, Firmen, Marken, Organisationen und Orte in diesem Spiel – einschließlich der Stadt Havenbrück – sind frei erfunden. Ähnlichkeiten mit realen Personen oder Unternehmen sind zufällig.", "SubtitleLabel", true)
	dl.size_flags_horizontal = SIZE_EXPAND_FILL
	dh.add_child(dl)
	dc.add_child(dh)
	right.add_child(dc)
	right.add_child(UIKit.label("Symbole, Logo und Benutzeroberfläche: eigene Gestaltung des Life-Simulator-Teams.", "CaptionLabel", true))
	cols.add_child(UIKit.scroll(right))
	content.add_child(cols)


func _show_text(title: String, text: String) -> void:
	var m := UIModal.custom(UIKit.modal_host(self), title, "book", 860)
	var rt := RichTextLabel.new()
	rt.text = text if text != "" else "Lizenztext nicht gefunden."
	rt.custom_minimum_size = Vector2(800, 520)
	rt.scroll_active = true
	rt.selection_enabled = true
	rt.add_theme_font_size_override("normal_font_size", 15)
	m.body.add_child(rt)
	var ok := UIKit.button("Schließen", "", "AccentButton", 160)
	ok.pressed.connect(m.dismiss)
	m.buttons.add_child(ok)


func _show_components() -> void:
	var lines := PackedStringArray()
	for c in Engine.get_copyright_info():
		var parts: Array = c.get("parts", [])
		var lic := ""
		if not parts.is_empty():
			lic = str(parts[0].get("license", ""))
		lines.append("%s  (%s)" % [c.get("name", ""), lic])
	_show_text("Komponenten der Godot Engine", "\n".join(lines))
