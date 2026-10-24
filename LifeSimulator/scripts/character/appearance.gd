class_name Appearance
extends RefCounted
## Kataloge und Hilfsfunktionen fuer das Aussehen von Figuren.
##
## Alle hier gelisteten Optionen werden von `Humanoid` tatsaechlich
## dargestellt. Der Charaktereditor zeigt ausschliesslich diese Kataloge an;
## neue Optionen muessen daher immer zusammen mit ihrer Darstellung in
## `HumanoidBuilder` ergaenzt werden.
##
## Datenformat (siehe docs/ARCHITECTURE.md, Abschnitt Humanoid):
## [codeblock]
## {skin: Color, hair_style: int, hair_color: Color, eye_color: Color,
##  face: {jaw, nose, brows, cheeks} (0..1),
##  build: {weight, muscle, height} (0..1),
##  top: {style: int, color: Color}, bottom: {style: int, color: Color},
##  shoes: {style: int, color: Color}, accessories: [String], beard: int,
##  freckles: float (0..1), makeup: int, accessory_color: Color}
## [/codeblock]
## `freckles`, `makeup` und `accessory_color` sind optionale Zusaetze; fehlen
## sie, setzt `validate()` Standardwerte.

const MIN_AGE := 0.5
const MAX_AGE := 100.0

## Lebensphasen fuer Anzeige und Schnellauswahl. Das tatsaechliche Alter ist
## frei einstellbar; `default` ist das Alter, das ein Klick auf die Phase setzt.
const LIFE_STAGES := [
	{"id": "baby", "label": "Baby", "min": 0.0, "max": 2.0, "default": 1.0},
	{"id": "toddler", "label": "Kleinkind", "min": 2.0, "max": 4.0, "default": 3.0},
	{"id": "child", "label": "Kind", "min": 4.0, "max": 12.0, "default": 8.0},
	{"id": "teen", "label": "Jugendlicher", "min": 12.0, "max": 18.0, "default": 15.0},
	{"id": "young_adult", "label": "Junger Erwachsener", "min": 18.0, "max": 30.0, "default": 24.0},
	{"id": "adult", "label": "Erwachsener", "min": 30.0, "max": 50.0, "default": 38.0},
	{"id": "older_adult", "label": "Älterer Erwachsener", "min": 50.0, "max": 65.0, "default": 57.0},
	{"id": "senior", "label": "Senior", "min": 65.0, "max": 100.0, "default": 74.0},
]

const BODIES := [
	{"id": "female", "label": "Weiblich"},
	{"id": "male", "label": "Männlich"},
]

# ------------------------------------------------------------------ Frisuren
## volume: Frisur hat Volumen ueber dem Oberkopf (wird unter Kopfbedeckungen
## flach gelegt). long: Haare fallen ueber Hals/Schultern.
const HAIR_STYLES := [
	{"id": 0, "key": "short", "label": "Kurz", "min_age": 0.0},
	{"id": 1, "key": "buzz", "label": "Raspelkurz", "min_age": 0.0},
	{"id": 2, "key": "side_part", "label": "Seitenscheitel", "min_age": 3.0},
	{"id": 3, "key": "long", "label": "Lang glatt", "min_age": 2.0, "long": true},
	{"id": 4, "key": "bob", "label": "Bob", "min_age": 2.0},
	{"id": 5, "key": "ponytail", "label": "Pferdeschwanz", "min_age": 2.0, "volume": true},
	{"id": 6, "key": "bun", "label": "Dutt", "min_age": 3.0, "volume": true},
	{"id": 7, "key": "curly", "label": "Locken", "min_age": 0.0},
	{"id": 8, "key": "afro", "label": "Afro", "min_age": 1.0, "volume": true},
	{"id": 9, "key": "pigtails", "label": "Zöpfe", "min_age": 2.0},
	{"id": 10, "key": "undercut", "label": "Undercut", "min_age": 8.0},
	{"id": 11, "key": "receding", "label": "Geheimratsecken", "min_age": 25.0},
	{"id": 12, "key": "bald", "label": "Glatze", "min_age": 0.0},
	{"id": 13, "key": "long_wavy", "label": "Lang gewellt", "min_age": 4.0, "long": true},
]

