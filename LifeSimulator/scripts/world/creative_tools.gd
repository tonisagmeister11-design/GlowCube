class_name CreativeTools
extends RefCounted
## Werkzeuge des Kreativmodus (Testmodus).
##
## Jede Methode tut nur etwas, wenn die Welt im Modus "creative" laeuft. In
## jedem anderen Modus kehrt sie ohne Wirkung zurueck - so kann ein Cheat nie in
## ein normales Spiel rutschen, auch nicht ueber ein versehentlich geoeffnetes
## Menue oder eine Taste.
##
## Geld entsteht ausschliesslich ueber Ledger.mint(); die Geldmengen-Invariante
## (Summe der Salden = minted - burned) bleibt dadurch erhalten.

const MODE := "creative"


static func active(world) -> bool:
	return world != null and world.rules != null and str(world.rules.mode) == MODE


static func _life(world):
	return world.player if world != null else null


# ============================================================ Geld

static func give_money(world, amount: float) -> bool:
	if not active(world) or world.ledger == null:
		return false
	var life = _life(world)
	if life == null or int(life.account) < 0:
		return false
	world.ledger.mint(int(life.account), amount, "Kreativmodus")
	world.notify("+%s (Kreativmodus)" % Economy.fmt_money(amount), "success")
	return true


# ============================================================ Dinge und Fahrzeuge

## Ware oder Fahrzeug direkt geben, ohne Kauf und ohne Altersgrenze.
static func give_item(world, good_id: String, qty := 1) -> bool:
	if not active(world):
		return false
	var life = _life(world)
	var g: Dictionary = GoodsCatalog.get_good(good_id)
	if life == null or g.is_empty() or qty <= 0:
		return false
	if bool(g.get("vehicle", false)):
		for i in qty:
			life.vehicles.append({"good": good_id, "name": str(g.name), "bought_day": world.get_day()})
	elif bool(g.get("service", false)):
		return false
	else:
		life.inventory[good_id] = int(life.inventory.get(good_id, 0)) + qty
	return true


## Alle Fahrzeugwaren, die ein Fremdmodell haben (die KALDRA-Fahrzeuge) plus
## alle uebrigen Fahrzeugwaren, jeweils einmal.
static func give_all_vehicles(world) -> int:
	if not active(world):
		return 0
	var life = _life(world)
	if life == null:
		return 0
	var owned := {}
	for v in life.vehicles:
		owned[str(v.get("good", ""))] = true
	var n := 0
	for id in GoodsCatalog.GOODS:
		var g: Dictionary = GoodsCatalog.GOODS[id]
		if bool(g.get("vehicle", false)) and str(g.get("category", "")) == "vehicle" and not owned.has(id):
			if give_item(world, str(id), 1):
				n += 1
	return n


## Alle Waren, die kein Fahrzeug und keine Dienstleistung sind.
static func item_ids() -> Array:
	var out: Array = []
	for id in GoodsCatalog.GOODS:
		var g: Dictionary = GoodsCatalog.GOODS[id]
		if not bool(g.get("vehicle", false)) and not bool(g.get("service", false)):
			out.append(str(id))
	out.sort()
	return out


static func vehicle_ids() -> Array:
	var out: Array = []
	for id in GoodsCatalog.GOODS:
		var g: Dictionary = GoodsCatalog.GOODS[id]
		if bool(g.get("vehicle", false)) and str(g.get("category", "")) == "vehicle":
			out.append(str(id))
	out.sort()
	return out


# ============================================================ Spieler

static func heal_all(world) -> bool:
	if not active(world):
		return false
	var life = _life(world)
	if life == null:
		return false
	life.needs = Needs.full()
	for k in ["hunger", "thirst", "energy", "hygiene", "social", "fun", "health"]:
		life.needs[k] = 100.0
	return true


static func clear_wanted(world) -> bool:
	if not active(world):
		return false
	var life = _life(world)
	if life == null:
		return false
	life.wanted = 0
	return true


# ============================================================ Welt

static func set_hour(world, hour: float) -> bool:
	if not active(world):
		return false
	var day_start: float = floor(world.time_minutes / 1440.0) * 1440.0
	world.time_minutes = day_start + clampf(hour, 0.0, 23.99) * 60.0
	return true


## Gebaeude nach Art, geordnet nach Abstand zu `from`.
static func buildings_of(world, kind: String, from: Vector2) -> Array:
	var out: Array = []
	if world == null or world.city == null:
		return out
	for b in world.city.buildings:
		if str(b.business_type) == kind or str(b.kind) == kind:
			out.append(b)
	out.sort_custom(func(a, b): return a.sidewalk_point.distance_to(from) < b.sidewalk_point.distance_to(from))
	return out


## Spieler vor das naechste Gebaeude der Art setzen.
static func teleport_to_kind(world, kind: String) -> bool:
	if not active(world):
		return false
	var player := _player_node()
	if player == null:
		return false
	var from := Vector2(player.global_position.x, player.global_position.z)
	var list := buildings_of(world, kind, from)
	if list.is_empty():
		return false
	var p: Vector2 = list[0].sidewalk_point
	player.teleport(Vector3(p.x, 0.3, p.y), 0.0)
	return true


static func teleport_to_building(world, building_id: int) -> bool:
	if not active(world) or building_id < 0 or building_id >= world.city.buildings.size():
		return false
	var player := _player_node()
	if player == null:
		return false
	var p: Vector2 = world.city.buildings[building_id].sidewalk_point
	player.teleport(Vector3(p.x, 0.3, p.y), 0.0)
	return true


static func _player_node() -> Node3D:
	var loop := Engine.get_main_loop()
	if not (loop is SceneTree):
		return null
	for n in (loop as SceneTree).get_nodes_in_group("player"):
		if n is Node3D and n.has_method("teleport"):
			return n
	return null


# ============================================================ Geschichte

## Alle Kapitel freischalten.
static func unlock_story(world) -> int:
	if not active(world) or world.get("story") == null:
		return 0
	var n := 0
	for c in StoryCatalog.CHAPTERS:
		if not world.story.unlocked.has(str(c.id)):
			world.story.unlocked.append(str(c.id))
			n += 1
	return n


static func start_mission(world, mission_id: String) -> Dictionary:
	if not active(world) or world.get("story") == null:
		return {"ok": false, "reason": "Nur im Kreativmodus."}
	unlock_story(world)
	return world.story.accept(mission_id)
