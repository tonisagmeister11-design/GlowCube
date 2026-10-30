class_name GameRules
extends RefCounted
## Regeln eines Spielstands. Alle drei Spielmodi nutzen dieselben Systeme;
## sie unterscheiden sich nur in diesen Parametern. Der Sandbox-Modus erlaubt,
## jeden Wert frei einzustellen.

const MODES := {
	"life": {"label": "Lebensmodus", "desc": "Das volle Leben: Alterung, Bedürfnisse, Rechnungen, Krankheit und Tod."},
	"sandbox": {"label": "Sandbox", "desc": "Alle Regeln frei einstellbar – Startgeld, Alterung, Wirtschaft, Kriminalität und Tod."},
	"creative": {"label": "Kreativ", "desc": "Testmodus: viel Geld, Fliegen, alles direkt bekommen, kein Tod, keine Rechnungen. Zum Ausprobieren aller Funktionen."},
	"free": {"label": "Freier Modus", "desc": "Entspannt spielen: langsame Bedürfnisse, kein Tod durch Vernachlässigung, großzügiger Start."},
}

## Definition aller Regeln: key -> {label, type, min, max, step, options}
const SCHEMA := {
	"start_money":        {"label": "Startgeld (Erwachsene)", "type": "float", "min": 0.0, "max": 1000000.0, "step": 100.0},
	"days_per_year":      {"label": "Spieltage pro Lebensjahr", "type": "int", "min": 1, "max": 60, "step": 1},
	"aging":              {"label": "Alterung aktiv", "type": "bool"},
	"needs_decay":        {"label": "Bedürfnisverlust", "type": "float", "min": 0.0, "max": 3.0, "step": 0.1},
	"needs_can_harm":     {"label": "Vernachlässigung schadet der Gesundheit", "type": "bool"},
	"price_level":        {"label": "Preisniveau", "type": "float", "min": 0.5, "max": 2.0, "step": 0.05},
	"wage_level":         {"label": "Lohnniveau", "type": "float", "min": 0.5, "max": 3.0, "step": 0.05},
	"rent_required":      {"label": "Miete muss bezahlt werden", "type": "bool"},
	"crime_rate":         {"label": "Kriminalitätsrate (NPCs)", "type": "float", "min": 0.0, "max": 3.0, "step": 0.1},
	"police_strictness":  {"label": "Polizeihärte", "type": "float", "min": 0.0, "max": 2.0, "step": 0.1},
	"death":              {"label": "Tod", "type": "enum", "options": ["permanent", "new_character", "continue_as_heir", "disabled"]},
	"illness":            {"label": "Krankheiten", "type": "bool"},
	"lifespan":           {"label": "Durchschnittliche Lebenserwartung (Jahre)", "type": "int", "min": 50, "max": 120, "step": 1},
	"population":         {"label": "Einwohner (simuliert)", "type": "int", "min": 300, "max": 6000, "step": 100},
	"city_size":          {"label": "Stadtgröße", "type": "enum", "options": ["small", "medium", "large"]},
}

const PRESETS := {
	"life": {"start_money": 1500.0, "days_per_year": 3, "aging": true, "needs_decay": 1.0, "needs_can_harm": true,
		"price_level": 1.0, "wage_level": 1.0, "rent_required": true, "crime_rate": 1.0, "police_strictness": 1.0,
		"death": "new_character", "illness": true, "lifespan": 82, "population": 2400, "city_size": "medium"},
	"sandbox": {"start_money": 5000.0, "days_per_year": 3, "aging": true, "needs_decay": 1.0, "needs_can_harm": true,
		"price_level": 1.0, "wage_level": 1.0, "rent_required": true, "crime_rate": 1.0, "police_strictness": 1.0,
		"death": "new_character", "illness": true, "lifespan": 82, "population": 2400, "city_size": "medium"},
	"creative": {"start_money": 1000000.0, "days_per_year": 6, "aging": false, "needs_decay": 0.0, "needs_can_harm": false,
		"price_level": 1.0, "wage_level": 1.0, "rent_required": false, "crime_rate": 0.3, "police_strictness": 0.0,
		"death": "disabled", "illness": false, "lifespan": 95, "population": 2000, "city_size": "medium"},
	"free": {"start_money": 25000.0, "days_per_year": 6, "aging": true, "needs_decay": 0.4, "needs_can_harm": false,
		"price_level": 0.8, "wage_level": 1.3, "rent_required": true, "crime_rate": 0.6, "police_strictness": 0.6,
		"death": "disabled", "illness": false, "lifespan": 95, "population": 2000, "city_size": "medium"},
}

const DEATH_LABELS := {
	"permanent": "Endgültig (Spielstand endet)",
	"new_character": "Neue Figur beginnen",
	"continue_as_heir": "Als Erbe weiterspielen (Kind/Haushalt)",
	"disabled": "Kein Tod",
}

var mode := "life"
var values: Dictionary = {}


static func make(mode_name: String, overrides := {}) -> GameRules:
	var r := GameRules.new()
	r.mode = mode_name if PRESETS.has(mode_name) else "life"
	r.values = PRESETS[r.mode].duplicate()
	if r.mode == "sandbox":
		for k in overrides:
			if SCHEMA.has(k):
				r.values[k] = _clamp(k, overrides[k])
	else:
		# Stadtgroesse und Bevoelkerung sind in allen Modi waehlbar
		for k in ["city_size", "population"]:
			if overrides.has(k):
				r.values[k] = _clamp(k, overrides[k])
	return r


static func _clamp(k: String, v):
	var s: Dictionary = SCHEMA[k]
	match s.type:
		"float": return clampf(float(v), s.min, s.max)
		"int": return clampi(int(v), s.min, s.max)
		"bool": return bool(v)
		"enum": return v if v in s.options else s.options[0]
	return v


func get_rule(k: String):
	return values.get(k, PRESETS["life"].get(k))


func f(k: String) -> float:
	return float(get_rule(k))


func b(k: String) -> bool:
	return bool(get_rule(k))


## Menschlich lesbare Regelliste (fuer transparente Anzeige im Menue).
func describe() -> Array:
	var out := []
	for k in SCHEMA:
		var s: Dictionary = SCHEMA[k]
		var v = values.get(k)
		var txt := str(v)
		match s.type:
			"bool": txt = "Ja" if v else "Nein"
			"float": txt = ("%d %%" % roundi(float(v) * 100.0)) if k in ["needs_decay", "price_level", "wage_level", "crime_rate", "police_strictness"] else ("%.0f €" % float(v))
			"enum": txt = DEATH_LABELS.get(v, {"small": "Klein", "medium": "Mittel", "large": "Groß"}.get(v, str(v)))
		out.append({"key": k, "label": s.label, "value": txt})
	return out


func to_save() -> Dictionary:
	return {"mode": mode, "values": values}


static func from_save(d: Dictionary) -> GameRules:
	var r := GameRules.make(d.get("mode", "life"))
	for k in d.get("values", {}):
		if SCHEMA.has(k):
			r.values[k] = _clamp(k, d.values[k])
	return r
