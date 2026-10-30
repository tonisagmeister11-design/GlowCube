class_name HousingMarket
extends RefCounted
## Immobilienmarkt: alle Wohneinheiten der Stadt mit Miete, Kaufpreis,
## Eigentuemer und Mieter.
##
## properties[id] = {id, building, unit, kind: room|apartment|house|villa|estate|shelter_bed,
##   rooms, size_m2, quality 0..1, rent_per_week, price, owner: {kind, id},
##   tenant: {kind: "player"|"household"|"background"|"", id}, for_rent, for_sale,
##   district, zone, wealth, pos, address, floor, arrears, rent_day, label}
## Mieter "background" = nicht simulierte Bewohner (fuellen die Stadt, damit
## nicht Tausende Wohnungen leer stehen); sie zahlen nichts und ziehen
## gelegentlich aus.
##
## Preisformel (siehe docs/ECONOMY.md):
##   Preis/m2 = (1500 + 7500 * wealth^1.5) * (0.75 + 0.5 * quality) * Art-Faktor * price_level
##   Wochenmiete = Preis * 0.0011 (Zimmer: *1.5 + 15)
## Kosten: Mieter zahlen woechentlich (rent_day = Wochentag), Haushalte mit
## wenig Einkommen erhalten Wohngeld. Unternehmen zahlen 15 % der Miete als
## Instandhaltung an Bauunternehmen und 8 % als Grundsteuer, private
## Eigentuemer zahlen property_tax * Wert pro Woche.

const KIND_LABELS := {"room": "WG-Zimmer", "apartment": "Wohnung", "house": "Haus", "villa": "Villa",
	"estate": "Anwesen", "shelter_bed": "Notunterkunft (Bett)"}
const KIND_FACTOR := {"room": 1.0, "apartment": 1.0, "house": 1.0, "villa": 1.0, "estate": 1.15, "shelter_bed": 0.0}
const RENT_YIELD := 0.0011
const BACKGROUND_VACANCY := 0.10
const PURCHASE_FEES := 0.05        # 3,5 % Grunderwerbsteuer + 1,5 % Makler

var properties: Dictionary = {}
var next_id := 1
var rng := RandomNumberGenerator.new()
var _home_index: Dictionary = {}   # "kind:id" -> property id
var _by_building: Dictionary = {}  # building id -> [property ids]
var _vacant_rent: Dictionary = {}  # property id -> true (frei zur Miete)
var _for_sale: Dictionary = {}     # property id -> true
var _price_level := 1.0


func setup(world) -> void:
	rng.seed = hash("housing:%d" % world.seed)
	_price_level = world.rules.f("price_level")
	var city: CityModel = world.city
	var eco = world.economy
	var hc: Array = eco.housing_companies if eco else []
	var gov = world.government
	for b in city.buildings:
		var kind: String = b.kind
		var dist: Dictionary = city.districts[b.district] if int(b.district) >= 0 else {}
		var wealth: float = float(dist.get("wealth", city.zone_info(b.zone).wealth))
		var cond: float = float(b.style.get("condition", 0.5))
		if kind == "shelter":
			for i in 24:
				_add(b, i, "shelter_bed", 1, 6.0, 0.2, wealth, {"kind": "city", "id": 0}, 0)
			continue
		if int(b.res_units) <= 0:
			continue
		match kind:
			"house", "townhouse":
				var rooms := rng.randi_range(4, 6) if kind == "house" else rng.randi_range(3, 5)
				var size := rng.randf_range(110.0, 170.0) if kind == "house" else rng.randf_range(90.0, 140.0)
				_add(b, 0, "house", rooms, size, _quality(cond, wealth), wealth, _developer(hc), 0)
			"villa":
				_add(b, 0, "villa", rng.randi_range(6, 9), rng.randf_range(200.0, 340.0), _quality(cond, wealth), wealth, _developer(hc), 0)
			"estate":
				_add(b, 0, "estate", rng.randi_range(12, 20), rng.randf_range(520.0, 900.0), _quality(cond, wealth), wealth, _developer(hc), 0)
			_:
				var units: int = int(b.res_units)
				var per_floor: int = maxi(1, int(CityCatalog.BUILDING_KINDS[kind].get("res_units", 4)))
				var social: bool = b.zone in ["poor", "slum", "highrise"] and rng.randf() < 0.5
				var owner: Dictionary = {"kind": "city", "id": 0} if social or hc.is_empty() else {"kind": "company", "id": hc[b.id % hc.size()]}
				var wg_share := 0.0
				match kind:
					"tenement": wg_share = 0.35
					"apartment": wg_share = 0.12 if b.zone in ["apartments", "university", "commercial"] else 0.05
					"highrise_res": wg_share = 0.08
				for u in units:
					var floor := 1 + u / per_floor + (1 if int(b.biz_units) > 0 else 0)
					var q := _quality(cond, wealth)
					if rng.randf() < wg_share:
						for r in 3:
							_add(b, u * 10 + r, "room", 1, rng.randf_range(12.0, 22.0), q, wealth, owner, floor)
						continue
					var rooms := _pick_rooms(kind)
					var size := _size_for_rooms(rooms)
					_add(b, u, "apartment", rooms, size, q, wealth, owner, floor)
	_rebuild_indices()


