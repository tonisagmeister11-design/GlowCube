class_name AmbienceDirector
extends Node
## Waehlt Ambiente-Schichten und Musik passend zu Ort, Tageszeit und Situation.
##
## Einbindung durch die Weltszene:
##   var amb := AmbienceDirector.new()
##   add_child(amb)
##   amb.setup(world, func() -> Vector3: return player.global_position)
## Innenraeume: amb.set_indoors(true, "shop") / amb.set_indoors(false)
## Wetter (sobald es ein Wettersystem gibt): amb.set_weather("rain") / ("")
##
## Entscheidungsgrundlagen: Zone des Stadtteils an der Spielerposition
## (world.city.district_at), Naehe zu Parks (world.city.parks) und zum Fluss
## (world.city.river), Uhrzeit (world.get_hour_f()), Innenraum-Flag.

## Laute, staedtische Zonen -> Stadtgeraeusche.
const URBAN_ZONES := ["downtown", "business", "commercial", "oldtown", "apartments",
	"highrise", "poor", "slum", "industrial", "university", "civic"]
## Ruhige Wohnzonen -> Wohngebiet-Ambiente.
const QUIET_ZONES := ["residential", "suburb", "rich", "estates"]
## Innenraumarten mit Laden-Ambiente.
const SHOP_KINDS := ["shop", "store", "supermarket", "mall", "market", "kiosk", "bakery",
	"cafe", "restaurant", "bar", "pharmacy", "boutique"]
const PARK_RADIUS := 45.0     # m: bis hierhin hoert man den Park
const RIVER_RADIUS := 70.0
const MUSIC_FADE := 6.0

var update_interval := 1.0    # Sekunden zwischen Neuberechnungen
var fade_time := 4.0          # Ueberblendung der Ambiente-Schichten
var music_enabled := true
## Dezente Musik: nach einer Spielphase folgt eine Pause (nur Ambiente).
var music_rest := true
var music_play_range := Vector2(150.0, 300.0)   # Sekunden Musik
var music_rest_range := Vector2(45.0, 120.0)    # Sekunden Pause
var weather := ""             # "" oder "rain"

var last_layers: Dictionary = {}
var last_music := ""

var _world: WorldState
var _focus: Callable
var _indoors := false
var _indoor_kind := ""
var _timer := 0.0
var _music_playing_phase := true
var _phase_left := 0.0
var _rng := RandomNumberGenerator.new()


func setup(world: WorldState, focus_provider: Callable) -> void:
	_world = world
	_focus = focus_provider
	_rng.seed = hash("audio:%d" % (world.seed if world else 0))
	_music_playing_phase = true
	_phase_left = _rng.randf_range(music_play_range.x, music_play_range.y)
	update_now()


func set_indoors(indoors: bool, kind := "") -> void:
	_indoors = indoors
	_indoor_kind = kind
	update_now()


func set_weather(kind: String) -> void:
	weather = kind
	update_now()


## Tag/Nacht-Musik nach Uhrzeit (Abend ab 19:30, Morgen ab 6:00).
static func music_for_hour(h: float) -> String:
	return "night" if h >= 19.5 or h < 6.0 else "day"


## 0 = Tag, 1 = Nacht, weiche Uebergaenge 5-7 Uhr und 19-21 Uhr.
static func night_factor(h: float) -> float:
	if h < 5.0 or h >= 21.0:
		return 1.0
	if h < 7.0:
		return 1.0 - smoothstep(5.0, 7.0, h)
	if h >= 19.0:
		return smoothstep(19.0, 21.0, h)
	return 0.0


func _process(delta: float) -> void:
	if _world == null:
		return
	_timer -= delta
	if _timer <= 0.0:
		_timer = update_interval
		_update_ambience()
	_update_music(delta)


func update_now() -> void:
	_timer = update_interval
	_update_ambience()
	_update_music(0.0)


func _focus_pos() -> Vector3:
	if _focus.is_valid():
		var v = _focus.call()
		if v is Vector3:
			return v
		if v is Vector2:
			return Vector3(v.x, 0.0, v.y)
	return Vector3.ZERO


func _update_ambience() -> void:
	if _world == null:
		return
	var layers := compute_layers(_focus_pos(), _world.get_hour_f())
	last_layers = layers
	var audio := _audio()
	if audio:
		audio.set_outdoor_muffled(_indoors)
		audio.set_ambience(layers, fade_time)


