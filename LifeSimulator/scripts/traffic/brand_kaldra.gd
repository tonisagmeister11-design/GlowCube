class_name BrandKaldra
extends RefCounted
## Die Fahrzeugmarke KALDRA: Emblem, Schriftzeichen und Modellnamen.
##
## KALDRA ist eine erfundene Marke dieses Spiels. Sie ersetzt bei den
## zugelieferten Fahrzeugmodellen die Zeichen der echten Hersteller - alle
## Marken im Spiel sind erfunden.
##
## Das Emblem ist bewusst schlicht gehalten: ein stehendes Sechseck mit
## dunkler Innenflaeche und einem kantigen K. Es muss aus zwei Metern genauso
## lesbar sein wie aus dreissig, und es darf kaum Dreiecke kosten, weil es an
## jedem Fahrzeug zweimal haengt.

const NAME := "KALDRA"

## Modellbezeichnungen der Marke, nach Fahrzeugkennung.
const MODEL_NAMES := {
	"kaldra_furio":   "KALDRA Furio",
	"kaldra_lucente": "KALDRA Lucente",
	"kaldra_saluto":  "KALDRA Saluto",
	"kaldra_vento":   "KALDRA Vento",
	"kaldra_trail":   "KALDRA Trail",
}

## Umschrift der alten, ueber mehrere Marken verstreuten Kennungen.
## Alte Spielstaende nennen noch diese Namen.
const RENAMED := {
	"vanari_furio":   "kaldra_furio",
	"vanari_lucente": "kaldra_lucente",
	"aurel_saluto":   "kaldra_saluto",
	"kastor_vento":   "kaldra_vento",
	"borek_trail":    "kaldra_trail",
}

## Das K in einem Feld von -0.5 bis 0.5, als drei Vielecke: senkrechter
## Balken, oberer und unterer Schenkel. Kantig, damit es auch bei grober
## Darstellung ein K bleibt.
const K_SHAPE := [
	[Vector2(-0.42, -0.50), Vector2(-0.42, 0.50), Vector2(-0.16, 0.50), Vector2(-0.16, -0.50)],
	[Vector2(-0.16, 0.02), Vector2(0.14, 0.50), Vector2(0.44, 0.50), Vector2(0.02, -0.04)],
	[Vector2(-0.16, -0.06), Vector2(0.02, 0.02), Vector2(0.46, -0.50), Vector2(0.16, -0.50)],
]


## Liegendes Sechseck (waagerechte Ober- und Unterkante), angegeben ueber die
## Hoehe. Liegend statt stehend, weil ein Markenzeichen am Kuehlergrill breiter
## als hoch sitzt - stehend wirkt es wie ein Wappen.
static func hexagon(height: float) -> PackedVector2Array:
	var r: float = height / sqrt(3.0)
	var pts := PackedVector2Array()
	for i in 6:
		var a := TAU * (float(i) / 6.0)
		pts.append(Vector2(cos(a) * r, sin(a) * r))
	return pts


## Das Emblem als Mesh. `height` ist die Hoehe in Metern (0.09 entspricht
## einem ueblichen Markenzeichen am Kuehlergrill). Das Zeichen liegt in der
## XY-Ebene und schaut nach +Z; der Aufrufer dreht es ans Fahrzeug.
##
## Oberflaeche 0 ist Chrom (Grundplatte und K), Oberflaeche 1 die dunkle
## Innenflaeche.
static func badge_mesh(height := 0.09) -> ArrayMesh:
	var chrome := SurfaceTool.new()
	chrome.begin(Mesh.PRIMITIVE_TRIANGLES)
	var dark := SurfaceTool.new()
	dark.begin(Mesh.PRIMITIVE_TRIANGLES)

	# Grundplatte aus Chrom
	_prism(chrome, hexagon(height), height * 0.08, 0.0)
	# Dunkle Innenflaeche, vorgesetzt. 0.68 statt knapp darunter, damit der
	# Chromrand als Rand erkennbar bleibt und nicht zur Haarlinie wird.
	_prism(dark, hexagon(height * 0.68), height * 0.05, height * 0.08)
	# K darueber, deutlich erhaben - so faengt es Licht von der Seite
	var k_size: float = height * 0.52
	for poly in K_SHAPE:
		var scaled := PackedVector2Array()
		for p in poly:
			scaled.append(p * k_size)
		_prism(chrome, scaled, height * 0.07, height * 0.12)

	var mesh := ArrayMesh.new()
	chrome.index()
	dark.index()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, chrome.commit_to_arrays())
	mesh.surface_set_name(0, "badge_chrome")
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, dark.commit_to_arrays())
	mesh.surface_set_name(1, "badge_dark")
	return mesh


## Fertiger Knoten mit Materialien, bereit zum Anhaengen.
static func badge_node(height := 0.09) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	mi.mesh = badge_mesh(height)
	mi.set_surface_override_material(0, _chrome())
	mi.set_surface_override_material(1, _dark())
	return mi


