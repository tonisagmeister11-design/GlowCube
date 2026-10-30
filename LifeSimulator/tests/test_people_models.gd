extends RefCounted
## Prueft zugelieferte Personenmodelle: Aufbereitung, Knochenzuordnung,
## Bewegungsablauf und das Leistungsbudget.

var runner


func test_models_available_and_sane() -> void:
	var ids: Array = PersonModels.available()
	runner.check(ids.size() >= 1, "Mindestens ein Personenmodell vorhanden: %s" % str(ids))
	for id in ids:
		var d: Dictionary = PersonModels.info(str(id))
		runner.check(str(d.get("name", "")) != "", "Modell '%s' hat einen Namen" % id)
		runner.check(str(d.get("license", "")) != "", "Lizenzangabe vorhanden (%s)" % id)
		runner.check(["male", "female"].has(str(d.get("sex", ""))), "Geschlecht gesetzt (%s)" % id)
		var h := float(d.get("height", 0.0))
		runner.check(h > 1.4 and h < 2.1, "Koerpergroesse plausibel: %.2f m" % h)


func test_build_and_height() -> void:
	for id in PersonModels.available():
		var p: Node3D = PersonModels.build(str(id), false)
		runner.check(p != null, "Modell '%s' baut" % id)
		if p == null:
			continue
		runner.check(p.get_node_or_null("Model") != null, "Modellknoten vorhanden")
		var h: float = p.height()
		runner.check(absf(h - float(PersonModels.info(str(id)).height)) < 0.05,
			"Gebaute Hoehe passt zur Angabe: %.2f m" % h)
		runner.check(p.has_method("set_motion") and p.has_method("set_action"),
			"Figuren-API vorhanden")
		p.free()


func test_animated_model_has_bones() -> void:
	# Ein Modell mit Skelett muss alle Knochenrollen gefunden haben, sonst
	# kann der Bewegungsablauf Beine oder Arme nicht bewegen.
	var found_animated := false
	for id in PersonModels.available():
		if not bool(PersonModels.info(str(id)).get("animated", false)):
			continue
		found_animated = true
		var p: DetailedPerson = PersonModels.build(str(id), false)
		runner.check(p != null, "Modell '%s' baut" % id)
		if p == null:
			continue
		runner.check(p.animator != null, "Bewegungsablauf eingerichtet (%s)" % id)
		if p.animator != null:
			for role in ["hips", "thigh_l", "thigh_r", "calf_l", "calf_r",
					"upperarm_l", "upperarm_r", "head"]:
				runner.check(p.animator._idx.has(role), "Knochen '%s' zugeordnet" % role)
		p.free()
	runner.check(found_animated, "Mindestens ein Modell ist beweglich")


func test_walk_moves_legs() -> void:
	# Der Gehtakt muss die Oberschenkel gegenlaeufig drehen.
	for id in PersonModels.for_role("passant"):
		var p: DetailedPerson = PersonModels.build(str(id), false)
		if p == null or p.animator == null:
			continue
		var sk: Skeleton3D = p.animator.skeleton
		var li: int = int(p.animator._idx["thigh_l"])
		var ri: int = int(p.animator._idx["thigh_r"])
		p.animator.speed = 1.45
		p.animator._blend = 1.0
		p.animator._phase = PI * 0.5
		p.animator.step(0.0)
		var l1: Quaternion = sk.get_bone_pose_rotation(li)
		var r1: Quaternion = sk.get_bone_pose_rotation(ri)
		var rest_l: Quaternion = sk.get_bone_rest(li).basis.get_rotation_quaternion()
		runner.check(l1.angle_to(rest_l) > 0.05, "Linker Oberschenkel bewegt sich")
		runner.check(l1.angle_to(r1) > 0.1, "Beine laufen gegenlaeufig")
		# Im Stand kehrt die Figur zur Grundhaltung zurueck
		p.animator.speed = 0.0
		p.animator._blend = 0.0
		p.animator.step(0.0)
		var l2: Quaternion = sk.get_bone_pose_rotation(li)
		runner.check(l2.angle_to(rest_l) < 0.05, "Im Stand stehen die Beine still")
		p.free()


func test_budget_limits_detailed_people() -> void:
	# Das Budget haengt an der Leistungsstufe: in den unteren Stufen gar keine
	# aufwendigen Figuren.
	var perf: Node = Engine.get_main_loop().root.get_node_or_null("Perf")
	runner.check(perf != null, "Leistungsmanager vorhanden")
	if perf == null:
		return
	var before: int = int(perf.level)
	var live_before: int = PersonModels.live
	perf.level = 0
	runner.check_eq(PersonModels.budget_left(), 0, "Notfallstufe erlaubt keine aufwendigen Figuren")
	perf.level = 3
	runner.check(PersonModels.budget_left() > 0, "Hohe Stufe erlaubt welche")
	# Gebaute Figuren zaehlen gegen das Budget und geben es wieder frei
	var cap: int = PersonModels.budget_left()
	var p: Node3D = PersonModels.build(str(PersonModels.available()[0]), true)
	if p != null:
		runner.check_eq(PersonModels.budget_left(), cap - 1, "Gebaute Figur zaehlt")
		Engine.get_main_loop().root.add_child(p)
		p.free()
		runner.check_eq(PersonModels.live, live_before, "Budget wird beim Entfernen wieder frei")
	perf.level = before


