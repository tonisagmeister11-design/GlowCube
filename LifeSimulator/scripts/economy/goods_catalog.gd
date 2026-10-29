class_name GoodsCatalog
extends RefCounted
## Warenkatalog: alle kaufbaren Gueter mit Grundpreis, Einheit und Wirkung
## auf die Beduerfnisse. Alle Marken sind frei erfunden.
##
## GOODS[id] = {name, category, base_price, unit, needs: {hunger, thirst, energy,
##   hygiene, fun, health, social}, consumable, durable, icon, tier (0..3),
##   meals (Mahlzeiten fuer den Haushaltsvorrat), service (vor Ort verbraucht),
##   vehicle (Fahrzeugdaten oder {}), wholesale (Einkaufsanteil am Preis), brand}
## Beduerfniswerte: positive Zahl = Beduerfnis wird um so viele Punkte erfuellt
## (100 = voll erfuellt), negative = Schaden.
##
## SHOP_ASSORTMENT[business_type] = [good ids] - Sortiment je Geschaeftsart.
## Preise im Laden = base_price * Stufenfaktor * Viertelfaktor * price_level
## (siehe Economy.reference_price()).

const CATEGORIES := {
	"bakery": "Backwaren", "produce": "Obst & Gemüse", "meat": "Fleisch & Wurst",
	"groceries": "Lebensmittel", "drinks": "Getränke", "hygiene": "Drogerie & Haushalt",
	"medicine": "Medizin", "fastfood": "Imbiss", "cafe": "Café", "restaurant": "Restaurant",
	"bar": "Bar", "clothing": "Kleidung", "luxury": "Luxus", "electronics": "Elektronik",
	"furniture": "Möbel", "hardware": "Baumarkt", "secondhand": "Gebrauchtwaren",
	"service": "Dienstleistung", "fuel": "Kraftstoff", "vehicle": "Fahrzeuge", "market": "Marktware",
	"gourmet": "Feinkost",
}

## Icon je Kategorie (SVG unter res://assets/icons/<name>.svg).
const CATEGORY_ICONS := {
	"bakery": "bread", "produce": "apple", "meat": "meat", "groceries": "basket", "drinks": "bottle",
	"hygiene": "soap", "medicine": "pill", "fastfood": "burger", "cafe": "coffee", "restaurant": "plate",
	"bar": "glass", "clothing": "shirt", "luxury": "diamond", "electronics": "phone", "furniture": "sofa",
	"hardware": "tool", "secondhand": "tag", "service": "ticket", "fuel": "fuel", "vehicle": "car",
	"market": "basket", "gourmet": "star",
}

## Einkaufsanteil (Grosshandelspreis / Ladenpreis) je Kategorie. Dienstleistungen
## haben niedrige Wareneinsaetze, weil der Preis vor allem Arbeit bezahlt.
const WHOLESALE := {
	"bakery": 0.42, "produce": 0.55, "meat": 0.55, "groceries": 0.62, "drinks": 0.55, "hygiene": 0.55,
	"medicine": 0.55, "fastfood": 0.35, "cafe": 0.25, "restaurant": 0.32, "bar": 0.25, "clothing": 0.45,
	"luxury": 0.40, "electronics": 0.72, "furniture": 0.55, "hardware": 0.58, "secondhand": 0.35,
	"service": 0.15, "fuel": 0.80, "vehicle": 0.82, "market": 0.50, "gourmet": 0.50,
}

