class_name StreamingManager
extends Node3D
## World streaming.
##
## * The city is split into 200 m chunks (GLB scenes with LOD0/LOD1 + collision).
## * Chunks within the view distance are loaded on background threads and
##   instantiated on the main thread (budgeted per frame).
## * Everything outside is drawn by the always-loaded far HLOD (city_far.glb),
##   whose per-chunk node is hidden while the detailed chunk is present.
## * Per loaded chunk: props as MultiMeshes (LOD + visibility ranges), traffic
##   lights, box occluders for tall buildings and lamp light positions.

signal chunk_loaded(c: Vector2i)
signal chunk_unloaded(c: Vector2i)

const CHUNK_DIR := "res://assets/generated/city/"
const MAX_PARALLEL_LOADS := 3
const INSTANCE_BUDGET_MS := 6.0
const UPDATE_INTERVAL := 0.2

var world: WorldData
var props: PropLibrary
var signals_ctl: TrafficSignals
var graph: RoadGraph
var focus := Vector3.ZERO
var load_radius := 520.0
var collision_radius := 300.0

var _chunks := {}           # Vector2i -> ChunkState
var _available := {}        # Vector2i -> true (chunk files present)
var _pending := {}          # Vector2i -> path (threaded request in flight)
var _ready_queue: Array = []  # [Vector2i, PackedScene]
var _far_nodes := {}        # Vector2i -> Node3D
var _far_root: Node3D
var _timer := 0.0
var _signal_timer := 0.0
var _signals_by_chunk := {}
var lamp_lights := {}       # Vector2i -> PackedVector3Array
var tl_head_mat: ShaderMaterial


class ChunkState:
	var coord: Vector2i
	var root: Node3D
	var multimeshes: Array = []
	var tl_heads: MultiMeshInstance3D
	var tl_index: Array = []   # [node, edge] per head instance


func setup(w: WorldData, p: PropLibrary, g: RoadGraph, s: TrafficSignals) -> void:
	world = w
	props = p
	graph = g
	signals_ctl = s
	load_radius = Settings.view_distance() if Settings else 520.0
	for c in world.chunks:
		if ResourceLoader.exists(_path(c)):
			_available[c] = true
	for sg in graph.signals:
		var p3 := Vector3(sg["pos"][0], sg["pos"][1], sg["pos"][2])
		var ck := world.chunk_of(p3)
		if not _signals_by_chunk.has(ck):
			_signals_by_chunk[ck] = []
		_signals_by_chunk[ck].append(sg)
	tl_head_mat = ShaderMaterial.new()
	tl_head_mat.shader = load("res://assets/shaders/traffic_lens.gdshader")
	_load_far()


func _path(c: Vector2i) -> String:
	return CHUNK_DIR + "chunk_%d_%d.glb" % [c.x, c.y]


func _load_far() -> void:
	var p := CHUNK_DIR + "city_far.glb"
	if not ResourceLoader.exists(p):
		return
	var s: PackedScene = load(p)
	_far_root = s.instantiate()
	_far_root.name = "FarCity"
	add_child(_far_root)
	for n in _far_root.find_children("Far_*", "MeshInstance3D", true, false):
		var parts := String(n.name).split("_")
		if parts.size() >= 3:
			var c := Vector2i(int(parts[1]), int(parts[2]))
			_far_nodes[c] = n
			(n as MeshInstance3D).cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF


func set_focus(p: Vector3) -> void:
	focus = p


func is_loaded_at(p: Vector3) -> bool:
	return _chunks.has(world.chunk_of(p))


func loaded_chunks() -> Array:
	return _chunks.keys()


## Synchronously load everything around `p` (used behind the loading screen / teleports).
func load_area_blocking(p: Vector3, radius := -1.0, progress: Callable = Callable()) -> void:
	focus = p
	var r := load_radius if radius < 0.0 else radius
	var want := _wanted(p, r)
	var i := 0
	for c in want:
		i += 1
		if _chunks.has(c):
			continue
		var s: PackedScene = load(_path(c))
		_instantiate(c, s)
		if progress.is_valid():
			progress.call(float(i) / float(want.size()), "Stadtteil %d/%d" % [i, want.size()])


