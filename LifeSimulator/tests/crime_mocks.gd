extends RefCounted
## Testhilfen fuer Kriminalitaet und Politik: kleine Ersatzobjekte, die nur
## die Vertragsmethoden aus ARCHITECTURE.md erfuellen (Government, Economy,
## Population, PlayerLife), plus echte Stadt (CityGenerator) und echter Ledger.

const RES_KINDS := ["house", "townhouse", "villa", "estate", "apartment", "tenement", "highrise_res", "mixed"]

static var _city_cache: Dictionary = {}


class MockGovernment extends RefCounted:
	var rates := {"income_tax": 0.2, "sales_tax": 0.1, "property_tax": 0.01, "business_tax": 0.15}
	var budget := {"police": 0.15, "education": 0.2, "health": 0.15, "transit": 0.1, "infrastructure": 0.1, "welfare": 0.2, "housing": 0.1}
	var policies := {}
	var account := -1

	func setup(world) -> void:
		account = world.ledger.open_account("government", 0, "Staatskasse", 500000.0)

	func modifier(name: String) -> float:
		match name:
			"police_effectiveness":
				return 0.4 + 0.6 * float(budget.police) / 0.15
			"education_quality":
				return 0.5 + 0.5 * float(budget.education) / 0.2
			"health_quality":
				return 0.5 + 0.5 * float(budget.health) / 0.15
			"bus_fare":
				return float(policies.get("bus_fare", 2.5))
		return 1.0

	func set_rate(key: String, value: float) -> void:
		rates[key] = value

	func set_budget(key: String, value: float) -> void:
		budget[key] = value


class MockEconomy extends RefCounted:
	var businesses := {}
	var _by_building := {}

	func setup(world) -> void:
		var id := 1
		for b in world.city.buildings:
			if str(b.business_type) == "":
				continue
			var acc: int = world.ledger.open_account("business", id, str(b.name), 3000.0 + float(b.get("district", 0) % 3) * 2000.0)
			businesses[id] = {"id": id, "building": int(b.id), "type": b.business_type, "name": str(b.name), "account": acc,
				"inventory": {"bread": {"stock": 30, "price": 3.0, "max": 50}, "snack": {"stock": 20, "price": 2.5, "max": 40}},
				"open": 7, "close": 22, "employees": [], "owner": {"kind": "npc", "id": -1}, "status": "open"}
			_by_building[int(b.id)] = id
			id += 1

	func get_business(id: int) -> Dictionary:
		return businesses.get(id, {})

	func business_for_building(bid: int) -> int:
		return int(_by_building.get(bid, -1))


class MockPopulation extends RefCounted:
	var persons := {}
	var households := {}
	var _grid := {}
	var _pos := {}
	const CELL := 50.0

	func setup(world) -> void:
		var rng := RandomNumberGenerator.new()
		rng.seed = hash("mockpop:%d" % world.seed)
		var pid := 1
		var hid := 1
		var limit := int(world.rules.f("population"))
		for b in world.city.buildings:
			if not (str(b.kind) in RES_KINDS):
				continue
			if pid > limit:
				break
			var d: Dictionary = world.city.districts[int(b.district)]
			var crime_lvl: float = float(d.crime)
			var wealth: float = float(d.wealth)
			var acc: int = world.ledger.open_account("household", hid, "Haushalt %d" % hid, 400.0 + wealth * 6000.0)
			var members := []
			var n := 1 + rng.randi_range(0, 2) + (2 if str(b.kind) in ["apartment", "tenement", "highrise_res"] else 0)
			for i in n:
				var age := rng.randf_range(8.0, 75.0)
				var crim := clampf(rng.randf() * (0.35 + crime_lvl), 0.0, 1.0)
				var pos: Vector2 = b.door
				persons[pid] = {"id": pid, "first": "Person", "last": "Nr%d" % pid, "body": "male" if pid % 2 == 0 else "female",
					"birth": world.birth_for_age(age), "household": hid, "home": -1, "home_building": int(b.id),
					"job": {"occupation": "worker"} if rng.randf() < 0.85 else {}, "school": -1,
					"traits": {"criminal": crim, "lawful": 1.0 - crim, "social": rng.randf(), "ambitious": rng.randf()},
					"needs": {"hunger": 80.0}, "activity": "home",
					"location": {"building": int(b.id), "from": pos, "to": pos, "depart": 0.0, "arrive": 0.0},
					"relationships": {}, "alive": true, "homeless": false, "criminal_record": [], "gang": -1}
				_pos[pid] = pos
				var k := Vector2i(int(floor(pos.x / CELL)), int(floor(pos.y / CELL)))
				if not _grid.has(k):
					_grid[k] = []
				_grid[k].append(pid)
				members.append(pid)
				pid += 1
			households[hid] = {"id": hid, "members": members, "account": acc, "home": -1, "wealth": wealth}
			hid += 1

	func get_person(id: int) -> Dictionary:
		return persons.get(id, {})

	func position_of(_world, id: int) -> Vector2:
		return _pos.get(id, Vector2.ZERO)

	func persons_near(_world, pos: Vector2, radius: float) -> Array:
		var out := []
		var r := int(ceil(radius / CELL))
		var k := Vector2i(int(floor(pos.x / CELL)), int(floor(pos.y / CELL)))
		for dx in range(-r, r + 1):
			for dz in range(-r, r + 1):
				for id in _grid.get(k + Vector2i(dx, dz), []):
					if (_pos[id] as Vector2).distance_to(pos) <= radius:
						out.append(id)
		out.sort()
		return out


