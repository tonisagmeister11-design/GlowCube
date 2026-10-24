class_name CrimeSystem
extends RefCounted
## Kriminalitaet im Alltag: Taten des Spielers und der NPCs, Fahndung
## (Fahndungsstufe 0..5), Festnahmen, Strafen, Vorstrafen, offene Ermittlungs-
## faelle fuer Polizisten und Einsatzmeldungen fuer den Verkehr.
## Polizei-Laufbahn (PoliceCareer) und Banden (Gangs) haengen als
## Untersysteme an diesem Objekt: `world.crime.police`, `world.crime.gangs`.
##
## Geld bewegt sich ausschliesslich ueber den Ledger: Beute kommt immer vom
## echten Opfer (Haushalt, Geschaeft), Strafen gehen an das Staatskonto.
## Polizeiwirkung: Government.modifier("police_effectiveness") (haengt am
## Polizeibudget) und das Gesetz policies["police_strictness"] aus der Politik.

const A := preload("res://scripts/crime/contract_access.gd")

const SEVERITY_ORDER := {"minor": 0, "misdemeanor": 1, "felony": 2}
const SEVERITY_LABELS := {"minor": "Ordnungswidrigkeit", "misdemeanor": "Vergehen", "felony": "Verbrechen"}
const WANTED_MAX := 5

## Tatarten. victim: person|business|household|vehicle|buyers|none.
## base: Grundchance; skill: Faehigkeit des Spielers; loot: [min, max];
## wanted: Fahndungsstufen bei Zeugen; fine (Euro), jail (Stunden);
## victim_reports: Wahrscheinlichkeit, dass das Opfer selbst identifizieren kann.
const TYPES := {
	"pickpocket": {"label": "Taschendiebstahl", "severity": "misdemeanor", "victim": "person", "base": 0.55,
		"skill": "stealth", "loot": [5.0, 70.0], "wanted": 1, "fine": 250.0, "jail": 4.0, "victim_reports": 0.25, "night": 0.0},
	"shoplifting": {"label": "Ladendiebstahl", "severity": "minor", "victim": "business", "base": 0.6,
		"skill": "stealth", "loot": [5.0, 80.0], "wanted": 1, "fine": 150.0, "jail": 0.0, "victim_reports": 0.3, "night": 0.0},
	"burglary": {"label": "Einbruch", "severity": "felony", "victim": "household", "base": 0.45,
		"skill": "stealth", "loot": [60.0, 900.0], "wanted": 2, "fine": 900.0, "jail": 36.0, "victim_reports": 0.0, "night": 0.15},
	"robbery": {"label": "Raubüberfall", "severity": "felony", "victim": "business", "base": 0.5,
		"skill": "strength", "loot": [100.0, 2500.0], "wanted": 3, "fine": 1800.0, "jail": 72.0, "victim_reports": 0.85, "night": 0.08},
	"car_theft": {"label": "Autodiebstahl", "severity": "felony", "victim": "vehicle", "base": 0.45,
		"skill": "driving", "loot": [1500.0, 9000.0], "wanted": 2, "fine": 1200.0, "jail": 48.0, "victim_reports": 0.0, "night": 0.12},
	"smuggling": {"label": "Schmuggel/illegaler Handel", "severity": "felony", "victim": "buyers", "base": 0.6,
		"skill": "charisma", "loot": [40.0, 300.0], "wanted": 2, "fine": 1000.0, "jail": 36.0, "victim_reports": 0.0, "night": 0.08},
	"vandalism": {"label": "Sachbeschädigung", "severity": "minor", "victim": "business", "base": 0.7,
		"skill": "stealth", "loot": [0.0, 0.0], "wanted": 1, "fine": 300.0, "jail": 0.0, "victim_reports": 0.1, "night": 0.1},
	"extortion": {"label": "Schutzgelderpressung", "severity": "felony", "victim": "business", "base": 0.55,
		"skill": "charisma", "loot": [40.0, 600.0], "wanted": 2, "fine": 1500.0, "jail": 48.0, "victim_reports": 0.3, "night": 0.0},
}

## Gewichte fuer NPC-Taten (Tag/Nacht).
const NPC_WEIGHTS_DAY := {"pickpocket": 3.0, "shoplifting": 3.5, "burglary": 1.0, "robbery": 0.7,
	"car_theft": 1.0, "vandalism": 1.0, "smuggling": 0.8, "extortion": 0.6}
const NPC_WEIGHTS_NIGHT := {"pickpocket": 1.0, "shoplifting": 0.5, "burglary": 3.0, "robbery": 1.2,
	"car_theft": 2.5, "vandalism": 2.0, "smuggling": 1.2, "extortion": 0.2}

## Grundrate NPC-Taten pro Stunde und Stadtteil bei Kriminalitaetsstufe 1.
const NPC_BASE_RATE := 0.12
const MAX_LOG := 300
const MAX_CASES := 80
const MAX_DISPATCH := 30
const CASE_COLD_DAYS := 10
const PROSECUTE_EVIDENCE := 0.6

var rng := RandomNumberGenerator.new()
var crimes: Array = []                 # Tatprotokoll (neueste zuerst)
var next_crime_id := 1
var cases: Dictionary = {}             # id -> Fall
var next_case_id := 1
var pending_dispatches: Array = []     # Einsatzmeldungen fuer Verkehr/Polizeiautos
var next_dispatch_id := 1

# ---- Spieler
var wanted := 0                        # Fahndungsstufe 0..5
var last_seen := 0.0                   # Spielminute der letzten Sichtung
var charges: Array = []                # offene Tat-IDs (noch nicht verurteilt)
var record: Array = []                 # Vorstrafen des Spielers
var jail_until := 0.0                  # Spielminute der Entlassung (0 = frei)
var jail_reason := ""
var stolen_goods: Array = []           # {kind, label, value, owner_account}
var hiding_until := 0.0
var player_pos := Vector2.ZERO
var player_pos_known := false
var player_indoors := false
var player_building := -1
var arrests_of_player := 0

# ---- NPCs / Statistik
var npc_jail: Dictionary = {}          # person id -> Entlassung (Spielminute)
var npc_records: Dictionary = {}       # person id -> [Eintraege]
var district_stats: Dictionary = {}    # district id -> {total, solved, today, yesterday}
var history: Array = []                # [{day, crimes, solved, player}] letzte 21 Tage
var today := {"crimes": 0, "solved": 0, "player": 0}
var police_ref := -1.0                 # Polizeibudget-Anteil bei Spielbeginn
var welfare_ref := -1.0
var total_npc_crimes := 0
var total_npc_solved := 0

# ---- Untersysteme
var police: PoliceCareer
var gangs: Gangs

# ---- Caches (nicht gespeichert)
var _crim_by_district: Dictionary = {}
var _persons_by_district: Dictionary = {}
var _biz_by_district: Dictionary = {}
var _all_criminals: Array = []
var _crim_ratio_city := 0.0
var _cache_day := -999


func _init() -> void:
	police = PoliceCareer.new()
	gangs = Gangs.new()


# ============================================================ Lebenszyklus

func setup(world) -> void:
	rng.seed = hash("crime:%d" % int(world.seed))
	police_ref = A.gov_budget(world, "police", -1.0)
	welfare_ref = A.gov_budget(world, "welfare", -1.0)
	rebuild_caches(world)
	gangs.setup(world, self)
	police.setup(world, self)


func post_load(world) -> void:
	rebuild_caches(world)
	gangs.post_load(world, self)
	police.post_load(world, self)


func step(world, dt: float) -> void:
	var now: float = world.time_minutes
	if jail_until > 0.0:
		if now >= jail_until:
			_release_player(world)
		return
	if wanted > 0:
		_wanted_step(world, dt)
	gangs.step(world, self, dt)


func on_hour(world, day: int, hour: int) -> void:
	_release_npcs(world)
	_npc_crime_hour(world, hour)
	if wanted >= 2 and jail_until <= 0.0:
		add_dispatch(_player_pos(world), "pursuit", "Verfolgung: Fahndungsstufe %d" % wanted, -1, wanted)
	gangs.on_hour(world, self, day, hour)


