class_name Politics
extends RefCounted
## Politik: Parteien, Ämter, Wahlen, Wahlkampf, Zustimmung, Skandale und
## Entscheidungen. Alle Entscheidungen fliessen in die Government-Daten
## (rates, budget, policies), die Wirtschaft, Arbeitsmarkt, Verkehr und
## Kriminalitaet lesen. Kein Hebel ist Dekoration: apply_decision() liefert
## die gemessenen Vorher/Nachher-Werte zurueck.
##
## Ämterleiter: Parteimitglied -> Stadtrat -> Bürgermeister -> Abgeordneter
## -> Minister -> Präsident (Staatsoberhaupt).
## Befugnisse: Stadtrat stellt Anträge (Abstimmung im Rat), Bürgermeister
## steuert die städtischen Hebel, Minister ein Ressort, Präsident alle Hebel.
## Ohne Spieler im Amt regieren NPC-Amtsinhaber nach Parteiprogramm und
## Zufriedenheit der Bevölkerung.

const A := preload("res://scripts/crime/contract_access.gd")

const PARTIES := {
	1: {"short": "BfO", "name": "Bürgerbund für Ordnung", "color": Color(0.20, 0.32, 0.62), "popularity": 0.27,
		"description": "Sicherheit, niedrige Steuern, starke Polizei.",
		"ideology": {"tax": -0.6, "welfare": -0.5, "police": 0.9, "education": 0.1, "transit": -0.2, "housing": -0.3, "health": 0.0, "infrastructure": 0.4}},
	2: {"short": "SoA", "name": "Soziale Allianz", "color": Color(0.80, 0.18, 0.22), "popularity": 0.28,
		"description": "Sozialstaat, bezahlbares Wohnen, Bildung für alle.",
		"ideology": {"tax": 0.6, "welfare": 0.9, "police": -0.2, "education": 0.6, "transit": 0.3, "housing": 0.7, "health": 0.6, "infrastructure": 0.1}},
	3: {"short": "LH", "name": "Lebenswertes Havenbrück", "color": Color(0.22, 0.62, 0.32), "popularity": 0.22,
		"description": "Nahverkehr, Schulen, gesunde Stadt.",
		"ideology": {"tax": 0.2, "welfare": 0.2, "police": -0.4, "education": 0.7, "transit": 0.9, "housing": 0.5, "health": 0.4, "infrastructure": 0.2}},
	4: {"short": "PdF", "name": "Partei des Fortschritts", "color": Color(0.95, 0.70, 0.12), "popularity": 0.23,
		"description": "Unternehmergeist, niedrige Abgaben, moderne Infrastruktur.",
		"ideology": {"tax": -0.8, "welfare": -0.6, "police": 0.2, "education": 0.4, "transit": 0.0, "housing": -0.2, "health": -0.1, "infrastructure": 0.9}},
}

## Ämter. record: schwerste erlaubte Vorstrafe ("any" = egal,
## "misdemeanor" = keine Verbrechen, "minor" = nur Ordnungswidrigkeiten,
## "none" = keine Vorstrafe). prior: eines dieser Ämter muss vorher
## bekleidet worden sein (oder Einfluss >= prior_influence).
const OFFICES := {
	"member": {"title": "Parteimitglied", "rank": 0, "term": 0, "salary": 0.0, "seats": 0, "min_age": 16.0,
		"influence": 0.0, "fee": 50.0, "record": "any", "prior": [], "powers": "Wahlkampf, Spenden, Kandidatur für den Stadtrat."},
	"council": {"title": "Stadtrat", "rank": 1, "term": 10, "salary": 60.0, "seats": 9, "min_age": 18.0,
		"influence": 10.0, "fee": 200.0, "record": "misdemeanor", "prior": [], "powers": "Anträge zu städtischen Hebeln (Abstimmung im Rat)."},
	"mayor": {"title": "Bürgermeister", "rank": 2, "term": 14, "salary": 220.0, "seats": 1, "min_age": 21.0,
		"influence": 30.0, "fee": 1000.0, "record": "misdemeanor", "prior": ["council"], "prior_influence": 50.0,
		"powers": "Grundsteuer, Budgetanteile für Nahverkehr, Wohnen und Infrastruktur, Fahrpreis, Infrastrukturprogramm."},
	"mp": {"title": "Abgeordneter", "rank": 3, "term": 18, "salary": 180.0, "seats": 15, "min_age": 21.0,
		"influence": 35.0, "fee": 1500.0, "record": "misdemeanor", "prior": [], "powers": "Sitz im Parlament, Voraussetzung für ein Ministeramt."},
	"minister": {"title": "Minister", "rank": 4, "term": 0, "salary": 300.0, "seats": 0, "min_age": 25.0,
		"influence": 55.0, "fee": 0.0, "record": "minor", "prior": ["mp"], "appointed": true,
		"powers": "Alle Hebel des eigenen Ressorts."},
	"president": {"title": "Präsident (Staatsoberhaupt)", "rank": 5, "term": 24, "salary": 500.0, "seats": 1, "min_age": 30.0,
		"influence": 70.0, "fee": 5000.0, "record": "minor", "prior": ["mayor", "mp", "minister"],
		"powers": "Alle Steuern, alle Budgetanteile, Sozialleistungen, Gesetze (Polizeibefugnisse, Mietpreisbremse)."},
}
const OFFICE_ORDER := ["member", "council", "mayor", "mp", "minister", "president"]
const ELECTED := ["council", "mayor", "mp", "president"]
const FIRST_ELECTION := {"council": 10, "mayor": 14, "mp": 18, "president": 24}

const PORTFOLIOS := {
	"finance": "Finanzen", "interior": "Inneres", "education": "Bildung", "health": "Gesundheit",
	"transport": "Verkehr und Infrastruktur", "social": "Soziales und Wohnen",
}

## Entscheidungshebel. kind: rate | budget | policy | program.
## scope: city (Bürgermeister, Stadtratsanträge) | national.
const LEVERS := {
	"income_tax": {"label": "Einkommensteuer", "kind": "rate", "key": "income_tax", "scope": "national", "portfolio": "finance",
		"min": 0.0, "max": 0.6, "step": 0.01, "unit": "%", "issue": "tax",
		"description": "Steuer auf Löhne und Gehälter."},
	"sales_tax": {"label": "Umsatzsteuer", "kind": "rate", "key": "sales_tax", "scope": "national", "portfolio": "finance",
		"min": 0.0, "max": 0.35, "step": 0.01, "unit": "%", "issue": "tax",
		"description": "Steuer auf alle Einkäufe."},
	"business_tax": {"label": "Unternehmensteuer", "kind": "rate", "key": "business_tax", "scope": "national", "portfolio": "finance",
		"min": 0.0, "max": 0.5, "step": 0.01, "unit": "%", "issue": "tax",
		"description": "Steuer auf Gewinne von Geschäften und Firmen."},
	"property_tax": {"label": "Grundsteuer", "kind": "rate", "key": "property_tax", "scope": "city", "portfolio": "finance",
		"min": 0.0, "max": 0.05, "step": 0.001, "unit": "%", "issue": "tax",
		"description": "Abgabe auf Immobilien, fließt über Mieten an Haushalte weiter."},
	"budget_police": {"label": "Polizeibudget", "kind": "budget", "key": "police", "scope": "national", "portfolio": "interior",
		"min": 0.02, "max": 0.5, "step": 0.01, "unit": "share", "issue": "police",
		"description": "Anteil des Haushalts für die Polizei."},
	"budget_education": {"label": "Bildungsbudget", "kind": "budget", "key": "education", "scope": "national", "portfolio": "education",
		"min": 0.02, "max": 0.5, "step": 0.01, "unit": "share", "issue": "education",
		"description": "Schulen und Universität. Wirkt langfristig auf Qualifikation."},
	"budget_health": {"label": "Gesundheitsbudget", "kind": "budget", "key": "health", "scope": "national", "portfolio": "health",
		"min": 0.02, "max": 0.5, "step": 0.01, "unit": "share", "issue": "health",
		"description": "Krankenhäuser und Praxen."},
	"budget_transit": {"label": "Nahverkehrsbudget", "kind": "budget", "key": "transit", "scope": "city", "portfolio": "transport",
		"min": 0.02, "max": 0.5, "step": 0.01, "unit": "share", "issue": "transit",
		"description": "Busse: Takt und Linien."},
	"budget_infrastructure": {"label": "Infrastrukturbudget", "kind": "budget", "key": "infrastructure", "scope": "city", "portfolio": "transport",
		"min": 0.02, "max": 0.5, "step": 0.01, "unit": "share", "issue": "infrastructure",
		"description": "Straßen, Brücken, Beleuchtung."},
	"budget_welfare": {"label": "Sozialbudget", "kind": "budget", "key": "welfare", "scope": "national", "portfolio": "social",
		"min": 0.02, "max": 0.5, "step": 0.01, "unit": "share", "issue": "welfare",
		"description": "Grundsicherung und Arbeitslosengeld."},
	"budget_housing": {"label": "Wohnungsbudget", "kind": "budget", "key": "housing", "scope": "city", "portfolio": "social",
		"min": 0.02, "max": 0.5, "step": 0.01, "unit": "share", "issue": "housing",
		"description": "Wohnungsbau und Mietzuschüsse."},
	"bus_fare": {"label": "Fahrpreis Bus", "kind": "policy", "key": "bus_fare", "scope": "city", "portfolio": "transport",
		"min": 0.0, "max": 6.0, "step": 0.1, "unit": "€", "issue": "transit",
		"description": "Preis eines Einzelfahrscheins."},
	"welfare_level": {"label": "Höhe der Grundsicherung", "kind": "policy", "key": "welfare_level", "scope": "national", "portfolio": "social",
		"min": 0.5, "max": 2.0, "step": 0.05, "unit": "x", "issue": "welfare",
		"description": "Faktor auf die tägliche Grundsicherung für Haushalte mit niedrigem Einkommen."},
	"unemployment_benefit": {"label": "Arbeitslosengeld", "kind": "policy", "key": "unemployment_benefit_level", "scope": "national", "portfolio": "social",
		"min": 0.5, "max": 2.0, "step": 0.05, "unit": "x", "issue": "welfare",
		"description": "Faktor auf das Arbeitslosengeld."},
	"rent_cap": {"label": "Mietpreisbremse", "kind": "policy", "key": "rent_cap", "scope": "national", "portfolio": "social",
		"min": 0.0, "max": 1.0, "step": 1.0, "unit": "toggle", "issue": "housing",
		"description": "Gesetz: Mieten dürfen bei Neuvermietung kaum steigen."},
	"police_strictness": {"label": "Polizeibefugnisse", "kind": "policy", "key": "police_strictness", "scope": "national", "portfolio": "interior",
		"min": 0.5, "max": 1.5, "step": 0.05, "unit": "x", "issue": "police",
		"description": "Gesetz: Härte von Kontrollen, Strafen und Haftzeiten."},
	"infrastructure_program": {"label": "Infrastrukturprogramm finanzieren", "kind": "program", "key": "infrastructure_program", "scope": "city", "portfolio": "transport",
		"min": 0.0, "max": 250000.0, "step": 5000.0, "unit": "€", "issue": "infrastructure",
		"description": "Einmalige Investition aus dem Staatskonto in Straßen und Brücken."},
}

