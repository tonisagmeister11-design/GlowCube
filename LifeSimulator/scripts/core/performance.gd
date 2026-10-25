extends Node
## Zentrale Leistungssteuerung (Autoload `Perf`).
##
## Alle teuren Systeme fragen hier ihre Grenzwerte ab, statt eigene Zahlen zu
## verwenden. Dadurch wirkt eine Stufe sofort auf Darstellung, Stadtaufbau,
## Verkehr, Menschenmenge, Licht und Simulationstakt.
##
## Stufen (quality):
##   0 Leistungsmodus  – für sehr schwache PCs und Laptops ohne eigene Grafikkarte
##   1 Niedrig
##   2 Mittel (Standard)
##   3 Hoch
##   4 Ultra
##
## Der größte Hebel ist der Renderer: „Kompatibilität" (OpenGL) läuft auch
## ohne Vulkan und auf alter Onboard-Grafik deutlich schneller. Godot kann den
## Renderer nicht im laufenden Betrieb wechseln, deshalb bietet der
## Leistungsmodus einen Neustart an (restart_with_renderer()).

signal level_changed(level: int)
signal applied()

const LEVELS := 5
const NAMES := ["Leistungsmodus", "Niedrig", "Mittel", "Hoch", "Ultra"]
const DESCRIPTIONS := [
	"Für sehr schwache PCs und Laptops: kleine Sichtweite, keine Schatten, wenige Passanten und Fahrzeuge, einfache Oberflächen, halbe Auflösung.",
	"Für ältere PCs: kurze Sichtweite, weiche Schatten aus, wenig Verkehr.",
	"Ausgewogen für übliche PCs.",
	"Für kräftige PCs: große Sichtweite, Schatten, viele Passanten.",
	"Für starke PCs: maximale Sichtweite, beste Schatten und Effekte.",
]

## Abgeleitete Werte je Stufe. Jede Zeile ist von Stufe 0 bis 4 geordnet.
const TABLE := {
	# Darstellung
	"render_scale":      [0.58, 0.75, 1.00, 1.00, 1.00],
	# Hoechste sinnvolle Fensteraufloesung je Stufe. Im Kompatibilitaets-
	# Renderer wirkt die interne Skalierung nicht, deshalb begrenzt der
	# Leistungsmodus zusaetzlich die echte Aufloesung.
	"max_width":         [1280, 1600, 1920, 2560, 3840],
	"msaa":              [0, 0, 1, 1, 2],          # 0 aus, 1 = 2x, 2 = 4x
	"fxaa":              [false, true, true, true, true],
	"shadows":           [0, 1, 2, 2, 3],          # 0 = aus
	"shadow_distance":   [0.0, 120.0, 260.0, 380.0, 500.0],
	"debanding":         [false, false, true, true, true],
	"glow":              [false, false, true, true, true],
	"ssao":              [false, false, false, true, true],
	"ssr":               [false, false, false, false, true],
	"sky_quality":       [0, 1, 2, 3, 3],
	# Stadt
	"view_distance":     [220.0, 340.0, 600.0, 850.0, 1200.0],
	"detail_range":      [130.0, 150.0, 190.0, 240.0, 320.0],
	"prop_range_mult":   [0.70, 0.80, 1.00, 1.20, 1.40],
	"prop_density":      [0.35, 0.60, 1.00, 1.00, 1.00],
	"street_lights":     [0, 4, 10, 16, 24],       # echte Lichtquellen nachts
	"far_lod":           [true, true, true, true, false],
	"interior_lights":   [2, 4, 6, 8, 8],
	"chunk_budget":      [1, 2, 3, 3, 4],          # Chunks gleichzeitig im Aufbau
	"collision_range":   [90.0, 120.0, 170.0, 200.0, 240.0],
	# Belebung
	"max_peds":          [10, 22, 55, 90, 130],
	"ped_lod_bias":      [2, 1, 0, 0, 0],          # erhöht die LOD-Stufe der Figuren
	"ped_update_hz":     [8.0, 12.0, 20.0, 30.0, 30.0],
	"max_vehicles":      [14, 28, 65, 100, 140],
	"parked_cars":       [0.15, 0.4, 1.0, 1.0, 1.0],
	"vehicle_lights":    [false, false, true, true, true],
	# Oberflächen / Shader
	"facade_detail":     [0, 1, 2, 2, 3],          # 0 = flache Fenster ohne Innenraum
	"wind":              [false, false, true, true, true],
	"water_detail":      [0, 1, 2, 2, 3],
	# Simulation
	"sim_step_mult":     [2.0, 1.5, 1.0, 1.0, 1.0],  # größere Simulationsschritte
	"npc_detail_radius": [120.0, 180.0, 260.0, 320.0, 400.0],
}

