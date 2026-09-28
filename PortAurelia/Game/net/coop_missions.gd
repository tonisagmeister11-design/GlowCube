class_name CoopMissions
extends Node
## Co-op missions (multiplayer, at least 2 players). Everything runs on the host: he spawns the
## enemies and vehicles (replicated to every client by Net), checks where all players are and
## publishes the objective, markers and GPS target to everybody. Every player gets the reward.
##
##   "heist"   KOOP-COUP GELDTRANSPORTER  meet up, stop the armoured van, kill the guards, grab both
##             money bags, escape together to the hideout                     $40,000 each
##   "deliver" DOPPELTE LIEFERUNG         pick up together, then everybody delivers his own
##             package to a different part of the city against the clock   $15,000 each
##   "war"     STRASSENKRIEG               hold a street corner together against three waves
##             of gang members                                               $25,000 each

const MISSIONS := {
	"heist": {"title": "Koop-Coup: Geldtransporter", "reward": 40000,
		"desc": "Trefft euch, stoppt den Geldtransporter, erledigt die Wachen, holt beide Geldsäcke und flieht zusammen."},
	"deliver": {"title": "Doppelte Lieferung", "reward": 15000,
		"desc": "Holt die Ware zusammen ab – dann bringt jeder sein Paket in einen anderen Stadtteil. Gegen die Uhr!"},
	"war": {"title": "Straßenkrieg", "reward": 25000,
		"desc": "Haltet zusammen eine Straßenecke gegen drei Wellen einer Gang."},
}

var mission_id := ""
var world: GameWorld
var _spawned: Array = []
var _cancelled := false
var _markers: Array = []
var _per_player := {}
var _objective := ""
var _gps = null
var _timer_end := -1.0


static func start(id: String) -> bool:
	if not Net.is_host() or not MISSIONS.has(id):
		return false
	if Net.coop and is_instance_valid(Net.coop):
		Events.notify.emit("Es läuft schon eine Koop-Mission.", 2.5)
		return false
	if Net.players.size() < 2 and not OS.has_environment("HH_COOP_SOLO"):
		Events.notify.emit("Koop-Missionen braucht mindestens 2 Spieler.", 3.0)
		return false
	var c := CoopMissions.new()
	c.mission_id = id
	c.name = "CoopMission"
	GameWorld.instance.add_child(c)
	Net.coop = c
	return true


func cancel(reason := "Abgebrochen.") -> void:
	if _cancelled:
		return
	_cancelled = true
	_finish(false, reason)


func _ready() -> void:
	world = GameWorld.instance
	_run()


# ------------------------------------------------------------------ players (host view)
## [peer id, position, alive] of everybody in the session.
func players() -> Array:
	var out := []
	var me := world.player as Player
	if me:
		out.append([1, me.global_position, not me.health.dead])
	for id in Net.players:
		if id == 1:
			continue
		var st: Array = Net.players[id].get("st", [])
		if st.size() == Net.STATE_SIZE and Net.players[id].get("world", false):
			out.append([id, st[0], int(st[2]) != 3])
	return out


func all_within(pos: Vector3, r: float) -> bool:
	var ps := players()
	return ps.size() > 0 and ps.all(func(p): return (p[1] as Vector3).distance_to(pos) <= r)


func count_within(pos: Vector3, r: float) -> int:
	return players().filter(func(p): return (p[1] as Vector3).distance_to(pos) <= r).size()


func anyone_within(pos: Vector3, r: float) -> int:
	for p in players():
		if (p[1] as Vector3).distance_to(pos) <= r:
			return int(p[0])
	return 0


func all_dead() -> bool:
	var ps := players()
	return ps.size() > 0 and ps.all(func(p): return not p[2])


## Nearest living player's node on the host (the local player or another player's copy).
func nearest_target(from: Vector3) -> Node3D:
	var best: Node3D = null
	var bd := INF
	var me := world.player as Player
	if me and not me.health.dead:
		best = me
		bd = me.global_position.distance_to(from)
	for id in Net.proxies():
		var pr = Net.proxies()[id]
		if pr and is_instance_valid(pr) and not (pr as RemotePlayer).is_dead():
			var d := (pr as Node3D).global_position.distance_to(from)
			if d < bd:
				bd = d
				best = pr
	return best


