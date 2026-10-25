class_name PlayerLife
extends RefCounted
## Das Leben der Spielfigur: Beduerfnisse, Alter, Faehigkeiten, Inventar,
## Wohnen, Arbeit, Krankheit, Tod und Erbe.
##
## Aktionen geben {ok, reason, minutes?, ...} zurueck. Zeitaufwendige Aktionen
## (schlafen, arbeiten, lernen, ausruhen) setzen `activity` mit Endzeit; ihre
## Wirkung entsteht waehrend die Zeit vergeht (world.advance / Game.skip_time).
## `minutes` im Ergebnis ist die Dauer, die die UI vorspulen kann.

const PLAYER_ID := 0

var name := "Alex Berger"
var body := "female"
var birth := 0.0
var appearance: Dictionary = {}
var needs: Dictionary = Needs.full()
var mood := 70.0
var skills: Dictionary = Skills.empty()
var education := "none"
var account := -1
var household := -1             # NPC-Haushalt (Eltern) oder -1
var home := -1                  # Property-ID
var inventory: Dictionary = {}  # good_id -> Menge
var vehicles: Array = []        # [{good, name, bought_day}]
var licenses: Dictionary = {}
var job: Dictionary = {}
var relationships: Dictionary = {}
var partner := -1
var criminal_record := 0
var wanted := 0
var gang := -1
var police_rank := -1
var political_office := ""
var alive := true
var cause_of_death := ""
var stage := "adult"            # aktuelle Lebensphase (siehe life_stage())
var illness: Dictionary = {}    # {name, severity 0..1, since}
var activity: Dictionary = {}   # {name, start, until, data}
var generation := 1
var adult_day := -1
var truancy := 0
var start_situation := ""
var _age_cache := 25.0
var _last_age_int := -1
var rng := RandomNumberGenerator.new()


# ============================================================ Alter

func age(world) -> float:
	return world.age_years(birth)


## Lebensphase fuer ein Alter (ohne Argument: aktuelles Alter).
func life_stage(age_years := -1.0) -> String:
	return LifeStages.stage(_age_cache if age_years < 0.0 else age_years)


## Faktor fuer die Laufgeschwindigkeit (Spielersteuerung).
func movement_speed_factor() -> float:
	var f := LifeStages.speed_factor(_age_cache)
	if float(needs.energy) < 15.0:
		f *= 0.8
	if float(needs.health) < 30.0:
		f *= 0.85
	if not illness.is_empty():
		f *= 0.9
	return f


func money(world) -> float:
	return world.ledger.balance(account)


func is_in_school(world) -> bool:
	var a := age(world)
	return a >= 6.0 and a < 18.0 and education in ["none"]


# ============================================================ Aufbau

func setup(world) -> void:
	rng.seed = hash("player:%d" % world.seed)
	var ch: Dictionary = world.character
	var st: Dictionary = world.start_config
	_init_character(world, ch, st)


func _init_character(world, ch: Dictionary, st: Dictionary) -> void:
	name = str(ch.get("name", "Alex Berger"))
	body = str(ch.get("body", "female"))
	var a: float = clampf(float(ch.get("age", 24.0)), 0.5, 100.0)
	birth = world.birth_for_age(a)
	_age_cache = a
	_last_age_int = int(a)
	appearance = ch.get("appearance", {}).duplicate(true)
	if appearance.is_empty():
		appearance = NpcAppearance.generate(rng, body, a)
	needs = Needs.full()
	skills = Skills.empty()
	for k in skills:
		skills[k] = clampf(rng.randf_range(5.0, 20.0) * clampf(a / 20.0, 0.1, 1.0), 0.0, 100.0)
	education = "none"
	if a >= 16.0:
		education = "school"
	if a >= 21.0:
		education = "vocational"
		skills.driving = maxf(float(skills.driving), 45.0)
		licenses["car"] = true
	alive = true
	cause_of_death = ""
	stage = LifeStages.stage(a)
	if account < 0 or not world.ledger.has_account(account):
		account = world.ledger.open_account("player", PLAYER_ID, name, 0.0)
	var sit: String = str(st.get("situation", "auto"))
	if sit == "auto":
		sit = "with_parents" if a < 18.0 else ("shared_room" if a < 25.0 else "own_apartment")
	if a < 18.0 and sit != "with_parents":
		sit = "with_parents" if sit != "homeless" else "poor_parents"
	start_situation = sit
	var base: float = world.rules.f("start_money")
	var amount := base
	match sit:
		"with_parents", "poor_parents":
			amount = 15.0 + a * 3.0 if a < 18.0 else base * 0.5
			_join_parents(world, a, sit == "poor_parents")
		"own_apartment":
			_start_with_home(world, ["apartment"], 320.0)
		"shared_room":
			_start_with_home(world, ["room"], 160.0)
		"homeless":
			amount = minf(base, 20.0)
		"wealthy":
			amount = maxf(base * 40.0, 150000.0)
			_start_wealthy(world)
	if st.has("money"):
		amount = float(st.money)
	world.ledger.mint(account, amount, "Startgeld")
	if a >= 6.0 and a < 18.0 and world.education:
		world.education.enroll(world, "player", PLAYER_ID, "school")
	if a >= 18.0:
		adult_day = world.get_day()


