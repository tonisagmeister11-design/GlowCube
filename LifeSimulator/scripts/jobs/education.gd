class_name Education
extends RefCounted
## Bildung: Schule (nach Alter), Berufsausbildung, Studium (Bachelor, Master,
## Promotion) und Kurse, die Faehigkeiten steigern.
##
## Abschluesse: none < school < vocational < bachelor < master < doctor.
## Studiengaenge brauchen Anwesenheitsstunden (attend) und eine
## Mindestdauer in Spieltagen:
##   Stunden = Jahre * days_per_year * 5/7 * 6,  Mindesttage = Jahre * days_per_year * 0.6
## enrollments["kind:id"] = {program, field, hours, required, start_day, min_days,
##   fee_week, unpaid_weeks}

const PROGRAMS := {
	"school": {"name": "Schule", "years": 12.0, "min_age": 5.0, "requires": "none", "grants": "school",
		"place": "school", "fee_week": 0.0, "stipend_hour": 0.0, "fields": []},
	"vocational": {"name": "Berufsausbildung", "years": 3.0, "min_age": 15.0, "requires": "school", "grants": "vocational",
		"place": "school", "fee_week": 0.0, "stipend_hour": 6.5,
		"fields": ["crafts", "gastronomy", "medicine", "office", "driving", "safety", "business"]},
	"bachelor": {"name": "Bachelorstudium", "years": 3.0, "min_age": 17.0, "requires": "school", "grants": "bachelor",
		"place": "university", "fee_week": 35.0, "stipend_hour": 0.0,
		"fields": ["it", "business", "medicine", "law", "teaching", "engineering", "media", "architecture", "science"]},
	"master": {"name": "Masterstudium", "years": 2.0, "min_age": 19.0, "requires": "bachelor", "grants": "master",
		"place": "university", "fee_week": 35.0, "stipend_hour": 0.0,
		"fields": ["it", "business", "medicine", "law", "teaching", "engineering", "media", "architecture", "science"]},
	"doctor": {"name": "Promotion", "years": 3.0, "min_age": 22.0, "requires": "master", "grants": "doctor",
		"place": "university", "fee_week": 0.0, "stipend_hour": 9.0,
		"fields": ["it", "medicine", "law", "engineering", "science", "teaching"]},
}

const FIELDS := {
	"it": {"label": "Informatik", "skills": {"programming": 1.0, "logic": 0.6}},
	"business": {"label": "Betriebswirtschaft", "skills": {"business": 1.0, "logic": 0.4, "charisma": 0.3}},
	"medicine": {"label": "Medizin & Pflege", "skills": {"medicine": 1.0, "logic": 0.2}},
	"law": {"label": "Rechtswissenschaft", "skills": {"law": 1.0, "logic": 0.4}},
	"teaching": {"label": "Lehramt & Pädagogik", "skills": {"teaching": 1.0, "charisma": 0.3}},
	"engineering": {"label": "Ingenieurwesen", "skills": {"logic": 0.8, "handiwork": 0.6}},
	"media": {"label": "Journalismus & Medien", "skills": {"writing": 1.0, "charisma": 0.4, "creativity": 0.3}},
	"architecture": {"label": "Architektur", "skills": {"creativity": 1.0, "logic": 0.5}},
	"science": {"label": "Naturwissenschaften", "skills": {"logic": 1.0, "medicine": 0.3}},
	"crafts": {"label": "Handwerk", "skills": {"handiwork": 1.0, "fitness": 0.3}},
	"gastronomy": {"label": "Gastronomie", "skills": {"cooking": 1.0, "charisma": 0.3}},
	"office": {"label": "Kaufmännisch", "skills": {"logic": 0.6, "business": 0.6}},
	"driving": {"label": "Verkehr & Logistik", "skills": {"driving": 1.0, "logic": 0.2}},
	"safety": {"label": "Polizei & Sicherheit", "skills": {"fitness": 0.8, "law": 0.6}},
}

