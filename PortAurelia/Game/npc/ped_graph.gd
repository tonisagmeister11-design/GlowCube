class_name PedGraph
extends RefCounted
## Pedestrian navigation graph (sidewalk corners, sidewalks, crosswalks) loaded from
## res://data/city/peds.json. Pedestrians walk edge paths node to node; crosswalks
## are only entered when the crossing signal allows it.

const PATH := "res://data/city/peds.json"
const GRID := 50.0

enum Kind { WALK, CROSSWALK }

var node_pos: PackedVector3Array = PackedVector3Array()
var node_edges: Array = []          # node -> Array[int] edge ids
var edge_a: PackedInt32Array = PackedInt32Array()
var edge_b: PackedInt32Array = PackedInt32Array()
var edge_kind: PackedInt32Array = PackedInt32Array()
var edge_road_node: PackedInt32Array = PackedInt32Array()   # crosswalk: road intersection node
var edge_road_edge: PackedInt32Array = PackedInt32Array()   # crosswalk: road edge crossed
var edge_path: Array = []           # Array[PackedVector3Array] from a to b
var edge_len: PackedFloat32Array = PackedFloat32Array()
var _grid := {}                     # Vector2i -> PackedInt32Array edge ids


func load_graph() -> bool:
	var d = WorldData._read_json(PATH)
	if d == null:
		push_error("PedGraph: missing " + PATH)
		return false
	for n in d["nodes"]:
		var p: Array = n["p"]
		node_pos.append(Vector3(p[0], p[1], p[2]))
		node_edges.append([])
	for e in d["edges"]:
		var id := edge_a.size()
		edge_a.append(int(e["a"]))
		edge_b.append(int(e["b"]))
		edge_kind.append(int(e.get("k", 0)))
		edge_road_node.append(int(e.get("nd", -1)))
		edge_road_edge.append(int(e.get("e", -1)))
		var pts := PackedVector3Array()
		for q in e["path"]:
			pts.append(Vector3(q[0], q[1], q[2]))
		# make sure the path connects the two corner nodes
		var pa := node_pos[edge_a[id]]
		var pb := node_pos[edge_b[id]]
		if pts.is_empty() or pts[0].distance_to(pa) > 0.3:
			pts.insert(0, pa)
		if pts[pts.size() - 1].distance_to(pb) > 0.3:
			pts.append(pb)
		edge_path.append(pts)
		var L := 0.0
		for i in range(1, pts.size()):
			L += pts[i].distance_to(pts[i - 1])
		edge_len.append(L)
		node_edges[edge_a[id]].append(id)
		node_edges[edge_b[id]].append(id)
		var cells := {}
		for i in pts.size():
			var q := pts[i]
			cells[Vector2i(floori(q.x / GRID), floori(q.z / GRID))] = true
			if i > 0:
				var m := (q + pts[i - 1]) * 0.5
				cells[Vector2i(floori(m.x / GRID), floori(m.z / GRID))] = true
		for c in cells:
			if not _grid.has(c):
				_grid[c] = PackedInt32Array()
			_grid[c].append(id)
	return true


func other_end(edge: int, node: int) -> int:
	return edge_b[edge] if edge_a[edge] == node else edge_a[edge]


## Path of `edge` oriented to start at `from_node`.
func oriented_path(edge: int, from_node: int) -> PackedVector3Array:
	var pts: PackedVector3Array = edge_path[edge]
	if edge_a[edge] == from_node:
		return pts
	var r := pts.duplicate()
	r.reverse()
	return r


func edges_near(p: Vector3, radius: float) -> PackedInt32Array:
	var out := PackedInt32Array()
	var seen := {}
	var c0 := Vector2i(floori((p.x - radius) / GRID), floori((p.z - radius) / GRID))
	var c1 := Vector2i(floori((p.x + radius) / GRID), floori((p.z + radius) / GRID))
	for x in range(c0.x, c1.x + 1):
		for z in range(c0.y, c1.y + 1):
			var k := Vector2i(x, z)
			if _grid.has(k):
				for id in _grid[k]:
					if not seen.has(id):
						seen[id] = true
						out.append(id)
	return out


## Closest point on the sidewalk network: {edge, t (distance along a->b), pos, dist}.
func closest(p: Vector3, radius := 60.0, walk_only := true) -> Dictionary:
	var best := {}
	var bd := INF
	for id in edges_near(p, radius):
		if walk_only and edge_kind[id] != Kind.WALK:
			continue
		var pts: PackedVector3Array = edge_path[id]
		var acc := 0.0
		for i in range(1, pts.size()):
			var a := pts[i - 1]
			var b := pts[i]
			var ab := b - a
			var L := ab.length()
			var t := clampf((p - a).dot(ab) / maxf(L * L, 0.0001), 0.0, 1.0)
			var q := a + ab * t
			var d := q.distance_to(p)
			if d < bd:
				bd = d
				best = {"edge": id, "t": acc + t * L, "pos": q, "dist": d}
			acc += L
	return best


## Random point on a sidewalk between rmin and rmax from p.
func random_point(p: Vector3, rmin: float, rmax: float, rng: RandomNumberGenerator) -> Dictionary:
	var ids := edges_near(p, rmax)
	if ids.is_empty():
		return {}
	for attempt in 10:
		var id: int = ids[rng.randi() % ids.size()]
		if edge_kind[id] != Kind.WALK or edge_len[id] < 4.0:
			continue
		var t := rng.randf_range(1.0, edge_len[id] - 1.0)
		var q := point_on(id, t)
		var d := q.distance_to(p)
		if d >= rmin and d <= rmax:
			return {"edge": id, "t": t, "pos": q}
	return {}


func point_on(edge: int, t: float) -> Vector3:
	var pts: PackedVector3Array = edge_path[edge]
	var acc := 0.0
	for i in range(1, pts.size()):
		var L := pts[i].distance_to(pts[i - 1])
		if acc + L >= t:
			return pts[i - 1].lerp(pts[i], (t - acc) / maxf(L, 0.0001))
		acc += L
	return pts[pts.size() - 1]


## Breadth-limited Dijkstra over nodes; returns node list from `from` to `to` or [].
func route(from: int, to: int, max_nodes := 4000) -> PackedInt32Array:
	var dist := {from: 0.0}
	var prev := {}
	var open := [from]
	var visited := 0
	while not open.is_empty() and visited < max_nodes:
		var bi := 0
		for i in open.size():
			if dist[open[i]] < dist[open[bi]]:
				bi = i
		var n: int = open[bi]
		open.remove_at(bi)
		visited += 1
		if n == to:
			break
		for e in node_edges[n]:
			var m := other_end(e, n)
			var nd: float = dist[n] + edge_len[e] + (6.0 if edge_kind[e] == Kind.CROSSWALK else 0.0)
			if nd < dist.get(m, INF):
				dist[m] = nd
				prev[m] = [n, e]
				if not open.has(m):
					open.append(m)
	if not dist.has(to):
		return PackedInt32Array()
	var out := PackedInt32Array([to])
	var cur := to
	while cur != from:
		cur = prev[cur][0]
		out.insert(0, cur)
	return out


func edge_between(a: int, b: int) -> int:
	for e in node_edges[a]:
		if other_end(e, a) == b:
			return e
	return -1


func nearest_node(p: Vector3, radius := 80.0) -> int:
	var best := -1
	var bd := INF
	for id in edges_near(p, radius):
		for n in [edge_a[id], edge_b[id]]:
			var d := node_pos[n].distance_squared_to(p)
			if d < bd:
				bd = d
				best = n
	return best
