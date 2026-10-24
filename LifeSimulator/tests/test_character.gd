extends Node
## Tests fuer Aussehen, Proportionen, Humanoid (alle Optionen, LOD,
## Aktionen) und den Spieler-Controller (Bewegung, Bordstein, Wand,
## Interaktion, Fahrzeug) in einem kleinen Testlevel.

var runner


func test_life_stage_boundaries() -> void:
	var cases := [[0.5, "baby"], [1.99, "baby"], [2.0, "toddler"], [3.99, "toddler"], [4.0, "child"],
		[11.9, "child"], [12.0, "teen"], [17.99, "teen"], [18.0, "young_adult"], [30.0, "adult"],
		[50.0, "older_adult"], [64.9, "older_adult"], [65.0, "senior"], [100.0, "senior"]]
	for c in cases:
		runner.check_eq(Appearance.life_stage(c[0]), c[1], "Lebensphase fuer %s" % str(c[0]))
	runner.check_eq(Appearance.LIFE_STAGES.size(), 8, "Anzahl Lebensphasen")
	runner.check_eq(Appearance.life_stage_label(70.0), "Senior", "Label")
	for st in Appearance.LIFE_STAGES:
		runner.check_eq(Appearance.life_stage(st.default), st.id, "Standardalter liegt in Phase %s" % st.id)


func test_appearance_validation() -> void:
	var v := Appearance.validate({"skin": "#ff0000", "hair_style": 999, "face": {"jaw": 5.0, "nose": "x"},
		"accessories": ["cap", "hat", "glasses", "glasses", "unbekannt"], "top": {"style": 5, "color": [0.1, 0.2, 0.3]}})
	runner.check_eq(v.skin, Color(1, 0, 0), "Farbe aus Hex-String")
	runner.check_eq(v.hair_style, 0, "Unbekannte Frisur -> Standard")
	runner.check_near(v.face.jaw, 1.0, 0.001, "Regler begrenzt")
	runner.check_near(v.face.nose, 0.5, 0.001, "Ungueltiger Regler -> 0.5")
	runner.check_eq(v.accessories, ["cap", "glasses"], "Ein Teil pro Slot, keine Duplikate")
	runner.check_eq(Appearance.key_of(Appearance.BOTTOMS, v.bottom.style), "dress", "Kleid setzt Rockteil")
	runner.check_eq(v.bottom.color, v.top.color, "Rockteil hat Kleidfarbe")
	var baby := Appearance.validate({"top": {"style": 0}, "beard": 4, "accessories": ["watch"]}, 0.8)
	runner.check_eq(Appearance.key_of(Appearance.TOPS, baby.top.style), "onesie", "Baby traegt Strampler")
	runner.check_eq(baby.beard, 0, "Kein Bart bei Babys")
	runner.check(baby.accessories.is_empty(), "Keine Uhr fuer Babys")
	var garbage := Appearance.validate(null)
	runner.check(garbage.has("build") and garbage.has("shoes"), "Leere Eingabe ergibt vollstaendige Daten")
	var rng := RandomNumberGenerator.new()
	rng.seed = 42
	for age in [0.6, 1.5, 3.0, 8.0, 14.0, 25.0, 45.0, 70.0, 95.0]:
		for b in ["female", "male"]:
			var r := Appearance.random(rng, b, age)
			runner.check_eq(var_to_str(Appearance.validate(r, age)), var_to_str(r), "Zufall ist gueltig (%s, %s)" % [b, str(age)])
	runner.check(Appearance.random_name(rng, "male").split(" ").size() == 2, "Zufallsname")


