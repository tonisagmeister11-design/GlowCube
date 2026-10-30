class_name CrimeWorld
extends Node3D
## Weltschicht fuer Kriminalitaet, Banden und Polizei. Verbindet die reinen
## Simulationsklassen (CrimeSystem, Gangs, PoliceCareer) mit der 3D-Welt:
##
## * Bandenauftraege bekommen ein sichtbares Ziel (Markierung + Route) und eine
##   Interaktion am Zielort, die genau den passenden Schritt meldet
##   (pickup/deliver/collect/steal/rob/fight/talk). Schritte lassen sich nur
##   vor Ort erledigen; abgeschlossen werden kann ein Auftrag erst, wenn alle
##   Schritte erledigt sind (`can_complete`).
## * Taten des Spielers mit Risikovorschau (`risk_text`) und sauberer Folge:
##   Fahndung, Einsatzmeldung an den Verkehr, Festnahme, Haft.
## * Haft: Der Spieler wird an die naechste Polizeiwache gesetzt, kann sich
##   nicht frei bewegen (Leine) und hat immer die Optionen „Strafe absitzen“
##   und „Kaution zahlen“ (kein Softlock).
## * Fahndung sichtbar: Streifenwagen in der Naehe werden langsamer und fahren
##   den Spieler an; ab Stufe 3 folgt nach kurzer Vorwarnung die Festnahme.
##
## Die Knoten in der Gruppe "crime_world" sind die Schnittstelle fuer die
## Oberflaeche (game_ui.gd) und die Tests.

const A := preload("res://scripts/crime/contract_access.gd")

## Entfernung, in der ein Auftragsschritt vor Ort gilt (Fallback ohne Gebaeude).
const STEP_RANGE := 60.0
## Hoechstabstand vom Zellenpunkt waehrend der Haft.
const JAIL_LEASH := 16.0
## Abstand, ab dem ein Streifenwagen den gesuchten Spieler stellt.
const ARREST_RANGE := 13.0
## Vorwarnzeit in Sekunden, bevor die Festnahme erfolgt.
const ARREST_WARN := 3.0
## Umkreis, in dem ein geparktes Auto aufgebrochen werden kann.
const CAR_RANGE := 9.0

var world: WorldState
var player: Node3D
## Ersatzposition fuer Tests ohne Spielerknoten.
var fallback_pos := Vector2.ZERO

var objective: Node3D
var car_spot: Node3D
var cell_point: Node3D
var jail_anchor := Vector3.ZERO
var jailed := false
var last_arrest: Dictionary = {}

var _step_key := ""
var _tick := 0.0
var _warn := 0.0
var _dispatch_t := 0.0
var _leash_t := 0.0


# ============================================================ Interaktionen

## Ziel eines Bandenauftrags in der Welt.
class MissionPoint extends Node3D:
	var owner_world: CrimeWorld
	var label := "Auftrag"
	var event := ""

	func get_prompt(_player = null) -> String:
		return label

	func interact(_player = null) -> void:
		if owner_world:
			owner_world.interact_objective()


## Geparktes Auto in Reichweite (Autodiebstahl).
class CarPoint extends Node3D:
	var owner_world: CrimeWorld

	func get_prompt(_player = null) -> String:
		return "Auto aufbrechen"

	func interact(_player = null) -> void:
		if owner_world:
			owner_world.open_panel("crime_car", {"pos": Vector2(global_position.x, global_position.z)})


## Zelle: immer erreichbar, damit die Haft nicht blockiert.
class CellPoint extends Node3D:
	var owner_world: CrimeWorld

	func get_prompt(_player = null) -> String:
		return "Haft: Optionen"

	func interact(_player = null) -> void:
		if owner_world:
			owner_world.open_panel("jail", {})


# ============================================================ Aufbau

func setup(p_world: WorldState, p_player: Node3D = null) -> void:
	world = p_world
	player = p_player
	name = "CrimeWorld"
	add_to_group("crime_world")


func _ready() -> void:
	if not is_in_group("crime_world"):
		add_to_group("crime_world")
	if world == null:
		var game := get_node_or_null("/root/Game")
		if game:
			world = game.world


func _process(delta: float) -> void:
	if world == null:
		return
	if player == null or not is_instance_valid(player):
		player = get_tree().get_first_node_in_group("player") as Node3D
	_tick -= delta
	if _tick <= 0.0:
		_tick = 0.35
		tick(0.35)
	_update_jail_leash()
	_update_pursuit(delta)


