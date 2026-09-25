class_name WorldData
extends RefCounted
## Static world description produced by the city plan generator
## (res://data/city/world.json + props.json). Shared by streaming, spawners, map and missions.

const WORLD_PATH := "res://data/city/world.json"
const PROPS_PATH := "res://data/city/props.json"

var world_min := -1600.0
var world_size := 3200.0
var chunk_size := 200.0
var sea_level := -1.5
var spawn := Vector3.ZERO
var chunks: Array = []               # Array[Vector2i]
var pois: Array = []                 # Array[Dictionary]
var parking: Array = []              # Array[Dictionary] {pos: Vector3, dir: Vector3, kind}
var buildings: Array = []            # Array[Dictionary]
var buildings_by_chunk := {}         # Vector2i -> Array[int]
var props_by_chunk := {}             # "x_z" -> Array
var parking_by_chunk := {}           # Vector2i -> Array[int]
var districts: Array = []
var tunnels: Array = []


func load_all() -> bool:
	var w = _read_json(WORLD_PATH)
	if w == null:
		push_error("WorldData: missing " + WORLD_PATH)
		return false
	world_min = w.get("world_min", -1600.0)
	world_size = w.get("world_size", 3200.0)
	chunk_size = w.get("chunk", 200.0)
	sea_level = w.get("sea_level", -1.5)
	var sp: Array = w.get("spawn", [0, 0, 0])
	spawn = Vector3(sp[0], sp[1], sp[2])
	for c in w.get("chunks", []):
		chunks.append(Vector2i(int(c[0]), int(c[1])))
	pois = w.get("pois", [])
	for p in pois:
		var e: Array = p.get("entrance", [0, 0, 0])
		p["entrance_v"] = Vector3(e[0], e[1], e[2])
		var f: Array = p.get("facing", [0, 1])
		p["facing_v"] = Vector3(f[0], 0.0, f[1])
	for i in w.get("parking", []).size():
		var pk: Dictionary = w["parking"][i]
		var pos := Vector3(pk["pos"][0], pk["pos"][1], pk["pos"][2])
		var dir := Vector3(pk["dir"][0], 0.0, pk["dir"][1])
		var rec := {"pos": pos, "dir": dir, "kind": pk.get("kind", "street"), "occupied": false}
		parking.append(rec)
		var ck := chunk_of(pos)
		if not parking_by_chunk.has(ck):
			parking_by_chunk[ck] = []
		parking_by_chunk[ck].append(parking.size() - 1)
	buildings = w.get("buildings", [])
	for b in buildings:
		var fp: Array = b["fp"]
		var c := Vector2.ZERO
		for q in fp:
			c += Vector2(q[0], q[1])
		c /= float(fp.size())
		b["center"] = c
		var ck := chunk_of(Vector3(c.x, 0, c.y))
		if not buildings_by_chunk.has(ck):
			buildings_by_chunk[ck] = []
		buildings_by_chunk[ck].append(int(b["id"]))
	districts = w.get("districts", [])
	tunnels = w.get("tunnels", [])
	var pr = _read_json(PROPS_PATH)
	if pr != null:
		props_by_chunk = pr
	return true


func chunk_of(p: Vector3) -> Vector2i:
	var n := int(world_size / chunk_size)
	return Vector2i(clampi(int(floor((p.x - world_min) / chunk_size)), 0, n - 1),
		clampi(int(floor((p.z - world_min) / chunk_size)), 0, n - 1))


func chunk_center(c: Vector2i) -> Vector3:
	return Vector3(world_min + (c.x + 0.5) * chunk_size, 0.0, world_min + (c.y + 0.5) * chunk_size)


func district_at(p: Vector3) -> Dictionary:
	for d in districts:
		var r: Array = d["rect"]
		if p.x >= r[0] and p.z >= r[1] and p.x < r[2] and p.z < r[3]:
			return d
	return {"id": "sea", "name": "Pacific Coast", "ped": 0.0, "traffic": 0.0}


func pois_of_type(t: String) -> Array:
	var out := []
	for p in pois:
		if p["type"] == t:
			out.append(p)
	return out


func nearest_poi(t: String, pos: Vector3) -> Dictionary:
	var best := {}
	var bd := INF
	for p in pois:
		if t != "" and p["type"] != t:
			continue
		var d: float = (p["entrance_v"] as Vector3).distance_squared_to(pos)
		if d < bd:
			bd = d
			best = p
	return best


static func _read_json(path: String):
	var f := FileAccess.open(path, FileAccess.READ)
	if f == null:
		return null
	var txt := f.get_as_text()
	f.close()
	return JSON.parse_string(txt)