func test_proportions_by_age() -> void:
	var p3 := BodyProportions.compute(3.0, "female")
	var p30 := BodyProportions.compute(30.0, "female")
	var r3 := p3.head_to_height()
	var r30 := p30.head_to_height()
	runner.check(r3 > 4.0 and r3 < 5.0, "Kopfverhaeltnis Kleinkind ~1:4,5 (ist %.2f)" % r3)
	runner.check(r30 > 7.2 and r30 < 7.8, "Kopfverhaeltnis Erwachsene ~1:7,5 (ist %.2f)" % r30)
	var p7 := BodyProportions.compute(7.0, "male")
	var r7 := p7.head_to_height()
	runner.check(r7 > 5.0 and r7 < 6.2, "Kopfverhaeltnis Kind 1:5-6 (ist %.2f)" % r7)
	var p15 := BodyProportions.compute(15.0, "male")
	runner.check(absf(p15.head_to_height() - 7.0) < 0.35, "Kopfverhaeltnis Jugendliche ~1:7")
	var hs := []
	for a in [1.0, 6.0, 12.0, 18.0]:
		hs.append(BodyProportions.compute(a, "male").height)
	runner.check(hs[0] < hs[1] and hs[1] < hs[2] and hs[2] < hs[3], "Groesse steigt mit dem Alter: %s" % str(hs))
	# Kinder sind keine skalierten Erwachsenen: relativ kuerzere Beine
	runner.check(p3.leg_length() / p3.height < p30.leg_length() / p30.height - 0.05, "Kinder haben relativ kuerzere Beine")
	runner.check(BodyProportions.compute(90.0, "male").height < BodyProportions.compute(40.0, "male").height, "Senioren schrumpfen")
	runner.check(BodyProportions.compute(85.0, "female").stoop > 0.5, "Gebeugte Haltung im Alter")
	runner.check(BodyProportions.compute(80.0, "female").grey > 0.9, "Graue Haare im Alter")
	runner.check(BodyProportions.compute(20.0, "female").grey < 0.05, "Keine grauen Haare mit 20")
	var tall := BodyProportions.compute(30.0, "male", {"height": 1.0})
	var short := BodyProportions.compute(30.0, "male", {"height": 0.0})
	runner.check(tall.height > short.height + 0.15, "Groessenregler wirkt")
	runner.check(BodyProportions.compute(30.0, "male").shoulder_w > p30.shoulder_w, "Maennliche Schultern breiter")
	runner.check(p30.hip_w > BodyProportions.compute(30.0, "male").hip_w, "Weibliche Huefte breiter")


func test_humanoid_heights_and_lod() -> void:
	var last := 0.0
	for a in [1.0, 3.0, 7.0, 12.0, 18.0]:
		var h := Humanoid.new()
		add_child(h)
		h.setup(Appearance.defaults("male", a), a, "male")
		runner.check(h.height() > last, "Humanoid-Hoehe steigt (%s Jahre: %.2f)" % [str(a), h.height()])
		runner.check(h.get_skeleton() != null and h.get_skeleton().get_bone_count() == HumanoidBuilder.BONE_COUNT, "Skelett vollstaendig")
		last = h.height()
		h.queue_free()
	var app := Appearance.defaults("female", 30.0)
	var counts := []
	for level in 3:
		var n := Humanoid.create_lod(app, 30.0, "female", level)
		runner.check(n is Humanoid, "create_lod liefert Humanoid")
		add_child(n)
		var mi: MeshInstance3D = (n as Humanoid).get_mesh_instance()
		runner.check(mi != null and mi.mesh != null, "LOD %d hat Mesh" % level)
		var verts := 0
		for s in mi.mesh.get_surface_count():
			verts += (mi.mesh.surface_get_arrays(s)[Mesh.ARRAY_VERTEX] as PackedVector3Array).size()
		counts.append(verts)
		if level == 2:
			runner.check((n as Humanoid).get_skeleton() == null, "LOD 2 ohne Skelett")
			runner.check(mi.mesh.get_surface_count() == 1, "LOD 2 mit einer Oberflaeche")
		n.queue_free()
	runner.check(counts[0] > counts[1] and counts[1] > counts[2], "LOD-Stufen werden guenstiger: %s" % str(counts))
	runner.check(counts[2] < 2500, "LOD 2 ist leicht (%d Vertices)" % counts[2])


func test_all_options_build() -> void:
	HumanoidBuilder.clear_cache()
	var base := Appearance.defaults("female", 30.0)
	var built := 0
	var cats := [["hair_style", Appearance.HAIR_STYLES], ["beard", Appearance.BEARDS]]
	for c in cats:
		for o in c[1]:
			var app := base.duplicate(true)
			app[c[0]] = o.id
			built += _build_ok(app, 30.0, "male", "%s %s" % [c[0], o.key])
	for grp in [["top", Appearance.TOPS], ["bottom", Appearance.BOTTOMS], ["shoes", Appearance.SHOES]]:
		for o in grp[1]:
			var age: float = 30.0 if not o.has("max_age") else 1.5
			var app := Appearance.defaults("female", age)
			app[grp[0]].style = o.id
			built += _build_ok(app, age, "female", "%s %s" % [grp[0], o.key])
	for o in Appearance.ACCESSORIES:
		var app := base.duplicate(true)
		app.accessories = [o.id]
		built += _build_ok(app, 30.0, "female", "Accessoire %s" % o.id)
	var rng := RandomNumberGenerator.new()
	rng.seed = 5
	for age in [0.5, 1.0, 2.5, 5.0, 9.0, 13.0, 16.0, 22.0, 40.0, 60.0, 80.0, 100.0]:
		for b in ["female", "male"]:
			for level in 3:
				built += _build_ok(Appearance.random(rng, b, age), age, b, "Zufall %s %s LOD %d" % [b, str(age), level], level)
	runner.check(built > 120, "Viele Kombinationen gebaut (%d)" % built)


