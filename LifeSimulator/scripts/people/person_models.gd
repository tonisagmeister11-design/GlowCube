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
## Eine Figur kostet so viel wie ein halbes Spielerfahrzeug, deshalb sind es
## in den unteren Stufen gar keine.
const MAX_BY_LEVEL := [0, 0, 3, 6, 10]

const MODELS := {
	"bruno": {
		"name": "Bruno", "sex": "male", "age": 26.0, "height": 1.82,
		"scene": "res://assets/models/people/mann.scn",
		"animated": true,
		"roles": ["story", "passant"],
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


## Modelle für eine Rolle ("story", "passant", "stehend").
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


## Baut die Figur. `count_against_budget` false für Figuren der Geschichte -
## die sind gesetzt und werden nicht wegen der Leistungsstufe weggelassen.
## Gibt null zurück, wenn die Szene fehlt.
static func build(id: String, count_against_budget := true) -> Node3D:
	var d := info(id)
	if d.is_empty() or not ResourceLoader.exists(str(d.scene)):
		return null
	var node := DetailedPerson.new()
	if not node.setup(id, d):
		node.free()
		return null
	node.counts_against_budget = count_against_budget
	if count_against_budget:
		live += 1
	return node