## Kurse: Gebuehr einmalig, Faehigkeitsgewinn verteilt auf die Stunden.
const COURSES := {
	"driving_license": {"name": "Führerschein (Fahrschule)", "hours": 14.0, "fee": 1450.0, "min_age": 17.0, "skills": {"driving": 40.0}},
	"first_aid": {"name": "Erste-Hilfe-Kurs", "hours": 4.0, "fee": 45.0, "min_age": 12.0, "skills": {"medicine": 8.0}},
	"cooking_course": {"name": "Kochkurs", "hours": 8.0, "fee": 120.0, "min_age": 12.0, "skills": {"cooking": 15.0}},
	"programming_course": {"name": "Programmierkurs", "hours": 20.0, "fee": 390.0, "min_age": 12.0, "skills": {"programming": 18.0, "logic": 6.0}},
	"accounting_course": {"name": "Buchhaltungskurs", "hours": 16.0, "fee": 290.0, "min_age": 16.0, "skills": {"business": 12.0, "logic": 8.0}},
	"rhetoric_course": {"name": "Rhetorikseminar", "hours": 8.0, "fee": 180.0, "min_age": 14.0, "skills": {"charisma": 12.0}},
	"language_course": {"name": "Sprachkurs", "hours": 16.0, "fee": 220.0, "min_age": 10.0, "skills": {"charisma": 6.0, "writing": 8.0}},
	"craft_course": {"name": "Heimwerkerkurs", "hours": 10.0, "fee": 150.0, "min_age": 14.0, "skills": {"handiwork": 14.0}},
	"fitness_course": {"name": "Fitnesskurs", "hours": 10.0, "fee": 90.0, "min_age": 12.0, "skills": {"fitness": 15.0}},
	"writing_course": {"name": "Schreibwerkstatt", "hours": 10.0, "fee": 160.0, "min_age": 12.0, "skills": {"writing": 14.0, "creativity": 6.0}},
	"law_course": {"name": "Rechtskunde-Seminar", "hours": 12.0, "fee": 240.0, "min_age": 16.0, "skills": {"law": 12.0}},
	"security_course": {"name": "Sachkundeprüfung Bewachung", "hours": 12.0, "fee": 200.0, "min_age": 18.0, "skills": {"law": 8.0, "fitness": 6.0}},
}

var enrollments: Dictionary = {}
var rng := RandomNumberGenerator.new()
var _schools: Array = []          # [building id, pos]
var _university := -1


func setup(world) -> void:
	rng.seed = hash("education:%d" % world.seed)
	_index(world)


func _index(world) -> void:
	_schools.clear()
	_university = -1
	for b in world.city.buildings:
		if b.kind == "school":
			_schools.append([b.id, b.sidewalk_point])
		elif b.kind == "university" and _university < 0:
			_university = b.id


# ============================================================ Statische Hilfen

## Klassenstufe fuer ein Alter (0 = noch nicht schulpflichtig).
static func grade_for_age(age: float) -> int:
	if age < 6.0:
		return 0
	return clampi(int(floor(age - 6.0)) + 1, 1, 12)


static func school_stage(age: float) -> String:
	if age < 6.0:
		return "Kindergarten"
	if age < 10.0:
		return "Grundschule"
	if age < 16.0:
		return "Sekundarstufe"
	if age < 18.0:
		return "Oberstufe"
	return ""


static func program_hours(program: String, days_per_year: float) -> float:
	var pr: Dictionary = PROGRAMS.get(program, {})
	if pr.is_empty():
		return float(COURSES.get(program, {}).get("hours", 10.0))
	return maxf(20.0, float(pr.years) * days_per_year * 5.0 / 7.0 * 6.0)


func school_for(world, pos: Vector2) -> int:
	var best := -1
	var bd := INF
	for s in _schools:
		var d: float = pos.distance_squared_to(s[1])
		if d < bd:
			bd = d
			best = s[0]
	return best


