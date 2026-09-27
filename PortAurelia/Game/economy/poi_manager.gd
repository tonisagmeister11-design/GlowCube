class_name PoiManager
extends Node
## Economy & points of interest: shops, weapon store, clothing, mechanic (repair,
## respray, upgrades), car dealer, hospital, properties (purchase + daily income),
## safehouses (save, sleep, wardrobe, garage) and bus stops (public transport).
## Store robberies happen inside the shop interiors (see InteriorManager). Creates interaction markers and radar blips for all of them.

const ICONS := {
	"shop_convenience": ["$", Color(0.3, 0.8, 0.35)], "shop_supermarket": ["$", Color(0.3, 0.8, 0.35)],
	"gas_station": ["G", Color(0.95, 0.6, 0.2)], "diner": ["D", Color(0.95, 0.55, 0.35)],
	"shop_weapons": ["W", Color(0.85, 0.25, 0.25)], "shop_clothing": ["K", Color(0.7, 0.45, 0.9)],
	"mechanic": ["M", Color(0.95, 0.85, 0.3)], "car_dealer": ["A", Color(0.3, 0.7, 0.95)],
	"hospital": ["+", Color(0.95, 0.95, 0.95)], "police_station": ["P", Color(0.3, 0.45, 1.0)],
	"safehouse": ["H", Color(0.35, 0.9, 0.5)], "bank": ["B", Color(0.3, 0.75, 0.6)],
	"jewelry": ["J", Color(0.75, 0.85, 1.0)], "electronics": ["E", Color(0.5, 0.8, 0.9)],
	"parking_garage": ["G", Color(0.6, 0.6, 0.7)], "airport_terminal": ["F", Color(0.8, 0.8, 0.9)],
}
const JEWELRY := [["Goldkette", 8500], ["Diamantring", 24000], ["Luxusuhr", 65000], ["Diamant-Collier", 180000]]
const FOOD := [["Snack & Limo", 6, 20.0], ["Sandwich", 14, 45.0], ["Brot & Aufschnitt", 9, 35.0]]
const MEALS := [["Burger & Pommes", 18, 60.0], ["Pancakes mit Sirup", 12, 40.0], ["Großes Menü", 28, 100.0],
	["Steak mit Beilagen", 35, 100.0]]
const GADGETS := [["Smartphone Pro", 1400, "Das neueste Modell."], ["Noise-Cancelling-Kopfhörer", 450, "Endlich Ruhe."],
	["Gaming-Konsole", 650, "Für die Abende im Safehouse."], ["OLED-Fernseher 85\"", 4800, "Heimkino deluxe."],
	["Drohne", 2200, "Luftaufnahmen von Port Aurelia."]]
const INCOME := {"property_business": 45000, "property_garage": 1500, "property_apartment": 2500, "property_house": 4500,
	"property_penthouse": 14000, "property_villa": 30000}
## Real estate is a serious investment.
const PROPERTY_PRICE := {"property_business": 3200000, "property_garage": 180000, "property_apartment": 280000,
	"property_house": 520000, "property_penthouse": 1900000, "property_villa": 4800000}
const CLOTH_COLORS := [["Weiß", Color(0.95, 0.95, 0.93)], ["Schwarz", Color(0.08, 0.08, 0.1)], ["Rot", Color(0.75, 0.1, 0.1)],
	["Blau", Color(0.15, 0.3, 0.65)], ["Grün", Color(0.2, 0.45, 0.25)], ["Gelb", Color(0.9, 0.75, 0.15)],
	["Grau", Color(0.5, 0.5, 0.53)], ["Beige", Color(0.8, 0.7, 0.55)], ["Pink", Color(0.9, 0.5, 0.65)]]
const CLOTHES := {
	"top": [["TShirt", "T-Shirt", 45], ["LongSleeve", "Langarmshirt", 70], ["Jacket", "Jacke", 180], ["Suit", "Anzug", 650]],
	"bottom": [["Jeans", "Jeans", 80], ["Shorts", "Shorts", 40], ["Skirt", "Rock", 60]],
	"shoes": [["Sneakers", "Sneaker", 90], ["Boots", "Stiefel", 140]],
	"hat": [["", "Kein Hut", 0], ["Cap", "Cap", 30]],
}

