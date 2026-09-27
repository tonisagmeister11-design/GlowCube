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
	"machete": {"name": "Machete", "slot": 2, "kind": "melee", "damage": 48.0, "rate": 0.6, "range": 1.8,
		"model": "machete", "anim": "melee_swing", "price": 900},
	"deagle": {"name": "Magnum .50", "slot": 3, "kind": "pistol", "damage": 72.0, "rate": 0.45, "auto": false,
		"mag": 7, "reserve": 70, "reload": 1.6, "spread": 2.2, "aim_spread": 0.3, "recoil": 5.5, "range": 150.0,
		"model": "deagle", "anim": "aim_pistol", "sound": "deagle", "price": 6800, "ammo_price": 140},
	"lmg": {"name": "Maschinengewehr MG-240", "slot": 6, "kind": "rifle", "damage": 30.0, "rate": 0.085, "auto": true,
		"mag": 100, "reserve": 500, "reload": 3.6, "spread": 3.0, "aim_spread": 1.2, "recoil": 1.1, "range": 240.0,
		"model": "lmg", "anim": "aim_rifle", "sound": "lmg", "price": 24000, "ammo_price": 400},
	"grenade_launcher": {"name": "Granatwerfer", "slot": 8, "kind": "launcher", "damage": 420.0, "rate": 0.8,
		"auto": false, "mag": 6, "reserve": 24, "reload": 3.0, "spread": 1.0, "aim_spread": 0.4, "recoil": 4.0,
		"range": 120.0, "model": "grenade_launcher", "anim": "aim_rifle", "sound": "grenade_launch", "price": 45000,
		"ammo_price": 1500, "projectile": "grenade", "blast": 6.0},
	"rpg": {"name": "Raketenwerfer RPG", "slot": 8, "kind": "launcher", "damage": 900.0, "rate": 1.6, "auto": false,
		"mag": 1, "reserve": 12, "reload": 2.2, "spread": 0.6, "aim_spread": 0.1, "recoil": 7.0, "range": 400.0,
		"model": "rpg", "anim": "aim_rifle", "sound": "rocket_launch", "price": 85000, "ammo_price": 2500,
		"projectile": "rocket", "blast": 7.5, "infinite": true},
	"minigun": {"name": "Minigun", "slot": 9, "kind": "heavy", "damage": 26.0, "rate": 0.045, "auto": true,
		"mag": 500, "reserve": 1500, "reload": 4.5, "spread": 3.6, "aim_spread": 2.4, "recoil": 0.5, "range": 180.0,
		"model": "minigun", "anim": "aim_rifle", "sound": "minigun", "price": 150000, "ammo_price": 3000,
		"spinup": 0.45},
}


## How each weapon aims: camera arm/height/shoulder offset, extra zoom (FOV degrees) and
## the reticle drawn by the HUD. The sniper keeps its first-person scope.
const AIM := {
	"pistol": {"arm": 1.55, "height": 1.6, "side": 0.55, "zoom": 12.0, "reticle": "dot"},
	"revolver": {"arm": 1.45, "height": 1.6, "side": 0.55, "zoom": 15.0, "reticle": "dot"},
	"deagle": {"arm": 1.4, "height": 1.6, "side": 0.55, "zoom": 16.0, "reticle": "dot"},
	"smg": {"arm": 1.5, "height": 1.58, "side": 0.6, "zoom": 12.0, "reticle": "cross"},
	"shotgun": {"arm": 1.7, "height": 1.58, "side": 0.6, "zoom": 6.0, "reticle": "circle"},
	"rifle": {"arm": 1.3, "height": 1.62, "side": 0.55, "zoom": 20.0, "reticle": "cross"},
	"lmg": {"arm": 1.6, "height": 1.6, "side": 0.62, "zoom": 14.0, "reticle": "cross"},
	"sniper": {"arm": 1.3, "height": 1.62, "side": 0.55, "zoom": 20.0, "reticle": "scope"},
	"rpg": {"arm": 2.1, "height": 1.95, "side": 0.8, "zoom": 22.0, "reticle": "rocket"},
	"grenade_launcher": {"arm": 2.0, "height": 1.9, "side": 0.75, "zoom": 10.0, "reticle": "arc"},
	"minigun": {"arm": 2.4, "height": 1.75, "side": 0.8, "zoom": 4.0, "reticle": "heavy"},
}


static func aim_profile(id: String) -> Dictionary:
	return AIM.get(id, AIM["pistol"])


static func get_def(id: String) -> Dictionary:
	return WEAPONS.get(id, WEAPONS["unarmed"])


static func is_ranged(id: String) -> bool:
	return get_def(id)["kind"] != "melee"
