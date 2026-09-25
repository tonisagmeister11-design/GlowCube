class_name RandomEvents
extends Node
## Ambient random events that happen near the player during free roam:
##   purse snatcher (catch the thief), police chase (cops pursue a criminal car),
##   gang shootout (two crews fight each other), armoured van (rob it at your own risk).
## At most one event at a time, none during missions or while wanted.

var world: GameWorld
var active := {}                # {kind, nodes: [], blips: [], timer}
var _cooldown := 60.0
var rng := RandomNumberGenerator.new()


func _ready() -> void:
	name = "RandomEvents"
	world = GameWorld.instance
	world.events = self
	rng.randomize()


func _process(delta: float) -> void:
	if world.player == null or get_tree().paused:
		return
	if not active.is_empty():
		_update_active(delta)
		return
	_cooldown -= delta
	if _cooldown > 0.0:
		return
	_cooldown = rng.randf_range(90.0, 180.0)
	if world.missions and world.missions.call("is_active"):
		return
	if world.police and int(world.police.get("wanted_level")) > 0:
		return
	var kinds := ["purse", "police_chase", "gang_fight", "armored_van"]
	trigger(kinds[rng.randi() % kinds.size()])


func trigger(kind: String) -> bool:
	if not active.is_empty():
		return false
	var ok := false
	match kind:
		"purse":
			ok = _purse()
		"police_chase":
			ok = _police_chase()
		"gang_fight":
			ok = _gang_fight()
		"armored_van":
			ok = _armored_van()
	if ok:
		Events.random_event.emit(kind, world.player.global_position)
	return ok


func blips() -> Array:
	var out := []
	for b in active.get("blips", []):
		if is_instance_valid(b[0]) and not (b[0] is NPC and (b[0] as NPC).is_dead()):
			out.append({"pos": (b[0] as Node3D).global_position, "icon": "", "color": b[1], "size": 8.0, "edge": false})
	return out


func _end(note := "") -> void:
	for n in active.get("nodes", []):
		if not is_instance_valid(n):
			continue
		if n is NPC:
			(n as NPC).persistent = false
		elif n is Vehicle and world.traffic:
			(world.traffic as TrafficManager).unprotect(n)
	if note != "":
		Events.notify.emit(note, 3.5)
	active = {}


func _update_active(delta: float) -> void:
	active["timer"] = float(active.get("timer", 0.0)) + delta
	var pp := world.player.global_position
	match active["kind"]:
		"purse":
			var thief: NPC = active["thief"]
			if not is_instance_valid(thief):
				_end()
			elif thief.is_dead() or thief.state == NPC.S.KNOCKED or thief.health.health < 50.0:
				Pickup.spawn(world, thief.global_position + Vector3(0.6, 0, 0), "money", rng.randi_range(150, 450))
				_end("Du hast den Taschendieb gestellt! Die Beute gehört dir.")
			elif thief.global_position.distance_to(pp) > 170.0 or float(active["timer"]) > 90.0:
				_end("Der Dieb ist entkommen.")
			elif thief.state != NPC.S.FLEE:
				thief.flee_from(pp, 30.0)
		"police_chase":
			var crook: Vehicle = active["crook"]
			if not is_instance_valid(crook) or crook.destroyed or crook.global_position.distance_to(pp) > 350.0 \
					or float(active["timer"]) > 120.0:
				for c in active["cops"]:
					if is_instance_valid(c) and (c as Vehicle).ai_driver:
						(c as Vehicle).set_siren(false)
						((c as Vehicle).ai_driver as TrafficDriver).resume_traffic()
				_end()
		"gang_fight":
			var alive := 0
			for n in active["nodes"]:
				if is_instance_valid(n) and not (n as NPC).is_dead():
					alive += 1
			if alive <= 1 or float(active["timer"]) > 90.0 or (active["nodes"][0] as Node3D).global_position.distance_to(pp) > 250.0:
				_end()
		"armored_van":
			var van: Vehicle = active["van"]
			if not is_instance_valid(van):
				_end()
			elif van.destroyed or van.engine_health < 250.0 or (van.ai_driver == null and not active.get("looted", false)):
				active["looted"] = true
				for i in 3:
					var pk := Pickup.spawn(world, van.global_position + van.global_basis.z * 3.0 + Vector3(i - 1.0, 0, 0), "money",
						rng.randi_range(600, 1100))
					pk.lifetime = 120.0
				if world.police:
					world.police.call("set_wanted", maxi(2, int(world.police.get("wanted_level"))))
				_end("Der Geldtransporter ist aufgebrochen – schnapp dir das Geld!")
			elif van.global_position.distance_to(pp) > 400.0 or float(active["timer"]) > 150.0:
				_end()


