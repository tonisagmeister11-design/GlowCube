class_name GPS
extends RefCounted
## Road navigation to a target (waypoint or mission objective) over the traffic lane
## graph. Recomputes periodically while the player moves; exposes the route polyline
## for the radar and the map screen.

var target := Vector3.INF
var points := PackedVector3Array()
var remaining := 0.0
var _last_from := Vector3.INF
var _timer := 0.0


func set_target(t: Vector3) -> void:
	target = t
	points = PackedVector3Array()
	_last_from = Vector3.INF
	_timer = 0.0


func active() -> bool:
	return target != Vector3.INF


func update(delta: float, from: Vector3, heading: Vector3) -> void:
	if target == Vector3.INF:
		return
	_timer -= delta
	if _timer > 0.0 and from.distance_to(_last_from) < 25.0:
		return
	_timer = 2.0
	_last_from = from
	var g := GameWorld.instance.graph
	if from.distance_to(target) < 40.0:
		points = PackedVector3Array([from, target])
		remaining = from.distance_to(target)
		return
	var c := g.closest_lane(from, 80.0, heading)
	if c.is_empty():
		points = PackedVector3Array([from, target])
		remaining = from.distance_to(target)
		return
	var ids := g.route(c["lane"], target, 5000)
	if ids.is_empty():
		points = PackedVector3Array([from, target])
		remaining = from.distance_to(target)
		return
	var pts := g.route_points(ids, 12.0)
	# drop the part of the first lane behind the player
	var start := 0
	var bd := INF
	for i in mini(pts.size(), 12):
		var d := pts[i].distance_to(from)
		if d < bd:
			bd = d
			start = i
	points = PackedVector3Array([from])
	points.append_array(pts.slice(start))
	points.append(target)
	remaining = 0.0
	for i in range(1, points.size()):
		remaining += points[i].distance_to(points[i - 1])
