class_name StoryRunner
extends Node3D
## Fuehrt die laufende Story-Mission in der 3D-Welt aus: setzt Ziele, zeigt
## Marker und Routen, spielt Dialoge und inszenierte Abschnitte.
##
## Der inszenierte Abschnitt „ambush" ist der Hinterhalt an der Hafenstrasse:
## Waehrend der Spieler faehrt, naehert sich ein Pick-up, legt einen
## Raketenwerfer an – und der Spieler muss rechtzeitig aus dem Wagen springen.

signal beat_spoken(speaker: String, line: String, radio: bool)
signal scripted_started(kind: String)
signal scripted_finished(kind: String, survived: bool)

const MARKER_SCENE_RADIUS := 2.6
const CHASE_DISTANCE := 26.0
const JUMP_GRACE := 0.35     # Nachsicht: kurz nach Ablauf zaehlt der Sprung noch

var world: WorldState
var player: Node3D
var story                      # StorySystem
var marker: Area3D
var target_pos := Vector2.INF
var target_label := ""

# Inszenierter Abschnitt
var scripted := ""
var scripted_t := 0.0
var beats_done := {}
var chase: Node3D
var launcher: Node3D
var aiming := false
var aim_started := -1.0
var jumped := false
var exploded := false
var jump_window := false
var _hint_shown := false
var _last_sync := 0.0
var _hideout := -1


func setup(p_world: WorldState, p_player: Node3D = null) -> void:
	world = p_world
	player = p_player if p_player != null else get_tree().get_first_node_in_group("player")
	story = world.get("story")
	add_to_group("story_runner")
	set_process(true)
	_sync_objective()


func _process(delta: float) -> void:
	if world == null or story == null:
		return
	if scripted != "":
		_tick_scripted(delta)
		return
	_last_sync += delta
	if _last_sync > 0.5:
		_last_sync = 0.0
		_sync_objective()
	if target_pos != Vector2.INF and _player_pos2().distance_to(target_pos) < 6.0:
		var st: Dictionary = story.current_step()
		if str(st.get("type", "")) in ["goto", "drive", "flee"]:
			_auto_complete_step(st)


func _player_pos2() -> Vector2:
	if player == null or not is_instance_valid(player):
		player = get_tree().get_first_node_in_group("player")
	if player == null:
		return Vector2.ZERO
	var p: Vector3 = player.global_position
	return Vector2(p.x, p.z)


# ============================================================ Ziele

## Das Gebaeude, das in dieser Stadt als Unterschlupf dient. Wird einmal
## bestimmt und bleibt gleich, damit HUD und Auftraege dasselbe meinen.
func hideout_building() -> int:
	if _hideout < 0:
		_hideout = building_for_place("hideout")
	return _hideout


## Gebaeude des aktuellen Ziels (-1, wenn keines gesetzt ist).
func target_building() -> int:
	return int(story.step_data.get("building", -1)) if story != null else -1

## Ordnet einem Ortsschluessel ein echtes Gebaeude der erzeugten Stadt zu.
func building_for_place(place: String, exclude: Array = []) -> int:
	var c := world.city
	var kinds := {
		"hideout": ["abandoned", "warehouse", "tenement"],
		"bar": ["mixed", "shop"],
		"warehouse": ["warehouse", "factory"],
		"harbor": ["warehouse", "factory"],
		"market": ["market_hall", "market_square"],
		"shop": ["shop", "mixed", "supermarket"],
		"bank": ["office"],
		"police": ["police"],
		"hospital": ["hospital", "clinic"],
		"garage": ["warehouse", "shop"],
		"city_hall": ["city_hall"],
		"club": ["mixed", "shop"],
		"apartment": ["apartment", "tenement"],
	}
	var want_biz := {"bar": "bar", "shop": "", "club": "cinema", "garage": "car_dealer", "market": "market"}
	var from := _player_pos2()
	var best := -1
	var best_score := -INF
	var rng := RandomNumberGenerator.new()
	rng.seed = hash("place:%s:%d" % [place, world.seed])
	for b in c.buildings:
		if exclude.has(int(b.id)):
			continue
		var ok := false
		for k in kinds.get(place, []):
			if str(b.kind) == k:
				ok = true
				break
		if not ok:
			continue
		var bt := str(b.business_type)
		var wanted := str(want_biz.get(place, ""))
		var score := 0.0
		if wanted != "" and bt == wanted:
			score += 400.0
		elif wanted != "" and bt != "":
			score += 60.0
		# Hafen und Lager: am Fluss bzw. im Industriegebiet
		if place == "harbor":
			score += 300.0 - absf(float(b.footprint.get_center().y) - float(c.river.z)) * 0.6
		if place == "hideout" and str(b.zone) in ["slum", "poor", "industrial"]:
			score += 250.0
		if place == "warehouse" and str(b.zone) == "industrial":
			score += 150.0
		# In erreichbarer Naehe, aber nicht direkt vor der Nase
		var d: float = b.sidewalk_point.distance_to(from)
		score += clampf(600.0 - absf(d - 320.0), -400.0, 600.0) * 0.4
		score += rng.randf() * 40.0
		if score > best_score:
			best_score = score
			best = int(b.id)
	return best


