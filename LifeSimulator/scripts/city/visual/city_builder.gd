class_name CityBuilder
extends Node3D
## Baut die 3D-Stadt aus einem CityModel und streamt sie um einen Fokusknoten.
##
## Ebenen:
##  * Global (sofort): Asphaltflaeche, Fluss, Kaimauern, Bruecken, Umland mit
##    Feldern, Huegeln und Waldguertel.
##  * Fern (je Chunk, einmalig): vereinfachte Gebaeudehuellen, Bloecke, Baeume -
##    damit die Skyline bis zum Horizont steht.
##  * Nah (je Chunk, nach Bedarf): volle Geometrie, Details, Requisiten
##    (MultiMesh), Schriften, Kollision (nur sehr nah), Ampeln mit Live-Zustand.
## Chunk-Geometrie entsteht in Worker-Threads; im Hauptthread wird pro Frame
## nur ein fertiger Chunk eingehaengt.
##
## Oeffentlich:
##   setup(city: CityModel, focus: Node3D = null)
##   set_focus(node: Node3D)
##   build_area_now(center: Vector3, radius: float)   # synchron (Menue, Tests)
##   is_area_ready(center: Vector3, radius: float) -> bool
##   set_debug_overlay(on: bool) / toggle_debug_overlay()
##   get_stats() -> Dictionary
## Signale: chunk_loaded(key), area_ready

signal chunk_loaded(key: Vector2i)
signal area_ready

const CITY_LAYER := 1
const COLLISION_RADIUS := 170.0
const MAX_TASKS := 3

var city: CityModel
var focus: Node3D
var view_distance := 600.0
var detail_range := 220.0
var quality := 2
var shadows := 2
var lights_enabled := true
## Reichweitenfaktor fuer Requisiten und Reichweite der Kollision (Meter),
## gesetzt vom Leistungsmanager.
var prop_range_mult := 1.0
var collision_range := 170.0
var max_tasks := 3
var stream_enabled := true

var _index: Dictionary = {}          # chunk -> {nodes, crosswalks, parking, street_parking, bus_stops}
var _lots_by_block: Dictionary = {}
var _loaded: Dictionary = {}         # key -> Node3D
var _far: Dictionary = {}            # key -> Node3D
var _chunk_meta: Dictionary = {}     # key -> {shapes, lamps, lights, signals, mm}
var _pending: Dictionary = {}        # key -> task id
var _results: Dictionary = {}        # key -> data
var _far_pending: Array = []
var _far_results: Array = []
var _mutex := Mutex.new()
var _bodies: Dictionary = {}         # key -> StaticBody3D
var _timer := 0.0
var _signal_timer := 0.0
var _light_timer := 0.0
var _pool: Array[OmniLight3D] = []
var _font: FontVariation
var _debug_node: Node3D
var _global_node: Node3D
var _near_root: Node3D
var _far_root: Node3D
var _build_ms: Array = []
var _await_center := Vector3.INF
var _await_radius := 0.0


func _ready() -> void:
	_read_settings()
	if has_node("/root/Settings"):
		get_node("/root/Settings").changed.connect(_on_setting_changed)


func _exit_tree() -> void:
	# Laufende Worker abwarten, bevor die Daten verschwinden
	for k in _pending:
		WorkerThreadPool.wait_for_task_completion(_pending[k])
	_pending.clear()
	for t in _far_pending:
		WorkerThreadPool.wait_for_task_completion(t)
	_far_pending.clear()


func setup(c: CityModel, focus_node: Node3D = null) -> void:
	city = c
	focus = focus_node
	CityMaterials.warmup()
	_font = FontVariation.new()
	var base: Font = load("res://assets/fonts/Montserrat-Variable.ttf")
	_font.base_font = base
	_font.variation_opentype = {TextServerManager.get_primary_interface().name_to_tag("wght"): 700}
	_build_index()
	_global_node = Node3D.new()
	_global_node.name = "Global"
	add_child(_global_node)
	_near_root = Node3D.new()
	_near_root.name = "Near"
	add_child(_near_root)
	_far_root = Node3D.new()
	_far_root.name = "Far"
	add_child(_far_root)
	_build_global()
	# Die Fernschicht zeichnet die ganze Stadt vereinfacht. Im Leistungsmodus
	# entfaellt sie; der dichtere Nebel verdeckt den Horizont.
	var perf := get_node_or_null("/root/Perf")
	if perf == null or perf.b("far_lod"):
		_start_far_layer()
	_make_light_pool()