var rng := RandomNumberGenerator.new()
var parties: Dictionary = {}          # id -> {short, name, popularity, ideology, account, members}
var council_seats: Dictionary = {}    # party id -> Sitze
var parliament_seats: Dictionary = {}
var holders: Dictionary = {}          # "mayor"/"president"/"minister:<ressort>" -> {name, party, is_player, since, term_end}
var next_election: Dictionary = {}    # office -> Tag
var last_results: Dictionary = {}     # office -> Ergebnis
var policies: Dictionary = {}         # eigene Kopie aller gesetzten Policies
var refs: Dictionary = {}             # Startwerte (Bezug fuer Zufriedenheit)
var indices := {"education": 1.0, "infrastructure": 1.0, "health": 1.0}
var infrastructure_bonus := 0.0
var decisions: Array = []             # {day, id, label, before, after, by}
var last_satisfaction: Dictionary = {}

# ---- Spieler
var party := -1
var influence := 0.0
var office := ""                      # "" | council | mayor | mp | minister | president
var portfolio := ""
var office_since := -1
var term_end := -1
var approval := 0.5
var low_approval_days := 0
var held_offices: Array = []
var candidacies: Dictionary = {}      # office -> true
var endorsements: Dictionary = {}     # person id -> Tag
var scandals: Array = []
var revealed_records := 0
var last_motion_day := -1
var last_activity_day := 0


# ============================================================ Aufbau

func setup(world) -> void:
	rng.seed = hash("politics:%d" % int(world.seed))
	parties.clear()
	for pid in PARTIES:
		var def: Dictionary = PARTIES[pid]
		var acc := -1
		if world.ledger != null:
			acc = world.ledger.open_account("company", 9000 + int(pid), "Partei " + str(def.name), 5000.0)
		parties[pid] = {"id": pid, "short": def.short, "name": def.name, "popularity": float(def.popularity),
			"ideology": def.ideology.duplicate(), "account": acc, "leader_name": _random_name()}
	_capture_refs(world)
	council_seats = _allocate_seats(OFFICES.council.seats, _party_shares(0.0))
	parliament_seats = _allocate_seats(OFFICES.mp.seats, _party_shares(0.0))
	var mayor_party := _largest(council_seats)
	var pres_party := _largest(parliament_seats)
	holders = {
		"mayor": {"name": _random_name(), "party": mayor_party, "is_player": false, "since": 0, "term_end": FIRST_ELECTION.mayor},
		"president": {"name": _random_name(), "party": pres_party, "is_player": false, "since": 0, "term_end": FIRST_ELECTION.president},
	}
	for p in PORTFOLIOS:
		holders["minister:" + p] = {"name": _random_name(), "party": pres_party, "is_player": false, "since": 0, "term_end": -1}
	next_election = FIRST_ELECTION.duplicate()
	last_satisfaction = satisfaction(world)


func post_load(world) -> void:
	if refs.is_empty():
		_capture_refs(world)


func _capture_refs(world) -> void:
	refs = {}
	for id in LEVERS:
		var v := current_value(world, id)
		refs[id] = v
	if float(refs.get("bus_fare", 0.0)) <= 0.0:
		refs["bus_fare"] = 2.5


func _random_name() -> String:
	var f: Array = CityCatalog.FIRST_NAMES_F if rng.randf() < 0.5 else CityCatalog.FIRST_NAMES_M
	var l: Array = CityCatalog.LAST_NAMES
	return "%s %s" % [f[rng.randi_range(0, f.size() - 1)], l[rng.randi_range(0, l.size() - 1)]]


func _party_shares(noise: float) -> Dictionary:
	var out := {}
	var total := 0.0
	for pid in parties:
		var v := maxf(0.01, float(parties[pid].popularity) + rng.randf_range(-noise, noise))
		out[pid] = v
		total += v
	for pid in out:
		out[pid] = float(out[pid]) / total
	return out


## Sitzverteilung nach groessten Resten.
func _allocate_seats(n: int, shares: Dictionary) -> Dictionary:
	var seats := {}
	var rests := []
	var used := 0
	var ids := shares.keys()
	ids.sort()
	for pid in ids:
		var q := float(shares[pid]) * n
		seats[pid] = int(floor(q))
		used += int(floor(q))
		rests.append([q - floor(q), pid])
	rests.sort_custom(func(a, b): return a[0] > b[0])
	var i := 0
	while used < n and i < rests.size():
		seats[rests[i][1]] = int(seats[rests[i][1]]) + 1
		used += 1
		i += 1
	return seats


func _largest(seats: Dictionary) -> int:
	var best := -1
	for pid in seats:
		if best < 0 or int(seats[pid]) > int(seats[best]):
			best = pid
	return best


# ============================================================ Government-Zugriff

## Aktueller Wert eines Hebels aus dem Government (bzw. eigener Kopie).
func current_value(world, lever_id: String) -> float:
	var l: Dictionary = LEVERS.get(lever_id, {})
	if l.is_empty():
		return 0.0
	var key: String = l.key
	match str(l.kind):
		"rate":
			return A.gov_rate(world, key, float(policies.get("rate:" + key, 0.0)))
		"budget":
			return A.gov_budget(world, key, float(policies.get("budget:" + key, 0.0)))
		"policy":
			var def := 1.0
			if key == "bus_fare":
				def = A.gov_modifier(world, "bus_fare", 2.5)
			elif key == "rent_cap":
				def = 0.0
			var v = A.gov_policy(world, key, null)
			if v == null:
				return float(policies.get(key, def))
			return float(v)
		"program":
			return 0.0
	return 0.0


## Wert eines Gesetzes/einer Policy (Fallback fuer Systeme ohne Government).
func policy_value(key: String, default := 1.0) -> float:
	return float(policies.get(key, default))


func _call_setter(world, g, method: String, key: String, value: float) -> bool:
	if not A.has_m(g, method):
		return false
	for m in g.get_method_list():
		if m.name != method:
			continue
		var n: int = m.args.size()
		if n == 2:
			g.call(method, key, value)
			return true
		if n == 3:
			g.call(method, world, key, value)
			return true
	return false


func _write_rate(world, key: String, v: float) -> void:
	var g = A.gov(world)
	if not _call_setter(world, g, "set_rate", key, v):
		var r = A.field(g, "rates", null)
		if typeof(r) == TYPE_DICTIONARY:
			r[key] = v
	policies["rate:" + key] = v


