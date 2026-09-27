class_name BreakableProps
extends Node
## Street furniture reacts to cars: lamp posts, fences, bins, bushes and small trees are
## knocked over (the instanced prop is hidden and a physics copy flies off), while big
## trees get solid trunk colliders near the player so cars crash into them instead of
## driving through.

const KNOCK := {
	# type: [hit radius, mass, pivot height of the physics copy (fraction of the height)]
	"lamp_street": [0.35, 180.0], "lamp_plaza": [0.4, 120.0], "lamp_wood": [0.3, 140.0],
	"lamp_highway": [0.35, 260.0], "lamp_highway_double": [0.4, 320.0],
	"dumpster": [1.0, 260.0], "bush": [1.0, 60.0], "hedge": [1.1, 80.0],
	"tree_cypress": [0.45, 220.0], "palm_short": [0.4, 200.0], "tree_round": [0.45, 260.0],
	"fence_wood": [0.2, 40.0], "fence_chain": [0.2, 45.0], "fence_construction": [0.3, 60.0],
	"bus_stop": [1.2, 400.0],
}
const FENCES := ["fence_wood", "fence_chain", "fence_construction"]
const SOLID := {"palm_tall": 0.35, "tree_oak": 0.45, "tree_pine": 0.4}
const MAX_DEBRIS := 24

var world: GameWorld
var _cache := {}               # Vector2i -> Array of [index, pos, yaw, type]
var _last_pos := {}            # vehicle -> previous position
var _debris: Array = []
var _trunks: Array[StaticBody3D] = []
var _trunk_timer := 0.0


func _ready() -> void:
	name = "BreakableProps"
	world = GameWorld.instance
	world.streaming.chunk_unloaded.connect(func(c): _cache.erase(c))
	for i in 40:
		var b := StaticBody3D.new()
		b.collision_layer = 1
		b.set_meta("surface", "wood")
		var cs := CollisionShape3D.new()
		var cy := CylinderShape3D.new()
		cy.radius = 0.4
		cy.height = 6.0
		cs.shape = cy
		cs.position.y = 3.0
		b.add_child(cs)
		b.process_mode = Node.PROCESS_MODE_DISABLED
		add_child(b)
		b.global_position = Vector3(0, -9999, 0)
		_trunks.append(b)


func _list(c: Vector2i) -> Array:
	if _cache.has(c):
		return _cache[c]
	var out := []
	var raw: Array = world.data.props_by_chunk.get("%d_%d" % [c.x, c.y], [])
	for i in raw.size():
		var pr: Array = raw[i]
		var t: String = pr[0]
		if KNOCK.has(t) or SOLID.has(t):
			out.append([i, Vector3(pr[1], pr[2], pr[3]), float(pr[4]), t])
	_cache[c] = out
	return out


func _physics_process(_delta: float) -> void:
	var p := world.player as Player
	if p == null or world.traffic == null:
		return
	var cands: Array = world.traffic.call("vehicles_near", p.global_position, 90.0)
	if p.vehicle is Vehicle and not p.vehicle in cands:
		cands.append(p.vehicle)
	for v in cands:
		var veh := v as Vehicle
		if veh == null or veh.kinematic_mode:
			continue
		var pos := veh.global_position
		var prev: Vector3 = _last_pos.get(veh, pos)
		_last_pos[veh] = pos
		if veh.linear_velocity.length() < 3.0:
			continue
		_check_vehicle(veh, prev, pos)
	if _last_pos.size() > 120:
		_last_pos.clear()


func _check_vehicle(veh: Vehicle, a: Vector3, b: Vector3) -> void:
	var c := world.data.chunk_of(b)
	var half := float(veh.meta.get("width", 1.9)) * 0.5
	var length := float(veh.meta.get("length", 4.5)) * 0.5
	var fwd := -veh.global_basis.z
	# sweep the front half of the car from last frame to now
	var fa := a + fwd * length * 0.6
	var fb := b + fwd * length * 0.6
	for dx in [-1, 0, 1]:
		for dz in [-1, 0, 1]:
			var cc := c + Vector2i(dx, dz)
			if not world.streaming.is_chunk_loaded(cc):
				continue
			for e in _list(cc):
				var t: String = e[3]
				if not KNOCK.has(t):
					continue
				var pp: Vector3 = e[1]
				if absf(pp.y - b.y) > 4.0:
					continue
				var r: float = KNOCK[t][0] + half
				var d := _dist_to_prop(fa, fb, pp, float(e[2]), t)
				if d < r and world.streaming.break_prop(cc, int(e[0])):
					_knock(veh, cc, e)


func _dist_to_prop(a: Vector3, b: Vector3, pp: Vector3, yaw: float, t: String) -> float:
	var a2 := Vector2(a.x, a.z)
	var b2 := Vector2(b.x, b.z)
	if t in FENCES:
		# fence panels are 3 m long along their local X
		var ax := Vector2(cos(yaw), -sin(yaw)) * 1.5
		var p0 := Vector2(pp.x, pp.z) - ax
		var p1 := Vector2(pp.x, pp.z) + ax
		return minf(minf(_seg_point(a2, b2, p0), _seg_point(a2, b2, p1)), _seg_seg(a2, b2, p0, p1))
	return _seg_point(a2, b2, Vector2(pp.x, pp.z))


