class_name RoadBuilder
extends RefCounted
## Boden- und Strassengeometrie (reine Daten, thread-sicher):
##  * Gehwege (Platten, y = 0.15) mit Granit-Bordstein und abgerundeten Ecken
##  * Grundstuecksflaechen (Rasen, Pflaster, Beton, Kies) mit Wegen, Zufahrten
##    und Parkplaetzen inkl. Stellplatzmarkierung
##  * Parks (Rasen, Kieswege), Uferpromenaden (Pflaster)
##  * Fahrbahnmarkierungen: Mittellinie, Fahrstreifen, Radwege (rot),
##    Parkstreifen, Halte-/Wartelinien, Zebrastreifen, Bushaltestellen
##  * Kreisverkehr-Inseln
## Global (einmal fuer die ganze Stadt): Asphaltflaeche, Fluss mit Kaimauern,
## Bruecken mit Pfeilern und Gelaendern.

const SIDEWALK_Y := 0.15
const MARK_Y := 0.012
const WHITE := Color(0.93, 0.93, 0.90)
const YELLOW := Color(0.95, 0.75, 0.15)
const BIKE := Color(0.78, 0.52, 0.46)
const PARK_TINT := Color(0.88, 0.88, 0.9)


# ============================================================ Hilfen

## Streifen (Markierung) von p0 nach p1 mit Breite w auf Hoehe y.
static func stripe(mb: MeshBatch, key: String, p0: Vector2, p1: Vector2, w: float, col: Color, y := MARK_Y) -> void:
	var d := p1 - p0
	if d.length_squared() < 1e-6:
		return
	var n := Vector2(-d.y, d.x).normalized() * (w * 0.5)
	quad2(mb, key, p0 - n, p1 - n, p1 + n, p0 + n, y, col)


## Waagerechtes Viereck aus 2D-Punkten (Umlaufsinn egal).
static func quad2(mb: MeshBatch, key: String, a: Vector2, b: Vector2, c: Vector2, d: Vector2, y: float, col: Color) -> void:
	var poly := PackedVector2Array([a, b, c, d])
	if MeshBatch.area_xz(poly) > 0.0:
		mb.flat_quad(key, Vector3(d.x, y, d.y), Vector3(c.x, y, c.y), Vector3(b.x, y, b.y), Vector3(a.x, y, a.y), col)
	else:
		mb.flat_quad(key, Vector3(a.x, y, a.y), Vector3(b.x, y, b.y), Vector3(c.x, y, c.y), Vector3(d.x, y, d.y), col)


## Gestrichelte Linie.
static func dashed(mb: MeshBatch, p0: Vector2, p1: Vector2, w: float, dash: float, gap: float, col: Color) -> void:
	var ln := p0.distance_to(p1)
	if ln < 0.1:
		return
	var dir := (p1 - p0) / ln
	var t := gap * 0.5
	while t < ln:
		var t1 := minf(t + dash, ln)
		stripe(mb, "marking", p0 + dir * t, p0 + dir * t1, w, col)
		t += dash + gap


## Abgerundetes Rechteck (gegen den Uhrzeigersinn von oben), Radien je Ecke
## [NW, NE, SE, SW]. seg Punkte je Ecke.
static func rounded_rect(r: Rect2, radii: Array, seg := 5) -> PackedVector2Array:
	var pts := PackedVector2Array()
	# Ecken in Reihenfolge SW -> SE -> NE -> NW ergibt gegen den Uhrzeigersinn von oben (Konvention MeshBatch)
	var corners := [
		[Vector2(r.position.x, r.end.y), 3, PI * 0.5, PI],          # SW: Bogen von unten nach links
		[Vector2(r.end.x, r.end.y), 2, 0.0, PI * 0.5],               # SE
		[Vector2(r.end.x, r.position.y), 1, -PI * 0.5, 0.0],         # NE
		[Vector2(r.position.x, r.position.y), 0, PI, PI * 1.5],      # NW
	]
	# Reihenfolge so, dass die Flaeche negativ (CCW von oben) wird: pruefen am Ende
	for c in corners:
		var cp: Vector2 = c[0]
		var rad: float = radii[c[1]]
		var sx := 1.0 if cp.x == r.position.x else -1.0
		var sz := 1.0 if cp.y == r.position.y else -1.0
		var center := cp + Vector2(sx * rad, sz * rad)
		if rad < 0.05:
			pts.append(cp)
			continue
		var a0: float = c[2]
		var a1: float = c[3]
		for k in seg + 1:
			var a := lerpf(a0, a1, float(k) / seg)
			pts.append(center + Vector2(cos(a), sin(a)) * rad)
	if MeshBatch.area_xz(pts) > 0.0:
		pts.reverse()
	return pts


