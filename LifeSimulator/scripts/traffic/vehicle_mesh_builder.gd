class_name VehicleMeshBuilder
extends RefCounted
## Prozedurale Fahrzeugmodelle (SurfaceTool), stilisiert-realistisch.
##
## Koordinaten des Modells: vorne = -Z, oben = +Y, rechts = +X, Ursprung am
## Boden in der Fahrzeugmitte. Ein Modell besteht aus einem Karosserie-Mesh
## mit benannten Oberflaechen (Lack, Glas, Kunststoff, Chrom, Lichter, ...),
## einem gemeinsamen Rad-Mesh und Radpositionen. Lackfarbe und Lichter
## werden je Fahrzeug ueber Material-Overrides der Oberflaechen gesetzt.
##
## get_model(type_id) -> {mesh, surf: {name: index}, wheel_mesh, wheels: [{pos, r, steer}],
##   length, width, height, lights: {head: [Vector3], tail: [Vector3]}, doors: [...]}

const SURF_ORDER := ["paint", "glass", "trim", "chrome", "tire", "plate", "head", "tail",
	"blink_l", "blink_r", "beacon_a", "beacon_b", "stripe", "rider", "skin", "door", "display", "cargo"]

static var _models: Dictionary = {}
static var _mats: Dictionary = {}
static var _paints: Dictionary = {}
static var _wheel_meshes: Dictionary = {}


# ============================================================ Materialien

static func _std(albedo: Color, metallic := 0.0, roughness := 0.6, emission := Color.BLACK, energy := 0.0) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = albedo
	m.metallic = metallic
	m.roughness = roughness
	if energy > 0.0:
		m.emission_enabled = true
		m.emission = emission
		m.emission_energy_multiplier = energy
	return m


## Gemeinsame Materialien: glass, trim, chrome, tire, rim, plate, head_off/on,
## tail_off/on, brake, blink_off/on, beacon_off, beacon_blue, beacon_red,
## display, skin, cargo, door, beam.
static func material(name: String) -> Material:
	if _mats.has(name):
		return _mats[name]
	var m: Material
	match name:
		"glass":
			m = _std(Color(0.10, 0.13, 0.17), 0.55, 0.06)
		"trim":
			m = _std(Color(0.06, 0.06, 0.07), 0.0, 0.75)
		"chrome":
			m = _std(Color(0.80, 0.81, 0.84), 0.95, 0.18)
		"tire":
			m = _std(Color(0.045, 0.045, 0.05), 0.0, 0.92)
		"rim":
			m = _std(Color(0.66, 0.68, 0.72), 0.8, 0.3)
		"plate":
			m = _std(Color(0.93, 0.93, 0.90), 0.0, 0.5)
		"head_off":
			m = _std(Color(0.80, 0.84, 0.88), 0.6, 0.08)
		"head_on":
			m = _std(Color(1.0, 0.97, 0.88), 0.0, 0.2, Color(1.0, 0.95, 0.82), 4.0)
		"tail_off":
			m = _std(Color(0.42, 0.03, 0.04), 0.2, 0.25)
		"tail_on":
			m = _std(Color(0.6, 0.04, 0.04), 0.0, 0.3, Color(1.0, 0.06, 0.04), 1.6)
		"brake":
			m = _std(Color(0.9, 0.05, 0.05), 0.0, 0.3, Color(1.0, 0.05, 0.03), 5.0)
		"blink_off":
			m = _std(Color(0.55, 0.32, 0.06), 0.2, 0.25)
		"blink_on":
			m = _std(Color(1.0, 0.6, 0.1), 0.0, 0.3, Color(1.0, 0.55, 0.05), 6.0)
		"beacon_off":
			m = _std(Color(0.08, 0.12, 0.35), 0.2, 0.2)
		"beacon_blue":
			m = _std(Color(0.2, 0.35, 1.0), 0.0, 0.2, Color(0.15, 0.3, 1.0), 9.0)
		"beacon_red":
			m = _std(Color(1.0, 0.1, 0.1), 0.0, 0.2, Color(1.0, 0.08, 0.05), 9.0)
		"beacon_yellow":
			m = _std(Color(1.0, 0.8, 0.1), 0.0, 0.2, Color(1.0, 0.75, 0.05), 7.0)
		"display":
			m = _std(Color(0.03, 0.03, 0.03), 0.0, 0.4)
		"skin":
			m = _std(Color(0.80, 0.62, 0.50), 0.0, 0.7)
		"cargo":
			m = _std(Color(0.88, 0.88, 0.86), 0.1, 0.55)
		"door":
			m = _std(Color(0.12, 0.15, 0.19), 0.5, 0.1)
		"beam":
			var bm := StandardMaterial3D.new()
			bm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
			bm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
			bm.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
			bm.albedo_color = Color(1.0, 0.92, 0.7, 0.55)
			var gt := GradientTexture2D.new()
			var gr := Gradient.new()
			gr.set_color(0, Color(1, 1, 1, 1))
			gr.set_color(1, Color(1, 1, 1, 0))
			gt.gradient = gr
			gt.fill = GradientTexture2D.FILL_RADIAL
			gt.fill_from = Vector2(0.5, 1.0)
			gt.fill_to = Vector2(0.5, 0.0)
			gt.width = 64
			gt.height = 64
			bm.albedo_texture = gt
			bm.cull_mode = BaseMaterial3D.CULL_DISABLED
			bm.no_depth_test = false
			m = bm
		_:
			m = _std(Color(1, 0, 1))
	_mats[name] = m
	return m


## Lackmaterial (zwischengespeichert je Farbe).
static func paint_material(c: Color, metallic := 0.45) -> StandardMaterial3D:
	var key := c.to_html(false) + ("m" if metallic > 0.3 else "s")
	if _paints.has(key):
		return _paints[key]
	var m := _std(c, metallic, 0.32)
	m.clearcoat_enabled = true
	m.clearcoat = 0.6
	m.clearcoat_roughness = 0.15
	_paints[key] = m
	return m


# ============================================================ Grundformen

