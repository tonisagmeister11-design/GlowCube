extends RefCounted
## Gemeinsame Testwelten fuer die Simulationstests (werden einmal pro Lauf
## erzeugt, damit nicht jede Testdatei eine Stadt generiert).

static var _medium: WorldState = null
static var _created_ms := 0


## Mittlere Stadt, 2400 Einwohner, Spieler 24 Jahre (automatische Startsituation).
static func medium() -> WorldState:
	if _medium == null:
		var t0 := Time.get_ticks_msec()
		_medium = WorldState.create_new({"seed": 777, "mode": "life",
			"character": {"name": "Robin Test", "age": 24.0, "body": "female"}, "start": {"situation": "auto"}})
		_created_ms = Time.get_ticks_msec() - t0
	return _medium


static func created_ms() -> int:
	return _created_ms


## Kleine Welt mit eigener Konfiguration (fuer Startsituationen/Regeln).
static func small(character: Dictionary, start: Dictionary, mode := "life", rules := {}) -> WorldState:
	var r := {"city_size": "small", "population": 500}
	r.merge(rules, true)
	return WorldState.create_new({"seed": 99, "mode": mode, "rules": r, "character": character, "start": start})


## Spult bis zur naechsten gegebenen Uhrzeit (Stunde) vor.
static func advance_to_hour(w: WorldState, hour: float) -> void:
	var now := w.get_hour_f()
	var d := hour - now
	if d <= 0.0:
		d += 24.0
	w.advance(d * 60.0)