func on_day(world, day: int) -> void:
	history.push_front({"day": day - 1, "crimes": int(today.crimes), "solved": int(today.solved), "player": int(today.player)})
	if history.size() > 21:
		history.pop_back()
	today = {"crimes": 0, "solved": 0, "player": 0}
	for did in district_stats:
		var s: Dictionary = district_stats[did]
		s.yesterday = int(s.today)
		s.today = 0
	rebuild_caches(world)
	_process_cases(world, day)
	gangs.on_day(world, self, day)
	police.on_day(world, self, day)


# ============================================================ Caches

## Baut die Zuordnung Person/Geschaeft -> Stadtteil neu auf (einmal pro Tag).
func rebuild_caches(world) -> void:
	_crim_by_district.clear()
	_persons_by_district.clear()
	_biz_by_district.clear()
	_all_criminals.clear()
	var all := A.persons(world)
	var n_persons := 0
	for pid in all:
		var p = all[pid]
		if typeof(p) != TYPE_DICTIONARY or not bool(p.get("alive", true)):
			continue
		var did := _person_district(world, p, int(pid))
		if did < 0:
			continue
		n_persons += 1
		if not _persons_by_district.has(did):
			_persons_by_district[did] = []
		_persons_by_district[did].append(int(pid))
		if is_criminal(p):
			if not _crim_by_district.has(did):
				_crim_by_district[did] = []
			_crim_by_district[did].append(int(pid))
			_all_criminals.append(int(pid))
	_crim_ratio_city = float(_all_criminals.size()) / maxf(1.0, float(n_persons))
	var eco = A.field(world, "economy")
	var bizs = A.field(eco, "businesses", {})
	if typeof(bizs) == TYPE_DICTIONARY:
		for id in bizs:
			var b = bizs[id]
			if typeof(b) != TYPE_DICTIONARY:
				continue
			var bd := A.building(world, int(b.get("building", -1)))
			if bd.is_empty():
				continue
			var did2 := int(bd.get("district", -1))
			if not _biz_by_district.has(did2):
				_biz_by_district[did2] = []
			_biz_by_district[did2].append(int(id))
	_cache_day = world.get_day() if world.has_method("get_day") else 0


## Gilt eine Person als kriminell (hoher Charakterzug oder Bandenmitglied)?
static func is_criminal(p: Dictionary) -> bool:
	return A.person_trait(p, "criminal") >= 0.6 or int(p.get("gang", -1)) >= 0


func _person_district(world, p: Dictionary, pid: int) -> int:
	var home := int(p.get("home", -1))
	var housing = A.field(world, "housing")
	if home >= 0 and A.has_m(housing, "get_property"):
		var prop = housing.get_property(home)
		if typeof(prop) == TYPE_DICTIONARY and prop.has("building"):
			var bd := A.building(world, int(prop.building))
			if not bd.is_empty():
				return int(bd.get("district", -1))
	var loc = p.get("location", {})
	if typeof(loc) == TYPE_DICTIONARY and int(loc.get("building", -1)) >= 0:
		var bd2 := A.building(world, int(loc.building))
		if not bd2.is_empty():
			return int(bd2.get("district", -1))
	if world.city == null:
		return -1
	var d: Dictionary = world.city.district_at(A.person_pos(world, pid))
	return int(d.get("id", -1))


func persons_in_district(did: int) -> Array:
	return _persons_by_district.get(did, [])


func criminals_in_district(did: int) -> Array:
	return _crim_by_district.get(did, [])


func businesses_in_district(did: int) -> Array:
	return _biz_by_district.get(did, [])


# ============================================================ Polizei-Kennzahlen

## Wirksamkeit der Polizei (1.0 = Standard). Quelle: Government-Modifikator
## (Polizeibudget); fehlt er, wird der Budgetanteil relativ zum Startwert genutzt.
## Aktive Spieler-Polizisten erhoehen den Wert leicht.
func police_effectiveness(world) -> float:
	var eff := 1.0
	var m := A.gov_modifier(world, "police_effectiveness", -1.0)
	if m >= 0.0:
		eff = m
	else:
		var share := A.gov_budget(world, "police", -1.0)
		if share >= 0.0 and police_ref > 0.0:
			eff = 0.4 + 0.6 * share / police_ref
	eff *= 1.0 + police.effect_bonus()
	return clampf(eff, 0.1, 3.0)


## Polizeihaerte = Regel police_strictness * Gesetz (policies["police_strictness"]).
func strictness(world) -> float:
	var rule: float = world.rules.f("police_strictness") if world.rules != null else 1.0
	return clampf(rule * law_strictness(world), 0.0, 3.0)


func law_strictness(world) -> float:
	var v = A.gov_policy(world, "police_strictness", null)
	if v == null:
		var pol = A.field(world, "politics")
		if A.has_m(pol, "policy_value"):
			v = pol.policy_value("police_strictness", 1.0)
	return clampf(float(v) if v != null else 1.0, 0.3, 2.0)


## Wirkung der Sozialleistungen auf NPC-Kriminalitaet (< 1 senkt sie).
func welfare_factor(world) -> float:
	var level := 1.0
	var share := A.gov_budget(world, "welfare", -1.0)
	if share >= 0.0 and welfare_ref > 0.0:
		level = share / welfare_ref
	var pol = A.gov_policy(world, "welfare_level", null)
	if pol != null:
		level *= float(pol)
	return clampf(1.0 - 0.25 * (level - 1.0), 0.65, 1.4)


## Erwartete NPC-Taten pro Stunde in einem Stadtteil (ohne Tageszeitfaktor).
func expected_npc_crimes(world, did: int) -> float:
	var d := A.district(world, did)
	if d.is_empty():
		return 0.0
	var crime_lvl: float = float(d.get("crime", 0.2))
	var wealth: float = float(d.get("wealth", 0.5))
	var rate: float = world.rules.f("crime_rate") if world.rules != null else 1.0
	var eff := police_effectiveness(world)
	var deter := 1.0 / (0.35 + 0.65 * eff)
	var strict_f := 1.0 / (0.8 + 0.2 * strictness(world))
	var crim_f := 1.0
	if _crim_ratio_city > 0.0:
		var np := float(persons_in_district(did).size())
		var nc := float(criminals_in_district(did).size())
		var ratio := (nc / maxf(1.0, np)) / _crim_ratio_city if np > 0.0 else 0.0
		crim_f = clampf(0.5 + 0.5 * ratio, 0.5, 2.0)
	var gang_f := 1.0 + gangs.gang_strength_in(did) / 200.0
	return NPC_BASE_RATE * crime_lvl * (1.5 - wealth) * rate * deter * strict_f * welfare_factor(world) * crim_f * gang_f


## Erwartete NPC-Taten pro Tag in der ganzen Stadt (Prognose fuer Politik/UI).
func expected_npc_crimes_per_day(world) -> float:
	if world.city == null:
		return 0.0
	var hours := 0.0
	for h in 24:
		hours += _hour_factor(h)
	var total := 0.0
	for d in world.city.districts:
		total += expected_npc_crimes(world, int(d.id))
	return total * hours


func _hour_factor(hour: int) -> float:
	if hour >= 22 or hour < 5:
		return 1.5
	if hour >= 18:
		return 1.2
	if hour < 8:
		return 0.6
	return 0.9


## Aufklaerungswahrscheinlichkeit einer NPC-Tat durch die Polizei.
func solve_chance(world) -> float:
	return clampf(0.22 * police_effectiveness(world) * (0.6 + 0.4 * strictness(world)), 0.02, 0.9)


# ============================================================ Spieler: Taten