class MB:
	var tools: Dictionary = {}

	func _st(name: String) -> SurfaceTool:
		if not tools.has(name):
			var st := SurfaceTool.new()
			st.begin(Mesh.PRIMITIVE_TRIANGLES)
			tools[name] = st
		return tools[name]

	## Dreieck mit Normalen weg vom Bezugspunkt `center` (robuste Orientierung).
	func tri(name: String, a: Vector3, b: Vector3, c: Vector3, center: Vector3) -> void:
		var n := (c - a).cross(b - a)
		if n.length_squared() < 1e-12:
			return
		n = n.normalized()
		if n.dot((a + b + c) / 3.0 - center) < 0.0:
			var tmp := b
			b = c
			c = tmp
			n = -n
		var st := _st(name)
		st.set_normal(n)
		st.add_vertex(a)
		st.set_normal(n)
		st.add_vertex(b)
		st.set_normal(n)
		st.add_vertex(c)

	func quad(name: String, a: Vector3, b: Vector3, c: Vector3, d: Vector3, center: Vector3) -> void:
		tri(name, a, b, c, center)
		tri(name, a, c, d, center)

	## Quader (optional gedreht um X um `pitch`).
	func box(name: String, center: Vector3, size: Vector3, basis := Basis()) -> void:
		var h := size * 0.5
		var p := []
		for sx in [-1, 1]:
			for sy in [-1, 1]:
				for sz in [-1, 1]:
					p.append(center + basis * Vector3(h.x * sx, h.y * sy, h.z * sz))
		# Index: sx*4 + sy*2 + sz  (0 = -, 1 = +)
		quad(name, p[0], p[1], p[3], p[2], center)  # -x
		quad(name, p[4], p[5], p[7], p[6], center)  # +x
		quad(name, p[0], p[1], p[5], p[4], center)  # -y
		quad(name, p[2], p[3], p[7], p[6], center)  # +y
		quad(name, p[0], p[2], p[6], p[4], center)  # -z
		quad(name, p[1], p[3], p[7], p[5], center)  # +z

	## Zylinder entlang einer Achse.
	func cyl(name: String, center: Vector3, axis: Vector3, r: float, h: float, seg := 12, caps := true) -> void:
		axis = axis.normalized()
		var u := axis.cross(Vector3.UP)
		if u.length_squared() < 0.01:
			u = axis.cross(Vector3.RIGHT)
		u = u.normalized()
		var w := axis.cross(u).normalized()
		var a0 := center - axis * h * 0.5
		var a1 := center + axis * h * 0.5
		for i in seg:
			var t0 := TAU * i / seg
			var t1 := TAU * (i + 1) / seg
			var d0 := (u * cos(t0) + w * sin(t0)) * r
			var d1 := (u * cos(t1) + w * sin(t1)) * r
			quad(name, a0 + d0, a0 + d1, a1 + d1, a1 + d0, center)
			if caps:
				tri(name, a0, a0 + d0, a0 + d1, center + axis * h)
				tri(name, a1, a1 + d0, a1 + d1, center - axis * h)

	## Kugel (fuer Koepfe, Helme).
	func sphere(name: String, center: Vector3, r: Vector3, rings := 6, seg := 10) -> void:
		for i in rings:
			var p0 := PI * i / rings - PI * 0.5
			var p1 := PI * (i + 1) / rings - PI * 0.5
			for j in seg:
				var t0 := TAU * j / seg
				var t1 := TAU * (j + 1) / seg
				var a := center + Vector3(cos(p0) * cos(t0) * r.x, sin(p0) * r.y, cos(p0) * sin(t0) * r.z)
				var b := center + Vector3(cos(p0) * cos(t1) * r.x, sin(p0) * r.y, cos(p0) * sin(t1) * r.z)
				var c := center + Vector3(cos(p1) * cos(t1) * r.x, sin(p1) * r.y, cos(p1) * sin(t1) * r.z)
				var d := center + Vector3(cos(p1) * cos(t0) * r.x, sin(p1) * r.y, cos(p1) * sin(t0) * r.z)
				quad(name, a, b, c, d, center)

	func commit() -> Array:
		var mesh := ArrayMesh.new()
		var idx := {}
		for name in VehicleMeshBuilder.SURF_ORDER:
			if tools.has(name):
				var st: SurfaceTool = tools[name]
				st.commit(mesh)
				idx[name] = mesh.get_surface_count() - 1
		return [mesh, idx]


## Karosserie als Loft ueber Querschnitte. Querschnitt:
## [u (vorne +), y_unten, y_guertel, y_dach, halbe Breite, halbe Dachbreite]
## Abschnitte ohne Dach (y_dach <= y_guertel) sind Motorhaube/Kofferraum;
## Uebergaenge zwischen Abschnitten mit und ohne Dach werden zu Scheiben.
static func loft(mb: MB, secs: Array, glass_sides := true, side_surface := "paint") -> void:
	var rings := []
	for s in secs:
		var u: float = s[0]
		var yb: float = s[1]
		var ybelt: float = s[2]
		var yr: float = maxf(s[3], ybelt)
		var hw: float = s[4]
		var hr: float = s[5]
		var z := -u
		var ymid := lerpf(yb, ybelt, 0.45)
		var r := [
			Vector3(hw * 0.86, yb, z),
			Vector3(hw * 0.985, yb + 0.10, z),
			Vector3(hw, ymid, z),
			Vector3(hw * 0.975, ybelt, z),
			Vector3(hr, yr, z),
		]
		rings.append({"r": r, "roof": yr > ybelt + 0.02, "c": Vector3(0, (yb + yr) * 0.5, z)})
	for i in range(rings.size() - 1):
		var A: Dictionary = rings[i]
		var B: Dictionary = rings[i + 1]
		var ra: Array = A.r
		var rb: Array = B.r
		var c: Vector3 = (A.c + B.c) * 0.5
		for side: float in [-1.0, 1.0]:
			var m := Vector3(side, 1, 1)
			# unten -> Guertel
			mb.quad("trim", ra[0] * m, rb[0] * m, rb[1] * m, ra[1] * m, c)
			mb.quad(side_surface, ra[1] * m, rb[1] * m, rb[2] * m, ra[2] * m, c)
			mb.quad(side_surface, ra[2] * m, rb[2] * m, rb[3] * m, ra[3] * m, c)
			# Fensterbereich
			if A.roof and B.roof:
				mb.quad("glass" if glass_sides else side_surface, ra[3] * m, rb[3] * m, rb[4] * m, ra[4] * m, c)
			elif A.roof or B.roof:
				mb.quad("paint", ra[3] * m, rb[3] * m, rb[4] * m, ra[4] * m, c)
		# Unterboden
		mb.quad("trim", ra[0], rb[0], rb[0] * Vector3(-1, 1, 1), ra[0] * Vector3(-1, 1, 1), c + Vector3(0, 2, 0))
		# Oberseite
		var top := "paint"
		if A.roof != B.roof:
			top = "glass"
		mb.quad(top, ra[4], rb[4], rb[4] * Vector3(-1, 1, 1), ra[4] * Vector3(-1, 1, 1), c)
	# Stirnflaechen
	for k in [0, rings.size() - 1]:
		var R: Dictionary = rings[k]
		var rr: Array = R.r
		var cc: Vector3 = R.c + Vector3(0, 0, 1.0 if k == 0 else -1.0)
		var poly := []
		for j in rr.size():
			poly.append(rr[j])
		for j in range(rr.size() - 1, -1, -1):
			poly.append(rr[j] * Vector3(-1, 1, 1))
		var mid: Vector3 = R.c
		for j in poly.size():
			mb.tri("paint", mid, poly[j], poly[(j + 1) % poly.size()], cc)


