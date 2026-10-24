extends Node
## Zentrale Audio-Steuerung (Autoload "Audio"): UI-Klaenge, 2D/3D-Effekte aus
## Spieler-Pools, Musik mit Ueberblendung und geschichtetes Ambiente.
##
## Busse Master/Music/SFX/Ambient legt Settings.apply_audio() an (Lautstaerke-
## regler). Zusaetzlich entsteht hier "AmbientOutdoor" (sendet an Ambient) mit
## einem Tiefpass, der in Innenraeumen die Aussengeraeusche daempft.
##
## Klangnamen und Dateien stehen in SoundRegistry. Unbekannte Namen oder
## fehlende Dateien erzeugen genau eine Warnung je Name und werden ignoriert.

const POOL_UI := 6
const POOL_2D := 10
const POOL_3D := 24
const SILENT_DB := -80.0
## Gleicher Klang innerhalb dieser Zeit nicht nochmals (verhindert Phasing).
const MIN_REPEAT := 0.035
const MONEY_COOLDOWN := 0.3
const OUTDOOR_BUS := "AmbientOutdoor"
const MUFFLE_HZ := 650.0

var music_track := ""            # aktueller Musiktitel ("" = still)
var auto_menu_music := true      # Menuemusik beim Start automatisch (nicht headless)
var warning_count := 0           # Anzahl Warnungen wegen fehlender Klaenge

var _rng := RandomNumberGenerator.new()
var _streams: Dictionary = {}    # Schluessel "pfad|loop" -> AudioStream oder null
var _warned: Dictionary = {}     # Klangname/Pfad -> true
var _last_variant: Dictionary = {}
var _last_play: Dictionary = {}
var _ui_pool: Array[AudioStreamPlayer] = []
var _pool_2d: Array[AudioStreamPlayer] = []
var _pool_3d: Array[AudioStreamPlayer3D] = []
var _music: Array[AudioStreamPlayer] = []
var _music_active := 0
var _fades: Dictionary = {}      # instance_id -> {player, gain, target, rate, stop, free, base}
var _amb: Dictionary = {}        # Schicht -> AudioStreamPlayer
var _amb_targets: Dictionary = {}
var _last_balance := NAN
var _money_time := -10.0
var _phone_open := false
var _muffle: AudioEffectLowPassFilter
var _muffled := false


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	_rng.randomize()
	_ensure_buses()
	for i in POOL_UI:
		_ui_pool.append(_make_player("SFX"))
	for i in POOL_2D:
		_pool_2d.append(_make_player("SFX"))
	for i in POOL_3D:
		var p := AudioStreamPlayer3D.new()
		p.bus = "SFX"
		p.attenuation_model = AudioStreamPlayer3D.ATTENUATION_INVERSE_DISTANCE
		p.panning_strength = 0.8
		add_child(p)
		_pool_3d.append(p)
	for i in 2:
		_music.append(_make_player("Music"))
	_connect_events()
	if auto_menu_music and DisplayServer.get_name() != "headless":
		call_deferred("_auto_start")


func _auto_start() -> void:
	var game := get_node_or_null("/root/Game")
	if game == null or game.get("world") == null:
		play_music("menu", 1.5)


func _make_player(bus: String) -> AudioStreamPlayer:
	var p := AudioStreamPlayer.new()
	p.bus = bus
	add_child(p)
	return p


# ============================================================ Busse

func _ensure_buses() -> void:
	for b in ["Music", "SFX", "Ambient"]:
		if AudioServer.get_bus_index(b) < 0:
			var idx := AudioServer.bus_count
			AudioServer.add_bus(idx)
			AudioServer.set_bus_name(idx, b)
			AudioServer.set_bus_send(idx, "Master")
	var oi := AudioServer.get_bus_index(OUTDOOR_BUS)
	if oi < 0:
		oi = AudioServer.bus_count
		AudioServer.add_bus(oi)
		AudioServer.set_bus_name(oi, OUTDOOR_BUS)
		AudioServer.set_bus_send(oi, "Ambient")
		_muffle = AudioEffectLowPassFilter.new()
		_muffle.cutoff_hz = MUFFLE_HZ
		AudioServer.add_bus_effect(oi, _muffle, 0)
		AudioServer.set_bus_effect_enabled(oi, 0, false)


