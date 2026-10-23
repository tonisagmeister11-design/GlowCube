class_name MeshBatch
extends RefCounted
## Sammelt Geometrie fuer mehrere Materialien (Schluessel -> Oberflaeche) und
## erzeugt daraus ein ArrayMesh. Wird in Worker-Threads befuellt (reine Daten),
## das Mesh entsteht erst im Hauptthread ueber build_mesh().
##
## Jeder Vertex hat Position, Normale, Tangente, UV (Meter), Farbe und CUSTOM0
## (RGBA float; fuer Fassaden: Geschosshoehe, Achsabstand, Zufallswert, Flags).

var surfaces: Dictionary = {}   # key -> Surf


class Surf:
	var v := PackedVector3Array()
	var n := PackedVector3Array()
	var t := PackedFloat32Array()
	var uv := PackedVector2Array()
	var c := PackedColorArray()
	var cu := PackedFloat32Array()
	var idx := PackedInt32Array()

	func vcount() -> int:
		return v.size()

	## Viereck p0..p3 gegen den Uhrzeigersinn von vorne gesehen (Normale zeigt zum Betrachter).
	func quad(p0: Vector3, p1: Vector3, p2: Vector3, p3: Vector3, nrm: Vector3, tan: Vector3,
			uv0: Vector2, uv1: Vector2, uv2: Vector2, uv3: Vector2, col: Color, cus: Color) -> void:
		var b := v.size()
		v.append(p0)
		v.append(p1)
		v.append(p2)
		v.append(p3)
		for i in 4:
			n.append(nrm)
			t.append(tan.x)
			t.append(tan.y)
			t.append(tan.z)
			t.append(1.0)
			c.append(col)
			cu.append(cus.r)
			cu.append(cus.g)
			cu.append(cus.b)
			cu.append(cus.a)
		uv.append(uv0)
		uv.append(uv1)
		uv.append(uv2)
		uv.append(uv3)
		# Godot: Vorderseite = Uhrzeigersinn in Bildschirmsicht -> Indizes umgedreht
		idx.append(b)
		idx.append(b + 2)
		idx.append(b + 1)
		idx.append(b)
		idx.append(b + 3)
		idx.append(b + 2)

	func tri(p0: Vector3, p1: Vector3, p2: Vector3, nrm: Vector3, tan: Vector3,
			uv0: Vector2, uv1: Vector2, uv2: Vector2, col: Color, cus: Color) -> void:
		var b := v.size()
		v.append(p0)
		v.append(p1)
		v.append(p2)
		for i in 3:
			n.append(nrm)
			t.append(tan.x)
			t.append(tan.y)
			t.append(tan.z)
			t.append(1.0)
			c.append(col)
			cu.append(cus.r)
			cu.append(cus.g)
			cu.append(cus.b)
			cu.append(cus.a)
		uv.append(uv0)
		uv.append(uv1)
		uv.append(uv2)
		idx.append(b)
		idx.append(b + 2)
		idx.append(b + 1)

	## Einzelner Vertex mit eigener Normale (fuer glatte Flaechen); Indizes separat.
	func vert(p: Vector3, nrm: Vector3, tan: Vector3, u: Vector2, col: Color, cus: Color) -> int:
		v.append(p)
		n.append(nrm)
		t.append(tan.x)
		t.append(tan.y)
		t.append(tan.z)
		t.append(1.0)
		uv.append(u)
		c.append(col)
		cu.append(cus.r)
		cu.append(cus.g)
		cu.append(cus.b)
		cu.append(cus.a)
		return v.size() - 1

	## Dreieck aus Vertex-Indizes, gegen den Uhrzeigersinn von vorne gesehen.
	func tri_idx(a: int, b: int, c2: int) -> void:
		idx.append(a)
		idx.append(c2)
		idx.append(b)

	func to_arrays() -> Array:
		var arr := []
		arr.resize(Mesh.ARRAY_MAX)
		arr[Mesh.ARRAY_VERTEX] = v
		arr[Mesh.ARRAY_NORMAL] = n
		arr[Mesh.ARRAY_TANGENT] = t
		arr[Mesh.ARRAY_TEX_UV] = uv
		arr[Mesh.ARRAY_COLOR] = c
		arr[Mesh.ARRAY_CUSTOM0] = cu
		arr[Mesh.ARRAY_INDEX] = idx
		return arr


const NO_CUSTOM := Color(0, 0, 0, 0)


