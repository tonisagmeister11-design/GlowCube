class_name Population
extends RefCounted
## Bevoelkerung: Personen und Haushalte, Tagesablaeufe, Lebenslauf
## (Altern, Geburt, Tod, Auszug, Partnerschaft, Zu-/Wegzug), Haushaltsfinanzen.
##
## persons[id] = {id, first, last, body, birth, household, home, job, school, traits, needs,
##   appearance, activity, location: {building, from, to, depart, arrive, mode}, relationships,
##   alive, homeless, criminal_record, gang, education, skills, health, parents, partner,
##   children, sleep: [bett_h, wach_h], plan, plan_i, next_t, needs_t, act_start, retired,
##   pension, unemployed_since, homemaker, illness, camp}
## households[id] = {id, members, account, home, wealth (0..1), cls, pantry, car, disc,
##   homeless, player_member, shopper, last_shop_day, district}
##
## Simulation: ereignisgesteuert. Jede Person hat einen Tagesplan (NpcSchedule)
## und steht in einer Zeitschlange (5-Minuten-Slots); nur Personen mit
## Aktivitaetswechsel werden bearbeitet. Beduerfnisse werden bei jedem Wechsel
## nachgerechnet (lazy). Position waehrend des Pendelns wird entlang
## city.ped_route interpoliert (zeitgerafft, siehe docs/ECONOMY.md).

const PLAYER_ID := 0
const CELL := 64.0
const CLASSES := ["poor", "lower", "middle", "upper", "rich"]
const CLASS_MEAL := [0.85, 0.95, 1.0, 1.2, 1.6]
const INDOOR_ACTS := ["sleep", "work", "school", "shop", "home", "hospital", "jail"]

var persons: Dictionary = {}
var households: Dictionary = {}
var next_person_id := 1
var next_household_id := 1
var rng := RandomNumberGenerator.new()
var target_population := 2400
var history: Array = []
var day_stats: Dictionary = {}
var setup_ms: Dictionary = {}   # Messwerte des Aufbaus (nicht gespeichert)

# Abgeleitet (nicht gespeichert)
var _queue: Dictionary = {}
var _last_slot := -1
var _grid: Dictionary = {}
var _cell_of: Dictionary = {}
var _moving: Dictionary = {}
var _routes: Dictionary = {}
var _parks: Array = []
var _camps: Array = []
var _hospitals: Array = []
var _district_people: Dictionary = {}


# ============================================================ Zugriff

func get_person(id: int) -> Dictionary:
	return persons.get(id, {})


func get_household(id: int) -> Dictionary:
	return households.get(id, {})


func person_name(id: int) -> String:
	var p: Dictionary = persons.get(id, {})
	return "%s %s" % [p.first, p.last] if not p.is_empty() else ""


func age_of(world, id: int) -> float:
	var p: Dictionary = persons.get(id, {})
	return world.age_years(float(p.birth)) if not p.is_empty() else 0.0


func members_of(hid: int) -> Array:
	return households.get(hid, {}).get("members", []).duplicate()


func adults_of(world, hid: int) -> Array:
	var out := []
	for m in households.get(hid, {}).get("members", []):
		if world.age_years(float(persons[m].birth)) >= 18.0:
			out.append(m)
	return out


func home_building(p: Dictionary) -> int:
	var h: Dictionary = households.get(int(p.household), {})
	var pid: int = int(h.get("home", -1))
	if pid >= 0 and _world_ref and _world_ref.housing:
		var prop: Dictionary = _world_ref.housing.properties.get(pid, {})
		if not prop.is_empty():
			return int(prop.building)
	return int(p.get("camp", -1))


func home_pos(world, p: Dictionary) -> Vector2:
	var b := home_building(p)
	if b >= 0:
		return world.city.buildings[b].sidewalk_point
	return world.city.spawn_points[int(p.id) % world.city.spawn_points.size()].pos


var _world_ref = null


# ============================================================ Aufbau

func setup(world) -> void:
	_world_ref = world
	rng.seed = hash("population:%d" % world.seed)
	target_population = int(world.rules.get_rule("population"))
	var t := Time.get_ticks_usec()
	_index_city(world)
	_generate_households(world)
	setup_ms["households"] = (Time.get_ticks_usec() - t) / 1000
	_assign_owners(world)
	_assign_jobs(world)
	setup_ms["jobs"] = (Time.get_ticks_usec() - t) / 1000
	_assign_wealth_and_homes(world)
	setup_ms["homes"] = (Time.get_ticks_usec() - t) / 1000
	_assign_education(world)
	_make_relationships(world)
	_init_finances(world)
	setup_ms["finances"] = (Time.get_ticks_usec() - t) / 1000
	for id in persons:
		_init_schedule(world, persons[id])
	setup_ms["schedules"] = (Time.get_ticks_usec() - t) / 1000
	_last_slot = int(world.time_minutes / 5.0)


func _index_city(world) -> void:
	_parks.clear()
	_camps.clear()
	_hospitals.clear()
	for pk in world.city.parks:
		_parks.append(pk)
	for b in world.city.buildings:
		if b.kind == "homeless_camp":
			_camps.append(b.id)
		elif b.kind in ["hospital", "clinic"]:
			_hospitals.append(b.id)


func _rand_age(lo: float, hi: float, lifespan: float) -> float:
	# Gewichtet mit Ueberlebenswahrscheinlichkeit
	for i in 8:
		var a := rng.randf_range(lo, hi)
		if rng.randf() < LifeStages.survival(a, lifespan):
			return a
	return rng.randf_range(lo, minf(hi, lo + 10.0))


func _new_person(world, hid: int, age: float, body: String, last: String, wealth := 0.5) -> int:
	var id := next_person_id
	next_person_id += 1
	var first: String = CityCatalog.FIRST_NAMES_F[rng.randi() % CityCatalog.FIRST_NAMES_F.size()] if body == "female" \
		else CityCatalog.FIRST_NAMES_M[rng.randi() % CityCatalog.FIRST_NAMES_M.size()]
	var tr := {}
	for t in ["friendly", "shy", "ambitious", "lazy", "risky", "helpful", "suspicious", "social", "lawful"]:
		tr[t] = snappedf(rng.randf(), 0.01)
	tr["criminal"] = snappedf(pow(rng.randf(), 4.0) * (1.0 - float(tr.lawful) * 0.5), 0.01)
	var bt := rng.randf_range(22.0, 24.3)
	var wk := rng.randf_range(6.0, 7.6)
	if age < 12.0:
		bt = rng.randf_range(19.5, 21.0)
		wk = rng.randf_range(6.5, 7.2)
	elif age >= 65.0:
		bt = rng.randf_range(21.0, 23.0)
		wk = rng.randf_range(6.0, 8.0)
	var skills := Skills.empty()
	for k in skills:
		skills[k] = snappedf(clampf(rng.randf_range(0.0, 25.0) * clampf(age / 25.0, 0.1, 1.0), 0.0, 100.0), 0.1)
	if age >= 18.0 and rng.randf() < 0.75:
		skills.driving = maxf(float(skills.driving), rng.randf_range(35.0, 70.0))
	persons[id] = {"id": id, "first": first, "last": last, "body": body, "birth": world.birth_for_age(age),
		"household": hid, "home": -1, "job": {}, "school": -1, "traits": tr,
		"needs": {"hunger": rng.randf_range(55.0, 90.0), "energy": rng.randf_range(55.0, 90.0),
			"hygiene": rng.randf_range(55.0, 95.0), "social": rng.randf_range(40.0, 90.0), "fun": rng.randf_range(40.0, 90.0)},
		"health": rng.randf_range(80.0, 100.0) - maxf(0.0, age - 60.0) * 0.5,
		"appearance": NpcAppearance.generate(rng, body, age, wealth), "activity": "home",
		"location": {"building": -1, "from": Vector2.ZERO, "to": Vector2.ZERO, "depart": 0.0, "arrive": 0.0, "mode": ""},
		"relationships": {}, "alive": true, "homeless": false,
		"criminal_record": 1 if float(tr.criminal) > 0.6 and rng.randf() < 0.5 else 0, "gang": -1,
		"education": "none", "skills": skills, "parents": [], "partner": -1, "children": [],
		"sleep": [bt, wk], "plan": [], "plan_i": 0, "next_t": 0.0, "needs_t": world.time_minutes, "act_start": world.time_minutes,
		"retired": false, "pension": 0.0, "unemployed_since": -1, "homemaker": false, "illness": "", "camp": -1}
	households[hid].members.append(id)
	return id


func _new_household(world, cls_hint := "middle") -> int:
	var hid := next_household_id
	next_household_id += 1
	households[hid] = {"id": hid, "members": [], "account": -1, "home": -1, "wealth": 0.5, "cls": cls_hint,
		"pantry": 0.0, "car": false, "disc": 0.0, "homeless": false, "player_member": false, "shopper": -1,
		"last_shop_day": -1, "district": -1, "founded": world.get_day()}
	households[hid].account = world.ledger.open_account("household", hid, "Haushalt %d" % hid, 0.0)
	return hid


func _rand_body() -> String:
	return "female" if rng.randf() < 0.5 else "male"


func _last_name() -> String:
	return CityCatalog.LAST_NAMES[rng.randi() % CityCatalog.LAST_NAMES.size()]


func _generate_households(world) -> void:
	var L: float = world.rules.f("lifespan")
	var types := [["single_young", 14], ["single_mid", 11], ["single_senior", 9], ["couple_young", 8], ["couple_mid", 9],
		["couple_senior", 7], ["family", 23], ["single_parent", 6], ["flatshare", 4], ["family_adult", 5], ["homeless", 4]]
	var tw := 0.0
	for t in types:
		tw += float(t[1])
	var count := 0
	while count < target_population:
		var r := rng.randf() * tw
		var typ: String = types[0][0]
		for t in types:
			r -= float(t[1])
			if r <= 0.0:
				typ = t[0]
				break
		var hid := _new_household(world)
		var ln := _last_name()
		var h: Dictionary = households[hid]
		match typ:
			"single_young":
				_new_person(world, hid, rng.randf_range(19.0, 34.0), _rand_body(), ln)
			"single_mid":
				_new_person(world, hid, rng.randf_range(35.0, 64.0), _rand_body(), ln)
			"single_senior":
				_new_person(world, hid, _rand_age(65.0, L + 12.0, L), _rand_body(), ln)
			"couple_young", "couple_mid", "couple_senior":
				var a := rng.randf_range(23.0, 40.0) if typ == "couple_young" else (rng.randf_range(40.0, 64.0) if typ == "couple_mid" else _rand_age(65.0, L + 8.0, L))
				_couple(world, hid, a, ln)
			"family", "family_adult":
				var pa := rng.randf_range(27.0, 50.0) if typ == "family" else rng.randf_range(44.0, 60.0)
				var ids := _couple(world, hid, pa, ln)
				var nk := rng.randi_range(1, 3)
				for k in nk:
					var ka := rng.randf_range(0.0, minf(17.9, pa - 19.0))
					if typ == "family_adult" and k == 0:
						ka = rng.randf_range(18.0, minf(27.0, pa - 18.0))
					_child(world, hid, ka, ln, ids)
			"single_parent":
				var pa2 := rng.randf_range(24.0, 50.0)
				var pid := _new_person(world, hid, pa2, "female" if rng.randf() < 0.8 else "male", ln)
				for k in rng.randi_range(1, 2):
					_child(world, hid, rng.randf_range(0.0, minf(17.9, pa2 - 19.0)), ln, [pid])
			"flatshare":
				for k in rng.randi_range(2, 3):
					_new_person(world, hid, rng.randf_range(19.0, 30.0), _rand_body(), _last_name())
			"homeless":
				_new_person(world, hid, rng.randf_range(22.0, 62.0), "male" if rng.randf() < 0.7 else "female", ln, 0.05)
				h.homeless = true
				h.cls = "poor"
		count += h.members.size()
		h.shopper = h.members[0]


