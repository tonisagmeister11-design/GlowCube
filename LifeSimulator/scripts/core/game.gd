extends Node
## Session-Verwaltung: neues Spiel, Laden/Speichern, Szenenwechsel,
## Zeitfluss und Autosave. Die Simulation selbst steckt in WorldState.

const SCENE_MENU := "res://scenes/menu/main_menu.tscn"
const SCENE_LOADING := "res://scenes/menu/loading_screen.tscn"
const SCENE_WORLD := "res://scenes/world/world.tscn"

## Zeitstufen: Spielminuten pro Echtzeitsekunde (Index 0 = Pause).
const SPEEDS := [0.0, 1.0, 3.0, 10.0, 30.0]
const SPEED_LABELS := ["Pause", "1x", "3x", "10x", "30x"]

signal world_ready(world)
signal loading_progress(fraction: float, text: String)
signal speed_changed(index: int)

var world: WorldState
var playing := false
var speed_index := 1
var paused_by_ui := 0     # Zaehler offener Menues, die die Zeit anhalten
var current_slot := ""
var _autosave_timer := 0.0
var _thread: Thread
var _pending_config := {}
var _pending_load := {}


func _process(delta: float) -> void:
	if world == null or not playing:
		return
	world.play_time_seconds += delta
	if paused_by_ui <= 0 and speed_index > 0:
		var minutes: float = delta * SPEEDS[speed_index] * float(Settings.get_value("gameplay", "time_scale"))
		# Hoechstens ~2 Spielstunden pro Frame simulieren, Rest folgt.
		# Im Leistungsmodus werden weniger, dafuer groessere Schritte gerechnet.
		var steps := 24
		if has_node("/root/Perf"):
			steps = maxi(4, int(24.0 / get_node("/root/Perf").f("sim_step_mult")))
		world.advance(minutes, steps)
	var interval := float(Settings.get_value("gameplay", "autosave_minutes")) * 60.0
	if interval > 0.0:
		_autosave_timer += delta
		if _autosave_timer >= interval:
			_autosave_timer = 0.0
			save_game(SaveSystem.AUTOSAVE_SLOT, true)


# ============================================================ Zeit

func set_speed(index: int) -> void:
	speed_index = clampi(index, 0, SPEEDS.size() - 1)
	speed_changed.emit(speed_index)


func faster() -> void:
	set_speed(speed_index + 1)


func slower() -> void:
	set_speed(speed_index - 1)


func push_ui_pause() -> void:
	paused_by_ui += 1


func pop_ui_pause() -> void:
	paused_by_ui = maxi(0, paused_by_ui - 1)


## Spult die Simulation vor (Schlafen, Warten, Haft). Laeuft synchron;
## bei grossen Spruengen in Etappen, damit die Oberflaeche aktualisiert.
func skip_time(minutes: float) -> void:
	if world == null:
		return
	world.advance(minutes)
	EventBus.time_skipped.emit(minutes)


# ============================================================ Neues Spiel / Laden

## config: {seed, mode, rules, character, start}
func start_new_game(config: Dictionary) -> void:
	_pending_config = config.duplicate(true)
	_pending_load = {}
	current_slot = ""
	_begin_loading()


func load_game(slot: String) -> bool:
	var res := SaveSystem.load_data(slot)
	if not res.ok:
		Log.error("save", "Laden von '%s' fehlgeschlagen: %s" % [slot, res.error])
		EventBus.notify.emit(res.error, "danger")
		return false
	if res.get("from_backup", false):
		Log.warn("save", "Spielstand '%s' beschädigt - Sicherungskopie wird geladen" % slot)
	_pending_load = res.data
	_pending_config = {}
	current_slot = slot
	_begin_loading()
	return true


func continue_latest() -> bool:
	var slot := SaveSystem.latest_slot()
	if slot == "":
		return false
	return load_game(slot)


func _begin_loading() -> void:
	playing = false
	world = null
	get_tree().change_scene_to_file(SCENE_LOADING)
	# Weltaufbau im Hintergrund, damit der Ladebildschirm fluessig bleibt
	_thread = Thread.new()
	_thread.start(_build_world_threaded)


func _build_world_threaded() -> void:
	var t0 := Time.get_ticks_msec()
	var w: WorldState
	call_deferred("_report_progress", 0.1, "Stadt wird generiert ...")
	if _pending_load.is_empty():
		w = WorldState.create_new(_pending_config)
	else:
		w = WorldState.from_save(_pending_load)
	call_deferred("_report_progress", 0.9, "Welt wird aufgebaut ...")
	call_deferred("_on_world_built", w, Time.get_ticks_msec() - t0)


func _report_progress(f: float, text: String) -> void:
	loading_progress.emit(f, text)


func _on_world_built(w: WorldState, ms: int) -> void:
	if _thread:
		_thread.wait_to_finish()
		_thread = null
	world = w
	w.silent = false
	for msg in w.load_warnings:
		Log.warn("world", msg)
	Log.info("game", "Welt bereit (Seed %d, %d Gebäude, %d ms)" % [w.seed, w.city.buildings.size(), ms])
	var errs := 0
	for i in w.city.issues:
		if i.severity == "error":
			errs += 1
	if errs > 0:
		Log.warn("city", "%d Validierungsfehler nach Reparatur" % errs)
	if w.ledger.player_account >= 0:
		w.ledger.on_player_balance = func(b): EventBus.player_money_changed.emit(b)
	get_tree().change_scene_to_file(SCENE_WORLD)


## Von der Welt-Szene aufgerufen, sobald die 3D-Darstellung bereit ist.
func world_scene_ready() -> void:
	playing = true
	_autosave_timer = 0.0
	set_speed(1)
	world_ready.emit(world)
	EventBus.game_started.emit(world)
	if not _pending_load.is_empty():
		EventBus.game_loaded.emit(current_slot)
	_pending_load = {}


# ============================================================ Speichern

func save_game(slot: String, silent := false) -> bool:
	if world == null:
		return false
	var meta := {"character_name": str(world.character.get("name", "Unbekannt"))}
	if world.player and world.player.has_method("save_meta"):
		meta.merge(world.player.save_meta(world), true)
	var err := SaveSystem.save(slot, world, meta)
	if err != OK:
		Log.error("save", "Speichern in '%s' fehlgeschlagen (Fehler %d)" % [slot, err])
		EventBus.notify.emit("Speichern fehlgeschlagen (Fehler %d)" % err, "danger")
		return false
	if slot != SaveSystem.AUTOSAVE_SLOT:
		current_slot = slot
	Log.info("save", "Gespeichert: %s" % slot)
	EventBus.game_saved.emit(slot)
	if not silent:
		EventBus.notify.emit("Spiel gespeichert", "success")
	return true


func goto_main_menu() -> void:
	playing = false
	world = null
	paused_by_ui = 0
	EventBus.returned_to_menu.emit()
	get_tree().change_scene_to_file(SCENE_MENU)


func quit_game() -> void:
	if world and playing:
		save_game(SaveSystem.AUTOSAVE_SLOT, true)
	get_tree().quit()


func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_CLOSE_REQUEST:
		if world and playing:
			save_game(SaveSystem.AUTOSAVE_SLOT, true)


func _exit_tree() -> void:
	if _thread and _thread.is_started():
		_thread.wait_to_finish()
