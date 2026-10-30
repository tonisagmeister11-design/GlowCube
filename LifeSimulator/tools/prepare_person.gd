extends SceneTree
## Bereitet ein zugeliefertes Personenmodell (FBX/GLB mit Skelett) fuer das
## Spiel auf.
##
## Anders als bei Fahrzeugen darf hier nicht zusammengefuehrt oder vereinfacht
## werden: die Meshes haengen ueber Knochengewichte am Skelett, und beides
## ginge dabei verloren. Stattdessen:
##
##   1. Teile wegwerfen, die man nie sieht (Zaehne, Zunge, Traenenkanal,
##      Augenschatten) - das spart Dreiecke, ohne dass etwas fehlt,
##   2. Groesse pruefen und auf Sollhoehe skalieren,
##   3. die Knochen des Skeletts den Rollen zuordnen, die der Bewegungsablauf
##      braucht (Becken, Oberschenkel, Unterschenkel, Fuss, Wirbelsaeule,
##      Oberarm, Unterarm, Kopf) und die Zuordnung in der Szene ablegen,
##   4. Haltungsknochen des Gesichts bleiben unberuehrt.
##
## Aufruf:
##   godot --headless --path . --script tools/prepare_person.gd -- \
##       --in=res://assets/models/people/mann_a.fbx \
##       --out=res://assets/models/people/mann.scn \
##       --name="Bruno" --height=1.82

## Teile, die im Spiel nie zu sehen sind (Mund bleibt geschlossen).
const DROP := ["teeth", "tongue", "tearline", "eyeocclusion", "occlusion"]

## Materialien, deren Textur Durchsichtigkeit braucht. Ohne sie legen sich
## Haarbueschel und Wimpern als schwarze Flaechen ueber das Gesicht.
const CUTOUT := ["transparency", "hair", "scalp", "eyelash", "brow", "tearline", "cornea"]
## Materialien der Haut: matter und etwas weicher als Stoff.
const SKIN := ["skin", "nails"]

## Knochenrollen -> moegliche Namen im Modell, kleingeschrieben.
## Abgedeckt sind die ueblichen Benennungen: Unreal/Rigify (thigh_l),
## Mixamo (leftupleg), Character Creator (cc_base_l_thigh), Blender (.l).
const BONE_ROLES := {
	"hips":        ["pelvis", "hips", "cc_base_hip", "mixamorig:hips", "root_hip"],
	"spine":       ["spine_01", "spine", "cc_base_spine01", "mixamorig:spine"],
	"chest":       ["spine_02", "chest", "cc_base_spine02", "mixamorig:spine1"],
	"upper_chest": ["spine_03", "upperchest", "cc_base_spine03", "mixamorig:spine2"],
	"neck":        ["neck_01", "neck", "cc_base_necktwist01", "mixamorig:neck"],
	"head":        ["head", "cc_base_head", "mixamorig:head"],
	"thigh_l":     ["thigh_l", "leftupleg", "cc_base_l_thigh", "thigh.l", "mixamorig:leftupleg"],
	"thigh_r":     ["thigh_r", "rightupleg", "cc_base_r_thigh", "thigh.r", "mixamorig:rightupleg"],
	"calf_l":      ["calf_l", "leftleg", "cc_base_l_calf", "shin.l", "mixamorig:leftleg"],
	"calf_r":      ["calf_r", "rightleg", "cc_base_r_calf", "shin.r", "mixamorig:rightleg"],
	"foot_l":      ["foot_l", "leftfoot", "cc_base_l_foot", "foot.l", "mixamorig:leftfoot"],
	"foot_r":      ["foot_r", "rightfoot", "cc_base_r_foot", "foot.r", "mixamorig:rightfoot"],
	"upperarm_l":  ["upperarm_l", "leftarm", "cc_base_l_upperarm", "upper_arm.l", "mixamorig:leftarm"],
	"upperarm_r":  ["upperarm_r", "rightarm", "cc_base_r_upperarm", "upper_arm.r", "mixamorig:rightarm"],
	"lowerarm_l":  ["lowerarm_l", "leftforearm", "cc_base_l_forearm", "forearm.l", "mixamorig:leftforearm"],
	"lowerarm_r":  ["lowerarm_r", "rightforearm", "cc_base_r_forearm", "forearm.r", "mixamorig:rightforearm"],
	"hand_l":      ["hand_l", "lefthand", "cc_base_l_hand", "hand.l", "mixamorig:lefthand"],
	"hand_r":      ["hand_r", "righthand", "cc_base_r_hand", "hand.r", "mixamorig:righthand"],
}