func _developer(hc: Array) -> Dictionary:
	if hc.is_empty():
		return {"kind": "city", "id": 0}
	return {"kind": "company", "id": hc[rng.randi() % hc.size()]}


func _quality(cond: float, wealth: float) -> float:
	return clampf(cond * 0.55 + wealth * 0.3 + rng.randf_range(0.0, 0.2), 0.05, 1.0)


func _pick_rooms(kind: String) -> int:
	var r := rng.randf()
	if kind == "highrise_res":
		return 1 if r < 0.35 else (2 if r < 0.75 else 3)
	if kind == "tenement":
		return 1 if r < 0.4 else (2 if r < 0.85 else 3)
	return 1 if r < 0.2 else (2 if r < 0.55 else (3 if r < 0.85 else 4))


func _size_for_rooms(rooms: int) -> float:
	match rooms:
		1: return rng.randf_range(26.0, 40.0)
		2: return rng.randf_range(44.0, 62.0)
		3: return rng.randf_range(64.0, 86.0)
	return rng.randf_range(88.0, 125.0)


static func price_per_m2(wealth: float, quality: float, kind: String, price_level: float) -> float:
	return (1500.0 + 7500.0 * pow(wealth, 1.5)) * (0.75 + 0.5 * quality) * float(KIND_FACTOR.get(kind, 1.0)) * price_level


static func rent_for(price: float, kind: String) -> float:
	match kind:
		"shelter_bed": return 0.0
		"room": return maxf(70.0, price * RENT_YIELD * 1.5 + 15.0)
		"apartment": return maxf(140.0, price * RENT_YIELD)
	return price * RENT_YIELD


func _add(b: Dictionary, unit: int, kind: String, rooms: int, size: float, quality: float, wealth: float,
		owner: Dictionary, floor: int) -> int:
	var id := next_id
	next_id += 1
	var price := 0.0
	if kind != "shelter_bed":
		price = Economy.nice_price(size * price_per_m2(wealth, quality, kind, _price_level))
	var rent := snappedf(rent_for(price, kind), 5.0)
	var label: String = KIND_LABELS[kind]
	if kind == "apartment":
		label = "%d-Zimmer-Wohnung" % rooms
	properties[id] = {"id": id, "building": b.id, "unit": unit, "kind": kind, "rooms": rooms,
		"size_m2": snappedf(size, 1.0), "quality": snappedf(quality, 0.01), "rent_per_week": rent, "price": price,
		"owner": owner.duplicate(), "tenant": {"kind": "", "id": -1}, "for_rent": kind != "shelter_bed",
		"for_sale": false, "district": int(b.district), "zone": b.zone, "wealth": wealth, "pos": b.sidewalk_point,
		"address": b.address, "floor": floor, "arrears": 0.0, "rent_day": id % 7, "label": label}
	return id