func set_focus(node: Node3D) -> void:
	focus = node


func _read_settings() -> void:
	# Alle Grenzwerte kommen aus dem Leistungsmanager, damit eine Stufe
	# sofort auf Sichtweite, Details, Schatten und Lichter wirkt.
	var perf := get_node_or_null("/root/Perf")
	if perf != null:
		view_distance = perf.view_distance()
		quality = int(perf.level)
		shadows = perf.i("shadows")
		detail_range = perf.f("detail_range") * perf.dynamic_scale
		prop_range_mult = perf.f("prop_range_mult")
		collision_range = perf.f("collision_range")
		max_tasks = perf.i("chunk_budget")
		lights_enabled = perf.i("street_lights") > 0
		return
	if not has_node("/root/Settings"):
		return
	var s := get_node("/root/Settings")
	view_distance = clampf(float(s.get_value("graphics", "view_distance")), 250.0, 3000.0)
	quality = int(s.get_value("graphics", "quality"))
	shadows = int(s.get_value("graphics", "shadows"))
	detail_range = [70.0, 120.0, 170.0, 230.0, 320.0][clampi(quality, 0, 4)]


func _on_setting_changed(section: String, key: String, _value) -> void:
	if section != "graphics":
		return
	_read_settings()
	for k in _loaded:
		_apply_quality(_loaded[k])
	_make_light_pool()


func _apply_quality(chunk: Node3D) -> void:
	for ch in chunk.get_children():
		if ch is GeometryInstance3D:
			if ch.has_meta("detail"):
				ch.visibility_range_end = detail_range
			if ch.has_meta("prop_range"):
				ch.visibility_range_end = float(ch.get_meta("prop_range")) * prop_range_mult
			if ch.has_meta("small"):
				ch.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON if shadows >= 2 else GeometryInstance3D.SHADOW_CASTING_SETTING_OFF


# ============================================================ Index

func _build_index() -> void:
	var add := func(k: Vector2i, list: String, v) -> void:
		if not _index.has(k):
			_index[k] = {"nodes": [], "crosswalks": [], "parking": [], "street_parking": [], "bus_stops": []}
		_index[k][list].append(v)
	for n in city.nodes:
		add.call(city.chunk_key(n.pos), "nodes", n.id)
	for i in city.crosswalks.size():
		add.call(city.chunk_key(city.crosswalks[i].center), "crosswalks", i)
	for pa in city.parking_areas:
		add.call(city.chunk_key(pa.rect.get_center()), "parking", pa.id)
	for i in city.street_parking.size():
		add.call(city.chunk_key(city.street_parking[i].pos), "street_parking", i)
	for s in city.bus_stops:
		add.call(city.chunk_key(s.pos), "bus_stops", s.id)
	for lot in city.lots:
		if not _lots_by_block.has(lot.block):
			_lots_by_block[lot.block] = []
		_lots_by_block[lot.block].append(lot.id)


func all_chunk_keys() -> Array:
	var keys := {}
	for k in city.chunks:
		keys[k] = true
	for k in _index:
		keys[k] = true
	return keys.keys()


# ============================================================ Chunk-Daten (Worker)