func _write_budget(world, key: String, v: float) -> void:
	var g = A.gov(world)
	var b = A.field(g, "budget", null)
	if typeof(b) != TYPE_DICTIONARY:
		policies["budget:" + key] = v
		return
	# Gesamtsumme der Anteile bleibt erhalten: andere Posten werden anteilig angepasst.
	var old := float(b.get(key, 0.0))
	var total := 0.0
	for k in b:
		total += float(b[k])
	var others := total - old
	var new_others := maxf(0.0, total - v)
	var scale := new_others / others if others > 0.0 else 1.0
	var values := {}
	for k in b:
		values[k] = v if str(k) == key else float(b[k]) * scale
	for k in values:
		if not _call_setter(world, g, "set_budget", str(k), float(values[k])):
			b[k] = float(values[k])
	policies["budget:" + key] = v


func _write_policy(world, key: String, v: float) -> void:
	var g = A.gov(world)
	if not _call_setter(world, g, "set_policy", key, v):
		var p = A.field(g, "policies", null)
		if typeof(p) == TYPE_DICTIONARY:
			p[key] = v
	policies[key] = v


func _ratio(world, lever_id: String) -> float:
	var ref := float(refs.get(lever_id, 0.0))
	var cur := current_value(world, lever_id)
	if ref <= 0.0:
		return 1.0 if cur <= 0.0 else 1.0 + cur
	return cur / ref


# ============================================================ Zufriedenheit

## Zufriedenheit der Bevoelkerung je Thema (0..1) und gesamt.
func satisfaction(world) -> Dictionary:
	var out := {}
	# Arbeitslosigkeit
	var adults := 0
	var unemployed := 0
	var all := A.persons(world)
	for pid in all:
		var p = all[pid]
		if typeof(p) != TYPE_DICTIONARY or not bool(p.get("alive", true)):
			continue
		var age := A.person_age(world, p)
		if age < 18.0 or age >= 65.0:
			continue
		adults += 1
		var job = p.get("job", {})
		if typeof(job) != TYPE_DICTIONARY or job.is_empty():
			if int(p.get("school", -1)) < 0:
				unemployed += 1
	var u := float(unemployed) / float(adults) if adults > 0 else 0.08
	out.unemployment_rate = u
	out.unemployment = clampf(1.0 - u * 2.5, 0.0, 1.0)
	# Kriminalitaet
	var crime = A.field(world, "crime")
	var per_day := 0.0
	if crime != null:
		var cs: Dictionary = crime.city_stats()
		per_day = float(cs.crimes_per_day)
		if crime.history.is_empty():
			per_day = crime.expected_npc_crimes_per_day(world)
	var pop_k := maxf(1.0, float(all.size()) / 1000.0) if all.size() > 0 else 2.0
	out.crime_per_1000 = per_day / pop_k
	out.crime = clampf(1.0 - out.crime_per_1000 / 16.0, 0.0, 1.0)
	# Steuern
	var tr := (_ratio(world, "income_tax") + _ratio(world, "sales_tax") + _ratio(world, "business_tax")) / 3.0
	tr = (tr * 3.0 + _ratio(world, "property_tax")) / 4.0
	out.taxes = clampf(0.5 - (tr - 1.0), 0.0, 1.0)
	# Sozialleistungen
	var wr := _ratio(world, "budget_welfare") * current_value(world, "welfare_level") * (0.5 + 0.5 * current_value(world, "unemployment_benefit"))
	out.welfare = clampf(0.5 + (wr - 1.0) * 0.6, 0.0, 1.0)
	# Bildung (langfristiger Index), Gesundheit, Nahverkehr, Wohnen
	out.education = clampf(0.5 + (float(indices.education) - 1.0) * 0.8, 0.0, 1.0)
	var hq := A.gov_modifier(world, "health_quality", -1.0)
	out.health = clampf(0.5 + ((hq if hq >= 0.0 else float(indices.health)) - 1.0) * 0.8, 0.0, 1.0)
	var fare_r := current_value(world, "bus_fare") / maxf(0.1, float(refs.get("bus_fare", 2.5)))
	out.transit = clampf(0.5 + (_ratio(world, "budget_transit") - 1.0) * 0.6 - (fare_r - 1.0) * 0.3 + (float(indices.infrastructure) - 1.0) * 0.3, 0.0, 1.0)
	out.housing = clampf(0.5 + (_ratio(world, "budget_housing") - 1.0) * 0.5 + current_value(world, "rent_cap") * 0.1, 0.0, 1.0)
	out.overall = clampf(out.unemployment * 0.2 + out.crime * 0.18 + out.taxes * 0.17 + out.welfare * 0.12 +
		out.education * 0.1 + out.health * 0.1 + out.transit * 0.07 + out.housing * 0.06, 0.0, 1.0)
	return out


# ============================================================ Partei / Einfluss

func join_party(world, party_id: int) -> Dictionary:
	if not parties.has(party_id):
		return {"ok": false, "reason": "Unbekannte Partei."}
	if party == party_id:
		return {"ok": false, "reason": "Du bist bereits Mitglied."}
	if A.player_age(world) < float(OFFICES.member.min_age):
		return {"ok": false, "reason": "Mindestalter %d Jahre." % int(OFFICES.member.min_age)}
	var fee := float(OFFICES.member.fee)
	if not world.ledger.transfer(A.player_account(world), int(parties[party_id].account), fee, "Parteibeitrag"):
		return {"ok": false, "reason": "Der Mitgliedsbeitrag von %.0f € ist nicht gedeckt." % fee}
	if party >= 0:
		influence *= 0.5
		_resign(world, "Parteiwechsel")
		candidacies.clear()
	party = party_id
	influence = maxf(influence, 2.0)
	_set_player_office_field(world)
	return {"ok": true, "text": "Du bist jetzt Mitglied der Partei %s." % parties[party_id].name}


func leave_party(world) -> Dictionary:
	if party < 0:
		return {"ok": false, "reason": "Du bist in keiner Partei."}
	_resign(world, "Parteiaustritt")
	party = -1
	candidacies.clear()
	influence *= 0.5
	_set_player_office_field(world)
	return {"ok": true, "text": "Du bist aus der Partei ausgetreten."}


func _gain_influence(world, amount: float) -> float:
	var g := amount * (1.0 - influence / 130.0) if amount > 0.0 else amount
	influence = clampf(influence + g, 0.0, 100.0)
	last_activity_day = world.get_day()
	return g


## Wahlkampf: Stunden auf der Strasse und Geld (geht an die Parteikasse).
func campaign(world, hours: float, money: float, advance := true) -> Dictionary:
	if party < 0:
		return {"ok": false, "reason": "Du musst Mitglied einer Partei sein."}
	hours = clampf(hours, 0.0, 12.0)
	money = maxf(0.0, money)
	if money > 0.0 and not world.ledger.transfer(A.player_account(world), int(parties[party].account), money, "Wahlkampfspende"):
		return {"ok": false, "reason": "Nicht genug Geld."}
	if advance and hours > 0.0:
		world.advance(hours * 60.0)
	var cha := A.player_skill(world, "charisma", 20.0)
	var gain := _gain_influence(world, hours * 0.5 * (1.0 + cha / 100.0) + sqrt(money) / 5.0)
	parties[party].popularity = float(parties[party].popularity) + money / 400000.0 + hours * 0.0008
	return {"ok": true, "influence_gain": gain, "influence": influence, "minutes": hours * 60.0,
		"text": "Wahlkampf: Einfluss +%.1f (jetzt %.0f)." % [gain, influence]}


## Rede halten (2 Stunden). Kann auch schiefgehen.
func give_speech(world, advance := true) -> Dictionary:
	if party < 0:
		return {"ok": false, "reason": "Du musst Mitglied einer Partei sein."}
	if advance:
		world.advance(120.0)
	var cha := A.player_skill(world, "charisma", 20.0)
	if rng.randf() < 0.55 + cha / 200.0:
		var g := _gain_influence(world, rng.randf_range(3.0, 7.0))
		return {"ok": true, "success": true, "influence_gain": g, "text": "Deine Rede kommt gut an. Einfluss +%.1f." % g}
	var l := _gain_influence(world, -2.0)
	return {"ok": true, "success": false, "influence_gain": l, "text": "Die Rede kam schlecht an."}


## Unterstuetzung durch Bekannte (Beziehung >= 30), einmal je Person in 10 Tagen.
func seek_endorsement(world, person_id: int) -> Dictionary:
	if party < 0:
		return {"ok": false, "reason": "Du musst Mitglied einer Partei sein."}
	var rel := A.player_relationship(world, person_id)
	if rel < 30.0:
		return {"ok": false, "reason": "Die Beziehung ist zu schwach (mindestens 30)."}
	var day: int = world.get_day()
	if endorsements.has(person_id) and day - int(endorsements[person_id]) < 10:
		return {"ok": false, "reason": "Diese Person unterstützt dich bereits."}
	endorsements[person_id] = day
	var p := A.person(world, person_id)
	var g := _gain_influence(world, rel / 8.0 * (1.0 + A.person_trait(p, "social", 0.5)))
	return {"ok": true, "influence_gain": g, "text": "%s unterstützt dich öffentlich. Einfluss +%.1f." % [A.person_name(world, person_id), g]}


