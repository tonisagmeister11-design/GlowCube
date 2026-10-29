extends SceneTree
## Bereitet ein zugeliefertes Fahrzeugmodell (FBX/GLB) fuer das Spiel auf.
##
## Fertige Modelle aus Modellierprogrammen sind fuer Standbilder gebaut, nicht
## fuer ein Spiel: hunderte Einzelteile, Millionen Dreiecke, beliebiger Maßstab
## und beliebige Ausrichtung. Dieses Werkzeug macht daraus ein Spielfahrzeug:
##
##   1. liest das Modell und zerlegt es in Teil-und-Oberflaeche,
##   2. bringt es auf echte Groesse und richtet es nach vorn (-Z) aus,
##   3. trennt die vier Raeder ab (ueber Namen, sonst ueber die Geometrie),
##   4. fuehrt alles Uebrige nach Materialgruppe zu wenigen Meshes zusammen,
##   5. vereinfacht die Geometrie auf ein Dreiecksbudget (meshoptimizer),
##   6. legt PBR-Materialien an - oder behaelt mitgelieferte Texturen,
##   7. erzeugt eine vereinfachte Fassung fuer Ferne und Leistungsmodus,
##   8. speichert das Ergebnis als binaere Szene.
##
## Aufruf:
##   godot --headless --path . --script tools/prepare_vehicle.gd -- \
##       --in=res://assets/models/vehicles/auto.fbx \
##       --out=res://assets/models/vehicles/auto.scn \
##       --name="Vanari Furio" --color=1,0.72,0.05 \
##       --length=4.9 --budget=28000 [--textures] [--no-interior] \
##       [--albedo=res://.../textur.jpg]
##
## --length   Sollaenge in Metern; das Modell wird gleichmaessig darauf skaliert.
##            Ohne Angabe bleibt der Maßstab des Modells.
## --budget   Dreiecke fuer die gesamte Karosserie (Standard 30000). Raeder
##            bekommen zusaetzlich ein Fuenftel davon.
## --textures Mitgelieferte Texturen behalten, statt eigene Materialien zu
##            setzen (nur sinnvoll, wenn die Texturen wirklich vorhanden sind).
## --no-interior  Innenraumteile ganz weglassen (spart viel bei Modellen mit
##            vollstaendig modelliertem Cockpit).
## --albedo   Farbtextur von Hand zuweisen. Noetig, wenn das Modell seine
##            Textur in einem Nebenordner erwartet, der nicht mitkam.

## Materialgruppen. Geprueft wird der Materialname des Modells und der
## Teilename, kleingeschrieben, als Teilstring. Reihenfolge entscheidet.
const GROUP_KEYS := [
	["glass tail", "lamp_red"], ["tail lights", "lamp_red"], ["taillight", "lamp_red"],
	["tail_light", "lamp_red"], ["rueckleuchte", "lamp_red"],
	["headlight", "lamp_white"], ["head_light", "lamp_white"],
	["daylight", "lamp_white"], ["scheinwerfer", "lamp_white"],
	["carbon fibre", "carbon"], ["carbonfiber", "carbon"], ["carbon", "carbon"],
	["car paint", "paint"], ["carpaint", "paint"], ["paintedmetal", "paint"],
	["extr_body", "paint"], ["greenbody", "paint"], ["bodypaint", "paint"],
	["lack", "paint"], ["red metal", "paint"], ["paint", "paint"],
	["glossy mirror", "chrome"], ["chrome", "chrome"], ["mirror", "chrome"],
	["orange glasses", "lamp_amber"],
	["glass darker", "glass_tinted"], ["black glass", "glass_tinted"],
	["glasstinted", "glass_tinted"],
	["window", "glass"], ["windscreen", "glass"], ["glass", "glass"],
	["brake disk", "metal"], ["brakedisk", "metal"], ["disk", "metal"],
	["aluminum", "metal"], ["aluminium", "metal"], ["steel", "metal"],
	["black metal", "dark_metal"],
	["rims", "chrome"], ["rim", "chrome"],
	["tire", "rubber"], ["rubber", "rubber"], ["reifen", "rubber"],
	["black matte", "dark"], ["blackbody", "dark"], ["matte grey", "plastic"],
	["extr_black", "dark"], ["extr_rubber", "rubber"], ["extr_chrome", "chrome"],
	["extr_glass", "glass"], ["extr_metal", "metal"], ["extr_far", "interior"],
	["leather", "interior"], ["alcantara", "interior"], ["fabric", "interior"],
	["farbic", "interior"], ["farcic", "interior"], ["seat", "interior"],
	["display", "lamp_white"], ["decal", "detail"], ["label", "detail"],
	["plastic", "plastic"], ["grille", "dark"], ["frame", "plastic"],
	["emission", "lamp_white"], ["metal", "metal"],
]
## Gruppen, die im Innenraum liegen: aus der Ferne unsichtbar, im
## Leistungsmodus ganz aus.
const INTERIOR_GROUPS := ["interior"]
## Teilenamen, die auf Innenraum hindeuten (zusaetzlich zur Materialgruppe).
const INTERIOR_HINTS := ["seat", "dash", "steeringwheel", "steering_wheel", "interior",
	"cabin", "console", "pedal", "carpet", "trunkliner", "trunkbay", "doorpanel",
	"door_panel", "sunvisor", "intr_", "airbag", "glovebox", "handbrake", "gearlever",
	"seatbelt", "headrest", "cupholder", "air_slider", "air_control"]

