class_name PropBuilder
extends RefCounted
## Requisiten der Stadt als MultiMesh-Instanzen. Die Meshes (Laternen,
## Baeume, Hecken, Baenke, Ampeln, Schilder, Wartehaeuschen, Brunnen,
## Spielplaetze, Container, Paletten) werden einmal im Hauptthread erzeugt;
## collect() sammelt fuer einen Chunk nur Transformationen (thread-sicher).

const GROUND := 0.15
const CONTAINER_COLORS := [Color(0.72, 0.18, 0.12), Color(0.12, 0.35, 0.62), Color(0.18, 0.5, 0.3), Color(0.85, 0.6, 0.12), Color(0.5, 0.5, 0.52)]
const TREE_VARIANTS := 4

static var _meshes: Dictionary = {}


# ============================================================ Sammeln (Worker)

static func _add(out: Dictionary, key: String, xf: Transform3D, col := Color.WHITE, custom := Color(0, 0, 0, 0)) -> int:
	var inst: Dictionary = out["inst"]
	if not inst.has(key):
		inst[key] = []
	inst[key].append([xf, col, custom])
	return inst[key].size() - 1


static func _shape(out: Dictionary, pos: Vector3, size: Vector3, yaw := 0.0) -> void:
	out["shapes"].append({"xf": Transform3D(Basis(Vector3.UP, yaw), pos), "size": size})


static func _ground_y(city: CityModel, p: Dictionary) -> float:
	# Strassenmoebel stehen auf dem Gehweg; Bruecken liegen bei 0.15 (Gehweg)
	return GROUND