# ============================================================ Bloecke

static func corner_radii(city: CityModel, b: Dictionary) -> Array:
	var c: Rect2 = b.curb
	var pts := [c.position, Vector2(c.end.x, c.position.y), c.end, Vector2(c.position.x, c.end.y)]
	var sw := minf(b.inner.position.x - c.position.x, b.inner.position.y - c.position.y)
	sw = maxf(sw, 1.0)
	var out := []
	for p in pts:
		var nid := city.nearest_node(p)
		var r := 1.6
		if nid >= 0:
			var n: Dictionary = city.nodes[nid]
			if n.pos.distance_to(p) > 30.0:
				r = 0.3
			elif n.kind == "roundabout":
				var box := city.node_box(nid)
				var h := maxf(box.x, box.y)
				r = (float(n.roundabout.r_outer) + 0.8 - 1.4142 * h) / 0.4142
			elif n.kind in ["pass"]:
				r = 0.3
		if b.kind == "promenade":
			r = 0.3
		out.append(clampf(r, 0.3, minf(sw * 2.8, minf(c.size.x, c.size.y) * 0.3)))
	return out


## Gehweg, Bordstein und Grundstuecksflaechen eines Blocks.
static func build_block(city: CityModel, b: Dictionary, mb: MeshBatch, det: MeshBatch, out: Dictionary, lots: Array = []) -> void:
	var curb: Rect2 = b.curb
	var inner: Rect2 = b.inner
	var radii := corner_radii(city, b)
	var outer := rounded_rect(curb, radii)
	var radii_in := []
	for r in radii:
		radii_in.append(maxf(float(r) - 0.16, 0.05))
	var inset := rounded_rect(curb.grow(-0.16), radii_in)
	var y := SIDEWALK_Y
	var promenade: bool = b.kind == "promenade"
	# Bordsteinkante: senkrechte Aussenflaeche + Oberseite
	var n := outer.size()
	for i in n:
		var a := outer[i]
		var c := outer[(i + 1) % n]
		mb.wall("g_curb", a, c, -0.1, y, Color.WHITE)
	if inset.size() == n:
		for i in n:
			var a := outer[i]
			var c := outer[(i + 1) % n]
			var ai := inset[i]
			var ci := inset[(i + 1) % n]
			quad2(mb, "g_curb", a, c, ci, ai, y, Color(0.95, 0.95, 0.95))
	# Gehwegflaeche: Faecher von den Innenecken zu den Boegen
	var key := "g_plaza" if promenade else "g_pavers"
	if promenade:
		var tris := Geometry2D.triangulate_polygon(inset)
		var s := mb.surf(key)
		for i in range(0, tris.size(), 3):
			var p0 := inset[tris[i]]
			var p1 := inset[tris[i + 1]]
			var p2 := inset[tris[i + 2]]
			var poly := PackedVector2Array([p0, p1, p2])
			if MeshBatch.area_xz(poly) > 0.0:
				var t := p1
				p1 = p2
				p2 = t
			s.tri(Vector3(p0.x, y, p0.y), Vector3(p1.x, y, p1.y), Vector3(p2.x, y, p2.y), Vector3.UP, Vector3.RIGHT,
				Vector2(p0.x, p0.y), Vector2(p1.x, p1.y), Vector2(p2.x, p2.y), Color.WHITE, MeshBatch.NO_CUSTOM)
		return
	_ring_fill(mb, key, inset, inner, y)
	# Grundstuecke
	_block_lots(city, b, mb, det, out, lots)


