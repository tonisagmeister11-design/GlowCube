class_name HumanoidBuilder
extends RefCounted
## Erzeugt Skelett und gehauteten Koerper einer Figur vollstaendig prozedural
## (ohne externe Assets).
##
## Aufbau: Ein Skelett mit 24 Knochen (Ruheposen ohne Rotation, Arme haengen
## seitlich, Blick nach +Z). Der Koerper besteht aus geloften Querschnitten
## (Rumpf, Arme, Beine, Fuesse, Haende), einem eigens geformten Kopf mit
## Augaepfeln, Lidern, Nase, Lippen, Ohren und Brauen sowie Kleidung,
## Haaren und Accessoires als versetzte Schichten mit identischer
## Gewichtung. Verdeckte Haut wird nicht erzeugt (kein Durchstechen,
## weniger Vertices).
##
## Ergebnis wird nach Eingabe zwischengespeichert, damit gleiche Figuren
## (z. B. derselbe NPC nach erneutem Erscheinen) keine Neuberechnung brauchen.

enum {
	B_ROOT, B_HIPS, B_SPINE, B_CHEST, B_NECK, B_HEAD, B_JAW, B_EYE_L, B_EYE_R, B_LIDS,
	B_SHOULDER_L, B_UPPERARM_L, B_FOREARM_L, B_HAND_L,
	B_SHOULDER_R, B_UPPERARM_R, B_FOREARM_R, B_HAND_R,
	B_THIGH_L, B_SHIN_L, B_FOOT_L, B_THIGH_R, B_SHIN_R, B_FOOT_R,
	BONE_COUNT
}

const BONE_NAMES := ["root", "hips", "spine", "chest", "neck", "head", "jaw", "eye_l", "eye_r", "lids",
	"shoulder_l", "upperarm_l", "forearm_l", "hand_l", "shoulder_r", "upperarm_r", "forearm_r", "hand_r",
	"thigh_l", "shin_l", "foot_l", "thigh_r", "shin_r", "foot_r"]
const BONE_PARENTS := [-1, 0, 1, 2, 3, 4, 5, 5, 5, 5, 3, 10, 11, 12, 3, 14, 15, 16, 1, 18, 19, 1, 21, 22]

const SOLE := {"sneakers": 0.026, "boots": 0.032, "dress_shoes": 0.022, "sandals": 0.016,
	"flats": 0.010, "booties": 0.012, "barefoot": 0.0}

const CACHE_MAX := 160
static var _cache := {}
static var _cache_order: Array = []

# ------------------------------------------------------------------ Zustand
var app: Dictionary
var p: BodyProportions
var lod := 0
var H := 1.7               ## jugendliche Groesse (Bezug fuer Anteile)
var sole := 0.0
var rest_global: Array = []   ## Transform3D je Knochen (Modellraum)
var s_skin: Surf
var s_cloth: Surf
var s_hair: Surf
var s_eye: Surf

# Kleidung (aufgeloest)
var top_key := "tshirt"
var bottom_key := "jeans"
var shoe_key := "sneakers"
var hair_key := "short"
var beard_key := "none"
var acc: Array = []
var top_col := Color.WHITE
var bottom_col := Color.BLUE
var shoe_col := Color.WHITE
var acc_col := Color.GRAY
var skin_col := Color.BEIGE
var hair_col := Color.BROWN
var brow_col := Color.BROWN
var headwear := ""

# Kopf (Kopfeinheiten)
var hh := 0.23
var head_c := Vector3.ZERO
var e := 0.0             ## Augenhoehe
var lf := 0.5            ## Gesichtsunterlaenge (Auge bis Kinn)
var ex := 0.137          ## Augenabstand zur Mitte
var nose_b := -0.25
var mouth_y := -0.333
var hw_scale := 1.0      ## Breitenfaktor Kopf
var hd_scale := 1.0      ## Tiefenfaktor Kopf
var head_keys: Array = []
var eye_z := 0.4
var face_jaw := 0.5
var face_nose := 0.5
var face_brows := 0.5
var face_cheeks := 0.5

# Rumpf
var torso_keys: Array = []
var top_hem_y := 0.0
var top_neck_y := 0.0
var bottom_top_y := 0.0


# ================================================================== Oberflaechen

class Surf:
	var v := PackedVector3Array()
	var col := PackedColorArray()
	var uv := PackedVector2Array()
	var uv2 := PackedVector2Array()
	var bones := PackedInt32Array()
	var weights := PackedFloat32Array()
	var idx := PackedInt32Array()
	var param := 0.0

	func add(pt: Vector3, c: Color, bw: Array) -> int:
		var i := v.size()
		v.append(pt)
		col.append(c)
		uv.append(Vector2(pt.x, pt.y))
		uv2.append(Vector2(pt.z, param))
		_bw(bw)
		return i

	func add_uv(pt: Vector3, c: Color, bw: Array, u: Vector2, u2: Vector2) -> int:
		var i := v.size()
		v.append(pt)
		col.append(c)
		uv.append(u)
		uv2.append(u2)
		_bw(bw)
		return i

	func _bw(bw: Array) -> void:
		var n: int = bw.size() / 2
		var tot := 0.0
		for k in n:
			tot += float(bw[k * 2 + 1])
		if tot <= 0.0:
			tot = 1.0
		for k in 4:
			if k < n:
				bones.append(int(bw[k * 2]))
				weights.append(float(bw[k * 2 + 1]) / tot)
			else:
				bones.append(0)
				weights.append(0.0)

	## Dreieck, im Uhrzeigersinn von aussen gesehen (Godot-Vorderseite).
	func tri(a: int, b: int, c: int) -> void:
		idx.append(a)
		idx.append(b)
		idx.append(c)

	func empty() -> bool:
		return idx.is_empty()

	func arrays(skinned: bool) -> Array:
		var arr := []
		arr.resize(Mesh.ARRAY_MAX)
		arr[Mesh.ARRAY_VERTEX] = v
		arr[Mesh.ARRAY_NORMAL] = _normals()
		arr[Mesh.ARRAY_COLOR] = col
		arr[Mesh.ARRAY_TEX_UV] = uv
		arr[Mesh.ARRAY_TEX_UV2] = uv2
		if skinned:
			arr[Mesh.ARRAY_BONES] = bones
			arr[Mesh.ARRAY_WEIGHTS] = weights
		arr[Mesh.ARRAY_INDEX] = idx
		return arr

	func _normals() -> PackedVector3Array:
		var n := PackedVector3Array()
		n.resize(v.size())
		var i := 0
		while i < idx.size():
			var a := idx[i]
			var b := idx[i + 1]
			var c := idx[i + 2]
			var fn := (v[c] - v[a]).cross(v[b] - v[a])
			n[a] += fn
			n[b] += fn
			n[c] += fn
			i += 3
		for k in n.size():
			var l := n[k].length()
			n[k] = n[k] / l if l > 1e-12 else Vector3.UP
		return n


# ================================================================== Oeffentliche API

## Baut eine Figur. Rueckgabe:
## {mesh: ArrayMesh, skin: Skin (null bei lod 2), rest: Array[Transform3D] (lokal),
##  global_rest: Array[Transform3D], proportions: BodyProportions, sole: float,
##  height: float, lod: int}
static func build(appearance: Dictionary, age: float, body: String, level := 0) -> Dictionary:
	var a := Appearance.validate(appearance, age)
	var key := _cache_key(a, age, body, level)
	if _cache.has(key):
		_cache_order.erase(key)
		_cache_order.append(key)
		return _cache[key]
	var b := HumanoidBuilder.new()
	var res := b._build(a, age, body, clampi(level, 0, 2))
	_cache[key] = res
	_cache_order.append(key)
	while _cache_order.size() > CACHE_MAX:
		_cache.erase(_cache_order.pop_front())
	return res


static func clear_cache() -> void:
	_cache.clear()
	_cache_order.clear()


static func _cache_key(a: Dictionary, age: float, body: String, level: int) -> String:
	# Alter auf Monate (Kinder) bzw. halbe Jahre gerundet: sichtbar identisch
	var q := snappedf(age, 1.0 / 12.0) if age < 18.0 else snappedf(age, 0.5)
	return "%s|%s|%d|%s" % [body, str(q), level, var_to_str(a).hash()]


# ================================================================== Aufbau

func _build(a: Dictionary, age: float, body: String, level: int) -> Dictionary:
	app = a
	lod = level
	p = BodyProportions.compute(age, body, a.build)
	H = p.height / (1.0 - 0.0006 * maxf(0.0, age - 45.0) - 0.0010 * maxf(0.0, age - 70.0))
	_resolve_clothing()
	sole = float(SOLE.get(shoe_key, 0.0)) * clampf(p.height / 1.7, 0.45, 1.1)
	_setup_bones()
	s_skin = Surf.new()
	s_cloth = Surf.new()
	s_hair = Surf.new()
	s_eye = Surf.new()
	s_skin.param = HumanoidMaterials.skin_param(p.skin_age, float(app.freckles))
	s_hair.param = p.grey
	_setup_head()
	_setup_torso()
	if lod == 2:
		_build_lod2()
	else:
		_build_torso()
		_build_skirt()
		_build_arms()
		_build_legs()
		_build_feet()
		_build_hands()
		_build_head()
		_build_face()
		_build_hair()
		_build_beard()
		_build_clothing_extras()
		_build_accessories()
	var mesh := ArrayMesh.new()
	var skinned := lod < 2
	var mats := [[s_skin, HumanoidMaterials.skin()], [s_cloth, HumanoidMaterials.cloth()],
		[s_hair, HumanoidMaterials.hair()], [s_eye, HumanoidMaterials.eye()]]
	for m in mats:
		var s: Surf = m[0]
		if s.empty():
			continue
		mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, s.arrays(skinned))
		mesh.surface_set_material(mesh.get_surface_count() - 1, m[1])
	var local_rest: Array = []
	for i in BONE_COUNT:
		var par: int = BONE_PARENTS[i]
		if par < 0:
			local_rest.append(rest_global[i])
		else:
			local_rest.append(Transform3D(Basis(), rest_global[i].origin - rest_global[par].origin))
	var skin: Skin = null
	if skinned:
		skin = Skin.new()
		for i in BONE_COUNT:
			skin.add_bind(i, rest_global[i].affine_inverse())
	return {"mesh": mesh, "skin": skin, "rest": local_rest, "global_rest": rest_global.duplicate(),
		"proportions": p, "sole": sole, "height": p.height + sole, "lod": lod,
		"top": top_key, "bottom": bottom_key, "hair": hair_key}


func _resolve_clothing() -> void:
	top_key = Appearance.key_of(Appearance.TOPS, app.top.style)
	bottom_key = Appearance.key_of(Appearance.BOTTOMS, app.bottom.style)
	shoe_key = Appearance.key_of(Appearance.SHOES, app.shoes.style)
	hair_key = Appearance.key_of(Appearance.HAIR_STYLES, app.hair_style)
	beard_key = Appearance.key_of(Appearance.BEARDS, app.beard)
	acc = app.accessories.duplicate()
	top_col = app.top.color
	bottom_col = app.bottom.color
	shoe_col = app.shoes.color
	acc_col = app.accessory_color
	skin_col = app.skin
	skin_col.a = 0.12
	hair_col = app.hair_color
	if top_key == "onesie":
		bottom_key = "onesie"
		bottom_col = top_col
	if top_key == "dress":
		bottom_key = "dress"
		bottom_col = top_col
	for h in ["cap", "hat", "beanie"]:
		if acc.has(h):
			headwear = h
	# Augenbrauen: dunkler als Haare, ergrauen langsamer
	var bc := hair_col
	if hair_key == "bald" or hair_key == "receding":
		bc = hair_col
	brow_col = bc.darkened(0.25)
	if hair_col.get_luminance() > 0.6:
		brow_col = bc.darkened(0.45)
	brow_col = brow_col.lerp(Color(0.7, 0.7, 0.7), p.grey * 0.55)
	if p.age < 2.0:
		brow_col = brow_col.lerp(skin_col.darkened(0.15), 0.6)


func _setup_bones() -> void:
	rest_global.clear()
	rest_global.resize(BONE_COUNT)
	var y0 := sole
	var g := func(x: float, y: float, z: float) -> Transform3D:
		return Transform3D(Basis(), Vector3(x, y + y0, z))
	rest_global[B_ROOT] = Transform3D()
	rest_global[B_HIPS] = g.call(0.0, p.hip_y + 0.02 * H, 0.0)
	rest_global[B_SPINE] = g.call(0.0, p.waist_y, -0.012 * H)
	rest_global[B_CHEST] = g.call(0.0, p.bust_y, -0.014 * H)
	rest_global[B_NECK] = g.call(0.0, p.shoulder_y + 0.012 * H, -0.016 * H)
	hh = p.head_h
	hw_scale = p.head_w / hh / 0.68
	hd_scale = p.head_d / hh / 0.86
	head_c = Vector3(0.0, p.chin_y + 0.5 * hh + y0, 0.035 * hh)
	rest_global[B_HEAD] = Transform3D(Basis(), head_c + Vector3(0.0, -0.22, -0.1) * hh)
	e = p.eye_y
	lf = e + 0.5
	ex = 0.137 * hw_scale * lerpf(1.06, 1.0, p.maturity)
	nose_b = e - 0.5 * lf
	mouth_y = e - 0.665 * lf
	rest_global[B_JAW] = Transform3D(Basis(), head_c + Vector3(0.0, e - 0.16, -0.06) * hh)
	# Augenlage wird in _setup_head bestimmt; vorlaeufig
	rest_global[B_EYE_L] = Transform3D(Basis(), head_c + Vector3(ex, e, 0.3) * hh)
	rest_global[B_EYE_R] = Transform3D(Basis(), head_c + Vector3(-ex, e, 0.3) * hh)
	rest_global[B_LIDS] = Transform3D(Basis(), head_c + Vector3(0.0, e, 0.3) * hh)
	var sx := p.shoulder_w * 0.5
	for side in [1, -1]:
		var o := 0 if side == 1 else 4
		rest_global[B_SHOULDER_L + o] = g.call(side * p.neck_r * 0.6, p.shoulder_y - 0.008 * H, -0.004 * H)
		rest_global[B_UPPERARM_L + o] = g.call(side * sx, p.shoulder_y, -0.008 * H)
		rest_global[B_FOREARM_L + o] = g.call(side * sx, p.shoulder_y - p.upper_arm_len, -0.008 * H)
		rest_global[B_HAND_L + o] = g.call(side * sx, p.shoulder_y - p.upper_arm_len - p.forearm_len, -0.008 * H)
		var o2 := 0 if side == 1 else 3
		var lx := p.hip_sep * 0.5
		rest_global[B_THIGH_L + o2] = g.call(side * lx, p.hip_y, 0.0)
		rest_global[B_SHIN_L + o2] = g.call(side * lx * 0.94, p.knee_y, 0.0)
		rest_global[B_FOOT_L + o2] = g.call(side * lx * 0.92, p.ankle_y, 0.0)


# ================================================================== Hilfen

static func _sstep(a: float, b: float, x: float) -> float:
	if a == b:
		return 1.0 if x >= b else 0.0
	var t := clampf((x - a) / (b - a), 0.0, 1.0)
	return t * t * (3.0 - 2.0 * t)


static func _g2(dx: float, dy: float) -> float:
	return exp(-(dx * dx + dy * dy))


static func _spow(v: float, e2: float) -> float:
	return signf(v) * pow(absf(v), e2)


