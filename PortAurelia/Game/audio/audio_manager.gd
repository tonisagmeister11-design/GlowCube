extends Node
## Central audio: pooled 3D one-shots, footsteps, weapons, UI, ambience layers
## (city, wind, rain, ocean, birds, night) and the MusicManager.
##
## MUSIC RULE: no music is generated or started automatically. The MusicManager
## only plays the player's own files from <game folder>/Audio/Music/ and only when
## "Musik aktivieren" is switched on in the settings.

const MANIFEST := "res://Audio/SFX/manifest.json"
const POOL_3D := 40
const LOOP_NAMES := ["engine_loop", "engine_sport_loop", "engine_diesel_loop", "engine_bike_loop", "tire_screech",
	"road_noise", "siren_wail", "siren_yelp", "rain_loop", "rain_heavy_loop", "wind_loop", "city_loop", "ocean_loop",
	"birds_loop", "night_loop"]

var streams := {}
var _paths := {}
var _pool: Array[AudioStreamPlayer3D] = []
var _pool_i := 0
var _ui: AudioStreamPlayer
var ambience := {}      # name -> AudioStreamPlayer
var music: MusicManager


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	var m = WorldData._read_json(MANIFEST)
	if m:
		_paths = m
	for i in POOL_3D:
		var p := AudioStreamPlayer3D.new()
		p.bus = "SFX"
		p.unit_size = 8.0
		p.max_distance = 180.0
		p.attenuation_filter_cutoff_hz = 6000.0
		p.attenuation_filter_db = -12.0
		add_child(p)
		_pool.append(p)
	_ui = AudioStreamPlayer.new()
	_ui.bus = "UI"
	add_child(_ui)
	for n in ["city_loop", "wind_loop", "rain_loop", "rain_heavy_loop", "ocean_loop", "birds_loop", "night_loop"]:
		var a := AudioStreamPlayer.new()
		a.bus = "Environment"
		a.stream = get_stream(n)
		a.volume_db = -80.0
		add_child(a)
		ambience[n] = a
	music = MusicManager.new()
	music.name = "MusicManager"
	add_child(music)


func get_stream(n: String) -> AudioStream:
	if streams.has(n):
		return streams[n]
	var p: String = _paths.get(n, "")
	if p == "" or not ResourceLoader.exists(p):
		return null
	var s: AudioStream = load(p)
	if s is AudioStreamWAV and n in LOOP_NAMES:
		var w := s as AudioStreamWAV
		w.loop_mode = AudioStreamWAV.LOOP_FORWARD
		w.loop_begin = 0
		w.loop_end = int(w.get_length() * w.mix_rate)
	streams[n] = s
	return s


func _variant(base: String, count: int) -> String:
	return "%s_%d" % [base, randi() % count]


func play_3d(n: String, pos: Vector3, volume_db := 0.0, pitch := 1.0, bus := "SFX", max_dist := 180.0) -> void:
	var s := get_stream(n)
	if s == null:
		return
	var cam := get_viewport().get_camera_3d()
	if cam and cam.global_position.distance_to(pos) > max_dist:
		return
	var p := _pool[_pool_i]
	_pool_i = (_pool_i + 1) % _pool.size()
	p.stream = s
	p.bus = bus
	p.max_distance = max_dist
	p.volume_db = volume_db
	p.pitch_scale = pitch * randf_range(0.96, 1.04)
	p.global_position = pos
	p.play()


func play_ui(n: String, volume_db := 0.0) -> void:
	var s := get_stream(n)
	if s == null:
		return
	_ui.stream = s
	_ui.volume_db = volume_db
	_ui.play()


func play_footstep(pos: Vector3, surface: String, speed: float) -> void:
	var surf := surface
	match surface:
		"road", "concrete", "sidewalk", "building":
			surf = "concrete" if surface != "road" else "road"
		"grass", "sand", "wood", "metal":
			pass
		_:
			surf = "concrete"
	play_3d(_variant("step_" + surf, 4), pos, -14.0 + minf(speed, 7.0), 1.0, "SFX", 40.0)


func play_weapon(n: String, pos: Vector3, is_player: bool) -> void:
	play_3d(n, pos, 2.0 if is_player else 0.0, 1.0, "Weapons", 400.0)


func play_voice(kind: String, pos: Vector3) -> void:
	var counts := {"pain": 3, "scream": 2, "shout": 1, "death": 1}
	play_3d(_variant(kind, counts.get(kind, 1)), pos, -2.0, randf_range(0.9, 1.15), "Voice", 60.0)


## Ambience mix (0..1 per layer), driven by the world (district, weather, time).
func set_ambience(levels: Dictionary, delta: float) -> void:
	for n in ambience:
		var a: AudioStreamPlayer = ambience[n]
		var target: float = levels.get(n, 0.0)
		var cur := db_to_linear(a.volume_db) if a.volume_db > -79.0 else 0.0
		cur = move_toward(cur, target, delta * 0.5)
		if cur < 0.005:
			if a.playing:
				a.stop()
			a.volume_db = -80.0
		else:
			a.volume_db = linear_to_db(cur)
			if not a.playing:
				a.play(randf() * 3.0)


func stop_ambience() -> void:
	for a in ambience.values():
		(a as AudioStreamPlayer).stop()
		(a as AudioStreamPlayer).volume_db = -80.0