func _couple(world, hid: int, age: float, ln: String) -> Array:
	var a := _new_person(world, hid, age, "female", ln)
	var b := _new_person(world, hid, clampf(age + rng.randf_range(-4.0, 5.0), 18.0, 105.0), "male", ln)
	persons[a].partner = b
	persons[b].partner = a
	persons[a].relationships[b] = rng.randi_range(65, 100)
	persons[b].relationships[a] = rng.randi_range(65, 100)
	return [a, b]


func _child(world, hid: int, age: float, ln: String, parents: Array) -> int:
	var c := _new_person(world, hid, maxf(0.05, age), _rand_body(), ln)
	persons[c].parents = parents.duplicate()
	for par in parents:
		persons[par].children.append(c)
		persons[par].relationships[c] = rng.randi_range(60, 100)
		persons[c].relationships[par] = rng.randi_range(50, 100)
	if parents.size() > 0:
		var a: Dictionary = persons[parents[0]].appearance
		var b: Dictionary = persons[parents[-1]].appearance
		var app := NpcAppearance.child_of(rng, persons[c].body, a, b)
		var gen := NpcAppearance.generate(rng, persons[c].body, age)
		gen.skin = app.skin
		gen.hair_color = app.hair_color
		gen.eye_color = app.eye_color
		persons[c].appearance = gen
	return c


func _retire_age(world) -> float:
	return world.government.policy("retirement_age") if world.government else 66.0


func _assign_owners(world) -> void:
	var eco = world.economy
	var jobs = world.jobs
	if eco == null or jobs == null:
		return
	var cands := []
	for hid in households:
		var h: Dictionary = households[hid]
		if h.homeless:
			continue
		for m in h.members:
			var a = world.age_years(float(persons[m].birth))
			if a >= 26.0 and a < 63.0:
				cands.append(m)
				break
	for bid in eco.businesses:
		var b: Dictionary = eco.businesses[bid]
		if b.owner.kind != "external":
			continue
		var pid: int = jobs.owner_position(bid)
		if cands.is_empty() or pid < 0:
			b.owner = {"kind": "city", "id": 0} if pid < 0 else b.owner
			continue
		var k := rng.randi() % cands.size()
		var person: int = cands[k]
		cands.remove_at(k)
		var hid: int = int(persons[person].household)
		b.owner = {"kind": "household", "id": hid}
		persons[person].education = "vocational" if rng.randf() < 0.6 else "school"
		persons[person].skills.business = maxf(float(persons[person].skills.business), rng.randf_range(25.0, 55.0))
		jobs.hire(world, "npc", person, pid, rng.randi_range(0, 1))
		households[hid].owner_business = true
	# Unbesetzte Inhaberstellen werden normale Stellen
	for bid in eco.businesses:
		var b2: Dictionary = eco.businesses[bid]
		if b2.owner.kind == "external":
			b2.owner = {"kind": "city", "id": 0}


func _edu_for_age(age: float) -> String:
	var r := rng.randf()
	var lvl := "none"
	if r < 0.07:
		lvl = "none"
	elif r < 0.30:
		lvl = "school"
	elif r < 0.66:
		lvl = "vocational"
	elif r < 0.83:
		lvl = "bachelor"
	elif r < 0.96:
		lvl = "master"
	else:
		lvl = "doctor"
	if age < 21.0 and Occupations.edu_rank(lvl) > 2:
		lvl = "vocational"
	if age < 23.0 and Occupations.edu_rank(lvl) > 3:
		lvl = "bachelor"
	if age < 27.0 and lvl == "doctor":
		lvl = "master"
	if age < 16.0:
		lvl = "none"
	elif age < 19.0 and Occupations.edu_rank(lvl) > 1:
		lvl = "school"
	return lvl


func _assign_jobs(world) -> void:
	var jobs = world.jobs
	var retire := _retire_age(world)
	var pools := {}
	for lvl in Occupations.EDUCATION_LEVELS:
		pools[lvl] = []
	for id in persons:
		var p: Dictionary = persons[id]
		var a = world.age_years(float(p.birth))
		if a < 16.0:
			continue
		if p.education == "none":
			p.education = _edu_for_age(a)
		if not p.job.is_empty():
			continue
		if a >= retire:
			p.retired = true
			continue
		if a < 18.0:
			continue
		var h: Dictionary = households[int(p.household)]
		if h.homeless:
			if rng.randf() < 0.85:
				p.unemployed_since = 0
				continue
		# Studierende (18-25) und Hausleute werden spaeter behandelt
		if a < 25.0 and rng.randf() < 0.3 and Occupations.edu_rank(p.education) >= 1:
			p.student_plan = true
			continue
		if p.partner >= 0 and rng.randf() < 0.05:
			p.homemaker = true
			continue
		pools[p.education].append(id)
	if jobs == null:
		return
	for lvl in pools:
		_shuffle(pools[lvl])
	var pos_ids := []
	for pid in jobs.positions:
		var pos: Dictionary = jobs.positions[pid]
		if pos.active and not pos.reserved_owner and pos.holder_kind == "":
			pos_ids.append(pid)
	pos_ids.sort_custom(func(a, b): return Occupations.edu_rank(Occupations.OCCUPATIONS[jobs.positions[a].occupation].education) \
		> Occupations.edu_rank(Occupations.OCCUPATIONS[jobs.positions[b].occupation].education))
	# leicht unter Vollbeschaeftigung: ein Teil der Stellen bleibt offen
	var fill := 0.95
	for pid in pos_ids:
		if rng.randf() > fill:
			continue
		var pos: Dictionary = jobs.positions[pid]
		var occ: Dictionary = Occupations.OCCUPATIONS[pos.occupation]
		var req := Occupations.edu_rank(occ.education)
		var chosen := -1
		for attempt in 4:
			var lvl_i := req
			if rng.randf() < 0.3 or pools[Occupations.EDUCATION_LEVELS[lvl_i]].is_empty():
				lvl_i = mini(5, req + rng.randi_range(0, 2))
			for j in range(lvl_i, 6):
				var pool: Array = pools[Occupations.EDUCATION_LEVELS[j]]
				if pool.is_empty():
					continue
				var cand: int = pool[pool.size() - 1]
				if world.age_years(float(persons[cand].birth)) >= float(occ.min_age):
					pool.pop_back()
					chosen = cand
					break
			if chosen >= 0:
				break
		if chosen < 0:
			continue
		var p: Dictionary = persons[chosen]
		for s in occ.skills:
			p.skills[s] = maxf(float(p.skills[s]), float(occ.skills[s]) + rng.randf_range(0.0, 25.0))
		if not occ.criminal_record_ok:
			p.criminal_record = 0
		var a = world.age_years(float(p.birth))
		var rank := clampi(int((a - 22.0) / 14.0 * rng.randf_range(0.4, 1.3)), 0, occ.ranks.size() - 1)
		jobs.hire(world, "npc", chosen, pid, rank)
	for lvl in pools:
		for id in pools[lvl]:
			persons[id].unemployed_since = 0


func _shuffle(a: Array) -> void:
	for i in range(a.size() - 1, 0, -1):
		var j := rng.randi() % (i + 1)
		var t = a[i]
		a[i] = a[j]
		a[j] = t


## Woechentliches Nettoeinkommen (Loehne, Renten, Leistungen) eines Haushalts.
func household_income_week(world, hid: int) -> float:
	var h: Dictionary = households.get(hid, {})
	if h.is_empty():
		return 0.0
	var jobs = world.jobs
	var gov = world.government
	var s := 0.0
	for m in h.members:
		var p: Dictionary = persons[m]
		if not p.job.is_empty() and jobs:
			var pos: Dictionary = jobs.positions.get(int(p.job.position), {})
			if not pos.is_empty():
				var w: float = jobs.hourly_wage(world, pos)
				var gross: float = w * float(pos.shift_hours) * float(pos.days.size())
				var keep := 0.72
				if gov:
					keep = 1.0 - gov.income_tax_rate(w) - gov.rate("pension_contribution")
				s += gross * keep
		elif p.retired:
			s += float(p.pension) * 7.0
		elif int(p.unemployed_since) >= 0 and gov:
			s += gov.policy("unemployment_benefit") * 7.0
	if h.get("player_member", false) and world.player:
		s += 0.0
	return s


