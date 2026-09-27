extends Mission
## Job "Autoverkäufer" (legal, medium money): customers walk into Aurelia Motors. Talk to
## each one and pick your pitch - an honest price sells more often, a mark-up pays more if
## it works. You earn a commission on every car you sell.

const MODELS := ["compact", "sedan", "suv", "luxury", "sports", "muscle", "supercar"]
var _choice := -1


func run() -> void:
	var dealer := poi("car_dealer")
	if dealer.is_empty():
		fail("Kein Autohaus gefunden.")
		return
	var lot := poi_pos(dealer, 7.0)
	objective("Fahr zum Autohaus %s." % dealer["name"])
	if not await reach(lot, 6.0):
		return
	var total := 0
	var sold := 0
	for i in 4:
		var model: String = MODELS[randi() % MODELS.size()]
		var price := int(VehicleDefs.get_def(model).get("price", 20000))
		var side := (dealer["facing_v"] as Vector3).cross(Vector3.UP).normalized()
		var customer := spawn_npc("business" if price > 60000 else "civilian", lot + side * randf_range(-4.0, 4.0) + Vector3.UP * 0.1)
		customer._set_state(NPC.S.IDLE)
		customer._timer = 1e9
		objective("Kunde %d/4 interessiert sich für einen %s. Sprich ihn an!" % [i + 1, VehicleDefs.display_name(model)])
		var mk := InteractMarker.new()
		mk.prompt = "E: Verkaufsgespräch"
		mk.color = Color(0.4, 1.0, 0.45)
		mk.interact_radius = 2.0
		_choice = -1
		mk.on_interact = func(_pl): _pitch(model, price)
		world.add_child(mk)
		mk.global_position = customer.global_position
		_markers.append(mk)
		if not await until(func(): return _choice >= 0):
			return
		mk.queue_free()
		# 0 discount, 1 fair, 2 mark-up
		var chance: float = [0.9, 0.65, 0.35][_choice]
		var rate: float = [0.02, 0.04, 0.07][_choice]
		if randf() < chance:
			var fee := int(price * rate)
			total += fee
			sold += 1
			Game.player_data.add_money(fee, "job_sales")
			customer.say("buyer")
			Events.big_message.emit("VERKAUFT", "%s · Provision $%d" % [VehicleDefs.display_name(model), fee], 2.5)
		else:
			Events.big_message.emit("KEIN DEAL", "Der Kunde geht.", 2.0)
		customer.start_walking()
		if not await wait(2.0):
			return
	Events.big_message.emit("FEIERABEND", "%d Autos verkauft · $%d Provision" % [sold, total], 3.5)
	complete()


func _pitch(model: String, price: int) -> void:
	MenuPanel.open("Verkaufsgespräch – %s" % VehicleDefs.display_name(model), [
		{"label": "Rabatt anbieten (fast sicher, 2 % Provision)", "action": func(): _choice = 0},
		{"label": "Fairer Preis (65 %, 4 % Provision)", "action": func(): _choice = 1},
		{"label": "Aufschlag verlangen (35 %, 7 % Provision)", "action": func(): _choice = 2},
	], "Listenpreis $%d" % price)
