class_name Economy
extends RefCounted
## Geschaefte, Firmen, Bank und Warenstroeme der Stadt.
##
## businesses[id] = {id, building, type, name, color, account, inventory: {good_id:
##   {stock, price, max}}, open, close, tier, employees: [person ids], owner: {kind, id},
##   revenue_today, customers_today, status: "open"|"closed"|"bankrupt",
##   district, zone, wealth, pos, quality, lease, utilities, cash_target, distress,
##   history: [{day, revenue, profit, customers}], profit_avg, for_sale, sale_price,
##   auto, staff_base, staff_max, is_service, public, founded_day, sold_today}
## companies[id] siehe Company.
##
## Geldfluesse: Kaeufe (Ledger buyer -> Geschaeft, Umsatzsteueranteil -> Staat),
## Nachschub (Geschaeft -> Grosshaendler, der 78 % als Import verbrennt bzw.
## ohne Grosshaendler direkt burn), Besucher aus dem Umland (mint),
## Firmenexporte (mint), Vorleistungen (burn).

const CAT_TYPES := {
	"food": ["supermarket", "grocery", "market", "produce", "bakery", "butcher", "convenience"],
	"lunch": ["fastfood", "cafe", "bakery", "restaurant", "night_market", "convenience"],
	"cafe": ["cafe", "bakery"],
	"bar": ["bar", "night_market"],
	"restaurant": ["restaurant"],
	"fastfood": ["fastfood", "night_market"],
	"cinema": ["cinema"],
	"gym": ["gym"],
	"durable": ["clothing", "electronics", "furniture", "hardware", "secondhand", "flea_market"],
	"luxury": ["luxury", "luxury_market"],
	"pharmacy": ["pharmacy"],
	"fuel": ["gas_station"],
	"cars": ["car_dealer"],
}
## Attraktivitaet je Geschaeftsart fuer den Lebensmitteleinkauf.
const FOOD_WEIGHT := {"supermarket": 4.0, "market": 2.0, "grocery": 1.6, "produce": 0.8, "bakery": 0.9,
	"butcher": 0.7, "convenience": 0.7}
const IMPORT_SHARE := 0.78
const TIER_FOOD := [0.88, 1.0, 1.12, 1.26]
const TIER_DURABLE := [0.94, 1.0, 1.06, 1.12]

var businesses: Dictionary = {}
var companies: Dictionary = {}
var bank: Bank
var next_business_id := 1
var next_company_id := 1
var rng := RandomNumberGenerator.new()
var stats_today: Dictionary = {}
var history: Array = []
var visitor_scale := 1.0
var commercial_landlord := -1     # Firmen-ID (Gewerbeimmobilien)
var housing_companies: Array = [] # Firmen-IDs (Wohnungsbau)
var wholesale_ids: Array = []
var construction_ids: Array = []
var used_names: Dictionary = {}

# Nicht gespeichert (aus Daten neu aufgebaut)
var _by_building: Dictionary = {}
var _company_by_building: Dictionary = {}
var _near: Dictionary = {}        # district id -> {cat: [business ids nach Entfernung]}
var _goods_cache: Dictionary = {} # business id -> {all: [], meal: [], service: []}


# ============================================================ Hilfen

static func fmt_money(x: float) -> String:
	var neg := x < 0.0
	var v := absf(x)
	var whole := int(floor(v))
	var cents := int(round((v - whole) * 100.0))
	if cents >= 100:
		whole += 1
		cents -= 100
	var s := str(whole)
	var out := ""
	while s.length() > 3:
		out = "." + s.substr(s.length() - 3) + out
		s = s.substr(0, s.length() - 3)
	out = s + out
	return "%s%s,%02d €" % ["-" if neg else "", out, cents]


static func nice_price(p: float) -> float:
	if p < 20.0:
		return maxf(0.1, snappedf(p, 0.05))
	if p < 200.0:
		return snappedf(p, 0.5)
	if p < 5000.0:
		return snappedf(p, 5.0)
	return snappedf(p, 100.0)


static func in_hours(o: int, c: int, h: float) -> bool:
	if o == 0 and c >= 24:
		return true
	if c > o:
		return h >= o and h < c
	return h >= o or h < c


func stat(name: String, amount: float) -> void:
	stats_today[name] = float(stats_today.get(name, 0.0)) + amount


# ============================================================ Aufbau

func setup(world) -> void:
	rng.seed = hash("economy:%d" % world.seed)
	_price_level_cache = world.rules.f("price_level")
	var city: CityModel = world.city
	bank = Bank.new()
	bank.setup(world, 4000000.0)
	# Firmen zuerst (Grosshaendler, Vermieter werden von Geschaeften gebraucht)
	var by_kind := {"office": [], "tower": [], "factory": [], "warehouse": []}
	for b in city.buildings:
		if by_kind.has(b.kind):
			by_kind[b.kind].append(b)
	var taken := {}
	for req in Company.REQUIRED:
		var lst: Array = by_kind[req[0]]
		for b in lst:
			if not taken.has(b.id):
				taken[b.id] = true
				_create_company(world, b, req[1])
				break
	for kind in by_kind:
		for b in by_kind[kind]:
			if taken.has(b.id):
				continue
			taken[b.id] = true
			_create_company(world, b, _pick_weighted(Company.KIND_SECTORS[kind]))
	for b in city.buildings:
		if b.business_type != "" and CityCatalog.BUSINESS_TYPES.has(b.business_type):
			_create_business(world, b)
	_build_indices(world)


