class_name WorldState
extends RefCounted
## Gesamter Simulationszustand eines Spielstands (ohne Darstellung).
## Laeuft vollstaendig ohne Szenenbaum, damit Tests und Zeitraffer
## (Schlafen, Gefaengnis) dieselbe Logik verwenden wie das laufende Spiel.
##
## Systeme sind RefCounted-Objekte mit folgender Schnittstelle (alle optional
## ausser setup):
##   setup(world)                     - nach Erzeugung einer neuen Welt
##   step(world, minutes: float)      - jeder Simulationsschritt (STEP_MINUTES)
##   on_hour(world, day: int, hour: int)
##   on_day(world, day: int)
##   to_save() -> Dictionary / from_save(world, d: Dictionary)
##   post_load(world)                 - nach dem Laden aller Systeme
## Die Reihenfolge in SYSTEM_ORDER ist zugleich Setup- und Tick-Reihenfolge.

const SAVE_VERSION := 1
const STEP_MINUTES := 5.0
const START_MINUTES := 7.0 * 60.0   # Tag 0, 07:00 Uhr
const WEEKDAYS := ["Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"]
const WEEKDAYS_SHORT := ["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"]

## name -> Skriptpfad. Fehlende Skripte werden uebersprungen (mit Warnung),
## damit Teilsysteme unabhaengig entwickelt werden koennen.
const SYSTEM_ORDER := [
	["government", "res://scripts/politics/government.gd"],
	["economy", "res://scripts/economy/economy.gd"],
	["housing", "res://scripts/housing/housing_market.gd"],
	["jobs", "res://scripts/jobs/job_market.gd"],
	["education", "res://scripts/jobs/education.gd"],
	["population", "res://scripts/npc/population.gd"],
	["crime", "res://scripts/crime/crime_system.gd"],
	["politics", "res://scripts/politics/politics.gd"],
	["player", "res://scripts/life/player_life.gd"],
	["goals", "res://scripts/life/goals.gd"],
]

var seed: int = 0
var rules: GameRules
var city: CityModel
var ledger: Ledger
var time_minutes: float = START_MINUTES
var play_time_seconds: float = 0.0   # echte Spielzeit
var character: Dictionary = {}       # Charaktereditor-Daten des Spielers
var start_config: Dictionary = {}

# Systeme (dynamisch geladen, siehe SYSTEM_ORDER)
var government
var economy
var housing
var jobs
var education
var population
var crime
var politics
var player
var goals

var systems: Array = []              # [[name, obj], ...] in Tick-Reihenfolge
var news: Array = []                 # {day, hour, category, text}
var events: Array = []               # Ereignisse fuer Tests/Headless
## true waehrend Aufbau/Laden im Hintergrund-Thread: keine EventBus-Signale.
var silent := false
var load_warnings: Array = []
var _acc := 0.0
var _last_hour := -1
var _last_day := -1

const MAX_NEWS := 200


# ============================================================ Erzeugung

## config: {seed, mode, rules: {}, character: {}, start: {...}}
static func create_new(config: Dictionary) -> WorldState:
	var w := WorldState.new()
	w.silent = true
	w.seed = int(config.get("seed", randi()))
	w.rules = GameRules.make(config.get("mode", "life"), config.get("rules", {}))
	w.character = config.get("character", {}).duplicate(true)
	w.start_config = config.get("start", {}).duplicate(true)
	w.time_minutes = START_MINUTES
	w.city = CityGenerator.generate(w.seed, {"size": w.rules.get_rule("city_size")})
	w.ledger = Ledger.new()
	w._instantiate_systems()
	for s in w.systems:
		if s[1].has_method("setup"):
			s[1].setup(w)
	w._last_hour = w.get_hour()
	w._last_day = w.get_day()
	return w


func _instantiate_systems() -> void:
	systems.clear()
	for entry in SYSTEM_ORDER:
		var name: String = entry[0]
		var path: String = entry[1]
		if not ResourceLoader.exists(path):
			_warn("System '%s' fehlt (%s) - wird übersprungen" % [name, path])
			continue
		var script: Script = load(path)
		if script == null or not script.can_instantiate():
			_warn("System '%s' konnte nicht geladen werden" % name)
			continue
		var obj = script.new()
		set(name, obj)
		systems.append([name, obj])


func get_system(name: String):
	return get(name)


# ============================================================ Zeit

func get_day() -> int:
	return int(floor(time_minutes / 1440.0))


func get_hour() -> int:
	return int(floor(fmod(time_minutes, 1440.0) / 60.0))


func get_minute() -> int:
	return int(floor(fmod(time_minutes, 60.0)))


func get_hour_f() -> float:
	return fmod(time_minutes, 1440.0) / 60.0


func get_weekday() -> int:
	return get_day() % 7


func is_weekend() -> bool:
	return get_weekday() >= 5


func minutes_per_year() -> float:
	return rules.f("days_per_year") * 1440.0