static func gen_chunk(c: CityModel, key: Vector2i, idx: Dictionary, lots_by_block: Dictionary) -> Dictionary:
	var t0 := Time.get_ticks_usec()
	var mb := MeshBatch.new()
	var det := MeshBatch.new()
	var out := {"labels": [], "shapes": [], "lights": [], "inst": {}, "lamps": [], "signals": []}
	var ch: Dictionary = c.chunks.get(key, {"buildings": [], "props": [], "blocks": [], "edges": [], "parks": []})
	var ix: Dictionary = idx.get(key, {"nodes": [], "crosswalks": [], "parking": [], "street_parking": [], "bus_stops": []})
	for bid in ch.blocks:
		var blk: Dictionary = c.blocks[bid]
		RoadBuilder.build_block(c, blk, mb, det, out, lots_by_block.get(bid, []))
		if blk.kind != "promenade":
			out["shapes"].append({"points": _block_ramp_points(blk.curb)})
		else:
			out["shapes"].append({"points": _block_ramp_points(blk.curb)})
	for pid in ch.parks:
		RoadBuilder.build_park(c, c.parks[pid], mb, det, out)
	for eid in ch.edges:
		RoadBuilder.build_edge_markings(c, c.edges[eid], mb)
	for nid in ix.nodes:
		RoadBuilder.build_node(c, c.nodes[nid], mb, det, out)
	for i in ix.crosswalks:
		RoadBuilder.build_crosswalk(c, c.crosswalks[i], mb)
	for i in ix.street_parking:
		RoadBuilder.build_street_parking(c, c.street_parking[i], mb)
	for i in ix.bus_stops:
		RoadBuilder.build_bus_stop(c, c.bus_stops[i], mb)
	for i in ix.parking:
		RoadBuilder.build_parking(c, c.parking_areas[i], mb)
	for bid in ch.buildings:
		BuildingBuilder.build(c, c.buildings[bid], mb, det, out)
	PropBuilder.collect(c, ch.props, out)
	return {"key": key, "main": mb.to_data(), "detail": det.to_data(), "out": out,
		"ms": (Time.get_ticks_usec() - t0) / 1000.0, "verts": mb.vertex_count() + det.vertex_count()}


## Bordstein als Rampe (konvexe Kollision): Kanten 0.5 m schraeg, damit
## Figuren ohne Treppenlogik auf den Gehweg kommen.
static func _block_ramp_points(r: Rect2) -> PackedVector3Array:
	var o := r.grow(0.35)
	var pts := PackedVector3Array()
	for p in [o.position, Vector2(o.end.x, o.position.y), o.end, Vector2(o.position.x, o.end.y)]:
		pts.append(Vector3(p.x, -0.05, p.y))
	var i := r.grow(-0.15)
	for p in [i.position, Vector2(i.end.x, i.position.y), i.end, Vector2(i.position.x, i.end.y)]:
		pts.append(Vector3(p.x, 0.15, p.y))
	return pts


static func gen_far(c: CityModel, keys: Array, lots_by_block: Dictionary) -> Array:
	var res := []
	for key in keys:
		var mb := MeshBatch.new()
		var out := {"inst": {}, "labels": [], "shapes": []}
		var ch: Dictionary = c.chunks.get(key, {"buildings": [], "props": [], "blocks": []})
		for bid in ch.blocks:
			var blk: Dictionary = c.blocks[bid]
			var curb: Rect2 = blk.curb
			var inner: Rect2 = blk.inner
			var y := RoadBuilder.SIDEWALK_Y
			var cs := BuildingBuilder.rect_corners(curb)
			for i in 4:
				mb.wall("g_curb", cs[i], cs[(i + 1) % 4], -0.1, y)
			if blk.kind == "promenade":
				mb.flat_rect("g_plaza", curb, y)
				continue
			mb.flat_rect("g_pavers", Rect2(curb.position.x, curb.position.y, curb.size.x, inner.position.y - curb.position.y), y)
			mb.flat_rect("g_pavers", Rect2(curb.position.x, inner.end.y, curb.size.x, curb.end.y - inner.end.y), y)
			mb.flat_rect("g_pavers", Rect2(curb.position.x, inner.position.y, inner.position.x - curb.position.x, inner.size.y), y)
			mb.flat_rect("g_pavers", Rect2(inner.end.x, inner.position.y, curb.end.x - inner.end.x, inner.size.y), y)
			if blk.kind == "park":
				mb.flat_rect("g_grass", inner, y)
				continue
			for lid in lots_by_block.get(bid, []):
				var lot: Dictionary = c.lots[lid]
				mb.flat_rect(RoadBuilder._lot_key(c, lot), lot.rect, y)
		for pid in ch.get("parks", []):
			var pk: Dictionary = c.parks[pid]
			if not pk.promenade:
				for path in pk.paths:
					mb.flat_rect("g_gravel_ov", path, RoadBuilder.SIDEWALK_Y)
		for bid in ch.buildings:
			BuildingBuilder.build_far(c, c.buildings[bid], mb)
		PropBuilder.collect_far_trees(c, ch.props, out)
		res.append({"key": key, "main": mb.to_data(), "inst": out.inst})
	return res