## Regelmaessige Aktualisierung (eigener Takt, damit Tests sie direkt rufen).
func tick(delta: float) -> void:
	if world == null:
		return
	var c = crime()
	if c == null:
		return
	var p2 := pos2()
	if c.has_method("set_player_context"):
		c.set_player_context(p2, false, -1)
	_sync_jail()
	if not jailed:
		_sync_objective()
		_sync_car_spot()
	_dispatch_t -= delta


# ============================================================ Zugriffe

func crime():
	return world.get("crime") if world != null else null


func gangs():
	var c = crime()
	return c.get("gangs") if c != null else null


func police():
	var c = crime()
	return c.get("police") if c != null else null


func traffic():
	if not is_inside_tree():
		return null
	return get_tree().get_first_node_in_group("traffic_manager")


func pos2() -> Vector2:
	if player != null and is_instance_valid(player):
		return Vector2(player.global_position.x, player.global_position.z)
	return fallback_pos


func pos3() -> Vector3:
	var p := pos2()
	return Vector3(p.x, 0.2, p.y)


func open_panel(kind: String, ctx: Dictionary) -> void:
	var bus := get_node_or_null("/root/EventBus")
	if bus:
		bus.open_panel.emit(kind, ctx)


func notify(text: String, kind := "info") -> void:
	if world != null:
		world.notify(text, kind)


# ============================================================ Auftraege

## Laufender Auftrag des Spielers (Rohdaten) oder {}.
func active_mission() -> Dictionary:
	var gs = gangs()
	if gs == null:
		return {}
	var ids: Array = gs.missions.keys()
	ids.sort()
	for id in ids:
		var m: Dictionary = gs.missions[id]
		if str(m.get("status", "")) == "active":
			return m
	return {}


## Auftrag nach ID, sonst der erste laufende Auftrag.
func mission_of(id: int) -> Dictionary:
	var gs = gangs()
	if gs == null:
		return {}
	if id >= 0:
		var m: Dictionary = gs.get_mission(id)
		return m if str(m.get("status", "")) == "active" else {}
	return active_mission()


## Naechster offener Schritt eines laufenden Auftrags oder {}.
func current_step(id := -1) -> Dictionary:
	var m := mission_of(id)
	if m.is_empty():
		return {}
	for s in m.steps:
		if not bool(s.get("done", false)):
			return s
	return {}


## Zielpunkt eines Schritts auf begehbarem Boden.
func step_target(step: Dictionary) -> Vector2:
	if step.is_empty():
		return Vector2.ZERO
	var bid := int(step.get("building", -1))
	if bid >= 0:
		return A.building_pos(world, bid)
	var p := A.to_v2(step.get("pos", Vector2.ZERO))
	if world.city == null:
		return p
	var ne: Dictionary = world.city.nearest_edge(p)
	if int(ne.get("edge", -1)) >= 0:
		var e: Dictionary = world.city.edges[int(ne.edge)]
		return world.city.edge_point(e, float(ne.t))
	return p


## Entfernung zum aktuellen Auftragsziel in Metern (-1 ohne Auftrag).
func step_distance(id := -1) -> float:
	var st := current_step(id)
	if st.is_empty():
		return -1.0
	return pos2().distance_to(step_target(st))


## Eine knappe Zeile fuer das HUD.
func mission_hud_line() -> String:
	var m := active_mission()
	if m.is_empty():
		return ""
	var st := current_step()
	if st.is_empty():
		return "Auftrag: %s · alle Schritte erledigt – im Telefon abschließen" % str(m.get("label", ""))
	return "Auftrag: %s · %s · %d m" % [str(m.get("label", "")), str(st.get("label", "")), int(round(step_distance()))]


func duty_hud_line() -> String:
	var pol = police()
	if pol == null or not bool(pol.get("member")):
		return ""
	return "Im Dienst · %s" % str(pol.title())


func jail_hud_line() -> String:
	var c = crime()
	if c == null or not c.in_jail(world):
		return ""
	return "In Haft · noch %.1f Std. · %s" % [c.jail_remaining_minutes(world) / 60.0, str(c.jail_reason)]


## Setzt Markierung und Route auf den aktuellen Schritt.
func _sync_objective() -> void:
	var st := current_step()
	var m := active_mission()
	if st.is_empty() or m.is_empty():
		_step_key = ""
		if objective and is_instance_valid(objective):
			objective.queue_free()
			objective = null
		return
	var target := step_target(st)
	var key := "%d:%s:%.1f:%.1f" % [int(m.id), str(st.get("event", "")), target.x, target.y]
	if objective == null or not is_instance_valid(objective):
		objective = _make_marker()
		add_child(objective)
	(objective as MissionPoint).owner_world = self
	(objective as MissionPoint).label = "%s: %s" % [str(m.get("label", "Auftrag")), str(st.get("label", ""))]
	(objective as MissionPoint).event = str(st.get("event", ""))
	objective.global_position = Vector3(target.x, 0.6, target.y)
	if key != _step_key:
		_step_key = key
		var bus := get_node_or_null("/root/EventBus")
		if bus:
			bus.request_route.emit(Vector3(target.x, 0.0, target.y), str(st.get("label", "Auftrag")))


