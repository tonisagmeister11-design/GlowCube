class_name PedPaths
extends RefCounted
## Fussgaengerrouten auf Basis von CityModel.ped_route(), verfeinert fuer die
## Darstellung: Strassenquerungen laufen exakt ueber die Zebrastreifen
## (city.crosswalks), Kreisverkehre werden aussen umgangen.
##
## route(city, from, to) -> {points: PackedVector2Array, crossings: Array[int]}
## crossings[k] beschreibt das Teilstueck points[k] -> points[k + 1]:
##   -1 = Gehweg, >= 0 = Index des Zebrastreifens/Ampelfurt in city.crosswalks,
##   -2 = ungeregelte Querung (Knoten ohne Zebrastreifen).

const CROSS_MARGIN := 0.9   # Wartepunkt hinter dem Bordstein

static var _cw_cache: Dictionary = {}   # Instanz-ID der Stadt -> {node*8192+edge: index}


static func _cw_map(m: CityModel) -> Dictionary:
	var key := m.get_instance_id()
	if not _cw_cache.has(key):
		var d := {}
		for i in m.crosswalks.size():
			var cw: Dictionary = m.crosswalks[i]
			d[int(cw.node) * 8192 + int(cw.edge)] = i
		_cw_cache[key] = d
	return _cw_cache[key]


static func route(m: CityModel, from: Vector2, to: Vector2) -> Dictionary:
	var raw := m.ped_route(from, to)
	var pts := PackedVector2Array()
	var flags: Array = []
	if raw.is_empty():
		return {"points": pts, "crossings": flags}
	pts.append(raw[0])
	for i in range(raw.size() - 1):
		var a: Vector2 = pts[pts.size() - 1]
		var b: Vector2 = raw[i + 1]
		if a.distance_to(b) < 0.05:
			continue
		var mid := (a + b) * 0.5
		if a.distance_to(b) > 2.0 and not sidewalk_at(m, mid):
			var cw := _find_crosswalk(m, mid)
			if cw >= 0:
				var ends := crosswalk_ends(m, cw)
				var e1: Vector2 = ends[0]
				var e2: Vector2 = ends[1]
				if e1.distance_to(a) > e2.distance_to(a):
					var tmp := e1
					e1 = e2
					e2 = tmp
				pts.append(e1)
				flags.append(-1)
				pts.append(e2)
				flags.append(cw)
				pts.append(b)
				flags.append(-1)
				continue
			pts.append(b)
			flags.append(-2)
			continue
		pts.append(b)
		flags.append(-1)
	_fix_roundabouts(m, pts, flags)
	return {"points": pts, "crossings": flags}


## Endpunkte einer Querung (je CROSS_MARGIN hinter den Bordsteinen).
static func crosswalk_ends(m: CityModel, cw: int) -> Array:
	var c: Dictionary = m.crosswalks[cw]
	var e: Dictionary = m.edges[c.edge]
	var r := m.edge_right(e)
	var half: float = float(e.width) * 0.5 + CROSS_MARGIN
	return [c.center + r * half, c.center - r * half]


static func _find_crosswalk(m: CityModel, mid: Vector2) -> int:
	var nid := m.nearest_node(mid)
	var n: Dictionary = m.nodes[nid]
	if mid.distance_to(n.pos) > 60.0:
		return -1
	var map := _cw_map(m)
	for eid in n.edges:
		var e: Dictionary = m.edges[eid]
		var d: Vector2 = (m.nodes[m.edge_other(e, nid)].pos - n.pos).normalized()
		var rel: Vector2 = mid - n.pos
		var along := rel.dot(d)
		var latd := absf(rel.x * d.y - rel.y * d.x)
		if along > 0.0 and latd < float(e.width) * 0.5 + 2.0:
			return map.get(nid * 8192 + int(eid), -1)
	return -1


