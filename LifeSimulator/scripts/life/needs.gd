class_name Needs
extends RefCounted
## Beduerfnisse (0..100, 100 = voll erfuellt) und ihre Veraenderung pro
## Spielstunde. Die Raten sind so gewaehlt, dass man im Spiel etwa dreimal
## am Tag isst, einmal duscht und nachts schlaeft (bei needs_decay = 1).

const KEYS := ["hunger", "thirst", "energy", "hygiene", "social", "fun", "health"]
const LABELS := {"hunger": "Hunger", "thirst": "Durst", "energy": "Energie", "hygiene": "Hygiene",
	"social": "Soziales", "fun": "Spaß", "health": "Gesundheit"}

## Verlust pro Stunde im Wachzustand / im Schlaf (negativ = Erholung).
const AWAKE := {"hunger": 5.0, "thirst": 6.5, "energy": 5.0, "hygiene": 2.6, "social": 1.6, "fun": 2.2}
const ASLEEP := {"hunger": 1.8, "thirst": 2.2, "energy": -12.5, "hygiene": 0.8, "social": 0.2, "fun": 0.3}
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
	return {"hunger": 85.0, "thirst": 85.0, "energy": 85.0, "hygiene": 85.0, "social": 70.0, "fun": 70.0, "health": 100.0}


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
