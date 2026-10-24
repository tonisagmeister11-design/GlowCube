class_name Gangs
extends RefCounted
## Kriminelle Organisationen (alle Namen frei erfunden): Reviere, Verstecke,
## Mitglieder (NPC-Personen), Bandenkasse (Ledger-Konto "gang"), Stärke,
## Ruf des Spielers, Rangordnung und generierte Aufträge.
##
## Laufbahn des Spielers: Kontakt im Versteck -> Aufnahmeauftrag -> Mitläufer
## -> ... -> Unterboss -> Boss/Pate. Jede Stufe hat nachvollziehbare
## Bedingungen (rank_requirements); die Spitze erfordert eine Entscheidung:
## den Boss herausfordern oder ihm nachfolgen, wenn er verhaftet wurde oder
## sich zurückzieht.

const A := preload("res://scripts/crime/contract_access.gd")

const RANKS := ["Mitläufer", "Kurier", "Soldat", "Vollstrecker", "Leutnant", "Capo", "Unterboss", "Boss"]
const BOSS_RANK := 7
const UNDERBOSS_RANK := 6

## Bedingungen je Rang (Index = Zielrang). rep: Ruf bei der Bande (0..100),
## missions: erledigte Aufträge, loyalty: Loyalität (0..100), trust: Vertrauen
## des Bosses (Beziehung), days: Tage im aktuellen Rang, heavy: harte Aufträge
## (Schutzgeld/Raub/Revier), recruits: angeworbene Mitglieder, defends:
## erfolgreich verteidigte Reviere.
const RANK_REQ := [
	{},
	{"rep": 12.0, "missions": 3, "loyalty": 0.0, "trust": 0.0, "days": 0},
	{"rep": 25.0, "missions": 6, "loyalty": 40.0, "trust": 0.0, "days": 1},
	{"rep": 38.0, "missions": 10, "loyalty": 48.0, "trust": 10.0, "days": 1, "heavy": 2},
	{"rep": 52.0, "missions": 15, "loyalty": 58.0, "trust": 20.0, "days": 1, "heavy": 4},
	{"rep": 66.0, "missions": 20, "loyalty": 66.0, "trust": 32.0, "days": 1, "recruits": 1},
	{"rep": 80.0, "missions": 26, "loyalty": 75.0, "trust": 45.0, "days": 1, "defends": 2},
	{"rep": 92.0, "missions": 32, "loyalty": 82.0, "trust": 55.0, "days": 1, "decision": true},
]

const DEFS := [
	{"key": "rostgraben", "name": "Rostgraben-Crew", "kind": "Straßengang", "boss_title": "Boss",
		"description": "Junge Straßengang aus den Brennpunkten. Diebstahl, Autos, Revierkämpfe.",
		"zones": ["slum", "poor", "highrise"], "hideout_kinds": ["abandoned", "homeless_camp", "tenement"],
		"hideout_biz": ["convenience", "secondhand"], "capital": 4000.0, "strength": 42.0, "max_districts": 3},
	{"key": "moewen", "name": "Schwarze Möwen", "kind": "Schmugglerring", "boss_title": "Boss",
		"description": "Schmuggler aus dem Hafen. Kontrollieren Lagerhallen und den illegalen Handel.",
		"zones": ["industrial", "poor"], "hideout_kinds": ["warehouse", "factory"],
		"hideout_biz": ["gas_station"], "capital": 12000.0, "strength": 50.0, "max_districts": 2},
	{"key": "neon", "name": "Neon-Syndikat", "kind": "Schutzgeldsyndikat", "boss_title": "Boss",
		"description": "Organisierte Erpresser im Nachtleben der Innenstadt.",
		"zones": ["downtown", "commercial", "highrise", "university", "apartments"], "hideout_kinds": [],
		"hideout_biz": ["bar", "night_market", "fastfood", "cinema", "cafe"], "capital": 20000.0, "strength": 55.0, "max_districts": 3},
	{"key": "valcorvo", "name": "Famiglia Valcorvo", "kind": "Mafia-Familie", "boss_title": "Pate",
		"description": "Alte, verschwiegene Familie. Restaurants, Bauaufträge, Einfluss bis ins Rathaus.",
		"zones": ["oldtown", "business", "commercial", "apartments"], "hideout_kinds": ["restaurant"],
		"hideout_biz": ["restaurant", "cafe", "bakery"], "capital": 40000.0, "strength": 62.0, "max_districts": 3},
]

## Auftragsarten. steps: Abfolge der Ereignisse fuer progress_mission.
const MISSION_TYPES := {
	"initiation": {"label": "Aufnahmeprüfung", "min_rank": -1, "hours": 12.0, "reward": [50.0, 100.0], "rep": 10.0, "risk": 0.1},
	"delivery": {"label": "Paket ausliefern", "min_rank": 0, "hours": 6.0, "reward": [120.0, 300.0], "rep": 4.0, "risk": 0.15},
	"collect": {"label": "Schutzgeld eintreiben", "min_rank": 1, "hours": 8.0, "reward": [0.0, 0.0], "rep": 5.0, "risk": 0.3},
	"car_theft": {"label": "Auto stehlen", "min_rank": 1, "hours": 8.0, "reward": [250.0, 500.0], "rep": 5.0, "risk": 0.35},
	"robbery": {"label": "Laden ausrauben", "min_rank": 2, "hours": 10.0, "reward": [100.0, 250.0], "rep": 7.0, "risk": 0.5},
	"defend": {"label": "Revier verteidigen", "min_rank": 2, "hours": 6.0, "reward": [200.0, 400.0], "rep": 6.0, "risk": 0.4},
	"recruit": {"label": "Mitglied anwerben", "min_rank": 3, "hours": 24.0, "reward": [150.0, 250.0], "rep": 5.0, "risk": 0.05},
}
const STEP_LABELS := {"pickup": "Paket im Versteck abholen", "deliver": "Abliefern", "collect": "Schutzgeld kassieren",
	"steal": "Auto stehlen", "rob": "Überfall durchführen", "fight": "Gegner vertreiben", "talk": "Anwerben"}
const HEAVY_TYPES := ["collect", "robbery", "defend"]
const MAX_ACTIVE := 2

var rng := RandomNumberGenerator.new()
var gangs: Dictionary = {}            # id -> Bande
var missions: Dictionary = {}         # id -> Auftrag
var next_mission_id := 1
var _seed := 0

# ---- Spieler
var player_gang := -1
var rank := 0
var rep: Dictionary = {}              # gang id -> Ruf (-100..100)
var trust: Dictionary = {}            # gang id -> Vertrauen des Bosses (0..100)
var loyalty := 0.0
var missions_done := 0
var missions_by_type: Dictionary = {}
var recruits := 0
var defends := 0
var joined_day := -1
var rank_since_day := 0
var last_mission_day := 0
var is_boss := false
var decisions: Array = []             # {day, text}
var expelled_from: Array = []


# ============================================================ Aufbau