func _join_parents(world, a: float, poor: bool) -> void:
	var pop = world.population
	if pop == null:
		return
	var hid: int = pop.find_parent_household(world, a, poor)
	if hid < 0:
		return
	household = hid
	var h: Dictionary = pop.households[hid]
	h.player_member = true
	home = int(h.home)
	for m in h.members:
		var rel := 75 if pop.age_of(world, m) > a + 15.0 else 55
		relationships[m] = rel
		pop.persons[m].relationships[PLAYER_ID] = rel
	var last: String = pop.persons[h.members[0]].last
	if not name.contains(" "):
		name = "%s %s" % [name, last]


func _start_with_home(world, kinds: Array, max_rent: float) -> void:
	var housing = world.housing
	if housing == null:
		return
	var best := -1
	var bs := -1e9
	for pid in housing.vacant_rentals():
		var p: Dictionary = housing.properties[pid]
		if not p.kind in kinds or float(p.rent_per_week) > max_rent:
			continue
		var sc: float = float(p.quality) - float(p.rent_per_week) / max_rent
		if sc > bs:
			bs = sc
			best = pid
	if best < 0:
		for pid in housing.vacant_rentals():
			if housing.properties[pid].kind in ["room", "apartment"]:
				best = pid
				break
	if best >= 0:
		housing.assign("player", PLAYER_ID, best)
		home = best


func _start_wealthy(world) -> void:
	var housing = world.housing
	if housing == null:
		return
	var best := -1
	var bw := -1.0
	for pid in housing.vacant_rentals():
		var p: Dictionary = housing.properties[pid]
		if p.kind in ["house", "villa"] and float(p.wealth) + float(p.quality) * 0.2 > bw:
			bw = float(p.wealth) + float(p.quality) * 0.2
			best = pid
	if best >= 0:
		housing.assign("player", PLAYER_ID, best, true)
		home = best


# ============================================================ Takt

func step(world, dt: float) -> void:
	if not alive:
		return
	_age_cache = age(world)
	var f: float = world.rules.f("needs_decay")
	var h := dt / 60.0
	var act: String = activity.get("name", "")
	var sleeping := act == "sleep"
	var rates: Dictionary = Needs.ASLEEP if sleeping else Needs.AWAKE
	# Haushalt versorgt Kinder und Jugendliche mit Essen, Trinken und Hygiene,
	# solange Vorraete da sind. Erwachsene muessen selbst sorgen.
	var cared := household >= 0 and _age_cache < 18.0
	for k in rates:
		var r: float = float(rates[k])
		if r > 0.0:
			r *= f
			if cared and k in ["hunger", "thirst", "hygiene"]:
				r *= 0.15 if _age_cache < 12.0 else 0.4
			elif cared and k == "energy" and _age_cache < 16.0:
				r *= 0.5    # Eltern achten auf Schlafenszeiten
		elif k == "energy" and sleeping:
			r *= float(activity.get("data", {}).get("quality", 1.0))
		needs[k] = clampf(float(needs[k]) - r * h, 0.0, 100.0)
	if ACT_EFFECTS.has(act):
		var eff: Dictionary = ACT_EFFECTS[act]
		for k in eff:
			needs[k] = clampf(float(needs[k]) + float(eff[k]) * h, 0.0, 100.0)
	needs.energy = minf(float(needs.energy), LifeStages.energy_cap(_age_cache))
	# Gesundheit: Vernachlaessigung schadet langsam und nur im echten Mangel.
	# Von voller Gesundheit bis zum Tod vergehen so mehrere Spieltage, und der
	# Spieler wird vorher mehrfach gewarnt.
	var harm := 0.0
	if world.rules.b("needs_can_harm"):
		if float(needs.hunger) <= 0.5:
			harm += 0.5
		if float(needs.thirst) <= 0.5:
			harm += 0.7
		if float(needs.energy) <= 0.5:
			harm += 0.25
		if float(needs.hygiene) <= 0.5:
			harm += 0.1
	if not illness.is_empty():
		harm += float(illness_info().harm) * float(illness.severity)
	_tick_illness(world, h)
	if harm > 0.0:
		needs.health = maxf(0.0, float(needs.health) - harm * h)
	if harm <= 0.0 and float(needs.hunger) > 35.0 and float(needs.thirst) > 35.0:
		needs.health = minf(100.0, float(needs.health) + 1.2 * h)
	_warn_needs(world)
	mood = Needs.mood(needs)
	if not activity.is_empty() and world.time_minutes >= float(activity.until) - 0.01:
		_finish_activity(world)
	# Kinder und Jugendliche im Haushalt der Familie koennen nicht verwahrlosen:
	# Die Eltern kochen, kaufen ein und achten auf Schlafenszeiten. Das Kindspiel
	# soll vom Leben erzaehlen, nicht vom Verhungern.
	if cared:
		for k in ["hunger", "thirst", "hygiene", "energy"]:
			needs[k] = maxf(float(needs[k]), 35.0)
		needs.health = maxf(float(needs.health), 45.0)
	if float(needs.health) <= 0.0:
		var deadly: bool = illness.is_empty() or bool(illness_info().deadly)
		if world.rules.get_rule("death") == "disabled" or not deadly:
			# Leichte Krankheiten bringen einen nicht um: Restgesundheit bleibt,
			# der Spieler wird aber deutlich zum Arzt geschickt.
			needs.health = 1.0
			if world.time_minutes - _last_warn >= 120.0:
				_last_warn = world.time_minutes
				world.notify("Dir geht es sehr schlecht. Geh zum Arzt oder nimm Medizin.", "danger")
		else:
			var c := "Krankheit (%s)" % illness.name if not illness.is_empty() else "Vernachlässigung"
			die(world, c)