func _pick_weighted(w: Dictionary) -> String:
	var total := 0.0
	for k in w:
		total += float(w[k])
	var r := rng.randf() * total
	for k in w:
		r -= float(w[k])
		if r <= 0.0:
			return k
	return w.keys()[0]


func _create_company(world, b: Dictionary, sector: String) -> int:
	var id := next_company_id
	next_company_id += 1
	var name := Company.make_name(rng, sector, used_names)
	var c := {"id": id, "building": b.id, "name": name, "sector": sector, "account": -1, "employees": [],
		"owner": {"kind": "external", "id": -1}, "productivity": rng.randf_range(0.92, 1.08), "status": "open",
		"revenue_today": 0.0, "exports_today": 0.0, "wages_today": 0.0, "costs_today": 0.0, "profit_hist": [],
		"district": int(b.district), "zone": b.zone, "pos": b.sidewalk_point, "size_weight": Company.size_weight(b),
		"cash_target": 0.0, "wage_avg": 0.0, "distress": 0}
	c.account = world.ledger.open_account("company", id, name, 0.0)
	companies[id] = c
	match sector:
		"property":
			if commercial_landlord < 0:
				commercial_landlord = id
		"housing": housing_companies.append(id)
		"wholesale": wholesale_ids.append(id)
		"construction": construction_ids.append(id)
	return id


func _create_business(world, bld: Dictionary) -> int:
	var id := next_business_id
	next_business_id += 1
	var btype: String = bld.business_type
	var spec: Dictionary = CityCatalog.BUSINESS_TYPES[btype]
	var dist: Dictionary = world.city.districts[bld.district] if int(bld.district) >= 0 else {}
	var wealth: float = float(dist.get("wealth", world.city.zone_info(bld.zone).wealth))
	var tier: int = int(spec.tier)
	if wealth >= 0.85 and btype != "secondhand":
		tier += 1
	elif wealth <= 0.25:
		tier -= 1
	tier = clampi(tier, 0, 3)
	var name: String = bld.name if str(bld.name) != "" else _business_name(btype)
	used_names[name] = true
	var area: float = bld.footprint.size.x * bld.footprint.size.y
	var floors_used := 1
	if bld.kind in ["shop", "restaurant"]:
		floors_used = mini(2, maxi(1, int(bld.floors)))
	var lease_rate = (0.05 + 0.20 * wealth) * world.rules.f("price_level")
	if bld.get("open_air", false):
		lease_rate *= 0.3
	var staff: Array = BusinessAI.STAFF.get(btype, [2, 3])
	var b := {"id": id, "building": bld.id, "type": btype, "name": name, "color": bld.get("brand_color", Color.WHITE),
		"account": -1, "inventory": {}, "open": int(spec.open[0]), "close": int(spec.open[1]), "tier": tier,
		"employees": [], "owner": {"kind": "external", "id": -1}, "revenue_today": 0.0, "customers_today": 0,
		"status": "open", "district": int(bld.district), "zone": bld.zone, "wealth": wealth, "pos": bld.sidewalk_point,
		"quality": clampf(rng.randf_range(0.6, 1.0) * (0.85 + 0.3 * float(bld.style.get("condition", 0.5))), 0.4, 1.2),
		"lease": snappedf(area * floors_used * lease_rate, 0.5), "utilities": snappedf(8.0 + area * floors_used * 0.025, 0.5),
		"cash_target": 0.0, "distress": 0, "history": [], "profit_avg": 0.0, "profit_today": 0.0, "for_sale": false,
		"sale_price": 0.0, "auto": true, "staff_base": int(staff[0]), "staff_max": int(staff[1]),
		"is_service": btype == "bank", "public": btype == "job_center", "founded_day": world.get_day(),
		"tax_today": 0.0, "cost_today": 0.0, "wages_today": 0.0, "sold_today": {}, "wage_avg": 0.0,
		"unpaid_wages": 0.0, "short_stock": false, "bankrupt_days": 0}
	# Konto
	if btype == "bank":
		b.account = bank.account
		b.owner = {"kind": "bank", "id": 0}
	elif btype == "job_center" and world.government:
		b.account = world.government.dept_accounts["welfare"]
		b.owner = {"kind": "city", "id": 0}
	else:
		b.account = world.ledger.open_account("business", id, name, 0.0)
		# Ketten (Supermaerkte, Tankstellen, Autohaeuser) gehoeren Firmen
		if btype in ["supermarket", "gas_station", "car_dealer", "cinema"] and not wholesale_ids.is_empty():
			b.owner = {"kind": "company", "id": wholesale_ids[id % wholesale_ids.size()]}
		elif btype in ["market", "night_market", "flea_market", "luxury_market"]:
			b.owner = {"kind": "city", "id": 0}
	# Sortiment
	var stock_value := 0.0
	for gid in GoodsCatalog.SHOP_ASSORTMENT.get(btype, []):
		var g: Dictionary = GoodsCatalog.GOODS[gid]
		var gt: int = int(g.tier)
		if gt > tier + 1 or (tier >= 3 and gt == 0 and g.category in ["clothing", "restaurant"]):
			continue
		if btype == "restaurant" and gid == "tasting_menu" and tier < 3:
			continue
		var mx := GoodsCatalog.default_max_stock(float(g.base_price), btype)
		if btype == "supermarket":
			mx = int(mx * clampf(area / 900.0, 0.7, 1.6))
		var price := nice_price(reference_price_raw(g, btype, tier, wealth, world.rules.f("price_level")) * rng.randf_range(0.96, 1.05))
		var st := int(mx * rng.randf_range(0.6, 1.0))
		b.inventory[gid] = {"stock": st, "price": price, "max": mx}
		stock_value += st * price * float(g.wholesale)
	var fixed: float = float(b.lease) + float(b.utilities)
	b.cash_target = fixed * 8.0 + b.staff_base * 120.0 * 5.0 + stock_value * 0.35
	if b.account != bank.account and not b.public:
		world.ledger.mint(b.account, b.cash_target * rng.randf_range(0.9, 1.4), "Startkapital")
	businesses[id] = b
	return id