func _make_marker() -> MissionPoint:
	var n := MissionPoint.new()
	n.add_to_group("interactable")
	var mi := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = 0.35
	cm.bottom_radius = 0.35
	cm.height = 1.6
	mi.mesh = cm
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(1.0, 0.72, 0.2, 0.42)
	mat.emission_enabled = true
	mat.emission = Color(1.0, 0.72, 0.2)
	mat.emission_energy_multiplier = 1.6
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	mi.material_override = mat
	mi.position.y = 0.8
	n.add_child(mi)
	return n


## Meldet den Schritt am Zielort. Wird von der Markierung aufgerufen.
func interact_objective() -> Dictionary:
	var st := current_step()
	if st.is_empty():
		notify("Kein offener Auftragsschritt.", "warning")
		return {"ok": false, "reason": "Kein offener Auftragsschritt."}
	var r := progress_here(str(st.get("event", "")), {})
	if bool(r.get("ok", false)):
		var txt := str(r.get("text", ""))
		if txt != "":
			notify(txt, "success")
		if bool(r.get("done", false)):
			notify("Alle Schritte erledigt – im Telefon abschließen.", "info")
	else:
		notify(str(r.get("reason", "Nicht möglich.")), "warning")
	_sync_objective()
	return r


## Schritt des laufenden Auftrags vor Ort melden. Die Position wird immer
## mitgegeben, damit Gangs die Ortspruefung durchfuehren kann; fuer Schritte
## ohne Gebaeude (Revierkampf, Anwerbung) prueft diese Schicht selbst.
func progress_here(event: String, data := {}) -> Dictionary:
	var gs = gangs()
	var m := active_mission()
	if gs == null or m.is_empty():
		return {"ok": false, "reason": "Kein laufender Auftrag."}
	var st := current_step()
	if st.is_empty() or str(st.get("event", "")) != event:
		return {"ok": false, "reason": "Dieser Schritt ist gerade nicht dran."}
	var p := pos2()
	if int(st.get("building", -1)) < 0 and p.distance_to(step_target(st)) > STEP_RANGE:
		return {"ok": false, "reason": "Du bist nicht am Zielort."}
	var d := data.duplicate()
	d["pos"] = p
	if st.has("business_id"):
		d["business_id"] = int(st.business_id)
	var r: Dictionary = gs.progress_mission(world, int(m.id), event, d)
	_after_crime(r.get("crime", {}))
	return r


## Darf der Auftrag abgeschlossen werden? Nur wenn alle Schritte in der Welt
## erledigt wurden (Schutz vor Abschluss aus dem Menue heraus).
func can_complete(id := -1) -> bool:
	var gs = gangs()
	if gs == null:
		return false
	var m: Dictionary = gs.get_mission(id) if id >= 0 else active_mission()
	if m.is_empty() or str(m.get("status", "")) != "active":
		return false
	for s in m.steps:
		if not bool(s.get("done", false)):
			return false
	return true


## Abschluss des laufenden Auftrags (mit Pruefung).
func complete_active_mission(id := -1) -> Dictionary:
	var gs = gangs()
	var m := mission_of(id)
	if gs == null or m.is_empty():
		return {"ok": false, "reason": "Kein laufender Auftrag."}
	if not can_complete(int(m.id)):
		return {"ok": false, "reason": "Noch nicht alle Schritte vor Ort erledigt."}
	var r: Dictionary = gs.complete_mission(world, int(m.id))
	_step_key = ""
	_sync_objective()
	var bus := get_node_or_null("/root/EventBus")
	if bus:
		bus.route_cleared.emit()
	return r


# ============================================================ Taten