## Catmull-Rom-Interpolation einer Schluesseltabelle [[y, a, b, c, ...], ...]
## (aufsteigend nach y). Liefert die interpolierten Werte ab Index 1.
static func _keys_at(keys: Array, y: float) -> Array:
	var n := keys.size()
	if y <= keys[0][0]:
		return keys[0].slice(1)
	if y >= keys[n - 1][0]:
		return keys[n - 1].slice(1)
	var i := 0
	while i < n - 2 and y > keys[i + 1][0]:
		i += 1
	var k1: Array = keys[i]
	var k2: Array = keys[i + 1]
	var k0: Array = keys[maxi(i - 1, 0)]
	var k3: Array = keys[mini(i + 2, n - 1)]
	var t: float = (y - k1[0]) / maxf(k2[0] - k1[0], 1e-6)
	var out := []
	for c in range(1, k1.size()):
		var p0: float = k0[c]
		var p1: float = k1[c]
		var p2: float = k2[c]
		var p3: float = k3[c]
		var m1 := (p2 - p0) * 0.5
		var m2 := (p3 - p1) * 0.5
		# Ueberschwinger begrenzen (monoton zwischen den Stuetzstellen)
		var lo := minf(p1, p2)
		var hi := maxf(p1, p2)
		var t2 := t * t
		var t3 := t2 * t
		var val := (2.0 * t3 - 3.0 * t2 + 1.0) * p1 + (t3 - 2.0 * t2 + t) * m1 + (-2.0 * t3 + 3.0 * t2) * p2 + (t3 - t2) * m2
		var span := hi - lo
		out.append(clampf(val, lo - span * 0.15, hi + span * 0.15))
	return out


## Verbindet Ringe gleicher Punktzahl zu einer Roehre.
## cols: je Ring Color oder PackedColorArray; wts: je Ring Gewichtsliste
## [b, w, ...] oder Array von Gewichtslisten je Punkt.
## params: optional je Ring float oder PackedFloat32Array (UV2.y).
func _loft(s: Surf, rings: Array, cols: Array, wts: Array, cap0: bool, cap1: bool, closed := true, params := []) -> PackedInt32Array:
	var n: int = rings[0].size()
	var base := s.v.size()
	var first_idx := PackedInt32Array()
	for k in rings.size():
		var ring: PackedVector3Array = rings[k]
		var c = cols[k]
		var w = wts[k]
		var per_v_w: bool = w.size() > 0 and w[0] is Array
		var pr = params[k] if k < params.size() else null
		for j in n:
			var col: Color = c[j] if c is PackedColorArray else c
			if pr != null:
				s.param = pr[j] if pr is PackedFloat32Array else float(pr)
			s.add(ring[j], col, w[j] if per_v_w else w)
	var segs := n if closed else n - 1
	for k in rings.size() - 1:
		var r0 := base + k * n
		var r1 := base + (k + 1) * n
		for j in segs:
			var j1 := (j + 1) % n
			s.tri(r0 + j, r1 + j1, r0 + j1)
			s.tri(r0 + j, r1 + j, r1 + j1)
	if cap0:
		_cap(s, base, n, rings[0], cols[0], wts[0], true)
	if cap1:
		_cap(s, base + (rings.size() - 1) * n, n, rings[rings.size() - 1], cols[cols.size() - 1], wts[wts.size() - 1], false)
	first_idx.append(base)
	return first_idx


func _cap(s: Surf, start: int, n: int, ring: PackedVector3Array, c, w, is_start: bool) -> void:
	var center := Vector3.ZERO
	for q in ring:
		center += q
	center /= float(n)
	var col: Color = c[0] if c is PackedColorArray else c
	var ww: Array = w[0] if (w.size() > 0 and w[0] is Array) else w
	var ci := s.add(center, col, ww)
	for j in n:
		var j1 := (j + 1) % n
		if is_start:
			s.tri(ci, start + j, start + j1)
		else:
			s.tri(ci, start + j1, start + j)


## Versetzt Ringpunkte entlang der Flaechennormalen nach aussen.
static func _offset_rings(rings: Array, off) -> Array:
	var out := []
	var nr := rings.size()
	for k in nr:
		var ring: PackedVector3Array = rings[k]
		var n := ring.size()
		var prev: PackedVector3Array = rings[maxi(k - 1, 0)]
		var next: PackedVector3Array = rings[mini(k + 1, nr - 1)]
		var o: float = off[k] if off is Array else float(off)
		var center := Vector3.ZERO
		for q in ring:
			center += q
		center /= float(n)
		var res := PackedVector3Array()
		res.resize(n)
		for j in n:
			var t1 := ring[(j + 1) % n] - ring[(j - 1 + n) % n]
			var t2 := next[j] - prev[j]
			var nn := t1.cross(t2)
			if nn.length_squared() < 1e-14:
				nn = ring[j] - center
			if nn.dot(ring[j] - center) < 0.0:
				nn = -nn
			res[j] = ring[j] + nn.normalized() * o
		out.append(res)
	return out


## Ellipsoid/Kugel (fuer Augen, Knoepfe, Dutt usw.).
func _sphere(s: Surf, c: Vector3, r: Vector3, col: Color, bw: Array, seg := 10, rings := 7, basis := Basis()) -> void:
	var rr := []
	var cols := []
	var ws := []
	for k in range(1, rings):
		var phi := PI * float(k) / float(rings)
		var ring := PackedVector3Array()
		for j in seg:
			var th := TAU * float(j) / float(seg)
			var d := Vector3(sin(phi) * sin(th), cos(phi), sin(phi) * cos(th))
			ring.append(c + basis * (d * r))
		rr.append(ring)
		cols.append(col)
		ws.append(bw)
	# Pole als Kappen (von oben nach unten -> umgekehrte Reihenfolge fuer Aussenseite)
	rr.reverse()
	_loft(s, rr, cols, ws, true, true)


## Rohre entlang eines Pfades (Finger, Brillenbuegel, Ketten, Zoepfe).
func _path_tube(s: Surf, pts: Array, radii: Array, col, bw, seg := 6, cap0 := true, cap1 := true, closed_path := false, params := []) -> void:
	var rings := []
	var cols := []
	var ws := []
	var n := pts.size()
	var prev_side := Vector3.ZERO
	for k in n:
		var a: Vector3 = pts[maxi(k - 1, 0)] if not closed_path else pts[(k - 1 + n) % n]
		var b: Vector3 = pts[mini(k + 1, n - 1)] if not closed_path else pts[(k + 1) % n]
		var t := (b - a).normalized()
		var side := prev_side
		if side == Vector3.ZERO or absf(side.dot(t)) > 0.9:
			side = t.cross(Vector3.UP if absf(t.y) < 0.9 else Vector3.RIGHT).normalized()
		side = (side - t * side.dot(t)).normalized()
		prev_side = side
		var up := t.cross(side).normalized()
		var r: float = radii[k] if radii.size() > k else radii[radii.size() - 1]
		var ring := PackedVector3Array()
		for j in seg:
			var th := TAU * float(j) / float(seg)
			ring.append(pts[k] + (side * cos(th) + up * sin(th)) * r)
		rings.append(ring)
		cols.append(col[k] if col is Array else col)
		ws.append(bw[k] if (bw is Array and bw.size() > 0 and bw[0] is Array) else bw)
	if closed_path:
		rings.append(rings[0])
		cols.append(cols[0])
		ws.append(ws[0])
	# Orientierung: Ringe laufen gegen die Pfadrichtung -> pruefen
	var o0: PackedVector3Array = rings[0]
	var o1: PackedVector3Array = rings[1]
	var tt := (o1[0] - o0[0])
	var rt := (o0[1] - o0[0])
	var p0: Vector3 = pts[0]
	var outward: Vector3 = o0[0] - p0
	if rt.cross(tt).dot(outward) < 0.0:
		for k in rings.size():
			var rv: PackedVector3Array = rings[k]
			rv.reverse()
			rings[k] = rv
	_loft(s, rings, cols, ws, cap0 and not closed_path, cap1 and not closed_path, true, params)


func _seg(n0: int) -> int:
	match lod:
		0:
			return n0
		1:
			return maxi(6, int(n0 * 0.45))
	return maxi(5, int(n0 * 0.3))


static func _normals_of(rings: Array) -> Array:
	var out := []
	var nr := rings.size()
	for k in nr:
		var ring: PackedVector3Array = rings[k]
		var n := ring.size()
		var prev: PackedVector3Array = rings[maxi(k - 1, 0)]
		var next: PackedVector3Array = rings[mini(k + 1, nr - 1)]
		var center := Vector3.ZERO
		for q in ring:
			center += q
		center /= float(n)
		var res := PackedVector3Array()
		res.resize(n)
		for j in n:
			var t1 := ring[(j + 1) % n] - ring[(j - 1 + n) % n]
			var t2 := next[j] - prev[j]
			var nn := t1.cross(t2)
			if nn.length_squared() < 1e-14:
				nn = ring[j] - center
			if nn.dot(ring[j] - center) < 0.0:
				nn = -nn
			res[j] = nn.normalized()
		out.append(res)
	return out


## Fuegt Schnittwerte in eine Abtastliste ein (nahe Werte werden ersetzt).
static func _merge_samples(samples: Array, cuts: Array, min_gap: float) -> Array:
	var all := samples.duplicate()
	for c in cuts:
		var keep := []
		for v in all:
			if absf(float(v) - float(c)) > min_gap:
				keep.append(v)
		keep.append(c)
		all = keep
	all.sort()
	return all


## Index des Abtastwerts, der einem Schnittwert am naechsten liegt.
static func _idx_of(samples: Array, v: float) -> int:
	var best := 0
	var bd := INF
	for i in samples.size():
		var d := absf(float(samples[i]) - v)
		if d < bd:
			bd = d
			best = i
	return best


## Baut aus Basisringen ein Teilstueck [i0, i1] mit Versatz.
## colf(k, j, pt) -> Color, wf(k, j, pt) -> Array, pf(k, j, pt) -> float (UV2.y) oder null.
## hem0/hem1: offene Kante bekommt eine sichtbare Stoffkante (Saum).
func _piece(s: Surf, base: Array, normals: Array, i0: int, i1: int, off, colf: Callable, wf: Callable, pf, cap0: bool, cap1: bool, hem0 := false, hem1 := false) -> void:
	if i1 <= i0:
		return
	var rings := []
	var cols := []
	var wts := []
	var prms := []
	var n: int = base[0].size()
	var ks := []
	if hem0:
		ks.append(-i0 - 1)
	for k in range(i0, i1 + 1):
		ks.append(k)
	if hem1:
		ks.append(-i1 - 1)
	for kk in ks:
		var k: int = kk if kk >= 0 else -kk - 1
		var is_hem: bool = kk < 0
		var o: float = off[k] if off is Array else float(off)
		if is_hem:
			o = o * 0.15
		var ring := PackedVector3Array()
		var cr := PackedColorArray()
		var wr := []
		var pr := PackedFloat32Array()
		var b: PackedVector3Array = base[k]
		var nm: PackedVector3Array = normals[k]
		for j in n:
			ring.append(b[j] + nm[j] * o)
			var c: Color = colf.call(k, j, b[j])
			if is_hem:
				c = c.darkened(0.3)
			cr.append(c)
			wr.append(wf.call(k, j, b[j]))
			pr.append(float(pf.call(k, j, b[j])) if pf is Callable else s.param)
		rings.append(ring)
		cols.append(cr)
		wts.append(wr)
		prms.append(pr)
	_loft(s, rings, cols, wts, cap0 and not hem0, cap1 and not hem1, true, prms)


# ================================================================== Kopfform

func _setup_head() -> void:
	face_jaw = float(app.face.jaw)
	face_nose = float(app.face.nose)
	face_brows = float(app.face.brows)
	face_cheeks = float(app.face.cheeks)
	var W := 0.34 * hw_scale
	var D := 0.86 * hd_scale
	var fem := p.fem * p.dimorph
	var jawf := (0.86 + 0.28 * face_jaw) * lerpf(1.0, 0.93, fem) * lerpf(0.92, 1.0, p.maturity)
	# [y (Erwachsenen-Raster), Breite/W, vorne/D, hinten/D, Lage der breitesten Stelle 0..1]
	var raw := [
		[-0.50, 0.12, 0.33, 0.13, 0.15],
		[-0.48, 0.33, 0.39, 0.07, 0.18],
		[-0.45, 0.50, 0.42, 0.00, 0.20],
		[-0.40, 0.66, 0.44, -0.08, 0.20],
		[-0.33, 0.78, 0.455, -0.17, 0.22],
		[-0.26, 0.86, 0.46, -0.28, 0.28],
		[-0.18, 0.93, 0.465, -0.40, 0.36],
		[-0.10, 0.98, 0.465, -0.48, 0.42],
		[-0.02, 0.985, 0.455, -0.52, 0.45],
		[0.06, 1.00, 0.475, -0.545, 0.45],
		[0.14, 1.00, 0.47, -0.56, 0.45],
		[0.24, 0.96, 0.45, -0.555, 0.45],
		[0.34, 0.85, 0.40, -0.53, 0.44],
		[0.42, 0.66, 0.30, -0.46, 0.42],
		[0.47, 0.42, 0.18, -0.33, 0.40],
		[0.50, 0.08, 0.04, -0.12, 0.40],
	]
	head_keys = []
	for r in raw:
		var y: float = r[0]
		var yn := _face_y(y)
		var w: float = r[1] * W
		var zf: float = r[2] * D
		var zb: float = r[3] * D
		if y <= -0.26:
			var t := _sstep(-0.26, -0.42, y)
			w *= lerpf(1.0, jawf, t)
			# Babys: kleines, zurueckliegendes Kinn
			zf -= (1.0 - p.chin_amount) * 0.05 * t
		if y >= 0.14:
			# Kinder: runderer Hirnschaedel
			w *= lerpf(1.04, 1.0, p.maturity)
		head_keys.append([yn, w, zf, zb, r[4]])
	var base := _head_front(ex, e, false)
	eye_z = base.z - p.eye_r * 0.86
	rest_global[B_EYE_L] = Transform3D(Basis(), _hm(Vector3(ex, e, eye_z)))
	rest_global[B_EYE_R] = Transform3D(Basis(), _hm(Vector3(-ex, e, eye_z)))
	rest_global[B_LIDS] = Transform3D(Basis(), _hm(Vector3(0.0, e, eye_z)))


func _face_y(y: float) -> float:
	if y >= 0.14:
		return y
	if y >= 0.0:
		return lerpf(e, 0.14, y / 0.14)
	return -0.5 + (y + 0.5) * (lf / 0.5)


## Kopfpunkt in Kopfeinheiten fuer Hoehe y und Winkel th (0 = vorne, +X = links).
func _head_pt(y: float, th: float, feat: bool) -> Vector3:
	var k := _keys_at(head_keys, y)
	var w: float = k[0]
	var zf: float = k[1]
	var zb: float = k[2]
	var zc: float = lerpf(zb, zf, k[3])
	var s := sin(th)
	var c := cos(th)
	var nexp := 2.45 if c > 0.0 else 2.1
	var x := w * _spow(s, 2.0 / nexp)
	var cc := _spow(c, 2.0 / nexp)
	var z := zc + (zf - zc) * cc if cc >= 0.0 else zc + (zc - zb) * cc
	if not feat:
		return Vector3(x, y, z)
	var front := maxf(c, 0.0)
	var fr2 := front * front
	var ax := absf(x)
	var sx := signf(x)
	var er := p.eye_r
	# Augenhoehlen
	z -= er * 0.95 * _g2((ax - ex) / (0.072 * hw_scale), (y - e - 0.004) / 0.052) * fr2
	# Nasenwurzel
	z += 0.012 * _g2(x / 0.045, (y - e + 0.02) / 0.07) * front
	# Brauenbogen
	var ridge := 0.006 + 0.016 * p.brow_ridge + 0.006 * (face_brows - 0.5)
	z += ridge * exp(-pow((y - (e + 0.078)) / 0.032, 2.0)) * (1.0 - _sstep(0.17, 0.3, ax)) * fr2
	# Jochbein
	var cb := (0.010 + 0.022 * face_cheeks) * lerpf(0.4, 1.0, p.maturity)
	var gcb := _g2((ax - 0.205 * hw_scale) / 0.07, (y - (e - 0.1)) / 0.055) * front
	z += cb * gcb
	x += sx * cb * 0.5 * gcb
	# Wangen (Babyspeck, Gewicht)
	var cf := 0.03 * p.cheek_fat * (0.6 + 0.8 * face_cheeks)
	var gcf := _g2((ax - 0.185 * hw_scale) / 0.09, (y - (e - 0.55 * lf)) / 0.09) * front
	z += cf * gcf
	x += sx * cf * 0.7 * gcf
	# Mundpartie
	z += 0.016 * _g2(x / 0.13, (y - mouth_y) / 0.07) * front
	z -= 0.007 * _g2((ax - 0.105) / 0.03, (y - mouth_y) / 0.03) * front
	# Kinn
	var ch := p.chin_amount * (0.012 + 0.02 * face_jaw)
	z += ch * _g2(x / (0.06 + 0.035 * face_jaw), (y + 0.445) / 0.035) * front
	# Schlaefen
	x -= sx * 0.012 * _g2((y - (e + 0.1)) / 0.08, (c - 0.35) / 0.3)
	# Haengebacken im Alter
	var gj := _g2((ax - 0.2) / 0.07, (y + 0.4) / 0.05) * front
	z += p.jowls * 0.01 * gj
	x += sx * p.jowls * 0.012 * gj
	return Vector3(x, y, z)


