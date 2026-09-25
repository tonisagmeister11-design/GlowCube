extends Node
## Save slots 1-3 + autosave as JSON files in <game folder>/Saves/.
## A save contains PlayerData plus world state gathered from the running world
## (time, weather, mission progress, vehicles in garages, ...).

signal saved(slot: int)

const VERSION := 1
const AUTOSAVE := -1

var autosave_interval := 180.0
var _auto_timer := 0.0


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS


func _process(delta: float) -> void:
	if Game.state != Game.State.PLAYING:
		return
	_auto_timer += delta
	if _auto_timer >= autosave_interval:
		_auto_timer = 0.0
		autosave()


func _path(slot: int) -> String:
	var name := "autosave.json" if slot == AUTOSAVE else "slot_%d.json" % (slot + 1)
	return Paths.saves_dir.path_join(name)


func autosave() -> bool:
	var w := GameWorld.instance
	if w == null or w.player == null or (w.player as Player).state != Player.State.GROUND:
		return false
	if w.police and int(w.police.get("wanted_level")) > 0:
		return false
	if w.missions and w.missions.call("is_active"):
		return false
	var ok := save_slot(AUTOSAVE)
	if ok:
		Events.notify.emit("Automatisch gespeichert", 2.0)
	return ok


func save_slot(slot: int) -> bool:
	var pd := Game.player_data
	_gather(pd)
	var data := {"version": VERSION, "time": Time.get_datetime_string_from_system(), "slot": slot,
		"district": _district_name(), "player": pd.to_dict(), "settings": Settings.data}
	var f := FileAccess.open(_path(slot), FileAccess.WRITE)
	if f == null:
		push_error("Cannot write save: " + _path(slot))
		return false
	f.store_string(JSON.stringify(data, "\t"))
	f.close()
	saved.emit(slot)
	return true


func _gather(pd: PlayerData) -> void:
	var w := GameWorld.instance
	if w == null:
		return
	var p := w.player as Player
	if p:
		pd.position = p.global_position
		pd.yaw = p.rotation.y
		# inside an interior the player stands below the city: save the door instead
		var im := InteriorManager.get_manager()
		if im and im.is_inside():
			var poi: Dictionary = im.current_poi
			pd.position = (poi["entrance_v"] as Vector3) + (poi["facing_v"] as Vector3) * 2.5 + Vector3.UP * 0.2
			var f: Vector3 = poi["facing_v"]
			pd.yaw = atan2(-f.x, -f.z)
		elif p.is_in_vehicle() and p.vehicle:
			pd.position = (p.vehicle as Vehicle).get_exit_point()
		pd.health = p.health.health
		pd.armor = p.health.armor
		pd.weapons = p.weapons.serialize()
		pd.outfit = p.model.outfit
	if w.day_night:
		pd.hour = float(w.day_night.get("hour"))
	if w.weather:
		pd.weather = String(w.weather.get("state"))
	# world state: game day and where the personal vehicles are parked
	if w.day_night:
		pd.world_state["day"] = int(w.day_night.get("day"))
	var parked := {}
	for v in w.get_tree().get_nodes_in_group("vehicles"):
		var veh := v as Vehicle
		if veh.owned_id != "" and not veh.destroyed:
			var t := veh.global_transform
			parked[veh.owned_id] = [t.origin.x, t.origin.y, t.origin.z, veh.global_rotation.y]
	pd.world_state["vehicles_parked"] = parked


func _district_name() -> String:
	var w := GameWorld.instance
	if w and w.player:
		return String(w.data.district_at(w.player.global_position).get("name", ""))
	return ""


func load_slot(slot: int) -> PlayerData:
	var f := FileAccess.open(_path(slot), FileAccess.READ)
	if f == null:
		return null
	var d = JSON.parse_string(f.get_as_text())
	f.close()
	if not d is Dictionary or not d.has("player"):
		return null
	return PlayerData.from_dict(d["player"])


func slot_info(slot: int) -> Dictionary:
	var f := FileAccess.open(_path(slot), FileAccess.READ)
	if f == null:
		return {}
	var d = JSON.parse_string(f.get_as_text())
	f.close()
	if not d is Dictionary:
		return {}
	var pl: Dictionary = d.get("player", {})
	var st: Dictionary = pl.get("stats", {})
	return {"time": d.get("time", ""), "district": d.get("district", ""), "money": pl.get("money", 0),
		"missions": st.get("missions_passed", 0), "play_time": st.get("play_time", 0.0)}


func has_slot(slot: int) -> bool:
	return FileAccess.file_exists(_path(slot))


## Most recently written slot (autosave included), or -99 if there is none.
func latest_slot() -> int:
	var best := -99
	var best_t := -1
	for s in [AUTOSAVE, 0, 1, 2]:
		var p := _path(s)
		if FileAccess.file_exists(p):
			var t := FileAccess.get_modified_time(p)
			if t > best_t:
				best_t = t
				best = s
	return best


func delete_slot(slot: int) -> void:
	if has_slot(slot):
		DirAccess.remove_absolute(_path(slot))


## F5: quick save into the autosave slot (not during missions or while wanted).
func _unhandled_input(event: InputEvent) -> void:
	if not event.is_action_pressed("quick_save") or Game.state != Game.State.PLAYING:
		return
	var w := GameWorld.instance
	if w == null:
		return
	if w.missions and w.missions.call("is_active"):
		Events.notify.emit("Während einer Mission kann nicht gespeichert werden.", 3.0)
		return
	if w.police and int(w.police.get("wanted_level")) > 0:
		Events.notify.emit("Du wirst gesucht – Speichern nicht möglich.", 3.0)
		return
	if autosave():
		Events.notify.emit("Schnellspeicherung erstellt.", 2.5)