## Risikovorschau als deutscher Text.
func risk_text(type: String, ctx := {}) -> String:
	var c = crime()
	if c == null:
		return ""
	var cx := ctx.duplicate()
	if not cx.has("pos"):
		cx["pos"] = pos2()
	var r: Dictionary = c.risk_preview(world, type, cx)
	if r.is_empty():
		return ""
	var jail := float(r.get("jail_hours", 0.0))
	return "Erfolgschance %d %% · %d mögliche Zeugen · Fahndung +%d · Strafe %s · %s" % [
		int(round(float(r.get("chance", 0.0)) * 100.0)), int(r.get("witnesses_near", 0)),
		int(r.get("wanted_if_seen", 0)), Economy.fmt_money(float(r.get("fine", 0.0))),
		("Haft bis %.0f Std." % jail) if jail > 0.0 else "keine Haft"]


## Fuehrt eine Tat des Spielers aus und kuemmert sich um die Folgen in der Welt
## (Einsatz, Haft). Rueckgabe wie CrimeSystem.attempt.
func attempt(type: String, ctx := {}) -> Dictionary:
	var c = crime()
	if c == null:
		return {"ok": false, "reason": "Nicht möglich."}
	if c.in_jail(world):
		return {"ok": false, "reason": "Du sitzt im Gefängnis."}
	var cx := ctx.duplicate()
	if not cx.has("pos"):
		cx["pos"] = pos2()
	var r: Dictionary = c.attempt(world, type, cx)
	_after_crime(r)
	return r


## Autodiebstahl an einem geparkten Auto: bei Erfolg wird es entriegelt.
func steal_parked_car(pos: Vector2) -> Dictionary:
	var r := attempt("car_theft", {"pos": pos})
	if bool(r.get("ok", false)) and bool(r.get("success", false)):
		var tm = traffic()
		if tm != null and tm.has_method("unlock_nearest_parked"):
			if tm.unlock_nearest_parked(Vector3(pos.x, 0.0, pos.y), CAR_RANGE):
				r["text"] = str(r.get("text", "")) + " Das Auto ist offen – steig ein."
			else:
				r["text"] = str(r.get("text", "")) + " Das Auto ist nicht mehr da."
	return r


func _after_crime(r) -> void:
	if not (r is Dictionary) or r.is_empty():
		return
	if bool(r.get("arrested", false)):
		_sync_jail()
	var c = crime()
	if c != null and bool(r.get("witnessed", false)):
		_dispatch_t = 0.0


# ============================================================ Haft

func _sync_jail() -> void:
	var c = crime()
	if c == null:
		return
	var now: bool = c.in_jail(world)
	if now and not jailed:
		_enter_jail()
	elif not now and jailed:
		_leave_jail()


func _enter_jail() -> void:
	jailed = true
	var c = crime()
	if c == null:
		return
	var p := pos2()
	var bid := int(c.nearest_police_station(world, p))
	var anchor := p
	if bid >= 0:
		anchor = A.building_pos(world, bid)
	jail_anchor = Vector3(anchor.x, 0.2, anchor.y)
	_teleport(jail_anchor)
	if objective and is_instance_valid(objective):
		objective.queue_free()
		objective = null
	_step_key = ""
	if car_spot and is_instance_valid(car_spot):
		car_spot.queue_free()
		car_spot = null
	if is_inside_tree():
		cell_point = CellPoint.new()
		(cell_point as CellPoint).owner_world = self
		cell_point.add_to_group("interactable")
		add_child(cell_point)
		cell_point.global_position = jail_anchor + Vector3(0, 1.0, 0)
	var bus := get_node_or_null("/root/EventBus")
	if bus:
		bus.route_cleared.emit()
	notify("Du sitzt in der Zelle der Polizeiwache.", "danger")
	open_panel("jail", {})


func _leave_jail() -> void:
	jailed = false
	_warn = 0.0
	if cell_point and is_instance_valid(cell_point):
		cell_point.queue_free()
		cell_point = null
	_teleport(release_position())
	notify("Du bist wieder auf freiem Fuß.", "success")


## Vernuenftiger Entlassungspunkt: Gehweg vor der Wache.
func release_position() -> Vector3:
	var c = crime()
	if c == null or world == null or world.city == null:
		return jail_anchor
	var anchor := Vector2(jail_anchor.x, jail_anchor.z)
	var bid := int(c.nearest_police_station(world, anchor))
	if bid >= 0:
		var b: Dictionary = world.city.buildings[bid]
		var sp := A.to_v2(b.get("sidewalk_point", b.get("door", anchor)))
		return Vector3(sp.x, 0.2, sp.y)
	return jail_anchor


func _update_jail_leash() -> void:
	if not jailed or player == null or not is_instance_valid(player):
		return
	var pp: Vector3 = player.global_position
	if pp.distance_to(jail_anchor) <= JAIL_LEASH:
		return
	_teleport(jail_anchor)
	_leash_t -= 0.1
	if _leash_t <= 0.0:
		_leash_t = 6.0
		notify("Aus der Zelle kommst du nicht heraus.", "warning")