static func collect(city: CityModel, ids: Array, out: Dictionary) -> void:
	for pid in ids:
		var p: Dictionary = city.props[pid]
		var pos: Vector2 = p.pos
		var y := _ground_y(city, p)
		var rot: float = p.rot
		var base := Vector3(pos.x, y, pos.y)
		match p.kind:
			"lamp":
				var park: bool = p.layer == "park"
				_add(out, "lamp_park" if park else "lamp", Transform3D(Basis(Vector3.UP, rot), base))
				var head := base + (Vector3(0, 4.1, 0) if park else Basis(Vector3.UP, rot) * Vector3(0, 7.6, 1.4))
				out["lamps"].append(head)
				_shape(out, base + Vector3(0, 1.5, 0), Vector3(0.25, 3.0, 0.25))
			"street_tree", "tree":
				var v: int = int(p.get("variant", 0)) % TREE_VARIANTS
				var sc: float = float(p.get("scale", 1.0)) * (0.85 if p.kind == "street_tree" else 1.0)
				_add(out, "tree_%d" % v, Transform3D(Basis(Vector3.UP, rot).scaled(Vector3(sc, sc, sc)), base))
				_shape(out, base + Vector3(0, 1.5, 0), Vector3(0.45, 3.0, 0.45))
				if p.kind == "street_tree":
					_add(out, "tree_grate", Transform3D(Basis(), base + Vector3(0, 0.002, 0)))
			"hedge":
				var r: Rect2 = p.rect
				var h := 1.15 + fmod(absf(pos.x * 0.37 + pos.y * 0.11), 0.3)
				_add(out, "hedge", Transform3D(Basis.from_scale(Vector3(r.size.x, h, r.size.y)), Vector3(r.get_center().x, y, r.get_center().y)))
				_shape(out, Vector3(r.get_center().x, y + h * 0.5, r.get_center().y), Vector3(r.size.x, h, r.size.y))
			"bench":
				_add(out, "bench", Transform3D(Basis(Vector3.UP, rot), base))
				_shape(out, base + Vector3(0, 0.4, 0), Vector3(1.8, 0.8, 0.6), rot)
			"bin":
				_add(out, "bin", Transform3D(Basis(Vector3.UP, rot), base))
				_shape(out, base + Vector3(0, 0.5, 0), Vector3(0.5, 1.0, 0.5))
			"hydrant":
				_add(out, "hydrant", Transform3D(Basis(Vector3.UP, rot), base))
				_shape(out, base + Vector3(0, 0.4, 0), Vector3(0.3, 0.8, 0.3))
			"traffic_light":
				var wide: bool = float(p.get("arm_width", 0.0)) >= 12.0
				var idx := _add(out, "signal_arm" if wide else "signal", Transform3D(Basis(Vector3.UP, rot), base))
				out["signals"].append({"key": "signal_arm" if wide else "signal", "idx": idx, "node": p.ref, "axis": str(p.get("axis", "x"))})
				_shape(out, base + Vector3(0, 2.5, 0), Vector3(0.3, 5.0, 0.3))
			"sign_stop", "sign_yield", "sign_roundabout":
				_add(out, p.kind, Transform3D(Basis(Vector3.UP, rot), base))
				_shape(out, base + Vector3(0, 1.3, 0), Vector3(0.15, 2.6, 0.15))
			"street_sign":
				_add(out, "street_sign", Transform3D(Basis(), base))
				var names: Array = p.get("names", [])
				for k in mini(names.size(), 2):
					var yaw := 0.0 if k == 0 else PI * 0.5
					var ldir := Basis(Vector3.UP, yaw) * Vector3(1, 0, 0)
					var lp := base + Vector3(0, 2.75 - k * 0.3, 0) + ldir * 0.55
					for face in [1.0, -1.0]:
						out["labels"].append({"text": str(names[k]), "pos": lp + Basis(Vector3.UP, yaw) * Vector3(0, 0, 0.03 * face),
							"yaw": yaw if face > 0 else yaw + PI, "h": 0.13, "color": Color(1, 1, 1), "max_w": 1.0,
							"outline": Color(0, 0, 0, 0), "range": 45.0, "emissive": false})
				_shape(out, base + Vector3(0, 1.5, 0), Vector3(0.12, 3.0, 0.12))
			"bus_shelter":
				var st: Dictionary = city.bus_stops[int(p.get("stop", p.ref))]
				var dir: Vector2 = st.dir
				var toward_road := Vector2(dir.y, -dir.x)
				var yaw2 := atan2(toward_road.x, toward_road.y)
				var bas := Basis(Vector3.UP, yaw2)
				_add(out, "bus_shelter", Transform3D(bas, base))
				var nums := []
				for lid in st.lines:
					nums.append(str(city.bus_lines[lid].number))
				var txt := "%s\nLinie %s" % [str(st.name), ", ".join(nums)]
				out["labels"].append({"text": txt, "pos": base + bas * Vector3(1.95, 2.35, 0.08), "yaw": yaw2, "h": 0.13,
					"color": Color(0.1, 0.25, 0.15), "max_w": 1.3, "outline": Color(0, 0, 0, 0), "range": 50.0, "emissive": false})
				_shape(out, base + bas * Vector3(0, 1.2, -0.7), Vector3(3.6, 2.4, 0.15), yaw2)
			"fountain":
				_add(out, "fountain", Transform3D(Basis(), base))
				_shape(out, base + Vector3(0, 0.3, 0), Vector3(8.2, 0.6, 8.2))
			"playground":
				_add(out, "playground", Transform3D(Basis(Vector3.UP, rot), base))
				_shape(out, base + Vector3(-2.5, 1.0, 0), Vector3(3.0, 2.0, 1.0))
			"container":
				var cc: Color = CONTAINER_COLORS[int(p.get("color_idx", 0)) % CONTAINER_COLORS.size()]
				_add(out, "container", Transform3D(Basis(Vector3.UP, rot), base), cc)
				_shape(out, base + Vector3(0, 1.3, 0), Vector3(2.44, 2.6, 6.06), rot)
			"pallets":
				_add(out, "pallets", Transform3D(Basis(Vector3.UP, rot), base))
				_shape(out, base + Vector3(0, 0.5, 0), Vector3(1.2, 1.0, 1.0), rot)


## Baeume fuer die Fernansicht (nur Transformationen).
static func collect_far_trees(city: CityModel, ids: Array, out: Dictionary) -> void:
	for pid in ids:
		var p: Dictionary = city.props[pid]
		if p.kind != "tree" and p.kind != "street_tree":
			continue
		var pos: Vector2 = p.pos
		var sc: float = float(p.get("scale", 1.0)) * (0.85 if p.kind == "street_tree" else 1.0)
		_add(out, "tree_far", Transform3D(Basis(Vector3.UP, p.rot).scaled(Vector3(sc, sc, sc)), Vector3(pos.x, GROUND, pos.y)))


