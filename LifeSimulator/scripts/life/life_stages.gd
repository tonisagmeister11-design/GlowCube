class_name LifeStages
extends RefCounted
## Lebensphasen, Sterblichkeit (Gompertz) und altersabhaengige Faktoren.

const STAGES := [["baby", 0.0, 2.0], ["toddler", 2.0, 4.0], ["child", 4.0, 12.0], ["teen", 12.0, 18.0],
	["young_adult", 18.0, 30.0], ["adult", 30.0, 50.0], ["older_adult", 50.0, 65.0], ["senior", 65.0, 999.0]]
const LABELS := {"baby": "Baby", "toddler": "Kleinkind", "child": "Kind", "teen": "Jugendliche/r",
	"young_adult": "Junge/r Erwachsene/r", "adult": "Erwachsene/r", "older_adult": "Ältere/r Erwachsene/r", "senior": "Senior/in"}

## Gompertz-Steigung pro Jahr. Modalwert = lifespan + 6,8 ergibt eine mittlere
## Lebenserwartung von etwa `lifespan`.
const GOMPERTZ_B := 0.085


static func stage(age: float) -> String:
	for s in STAGES:
		if age < s[2]:
			return s[0]
	return "senior"


static func label(st: String) -> String:
	return LABELS.get(st, st)


static func is_adult(age: float) -> bool:
	return age >= 18.0


## Sterberisiko pro Lebensjahr.
static func hazard_per_year(age: float, lifespan: float) -> float:
	var mode := lifespan + 6.8
	var h := GOMPERTZ_B * exp(GOMPERTZ_B * (age - mode))
	if age < 1.0:
		h += 0.003
	return h + 0.0004


## Ueberlebenswahrscheinlichkeit bis zum Alter (fuer die Altersverteilung beim Start).
static func survival(age: float, lifespan: float) -> float:
	var mode := lifespan + 6.8
	var b := GOMPERTZ_B
	return exp(-exp(-b * mode) * (exp(b * age) - 1.0))


## Bewegungstempo relativ zu einem jungen Erwachsenen.
static func speed_factor(age: float) -> float:
	if age < 2.0:
		return 0.3
	if age < 4.0:
		return 0.55
	if age < 10.0:
		return 0.85
	if age < 50.0:
		return 1.0
	if age < 65.0:
		return 0.95
	if age < 75.0:
		return 0.85
	if age < 85.0:
		return 0.72
	return 0.6


## Hoechstwert der Energie (aeltere Menschen ermueden schneller).
static func energy_cap(age: float) -> float:
	if age < 60.0:
		return 100.0
	return clampf(100.0 - (age - 60.0) * 1.2, 55.0, 100.0)
