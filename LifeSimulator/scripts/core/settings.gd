extends Node
## Dauerhafte Einstellungen (user://settings.cfg) und Tastenbelegung.
## Grafikoptionen werden ueber apply_graphics() auf Fenster und Viewport
## angewendet; die Welt-Szene liest zusaetzlich quality-abhaengige Werte
## (Sichtweite, Schatten) ueber get_value().

const PATH := "user://settings.cfg"

signal changed(section: String, key: String, value)
signal bindings_changed()

## Standardwerte. Jede hier gelistete Option ist im Einstellungsmenue
## tatsaechlich wirksam.
const DEFAULTS := {
	"graphics": {
		"fullscreen": false,
		"resolution": Vector2i(1600, 900),
		"vsync": true,
		"quality": 2,            # 0 Leistungsmodus, 1 niedrig, 2 mittel, 3 hoch, 4 ultra
		"auto_adjust": true,     # Detailgrad bei Bildratenabfall automatisch senken
		"detected": false,       # Hardware beim ersten Start eingeschaetzt
		"renderer": "",          # "" = wie gestartet, sonst forward_plus | gl_compatibility
		"view_distance": 600.0,  # Meter
		"shadows": 2,            # 0 aus, 1 niedrig, 2 mittel, 3 hoch
		"msaa": 1,               # 0 aus, 1 2x, 2 4x
		"fxaa": true,
		"render_scale": 1.0,
		"max_fps": 0,
		"show_fps": false,
	},
	"audio": {
		"master": 0.8,
		"music": 0.6,
		"sfx": 0.8,
		"ambient": 0.7,
	},
	"controls": {
		"mouse_sensitivity": 0.25,
		"invert_y": false,
	},
	"gameplay": {
		"ui_scale": 1.0,
		"subtitles": true,
		"autosave_minutes": 10,  # Echtzeit-Minuten, 0 = aus
		"population_density": 1.0,
		"traffic_density": 1.0,
		"time_scale": 1.0,       # Echtzeit-Sekunden pro Spielminute (Kehrwert = Tempo)
		"camera_mode": "third",
	},
	"ai": {
		"groq_enabled": false,
		"groq_model": "llama-3.1-8b-instant",
	},
}

## Aktionen mit Standardtasten (physical keycodes / Maustasten).
## Format: action -> [ [typ, code], ... ]  typ: "key" | "mouse"
const DEFAULT_BINDINGS := {
	"move_forward": [["key", KEY_W], ["key", KEY_UP]],
	"move_back": [["key", KEY_S], ["key", KEY_DOWN]],
	"move_left": [["key", KEY_A], ["key", KEY_LEFT]],
	"move_right": [["key", KEY_D], ["key", KEY_RIGHT]],
	"sprint": [["key", KEY_SHIFT]],
	"jump": [["key", KEY_SPACE]],
	"interact": [["key", KEY_E]],
	"vehicle": [["key", KEY_F]],
	"handbrake": [["key", KEY_SPACE]],
	"horn": [["key", KEY_H]],
	"map": [["key", KEY_M]],
	"phone": [["key", KEY_TAB]],
	"inventory": [["key", KEY_I]],
	"help": [["key", KEY_F1]],
	"pause": [["key", KEY_ESCAPE]],
	"camera_toggle": [["key", KEY_V]],
	"time_faster": [["key", KEY_PERIOD]],
	"time_slower": [["key", KEY_COMMA]],
	"quick_save": [["key", KEY_F5]],
	"quick_load": [["key", KEY_F9]],
	"debug_overlay": [["key", KEY_F3]],
	"debug_city": [["key", KEY_F4]],
	"screenshot": [["key", KEY_F12]],
	"primary": [["mouse", MOUSE_BUTTON_LEFT]],
	"secondary": [["mouse", MOUSE_BUTTON_RIGHT]],
}

## Anzeigenamen fuer das Steuerungsmenue.
const ACTION_LABELS := {
	"move_forward": "Vorwärts", "move_back": "Rückwärts", "move_left": "Links",
	"move_right": "Rechts", "sprint": "Rennen", "jump": "Springen",
	"interact": "Interagieren", "vehicle": "Fahrzeug ein-/aussteigen",
	"handbrake": "Handbremse", "horn": "Hupe", "map": "Stadtkarte",
	"phone": "Smartphone", "inventory": "Inventar", "pause": "Pause / Menü", "help": "Hilfe und Ziele",
	"camera_toggle": "Kameraperspektive", "time_faster": "Zeit schneller",
	"time_slower": "Zeit langsamer", "quick_save": "Schnellspeichern",
	"quick_load": "Schnellladen", "debug_overlay": "Debug-Anzeige",
	"debug_city": "Stadt-Debugansicht", "screenshot": "Screenshot",
	"primary": "Primäraktion", "secondary": "Sekundäraktion",
}

var _cfg := ConfigFile.new()


func _ready() -> void:
	_load()
	_apply_bindings()
	if not _is_headless():
		apply_graphics()
	apply_audio()


func _is_headless() -> bool:
	return DisplayServer.get_name() == "headless"


func get_value(section: String, key: String):
	var def = DEFAULTS.get(section, {}).get(key)
	return _cfg.get_value(section, key, def)


func set_value(section: String, key: String, value, save_now := true) -> void:
	_cfg.set_value(section, key, value)
	changed.emit(section, key, value)
	if save_now:
		save()


func save() -> void:
	var err := _cfg.save(PATH)
	if err != OK:
		Log.error("settings", "Einstellungen konnten nicht gespeichert werden (Fehler %d)" % err)