func _business_name(btype: String) -> String:
	var lst: Array = CityCatalog.BUSINESS_NAMES.get(btype, ["Laden"])
	for i in 10:
		var n: String = lst[rng.randi() % lst.size()]
		if not used_names.has(n):
			return n
	var base: String = lst[rng.randi() % lst.size()]
	var k := 2
	while used_names.has("%s %d" % [base, k]):
		k += 1
	return "%s %d" % [base, k]


func _build_indices(world) -> void:
	_by_building.clear()
	_company_by_building.clear()
	_goods_cache.clear()
	for id in businesses:
		_by_building[int(businesses[id].building)] = id
		_cache_goods(id)
	for id in companies:
		_company_by_building[int(companies[id].building)] = id
	_near.clear()
	for d in world.city.districts:
		var entry := {}
		for cat in CAT_TYPES:
			var lst := []
			for id in businesses:
				var b: Dictionary = businesses[id]
				if b.type in CAT_TYPES[cat]:
					lst.append([d.center.distance_squared_to(b.pos), id])
			lst.sort_custom(func(a, c): return a[0] < c[0])
			var ids := []
			for e in lst.slice(0, 8):
				ids.append(e[1])
			entry[cat] = ids
		_near[int(d.id)] = entry


func _cache_goods(id: int) -> void:
	var b: Dictionary = businesses[id]
	var all := []
	var meal := []
	var service := []
	for gid in b.inventory:
		var g: Dictionary = GoodsCatalog.GOODS[gid]
		all.append(gid)
		if float(g.meals) > 0.0:
			meal.append(gid)
		if g.service or float(g.needs.get("hunger", 0)) > 0.0 or float(g.needs.get("fun", 0)) > 0.0:
			service.append(gid)
	_goods_cache[id] = {"all": all, "meal": meal, "service": service}


# ============================================================ Preise

static func reference_price_raw(g: Dictionary, btype: String, tier: int, wealth: float, price_level: float) -> float:
	var cat: String = g.category
	var tf: float
	if cat in ["luxury", "vehicle", "gourmet", "electronics", "furniture"]:
		tf = TIER_DURABLE[tier]
	else:
		tf = TIER_FOOD[tier]
	var df := 0.92 + 0.16 * wealth
	if btype in ["convenience", "gas_station"] and not cat in ["fuel"]:
		df += 0.12   # Kiosk-/Tankstellenaufschlag
	return float(g.base_price) * tf * df * price_level


## Referenzpreis eines Artikels in einem Geschaeft (ohne Marktschwankung).
func reference_price(gid: String, b: Dictionary) -> float:
	var g: Dictionary = GoodsCatalog.GOODS[gid]
	return reference_price_raw(g, b.type, int(b.tier), float(b.wealth), _price_level_cache)


var _price_level_cache := 1.0


func wholesale_cost(gid: String, b: Dictionary) -> float:
	var g: Dictionary = GoodsCatalog.GOODS[gid]
	return reference_price(gid, b) * float(g.wholesale) / (1.0 + 0.12)


# ============================================================ Abfragen

func get_business(id: int) -> Dictionary:
	return businesses.get(id, {})


func business_for_building(building_id: int) -> int:
	return _by_building.get(building_id, -1)


func company_for_building(building_id: int) -> int:
	return _company_by_building.get(building_id, -1)


func get_company(id: int) -> Dictionary:
	return companies.get(id, {})


func is_open(world, id: int) -> bool:
	var b: Dictionary = businesses.get(id, {})
	if b.is_empty() or b.status == "bankrupt":
		return false
	return in_hours(int(b.open), int(b.close), world.get_hour_f())


## Geoeffnet zu einer bestimmten Tagesstunde (fuer die Tagesplanung der NPCs).
func is_open_at(id: int, hour_f: float) -> bool:
	var b: Dictionary = businesses.get(id, {})
	if b.is_empty() or b.status == "bankrupt":
		return false
	return in_hours(int(b.open), int(b.close), fposmod(hour_f, 24.0))


