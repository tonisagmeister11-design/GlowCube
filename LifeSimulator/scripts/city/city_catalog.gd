class_name CityCatalog
extends RefCounted
## Statische Daten fuer die Stadtgenerierung: Zonen, Gebaeudearten,
## Geschaeftsarten und fiktive Namen. Alle Namen sind frei erfunden.

const CITY_NAME := "Havenbrueck"
const CITY_NAME_DISPLAY := "Havenbrück"

## Zonen (Stadtteiltypen). wealth/crime 0..1 wirken auf Mieten, Preise und
## NPC-Verhalten.
const ZONES := {
	"downtown":    {"label": "Innenstadt",          "wealth": 0.70, "crime": 0.30, "density": 1.00},
	"business":    {"label": "Geschäftsviertel",    "wealth": 0.75, "crime": 0.20, "density": 0.90},
	"commercial":  {"label": "Einkaufsviertel",     "wealth": 0.55, "crime": 0.30, "density": 0.70},
	"oldtown":     {"label": "Altstadt",            "wealth": 0.60, "crime": 0.25, "density": 0.70},
	"apartments":  {"label": "Mietwohngebiet",      "wealth": 0.45, "crime": 0.30, "density": 0.70},
	"highrise":    {"label": "Hochhausviertel",     "wealth": 0.40, "crime": 0.40, "density": 0.90},
	"residential": {"label": "Wohngebiet",          "wealth": 0.55, "crime": 0.15, "density": 0.40},
	"suburb":      {"label": "Vorort",              "wealth": 0.60, "crime": 0.10, "density": 0.25},
	"rich":        {"label": "Villenviertel",       "wealth": 0.95, "crime": 0.05, "density": 0.20},
	"estates":     {"label": "Anwesen",             "wealth": 1.00, "crime": 0.03, "density": 0.10},
	"poor":        {"label": "Arbeiterviertel",     "wealth": 0.20, "crime": 0.55, "density": 0.70},
	"slum":        {"label": "Brennpunkt",          "wealth": 0.05, "crime": 0.85, "density": 0.60},
	"industrial":  {"label": "Industriegebiet",     "wealth": 0.30, "crime": 0.45, "density": 0.30},
	"university":  {"label": "Universitätsviertel", "wealth": 0.50, "crime": 0.15, "density": 0.50},
	"civic":       {"label": "Verwaltungsviertel",  "wealth": 0.70, "crime": 0.10, "density": 0.50},
	"park":        {"label": "Park",                "wealth": 0.60, "crime": 0.15, "density": 0.00},
}

const DISTRICT_NAMES := {
	"downtown": ["Zentrum", "Stadtmitte"],
	"business": ["Bankenviertel", "Neue Mitte", "Kontorviertel"],
	"commercial": ["Marktviertel", "Westend", "Galerieviertel", "Ladenstadt"],
	"oldtown": ["Altstadt"],
	"apartments": ["Lindenau", "Birkenfeld", "Rosenhof", "Am Kanal", "Ostend", "Nordstadt"],
	"highrise": ["Hochfeld", "Turmviertel", "Neustadt-Nord"],
	"residential": ["Gartenstadt", "Sonnenfeld", "Eichengrund", "Am Wiesengrund", "Kirchberg-Siedlung"],
	"suburb": ["Waldrand", "Felderheim", "Südweiler", "Haselbach", "Mühlbach"],
	"rich": ["Seeblick", "Villenkolonie Parkhain", "Goldufer"],
	"estates": ["Herrenhain", "Rosenanger"],
	"poor": ["Kesselviertel", "Schmiedeviertel", "Eisenhof"],
	"slum": ["Rostgraben", "Unterhafen"],
	"industrial": ["Industriepark Ost", "Hafenwerke", "Gewerbegebiet Nord"],
	"university": ["Campusviertel"],
	"civic": ["Regierungsviertel"],
	"park": ["Stadtpark", "Volksgarten", "Lindenpark", "Uferpark", "Wiesenpark", "Schlossgarten"],
}

