class_name Government
extends RefCounted
## Staat / Stadtverwaltung: Steuern, Haushalt, oeffentliche Arbeitgeber,
## Sozialleistungen und Renten. Rein datenbasiert (kein Szenenbaum).
##
## Geldfluesse (alle ueber den Ledger):
##   Lohnsteuer + Rentenbeitrag   - beim Lohn einbehalten (JobMarket.work)
##   Umsatzsteuer                 - bei jedem Verkauf (Economy)
##   Grundsteuer                  - woechentlich je Immobilie (HousingMarket)
##   Gewerbesteuer                - taeglich auf Gewinne (Economy)
##   Fahrgeld                     - Busfahrten (Population/PlayerLife) -> Verkehrsbetriebe
## Ausgaben: Ressorts erhalten taeglich ihren Haushaltsanteil, zahlen davon
## Loehne des oeffentlichen Dienstes; Ueberschuesse der Ressorts fliessen als
## Sachkosten aus der Stadt (burn). Sozialleistungen kommen aus dem Ressort
## "welfare", Renten aus der Rentenkasse (Defizite deckt die Stadtkasse).
##
## Erweiterbar: `policies` ist ein freies Dictionary; `set_rate`, `set_budget`,
## `set_policy` pruefen Grenzen und senden `policy_changed`.

const DEFAULT_RATES := {"income_tax": 0.20, "sales_tax": 0.12, "property_tax": 0.00012,
	"business_tax": 0.15, "pension_contribution": 0.09}
const RATE_LIMITS := {"income_tax": [0.0, 0.6], "sales_tax": [0.0, 0.3], "property_tax": [0.0, 0.002],
	"business_tax": [0.0, 0.6], "pension_contribution": [0.0, 0.25]}
const RATE_LABELS := {"income_tax": "Einkommensteuer", "sales_tax": "Umsatzsteuer", "property_tax": "Grundsteuer (pro Woche)",
	"business_tax": "Gewerbesteuer", "pension_contribution": "Rentenbeitrag"}

## Haushaltsanteile (Summe 1). "administration" ist eine Ergaenzung zum Vertrag.
const DEFAULT_BUDGET := {"police": 0.17, "education": 0.24, "health": 0.18, "transit": 0.08,
	"infrastructure": 0.12, "welfare": 0.13, "housing": 0.03, "administration": 0.05}
const BUDGET_LABELS := {"police": "Polizei & Feuerwehr", "education": "Bildung", "health": "Gesundheit",
	"transit": "Nahverkehr", "infrastructure": "Infrastruktur & Stadtwerke", "welfare": "Soziales",
	"housing": "Sozialer Wohnungsbau", "administration": "Verwaltung"}
const DEPT_NAMES := {"police": "Polizeipräsidium & Feuerwehr Havenbrück", "education": "Schul- und Hochschulamt",
	"health": "Städtisches Klinikum", "transit": "Havenbrücker Verkehrsbetriebe", "infrastructure": "Stadtwerke Havenbrück",
	"welfare": "Sozialamt", "housing": "Stadtbau Havenbrück", "administration": "Stadtverwaltung"}

## Gebaeudeart -> Ressort (oeffentliche Arbeitgeber).
const BUILDING_DEPT := {"police": "police", "fire_station": "police", "school": "education", "university": "education",
	"hospital": "health", "clinic": "health", "bus_depot": "transit", "station": "transit",
	"city_hall": "administration", "parliament": "administration", "shelter": "welfare"}