# ------------------------------------------------------------------ events
func _purse() -> bool:
	var pm := world.peds as PedManager
	if pm == null:
		return false
	var sp := pm.ped_graph.random_point(world.player.global_position, 30.0, 70.0, rng)
	if sp.is_empty():
		return false
	var victim := pm.spawn_npc(sp["pos"], "civilian", {}, false)
	var thief := pm.spawn_npc(sp["pos"] + Vector3(1.2, 0, 0.5), "civilian", Outfits.random("gang", rng), true)
	thief.bravery = 0.0
	victim.cower(4.0)
	thief.flee_from(world.player.global_position, 30.0)
	AudioManager.play_voice("scream", victim.global_position)
	Events.notify.emit("\"Hilfe! Haltet den Dieb!\" – Schnapp dir den Taschendieb.", 4.0)
	active = {"kind": "purse", "thief": thief, "nodes": [thief], "blips": [[thief, Color(0.95, 0.3, 0.3)]]}
	return true


func _police_chase() -> bool:
	var tm := world.traffic as TrafficManager
	var g := world.graph
	var ids := g.lanes_near(world.player.global_position, 200.0)
	for attempt in 20:
		if ids.is_empty():
			return false
		var lid: int = ids[rng.randi() % ids.size()]
		var l: RoadGraph.Lane = g.lanes[lid]
		if l.is_connector or l.length < 40.0:
			continue
		var s := rng.randf_range(20.0, l.length - 5.0)
		var p := l.point_at(s)
		var d := p.distance_to(world.player.global_position)
		if d < 90.0 or d > 200.0:
			continue
		var dir := l.dir_at(s)
		var crook := Vehicle.create(["sports", "sedan", "compact"][rng.randi() % 3])
		crook.transform = Transform3D(Basis.looking_at(dir, Vector3.UP), p + Vector3.UP * 0.5)
		world.add_child(crook)
		var drv := tm.make_driver(crook, Outfits.random("gang", rng))
		if drv == null:
			crook.queue_free()
			return false
		drv.mode = TrafficDriver.Mode.FLEE
		drv._mode_timer = 9999.0
		tm.keep[crook] = true
		var cops := []
		for i in 2:
			var cp := p - dir * (14.0 + i * 12.0)
			var cv := Vehicle.create("police")
			cv.transform = Transform3D(Basis.looking_at(dir, Vector3.UP), cp + Vector3.UP * 0.5)
			world.add_child(cv)
			var cd := tm.make_driver(cv, Outfits.random("cop", rng))
			if cd:
				cd.pursue(crook)
				tm.keep[cv] = true
				cv.set_siren(true)
				cops.append(cv)
		active = {"kind": "police_chase", "crook": crook, "cops": cops, "nodes": [crook] + cops,
			"blips": [[crook, Color(0.95, 0.3, 0.3)]]}
		return true
	return false


func _gang_fight() -> bool:
	var pm := world.peds as PedManager
	var sp := pm.ped_graph.random_point(world.player.global_position, 50.0, 100.0, rng)
	if sp.is_empty():
		return false
	var c: Vector3 = sp["pos"]
	var a := []
	var b := []
	for i in 2:
		var n1 := pm.spawn_npc(c + Vector3(i * 1.5, 0.1, 0), "gang", Outfits.random("gang", rng), true)
		n1.weapons.give("pistol", 60)
		a.append(n1)
		var n2 := pm.spawn_npc(c + Vector3(i * 1.5, 0.1, 14.0), "gang", Outfits.random("gang", rng), true)
		n2.weapons.give("pistol" if i == 0 else "smg", 90)
		b.append(n2)
	for i in 2:
		(a[i] as NPC).engage(b[i])
		(b[i] as NPC).engage(a[(i + 1) % 2])
	Events.notify.emit("Schüsse in der Nähe – eine Bandenschießerei!", 3.5)
	active = {"kind": "gang_fight", "nodes": a + b, "blips": []}
	for n in a + b:
		active["blips"].append([n, Color(0.95, 0.4, 0.2)])
	return true


func _armored_van() -> bool:
	var tm := world.traffic as TrafficManager
	var g := world.graph
	var ids := g.lanes_near(world.player.global_position, 180.0)
	for attempt in 20:
		if ids.is_empty():
			return false
		var lid: int = ids[rng.randi() % ids.size()]
		var l: RoadGraph.Lane = g.lanes[lid]
		if l.is_connector or l.length < 30.0:
			continue
		var s := rng.randf_range(10.0, l.length - 5.0)
		var p := l.point_at(s)
		var d := p.distance_to(world.player.global_position)
		if d < 70.0 or d > 180.0:
			continue
		var van := Vehicle.create("van", Color(0.85, 0.86, 0.88))
		van.transform = Transform3D(Basis.looking_at(l.dir_at(s), Vector3.UP), p + Vector3.UP * 0.5)
		world.add_child(van)
		van.upgrades["armor"] = 2
		var drv := tm.make_driver(van, Outfits.random("worker", rng))
		if drv == null:
			van.queue_free()
			return false
		tm.keep[van] = true
		Events.notify.emit("Ein Geldtransporter ist in der Nähe unterwegs...", 4.0)
		active = {"kind": "armored_van", "van": van, "nodes": [van], "blips": [[van, Color(0.4, 0.9, 0.4)]]}
		return true
	return false
