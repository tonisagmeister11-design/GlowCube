class_name BusinessAI
extends RefCounted
## Entscheidungen der Geschaefte: Nachschub, Preise, Personal, Insolvenz,
## Ausschuettung. Alle Regeln sind bewusst einfach und nachvollziehbar
## (siehe docs/ECONOMY.md).

## Besucher/Pendler aus dem Umland: Umsatz pro Tag (Euro) je Geschaeftsart bei
## Zonenfaktor 1. Dieses Geld kommt von ausserhalb der Stadt (mint) und
## entspricht dem Dienstleistungsexport des Handels.
const VISITOR_BASE := {
	"supermarket": 1100.0, "grocery": 260.0, "produce": 200.0, "bakery": 300.0, "butcher": 320.0,
	"convenience": 230.0, "market": 650.0, "night_market": 520.0, "flea_market": 240.0, "luxury_market": 1500.0,
	"restaurant": 620.0, "fastfood": 330.0, "cafe": 260.0, "bar": 380.0, "gym": 190.0, "cinema": 650.0,
	"clothing": 480.0, "electronics": 750.0, "furniture": 600.0, "hardware": 520.0, "pharmacy": 380.0,
	"luxury": 1800.0, "secondhand": 150.0, "car_dealer": 2600.0, "gas_station": 560.0, "real_estate": 260.0,
	"bank": 0.0, "job_center": 0.0,
}
const ZONE_VISITORS := {
	"downtown": 1.45, "business": 1.15, "commercial": 1.3, "oldtown": 1.35, "apartments": 0.85, "highrise": 0.8,
	"residential": 0.8, "suburb": 0.85, "rich": 1.1, "estates": 0.8, "poor": 0.7, "slum": 0.5, "industrial": 0.9,
	"university": 1.0, "civic": 1.0, "park": 1.0,
}

## Grundbelegschaft (inkl. Inhaber) je Geschaeftsart und Obergrenze.
const STAFF := {
	"supermarket": [4, 7], "grocery": [2, 3], "produce": [1, 2], "bakery": [2, 4], "butcher": [2, 3],
	"convenience": [1, 2], "market": [3, 5], "night_market": [2, 4], "flea_market": [1, 2], "luxury_market": [2, 4],
	"restaurant": [3, 6], "fastfood": [2, 4], "cafe": [2, 4], "bar": [2, 4], "gym": [2, 3], "cinema": [3, 5],
	"clothing": [2, 4], "electronics": [2, 4], "furniture": [2, 4], "hardware": [2, 4], "pharmacy": [2, 3],
	"luxury": [2, 3], "secondhand": [1, 2], "car_dealer": [3, 5], "gas_station": [2, 3], "real_estate": [2, 3],
	"bank": [3, 5], "job_center": [4, 6],
}

## Geschaeftsarten ohne Warenverkauf / mit Sonderkonto.
const NO_GOODS := ["bank", "real_estate", "job_center"]


## Nachschub: fuellt Artikel unter 50 % des Hoechstbestands auf, soweit das
## Konto (abzueglich einer Lohnreserve) reicht.
static func restock(world, eco, b: Dictionary) -> float:
	if b.status == "bankrupt" or b.inventory.is_empty():
		return 0.0
	var L: Ledger = world.ledger
	var orders := []
	var total := 0.0
	for gid in b.inventory:
		var it: Dictionary = b.inventory[gid]
		if int(it.stock) < int(it.max) * 0.5:
			var need: int = int(it.max) - int(it.stock)
			var uc: float = eco.wholesale_cost(gid, b)
			orders.append([gid, need, uc])
			total += need * uc
	if orders.is_empty():
		return 0.0
	var reserve: float = float(b.get("wage_avg", 0.0)) * 1.0
	var avail := L.balance(b.account) - reserve
	if avail <= 1.0:
		b.short_stock = true
		return 0.0
	var f := minf(1.0, avail / total)
	b.short_stock = f < 1.0
	var cost := 0.0
	for o in orders:
		var q := int(floor(o[1] * f))
		if q <= 0 and f > 0.2 and o[2] < avail - cost:
			q = 1
		if q <= 0:
			continue
		cost += q * float(o[2])
		b.inventory[o[0]].stock = int(b.inventory[o[0]].stock) + q
	if cost > 0.0:
		var paid: float = eco.pay_wholesale(world, b, cost)
		b.cost_today = float(b.cost_today) + paid
	return cost


## Preisanpassung nach Abverkauf (nur bei automatischer Fuehrung).
static func adjust_prices(eco, b: Dictionary) -> void:
	if not b.get("auto", true):
		return
	var sold: Dictionary = b.get("sold_today", {})
	for gid in b.inventory:
		var it: Dictionary = b.inventory[gid]
		var ref: float = eco.reference_price(gid, b)
		var s: int = int(sold.get(gid, 0))
		var ratio := float(s) / maxf(1.0, float(it.max))
		var p: float = it.price
		if int(it.stock) == 0 or ratio > 0.6:
			p *= 1.03
		elif ratio < 0.05 and int(it.max) > 6:
			p *= 0.985
		it.price = Economy.nice_price(clampf(p, ref * 0.8, ref * 1.5))