## Zeilenformat: [id, name, category, base_price, unit, needs, flags, meals, tier]
## flags: c = Verbrauchsgut, d = langlebig, s = Dienstleistung (vor Ort), v = Fahrzeug
const _ROWS := [
	# --- Baeckerei
	["bread", "Bauernbrot „Goldkruste“", "bakery", 3.20, "Laib", {"hunger": 30}, "c", 3.0, 1],
	["rolls", "Brötchen (6 Stück)", "bakery", 2.40, "Tüte", {"hunger": 22}, "c", 2.0, 1],
	["pretzel", "Butterbrezel", "bakery", 1.60, "Stück", {"hunger": 14, "fun": 2}, "c", 0.0, 1],
	["croissant", "Buttercroissant", "bakery", 1.50, "Stück", {"hunger": 12, "fun": 3}, "c", 0.0, 1],
	["cake_slice", "Käsekuchen „Krume“", "bakery", 3.20, "Stück", {"hunger": 16, "fun": 6}, "c", 0.0, 1],
	["layer_cake", "Festtagstorte „Mehl & Meister“", "bakery", 26.00, "Torte", {"hunger": 30, "fun": 15, "social": 10}, "c", 4.0, 2],
	["bakery_coffee", "Kaffee to go", "bakery", 2.20, "Becher", {"energy": 12, "thirst": 8}, "c", 0.0, 1],
	# --- Obst und Gemuese
	["apples", "Äpfel „Sonnenernte“ (1 kg)", "produce", 2.80, "Beutel", {"hunger": 12, "health": 2}, "c", 1.0, 1],
	["bananas", "Bananen (1 kg)", "produce", 1.90, "Bund", {"hunger": 14, "health": 1}, "c", 1.0, 1],
	["vegetables", "Gemüsekiste „Erntefrisch“", "produce", 7.50, "Kiste", {"hunger": 20, "health": 4}, "c", 4.0, 1],
	["salad", "Salatkopf", "produce", 1.30, "Stück", {"hunger": 8, "health": 2}, "c", 1.0, 1],
	["potatoes", "Kartoffeln (2,5 kg)", "produce", 3.20, "Sack", {"hunger": 25}, "c", 5.0, 1],
	["tomatoes", "Rispentomaten (500 g)", "produce", 2.40, "Schale", {"hunger": 8, "health": 2}, "c", 1.0, 1],
	["berries", "Erdbeeren (500 g)", "produce", 3.90, "Schale", {"hunger": 8, "fun": 4, "health": 1}, "c", 1.0, 2],
	# --- Fleisch
	["sausages", "Bratwürste „Wurstwerk“ (4 Stück)", "meat", 5.90, "Packung", {"hunger": 35}, "c", 2.0, 1],
	["steak", "Rinderhüftsteak", "meat", 11.50, "Stück", {"hunger": 40, "fun": 5}, "c", 1.5, 2],
	["chicken", "Hähnchenbrust (500 g)", "meat", 6.40, "Packung", {"hunger": 35}, "c", 2.0, 1],
	["ham", "Schinkenaufschnitt", "meat", 2.90, "Packung", {"hunger": 12}, "c", 1.0, 1],
	["minced", "Hackfleisch gemischt (500 g)", "meat", 4.80, "Packung", {"hunger": 30}, "c", 2.0, 1],
	# --- Lebensmittel
	["milk", "Vollmilch „Weidegold“ (1 l)", "groceries", 1.20, "Liter", {"thirst": 15, "hunger": 6}, "c", 1.0, 1],
	["eggs", "Eier „Hofglück“ (10 Stück)", "groceries", 3.10, "Packung", {"hunger": 20}, "c", 2.0, 1],
	["cheese", "Gouda „Käsehof“", "groceries", 3.40, "Packung", {"hunger": 15}, "c", 1.5, 1],
	["butter", "Butter „Weidegold“", "groceries", 2.30, "Stück", {"hunger": 5}, "c", 1.0, 1],
	["pasta", "Spaghetti „Nonna Lucia“", "groceries", 1.50, "Packung", {"hunger": 30}, "c", 3.0, 1],
	["rice", "Langkornreis (1 kg)", "groceries", 2.20, "Packung", {"hunger": 30}, "c", 4.0, 1],
	["cereal", "Müsli „Morgensonne“", "groceries", 3.80, "Packung", {"hunger": 20, "health": 1}, "c", 3.0, 1],
	["frozen_pizza", "Tiefkühlpizza „Ofenheld“", "groceries", 3.30, "Stück", {"hunger": 40, "health": -1}, "c", 1.0, 1],
	["canned_soup", "Dosensuppe „Eintopf Express“", "groceries", 1.90, "Dose", {"hunger": 25}, "c", 1.0, 0],
	["ready_meal", "Fertiggericht „Schnellküche“", "groceries", 3.90, "Packung", {"hunger": 38}, "c", 1.0, 1],
	["instant_noodles", "Instantnudeln „Nudelblitz“", "groceries", 0.90, "Becher", {"hunger": 22, "health": -1}, "c", 1.0, 0],
	["coffee_beans", "Kaffee „Bohnenkönig“ (500 g)", "groceries", 6.90, "Packung", {"energy": 10}, "c", 0.0, 1],
	["tea", "Kräutertee „Teegarten“", "groceries", 2.50, "Packung", {"thirst": 10, "health": 1}, "c", 0.0, 1],
	["chocolate", "Schokolade „Kakaoperle“", "groceries", 1.40, "Tafel", {"hunger": 6, "fun": 5}, "c", 0.0, 1],
	["chips", "Kartoffelchips „Knusperland“", "groceries", 1.80, "Tüte", {"hunger": 8, "fun": 4, "health": -1}, "c", 0.0, 1],
	["baby_food", "Babybrei „Kleiner Löffel“", "groceries", 1.60, "Glas", {"hunger": 25}, "c", 1.0, 1],
	# --- Getraenke
	["water", "Mineralwasser „Quellklar“ (1,5 l)", "drinks", 0.70, "Flasche", {"thirst": 40}, "c", 0.0, 0],
	["juice", "Orangensaft „Sonnenhof“", "drinks", 2.10, "Liter", {"thirst": 30, "health": 1}, "c", 0.0, 1],
	["cola", "Cola „Fizz“", "drinks", 1.40, "Flasche", {"thirst": 25, "energy": 4}, "c", 0.0, 1],
	["energy_drink", "Energydrink „Voltage“", "drinks", 1.90, "Dose", {"energy": 18, "thirst": 12, "health": -2}, "c", 0.0, 1],
	["beer", "Bier „Havenbräu“ (6er)", "drinks", 5.90, "Träger", {"fun": 8, "thirst": 10, "social": 3, "health": -2}, "c", 0.0, 1],
	["wine", "Rotwein „Flussterrasse“", "drinks", 7.90, "Flasche", {"fun": 10, "social": 4, "health": -1}, "c", 0.0, 2],
	# --- Drogerie und Haushalt
	["shower_gel", "Duschgel „Frischwerk“", "hygiene", 2.40, "Flasche", {"hygiene": 30}, "c", 0.0, 1],
	["toilet_paper", "Toilettenpapier (8 Rollen)", "hygiene", 3.80, "Packung", {"hygiene": 10}, "c", 0.0, 1],
	["detergent", "Waschmittel „Weißwunder“", "hygiene", 6.50, "Packung", {"hygiene": 15}, "c", 0.0, 1],
	["toothpaste", "Zahnpasta „Perlweiß“", "hygiene", 1.90, "Tube", {"hygiene": 12, "health": 1}, "c", 0.0, 1],
	["diapers", "Windeln „Kuschelpo“", "hygiene", 9.90, "Packung", {"hygiene": 20}, "c", 0.0, 1],
	# --- Apotheke
	["painkillers", "Schmerztabletten „Dolorin“", "medicine", 5.90, "Packung", {"health": 10}, "c", 0.0, 2],
	["cold_medicine", "Erkältungsmittel „Grippex“", "medicine", 8.90, "Packung", {"health": 18}, "c", 0.0, 2],
	["cough_syrup", "Hustensaft „Bronchiflor“", "medicine", 7.40, "Flasche", {"health": 12}, "c", 0.0, 2],
	["vitamins", "Vitamintabletten „Vitalis“", "medicine", 7.50, "Dose", {"health": 6, "energy": 3}, "c", 0.0, 2],
	["first_aid_kit", "Verbandskasten", "medicine", 12.00, "Kasten", {"health": 15}, "c", 0.0, 2],
	["sleep_aid", "Baldrian-Schlafdragees", "medicine", 6.80, "Packung", {"energy": 8, "fun": 2}, "c", 0.0, 2],
	# --- Imbiss
	["burger_menu", "Burger-Menü „Bros Classic“", "fastfood", 8.90, "Menü", {"hunger": 55, "thirst": 20, "fun": 4, "health": -1}, "s", 0.0, 0],
	["doner", "Döner Kebab", "fastfood", 6.50, "Stück", {"hunger": 55, "fun": 3}, "s", 0.0, 0],
	["currywurst", "Currywurst mit Pommes", "fastfood", 5.80, "Portion", {"hunger": 48, "fun": 3, "health": -1}, "s", 0.0, 0],
	["pizza_slice", "Pizzastück „Pizza Blitz“", "fastfood", 3.50, "Stück", {"hunger": 25}, "s", 0.0, 0],
	["noodle_box", "Nudelbox „Noodle Box“", "fastfood", 7.20, "Box", {"hunger": 50}, "s", 0.0, 0],
	["fries", "Pommes frites", "fastfood", 3.20, "Portion", {"hunger": 22, "health": -1}, "s", 0.0, 0],
	["soft_drink", "Softdrink (0,5 l)", "fastfood", 2.50, "Becher", {"thirst": 30}, "s", 0.0, 0],
	# --- Cafe
	["cappuccino", "Cappuccino", "cafe", 3.40, "Tasse", {"energy": 18, "thirst": 10, "fun": 3, "social": 2}, "s", 0.0, 1],
	["espresso", "Espresso", "cafe", 2.20, "Tasse", {"energy": 15, "fun": 1}, "s", 0.0, 1],
	["latte", "Latte macchiato", "cafe", 3.90, "Glas", {"energy": 15, "thirst": 12, "fun": 4}, "s", 0.0, 1],
	["apple_cake", "Apfelkuchen mit Sahne", "cafe", 4.20, "Stück", {"hunger": 20, "fun": 6}, "s", 0.0, 1],
	["ciabatta", "Belegtes Ciabatta", "cafe", 5.50, "Stück", {"hunger": 32}, "s", 0.0, 1],
	["breakfast_plate", "Frühstücksteller", "cafe", 11.90, "Teller", {"hunger": 55, "thirst": 15, "fun": 6, "social": 4}, "s", 0.0, 1],
	# --- Restaurant
	["lunch_menu", "Mittagsmenü", "restaurant", 12.90, "Menü", {"hunger": 60, "thirst": 15, "fun": 4}, "s", 0.0, 1],
	["pasta_dish", "Pasta des Hauses", "restaurant", 15.50, "Teller", {"hunger": 60, "fun": 8, "social": 4}, "s", 0.0, 2],
	["schnitzel", "Wiener Schnitzel mit Kartoffelsalat", "restaurant", 19.90, "Teller", {"hunger": 70, "fun": 8, "social": 4}, "s", 0.0, 2],
	["fish_dish", "Zanderfilet auf Gemüsebett", "restaurant", 24.50, "Teller", {"hunger": 65, "fun": 10, "health": 2, "social": 4}, "s", 0.0, 2],
	["steak_dinner", "Rumpsteak mit Beilagen", "restaurant", 32.00, "Teller", {"hunger": 75, "fun": 12, "social": 5}, "s", 0.0, 2],
	["tasting_menu", "Degustationsmenü (5 Gänge)", "restaurant", 89.00, "Menü", {"hunger": 80, "fun": 25, "social": 10}, "s", 0.0, 3],
	["dessert", "Mousse au Chocolat", "restaurant", 7.50, "Schale", {"hunger": 10, "fun": 8}, "s", 0.0, 2],
	["house_wine", "Glas Hauswein", "restaurant", 5.50, "Glas", {"fun": 6, "social": 4, "thirst": 5}, "s", 0.0, 2],
	# --- Bar
	["draft_beer", "Bier vom Fass (0,5 l)", "bar", 4.50, "Glas", {"fun": 8, "social": 6, "thirst": 12, "health": -1}, "s", 0.0, 1],
	["cocktail", "Cocktail „Blaue Stunde“", "bar", 9.50, "Glas", {"fun": 14, "social": 8, "health": -1}, "s", 0.0, 2],
	["longdrink", "Longdrink", "bar", 8.00, "Glas", {"fun": 11, "social": 6, "health": -1}, "s", 0.0, 2],
	["bar_soft", "Limonade „Hausgemacht“", "bar", 3.20, "Glas", {"thirst": 30, "social": 3}, "s", 0.0, 1],
	["shot", "Kurzer „Hafenfeuer“", "bar", 3.00, "Glas", {"fun": 6, "social": 4, "health": -2}, "s", 0.0, 1],
	# --- Kleidung
	["tshirt", "T-Shirt „Basic Line“", "clothing", 9.90, "Stück", {"fun": 3, "hygiene": 5}, "d", 0.0, 0],
	["jeans", "Jeans „Denim Works“", "clothing", 39.90, "Stück", {"fun": 5}, "d", 0.0, 1],
	["sweater", "Pullover „Nordmode“", "clothing", 34.90, "Stück", {"fun": 5}, "d", 0.0, 1],
	["winter_jacket", "Winterjacke „Fadenwerk“", "clothing", 89.00, "Stück", {"fun": 6, "health": 2}, "d", 0.0, 1],
	["sneakers", "Sneaker „Laufwerk“", "clothing", 69.00, "Paar", {"fun": 6}, "d", 0.0, 1],
	["dress", "Sommerkleid „Linea“", "clothing", 49.00, "Stück", {"fun": 7}, "d", 0.0, 2],
	["business_suit", "Anzug „Linea Business“", "clothing", 249.00, "Stück", {"fun": 6}, "d", 0.0, 2],
	["work_boots", "Arbeitsschuhe S3", "clothing", 59.00, "Paar", {}, "d", 0.0, 1],
	["kids_clothes", "Kinderkleidung „Kleiner Strolch“", "clothing", 19.90, "Set", {"fun": 4}, "d", 0.0, 1],
	# --- Luxus
	["designer_bag", "Handtasche „Maison Lumière“", "luxury", 1450.00, "Stück", {"fun": 30}, "d", 0.0, 3],
	["luxury_watch", "Automatikuhr „Aurelia Chronograph“", "luxury", 4900.00, "Stück", {"fun": 35}, "d", 0.0, 3],
	["gold_necklace", "Goldkette „Goldfaden“", "luxury", 2200.00, "Stück", {"fun": 30}, "d", 0.0, 3],
	["tailored_suit", "Maßanzug „Valentin & Rose“", "luxury", 1890.00, "Stück", {"fun": 25}, "d", 0.0, 3],
	["perfume", "Parfum „Opaline No. 7“", "luxury", 129.00, "Flakon", {"fun": 12, "hygiene": 10}, "c", 0.0, 3],
	["silk_scarf", "Seidentuch „Aurelia“", "luxury", 320.00, "Stück", {"fun": 15}, "d", 0.0, 3],
	# --- Elektronik
	["phone_basic", "Smartphone „Pixo S“", "electronics", 179.00, "Stück", {"fun": 10, "social": 5}, "d", 0.0, 1],
	["phone_premium", "Smartphone „Pixo Ultra“", "electronics", 1099.00, "Stück", {"fun": 20, "social": 8}, "d", 0.0, 2],
	["laptop", "Laptop „Byteforce 14“", "electronics", 749.00, "Stück", {"fun": 12}, "d", 0.0, 2],
	["television", "Fernseher „Klarbild 55“", "electronics", 549.00, "Stück", {"fun": 20}, "d", 0.0, 2],
	["game_console", "Spielkonsole „Nexo“", "electronics", 449.00, "Stück", {"fun": 25}, "d", 0.0, 2],
	["headphones", "Kopfhörer „Klangraum“", "electronics", 89.00, "Stück", {"fun": 10}, "d", 0.0, 1],
	# --- Moebel
	["bed", "Bett „Traumwerk“ 140×200", "furniture", 399.00, "Stück", {"energy": 5}, "d", 0.0, 1],
	["sofa", "Sofa „Wolke“", "furniture", 699.00, "Stück", {"fun": 10}, "d", 0.0, 2],
	["dining_set", "Esstisch mit vier Stühlen", "furniture", 349.00, "Set", {"social": 5}, "d", 0.0, 1],
	["wardrobe", "Kleiderschrank „Raumglück“", "furniture", 279.00, "Stück", {}, "d", 0.0, 1],
	["desk", "Schreibtisch „Wohnwerk“", "furniture", 189.00, "Stück", {}, "d", 0.0, 1],
	["floor_lamp", "Stehleuchte", "furniture", 59.00, "Stück", {"fun": 2}, "d", 0.0, 1],
	# --- Baumarkt
	["toolbox", "Werkzeugkoffer „BauProfi“", "hardware", 79.00, "Koffer", {}, "d", 0.0, 1],
	["wall_paint", "Wandfarbe (10 l)", "hardware", 39.00, "Eimer", {"fun": 2}, "c", 0.0, 1],
	["cordless_drill", "Akkuschrauber", "hardware", 99.00, "Stück", {}, "d", 0.0, 1],
	["houseplant", "Zimmerpflanze", "hardware", 14.00, "Topf", {"fun": 3}, "d", 0.0, 1],
	["bicycle", "Fahrrad „Stadtflitzer“", "hardware", 449.00, "Stück", {"fun": 6, "health": 2}, "v", 0.0, 1],
	["light_bulbs", "LED-Lampen (4er)", "hardware", 9.90, "Packung", {}, "c", 0.0, 1],
	# --- Gebrauchtwaren
	["used_clothes", "Gebrauchte Kleidung (Kiste)", "secondhand", 6.00, "Kiste", {"fun": 2, "hygiene": 4}, "d", 0.0, 0],
	["used_phone", "Gebrauchtes Smartphone", "secondhand", 59.00, "Stück", {"fun": 6, "social": 3}, "d", 0.0, 0],
	["used_armchair", "Gebrauchter Sessel", "secondhand", 35.00, "Stück", {"fun": 3}, "d", 0.0, 0],
	["vinyl", "Schallplatte", "secondhand", 8.00, "Stück", {"fun": 5}, "d", 0.0, 0],
	["used_bike", "Gebrauchtes Fahrrad", "secondhand", 90.00, "Stück", {"fun": 4}, "v", 0.0, 0],
	["old_books", "Bücherkiste", "secondhand", 5.00, "Kiste", {"fun": 6}, "d", 0.0, 0],
	# --- Dienstleistungen (Fitness, Kino)
	["gym_day", "Tageskarte Fitnessstudio", "service", 9.00, "Karte", {"fun": 6, "health": 4, "energy": -10, "hygiene": -15}, "s", 0.0, 1],
	["gym_week", "Wochenkarte Fitnessstudio", "service", 29.00, "Karte", {"fun": 6, "health": 4, "energy": -10, "hygiene": -15}, "s", 0.0, 2],
	["protein_shake", "Proteinshake", "service", 3.90, "Becher", {"hunger": 12, "thirst": 20}, "s", 0.0, 1],
	["cinema_ticket", "Kinokarte", "service", 11.50, "Karte", {"fun": 30, "social": 5}, "s", 0.0, 1],
	["cinema_premium", "Premium-Kinosessel", "service", 18.00, "Karte", {"fun": 36, "social": 5}, "s", 0.0, 2],
	["popcorn", "Popcorn (groß)", "service", 5.50, "Eimer", {"hunger": 10, "fun": 4}, "s", 0.0, 1],
	# --- Tankstelle
	["fuel", "Benzin „PetroStar“ (10 l)", "fuel", 17.90, "Füllung", {}, "c", 0.0, 1],
	["car_wash", "Autowäsche Premium", "fuel", 12.00, "Wäsche", {}, "s", 0.0, 1],
	# --- Fahrzeuge
	["car_used", "Gebrauchter Kleinwagen „Flitzer“", "vehicle", 3900.00, "Fahrzeug", {"fun": 10}, "v", 0.0, 0],
	["car_small", "Kleinwagen „Stadtfloh“", "vehicle", 14900.00, "Fahrzeug", {"fun": 12}, "v", 0.0, 1],
	["car_compact", "Kompaktwagen „Pendler“", "vehicle", 24900.00, "Fahrzeug", {"fun": 14}, "v", 0.0, 1],
	["car_sedan", "Limousine „Comtesse“", "vehicle", 42900.00, "Fahrzeug", {"fun": 18}, "v", 0.0, 2],
	["car_suv", "SUV „Terrano X“", "vehicle", 54900.00, "Fahrzeug", {"fun": 18}, "v", 0.0, 2],
	["car_sports", "Sportwagen „Furia GT“", "vehicle", 119000.00, "Fahrzeug", {"fun": 35}, "v", 0.0, 3],
	["car_luxury", "Luxuslimousine „Imperator“", "vehicle", 189000.00, "Fahrzeug", {"fun": 30}, "v", 0.0, 3],
	["car_hyper", "Supersportwagen „Vanari Furio“", "vehicle", 398000.00, "Fahrzeug", {"fun": 45}, "v", 0.0, 3],
	["car_coupe", "Sportcoupé „Vanari Lucente“", "vehicle", 212000.00, "Fahrzeug", {"fun": 38}, "v", 0.0, 3],
	["car_muscle", "Muscle-Car „Kastor Vento“", "vehicle", 58000.00, "Fahrzeug", {"fun": 32}, "v", 0.0, 2],
	["car_classic", "Oldtimer „Aurel Saluto“", "vehicle", 64000.00, "Fahrzeug", {"fun": 34}, "v", 0.0, 2],
	["car_crossover", "Geländewagen „Borek Trail“", "vehicle", 29500.00, "Fahrzeug", {"fun": 20}, "v", 0.0, 1],
	["scooter", "Motorroller „Vespina“", "vehicle", 2900.00, "Fahrzeug", {"fun": 10}, "v", 0.0, 1],
	["motorcycle", "Motorrad „Donner 700“", "vehicle", 8900.00, "Fahrzeug", {"fun": 20}, "v", 0.0, 2],
	# --- Marktwaren
	["flowers", "Blumenstrauß", "market", 12.00, "Strauß", {"fun": 5, "social": 8}, "c", 0.0, 1],
	["honey", "Honig vom Imker", "market", 6.50, "Glas", {"hunger": 6, "health": 1}, "c", 1.0, 1],
	["fresh_fish", "Frischer Fisch", "market", 9.00, "Stück", {"hunger": 35, "health": 2}, "c", 2.0, 1],
	["farm_cheese", "Bergkäse vom Hof", "market", 5.80, "Stück", {"hunger": 15, "fun": 2}, "c", 1.5, 1],
	["bao_buns", "Bao-Brötchen", "market", 5.50, "Portion", {"hunger": 30, "fun": 4}, "s", 0.0, 1],
	["skewers", "Grillspieße", "market", 6.00, "Portion", {"hunger": 35, "fun": 4}, "s", 0.0, 1],
	["street_noodles", "Wok-Nudeln", "market", 6.80, "Portion", {"hunger": 45, "fun": 3}, "s", 0.0, 1],
	# --- Feinkost (Luxusmarkt)
	["champagne", "Champagner „Étoile“", "gourmet", 79.00, "Flasche", {"fun": 20, "social": 10}, "c", 0.0, 3],
	["truffle_oil", "Trüffelöl", "gourmet", 39.00, "Flasche", {"fun": 6}, "c", 1.0, 3],
	["caviar", "Kaviar (50 g)", "gourmet", 120.00, "Dose", {"hunger": 10, "fun": 20}, "c", 0.0, 3],
	["gourmet_basket", "Feinkostkorb „Goldufer“", "gourmet", 149.00, "Korb", {"hunger": 30, "fun": 15}, "c", 4.0, 3],
]