var src := ""
var dst := ""
var disp_name := "Fahrzeug"
var paint := Color(0.86, 0.12, 0.08)
var want_length := 0.0
var budget := 30000
var keep_tex := false
var drop_interior := false
var albedo_path := ""


func _init() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--in="):
			src = a.substr(5)
		elif a.begins_with("--out="):
			dst = a.substr(6)
		elif a.begins_with("--name="):
			disp_name = a.substr(7)
		elif a.begins_with("--length="):
			want_length = float(a.substr(9))
		elif a.begins_with("--budget="):
			budget = int(a.substr(9))
		elif a == "--textures":
			keep_tex = true
		elif a.begins_with("--albedo="):
			albedo_path = a.substr(9)
		elif a == "--no-interior":
			drop_interior = true
		elif a.begins_with("--color="):
			var p := a.substr(8).split(",")
			if p.size() >= 3:
				paint = Color(float(p[0]), float(p[1]), float(p[2]))
	if src == "" or dst == "":
		print("Aufruf: --in=<modell> --out=<szene> [--name=..] [--color=r,g,b]")
		print("        [--length=4.9] [--budget=30000] [--textures] [--no-interior]")
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
	var model: Node = (load(src) as PackedScene).instantiate()
	var model3 := model as Node3D

	# --- 1. Teile einsammeln, je Oberflaeche einzeln (Modelle haben oft ein
	#        einziges Mesh mit zwanzig Materialien)
	var parts: Array = []
	var stack: Array = [model]
	while not stack.is_empty():
		var c: Node = stack.pop_back()
		for ch in c.get_children():
			stack.append(ch)
		if c is MeshInstance3D and (c as MeshInstance3D).mesh != null:
			var mi := c as MeshInstance3D
			var xf := _global_of(c, model3)
			for s in mi.mesh.get_surface_count():
				if mi.mesh.surface_get_primitive_type(s) != Mesh.PRIMITIVE_TRIANGLES:
					continue
				var mat: Material = mi.mesh.surface_get_material(s)
				var mname := ""
				if mat != null:
					mname = mat.resource_name
				parts.append({
					"mesh": mi.mesh, "surface": s, "xf": xf,
					"name": String(mi.name), "matname": mname, "mat": mat,
				})
	if parts.is_empty():
		print("FEHLER: keine Dreiecks-Oberflaechen im Modell")
		quit(1)
		return

	# --- 2. Maßstab und Ausrichtung
	var raw := _bounds(parts)
	var fix := _orient_and_scale(raw)
	for p in parts:
		p.xf = fix * p.xf
	var aabb := _bounds(parts)
	# auf dem Boden absetzen und mittig zentrieren
	var shift := Transform3D(Basis(), Vector3(-aabb.get_center().x, -aabb.position.y, -aabb.get_center().z))
	for p in parts:
		p.xf = shift * p.xf
	aabb = _bounds(parts)

	# --- 3. Gruppen bilden, Raeder abtrennen
	var wheels := {"FL": [], "FR": [], "RL": [], "RR": []}
	var groups := {}
	var named_wheels := false
	for p in parts:
		if _wheel_slot(p) != "":
			named_wheels = true
			break
	var dropped_interior := 0
	for p in parts:
		var g := _group_of(p)
		if drop_interior and _is_interior(p, g):
			dropped_interior += 1
			continue
		var slot := ""
		if named_wheels:
			slot = _wheel_slot(p)
		elif _looks_like_wheel(p, aabb):
			slot = _quadrant(p, aabb)
		if slot != "":
			wheels[slot].append(p)
		else:
			if not groups.has(g):
				groups[g] = []
			groups[g].append(p)

	# Modelle aus einem einzigen Netz haben keine Radknoten. Dann werden die
	# Raeder dreieckweise aus den passenden Gruppen herausgeschnitten.
	var split_wheels := 0
	var any_wheel := false
	for slot in wheels:
		if not (wheels[slot] as Array).is_empty():
			any_wheel = true
			break
	if not any_wheel:
		split_wheels = _triangle_wheels(groups, aabb, wheels)

	# --- 4. Szene aufbauen
	var root := Node3D.new()
	root.name = "Vehicle"
	var body := Node3D.new()
	body.name = "Body"
	root.add_child(body)
	body.owner = root

	# Budget auf die Gruppen nach ihrem Anteil verteilen
	var raw_tris := {}
	var raw_total := 0
	for g in groups:
		var n := 0
		for p in groups[g]:
			n += _tris_of(p)
		raw_tris[g] = n
		raw_total += n

	var total_tris := 0
	var merged := 0
	for g in groups:
		var share := budget
		if raw_total > 0:
			share = maxi(600, int(round(float(budget) * float(raw_tris[g]) / float(raw_total))))
		var mesh := _merge(groups[g])
		if mesh == null:
			continue
		var before := _mesh_tris(mesh)
		# Lack und Carbon sind die Aussenhaut. Dort wuerde die Rasterung die
		# Flaechen aufreissen, also bleibt es bei meshoptimizer - lieber ein
		# paar Dreiecke mehr als eine zerknitterte Karosserie.
		mesh = _decimate(mesh, share, not ["paint", "carbon"].has(str(g)))
		print("   Gruppe %-12s %7d -> %7d (Budget %d)" % [str(g), before, _mesh_tris(mesh), share])
		var mi := MeshInstance3D.new()
		mi.name = "Body_" + str(g)
		mi.mesh = mesh
		mi.material_override = _material_for(str(g), groups[g])
		body.add_child(mi)
		mi.owner = root
		merged += 1
		total_tris += _mesh_tris(mesh)

	# --- 5. Raeder als drehbare Knoten
	var wheel_nodes := 0
	var wheel_budget := maxi(1200, budget / 5)
	for slot in wheels:
		var list: Array = wheels[slot]
		if list.is_empty():
			continue
		var center := _center_of(list)
		var pivot := Node3D.new()
		pivot.name = "Wheel" + str(slot)
		pivot.position = center
		root.add_child(pivot)
		pivot.owner = root
		var rel: Array = []
		for p in list:
			var q: Dictionary = p.duplicate()
			q.xf = Transform3D(Basis(), -center) * p.xf
			rel.append(q)
		var wm := _merge(rel)
		if wm != null:
			wm = _decimate(wm, wheel_budget / 4)
			var wmi := MeshInstance3D.new()
			wmi.name = "Mesh"
			wmi.mesh = wm
			wmi.material_override = _material_for("rubber", list)
			pivot.add_child(wmi)
			wmi.owner = root
			total_tris += _mesh_tris(wm)
		wheel_nodes += 1

	# --- 6. Vereinfachte Fassung: nur Aussenhaut, hart reduziert
	var far_parts: Array = []
	for g in groups:
		if INTERIOR_GROUPS.has(g):
			continue
		for p in groups[g]:
			if not _is_interior(p, str(g)):
				far_parts.append(p)
	var far_tris := 0
	var far_mesh := _merge(far_parts)
	if far_mesh != null:
		far_mesh = _decimate(far_mesh, maxi(900, budget / 8))
		var far := MeshInstance3D.new()
		far.name = "Far"
		far.mesh = far_mesh
		far.material_override = _material_for("paint", far_parts)
		far.visible = false
		root.add_child(far)
		far.owner = root
		far_tris = _mesh_tris(far_mesh)

	root.set_meta("size", aabb.size)
	root.set_meta("display_name", disp_name)
	root.set_meta("triangles", total_tris)
	root.set_meta("triangles_far", far_tris)

	var scene := PackedScene.new()
	scene.pack(root)
	var err := ResourceSaver.save(scene, dst)
	print("Oberflaechen gelesen: %d  Rohdreiecke: %d" % [parts.size(), raw_total])
	print("Gruppen: %d  Raeder: %d (dreieckweise %d)  Innenraum verworfen: %d" % [merged, wheel_nodes, split_wheels, dropped_interior])
	print("Dreiecke: %d (Ferne %d)  Gruppen: %s" % [total_tris, far_tris, str(groups.keys())])
	print("Groesse (m): %.2f breit, %.2f hoch, %.2f lang" % [aabb.size.x, aabb.size.y, aabb.size.z])
	print("Gespeichert: %s (Fehler %d, %d ms)" % [dst, err, Time.get_ticks_msec() - t0])