var world: GameWorld
var markers := {}              # poi id -> InteractMarker
var _vehicle_markers: Array = []
var _bus_markers := {}         # prop key -> InteractMarker
var _bus_timer := 0.0
var _last_income_day := -1
var rng := RandomNumberGenerator.new()
var _pending_parked := {}      # owned vehicle id -> [x, y, z, yaw] waiting to be respawned
var _parked_timer := 0.0


func _ready() -> void:
	name = "Economy"
	world = GameWorld.instance
	world.economy = self
	rng.randomize()
	for p in world.data.pois:
		_create_marker(p)
	Events.time_changed.connect(_on_time)
	_pending_parked = (Game.player_data.world_state.get("vehicles_parked", {}) as Dictionary).duplicate()


# ------------------------------------------------------------------ markers
func _entrance(p: Dictionary, out := 1.6) -> Vector3:
	return (p["entrance_v"] as Vector3) + (p["facing_v"] as Vector3) * out


func _create_marker(p: Dictionary) -> void:
	var t: String = p["type"]
	var handler := Callable()
	var prompt := "E: " + String(p["name"])
	var col := Color(1.0, 0.8, 0.2)
	match t:
		"shop_convenience", "shop_supermarket", "gas_station", "diner", "electronics", "jewelry":
			handler = _enter_or.bind(p, _open_store)
			prompt = "E: %s betreten" % p["name"]
		"shop_weapons":
			handler = _enter_or.bind(p, _open_weapons)
			prompt = "E: %s betreten" % p["name"]
			col = Color(0.95, 0.3, 0.3)
		"shop_clothing":
			handler = _enter_or.bind(p, _open_clothing)
			prompt = "E: %s betreten" % p["name"]
			col = Color(0.75, 0.5, 0.95)
		"car_dealer":
			handler = _open_dealer.bind(p)
			col = Color(0.35, 0.7, 1.0)
		"hospital":
			handler = _open_hospital.bind(p)
			col = Color(0.95, 0.95, 0.95)
		"safehouse", "parking_garage":
			handler = _open_safehouse.bind(p)
			col = Color(0.35, 0.95, 0.5)
		"mechanic":
			_create_vehicle_marker(p)
			return
		_:
			if t.begins_with("property_"):
				handler = _open_property.bind(p)
				col = Color(0.35, 0.95, 0.5)
			else:
				return
	var m := InteractMarker.new()
	m.prompt = prompt
	m.color = col
	m.on_interact = handler
	world.add_child(m)
	m.global_position = _entrance(p)
	markers[int(p["id"])] = m


func _create_vehicle_marker(p: Dictionary) -> void:
	var m := InteractMarker.new()
	m.vehicle_marker = true
	m.interact_radius = 4.5
	m.color = Color(0.95, 0.85, 0.3)
	m.prompt = String(p["name"])
	world.add_child(m)
	m.global_position = _entrance(p, 7.0)
	m.set_meta("poi", p)
	m.set_meta("cooldown", 0.0)
	_vehicle_markers.append(m)
	markers[int(p["id"])] = m


func blips() -> Array:
	var out := []
	var pp := world.player.global_position
	for p in world.data.pois:
		var t: String = p["type"]
		var ic = ICONS.get(t)
		if ic == null and t.begins_with("property_"):
			ic = ["H", Color(0.35, 0.9, 0.5)] if _owns(p) else ["€", Color(0.6, 0.9, 0.6)]
		if ic == null:
			continue
		var pos: Vector3 = p["entrance_v"]
		var d := pos.distance_to(pp)
		var important := t == "safehouse" or (t.begins_with("property_") and _owns(p))
		if d < 380.0 or important:
			out.append({"pos": pos, "icon": ic[0], "color": ic[1], "size": 8.0, "edge": important})
	return out


func map_pois() -> Array:
	## all POIs for the full map screen: {pos, icon, color, name}
	var out := []
	for p in world.data.pois:
		var t: String = p["type"]
		var ic = ICONS.get(t)
		if ic == null and t.begins_with("property_"):
			ic = ["H", Color(0.35, 0.9, 0.5)] if _owns(p) else ["€", Color(0.6, 0.9, 0.6)]
		if ic == null:
			ic = ["•", Color(0.8, 0.8, 0.8)]
		out.append({"pos": p["entrance_v"], "icon": ic[0], "color": ic[1], "name": p["name"]})
	return out