const STREET_NAMES := [
	"Hauptstraße", "Lindenallee", "Bahnhofstraße", "Gartenweg", "Schillerstraße", "Goethestraße",
	"Kastanienweg", "Rosenstraße", "Uferstraße", "Brückenstraße", "Marktstraße", "Kirchgasse",
	"Mühlenweg", "Ahornstraße", "Birkenweg", "Eichenallee", "Bergstraße", "Feldstraße",
	"Hafenstraße", "Industriestraße", "Parkstraße", "Schulstraße", "Turmstraße", "Wiesenweg",
	"Sonnenstraße", "Mondgasse", "Sternstraße", "Kaiserallee", "Friedensstraße", "Rathausstraße",
	"Königstraße", "Werftstraße", "Glockengasse", "Fichtenweg", "Tannenstraße", "Ulmenstraße",
	"Weidenweg", "Holunderweg", "Fliederstraße", "Nelkenweg", "Tulpenstraße", "Akazienweg",
	"Am Kanal", "Am Stadtgraben", "Mozartstraße", "Beethovenstraße", "Händelweg", "Bachstraße",
	"Kantstraße", "Leibnizstraße", "Humboldtstraße", "Keplerstraße", "Gaußstraße", "Röntgenweg",
	"Lessingstraße", "Heinestraße", "Fontaneweg", "Storchengasse", "Falkenstraße", "Adlerweg",
	"Amselweg", "Lerchenstraße", "Meisenweg", "Finkenstraße", "Drosselgasse", "Kranichweg",
	"Silberstraße", "Kupferweg", "Eisenstraße", "Zinngasse", "Salzstraße", "Weberstraße",
	"Schmiedestraße", "Bäckergasse", "Färberstraße", "Gerberweg", "Seilergasse", "Zimmererweg",
	"Neue Straße", "Alte Landstraße", "Waldstraße", "Seestraße", "Wallstraße", "Torstraße",
]
const ARTERIAL_NAMES := [
	"Grand Avenue", "Metropolring", "Nordbrücker Allee", "Südtor-Boulevard", "Kaiser-Wilhelm-Ring",
	"Westallee", "Ostallee", "Havenbrücker Damm", "Zentralachse", "Freiheitsallee", "Hansaring",
	"Europaallee", "Messeallee", "Universitätsallee",
]
const EMBANKMENT_NAMES := ["Nordufer", "Südufer"]
const RING_NAMES := ["Nordring", "Ostring", "Südring", "Westring"]