# ============================================================ Meshes (Hauptthread)

static func get_mesh(key: String) -> Mesh:
	if _meshes.has(key):
		return _meshes[key]
	var mb := MeshBatch.new()
	_build_mesh(key, mb)
	var m := MeshBatch.build_mesh(mb.to_data(), CityMaterials.lookup())
	_meshes[key] = m
	return m


## Sichtweite je Requisitenart (Meter) fuer visibility_range_end.
static func range_of(key: String) -> float:
	if key.begins_with("tree"):
		return 900.0
	match key:
		"lamp", "lamp_park", "bus_shelter", "signal", "signal_arm", "fountain", "container":
			return 420.0
		"hedge", "playground":
			return 300.0
	return 160.0


static func casts_shadow(key: String) -> bool:
	return not key in ["tree_grate", "street_sign", "hydrant", "bin"]


static func _build_mesh(key: String, mb: MeshBatch) -> void:
	if key.begins_with("tree_") and key != "tree_grate" and key != "tree_far":
		_tree(mb, int(key.substr(5)), false)
		return
	match key:
		"tree_far":
			_tree(mb, 0, true)
		"tree_grate":
			mb.box("paint_metal", Vector3(0, 0.005, 0), Vector3(1.4, 0.01, 1.4), Color(0.18, 0.18, 0.19), MeshBatch.NO_CUSTOM, 4)
			mb.box("g_dirt", Vector3(0, 0.008, 0), Vector3(0.6, 0.01, 0.6), Color.WHITE, MeshBatch.NO_CUSTOM, 4)
		"lamp":
			var c := Color(0.22, 0.24, 0.26)
			mb.cylinder("paint_metal", Vector3(0, 0, 0), 0.16, 0.16, 0.6, 10, c)
			mb.cylinder("paint_metal", Vector3(0, 0.6, 0), 0.11, 0.065, 7.1, 10, c)
			# Ausleger Richtung Fahrbahn (+z)
			var arm := Transform3D(Basis(Vector3.RIGHT, 1.25), Vector3(0, 7.45, 0.7))
			mb.box_xf("paint_metal", arm, Vector3(0.08, 0.08, 1.5), c)
			mb.box_xf("paint_metal", Transform3D(Basis(), Vector3(0, 7.65, 1.45)), Vector3(0.36, 0.12, 0.8), c)
			mb.box_xf("lamp_glow", Transform3D(Basis(), Vector3(0, 7.54, 1.45)), Vector3(0.3, 0.1, 0.7), Color(1.0, 0.86, 0.62))
		"lamp_park":
			var c2 := Color(0.12, 0.13, 0.14)
			mb.cylinder("paint_metal", Vector3(0, 0, 0), 0.09, 0.06, 3.7, 8, c2)
			mb.box("paint_metal", Vector3(0, 3.72, 0), Vector3(0.36, 0.06, 0.36), c2)
			mb.box("lamp_glow", Vector3(0, 4.0, 0), Vector3(0.28, 0.5, 0.28), Color(1.0, 0.85, 0.6))
			mb.cylinder("paint_metal", Vector3(0, 4.25, 0), 0.26, 0.04, 0.25, 8, c2)
		"hedge":
			# Einheitsquader 1 x 1 x 1 (Unterkante 0), oben leicht gerundet
			mb.box("hedge", Vector3(0, 0.45, 0), Vector3(1, 0.9, 1), Color(0.8, 0.95, 0.75, 0.0), MeshBatch.NO_CUSTOM, 1 | 2 | 16 | 32)
			mb.box("hedge", Vector3(0, 0.95, 0), Vector3(0.92, 0.1, 0.92), Color(0.9, 1.0, 0.85, 0.0), MeshBatch.NO_CUSTOM, 1 | 2 | 4 | 16 | 32)
		"bench":
			var w := Color(0.55, 0.38, 0.24)
			var m := Color(0.15, 0.16, 0.17)
			for k in 3:
				mb.box("u_wood", Vector3(0, 0.45, -0.18 + k * 0.15), Vector3(1.8, 0.04, 0.12), w)
			for k in 2:
				mb.box("u_wood", Vector3(0, 0.62 + k * 0.16, -0.3), Vector3(1.8, 0.11, 0.035), w)
			for sx in [-0.75, 0.75]:
				mb.box("paint_metal", Vector3(sx, 0.22, -0.05), Vector3(0.06, 0.44, 0.5), m)
				mb.box("paint_metal", Vector3(sx, 0.62, -0.32), Vector3(0.06, 0.5, 0.05), m)
		"bin":
			mb.cylinder("paint_metal", Vector3(0, 0, 0), 0.05, 0.05, 0.5, 6, Color(0.2, 0.2, 0.2))
			mb.cylinder("paint_metal", Vector3(0, 0.45, 0), 0.22, 0.24, 0.6, 12, Color(0.2, 0.42, 0.28))
		"hydrant":
			var r := Color(0.78, 0.12, 0.1)
			mb.cylinder("paint", Vector3(0, 0, 0), 0.13, 0.11, 0.62, 10, r)
			mb.cylinder("paint", Vector3(0, 0.62, 0), 0.12, 0.04, 0.12, 10, r)
			mb.box("paint", Vector3(0, 0.45, 0), Vector3(0.36, 0.08, 0.08), r)
		"signal", "signal_arm":
			_signal(mb, key == "signal_arm")
		"sign_stop", "sign_yield", "sign_roundabout":
			mb.cylinder("paint_metal", Vector3(0, 0, 0), 0.04, 0.04, 2.4, 8, Color(0.65, 0.67, 0.7))
			var cell := 0 if key == "sign_stop" else (1 if key == "sign_yield" else 2)
			BuildingBuilder.sign_quad(mb, Vector3(0, 2.2, 0.06), 0.0, Vector2(0.75, 0.75), cell, true)
		"street_sign":
			mb.cylinder("paint_metal", Vector3(0, 0, 0), 0.04, 0.04, 3.0, 8, Color(0.65, 0.67, 0.7))
			mb.box("paint", Vector3(0.55, 2.75, 0), Vector3(1.1, 0.22, 0.05), Color(0.1, 0.25, 0.55))
			mb.box("paint", Vector3(0, 2.45, 0.55), Vector3(0.05, 0.22, 1.1), Color(0.1, 0.25, 0.55))
		"bus_shelter":
			_shelter(mb)
		"fountain":
			var poly := PackedVector2Array()
			var poly_in := PackedVector2Array()
			for i in 24:
				var a := TAU * i / 24.0
				poly.append(Vector2(cos(a), sin(a)) * 4.1)
				poly_in.append(Vector2(cos(a), sin(a)) * 3.75)
			mb.prism("u_stone", poly, 0.0, 0.5, Color(0.85, 0.83, 0.78), MeshBatch.NO_CUSTOM, false, true)
			for i in 24:
				var a0 := poly[i]
				var a1 := poly[(i + 1) % 24]
				var b0 := poly_in[i]
				var b1 := poly_in[(i + 1) % 24]
				RoadBuilder.quad2(mb, "u_stone", a0, a1, b1, b0, 0.5, Color(0.88, 0.86, 0.8))
			mb.prism("pool_water", poly_in, 0.0, 0.38, Color.WHITE, MeshBatch.NO_CUSTOM, true, false)
			mb.cylinder("u_stone", Vector3(0, 0.3, 0), 0.5, 0.35, 1.4, 12, Color(0.85, 0.83, 0.78))
			mb.cylinder("u_stone", Vector3(0, 1.7, 0), 1.2, 1.2, 0.2, 16, Color(0.85, 0.83, 0.78))
			mb.cylinder("pool_water", Vector3(0, 1.85, 0), 1.1, 0.2, 0.9, 12, Color.WHITE)
		"playground":
			_playground(mb)
		"container":
			var cc := Color(1, 1, 1)
			mb.box("u_metal", Vector3(0, 1.3, 0), Vector3(2.44, 2.6, 6.06), cc)
			mb.box("paint_metal", Vector3(0, 1.3, 3.04), Vector3(2.3, 2.45, 0.04), Color(0.5, 0.5, 0.5))
		"pallets":
			for k in 4:
				mb.box("u_wood", Vector3(0, 0.07 + k * 0.15, 0), Vector3(1.2, 0.12, 0.8), Color(0.72, 0.6, 0.42))
			mb.box("paint_rough", Vector3(0, 0.85, 0), Vector3(1.1, 0.4, 0.7), Color(0.75, 0.68, 0.55))