const DEFAULT_POLICIES := {
	"unemployment_benefit": 36.0,   # pro Tag fuer Arbeitslose
	"welfare_per_day": 24.0,        # Grundsicherung (Erwachsene ohne Einkommen, nicht arbeitslos gemeldet)
	"homeless_aid": 12.0,           # pro Tag fuer Obdachlose
	"child_benefit": 7.0,           # pro Kind und Tag
	"pension_rate": 0.48,           # Anteil am letzten Tagesbrutto
	"min_pension": 32.0,            # Mindestrente pro Tag
	"max_pension": 140.0,
	"bus_fare": 2.5,                # pro Fahrt
	"minimum_wage": 12.5,           # pro Stunde
	"rent_subsidy": 0.30,           # Anteil der Miete fuer Geringverdiener (Wohngeld)
	"retirement_age": 66.0,
	"school_age": 6.0,
	"doctor_fee": 20.0,             # Praxisgebuehr
	"hospital_fee": 60.0,
}
const POLICY_LIMITS := {
	"unemployment_benefit": [0.0, 200.0], "welfare_per_day": [0.0, 150.0], "homeless_aid": [0.0, 80.0],
	"child_benefit": [0.0, 60.0], "pension_rate": [0.0, 1.0], "min_pension": [0.0, 200.0], "max_pension": [0.0, 500.0],
	"bus_fare": [0.0, 20.0], "minimum_wage": [0.0, 60.0], "rent_subsidy": [0.0, 0.8], "retirement_age": [55.0, 80.0],
	"school_age": [4.0, 8.0], "doctor_fee": [0.0, 200.0], "hospital_fee": [0.0, 500.0],
}

var rates: Dictionary = DEFAULT_RATES.duplicate()
var budget: Dictionary = DEFAULT_BUDGET.duplicate()
var policies: Dictionary = DEFAULT_POLICIES.duplicate()
var account := -1                 # Stadtkasse
var pension_account := -1         # Rentenkasse
var dept_accounts: Dictionary = {}  # ressort -> Konto
## Einnahmen/Ausgaben des laufenden Tages nach Art.
var revenue_today: Dictionary = {}
var spending_today: Dictionary = {}
var history: Array = []           # [{day, revenue, spending, balance}] (max 60)
var revenue_ema := 0.0            # geglaettete Tageseinnahmen
var spend_factor := 1.0
var allocation_today: Dictionary = {}
var reference_allocation: Dictionary = {}
var wage_need: Dictionary = {}    # ressort -> geschaetzter Tageslohnbedarf
var rng := RandomNumberGenerator.new()

const RESERVE_DAYS := 12.0


func setup(world) -> void:
	rng.seed = hash("government:%d" % world.seed)
	var L: Ledger = world.ledger
	account = L.open_account("government", 0, "Stadtkasse Havenbrück", 0.0)
	pension_account = L.open_account("government", 1, "Rentenkasse Havenbrück", 0.0)
	var i := 2
	for dept in DEFAULT_BUDGET:
		dept_accounts[dept] = L.open_account("government", i, DEPT_NAMES[dept], 0.0)
		i += 1
	# Startkapital: wird nach dem Aufbau der Bevoelkerung (Lohnbedarf bekannt)
	# in initial_funding() festgelegt.


## Wird von Population.setup aufgerufen, sobald Loehne/Renten bekannt sind.
func initial_funding(world, est_daily_spending: float, est_pensions: float) -> void:
	var L: Ledger = world.ledger
	revenue_ema = est_daily_spending
	L.mint(account, est_daily_spending * RESERVE_DAYS, "Startkapital Stadtkasse")
	L.mint(pension_account, est_pensions * 10.0, "Startkapital Rentenkasse")
	for dept in budget:
		L.mint(dept_accounts[dept], est_daily_spending * budget[dept] * 2.0, "Startkapital Ressort")
		reference_allocation[dept] = est_daily_spending * DEFAULT_BUDGET[dept]
		allocation_today[dept] = reference_allocation[dept]


# ============================================================ Steuern

func rate(name: String) -> float:
	return float(rates.get(name, 0.0))


func policy(name: String) -> float:
	return float(policies.get(name, DEFAULT_POLICIES.get(name, 0.0)))


## Progressiver Einkommensteuersatz je Stundenlohn (effektiv).
func income_tax_rate(hourly_wage: float) -> float:
	return rate("income_tax") * clampf(0.45 + hourly_wage / 30.0, 0.5, 1.6)


