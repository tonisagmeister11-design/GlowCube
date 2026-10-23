class_name JobMarket
extends RefCounted
## Arbeitsmarkt: Stellen an allen Arbeitsorten (Geschaefte, Firmen,
## oeffentliche Gebaeude), Bewerbung, Lohnzahlung, Befoerderung, Kuendigung.
##
## positions[id] = {id, building, occupation, employer_kind: "business"|"company"|"gov",
##   employer_id (int bzw. Ressortname), account, holder_kind: ""|"npc"|"player",
##   holder (id), rank, performance 0..100, since, days_in_rank, days_worked,
##   wage (Stundenlohn Stufe 0, inkl. wage_level), shift_start, shift_hours, days: [Wochentage],
##   active, reserved_owner, missed, last_worked_day}
## Lohn: Arbeitgeberkonto -> Arbeitnehmer (brutto), davon Lohnsteuer und
## Rentenbeitrag an den Staat (Government.split_wage).

const PROMO_SHIFTS := 6
const LABOR_SHARE := 0.50      # Stellen je Einwohner
const PUBLIC_SHARE := 0.15

var positions: Dictionary = {}
var next_id := 1
var rng := RandomNumberGenerator.new()
var _holder: Dictionary = {}       # "kind:id" -> position id
var _by_building: Dictionary = {}  # building id -> [position ids]
var _by_employer: Dictionary = {}  # "kind:id" -> [position ids]


# ============================================================ Aufbau

func setup(world) -> void:
	rng.seed = hash("jobs:%d" % world.seed)
	var city: CityModel = world.city
	var eco = world.economy
	var gov = world.government
	var wl: float = world.rules.f("wage_level")
	var pop: int = int(world.rules.get_rule("population"))
	var target_total := float(pop) * LABOR_SHARE
	var n_business := 0
	# --- Geschaefte
	if eco:
		for bid in eco.businesses:
			var b: Dictionary = eco.businesses[bid]
			var staff: Array = Occupations.BUSINESS_STAFF.get(b.type, ["salesperson"])
			var private_owner: bool = b.owner.kind == "external"
			var occs := []
			if private_owner:
				occs.append("entrepreneur")
			for o in staff:
				occs.append(o)
			var mx: int = int(b.staff_max)
			for i in mini(mx, occs.size()):
				var pid := _add(world, b.building, occs[i], "business", bid, int(b.account), wl, b)
				positions[pid].active = i < int(b.staff_base)
				positions[pid].reserved_owner = private_owner and i == 0
				if positions[pid].active:
					n_business += 1
	# --- Oeffentlicher Dienst
	var pub_raw := 0.0
	var pub_buildings := []
	for b in city.buildings:
		if Occupations.PUBLIC_STAFF.has(b.kind):
			pub_buildings.append(b)
			for e in Occupations.PUBLIC_STAFF[b.kind]:
				pub_raw += float(e[1])
	var pub_target := target_total * PUBLIC_SHARE
	var ps := minf(1.0, pub_target / maxf(1.0, pub_raw))
	var n_public := 0
	for b in pub_buildings:
		var acc: int = gov.dept_account_for_building(b.kind) if gov else -1
		var dept: String = Government.BUILDING_DEPT.get(b.kind, "administration")
		for e in Occupations.PUBLIC_STAFF[b.kind]:
			var n := int(round(float(e[1]) * ps * rng.randf_range(0.85, 1.15)))
			if n == 0 and float(e[1]) >= 1.0 and rng.randf() < 0.6:
				n = 1
			for i in n:
				_add(world, b.id, e[0], "gov", dept, acc, wl, b)
				n_public += 1
	# --- Firmen
	if eco:
		var rest := maxf(20.0, target_total - n_business - n_public)
		var wsum := 0.0
		for cid in eco.companies:
			wsum += float(eco.companies[cid].size_weight)
		for cid in eco.companies:
			var c: Dictionary = eco.companies[cid]
			var n := maxi(2, int(round(rest * float(c.size_weight) / maxf(1.0, wsum))))
			var staff: Array = Company.SECTORS[c.sector].staff
			var tw := 0.0
			for e in staff:
				tw += float(e[1])
			var counts := {}
			var assigned := 0
			for e in staff:
				var k := int(floor(n * float(e[1]) / tw))
				counts[e[0]] = k
				assigned += k
			var idx := 0
			while assigned < n:
				var e2: Array = staff[idx % staff.size()]
				counts[e2[0]] = int(counts[e2[0]]) + 1
				assigned += 1
				idx += 1
			var bld: Dictionary = city.buildings[int(c.building)]
			for occ in counts:
				for i in int(counts[occ]):
					_add(world, c.building, occ, "company", cid, int(c.account), wl, bld)
	update_wage_need(world)