func _rebuild_indices() -> void:
	_home_index.clear()
	_by_building.clear()
	_vacant_rent.clear()
	_for_sale.clear()
	for id in properties:
		var p: Dictionary = properties[id]
		var bid: int = int(p.building)
		if not _by_building.has(bid):
			_by_building[bid] = []
		_by_building[bid].append(id)
		var t: Dictionary = p.tenant
		if t.kind in ["player", "household"]:
			_home_index["%s:%d" % [t.kind, int(t.id)]] = id
		_update_flags(p)


func _update_flags(p: Dictionary) -> void:
	var id: int = int(p.id)
	var vacant: bool = str(p.tenant.kind) == ""
	if vacant and p.for_rent and p.kind != "shelter_bed":
		_vacant_rent[id] = true
	else:
		_vacant_rent.erase(id)
	if p.for_sale:
		_for_sale[id] = true
	else:
		_for_sale.erase(id)


# ============================================================ Abfragen

func get_property(id: int) -> Dictionary:
	return properties.get(id, {})


func properties_in_building(bid: int) -> Array:
	return _by_building.get(bid, []).duplicate()


func home_of(kind: String, id: int) -> int:
	return _home_index.get("%s:%d" % [kind, id], -1)


func vacant_rentals() -> Array:
	return _vacant_rent.keys()


func market_value(id: int) -> float:
	var p: Dictionary = properties.get(id, {})
	return float(p.get("price", 0.0))


## Angebote fuer UI/NPC. filter: {mode: "rent"|"buy", kind, max_rent, max_price,
## min_rooms, district, zone, limit, sort: "price"|"quality"}
## Rueckgabe: Array von Dictionaries (Kopien) mit Zusatzfeldern
## {district_name, building_kind, kind_label}.
func list_available(filter := {}, world = null) -> Array:
	var mode: String = filter.get("mode", "rent")
	var src: Dictionary = _vacant_rent if mode == "rent" else _for_sale
	var out := []
	for id in src:
		var p: Dictionary = properties[id]
		if filter.has("kind") and p.kind != filter.kind:
			continue
		if filter.has("max_rent") and float(p.rent_per_week) > float(filter.max_rent):
			continue
		if filter.has("max_price") and float(p.price) > float(filter.max_price):
			continue
		if filter.has("min_rooms") and int(p.rooms) < int(filter.min_rooms):
			continue
		if filter.has("district") and int(p.district) != int(filter.district):
			continue
		if filter.has("zone") and p.zone != filter.zone:
			continue
		var e := p.duplicate(true)
		e["kind_label"] = KIND_LABELS.get(p.kind, p.kind)
		if world != null and int(p.district) >= 0:
			e["district_name"] = world.city.districts[int(p.district)].name
			e["building_kind"] = world.city.buildings[int(p.building)].kind
		out.append(e)
	var key := "rent_per_week" if mode == "rent" else "price"
	if filter.get("sort", "price") == "quality":
		out.sort_custom(func(a, b): return float(a.quality) > float(b.quality))
	else:
		out.sort_custom(func(a, b): return float(a[key]) < float(b[key]))
	var limit: int = int(filter.get("limit", 0))
	if limit > 0 and out.size() > limit:
		out = out.slice(0, limit)
	return out


func _tenant_account(world, kind: String, id: int) -> int:
	if kind == "player":
		return world.ledger.player_account
	if kind == "household" and world.population:
		var h: Dictionary = world.population.households.get(id, {})
		return int(h.get("account", -1))
	return -1


func _owner_account(world, owner: Dictionary) -> int:
	if owner.kind == "city":
		return world.government.dept_accounts.get("housing", world.government.account) if world.government else -1
	return world.economy.owner_account(world, owner) if world.economy else -1