## Warnt hoechstens alle zwei Spielstunden vor dem naechsten kritischen
## Beduerfnis und vor sinkender Gesundheit.
func _warn_needs(world) -> void:
	if world.time_minutes - _last_warn < 120.0:
		return
	var urgent: Dictionary = Needs.most_urgent(needs)
	var v: float = float(urgent.get("value", 100.0))
	var hp: float = float(needs.health)
	if hp < 35.0 and world.rules.b("needs_can_harm"):
		_last_warn = world.time_minutes
		world.notify("Deine Gesundheit sinkt (%d %%). %s" % [int(hp), Needs.advice(str(urgent.get("key", "")))], "danger")
	elif v < 12.0:
		_last_warn = world.time_minutes
		world.notify(Needs.advice(str(urgent.get("key", ""))), "warning")
	elif v < 25.0:
		_last_warn = world.time_minutes
		world.notify(Needs.advice(str(urgent.get("key", ""))), "info")


var _last_warn := -1000.0


## Krankheiten: harm = Gesundheitsverlust pro Stunde bei voller Schwere,
## heal = Rueckgang der Schwere pro Stunde (Selbstheilung), deadly = kann
## toedlich enden, wenn sie unbehandelt bleibt. Leichte Krankheiten klingen
## von selbst in ein bis zwei Spieltagen ab und sind nie toedlich.
const ILLNESSES := {
	"Erkältung":          {"harm": 0.22, "heal": 0.030, "deadly": false, "max": 0.7},
	"Migräne":            {"harm": 0.12, "heal": 0.055, "deadly": false, "max": 0.6},
	"Magen-Darm-Infekt":  {"harm": 0.30, "heal": 0.035, "deadly": false, "max": 0.8},
	"Grippe":             {"harm": 0.45, "heal": 0.022, "deadly": false, "max": 1.0},
	"Lungenentzündung":   {"harm": 0.85, "heal": 0.012, "deadly": true,  "max": 1.0},
}


func illness_info() -> Dictionary:
	return ILLNESSES.get(str(illness.get("name", "")), {"harm": 0.3, "heal": 0.03, "deadly": false, "max": 1.0})


## Lässt eine Krankheit abklingen. Medizin und Arztbesuch beschleunigen das.
func _tick_illness(world, h: float) -> void:
	if illness.is_empty():
		return
	var info := illness_info()
	var heal: float = float(info.heal) * (1.6 if float(needs.energy) > 60.0 else 1.0)
	illness.severity = float(illness.severity) - heal * h
	if float(illness.severity) <= 0.0:
		var was := str(illness.name)
		illness = {}
		world.notify("Du bist wieder gesund (%s überstanden)." % was, "success")


const ACT_EFFECTS := {
	"work": {"energy": -1.5, "social": 2.5, "fun": -1.0},
	"study": {"energy": -1.5, "fun": -1.0, "social": 1.5},
	"rest": {"energy": 6.0, "fun": 2.0},
	"socialize": {"social": 18.0, "fun": 8.0},
	"doctor": {},
}


func on_hour(world, day: int, hour: int) -> void:
	if not alive:
		return
	world.emit_event("player_needs_changed", [])
	var a := age(world)
	# Eltern versorgen minderjaehrige Kinder (Essen aus dem Haushaltsvorrat, Wasser)
	if a < 18.0 and household >= 0 and world.population and world.population.households.has(household):
		var hh: Dictionary = world.population.households[household]
		if float(needs.hunger) < 25.0 and float(hh.pantry) >= 1.0:
			hh.pantry = float(hh.pantry) - 1.0
			needs.hunger = minf(100.0, float(needs.hunger) + 45.0)
		if float(needs.thirst) < 25.0:
			needs.thirst = minf(100.0, float(needs.thirst) + 40.0)
	var ai := int(a)
	if ai != _last_age_int:
		_last_age_int = ai
		world.emit_event("player_aged", [ai])
		if ai == 18:
			_turn_adult(world)
	var stg := LifeStages.stage(a)
	if stg != stage:
		stage = stg
		world.emit_event("player_life_stage_changed", [stg])
		world.notify("Neue Lebensphase: %s" % LifeStages.label(stg), "info")
	if world.rules.b("illness") and illness.is_empty():
		var risk := 0.0012 * (1.0 + (100.0 - float(needs.hygiene)) / 60.0 + maxf(0.0, a - 55.0) / 25.0)
		if rng.randf() < risk:
			# Leichte Krankheiten sind haeufig, schwere selten und nur bei
			# geschwaechter Gesundheit oder hohem Alter.
			var pool := ["Erkältung", "Erkältung", "Migräne", "Magen-Darm-Infekt", "Grippe"]
			if float(needs.health) < 55.0 or a > 65.0:
				pool.append("Lungenentzündung")
			var nm: String = pool[rng.randi() % pool.size()]
			var mx: float = float(ILLNESSES[nm].max)
			illness = {"name": nm, "severity": rng.randf_range(0.25, mx), "since": day}
			world.notify("Du bist krank: %s. Ruhe, Medizin aus der Apotheke oder ein Arztbesuch helfen." % nm, "warning")


