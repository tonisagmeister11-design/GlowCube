class_name Bank
extends RefCounted
## Bank: Ratenkredite, Hypotheken und Geschaeftskredite mit Zins und
## Tilgung (Annuitaet, woechentliche Raten). Die Bank verleiht nur aus ihren
## Reserven (keine Geldschoepfung): Auszahlung = Transfer Bank -> Kreditnehmer,
## Rate = Transfer Kreditnehmer -> Bank.
##
## loan = {id, kind: "mortgage"|"consumer"|"business", borrower_kind, borrower_id,
##   account, principal, remaining, rate_week, payment_week, term_weeks,
##   property, start_day, next_due_day, missed, status}
## Kreditrahmen: Rate hoechstens 35 % des woechentlichen Nettoeinkommens
## abzueglich bestehender Raten; Hypotheken brauchen 10 % Eigenkapital.

const MORTGAGE_RATE_WEEK := 0.0009     # ca. 4,8 % pro Jahr (Echtzeitwochen)
const CONSUMER_RATE_WEEK := 0.0022     # ca. 12 % pro Jahr
const BUSINESS_RATE_WEEK := 0.0015
const MORTGAGE_MAX_WEEKS := 1040
const CONSUMER_MAX_WEEKS := 52
const BUSINESS_MAX_WEEKS := 156
const MAX_PAYMENT_SHARE := 0.35
const MIN_DOWN := 0.10
const KIND_LABELS := {"mortgage": "Hypothek", "consumer": "Ratenkredit", "business": "Geschäftskredit"}

var account := -1
var loans: Dictionary = {}
var next_id := 1
var blocked: Dictionary = {}      # "kind:id" -> true (Kreditsperre nach Ausfall)
var owner: Dictionary = {"kind": "external", "id": -1}
var capital_target := 0.0
var interest_today := 0.0


func setup(world, capital: float) -> void:
	account = world.ledger.open_account("bank", 0, "Havenbrücker Sparkasse", capital)
	capital_target = capital


static func annuity(principal: float, rate: float, weeks: int) -> float:
	if weeks <= 0:
		return principal
	if rate <= 0.0:
		return principal / weeks
	return principal * rate / (1.0 - pow(1.0 + rate, -weeks))


static func present_value(payment: float, rate: float, weeks: int) -> float:
	if rate <= 0.0:
		return payment * weeks
	return payment * (1.0 - pow(1.0 + rate, -weeks)) / rate


static func _key(kind: String, id: int) -> String:
	return "%s:%d" % [kind, id]


## Woechentliches Nettoeinkommen eines Kreditnehmers.
func weekly_income(world, kind: String, id: int) -> float:
	match kind:
		"player":
			return world.player.income_week(world) if world.player else 0.0
		"household":
			return world.population.household_income_week(world, id) if world.population else 0.0
		"business":
			var b: Dictionary = world.economy.get_business(id)
			return maxf(0.0, float(b.get("profit_avg", 0.0)) * 7.0) if not b.is_empty() else 0.0
	return 0.0


func payments_of(kind: String, id: int) -> float:
	var s := 0.0
	for l in loans.values():
		if l.borrower_kind == kind and int(l.borrower_id) == id:
			s += float(l.payment_week)
	return s


func loans_of(kind: String, id: int) -> Array:
	var out := []
	for l in loans.values():
		if l.borrower_kind == kind and int(l.borrower_id) == id:
			out.append(l)
	return out


func debt_of(kind: String, id: int) -> float:
	var s := 0.0
	for l in loans_of(kind, id):
		s += float(l.remaining)
	return s


func _terms(purpose: String) -> Array:
	match purpose:
		"mortgage": return [MORTGAGE_RATE_WEEK, MORTGAGE_MAX_WEEKS]
		"business": return [BUSINESS_RATE_WEEK, BUSINESS_MAX_WEEKS]
	return [CONSUMER_RATE_WEEK, CONSUMER_MAX_WEEKS]