## Ring zwischen abgerundetem Aussenpolygon und Innenrechteck fuellen.
static func _ring_fill(mb: MeshBatch, key: String, outer: PackedVector2Array, inner: Rect2, y: float) -> void:
	var ic := [Vector2(inner.position.x, inner.end.y), inner.end, Vector2(inner.end.x, inner.position.y), inner.position]
	# Jedem Aussenpunkt die naechste Innenecke (nach Quadrant) zuordnen
	var center := inner.get_center()
	var assign := PackedInt32Array()
	for p in outer:
		var q := 0
		if p.x <= center.x and p.y >= center.y:
			q = 0
		elif p.x > center.x and p.y >= center.y:
			q = 1
		elif p.x > center.x and p.y < center.y:
			q = 2
		else:
			q = 3
		assign.append(q)
	var n := outer.size()
	for i in n:
		var j := (i + 1) % n
		var a := outer[i]
		var c := outer[j]
		var qa := assign[i]
		var qc := assign[j]
		if qa == qc:
			_tri2(mb, key, a, c, ic[qa], y)
		else:
			quad2(mb, key, a, c, ic[qc], ic[qa], y, Color.WHITE)


static func _tri2(mb: MeshBatch, key: String, a: Vector2, b: Vector2, c: Vector2, y: float) -> void:
	var poly := PackedVector2Array([a, b, c])
	var area := MeshBatch.area_xz(poly)
	if absf(area) < 1e-5:
		return
	var s := mb.surf(key)
	if area > 0.0:
		var t := b
		b = c
		c = t
	s.tri(Vector3(a.x, y, a.y), Vector3(b.x, y, b.y), Vector3(c.x, y, c.y), Vector3.UP, Vector3.RIGHT,
		Vector2(a.x, a.y), Vector2(b.x, b.y), Vector2(c.x, c.y), Color.WHITE, MeshBatch.NO_CUSTOM)


const PAVED_KINDS := ["mixed", "shop", "restaurant", "office", "tower", "market_hall", "station", "city_hall", "parliament"]
const CONCRETE_KINDS := ["warehouse", "factory", "bus_depot", "supermarket", "gas_station", "fire_station", "police"]


static func _lot_key(city: CityModel, lot: Dictionary) -> String:
	if lot.usage == "green" or lot.building < 0:
		return "g_grass"
	var bld: Dictionary = city.buildings[lot.building]
	var k: String = bld.kind
	if k in PAVED_KINDS:
		return "g_plaza" if k in ["station", "city_hall", "parliament", "market_hall"] or bld.zone in ["downtown", "oldtown"] else "g_pavers"
	if k in CONCRETE_KINDS:
		return "g_concrete"
	if k == "homeless_camp":
		return "g_dirt"
	if k == "abandoned":
		return "g_gravel"
	if k == "market_square":
		return "g_plaza"
	return "g_grass"


static func _block_lots(city: CityModel, b: Dictionary, mb: MeshBatch, det: MeshBatch, out: Dictionary, lots: Array) -> void:
	var inner: Rect2 = b.inner
	var y := SIDEWALK_Y
	if b.kind == "park":
		mb.flat_rect("g_grass", inner, y)
		return
	var covered := 0.0
	for lid in lots:
		var lot: Dictionary = city.lots[lid]
		var r: Rect2 = lot.rect
		covered += r.get_area()
		mb.flat_rect(_lot_key(city, lot), r, y)
		if lot.building < 0:
			continue
		var bld: Dictionary = city.buildings[lot.building]
		var wk: Rect2 = bld.walkway
		if wk.has_area():
			mb.flat_rect("g_pavers_ov" if _lot_key(city, lot) != "g_pavers" else "g_plaza_ov", wk, y)
		var dw: Rect2 = bld.driveway
		if dw.has_area():
			mb.flat_rect("g_concrete_ov", dw, y)
		var pk: Rect2 = bld.parking
		if pk.has_area():
			mb.flat_rect("g_asphalt_ov", pk, y)
	if covered < inner.get_area() * 0.98:
		# Luecken (z. B. nach Reparatur entfernte Grundstuecke) mit Rasen unterlegen
		mb.flat_rect("g_grass", inner, y - 0.03)


