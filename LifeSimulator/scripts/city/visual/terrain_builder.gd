class_name TerrainBuilder
extends RefCounted
## Umland ausserhalb des Rings: Gruenstreifen, Felder (Farbflecken per
## Vertexfarbe), sanft ansteigende Huegel bis zum Horizont, Flusstal und ein
## Waldguertel (Baum-Instanzen). Innerhalb der Stadt liegt das Gelaende knapp
## unter der Asphaltflaeche und ist unsichtbar.

const EXTENT := 4200.0


static func height_at(city: CityModel, noise: FastNoiseLite, x: float, z: float) -> float:
	var half := city.size * 0.5 + 12.0
	var d := maxf(absf(x), absf(z)) - half
	if d <= 0.0:
		return -0.06
	var rv: Dictionary = city.river
	var dz := absf(z - float(rv.z))
	var rw: float = rv.width * 0.5
	var hills := pow(clampf(d / 1600.0, 0.0, 1.0), 1.6) * (180.0 + 160.0 * noise.get_noise_2d(x * 0.6, z * 0.6))
	var undul := noise.get_noise_2d(x * 3.0, z * 3.0) * 3.0 * clampf(d / 200.0, 0.0, 1.0)
	var h := maxf(hills + undul, 0.0) - 0.06
	# Flusstal
	if dz < rw + 2.0:
		return -CityModel.RIVER_DEPTH - 0.5
	var valley := clampf((dz - rw - 2.0) / (60.0 + d * 0.2), 0.0, 1.0)
	var bank := lerpf(-0.6, h, smoothstep(0.0, 1.0, valley))
	if dz < rw + 8.0:
		bank = lerpf(-CityModel.RIVER_DEPTH - 0.5, 0.0, (dz - rw - 2.0) / 6.0)
	return bank


static func _coords(city: CityModel) -> PackedFloat32Array:
	var half := city.size * 0.5
	var out := PackedFloat32Array()
	var x := -EXTENT
	while x < EXTENT:
		out.append(x)
		var d := absf(x) - half
		var step := 40.0 if d < 400.0 else (90.0 if d < 1400.0 else 220.0)
		if absf(x) < half - 30.0:
			step = 300.0
		x += step
	out.append(EXTENT)
	return out


