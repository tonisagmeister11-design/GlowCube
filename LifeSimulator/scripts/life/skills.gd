class_name Skills
extends RefCounted
## Faehigkeiten (0..100) von Spieler und NPCs. Steigen durch Arbeit,
## Ausbildung, Kurse und Uebung; Lernen wird mit steigendem Wert langsamer.

const SKILLS := {
	"cooking": "Kochen", "driving": "Fahren", "fitness": "Fitness", "charisma": "Ausstrahlung",
	"logic": "Logik", "handiwork": "Handwerk", "medicine": "Medizin", "law": "Recht",
	"programming": "Programmieren", "business": "Wirtschaft", "teaching": "Pädagogik",
	"writing": "Schreiben", "creativity": "Kreativität",
}


static func empty() -> Dictionary:
	var d := {}
	for k in SKILLS:
		d[k] = 0.0
	return d


static func label(skill: String) -> String:
	return SKILLS.get(skill, skill)


## Lernzuwachs mit abnehmendem Ertrag: amount Punkte bei 0, ein Viertel bei 100.
static func gain(skills: Dictionary, skill: String, amount: float) -> float:
	var v: float = float(skills.get(skill, 0.0))
	var inc := amount * (1.0 - 0.75 * v / 100.0)
	skills[skill] = clampf(v + inc, 0.0, 100.0)
	return float(skills[skill])


## Erfuellt skills die Mindestwerte req? Rueckgabe: Liste fehlender Faehigkeiten.
static func missing(skills: Dictionary, req: Dictionary) -> Array:
	var out := []
	for k in req:
		if float(skills.get(k, 0.0)) + 0.001 < float(req[k]):
			out.append(k)
	return out