func list_businesses(type := "") -> Array:
	var out := []
	for id in businesses:
		if type == "" or businesses[id].type == type:
			out.append(id)
	return out


func price_of(id: int, good_id: String) -> float:
	var b: Dictionary = businesses.get(id, {})
	if b.is_empty() or not b.inventory.has(good_id):
		return -1.0
	return float(b.inventory[good_id].price)


## Sortiment fuer die Ladenansicht: [{good, name, price, stock, unit, category, needs, icon}]
func shop_items(id: int) -> Array:
	var b: Dictionary = businesses.get(id, {})
	var out := []
	if b.is_empty():
		return out
	for gid in b.inventory:
		var g: Dictionary = GoodsCatalog.GOODS[gid]
		var it: Dictionary = b.inventory[gid]
		out.append({"good": gid, "name": g.name, "price": float(it.price), "stock": int(it.stock), "max": int(it.max),
			"unit": g.unit, "category": g.category, "needs": g.needs, "icon": g.icon, "durable": g.durable,
			"service": g.service, "vehicle": g.vehicle})
	return out


## Naechstgelegene Geschaefte einer Kategorie (CAT_TYPES) zu einem Stadtteil.
func near(district_id: int, cat: String) -> Array:
	var e: Dictionary = _near.get(district_id, {})
	return e.get(cat, [])


## Naechstes Geschaeft einer Art zu einer Position (lineare Suche).
func nearest_business(pos: Vector2, types: Array, open_only_hour := -1.0) -> int:
	var best := -1
	var bd := INF
	for id in businesses:
		var b: Dictionary = businesses[id]
		if not b.type in types or b.status == "bankrupt":
			continue
		if open_only_hour >= 0.0 and not is_open_at(id, open_only_hour):
			continue
		var d: float = pos.distance_squared_to(b.pos)
		if d < bd:
			bd = d
			best = id
	return best


func owner_account(world, owner: Dictionary) -> int:
	var kind: String = owner.get("kind", "")
	var oid: int = int(owner.get("id", -1))
	match kind:
		"household":
			var h: Dictionary = world.population.households.get(oid, {}) if world.population else {}
			return int(h.get("account", -1))
		"player":
			return world.ledger.player_account
		"company":
			return int(companies[oid].account) if companies.has(oid) else -1
		"city":
			return world.government.account if world.government else -1
		"bank":
			return bank.account
	return -1


func landlord_account(world) -> int:
	if companies.has(commercial_landlord):
		return int(companies[commercial_landlord].account)
	return world.government.account if world.government else bank.account


# ============================================================ Kaufen

## Kauf durch ein beliebiges Konto (Spieler, Haushalt ...).
## Rueckgabe {ok, reason, total, unit_price, good, qty}
func buy(world, buyer_account: int, business_id: int, good_id: String, qty: int) -> Dictionary:
	var b: Dictionary = businesses.get(business_id, {})
	if b.is_empty():
		return {"ok": false, "reason": "Geschäft nicht gefunden.", "total": 0.0}
	if b.status == "bankrupt":
		return {"ok": false, "reason": "%s hat Insolvenz angemeldet und ist geschlossen." % b.name, "total": 0.0}
	if not is_open(world, business_id):
		return {"ok": false, "reason": "%s ist geschlossen (Öffnungszeiten %s)." % [b.name, hours_text(b)], "total": 0.0}
	if not b.inventory.has(good_id):
		return {"ok": false, "reason": "Dieses Produkt wird hier nicht verkauft.", "total": 0.0}
	if qty < 1:
		return {"ok": false, "reason": "Ungültige Menge.", "total": 0.0}
	var it: Dictionary = b.inventory[good_id]
	if int(it.stock) < qty:
		if int(it.stock) == 0:
			return {"ok": false, "reason": "Ausverkauft.", "total": 0.0}
		return {"ok": false, "reason": "Nicht genug auf Lager (noch %d)." % int(it.stock), "total": 0.0}
	var total: float = snappedf(float(it.price) * qty, 0.01)
	var L: Ledger = world.ledger
	if not L.can_pay(buyer_account, total):
		return {"ok": false, "reason": "Nicht genug Geld (benötigt %s)." % fmt_money(total), "total": total}
	var g: Dictionary = GoodsCatalog.GOODS[good_id]
	if not L.transfer(buyer_account, int(b.account), total, "%s: %s" % [b.name, g.name]):
		return {"ok": false, "reason": "Zahlung fehlgeschlagen.", "total": total}
	it.stock = int(it.stock) - qty
	_book_sale(world, b, good_id, qty, total)
	b.customers_today = int(b.customers_today) + 1
	stat("sales_direct", total)
	world.emit_event("business_changed", [business_id])
	return {"ok": true, "reason": "", "total": total, "unit_price": float(it.price), "good": good_id, "qty": qty}


func hours_text(b: Dictionary) -> String:
	if int(b.open) == 0 and int(b.close) >= 24:
		return "rund um die Uhr"
	return "%d–%d Uhr" % [int(b.open), int(b.close) % 24 if int(b.close) != 24 else 24]