func setup(world, crime) -> void:
	_seed = int(world.seed)
	rng.seed = hash("gangs:%d" % _seed)
	gangs.clear()
	var taken := {}
	var gid := 1
	for def in DEFS:
		var g := {"id": gid, "key": def.key, "name": def.name, "kind": def.kind, "description": def.description,
			"boss_title": def.boss_title, "territory": [], "hideout": -1, "members": [], "leader": -1,
			"leader_name": _random_name(), "account": -1, "strength": float(def.strength), "heat": 0.0,
			"vacancy": false, "vacancy_since": -1, "income_today": 0.0, "ranks": {}}
		g.territory = _pick_territory(world, def, taken)
		for did in g.territory:
			taken[did] = gid
		g.hideout = _pick_hideout(world, def, g.territory)
		if world.ledger != null:
			g.account = world.ledger.open_account("gang", gid, str(def.name), float(def.capital))
		gangs[gid] = g
		rep[gid] = 0.0
		trust[gid] = 0.0
		gid += 1
	_assign_members(world, crime)


func post_load(_world, _crime) -> void:
	pass


func _random_name() -> String:
	var first: Array = CityCatalog.FIRST_NAMES_M
	var last: Array = CityCatalog.LAST_NAMES
	return "%s %s" % [first[rng.randi_range(0, first.size() - 1)], last[rng.randi_range(0, last.size() - 1)]]


func _pick_territory(world, def: Dictionary, taken: Dictionary) -> Array:
	var out := []
	if world.city == null:
		return out
	var cands := []
	for d in world.city.districts:
		if taken.has(int(d.id)) or str(d.zone) in ["park", "civic", "estates"]:
			continue
		if str(d.zone) in def.zones:
			cands.append(d)
	cands.sort_custom(func(a, b): return float(a.crime) > float(b.crime) or (float(a.crime) == float(b.crime) and int(a.id) < int(b.id)))
	for d in cands:
		if out.size() >= int(def.max_districts):
			break
		out.append(int(d.id))
	if out.is_empty():
		# Ausweichen: kriminellster freier Stadtteil
		var best := {}
		for d in world.city.districts:
			if taken.has(int(d.id)) or str(d.zone) in ["park", "civic"]:
				continue
			if best.is_empty() or float(d.crime) > float(best.crime):
				best = d
		if not best.is_empty():
			out.append(int(best.id))
	return out


func _pick_hideout(world, def: Dictionary, territory: Array) -> int:
	if world.city == null:
		return -1
	var first_any := -1
	var anywhere := -1
	for b in world.city.buildings:
		var in_terr: bool = int(b.get("district", -1)) in territory
		var fits: bool = str(b.kind) in def.hideout_kinds or str(b.business_type) in def.hideout_biz
		if in_terr and fits:
			return int(b.id)
		if first_any < 0 and in_terr and not (str(b.kind) in ["house", "villa", "estate", "townhouse"]):
			first_any = int(b.id)
		if anywhere < 0 and fits:
			anywhere = int(b.id)
	if first_any >= 0:
		return first_any
	if anywhere >= 0:
		return anywhere
	for k in ["abandoned", "warehouse", "factory"]:
		var l: Array = world.city.buildings_of_kind(k)
		if not l.is_empty():
			return int(l[0].id)
	return 0 if world.city.buildings.size() > 0 else -1


func _assign_members(world, crime) -> void:
	var all := A.persons(world)
	var owner := {}
	for gid in gangs:
		for did in gangs[gid].territory:
			owner[did] = gid
	var ids := all.keys()
	ids.sort()
	for pid in ids:
		var p = all[pid]
		if typeof(p) != TYPE_DICTIONARY or not bool(p.get("alive", true)):
			continue
		if A.person_age(world, p) < 16.0:
			continue
		var c := A.person_trait(p, "criminal")
		if c < 0.55:
			continue
		var did: int = crime._person_district(world, p, int(pid)) if crime != null else -1
		var gid := int(owner.get(did, -1))
		if gid < 0 and c >= 0.75 and rng.randf() < 0.35:
			gid = gangs.keys()[rng.randi_range(0, gangs.size() - 1)]
		if gid < 0 or rng.randf() > 0.75:
			continue
		p["gang"] = gid
		gangs[gid].members.append(int(pid))
	for gid in gangs:
		_choose_leader(world, gangs[gid])


func _choose_leader(world, g: Dictionary) -> void:
	var best := -1
	var bs := -1.0
	for pid in g.members:
		var p := A.person(world, pid)
		if p.is_empty() or not bool(p.get("alive", true)):
			continue
		var s := A.person_trait(p, "criminal") + A.person_trait(p, "ambitious") + A.person_age(world, p) / 200.0
		if s > bs:
			bs = s
			best = pid
	g.leader = best
	if best >= 0:
		g.leader_name = A.person_name(world, best)
	g.vacancy = false
	g.vacancy_since = -1


# ============================================================ Abfragen

func get_gang(gid: int) -> Dictionary:
	return gangs.get(gid, {})


func gang_account(gid: int) -> int:
	return int(gangs.get(gid, {}).get("account", -1))


func district_owner(did: int) -> int:
	for gid in gangs:
		if did in gangs[gid].territory:
			return gid
	return -1


func gang_strength_in(did: int) -> float:
	var gid := district_owner(did)
	return float(gangs[gid].strength) if gid >= 0 else 0.0


func strongest_gang() -> int:
	var best := -1
	for gid in gangs:
		if best < 0 or float(gangs[gid].strength) > float(gangs[best].strength):
			best = gid
	return best


func rank_title(gid: int, r: int) -> String:
	if r == BOSS_RANK:
		return str(gangs.get(gid, {}).get("boss_title", "Boss"))
	return RANKS[clampi(r, 0, RANKS.size() - 1)]


func get_rep(gid: int) -> float:
	return float(rep.get(gid, 0.0))


func add_rep(_world, gid: int, delta: float) -> void:
	rep[gid] = clampf(get_rep(gid) + delta, -100.0, 100.0)


## Vertrauen des Bosses: interner Wert oder Beziehung des Spielers zum Anführer.
func boss_trust(world, gid: int) -> float:
	var t := float(trust.get(gid, 0.0))
	var g := get_gang(gid)
	if not g.is_empty() and int(g.leader) >= 0:
		t = maxf(t, A.player_relationship(world, int(g.leader)))
	return t


## Uebersicht aller Banden fuer die Oberflaeche.
func overview(world) -> Array:
	var out := []
	var ids := gangs.keys()
	ids.sort()
	for gid in ids:
		var g: Dictionary = gangs[gid]
		var terr := []
		for did in g.territory:
			terr.append(str(A.district(world, did).get("name", "?")))
		var hb := A.building(world, int(g.hideout))
		out.append({"id": gid, "name": g.name, "kind": g.kind, "description": g.description,
			"territory": g.territory.duplicate(), "territory_names": terr, "hideout": int(g.hideout),
			"hideout_name": str(hb.get("name", hb.get("label", ""))), "hideout_address": str(hb.get("address", "")),
			"hideout_pos": A.building_pos(world, int(g.hideout)), "members": g.members.size(),
			"strength": roundf(float(g.strength)), "leader_name": g.leader_name, "boss_title": g.boss_title,
			"vacancy": bool(g.vacancy), "player_rep": roundf(get_rep(gid)), "is_player_gang": gid == player_gang,
			"player_rank": rank_title(gid, rank) if gid == player_gang else "",
			"funds": world.ledger.balance(int(g.account)) if world.ledger != null else 0.0})
	return out


## Status des Spielers in seiner Bande.
func player_overview(world) -> Dictionary:
	if player_gang < 0:
		return {"member": false, "rep": rep.duplicate(), "expelled_from": expelled_from.duplicate()}
	return {"member": true, "gang": player_gang, "gang_name": gangs[player_gang].name, "rank": rank,
		"title": rank_title(player_gang, rank), "rep": get_rep(player_gang), "loyalty": loyalty,
		"trust": boss_trust(world, player_gang), "missions_done": missions_done, "recruits": recruits,
		"defends": defends, "is_boss": is_boss, "decisions": decisions.duplicate(true),
		"requirements": rank_requirements(world, player_gang)}