static func _tree(mb: MeshBatch, variant: int, far: bool) -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = 1000 + variant * 77
	var trunk_h = [3.0, 2.6, 3.6, 2.2][variant]
	var crown_r = [2.6, 2.2, 2.0, 2.8][variant]
	var crown_h = [3.4, 3.0, 4.6, 2.6][variant]
	var shades := [Color(0.95, 1.0, 0.9), Color(0.85, 1.0, 0.8), Color(1.0, 1.0, 0.85), Color(0.9, 0.95, 0.95)]
	if far:
		mb.cylinder("u_bark", Vector3.ZERO, 0.2, 0.12, trunk_h, 5, Color(0.6, 0.5, 0.42))
		_blob(mb, Vector3(0, trunk_h + crown_h * 0.45, 0), Vector3(crown_r, crown_h * 0.6, crown_r), 6, 4, rng, Color(0.85, 0.95, 0.8), 0.0)
		return
	mb.cylinder("u_bark", Vector3.ZERO, 0.24, 0.15, trunk_h + 0.6, 8, Color(0.7, 0.6, 0.5))
	# Aeste
	for k in 3:
		var a := TAU * k / 3.0 + variant
		var dir := Vector3(cos(a), 1.4, sin(a)).normalized()
		var p0 := Vector3(0, trunk_h * 0.75, 0)
		var p1 := p0 + dir * 1.4
		var mid := (p0 + p1) * 0.5
		var up := dir
		var side := up.cross(Vector3.FORWARD).normalized()
		var fwd := side.cross(up).normalized()
		mb.box_xf("u_bark", Transform3D(Basis(side, up, fwd), mid), Vector3(0.12, 1.4, 0.12), Color(0.7, 0.6, 0.5))
	var n_blobs := 6 + variant
	for k in n_blobs:
		var a2 := rng.randf() * TAU
		var rr = rng.randf_range(0.2, 1.0) * crown_r * 0.55
		var hh = rng.randf_range(0.25, 0.85) * crown_h
		var c := Vector3(cos(a2) * rr, trunk_h + hh, sin(a2) * rr)
		var s = rng.randf_range(0.55, 0.85) * crown_r
		var shade: Color = shades[variant].darkened(0.25 * (1.0 - hh / crown_h))
		_blob(mb, c, Vector3(s, s * 0.85, s), 10, 7, rng, shade, clampf(hh / crown_h, 0.2, 1.0))