const BEARDS := [
	{"id": 0, "key": "none", "label": "Kein Bart", "min_age": 0.0},
	{"id": 1, "key": "stubble", "label": "Dreitagebart", "min_age": 15.0},
	{"id": 2, "key": "moustache", "label": "Schnurrbart", "min_age": 16.0},
	{"id": 3, "key": "goatee", "label": "Kinnbart", "min_age": 16.0},
	{"id": 4, "key": "full", "label": "Vollbart", "min_age": 17.0},
	{"id": 5, "key": "chin_strap", "label": "Kinnriemen", "min_age": 17.0},
]

# ------------------------------------------------------------------ Kleidung
## covers_legs (Oberteil): Teil bedeckt auch die Beine (Strampler).
## dress (Oberteil): Unterteil wird automatisch zur Kleid-Fortsetzung.
## max_age: nur bis zu diesem Alter waehlbar.
const TOPS := [
	{"id": 0, "key": "tshirt", "label": "T-Shirt", "min_age": 1.0},
	{"id": 1, "key": "shirt", "label": "Hemd", "min_age": 3.0},
	{"id": 2, "key": "hoodie", "label": "Kapuzenpullover", "min_age": 2.0},
	{"id": 3, "key": "jacket", "label": "Jacke", "min_age": 2.0},
	{"id": 4, "key": "blouse", "label": "Bluse", "min_age": 6.0},
	{"id": 5, "key": "dress", "label": "Kleid", "min_age": 1.0, "dress": true},
	{"id": 6, "key": "suit", "label": "Sakko mit Hemd", "min_age": 10.0},
	{"id": 7, "key": "tank", "label": "Trägertop", "min_age": 4.0},
	{"id": 8, "key": "sweater", "label": "Pullover", "min_age": 1.0},
	{"id": 9, "key": "onesie", "label": "Strampler", "min_age": 0.0, "max_age": 4.0, "covers_legs": true},
	{"id": 10, "key": "polo", "label": "Poloshirt", "min_age": 4.0},
]

## dress_only: wird nur automatisch zusammen mit einem Kleid verwendet.
const BOTTOMS := [
	{"id": 0, "key": "jeans", "label": "Jeans", "min_age": 1.0},
	{"id": 1, "key": "trousers", "label": "Stoffhose", "min_age": 3.0},
	{"id": 2, "key": "shorts", "label": "Shorts", "min_age": 1.0},
	{"id": 3, "key": "skirt", "label": "Rock", "min_age": 2.0},
	{"id": 4, "key": "long_skirt", "label": "Langer Rock", "min_age": 4.0},
	{"id": 5, "key": "joggers", "label": "Jogginghose", "min_age": 1.0},
	{"id": 6, "key": "leggings", "label": "Leggings", "min_age": 2.0},
	{"id": 7, "key": "dress", "label": "Kleid (Rockteil)", "min_age": 0.0, "dress_only": true},
]

const SHOES := [
	{"id": 0, "key": "sneakers", "label": "Turnschuhe", "min_age": 1.0},
	{"id": 1, "key": "boots", "label": "Stiefel", "min_age": 2.0},
	{"id": 2, "key": "dress_shoes", "label": "Lederschuhe", "min_age": 3.0},
	{"id": 3, "key": "sandals", "label": "Sandalen", "min_age": 1.0},
	{"id": 4, "key": "flats", "label": "Ballerinas", "min_age": 2.0},
	{"id": 5, "key": "booties", "label": "Babyschuhe", "min_age": 0.0, "max_age": 3.0},
	{"id": 6, "key": "barefoot", "label": "Barfuß", "min_age": 0.0},
]

## slot: Accessoires mit gleichem Slot schliessen sich gegenseitig aus.
const ACCESSORIES := [
	{"id": "glasses", "label": "Brille", "slot": "eyes", "min_age": 2.0},
	{"id": "sunglasses", "label": "Sonnenbrille", "slot": "eyes", "min_age": 3.0},
	{"id": "cap", "label": "Basecap", "slot": "head", "min_age": 2.0},
	{"id": "hat", "label": "Hut", "slot": "head", "min_age": 1.0},
	{"id": "beanie", "label": "Mütze", "slot": "head", "min_age": 0.5},
	{"id": "earrings", "label": "Ohrringe", "slot": "ears", "min_age": 3.0},
	{"id": "watch", "label": "Armbanduhr", "slot": "wrist", "min_age": 6.0},
	{"id": "necklace", "label": "Halskette", "slot": "neck", "min_age": 5.0},
	{"id": "scarf", "label": "Schal", "slot": "scarf", "min_age": 2.0},
	{"id": "backpack", "label": "Rucksack", "slot": "back", "min_age": 3.0},
]

