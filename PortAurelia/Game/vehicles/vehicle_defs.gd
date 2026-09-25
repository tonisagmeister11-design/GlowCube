class_name VehicleDefs
extends RefCounted
## Handling and gameplay data per vehicle type. Visuals come from
## res://assets/generated/vehicles/<id>.glb + vehicles_meta.json.

const DEFS := {
	"compact":   {"mass": 1050.0, "power": 75000.0, "top": 46.0, "grip": 1.05, "drive": "fwd", "steer": 36.0,
		"spring": 36000.0, "damp": 3200.0, "travel": 0.2, "brake": 9000.0, "sound": "engine_loop", "pitch": 1.25,
		"price": 12000, "category": "compact", "cam": 5.6},
	"sedan":     {"mass": 1450.0, "power": 110000.0, "top": 52.0, "grip": 1.05, "drive": "rwd", "steer": 34.0,
		"spring": 42000.0, "damp": 3800.0, "travel": 0.2, "brake": 11000.0, "sound": "engine_loop", "pitch": 1.0,
		"price": 22000, "category": "sedan", "cam": 6.0},
	"luxury":    {"mass": 1800.0, "power": 170000.0, "top": 58.0, "grip": 1.08, "drive": "rwd", "steer": 32.0,
		"spring": 45000.0, "damp": 4600.0, "travel": 0.22, "brake": 13500.0, "sound": "engine_loop", "pitch": 0.85,
		"price": 68000, "category": "luxury", "cam": 6.4},
	"sports":    {"mass": 1350.0, "power": 240000.0, "top": 68.0, "grip": 1.25, "drive": "rwd", "steer": 32.0,
		"spring": 60000.0, "damp": 5200.0, "travel": 0.15, "brake": 15000.0, "sound": "engine_sport_loop", "pitch": 1.1,
		"price": 95000, "category": "sports", "cam": 5.8, "downforce": 1.2},
	"supercar":  {"mass": 1400.0, "power": 380000.0, "top": 88.0, "grip": 1.4, "drive": "awd", "steer": 30.0,
		"spring": 70000.0, "damp": 6000.0, "travel": 0.13, "brake": 18000.0, "sound": "engine_sport_loop",
		"pitch": 1.25, "price": 320000, "category": "super", "cam": 5.8, "downforce": 2.2},
	"suv":       {"mass": 2100.0, "power": 170000.0, "top": 50.0, "grip": 1.0, "drive": "awd", "steer": 34.0,
		"spring": 50000.0, "damp": 5000.0, "travel": 0.26, "brake": 15000.0, "sound": "engine_loop", "pitch": 0.9,
		"price": 45000, "category": "suv", "cam": 6.8},
	"pickup":    {"mass": 2200.0, "power": 180000.0, "top": 48.0, "grip": 0.98, "drive": "rwd", "steer": 34.0,
		"spring": 52000.0, "damp": 5200.0, "travel": 0.28, "brake": 15000.0, "sound": "engine_diesel_loop",
		"pitch": 1.1, "price": 36000, "category": "pickup", "cam": 7.0},
	"van":       {"mass": 2400.0, "power": 130000.0, "top": 40.0, "grip": 0.95, "drive": "rwd", "steer": 34.0,
		"spring": 55000.0, "damp": 5600.0, "travel": 0.24, "brake": 16000.0, "sound": "engine_diesel_loop",
		"pitch": 1.2, "price": 30000, "category": "van", "cam": 7.2},
	"delivery":  {"mass": 2500.0, "power": 130000.0, "top": 38.0, "grip": 0.95, "drive": "rwd", "steer": 34.0,
		"spring": 55000.0, "damp": 5600.0, "travel": 0.24, "brake": 16000.0, "sound": "engine_diesel_loop",
		"pitch": 1.2, "price": 30000, "category": "van", "cam": 7.2},
	"truck":     {"mass": 7000.0, "power": 280000.0, "top": 32.0, "grip": 0.9, "drive": "rwd", "steer": 32.0,
		"spring": 160000.0, "damp": 16000.0, "travel": 0.25, "brake": 45000.0, "sound": "engine_diesel_loop",
		"pitch": 0.8, "price": 60000, "category": "truck", "cam": 10.0},
	"fire_truck": {"mass": 9000.0, "power": 360000.0, "top": 34.0, "grip": 0.9, "drive": "rwd", "steer": 32.0,
		"spring": 200000.0, "damp": 20000.0, "travel": 0.25, "brake": 60000.0, "sound": "engine_diesel_loop",
		"pitch": 0.75, "price": 0, "category": "emergency", "cam": 11.0, "siren": true},
	"bus":       {"mass": 11000.0, "power": 330000.0, "top": 26.0, "grip": 0.9, "drive": "rwd", "steer": 36.0,
		"spring": 240000.0, "damp": 24000.0, "travel": 0.22, "brake": 70000.0, "sound": "engine_diesel_loop",
		"pitch": 0.7, "price": 0, "category": "bus", "cam": 13.0},
	"taxi":      {"mass": 1450.0, "power": 115000.0, "top": 52.0, "grip": 1.05, "drive": "rwd", "steer": 34.0,
		"spring": 42000.0, "damp": 3800.0, "travel": 0.2, "brake": 11000.0, "sound": "engine_loop", "pitch": 1.0,
		"price": 0, "category": "service", "cam": 6.0},
	"police":    {"mass": 1650.0, "power": 230000.0, "top": 64.0, "grip": 1.18, "drive": "rwd", "steer": 34.0,
		"spring": 50000.0, "damp": 4800.0, "travel": 0.2, "brake": 15000.0, "sound": "engine_sport_loop",
		"pitch": 0.9, "price": 0, "category": "emergency", "cam": 6.2, "siren": true},
	"ambulance": {"mass": 2800.0, "power": 170000.0, "top": 44.0, "grip": 0.95, "drive": "rwd", "steer": 34.0,
		"spring": 60000.0, "damp": 6000.0, "travel": 0.24, "brake": 18000.0, "sound": "engine_diesel_loop",
		"pitch": 1.1, "price": 0, "category": "emergency", "cam": 7.4, "siren": true},
	"motorcycle": {"mass": 230.0, "power": 45000.0, "top": 62.0, "grip": 1.15, "drive": "rwd", "steer": 30.0,
		"spring": 16000.0, "damp": 1400.0, "travel": 0.14, "brake": 3200.0, "sound": "engine_bike_loop",
		"pitch": 1.0, "price": 14000, "category": "bike", "cam": 4.8, "bike": true},
}

