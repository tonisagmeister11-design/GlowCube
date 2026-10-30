class_name NpcSchedule
extends RefCounted
## Tagesplanung der NPCs. Ein Plan beginnt mit dem Einschlafen und endet mit
## dem naechsten Einschlafen. Eintraege: [start_min, activity, building, pos, mode, purpose]
## activity: sleep|home|commute|work|school|shop|leisure|wander|hospital
## Ein commute-Eintrag fuehrt zu building/pos und endet mit dem naechsten Eintrag.

const WALK_M_PER_MIN := 78.0
const BUS_M_PER_MIN := 330.0
const CAR_M_PER_MIN := 420.0


## Reisezeit in Minuten und Verkehrsmittel. -> [minutes, mode]
static func travel(a: Vector2, b: Vector2, has_car: bool, can_bus: bool) -> Array:
	var d := (absf(a.x - b.x) + absf(a.y - b.y)) * 1.05
	if d < 1200.0 or not (has_car or can_bus):
		return [maxf(2.0, d / WALK_M_PER_MIN + 1.0), "walk"]
	if has_car:
		return [d / CAR_M_PER_MIN + 4.0, "car"]
	return [d / BUS_M_PER_MIN + 7.0, "bus"]


static func _wd(day: int) -> int:
	return day % 7


## Verpflichtungen an Tag D: [{start, end, act, building, pos}] nach Start sortiert.
static func obligations(world, pop, p: Dictionary, D: int, age: float) -> Array:
	var out := []
	var jobs = world.jobs
	var city: CityModel = world.city
	if int(p.get("hospital_until", -1)) > D * 1440:
		var hb: int = int(p.get("hospital_building", -1))
		if hb >= 0:
			return [{"start": D * 1440.0 + 9 * 60.0, "end": D * 1440.0 + 20 * 60.0, "act": "hospital", "building": hb,
				"pos": city.buildings[hb].sidewalk_point}]
	if jobs and not p.job.is_empty():
		var pos: Dictionary = jobs.positions.get(int(p.job.position), {})
		if not pos.is_empty() and jobs.is_workday(pos, D):
			var s := D * 1440.0 + float(pos.shift_start) * 60.0
			out.append({"start": s, "end": s + float(pos.shift_hours) * 60.0, "act": "work", "building": int(pos.building),
				"pos": city.buildings[int(pos.building)].sidewalk_point})
	if int(p.school) >= 0 and _wd(D) < 5:
		var b: Dictionary = city.buildings[int(p.school)]
		var s0 := 8.0
		var e0 := 13.0
		if b.kind == "university":
			s0 = 9.0
			e0 = 15.0
		elif age >= 10.0:
			e0 = 15.0
		var so := {"start": D * 1440.0 + s0 * 60.0, "end": D * 1440.0 + e0 * 60.0, "act": "school",
			"building": int(p.school), "pos": b.sidewalk_point}
		var clash := false
		for o in out:
			if so.start < o.end and o.start < so.end:
				clash = true
		if not clash:
			out.append(so)
	out.sort_custom(func(a, c): return a.start < c.start)
	return out


static func make_plan(world, pop, p: Dictionary, t0: float) -> Array:
	var rng: RandomNumberGenerator = pop.rng
	var h: Dictionary = pop.households.get(int(p.household), {})
	var home_b: int = pop.home_building(p)
	var home_pos: Vector2 = pop.home_pos(world, p)
	var age: float = world.age_years(float(p.birth))
	var has_car: bool = h.get("car", false) and age >= 18.0
	var can_bus: bool = world.ledger.balance(int(h.get("account", -1))) > 10.0
	var plan := [[t0, "sleep", home_b, home_pos, "", ""]]
	var hod := fposmod(t0, 1440.0) / 60.0
	var D := int(floor(t0 / 1440.0)) + (1 if hod >= 12.0 else 0)
	var sl: Array = p.sleep
	var obs := obligations(world, pop, p, D, age)
	var free_day := obs.is_empty()
	var wake := D * 1440.0 + float(sl[1]) * 60.0 + rng.randf_range(-20.0, 20.0)
	if free_day and age >= 12.0:
		wake += rng.randf_range(45.0, 110.0)
	if not obs.is_empty():
		var first: Dictionary = obs[0]
		var tr := travel(home_pos, first.pos, has_car, can_bus)
		var need: float = float(first.start) - float(tr[0]) - 40.0
		if need < wake:
			wake = need
		if float(first.start) - t0 > 16.0 * 60.0:
			wake = t0 + 8.0 * 60.0
	wake = clampf(wake, t0 + 5.0 * 60.0, t0 + 12.0 * 60.0)
	plan.append([wake, "home", home_b, home_pos, "", "wake"])
	var cur_t := wake + 25.0
	var cur_pos := home_pos
	var cur_b := home_b
	for ob in obs:
		var tr2 := travel(cur_pos, ob.pos, has_car, can_bus)
		var dep := maxf(float(ob.start) - float(tr2[0]), cur_t)
		if cur_b != int(ob.building):
			plan.append([dep, "commute", ob.building, ob.pos, tr2[1], ""])
			dep += float(tr2[0])
		plan.append([dep, ob.act, ob.building, ob.pos, "", ""])
		cur_t = maxf(float(ob.end), dep + 20.0)
		cur_pos = ob.pos
		cur_b = int(ob.building)
	if free_day:
		cur_t = wake + rng.randf_range(50.0, 140.0)
	var bed := D * 1440.0 + float(sl[0]) * 60.0 + rng.randf_range(-15.0, 15.0)
	var errands: Array = pop.plan_errands(world, p, h, age, cur_t, bed, free_day, D)
	for er in errands:
		var tr3 := travel(cur_pos, er.pos, has_car, can_bus)
		var back := travel(er.pos, home_pos, has_car, can_bus)
		if cur_t + float(tr3[0]) + float(er.duration) + float(back[0]) > bed - 20.0:
			continue
		if er.has("open_check") and not world.economy.is_open_at(int(er.open_check), fposmod(cur_t + float(tr3[0]) + 5.0, 1440.0) / 60.0):
			continue
		plan.append([cur_t, "commute", er.building, er.pos, tr3[1], er.purpose])
		cur_t += float(tr3[0])
		plan.append([cur_t, er.act, er.building, er.pos, "", er.purpose])
		cur_t += float(er.duration)
		cur_pos = er.pos
		cur_b = int(er.building)
	if cur_b != home_b or cur_pos != home_pos:
		var tr4 := travel(cur_pos, home_pos, has_car, can_bus)
		plan.append([cur_t, "commute", home_b, home_pos, tr4[1], ""])
		cur_t += float(tr4[0])
		plan.append([cur_t, "home", home_b, home_pos, "", "evening"])
	elif plan[plan.size() - 1][1] != "home":
		plan.append([cur_t, "home", home_b, home_pos, "", "evening"])
	bed = maxf(bed, cur_t + 30.0)
	plan.append([bed, "sleep", home_b, home_pos, "", ""])
	return plan
