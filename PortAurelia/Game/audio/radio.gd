class_name CarRadio
extends Node
## Car radio + police-chase track (the player's songs, built into the game):
##  * getting into a car starts a random song; getting back into the same car resumes it
##    where it stopped; getting out cuts the music off at once
##  * "+" / "-" radio volume, "." next song, "," radio on/off
##  * the chase track (FUNK IRREGULAR, from 0:10) plays when the police are right on you on
##    foot and stops once you have escaped - it never plays on the radio

const TRACKS := [
	["Rolling With The Good Times", "res://Audio/Radio/rolling_with_the_good_times.ogg"],
	["Radio Track 2", "res://Audio/Radio/track_2.ogg"],
	["Radio Track 3", "res://Audio/Radio/track_3.ogg"],
]
const CHASE := "res://Audio/Chase/funk_irregular.ogg"
const CHASE_START := 10.0

var radio: AudioStreamPlayer
var chase: AudioStreamPlayer
var enabled := true
var _track := -1
var _vehicle_id := 0           # instance id of the car the radio last played in
var _resume := {}              # vehicle instance id -> [track, position]
var _chase_fade: Tween
var _streams := {}


func _ready() -> void:
	name = "CarRadio"
	process_mode = Node.PROCESS_MODE_ALWAYS
	radio = AudioStreamPlayer.new()
	radio.bus = "Radio"
	add_child(radio)
	radio.finished.connect(_next_song)
	chase = AudioStreamPlayer.new()
	chase.bus = "Chase"   # own, very loud bus
	add_child(chase)
	Events.player_entered_vehicle.connect(_on_enter)
	Events.player_exited_vehicle.connect(_on_exit)


func _stream(path: String) -> AudioStream:
	if not _streams.has(path):
		_streams[path] = load(path) if ResourceLoader.exists(path) else null
	return _streams[path]


# ------------------------------------------------------------------ radio
func _on_enter(v: Node) -> void:
	if not enabled or v == null:
		return
	var id := v.get_instance_id()
	if _resume.has(id):
		var r: Array = _resume[id]
		_play(int(r[0]), float(r[1]))
	else:
		var t := randi() % TRACKS.size()
		if TRACKS.size() > 1 and t == _track:
			t = (t + 1) % TRACKS.size()
		_play(t, 0.0)
		Events.notify.emit("♪ Radio: %s" % TRACKS[t][0], 2.5)
	_vehicle_id = id


func _on_exit(_v: Node = null) -> void:
	if radio.playing:
		_resume[_vehicle_id] = [_track, radio.get_playback_position()]
	radio.stop()   # abrupt, like turning the key


func _play(t: int, pos: float) -> void:
	var s := _stream(TRACKS[t][1])
	if s == null:
		return
	_track = t
	radio.stream = s
	radio.play(pos)
	radio.stream_paused = chase.playing   # the chase track has priority


func _next_song() -> void:
	var p := _player()
	if p == null or not p.is_in_vehicle() or not enabled:
		return
	_play((_track + 1) % TRACKS.size(), 0.0)
	Events.notify.emit("♪ Radio: %s" % TRACKS[_track][0], 2.5)


func _player() -> Player:
	var w := GameWorld.instance
	return w.player as Player if w and is_instance_valid(w) else null


func _unhandled_input(event: InputEvent) -> void:
	var p := _player()
	if p == null or not p.is_in_vehicle():
		return
	if event.is_action_pressed("radio_up"):
		_change_volume(0.1)
	elif event.is_action_pressed("radio_down"):
		_change_volume(-0.1)
	elif event.is_action_pressed("radio_next") and enabled:
		_next_song()
	elif event.is_action_pressed("radio_toggle"):
		enabled = not enabled
		if enabled:
			_on_enter(p.vehicle)
		else:
			_on_exit()
			Events.notify.emit("Radio aus", 1.5)


func _change_volume(d: float) -> void:
	var v := clampf(float(Settings.get_value("audio", "radio")) + d, 0.0, 1.0)
	Settings.set_value("audio", "radio", v, false)
	Settings.apply_audio_only()
	Events.notify.emit("Radio-Lautstärke: %d %%" % int(round(v * 100.0)), 1.2)


func stop() -> void:
	radio.stop()
	_resume.clear()
	stop_chase(true)


# ------------------------------------------------------------------ chase track
func is_chasing() -> bool:
	return chase.playing


func start_chase() -> void:
	if chase.playing:
		return
	var s := _stream(CHASE)
	if s == null:
		return
	if _chase_fade:
		_chase_fade.kill()
	if s is AudioStreamOggVorbis:
		(s as AudioStreamOggVorbis).loop = true              # keeps going until you get away
		(s as AudioStreamOggVorbis).loop_offset = CHASE_START
	chase.stream = s
	chase.volume_db = 0.0
	chase.play(CHASE_START)
	_duck(true)
	# never on top of other music: the car radio pauses while the chase track plays
	radio.stream_paused = true
	if AudioManager.music and AudioManager.music.player:
		AudioManager.music.player.stream_paused = true


func stop_chase(now := false) -> void:
	if not chase.playing:
		_resume_after_chase()
		return
	if now:
		chase.stop()
		_resume_after_chase()
		return
	if _chase_fade:
		_chase_fade.kill()
	_chase_fade = create_tween()
	_chase_fade.tween_property(chase, "volume_db", -40.0, 1.5)
	_chase_fade.tween_callback(func():
		chase.stop()
		_resume_after_chase())


## During a chase engines, traffic and city noise step back so the music is clearly heard.
func _duck(on: bool) -> void:
	for b in ["Vehicles", "Environment"]:
		var idx := AudioServer.get_bus_index(b)
		if idx < 0:
			continue
		var base := linear_to_db(maxf(float(Settings.get_value("audio", String(b).to_lower())), 0.0001))
		AudioServer.set_bus_volume_db(idx, base - (6.0 if on else 0.0))


func _resume_after_chase() -> void:
	_duck(false)
	radio.stream_paused = false
	if AudioManager.music and AudioManager.music.player:
		AudioManager.music.player.stream_paused = false