## Kreditrahmen: {weekly_income, max_payment, max_loan, rate_week, weeks, blocked}
func credit_limit(world, kind: String, id: int, purpose := "consumer") -> Dictionary:
	var inc := weekly_income(world, kind, id)
	var t := _terms(purpose)
	var max_pay := maxf(0.0, inc * MAX_PAYMENT_SHARE - payments_of(kind, id))
	var max_loan := present_value(max_pay, t[0], t[1])
	if purpose == "consumer":
		max_loan = minf(max_loan, inc * 8.0)
	var is_blocked := blocked.has(_key(kind, id))
	if is_blocked:
		max_loan = 0.0
	return {"weekly_income": inc, "max_payment": max_pay, "max_loan": floorf(max_loan / 100.0) * 100.0,
		"rate_week": t[0], "weeks": t[1], "blocked": is_blocked}


## Kreditantrag. Bei Erfolg wird der Betrag auf `acc` ausgezahlt.
## Rueckgabe {ok, reason, loan_id, payment_week, rate_week, weeks}
func request_loan(world, kind: String, id: int, acc: int, amount: float, purpose := "consumer",
		weeks := 0, property := -1) -> Dictionary:
	if amount <= 0.0:
		return {"ok": false, "reason": "Ungültiger Kreditbetrag."}
	if kind == "player" and world.player and world.player.age(world) < 18.0:
		return {"ok": false, "reason": "Kredite gibt es erst ab 18 Jahren."}
	var lim := credit_limit(world, kind, id, purpose)
	if lim.blocked:
		return {"ok": false, "reason": "Kreditsperre: Frühere Raten wurden nicht bezahlt."}
	if lim.weekly_income <= 0.0:
		return {"ok": false, "reason": "Ohne regelmäßiges Einkommen gibt die Bank keinen Kredit."}
	var t := _terms(purpose)
	var w: int = weeks if weeks > 0 else int(t[1])
	w = mini(w, int(t[1]))
	var pay := annuity(amount, t[0], w)
	if pay > lim.max_payment + 0.01:
		return {"ok": false, "reason": "Die Rate von %s pro Woche übersteigt deinen Kreditrahmen (höchstens %s)." % [
			Economy.fmt_money(pay), Economy.fmt_money(lim.max_payment)]}
	var L: Ledger = world.ledger
	if L.balance(account) < amount + capital_target * 0.1:
		return {"ok": false, "reason": "Die Bank vergibt derzeit keine neuen Kredite (Liquidität)."}
	if not L.transfer(account, acc, amount, "Kreditauszahlung (%s)" % KIND_LABELS.get(purpose, purpose)):
		return {"ok": false, "reason": "Auszahlung fehlgeschlagen."}
	var lid := next_id
	next_id += 1
	loans[lid] = {"id": lid, "kind": purpose, "borrower_kind": kind, "borrower_id": id, "account": acc,
		"principal": amount, "remaining": amount, "rate_week": t[0], "payment_week": snappedf(pay, 0.01),
		"term_weeks": w, "property": property, "start_day": world.get_day(), "next_due_day": world.get_day() + 7,
		"missed": 0, "status": "active"}
	return {"ok": true, "reason": "", "loan_id": lid, "payment_week": snappedf(pay, 0.01), "rate_week": t[0], "weeks": w}


## Bestehenden Kredit ohne Auszahlung eintragen (Startzustand der Welt:
## Hypotheken auf bereits gekauften Haeusern).
func register_existing(world, kind: String, id: int, acc: int, remaining: float, purpose: String, property := -1) -> int:
	var t := _terms(purpose)
	var w := int(t[1] * 0.6)
	var lid := next_id
	next_id += 1
	loans[lid] = {"id": lid, "kind": purpose, "borrower_kind": kind, "borrower_id": id, "account": acc,
		"principal": remaining, "remaining": remaining, "rate_week": t[0],
		"payment_week": snappedf(annuity(remaining, t[0], w), 0.01), "term_weeks": w, "property": property,
		"start_day": world.get_day(), "next_due_day": world.get_day() + 1 + (lid % 7), "missed": 0, "status": "active"}
	return lid