## Gebaeudearten. footprint = [min_w, max_w, min_d, max_d] (Meter),
## setback = [vorne, seitlich, hinten], floors = [min, max].
## res_units: Wohneinheiten pro Etage (0 = nicht bewohnbar).
## biz: Anzahl Gewerbeeinheiten im Erdgeschoss.
const BUILDING_KINDS := {
	"house":        {"label": "Einfamilienhaus", "footprint": [8, 12, 8, 12],  "setback": [6, 2.5, 4], "floors": [1, 2],  "floor_h": 3.0, "res_units": 0, "res_fixed": 1, "driveway": true},
	"townhouse":    {"label": "Reihenhaus",      "footprint": [6, 9, 9, 13],   "setback": [3, 0.6, 4], "floors": [2, 3],  "floor_h": 3.0, "res_units": 0, "res_fixed": 1},
	"villa":        {"label": "Villa",           "footprint": [14, 22, 12, 18],"setback": [9, 5, 8],   "floors": [2, 3],  "floor_h": 3.4, "res_units": 0, "res_fixed": 1, "driveway": true, "pool": true},
	"estate":       {"label": "Anwesen",         "footprint": [22, 34, 16, 24],"setback": [16, 10, 12],"floors": [2, 3],  "floor_h": 3.8, "res_units": 0, "res_fixed": 1, "driveway": true, "pool": true},
	"apartment":    {"label": "Mehrfamilienhaus","footprint": [16, 30, 12, 18],"setback": [3, 2, 4],   "floors": [3, 6],  "floor_h": 3.0, "res_units": 4},
	"tenement":     {"label": "Mietskaserne",    "footprint": [14, 28, 12, 16],"setback": [1.5, 1, 3], "floors": [3, 5],  "floor_h": 2.9, "res_units": 6},
	"highrise_res": {"label": "Wohnhochhaus",    "footprint": [18, 28, 16, 24],"setback": [6, 5, 6],   "floors": [9, 22], "floor_h": 2.9, "res_units": 6},
	"mixed":        {"label": "Wohn- und Geschäftshaus", "footprint": [12, 26, 12, 18], "setback": [0.5, 0.5, 3], "floors": [3, 6], "floor_h": 3.2, "res_units": 3, "biz": 1},
	"office":       {"label": "Bürogebäude",     "footprint": [18, 36, 16, 28],"setback": [2, 1.5, 3], "floors": [4, 12], "floor_h": 3.6, "res_units": 0},
	"tower":        {"label": "Wolkenkratzer",   "footprint": [22, 34, 22, 34],"setback": [6, 4, 5],   "floors": [16, 48],"floor_h": 3.8, "res_units": 0},
	"shop":         {"label": "Geschäft",        "footprint": [10, 20, 10, 18],"setback": [1, 1, 3],   "floors": [1, 2],  "floor_h": 4.0, "res_units": 0, "biz": 1},
	"supermarket":  {"label": "Supermarkt",      "footprint": [28, 44, 22, 32],"setback": [16, 3, 3],  "floors": [1, 1],  "floor_h": 6.0, "res_units": 0, "biz": 1, "parking_front": true},
	"market_hall":  {"label": "Markthalle",      "footprint": [30, 50, 24, 36],"setback": [6, 3, 3],   "floors": [1, 1],  "floor_h": 9.0, "res_units": 0, "biz": 1},
	"market_square":{"label": "Marktplatz",      "footprint": [24, 60, 20, 40],"setback": [2, 2, 2],   "floors": [0, 0],  "floor_h": 0.0, "res_units": 0, "biz": 1, "open_air": true},
	"restaurant":   {"label": "Restaurant",      "footprint": [10, 18, 10, 16],"setback": [3, 1.5, 3], "floors": [1, 2],  "floor_h": 3.8, "res_units": 0, "biz": 1},
	"warehouse":    {"label": "Lagerhalle",      "footprint": [26, 50, 20, 40],"setback": [10, 4, 4],  "floors": [1, 1],  "floor_h": 9.0, "res_units": 0, "parking_front": true},
	"factory":      {"label": "Fabrik",          "footprint": [30, 60, 24, 44],"setback": [10, 4, 4],  "floors": [1, 2],  "floor_h": 8.0, "res_units": 0, "parking_front": true},
	"school":       {"label": "Schule",          "footprint": [34, 56, 18, 26],"setback": [10, 6, 10], "floors": [2, 3],  "floor_h": 3.6, "res_units": 0},
	"university":   {"label": "Universität",     "footprint": [40, 70, 22, 34],"setback": [14, 8, 10], "floors": [3, 5],  "floor_h": 4.0, "res_units": 0},
	"hospital":     {"label": "Krankenhaus",     "footprint": [40, 64, 26, 40],"setback": [14, 6, 8],  "floors": [4, 8],  "floor_h": 3.8, "res_units": 0},
	"clinic":       {"label": "Arztpraxis",      "footprint": [12, 20, 10, 16],"setback": [3, 1.5, 3], "floors": [2, 3],  "floor_h": 3.4, "res_units": 0},
	"police":       {"label": "Polizeiwache",    "footprint": [24, 36, 16, 24],"setback": [8, 4, 6],   "floors": [2, 3],  "floor_h": 3.6, "res_units": 0, "parking_front": true},
	"fire_station": {"label": "Feuerwache",      "footprint": [24, 34, 18, 24],"setback": [10, 4, 5],  "floors": [2, 2],  "floor_h": 4.5, "res_units": 0},
	"city_hall":    {"label": "Rathaus",         "footprint": [40, 60, 24, 34],"setback": [16, 8, 8],  "floors": [3, 4],  "floor_h": 4.5, "res_units": 0},
	"parliament":   {"label": "Regierungssitz",  "footprint": [50, 76, 28, 40],"setback": [20, 10, 10],"floors": [3, 4],  "floor_h": 5.0, "res_units": 0},
	"station":      {"label": "Hauptbahnhof",    "footprint": [50, 80, 24, 36],"setback": [14, 6, 6],  "floors": [2, 3],  "floor_h": 6.0, "res_units": 0},
	"bus_depot":    {"label": "Busdepot",        "footprint": [36, 60, 24, 40],"setback": [10, 4, 4],  "floors": [1, 1],  "floor_h": 7.0, "res_units": 0},
	"gas_station":  {"label": "Tankstelle",      "footprint": [10, 14, 8, 12], "setback": [14, 4, 4],  "floors": [1, 1],  "floor_h": 4.0, "res_units": 0, "biz": 1},
	"shelter":      {"label": "Obdachlosenunterkunft", "footprint": [16, 24, 12, 18], "setback": [3, 2, 3], "floors": [2, 3], "floor_h": 3.0, "res_units": 0},
	"abandoned":    {"label": "Leerstehendes Gebäude", "footprint": [12, 24, 10, 16], "setback": [2, 1.5, 3], "floors": [2, 4], "floor_h": 3.0, "res_units": 0},
	"homeless_camp":{"label": "Zeltlager",        "footprint": [10, 24, 8, 18], "setback": [3, 2, 2],   "floors": [0, 0],  "floor_h": 0.0, "res_units": 0, "open_air": true},
}