# ------------------------------------------------------------------ update
func _process(delta: float) -> void:
	var p := world.player as Player
	if p == null:
		return
	_update_vehicle_markers(p, delta)
	_bus_timer -= delta
	if _bus_timer <= 0.0:
		_bus_timer = 1.5
		_update_bus_markers(p.global_position)
	_parked_timer -= delta
	if _parked_timer <= 0.0 and not _pending_parked.is_empty():
		_parked_timer = 2.0
		_restore_parked(p.global_position)


## Personal vehicles reappear where they were parked once the area is streamed in.
func _restore_parked(pp: Vector3) -> void:
	for id in _pending_parked.keys():
		var a: Array = _pending_parked[id]
		var pos := Vector3(a[0], a[1], a[2])
		if pos.distance_to(pp) > 250.0 or not world.streaming.is_loaded_at(pos):
			continue
		for e in Game.player_data.owned_vehicles:
			if e["id"] == id:
				var v := spawn_owned_vehicle(e, pos, Vector3.ZERO)
				v.global_rotation.y = float(a[3])
				break
		_pending_parked.erase(id)


func _update_vehicle_markers(p: Player, delta: float) -> void:
	for m in _vehicle_markers:
		m.set_meta("cooldown", float(m.get_meta("cooldown")) - delta)
		if not p.is_in_vehicle() or MenuPanel.is_open():
			continue
		var v := p.vehicle as Vehicle
		if v.global_position.distance_to(m.global_position) < m.interact_radius and v.speed() < 2.0 \
				and float(m.get_meta("cooldown")) <= 0.0:
			m.set_meta("cooldown", 6.0)
			_open_mechanic(m.get_meta("poi"), v)


func _on_time(hour: float) -> void:
	# daily property income at 06:00
	var dn := world.day_night
	if dn == null:
		return
	var day: int = dn.get("day")
	if hour >= 6.0 and hour < 6.1 and day != _last_income_day:
		_last_income_day = day
		var total := 0
		for pid in Game.player_data.properties:
			total += int(INCOME.get(_poi_type_by_key(pid), 0))
		if total > 0:
			Game.player_data.add_money(total, "income")
			Events.notify.emit("Einnahmen aus Immobilien: +$%d" % total, 5.0)


func _poi_type_by_key(key: String) -> String:
	for p in world.data.pois:
		if _prop_key(p) == key:
			return p["type"]
	return ""


func _prop_key(p: Dictionary) -> String:
	return "safehouse" if p["type"] == "safehouse" else "%s_%d" % [p["type"], int(p["id"])]


func property_price(p: Dictionary) -> int:
	var base := int(PROPERTY_PRICE.get(p["type"], int(p.get("price", 50000))))
	var k := float(int(p.get("id", 0)) * 7919 % 100) / 100.0
	return int(round(base * lerpf(0.85, 1.25, k) / 1000.0)) * 1000


func _owns(p: Dictionary) -> bool:
	return Game.player_data.properties.has(_prop_key(p))


## Enter the building's interior if one exists, otherwise open the menu directly.
func _enter_or(pl: Player, p: Dictionary, fallback: Callable) -> void:
	var im := InteriorManager.get_manager()
	if im and im.has_interior(p["type"]):
		im.enter(p)
	else:
		fallback.call(pl, p)