# ============================================================ Beitritt

func join_requirements(world, gid: int) -> Array:
	var crime = world.crime
	var police_member: bool = crime != null and crime.police.member
	return [
		{"label": "Mindestalter 16 Jahre", "met": A.player_age(world) >= 16.0, "value": A.player_age(world), "target": 16.0},
		{"label": "Ruf bei der Bande mindestens -10", "met": get_rep(gid) >= -10.0, "value": get_rep(gid), "target": -10.0},
		{"label": "Nicht im Polizeidienst", "met": not police_member, "value": 1.0 if police_member else 0.0, "target": 0.0},
		{"label": "Keiner anderen Bande angehörig", "met": player_gang < 0, "value": float(player_gang), "target": -1.0},
		{"label": "Nicht aus dieser Bande ausgestoßen", "met": not (gid in expelled_from), "value": 0.0, "target": 0.0},
	]


## Kontaktaufnahme im Versteck. building: Gebaeude, in dem der Spieler steht
## (-1 = von der Oberflaeche nicht geprueft). Erzeugt den Aufnahmeauftrag.
func contact(world, gid: int, building := -1) -> Dictionary:
	var g := get_gang(gid)
	if g.is_empty():
		return {"ok": false, "reason": "Unbekannte Organisation."}
	if building >= 0 and building != int(g.hideout):
		var hb := A.building(world, int(g.hideout))
		return {"ok": false, "reason": "Den Kontakt findest du im Versteck: %s." % str(hb.get("address", hb.get("name", "unbekannt")))}
	for r in join_requirements(world, gid):
		if not r.met:
			return {"ok": false, "reason": "Bedingung nicht erfüllt: %s." % r.label}
	for id in missions:
		var m: Dictionary = missions[id]
		if m.type == "initiation" and int(m.gang) == gid and m.status == "active":
			return {"ok": true, "mission_id": id, "text": "Du hast den Auftrag bereits: %s." % m.description}
	var m2 := _make_mission(world, gid, "initiation", rng)
	if m2.is_empty():
		return {"ok": false, "reason": "Die Bande hat gerade keine Aufgabe für dich."}
	m2.status = "active"
	m2.deadline = world.time_minutes + float(MISSION_TYPES.initiation.hours) * 60.0
	missions[int(m2.id)] = m2
	return {"ok": true, "mission_id": int(m2.id), "text": "%s: \"Beweis erst, dass man dir trauen kann.\" %s" % [g.leader_name, m2.description]}


func _join(world, gid: int) -> void:
	player_gang = gid
	rank = 0
	loyalty = 30.0
	missions_done = 0
	missions_by_type = {}
	recruits = 0
	defends = 0
	is_boss = false
	joined_day = world.get_day()
	rank_since_day = joined_day
	last_mission_day = joined_day
	rep[gid] = maxf(get_rep(gid), 10.0)
	A.set_field(A.player(world), "gang", gid)
	_decision(world, "Aufnahme in %s als Mitläufer." % gangs[gid].name)
	world.notify("Du bist jetzt Mitläufer bei %s." % gangs[gid].name, "success")


func leave_gang(world) -> Dictionary:
	if player_gang < 0:
		return {"ok": false, "reason": "Du gehörst keiner Bande an."}
	var gid := player_gang
	add_rep(world, gid, -35.0)
	_decision(world, "Ausstieg aus %s." % gangs[gid].name)
	_drop_player(world, gid)
	if is_boss:
		is_boss = false
		_choose_leader(world, gangs[gid])
	return {"ok": true, "text": "Du hast %s verlassen. Man wird dich nicht vergessen." % gangs[gid].name}


func _drop_player(world, gid: int) -> void:
	player_gang = -1
	rank = 0
	is_boss = false
	A.set_field(A.player(world), "gang", -1)
	for id in missions:
		var m: Dictionary = missions[id]
		if int(m.gang) == gid and m.status in ["available", "active"]:
			m.status = "failed"


func _decision(world, text: String) -> void:
	decisions.push_front({"day": world.get_day(), "text": text})
	if decisions.size() > 40:
		decisions.pop_back()


# ============================================================ Raenge

## Nachvollziehbare Bedingungen fuer den naechsten Rang (fuer das Telefon).
func rank_requirements(world, gid: int) -> Dictionary:
	var out := {"gang": gid, "rank": rank if gid == player_gang else -1, "title": rank_title(gid, rank) if gid == player_gang else "",
		"next_rank": -1, "next_title": "", "requirements": [], "can_promote": false, "boss_options": []}
	if gid != player_gang:
		out.requirements = join_requirements(world, gid)
		out.next_title = RANKS[0]
		return out
	if rank >= BOSS_RANK:
		out.next_title = ""
		return out
	var nr := rank + 1
	out.next_rank = nr
	out.next_title = rank_title(gid, nr)
	var req: Dictionary = RANK_REQ[nr]
	var reqs := []
	reqs.append({"label": "Ruf bei der Bande", "value": get_rep(gid), "target": float(req.rep), "met": get_rep(gid) >= float(req.rep)})
	reqs.append({"label": "Erledigte Aufträge", "value": float(missions_done), "target": float(req.missions), "met": missions_done >= int(req.missions)})
	reqs.append({"label": "Loyalität", "value": loyalty, "target": float(req.loyalty), "met": loyalty >= float(req.loyalty)})
	if float(req.trust) > 0.0:
		var t := boss_trust(world, gid)
		reqs.append({"label": "Vertrauen von %s" % gangs[gid].leader_name, "value": t, "target": float(req.trust), "met": t >= float(req.trust)})
	if int(req.days) > 0:
		var dd: int = world.get_day() - rank_since_day
		reqs.append({"label": "Tage im aktuellen Rang", "value": float(dd), "target": float(req.days), "met": dd >= int(req.days)})
	if req.has("heavy"):
		var h := heavy_missions()
		reqs.append({"label": "Harte Aufträge (Schutzgeld, Raub, Revier)", "value": float(h), "target": float(req.heavy), "met": h >= int(req.heavy)})
	if req.has("recruits"):
		reqs.append({"label": "Angeworbene Mitglieder", "value": float(recruits), "target": float(req.recruits), "met": recruits >= int(req.recruits)})
	if req.has("defends"):
		reqs.append({"label": "Verteidigte Reviere", "value": float(defends), "target": float(req.defends), "met": defends >= int(req.defends)})
	if A.has_m(world.crime, "in_jail"):
		reqs.append({"label": "Nicht in Haft", "value": 0.0, "target": 0.0, "met": not world.crime.in_jail(world)})
	var all_met := true
	for r in reqs:
		all_met = all_met and bool(r.met)
	out.requirements = reqs
	if nr == BOSS_RANK:
		var g: Dictionary = gangs[gid]
		out.boss_options = [
			{"id": "challenge", "label": "%s herausfordern" % g.leader_name, "available": all_met and not g.vacancy,
				"chance": challenge_chance(world) if all_met else 0.0,
				"description": "Machtprobe um die Führung. Scheitern kostet Rang, Ruf und Gesundheit."},
			{"id": "succeed", "label": "Nachfolge antreten", "available": all_met and bool(g.vacancy),
				"chance": 1.0, "description": "Nur möglich, wenn der %s verhaftet wurde oder sich zurückgezogen hat." % g.boss_title},
			{"id": "wait", "label": "Loyal bleiben und abwarten", "available": true, "chance": 1.0,
				"description": "Kein Risiko. Die Nachfolge wird frei, wenn der Anführer ausfällt."},
		]
	else:
		out.can_promote = all_met
	return out


