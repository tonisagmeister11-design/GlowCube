class_name Company
extends RefCounted
## Firmen in Buero-, Hochhaus-, Fabrik- und Lagergebaeuden: Branchen,
## Namen, Exportleistung und Tagesabschluss. Firmen bringen ueber Exporte
## Geld in die Stadt (mint) und kaufen Vorleistungen von ausserhalb (burn).
##
## company = {id, building, name, sector, account, employees: [person ids],
##   owner: {kind: "household"|"external"|"player", id}, productivity, status,
##   revenue_today, exports_today, wages_today, costs_today, profit_hist: [],
##   district, zone, pos, size_weight, cash_target}

## export_mult: Exporterloes je Euro Bruttolohn; input_share: Anteil der
## Exporterloese fuer Vorleistungen (Import); local_owner: Anteil der Firmen
## mit Eigentuemer in der Stadt.
const SECTORS := {
	"it": {"label": "IT-Dienstleistungen", "kinds": ["office", "tower"], "export_mult": 1.75, "input_share": 0.12,
		"staff": [["it_specialist", 6], ["office_clerk", 2], ["manager", 1], ["cleaner", 0.5], ["security_guard", 0.3]],
		"names": ["Byteforge", "Datenwerk", "Nordlicht Software", "Codehafen", "Pixelbrücke", "Logikraum"], "suffix": ["GmbH", "AG"]},
	"finance": {"label": "Finanzdienstleistungen", "kinds": ["tower", "office"], "export_mult": 1.8, "input_share": 0.10,
		"staff": [["bank_clerk", 4], ["accountant", 2], ["office_clerk", 2], ["manager", 1.2], ["security_guard", 0.5], ["cleaner", 0.5]],
		"names": ["Hanseatica Capital", "Flussbank Invest", "Goldufer Vermögen", "Kontorhaus Finanz", "Merkur Partners"], "suffix": ["AG", "KG"]},
	"insurance": {"label": "Versicherungen", "kinds": ["tower", "office"], "export_mult": 1.7, "input_share": 0.10,
		"staff": [["office_clerk", 5], ["accountant", 1.5], ["manager", 1], ["lawyer", 0.5], ["cleaner", 0.5]],
		"names": ["Havenschutz Versicherung", "Anker Assekuranz", "Leuchtturm Versicherungen", "Brückenbund"], "suffix": ["AG", "VVaG"]},
	"consulting": {"label": "Unternehmensberatung", "kinds": ["office", "tower"], "export_mult": 1.75, "input_share": 0.10,
		"staff": [["manager", 2], ["office_clerk", 2], ["accountant", 1], ["it_specialist", 1], ["cleaner", 0.4]],
		"names": ["Weitblick Consulting", "Kompass Beratung", "Strategiehaus", "Brandt & Okafor"], "suffix": ["GmbH", "Partners"]},
	"law": {"label": "Kanzlei", "kinds": ["office", "tower"], "export_mult": 1.7, "input_share": 0.08,
		"staff": [["lawyer", 4], ["office_clerk", 3], ["accountant", 0.5], ["cleaner", 0.4]],
		"names": ["Kanzlei Falk & Demir", "Richter Lindqvist Rechtsanwälte", "Justitia Partner", "Kanzlei am Markt"], "suffix": ["", "PartG"]},
	"media": {"label": "Medien & Verlag", "kinds": ["office", "tower"], "export_mult": 1.6, "input_share": 0.15,
		"staff": [["journalist", 5], ["office_clerk", 2], ["it_specialist", 1], ["manager", 0.6], ["cleaner", 0.4]],
		"names": ["Havenbrücker Tagblatt", "Stadtfunk Medien", "Flusskurier Verlag", "Morgenpost Havenbrück", "Kanal 7"], "suffix": ["GmbH", ""]},
	"architecture": {"label": "Architektur & Planung", "kinds": ["office"], "export_mult": 1.65, "input_share": 0.12,
		"staff": [["architect", 4], ["engineer", 2], ["office_clerk", 1], ["cleaner", 0.3]],
		"names": ["Raumwerk Architekten", "Linie & Licht", "Bauatelier Nord", "Planquadrat"], "suffix": ["GmbH", ""]},
	"housing": {"label": "Wohnungsbaugesellschaft", "kinds": ["office"], "export_mult": 0.0, "input_share": 0.0,
		"staff": [["office_clerk", 4], ["real_estate_agent", 2], ["accountant", 1], ["manager", 0.5], ["craftsman", 2], ["cleaner", 1]],
		"names": ["Havenbrücker Wohnbau", "Heimstatt Wohnen", "Kanalblick Immobilien"], "suffix": ["AG", "eG"]},
	"property": {"label": "Gewerbeimmobilien", "kinds": ["office"], "export_mult": 0.0, "input_share": 0.0,
		"staff": [["office_clerk", 3], ["accountant", 1], ["manager", 0.5], ["craftsman", 1], ["security_guard", 1]],
		"names": ["Havenbrücker Gewerbebau", "Ladenzeile Immobilien"], "suffix": ["AG"]},
	"manufacturing": {"label": "Maschinenbau", "kinds": ["factory"], "export_mult": 1.75, "input_share": 0.32,
		"staff": [["factory_worker", 8], ["engineer", 1.5], ["mechanic", 1], ["warehouse_worker", 1.5], ["office_clerk", 1], ["manager", 0.5], ["security_guard", 0.4], ["cleaner", 0.5]],
		"names": ["Eisenwerk Havenbrück", "Kranbau Nord", "Präzision Meyer", "Turbinenwerk Ost"], "suffix": ["GmbH", "AG"]},
	"food_processing": {"label": "Lebensmittelindustrie", "kinds": ["factory"], "export_mult": 1.65, "input_share": 0.38,
		"staff": [["factory_worker", 8], ["cook", 1], ["warehouse_worker", 2], ["engineer", 0.5], ["office_clerk", 1], ["cleaner", 0.6]],
		"names": ["Havenbräu Brauerei", "Weidegold Molkerei", "Knusperland Snacks", "Mühle am Fluss"], "suffix": ["GmbH", "KG"]},
	"chemicals": {"label": "Chemie & Pharma", "kinds": ["factory"], "export_mult": 1.8, "input_share": 0.35,
		"staff": [["factory_worker", 6], ["scientist", 1.5], ["engineer", 1], ["office_clerk", 1], ["security_guard", 0.5], ["cleaner", 0.5]],
		"names": ["Vitalis Pharma", "Grippex Labor", "Chemiewerk Unterhafen"], "suffix": ["AG"]},
	"electronics_mfg": {"label": "Elektrotechnik", "kinds": ["factory"], "export_mult": 1.75, "input_share": 0.36,
		"staff": [["factory_worker", 7], ["engineer", 2], ["it_specialist", 0.5], ["warehouse_worker", 1], ["office_clerk", 1], ["cleaner", 0.5]],
		"names": ["Klarbild Elektronik", "Voltwerk", "Schaltkreis Nord"], "suffix": ["GmbH", "AG"]},
	"wholesale": {"label": "Großhandel", "kinds": ["warehouse"], "export_mult": 0.35, "input_share": 0.2,
		"staff": [["warehouse_worker", 6], ["delivery_driver", 3], ["office_clerk", 1], ["manager", 0.4], ["security_guard", 0.4]],
		"names": ["Großmarkt Havenbrück", "Hanse Großhandel", "FrischLogistik", "Kontor Warenhandel", "Nordsee Großhandel"], "suffix": ["GmbH", "KG"]},
	"logistics": {"label": "Logistik & Spedition", "kinds": ["warehouse"], "export_mult": 1.6, "input_share": 0.25,
		"staff": [["delivery_driver", 5], ["warehouse_worker", 4], ["office_clerk", 1], ["mechanic", 0.5]],
		"names": ["Hafenkran Spedition", "Blitzkurier", "Transnord Logistik", "Paketwerk"], "suffix": ["GmbH", ""]},
	"construction": {"label": "Bauunternehmen", "kinds": ["warehouse"], "export_mult": 1.45, "input_share": 0.30,
		"staff": [["construction_worker", 7], ["craftsman", 3], ["engineer", 0.5], ["office_clerk", 0.6]],
		"names": ["Bau Hartmann", "Fundament & Co.", "Kranich Bau", "Stein auf Stein"], "suffix": ["GmbH", "KG"]},
	"taxi": {"label": "Taxi & Fahrdienst", "kinds": ["warehouse"], "export_mult": 1.45, "input_share": 0.25,
		"staff": [["taxi_driver", 8], ["mechanic", 0.6], ["office_clerk", 0.5]],
		"names": ["Taxi Havenbrück", "Stadttaxi Zentral", "Funktaxi Nord"], "suffix": ["eG", "GmbH"]},
}