## Punkte innerhalb eines Kreisverkehrs durch einen Bogen aussen herum ersetzen.
static func _fix_roundabouts(m: CityModel, pts: PackedVector2Array, flags: Array) -> void:
	var k := 1
	while k < pts.size() - 1:
		var p := pts[k]
		var nid := m.nearest_node(p)
		var n: Dictionary = m.nodes[nid]
		if n.kind != "roundabout" or p.distance_to(n.pos) > float(n.roundabout.r_outer) + 0.3:
			k += 1
			continue
		var c: Vector2 = n.pos
		var R: float = float(n.roundabout.r_outer) + 1.0
		var A = _circle_hit(pts[k - 1], p, c, R)
		var B = _circle_hit(pts[k + 1], p, c, R)
		if A == null or B == null:
			k += 1
			continue
		var a0 := atan2(A.y - c.y, A.x - c.x)
		var a1 := atan2(B.y - c.y, B.x - c.x)
		var da := wrapf(a1 - a0, -PI, PI)
		var arc := PackedVector2Array([A])
		var steps := maxi(1, int(absf(da) * R / 1.5))
		for j in range(1, steps):
			var t := a0 + da * float(j) / steps
			arc.append(c + Vector2(cos(t), sin(t)) * R)
		arc.append(B)
		pts.remove_at(k)
		var fk = flags[k]
		flags.remove_at(k)
		for j in arc.size():
			pts.insert(k + j, arc[j])
		# Teilstuecke: vor A wie bisher (flags[k-1]), Bogen -1, nach B = fk
		for j in arc.size() - 1:
			flags.insert(k + j, -1)
		flags.insert(k + arc.size() - 1, fk)
		k += arc.size()


## Schnittpunkt der Strecke outside -> inside mit dem Kreis (c, R).
static func _circle_hit(outside: Vector2, inside: Vector2, c: Vector2, R: float):
	if outside.distance_to(c) < R:
		return null
	var d := inside - outside
	var f := outside - c
	var a := d.dot(d)
	var b := 2.0 * f.dot(d)
	var cc := f.dot(f) - R * R
	var disc := b * b - 4.0 * a * cc
	if disc < 0.0 or a < 1e-9:
		return null
	var t := (-b - sqrt(disc)) / (2.0 * a)
	if t < 0.0 or t > 1.0:
		return null
	return outside + d * t


## Liegt p auf einem Gehweg (Blockrand, Promenade, Brueckengehweg)?
static func sidewalk_at(m: CityModel, p: Vector2) -> bool:
	var nid := m.nearest_node(p)
	var n: Dictionary = m.nodes[nid]
	if n.kind == "roundabout" and p.distance_to(n.pos) < float(n.roundabout.r_outer):
		return false
	var bid := m.block_at_point(p)
	if bid >= 0:
		var b: Dictionary = m.blocks[bid]
		if b.kind == "promenade":
			return true
		if not b.inner.has_point(p):
			return true
		return b.kind == "park"
	# Gehwege entlang von Kanten (z.B. Bruecken)
	var k := m.chunk_key(p)
	for dx in [-1, 0, 1]:
		for dz in [-1, 0, 1]:
			var c = m.chunks.get(k + Vector2i(dx, dz))
			if c == null:
				continue
			for eid in c.edges:
				var e: Dictionary = m.edges[eid]
				var pa: Vector2 = m.nodes[e.a].pos
				var d := m.edge_dir(e)
				var rel := p - pa
				var along := rel.dot(d)
				if along < 0.0 or along > e.length:
					continue
				var latv := rel.dot(CityModel.right_of(d))
				var hw: float = float(e.width) * 0.5
				var sw: float = e.sidewalk[1] if latv > 0.0 else e.sidewalk[0]
				if sw > 0.0 and absf(latv) >= hw - 0.05 and absf(latv) <= hw + sw + 0.05:
					return true
	return false


## Darf ein Fussgaenger an p stehen (Gehweg oder die angegebene Querung)?
static func is_walkable(m: CityModel, p: Vector2, crossing := -1) -> bool:
	if crossing >= 0:
		var c: Dictionary = m.crosswalks[crossing]
		var e: Dictionary = m.edges[c.edge]
		var d := m.edge_dir(e)
		var rel: Vector2 = p - c.center
		if absf(rel.dot(d)) <= float(c.width) * 0.5 + 0.6 and absf(rel.dot(CityModel.right_of(d))) <= float(c.length) * 0.5 + CROSS_MARGIN + 0.6:
			return true
	elif crossing == -2:
		return true
	return sidewalk_at(m, p)


## Zebrastreifen in der Naehe eines Punktes (fuer Wartende), sonst -1.
static func crosswalk_near(m: CityModel, p: Vector2, radius := 6.0) -> int:
	var nid := m.nearest_node(p)
	var map := _cw_map(m)
	for eid in m.nodes[nid].edges:
		var i: int = map.get(nid * 8192 + int(eid), -1)
		if i >= 0 and m.crosswalks[i].center.distance_to(p) < radius:
			return i
	return -1