# ============================================================ Rad

static func wheel_mesh(r: float, w: float, style := 0) -> ArrayMesh:
	var key := "%.2f:%.2f:%d" % [r, w, style]
	if _wheel_meshes.has(key):
		return _wheel_meshes[key]
	var mb := MB.new()
	var seg := 16 if r > 0.25 else 12
	if style == 1:
		# Fahrrad: duenner Reifen mit Speichen
		mb.cyl("tire", Vector3.ZERO, Vector3.RIGHT, r, w, 18, false)
		mb.cyl("tire", Vector3.ZERO, Vector3.RIGHT, r - 0.035, w * 1.05, 18, false)
		for i in 6:
			var a := PI * i / 6.0
			mb.box("chrome", Vector3.ZERO, Vector3(0.012, r * 1.85, 0.012), Basis(Vector3.RIGHT, a))
		mb.cyl("chrome", Vector3.ZERO, Vector3.RIGHT, 0.04, w * 1.4, 8)
	else:
		mb.cyl("tire", Vector3.ZERO, Vector3.RIGHT, r, w, seg, true)
		for sx: float in [-1.0, 1.0]:
			var face := Vector3(sx * (w * 0.5 + 0.004), 0, 0)
			mb.cyl("chrome", face, Vector3.RIGHT, r * 0.66, 0.012, seg, true)
			mb.cyl("tire", face + Vector3(sx * 0.006, 0, 0), Vector3.RIGHT, r * 0.2, 0.01, 8, true)
			for i in 5:
				var a := TAU * i / 5.0
				mb.box("trim", face + Vector3(sx * 0.004, 0, 0) + Vector3(0, cos(a), sin(a)) * r * 0.43,
					Vector3(0.008, r * 0.1, r * 0.2), Basis(Vector3.RIGHT, -a))
	var res: Array = mb.commit()
	var mesh: ArrayMesh = res[0]
	var idx: Dictionary = res[1]
	if idx.has("tire"):
		mesh.surface_set_material(idx.tire, material("tire"))
	if idx.has("chrome"):
		mesh.surface_set_material(idx.chrome, material("rim"))
	if idx.has("trim"):
		mesh.surface_set_material(idx.trim, material("trim"))
	_wheel_meshes[key] = mesh
	return mesh


# ============================================================ Modelle

static func get_model(type_id: String) -> Dictionary:
	if _models.has(type_id):
		return _models[type_id]
	var t := VehicleCatalog.type_info(type_id)
	var mb := MB.new()
	var d := {"length": t.length, "width": t.width, "height": t.height, "wheels": [],
		"lights": {"head": [], "tail": []}, "doors": [], "labels": [], "beacons": false}
	match type_id:
		"sedan", "taxi", "police":
			_car(mb, d, "sedan")
		"hatchback":
			_car(mb, d, "hatchback")
		"suv":
			_car(mb, d, "suv")
		"sports":
			_car(mb, d, "sports")
		"luxury":
			_car(mb, d, "luxury")
		"van", "ambulance":
			_van(mb, d, type_id == "ambulance")
		"truck", "fire":
			_truck(mb, d, type_id == "fire")
		"bus":
			_bus(mb, d)
		"motorcycle":
			_moto(mb, d)
		"bicycle":
			_bicycle(mb, d)
		_:
			_car(mb, d, "sedan")
	match type_id:
		"taxi":
			mb.box("paint", Vector3(0, 1.53, 0.15), Vector3(0.62, 0.05, 0.30))
			mb.box("display", Vector3(0, 1.62, 0.15), Vector3(0.58, 0.16, 0.26))
			d.labels.append({"text": "TAXI", "pos": Vector3(0, 1.62, 0.02), "rot": 0.0, "size": 0.12, "color": Color(0.1, 0.1, 0.1), "glow": true, "bg": Color(1.0, 0.92, 0.55)})
			d.labels.append({"text": "TAXI", "pos": Vector3(0, 1.62, 0.28), "rot": PI, "size": 0.12, "color": Color(0.1, 0.1, 0.1), "glow": true})
		"police":
			_light_bar(mb, d, Vector3(0, 1.48, 0.15), 1.1)
			mb.box("stripe", Vector3(-0.925, 0.62, 0.0), Vector3(0.02, 0.2, 4.2))
			mb.box("stripe", Vector3(0.925, 0.62, 0.0), Vector3(0.02, 0.2, 4.2))
			d.labels.append({"text": "POLIZEI", "pos": Vector3(0.94, 0.80, 0.25), "rot": PI * 0.5, "size": 0.2, "color": Color(0.08, 0.2, 0.55)})
			d.labels.append({"text": "POLIZEI", "pos": Vector3(-0.94, 0.80, 0.25), "rot": -PI * 0.5, "size": 0.2, "color": Color(0.08, 0.2, 0.55)})
			d["stripe_color"] = Color(0.10, 0.25, 0.65)
	var res: Array = mb.commit()
	d["mesh"] = res[0]
	d["surf"] = res[1]
	var mesh: ArrayMesh = d.mesh
	var surf: Dictionary = d.surf
	var defaults := {"glass": "glass", "trim": "trim", "chrome": "chrome", "tire": "tire", "plate": "plate",
		"head": "head_off", "tail": "tail_off", "blink_l": "blink_off", "blink_r": "blink_off",
		"beacon_a": "beacon_off", "beacon_b": "beacon_off", "skin": "skin", "door": "door",
		"display": "display", "cargo": "cargo"}
	for k in surf:
		if defaults.has(k):
			mesh.surface_set_material(surf[k], material(defaults[k]))
		elif k == "stripe":
			mesh.surface_set_material(surf[k], paint_material(d.get("stripe_color", Color(0.8, 0.1, 0.1)), 0.1))
		elif k == "rider":
			mesh.surface_set_material(surf[k], paint_material(Color(0.2, 0.25, 0.35), 0.0))
		elif k == "paint":
			mesh.surface_set_material(surf[k], paint_material(Color(0.6, 0.6, 0.6)))
	_models[type_id] = d
	return d