# ============================================================ Maßstab, Lage

func _bounds(parts: Array) -> AABB:
	var out := AABB()
	var first := true
	for p in parts:
		var b: AABB = p.xf * _surface_aabb_of(p)
		if first:
			out = b
			first = false
		else:
			out = out.merge(b)
	return out


## Arrays eines Teils: entweder aus dem Modell oder bereits mitgegeben
## (so entstehen beim dreieckweisen Abtrennen der Raeder neue Teile).
func _part_arrays(p: Dictionary) -> Array:
	if p.has("arrays"):
		return p.arrays
	return (p.mesh as Mesh).surface_get_arrays(int(p.surface))


func _surface_aabb_of(p: Dictionary) -> AABB:
	var arrays: Array = _part_arrays(p)
	var v: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
	if v.is_empty():
		return AABB()
	var out := AABB(v[0], Vector3.ZERO)
	for i in range(1, v.size()):
		out = out.expand(v[i])
	return out


func _surface_aabb(mesh: Mesh, surface: int) -> AABB:
	var arrays: Array = mesh.surface_get_arrays(surface)
	var v: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
	if v.is_empty():
		return AABB()
	var out := AABB(v[0], Vector3.ZERO)
	for i in range(1, v.size()):
		out = out.expand(v[i])
	return out