func donate(world, amount: float) -> Dictionary:
	if party < 0:
		return {"ok": false, "reason": "Du musst Mitglied einer Partei sein."}
	if amount <= 0.0 or not world.ledger.transfer(A.player_account(world), int(parties[party].account), amount, "Parteispende"):
		return {"ok": false, "reason": "Spende nicht möglich."}
	var g := _gain_influence(world, sqrt(amount) / 4.0)
	return {"ok": true, "influence_gain": g, "text": "Spende von %.0f €: Einfluss +%.1f." % [amount, g]}


# ============================================================ Ämter / Kandidatur

func office_rank(o: String) -> int:
	return int(OFFICES.get(o, {"rank": -1}).rank) if o != "" else (0 if party >= 0 else -1)


## Bedingungen fuer ein Amt (nachvollziehbar fuer die Oberflaeche).
func office_requirements(world, o: String) -> Array:
	var def: Dictionary = OFFICES.get(o, {})
	if def.is_empty():
		return []
	var reqs := []
	var age := A.player_age(world)
	reqs.append({"label": "Mindestalter %d Jahre" % int(def.min_age), "met": age >= float(def.min_age), "value": age, "target": float(def.min_age)})
	if o != "member":
		reqs.append({"label": "Parteimitglied", "met": party >= 0, "value": 1.0 if party >= 0 else 0.0, "target": 1.0})
		reqs.append({"label": "Einfluss mindestens %d" % int(def.influence), "met": influence >= float(def.influence), "value": influence, "target": float(def.influence)})
	var rec := str(def.record)
	if rec != "any":
		var crime = A.field(world, "crime")
		var bad := false
		if crime != null:
			match rec:
				"misdemeanor":
					bad = crime.has_record("player", A.PLAYER_ID, "felony")
				"minor":
					bad = crime.has_record("player", A.PLAYER_ID, "misdemeanor")
				"none":
					bad = crime.has_record("player", A.PLAYER_ID, "")
		var lbl: String = {"misdemeanor": "Keine Vorstrafe wegen eines Verbrechens", "minor": "Keine Vorstrafe (außer Ordnungswidrigkeiten)", "none": "Keine Vorstrafen"}[rec]
		reqs.append({"label": lbl, "met": not bad, "value": 1.0 if bad else 0.0, "target": 0.0})
	var prior: Array = def.get("prior", [])
	if not prior.is_empty():
		var held := false
		for p in prior:
			if p in held_offices or p == office:
				held = true
		var alt := float(def.get("prior_influence", 999.0))
		var titles := []
		for p in prior:
			titles.append(OFFICES[p].title)
		var lbl2 := "Vorher im Amt: %s" % " oder ".join(titles)
		if alt < 999.0:
			lbl2 += " (oder Einfluss %d)" % int(alt)
		reqs.append({"label": lbl2, "met": held or influence >= alt, "value": 1.0 if held else 0.0, "target": 1.0})
	if o == "minister":
		var pres: Dictionary = holders.get("president", {})
		var same := party >= 0 and int(pres.get("party", -2)) == party
		reqs.append({"label": "Partei stellt den Präsidenten", "met": same or bool(pres.get("is_player", false)), "value": 1.0 if same else 0.0, "target": 1.0})
		reqs.append({"label": "Abgeordnetenmandat", "met": office == "mp", "value": 1.0 if office == "mp" else 0.0, "target": 1.0})
	if float(def.fee) > 0.0 and o != "member":
		var bal: float = world.ledger.balance(A.player_account(world))
		reqs.append({"label": "Kandidaturgebühr %.0f €" % float(def.fee), "met": bal >= float(def.fee), "value": bal, "target": float(def.fee)})
	return reqs


## Kandidatur fuer die naechste Wahl (bzw. Ernennung zum Minister).
## options.portfolio: Ressort fuer Minister.
func run_for(world, o: String, options := {}) -> Dictionary:
	if not OFFICES.has(o) or o == "member":
		return {"ok": false, "reason": "Dieses Amt gibt es nicht." if o != "member" else "Tritt dafür einer Partei bei (join_party)."}
	if office == o and o != "minister":
		return {"ok": false, "reason": "Du hast dieses Amt bereits; als Amtsinhaber trittst du automatisch wieder an."}
	if candidacies.has(o):
		return {"ok": false, "reason": "Du kandidierst bereits."}
	for r in office_requirements(world, o):
		if not r.met:
			return {"ok": false, "reason": "Bedingung nicht erfüllt: %s." % r.label}
	if o == "minister":
		var pf := str(options.get("portfolio", ""))
		if not PORTFOLIOS.has(pf):
			pf = _preferred_portfolio()
		_take_office(world, "minister", -1)
		portfolio = pf
		holders["minister:" + pf] = {"name": _player_name(world), "party": party, "is_player": true, "since": world.get_day(), "term_end": -1}
		_set_player_office_field(world)
		world.post_news("Politik", "%s wird Minister für %s." % [_player_name(world), PORTFOLIOS[pf]])
		return {"ok": true, "text": "Du bist jetzt Minister für %s." % PORTFOLIOS[pf], "portfolio": pf}
	var fee := float(OFFICES[o].fee)
	var gacc := A.gov_account(world)
	var paid := false
	if gacc >= 0:
		paid = world.ledger.transfer(A.player_account(world), gacc, fee, "Kandidaturgebühr")
	else:
		paid = world.ledger.burn(A.player_account(world), fee, "Kandidaturgebühr")
	if not paid:
		return {"ok": false, "reason": "Kandidaturgebühr nicht gedeckt."}
	candidacies[o] = true
	return {"ok": true, "text": "Kandidatur für %s eingereicht. Wahl an Tag %d." % [OFFICES[o].title, int(next_election.get(o, 0)) + 1]}


func _preferred_portfolio() -> String:
	if party < 0:
		return "finance"
	var ide: Dictionary = parties[party].ideology
	var best := "finance"
	var bv := -9.0
	var map := {"interior": "police", "education": "education", "health": "health", "transport": "transit", "social": "welfare"}
	for pf in map:
		if float(ide.get(map[pf], 0.0)) > bv:
			bv = float(ide.get(map[pf], 0.0))
			best = pf
	return best


func _player_name(world) -> String:
	var n = A.field(A.player(world), "name", "")
	if str(n) == "" and typeof(world.character) == TYPE_DICTIONARY:
		n = world.character.get("name", "")
	return str(n) if str(n) != "" else "Unabhängige Person"


func _take_office(world, o: String, end_day: int) -> void:
	if office != "" and office != o and not (office in held_offices):
		held_offices.append(office)
	if office == "minister" and portfolio != "":
		holders["minister:" + portfolio] = {"name": _random_name(), "party": int(holders.president.party), "is_player": false, "since": world.get_day(), "term_end": -1}
	if office == "mayor" and o != "mayor":
		holders["mayor"] = {"name": _random_name(), "party": int(holders.mayor.party), "is_player": false, "since": world.get_day(), "term_end": int(next_election.mayor)}
	if office == "president" and o != "president":
		holders["president"] = {"name": _random_name(), "party": int(holders.president.party), "is_player": false, "since": world.get_day(), "term_end": int(next_election.president)}
	office = o
	if not (o in held_offices):
		held_offices.append(o)
	office_since = world.get_day()
	term_end = end_day
	approval = 0.55
	low_approval_days = 0
	_set_player_office_field(world)


## Verlust des Amtes (Abwahl, Skandal, Ruecktritt).
func _resign(world, why: String) -> void:
	if office == "":
		return
	var old := office
	if old == "minister" and portfolio != "":
		holders["minister:" + portfolio] = {"name": _random_name(), "party": int(holders.president.party), "is_player": false, "since": world.get_day(), "term_end": -1}
	elif old in ["mayor", "president"] and bool(holders.get(old, {}).get("is_player", false)):
		holders[old] = {"name": _random_name(), "party": int(holders[old].party), "is_player": false, "since": world.get_day(), "term_end": int(next_election.get(old, 0))}
	office = ""
	portfolio = ""
	term_end = -1
	_set_player_office_field(world)
	world.post_news("Politik", "%s verliert das Amt %s: %s." % [_player_name(world), OFFICES[old].title, why])
	world.notify("Du hast dein Amt verloren: %s." % why, "danger")


func resign(world) -> Dictionary:
	if office == "":
		return {"ok": false, "reason": "Du hast kein Amt."}
	_resign(world, "Rücktritt")
	return {"ok": true, "text": "Du bist zurückgetreten."}


func _set_player_office_field(world) -> void:
	A.set_field(A.player(world), "political_office", office if office != "" else ("member" if party >= 0 else ""))