## Parkplatzmarkierungen eines Parkplatzes.
static func build_parking(city: CityModel, pa: Dictionary, mb: MeshBatch) -> void:
	for sp in pa.spots:
		var pos: Vector2 = sp.pos
		var rot: float = sp.rot
		# Stellplatz 2.6 x 5.0; rot 0 -> Laengsachse z
		var along := Vector2(sin(rot), cos(rot))
		var side := Vector2(along.y, -along.x)
		for e in [-1.0, 1.0]:
			var c: Vector2 = pos + side * (1.3 * e)
			stripe(mb, "marking", c - along * 2.4, c + along * 2.4, 0.1, WHITE, SIDEWALK_Y + MARK_Y)


static func build_park(city: CityModel, pk: Dictionary, mb: MeshBatch, det: MeshBatch, out: Dictionary) -> void:
	if pk.promenade:
		for f in pk.features:
			if f.kind == "railing":
				_railing(mb, det, f.from, f.to, SIDEWALK_Y, out)
		return
	for path in pk.paths:
		mb.flat_rect("g_gravel_ov", path, SIDEWALK_Y)


static func _railing(mb: MeshBatch, det: MeshBatch, a: Vector2, b: Vector2, y: float, out: Dictionary) -> void:
	var ln := a.distance_to(b)
	if ln < 0.5:
		return
	var dir := (b - a) / ln
	var mid := (a + b) * 0.5
	var yaw := atan2(dir.x, dir.y)
	var bas := Basis(Vector3.UP, yaw)
	mb.box_xf("paint_metal", Transform3D(bas, Vector3(mid.x, y + 1.05, mid.y)), Vector3(0.07, 0.07, ln), Color(0.2, 0.22, 0.24))
	det.box_xf("paint_metal", Transform3D(bas, Vector3(mid.x, y + 0.55, mid.y)), Vector3(0.03, 0.03, ln), Color(0.2, 0.22, 0.24))
	var npost := int(ln / 2.0)
	for k in npost + 1:
		var q := a + dir * (ln * k / maxf(npost, 1))
		det.box("paint_metal", Vector3(q.x, y + 0.53, q.y), Vector3(0.06, 1.06, 0.06), Color(0.2, 0.22, 0.24))
	out["shapes"].append({"xf": Transform3D(bas, Vector3(mid.x, y + 0.6, mid.y)), "size": Vector3(0.2, 1.2, ln)})


# ============================================================ Markierungen

static func build_edge_markings(city: CityModel, e: Dictionary, mb: MeshBatch) -> void:
	var na: Dictionary = city.nodes[e.a]
	var nb: Dictionary = city.nodes[e.b]
	var t0 := city.edge_end_clearance(e, e.a) + 0.3
	var t1: float = e.length - city.edge_end_clearance(e, e.b) - 0.3
	if t1 - t0 < 2.0:
		return
	var lanes: int = e.lanes
	var lw: float = e.lane_w
	var bw: float = e.bike_w
	var pw: float = e.park_w
	var p := func(t: float, lat: float) -> Vector2: return city.edge_point(e, t, lat)
	# Mittellinie
	if lanes >= 2:
		stripe(mb, "marking", p.call(t0 + 1.5, -0.12), p.call(t1 - 1.5, -0.12), 0.12, WHITE)
		stripe(mb, "marking", p.call(t0 + 1.5, 0.12), p.call(t1 - 1.5, 0.12), 0.12, WHITE)
		for side in [-1.0, 1.0]:
			dashed(mb, p.call(t0 + 1.5, side * lw), p.call(t1 - 1.5, side * lw), 0.12, 3.0, 6.0, WHITE)
	elif e.cls != "street" or e.width >= 10.0:
		dashed(mb, p.call(t0 + 1.5, 0.0), p.call(t1 - 1.5, 0.0), 0.12, 3.0, 6.0, WHITE)
	var edge_lat := lanes * lw
	for side in [-1.0, 1.0]:
		var sw: float = e.sidewalk[1] if side > 0 else e.sidewalk[0]
		if bw > 0.0:
			# Radweg rot eingefaerbt mit durchgezogener Begrenzung
			quad2(mb, "g_asphalt_ov", p.call(t0, side * edge_lat), p.call(t1, side * edge_lat),
				p.call(t1, side * (edge_lat + bw)), p.call(t0, side * (edge_lat + bw)), 0.0, BIKE)
			stripe(mb, "marking", p.call(t0, side * (edge_lat + 0.12)), p.call(t1, side * (edge_lat + 0.12)), 0.25, WHITE)
		elif pw > 0.0:
			dashed(mb, p.call(t0 + 1.0, side * (edge_lat + 0.08)), p.call(t1 - 1.0, side * (edge_lat + 0.08)), 0.12, 1.0, 1.0, WHITE)
		elif e.cls in ["ring", "arterial"] or sw <= 0.0:
			stripe(mb, "marking", p.call(t0, side * (edge_lat - 0.25)), p.call(t1, side * (edge_lat - 0.25)), 0.15, WHITE)
	# Halte- und Wartelinien an den Kantenenden (rechte Fahrbahnhaelfte der Zufahrt)
	for end_i in 2:
		var node: Dictionary = na if end_i == 0 else nb
		if not node.kind in ["signal", "stop", "yield", "roundabout"]:
			continue
		if node.kind == "yield" and node.priority_axis == e.axis:
			continue
		var clr := city.edge_end_clearance(e, node.id) + 0.5
		var t = clr if end_i == 0 else e.length - clr
		# Zufahrt zu Knoten a faehrt in Richtung b->a: deren rechte Seite ist links von a->b
		var s := -1.0 if end_i == 0 else 1.0
		var w := lanes * lw + bw
		if node.kind in ["signal", "stop"]:
			stripe(mb, "marking", p.call(t, s * 0.25), p.call(t, s * w), 0.45, WHITE)
		else:
			var k := 0.3
			while k < w - 0.3:
				var c: Vector2 = p.call(t, s * k)
				var fwd := city.edge_dir(e) * (1.0 if end_i == 0 else -1.0)
				var nrm := Vector2(-fwd.y, fwd.x)
				_tri_mark(mb, c - nrm * 0.25, c + nrm * 0.25, c + fwd * 0.55)
				k += 0.9
		# Pfeile vor Ampeln auf Hauptstrassen
		if node.kind == "signal" and t1 - t0 > 40.0:
			for lane in lanes:
				var lat := s * (lane + 0.5) * lw
				var tt = t + (8.0 if end_i == 0 else -8.0)
				var fwd2 := city.edge_dir(e) * (-1.0 if end_i == 0 else 1.0)
				_arrow(mb, p.call(tt, lat), fwd2)