## Dreht das Modell so, dass die laengste waagerechte Achse nach -Z zeigt
## (Blickrichtung in Godot), und skaliert auf die gewuenschte Laenge.
func _orient_and_scale(b: AABB) -> Transform3D:
	var basis := Basis()
	var length := b.size.z
	if b.size.x > b.size.z:
		basis = Basis(Vector3.UP, PI * 0.5)
		length = b.size.x
	var s := 1.0
	if want_length > 0.0 and length > 0.0001:
		s = want_length / length
	return Transform3D(basis.scaled(Vector3(s, s, s)), Vector3.ZERO)


# ============================================================ Gruppen, Raeder

func _global_of(node: Node, root: Node3D) -> Transform3D:
	var t := Transform3D()
	var cur: Node = node
	while cur != null and cur != root.get_parent():
		if cur is Node3D:
			t = (cur as Node3D).transform * t
		cur = cur.get_parent()
	return t


func _group_of(p: Dictionary) -> String:
	var hay := (str(p.matname) + " " + str(p.name)).to_lower()
	for e in GROUP_KEYS:
		if hay.contains(str(e[0])):
			return str(e[1])
	return "plastic"


func _is_interior(p: Dictionary, group: String) -> bool:
	if INTERIOR_GROUPS.has(group):
		return true
	var n := (str(p.name) + " " + str(p.matname)).to_lower()
	for h in INTERIOR_HINTS:
		if n.contains(h):
			return true
	return false