func _build_ok(app: Dictionary, age: float, b: String, label: String, level := 0) -> int:
	var r := HumanoidBuilder.build(app, age, b, level)
	var mesh: ArrayMesh = r.mesh
	var ok := mesh != null and mesh.get_surface_count() > 0
	if ok:
		var arr := mesh.surface_get_arrays(0)
		var v: PackedVector3Array = arr[Mesh.ARRAY_VERTEX]
		var idx: PackedInt32Array = arr[Mesh.ARRAY_INDEX]
		ok = v.size() > 50 and idx.size() % 3 == 0
		for q in v:
			if not q.is_finite():
				ok = false
				break
	runner.check(ok, "Bau fehlgeschlagen: %s" % label)
	return 1 if ok else 0


func test_actions_and_poses() -> void:
	var h := Humanoid.new()
	add_child(h)
	h.setup(Appearance.defaults("male", 35.0), 35.0, "male")
	var sk := h.get_skeleton()
	var stand_y := sk.get_bone_global_pose(HumanoidBuilder.B_HIPS).origin.y
	for a in Humanoid.ACTIONS:
		h.set_action(a)
		h.set_motion(1.4 if a == "walk" else (4.5 if a == "run" else 0.0))
		for i in 30:
			h._process(1.0 / 30.0)
		var head := sk.get_bone_global_pose(HumanoidBuilder.B_HEAD).origin
		runner.check(head.is_finite(), "Pose %s endlich" % a)
		if a == "sit":
			var sy := sk.get_bone_global_pose(HumanoidBuilder.B_HIPS).origin.y
			runner.check(sy < stand_y - 0.25 and absf(sy - h.seat_height()) < 0.15, "Sitzen senkt das Becken auf Sitzhoehe")
		if a == "sleep":
			runner.check(head.y < 0.5 and head.z < -0.5, "Schlafen: liegt, Kopf Richtung -Z")
		if a == "wave":
			var hand := sk.get_bone_global_pose(HumanoidBuilder.B_HAND_R).origin
			runner.check(hand.y > head.y, "Winken: Hand ueber dem Kopf")
	# Gehen: Fuesse wechseln sich ab, Schritte werden gemeldet
	var steps := [0]
	h.footstep.connect(func(_s, _i): steps[0] += 1)
	h.set_action("walk")
	h.set_motion(1.4)
	for i in 120:
		h._process(1.0 / 60.0)
	runner.check(steps[0] >= 2, "Schrittereignisse beim Gehen (%d)" % steps[0])
	h.queue_free()
	# Senioren gehen langsamer, Kleinkinder auch
	runner.check(Humanoid.age_speed_factor(80.0) < 0.85 and Humanoid.age_speed_factor(3.0) < 0.7, "Tempo nach Alter")
	runner.check(Humanoid.age_speed_factor(25.0) == 1.0, "Erwachsene volles Tempo")


# ================================================================== Spieler

func _box(parent: Node, size: Vector3, pos: Vector3) -> StaticBody3D:
	var b := StaticBody3D.new()
	var cs := CollisionShape3D.new()
	var shape := BoxShape3D.new()
	shape.size = size
	cs.shape = shape
	b.add_child(cs)
	b.position = pos
	parent.add_child(b)
	return b


func _frames(n: int) -> void:
	for i in n:
		await get_tree().physics_frame