func test_variants_differ() -> void:
	# Aus einem Modell sollen mehrere Leute werden: Hautton, Kleidungsfarben,
	# Statur und Groesse muessen sich unterscheiden.
	for id in PersonModels.available():
		var list: Array = PersonModels.variants(str(id))
		if list.is_empty():
			continue
		runner.check(list.size() >= 5, "Mindestens fuenf Spielarten (%s): %d" % [id, list.size()])
		var skins := {}
		var heights := {}
		for i in list.size():
			var p: DetailedPerson = PersonModels.build(str(id), false, i)
			runner.check(p != null, "Spielart %d baut" % i)
			if p == null:
				continue
			runner.check(str(p.person_name) != "", "Spielart %d hat einen Namen" % i)
			skins[str(list[i].skin)] = true
			heights["%.3f" % p.height()] = true
			p.free()
		runner.check(skins.size() >= 4, "Verschiedene Hauttoene: %d" % skins.size())
		runner.check(heights.size() >= 4, "Verschiedene Koerpergroessen: %d" % heights.size())


func test_clothing_stays_visible_at_distance() -> void:
	# Eine fruehere Fassung hat auf Entfernung alles ausser dem Koerper
	# ausgeblendet - die Figur stand dann ohne Kleidung da.
	for id in PersonModels.for_role("passant"):
		var p: DetailedPerson = PersonModels.build(str(id), false)
		if p == null:
			continue
		for level in [0, 1, 2]:
			p.set_lod(level)
			for m in p._meshes:
				runner.check(m.visible, "Stufe %d: '%s' bleibt sichtbar" % [level, m.name])
		p.free()


func test_footstep_signal_on_walk() -> void:
	# Ohne dieses Signal verstummen die Schrittgeraeusche der Spielerfigur.
	for id in PersonModels.for_role("skin"):
		var p: DetailedPerson = PersonModels.build(str(id), false)
		if p == null or p.animator == null:
			continue
		var steps: Array = []
		p.footstep.connect(func(side: int, _strength: float): steps.append(side))
		p.set_motion(1.45)
		# Eine ganze Schrittfolge durchlaufen
		for i in 120:
			p.animator.step(0.02)
		runner.check(steps.size() >= 2, "Schritte gemeldet: %d" % steps.size())
		runner.check(steps.has(0) and steps.has(1), "Beide Fuesse melden sich")
		p.free()


func test_skin_models_can_walk() -> void:
	# Ein Modell ohne Skelett taugt nicht als Spielerfigur - der Spieler stuende
	# bewegungslos da.
	for id in PersonModels.for_role("skin"):
		runner.check(bool(PersonModels.info(str(id)).get("animated", false)),
			"Spieler-Aussehen '%s' ist beweglich" % id)


func test_player_body_accepts_a_skin() -> void:
	# Die Spielerfigur haelt ihre sichtbare Figur in `body`. War die Variable
	# auf Humanoid festgelegt, liess sich ein fertiges Modell gar nicht
	# zuweisen - der Spieler stand dann ohne Koerper da. Das faellt nur im
	# laufenden Spiel auf, deshalb hier der Vertrag.
	var script: GDScript = load("res://scripts/player/player_controller.gd")
	runner.check(script != null, "Spielerskript ladbar")
	if script == null:
		return
	var found := false
	for prop in script.get_script_property_list():
		if str(prop.name) == "body":
			found = true
			# 0 = Variant, sonst der festgelegte Typ. Humanoid waere zu eng.
			var hint := str(prop.get("class_name", ""))
			runner.check(hint == "" or hint == "Node3D",
				"`body` ist nicht auf eine Figurenklasse festgelegt (ist: '%s')" % hint)
	runner.check(found, "Spielerfigur hat ein Feld `body`")
	# Und die beiden Figurenarten muessen dieselben Methoden anbieten.
	var p: DetailedPerson = null
	for id in PersonModels.for_role("skin"):
		p = PersonModels.build(str(id), false)
		break
	if p == null:
		return
	for m in ["height", "eye_height", "set_motion", "set_action", "get_action", "set_shadow_only"]:
		runner.check(p.has_method(m), "Fertige Figur kann '%s'" % m)
	runner.check(p.has_signal("footstep"), "Fertige Figur meldet Schritte")
	p.free()