# ============================================================ Global

func _build_global() -> void:
	var t0 := Time.get_ticks_msec()
	var mb := MeshBatch.new()
	var det := MeshBatch.new()
	var out := {"labels": [], "shapes": [], "lights": [], "inst": {}, "lamps": [], "signals": []}
	RoadBuilder.build_asphalt(city, mb)
	RoadBuilder.build_river(city, mb, det, out)
	TerrainBuilder.build(city, mb, out)
	var mi := MeshInstance3D.new()
	mi.name = "GlobalMesh"
	mi.mesh = MeshBatch.build_mesh(mb.to_data(), CityMaterials.lookup())
	mi.layers = CITY_LAYER
	_global_node.add_child(mi)
	var dmi := MeshInstance3D.new()
	dmi.mesh = MeshBatch.build_mesh(det.to_data(), CityMaterials.lookup())
	dmi.visibility_range_end = 300.0
	_global_node.add_child(dmi)
	_add_instances(_global_node, out.inst, true)
	# Globale Kollision: Boden nord/sued, Flussbett, Bruecken, Kaimauer-Brustwehr
	var body := StaticBody3D.new()
	body.name = "GlobalCollision"
	_global_node.add_child(body)
	var rv: Dictionary = city.river
	var big := 9000.0
	var zn: float = rv.z_north
	var zs: float = rv.z_south
	_add_box(body, Transform3D(Basis(), Vector3(0, -0.5, (zn - big) * 0.5)), Vector3(big * 2.0, 1.0, big + zn))
	_add_box(body, Transform3D(Basis(), Vector3(0, -0.5, (zs + big) * 0.5)), Vector3(big * 2.0, 1.0, big - zs))
	for s in out.shapes:
		_add_shape(body, s)
	# Brustwehr an den Ufern (ausser an Bruecken), damit niemand in den Fluss laeuft
	var gaps := []
	for e in city.edges:
		if e.bridge:
			var px: float = city.nodes[e.a].pos.x
			gaps.append([px - e.width * 0.5 - 4.0, px + e.width * 0.5 + 4.0])
	gaps.sort_custom(func(a, b): return a[0] < b[0])
	var x := -big
	for g in gaps + [[big, big]]:
		if g[0] > x:
			for zz in [zn - 0.2, zs + 0.2]:
				_add_box(body, Transform3D(Basis(), Vector3((x + g[0]) * 0.5, 0.6, zz)), Vector3(g[0] - x, 1.2, 0.3))
		x = maxf(x, g[1])
	Log_info("Globale Ebene gebaut (%d ms)" % (Time.get_ticks_msec() - t0))


func _add_box(body: StaticBody3D, xf: Transform3D, size: Vector3) -> void:
	var cs := CollisionShape3D.new()
	var bs := BoxShape3D.new()
	bs.size = size
	cs.shape = bs
	cs.transform = xf
	body.add_child(cs)


func _add_shape(body: StaticBody3D, s: Dictionary) -> void:
	if s.has("points"):
		var cs := CollisionShape3D.new()
		var cp := ConvexPolygonShape3D.new()
		cp.points = s.points
		cs.shape = cp
		body.add_child(cs)
	else:
		_add_box(body, s.xf, s.size)


# ============================================================ Fernebene

func _start_far_layer() -> void:
	var keys := all_chunk_keys()
	var groups := [[], [], [], []]
	for i in keys.size():
		groups[i % 4].append(keys[i])
	for g in groups:
		if g.is_empty():
			continue
		var tid := WorkerThreadPool.add_task(_far_task.bind(g))
		_far_pending.append(tid)


func _far_task(keys: Array) -> void:
	var res := gen_far(city, keys, _lots_by_block)
	_mutex.lock()
	_far_results.append_array(res)
	_mutex.unlock()


