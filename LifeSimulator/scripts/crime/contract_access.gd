class_name ContractAccess
extends RefCounted
## Defensiver Zugriff auf die Vertraege anderer Systeme (Population,
## PlayerLife, Government, Economy, JobMarket, HousingMarket).
## Kriminalitaet und Politik werden parallel zu diesen Systemen entwickelt;
## alle Zugriffe pruefen daher, ob Objekt, Methode oder Feld existieren, und
## liefern sinnvolle Standardwerte. So laufen CrimeSystem und Politics auch,
## wenn ein Teilsystem fehlt oder anders aufgebaut ist.

const PLAYER_ID := 0
const EDU_ORDER := ["none", "school", "vocational", "bachelor", "master", "doctor"]
const EDU_LABELS := {"none": "kein Abschluss", "school": "Schulabschluss", "vocational": "Berufsausbildung",
	"bachelor": "Bachelor", "master": "Master", "doctor": "Promotion"}


# ============================================================ Allgemein

## Liest ein Feld aus einem Objekt oder Dictionary.
static func field(obj, key: String, default = null):
	if obj == null:
		return default
	if typeof(obj) == TYPE_DICTIONARY:
		return obj.get(key, default)
	if typeof(obj) == TYPE_OBJECT and is_instance_valid(obj) and key in obj:
		var v = obj.get(key)
		return default if v == null else v
	return default


## Setzt ein Feld (Objekt oder Dictionary), falls vorhanden bzw. moeglich.
static func set_field(obj, key: String, value) -> bool:
	if obj == null:
		return false
	if typeof(obj) == TYPE_DICTIONARY:
		obj[key] = value
		return true
	if typeof(obj) == TYPE_OBJECT and is_instance_valid(obj) and key in obj:
		obj.set(key, value)
		return true
	return false


static func has_m(obj, method: String) -> bool:
	return obj != null and typeof(obj) == TYPE_OBJECT and is_instance_valid(obj) and obj.has_method(method)


static func to_v2(v, default := Vector2.ZERO) -> Vector2:
	match typeof(v):
		TYPE_VECTOR2:
			return v
		TYPE_VECTOR3:
			return Vector2(v.x, v.z)
		TYPE_ARRAY:
			if v.size() >= 2:
				return Vector2(float(v[0]), float(v[1]))
		TYPE_DICTIONARY:
			if v.has("x"):
				return Vector2(float(v.x), float(v.get("y", v.get("z", 0.0))))
	return default


## Wandelt Vector2/Vector3 rekursiv in einfache Daten um (Speicherformat).
static func pack(v):
	match typeof(v):
		TYPE_VECTOR2:
			return {"__v2": [v.x, v.y]}
		TYPE_VECTOR3:
			return {"__v3": [v.x, v.y, v.z]}
		TYPE_DICTIONARY:
			var out := {}
			for k in v:
				out[k] = pack(v[k])
			return out
		TYPE_ARRAY:
			var arr := []
			for e in v:
				arr.append(pack(e))
			return arr
	return v


static func unpack(v):
	match typeof(v):
		TYPE_DICTIONARY:
			if v.has("__v2"):
				return Vector2(float(v.__v2[0]), float(v.__v2[1]))
			if v.has("__v3"):
				return Vector3(float(v.__v3[0]), float(v.__v3[1]), float(v.__v3[2]))
			var out := {}
			for k in v:
				out[k] = unpack(v[k])
			return out
		TYPE_ARRAY:
			var arr := []
			for e in v:
				arr.append(unpack(e))
			return arr
	return v


## Dictionary mit String-Schluesseln (z.B. nach JSON) -> int-Schluessel.
static func int_keys(d: Dictionary) -> Dictionary:
	var out := {}
	for k in d:
		var key = k
		if typeof(k) == TYPE_STRING and str(k).is_valid_int():
			key = int(k)
		elif typeof(k) == TYPE_FLOAT:
			key = int(k)
		out[key] = d[k]
	return out


# ============================================================ Bevoelkerung

static func persons(world) -> Dictionary:
	var pop = field(world, "population")
	var p = field(pop, "persons", {})
	return p if typeof(p) == TYPE_DICTIONARY else {}


static func person(world, pid: int) -> Dictionary:
	var pop = field(world, "population")
	if pop == null or pid < 0:
		return {}
	if has_m(pop, "get_person"):
		var p = pop.get_person(pid)
		if typeof(p) == TYPE_DICTIONARY:
			return p
	var all := persons(world)
	var q = all.get(pid, {})
	return q if typeof(q) == TYPE_DICTIONARY else {}