func _book_sale(world, b: Dictionary, gid: String, qty: int, total: float) -> void:
	b.revenue_today = float(b.revenue_today) + total
	var sold: Dictionary = b.sold_today
	sold[gid] = int(sold.get(gid, 0)) + qty
	var gov = world.government
	if gov:
		var tax: float = gov.sales_tax_part(total)
		var paid: float = gov.collect(world, int(b.account), tax, "sales_tax")
		b.tax_today = float(b.tax_today) + paid


## Einkauf eines NPC-Haushalts. Gibt den ausgegebenen Betrag zurueck.
func npc_shop(world, household_account: int, business_id: int, budget: float) -> float:
	return float(npc_basket(world, household_account, business_id, budget, "").spent)


## Wie npc_shop, aber mit Details: {spent, meals, items, needs}.
## purpose: "food" (Vorrat), "lunch"/"leisure" (vor Ort), "durable", "" (gemischt)
func npc_basket(world, acc: int, business_id: int, budget: float, purpose := "") -> Dictionary:
	var res := {"spent": 0.0, "meals": 0.0, "items": 0, "needs": {}}
	var b: Dictionary = businesses.get(business_id, {})
	if b.is_empty() or b.status != "open":
		return res
	var L: Ledger = world.ledger
	budget = minf(budget, L.balance(acc))
	if budget < 0.5:
		return res
	var cache: Dictionary = _goods_cache.get(business_id, {})
	var pool: Array = cache.get("all", [])
	var max_items := 3
	match purpose:
		"food":
			pool = cache.get("meal", [])
			if pool.is_empty():
				pool = cache.get("all", [])
			max_items = 14
		"lunch", "leisure":
			pool = cache.get("service", [])
			if pool.is_empty():
				pool = cache.get("all", [])
			max_items = 2
		"durable":
			max_items = 1
	if pool.is_empty():
		return res
	var total := 0.0
	var inv: Dictionary = b.inventory
	var needs := {}
	var meals := 0.0
	var count := 0
	var tries := 0
	var lines := {}
	while tries < max_items * 3 and count < max_items:
		tries += 1
		var gid: String = pool[rng.randi() % pool.size()]
		var it: Dictionary = inv[gid]
		var avail: int = int(it.stock) - int(lines.get(gid, 0))
		if avail <= 0:
			continue
		var p: float = float(it.price)
		if total + p > budget:
			if purpose == "durable" and tries < 4:
				continue
			if p > budget * 0.5:
				continue
			break
		total += p
		count += 1
		lines[gid] = int(lines.get(gid, 0)) + 1
		var g: Dictionary = GoodsCatalog.GOODS[gid]
		meals += float(g.meals)
		for k in g.needs:
			needs[k] = float(needs.get(k, 0.0)) + float(g.needs[k])
	if total <= 0.0:
		return res
	total = snappedf(total, 0.01)
	if not L.transfer(acc, int(b.account), total, "Einkauf %s" % b.name):
		return res
	for gid in lines:
		inv[gid].stock = int(inv[gid].stock) - int(lines[gid])
		var sold: Dictionary = b.sold_today
		sold[gid] = int(sold.get(gid, 0)) + int(lines[gid])
	b.revenue_today = float(b.revenue_today) + total
	b.customers_today = int(b.customers_today) + 1
	var gov = world.government
	if gov:
		b.tax_today = float(b.tax_today) + gov.collect(world, int(b.account), gov.sales_tax_part(total), "sales_tax")
	stat("sales_npc", total)
	res.spent = total
	res.meals = meals
	res.items = count
	res.needs = needs
	return res


## Besucher aus dem Umland kaufen ein (Geld kommt von ausserhalb).
func _visitor_sales(world, b: Dictionary, budget: float) -> void:
	var pool: Array = _goods_cache.get(int(b.id), {}).get("all", [])
	if pool.is_empty() or budget <= 0.0:
		return
	var inv: Dictionary = b.inventory
	var total := 0.0
	var tries := 0
	var n := 0
	while tries < 10 and budget - total > 0.3:
		tries += 1
		var gid: String = pool[rng.randi() % pool.size()]
		var it: Dictionary = inv[gid]
		if int(it.stock) <= 0:
			continue
		var p: float = float(it.price)
		if p > budget - total:
			if rng.randf() < (budget - total) / p:
				total += p
				n += 1
				it.stock = int(it.stock) - 1
				b.sold_today[gid] = int(b.sold_today.get(gid, 0)) + 1
			break
		total += p
		n += 1
		it.stock = int(it.stock) - 1
		b.sold_today[gid] = int(b.sold_today.get(gid, 0)) + 1
	if total <= 0.0:
		return
	world.ledger.mint(int(b.account), total, "Umsatz mit Besuchern")
	b.revenue_today = float(b.revenue_today) + total
	b.customers_today = int(b.customers_today) + n
	var gov = world.government
	if gov:
		b.tax_today = float(b.tax_today) + gov.collect(world, int(b.account), gov.sales_tax_part(total), "sales_tax")
	stat("sales_visitors", total)