## Punkt der vorderen Kopfflaeche bei (x, y) in Kopfeinheiten.
func _head_front(x: float, y: float, feat := true) -> Vector3:
	var k := _keys_at(head_keys, y)
	var w: float = maxf(k[0], 1e-4)
	var r := clampf(absf(x) / w, 0.0, 0.999)
	var s := pow(r, 2.45 / 2.0)
	var th := asin(s) * signf(x)
	return _head_pt(y, th, feat)


func _head_w(y: float) -> float:
	return float(_keys_at(head_keys, y)[0])


## Kopfeinheiten -> Modellraum.
func _hm(v: Vector3) -> Vector3:
	return head_c + v * hh


## Winkelverteilung mit hoeherer Dichte im Gesicht (von hinten ueber vorne nach hinten).
func _head_thetas(n: int) -> PackedFloat32Array:
	var out := PackedFloat32Array()
	for j in n:
		var sv := 2.0 * float(j) / float(n) - 1.0
		out.append(PI * _spow(sv, 1.45))
	return out


# ================================================================== Rumpfform

func _setup_torso() -> void:
	var hw := p.hip_w * 0.5
	var ww := p.waist_w * 0.5
	var cw := p.chest_w * 0.5
	var sw := p.shoulder_w * 0.5
	var nr := p.neck_r
	var hd := p.hip_d * 0.5
	var wd := p.waist_d * 0.5
	var cd := p.chest_d * 0.5
	var nz := -0.012 * H
	var nz3 := head_c.z - 0.13 * hh
	var y12 := maxf(p.shoulder_y + 0.036 * H, p.chin_y - 0.1 * hh)
	var raw := [
		[p.crotch_y - 0.004 * H, p.hip_sep * 0.38, 0.30 * hd, -0.30 * hd, 2.0],
		[p.crotch_y + 0.012 * H, hw * 0.84, 0.66 * hd, -0.82 * hd, 2.3],
		[p.hip_y, hw * 0.97, 0.84 * hd, -0.98 * hd, 2.5],
		[p.hip_y + 0.035 * H, hw, 0.9 * hd, -0.93 * hd, 2.5],
		[lerpf(p.hip_y + 0.035 * H, p.waist_y, 0.5), lerpf(hw, ww, 0.55), lerpf(0.9 * hd, wd, 0.5), -lerpf(0.93 * hd, 0.95 * wd, 0.5), 2.5],
		[p.waist_y, ww, wd, -0.95 * wd, 2.4],
		[lerpf(p.waist_y, p.bust_y, 0.55), lerpf(ww, cw, 0.75), lerpf(wd, cd, 0.7) * 0.98, -lerpf(0.95 * wd, cd, 0.75), 2.4],
		[p.bust_y, cw, cd, -cd, 2.5],
		[p.shoulder_y - 0.045 * H, cw * 1.01, cd * 0.94, -cd, 2.6],
		[p.shoulder_y - 0.014 * H, sw + p.upper_arm_r * 0.55, cd * 0.72, -cd * 0.84, 2.8],
		[p.shoulder_y + 0.010 * H, lerpf(nr, sw + p.upper_arm_r * 0.3, 0.6), cd * 0.40, -cd * 0.62, 2.4],
		[p.shoulder_y + 0.024 * H, nr * 1.3, nz + nr * 0.95, nz - nr * 1.25, 2.1],
		[y12, nr, lerpf(nz, nz3, 0.5) + nr, lerpf(nz, nz3, 0.5) - nr * 1.05, 2.0],
		[p.chin_y + 0.35 * hh, nr * 0.92, nz3 + nr * 0.9, nz3 - nr, 2.0],
	]
	torso_keys = []
	var last_y := -INF
	for r in raw:
		var y: float = maxf(float(r[0]), last_y + 0.004 * H)
		last_y = y
		torso_keys.append([y, r[1], r[2], r[3], r[4]])
	_setup_clothing_lines()


func _setup_clothing_lines() -> void:
	var hip := p.hip_y
	match top_key:
		"tshirt", "polo":
			top_hem_y = hip + 0.03 * H
		"shirt":
			top_hem_y = hip + 0.015 * H
		"hoodie", "sweater":
			top_hem_y = hip + 0.005 * H
		"jacket":
			top_hem_y = hip - 0.012 * H
		"suit":
			top_hem_y = hip - 0.035 * H
		"blouse", "tank":
			top_hem_y = hip + 0.035 * H
		"dress", "onesie":
			top_hem_y = p.crotch_y - 0.01 * H
		_:
			top_hem_y = hip + 0.03 * H
	top_neck_y = p.shoulder_y + 0.022 * H
	match top_key:
		"shirt", "polo", "suit":
			top_neck_y = p.shoulder_y + 0.028 * H
		"jacket", "hoodie":
			top_neck_y = p.shoulder_y + 0.03 * H
		"tank", "dress", "blouse":
			top_neck_y = p.shoulder_y + 0.016 * H
	match bottom_key:
		"jeans", "shorts":
			bottom_top_y = p.waist_y - 0.012 * H
		"leggings", "joggers":
			bottom_top_y = p.waist_y + 0.004 * H
		_:
			bottom_top_y = p.waist_y
	bottom_top_y = maxf(bottom_top_y, top_hem_y + 0.02 * H)


## Rumpfpunkt (Modellraum) fuer Hoehe y (ohne Sohle) und Winkel th.
func _torso_pt(y: float, th: float) -> Vector3:
	var k := _keys_at(torso_keys, y)
	var w: float = k[0]
	var zf: float = k[1]
	var zb: float = k[2]
	var nexp: float = k[3]
	var s := sin(th)
	var c := cos(th)
	var x := w * _spow(s, 2.0 / nexp)
	var cc := _spow(c, 2.0 / nexp)
	var z := zf * cc if cc >= 0.0 else -zb * cc
	var front := maxf(cc, 0.0)
	var back := maxf(-cc, 0.0)
	var ax := absf(x)
	# Brust
	if p.bust > 0.0:
		for sx in [-1.0, 1.0]:
			var dy := y - p.bust_y
			var sy := 0.05 * H if dy > 0.0 else 0.028 * H
			var g := exp(-pow((x - sx * p.chest_w * 0.24) / (0.048 * H), 2.0) - pow(dy / sy, 2.0))
			z += p.bust * g * sqrt(front)
	# Brustmuskel
	var pec := 0.006 * H * p.maturity * (1.0 - p.fem * p.dimorph) * (0.4 + p.muscle)
	z += pec * exp(-pow((ax - p.chest_w * 0.22) / (0.05 * H), 2.0) - pow((y - (p.shoulder_y - 0.085 * H)) / (0.035 * H), 2.0)) * front
	# Bauch
	z += p.belly * exp(-pow((y - (p.waist_y - 0.025 * H)) / (0.065 * H), 2.0)) * pow(front, 1.5) * (1.0 - pow(ax / maxf(w, 1e-4), 2.0) * 0.5)
	# Gesaess
	z -= p.glute * exp(-pow((ax - p.hip_w * 0.22) / (0.05 * H), 2.0) - pow((y - (p.hip_y - 0.005 * H)) / (0.042 * H), 2.0)) * back
	# Wirbelsaeulenrinne
	z += 0.004 * H * exp(-pow(x / (0.012 * H), 2.0)) * back * _sstep(p.hip_y, p.waist_y, y) * (1.0 - _sstep(p.shoulder_y - 0.05 * H, p.shoulder_y, y))
	# Schulterblaetter
	z -= 0.006 * H * exp(-pow((ax - p.chest_w * 0.26) / (0.04 * H), 2.0) - pow((y - (p.shoulder_y - 0.075 * H)) / (0.04 * H), 2.0)) * back
	return Vector3(x, y + sole, z)


## Hintere Rumpfflaeche (z) bei (x, y) – fuer Haare und Rucksack.
func _torso_back_z(x: float, y: float) -> float:
	var k := _keys_at(torso_keys, y)
	var w: float = k[0]
	var zb: float = k[2]
	var nexp: float = k[3]
	var r := clampf(absf(x) / maxf(w, 1e-4), 0.0, 1.0)
	return zb * pow(maxf(1.0 - pow(r, nexp), 0.0), 1.0 / nexp)


func _torso_half_w(y: float) -> float:
	return float(_keys_at(torso_keys, y)[0])


func _torso_weights(y: float, x: float) -> Array:
	var b := []
	var t1 := _sstep(p.hip_y + 0.02 * H, p.waist_y, y)
	var t2 := _sstep(p.waist_y, p.bust_y, y)
	var t3 := _sstep(p.shoulder_y + 0.01 * H, p.shoulder_y + 0.045 * H, y)
	var t4 := _sstep(p.chin_y - 0.05 * hh, p.chin_y + 0.3 * hh, y)
	if t3 > 0.0:
		b = [B_CHEST, (1.0 - t3), B_NECK, t3 * (1.0 - t4), B_HEAD, t3 * t4]
	elif t2 > 0.0:
		b = [B_SPINE, 1.0 - t2, B_CHEST, t2]
	else:
		b = [B_HIPS, 1.0 - t1, B_SPINE, t1]
	# Schulter folgt etwas dem Oberarm
	var sh := _sstep(p.shoulder_w * 0.32, p.shoulder_w * 0.5, absf(x)) * _sstep(p.shoulder_y - 0.07 * H, p.shoulder_y - 0.02 * H, y) * (1.0 - t3)
	if sh > 0.0:
		var ua := B_UPPERARM_L if x > 0.0 else B_UPPERARM_R
		for i in range(1, b.size(), 2):
			b[i] = float(b[i]) * (1.0 - sh * 0.35)
		b.append(ua)
		b.append(sh * 0.35)
	return b


# ================================================================== Kleidungsdetails

func _bottom_fabric() -> int:
	match bottom_key:
		"jeans":
			return HumanoidMaterials.FABRIC_DENIM
		"joggers", "leggings":
			return HumanoidMaterials.FABRIC_KNIT
		"dress":
			return _top_fabric_base()
	return HumanoidMaterials.FABRIC_COTTON


func _top_fabric_base() -> int:
	match top_key:
		"sweater":
			return HumanoidMaterials.FABRIC_KNIT
		"blouse", "jacket":
			return HumanoidMaterials.FABRIC_SATIN
	return HumanoidMaterials.FABRIC_COTTON


func _skin_tint(c: Color) -> Color:
	return c


## Farbe und Stoffart eines Oberteilpunkts (Basis-Rumpfpunkt).
func _top_detail(pt: Vector3) -> Array:
	var y := pt.y - sole
	var ax := absf(pt.x)
	var front := pt.z > 0.0
	var c := top_col
	var f := _top_fabric_base()
	var skin := [skin_col, HumanoidMaterials.FABRIC_SKIN]
	var sy := p.shoulder_y
	match top_key:
		"tshirt":
			if y > top_neck_y - 0.012 * H:
				c = c.darkened(0.08)
		"shirt":
			if front and y > sy - 0.035 * H and ax < (y - (sy - 0.035 * H)) * 0.6:
				return skin
			if front and ax < 0.007 * H:
				c = c.darkened(0.06)
				if ax < 0.0035 * H and fmod(y, 0.055 * H) < 0.008 * H:
					c = c.lightened(0.5)
		"polo":
			if front and y > sy - 0.015 * H and ax < (y - (sy - 0.015 * H)) * 0.5:
				return skin
			if front and ax < 0.006 * H and y > sy - 0.07 * H:
				c = c.darkened(0.08)
		"blouse":
			if front and y > sy - 0.07 * H and ax < (y - (sy - 0.07 * H)) * 0.5:
				return skin
		"tank", "dress":
			var strap_c := p.neck_r + 0.028 * H
			var strap_w := 0.011 * H if top_key == "tank" else 0.022 * H
			if y > p.bust_y + 0.035 * H:
				if absf(ax - strap_c) > strap_w * 0.5 and ax > strap_c - strap_w * 0.5:
					return skin
				if front and ax < strap_c - strap_w * 0.5 and y > p.bust_y + 0.05 * H:
					return skin
				if not front and ax < strap_c - strap_w * 0.5 and y > sy - 0.03 * H:
					return skin
		"suit":
			var v0 := p.bust_y - 0.025 * H
			if front and y > v0 and ax < (y - v0) * 0.48 + 0.003 * H:
				var tie_w := 0.008 * H + (sy - y) * 0.04
				if ax < tie_w and y < sy + 0.02 * H:
					return [acc_col.darkened(0.35), HumanoidMaterials.FABRIC_SATIN]
				return [Color(0.94, 0.94, 0.95), HumanoidMaterials.FABRIC_COTTON]
			if front and absf(ax - ((y - v0) * 0.48 + 0.006 * H)) < 0.003 * H and y > v0:
				c = c.darkened(0.25)
			if front and ax < 0.004 * H and absf(y - (p.waist_y - 0.012 * H)) < 0.005 * H:
				return [Color(0.08, 0.08, 0.08), HumanoidMaterials.FABRIC_PLASTIC]
		"jacket":
			if front and ax < 0.0035 * H:
				return [Color(0.55, 0.56, 0.58), HumanoidMaterials.FABRIC_METAL]
			if y < top_hem_y + 0.02 * H:
				c = c.darkened(0.12)
		"hoodie":
			if front and y > p.hip_y + 0.025 * H and y < p.waist_y + 0.02 * H and ax < p.chest_w * 0.3:
				c = c.darkened(0.05)
				if absf(ax - p.chest_w * 0.3) < 0.006 * H or absf(y - (p.waist_y + 0.02 * H)) < 0.004 * H:
					c = c.darkened(0.15)
			if y < top_hem_y + 0.025 * H:
				c = c.darkened(0.1)
				f = HumanoidMaterials.FABRIC_KNIT
		"sweater":
			if y < top_hem_y + 0.025 * H or y > top_neck_y - 0.015 * H:
				c = c.darkened(0.1)
		"onesie":
			if front and ax < 0.003 * H and y < p.bust_y and fmod(y, 0.04 * H) < 0.006 * H:
				c = c.lightened(0.5)
			if y > top_neck_y - 0.012 * H:
				c = c.lightened(0.25)
	return [c, f]


func _bottom_detail(pt: Vector3) -> Array:
	var y := pt.y - sole
	var ax := absf(pt.x)
	var front := pt.z > 0.0
	var c := bottom_col
	var f := _bottom_fabric()
	match bottom_key:
		"jeans", "trousers", "shorts":
			if y > bottom_top_y - 0.014 * H:
				if front and ax < 0.011 * H:
					return [Color(0.72, 0.72, 0.74), HumanoidMaterials.FABRIC_METAL]
				return [Color(0.13, 0.09, 0.07), HumanoidMaterials.FABRIC_LEATHER]
			if front and ax < 0.0025 * H and y > p.crotch_y + 0.02 * H:
				c = c.darkened(0.2)
		"joggers", "leggings":
			if y > bottom_top_y - 0.016 * H:
				c = c.darkened(0.1)
	return [c, f]


# ================================================================== Rumpf