static func person_name(world, pid: int) -> String:
	var p := person(world, pid)
	if p.is_empty():
		return "Unbekannt"
	return ("%s %s" % [p.get("first", ""), p.get("last", "")]).strip_edges()


static func person_alive(world, pid: int) -> bool:
	var p := person(world, pid)
	return not p.is_empty() and bool(p.get("alive", true))


static func person_trait(p: Dictionary, t: String, default := 0.0) -> float:
	var tr = p.get("traits", {})
	if typeof(tr) != TYPE_DICTIONARY:
		return default
	return float(tr.get(t, default))


static func person_age(world, p: Dictionary) -> float:
	if p.has("birth") and world != null and world.has_method("age_years"):
		return world.age_years(float(p.birth))
	return 30.0


static func household_account(world, pid: int) -> int:
	var p := person(world, pid)
	if p.is_empty():
		return -1
	if p.has("account"):
		return int(p.account)
	var hid := int(p.get("household", -1))
	if hid < 0:
		return -1
	var pop = field(world, "population")
	var hh = field(pop, "households", {})
	if typeof(hh) != TYPE_DICTIONARY or not hh.has(hid):
		return -1
	var h = hh[hid]
	return int(h.get("account", -1)) if typeof(h) == TYPE_DICTIONARY else -1


static func person_pos(world, pid: int) -> Vector2:
	var pop = field(world, "population")
	if has_m(pop, "position_of"):
		return to_v2(pop.position_of(world, pid), Vector2.ZERO)
	var p := person(world, pid)
	var loc = p.get("location", {})
	if typeof(loc) == TYPE_DICTIONARY:
		var b := int(loc.get("building", -1))
		if b >= 0:
			return building_pos(world, b)
		return to_v2(loc.get("to", Vector2.ZERO))
	return Vector2.ZERO


static func persons_near(world, pos: Vector2, radius: float) -> Array:
	var pop = field(world, "population")
	if has_m(pop, "persons_near"):
		var r = pop.persons_near(world, pos, radius)
		if typeof(r) == TYPE_ARRAY or typeof(r) == TYPE_PACKED_INT32_ARRAY or typeof(r) == TYPE_PACKED_INT64_ARRAY:
			var out := []
			for i in r:
				out.append(int(i))
			return out
	return []


## Haengt einen Eintrag an das Vorstrafenfeld an, egal welcher Typ verwendet wird.
static func add_record(holder, entry: Dictionary) -> void:
	if holder == null:
		return
	var cur = field(holder, "criminal_record", null)
	match typeof(cur):
		TYPE_ARRAY:
			cur.append(entry.duplicate())
		TYPE_BOOL, TYPE_NIL:
			set_field(holder, "criminal_record", [entry.duplicate()] if cur == null else true)
		TYPE_INT, TYPE_FLOAT:
			set_field(holder, "criminal_record", int(cur) + 1)
		_:
			pass


# ============================================================ Spieler

static func player(world):
	return field(world, "player")


static func player_account(world) -> int:
	var pl = player(world)
	var acc = field(pl, "account", null)
	if acc != null and int(acc) >= 0:
		return int(acc)
	var led = field(world, "ledger")
	return int(field(led, "player_account", -1))


static func player_age(world) -> float:
	var pl = player(world)
	if has_m(pl, "age"):
		return float(pl.age(world))
	var birth = field(pl, "birth", null)
	if birth != null and world != null:
		return world.age_years(float(birth))
	var ch = field(world, "character", {})
	return float(ch.get("age", 25.0)) if typeof(ch) == TYPE_DICTIONARY else 25.0


static func player_alive(world) -> bool:
	return bool(field(player(world), "alive", true))


static func player_skill(world, skill: String, default := 0.0) -> float:
	var sk = field(player(world), "skills", {})
	if typeof(sk) != TYPE_DICTIONARY:
		return default
	return float(sk.get(skill, default))


static func player_need(world, need: String, default := 100.0) -> float:
	var n = field(player(world), "needs", {})
	if typeof(n) != TYPE_DICTIONARY:
		return default
	return float(n.get(need, default))


static func change_player_need(world, need: String, delta: float) -> void:
	var n = field(player(world), "needs", null)
	if typeof(n) == TYPE_DICTIONARY and n.has(need):
		n[need] = clampf(float(n[need]) + delta, 0.0, 100.0)