static func _tri_mark(mb: MeshBatch, a: Vector2, b: Vector2, c: Vector2) -> void:
	var poly := PackedVector2Array([a, b, c])
	var area := MeshBatch.area_xz(poly)
	if absf(area) < 1e-5:
		return
	if area > 0.0:
		var t := b
		b = c
		c = t
	mb.surf("marking").tri(Vector3(a.x, MARK_Y, a.y), Vector3(b.x, MARK_Y, b.y), Vector3(c.x, MARK_Y, c.y), Vector3.UP, Vector3.RIGHT,
		Vector2(a.x, a.y), Vector2(b.x, b.y), Vector2(c.x, c.y), WHITE, MeshBatch.NO_CUSTOM)


## Geradeauspfeil, Spitze in Richtung fwd.
static func _arrow(mb: MeshBatch, c: Vector2, fwd: Vector2) -> void:
	var nrm := Vector2(-fwd.y, fwd.x)
	stripe(mb, "marking", c - fwd * 2.5, c + fwd * 0.8, 0.18, WHITE)
	_tri_mark(mb, c + fwd * 0.6 - nrm * 0.45, c + fwd * 0.6 + nrm * 0.45, c + fwd * 2.0)


static func build_crosswalk(city: CityModel, cw: Dictionary, mb: MeshBatch) -> void:
	var e: Dictionary = city.edges[cw.edge]
	var dir := city.edge_dir(e)
	var across := Vector2(-dir.y, dir.x)
	var c: Vector2 = cw.center
	var half: float = cw.length * 0.5 - 0.3
	var w: float = cw.width
	var k := -half + 0.25
	while k < half:
		var q := c + across * k
		stripe(mb, "marking_fresh", q - dir * (w * 0.5), q + dir * (w * 0.5), 0.5, WHITE)
		k += 1.0


static func build_street_parking(city: CityModel, sp: Dictionary, mb: MeshBatch) -> void:
	var e: Dictionary = city.edges[sp.edge]
	var dir := city.edge_dir(e)
	var pos: Vector2 = sp.pos
	var nrm := Vector2(-dir.y, dir.x)
	var pw: float = e.park_w
	for s in [-1.0, 1.0]:
		var q = pos + dir * (2.75 * s)
		stripe(mb, "marking", q - nrm * (pw * 0.45), q + nrm * (pw * 0.45), 0.1, WHITE)


