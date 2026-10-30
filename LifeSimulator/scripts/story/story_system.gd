class_name StorySystem
extends RefCounted
## Fortschritt des Story-Modus (reine Daten, laeuft auch ohne Szene).
##
## Kapitel werden freigeschaltet, sobald ihre Bedingungen erfuellt sind; die
## Missionen eines Kapitels werden der Reihe nach angeboten. Die sichtbare
## Durchfuehrung in der Welt uebernimmt StoryRunner.

const PLAYER_ID := 0

var world                              # WorldState (ohne Typ: Zyklus vermeiden)
var unlocked: Array[String] = []       # freigeschaltete Kapitel
var completed: Array[String] = []      # abgeschlossene Missionen
var failed: Dictionary = {}            # mission_id -> Anzahl Fehlversuche
var choices: Dictionary = {}           # mission_id -> gewaehlte Option
var active := ""                       # laufende Mission
var step := 0                          # aktueller Schritt
var step_data: Dictionary = {}         # Laufzeitdaten des Schritts (Ort, Ziel ...)
var offered := ""                      # angebotene, noch nicht angenommene Mission
var rng := RandomNumberGenerator.new()


func setup(w) -> void:
	world = w
	rng.seed = hash("story:%d" % w.seed)
	_check_unlocks()


# ============================================================ Freischaltung

func _gang() -> Dictionary:
	if world.crime == null or world.crime.gangs == null:
		return {}
	var g = world.crime.gangs
	if g.has_method("player_overview"):
		var o = g.player_overview(world)
		if typeof(o) == TYPE_DICTIONARY:
			return o
	return {}


func _gang_rank() -> int:
	var o := _gang()
	return int(o.get("rank", -1)) if not o.is_empty() else -1


func in_gang() -> bool:
	return _gang_rank() >= 0


func _meets(unlock: Dictionary) -> bool:
	if bool(unlock.get("gang", false)) and not in_gang():
		return false
	if unlock.has("gang_rank") and _gang_rank() < int(unlock.gang_rank):
		return false
	for m in unlock.get("missions_done", []):
		if not completed.has(str(m)):
			return false
	return true


func _check_unlocks() -> void:
	for c in StoryCatalog.CHAPTERS:
		if unlocked.has(str(c.id)):
			continue
		if _meets(c.unlock):
			unlocked.append(str(c.id))
			world.notify("Neues Kapitel: %s" % str(c.title), "success")
			world.post_news("Leben", "Neues Kapitel der Geschichte: %s" % str(c.title))
			world.emit_event("story_chapter_unlocked", [c.duplicate()])


## Naechste spielbare Mission eines freigeschalteten Kapitels.
func next_mission() -> String:
	for c in StoryCatalog.CHAPTERS:
		if not unlocked.has(str(c.id)):
			continue
		for mid in c.missions:
			if not completed.has(str(mid)):
				return str(mid)
	return ""


func chapter_progress() -> Dictionary:
	var done := 0
	var total := 0
	for c in StoryCatalog.CHAPTERS:
		for m in c.missions:
			total += 1
			if completed.has(str(m)):
				done += 1
	return {"done": done, "total": total, "chapters": unlocked.size()}


# ============================================================ Ablauf

func is_active() -> bool:
	return active != ""


func mission_def(id := "") -> Dictionary:
	return StoryCatalog.mission(id if id != "" else active)


func current_step() -> Dictionary:
	var d := mission_def()
	if d.is_empty():
		return {}
	var steps: Array = d.steps
	return steps[step].duplicate(true) if step < steps.size() else {}


func steps_total() -> int:
	var d := mission_def()
	return (d.steps as Array).size() if not d.is_empty() else 0


## Bietet die naechste Mission an (fuer Auftraggeber-Gespraech und Telefon).
func offer_next() -> Dictionary:
	if is_active():
		return {}
	var mid := next_mission()
	if mid == "":
		return {}
	offered = mid
	return StoryCatalog.mission(mid)


func accept(mid := "") -> Dictionary:
	if is_active():
		return {"ok": false, "reason": "Du hast schon einen Auftrag laufen."}
	var id := mid if mid != "" else (offered if offered != "" else next_mission())
	if id == "" or StoryCatalog.mission(id).is_empty():
		return {"ok": false, "reason": "Im Moment gibt es nichts zu tun."}
	if completed.has(id):
		return {"ok": false, "reason": "Das hast du bereits erledigt."}
	active = id
	step = 0
	step_data = {}
	offered = ""
	var d := StoryCatalog.mission(id)
	world.notify("Auftrag angenommen: %s" % str(d.title), "info")
	world.emit_event("story_mission_started", [id])
	return {"ok": true, "mission": d, "id": id}