func surf(key: String) -> Surf:
	var s: Surf = surfaces.get(key)
	if s == null:
		s = Surf.new()
		surfaces[key] = s
	return s


func is_empty() -> bool:
	for k in surfaces:
		if surfaces[k].v.size() > 0:
			return false
	return true


func vertex_count() -> int:
	var nn := 0
	for k in surfaces:
		nn += surfaces[k].v.size()
	return nn


## Waagerechtes Rechteck (Draufsicht) auf Hoehe y, Normale nach oben.
## UV = Weltkoordinaten x/z in Metern.
func flat_rect(key: String, r: Rect2, y: float, col := Color.WHITE, cus := NO_CUSTOM) -> void:
	var s := surf(key)
	var a := Vector3(r.position.x, y, r.position.y)
	var b := Vector3(r.end.x, y, r.position.y)
	var c := Vector3(r.end.x, y, r.end.y)
	var d := Vector3(r.position.x, y, r.end.y)
	s.quad(d, c, b, a, Vector3.UP, Vector3.RIGHT,
		Vector2(d.x, d.z), Vector2(c.x, c.z), Vector2(b.x, b.z), Vector2(a.x, a.z), col, cus)


## Waagerechtes Viereck aus 4 Punkten (gegen den Uhrzeigersinn von oben).
func flat_quad(key: String, p0: Vector3, p1: Vector3, p2: Vector3, p3: Vector3, col := Color.WHITE, cus := NO_CUSTOM) -> void:
	var s := surf(key)
	var nrm := (p2 - p0).cross(p1 - p0)
	if nrm.y < 0.0:
		nrm = -nrm
	nrm = nrm.normalized() if nrm.length_squared() > 1e-10 else Vector3.UP
	s.quad(p0, p1, p2, p3, nrm, Vector3.RIGHT, Vector2(p0.x, p0.z), Vector2(p1.x, p1.z),
		Vector2(p2.x, p2.z), Vector2(p3.x, p3.z), col, cus)


## Senkrechte Wand von a nach b (Draufsicht), Unterkante y0, Oberkante y1.
## Die Normale zeigt nach rechts von a->b gesehen... genauer: Aussenseite liegt
## links, wenn man von a nach b blickt und die Wand im Uhrzeigersinn umlaeuft.
## Hier: Normale = cross(UP, dir) * -1 -> Wand a->b mit Blick von aussen
## verlaeuft von links nach rechts. UV.x = Meter ab a (+u0), UV.y = Hoehe ab y0 (+v0).
func wall(key: String, a: Vector2, b: Vector2, y0: float, y1: float, col := Color.WHITE,
		cus := NO_CUSTOM, u0 := 0.0, v0 := 0.0) -> void:
	var s := surf(key)
	var d := b - a
	var ln := d.length()
	if ln < 0.001 or y1 - y0 < 0.001:
		return
	var dir3 := Vector3(d.x / ln, 0.0, d.y / ln)
	# Aussen: rechts-von(UP, n) = dir  =>  n = dir x UP
	var nrm := dir3.cross(Vector3.UP)
	var p0 := Vector3(a.x, y0, a.y)
	var p1 := Vector3(b.x, y0, b.y)
	var p2 := Vector3(b.x, y1, b.y)
	var p3 := Vector3(a.x, y1, a.y)
	s.quad(p0, p1, p2, p3, nrm, dir3, Vector2(u0, v0), Vector2(u0 + ln, v0),
		Vector2(u0 + ln, v0 + y1 - y0), Vector2(u0, v0 + y1 - y0), col, cus)


## Quader (achsparallel) mit Mittelpunkt c und Groesse s. UV in Metern.
## faces: Bitmaske 1=+x 2=-x 4=+y 8=-y 16=+z 32=-z.
func box(key: String, c: Vector3, sz: Vector3, col := Color.WHITE, cus := NO_CUSTOM, faces := 63) -> void:
	var h := sz * 0.5
	var x0 := c.x - h.x
	var x1 := c.x + h.x
	var y0 := c.y - h.y
	var y1 := c.y + h.y
	var z0 := c.z - h.z
	var z1 := c.z + h.z
	if faces & 16:
		wall(key, Vector2(x0, z1), Vector2(x1, z1), y0, y1, col, cus, x0)
	if faces & 32:
		wall(key, Vector2(x1, z0), Vector2(x0, z0), y0, y1, col, cus, -x1)
	if faces & 1:
		wall(key, Vector2(x1, z1), Vector2(x1, z0), y0, y1, col, cus, -z1)
	if faces & 2:
		wall(key, Vector2(x0, z0), Vector2(x0, z1), y0, y1, col, cus, z0)
	if faces & 4:
		flat_rect(key, Rect2(x0, z0, sz.x, sz.z), y1, col, cus)
	if faces & 8:
		var s := surf(key)
		var a := Vector3(x0, y0, z0)
		var b := Vector3(x1, y0, z0)
		var cc := Vector3(x1, y0, z1)
		var d := Vector3(x0, y0, z1)
		s.quad(a, b, cc, d, Vector3.DOWN, Vector3.RIGHT, Vector2(a.x, a.z), Vector2(b.x, b.z),
			Vector2(cc.x, cc.z), Vector2(d.x, d.z), col, cus)