func _add(world, building: int, occ: String, ekind: String, eid, acc: int, wl: float, ctx: Dictionary) -> int:
	var o: Dictionary = Occupations.OCCUPATIONS[occ]
	var id := next_id
	next_id += 1
	var shifts: Array = o.shifts
	var sh: Array = shifts[id % shifts.size()]
	var start := float(sh[0])
	var hours := float(sh[1])
	if ekind == "business":
		var op: int = int(ctx.get("open", 8))
		var cl: int = int(ctx.get("close", 20))
		if not (op == 0 and cl >= 24):
			var close_h := float(cl if cl > op else cl + 24)
			# Schicht muss in die Oeffnungszeit (+-1 h) passen
			if start < op - 1.0 or start + hours > close_h + 1.0:
				start = float(op) - 0.5 if (id % 2 == 0 or close_h - op < hours + 2.0) else maxf(float(op), close_h - hours + 0.5)
				start = fposmod(start, 24.0)
	var days: Array = Occupations.WEEKDAYS
	if o.rotating:
		var off := id % 7
		days = []
		for i in 5:
			days.append((off + i) % 7)
		days.sort()
	var prem := 1.0
	if ekind == "company":
		var sec: String = world.economy.companies[eid].sector if world.economy else ""
		if sec in ["finance", "law", "consulting", "insurance"]:
			prem = 1.08
	elif ekind == "business":
		prem = 1.0 + 0.04 * (int(ctx.get("tier", 1)) - 1)
	var wage := float(o.wage) * wl * prem * rng.randf_range(0.96, 1.06)
	positions[id] = {"id": id, "building": building, "occupation": occ, "employer_kind": ekind, "employer_id": eid,
		"account": acc, "holder_kind": "", "holder": -1, "rank": 0, "performance": 55.0, "since": 0,
		"days_in_rank": 0, "days_worked": 0, "wage": snappedf(wage, 0.05), "shift_start": start, "shift_hours": hours,
		"days": days, "active": true, "reserved_owner": false, "missed": 0, "last_worked_day": -1}
	_index(id)
	return id


func _index(id: int) -> void:
	var p: Dictionary = positions[id]
	var b: int = int(p.building)
	if not _by_building.has(b):
		_by_building[b] = []
	_by_building[b].append(id)
	var ek := "%s:%s" % [p.employer_kind, str(p.employer_id)]
	if not _by_employer.has(ek):
		_by_employer[ek] = []
	_by_employer[ek].append(id)
	if p.holder_kind != "":
		_holder["%s:%d" % [p.holder_kind, int(p.holder)]] = id


func _rebuild_indices() -> void:
	_holder.clear()
	_by_building.clear()
	_by_employer.clear()
	for id in positions:
		_index(id)


## Geschaetzter taeglicher Lohnbedarf je Ressort (fuer den Staatshaushalt).
func update_wage_need(world) -> void:
	var gov = world.government
	if gov == null:
		return
	var need := {}
	for p in positions.values():
		if p.employer_kind != "gov" or not p.active:
			continue
		var d: String = p.employer_id
		need[d] = float(need.get(d, 0.0)) + hourly_wage(world, p) * float(p.shift_hours) * float(p.days.size()) / 7.0
	gov.wage_need = need


# ============================================================ Abfragen

func get_position(id: int) -> Dictionary:
	return positions.get(id, {})


func position_of(kind: String, id: int) -> int:
	return _holder.get("%s:%d" % [kind, id], -1)


func positions_in_building(bid: int) -> Array:
	return _by_building.get(bid, []).duplicate()


func positions_of_employer(ekind: String, eid) -> Array:
	return _by_employer.get("%s:%s" % [ekind, str(eid)], []).duplicate()


func hourly_wage(world, p: Dictionary) -> float:
	var w: float = float(p.wage) * Occupations.rank_mult(p.occupation, int(p.rank))
	if world and world.government:
		w = maxf(w, world.government.policy("minimum_wage") * world.rules.f("wage_level"))
	return w