func on_day(world, day: int) -> void:
	if not alive:
		return
	var a := age(world)
	if not world.rules.b("aging"):
		birth += 1440.0
	var L: Ledger = world.ledger
	var pop = world.population
	# Taschengeld / Kostgeld im Elternhaus
	if household >= 0 and pop and pop.households.has(household):
		var hacc: int = int(pop.households[household].account)
		if a >= 6.0 and a < 18.0 and day % 7 == 0:
			L.transfer_partial(hacc, account, snappedf(a * 1.5, 0.5), "Taschengeld")
		elif a >= 18.0 and adult_day >= 0 and day - adult_day > 7 and day % 7 == 0:
			if not job.is_empty():
				if not L.transfer(account, hacc, 40.0, "Kostgeld an die Eltern"):
					world.notify("Deine Eltern erwarten Kostgeld (40 € pro Woche).", "warning")
			elif day - adult_day > 14:
				world.notify("Deine Eltern drängen: Such dir Arbeit und eine eigene Wohnung.", "warning")
	# Schulpflicht
	if a >= 6.0 and a < 16.0 and day % 7 < 5 and world.education:
		var e: Dictionary = world.education.enrollment_of("player", PLAYER_ID)
		var hrs := float(e.get("hours", 0.0))
		if hrs <= float(e.get("_last_hours", -1.0)) + 0.01 and day % 7 != 0:
			truancy += 1
			if truancy % 3 == 0:
				world.notify("Du hast die Schule geschwänzt.", "warning")
		e["_last_hours"] = hrs
	if a >= 16.0 and education == "none" and world.education:
		var e2: Dictionary = world.education.enrollment_of("player", PLAYER_ID)
		var prog := float(e2.get("hours", 0.0)) / maxf(1.0, float(e2.get("required", 1.0)))
		if prog >= 0.4 or a >= 18.0:
			world.education.graduate_school(world, "player", PLAYER_ID)
			if education == "school":
				world.notify("Schulabschluss erreicht!", "success")
	# Tod durch Alter
	var rule: String = str(world.rules.get_rule("death"))
	if world.rules.b("aging") and rule != "disabled":
		var hz = LifeStages.hazard_per_year(a, world.rules.f("lifespan")) / world.rules.f("days_per_year")
		hz *= 1.0 + maxf(0.0, 60.0 - float(needs.health)) / 30.0
		if a > 30.0 and rng.randf() < hz:
			die(world, "Alter")


func _turn_adult(world) -> void:
	adult_day = world.get_day()
	world.notify("Du bist volljährig! Du kannst noch eine Weile bei deinen Eltern wohnen – such dir Arbeit und eine Wohnung.", "info")
	world.post_news("leben", "%s ist volljährig geworden." % name)


# ============================================================ Aktivitaeten

func _start(world, act: String, minutes: float, data := {}) -> Dictionary:
	if not alive:
		return {"ok": false, "reason": "Du lebst nicht mehr."}
	if not activity.is_empty():
		_finish_activity(world, true)
	activity = {"name": act, "start": world.time_minutes, "until": world.time_minutes + minutes, "data": data}
	return {"ok": true, "reason": "", "minutes": minutes, "until": world.time_minutes + minutes}


## Bricht die laufende Aktivitaet ab (Wirkung anteilig).
func stop_activity(world) -> void:
	if not activity.is_empty():
		_finish_activity(world, true)


func _finish_activity(world, aborted := false) -> void:
	var act: Dictionary = activity
	activity = {}
	var hours := (minf(world.time_minutes, float(act.until)) - float(act.start)) / 60.0
	match act.get("name", ""):
		"work":
			if world.jobs and hours > 0.05:
				var res: Dictionary = world.jobs.work(world, "player", PLAYER_ID, hours)
				if res.get("ok", false):
					world.notify("Schicht beendet: %s netto." % Economy.fmt_money(float(res.pay)), "money")
		"study":
			if world.education and hours > 0.05:
				world.education.attend(world, "player", PLAYER_ID, hours)
		"socialize":
			var pid: int = int(act.data.get("person", -1))
			if pid >= 0:
				change_relationship(world, pid, hours * 6.0)


func is_busy() -> bool:
	return not activity.is_empty()


# ============================================================ Aktionen

func _has_home_access(world) -> bool:
	return home >= 0


func eat(world, good_id: String) -> Dictionary:
	var g: Dictionary = GoodsCatalog.get_good(good_id)
	if g.is_empty():
		return {"ok": false, "reason": "Unbekanntes Produkt."}
	if float(g.needs.get("hunger", 0.0)) <= 0.0:
		return {"ok": false, "reason": "Das kann man nicht essen."}
	return _consume(world, good_id, 10.0)


func drink(world, good_id := "") -> Dictionary:
	if good_id == "" or good_id == "tap_water":
		if not _has_home_access(world):
			return {"ok": false, "reason": "Hier gibt es kein Leitungswasser. Kaufe dir etwas zu trinken."}
		needs.thirst = minf(100.0, float(needs.thirst) + 40.0)
		return {"ok": true, "reason": "", "minutes": 2.0}
	var g: Dictionary = GoodsCatalog.get_good(good_id)
	if g.is_empty() or float(g.needs.get("thirst", 0.0)) <= 0.0:
		return {"ok": false, "reason": "Das kann man nicht trinken."}
	return _consume(world, good_id, 3.0)


## Essen aus dem Vorrat des Elternhaushalts.
func eat_at_home(world) -> Dictionary:
	var pop = world.population
	if household < 0 or pop == null or not pop.households.has(household):
		for gid in inventory:
			if float(GoodsCatalog.GOODS[gid].needs.get("hunger", 0.0)) > 0.0:
				return eat(world, gid)
		return {"ok": false, "reason": "Du hast nichts zu essen im Haus. Kaufe Lebensmittel ein."}
	var h: Dictionary = pop.households[household]
	if float(h.pantry) < 1.0:
		return {"ok": false, "reason": "Der Kühlschrank ist leer."}
	h.pantry = float(h.pantry) - 1.0
	needs.hunger = minf(100.0, float(needs.hunger) + 45.0)
	needs.thirst = minf(100.0, float(needs.thirst) + 15.0)
	return {"ok": true, "reason": "", "minutes": 20.0}