## Radposition aus dem Namen des Teils.
func _wheel_slot(p: Dictionary) -> String:
	var n := str(p.name).to_lower()
	if not (n.contains("wheel") or n.contains("tire") or n.contains("tyre")
			or n.begins_with("rim") or n.contains("_rim") or n.contains("brake")):
		return ""
	if n.contains("fl") or n.contains("lf") or n.contains("frontleft") or n.contains("front_left"):
		return "FL"
	if n.contains("fr") or n.contains("rf") or n.contains("frontright") or n.contains("front_right"):
		return "FR"
	if n.contains("rl") or n.contains("lr") or n.contains("rearleft") or n.contains("rear_left"):
		return "RL"
	if n.contains("rr") or n.contains("rearright") or n.contains("rear_right"):
		return "RR"
	return ""


## Ohne brauchbare Namen: Ein Rad liegt unten, seitlich aussen und ist in der
## Seitenansicht ungefaehr rund. Das trifft Reifen, Felgen und Bremsscheiben.
func _looks_like_wheel(p: Dictionary, veh: AABB) -> bool:
	var b: AABB = p.xf * _surface_aabb_of(p)
	if b.size.y <= 0.0001:
		return false
	if b.get_center().y > veh.position.y + veh.size.y * 0.5:
		return false
	if b.size.y > veh.size.y * 0.55 or b.size.z > veh.size.z * 0.35:
		return false
	if b.size.y < veh.size.y * 0.12:
		return false
	var round_ratio := b.size.z / maxf(b.size.y, 0.0001)
	if round_ratio < 0.55 or round_ratio > 1.8:
		return false
	return absf(b.get_center().x) > veh.size.x * 0.15


func _quadrant(p: Dictionary, veh: AABB) -> String:
	var c: Vector3 = (p.xf * _surface_aabb_of(p)).get_center()
	var left := c.x > veh.get_center().x
	var front := c.z < veh.get_center().z
	if front:
		return "FL" if left else "FR"
	return "RL" if left else "RR"


func _center_of(list: Array) -> Vector3:
	var out := AABB()
	var first := true
	for p in list:
		var b: AABB = p.xf * _surface_aabb_of(p)
		if first:
			out = b
			first = false
		else:
			out = out.merge(b)
	return out.get_center()


## Schneidet die vier Raeder dreieckweise aus den Gruppen heraus, die ein Rad
## bilden koennen. Ein Raddreieck liegt in der unteren Haelfte, seitlich
## aussen und nicht in der Fahrzeugmitte der Laenge.
func _triangle_wheels(groups: Dictionary, veh: AABB, wheels: Dictionary) -> int:
	var cx: float = veh.get_center().x
	var cz: float = veh.get_center().z
	var y_max: float = veh.position.y + veh.size.y * 0.45
	var x_min: float = veh.size.x * 0.20
	var z_min: float = veh.size.z * 0.14
	var moved := 0
	for g in ["rubber", "chrome", "metal", "dark_metal"]:
		if not groups.has(g):
			continue
		var keep: Array = []
		for p in groups[g]:
			var arrays: Array = _part_arrays(p)
			var verts: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
			var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
			if verts.is_empty() or indices.is_empty():
				keep.append(p)
				continue
			var xf: Transform3D = p.xf
			var buckets := {"FL": PackedInt32Array(), "FR": PackedInt32Array(),
				"RL": PackedInt32Array(), "RR": PackedInt32Array(), "": PackedInt32Array()}
			for t in range(0, indices.size() - 2, 3):
				var a := indices[t]
				var b := indices[t + 1]
				var c := indices[t + 2]
				var mid: Vector3 = xf * ((verts[a] + verts[b] + verts[c]) / 3.0)
				var slot := ""
				if mid.y <= y_max and absf(mid.x - cx) >= x_min and absf(mid.z - cz) >= z_min:
					if mid.z < cz:
						slot = "FL" if mid.x > cx else "FR"
					else:
						slot = "RL" if mid.x > cx else "RR"
				var bk: PackedInt32Array = buckets[slot]
				bk.append(a)
				bk.append(b)
				bk.append(c)
				buckets[slot] = bk
			for slot in buckets:
				var bi: PackedInt32Array = buckets[slot]
				if bi.is_empty():
					continue
				var sub: Dictionary = {
					"arrays": _compact_arrays(arrays, bi), "xf": xf,
					"name": str(p.name) + ("_" + str(slot) if slot != "" else ""),
					"matname": p.matname, "mat": p.get("mat"), "mesh": p.get("mesh"),
					"surface": p.get("surface", 0),
				}
				if slot == "":
					keep.append(sub)
				else:
					(wheels[slot] as Array).append(sub)
					moved += 1
		groups[g] = keep
	return moved


