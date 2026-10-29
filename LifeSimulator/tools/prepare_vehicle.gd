extends SceneTree
## Bereitet ein zugeliefertes Fahrzeugmodell (FBX/GLB) fuer das Spiel auf.
##
## Fertige Modelle aus Modellierprogrammen bestehen oft aus hunderten
## Einzelteilen mit eigenen Materialien. Jedes Teil kostet einen Zeichenaufruf;
## 500 Teile fuer ein Auto waeren fuer die Bildrate untragbar. Dieses Werkzeug:
##
##   1. liest das Modell,
##   2. trennt die Raeder ab (sie muessen sich drehen koennen),
##   3. fuehrt alles Uebrige nach Materialgruppe zu wenigen Meshes zusammen,
##   4. legt eigene PBR-Materialien an (Lack, Glas, Chrom, Carbon, Gummi ...),
##      weil mitgelieferte Texturen oft fehlen,
##   5. erzeugt zusaetzlich eine vereinfachte Fassung fuer grosse Entfernungen
##      und den Leistungsmodus,
##   6. speichert das Ergebnis als Szene.
##
## Aufruf:
##   godot --headless --path . --script tools/prepare_vehicle.gd -- \
##       --in=res://assets/models/vehicles/sportwagen.fbx \
##       --out=res://assets/models/vehicles/sportwagen.scn \
##       --name="Vanari Furio" --color=1,0.72,0.05

## Materialgruppen: Schluessel aus dem Teilenamen -> Materialvorlage.
const MATERIAL_GROUPS := {
	"CarPaint": "paint", "PaintedMetal": "paint", "Paint": "paint",
	"GlassClear": "glass", "Glass": "glass", "GlassTinted": "glass_tinted",
	"Chrome": "chrome", "Aluminum": "metal", "Steel": "metal", "Screw": "metal",
	"CarbonFiber": "carbon", "Carbon": "carbon",
	"PlasticBlack": "plastic", "BlackFrame": "plastic", "BlackHole": "dark",
	"Rubber": "rubber", "Tire": "rubber",
	"Leather": "interior", "Alcantara": "interior", "Stitch": "interior",
	"Label": "detail", "Detail": "detail", "Grille": "dark", "Custom": "detail",
}
## Teile, die im Innenraum liegen und aus der Ferne nicht sichtbar sind.
const INTERIOR_HINTS := ["seat", "dash", "steeringwheel", "interior", "cabin", "console",
	"pedal", "carpet", "trunkliner", "trunkbay", "doorpanel", "sunvisor", "mirror_i"]

var src := ""
var dst := ""
var disp_name := "Fahrzeug"
var paint := Color(0.86, 0.12, 0.08)


func _init() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--in="):
			src = a.substr(5)
		elif a.begins_with("--out="):
			dst = a.substr(6)
		elif a.begins_with("--name="):
			disp_name = a.substr(7)
		elif a.begins_with("--color="):
			var p := a.substr(8).split(",")
			if p.size() >= 3:
				paint = Color(float(p[0]), float(p[1]), float(p[2]))
	if src == "" or dst == "":
		print("Aufruf: --in=<modell> --out=<szene> [--name=..] [--color=r,g,b]")
		quit(1)
		return
	_run()
	quit(0)