## Vorschau fuer die Oberflaeche: Erfolgschance, Zeugen, Fahndungsrisiko.
func risk_preview(world, type: String, ctx := {}) -> Dictionary:
	if not TYPES.has(type):
		return {}
	var pos := _ctx_pos(world, ctx)
	var t: Dictionary = TYPES[type]
	var near := _witness_candidates(world, pos, -1)
	return {"type": type, "label": t.label, "severity": t.severity, "severity_label": SEVERITY_LABELS[t.severity],
		"chance": success_chance(world, type, pos), "witnesses_near": near.size(),
		"police_effectiveness": police_effectiveness(world), "wanted_if_seen": int(t.wanted),
		"fine": float(t.fine), "jail_hours": float(t.jail)}


func success_chance(world, type: String, pos: Vector2) -> float:
	var t: Dictionary = TYPES.get(type, {})
	if t.is_empty():
		return 0.0
	var c: float = float(t.base)
	var skill := A.player_skill(world, str(t.skill), 10.0)
	c += (skill - 20.0) / 250.0
	var hour: int = world.get_hour()
	if hour >= 22 or hour < 5:
		c += float(t.night)
	var d := A.district_at(world, pos)
	c += (float(d.get("crime", 0.3)) - 0.3) * 0.15
	c -= (police_effectiveness(world) - 1.0) * 0.12
	c -= (strictness(world) - 1.0) * 0.05
	if player_building >= 0 and _is_police_building(world, player_building):
		c -= 0.4
	return clampf(c, 0.05, 0.95)


## Fuehrt eine Tat des Spielers aus.
## ctx (alles optional): pos: Vector2, person_id, business_id, building,
## for_gang (Bande erhaelt die Beute), _mission (Missions-ID, intern).
## Rueckgabe: {ok, success, loot, goods, witnessed, wanted_delta, reason, text, crime_id, arrested}
func attempt(world, type: String, ctx := {}) -> Dictionary:
	var res := {"ok": false, "success": false, "loot": 0.0, "goods": {}, "witnessed": false,
		"wanted_delta": 0, "reason": "", "text": "", "crime_id": -1, "arrested": false}
	if not TYPES.has(type):
		res.reason = "Unbekannte Tat."
		res.text = res.reason
		return res
	if in_jail(world):
		res.reason = "Du sitzt im Gefängnis."
		res.text = res.reason
		return res
	if not A.player_alive(world):
		res.reason = "Nicht möglich."
		res.text = res.reason
		return res
	var age := A.player_age(world)
	if age < 12.0:
		res.reason = "Dafür bist du zu jung."
		res.text = res.reason
		return res
	var t: Dictionary = TYPES[type]
	var pos := _ctx_pos(world, ctx)
	var dist := A.district_at(world, pos)
	var victim := _resolve_victim(world, type, ctx, pos)
	if str(t.victim) != "none" and victim.is_empty():
		res.reason = "Hier gibt es kein passendes Ziel."
		res.text = res.reason
		return res
	res.ok = true
	var chance := success_chance(world, type, pos)
	var success := rng.randf() < chance
	# Zeugen
	var wit := _witness_reports(world, pos, int(victim.get("person", -1)), success)
	var witnessed: bool = wit > 0
	if rng.randf() < (float(t.victim_reports) if success else 0.9):
		if not victim.is_empty():
			witnessed = true
	var loot := 0.0
	var goods := {}
	var for_gang := int(ctx.get("for_gang", -1))
	if success:
		var lr := _take_loot(world, type, victim, dist, for_gang)
		loot = float(lr.loot)
		goods = lr.goods
	var wanted_delta := 0
	if witnessed:
		wanted_delta = int(t.wanted) + (0 if success else 1)
		if age < 14.0:
			wanted_delta = mini(wanted_delta, 1)
	var crime := _log_crime(world, {"type": type, "pos": pos, "district": int(dist.get("id", -1)),
		"perp_kind": "player", "perp": A.PLAYER_ID, "gang": gangs.player_gang, "victim_kind": str(victim.get("kind", "")),
		"victim": int(victim.get("id", -1)), "victim_name": str(victim.get("name", "")), "loot": loot,
		"success": success, "witnessed": witnessed})
	res.crime_id = int(crime.id)
	today.player = int(today.player) + 1
	if witnessed:
		charges.append(int(crime.id))
		last_seen = world.time_minutes
		_set_wanted(world, mini(WANTED_MAX, wanted + wanted_delta))
		add_dispatch(pos, "crime", "%s gemeldet" % t.label, int(crime.id), int(t.wanted))
	elif success and str(t.severity) != "minor":
		# Unbemerkt: es entsteht ein Ermittlungsfall gegen Unbekannt (Spieler).
		_open_case(world, crime, A.PLAYER_ID, "player", rng.randf_range(0.1, 0.35))
	# Opferbeziehung leidet, falls bekannt
	if int(victim.get("person", -1)) >= 0 and witnessed:
		A.change_player_relationship(world, int(victim.person), -30.0)
	gangs.on_player_crime(world, self, crime, ctx)
	res.success = success
	res.loot = loot
	res.goods = goods
	res.witnessed = witnessed
	res.wanted_delta = wanted_delta
	# Sofortige Festnahme bei Scheitern unter Zeugen in Polizeinaehe
	if not success and witnessed:
		var p_now := 0.12 * police_effectiveness(world) * (0.5 + 0.5 * strictness(world))
		if _police_station_near(world, pos, 300.0):
			p_now += 0.25
		if rng.randf() < p_now:
			var ar := arrest_player(world, "Auf frischer Tat ertappt")
			res.arrested = true
			res.text = "%s gescheitert. Du wurdest auf frischer Tat festgenommen. %s" % [t.label, ar.text]
			return res
	if success:
		var what := ""
		if loot > 0.0:
			what = " Beute: %.2f €." % loot
		elif not goods.is_empty():
			what = " Beute: %s." % _goods_text(goods)
		res.text = "%s gelungen.%s" % [t.label, what]
	else:
		res.text = "%s gescheitert." % t.label
	if witnessed:
		res.text += " Du wurdest gesehen – Fahndungsstufe %d." % wanted
		if str(t.severity) == "felony" and success:
			world.post_news("Polizei", "%s in %s: Die Polizei sucht nach dem Täter." % [t.label, dist.get("name", "der Stadt")])
	world.emit_event("crime_committed", [crime.duplicate()])
	return res


## Der Spieler wird bei einer Tat erwischt (z.B. Polizeikontrolle bei einer
## Lieferung): Tat wird protokolliert, Anklage und Fahndung steigen.
func caught_in_act(world, type: String, pos: Vector2) -> Dictionary:
	var t: Dictionary = TYPES.get(type, TYPES["smuggling"])
	var dist := A.district_at(world, pos)
	var crime := _log_crime(world, {"type": type, "pos": pos, "district": int(dist.get("id", -1)),
		"perp_kind": "player", "perp": A.PLAYER_ID, "gang": gangs.player_gang, "victim_kind": "", "victim": -1,
		"victim_name": "", "loot": 0.0, "success": false, "witnessed": true})
	charges.append(int(crime.id))
	last_seen = world.time_minutes
	_set_wanted(world, mini(WANTED_MAX, wanted + int(t.wanted)))
	add_dispatch(pos, "pursuit", "Kontrolle: %s" % t.label, int(crime.id), int(t.wanted))
	world.emit_event("crime_committed", [crime.duplicate()])
	return {"ok": true, "crime_id": int(crime.id), "wanted": wanted}


func _goods_text(goods: Dictionary) -> String:
	var parts := []
	for g in goods:
		parts.append("%dx %s" % [int(goods[g]), str(g)])
	return ", ".join(parts)


func _ctx_pos(world, ctx: Dictionary) -> Vector2:
	if ctx.has("pos"):
		return A.to_v2(ctx.pos, Vector2.ZERO)
	if ctx.has("building") and int(ctx.building) >= 0:
		return A.building_pos(world, int(ctx.building))
	if ctx.has("business_id"):
		var b := A.business(world, int(ctx.business_id))
		if not b.is_empty():
			return A.building_pos(world, int(b.get("building", -1)))
	if ctx.has("person_id"):
		return A.person_pos(world, int(ctx.person_id))
	return _player_pos(world)