## Aktuelles Amt des Spielers ({} ohne Parteimitgliedschaft).
func player_office() -> Dictionary:
	if party < 0 and office == "":
		return {}
	var o := office if office != "" else "member"
	return {"office": o, "title": OFFICES[o].title + ((" für " + PORTFOLIOS[portfolio]) if o == "minister" and portfolio != "" else ""),
		"portfolio": portfolio, "since": office_since, "term_end": term_end, "approval": approval, "influence": influence,
		"party": party, "party_name": str(parties.get(party, {}).get("name", "")), "salary_per_day": float(OFFICES[o].salary),
		"powers": OFFICES[o].powers, "held_offices": held_offices.duplicate(), "candidacies": candidacies.keys()}


## Uebersicht aller Aemter fuer die Oberflaeche.
func offices() -> Array:
	var out := []
	for o in OFFICE_ORDER:
		var def: Dictionary = OFFICES[o]
		var e := {"id": o, "title": def.title, "term_days": int(def.term), "salary": float(def.salary), "seats": int(def.seats),
			"powers": def.powers, "next_election": int(next_election.get(o, -1)), "is_player": office == o or (o == "member" and party >= 0),
			"holders": []}
		match o:
			"council":
				e.holders = _seat_list(council_seats)
			"mp":
				e.holders = _seat_list(parliament_seats)
			"mayor", "president":
				var h: Dictionary = holders.get(o, {})
				e.holders = [{"name": h.get("name", ""), "party": int(h.get("party", -1)), "party_name": str(parties.get(int(h.get("party", -1)), {}).get("short", "")),
					"is_player": bool(h.get("is_player", false))}]
			"minister":
				for pf in PORTFOLIOS:
					var h2: Dictionary = holders.get("minister:" + pf, {})
					e.holders.append({"portfolio": pf, "portfolio_name": PORTFOLIOS[pf], "name": h2.get("name", ""),
						"party": int(h2.get("party", -1)), "is_player": bool(h2.get("is_player", false))})
		out.append(e)
	return out


func _seat_list(seats: Dictionary) -> Array:
	var out := []
	var ids := seats.keys()
	ids.sort()
	for pid in ids:
		out.append({"party": pid, "party_name": str(parties[pid].short), "seats": int(seats[pid])})
	return out


func party_list() -> Array:
	var out := []
	var ids := parties.keys()
	ids.sort()
	for pid in ids:
		var p: Dictionary = parties[pid]
		out.append({"id": pid, "short": p.short, "name": p.name, "popularity": float(p.popularity),
			"description": PARTIES[pid].description, "ideology": p.ideology.duplicate(), "color": PARTIES[pid].color,
			"council_seats": int(council_seats.get(pid, 0)), "parliament_seats": int(parliament_seats.get(pid, 0)),
			"is_player_party": pid == party})
	return out


func upcoming_elections(world) -> Array:
	var out := []
	for o in ELECTED:
		var d := int(next_election.get(o, -1))
		var can := true
		for r in office_requirements(world, o):
			can = can and bool(r.met)
		out.append({"office": o, "title": OFFICES[o].title, "day": d, "in_days": d - world.get_day(),
			"player_candidate": candidacies.has(o) or office == o, "can_run": can})
	out.sort_custom(func(a, b): return int(a.day) < int(b.day))
	return out


# ============================================================ Wahlen

func _run_election(world, o: String) -> Dictionary:
	var day: int = world.get_day()
	var sat := satisfaction(world)
	var player_runs: bool = (candidacies.has(o) or office == o) and party >= 0
	var crime = A.field(world, "crime")
	var scandal_malus := 0.0
	if crime != null and crime.has_record("player", A.PLAYER_ID, "misdemeanor"):
		scandal_malus = 0.6 if crime.has_record("player", A.PLAYER_ID, "felony") else 0.25
	var turnout := maxi(500, int(float(maxi(A.persons(world).size(), 2000)) * rng.randf_range(0.45, 0.68)))
	var result := {"office": o, "title": OFFICES[o].title, "day": day, "turnout": turnout, "candidates": [], "seats": {},
		"player_ran": player_runs, "player_elected": false, "winner": {}}
	if int(OFFICES[o].seats) > 1:
		# Listenwahl: Parteianteile -> Sitze; Spieler zieht ein, wenn sein Listenplatz reicht.
		var shares := {}
		var total := 0.0
		for pid in parties:
			var s := float(parties[pid].popularity) * 3.0 + _incumbent_bonus(pid, o, sat) + rng.randf_range(-0.15, 0.15)
			if player_runs and pid == party:
				s += influence / 300.0 - scandal_malus * 0.3
			shares[pid] = exp(maxf(-5.0, s) * 2.0)
			total += float(shares[pid])
		for pid in shares:
			shares[pid] = float(shares[pid]) / total
		var seats := _allocate_seats(int(OFFICES[o].seats), shares)
		if o == "council":
			council_seats = seats
		else:
			parliament_seats = seats
		result.seats = seats
		for pid in shares:
			result.candidates.append({"name": "Liste " + str(parties[pid].short), "party": pid, "party_name": parties[pid].name,
				"votes": int(round(float(shares[pid]) * turnout)), "share": float(shares[pid]), "seats": int(seats[pid]),
				"is_player": false, "elected": int(seats[pid]) > 0})
		var lead_party := _largest(seats)
		result.winner = {"name": "Liste " + str(parties[lead_party].short), "party": lead_party, "is_player": false}
		if player_runs:
			var threshold := 20.0 if o == "council" else 40.0
			var list_pos := 1 + maxi(0, int(ceil((threshold - influence) / 8.0)))
			result.player_list_position = list_pos
			result.player_elected = int(seats.get(party, 0)) >= list_pos
			result.candidates.append({"name": _player_name(world), "party": party, "party_name": parties[party].name,
				"votes": 0, "share": 0.0, "seats": 0, "is_player": true, "elected": result.player_elected, "list_position": list_pos})
	else:
		var cands := []
		for pid in parties:
			var is_pl: bool = player_runs and pid == party
			var infl := influence if is_pl else rng.randf_range(25.0, 70.0)
			var name := _player_name(world) if is_pl else (str(holders[o].name) if int(holders[o].party) == pid and not bool(holders[o].is_player) else _random_name())
			var s := float(parties[pid].popularity) * 3.0 + infl / 100.0 * 1.2 + _incumbent_bonus(pid, o, sat) + rng.randf_range(-0.2, 0.2)
			if is_pl:
				s -= scandal_malus
			cands.append({"name": name, "party": pid, "party_name": parties[pid].name, "score": s, "is_player": is_pl})
		var total2 := 0.0
		for c in cands:
			c.w = exp(float(c.score) * 2.0)
			total2 += float(c.w)
		var best := {}
		for c in cands:
			c.share = float(c.w) / total2
			c.votes = int(round(float(c.share) * turnout))
			c.erase("w")
			c.erase("score")
			if best.is_empty() or float(c.share) > float(best.share):
				best = c
		for c in cands:
			c.elected = c == best
		result.candidates = cands
		result.winner = {"name": best.name, "party": int(best.party), "is_player": bool(best.is_player)}
		result.player_elected = bool(best.is_player)
		var prev_party := int(holders[o].party)
		if not result.player_elected:
			holders[o] = {"name": best.name, "party": int(best.party), "is_player": false, "since": day, "term_end": day + int(OFFICES[o].term)}
		if o == "president" and int(best.party) != prev_party:
			_reshuffle_ministers(world, int(best.party))
	# Spielerergebnis
	var term := int(OFFICES[o].term)
	if result.player_elected:
		_take_office(world, o, day + term)
		if o in ["mayor", "president"]:
			holders[o] = {"name": _player_name(world), "party": party, "is_player": true, "since": day, "term_end": day + term}
			if o == "president":
				_reshuffle_ministers(world, party)
		world.notify("Gewählt: Du bist jetzt %s." % OFFICES[o].title, "success")
		_gain_influence(world, 10.0)
	elif office == o:
		_resign(world, "Abwahl")
	candidacies.erase(o)
	last_results[o] = result
	var wtxt := "%s (%s)" % [result.winner.get("name", ""), str(parties.get(int(result.winner.get("party", -1)), {}).get("short", ""))]
	world.post_news("Politik", "Wahl %s: %s vorn. Wahlbeteiligung %d Stimmen." % [OFFICES[o].title, wtxt, turnout])
	world.emit_event("election_finished", [result])
	return result


func _incumbent_bonus(pid: int, o: String, sat: Dictionary) -> float:
	var gov_party := int(holders.get("president", {}).get("party", -1))
	if o in ["council", "mayor"]:
		gov_party = int(holders.get("mayor", {}).get("party", -1))
	return (float(sat.overall) - 0.5) * 1.5 if pid == gov_party else 0.0