func _consume(world, good_id: String, minutes: float) -> Dictionary:
	if int(inventory.get(good_id, 0)) <= 0:
		return {"ok": false, "reason": "Du hast kein „%s“." % GoodsCatalog.GOODS[good_id].name}
	var g: Dictionary = GoodsCatalog.GOODS[good_id]
	_apply(g.needs)
	inventory[good_id] = int(inventory[good_id]) - 1
	if int(inventory[good_id]) <= 0:
		inventory.erase(good_id)
	if g.category == "medicine" and not illness.is_empty():
		illness.severity = float(illness.severity) - 0.35
		if float(illness.severity) <= 0.0:
			illness = {}
			world.notify("Die Medizin hat geholfen – du bist wieder gesund.", "success")
	world.emit_event("player_needs_changed", [])
	return {"ok": true, "reason": "", "minutes": minutes}


func _apply(nd: Dictionary) -> void:
	for k in nd:
		if needs.has(k):
			needs[k] = clampf(float(needs[k]) + float(nd[k]), 0.0, 100.0)


## Beliebigen Gegenstand verwenden (Medizin, Drogerie, Essen ...).
func use_item(world, good_id: String) -> Dictionary:
	var g: Dictionary = GoodsCatalog.get_good(good_id)
	if g.is_empty():
		return {"ok": false, "reason": "Unbekannter Gegenstand."}
	if not g.consumable:
		return {"ok": false, "reason": "%s ist ein dauerhafter Gegenstand." % g.name}
	return _consume(world, good_id, 5.0)


func sleep(world, hours: float) -> Dictionary:
	hours = clampf(hours, 0.5, 14.0)
	var q := 1.0
	if home < 0:
		q = 0.6
	elif world.housing:
		var p: Dictionary = world.housing.get_property(home)
		q = 0.85 + 0.15 * float(p.get("quality", 0.5)) + (0.1 if inventory.has("bed") else 0.0)
	var r := _start(world, "sleep", hours * 60.0, {"quality": q})
	if r.ok and home < 0:
		r["reason"] = "Du schläfst draußen – unbequem und unsicher."
	return r


func rest(world, hours := 1.0) -> Dictionary:
	return _start(world, "rest", clampf(hours, 0.25, 6.0) * 60.0)


func shower(world, at_building := -1) -> Dictionary:
	var ok := home >= 0
	if at_building >= 0 and world.economy:
		var bid: int = world.economy.business_for_building(at_building)
		ok = ok or (bid >= 0 and world.economy.businesses[bid].type == "gym")
	if not ok:
		return {"ok": false, "reason": "Du hast keine Dusche. Im Fitnessstudio oder in der Notunterkunft kannst du duschen."}
	needs.hygiene = 100.0
	return {"ok": true, "reason": "", "minutes": 15.0}


## Arbeitsschicht beginnen. Erlaubt ab 60 Minuten vor Schichtbeginn bis
## Schichtende an einem Arbeitstag; Verspaetung kostet Leistung.
func work_shift(world, at_building := -1) -> Dictionary:
	if not alive:
		return {"ok": false, "reason": "Du lebst nicht mehr."}
	var jobs = world.jobs
	if jobs == null:
		return {"ok": false, "reason": "Kein Arbeitsmarkt."}
	var pid: int = jobs.position_of("player", PLAYER_ID)
	if pid < 0:
		return {"ok": false, "reason": "Du hast keine Arbeitsstelle."}
	var p: Dictionary = jobs.positions[pid]
	if at_building >= 0 and at_building != int(p.building):
		return {"ok": false, "reason": "Das ist nicht dein Arbeitsplatz."}
	var now: float = world.time_minutes
	var day: int = world.get_day()
	for d in [day, day - 1]:
		if not jobs.is_workday(p, d):
			continue
		var s: float = jobs.shift_start_minutes(p, d)
		var e: float = s + float(p.shift_hours) * 60.0
		if now >= s - 60.0 and now < e - 15.0:
			if int(p.last_worked_day) == d:
				return {"ok": false, "reason": "Diese Schicht hast du bereits gearbeitet."}
			var begin := maxf(now, s)
			if now > s + 15.0:
				p.performance = maxf(0.0, float(p.performance) - 5.0)
				world.notify("Du kommst zu spät zur Arbeit.", "warning")
			var r := _start(world, "work", e - now)
			activity.start = begin
			r["shift_end"] = e
			return r
	return {"ok": false, "reason": "Jetzt ist keine Schicht. Deine Schicht: %s ab %02d:%02d Uhr." % [
		_days_text(p.days), int(p.shift_start), int(fmod(float(p.shift_start), 1.0) * 60.0)]}


func _days_text(days: Array) -> String:
	var parts := []
	for d in days:
		parts.append(WorldState.WEEKDAYS_SHORT[int(d)])
	return ", ".join(parts)


func study(world, hours := 2.0) -> Dictionary:
	if world.education == null:
		return {"ok": false, "reason": "Keine Bildungseinrichtung."}
	var e: Dictionary = world.education.enrollment_of("player", PLAYER_ID)
	var c: Dictionary = world.education.enrollments.get("player:0:course", {})
	if e.is_empty() and c.is_empty():
		return {"ok": false, "reason": "Du bist nirgends eingeschrieben."}
	return _start(world, "study", clampf(hours, 0.5, 8.0) * 60.0)