var src := ""
var dst := ""
var disp_name := "Person"
var want_height := 0.0
var bulk := 1.0
var drop_regions: Array = []


func _init() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--in="):
			src = a.substr(5)
		elif a.begins_with("--out="):
			dst = a.substr(6)
		elif a.begins_with("--name="):
			disp_name = a.substr(7)
		elif a.begins_with("--height="):
			want_height = float(a.substr(9))
		elif a.begins_with("--bulk="):
			bulk = float(a.substr(7))
		elif a.begins_with("--drop-region="):
			# Mesh:x0,y0,z0,x1,y1,z1 in Anteilen der Koerperausdehnung
			var two := a.substr(14).split(":")
			if two.size() == 2:
				var v := str(two[1]).split(",")
				if v.size() >= 6:
					drop_regions.append({
						"mesh": str(two[0]).to_lower(),
						"box": AABB(
							Vector3(float(v[0]), float(v[1]), float(v[2])),
							Vector3(float(v[3]) - float(v[0]), float(v[4]) - float(v[1]), float(v[5]) - float(v[2]))),
					})
					continue
			print("WARNUNG: --drop-region=Mesh:x0,y0,z0,x1,y1,z1 erwartet, ignoriert: ", a)
	if src == "" or dst == "":
		print("Aufruf: --in=<modell> --out=<szene> [--name=..] [--height=1.82]")
		print("        [--bulk=1.07] [--drop-region=Vest:0,0.84,0,1,1,1]")
		quit(1)
		return
	_run()
	quit(0)