## Sortiment je Geschaeftsart (BUSINESS_TYPES aus CityCatalog).
const SHOP_ASSORTMENT := {
	"supermarket": ["bread", "rolls", "apples", "bananas", "vegetables", "salad", "potatoes", "tomatoes",
		"sausages", "chicken", "ham", "minced", "milk", "eggs", "cheese", "butter", "pasta", "rice", "cereal",
		"frozen_pizza", "canned_soup", "ready_meal", "instant_noodles", "coffee_beans", "tea", "chocolate",
		"chips", "baby_food", "water", "juice", "cola", "energy_drink", "beer", "wine", "shower_gel",
		"toilet_paper", "detergent", "toothpaste", "diapers", "flowers", "light_bulbs"],
	"grocery": ["bread", "rolls", "apples", "bananas", "potatoes", "milk", "eggs", "cheese", "butter", "pasta",
		"rice", "canned_soup", "ready_meal", "instant_noodles", "coffee_beans", "chocolate", "baby_food",
		"water", "juice", "cola", "beer", "shower_gel", "toilet_paper", "toothpaste"],
	"produce": ["apples", "bananas", "vegetables", "salad", "potatoes", "tomatoes", "berries", "juice", "honey"],
	"bakery": ["bread", "rolls", "pretzel", "croissant", "cake_slice", "layer_cake", "bakery_coffee", "ciabatta"],
	"butcher": ["sausages", "steak", "chicken", "ham", "minced"],
	"convenience": ["rolls", "chocolate", "chips", "instant_noodles", "ready_meal", "canned_soup", "cola", "water",
		"energy_drink", "beer", "ciabatta", "toilet_paper", "toothpaste", "bakery_coffee"],
	"clothing": ["tshirt", "jeans", "sweater", "winter_jacket", "sneakers", "dress", "business_suit", "work_boots", "kids_clothes"],
	"electronics": ["phone_basic", "phone_premium", "laptop", "television", "game_console", "headphones"],
	"furniture": ["bed", "sofa", "dining_set", "wardrobe", "desk", "floor_lamp"],
	"hardware": ["toolbox", "wall_paint", "cordless_drill", "houseplant", "bicycle", "light_bulbs", "work_boots"],
	"pharmacy": ["painkillers", "cold_medicine", "cough_syrup", "vitamins", "first_aid_kit", "sleep_aid",
		"shower_gel", "toothpaste", "diapers"],
	"luxury": ["designer_bag", "luxury_watch", "gold_necklace", "tailored_suit", "perfume", "silk_scarf"],
	"secondhand": ["used_clothes", "used_phone", "used_armchair", "vinyl", "used_bike", "old_books"],
	"market": ["apples", "vegetables", "potatoes", "tomatoes", "berries", "bread", "farm_cheese", "eggs", "honey",
		"fresh_fish", "flowers", "sausages"],
	"flea_market": ["used_clothes", "vinyl", "used_armchair", "used_bike", "used_phone", "old_books", "houseplant"],
	"night_market": ["bao_buns", "skewers", "street_noodles", "juice", "cola", "beer"],
	"luxury_market": ["champagne", "truffle_oil", "caviar", "gourmet_basket", "flowers", "farm_cheese", "wine", "perfume"],
	"restaurant": ["lunch_menu", "pasta_dish", "schnitzel", "fish_dish", "steak_dinner", "dessert", "house_wine", "tasting_menu"],
	"fastfood": ["burger_menu", "doner", "currywurst", "pizza_slice", "noodle_box", "fries", "soft_drink"],
	"cafe": ["cappuccino", "espresso", "latte", "apple_cake", "ciabatta", "breakfast_plate", "croissant"],
	"bar": ["draft_beer", "cocktail", "longdrink", "bar_soft", "shot", "house_wine"],
	"gym": ["gym_day", "gym_week", "protein_shake"],
	"cinema": ["cinema_ticket", "cinema_premium", "popcorn", "soft_drink"],
	"car_dealer": ["car_used", "car_small", "car_compact", "car_crossover", "car_sedan", "car_suv",
		"car_muscle", "car_classic", "car_sports", "car_luxury", "car_coupe", "car_hyper", "scooter", "motorcycle"],
	"gas_station": ["fuel", "car_wash", "cola", "chips", "ciabatta", "bakery_coffee", "water", "energy_drink"],
	"bank": [],
	"real_estate": [],
	"job_center": [],
}