func enroll(world, program: String, field := "") -> Dictionary:
	if world.education == null:
		return {"ok": false, "reason": "Keine Bildungseinrichtung."}
	return world.education.enroll(world, "player", PLAYER_ID, program, field)


func buy(world, business_id: int, good_id: String, qty := 1) -> Dictionary:
	if not alive:
		return {"ok": false, "reason": "Du lebst nicht mehr."}
	var eco = world.economy
	var g: Dictionary = GoodsCatalog.get_good(good_id)
	if eco == null or g.is_empty():
		return {"ok": false, "reason": "Nicht verfügbar."}
	if g.vehicle and g.category == "vehicle" and age(world) < 18.0:
		return {"ok": false, "reason": "Fahrzeuge kannst du erst ab 18 kaufen."}
	if g.category == "bar" and age(world) < 16.0:
		return {"ok": false, "reason": "Alkohol erst ab 16 Jahren."}
	var res: Dictionary = eco.buy(world, account, business_id, good_id, qty)
	if not res.ok:
		return res
	if g.service:
		for i in qty:
			_apply(g.needs)
		if g.category == "service" and good_id.begins_with("gym"):
			Skills.gain(skills, "fitness", 2.0)
	elif g.vehicle:
		for i in qty:
			vehicles.append({"good": good_id, "name": g.name, "bought_day": world.get_day()})
		world.notify("Neues Fahrzeug: %s" % g.name, "success")
	else:
		inventory[good_id] = int(inventory.get(good_id, 0)) + qty
		if g.durable:
			_apply(g.needs)
	return res


func see_doctor(world, building_id := -1) -> Dictionary:
	var gov = world.government
	var fee := 20.0
	var dept := -1
	if gov:
		fee = gov.policy("doctor_fee")
		dept = gov.dept_accounts.get("health", gov.account)
	if building_id >= 0:
		var k: String = world.city.buildings[building_id].kind
		if not k in ["clinic", "hospital"]:
			return {"ok": false, "reason": "Hier gibt es keinen Arzt."}
		if k == "hospital" and gov:
			fee = gov.policy("hospital_fee") if float(needs.health) < 40.0 else fee
	if dept >= 0 and not world.ledger.transfer(account, dept, fee, "Arztbesuch"):
		return {"ok": false, "reason": "Nicht genug Geld für die Behandlung (%s)." % Economy.fmt_money(fee)}
	if gov:
		gov.record_revenue("fees", fee)
	var q: float = clampf(gov.modifier("health_quality"), 0.6, 1.4) if gov else 1.0
	var was = illness.get("name", "")
	illness = {}
	needs.health = minf(100.0, float(needs.health) + 25.0 * q)
	var msg := "Du wurdest behandelt." if was == "" else "Behandlung erfolgreich: %s ist auskuriert." % was
	world.notify(msg, "success")
	return {"ok": true, "reason": "", "minutes": 45.0, "cost": fee}


func socialize(world, person_id: int, minutes := 60.0) -> Dictionary:
	var pop = world.population
	if pop == null or not pop.persons.has(person_id):
		return {"ok": false, "reason": "Diese Person gibt es nicht."}
	return _start(world, "socialize", clampf(minutes, 10.0, 240.0), {"person": person_id})


func change_relationship(world, person_id: int, delta: float) -> void:
	var v := clampf(float(relationships.get(person_id, 0)) + delta, -100.0, 100.0)
	relationships[person_id] = int(v)
	var pop = world.population
	if pop and pop.persons.has(person_id):
		pop.persons[person_id].relationships[PLAYER_ID] = int(v)


## Feste Beziehung (ab Beziehungswert 70, beide volljaehrig).
func start_partnership(world, person_id: int) -> Dictionary:
	var pop = world.population
	if pop == null or not pop.persons.has(person_id):
		return {"ok": false, "reason": "Diese Person gibt es nicht."}
	var p: Dictionary = pop.persons[person_id]
	if age(world) < 18.0 or pop.age_of(world, person_id) < 18.0:
		return {"ok": false, "reason": "Beide müssen volljährig sein."}
	if int(p.partner) >= 0:
		return {"ok": false, "reason": "%s ist bereits vergeben." % p.first}
	if float(relationships.get(person_id, 0)) < 70.0:
		return {"ok": false, "reason": "Ihr kennt euch noch nicht gut genug (Beziehung mindestens 70)."}
	partner = person_id
	p.partner = -2   # -2 = Spielfigur
	world.notify("Du bist jetzt mit %s zusammen." % p.first, "success")
	return {"ok": true, "reason": ""}


## Gemeinsames Kind mit dem Partner (lebt im Haushalt des Partners).
func have_child(world) -> Dictionary:
	var pop = world.population
	if partner < 0 or pop == null or not pop.persons.has(partner):
		return {"ok": false, "reason": "Dafür brauchst du eine feste Beziehung."}
	var mum_age: float = age(world) if body == "female" else pop.age_of(world, partner)
	if mum_age > 45.0 or age(world) < 18.0:
		return {"ok": false, "reason": "Im Moment ist kein Nachwuchs möglich."}
	var pp: Dictionary = pop.persons[partner]
	var c: int = pop._child(world, int(pp.household), 0.01, pp.last, [partner])
	pop.persons[c].player_parent = true
	pop._init_schedule(world, pop.persons[c])
	relationships[c] = 90
	world.notify("Euer Kind %s ist geboren!" % pop.persons[c].first, "success")
	return {"ok": true, "reason": "", "child": c}