func _resolve_target(st: Dictionary) -> void:
	target_pos = Vector2.INF
	target_label = ""
	var place := str(st.get("place", ""))
	if place == "":
		return
	var exclude: Array = []
	if bool(st.get("exclude_last", false)):
		exclude = story.step_data.get("used_buildings", [])
	var bid := -1
	if story.step_data.has("building"):
		bid = int(story.step_data.building)
	elif place == "hideout":
		bid = hideout_building()
		story.step_data["building"] = bid
	else:
		bid = building_for_place(place, exclude)
		if bid >= 0:
			story.step_data["building"] = bid
			var used: Array = story.step_data.get("used_buildings", [])
			used.append(bid)
			story.step_data["used_buildings"] = used
	if bid < 0:
		return
	var b: Dictionary = world.city.buildings[bid]
	target_pos = b.sidewalk_point
	target_label = str(b.name) if str(b.name) != "" else str(b.label)


func _sync_objective() -> void:
	if not story.is_active():
		_clear_marker()
		target_pos = Vector2.INF
		return
	var st: Dictionary = story.current_step()
	if st.is_empty():
		return
	if target_pos == Vector2.INF:
		_resolve_target(st)
		if target_pos != Vector2.INF:
			world.emit_event("request_route", [Vector3(target_pos.x, 0.2, target_pos.y), target_label])
	_place_marker()


func _place_marker() -> void:
	if target_pos == Vector2.INF:
		_clear_marker()
		return
	if marker == null or not is_instance_valid(marker):
		marker = _make_marker()
		add_child(marker)
	marker.global_position = Vector3(target_pos.x, 1.0, target_pos.y)


func _clear_marker() -> void:
	if marker != null and is_instance_valid(marker):
		marker.queue_free()
	marker = null


func _make_marker() -> Area3D:
	var a := Area3D.new()
	a.name = "StoryObjective"
	a.add_to_group("interactable")
	a.set_script(preload("res://scripts/story/story_objective.gd"))
	var cs := CollisionShape3D.new()
	var sp := SphereShape3D.new()
	sp.radius = MARKER_SCENE_RADIUS
	cs.shape = sp
	a.add_child(cs)
	a.set("runner", self)
	# Sichtbarer Lichtkegel
	var mi := MeshInstance3D.new()
	var cyl := CylinderMesh.new()
	cyl.top_radius = 0.9
	cyl.bottom_radius = 0.9
	cyl.height = 14.0
	mi.mesh = cyl
	var mat := StandardMaterial3D.new()
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.albedo_color = Color(1.0, 0.78, 0.25, 0.14)
	mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	mat.cull_mode = BaseMaterial3D.CULL_DISABLED
	mi.material_override = mat
	mi.position.y = 6.0
	a.add_child(mi)
	return a


## Text fuer den Hinweis am Ziel.
func objective_prompt() -> String:
	var st: Dictionary = story.current_step()
	if st.is_empty():
		return ""
	match str(st.get("type", "")):
		"collect": return "Abholen: %s" % str(st.get("text", ""))
		"deliver": return "Abgeben: %s" % str(st.get("text", ""))
		"talk": return "Sprechen: %s" % str(st.get("text", ""))
		"choice": return "Entscheiden"
		"interact": return str(st.get("text", "Handeln"))
	return str(st.get("text", ""))


