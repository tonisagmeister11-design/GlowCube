class_name PoliceCareer
extends RefCounted
## Polizei-Laufbahn des Spielers: Eintritt, Dienstgrade, Streife, Ermittlungen,
## Festnahmen. Haengt an CrimeSystem (`world.crime.police`) und arbeitet mit
## dessen Faellen (`crime.cases`) und Taten der NPCs.
##
## JobMarket-Anbindung: Gibt es in Occupations.OCCUPATIONS einen Polizeiberuf
## (ID enthaelt "police" oder Name enthaelt "Polizei"), erfolgt der Eintritt
## ueber `jobs.list_openings(world, "player", 0)` + `jobs.apply(...)`; der Lohn
## kommt dann vom JobMarket. Ohne Polizeiberuf bezahlt dieses Modul den
## Dienst direkt aus dem Staatskonto (Ledger). Dienstgrad und Leistung
## fuehrt immer dieses Modul (Spiegel: player.police_rank).

const A := preload("res://scripts/crime/contract_access.gd")

## Dienstgrade mit Bedingungen: perf (Leistung 0..100), solved (aufgeklaerte
## Faelle), arrests (Festnahmen), days (Tage im Dienstgrad), education.
const RANKS := [
	{"title": "Polizeianwärter", "wage": 14.0, "education": "school", "perf": 0.0, "solved": 0, "arrests": 0, "days": 0},
	{"title": "Polizeimeister", "wage": 18.0, "education": "school", "perf": 45.0, "solved": 1, "arrests": 2, "days": 1},
	{"title": "Polizeiobermeister", "wage": 21.0, "education": "school", "perf": 55.0, "solved": 3, "arrests": 5, "days": 1},
	{"title": "Kommissar", "wage": 26.0, "education": "vocational", "perf": 62.0, "solved": 6, "arrests": 8, "days": 2},
	{"title": "Hauptkommissar", "wage": 32.0, "education": "vocational", "perf": 70.0, "solved": 10, "arrests": 12, "days": 2},
	{"title": "Polizeirat", "wage": 40.0, "education": "bachelor", "perf": 78.0, "solved": 15, "arrests": 16, "days": 3},
	{"title": "Polizeipräsident", "wage": 55.0, "education": "master", "perf": 85.0, "solved": 22, "arrests": 22, "days": 3},
]
const MIN_AGE := 18.0
const MIN_HEALTH := 50.0
const MIN_FITNESS := 15.0
const MAX_ASSIGNED := 3

var rng := RandomNumberGenerator.new()
var member := false
var rank := 0
var performance := 50.0
var cases_solved := 0
var arrests := 0
var patrol_hours := 0.0
var joined_day := -1
var rank_since_day := 0
var last_duty_day := -99
var via_jobs := false
var occupation_id := ""
var assigned: Array = []
var patrol_boost: Dictionary = {}     # district id -> Spielminute (Ende der Streife)
var dismissed_reason := ""
var duty_log: Array = []              # {day, text}


func setup(world, _crime) -> void:
	rng.seed = hash("police:%d" % int(world.seed))
	occupation_id = police_occupation_id()


func post_load(_world, _crime) -> void:
	occupation_id = police_occupation_id()


## Sucht einen Polizeiberuf im Berufskatalog (falls vorhanden).
static func police_occupation_id() -> String:
	var path := "res://scripts/jobs/occupations.gd"
	if not ResourceLoader.exists(path):
		return ""
	var scr = load(path)
	if scr == null:
		return ""
	if not (scr is Script):
		return ""
	var consts: Dictionary = scr.get_script_constant_map()
	var table = consts.get("OCCUPATIONS", {})
	if typeof(table) != TYPE_DICTIONARY:
		return ""
	var best := ""
	var best_edu := 99
	for id in table:
		var o = table[id]
		var name := str(o.get("name", "")) if typeof(o) == TYPE_DICTIONARY else ""
		if str(id).to_lower().contains("police") or name.contains("Polizei"):
			var e := A.edu_rank(str(o.get("education", "none")))
			if e < best_edu:
				best_edu = e
				best = str(id)
	return best


func title() -> String:
	return str(RANKS[rank].title) if member else ""