func _assign_wealth_and_homes(world) -> void:
	var housing = world.housing
	var gov = world.government
	# Pensionen
	for id in persons:
		var p: Dictionary = persons[id]
		if p.retired:
			var est: float = 14.0 + Occupations.edu_rank(p.education) * 4.0
			var rate = gov.policy("pension_rate") if gov else 0.48
			p.pension = clampf(est * 8.0 * rate * world.rules.f("wage_level"), gov.policy("min_pension") if gov else 30.0,
				gov.policy("max_pension") if gov else 140.0)
	# Klassen nach Einkommen pro Kopf
	var arr := []
	for hid in households:
		var h: Dictionary = households[hid]
		var inc := household_income_week(world, hid)
		h.income_week = inc
		var per := inc / maxf(1.0, float(h.members.size()) * 0.7)
		arr.append([per + rng.randf_range(0.0, 120.0), hid])
	arr.sort_custom(func(a, b): return a[0] < b[0])
	var n := arr.size()
	for i in n:
		var h: Dictionary = households[arr[i][1]]
		if h.homeless:
			h.cls = "poor"
			h.wealth = 0.02
			continue
		var q := float(i) / float(n)
		var cls := "poor" if q < 0.1 else ("lower" if q < 0.33 else ("middle" if q < 0.78 else ("upper" if q < 0.965 else "rich")))
		if h.get("owner_business", false) and CLASSES.find(cls) < 2:
			cls = "middle"
		h.cls = cls
		h.wealth = clampf(q, 0.02, 0.99)
	if housing == null:
		return
	# Wohnungen nach Budget vergeben
	var buckets := {"room": [], "apartment": [], "house": [], "villa": [], "estate": []}
	for pid in housing.properties:
		var pr: Dictionary = housing.properties[pid]
		if buckets.has(pr.kind):
			buckets[pr.kind].append(pid)
	for k in buckets:
		var pairs := []
		for pid in buckets[k]:
			pairs.append([float(housing.properties[pid].rent_per_week), pid])
		pairs.sort()
		var ids2 := []
		var rents := PackedFloat32Array()
		for e in pairs:
			ids2.append(e[1])
			rents.append(e[0])
		buckets[k] = ids2
		_rent_cache[k] = rents
	var taken := {}
	var order := []
	for e in arr:
		order.push_front(e[1])
	var bank = world.economy.bank if world.economy else null
	for hid in order:
		var h: Dictionary = households[hid]
		if h.homeless:
			continue
		var size: int = h.members.size()
		var need_rooms := clampi(int(ceil(size * 0.7)), 1, 6)
		var inc: float = float(h.income_week)
		var budget := maxf(90.0, inc * rng.randf_range(0.26, 0.38))
		var kinds := []
		var own := false
		match h.cls:
			"rich":
				kinds = ["estate", "villa", "house"]
				own = true
				budget = 99999.0
			"upper":
				kinds = ["house", "apartment"] if size >= 2 else ["apartment", "house"]
				own = rng.randf() < 0.7
				budget *= 1.5
			"middle":
				kinds = ["house", "apartment"] if size >= 3 and rng.randf() < 0.45 else ["apartment"]
				own = kinds[0] == "house" and rng.randf() < 0.6
			_:
				kinds = ["apartment", "room"] if size >= 2 else (["room", "apartment"] if rng.randf() < 0.5 else ["apartment", "room"])
		if size >= 2 and h.members.size() == 2 and world.age_years(float(persons[h.members[0]].birth)) < 31.0 and h.cls in ["poor", "lower"]:
			kinds = ["apartment", "room"]
		var chosen := -1
		for kind in kinds:
			var lst: Array = buckets[kind]
			if lst.is_empty():
				continue
			var hi := lst.size() - 1
			if not own:
				hi = _upper_index(housing, lst, budget, kind)
			if hi < 0:
				continue
			var lo := 0 if own else _upper_index(housing, lst, budget * 0.45, kind)
			lo = maxi(0, lo)
			if own:
				lo = int(hi * clampf(float(h.wealth) - 0.3, 0.0, 0.9))
			var best := -1
			var bs := -1e9
			for t in 36:
				var idx := rng.randi_range(lo, maxi(lo, hi))
				var cid: int = lst[idx]
				if taken.has(cid):
					continue
				var pr: Dictionary = housing.properties[cid]
				var sc: float = float(pr.quality) * 0.6 + float(pr.wealth) * 0.5 - absf(int(pr.rooms) - need_rooms) * 0.25
				if kind == "room" and size > 1:
					sc -= 2.0
				if sc > bs:
					bs = sc
					best = cid
			if best >= 0:
				chosen = best
				break
		if chosen < 0:
			# Notfall: guenstigste freie Wohnung
			for kind in ["apartment", "room", "house"]:
				for cid in buckets[kind]:
					if not taken.has(cid):
						chosen = cid
						break
				if chosen >= 0:
					break
		if chosen < 0:
			h.homeless = true
			continue
		taken[chosen] = true
		var pr2: Dictionary = housing.properties[chosen]
		var owner_occ: bool = own and pr2.kind in ["house", "villa", "estate", "apartment"]
		housing.assign("household", hid, chosen, owner_occ)
		h.home = chosen
		h.district = int(pr2.district)
		if owner_occ and bank and h.cls != "rich" and rng.randf() < 0.55:
			bank.register_existing(world, "household", hid, int(h.account), float(pr2.price) * rng.randf_range(0.15, 0.65), "mortgage", chosen)
	# Obdachlose: Notunterkunft oder Zeltlager
	for hid in households:
		var h: Dictionary = households[hid]
		if not h.homeless:
			continue
		var bed: int = housing.free_shelter_bed()
		for m in h.members:
			persons[m].homeless = true
			if not _camps.is_empty():
				persons[m].camp = _camps[m % _camps.size()]
		if bed >= 0 and rng.randf() < 0.6:
			housing.assign("household", hid, bed)
			h.home = bed
	# Hintergrundbewohner fuellen leere Einheiten bis auf ~10 % Leerstand
	var vac: Array = housing.vacant_rentals()
	_shuffle(vac)
	var keep := int(housing.properties.size() * HousingMarket.BACKGROUND_VACANCY)
	for i in maxi(0, vac.size() - keep):
		housing.set_background(vac[i])
	# Vermieter: einige Mehrfamilienhaeuser gehoeren wohlhabenden Haushalten
	var rich := []
	for hid in households:
		if households[hid].cls in ["rich", "upper"]:
			rich.append(hid)
	if not rich.is_empty():
		var by_b := {}
		for pid in housing.properties:
			var pr3: Dictionary = housing.properties[pid]
			if pr3.owner.kind == "company" and pr3.kind in ["apartment", "room"]:
				by_b[int(pr3.building)] = true
		for bid in by_b:
			if rng.randf() < 0.15:
				var lord: int = rich[rng.randi() % rich.size()]
				for pid in housing.properties_in_building(bid):
					housing.properties[pid].owner = {"kind": "household", "id": lord}
		# Firmen- und Bankeigentuemer
		if world.economy:
			for cid in world.economy.companies:
				if rng.randf() < 0.5:
					world.economy.companies[cid].owner = {"kind": "household", "id": rich[rng.randi() % rich.size()]}
			world.economy.bank.owner = {"kind": "household", "id": rich[rng.randi() % rich.size()]}
	for hid in households:
		var h: Dictionary = households[hid]
		h.car = h.cls in ["middle", "upper", "rich"] and rng.randf() < (0.4 + 0.15 * CLASSES.find(h.cls))
		for m in h.members:
			persons[m].home = h.home


var _rent_cache := {}


func _upper_index(housing, lst: Array, budget: float, kind := "") -> int:
	var rents: PackedFloat32Array = _rent_cache.get(kind, PackedFloat32Array())
	if rents.is_empty() or rents[0] > budget:
		return -1
	return rents.bsearch(budget, false) - 1


func _assign_education(world) -> void:
	var edu = world.education
	if edu == null:
		return
	var school_age: float = world.government.policy("school_age") if world.government else 6.0
	for id in persons:
		var p: Dictionary = persons[id]
		var a = world.age_years(float(p.birth))
		if a >= school_age and a < 18.0:
			if a >= 16.0:
				p.education = "school" if rng.randf() < 0.5 else "none"
				if p.education == "school" and rng.randf() < 0.5:
					_start_training(world, p, "vocational")
					continue
			p.school = edu.school_for(world, home_pos(world, p))
			edu.enroll(world, "npc", id, "school")
		elif p.get("student_plan", false):
			p.erase("student_plan")
			var prog := "bachelor" if p.education in ["school", "vocational"] else ("master" if p.education == "bachelor" else "vocational")
			if a < 20.0 and rng.randf() < 0.4:
				prog = "vocational"
			_start_training(world, p, prog)


func _start_training(world, p: Dictionary, prog: String) -> void:
	var edu = world.education
	if Occupations.edu_rank(p.education) < 1:
		p.education = "school"
	var res: Dictionary = edu.enroll(world, "npc", int(p.id), prog)
	if not res.ok:
		p.unemployed_since = world.get_day()
		return
	var pr: Dictionary = Education.PROGRAMS[prog]
	p.school = edu.university() if pr.place == "university" else edu.school_for(world, home_pos(world, p))
	if int(p.school) < 0:
		edu.drop_out(world, "npc", int(p.id))
		p.unemployed_since = world.get_day()
	else:
		p.unemployed_since = -1


func _make_relationships(world) -> void:
	_district_people.clear()
	for id in persons:
		var p: Dictionary = persons[id]
		var d: int = int(households[int(p.household)].district)
		if not _district_people.has(d):
			_district_people[d] = []
		_district_people[d].append(id)
	var jobs = world.jobs
	for id in persons:
		var p: Dictionary = persons[id]
		if world.age_years(float(p.birth)) < 5.0:
			continue
		var d: int = int(households[int(p.household)].district)
		var pool: Array = _district_people[d]
		var n := rng.randi_range(1, 2) + int(float(p.traits.social) * 3.0)
		for i in n:
			var other: int = pool[rng.randi() % pool.size()]
			if other == id and not p.job.is_empty() and jobs:
				var col: Array = jobs.positions_in_building(int(p.job.building))
				var cp: Dictionary = jobs.positions[col[rng.randi() % col.size()]]
				if cp.holder_kind == "npc":
					other = int(cp.holder)
			if other == id or p.relationships.has(other):
				continue
			var v := rng.randi_range(25, 80)
			p.relationships[other] = v
			persons[other].relationships[id] = v


func _init_finances(world) -> void:
	var L: Ledger = world.ledger
	var gov = world.government
	var pensions := 0.0
	for hid in households:
		var h: Dictionary = households[hid]
		var inc: float = float(h.get("income_week", 0.0))
		var weeks := 0.0
		match h.cls:
			"poor": weeks = rng.randf_range(0.2, 1.5)
			"lower": weeks = rng.randf_range(0.8, 4.0)
			"middle": weeks = rng.randf_range(3.0, 14.0)
			"upper": weeks = rng.randf_range(10.0, 40.0)
		var amount := inc * weeks + 150.0
		if h.cls == "rich":
			amount = rng.randf_range(250000.0, 2500000.0)
		if h.homeless:
			amount = rng.randf_range(0.0, 40.0)
		L.mint(int(h.account), amount, "Startvermögen")
		h.pantry = float(h.members.size()) * rng.randf_range(2.0, 6.0)
		for m in h.members:
			if persons[m].retired:
				pensions += float(persons[m].pension)
	if gov and world.jobs:
		var wages := 0.0
		for d in gov.wage_need:
			wages += float(gov.wage_need[d])
		gov.initial_funding(world, wages / 0.62, pensions)


func _init_schedule(world, p: Dictionary) -> void:
	var now: float = world.time_minutes
	var day := int(floor(now / 1440.0))
	var t0 := (day - 1) * 1440.0 + float(p.sleep[0]) * 60.0
	p.plan = NpcSchedule.make_plan(world, self, p, t0)
	p.plan_i = 0
	var hp := home_pos(world, p)
	p.location = {"building": home_building(p), "from": hp, "to": hp, "depart": t0, "arrive": t0, "mode": ""}
	p.activity = "sleep"
	# ohne Effekte bis jetzt vorspulen
	var i := 0
	while i + 1 < p.plan.size() and float(p.plan[i + 1][0]) <= now:
		i += 1
	if i == p.plan.size() - 1:
		# Plan endete vor jetzt (Nachtmensch): ab dem Einschlafen neu planen
		p.plan = NpcSchedule.make_plan(world, self, p, float(p.plan[i][0]))
		i = 0
		while i + 1 < p.plan.size() and float(p.plan[i + 1][0]) <= now:
			i += 1
	p.plan_i = i
	var e: Array = p.plan[i]
	var act: String = e[1]
	p.activity = act
	p.act_start = float(e[0])
	if act == "commute" and i + 1 < p.plan.size():
		var prev: Vector2 = p.plan[i - 1][3] if i > 0 else hp
		var prev_b: int = int(p.plan[i - 1][2]) if i > 0 else home_building(p)
		p.location = {"building": -1, "from": prev, "to": e[3], "depart": float(e[0]), "arrive": float(p.plan[i + 1][0]),
			"mode": e[4], "from_b": prev_b, "to_b": int(e[2])}
		_moving[int(p.id)] = true
	else:
		p.location = {"building": int(e[2]), "from": e[3], "to": e[3], "depart": float(e[0]), "arrive": float(e[0]), "mode": ""}
		_grid_add(int(p.id), e[3])
	if i + 1 < p.plan.size():
		_schedule(p, float(p.plan[i + 1][0]))
	else:
		_schedule(p, now + 5.0)


# ============================================================ Zeitschlange / Raumindex

func _schedule(p: Dictionary, t: float) -> void:
	p.next_t = t
	var slot := int(ceil(t / 5.0 - 0.0001))
	if slot <= _last_slot:
		slot = _last_slot + 1
	if not _queue.has(slot):
		_queue[slot] = []
	_queue[slot].append(int(p.id))


func _grid_add(id: int, pos: Vector2) -> void:
	var c := Vector2i(floori(pos.x / CELL), floori(pos.y / CELL))
	if not _grid.has(c):
		_grid[c] = {}
	_grid[c][id] = true
	_cell_of[id] = c


func _grid_remove(id: int) -> void:
	if _cell_of.has(id):
		var c: Vector2i = _cell_of[id]
		if _grid.has(c):
			_grid[c].erase(id)
		_cell_of.erase(id)


# ============================================================ Takt

func step(world, dt: float) -> void:
	_world_ref = world
	var now: float = world.time_minutes
	var slot := int(floor(now / 5.0 + 0.0001))
	while _last_slot < slot:
		_last_slot += 1
		if not _queue.has(_last_slot):
			continue
		var lst: Array = _queue[_last_slot]
		_queue.erase(_last_slot)
		for id in lst:
			var p = persons.get(id)
			if p == null or not p.alive:
				continue
			if float(p.next_t) <= now + 0.01:
				_advance(world, p, now)