## Wird vom Marker aufgerufen, wenn der Spieler dort interagiert.
func interact_objective() -> Dictionary:
	if not story.is_active():
		return {"ok": false, "reason": "Kein Auftrag aktiv."}
	var st: Dictionary = story.current_step()
	var typ := str(st.get("type", ""))
	if target_pos != Vector2.INF and _player_pos2().distance_to(target_pos) > 9.0:
		return {"ok": false, "reason": "Du bist nicht am Ziel."}
	if typ == "choice":
		world.emit_event("open_panel", ["story_choice", {"step": st}])
		return {"ok": true, "panel": true}
	_play_dialog(st)
	match typ:
		"collect":
			if st.has("item") and world.player:
				var inv: Dictionary = world.player.inventory
				inv[str(st.item)] = int(inv.get(str(st.item), 0)) + 1
			if st.has("amount"):
				_collect_money(float(st.amount))
		"deliver":
			_deliver(st)
		"interact":
			if st.has("crime") and world.crime:
				world.crime.attempt(world, str(st.crime), {"pos": _player_pos2()})
	return _auto_complete_step(st)


func _collect_money(amount: float) -> void:
	# Geld kommt vom Geschaeft am Ort, nicht aus dem Nichts
	var bid := int(story.step_data.get("building", -1))
	var led := world.ledger
	var pacc: int = led.player_account
	if bid >= 0 and world.economy:
		var biz: int = world.economy.business_for_building(bid)
		if biz >= 0:
			var b: Dictionary = world.economy.get_business(biz)
			var acc := int(b.get("account", -1))
			if acc >= 0 and led.has_account(acc):
				var got := led.transfer_partial(acc, pacc, amount, "Schutzgeld")
				world.notify("Kassiert: %s" % Economy.fmt_money(got), "money")
				return
	led.mint(pacc, amount, "Schutzgeld")
	world.notify("Kassiert: %s" % Economy.fmt_money(amount), "money")


func _deliver(st: Dictionary) -> void:
	if world.player == null:
		return
	var inv: Dictionary = world.player.inventory
	for key in ["paket", "geldtasche", "beweise"]:
		if int(inv.get(key, 0)) > 0:
			inv[key] = int(inv[key]) - 1
			if int(inv[key]) <= 0:
				inv.erase(key)


func _auto_complete_step(st: Dictionary) -> Dictionary:
	# Inszenierter Abschnitt startet statt des normalen Abschlusses
	if st.has("scripted") and scripted == "":
		_start_scripted(str(st.scripted))
		return {"ok": true, "scripted": true}
	_play_dialog(st)
	var r: Dictionary = story.advance_step()
	target_pos = Vector2.INF
	_clear_marker()
	_sync_objective()
	return r


func _play_dialog(st: Dictionary) -> void:
	for d in st.get("dialog", []):
		_speak(str(d[0]), str(d[1]), false)


## Spricht eine Zeile: Untertitel, optionale eigene Sprachaufnahme, Funkton.
func _speak(speaker: String, line: String, radio := false) -> void:
	beat_spoken.emit(speaker, line, radio)
	world.emit_event("story_line", [speaker, line, radio])
	var sp := StoryCatalog.speaker(speaker)
	var audio := get_node_or_null("/root/Audio")
	if audio != null:
		# Eigene Aufnahme, falls vorhanden; sonst nur ein dezenter Funkton
		var voice := "voice_%s" % str(sp.voice)
		if audio.has_method("has_sound") and audio.has_sound(voice):
			audio.play_sfx(voice)
		elif radio and audio.has_method("play_ui"):
			audio.play_ui("notification")


# ============================================================ Hinterhalt

func _start_scripted(kind: String) -> void:
	scripted = kind
	scripted_t = 0.0
	beats_done.clear()
	aiming = false
	aim_started = -1.0
	jumped = false
	exploded = false
	jump_window = false
	_hint_shown = false
	scripted_started.emit(kind)
	world.emit_event("story_scripted_started", [kind])


func _def_scripted() -> Dictionary:
	var d: Dictionary = story.mission_def()
	return d.get("scripted_%s" % scripted, {})


