class_name Needs
extends RefCounted
## Beduerfnisse (0..100, 100 = voll erfuellt) und ihre Veraenderung pro
## Spielstunde. Bei Tempo 1x entspricht eine Spielstunde etwa einer echten
## Minute. Die Raten sind so gewaehlt, dass ein voller Vorrat rund zwei
## Spieltage haelt: Man isst etwa einmal pro echter Stunde und schlaeft
## nachts, statt staendig Knoepfe druecken zu muessen.

const KEYS := ["hunger", "thirst", "energy", "hygiene", "social", "fun", "health"]
const LABELS := {"hunger": "Hunger", "thirst": "Durst", "energy": "Energie", "hygiene": "Hygiene",
	"social": "Soziales", "fun": "Spaß", "health": "Gesundheit"}

## Verlust pro Stunde im Wachzustand / im Schlaf (negativ = Erholung).
const AWAKE := {"hunger": 2.0, "thirst": 2.4, "energy": 2.1, "hygiene": 1.1, "social": 0.7, "fun": 0.9}
const ASLEEP := {"hunger": 0.7, "thirst": 0.8, "energy": -12.5, "hygiene": 0.35, "social": 0.1, "fun": 0.15}
## Zusatzwirkung von Aktivitaeten pro Stunde (positiv = Erfuellung).
const ACTIVITY := {
	"work": {"energy": -1.5, "social": 2.0, "fun": -1.0},
	"school": {"energy": -1.0, "social": 3.0, "fun": -0.5},
	"study": {"energy": -1.5, "fun": -1.0},
	"leisure": {"fun": 8.0, "social": 5.0},
	"rest": {"energy": 6.0, "fun": 2.0},
	"shop": {"fun": 1.0},
}


static func full() -> Dictionary:
	return {"hunger": 90.0, "thirst": 90.0, "energy": 95.0, "hygiene": 90.0, "social": 75.0, "fun": 75.0, "health": 100.0}


## Deutscher Hinweis, was gegen ein knappes Beduerfnis hilft.
const ADVICE := {
	"hunger": "Du hast Hunger. Kaufe etwas zu essen oder iss zu Hause.",
	"thirst": "Du hast Durst. Trinke zu Hause Wasser oder kaufe ein Getränk.",
	"energy": "Du bist müde. Schlafe zu Hause oder ruhe dich aus.",
	"hygiene": "Du solltest duschen – zu Hause, im Fitnessstudio oder in der Notunterkunft.",
	"social": "Dir fehlen Kontakte. Sprich jemanden an oder triff dich mit Freunden.",
	"fun": "Du langweilst dich. Geh ins Kino, in ein Café oder in den Park.",
}


static func advice(key: String) -> String:
	return ADVICE.get(key, "")


## Stimmung 0..100 aus den Beduerfnissen (niedrige Werte wiegen schwerer).
static func mood(n: Dictionary) -> float:
	var s := 0.0
	var w := 0.0
	for k in ["hunger", "thirst", "energy", "hygiene", "social", "fun"]:
		if not n.has(k):
			continue
		var v: float = float(n[k])
		var weight := 1.0 + (1.5 if v < 25.0 else 0.0)
		s += v * weight
		w += weight
	var m := s / maxf(w, 1.0)
	if n.has("health"):
		m = m * 0.75 + float(n.health) * 0.25
	return clampf(m, 0.0, 100.0)


static func mood_label(m: float) -> String:
	if m >= 80.0:
		return "Glücklich"
	if m >= 60.0:
		return "Zufrieden"
	if m >= 40.0:
		return "Durchwachsen"
	if m >= 20.0:
		return "Unglücklich"
	return "Verzweifelt"


## Kritischstes Beduerfnis (fuer HUD-Hinweise): {key, value}
static func most_urgent(n: Dictionary) -> Dictionary:
	var best := ""
	var bv := 1000.0
	for k in ["hunger", "thirst", "energy", "hygiene", "social", "fun"]:
		if n.has(k) and float(n[k]) < bv:
			bv = float(n[k])
			best = k
	return {"key": best, "value": bv}