## Daempft Aussengeraeusche (Tiefpass auf AmbientOutdoor), z.B. in Gebaeuden.
func set_outdoor_muffled(on: bool) -> void:
	_muffled = on
	var oi := AudioServer.get_bus_index(OUTDOOR_BUS)
	if oi >= 0 and AudioServer.get_bus_effect_count(oi) > 0:
		AudioServer.set_bus_effect_enabled(oi, 0, on)


func is_outdoor_muffled() -> bool:
	return _muffled


# ============================================================ Laden

func _warn_once(key: String, msg: String) -> void:
	if _warned.has(key):
		return
	_warned[key] = true
	warning_count += 1
	var logger := get_node_or_null("/root/Log")
	if logger:
		logger.warn("audio", msg)
	else:
		push_warning("audio: " + msg)


## Laedt (und cached) einen Stream; bei loop = true wird die Schleife
## sichergestellt, auch wenn die Datei keine Schleifenpunkte enthaelt.
func _load_stream(path: String, loop: bool) -> AudioStream:
	var key := "%s|%s" % [path, loop]
	if _streams.has(key):
		return _streams[key]
	var s: AudioStream = null
	if ResourceLoader.exists(path):
		s = load(path) as AudioStream
	if s == null:
		_warn_once(path, "Audiodatei fehlt oder ist ungültig: %s" % path)
	elif loop:
		s = _as_loop(s)
	_streams[key] = s
	return s


func _as_loop(s: AudioStream) -> AudioStream:
	if s is AudioStreamWAV:
		var w: AudioStreamWAV = s
		if w.loop_mode == AudioStreamWAV.LOOP_DISABLED:
			var c: AudioStreamWAV = w.duplicate()
			c.loop_mode = AudioStreamWAV.LOOP_FORWARD
			c.loop_begin = 0
			c.loop_end = int(round(w.get_length() * w.mix_rate))
			return c
	elif s is AudioStreamOggVorbis:
		if not s.loop:
			var c2: AudioStreamOggVorbis = s.duplicate()
			c2.loop = true
			return c2
	elif s is AudioStreamMP3:
		if not s.loop:
			var c3: AudioStreamMP3 = s.duplicate()
			c3.loop = true
			return c3
	return s


## Waehlt eine Variante (nie zweimal dieselbe hintereinander) und laedt sie.
func _pick_stream(key: String, e: Dictionary) -> AudioStream:
	var files: Array = e.get("files", [])
	if files.is_empty():
		_warn_once(key, "Klang '%s' hat keine Dateien" % key)
		return null
	var idx := 0
	if files.size() > 1:
		idx = _rng.randi_range(0, files.size() - 1)
		if idx == int(_last_variant.get(key, -1)):
			idx = (idx + 1 + _rng.randi_range(0, files.size() - 2)) % files.size()
	_last_variant[key] = idx
	var loop: bool = e.get("loop", false)
	return _load_stream(SoundRegistry.path_of(str(files[idx])), loop)


## Laedt alle Klaenge vorab (vermeidet Ruckler beim ersten Abspielen).
func preload_all() -> void:
	for key in SoundRegistry.SFX.keys():
		var e: Dictionary = SoundRegistry.SFX[key]
		for f in e.files:
			_load_stream(SoundRegistry.path_of(str(f)), e.get("loop", false))


func has_sound(sound: String) -> bool:
	return not SoundRegistry.sfx(sound).is_empty()


# ============================================================ Effekte