func university() -> int:
	return _university


# ============================================================ Zugriff auf Lernende

func _get(world, kind: String, id: int) -> Dictionary:
	if kind == "player" and world.player:
		var pl = world.player
		return {"skills": pl.skills, "education": pl.education, "age": pl.age(world), "account": world.ledger.player_account}
	if kind == "npc" and world.population:
		var p: Dictionary = world.population.persons.get(id, {})
		if p.is_empty():
			return {}
		var h: Dictionary = world.population.households.get(int(p.household), {})
		return {"skills": p.skills, "education": p.education, "age": world.age_years(float(p.birth)),
			"account": int(h.get("account", -1))}
	return {}


func _set_education(world, kind: String, id: int, level: String) -> void:
	if kind == "player" and world.player:
		if Occupations.edu_rank(level) > Occupations.edu_rank(world.player.education):
			world.player.education = level
	elif kind == "npc" and world.population:
		var p: Dictionary = world.population.persons.get(id, {})
		if not p.is_empty() and Occupations.edu_rank(level) > Occupations.edu_rank(p.education):
			p.education = level


static func _key(kind: String, id: int) -> String:
	return "%s:%d" % [kind, id]


func enrollment_of(kind: String, id: int) -> Dictionary:
	return enrollments.get(_key(kind, id), {})


## Programme/Kurse mit Eignung fuer die UI.
func list_programs(world, kind: String, id: int) -> Array:
	var out := []
	var s := _get(world, kind, id)
	for pid in PROGRAMS:
		if pid == "school":
			continue
		var pr: Dictionary = PROGRAMS[pid]
		var reason := _program_block(world, s, pid)
		var fields := []
		for f in pr.fields:
			fields.append({"id": f, "label": FIELDS[f].label})
		out.append({"id": pid, "name": pr.name, "type": "program", "fee_week": float(pr.fee_week),
			"hours": program_hours(pid, world.rules.f("days_per_year")), "eligible": reason == "", "reason": reason,
			"grants": pr.grants, "fields": fields})
	for cid in COURSES:
		var c: Dictionary = COURSES[cid]
		var reason2 := ""
		if not s.is_empty() and float(s.age) < float(c.min_age):
			reason2 = "Mindestalter %d Jahre" % int(c.min_age)
		out.append({"id": cid, "name": c.name, "type": "course", "fee": float(c.fee), "hours": float(c.hours),
			"eligible": reason2 == "", "reason": reason2, "skills": c.skills})
	return out


func _program_block(world, s: Dictionary, program: String) -> String:
	var pr: Dictionary = PROGRAMS.get(program, {})
	if s.is_empty():
		return "Unbekannt"
	if float(s.age) < float(pr.min_age):
		return "Mindestalter %d Jahre" % int(pr.min_age)
	if Occupations.edu_rank(s.education) < Occupations.edu_rank(pr.requires):
		return "Voraussetzung: %s" % Occupations.edu_label(pr.requires)
	if Occupations.edu_rank(s.education) >= Occupations.edu_rank(pr.grants) and program != "vocational":
		return "Abschluss bereits vorhanden"
	return ""


# ============================================================ Einschreiben / Lernen