## Kleiner Bonus auf die Polizeiwirkung, solange der Spieler aktiv Dienst tut.
func effect_bonus() -> float:
	return 0.01 * float(rank + 1) if member else 0.0


## Faktor auf NPC-Taten in einem Stadtteil waehrend einer Streife.
func patrol_factor(world, did: int) -> float:
	if patrol_boost.has(did) and float(patrol_boost[did]) > world.time_minutes:
		return 0.6
	return 1.0


# ============================================================ Eintritt

func join_requirements(world) -> Array:
	var crime = world.crime
	var age := A.player_age(world)
	var felony: bool = crime != null and crime.has_record("player", A.PLAYER_ID, "felony")
	var edu := A.player_education(world)
	var health := A.player_need(world, "health", 100.0)
	var fitness := A.player_skill(world, "fitness", 20.0)
	var wanted: int = crime.wanted if crime != null else 0
	var jailed: bool = crime != null and crime.in_jail(world)
	var gang_member: bool = crime != null and crime.gangs.player_gang >= 0
	return [
		{"label": "Mindestalter %d Jahre" % int(MIN_AGE), "met": age >= MIN_AGE, "value": age, "target": MIN_AGE},
		{"label": "Keine Vorstrafe wegen eines Verbrechens", "met": not felony, "value": 1.0 if felony else 0.0, "target": 0.0},
		{"label": "Schulabschluss", "met": A.edu_rank(edu) >= A.edu_rank("school"), "value": float(A.edu_rank(edu)), "target": float(A.edu_rank("school"))},
		{"label": "Gesundheit mindestens %d" % int(MIN_HEALTH), "met": health >= MIN_HEALTH, "value": health, "target": MIN_HEALTH},
		{"label": "Fitness mindestens %d" % int(MIN_FITNESS), "met": fitness >= MIN_FITNESS, "value": fitness, "target": MIN_FITNESS},
		{"label": "Nicht zur Fahndung ausgeschrieben, nicht in Haft", "met": wanted == 0 and not jailed, "value": float(wanted), "target": 0.0},
		{"label": "Keine Bandenmitgliedschaft", "met": not gang_member, "value": 1.0 if gang_member else 0.0, "target": 0.0},
	]


func join(world) -> Dictionary:
	if member:
		return {"ok": false, "reason": "Du bist bereits im Polizeidienst."}
	for r in join_requirements(world):
		if not r.met:
			return {"ok": false, "reason": "Bedingung nicht erfüllt: %s." % r.label}
	via_jobs = false
	var jobs = A.field(world, "jobs")
	if occupation_id != "" and A.has_m(jobs, "list_openings") and A.has_m(jobs, "apply"):
		var openings = jobs.list_openings(world, "player", A.PLAYER_ID)
		var reason := "Derzeit keine freie Stelle bei der Polizei."
		if typeof(openings) == TYPE_ARRAY:
			for o in openings:
				if typeof(o) != TYPE_DICTIONARY or str(o.get("occupation", "")) != occupation_id:
					continue
				var pos_id := int(o.get("id", o.get("position", o.get("position_id", -1))))
				var r = jobs.apply(world, "player", A.PLAYER_ID, pos_id)
				if typeof(r) == TYPE_DICTIONARY and bool(r.get("ok", false)):
					via_jobs = true
					break
				if typeof(r) == TYPE_DICTIONARY:
					reason = str(r.get("reason", reason))
		if not via_jobs:
			return {"ok": false, "reason": reason}
	member = true
	rank = 0
	performance = 50.0
	joined_day = world.get_day()
	rank_since_day = joined_day
	last_duty_day = joined_day
	dismissed_reason = ""
	_sync_player(world)
	_log(world, "Eintritt in den Polizeidienst als %s." % title())
	world.notify("Willkommen bei der Polizei, %s." % title(), "success")
	return {"ok": true, "text": "Du bist jetzt %s." % title(), "via_jobs": via_jobs}


func quit(world, reason := "Kündigung") -> Dictionary:
	if not member:
		return {"ok": false, "reason": "Du bist nicht im Polizeidienst."}
	_leave(world, reason)
	return {"ok": true, "text": "Du hast den Polizeidienst verlassen."}