# ============================================================ Mieten / Kaufen

## Mietet eine Einheit. Die erste Wochenmiete wird sofort faellig.
func rent(world, tenant_kind: String, tenant_id: int, account: int, property_id: int) -> Dictionary:
	var p: Dictionary = properties.get(property_id, {})
	if p.is_empty():
		return {"ok": false, "reason": "Immobilie nicht gefunden."}
	if str(p.tenant.kind) != "":
		return {"ok": false, "reason": "Diese Unterkunft ist bereits vergeben."}
	if p.kind == "shelter_bed":
		return _take_shelter(world, tenant_kind, tenant_id, p)
	if not p.for_rent:
		return {"ok": false, "reason": "Diese Immobilie wird nicht vermietet."}
	if p.owner.kind == tenant_kind and int(p.owner.id) == tenant_id:
		return move_in_owned(world, tenant_kind, tenant_id, property_id)
	var r: float = float(p.rent_per_week)
	if tenant_kind == "player" and world.player:
		if world.player.age(world) < 18.0:
			return {"ok": false, "reason": "Mietverträge kannst du erst ab 18 Jahren abschließen."}
		var inc: float = world.player.income_week(world)
		var bal: float = world.ledger.balance(account)
		var need_bal: float = r * (2.0 if p.kind == "room" else 6.0)
		if inc * 0.5 < r and bal < need_bal:
			return {"ok": false, "reason": "Der Vermieter lehnt ab: Einkommen zu niedrig (Miete höchstens die Hälfte des Einkommens oder %s Rücklagen)." % Economy.fmt_money(need_bal)}
	var owner_acc := _owner_account(world, p.owner)
	if not world.ledger.can_pay(account, r):
		return {"ok": false, "reason": "Nicht genug Geld für die erste Wochenmiete (%s)." % Economy.fmt_money(r)}
	if owner_acc >= 0:
		world.ledger.transfer(account, owner_acc, r, "Miete %s" % p.address)
	# alte Wohnung aufgeben
	var old := home_of(tenant_kind, tenant_id)
	if old >= 0:
		move_out(world, old)
	p.tenant = {"kind": tenant_kind, "id": tenant_id}
	p.arrears = 0.0
	p.rent_day = (world.get_day() + 7) % 7
	_home_index["%s:%d" % [tenant_kind, tenant_id]] = property_id
	_update_flags(p)
	if tenant_kind == "player":
		world.emit_event("player_home_changed", [property_id])
	return {"ok": true, "reason": ""}


func _take_shelter(world, kind: String, id: int, p: Dictionary) -> Dictionary:
	var old := home_of(kind, id)
	if old >= 0 and properties[old].kind != "shelter_bed":
		return {"ok": false, "reason": "Die Notunterkunft ist nur für Menschen ohne Wohnung."}
	if old >= 0:
		move_out(world, old)
	p.tenant = {"kind": kind, "id": id}
	_home_index["%s:%d" % [kind, id]] = int(p.id)
	_update_flags(p)
	if kind == "player":
		world.emit_event("player_home_changed", [int(p.id)])
	return {"ok": true, "reason": ""}


## Freies Bett in einer Notunterkunft (oder -1).
func free_shelter_bed() -> int:
	for id in properties:
		var p: Dictionary = properties[id]
		if p.kind == "shelter_bed" and str(p.tenant.kind) == "":
			return id
	return -1