## Wichtige Einrichtungen, die auf der Karte erscheinen.
const CIVIC_KINDS := ["school", "university", "hospital", "clinic", "police", "fire_station",
	"city_hall", "parliament", "station", "bus_depot", "shelter"]

## Geschaeftsarten. tier: 0 billig .. 3 luxus. map_category fuer Kartenfilter.
const BUSINESS_TYPES := {
	"supermarket": {"label": "Supermarkt",        "map_category": "food",     "tier": 1, "open": [7, 22]},
	"grocery":     {"label": "Lebensmittelladen", "map_category": "food",     "tier": 1, "open": [7, 21]},
	"produce":     {"label": "Obst & Gemüse",     "map_category": "food",     "tier": 1, "open": [7, 19]},
	"bakery":      {"label": "Bäckerei",          "map_category": "food",     "tier": 1, "open": [6, 18]},
	"butcher":     {"label": "Metzgerei",         "map_category": "food",     "tier": 1, "open": [7, 18]},
	"convenience": {"label": "Kiosk",             "map_category": "food",     "tier": 1, "open": [0, 24]},
	"clothing":    {"label": "Bekleidung",        "map_category": "shopping", "tier": 2, "open": [9, 20]},
	"electronics": {"label": "Elektronik",        "map_category": "shopping", "tier": 2, "open": [9, 20]},
	"furniture":   {"label": "Möbelhaus",         "map_category": "shopping", "tier": 2, "open": [9, 20]},
	"hardware":    {"label": "Baumarkt",          "map_category": "shopping", "tier": 1, "open": [7, 20]},
	"pharmacy":    {"label": "Apotheke",          "map_category": "health",   "tier": 2, "open": [8, 20]},
	"luxury":      {"label": "Luxusboutique",     "map_category": "shopping", "tier": 3, "open": [10, 19]},
	"secondhand":  {"label": "Secondhand",        "map_category": "shopping", "tier": 0, "open": [10, 18]},
	"market":      {"label": "Markt",             "map_category": "market",   "tier": 1, "open": [7, 18]},
	"flea_market": {"label": "Flohmarkt",         "map_category": "market",   "tier": 0, "open": [8, 16]},
	"night_market":{"label": "Nachtmarkt",        "map_category": "market",   "tier": 1, "open": [18, 2]},
	"luxury_market":{"label": "Luxusmarkt",       "map_category": "market",   "tier": 3, "open": [10, 20]},
	"restaurant":  {"label": "Restaurant",        "map_category": "food",     "tier": 2, "open": [11, 23]},
	"fastfood":    {"label": "Imbiss",            "map_category": "food",     "tier": 0, "open": [10, 24]},
	"cafe":        {"label": "Café",              "map_category": "food",     "tier": 1, "open": [7, 19]},
	"bar":         {"label": "Bar",               "map_category": "leisure",  "tier": 2, "open": [17, 3]},
	"gym":         {"label": "Fitnessstudio",     "map_category": "leisure",  "tier": 2, "open": [6, 23]},
	"cinema":      {"label": "Kino",              "map_category": "leisure",  "tier": 2, "open": [14, 24]},
	"car_dealer":  {"label": "Autohaus",          "map_category": "vehicles", "tier": 2, "open": [9, 19]},
	"gas_station": {"label": "Tankstelle",        "map_category": "vehicles", "tier": 1, "open": [0, 24]},
	"real_estate": {"label": "Immobilienmakler",  "map_category": "services", "tier": 2, "open": [9, 18]},
	"job_center":  {"label": "Jobcenter",         "map_category": "services", "tier": 1, "open": [8, 17]},
	"bank":        {"label": "Bank",              "map_category": "services", "tier": 2, "open": [9, 17]},
}