func _tick_scripted(delta: float) -> void:
	scripted_t += delta
	var def: Dictionary = _def_scripted()
	if def.is_empty():
		scripted = ""
		return
	var warn: float = float(def.get("warn_after", 6.0))
	# Dialogzeilen
	for i in (def.get("beats", []) as Array).size():
		var b: Dictionary = def.beats[i]
		if beats_done.has(i):
			continue
		if scripted_t >= warn + float(b.get("t", 0.0)):
			beats_done[i] = true
			_speak(str(b.speaker), str(b.line), bool(b.get("radio", false)))
	# Verfolger erscheint
	if chase == null and scripted_t >= warn:
		_spawn_chase()
	_update_chase(delta)
	# Zielen und Absprungfenster
	var last_beat := warn
	for b in def.get("beats", []):
		last_beat = maxf(last_beat, warn + float(b.get("t", 0.0)))
	if not aiming and scripted_t >= last_beat - 1.0:
		aiming = true
		aim_started = scripted_t
		jump_window = true
		if launcher != null:
			launcher.visible = true
		world.emit_event("story_prompt", [str(def.get("prompt", "SPRINGEN")), str(def.get("prompt_hint", ""))])
	if jump_window and not jumped:
		if Input.is_action_just_pressed(str(def.get("jump_action", "jump"))):
			_do_jump()
			return
		if scripted_t > aim_started + float(def.get("aim_time", 4.5)) + JUMP_GRACE:
			_fire_rocket(false)


func _spawn_chase() -> void:
	var c := Node3D.new()
	c.name = "Verfolger"
	add_child(c)
	chase = c
	# Fahrzeug: vorhandene Fahrzeugmodelle nutzen, sonst einfacher Ersatz
	var built := false
	if ResourceLoader.exists("res://scripts/traffic/vehicle_mesh_builder.gd"):
		var vb = load("res://scripts/traffic/vehicle_mesh_builder.gd")
		if vb != null and vb.has_method("build"):
			var v = vb.build("suv", Color(0.08, 0.08, 0.1))
			if v is Node3D:
				c.add_child(v)
				built = true
	if not built:
		var body := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(2.0, 1.3, 5.0)
		body.mesh = bm
		var mat := StandardMaterial3D.new()
		mat.albedo_color = Color(0.08, 0.08, 0.1)
		body.material_override = mat
		body.position.y = 0.8
		c.add_child(body)
	# Schuetze auf der Ladeflaeche mit Werfer
	launcher = RpgLauncher.build_launcher()
	launcher.position = Vector3(0.5, 1.75, -1.4)
	launcher.rotation.y = PI
	launcher.visible = false
	c.add_child(launcher)
	c.global_position = _behind_player(CHASE_DISTANCE + 12.0)
	world.emit_event("notify", ["Im Rückspiegel: ein dunkler Pick-up.", "warning"])


func _behind_player(dist: float) -> Vector3:
	if player == null:
		return Vector3.ZERO
	var back: Vector3 = player.global_transform.basis.z * dist
	return player.global_position + back


func _update_chase(delta: float) -> void:
	if chase == null or player == null or not is_instance_valid(chase):
		return
	# Faehrt auf Abstand hinter dem Spieler, holt beim Zielen auf
	var want := CHASE_DISTANCE if not aiming else CHASE_DISTANCE * 0.55
	var goal := _behind_player(want)
	chase.global_position = chase.global_position.lerp(goal, clampf(delta * 1.6, 0.0, 1.0))
	chase.look_at(player.global_position, Vector3.UP)
	if launcher != null and aiming:
		launcher.look_at(player.global_position + Vector3(0, 0.6, 0), Vector3.UP)


func _do_jump() -> void:
	jumped = true
	jump_window = false
	world.emit_event("story_prompt_cleared", [])
	# Spieler aus dem Fahrzeug werfen
	var veh: Node = null
	if player.has_method("is_in_vehicle") and player.is_in_vehicle():
		veh = player.get("current_vehicle") if "current_vehicle" in player else null
		if player.has_method("exit_vehicle"):
			player.exit_vehicle()
	var side: Vector3 = player.global_transform.basis.x * 4.0 + Vector3(0, 1.2, 0)
	if player is CharacterBody3D:
		player.velocity = side * 2.2
	world.emit_event("notify", ["Du springst aus dem Wagen.", "warning"])
	var audio := get_node_or_null("/root/Audio")
	if audio != null and audio.has_method("play_sfx"):
		audio.play_sfx("brake")
	# Kurz danach schlaegt die Rakete in den rollenden Wagen ein
	await get_tree().create_timer(0.9).timeout
	_fire_rocket(true)