func _instantiate_far(d: Dictionary) -> void:
	var node := Node3D.new()
	node.name = "Far_%d_%d" % [d.key.x, d.key.y]
	var mi := MeshInstance3D.new()
	mi.mesh = MeshBatch.build_mesh(d.main, CityMaterials.lookup())
	mi.layers = CITY_LAYER
	node.add_child(mi)
	for k in d.inst:
		var mmi := _make_mmi(k, d.inst[k])
		mmi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		node.add_child(mmi)
	_far_root.add_child(node)
	_far[d.key] = node
	node.visible = not _loaded.has(d.key)


# ============================================================ Streaming

func _process(delta: float) -> void:
	if city == null:
		return
	# fertige Fern-Chunks einhaengen (mehrere pro Frame, sie sind klein)
	_mutex.lock()
	var batch := _far_results.slice(0, 6)
	_far_results = _far_results.slice(6)
	_mutex.unlock()
	if not batch.is_empty():
		for d in batch:
			_instantiate_far(d)
	var done := []
	for t in _far_pending:
		if WorkerThreadPool.is_task_completed(t):
			WorkerThreadPool.wait_for_task_completion(t)
			done.append(t)
	for t in done:
		_far_pending.erase(t)
	_timer -= delta
	if _timer <= 0.0 and stream_enabled:
		_timer = 0.25
		_update_streaming()
	_poll_tasks()
	_instantiate_ready(1)
	_signal_timer -= delta
	if _signal_timer <= 0.0:
		_signal_timer = 0.2
		_update_signals()
	_light_timer -= delta
	if _light_timer <= 0.0:
		_light_timer = 0.4
		_update_lights()
		_update_collision()
	if _await_center != Vector3.INF and is_area_ready(_await_center, _await_radius):
		_await_center = Vector3.INF
		area_ready.emit()


func focus_position() -> Vector3:
	if focus and is_instance_valid(focus):
		return focus.global_position
	var cam := get_viewport().get_camera_3d() if is_inside_tree() else null
	if cam:
		return cam.global_position
	return Vector3.ZERO


func _chunk_dist(k: Vector2i, p: Vector3) -> float:
	var r := city.chunk_rect(k)
	var q := Vector2(clampf(p.x, r.position.x, r.end.x), clampf(p.z, r.position.y, r.end.y))
	return q.distance_to(Vector2(p.x, p.z))


func _update_streaming() -> void:
	var p := focus_position()
	var want := []
	_mutex.lock()
	for k in all_chunk_keys():
		var d := _chunk_dist(k, p)
		if d <= view_distance and not _loaded.has(k) and not _pending.has(k) and not _results.has(k):
			want.append([d, k])
	_mutex.unlock()
	want.sort_custom(func(a, b): return a[0] < b[0])
	for w in want:
		if _pending.size() >= maxi(1, max_tasks):
			break
		_request_chunk(w[1])
	# Entladen
	var drop := []
	for k in _loaded:
		if _chunk_dist(k, p) > view_distance + 220.0:
			drop.append(k)
	for k in drop:
		_unload(k)


func _request_chunk(k: Vector2i) -> void:
	var tid := WorkerThreadPool.add_task(_chunk_task.bind(k))
	_pending[k] = tid


func _chunk_task(k: Vector2i) -> void:
	var d := gen_chunk(city, k, _index, _lots_by_block)
	_mutex.lock()
	_results[k] = d
	_mutex.unlock()


func _poll_tasks() -> void:
	var done := []
	for k in _pending:
		if WorkerThreadPool.is_task_completed(_pending[k]):
			WorkerThreadPool.wait_for_task_completion(_pending[k])
			done.append(k)
	for k in done:
		_pending.erase(k)


func _instantiate_ready(max_count: int) -> void:
	var p := focus_position()
	_mutex.lock()
	var keys := _results.keys()
	_mutex.unlock()
	if keys.is_empty():
		return
	keys.sort_custom(func(a, b): return _chunk_dist(a, p) < _chunk_dist(b, p))
	for i in mini(max_count, keys.size()):
		_mutex.lock()
		var d: Dictionary = _results[keys[i]]
		_results.erase(keys[i])
		_mutex.unlock()
		_instantiate_chunk(d)


