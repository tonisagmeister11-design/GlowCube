class_name ImportedVehicles
extends RefCounted
## Fahrzeuge aus zugelieferten 3D-Modellen (von `tools/prepare_vehicle.gd`
## aufbereitet). Sie treten neben die prozedural gebauten Fahrzeuge: Wo eine
## Szene hinterlegt ist, wird sie verwendet, sonst baut VehicleMeshBuilder
## weiterhin das einfache Modell.
##
## Wichtig: Modellnamen echter Hersteller werden im Spiel nicht verwendet.
## Jedes Fahrzeug bekommt einen eigenen, erfundenen Namen (siehe `name`).
## Prüfe vor der Weitergabe des Spiels, ob die Lizenz des Modells das erlaubt;
## `license` hält fest, was dazu bekannt ist.

## model_id -> Daten. `scene` ist die aufbereitete Szene, `far_only_above`
## schaltet ab dieser Entfernung (Meter) auf die vereinfachte Fassung um.
const VEHICLES := {
	"vanari_furio": {
		"name": "Vanari Furio", "brand": "Vanari", "type": "sports", "category": "Sportwagen",
		"price": 398000.0, "top_speed": 345.0, "accel": 2.6, "seats": 2, "fuel": 15.2,
		"scene": "res://assets/models/vehicles/sportwagen.scn",
		"source": "Vom Spieler beigesteuertes Modell (FBX)",
		"license": "ungeklärt – vor einer Veröffentlichung prüfen",
		"far_only_above": 70.0,
		"paint_group": "Body_paint",
	},
}


static func has_model(model_id: String) -> bool:
	return VEHICLES.has(model_id)


static func info(model_id: String) -> Dictionary:
	return VEHICLES.get(model_id, {})


## Alle einsatzbereiten Modelle (Szene vorhanden).
static func available() -> Array:
	var out := []
	for id in VEHICLES:
		if ResourceLoader.exists(str(VEHICLES[id].scene)):
			out.append(id)
	return out


## Baut das Fahrzeug. Gibt null zurück, wenn die Szene fehlt – der Aufrufer
## nutzt dann das prozedurale Modell.
static func build(model_id: String, color := Color.WHITE) -> Node3D:
	var d := info(model_id)
	if d.is_empty() or not ResourceLoader.exists(str(d.scene)):
		return null
	var node: Node3D = (load(str(d.scene)) as PackedScene).instantiate()
	node.name = "ImportedVehicle"
	node.set_meta("model_id", model_id)
	_apply_paint(node, str(d.get("paint_group", "")), color)
	_apply_detail(node)
	return node


## Lackfarbe auf die Lackgruppe übertragen.
static func _apply_paint(node: Node3D, group: String, color: Color) -> void:
	if group == "" or color == Color.WHITE:
		return
	var mi := node.find_child(group, true, false)
	if mi is MeshInstance3D:
		var m: StandardMaterial3D = (mi as MeshInstance3D).material_override as StandardMaterial3D
		if m != null:
			var c := m.duplicate() as StandardMaterial3D
			c.albedo_color = color
			(mi as MeshInstance3D).material_override = c


## Detailgrad nach Leistungsstufe: Im Leistungsmodus zeigt das Fahrzeug nur
## die vereinfachte Außenhülle, sonst die volle Karosserie.
static func _apply_detail(node: Node3D) -> void:
	var loop := Engine.get_main_loop()
	var level := 2
	if loop is SceneTree and loop.root.has_node("Perf"):
		level = int(loop.root.get_node("Perf").level)
	var body := node.get_node_or_null("Body")
	var far := node.get_node_or_null("Far")
	if level <= 0 and far != null and body != null:
		# Leistungsmodus: eine Oberfläche statt zehn, kein Innenraum
		body.visible = false
		far.visible = true
	elif body != null:
		body.visible = true
		if far != null:
			far.visible = false
		# Innenraum erst aus der Nähe
		if level <= 1:
			for key in ["Body_interior", "Body_detail"]:
				var n := node.find_child(key, true, false)
				if n is GeometryInstance3D:
					(n as GeometryInstance3D).visibility_range_end = 25.0


## Dreieckszahl des Modells (für Leistungsangaben).
static func triangles(model_id: String) -> int:
	var d := info(model_id)
	if d.is_empty() or not ResourceLoader.exists(str(d.scene)):
		return 0
	var n: Node3D = (load(str(d.scene)) as PackedScene).instantiate()
	var t := int(n.get_meta("triangles", 0))
	n.free()
	return t