## Schliesst den aktuellen Schritt ab und geht weiter. Gibt zurueck, ob die
## Mission damit beendet ist.
func advance_step(data := {}) -> Dictionary:
	if not is_active():
		return {"ok": false, "reason": "Kein Auftrag aktiv."}
	step += 1
	step_data = {}
	world.emit_event("story_step_changed", [active, step])
	if step >= steps_total():
		return complete()
	return {"ok": true, "done": false, "step": step}


func set_choice(option_id: String) -> void:
	choices[active] = option_id


func complete() -> Dictionary:
	if not is_active():
		return {"ok": false, "reason": "Kein Auftrag aktiv."}
	var id := active
	var d := StoryCatalog.mission(id)
	var reward: Dictionary = d.get("reward", {})
	var paid := 0.0
	if float(reward.get("money", 0.0)) > 0.0:
		paid = _pay_reward(float(reward.money), str(d.title))
	if float(reward.get("rep", 0.0)) > 0.0:
		_add_rep(float(reward.rep))
	if reward.has("skill"):
		var sk: Array = reward.skill
		if world.player and "skills" in world.player:
			Skills.gain(world.player.skills, str(sk[0]), float(sk[1]))
	completed.append(id)
	active = ""
	step = 0
	step_data = {}
	world.notify("Auftrag erledigt: %s" % str(d.title), "success")
	world.post_news("Leben", "%s hat einen Auftrag abgeschlossen." % str(world.player.name) if world.player else "Auftrag abgeschlossen.")
	world.emit_event("story_mission_completed", [id, paid])
	_check_unlocks()
	return {"ok": true, "done": true, "money": paid, "mission": d}


func fail(reason := "") -> Dictionary:
	if not is_active():
		return {"ok": false}
	var id := active
	var d := StoryCatalog.mission(id)
	failed[id] = int(failed.get(id, 0)) + 1
	active = ""
	step = 0
	step_data = {}
	var txt: String = reason if reason != "" else str(d.get("fail_text", "Der Auftrag ist gescheitert."))
	world.notify(txt, "danger")
	world.emit_event("story_mission_failed", [id, txt])
	return {"ok": true, "failed": true, "text": txt}


## Belohnung kommt aus der Bandenkasse, nicht aus dem Nichts. Reicht sie nicht,
## wird der Rest als Schuld der Bande vermerkt (und spaeter gezahlt).
func _pay_reward(amount: float, reason: String) -> float:
	var led = world.ledger
	var pacc: int = led.player_account
	if pacc < 0:
		return 0.0
	var gacc := -1
	var o := _gang()
	if not o.is_empty() and o.has("account"):
		gacc = int(o.account)
	elif world.crime and world.crime.gangs and "gangs" in world.crime.gangs:
		for gid in world.crime.gangs.gangs:
			var g: Dictionary = world.crime.gangs.gangs[gid]
			if int(g.get("id", -1)) == int(o.get("gang", -1)):
				gacc = int(g.get("account", -1))
	if gacc >= 0 and led.has_account(gacc):
		return led.transfer_partial(gacc, pacc, amount, "Auftrag: %s" % reason)
	# Ohne Bandenkasse: Geld kommt von aussen in die Stadt (Auftraggeber)
	led.mint(pacc, amount, "Auftrag: %s" % reason)
	return amount


func _add_rep(amount: float) -> void:
	var g = world.crime.gangs if world.crime else null
	if g == null:
		return
	if g.has_method("add_reputation"):
		g.add_reputation(world, amount)
	elif "player_rep" in g:
		g.player_rep = float(g.player_rep) + amount


# ============================================================ Speichern

func to_save() -> Dictionary:
	return {"unlocked": unlocked, "completed": completed, "failed": failed, "choices": choices,
		"active": active, "step": step, "offered": offered}


func from_save(w, d: Dictionary) -> void:
	world = w
	rng.seed = hash("story:%d" % w.seed)
	unlocked.clear()
	for u in d.get("unlocked", []):
		unlocked.append(str(u))
	completed.clear()
	for c in d.get("completed", []):
		completed.append(str(c))
	failed = d.get("failed", {})
	choices = d.get("choices", {})
	active = str(d.get("active", ""))
	step = int(d.get("step", 0))
	offered = str(d.get("offered", ""))


func post_load(_w) -> void:
	_check_unlocks()


# ============================================================ Takt

func on_hour(_w, _day: int, _hour: int) -> void:
	_check_unlocks()
