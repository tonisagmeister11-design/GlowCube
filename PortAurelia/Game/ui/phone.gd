class_name Phone
extends Node
## Mobile phone (arrow up / phone button): contacts (Marco, taxi, mechanic),
## side jobs, messages, statistics. Ring tone + message notifications.

var world: GameWorld
var messages: Array = []       # [{from, text, time}]


func _ready() -> void:
	name = "Phone"
	world = GameWorld.instance
	Events.mission_completed.connect(_on_mission_done)
	if Game.is_new_game():
		get_tree().create_timer(6.0).timeout.connect(func():
			message("Marco", "Hey, du bist zurück! Komm zum Safehouse, ich hab einen Wagen für dich. (Symbol M auf dem Radar)"))


func message(from: String, text: String) -> void:
	messages.push_front({"from": from, "text": text, "time": world.day_night.call("clock_text") if world.day_night else ""})
	AudioManager.play_ui("message", -2.0)
	Events.notify.emit("Neue Nachricht von %s:\n%s" % [from, text], 6.0)


func _on_mission_done(mid: String, _reward: int) -> void:
	var nxt: Dictionary = world.missions.call("next_story") if world.missions else {}
	if mid.begins_with("m0") and not nxt.is_empty():
		get_tree().create_timer(8.0).timeout.connect(func():
			message("Marco", "Gute Arbeit. Nächster Job: \"%s\". %s" % [nxt["title"], nxt["desc"]]))


func _unhandled_input(event: InputEvent) -> void:
	if not event.is_action_pressed("phone"):
		return
	var p := world.player as Player
	if p == null or p.state == Player.State.DEAD or Game.state != Game.State.PLAYING:
		return
	if MenuPanel.is_open():
		if MenuPanel.current.phone_style:
			MenuPanel.current.close()
		return
	get_viewport().set_input_as_handled()
	open_home()


func open_home() -> void:
	AudioManager.play_ui("click", -6.0)
	MenuPanel.open("Telefon", [
		{"label": "Kontakte", "action": _contacts},
		{"label": "Jobs", "action": _jobs},
		{"label": "Nachrichten (%d)" % messages.size(), "action": _messages},
		{"label": "Statistik", "action": _stats},
	], world.day_night.call("clock_text") if world.day_night else "", true)


func _contacts() -> void:
	var items := [
		{"label": "Marco", "desc": "Zeigt dir, wo der nächste Auftrag wartet.", "action": _call_marco},
		{"label": "Aurelia Taxi", "desc": "Bringt dich schnell zum gesetzten Wegpunkt.", "action": _call_taxi},
		{"label": "Torque & Tune Abschleppdienst", "desc": "Liefert eines deiner Fahrzeuge in deine Nähe ($250).",
			"enabled": not Game.player_data.owned_vehicles.is_empty(), "action": _call_mechanic},
	]
	MenuPanel.open("Kontakte", items, "", true)


func _call_marco() -> void:
	var mm := world.missions
	if mm and mm.call("is_active"):
		Events.notify.emit("Marco: \"Konzentrier dich auf den aktuellen Job!\"", 4.0)
		return
	var nxt: Dictionary = mm.call("next_story") if mm else {}
	if nxt.is_empty():
		Events.notify.emit("Marco: \"Du hast alles erledigt. Genieß die Stadt.\"", 4.0)
		return
	var p := world.data.nearest_poi(nxt["giver"], world.data.spawn)
	Events.waypoint_set.emit(p["entrance_v"])
	Events.notify.emit("Marco: \"Komm vorbei – %s. Ich hab's dir auf die Karte gesetzt.\"" % nxt["title"], 5.0)


