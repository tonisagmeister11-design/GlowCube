extends Node3D
## Drift handling on an open flat pad (headless): Shift-drift holds a controlled slide angle,
## hard steering at speed drifts on its own, and letting go straightens the car out.

var failures := 0
var checks := 0


func check(name: String, ok: bool, info := "") -> void:
	checks += 1
	if not ok:
		failures += 1
	print("%s %s  (%s)" % ["PASS" if ok else "FAIL", name, info])


func _ready() -> void:
	var ground := StaticBody3D.new()
	var cs := CollisionShape3D.new()
	var box := BoxShape3D.new()
	box.size = Vector3(4000, 2, 4000)
	cs.shape = box
	ground.add_child(cs)
	ground.position.y = -1.0
	add_child(ground)
	for id in ["sports", "sedan"]:
		await _run(id)
	print("=== %d checks, %d failed ===" % [checks, failures])
	get_tree().quit(1 if failures > 0 else 0)


func _slip(v: Vehicle) -> float:
	var vel := v.linear_velocity * Vector3(1, 0, 1)
	if vel.length() < 3.0:
		return 0.0
	return rad_to_deg((-v.global_basis.z * Vector3(1, 0, 1)).normalized().angle_to(vel.normalized()))


func _drive(v: Vehicle, t: float, throttle: float, steer: float, drift: bool) -> Array:
	v.throttle = throttle
	v.steer_input = steer
	v.drift_input = drift
	v.brake_input = 0.0
	var peak := 0.0
	var last := 0.0
	var n := int(t * 60.0)
	for i in n:
		await get_tree().physics_frame
		last = _slip(v)
		peak = maxf(peak, last)
	return [peak, last]


func _run(id: String) -> void:
	var v := Vehicle.create(id)
	v.ai_driver = Node.new()
	v.auto_drift = true
	add_child(v)
	v.global_position = Vector3(0, 1.0, 0)
	for i in 30:
		await get_tree().physics_frame
	await _drive(v, 6.0, 1.0, 0.0, false)
	var spd0 := v.speed_kmh
	var r: Array = await _drive(v, 2.5, 1.0, -1.0, true)
	check(id + " shift drift slides", r[0] > 15.0, "peak slip %.0f deg from %.0f km/h" % [r[0], spd0])
	check(id + " shift drift no spin-out", r[0] < 75.0 and v.speed_kmh > 15.0, "peak %.0f deg, %.0f km/h" % [r[0], v.speed_kmh])
	r = await _drive(v, 2.0, 0.6, 0.0, false)
	check(id + " straightens after drift", r[1] < 10.0, "slip %.0f deg" % r[1])
	await _drive(v, 4.0, 1.0, 0.0, false)
	r = await _drive(v, 2.0, 1.0, 1.0, false)
	check(id + " hard corner drifts", r[0] > 10.0 and r[0] < 75.0, "peak slip %.0f deg at %.0f km/h" % [r[0], v.speed_kmh])
	check(id + " stays upright", v.global_basis.y.dot(Vector3.UP) > 0.9)
	v.ai_driver.free()
	v.queue_free()
	await get_tree().physics_frame