## Benannte Maerkte (Vorgabe aus dem Design) und Zonen, in die sie passen.
const NAMED_MARKETS := [
	{"name": "Central Market", "type": "market", "zones": ["downtown", "oldtown"], "kind": "market_hall", "color": Color(0.85, 0.32, 0.18)},
	{"name": "Westside Fresh Market", "type": "market", "zones": ["commercial", "apartments"], "kind": "market_hall", "color": Color(0.30, 0.65, 0.30)},
	{"name": "Riverside Market Hall", "type": "market", "zones": ["commercial", "oldtown", "apartments"], "kind": "market_hall", "color": Color(0.18, 0.48, 0.78)},
	{"name": "Old Town Bazaar", "type": "market", "zones": ["oldtown", "commercial"], "kind": "market_square", "color": Color(0.72, 0.50, 0.20)},
	{"name": "Southgate Street Market", "type": "market", "zones": ["poor", "apartments", "commercial"], "kind": "market_square", "color": Color(0.80, 0.55, 0.15)},
	{"name": "Grand Avenue Market", "type": "market", "zones": ["business", "commercial", "downtown"], "kind": "market_hall", "color": Color(0.55, 0.20, 0.45)},
	{"name": "Harbor Trade Market", "type": "market", "zones": ["industrial", "poor"], "kind": "market_hall", "color": Color(0.20, 0.35, 0.55)},
	{"name": "Greenfield Farmers Market", "type": "market", "zones": ["suburb", "residential"], "kind": "market_square", "color": Color(0.40, 0.62, 0.22)},
	{"name": "Nightfall Night Market", "type": "night_market", "zones": ["downtown", "commercial", "oldtown"], "kind": "market_square", "color": Color(0.45, 0.25, 0.70)},
	{"name": "Northbridge Flea Market", "type": "flea_market", "zones": ["poor", "slum", "apartments"], "kind": "market_square", "color": Color(0.60, 0.42, 0.28)},
	{"name": "Metro Plaza Market", "type": "market", "zones": ["highrise", "apartments", "commercial"], "kind": "market_hall", "color": Color(0.15, 0.55, 0.60)},
	{"name": "Luxury Square", "type": "luxury_market", "zones": ["rich", "business", "downtown"], "kind": "market_square", "color": Color(0.80, 0.68, 0.30)},
	{"name": "Sunrise Morning Market", "type": "market", "zones": ["residential", "suburb", "apartments"], "kind": "market_square", "color": Color(0.92, 0.60, 0.25)},
	{"name": "Ironworks Market Hall", "type": "market", "zones": ["industrial", "poor"], "kind": "market_hall", "color": Color(0.45, 0.45, 0.48)},
	{"name": "Campus Corner Market", "type": "market", "zones": ["university", "apartments"], "kind": "market_square", "color": Color(0.25, 0.45, 0.80)},
	{"name": "Lantern Street Market", "type": "night_market", "zones": ["poor", "oldtown", "commercial"], "kind": "market_square", "color": Color(0.85, 0.40, 0.20)},
]