# ------------------------------------------------------------------ stores & robbery
## Every kind of shop sells its own things (no more sandwiches at the electronics store).
func _open_store(_pl: Player, p: Dictionary) -> void:
	var t: String = p["type"]
	var items := []
	var pl := world.player as Player
	var greet := "Willkommen! Was darf es sein?"
	match t:
		"jewelry":
			for j in JEWELRY:
				items.append({"label": j[0], "price": j[1], "desc": "Reiner Luxus – gehört danach dir.",
					"action": func(): Game.player_data.stat_add("jewelry_bought", 1); Events.notify.emit("%s gekauft." % j[0], 2.5),
					"keep_open": true})
			greet = "Willkommen bei %s. Nur das Feinste." % p["name"]
		"electronics":
			var has_scanner: bool = Game.player_data.world_state.get("scanner", false)
			items.append({"label": "Polizeiscanner", "price": 18000, "enabled": not has_scanner,
				"desc": "Hört den Polizeifunk ab: Du hängst die Polizei ein Drittel schneller ab." if not has_scanner else "Schon gekauft.",
				"action": func(): Game.player_data.world_state["scanner"] = true; Events.notify.emit("Polizeiscanner gekauft.", 2.5)})
			for g in GADGETS:
				items.append({"label": g[0], "price": g[1], "desc": g[2], "keep_open": true,
					"action": func(): Game.player_data.stat_add("gadgets_bought", 1); Events.notify.emit("%s gekauft." % g[0], 2.5)})
			greet = "Pixel, Sound und Hightech – was darf's sein?"
		"gas_station":
			items.append(_food_item(FOOD[0]))
			items.append({"label": "Reparaturset", "price": 150, "desc": "Behebt kleinere Schäden am letzten Fahrzeug.",
				"action": _quick_fix})
			items.append({"label": "Reifen-Flickset", "price": 120, "desc": "Repariert platte Reifen am letzten Fahrzeug.",
				"action": _fix_tyres})
			items.append({"label": "Nitro-Kartusche", "price": 3500, "desc": "+1 Nitro-Ladung für dein aktuelles Fahrzeug.",
				"action": _add_nitro_charge})
			greet = "Tanken, Snacks, Autozubehör."
		"diner":
			for f in MEALS:
				items.append(_food_item(f))
			greet = "Setz dich! Die Küche hat offen."
		"shop_supermarket":
			for f in FOOD + MEALS.slice(0, 2):
				items.append(_food_item(f))
			items.append({"label": "Verbandskasten", "price": 90, "enabled": pl.health.health < pl.health.max_health,
				"desc": "Stellt 60 Gesundheit wieder her.", "action": func(): pl.health.heal(60.0), "keep_open": true})
		_:
			for f in FOOD:
				items.append(_food_item(f))
			items.append({"label": "Reparaturset", "price": 150, "desc": "Behebt kleinere Schäden am letzten Fahrzeug.",
				"action": _quick_fix})
	MenuPanel.open(p["name"], items, greet + "   (Tipp: Mit gezogener Waffe auf den Verkäufer zielen = Überfall)")


func _food_item(f: Array) -> Dictionary:
	return {"label": f[0], "price": f[1], "desc": "Stellt %d Gesundheit wieder her." % int(f[2]),
		"action": func(): (world.player as Player).health.heal(f[2]); Events.notify.emit("Guten Appetit!", 2.0),
		"keep_open": true}


func _fix_tyres() -> void:
	var v := _last_vehicle()
	if v:
		v._flat = [false, false, false, false]
		for w in v._wheels:
			if w["node"]:
				(w["node"] as Node3D).scale = Vector3.ONE
		Events.notify.emit("Reifen geflickt.", 2.0)


func _add_nitro_charge() -> void:
	var v := _last_vehicle()
	if v == null:
		Events.notify.emit("Kein Fahrzeug in der Nähe.", 2.0)
		return
	v.nitro = mini(v.nitro + 1, 9)
	v.upgrades["nitro"] = v.nitro
	_save_upgrades(v)
	Events.notify.emit("Nitro: %d Ladungen." % v.nitro, 2.0)


func _quick_fix() -> void:
	var v := _last_vehicle()
	if v:
		v.engine_health = maxf(v.engine_health, 650.0)
		v.body_health = maxf(v.body_health, 650.0)
		Events.notify.emit("Fahrzeug notdürftig repariert.", 2.5)


func _last_vehicle() -> Vehicle:
	var pl := world.player as Player
	if pl.vehicle is Vehicle:
		return pl.vehicle
	var best: Vehicle = null
	var bd := 25.0
	for v in world.traffic.call("vehicles_near", pl.global_position, 25.0):
		var d: float = (v as Node3D).global_position.distance_to(pl.global_position)
		if d < bd and ((v as Vehicle).player_owned or (v as Vehicle).ai_driver == null):
			bd = d
			best = v
	return best


# ------------------------------------------------------------------ weapons
func _open_weapons(_pl: Player, p: Dictionary) -> void:
	MenuPanel.open(p["name"], _weapon_items(), "Waffen, Munition und Schutzwesten.")