# ============================================================ Geometrie

func _tris_of(p: Dictionary) -> int:
	var arrays: Array = _part_arrays(p)
	var idx: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
	if idx.size() > 0:
		return idx.size() / 3
	return (arrays[Mesh.ARRAY_VERTEX] as PackedVector3Array).size() / 3


func _mesh_tris(mesh: ArrayMesh) -> int:
	var n := 0
	for s in mesh.get_surface_count():
		var idx: PackedInt32Array = mesh.surface_get_arrays(s)[Mesh.ARRAY_INDEX]
		if idx.size() > 0:
			n += idx.size() / 3
		else:
			n += mesh.surface_get_array_len(s) / 3
	return n


## Fuehrt mehrere Oberflaechen zu einer zusammen.
func _merge(list: Array) -> ArrayMesh:
	if list.is_empty():
		return null
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	var any := false
	for p in list:
		var arrays: Array = _part_arrays(p)
		var verts: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
		if verts.is_empty():
			continue
		var xf: Transform3D = p.xf
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
				st.set_normal((nb * normals[i]).normalized())
			if i < uvs.size():
				st.set_uv(uvs[i])
			st.add_vertex(xf * verts[i])
			any = true
	if not any:
		return null
	st.index()
	if st.get_aabb().size == Vector3.ZERO:
		return null
	return st.commit()


## Vereinfacht ein Mesh auf hoechstens `target` Dreiecke. Godot bringt
## meshoptimizer ueber ImporterMesh mit; die erzeugten Detailstufen werden
## durchgesehen und die feinste genommen, die noch ins Budget passt.
## Danach werden unbenutzte Eckpunkte entfernt, sonst bleibt die Datei gross.
func _decimate(mesh: ArrayMesh, target: int, allow_cluster := true) -> ArrayMesh:
	if mesh == null or mesh.get_surface_count() == 0:
		return mesh
	var arrays: Array = mesh.surface_get_arrays(0)
	var idx: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
	if idx.is_empty() or idx.size() / 3 <= target:
		return mesh
	# meshoptimizer halbiert je Stufe, die Leiter springt also grob. Deshalb
	# wird das Ergebnis erneut eingespeist und das Ziel von oben angenaehert:
	# lieber etwas ueber dem Budget als eine zerknitterte Karosserie.
	var cur: Array = arrays
	var cur_tris: int = idx.size() / 3
	for pass_no in 16:
		if cur_tris <= target:
			break
		var step := _lod_once(cur, target, cur_tris)
		if step.is_empty() and allow_cluster:
			# meshoptimizer verweigert manche Netze (offene Flaechen, doppelte
			# Dreiecke - typisch fuer Glasscheiben). Dann wird gerastert.
			step = _cluster(cur, target)
		if step.is_empty():
			break
		var ni: PackedInt32Array = step[Mesh.ARRAY_INDEX]
		var nt: int = ni.size() / 3
		if nt <= 0 or nt >= cur_tris:
			break
		# Wuerde der naechste Schritt weit unter das Budget fallen und sind wir
		# ohnehin schon nahe dran, bleibt es bei der feineren Fassung.
		if nt < target * 6 / 10 and cur_tris <= target * 3 / 2:
			break
		cur = step
		cur_tris = nt
	if cur_tris >= idx.size() / 3:
		return mesh
	var out := ArrayMesh.new()
	out.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, cur)
	return out