## UI-Klang (nicht positionell). name: "click" oder "ui_click".
func play_ui(sound: String) -> AudioStreamPlayer:
	var key := sound if sound.begins_with("ui_") else "ui_" + sound
	if not has_sound(key):
		key = sound
	var e := SoundRegistry.sfx(key)
	if e.is_empty():
		_warn_once(sound, "Unbekannter UI-Klang '%s'" % sound)
		return null
	if not _repeat_ok(key):
		return null
	var stream := _pick_stream(key, e)
	if stream == null:
		return null
	var p := _take_2d(_ui_pool)
	_start(p, stream, float(e.get("vol", 0.0)), _pitch(e, 1.0))
	return p


## Effekt abspielen. pos: Vector3 (3D) oder Node3D (dessen Position) oder null
## (nicht positionell). Gibt den Spieler zurueck oder null.
func play_sfx(sound: String, pos = null, volume_db := 0.0, pitch := 1.0) -> Node:
	var e := SoundRegistry.sfx(sound)
	if e.is_empty():
		_warn_once(sound, "Unbekannter Klang '%s'" % sound)
		return null
	var is_3d := pos is Vector3 or pos is Node3D
	if not is_3d and not _repeat_ok(sound):
		return null
	var stream := _pick_stream(sound, e)
	if stream == null:
		return null
	var vol: float = float(e.get("vol", 0.0)) + volume_db
	var ps := _pitch(e, pitch)
	if is_3d:
		var p3 := _take_3d()
		var where: Vector3 = pos if pos is Vector3 else (pos as Node3D).global_position
		p3.max_distance = float(e.get("dist", 60.0))
		p3.unit_size = float(e.get("unit", 4.0))
		p3.global_position = where
		_start(p3, stream, vol, ps)
		return p3
	var p := _take_2d(_pool_2d)
	_start(p, stream, vol, ps)
	return p


## Dauerklang (Motor, Sirene), der einem Knoten folgt. Der Aufrufer besitzt den
## Spieler (pitch_scale/volume_db steuern, queue_free beim Entfernen).
func attach_loop(sound: String, parent: Node3D, volume_db := 0.0) -> AudioStreamPlayer3D:
	var e := SoundRegistry.sfx(sound)
	if e.is_empty() or parent == null:
		_warn_once(sound, "Unbekannter Klang '%s'" % sound)
		return null
	var stream := _load_stream(SoundRegistry.path_of(str(e.files[0])), true)
	if stream == null:
		return null
	var p := AudioStreamPlayer3D.new()
	p.bus = str(e.get("bus", "SFX"))
	p.stream = stream
	p.max_distance = float(e.get("dist", 60.0))
	p.unit_size = float(e.get("unit", 4.0))
	p.volume_db = float(e.get("vol", 0.0)) + volume_db
	parent.add_child(p)
	if p.is_inside_tree():
		p.play(_rng.randf() * maxf(stream.get_length() - 0.05, 0.0))
	else:
		p.autoplay = true
	return p


func _pitch(e: Dictionary, base: float) -> float:
	var pv: float = e.get("pitch", 0.0)
	return maxf(0.05, base * (1.0 + _rng.randf_range(-pv, pv)))


func _repeat_ok(key: String) -> bool:
	var now := Time.get_ticks_msec() / 1000.0
	if now - float(_last_play.get(key, -10.0)) < MIN_REPEAT:
		return false
	_last_play[key] = now
	return true


func _start(p: Node, stream: AudioStream, vol: float, pitch: float) -> void:
	_fades.erase(p.get_instance_id())
	p.set("stream", stream)
	p.set("volume_db", vol)
	p.set("pitch_scale", pitch)
	p.set_meta("t0", Time.get_ticks_msec())
	p.call("play")


func _take_2d(pool: Array[AudioStreamPlayer]) -> AudioStreamPlayer:
	var oldest: AudioStreamPlayer = pool[0]
	for p in pool:
		if not p.playing:
			return p
		if int(p.get_meta("t0", 0)) < int(oldest.get_meta("t0", 0)):
			oldest = p
	oldest.stop()
	return oldest