## Zielbildrate für die automatische Nachregelung.
const TARGET_FPS := 48.0
const MIN_FPS := 32.0

var level := 2
var auto_adjust := true
## Laufzeit-Abschlag (1.0 = voll), den die Nachregelung auf Sichtweite,
## Passanten und Fahrzeuge legt, wenn die Bildrate einbricht.
var dynamic_scale := 1.0
var measured_fps := 0.0
var renderer_name := ""
var adapter := ""

var _fps_samples: Array[float] = []
var _adjust_timer := 0.0
var _settle := 0.0


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	# Erkennung einmalig beim ersten Start, danach gilt die Einstellung
	renderer_name = _detect_renderer()
	adapter = RenderingServer.get_video_adapter_name() if not _headless() else "headless"
	level = int(Settings.get_value("graphics", "quality"))
	auto_adjust = bool(Settings.get_value("graphics", "auto_adjust"))
	if not bool(Settings.get_value("graphics", "detected")):
		level = suggest_level()
		Settings.set_value("graphics", "quality", level, false)
		Settings.set_value("graphics", "detected", true)
		Log.info("perf", "Erststart: Stufe '%s' vorgeschlagen (%s, %s)" % [NAMES[level], adapter, renderer_name])
	Settings.changed.connect(_on_setting_changed)
	apply()


func _headless() -> bool:
	return DisplayServer.get_name() == "headless"


func _detect_renderer() -> String:
	if _headless():
		return "headless"
	var m := str(ProjectSettings.get_setting("rendering/renderer/rendering_method", "forward_plus"))
	var drv := str(RenderingServer.get_video_adapter_api_version())
	if drv.to_lower().contains("opengl") or m == "gl_compatibility":
		return "gl_compatibility"
	return m


func is_compatibility() -> bool:
	return renderer_name == "gl_compatibility"


# ============================================================ Werte

## Wert der aktuellen Stufe. Unbekannte Schlüssel liefern null.
func v(key: String):
	var row = TABLE.get(key)
	if row == null:
		return null
	return row[clampi(level, 0, LEVELS - 1)]


func f(key: String) -> float:
	return float(v(key))


func i(key: String) -> int:
	return int(v(key))


func b(key: String) -> bool:
	return bool(v(key))


## Sichtweite inklusive Nutzerwunsch und dynamischem Abschlag.
func view_distance() -> float:
	# Die Stufe bestimmt die Sichtweite. Wer den Regler bewusst verstellt hat,
	# darf nach unten abweichen, aber nie ueber die Stufe hinaus.
	var base: float = f("view_distance")
	var user: float = float(Settings.get_value("graphics", "view_distance"))
	var d: float = base if user >= 595.0 and user <= 605.0 else minf(user, base)
	return maxf(120.0, d * dynamic_scale)


func max_peds() -> int:
	var d: float = clampf(float(Settings.get_value("gameplay", "population_density")), 0.0, 2.0)
	return maxi(0, int(round(i("max_peds") * d * dynamic_scale)))


func max_vehicles() -> int:
	var d: float = clampf(float(Settings.get_value("gameplay", "traffic_density")), 0.0, 3.0)
	return maxi(0, int(round(i("max_vehicles") * d * dynamic_scale)))


func shadows_enabled() -> bool:
	return i("shadows") > 0 and level > 0


## Hinweise auf sehr schwache bzw. sehr starke Grafik. Reine Namensprüfung,
## damit sie auch ohne Bildschirm prüfbar bleibt.
const WEAK_HINTS := ["llvmpipe", "softpipe", "swiftshader", "intel(r) hd", "intel hd", "uhd graphics 6",
	"hd graphics 4", "hd graphics 3", "gma", "radeon hd", "geforce 9", "geforce gt 2", "geforce gt 3",
	"microsoft basic", "vmware", "virtualbox"]
