class_name DialogueLibrary
extends RefCounted
## Lokale, regelbasierte NPC-Saetze. Funktioniert ohne Internet und bildet
## die Grundlage jedes Gespraechs; die optionale KI darf den Text nur
## umformulieren. Auswahl haengt von Persoenlichkeit, Tageszeit, Taetigkeit,
## Beruf, Beziehung und Stadtteil ab und ist pro Kontext deterministisch.

const GREETINGS := {
	"friendly": ["Hallo! Schön, dich zu sehen.", "Na, wie geht's dir heute?", "Hey, du siehst gut aus heute!"],
	"shy": ["Oh ... hallo.", "Äh, hi.", "Hallo ... kennen wir uns?"],
	"suspicious": ["Was willst du?", "Kenne ich dich?", "Ja? Mach's kurz."],
	"social": ["Hey! Komm, erzähl mal, was gibt's Neues?", "Ah, endlich jemand zum Reden!", "Hallo hallo! Was treibt dich hierher?"],
	"neutral": ["Hallo.", "Guten Tag.", "Hi."],
}
const MORNING := ["Guten Morgen.", "Morgen! Noch nicht ganz wach ...", "Früh unterwegs, was?"]
const EVENING := ["Guten Abend.", "Na, auch noch unterwegs?", "Langer Tag heute."]
const NIGHT := ["Ziemlich spät, oder?", "Um diese Uhrzeit noch draußen?", "Pass auf dich auf, nachts ist es hier nicht immer ruhig."]

const ACTIVITY_LINES := {
	"work": ["Ich bin gerade bei der Arbeit, viel Zeit habe ich nicht.", "Die Schicht zieht sich heute."],
	"commute": ["Ich muss weiter, mein Termin wartet.", "Bin auf dem Weg, nur kurz."],
	"shop": ["Ich hole nur schnell ein paar Sachen.", "Die Preise steigen gefühlt jede Woche."],
	"leisure": ["Ich genieße gerade meinen freien Tag.", "Endlich mal entspannen."],
	"school": ["Gleich fängt der Unterricht an.", "Schule ... naja, muss halt sein."],
	"wander": ["Ich laufe einfach ein bisschen herum.", "Kein Ziel heute, nur frische Luft."],
	"home": ["Ich wollte eigentlich gerade nach Hause.", "Zuhause ist es doch am schönsten."],
}

const JOB_LINES := [
	"Ich arbeite als %s. Ist okay, zahlt die Rechnungen.",
	"%s – das ist mein Job. Manchmal anstrengend, aber ich mag die Leute.",
	"Ich bin %s. Wenn du was in der Richtung suchst, schau beim Jobcenter vorbei.",
]
const NO_JOB_LINES := [
	"Gerade suche ich Arbeit. Das Jobcenter hat diese Woche nichts Passendes.",
	"Arbeit? Schwierig im Moment. Ich halte mich irgendwie über Wasser.",
]
const HOMELESS_LINES := [
	"Hast du vielleicht ein bisschen Kleingeld? Die Notunterkunft ist voll.",
	"Seit ich die Wohnung verloren habe, schlafe ich hier draußen. Es ist hart.",
	"Danke, dass du mich nicht ignorierst. Das machen die meisten.",
]
const DISTRICT_LINES := {
	"rich": ["Hier ist es ruhig. Teuer, aber ruhig.", "Die Nachbarn achten sehr auf ihre Gärten."],
	"poor": ["Hier hält man zusammen, auch wenn das Geld knapp ist.", "Die Miete frisst fast alles auf."],
	"slum": ["Nachts solltest du hier vorsichtig sein.", "Die Polizei sieht man hier selten."],
	"downtown": ["In der Innenstadt ist immer was los.", "Die neuen Türme sieht man von überall."],
	"industrial": ["Die Fabriken laufen rund um die Uhr.", "Hier riecht es immer nach Öl."],
	"suburb": ["Ruhig hier draußen, aber ohne Auto kommst du kaum weg.", "Die Kinder können hier noch auf der Straße spielen."],
	"default": ["Ich wohne schon lange in der Gegend.", "Havenbrück verändert sich ständig."],
}
const FAREWELLS := ["Mach's gut!", "Bis dann.", "Man sieht sich.", "Tschüss!"]
const COMPLIMENT_OK := ["Oh, danke! Das ist nett von dir.", "Haha, danke, das hört man gern."]
const COMPLIMENT_BAD := ["Äh ... okay?", "Spar dir die Schmeicheleien."]


static func _pick(arr: Array, salt: int) -> String:
	if arr.is_empty():
		return ""
	return arr[absi(salt) % arr.size()]


static func _trait(p: Dictionary, name: String) -> float:
	var t: Dictionary = p.get("traits", {})
	return float(t.get(name, 0.5))


static func temperament(p: Dictionary) -> String:
	if _trait(p, "suspicious") > 0.7:
		return "suspicious"
	if _trait(p, "shy") > 0.7:
		return "shy"
	if _trait(p, "social") > 0.65:
		return "social"
	if _trait(p, "friendly") > 0.55:
		return "friendly"
	return "neutral"


## Begruessung abhaengig von Tageszeit, Persoenlichkeit und Beziehung.
static func greeting(p: Dictionary, hour: int, relation: float, salt: int) -> String:
	var parts := []
	if hour >= 5 and hour < 10:
		parts.append(_pick(MORNING, salt))
	elif hour >= 18 and hour < 22:
		parts.append(_pick(EVENING, salt))
	elif hour >= 22 or hour < 5:
		parts.append(_pick(NIGHT, salt))
	if relation > 40.0:
		parts.append("Schön, dass wir uns mal wieder treffen!")
	elif relation < -30.0:
		parts.append("Ach, du schon wieder.")
	else:
		parts.append(_pick(GREETINGS[temperament(p)], salt + 1))
	return " ".join(parts)


static func smalltalk(p: Dictionary, zone: String, salt: int) -> String:
	if p.get("homeless", false):
		return _pick(HOMELESS_LINES, salt)
	var act: String = p.get("activity", "")
	if ACTIVITY_LINES.has(act) and salt % 2 == 0:
		return _pick(ACTIVITY_LINES[act], salt)
	var key := zone
	if zone in ["estates"]:
		key = "rich"
	elif zone in ["business", "commercial", "oldtown"]:
		key = "downtown"
	return _pick(DISTRICT_LINES.get(key, DISTRICT_LINES["default"]), salt)


static func about_job(p: Dictionary, job_title: String, salt: int) -> String:
	if job_title == "":
		return _pick(NO_JOB_LINES, salt)
	return _pick(JOB_LINES, salt) % job_title


static func farewell(salt: int) -> String:
	return _pick(FAREWELLS, salt)


static func compliment_reply(p: Dictionary, accepted: bool, salt: int) -> String:
	return _pick(COMPLIMENT_OK if accepted else COMPLIMENT_BAD, salt)