func _leave(world, reason: String) -> void:
	member = false
	dismissed_reason = reason
	var jobs = A.field(world, "jobs")
	if via_jobs and A.has_m(jobs, "quit"):
		jobs.quit(world, "player", A.PLAYER_ID)
	via_jobs = false
	for cid in assigned:
		var c: Dictionary = world.crime.cases.get(cid, {}) if world.crime != null else {}
		if not c.is_empty() and c.status == "assigned":
			c.status = "open"
			c.assigned_player = false
	assigned.clear()
	_sync_player(world)
	_log(world, "Ausscheiden aus dem Polizeidienst: %s." % reason)


func _sync_player(world) -> void:
	var pl = A.player(world)
	var cur = A.field(pl, "police_rank", null)
	if typeof(cur) == TYPE_STRING:
		A.set_field(pl, "police_rank", title())
	else:
		A.set_field(pl, "police_rank", rank if member else -1)


func _log(world, text: String) -> void:
	duty_log.push_front({"day": world.get_day(), "text": text})
	if duty_log.size() > 40:
		duty_log.pop_back()


# ============================================================ Dienstgrade

func rank_requirements(world) -> Dictionary:
	if not member:
		return {"member": false, "next_title": RANKS[0].title, "requirements": join_requirements(world), "can_promote": false}
	if rank >= RANKS.size() - 1:
		return {"member": true, "title": title(), "next_title": "", "requirements": [], "can_promote": false}
	var nr: Dictionary = RANKS[rank + 1]
	var edu := A.player_education(world)
	var dd: int = world.get_day() - rank_since_day
	var reqs := [
		{"label": "Leistung", "value": performance, "target": float(nr.perf), "met": performance >= float(nr.perf)},
		{"label": "Aufgeklärte Fälle", "value": float(cases_solved), "target": float(nr.solved), "met": cases_solved >= int(nr.solved)},
		{"label": "Festnahmen", "value": float(arrests), "target": float(nr.arrests), "met": arrests >= int(nr.arrests)},
		{"label": "Tage im Dienstgrad", "value": float(dd), "target": float(nr.days), "met": dd >= int(nr.days)},
		{"label": "Abschluss: %s" % A.EDU_LABELS.get(nr.education, nr.education), "value": float(A.edu_rank(edu)),
			"target": float(A.edu_rank(str(nr.education))), "met": A.edu_rank(edu) >= A.edu_rank(str(nr.education))},
	]
	var ok := true
	for r in reqs:
		ok = ok and bool(r.met)
	return {"member": true, "title": title(), "next_title": nr.title, "requirements": reqs, "can_promote": ok}


func check_promotion(world) -> bool:
	if not member:
		return false
	var rr := rank_requirements(world)
	if not rr.can_promote:
		return false
	rank += 1
	rank_since_day = world.get_day()
	_sync_player(world)
	_log(world, "Beförderung zum %s." % title())
	world.notify("Beförderung: Du bist jetzt %s." % title(), "success")
	if rank == RANKS.size() - 1:
		world.post_news("Polizei", "Neuer Polizeipräsident: %s übernimmt die Leitung der Polizei." % _player_name(world))
	return true


func _player_name(world) -> String:
	var n = A.field(A.player(world), "name", "")
	if str(n) == "" and typeof(world.character) == TYPE_DICTIONARY:
		n = world.character.get("name", "")
	return str(n) if str(n) != "" else "Ein Beamter"


func status(world) -> Dictionary:
	return {"member": member, "rank": rank, "title": title(), "performance": performance, "cases_solved": cases_solved,
		"arrests": arrests, "patrol_hours": patrol_hours, "via_jobs": via_jobs, "occupation": occupation_id,
		"assigned": assigned.duplicate(), "dismissed_reason": dismissed_reason, "log": duty_log.duplicate(true),
		"wage_per_hour": float(RANKS[rank].wage) * _wage_level(world) if member else 0.0,
		"next": rank_requirements(world)}


func _wage_level(world) -> float:
	return world.rules.f("wage_level") if world.rules != null else 1.0


func _duty_check(world) -> String:
	if not member:
		return "Du bist nicht im Polizeidienst."
	var crime = world.crime
	if crime != null and crime.in_jail(world):
		return "Du sitzt im Gefängnis."
	if crime != null and crime.wanted > 0:
		return "Du wirst selbst gesucht."
	return ""