func _wanted(p: Vector3, r: float) -> Array:
	var out := []
	for c in _available:
		var cc := world.chunk_center(c)
		var dx := maxf(absf(p.x - cc.x) - world.chunk_size * 0.5, 0.0)
		var dz := maxf(absf(p.z - cc.z) - world.chunk_size * 0.5, 0.0)
		var d := sqrt(dx * dx + dz * dz)
		if d <= r:
			out.append(c)
	out.sort_custom(func(a, b): return world.chunk_center(a).distance_squared_to(p) < world.chunk_center(b).distance_squared_to(p))
	return out


func _process(delta: float) -> void:
	if world == null:
		return
	_timer -= delta
	if _timer <= 0.0:
		_timer = UPDATE_INTERVAL
		_update_requests()
	_poll_pending()
	_instantiate_budgeted()
	_signal_timer -= delta
	if _signal_timer <= 0.0:
		_signal_timer = 0.4
		_update_traffic_lights()


func _update_requests() -> void:
	var want := _wanted(focus, load_radius)
	var want_set := {}
	for c in want:
		want_set[c] = true
	# unload with hysteresis
	for c in _chunks.keys():
		if not want_set.has(c):
			var cc := world.chunk_center(c)
			if Vector2(cc.x - focus.x, cc.z - focus.z).length() > load_radius + world.chunk_size * 1.2:
				_unload(c)
	# request new
	for c in want:
		if _chunks.has(c) or _pending.has(c):
			continue
		if _pending.size() >= MAX_PARALLEL_LOADS:
			break
		var p := _path(c)
		if ResourceLoader.load_threaded_request(p, "PackedScene", true) == OK:
			_pending[c] = p


func _poll_pending() -> void:
	for c in _pending.keys():
		var p: String = _pending[c]
		var st := ResourceLoader.load_threaded_get_status(p)
		if st == ResourceLoader.THREAD_LOAD_LOADED:
			var s: PackedScene = ResourceLoader.load_threaded_get(p)
			_pending.erase(c)
			_ready_queue.append([c, s])
		elif st == ResourceLoader.THREAD_LOAD_FAILED or st == ResourceLoader.THREAD_LOAD_INVALID_RESOURCE:
			_pending.erase(c)
			_available.erase(c)
			push_warning("Streaming: failed to load " + p)


func _instantiate_budgeted() -> void:
	var t0 := Time.get_ticks_usec()
	while not _ready_queue.is_empty():
		var item: Array = _ready_queue.pop_front()
		var c: Vector2i = item[0]
		if _chunks.has(c):
			continue
		_instantiate(c, item[1])
		if (Time.get_ticks_usec() - t0) / 1000.0 > INSTANCE_BUDGET_MS:
			break


func _instantiate(c: Vector2i, scene: PackedScene) -> void:
	var st := ChunkState.new()
	st.coord = c
	st.root = scene.instantiate()
	st.root.name = "Chunk_%d_%d" % [c.x, c.y]
	add_child(st.root)
	_chunks[c] = st
	if _far_nodes.has(c):
		(_far_nodes[c] as Node3D).visible = false
	_build_props(st)
	_build_traffic_lights(st)
	_build_occluders(st)
	chunk_loaded.emit(c)


func _unload(c: Vector2i) -> void:
	var st: ChunkState = _chunks[c]
	_chunks.erase(c)
	if is_instance_valid(st.root):
		st.root.queue_free()
	lamp_lights.erase(c)
	if _far_nodes.has(c):
		(_far_nodes[c] as Node3D).visible = true
	chunk_unloaded.emit(c)


