class_name Ledger
extends RefCounted
## Doppelte Buchfuehrung fuer alle Geldbewegungen. Jede Partei (Spieler,
## Haushalt, Geschaeft, Firma, Staat, Gang) besitzt ein Konto. Geld wird nur
## ueber transfer() bewegt; neues Geld entsteht ausschliesslich ueber mint()
## (Exporterloese, Startkapital) und verschwindet ueber burn() (Importe).
## Dadurch bleibt die Geldmenge nachvollziehbar und testbar:
##   Summe aller Salden == total_minted - total_burned

const PLAYER_KIND := "player"

## id -> {id, kind, owner, name, balance, allow_overdraft, credit_limit}
var accounts: Dictionary = {}
var next_id := 1
var total_minted := 0.0
var total_burned := 0.0
## Letzte Buchungen mit Spielerbeteiligung (fuer Kontoauszug im Telefon).
var player_log: Array = []
var player_account := -1
## Optional: wird bei Aenderungen des Spielerkontos aufgerufen (balance).
var on_player_balance: Callable

const MAX_LOG := 120


func open_account(kind: String, owner: int, name: String, initial := 0.0, allow_overdraft := false) -> int:
	var id := next_id
	next_id += 1
	accounts[id] = {"id": id, "kind": kind, "owner": owner, "name": name, "balance": 0.0,
		"allow_overdraft": allow_overdraft, "credit_limit": 0.0}
	if initial > 0.0:
		mint(id, initial, "Startkapital")
	if kind == PLAYER_KIND:
		player_account = id
	return id


func close_account(id: int, heir := -1) -> void:
	if not accounts.has(id):
		return
	var bal: float = accounts[id].balance
	if bal > 0.0 and heir >= 0 and accounts.has(heir):
		transfer(id, heir, bal, "Kontoaufloesung")
	elif bal != 0.0:
		# Restsaldo verfaellt (positiv) bzw. wird abgeschrieben (negativ)
		if bal > 0.0:
			burn(id, bal, "Kontoaufloesung")
		else:
			mint(id, -bal, "Abschreibung")
	accounts.erase(id)


func has_account(id: int) -> bool:
	return accounts.has(id)


func balance(id: int) -> float:
	return accounts[id].balance if accounts.has(id) else 0.0


func can_pay(id: int, amount: float) -> bool:
	if not accounts.has(id):
		return false
	var a: Dictionary = accounts[id]
	return a.allow_overdraft or a.balance + a.credit_limit >= amount - 0.0001


## Bewegt Geld. Gibt false zurueck (ohne Buchung), wenn das Konto nicht
## gedeckt ist oder ein Konto fehlt. amount muss > 0 sein.
func transfer(from_id: int, to_id: int, amount: float, reason: String) -> bool:
	if amount <= 0.0 or not accounts.has(from_id) or not accounts.has(to_id) or from_id == to_id:
		return amount == 0.0
	if not can_pay(from_id, amount):
		return false
	amount = snappedf(amount, 0.01)
	accounts[from_id].balance -= amount
	accounts[to_id].balance += amount
	_log(from_id, -amount, reason, to_id)
	_log(to_id, amount, reason, from_id)
	return true


## Zahlt so viel wie moeglich (bis amount) und gibt den gezahlten Betrag zurueck.
func transfer_partial(from_id: int, to_id: int, amount: float, reason: String) -> float:
	if not accounts.has(from_id):
		return 0.0
	var a: Dictionary = accounts[from_id]
	var avail: float = amount if a.allow_overdraft else maxf(0.0, a.balance + a.credit_limit)
	var pay := minf(amount, avail)
	if pay > 0.0 and transfer(from_id, to_id, pay, reason):
		return pay
	return 0.0


## Geld von ausserhalb der Stadt (Exporte, Startkapital).
func mint(to_id: int, amount: float, reason: String) -> void:
	if amount <= 0.0 or not accounts.has(to_id):
		return
	amount = snappedf(amount, 0.01)
	accounts[to_id].balance += amount
	total_minted += amount
	_log(to_id, amount, reason, -1)


## Geld verlaesst die Stadt (Importe, Grosshandel von ausserhalb).
func burn(from_id: int, amount: float, reason: String) -> bool:
	if amount <= 0.0 or not accounts.has(from_id):
		return amount == 0.0
	if not can_pay(from_id, amount):
		return false
	amount = snappedf(amount, 0.01)
	accounts[from_id].balance -= amount
	total_burned += amount
	_log(from_id, -amount, reason, -1)
	return true


func total_money() -> float:
	var s := 0.0
	for id in accounts:
		s += accounts[id].balance
	return s


## Fuer Tests: Differenz zwischen Kontensumme und erwarteter Geldmenge.
func conservation_error() -> float:
	return absf(total_money() - (total_minted - total_burned))


func _log(id: int, amount: float, reason: String, other: int) -> void:
	if id != player_account:
		return
	var other_name := "Extern"
	if other >= 0 and accounts.has(other):
		other_name = accounts[other].name
	player_log.push_front({"amount": amount, "reason": reason, "other": other_name})
	if player_log.size() > MAX_LOG:
		player_log.pop_back()
	if on_player_balance.is_valid():
		on_player_balance.call(accounts[id].balance)


func to_save() -> Dictionary:
	var acc := {}
	for id in accounts:
		acc[str(id)] = accounts[id].duplicate()
	return {"accounts": acc, "next_id": next_id, "minted": total_minted, "burned": total_burned,
		"player_log": player_log, "player_account": player_account}


func from_save(d: Dictionary) -> void:
	accounts.clear()
	for k in d.get("accounts", {}):
		var a: Dictionary = d.accounts[k]
		a.id = int(a.id)
		a.owner = int(a.owner)
		accounts[int(k)] = a
	next_id = int(d.get("next_id", 1))
	total_minted = float(d.get("minted", 0.0))
	total_burned = float(d.get("burned", 0.0))
	player_log = d.get("player_log", [])
	player_account = int(d.get("player_account", -1))