func _player_pos(world) -> Vector2:
	if player_pos_known:
		return player_pos
	var p = A.field(A.player(world), "position", null)
	if p != null:
		return A.to_v2(p)
	if world.city != null and world.city.spawn_points.size() > 0:
		return A.to_v2(world.city.spawn_points[0].get("pos", Vector2.ZERO))
	return Vector2.ZERO


## Die Weltszene meldet regelmaessig Position und Aufenthalt des Spielers.
func set_player_context(pos: Vector2, indoors := false, building := -1) -> void:
	player_pos = pos
	player_pos_known = true
	player_indoors = indoors
	player_building = building


func _resolve_victim(world, type: String, ctx: Dictionary, pos: Vector2) -> Dictionary:
	var kind: String = TYPES[type].victim
	match kind:
		"person", "vehicle":
			var pid := int(ctx.get("person_id", -1))
			if pid < 0:
				pid = _pick_person_near(world, pos, 25.0 if kind == "person" else 60.0)
			if pid < 0:
				return {}
			return {"kind": "person", "id": pid, "person": pid, "account": A.household_account(world, pid),
				"name": A.person_name(world, pid)}
		"household":
			var pid2 := int(ctx.get("person_id", -1))
			var bid := int(ctx.get("building", -1))
			if pid2 < 0 and bid >= 0:
				pid2 = _resident_of(world, bid)
			if pid2 < 0:
				pid2 = _pick_person_near(world, pos, 60.0)
			if pid2 < 0:
				return {}
			return {"kind": "household", "id": pid2, "person": -1, "account": A.household_account(world, pid2),
				"name": "Haushalt " + str(A.person(world, pid2).get("last", ""))}
		"business":
			var biz := int(ctx.get("business_id", -1))
			if biz < 0 and int(ctx.get("building", -1)) >= 0:
				biz = A.business_for_building(world, int(ctx.building))
			if biz < 0:
				biz = _nearest_business(world, pos, 80.0)
			if biz < 0 or A.business(world, biz).is_empty():
				return {}
			return {"kind": "business", "id": biz, "person": -1, "account": A.business_account(world, biz),
				"name": A.business_name(world, biz)}
		"buyers":
			var near := _witness_candidates(world, pos, -1)
			if near.is_empty():
				var d := A.district_at(world, pos)
				near = persons_in_district(int(d.get("id", -1)))
			if near.is_empty():
				return {}
			return {"kind": "buyers", "id": -1, "person": -1, "buyers": near.slice(0, 6), "name": "Abnehmer"}
	return {"kind": "none", "id": -1}


func _pick_person_near(world, pos: Vector2, radius: float) -> int:
	var ids := A.persons_near(world, pos, radius)
	var best := -1
	for pid in ids:
		var p := A.person(world, pid)
		if p.is_empty() or not bool(p.get("alive", true)) or npc_jail.has(pid):
			continue
		if gangs.player_gang >= 0 and int(p.get("gang", -1)) == gangs.player_gang:
			continue
		if A.household_account(world, pid) < 0:
			continue
		best = pid
		break
	return best


func _resident_of(world, bid: int) -> int:
	var housing = A.field(world, "housing")
	var all := A.persons(world)
	for pid in all:
		var p = all[pid]
		if typeof(p) != TYPE_DICTIONARY:
			continue
		if int(p.get("home_building", -1)) == bid:
			return int(pid)
		var home := int(p.get("home", -1))
		if home >= 0 and A.has_m(housing, "get_property"):
			var prop = housing.get_property(home)
			if typeof(prop) == TYPE_DICTIONARY and int(prop.get("building", -1)) == bid:
				return int(pid)
	return -1


func _nearest_business(world, pos: Vector2, radius: float) -> int:
	var d := A.district_at(world, pos)
	var best := -1
	var bd := radius * radius
	var cand: Array = businesses_in_district(int(d.get("id", -1)))
	if cand.is_empty():
		for did in _biz_by_district:
			cand.append_array(_biz_by_district[did])
	for biz in cand:
		var b := A.business(world, biz)
		var p := A.building_pos(world, int(b.get("building", -1)))
		var dd := p.distance_squared_to(pos)
		if dd < bd:
			bd = dd
			best = biz
	return best


func _witness_candidates(world, pos: Vector2, victim_pid: int) -> Array:
	var out := []
	for pid in A.persons_near(world, pos, 30.0):
		if pid == victim_pid or npc_jail.has(pid):
			continue
		var p := A.person(world, pid)
		if p.is_empty() or not bool(p.get("alive", true)):
			continue
		if str(p.get("activity", "")) in ["sleep", "jail", "hospital"]:
			continue
		if gangs.player_gang >= 0 and int(p.get("gang", -1)) == gangs.player_gang:
			continue
		out.append(pid)
	return out


## Anzahl der Zeugen, die die Tat melden (deterministischer Wurf je Zeuge).
func _witness_reports(world, pos: Vector2, victim_pid: int, success: bool) -> int:
	var hour: int = world.get_hour()
	var night := hour >= 22 or hour < 5
	var stealth := A.player_skill(world, "stealth", 10.0)
	var reports := 0
	var cands := _witness_candidates(world, pos, victim_pid)
	if A.field(world, "population") == null:
		# Ohne Bevoelkerungsdaten: Passanten aus Dichte und Tageszeit schaetzen
		var d := A.district_at(world, pos)
		var zone: Dictionary = world.city.zone_info(str(d.get("zone", "residential"))) if world.city != null else {}
		var expected: float = float(zone.get("density", 0.5)) * (1.0 if night else 4.0)
		for i in int(round(expected)):
			cands.append(-1)
	for pid in cands:
		var p := A.person(world, pid) if pid >= 0 else {}
		var lawful := A.person_trait(p, "lawful", 0.5)
		var crim := A.person_trait(p, "criminal", 0.1)
		var pr := (0.08 + 0.25 * lawful - 0.1 * crim) * (1.0 - stealth / 200.0)
		if night:
			pr *= 0.6
		if not success:
			pr *= 2.0
		if rng.randf() < clampf(pr, 0.01, 0.95):
			reports += 1
	return reports


## Bewegt die Beute vom echten Opfer zum Spieler (oder zur Bande).
func _take_loot(world, type: String, victim: Dictionary, dist: Dictionary, for_gang: int) -> Dictionary:
	var t: Dictionary = TYPES[type]
	var led: Ledger = world.ledger
	var to_acc := A.player_account(world)
	if for_gang >= 0:
		var gacc := gangs.gang_account(for_gang)
		if gacc >= 0:
			to_acc = gacc
	var wealth: float = float(dist.get("wealth", 0.5))
	var amount := rng.randf_range(float(t.loot[0]), float(t.loot[1])) * (0.5 + wealth)
	var label := str(t.label)
	var out := {"loot": 0.0, "goods": {}}
	match type:
		"pickpocket", "burglary", "extortion", "robbery":
			var acc := int(victim.get("account", -1))
			if acc < 0 or to_acc < 0:
				return out
			if victim.kind == "business":
				var bal := led.balance(acc)
				var share := 0.06 if type == "extortion" else 0.25
				amount = minf(amount, maxf(0.0, bal * share))
				if type == "extortion":
					amount = maxf(amount, minf(20.0, bal))
			else:
				amount = minf(amount, maxf(0.0, led.balance(acc) * (0.15 if type == "burglary" else 0.5)))
			out.loot = led.transfer_partial(acc, to_acc, snappedf(amount, 0.01), label)
		"shoplifting":
			var b := A.business(world, int(victim.id))
			var inv = b.get("inventory", {})
			if typeof(inv) != TYPE_DICTIONARY:
				return out
			var keys: Array = inv.keys()
			keys.sort()
			var pick := ""
			for i in keys.size():
				var g = keys[(i + rng.randi_range(0, keys.size() - 1)) % keys.size()]
				var e = inv[g]
				if typeof(e) == TYPE_DICTIONARY and int(e.get("stock", 0)) > 0 and float(e.get("price", 0.0)) <= 120.0:
					pick = str(g)
					break
			if pick == "":
				return out
			var e2: Dictionary = inv[pick]
			var qty := mini(int(e2.stock), rng.randi_range(1, 2))
			e2.stock = int(e2.stock) - qty
			A.inventory_add(world, pick, qty)
			out.goods = {pick: qty}
			out.loot = 0.0
			out["goods_value"] = float(e2.get("price", 0.0)) * qty
		"car_theft":
			var value := snappedf(amount, 10.0)
			stolen_goods.append({"kind": "car", "label": "Gestohlenes Auto", "value": value,
				"owner_account": int(victim.get("account", -1)), "day": world.get_day()})
			out.goods = {"Gestohlenes Auto": 1}
			out["goods_value"] = value
		"smuggling":
			var total := 0.0
			for pid in victim.get("buyers", []):
				var acc2 := A.household_account(world, int(pid))
				if acc2 < 0 or to_acc < 0:
					continue
				var each := minf(amount / 3.0, led.balance(acc2) * 0.1)
				if each >= 1.0:
					total += led.transfer_partial(acc2, to_acc, snappedf(each, 0.01), "Illegaler Handel")
			out.loot = total
		"vandalism":
			var acc3 := int(victim.get("account", -1))
			if acc3 >= 0:
				# Reparatur durch auswaertige Handwerker: Geld verlaesst die Stadt
				var cost := minf(rng.randf_range(50.0, 300.0), maxf(0.0, led.balance(acc3)))
				if cost > 0.0:
					led.burn(acc3, snappedf(cost, 0.01), "Reparatur nach Sachbeschädigung")
	return out