## Pflichtbranchen (werden zuerst vergeben, falls Gebaeude vorhanden).
const REQUIRED := [["office", "housing"], ["office", "property"], ["warehouse", "wholesale"], ["warehouse", "wholesale"],
	["warehouse", "construction"], ["warehouse", "logistics"], ["warehouse", "taxi"], ["office", "media"], ["office", "housing"]]

## Gewichtete Branchenwahl je Gebaeudeart.
const KIND_SECTORS := {
	"office": {"it": 3, "consulting": 2, "law": 2, "media": 1, "architecture": 1, "insurance": 1, "finance": 1},
	"tower": {"finance": 3, "insurance": 2, "it": 2, "consulting": 2, "law": 1, "media": 1},
	"factory": {"manufacturing": 3, "food_processing": 2, "chemicals": 1, "electronics_mfg": 2},
	"warehouse": {"wholesale": 3, "logistics": 3, "construction": 2, "taxi": 1},
}


## Groessengewicht (proportional zur Belegschaft) aus der Gebaeudeflaeche.
static func size_weight(b: Dictionary) -> float:
	var area: float = b.footprint.size.x * b.footprint.size.y
	match b.kind:
		"office": return area * float(b.floors) / 260.0
		"tower": return area * float(b.floors) / 320.0
		"factory": return area * float(b.floors) / 120.0
		"warehouse": return area / 140.0
	return area / 200.0