# ============================================================ Dienst

## Streife in einem Stadtteil (Standard: Stadtteil mit der hoechsten
## Kriminalitaet). Senkt dort waehrenddessen die NPC-Taten, kann Taeter auf
## frischer Tat stellen und Spuren zu offenen Faellen finden.
## advance = true: die Zeit laeuft hier (sonst ruft die Oberflaeche
## Game.skip_time(minutes) auf).
func patrol(world, hours: float, advance := true, district_id := -1) -> Dictionary:
	var err := _duty_check(world)
	if err != "":
		return {"ok": false, "reason": err}
	var crime = world.crime
	hours = clampf(hours, 1.0, 12.0)
	var did := district_id
	if did < 0:
		var best := -1.0
		for d in world.city.districts:
			var ci: float = crime.district_crime_index(world, int(d.id))
			if ci > best:
				best = ci
				did = int(d.id)
	var dist := A.district(world, did)
	patrol_boost[did] = world.time_minutes + hours * 60.0
	crime.add_dispatch(A.to_v2(dist.get("center", Vector2.ZERO)), "patrol", "Streife in %s" % dist.get("name", ""), -1, 0)
	if advance:
		world.advance(hours * 60.0)
	var events := []
	var made_arrests := 0
	for h in int(ceil(hours)):
		var p_enc := 0.15 + float(dist.get("crime", 0.2)) * 0.5
		if rng.randf() >= p_enc:
			continue
		var pool: Array = crime.criminals_in_district(did)
		var perp := -1
		for tries in 4:
			if pool.is_empty():
				break
			var c: int = pool[rng.randi_range(0, pool.size() - 1)]
			if not crime.is_npc_jailed(c):
				perp = c
				break
		if perp >= 0 and rng.randf() < 0.5:
			var type: String = ["pickpocket", "shoplifting", "vandalism", "car_theft"][rng.randi_range(0, 3)]
			crime.convict_npc(world, perp, type)
			arrests += 1
			made_arrests += 1
			performance = minf(100.0, performance + 3.0)
			events.append("%s auf frischer Tat gestellt (%s)." % [A.person_name(world, perp), crime.TYPES[type].label])
		else:
			for cid in crime.cases:
				var cs: Dictionary = crime.cases[cid]
				if int(cs.district) == did and cs.status in ["open", "assigned"] and cs.suspect_kind != "player":
					cs.evidence = clampf(float(cs.evidence) + 0.15, 0.0, 1.0)
					events.append("Spur im Fall %s gefunden." % cs.label)
					break
	patrol_hours += hours
	performance = minf(100.0, performance + 0.5 * hours)
	last_duty_day = world.get_day()
	var pay := _pay(world, hours)
	check_promotion(world)
	var text := "Streife in %s beendet (%.0f Stunden)." % [dist.get("name", ""), hours]
	if events.is_empty():
		text += " Ruhiger Dienst."
	return {"ok": true, "text": text, "events": events, "arrests": made_arrests, "pay": pay, "minutes": hours * 60.0,
		"district": did}


func _pay(world, hours: float) -> float:
	if via_jobs:
		return 0.0
	var gacc := A.gov_account(world)
	var pacc := A.player_account(world)
	if gacc < 0 or pacc < 0:
		return 0.0
	var amount := snappedf(float(RANKS[rank].wage) * _wage_level(world) * hours, 0.01)
	return world.ledger.transfer_partial(gacc, pacc, amount, "Polizeidienst")


## Uebernimmt einen offenen Fall.
func take_case(world, case_id: int) -> Dictionary:
	var err := _duty_check(world)
	if err != "":
		return {"ok": false, "reason": err}
	var c: Dictionary = world.crime.cases.get(case_id, {})
	if c.is_empty() or c.status != "open" or c.suspect_kind == "player":
		return {"ok": false, "reason": "Fall nicht verfügbar."}
	if assigned.size() >= MAX_ASSIGNED:
		return {"ok": false, "reason": "Du bearbeitest bereits %d Fälle." % MAX_ASSIGNED}
	c.status = "assigned"
	c.assigned_player = true
	assigned.append(case_id)
	return {"ok": true, "text": "Fall übernommen: %s in %s." % [c.label, c.district_name]}