# ------------------------------------------------------------------ props
func _build_props(st: ChunkState) -> void:
	if props == null:
		return
	var key := "%d_%d" % [st.coord.x, st.coord.y]
	var list: Array = world.props_by_chunk.get(key, [])
	if list.is_empty():
		return
	var groups := {}
	var lights := PackedVector3Array()
	for pr in list:
		var t: String = pr[0]
		var variant := int(pr[5])
		var gkey := t
		if props.variants.has(t) and int(props.variants[t]) > 1:
			gkey = "%s#%d" % [t, variant % int(props.variants[t])]
		if not groups.has(gkey):
			groups[gkey] = []
		groups[gkey].append(pr)
		for lp in props.light_points(t):
			var basis := Basis(Vector3.UP, float(pr[4]))
			lights.append(Vector3(pr[1], pr[2], pr[3]) + basis * Vector3(lp[0], lp[1], lp[2]))
	lamp_lights[st.coord] = lights
	var holder := Node3D.new()
	holder.name = "Props"
	st.root.add_child(holder)
	for gkey in groups:
		var parts := String(gkey).split("#")
		var t := parts[0]
		var variant := int(parts[1]) if parts.size() > 1 else 0
		var cat := props.category(t)
		for lod in [0, 1]:
			var mesh := props.mesh_for(t, variant, lod)
			if mesh == null:
				continue
			if lod == 1 and float(cat[1]) <= 0.0:
				continue
			var mm := MultiMesh.new()
			mm.transform_format = MultiMesh.TRANSFORM_3D
			mm.use_colors = t == "container"
			mm.mesh = mesh
			var arr: Array = groups[gkey]
			mm.instance_count = arr.size()
			for i in arr.size():
				var pr: Array = arr[i]
				var sc := 1.0
				if cat == PropLibrary.CATEGORY["tree"]:
					sc = 0.8 + 0.12 * float(int(pr[5]) % 4)
				var b := Basis(Vector3.UP, float(pr[4])).scaled(Vector3(sc, sc, sc))
				mm.set_instance_transform(i, Transform3D(b, Vector3(pr[1], pr[2], pr[3])))
				if mm.use_colors:
					mm.set_instance_color(i, PropLibrary.CONTAINER_COLORS[int(pr[5]) % PropLibrary.CONTAINER_COLORS.size()])
			var mmi := MultiMeshInstance3D.new()
			mmi.multimesh = mm
			mmi.name = "%s_L%d" % [t, lod]
			if lod == 0:
				mmi.visibility_range_end = float(cat[0])
				mmi.visibility_range_end_margin = 10.0
			else:
				mmi.visibility_range_begin = float(cat[0])
				mmi.visibility_range_end = float(cat[1])
				mmi.visibility_range_begin_margin = 10.0
			mmi.visibility_range_fade_mode = GeometryInstance3D.VISIBILITY_RANGE_FADE_SELF
			mmi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON if (cat[2] and lod == 0) else GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
			holder.add_child(mmi)
			st.multimeshes.append(mmi)


# ------------------------------------------------------------------ traffic lights
func _build_traffic_lights(st: ChunkState) -> void:
	var sigs: Array = _signals_by_chunk.get(st.coord, [])
	if sigs.is_empty() or props == null:
		return
	var pole := props.mesh_for("tl_pole", 0, 0)
	var arm := props.mesh_for("tl_arm", 0, 0)
	var head := props.mesh_for("tl_head", 0, 0)
	if pole == null or head == null:
		return
	var head_mesh: Mesh = head.duplicate()
	for i in head_mesh.get_surface_count():
		var m := head_mesh.surface_get_material(i)
		if m and m.resource_name == "signal_lens":
			head_mesh.surface_set_material(i, tl_head_mat)
	var mm_pole := MultiMesh.new()
	mm_pole.transform_format = MultiMesh.TRANSFORM_3D
	mm_pole.mesh = pole
	mm_pole.instance_count = sigs.size()
	var mm_arm := MultiMesh.new()
	mm_arm.transform_format = MultiMesh.TRANSFORM_3D
	mm_arm.mesh = arm
	mm_arm.instance_count = sigs.size()
	var heads := []
	for i in sigs.size():
		var sg: Dictionary = sigs[i]
		var pos := Vector3(sg["pos"][0], sg["pos"][1], sg["pos"][2])
		var b := Basis(Vector3.UP, float(sg["rot"]))
		mm_pole.set_instance_transform(i, Transform3D(b, pos))
		var armlen: float = sg["arm"]
		mm_arm.set_instance_transform(i, Transform3D(b * Basis.from_scale(Vector3(armlen, 1.0, 1.0)), pos))
		var nh := 1 if armlen < 5.0 else 2
		for k in nh:
			var off := -armlen * (0.5 if nh == 1 else (0.35 + 0.5 * k))
			heads.append([Transform3D(b, pos + b * Vector3(off, 5.35, 0.0)), int(sg["node"]), int(sg["edge"])])
		# pole mounted secondary head, facing traffic
		heads.append([Transform3D(b, pos + b * Vector3(0.0, 3.2, 0.3)), int(sg["node"]), int(sg["edge"])])
	var mm_head := MultiMesh.new()
	mm_head.transform_format = MultiMesh.TRANSFORM_3D
	mm_head.use_custom_data = true
	mm_head.mesh = head_mesh
	mm_head.instance_count = heads.size()
	for i in heads.size():
		mm_head.set_instance_transform(i, heads[i][0])
		st.tl_index.append([heads[i][1], heads[i][2]])
	var holder := Node3D.new()
	holder.name = "TrafficLights"
	st.root.add_child(holder)
	for mm in [mm_pole, mm_arm]:
		var mi := MultiMeshInstance3D.new()
		mi.multimesh = mm
		mi.visibility_range_end = 380.0
		holder.add_child(mi)
	st.tl_heads = MultiMeshInstance3D.new()
	st.tl_heads.multimesh = mm_head
	st.tl_heads.visibility_range_end = 380.0
	holder.add_child(st.tl_heads)
	_refresh_heads(st)