func employer_name(world, p: Dictionary) -> String:
	match p.employer_kind:
		"business":
			return str(world.economy.businesses[p.employer_id].name) if world.economy else ""
		"company":
			return str(world.economy.companies[p.employer_id].name) if world.economy else ""
		"gov":
			return Government.DEPT_NAMES.get(p.employer_id, "Stadt Havenbrück")
	return ""


func is_workday(p: Dictionary, day: int) -> bool:
	return p.days.has(day % 7)


## Schichtbeginn (Spielminuten) am Tag `day`.
func shift_start_minutes(p: Dictionary, day: int) -> float:
	return day * 1440.0 + float(p.shift_start) * 60.0


func job_of(kind: String, id: int, world = null) -> Dictionary:
	var pid := position_of(kind, id)
	if pid < 0:
		return {}
	var p: Dictionary = positions[pid]
	var o: Dictionary = Occupations.OCCUPATIONS[p.occupation]
	var out := {"position": pid, "occupation": p.occupation, "name": o.name, "title": Occupations.title(p.occupation, int(p.rank)),
		"building": int(p.building), "rank": int(p.rank), "ranks": o.ranks.size(), "performance": float(p.performance),
		"since": int(p.since), "days_worked": int(p.days_worked), "shift": [float(p.shift_start), float(p.shift_hours)],
		"days": p.days.duplicate(), "missed": int(p.missed), "employer_kind": p.employer_kind, "category": o.category}
	if world != null:
		out["wage"] = hourly_wage(world, p)
		out["employer"] = employer_name(world, p)
		var b: Dictionary = world.city.buildings[int(p.building)]
		out["workplace"] = b.name if str(b.name) != "" else b.label
		out["address"] = b.address
	return out


## Daten eines Bewerbers / Beschaeftigten.
func _worker(world, kind: String, id: int) -> Dictionary:
	if kind == "player" and world.player:
		var pl = world.player
		return {"skills": pl.skills, "education": pl.education, "age": pl.age(world), "criminal": int(pl.criminal_record) > 0,
			"account": world.ledger.player_account, "lazy": 0.0, "ambitious": 0.5, "energy": float(pl.needs.get("energy", 70.0))}
	if kind == "npc" and world.population:
		var p: Dictionary = world.population.persons.get(id, {})
		if p.is_empty():
			return {}
		var h: Dictionary = world.population.households.get(int(p.household), {})
		return {"skills": p.skills, "education": p.education, "age": world.age_years(float(p.birth)),
			"criminal": int(p.criminal_record) > 0, "account": int(h.get("account", -1)),
			"lazy": float(p.traits.lazy), "ambitious": float(p.traits.ambitious), "energy": float(p.needs.energy)}
	return {}


## Prueft die Anforderungen. Rueckgabe: Liste deutscher Gruende (leer = geeignet).
func check_requirements(world, kind: String, id: int, occ_id: String) -> Array:
	var o: Dictionary = Occupations.OCCUPATIONS.get(occ_id, {})
	var w := _worker(world, kind, id)
	var out := []
	if o.is_empty() or w.is_empty():
		return ["Unbekannt"]
	if float(w.age) < float(o.min_age):
		out.append("Mindestalter %d Jahre" % int(o.min_age))
	if Occupations.edu_rank(w.education) < Occupations.edu_rank(o.education):
		out.append("Abschluss: %s" % Occupations.edu_label(o.education))
	for s in Skills.missing(w.skills, o.skills):
		out.append("%s mindestens %d" % [Skills.label(s), int(o.skills[s])])
	if w.criminal and not o.criminal_record_ok:
		out.append("Kein Vorstrafenregister erlaubt")
	return out