## Kauft eine Immobilie. Nebenkosten 5 % (Grunderwerbsteuer an die Stadt,
## Maklerprovision). Mit use_mortgage finanziert die Bank bis zu 90 % des
## Kaufpreises. Ist die Immobilie frei, zieht der Kaeufer ein.
func buy(world, buyer_kind: String, buyer_id: int, account: int, property_id: int, use_mortgage := false) -> Dictionary:
	var p: Dictionary = properties.get(property_id, {})
	if p.is_empty():
		return {"ok": false, "reason": "Immobilie nicht gefunden."}
	if not p.for_sale:
		return {"ok": false, "reason": "Diese Immobilie steht nicht zum Verkauf."}
	if p.owner.kind == buyer_kind and int(p.owner.id) == buyer_id:
		return {"ok": false, "reason": "Die Immobilie gehört dir bereits."}
	if buyer_kind == "player" and world.player and world.player.age(world) < 18.0:
		return {"ok": false, "reason": "Immobilien kannst du erst ab 18 Jahren kaufen."}
	var L: Ledger = world.ledger
	var price: float = float(p.price)
	var fees := price * PURCHASE_FEES
	var loan_id := -1
	if use_mortgage:
		var down := price * Bank.MIN_DOWN + fees
		if not L.can_pay(account, down):
			return {"ok": false, "reason": "Für die Finanzierung brauchst du %s Eigenkapital (10 %% + Nebenkosten)." % Economy.fmt_money(down)}
		var own := minf(L.balance(account) - fees, price)
		var need := price - own
		if need > 0.0:
			var res: Dictionary = world.economy.bank.request_loan(world, buyer_kind, buyer_id, account, need, "mortgage", 0, property_id)
			if not res.ok:
				return {"ok": false, "reason": "Bank: " + str(res.reason)}
			loan_id = int(res.loan_id)
	if not L.can_pay(account, price + fees):
		return {"ok": false, "reason": "Nicht genug Geld (Kaufpreis %s + Nebenkosten %s)." % [Economy.fmt_money(price), Economy.fmt_money(fees)]}
	var seller := _owner_account(world, p.owner)
	if seller >= 0:
		L.transfer(account, seller, price, "Immobilienkauf %s" % p.address)
	else:
		L.burn(account, price, "Immobilienkauf %s" % p.address)
	if world.government:
		world.government.collect(world, account, price * 0.035, "property_transfer_tax")
	if world.economy:
		world.economy.broker_commission(world, account, price * 0.015)
	p.owner = {"kind": buyer_kind, "id": buyer_id}
	p.for_sale = false
	p.for_rent = false
	if str(p.tenant.kind) == "" and buyer_kind in ["player", "household"]:
		var old := home_of(buyer_kind, buyer_id)
		if old >= 0:
			move_out(world, old)
		p.tenant = {"kind": buyer_kind, "id": buyer_id}
		_home_index["%s:%d" % [buyer_kind, buyer_id]] = property_id
		if buyer_kind == "player":
			world.emit_event("player_home_changed", [property_id])
	_update_flags(p)
	return {"ok": true, "reason": "", "loan_id": loan_id, "price": price, "fees": fees}


## Eigentuemer zieht in die eigene, freie Immobilie ein.
func move_in_owned(world, kind: String, id: int, property_id: int) -> Dictionary:
	var p: Dictionary = properties.get(property_id, {})
	if p.is_empty() or p.owner.kind != kind or int(p.owner.id) != id:
		return {"ok": false, "reason": "Die Immobilie gehört dir nicht."}
	if str(p.tenant.kind) != "" and not (p.tenant.kind == kind and int(p.tenant.id) == id):
		return {"ok": false, "reason": "Die Immobilie ist vermietet."}
	var old := home_of(kind, id)
	if old >= 0 and old != property_id:
		move_out(world, old)
	p.tenant = {"kind": kind, "id": id}
	p.for_rent = false
	_home_index["%s:%d" % [kind, id]] = property_id
	_update_flags(p)
	if kind == "player":
		world.emit_event("player_home_changed", [property_id])
	return {"ok": true, "reason": ""}