## Unregelmaessige Kugel (Laubballen). sway in Alpha.
static func _blob(mb: MeshBatch, c: Vector3, r: Vector3, seg: int, rings: int, rng: RandomNumberGenerator, col: Color, sway: float) -> void:
	var s := mb.surf("foliage")
	var rows := []
	var jit := []
	for j in rings + 1:
		var row := []
		for i in seg:
			jit.append(rng.randf_range(0.82, 1.12))
	for j in rings + 1:
		var phi := PI * float(j) / rings - PI * 0.5
		var row := []
		for i in seg + 1:
			var th := TAU * float(i % seg) / seg
			var d := Vector3(cos(th) * cos(phi), sin(phi), sin(th) * cos(phi))
			var jj: float = jit[(j * seg + (i % seg)) % jit.size()] if j > 0 and j < rings else 1.0
			var p := c + Vector3(d.x * r.x, d.y * r.y, d.z * r.z) * jj
			var ao := lerpf(0.55, 1.0, (d.y + 1.0) * 0.5)
			var vc := Color(col.r * ao, col.g * ao, col.b * ao, sway)
			row.append(s.vert(p, d, Vector3(-sin(th), 0, cos(th)), Vector2(th * r.x, -phi * r.y), vc, MeshBatch.NO_CUSTOM))
		rows.append(row)
	for j in rings:
		for i in seg:
			var a: int = rows[j][i]
			var b: int = rows[j][i + 1]
			var cc: int = rows[j + 1][i + 1]
			var d2: int = rows[j + 1][i]
			s.tri_idx(a, d2, cc)
			s.tri_idx(a, cc, b)