func _build_torso() -> void:
	var n := _seg(28)
	var y0: float = torso_keys[0][0]
	var y1: float = torso_keys[torso_keys.size() - 1][0]
	var step := (0.011 if lod == 0 else 0.03) * H
	var samples := []
	var y := y0
	while y < y1 - step * 0.5:
		samples.append(y)
		y += step
	samples.append(y1)
	var skin_from := top_neck_y - 0.02 * H
	var full_top := top_key in ["dress", "onesie"]
	var cuts := [top_hem_y, top_neck_y, skin_from]
	if not full_top:
		cuts.append(bottom_top_y)
	samples = _merge_samples(samples, cuts, step * 0.3)
	var thetas := PackedFloat32Array()
	for j in n:
		thetas.append(TAU * float(j) / float(n))
	var base := []
	for yy in samples:
		var ring := PackedVector3Array()
		for th in thetas:
			ring.append(_torso_pt(float(yy), th))
		base.append(ring)
	var normals := _normals_of(base)
	var wf := func(_k: int, _j: int, pt: Vector3) -> Array:
		return _torso_weights(pt.y - sole, pt.x)
	# Unterteil
	if not full_top:
		var ib := _idx_of(samples, bottom_top_y)
		var ob := (0.002 if bottom_key == "leggings" else 0.005) * H
		_piece(s_cloth, base, normals, 0, ib, ob,
			func(_k: int, _j: int, pt: Vector3) -> Color: return _bottom_detail(pt)[0], wf,
			func(_k: int, _j: int, pt: Vector3) -> float: return float(_bottom_detail(pt)[1]),
			true, false, false, true)
	# Oberteil
	var it0 := 0 if full_top else _idx_of(samples, top_hem_y)
	var it1 := _idx_of(samples, top_neck_y)
	var ot := 0.009 * H
	match top_key:
		"hoodie":
			ot = 0.014 * H
		"jacket", "suit":
			ot = 0.016 * H
		"sweater":
			ot = 0.012 * H
		"tank", "blouse", "dress":
			ot = 0.007 * H
	var offs := []
	for k in samples.size():
		var yy: float = samples[k]
		# lockerer Fall unterhalb der Brust (kein hautenges Oberteil)
		var o := ot
		if top_key != "dress" and top_key != "onesie" and p.bust > 0.0:
			o += p.bust * 0.35 * _sstep(p.bust_y, p.waist_y, yy)
		offs.append(o)
	_piece(s_cloth, base, normals, it0, it1, offs,
		func(_k: int, _j: int, pt: Vector3) -> Color: return _top_detail(pt)[0], wf,
		func(_k: int, _j: int, pt: Vector3) -> float: return float(_top_detail(pt)[1]),
		full_top, false, not full_top, true)
	# Haut (Hals)
	var is0 := _idx_of(samples, skin_from)
	var skinf := func(_k: int, _j: int, pt: Vector3) -> Color:
		return skin_col
	_piece(s_skin, base, normals, is0, samples.size() - 1, 0.0, skinf, wf, null, false, false)


# ================================================================== Rock / Kleid

func _build_skirt() -> void:
	var hem := 0.0
	var top := 0.0
	var flare := 0.0
	var col := bottom_col
	var fab := _bottom_fabric()
	match bottom_key:
		"skirt":
			top = p.waist_y - 0.005 * H
			hem = p.knee_y + 0.035 * H
			flare = 0.16
		"long_skirt":
			top = p.waist_y - 0.005 * H
			hem = p.ankle_y + 0.07 * H
			flare = 0.12
		"dress":
			top = p.waist_y - 0.01 * H
			hem = p.knee_y + 0.015 * H
			flare = 0.2
		_:
			return
	var n := _seg(28)
	var step := (0.016 if lod == 0 else 0.05) * H
	var samples := []
	var y := top
	while y > hem + step * 0.5:
		samples.append(y)
		y -= step
	samples.append(hem)
	var rings := []
	var cols := []
	var wts := []
	var leg_x := p.hip_sep * 0.5
	for yy in samples:
		var yv: float = yy
		var t := (top - yv) / maxf(top - hem, 1e-4)
		var ring := PackedVector3Array()
		var cr := PackedColorArray()
		var wr := []
		# Umriss: Rumpf oben, darunter Huellkurve um beide Oberschenkel
		var tw := _torso_half_w(clampf(yv, p.crotch_y + 0.01 * H, 10.0))
		var legs_w := leg_x + p.thigh_r * lerpf(1.0, 0.8, clampf((p.hip_y - yv) / p.thigh_len, 0.0, 1.0))
		var a := maxf(tw, legs_w) + 0.008 * H + flare * (top - yv)
		var zf := maxf(float(_keys_at(torso_keys, clampf(yv, p.crotch_y + 0.01 * H, 10.0))[1]), p.thigh_r * 1.05) + 0.008 * H + flare * 0.6 * (top - yv)
		var zb := maxf(-float(_keys_at(torso_keys, clampf(yv, p.crotch_y + 0.01 * H, 10.0))[2]) + p.glute * 0.6, p.thigh_r * 1.05) + 0.008 * H + flare * 0.7 * (top - yv)
		for j in n:
			var th := TAU * float(j) / float(n)
			var s := sin(th)
			var c := cos(th)
			var x := a * _spow(s, 2.0 / 2.3)
			var cc := _spow(c, 2.0 / 2.3)
			var z := zf * cc if cc >= 0.0 else zb * cc
			# leichte Faltenwuerfe
			var fold := 1.0 + 0.025 * t * sin(th * 9.0)
			ring.append(Vector3(x * fold, yv + sole, z * fold))
			var shade := col.darkened(0.06 * t * (0.5 + 0.5 * sin(th * 9.0)))
			cr.append(shade)
			var k := _sstep(0.0, 1.0, t) * 0.8 * (0.75 + 0.25 * maxf(cc, 0.0))
			var wl := k * _sstep(-0.35, 0.6, x / a)
			var wrt := k * _sstep(-0.35, 0.6, -x / a)
			wr.append([B_HIPS, maxf(1.0 - wl - wrt, 0.0), B_THIGH_L, wl, B_THIGH_R, wrt])
		rings.append(ring)
		cols.append(cr)
		wts.append(wr)
	# Saum: Innenkante
	var last: PackedVector3Array = rings[rings.size() - 1]
	var inner := PackedVector3Array()
	for q in last:
		inner.append(Vector3(q.x * 0.96, q.y + 0.004 * H, q.z * 0.96))
	rings.append(inner)
	cols.append(col.darkened(0.45))
	wts.append(wts[wts.size() - 1])
	# Ringe laufen nach unten: Reihenfolge umdrehen (Achse nach oben)
	rings.reverse()
	cols.reverse()
	wts.reverse()
	s_cloth.param = fab
	_loft(s_cloth, rings, cols, wts, false, false)


# ================================================================== Arme

func _sleeve_end() -> float:
	var lu := p.upper_arm_len
	var lfa := p.forearm_len
	match top_key:
		"tshirt", "polo":
			return lu * 0.45
		"blouse":
			return lu + lfa * 0.45
		"tank", "dress":
			return -1.0
		"suit":
			return lu + lfa - 0.024 * H
	return lu + lfa - 0.005 * H


func _arm_keys() -> Array:
	var ua := p.upper_arm_r
	var er := p.elbow_r
	var fa := p.forearm_r
	var wr := p.wrist_r
	var lu := p.upper_arm_len
	var lfa := p.forearm_len
	var rd := ua * 1.04
	var mus := (0.6 + 0.8 * p.muscle) * p.maturity
	return [
		[0.0, rd, rd, rd * 1.02, 0.0],
		[0.22 * lu, ua * 1.1, ua * 1.0, ua * 1.05, -0.002 * H],
		[0.55 * lu, ua * 0.96, ua * (1.0 + 0.12 * mus), ua * 1.02, 0.002 * H],
		[0.85 * lu, er * 1.18, er * 1.1, er * 1.12, 0.0],
		[lu, er * 1.02, er * 1.0, er * 1.15, -0.002 * H],
		[lu + 0.2 * lfa, fa * 1.04, fa * 1.12, fa * 1.05, 0.002 * H],
		[lu + 0.55 * lfa, fa * 0.8, fa * 0.92, fa * 0.88, 0.001 * H],
		[lu + 0.9 * lfa, wr * 0.8, wr * 1.08, wr * 1.08, 0.0],
		[lu + lfa, wr * 0.74, wr * 1.05, wr * 1.05, 0.0],
		[lu + lfa + 0.5 * wr, wr * 0.7, wr * 1.0, wr * 1.0, 0.0],
	]


func _arm_weights(side: int, s: float) -> Array:
	var o := 0 if side == 1 else 4
	var lu := p.upper_arm_len
	var lfa := p.forearm_len
	var t1 := _sstep(lu - p.elbow_r * 0.9, lu + p.elbow_r * 0.9, s)
	var t2 := _sstep(lu + lfa - p.wrist_r * 0.8, lu + lfa + p.wrist_r * 0.4, s)
	if t2 > 0.0:
		return [B_FOREARM_L + o, 1.0 - t2, B_HAND_L + o, t2]
	return [B_UPPERARM_L + o, 1.0 - t1, B_FOREARM_L + o, t1]


func _build_arms() -> void:
	var n := _seg(14)
	var keys := _arm_keys()
	var rd: float = keys[0][1]
	var lu := p.upper_arm_len
	var lfa := p.forearm_len
	var s_end: float = keys[keys.size() - 1][0]
	var step := (0.012 if lod == 0 else 0.035) * H
	var samples := []
	var dome := [-0.96, -0.85, -0.65, -0.35] if lod == 0 else [-0.9, -0.5]
	for d in dome:
		samples.append(float(d) * rd * 0.62)
	var sv := 0.0
	while sv < s_end - step * 0.4:
		samples.append(sv)
		sv += step
	samples.append(s_end)
	var se := _sleeve_end()
	var cuts := []
	if se > 0.0:
		cuts.append(se)
		cuts.append(se - 0.02 * H)
	if top_key == "suit":
		cuts.append(lu + lfa - 0.007 * H)
	if acc.has("watch"):
		pass
	samples = _merge_samples(samples, cuts, step * 0.3)
	for side in [1, -1]:
		var j0: Vector3 = rest_global[B_UPPERARM_L if side == 1 else B_UPPERARM_R].origin
		var base := []
		for s in samples:
			var sf: float = s
			var k := _keys_at(keys, maxf(sf, 0.0))
			var scale := 1.0
			if sf < 0.0:
				scale = sqrt(maxf(1.0 - pow(sf / (rd * 0.62), 2.0), 0.0))
			var rx: float = k[0] * scale
			var rzf: float = k[1] * scale
			var rzb: float = k[2] * scale
			var cz: float = k[3]
			var ring := PackedVector3Array()
			for j in n:
				var th := TAU * float(j) / float(n)
				var sn := sin(th)
				var cs := cos(th)
				var x := -rx * _spow(sn, 2.0 / 2.15)
				var cc := _spow(cs, 2.0 / 2.15)
				var z := cz + (rzf * cc if cc >= 0.0 else rzb * cc)
				ring.append(j0 + Vector3(x, -sf, z))
			base.append(ring)
		var normals := _normals_of(base)
		var wf := func(_k: int, _j: int, pt: Vector3) -> Array:
			return _arm_weights(side, j0.y - pt.y)
		var skin_from := 0
		if se > 0.0:
			var ie := _idx_of(samples, se)
			var off := []
			for s in samples:
				var sf: float = s
				var o := 0.007 * H
				if top_key in ["tshirt", "polo"]:
					o += 0.008 * H * _sstep(0.0, se, sf)
				elif top_key == "blouse":
					o += 0.006 * H * _sstep(lu, se, sf)
				elif top_key in ["hoodie", "sweater"]:
					o = 0.011 * H - 0.006 * H * _sstep(se - 0.03 * H, se, sf)
				elif top_key in ["jacket", "suit"]:
					o = 0.012 * H
				off.append(o)
			var cf := func(_k: int, _j: int, pt: Vector3) -> Color:
				var sf: float = j0.y - pt.y
				var c := top_col
				if top_key in ["hoodie", "sweater"] and sf > se - 0.03 * H:
					c = c.darkened(0.1)
				return c
			_piece(s_cloth, base, normals, 0, ie, off, cf, wf,
				func(_k: int, _j: int, _pt: Vector3) -> float: return float(_top_fabric_base()),
				true, false, false, true)
			skin_from = _idx_of(samples, se - 0.02 * H)
			if top_key == "suit":
				var ic := _idx_of(samples, lu + lfa - 0.007 * H)
				_piece(s_cloth, base, normals, skin_from, ic, 0.005 * H,
					func(_k: int, _j: int, _pt: Vector3) -> Color: return Color(0.94, 0.94, 0.95), wf,
					func(_k: int, _j: int, _pt: Vector3) -> float: return float(HumanoidMaterials.FABRIC_COTTON),
					false, false, false, true)
		var skf := func(_k: int, _j: int, pt: Vector3) -> Color:
			var sf: float = j0.y - pt.y
			var c := skin_col
			# Ellenbogen leicht geroetet
			c = c.lerp(c * Color(1.0, 0.9, 0.88), 0.25 * exp(-pow((sf - lu) / (0.02 * H), 2.0)) * float(pt.z < j0.z))
			return c
		_piece(s_skin, base, normals, skin_from, samples.size() - 1, 0.0, skf, wf, null, skin_from == 0, false)


# ================================================================== Beine

func _leg_keys() -> Array:
	var thr := p.thigh_r
	var kr := p.knee_r
	var cr := p.calf_r
	var ar := p.ankle_r
	var T := p.thigh_len
	var S := p.shin_len
	return [
		[-0.075 * H, thr * 0.85, thr * 0.9, thr * 0.9, 0.0],
		[-0.03 * H, thr * 0.98, thr * 1.0, thr * 1.0, 0.0],
		[0.12 * T, thr * 1.0, thr * 0.98, thr * 0.97, 0.002 * H],
		[0.45 * T, thr * 0.86, thr * 0.88, thr * 0.84, 0.004 * H],
		[0.8 * T, kr * 1.22, kr * 1.22, kr * 1.12, 0.003 * H],
		[T, kr, kr * 1.12, kr * 1.02, 0.004 * H],
		[T + 0.1 * S, kr * 0.92, kr * 0.98, kr * 1.0, -0.002 * H],
		[T + 0.3 * S, cr * 0.95, cr * 0.95, cr * 1.3, -0.004 * H],
		[T + 0.6 * S, cr * 0.72, cr * 0.75, cr * 0.85, -0.002 * H],
		[T + 0.88 * S, ar * 1.05, ar * 1.1, ar * 1.12, 0.0],
		[T + S, ar, ar * 1.18, ar * 1.18, 0.0],
		[T + S + 0.6 * ar, ar, ar * 1.1, ar * 1.1, 0.0],
	]


func _leg_weights(side: int, s: float) -> Array:
	var o := 0 if side == 1 else 3
	var T := p.thigh_len
	var S := p.shin_len
	if s < 0.0:
		var w := _sstep(0.0, -0.075 * H, s) * 0.55
		return [B_HIPS, w, B_THIGH_L + o, 1.0 - w]
	var t2 := _sstep(T + S - p.ankle_r, T + S + p.ankle_r * 0.5, s)
	if t2 > 0.0:
		return [B_SHIN_L + o, 1.0 - t2, B_FOOT_L + o, t2]
	var t1 := _sstep(T - p.knee_r, T + p.knee_r * 0.8, s)
	return [B_THIGH_L + o, 1.0 - t1, B_SHIN_L + o, t1]


func _pants_end() -> float:
	var T := p.thigh_len
	var S := p.shin_len
	var ar := p.ankle_r
	match bottom_key:
		"jeans":
			return T + S - ar * 1.4
		"trousers":
			return T + S - ar * 0.6
		"joggers":
			return T + S - ar * 1.0
		"leggings":
			return T + S - ar * 2.0
		"shorts":
			return T * 0.62
		"onesie":
			return T + S + ar * 0.5
	return -1.0


