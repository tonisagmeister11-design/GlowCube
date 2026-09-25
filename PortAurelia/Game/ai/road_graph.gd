class_name RoadGraph
extends RefCounted
## Lane-level road network loaded from res://data/city/roads.json.
## Lanes are directed polylines; connectors are lanes through intersections.
## Used by traffic AI, police pursuit, GPS routing and spawners.

const ROADS_PATH := "res://data/city/roads.json"
const CELL := 50.0


class Lane:
	var id := 0
	var is_connector := false
	var pts := PackedVector3Array()
	var cum := PackedFloat32Array()   # cumulative length at each point
	var length := 0.0
	var speed := 12.0
	var next := PackedInt32Array()
	var left := -1
	var right := -1
	var edge := -1
	var from_node := -1
	var to_node := -1
	var index := 0
	var count := 1
	var node := -1          # for connectors: the intersection node
	var turn := 0           # 0 straight, 1 left, 2 right
	var in_edge := -1

	func point_at(s: float) -> Vector3:
		s = clampf(s, 0.0, length)
		var i := cum.bsearch(s)
		i = clampi(i, 1, pts.size() - 1)
		var l0 := cum[i - 1]
		var l1 := cum[i]
		var t := 0.0 if l1 - l0 < 0.0001 else (s - l0) / (l1 - l0)
		return pts[i - 1].lerp(pts[i], t)

	func dir_at(s: float) -> Vector3:
		s = clampf(s, 0.0, length)
		var i := clampi(cum.bsearch(s), 1, pts.size() - 1)
		return (pts[i] - pts[i - 1]).normalized()

	func closest_s(p: Vector3) -> float:
		var best := INF
		var best_s := 0.0
		for i in range(1, pts.size()):
			var a := pts[i - 1]
			var b := pts[i]
			var ab := b - a
			var l2 := ab.length_squared()
			var t := 0.0 if l2 < 0.0001 else clampf((p - a).dot(ab) / l2, 0.0, 1.0)
			var q := a + ab * t
			var d := q.distance_squared_to(p)
			if d < best:
				best = d
				best_s = cum[i - 1] + (cum[i] - cum[i - 1]) * t
		return best_s


var nodes: Array = []      # Array[Dictionary] {pos, kind, control, phases, offset, edges, level}
var edges: Array = []      # Array[Dictionary]
var lanes: Array[Lane] = []
var signals: Array = []    # traffic light placements
var _grid := {}            # Vector2i -> PackedInt32Array of lane ids (non-connector lanes)
var lanes_into_node := {}  # node id -> Array[int] lanes ending at node


func load_graph() -> bool:
	var d = WorldData._read_json(ROADS_PATH)
	if d == null:
		push_error("RoadGraph: missing " + ROADS_PATH)
		return false
	for n in d["nodes"]:
		var p: Array = n["p"]
		var ph := {}
		for k in n.get("ph", {}):
			ph[int(k)] = int(n["ph"][k])
		nodes.append({"pos": Vector3(p[0], p[1], p[2]), "kind": n["k"], "control": n["c"], "phases": ph,
			"offset": float(n.get("off", 0.0)), "edges": n["e"], "level": n["lv"], "radius": n.get("r", 0.0)})
	edges = d["edges"]
	for l in d["lanes"]:
		var lane := Lane.new()
		lane.id = int(l["id"])
		lane.is_connector = int(l["k"]) == 1
		for q in l["pts"]:
			lane.pts.append(Vector3(q[0], q[1], q[2]))
		lane.cum.resize(lane.pts.size())
		var acc := 0.0
		for i in lane.pts.size():
			if i > 0:
				acc += lane.pts[i - 1].distance_to(lane.pts[i])
			lane.cum[i] = acc
		lane.length = acc
		lane.speed = float(l["sp"])
		lane.next = PackedInt32Array(l["nx"])
		lane.left = int(l.get("l", -1))
		lane.right = int(l.get("r", -1))
		lane.edge = int(l.get("e", -1))
		lane.from_node = int(l["fn"])
		lane.to_node = int(l["tn"])
		lane.index = int(l.get("i", 0))
		lane.count = int(l.get("n", 1))
		if lane.is_connector:
			lane.node = int(l.get("nd", -1))
			lane.turn = int(l.get("tr", 0))
			lane.in_edge = int(l.get("ie", -1))
		lanes.append(lane)
	signals = d.get("signals", [])
	for lane in lanes:
		if lane.is_connector or lane.pts.size() < 2:
			continue
		if not lanes_into_node.has(lane.to_node):
			lanes_into_node[lane.to_node] = []
		lanes_into_node[lane.to_node].append(lane.id)
		var seen := {}
		var s := 0.0
		while s <= lane.length:
			var c := _cell(lane.point_at(s))
			if not seen.has(c):
				seen[c] = true
				if not _grid.has(c):
					_grid[c] = PackedInt32Array()
				_grid[c].append(lane.id)
			s += CELL * 0.5
	return true