func _instantiate_chunk(d: Dictionary) -> void:
	var key: Vector2i = d.key
	if _loaded.has(key):
		return
	var t0 := Time.get_ticks_usec()
	var node := Node3D.new()
	node.name = "Chunk_%d_%d" % [key.x, key.y]
	var mi := MeshInstance3D.new()
	mi.name = "Main"
	mi.mesh = MeshBatch.build_mesh(d.main, CityMaterials.lookup())
	mi.layers = CITY_LAYER
	node.add_child(mi)
	if not d.detail.is_empty():
		var dm := MeshInstance3D.new()
		dm.name = "Detail"
		dm.mesh = MeshBatch.build_mesh(d.detail, CityMaterials.lookup())
		dm.set_meta("detail", true)
		dm.set_meta("small", true)
		dm.visibility_range_end = detail_range
		dm.visibility_range_end_margin = 20.0
		dm.visibility_range_fade_mode = GeometryInstance3D.VISIBILITY_RANGE_FADE_DISABLED
		node.add_child(dm)
	var out: Dictionary = d.out
	var mms := _add_instances(node, out.inst, false)
	for lab in out.labels:
		node.add_child(_make_label(lab))
	_near_root.add_child(node)
	_apply_quality(node)
	_loaded[key] = node
	var lights: Array = []
	for l in out.lamps:
		lights.append(l)
	for l in out.lights:
		lights.append(l.pos)
	_chunk_meta[key] = {"shapes": out.shapes, "lights": lights, "signals": out.signals, "mm": mms}
	if _far.has(key):
		_far[key].visible = false
	_build_ms.append([d.ms, (Time.get_ticks_usec() - t0) / 1000.0, d.verts])
	chunk_loaded.emit(key)


func _unload(k: Vector2i) -> void:
	if _loaded.has(k):
		_loaded[k].queue_free()
		_loaded.erase(k)
	_chunk_meta.erase(k)
	if _bodies.has(k):
		_bodies[k].queue_free()
		_bodies.erase(k)
	if _far.has(k):
		_far[k].visible = true


func _add_instances(parent: Node3D, inst: Dictionary, global: bool) -> Dictionary:
	var mms := {}
	for k in inst:
		var mmi := _make_mmi(k, inst[k])
		if global:
			mmi.visibility_range_end = 0.0
			mmi.remove_meta("prop_range")
			mmi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		parent.add_child(mmi)
		mms[k] = mmi.multimesh
	return mms


func _make_mmi(key: String, items: Array) -> MultiMeshInstance3D:
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.use_colors = true
	mm.use_custom_data = key.begins_with("signal")
	mm.mesh = PropBuilder.get_mesh(key)
	mm.instance_count = items.size()
	for i in items.size():
		mm.set_instance_transform(i, items[i][0])
		mm.set_instance_color(i, items[i][1])
		if mm.use_custom_data:
			mm.set_instance_custom_data(i, items[i][2])
	var mmi := MultiMeshInstance3D.new()
	mmi.name = "MM_" + key
	mmi.multimesh = mm
	mmi.layers = CITY_LAYER
	mmi.set_meta("prop_range", PropBuilder.range_of(key))
	mmi.visibility_range_end = PropBuilder.range_of(key)
	if not PropBuilder.casts_shadow(key):
		mmi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	elif not key.begins_with("tree"):
		mmi.set_meta("small", true)
	return mmi


func _make_label(lab: Dictionary) -> Label3D:
	var l := Label3D.new()
	l.text = lab.text
	l.font = _font
	l.font_size = 64
	l.outline_size = 0
	var h: float = lab.h
	var px := h / 64.0 * 1.25
	var lines := String(lab.text).split("\n")
	var longest := 0
	for ln in lines:
		longest = maxi(longest, ln.length())
	var est := longest * 0.62 * h * 1.25
	if lab.max_w > 0.0 and est > lab.max_w:
		px *= lab.max_w / est
	l.pixel_size = px
	l.modulate = lab.color
	var oc: Color = lab.outline
	if oc.a > 0.0:
		l.outline_size = 10
		l.outline_modulate = oc
	l.shaded = not lab.emissive
	l.double_sided = false
	l.alpha_cut = Label3D.ALPHA_CUT_OPAQUE_PREPASS
	l.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
	l.position = lab.pos
	l.rotation.y = lab.yaw
	l.visibility_range_end = lab.range
	l.layers = CITY_LAYER
	l.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	return l