## Einschreibung in ein Programm ("vocational", "bachelor", ...), einen Kurs
## (COURSES) oder "school". Rueckgabe {ok, reason}
func enroll(world, kind: String, id: int, program: String, field := "") -> Dictionary:
	var s := _get(world, kind, id)
	if s.is_empty():
		return {"ok": false, "reason": "Unbekannte Person."}
	var key := _key(kind, id)
	if enrollments.has(key) and enrollments[key].program != "school" and program != "school":
		return {"ok": false, "reason": "Du bist bereits in „%s“ eingeschrieben." % _name(enrollments[key].program)}
	var dpy: float = world.rules.f("days_per_year")
	if COURSES.has(program):
		var c: Dictionary = COURSES[program]
		if float(s.age) < float(c.min_age):
			return {"ok": false, "reason": "Mindestalter %d Jahre." % int(c.min_age)}
		var gov = world.government
		var to: int = gov.dept_accounts.get("education", gov.account) if gov else -1
		if to >= 0 and not world.ledger.transfer(int(s.account), to, float(c.fee), "Kursgebühr: %s" % c.name):
			return {"ok": false, "reason": "Nicht genug Geld für die Kursgebühr (%s)." % Economy.fmt_money(float(c.fee))}
		if gov:
			gov.record_revenue("fees", float(c.fee))
		var ck := key + ":course"
		enrollments[ck] = {"program": program, "field": "", "hours": 0.0, "required": float(c.hours),
			"start_day": world.get_day(), "min_days": 0, "fee_week": 0.0, "unpaid_weeks": 0}
		return {"ok": true, "reason": ""}
	if not PROGRAMS.has(program):
		return {"ok": false, "reason": "Unbekannter Bildungsgang."}
	var pr: Dictionary = PROGRAMS[program]
	if program != "school":
		var block := _program_block(world, s, program)
		if block != "":
			return {"ok": false, "reason": block + "."}
		if field == "" and not pr.fields.is_empty():
			field = pr.fields[rng.randi() % pr.fields.size()]
		if field != "" and not pr.fields.has(field):
			return {"ok": false, "reason": "Diese Fachrichtung gibt es hier nicht."}
	var required := program_hours(program, dpy)
	var min_days := int(float(pr.years) * dpy * 0.6)
	if program == "school":
		# Schule: Fortschritt ab dem aktuellen Alter
		var age: float = float(s.age)
		required = maxf(0.0, (16.0 - age)) * dpy * 5.0 / 7.0 * 5.0
		min_days = 0
	enrollments[key] = {"program": program, "field": field, "hours": 0.0, "required": required,
		"start_day": world.get_day(), "min_days": min_days, "fee_week": float(pr.fee_week), "unpaid_weeks": 0}
	return {"ok": true, "reason": ""}


func _name(program: String) -> String:
	if PROGRAMS.has(program):
		return PROGRAMS[program].name
	return COURSES.get(program, {}).get("name", program)


## Anwesenheit (Unterricht/Vorlesung/Kurs). Rueckgabe {ok, reason, progress, completed, degree}
func attend(world, kind: String, id: int, hours: float) -> Dictionary:
	var key := _key(kind, id)
	var s := _get(world, kind, id)
	if s.is_empty():
		return {"ok": false, "reason": "Unbekannte Person.", "progress": 0.0, "completed": false}
	var ek := key + ":course" if enrollments.has(key + ":course") else key
	if not enrollments.has(ek):
		return {"ok": false, "reason": "Du bist nirgends eingeschrieben.", "progress": 0.0, "completed": false}
	var e: Dictionary = enrollments[ek]
	var quality: float = clampf(world.government.modifier("education_quality"), 0.6, 1.4) if world.government else 1.0
	e.hours = float(e.hours) + hours
	var completed := false
	var degree := ""
	if COURSES.has(e.program):
		var c: Dictionary = COURSES[e.program]
		for sk in c.skills:
			Skills.gain(s.skills, sk, float(c.skills[sk]) * hours / float(c.hours) * quality)
		if float(e.hours) >= float(e.required) - 0.01:
			enrollments.erase(ek)
			completed = true
			degree = e.program
			if kind == "player":
				world.notify("Kurs abgeschlossen: %s" % c.name, "success")
				if e.program == "driving_license" and world.player:
					world.player.licenses["car"] = true
	else:
		var pr: Dictionary = PROGRAMS[e.program]
		if e.field != "":
			var fs: Dictionary = FIELDS[e.field].skills
			for sk in fs:
				Skills.gain(s.skills, sk, 0.9 * float(fs[sk]) * hours * quality)
		elif e.program == "school":
			Skills.gain(s.skills, "logic", 0.25 * hours * quality)
			Skills.gain(s.skills, "writing", 0.2 * hours * quality)
			Skills.gain(s.skills, "creativity", 0.1 * hours * quality)
		# Ausbildungsverguetung / Doktorandenstelle
		if float(pr.stipend_hour) > 0.0 and world.government:
			var gov = world.government
			var acc: int = gov.dept_accounts.get("education", gov.account)
			gov.cover(world, acc, float(pr.stipend_hour) * hours)
			world.ledger.transfer_partial(acc, int(s.account), float(pr.stipend_hour) * hours * world.rules.f("wage_level"),
				"Ausbildungsvergütung" if e.program == "vocational" else "Doktorandenstelle")
		var days_ok: bool = world.get_day() - int(e.start_day) >= int(e.min_days)
		if float(e.hours) >= float(e.required) and days_ok and e.program != "school":
			enrollments.erase(ek)
			_set_education(world, kind, id, pr.grants)
			completed = true
			degree = pr.grants
			if kind == "player":
				world.notify("Abschluss erreicht: %s (%s)" % [Occupations.edu_label(pr.grants), FIELDS.get(e.field, {}).get("label", "")], "success")
	var prog := clampf(float(e.hours) / maxf(1.0, float(e.required)), 0.0, 1.0)
	return {"ok": true, "reason": "", "progress": 1.0 if completed else prog, "completed": completed, "degree": degree}


