class_name Goals
extends RefCounted
## Lebensziele: eine kleine, datengetriebene Liste von Zielen, die sich an
## Lebensphase und Lage der Spielfigur anpasst. Jedes Ziel hat Titel, kurze
## Beschreibung, einen Hinweis (was und wo), eine Pruefbedingung und eine
## Belohnung (Geld, Faehigkeitspunkte oder nur Anerkennung).
##
## Ziele erfuellen sich von selbst, sobald ihre Bedingung zutrifft. Geprueft
## wird nur stuendlich (on_hour), nicht in jedem Simulationsschritt.
##
## Beim Spielstart bereits erfuellte Ziele werden als `skipped` vermerkt: sie
## tauchen nie auf und zahlen keine Belohnung (sonst wuerde eine wohlhabende
## Startfigur sofort "Spare 5.000 €" abraeumen).

const MAX_ACTIVE := 3

## Zieldefinitionen in Reihenfolge der Dringlichkeit.
## Felder je Eintrag:
##   id       eindeutiger Schluessel (zugleich Zweig in `_check`)
##   title    deutscher Titel
##   desc     ein Satz Beschreibung
##   hint     was konkret zu tun ist
##   icon     Datei unter assets/icons (ohne .svg)
##   place    Ort fuer die Kartenmarkierung (siehe `_find_place`), "" = keiner
##   min_age  / max_age  Altersfenster in Jahren
##   needs    Liste von Ziel-IDs, die vorher erledigt sein muessen
##   reward   {"money": float} | {"skill": String, "amount": float} | {} (Anerkennung)
const DEFS: Array = [
	{"id": "eat", "title": "Iss etwas", "desc": "Hunger ist das erste Problem jedes Lebens.",
		"hint": "Kaufe im Supermarkt etwas zu essen und benutze es im Inventar.",
		"icon": "hunger", "place": "food", "min_age": 0.0, "max_age": 999.0, "needs": [], "reward": {}},
	{"id": "school_day", "title": "Geh zur Schule", "desc": "In der Schule lernst du die Grundlagen für alles Weitere.",
		"hint": "Besuche an einem Wochentag die Schule und nimm am Unterricht teil.",
		"icon": "school", "place": "school", "min_age": 6.0, "max_age": 17.0, "needs": [], "reward": {}},
	{"id": "friend", "title": "Finde Freunde", "desc": "Wer Menschen kennt, kommt im Leben weiter.",
		"hint": "Sprich Leute auf der Straße an und unterhalte dich mit ihnen.",
		"icon": "users", "place": "", "min_age": 5.0, "max_age": 999.0, "needs": [],
		"reward": {"skill": "charisma", "amount": 5.0}},
	{"id": "school_done", "title": "Schließe die Schule ab", "desc": "Ein Abschluss öffnet dir die meisten Berufe.",
		"hint": "Geh regelmäßig zur Schule, bis du den Abschluss hast.",
		"icon": "graduation", "place": "school", "min_age": 6.0, "max_age": 30.0, "needs": [],
		"reward": {"money": 250.0}},
	{"id": "job", "title": "Finde eine Arbeit", "desc": "Ohne Einkommen wird jede Woche teurer.",
		"hint": "Im Jobcenter kannst du dich auf offene Stellen bewerben.",
		"icon": "briefcase", "place": "job_center", "min_age": 16.0, "max_age": 999.0, "needs": [],
		"reward": {"money": 100.0}},
	{"id": "first_shift", "title": "Arbeite deine erste Schicht", "desc": "Der erste Lohn ist selbst verdient.",
		"hint": "Geh zur Schichtzeit zu deinem Arbeitsplatz und beginne die Schicht.",
		"icon": "clock", "place": "work", "min_age": 16.0, "max_age": 999.0, "needs": ["job"],
		"reward": {"money": 150.0}},
	{"id": "rent", "title": "Miete eine eigene Wohnung", "desc": "Ein eigenes Zuhause heißt Bett, Dusche und Küche.",
		"hint": "Beim Immobilienmakler findest du freie Wohnungen zur Miete.",
		"icon": "home", "place": "real_estate", "min_age": 18.0, "max_age": 999.0, "needs": [],
		"reward": {"money": 200.0}},
	{"id": "skill_50", "title": "Lerne eine Fähigkeit auf 50", "desc": "Gute Fähigkeiten bringen bessere Stellen.",
		"hint": "Arbeite, lerne oder belege einen Kurs – Fähigkeiten siehst du im Smartphone.",
		"icon": "sparkle", "place": "university", "min_age": 10.0, "max_age": 999.0, "needs": [],
		"reward": {"money": 200.0}},
	{"id": "save_5000", "title": "Spare 5.000 €", "desc": "Rücklagen machen dich unabhängig.",
		"hint": "Arbeite regelmäßig und gib weniger aus, als du verdienst.",
		"icon": "wallet", "place": "", "min_age": 14.0, "max_age": 999.0, "needs": [],
		"reward": {"money": 500.0}},
	{"id": "promotion", "title": "Werde befördert", "desc": "Wer zuverlässig arbeitet, steigt auf.",
		"hint": "Erscheine pünktlich zu deinen Schichten und halte deine Leistung hoch.",
		"icon": "chart", "place": "work", "min_age": 16.0, "max_age": 999.0, "needs": ["first_shift"],
		"reward": {"money": 400.0}},
	{"id": "car", "title": "Kaufe ein Auto", "desc": "Mit einem eigenen Wagen bist du in der Stadt schneller.",
		"hint": "Im Autohaus kannst du Fahrzeuge kaufen.",
		"icon": "car", "place": "car_dealer", "min_age": 18.0, "max_age": 999.0, "needs": [],
		"reward": {"skill": "driving", "amount": 6.0}},
	{"id": "property", "title": "Kaufe eine Immobilie", "desc": "Eigentum statt Miete – ein großer Schritt.",
		"hint": "Der Immobilienmakler vermittelt auch Kaufobjekte; die Bank gibt Hypotheken.",
		"icon": "building", "place": "real_estate", "min_age": 18.0, "max_age": 999.0, "needs": [],
		"reward": {"money": 1000.0}},
	{"id": "family", "title": "Gründe eine Familie", "desc": "Eine feste Beziehung und ein Kind.",
		"hint": "Lerne jemanden näher kennen, werde ein Paar und gründet eine Familie.",
		"icon": "heart", "place": "", "min_age": 18.0, "max_age": 50.0, "needs": [],
		"reward": {"money": 300.0}},
	{"id": "police", "title": "Werde Polizist", "desc": "Dienst für die Stadt – ein sicherer Beruf.",
		"hint": "Auf der Polizeiwache kannst du dich für den Polizeidienst bewerben.",
		"icon": "police", "place": "police", "min_age": 18.0, "max_age": 60.0, "needs": [],
		"reward": {"money": 300.0}},
	{"id": "gang_rank", "title": "Steige in einer Organisation auf", "desc": "In den Hinterhöfen zählt nur der Rang.",
		"hint": "Nimm Aufträge an und erarbeite dir das Vertrauen des Bosses.",
		"icon": "mask", "place": "", "min_age": 16.0, "max_age": 999.0, "needs": [],
		"reward": {"money": 500.0}},
	{"id": "office", "title": "Werde in ein politisches Amt gewählt", "desc": "Wer die Regeln macht, verändert die Stadt.",
		"hint": "Tritt einer Partei bei und kandidiere bei der nächsten Wahl.",
		"icon": "vote", "place": "city_hall", "min_age": 18.0, "max_age": 999.0, "needs": [],
		"reward": {"money": 1000.0}},
	{"id": "millionaire", "title": "Werde Millionär", "desc": "Eine Million Euro auf dem Konto.",
		"hint": "Karriere, Unternehmen oder Immobilien – viele Wege führen dorthin.",
		"icon": "trophy", "place": "", "min_age": 18.0, "max_age": 999.0, "needs": ["save_5000"],
		"reward": {"money": 10000.0}},
]