func _build_legs() -> void:
	var n := _seg(14)
	var keys := _leg_keys()
	var T := p.thigh_len
	var S := p.shin_len
	var s0: float = keys[0][0]
	var s_end: float = keys[keys.size() - 1][0]
	var step := (0.014 if lod == 0 else 0.04) * H
	var samples := []
	var sv := s0
	while sv < s_end - step * 0.4:
		samples.append(sv)
		sv += step
	samples.append(s_end)
	var pe := _pants_end()
	var cuts := []
	if pe > 0.0:
		cuts.append(minf(pe, s_end))
		cuts.append(pe - 0.02 * H)
	var boots := shoe_key == "boots"
	var boot_top := T + S * 0.4
	if boots:
		cuts.append(boot_top)
	var socks := shoe_key in ["sneakers", "booties"] and bottom_key != "onesie"
	var sock_top := T + S - 0.05 * H
	if socks:
		cuts.append(sock_top)
	samples = _merge_samples(samples, cuts, step * 0.3)
	for side in [1, -1]:
		var o2 := 0 if side == 1 else 3
		var j0: Vector3 = rest_global[B_THIGH_L + o2].origin
		var base := []
		for s in samples:
			var sf: float = s
			var k := _keys_at(keys, sf)
			var rx: float = k[0]
			var rzf: float = k[1]
			var rzb: float = k[2]
			var cz: float = k[3]
			var cx := j0.x * lerpf(1.0, 0.92, clampf(sf / (T + S), 0.0, 1.0))
			# Aussenseite Oberschenkel (weiblich)
			cx += side * p.thigh_r * 0.08 * p.fem * p.dimorph * exp(-pow((sf - 0.1 * T) / (0.25 * T), 2.0))
			var ring := PackedVector3Array()
			for j in n:
				var th := TAU * float(j) / float(n)
				var x := -rx * _spow(sin(th), 2.0 / 2.1)
				var cc := _spow(cos(th), 2.0 / 2.1)
				var z := cz + (rzf * cc if cc >= 0.0 else rzb * cc)
				ring.append(Vector3(cx + x, j0.y - sf, z))
			base.append(ring)
		var normals := _normals_of(base)
		var wf := func(_k: int, _j: int, pt: Vector3) -> Array:
			return _leg_weights(side, j0.y - pt.y)
		var skin_from := 0
		var skin_to := samples.size() - 1
		if pe > 0.0:
			var ie := _idx_of(samples, minf(pe, s_end))
			var off := []
			for s in samples:
				var sf: float = s
				var o := 0.005 * H
				match bottom_key:
					"leggings":
						o = 0.0018 * H
					"trousers", "jeans":
						# gerades Bein: unterhalb des Knies weiter
						var k := _keys_at(keys, sf)
						var r: float = (float(k[0]) + float(k[1])) * 0.5
						var target := p.knee_r * (1.12 if bottom_key == "trousers" else 1.0)
						o += maxf(0.0, target - r) * _sstep(T * 0.8, T + S * 0.2, sf)
					"joggers":
						o = 0.008 * H - 0.005 * H * _sstep(pe - 0.03 * H, pe, sf)
					"shorts":
						o = 0.008 * H + 0.004 * H * _sstep(0.0, pe, sf)
					"onesie":
						o = 0.008 * H
				off.append(o)
			var cf := func(_k: int, _j: int, pt: Vector3) -> Color:
				var sf: float = j0.y - pt.y
				var c := bottom_col
				if bottom_key == "trousers" and pt.z > 0.0 and absf(pt.x - j0.x) < 0.004 * H:
					c = c.lightened(0.06)
				if bottom_key == "jeans" and absf(absf(pt.x) - (absf(j0.x) + p.thigh_r * 0.95)) < 0.004 * H:
					c = c.lightened(0.12)
				if bottom_key == "joggers" and sf > pe - 0.03 * H:
					c = c.darkened(0.12)
				return c
			_piece(s_cloth, base, normals, 0, ie, off, cf, wf,
				func(_k: int, _j: int, _pt: Vector3) -> float: return float(_bottom_fabric()),
				false, ie == samples.size() - 1, false, ie < samples.size() - 1)
			skin_from = _idx_of(samples, pe - 0.02 * H)
		if boots:
			var ib := _idx_of(samples, boot_top)
			_piece(s_cloth, base, normals, ib, samples.size() - 1, 0.0075 * H,
				func(_k: int, _j: int, _pt: Vector3) -> Color: return shoe_col, wf,
				func(_k: int, _j: int, _pt: Vector3) -> float: return float(HumanoidMaterials.FABRIC_LEATHER),
				false, false, true, false)
			skin_to = mini(skin_to, ib + 1)
		if socks and (pe < 0.0 or pe < sock_top):
			var isk := _idx_of(samples, sock_top)
			_piece(s_cloth, base, normals, isk, samples.size() - 1, 0.0015 * H,
				func(_k: int, _j: int, _pt: Vector3) -> Color: return Color(0.93, 0.93, 0.92), wf,
				func(_k: int, _j: int, _pt: Vector3) -> float: return float(HumanoidMaterials.FABRIC_KNIT),
				false, false, true, false)
		if skin_from < skin_to and (pe < 0.0 or pe < s_end - 0.01 * H):
			var skf := func(_k: int, _j: int, pt: Vector3) -> Color:
				var sf: float = j0.y - pt.y
				var c := skin_col
				c = c.lerp(c * Color(1.0, 0.9, 0.88), 0.25 * exp(-pow((sf - T) / (0.02 * H), 2.0)) * float(pt.z > 0.0))
				return c
			_piece(s_skin, base, normals, skin_from, skin_to, 0.0, skf, wf, null, skin_from == 0, false)


# ================================================================== Fuesse und Schuhe

## Querschnitte eines Fusses / Schuhs entlang der Laenge (Modellraum).
## dw/dtop/dfront/dback: Zugaben, bottom: Unterkante, collar: max. Oberkante.
func _foot_rings(side: int, dw: float, dtop: float, dfront: float, dback: float, bottom: float, collar: float, pointy: float, n: int, nst: int) -> Array:
	var o2 := 0 if side == 1 else 3
	var F: Vector3 = rest_global[B_FOOT_L + o2].origin
	var L := p.foot_len
	var W := p.foot_w
	var keys := [
		[0.0, 0.42, 0.12],
		[0.03, 0.56, 0.17],
		[0.09, 0.68, 0.30],
		[0.2, 0.72, 0.36],
		[0.36, 0.80, 0.33],
		[0.55, 0.93, 0.24],
		[0.72, 1.0, 0.165],
		[0.86, 0.95, 0.125],
		[0.95, 0.80, 0.10],
		[1.0, 0.50, 0.075],
	]
	var foot_bottom := sole
	var yaw := side * 0.09
	var rings := []
	for i in nst + 1:
		var u := float(i) / float(nst)
		var k := _keys_at(keys, u)
		var w: float = float(k[0]) * W * 0.5 + dw
		if pointy > 0.0:
			w *= 1.0 - pointy * _sstep(0.75, 1.0, u)
		var top: float = foot_bottom + float(k[1]) * L + dtop
		if u > 0.06 and u < 0.36:
			top = maxf(top, foot_bottom + p.ankle_y + p.ankle_r * 0.6 + dtop)
		top = minf(top, collar)
		var z := -0.22 * L - dback + u * (L + dfront + dback)
		var cy := (top + bottom) * 0.5
		var ry := (top - bottom) * 0.5
		# Enden runden
		var endf := 1.0
		if u < 0.02:
			endf = 0.6
		var ring := PackedVector3Array()
		for j in n:
			var th := TAU * float(j) / float(n)
			var x := -w * endf * _spow(sin(th), 2.0 / 2.6)
			var yy := cy + ry * _spow(cos(th), 2.0 / 2.6)
			yy = maxf(yy, bottom)
			var xr := x * cos(yaw) + z * sin(yaw)
			var zr := -x * sin(yaw) + z * cos(yaw)
			ring.append(Vector3(F.x + xr, yy, F.z + zr))
		rings.append(ring)
	return rings


func _build_feet() -> void:
	var n := _seg(12)
	var nst := 12 if lod == 0 else 6
	var L := p.foot_len
	var covered := shoe_key in ["sneakers", "boots", "dress_shoes", "booties"]
	for side in [1, -1]:
		var o2 := 0 if side == 1 else 3
		var foot_b := B_FOOT_L + o2
		var shin_b := B_SHIN_L + o2
		var F: Vector3 = rest_global[foot_b].origin
		var wfun := func(pt: Vector3) -> Array:
			var t := _sstep(F.y - p.ankle_y * 0.2, F.y + p.ankle_r * 1.5, pt.y)
			return [foot_b, 1.0 - t * 0.5, shin_b, t * 0.5]
		if not covered or lod == 0:
			var fr := _foot_rings(side, 0.0, 0.0, 0.0, 0.0, sole, 1e9, 0.0, n, nst)
			var cols := []
			var wts := []
			for ring in fr:
				var cr := PackedColorArray()
				var wr := []
				for q in ring:
					var c := skin_col
					# Zehen- und Sohlenbereich leicht roetlich
					if q.y < sole + 0.01 * L:
						c = c.lerp(c * Color(1.05, 0.88, 0.85), 0.35)
					cr.append(c)
					wr.append(wfun.call(q))
				cols.append(cr)
				wts.append(wr)
			_loft(s_skin, fr, cols, wts, true, true)
		if shoe_key == "barefoot":
			continue
		var dw := 0.05 * L
		var dtop := 0.05 * L
		var dfront := 0.05 * L
		var dback := 0.03 * L
		var collar := 1e9
		var pointy := 0.0
		var sole_col := Color(0.95, 0.95, 0.93)
		var sole_h := sole + 0.03 * L
		var upper_f := HumanoidMaterials.FABRIC_COTTON
		var sole_f := HumanoidMaterials.FABRIC_RUBBER
		var bottom := 0.0
		match shoe_key:
			"sneakers":
				collar = sole + 0.34 * L
			"boots":
				sole_col = Color(0.12, 0.1, 0.09)
				upper_f = HumanoidMaterials.FABRIC_LEATHER
				dw = 0.06 * L
			"dress_shoes":
				collar = sole + 0.26 * L
				pointy = 0.35
				dw = 0.035 * L
				dtop = 0.03 * L
				dfront = 0.09 * L
				sole_col = Color(0.1, 0.08, 0.07)
				sole_h = sole + 0.01 * L
				upper_f = HumanoidMaterials.FABRIC_LEATHER
			"flats":
				collar = sole + 0.17 * L
				pointy = 0.15
				dw = 0.03 * L
				dtop = 0.02 * L
				dfront = 0.03 * L
				sole_col = shoe_col.darkened(0.4)
				sole_h = sole + 0.005 * L
				upper_f = HumanoidMaterials.FABRIC_LEATHER
			"booties":
				dw = 0.08 * L
				dtop = 0.08 * L
				dfront = 0.06 * L
				dback = 0.05 * L
				collar = sole + p.ankle_y + 0.25 * L
				sole_col = shoe_col.darkened(0.15)
				upper_f = HumanoidMaterials.FABRIC_KNIT
				sole_f = HumanoidMaterials.FABRIC_KNIT
			"sandals":
				# Sohle + zwei Riemen
				var sr := _foot_rings(side, 0.04 * L, 0.0, 0.03 * L, 0.02 * L, 0.0, sole + 0.012 * L, 0.0, n, nst)
				_shoe_loft(sr, Color(0.35, 0.24, 0.16), Color(0.35, 0.24, 0.16), 1e9, HumanoidMaterials.FABRIC_LEATHER, HumanoidMaterials.FABRIC_RUBBER, wfun)
				var fr2 := _foot_rings(side, 0.012 * L, 0.012 * L, 0.0, 0.0, sole - 0.002 * L, 1e9, 0.0, n, nst)
				for u in [0.3, 0.62]:
					var i0 := int(round(float(u) * nst))
					var band := [fr2[maxi(i0 - 1, 0)], fr2[i0]]
					var bc := []
					var bw := []
					for ring in band:
						var wr := []
						for q in ring:
							wr.append(wfun.call(q))
						bc.append(shoe_col)
						bw.append(wr)
					s_cloth.param = HumanoidMaterials.FABRIC_LEATHER
					_loft(s_cloth, band, bc, bw, false, false)
				continue
		var rings := _foot_rings(side, dw, dtop, dfront, dback, bottom, collar, pointy, n, nst)
		_shoe_loft(rings, shoe_col, sole_col, sole_h, upper_f, sole_f, wfun)


func _shoe_loft(rings: Array, upper: Color, sole_c: Color, sole_h: float, upper_f: int, sole_f: int, wfun: Callable) -> void:
	var cols := []
	var wts := []
	var prms := []
	var nr := rings.size()
	for k in nr:
		var ring: PackedVector3Array = rings[k]
		var cr := PackedColorArray()
		var wr := []
		var pr := PackedFloat32Array()
		for q in ring:
			var is_sole := q.y < sole_h
			var c := sole_c if is_sole else upper
			# Schnuerung/Kappe bei Turnschuhen
			if shoe_key == "sneakers" and not is_sole and k > nr * 0.35 and k < nr * 0.72 and q.y > sole_h + 0.02 * H:
				c = upper.lerp(Color(0.95, 0.95, 0.95), 0.35)
			cr.append(c)
			wr.append(wfun.call(q))
			pr.append(float(sole_f if is_sole else upper_f))
		cols.append(cr)
		wts.append(wr)
		prms.append(pr)
	_loft(s_cloth, rings, cols, wts, true, true, true, prms)


# ================================================================== Haende

func _build_hands() -> void:
	var hl := p.hand_len
	var hand_w := 0.44 * hl * (1.0 + (p.weight - 0.5) * 0.15)
	var palm_len := 0.52 * hl
	var finger_len := 0.48 * hl
	for side in [1, -1]:
		var o := 0 if side == 1 else 4
		var hb := B_HAND_L + o
		var fb := B_FOREARM_L + o
		var Wp: Vector3 = rest_global[hb].origin
		var m := -float(side)
		var n := _seg(10)
		var keys := [
			[0.0, p.wrist_r * 0.72, p.wrist_r * 1.0],
			[0.25, 0.115 * hl, 0.42 * hand_w],
			[0.7, 0.115 * hl, 0.5 * hand_w],
			[1.0, 0.1 * hl, 0.48 * hand_w],
		]
		var mitten := lod > 0
		var total := palm_len + (finger_len * 0.9 if mitten else 0.0)
		var nst := 6 if lod == 0 else 4
		var rings := []
		var cols := []
		var wts := []
		for i in nst + 1:
			var t := float(i) / float(nst)
			var yv := t * total
			var k := _keys_at(keys, minf(yv / palm_len, 1.0))
			var rx: float = k[0]
			var rz: float = k[1]
			if mitten and yv > palm_len:
				var f := (yv - palm_len) / (total - palm_len)
				rz *= lerpf(1.0, 0.55, f * f)
				rx *= lerpf(1.0, 0.6, f)
			var ring := PackedVector3Array()
			var cr := PackedColorArray()
			for j in n:
				var th := TAU * float(j) / float(n)
				var x := -rx * _spow(sin(th), 2.0 / 2.4)
				var z := rz * _spow(cos(th), 2.0 / 2.4)
				# leichte Woelbung zur Handinnenflaeche
				var q := Wp + Vector3(x + m * 0.02 * hl * t, -yv, z)
				ring.append(q)
				cr.append(skin_col.lerp(skin_col.lightened(0.08) * Color(1.0, 0.95, 0.95), 0.6 * float(x * m > 0.0)))
			rings.append(ring)
			cols.append(cr)
			wts.append([hb, 1.0] if i > 0 else [hb, 0.7, fb, 0.3])
		_loft(s_skin, rings, cols, wts, false, true)
		var fr := 0.052 * hl * (1.0 + (p.weight - 0.5) * 0.2) * (1.0 + p.baby_fat * 0.2)
		# Daumen
		var tb := Wp + Vector3(m * 0.03 * hl, -0.16 * hl, 0.36 * hand_w)
		var d1 := Vector3(m * 0.35, -0.7, 0.6).normalized()
		var d2 := Vector3(m * 0.6, -0.72, 0.35).normalized()
		var tpts := [tb, tb + d1 * 0.22 * hl, tb + d1 * 0.22 * hl + d2 * 0.2 * hl]
		_path_tube(s_skin, tpts, [fr * 1.3, fr * 1.15, fr * 0.95], skin_col, [hb, 1.0], 6 if lod == 0 else 5)
		if mitten:
			continue
		var zs := [0.33, 0.11, -0.11, -0.32]
		var lens := [0.92, 1.0, 0.94, 0.76]
		var rads := [1.0, 1.04, 0.97, 0.85]
		for i in 4:
			var fl: float = finger_len * float(lens[i])
			var r: float = fr * float(rads[i])
			var bp := Wp + Vector3(m * 0.01 * hl, -palm_len * 0.93, float(zs[i]) * hand_w * 1.9 * 0.5)
			var pts := [bp]
			var ang := 0.0
			var cur := bp
			var segs := [0.45, 0.3, 0.25]
			var curls := [0.15, 0.32, 0.38]
			for si in 3:
				ang += float(curls[si])
				var dir := Vector3(m * sin(ang), -cos(ang), float(zs[i]) * 0.12)
				cur = cur + dir.normalized() * fl * float(segs[si])
				pts.append(cur)
			_path_tube(s_skin, pts, [r, r * 0.95, r * 0.88, r * 0.78], skin_col, [hb, 1.0], 6)