## Lagergroesse (max) nach Preisklasse: teure Gueter werden in kleinen Mengen gefuehrt.
static func default_max_stock(base_price: float, btype: String) -> int:
	if btype in ["car_dealer"]:
		return 2 if base_price > 60000.0 else 4
	if base_price >= 1000.0:
		return 3
	if base_price >= 200.0:
		return 6
	if base_price >= 50.0:
		return 12
	if base_price >= 15.0:
		return 30
	if btype in ["supermarket"]:
		return 120
	if btype in ["market", "grocery", "bakery"]:
		return 70
	return 50


## Wert einer Mahlzeit im Haushaltsvorrat: meals = Grundpreis / MEAL_COST
## (nur fuer Lebensmittel mit Mahlzeitwert).
const MEAL_COST := 2.6

static var GOODS: Dictionary = _build()


static func _build() -> Dictionary:
	var out := {}
	for r in _ROWS:
		var flags: String = r[6]
		var cat: String = r[2]
		out[r[0]] = {
			"id": r[0], "name": r[1], "category": cat, "base_price": float(r[3]), "unit": r[4],
			"needs": r[5], "consumable": flags.contains("c") or flags.contains("s"),
			"durable": flags.contains("d") or flags.contains("v"), "service": flags.contains("s"),
			"vehicle": flags.contains("v"), "icon": CATEGORY_ICONS.get(cat, "tag"),
			"meals": snappedf(float(r[3]) / MEAL_COST, 0.1) if float(r[7]) > 0.0 else 0.0, "tier": int(r[8]), "wholesale": float(WHOLESALE.get(cat, 0.6)),
		}
	return out