# ------------------------------------------------------------------ publishing
func publish() -> void:
	var st := {"active": true, "id": mission_id, "title": MISSIONS[mission_id]["title"], "objective": _objective,
		"markers": _markers, "per_player": _per_player}
	if _gps != null:
		st["gps"] = _gps
	if _timer_end > 0.0:
		st["timer"] = maxf(0.0, _timer_end - Time.get_ticks_msec() / 1000.0)
	Net.host_coop_state(st)


func objective(text: String, markers: Array = [], gps = null) -> void:
	_objective = text
	_markers = markers
	_gps = gps
	publish()


func _finish(ok: bool, text: String) -> void:
	if OS.has_environment("HH_NET_DEBUG"):
		print("NETDBG coop %s finished ok=%s: %s" % [mission_id, ok, text])
	for n in _spawned:
		if is_instance_valid(n):
			if n is NPC and not (n as NPC).is_dead():
				n.queue_free()
			elif n is Vehicle:
				get_tree().create_timer(20.0).timeout.connect(func(): if is_instance_valid(n): n.queue_free())
	_spawned.clear()
	Net.host_coop_state({"active": false})
	Net.host_coop_reward(int(MISSIONS[mission_id]["reward"]) if ok else 0, text)
	if Net.coop == self:
		Net.coop = null
	queue_free()


func wait(t: float) -> void:
	await get_tree().create_timer(t).timeout


## Polls `cond` 5x per second, republishing the objective (live counters); false on cancel/timeout.
func until(cond: Callable, timeout := -1.0, live: Callable = Callable()) -> bool:
	var t0 := Time.get_ticks_msec() / 1000.0
	_timer_end = t0 + timeout if timeout > 0.0 else -1.0
	var k := 0
	while not _cancelled:
		if not Net.is_host():
			return false
		if cond.call():
			_timer_end = -1.0
			return true
		if timeout > 0.0 and Time.get_ticks_msec() / 1000.0 - t0 > timeout:
			_timer_end = -1.0
			cancel("Die Zeit ist abgelaufen.")
			return false
		if all_dead():
			cancel("Alle Spieler sind ausgeschaltet.")
			return false
		if live.is_valid():
			live.call()
		k += 1
		if k % 5 == 0:
			publish()
		await wait(0.2)
	return false


func road_point(from: Vector3, min_d: float, max_d: float, dir_hint := Vector3.ZERO) -> Dictionary:
	var g := world.graph
	var ids := g.lanes_near(from, max_d)
	var rng := RandomNumberGenerator.new()
	rng.randomize()
	for attempt in 80:
		if ids.is_empty():
			break
		var lid: int = ids[rng.randi() % ids.size()]
		var l: RoadGraph.Lane = g.lanes[lid]
		if l.is_connector or l.length < 20.0:
			continue
		var s := rng.randf_range(5.0, l.length - 8.0)
		var p := l.point_at(s)
		var d := p.distance_to(from)
		if d < min_d or d > max_d:
			continue
		if dir_hint != Vector3.ZERO and attempt < 60 and (p - from).normalized().dot(dir_hint) < 0.3:
			continue
		return {"pos": p, "dir": l.dir_at(s), "lane": lid, "s": s}
	return {}


func spawn_enemy(pos: Vector3, weapon: String, hp := 120.0, role := "gang") -> NPC:
	var n: NPC = world.peds.call("spawn_npc", pos, role, {}, true)
	n.weapons.give(weapon, 600)
	n.weapons.equip(weapon)
	n.weapons.accuracy = 0.55
	n.health.max_health = hp
	n.health.health = hp
	n.hostile = true
	n.outlaw = true            # shooting mission enemies is no crime
	var t := nearest_target(pos)
	if t:
		n.engage(t)
	_spawned.append(n)
	Net.host_add_entity(n, "npc", {"outfit": n.outfit})
	return n


func retarget(enemies: Array) -> void:
	for n in enemies:
		if is_instance_valid(n) and not (n as NPC).is_dead():
			var t := nearest_target((n as Node3D).global_position)
			if t and (n as NPC).target != t:
				(n as NPC).engage(t)


func spawn_vehicle(type_id: String, pos: Vector3, dir: Vector3, col: Color) -> Vehicle:
	var v := Vehicle.create(type_id, col)
	world.add_child(v)
	v.global_transform = Transform3D(Basis.looking_at(dir, Vector3.UP), pos + Vector3.UP * 0.6)
	_spawned.append(v)
	if world.traffic:
		(world.traffic as TrafficManager).keep[v] = true
	Net.host_add_entity(v, "vehicle", {"type": type_id, "paint": col})
	return v