func _take_3d() -> AudioStreamPlayer3D:
	var oldest: AudioStreamPlayer3D = _pool_3d[0]
	for p in _pool_3d:
		if not p.playing:
			return p
		if int(p.get_meta("t0", 0)) < int(oldest.get_meta("t0", 0)):
			oldest = p
	oldest.stop()
	return oldest


# ============================================================ Musik

## Wechselt den Musiktitel mit Ueberblendung. track "" blendet aus.
func play_music(track: String, fade := 2.0) -> void:
	if track == music_track:
		return
	var old := _music[_music_active]
	if old.playing:
		_fade_to(old, 0.0, fade, true)
	music_track = track
	if track == "":
		return
	var e := SoundRegistry.music(track)
	if e.is_empty():
		_warn_once("music:" + track, "Unbekannter Musiktitel '%s'" % track)
		music_track = ""
		return
	var stream := _pick_stream("music:" + track, e.merged({"loop": true}, true))
	if stream == null:
		music_track = ""
		return
	_music_active = 1 - _music_active
	var p := _music[_music_active]
	p.stop()
	p.stream = stream
	p.set_meta("base_db", float(e.get("vol", 0.0)))
	_set_gain(p, 0.0)
	p.play()
	_fade_to(p, 1.0, fade, false)


func stop_music(fade := 2.0) -> void:
	play_music("", fade)


func is_music_playing() -> bool:
	for p in _music:
		if p.playing and _gain_of(p) > 0.0:
			return true
	return false


# ============================================================ Ambiente

## Setzt die Ambiente-Schichten mit Gewicht 0..1, z.B. {"city_day": 1.0, "park": 0.4}.
## Nicht genannte Schichten werden ausgeblendet. fade in Sekunden.
func set_ambience(layers: Dictionary, fade := 3.0) -> void:
	var wanted: Dictionary = {}
	for k in layers.keys():
		var w := clampf(float(layers[k]), 0.0, 1.5)
		if w > 0.01:
			wanted[str(k)] = w
	for layer in _amb.keys():
		if not wanted.has(layer):
			_fade_to(_amb[layer], 0.0, fade, true, true)
			_amb.erase(layer)
	var active: Dictionary = {}
	for layer in wanted.keys():
		var w: float = wanted[layer]
		if _amb.has(layer):
			_fade_to(_amb[layer], w, fade, false)
			active[layer] = w
			continue
		var e := SoundRegistry.ambience(layer)
		if e.is_empty():
			_warn_once("amb:" + layer, "Unbekannte Ambiente-Schicht '%s'" % layer)
			continue
		var stream := _pick_stream("amb:" + layer, e.merged({"loop": true}, true))
		if stream == null:
			continue
		var bus := str(e.get("bus", "Ambient"))
		if AudioServer.get_bus_index(bus) < 0:
			bus = "Ambient"
		var p := _make_player(bus)
		p.name = "Amb_" + layer
		p.stream = stream
		p.set_meta("base_db", float(e.get("vol", 0.0)))
		_set_gain(p, 0.0)
		# Zufaelliger Einstieg, damit gleiche Schleifen nicht synchron klingen
		p.play(_rng.randf() * maxf(stream.get_length() - 0.1, 0.0))
		_amb[layer] = p
		_fade_to(p, w, fade, false)
		active[layer] = w
	_amb_targets = active


## Aktuelle Zielgewichte der Ambiente-Schichten.
func get_ambience() -> Dictionary:
	return _amb_targets.duplicate()


# ============================================================ Blenden

func _gain_of(p: Node) -> float:
	return float(p.get_meta("gain", 0.0))


func _set_gain(p: Node, g: float) -> void:
	p.set_meta("gain", g)
	var base: float = p.get_meta("base_db", 0.0)
	# Gleichleistungskurve: Summe zweier Ueberblendungen bleibt etwa gleich laut
	var amp := sin(clampf(g, 0.0, 1.0) * PI * 0.5) * maxf(g, 1.0)
	p.set("volume_db", base + (linear_to_db(amp) if amp > 0.0001 else SILENT_DB))


