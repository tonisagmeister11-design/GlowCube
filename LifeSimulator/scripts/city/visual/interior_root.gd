class_name InteriorRoot
extends Node3D
## Wurzelknoten eines erzeugten Innenraums (siehe InteriorBuilder).
## Ankerpunkte: Name -> Transform3D (lokal zum Innenraum). Der Anker zeigt mit
## -Z auf das zugehoerige Objekt (Blickrichtung der Figur, die davor steht).

var kind := ""
var params: Dictionary = {}
var anchors: Dictionary = {}
var size := Vector3.ZERO


func get_anchors() -> Dictionary:
	return anchors


## Anker in Weltkoordinaten (beruecksichtigt die Lage des Innenraums).
func get_anchor_global(name: String) -> Transform3D:
	if not anchors.has(name):
		return global_transform
	return global_transform * anchors[name]


func has_anchor(name: String) -> bool:
	return anchors.has(name)
