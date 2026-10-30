class_name PersonModels
extends RefCounted
## Zugelieferte Personenmodelle (von `tools/prepare_person.gd` aufbereitet).
##
## Sie treten neben die prozedural gebauten Figuren, ersetzen sie aber nicht:
## Der Charaktereditor braucht Alter von 0 bis 100 mit passenden Proportionen,
## und ein fertiges Erwachsenenmodell kann das nicht. Die zugelieferten Modelle
## kommen deshalb dort zum Einsatz, wo es auf Nähe ankommt - in der Geschichte
## und bei einzelnen Passanten in Spielernähe.
##
## Wichtig: Namen echter Personen oder Marken werden nicht verwendet.
## Prüfe vor der Weitergabe des Spiels die Lizenz der Modelle; `license` hält
## fest, was dazu bekannt ist.

## Wie viele aufwendige Figuren gleichzeitig erlaubt sind, je Leistungsstufe.
## Eine Figur kostet rund 66.000 Dreiecke, deshalb sind es in den unteren
## Stufen gar keine.
const MAX_BY_LEVEL := [0, 0, 5, 8, 12]

## Spielarten eines Modells: Hautton, Kleidungsfarben, Statur und Groesse.
##
## Ein fertiges Personenmodell ist teuer - in Dateigroesse wie in Dreiecken.
## Statt mehrere zu brauchen, wird dasselbe Modell beim Aufbauen eingefaerbt:
## Die Texturen bleiben unangetastet, nur die Albedo-Farbe der Materialien
## wird multipliziert. So werden aus einer Datei mehrere Leute.
##
##   skin   Tonung der Hautmaterialien (Std_Skin_*, Std_Nails)
##   cloth  Tonung je Kleidungs-Mesh
##   hair   Tonung der Haare
##   bulk   Breite (1.0 = wie aufbereitet), nur X und Z
##   size   Koerpergroesse als Faktor
const VARIANTS := {
	"bruno": [
		{"name": "Bruno", "skin": Color(1.00, 0.98, 0.96), "hair": Color(1, 1, 1),
			"cloth": {"Jacket": Color(1, 1, 1), "Vest": Color(1, 1, 1),
				"Chachi_Momma_Pants": Color(1, 1, 1), "Sneaker": Color(1, 1, 1)},
			"bulk": 1.00, "size": 1.00},
		{"name": "Teo", "skin": Color(0.52, 0.37, 0.28), "hair": Color(0.45, 0.38, 0.33),
			"cloth": {"Jacket": Color(0.62, 0.74, 0.92), "Vest": Color(0.80, 0.80, 0.84),
				"Chachi_Momma_Pants": Color(0.52, 0.52, 0.58), "Sneaker": Color(0.92, 0.92, 0.90)},
			"bulk": 1.05, "size": 0.98},
		{"name": "Marek", "skin": Color(0.88, 0.80, 0.72), "hair": Color(0.72, 0.62, 0.45),
			"cloth": {"Jacket": Color(0.42, 0.46, 0.40), "Vest": Color(0.30, 0.32, 0.36),
				"Chachi_Momma_Pants": Color(0.34, 0.36, 0.42), "Sneaker": Color(0.40, 0.40, 0.44)},
			"bulk": 1.12, "size": 1.03},
		{"name": "Idris", "skin": Color(0.38, 0.26, 0.20), "hair": Color(0.30, 0.26, 0.24),
			"cloth": {"Jacket": Color(0.86, 0.52, 0.22), "Vest": Color(0.22, 0.24, 0.28),
				"Chachi_Momma_Pants": Color(0.24, 0.26, 0.30), "Sneaker": Color(0.86, 0.86, 0.84)},
			"bulk": 1.02, "size": 1.01},
		{"name": "Sören", "skin": Color(0.96, 0.88, 0.82), "hair": Color(0.92, 0.86, 0.68),
			"cloth": {"Jacket": Color(0.70, 0.22, 0.26), "Vest": Color(0.86, 0.86, 0.88),
				"Chachi_Momma_Pants": Color(0.18, 0.20, 0.26), "Sneaker": Color(0.22, 0.22, 0.26)},
			"bulk": 0.96, "size": 0.96},
	],
}

## Materialnamen, die zur Haut gehoeren.
const SKIN_MATERIALS := ["std_skin", "std_nails"]
const HAIR_MATERIALS := ["hair", "scalp", "eyelash"]

