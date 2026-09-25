class_name PlayerData
extends RefCounted
## Persistent player progression (money, outfit, possessions, stats, mission progress).
## Serialised by SaveManager.

var money := 2500
var outfit := Player.default_outfit()
var owned_clothes: Array = ["TShirt", "Jeans", "Sneakers", "Short"]
var owned_vehicles: Array = []          # Array[Dictionary] {id, type, color, upgrades, damage, garage}
var properties: Array = ["safehouse"]    # property ids (poi types)
var weapons := {}                        # WeaponHolder.serialize()
var health := 100.0
var armor := 0.0
var position := Vector3.ZERO
var yaw := 0.0
var hour := 9.0
var weather := "sunny"
var missions := {}                       # mission id -> "done" / progress dict
var mission_current := ""
var collectibles: Array = []
var stats := {"kills": 0, "deaths": 0, "arrests": 0, "vehicles_stolen": 0, "distance_driven": 0.0,
	"distance_walked": 0.0, "shots_fired": 0, "money_earned": 0, "missions_passed": 0, "play_time": 0.0,
	"max_wanted": 0}
var unlocked_zones: Array = ["downtown", "financial", "residential", "shopping"]
var world_state := {}                    # arbitrary flags (e.g. destroyed/opened things)


func add_money(delta: int, reason := "") -> bool:
	if delta < 0 and money + delta < 0:
		return false
	money += delta
	if delta > 0:
		stats["money_earned"] = int(stats["money_earned"]) + delta
	Events.money_changed.emit(money, delta)
	return true


func can_afford(price: int) -> bool:
	return money >= price


func stat_add(key: String, v) -> void:
	stats[key] = stats.get(key, 0) + v
	Events.stat_changed.emit(key, stats[key])


func to_dict() -> Dictionary:
	var o := {}
	for k in outfit:
		var v = outfit[k]
		o[k] = v.to_html() if v is Color else v
	return {"money": money, "outfit": o, "owned_clothes": owned_clothes, "owned_vehicles": owned_vehicles,
		"properties": properties, "weapons": weapons, "health": health, "armor": armor,
		"position": [position.x, position.y, position.z], "yaw": yaw, "hour": hour, "weather": weather,
		"missions": missions, "mission_current": mission_current, "collectibles": collectibles, "stats": stats,
		"unlocked_zones": unlocked_zones, "world_state": world_state}


static func from_dict(d: Dictionary) -> PlayerData:
	var p := PlayerData.new()
	p.money = int(d.get("money", 2500))
	var o: Dictionary = d.get("outfit", {})
	for k in o:
		var v = o[k]
		p.outfit[k] = Color.html(v) if (v is String and k.ends_with("color") or k == "skin") else v
	p.owned_clothes = d.get("owned_clothes", p.owned_clothes)
	p.owned_vehicles = d.get("owned_vehicles", [])
	p.properties = d.get("properties", ["safehouse"])
	p.weapons = d.get("weapons", {})
	p.health = float(d.get("health", 100.0))
	p.armor = float(d.get("armor", 0.0))
	var pos: Array = d.get("position", [0, 0, 0])
	p.position = Vector3(pos[0], pos[1], pos[2])
	p.yaw = float(d.get("yaw", 0.0))
	p.hour = float(d.get("hour", 9.0))
	p.weather = d.get("weather", "sunny")
	p.missions = d.get("missions", {})
	p.mission_current = d.get("mission_current", "")
	p.collectibles = d.get("collectibles", [])
	for k in d.get("stats", {}):
		p.stats[k] = d["stats"][k]
	p.unlocked_zones = d.get("unlocked_zones", p.unlocked_zones)
	p.world_state = d.get("world_state", {})
	return p
