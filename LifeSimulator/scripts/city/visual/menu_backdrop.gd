class_name MenuBackdrop
extends Node3D
## Eigenstaendiger 3D-Hintergrund fuer das Hauptmenue: kleine Stadt mit
## festem Seed, Blick ueber den Fluss auf die Hochhaeuser der Innenstadt in
## goldener Abendstimmung, langsame Kamerafahrt. Baut nur den sichtbaren
## Bereich (Ladezeit ca. 2-3 s).
##
## Oeffentlich: Signal ready_to_show (nach dem Aufbau), Eigenschaft hour.

signal ready_to_show

const SEED := 3
const SIZE := "small"

@export var hour := 19.1
@export var drift_speed := 0.012

var city: CityModel
var builder: CityBuilder
var daynight: DayNight
var cam: Camera3D
var _anchor := Vector3.ZERO
var _target := Vector3.ZERO
var _side := Vector3.RIGHT
var _t := 0.0
var build_ms := 0


func _ready() -> void:
	var t0 := Time.get_ticks_msec()
	city = CityGenerator.generate(SEED, {"size": SIZE})
	daynight = DayNight.new()
	daynight.name = "DayNight"
	add_child(daynight)
	daynight.set_weather("clear", true)
	daynight.set_hour(hour)
	cam = Camera3D.new()
	cam.name = "Camera"
	cam.fov = 55.0
	cam.far = 7000.0
	add_child(cam)
	cam.make_current()
	_choose_view()
	builder = CityBuilder.new()
	builder.name = "City"
	add_child(builder)
	builder.view_distance = 700.0
	builder.lights_enabled = false
	builder.setup(city, cam)
	builder.stream_enabled = false
	_place_camera(0.0)
	builder.build_area_now(_target, 520.0)
	build_ms = Time.get_ticks_msec() - t0
	ready_to_show.emit()


func _choose_view() -> void:
	# Hoechstes Hochhaus nahe am Fluss als Blickziel
	var best := {}
	for b in city.buildings:
		if b.kind in ["tower", "office"]:
			var dz := absf(b.footprint.get_center().y - float(city.river.z))
			var score: float = b.height - dz * 0.25
			if best.is_empty() or score > best.score:
				best = {"b": b, "score": score}
	var tc := Vector2(0, float(city.river.z) - 200.0)
	if not best.is_empty():
		tc = best.b.footprint.get_center()
	var north := tc.y < float(city.river.z)
	# Kamera am gegenueberliegenden Ufer, leicht erhoeht, schraeg ueber den Fluss
	var bank_z: float = city.river.z_south + 30.0 if north else city.river.z_north - 30.0
	var across := 1.0 if north else -1.0
	_anchor = Vector3(tc.x - 140.0, 34.0, bank_z + across * 10.0)
	_target = Vector3(tc.x + 40.0, 40.0, tc.y)
	_side = Vector3(1, 0, 0)


func _place_camera(t: float) -> void:
	var off := _side * sin(t * TAU * drift_speed) * 60.0 + Vector3(0, sin(t * TAU * drift_speed * 0.7) * 4.0, 0)
	cam.global_position = _anchor + off
	cam.look_at(_target + off * 0.3, Vector3.UP)


func _process(delta: float) -> void:
	_t += delta
	if cam:
		_place_camera(_t)