func _call_taxi() -> void:
	var hud := world.hud as HUD
	var p := world.player as Player
	if hud == null or not hud.gps.active():
		Events.notify.emit("Taxi: \"Wohin soll's gehen? Setz zuerst einen Wegpunkt auf der Karte (M).\"", 4.0)
		return
	if world.police and int(world.police.get("wanted_level")) > 0:
		Events.notify.emit("Taxi: \"Mit der Polizei auf den Fersen? Vergiss es.\"", 4.0)
		return
	var dest: Vector3 = hud.gps.target
	var dist := p.global_position.distance_to(dest)
	var fare := 20 + int(dist / 40.0)
	MenuPanel.open("Aurelia Taxi", [
		{"label": "Fahrt zum Wegpunkt (%.1f km)" % (dist / 1000.0), "price": fare, "action": func():
			var eco := world.economy as PoiManager
			await eco.fade_out_in(func():
				world.streaming.load_area_blocking(dest, 260.0)
				var c := world.graph.closest_lane(dest, 60.0)
				var drop: Vector3 = dest
				var ped: Dictionary = (world.peds as PedManager).ped_graph.closest(dest, 60.0)
				if not ped.is_empty():
					drop = ped["pos"]
				p.teleport(drop + Vector3.UP * 0.3)
				if world.day_night:
					world.day_night.call("advance_hours", clampf(dist / 15000.0, 0.1, 0.6)))
			Events.waypoint_set.emit(Vector3.INF)},
	], "", true)


func _call_mechanic() -> void:
	var items := []
	for e in Game.player_data.owned_vehicles:
		items.append({"label": VehicleDefs.display_name(e["type"]), "price": 250, "action": func():
			for v in get_tree().get_nodes_in_group("vehicles"):
				if (v as Vehicle).owned_id == e["id"] and (v as Vehicle).driver == null:
					v.queue_free()
			var p := world.player as Player
			var c := world.graph.closest_lane(p.global_position, 60.0)
			if c.is_empty():
				return
			var l: RoadGraph.Lane = world.graph.lanes[c["lane"]]
			(world.economy as PoiManager).spawn_owned_vehicle(e, c["pos"], l.dir_at(c["s"]).cross(Vector3.DOWN))
			Events.notify.emit("Dein Fahrzeug wurde an die Straße geliefert.", 3.0)})
	MenuPanel.open("Abschleppdienst", items, "", true)


func _jobs() -> void:
	var mm := world.missions
	var items := []
	var active: bool = mm.call("is_active") if mm else false
	if active:
		items.append({"label": "Aktuellen Auftrag abbrechen", "action": func(): mm.call("abort_current")})
	for key in ["taxi", "courier", "vigilante"]:
		var s: Dictionary = MissionManager.SIDE[key]
		items.append({"label": s["title"], "desc": s["desc"], "enabled": not active, "action": func(): mm.call("start_side", key)})
	items.append({"label": "Straßenrennen finden", "desc": "Setzt einen Wegpunkt zum nächsten Rennstart (R).", "action": func():
		var best := Vector3.INF
		for m in mm.get("_race_markers"):
			if is_instance_valid(m) and (best == Vector3.INF or (m as Node3D).global_position.distance_to(world.player.global_position) \
					< best.distance_to(world.player.global_position)):
				best = (m as Node3D).global_position
		if best != Vector3.INF:
			Events.waypoint_set.emit(best)})
	MenuPanel.open("Jobs", items, "", true)


func _messages() -> void:
	var items := []
	for m in messages:
		items.append({"label": "%s  %s" % [m["time"], m["from"]], "desc": m["text"], "action": func(): pass, "keep_open": true})
	if items.is_empty():
		items.append({"label": "Keine Nachrichten", "enabled": false})
	MenuPanel.open("Nachrichten", items, "", true)


func _stats() -> void:
	var st: Dictionary = Game.player_data.stats
	var mm := world.missions
	var story: Vector2i = mm.call("story_progress") if mm else Vector2i.ZERO
	var items := [
		{"label": "Story-Missionen", "right": "%d / %d" % [story.x, story.y], "enabled": false},
		{"label": "Postkarten", "right": "%d / 25" % Game.player_data.collectibles.size(), "enabled": false},
		{"label": "Verdientes Geld", "right": "$%d" % int(st.get("money_earned", 0)), "enabled": false},
		{"label": "Spielzeit", "right": "%d min" % int(float(st.get("play_time", 0.0)) / 60.0), "enabled": false},
		{"label": "Tode / Verhaftungen", "right": "%d / %d" % [int(st.get("deaths", 0)), int(st.get("arrests", 0))], "enabled": false},
		{"label": "Überfälle", "right": str(int(st.get("robberies", 0))), "enabled": false},
		{"label": "Immobilien", "right": str(Game.player_data.properties.size()), "enabled": false},
		{"label": "Fahrzeuge", "right": str(Game.player_data.owned_vehicles.size()), "enabled": false},
	]
	MenuPanel.open("Statistik", items, "", true)
