class_name VehicleCatalog
extends RefCounted
## Fahrzeugtypen (Abmessungen, Fahrdynamik der KI) und kaufbare Modelle
## (fiktive Marken). Grundlage fuer Verkehr, Spielerfahrzeuge und den
## Autohaendler.
##
## TYPES: Bauform -> physikalische Werte. `kind` gruppiert Regeln der KI:
##   car | taxi | van | truck | bus | emergency | moto | bike
## MODELS: kaufbare Modelle -> {name, brand, type, category, price, top_speed (km/h),
##   accel (0-100 km/h in s), seats, fuel (l/100 km, 0 = Muskelkraft), colors}

const TYPES := {
	"sedan":      {"length": 4.70, "width": 1.84, "height": 1.46, "accel": 2.6, "decel": 3.6, "vmax": 46.0, "kind": "car", "label": "Limousine"},
	"hatchback":  {"length": 4.10, "width": 1.78, "height": 1.50, "accel": 2.4, "decel": 3.6, "vmax": 42.0, "kind": "car", "label": "Kleinwagen"},
	"suv":        {"length": 4.80, "width": 1.94, "height": 1.74, "accel": 2.5, "decel": 3.5, "vmax": 48.0, "kind": "car", "label": "SUV"},
	"sports":     {"length": 4.50, "width": 1.96, "height": 1.24, "accel": 4.2, "decel": 4.5, "vmax": 80.0, "kind": "car", "label": "Sportwagen"},
	"luxury":     {"length": 5.20, "width": 1.96, "height": 1.48, "accel": 3.2, "decel": 4.0, "vmax": 70.0, "kind": "car", "label": "Oberklasse"},
	"taxi":       {"length": 4.70, "width": 1.84, "height": 1.46, "accel": 2.6, "decel": 3.6, "vmax": 46.0, "kind": "taxi", "label": "Taxi"},
	"van":        {"length": 5.60, "width": 2.04, "height": 2.55, "accel": 2.0, "decel": 3.2, "vmax": 40.0, "kind": "van", "label": "Lieferwagen"},
	"truck":      {"length": 8.60, "width": 2.50, "height": 3.40, "accel": 1.3, "decel": 2.8, "vmax": 25.0, "kind": "truck", "label": "Lkw"},
	"bus":        {"length": 12.0, "width": 2.55, "height": 3.10, "accel": 1.3, "decel": 2.8, "vmax": 20.0, "kind": "bus", "label": "Stadtbus"},
	"police":     {"length": 4.80, "width": 1.86, "height": 1.50, "accel": 3.0, "decel": 4.0, "vmax": 55.0, "kind": "emergency", "label": "Polizei"},
	"ambulance":  {"length": 6.20, "width": 2.12, "height": 2.85, "accel": 2.2, "decel": 3.4, "vmax": 40.0, "kind": "emergency", "label": "Rettungswagen"},
	"fire":       {"length": 9.20, "width": 2.50, "height": 3.30, "accel": 1.6, "decel": 3.0, "vmax": 30.0, "kind": "emergency", "label": "Feuerwehr"},
	"motorcycle": {"length": 2.15, "width": 0.80, "height": 1.35, "accel": 4.0, "decel": 4.5, "vmax": 55.0, "kind": "moto", "label": "Motorrad"},
	"bicycle":    {"length": 1.80, "width": 0.65, "height": 1.70, "accel": 1.0, "decel": 2.5, "vmax": 7.0, "kind": "bike", "label": "Fahrrad"},
}

## Gewichte fuer zufaelligen Stadtverkehr (Busse und Einsatzfahrzeuge
## werden gesondert erzeugt).
const AI_WEIGHTS := {
	"sedan": 26.0, "hatchback": 24.0, "suv": 14.0, "sports": 3.0, "luxury": 5.0,
	"taxi": 7.0, "van": 8.0, "truck": 4.0, "motorcycle": 4.0, "bicycle": 5.0,
}

## Lackfarben fuer Privatfahrzeuge (realistische Verteilung: viel Grau/Schwarz/Weiss).
const PAINT := [
	Color(0.92, 0.92, 0.90), Color(0.08, 0.08, 0.09), Color(0.55, 0.57, 0.60),
	Color(0.32, 0.34, 0.37), Color(0.72, 0.73, 0.75), Color(0.60, 0.08, 0.09),
	Color(0.10, 0.20, 0.42), Color(0.20, 0.33, 0.55), Color(0.16, 0.30, 0.20),
	Color(0.80, 0.55, 0.20), Color(0.45, 0.30, 0.22), Color(0.85, 0.82, 0.70),
	Color(0.95, 0.75, 0.10), Color(0.35, 0.10, 0.30),
]
const PAINT_WEIGHTS := [18, 16, 12, 12, 10, 6, 6, 5, 3, 2, 3, 3, 1, 2]

