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
	"Markings_LOD0": Vector2(0.0, 200.0),
	"Detail_LOD0": Vector2(0.0, 170.0),
	"Buildings_LOD0": Vector2(0.0, 220.0),
	"Buildings_LOD1": Vector2(220.0, 0.0),
}

const LOOPING := ["idle", "walk", "run", "sprint", "crouch_walk", "crouch_idle", "fall", "swim", "swim_idle",
	"drive", "sit", "phone", "talk", "hands_up", "cower", "panic_run", "aim_pistol", "aim_rifle", "idle_armed"]

var _cache := {}


func _post_import(scene: Node) -> Object:
	_walk(scene)
	return scene


func _walk(n: Node) -> void:
	if n is AnimationPlayer:
		var ap := n as AnimationPlayer
		for an in ap.get_animation_list():
			var a := ap.get_animation(an)
			a.loop_mode = Animation.LOOP_LINEAR if String(an) in LOOPING else Animation.LOOP_NONE
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
	if nm in ["Body", "BumperF", "BumperR"] and mesh is ArrayMesh and _has_paint(mesh):
		_strip_lods(mi)
	for k in RANGES:
		if nm.begins_with(k):
			var r: Vector2 = RANGES[k]
			mi.visibility_range_begin = r.x
			mi.visibility_range_end = r.y
			mi.visibility_range_begin_margin = 12.0 if r.x > 0.0 else 0.0
			mi.visibility_range_end_margin = 12.0 if r.y > 0.0 else 0.0
			mi.visibility_range_fade_mode = GeometryInstance3D.VISIBILITY_RANGE_FADE_SELF
	# shadow casting only where it is visible: flat ground, markings and small details
	# don't cast (performance), buildings and structures do
	if nm.begins_with("Markings") or nm.begins_with("Far_") or nm.begins_with("Ground") or nm.begins_with("Detail"):
		mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF


func _has_paint(mesh: Mesh) -> bool:
	for i in mesh.get_surface_count():
		var m := mesh.surface_get_material(i)
		if m and String(m.resource_path).get_file().begins_with("car_paint"):
			return true
	return false


## Vehicle paint surfaces carry panel-gap distance fields in UV/UV2; automatic LOD
## simplification collapses vertices without respecting them (blotchy gap lines), so these
## meshes keep full detail and uncompressed attributes.
func _strip_lods(mi: MeshInstance3D) -> void:
	var src := mi.mesh as ArrayMesh
	var out := ArrayMesh.new()
	for i in src.get_surface_count():
		out.add_surface_from_arrays(src.surface_get_primitive_type(i), src.surface_get_arrays(i))
		out.surface_set_material(i, src.surface_get_material(i))
		out.surface_set_name(i, src.surface_get_name(i))
	out.resource_name = src.resource_name
	mi.mesh = out