func _advance(world, p: Dictionary, now: float) -> void:
	var guard := 0
	while guard < 30:
		guard += 1
		var i: int = int(p.plan_i) + 1
		if i >= p.plan.size():
			p.plan = NpcSchedule.make_plan(world, self, p, now)
			p.plan_i = 0
			if p.plan.size() < 2:
				_schedule(p, now + 60.0)
				return
			continue
		var e: Array = p.plan[i]
		if float(e[0]) > now + 0.01:
			_schedule(p, float(e[0]))
			return
		_exit_activity(world, p, now)
		p.plan_i = i
		var nxt := float(p.plan[i + 1][0]) if i + 1 < p.plan.size() else now + 5.0
		_enter(world, p, e, now, nxt)
		if not p.alive:
			return
		if i == p.plan.size() - 1:
			# Einschlafen: neuen Plan ab jetzt
			p.plan = NpcSchedule.make_plan(world, self, p, now)
			p.plan_i = 0
			if p.plan.size() > 1:
				_schedule(p, float(p.plan[1][0]))
			else:
				_schedule(p, now + 60.0)
			return


func _update_needs(world, p: Dictionary, now: float) -> void:
	var dt := (now - float(p.needs_t)) / 60.0
	p.needs_t = now
	if dt <= 0.0:
		return
	var n: Dictionary = p.needs
	var f: float = world.rules.f("needs_decay")
	if p.activity == "sleep":
		n.energy = minf(100.0, float(n.energy) + 12.5 * dt)
		n.hunger = maxf(0.0, float(n.hunger) - 1.8 * dt * f)
		n.hygiene = maxf(0.0, float(n.hygiene) - 0.8 * dt * f)
	else:
		n.energy = maxf(0.0, float(n.energy) - 5.0 * dt * f)
		n.hunger = maxf(0.0, float(n.hunger) - 5.0 * dt * f)
		n.hygiene = maxf(0.0, float(n.hygiene) - 2.6 * dt * f)
		var soc := -1.6
		var fun := -2.2
		match p.activity:
			"work", "school":
				soc = 1.5
			"leisure":
				soc = 6.0
				fun = 9.0
		n.social = clampf(float(n.social) + soc * dt, 0.0, 100.0)
		n.fun = clampf(float(n.fun) + fun * dt, 0.0, 100.0)
	if world.rules.b("needs_can_harm") and (float(n.hunger) < 8.0 or float(n.energy) < 3.0):
		p.health = maxf(1.0, float(p.health) - 1.5 * dt)


func _account(p: Dictionary) -> int:
	return int(households.get(int(p.household), {}).get("account", -1))


func _eat_home(p: Dictionary, amount: float) -> bool:
	var h: Dictionary = households.get(int(p.household), {})
	if float(h.get("pantry", 0.0)) >= 1.0:
		h.pantry = float(h.pantry) - 1.0
		p.needs.hunger = minf(100.0, float(p.needs.hunger) + amount)
		return true
	return false


func _enter(world, p: Dictionary, e: Array, now: float, next_t: float) -> void:
	_update_needs(world, p, now)
	var id: int = int(p.id)
	var act: String = e[1]
	p.activity = act
	p.act_start = now
	if act == "commute":
		var from: Vector2 = p.location.to
		var fb: int = int(p.location.building)
		_grid_remove(id)
		_moving[id] = true
		p.location = {"building": -1, "from": from, "to": e[3], "depart": now, "arrive": maxf(next_t, now + 1.0),
			"mode": e[4], "from_b": fb, "to_b": int(e[2])}
		if e[4] == "bus" and world.government:
			world.government.collect_fee(world, _account(p), "transit", world.government.modifier("bus_fare"), "Busfahrkarte")
		return
	_moving.erase(id)
	var pos: Vector2 = e[3]
	p.location = {"building": int(e[2]), "from": pos, "to": pos, "depart": now, "arrive": now, "mode": ""}
	_grid_remove(id)
	_grid_add(id, pos)
	var h: Dictionary = households.get(int(p.household), {})
	var purpose: String = e[5]
	var eco = world.economy
	match act:
		"home":
			if float(p.needs.hunger) < 60.0:
				_eat_home(p, 45.0)
			if purpose == "evening" or float(p.needs.hygiene) < 50.0:
				p.needs.hygiene = minf(100.0, float(p.needs.hygiene) + 60.0)
		"shop":
			if eco == null:
				return
			var bid: int = eco.business_for_building(int(e[2]))
			if bid < 0:
				return
			var acc := _account(p)
			if purpose == "food":
				var members: int = h.members.size() + (1 if h.get("player_member", false) else 0)
				var deficit := maxf(0.0, members * 6.0 - float(h.pantry))
				var cf: float = CLASS_MEAL[maxi(0, CLASSES.find(h.cls))]
				var budget = deficit * GoodsCatalog.MEAL_COST * cf * world.rules.f("price_level") * 1.1
				var res: Dictionary = eco.npc_basket(world, acc, bid, budget, "food")
				h.pantry = float(h.pantry) + float(res.meals)
				h.last_shop_day = world.get_day()
				day_stats["food_spend"] = float(day_stats.get("food_spend", 0.0)) + float(res.spent)
			else:
				var budget2 := minf(float(h.disc), world.ledger.balance(acc) * 0.5)
				if purpose == "fuel":
					budget2 = 40.0
				var res2: Dictionary = eco.npc_basket(world, acc, bid, budget2, "durable" if purpose != "fuel" else "")
				if purpose != "fuel":
					h.disc = maxf(0.0, float(h.disc) - float(res2.spent))
				p.needs.fun = minf(100.0, float(p.needs.fun) + 10.0)
				if eco.businesses[bid].type == "car_dealer" and float(res2.spent) > 1000.0:
					h.car = true
		"leisure":
			if purpose == "friend":
				p.needs.social = minf(100.0, float(p.needs.social) + 25.0)
				return
			if eco == null or int(e[2]) < 0:
				p.needs.fun = minf(100.0, float(p.needs.fun) + 10.0)
				return
			var bid2: int = eco.business_for_building(int(e[2]))
			if bid2 < 0:
				return
			var cf2: float = CLASS_MEAL[maxi(0, CLASSES.find(h.cls))]
			var want: float = {"cafe": 9.0, "bar": 16.0, "restaurant": 26.0, "cinema": 15.0, "gym": 9.0, "fastfood": 8.0,
				"night_market": 10.0, "bakery": 6.0}.get(eco.businesses[bid2].type, 10.0) * cf2
			var res3: Dictionary = eco.npc_basket(world, _account(p), bid2, minf(want * 1.3, maxf(float(h.disc), 6.0)), "leisure")
			h.disc = maxf(0.0, float(h.disc) - float(res3.spent))
			_apply_needs(p, res3.needs)
		"hospital":
			p.needs.energy = minf(100.0, float(p.needs.energy) + 10.0)


func _apply_needs(p: Dictionary, nd: Dictionary) -> void:
	for k in nd:
		if p.needs.has(k):
			p.needs[k] = clampf(float(p.needs[k]) + float(nd[k]), 0.0, 100.0)
		elif k == "health":
			p.health = clampf(float(p.health) + float(nd[k]) * 0.3, 0.0, 100.0)


func _exit_activity(world, p: Dictionary, now: float) -> void:
	var act: String = p.activity
	_update_needs(world, p, now)
	var hours := (now - float(p.act_start)) / 60.0
	match act:
		"sleep":
			if not _eat_home(p, 40.0) and float(p.needs.hunger) < 30.0 and p.get("homeless", false):
				p.needs.hunger = minf(100.0, float(p.needs.hunger) + 20.0)
		"work":
			if world.jobs and not p.job.is_empty() and hours > 0.2:
				var pos: Dictionary = world.jobs.positions.get(int(p.job.position), {})
				var hw := minf(hours, float(pos.get("shift_hours", 8.0)))
				var res: Dictionary = world.jobs.work(world, "npc", int(p.id), hw)
				day_stats["wages"] = float(day_stats.get("wages", 0.0)) + float(res.get("gross", 0.0))
			if hours >= 5.0:
				_lunch(world, p)
		"school":
			if world.education and hours > 0.5:
				var enr: Dictionary = world.education.enrollment_of("npc", int(p.id))
				if not enr.is_empty() and enr.program != "school":
					var r: Dictionary = world.education.attend(world, "npc", int(p.id), hours)
					if r.get("completed", false):
						p.school = -1
						p.unemployed_since = world.get_day()
						if r.get("degree", "") == "bachelor" and rng.randf() < 0.35 + float(p.traits.ambitious) * 0.3:
							_start_training(world, p, "master")
			p.needs.hunger = minf(100.0, float(p.needs.hunger) + 35.0)
		"wander":
			if p.get("homeless", false):
				_beg(world, p)
	if act in ["work", "school", "leisure", "shop"] and float(p.needs.hunger) < 25.0 and act != "work":
		_eat_home(p, 30.0)


func _lunch(world, p: Dictionary) -> void:
	var h: Dictionary = households.get(int(p.household), {})
	var cls_i := maxi(0, CLASSES.find(h.get("cls", "middle")))
	if rng.randf() < 0.55 - cls_i * 0.1 and _eat_home(p, 40.0):
		return
	var eco = world.economy
	if eco == null or p.job.is_empty():
		return
	var d: int = int(world.city.buildings[int(p.job.building)].district)
	var lst: Array = eco.near(d, "lunch")
	if lst.is_empty():
		return
	var bid: int = lst[rng.randi() % mini(4, lst.size())]
	if not eco.is_open(world, bid):
		return
	var res: Dictionary = eco.npc_basket(world, _account(p), bid, 7.0 + cls_i * 3.0, "lunch")
	_apply_needs(p, res.needs)


func _beg(world, p: Dictionary) -> void:
	var L: Ledger = world.ledger
	var acc := _account(p)
	var ids := households.keys()
	for t in 5:
		var hid: int = ids[rng.randi() % ids.size()]
		var h: Dictionary = households[hid]
		if not h.homeless and L.balance(int(h.account)) > 2000.0:
			L.transfer(int(h.account), acc, snappedf(rng.randf_range(3.0, 14.0), 0.5), "Spende")
			break


## Erledigungen nach Arbeit/Schule: [{act, building, pos, duration, purpose, open_check?}]
func plan_errands(world, p: Dictionary, h: Dictionary, age: float, cur_t: float, bed: float, free_day: bool, D: int) -> Array:
	var out := []
	var eco = world.economy
	var d: int = int(h.get("district", -1))
	if d < 0:
		d = int(world.city.district_at(home_pos(world, p)).get("id", 0))
	if age < 6.0:
		return out
	if p.get("homeless", false):
		var spot := _beg_spot(world)
		if not spot.is_empty():
			out.append({"act": "wander", "building": -1, "pos": spot.pos, "duration": rng.randf_range(150.0, 300.0), "purpose": "beg"})
		if eco and world.ledger.balance(_account(p)) > 6.0:
			var f := _pick_near(world, d, "fastfood")
			if f >= 0:
				out.append(_venue(world, f, "shop", 25.0, "lunch"))
		return out
	# Lebensmitteleinkauf
	var members: int = h.members.size()
	if age >= 16.0 and eco and float(h.pantry) < members * 3.0 and int(h.last_shop_day) != D \
			and (int(h.shopper) == int(p.id) or rng.randf() < 0.25):
		var s := _pick_food(world, d, h)
		if s >= 0:
			out.append(_venue(world, s, "shop", rng.randf_range(20.0, 40.0), "food"))
			h.last_shop_day = D
	# Tanken
	if h.get("car", false) and age >= 18.0 and (D + int(h.id)) % 5 == 0 and int(h.shopper) == int(p.id) and eco:
		var g := _pick_near(world, d, "fuel")
		if g >= 0:
			out.append(_venue(world, g, "shop", 10.0, "fuel"))
	# Anschaffungen
	if age >= 18.0 and eco and float(h.disc) > 60.0 and rng.randf() < 0.3:
		var cat := "luxury" if h.cls == "rich" and rng.randf() < 0.5 else ("cars" if float(h.disc) > 5000.0 and not h.car else ("pharmacy" if rng.randf() < 0.2 else "durable"))
		var s2 := _pick_near(world, d, cat)
		if s2 >= 0:
			out.append(_venue(world, s2, "shop", rng.randf_range(25.0, 50.0), "durable"))
	# Freizeit
	var chance := 0.15 + float(p.traits.social) * 0.3 + (1.0 - float(p.needs.fun) / 100.0) * 0.3 + (0.2 if free_day else 0.0)
	if p.retired:
		chance += 0.15
	if age < 12.0:
		chance = 0.3 if cur_t < D * 1440.0 + 17.0 * 60.0 else 0.0
	if rng.randf() < chance:
		var r := rng.randf()
		var money := float(h.disc) > 12.0
		if r < 0.3 or not money or age < 12.0:
			if r < 0.15 and not p.relationships.is_empty() and age >= 10.0:
				var fr := _friend_home(world, p)
				if not fr.is_empty():
					out.append({"act": "leisure", "building": fr.building, "pos": fr.pos, "duration": rng.randf_range(60.0, 150.0), "purpose": "friend"})
					return out
			var pk := _park_point(world, home_pos(world, p))
			if not pk.is_empty():
				out.append({"act": "leisure", "building": -1, "pos": pk.pos, "duration": rng.randf_range(40.0, 120.0), "purpose": "park"})
		else:
			var cats := ["cafe", "bar", "restaurant", "cinema", "gym", "fastfood"]
			var w := [3.0, 2.0 if age >= 18.0 else 0.0, 1.5 + CLASSES.find(h.cls) * 0.5, 1.2, 1.0 + float(p.traits.ambitious), 1.5 if age < 30.0 else 0.6]
			if age >= 65.0:
				w = [4.0, 0.3, 1.5, 0.6, 0.2, 0.3]
			var cat2 := _weighted(cats, w)
			var v := _pick_near(world, d, cat2)
			if v >= 0:
				out.append(_venue(world, v, "leisure", rng.randf_range(50.0, 140.0), cat2))
	return out


func _weighted(items: Array, w: Array) -> String:
	var t := 0.0
	for x in w:
		t += float(x)
	var r := rng.randf() * t
	for i in items.size():
		r -= float(w[i])
		if r <= 0.0:
			return items[i]
	return items[0]


func _venue(world, bid: int, act: String, dur: float, purpose: String) -> Dictionary:
	var b: Dictionary = world.economy.businesses[bid]
	return {"act": act, "building": int(b.building), "pos": b.pos, "duration": dur, "purpose": purpose, "open_check": bid}


func _pick_near(world, d: int, cat: String) -> int:
	var lst: Array = world.economy.near(d, cat)
	if lst.is_empty():
		return -1
	var bid: int = lst[rng.randi() % mini(4, lst.size())]
	return -1 if world.economy.businesses[bid].status == "bankrupt" else bid


func _pick_food(world, d: int, h: Dictionary) -> int:
	var lst: Array = world.economy.near(d, "food")
	var best := -1
	var bs := -1.0
	var ci := CLASSES.find(h.cls)
	for i in mini(6, lst.size()):
		var b: Dictionary = world.economy.businesses[lst[i]]
		if b.status == "bankrupt":
			continue
		var w: float = float(Economy.FOOD_WEIGHT.get(b.type, 1.0)) / (1.0 + i * 0.35)
		w *= 1.0 - absf(int(b.tier) - (1 + (1 if ci >= 3 else 0))) * 0.25
		w *= rng.randf_range(0.5, 1.5)
		if w > bs:
			bs = w
			best = int(lst[i])
	return best


func _beg_spot(world) -> Dictionary:
	var eco = world.economy
	if eco == null or eco.businesses.is_empty():
		return {}
	var ids: Array = eco.businesses.keys()
	for t in 6:
		var b: Dictionary = eco.businesses[ids[rng.randi() % ids.size()]]
		if b.zone in ["downtown", "commercial", "oldtown", "poor"]:
			return {"pos": b.pos + Vector2(rng.randf_range(-6.0, 6.0), rng.randf_range(-1.0, 1.0))}
	return {"pos": eco.businesses[ids[0]].pos}


func _park_point(world, near: Vector2) -> Dictionary:
	if _parks.is_empty():
		return {}
	var best: Dictionary = {}
	var bd := INF
	for t in 5:
		var pk: Dictionary = _parks[rng.randi() % _parks.size()]
		var c: Vector2 = pk.rect.get_center()
		var dd := c.distance_squared_to(near)
		if dd < bd:
			bd = dd
			best = pk
	var paths: Array = best.get("paths", [])
	var pos: Vector2 = best.rect.get_center()
	if not paths.is_empty():
		var r: Rect2 = paths[rng.randi() % paths.size()]
		pos = r.position + Vector2(rng.randf() * r.size.x, rng.randf() * r.size.y)
	return {"pos": pos}


func _friend_home(world, p: Dictionary) -> Dictionary:
	var keys: Array = p.relationships.keys()
	for t in 3:
		var o: int = keys[rng.randi() % keys.size()]
		var q: Dictionary = persons.get(o, {})
		if q.is_empty() or not q.alive or int(q.household) == int(p.household) or q.homeless:
			continue
		var b := home_building(q)
		if b >= 0:
			return {"building": b, "pos": world.city.buildings[b].sidewalk_point}
	return {}


# ============================================================ Darstellungsschnittstelle

## Position (Vector2 x/z) einer Person; beim Pendeln entlang der Fussroute.
func position_of(world, id: int) -> Vector2:
	var p: Dictionary = persons.get(id, {})
	if p.is_empty():
		return Vector2.ZERO
	var loc: Dictionary = p.location
	if p.activity != "commute":
		return loc.to + _spread(id, p)
	var dur: float = float(loc.arrive) - float(loc.depart)
	var t := clampf((float(world.time_minutes) - float(loc.depart)) / maxf(dur, 0.01), 0.0, 1.0)
	var r := _route(world, loc)
	return _along(r, t)[0]


func _spread(id: int, p: Dictionary) -> Vector2:
	if p.activity in ["wander", "leisure"] and int(p.location.building) < 0:
		return Vector2(float((id * 7) % 9) - 4.0, float((id * 13) % 9) - 4.0) * 0.5
	return Vector2.ZERO


func _route(world, loc: Dictionary) -> Array:
	var a: Vector2 = loc.from
	var b: Vector2 = loc.to
	var key := Vector4i(roundi(a.x), roundi(a.y), roundi(b.x), roundi(b.y))
	if _routes.has(key):
		return _routes[key]
	var pts: PackedVector2Array = world.city.ped_route(a, b)
	var cum := PackedFloat32Array()
	cum.append(0.0)
	var total := 0.0
	for i in range(1, pts.size()):
		total += pts[i - 1].distance_to(pts[i])
		cum.append(total)
	if _routes.size() > 3000:
		_routes.clear()
	var r := [pts, cum, total]
	_routes[key] = r
	return r


## -> [pos, dir]
func _along(r: Array, t: float) -> Array:
	var pts: PackedVector2Array = r[0]
	var cum: PackedFloat32Array = r[1]
	var total: float = r[2]
	if pts.size() < 2 or total <= 0.0:
		return [pts[0] if pts.size() > 0 else Vector2.ZERO, Vector2(0, 1)]
	var d := t * total
	var lo := 0
	var hi := cum.size() - 1
	while lo < hi - 1:
		var mid := (lo + hi) / 2
		if cum[mid] <= d:
			lo = mid
		else:
			hi = mid
	var seg := cum[hi] - cum[lo]
	var f := (d - cum[lo]) / seg if seg > 0.0 else 0.0
	var dir := (pts[hi] - pts[lo]).normalized()
	return [pts[lo].lerp(pts[hi], f), dir if dir != Vector2.ZERO else Vector2(0, 1)]


## Route einer pendelnden Person: {points, progress} (leer, wenn nicht unterwegs).
func route_of(world, id: int) -> Dictionary:
	var p: Dictionary = persons.get(id, {})
	if p.is_empty() or p.activity != "commute":
		return {}
	var loc: Dictionary = p.location
	var r := _route(world, loc)
	var dur: float = float(loc.arrive) - float(loc.depart)
	return {"points": r[0], "progress": clampf((float(world.time_minutes) - float(loc.depart)) / maxf(dur, 0.01), 0.0, 1.0),
		"length": r[2], "mode": loc.mode, "arrive": float(loc.arrive)}


func is_indoors(world, p: Dictionary) -> bool:
	var b: int = int(p.location.building)
	if b < 0 or p.activity == "commute":
		return false
	if not p.activity in INDOOR_ACTS and p.activity != "leisure":
		return false
	var k: String = world.city.buildings[b].kind
	return not k in ["homeless_camp", "market_square"]


## Personen im Umkreis (raeumlicher Index). outdoor_only: nur sichtbare Personen draussen.
func persons_near(world, pos: Vector2, radius: float, outdoor_only := false) -> Array[int]:
	var out: Array[int] = []
	var r2 := radius * radius
	var c0 := Vector2i(floori((pos.x - radius) / CELL), floori((pos.y - radius) / CELL))
	var c1 := Vector2i(floori((pos.x + radius) / CELL), floori((pos.y + radius) / CELL))
	for cx in range(c0.x, c1.x + 1):
		for cz in range(c0.y, c1.y + 1):
			var cell = _grid.get(Vector2i(cx, cz))
			if cell == null:
				continue
			for id in cell:
				var p: Dictionary = persons[id]
				if (p.location.to + _spread(id, p)).distance_squared_to(pos) <= r2:
					if not outdoor_only or not is_indoors(world, p):
						out.append(id)
	var m := radius + 80.0
	for id in _moving:
		var p2: Dictionary = persons.get(id, {})
		if p2.is_empty():
			continue
		var a: Vector2 = p2.location.from
		var b: Vector2 = p2.location.to
		if pos.x < minf(a.x, b.x) - m or pos.x > maxf(a.x, b.x) + m or pos.y < minf(a.y, b.y) - m or pos.y > maxf(a.y, b.y) + m:
			continue
		if position_of(world, id).distance_squared_to(pos) <= r2:
			out.append(id)
	return out


## Zustand fuer die Darstellung: {pos, heading (Gierwinkel, Blickrichtung Vector2(sin, cos)),
## dir, activity, moving, building, indoors, mode, age, body}
func visible_state(world, id: int) -> Dictionary:
	var p: Dictionary = persons.get(id, {})
	if p.is_empty():
		return {}
	var moving: bool = p.activity == "commute"
	var pos: Vector2
	var dir := Vector2(0, 1)
	if moving:
		var loc: Dictionary = p.location
		var dur: float = float(loc.arrive) - float(loc.depart)
		var t := clampf((float(world.time_minutes) - float(loc.depart)) / maxf(dur, 0.01), 0.0, 1.0)
		var res := _along(_route(world, loc), t)
		pos = res[0]
		dir = res[1]
	else:
		pos = p.location.to + _spread(id, p)
		var b: int = int(p.location.building)
		if b >= 0:
			dir = -Vector2(world.city.buildings[b].front_dir)
	return {"pos": pos, "heading": atan2(dir.x, dir.y), "dir": dir, "activity": p.activity, "moving": moving,
		"building": int(p.location.building), "indoors": is_indoors(world, p), "mode": str(p.location.get("mode", "")),
		"age": world.age_years(float(p.birth)), "body": p.body}


# ============================================================ Lebenslauf (taeglich)