static func build_bus_stop(city: CityModel, st: Dictionary, mb: MeshBatch) -> void:
	var e: Dictionary = city.edges[st.edge]
	var side: int = st.side
	var lat0: float = side * (e.width * 0.5 - 0.3)
	var lat1: float = side * (e.width * 0.5 - 2.6)
	var t: float = st.t
	var pts := []
	var k := -9.0
	var flip := false
	while k <= 9.0:
		pts.append(city.edge_point(e, t + k, lat0 if flip else lat1))
		flip = not flip
		k += 1.5
	for i in range(pts.size() - 1):
		stripe(mb, "marking", pts[i], pts[i + 1], 0.12, YELLOW)
	stripe(mb, "marking", city.edge_point(e, t - 9.0, lat0), city.edge_point(e, t + 9.0, lat0), 0.12, YELLOW)
	stripe(mb, "marking", city.edge_point(e, t - 9.0, lat1), city.edge_point(e, t + 9.0, lat1), 0.12, YELLOW)


static func build_node(city: CityModel, n: Dictionary, mb: MeshBatch, det: MeshBatch, out: Dictionary) -> void:
	if n.kind != "roundabout":
		return
	var c: Vector2 = n.pos
	var ri: float = n.roundabout.r_inner
	var ro: float = n.roundabout.r_outer
	var poly := PackedVector2Array()
	var seg := 28
	for i in seg:
		var a := -TAU * i / seg
		poly.append(c + Vector2(cos(a), sin(a)) * ri)
	mb.prism("g_curb", poly, -0.1, SIDEWALK_Y, Color.WHITE, MeshBatch.NO_CUSTOM, false, true)
	var poly2 := PackedVector2Array()
	for i in seg:
		var a := -TAU * i / seg
		poly2.append(c + Vector2(cos(a), sin(a)) * (ri - 0.25))
	# Kopfsteinring + Rasen
	for i in seg:
		var a0 := poly[i]
		var a1 := poly[(i + 1) % seg]
		var b0 := poly2[i]
		var b1 := poly2[(i + 1) % seg]
		quad2(mb, "g_curb", a0, a1, b1, b0, SIDEWALK_Y, Color.WHITE)
	var poly3 := PackedVector2Array()
	for i in seg:
		var a := -TAU * i / seg
		poly3.append(c + Vector2(cos(a), sin(a)) * (ri - 1.6))
	for i in seg:
		quad2(mb, "g_plaza", poly2[i], poly2[(i + 1) % seg], poly3[(i + 1) % seg], poly3[i], SIDEWALK_Y, Color(0.85, 0.82, 0.78))
	mb.prism("g_grass", poly3, SIDEWALK_Y, SIDEWALK_Y + 0.05, Color.WHITE, MeshBatch.NO_CUSTOM, true, false)
	out["shapes"].append({"xf": Transform3D(Basis(), Vector3(c.x, 0.4, c.y)), "size": Vector3(ri * 1.4, 0.8, ri * 1.4)})
	# Leitlinie aussen (gestrichelt)
	for i in 40:
		if i % 2 == 0:
			var a0 := TAU * i / 40.0
			var a1 := TAU * (i + 1) / 40.0
			stripe(mb, "marking", c + Vector2(cos(a0), sin(a0)) * (ro - 0.3), c + Vector2(cos(a1), sin(a1)) * (ro - 0.3), 0.12, WHITE)


# ============================================================ Global

## Asphaltflaeche der ganzen Stadt (ohne Flussband).
static func build_asphalt(city: CityModel, mb: MeshBatch) -> void:
	var half := city.size * 0.5
	var ring_hw := 8.0
	var x0 := -half - ring_hw
	var x1 := half + ring_hw
	var rv: Dictionary = city.river
	var zn: float = rv.z_north
	var zs: float = rv.z_south
	# in 200-m-Kacheln, damit Frustum-Culling greift
	var step := 200.0
	var z := -half - ring_hw
	while z < half + ring_hw:
		var z1 := minf(z + step, half + ring_hw)
		var x := x0
		while x < x1:
			var xe := minf(x + step, x1)
			for part in _split_z(z, z1, zn, zs):
				mb.flat_rect("g_asphalt", Rect2(x, part[0], xe - x, part[1] - part[0]), 0.0)
			x = xe
		z = z1


