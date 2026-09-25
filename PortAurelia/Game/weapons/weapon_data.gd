class_name WeaponData
extends RefCounted
## Weapon definitions. Adding a weapon = one entry here (+ a model in generate_weapons.py).

const WEAPONS := {
	"unarmed": {"name": "Fäuste", "slot": 1, "kind": "melee", "damage": 12.0, "rate": 0.45, "range": 1.4,
		"model": "", "anim": "punch", "price": 0},
	"bat": {"name": "Baseballschläger", "slot": 2, "kind": "melee", "damage": 30.0, "rate": 0.7, "range": 1.9,
		"model": "bat", "anim": "melee_swing", "price": 250},
	"knife": {"name": "Messer", "slot": 2, "kind": "melee", "damage": 38.0, "rate": 0.5, "range": 1.5,
		"model": "knife", "anim": "melee_swing", "price": 400},
	"pistol": {"name": "Pistole P9", "slot": 3, "kind": "pistol", "damage": 24.0, "rate": 0.2, "auto": false,
		"mag": 15, "reserve": 150, "reload": 1.2, "spread": 1.6, "aim_spread": 0.5, "recoil": 1.6, "range": 120.0,
		"model": "pistol", "anim": "aim_pistol", "sound": "pistol", "price": 1200, "ammo_price": 60},
	"revolver": {"name": "Revolver .44", "slot": 3, "kind": "pistol", "damage": 55.0, "rate": 0.55, "auto": false,
		"mag": 6, "reserve": 60, "reload": 1.9, "spread": 2.0, "aim_spread": 0.35, "recoil": 4.0, "range": 140.0,
		"model": "revolver", "anim": "aim_pistol", "sound": "revolver", "price": 2600, "ammo_price": 90},
	"smg": {"name": "MP-Vector", "slot": 4, "kind": "smg", "damage": 16.0, "rate": 0.075, "auto": true,
		"mag": 30, "reserve": 300, "reload": 1.6, "spread": 3.2, "aim_spread": 1.4, "recoil": 0.9, "range": 90.0,
		"model": "smg", "anim": "aim_rifle", "sound": "smg", "price": 3500, "ammo_price": 90},
	"shotgun": {"name": "Pump-Schrotflinte", "slot": 5, "kind": "shotgun", "damage": 11.0, "pellets": 8, "rate": 0.85,
		"auto": false, "mag": 7, "reserve": 56, "reload": 2.6, "spread": 5.5, "aim_spread": 4.2, "recoil": 6.0,
		"range": 45.0, "model": "shotgun", "anim": "aim_rifle", "sound": "shotgun", "price": 4200, "ammo_price": 80},
	"rifle": {"name": "Sturmgewehr AR-7", "slot": 6, "kind": "rifle", "damage": 28.0, "rate": 0.1, "auto": true,
		"mag": 30, "reserve": 270, "reload": 1.9, "spread": 2.4, "aim_spread": 0.7, "recoil": 1.3, "range": 220.0,
		"model": "rifle", "anim": "aim_rifle", "sound": "rifle", "price": 7500, "ammo_price": 120},
	"sniper": {"name": "Präzisionsgewehr LR-9", "slot": 7, "kind": "sniper", "damage": 120.0, "rate": 1.3,
		"auto": false, "mag": 5, "reserve": 40, "reload": 2.8, "spread": 5.0, "aim_spread": 0.05, "recoil": 8.0,
		"range": 600.0, "model": "sniper", "anim": "aim_rifle", "sound": "sniper", "price": 12000, "ammo_price": 150,
		"scope": true},
}


static func get_def(id: String) -> Dictionary:
	return WEAPONS.get(id, WEAPONS["unarmed"])


static func is_ranged(id: String) -> bool:
	return get_def(id)["kind"] != "melee"