func _seg_point(a: Vector2, b: Vector2, p: Vector2) -> float:
	var ab := b - a
	var t := clampf((p - a).dot(ab) / maxf(ab.length_squared(), 1e-6), 0.0, 1.0)
	return (a + ab * t).distance_to(p)


func _seg_seg(a: Vector2, b: Vector2, c: Vector2, d: Vector2) -> float:
	var n := 8
	var best := INF
	for i in n + 1:
		best = minf(best, _seg_point(a, b, c.lerp(d, float(i) / n)))
	return best


func _knock(veh: Vehicle, c: Vector2i, e: Array) -> void:
	var t: String = e[3]
	var mass: float = KNOCK[t][1]
	var vel := veh.linear_velocity
	# the car loses a little speed, heavier props cost more
	veh.linear_velocity = vel * clampf(1.0 - mass / (veh.mass * 3.0), 0.8, 0.98)
	AudioManager.play_3d("crash_%d" % (randi() % 3), e[1] + Vector3.UP, -6.0 + mass / 80.0, 1.2)
	if t.begins_with("lamp"):
		VFX.burst(e[1] + Vector3.UP * 1.0, Vector3.UP, "sparks")
	if veh.driver is Player:
		var pl := veh.driver as Player
		if pl.cam:
			pl.cam.add_shake(clampf(mass / 400.0, 0.1, 0.6))
	_spawn_debris(e, vel, mass)


func _spawn_debris(e: Array, vel: Vector3, mass: float) -> void:
	var t: String = e[3]
	var mesh := world.props.mesh_for(t, 0, 0) if world.props else null
	if mesh == null:
		return
	var rb := RigidBody3D.new()
	rb.mass = mass
	rb.collision_layer = 1 << 4
	rb.collision_mask = 1 | (1 << 2) | (1 << 4)
	var mi := MeshInstance3D.new()
	mi.mesh = mesh
	rb.add_child(mi)
	var box: AABB = world.props.aabb(t)
	var cs := CollisionShape3D.new()
	var bs := BoxShape3D.new()
	var sz := box.size
	if t.begins_with("lamp"):
		sz = Vector3(0.3, box.size.y, 0.3)   # just the pole; the arm is thin
		cs.position = Vector3(0, box.size.y * 0.5, 0)
	else:
		cs.position = box.get_center()
	bs.size = Vector3(maxf(sz.x, 0.15), maxf(sz.y, 0.15), maxf(sz.z, 0.15))
	cs.shape = bs
	rb.add_child(cs)
	world.add_child(rb)
	rb.global_transform = Transform3D(Basis(Vector3.UP, float(e[2])), e[1])
	# topple: push at the hit height along the car's direction, plus a little lift
	var push := vel * mass * 0.55 + Vector3.UP * mass * 1.5
	rb.apply_impulse(push, Vector3.UP * minf(box.size.y * 0.35, 1.2))
	rb.angular_velocity = Vector3(randf_range(-1, 1), randf_range(-0.5, 0.5), randf_range(-1, 1))
	_debris.append(rb)
	get_tree().create_timer(40.0).timeout.connect(func():
		if is_instance_valid(rb):
			rb.queue_free())
	while _debris.size() > MAX_DEBRIS:
		var old = _debris.pop_front()
		if is_instance_valid(old):
			old.queue_free()


func _process(delta: float) -> void:
	# solid trunks for the big trees around the player (a small pool of static cylinders)
	_trunk_timer -= delta
	if _trunk_timer > 0.0:
		return
	_trunk_timer = 0.5
	var p := world.player as Node3D
	if p == null:
		return
	var pp := p.global_position
	var near := []
	var c := world.data.chunk_of(pp)
	for dx in [-1, 0, 1]:
		for dz in [-1, 0, 1]:
			var cc := c + Vector2i(dx, dz)
			if not world.streaming.is_chunk_loaded(cc):
				continue
			for e in _list(cc):
				if SOLID.has(e[3]):
					var d: float = (e[1] as Vector3).distance_squared_to(pp)
					if d < 70.0 * 70.0:
						near.append([d, e])
	near.sort_custom(func(x, y): return x[0] < y[0])
	for i in _trunks.size():
		var b := _trunks[i]
		if i < near.size():
			var e: Array = near[i][1]
			b.process_mode = Node.PROCESS_MODE_INHERIT
			b.global_position = e[1]
			((b.get_child(0) as CollisionShape3D).shape as CylinderShape3D).radius = SOLID[e[3]]
		else:
			b.global_position = Vector3(0, -9999, 0)
			b.process_mode = Node.PROCESS_MODE_DISABLED
