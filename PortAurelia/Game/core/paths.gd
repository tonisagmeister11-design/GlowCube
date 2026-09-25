extends Node
## Portable folder layout.
##
## Release build:   <game folder>/StartGame.exe
##                  <game folder>/Saves/   <game folder>/Config/   <game folder>/Audio/Music/
## If the game folder is not writable (e.g. Program Files) everything falls back to
## the user data folder. In the editor the user data folder is used.

var base_dir := ""
var saves_dir := ""
var config_dir := ""
var music_dir := ""
var screenshots_dir := ""
var portable := false


func _enter_tree() -> void:
	var exe_dir := OS.get_executable_path().get_base_dir()
	if not OS.has_feature("editor") and _writable(exe_dir):
		base_dir = exe_dir
		portable = true
	else:
		base_dir = ProjectSettings.globalize_path("user://")
	saves_dir = base_dir.path_join("Saves")
	config_dir = base_dir.path_join("Config")
	screenshots_dir = base_dir.path_join("Screenshots")
	# music: prefer the folder next to the executable even if saves fall back
	music_dir = exe_dir.path_join("Audio").path_join("Music")
	if OS.has_feature("editor") or not DirAccess.dir_exists_absolute(music_dir):
		var alt := base_dir.path_join("Audio").path_join("Music")
		if OS.has_feature("editor"):
			music_dir = alt
	for d in [saves_dir, config_dir]:
		DirAccess.make_dir_recursive_absolute(d)


func _writable(dir: String) -> bool:
	var probe := dir.path_join(".write_test")
	var f := FileAccess.open(probe, FileAccess.WRITE)
	if f == null:
		return false
	f.close()
	DirAccess.remove_absolute(probe)
	return true