## Ein Vereinfachungsdurchgang. Gewaehlt wird die kleinste Detailstufe, die
## noch ueber dem Ziel liegt (damit die naechste Runde feiner nachregeln kann);
## gibt es keine solche, die feinste unterhalb des Ziels.
func _lod_once(arrays: Array, target: int, cur_tris: int) -> Array:
	var im := ImporterMesh.new()
	im.add_surface(Mesh.PRIMITIVE_TRIANGLES, arrays, [], {}, null, "s", 0)
	im.generate_lods(25.0, 60.0, [])
	var lods := im.get_surface_lod_count(0)
	if lods <= 0:
		return []
	var pick := -1
	for l in lods:
		var sz: int = im.get_surface_lod_indices(0, l).size() / 3
		if sz > target and sz < cur_tris:
			pick = l
	if pick < 0:
		for l in lods:
			var sz2: int = im.get_surface_lod_indices(0, l).size() / 3
			if sz2 > 0 and sz2 <= target:
				pick = l
				break
	if pick < 0:
		return []
	var best: PackedInt32Array = im.get_surface_lod_indices(0, pick)
	if best.is_empty():
		return []
	return _compact_arrays(arrays, best)


## Rasterbasierte Vereinfachung als Rueckfall: Eckpunkte in derselben
## Rasterzelle werden zu ihrem Mittel zusammengezogen, entartete Dreiecke
## fallen weg. Grob, aber es greift immer und erhaelt die Silhouette.
func _cluster(arrays: Array, target: int) -> Array:
	var verts: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
	var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
	if verts.is_empty() or indices.is_empty():
		return []
	var normals: PackedVector3Array = arrays[Mesh.ARRAY_NORMAL] if arrays[Mesh.ARRAY_NORMAL] != null else PackedVector3Array()
	var uvs: PackedVector2Array = arrays[Mesh.ARRAY_TEX_UV] if arrays[Mesh.ARRAY_TEX_UV] != null else PackedVector2Array()
	var has_n := normals.size() >= verts.size()
	var has_u := uvs.size() >= verts.size()
	var box := AABB(verts[0], Vector3.ZERO)
	for i in range(1, verts.size()):
		box = box.expand(verts[i])
	var span: Vector3 = box.size
	if span.length() <= 0.00001:
		return []
	# Startauflaesung aus dem Ziel abgeschaetzt, dann vergroebern
	var res := maxf(4.0, pow(float(target) * 2.0, 1.0 / 3.0) * 2.0)
	for attempt in 10:
		var cell := Vector3(maxf(span.x / res, 0.000001), maxf(span.y / res, 0.000001), maxf(span.z / res, 0.000001))
		var cells := {}
		var map := PackedInt32Array()
		map.resize(verts.size())
		var nv := PackedVector3Array()
		var acc: Array = []
		for i in verts.size():
			var v: Vector3 = verts[i] - box.position
			var key := "%d_%d_%d" % [int(floor(v.x / cell.x)), int(floor(v.y / cell.y)), int(floor(v.z / cell.z))]
			if not cells.has(key):
				cells[key] = nv.size()
				nv.append(verts[i])
				acc.append({"p": verts[i], "n": (normals[i] if has_n else Vector3.UP), "u": (uvs[i] if has_u else Vector2.ZERO), "c": 1})
			else:
				var slot: int = int(cells[key])
				var a: Dictionary = acc[slot]
				a.p += verts[i]
				if has_n:
					a.n += normals[i]
				if has_u:
					a.u += uvs[i]
				a.c = int(a.c) + 1
			map[i] = int(cells[key])
		var out_v := PackedVector3Array()
		var out_n := PackedVector3Array()
		var out_u := PackedVector2Array()
		for a in acc:
			var c := float(a.c)
			out_v.append((a.p as Vector3) / c)
			if has_n:
				out_n.append(((a.n as Vector3) / c).normalized())
			if has_u:
				out_u.append((a.u as Vector2) / c)
		var out_i := PackedInt32Array()
		for t in range(0, indices.size() - 2, 3):
			var a0 := map[indices[t]]
			var a1 := map[indices[t + 1]]
			var a2 := map[indices[t + 2]]
			if a0 == a1 or a1 == a2 or a0 == a2:
				continue
			out_i.append(a0)
			out_i.append(a1)
			out_i.append(a2)
		var tris: int = out_i.size() / 3
		if tris <= 0:
			return []
		if tris <= target or attempt == 9:
			var out_arrays := []
			out_arrays.resize(Mesh.ARRAY_MAX)
			out_arrays[Mesh.ARRAY_VERTEX] = out_v
			if has_n:
				out_arrays[Mesh.ARRAY_NORMAL] = out_n
			if has_u:
				out_arrays[Mesh.ARRAY_TEX_UV] = out_u
			out_arrays[Mesh.ARRAY_INDEX] = out_i
			return out_arrays
		res *= 0.68
	return []


