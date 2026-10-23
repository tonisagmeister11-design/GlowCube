class_name CityMapRenderer
extends RefCounted
## Zeichnet eine Draufsicht der Stadt in ein Image (Stadtkarte, Debug-Ansicht).
## px_per_m: Pixel pro Meter. Mit debug = true werden Validierungsprobleme
## als rote Markierungen eingezeichnet.

const COL_BG := Color(0.80, 0.84, 0.78)
const COL_ROAD := Color(0.30, 0.32, 0.36)
const COL_ROAD_MAJOR := Color(0.36, 0.36, 0.40)
const COL_SIDEWALK := Color(0.78, 0.77, 0.74)
const COL_WATER := Color(0.33, 0.55, 0.78)
const COL_PARK := Color(0.55, 0.74, 0.47)
const COL_LOT := Color(0.86, 0.87, 0.82)
const COL_BUILDING := Color(0.62, 0.60, 0.58)

const ZONE_TINT := {
	"downtown": Color(0.55, 0.58, 0.68), "business": Color(0.56, 0.62, 0.74), "commercial": Color(0.80, 0.62, 0.52),
	"oldtown": Color(0.78, 0.58, 0.46), "apartments": Color(0.84, 0.74, 0.58), "highrise": Color(0.70, 0.66, 0.70),
	"residential": Color(0.88, 0.78, 0.62), "suburb": Color(0.90, 0.84, 0.66), "rich": Color(0.92, 0.88, 0.76),
	"estates": Color(0.94, 0.90, 0.80), "poor": Color(0.66, 0.58, 0.54), "slum": Color(0.56, 0.50, 0.48),
	"industrial": Color(0.60, 0.60, 0.56), "university": Color(0.70, 0.62, 0.78), "civic": Color(0.80, 0.80, 0.86),
	"park": Color(0.55, 0.74, 0.47),
}


static func render(m: CityModel, px_per_m := 0.5, debug := false) -> Image:
	var size := int(ceil((m.size + 120.0) * px_per_m))
	var img := Image.create(size, size, false, Image.FORMAT_RGBA8)
	img.fill(COL_BG)
	var off := Vector2(m.size * 0.5 + 60.0, m.size * 0.5 + 60.0)
	var tr := func(r: Rect2) -> Rect2i:
		var p := (r.position + off) * px_per_m
		var s := r.size * px_per_m
		return Rect2i(Vector2i(floori(p.x), floori(p.y)), Vector2i(maxi(1, ceili(s.x)), maxi(1, ceili(s.y))))
	# Fluss
	var river := Rect2(Vector2(-m.size * 0.5 - 60.0, m.river.z_north), Vector2(m.size + 120.0, m.river.width))
	img.fill_rect(tr.call(river), COL_WATER)
	# Strassen
	for e in m.edges:
		var col := COL_ROAD_MAJOR if e.cls in ["arterial", "ring"] else COL_ROAD
		img.fill_rect(tr.call(m.edge_road_rect(e)), col)
	# Bloecke: Gehweg + Inneres
	for b in m.blocks:
		img.fill_rect(tr.call(b.curb), COL_SIDEWALK)
		var inner_col: Color = COL_PARK if b.kind in ["park", "promenade"] else COL_LOT
		if b.kind == "promenade":
			inner_col = Color(0.74, 0.72, 0.66)
			img.fill_rect(tr.call(b.curb), inner_col)
		else:
			img.fill_rect(tr.call(b.inner), inner_col)
	for pk in m.parks:
		if pk.promenade:
			continue
		for path in pk.paths:
			img.fill_rect(tr.call(path), Color(0.82, 0.78, 0.66))
	for l in m.lots:
		if l.usage == "green":
			img.fill_rect(tr.call(l.rect.grow(-0.5)), COL_PARK.darkened(0.05))
	# Parkplaetze, Zufahrten
	for pa in m.parking_areas:
		img.fill_rect(tr.call(pa.rect), Color(0.48, 0.48, 0.50))
	for b in m.buildings:
		if b.driveway.has_area():
			img.fill_rect(tr.call(b.driveway), Color(0.55, 0.55, 0.56))
		if b.walkway.has_area():
			img.fill_rect(tr.call(b.walkway), Color(0.75, 0.72, 0.68))
	# Gebaeude
	for b in m.buildings:
		var col2: Color = ZONE_TINT.get(b.zone, COL_BUILDING)
		if b.business_type != "":
			col2 = col2.lerp(b.brand_color, 0.45)
		if b.kind in CityCatalog.CIVIC_KINDS:
			col2 = Color(0.45, 0.52, 0.72)
		if b.open_air:
			col2 = col2.lightened(0.3)
		img.fill_rect(tr.call(b.footprint), col2.darkened(0.35))
		img.fill_rect(tr.call(b.footprint.grow(-1.0)), col2)
	# Zebrastreifen, Haltestellen, Baeume
	if px_per_m >= 0.4:
		for p in m.props:
			if p.kind in ["tree", "street_tree"]:
				_dot(img, (p.pos + off) * px_per_m, maxf(1.0, p.r * px_per_m * 0.8), Color(0.25, 0.5, 0.25))
	for s in m.bus_stops:
		_dot(img, (s.pos + off) * px_per_m, maxf(2.0, 2.5 * px_per_m), Color(0.1, 0.35, 0.9))
	for n in m.nodes:
		if n.kind == "signal":
			_dot(img, (n.pos + off) * px_per_m, maxf(1.5, 2.0 * px_per_m), Color(0.95, 0.75, 0.1))
		elif n.kind == "roundabout":
			_ring(img, (n.pos + off) * px_per_m, n.roundabout.r_outer * px_per_m, n.roundabout.r_inner * px_per_m, COL_ROAD, COL_PARK)
	if debug:
		for i in m.issues:
			var c := Color(0.9, 0.1, 0.1) if i.severity == "error" else Color(1.0, 0.6, 0.0)
			_dot(img, (i.pos + off) * px_per_m, maxf(3.0, 4.0 * px_per_m), c)
	return img


## Weltposition -> Bildpixel (fuer UI-Karten mit gleicher Skalierung).
static func world_to_px(m: CityModel, p: Vector2, px_per_m: float) -> Vector2:
	return (p + Vector2(m.size * 0.5 + 60.0, m.size * 0.5 + 60.0)) * px_per_m


static func px_to_world(m: CityModel, px: Vector2, px_per_m: float) -> Vector2:
	return px / px_per_m - Vector2(m.size * 0.5 + 60.0, m.size * 0.5 + 60.0)


static func _dot(img: Image, c: Vector2, r: float, col: Color) -> void:
	var r2 := r * r
	for x in range(int(c.x - r), int(c.x + r) + 1):
		for y in range(int(c.y - r), int(c.y + r) + 1):
			if x < 0 or y < 0 or x >= img.get_width() or y >= img.get_height():
				continue
			if Vector2(x, y).distance_squared_to(c) <= r2:
				img.set_pixel(x, y, col)


static func _ring(img: Image, c: Vector2, ro: float, ri: float, col: Color, inner: Color) -> void:
	for x in range(int(c.x - ro), int(c.x + ro) + 1):
		for y in range(int(c.y - ro), int(c.y + ro) + 1):
			if x < 0 or y < 0 or x >= img.get_width() or y >= img.get_height():
				continue
			var d := Vector2(x, y).distance_to(c)
			if d <= ri:
				img.set_pixel(x, y, inner)
			elif d <= ro:
				img.set_pixel(x, y, col)