## Offene Stellen. filter: {category, eligible_only, building, limit}
func list_openings(world, for_kind := "", for_id := -1, filter := {}) -> Array:
	var out := []
	for pid in positions:
		var p: Dictionary = positions[pid]
		if p.holder_kind != "" or not p.active or p.reserved_owner:
			continue
		var o: Dictionary = Occupations.OCCUPATIONS[p.occupation]
		if o.illegal:
			continue
		if filter.has("category") and o.category != filter.category:
			continue
		if filter.has("building") and int(p.building) != int(filter.building):
			continue
		var miss := []
		if for_kind != "":
			miss = check_requirements(world, for_kind, for_id, p.occupation)
		if filter.get("eligible_only", false) and not miss.is_empty():
			continue
		var b: Dictionary = world.city.buildings[int(p.building)]
		out.append({"position": pid, "occupation": p.occupation, "name": o.name, "title": Occupations.title(p.occupation, 0),
			"category": o.category, "building": int(p.building), "workplace": b.name if str(b.name) != "" else b.label,
			"address": b.address, "employer": employer_name(world, p), "wage": hourly_wage(world, p),
			"shift": [float(p.shift_start), float(p.shift_hours)], "days": p.days.duplicate(),
			"requirements": Occupations.requirements_text(p.occupation), "eligible": miss.is_empty(), "missing": miss,
			"district": int(b.district), "pos": b.sidewalk_point})
	out.sort_custom(func(a, c): return float(a.wage) > float(c.wage))
	var limit: int = int(filter.get("limit", 0))
	if limit > 0 and out.size() > limit:
		out = out.slice(0, limit)
	return out


# ============================================================ Einstellung

## Bewerbung. Rueckgabe {ok, reason, title, wage}
func apply(world, kind: String, id: int, position_id: int) -> Dictionary:
	var p: Dictionary = positions.get(position_id, {})
	if p.is_empty():
		return {"ok": false, "reason": "Stelle nicht gefunden."}
	var o: Dictionary = Occupations.OCCUPATIONS[p.occupation]
	if o.illegal:
		return {"ok": false, "reason": "Diese Tätigkeit wird nicht offiziell vergeben."}
	if not p.active:
		return {"ok": false, "reason": "Diese Stelle ist derzeit nicht ausgeschrieben."}
	if p.reserved_owner:
		return {"ok": false, "reason": "Diese Stelle ist dem Inhaber vorbehalten."}
	if p.holder_kind != "":
		if p.holder_kind == kind and int(p.holder) == id:
			return {"ok": false, "reason": "Du arbeitest bereits hier."}
		return {"ok": false, "reason": "Die Stelle ist bereits besetzt."}
	var miss := check_requirements(world, kind, id, p.occupation)
	if not miss.is_empty():
		return {"ok": false, "reason": "Anforderungen nicht erfüllt: " + ", ".join(miss) + "."}
	if kind == "player" and world.player and world.player.has_method("is_in_school") and world.player.is_in_school(world) \
			and float(p.shift_hours) > 4.0 and float(p.shift_start) < 15.0:
		return {"ok": false, "reason": "Während der Schulzeit sind nur Nachmittags-Aushilfsjobs möglich."}
	var old := position_of(kind, id)
	if old >= 0:
		_vacate(world, old, "switch")
		if kind == "player":
			world.notify("Du hast deine bisherige Stelle gekündigt.", "info")
	hire(world, kind, id, position_id)
	return {"ok": true, "reason": "", "title": Occupations.title(p.occupation, 0), "wage": hourly_wage(world, p)}


## Besetzt eine Stelle ohne Pruefung (Weltaufbau, interne Vermittlung).
func hire(world, kind: String, id: int, position_id: int, rank := 0) -> void:
	var p: Dictionary = positions[position_id]
	p.holder_kind = kind
	p.holder = id
	p.rank = rank
	p.performance = 55.0 + rank * 8.0
	p.since = world.get_day()
	p.days_in_rank = 0
	p.days_worked = 0
	p.missed = 0
	p.last_worked_day = world.get_day() - 1
	_holder["%s:%d" % [kind, id]] = position_id
	_employer_list_add(world, p, id, kind)
	var ref := {"position": position_id, "occupation": p.occupation, "building": int(p.building)}
	if kind == "npc" and world.population:
		var per: Dictionary = world.population.persons.get(id, {})
		if not per.is_empty():
			per.job = ref
			per.unemployed_since = -1
	elif kind == "player" and world.player:
		world.player.job = ref
		world.emit_event("player_job_changed", [p.occupation])


func _employer_list_add(world, p: Dictionary, id: int, kind: String) -> void:
	var lst := _employees_list(world, p)
	if lst != null:
		var pid := id if kind == "npc" else 0
		if not lst.has(pid):
			lst.append(pid)