## Verkauft gestohlene Waren (Autos) an einen Hehler (Bandenkasse).
## Der Spieler erhaelt 35 % des Werts aus dem Konto der Bande.
func fence_goods(world, gang_id := -1) -> Dictionary:
	if stolen_goods.is_empty():
		return {"ok": false, "reason": "Du hast nichts zu verkaufen.", "amount": 0.0}
	var gid := gang_id if gang_id >= 0 else gangs.player_gang
	if gid < 0:
		gid = gangs.strongest_gang()
	var gacc := gangs.gang_account(gid)
	if gacc < 0:
		return {"ok": false, "reason": "Kein Hehler erreichbar.", "amount": 0.0}
	var total := 0.0
	var keep := []
	for g in stolen_goods:
		var price := float(g.value) * 0.35
		var paid: float = world.ledger.transfer_partial(gacc, A.player_account(world), snappedf(price, 0.01), "Hehlerei")
		if paid <= 0.0:
			keep.append(g)
		total += paid
	stolen_goods = keep
	gangs.add_rep(world, gid, 2.0)
	return {"ok": total > 0.0, "reason": "" if total > 0.0 else "Der Hehler hat kein Geld.", "amount": total,
		"text": "Hehler zahlt %.2f €." % total}


# ============================================================ Protokoll / Faelle

func _log_crime(world, data: Dictionary) -> Dictionary:
	var t: Dictionary = TYPES[data.type]
	var c := data.duplicate()
	c.id = next_crime_id
	next_crime_id += 1
	c.label = t.label
	c.severity = t.severity
	c.time = world.time_minutes
	c.day = world.get_day()
	c.hour = world.get_hour()
	c.solved = false
	c.case = -1
	var did := int(c.get("district", -1))
	var d := A.district(world, did)
	c.district_name = str(d.get("name", ""))
	crimes.push_front(c)
	if crimes.size() > MAX_LOG:
		crimes.pop_back()
	if not district_stats.has(did):
		district_stats[did] = {"total": 0, "solved": 0, "today": 0, "yesterday": 0}
	var s: Dictionary = district_stats[did]
	s.total = int(s.total) + 1
	s.today = int(s.today) + 1
	today.crimes = int(today.crimes) + 1
	return c


func get_crime(crime_id: int) -> Dictionary:
	for c in crimes:
		if int(c.id) == crime_id:
			return c
	return {}


func _open_case(world, crime: Dictionary, suspect: int, suspect_kind: String, evidence: float) -> int:
	if cases.size() >= MAX_CASES:
		# aeltesten offenen Fall einstellen
		var oldest := -1
		for id in cases:
			if cases[id].status in ["open", "assigned"] and (oldest < 0 or id < oldest):
				oldest = id
		if oldest >= 0:
			cases[oldest].status = "cold"
			_prune_cases()
	var id := next_case_id
	next_case_id += 1
	cases[id] = {"id": id, "crime": int(crime.id), "type": str(crime.type), "label": str(crime.label),
		"severity": str(crime.severity), "district": int(crime.get("district", -1)),
		"district_name": str(crime.get("district_name", "")), "pos": A.to_v2(crime.get("pos", Vector2.ZERO)),
		"victim_name": str(crime.get("victim_name", "")), "suspect": suspect, "suspect_kind": suspect_kind,
		"gang": int(crime.get("gang", -1)), "evidence": clampf(evidence, 0.0, 1.0), "status": "open",
		"opened_day": world.get_day(), "assigned_player": false}
	crime.case = id
	return id


func _prune_cases() -> void:
	var drop := []
	for id in cases:
		if cases[id].status in ["cold", "solved"] and cases.size() - drop.size() > MAX_CASES:
			drop.append(id)
	for id in drop:
		cases.erase(id)


## Offene Faelle fuer die Polizei-Oberflaeche (ohne Faelle gegen den Spieler selbst).
func open_cases(world) -> Array:
	var out := []
	var ids := cases.keys()
	ids.sort()
	for id in ids:
		var c: Dictionary = cases[id]
		if not (c.status in ["open", "assigned"]) or c.suspect_kind == "player":
			continue
		var e := c.duplicate()
		e.age_days = world.get_day() - int(c.opened_day)
		e.suspect_name = A.person_name(world, int(c.suspect)) if float(c.evidence) >= PROSECUTE_EVIDENCE and int(c.suspect) >= 0 else "Unbekannt"
		e.evidence_percent = roundi(float(c.evidence) * 100.0)
		out.append(e)
	return out


func get_case(case_id: int) -> Dictionary:
	return cases.get(case_id, {})


## Taegliche Ermittlungsarbeit der NPC-Polizei an offenen Faellen.
func _process_cases(world, day: int) -> void:
	var eff := police_effectiveness(world)
	var ids := cases.keys()
	ids.sort()
	for id in ids:
		var c: Dictionary = cases[id]
		if c.status != "open":
			continue
		if day - int(c.opened_day) > CASE_COLD_DAYS:
			c.status = "cold"
			continue
		c.evidence = clampf(float(c.evidence) + rng.randf_range(0.0, 0.08) * eff, 0.0, 1.0)
		var p := 0.06 * eff * (0.5 + float(c.evidence))
		if rng.randf() < p:
			_solve_case_by_npc_police(world, c)
	_prune_cases()


func _solve_case_by_npc_police(world, c: Dictionary) -> void:
	c.status = "solved"
	var crime := get_crime(int(c.crime))
	if not crime.is_empty():
		crime.solved = true
	today.solved = int(today.solved) + 1
	_district_solved(int(c.district))
	if c.suspect_kind == "player":
		# Ermittlungen gegen den Spieler: Haftbefehl
		if not charges.has(int(c.crime)):
			charges.append(int(c.crime))
		var lvl := 3 if c.severity == "felony" else 1
		last_seen = world.time_minutes
		_set_wanted(world, maxi(wanted, lvl))
		world.notify("Die Polizei hat dich als Tatverdächtigen ermittelt (%s)." % c.label, "danger")
		world.post_news("Polizei", "Ermittlungserfolg: Haftbefehl im Fall %s in %s." % [c.label, c.district_name])
	elif int(c.suspect) >= 0:
		convict_npc(world, int(c.suspect), str(c.type))


func _district_solved(did: int) -> void:
	if district_stats.has(did):
		district_stats[did].solved = int(district_stats[did].solved) + 1


# ============================================================ NPC-Kriminalitaet