func _run() -> void:
	if not ResourceLoader.exists(src):
		print("FEHLER: ", src, " nicht gefunden")
		quit(1)
		return
	var t0 := Time.get_ticks_msec()
	var root: Node3D = (load(src) as PackedScene).instantiate() as Node3D
	root.name = "Person"

	# --- Unsichtbare Teile wegwerfen
	var dropped := 0
	var dropped_tris := 0
	var meshes: Array = []
	var skeleton: Skeleton3D = null
	var stack: Array = [root]
	while not stack.is_empty():
		var n: Node = stack.pop_back()
		for c in n.get_children():
			stack.append(c)
		if n is MeshInstance3D:
			meshes.append(n)
		elif n is Skeleton3D and skeleton == null:
			skeleton = n
	var kills: Array = []
	for m in meshes:
		var low := String(m.name).to_lower()
		for d in DROP:
			if low.contains(d):
				kills.append(m)
				break
	for m in kills:
		dropped_tris += _tris(m)
		dropped += 1
		meshes.erase(m)
		m.get_parent().remove_child(m)
		m.queue_free()

	# --- Lage: manche Modelle kommen mit Z nach oben und liegen dadurch.
	# Die Drehung muss vor dem Schneiden stehen, damit sich ein Schnittbereich
	# immer auf die aufgerichtete Figur bezieht.
	var pre := _bounds(meshes)
	if pre.size.z > pre.size.y * 1.4:
		root.rotation = Vector3(-PI * 0.5, 0.0, 0.0)
		for m in meshes:
			(m as MeshInstance3D).force_update_transform()
		print("Modell lag auf dem Ruecken (Z nach oben) - aufgerichtet")

	# --- Bereiche wegschneiden (etwa eine Kapuze, die ueber dem Kopf liegt).
	var cut_tris := _cut_regions(meshes)

	# --- Materialien nachbessern: Durchsichtigkeit, Haut, Rueckseiten.
	# Die Materialien gehoeren zur importierten Datei, deshalb wird mit einer
	# Kopie als Ueberschreibung am Knoten gearbeitet - nur die wird mitgespeichert.
	var cut := 0
	for m in meshes:
		var mi := m as MeshInstance3D
		for si in mi.mesh.get_surface_count():
			var base := mi.mesh.surface_get_material(si)
			if not (base is BaseMaterial3D):
				continue
			var mat := (base as BaseMaterial3D).duplicate() as BaseMaterial3D
			# Der FBX-Import setzt zwei Schalter, die das Modell unbrauchbar
			# machen: Eckpunktfarben als Albedo (die sind schwarz, die Haut
			# wird dadurch schwarz) und Eigenleuchten in Weiss.
			mat.vertex_color_use_as_albedo = false
			mat.emission_enabled = false
			var low := mat.resource_name.to_lower() + " " + String(mi.name).to_lower()
			var is_cut := false
			for c in CUTOUT:
				if low.contains(c):
					is_cut = true
					break
			if is_cut:
				mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
				mat.alpha_scissor_threshold = 0.5
				mat.cull_mode = BaseMaterial3D.CULL_DISABLED
				mat.specular_mode = BaseMaterial3D.SPECULAR_DISABLED if low.contains("eyelash") else mat.specular_mode
				cut += 1
			for sk in SKIN:
				if low.contains(sk):
					mat.roughness = 0.62
					mat.metallic = 0.0
					break
			if low.contains("cornea"):
				mat.alpha_scissor_threshold = 0.9
				mat.roughness = 0.05
			mi.set_surface_override_material(si, mat)

	# --- Morphziele entfernen. Figuren aus Charaktergeneratoren bringen
	# hunderte Gesichts-Morphs mit (Mimik, Lippensynchronisation). Das Spiel
	# nutzt keine davon, sie machen aber den Loewenanteil der Datei aus:
	# beim Beispielmodell 107 MB gegenueber 10 MB ohne.
	var shapes_dropped := 0
	for m in meshes:
		var mi := m as MeshInstance3D
		var src_mesh := mi.mesh
		if src_mesh.get_blend_shape_count() <= 0:
			continue
		shapes_dropped += src_mesh.get_blend_shape_count()
		var fresh := ArrayMesh.new()
		fresh.resource_name = src_mesh.resource_name
		for si in src_mesh.get_surface_count():
			var arrays: Array = src_mesh.surface_get_arrays(si)
			# Knochen und Gewichte muessen mit, sonst haengt das Mesh nicht
			# mehr am Skelett. Acht Gewichte je Eckpunkt bleiben acht.
			var flags: int = int(src_mesh.surface_get_format(si)) & Mesh.ARRAY_FLAG_USE_8_BONE_WEIGHTS
			fresh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays, [], {}, flags)
			fresh.surface_set_name(si, src_mesh.surface_get_name(si))
			fresh.surface_set_material(si, src_mesh.surface_get_material(si))
		mi.mesh = fresh

	# --- Groesse und Statur
	for m in meshes:
		(m as MeshInstance3D).force_update_transform()
	var box := _bounds(meshes)
	if want_height > 0.0 and box.size.y > 0.0001:
		var s: float = want_height / box.size.y
		# `bulk` verbreitert die Figur, ohne sie groesser zu machen. Modelle aus
		# Charaktergeneratoren sind oft auffallend schlank; sieben Prozent mehr
		# Breite wirken deutlich, ohne etwas sichtbar zu verzerren.
		root.scale = Vector3(s * bulk, s, s * bulk)
		box = AABB(Vector3(box.position.x * s * bulk, box.position.y * s, box.position.z * s * bulk),
			Vector3(box.size.x * s * bulk, box.size.y * s, box.size.z * s * bulk))

	# --- Knochenrollen zuordnen
	var roles := {}
	var missing: Array = []
	if skeleton != null:
		var by_name := {}
		for i in skeleton.get_bone_count():
			by_name[skeleton.get_bone_name(i).to_lower()] = i
		for role in BONE_ROLES:
			var found := -1
			for cand in BONE_ROLES[role]:
				if by_name.has(cand):
					found = int(by_name[cand])
					break
			if found >= 0:
				roles[role] = skeleton.get_bone_name(found)
			else:
				missing.append(role)

	var total := 0
	for m in meshes:
		total += _tris(m)

	root.set_meta("display_name", disp_name)
	root.set_meta("triangles", total)
	root.set_meta("height", box.size.y)
	root.set_meta("bone_roles", roles)
	if skeleton != null:
		root.set_meta("skeleton_path", root.get_path_to(skeleton))

	_own(root, root)
	var scene := PackedScene.new()
	var perr := scene.pack(root)
	var err := ResourceSaver.save(scene, dst)
	print("Meshes: %d (%d verworfen, %d Dreiecke gespart)" % [meshes.size(), dropped, dropped_tris])
	print("Materialien mit Durchsichtigkeit: %d  weggeschnittene Dreiecke: %d" % [cut, cut_tris])
	print("Morphziele entfernt: %d" % shapes_dropped)
	print("Dreiecke: %d  Hoehe: %.2f m" % [total, box.size.y])
	print("Knochen zugeordnet: %d von %d" % [roles.size(), BONE_ROLES.size()])
	if not missing.is_empty():
		print("  NICHT gefunden: ", missing)
	print("Gespeichert: %s (pack %d, save %d, %d ms)" % [dst, perr, err, Time.get_ticks_msec() - t0])