## Gedrehter Quader: Mittelpunkt c, Groesse sz (lokal), Basis aus Gierwinkel yaw.
func box_rot(key: String, c: Vector3, sz: Vector3, yaw: float, col := Color.WHITE, cus := NO_CUSTOM) -> void:
	var xf := Transform3D(Basis(Vector3.UP, yaw), c)
	box_xf(key, xf, sz, col, cus)


## Quader mit beliebiger Transformation (Einheitswuerfel * sz).
func box_xf(key: String, xf: Transform3D, sz: Vector3, col := Color.WHITE, cus := NO_CUSTOM, faces := 63) -> void:
	var s := surf(key)
	var h := sz * 0.5
	# Flaechen: (Normale, Tangente, Bitangente) lokal
	var defs := [
		[Vector3.RIGHT, Vector3.BACK * -1.0, Vector3.UP, 1],
		[Vector3.LEFT, Vector3.BACK, Vector3.UP, 2],
		[Vector3.UP, Vector3.RIGHT, Vector3.FORWARD, 4],
		[Vector3.DOWN, Vector3.RIGHT, Vector3.BACK, 8],
		[Vector3.BACK, Vector3.RIGHT, Vector3.UP, 16],
		[Vector3.FORWARD, Vector3.LEFT, Vector3.UP, 32],
	]
	for dfn in defs:
		if not (faces & int(dfn[3])):
			continue
		var nl: Vector3 = dfn[0]
		var tl: Vector3 = dfn[1]
		var bl: Vector3 = dfn[2]
		var ext_n := absf(nl.dot(h))
		var ext_t := absf(tl.dot(h))
		var ext_b := absf(bl.dot(h))
		var center := nl * ext_n
		var p0 := xf * (center - tl * ext_t - bl * ext_b)
		var p1 := xf * (center + tl * ext_t - bl * ext_b)
		var p2 := xf * (center + tl * ext_t + bl * ext_b)
		var p3 := xf * (center - tl * ext_t + bl * ext_b)
		var nw := (xf.basis * nl).normalized()
		var tw := (xf.basis * tl).normalized()
		s.quad(p0, p1, p2, p3, nw, tw, Vector2(0, 0), Vector2(ext_t * 2.0, 0),
			Vector2(ext_t * 2.0, ext_b * 2.0), Vector2(0, ext_b * 2.0), col, cus)