func _weapon_items() -> Array:
	var pl := world.player as Player
	var items := []
	for id in WeaponData.WEAPONS:
		if id == "unarmed":
			continue
		var d: Dictionary = WeaponData.WEAPONS[id]
		if not pl.weapons.has_weapon(id):
			items.append({"label": d["name"], "price": int(d["price"]), "desc": _weapon_desc(d),
				"action": func(): pl.weapons.give(id); pl.weapons.equip(id); Events.notify.emit("%s gekauft." % d["name"], 2.0),
				"keep_open": true})
		elif WeaponData.is_ranged(id):
			var amount := int(d["mag"]) * 2
			items.append({"label": "Munition: %s (+%d)" % [d["name"], amount], "price": int(d["ammo_price"]),
				"desc": "Aktuell: %d" % int(pl.weapons.owned[id]["reserve"]),
				"action": func(): pl.weapons.add_ammo(id, amount), "keep_open": true})
	items.append({"label": "Schutzweste", "price": 600, "enabled": pl.health.armor < 100.0,
		"desc": "Absorbiert einen Großteil des Schadens.", "action": func(): pl.health.add_armor(100.0), "keep_open": true})
	return items


func _weapon_desc(d: Dictionary) -> String:
	if d["kind"] == "melee":
		return "Nahkampf · Schaden %d" % int(d["damage"])
	return "Schaden %d · Magazin %d · Reichweite %d m" % [int(d["damage"]) * int(d.get("pellets", 1)), int(d["mag"]), int(d["range"])]


# ------------------------------------------------------------------ clothing
func _open_clothing(_pl: Player, p: Dictionary) -> void:
	var items := []
	var names := {"top": "Oberteile", "bottom": "Hosen & Röcke", "shoes": "Schuhe", "hat": "Kopfbedeckung"}
	for cat in CLOTHES:
		items.append({"label": names[cat], "action": _open_cloth_cat.bind(p, cat)})
	var pl := world.player as Player
	items.append({"label": "Sonnenbrille an/aus", "price": 25, "action": func():
		var o := pl.model.outfit.duplicate()
		o["glasses"] = not o.get("glasses", false)
		_apply_outfit(o), "keep_open": true})
	MenuPanel.open(p["name"], items, "Neue Kleidung wird sofort angezogen.")


func _open_cloth_cat(p: Dictionary, cat: String) -> void:
	var pl := world.player as Player
	var body: String = pl.model.outfit.get("body", "M")
	var items := []
	for c in CLOTHES[cat]:
		if c[0] == "Skirt" and body != "F":
			continue
		if cat == "hat" and c[0] == "":
			items.append({"label": "Kopfbedeckung ablegen", "action": func():
				var o := pl.model.outfit.duplicate()
				o["hat"] = ""
				_apply_outfit(o), "keep_open": true})
			continue
		for col in CLOTH_COLORS:
			items.append({"label": "%s (%s)" % [c[1], col[0]], "price": c[2], "keep_open": true,
				"action": func():
					var o := pl.model.outfit.duplicate()
					o[cat] = c[0]
					o[cat + "_color"] = col[1]
					if cat == "top":
						o["overlay"] = ""
					_apply_outfit(o)
					if not Game.player_data.owned_clothes.has(c[0]):
						Game.player_data.owned_clothes.append(c[0])})
	MenuPanel.open(p["name"], items)


func _apply_outfit(o: Dictionary) -> void:
	var pl := world.player as Player
	pl.model.apply_outfit(o)
	Game.player_data.outfit = o


# ------------------------------------------------------------------ hospital
func _open_hospital(_pl: Player, p: Dictionary) -> void:
	var pl := world.player as Player
	MenuPanel.open(p["name"], [
		{"label": "Behandlung (volle Gesundheit)", "price": 200, "enabled": pl.health.health < pl.health.max_health,
			"action": func(): pl.health.heal(1000.0); Events.notify.emit("Du wurdest behandelt.", 2.0)},
	])