func on_day(world, day: int) -> void:
	_world_ref = world
	day_stats = {"day": day, "births": 0, "deaths": 0, "moves": 0, "immigrants": 0, "emigrants": 0}
	var L: Ledger = world.ledger
	var gov = world.government
	var lifespan: float = world.rules.f("lifespan")
	var dpy: float = world.rules.f("days_per_year")
	var aging: bool = world.rules.b("aging")
	var retire := _retire_age(world)
	var school_age: float = gov.policy("school_age") if gov else 6.0
	var dead := []
	for id in persons:
		var p: Dictionary = persons[id]
		if not aging:
			p.birth = float(p.birth) + 1440.0
		var a = world.age_years(float(p.birth))
		# Krankheit
		if p.illness != "":
			p.health = float(p.health) - rng.randf_range(2.0, 7.0)
			if rng.randf() < 0.3:
				p.illness = ""
			elif float(p.health) < 35.0 and not _hospitals.is_empty():
				var hb: int = _hospitals[id % _hospitals.size()]
				p.hospital_building = hb
				p.hospital_until = world.time_minutes + 1440.0
				if gov:
					L.transfer_partial(_account(p), gov.dept_accounts["health"], gov.policy("hospital_fee"), "Krankenhaus")
				p.health = float(p.health) + 30.0
				p.illness = ""
		elif world.rules.b("illness") and rng.randf() < 0.004 * (1.0 + maxf(0.0, a - 50.0) / 20.0):
			p.illness = ["Erkältung", "Grippe", "Magen-Darm-Infekt", "Rückenleiden"][rng.randi() % 4]
		else:
			p.health = minf(100.0 - maxf(0.0, a - 70.0) * 0.8, float(p.health) + 3.0)
		# Tod
		if aging or float(p.health) <= 5.0:
			var hz := LifeStages.hazard_per_year(a, lifespan) / dpy * (1.0 + maxf(0.0, 60.0 - float(p.health)) / 25.0)
			if not aging:
				hz = 0.0 if float(p.health) > 5.0 else 0.05
			if rng.randf() < hz:
				dead.append(id)
				continue
		# Uebergaenge
		if a >= school_age and a < 16.0 and int(p.school) < 0 and not p.homeless and world.education:
			p.school = world.education.school_for(world, home_pos(world, p))
			world.education.enroll(world, "npc", id, "school")
		elif a >= 16.0 and a < 17.0 and p.education == "none" and int(p.school) >= 0:
			_finish_school(world, p, rng.randf() < 0.45)
		elif a >= 18.0 and p.education in ["none", "school"] and int(p.school) >= 0 and \
				world.education and world.education.enrollment_of("npc", id).get("program", "") in ["school", ""]:
			_finish_school(world, p, false)
		if a >= retire and not p.retired and a >= 18.0:
			_retire(world, p)
	for id in dead:
		die(world, id, "Alter" if world.age_years(float(persons[id].birth)) > 60.0 else "Krankheit")
	_household_finances(world, day)
	_housing_moves(world, day)
	_partnerships(world, day)
	_births(world, day)
	_migration(world, day)
	history.append(stats(world, true))
	if history.size() > 90:
		history.pop_front()


func _finish_school(world, p: Dictionary, continue_school: bool) -> void:
	var edu = world.education
	if continue_school and world.age_years(float(p.birth)) < 17.0:
		p.education = "school"
		return
	edu.graduate_school(world, "npc", int(p.id))
	p.school = -1
	var amb: float = float(p.traits.ambitious)
	var h: Dictionary = households[int(p.household)]
	var r := rng.randf()
	var uni := amb * 0.5 + CLASSES.find(h.cls) * 0.1
	if world.age_years(float(p.birth)) >= 17.0 and r < uni:
		_start_training(world, p, "bachelor")
	elif r < uni + 0.7:
		_start_training(world, p, "vocational")
	else:
		p.unemployed_since = world.get_day()


func _retire(world, p: Dictionary) -> void:
	var gov = world.government
	var last := 14.0 * 8.0
	if world.jobs and not p.job.is_empty():
		var pos: Dictionary = world.jobs.positions.get(int(p.job.position), {})
		if not pos.is_empty():
			last = world.jobs.hourly_wage(world, pos) * float(pos.shift_hours) * float(pos.days.size()) / 7.0
		world.jobs.release(world, "npc", int(p.id), "retire")
	p.retired = true
	p.unemployed_since = -1
	if gov:
		p.pension = clampf(last * gov.policy("pension_rate"), gov.policy("min_pension"), gov.policy("max_pension"))


## Arbeitsuchende Erwachsene (fuer die Stellenvermittlung).
func job_seekers(world) -> Array:
	var out := []
	for id in persons:
		var p: Dictionary = persons[id]
		if not p.alive or not p.job.is_empty() or p.retired or p.homemaker or int(p.school) >= 0:
			continue
		if int(p.unemployed_since) < 0:
			continue
		if p.homeless and rng.randf() < 0.85:
			continue
		out.append(id)
	return out


## Beschaeftigte, die sich nach einer besser bezahlten Stelle umsehen.
func job_switchers(world) -> Array:
	var out := []
	var ids := persons.keys()
	for t in 25:
		var p: Dictionary = persons[ids[rng.randi() % ids.size()]]
		if p.alive and not p.job.is_empty() and float(p.traits.ambitious) > 0.6 and not world.jobs.positions[int(p.job.position)].reserved_owner:
			out.append(int(p.id))
	return out


func _household_finances(world, day: int) -> void:
	var L: Ledger = world.ledger
	var gov = world.government
	for hid in households:
		var h: Dictionary = households[hid]
		var acc: int = int(h.account)
		if gov:
			var kids := 0
			for m in h.members:
				var p: Dictionary = persons[m]
				if p.retired:
					gov.pay_pension(world, acc, float(p.pension))
				elif p.job.is_empty() and int(p.unemployed_since) >= 0 and world.age_years(float(p.birth)) >= 18.0:
					gov.pay_welfare(world, acc, gov.policy("unemployment_benefit") * world.rules.f("price_level"), "Arbeitslosengeld")
				elif world.age_years(float(p.birth)) < 18.0:
					kids += 1
			if kids > 0:
				gov.pay_welfare(world, acc, gov.policy("child_benefit") * kids, "Kindergeld")
			if h.homeless:
				gov.pay_welfare(world, acc, gov.policy("homeless_aid"), "Hilfe für Wohnungslose")
		var bal := L.balance(acc)
		var inc_day: float = household_income_week(world, hid) / 7.0
		h.income_week = inc_day * 7.0
		var reserve := inc_day * 21.0 + 300.0
		h.disc = minf(float(h.disc) + maxf(0.0, bal - reserve) * 0.03 + inc_day * 0.12, bal * 0.5)
		# Reisen und Fernkaeufe wohlhabender Haushalte (Geld verlaesst die Stadt)
		if bal > 80000.0:
			L.burn(acc, (bal - 80000.0) * 0.0025, "Reisen und Einkäufe außerhalb")
		# Gelegenheitsarbeit fuer Wohnungslose
		if h.homeless and rng.randf() < 0.15 and world.economy and not world.economy.construction_ids.is_empty():
			var cc: Dictionary = world.economy.companies[world.economy.construction_ids[0]]
			L.transfer(int(cc.account), acc, 55.0, "Tagelohn")


## Umzuege: Ueberforderung, Aufstieg, Auszug junger Erwachsener, Wohnungslose.
func _housing_moves(world, day: int) -> void:
	var housing = world.housing
	if housing == null:
		return
	var L: Ledger = world.ledger
	var ids := households.keys()
	_crisis_evictions(world, ids)
	var checks := mini(60, ids.size())
	for t in checks:
		var hid: int = ids[rng.randi() % ids.size()]
		var h: Dictionary = households.get(hid, {})
		if h.is_empty() or h.members.is_empty() or h.player_member:
			continue
		var inc: float = float(h.get("income_week", 0.0))
		var bal := L.balance(int(h.account))
		var home: int = int(h.home)
		if h.homeless:
			if _has_wage(h) and bal > 300.0 and rng.randf() < 0.2:
				var cand := _find_home(world, h, minf(inc * 0.4, bal / 3.0), 1)
				if cand >= 0 and housing.rent(world, "household", hid, int(h.account), cand).ok:
					_moved(world, h, cand)
					h.homeless = false
					for m in h.members:
						persons[m].homeless = false
			continue
		if home < 0:
			continue
		var pr: Dictionary = housing.properties[home]
		var renting: bool = not (pr.owner.kind == "household" and int(pr.owner.id) == hid)
		var rent: float = float(pr.rent_per_week)
		var need_rooms := clampi(int(ceil(h.members.size() * 0.7)), 1, 6)
		if renting and inc > 0.0 and rent > inc * 0.6 and bal < rent * 4.0:
			var c2 := _find_home(world, h, inc * 0.35, maxi(1, need_rooms - 1))
			if c2 >= 0 and float(housing.properties[c2].rent_per_week) < rent and housing.rent(world, "household", hid, int(h.account), c2).ok:
				_moved(world, h, c2)
		elif renting and (rent < inc * 0.2 and bal > rent * 15.0 or int(pr.rooms) < need_rooms - 1) and rng.randf() < 0.3:
			var c3 := _find_home(world, h, inc * 0.33, need_rooms)
			if c3 >= 0 and float(housing.properties[c3].quality) + int(housing.properties[c3].rooms) * 0.1 > float(pr.quality) + int(pr.rooms) * 0.1 \
					and housing.rent(world, "household", hid, int(h.account), c3).ok:
				_moved(world, h, c3)
		# Auszug erwachsener Kinder mit Arbeit
		if h.members.size() >= 3 and rng.randf() < 0.25:
			for m in h.members:
				var p: Dictionary = persons[m]
				var a = world.age_years(float(p.birth))
				if a >= 20.0 and not p.parents.is_empty() and not p.job.is_empty() and p.partner < 0:
					_move_out_young(world, p)
					break


## Lebenskrisen (Trennung, Schulden, Sucht): Haelt die Wohnungslosigkeit bei
## etwa 2,5 % der Bevoelkerung. Betroffen sind nur arme, arbeitslose
## Ein-Personen-Haushalte mit sehr wenig Geld.
func _crisis_evictions(world, ids: Array) -> void:
	var homeless := 0
	for hid in households:
		if households[hid].homeless:
			homeless += households[hid].members.size()
	var target := int(persons.size() * 0.025)
	if homeless >= target:
		return
	var n := mini(3, (target - homeless) / 4 + 1)
	for t in 80:
		if n <= 0:
			break
		var hid: int = ids[rng.randi() % ids.size()]
		var h: Dictionary = households[hid]
		if h.homeless or h.player_member or h.members.size() != 1 or not h.cls in ["poor", "lower"] or _has_wage(h):
			continue
		if world.ledger.balance(int(h.account)) > 1200.0 or int(h.home) < 0:
			continue
		var p: Dictionary = persons[h.members[0]]
		if world.age_years(float(p.birth)) < 18.0 or p.retired:
			continue
		world.housing.move_out(world, int(h.home))
		h.home = -1
		h.homeless = true
		h.cls = "poor"
		var bed: int = world.housing.free_shelter_bed()
		if bed >= 0:
			world.housing.assign("household", hid, bed)
			h.home = bed
		p.homeless = true
		p.home = h.home
		if not _camps.is_empty():
			p.camp = _camps[int(p.id) % _camps.size()]
		var nb := home_building(p)
		if nb >= 0:
			_patch_plan(p, -999, nb, world.city.buildings[nb].sidewalk_point)
		day_stats["crisis"] = int(day_stats.get("crisis", 0)) + 1
		n -= 1


func _has_wage(h: Dictionary) -> bool:
	for m in h.members:
		if not persons[m].job.is_empty():
			return true
	return false