## Teilt einen Bruttolohn in {net, tax, pension}.
func split_wage(gross: float, hourly_wage: float) -> Dictionary:
	var tax := gross * income_tax_rate(hourly_wage)
	var pen := gross * rate("pension_contribution")
	return {"net": gross - tax - pen, "tax": tax, "pension": pen}


## Umsatzsteueranteil in einem Bruttobetrag.
func sales_tax_part(gross: float) -> float:
	var r := rate("sales_tax")
	return gross * r / (1.0 + r)


func record_revenue(kind: String, amount: float) -> void:
	if amount > 0.0:
		revenue_today[kind] = float(revenue_today.get(kind, 0.0)) + amount


func record_spending(kind: String, amount: float) -> void:
	if amount > 0.0:
		spending_today[kind] = float(spending_today.get(kind, 0.0)) + amount


## Zahlt eine Steuer vom Konto an die Stadtkasse. Gibt gezahlten Betrag zurueck.
func collect(world, from_account: int, amount: float, kind: String) -> float:
	if amount <= 0.0:
		return 0.0
	var paid: float = world.ledger.transfer_partial(from_account, account, amount, RATE_LABELS.get(kind, kind))
	record_revenue(kind, paid)
	return paid


## Fahrgeld/Gebuehren an ein Ressort.
func collect_fee(world, from_account: int, dept: String, amount: float, reason: String) -> bool:
	var to: int = dept_accounts.get(dept, account)
	if world.ledger.transfer(from_account, to, amount, reason):
		record_revenue("fees", amount)
		return true
	return false


## Stellt sicher, dass ein Ressortkonto `amount` zahlen kann (Stadtkasse
## springt ein). Gibt true zurueck, wenn danach gedeckt.
func cover(world, dept_account: int, amount: float) -> bool:
	var L: Ledger = world.ledger
	var missing := amount - L.balance(dept_account)
	if missing <= 0.0:
		return true
	if dept_account == account:
		return false
	L.transfer_partial(account, dept_account, missing + 1.0, "Zuschuss Stadtkasse")
	return L.balance(dept_account) >= amount - 0.01


## Zahlt eine Sozialleistung aus dem Sozialressort (bei Bedarf mit Zuschuss).
func pay_welfare(world, to_account: int, amount: float, reason: String) -> float:
	if amount <= 0.0:
		return 0.0
	var acc: int = dept_accounts["welfare"]
	cover(world, acc, amount)
	var paid: float = world.ledger.transfer_partial(acc, to_account, amount, reason)
	record_spending("welfare", paid)
	return paid


func pay_pension(world, to_account: int, amount: float) -> float:
	if amount <= 0.0:
		return 0.0
	var L: Ledger = world.ledger
	if L.balance(pension_account) < amount:
		L.transfer_partial(account, pension_account, amount * 20.0, "Zuschuss Rentenkasse")
		record_spending("pension_subsidy", amount * 20.0)
	var paid := L.transfer_partial(pension_account, to_account, amount, "Rente")
	record_spending("pensions", paid)
	return paid


func dept_account_for_building(kind: String) -> int:
	var dept: String = BUILDING_DEPT.get(kind, "")
	return dept_accounts.get(dept, -1) if dept != "" else -1


func dept_of_account(acc: int) -> String:
	for d in dept_accounts:
		if dept_accounts[d] == acc:
			return d
	return ""


func is_public_account(acc: int) -> bool:
	return acc == account or acc == pension_account or dept_accounts.values().has(acc)


# ============================================================ Politik

