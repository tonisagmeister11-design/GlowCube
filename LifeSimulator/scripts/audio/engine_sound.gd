class_name EngineSound
extends Node3D
## Motorklang fuer Fahrzeuge (optional einzuhaengen): Leerlauf- und
## Drehzahlschleife werden nach Last ueberblendet und in der Tonhoehe verschoben.
##
##   var eng := EngineSound.new()
##   eng.base_pitch = 0.7          # Bus/LKW tiefer, Kleinwagen ~1.1
##   vehicle.add_child(eng)
##   # pro Frame:
##   eng.set_load(absf(speed) / max_speed, throttle)

@export var base_pitch := 1.0
@export var volume_db := 0.0
@export var response := 3.0     # Traegheit der Drehzahl (1/s)

var _idle: AudioStreamPlayer3D
var _rev: AudioStreamPlayer3D
var _load := 0.0
var _target := 0.0


func _ready() -> void:
	var audio := get_node_or_null("/root/Audio")
	if audio == null:
		return
	_idle = audio.attach_loop("engine_idle", self, volume_db)
	_rev = audio.attach_loop("engine_rev", self, volume_db)
	_apply()


## speed_ratio 0..1 (Geschwindigkeit / Hoechstgeschwindigkeit), throttle 0..1.
func set_load(speed_ratio: float, throttle := 0.0) -> void:
	_target = clampf(maxf(speed_ratio, throttle * 0.6), 0.0, 1.0)


func _process(delta: float) -> void:
	_load = move_toward(_load, _target, response * delta)
	_apply()


func _apply() -> void:
	if _idle == null or _rev == null:
		return
	var idle_g := 1.0 - smoothstep(0.05, 0.35, _load)
	var rev_g := smoothstep(0.0, 0.3, _load)
	var e_idle := SoundRegistry.sfx("engine_idle")
	var e_rev := SoundRegistry.sfx("engine_rev")
	_idle.volume_db = float(e_idle.get("vol", 0.0)) + volume_db + linear_to_db(maxf(idle_g, 0.0001))
	_rev.volume_db = float(e_rev.get("vol", 0.0)) + volume_db + linear_to_db(maxf(rev_g, 0.0001))
	_idle.pitch_scale = base_pitch * (1.0 + 0.3 * _load)
	_rev.pitch_scale = base_pitch * (0.7 + 1.0 * _load)


func _exit_tree() -> void:
	# Schleifen vor dem Freigeben anhalten, sonst behaelt der AudioServer die Wiedergabe
	for p in [_idle, _rev]:
		if p != null and is_instance_valid(p):
			p.stop()


func horn(kind := "car") -> void:
	var audio := get_node_or_null("/root/Audio")
	if audio:
		audio.play_sfx("bus_horn" if kind == "bus" else "car_horn", self)