func alive(list: Array) -> Array:
	return list.filter(func(n): return is_instance_valid(n) and not (n as NPC).is_dead())


# ------------------------------------------------------------------ missions
func _run() -> void:
	Events.big_message.emit("KOOP-MISSION", MISSIONS[mission_id]["title"], 4.0)
	await wait(0.5)
	match mission_id:
		"heist":
			await _heist()
		"deliver":
			await _deliver()
		"war":
			await _war()


func _heist() -> void:
	objective("Trefft euch alle am Treffpunkt.", [], null)
	var meet_rp := road_point((world.player as Node3D).global_position, 70.0, 150.0)
	var meet: Vector3 = meet_rp.get("pos", (world.player as Node3D).global_position + Vector3(20, 0, 0))
	_markers = [[meet, 12.0, Color(0.3, 0.8, 1.0)]]
	_gps = meet
	var n := players().size()
	if not await until(func(): return all_within(meet, 14.0), 360.0, func():
			_objective = "Trefft euch alle am Treffpunkt (%d/%d da)." % [count_within(meet, 14.0), n]):
		return
	# the van
	var start := road_point(meet, 220.0, 380.0)
	if start.is_empty():
		cancel("Der Transporter ist nicht aufzufinden.")
		return
	var goal := road_point(start["pos"], 900.0, 1400.0)
	var van := spawn_vehicle("delivery", start["pos"], start["dir"], Color(0.2, 0.22, 0.2))
	van.upgrades["armor"] = 2
	van.upgrades["tires"] = 1
	var drv := (world.traffic as TrafficManager).make_driver(van, {})
	if drv:
		drv.respond_to(goal["pos"] if not goal.is_empty() else start["pos"] + start["dir"] * 1000.0)
		drv.cruise_factor = 0.75
	var goal_pos: Vector3 = goal.get("pos", start["pos"] + start["dir"] * 1000.0)
	objective("Haltet den Geldtransporter auf – Motor zerschießen oder rammen!", [], van.global_position)
	if not await until(func():
			if not is_instance_valid(van):
				return true
			if van.global_position.distance_to(goal_pos) < 25.0:
				cancel("Der Transporter ist entkommen.")
				return false
			return van.engine_health < 150.0 or van.destroyed or van.ai_driver == null, 300.0, func():
				if is_instance_valid(van):
					_gps = van.global_position):
		return
	Net.host_coop_wanted(2)
	if drv and is_instance_valid(van) and van.ai_driver != null:
		drv.abandon_vehicle(world.player)
	var at: Vector3 = van.global_position if is_instance_valid(van) else meet
	var guards := []
	for i in 2 + players().size():
		guards.append(spawn_enemy(at + Vector3(randf_range(-4, 4), 0.2, randf_range(-4, 4)), "smg", 150.0, "business"))
	if not await until(func(): return alive(guards).is_empty(), 240.0, func():
			retarget(guards)
			_objective = "Erledigt die Wachleute (%d übrig)." % alive(guards).size()
			_gps = at):
		return
	# two money bags behind the van
	var back := at + Vector3(0, 0.3, 0)
	if is_instance_valid(van):
		back = van.global_position + van.global_basis.z * 3.4
	var bags := [back + Vector3(1.2, 0, 0), back + Vector3(-1.2, 0, 0)]
	var got := [false, false]
	_markers = [[bags[0], 2.0, Color(0.3, 1.0, 0.4)], [bags[1], 2.0, Color(0.3, 1.0, 0.4)]]
	if not await until(func(): return got[0] and got[1], 120.0, func():
			for i in 2:
				if not got[i]:
					var who := anyone_within(bags[i], 2.4)
					if who != 0:
						got[i] = true
						Net._system_message("%s hat einen Geldsack geschnappt!" % Net.player_name(who))
			var mk := []
			for i in 2:
				if not got[i]:
					mk.append([bags[i], 2.0, Color(0.3, 1.0, 0.4)])
			_markers = mk
			_objective = "Schnappt euch die Geldsäcke (%d/2)." % [int(got[0]) + int(got[1])]):
		return
	Net.host_coop_wanted(3)
	var hide := road_point(at, 700.0, 1100.0)
	var hide_pos: Vector3 = hide.get("pos", at + Vector3(700, 0, 0))
	_markers = [[hide_pos, 16.0, Color(0.3, 0.8, 1.0)]]
	_gps = hide_pos
	if not await until(func(): return all_within(hide_pos, 18.0), 420.0, func():
			_objective = "Flieht zusammen ins Versteck (%d/%d da)." % [count_within(hide_pos, 18.0), players().size()]):
		return
	Net.host_coop_wanted(0)
	_finish(true, "Der Coup ist gelungen – $%d für jeden!" % MISSIONS["heist"]["reward"])