## Pkw-Karosserien.
static func _car(mb: MB, d: Dictionary, style: String) -> void:
	var L: float = d.length
	var W: float = d.width
	var hw := W * 0.5
	var secs := []
	var wr := 0.34
	var wb := 0.31     # Radstand / 2 als Anteil der Laenge
	var lamp_y := 0.66
	var tail_y := 0.80
	match style:
		"sedan":
			secs = [[-0.50, 0.30, 0.74, 0, 0.95, 0], [-0.475, 0.24, 0.88, 0, 0.99, 0], [-0.37, 0.22, 0.92, 0, 1.0, 0],
				[-0.20, 0.22, 0.93, 1.42, 1.0, 0.77], [0.08, 0.22, 0.95, 1.46, 1.0, 0.79], [0.25, 0.22, 0.96, 0, 1.0, 0],
				[0.44, 0.26, 0.84, 0, 0.98, 0], [0.50, 0.32, 0.72, 0, 0.92, 0]]
		"luxury":
			secs = [[-0.50, 0.30, 0.78, 0, 0.95, 0], [-0.475, 0.24, 0.90, 0, 0.99, 0], [-0.36, 0.22, 0.94, 0, 1.0, 0],
				[-0.19, 0.22, 0.95, 1.44, 1.0, 0.78], [0.09, 0.22, 0.97, 1.47, 1.0, 0.80], [0.24, 0.22, 0.98, 0, 1.0, 0],
				[0.45, 0.27, 0.86, 0, 0.98, 0], [0.50, 0.34, 0.76, 0, 0.94, 0]]
			wb = 0.32
		"hatchback":
			secs = [[-0.50, 0.30, 0.90, 0, 0.95, 0], [-0.47, 0.24, 0.94, 1.36, 1.0, 0.72], [-0.30, 0.22, 0.95, 1.48, 1.0, 0.78],
				[0.10, 0.22, 0.95, 1.50, 1.0, 0.80], [0.27, 0.22, 0.95, 0, 1.0, 0], [0.44, 0.26, 0.84, 0, 0.98, 0],
				[0.50, 0.32, 0.72, 0, 0.92, 0]]
			wr = 0.32
			wb = 0.315
			tail_y = 0.86
		"suv":
			secs = [[-0.50, 0.40, 1.00, 0, 0.96, 0], [-0.475, 0.36, 1.04, 1.66, 1.0, 0.86], [-0.30, 0.34, 1.06, 1.74, 1.0, 0.88],
				[0.12, 0.34, 1.06, 1.74, 1.0, 0.88], [0.27, 0.34, 1.08, 0, 1.0, 0], [0.45, 0.38, 1.0, 0, 0.98, 0],
				[0.50, 0.44, 0.88, 0, 0.94, 0]]
			wr = 0.38
			lamp_y = 0.86
			tail_y = 1.0
		"sports":
			secs = [[-0.50, 0.26, 0.66, 0, 0.94, 0], [-0.47, 0.20, 0.78, 0, 1.0, 0], [-0.36, 0.18, 0.80, 0, 1.0, 0],
				[-0.12, 0.18, 0.80, 1.20, 1.0, 0.70], [0.08, 0.18, 0.80, 1.24, 1.0, 0.72], [0.22, 0.18, 0.78, 0, 1.0, 0],
				[0.44, 0.18, 0.62, 0, 0.97, 0], [0.50, 0.22, 0.50, 0, 0.88, 0]]
			wr = 0.34
			lamp_y = 0.52
			tail_y = 0.66
	for s in secs:
		s[0] = s[0] * L
		s[4] = s[4] * hw
		s[5] = s[5] * hw
	loft(mb, secs)
	var front := -L * 0.5
	var back := L * 0.5
	# B-Saeule
	for sx: float in [-1.0, 1.0]:
		var pz: float = -L * (-0.04 if style != "hatchback" else 0.0)
		mb.box("trim", Vector3(sx * hw * 0.89, 1.15 if style != "sports" else 0.98, pz), Vector3(0.03, 0.42 if style != "sports" else 0.3, 0.09), Basis(Vector3.FORWARD, sx * 0.12))
		# Guertellinie, Seitenschweller
		mb.box("trim", Vector3(sx * hw * 0.975, 0.3 if style != "suv" else 0.42, 0), Vector3(0.04, 0.06, L * 0.62))
		# Spiegel
		var mz := -L * (0.22 if style != "sports" else 0.18)
		var my := 1.02 if style != "sports" else 0.86
		if style == "suv":
			my = 1.16
		mb.box("paint", Vector3(sx * (hw + 0.09), my, mz), Vector3(0.16, 0.11, 0.08))
		mb.box("glass", Vector3(sx * (hw + 0.09), my, mz + 0.045), Vector3(0.13, 0.08, 0.01))
	# Stossfaenger
	var bump_y := 0.38 if style != "suv" else 0.5
	if style == "sports":
		bump_y = 0.28
	mb.box("trim", Vector3(0, bump_y, front - 0.02), Vector3(W * 0.94, 0.16, 0.12))
	mb.box("trim", Vector3(0, bump_y, back + 0.02), Vector3(W * 0.94, 0.16, 0.12))
	# Kuehlergrill
	var grill := "chrome" if style == "luxury" else "trim"
	mb.box(grill, Vector3(0, lamp_y - 0.1, front + 0.005), Vector3(W * (0.42 if style != "luxury" else 0.36), 0.16 if style != "luxury" else 0.24, 0.04))
	# Scheinwerfer, Rueckleuchten, Blinker
	for sx: float in [-1.0, 1.0]:
		mb.box("head", Vector3(sx * hw * 0.66, lamp_y, front + 0.02), Vector3(hw * 0.42, 0.10, 0.06))
		mb.box("blink_l" if sx < 0 else "blink_r", Vector3(sx * hw * 0.9, lamp_y - 0.04, front + 0.04), Vector3(hw * 0.12, 0.06, 0.05))
		mb.box("tail", Vector3(sx * hw * 0.70, tail_y, back - 0.01), Vector3(hw * 0.44, 0.10, 0.06))
		mb.box("blink_l" if sx < 0 else "blink_r", Vector3(sx * hw * 0.92, tail_y - 0.09, back - 0.01), Vector3(hw * 0.12, 0.05, 0.05))
		d.lights.head.append(Vector3(sx * hw * 0.66, lamp_y, front))
		d.lights.tail.append(Vector3(sx * hw * 0.70, tail_y, back))
	# Kennzeichen
	mb.box("plate", Vector3(0, bump_y + 0.02, front - 0.09), Vector3(0.52, 0.11, 0.02))
	mb.box("plate", Vector3(0, bump_y + 0.2, back + 0.0), Vector3(0.52, 0.11, 0.02))
	# Dachreling / Spoiler
	if style == "suv":
		for sx: float in [-1.0, 1.0]:
			mb.box("trim", Vector3(sx * hw * 0.78, 1.77, L * 0.08), Vector3(0.05, 0.05, L * 0.62))
	if style == "sports":
		mb.box("trim", Vector3(0, 0.92, back - 0.15), Vector3(W * 0.86, 0.04, 0.26))
		for sx: float in [-1.0, 1.0]:
			mb.box("trim", Vector3(sx * hw * 0.6, 0.85, back - 0.15), Vector3(0.05, 0.12, 0.12))
	_wheel_arches(mb, d, L * wb, wr, hw, 2)


