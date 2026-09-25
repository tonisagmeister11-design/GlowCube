class_name Ambience
extends Node
## Environmental sound mix: city hum, ocean, birds, night insects, wind and rain layers.
## Levels follow the district around the listener, time of day, weather and whether
## the player sits in a (muffling) vehicle. Also announces district changes.

const COASTAL := ["beach", "marina", "harbor", "sea"]
const NATURE := ["park", "hills", "rural", "rural_east", "suburbs", "luxury"]
const URBAN := ["downtown", "financial", "shopping", "entertainment", "oldtown", "residential"]

var _district := ""
var _timer := 0.0


func _ready() -> void:
	name = "Ambience"


func _process(delta: float) -> void:
	var w := GameWorld.instance
	if w == null or w.player == null:
		return
	var cam := get_viewport().get_camera_3d()
	var pos: Vector3 = cam.global_position if cam else w.player.global_position
	var d: Dictionary = w.data.district_at(pos)
	var did: String = d.get("id", "")
	_timer -= delta
	if did != _district and _timer <= 0.0:
		_timer = 3.0
		_district = did
		Events.district_entered.emit(did, d.get("name", did))
	var night := float(ShaderGlobals.get_value("night", 0.0))
	var rain := 0.0
	var wind := 0.2
	if w.weather:
		rain = float(w.weather.cur[3])
		wind = float(w.weather.cur[4])
	var urban := 1.0 if did in URBAN else (0.4 if did in ["industrial", "construction", "airport"] else 0.1)
	var coastal := 1.0 if did in COASTAL else 0.0
	# the sea is audible near the shoreline everywhere
	if pos.y < 6.0 and w.data.district_at(pos + Vector3(0, 0, 120)).get("id", "") == "sea":
		coastal = maxf(coastal, 0.7)
	var nature := 1.0 if did in NATURE else 0.25
	var levels := {
		"city_loop": urban * lerpf(1.0, 0.55, night) * (1.0 - rain * 0.3),
		"ocean_loop": coastal,
		"birds_loop": nature * (1.0 - night) * (1.0 - rain),
		"night_loop": nature * night * (1.0 - rain) * 0.8,
		"wind_loop": clampf(wind * 0.6 + maxf(0.0, pos.y - 40.0) / 120.0, 0.0, 1.0),
		"rain_loop": clampf(rain * 1.6, 0.0, 1.0) * (1.0 if rain < 0.7 else 0.6),
		"rain_heavy_loop": clampf((rain - 0.6) * 2.5, 0.0, 1.0),
	}
	var p := w.player as Player
	if p and p.is_in_vehicle():
		for k in levels:
			levels[k] *= 0.45
	AudioManager.set_ambience(levels, delta)