func _load() -> void:
	if FileAccess.file_exists(PATH):
		var err := _cfg.load(PATH)
		if err != OK:
			Log.warn("settings", "settings.cfg beschaedigt (Fehler %d) - Standardwerte werden verwendet" % err)
			_cfg = ConfigFile.new()


# ---------------------------------------------------------------- Grafik

func apply_graphics() -> void:
	var win := get_window()
	var fs: bool = get_value("graphics", "fullscreen")
	if fs:
		DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_FULLSCREEN)
	else:
		DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_WINDOWED)
		var res: Vector2i = get_value("graphics", "resolution")
		var screen := DisplayServer.screen_get_size()
		res = Vector2i(mini(res.x, screen.x), mini(res.y, screen.y))
		DisplayServer.window_set_size(res)
		var pos := DisplayServer.screen_get_position() + (screen - res) / 2
		DisplayServer.window_set_position(pos)
	DisplayServer.window_set_vsync_mode(
		DisplayServer.VSYNC_ENABLED if get_value("graphics", "vsync") else DisplayServer.VSYNC_DISABLED)
	Engine.max_fps = int(get_value("graphics", "max_fps"))
	var vp := win
	var msaa: int = get_value("graphics", "msaa")
	vp.msaa_3d = [Viewport.MSAA_DISABLED, Viewport.MSAA_2X, Viewport.MSAA_4X][clampi(msaa, 0, 2)]
	vp.screen_space_aa = Viewport.SCREEN_SPACE_AA_FXAA if get_value("graphics", "fxaa") else Viewport.SCREEN_SPACE_AA_DISABLED
	vp.scaling_3d_scale = clampf(float(get_value("graphics", "render_scale")), 0.5, 1.0)
	var shadows: int = get_value("graphics", "shadows")
	var size: int = [1024, 2048, 4096, 8192][clampi(shadows, 0, 3)]
	RenderingServer.directional_shadow_atlas_set_size(size, true)
	vp.positional_shadow_atlas_size = [512, 1024, 2048, 4096][clampi(shadows, 0, 3)]
	win.content_scale_factor = clampf(float(get_value("gameplay", "ui_scale")), 0.75, 1.5)


func apply_audio() -> void:
	for bus_name in ["Master", "Music", "SFX", "Ambient"]:
		var idx := AudioServer.get_bus_index(bus_name)
		if idx < 0:
			idx = AudioServer.bus_count
			AudioServer.add_bus(idx)
			AudioServer.set_bus_name(idx, bus_name)
			AudioServer.set_bus_send(idx, "Master")
		var key: String = bus_name.to_lower()
		var vol: float = get_value("audio", key)
		AudioServer.set_bus_volume_db(idx, linear_to_db(maxf(vol, 0.0001)))
		AudioServer.set_bus_mute(idx, vol <= 0.001)


# ---------------------------------------------------------------- Steuerung

func _apply_bindings() -> void:
	for action in DEFAULT_BINDINGS.keys():
		if not InputMap.has_action(action):
			InputMap.add_action(action, 0.2)
		InputMap.action_erase_events(action)
		var binds = _cfg.get_value("bindings", action, DEFAULT_BINDINGS[action])
		for b in binds:
			var ev := _make_event(b)
			if ev:
				InputMap.action_add_event(action, ev)


func _make_event(b) -> InputEvent:
	if typeof(b) != TYPE_ARRAY or b.size() < 2:
		return null
	if b[0] == "key":
		var k := InputEventKey.new()
		k.physical_keycode = int(b[1])
		return k
	if b[0] == "mouse":
		var m := InputEventMouseButton.new()
		m.button_index = int(b[1])
		return m
	return null


## Ersetzt die primaere Belegung einer Aktion. event: InputEventKey oder
## InputEventMouseButton aus einer Eingabeabfrage.
func rebind(action: String, event: InputEvent) -> void:
	var b: Array
	if event is InputEventKey:
		b = ["key", event.physical_keycode if event.physical_keycode != 0 else event.keycode]
	elif event is InputEventMouseButton:
		b = ["mouse", event.button_index]
	else:
		return
	var binds: Array = _cfg.get_value("bindings", action, DEFAULT_BINDINGS.get(action, [])).duplicate(true)
	if binds.is_empty():
		binds.append(b)
	else:
		binds[0] = b
	_cfg.set_value("bindings", action, binds)
	save()
	_apply_bindings()
	bindings_changed.emit()


func reset_bindings() -> void:
	if _cfg.has_section("bindings"):
		_cfg.erase_section("bindings")
	save()
	_apply_bindings()
	bindings_changed.emit()


## Lesbarer Name der ersten Belegung, z.B. "E" oder "Linke Maustaste".
func binding_label(action: String) -> String:
	var evs := InputMap.action_get_events(action) if InputMap.has_action(action) else []
	if evs.is_empty():
		return "-"
	var ev: InputEvent = evs[0]
	if ev is InputEventKey:
		var code: int = ev.physical_keycode if ev.physical_keycode != 0 else ev.keycode
		return OS.get_keycode_string(code)
	if ev is InputEventMouseButton:
		match ev.button_index:
			MOUSE_BUTTON_LEFT: return "Linke Maustaste"
			MOUSE_BUTTON_RIGHT: return "Rechte Maustaste"
			MOUSE_BUTTON_MIDDLE: return "Mittlere Maustaste"
		return "Maustaste %d" % ev.button_index
	return "?"