## Orte: place -> [Geschaeftsarten, Gebaeudearten]
const PLACES: Dictionary = {
	"food": [["supermarket", "grocery", "convenience", "bakery"], []],
	"job_center": [["job_center"], []],
	"real_estate": [["real_estate"], []],
	"car_dealer": [["car_dealer"], []],
	"school": [[], ["school"]],
	"university": [[], ["university"]],
	"police": [[], ["police"]],
	"city_hall": [[], ["city_hall", "parliament"]],
}

var completed: Dictionary = {}   # id -> Tag der Erfuellung
var skipped: Dictionary = {}     # id -> true (bei Spielbeginn schon erfuellt)
var flags: Dictionary = {}       # Merker fuer mehrstufige Pruefungen
var intro_shown := false


# ============================================================ Aufbau

func setup(world) -> void:
	completed.clear()
	skipped.clear()
	flags.clear()
	intro_shown = false
	for def in DEFS:
		var id: String = str(def.id)
		if _check(world, id):
			skipped[id] = true


func post_load(_world) -> void:
	pass


# ============================================================ Takt

func on_hour(world, day: int, _hour: int) -> void:
	var p = world.player
	if p == null or not p.alive:
		return
	if float(p.needs.get("hunger", 100.0)) < 50.0:
		flags["hunger_low"] = true
	for def in DEFS:
		var id: String = str(def.id)
		if not _open(world, def):
			continue
		if _check(world, id):
			_complete(world, def, day)