## Radkaesten (schwarze Boegen) und Radliste fuer 2 oder mehr Achsen.
static func _wheel_arches(mb: MB, d: Dictionary, half_base: float, wr: float, hw: float, axles: int, axle_z := []) -> void:
	var zs: Array = axle_z if not axle_z.is_empty() else [-half_base, half_base]
	if axles == 3 and axle_z.is_empty():
		zs = [-half_base, half_base, half_base - wr * 2.3]
	for k in zs.size():
		var z: float = zs[k]
		for sx: float in [-1.0, 1.0]:
			var cx := sx * (hw - 0.115)
			d.wheels.append({"pos": Vector3(cx, wr, z), "r": wr, "steer": k == 0, "w": 0.23})
			# Bogen
			var steps := 8
			for i in steps:
				var a0 := PI * i / steps
				var a1 := PI * (i + 1) / steps
				var r0 := wr + 0.04
				var r1 := wr + 0.11
				var x := sx * (hw + 0.012)
				var p0 := Vector3(x, wr + sin(a0) * r0, z + cos(a0) * r0)
				var p1 := Vector3(x, wr + sin(a1) * r0, z + cos(a1) * r0)
				var p2 := Vector3(x, wr + sin(a1) * r1, z + cos(a1) * r1)
				var p3 := Vector3(x, wr + sin(a0) * r1, z + cos(a0) * r1)
				mb.quad("trim", p0, p1, p2, p3, Vector3(0, wr, z))
	d["wheel_mesh"] = wheel_mesh(wr, 0.23)


static func _light_bar(mb: MB, d: Dictionary, c: Vector3, w: float) -> void:
	mb.box("trim", c, Vector3(w, 0.06, 0.26))
	mb.box("beacon_a", c + Vector3(-w * 0.27, 0.07, 0), Vector3(w * 0.42, 0.1, 0.22))
	mb.box("beacon_b", c + Vector3(w * 0.27, 0.07, 0), Vector3(w * 0.42, 0.1, 0.22))
	d.beacons = true


## Lieferwagen / Rettungswagen.
static func _van(mb: MB, d: Dictionary, ambulance: bool) -> void:
	var L: float = d.length
	var W: float = d.width
	var hw := W * 0.5
	var H: float = d.height
	var secs := [[-0.50, 0.36, 2.40, 0, 0.98, 0], [-0.49, 0.34, 2.50, 0, 1.0, 0], [-0.20, 0.34, 2.52, 0, 1.0, 0],
		[0.14, 0.34, 1.12, 2.44, 1.0, 0.94], [0.27, 0.34, 1.10, 2.10, 1.0, 0.92], [0.36, 0.36, 1.06, 0, 1.0, 0],
		[0.47, 0.40, 0.96, 0, 0.98, 0], [0.50, 0.46, 0.84, 0, 0.94, 0]]
	if ambulance:
		secs = [[-0.50, 0.40, 2.70, 0, 0.98, 0], [-0.49, 0.38, 2.78, 0, 1.0, 0], [0.04, 0.38, 2.80, 0, 1.0, 0],
			[0.10, 0.38, 1.18, 2.46, 0.98, 0.92], [0.27, 0.38, 1.12, 2.12, 0.98, 0.9], [0.36, 0.40, 1.08, 0, 0.98, 0],
			[0.47, 0.42, 0.98, 0, 0.96, 0], [0.50, 0.48, 0.86, 0, 0.92, 0]]
	for s in secs:
		s[0] = s[0] * L
		s[4] = s[4] * hw
		s[5] = s[5] * hw
	loft(mb, secs, true, "cargo" if not ambulance else "paint")
	var front := -L * 0.5
	var back := L * 0.5
	# Seitenfenster Fahrerhaus / Trennkante
	for sx: float in [-1.0, 1.0]:
		mb.box("glass", Vector3(sx * (hw + 0.005), 1.55, -L * 0.18), Vector3(0.01, 0.62, L * 0.13))
		mb.box("trim", Vector3(sx * (hw + 0.006), 1.3, -L * 0.03), Vector3(0.012, 1.9 if not ambulance else 0.6, 0.03))
		mb.box("trim", Vector3(sx * (hw * 0.98), 0.42, 0), Vector3(0.05, 0.08, L * 0.7))
		mb.box("paint" if not ambulance else "trim", Vector3(sx * (hw + 0.1), 1.45, -L * 0.29), Vector3(0.12, 0.26, 0.08))
	# Ladeflaeche: Hecktueren
	mb.box("trim", Vector3(0, 1.3, back + 0.005), Vector3(0.02, 1.9, 0.01))
	mb.box("glass", Vector3(-hw * 0.5, 1.95, back + 0.006), Vector3(hw * 0.7, 0.4, 0.01))
	mb.box("glass", Vector3(hw * 0.5, 1.95, back + 0.006), Vector3(hw * 0.7, 0.4, 0.01))
	mb.box("trim", Vector3(0, 0.45, front - 0.03), Vector3(W * 0.96, 0.2, 0.12))
	mb.box("trim", Vector3(0, 0.45, back + 0.03), Vector3(W * 0.96, 0.2, 0.12))
	mb.box("trim", Vector3(0, 0.72, front + 0.0), Vector3(W * 0.5, 0.2, 0.04))
	for sx: float in [-1.0, 1.0]:
		mb.box("head", Vector3(sx * hw * 0.68, 0.78, front + 0.03), Vector3(hw * 0.36, 0.14, 0.06))
		mb.box("blink_l" if sx < 0 else "blink_r", Vector3(sx * hw * 0.92, 0.66, front + 0.05), Vector3(hw * 0.12, 0.07, 0.05))
		mb.box("tail", Vector3(sx * hw * 0.9, 0.9, back + 0.01), Vector3(hw * 0.14, 0.3, 0.05))
		mb.box("blink_l" if sx < 0 else "blink_r", Vector3(sx * hw * 0.9, 0.68, back + 0.01), Vector3(hw * 0.14, 0.1, 0.05))
		d.lights.head.append(Vector3(sx * hw * 0.68, 0.78, front))
		d.lights.tail.append(Vector3(sx * hw * 0.9, 0.9, back))
	mb.box("plate", Vector3(0, 0.62, back + 0.07), Vector3(0.52, 0.11, 0.02))
	if ambulance:
		mb.box("stripe", Vector3(-hw - 0.005, 1.25, 0.1), Vector3(0.01, 0.28, L * 0.95))
		mb.box("stripe", Vector3(hw + 0.005, 1.25, 0.1), Vector3(0.01, 0.28, L * 0.95))
		mb.box("stripe", Vector3(0, 1.25, back + 0.006), Vector3(W * 0.9, 0.28, 0.01))
		_light_bar(mb, d, Vector3(0, 2.50, -L * 0.15), 1.4)
		mb.box("beacon_a", Vector3(-hw * 0.8, 2.84, back - 0.15), Vector3(0.18, 0.12, 0.18))
		mb.box("beacon_b", Vector3(hw * 0.8, 2.84, back - 0.15), Vector3(0.18, 0.12, 0.18))
		d["stripe_color"] = Color(0.95, 0.45, 0.05)
		d.labels.append({"text": "RETTUNGSDIENST", "pos": Vector3(hw + 0.02, 1.85, 0.6), "rot": PI * 0.5, "size": 0.22, "color": Color(0.85, 0.1, 0.1)})
		d.labels.append({"text": "RETTUNGSDIENST", "pos": Vector3(-hw - 0.02, 1.85, 0.6), "rot": -PI * 0.5, "size": 0.22, "color": Color(0.85, 0.1, 0.1)})
		d.labels.append({"text": "112", "pos": Vector3(0, 1.8, back + 0.02), "rot": PI, "size": 0.3, "color": Color(0.85, 0.1, 0.1)})
	else:
		d["livery"] = true
	_wheel_arches(mb, d, L * 0.31, 0.37, hw, 2)