## Schneidet Bereiche aus benannten Meshes heraus, etwa eine Kapuze, die ueber
## dem Kopf liegt und die Figur wie eingepackt aussehen laesst.
##
## Entfernt werden nur Eintraege aus der Dreiecksliste. Die Eckpunkte bleiben
## stehen, auch die unbenutzten: Knochen und Gewichte haengen an ihnen, und ein
## Umsortieren waere die haeufigste Fehlerquelle beim Umgang mit verformten
## Meshes. Die paar uebrigen Eckpunkte kosten nichts.
func _cut_regions(meshes: Array) -> int:
	if drop_regions.is_empty():
		return 0
	var body := _bounds(meshes)
	var removed := 0
	for m in meshes:
		var mi := m as MeshInstance3D
		var low := String(mi.name).to_lower()
		var boxes: Array = []
		for r in drop_regions:
			if low.contains(str(r.mesh)):
				var b: AABB = r.box
				boxes.append(AABB(
					body.position + Vector3(b.position.x * body.size.x, b.position.y * body.size.y, b.position.z * body.size.z),
					Vector3(b.size.x * body.size.x, b.size.y * body.size.y, b.size.z * body.size.z)))
		if boxes.is_empty():
			continue
		var xf := mi.global_transform
		var fresh := ArrayMesh.new()
		fresh.resource_name = mi.mesh.resource_name
		var changed := false
		for si in mi.mesh.get_surface_count():
			var arrays: Array = mi.mesh.surface_get_arrays(si)
			var verts: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
			var idx: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
			if not verts.is_empty() and not idx.is_empty():
				var keep := PackedInt32Array()
				for t in range(0, idx.size() - 2, 3):
					var a := idx[t]
					var b2 := idx[t + 1]
					var c := idx[t + 2]
					var mid: Vector3 = xf * ((verts[a] + verts[b2] + verts[c]) / 3.0)
					var hit := false
					for bx in boxes:
						if (bx as AABB).has_point(mid):
							hit = true
							break
					if hit:
						removed += 1
						continue
					keep.append(a)
					keep.append(b2)
					keep.append(c)
				if keep.size() != idx.size():
					arrays[Mesh.ARRAY_INDEX] = keep
					changed = true
			var flags: int = int(mi.mesh.surface_get_format(si)) & Mesh.ARRAY_FLAG_USE_8_BONE_WEIGHTS
			fresh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays, [], {}, flags)
			fresh.surface_set_name(si, mi.mesh.surface_get_name(si))
			fresh.surface_set_material(si, mi.mesh.surface_get_material(si))
		if changed:
			mi.mesh = fresh
	return removed


func _bounds(meshes: Array) -> AABB:
	var out := AABB()
	var first := true
	for m in meshes:
		var mi := m as MeshInstance3D
		var b: AABB = mi.global_transform * mi.mesh.get_aabb()
		if first:
			out = b
			first = false
		else:
			out = out.merge(b)
	return out


func _tris(m: MeshInstance3D) -> int:
	var n := 0
	for s in m.mesh.get_surface_count():
		var idx: PackedInt32Array = m.mesh.surface_get_arrays(s)[Mesh.ARRAY_INDEX]
		n += idx.size() / 3 if idx.size() > 0 else m.mesh.surface_get_array_len(s) / 3
	return n


func _own(n: Node, owner_node: Node) -> void:
	for c in n.get_children():
		c.owner = owner_node
		_own(c, owner_node)