## Ermittelt in einem eigenen Fall: Beweise steigen (Faehigkeiten, Dienstgrad,
## Polizeiwirkung). Ab 60 % ist der Tatverdaechtige bekannt.
func investigate(world, case_id: int, hours := 2.0, advance := true) -> Dictionary:
	var err := _duty_check(world)
	if err != "":
		return {"ok": false, "reason": err}
	var crime = world.crime
	var c: Dictionary = crime.cases.get(case_id, {})
	if c.is_empty():
		return {"ok": false, "reason": "Unbekannter Fall."}
	if c.status == "open":
		var t := take_case(world, case_id)
		if not t.ok:
			return t
	if c.status != "assigned" or not bool(c.assigned_player):
		return {"ok": false, "reason": "Der Fall ist nicht in deiner Bearbeitung."}
	hours = clampf(hours, 0.5, 8.0)
	if advance:
		world.advance(hours * 60.0)
	var skill := maxf(A.player_skill(world, "logic", 10.0), A.player_skill(world, "intelligence", 10.0))
	var gain := rng.randf_range(0.08, 0.2) * (hours / 2.0) * (1.0 + skill / 100.0 + rank * 0.05) * sqrt(crime.police_effectiveness(world))
	c.evidence = clampf(float(c.evidence) + gain, 0.0, 1.0)
	if int(c.suspect) < 0 and float(c.evidence) >= crime.PROSECUTE_EVIDENCE:
		var pool: Array = crime.criminals_in_district(int(c.district))
		if not pool.is_empty():
			c.suspect = pool[rng.randi_range(0, pool.size() - 1)]
	performance = minf(100.0, performance + 0.5)
	last_duty_day = world.get_day()
	_pay(world, hours)
	var known: bool = float(c.evidence) >= crime.PROSECUTE_EVIDENCE and int(c.suspect) >= 0
	var text := "Ermittlungen: Beweislage %d %%." % roundi(float(c.evidence) * 100.0)
	if known:
		text += " Tatverdächtig: %s." % A.person_name(world, int(c.suspect))
	return {"ok": true, "evidence": float(c.evidence), "suspect_known": known,
		"suspect_name": A.person_name(world, int(c.suspect)) if known else "Unbekannt", "text": text, "minutes": hours * 60.0}


## Festnahme des Tatverdaechtigen eines eigenen Falls.
func arrest_suspect(world, case_id: int) -> Dictionary:
	var err := _duty_check(world)
	if err != "":
		return {"ok": false, "text": err, "reason": err}
	var crime = world.crime
	var c: Dictionary = crime.cases.get(case_id, {})
	if c.is_empty() or c.status != "assigned" or not bool(c.assigned_player):
		return {"ok": false, "text": "Der Fall ist nicht in deiner Bearbeitung.", "reason": "nicht zugewiesen"}
	if float(c.evidence) < crime.PROSECUTE_EVIDENCE or int(c.suspect) < 0:
		return {"ok": false, "text": "Die Beweise reichen nicht aus (mindestens %d %%)." % roundi(crime.PROSECUTE_EVIDENCE * 100.0), "reason": "Beweise"}
	var sid := int(c.suspect)
	if crime.is_npc_jailed(sid) or not A.person_alive(world, sid):
		c.status = "solved"
		assigned.erase(case_id)
		return {"ok": false, "text": "Der Tatverdächtige ist nicht mehr auffindbar. Fall abgeschlossen.", "reason": "nicht auffindbar"}
	var chance := 0.55 + float(c.evidence) * 0.3 + rank * 0.03 + A.player_skill(world, "fitness", 20.0) / 400.0
	if rng.randf() < clampf(chance, 0.1, 0.97):
		crime.convict_npc(world, sid, str(c.type))
		c.status = "solved"
		var cr: Dictionary = crime.get_crime(int(c.crime))
		if not cr.is_empty():
			cr.solved = true
		crime.today.solved = int(crime.today.solved) + 1
		crime._district_solved(int(c.district))
		assigned.erase(case_id)
		arrests += 1
		cases_solved += 1
		performance = minf(100.0, performance + 6.0)
		last_duty_day = world.get_day()
		var promoted := check_promotion(world)
		var name := A.person_name(world, sid)
		world.post_news("Polizei", "%s %s nimmt Tatverdächtigen im Fall %s fest." % [title(), _player_name(world), c.label])
		var txt := "%s wurde festgenommen. Fall aufgeklärt." % name
		if promoted:
			txt += " Du wurdest zum %s befördert." % title()
		return {"ok": true, "text": txt, "promoted": promoted}
	c.evidence = maxf(0.0, float(c.evidence) - 0.2)
	performance = maxf(0.0, performance - 2.0)
	crime.add_dispatch(A.to_v2(c.pos), "pursuit", "Flüchtiger Tatverdächtiger", int(c.crime), 2)
	return {"ok": false, "text": "Der Tatverdächtige ist entkommen. Neue Beweise nötig.", "reason": "entkommen"}