func _reshuffle_ministers(world, pres_party: int) -> void:
	for pf in PORTFOLIOS:
		var key: String = "minister:" + pf
		var h: Dictionary = holders.get(key, {})
		if bool(h.get("is_player", false)):
			if party != pres_party:
				_resign(world, "Regierungswechsel")
			else:
				continue
		holders[key] = {"name": _random_name(), "party": pres_party, "is_player": false, "since": world.get_day(), "term_end": -1}


## Fuer Tests/Werkzeuge: Wahl sofort durchfuehren.
func force_election(world, o: String) -> Dictionary:
	if not (o in ELECTED):
		return {}
	var r := _run_election(world, o)
	next_election[o] = world.get_day() + int(OFFICES[o].term)
	return r


# ============================================================ Befugnisse / Entscheidungen

## Darf der Spieler diesen Hebel direkt bedienen?
func can_decide(lever_id: String) -> bool:
	var l: Dictionary = LEVERS.get(lever_id, {})
	if l.is_empty():
		return false
	match office:
		"president":
			return true
		"minister":
			return str(l.portfolio) == portfolio
		"mayor":
			return str(l.scope) == "city"
	return false


## Aktionen fuer den Entscheidungs-Bildschirm. Jede Aktion fuehrt ueber
## apply_decision(world, id, value) zu einer echten Aenderung.
func available_actions(world) -> Array:
	var out := []
	for id in LEVERS:
		var l: Dictionary = LEVERS[id]
		var direct := can_decide(id)
		var motion: bool = office == "council" and str(l.scope) == "city"
		if not direct and not motion:
			continue
		var r := _range(world, id)
		out.append({"id": id if direct else "motion:" + id, "kind": "lever", "lever": id, "control": str(l.unit),
			"label": l.label if direct else "Antrag: " + str(l.label), "description": str(l.description) + ("" if direct else " Antrag im Stadtrat, Abstimmung nötig."),
			"current": current_value(world, id), "min": r[0], "max": r[1], "step": float(l.step), "unit": l.unit,
			"scope": l.scope, "effect_preview": effect_preview(world, id)})
	if party < 0:
		for pid in parties:
			out.append({"id": "join:%d" % pid, "kind": "action", "label": "Eintreten: %s" % parties[pid].name,
				"description": PARTIES[pid].description, "effect_preview": "Mitgliedsbeitrag %.0f €. Voraussetzung für jede Kandidatur." % float(OFFICES.member.fee)})
	else:
		out.append({"id": "campaign", "kind": "action", "label": "Wahlkampf (4 Stunden)", "description": "Wert = eingesetztes Geld (geht an die Parteikasse).",
			"effect_preview": "Einfluss steigt (Charisma und Geld helfen, ab hohem Einfluss abnehmend). Aktuell %.0f." % influence})
		out.append({"id": "speech", "kind": "action", "label": "Rede halten (2 Stunden)", "description": "Öffentlicher Auftritt.",
			"effect_preview": "Meist +3 bis +7 Einfluss, kann misslingen."})
		out.append({"id": "donate", "kind": "action", "label": "An die Partei spenden", "description": "Wert = Betrag in Euro.",
			"effect_preview": "Einfluss steigt mit der Wurzel des Betrags."})
		for o in ["council", "mayor", "mp", "minister", "president"]:
			if office == o or candidacies.has(o):
				continue
			var reqs := office_requirements(world, o)
			var ok := true
			var missing := []
			for rq in reqs:
				if not rq.met:
					ok = false
					missing.append(rq.label)
			out.append({"id": "run:" + o, "kind": "action", "label": ("Ministeramt anstreben" if o == "minister" else "Kandidieren: " + str(OFFICES[o].title)),
				"description": OFFICES[o].powers, "available": ok,
				"effect_preview": ("Ernennung sofort, Ressort nach Parteiprogramm." if o == "minister" else "Gebühr %.0f €, Wahl an Tag %d." % [float(OFFICES[o].fee), int(next_election.get(o, 0)) + 1]) if ok else "Fehlt: " + ", ".join(missing)})
	return out


func _range(world, lever_id: String) -> Array:
	var l: Dictionary = LEVERS[lever_id]
	var mn := float(l.min)
	var mx := float(l.max)
	var ref := float(refs.get(lever_id, 0.0))
	if str(l.kind) == "rate" and ref > 0.0:
		mx = maxf(mx, ref * 3.0)
	if lever_id == "infrastructure_program":
		mx = minf(mx, maxf(0.0, world.ledger.balance(A.gov_account(world)) * 0.5)) if A.gov_account(world) >= 0 else 0.0
	return [mn, mx]


## Verstaendliche Vorschau der Wirkung eines Hebels.
func effect_preview(world, lever_id: String) -> String:
	var l: Dictionary = LEVERS[lever_id]
	var cur := current_value(world, lever_id)
	var crime = A.field(world, "crime")
	match lever_id:
		"income_tax", "sales_tax", "business_tax", "property_tax":
			var base := _tax_base(world, lever_id)
			return "Aktuell %.1f %%. +1 Prozentpunkt bringt geschätzt %.0f € pro Tag mehr Einnahmen, belastet aber Haushalte bzw. Geschäfte entsprechend und senkt die Zustimmung." % [cur * 100.0, base * 0.01]
		"budget_police":
			var eff: float = crime.police_effectiveness(world) if crime != null else 1.0
			var per_day: float = crime.expected_npc_crimes_per_day(world) if crime != null else 0.0
			return "Anteil %.0f %%. Mehr Geld erhöht die Polizeiwirkung (jetzt %.2f): weniger NPC-Straftaten (Prognose %.1f pro Tag), höhere Festnahmequote. Fehlt dann in anderen Bereichen." % [cur * 100.0, eff, per_day]
		"police_strictness":
			return "Faktor %.2f. Härtere Gesetze: höhere Strafen und Festnahmequote, weniger Straftaten, aber Bürger fühlen sich gegängelt." % cur
		"budget_education":
			return "Anteil %.0f %%. Wirkt langfristig: Bildungsindex %.2f steigt langsam, bessere Abschlüsse und Löhne." % [cur * 100.0, float(indices.education)]
		"budget_health":
			return "Anteil %.0f %%. Bessere Versorgung, weniger Krankheitstage." % [cur * 100.0]
		"budget_transit":
			return "Anteil %.0f %%. Dichterer Bustakt, bessere Anbindung." % [cur * 100.0]
		"budget_infrastructure":
			return "Anteil %.0f %%. Bessere Straßen, Infrastrukturindex %.2f." % [cur * 100.0, float(indices.infrastructure)]
		"budget_welfare":
			return "Anteil %.0f %%. Hilft Haushalten mit niedrigem Einkommen, senkt Armutskriminalität." % [cur * 100.0]
		"budget_housing":
			return "Anteil %.0f %%. Mehr geförderte Wohnungen und Mietzuschüsse." % [cur * 100.0]
		"bus_fare":
			return "Aktuell %.2f €. Günstiger: mehr Fahrgäste, weniger Einnahmen." % cur
		"welfare_level", "unemployment_benefit":
			return "Faktor %.2f. Höher: mehr Geld für einkommensschwache NPCs, weniger Kriminalität, höhere Staatsausgaben." % cur
		"rent_cap":
			return "Derzeit %s. Bremst Mietsteigerungen, dämpft aber den Wohnungsbau." % ("aktiv" if cur >= 0.5 else "aus")
		"infrastructure_program":
			return "Einmalige Ausgabe aus dem Staatskonto. Hebt den Infrastrukturindex (jetzt %.2f) für einige Tage." % float(indices.infrastructure)
	return str(l.description)


func _tax_base(world, lever_id: String) -> float:
	var n := float(A.persons(world).size())
	if n <= 0.0:
		n = 2000.0
	var wage: float = world.rules.f("wage_level") if world.rules != null else 1.0
	match lever_id:
		"income_tax":
			return n * 0.5 * 8.0 * 16.0 * wage
		"sales_tax":
			return n * 22.0
		"business_tax":
			var eco = A.field(world, "economy")
			var nb := float(A.field(eco, "businesses", {}).size()) if eco != null else 80.0
			return maxf(1.0, nb) * 250.0
		"property_tax":
			return n * 0.4 * 300.0 / 7.0
	return 0.0