# ============================================================ Abfragen

## Ist das Ziel fuer die aktuelle Lage offen (noch nicht erledigt, Alter passt,
## Voraussetzungen erfuellt)?
func _open(world, def: Dictionary) -> bool:
	var id: String = str(def.id)
	if completed.has(id) or skipped.has(id):
		return false
	var p = world.player
	if p == null or not p.alive:
		return false
	var a: float = p.age(world)
	if a < float(def.min_age) or a > float(def.max_age):
		return false
	for need in def.needs:
		var nid: String = str(need)
		if not (completed.has(nid) or skipped.has(nid)):
			return false
	return true


## Die naechsten offenen Ziele (hoechstens MAX_ACTIVE), jeweils mit Ortsangabe.
func active(world) -> Array:
	var out: Array = []
	for def in DEFS:
		if out.size() >= MAX_ACTIVE:
			break
		if not _open(world, def):
			continue
		out.append(info(world, def))
	return out


## Anzeigedaten eines Ziels: {id, title, desc, hint, icon, place_label, pos, has_pos}
func info(world, def: Dictionary) -> Dictionary:
	var place: Dictionary = _find_place(world, str(def.place))
	var out: Dictionary = {"id": str(def.id), "title": str(def.title), "desc": str(def.desc),
		"hint": str(def.hint), "icon": str(def.icon), "reward_text": reward_text(def),
		"place_label": str(place.get("label", "")), "pos": place.get("pos", Vector2.ZERO),
		"has_pos": place.has("pos")}
	return out


## Bereits erfuellte Ziele, neueste zuerst.
func done_list() -> Array:
	var out: Array = []
	for def in DEFS:
		var id: String = str(def.id)
		if completed.has(id):
			out.append({"id": id, "title": str(def.title), "day": int(completed[id])})
	out.sort_custom(func(a, b): return int(a.day) > int(b.day))
	return out


func definition(id: String) -> Dictionary:
	for def in DEFS:
		if str(def.id) == id:
			return def
	return {}


func reward_text(def: Dictionary) -> String:
	var r: Dictionary = def.get("reward", {})
	if r.has("money"):
		return "Belohnung: %s" % Economy.fmt_money(float(r.money))
	if r.has("skill"):
		return "Belohnung: +%d %s" % [int(r.amount), Skills.label(str(r.skill))]
	return "Belohnung: Anerkennung"


# ============================================================ Pruefungen

func _check(world, id: String) -> bool:
	var p = world.player
	if p == null:
		return false
	match id:
		"eat":
			return bool(flags.get("hunger_low", false)) and float(p.needs.get("hunger", 0.0)) >= 60.0
		"school_day":
			return float(_enrollment(world).get("hours", 0.0)) > 0.0
		"friend":
			return _has_friend(world)
		"school_done":
			return Occupations.edu_rank(str(p.education)) >= Occupations.edu_rank("school")
		"job":
			return _position(world) >= 0
		"first_shift":
			return int(_job(world).get("days_worked", 0)) >= 1
		"rent":
			return int(p.home) >= 0 and int(p.household) < 0
		"skill_50":
			return _best_skill(p) >= 50.0
		"save_5000":
			return _money(world) >= 5000.0
		"promotion":
			return int(_job(world).get("rank", 0)) >= 1
		"car":
			return (p.vehicles as Array).size() > 0
		"property":
			return world.housing != null and not world.housing.owned_by("player", 0).is_empty()
		"family":
			return int(p.partner) >= 0 and _has_child(world)
		"police":
			return _police_member(p)
		"gang_rank":
			return _gang_rank(world) >= 1
		"office":
			var office: String = str(p.political_office)
			return office != "" and office != "member"
		"millionaire":
			return _money(world) >= 1000000.0
	return false


func _money(world) -> float:
	return world.ledger.balance(world.player.account)


func _position(world) -> int:
	return int(world.jobs.position_of("player", PlayerLife.PLAYER_ID)) if world.jobs else -1


func _job(world) -> Dictionary:
	return world.jobs.job_of("player", PlayerLife.PLAYER_ID) if world.jobs else {}


