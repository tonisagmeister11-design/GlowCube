class_name Occupations
extends RefCounted
## Berufe mit Anforderungen, Arbeitszeiten, Lohn und Karrierestufen.
##
## OCCUPATIONS[id] = {id, name, category, min_age, education, skills: {skill: min},
##   wage (Euro pro Stunde, Stufe 0, vor wage_level), shift: [start_hour, hours],
##   shifts: [[start, hours], ...] (Schichtvarianten), days: [0..6] (Standard),
##   rotating (Schichtbetrieb: 5 von 7 Tagen, versetzt), workplaces: [Gebaeudearten,
##   Geschaeftsarten oder Firmenbranchen], ranks: [{title, wage_mult, min_perf}],
##   criminal_record_ok, illegal, public}
## Illegale Berufe (illegal = true) werden nicht ueber den Arbeitsmarkt
## vergeben; sie sind fuer das Kriminalitaetsmodul markiert.

const EDUCATION_LEVELS := ["none", "school", "vocational", "bachelor", "master", "doctor"]
const EDUCATION_LABELS := {"none": "Kein Abschluss", "school": "Schulabschluss", "vocational": "Berufsausbildung",
	"bachelor": "Bachelor", "master": "Master", "doctor": "Promotion"}
const WEEKDAYS := [0, 1, 2, 3, 4]
const ALLDAYS := [0, 1, 2, 3, 4, 5, 6]