const STRONG_HINTS := ["rtx 50", "rtx 40", "rtx 30", "rtx 20", "radeon rx 7", "radeon rx 6", "arc a7", "arc b5"]


## Empfohlene Stufe allein aus dem Namen der Grafikkarte.
static func level_for_adapter(adapter_name: String, compatibility := false) -> int:
	var n := adapter_name.to_lower()
	for h in WEAK_HINTS:
		if n.contains(h):
			return 0
	if compatibility:
		return 1
	for h in STRONG_HINTS:
		if n.contains(h):
			return 3
	return 2


## Empfohlene Stufe aus Renderer und Grafikkarte.
func suggest_level() -> int:
	return level_for_adapter(adapter, is_compatibility())


# ============================================================ Anwenden

func set_level(new_level: int, save := true) -> void:
	new_level = clampi(new_level, 0, LEVELS - 1)
	if new_level == level:
		return
	level = new_level
	dynamic_scale = 1.0
	_fps_samples.clear()
	_settle = 3.0
	if save:
		Settings.set_value("graphics", "quality", level)
	Log.info("perf", "Qualitätsstufe: %s" % NAMES[level])
	level_changed.emit(level)
	apply()


func _on_setting_changed(section: String, key: String, value) -> void:
	if section != "graphics":
		return
	if key == "quality" and int(value) != level:
		set_level(int(value), false)
	elif key == "auto_adjust":
		auto_adjust = bool(value)
		if not auto_adjust:
			dynamic_scale = 1.0
			applied.emit()
	elif key in ["view_distance", "shadows", "msaa", "fxaa", "render_scale"]:
		apply()


## Wendet alles an, was ohne Neustart möglich ist, und meldet es den Systemen.
func apply() -> void:
	if not _headless():
		var win := get_window()
		var rs: float = f("render_scale")
		# Nutzerwunsch darf feiner, aber nicht gröber als die Stufe sein
		var user_rs: float = float(Settings.get_value("graphics", "render_scale"))
		win.scaling_3d_scale = clampf(minf(rs, user_rs) if level > 0 else rs, 0.4, 1.0)
		win.scaling_3d_mode = Viewport.SCALING_3D_MODE_BILINEAR
		if is_compatibility():
			# Ohne interne Skalierung hilft nur eine kleinere Bildflaeche
			_limit_window_resolution()
		var msaa: int = i("msaa")
		win.msaa_3d = [Viewport.MSAA_DISABLED, Viewport.MSAA_2X, Viewport.MSAA_4X][clampi(msaa, 0, 2)]
		win.screen_space_aa = Viewport.SCREEN_SPACE_AA_FXAA if b("fxaa") else Viewport.SCREEN_SPACE_AA_DISABLED
		win.use_debanding = b("debanding")
		var sh: int = i("shadows")
		RenderingServer.directional_shadow_atlas_set_size([512, 1024, 2048, 4096][clampi(sh, 0, 3)], sh >= 2)
		win.positional_shadow_atlas_size = [0, 512, 1024, 2048][clampi(sh, 0, 3)]
		RenderingServer.directional_soft_shadow_filter_set_quality(
			[RenderingServer.SHADOW_QUALITY_HARD, RenderingServer.SHADOW_QUALITY_SOFT_VERY_LOW,
			RenderingServer.SHADOW_QUALITY_SOFT_LOW, RenderingServer.SHADOW_QUALITY_SOFT_MEDIUM][clampi(sh, 0, 3)])
		RenderingServer.positional_soft_shadow_filter_set_quality(
			[RenderingServer.SHADOW_QUALITY_HARD, RenderingServer.SHADOW_QUALITY_SOFT_VERY_LOW,
			RenderingServer.SHADOW_QUALITY_SOFT_LOW, RenderingServer.SHADOW_QUALITY_SOFT_MEDIUM][clampi(sh, 0, 3)])
		# Teure Oberflächen-Effekte global abschalten
		RenderingServer.global_shader_parameter_set("perf_facade_detail", i("facade_detail"))
		RenderingServer.global_shader_parameter_set("perf_wind", 1.0 if b("wind") else 0.0)
		RenderingServer.global_shader_parameter_set("perf_water_detail", i("water_detail"))
	applied.emit()