func _teleport(target: Vector3) -> void:
	if player == null or not is_instance_valid(player):
		return
	if player.has_method("teleport"):
		player.teleport(target)
	else:
		player.global_position = target


## Strafe absitzen: Zeit ueberspringen und freilassen.
func serve_sentence() -> Dictionary:
	var c = crime()
	if c == null or not c.in_jail(world):
		return {"ok": false, "reason": "Du bist nicht in Haft."}
	var r: Dictionary = c.serve_sentence(world, false)
	var minutes := float(r.get("minutes", 0.0))
	var game := get_node_or_null("/root/Game")
	if game and game.has_method("skip_time"):
		game.skip_time(minutes + WorldState.STEP_MINUTES)
	else:
		world.advance(minutes + WorldState.STEP_MINUTES)
	# Verlaessliche Entlassung, auch wenn ein Rest unter einem Simulationsschritt bleibt
	if c.in_jail(world):
		c._release_player(world)
	_sync_jail()
	return r


func post_bail() -> Dictionary:
	var c = crime()
	if c == null:
		return {"ok": false, "reason": "Nicht möglich."}
	var r: Dictionary = c.post_bail(world)
	_sync_jail()
	return r


# ============================================================ Fahndung

func _update_pursuit(delta: float) -> void:
	var c = crime()
	if c == null or jailed:
		_warn = 0.0
		return
	var lvl := int(c.wanted_level())
	if lvl <= 0:
		_warn = 0.0
		return
	var p := pos2()
	var hiding: bool = c.has_method("is_hiding") and c.is_hiding(world)
	var tm = traffic()
	if _dispatch_t <= 0.0:
		_dispatch_t = 25.0
		if lvl >= 2 and not hiding:
			c.add_dispatch(p, "pursuit", "Fahndung: Stufe %d" % lvl, -1, lvl)
		if tm != null and tm.get("sim") != null:
			for e in tm.emergency:
				var ai = e.get("ai")
				if ai != null and str(ai.type) == "police" and (ai.pos as Vector2).distance_to(p) < 200.0:
					tm.sim.set_target(ai, p)
	if tm == null:
		return
	var near := false
	for e in tm.emergency:
		var ai2 = e.get("ai")
		if ai2 == null or str(ai2.type) != "police":
			continue
		var d: float = (ai2.pos as Vector2).distance_to(p)
		if d < 70.0:
			ai2.v_factor = 0.6
		if d < ARREST_RANGE:
			near = true
	if near and lvl >= 3 and not hiding:
		if _warn <= 0.0:
			notify("Polizei: Stehen bleiben!", "danger")
		_warn += delta
		if _warn >= ARREST_WARN:
			_warn = 0.0
			last_arrest = c.arrest_player(world, "Streifenwagen hat dich gestellt")
			_sync_jail()
	else:
		_warn = maxf(0.0, _warn - delta * 2.0)


# ============================================================ Geparkte Autos

func _sync_car_spot() -> void:
	if not is_inside_tree():
		return
	var tm = traffic()
	var best := Vector2.INF
	var bd := CAR_RANGE
	var p := pos2()
	var proxies = tm.get("_proxies") if tm != null else null
	if proxies is Array:
		for px in proxies:
			if px == null or not is_instance_valid(px) or px.spot.is_empty() or not px.locked:
				continue
			var d: float = (px.spot.pos as Vector2).distance_to(p)
			if d < bd:
				bd = d
				best = px.spot.pos
	if best == Vector2.INF:
		if car_spot and is_instance_valid(car_spot):
			car_spot.queue_free()
			car_spot = null
		return
	if car_spot == null or not is_instance_valid(car_spot):
		car_spot = CarPoint.new()
		(car_spot as CarPoint).owner_world = self
		car_spot.add_to_group("interactable")
		add_child(car_spot)
	car_spot.global_position = Vector3(best.x, 1.0, best.y)


# ============================================================ Hilfen fuer die Oberflaeche

## Darf der Spieler jemanden bestehlen? (Bande oder kriminelle Vergangenheit.)
func can_pickpocket() -> bool:
	var gs = gangs()
	if gs != null and int(gs.player_gang) >= 0:
		return true
	var c = crime()
	if c != null and not (c.record as Array).is_empty():
		return true
	return A.player_skill(world, "stealth", 0.0) >= 25.0


## Ist der Spieler im Polizeidienst?
func on_duty() -> bool:
	var pol = police()
	return pol != null and bool(pol.get("member"))