static func _signal(mb: MeshBatch, arm: bool) -> void:
	var pole := Color(0.32, 0.34, 0.36)
	mb.cylinder("paint_metal", Vector3.ZERO, 0.11, 0.09, 5.4 if arm else 3.4, 10, pole)
	# Lokales +z zeigt zum Gegenverkehr; Fahrbahn liegt bei -x
	var heads := [Vector3(0, 2.5, 0.12)]
	if arm:
		mb.box("paint_metal", Vector3(-2.8, 5.25, 0), Vector3(5.6, 0.14, 0.14), pole)
		heads.append(Vector3(-4.2, 4.55, 0.12))
	for h in heads:
		var s := mb.surf("signal")
		mb.box("signal", h + Vector3(0, 0.0, -0.05), Vector3(0.36, 1.05, 0.26), Color(0.12, 0.13, 0.14))
		for k in 3:
			var lens := float(k + 1)
			var y: float = h.y + 0.33 - k * 0.33
			_lens(mb, Vector3(h.x, y, h.z + 0.085), 0.12, lens)
			# Blende
			mb.box("signal", Vector3(h.x, y + 0.13, h.z + 0.16), Vector3(0.3, 0.02, 0.14), Color(0.1, 0.1, 0.1))
	# Fussgaengersignal seitlich (zeigt quer zur Fahrbahn)
	var ph := Vector3(0.0, 2.2, -0.2)
	mb.box("signal", ph + Vector3(0.0, 0.0, 0.0), Vector3(0.26, 0.6, 0.2), Color(0.12, 0.13, 0.14))
	_lens_x(mb, ph + Vector3(-0.11, 0.14, 0), 0.08, 4.0)
	_lens_x(mb, ph + Vector3(-0.11, -0.14, 0), 0.08, 5.0)


static func _lens(mb: MeshBatch, c: Vector3, r: float, lens: float) -> void:
	var s := mb.surf("signal")
	var cu := Color(lens, 0, 0, 0)
	var seg := 10
	var ci := s.vert(c, Vector3.BACK, Vector3.RIGHT, Vector2.ZERO, Color.WHITE, cu)
	var prev := -1
	for i in seg + 1:
		var a := TAU * i / seg
		var vi := s.vert(c + Vector3(cos(a), sin(a), 0) * r, Vector3.BACK, Vector3.RIGHT, Vector2.ZERO, Color.WHITE, cu)
		if prev >= 0:
			s.tri_idx(ci, prev, vi)
		prev = vi


static func _lens_x(mb: MeshBatch, c: Vector3, r: float, lens: float) -> void:
	var s := mb.surf("signal")
	var cu := Color(lens, 0, 0, 0)
	var seg := 8
	var ci := s.vert(c, Vector3.LEFT, Vector3.BACK, Vector2.ZERO, Color.WHITE, cu)
	var prev := -1
	for i in seg + 1:
		var a := TAU * i / seg
		var vi := s.vert(c + Vector3(0, sin(a), cos(a)) * r, Vector3.LEFT, Vector3.BACK, Vector2.ZERO, Color.WHITE, cu)
		if prev >= 0:
			s.tri_idx(ci, vi, prev)
		prev = vi