static func player_relationship(world, pid: int) -> float:
	var rel = field(player(world), "relationships", {})
	if typeof(rel) != TYPE_DICTIONARY:
		return 0.0
	var v = rel.get(pid, 0.0)
	if typeof(v) == TYPE_DICTIONARY:
		return float(v.get("value", v.get("score", 0.0)))
	return float(v)


static func change_player_relationship(world, pid: int, delta: float) -> void:
	var rel = field(player(world), "relationships", null)
	if typeof(rel) != TYPE_DICTIONARY:
		return
	var v = rel.get(pid, 0.0)
	if typeof(v) == TYPE_DICTIONARY:
		v["value"] = clampf(float(v.get("value", 0.0)) + delta, -100.0, 100.0)
	else:
		rel[pid] = clampf(float(v) + delta, -100.0, 100.0)


static func player_education(world) -> String:
	var e = field(player(world), "education", "none")
	if typeof(e) == TYPE_DICTIONARY:
		e = e.get("level", e.get("degree", "none"))
	var s := str(e)
	return s if s in EDU_ORDER else "none"


static func edu_rank(level: String) -> int:
	return maxi(0, EDU_ORDER.find(level))


static func inventory_add(world, good: String, qty: int) -> void:
	var inv = field(player(world), "inventory", null)
	if typeof(inv) == TYPE_DICTIONARY:
		inv[good] = int(inv.get(good, 0)) + qty
		if int(inv[good]) <= 0:
			inv.erase(good)


# ============================================================ Staat

static func gov(world):
	return field(world, "government")


static func gov_account(world) -> int:
	return int(field(gov(world), "account", -1))


static func gov_modifier(world, name: String, default := 1.0) -> float:
	var g = gov(world)
	if has_m(g, "modifier"):
		var v = g.modifier(name)
		if typeof(v) == TYPE_FLOAT or typeof(v) == TYPE_INT:
			return float(v)
	return default


static func gov_rate(world, key: String, default := 0.0) -> float:
	var r = field(gov(world), "rates", {})
	return float(r.get(key, default)) if typeof(r) == TYPE_DICTIONARY else default


static func gov_budget(world, key: String, default := 0.0) -> float:
	var b = field(gov(world), "budget", {})
	return float(b.get(key, default)) if typeof(b) == TYPE_DICTIONARY else default


static func gov_policy(world, key: String, default = null):
	var p = field(gov(world), "policies", {})
	if typeof(p) == TYPE_DICTIONARY and p.has(key):
		return p[key]
	return default


# ============================================================ Stadt / Wirtschaft

static func building(world, bid: int) -> Dictionary:
	var city = field(world, "city")
	if city == null or bid < 0 or bid >= city.buildings.size():
		return {}
	return city.buildings[bid]


static func building_pos(world, bid: int) -> Vector2:
	var b := building(world, bid)
	if b.is_empty():
		return Vector2.ZERO
	return to_v2(b.get("sidewalk_point", b.get("door", Vector2.ZERO)))


static func district(world, did: int) -> Dictionary:
	var city = field(world, "city")
	if city == null or did < 0 or did >= city.districts.size():
		return {}
	return city.districts[did]


static func district_at(world, pos: Vector2) -> Dictionary:
	var city = field(world, "city")
	if city == null:
		return {}
	return city.district_at(pos)


static func business(world, biz_id: int) -> Dictionary:
	var eco = field(world, "economy")
	if eco == null or biz_id < 0:
		return {}
	if has_m(eco, "get_business"):
		var b = eco.get_business(biz_id)
		if typeof(b) == TYPE_DICTIONARY:
			return b
	var all = field(eco, "businesses", {})
	if typeof(all) == TYPE_DICTIONARY:
		var q = all.get(biz_id, {})
		return q if typeof(q) == TYPE_DICTIONARY else {}
	return {}


static func business_for_building(world, bid: int) -> int:
	var eco = field(world, "economy")
	if has_m(eco, "business_for_building"):
		return int(eco.business_for_building(bid))
	var all = field(eco, "businesses", {})
	if typeof(all) == TYPE_DICTIONARY:
		for id in all:
			if int(all[id].get("building", -1)) == bid:
				return int(id)
	return -1


static func business_account(world, biz_id: int) -> int:
	return int(business(world, biz_id).get("account", -1))


static func business_name(world, biz_id: int) -> String:
	var b := business(world, biz_id)
	if b.has("name"):
		return str(b.name)
	var bd := building(world, int(b.get("building", -1)))
	return str(bd.get("name", bd.get("label", "Geschäft")))
