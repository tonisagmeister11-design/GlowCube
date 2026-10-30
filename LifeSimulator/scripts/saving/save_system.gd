class_name SaveSystem
extends RefCounted
## Versionierte Spielstaende in user://saves/.
##   <slot>.sav   - komprimierte Binaerdaten (FileAccess.store_var, ohne Objekte)
##   <slot>.meta  - kleine Vorschau (Name, Alter, Spielzeit, Zeitpunkt) fuer die Liste
##   <slot>.bak   - vorheriger Stand, wird bei defekter Datei automatisch versucht
## Schreiben erfolgt atomar ueber eine temporaere Datei.

const DIR := "user://saves"
const MAGIC := "LIFESIM"
const AUTOSAVE_SLOT := "autosave"
const QUICKSAVE_SLOT := "quicksave"


static func _ensure_dir() -> void:
	DirAccess.make_dir_recursive_absolute(DIR)


static func slot_path(slot: String, ext := "sav") -> String:
	return "%s/%s.%s" % [DIR, _sanitize(slot), ext]


static func _sanitize(slot: String) -> String:
	var out := ""
	for ch in slot:
		if ch.is_valid_identifier() or ch in "-_" or (ch >= "0" and ch <= "9"):
			out += ch
		else:
			out += "_"
	return out if out != "" else "slot"


## Speichert die Welt. meta: zusaetzliche Vorschaudaten (character_name, age ...).
## Rueckgabe: OK oder Fehlercode.
static func save(slot: String, world: WorldState, meta := {}) -> int:
	_ensure_dir()
	var data := world.to_save()
	var path := slot_path(slot)
	var tmp := path + ".tmp"
	var f := FileAccess.open_compressed(tmp, FileAccess.WRITE, FileAccess.COMPRESSION_ZSTD)
	if f == null:
		return FileAccess.get_open_error()
	f.store_pascal_string(MAGIC)
	f.store_32(WorldState.SAVE_VERSION)
	f.store_var(data, false)
	f.close()
	# Kontrolllesen, bevor die alte Datei ersetzt wird
	var check := _read_file(tmp)
	if check.is_empty():
		DirAccess.remove_absolute(tmp)
		return ERR_FILE_CORRUPT
	if FileAccess.file_exists(path):
		DirAccess.remove_absolute(path + ".bak")
		DirAccess.rename_absolute(path, path.get_basename() + ".bak")
	var err := DirAccess.rename_absolute(tmp, path)
	if err != OK:
		return err
	var m := meta.duplicate()
	m["slot"] = slot
	m["saved_unix"] = Time.get_unix_time_from_system()
	m["saved_text"] = Time.get_datetime_string_from_system(false, true)
	m["play_time"] = world.play_time_seconds
	m["mode"] = world.rules.mode
	m["day"] = world.get_day() + 1
	m["version"] = WorldState.SAVE_VERSION
	var mf := FileAccess.open(slot_path(slot, "meta"), FileAccess.WRITE)
	if mf:
		mf.store_string(JSON.stringify(m))
	return OK


static func _read_file(path: String) -> Dictionary:
	if not FileAccess.file_exists(path):
		return {}
	var f := FileAccess.open_compressed(path, FileAccess.READ, FileAccess.COMPRESSION_ZSTD)
	if f == null:
		return {}
	if f.get_length() < 8:
		return {}
	var magic := f.get_pascal_string()
	if magic != MAGIC:
		return {}
	var version := f.get_32()
	var data = f.get_var(false)
	if typeof(data) != TYPE_DICTIONARY:
		return {}
	data["_file_version"] = version
	return data


## Laedt Rohdaten eines Slots. Rueckgabe {ok, data, error, from_backup}.
static func load_data(slot: String) -> Dictionary:
	var path := slot_path(slot)
	var data := _read_file(path)
	var from_backup := false
	if data.is_empty():
		data = _read_file(path.get_basename() + ".bak")
		from_backup = not data.is_empty()
	if data.is_empty():
		return {"ok": false, "error": "Der Spielstand ist beschädigt oder nicht lesbar."}
	var v := int(data.get("version", 0))
	if v > WorldState.SAVE_VERSION:
		return {"ok": false, "error": "Der Spielstand stammt aus einer neueren Spielversion (%d)." % v}
	data = migrate(data)
	if not data.has("seed") or not data.has("systems"):
		return {"ok": false, "error": "Dem Spielstand fehlen wichtige Daten."}
	return {"ok": true, "data": data, "from_backup": from_backup}


## Hebt aeltere Speicherformate auf die aktuelle Version an.
static func migrate(data: Dictionary) -> Dictionary:
	var v := int(data.get("version", 0))
	if v < 1:
		data["version"] = 1
	return data


static func list_saves() -> Array:
	_ensure_dir()
	var out := []
	var dir := DirAccess.open(DIR)
	if dir == null:
		return out
	for f in dir.get_files():
		if not f.ends_with(".meta"):
			continue
		var txt := FileAccess.get_file_as_string(DIR + "/" + f)
		var parsed = JSON.parse_string(txt)
		if typeof(parsed) != TYPE_DICTIONARY:
			continue
		if not FileAccess.file_exists(slot_path(f.get_basename())):
			continue
		out.append(parsed)
	out.sort_custom(func(a, b): return float(a.get("saved_unix", 0)) > float(b.get("saved_unix", 0)))
	return out


static func delete(slot: String) -> void:
	for ext in ["sav", "meta", "bak"]:
		var p := slot_path(slot, ext)
		if FileAccess.file_exists(p):
			DirAccess.remove_absolute(p)


static func exists(slot: String) -> bool:
	return FileAccess.file_exists(slot_path(slot))


static func latest_slot() -> String:
	var l := list_saves()
	return l[0].slot if not l.is_empty() else ""