## Lkw / Feuerwehr: Fahrerhaus + Aufbau.
static func _truck(mb: MB, d: Dictionary, fire: bool) -> void:
	var L: float = d.length
	var W: float = d.width
	var hw := W * 0.5
	var cab_len := 2.2
	var front := -L * 0.5
	var cab_c := front + cab_len * 0.5
	var cab_surf := "paint"
	# Fahrerhaus als Loft
	var secs := [[-cab_len * 0.5, 0.75, 1.75, 2.95, hw, hw * 0.97], [cab_len * 0.20, 0.75, 1.75, 3.0, hw, hw * 0.97],
		[cab_len * 0.42, 0.75, 1.70, 2.85, hw, hw * 0.95], [cab_len * 0.5, 0.75, 1.55, 0, hw * 0.98, 0]]
	var mb2 := mb
	for s in secs:
		s[0] = -cab_c + s[0]
		s[0] = -(cab_c - s[0] + cab_c) * 0.0 + s[0]
	# Querschnitt-u ist vorne positiv: u = -z
	var secs_u := []
	for s in secs:
		secs_u.append([-(cab_c) + s[0] + cab_c * 0.0, s[1], s[2], s[3], s[4], s[5]])
	for s in secs_u:
		s[0] = -cab_c + (s[0] + cab_c)
	var real := []
	for s in [[-cab_len * 0.5, 0.75, 1.75, 2.95, hw, hw * 0.97], [cab_len * 0.20, 0.75, 1.75, 3.0, hw, hw * 0.97],
			[cab_len * 0.42, 0.75, 1.70, 2.85, hw, hw * 0.95], [cab_len * 0.5, 0.75, 1.55, 0, hw * 0.98, 0]]:
		real.append([-cab_c + s[0], s[1], s[2], s[3], s[4], s[5]])
	loft(mb2, real, true, cab_surf)
	# Chassis
	mb.box("trim", Vector3(0, 0.7, cab_c + L * 0.5 - cab_len * 0.5), Vector3(W * 0.7, 0.3, L - cab_len * 0.4))
	mb.box("trim", Vector3(0, 0.55, front - 0.05), Vector3(W * 0.98, 0.3, 0.14))
	mb.box("trim", Vector3(0, 1.15, front - 0.005), Vector3(W * 0.6, 0.45, 0.04))
	var box_len := L - cab_len - 0.25
	var box_c := front + cab_len + 0.2 + box_len * 0.5
	if fire:
		# Geraeteraum mit Rolllaeden, Leiter auf dem Dach
		mb.box("paint", Vector3(0, 1.95, box_c), Vector3(W, 2.3, box_len))
		for sx: float in [-1.0, 1.0]:
			for k in 3:
				mb.box("chrome", Vector3(sx * (hw + 0.005), 1.8, box_c - box_len * 0.33 + k * box_len * 0.33), Vector3(0.01, 1.5, box_len * 0.28))
			mb.box("stripe", Vector3(sx * (hw + 0.01), 0.98, box_c), Vector3(0.01, 0.16, box_len))
		for sx: float in [-1.0, 1.0]:
			mb.box("chrome", Vector3(sx * 0.42, 3.22, box_c - 0.6), Vector3(0.07, 0.07, box_len + 1.6))
		for k in 14:
			mb.box("chrome", Vector3(0, 3.22, box_c - box_len * 0.5 - 0.3 + k * (box_len + 1.0) / 13.0), Vector3(0.84, 0.04, 0.04))
		mb.box("trim", Vector3(0, 3.12, box_c + box_len * 0.35), Vector3(1.0, 0.12, 1.0))
		_light_bar(mb, d, Vector3(0, 3.02, front + 1.1), 1.6)
		d["stripe_color"] = Color(0.95, 0.95, 0.92)
		d.labels.append({"text": "FEUERWEHR", "pos": Vector3(0, 2.68, front - 0.03), "rot": 0.0, "size": 0.22, "color": Color(1, 1, 1)})
		d.labels.append({"text": "FEUERWEHR", "pos": Vector3(hw + 0.03, 2.75, box_c), "rot": PI * 0.5, "size": 0.32, "color": Color(1, 1, 1)})
		d.labels.append({"text": "FEUERWEHR", "pos": Vector3(-hw - 0.03, 2.75, box_c), "rot": -PI * 0.5, "size": 0.32, "color": Color(1, 1, 1)})
	else:
		mb.box("cargo", Vector3(0, 2.15, box_c), Vector3(W, 2.6, box_len))
		mb.box("trim", Vector3(0, 0.88, box_c), Vector3(W * 1.0, 0.12, box_len))
		for sx: float in [-1.0, 1.0]:
			mb.box("trim", Vector3(sx * (hw + 0.005), 2.15, box_c + box_len * 0.5 - 0.03), Vector3(0.01, 2.6, 0.06))
		mb.box("trim", Vector3(0, 2.15, box_c + box_len * 0.5 + 0.005), Vector3(0.03, 2.5, 0.01))
		d["livery"] = true
	for sx: float in [-1.0, 1.0]:
		mb.box("head", Vector3(sx * hw * 0.72, 0.85, front - 0.02), Vector3(0.36, 0.16, 0.06))
		mb.box("blink_l" if sx < 0 else "blink_r", Vector3(sx * hw * 0.93, 1.02, front - 0.01), Vector3(0.14, 0.08, 0.05))
		mb.box("tail", Vector3(sx * hw * 0.8, 0.82, L * 0.5 + 0.01), Vector3(0.3, 0.14, 0.05))
		mb.box("blink_l" if sx < 0 else "blink_r", Vector3(sx * hw * 0.55, 0.82, L * 0.5 + 0.01), Vector3(0.14, 0.14, 0.05))
		d.lights.head.append(Vector3(sx * hw * 0.72, 0.85, front))
		d.lights.tail.append(Vector3(sx * hw * 0.8, 0.82, L * 0.5))
		mb.box("trim", Vector3(sx * (hw + 0.14), 2.1, front + 0.35), Vector3(0.06, 0.4, 0.1))
	mb.box("plate", Vector3(0, 0.6, L * 0.5 + 0.02), Vector3(0.52, 0.11, 0.02))
	var wr := 0.5
	_wheel_arches(mb, d, 0.0, wr, hw, 3, [front + 1.25, L * 0.5 - 2.1, L * 0.5 - 1.05])