func _find_home(world, h: Dictionary, max_rent: float, min_rooms: int) -> int:
	var housing = world.housing
	var vac: Array = housing.vacant_rentals()
	if vac.is_empty():
		return -1
	var best := -1
	var bs := -1e9
	for t in 40:
		var pid: int = vac[rng.randi() % vac.size()]
		var pr: Dictionary = housing.properties[pid]
		if float(pr.rent_per_week) > max_rent or int(pr.rooms) < min_rooms:
			continue
		if pr.kind == "room" and h.members.size() > 1:
			continue
		var sc: float = float(pr.quality) + float(pr.wealth) * 0.5 - float(pr.rent_per_week) / maxf(1.0, max_rent) * 0.4
		if sc > bs:
			bs = sc
			best = pid
	return best


func _moved(world, h: Dictionary, pid: int) -> void:
	var old_b := -1
	if h.members.size() > 0:
		old_b = home_building(persons[h.members[0]])
	h.home = pid
	var pr: Dictionary = world.housing.properties[pid]
	h.district = int(pr.district)
	day_stats["moves"] = int(day_stats.get("moves", 0)) + 1
	var nb: int = int(pr.building)
	var npos: Vector2 = world.city.buildings[nb].sidewalk_point
	for m in h.members:
		var p: Dictionary = persons[m]
		p.home = pid
		_patch_plan(p, old_b, nb, npos)


func _patch_plan(p: Dictionary, old_b: int, nb: int, npos: Vector2) -> void:
	for i in range(int(p.plan_i) + 1, p.plan.size()):
		var e: Array = p.plan[i]
		if int(e[2]) == old_b or e[1] in ["sleep", "home"]:
			e[2] = nb
			e[3] = npos


func _move_out_young(world, p: Dictionary) -> void:
	var old: Dictionary = households[int(p.household)]
	var hid := _new_household(world, old.cls)
	var h: Dictionary = households[hid]
	var inc := 0.0
	old.members.erase(int(p.id))
	h.members.append(int(p.id))
	p.household = hid
	h.shopper = int(p.id)
	if int(old.shopper) == int(p.id):
		old.shopper = old.members[0]
	inc = household_income_week(world, hid)
	h.income_week = inc
	var L: Ledger = world.ledger
	L.transfer_partial(int(old.account), int(h.account), minf(1500.0, L.balance(int(old.account)) * 0.2), "Starthilfe Eltern")
	var c := _find_home(world, h, inc * 0.4, 1)
	if c >= 0 and world.housing.rent(world, "household", hid, int(h.account), c).ok:
		var ob := home_building(persons[old.members[0]]) if not old.members.is_empty() else -1
		h.home = c
		h.district = int(world.housing.properties[c].district)
		p.home = c
		var nb: int = int(world.housing.properties[c].building)
		_patch_plan(p, ob, nb, world.city.buildings[nb].sidewalk_point)
	else:
		# kein Wohnraum: bleibt bei den Eltern
		h.members.erase(int(p.id))
		old.members.append(int(p.id))
		p.household = int(old.id)
		world.ledger.transfer_partial(int(h.account), int(old.account), world.ledger.balance(int(h.account)), "Rückzahlung")
		world.ledger.close_account(int(h.account))
		households.erase(hid)


## Haushalt verliert die Wohnung (Mietschulden/Zwangsversteigerung).
func on_evicted(world, hid: int) -> void:
	var h: Dictionary = households.get(hid, {})
	if h.is_empty():
		return
	var inc: float = household_income_week(world, hid)
	h.home = -1
	var c := _find_home(world, h, inc * 0.3, 1)
	if c >= 0 and world.housing.rent(world, "household", hid, int(h.account), c).ok:
		_moved(world, h, c)
		return
	h.homeless = true
	var bed: int = world.housing.free_shelter_bed()
	if bed >= 0:
		world.housing.assign("household", hid, bed)
		h.home = bed
	for m in h.members:
		persons[m].homeless = true
		persons[m].home = h.home
		if not _camps.is_empty():
			persons[m].camp = _camps[m % _camps.size()]
	day_stats["evictions"] = int(day_stats.get("evictions", 0)) + 1


func _partnerships(world, day: int) -> void:
	var singles := []
	var ids := persons.keys()
	for t in 400:
		var p: Dictionary = persons[ids[rng.randi() % ids.size()]]
		var a = world.age_years(float(p.birth))
		if p.alive and int(p.partner) == -1 and a >= 20.0 and a < 45.0 and not p.homeless and households[int(p.household)].members.size() == 1:
			singles.append(p)
	var fem := []
	var mal := []
	for s in singles:
		if fem.has(s) or mal.has(s):
			continue
		if s.body == "female":
			fem.append(s)
		else:
			mal.append(s)
	for i in mini(fem.size(), mal.size()):
		var a: Dictionary = fem[i]
		var b: Dictionary = mal[i]
		if absf(world.age_years(float(a.birth)) - world.age_years(float(b.birth))) > 10.0 or rng.randf() > 0.4:
			continue
		var ha: Dictionary = households[int(a.household)]
		var hb: Dictionary = households[int(b.household)]
		# B zieht zu A
		a.partner = int(b.id)
		b.partner = int(a.id)
		a.relationships[int(b.id)] = 85
		b.relationships[int(a.id)] = 85
		world.ledger.transfer_partial(int(hb.account), int(ha.account), world.ledger.balance(int(hb.account)), "Zusammenzug")
		if int(hb.home) >= 0:
			world.housing.move_out(world, int(hb.home))
		hb.members.erase(int(b.id))
		ha.members.append(int(b.id))
		b.household = int(ha.id)
		b.home = ha.home
		_dissolve_if_empty(world, int(hb.id), int(ha.id))
		var nb := home_building(a)
		if nb >= 0:
			_patch_plan(b, -999, nb, world.city.buildings[nb].sidewalk_point)


func _births(world, day: int) -> void:
	var eligible := []
	var alive := persons.size()
	for hid in households:
		var h: Dictionary = households[hid]
		if h.homeless or h.members.size() > 5:
			continue
		for m in h.members:
			var p: Dictionary = persons[m]
			if p.body == "female" and p.partner >= 0:
				var a = world.age_years(float(p.birth))
				if a >= 20.0 and a < 43.0:
					eligible.append(m)
					break
	if eligible.is_empty():
		return
	var lifespan: float = world.rules.f("lifespan")
	var dpy: float = world.rules.f("days_per_year")
	var expected := float(target_population) / (lifespan * dpy)
	var ratio := float(target_population) / maxf(1.0, float(alive))
	var prob := clampf(expected * pow(ratio, 3.0) / float(eligible.size()), 0.0, 0.25)
	for m in eligible:
		if rng.randf() < prob:
			var mother: Dictionary = persons[m]
			var father: int = int(mother.partner)
			var parents := [m]
			if persons.has(father):
				parents.append(father)
			var c := _child(world, int(mother.household), 0.01, mother.last, parents)
			_init_schedule(world, persons[c])
			day_stats.births = int(day_stats.births) + 1


func _migration(world, day: int) -> void:
	var alive := persons.size()
	if alive < int(target_population * 0.97):
		var n := mini(6, (int(target_population * 0.97) - alive) / 8 + 1)
		for i in n:
			_immigrate(world)
	# Fachkraefte-Zuzug fuer schwer besetzbare Stellen (Master/Promotion)
	if world.jobs:
		var hard := []
		for pid in world.jobs.positions:
			var ps: Dictionary = world.jobs.positions[pid]
			if ps.active and ps.holder_kind == "" and not ps.reserved_owner \
					and Occupations.edu_rank(Occupations.OCCUPATIONS[ps.occupation].education) >= 3:
				hard.append(pid)
		for k in mini(2, hard.size() / 6):
			_skilled_immigrant(world, hard[rng.randi() % hard.size()])
	# Wegzug langzeitarbeitsloser, mittelloser Haushalte
	var ids := households.keys()
	for t in 20:
		var hid: int = ids[rng.randi() % ids.size()]
		var h: Dictionary = households.get(hid, {})
		if h.is_empty() or h.player_member:
			continue
		var leave := false
		if world.ledger.balance(int(h.account)) < 100.0 and float(h.get("income_week", 0.0)) < 300.0 and not h.homeless:
			leave = rng.randf() < 0.1
		if alive > int(target_population * 1.05) and rng.randf() < 0.3 and h.members.size() <= 2:
			leave = true
		if leave:
			_emigrate(world, hid)


func _immigrate(world) -> void:
	var hid := _new_household(world, "middle")
	var h: Dictionary = households[hid]
	var ln := _last_name()
	var a := rng.randf_range(20.0, 38.0)
	var p1 := _new_person(world, hid, a, _rand_body(), ln)
	persons[p1].education = _edu_for_age(a)
	persons[p1].unemployed_since = world.get_day()
	if rng.randf() < 0.4:
		var ids := _couple_extra(world, hid, p1, ln)
		if rng.randf() < 0.5:
			_child(world, hid, rng.randf_range(0.0, 8.0), ln, ids)
	h.shopper = p1
	world.ledger.mint(int(h.account), rng.randf_range(1500.0, 6000.0), "Zuzug: mitgebrachtes Vermögen")
	var c := _find_home(world, h, 420.0, 1)
	if h.members.size() == 1 and rng.randf() < 0.3:
		c = -1
	if c < 0:
		# Zuzug ohne Wohnung: Notunterkunft oder Zeltlager
		h.homeless = true
		h.cls = "poor"
		var bed: int = world.housing.free_shelter_bed()
		if bed >= 0:
			world.housing.assign("household", hid, bed)
			h.home = bed
		for m in h.members:
			persons[m].homeless = true
			persons[m].home = h.home
			if not _camps.is_empty():
				persons[m].camp = _camps[m % _camps.size()]
			_init_schedule(world, persons[m])
		day_stats.immigrants = int(day_stats.immigrants) + h.members.size()
		return
	world.housing.rent(world, "household", hid, int(h.account), c)
	h.home = c
	h.district = int(world.housing.properties[c].district)
	h.pantry = 6.0
	h.cls = "lower"
	h.wealth = 0.3
	for m in h.members:
		persons[m].home = c
		if world.age_years(float(persons[m].birth)) >= 6.0 and world.age_years(float(persons[m].birth)) < 16.0 and world.education:
			persons[m].school = world.education.school_for(world, home_pos(world, persons[m]))
			world.education.enroll(world, "npc", m, "school")
		_init_schedule(world, persons[m])
	day_stats.immigrants = int(day_stats.immigrants) + h.members.size()


func _skilled_immigrant(world, pos_id: int) -> void:
	var ps: Dictionary = world.jobs.positions[pos_id]
	var occ: Dictionary = Occupations.OCCUPATIONS[ps.occupation]
	var hid := _new_household(world, "upper")
	var h: Dictionary = households[hid]
	var a := maxf(float(occ.min_age) + 2.0, rng.randf_range(27.0, 45.0))
	var pid := _new_person(world, hid, a, _rand_body(), _last_name(), 0.7)
	var p: Dictionary = persons[pid]
	p.education = occ.education
	p.criminal_record = 0
	for s in occ.skills:
		p.skills[s] = float(occ.skills[s]) + rng.randf_range(2.0, 20.0)
	h.shopper = pid
	world.ledger.mint(int(h.account), rng.randf_range(4000.0, 15000.0), "Zuzug: mitgebrachtes Vermögen")
	var c := _find_home(world, h, 900.0, 1)
	if c < 0:
		_remove_household(world, hid, true)
		return
	world.housing.rent(world, "household", hid, int(h.account), c)
	h.home = c
	h.district = int(world.housing.properties[c].district)
	h.pantry = 6.0
	h.wealth = 0.7
	p.home = c
	world.jobs.hire(world, "npc", pid, pos_id)
	_init_schedule(world, p)
	day_stats.immigrants = int(day_stats.immigrants) + 1


func _couple_extra(world, hid: int, p1: int, ln: String) -> Array:
	var a = world.age_years(float(persons[p1].birth))
	var b := _new_person(world, hid, clampf(a + rng.randf_range(-3.0, 4.0), 19.0, 60.0), "male" if persons[p1].body == "female" else "female", ln)
	persons[b].education = _edu_for_age(a)
	persons[b].unemployed_since = world.get_day()
	persons[p1].partner = b
	persons[b].partner = p1
	persons[p1].relationships[b] = 85
	persons[b].relationships[p1] = 85
	return [p1, b]