## [id, name, category, min_age, education, skills, wage, shifts, rotating, workplaces, ranks, crim_ok, illegal]
const _ROWS := [
	["cashier", "Kassierer/in", "Handel", 16, "none", {}, 13.0, [[7, 8], [14, 8]], true,
		["supermarket", "grocery", "convenience", "gas_station", "cinema", "fastfood"],
		[["Kassierer/in", 1.0, 0], ["Erfahrene/r Kassierer/in", 1.12, 55], ["Kassenleitung", 1.3, 72]], true, false],
	["salesperson", "Verkäufer/in", "Handel", 16, "school", {"charisma": 10}, 14.0, [[9, 8], [11, 8]], true,
		["clothing", "electronics", "furniture", "hardware", "luxury", "secondhand", "bakery", "butcher", "produce", "market",
		 "flea_market", "night_market", "luxury_market", "car_dealer", "pharmacy", "grocery", "supermarket"],
		[["Verkäufer/in", 1.0, 0], ["Fachverkäufer/in", 1.15, 55], ["Filialleitung", 1.45, 75]], true, false],
	["warehouse_worker", "Lagerarbeiter/in", "Logistik", 18, "none", {"fitness": 10}, 13.5, [[6, 8], [14, 8]], false,
		["wholesale", "logistics", "supermarket", "hardware", "furniture", "manufacturing", "food_processing", "electronics_mfg"],
		[["Lagerhelfer/in", 1.0, 0], ["Lagerist/in", 1.15, 55], ["Lagerleitung", 1.4, 75]], true, false],
	["delivery_driver", "Auslieferungsfahrer/in", "Logistik", 18, "none", {"driving": 30}, 14.5, [[7, 8]], false,
		["wholesale", "logistics", "furniture"],
		[["Fahrer/in", 1.0, 0], ["Tourenplaner/in", 1.2, 70]], true, false],
	["bus_driver", "Busfahrer/in", "Verkehr", 21, "vocational", {"driving": 45}, 18.5, [[5, 8], [13, 8]], true,
		["bus_depot"], [["Busfahrer/in", 1.0, 0], ["Lehrfahrer/in", 1.12, 60], ["Verkehrsmeister/in", 1.3, 78]], false, false],
	["taxi_driver", "Taxifahrer/in", "Verkehr", 21, "none", {"driving": 40}, 14.5, [[7, 9], [16, 9]], true,
		["taxi"], [["Taxifahrer/in", 1.0, 0], ["Schichtleitung Taxi", 1.25, 70]], true, false],
	["mechanic", "Kfz-Mechaniker/in", "Handwerk", 18, "vocational", {"handiwork": 35}, 19.0, [[7, 8]], false,
		["car_dealer", "bus_depot", "gas_station", "manufacturing", "logistics", "taxi"],
		[["Kfz-Geselle/Gesellin", 1.0, 0], ["Kfz-Meister/in", 1.3, 70]], true, false],
	["craftsman", "Handwerker/in", "Handwerk", 18, "vocational", {"handiwork": 40}, 20.0, [[7, 8]], false,
		["construction", "hardware", "housing", "property", "city_hall"],
		[["Geselle/Gesellin", 1.0, 0], ["Vorarbeiter/in", 1.15, 60], ["Meister/in", 1.35, 78]], true, false],
	["cook", "Koch/Köchin", "Gastronomie", 16, "vocational", {"cooking": 35}, 15.5, [[10, 8], [15, 8]], true,
		["restaurant", "fastfood", "cafe", "hospital", "school", "night_market", "food_processing", "university", "shelter"],
		[["Jungkoch/-köchin", 1.0, 0], ["Koch/Köchin", 1.15, 55], ["Küchenchef/in", 1.45, 78]], true, false],
	["waiter", "Servicekraft", "Gastronomie", 16, "none", {"charisma": 15}, 13.0, [[11, 8], [16, 8], [7, 8]], true,
		["restaurant", "cafe", "bar"],
		[["Servicekraft", 1.0, 0], ["Oberkellner/in", 1.2, 65], ["Restaurantleitung", 1.4, 80]], true, false],
	["bartender", "Barkeeper/in", "Gastronomie", 18, "none", {"charisma": 20, "cooking": 10}, 13.5, [[18, 8]], true,
		["bar"], [["Barkeeper/in", 1.0, 0], ["Barchef/in", 1.3, 72]], true, false],
	["cleaner", "Reinigungskraft", "Dienstleistung", 16, "none", {}, 13.0, [[5, 7], [17, 7]], false,
		["it", "finance", "insurance", "consulting", "law", "media", "architecture", "housing", "manufacturing",
		 "food_processing", "chemicals", "electronics_mfg", "school", "hospital", "university", "city_hall",
		 "parliament", "station", "bus_depot"],
		[["Reinigungskraft", 1.0, 0], ["Objektleitung Reinigung", 1.3, 70]], true, false],
	["construction_worker", "Bauarbeiter/in", "Bau", 18, "none", {"fitness": 25}, 16.0, [[7, 8]], false,
		["construction"], [["Bauhelfer/in", 1.0, 0], ["Facharbeiter/in Bau", 1.15, 55], ["Polier/in", 1.4, 78]], true, false],
	["office_clerk", "Bürokaufmann/-frau", "Büro", 18, "vocational", {"logic": 15}, 18.0, [[8, 8]], false,
		["it", "finance", "insurance", "consulting", "law", "media", "architecture", "housing", "property", "manufacturing",
		 "food_processing", "chemicals", "electronics_mfg", "wholesale", "logistics", "construction", "taxi",
		 "city_hall", "parliament", "university", "hospital", "school", "bus_depot", "real_estate"],
		[["Sachbearbeiter/in", 1.0, 0], ["Senior-Sachbearbeiter/in", 1.18, 60], ["Teamleitung", 1.4, 78]], true, false],
	["accountant", "Buchhalter/in", "Büro", 20, "vocational", {"logic": 30, "business": 20}, 23.0, [[8, 8]], false,
		["finance", "insurance", "consulting", "law", "housing", "property"],
		[["Buchhalter/in", 1.0, 0], ["Bilanzbuchhalter/in", 1.2, 65], ["Leitung Rechnungswesen", 1.5, 82]], true, false],
	["it_specialist", "IT-Fachkraft", "IT", 20, "bachelor", {"programming": 40, "logic": 30}, 30.0, [[9, 8]], false,
		["it", "media", "electronics_mfg", "finance"],
		[["Junior-Entwickler/in", 1.0, 0], ["Entwickler/in", 1.25, 55], ["Senior-Entwickler/in", 1.55, 72], ["IT-Leitung", 1.9, 86]], true, false],
	["teacher", "Lehrer/in", "Bildung", 24, "master", {"teaching": 30}, 28.0, [[7, 8]], false,
		["school"], [["Lehrer/in", 1.0, 0], ["Oberstudienrat/-rätin", 1.15, 60], ["Schulleitung", 1.4, 80]], false, false],
	["professor", "Dozent/in", "Bildung", 28, "doctor", {"teaching": 30, "logic": 50}, 38.0, [[9, 8]], false,
		["university"], [["Dozent/in", 1.0, 0], ["Professor/in", 1.35, 70], ["Dekan/in", 1.6, 88]], false, false],
	["scientist", "Wissenschaftler/in", "Forschung", 26, "doctor", {"logic": 55}, 36.0, [[9, 8]], false,
		["university", "chemicals"], [["Wissenschaftliche/r Mitarbeiter/in", 1.0, 0], ["Projektleitung Forschung", 1.35, 72]], true, false],
	["doctor", "Arzt/Ärztin", "Gesundheit", 25, "master", {"medicine": 55}, 46.0, [[8, 9], [20, 10]], true,
		["hospital", "clinic"], [["Assistenzarzt/-ärztin", 1.0, 0], ["Facharzt/-ärztin", 1.25, 60], ["Oberarzt/-ärztin", 1.55, 75], ["Chefarzt/-ärztin", 2.0, 88]], false, false],
	["nurse", "Pflegefachkraft", "Gesundheit", 18, "vocational", {"medicine": 30}, 22.0, [[6, 8], [14, 8], [22, 8]], true,
		["hospital", "clinic"], [["Pflegefachkraft", 1.0, 0], ["Stationsleitung", 1.25, 70], ["Pflegedienstleitung", 1.45, 84]], true, false],
	["caregiver", "Altenpfleger/in", "Gesundheit", 18, "vocational", {"medicine": 15}, 17.5, [[6, 8], [14, 8]], true,
		["hospital"], [["Altenpfleger/in", 1.0, 0], ["Wohnbereichsleitung", 1.25, 70]], true, false],
	["pharmacist", "Apotheker/in", "Gesundheit", 24, "master", {"medicine": 45}, 32.0, [[8, 8]], true,
		["pharmacy"], [["Apotheker/in", 1.0, 0], ["Filialleitung Apotheke", 1.25, 70]], false, false],
	["firefighter", "Feuerwehrmann/-frau", "Sicherheit", 18, "vocational", {"fitness": 40}, 21.0, [[7, 10], [19, 10]], true,
		["fire_station"], [["Feuerwehrmann/-frau", 1.0, 0], ["Oberbrandmeister/in", 1.15, 60], ["Brandmeister/in", 1.3, 75], ["Wachleitung", 1.5, 88]], false, false],
	["police_officer", "Polizist/in", "Sicherheit", 19, "vocational", {"fitness": 35, "law": 15}, 22.0, [[6, 8], [14, 8], [22, 8]], true,
		["police"], [["Polizeimeister/in", 1.0, 0], ["Polizeiobermeister/in", 1.12, 55], ["Polizeihauptmeister/in", 1.25, 70], ["Kommissar/in", 1.45, 82], ["Polizeirat/-rätin", 1.75, 92]], false, false],
	["security_guard", "Sicherheitskraft", "Sicherheit", 18, "none", {"fitness": 20}, 14.5, [[6, 10], [18, 10]], true,
		["finance", "insurance", "property", "manufacturing", "chemicals", "wholesale", "station", "hospital", "parliament",
		 "university", "luxury", "bank"],
		[["Wachmann/-frau", 1.0, 0], ["Objektschutzleitung", 1.3, 72]], false, false],
	["entrepreneur", "Unternehmer/in", "Selbstständig", 18, "none", {"business": 15}, 20.0, [[8, 9]], true,
		["owner"], [["Inhaber/in", 1.0, 0], ["Unternehmer/in", 1.3, 70], ["Geschäftsführer/in", 1.6, 85]], true, false],
	["manager", "Manager/in", "Management", 25, "bachelor", {"business": 40, "charisma": 30}, 42.0, [[8, 9]], false,
		["it", "finance", "insurance", "consulting", "media", "housing", "property", "manufacturing", "food_processing",
		 "wholesale", "supermarket"],
		[["Abteilungsleitung", 1.0, 0], ["Bereichsleitung", 1.3, 65], ["Direktor/in", 1.7, 80], ["Vorstand", 2.4, 92]], true, false],
	["lawyer", "Rechtsanwalt/-anwältin", "Recht", 25, "master", {"law": 55}, 45.0, [[9, 9]], false,
		["law", "insurance", "city_hall"], [["Associate", 1.0, 0], ["Rechtsanwalt/-anwältin", 1.3, 60], ["Partner/in", 1.9, 82]], false, false],
	["journalist", "Journalist/in", "Medien", 20, "bachelor", {"writing": 35, "charisma": 20}, 22.0, [[10, 8]], true,
		["media"], [["Volontär/in", 1.0, 0], ["Redakteur/in", 1.3, 55], ["Ressortleitung", 1.6, 72], ["Chefredaktion", 2.0, 88]], true, false],
	["politician", "Politiker/in", "Politik", 25, "school", {"charisma": 50, "law": 20}, 38.0, [[9, 9]], false,
		["city_hall", "parliament"], [["Stadtrat/-rätin", 1.0, 0], ["Dezernent/in", 1.4, 70], ["Fraktionsvorsitz", 1.7, 85]], false, false],
	["civil_servant", "Verwaltungsangestellte/r", "Verwaltung", 18, "vocational", {"logic": 15}, 19.0, [[8, 8]], false,
		["city_hall", "parliament", "job_center", "station"],
		[["Verwaltungsfachangestellte/r", 1.0, 0], ["Amtsrat/-rätin", 1.25, 65], ["Amtsleitung", 1.5, 82]], false, false],
	["factory_worker", "Produktionsmitarbeiter/in", "Industrie", 18, "none", {"handiwork": 10}, 15.5, [[6, 8], [14, 8], [22, 8]], true,
		["manufacturing", "food_processing", "chemicals", "electronics_mfg"],
		[["Produktionshelfer/in", 1.0, 0], ["Maschinenführer/in", 1.15, 55], ["Schichtleitung", 1.35, 75]], true, false],
	["engineer", "Ingenieur/in", "Technik", 23, "bachelor", {"logic": 45, "handiwork": 20}, 33.0, [[8, 8]], false,
		["manufacturing", "chemicals", "electronics_mfg", "architecture", "construction", "food_processing"],
		[["Ingenieur/in", 1.0, 0], ["Projektingenieur/in", 1.25, 60], ["Chefingenieur/in", 1.6, 82]], true, false],
	["architect", "Architekt/in", "Technik", 24, "master", {"creativity": 40, "logic": 30}, 31.0, [[9, 8]], false,
		["architecture"], [["Architekt/in", 1.0, 0], ["Projektarchitekt/in", 1.3, 65], ["Büroinhaber/in", 1.7, 85]], true, false],
	["baker", "Bäcker/in", "Handwerk", 16, "vocational", {"cooking": 25}, 14.5, [[4, 8]], true,
		["bakery"], [["Bäckergeselle/-gesellin", 1.0, 0], ["Bäckermeister/in", 1.3, 72]], true, false],
	["butcher_trade", "Fleischer/in", "Handwerk", 16, "vocational", {"cooking": 20, "handiwork": 15}, 15.0, [[6, 8]], true,
		["butcher"], [["Fleischergeselle/-gesellin", 1.0, 0], ["Fleischermeister/in", 1.3, 72]], true, false],
	["fitness_trainer", "Fitnesstrainer/in", "Sport", 18, "vocational", {"fitness": 50}, 16.0, [[7, 8], [14, 8]], true,
		["gym"], [["Trainer/in", 1.0, 0], ["Studioleitung", 1.3, 72]], true, false],
	["bank_clerk", "Bankkaufmann/-frau", "Finanzen", 18, "vocational", {"business": 20, "logic": 20}, 22.0, [[9, 8]], false,
		["bank", "finance"], [["Bankkaufmann/-frau", 1.0, 0], ["Kundenberater/in", 1.2, 60], ["Filialdirektor/in", 1.55, 80]], false, false],
	["real_estate_agent", "Immobilienmakler/in", "Immobilien", 20, "vocational", {"charisma": 30, "business": 20}, 20.0, [[9, 8]], false,
		["real_estate", "housing"], [["Makler/in", 1.0, 0], ["Senior-Makler/in", 1.3, 70]], true, false],
	["social_worker", "Sozialarbeiter/in", "Soziales", 22, "bachelor", {"charisma": 25}, 23.0, [[8, 8]], false,
		["shelter", "job_center", "school"], [["Sozialarbeiter/in", 1.0, 0], ["Teamleitung Soziales", 1.25, 70]], false, false],
	["gardener", "Gärtner/in", "Handwerk", 18, "vocational", {"handiwork": 15}, 16.0, [[7, 8]], false,
		["city_hall", "produce"], [["Gärtner/in", 1.0, 0], ["Gärtnermeister/in", 1.25, 70]], true, false],
	["train_attendant", "Zugbegleiter/in", "Verkehr", 18, "vocational", {"charisma": 15}, 17.0, [[6, 8], [14, 8]], true,
		["station"], [["Zugbegleiter/in", 1.0, 0], ["Zugchef/in", 1.25, 70]], true, false],
	["mini_job", "Aushilfe", "Aushilfe", 15, "none", {}, 12.5, [[16, 4], [10, 4]], true,
		["supermarket", "fastfood", "cafe", "cinema", "convenience", "bakery", "gas_station"],
		[["Aushilfe", 1.0, 0]], true, false],
	# --- Illegal (fuer das Kriminalitaetsmodul markiert, nicht ueber den Arbeitsmarkt)
	["drug_dealer", "Dealer/in", "Illegal", 16, "none", {}, 0.0, [[20, 6]], true, [], [["Straßendealer/in", 1.0, 0], ["Großdealer/in", 1.5, 70]], true, true],
	["fence", "Hehler/in", "Illegal", 18, "none", {"business": 20}, 0.0, [[18, 6]], true, [], [["Hehler/in", 1.0, 0]], true, true],
	["smuggler", "Schmuggler/in", "Illegal", 18, "none", {"driving": 30}, 0.0, [[22, 6]], true, [], [["Schmuggler/in", 1.0, 0]], true, true],
	["pickpocket", "Taschendieb/in", "Illegal", 14, "none", {}, 0.0, [[12, 6]], true, [], [["Taschendieb/in", 1.0, 0]], true, true],
]