func _employees_list(world, p: Dictionary):
	if world.economy == null:
		return null
	if p.employer_kind == "business" and world.economy.businesses.has(p.employer_id):
		return world.economy.businesses[p.employer_id].employees
	if p.employer_kind == "company" and world.economy.companies.has(p.employer_id):
		return world.economy.companies[p.employer_id].employees
	return null


func _vacate(world, position_id: int, why := "") -> void:
	var p: Dictionary = positions[position_id]
	if p.holder_kind == "":
		return
	var kind: String = p.holder_kind
	var id: int = int(p.holder)
	_holder.erase("%s:%d" % [kind, id])
	var lst = _employees_list(world, p)
	if lst != null:
		lst.erase(id if kind == "npc" else 0)
	p.holder_kind = ""
	p.holder = -1
	if kind == "npc" and world.population:
		var per: Dictionary = world.population.persons.get(id, {})
		if not per.is_empty():
			per.job = {}
			if why != "switch" and why != "retire" and why != "death":
				per.unemployed_since = world.get_day()
	elif kind == "player" and world.player and why != "switch":
		world.player.job = {}
		world.emit_event("player_job_changed", [""])


func quit(world, kind: String, id: int) -> Dictionary:
	var pid := position_of(kind, id)
	if pid < 0:
		return {"ok": false, "reason": "Du hast keine Stelle."}
	_vacate(world, pid, "quit")
	return {"ok": true, "reason": ""}


## Entlassung. reason wird dem Spieler angezeigt.
func fire(world, kind: String, id: int, reason := "") -> Dictionary:
	var pid := position_of(kind, id)
	if pid < 0:
		return {"ok": false, "reason": "Keine Stelle."}
	_vacate(world, pid, "fired")
	if kind == "player":
		world.notify("Du wurdest entlassen%s." % ((": " + reason) if reason != "" else ""), "danger")
	return {"ok": true, "reason": ""}


## Ruhestand / Tod: Stelle ohne Arbeitslosigkeit freigeben.
func release(world, kind: String, id: int, why := "retire") -> void:
	var pid := position_of(kind, id)
	if pid >= 0:
		_vacate(world, pid, why)


## Betriebsbedingte Kuendigung einer Stelle (nicht der Inhaber).
func lay_off_one(world, ekind: String, eid) -> bool:
	var ids := positions_of_employer(ekind, eid)
	var best := -1
	var worst := 1e9
	for pid in ids:
		var p: Dictionary = positions[pid]
		if not p.active or p.reserved_owner:
			continue
		var score: float = float(p.performance) - (1000.0 if p.holder_kind == "" else 0.0) + (5.0 if p.holder_kind == "player" else 0.0)
		if score < worst:
			worst = score
			best = pid
	if best < 0:
		return false
	var p2: Dictionary = positions[best]
	var hk: String = p2.holder_kind
	_vacate(world, best, "layoff")
	p2.active = false
	if hk == "player":
		world.notify("Du wurdest betriebsbedingt entlassen.", "danger")
	update_wage_need(world)
	return true


## Eine ruhende Stelle wieder ausschreiben.
func expand(world, ekind: String, eid) -> bool:
	for pid in positions_of_employer(ekind, eid):
		var p: Dictionary = positions[pid]
		if not p.active:
			p.active = true
			return true
	return false


func close_employer(world, ekind: String, eid) -> void:
	for pid in positions_of_employer(ekind, eid):
		var hk: String = positions[pid].holder_kind
		_vacate(world, pid, "layoff")
		positions[pid].active = false
		if hk == "player":
			world.notify("Dein Arbeitgeber hat geschlossen. Du bist arbeitslos.", "danger")


func reopen_employer(world, ekind: String, eid, owner: Dictionary) -> void:
	var base := 2
	if ekind == "business" and world.economy:
		base = int(world.economy.businesses[eid].staff_base)
	var i := 0
	for pid in positions_of_employer(ekind, eid):
		var p: Dictionary = positions[pid]
		p.active = i < base
		i += 1
		if p.reserved_owner:
			p.active = true
			if owner.kind == "household" and world.population:
				world.population.assign_owner_job(world, int(owner.id), pid)