func _run() -> void:
	if not ResourceLoader.exists(src):
		print("FEHLER: ", src, " nicht gefunden")
		quit(1)
		return
	var packed: PackedScene = load(src)
	var model: Node = packed.instantiate()
	var model3 := model as Node3D
	var t0 := Time.get_ticks_msec()

	# Alle Meshes mit globaler Lage einsammeln
	var parts: Array = []
	var stack: Array = [model]
	while not stack.is_empty():
		var c: Node = stack.pop_back()
		for ch in c.get_children():
			stack.append(ch)
		if c is MeshInstance3D and c.mesh != null:
			parts.append({"node": c, "xf": _global_of(c, model3), "name": String(c.name)})

	# Raeder erkennen: Name beginnt mit Wheel/Tire/Rim oder liegt unter einem solchen Knoten
	var wheels := {"FL": [], "FR": [], "RL": [], "RR": []}
	var body_parts: Array = []
	for p in parts:
		var w := _wheel_slot(p.node)
		if w != "":
			wheels[w].append(p)
		else:
			body_parts.append(p)

	# Karosserie nach Materialgruppe zusammenfuehren
	var groups := {}
	for p in body_parts:
		var g := _group_of(String(p.name))
		if not groups.has(g):
			groups[g] = []
		groups[g].append(p)

	var root := Node3D.new()
	root.name = "Vehicle"
	var body := Node3D.new()
	body.name = "Body"
	root.add_child(body)
	body.owner = root

	var total_tris := 0
	var merged := 0
	for g in groups:
		var mesh := _merge(groups[g])
		if mesh == null:
			continue
		var mi := MeshInstance3D.new()
		mi.name = "Body_" + str(g)
		mi.mesh = mesh
		mi.material_override = _material(str(g))
		body.add_child(mi)
		mi.owner = root
		merged += 1
		for s in mesh.get_surface_count():
			total_tris += mesh.surface_get_array_len(s) / 3

	# Raeder als drehbare Knoten
	var wheel_nodes := 0
	for slot in wheels:
		if (wheels[slot] as Array).is_empty():
			continue
		var center := _center_of(wheels[slot])
		var pivot := Node3D.new()
		pivot.name = "Wheel" + str(slot)
		pivot.position = center
		root.add_child(pivot)
		pivot.owner = root
		# Teile relativ zum Radmittelpunkt zusammenfuehren
		var rel: Array = []
		for p in wheels[slot]:
			var q: Dictionary = p.duplicate()
			q.xf = Transform3D(Basis(), -center) * p.xf
			rel.append(q)
		var wm := _merge(rel)
		if wm != null:
			var wmi := MeshInstance3D.new()
			wmi.name = "Mesh"
			wmi.mesh = wm
			wmi.material_override = _material("rubber")
			pivot.add_child(wmi)
			wmi.owner = root
			for s in wm.get_surface_count():
				total_tris += wm.surface_get_array_len(s) / 3
		wheel_nodes += 1

	# Vereinfachte Fassung: nur aussen sichtbare Gruppen, ein Mesh
	var far_parts: Array = []
	for p in body_parts:
		if not _is_interior(String(p.name)):
			far_parts.append(p)
	var far_mesh := _merge(far_parts)
	if far_mesh != null:
		var far := MeshInstance3D.new()
		far.name = "Far"
		far.mesh = far_mesh
		far.material_override = _material("paint")
		far.visible = false
		root.add_child(far)
		far.owner = root

	# Kollisionskoerper aus der Gesamtausdehnung
	var aabb := AABB()
	var first := true
	for p in parts:
		var b: AABB = p.xf * p.node.mesh.get_aabb()
		if first:
			aabb = b
			first = false
		else:
			aabb = aabb.merge(b)
	root.set_meta("size", aabb.size)
	root.set_meta("display_name", disp_name)
	root.set_meta("triangles", total_tris)

	var scene := PackedScene.new()
	scene.pack(root)
	var err := ResourceSaver.save(scene, dst)
	print("Teile gelesen: %d  Gruppen: %d  Raeder: %d  Dreiecke: %d" % [parts.size(), merged, wheel_nodes, total_tris])
	print("Groesse (m): ", aabb.size)
	print("Gespeichert: %s (Fehler %d, %d ms)" % [dst, err, Time.get_ticks_msec() - t0])


# ============================================================ Hilfen

func _global_of(node: Node, root: Node3D) -> Transform3D:
	var t := Transform3D()
	var cur: Node = node
	while cur != null and cur != root.get_parent():
		if cur is Node3D:
			t = (cur as Node3D).transform * t
		cur = cur.get_parent()
	return t


## Radposition aus dem Namen des Teils oder eines Elternknotens.
func _wheel_slot(node: Node) -> String:
	var cur: Node = node
	while cur != null:
		var n := String(cur.name).to_lower()
		if n.begins_with("wheel") or n.begins_with("tire") or n.begins_with("rim") or n.begins_with("brake"):
			if n.contains("fl") or n.contains("lf") or n.contains("frontleft"):
				return "FL"
			if n.contains("fr") or n.contains("rf") or n.contains("frontright"):
				return "FR"
			if n.contains("rl") or n.contains("lr") or n.contains("rearleft"):
				return "RL"
			if n.contains("rr") or n.contains("rearright"):
				return "RR"
		cur = cur.get_parent()
	return ""