func _enrollment(world) -> Dictionary:
	return world.education.enrollment_of("player", PlayerLife.PLAYER_ID) if world.education else {}


func _best_skill(p) -> float:
	var best := 0.0
	var sk: Dictionary = p.skills
	for k in sk:
		best = maxf(best, float(sk[k]))
	return best


## Freundschaft: gute Beziehung zu jemandem ausserhalb des eigenen Haushalts.
func _has_friend(world) -> bool:
	var p = world.player
	var pop = world.population
	if pop == null:
		return false
	var members: Array = []
	if int(p.household) >= 0 and pop.households.has(int(p.household)):
		members = pop.households[int(p.household)].members
	for pid in p.relationships:
		if int(pid) in members:
			continue
		if float(p.relationships[pid]) >= 70.0:
			return true
	return false


func _has_child(world) -> bool:
	var pop = world.population
	if pop == null:
		return false
	for pid in world.player.relationships:
		var person: Dictionary = pop.persons.get(int(pid), {})
		if bool(person.get("player_parent", false)):
			return true
	return false


func _police_member(p) -> bool:
	var r = p.police_rank
	if r is String:
		return str(r) != ""
	return int(r) >= 0


func _gang_rank(world) -> int:
	if world.crime == null or world.crime.get("gangs") == null:
		return -1
	var g = world.crime.gangs
	return int(g.rank) if int(g.player_gang) >= 0 else -1


# ============================================================ Erfuellen

func _complete(world, def: Dictionary, day: int) -> void:
	var id: String = str(def.id)
	completed[id] = day
	var extra: String = _grant(world, def)
	var text: String = "Ziel erreicht: %s" % str(def.title)
	if extra != "":
		text += " – %s" % extra
	world.notify(text, "success")
	world.emit_event("goal_completed", [info(world, def)])
	world.post_news("leben", "%s: %s" % [str(world.player.name), str(def.title)])


## Zahlt die Belohnung aus und beschreibt sie. Geld entsteht ueber das Ledger.
func _grant(world, def: Dictionary) -> String:
	var r: Dictionary = def.get("reward", {})
	if r.has("money"):
		var amount: float = float(r.money)
		world.ledger.mint(world.player.account, amount, "Belohnung: %s" % str(def.title))
		return "%s erhalten" % Economy.fmt_money(amount)
	if r.has("skill"):
		var skill: String = str(r.skill)
		Skills.gain(world.player.skills, skill, float(r.amount))
		world.emit_event("player_skill_changed", [skill, float(world.player.skills[skill])])
		return "%s verbessert" % Skills.label(skill)
	return ""


# ============================================================ Orte

## Naechstgelegenes Gebaeude fuer einen Ortsschluessel. Bezugspunkt ist das
## Zuhause der Spielfigur, sonst die Stadtmitte.
func _find_place(world, place: String) -> Dictionary:
	if place == "":
		return {}
	if place == "home":
		return _building_place(world, _home_building(world))
	if place == "work":
		var pid: int = _position(world)
		if pid < 0:
			return {}
		return _building_place(world, int(world.jobs.positions[pid].building))
	var spec: Array = PLACES.get(place, [])
	if spec.is_empty():
		return {}
	var biz: Array = spec[0]
	var kinds: Array = spec[1]
	var from: Vector2 = _reference_pos(world)
	var best := -1
	var bd := INF
	for b in world.city.buildings:
		if not (str(b.business_type) in biz or str(b.kind) in kinds):
			continue
		var d: float = (b.sidewalk_point as Vector2).distance_to(from)
		if d < bd:
			bd = d
			best = int(b.id)
	return _building_place(world, best)


func _building_place(world, bid: int) -> Dictionary:
	if bid < 0 or bid >= world.city.buildings.size():
		return {}
	var b: Dictionary = world.city.buildings[bid]
	var label: String = str(b.name) if str(b.name) != "" else str(b.label)
	var did: int = int(b.district)
	if did >= 0 and did < world.city.districts.size():
		label += " · %s" % str(world.city.districts[did].name)
	return {"pos": b.sidewalk_point, "label": label, "building": bid}


func _home_building(world) -> int:
	var p = world.player
	if world.housing == null or int(p.home) < 0:
		return -1
	return int(world.housing.get_property(int(p.home)).get("building", -1))


func _reference_pos(world) -> Vector2:
	var hb: int = _home_building(world)
	if hb >= 0:
		return world.city.buildings[hb].sidewalk_point
	return Vector2.ZERO