## Oeffentliche Gebaeude: Belegschaft [[occ, anzahl]] vor Skalierung.
const PUBLIC_STAFF := {
	"school": [["teacher", 6], ["office_clerk", 1], ["cleaner", 1], ["cook", 1], ["social_worker", 0.5]],
	"university": [["professor", 7], ["scientist", 3], ["office_clerk", 3], ["cleaner", 2], ["cook", 1], ["security_guard", 1]],
	"hospital": [["doctor", 7], ["nurse", 14], ["caregiver", 3], ["cleaner", 2], ["cook", 1], ["office_clerk", 1], ["security_guard", 1]],
	"clinic": [["doctor", 1], ["nurse", 2]],
	"police": [["police_officer", 10], ["civil_servant", 1]],
	"fire_station": [["firefighter", 8]],
	"city_hall": [["civil_servant", 6], ["politician", 3], ["office_clerk", 2], ["lawyer", 1], ["cleaner", 1], ["gardener", 2], ["craftsman", 1]],
	"parliament": [["politician", 4], ["civil_servant", 2], ["security_guard", 1], ["cleaner", 1]],
	"bus_depot": [["bus_driver", 10], ["mechanic", 2], ["office_clerk", 1], ["cleaner", 1]],
	"station": [["train_attendant", 3], ["security_guard", 2], ["civil_servant", 1], ["cleaner", 1]],
	"shelter": [["social_worker", 2], ["cook", 1]],
}