func heavy_missions() -> int:
	var h := 0
	for t in HEAVY_TYPES:
		h += int(missions_by_type.get(t, 0))
	return h


## Befoerdert, solange alle Bedingungen erfuellt sind (bis Unterboss).
func check_promotion(world) -> bool:
	if player_gang < 0:
		return false
	var promoted := false
	while rank < UNDERBOSS_RANK:
		var rr := rank_requirements(world, player_gang)
		if not rr.can_promote:
			break
		rank += 1
		rank_since_day = world.get_day()
		promoted = true
		_decision(world, "Beförderung zum %s." % rank_title(player_gang, rank))
		world.notify("Beförderung: Du bist jetzt %s bei %s." % [rank_title(player_gang, rank), gangs[player_gang].name], "success")
		# Tage-Bedingung verhindert mehrere Stufen am selben Tag
	return promoted


func request_promotion(world) -> Dictionary:
	if player_gang < 0:
		return {"ok": false, "reason": "Du gehörst keiner Bande an."}
	if rank >= UNDERBOSS_RANK:
		return {"ok": false, "reason": "Die Spitze erreichst du nur durch eine Entscheidung (Herausforderung oder Nachfolge)."}
	if check_promotion(world):
		return {"ok": true, "text": "Du bist jetzt %s." % rank_title(player_gang, rank)}
	return {"ok": false, "reason": "Noch nicht alle Bedingungen erfüllt."}


func challenge_chance(world) -> float:
	if player_gang < 0:
		return 0.0
	var g: Dictionary = gangs[player_gang]
	var c := 0.3 + (get_rep(player_gang) - 90.0) / 40.0 + loyalty / 500.0 + boss_trust(world, player_gang) / 400.0
	c += (A.player_skill(world, "strength", 20.0) + A.player_skill(world, "charisma", 20.0)) / 800.0
	c -= float(g.strength) / 1000.0
	return clampf(c, 0.1, 0.85)


## Entscheidung an der Spitze: "challenge" (Machtprobe) oder "succeed" (Nachfolge).
func boss_decision(world, choice: String) -> Dictionary:
	if player_gang < 0 or rank != UNDERBOSS_RANK:
		return {"ok": false, "reason": "Nur als Unterboss möglich."}
	var rr := rank_requirements(world, player_gang)
	var opt := {}
	for o in rr.boss_options:
		if o.id == choice:
			opt = o
	if opt.is_empty():
		return {"ok": false, "reason": "Unbekannte Entscheidung."}
	if choice == "wait":
		_decision(world, "Entscheidung: loyal bleiben und abwarten.")
		loyalty = minf(100.0, loyalty + 2.0)
		return {"ok": true, "success": false, "text": "Du bleibst loyal. Deine Zeit wird kommen."}
	if not opt.available:
		return {"ok": false, "reason": "Die Bedingungen dafür sind nicht erfüllt."}
	var g: Dictionary = gangs[player_gang]
	if choice == "succeed":
		_become_boss(world, "Nachfolge nach dem Ausfall von %s" % g.leader_name)
		return {"ok": true, "success": true, "text": "Du trittst die Nachfolge an: %s von %s." % [g.boss_title, g.name]}
	# Herausforderung
	var success := rng.randf() < challenge_chance(world)
	if success:
		var old_name: String = g.leader_name
		if int(g.leader) >= 0:
			var p := A.person(world, int(g.leader))
			if not p.is_empty():
				p["gang"] = -1
			g.members.erase(int(g.leader))
		g.strength = maxf(5.0, float(g.strength) - 8.0)
		_become_boss(world, "Machtprobe gegen %s gewonnen" % old_name)
		world.post_news("Polizei", "Machtwechsel bei %s: %s soll sich ins Ausland abgesetzt haben." % [g.name, old_name])
		return {"ok": true, "success": true, "text": "Du hast %s verdrängt. Du bist jetzt %s von %s." % [old_name, g.boss_title, g.name]}
	rank = 2
	rank_since_day = world.get_day()
	add_rep(world, player_gang, -40.0)
	loyalty = maxf(0.0, loyalty - 30.0)
	trust[player_gang] = 0.0
	A.change_player_need(world, "health", -40.0)
	_decision(world, "Machtprobe gegen %s verloren – Degradierung zum Soldat." % g.leader_name)
	return {"ok": true, "success": false, "text": "Die Machtprobe ist gescheitert. Du wurdest zum Soldat degradiert und schwer verletzt."}


func _become_boss(world, why: String) -> void:
	var g: Dictionary = gangs[player_gang]
	rank = BOSS_RANK
	is_boss = true
	rank_since_day = world.get_day()
	g.leader = -1
	g.leader_name = _player_name(world)
	g.vacancy = false
	g.vacancy_since = -1
	_decision(world, "%s: Aufstieg zum %s." % [why, g.boss_title])
	world.notify("Du führst jetzt %s als %s." % [g.name, g.boss_title], "success")


func _player_name(world) -> String:
	var n = A.field(A.player(world), "name", "")
	if str(n) == "" and typeof(world.character) == TYPE_DICTIONARY:
		n = world.character.get("name", "Du")
	return str(n) if str(n) != "" else "Du"


## Aktionen als Boss: "raise_protection" (mehr Einnahmen, mehr Polizeidruck),
## "lie_low" (weniger Druck, weniger Einnahmen), "attack:<district_id>".
func boss_order(world, order: String) -> Dictionary:
	if not is_boss or player_gang < 0:
		return {"ok": false, "reason": "Nur der Anführer kann Befehle geben."}
	var g: Dictionary = gangs[player_gang]
	if order == "raise_protection":
		g["policy"] = "aggressive"
		return {"ok": true, "text": "Schutzgeld wird erhöht. Mehr Einnahmen, aber mehr Aufmerksamkeit der Polizei."}
	if order == "lie_low":
		g["policy"] = "quiet"
		return {"ok": true, "text": "Die Bande hält still. Weniger Einnahmen, weniger Polizeidruck."}
	if order.begins_with("attack:"):
		var did := int(order.substr(7))
		var def_gid := district_owner(did)
		if def_gid < 0 or def_gid == player_gang:
			return {"ok": false, "reason": "Dieser Stadtteil gehört keiner rivalisierenden Bande."}
		var r := _contest(world, player_gang, def_gid, did)
		return {"ok": true, "success": r.won, "text": r.text}
	return {"ok": false, "reason": "Unbekannter Befehl."}


# ============================================================ Auftraege

func _mission_rng(gid: int, day: int, salt: int) -> RandomNumberGenerator:
	var r := RandomNumberGenerator.new()
	r.seed = hash("gangs:%d:%d:%d:%d" % [_seed, gid, day, salt])
	return r


func _territory_buildings(world, gid: int, with_business: bool) -> Array:
	var out := []
	var terr: Array = gangs[gid].territory
	if world.crime != null and with_business:
		for did in terr:
			for biz in world.crime.businesses_in_district(did):
				out.append(biz)
		out.sort()
		return out
	for b in world.city.buildings:
		if int(b.get("district", -1)) in terr and int(b.id) != int(gangs[gid].hideout):
			out.append(int(b.id))
	return out