static func make_name(rng: RandomNumberGenerator, sector: String, used: Dictionary) -> String:
	var s: Dictionary = SECTORS[sector]
	var names: Array = s.names
	for i in 12:
		var n: String = names[rng.randi() % names.size()]
		var suf: String = s.suffix[rng.randi() % s.suffix.size()]
		var full := n if suf == "" else "%s %s" % [n, suf]
		if not used.has(full):
			used[full] = true
			return full
	var fb := "%s %d" % [names[rng.randi() % names.size()], used.size() + 1]
	used[fb] = true
	return fb


## Konjunktur je Branche: langsame Welle (+-10 %) plus Rauschen.
static func cycle(sector: String, day: int, rng: RandomNumberGenerator) -> float:
	var phase := float(hash(sector) % 100) / 100.0 * TAU
	return 1.0 + 0.10 * sin(float(day) / 9.0 + phase) + rng.randf_range(-0.04, 0.04)


## Tagesabschluss einer Firma.
static func end_of_day(world, eco, c: Dictionary, day: int) -> void:
	var L: Ledger = world.ledger
	var gov = world.government
	var sec: Dictionary = SECTORS.get(c.sector, SECTORS["it"])
	var wages: float = c.wages_today
	var exports := 0.0
	if wages > 0.0 and float(sec.export_mult) > 0.0:
		exports = wages * float(sec.export_mult) * float(c.productivity) * cycle(c.sector, day, eco.rng)
		L.mint(c.account, exports, "Exporterlös")
		eco.stat("exports", exports)
	var inputs := exports * float(sec.input_share)
	if inputs > 0.0 and L.burn(c.account, minf(inputs, L.balance(c.account)), "Vorleistungen (Import)"):
		eco.stat("imports", minf(inputs, L.balance(c.account) + inputs))
	var util: float = 10.0 + float(c.size_weight) * 1.2
	if gov:
		var paid := L.transfer_partial(c.account, gov.dept_accounts.get("infrastructure", gov.account), util, "Strom, Wasser, Abfall")
		gov.record_revenue("utilities", paid)
	var profit: float = float(c.revenue_today) + exports - inputs - wages - util - float(c.costs_today)
	if profit > 0.0 and gov:
		gov.collect(world, c.account, profit * gov.rate("business_tax"), "business_tax")
	c.exports_today = exports
	c.profit_hist.append(snappedf(profit, 0.01))
	if c.profit_hist.size() > 14:
		c.profit_hist.pop_front()
	# Lohnkosten-Schaetzung (gleitend) fuer Liquiditaetsziel
	if wages > 0.0:
		c.wage_avg = lerpf(float(c.get("wage_avg", wages)), wages, 0.2)
	c.cash_target = float(c.get("wage_avg", 500.0)) * 12.0 + 2000.0
	var bal := L.balance(c.account)
	# Liquiditaetsengpass: Stellen abbauen
	if bal < float(c.get("wage_avg", 0.0)) * 1.5 and wages > 0.0:
		c.distress = int(c.get("distress", 0)) + 1
		if c.distress >= 3 and world.jobs:
			world.jobs.lay_off_one(world, "company", c.id)
			c.distress = 0
	else:
		c.distress = maxi(0, int(c.get("distress", 0)) - 1)
	# Woechentliche Ausschuettung
	if day % 7 == c.id % 7:
		var excess := bal - float(c.cash_target) * 1.4
		if excess > 0.0:
			var div := excess * 0.6
			var owner_acc: int = eco.owner_account(world, c.owner)
			var local_part := div * 0.5 if owner_acc >= 0 else 0.0
			if local_part > 0.0:
				L.transfer(c.account, owner_acc, local_part, "Gewinnausschüttung %s" % c.name)
				eco.stat("dividends", local_part)
			var ext := div - local_part
			if ext > 0.0 and L.burn(c.account, ext, "Ausschüttung an auswärtige Anteilseigner"):
				eco.stat("dividends_external", ext)
	c.revenue_today = 0.0
	c.wages_today = 0.0
	c.costs_today = 0.0