const MAKEUP := [
	{"id": 0, "label": "Kein Make-up"},
	{"id": 1, "label": "Natürlich"},
	{"id": 2, "label": "Kräftig"},
]

# ------------------------------------------------------------------ Farben
const SKIN_TONES := [
	{"label": "Porzellan", "color": Color(0.96, 0.84, 0.76)},
	{"label": "Hell", "color": Color(0.94, 0.78, 0.67)},
	{"label": "Hell warm", "color": Color(0.91, 0.74, 0.60)},
	{"label": "Beige", "color": Color(0.86, 0.68, 0.54)},
	{"label": "Oliv", "color": Color(0.78, 0.62, 0.45)},
	{"label": "Goldbraun", "color": Color(0.72, 0.53, 0.37)},
	{"label": "Mittelbraun", "color": Color(0.62, 0.44, 0.30)},
	{"label": "Braun", "color": Color(0.58, 0.40, 0.27)},
	{"label": "Dunkelbraun", "color": Color(0.49, 0.33, 0.22)},
	{"label": "Tiefbraun", "color": Color(0.41, 0.27, 0.18)},
	{"label": "Ebenholz", "color": Color(0.34, 0.22, 0.15)},
]

const HAIR_COLORS := [
	{"label": "Schwarz", "color": Color(0.05, 0.045, 0.045)},
	{"label": "Dunkelbraun", "color": Color(0.14, 0.09, 0.06)},
	{"label": "Braun", "color": Color(0.27, 0.17, 0.10)},
	{"label": "Hellbraun", "color": Color(0.45, 0.31, 0.18)},
	{"label": "Dunkelblond", "color": Color(0.58, 0.44, 0.27)},
	{"label": "Blond", "color": Color(0.82, 0.68, 0.43)},
	{"label": "Platinblond", "color": Color(0.92, 0.86, 0.72)},
	{"label": "Rotblond", "color": Color(0.72, 0.42, 0.22)},
	{"label": "Rot", "color": Color(0.55, 0.20, 0.08)},
	{"label": "Kastanie", "color": Color(0.35, 0.14, 0.08)},
	{"label": "Grau", "color": Color(0.62, 0.62, 0.62)},
	{"label": "Weiß", "color": Color(0.92, 0.92, 0.90)},
	{"label": "Blau", "color": Color(0.15, 0.30, 0.70)},
	{"label": "Rosa", "color": Color(0.90, 0.45, 0.65)},
	{"label": "Grün", "color": Color(0.20, 0.55, 0.35)},
	{"label": "Violett", "color": Color(0.45, 0.25, 0.65)},
]

const EYE_COLORS := [
	{"label": "Dunkelbraun", "color": Color(0.20, 0.11, 0.06)},
	{"label": "Braun", "color": Color(0.38, 0.22, 0.10)},
	{"label": "Haselnuss", "color": Color(0.50, 0.38, 0.18)},
	{"label": "Bernstein", "color": Color(0.70, 0.48, 0.15)},
	{"label": "Grün", "color": Color(0.30, 0.48, 0.25)},
	{"label": "Graugrün", "color": Color(0.40, 0.50, 0.43)},
	{"label": "Blau", "color": Color(0.25, 0.45, 0.75)},
	{"label": "Hellblau", "color": Color(0.50, 0.68, 0.85)},
	{"label": "Grau", "color": Color(0.52, 0.56, 0.60)},
]

