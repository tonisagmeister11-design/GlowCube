class_name ParkedCarProxy
extends StaticBody3D
## Kollision und Fahrzeugvertrag fuer ein geparktes Auto in Spielernaehe
## (die Darstellung liegt im MultiMesh des TrafficManagers). Geparkte Autos
## sind abgeschlossen; set_locked(false) (z.B. Autodiebstahl) erlaubt das
## Einsteigen, dabei wird das Auto in ein PlayerVehicle umgewandelt.

var manager: TrafficManager
var spot: Dictionary = {}
var vehicle_id := -1
var locked := true
var _shape: CollisionShape3D


func _ready() -> void:
	_shape = CollisionShape3D.new()
	_shape.shape = BoxShape3D.new()
	add_child(_shape)
	collision_layer = 1
	collision_mask = 0


func assign(s: Dictionary) -> void:
	if s.get("key", -2) == spot.get("key", -3):
		return
	spot = s
	locked = true
	if s.is_empty():
		if is_in_group("vehicle"):
			remove_from_group("vehicle")
		_shape.disabled = true
		position = Vector3(0, -50, 0)
		return
	var t := VehicleCatalog.type_info(s.type)
	(_shape.shape as BoxShape3D).size = Vector3(t.width, t.height * 0.8, t.length)
	_shape.position = Vector3(0, t.height * 0.45, 0)
	_shape.disabled = false
	global_position = Vector3(s.pos.x, 0.0, s.pos.y)
	rotation.y = float(s.rot) + PI
	if not is_in_group("vehicle"):
		add_to_group("vehicle")


func set_locked(on: bool) -> void:
	locked = on


func can_enter(_player) -> bool:
	return not spot.is_empty() and not locked


func is_occupied() -> bool:
	return false


func enter(player) -> void:
	var pv := manager.claim_parked(spot, true)
	assign({})
	if pv and player and player.has_method("exit_vehicle") and player.has_method("enter_vehicle"):
		# Der Spieler-Controller hat dieses Objekt als Fahrzeug uebernommen:
		# auf das echte Fahrzeug umschalten.
		player.set("vehicle", null)
		player.enter_vehicle(pv)
	elif pv:
		pv.enter(player)


func exit() -> Vector3:
	return global_position + Vector3(1.5, 0, 0)


func get_camera_target() -> Node3D:
	return self