## Eigentuemer bietet die Immobilie zur Miete an.
func list_for_rent(world, owner_kind: String, owner_id: int, property_id: int, rent_week := 0.0) -> Dictionary:
	var p: Dictionary = properties.get(property_id, {})
	if p.is_empty() or p.owner.kind != owner_kind or int(p.owner.id) != owner_id:
		return {"ok": false, "reason": "Die Immobilie gehört dir nicht."}
	if p.tenant.kind == owner_kind and int(p.tenant.id) == owner_id:
		return {"ok": false, "reason": "Du wohnst selbst dort – zieh zuerst aus."}
	if rent_week > 0.0:
		p.rent_per_week = snappedf(rent_week, 1.0)
	p.for_rent = true
	_update_flags(p)
	return {"ok": true, "reason": ""}


## Eigentuemer bietet die Immobilie zum Verkauf an (Preis 0 = Marktwert).
func sell(world, owner_kind: String, owner_id: int, property_id: int, price := 0.0) -> Dictionary:
	var p: Dictionary = properties.get(property_id, {})
	if p.is_empty() or p.owner.kind != owner_kind or int(p.owner.id) != owner_id:
		return {"ok": false, "reason": "Die Immobilie gehört dir nicht."}
	if price > 0.0:
		p.price = snappedf(price, 100.0)
	p.for_sale = true
	p.listed_day = world.get_day()
	_update_flags(p)
	return {"ok": true, "reason": ""}


func move_out(world, property_id: int) -> void:
	var p: Dictionary = properties.get(property_id, {})
	if p.is_empty():
		return
	var t: Dictionary = p.tenant
	if str(t.kind) in ["player", "household"]:
		_home_index.erase("%s:%d" % [t.kind, int(t.id)])
		if t.kind == "player":
			world.emit_event("player_home_changed", [-1])
	var was_owner: bool = t.kind == p.owner.kind and int(t.id) == int(p.owner.id)
	p.tenant = {"kind": "", "id": -1}
	p.arrears = 0.0
	if p.kind != "shelter_bed":
		if was_owner and p.owner.kind == "household":
			# Private Eigentuemer verkaufen beim Auszug
			p.for_sale = true
			p.listed_day = world.get_day()
		elif not was_owner:
			p.for_rent = true
	_update_flags(p)


## Bank uebernimmt eine Immobilie (Zwangsversteigerung).
func foreclose(world, property_id: int) -> void:
	var p: Dictionary = properties.get(property_id, {})
	if p.is_empty():
		return
	var owner: Dictionary = p.owner
	if p.tenant.kind == owner.kind and int(p.tenant.id) == int(owner.id):
		var tk: String = p.tenant.kind
		var tid: int = int(p.tenant.id)
		move_out(world, property_id)
		if tk == "household" and world.population:
			world.population.on_evicted(world, tid)
	p.owner = {"kind": "bank", "id": 0}
	p.for_sale = true
	p.price = Economy.nice_price(float(p.price) * 0.9)
	p.listed_day = world.get_day()
	_update_flags(p)


## Uebertraegt Eigentum (Erbschaft).
func transfer_ownership(from_kind: String, from_id: int, to_kind: String, to_id: int) -> void:
	for p in properties.values():
		if p.owner.kind == from_kind and int(p.owner.id) == from_id:
			p.owner = {"kind": to_kind, "id": to_id}


func owned_by(kind: String, id: int) -> Array:
	var out := []
	for pid in properties:
		var p: Dictionary = properties[pid]
		if p.owner.kind == kind and int(p.owner.id) == id:
			out.append(pid)
	return out


## Direkte Zuweisung beim Weltaufbau / Umzug von NPC-Haushalten (ohne Zahlung).
func assign(kind: String, id: int, property_id: int, as_owner := false) -> void:
	var p: Dictionary = properties[property_id]
	p.tenant = {"kind": kind, "id": id}
	if as_owner:
		p.owner = {"kind": kind, "id": id}
		p.for_rent = false
		p.for_sale = false
	_home_index["%s:%d" % [kind, id]] = property_id
	_update_flags(p)