## Tagesabschluss eines Geschaefts.
static func end_of_day(world, eco, b: Dictionary, day: int) -> void:
	var L: Ledger = world.ledger
	var gov = world.government
	if b.status == "bankrupt":
		b.bankrupt_days = int(b.get("bankrupt_days", 0)) + 1
		_reset(b)
		return
	if b.get("public", false):
		_reset(b)
		return
	var fixed: float = float(b.lease) + float(b.utilities)
	var paid_lease := L.transfer_partial(b.account, eco.landlord_account(world), float(b.lease), "Gewerbemiete")
	eco.stat("lease", paid_lease)
	var paid_util := 0.0
	if gov:
		paid_util = L.transfer_partial(b.account, gov.dept_accounts.get("infrastructure", gov.account), float(b.utilities), "Strom, Wasser, Abfall")
		gov.record_revenue("utilities", paid_util)
	var unpaid := fixed - paid_lease - paid_util
	var profit: float = float(b.revenue_today) - float(b.tax_today) - float(b.cost_today) - float(b.wages_today) - fixed
	if profit > 0.0 and gov:
		gov.collect(world, b.account, profit * gov.rate("business_tax"), "business_tax")
	b.profit_today = profit
	b.history.append({"day": day, "revenue": snappedf(float(b.revenue_today), 0.01), "profit": snappedf(profit, 0.01),
		"customers": int(b.customers_today)})
	if b.history.size() > 14:
		b.history.pop_front()
	if float(b.wages_today) > 0.0:
		b.wage_avg = lerpf(float(b.get("wage_avg", b.wages_today)), float(b.wages_today), 0.25)
	adjust_prices(eco, b)
	# Gleitender Wochengewinn
	var sum := 0.0
	for h in b.history.slice(-7):
		sum += float(h.profit)
	var avg := sum / float(mini(7, b.history.size()))
	b.profit_avg = avg
	var bal := L.balance(b.account)
	var daily_need: float = fixed + float(b.get("wage_avg", 0.0))
	# Notlage: Rechnungen/Loehne nicht voll bezahlt oder Konto fast leer
	if unpaid > 0.5 or float(b.get("unpaid_wages", 0.0)) > 0.5 or bal < daily_need * 0.5:
		b.distress = int(b.distress) + 1
	else:
		b.distress = maxi(0, int(b.distress) - 1)
	b.unpaid_wages = 0.0
	if b.is_service:
		# Bankfilialen werden von der Bank getragen
		_reset(b)
		return
	# Rettung durch Eigentuemer
	if int(b.distress) >= 2:
		var owner_acc: int = eco.owner_account(world, b.owner)
		if b.owner.kind == "household" and owner_acc >= 0:
			var inject := minf(L.balance(owner_acc) * 0.3, float(b.cash_target))
			if inject > 50.0 and L.transfer(owner_acc, b.account, inject, "Kapitaleinlage %s" % b.name):
				b.distress = maxi(0, int(b.distress) - 2)
				bal = L.balance(b.account)
		elif b.owner.kind == "player" and int(b.distress) == 2:
			world.notify("%s ist in Geldnot – zahle Kapital ein, sonst droht die Insolvenz." % b.name, "warning")
	# Personal anpassen
	var jobs = world.jobs
	if jobs:
		var staff: int = b.employees.size()
		if avg < -float(b.get("wage_avg", 0.0)) * 0.25 and staff > 1 and int(b.distress) >= 1:
			jobs.lay_off_one(world, "business", b.id)
		elif avg > maxf(120.0, float(b.get("wage_avg", 100.0)) / maxf(1.0, staff) * 1.5) and bal > float(b.cash_target) \
				and int(b.distress) == 0 and day % 3 == b.id % 3:
			jobs.expand(world, "business", b.id)
	# Insolvenz
	if int(b.distress) >= 6 and bal < daily_need:
		eco.declare_bankrupt(world, b.id)
		return
	# Woechentliche Ausschuettung an den Eigentuemer
	if day % 7 == b.id % 7 and b.owner.kind != "player":
		var excess := bal - float(b.cash_target) * 1.3
		if excess > 0.0:
			var owner_acc2: int = eco.owner_account(world, b.owner)
			if owner_acc2 >= 0 and L.transfer(b.account, owner_acc2, excess * 0.6, "Gewinnausschüttung %s" % b.name):
				eco.stat("dividends", excess * 0.6)
	_reset(b)


static func _reset(b: Dictionary) -> void:
	b.revenue_today = 0.0
	b.customers_today = 0
	b.tax_today = 0.0
	b.cost_today = 0.0
	b.wages_today = 0.0
	b.sold_today = {}