## Stadtbus mit Fensterband, Tueren und Zielanzeige.
static func _bus(mb: MB, d: Dictionary) -> void:
	var L: float = d.length
	var W: float = d.width
	var hw := W * 0.5
	var H: float = d.height
	var front := -L * 0.5
	var back := L * 0.5
	var y0 := 0.32
	var belt := 1.12
	var top := 2.78
	# Grundkoerper (Lack) und Dach
	mb.box("paint", Vector3(0, (y0 + belt) * 0.5, 0), Vector3(W, belt - y0, L))
	mb.box("paint", Vector3(0, (top + H) * 0.5, 0), Vector3(W * 0.98, H - top, L - 0.1))
	mb.box("trim", Vector3(0, H + 0.12, L * 0.12), Vector3(W * 0.7, 0.22, 3.2))
	# Fensterband: Glas mit Saeulen
	mb.box("glass", Vector3(0, (belt + top) * 0.5, 0.1), Vector3(W * 0.985, top - belt, L - 0.4))
	var doors := [front + 1.3, -0.6]
	var pillars := []
	var z := front + 0.4
	while z < back - 0.5:
		pillars.append(z)
		z += 1.45
	for pz in pillars:
		var near_door := false
		for dz in doors:
			if absf(pz - dz) < 0.9:
				near_door = true
		for sx: float in [-1.0, 1.0]:
			mb.box("paint", Vector3(sx * hw * 0.995, (belt + top) * 0.5, pz), Vector3(0.03, top - belt, 0.12))
	# Heckpartie ohne Fenster
	mb.box("paint", Vector3(0, (belt + top) * 0.5, back - 0.35), Vector3(W, top - belt, 0.7))
	# Front: grosse Scheibe, Anzeige
	mb.box("glass", Vector3(0, 1.75, front + 0.02), Vector3(W * 0.92, 1.7, 0.04))
	mb.box("display", Vector3(0, 2.86, front + 0.03), Vector3(W * 0.8, 0.32, 0.05))
	mb.box("display", Vector3(hw + 0.01, 2.9, front + 2.3), Vector3(0.02, 0.24, 1.4))
	mb.box("display", Vector3(0, 2.9, back + 0.01), Vector3(0.6, 0.26, 0.02))
	mb.box("trim", Vector3(0, 0.55, front - 0.04), Vector3(W * 0.98, 0.42, 0.1))
	mb.box("trim", Vector3(0, 0.55, back + 0.04), Vector3(W * 0.98, 0.42, 0.1))
	mb.box("trim", Vector3(0, 0.35, 0), Vector3(W * 1.0, 0.1, L * 0.9))
	# Tueren (rechte Seite, +X) als eigene Oberflaeche
	for dz in doors:
		mb.box("door", Vector3(hw + 0.012, (y0 + top) * 0.5 + 0.08, dz), Vector3(0.02, top - y0 - 0.1, 1.25))
		mb.box("trim", Vector3(hw + 0.016, (y0 + top) * 0.5 + 0.08, dz), Vector3(0.02, top - y0 - 0.1, 0.04))
		d.doors.append({"z": dz, "x": hw + 0.012, "w": 1.25, "h": top - y0 - 0.1, "y": (y0 + top) * 0.5 + 0.08})
	# Spiegel
	for sx: float in [-1.0, 1.0]:
		mb.box("trim", Vector3(sx * (hw + 0.25), 2.2, front - 0.15), Vector3(0.08, 0.36, 0.08))
		mb.box("trim", Vector3(sx * (hw + 0.12), 2.5, front - 0.05), Vector3(0.3, 0.04, 0.04))
	# Lichter
	for sx: float in [-1.0, 1.0]:
		mb.box("head", Vector3(sx * hw * 0.72, 0.72, front - 0.02), Vector3(0.42, 0.14, 0.06))
		mb.box("blink_l" if sx < 0 else "blink_r", Vector3(sx * hw * 0.92, 0.9, front - 0.01), Vector3(0.14, 0.08, 0.05))
		mb.box("tail", Vector3(sx * hw * 0.84, 0.95, back + 0.01), Vector3(0.22, 0.4, 0.05))
		mb.box("blink_l" if sx < 0 else "blink_r", Vector3(sx * hw * 0.84, 1.25, back + 0.01), Vector3(0.22, 0.12, 0.05))
		d.lights.head.append(Vector3(sx * hw * 0.72, 0.72, front))
		d.lights.tail.append(Vector3(sx * hw * 0.84, 0.95, back))
	mb.box("plate", Vector3(0, 0.62, back + 0.1), Vector3(0.52, 0.11, 0.02))
	# Linienfarbe als Band
	mb.box("stripe", Vector3(-hw - 0.006, 0.85, 0.2), Vector3(0.012, 0.18, L - 1.0))
	mb.box("stripe", Vector3(hw + 0.006, 0.85, 1.6), Vector3(0.012, 0.18, L - 4.4))
	d["stripe_color"] = Color(0.85, 0.2, 0.25)
	_wheel_arches(mb, d, 0.0, 0.5, hw, 2, [front + 2.6, back - 3.2])