## Zylinder (senkrecht) mit seg Segmenten, ohne Boden. UV: u = Umfang (m), v = Hoehe.
func cylinder(key: String, base: Vector3, r0: float, r1: float, height: float, seg := 8,
		col := Color.WHITE, cus := NO_CUSTOM, cap := true) -> void:
	var s := surf(key)
	var circ := TAU * maxf(r0, r1)
	for i in seg:
		var a0 := TAU * float(i) / seg
		var a1 := TAU * float(i + 1) / seg
		var d0 := Vector3(cos(a0), 0, sin(a0))
		var d1 := Vector3(cos(a1), 0, sin(a1))
		var p0 := base + d0 * r0
		var p1 := base + d1 * r0
		var p2 := base + d1 * r1 + Vector3(0, height, 0)
		var p3 := base + d0 * r1 + Vector3(0, height, 0)
		var slope := (r0 - r1) / maxf(height, 0.001)
		var n0 := (d0 + Vector3(0, slope, 0)).normalized()
		var n1 := (d1 + Vector3(0, slope, 0)).normalized()
		var u0 := circ * float(i) / seg
		var u1 := circ * float(i + 1) / seg
		var b := s.vert(p0, n0, Vector3(-d0.z, 0, d0.x), Vector2(u0, 0), col, cus)
		s.vert(p1, n1, Vector3(-d1.z, 0, d1.x), Vector2(u1, 0), col, cus)
		s.vert(p2, n1, Vector3(-d1.z, 0, d1.x), Vector2(u1, height), col, cus)
		s.vert(p3, n0, Vector3(-d0.z, 0, d0.x), Vector2(u0, height), col, cus)
		# Aussenseite: von aussen gesehen gegen den Uhrzeigersinn p0,p3,p2,p1? -> pruefen ueber Normale
		s.tri_idx(b, b + 3, b + 2)
		s.tri_idx(b, b + 2, b + 1)
	if cap and r1 > 0.001:
		var top := base + Vector3(0, height, 0)
		var ci := s.vert(top, Vector3.UP, Vector3.RIGHT, Vector2(top.x, top.z), col, cus)
		var first := -1
		var prev := -1
		for i in seg + 1:
			var a := TAU * float(i % seg) / seg
			var p := top + Vector3(cos(a), 0, sin(a)) * r1
			var vi := s.vert(p, Vector3.UP, Vector3.RIGHT, Vector2(p.x, p.z), col, cus)
			if prev >= 0:
				s.tri_idx(ci, vi, prev)
			prev = vi
			if first < 0:
				first = vi


## Doppelte vorzeichenbehaftete Flaeche in (x, z). Negativ = gegen den
## Uhrzeigersinn von oben gesehen (Konvention dieser Klasse).
static func area_xz(poly: PackedVector2Array) -> float:
	var a := 0.0
	var nn := poly.size()
	for i in nn:
		var p := poly[i]
		var q := poly[(i + 1) % nn]
		a += p.x * q.y - q.x * p.y
	return a


## Prisma: Grundriss-Polygon (beliebiger Umlaufsinn), von y0 bis y1,
## mit Deckel. Fuer Brunnenbecken, Inseln usw.
func prism(key: String, poly_in: PackedVector2Array, y0: float, y1: float, col := Color.WHITE,
		cus := NO_CUSTOM, top := true, sides := true) -> void:
	var poly := poly_in
	if area_xz(poly) > 0.0:
		poly = poly_in.duplicate()
		poly.reverse()
	var nn := poly.size()
	if sides:
		for i in nn:
			wall(key, poly[i], poly[(i + 1) % nn], y0, y1, col, cus)
	if top:
		var tris := Geometry2D.triangulate_polygon(poly)
		var s := surf(key)
		for i in range(0, tris.size(), 3):
			var p0 := poly[tris[i]]
			var p1 := poly[tris[i + 1]]
			var p2 := poly[tris[i + 2]]
			var a3 := Vector3(p0.x, y1, p0.y)
			var b3 := Vector3(p1.x, y1, p1.y)
			var c3 := Vector3(p2.x, y1, p2.y)
			var nrm := (b3 - a3).cross(c3 - a3)
			if nrm.y < 0.0:
				s.tri(a3, c3, b3, Vector3.UP, Vector3.RIGHT, Vector2(a3.x, a3.z), Vector2(c3.x, c3.z), Vector2(b3.x, b3.z), col, cus)
			else:
				s.tri(a3, b3, c3, Vector3.UP, Vector3.RIGHT, Vector2(a3.x, a3.z), Vector2(b3.x, b3.z), Vector2(c3.x, c3.z), col, cus)


## Liefert Arrays je Material fuer den Hauptthread (thread-sicher, reine Daten).
func to_data() -> Dictionary:
	var out := {}
	for k in surfaces:
		var s: Surf = surfaces[k]
		if s.v.size() == 0:
			continue
		out[k] = s.to_arrays()
	return out


const FORMAT_FLAGS := Mesh.ARRAY_CUSTOM_RGBA_FLOAT << Mesh.ARRAY_FORMAT_CUSTOM0_SHIFT


## Erzeugt ein ArrayMesh aus to_data()-Daten. mat_lookup: Callable(key) -> Material.
static func build_mesh(data: Dictionary, mat_lookup: Callable) -> ArrayMesh:
	var mesh := ArrayMesh.new()
	var keys := data.keys()
	keys.sort()
	for k in keys:
		var arr: Array = data[k]
		mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arr, [], {}, FORMAT_FLAGS)
		mesh.surface_set_material(mesh.get_surface_count() - 1, mat_lookup.call(k))
	return mesh