## Setzt einen Steuersatz (geprueft). Rueckgabe {ok, reason}.
func set_rate(world, name: String, value: float) -> Dictionary:
	if not RATE_LIMITS.has(name):
		return {"ok": false, "reason": "Unbekannter Steuersatz „%s“." % name}
	var lim: Array = RATE_LIMITS[name]
	if value < lim[0] or value > lim[1]:
		return {"ok": false, "reason": "%s muss zwischen %s und %s liegen." % [RATE_LABELS[name], str(lim[0]), str(lim[1])]}
	rates[name] = value
	if world:
		world.emit_event("policy_changed", [name, value])
	return {"ok": true, "reason": ""}


## Setzt einen Haushaltsanteil; die uebrigen Anteile werden proportional
## angepasst, so dass die Summe 1 bleibt.
func set_budget(world, name: String, share: float) -> Dictionary:
	if not budget.has(name):
		return {"ok": false, "reason": "Unbekannter Haushaltsposten „%s“." % name}
	if share < 0.0 or share > 0.8:
		return {"ok": false, "reason": "Ein Haushaltsanteil muss zwischen 0 % und 80 % liegen."}
	var rest := 0.0
	for k in budget:
		if k != name:
			rest += float(budget[k])
	var scale := (1.0 - share) / rest if rest > 0.0 else 0.0
	for k in budget:
		budget[k] = share if k == name else float(budget[k]) * scale
	if world:
		world.emit_event("policy_changed", ["budget_" + name, share])
	return {"ok": true, "reason": ""}


func set_policy(world, name: String, value: float) -> Dictionary:
	if not POLICY_LIMITS.has(name):
		return {"ok": false, "reason": "Unbekannte Regelung „%s“." % name}
	var lim: Array = POLICY_LIMITS[name]
	if value < lim[0] or value > lim[1]:
		return {"ok": false, "reason": "Wert muss zwischen %s und %s liegen." % [str(lim[0]), str(lim[1])]}
	policies[name] = value
	if world:
		world.emit_event("policy_changed", [name, value])
	return {"ok": true, "reason": ""}


## Auswirkungen der Politik als Faktoren/Werte fuer andere Systeme.
func modifier(name: String) -> float:
	match name:
		"police_effectiveness": return _funding("police")
		"education_quality": return _funding("education")
		"health_quality": return _funding("health")
		"bus_frequency": return _funding("transit")
		"infrastructure_quality": return _funding("infrastructure")
		"housing_quality": return _funding("housing")
		"bus_fare": return policy("bus_fare")
		"welfare_per_day": return policy("welfare_per_day")
		"unemployment_benefit": return policy("unemployment_benefit")
		"rent_subsidy": return policy("rent_subsidy") * clampf(_funding("housing"), 0.5, 1.5)
		"child_benefit": return policy("child_benefit")
		"homeless_aid": return policy("homeless_aid")
		"minimum_wage": return policy("minimum_wage")
	return policy(name) if policies.has(name) else 1.0


## Finanzierungsgrad eines Ressorts: Anteil relativ zum Standard mal
## Ausgabefaktor (0.3 .. 2.0).
func _funding(dept: String) -> float:
	var def: float = DEFAULT_BUDGET.get(dept, 0.1)
	return clampf(float(budget.get(dept, def)) / def * spend_factor, 0.3, 2.0)


# ============================================================ Tageslauf