func apply_job(world, position_id: int) -> Dictionary:
	return world.jobs.apply(world, "player", PLAYER_ID, position_id) if world.jobs else {"ok": false, "reason": "Kein Arbeitsmarkt."}


func quit_job(world) -> Dictionary:
	return world.jobs.quit(world, "player", PLAYER_ID) if world.jobs else {"ok": false, "reason": "Kein Arbeitsmarkt."}


func rent_home(world, property_id: int) -> Dictionary:
	var r: Dictionary = world.housing.rent(world, "player", PLAYER_ID, account, property_id)
	if r.ok:
		_leave_parents(world)
		home = property_id
	return r


func buy_home(world, property_id: int, use_mortgage := false) -> Dictionary:
	var r: Dictionary = world.housing.buy(world, "player", PLAYER_ID, account, property_id, use_mortgage)
	if r.ok and world.housing.home_of("player", PLAYER_ID) == property_id:
		_leave_parents(world)
		home = property_id
	return r


## Auszug aus der aktuellen (Miet-)Wohnung.
func move_out(world) -> Dictionary:
	if household >= 0:
		_leave_parents(world)
		home = -1
		return {"ok": true, "reason": ""}
	var h: int = world.housing.home_of("player", PLAYER_ID)
	if h < 0:
		return {"ok": false, "reason": "Du hast keine Wohnung."}
	world.housing.move_out(world, h)
	home = -1
	return {"ok": true, "reason": ""}


func _leave_parents(world) -> void:
	if household >= 0 and world.population and world.population.households.has(household):
		world.population.households[household].player_member = false
	household = -1


## Woechentliches Nettoeinkommen (Schaetzung aus Stelle).
func income_week(world) -> float:
	var jobs = world.jobs
	if jobs == null:
		return 0.0
	var pid: int = jobs.position_of("player", PLAYER_ID)
	if pid < 0:
		return 0.0
	var p: Dictionary = jobs.positions[pid]
	var w: float = jobs.hourly_wage(world, p)
	var keep := 0.72
	if world.government:
		keep = 1.0 - world.government.income_tax_rate(w) - world.government.rate("pension_contribution")
	return w * float(p.shift_hours) * float(p.days.size()) * keep


# ============================================================ Tod und Erbe

func die(world, cause: String) -> void:
	if not alive:
		return
	if str(world.rules.get_rule("death")) == "disabled":
		needs.health = maxf(1.0, float(needs.health))
		return
	alive = false
	cause_of_death = cause
	activity = {}
	if world.jobs:
		world.jobs.release(world, "player", PLAYER_ID, "death")
	world.post_news("leben", "%s ist im Alter von %d Jahren gestorben (%s)." % [name, int(age(world)), cause])
	world.emit_event("player_died", [cause])


## Moegliche Erben: Partner, Kinder, Mitglieder des Elternhaushalts.
## -> [{id, name, age, relation}]
func heirs(world) -> Array:
	var out := []
	var pop = world.population
	if pop == null:
		return out
	var seen := {}
	if partner >= 0 and pop.persons.has(partner):
		out.append({"id": partner, "name": pop.person_name(partner), "age": pop.age_of(world, partner), "relation": "Partner/in"})
		seen[partner] = true
	for id in pop.persons:
		var p: Dictionary = pop.persons[id]
		if p.get("player_parent", false) and not seen.has(id):
			out.append({"id": id, "name": pop.person_name(id), "age": pop.age_of(world, id), "relation": "Kind"})
			seen[id] = true
	if household >= 0 and pop.households.has(household):
		for m in pop.households[household].members:
			if not seen.has(m):
				out.append({"id": m, "name": pop.person_name(m), "age": pop.age_of(world, m), "relation": "Familie"})
				seen[m] = true
	return out


func can_continue_as_heir(world) -> bool:
	return str(world.rules.get_rule("death")) == "continue_as_heir" and not heirs(world).is_empty()