func _emigrate(world, hid: int) -> void:
	var h: Dictionary = households[hid]
	day_stats.emigrants = int(day_stats.emigrants) + h.members.size()
	_remove_household(world, hid, true)


func _remove_household(world, hid: int, burn_money: bool) -> void:
	var h: Dictionary = households[hid]
	for m in h.members.duplicate():
		_remove_person(world, m)
	if int(h.home) >= 0:
		var pr: Dictionary = world.housing.properties.get(int(h.home), {})
		if not pr.is_empty() and pr.tenant.kind == "household" and int(pr.tenant.id) == hid:
			world.housing.move_out(world, int(h.home))
	_release_property(world, hid, -1)
	world.ledger.close_account(int(h.account))
	households.erase(hid)


func _release_property(world, hid: int, heir_hid: int) -> void:
	# Eigentum (Immobilien, Geschaefte, Firmenanteile, Kredite) an Erben oder Markt
	var housing = world.housing
	var eco = world.economy
	if heir_hid >= 0:
		housing.transfer_ownership("household", hid, "household", heir_hid)
		if eco:
			for bid in eco.businesses_owned_by("household", hid):
				eco.businesses[bid].owner = {"kind": "household", "id": heir_hid}
			for cid in eco.companies:
				var c: Dictionary = eco.companies[cid]
				if c.owner.kind == "household" and int(c.owner.id) == hid:
					c.owner = {"kind": "household", "id": heir_hid}
			eco.bank.transfer_debts("household", hid, "household", heir_hid, int(households[heir_hid].account))
		return
	for pid in housing.owned_by("household", hid):
		var pr: Dictionary = housing.properties[pid]
		pr.owner = {"kind": "city", "id": 0}
		pr.for_sale = false
		pr.for_rent = true
		housing._update_flags(pr)
	if eco:
		for bid in eco.businesses_owned_by("household", hid):
			eco.businesses[bid].owner = {"kind": "city", "id": 0}
			eco.businesses[bid].for_sale = true
		for cid in eco.companies:
			var c2: Dictionary = eco.companies[cid]
			if c2.owner.kind == "household" and int(c2.owner.id) == hid:
				c2.owner = {"kind": "external", "id": -1}
		eco.bank.transfer_debts("household", hid, "", -1, -1)
		if eco.bank.owner.kind == "household" and int(eco.bank.owner.id) == hid:
			eco.bank.owner = {"kind": "external", "id": -1}


func _remove_person(world, id: int) -> void:
	var p: Dictionary = persons.get(id, {})
	if p.is_empty():
		return
	p.alive = false
	if world.jobs:
		world.jobs.release(world, "npc", id, "death")
	if world.education:
		world.education.forget("npc", id)
	_grid_remove(id)
	_moving.erase(id)
	var h: Dictionary = households.get(int(p.household), {})
	if not h.is_empty():
		h.members.erase(id)
		if int(h.shopper) == id and not h.members.is_empty():
			h.shopper = h.members[0]
	if persons.has(int(p.partner)):
		persons[int(p.partner)].partner = -1
	persons.erase(id)


## Tod einer Person. Erbe: Haushalt bleibt bestehen; leerer Haushalt vererbt an
## Kinder (eigener Haushalt) oder an die Stadtkasse.
func die(world, id: int, cause: String) -> void:
	var p: Dictionary = persons.get(id, {})
	if p.is_empty():
		return
	var hid: int = int(p.household)
	var children: Array = p.children.duplicate()
	if world.player and world.player.has_method("on_relative_died"):
		world.player.on_relative_died(world, id, cause)
	_remove_person(world, id)
	day_stats.deaths = int(day_stats.get("deaths", 0)) + 1
	var heir := -1
	for c in children:
		if persons.has(c) and int(persons[c].household) != hid:
			heir = int(persons[c].household)
			break
	_dissolve_if_empty(world, hid, heir)


func _dissolve_if_empty(world, hid: int, heir_hid: int) -> void:
	var h: Dictionary = households.get(hid, {})
	if h.is_empty():
		return
	var adults := adults_of(world, hid)
	if h.members.is_empty() or (adults.is_empty() and not h.player_member):
		var L: Ledger = world.ledger
		# Minderjaehrige kommen in eine Pflegefamilie
		var foster := heir_hid
		if not h.members.is_empty():
			if foster < 0 or not households.has(foster):
				foster = _find_foster(world, hid)
			for m in h.members.duplicate():
				h.members.erase(m)
				households[foster].members.append(m)
				persons[m].household = foster
				persons[m].home = households[foster].home
				var nb := home_building(persons[m])
				if nb >= 0:
					_patch_plan(persons[m], -999, nb, world.city.buildings[nb].sidewalk_point)
			heir_hid = foster
		if int(h.home) >= 0:
			var pr: Dictionary = world.housing.properties.get(int(h.home), {})
			if not pr.is_empty() and pr.tenant.kind == "household" and int(pr.tenant.id) == hid:
				world.housing.move_out(world, int(h.home))
		if heir_hid >= 0 and households.has(heir_hid):
			_release_property(world, hid, heir_hid)
			L.close_account(int(h.account), int(households[heir_hid].account))
		else:
			# Ohne Erben: 25 % Erbschaftsteuer, Rest an Verwandte in der Stadt
			_release_property(world, hid, -1)
			var bal: float = L.balance(int(h.account))
			if bal > 0.0 and world.government:
				world.government.collect(world, int(h.account), bal * 0.25, "inheritance_tax")
			var rel := _find_foster(world, hid)
			L.close_account(int(h.account), int(households[rel].account) if households.has(rel) and rel != hid else (world.government.account if world.government else -1))
		households.erase(hid)


func _find_foster(world, exclude: int) -> int:
	var ids := households.keys()
	for t in 50:
		var hid: int = ids[rng.randi() % ids.size()]
		var h: Dictionary = households[hid]
		if hid != exclude and not h.homeless and not h.player_member and not adults_of(world, hid).is_empty() and int(h.home) >= 0:
			return hid
	for hid in ids:
		if hid != exclude and not adults_of(world, hid).is_empty():
			return hid
	return exclude


## Wohlhabender Haushalt mit mindestens `capital` Guthaben (oder -1).
func find_investor(world, capital: float) -> int:
	var ids := households.keys()
	var L: Ledger = world.ledger
	for t in 80:
		var hid: int = ids[rng.randi() % ids.size()]
		var h: Dictionary = households[hid]
		if not h.player_member and L.balance(int(h.account)) >= capital and not h.members.is_empty():
			return hid
	return -1


## Ein Erwachsener des Haushalts uebernimmt die Inhaberstelle.
func assign_owner_job(world, hid: int, position_id: int) -> void:
	var best := -1
	for m in households.get(hid, {}).get("members", []):
		var p: Dictionary = persons[m]
		var a = world.age_years(float(p.birth))
		if a >= 18.0 and not p.retired:
			best = m
			if p.job.is_empty():
				break
	if best >= 0:
		world.jobs.release(world, "npc", best, "switch")
		world.jobs.hire(world, "npc", best, position_id)


# ============================================================ Spieler als Kind

## Haushalt mit passenden Eltern fuer einen jungen Spieler.
func find_parent_household(world, player_age: float, prefer_poor := false) -> int:
	var best := -1
	var bs := -1e9
	for hid in households:
		var h: Dictionary = households[hid]
		if h.homeless or int(h.home) < 0 or h.player_member:
			continue
		var adults := adults_of(world, hid)
		if adults.is_empty():
			continue
		var ok := true
		for a in adults:
			var ad = world.age_years(float(persons[a].birth))
			if ad < player_age + 18.0 or ad > player_age + 48.0:
				ok = false
		if not ok:
			continue
		var sc := float(adults.size()) + rng.randf() * 2.0 + float(h.members.size() > adults.size())
		if prefer_poor:
			sc -= CLASSES.find(h.cls)
		if sc > bs:
			bs = sc
			best = hid
	return best


# ============================================================ Kennzahlen

func stats(world, compact := false) -> Dictionary:
	var employed := 0
	var unemployed := 0
	var homeless := 0
	var students := 0
	var retired := 0
	var children := 0
	for id in persons:
		var p: Dictionary = persons[id]
		var a = world.age_years(float(p.birth))
		if p.homeless:
			homeless += 1
		if a < 18.0:
			children += 1
		if p.retired:
			retired += 1
		elif not p.job.is_empty():
			employed += 1
		elif int(p.school) >= 0 and a >= 16.0:
			students += 1
		elif a >= 18.0 and int(p.unemployed_since) >= 0 and not p.homemaker:
			unemployed += 1
	var out := {"day": world.get_day(), "population": persons.size(), "households": households.size(), "employed": employed,
		"unemployed": unemployed, "unemployment_rate": float(unemployed) / maxf(1.0, float(employed + unemployed)),
		"homeless": homeless, "students": students, "retired": retired, "children": children}
	out.merge(day_stats, false)
	var arrears := 0
	if world.housing:
		for pid in world.housing.properties:
			var pr: Dictionary = world.housing.properties[pid]
			if pr.tenant.kind == "household" and float(pr.arrears) > 0.0:
				arrears += 1
	var hungry := 0
	for id in persons:
		if float(persons[id].needs.hunger) < 20.0:
			hungry += 1
	out["households_in_arrears"] = arrears
	out["hungry"] = hungry
	if not compact:
		var by_zone := {}
		var L: Ledger = world.ledger
		for hid in households:
			var h: Dictionary = households[hid]
			var z := "homeless" if h.homeless else (str(world.housing.properties[int(h.home)].zone) if int(h.home) >= 0 and world.housing else "?")
			if not by_zone.has(z):
				by_zone[z] = []
			by_zone[z].append(L.balance(int(h.account)))
		var med := {}
		for z in by_zone:
			var arr: Array = by_zone[z]
			arr.sort()
			med[z] = snappedf(float(arr[arr.size() / 2]), 1.0)
		out["median_balance_by_zone"] = med
	return out


# ============================================================ Speichern

func to_save() -> Dictionary:
	return {"persons": persons.duplicate(true), "households": households.duplicate(true), "next_person_id": next_person_id,
		"next_household_id": next_household_id, "rng": rng.state, "target": target_population, "last_slot": _last_slot,
		"history": history.duplicate(true), "day_stats": day_stats.duplicate()}


func from_save(world, d: Dictionary) -> void:
	_world_ref = world
	d = d.duplicate(true)
	rng.seed = hash("population:%d" % world.seed)
	persons = {}
	for k in d.get("persons", {}):
		persons[int(k)] = d.persons[k]
	households = {}
	for k in d.get("households", {}):
		households[int(k)] = d.households[k]
	next_person_id = int(d.get("next_person_id", 1))
	next_household_id = int(d.get("next_household_id", 1))
	if d.has("rng"):
		rng.state = int(d.rng)
	target_population = int(d.get("target", world.rules.get_rule("population")))
	history = d.get("history", [])
	day_stats = d.get("day_stats", {})
	_last_slot = int(d.get("last_slot", int(world.time_minutes / 5.0)))
	_index_city(world)
	_queue.clear()
	_grid.clear()
	_cell_of.clear()
	_moving.clear()
	_routes.clear()
	for id in persons:
		var p: Dictionary = persons[id]
		if p.activity == "commute":
			_moving[id] = true
		else:
			_grid_add(id, p.location.to)
		var t := float(p.next_t)
		var slot := maxi(int(ceil(t / 5.0 - 0.0001)), _last_slot + 1)
		if not _queue.has(slot):
			_queue[slot] = []
		_queue[slot].append(id)