func change_owner(world, business_id: int, old_owner: Dictionary, new_owner: Dictionary) -> void:
	for pid in positions_of_employer("business", business_id):
		var p: Dictionary = positions[pid]
		if p.reserved_owner:
			_vacate(world, pid, "quit")
			if new_owner.kind == "household" and world.population:
				world.population.assign_owner_job(world, int(new_owner.id), pid)


## Inhaberstelle (fuer den Spieler als Eigentuemer).
func owner_position(business_id: int) -> int:
	for pid in positions_of_employer("business", business_id):
		if positions[pid].reserved_owner:
			return pid
	return -1


func take_owner_position(world, kind: String, id: int, business_id: int) -> Dictionary:
	var pid := owner_position(business_id)
	if pid < 0:
		return {"ok": false, "reason": "Dieses Geschäft hat keine Inhaberstelle."}
	var b: Dictionary = world.economy.businesses[business_id]
	if b.owner.kind != kind or int(b.owner.id) != id:
		return {"ok": false, "reason": "Das Geschäft gehört dir nicht."}
	if positions[pid].holder_kind != "":
		return {"ok": false, "reason": "Die Inhaberstelle ist bereits besetzt."}
	var old := position_of(kind, id)
	if old >= 0:
		_vacate(world, old, "switch")
	hire(world, kind, id, pid)
	return {"ok": true, "reason": ""}


# ============================================================ Arbeit

## Bezahlt geleistete Stunden und aktualisiert Leistung/Befoerderung.
## Rueckgabe {ok, reason, pay (netto), gross, tax, pension, promoted, title, fired}
func work(world, kind: String, id: int, hours: float) -> Dictionary:
	var pid := position_of(kind, id)
	if pid < 0:
		return {"ok": false, "reason": "Du hast keine Arbeitsstelle.", "pay": 0.0}
	var p: Dictionary = positions[pid]
	var w := _worker(world, kind, id)
	if w.is_empty():
		return {"ok": false, "reason": "Unbekannt", "pay": 0.0}
	var L: Ledger = world.ledger
	var gov = world.government
	var rate := hourly_wage(world, p)
	var gross := snappedf(rate * hours, 0.01)
	var acc: int = int(p.account)
	if p.employer_kind == "gov" and gov:
		gov.cover(world, acc, gross)
	var worker_acc: int = int(w.account)
	var paid := 0.0
	if gross > 0.0 and worker_acc >= 0:
		paid = L.transfer_partial(acc, worker_acc, gross, "Lohn")
	if paid < gross - 0.01 and world.economy:
		world.economy.record_unpaid(p.employer_kind, p.employer_id, gross - paid)
		if kind == "player":
			world.notify("Dein Arbeitgeber konnte nur %s von %s zahlen." % [Economy.fmt_money(paid), Economy.fmt_money(gross)], "warning")
	if world.economy:
		world.economy.record_wage(p.employer_kind, p.employer_id, paid)
	var tax := 0.0
	var pen := 0.0
	if gov and paid > 0.0:
		var sp: Dictionary = gov.split_wage(paid, rate)
		tax = L.transfer_partial(worker_acc, gov.account, float(sp.tax), "Lohnsteuer")
		gov.record_revenue("income_tax", tax)
		pen = L.transfer_partial(worker_acc, gov.pension_account, float(sp.pension), "Rentenbeitrag")
		gov.record_revenue("pension_contribution", pen)
	# Leistung
	var o: Dictionary = Occupations.OCCUPATIONS[p.occupation]
	var skill_bonus := 0.0
	for s in o.skills:
		skill_bonus += clampf(float(w.skills.get(s, 0.0)) - float(o.skills[s]), -20.0, 40.0) * 0.4
	var base := 52.0 + skill_bonus - float(w.lazy) * 28.0 + float(w.ambitious) * 12.0
	base += (float(w.energy) - 50.0) * 0.15
	if kind == "player":
		base = 58.0 + skill_bonus + (float(w.energy) - 50.0) * 0.2
	base += rng.randf_range(-8.0, 8.0)
	var share := clampf(hours / maxf(1.0, float(p.shift_hours)), 0.0, 1.0)
	p.performance = clampf(lerpf(float(p.performance), clampf(base, 0.0, 100.0), 0.18 * share), 0.0, 100.0)
	if share >= 0.5:
		p.days_worked = int(p.days_worked) + 1
		p.days_in_rank = int(p.days_in_rank) + 1
		p.last_worked_day = world.get_day()
		p.missed = 0
	# Faehigkeiten wachsen durch Arbeit
	for s in o.skills:
		Skills.gain(w.skills, s, 0.12 * hours)
	var promoted := false
	var ranks: Array = o.ranks
	var r: int = int(p.rank)
	if r < ranks.size() - 1 and int(p.days_in_rank) >= PROMO_SHIFTS and float(p.performance) >= float(ranks[r + 1].min_perf):
		var chance := 1.0 if kind == "player" else 0.12 + float(w.ambitious) * 0.25
		if rng.randf() < chance:
			p.rank = r + 1
			p.days_in_rank = 0
			promoted = true
			if kind == "player":
				world.notify("Beförderung! Du bist jetzt %s." % Occupations.title(p.occupation, r + 1), "success")
				world.emit_event("player_job_changed", [p.occupation])
	var fired := false
	if int(p.days_worked) >= 4 and float(p.performance) < 14.0 and not p.reserved_owner:
		if kind == "player" or rng.randf() < 0.3:
			fire(world, kind, id, "zu schwache Leistung")
			fired = true
	elif kind == "player" and float(p.performance) < 25.0 and int(p.days_worked) >= 2:
		world.notify("Dein Chef ist mit deiner Leistung unzufrieden.", "warning")
	return {"ok": true, "reason": "", "pay": paid - tax - pen, "gross": paid, "tax": tax, "pension": pen,
		"promoted": promoted, "title": Occupations.title(p.occupation, int(p.rank)), "fired": fired}