const CLOTH_COLORS := [
	{"label": "Weiß", "color": Color(0.92, 0.92, 0.90)},
	{"label": "Hellgrau", "color": Color(0.70, 0.71, 0.72)},
	{"label": "Anthrazit", "color": Color(0.22, 0.23, 0.25)},
	{"label": "Schwarz", "color": Color(0.07, 0.07, 0.08)},
	{"label": "Marine", "color": Color(0.11, 0.16, 0.32)},
	{"label": "Jeansblau", "color": Color(0.20, 0.31, 0.50)},
	{"label": "Himmelblau", "color": Color(0.47, 0.66, 0.85)},
	{"label": "Petrol", "color": Color(0.07, 0.38, 0.42)},
	{"label": "Grün", "color": Color(0.20, 0.45, 0.27)},
	{"label": "Oliv", "color": Color(0.36, 0.38, 0.20)},
	{"label": "Senf", "color": Color(0.80, 0.62, 0.18)},
	{"label": "Orange", "color": Color(0.88, 0.45, 0.15)},
	{"label": "Rot", "color": Color(0.72, 0.13, 0.13)},
	{"label": "Bordeaux", "color": Color(0.42, 0.09, 0.15)},
	{"label": "Rosa", "color": Color(0.92, 0.62, 0.70)},
	{"label": "Flieder", "color": Color(0.62, 0.52, 0.78)},
	{"label": "Beige", "color": Color(0.80, 0.71, 0.56)},
	{"label": "Braun", "color": Color(0.38, 0.25, 0.15)},
]

const FIRST_NAMES_FEMALE := ["Anna", "Lena", "Mia", "Emma", "Sophie", "Laura", "Julia", "Lea", "Hannah",
	"Marie", "Clara", "Nina", "Sarah", "Katharina", "Elif", "Amira", "Zoe", "Ida", "Paula", "Greta",
	"Johanna", "Mila", "Leonie", "Frieda", "Aylin", "Selin", "Helena", "Ronja", "Charlotte", "Ella"]
const FIRST_NAMES_MALE := ["Lukas", "Jonas", "Leon", "Finn", "Paul", "Elias", "Noah", "Felix", "Max",
	"Ben", "Tim", "David", "Jan", "Moritz", "Emil", "Can", "Yusuf", "Mats", "Theo", "Anton",
	"Jakob", "Samuel", "Niklas", "Tobias", "Milan", "Deniz", "Oskar", "Henri", "Julian", "Malte"]
const LAST_NAMES := ["Berger", "Müller", "Schmidt", "Schneider", "Fischer", "Weber", "Wagner", "Becker",
	"Hoffmann", "Koch", "Richter", "Klein", "Wolf", "Neumann", "Schwarz", "Zimmermann", "Braun",
	"Krüger", "Hartmann", "Lange", "Werner", "Krause", "Lehmann", "Köhler", "Kaya", "Yilmaz",
	"Nowak", "Petrović", "Sommer", "Vogel", "Brandt", "Lorenz", "Winter", "Albrecht", "Seidel"]


# ================================================================== Lebensphasen

## Kennung der Lebensphase fuer ein Alter in Jahren.
static func life_stage(age: float) -> String:
	for st in LIFE_STAGES:
		if age < float(st.max):
			return str(st.id)
	return "senior"


## Deutscher Anzeigename der Lebensphase.
static func life_stage_label(age_or_id) -> String:
	var id: String = life_stage(float(age_or_id)) if typeof(age_or_id) != TYPE_STRING else str(age_or_id)
	for st in LIFE_STAGES:
		if st.id == id:
			return str(st.label)
	return ""


static func stage_info(id: String) -> Dictionary:
	for st in LIFE_STAGES:
		if st.id == id:
			return st
	return {}


## Lesbare Altersangabe, z. B. "8 Monate", "1 Jahr", "34 Jahre".
static func age_text(age: float) -> String:
	if age < 2.0:
		var months := int(round(age * 12.0))
		if months < 24:
			return "%d Monat%s" % [months, "" if months == 1 else "e"]
	var y := int(floor(age))
	return "%d Jahr%s" % [y, "" if y == 1 else "e"]


# ================================================================== Kataloge

## Optionen eines Katalogs, die fuer Alter/Koerper waehlbar sind.
static func options_for(catalog: Array, age: float, include_hidden := false) -> Array:
	var out := []
	for o in catalog:
		if age < float(o.get("min_age", 0.0)):
			continue
		if o.has("max_age") and age >= float(o.max_age):
			continue
		if not include_hidden and o.get("dress_only", false):
			continue
		out.append(o)
	return out


