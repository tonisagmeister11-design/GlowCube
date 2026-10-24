extends MenuScreen
## Uebersicht der Steuerung (nur lesen) mit Tastenkappen der aktuellen
## Belegung; Verweis auf die Neubelegung in den Einstellungen.

const GROUPS := [
	["Zu Fuß unterwegs", "walk", ["move_forward", "move_back", "move_left", "move_right", "sprint", "jump", "interact"]],
	["Fahrzeuge", "car", ["vehicle", "move_forward", "move_back", "move_left", "move_right", "handbrake", "horn"]],
	["Menüs und Informationen", "phone", ["phone", "map", "inventory", "pause"]],
	["Zeit und Spielstand", "clock", ["time_faster", "time_slower", "quick_save", "quick_load"]],
	["Kamera", "camera", ["camera_toggle", "screenshot"]],
	["Maus", "mouse", ["primary", "secondary"]],
]
## Abweichende Beschriftung im Fahrzeugkontext.
const VEHICLE_LABELS := {"move_forward": "Gas geben", "move_back": "Bremsen / Rückwärts", "move_left": "Links lenken", "move_right": "Rechts lenken"}


func on_open() -> void:
	setup_frame("Steuerung", "Tastatur und Maus. Alle Tasten lassen sich in den Einstellungen neu belegen.", "Hauptmenü", "keyboard")
	var rebind := UIKit.button("Tasten neu belegen", "edit", "AccentButton")
	rebind.pressed.connect(func() -> void:
		if host and host.has_method("open_screen"):
			host.open_screen("settings", "controls"))
	header_actions.add_child(rebind)
	var grid := GridContainer.new()
	grid.columns = 3
	grid.add_theme_constant_override("h_separation", 18)
	grid.add_theme_constant_override("v_separation", 18)
	for g in GROUPS:
		var card := UIKit.card()
		card.size_flags_horizontal = SIZE_EXPAND_FILL
		var v := UIKit.vbox(10)
		var h := UIKit.hbox(10)
		h.add_child(UIKit.icon_badge(g[1], UITheme.ACCENT, 40, 0.5))
		h.add_child(UIKit.label(g[0], "TitleLabel"))
		v.add_child(h)
		v.add_child(UIKit.separator())
		for a in g[2]:
			var r := UIKit.hbox(10)
			var lbl: String = Settings.ACTION_LABELS.get(a, a)
			if g[1] == "car":
				lbl = VEHICLE_LABELS.get(a, lbl)
			var l := UIKit.label(lbl)
			l.add_theme_color_override("font_color", UITheme.TEXT_MUTED)
			l.size_flags_horizontal = SIZE_EXPAND_FILL
			r.add_child(l)
			r.add_child(UIKit.action_keycap(a))
			v.add_child(r)
		if g[1] == "camera":
			var r2 := UIKit.hbox(10)
			var l2 := UIKit.label("Umsehen")
			l2.add_theme_color_override("font_color", UITheme.TEXT_MUTED)
			l2.size_flags_horizontal = SIZE_EXPAND_FILL
			r2.add_child(l2)
			r2.add_child(UIKit.keycap("Maus bewegen"))
			v.add_child(r2)
		card.add_child(v)
		grid.add_child(card)
	var v2 := UIKit.vbox(18)
	v2.add_child(grid)
	var tip := UIKit.card()
	var th := UIKit.hbox(14)
	th.add_child(UIKit.icon_rect("info", 22, UITheme.ACCENT))
	var tl := UIKit.label("Interaktionen werden im Spiel angezeigt, sobald du dich Geschäften, Personen, Fahrzeugen oder Türen näherst. Die Taste zum Interagieren steht immer im Hinweis.", "SubtitleLabel", true)
	tl.size_flags_horizontal = SIZE_EXPAND_FILL
	th.add_child(tl)
	tip.add_child(th)
	v2.add_child(tip)
	content.add_child(UIKit.scroll(v2))