# ============================================================ Tagesrhythmus

## Kurze Zeilen fuer die "Heute"-Anzeige: Schicht, Schule, Laden, Miete.
func today_lines(world) -> Array:
	var out: Array = []
	var p = world.player
	if p == null:
		return out
	var day: int = world.get_day()
	var pid: int = _position(world)
	if pid >= 0:
		var pos: Dictionary = world.jobs.positions[pid]
		if world.jobs.is_workday(pos, day):
			var start: float = world.jobs.shift_start_minutes(pos, day)
			var end: float = start + float(pos.shift_hours) * 60.0
			out.append("Schicht %s – %s Uhr" % [_clock(start), _clock(end)])
		else:
			out.append("Heute keine Schicht")
	var a: float = p.age(world)
	if a >= 6.0 and a < 18.0 and not _enrollment(world).is_empty():
		if world.get_weekday() < 5:
			out.append("Schule 08:00 – 13:00 Uhr")
		else:
			out.append("Heute keine Schule")
	var open_hours: Array = CityCatalog.BUSINESS_TYPES.get("supermarket", {}).get("open", [7, 22])
	out.append("Supermärkte %02d:00 – %02d:00 Uhr" % [int(open_hours[0]), int(open_hours[1])])
	var rent_day: int = _rent_day(world)
	if rent_day >= 0:
		var left: int = (rent_day - world.get_weekday() + 7) % 7
		var when: String = "heute" if left == 0 else ("morgen" if left == 1 else WorldState.WEEKDAYS[rent_day])
		out.append("Miete fällig: %s" % when)
	return out


func _rent_day(world) -> int:
	var p = world.player
	if world.housing == null or int(p.home) < 0:
		return -1
	var prop: Dictionary = world.housing.get_property(int(p.home))
	var tenant: Dictionary = prop.get("tenant", {})
	if prop.is_empty() or str(tenant.get("kind", "")) != "player":
		return -1
	var owner: Dictionary = prop.get("owner", {})
	if str(owner.get("kind", "")) == "player":
		return -1   # Eigentum: keine Miete
	return int(prop.get("rent_day", -1)) % 7


func _clock(minutes: float) -> String:
	var m: int = int(roundf(fmod(minutes, 1440.0)))
	return "%02d:%02d" % [m / 60, m % 60]


# ============================================================ Einstieg

## 4 bis 6 Zeilen fuer die Startkarte, gebaut aus der echten Startsituation.
func intro_lines(world) -> Array:
	var p = world.player
	var out: Array = []
	if p == null:
		return out
	var a: int = int(p.age(world))
	out.append("Du bist %s, %d Jahre alt, in Havenbrück." % [str(p.name), a])
	match str(p.start_situation):
		"with_parents", "poor_parents":
			out.append("Du wohnst noch bei deinen Eltern – Essen und ein Bett hast du, Geld kaum.")
		"own_apartment":
			out.append("Du hast eine eigene kleine Wohnung – und jede Woche ist Miete fällig.")
		"shared_room":
			out.append("Du hast ein Zimmer in einer Wohngemeinschaft; die Miete läuft wöchentlich weiter.")
		"homeless":
			out.append("Du hast kein Zuhause. In der Notunterkunft kannst du schlafen, duschen und essen.")
		"wealthy":
			out.append("Du bist vermögend und wohnst in einem großen Haus – Langeweile ist dein größtes Risiko.")
		_:
			out.append("Du beginnst dein Leben in dieser Stadt.")
	out.append("Achte auf deine Bedürfnisse unten links: Hunger, Durst, Energie, Hygiene.")
	for g in active(world):
		var line: String = "Nächstes Ziel: %s – %s" % [str(g.title), str(g.hint)]
		if str(g.place_label) != "":
			line += " (%s)" % str(g.place_label)
		out.append(line)
		if out.size() >= 5:
			break
	out.append("Mit [%s] siehst du jederzeit, was du tun kannst." % Settings.binding_label("help"))
	return out


# ============================================================ Speichern

func to_save() -> Dictionary:
	return {"completed": completed.duplicate(), "skipped": skipped.duplicate(),
		"flags": flags.duplicate(), "intro_shown": intro_shown}


func from_save(_world, d: Dictionary) -> void:
	completed = (d.get("completed", {}) as Dictionary).duplicate()
	skipped = (d.get("skipped", {}) as Dictionary).duplicate()
	flags = (d.get("flags", {}) as Dictionary).duplicate()
	intro_shown = bool(d.get("intro_shown", false))