func on_day(world, day: int) -> void:
	var L: Ledger = world.ledger
	var rev := 0.0
	for k in revenue_today:
		rev += float(revenue_today[k])
	var spent := 0.0
	for k in spending_today:
		spent += float(spending_today[k])
	revenue_ema = lerpf(revenue_ema, rev, 0.25) if revenue_ema > 0.0 else rev
	# Ausgabefaktor nach Reserve: volle Kasse -> mehr ausgeben, leere -> sparen
	var target := revenue_ema * RESERVE_DAYS
	var bal := L.balance(account)
	spend_factor = clampf(1.0 + (bal - target) / maxf(target, 1.0) * 0.5, 0.6, 1.5)
	var spend := revenue_ema * spend_factor
	# Rentenkasse: Ueberschuss ueber 15 Tage Bedarf an die Stadtkasse
	var pen_need := float(spending_today.get("pensions", 0.0))
	if pen_need > 0.0 and L.balance(pension_account) > pen_need * 15.0:
		L.transfer(pension_account, account, L.balance(pension_account) - pen_need * 15.0, "Überschuss Rentenkasse")
	# Rentenzahlungen/Zuschuesse kommen nicht aus den Ressorts
	spend -= float(spending_today.get("pension_subsidy", 0.0)) * 0.05
	for dept in budget:
		var amt: float = maxf(0.0, spend * float(budget[dept]))
		var paid := L.transfer_partial(account, dept_accounts[dept], amt, "Haushaltsmittel")
		allocation_today[dept] = paid
		# Sachkosten: Ueberschuss ueber Lohnbedarf verlaesst die Stadt
		var need: float = float(wage_need.get(dept, 0.0))
		var keep := need * 3.0 + amt * 1.5
		var excess := L.balance(dept_accounts[dept]) - keep
		if excess > 0.0:
			var b := excess * 0.6
			if L.burn(dept_accounts[dept], b, "Sachkosten und Investitionen"):
				record_spending("materials_" + dept, b)
	history.append({"day": day, "revenue": rev, "spending": spent, "balance": L.balance(account),
		"breakdown": revenue_today.duplicate()})
	if history.size() > 60:
		history.pop_front()
	revenue_today.clear()
	spending_today.clear()


## Kennzahlen fuer UI/Tests.
func report(world) -> Dictionary:
	var L: Ledger = world.ledger
	var depts := {}
	for d in dept_accounts:
		depts[d] = {"label": BUDGET_LABELS.get(d, d), "balance": L.balance(dept_accounts[d]),
			"share": float(budget.get(d, 0.0)), "allocation": float(allocation_today.get(d, 0.0))}
	return {"balance": L.balance(account), "pension_fund": L.balance(pension_account), "rates": rates.duplicate(),
		"budget": budget.duplicate(), "policies": policies.duplicate(), "departments": depts,
		"revenue_ema": revenue_ema, "spend_factor": spend_factor, "history": history.duplicate(true)}


# ============================================================ Speichern

func to_save() -> Dictionary:
	return {"rates": rates.duplicate(), "budget": budget.duplicate(), "policies": policies.duplicate(),
		"account": account, "pension_account": pension_account, "dept_accounts": dept_accounts.duplicate(),
		"revenue_today": revenue_today.duplicate(), "spending_today": spending_today.duplicate(),
		"history": history.duplicate(true), "revenue_ema": revenue_ema, "spend_factor": spend_factor,
		"allocation_today": allocation_today.duplicate(), "reference_allocation": reference_allocation.duplicate(),
		"wage_need": wage_need.duplicate(), "rng": rng.state}


func from_save(world, d: Dictionary) -> void:
	rng.seed = hash("government:%d" % world.seed)
	rates = DEFAULT_RATES.duplicate()
	rates.merge(d.get("rates", {}), true)
	budget = d.get("budget", DEFAULT_BUDGET).duplicate()
	policies = DEFAULT_POLICIES.duplicate()
	policies.merge(d.get("policies", {}), true)
	account = int(d.get("account", -1))
	pension_account = int(d.get("pension_account", -1))
	dept_accounts = {}
	var da: Dictionary = d.get("dept_accounts", {})
	for k in da:
		dept_accounts[str(k)] = int(da[k])
	revenue_today = d.get("revenue_today", {}).duplicate()
	spending_today = d.get("spending_today", {}).duplicate()
	history = d.get("history", []).duplicate(true)
	revenue_ema = float(d.get("revenue_ema", 0.0))
	spend_factor = float(d.get("spend_factor", 1.0))
	allocation_today = d.get("allocation_today", {}).duplicate()
	reference_allocation = d.get("reference_allocation", {}).duplicate()
	wage_need = d.get("wage_need", {}).duplicate()
	if d.has("rng"):
		rng.state = int(d.rng)