## Bauträger bieten einen Teil ihrer freien Haeuser und Wohnungen zum Kauf an
## (beim Weltaufbau, nachdem die Haushalte eingezogen sind).
func init_sale_listings(world) -> void:
	for id in _vacant_rent.keys():
		var p: Dictionary = properties[id]
		if p.owner.kind != "company":
			continue
		var share := 0.5 if p.kind in ["house", "villa", "estate"] else (0.08 if p.kind == "apartment" else 0.0)
		if rng.randf() < share:
			p.for_rent = false
			p.for_sale = true
			p.listed_day = world.get_day()
			_update_flags(p)


func set_background(property_id: int) -> void:
	var p: Dictionary = properties[property_id]
	p.tenant = {"kind": "background", "id": -1}
	_update_flags(p)


# ============================================================ Takt

func on_day(world, day: int) -> void:
	var L: Ledger = world.ledger
	var gov = world.government
	var eco = world.economy
	var wd := day % 7
	var tax_rate: float = gov.rate("property_tax") if gov else 0.0
	var evictions := []
	for id in properties:
		var p: Dictionary = properties[id]
		if int(p.rent_day) != wd or p.kind == "shelter_bed":
			continue
		var tk: String = p.tenant.kind
		var owner: Dictionary = p.owner
		var owner_occupied: bool = tk == owner.kind and int(p.tenant.id) == int(owner.id)
		# Grundsteuer privater Eigentuemer
		if owner.kind in ["household", "player"] and gov:
			var oacc := _owner_account(world, owner)
			if oacc >= 0:
				gov.collect(world, oacc, float(p.price) * tax_rate, "property_tax")
		if not (tk in ["player", "household"]) or owner_occupied:
			continue
		if tk == "player" and not world.rules.b("rent_required"):
			continue
		var r: float = float(p.rent_per_week)
		var tacc := _tenant_account(world, tk, int(p.tenant.id))
		var oacc2 := _owner_account(world, owner)
		if tacc < 0:
			continue
		# Wohngeld fuer Geringverdiener (NPC-Haushalte und Spieler)
		if gov:
			var inc := 0.0
			if tk == "household" and world.population:
				inc = world.population.household_income_week(world, int(p.tenant.id))
			elif tk == "player" and world.player:
				inc = world.player.income_week(world)
			if inc > 0.0 and r > inc * 0.4:
				gov.pay_welfare(world, tacc, minf(r * gov.modifier("rent_subsidy"), r - inc * 0.3), "Wohngeld")
		var due: float = r + float(p.arrears)
		var paid := 0.0
		if oacc2 >= 0:
			paid = L.transfer_partial(tacc, oacc2, due, "Miete %s" % p.address)
		else:
			paid = due if L.burn(tacc, due, "Miete") else 0.0
		p.arrears = maxf(0.0, due - paid)
		# Instandhaltung und Grundsteuer der Wohnungsunternehmen
		if paid > 0.0 and oacc2 >= 0 and owner.kind in ["company", "city", "bank"]:
			if eco and not eco.construction_ids.is_empty():
				var cc: Dictionary = eco.companies[eco.construction_ids[id % eco.construction_ids.size()]]
				var m := L.transfer_partial(oacc2, int(cc.account), paid * 0.15, "Instandhaltung")
				cc.revenue_today = float(cc.revenue_today) + m
			if gov and owner.kind != "city":
				gov.collect(world, oacc2, paid * 0.08, "property_tax")
		if float(p.arrears) > 0.01:
			if tk == "player":
				world.notify("Miete nicht vollständig bezahlt – Rückstand %s." % Economy.fmt_money(float(p.arrears)), "danger")
			if float(p.arrears) >= r * 2.0 - 0.01:
				evictions.append(id)
	for id in evictions:
		var p: Dictionary = properties[id]
		var tk: String = p.tenant.kind
		var tid: int = int(p.tenant.id)
		move_out(world, id)
		if tk == "player":
			world.notify("Dir wurde wegen Mietschulden gekündigt. Du hast keine Wohnung mehr.", "danger")
			world.post_news("wohnen", "Zwangsräumung in %s." % str(p.address))
		elif tk == "household" and world.population:
			world.population.on_evicted(world, tid)
	_background_churn(world)
	_market_sales(world, day)


