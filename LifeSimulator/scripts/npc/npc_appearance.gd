class_name NpcAppearance
extends RefCounted
## Zufaellige, plausible Aussehenswerte nach dem Humanoid-Vertrag
## (docs/ARCHITECTURE.md, Abschnitt Humanoid).

const SKIN := [Color(0.98, 0.87, 0.78), Color(0.95, 0.80, 0.68), Color(0.89, 0.71, 0.56), Color(0.80, 0.61, 0.45),
	Color(0.68, 0.49, 0.34), Color(0.55, 0.38, 0.26), Color(0.42, 0.29, 0.20), Color(0.33, 0.22, 0.15)]
const HAIR := [Color(0.08, 0.07, 0.06), Color(0.20, 0.13, 0.08), Color(0.35, 0.22, 0.12), Color(0.52, 0.36, 0.20),
	Color(0.80, 0.66, 0.40), Color(0.92, 0.82, 0.58), Color(0.55, 0.22, 0.10), Color(0.70, 0.30, 0.15)]
const EYES := [Color(0.35, 0.22, 0.12), Color(0.25, 0.15, 0.08), Color(0.30, 0.50, 0.75), Color(0.35, 0.55, 0.35),
	Color(0.55, 0.45, 0.25), Color(0.45, 0.50, 0.55)]
const CLOTH := [Color(0.12, 0.13, 0.16), Color(0.85, 0.85, 0.85), Color(0.20, 0.30, 0.55), Color(0.55, 0.15, 0.15),
	Color(0.25, 0.40, 0.25), Color(0.50, 0.45, 0.38), Color(0.80, 0.65, 0.25), Color(0.35, 0.20, 0.45),
	Color(0.15, 0.45, 0.50), Color(0.65, 0.35, 0.20), Color(0.92, 0.92, 0.88), Color(0.40, 0.40, 0.42)]
const ACCESSORIES := ["glasses", "sunglasses", "cap", "hat", "earrings", "watch", "scarf", "necklace", "headphones", "backpack"]


static func _pick(rng: RandomNumberGenerator, arr: Array):
	return arr[rng.randi() % arr.size()]


static func _vary(rng: RandomNumberGenerator, c: Color, amt: float) -> Color:
	return Color(clampf(c.r + rng.randf_range(-amt, amt), 0.0, 1.0), clampf(c.g + rng.randf_range(-amt, amt), 0.0, 1.0),
		clampf(c.b + rng.randf_range(-amt, amt), 0.0, 1.0))


## wealth 0..1 beeinflusst Kleidung (Stil und Farbe), age graue Haare.
static func generate(rng: RandomNumberGenerator, body: String, age: float, wealth := 0.5, skin_hint := -1) -> Dictionary:
	var si: int = skin_hint if skin_hint >= 0 else rng.randi() % SKIN.size()
	var skin := _vary(rng, SKIN[clampi(si, 0, SKIN.size() - 1)], 0.025)
	var hair: Color = _pick(rng, HAIR)
	if si >= 4 and rng.randf() < 0.85:
		hair = HAIR[rng.randi() % 2]
	var grey := clampf((age - 45.0) / 30.0, 0.0, 1.0) * rng.randf_range(0.5, 1.0)
	hair = hair.lerp(Color(0.82, 0.82, 0.80), grey)
	var male := body == "male"
	var hair_style := rng.randi_range(0, 5) if male else rng.randi_range(3, 9)
	if male and age > 40.0 and rng.randf() < 0.25:
		hair_style = 0
	var beard := 0
	if male and age >= 17.0:
		beard = rng.randi_range(0, 4) if rng.randf() < 0.45 else 0
	var weight := clampf(rng.randf_range(0.2, 0.6) + clampf((age - 25.0) / 60.0, 0.0, 0.25), 0.0, 1.0)
	var style_max := 3 if wealth < 0.35 else 5
	var acc := []
	if age > 40.0 and rng.randf() < 0.45:
		acc.append("glasses")
	elif rng.randf() < 0.12:
		acc.append("glasses")
	if rng.randf() < 0.15:
		acc.append(_pick(rng, ["cap", "hat", "scarf", "headphones", "backpack"]))
	if not male and rng.randf() < 0.35:
		acc.append("earrings")
	if wealth > 0.6 and rng.randf() < 0.4:
		acc.append("watch")
	return {
		"skin": skin, "hair_style": hair_style, "hair_color": hair, "eye_color": _pick(rng, EYES),
		"face": {"jaw": rng.randf(), "nose": rng.randf(), "brows": rng.randf(), "cheeks": rng.randf()},
		"build": {"weight": weight, "muscle": rng.randf_range(0.15, 0.7) if male else rng.randf_range(0.05, 0.5),
			"height": clampf(rng.randfn(0.55 if male else 0.42, 0.15), 0.0, 1.0)},
		"top": {"style": rng.randi_range(0, style_max), "color": _vary(rng, _pick(rng, CLOTH), 0.05)},
		"bottom": {"style": rng.randi_range(0, style_max), "color": _vary(rng, _pick(rng, CLOTH), 0.04)},
		"shoes": {"style": rng.randi_range(0, 5), "color": _pick(rng, CLOTH)},
		"accessories": acc, "beard": beard,
	}


## Kind erbt Hautton/Haare von den Eltern.
static func child_of(rng: RandomNumberGenerator, body: String, a: Dictionary, b: Dictionary) -> Dictionary:
	var app := generate(rng, body, 0.0)
	if not a.is_empty() and not b.is_empty():
		var t := rng.randf()
		var sa: Color = a.get("skin", app.skin)
		var sb: Color = b.get("skin", app.skin)
		app.skin = sa.lerp(sb, t)
		app.hair_color = a.get("hair_color", app.hair_color) if rng.randf() < 0.5 else b.get("hair_color", app.hair_color)
		app.eye_color = a.get("eye_color", app.eye_color) if rng.randf() < 0.5 else b.get("eye_color", app.eye_color)
	app.beard = 0
	return app