static func is_option_allowed(catalog: Array, id, age: float) -> bool:
	for o in options_for(catalog, age, true):
		if o.id == id:
			return true
	return false


static func find_option(catalog: Array, id) -> Dictionary:
	for o in catalog:
		if o.id == id:
			return o
	return {}


static func key_of(catalog: Array, id) -> String:
	return str(find_option(catalog, id).get("key", ""))


static func id_of_key(catalog: Array, key: String) -> int:
	for o in catalog:
		if o.get("key", "") == key:
			return int(o.id)
	return 0


## true, wenn Babys/Kleinkinder automatisch einen Strampler tragen.
static func forces_onesie(age: float) -> bool:
	return age < 2.0


# ================================================================== Standardwerte

## Standardaussehen fuer Koerper und Alter.
static func defaults(body: String, age: float) -> Dictionary:
	var fem := body == "female"
	var d := {
		"skin": SKIN_TONES[2].color,
		"hair_style": id_of_key(HAIR_STYLES, "long" if fem else "short"),
		"hair_color": HAIR_COLORS[2].color,
		"eye_color": EYE_COLORS[1].color,
		"face": {"jaw": 0.5, "nose": 0.5, "brows": 0.5, "cheeks": 0.5},
		"build": {"weight": 0.45, "muscle": 0.45, "height": 0.5},
		"top": {"style": id_of_key(TOPS, "tshirt"), "color": CLOTH_COLORS[6].color if fem else CLOTH_COLORS[4].color},
		"bottom": {"style": id_of_key(BOTTOMS, "jeans"), "color": CLOTH_COLORS[5].color},
		"shoes": {"style": id_of_key(SHOES, "sneakers"), "color": CLOTH_COLORS[0].color},
		"accessories": [],
		"beard": 0,
		"freckles": 0.0,
		"makeup": 0,
		"accessory_color": CLOTH_COLORS[2].color,
	}
	if age < 4.0:
		d.hair_style = id_of_key(HAIR_STYLES, "short")
	if age < 2.0:
		d.top = {"style": id_of_key(TOPS, "onesie"), "color": Color(0.62, 0.78, 0.90) if not fem else Color(0.95, 0.75, 0.80)}
		d.shoes = {"style": id_of_key(SHOES, "booties"), "color": Color(0.95, 0.95, 0.92)}
	elif age >= 65.0:
		d.top = {"style": id_of_key(TOPS, "sweater"), "color": CLOTH_COLORS[16].color}
		d.bottom = {"style": id_of_key(BOTTOMS, "trousers"), "color": CLOTH_COLORS[2].color}
		d.shoes = {"style": id_of_key(SHOES, "dress_shoes"), "color": CLOTH_COLORS[17].color}
		if fem:
			d.hair_style = id_of_key(HAIR_STYLES, "bob")
	return validate(d, age)


# ================================================================== Zufall