# ------------------------------------------------------------------ mechanic
func _open_mechanic(p: Dictionary, v: Vehicle) -> void:
	var damage := 1.0 - (v.engine_health + v.body_health) / 2000.0
	var repair_cost := maxi(50, int(damage * 1500.0))
	var items := [
		{"label": "Reparieren", "price": repair_cost, "enabled": damage > 0.01 or v._flat.has(true),
			"desc": "Motor, Karosserie, Scheiben und Reifen.", "action": func(): v.repair(); _notify_fixed()},
		{"label": "Neu lackieren", "price": 250, "desc": "Neue Farbe. Wer gesucht wird und nicht gesehen wurde, ist danach nicht mehr erkennbar.",
			"action": _open_paint.bind(p, v)},
	]
	var names := {"engine": "Motor-Tuning", "brakes": "Bremsen", "armor": "Panzerung"}
	var prices := [1500, 3500, 7000]
	for k in names:
		var lvl: int = v.upgrades[k]
		if lvl < 3:
			items.append({"label": "%s Stufe %d" % [names[k], lvl + 1], "price": prices[lvl], "keep_open": false,
				"desc": "Aktuell Stufe %d von 3." % lvl, "action": func(): v.upgrades[k] = lvl + 1; _save_upgrades(v)})
	if int(v.upgrades["tires"]) == 0:
		items.append({"label": "Kugelsichere Reifen", "price": 4000, "action": func(): v.upgrades["tires"] = 1; _save_upgrades(v)})
	if v.nitro < 9:
		items.append({"label": "Nitro (+3 Ladungen)", "price": 6000, "keep_open": true,
			"desc": "Aktuell %d Ladungen. Zünden mit N (Controller: A) während der Fahrt." % v.nitro,
			"action": func(): v.nitro = mini(v.nitro + 3, 9); v.upgrades["nitro"] = v.nitro; _save_upgrades(v); Events.notify.emit("Nitro eingebaut: %d Ladungen." % v.nitro, 2.5)})
	MenuPanel.open(p["name"], items, VehicleDefs.display_name(v.type_id))


func _notify_fixed() -> void:
	Events.notify.emit("Fahrzeug repariert.", 2.0)


func _open_paint(p: Dictionary, v: Vehicle) -> void:
	var items := []
	for c in [["Weiß", Color(0.92, 0.92, 0.9)], ["Schwarz", Color(0.03, 0.03, 0.035)], ["Silber", Color(0.6, 0.62, 0.64)],
			["Rot", Color(0.7, 0.04, 0.03)], ["Blau", Color(0.05, 0.15, 0.55)], ["Grün", Color(0.05, 0.35, 0.15)],
			["Gelb", Color(0.95, 0.75, 0.05)], ["Orange", Color(0.95, 0.4, 0.05)], ["Violett", Color(0.35, 0.1, 0.5)]]:
		items.append({"label": c[0], "action": func():
			v.set_paint(c[1])
			_save_upgrades(v)
			_respray_wanted()})
	MenuPanel.open(p["name"] + " – Lackierung", items)


func _respray_wanted() -> void:
	var pol := world.police
	if pol and int(pol.get("wanted_level")) > 0:
		if int(pol.get("wanted_level")) <= 3 and not pol.call("_cop_can_see", world.player.global_position + Vector3.UP, 60.0):
			pol.call("clear_wanted")
			Events.notify.emit("Neue Farbe – die Polizei erkennt dich nicht mehr.", 3.0)
		else:
			Events.notify.emit("Die Polizei hat dich gesehen!", 3.0)
	else:
		Events.notify.emit("Neu lackiert.", 2.0)


func _save_upgrades(v: Vehicle) -> void:
	if v.owned_id == "":
		return
	for e in Game.player_data.owned_vehicles:
		if e["id"] == v.owned_id:
			e["upgrades"] = v.upgrades.duplicate()
			e["color"] = v.paint.to_html()


# ------------------------------------------------------------------ car dealer
func _open_dealer(_pl: Player, p: Dictionary) -> void:
	var items := []
	for id in ["compact", "sedan", "pickup", "van", "suv", "motorcycle", "luxury", "sports", "muscle", "supercar", "hypercar"]:
		var d := VehicleDefs.get_def(id)
		var price := int(d.get("price", 20000))
		items.append({"label": VehicleDefs.display_name(id), "price": price,
			"desc": "Höchstgeschwindigkeit ca. %d km/h · Antrieb %s" % [int(float(d["top"]) * 3.6), String(d["drive"]).to_upper()],
			"action": _buy_vehicle.bind(p, id)})
	MenuPanel.open(p["name"], items, "Gekaufte Fahrzeuge stehen danach in deiner Garage bereit.")


func _buy_vehicle(p: Dictionary, id: String) -> void:
	var entry := {"id": "veh_%d" % Time.get_ticks_usec(), "type": id, "color": VehicleDefs.random_color(null, id).to_html(),
		"upgrades": {"engine": 0, "brakes": 0, "armor": 0, "tires": 0}}
	Game.player_data.owned_vehicles.append(entry)
	Events.vehicle_purchased.emit(id)
	var v := spawn_owned_vehicle(entry, _entrance(p, 8.0), p["facing_v"])
	Events.notify.emit("%s gekauft! Er steht vor dem Autohaus." % VehicleDefs.display_name(id), 4.0)
	if v:
		Events.waypoint_set.emit(v.global_position)