## Nicht simulierte Bewohner ziehen ein/aus, damit der Markt in Bewegung bleibt.
func _background_churn(world) -> void:
	var vac := _vacant_rent.keys()
	var total := properties.size()
	var target := int(total * BACKGROUND_VACANCY)
	# zu viele Leerstaende -> Hintergrundmieter ziehen ein (und umgekehrt)
	var n := mini(12, absi(vac.size() - target) / 4 + 2)
	for i in n:
		if vac.size() > target and not vac.is_empty():
			var pid: int = vac[rng.randi() % vac.size()]
			set_background(pid)
			vac.erase(pid)
	if vac.size() < target:
		var tries := 0
		var freed := 0
		while freed < n and tries < 200:
			tries += 1
			var pid: int = rng.randi_range(1, next_id - 1)
			var p: Dictionary = properties.get(pid, {})
			if not p.is_empty() and p.tenant.kind == "background":
				p.tenant = {"kind": "", "id": -1}
				p.for_rent = p.owner.kind in ["company", "city", "bank"] or p.for_rent
				_update_flags(p)
				freed += 1


## Angebotene Immobilien finden Kaeufer (wohlhabende Haushalte der Stadt
## oder Zuzuegler von ausserhalb, deren Geld per mint in die Stadt kommt).
func _market_sales(world, day: int) -> void:
	for id in _for_sale.keys():
		var p: Dictionary = properties[id]
		if not p.owner.kind in ["player", "household", "bank"]:
			continue
		var listed: int = int(p.get("listed_day", day))
		if day - listed < 2 or rng.randf() > 0.15:
			continue
		var price: float = float(p.price)
		var seller := _owner_account(world, p.owner)
		if seller < 0:
			continue
		var buyer := -1
		if world.population:
			buyer = world.population.find_investor(world, price * 1.6)
		var L: Ledger = world.ledger
		if buyer < 0 and p.owner.kind != "player":
			# kein Kaeufer in der Stadt: Eigentuemer vermietet stattdessen
			if listed_days_exceeded(p, day):
				p.for_sale = false
				p.for_rent = str(p.tenant.kind) == ""
				_update_flags(p)
			continue
		if buyer >= 0:
			var bacc: int = world.population.households[buyer].account
			if not L.transfer(bacc, seller, price, "Immobilienkauf %s" % p.address):
				continue
			p.owner = {"kind": "household", "id": buyer}
			p.for_rent = str(p.tenant.kind) == ""
		else:
			L.mint(seller, price, "Verkauf an Käufer von außerhalb")
			p.owner = {"kind": "company", "id": world.economy.housing_companies[0]} if world.economy and not world.economy.housing_companies.is_empty() else {"kind": "city", "id": 0}
			p.for_rent = str(p.tenant.kind) == ""
		p.for_sale = false
		if p.get("seller_kind", "") == "player" or seller == L.player_account:
			world.notify("Deine Immobilie %s wurde für %s verkauft." % [str(p.address), Economy.fmt_money(price)], "money")
		_update_flags(p)


func listed_days_exceeded(p: Dictionary, day: int) -> bool:
	return day - int(p.get("listed_day", day)) > 6


# ============================================================ Speichern

func to_save() -> Dictionary:
	return {"properties": properties.duplicate(true), "next_id": next_id, "rng": rng.state}


func from_save(world, d: Dictionary) -> void:
	d = d.duplicate(true)
	rng.seed = hash("housing:%d" % world.seed)
	_price_level = world.rules.f("price_level")
	properties = {}
	for k in d.get("properties", {}):
		properties[int(k)] = d.properties[k]
	next_id = int(d.get("next_id", 1))
	if d.has("rng"):
		rng.state = int(d.rng)
	_rebuild_indices()
