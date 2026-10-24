class_name DoorInteractable
extends Area3D
## Eingang eines Gebaeudes. Zeigt einen Hinweis und oeffnet beim Interagieren
## das Gebaeude-Fenster (Einkaufen, Schlafen, Arbeiten ...).

var building_id := -1
var label := ""


func setup(b: Dictionary, prompt_label: String) -> void:
	building_id = int(b.id)
	label = prompt_label
	add_to_group("interactable")
	var shape := CollisionShape3D.new()
	var s := SphereShape3D.new()
	s.radius = 1.6
	shape.shape = s
	add_child(shape)
	collision_layer = 0
	collision_mask = 0
	monitoring = false
	var p: Vector2 = b.door
	var out: Vector2 = b.front_dir
	position = Vector3(p.x + out.x * 0.8, 1.0, p.y + out.y * 0.8)


func get_prompt(_player = null) -> String:
	return label


func interact(_player = null) -> void:
	EventBus.open_panel.emit("building", {"building": building_id})
