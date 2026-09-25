@tool
extends EditorScenePostImport
## Post-import processing for every GLB coming out of the Blender pipeline.
##
## * Replaces imported placeholder materials with the project's material library
##   (res://assets/materials/<material name>.tres) by name.
## * Sets visibility ranges from node name suffixes (_LOD0 / _LOD1 / Far_*).
## * Tags collision bodies (Col_<surface>) with their surface type for
##   footstep sounds and tyre grip, and puts them on the world physics layer.

const MAT_DIR := "res://assets/materials/"

const RANGES := {
	"Markings_LOD0": Vector2(0.0, 240.0),
	"Detail_LOD0": Vector2(0.0, 210.0),
	"Buildings_LOD0": Vector2(0.0, 260.0),
	"Buildings_LOD1": Vector2(260.0, 0.0),
}

var _cache := {}


func _post_import(scene: Node) -> Object:
	_walk(scene)
	return scene


func _walk(n: Node) -> void:
	if n is MeshInstance3D:
		_setup_mesh(n as MeshInstance3D)
	elif n is StaticBody3D:
		var nm := String(n.name)
		if nm.begins_with("Col_"):
			n.set_meta("surface", nm.substr(4))
			(n as StaticBody3D).collision_layer = 1
			(n as StaticBody3D).collision_mask = 0
	for c in n.get_children():
		_walk(c)


func _material(key: String) -> Material:
	if _cache.has(key):
		return _cache[key]
	var path := MAT_DIR + key + ".tres"
	var m: Material = null
	if ResourceLoader.exists(path):
		m = load(path)
	_cache[key] = m
	return m


func _setup_mesh(mi: MeshInstance3D) -> void:
	var mesh := mi.mesh
	if mesh:
		for i in mesh.get_surface_count():
			var m := mesh.surface_get_material(i)
			if m == null:
				continue
			var key := m.resource_name
			# Blender may append .001 style suffixes
			var dot := key.find(".")
			if dot > 0:
				key = key.substr(0, dot)
			var lib := _material(key)
			if lib:
				mesh.surface_set_material(i, lib)
	var nm := String(mi.name)
	for k in RANGES:
		if nm.begins_with(k):
			var r: Vector2 = RANGES[k]
			mi.visibility_range_begin = r.x
			mi.visibility_range_end = r.y
			mi.visibility_range_begin_margin = 12.0 if r.x > 0.0 else 0.0
			mi.visibility_range_end_margin = 12.0 if r.y > 0.0 else 0.0
			mi.visibility_range_fade_mode = GeometryInstance3D.VISIBILITY_RANGE_FADE_SELF
	if nm.begins_with("Markings"):
		mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	elif nm.begins_with("Far_"):
		mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	elif nm.begins_with("Ground"):
		mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON
