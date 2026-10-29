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