func _make_mission(world, gid: int, type: String, r: RandomNumberGenerator) -> Dictionary:
	var g: Dictionary = gangs[gid]
	var mt: Dictionary = MISSION_TYPES[type]
	var hide := int(g.hideout)
	var steps := []
	var desc := ""
	var real_type := type
	if type == "initiation":
		real_type = "delivery"
	match real_type:
		"delivery":
			var cands := _territory_buildings(world, gid, false)
			if cands.is_empty():
				cands = [r.randi_range(0, world.city.buildings.size() - 1)]
			var to: int = cands[r.randi_range(0, cands.size() - 1)]
			steps = [_step("pickup", hide, world), _step("deliver", to, world)]
			var tb := A.building(world, to)
			desc = "Hol ein Paket im Versteck ab und bring es zu %s." % str(tb.get("address", tb.get("label", "")))
		"collect":
			var bl := _territory_buildings(world, gid, true)
			if bl.is_empty():
				return {}
			var n := mini(bl.size(), r.randi_range(1, 3))
			var start := r.randi_range(0, bl.size() - 1)
			var names := []
			for i in n:
				var biz: int = bl[(start + i) % bl.size()]
				var st := _step("collect", int(A.business(world, biz).get("building", -1)), world)
				st["business_id"] = biz
				steps.append(st)
				names.append(A.business_name(world, biz))
			desc = "Treib das Schutzgeld ein bei: %s." % ", ".join(names)
		"car_theft":
			var richest := {}
			for d in world.city.districts:
				if str(d.zone) in ["park", "civic"]:
					continue
				if richest.is_empty() or float(d.wealth) + r.randf() * 0.3 > float(richest.wealth):
					richest = d
			var target_b := -1
			for b in world.city.buildings:
				if int(b.get("district", -1)) == int(richest.get("id", -2)) and str(b.kind) in ["house", "villa", "townhouse", "apartment"]:
					target_b = int(b.id)
					if r.randf() < 0.3:
						break
			if target_b < 0:
				return {}
			steps = [_step("steal", target_b, world), _step("deliver", hide, world)]
			desc = "Stiehl ein Auto in %s und bring es ins Versteck." % str(richest.get("name", ""))
		"robbery":
			var cands2 := []
			if world.crime != null:
				for did in world.crime._biz_by_district:
					if not (did in g.territory):
						cands2.append_array(world.crime._biz_by_district[did])
			cands2.sort()
			if cands2.is_empty():
				return {}
			var biz2: int = cands2[r.randi_range(0, cands2.size() - 1)]
			var st2 := _step("rob", int(A.business(world, biz2).get("building", -1)), world)
			st2["business_id"] = biz2
			steps = [st2]
			desc = "Überfall auf %s. Die Bande erhält 40 %% der Beute." % A.business_name(world, biz2)
		"defend":
			var rivals := []
			for og in gangs:
				if og != gid:
					rivals.append(og)
			if rivals.is_empty() or g.territory.is_empty():
				return {}
			var did: int = g.territory[r.randi_range(0, g.territory.size() - 1)]
			var rival: int = rivals[r.randi_range(0, rivals.size() - 1)]
			var st3 := {"event": "fight", "label": STEP_LABELS.fight, "building": -1,
				"pos": A.to_v2(A.district(world, did).get("center", Vector2.ZERO)), "done": false, "rival": rival, "district": did}
			steps = [st3]
			desc = "Mitglieder von %s drängen in %s. Vertreib sie." % [gangs[rival].name, A.district(world, did).get("name", "")]
		"recruit":
			var pid := -1
			if world.crime != null:
				var pool := []
				for did2 in g.territory:
					pool.append_array(world.crime.persons_in_district(did2))
				pool.sort()
				for tries in 12:
					if pool.is_empty():
						break
					var cand: int = pool[r.randi_range(0, pool.size() - 1)]
					var p := A.person(world, cand)
					if int(p.get("gang", -1)) < 0 and A.person_trait(p, "criminal") >= 0.25 and A.person_age(world, p) >= 16.0:
						pid = cand
						break
			if pid < 0:
				return {}
			var st4 := {"event": "talk", "label": STEP_LABELS.talk, "building": -1, "pos": A.person_pos(world, pid),
				"done": false, "person_id": pid}
			steps = [st4]
			desc = "Überzeuge %s, sich der Bande anzuschließen." % A.person_name(world, pid)
	if steps.is_empty():
		return {}
	var id := next_mission_id
	next_mission_id += 1
	var reward := snappedf(r.randf_range(float(mt.reward[0]), float(mt.reward[1])), 5.0)
	var m := {"id": id, "gang": gid, "type": type, "label": mt.label, "description": desc, "steps": steps,
		"status": "available", "reward": reward, "rep": float(mt.rep), "risk": float(mt.risk),
		"min_rank": int(mt.min_rank), "created_day": world.get_day(), "expires_day": world.get_day() + 1,
		"deadline": 0.0, "hours": float(mt.hours), "collected": 0.0, "loot": 0.0, "attempts": 0}
	return m


func _step(event: String, bid: int, world) -> Dictionary:
	return {"event": event, "label": STEP_LABELS.get(event, event), "building": bid, "pos": A.building_pos(world, bid), "done": false}


func _refresh_board(world) -> void:
	if player_gang < 0:
		return
	var day: int = world.get_day()
	for id in missions:
		var m: Dictionary = missions[id]
		if int(m.gang) == player_gang and m.status == "available" and int(m.created_day) == day:
			return
	var r := _mission_rng(player_gang, day, next_mission_id)
	var types := []
	for t in MISSION_TYPES:
		if t != "initiation" and int(MISSION_TYPES[t].min_rank) <= rank:
			types.append(t)
	var n := 4
	var made := 0
	var guard := 0
	while made < n and guard < 16:
		guard += 1
		var t: String = types[(made + guard + r.randi_range(0, types.size() - 1)) % types.size()]
		if made == 0 and types.size() > 1:
			t = types[types.size() - 1 - r.randi_range(0, mini(2, types.size() - 1))]
		var m := _make_mission(world, player_gang, t, r)
		if m.is_empty():
			continue
		missions[int(m.id)] = m
		made += 1


## Auftraege fuer den Spieler (verfuegbar und angenommen).
func available_missions(world) -> Array:
	_refresh_board(world)
	var out := []
	var ids := missions.keys()
	ids.sort()
	for id in ids:
		var m: Dictionary = missions[id]
		if m.status == "active" or (m.status == "available" and int(m.gang) == player_gang):
			out.append(_mission_view(world, m))
	return out


func _mission_view(world, m: Dictionary) -> Dictionary:
	var v := m.duplicate(true)
	v.rank_ok = rank >= int(m.min_rank)
	v.min_rank_title = rank_title(int(m.gang), maxi(0, int(m.min_rank)))
	v.risk_label = "hoch" if float(m.risk) >= 0.4 else ("mittel" if float(m.risk) >= 0.2 else "gering")
	var first := {}
	for s in m.steps:
		if not s.done:
			first = s
			break
	v.next_step = first.get("label", "Abschließen")
	v.target_building = int(first.get("building", -1))
	v.target_pos = first.get("pos", Vector2.ZERO)
	v.minutes_left = maxf(0.0, float(m.deadline) - world.time_minutes) if m.status == "active" else float(m.hours) * 60.0
	v.ready = _all_done(m)
	return v


