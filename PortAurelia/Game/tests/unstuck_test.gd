extends Node
## Stuck in the ground: automatic rescue, the U key (on foot and in a car), never into a building,
## and a spawn below the ground is corrected.
##   godot --headless --path Game res://tests/unstuck_test.tscn

var world: GameWorld
var results := []


func _ready() -> void:
	Game.player_data = PlayerData.new()
	Game.pending_slot = -2
	world = load("res://scenes/world.tscn").instantiate()
	add_child(world)
	await Events.world_ready
	await wait(1.5)
	Game.state = Game.State.PLAYING
	var p := world.player as Player
	p.health.invulnerable = true
	var home := p.global_position
	# 1. fallen into the ground: out automatically
	var under := _ground(home) + Vector3.DOWN * 6.0
	p.global_position = under
	p.velocity = Vector3.ZERO
	await wait(0.3)
	var sp := world.get_world_3d().direct_space_state
	for back in [true, false]:
		var qq := PhysicsRayQueryParameters3D.create(p.global_position + Vector3.UP * 0.3, p.global_position + Vector3.UP * 14.0)
		qq.collision_mask = 1
		qq.hit_back_faces = back
		qq.exclude = [p.get_rid()]
		print("  up back=%s: %s" % [back, sp.intersect_ray(qq)])
	var qd := PhysicsRayQueryParameters3D.create(p.global_position + Vector3.UP * 0.3, p.global_position + Vector3.DOWN * 40.0)
	qd.collision_mask = 1
	qd.exclude = [p.get_rid()]
	print("  down: %s  state %d  under %s  ground %s" % [sp.intersect_ray(qd), p.state, SafeSpot.is_under_ground(p.global_position, [p.get_rid()]), _ground(home)])
	check("fallen through the ground: rescued automatically", await wait_until(func(): return _outdoors_on_ground(p.global_position), 6.0),
		"at %s" % p.global_position)
	# 2. the U key, many places around the city: always outdoors on the ground, never in a house
	var bad := 0
	var tries := 0
	for i in 12:
		var a := i / 12.0 * TAU
		var q := home + Vector3(cos(a), 0, sin(a)) * (60.0 + i * 25.0)
		world.streaming.load_area_blocking(q, 150.0)
		p.global_position = Vector3(q.x, _ground(q).y - 4.0, q.z)   # in the ground, maybe under a building
		p._rescue_ms = -100000
		_press_u()
		await wait(0.4)
		tries += 1
		if not _outdoors_on_ground(p.global_position):
			bad += 1
			print("  not outdoors: ", p.global_position)
	check("U always brings you outdoors onto the ground (never into a building)", bad == 0, "%d of %d bad" % [bad, tries])
	# 3. stays standing there (no falling again)
	var y0 := p.global_position.y
	await wait(2.0)
	check("you stand on the ground after the rescue", absf(p.global_position.y - y0) < 1.0 and p.is_on_floor(), "%.2f -> %.2f" % [y0, p.global_position.y])
	# 4. in a car stuck in the ground
	var car := Vehicle.create("sedan")
	world.add_child(car)
	car.global_position = p.global_position + Vector3(3, 1, 0)
	await wait(0.6)
	p.enter_vehicle(car)
	await wait(0.4)
	car.global_position = _ground(car.global_position) + Vector3.DOWN * 5.0
	p._rescue_ms = -100000
	_press_u()
	for i in 10:
		await wait(0.1)
		print("  car y %.2f at %s vel %s" % [car.global_position.y, car.global_position, car.linear_velocity])
	check("U with a car: the car is back on the road", p.is_in_vehicle() and _outdoors_on_ground(car.global_position + Vector3.DOWN * 0.5, 2.0),
		"car at %s" % car.global_position)
	var failed := results.filter(func(r): return not r[1]).size()
	print("=== %d checks, %d failed ===" % [results.size(), failed])
	Game.stop_world()
	get_tree().quit(1 if failed > 0 else 0)


func _press_u() -> void:
	var ev := InputEventAction.new()
	ev.action = "unstuck"
	ev.pressed = true
	Input.parse_input_event(ev)


## Highest surface at the xz of p (roof or ground).
func _ground(p: Vector3) -> Vector3:
	var q := PhysicsRayQueryParameters3D.create(Vector3(p.x, 300, p.z), Vector3(p.x, -50, p.z))
	q.collision_mask = 1
	var h := world.get_world_3d().direct_space_state.intersect_ray(q)
	return h["position"] if not h.is_empty() else p


## Standing on something, with open sky above (not inside or under a building).
func _outdoors_on_ground(pos: Vector3, tol := 1.2) -> bool:
	var space := world.get_world_3d().direct_space_state
	var down := PhysicsRayQueryParameters3D.create(pos + Vector3.UP * 0.5, pos + Vector3.DOWN * 2.0)
	down.collision_mask = 1
	var d := space.intersect_ray(down)
	if d.is_empty() or (d["position"] as Vector3).distance_to(pos) > tol:
		return false
	var up := PhysicsRayQueryParameters3D.create(pos + Vector3.UP * 0.6, pos + Vector3.UP * 40.0)
	up.collision_mask = 1
	var u := space.intersect_ray(up)
	return u.is_empty() or (u["position"] as Vector3).y > pos.y + 3.5   # trees/awnings are fine, ceilings are not


func check(n: String, ok: bool, info := "") -> void:
	results.append([n, ok])
	print(("PASS " if ok else "FAIL ") + n + ("  (" + info + ")" if info != "" else ""))


func wait(t: float) -> void:
	await get_tree().create_timer(t).timeout


func wait_until(cond: Callable, timeout: float) -> bool:
	var t := 0.0
	while t < timeout:
		if cond.call():
			return true
		await wait(0.1)
		t += 0.1
	return cond.call()