## Fiktive Ladennamen je Geschaeftsart. Farbe pro Marke kommt aus BRAND_COLORS.
const BUSINESS_NAMES := {
	"supermarket": ["FreshWay", "Korbmarkt", "PrimaKauf", "Value Basket", "GrünKorb", "Stadtmarkt Plus", "Daily Harvest"],
	"grocery": ["Ecklädchen", "Tante Emmas", "Corner Pantry", "Kleiner Korb", "Nachbarschaftsladen"],
	"produce": ["Gärtners Obst", "Sonnenernte", "Apfel & Co.", "Grünzeug", "Erntefrisch"],
	"bakery": ["Backstube Krume", "Morgenbrot", "Goldkruste", "Mehl & Meister", "Brezelhaus"],
	"butcher": ["Metzgerei Hartmann", "Fleischerei Kober", "Wurstwerk", "Landmetzger Brandt"],
	"convenience": ["24/7 Quickstop", "Nachtkiosk", "Späti Ecke", "Snack Point", "Kiosk Zentral"],
	"clothing": ["Fadenwerk", "Urban Thread", "Stoffwechsel", "Nordmode", "Kleiderei", "Linea"],
	"electronics": ["Voltmarkt", "Pixelhaus", "CircuitCity Havenbrück", "Technikwelt", "Byte & Kabel"],
	"furniture": ["Wohnwerk", "Holz & Heim", "Sofaland", "Raumglück"],
	"hardware": ["Werkzeugkiste", "BauProfi", "Hammer & Nagel", "Heimwerkerhof"],
	"pharmacy": ["Löwen-Apotheke", "Stern-Apotheke", "Linden-Apotheke", "Kurapotheke", "Brücken-Apotheke"],
	"luxury": ["Maison Lumière", "Aurelia", "Valentin & Rose", "Goldfaden", "Opaline"],
	"secondhand": ["Zweite Chance", "Fundgrube", "Retrokiste", "Altgold & Krempel"],
	"restaurant": ["Zum Goldenen Anker", "Trattoria Fiume", "Brasserie Havenbrück", "Ratskeller", "Bistro Laterne", "Seoul Garden", "Casa Mar"],
	"fastfood": ["Burger Bros", "Dönerhaus Bosporus", "Wurstbude Hansi", "Pizza Blitz", "Noodle Box"],
	"cafe": ["Café Morgenrot", "Bohnenwerk", "Kaffeehaus Lindner", "Milchschaum", "Café Ufer"],
	"bar": ["Blaue Stunde", "Der Hafenkran", "Neon Lounge", "Kellerbar 12", "Nachtfalter"],
	"gym": ["IronForm", "FitWerk", "Pulse Gym", "Muskelkater"],
	"cinema": ["Lichtspielhaus", "Kino Stern", "Metropol-Kino"],
	"car_dealer": ["Autohaus Weller", "Stadtmobil Motors", "Drive Deluxe", "Gebrauchtwagen Krause"],
	"gas_station": ["Tank & Rast Nord", "Volltanken", "PetroStar", "Treibstoff 24"],
	"real_estate": ["Immobilien Falk & Partner", "Wohnglück Makler", "Stadtquartier Immobilien"],
	"job_center": ["Jobcenter Havenbrück"],
	"bank": ["Havenbrücker Sparkasse", "Bank am Fluss", "Volks- und Bürgerbank"],
}

const BRAND_COLORS := [
	Color(0.85, 0.20, 0.20), Color(0.20, 0.55, 0.85), Color(0.15, 0.65, 0.35), Color(0.95, 0.65, 0.10),
	Color(0.55, 0.25, 0.70), Color(0.10, 0.60, 0.65), Color(0.90, 0.40, 0.55), Color(0.35, 0.35, 0.40),
	Color(0.80, 0.50, 0.20), Color(0.25, 0.30, 0.65), Color(0.60, 0.70, 0.15), Color(0.75, 0.15, 0.35),
]