static func build(city: CityModel, mb: MeshBatch, out: Dictionary) -> void:
	var noise := FastNoiseLite.new()
	noise.seed = city.seed
	noise.frequency = 0.0006
	noise.fractal_octaves = 4
	var xs := _coords(city)
	var zs := _coords(city)
	# Feine Stuetzstellen am Fluss fuer das Tal
	var rv: Dictionary = city.river
	var extra := [rv.z_north - 70.0, rv.z_north - 30.0, rv.z_north - 8.0, rv.z_north - 2.0, rv.z_north + 1.0,
		rv.z_south - 1.0, rv.z_south + 2.0, rv.z_south + 8.0, rv.z_south + 30.0, rv.z_south + 70.0]
	var zl := Array(zs)
	for e in extra:
		zl.append(e)
	zl.sort()
	var field := FastNoiseLite.new()
	field.seed = city.seed + 7
	field.noise_type = FastNoiseLite.TYPE_CELLULAR
	field.frequency = 0.004
	field.cellular_return_type = FastNoiseLite.RETURN_CELL_VALUE
	var s := mb.surf("g_grass")
	var grid := []
	var half := city.size * 0.5
	for j in zl.size():
		var row := PackedInt32Array()
		for i in xs.size():
			var x: float = xs[i]
			var z: float = zl[j]
			var h := height_at(city, noise, x, z)
			var d := maxf(absf(x), absf(z)) - half
			var cv := field.get_noise_2d(x, z)
			var col := Color(1, 1, 1)
			if d > 120.0 and h < 60.0:
				# Felder: gelblich, hellgruen, braun
				if cv > 0.4:
					col = Color(1.25, 1.12, 0.62)
				elif cv > 0.0:
					col = Color(1.05, 1.15, 0.8)
				elif cv > -0.4:
					col = Color(0.95, 0.9, 0.75)
			if h > 70.0:
				col = Color(0.75, 0.85, 0.7)
			if h < -1.0:
				col = Color(0.7, 0.62, 0.5)
			col.a = 0.0
			row.append(s.vert(Vector3(x, h, z), Vector3.UP, Vector3.RIGHT, Vector2(x, z), col, MeshBatch.NO_CUSTOM))
		grid.append(row)
	# Normalen aus Nachbarn
	for j in zl.size():
		for i in xs.size():
			var vi: int = grid[j][i]
			var l: Vector3 = s.v[grid[j][maxi(i - 1, 0)]]
			var r: Vector3 = s.v[grid[j][mini(i + 1, xs.size() - 1)]]
			var u: Vector3 = s.v[grid[maxi(j - 1, 0)][i]]
			var dn: Vector3 = s.v[grid[mini(j + 1, zl.size() - 1)][i]]
			var nrm := (dn - u).cross(r - l).normalized()
			if nrm.y < 0:
				nrm = -nrm
			s.n[vi] = nrm
	for j in zl.size() - 1:
		for i in xs.size() - 1:
			var cx := (xs[i] + xs[i + 1]) * 0.5
			var cz := (float(zl[j]) + float(zl[j + 1])) * 0.5
			if absf(cx) < half - 20.0 and absf(cz) < half - 20.0:
				continue
			var a: int = grid[j][i]
			var b: int = grid[j][i + 1]
			var c: int = grid[j + 1][i + 1]
			var d2: int = grid[j + 1][i]
			# von oben: a(x0,z0) b(x1,z0) c(x1,z1) d(x0,z1); CCW von oben = d, c, b, a
			s.tri_idx(d2, c, b)
			s.tri_idx(d2, b, a)
	# Waldguertel
	var rng := RandomNumberGenerator.new()
	rng.seed = hash("forest:%d" % city.seed)
	var forest := FastNoiseLite.new()
	forest.seed = city.seed + 13
	forest.frequency = 0.003
	var inst: Dictionary = out["inst"]
	var trees := []
	var count := 0
	var attempts := 0
	# Waldmenge nach Leistungsstufe: auf schwachen Rechnern sind viele tausend
	# Baumflaechen der groesste Posten im Bild.
	var tree_budget := 9000
	var tree_reach := 1500.0
	var loop := Engine.get_main_loop()
	if loop is SceneTree and loop.root.has_node("Perf"):
		var perf = loop.root.get_node("Perf")
		var mult: float = perf.f("prop_density")
		tree_budget = int(9000 * mult)
		tree_reach = lerpf(500.0, 1500.0, clampf(mult, 0.0, 1.0))
	while count < tree_budget and attempts < 40000:
		attempts += 1
		var a := rng.randf() * TAU
		var r := half + 60.0 + pow(rng.randf(), 1.3) * tree_reach
		var x := cos(a) * r
		var z := sin(a) * r
		if maxf(absf(x), absf(z)) < half + 45.0:
			continue
		if absf(z - float(rv.z)) < float(rv.width) * 0.5 + 25.0:
			continue
		var dens := forest.get_noise_2d(x, z) + (0.35 if r < half + 500.0 else 0.0)
		if dens < 0.05:
			continue
		var h := height_at(city, noise, x, z)
		if h < -0.5:
			continue
		var sc := rng.randf_range(1.0, 1.9)
		trees.append([Transform3D(Basis(Vector3.UP, rng.randf() * TAU).scaled(Vector3(sc, sc * rng.randf_range(0.9, 1.3), sc)), Vector3(x, h - 0.2, z)), Color(1, 1, 1), Color()])
		count += 1
	inst["tree_far"] = trees