const TAXI_COLOR := Color(0.96, 0.86, 0.55)       # elfenbeinfarben
const POLICE_COLOR := Color(0.93, 0.94, 0.96)
const AMBULANCE_COLOR := Color(0.96, 0.96, 0.94)
const FIRE_COLOR := Color(0.78, 0.07, 0.06)

## Kaufbare Modelle. Preise in Euro, Verbrauch l/100 km.
const MODELS := {
	"brava_lumo":     {"name": "Brava Lumo", "brand": "Brava", "type": "hatchback", "category": "Kleinwagen",
		"price": 14900.0, "top_speed": 165.0, "accel": 12.1, "seats": 4, "fuel": 5.4},
	"brava_lumo_e":   {"name": "Brava Lumo Stromer", "brand": "Brava", "type": "hatchback", "category": "Kleinwagen",
		"price": 23900.0, "top_speed": 150.0, "accel": 9.6, "seats": 4, "fuel": 0.0, "electric": true},
	"kessler_arda":   {"name": "Kessler Arda", "brand": "Kessler", "type": "sedan", "category": "Limousine",
		"price": 27400.0, "top_speed": 205.0, "accel": 9.2, "seats": 5, "fuel": 6.3},
	"kessler_arda_t": {"name": "Kessler Arda Touring", "brand": "Kessler", "type": "sedan", "category": "Limousine",
		"price": 31200.0, "top_speed": 215.0, "accel": 8.4, "seats": 5, "fuel": 6.8},
	"nordwerk_tavik": {"name": "Nordwerk Tavik", "brand": "Nordwerk", "type": "suv", "category": "SUV",
		"price": 38900.0, "top_speed": 200.0, "accel": 8.9, "seats": 5, "fuel": 8.1},
	"nordwerk_fjell": {"name": "Nordwerk Fjell XL", "brand": "Nordwerk", "type": "suv", "category": "SUV",
		"price": 52500.0, "top_speed": 215.0, "accel": 7.4, "seats": 7, "fuel": 9.3},
	"velmar_strale":  {"name": "Velmar Strale GT", "brand": "Velmar", "type": "sports", "category": "Sportwagen",
		"price": 89000.0, "top_speed": 295.0, "accel": 4.1, "seats": 2, "fuel": 11.8},
	"velmar_falco":   {"name": "Velmar Falco R", "brand": "Velmar", "type": "sports", "category": "Sportwagen",
		"price": 164000.0, "top_speed": 320.0, "accel": 3.2, "seats": 2, "fuel": 14.5},
	"auren_regent":   {"name": "Auren Regent", "brand": "Auren", "type": "luxury", "category": "Oberklasse",
		"price": 112000.0, "top_speed": 250.0, "accel": 5.4, "seats": 5, "fuel": 10.2},
	"elbmotor_kurier": {"name": "Elbmotor Kurier", "brand": "Elbmotor", "type": "van", "category": "Transporter",
		"price": 34600.0, "top_speed": 160.0, "accel": 14.5, "seats": 3, "fuel": 8.9},
	"ruhrkraft_m8":   {"name": "Ruhrkraft M8", "brand": "Ruhrkraft", "type": "motorcycle", "category": "Motorrad",
		"price": 9800.0, "top_speed": 210.0, "accel": 3.9, "seats": 2, "fuel": 4.6},
	"falkner_city":   {"name": "Falkner Stadtrad", "brand": "Falkner", "type": "bicycle", "category": "Fahrrad",
		"price": 690.0, "top_speed": 28.0, "accel": 8.0, "seats": 1, "fuel": 0.0},
	# Fahrzeuge aus zugelieferten 3D-Modellen (siehe ImportedVehicles)
	"vanari_furio":   {"name": "Vanari Furio", "brand": "Vanari", "type": "sports", "category": "Sportwagen",
		"price": 398000.0, "top_speed": 345.0, "accel": 2.6, "seats": 2, "fuel": 15.2, "imported": true},
}

const CATEGORIES := ["Fahrrad", "Motorrad", "Kleinwagen", "Limousine", "SUV", "Sportwagen", "Oberklasse", "Transporter"]

## Firmenaufschriften (fiktiv) fuer Lieferwagen und Lkw.
const LIVERIES := [
	{"text": "PAKETBLITZ", "color": Color(0.95, 0.75, 0.10), "text_color": Color(0.10, 0.10, 0.12)},
	{"text": "Frischmarkt Lehner", "color": Color(0.20, 0.55, 0.30), "text_color": Color(1, 1, 1)},
	{"text": "Möbel Sander", "color": Color(0.85, 0.30, 0.15), "text_color": Color(1, 1, 1)},
	{"text": "Elektro Wirth", "color": Color(0.15, 0.35, 0.70), "text_color": Color(1, 1, 1)},
	{"text": "Getränke Ostbach", "color": Color(0.55, 0.12, 0.18), "text_color": Color(1, 1, 1)},
	{"text": "Bauhof Kerner", "color": Color(0.92, 0.92, 0.90), "text_color": Color(0.85, 0.35, 0.10)},
]