func _npc_crime_hour(world, hour: int) -> void:
	if world.city == null:
		return
	var hf := _hour_factor(hour)
	for d in world.city.districts:
		var lam := expected_npc_crimes(world, int(d.id)) * hf * police.patrol_factor(world, int(d.id))
		var n := _poisson(lam)
		for i in n:
			_npc_crime(world, d, hour)


func _poisson(lam: float) -> int:
	if lam <= 0.0:
		return 0
	var l := exp(-lam)
	var k := 0
	var p := 1.0
	while k < 8:
		p *= rng.randf()
		if p <= l:
			break
		k += 1
	return k


func _pick_weighted(weights: Dictionary) -> String:
	var total := 0.0
	for k in weights:
		total += float(weights[k])
	var r := rng.randf() * total
	for k in weights:
		r -= float(weights[k])
		if r <= 0.0:
			return k
	return weights.keys()[0]


func _npc_crime(world, d: Dictionary, hour: int) -> void:
	var did := int(d.id)
	var night := hour >= 21 or hour < 5
	var weights: Dictionary = (NPC_WEIGHTS_NIGHT if night else NPC_WEIGHTS_DAY).duplicate()
	if businesses_in_district(did).is_empty():
		for k in ["shoplifting", "robbery", "vandalism", "extortion"]:
			weights.erase(k)
	if gangs.district_owner(did) < 0:
		weights.erase("extortion")
	if weights.is_empty():
		weights = {"pickpocket": 1.0, "burglary": 1.0}
	var type := _pick_weighted(weights)
	var t: Dictionary = TYPES[type]
	# Taeter
	var perp := -1
	var pool: Array = criminals_in_district(did)
	if pool.is_empty() or rng.randf() < 0.25:
		pool = _all_criminals
	for tries in 4:
		if pool.is_empty():
			break
		var cand: int = pool[rng.randi_range(0, pool.size() - 1)]
		if not npc_jail.has(cand):
			perp = cand
			break
	var perp_gang := int(A.person(world, perp).get("gang", -1)) if perp >= 0 else -1
	# Opfer
	var victim := {}
	if str(t.victim) == "business":
		var bl: Array = businesses_in_district(did)
		if not bl.is_empty():
			var biz: int = bl[rng.randi_range(0, bl.size() - 1)]
			victim = {"kind": "business", "id": biz, "account": A.business_account(world, biz), "name": A.business_name(world, biz)}
	else:
		var pl: Array = persons_in_district(did)
		if not pl.is_empty():
			var vp: int = pl[rng.randi_range(0, pl.size() - 1)]
			if vp != perp:
				victim = {"kind": "person", "id": vp, "account": A.household_account(world, vp), "name": A.person_name(world, vp)}
	var pos: Vector2 = A.to_v2(d.get("center", Vector2.ZERO))
	if victim.get("kind", "") == "business":
		pos = A.building_pos(world, int(A.business(world, int(victim.id)).get("building", -1)))
	var loot := 0.0
	var to_acc := A.household_account(world, perp) if perp >= 0 else -1
	if perp_gang >= 0 and type in ["extortion", "smuggling", "robbery"]:
		var gacc := gangs.gang_account(perp_gang)
		if gacc >= 0:
			to_acc = gacc
	var led: Ledger = world.ledger
	var vacc := int(victim.get("account", -1))
	var wealth: float = float(d.get("wealth", 0.5))
	match type:
		"pickpocket", "burglary", "robbery", "extortion", "smuggling":
			if vacc >= 0 and to_acc >= 0:
				var amt := rng.randf_range(float(t.loot[0]), float(t.loot[1])) * (0.4 + wealth) * 0.6
				var cap := led.balance(vacc) * (0.05 if victim.kind == "business" else 0.2)
				amt = minf(amt, maxf(0.0, cap))
				if amt >= 1.0:
					loot = led.transfer_partial(vacc, to_acc, snappedf(amt, 0.01), str(t.label))
		"shoplifting":
			var b := A.business(world, int(victim.get("id", -1)))
			var inv = b.get("inventory", {})
			if typeof(inv) == TYPE_DICTIONARY and not inv.is_empty():
				var keys: Array = inv.keys()
				var g = keys[rng.randi_range(0, keys.size() - 1)]
				if typeof(inv[g]) == TYPE_DICTIONARY and int(inv[g].get("stock", 0)) > 0:
					inv[g].stock = int(inv[g].stock) - 1
		"vandalism":
			if vacc >= 0:
				var cost := minf(rng.randf_range(20.0, 150.0), maxf(0.0, led.balance(vacc) * 0.02))
				if cost >= 1.0:
					led.burn(vacc, snappedf(cost, 0.01), "Reparatur nach Sachbeschädigung")
		_:
			pass
	var crime := _log_crime(world, {"type": type, "pos": pos, "district": did, "perp_kind": "npc", "perp": perp,
		"gang": perp_gang, "victim_kind": str(victim.get("kind", "")), "victim": int(victim.get("id", -1)),
		"victim_name": str(victim.get("name", "")), "loot": loot, "success": true, "witnessed": false})
	total_npc_crimes += 1
	if perp_gang >= 0:
		gangs.on_npc_gang_crime(world, self, perp_gang, did)
	if type in ["robbery", "burglary", "car_theft"]:
		add_dispatch(pos, "crime", "%s in %s" % [t.label, d.get("name", "")], int(crime.id), 2)
	if rng.randf() < solve_chance(world) and perp >= 0:
		crime.solved = true
		total_npc_solved += 1
		today.solved = int(today.solved) + 1
		_district_solved(did)
		convict_npc(world, perp, type)
	else:
		_open_case(world, crime, perp, "npc", rng.randf_range(0.05, 0.4))
	if type == "robbery" and rng.randf() < 0.5:
		world.post_news("Polizei", "%s auf %s in %s." % [t.label, victim.get("name", "ein Geschäft"), d.get("name", "")])
	world.emit_event("crime_committed", [crime.duplicate()])


## Verurteilung eines NPC: Vorstrafe + Haft. Bandenmitglieder schwaechen die Bande.
func convict_npc(world, pid: int, type: String) -> Dictionary:
	if pid < 0:
		return {}
	var t: Dictionary = TYPES.get(type, TYPES["shoplifting"])
	var hours: float = maxf(4.0, float(t.jail)) * maxf(0.3, strictness(world))
	var entry := {"type": type, "label": str(t.label), "severity": str(t.severity), "day": world.get_day(),
		"jail_hours": hours}
	if not npc_records.has(pid):
		npc_records[pid] = []
	npc_records[pid].append(entry)
	var p := A.person(world, pid)
	if not p.is_empty():
		A.add_record(p, entry)
		p["activity"] = "jail"
	npc_jail[pid] = world.time_minutes + hours * 60.0
	gangs.on_member_arrested(world, self, pid)
	return entry


func _release_npcs(world) -> void:
	var now: float = world.time_minutes
	var done := []
	for pid in npc_jail:
		if now >= float(npc_jail[pid]):
			done.append(pid)
	for pid in done:
		npc_jail.erase(pid)
		var p := A.person(world, pid)
		if not p.is_empty() and str(p.get("activity", "")) == "jail":
			p["activity"] = "home"


func is_npc_jailed(pid: int) -> bool:
	return npc_jail.has(pid)


# ============================================================ Fahndung / Festnahme

func wanted_level() -> int:
	return wanted


func _set_wanted(world, lvl: int) -> void:
	lvl = clampi(lvl, 0, WANTED_MAX)
	if lvl == wanted:
		return
	wanted = lvl
	A.set_field(A.player(world), "wanted", wanted)
	world.emit_event("wanted_level_changed", [wanted])


## Wahrscheinlichkeit pro Stunde, bei aktueller Fahndungsstufe festgenommen zu werden.
func arrest_chance_per_hour(world) -> float:
	return arrest_chance_for_level(world, wanted)