## Fahrer-/Mitfahrerfigur (sitzend) fuer Zweiraeder.
static func _rider(mb: MB, seat: Vector3, lean: float, hands: Vector3, helmet: bool) -> void:
	var b := Basis(Vector3.RIGHT, -lean)
	var hip := seat + Vector3(0, 0.08, 0)
	var chest := hip + b * Vector3(0, 0.5, 0)
	mb.box("rider", (hip + chest) * 0.5, Vector3(0.36, 0.56, 0.22), b)
	var head := chest + b * Vector3(0, 0.22, 0)
	mb.sphere("skin", head, Vector3(0.1, 0.12, 0.11), 5, 8)
	if helmet:
		mb.sphere("trim", head + Vector3(0, 0.03, 0), Vector3(0.14, 0.14, 0.15), 5, 8)
	else:
		mb.sphere("rider", head + Vector3(0, 0.05, 0.02), Vector3(0.105, 0.09, 0.11), 4, 8)
	for sx: float in [-1.0, 1.0]:
		var sh := chest + Vector3(sx * 0.2, -0.05, 0)
		var hand := hands + Vector3(sx * 0.24, 0, 0)
		var mid := (sh + hand) * 0.5
		var dirv := (hand - sh)
		var bb := Basis.looking_at(dirv.normalized(), Vector3.UP)
		mb.box("rider", mid, Vector3(0.09, 0.09, dirv.length()), bb)
		# Beine: Oberschenkel nach vorne, Unterschenkel nach unten
		var knee := hip + Vector3(sx * 0.12, 0.05, -0.38)
		var foot := knee + Vector3(0, -0.45, 0.08)
		mb.box("trim", (hip + Vector3(sx * 0.12, 0, 0) + knee) * 0.5, Vector3(0.13, 0.13, 0.42), Basis.looking_at((knee - hip).normalized(), Vector3.UP))
		mb.box("trim", (knee + foot) * 0.5, Vector3(0.11, 0.45, 0.11))


static func _moto(mb: MB, d: Dictionary) -> void:
	var L: float = d.length
	var wr := 0.31
	var zf := -L * 0.5 + wr + 0.05
	var zr := L * 0.5 - wr - 0.05
	d.wheels.append({"pos": Vector3(0, wr, zf), "r": wr, "steer": true, "w": 0.12})
	d.wheels.append({"pos": Vector3(0, wr, zr), "r": wr, "steer": false, "w": 0.15})
	d["wheel_mesh"] = wheel_mesh(wr, 0.13)
	# Tank, Sitz, Verkleidung, Motor
	mb.box("paint", Vector3(0, 0.88, -0.2), Vector3(0.32, 0.24, 0.6), Basis(Vector3.RIGHT, 0.12))
	mb.box("paint", Vector3(0, 0.86, -0.62), Vector3(0.36, 0.34, 0.3), Basis(Vector3.RIGHT, -0.5))
	mb.box("trim", Vector3(0, 0.86, 0.32), Vector3(0.28, 0.1, 0.62))
	mb.box("paint", Vector3(0, 0.82, 0.72), Vector3(0.22, 0.12, 0.36), Basis(Vector3.RIGHT, -0.25))
	mb.box("chrome", Vector3(0, 0.5, 0.0), Vector3(0.3, 0.32, 0.5))
	mb.box("chrome", Vector3(0.16, 0.38, 0.55), Vector3(0.08, 0.08, 0.7))
	mb.box("trim", Vector3(0, 0.62, zf + 0.12), Vector3(0.06, 0.7, 0.06), Basis(Vector3.RIGHT, 0.4))
	mb.box("trim", Vector3(0, 0.62, zr - 0.25), Vector3(0.08, 0.06, 0.6), Basis(Vector3.RIGHT, -0.4))
	mb.box("trim", Vector3(0, 1.06, -0.72), Vector3(0.7, 0.04, 0.04))
	mb.box("head", Vector3(0, 0.95, -0.80), Vector3(0.16, 0.12, 0.05))
	mb.box("tail", Vector3(0, 0.92, 0.9), Vector3(0.14, 0.06, 0.04))
	mb.box("blink_l", Vector3(-0.14, 0.92, -0.78), Vector3(0.05, 0.04, 0.04))
	mb.box("blink_r", Vector3(0.14, 0.92, -0.78), Vector3(0.05, 0.04, 0.04))
	d.lights.head.append(Vector3(0, 0.95, -0.8))
	d.lights.tail.append(Vector3(0, 0.92, 0.9))
	_rider(mb, Vector3(0, 0.92, 0.25), 0.55, Vector3(0, 1.1, -0.66), true)
	d["rider"] = true


static func _bicycle(mb: MB, d: Dictionary) -> void:
	var wr := 0.34
	var zf := -0.55
	var zr := 0.52
	d.wheels.append({"pos": Vector3(0, wr, zf), "r": wr, "steer": true, "w": 0.04})
	d.wheels.append({"pos": Vector3(0, wr, zr), "r": wr, "steer": false, "w": 0.04})
	d["wheel_mesh"] = wheel_mesh(wr, 0.04, 1)
	var bb := Vector3(0, 0.36, 0.0)
	var seat := Vector3(0, 0.95, 0.22)
	var head := Vector3(0, 0.98, -0.42)
	_tube(mb, "paint", bb, seat - Vector3(0, 0.08, 0), 0.03)
	_tube(mb, "paint", bb, head - Vector3(0, 0.12, 0), 0.032)
	_tube(mb, "paint", seat - Vector3(0, 0.12, 0), head - Vector3(0, 0.08, 0), 0.028)
	_tube(mb, "paint", bb, Vector3(0, wr, zr), 0.02)
	_tube(mb, "paint", seat - Vector3(0, 0.1, 0), Vector3(0, wr, zr), 0.02)
	_tube(mb, "chrome", head, Vector3(0, wr, zf), 0.022)
	mb.box("trim", seat, Vector3(0.12, 0.05, 0.24))
	mb.box("trim", head + Vector3(0, 0.05, 0), Vector3(0.56, 0.03, 0.03))
	mb.box("head", Vector3(0, 0.82, zf - 0.08), Vector3(0.07, 0.07, 0.04))
	mb.box("tail", Vector3(0, 0.75, zr + 0.18), Vector3(0.06, 0.04, 0.03))
	d.lights.head.append(Vector3(0, 0.82, zf - 0.1))
	d.lights.tail.append(Vector3(0, 0.75, zr + 0.2))
	_rider(mb, seat + Vector3(0, 0.0, 0.0), 0.35, head + Vector3(0, 0.08, 0), false)
	d["rider"] = true


static func _tube(mb: MB, surf: String, a: Vector3, b: Vector3, r: float) -> void:
	var dirv := b - a
	mb.cyl(surf, (a + b) * 0.5, dirv.normalized(), r, dirv.length(), 6, false)