const MODELS := {
	"bruno": {
		"name": "Bruno", "sex": "male", "age": 26.0, "height": 1.82,
		"scene": "res://assets/models/people/mann.scn",
		"animated": true,
		"roles": ["story", "passant", "skin"],
		"source": "Vom Spieler beigesteuertes Modell (FBX, mit Texturen)",
		"license": "ungeklärt – vor einer Veröffentlichung prüfen",
	},
	"nadia": {
		"name": "Nadia", "sex": "female", "age": 31.0, "height": 1.72,
		"scene": "res://assets/models/people/frau.scn",
		# Ohne Skelett: die Figur kann nicht gehen, nur stehen.
		"animated": false,
		"roles": ["stehend"],
		"source": "Vom Spieler beigesteuertes Modell (FBX, ohne Skelett)",
		"license": "ungeklärt – vor einer Veröffentlichung prüfen",
	},
}

## Anzahl der gerade aufgebauten aufwendigen Figuren.
static var live := 0


static func has_model(id: String) -> bool:
	return MODELS.has(id)


static func info(id: String) -> Dictionary:
	return MODELS.get(id, {})


## Alle einsatzbereiten Modelle (Szene vorhanden).
static func available() -> Array:
	var out: Array = []
	for id in MODELS:
		if ResourceLoader.exists(str(MODELS[id].scene)):
			out.append(id)
	return out


## Modelle für eine Rolle ("story", "passant", "stehend", "skin").
## "skin" heißt: taugt als Aussehen der Spielerfigur - dafür braucht das
## Modell ein Skelett, sonst stünde der Spieler bewegungslos da.
static func for_role(role: String) -> Array:
	var out: Array = []
	for id in available():
		if (MODELS[id].roles as Array).has(role):
			out.append(id)
	return out


## Wie viele aufwendige Figuren die aktuelle Leistungsstufe noch zulässt.
static func budget_left() -> int:
	var loop := Engine.get_main_loop()
	var level := 2
	if loop is SceneTree and (loop as SceneTree).root.has_node("Perf"):
		level = int((loop as SceneTree).root.get_node("Perf").level)
	var cap: int = MAX_BY_LEVEL[clampi(level, 0, MAX_BY_LEVEL.size() - 1)]
	return maxi(0, cap - live)


## Spielarten eines Modells.
static func variants(id: String) -> Array:
	return VARIANTS.get(id, [])


## Wie oft eine Spielart gerade in der Stadt unterwegs ist.
static var variant_use := {}


## Wählt die Spielart, die am seltensten zu sehen ist - sonst liefen fünfmal
## dieselben Leute durch die Stadt.
static func pick_variant(id: String) -> int:
	var list := variants(id)
	if list.is_empty():
		return -1
	var best := 0
	var best_n := 1 << 30
	for i in list.size():
		var n: int = int(variant_use.get("%s:%d" % [id, i], 0))
		if n < best_n:
			best_n = n
			best = i
	return best


static func claim_variant(id: String, index: int) -> void:
	if index < 0:
		return
	var key := "%s:%d" % [id, index]
	variant_use[key] = int(variant_use.get(key, 0)) + 1


static func release_variant(id: String, index: int) -> void:
	if index < 0:
		return
	var key := "%s:%d" % [id, index]
	variant_use[key] = maxi(0, int(variant_use.get(key, 0)) - 1)


## Baut die Figur. `count_against_budget` false für Figuren der Geschichte -
## die sind gesetzt und werden nicht wegen der Leistungsstufe weggelassen.
## `variant` -1 wählt selbst die seltenste Spielart.
## Gibt null zurück, wenn die Szene fehlt.
static func build(id: String, count_against_budget := true, variant := -1) -> Node3D:
	var d := info(id)
	if d.is_empty() or not ResourceLoader.exists(str(d.scene)):
		return null
	var list := variants(id)
	if variant < 0:
		variant = pick_variant(id)
	variant = clampi(variant, -1, list.size() - 1)
	var node := DetailedPerson.new()
	if not node.setup(id, d, list[variant] if variant >= 0 else {}):
		node.free()
		return null
	node.variant = variant
	node.counts_against_budget = count_against_budget
	claim_variant(id, variant)
	if count_against_budget:
		live += 1
	return node