# ================================================================== Ausrichtung

## Dreht die Ringfolge um, falls die Flaeche nach innen zeigen wuerde.
func _orient(rings: Array, i: int, outward: Vector3) -> void:
	var r0: PackedVector3Array = rings[0]
	var r1: PackedVector3Array = rings[1]
	var n := r0.size()
	var rt := r0[(i + 1) % n] - r0[i]
	var at := r1[i] - r0[i]
	if rt.cross(at).dot(outward) < 0.0:
		for k in rings.size():
			var rv: PackedVector3Array = rings[k]
			rv.reverse()
			rings[k] = rv


func _merge_into(dst: Surf, src: Surf, param: float) -> void:
	var base := dst.v.size()
	for i in src.v.size():
		dst.v.append(src.v[i])
		dst.col.append(src.col[i])
		dst.uv.append(src.uv[i])
		dst.uv2.append(Vector2(src.uv2[i].x, param))
		for k in 4:
			dst.bones.append(src.bones[i * 4 + k])
			dst.weights.append(src.weights[i * 4 + k])
	for q in src.idx:
		dst.idx.append(base + q)
	src.v.clear()
	src.idx.clear()


func _build_lod2() -> void:
	_build_torso()
	_build_skirt()
	_build_arms()
	_build_legs()
	_build_feet()
	_build_hands()
	_build_head()
	_build_hair()
	for side in [1.0, -1.0]:
		_sphere(s_cloth, _hm(Vector3(side * ex, e, eye_z + p.eye_r * 0.8)), Vector3.ONE * p.eye_r * hh * 0.45, Color(0.08, 0.06, 0.05), [B_HEAD, 1.0], 5, 4)
	if headwear != "":
		_build_accessories()
	_merge_into(s_cloth, s_skin, float(HumanoidMaterials.FABRIC_SKIN))
	_merge_into(s_cloth, s_hair, float(HumanoidMaterials.FABRIC_COTTON))


# ================================================================== Kopf

func _beard_mask(style: String, hp: Vector3) -> float:
	var ax := absf(hp.x)
	var y := hp.y
	var ear := _sstep(-0.1, -0.03, hp.z)
	var lips := pow(hp.x / 0.115, 2.0) + pow((y - mouth_y) / 0.042, 2.0)
	var hole := _sstep(0.85, 1.25, lips)
	var mous := (1.0 - _sstep(0.1, 0.135, ax)) * _sstep(mouth_y + 0.02, mouth_y + 0.035, y) * (1.0 - _sstep(nose_b - 0.022, nose_b - 0.006, y)) * _sstep(0.15, 0.25, hp.z)
	match style:
		"full", "stubble":
			var line := lerpf(e - 0.2, e - 0.035, _sstep(0.08, 0.3, ax))
			return (1.0 - _sstep(line - 0.02, line + 0.02, y)) * ear * hole
		"moustache":
			return mous
		"goatee":
			var chin := (1.0 - _sstep(0.09, 0.13, ax)) * (1.0 - _sstep(mouth_y - 0.045, mouth_y - 0.028, y)) * _sstep(-0.05, 0.1, hp.z)
			return maxf(mous, chin * hole)
		"chin_strap":
			var lj := lerpf(-0.47, e - 0.15, _sstep(0.12, 0.33, ax))
			return maxf(1.0 - _sstep(0.022, 0.038, absf(y - lj)), 1.0 - _sstep(-0.47, -0.44, y)) * ear * hole
	return 0.0


func _head_color(hp: Vector3) -> Color:
	var c := skin_col
	var x := hp.x
	var y := hp.y
	var ax := absf(x)
	var front := clampf(hp.z * 3.0, 0.0, 1.0)
	var mk := float(app.makeup)
	var g := _g2((ax - 0.19 * hw_scale) / 0.08, (y - (e - 0.13)) / 0.07) * front
	c = c.lerp(c * Color(1.07, 0.84, 0.82), g * (0.18 + 0.35 * p.baby_fat + mk * 0.12))
	if mk > 0.0:
		var ge := _g2((ax - ex) / 0.07, (y - (e + 0.035)) / 0.028) * front
		c = c.lerp(Color(0.42, 0.3, 0.32), ge * 0.18 * mk)
	var gu := _g2((ax - ex) / 0.06, (y - (e - 0.065)) / 0.028) * front
	c = c.darkened(gu * (0.04 + 0.1 * p.skin_age))
	# Bartschatten
	var bs := 0.0
	if p.age >= 15.0 and p.fem < 0.5:
		bs = 0.05
	match beard_key:
		"stubble":
			bs = 0.38
		"full", "goatee", "moustache", "chin_strap":
			bs = 0.18
	if bs > 0.0:
		var m := _beard_mask("full", hp)
		c = c.lerp(hair_col.darkened(0.3).lerp(Color(0.6, 0.6, 0.6), p.grey * 0.6), m * bs)
	# Faltenzonen (Alpha)
	var z := 0.0
	z += _sstep(e + 0.12, e + 0.17, y) * (1.0 - _sstep(e + 0.3, e + 0.36, y)) * front * (1.0 - _sstep(0.2, 0.28, ax))
	z += _g2((ax - (ex + 0.1)) / 0.035, (y - e) / 0.045)
	z += _g2((ax - 0.12) / 0.03, (y - (nose_b - 0.05)) / 0.06) * front
	z += _g2((ax - ex) / 0.06, (y - (e - 0.07)) / 0.025) * front
	z += (1.0 - _sstep(-0.48, -0.4, y)) * 0.6
	c.a = clampf(z + 0.12, 0.0, 1.0)
	return c


func _jaw_w(hp: Vector3) -> float:
	return _sstep(mouth_y + 0.004, mouth_y - 0.05, hp.y) * _sstep(-0.12, 0.08, hp.z)


func _build_head() -> void:
	var n := 44 if lod == 0 else (18 if lod == 1 else 10)
	var thetas := _head_thetas(n)
	var ys := []
	var step := 0.018 if lod == 0 else (0.06 if lod == 1 else 0.12)
	var y := -0.5
	while y < 0.2:
		ys.append(y)
		y += step
	var top := [0.26, 0.32, 0.37, 0.41, 0.445, 0.47, 0.488] if lod == 0 else [0.3, 0.4, 0.465]
	ys.append_array(top)
	var rings := []
	var cols := []
	var wts := []
	for yy in ys:
		var ring := PackedVector3Array()
		var cr := PackedColorArray()
		var wr := []
		for th in thetas:
			var hp := _head_pt(float(yy), th, lod < 2)
			ring.append(_hm(hp))
			cr.append(_head_color(hp))
			var wj := _jaw_w(hp)
			wr.append([B_HEAD, 1.0 - wj, B_JAW, wj])
		rings.append(ring)
		cols.append(cr)
		wts.append(wr)
	_loft(s_skin, rings, cols, wts, true, true)


func _build_face() -> void:
	var er := p.eye_r
	# ---------------------------------------------- Augaepfel
	for side in [1, -1]:
		var eb := B_EYE_L if side == 1 else B_EYE_R
		var c := _hm(Vector3(side * ex, e, eye_z))
		var r := er * hh
		var seg := 14 if lod == 0 else 8
		var rn := 10 if lod == 0 else 6
		var base := s_eye.v.size()
		for k in range(rn + 1):
			var phi := PI * float(k) / float(rn)
			for j in seg:
				var th := TAU * float(j) / float(seg)
				var d := Vector3(sin(phi) * sin(th), -cos(phi), sin(phi) * cos(th))
				s_eye.add_uv(c + d * r, app.eye_color, [eb, 1.0], Vector2(d.x, d.y), Vector2(d.z, 0.0))
		for k in rn:
			for j in seg:
				var j1 := (j + 1) % seg
				var a := base + k * seg + j
				var b := base + k * seg + j1
				var cc := base + (k + 1) * seg + j1
				var dd := base + (k + 1) * seg + j
				s_eye.tri(a, cc, b)
				s_eye.tri(a, dd, cc)
	if lod > 0:
		_build_brows()
		_build_nose()
		_build_lips()
		_build_ears()
		return
	# ---------------------------------------------- Lider
	var R := er * 1.09
	var cols_n := 11
	for side in [1, -1]:
		var cen := Vector3(side * ex, e, eye_z)
		for upper in [true, false]:
			var rings := []
			var cols := []
			var wts := []
			var rows := [0.0, 0.08, 0.25, 0.5, 0.75, 1.0]
			for t in rows:
				var ring := PackedVector3Array()
				var cr := PackedColorArray()
				for j in cols_n:
					var lam := lerpf(-1.75, 1.75, float(j) / float(cols_n - 1))
					var edge := 0.30 * cos(lam * 1.2) - 0.03 if upper else -0.30 * cos(lam * 1.2) + 0.06
					edge = clampf(edge, -0.4, 0.4)
					var far := 1.45 if upper else -1.4
					var phi := lerpf(edge, far, float(t))
					var rr := R * (0.985 if t == 0.0 else 1.0)
					var d := Vector3(sin(lam) * cos(phi), sin(phi), cos(lam) * cos(phi))
					ring.append(_hm(cen + d * rr))
					var col := skin_col.darkened(0.04)
					if t == 0.0:
						col = Color(0.08, 0.05, 0.04) if upper else skin_col.darkened(0.25)
					elif t == 0.08 and upper:
						col = skin_col.lerp(Color(0.1, 0.07, 0.06), 0.5 + 0.3 * float(app.makeup))
					cr.append(col)
				rings.append(ring)
				cols.append(cr)
				wts.append([B_LIDS, 1.0] if upper else [B_HEAD, 1.0])
			_orient(rings, cols_n / 2, Vector3(0, 0, 1))
			_loft(s_skin, rings, cols, wts, false, false, false)
	_build_brows()
	_build_nose()
	_build_lips()
	_build_ears()


func _build_brows() -> void:
	var np := 7 if lod == 0 else 4
	for side in [1, -1]:
		var rings := []
		var cols := []
		var wts := []
		for row in 3:
			var ring := PackedVector3Array()
			for i in np:
				var t := float(i) / float(np - 1)
				var x: float = side * (ex - 0.078 + t * 0.175) * hw_scale
				var yc := e + 0.083 + 0.024 * sin(PI * pow(t, 0.75)) - 0.014 * t + (face_brows - 0.5) * 0.01
				var th := (0.012 + 0.018 * face_brows) * (1.0 - 0.55 * t) * lerpf(0.55, 1.0, p.maturity)
				var yy := yc + (float(row) - 1.0) * th * 0.5
				var hp := _head_front(x, yy)
				hp.z += 0.005 + (0.003 if row == 1 else 0.0)
				ring.append(_hm(hp))
			rings.append(ring)
			cols.append(brow_col)
			wts.append([B_HEAD, 1.0])
		# Reihen laufen nach oben, Punkte nach aussen (+-X): Vorderseite pruefen
		var r0: PackedVector3Array = rings[0]
		var rings_t := []
		for i in np:
			var col := PackedVector3Array()
			for row in 3:
				var rr: PackedVector3Array = rings[row]
				col.append(rr[i])
			rings_t.append(col)
		var cl := []
		var wl := []
		for i in np:
			cl.append(brow_col)
			wl.append([B_HEAD, 1.0])
		_orient(rings_t, 0, Vector3(0, 0, 1))
		_loft(s_hair, rings_t, cl, wl, false, false, false)


func _build_nose() -> void:
	var ns := p.nose_scale * (0.8 + 0.4 * face_nose)
	var nw := p.nose_scale * (0.88 + 0.24 * face_nose) * lerpf(1.15, 1.0, p.maturity)
	var y0 := e + 0.01
	var yb := nose_b
	var tab := [[0.0, 0.032, 0.003], [0.3, 0.035, 0.022], [0.6, 0.04, 0.045], [0.8, 0.05, 0.064], [0.92, 0.06, 0.078], [1.0, 0.07, 0.068]]
	var rows := []
	for t in tab:
		rows.append([lerpf(y0, yb, float(t[0])), float(t[1]) * nw, float(t[2]) * ns, float(t[0])])
	rows.append([yb - 0.006, 0.068 * nw, 0.045 * ns, 1.1])
	rows.append([yb + 0.004, 0.056 * nw, 0.01 * ns, 1.2])
	var nc := 11 if lod == 0 else 5
	var rings := []
	var cols := []
	var wts := []
	for r in rows:
		var ring := PackedVector3Array()
		var cr := PackedColorArray()
		for j in nc:
			var phi := lerpf(-PI * 0.5, PI * 0.5, float(j) / float(nc - 1))
			var w: float = r[1]
			var t: float = r[3]
			var x := w * sin(phi)
			var ala := 0.012 * ns * _sstep(0.72, 1.0, t) * pow(absf(sin(phi)), 2.0)
			x += signf(x) * ala * 0.6
			var surf := _head_front(x, r[0])
			var z := surf.z - 0.004 + float(r[2]) * pow(maxf(cos(phi), 0.0), 1.3) + ala
			ring.append(_hm(Vector3(x, r[0], z)))
			var c := skin_col
			if t > 1.05 and absf(phi) > 0.25 and absf(phi) < 1.0:
				c = skin_col.darkened(0.55)
			elif t > 0.85:
				c = c.lerp(c * Color(1.04, 0.9, 0.88), 0.25)
			c.a = 0.1
			cr.append(c)
		rings.append(ring)
		cols.append(cr)
		wts.append([B_HEAD, 1.0])
	_orient(rings, nc / 2, Vector3(0, 0, 1))
	_loft(s_skin, rings, cols, wts, false, false, false)


func _build_lips() -> void:
	var fem := p.fem * p.dimorph
	var mw := lerpf(0.082, 0.108, p.maturity) * (1.0 + (face_jaw - 0.5) * 0.12)
	var lip := lerpf(0.85, 1.0, p.maturity) * lerpf(1.0, 1.22, fem) * (1.0 - p.skin_age * 0.2)
	var lc := skin_col.lerp(skin_col * Color(0.86, 0.55, 0.55), 0.55)
	if int(app.makeup) == 2:
		lc = Color(0.62, 0.12, 0.18)
	elif int(app.makeup) == 1:
		lc = lc.lerp(Color(0.75, 0.38, 0.4), 0.4)
	lc.a = 0.0
	var ns := 9 if lod == 0 else 5
	var nr := 8 if lod == 0 else 5
	for upper in [true, false]:
		var r0 := (0.018 if upper else 0.023) * lip
		var rings := []
		var cols := []
		var wts := []
		for i in ns:
			var t := float(i) / float(ns - 1)
			var x := lerpf(-mw, mw, t)
			var prof := sqrt(maxf(1.0 - pow(x / mw, 2.0), 0.0))
			prof = maxf(prof, 0.12)
			var ry := r0 * prof
			var rz := r0 * 0.85 * prof
			var cy := mouth_y + (ry * 0.75 if upper else -ry * 0.8)
			if upper:
				cy += 0.004 * cos(clampf(x / (0.35 * mw), -PI, PI)) * prof
			var surf := _head_front(x, cy)
			var cz := surf.z - r0 * 0.35 * prof - 0.006 * (1.0 - prof)
			var ring := PackedVector3Array()
			for j in nr:
				var phi := TAU * float(j) / float(nr)
				ring.append(_hm(Vector3(x, cy - ry * sin(phi), cz + rz * cos(phi))))
			rings.append(ring)
			cols.append(lc)
			wts.append([B_HEAD, 1.0] if upper else [B_JAW, 1.0])
		_loft(s_skin, rings, cols, wts, true, true)
	# Mundinneres
	var mi := PackedVector3Array()
	var cm := _head_front(0.0, mouth_y)
	for j in 10:
		var t := TAU * float(j) / 10.0
		var x := mw * 0.85 * cos(t)
		var yy := mouth_y + 0.03 * sin(t)
		var sf := _head_front(x, yy)
		mi.append(_hm(Vector3(x, yy, sf.z - 0.018)))
	var ci := s_skin.add(_hm(Vector3(0.0, mouth_y, cm.z - 0.03)), Color(0.18, 0.05, 0.05, 0.0), [B_HEAD, 0.5, B_JAW, 0.5])
	var b0 := s_skin.v.size()
	for q in mi:
		s_skin.add(q, Color(0.22, 0.07, 0.07, 0.0), [B_HEAD, 0.5, B_JAW, 0.5])
	for j in 10:
		var j1 := (j + 1) % 10
		var a := b0 + j
		var b := b0 + j1
		var fn := (s_skin.v[b] - s_skin.v[ci]).cross(s_skin.v[a] - s_skin.v[ci])
		if fn.z >= 0.0:
			s_skin.tri(ci, a, b)
		else:
			s_skin.tri(ci, b, a)


