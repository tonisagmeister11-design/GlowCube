class_name BusStopPoint
extends Area3D
## Interagierbare Bushaltestelle (Gruppe "interactable"): zeigt die naechsten
## Abfahrten; steht ein Bus der Linie an der Haltestelle, kann der Spieler
## einsteigen und ein Ziel waehlen (BusPanel).

var manager: TrafficManager
var stop_id := -1


func setup(p_manager: TrafficManager, p_stop: int) -> void:
	manager = p_manager
	stop_id = p_stop
	var st: Dictionary = manager.city.bus_stops[stop_id]
	name = "BusStop%d" % stop_id
	position = Vector3(st.pos.x, 1.0, st.pos.y)
	monitoring = false
	collision_layer = 0
	collision_mask = 0
	var cs := CollisionShape3D.new()
	var sp := SphereShape3D.new()
	sp.radius = 2.0
	cs.shape = sp
	add_child(cs)
	add_to_group("interactable")


func get_prompt(_player) -> String:
	var fare := manager.transit.fare(manager.world)
	var bus := manager.bus_at_stop(stop_id)
	if bus:
		var bl: Dictionary = manager.city.bus_lines[bus.line]
		return "Einsteigen – Linie %d (%s)" % [int(bl.number), TrafficManager._money(fare)]
	var arr := manager.transit.next_arrivals(manager.world, stop_id, 1)
	if arr.is_empty():
		return "Haltestelle %s" % manager.city.bus_stops[stop_id].name
	return "Abfahrten – Linie %d in %d min" % [arr[0].number, int(ceil(arr[0].minutes))]


func interact(player) -> void:
	BusPanel.open(self, manager, stop_id, player)