# ============================================================ Tageslauf

func on_day(world, day: int) -> void:
	# Fehlschichten des Spielers
	var ppid := position_of("player", 0)
	if ppid >= 0:
		var p: Dictionary = positions[ppid]
		var yesterday := day - 1
		if is_workday(p, yesterday) and int(p.last_worked_day) < yesterday and int(p.since) < yesterday:
			p.missed = int(p.missed) + 1
			p.performance = maxf(0.0, float(p.performance) - 12.0)
			if int(p.missed) >= 3 and not p.reserved_owner:
				fire(world, "player", 0, "unentschuldigtes Fehlen")
			else:
				world.notify("Du bist nicht zur Arbeit erschienen (%d/3 Fehltage)." % int(p.missed), "warning")
	_match_npcs(world, day)
	if day % 3 == 0:
		update_wage_need(world)


## Vermittlung: offene Stellen werden mit arbeitsuchenden NPCs besetzt.
func _match_npcs(world, day: int) -> void:
	var pop = world.population
	if pop == null:
		return
	var seekers: Array = pop.job_seekers(world)
	var switchers: Array = pop.job_switchers(world)
	if seekers.is_empty() and switchers.is_empty():
		return
	var vacancies := []
	for pid in positions:
		var p: Dictionary = positions[pid]
		if p.holder_kind == "" and p.active and not p.reserved_owner:
			vacancies.append(pid)
	for pid in vacancies:
		if rng.randf() > 0.55:
			continue
		var p: Dictionary = positions[pid]
		var hired := false
		for t in 24:
			var use_switch := not switchers.is_empty() and (seekers.is_empty() or rng.randf() < 0.15)
			var pool: Array = switchers if use_switch else seekers
			if pool.is_empty():
				break
			var k := rng.randi() % pool.size()
			var cand: int = pool[k]
			if not check_requirements(world, "npc", cand, p.occupation).is_empty():
				continue
			if use_switch:
				var cur := position_of("npc", cand)
				if cur >= 0 and hourly_wage(world, positions[cur]) * 1.1 > hourly_wage(world, p):
					continue
				if cur >= 0:
					_vacate(world, cur, "switch")
			hire(world, "npc", cand, pid)
			pool.remove_at(k)
			hired = true
			break
		if not hired and seekers.is_empty():
			break


# ============================================================ Speichern

func to_save() -> Dictionary:
	return {"positions": positions.duplicate(true), "next_id": next_id, "rng": rng.state}


func from_save(world, d: Dictionary) -> void:
	d = d.duplicate(true)
	rng.seed = hash("jobs:%d" % world.seed)
	positions = {}
	for k in d.get("positions", {}):
		positions[int(k)] = d.positions[k]
	next_id = int(d.get("next_id", 1))
	if d.has("rng"):
		rng.state = int(d.rng)
	_rebuild_indices()