## Festnahmewahrscheinlichkeit pro Stunde fuer eine beliebige Fahndungsstufe
## (fuer Vorschau und Politik-Auswertung).
func arrest_chance_for_level(world, lvl: int) -> float:
	if lvl <= 0:
		return 0.0
	var base: float = [0.0, 0.06, 0.14, 0.24, 0.36, 0.5][clampi(lvl, 0, WANTED_MAX)]
	var p := base * police_effectiveness(world) * (0.4 + 0.6 * strictness(world))
	if is_hiding(world):
		p *= 0.15
	elif player_indoors:
		p *= 0.5
	return clampf(p, 0.0, 0.9)


## Minuten ohne Sichtung, nach denen die Fahndung um eine Stufe sinkt.
func decay_minutes(world) -> float:
	var m := 90.0 * (0.6 + 0.4 * strictness(world))
	if is_hiding(world):
		m *= 0.5
	return maxf(20.0, m)


func is_hiding(world) -> bool:
	return hiding_until > world.time_minutes


func _wanted_step(world, dt: float) -> void:
	var p_h := arrest_chance_per_hour(world)
	var p_step := 1.0 - pow(1.0 - p_h, dt / 60.0)
	if rng.randf() < p_step:
		arrest_player(world, "Fahndung")
		return
	# Sichtung durch Streifen haelt die Fahndung aufrecht
	var see_h := 0.0 if is_hiding(world) else 0.25 * police_effectiveness(world) * (0.5 if player_indoors else 1.0)
	if rng.randf() < 1.0 - pow(1.0 - clampf(see_h, 0.0, 0.95), dt / 60.0):
		last_seen = world.time_minutes
	if world.time_minutes - last_seen >= decay_minutes(world):
		last_seen = world.time_minutes
		_set_wanted(world, wanted - 1)
		if wanted == 0:
			world.notify("Die Polizei hat deine Spur verloren.", "success")


## Untertauchen (zu Hause, im Versteck der Bande): senkt Festnahmerisiko,
## Fahndung klingt schneller ab. Zeit vergeht nur mit advance = true.
func hide(world, hours: float, advance := true) -> Dictionary:
	if in_jail(world):
		return {"ok": false, "reason": "Du sitzt im Gefängnis.", "minutes": 0.0}
	hours = clampf(hours, 0.5, 24.0)
	hiding_until = world.time_minutes + hours * 60.0
	if advance:
		world.advance(hours * 60.0)
	return {"ok": true, "minutes": hours * 60.0, "text": "Du tauchst unter.", "wanted": wanted}


## Fuehrt eine Festnahme des Spielers durch: Geldstrafe (Teilzahlung moeglich,
## Rest wird in Haftstunden umgewandelt), Haft, Vorstrafen.
func arrest_player(world, reason := "", mult := 1.0) -> Dictionary:
	var strict := strictness(world)
	var fine := 0.0
	var jail := 0.0
	var entries := []
	var prior := 0
	for r in record:
		if str(r.get("severity", "")) == "felony":
			prior += 1
	for cid in charges:
		var c := get_crime(int(cid))
		var type: String = str(c.get("type", "shoplifting"))
		var t: Dictionary = TYPES.get(type, TYPES["shoplifting"])
		fine += float(t.fine)
		jail += float(t.jail)
		entries.append({"type": type, "label": str(t.label), "severity": str(t.severity), "day": world.get_day(),
			"crime": int(cid)})
		if not c.is_empty():
			c.solved = true
	if entries.is_empty() and wanted > 0:
		fine = 120.0 * wanted
		jail = 3.0 * wanted
		entries.append({"type": "resisting", "label": "Flucht vor der Polizei", "severity": "minor", "day": world.get_day()})
	var repeat := 1.0 + 0.25 * mini(prior, 4)
	fine *= (0.5 + 0.5 * strict) * repeat * mult
	jail *= (0.3 + 0.7 * strict) * repeat * mult
	var led: Ledger = world.ledger
	var paid := 0.0
	var gacc := A.gov_account(world)
	var pacc := A.player_account(world)
	if fine > 0.0 and pacc >= 0:
		if gacc >= 0:
			paid = led.transfer_partial(pacc, gacc, snappedf(fine, 0.01), "Geldstrafe")
		else:
			var avail := maxf(0.0, led.balance(pacc))
			paid = minf(fine, avail)
			if paid > 0.0:
				led.burn(pacc, snappedf(paid, 0.01), "Geldstrafe")
	var unpaid := maxf(0.0, fine - paid)
	jail += unpaid / 40.0
	for e in entries:
		e["fine"] = snappedf(fine / entries.size(), 0.01)
		e["jail_hours"] = snappedf(jail / entries.size(), 0.1)
		record.append(e)
		A.add_record(A.player(world), e)
	# Beute wird beschlagnahmt
	stolen_goods.clear()
	charges.clear()
	hiding_until = 0.0
	arrests_of_player += 1
	_set_wanted(world, 0)
	if jail >= 0.5:
		jail_until = world.time_minutes + jail * 60.0
		jail_reason = entries[0].label if not entries.is_empty() else reason
	var text := "Festgenommen. Geldstrafe %.2f € (bezahlt %.2f €)" % [fine, paid]
	if jail >= 0.5:
		text += ", Haft %.1f Stunden." % jail
	else:
		text += "."
	world.notify(text, "danger")
	world.post_news("Polizei", "Festnahme: %s wurde wegen %s festgenommen." % [_player_name(world), str(entries[0].label) if not entries.is_empty() else reason])
	police.on_player_arrested(world, self, entries)
	gangs.on_player_arrested(world, self)
	return {"ok": true, "fine": fine, "paid": paid, "jail_hours": jail, "records": entries, "text": text}


func _player_name(world) -> String:
	var n = A.field(A.player(world), "name", "")
	if str(n) == "":
		n = world.character.get("name", "Spieler") if typeof(world.character) == TYPE_DICTIONARY else "Spieler"
	return str(n)


## Stellt sich der Polizei: Strafe und Haft um 40 % gemindert.
func turn_yourself_in(world) -> Dictionary:
	if in_jail(world):
		return {"ok": false, "reason": "Du sitzt bereits im Gefängnis."}
	if wanted <= 0 and charges.is_empty():
		return {"ok": false, "reason": "Gegen dich liegt nichts vor."}
	var r := arrest_player(world, "Selbstanzeige", 0.6)
	r.text = "Du hast dich gestellt. " + str(r.text)
	return r


func in_jail(world) -> bool:
	return jail_until > 0.0 and world.time_minutes < jail_until


func jail_remaining_minutes(world) -> float:
	return maxf(0.0, jail_until - world.time_minutes) if jail_until > 0.0 else 0.0


## Kaution: Restliche Haft gegen Zahlung an den Staat.
func bail_amount(world) -> float:
	var hours := jail_remaining_minutes(world) / 60.0
	return snappedf(100.0 + hours * 35.0 * (0.5 + 0.5 * strictness(world)), 1.0)


func post_bail(world) -> Dictionary:
	if not in_jail(world):
		return {"ok": false, "reason": "Du bist nicht in Haft."}
	var amount := bail_amount(world)
	var pacc := A.player_account(world)
	var gacc := A.gov_account(world)
	var ok := false
	if gacc >= 0:
		ok = world.ledger.transfer(pacc, gacc, amount, "Kaution")
	else:
		ok = world.ledger.can_pay(pacc, amount) and world.ledger.burn(pacc, amount, "Kaution")
	if not ok:
		return {"ok": false, "reason": "Du kannst die Kaution von %.0f € nicht bezahlen." % amount}
	_release_player(world)
	return {"ok": true, "amount": amount, "text": "Kaution über %.0f € bezahlt – du bist frei." % amount}


## Haft absitzen: liefert die verbleibenden Minuten; die Oberflaeche ruft
## damit Game.skip_time(minutes) auf. Mit advance = true laeuft die Zeit hier.
func serve_sentence(world, advance := false) -> Dictionary:
	if not in_jail(world):
		return {"ok": false, "minutes": 0.0, "reason": "Du bist nicht in Haft."}
	var m := jail_remaining_minutes(world)
	if advance:
		world.advance(m + WorldState.STEP_MINUTES)
	return {"ok": true, "minutes": m, "text": "Du sitzt deine Strafe ab (%.1f Stunden)." % (m / 60.0)}