static func _chrome() -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.86, 0.87, 0.90)
	m.metallic = 0.95
	m.roughness = 0.14
	return m


static func _dark() -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.035, 0.037, 0.042)
	m.metallic = 0.3
	m.roughness = 0.42
	return m


## Zieht ein Vieleck zu einem flachen Koerper: Vorderflaeche, Rueckflaeche und
## Seitenwaende. Gefaechert wird vom Schwerpunkt aus, das genuegt fuer die
## einfachen Formen hier.
##
## Godot sieht Dreiecke im Uhrzeigersinn als Vorderseite. Welche Richtung ein
## Vieleck hat, weiss der Aufrufer meist nicht, deshalb wird sie hier aus der
## vorzeichenbehafteten Flaeche bestimmt und notfalls umgedreht - sonst
## verschwindet die Flaeche, weil sie weggeschnitten wird.
static func _prism(st: SurfaceTool, p_poly: PackedVector2Array, depth: float, z0: float) -> void:
	var n := p_poly.size()
	if n < 3:
		return
	var poly := p_poly
	var area := 0.0
	for i in n:
		var a := poly[i]
		var b := poly[(i + 1) % n]
		area += a.x * b.y - b.x * a.y
	if area > 0.0:
		var rev := PackedVector2Array()
		for i in range(n - 1, -1, -1):
			rev.append(poly[i])
		poly = rev
	var mid := Vector2.ZERO
	for p in poly:
		mid += p
	mid /= float(n)
	var z1: float = z0 + depth

	# Vorderflaeche (schaut nach +Z)
	for i in n:
		var a := poly[i]
		var b := poly[(i + 1) % n]
		_tri(st, Vector3(mid.x, mid.y, z1), Vector3(a.x, a.y, z1), Vector3(b.x, b.y, z1), Vector3.BACK)
	# Rueckflaeche (schaut nach -Z, umgekehrte Reihenfolge)
	for i in n:
		var a := poly[i]
		var b := poly[(i + 1) % n]
		_tri(st, Vector3(mid.x, mid.y, z0), Vector3(b.x, b.y, z0), Vector3(a.x, a.y, z0), Vector3.FORWARD)
	# Seitenwaende
	for i in n:
		var a := poly[i]
		var b := poly[(i + 1) % n]
		var edge := (b - a).normalized()
		var nrm := Vector3(edge.y, -edge.x, 0.0)
		# nach aussen zeigen lassen
		if nrm.dot(Vector3(a.x - mid.x, a.y - mid.y, 0.0)) < 0.0:
			nrm = -nrm
		_tri(st, Vector3(a.x, a.y, z0), Vector3(b.x, b.y, z0), Vector3(b.x, b.y, z1), nrm)
		_tri(st, Vector3(a.x, a.y, z0), Vector3(b.x, b.y, z1), Vector3(a.x, a.y, z1), nrm)


static func _tri(st: SurfaceTool, a: Vector3, b: Vector3, c: Vector3, nrm: Vector3) -> void:
	for v in [a, b, c]:
		st.set_normal(nrm)
		st.set_uv(Vector2(v.x, v.y))
		st.add_vertex(v)


## Das Zeichen als Bild, fuer Texturflaechen (Kuehlergrill, Kennzeichen).
## Heller Chromton auf durchsichtigem Grund.
static func badge_image(px := 256) -> Image:
	var img := Image.create(px, px, false, Image.FORMAT_RGBA8)
	img.fill(Color(0, 0, 0, 0))
	var c := float(px) * 0.5
	var h: float = float(px) * 0.94
	var hex := hexagon(h)
	var hex_in := hexagon(h * 0.68)
	var k_size: float = h * 0.52
	for y in px:
		for x in px:
			var p := Vector2(float(x) - c, c - float(y))
			if not _inside(hex, p):
				continue
			var col := Color(0.86, 0.87, 0.90, 1.0)
			if _inside(hex_in, p):
				col = Color(0.05, 0.05, 0.06, 1.0)
				for poly in K_SHAPE:
					var scaled := PackedVector2Array()
					for q in poly:
						scaled.append(q * k_size)
					if _inside(scaled, p):
						col = Color(0.90, 0.91, 0.94, 1.0)
						break
			img.set_pixel(x, y, col)
	return img


static func badge_texture(px := 256) -> ImageTexture:
	return ImageTexture.create_from_image(badge_image(px))


## Punkt-im-Vieleck (Strahlenverfahren). Nur fuer die Bilderzeugung.
static func _inside(poly: PackedVector2Array, p: Vector2) -> bool:
	var inside := false
	var n := poly.size()
	var j := n - 1
	for i in n:
		var a := poly[i]
		var b := poly[j]
		if (a.y > p.y) != (b.y > p.y):
			var t: float = (p.y - a.y) / (b.y - a.y)
			if p.x < a.x + t * (b.x - a.x):
				inside = not inside
		j = i
	return inside