const CIVIL_COLORS := [Color(0.62, 0.05, 0.05), Color(0.08, 0.09, 0.1), Color(0.9, 0.9, 0.9), Color(0.55, 0.57, 0.6),
	Color(0.12, 0.2, 0.45), Color(0.3, 0.32, 0.34), Color(0.15, 0.3, 0.2), Color(0.85, 0.72, 0.5),
	Color(0.95, 0.75, 0.1), Color(0.35, 0.12, 0.35), Color(0.05, 0.35, 0.55), Color(0.72, 0.36, 0.08)]

static var _meta := {}


## Fictional make/model names shown in the HUD, garage and dealer.
const NAMES := {
	"compact": "Vireo Pico", "sedan": "Aster Linea", "luxury": "Marquis Regent", "sports": "Falco GT",
	"supercar": "Stratos Vento", "suv": "Brava Ridge", "pickup": "Hauler 1500", "van": "Porter Cargo",
	"truck": "Titan Box", "bus": "Metro Liner", "taxi": "Aster Cab", "police": "Interceptor PD",
	"ambulance": "Medic Unit", "fire_truck": "Blaze Engine", "delivery": "Parcel Runner", "motorcycle": "Nitro 600",
}


static func display_name(id: String) -> String:
	return NAMES.get(id, id.capitalize())


static func get_def(id: String) -> Dictionary:
	return DEFS.get(id, DEFS["sedan"])


static func meta(id: String) -> Dictionary:
	if _meta.is_empty():
		var m = WorldData._read_json("res://assets/generated/vehicles/vehicles_meta.json")
		if m:
			_meta = m
	return _meta.get(id, {})


static func random_color(rng: RandomNumberGenerator = null) -> Color:
	var i := (rng.randi() if rng else randi()) % CIVIL_COLORS.size()
	return CIVIL_COLORS[i]