# ============================================================ Synchroner Aufbau / Status

## Baut alle Chunks im Umkreis sofort (blockierend, mit Worker-Threads).
func build_area_now(center: Vector3, radius: float) -> void:
	var keys := []
	for k in all_chunk_keys():
		if _chunk_dist(k, center) <= radius and not _loaded.has(k):
			keys.append(k)
	var tasks := {}
	for k in keys:
		if _pending.has(k):
			continue
		tasks[k] = WorkerThreadPool.add_task(_chunk_task.bind(k))
	for k in tasks:
		WorkerThreadPool.wait_for_task_completion(tasks[k])
	for k in _pending:
		WorkerThreadPool.wait_for_task_completion(_pending[k])
	_pending.clear()
	for t in _far_pending:
		WorkerThreadPool.wait_for_task_completion(t)
	_far_pending.clear()
	_instantiate_ready(1000)
	_mutex.lock()
	var fr := _far_results.duplicate()
	_far_results.clear()
	_mutex.unlock()
	for d in fr:
		_instantiate_far(d)
	_update_signals()
	_update_lights()
	_update_collision()


func is_area_ready(center: Vector3, radius: float) -> bool:
	for k in all_chunk_keys():
		if _chunk_dist(k, center) <= radius and not _loaded.has(k):
			return false
	return true


## Signal area_ready, sobald alle Chunks im Umkreis geladen sind.
func await_area(center: Vector3, radius: float) -> void:
	_await_center = center
	_await_radius = radius


func get_stats() -> Dictionary:
	var gen := 0.0
	var inst := 0.0
	var verts := 0
	for b in _build_ms:
		gen += b[0]
		inst += b[1]
		verts += b[2]
	var n := maxi(_build_ms.size(), 1)
	return {"loaded": _loaded.size(), "far": _far.size(), "chunks_built": _build_ms.size(),
		"avg_gen_ms": gen / n, "avg_inst_ms": inst / n, "avg_verts": verts / n, "pending": _pending.size()}


# ============================================================ Ampeln, Licht, Kollision

func _update_signals() -> void:
	var t := Time.get_ticks_msec() / 1000.0
	var p := focus_position()
	for k in _chunk_meta:
		if _chunk_dist(k, p) > 450.0:
			continue
		var meta: Dictionary = _chunk_meta[k]
		for s in meta.signals:
			var mm: MultiMesh = meta.mm.get(s.key)
			if mm == null:
				continue
			var st := city.signal_state(s.node, s.axis, t)
			var v := 0.0 if st == "red" else (1.0 if st == "yellow" else 2.0)
			var ped := 1.0 if city.ped_signal_green(s.node, s.axis, t) else 0.0
			mm.set_instance_custom_data(s.idx, Color(v, ped, 0, 0))


func _make_light_pool() -> void:
	for l in _pool:
		l.queue_free()
	_pool.clear()
	var perf := get_node_or_null("/root/Perf")
	var n: int = perf.i("street_lights") if perf != null else [0, 4, 10, 16, 24][clampi(quality, 0, 4)]
	if RenderingServer.get_rendering_device() == null:
		n = mini(n, 8)
	for i in n:
		var l := OmniLight3D.new()
		l.omni_range = 22.0
		l.omni_attenuation = 1.0
		l.light_color = Color(1.0, 0.82, 0.6)
		l.light_energy = 0.0
		l.shadow_enabled = false
		l.visible = false
		add_child(l)
		_pool.append(l)


func _update_lights() -> void:
	var night := CityMaterials.get_global("night")
	if night < 0.05 or not lights_enabled:
		for l in _pool:
			l.visible = false
		return
	var p := focus_position()
	var cands := []
	for k in _chunk_meta:
		if _chunk_dist(k, p) > 120.0:
			continue
		for lp in _chunk_meta[k].lights:
			cands.append([lp.distance_squared_to(p), lp])
	cands.sort_custom(func(a, b): return a[0] < b[0])
	for i in _pool.size():
		var l := _pool[i]
		if i < cands.size():
			l.visible = true
			l.global_position = cands[i][1] - Vector3(0, 0.4, 0)
			l.light_energy = 7.0 * night
		else:
			l.visible = false