func _fire_rocket(player_jumped: bool) -> void:
	if exploded:
		return
	exploded = true
	jump_window = false
	world.emit_event("story_prompt_cleared", [])
	var target: Vector3 = player.global_position
	if player_jumped:
		# Der Wagen rollt weiter: Einschlag vor dem Spieler
		target = player.global_position - player.global_transform.basis.z * 14.0
	var from: Vector3 = launcher.global_position if launcher != null else _behind_player(20.0) + Vector3(0, 1.6, 0)
	var audio := get_node_or_null("/root/Audio")
	if audio != null and audio.has_method("play_sfx"):
		audio.play_sfx("horn", from)
	# Rakete fliegen lassen
	var rocket := RpgLauncher.build_rocket()
	add_child(rocket)
	rocket.global_position = from
	rocket.look_at(target, Vector3.UP)
	var dist := from.distance_to(target)
	var fly := clampf(dist / 85.0, 0.25, 1.4)
	var tw := create_tween()
	tw.tween_property(rocket, "global_position", target, fly).set_trans(Tween.TRANS_LINEAR)
	tw.tween_callback(func() -> void:
		if is_instance_valid(rocket):
			rocket.queue_free()
		RpgLauncher.spawn_explosion(self, target, 1.3)
		_slow_motion()
		if audio != null and audio.has_method("play_sfx"):
			audio.play_sfx("error", target, 6.0)
		_after_impact(player_jumped))


## Kurze Zeitlupe im Moment des Einschlags, danach zurueck auf Normaltempo.
func _slow_motion() -> void:
	Engine.time_scale = 0.35
	await get_tree().create_timer(0.55 * 0.35, true, false, true).timeout
	var tw := create_tween()
	tw.tween_method(func(v: float): Engine.time_scale = v, 0.35, 1.0, 0.6)


func _after_impact(player_jumped: bool) -> void:
	var def: Dictionary = _def_scripted()
	Engine.time_scale = 1.0
	scripted_finished.emit("ambush", player_jumped)
	world.emit_event("story_scripted_finished", ["ambush", player_jumped])
	if chase != null and is_instance_valid(chase):
		var away := chase.create_tween()
		away.tween_property(chase, "global_position", chase.global_position - chase.global_transform.basis.z * 160.0, 6.0)
		away.tween_callback(chase.queue_free)
		chase = null
		launcher = null
	if player_jumped:
		# Ueberlebt: verletzt, aber im Spiel. Weiter mit dem Fluchtschritt.
		if world.player and "needs" in world.player:
			world.player.needs.health = maxf(12.0, float(world.player.needs.health) - 28.0)
		world.notify("Du hast es gerade noch rausgeschafft. Der Wagen ist Schrott.", "warning")
		if world.crime:
			world.crime.set_player_context(_player_pos2(), false, -1)
		scripted = ""
		var st: Dictionary = story.current_step()
		story.advance_step()
		target_pos = Vector2.INF
		_clear_marker()
		_sync_objective()
	else:
		# Nicht gesprungen: schwer verletzt im Krankenhaus, Mission gescheitert
		scripted = ""
		if world.player and "needs" in world.player:
			world.player.needs.health = 8.0
		world.notify("Du bist nicht rechtzeitig rausgekommen.", "danger")
		story.fail(str(story.mission_def().get("fail_text", "")))
		_to_hospital()
		target_pos = Vector2.INF
		_clear_marker()


func _to_hospital() -> void:
	var best := -1
	var bd := INF
	var from := _player_pos2()
	for b in world.city.buildings:
		if str(b.kind) in ["hospital", "clinic"]:
			var d: float = b.sidewalk_point.distance_to(from)
			if d < bd:
				bd = d
				best = int(b.id)
	if best >= 0 and player != null and player.has_method("teleport"):
		var p: Vector2 = world.city.buildings[best].sidewalk_point
		player.teleport(Vector3(p.x, 0.3, p.y), 0.0)
		world.notify("Jemand hat dich ins Krankenhaus gebracht.", "info")
	var game := get_node_or_null("/root/Game")
	if game != null and game.has_method("skip_time"):
		game.skip_time(480.0)


## Fuer Tests: den inszenierten Abschnitt ohne Eingabe durchspielen.
func debug_force_jump() -> void:
	if scripted != "" and jump_window:
		_do_jump()