## Globale Shader-Parameter anlegen (einmalig, vor dem ersten Aufbau).
static func ensure_globals() -> void:
	for entry in [["perf_facade_detail", TYPE_INT, 2], ["perf_wind", TYPE_FLOAT, 1.0], ["perf_water_detail", TYPE_INT, 2]]:
		var n: String = entry[0]
		if not ProjectSettings.has_setting("shader_globals/" + n):
			RenderingServer.global_shader_parameter_add(n,
				RenderingServer.GLOBAL_VAR_TYPE_INT if entry[1] == TYPE_INT else RenderingServer.GLOBAL_VAR_TYPE_FLOAT,
				entry[2])


# ============================================================ Nachregelung

func _process(delta: float) -> void:
	if _headless():
		return
	if _settle > 0.0:
		_settle -= delta
		return
	_fps_samples.append(Engine.get_frames_per_second())
	if _fps_samples.size() > 90:
		_fps_samples.remove_at(0)
	_adjust_timer += delta
	if _adjust_timer < 2.0 or _fps_samples.size() < 45:
		return
	_adjust_timer = 0.0
	var sum := 0.0
	for s in _fps_samples:
		sum += s
	measured_fps = sum / _fps_samples.size()
	if not auto_adjust or not Game.playing:
		return
	if measured_fps < MIN_FPS and dynamic_scale > 0.45:
		dynamic_scale = maxf(0.45, dynamic_scale - 0.12)
		Log.info("perf", "Bildrate %.0f – Detailgrad gesenkt (%.2f)" % [measured_fps, dynamic_scale])
		applied.emit()
		_settle = 4.0
	elif measured_fps > TARGET_FPS + 12.0 and dynamic_scale < 1.0:
		dynamic_scale = minf(1.0, dynamic_scale + 0.08)
		applied.emit()
		_settle = 4.0


## Begrenzt die Fenstergröße im Fenstermodus auf die Stufe. Vollbild bleibt
## unangetastet, weil dort die Bildschirmauflösung gilt.
func _limit_window_resolution() -> void:
	if bool(Settings.get_value("graphics", "fullscreen")):
		return
	var limit: int = i("max_width")
	var size := DisplayServer.window_get_size()
	if size.x <= limit:
		return
	var h := int(round(float(limit) * float(size.y) / float(size.x)))
	DisplayServer.window_set_size(Vector2i(limit, h))
	Settings.set_value("graphics", "resolution", Vector2i(limit, h), false)
	Log.info("perf", "Fensterauflösung auf %d × %d begrenzt" % [limit, h])


## Empfohlene Fenstergröße der aktuellen Stufe (für die Oberfläche).
func recommended_resolution() -> Vector2i:
	var limit: int = i("max_width")
	var screen := DisplayServer.screen_get_size() if not _headless() else Vector2i(1920, 1080)
	var w := mini(limit, screen.x)
	return Vector2i(w, int(round(float(w) * 9.0 / 16.0)))


## Setzt die Nachregelung zurück (z. B. nach dem Laden einer Welt).
func reset_dynamic() -> void:
	dynamic_scale = 1.0
	_fps_samples.clear()
	_settle = 3.0


# ============================================================ Renderer

## Startet das Spiel mit einem anderen Renderer neu. "gl_compatibility" läuft
## ohne Vulkan und ist auf schwacher Hardware deutlich schneller.
func restart_with_renderer(method: String) -> void:
	if _headless() or not OS.has_feature("standalone"):
		Log.warn("perf", "Renderer-Wechsel braucht die fertige Spielfassung")
		return
	if Game.world != null and Game.playing:
		Game.save_game(SaveSystem.AUTOSAVE_SLOT, true)
	Settings.set_value("graphics", "renderer", method)
	var args := OS.get_cmdline_args().duplicate()
	var clean := PackedStringArray()
	var skip := false
	for a in args:
		if skip:
			skip = false
			continue
		if a == "--rendering-method" or a == "--rendering-driver":
			skip = true
			continue
		clean.append(a)
	clean.append("--rendering-method")
	clean.append(method)
	Log.info("perf", "Neustart mit Renderer %s" % method)
	OS.set_restart_on_exit(true, clean)
	get_tree().quit()


## Soll beim Start ein Neustart mit dem gespeicherten Renderer erfolgen?
func wanted_renderer() -> String:
	return str(Settings.get_value("graphics", "renderer"))


func renderer_label() -> String:
	return "Kompatibilität (OpenGL)" if is_compatibility() else "Standard (Vulkan)"