func spawn_owned_vehicle(entry: Dictionary, pos: Vector3, facing: Vector3) -> Vehicle:
	var v := Vehicle.create(entry["type"], Color.html(entry["color"]))
	v.player_owned = true
	v.owned_id = entry["id"]
	_pending_parked.erase(entry["id"])
	var side := facing.cross(Vector3.UP).normalized()
	if side.length() < 0.01:
		side = Vector3.FORWARD
	v.transform = Transform3D(Basis.looking_at(side, Vector3.UP), pos + Vector3.UP * 0.6)
	world.add_child(v)
	for k in entry.get("upgrades", {}):
		v.upgrades[k] = int(entry["upgrades"][k])
	if v.upgrades.has("nitro"):
		v.nitro = int(v.upgrades["nitro"])
	if world.traffic:
		(world.traffic as TrafficManager).keep[v] = true
	return v


# ------------------------------------------------------------------ properties & safehouse
func _open_property(pl: Player, p: Dictionary) -> void:
	if _owns(p):
		_open_safehouse(pl, p)
		return
	var price := property_price(p)
	var inc := int(INCOME.get(p["type"], 0))
	MenuPanel.open(p["name"], [
		{"label": "Kaufen", "price": price, "desc": "Speicherpunkt, Garage und täglich $%d Einnahmen." % inc,
			"action": func():
				Game.player_data.properties.append(_prop_key(p))
				Events.property_purchased.emit(_prop_key(p))
				Events.big_message.emit("IMMOBILIE GEKAUFT", p["name"], 3.5)
				AudioManager.play_ui("mission_passed")},
	], "Zu verkaufen.")


func _open_safehouse(_pl: Player, p: Dictionary) -> void:
	var im := InteriorManager.get_manager()
	var items := []
	if im and im.has_interior(p["type"]):
		items.append({"label": "Wohnung betreten", "action": func(): im.enter(p)})
	items.append_array([
		{"label": "Spiel speichern", "action": _open_save_menu},
		{"label": "Schlafen (6 Stunden)", "desc": "Heilt vollständig und speichert automatisch.", "action": _sleep},
		{"label": "Garderobe", "action": _open_wardrobe},
	])
	if not Game.player_data.owned_vehicles.is_empty():
		items.append({"label": "Garage: Fahrzeug holen", "action": _open_garage.bind(p)})
	MenuPanel.open(p["name"], items)


func _open_save_menu() -> void:
	var items := []
	for slot in [0, 1, 2]:
		var info := SaveManager.slot_info(slot)
		var label := "Speicherplatz %d" % (slot + 1)
		var right := "leer" if info.is_empty() else "%s · $%s" % [String(info["district"]), str(info["money"])]
		items.append({"label": label, "right": right, "action": func():
			if SaveManager.save_slot(slot):
				Events.notify.emit("Spiel gespeichert (Platz %d)." % (slot + 1), 3.0)})
	MenuPanel.open("Speichern", items)


func _sleep() -> void:
	var pl := world.player as Player
	if world.police and int(world.police.get("wanted_level")) > 0:
		Events.notify.emit("Du kannst nicht schlafen, während du gesucht wirst.", 3.0)
		return
	await fade_out_in(func():
		if world.day_night:
			world.day_night.call("advance_hours", 6.0)
		pl.health.heal(1000.0))
	SaveManager.autosave()
	Events.notify.emit("Ausgeschlafen. Spiel automatisch gespeichert.", 3.0)


func _open_wardrobe() -> void:
	var pl := world.player as Player
	var items := []
	var owned: Array = Game.player_data.owned_clothes
	for cat in CLOTHES:
		for c in CLOTHES[cat]:
			if c[0] != "" and owned.has(c[0]):
				items.append({"label": c[1], "keep_open": true, "action": func():
					var o := pl.model.outfit.duplicate()
					o[cat] = c[0]
					if cat == "top":
						o["overlay"] = ""
					_apply_outfit(o)})
	MenuPanel.open("Garderobe", items)