func _build_ears() -> void:
	var es := p.ear_scale
	var ey := e - 0.095 * lf / 0.5
	for side in [1, -1]:
		var sp := _head_pt(ey, side * PI * 0.55, false)
		var center := Vector3(sp.x - side * 0.008, ey, sp.z - 0.03)
		var back := 0.38
		var wdir := Vector3(side * cos(back), 0.0, -sin(back) * 0.6).normalized()
		var udir := Vector3(0, 0, 1).cross(Vector3.UP).cross(wdir).normalized()
		udir = Vector3.UP.cross(wdir).normalized() * float(side)
		var vdir := Vector3(0, 1, 0)
		var prof := [[0.0, 0.004], [0.35, 0.011], [0.62, 0.022], [0.86, 0.03], [1.0, 0.024], [1.04, 0.006], [0.9, -0.02]] if lod == 0 else [[0.0, 0.01], [0.8, 0.026], [1.0, 0.012], [0.85, -0.02]]
		var nseg := 14 if lod == 0 else 7
		var rings := []
		var cols := []
		var wts := []
		var a := 0.068 * es
		var b := 0.128 * es
		for pr in prof:
			var ring := PackedVector3Array()
			var cr := PackedColorArray()
			for j in nseg:
				var t := TAU * float(j) / float(nseg)
				var sc: float = pr[0]
				var lobe := 1.0 + 0.12 * _sstep(-0.3, -1.0, sin(t))
				var q := center + udir * (a * sc * cos(t)) + vdir * (b * sc * sin(t) * lobe) + wdir * (float(pr[1]) * es)
				# Ohr oben leicht abstehend
				q += wdir * 0.012 * maxf(sin(t), 0.0) * sc
				ring.append(_hm(q))
				var c := skin_col.lerp(skin_col * Color(1.05, 0.86, 0.84), 0.3 * sc)
				c.a = 0.1
				cr.append(c)
			rings.append(ring)
			cols.append(cr)
			wts.append([B_HEAD, 1.0])
		var r0: PackedVector3Array = rings[0]
		var r1: PackedVector3Array = rings[1]
		var rt := r0[1] - r0[0]
		var at := r1[0] - r0[0]
		if rt.cross(at).dot(wdir) < 0.0:
			for k in rings.size():
				var rv: PackedVector3Array = rings[k]
				rv.reverse()
				rings[k] = rv
		_loft(s_skin, rings, cols, wts, true, false)


# ================================================================== Haare

func _hairline(th: float) -> float:
	var a := absf(th)
	var front := e + 0.29 - 0.03 * a * a
	match hair_key:
		"bob", "long_wavy":
			front = e + 0.2 - 0.02 * a * a
		"receding":
			var thin := 0.4 + 0.6 * _sstep(25.0, 70.0, p.age)
			front += (0.05 + 0.1 * _sstep(0.15, 0.7, a) * (1.0 - _sstep(0.9, 1.25, a))) * thin
		"side_part":
			if th < 0.0:
				front -= 0.05 * (1.0 - _sstep(0.2, 0.9, a))
	var yh := front
	yh = lerpf(yh, e - 0.07, _sstep(0.95, 1.4, a))
	var over_ear := e + 0.065
	if hair_key in ["long", "long_wavy", "bob", "afro", "curly"]:
		over_ear = e - 0.1
	yh = lerpf(yh, over_ear, _sstep(1.45, 1.62, a))
	yh = lerpf(yh, e - 0.38, _sstep(1.95, 2.55, a))
	return yh


## Haardicke (Kopfeinheiten) an (y, th); <= 0: keine Haare.
func _hair_t(y: float, th: float) -> float:
	if hair_key == "bald":
		return -1.0
	var yh := _hairline(th)
	var mask := _sstep(yh - 0.01, yh + 0.035, y)
	var top := _sstep(e + 0.15, 0.45, y)
	var t := 0.03
	var bumps := sin(th * 7.0 + y * 31.0) * sin(th * 11.0 - y * 23.0)
	match hair_key:
		"short":
			t = 0.028 + 0.02 * top + 0.01 * (1.0 - _sstep(0.0, 0.8, absf(th))) * _sstep(e + 0.24, e + 0.32, y)
		"buzz":
			t = 0.007
		"side_part":
			t = 0.038 + 0.026 * top
			var hp := _head_pt(y, th, false)
			t -= 0.02 * exp(-pow((hp.x - 0.1) / 0.018, 2.0)) * _sstep(e + 0.2, e + 0.3, y)
			if hp.x < 0.1:
				t += 0.008 * top
		"undercut":
			var hp2 := _head_pt(y, th, false)
			var topz := _sstep(e + 0.2, e + 0.27, y) * (1.0 - _sstep(0.2, 0.27, absf(hp2.x)))
			t = lerpf(0.007, 0.065, topz)
		"long":
			t = 0.03 + 0.012 * top
		"long_wavy":
			t = 0.04 + 0.014 * top + 0.006 * bumps
		"bob":
			t = 0.042 + 0.012 * top
		"ponytail", "bun", "pigtails":
			t = 0.02
		"curly":
			t = 0.065 + 0.028 * bumps + 0.01 * top
		"afro":
			t = 0.16 + 0.07 * top + 0.02 * bumps
		"receding":
			t = 0.02
			var thin := 0.4 + 0.6 * _sstep(25.0, 70.0, p.age)
			mask *= 1.0 - 0.95 * thin * _sstep(e + 0.26, e + 0.36, y) * (1.0 - _sstep(1.3, 2.0, absf(th)))
	t *= lerpf(0.35, 1.0, _sstep(0.5, 4.0, p.age))
	if p.age < 1.5:
		mask *= 0.85
	if headwear != "":
		t = minf(t, 0.05 if hair_key == "afro" else 0.035)
	return t if mask > 0.02 else -1.0 if mask <= 0.0 else t * mask


func _hair_mask(y: float, th: float) -> float:
	var yh := _hairline(th)
	return _sstep(yh - 0.01, yh + 0.035, y)


func _build_hair() -> void:
	if hair_key == "bald":
		return
	var n := 40 if lod == 0 else (16 if lod == 1 else 10)
	var step := 0.025 if lod == 0 else (0.07 if lod == 1 else 0.12)
	var ys := []
	var y := e - 0.4
	while y < 0.47:
		ys.append(y)
		y += step
	ys.append(0.49)
	var hc := hair_col
	var center := Vector3(0.0, e * 0.3, -0.03)
	var rings := []
	var cols := []
	var wts := []
	for k in ys.size():
		var yy: float = ys[k]
		var ring := PackedVector3Array()
		var cr := PackedColorArray()
		for j in n:
			var th := TAU * float(j) / float(n) - PI
			var base := _head_pt(yy, th, false)
			var dir := (base - center).normalized()
			var t := _hair_t(yy, th)
			var m := _hair_mask(yy, th)
			if k == 0:
				m = 0.0
			var off := lerpf(-0.03, maxf(t, 0.004), m) if t > 0.0 else -0.03
			ring.append(_hm(base + dir * off))
			# Wurzelansatz etwas dunkler, Spitzen etwas heller
			cr.append(hc.darkened(0.12 * (1.0 - m)))
		rings.append(ring)
		cols.append(cr)
		wts.append([B_HEAD, 1.0])
	_loft(s_hair, rings, cols, wts, false, true)
	if lod == 2:
		return
	match hair_key:
		"long":
			_hair_curtain((p.shoulder_y - 0.13 * H + sole - head_c.y) / hh, 0.035, 0.0, 1.75)
		"long_wavy":
			_hair_curtain((p.shoulder_y - 0.09 * H + sole - head_c.y) / hh, 0.045, 1.0, 1.7)
		"bob":
			_hair_curtain(-0.4, 0.04, 0.0, 1.3)
		"ponytail":
			var a := _head_pt(e + 0.2, PI, false)
			var pts := [a + Vector3(0, 0, -0.01), a + Vector3(0, -0.05, -0.09), Vector3(0, e - 0.1, a.z - 0.13), Vector3(0, e - 0.4, a.z - 0.1), Vector3(0, e - 0.62, a.z - 0.06)]
			var mp := []
			for q in pts:
				mp.append(_hm(q))
			_path_tube(s_hair, mp, [0.04 * hh, 0.06 * hh, 0.058 * hh, 0.045 * hh, 0.012 * hh], hc, [B_HEAD, 1.0], 9 if lod == 0 else 6)
			_sphere(s_cloth, _hm(a + Vector3(0, -0.012, -0.035)), Vector3(0.045, 0.045, 0.03) * hh, acc_col, [B_HEAD, 1.0], 8, 5)
		"bun":
			if headwear == "" or headwear == "cap":
				var bp := _head_pt(e + 0.32, PI, false)
				_sphere(s_hair, _hm(bp + Vector3(0, 0.03, -0.06)), Vector3(0.11, 0.095, 0.1) * hh, hc, [B_HEAD, 1.0], 12 if lod == 0 else 7, 8 if lod == 0 else 5)
		"pigtails":
			for side in [1.0, -1.0]:
				var a2 := _head_pt(e + 0.08, side * 1.95, false)
				var o := Vector3(side * 0.04, 0, 0)
				var pts2 := [a2 + o, a2 + o + Vector3(side * 0.05, -0.08, -0.02), a2 + o + Vector3(side * 0.06, -0.3, 0.0), a2 + o + Vector3(side * 0.05, -0.5, 0.02)]
				var mp2 := []
				for q in pts2:
					mp2.append(_hm(q))
				_path_tube(s_hair, mp2, [0.045 * hh, 0.05 * hh, 0.04 * hh, 0.015 * hh], hc, [B_HEAD, 1.0], 8 if lod == 0 else 5)
				_sphere(s_cloth, _hm(a2 + o + Vector3(side * 0.02, -0.04, -0.01)), Vector3(0.04, 0.035, 0.04) * hh, acc_col, [B_HEAD, 1.0], 7, 5)


## Haarvorhang hinter/neben dem Kopf (lange Haare, Bob).
func _hair_curtain(y_end: float, thick: float, wave: float, open_low: float) -> void:
	var y_top := e + 0.1
	var nrow := 16 if lod == 0 else 6
	var ncol := 22 if lod == 0 else 9
	var rings := []
	var cols := []
	var wts := []
	var mid_out := 0
	for k in nrow + 1:
		var t := float(k) / float(nrow)
		var y := lerpf(y_top, y_end, t)
		var th_o := lerpf(1.4, open_low, _sstep(e - 0.05, -0.5, y))
		var inner := PackedVector3Array()
		var outer := PackedVector3Array()
		for j in ncol:
			var th := lerpf(th_o, TAU - th_o, float(j) / float(ncol - 1))
			var hy := maxf(y, e - 0.02)
			var bp := _head_pt(hy, th, false)
			var hz := lerpf(bp.z, _head_pt(hy, PI, false).z * 0.5 + bp.z * 0.5, 0.0)
			var dir := Vector3(bp.x, 0.0, bp.z - 0.0).normalized()
			var q := Vector3(bp.x, y, hz) + dir * 0.045
			if wave > 0.0:
				q += dir * 0.014 * sin(y * 40.0) * _sstep(0.0, 0.25, t)
			if hair_key == "bob" and t > 0.8:
				q -= dir * 0.03 * (t - 0.8) / 0.2
			# nicht in den Rumpf
			var mq := _hm(q)
			if mq.y - sole < p.shoulder_y + 0.05 * H:
				var bz := _torso_back_z(mq.x, mq.y - sole) - 0.012 * H
				if mq.z > bz:
					mq.z = lerpf(mq.z, bz, _sstep(p.shoulder_y + 0.05 * H, p.shoulder_y + 0.0 * H, mq.y - sole))
				q = (mq - head_c) / hh
			var tk := thick * (1.0 - 0.6 * _sstep(0.85, 1.0, t))
			inner.append(_hm(q))
			outer.append(_hm(q + dir * tk))
		var ring := PackedVector3Array()
		ring.append_array(outer)
		var rin := inner.duplicate()
		rin.reverse()
		ring.append_array(rin)
		if k == nrow:
			var mid := PackedVector3Array()
			for j in ring.size():
				var jj := ring.size() - 1 - j
				mid.append((ring[j] + ring[jj]) * 0.5)
			ring = mid
		rings.append(ring)
		var hc := hair_col.darkened(0.05 * t)
		cols.append(hc)
		var my: float = head_c.y + y * hh - sole
		var wh := _sstep(p.shoulder_y, p.chin_y + 0.1 * hh, my)
		wts.append([B_HEAD, wh, B_NECK, (1.0 - wh) * 0.3, B_CHEST, (1.0 - wh) * 0.7])
	mid_out = ncol / 2
	var r0: PackedVector3Array = rings[0]
	var outward := r0[mid_out] - _hm(Vector3(0, y_top, 0))
	_orient(rings, mid_out, outward)
	_loft(s_hair, rings, cols, wts, false, false)


func _build_beard() -> void:
	if beard_key in ["none", "stubble"] or lod == 2:
		return
	var tk := 0.03
	match beard_key:
		"full":
			tk = 0.035 + 0.02 * _sstep(30.0, 70.0, p.age)
		"goatee":
			tk = 0.024
		"moustache":
			tk = 0.02
		"chin_strap":
			tk = 0.013
	var nrow := 18 if lod == 0 else 7
	var ncol := 30 if lod == 0 else 12
	var bc := hair_col.darkened(0.1)
	var rings := []
	var cols := []
	var wts := []
	var y_top := e - 0.02
	var center := Vector3(0.0, e - 0.1, -0.05)
	for k in nrow + 1:
		var y := lerpf(-0.5, y_top, float(k) / float(nrow))
		var ring := PackedVector3Array()
		var wr := []
		for j in ncol:
			var th := lerpf(-1.85, 1.85, float(j) / float(ncol - 1))
			var hp := _head_pt(y, th, true)
			var m := _beard_mask(beard_key, hp)
			var dir := (hp - center).normalized()
			ring.append(_hm(hp + dir * lerpf(-0.012, tk, m)))
			var wj := _jaw_w(hp)
			wr.append([B_HEAD, 1.0 - wj, B_JAW, wj])
		rings.append(ring)
		cols.append(bc)
		wts.append(wr)
	_orient(rings, ncol / 2, Vector3(0, -0.2, 1))
	_loft(s_hair, rings, cols, wts, false, false, false)


# ================================================================== Kleidungsextras

