class_name MusicManager
extends Node
## Plays the player's OWN music files. Disabled by default - nothing plays until
## "Musik aktivieren" is enabled in the settings and files exist in
## <game folder>/Audio/Music/ (.ogg, .mp3, .wav). There is no built-in music.

var playlist: PackedStringArray = []
var index := 0
var player: AudioStreamPlayer
var enabled := false


func _ready() -> void:
	player = AudioStreamPlayer.new()
	player.bus = "Music"
	add_child(player)
	player.finished.connect(_next)
	Settings.changed.connect(_on_settings)
	rescan()
	_on_settings("audio", "music_enabled")


func rescan() -> void:
	playlist.clear()
	var dir := Paths.music_dir
	if not DirAccess.dir_exists_absolute(dir):
		DirAccess.make_dir_recursive_absolute(dir)
		return
	for f in DirAccess.get_files_at(dir):
		var e := f.get_extension().to_lower()
		if e in ["ogg", "mp3", "wav"]:
			playlist.append(dir.path_join(f))
	playlist.sort()


func _on_settings(section: String, key: String) -> void:
	if section != "audio":
		return
	var want: bool = Settings.get_value("audio", "music_enabled")
	if want == enabled:
		return
	enabled = want
	if enabled:
		rescan()
		if playlist.size() > 0:
			index = randi() % playlist.size()
			_play_current()
	else:
		player.stop()


func _play_current() -> void:
	if playlist.is_empty():
		return
	var path := playlist[index % playlist.size()]
	var s := _load(path)
	if s == null:
		_next()
		return
	player.stream = s
	player.play()


func _next() -> void:
	if not enabled or playlist.is_empty():
		return
	index = (index + 1) % playlist.size()
	_play_current()


func skip() -> void:
	_next()


func current_title() -> String:
	if not enabled or playlist.is_empty():
		return ""
	return playlist[index % playlist.size()].get_file().get_basename()


static func _load(path: String) -> AudioStream:
	var e := path.get_extension().to_lower()
	if e == "ogg":
		return AudioStreamOggVorbis.load_from_file(path)
	if e == "mp3":
		var f := FileAccess.open(path, FileAccess.READ)
		if f == null:
			return null
		var s := AudioStreamMP3.new()
		s.data = f.get_buffer(f.get_length())
		return s
	if e == "wav":
		return AudioStreamWAV.load_from_file(path)
	return null