static func type_info(type_id: String) -> Dictionary:
	return TYPES.get(type_id, TYPES["sedan"])


static func get_model(model_id: String) -> Dictionary:
	return MODELS.get(model_id, {})


## Kaufbare Modelle, optional nach Kategorie gefiltert. Liefert Kopien mit `id`.
static func buyable_models(category := "") -> Array:
	var out := []
	for id in MODELS:
		var m: Dictionary = MODELS[id]
		if category == "" or m.category == category:
			var d := m.duplicate()
			d["id"] = id
			out.append(d)
	out.sort_custom(func(a, b): return a.price < b.price)
	return out


static func price_of(model_id: String) -> float:
	return float(MODELS.get(model_id, {}).get("price", 0.0))


## Fahrdynamik eines Modells fuer das Spielerfahrzeug (m/s, m/s^2).
static func handling(model_id: String) -> Dictionary:
	var m: Dictionary = MODELS.get(model_id, {})
	var type_id: String = m.get("type", type_for_good(model_id))
	var t: Dictionary = type_info(type_id)
	var top_kmh: float = m.get("top_speed", t.vmax * 3.6)
	var accel_s: float = m.get("accel", {"sports": 4.5, "luxury": 6.0, "suv": 8.5, "motorcycle": 4.0, "bicycle": 8.0}.get(type_id, 9.5))
	if m.is_empty() and GOOD_TYPES.has(model_id):
		var spec: Dictionary = GoodsCatalog.VEHICLE_SPECS.get(model_id, {})
		top_kmh = float(spec.get("top_speed", t.vmax)) * 3.6
	# Arcade: etwas kraeftiger als die Katalogwerte
	return {
		"top_speed": top_kmh / 3.6,
		"accel": clampf(27.8 / maxf(accel_s, 2.5) * 1.6, 2.5, 11.0),
		"brake": 9.0 if t.kind != "bike" else 4.0,
		"length": t.length, "width": t.width, "height": t.height,
	}


static func random_ai_type(rng: RandomNumberGenerator) -> String:
	var total := 0.0
	for k in AI_WEIGHTS:
		total += AI_WEIGHTS[k]
	var r := rng.randf() * total
	for k in AI_WEIGHTS:
		r -= AI_WEIGHTS[k]
		if r <= 0.0:
			return k
	return "sedan"


static func random_paint(rng: RandomNumberGenerator) -> Color:
	var total := 0
	for w in PAINT_WEIGHTS:
		total += w
	var r := rng.randi_range(0, total - 1)
	for i in PAINT.size():
		r -= PAINT_WEIGHTS[i]
		if r < 0:
			return PAINT[i]
	return PAINT[0]


## Standardfarbe eines Typs (Taxi, Einsatzfahrzeuge) oder Zufallslack.
static func paint_for(type_id: String, rng: RandomNumberGenerator) -> Color:
	match type_id:
		"taxi":
			return TAXI_COLOR
		"police":
			return POLICE_COLOR
		"ambulance":
			return AMBULANCE_COLOR
		"fire":
			return FIRE_COLOR
		"bus":
			return Color(0.95, 0.95, 0.93)
	return random_paint(rng)


## Bauform fuer Fahrzeugwaren (GoodsCatalog, z.B. "car_suv") bzw. Katalogmodelle.
const GOOD_TYPES := {
	"bicycle": "bicycle", "used_bike": "bicycle", "scooter": "motorcycle", "motorcycle": "motorcycle",
	"car_used": "hatchback", "car_small": "hatchback", "car_compact": "sedan", "car_sedan": "sedan",
	"car_suv": "suv", "car_sports": "sports", "car_luxury": "luxury",
	"car_hyper": "sports",
}


## Ware -> aufbereitetes Fremdmodell (leer, wenn es keines gibt).
const GOOD_IMPORTED := {
	"car_hyper": "vanari_furio",
}


static func imported_for_good(good_id: String) -> String:
	return str(GOOD_IMPORTED.get(good_id, ""))


static func type_for_good(good_id: String) -> String:
	if GOOD_TYPES.has(good_id):
		return GOOD_TYPES[good_id]
	if MODELS.has(good_id):
		return MODELS[good_id].type
	if TYPES.has(good_id):
		return good_id
	return "sedan"