func _group_of(part_name: String) -> String:
	for key in MATERIAL_GROUPS:
		if part_name.contains(key):
			return str(MATERIAL_GROUPS[key])
	return "plastic"


func _is_interior(part_name: String) -> bool:
	var n := part_name.to_lower()
	for h in INTERIOR_HINTS:
		if n.contains(h):
			return true
	return false


func _center_of(list: Array) -> Vector3:
	var aabb := AABB()
	var first := true
	for p in list:
		var b: AABB = p.xf * p.node.mesh.get_aabb()
		if first:
			aabb = b
			first = false
		else:
			aabb = aabb.merge(b)
	return aabb.get_center()


## Fuehrt mehrere Meshes zu einem zusammen (eine Oberflaeche, ein Material).
func _merge(list: Array) -> ArrayMesh:
	if list.is_empty():
		return null
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	var any := false
	for p in list:
		var mesh: Mesh = p.node.mesh
		var xf: Transform3D = p.xf
		for s in mesh.get_surface_count():
			if mesh.surface_get_primitive_type(s) != Mesh.PRIMITIVE_TRIANGLES:
				continue
			var arrays: Array = mesh.surface_get_arrays(s)
			var verts: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
			if verts.is_empty():
				continue
			var normals: PackedVector3Array = arrays[Mesh.ARRAY_NORMAL] if arrays[Mesh.ARRAY_NORMAL] != null else PackedVector3Array()
			var uvs: PackedVector2Array = arrays[Mesh.ARRAY_TEX_UV] if arrays[Mesh.ARRAY_TEX_UV] != null else PackedVector2Array()
			var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX] if arrays[Mesh.ARRAY_INDEX] != null else PackedInt32Array()
			var order: PackedInt32Array = indices
			if order.is_empty():
				order = PackedInt32Array()
				for i in verts.size():
					order.append(i)
			var nb := xf.basis.inverse().transposed()
			for i in order:
				if i >= verts.size():
					continue
				if i < normals.size():
					st.set_normal(nb * normals[i])
				if i < uvs.size():
					st.set_uv(uvs[i])
				st.add_vertex(xf * verts[i])
				any = true
	if not any:
		return null
	st.index()
	if st.get_aabb().size == Vector3.ZERO:
		return null
	var out := st.commit()
	return out


## Eigene Materialien, weil mitgelieferte Texturen haeufig fehlen.
func _material(group: String) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	match group:
		"paint":
			m.albedo_color = paint
			m.metallic = 0.75
			m.roughness = 0.22
			m.clearcoat_enabled = true
			m.clearcoat = 0.9
			m.clearcoat_roughness = 0.08
		"glass":
			m.albedo_color = Color(0.55, 0.62, 0.68, 0.32)
			m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
			m.metallic = 0.1
			m.roughness = 0.04
			m.cull_mode = BaseMaterial3D.CULL_DISABLED
		"glass_tinted":
			m.albedo_color = Color(0.12, 0.14, 0.16, 0.72)
			m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
			m.roughness = 0.08
		"chrome":
			m.albedo_color = Color(0.92, 0.93, 0.95)
			m.metallic = 1.0
			m.roughness = 0.08
		"metal":
			m.albedo_color = Color(0.55, 0.56, 0.58)
			m.metallic = 0.9
			m.roughness = 0.35
		"carbon":
			m.albedo_color = Color(0.08, 0.085, 0.095)
			m.metallic = 0.5
			m.roughness = 0.3
		"rubber":
			m.albedo_color = Color(0.055, 0.055, 0.06)
			m.metallic = 0.0
			m.roughness = 0.92
		"interior":
			m.albedo_color = Color(0.12, 0.12, 0.13)
			m.roughness = 0.78
		"dark":
			m.albedo_color = Color(0.03, 0.03, 0.035)
			m.roughness = 0.6
		"detail":
			m.albedo_color = Color(0.3, 0.3, 0.32)
			m.metallic = 0.4
			m.roughness = 0.4
		_:
			m.albedo_color = Color(0.1, 0.1, 0.11)
			m.roughness = 0.65
	return m
