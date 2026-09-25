class_name Ocean
extends MeshInstance3D
## Global water plane at sea level. A radial grid (dense near the camera, coarse far away)
## follows the active camera, so waves stay detailed where the player looks while the
## horizon still reaches the fog. Everything below SEA_LEVEL (ocean, canal, marina,
## harbour basins, park lake) is water.

const SEA_LEVEL := -1.5
const RINGS := 72
const SEGMENTS := 112
const MAX_RADIUS := 5000.0

var material: ShaderMaterial


func _ready() -> void:
	mesh = _build_mesh()
	material = ShaderMaterial.new()
	material.shader = load("res://assets/shaders/water.gdshader")
	material.set_shader_parameter("normal_a", load("res://assets/textures/water_nrm.png"))
	material.set_shader_parameter("normal_b", load("res://assets/textures/water_nrm2.png"))
	material.set_shader_parameter("foam_noise", load("res://assets/textures/detail_noise.png"))
	material_override = material
	cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	extra_cull_margin = 16384.0
	position.y = SEA_LEVEL


func _build_mesh() -> ArrayMesh:
	var verts := PackedVector3Array()
	var idx := PackedInt32Array()
	verts.append(Vector3.ZERO)
	var radii := []
	for r in RINGS:
		var t := float(r + 1) / RINGS
		radii.append(pow(t, 2.6) * MAX_RADIUS + t * 2.0)
	for r in RINGS:
		for s in SEGMENTS:
			var a := TAU * s / SEGMENTS
			verts.append(Vector3(cos(a) * radii[r], 0.0, sin(a) * radii[r]))
	for s in SEGMENTS:
		var s2 := (s + 1) % SEGMENTS
		idx.append_array([0, 1 + s2, 1 + s])
	for r in range(RINGS - 1):
		var b0 := 1 + r * SEGMENTS
		var b1 := 1 + (r + 1) * SEGMENTS
		for s in SEGMENTS:
			var s2 := (s + 1) % SEGMENTS
			idx.append_array([b0 + s, b0 + s2, b1 + s2, b0 + s, b1 + s2, b1 + s])
	var normals := PackedVector3Array()
	normals.resize(verts.size())
	normals.fill(Vector3.UP)
	var arr := []
	arr.resize(Mesh.ARRAY_MAX)
	arr[Mesh.ARRAY_VERTEX] = verts
	arr[Mesh.ARRAY_NORMAL] = normals
	arr[Mesh.ARRAY_INDEX] = idx
	var m := ArrayMesh.new()
	m.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arr)
	return m


func _process(_delta: float) -> void:
	var cam := get_viewport().get_camera_3d()
	if cam:
		var p := cam.global_position
		global_position = Vector3(snappedf(p.x, 4.0), SEA_LEVEL, snappedf(p.z, 4.0))


func set_storm(v: float) -> void:
	material.set_shader_parameter("storm", v)