## Bezahlt eine Grosshandelsbestellung. Rueckgabe: gezahlter Betrag.
func pay_wholesale(world, b: Dictionary, cost: float) -> float:
	var L: Ledger = world.ledger
	var amt := minf(cost, L.balance(int(b.account)))
	if amt <= 0.0:
		return 0.0
	if not wholesale_ids.is_empty():
		var cid: int = wholesale_ids[int(b.id) % wholesale_ids.size()]
		var c: Dictionary = companies[cid]
		if L.transfer(int(b.account), int(c.account), amt, "Wareneinkauf %s" % c.name):
			c.revenue_today = float(c.revenue_today) + amt
			var imp := amt * IMPORT_SHARE
			L.burn(int(c.account), imp, "Warenimport")
			c.costs_today = float(c.costs_today) + imp
			stat("imports", imp)
			stat("wholesale", amt)
			return amt
		return 0.0
	if L.burn(int(b.account), amt, "Warenimport"):
		stat("imports", amt)
		return amt
	return 0.0


## Lohnbuchung (von JobMarket gemeldet) fuer Gewinnrechnung.
func record_wage(employer_kind: String, employer_id, amount: float) -> void:
	if employer_kind == "business" and businesses.has(employer_id):
		businesses[employer_id].wages_today = float(businesses[employer_id].wages_today) + amount
	elif employer_kind == "company" and companies.has(employer_id):
		companies[employer_id].wages_today = float(companies[employer_id].wages_today) + amount


func record_unpaid(employer_kind: String, employer_id, amount: float) -> void:
	if employer_kind == "business" and businesses.has(employer_id):
		businesses[employer_id].unpaid_wages = float(businesses[employer_id].unpaid_wages) + amount


## Maklerprovision an ein Maklerbuero (Immobiliengeschaefte).
func broker_commission(world, payer_acc: int, amount: float) -> float:
	var ids := list_businesses("real_estate")
	if ids.is_empty() or amount <= 0.0:
		return 0.0
	var b: Dictionary = businesses[ids[rng.randi() % ids.size()]]
	if b.status == "bankrupt":
		return 0.0
	var paid: float = world.ledger.transfer_partial(payer_acc, int(b.account), amount, "Maklerprovision")
	b.revenue_today = float(b.revenue_today) + paid
	return paid


# ============================================================ Eigentuemer-Aktionen

func _owns(b: Dictionary, kind: String, id: int) -> bool:
	return b.owner.kind == kind and int(b.owner.id) == id


func businesses_owned_by(kind: String, id: int) -> Array:
	var out := []
	for bid in businesses:
		if _owns(businesses[bid], kind, id):
			out.append(bid)
	return out


func set_price(world, business_id: int, good_id: String, price: float, kind := "player", id := 0) -> Dictionary:
	var b: Dictionary = businesses.get(business_id, {})
	if b.is_empty() or not _owns(b, kind, id):
		return {"ok": false, "reason": "Das Geschäft gehört dir nicht."}
	if not b.inventory.has(good_id):
		return {"ok": false, "reason": "Artikel nicht im Sortiment."}
	if price <= 0.0:
		return {"ok": false, "reason": "Der Preis muss größer als 0 sein."}
	b.inventory[good_id].price = snappedf(price, 0.01)
	b.auto = false
	world.emit_event("business_changed", [business_id])
	return {"ok": true, "reason": ""}


func set_auto(world, business_id: int, auto: bool, kind := "player", id := 0) -> Dictionary:
	var b: Dictionary = businesses.get(business_id, {})
	if b.is_empty() or not _owns(b, kind, id):
		return {"ok": false, "reason": "Das Geschäft gehört dir nicht."}
	b.auto = auto
	return {"ok": true, "reason": ""}


## Ware nachbestellen (Eigentuemer). Kosten = Grosshandelspreis.
func order_stock(world, business_id: int, good_id: String, qty: int, kind := "player", id := 0) -> Dictionary:
	var b: Dictionary = businesses.get(business_id, {})
	if b.is_empty() or not _owns(b, kind, id):
		return {"ok": false, "reason": "Das Geschäft gehört dir nicht."}
	if not b.inventory.has(good_id) or qty < 1:
		return {"ok": false, "reason": "Ungültige Bestellung."}
	var it: Dictionary = b.inventory[good_id]
	qty = mini(qty, int(it.max) * 2 - int(it.stock))
	if qty < 1:
		return {"ok": false, "reason": "Das Lager ist voll."}
	var cost := wholesale_cost(good_id, b) * qty
	if world.ledger.balance(int(b.account)) < cost:
		return {"ok": false, "reason": "Das Geschäftskonto reicht nicht (%s nötig)." % fmt_money(cost)}
	var paid := pay_wholesale(world, b, cost)
	b.cost_today = float(b.cost_today) + paid
	it.stock = int(it.stock) + qty
	world.emit_event("business_changed", [business_id])
	return {"ok": true, "reason": "", "cost": paid, "qty": qty}


func withdraw(world, business_id: int, amount: float, kind := "player", id := 0) -> Dictionary:
	var b: Dictionary = businesses.get(business_id, {})
	if b.is_empty() or not _owns(b, kind, id):
		return {"ok": false, "reason": "Das Geschäft gehört dir nicht."}
	var to := owner_account(world, b.owner)
	if amount <= 0.0 or not world.ledger.transfer(int(b.account), to, amount, "Entnahme %s" % b.name):
		return {"ok": false, "reason": "Nicht genug Geld auf dem Geschäftskonto."}
	return {"ok": true, "reason": ""}