## Schulabschluss (von Population/PlayerLife beim Erreichen des Alters).
func graduate_school(world, kind: String, id: int) -> void:
	enrollments.erase(_key(kind, id))
	_set_education(world, kind, id, "school")


func drop_out(world, kind: String, id: int) -> void:
	enrollments.erase(_key(kind, id))
	enrollments.erase(_key(kind, id) + ":course")


func on_day(world, day: int) -> void:
	if day % 7 != 0:
		return
	var gov = world.government
	if gov == null:
		return
	for key in enrollments.keys():
		var e: Dictionary = enrollments[key]
		if float(e.fee_week) <= 0.0:
			continue
		var parts: PackedStringArray = key.split(":")
		var s := _get(world, parts[0], int(parts[1]))
		if s.is_empty():
			enrollments.erase(key)
			continue
		if world.ledger.transfer(int(s.account), gov.dept_accounts.get("education", gov.account), float(e.fee_week), "Semesterbeitrag"):
			gov.record_revenue("fees", float(e.fee_week))
			e.unpaid_weeks = 0
		else:
			e.unpaid_weeks = int(e.unpaid_weeks) + 1
			if parts[0] == "player":
				world.notify("Semesterbeitrag nicht bezahlt.", "warning")
			if int(e.unpaid_weeks) >= 3:
				enrollments.erase(key)
				if parts[0] == "player":
					world.notify("Du wurdest exmatrikuliert (Beiträge nicht bezahlt).", "danger")


## Entfernt Einschreibungen einer Person (Tod/Wegzug).
func forget(kind: String, id: int) -> void:
	enrollments.erase(_key(kind, id))
	enrollments.erase(_key(kind, id) + ":course")


## Uebertraegt Einschreibungen (Erbe uebernimmt Spielfigur).
func rekey(from_kind: String, from_id: int, to_kind: String, to_id: int) -> void:
	for suffix in ["", ":course"]:
		var k := _key(from_kind, from_id) + suffix
		if enrollments.has(k):
			enrollments[_key(to_kind, to_id) + suffix] = enrollments[k]
			enrollments.erase(k)


func to_save() -> Dictionary:
	return {"enrollments": enrollments.duplicate(true), "rng": rng.state}


func from_save(world, d: Dictionary) -> void:
	rng.seed = hash("education:%d" % world.seed)
	enrollments = d.get("enrollments", {}).duplicate(true)
	if d.has("rng"):
		rng.state = int(d.rng)
	_index(world)
