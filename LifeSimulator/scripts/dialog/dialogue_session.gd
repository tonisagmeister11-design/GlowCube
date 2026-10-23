class_name DialogueSession
extends RefCounted
## Ein Gespraech zwischen Spieler und NPC. Alle Spielwirkungen (Beziehung,
## soziales Beduerfnis, Geld) werden hier lokal und deterministisch
## entschieden. Die optionale KI (GroqClient) formuliert nur den Text um.
##
## Zustand fuer die Oberflaeche:
##   {speaker, subtitle, text, options: [{id, label}], end: bool}
## Beziehungen: Spielersicht in world.player.relationships[person_id],
## NPC-Sicht in person.relationships[PLAYER_KEY].

const PLAYER_KEY := -1
const GIFT_AMOUNT := 5.0

var world: WorldState
var person_id: int
var person: Dictionary = {}
var _salt := 0
var _asked := {}


func _init(w: WorldState, pid: int) -> void:
	world = w
	person_id = pid
	if w and w.population and w.population.has_method("get_person"):
		var p = w.population.get_person(pid)
		if typeof(p) == TYPE_DICTIONARY:
			person = p
	_salt = hash("%d:%d:%d" % [w.seed if w else 0, pid, w.get_day() if w else 0])


func speaker_name() -> String:
	if person.is_empty():
		return "Passant"
	return "%s %s" % [person.get("first", ""), person.get("last", "")]


func _subtitle() -> String:
	if person.is_empty():
		return ""
	var parts := []
	if person.has("birth") and world:
		parts.append("%d Jahre" % int(world.age_years(float(person.birth))))
	var jt := _job_title()
	if jt != "":
		parts.append(jt)
	elif person.get("homeless", false):
		parts.append("Ohne Wohnung")
	var rel := relation()
	parts.append("Beziehung: %s" % _relation_label(rel))
	return " · ".join(parts)


func _job_title() -> String:
	var job = person.get("job", {})
	if typeof(job) != TYPE_DICTIONARY or job.is_empty():
		return ""
	if job.has("title"):
		return str(job.title)
	var occ := str(job.get("occupation", ""))
	if occ != "" and ResourceLoader.exists("res://scripts/jobs/occupations.gd"):
		var occs = load("res://scripts/jobs/occupations.gd")
		var table = occs.get("OCCUPATIONS") if occs else null
		if typeof(table) == TYPE_DICTIONARY and table.has(occ):
			return str(table[occ].get("name", occ))
	return occ


static func _relation_label(v: float) -> String:
	if v >= 60.0:
		return "Enge Freundschaft"
	if v >= 25.0:
		return "Befreundet"
	if v >= 5.0:
		return "Bekannt"
	if v > -20.0:
		return "Fremd"
	return "Angespannt"


func relation() -> float:
	if world and world.player and "relationships" in world.player:
		var r: Dictionary = world.player.relationships
		return float(r.get(person_id, 0.0))
	return 0.0


func _change_relation(delta: float) -> void:
	if world == null:
		return
	if world.player and "relationships" in world.player:
		var r: Dictionary = world.player.relationships
		r[person_id] = clampf(float(r.get(person_id, 0.0)) + delta, -100.0, 100.0)
	if not person.is_empty():
		if not person.has("relationships"):
			person["relationships"] = {}
		var pr: Dictionary = person.relationships
		pr[PLAYER_KEY] = clampf(float(pr.get(PLAYER_KEY, 0.0)) + delta, -100.0, 100.0)


func _social(delta: float) -> void:
	if world and world.player and "needs" in world.player:
		var n: Dictionary = world.player.needs
		if n.has("social"):
			n.social = clampf(float(n.social) + delta, 0.0, 100.0)


func _zone() -> String:
	if world == null or person.is_empty():
		return ""
	var pos := Vector2.ZERO
	if world.population.has_method("position_of"):
		pos = world.population.position_of(world, person_id)
	var d: Dictionary = world.city.district_at(pos)
	return str(d.get("zone", ""))


func _state(text: String, end := false) -> Dictionary:
	var opts := []
	if not end:
		if not _asked.has("smalltalk"):
			opts.append({"id": "smalltalk", "label": "Plaudern"})
		if not _asked.has("job"):
			opts.append({"id": "job", "label": "Nach der Arbeit fragen"})
		if not _asked.has("news"):
			opts.append({"id": "news", "label": "Was gibt es Neues?"})
		if not _asked.has("directions"):
			opts.append({"id": "directions", "label": "Nach dem Weg zum nächsten Supermarkt fragen"})
		if not _asked.has("compliment"):
			opts.append({"id": "compliment", "label": "Ein Kompliment machen"})
		if person.get("homeless", false) and not _asked.has("gift"):
			opts.append({"id": "gift", "label": "%d € geben" % int(GIFT_AMOUNT)})
		opts.append({"id": "bye", "label": "Verabschieden"})
	return {"speaker": speaker_name(), "subtitle": _subtitle(), "text": text, "options": opts, "end": end}