## Alter in Jahren fuer einen Geburtszeitpunkt (Spielminuten, darf negativ sein).
func age_years(birth_minutes: float) -> float:
	return (time_minutes - birth_minutes) / minutes_per_year()


func birth_for_age(age: float) -> float:
	return time_minutes - age * minutes_per_year()


func time_string() -> String:
	return "%02d:%02d" % [get_hour(), get_minute()]


func date_string() -> String:
	return "%s, Tag %d" % [WEEKDAYS[get_weekday()], get_day() + 1]


## Laesst Zeit in festen Schritten vergehen. Wird vom laufenden Spiel jedes
## Frame mit kleinen Werten und beim Schlafen/Vorspulen mit grossen Werten
## aufgerufen. max_steps begrenzt die Arbeit pro Aufruf (Rest wird gemerkt).
func advance(minutes: float, max_steps := 100000) -> void:
	_acc += minutes
	var n := 0
	while _acc >= STEP_MINUTES and n < max_steps:
		_acc -= STEP_MINUTES
		n += 1
		_step(STEP_MINUTES)


func _step(dt: float) -> void:
	time_minutes += dt
	for s in systems:
		if s[1].has_method("step"):
			s[1].step(self, dt)
	var h := get_hour()
	if h != _last_hour:
		_last_hour = h
		var d := get_day()
		for s in systems:
			if s[1].has_method("on_hour"):
				s[1].on_hour(self, d, h)
		emit_event("hour_passed", [d, h])
		if d != _last_day:
			_last_day = d
			for s in systems:
				if s[1].has_method("on_day"):
					s[1].on_day(self, d)
			emit_event("day_passed", [d])


# ============================================================ Ereignisse

## Leitet Ereignisse an den EventBus weiter, falls das Spiel laeuft.
## In Tests werden sie in `events` gesammelt.
func emit_event(signal_name: String, args := []) -> void:
	events.append([signal_name, args])
	if events.size() > 500:
		events = events.slice(-250)
	if silent:
		return
	var bus := _event_bus()
	if bus and bus.has_signal(signal_name):
		bus.callv("emit_signal", [signal_name] + args)


func notify(text: String, kind := "info") -> void:
	emit_event("notify", [text, kind])


func post_news(category: String, text: String) -> void:
	var entry := {"day": get_day(), "hour": get_hour(), "category": category, "text": text}
	news.push_front(entry)
	if news.size() > MAX_NEWS:
		news.pop_back()
	emit_event("news_posted", [entry])


func _event_bus() -> Object:
	var loop := Engine.get_main_loop()
	if loop is SceneTree and loop.root.has_node("EventBus"):
		return loop.root.get_node("EventBus")
	return null


func _warn(msg: String) -> void:
	if silent:
		load_warnings.append(msg)
		return
	var loop := Engine.get_main_loop()
	if loop is SceneTree and loop.root.has_node("Log"):
		loop.root.get_node("Log").warn("world", msg)
	else:
		push_warning(msg)


# ============================================================ Speichern

func to_save() -> Dictionary:
	var sys := {}
	for s in systems:
		if s[1].has_method("to_save"):
			sys[s[0]] = s[1].to_save()
	return {"version": SAVE_VERSION, "seed": seed, "rules": rules.to_save(), "time": time_minutes,
		"play_time": play_time_seconds, "character": character, "start": start_config,
		"ledger": ledger.to_save(), "systems": sys, "news": news.slice(0, 60)}


## Stellt eine Welt aus Speicherdaten wieder her. Die Stadt wird aus dem Seed
## neu generiert (deterministisch); veraenderliche Daten kommen aus dem Spielstand.
static func from_save(d: Dictionary) -> WorldState:
	var w := WorldState.new()
	w.silent = true
	w.seed = int(d.seed)
	w.rules = GameRules.from_save(d.get("rules", {}))
	w.time_minutes = float(d.get("time", START_MINUTES))
	w.play_time_seconds = float(d.get("play_time", 0.0))
	w.character = d.get("character", {})
	w.start_config = d.get("start", {})
	w.news = d.get("news", [])
	w.city = CityGenerator.generate(w.seed, {"size": w.rules.get_rule("city_size")})
	w.ledger = Ledger.new()
	w.ledger.from_save(d.get("ledger", {}))
	w._instantiate_systems()
	var sys: Dictionary = d.get("systems", {})
	for s in w.systems:
		if sys.has(s[0]) and s[1].has_method("from_save"):
			s[1].from_save(w, sys[s[0]])
		elif s[1].has_method("setup"):
			w._warn("Keine Speicherdaten für '%s' - System wird neu initialisiert" % s[0])
			s[1].setup(w)
	for s in w.systems:
		if s[1].has_method("post_load"):
			s[1].post_load(w)
	w._last_hour = w.get_hour()
	w._last_day = w.get_day()
	return w