func _deliver() -> void:
	var host_pos := (world.player as Node3D).global_position
	var pick_rp := road_point(host_pos, 50.0, 130.0)
	var pick: Vector3 = pick_rp.get("pos", host_pos + Vector3(15, 0, 0))
	_markers = [[pick, 10.0, Color(1.0, 0.8, 0.2)]]
	_gps = pick
	var n := players().size()
	if not await until(func(): return all_within(pick, 12.0), 360.0, func():
			_objective = "Holt die Ware zusammen ab (%d/%d da)." % [count_within(pick, 12.0), n]):
		return
	# one destination per player, in different directions
	var ps := players()
	var dests := {}
	var angle0 := randf() * TAU
	var far := 0.0
	for i in ps.size():
		var a := angle0 + TAU * float(i) / float(ps.size())
		var rp := road_point(pick, 450.0, 850.0, Vector3(cos(a), 0, sin(a)))
		var d: Vector3 = rp.get("pos", pick + Vector3(cos(a), 0, sin(a)) * 500.0)
		dests[int(ps[i][0])] = d
		far = maxf(far, d.distance_to(pick))
	var done := {}
	_markers = []
	_gps = null
	var limit := 150.0 + far / 500.0 * 60.0
	if not await until(func(): return done.size() >= dests.size(), limit, func():
			for pid in dests:
				if done.has(pid):
					continue
				for p in players():
					if int(p[0]) == pid and (p[1] as Vector3).distance_to(dests[pid]) < 10.0:
						done[pid] = true
						Net._system_message("%s hat sein Paket abgeliefert!" % Net.player_name(pid))
			_per_player = {}
			for pid in dests:
				if done.has(pid):
					_per_player[pid] = {"objective": "Paket abgeliefert – warte auf die anderen (%d/%d)." % [done.size(), dests.size()]}
				else:
					_per_player[pid] = {"objective": "Bring dein Paket ans Ziel! (%d/%d geliefert)" % [done.size(), dests.size()],
						"markers": [[dests[pid], 8.0, Color(1.0, 0.8, 0.2)]], "gps": dests[pid]}
			_objective = "Liefert eure Pakete ab."):
		return
	_finish(true, "Alle Pakete sind angekommen – $%d für jeden!" % MISSIONS["deliver"]["reward"])


func _war() -> void:
	var host_pos := (world.player as Node3D).global_position
	var zrp := road_point(host_pos, 90.0, 200.0)
	var zone: Vector3 = zrp.get("pos", host_pos + Vector3(40, 0, 0))
	_markers = [[zone, 18.0, Color(1.0, 0.3, 0.3)]]
	_gps = zone
	var n := players().size()
	if not await until(func(): return all_within(zone, 20.0), 360.0, func():
			_objective = "Geht zusammen zur Straßenecke (%d/%d da)." % [count_within(zone, 20.0), n]):
		return
	_gps = null
	var weapons := ["pistol", "smg", "pistol", "rifle", "shotgun"]
	for wave in 3:
		Net._system_message("Welle %d/3 kommt!" % (wave + 1))
		var enemies := []
		var count := mini(3 + 2 * players().size() + wave * 2, 12)
		for i in count:
			var a := randf() * TAU
			var p := zone + Vector3(cos(a), 0, sin(a)) * randf_range(32.0, 48.0)
			var rp := road_point(p, 0.0, 25.0)
			if not rp.is_empty():
				p = rp["pos"]
			enemies.append(spawn_enemy(p + Vector3.UP * 0.3, weapons[(i + wave) % weapons.size()], 90.0 + wave * 20.0))
			await wait(0.15)
		if not await until(func(): return alive(enemies).is_empty(), 300.0, func():
				retarget(enemies)
				_objective = "Welle %d/3: noch %d Gegner." % [wave + 1, alive(enemies).size()]):
			return
		if wave < 2:
			objective("Welle %d geschafft! Die nächste kommt gleich ..." % (wave + 1), _markers)
			await wait(8.0)
			if _cancelled:
				return
	_finish(true, "Die Straße gehört euch – $%d für jeden!" % MISSIONS["war"]["reward"])