## Reine Berechnung (testbar): Schichtname -> Gewicht 0..1.
func compute_layers(pos: Vector3, hour: float) -> Dictionary:
	var night := night_factor(hour)
	var day := 1.0 - night
	var out: Dictionary = {}
	if _indoors:
		if _indoor_kind in SHOP_KINDS:
			out["shop"] = 1.0
		# Gedaempfte Stadt von draussen (Bus AmbientOutdoor mit Tiefpass)
		out["city_day"] = 0.35 * day
		out["city_night"] = 0.3 * night
		if weather == "rain":
			out["rain"] = 0.5
		return _clean(out)
	var p2 := Vector2(pos.x, pos.z)
	var zone := _zone_at(p2)
	var park_w := park_weight(p2)
	if zone == "park":
		park_w = 1.0
	var city := 1.0
	var res := 0.0
	if zone in QUIET_ZONES:
		city = 0.3
		res = 1.0
	elif zone == "park":
		city = 0.35
	elif zone != "" and not zone in URBAN_ZONES:
		city = 0.7
	city *= 1.0 - 0.65 * park_w
	res *= 1.0 - 0.6 * park_w
	out["city_day"] = city * day
	# Nachts: Stadtrauschen leiser, Grillen in ruhigen Gegenden
	out["city_night"] = city * night + (res + park_w) * night * 0.6
	out["residential"] = res * day
	out["park"] = park_w * (1.0 - 0.75 * night)
	var river_w := river_weight(p2)
	if river_w > 0.0:
		out["river"] = river_w * 0.8
	if weather == "rain":
		out["rain"] = 0.9
		for k in ["park", "residential"]:
			out[k] = float(out.get(k, 0.0)) * 0.4
	return _clean(out)


func _clean(d: Dictionary) -> Dictionary:
	var out: Dictionary = {}
	for k in d.keys():
		var w: float = clampf(float(d[k]), 0.0, 1.0)
		if w >= 0.02:
			out[k] = w
	return out


func _zone_at(p: Vector2) -> String:
	if _world == null or _world.city == null:
		return ""
	var d: Dictionary = _world.city.district_at(p)
	return str(d.get("zone", ""))


## 1 im Park, faellt bis PARK_RADIUS ausserhalb auf 0.
func park_weight(p: Vector2) -> float:
	if _world == null or _world.city == null:
		return 0.0
	var best := INF
	for park in _world.city.parks:
		var r: Rect2 = park.rect
		best = minf(best, _rect_distance(r, p))
		if best <= 0.0:
			return 1.0
	return clampf(1.0 - best / PARK_RADIUS, 0.0, 1.0)


func river_weight(p: Vector2) -> float:
	if _world == null or _world.city == null or _world.city.river.is_empty():
		return 0.0
	var rv: Dictionary = _world.city.river
	var z: float = rv.get("z", 0.0)
	var half: float = float(rv.get("width", 0.0)) * 0.5
	var x0: float = rv.get("x0", -INF)
	var x1: float = rv.get("x1", INF)
	var dz := maxf(absf(p.y - z) - half, 0.0)
	var dx := maxf(maxf(x0 - p.x, p.x - x1), 0.0)
	var d := Vector2(dx, dz).length()
	return clampf(1.0 - d / RIVER_RADIUS, 0.0, 1.0)


static func _rect_distance(r: Rect2, p: Vector2) -> float:
	var dx := maxf(maxf(r.position.x - p.x, p.x - r.end.x), 0.0)
	var dy := maxf(maxf(r.position.y - p.y, p.y - r.end.y), 0.0)
	return Vector2(dx, dy).length()


func _update_music(delta: float) -> void:
	var audio := _audio()
	if audio == null or _world == null or not music_enabled:
		return
	var want := music_for_hour(_world.get_hour_f())
	if music_rest:
		_phase_left -= delta
		if _phase_left <= 0.0:
			_music_playing_phase = not _music_playing_phase
			var rng_v := music_play_range if _music_playing_phase else music_rest_range
			_phase_left = _rng.randf_range(rng_v.x, rng_v.y)
	var target := want if (_music_playing_phase or not music_rest) else ""
	if audio.music_track != target:
		audio.play_music(target, MUSIC_FADE)
	last_music = target


func _audio() -> Node:
	return get_node_or_null("/root/Audio") if is_inside_tree() else null