## Geschaefte: Reihenfolge der Stellen (Stelle 0 = Inhaber bei privaten Eigentuemern).
const BUSINESS_STAFF := {
	"supermarket": ["cashier", "warehouse_worker", "cashier", "salesperson", "manager", "cashier", "mini_job"],
	"grocery": ["salesperson", "cashier", "mini_job"],
	"produce": ["salesperson", "gardener"],
	"bakery": ["baker", "salesperson", "salesperson", "mini_job"],
	"butcher": ["butcher_trade", "salesperson", "butcher_trade"],
	"convenience": ["cashier", "mini_job"],
	"market": ["salesperson", "salesperson", "salesperson", "warehouse_worker", "salesperson"],
	"night_market": ["cook", "salesperson", "cook", "salesperson"],
	"flea_market": ["salesperson", "salesperson"],
	"luxury_market": ["salesperson", "salesperson", "security_guard", "salesperson"],
	"restaurant": ["cook", "waiter", "waiter", "cook", "waiter", "cook"],
	"fastfood": ["cook", "cashier", "mini_job", "cook"],
	"cafe": ["waiter", "cook", "waiter", "mini_job"],
	"bar": ["bartender", "waiter", "bartender", "security_guard"],
	"gym": ["fitness_trainer", "fitness_trainer", "cleaner"],
	"cinema": ["cashier", "cashier", "mini_job", "security_guard", "cleaner"],
	"clothing": ["salesperson", "salesperson", "salesperson", "salesperson"],
	"electronics": ["salesperson", "salesperson", "salesperson", "delivery_driver"],
	"furniture": ["salesperson", "delivery_driver", "salesperson", "warehouse_worker"],
	"hardware": ["salesperson", "warehouse_worker", "salesperson", "craftsman"],
	"pharmacy": ["pharmacist", "salesperson", "salesperson"],
	"luxury": ["salesperson", "salesperson", "security_guard"],
	"secondhand": ["salesperson", "salesperson"],
	"car_dealer": ["salesperson", "mechanic", "salesperson", "mechanic", "office_clerk"],
	"gas_station": ["cashier", "cashier", "mechanic"],
	"real_estate": ["real_estate_agent", "real_estate_agent", "office_clerk"],
	"bank": ["bank_clerk", "bank_clerk", "bank_clerk", "security_guard", "bank_clerk"],
	"job_center": ["civil_servant", "civil_servant", "social_worker", "civil_servant", "office_clerk", "civil_servant"],
}