func _build_clothing_extras() -> void:
	var yN := top_neck_y
	s_cloth.param = _top_fabric_base()
	match top_key:
		"shirt", "polo", "suit":
			# Kragen
			var col := Color(0.94, 0.94, 0.95) if top_key == "suit" else top_col
			var rings := []
			for row in 3:
				var yy := yN - 0.004 * H + float(row) * 0.009 * H
				var ring := PackedVector3Array()
				var nseg := 16 if lod == 0 else 8
				for j in nseg + 1:
					var th := lerpf(0.42, TAU - 0.42, float(j) / float(nseg))
					var b := _torso_pt(yy, th)
					var c := Vector3(0.0, b.y, (float(torso_keys[torso_keys.size() - 3][2]) + float(torso_keys[torso_keys.size() - 3][3])) * 0.5)
					var d := (b - c)
					d.y = 0.0
					d = d.normalized()
					ring.append(b + d * (0.012 * H - float(row) * 0.002 * H))
				rings.append(ring)
			var slab := []
			for row in 3:
				var outer: PackedVector3Array = rings[row]
				var ring2 := PackedVector3Array()
				ring2.append_array(outer)
				var inn := PackedVector3Array()
				for q in outer:
					var c2 := Vector3(0.0, q.y, -0.012 * H)
					inn.append(q - (q - c2).normalized() * 0.003 * H)
				inn.reverse()
				ring2.append_array(inn)
				slab.append(ring2)
			var r0: PackedVector3Array = slab[0]
			_orient(slab, 4, r0[4] - Vector3(0.0, r0[4].y, -0.012 * H))
			var cl := [col, col, col.darkened(0.1)]
			var wl := [[B_CHEST, 0.6, B_NECK, 0.4], [B_CHEST, 0.4, B_NECK, 0.6], [B_CHEST, 0.3, B_NECK, 0.7]]
			s_cloth.param = HumanoidMaterials.FABRIC_COTTON
			_loft(s_cloth, slab, cl, wl, false, false)
		"hoodie":
			var pts := []
			var nseg := 12 if lod == 0 else 6
			for j in nseg + 1:
				var th := lerpf(1.0, TAU - 1.0, float(j) / float(nseg))
				var b := _torso_pt(yN - 0.012 * H, th)
				var back := maxf(-cos(th), 0.0)
				b.y += 0.012 * H * back
				var d := Vector3(b.x, 0.0, b.z + 0.012 * H).normalized()
				pts.append(b + d * 0.012 * H)
			_path_tube(s_cloth, pts, [0.016 * H], top_col.darkened(0.06), [B_CHEST, 0.6, B_NECK, 0.4], 8 if lod == 0 else 5)
			if lod == 0:
				for side in [1.0, -1.0]:
					var f := _torso_pt(yN - 0.02 * H, side * 0.22)
					var dp := [f + Vector3(0, 0, 0.014 * H), f + Vector3(0, -0.07 * H, 0.016 * H)]
					_path_tube(s_cloth, dp, [0.0025 * H], Color(0.92, 0.92, 0.9), [B_CHEST, 1.0], 5)


# ================================================================== Accessoires

func _build_accessories() -> void:
	var gold := Color(0.86, 0.69, 0.32)
	for a in acc:
		s_cloth.param = HumanoidMaterials.FABRIC_PLASTIC
		match str(a):
			"glasses", "sunglasses":
				if lod == 2:
					continue
				var sun: bool = a == "sunglasses"
				var fc := Color(0.06, 0.06, 0.07) if sun else Color(0.16, 0.1, 0.07)
				var lw := 0.075 * hw_scale
				var lh := 0.05 if sun else 0.045
				var lz := _head_front(ex, e + 0.01).z + 0.05
				for side in [1.0, -1.0]:
					var pts := []
					var npt := 14 if lod == 0 else 8
					for j in npt:
						var t := TAU * float(j) / float(npt)
						var x: float = side * ex + lw * cos(t)
						var y := e + 0.005 + lh * _spow(sin(t), 0.8)
						pts.append(_hm(Vector3(x, y, lz - 0.012 * pow(absf(cos(t)), 2.0) * float(side * cos(t) > 0.0))))
					_path_tube(s_cloth, pts, [0.006 * hh], fc, [B_HEAD, 1.0], 5, false, false, true)
					if sun:
						var lens := PackedVector3Array()
						for q in pts:
							lens.append(q)
						var ci := s_cloth.add(_hm(Vector3(side * ex, e + 0.005, lz + 0.002)), Color(0.04, 0.05, 0.06), [B_HEAD, 1.0])
						s_cloth.uv2[ci] = Vector2(s_cloth.uv2[ci].x, HumanoidMaterials.FABRIC_GLASS)
						var b0 := s_cloth.v.size()
						for q in lens:
							var vi := s_cloth.add(q, Color(0.04, 0.05, 0.06), [B_HEAD, 1.0])
							s_cloth.uv2[vi] = Vector2(s_cloth.uv2[vi].x, HumanoidMaterials.FABRIC_GLASS)
						for j in lens.size():
							var j1 := (j + 1) % lens.size()
							var fn := (s_cloth.v[b0 + j1] - s_cloth.v[ci]).cross(s_cloth.v[b0 + j] - s_cloth.v[ci])
							if fn.z >= 0.0:
								s_cloth.tri(ci, b0 + j, b0 + j1)
							else:
								s_cloth.tri(ci, b0 + j1, b0 + j)
					# Buegel
					var ox: float = side * (ex + lw)
					var sw := _head_w(e + 0.02) + 0.012
					var tp := [_hm(Vector3(ox, e + 0.02, lz - 0.01)), _hm(Vector3(side * sw, e + 0.02, lz - 0.08)), _hm(Vector3(side * (sw - 0.005), e + 0.0, -0.06)), _hm(Vector3(side * (sw - 0.02), e - 0.06, -0.1))]
					_path_tube(s_cloth, tp, [0.005 * hh], fc, [B_HEAD, 1.0], 4)
				var bz := _head_front(0.0, e + 0.03).z + 0.012
				_path_tube(s_cloth, [_hm(Vector3(ex - lw, e + 0.02, lz - 0.005)), _hm(Vector3(0, e + 0.035, bz)), _hm(Vector3(-ex + lw, e + 0.02, lz - 0.005))], [0.005 * hh], fc, [B_HEAD, 1.0], 4)
			"cap", "beanie", "hat":
				_build_headwear(str(a))
			"earrings":
				if lod == 2:
					continue
				var ey := e - 0.095 * lf / 0.5 - 0.115 * p.ear_scale
				for side in [1.0, -1.0]:
					var sp := _head_pt(ey + 0.1, side * PI * 0.55, false)
					var c := Vector3(sp.x + side * 0.012, ey - 0.02, sp.z - 0.035)
					var pts := []
					for j in 10:
						var t := TAU * float(j) / 10.0
						pts.append(_hm(c + Vector3(0, 0.026 * sin(t) - 0.026, 0.026 * cos(t))))
					_path_tube(s_cloth, pts, [0.004 * hh], gold, [B_HEAD, 1.0], 4, false, false, true, [float(HumanoidMaterials.FABRIC_METAL)])
			"watch":
				if lod == 2:
					continue
				var j0: Vector3 = rest_global[B_UPPERARM_L].origin
				var sv := p.upper_arm_len + p.forearm_len - p.wrist_r * 1.3
				var c := Vector3(j0.x, j0.y - sv, j0.z)
				var pts := []
				for j in 12:
					var t := TAU * float(j) / 12.0
					pts.append(c + Vector3(p.wrist_r * 0.85 * sin(t), 0.0, p.wrist_r * 1.18 * cos(t)))
				_path_tube(s_cloth, pts, [0.004 * H], Color(0.12, 0.09, 0.07), [B_FOREARM_L, 0.6, B_HAND_L, 0.4], 5, false, false, true, [float(HumanoidMaterials.FABRIC_LEATHER)])
				s_cloth.param = HumanoidMaterials.FABRIC_METAL
				_sphere(s_cloth, c + Vector3(p.wrist_r * 0.95, 0, 0), Vector3(0.004, 0.011, 0.011) * H / 1.7 * 1.7, Color(0.75, 0.76, 0.78), [B_FOREARM_L, 0.6, B_HAND_L, 0.4], 10, 5, Basis())
			"necklace":
				if lod == 2:
					continue
				var pts := []
				var yb := p.shoulder_y + 0.012 * H
				for j in 18:
					var th := TAU * float(j) / 18.0
					var droop := 0.03 * H * pow(maxf(cos(th), 0.0), 2.0)
					var b := _torso_pt(yb - droop, th)
					var d := Vector3(b.x, 0, b.z + 0.012 * H).normalized()
					pts.append(b + d * (0.011 * H if top_key in ["hoodie", "jacket", "suit", "sweater", "shirt", "polo"] else 0.004 * H))
				_path_tube(s_cloth, pts, [0.0022 * H], gold, [B_CHEST, 1.0], 4, false, false, true, [float(HumanoidMaterials.FABRIC_METAL)])
				var pf: Vector3 = pts[0]
				s_cloth.param = HumanoidMaterials.FABRIC_METAL
				_sphere(s_cloth, pf + Vector3(0, -0.008 * H, 0.002 * H), Vector3.ONE * 0.0055 * H, gold, [B_CHEST, 1.0], 6, 4)
			"scarf":
				var pts := []
				var ys := p.shoulder_y + 0.03 * H
				for j in 14:
					var th := TAU * float(j) / 14.0
					var b := _torso_pt(ys, th)
					var d := Vector3(b.x, 0, b.z + 0.012 * H).normalized()
					pts.append(b + d * 0.016 * H)
				_path_tube(s_cloth, pts, [0.02 * H], acc_col, [B_CHEST, 0.5, B_NECK, 0.5], 8 if lod == 0 else 5, false, false, true, [float(HumanoidMaterials.FABRIC_KNIT)])
				var f := _torso_pt(ys - 0.01 * H, 0.35)
				var hang := [f + Vector3(0, 0, 0.022 * H), f + Vector3(0.004 * H, -0.06 * H, 0.03 * H), f + Vector3(0.006 * H, -0.14 * H, 0.032 * H)]
				_path_tube(s_cloth, hang, [0.017 * H, 0.015 * H, 0.014 * H], acc_col.darkened(0.06), [B_CHEST, 1.0], 6, false, true, false, [float(HumanoidMaterials.FABRIC_KNIT)])
			"backpack":
				var y0 := p.waist_y + 0.01 * H
				var y1 := p.shoulder_y - 0.025 * H
				var rings := []
				var cols := []
				var wts := []
				var nst := 7 if lod == 0 else 4
				var bw := maxf(p.chest_w * 0.42, 0.075 * H)
				var bd := 0.05 * H
				var zb := _torso_back_z(0.0, lerpf(y0, y1, 0.5))
				for k in nst + 1:
					var t := float(k) / float(nst)
					var y := lerpf(y0, y1, t)
					var round_t := sin(PI * clampf(t * 1.0, 0.0, 1.0))
					var sc := 0.55 + 0.45 * pow(round_t, 0.35)
					var ring := PackedVector3Array()
					var nseg := 12 if lod == 0 else 6
					for j in nseg:
						var th := TAU * float(j) / float(nseg)
						var x := bw * sc * _spow(sin(th), 0.6)
						var z := zb - bd * 0.55 + bd * 0.55 * sc * _spow(cos(th), 0.6)
						ring.append(Vector3(x, y + sole, z))
					rings.append(ring)
					cols.append(acc_col if k != nst / 2 else acc_col.darkened(0.15))
					wts.append([B_CHEST, 0.8, B_SPINE, 0.2])
				s_cloth.param = HumanoidMaterials.FABRIC_SATIN
				_loft(s_cloth, rings, cols, wts, true, true)
				for side in [1.0, -1.0]:
					var sx: float = side * p.neck_r * 1.6
					var fr := _torso_pt(p.bust_y + 0.02 * H, side * 0.55)
					var top := _torso_pt(p.shoulder_y + 0.012 * H, side * PI * 0.5)
					var st := [Vector3(sx, y1 + sole, zb - 0.01 * H), Vector3(sx, top.y + 0.012 * H, -0.01 * H), Vector3(fr.x * 0.95, fr.y, fr.z + 0.012 * H), Vector3(fr.x, p.waist_y + 0.02 * H + sole, _torso_pt(p.waist_y + 0.02 * H, side * 0.6).z + 0.01 * H)]
					_path_tube(s_cloth, st, [0.006 * H], acc_col.darkened(0.3), [B_CHEST, 1.0], 4)


func _build_headwear(kind: String) -> void:
	var n := 30 if lod == 0 else (12 if lod == 1 else 8)
	var center := Vector3(0.0, e * 0.3, -0.03)
	var col := acc_col
	var y_low := e + 0.17
	var extra := 0.016
	var fab := HumanoidMaterials.FABRIC_COTTON
	if kind == "beanie":
		y_low = e + 0.12
		extra = 0.022
		fab = HumanoidMaterials.FABRIC_KNIT
	elif kind == "hat":
		y_low = e + 0.16
		extra = 0.02
	var ys := []
	var steps := 7 if lod == 0 else 4
	for k in steps + 1:
		ys.append(lerpf(y_low, 0.49, pow(float(k) / float(steps), 0.85)))
	var rings := []
	var cols := []
	for k in ys.size():
		var y: float = ys[k]
		var ring := PackedVector3Array()
		for j in n:
			var th := TAU * float(j) / float(n) - PI
			var base := _head_pt(y, th, false)
			var dir := (base - center).normalized()
			var ht := maxf(_hair_t(y, th), 0.0)
			var off := ht + extra
			if kind == "beanie" and k == 0:
				off += 0.012
			if kind == "hat":
				off = maxf(off, 0.05)
				base.y = lerpf(y, y_low + (y - y_low) * 1.5, 0.6)
			ring.append(_hm(base + dir * off))
		rings.append(ring)
		cols.append(col.darkened(0.12) if (k == 0 and kind != "cap") else col)
	s_cloth.param = fab
	_loft(s_cloth, rings, cols, [[B_HEAD, 1.0]].duplicate() if false else _fill([B_HEAD, 1.0], rings.size()), false, true)
	if kind == "cap":
		# Schirm
		var brim := []
		var nb := 9 if lod == 0 else 5
		var r_front: PackedVector3Array = rings[0]
		for row in 4:
			var t := float(row) / 3.0
			var ring := PackedVector3Array()
			for j in nb:
				var th := lerpf(-0.95, 0.95, float(j) / float(nb - 1))
				var b := _head_pt(y_low, th, false)
				var dir := (b - center)
				dir.y = 0.0
				dir = dir.normalized()
				var reach := 0.24 * cos(th * 0.8) * t
				var q := b + dir * (maxf(_hair_t(y_low, th), 0.0) + extra + reach) + Vector3(0, -0.04 * t * t, 0)
				ring.append(_hm(q))
			brim.append(ring)
		_brim(brim, col.darkened(0.05))
		_sphere(s_cloth, _hm(Vector3(0, 0.5 + extra + maxf(_hair_t(0.49, 0.0), 0.0), -0.03)), Vector3.ONE * 0.022 * hh, col, [B_HEAD, 1.0], 6, 4)
	elif kind == "hat":
		var brim := []
		var nb := 24 if lod == 0 else 10
		for row in 3:
			var t := float(row) / 2.0
			var ring := PackedVector3Array()
			for j in nb + 1:
				var th := TAU * float(j) / float(nb) - PI
				var b := _head_pt(y_low, th, false)
				var dir := (b - center)
				dir.y = 0.0
				dir = dir.normalized()
				var q := b + dir * (0.05 + extra + 0.22 * t) + Vector3(0, -0.02 * t, 0)
				ring.append(_hm(q))
			brim.append(ring)
		_brim(brim, col.darkened(0.08))
	elif kind == "beanie" and lod < 2:
		_sphere(s_cloth, _hm(Vector3(0, 0.56 + maxf(_hair_t(0.49, 0.0), 0.0), -0.03)), Vector3.ONE * 0.06 * hh, col.lightened(0.15), [B_HEAD, 1.0], 8, 6)


static func _fill(v, count: int) -> Array:
	var out := []
	for i in count:
		out.append(v)
	return out


## Doppelseitige Platte (Schirm/Krempe) aus Reihen.
func _brim(rows: Array, col: Color) -> void:
	var th := 0.008 * hh
	var top := []
	var bot := []
	for r in rows:
		var a := PackedVector3Array()
		var b := PackedVector3Array()
		for q in r:
			a.append(q + Vector3(0, th, 0))
			b.append(q)
		top.append(a)
		bot.append(b)
	var w := _fill([B_HEAD, 1.0], rows.size())
	var c := _fill(col, rows.size())
	var t0: PackedVector3Array = top[0]
	_orient(top, t0.size() / 2, Vector3.UP)
	_loft(s_cloth, top, c, w, false, false, false)
	_orient(bot, t0.size() / 2, Vector3.DOWN)
	_loft(s_cloth, bot, c, w, false, false, false)
