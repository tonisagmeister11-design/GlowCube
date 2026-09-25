class_name Outfits
extends RefCounted
## Procedural outfit generator for NPC roles. Returns dictionaries for
## CharacterModel.apply_outfit (all clothing meshes come from generate_characters.py).

const SKINS := [Color(0.96, 0.8, 0.69), Color(0.9, 0.72, 0.58), Color(0.82, 0.62, 0.48), Color(0.7, 0.5, 0.36),
	Color(0.56, 0.38, 0.26), Color(0.42, 0.28, 0.19), Color(0.32, 0.21, 0.14)]
const HAIR_COLORS := [Color(0.06, 0.05, 0.04), Color(0.12, 0.08, 0.05), Color(0.25, 0.16, 0.08), Color(0.45, 0.3, 0.14),
	Color(0.7, 0.55, 0.3), Color(0.55, 0.2, 0.08), Color(0.6, 0.6, 0.6)]
const CASUAL := [Color(0.95, 0.95, 0.93), Color(0.1, 0.1, 0.12), Color(0.7, 0.12, 0.12), Color(0.15, 0.3, 0.6),
	Color(0.2, 0.45, 0.25), Color(0.85, 0.7, 0.2), Color(0.55, 0.55, 0.58), Color(0.9, 0.5, 0.6), Color(0.35, 0.2, 0.45),
	Color(0.9, 0.45, 0.15), Color(0.3, 0.65, 0.75)]
const DENIM := [Color(0.18, 0.26, 0.42), Color(0.12, 0.16, 0.26), Color(0.3, 0.38, 0.5), Color(0.1, 0.1, 0.12),
	Color(0.55, 0.48, 0.38)]
const SUIT := [Color(0.1, 0.1, 0.12), Color(0.16, 0.18, 0.24), Color(0.3, 0.3, 0.32), Color(0.35, 0.28, 0.22)]


static func _pick(rng: RandomNumberGenerator, arr: Array):
	return arr[rng.randi() % arr.size()]


static func random(role: String, rng: RandomNumberGenerator) -> Dictionary:
	var f := rng.randf() < 0.5
	if role in ["cop", "worker", "gang"]:
		f = rng.randf() < 0.2
	var b := "F" if f else "M"
	var o := {"body": b, "skin": _pick(rng, SKINS), "hair_color": _pick(rng, HAIR_COLORS)}
	o["hair"] = _pick(rng, ["Long", "Bun", "Curly", "Long"]) if f else _pick(rng, ["Short", "Buzz", "Curly", "Short", "None"])
	o["top"] = "TShirt"
	o["top_color"] = _pick(rng, CASUAL)
	o["bottom"] = "Jeans"
	o["bottom_color"] = _pick(rng, DENIM)
	o["shoes"] = "Sneakers"
	o["shoes_color"] = _pick(rng, [Color(0.92, 0.92, 0.92), Color(0.1, 0.1, 0.1), Color(0.35, 0.25, 0.18)])
	match role:
		"business":
			o["top"] = "Suit"
			o["top_color"] = _pick(rng, SUIT)
			o["bottom"] = "Skirt" if f and rng.randf() < 0.5 else "Jeans"
			o["bottom_color"] = o["top_color"]
			o["shoes"] = "Boots"
			o["shoes_color"] = Color(0.06, 0.05, 0.05)
			if o["hair"] == "None":
				o["hair"] = "Short"
		"worker":
			o["top"] = "LongSleeve"
			o["top_color"] = _pick(rng, [Color(0.3, 0.35, 0.45), Color(0.5, 0.45, 0.35), Color(0.9, 0.9, 0.88)])
			o["overlay"] = "Vest"
			o["overlay_color"] = _pick(rng, [Color(1.0, 0.55, 0.05), Color(0.85, 1.0, 0.1)])
			o["shoes"] = "Boots"
			o["shoes_color"] = Color(0.35, 0.25, 0.15)
			if rng.randf() < 0.6:
				o["hat"] = "Cap"
				o["hat_color"] = _pick(rng, [Color(0.95, 0.95, 0.9), Color(1.0, 0.8, 0.1)])
		"tourist", "beach":
			o["top"] = "TShirt"
			o["bottom"] = "Shorts"
			o["bottom_color"] = _pick(rng, CASUAL + DENIM)
			o["glasses"] = rng.randf() < 0.6
			if rng.randf() < 0.35:
				o["hat"] = "Cap"
				o["hat_color"] = _pick(rng, CASUAL)
		"jogger":
			o["top"] = "TShirt"
			o["top_color"] = _pick(rng, [Color(0.9, 0.2, 0.3), Color(0.1, 0.5, 0.9), Color(0.1, 0.1, 0.1), Color(0.95, 0.95, 0.95)])
			o["bottom"] = "Shorts"
			o["bottom_color"] = Color(0.08, 0.08, 0.1)
			if f and o["hair"] == "Long":
				o["hair"] = "Bun"
		"gang":
			var gang_col: Color = _pick(rng, [Color(0.55, 0.1, 0.55), Color(0.1, 0.45, 0.15), Color(0.8, 0.65, 0.1)])
			o["top"] = _pick(rng, ["TShirt", "LongSleeve", "Jacket"])
			o["top_color"] = gang_col if rng.randf() < 0.7 else Color(0.08, 0.08, 0.08)
			o["bottom_color"] = Color(0.1, 0.1, 0.12)
			if rng.randf() < 0.6:
				o["hat"] = "Cap"
				o["hat_color"] = gang_col
		"cop":
			o["top"] = "LongSleeve"
			o["top_color"] = Color(0.12, 0.16, 0.3)
			o["overlay"] = "Vest"
			o["overlay_color"] = Color(0.08, 0.08, 0.1)
			o["bottom_color"] = Color(0.1, 0.12, 0.2)
			o["shoes"] = "Boots"
			o["shoes_color"] = Color(0.05, 0.05, 0.05)
			o["hat"] = "Police"
			o["hat_color"] = Color(0.1, 0.12, 0.25)
			o["hair"] = "Buzz" if not f else "Bun"
		"medic":
			o["top"] = "LongSleeve"
			o["top_color"] = Color(0.85, 0.9, 0.95)
			o["overlay"] = "Vest"
			o["overlay_color"] = Color(0.8, 0.1, 0.1)
			o["bottom_color"] = Color(0.15, 0.2, 0.35)
		_:
			var r := rng.randf()
			if r < 0.25:
				o["top"] = "LongSleeve"
			elif r < 0.4:
				o["top"] = "Jacket"
			if f and rng.randf() < 0.3:
				o["bottom"] = "Skirt"
				o["bottom_color"] = _pick(rng, CASUAL)
			elif rng.randf() < 0.15:
				o["bottom"] = "Shorts"
			o["glasses"] = rng.randf() < 0.1
			if rng.randf() < 0.1:
				o["hat"] = "Cap"
				o["hat_color"] = _pick(rng, CASUAL)
	return o