func _release_player(world) -> void:
	jail_until = 0.0
	jail_reason = ""
	world.notify("Du wurdest aus der Haft entlassen.", "info")


## Aussage gegen die eigene Bande in Haft: Haft halbiert, Bande geschwaecht,
## Spieler wird ausgeschlossen und ist bei der Bande verhasst.
func testify_against_gang(world) -> Dictionary:
	if not in_jail(world):
		return {"ok": false, "reason": "Nur in Haft möglich."}
	if gangs.player_gang < 0:
		return {"ok": false, "reason": "Du gehörst keiner Bande an."}
	var gid := gangs.player_gang
	jail_until = world.time_minutes + jail_remaining_minutes(world) * 0.5
	var r := gangs.betrayed_by_player(world, self, gid)
	return {"ok": true, "text": "Du hast ausgesagt. Deine Haft wird halbiert. " + str(r.get("text", ""))}


## Vorstrafen pruefen. kind: "player" | "npc"/"person"; severity: "" (beliebig),
## "minor", "misdemeanor" oder "felony" (mindestens diese Schwere).
func has_record(kind: String, id: int, severity := "") -> bool:
	var min_sev: int = SEVERITY_ORDER.get(severity, 0)
	var lists := []
	if kind == "player":
		lists.append(record)
	else:
		lists.append(npc_records.get(id, []))
	for l in lists:
		for e in l:
			if typeof(e) == TYPE_DICTIONARY and int(SEVERITY_ORDER.get(str(e.get("severity", "minor")), 0)) >= min_sev:
				return true
	return false


func record_of_player() -> Array:
	return record.duplicate(true)


# ============================================================ Einsaetze

## Einsatzmeldung fuer das Verkehrsmodul (Polizeiautos).
func add_dispatch(pos: Vector2, kind: String, label: String, crime_id := -1, priority := 1) -> void:
	pending_dispatches.append({"id": next_dispatch_id, "pos": pos, "kind": kind, "label": label,
		"crime": crime_id, "priority": priority, "created": 0.0})
	next_dispatch_id += 1
	while pending_dispatches.size() > MAX_DISPATCH:
		pending_dispatches.pop_front()


## Entnimmt bis zu max_n Einsatzmeldungen (aelteste zuerst).
func take_dispatches(max_n := 10) -> Array:
	var out := pending_dispatches.slice(0, max_n)
	pending_dispatches = pending_dispatches.slice(max_n)
	return out


func _is_police_building(world, bid: int) -> bool:
	return str(A.building(world, bid).get("kind", "")) == "police"


func _police_station_near(world, pos: Vector2, radius: float) -> bool:
	if world.city == null:
		return false
	for b in world.city.buildings_of_kind("police"):
		if A.to_v2(b.door).distance_to(pos) <= radius:
			return true
	return false


## Naechste Polizeiwache (Gebaeude-ID) zu einer Position.
func nearest_police_station(world, pos: Vector2) -> int:
	if world.city == null:
		return -1
	var best := -1
	var bd := INF
	for b in world.city.buildings_of_kind("police"):
		var d := A.to_v2(b.door).distance_squared_to(pos)
		if d < bd:
			bd = d
			best = int(b.id)
	return best


# ============================================================ Statistik

## Taten der letzten n Tage je Stadtteil (fuer Karte/Politik).
func district_crime_index(world, did: int) -> float:
	var s: Dictionary = district_stats.get(did, {})
	var d := A.district(world, did)
	var base: float = float(d.get("crime", 0.2))
	if s.is_empty():
		return base
	return clampf(0.5 * base + 0.25 * float(s.get("yesterday", 0)) / 3.0, 0.0, 1.0)


## Taten pro Tag (Durchschnitt der letzten Tage) und Aufklaerungsquote.
func city_stats() -> Dictionary:
	var n := 0
	var crimes_sum := 0
	var solved_sum := 0
	for h in history.slice(0, 7):
		n += 1
		crimes_sum += int(h.crimes)
		solved_sum += int(h.solved)
	var per_day := float(crimes_sum) / float(n) if n > 0 else float(today.crimes)
	return {"crimes_per_day": per_day, "solve_rate": float(solved_sum) / maxf(1.0, float(crimes_sum)),
		"open_cases": _count_open(), "total_npc_crimes": total_npc_crimes, "total_npc_solved": total_npc_solved}


func _count_open() -> int:
	var n := 0
	for id in cases:
		if cases[id].status in ["open", "assigned"]:
			n += 1
	return n


## Uebersicht fuer das Telefon (Fahndung, Haft, Vorstrafen, Statistik).
func player_status(world) -> Dictionary:
	return {"wanted": wanted, "in_jail": in_jail(world), "jail_remaining_hours": jail_remaining_minutes(world) / 60.0,
		"jail_reason": jail_reason, "bail": bail_amount(world) if in_jail(world) else 0.0,
		"charges": charges.size(), "record": record.duplicate(true), "stolen_goods": stolen_goods.duplicate(true),
		"arrest_chance_per_hour": arrest_chance_per_hour(world), "hiding": is_hiding(world),
		"police_effectiveness": police_effectiveness(world), "strictness": strictness(world)}


# ============================================================ Speichern

func to_save() -> Dictionary:
	return A.pack({"rng_state": rng.state, "crimes": crimes.slice(0, 120), "next_crime_id": next_crime_id,
		"cases": cases, "next_case_id": next_case_id, "dispatches": pending_dispatches, "next_dispatch_id": next_dispatch_id,
		"wanted": wanted, "last_seen": last_seen, "charges": charges, "record": record, "jail_until": jail_until,
		"jail_reason": jail_reason, "stolen_goods": stolen_goods, "hiding_until": hiding_until,
		"arrests_of_player": arrests_of_player, "npc_jail": npc_jail, "npc_records": npc_records,
		"district_stats": district_stats, "history": history, "today": today, "police_ref": police_ref,
		"welfare_ref": welfare_ref, "total_npc_crimes": total_npc_crimes, "total_npc_solved": total_npc_solved,
		"police": police.to_save(), "gangs": gangs.to_save()})


func from_save(world, d: Dictionary) -> void:
	var u: Dictionary = A.unpack(d)
	rng.seed = hash("crime:%d" % int(world.seed))
	if u.has("rng_state"):
		rng.state = int(u.rng_state)
	crimes = u.get("crimes", [])
	next_crime_id = int(u.get("next_crime_id", 1))
	cases = A.int_keys(u.get("cases", {}))
	next_case_id = int(u.get("next_case_id", 1))
	pending_dispatches = u.get("dispatches", [])
	next_dispatch_id = int(u.get("next_dispatch_id", 1))
	wanted = int(u.get("wanted", 0))
	last_seen = float(u.get("last_seen", 0.0))
	charges = u.get("charges", [])
	record = u.get("record", [])
	jail_until = float(u.get("jail_until", 0.0))
	jail_reason = str(u.get("jail_reason", ""))
	stolen_goods = u.get("stolen_goods", [])
	hiding_until = float(u.get("hiding_until", 0.0))
	arrests_of_player = int(u.get("arrests_of_player", 0))
	npc_jail = A.int_keys(u.get("npc_jail", {}))
	npc_records = A.int_keys(u.get("npc_records", {}))
	district_stats = A.int_keys(u.get("district_stats", {}))
	history = u.get("history", [])
	today = u.get("today", {"crimes": 0, "solved": 0, "player": 0})
	police_ref = float(u.get("police_ref", -1.0))
	welfare_ref = float(u.get("welfare_ref", -1.0))
	total_npc_crimes = int(u.get("total_npc_crimes", 0))
	total_npc_solved = int(u.get("total_npc_solved", 0))
	police.from_save(world, u.get("police", {}))
	gangs.from_save(world, u.get("gangs", {}))
