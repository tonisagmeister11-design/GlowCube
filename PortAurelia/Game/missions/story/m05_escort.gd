extends Mission
## 5 · Eskorte – pick up Mr. Castell at the Grand Solace Hotel and bring him to the airport.
## A rival crew pursues in two cars and fires at you (drive-by).

var _vip_model: CharacterModel
var _enemies: Array = []
var _shoot_cd := {}


func run() -> void:
	var hotel := poi("landmark_grand_hotel")
	var pos := poi_pos(hotel, 6.0)
	var vip := spawn_npc("business", sidewalk_near(pos))
	vip.bravery = 0.0
	add_blip(vip, Color(0.3, 0.9, 0.5), "", true)
	fail_if(func(): return not is_instance_valid(vip) or vip.is_dead(), "Mr. Castell ist tot.")
	objective("Hol Mr. Castell mit einem Auto am %s ab." % hotel["name"])
	if not await until(func():
			return player.is_in_vehicle() and (player.vehicle as Vehicle).speed() < 2.0 \
				and player.vehicle.global_position.distance_to(vip.global_position) < 9.0 \
				and not (player.vehicle as Vehicle).def.get("bike", false)):
		return
	# VIP gets in (passenger seat)
	var car := player.vehicle as Vehicle
	var outfit := vip.outfit
	_fail_checks.clear()
	blip_list.clear()
	vip.queue_free()
	_vip_model = CharacterModel.new()
	_vip_model.outfit = outfit
	car.add_child(_vip_model)
	var seat := car.driver_seat_transform()
	seat.origin.x = -seat.origin.x
	_vip_model.transform = seat
	_vip_model.set_mode("drive")
	fail_if(func(): return not is_instance_valid(car) or car.destroyed, "Mr. Castell hat den Anschlag nicht überlebt.")
	fail_if(func(): return player.vehicle != car and player.global_position.distance_to(car.global_position) > 40.0,
		"Du hast Mr. Castell zurückgelassen.")
	var airport := poi("airport_terminal")
	objective("Bring Mr. Castell zum Flughafen.")
	Events.subtitle.emit("Castell: \"Fahren Sie! Ich werde verfolgt.\"", 4.0)
	# pursuers after a short while
	get_tree().create_timer(12.0).timeout.connect(_spawn_pursuers)
	if not await reach(poi_pos(airport, 8.0), 10.0, true):
		return
	Events.subtitle.emit("Castell: \"Ausgezeichnete Arbeit. Marco wird zufrieden sein.\"", 4.0)
	await wait(1.5)
	if is_instance_valid(_vip_model):
		_vip_model.queue_free()
	complete()


func _spawn_pursuers() -> void:
	if not active:
		return
	for i in 2:
		var rp := road_point(player.global_position, 70.0, 160.0)
		if rp.is_empty():
			continue
		var v := spawn_vehicle("suv", rp["pos"], rp["dir"], Color(0.05, 0.05, 0.06))
		var drv := drive(v, Outfits.random("gang", RandomNumberGenerator.new()))
		if drv:
			drv.pursue(player.vehicle if player.vehicle else player)
			drv.aggression = 1.3
		_enemies.append(v)
		add_blip(v, Color(0.95, 0.2, 0.2), "", false)
	Events.notify.emit("Verfolger! Häng sie ab oder mach ihre Wagen fahruntüchtig.", 4.0)


func _process(delta: float) -> void:
	super(delta)
	if not active:
		return
	# drive-by fire from pursuing cars
	for v in _enemies:
		if not is_instance_valid(v) or (v as Vehicle).destroyed or (v as Vehicle).engine_health < 150.0:
			continue
		var veh := v as Vehicle
		var tgt: Node3D = player.vehicle if player.vehicle else player
		var d := veh.global_position.distance_to(tgt.global_position)
		_shoot_cd[v] = float(_shoot_cd.get(v, 0.0)) - delta
		if d < 30.0 and float(_shoot_cd[v]) <= 0.0:
			_shoot_cd[v] = randf_range(0.18, 0.35)
			var from := veh.global_position + Vector3.UP * 1.3 + veh.global_basis.x * 0.9
			var to := tgt.global_position + Vector3.UP * randf_range(0.6, 1.2) + Vector3(randf_range(-1, 1), 0, randf_range(-1, 1))
			var q := PhysicsRayQueryParameters3D.create(from, to)
			q.exclude = [veh.get_rid()]
			q.collision_mask = 1 | (1 << 1) | (1 << 2)
			var r := veh.get_world_3d().direct_space_state.intersect_ray(q)
			VFX.muzzle_flash(from, (to - from).normalized(), 0.8)
			AudioManager.play_weapon("smg", from, false)
			if not r.is_empty():
				VFX.tracer(from, r["position"])
				if randf() < 0.55:
					Combat.apply_damage(r["collider"], 9.0, veh, r["position"], (to - from).normalized())
