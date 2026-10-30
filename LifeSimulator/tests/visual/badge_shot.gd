extends Node3D
## Zeigt das KALDRA-Emblem gross und klein, als Mesh und als Bild.
func _ready() -> void:
	var out := "/tmp"
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
	var env := WorldEnvironment.new()
	var e := Environment.new()
	e.background_mode = Environment.BG_COLOR
	e.background_color = Color(0.14, 0.15, 0.17)
	e.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	e.ambient_light_color = Color(0.6, 0.63, 0.68)
	e.ambient_light_energy = 1.0
	e.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.environment = e
	add_child(env)
	# Hauptlicht von schraeg vorn: so trifft es die Vorderflaechen des Emblems,
	# sonst beurteilt man die Beleuchtung statt der Form.
	var key := DirectionalLight3D.new()
	key.rotation_degrees = Vector3(-28, 18, 0)
	key.light_energy = 2.6
	add_child(key)
	var fill := DirectionalLight3D.new()
	fill.rotation_degrees = Vector3(-6, -40, 0)
	fill.light_energy = 1.2
	add_child(fill)

	# Emblem in Originalgroesse, auf einer lackierten Flaeche
	var panel := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = Vector3(0.6, 0.4, 0.02)
	panel.mesh = bm
	var pmat := StandardMaterial3D.new()
	pmat.albedo_color = Color(0.72, 0.03, 0.05)
	pmat.metallic = 0.75
	pmat.roughness = 0.22
	pmat.clearcoat_enabled = true
	pmat.clearcoat = 0.9
	panel.material_override = pmat
	add_child(panel)

	var badge := BrandKaldra.badge_node(0.09)
	badge.position = Vector3(0, 0, 0.01)
	add_child(badge)
	print("Emblem-Dreiecke: ", _tris(badge.mesh))

	var cam := Camera3D.new()
	add_child(cam)
	cam.current = true
	cam.fov = 30.0

	# Nah
	cam.position = Vector3(0.05, 0.05, 0.30)
	cam.look_at(Vector3(0, 0, 0))
	await _shot(out, "nah")
	# Schraeg
	cam.position = Vector3(0.26, 0.10, 0.22)
	cam.look_at(Vector3(0, 0, 0))
	await _shot(out, "schraeg")
	# Aus Spielentfernung (drei Meter)
	cam.position = Vector3(0.0, 0.0, 3.0)
	cam.look_at(Vector3(0, 0, 0))
	await _shot(out, "fern")

	# Das Zeichen als Bild
	BrandKaldra.badge_image(256).save_png("%s/badge_bild.png" % out)
	get_tree().quit()


func _tris(m: Mesh) -> int:
	var n := 0
	for s in m.get_surface_count():
		var idx: PackedInt32Array = m.surface_get_arrays(s)[Mesh.ARRAY_INDEX]
		n += idx.size() / 3 if idx.size() > 0 else m.surface_get_array_len(s) / 3
	return n


func _shot(out: String, tag: String) -> void:
	for i in 6:
		await get_tree().process_frame
	get_viewport().get_texture().get_image().save_png("%s/badge_%s.png" % [out, tag])