func get_mission(id: int) -> Dictionary:
	return missions.get(id, {})


func accept_mission(world, id: int) -> Dictionary:
	var m: Dictionary = missions.get(id, {})
	if m.is_empty() or m.status != "available":
		return {"ok": false, "reason": "Auftrag nicht verfügbar."}
	if int(m.gang) != player_gang:
		return {"ok": false, "reason": "Du arbeitest nicht für diese Bande."}
	if rank < int(m.min_rank):
		return {"ok": false, "reason": "Dafür brauchst du mindestens den Rang %s." % rank_title(player_gang, int(m.min_rank))}
	var active := 0
	for mid in missions:
		if missions[mid].status == "active":
			active += 1
	if active >= MAX_ACTIVE:
		return {"ok": false, "reason": "Du hast bereits %d laufende Aufträge." % MAX_ACTIVE}
	m.status = "active"
	m.deadline = world.time_minutes + float(m.hours) * 60.0
	return {"ok": true, "mission": _mission_view(world, m), "text": "Auftrag angenommen: %s." % m.label}


func _all_done(m: Dictionary) -> bool:
	for s in m.steps:
		if not s.done:
			return false
	return true


## Fortschritt eines Auftrags. event: pickup|deliver|collect|steal|rob|fight|talk.
## data (optional): pos (Vector2) bzw. building zur Ortspruefung,
## business_id / person_id zur Zielauswahl.
func progress_mission(world, id: int, event: String, data := {}) -> Dictionary:
	var m: Dictionary = missions.get(id, {})
	if m.is_empty() or m.status != "active":
		return {"ok": false, "reason": "Kein laufender Auftrag."}
	if world.time_minutes > float(m.deadline):
		fail_mission(world, id, "Zeit abgelaufen")
		return {"ok": false, "reason": "Die Zeit ist abgelaufen."}
	var step := {}
	for s in m.steps:
		if s.done:
			continue
		if s.event == event and (not data.has("business_id") or int(s.get("business_id", -1)) == int(data.business_id) or not s.has("business_id")):
			step = s
			break
		if s.event != "collect":
			break  # Reihenfolge einhalten
	if step.is_empty():
		return {"ok": false, "reason": "Dieser Schritt ist gerade nicht dran."}
	if data.has("building") and int(step.get("building", -1)) >= 0 and int(data.building) != int(step.building):
		return {"ok": false, "reason": "Du bist nicht am Zielort."}
	if data.has("pos") and int(step.get("building", -1)) >= 0 and A.to_v2(data.pos).distance_to(A.to_v2(step.pos)) > 60.0:
		return {"ok": false, "reason": "Du bist nicht am Zielort."}
	var crime = world.crime
	var res := {"ok": true, "done": false, "text": "", "mission": {}}
	match event:
		"pickup":
			step.done = true
			res.text = "Paket abgeholt."
		"deliver":
			if m.type == "car_theft":
				var car_idx := -1
				if crime != null:
					for i in crime.stolen_goods.size():
						if str(crime.stolen_goods[i].kind) == "car":
							car_idx = i
				if car_idx < 0:
					return {"ok": false, "reason": "Du hast kein gestohlenes Auto dabei."}
				crime.stolen_goods.remove_at(car_idx)
				step.done = true
				res.text = "Auto abgeliefert."
			else:
				# Polizeikontrolle unterwegs
				var p_check: float = float(m.risk) * (crime.police_effectiveness(world) if crime != null else 1.0) * (0.5 + 0.2 * (crime.wanted if crime != null else 0))
				if crime != null and rng.randf() < p_check * 0.5:
					var a: Dictionary = crime.caught_in_act(world, "smuggling", A.to_v2(step.pos))
					fail_mission(world, id, "Polizeikontrolle")
					return {"ok": false, "reason": "Polizeikontrolle! Das Paket wurde beschlagnahmt.", "crime": a}
				step.done = true
				res.text = "Paket abgeliefert."
		"collect", "rob", "steal":
			if crime == null:
				return {"ok": false, "reason": "Nicht möglich."}
			var ctype: String = {"collect": "extortion", "rob": "robbery", "steal": "car_theft"}[event]
			var ctx := {"_mission": id, "pos": A.to_v2(step.pos)}
			if step.has("business_id"):
				ctx.business_id = int(step.business_id)
			if event == "collect":
				ctx.for_gang = int(m.gang)
			var a2: Dictionary = crime.attempt(world, ctype, ctx)
			res.crime = a2
			if not a2.ok:
				return {"ok": false, "reason": a2.reason, "crime": a2}
			m.attempts = int(m.attempts) + 1
			if a2.success:
				step.done = true
				if event == "collect":
					m.collected = float(m.collected) + float(a2.loot)
				elif event == "rob":
					m.loot = float(m.loot) + float(a2.loot)
				res.text = str(a2.text)
			else:
				res.text = str(a2.text)
				if int(m.attempts) >= 4:
					fail_mission(world, id, "Zu viele Fehlversuche")
					res.text += " Der Auftrag ist gescheitert."
		"fight":
			var rival := int(step.get("rival", -1))
			var mine := 30.0 + A.player_skill(world, "strength", 20.0) * 0.4 + A.player_skill(world, "fitness", 20.0) * 0.2 + rank * 4.0 + rng.randf() * 40.0
			var theirs := float(gangs.get(rival, {}).get("strength", 40.0)) * 0.5 + rng.randf() * 40.0
			A.change_player_need(world, "health", -rng.randf_range(5.0, 15.0))
			if crime != null and rng.randf() < 0.25:
				crime.last_seen = world.time_minutes
				crime._set_wanted(world, mini(5, crime.wanted + 1))
			if mine >= theirs:
				step.done = true
				if gangs.has(rival):
					gangs[rival].strength = maxf(5.0, float(gangs[rival].strength) - 3.0)
				gangs[int(m.gang)].strength = minf(100.0, float(gangs[int(m.gang)].strength) + 2.0)
				res.text = "Die Gegner wurden vertrieben."
			else:
				A.change_player_need(world, "health", -15.0)
				fail_mission(world, id, "Revier verloren")
				return {"ok": true, "done": false, "failed": true, "text": "Du wurdest zurückgeschlagen. Auftrag gescheitert."}
		"talk":
			var pid := int(step.get("person_id", data.get("person_id", -1)))
			var p := A.person(world, pid)
			if p.is_empty():
				fail_mission(world, id, "Person nicht auffindbar")
				return {"ok": false, "reason": "Die Person ist nicht auffindbar."}
			var chance := 0.35 + A.player_skill(world, "charisma", 20.0) / 200.0 + A.player_relationship(world, pid) / 200.0
			chance += float(gangs[int(m.gang)].strength) / 300.0 + A.person_trait(p, "criminal") * 0.3 - A.person_trait(p, "lawful") * 0.2
			m.attempts = int(m.attempts) + 1
			if rng.randf() < clampf(chance, 0.05, 0.95):
				p["gang"] = int(m.gang)
				gangs[int(m.gang)].members.append(pid)
				step.done = true
				res.text = "%s schließt sich an." % A.person_name(world, pid)
			else:
				A.change_player_relationship(world, pid, -5.0)
				res.text = "%s lehnt ab." % A.person_name(world, pid)
				if int(m.attempts) >= 3:
					fail_mission(world, id, "Anwerbung gescheitert")
					res.text += " Der Auftrag ist gescheitert."
		_:
			return {"ok": false, "reason": "Unbekannter Schritt."}
	res.done = _all_done(m)
	res.mission = _mission_view(world, m)
	return res


## Schliesst einen Auftrag ab: Lohn aus der Bandenkasse, Ruf, Loyalitaet,
## Vertrauen; prueft Befoerderung. Rueckgabe {ok, reward, rep, text, promoted}.
func complete_mission(world, id: int) -> Dictionary:
	var m: Dictionary = missions.get(id, {})
	if m.is_empty() or m.status != "active":
		return {"ok": false, "reward": 0.0, "rep": 0.0, "text": "Kein laufender Auftrag.", "reason": "Kein laufender Auftrag."}
	if world.time_minutes > float(m.deadline):
		fail_mission(world, id, "Zeit abgelaufen")
		return {"ok": false, "reward": 0.0, "rep": 0.0, "text": "Die Zeit ist abgelaufen.", "reason": "Zeit abgelaufen."}
	if not _all_done(m):
		return {"ok": false, "reward": 0.0, "rep": 0.0, "text": "Noch nicht alle Schritte erledigt.", "reason": "Noch nicht alle Schritte erledigt."}
	var gid := int(m.gang)
	var led: Ledger = world.ledger
	var gacc := gang_account(gid)
	var pacc := A.player_account(world)
	var reward := float(m.reward)
	if m.type == "collect":
		reward = snappedf(float(m.collected) * 0.2, 0.01)
	var paid := 0.0
	if reward > 0.0 and gacc >= 0 and pacc >= 0:
		paid = led.transfer_partial(gacc, pacc, reward, "Bandenauftrag: %s" % m.label)
	if m.type == "robbery" and float(m.loot) > 0.0 and gacc >= 0 and pacc >= 0:
		led.transfer_partial(pacc, gacc, snappedf(float(m.loot) * 0.4, 0.01), "Anteil der Bande")
	m.status = "done"
	var rep_gain := float(m.rep)
	add_rep(world, gid, rep_gain)
	var promoted := false
	if m.type == "initiation":
		_join(world, gid)
	else:
		missions_done += 1
		missions_by_type[m.type] = int(missions_by_type.get(m.type, 0)) + 1
		if m.type == "recruit":
			recruits += 1
		if m.type == "defend":
			defends += 1
		loyalty = minf(100.0, loyalty + 2.5)
		trust[gid] = minf(100.0, float(trust.get(gid, 0.0)) + 2.0)
		var g: Dictionary = gangs[gid]
		if int(g.leader) >= 0:
			A.change_player_relationship(world, int(g.leader), 2.0)
		last_mission_day = world.get_day()
		promoted = check_promotion(world)
	var text := "Auftrag erledigt: %s. Lohn %.2f €, Ruf +%.0f." % [m.label, paid, rep_gain]
	if promoted:
		text += " Beförderung zum %s!" % rank_title(gid, rank)
	return {"ok": true, "reward": paid, "rep": rep_gain, "text": text, "promoted": promoted}


func fail_mission(world, id: int, reason := "") -> Dictionary:
	var m: Dictionary = missions.get(id, {})
	if m.is_empty() or not (m.status in ["active", "available"]):
		return {"ok": false, "reason": "Kein laufender Auftrag."}
	var was_active: bool = m.status == "active"
	m.status = "failed"
	m["fail_reason"] = reason
	if was_active:
		add_rep(world, int(m.gang), -float(m.rep) * 0.75)
		if int(m.gang) == player_gang:
			loyalty = maxf(0.0, loyalty - 3.0)
			trust[player_gang] = maxf(0.0, float(trust.get(player_gang, 0.0)) - 2.0)
	return {"ok": true, "text": "Auftrag gescheitert: %s%s" % [m.label, (" (%s)" % reason) if reason != "" else ""]}


# ============================================================ Ereignisse aus CrimeSystem

func on_player_crime(world, crime, c: Dictionary, ctx: Dictionary) -> void:
	var did := int(c.get("district", -1))
	var owner := district_owner(did)
	if owner >= 0 and owner != player_gang and not ctx.has("_mission"):
		add_rep(world, owner, -3.0)
	if ctx.has("_mission") or not bool(c.get("success", false)):
		return
	# Eigenstaendig begangene Tat passt zu einem laufenden Auftrag?
	var ev: String = {"robbery": "rob", "extortion": "collect", "car_theft": "steal"}.get(str(c.type), "")
	if ev == "":
		return
	for id in missions:
		var m: Dictionary = missions[id]
		if m.status != "active":
			continue
		for s in m.steps:
			if s.done or s.event != ev:
				continue
			if s.has("business_id") and int(s.business_id) != int(c.get("victim", -1)):
				continue
			s.done = true
			if ev == "rob":
				m.loot = float(m.loot) + float(c.get("loot", 0.0))
			return


func on_member_arrested(world, _crime, pid: int) -> void:
	for gid in gangs:
		var g: Dictionary = gangs[gid]
		if pid in g.members:
			g.strength = maxf(5.0, float(g.strength) - 2.0)
			if pid == int(g.leader) and not g.vacancy:
				g.vacancy = true
				g.vacancy_since = world.get_day()
				world.post_news("Polizei", "Schlag gegen %s: Mutmaßlicher Anführer %s verhaftet." % [g.name, g.leader_name])
				if gid == player_gang and rank == UNDERBOSS_RANK:
					world.notify("Der %s ist verhaftet. Du kannst die Nachfolge antreten." % g.boss_title, "warning")
			return


func on_npc_gang_crime(_world, _crime, gid: int, _did: int) -> void:
	if gangs.has(gid):
		gangs[gid].heat = float(gangs[gid].heat) + 0.5


func on_player_arrested(world, _crime) -> void:
	if player_gang < 0:
		return
	loyalty = minf(100.0, loyalty + 3.0)
	for id in missions:
		var m: Dictionary = missions[id]
		if m.status == "active":
			fail_mission(world, id, "Festnahme")


func betrayed_by_player(world, crime, gid: int) -> Dictionary:
	var g: Dictionary = gangs[gid]
	g.strength = maxf(5.0, float(g.strength) - 20.0)
	_decision(world, "Aussage gegen %s bei der Polizei." % g.name)
	var text := "%s ist geschwächt." % g.name
	if int(g.leader) >= 0 and rng.randf() < 0.6 and crime != null:
		crime.convict_npc(world, int(g.leader), "extortion")
		text += " Der %s wurde verhaftet." % g.boss_title
	var was_boss := is_boss
	_drop_player(world, gid)
	if was_boss:
		_choose_leader(world, g)
	rep[gid] = -80.0
	expelled_from.append(gid)
	return {"text": text + " Du bist ausgestoßen."}


# ============================================================ Simulation

func step(world, _crime, _dt: float) -> void:
	pass


func on_hour(world, _crime, _day: int, _hour: int) -> void:
	var now: float = world.time_minutes
	for id in missions:
		var m: Dictionary = missions[id]
		if m.status == "active" and now > float(m.deadline):
			fail_mission(world, id, "Zeit abgelaufen")
			world.notify("Auftrag gescheitert: %s (Zeit abgelaufen)." % m.label, "warning")