## Geschaeftsmix je Zone fuer Erdgeschoss-Gewerbe (gewichtete Auswahl).
const ZONE_BUSINESS_MIX := {
	"downtown":   {"restaurant": 4, "cafe": 4, "clothing": 4, "electronics": 2, "luxury": 2, "bar": 3, "pharmacy": 1, "convenience": 2, "fastfood": 2, "bank": 2, "cinema": 1, "gym": 1, "real_estate": 1},
	"business":   {"cafe": 4, "restaurant": 3, "fastfood": 2, "bank": 3, "convenience": 2, "luxury": 1, "gym": 1, "real_estate": 1, "pharmacy": 1},
	"commercial": {"clothing": 4, "electronics": 3, "furniture": 2, "hardware": 1, "restaurant": 2, "cafe": 2, "fastfood": 2, "pharmacy": 2, "grocery": 2, "bakery": 2, "secondhand": 1, "gym": 1, "cinema": 1, "car_dealer": 1, "gas_station": 1},
	"oldtown":    {"bakery": 3, "butcher": 2, "cafe": 4, "restaurant": 4, "clothing": 2, "pharmacy": 1, "bar": 2, "secondhand": 1, "produce": 2},
	"apartments": {"grocery": 3, "bakery": 3, "convenience": 3, "fastfood": 2, "pharmacy": 2, "cafe": 2, "butcher": 1, "produce": 1, "gym": 1, "secondhand": 1},
	"highrise":   {"convenience": 4, "fastfood": 3, "grocery": 3, "pharmacy": 1, "bakery": 2, "secondhand": 1, "gym": 1},
	"residential":{"bakery": 2, "grocery": 2, "cafe": 1, "pharmacy": 1, "produce": 1},
	"suburb":     {"bakery": 1, "grocery": 1, "gas_station": 1, "hardware": 1},
	"rich":       {"luxury": 3, "cafe": 2, "restaurant": 2, "bakery": 1},
	"estates":    {"luxury": 1},
	"poor":       {"convenience": 4, "fastfood": 3, "secondhand": 3, "grocery": 2, "bar": 2, "bakery": 1, "pharmacy": 1},
	"slum":       {"convenience": 3, "secondhand": 2, "fastfood": 2, "bar": 2},
	"industrial": {"fastfood": 2, "gas_station": 2, "hardware": 2, "car_dealer": 2, "convenience": 1},
	"university": {"cafe": 4, "fastfood": 3, "bar": 2, "electronics": 1, "secondhand": 2, "convenience": 2},
	"civic":      {"cafe": 2, "restaurant": 1, "bank": 1, "job_center": 1},
}

## Vornamen/Nachnamen fuer NPCs (fiktiv, gemischt).
const FIRST_NAMES_F := ["Anna", "Lea", "Sophie", "Marie", "Emma", "Mia", "Hannah", "Laura", "Julia", "Sarah",
	"Lena", "Clara", "Paula", "Nora", "Ida", "Greta", "Elif", "Aylin", "Zoe", "Lina", "Maja", "Frieda",
	"Ronja", "Helena", "Johanna", "Katharina", "Sabine", "Petra", "Monika", "Ursula", "Ingrid", "Renate",
	"Yasmin", "Leyla", "Natalia", "Olga", "Chiara", "Giulia", "Amira", "Selin", "Mila", "Charlotte"]
const FIRST_NAMES_M := ["Lukas", "Leon", "Paul", "Jonas", "Felix", "Noah", "Elias", "Ben", "Finn", "Max",
	"Tim", "Jan", "David", "Simon", "Moritz", "Emil", "Anton", "Theo", "Karl", "Oskar", "Can", "Deniz",
	"Mehmet", "Ali", "Mateo", "Luca", "Marco", "Thomas", "Michael", "Stefan", "Andreas", "Klaus", "Jürgen",
	"Wolfgang", "Dieter", "Igor", "Pavel", "Kofi", "Samuel", "Viktor", "Henrik", "Jakob"]
const LAST_NAMES := ["Müller", "Schmidt", "Schneider", "Fischer", "Weber", "Meyer", "Wagner", "Becker",
	"Schulz", "Hoffmann", "Koch", "Richter", "Klein", "Wolf", "Neumann", "Schwarz", "Zimmermann",
	"Braun", "Krüger", "Hofmann", "Hartmann", "Lange", "Schmitt", "Werner", "Krause", "Lehmann",
	"Köhler", "Maier", "Huber", "Kaiser", "Fuchs", "Peters", "Lang", "Scholz", "Möller", "Weiß",
	"Jung", "Hahn", "Vogel", "Friedrich", "Keller", "Günther", "Frank", "Berger", "Winkler", "Roth",
	"Yilmaz", "Kaya", "Demir", "Nowak", "Kowalski", "Rossi", "Bianchi", "Petrov", "Ivanova", "Okafor",
	"Larsen", "Novak", "Moretti", "Lindqvist", "Sørensen", "Costa", "Silva", "Haddad", "Nguyen", "Kim"]