## Nach dem Tod als Erbe weiterspielen (nur wenn die Regel es erlaubt und
## die Person ein echter Erbe ist).
func continue_as(world, person_id: int) -> Dictionary:
	if alive:
		return {"ok": false, "reason": "Die Spielfigur lebt noch."}
	if str(world.rules.get_rule("death")) != "continue_as_heir":
		return {"ok": false, "reason": "Die Regeln erlauben kein Weiterspielen als Erbe."}
	var ok := false
	for hr in heirs(world):
		if int(hr.id) == person_id:
			ok = true
	if not ok:
		return {"ok": false, "reason": "Diese Person ist kein Erbe."}
	var pop = world.population
	var p: Dictionary = pop.persons[person_id]
	name = "%s %s" % [p.first, p.last]
	body = p.body
	birth = float(p.birth)
	appearance = p.appearance.duplicate(true)
	skills = p.skills.duplicate()
	education = p.education
	criminal_record = int(p.criminal_record)
	relationships = {}
	for k in p.relationships:
		if int(k) != PLAYER_ID:
			relationships[k] = p.relationships[k]
	needs = Needs.full()
	illness = {}
	activity = {}
	alive = true
	cause_of_death = ""
	partner = -1
	generation += 1
	# Stelle uebernehmen
	var pos := -1
	if world.jobs and not p.job.is_empty():
		pos = int(p.job.position)
		world.jobs.release(world, "npc", person_id, "switch")
	# Haushalt: Erbe lebt weiter in seinem Haushalt
	var hid: int = int(p.household)
	var hh: Dictionary = pop.households.get(hid, {})
	if world.education:
		world.education.rekey("npc", person_id, "player", PLAYER_ID)
	var own_home: int = world.housing.home_of("player", PLAYER_ID) if world.housing else -1
	pop._remove_person(world, person_id)
	if not hh.is_empty() and pop.households.has(hid):
		if hh.members.is_empty() and own_home < 0:
			# Haushaltsvermoegen geht an den Erben
			world.ledger.transfer_partial(int(hh.account), account, world.ledger.balance(int(hh.account)), "Erbschaft")
			if int(hh.home) >= 0:
				var pr: Dictionary = world.housing.properties[int(hh.home)]
				world.housing.move_out(world, int(hh.home))
				if pr.owner.kind == "household" and int(pr.owner.id) == hid:
					pr.owner = {"kind": "player", "id": PLAYER_ID}
				world.housing.assign("player", PLAYER_ID, int(hh.home), pr.owner.kind == "player")
				home = int(hh.home)
			pop._dissolve_if_empty(world, hid, -1)
			household = -1
		elif own_home < 0:
			household = hid
			hh.player_member = true
			home = int(hh.home)
	if own_home >= 0:
		household = -1
		home = own_home
	if pos >= 0 and world.jobs:
		world.jobs.hire(world, "player", PLAYER_ID, pos)
	_age_cache = age(world)
	_last_age_int = int(_age_cache)
	stage = LifeStages.stage(_age_cache)
	world.ledger.accounts[account].name = name
	world.notify("Du spielst jetzt als %s weiter." % name, "info")
	world.emit_event("player_home_changed", [home])
	return {"ok": true, "reason": ""}


## Neue Spielfigur in derselben Welt (Regel new_character). Der Nachlass der
## alten Figur faellt an die Stadt; Immobilien gehen an die Bank.
func restart_as_new(world, character: Dictionary, start := {}) -> Dictionary:
	if alive:
		return {"ok": false, "reason": "Die Spielfigur lebt noch."}
	var L: Ledger = world.ledger
	if world.government and L.balance(account) > 0.0:
		L.transfer(account, world.government.account, L.balance(account), "Nachlass")
	var h: int = world.housing.home_of("player", PLAYER_ID) if world.housing else -1
	if h >= 0:
		world.housing.move_out(world, h)
	if world.housing:
		for pid in world.housing.owned_by("player", PLAYER_ID):
			world.housing.properties[pid].owner = {"kind": "bank", "id": 0}
			world.housing.properties[pid].for_sale = true
			world.housing._update_flags(world.housing.properties[pid])
	if world.economy:
		world.economy.bank.transfer_debts("player", PLAYER_ID, "", -1, -1)
		for bid in world.economy.businesses_owned_by("player", PLAYER_ID):
			world.economy.businesses[bid].owner = {"kind": "city", "id": 0}
			world.economy.businesses[bid].for_sale = true
	if world.education:
		world.education.forget("player", PLAYER_ID)
	_leave_parents(world)
	inventory = {}
	vehicles = []
	licenses = {}
	job = {}
	relationships = {}
	partner = -1
	criminal_record = 0
	wanted = 0
	home = -1
	generation += 1
	_init_character(world, character, start)
	world.notify("Ein neues Leben beginnt: %s." % name, "info")
	return {"ok": true, "reason": ""}


func on_relative_died(world, person_id: int, cause: String) -> void:
	if relationships.has(person_id) and int(relationships[person_id]) >= 50:
		var pop = world.population
		world.notify("%s ist gestorben." % pop.person_name(person_id), "danger")
		needs.social = maxf(0.0, float(needs.social) - 20.0)
	if person_id == partner:
		partner = -1


# ============================================================ Anzeige / Speichern

func status(world) -> Dictionary:
	return {"name": name, "age": age(world), "stage": life_stage(), "stage_label": LifeStages.label(life_stage()),
		"money": money(world), "needs": needs.duplicate(), "mood": mood, "mood_label": Needs.mood_label(mood),
		"illness": illness.duplicate(), "job": world.jobs.job_of("player", PLAYER_ID, world) if world.jobs else {},
		"home": home, "education": education, "activity": activity.get("name", ""), "alive": alive}


func save_meta(world) -> Dictionary:
	var title := ""
	if world.jobs:
		title = str(world.jobs.job_of("player", PLAYER_ID).get("title", ""))
	return {"character_name": name, "age": int(age(world)), "money": snappedf(money(world), 0.01), "job_title": title}


const _FIELDS := ["name", "body", "birth", "appearance", "needs", "mood", "skills", "education", "account", "household",
	"home", "inventory", "vehicles", "licenses", "job", "relationships", "partner", "criminal_record", "wanted", "gang",
	"police_rank", "political_office", "alive", "cause_of_death", "stage", "illness", "activity", "generation",
	"adult_day", "truancy", "start_situation", "_last_age_int"]


func to_save() -> Dictionary:
	var d := {}
	for f in _FIELDS:
		var v = get(f)
		d[f] = v.duplicate(true) if v is Dictionary or v is Array else v
	d["rng"] = rng.state
	return d


func from_save(world, d: Dictionary) -> void:
	rng.seed = hash("player:%d" % world.seed)
	for f in _FIELDS:
		if d.has(f):
			var v = d[f]
			set(f, v.duplicate(true) if v is Dictionary or v is Array else v)
	if d.has("rng"):
		rng.state = int(d.rng)
	_age_cache = age(world)