## Sondertilgung. Rueckgabe {ok, reason, remaining}
func repay(world, loan_id: int, amount: float) -> Dictionary:
	if not loans.has(loan_id):
		return {"ok": false, "reason": "Kredit nicht gefunden.", "remaining": 0.0}
	var l: Dictionary = loans[loan_id]
	var amt := minf(amount, float(l.remaining))
	if amt <= 0.0:
		return {"ok": false, "reason": "Ungültiger Betrag.", "remaining": float(l.remaining)}
	if not world.ledger.transfer(int(l.account), account, amt, "Sondertilgung"):
		return {"ok": false, "reason": "Nicht genug Geld auf dem Konto.", "remaining": float(l.remaining)}
	l.remaining = float(l.remaining) - amt
	if float(l.remaining) <= 0.01:
		loans.erase(loan_id)
		return {"ok": true, "reason": "", "remaining": 0.0}
	return {"ok": true, "reason": "", "remaining": float(l.remaining)}


## Schulden gehen auf einen Erben ueber (oder werden abgeschrieben).
func transfer_debts(from_kind: String, from_id: int, to_kind: String, to_id: int, acc: int) -> void:
	for l in loans_of(from_kind, from_id):
		if to_kind == "":
			loans.erase(l.id)
		else:
			l.borrower_kind = to_kind
			l.borrower_id = to_id
			l.account = acc


func on_day(world, day: int) -> void:
	var L: Ledger = world.ledger
	interest_today = 0.0
	for lid in loans.keys():
		var l: Dictionary = loans[lid]
		if day < int(l.next_due_day):
			continue
		l.next_due_day = int(l.next_due_day) + 7
		var interest: float = float(l.remaining) * float(l.rate_week)
		var due := minf(float(l.payment_week), float(l.remaining) + interest)
		var paid := 0.0
		if L.has_account(int(l.account)):
			paid = L.transfer_partial(int(l.account), account, due, "Kreditrate (%s)" % KIND_LABELS.get(l.kind, l.kind))
		interest_today += minf(paid, interest)
		l.remaining = maxf(0.0, float(l.remaining) + interest - paid)
		if paid < due - 0.01:
			l.missed = int(l.missed) + 1
			if l.borrower_kind == "player":
				world.notify("Kreditrate nicht vollständig bezahlt (%d. Mahnung)." % int(l.missed), "danger")
		else:
			l.missed = 0
		if float(l.remaining) <= 0.01:
			loans.erase(lid)
			if l.borrower_kind == "player":
				world.notify("Kredit vollständig abbezahlt.", "success")
			continue
		if l.kind == "mortgage" and int(l.missed) >= 3:
			loans.erase(lid)
			if world.housing and int(l.property) >= 0:
				world.housing.foreclose(world, int(l.property))
			if l.borrower_kind == "player":
				world.notify("Zwangsversteigerung: Die Bank hat deine Immobilie übernommen.", "danger")
		elif l.kind != "mortgage" and int(l.missed) >= 6:
			loans.erase(lid)
			blocked[_key(l.borrower_kind, int(l.borrower_id))] = true
			if l.borrower_kind == "player":
				world.notify("Der Kredit wurde gekündigt und abgeschrieben. Du erhältst keine Kredite mehr.", "danger")
	# Woechentliche Ausschuettung der Bank
	if day % 7 == 3:
		var excess := L.balance(account) - capital_target * 1.05
		if excess > 0.0:
			var div := excess * 0.4
			var oacc: int = world.economy.owner_account(world, owner) if world.economy else -1
			var local := div * 0.5 if oacc >= 0 else 0.0
			if local > 0.0:
				L.transfer(account, oacc, local, "Gewinnausschüttung Sparkasse")
			if div - local > 0.0:
				L.burn(account, div - local, "Ausschüttung an auswärtige Anteilseigner")


func to_save() -> Dictionary:
	return {"account": account, "loans": loans.duplicate(true), "next_id": next_id, "blocked": blocked.duplicate(),
		"owner": owner.duplicate(), "capital_target": capital_target}


func from_save(d: Dictionary) -> void:
	account = int(d.get("account", -1))
	loans = {}
	var ls: Dictionary = d.get("loans", {})
	for k in ls:
		loans[int(k)] = ls[k].duplicate(true)
	next_id = int(d.get("next_id", 1))
	blocked = d.get("blocked", {}).duplicate()
	owner = d.get("owner", {"kind": "external", "id": -1}).duplicate()
	capital_target = float(d.get("capital_target", 0.0))
