class_name PropLibrary
extends RefCounted
## Meshes and metadata of all instanced props (from the Blender asset library).

const LIB_PATH := "res://assets/generated/props/props_library.glb"
const META_PATH := "res://assets/generated/props/props_meta.json"

## How far each prop category is drawn (LOD0 end, LOD1 end) and whether it casts shadows.
const CATEGORY := {
	"tree": [170.0, 900.0, true],
	"lamp": [420.0, 0.0, true],
	"small": [110.0, 0.0, false],
	"medium": [220.0, 0.0, true],
	"large": [2000.0, 0.0, true],
	"fence": [140.0, 0.0, false],
}
const TYPE_CATEGORY := {
	"palm_tall": "tree", "palm_short": "tree", "tree_round": "tree", "tree_oak": "tree", "tree_pine": "tree",
	"tree_cypress": "tree", "bush": "small", "hedge": "fence", "rock": "medium",
	"lamp_street": "lamp", "lamp_plaza": "lamp", "lamp_wood": "lamp", "lamp_highway": "lamp",
	"lamp_highway_double": "lamp",
	"fence_wood": "fence", "fence_chain": "fence", "fence_construction": "fence",
	"container": "medium", "bus_stop": "medium", "billboard": "large", "gantry_crane": "large",
	"tower_crane": "large", "cargo_ship": "large", "airliner": "large", "ferris_wheel": "large",
	"tug_boat": "large", "yacht": "medium", "sailboat": "medium", "boat_small": "medium",
	"small_plane": "medium", "lifeguard_tower": "medium", "fountain": "medium", "pavilion": "medium",
	"playground": "medium", "pool": "medium", "dumpster": "small", "material_pile": "medium",
}
const CONTAINER_COLORS := [Color(0.75, 0.22, 0.14), Color(0.15, 0.33, 0.6), Color(0.2, 0.52, 0.3),
	Color(0.86, 0.64, 0.14), Color(0.62, 0.62, 0.64), Color(0.45, 0.16, 0.42), Color(0.9, 0.45, 0.1),
	Color(0.1, 0.45, 0.5)]

var meshes := {}      # name -> Mesh
var meta := {}        # name -> Dictionary
var variants := {}


func load_library() -> bool:
	if not ResourceLoader.exists(LIB_PATH):
		push_warning("PropLibrary: missing " + LIB_PATH)
		return false
	var scene: PackedScene = load(LIB_PATH)
	var root := scene.instantiate()
	_collect(root)
	root.free()
	var m = WorldData._read_json(META_PATH)
	if m:
		meta = m.get("types", {})
		variants = m.get("variants", {})
	return true


func _collect(n: Node) -> void:
	if n is MeshInstance3D and (n as MeshInstance3D).mesh:
		meshes[String(n.name)] = (n as MeshInstance3D).mesh
	for c in n.get_children():
		_collect(c)


func mesh_for(type_name: String, variant: int, lod: int) -> Mesh:
	var key := type_name
	if variants.has(type_name) and int(variants[type_name]) > 1:
		key = "%s_%d" % [type_name, variant % int(variants[type_name])]
	if lod == 1:
		return meshes.get(key + "_LOD1", null)
	return meshes.get(key, null)


func category(type_name: String) -> Array:
	return CATEGORY[TYPE_CATEGORY.get(type_name, "small")]


func light_points(type_name: String) -> Array:
	return meta.get(type_name, {}).get("lights", [])


func aabb(type_name: String) -> AABB:
	var a: Array = meta.get(type_name, {}).get("aabb", [0, 0, 0, 0, 0, 0])
	return AABB(Vector3(a[0], a[1], a[2]), Vector3(a[3] - a[0], a[4] - a[1], a[5] - a[2]))