## Fuehrt eine Entscheidung aus. decision_id: Hebel-ID, "motion:<hebel>",
## "join:<partei>", "run:<amt>", "campaign", "speech", "donate".
## Rueckgabe {ok, text, effects}; effects: {before, after, derived: {...}}.
func apply_decision(world, decision_id: String, value = 0.0) -> Dictionary:
	if decision_id.begins_with("join:"):
		var r1 := join_party(world, int(decision_id.substr(5)))
		return {"ok": r1.ok, "text": r1.get("text", r1.get("reason", "")), "effects": {}}
	if decision_id.begins_with("run:"):
		var r2 := run_for(world, decision_id.substr(4))
		return {"ok": r2.ok, "text": r2.get("text", r2.get("reason", "")), "effects": {}}
	if decision_id == "campaign":
		var r3 := campaign(world, 4.0, float(value))
		return {"ok": r3.ok, "text": r3.get("text", r3.get("reason", "")), "effects": {"influence": influence}}
	if decision_id == "speech":
		var r4 := give_speech(world)
		return {"ok": r4.ok, "text": r4.get("text", r4.get("reason", "")), "effects": {"influence": influence}}
	if decision_id == "donate":
		var r5 := donate(world, float(value))
		return {"ok": r5.ok, "text": r5.get("text", r5.get("reason", "")), "effects": {"influence": influence}}
	if decision_id.begins_with("motion:"):
		return propose_motion(world, decision_id.substr(7), float(value))
	if not LEVERS.has(decision_id):
		return {"ok": false, "text": "Unbekannte Entscheidung.", "effects": {}}
	if not can_decide(decision_id):
		return {"ok": false, "text": "Dazu fehlt dir die Befugnis (%s)." % _power_hint(decision_id), "effects": {}}
	if A.gov(world) == null and LEVERS[decision_id].kind != "policy":
		return {"ok": false, "text": "Keine Regierung vorhanden.", "effects": {}}
	var by: String = OFFICES[office].title
	return _apply_lever(world, decision_id, float(value), by, true)


func _power_hint(lever_id: String) -> String:
	var l: Dictionary = LEVERS[lever_id]
	var who := ["Präsident"]
	if str(l.scope) == "city":
		who.append("Bürgermeister")
	who.append("Minister für " + PORTFOLIOS[l.portfolio])
	return "zuständig: " + ", ".join(who)


func _measure(world) -> Dictionary:
	var crime = A.field(world, "crime")
	var m := {}
	if crime != null:
		m.police_effectiveness = crime.police_effectiveness(world)
		m.strictness = crime.strictness(world)
		m.npc_crimes_per_day = crime.expected_npc_crimes_per_day(world)
		m.arrest_chance_wanted3 = crime.arrest_chance_for_level(world, 3)
		m.solve_chance = crime.solve_chance(world)
	var sat := satisfaction(world)
	m.satisfaction = float(sat.overall)
	m.gov_account = world.ledger.balance(A.gov_account(world)) if A.gov_account(world) >= 0 else 0.0
	for k in ["police_effectiveness", "education_quality", "health_quality", "bus_frequency", "bus_fare", "welfare_per_day",
			"unemployment_benefit", "rent_subsidy", "infrastructure_quality"]:
		var v := A.gov_modifier(world, k, -1.0)
		if v >= 0.0:
			m["gov_" + k] = v
	return m


func _apply_lever(world, lever_id: String, value: float, by: String, by_player: bool) -> Dictionary:
	var l: Dictionary = LEVERS[lever_id]
	var r := _range(world, lever_id)
	value = clampf(value, float(r[0]), float(r[1]))
	if str(l.unit) == "toggle":
		value = 1.0 if value >= 0.5 else 0.0
	var before := current_value(world, lever_id)
	var m0 := _measure(world)
	match str(l.kind):
		"rate":
			_write_rate(world, str(l.key), value)
		"budget":
			_write_budget(world, str(l.key), value)
		"policy":
			_write_policy(world, str(l.key), value)
		"program":
			if value <= 0.0:
				return {"ok": false, "text": "Betrag muss größer als 0 sein.", "effects": {}}
			var gacc := A.gov_account(world)
			# Bauauftraege an auswaertige Firmen: Geld verlaesst die Stadt
			if gacc < 0 or not world.ledger.burn(gacc, value, "Infrastrukturprogramm"):
				return {"ok": false, "text": "Das Staatskonto deckt die Ausgabe nicht.", "effects": {}}
			infrastructure_bonus += value / 100000.0
			indices.infrastructure = float(indices.infrastructure) + value / 200000.0
			_write_policy(world, "infrastructure_program", infrastructure_bonus)
	var after := current_value(world, lever_id) if str(l.kind) != "program" else value
	var m1 := _measure(world)
	_publish_indices(world)
	# Zustimmung reagiert auf spuerbare Entscheidungen
	if by_player:
		var ref := maxf(0.0001, absf(float(refs.get(lever_id, before))) if float(refs.get(lever_id, 0.0)) != 0.0 else maxf(0.01, absf(before)))
		var rel := (after - before) / ref
		match str(l.issue):
			"tax":
				approval -= rel * 0.15
			"welfare", "education", "health", "transit", "housing":
				approval += rel * 0.05
			"police":
				approval += rel * 0.02
			"infrastructure":
				approval += 0.02 if after > before else 0.0
		if lever_id == "bus_fare":
			approval -= rel * 0.08
		approval = clampf(approval, 0.0, 1.0)
	var rec := {"day": world.get_day(), "id": lever_id, "label": l.label, "before": before, "after": after, "by": by, "player": by_player}
	decisions.push_front(rec)
	if decisions.size() > 60:
		decisions.pop_back()
	world.emit_event("policy_changed", [lever_id, float(after)])
	var txt := "%s: %s -> %s." % [l.label, _fmt(lever_id, before), _fmt(lever_id, after)]
	world.post_news("Politik", "%s (%s)" % [txt, by])
	var derived := {}
	for k in m1:
		if m0.has(k) and absf(float(m1[k]) - float(m0[k])) > 0.00001:
			derived[k] = [float(m0[k]), float(m1[k])]
	return {"ok": true, "text": txt, "effects": {"before": before, "after": after, "derived": derived, "measures_before": m0, "measures_after": m1}}


func _fmt(lever_id: String, v: float) -> String:
	var unit: String = LEVERS[lever_id].unit
	match unit:
		"%":
			return "%.1f %%" % (v * 100.0)
		"share":
			return "%.0f %%" % (v * 100.0)
		"€":
			return "%.2f €" % v
		"toggle":
			return "an" if v >= 0.5 else "aus"
	return "%.2f" % v


## Antrag im Stadtrat (nur Stadtrat; Bürgermeister/Präsident entscheiden direkt).
func propose_motion(world, lever_id: String, value: float) -> Dictionary:
	if office != "council":
		return {"ok": false, "text": "Anträge stellen nur Mitglieder des Stadtrats.", "effects": {}}
	if not LEVERS.has(lever_id) or str(LEVERS[lever_id].scope) != "city":
		return {"ok": false, "text": "Der Stadtrat ist dafür nicht zuständig.", "effects": {}}
	var day: int = world.get_day()
	if last_motion_day == day:
		return {"ok": false, "text": "Du hast heute bereits einen Antrag gestellt.", "effects": {}}
	last_motion_day = day
	var l: Dictionary = LEVERS[lever_id]
	var cur := current_value(world, lever_id)
	var dir := signf(value - cur)
	var issue := str(l.issue)
	var yes := 0
	var no := 0
	for pid in council_seats:
		var n := int(council_seats[pid])
		var ide := float(parties[pid].ideology.get(issue, 0.0))
		if lever_id == "bus_fare":
			ide = -float(parties[pid].ideology.get("transit", 0.0))
		var stance := ide * dir
		for i in n:
			var p_yes := 0.5 + stance * 0.45
			if pid == party:
				p_yes = maxf(p_yes, 0.85)
			p_yes += (influence - 40.0) / 300.0
			if rng.randf() < clampf(p_yes, 0.05, 0.95):
				yes += 1
			else:
				no += 1
	var passed := yes > no
	var res := {"ok": true, "passed": passed, "yes": yes, "no": no, "effects": {}}
	if passed:
		var r := _apply_lever(world, lever_id, value, "Stadtrat", true)
		res.effects = r.effects
		res.text = "Antrag angenommen (%d:%d). %s" % [yes, no, r.text]
		_gain_influence(world, 3.0)
	else:
		res.text = "Antrag abgelehnt (%d:%d)." % [yes, no]
		_gain_influence(world, -1.0)
	return res


# ============================================================ Simulation

func on_day(world, day: int) -> void:
	var sat := satisfaction(world)
	last_satisfaction = sat
	_update_indices(world)
	_drift_popularity(sat)
	# Gehalt
	if office != "" and float(OFFICES[office].salary) > 0.0:
		var gacc := A.gov_account(world)
		if gacc >= 0:
			world.ledger.transfer_partial(gacc, A.player_account(world), float(OFFICES[office].salary), "Amtsbezüge " + str(OFFICES[office].title))
	# Einfluss
	if office != "":
		_gain_influence(world, 0.3 * office_rank(office))
	elif influence > 20.0 and day - last_activity_day > 2:
		influence = maxf(0.0, influence - 0.5)
	# Zustimmung, Abwahl, Skandale
	if office != "":
		approval = clampf(approval + (float(sat.overall) - approval) * 0.1, 0.0, 1.0)
		if approval < 0.15 and office in ["mayor", "president", "minister"]:
			low_approval_days += 1
			if low_approval_days >= 3:
				_resign(world, "Misstrauensvotum nach anhaltend geringer Zustimmung")
		else:
			low_approval_days = 0
		_check_scandal(world)
	# Wahlen
	for o in ELECTED:
		if day >= int(next_election.get(o, 999999)):
			_run_election(world, o)
			next_election[o] = day + int(OFFICES[o].term)
	# NPC-Regierung
	if day % 2 == 0:
		_npc_govern(world, sat)


