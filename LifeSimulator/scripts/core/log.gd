extends Node
## Zentrales Logging. Schreibt nach user://logs/life_sim.log und haelt einen
## Ringpuffer fuer das Debug-Overlay (F3). Niemals API-Keys loggen.

enum Level { DEBUG, INFO, WARN, ERROR }

const MAX_LINES := 400
const LEVEL_NAMES := ["DEBUG", "INFO", "WARN", "ERROR"]

var min_level: int = Level.INFO
var lines: Array[String] = []
var error_count := 0
var warn_count := 0
var _file: FileAccess

signal line_added(text: String, level: int)


func _ready() -> void:
	DirAccess.make_dir_recursive_absolute("user://logs")
	var path := "user://logs/life_sim.log"
	# Vorherigen Lauf aufbewahren, damit Absturzursachen nachvollziehbar bleiben.
	if FileAccess.file_exists(path):
		DirAccess.rename_absolute(path, "user://logs/life_sim.previous.log")
	_file = FileAccess.open(path, FileAccess.WRITE)
	info("core", "Life Simulator %s gestartet (Godot %s, %s)" % [
		ProjectSettings.get_setting("application/config/version", "?"),
		Engine.get_version_info().string, OS.get_name()])


func debug(tag: String, msg: String) -> void:
	_write(Level.DEBUG, tag, msg)


func info(tag: String, msg: String) -> void:
	_write(Level.INFO, tag, msg)


func warn(tag: String, msg: String) -> void:
	warn_count += 1
	_write(Level.WARN, tag, msg)


func error(tag: String, msg: String) -> void:
	error_count += 1
	_write(Level.ERROR, tag, msg)


func _write(level: int, tag: String, msg: String) -> void:
	if level < min_level:
		return
	var t := Time.get_time_string_from_system()
	var text := "%s [%s] %s: %s" % [t, LEVEL_NAMES[level], tag, msg]
	lines.append(text)
	if lines.size() > MAX_LINES:
		lines.remove_at(0)
	if _file:
		_file.store_line(text)
		if level >= Level.WARN:
			_file.flush()
	if level == Level.ERROR:
		push_error(text)
	elif level == Level.WARN:
		push_warning(text)
	else:
		print(text)
	line_added.emit(text, level)


func _exit_tree() -> void:
	if _file:
		_file.flush()