func test_player_movement() -> void:
	var level := Node3D.new()
	add_child(level)
	_box(level, Vector3(20, 1, 30), Vector3(0, -0.5, 0))                 # Boden, Oberkante y = 0
	_box(level, Vector3(20, 0.15, 20), Vector3(0, 0.075, 12.0))          # Bordstein 0,15 m ab z = 2
	_box(level, Vector3(20, 3, 1), Vector3(0, 1.5, 7.0))                 # Wand bei z = 6.5
	var player: PlayerController = load("res://scenes/player/player.tscn").instantiate()
	level.add_child(player)
	runner.check(player.is_in_group("player"), "Spieler in Gruppe player")
	runner.check(player.get_body() is Humanoid, "get_body liefert Humanoid")
	player.teleport(Vector3(0, 0.5, 0), 0.0)
	await _frames(40)
	runner.check(player.is_on_floor(), "Schwerkraft: Spieler steht am Boden")
	runner.check_near(player.global_position.y, 0.0, 0.05, "Bodenhoehe")
	Input.action_press("move_forward")
	await _frames(280)
	Input.action_release("move_forward")
	await _frames(10)
	var pz := player.global_position.z
	runner.check(player.global_position.y > 0.12, "Bordstein (0,15 m) erklommen, y = %.3f" % player.global_position.y)
	runner.check(pz > 4.0, "Spieler hat sich bewegt, z = %.2f" % pz)
	runner.check(pz < 6.5, "Wand blockiert, z = %.2f" % pz)
	# Seitwaerts relativ zur Kamera
	var x0 := player.global_position.x
	Input.action_press("move_right")
	await _frames(60)
	Input.action_release("move_right")
	runner.check(player.global_position.x < x0 - 0.5, "Rechts relativ zur Kamera (Blick +Z -> -X)")
	# Steuerung aus
	player.set_controls_enabled(false)
	var p0 := player.global_position
	Input.action_press("move_back")
	await _frames(30)
	Input.action_release("move_back")
	runner.check(player.global_position.distance_to(p0) < 0.1, "Deaktivierte Steuerung bewegt nicht")
	player.set_controls_enabled(true)
	# Interaktion
	var got := {"avail": "", "used": false}
	var bus := get_node("/root/EventBus")
	var on_avail := func(t: String): got.avail = t
	bus.interaction_available.connect(on_avail)
	var area := Area3D.new()
	var scr := GDScript.new()
	scr.source_code = "extends Area3D\nvar used := false\nfunc get_prompt(_p) -> String:\n\treturn \"Testobjekt benutzen\"\nfunc interact(_p) -> void:\n\tused = true\n"
	scr.reload()
	area.set_script(scr)
	area.add_to_group("interactable")
	level.add_child(area)
	player.teleport(Vector3(0, 0.2, 3.5), 0.0)
	area.global_position = player.global_position + Vector3(0, 1.0, 1.2)
	await _frames(20)
	runner.check_eq(got.avail, "Testobjekt benutzen", "Interaktionshinweis")
	runner.check(player.get_interaction_target() == area, "Ziel erkannt")
	runner.check(player.interact_nearest(), "Interaktion ausgefuehrt")
	runner.check(area.get("used") == true, "interact() aufgerufen")
	bus.interaction_available.disconnect(on_avail)
	area.queue_free()
	# Fahrzeug
	var car := Node3D.new()
	var vs := GDScript.new()
	vs.source_code = "extends Node3D\nvar vehicle_id := 1\nvar occupant = null\nvar target: Node3D\nfunc _ready():\n\ttarget = Node3D.new()\n\tadd_child(target)\nfunc can_enter(_p) -> bool:\n\treturn occupant == null\nfunc enter(p) -> void:\n\toccupant = p\nfunc exit() -> Vector3:\n\toccupant = null\n\treturn global_position + Vector3(2, 0.2, 0)\nfunc get_camera_target() -> Node3D:\n\treturn target\nfunc is_occupied() -> bool:\n\treturn occupant != null\n"
	vs.reload()
	car.set_script(vs)
	car.add_to_group("vehicle")
	level.add_child(car)
	car.global_position = player.global_position + Vector3(1.5, 0, 0)
	runner.check(player.enter_vehicle(car), "Einsteigen")
	runner.check(not player.get_body().visible, "Koerper waehrend der Fahrt versteckt")
	car.global_position += Vector3(5, 0, 0)
	await _frames(3)
	runner.check(player.global_position.distance_to(car.global_position) < 0.1, "Spieler folgt dem Fahrzeug")
	player.exit_vehicle()
	runner.check(not player.is_in_vehicle() and player.get_body().visible, "Aussteigen")
	runner.check(player.global_position.distance_to(car.global_position + Vector3(2, 0.2, 0)) < 0.05, "Ausstiegspunkt")
	level.queue_free()
	await _frames(2)