func _cell(p: Vector3) -> Vector2i:
	return Vector2i(floori(p.x / CELL), floori(p.z / CELL))


## Lanes (not connectors) passing near a position.
func lanes_near(p: Vector3, radius := 60.0) -> PackedInt32Array:
	var out := PackedInt32Array()
	var seen := {}
	var r := int(ceil(radius / CELL))
	var c := _cell(p)
	for dx in range(-r, r + 1):
		for dz in range(-r, r + 1):
			var k := Vector2i(c.x + dx, c.y + dz)
			if _grid.has(k):
				for id in _grid[k]:
					if not seen.has(id):
						seen[id] = true
						out.append(id)
	return out


## Closest lane position to p: returns {lane, s, dist} or {} if none within radius.
func closest_lane(p: Vector3, radius := 40.0, dir_hint := Vector3.ZERO) -> Dictionary:
	var best := {}
	var bd := radius * radius
	for id in lanes_near(p, radius):
		var lane := lanes[id]
		var s := lane.closest_s(p)
		var q := lane.point_at(s)
		var d := q.distance_squared_to(p)
		if dir_hint != Vector3.ZERO:
			var ld := lane.dir_at(s)
			if ld.dot(dir_hint) < 0.0:
				d += 400.0
		if d < bd:
			bd = d
			best = {"lane": id, "s": s, "dist": sqrt(d), "pos": q}
	return best


# ------------------------------------------------------------------ routing
## A* over lanes from a lane position to the lane closest to target. Returns lane id list.
func route(from_lane: int, target: Vector3, max_nodes := 6000) -> PackedInt32Array:
	var goal := closest_lane(target, 120.0)
	if goal.is_empty():
		return PackedInt32Array()
	var goal_lane: int = goal["lane"]
	var open := {from_lane: true}
	var g := {from_lane: 0.0}
	var came := {}
	var f := {from_lane: lanes[from_lane].pts[-1].distance_to(target)}
	var expanded := 0
	while not open.is_empty() and expanded < max_nodes:
		var cur := -1
		var cf := INF
		for k in open:
			if f[k] < cf:
				cf = f[k]
				cur = k
		if cur == goal_lane:
			var path := PackedInt32Array([cur])
			while came.has(cur):
				cur = came[cur]
				path.insert(0, cur)
			return path
		open.erase(cur)
		expanded += 1
		var lane := lanes[cur]
		var cost_here: float = g[cur]
		var options := Array(lane.next)
		if lane.left >= 0:
			options.append(lane.left)
		if lane.right >= 0:
			options.append(lane.right)
		for nx in options:
			var nl := lanes[nx]
			var step := nl.length / maxf(nl.speed, 5.0)
			if nx == lane.left or nx == lane.right:
				step = 1.5
			var ng := cost_here + step
			if not g.has(nx) or ng < g[nx]:
				g[nx] = ng
				came[nx] = cur
				f[nx] = ng + nl.pts[-1].distance_to(target) / 25.0
				open[nx] = true
	return PackedInt32Array()


## Polyline of a lane route for drawing (map/minimap GPS line).
func route_points(route_ids: PackedInt32Array, step := 8.0) -> PackedVector3Array:
	var out := PackedVector3Array()
	for id in route_ids:
		var lane := lanes[id]
		var s := 0.0
		while s < lane.length:
			out.append(lane.point_at(s))
			s += step
		out.append(lane.pts[-1])
	return out


func node_pos(id: int) -> Vector3:
	return nodes[id]["pos"]