# ============================================================ Ereignisse

func on_player_arrested(world, _crime, entries: Array) -> void:
	if not member:
		return
	var felony := false
	for e in entries:
		if str(e.get("severity", "")) == "felony":
			felony = true
	if felony:
		_leave(world, "Entlassung wegen einer Straftat")
		world.notify("Du wurdest aus dem Polizeidienst entlassen.", "danger")
	else:
		performance = maxf(0.0, performance - 15.0)


func on_day(world, crime, day: int) -> void:
	if not member:
		return
	# JobMarket: wurde die Stelle dort beendet?
	var jobs = A.field(world, "jobs")
	if via_jobs and A.has_m(jobs, "job_of"):
		var j = jobs.job_of("player", A.PLAYER_ID)
		if typeof(j) == TYPE_DICTIONARY and str(j.get("occupation", "")) != occupation_id:
			via_jobs = false
			_leave(world, "Stelle beendet")
			return
	# Korruption: Doppelleben als Polizist und Bandenmitglied
	if crime != null and crime.gangs.player_gang >= 0 and rng.randf() < 0.08:
		var entry := {"type": "corruption", "label": "Bestechlichkeit", "severity": "felony", "day": day, "fine": 0.0, "jail_hours": 0.0}
		crime.record.append(entry)
		A.add_record(A.player(world), entry)
		_leave(world, "Verbindung zu einer Bande aufgedeckt")
		world.post_news("Polizei", "Skandal: Beamter mit Verbindungen zu %s entlassen." % crime.gangs.gangs[crime.gangs.player_gang].name)
		world.notify("Deine Verbindung zur Bande wurde aufgedeckt. Du bist entlassen.", "danger")
		return
	if day - last_duty_day > 2:
		performance = maxf(0.0, performance - 1.5)
	var expired := []
	for did in patrol_boost:
		if float(patrol_boost[did]) < world.time_minutes:
			expired.append(did)
	for did in expired:
		patrol_boost.erase(did)


# ============================================================ Speichern

func to_save() -> Dictionary:
	return {"rng_state": rng.state, "member": member, "rank": rank, "performance": performance, "cases_solved": cases_solved,
		"arrests": arrests, "patrol_hours": patrol_hours, "joined_day": joined_day, "rank_since_day": rank_since_day,
		"last_duty_day": last_duty_day, "via_jobs": via_jobs, "assigned": assigned, "patrol_boost": patrol_boost,
		"dismissed_reason": dismissed_reason, "log": duty_log}


func from_save(world, d: Dictionary) -> void:
	rng.seed = hash("police:%d" % int(world.seed))
	if d.has("rng_state"):
		rng.state = int(d.rng_state)
	member = bool(d.get("member", false))
	rank = int(d.get("rank", 0))
	performance = float(d.get("performance", 50.0))
	cases_solved = int(d.get("cases_solved", 0))
	arrests = int(d.get("arrests", 0))
	patrol_hours = float(d.get("patrol_hours", 0.0))
	joined_day = int(d.get("joined_day", -1))
	rank_since_day = int(d.get("rank_since_day", 0))
	last_duty_day = int(d.get("last_duty_day", -99))
	via_jobs = bool(d.get("via_jobs", false))
	assigned = d.get("assigned", [])
	patrol_boost = A.int_keys(d.get("patrol_boost", {}))
	dismissed_reason = str(d.get("dismissed_reason", ""))
	duty_log = d.get("log", [])
	occupation_id = police_occupation_id()