## Zufaelliges, stimmiges Aussehen fuer Koerper und Alter.
static func random(rng: RandomNumberGenerator, body: String, age: float) -> Dictionary:
	var fem := body == "female"
	var d := defaults(body, age)
	d.skin = _jitter(rng, _pick(rng, SKIN_TONES).color, 0.02)
	# Haarfarbe: natuerliche Farben deutlich haeufiger
	var hc: Color
	if rng.randf() < 0.06 and age >= 12.0:
		hc = HAIR_COLORS[12 + rng.randi_range(0, 3)].color
	else:
		var natural := [0, 0, 1, 1, 1, 2, 2, 3, 4, 5, 6, 7, 8, 9]
		hc = HAIR_COLORS[natural[rng.randi_range(0, natural.size() - 1)]].color
		if d.skin.get_luminance() < 0.35 and rng.randf() < 0.75:
			hc = HAIR_COLORS[rng.randi_range(0, 1)].color
	d.hair_color = _jitter(rng, hc, 0.02)
	d.eye_color = _pick(rng, EYE_COLORS).color
	if d.skin.get_luminance() < 0.4 and rng.randf() < 0.8:
		d.eye_color = EYE_COLORS[rng.randi_range(0, 1)].color
	d.face = {"jaw": _gauss01(rng), "nose": _gauss01(rng), "brows": _gauss01(rng), "cheeks": _gauss01(rng)}
	d.build = {"weight": clampf(_gauss01(rng) * 0.9 + (0.08 if age > 40.0 else 0.0), 0.0, 1.0),
		"muscle": _gauss01(rng), "height": _gauss01(rng)}
	# Frisur
	var styles := options_for(HAIR_STYLES, age)
	var weights := []
	for s in styles:
		var k: String = s.key
		var w := 1.0
		match k:
			"long", "long_wavy", "bob", "ponytail", "bun", "pigtails":
				w = 3.0 if fem else 0.25
			"short", "side_part", "undercut", "buzz":
				w = 0.6 if fem else 3.0
			"receding":
				w = 0.0 if fem else (3.0 if age > 45.0 else 0.8)
			"bald":
				w = 0.05 if fem else (1.5 if age > 50.0 else 0.3)
			"afro":
				w = 1.2 if d.skin.get_luminance() < 0.4 else 0.15
			"curly":
				w = 1.0
		if age < 1.5 and k != "short" and k != "buzz" and k != "bald" and k != "curly":
			w *= 0.1
		weights.append(w)
	d.hair_style = int(styles[_weighted(rng, weights)].id)
	# Bart
	d.beard = 0
	if not fem and age >= 17.0 and rng.randf() < 0.45:
		d.beard = rng.randi_range(1, BEARDS.size() - 1)
	d.freckles = rng.randf_range(0.3, 0.9) if rng.randf() < 0.15 else 0.0
	d.makeup = 0
	if fem and age >= 15.0 and rng.randf() < 0.55:
		d.makeup = 1 if rng.randf() < 0.75 else 2
	# Kleidung
	if not forces_onesie(age):
		var tops := options_for(TOPS, age)
		var tw := []
		for t in tops:
			var k: String = t.key
			var w := 1.0
			match k:
				"dress", "blouse":
					w = 1.4 if fem else 0.0
				"suit":
					w = 0.7 if age >= 22.0 else 0.1
				"onesie":
					w = 0.5
				"tank":
					w = 0.6
			tw.append(w)
		d.top = {"style": int(tops[_weighted(rng, tw)].id), "color": _pick_cloth(rng)}
		var bottoms := options_for(BOTTOMS, age)
		var bw := []
		for b in bottoms:
			var k: String = b.key
			var w := 1.0
			match k:
				"skirt", "long_skirt", "leggings":
					w = 1.0 if fem else 0.0
				"jeans":
					w = 2.0
			bw.append(w)
		var bottom_color: Color = CLOTH_COLORS[rng.randi_range(2, 5)].color if rng.randf() < 0.6 else _pick_cloth(rng)
		d.bottom = {"style": int(bottoms[_weighted(rng, bw)].id), "color": bottom_color}
		if key_of(TOPS, d.top.style) == "suit":
			d.bottom = {"style": id_of_key(BOTTOMS, "trousers"), "color": d.top.color}
		var shoes := options_for(SHOES, age)
		var sw := []
		for s in shoes:
			var k: String = s.key
			var w := 1.0
			match k:
				"flats":
					w = 1.0 if fem else 0.0
				"sneakers":
					w = 3.0
				"barefoot":
					w = 0.05
				"booties":
					w = 2.0
			sw.append(w)
		var shoe_colors := [CLOTH_COLORS[0].color, CLOTH_COLORS[3].color, CLOTH_COLORS[17].color, CLOTH_COLORS[2].color]
		d.shoes = {"style": int(shoes[_weighted(rng, sw)].id), "color": shoe_colors[rng.randi_range(0, 3)] if rng.randf() < 0.75 else _pick_cloth(rng)}
	else:
		d.top.color = _pick_cloth(rng).lightened(0.35)
	# Accessoires
	d.accessories = []
	var acc := options_for(ACCESSORIES, age)
	acc.shuffle()
	var count := 0
	for a in acc:
		var p := 0.12
		match str(a.id):
			"glasses":
				p = 0.3 if age > 45.0 else 0.15
			"earrings":
				p = 0.45 if fem else 0.05
			"watch":
				p = 0.3
			"necklace":
				p = 0.25 if fem else 0.05
			"backpack":
				p = 0.35 if age >= 6.0 and age < 30.0 else 0.08
		if rng.randf() < p and count < 3:
			d.accessories.append(str(a.id))
			count += 1
	d.accessory_color = _pick_cloth(rng)
	return validate(d, age)