func deposit(world, business_id: int, from_account: int, amount: float) -> Dictionary:
	var b: Dictionary = businesses.get(business_id, {})
	if b.is_empty():
		return {"ok": false, "reason": "Geschäft nicht gefunden."}
	if amount <= 0.0 or not world.ledger.transfer(from_account, int(b.account), amount, "Kapitaleinlage %s" % b.name):
		return {"ok": false, "reason": "Nicht genug Geld."}
	b.distress = 0
	return {"ok": true, "reason": ""}


## Zum Verkauf stehende Geschaefte (insolvente und angebotene).
func businesses_for_sale() -> Array:
	var out := []
	for id in businesses:
		var b: Dictionary = businesses[id]
		if b.for_sale or b.status == "bankrupt":
			out.append({"id": id, "name": b.name, "type": b.type, "price": sale_price(id), "bankrupt": b.status == "bankrupt",
				"district": int(b.district)})
	return out


func sale_price(id: int) -> float:
	var b: Dictionary = businesses[id]
	if b.status == "bankrupt":
		return nice_price(float(b.cash_target) * 1.2)
	if b.for_sale and float(b.sale_price) > 0.0:
		return float(b.sale_price)
	return nice_price(maxf(float(b.cash_target), float(b.profit_avg) * 120.0))


## Geschaeft kaufen. Bei insolventen Geschaeften fliessen 60 % des Preises als
## Betriebskapital in das Geschaeft, 40 % an die Glaeubiger (Stadtkasse).
func buy_business(world, buyer_kind: String, buyer_id: int, account: int, business_id: int) -> Dictionary:
	var b: Dictionary = businesses.get(business_id, {})
	if b.is_empty():
		return {"ok": false, "reason": "Geschäft nicht gefunden."}
	if b.type in ["bank", "job_center"]:
		return {"ok": false, "reason": "Dieses Geschäft ist nicht verkäuflich."}
	if not (b.for_sale or b.status == "bankrupt"):
		return {"ok": false, "reason": "Dieses Geschäft steht nicht zum Verkauf."}
	if _owns(b, buyer_kind, buyer_id):
		return {"ok": false, "reason": "Das Geschäft gehört dir bereits."}
	var price := sale_price(business_id)
	var L: Ledger = world.ledger
	if not L.can_pay(account, price):
		return {"ok": false, "reason": "Nicht genug Geld (Kaufpreis %s)." % fmt_money(price)}
	if b.status == "bankrupt":
		var creditors: int = world.government.account if world.government else bank.account
		L.transfer(account, creditors, price * 0.4, "Kauf %s (Insolvenzmasse)" % b.name)
		L.transfer(account, int(b.account), price * 0.6, "Betriebskapital %s" % b.name)
		_reopen(world, b, {"kind": buyer_kind, "id": buyer_id}, b.name)
	else:
		var seller := owner_account(world, b.owner)
		if seller >= 0:
			L.transfer(account, seller, price, "Verkauf %s" % b.name)
		else:
			L.burn(account, price, "Kauf %s" % b.name)
		if world.jobs:
			world.jobs.change_owner(world, business_id, b.owner, {"kind": buyer_kind, "id": buyer_id})
		b.owner = {"kind": buyer_kind, "id": buyer_id}
		b.for_sale = false
	world.post_news("wirtschaft", "%s hat einen neuen Eigentümer." % b.name)
	world.emit_event("business_changed", [business_id])
	return {"ok": true, "reason": "", "price": price}


func declare_bankrupt(world, id: int) -> void:
	var b: Dictionary = businesses[id]
	if b.status == "bankrupt":
		return
	b.status = "bankrupt"
	b.bankrupt_days = 0
	b.for_sale = false
	if world.jobs:
		world.jobs.close_employer(world, "business", id)
	for gid in b.inventory:
		b.inventory[gid].stock = 0
	# Restguthaben an Glaeubiger (Vermieter)
	var L: Ledger = world.ledger
	var rest := L.balance(int(b.account))
	if rest > 0.0:
		L.transfer(int(b.account), landlord_account(world), rest, "Insolvenzmasse")
	stat("bankruptcies", 1.0)
	world.post_news("wirtschaft", "%s (%s) hat Insolvenz angemeldet." % [b.name, CityCatalog.BUSINESS_TYPES[b.type].label])
	if b.owner.kind == "player":
		world.notify("Dein Geschäft %s ist insolvent." % b.name, "danger")
	world.emit_event("business_changed", [id])


func _reopen(world, b: Dictionary, owner: Dictionary, name: String) -> void:
	b.owner = owner
	b.name = name
	b.status = "closed"
	b.distress = 0
	b.history = []
	b.profit_avg = 0.0
	b.for_sale = false
	b.founded_day = world.get_day()
	b.auto = true
	used_names[name] = true
	if world.ledger.accounts.has(int(b.account)):
		world.ledger.accounts[int(b.account)].name = name
	if world.jobs:
		world.jobs.reopen_employer(world, "business", int(b.id), owner)
	BusinessAI.restock(world, self, b)
	b.status = "open" if is_open_at(int(b.id), world.get_hour_f()) else "closed"


## Neugruendung insolventer Geschaefte durch Investoren aus der Stadt.
func _try_refound(world, b: Dictionary) -> void:
	if int(b.bankrupt_days) < 3 or rng.randf() > 0.3 or world.population == null:
		return
	var capital: float = float(b.cash_target) * 1.1
	var hid: int = world.population.find_investor(world, capital * 1.5)
	if hid < 0:
		return
	var acc: int = world.population.households[hid].account
	if not world.ledger.transfer(acc, int(b.account), capital, "Gründungskapital"):
		return
	var name := _business_name(b.type)
	_reopen(world, b, {"kind": "household", "id": hid}, name)
	stat("foundings", 1.0)
	world.post_news("wirtschaft", "Neueröffnung: %s (%s)." % [name, CityCatalog.BUSINESS_TYPES[b.type].label])


# ============================================================ Takt

func on_hour(world, day: int, hour: int) -> void:
	_price_level_cache = world.rules.f("price_level")
	var hf := float(hour) + 0.5
	for id in businesses:
		var b: Dictionary = businesses[id]
		if b.status == "bankrupt":
			continue
		var op := in_hours(int(b.open), int(b.close), hf)
		b.status = "open" if op else "closed"
		var o: int = int(b.open)
		if hour == o or (o == 0 and hour == 5) or (op and hour == 13 and b.get("short_stock", false)):
			BusinessAI.restock(world, self, b)
		if op and not b.public and not b.is_service:
			var vb: float = float(BusinessAI.VISITOR_BASE.get(b.type, 0.0))
			if vb > 0.0:
				var hours_open := float((int(b.close) - o + 24) % 24) if int(b.close) != o else 24.0
				if o == 0 and int(b.close) >= 24:
					hours_open = 24.0
				var per_h: float = vb * float(BusinessAI.ZONE_VISITORS.get(b.zone, 1.0)) * float(b.quality) \
					* visitor_scale * _price_level_cache / maxf(1.0, hours_open)
				_visitor_sales(world, b, per_h * rng.randf_range(0.6, 1.4))


func on_day(world, day: int) -> void:
	for id in businesses:
		BusinessAI.end_of_day(world, self, businesses[id], day)
		var b: Dictionary = businesses[id]
		if b.status == "bankrupt":
			_try_refound(world, b)
	for id in companies:
		Company.end_of_day(world, self, companies[id], day)
	bank.on_day(world, day)
	var rep := quick_report(world)
	rep["day"] = day
	rep["flows"] = stats_today.duplicate()
	history.append(rep)
	if history.size() > 90:
		history.pop_front()
	stats_today.clear()


## Kennzahlen (schnell): Geldmenge nach Kontoart, Geschaeftslage.
func quick_report(world) -> Dictionary:
	var by_kind := {}
	for a in world.ledger.accounts.values():
		by_kind[a.kind] = float(by_kind.get(a.kind, 0.0)) + float(a.balance)
	var open := 0
	var bankrupt := 0
	for b in businesses.values():
		if b.status == "bankrupt":
			bankrupt += 1
		else:
			open += 1
	return {"money": world.ledger.total_money(), "by_kind": by_kind, "businesses_active": open,
		"businesses_bankrupt": bankrupt, "loans": bank.loans.size()}


# ============================================================ Speichern

func to_save() -> Dictionary:
	return {"businesses": businesses.duplicate(true), "companies": companies.duplicate(true), "bank": bank.to_save(),
		"next_business_id": next_business_id, "next_company_id": next_company_id, "rng": rng.state,
		"stats_today": stats_today.duplicate(), "history": history.duplicate(true), "visitor_scale": visitor_scale,
		"commercial_landlord": commercial_landlord, "housing_companies": housing_companies.duplicate(),
		"wholesale_ids": wholesale_ids.duplicate(), "construction_ids": construction_ids.duplicate(),
		"used_names": used_names.duplicate()}


func from_save(world, d: Dictionary) -> void:
	d = d.duplicate(true)
	rng.seed = hash("economy:%d" % world.seed)
	businesses = {}
	for k in d.get("businesses", {}):
		businesses[int(k)] = d.businesses[k]
	companies = {}
	for k in d.get("companies", {}):
		companies[int(k)] = d.companies[k]
	bank = Bank.new()
	bank.from_save(d.get("bank", {}))
	next_business_id = int(d.get("next_business_id", 1))
	next_company_id = int(d.get("next_company_id", 1))
	if d.has("rng"):
		rng.state = int(d.rng)
	stats_today = d.get("stats_today", {})
	history = d.get("history", [])
	visitor_scale = float(d.get("visitor_scale", 1.0))
	commercial_landlord = int(d.get("commercial_landlord", -1))
	housing_companies = d.get("housing_companies", [])
	wholesale_ids = d.get("wholesale_ids", [])
	construction_ids = d.get("construction_ids", [])
	used_names = d.get("used_names", {})
	_price_level_cache = world.rules.f("price_level")
	_build_indices(world)