func start() -> Dictionary:
	_salt += 1
	var hour := world.get_hour() if world else 12
	_social(2.0)
	return _state(DialogueLibrary.greeting(person, hour, relation(), _salt))


func choose(option_id: String) -> Dictionary:
	_salt += 7
	_asked[option_id] = true
	match option_id:
		"smalltalk":
			var gain := 2.0 + 4.0 * DialogueLibrary._trait(person, "friendly") + 3.0 * DialogueLibrary._trait(person, "social")
			_change_relation(gain)
			_social(8.0)
			return _state(DialogueLibrary.smalltalk(person, _zone(), _salt))
		"job":
			_social(3.0)
			_change_relation(1.0)
			return _state(DialogueLibrary.about_job(person, _job_title(), _salt))
		"news":
			_social(3.0)
			if world and not world.news.is_empty():
				var n: Dictionary = world.news[absi(_salt) % mini(5, world.news.size())]
				return _state("Hast du schon gehört? %s" % str(n.get("text", "")))
			return _state("Nichts Besonderes, ehrlich gesagt. Es ist ruhig in Havenbrück.")
		"directions":
			return _state(_directions())
		"compliment":
			var accept := relation() > -20.0 and (absi(_salt) % 100) < int(40 + 50 * DialogueLibrary._trait(person, "friendly"))
			_change_relation(6.0 if accept else -3.0)
			_social(4.0 if accept else 0.0)
			return _state(DialogueLibrary.compliment_reply(person, accept, _salt))
		"gift":
			return _state(_gift())
		_:
			_change_relation(0.5)
			return _state(DialogueLibrary.farewell(_salt), true)


func _directions() -> String:
	if world == null:
		return "Keine Ahnung, tut mir leid."
	var from := Vector2.ZERO
	if world.population and world.population.has_method("position_of") and not person.is_empty():
		from = world.population.position_of(world, person_id)
	var best: Dictionary = {}
	var bd := INF
	for b in world.city.buildings:
		if b.business_type in ["supermarket", "grocery"]:
			var d: float = b.sidewalk_point.distance_to(from)
			if d < bd:
				bd = d
				best = b
	if best.is_empty():
		return "Hier in der Nähe gibt es keinen, fürchte ich."
	world.emit_event("request_route", [CityModel.v3(best.sidewalk_point), str(best.name)])
	var dist := "gleich um die Ecke" if bd < 150.0 else "etwa %d Meter von hier" % (roundi(bd / 50.0) * 50)
	return "%s in der %s, %s. Ich habe es dir auf der Karte markiert." % [best.name, str(best.address).rsplit(" ", true, 1)[0] if str(best.address) != "" else "Nähe", dist]


func _gift() -> String:
	if world == null or world.ledger.player_account < 0:
		return "Danke trotzdem."
	var target := -1
	if world.population and "households" in world.population:
		var hh = world.population.households.get(int(person.get("household", -1)))
		if typeof(hh) == TYPE_DICTIONARY:
			target = int(hh.get("account", -1))
	var ok := false
	if target >= 0:
		ok = world.ledger.transfer(world.ledger.player_account, target, GIFT_AMOUNT, "Spende")
	else:
		ok = world.ledger.burn(world.ledger.player_account, GIFT_AMOUNT, "Spende")
	if not ok:
		return "Schon gut, du hast ja selbst nichts."
	_change_relation(10.0)
	_social(5.0)
	return "Das ist wirklich lieb von dir. Danke!"


## Optional: Text von der KI umformulieren lassen. Liefert den lokalen Text,
## wenn die KI aus ist oder scheitert. groq: GroqClient-Node (oder null).
func enhance(state: Dictionary, groq: Node) -> Dictionary:
	if groq == null or not groq.has_method("is_enabled") or not groq.is_enabled():
		return state
	var age_txt := ""
	if person.has("birth") and world:
		age_txt = "%d Jahre alt, " % int(world.age_years(float(person.birth)))
	var sys := ("Du bist %s, %s%s, in der fiktiven Stadt Havenbrück. Persönlichkeit: %s. " +
		"Antworte auf Deutsch in höchstens zwei kurzen Sätzen, natürlich und passend zur Persönlichkeit. " +
		"Keine Emojis, keine Anführungszeichen, keine Aufzählungen. Erfinde keine Preise, Belohnungen, " +
		"Versprechen oder Spielregeln.") % [speaker_name(), age_txt, _job_title() if _job_title() != "" else "ohne festen Job",
		DialogueLibrary.temperament(person)]
	var usr := "Formuliere diese Aussage in deinen eigenen Worten, behalte den Inhalt bei: %s" % state.text
	var t: String = await groq.complete(sys, usr)
	if t != "":
		var s2 := state.duplicate()
		s2.text = t
		s2["ai"] = true
		return s2
	return state