## Zufaelliger deutscher Vor- und Nachname.
static func random_name(rng: RandomNumberGenerator, body: String) -> String:
	var firsts: Array = FIRST_NAMES_FEMALE if body == "female" else FIRST_NAMES_MALE
	return "%s %s" % [firsts[rng.randi_range(0, firsts.size() - 1)], LAST_NAMES[rng.randi_range(0, LAST_NAMES.size() - 1)]]


static func _pick(rng: RandomNumberGenerator, arr: Array):
	return arr[rng.randi_range(0, arr.size() - 1)]


static func _pick_cloth(rng: RandomNumberGenerator) -> Color:
	return _jitter(rng, _pick(rng, CLOTH_COLORS).color, 0.03)


static func _jitter(rng: RandomNumberGenerator, c: Color, amount: float) -> Color:
	return Color(clampf(c.r + rng.randf_range(-amount, amount), 0.0, 1.0),
		clampf(c.g + rng.randf_range(-amount, amount), 0.0, 1.0),
		clampf(c.b + rng.randf_range(-amount, amount), 0.0, 1.0), 1.0)


static func _gauss01(rng: RandomNumberGenerator) -> float:
	return clampf(rng.randfn(0.5, 0.18), 0.0, 1.0)


static func _weighted(rng: RandomNumberGenerator, weights: Array) -> int:
	var total := 0.0
	for w in weights:
		total += float(w)
	if total <= 0.0:
		return 0
	var r := rng.randf() * total
	for i in weights.size():
		r -= float(weights[i])
		if r <= 0.0:
			return i
	return weights.size() - 1


# ================================================================== Validierung

## Liefert eine vollstaendige, gueltige Kopie. Fehlende Schluessel werden
## ergaenzt, Zahlen begrenzt und Farben aus Strings/Arrays umgewandelt.
## Ist `age` angegeben (>= 0), werden altersabhaengige Regeln angewendet
## (Strampler fuer Babys, kein Bart bei Kindern, gesperrte Optionen).
static func validate(d, age := -1.0) -> Dictionary:
	var src: Dictionary = d if d is Dictionary else {}
	var out := {}
	out.skin = _color(src.get("skin"), SKIN_TONES[2].color)
	out.hair_style = _int_option(HAIR_STYLES, src.get("hair_style"), 0)
	out.hair_color = _color(src.get("hair_color"), HAIR_COLORS[2].color)
	out.eye_color = _color(src.get("eye_color"), EYE_COLORS[1].color)
	var face: Dictionary = src.get("face", {}) if src.get("face") is Dictionary else {}
	out.face = {}
	for k in ["jaw", "nose", "brows", "cheeks"]:
		out.face[k] = _unit(face.get(k), 0.5)
	var build: Dictionary = src.get("build", {}) if src.get("build") is Dictionary else {}
	out.build = {}
	for k in ["weight", "muscle", "height"]:
		out.build[k] = _unit(build.get(k), 0.5)
	out.top = _piece(src.get("top"), TOPS, 0, CLOTH_COLORS[4].color)
	out.bottom = _piece(src.get("bottom"), BOTTOMS, 0, CLOTH_COLORS[5].color)
	out.shoes = _piece(src.get("shoes"), SHOES, 0, CLOTH_COLORS[0].color)
	out.beard = _int_option(BEARDS, src.get("beard"), 0)
	out.freckles = _unit(src.get("freckles"), 0.0)
	out.makeup = clampi(int(src.get("makeup", 0)) if _is_num(src.get("makeup")) else 0, 0, MAKEUP.size() - 1)
	out.accessory_color = _color(src.get("accessory_color"), CLOTH_COLORS[2].color)
	# Accessoires: bekannte IDs, ohne Duplikate, ein Teil pro Slot
	var acc: Array = []
	var slots := {}
	var raw = src.get("accessories", [])
	if raw is Array or raw is PackedStringArray:
		for a in raw:
			var id := str(a)
			var opt := find_option(ACCESSORIES, id)
			if opt.is_empty() or acc.has(id):
				continue
			if slots.has(opt.slot):
				continue
			slots[opt.slot] = true
			acc.append(id)
	out.accessories = acc
	# Kleid: Unterteil wird zur Kleid-Fortsetzung, umgekehrt nur mit Kleid
	var dress_top: bool = find_option(TOPS, out.top.style).get("dress", false)
	var dress_bottom_id := id_of_key(BOTTOMS, "dress")
	if dress_top:
		out.bottom.style = dress_bottom_id
		out.bottom.color = out.top.color
	elif out.bottom.style == dress_bottom_id:
		out.bottom.style = 0
	if age >= 0.0:
		_apply_age_rules(out, age)
	return out