func _update_indices(world) -> void:
	# Bildung wirkt langfristig: Index naehert sich langsam dem Budgetverhaeltnis.
	indices.education = float(indices.education) + (_ratio(world, "budget_education") - float(indices.education)) * 0.05
	indices.health = float(indices.health) + (_ratio(world, "budget_health") - float(indices.health)) * 0.15
	var infra_target := _ratio(world, "budget_infrastructure") + infrastructure_bonus
	indices.infrastructure = float(indices.infrastructure) + (infra_target - float(indices.infrastructure)) * 0.1
	infrastructure_bonus *= 0.95
	_publish_indices(world)


func _publish_indices(world) -> void:
	var g = A.gov(world)
	var p = A.field(g, "policies", null)
	if typeof(p) == TYPE_DICTIONARY:
		p["education_index"] = float(indices.education)
		p["infrastructure_index"] = float(indices.infrastructure)
		p["health_index"] = float(indices.health)
	policies["education_index"] = float(indices.education)
	policies["infrastructure_index"] = float(indices.infrastructure)


func _drift_popularity(sat: Dictionary) -> void:
	var issues := {"police": 1.0 - float(sat.crime), "welfare": 1.0 - float(sat.unemployment) * 0.5 - float(sat.welfare) * 0.5,
		"tax": -(1.0 - float(sat.taxes)), "education": 1.0 - float(sat.education), "transit": 1.0 - float(sat.transit),
		"housing": 1.0 - float(sat.housing), "health": 1.0 - float(sat.health)}
	var target := {}
	var total := 0.0
	for pid in parties:
		var ide: Dictionary = parties[pid].ideology
		var t := float(PARTIES[pid].popularity)
		for k in issues:
			t += float(ide.get(k, 0.0)) * (float(issues[k]) - 0.5) * 0.08
		if int(holders.get("president", {}).get("party", -1)) == pid:
			t += (float(sat.overall) - 0.5) * 0.1
		if pid == party:
			t += influence / 2000.0
		target[pid] = maxf(0.03, t)
		total += float(target[pid])
	for pid in parties:
		var tv := float(target[pid]) / total
		parties[pid].popularity = float(parties[pid].popularity) + (tv - float(parties[pid].popularity)) * 0.15


func _check_scandal(world) -> void:
	var crime = A.field(world, "crime")
	if crime == null:
		return
	var n: int = crime.record.size()
	if n > revealed_records and rng.randf() < 0.25:
		revealed_records = n
		var felony: bool = crime.has_record("player", A.PLAYER_ID, "felony")
		approval = maxf(0.0, approval - 0.25)
		influence = maxf(0.0, influence - 20.0)
		var txt := "Skandal: %s ist vorbestraft." % _player_name(world)
		scandals.append({"day": world.get_day(), "text": txt})
		world.post_news("Politik", txt)
		if felony and office_rank(office) >= OFFICES.mayor.rank:
			_resign(world, "Amtsenthebung nach Bekanntwerden einer Straftat")
		return
	if crime.gangs.player_gang >= 0 and rng.randf() < 0.05:
		approval = maxf(0.0, approval - 0.3)
		influence = maxf(0.0, influence - 25.0)
		var txt2 := "Skandal: %s soll Verbindungen zu %s haben." % [_player_name(world), crime.gangs.gangs[crime.gangs.player_gang].name]
		scandals.append({"day": world.get_day(), "text": txt2})
		world.post_news("Politik", txt2)


## NPC-Amtsinhaber passen Politik an Zufriedenheit und Parteiprogramm an.
func _npc_govern(world, sat: Dictionary) -> void:
	if A.gov(world) == null:
		return
	if office != "president":
		var pres_party := int(holders.get("president", {}).get("party", -1))
		_npc_adjust(world, sat, pres_party, ["budget_police", "budget_welfare", "budget_education", "budget_health", "income_tax"], "Regierung")
	if office != "mayor" and office != "president":
		var mayor_party := int(holders.get("mayor", {}).get("party", -1))
		_npc_adjust(world, sat, mayor_party, ["budget_transit", "budget_housing", "budget_infrastructure"], "Bürgermeisteramt")


func _npc_adjust(world, sat: Dictionary, pid: int, levers: Array, by: String) -> void:
	if not parties.has(pid):
		return
	var ide: Dictionary = parties[pid].ideology
	var issue_score := {"budget_police": float(sat.crime), "budget_welfare": (float(sat.welfare) + float(sat.unemployment)) * 0.5,
		"budget_education": float(sat.education), "budget_health": float(sat.health), "income_tax": float(sat.taxes),
		"budget_transit": float(sat.transit), "budget_housing": float(sat.housing), "budget_infrastructure": float(sat.transit)}
	var worst := ""
	var ws := 2.0
	for lid in levers:
		var issue: String = LEVERS[lid].issue
		var s := float(issue_score.get(lid, 0.5)) - float(ide.get(issue, 0.0)) * 0.1
		if s < ws:
			ws = s
			worst = lid
	if worst == "" or ws > 0.45:
		return
	var cur := current_value(world, worst)
	var ref := float(refs.get(worst, cur))
	var step := float(LEVERS[worst].step)
	var target := cur
	if worst == "income_tax":
		target = maxf(ref * 0.7, cur - step)   # Unzufriedenheit mit Steuern -> senken
	else:
		target = minf(ref * 1.5 if ref > 0.0 else cur + step, cur + step)
	if absf(target - cur) < 0.0001:
		return
	_apply_lever(world, worst, target, by, false)


# ============================================================ Speichern

func to_save() -> Dictionary:
	var pstate := {}
	for pid in parties:
		pstate[pid] = {"popularity": float(parties[pid].popularity), "account": int(parties[pid].account),
			"leader_name": str(parties[pid].leader_name)}
	return {"rng_state": rng.state, "parties": pstate, "council_seats": council_seats, "parliament_seats": parliament_seats,
		"holders": holders, "next_election": next_election, "last_results": last_results, "policies": policies,
		"refs": refs, "indices": indices, "infrastructure_bonus": infrastructure_bonus, "decisions": decisions,
		"party": party, "influence": influence, "office": office, "portfolio": portfolio, "office_since": office_since,
		"term_end": term_end, "approval": approval, "low_approval_days": low_approval_days, "held_offices": held_offices,
		"candidacies": candidacies, "endorsements": endorsements, "scandals": scandals, "revealed_records": revealed_records,
		"last_motion_day": last_motion_day, "last_activity_day": last_activity_day}


func from_save(world, d: Dictionary) -> void:
	rng.seed = hash("politics:%d" % int(world.seed))
	if d.has("rng_state"):
		rng.state = int(d.rng_state)
	parties.clear()
	var ps: Dictionary = A.int_keys(d.get("parties", {}))
	for pid in PARTIES:
		var def: Dictionary = PARTIES[pid]
		var st: Dictionary = ps.get(pid, {})
		parties[pid] = {"id": pid, "short": def.short, "name": def.name, "popularity": float(st.get("popularity", def.popularity)),
			"ideology": def.ideology.duplicate(), "account": int(st.get("account", -1)), "leader_name": str(st.get("leader_name", ""))}
	council_seats = A.int_keys(d.get("council_seats", {}))
	parliament_seats = A.int_keys(d.get("parliament_seats", {}))
	holders = d.get("holders", {})
	next_election = d.get("next_election", FIRST_ELECTION.duplicate())
	last_results = d.get("last_results", {})
	policies = d.get("policies", {})
	refs = d.get("refs", {})
	indices = d.get("indices", {"education": 1.0, "infrastructure": 1.0, "health": 1.0})
	infrastructure_bonus = float(d.get("infrastructure_bonus", 0.0))
	decisions = d.get("decisions", [])
	party = int(d.get("party", -1))
	influence = float(d.get("influence", 0.0))
	office = str(d.get("office", ""))
	portfolio = str(d.get("portfolio", ""))
	office_since = int(d.get("office_since", -1))
	term_end = int(d.get("term_end", -1))
	approval = float(d.get("approval", 0.5))
	low_approval_days = int(d.get("low_approval_days", 0))
	held_offices = d.get("held_offices", [])
	candidacies = d.get("candidacies", {})
	endorsements = A.int_keys(d.get("endorsements", {}))
	scandals = d.get("scandals", [])
	revealed_records = int(d.get("revealed_records", 0))
	last_motion_day = int(d.get("last_motion_day", -1))
	last_activity_day = int(d.get("last_activity_day", 0))