static func _split_z(z0: float, z1: float, zn: float, zs: float) -> Array:
	var out := []
	if z1 <= zn or z0 >= zs:
		return [[z0, z1]]
	if z0 < zn:
		out.append([z0, zn])
	if z1 > zs:
		out.append([zs, z1])
	return out


## Fluss: Wasser, Kaimauern, Flussbett, Bruecken.
static func build_river(city: CityModel, mb: MeshBatch, det: MeshBatch, out: Dictionary) -> void:
	var rv: Dictionary = city.river
	var zn: float = rv.z_north
	var zs: float = rv.z_south
	var half := city.size * 0.5
	var wy := -CityModel.RIVER_DEPTH + 0.8
	var far := 4200.0
	var x := -far
	while x < far:
		var xe := minf(x + 300.0, far)
		mb.flat_rect("water", Rect2(x, zn - 2.0, xe - x, zs - zn + 4.0), wy)
		x = xe
	mb.flat_rect("g_dirt", Rect2(-half - 70.0, zn, city.size + 140.0, zs - zn), -CityModel.RIVER_DEPTH - 0.5)
	# Kaimauern innerhalb der Stadt
	var bridges := []
	for e in city.edges:
		if e.bridge:
			bridges.append(e)
	var qx0 := -half - 60.0
	var qx1 := half + 60.0
	mb.wall("g_stone", Vector2(qx0, zn), Vector2(qx1, zn), -CityModel.RIVER_DEPTH - 0.6, 0.0)
	mb.wall("g_stone", Vector2(qx1, zs), Vector2(qx0, zs), -CityModel.RIVER_DEPTH - 0.6, 0.0)
	# Abdeckstein ausserhalb der Bruecken
	var gaps := []
	for e in bridges:
		var px: float = city.nodes[e.a].pos.x
		var hw: float = e.width * 0.5 + maxf(e.sidewalk[0], e.sidewalk[1]) + 0.5
		gaps.append([px - hw, px + hw])
	gaps.sort_custom(func(a, b): return a[0] < b[0])
	var xs := qx0
	for g in gaps + [[qx1, qx1]]:
		if g[0] > xs + 0.5:
			for zz in [zn, zs]:
				var sgn := 1.0 if zz == zn else -1.0
				mb.box("u_stone", Vector3((xs + g[0]) * 0.5, 0.09, zz - sgn * 0.25), Vector3(g[0] - xs, 0.18, 0.6), Color(0.85, 0.83, 0.78))
		xs = maxf(xs, g[1])
	for e in bridges:
		_bridge(city, e, mb, det, out)
	# Kollision: Flussbett und Waende (Kaimauern verhindern das Hineinlaufen)
	out["shapes"].append({"xf": Transform3D(Basis(), Vector3(0, -CityModel.RIVER_DEPTH - 1.0, (zn + zs) * 0.5)), "size": Vector3(far * 2.0, 1.0, zs - zn)})