static func _apply_age_rules(out: Dictionary, age: float) -> void:
	var onesie_id := id_of_key(TOPS, "onesie")
	if forces_onesie(age):
		out.top.style = onesie_id
		if out.shoes.style != id_of_key(SHOES, "barefoot"):
			out.shoes.style = id_of_key(SHOES, "booties")
	if not is_option_allowed(TOPS, out.top.style, age):
		out.top.style = onesie_id if age < 1.0 else id_of_key(TOPS, "tshirt")
	if out.top.style == onesie_id:
		out.bottom.style = 0
	var dress_bottom_id := id_of_key(BOTTOMS, "dress")
	if out.bottom.style != dress_bottom_id and not is_option_allowed(BOTTOMS, out.bottom.style, age):
		out.bottom.style = id_of_key(BOTTOMS, "joggers") if age >= 1.0 else 0
	if not is_option_allowed(SHOES, out.shoes.style, age):
		out.shoes.style = id_of_key(SHOES, "booties") if age < 3.0 else id_of_key(SHOES, "sneakers")
	if not is_option_allowed(HAIR_STYLES, out.hair_style, age):
		out.hair_style = id_of_key(HAIR_STYLES, "short")
	if not is_option_allowed(BEARDS, out.beard, age):
		out.beard = 0
	if age < 13.0:
		out.makeup = 0
	var acc: Array = []
	for a in out.accessories:
		if is_option_allowed(ACCESSORIES, a, age):
			acc.append(a)
	out.accessories = acc


static func _piece(v, catalog: Array, def_style: int, def_color: Color) -> Dictionary:
	var src: Dictionary = v if v is Dictionary else {}
	return {"style": _int_option(catalog, src.get("style"), def_style), "color": _color(src.get("color"), def_color)}


static func _int_option(catalog: Array, v, def: int) -> int:
	if not _is_num(v):
		return def
	var i := int(v)
	return i if not find_option(catalog, i).is_empty() else def


static func _is_num(v) -> bool:
	return typeof(v) == TYPE_INT or typeof(v) == TYPE_FLOAT


static func _unit(v, def: float) -> float:
	if not _is_num(v):
		return def
	var f := float(v)
	if is_nan(f):
		return def
	return clampf(f, 0.0, 1.0)


## Wandelt Color, "#rrggbb", "(r, g, b, a)" oder [r, g, b] in eine Farbe um.
static func _color(v, def: Color) -> Color:
	var c := def
	match typeof(v):
		TYPE_COLOR:
			c = v
		TYPE_STRING, TYPE_STRING_NAME:
			var s := str(v).strip_edges()
			if s.begins_with("("):
				var parts := s.trim_prefix("(").trim_suffix(")").split(",")
				if parts.size() >= 3:
					c = Color(parts[0].to_float(), parts[1].to_float(), parts[2].to_float())
			elif Color.html_is_valid(s):
				c = Color.html(s)
		TYPE_ARRAY, TYPE_PACKED_FLOAT32_ARRAY, TYPE_PACKED_FLOAT64_ARRAY:
			if v.size() >= 3:
				c = Color(float(v[0]), float(v[1]), float(v[2]))
	return Color(clampf(c.r, 0.0, 1.0), clampf(c.g, 0.0, 1.0), clampf(c.b, 0.0, 1.0), 1.0)