static func _shelter(mb: MeshBatch) -> void:
	# Lokales +z zeigt zur Fahrbahn; Rueckwand bei z = -0.75
	var frame := Color(0.25, 0.27, 0.3)
	for x in [-1.75, 1.75]:
		for z in [-0.75, 0.6]:
			mb.box("paint_metal", Vector3(x, 1.2, z), Vector3(0.08, 2.4, 0.08), frame)
	mb.box("glass_clear", Vector3(0, 1.25, -0.75), Vector3(3.5, 2.1, 0.03), Color.WHITE)
	mb.box("glass_clear", Vector3(-1.75, 1.25, -0.08), Vector3(0.03, 2.1, 1.3), Color.WHITE)
	mb.box("paint_metal", Vector3(0, 2.45, -0.1), Vector3(3.8, 0.1, 1.7), frame)
	mb.box("lamp_glow", Vector3(0, 2.39, -0.1), Vector3(3.0, 0.02, 0.3), Color(0.95, 0.97, 1.0))
	mb.box("u_wood", Vector3(0, 0.46, -0.52), Vector3(2.4, 0.05, 0.38), Color(0.55, 0.4, 0.28))
	mb.box("paint_metal", Vector3(-1.0, 0.22, -0.52), Vector3(0.06, 0.44, 0.3), frame)
	mb.box("paint_metal", Vector3(1.0, 0.22, -0.52), Vector3(0.06, 0.44, 0.3), frame)
	# Haltestellenmast mit H-Schild und Fahrplan
	mb.cylinder("paint_metal", Vector3(1.95, 0, 0.45), 0.04, 0.04, 2.9, 8, Color(0.6, 0.62, 0.64))
	BuildingBuilder.sign_quad(mb, Vector3(1.95, 2.75, 0.45), 0.0, Vector2(0.55, 0.55), 3, true)
	mb.box("paint", Vector3(1.95, 2.35, 0.03), Vector3(1.4, 0.4, 0.03), Color(0.96, 0.85, 0.2))
	# Werbetafel (leuchtet)
	mb.box("window_glow", Vector3(1.0, 1.3, -0.73), Vector3(1.2, 1.7, 0.02), Color(0.75, 0.82, 0.9))


static func _playground(mb: MeshBatch) -> void:
	# Sandkasten
	mb.box("u_wood", Vector3(2.5, 0.12, 2.0), Vector3(3.0, 0.24, 3.0), Color(0.6, 0.45, 0.3), MeshBatch.NO_CUSTOM, 1 | 2 | 16 | 32)
	mb.box("g_gravel", Vector3(2.5, 0.1, 2.0), Vector3(2.8, 0.02, 2.8), Color(1.0, 0.92, 0.75), MeshBatch.NO_CUSTOM, 4)
	# Rutsche
	mb.box("paint", Vector3(-2.5, 1.0, 0.0), Vector3(1.2, 0.08, 1.2), Color(0.85, 0.25, 0.2))
	for x in [-3.0, -2.0]:
		for z in [-0.5, 0.5]:
			mb.box("paint_metal", Vector3(x, 0.5, z), Vector3(0.08, 1.0, 0.08), Color(0.2, 0.45, 0.8))
	mb.box_xf("paint", Transform3D(Basis(Vector3.BACK, -0.55), Vector3(-0.9, 0.55, 0)), Vector3(2.2, 0.06, 0.6), Color(0.95, 0.75, 0.15))
	mb.box_xf("paint_metal", Transform3D(Basis(Vector3.BACK, 1.1), Vector3(-3.4, 0.55, 0)), Vector3(1.3, 0.06, 0.45), Color(0.2, 0.45, 0.8))
	# Schaukel
	for z in [-2.5, 2.5]:
		mb.box_xf("paint_metal", Transform3D(Basis(Vector3.RIGHT, 0.25), Vector3(0.0, 1.2, z - 0.3)), Vector3(0.08, 2.5, 0.08), Color(0.25, 0.55, 0.3))
		mb.box_xf("paint_metal", Transform3D(Basis(Vector3.RIGHT, -0.25), Vector3(0.0, 1.2, z + 0.3)), Vector3(0.08, 2.5, 0.08), Color(0.25, 0.55, 0.3))
	mb.box("paint_metal", Vector3(0.0, 2.4, 0), Vector3(0.08, 0.08, 5.2), Color(0.25, 0.55, 0.3))
	for z in [-1.0, 1.0]:
		mb.box("paint_metal", Vector3(0.0, 1.5, z), Vector3(0.02, 1.8, 0.02), Color(0.3, 0.3, 0.3))
		mb.box("paint", Vector3(0.0, 0.55, z), Vector3(0.25, 0.04, 0.5), Color(0.15, 0.15, 0.15))