static func _bridge(city: CityModel, e: Dictionary, mb: MeshBatch, det: MeshBatch, out: Dictionary) -> void:
	var rv: Dictionary = city.river
	var zn: float = rv.z_north - 1.5
	var zs: float = rv.z_south + 1.5
	var x: float = city.nodes[e.a].pos.x
	var hw: float = e.width * 0.5
	var swl: float = e.sidewalk[0]
	var swr: float = e.sidewalk[1]
	# Kante a->b verlaeuft in +z (Norden -> Sueden): rechts = -x
	var x_right := x - hw - swr
	var x_left := x + hw + swl
	var L := zs - zn
	var zc := (zn + zs) * 0.5
	mb.flat_rect("g_asphalt", Rect2(x - hw, zn, hw * 2.0, L), 0.0)
	# Gehwege
	if swr > 0.0:
		mb.box("g_pavers", Vector3(x - hw - swr * 0.5, 0.075, zc), Vector3(swr, 0.15, L))
	if swl > 0.0:
		mb.box("g_pavers", Vector3(x + hw + swl * 0.5, 0.075, zc), Vector3(swl, 0.15, L))
	var deck_w := x_left - x_right
	# Tragwerk mit Boegen zwischen Pfeilern
	var col := Color(0.82, 0.80, 0.76)
	var spans := 3
	var span_len = (rv.z_south - rv.z_north) / spans
	mb.box("u_concrete", Vector3((x_left + x_right) * 0.5, -0.45, zc), Vector3(deck_w + 0.4, 0.9, L), col, MeshBatch.NO_CUSTOM, 1 | 2 | 16 | 32)
	for k in spans:
		var z0: float = rv.z_north + k * span_len
		var seg := 8
		for i in seg:
			var t0 := float(i) / seg
			var t1 := float(i + 1) / seg
			var y0 := -0.9 - 1.6 * (1.0 - sin(PI * t0)) - 0.4
			var y1 := -0.9 - 1.6 * (1.0 - sin(PI * t1)) - 0.4
			var za = z0 + span_len * t0
			var zb = z0 + span_len * t1
			for xx in [x_right - 0.2, x_left + 0.2]:
				var sgn := 1.0 if xx > x else -1.0
				var a := Vector2(xx, za if sgn < 0 else zb)
				var b := Vector2(xx, zb if sgn < 0 else za)
				var ya := y0 if sgn < 0 else y1
				var yb := y1 if sgn < 0 else y0
				var s := mb.surf("u_concrete")
				var nrm := Vector3(sgn, 0, 0)
				var p0 := Vector3(a.x, minf(ya, -0.9), a.y)
				var p1 := Vector3(b.x, minf(yb, -0.9), b.y)
				var p2 := Vector3(b.x, -0.9, b.y)
				var p3 := Vector3(a.x, -0.9, a.y)
				if p2.y - p1.y > 0.01 or p3.y - p0.y > 0.01:
					BuildingBuilder._quad_ccw_up(s, p0, p1, p2, p3, nrm, Vector3(0, 0, -sgn), Vector2(a.y, -ya), Vector2(b.y, -yb), Vector2(b.y, 0.9), Vector2(a.y, 0.9), col)
			# Unterseite der Boegen
			var ya2 := minf(y0, -0.9)
			var yb2 := minf(y1, -0.9)
			var q0 := Vector3(x_right - 0.2, ya2, za)
			var q1 := Vector3(x_left + 0.2, ya2, za)
			var q2 := Vector3(x_left + 0.2, yb2, zb)
			var q3 := Vector3(x_right - 0.2, yb2, zb)
			var nn := (q1 - q0).cross(q3 - q0).normalized()
			BuildingBuilder._quad_ccw_up(mb.surf("u_concrete"), q0, q1, q2, q3, -nn if nn.y > 0 else nn, Vector3.RIGHT, Vector2(0, 0), Vector2(1, 0), Vector2(1, 1), Vector2(0, 1), col.darkened(0.2))
		if k > 0:
			mb.box("u_stone", Vector3((x_left + x_right) * 0.5, -3.0, z0), Vector3(deck_w + 1.0, 4.4, 2.4), Color(0.78, 0.75, 0.7))
	# Gelaender
	for xx2 in [x_right + 0.15, x_left - 0.15]:
		var sgn2 := 1.0 if xx2 > x else -1.0
		var y := 0.15
		mb.box("u_stone", Vector3(xx2, y + 0.35, zc), Vector3(0.3, 0.7, L), Color(0.85, 0.83, 0.78))
		mb.box("paint_metal", Vector3(xx2, y + 1.08, zc), Vector3(0.08, 0.06, L), Color(0.25, 0.27, 0.3))
		var np := int(L / 2.5)
		for k2 in np + 1:
			det.box("paint_metal", Vector3(xx2, y + 0.9, zn + L * k2 / maxf(np, 1)), Vector3(0.05, 0.4, 0.05), Color(0.25, 0.27, 0.3))
		out["shapes"].append({"xf": Transform3D(Basis(), Vector3(xx2, 0.7, zc)), "size": Vector3(0.3, 1.4, L)})
	out["shapes"].append({"xf": Transform3D(Basis(), Vector3((x_left + x_right) * 0.5, -0.45, zc)), "size": Vector3(deck_w, 0.9, L)})