func on_day(world, crime, day: int) -> void:
	var led: Ledger = world.ledger
	var eff: float = crime.police_effectiveness(world) if crime != null else 1.0
	var strict: float = crime.strictness(world) if crime != null else 1.0
	var ids := gangs.keys()
	ids.sort()
	for gid in ids:
		var g: Dictionary = gangs[gid]
		var policy := str(g.get("policy", "normal"))
		var mult := 1.6 if policy == "aggressive" else (0.5 if policy == "quiet" else 1.0)
		# Schutzgeld aus dem Revier (illegales Geschaeft)
		var income := 0.0
		if crime != null and led != null and int(g.account) >= 0:
			var bl := []
			for did in g.territory:
				bl.append_array(crime.businesses_in_district(did))
			bl.sort()
			for i in mini(4, bl.size()):
				var biz: int = bl[rng.randi_range(0, bl.size() - 1)]
				var bacc := A.business_account(world, biz)
				if bacc < 0:
					continue
				var amt := minf(60.0 * mult, maxf(0.0, led.balance(bacc) * 0.015 * mult))
				if amt >= 1.0:
					income += led.transfer_partial(bacc, int(g.account), snappedf(amt, 0.01), "Schutzgeld")
			# Mitglieder bezahlen
			var pay_n := mini(3, g.members.size())
			for i in pay_n:
				var pid: int = g.members[rng.randi_range(0, g.members.size() - 1)]
				var hacc := A.household_account(world, pid)
				if hacc >= 0:
					led.transfer_partial(int(g.account), hacc, snappedf(rng.randf_range(15.0, 45.0), 0.01), "Bandenlohn")
			if is_boss and gid == player_gang and income > 0.0:
				led.transfer_partial(int(g.account), A.player_account(world), snappedf(income * 0.15, 0.01), "Anteil des Anführers")
		g.income_today = income
		# Staerke: Mitglieder, Einnahmen, Polizeidruck
		var pressure := eff * strict * 1.5 * (1.4 if policy == "aggressive" else (0.6 if policy == "quiet" else 1.0)) + float(g.heat) * 0.1
		g.strength = clampf(float(g.strength) + 1.0 + minf(2.0, g.members.size() / 15.0) + income / 400.0 - pressure, 5.0, 100.0)
		g.heat = float(g.heat) * 0.5
		# Nachfolge
		if bool(g.vacancy):
			var wait := 3 if (gid == player_gang and rank == UNDERBOSS_RANK) else 1
			if day - int(g.vacancy_since) > wait:
				g.members.erase(int(g.leader))
				_choose_leader(world, g)
				if int(g.leader) < 0:
					g.leader_name = _random_name()
				world.post_news("Polizei", "%s hat offenbar einen neuen Anführer: %s." % [g.name, g.leader_name])
		elif not (is_boss and gid == player_gang) and rng.randf() < 0.01:
			g.vacancy = true
			g.vacancy_since = day
			if gid == player_gang:
				world.notify("Der %s von %s zieht sich zurück." % [g.boss_title, g.name], "warning")
	_rival_conflict(world, day)
	# Loyalitaet sinkt ohne Auftraege
	if player_gang >= 0 and day - last_mission_day > 3:
		loyalty = maxf(0.0, loyalty - 1.0)
	# Alte Auftraege aufraeumen
	var drop := []
	for id in missions:
		var m: Dictionary = missions[id]
		if m.status == "available" and day > int(m.expires_day):
			drop.append(id)
		elif m.status in ["done", "failed"] and day - int(m.created_day) > 3:
			drop.append(id)
	for id in drop:
		missions.erase(id)
	_refresh_board(world)


func _rival_conflict(world, _day: int) -> void:
	if gangs.size() < 2 or rng.randf() > 0.6:
		return
	var ids := gangs.keys()
	ids.sort()
	var a: int = ids[rng.randi_range(0, ids.size() - 1)]
	var b: int = ids[rng.randi_range(0, ids.size() - 1)]
	if a == b or gangs[b].territory.is_empty():
		return
	var did: int = gangs[b].territory[rng.randi_range(0, gangs[b].territory.size() - 1)]
	_contest(world, a, b, did)


## Abstrakter Revierkampf. Gewinnt der Angreifer deutlich und hat der
## Verteidiger mehr als einen Stadtteil, wechselt der Stadtteil den Besitzer.
func _contest(world, att: int, def: int, did: int) -> Dictionary:
	var ga: Dictionary = gangs[att]
	var gd: Dictionary = gangs[def]
	var sa := float(ga.strength) * rng.randf_range(0.6, 1.4)
	var sd := float(gd.strength) * rng.randf_range(0.6, 1.4) * 1.1
	var dname := str(A.district(world, did).get("name", ""))
	ga.strength = maxf(5.0, float(ga.strength) - 3.0)
	gd.strength = maxf(5.0, float(gd.strength) - 3.0)
	var won := sa > sd
	var text := ""
	if won and sa > sd * 1.2 and gd.territory.size() > 1:
		gd.territory.erase(did)
		ga.territory.append(did)
		text = "Revierkampf in %s: %s übernimmt das Gebiet von %s." % [dname, ga.name, gd.name]
	elif won:
		text = "Revierkämpfe in %s zwischen %s und %s – keine Seite setzt sich durch." % [dname, ga.name, gd.name]
	else:
		text = "%s wehrt einen Angriff von %s in %s ab." % [gd.name, ga.name, dname]
	world.post_news("Polizei", text)
	if world.crime != null:
		world.crime.add_dispatch(A.to_v2(A.district(world, did).get("center", Vector2.ZERO)), "crime", "Schlägerei in %s" % dname, -1, 2)
	return {"won": won, "text": text}


# ============================================================ Speichern

func to_save() -> Dictionary:
	return {"rng_state": rng.state, "seed": _seed, "gangs": gangs, "missions": missions, "next_mission_id": next_mission_id,
		"player_gang": player_gang, "rank": rank, "rep": rep, "trust": trust, "loyalty": loyalty,
		"missions_done": missions_done, "missions_by_type": missions_by_type, "recruits": recruits, "defends": defends,
		"joined_day": joined_day, "rank_since_day": rank_since_day, "last_mission_day": last_mission_day,
		"is_boss": is_boss, "decisions": decisions, "expelled_from": expelled_from}


func from_save(world, d: Dictionary) -> void:
	_seed = int(d.get("seed", world.seed))
	rng.seed = hash("gangs:%d" % _seed)
	if d.has("rng_state"):
		rng.state = int(d.rng_state)
	gangs = A.int_keys(d.get("gangs", {}))
	missions = A.int_keys(d.get("missions", {}))
	next_mission_id = int(d.get("next_mission_id", 1))
	player_gang = int(d.get("player_gang", -1))
	rank = int(d.get("rank", 0))
	rep = A.int_keys(d.get("rep", {}))
	trust = A.int_keys(d.get("trust", {}))
	loyalty = float(d.get("loyalty", 0.0))
	missions_done = int(d.get("missions_done", 0))
	missions_by_type = d.get("missions_by_type", {})
	recruits = int(d.get("recruits", 0))
	defends = int(d.get("defends", 0))
	joined_day = int(d.get("joined_day", -1))
	rank_since_day = int(d.get("rank_since_day", 0))
	last_mission_day = int(d.get("last_mission_day", 0))
	is_boss = bool(d.get("is_boss", false))
	decisions = d.get("decisions", [])
	expelled_from = d.get("expelled_from", [])
