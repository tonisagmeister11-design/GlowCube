class_name UIRulesSummary
extends RefCounted
## Transparente Regeluebersicht aus GameRules.describe() (Menue, Pause,
## Telefon). Jede Regel als Zeile mit Icon, Bezeichnung und Wert.

const ICONS := {
	"start_money": "money", "days_per_year": "calendar", "aging": "cake", "needs_decay": "hunger",
	"needs_can_harm": "health", "price_level": "tag", "wage_level": "briefcase", "rent_required": "home",
	"crime_rate": "mask", "police_strictness": "police", "death": "heart", "illness": "pharmacy",
	"lifespan": "hourglass", "population": "users", "city_size": "city",
}


## columns: Anzahl Spalten; compact = kleinere Zeilen (Telefon).
static func build(rules: GameRules, columns := 2, compact := false, skip: Array = []) -> GridContainer:
	var grid := GridContainer.new()
	grid.columns = columns
	grid.add_theme_constant_override("h_separation", 12)
	grid.add_theme_constant_override("v_separation", 6 if compact else 8)
	if rules == null:
		return grid
	for r in rules.describe():
		if r.key in skip:
			continue
		var row := PanelContainer.new()
		row.add_theme_stylebox_override("panel", UITheme.box(Color(1, 1, 1, 0.035), UITheme.RADIUS_M,
			Color(0, 0, 0, 0), 0, 12 if compact else 16, 7 if compact else 10))
		row.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var h := UIKit.hbox(10)
		h.add_child(UIKit.icon_rect(ICONS.get(r.key, "info"), 18 if compact else 20, UITheme.ACCENT))
		var l := UIKit.label(str(r.label), "CaptionLabel" if compact else "", true)
		if not compact:
			l.add_theme_color_override("font_color", UITheme.TEXT_MUTED)
			l.add_theme_font_size_override("font_size", 17)
		l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		h.add_child(l)
		var v := UIKit.label(str(r.value), "ValueLabel")
		v.add_theme_font_size_override("font_size", 15 if compact else 17)
		v.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
		h.add_child(v)
		row.add_child(h)
		grid.add_child(row)
	return grid


## Wenige Kernaussagen eines Modus (fuer Moduskarten).
static func highlights(mode: String) -> Array:
	var r := GameRules.make(mode)
	var out := []
	out.append(["money", "Startgeld %s" % UIKit.money(r.f("start_money"), 0)])
	out.append(["calendar", "%d Spieltage pro Lebensjahr" % int(r.f("days_per_year"))])
	out.append(["hunger", "Bedürfnisse sinken mit %d %%" % roundi(r.f("needs_decay") * 100.0)])
	out.append(["heart", "Tod: %s" % GameRules.DEATH_LABELS.get(r.get_rule("death"), "")])
	if mode == "sandbox":
		out = [["sliders", "Alle Regeln frei einstellbar"], ["money", "Startgeld frei wählbar"],
			["mask", "Kriminalität und Polizei regelbar"], ["heart", "Tod und Krankheit abschaltbar"]]
	return out