## Baut ein Mesh nur aus den tatsaechlich benutzten Eckpunkten.
func _compact_arrays(arrays: Array, indices: PackedInt32Array) -> Array:
	var verts: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
	var normals: PackedVector3Array = arrays[Mesh.ARRAY_NORMAL] if arrays[Mesh.ARRAY_NORMAL] != null else PackedVector3Array()
	var uvs: PackedVector2Array = arrays[Mesh.ARRAY_TEX_UV] if arrays[Mesh.ARRAY_TEX_UV] != null else PackedVector2Array()
	var map := {}
	var nv := PackedVector3Array()
	var nn := PackedVector3Array()
	var nu := PackedVector2Array()
	var ni := PackedInt32Array()
	var has_n := normals.size() >= verts.size()
	var has_u := uvs.size() >= verts.size()
	for i in indices:
		if not map.has(i):
			map[i] = nv.size()
			nv.append(verts[i])
			if has_n:
				nn.append(normals[i])
			if has_u:
				nu.append(uvs[i])
		ni.append(int(map[i]))
	var out_arrays := []
	out_arrays.resize(Mesh.ARRAY_MAX)
	out_arrays[Mesh.ARRAY_VERTEX] = nv
	if has_n:
		out_arrays[Mesh.ARRAY_NORMAL] = nn
	if has_u:
		out_arrays[Mesh.ARRAY_TEX_UV] = nu
	out_arrays[Mesh.ARRAY_INDEX] = ni
	return out_arrays


# ============================================================ Materialien

## Mit --textures wird das erste mitgelieferte Material mit Albedo-Textur
## uebernommen; sonst gibt es ein eigenes Material der Gruppe.
func _material_for(group: String, list: Array) -> Material:
	if albedo_path != "" and ResourceLoader.exists(albedo_path):
		var tm := StandardMaterial3D.new()
		tm.albedo_texture = load(albedo_path)
		tm.roughness = 0.45
		tm.metallic = 0.1
		return tm
	if keep_tex:
		for p in list:
			var m: Material = p.get("mat")
			if m is BaseMaterial3D and (m as BaseMaterial3D).albedo_texture != null:
				var c := (m as BaseMaterial3D).duplicate() as BaseMaterial3D
				c.cull_mode = BaseMaterial3D.CULL_BACK
				return c
	return _material(group)


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
		"dark_metal":
			m.albedo_color = Color(0.1, 0.1, 0.11)
			m.metallic = 0.85
			m.roughness = 0.42
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
		"lamp_white":
			m.albedo_color = Color(0.95, 0.95, 0.92)
			m.emission_enabled = true
			m.emission = Color(1.0, 0.97, 0.9)
			m.emission_energy_multiplier = 0.9
			m.roughness = 0.12
		"lamp_red":
			m.albedo_color = Color(0.5, 0.04, 0.03)
			m.emission_enabled = true
			m.emission = Color(1.0, 0.1, 0.06)
			m.emission_energy_multiplier = 1.1
			m.roughness = 0.14
		"lamp_amber":
			m.albedo_color = Color(0.6, 0.3, 0.03)
			m.emission_enabled = true
			m.emission = Color(1.0, 0.55, 0.08)
			m.emission_energy_multiplier = 0.9
			m.roughness = 0.14
		"detail":
			m.albedo_color = Color(0.3, 0.3, 0.32)
			m.metallic = 0.4
			m.roughness = 0.4
		_:
			m.albedo_color = Color(0.1, 0.1, 0.11)
			m.roughness = 0.65
	return m