static func has(id: String) -> bool:
	return GOODS.has(id)


static func get_good(id: String) -> Dictionary:
	return GOODS.get(id, {})


static func goods_in_category(cat: String) -> Array:
	var out := []
	for id in GOODS:
		if GOODS[id].category == cat:
			out.append(id)
	return out


static func category_label(cat: String) -> String:
	return CATEGORIES.get(cat, cat)


## Fahrzeugdaten fuer Fahrzeug-Gueter (Hoechstgeschwindigkeit m/s, Sitze, Verbrauch).
const VEHICLE_SPECS := {
	"bicycle": {"kind": "bicycle", "top_speed": 7.0, "seats": 1, "fuel_per_km": 0.0},
	"used_bike": {"kind": "bicycle", "top_speed": 6.0, "seats": 1, "fuel_per_km": 0.0},
	"scooter": {"kind": "scooter", "top_speed": 13.0, "seats": 2, "fuel_per_km": 0.03},
	"motorcycle": {"kind": "motorcycle", "top_speed": 50.0, "seats": 2, "fuel_per_km": 0.05},
	"car_used": {"kind": "car", "top_speed": 38.0, "seats": 4, "fuel_per_km": 0.08},
	"car_small": {"kind": "car", "top_speed": 44.0, "seats": 4, "fuel_per_km": 0.06},
	"car_compact": {"kind": "car", "top_speed": 52.0, "seats": 5, "fuel_per_km": 0.065},
	"car_sedan": {"kind": "car", "top_speed": 61.0, "seats": 5, "fuel_per_km": 0.075},
	"car_suv": {"kind": "suv", "top_speed": 55.0, "seats": 5, "fuel_per_km": 0.09},
	"car_sports": {"kind": "sports", "top_speed": 85.0, "seats": 2, "fuel_per_km": 0.12},
	"car_luxury": {"kind": "luxury", "top_speed": 70.0, "seats": 5, "fuel_per_km": 0.11},
	"car_coupe": {"kind": "sports", "top_speed": 88.0, "seats": 4, "fuel_per_km": 0.126},
	"car_muscle": {"kind": "sports", "top_speed": 74.0, "seats": 4, "fuel_per_km": 0.138},
	"car_classic": {"kind": "car", "top_speed": 43.0, "seats": 4, "fuel_per_km": 0.114},
	"car_crossover": {"kind": "suv", "top_speed": 53.0, "seats": 5, "fuel_per_km": 0.076},
}