func _refresh_heads(st: ChunkState) -> void:
	if st.tl_heads == null or signals_ctl == null:
		return
	var mm := st.tl_heads.multimesh
	for i in st.tl_index.size():
		var s := signals_ctl.state(st.tl_index[i][0], st.tl_index[i][1])
		mm.set_instance_custom_data(i, Color(float(s), 0.0, 0.0, 0.0))


func _update_traffic_lights() -> void:
	for c in _chunks:
		var st: ChunkState = _chunks[c]
		if st.tl_heads and world.chunk_center(c).distance_to(focus) < 450.0:
			_refresh_heads(st)


# ------------------------------------------------------------------ occluders
func _build_occluders(st: ChunkState) -> void:
	var ids: Array = world.buildings_by_chunk.get(st.coord, [])
	if ids.is_empty():
		return
	var holder := Node3D.new()
	holder.name = "Occluders"
	st.root.add_child(holder)
	for id in ids:
		var b: Dictionary = world.buildings[id]
		var h: float = b["h"]
		if h < 9.0:
			continue
		var fp: Array = b["fp"]
		var obb := _obb(fp)
		if obb.is_empty() or obb["hu"] < 4.0 or obb["hv"] < 4.0:
			continue
		var occ := OccluderInstance3D.new()
		var box := BoxOccluder3D.new()
		var shrink := 0.6
		box.size = Vector3((obb["hu"] - shrink) * 2.0, h * 0.9, (obb["hv"] - shrink) * 2.0)
		occ.occluder = box
		var u: Vector2 = obb["u"]
		var c: Vector2 = obb["c"]
		occ.transform = Transform3D(Basis(Vector3.UP, atan2(-u.y, u.x)), Vector3(c.x, float(b["y"]) + h * 0.45, c.y))
		holder.add_child(occ)


static func _obb(fp: Array) -> Dictionary:
	var pts := []
	for q in fp:
		pts.append(Vector2(q[0], q[1]))
	var best := {}
	var best_area := INF
	for i in pts.size():
		var e: Vector2 = (pts[(i + 1) % pts.size()] - pts[i])
		if e.length() < 0.5:
			continue
		e = e.normalized()
		var v := Vector2(-e.y, e.x)
		var umin := INF
		var umax := -INF
		var vmin := INF
		var vmax := -INF
		for p in pts:
			var pu: float = p.dot(e)
			var pv: float = p.dot(v)
			umin = minf(umin, pu)
			umax = maxf(umax, pu)
			vmin = minf(vmin, pv)
			vmax = maxf(vmax, pv)
		var area := (umax - umin) * (vmax - vmin)
		if area < best_area:
			best_area = area
			best = {"u": e, "c": e * (umin + umax) * 0.5 + v * (vmin + vmax) * 0.5,
				"hu": (umax - umin) * 0.5, "hv": (vmax - vmin) * 0.5}
	return best