class MockPlayer extends RefCounted:
	var name := "Alex Berger"
	var body := "female"
	var birth := 0.0
	var needs := {"hunger": 80.0, "thirst": 80.0, "energy": 80.0, "hygiene": 80.0, "social": 80.0, "fun": 80.0, "health": 100.0}
	var skills := {"stealth": 40.0, "strength": 40.0, "fitness": 40.0, "charisma": 40.0, "driving": 40.0, "logic": 40.0}
	var education := "school"
	var account := -1
	var inventory := {}
	var relationships := {}
	var criminal_record := []
	var wanted := 0
	var gang := -1
	var police_rank := -1
	var political_office := ""
	var alive := true
	var job := {}
	var start_age := 25.0
	var start_money := 3000.0

	func setup(world) -> void:
		account = world.ledger.open_account("player", 0, name, start_money)
		birth = world.birth_for_age(start_age)

	func age(world) -> float:
		return world.age_years(birth)


## Baut eine Testwelt. opts: rules (Sandbox), age, money, politics (bool),
## skills (Dictionary), population (int).
static func make_world(seed: int, opts := {}) -> WorldState:
	var w := WorldState.new()
	w.silent = true
	w.seed = seed
	var rules: Dictionary = {"population": int(opts.get("population", 900))}
	rules.merge(opts.get("rules", {}), true)
	w.rules = GameRules.make("sandbox", rules)
	if not _city_cache.has(seed):
		_city_cache[seed] = CityGenerator.generate(seed, {"size": "small"})
	w.city = _city_cache[seed]
	w.ledger = Ledger.new()
	var gov := MockGovernment.new()
	var eco := MockEconomy.new()
	var pop := MockPopulation.new()
	var crime := CrimeSystem.new()
	var pl := MockPlayer.new()
	pl.start_age = float(opts.get("age", 25.0))
	pl.start_money = float(opts.get("money", 3000.0))
	var sk: Dictionary = opts.get("skills", {})
	for k in sk:
		pl.skills[k] = sk[k]
	w.government = gov
	w.economy = eco
	w.population = pop
	w.crime = crime
	w.player = pl
	w.systems = [["government", gov], ["economy", eco], ["population", pop], ["crime", crime]]
	if bool(opts.get("politics", true)):
		var pol := Politics.new()
		w.politics = pol
		w.systems.append(["politics", pol])
	w.systems.append(["player", pl])
	for s in w.systems:
		s[1].setup(w)
	w._last_hour = w.get_hour()
	w._last_day = w.get_day()
	return w


static func save_roundtrip(d: Dictionary) -> Dictionary:
	return bytes_to_var(var_to_bytes(d))