func _update_collision() -> void:
	var p := focus_position()
	for k in _chunk_meta:
		var near := _chunk_dist(k, p) <= maxf(80.0, collision_range)
		if near and not _bodies.has(k):
			var body := StaticBody3D.new()
			body.name = "Collision_%d_%d" % [k.x, k.y]
			for s in _chunk_meta[k].shapes:
				_add_shape(body, s)
			add_child(body)
			_bodies[k] = body
		elif not near and _bodies.has(k):
			_bodies[k].queue_free()
			_bodies.erase(k)


func has_collision_at(p: Vector3) -> bool:
	return _bodies.has(city.chunk_key(Vector2(p.x, p.z)))


# ============================================================ Debug-Ansicht (F4)

func toggle_debug_overlay() -> void:
	set_debug_overlay(_debug_node == null)


func set_debug_overlay(on: bool) -> void:
	if not on:
		if _debug_node:
			_debug_node.queue_free()
			_debug_node = null
		return
	if _debug_node:
		return
	_debug_node = Node3D.new()
	_debug_node.name = "DebugOverlay"
	add_child(_debug_node)
	var im := ImmediateMesh.new()
	var mat := StandardMaterial3D.new()
	mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	mat.vertex_color_use_as_albedo = true
	mat.no_depth_test = true
	im.surface_begin(Mesh.PRIMITIVE_LINES, mat)
	var y := 0.4
	var rect_lines := func(r: Rect2, c: Color, h: float) -> void:
		var cs := [r.position, Vector2(r.end.x, r.position.y), r.end, Vector2(r.position.x, r.end.y)]
		for i in 4:
			im.surface_set_color(c)
			im.surface_add_vertex(Vector3(cs[i].x, h, cs[i].y))
			im.surface_set_color(c)
			im.surface_add_vertex(Vector3(cs[(i + 1) % 4].x, h, cs[(i + 1) % 4].y))
	for lot in city.lots:
		rect_lines.call(lot.rect, Color(1.0, 0.85, 0.2) if lot.usage == "building" else Color(0.3, 1.0, 0.3), y)
	for b in city.buildings:
		rect_lines.call(b.footprint, Color(1.0, 0.4, 0.1), y + 0.1)
	for link in city.ped_links:
		var a: Vector2 = city.ped_points[link[0]]
		var b2: Vector2 = city.ped_points[link[1]]
		var c := Color(0.2, 0.9, 1.0) if link[2] == "side" else Color(1.0, 0.2, 1.0)
		im.surface_set_color(c)
		im.surface_add_vertex(Vector3(a.x, y + 0.2, a.y))
		im.surface_set_color(c)
		im.surface_add_vertex(Vector3(b2.x, y + 0.2, b2.y))
	for i in city.issues:
		var p: Vector2 = i.pos
		var c2 := Color(1, 0, 0) if i.severity == "error" else Color(1, 0.5, 0)
		im.surface_set_color(c2)
		im.surface_add_vertex(Vector3(p.x, 0, p.y))
		im.surface_set_color(c2)
		im.surface_add_vertex(Vector3(p.x, 25, p.y))
	im.surface_end()
	var mi := MeshInstance3D.new()
	mi.mesh = im
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	_debug_node.add_child(mi)
	var errs := 0
	for i in city.issues:
		if i.severity == "error":
			errs += 1
	var lbl := Label3D.new()
	lbl.text = "Validierung: %d Fehler, %d Hinweise" % [errs, city.issues.size() - errs]
	lbl.billboard = BaseMaterial3D.BILLBOARD_ENABLED
	lbl.no_depth_test = true
	lbl.pixel_size = 0.05
	lbl.font_size = 48
	lbl.position = focus_position() + Vector3(0, 12, 0)
	_debug_node.add_child(lbl)


func is_debug_overlay() -> bool:
	return _debug_node != null


func Log_info(msg: String) -> void:
	if has_node("/root/Log"):
		get_node("/root/Log").info("city", msg)
	else:
		print(msg)
