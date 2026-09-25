class_name DayNight
extends Node
## Game clock. One in-game day lasts `DAY_MINUTES` real minutes (default 48, like a
## compressed real day). Drives the sky/sun/moon, street lamps and lit windows through
## SkyEnvironment and the shared shader globals.

const DAY_MINUTES := 48.0

var hour := 9.0
var day := 1
var paused := false
var time_scale := 1.0               # debug fast-forward
var _last_emit := -1.0


func _ready() -> void:
	name = "DayNight"
	var w := GameWorld.instance
	w.day_night = self
	if Game.player_data:
		hour = Game.player_data.hour
	_apply()


func _process(delta: float) -> void:
	if paused or get_tree().paused:
		return
	var hours := delta * 24.0 / (DAY_MINUTES * 60.0) * time_scale
	_advance(hours)


func _advance(hours: float) -> void:
	hour += hours
	while hour >= 24.0:
		hour -= 24.0
		day += 1
	_apply()


func _apply() -> void:
	var w := GameWorld.instance
	if w and w.sky:
		w.sky.set_time(hour)
	var minute := floorf(hour * 60.0)
	if minute != _last_emit:
		_last_emit = minute
		Events.time_changed.emit(hour)


func set_hour(h: float) -> void:
	hour = fposmod(h, 24.0)
	_apply()


func advance_hours(h: float) -> void:
	_advance(h)


func clock_text() -> String:
	var hh := int(hour)
	var mm := int((hour - hh) * 60.0)
	return "%02d:%02d" % [hh, mm]


func is_night() -> bool:
	return hour < 6.0 or hour > 20.5