static var OCCUPATIONS: Dictionary = _build()


static func _build() -> Dictionary:
	var out := {}
	for r in _ROWS:
		var ranks := []
		for rk in r[10]:
			ranks.append({"title": rk[0], "wage_mult": float(rk[1]), "min_perf": float(rk[2])})
		var shifts: Array = r[7]
		out[r[0]] = {"id": r[0], "name": r[1], "category": r[2], "min_age": float(r[3]), "education": r[4],
			"skills": r[5], "wage": float(r[6]), "shift": shifts[0], "shifts": shifts,
			"days": ALLDAYS if r[8] else WEEKDAYS, "rotating": bool(r[8]), "workplaces": r[9], "ranks": ranks,
			"criminal_record_ok": bool(r[11]), "illegal": bool(r[12]),
			"public": false}
	return out


static func get_occ(id: String) -> Dictionary:
	return OCCUPATIONS.get(id, {})


static func edu_rank(level: String) -> int:
	return maxi(0, EDUCATION_LEVELS.find(level))


static func edu_label(level: String) -> String:
	return EDUCATION_LABELS.get(level, level)


static func title(occ_id: String, rank: int) -> String:
	var o: Dictionary = OCCUPATIONS.get(occ_id, {})
	if o.is_empty():
		return ""
	var ranks: Array = o.ranks
	return ranks[clampi(rank, 0, ranks.size() - 1)].title


static func rank_mult(occ_id: String, rank: int) -> float:
	var o: Dictionary = OCCUPATIONS.get(occ_id, {})
	if o.is_empty():
		return 1.0
	var ranks: Array = o.ranks
	return float(ranks[clampi(rank, 0, ranks.size() - 1)].wage_mult)


static func legal_ids() -> Array:
	var out := []
	for id in OCCUPATIONS:
		if not OCCUPATIONS[id].illegal:
			out.append(id)
	return out


static func illegal_ids() -> Array:
	var out := []
	for id in OCCUPATIONS:
		if OCCUPATIONS[id].illegal:
			out.append(id)
	return out


## Anforderungen als lesbarer Text.
static func requirements_text(occ_id: String) -> String:
	var o: Dictionary = OCCUPATIONS.get(occ_id, {})
	if o.is_empty():
		return ""
	var parts := ["ab %d Jahren" % int(o.min_age)]
	if o.education != "none":
		parts.append(edu_label(o.education))
	for k in o.skills:
		parts.append("%s %d" % [Skills.label(k), int(o.skills[k])])
	if not o.criminal_record_ok:
		parts.append("ohne Vorstrafen")
	return ", ".join(parts)