func _fade_to(p: Node, target: float, time: float, stop_at_zero: bool, free_at_zero := false) -> void:
	var g := _gain_of(p)
	if time <= 0.001:
		_set_gain(p, target)
		if target <= 0.0 and (stop_at_zero or free_at_zero):
			_finish(p, free_at_zero)
		return
	_fades[p.get_instance_id()] = {
		"player": p, "target": target, "rate": maxf(absf(target - g), 0.05) / time,
		"stop": stop_at_zero, "free": free_at_zero,
	}


func _finish(p: Node, free_it: bool) -> void:
	p.call("stop")
	if free_it:
		p.queue_free()


func _process(delta: float) -> void:
	if _fades.is_empty():
		return
	var done: Array = []
	for id in _fades.keys():
		var f: Dictionary = _fades[id]
		var p: Node = f.player
		if not is_instance_valid(p):
			done.append(id)
			continue
		var target: float = f.target
		var g := move_toward(_gain_of(p), target, float(f.rate) * delta)
		_set_gain(p, g)
		if is_equal_approx(g, target):
			done.append(id)
			if target <= 0.0 and (f.stop or f.free):
				_finish(p, f.free)
	for id in done:
		_fades.erase(id)


# ============================================================ Alles stoppen

func stop_all() -> void:
	for id in _fades.keys():
		var f: Dictionary = _fades[id]
		if is_instance_valid(f.player) and f.free:
			f.player.stop()
			f.player.queue_free()
	_fades.clear()
	for p in _music:
		p.stop()
		_set_gain(p, 0.0)
	music_track = ""
	for layer in _amb.keys():
		_amb[layer].stop()
		_amb[layer].queue_free()
	_amb.clear()
	_amb_targets = {}
	for p in _ui_pool:
		p.stop()
	for p in _pool_2d:
		p.stop()
	for p in _pool_3d:
		p.stop()
	set_outdoor_muffled(false)


# ============================================================ EventBus

func _connect_events() -> void:
	var bus := get_node_or_null("/root/EventBus")
	if bus == null:
		return
	bus.notify.connect(_on_notify)
	bus.player_money_changed.connect(_on_money)
	bus.game_started.connect(_on_game_started)
	bus.returned_to_menu.connect(_on_menu)
	bus.player_job_changed.connect(_on_job)
	bus.open_panel.connect(_on_open_panel)
	bus.close_panels.connect(_on_close_panels)


func _on_notify(_text: String, kind: String) -> void:
	match kind:
		"money":
			_money_sound("money_gain")
		"danger":
			play_ui("error")
		_:
			play_ui("notification")


func _on_money(balance: float) -> void:
	if is_nan(_last_balance):
		_last_balance = balance
		return
	var delta := balance - _last_balance
	_last_balance = balance
	if absf(delta) < 0.5:
		return
	_money_sound("money_gain" if delta > 0.0 else "money_spend")


func _money_sound(sound: String) -> void:
	var now := Time.get_ticks_msec() / 1000.0
	if now - _money_time < MONEY_COOLDOWN:
		return
	_money_time = now
	play_ui(sound)


func _on_game_started(world) -> void:
	_last_balance = NAN
	if world != null and world.get("ledger") != null and world.ledger.player_account >= 0:
		_last_balance = world.ledger.balance(world.ledger.player_account)
	var h := 12.0
	if world != null and world.has_method("get_hour_f"):
		h = world.get_hour_f()
	play_music(AmbienceDirector.music_for_hour(h), 3.0)


func _on_menu() -> void:
	set_ambience({}, 1.5)
	set_outdoor_muffled(false)
	_phone_open = false
	play_music("menu", 2.0)


func _on_job(job_id: String) -> void:
	if job_id != "":
		play_ui("levelup")


func _on_open_panel(panel: String, _context: Dictionary) -> void:
	if panel == "phone" and not _phone_open:
		_phone_open = true
		play_ui("phone_open")


func _on_close_panels() -> void:
	if _phone_open:
		_phone_open = false
		play_ui("phone_close")