func _open_garage(p: Dictionary) -> void:
	var items := []
	for e in Game.player_data.owned_vehicles:
		items.append({"label": VehicleDefs.display_name(e["type"]), "action": func():
			for v in get_tree().get_nodes_in_group("vehicles"):
				if (v as Vehicle).owned_id == e["id"]:
					if (v as Vehicle).driver == null:
						v.queue_free()
					else:
						Events.notify.emit("Dieses Fahrzeug ist bereits unterwegs.", 2.5)
						return
			spawn_owned_vehicle(e, _entrance(p, 6.0), p["facing_v"])
			Events.notify.emit("Dein Fahrzeug steht bereit.", 2.5)})
	MenuPanel.open("Garage", items)


# ------------------------------------------------------------------ public transport (bus)
func _update_bus_markers(pp: Vector3) -> void:
	var c := world.data.chunk_of(pp)
	var wanted := {}
	for dx in range(-1, 2):
		for dz in range(-1, 2):
			var cc := c + Vector2i(dx, dz)
			var lst: Array = world.data.props_by_chunk.get("%d_%d" % [cc.x, cc.y], [])
			for i in lst.size():
				var pr: Array = lst[i]
				if pr[0] != "bus_stop":
					continue
				var pos := Vector3(pr[1], pr[2], pr[3])
				if pos.distance_to(pp) > 70.0:
					continue
				var key := "%d_%d_%d" % [cc.x, cc.y, i]
				wanted[key] = true
				if not _bus_markers.has(key):
					var m := InteractMarker.new()
					m.prompt = "E: Bus nehmen"
					m.color = Color(0.3, 0.6, 1.0)
					m.interact_radius = 2.5
					m.on_interact = func(_p): _open_bus(pos)
					world.add_child(m)
					var b := Basis(Vector3.UP, float(pr[4]))
					m.global_position = pos + b * Vector3(0, 0, -1.2)
					_bus_markers[key] = m
	for k in _bus_markers.keys():
		if not wanted.has(k):
			_bus_markers[k].queue_free()
			_bus_markers.erase(k)


func _open_bus(from: Vector3) -> void:
	var items := []
	var dests := {}
	for p in world.data.pois:
		var t: String = p["type"]
		if t in ["safehouse", "hospital", "landmark_mall", "airport_terminal", "landmark_stadium", "car_dealer",
				"landmark_grand_hotel", "police_station", "shop_weapons", "shop_clothing"] or (t.begins_with("property_") and _owns(p)):
			var key: String = p["name"]
			if dests.has(key):
				continue
			dests[key] = p
	for key in dests:
		var p: Dictionary = dests[key]
		var dist: float = from.distance_to(p["entrance_v"])
		if dist < 150.0:
			continue
		var fare := 10 + int(dist / 100.0) * 2
		items.append({"label": key, "price": fare, "right": "%.1f km" % (dist / 1000.0),
			"action": func(): _travel(p, dist)})
	MenuPanel.open("Stadtbus", items, "Wähle ein Ziel. Der Bus bringt dich in die Nähe.")


func _travel(p: Dictionary, dist: float) -> void:
	var pl := world.player as Player
	if world.police and int(world.police.get("wanted_level")) > 0:
		Events.notify.emit("Mit Fahndungslevel nimmt dich kein Bus mit.", 3.0)
		return
	var dest := _entrance(p, 3.0)
	await fade_out_in(func():
		world.streaming.load_area_blocking(dest, 260.0)
		pl.teleport(dest + Vector3.UP * 0.3)
		if world.day_night:
			world.day_night.call("advance_hours", clampf(dist / 12000.0, 0.15, 0.8)))


## Fade the screen to black, run `mid`, fade back.
func fade_out_in(mid: Callable) -> void:
	var layer := CanvasLayer.new()
	layer.layer = 50
	var r := ColorRect.new()
	r.color = Color(0, 0, 0, 0)
	r.set_anchors_preset(Control.PRESET_FULL_RECT)
	r.mouse_filter = Control.MOUSE_FILTER_IGNORE
	layer.add_child(r)
	add_child(layer)
	var tw := create_tween()
	tw.tween_property(r, "color:a", 1.0, 0.5)
	await tw.finished
	mid.call()
	await get_tree().create_timer(0.4).timeout
	var tw2 := create_tween()
	tw2.tween_property(r, "color:a", 0.0, 0.6)
	await tw2.finished
	layer.queue_free()
