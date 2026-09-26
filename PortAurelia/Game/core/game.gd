extends Node
## Game flow: main menu -> loading screen -> world, pause, death/arrest respawn,
## new game / continue / load. Owns the PlayerData of the running session.

enum State { MENU, LOADING, PLAYING, PAUSED }

const MENU_SCENE := "res://scenes/main_menu.tscn"
const LOADING_SCENE := "res://scenes/loading_screen.tscn"
const WORLD_SCENE := "res://scenes/world.tscn"
const RESPAWN_HOSPITAL_FEE := 500
const BUST_FEE_FRACTION := 0.1

var state := State.MENU
var player_data: PlayerData
var pending_slot := -2          # -2 new game, -1 autosave, 0..2 slots
var debug_enabled := false
var session_time := 0.0
var free_roam := false          # started via FREE ROAM: no story intro
var pending_mission := ""      # started via MISSIONS: begins right after the world is ready


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	debug_enabled = OS.is_debug_build() or OS.has_feature("debug_tools") or "--debug" in OS.get_cmdline_user_args()
	if OS.has_feature("release_build"):
		debug_enabled = false
	player_data = PlayerData.new()
	Events.player_died.connect(_on_player_died)
	Events.player_busted.connect(_on_player_busted)
	if "--smoke-test" in OS.get_cmdline_user_args():
		_smoke_test.call_deferred()


## Release verification: boots a new game, waits for the world, prints the result and quits.
func _smoke_test() -> void:
	print("SMOKE packs=", Paths.mounted_packs, " saves=", Paths.saves_dir)
	await get_tree().create_timer(1.0).timeout
	new_game()
	await Events.world_ready
	await get_tree().create_timer(3.0).timeout
	var w := GameWorld.instance
	var ok := w != null and w.streaming.loaded_chunks().size() > 4 and w.traffic != null and w.peds != null
	print("SMOKE ", "OK" if ok else "FAIL", " chunks=", w.streaming.loaded_chunks().size() if w else 0,
		" traffic=", (w.traffic as TrafficManager).drivers.size() if w else 0)
	get_tree().quit(0 if ok else 1)


func _process(delta: float) -> void:
	if state == State.PLAYING:
		session_time += delta
		player_data.stats["play_time"] = float(player_data.stats.get("play_time", 0.0)) + delta


# ------------------------------------------------------------------ flow
func new_game() -> void:
	player_data = PlayerData.new()
	pending_slot = -2
	free_roam = false
	pending_mission = ""
	_go_loading()


## FREE ROAM: a fresh game without the story intro, some starting cash to explore.
func start_free_roam() -> void:
	new_game()
	free_roam = true
	player_data.money += 10000


## MISSIONS menu: loads the latest save (or a new game) and starts the chosen mission.
func play_mission(mid: String) -> void:
	var slot := SaveManager.latest_slot()
	if slot != -99:
		load_game(slot)
	else:
		new_game()
	free_roam = true
	pending_mission = mid


func continue_game() -> void:
	var slot := SaveManager.latest_slot()
	if slot == -99:
		new_game()
		return
	load_game(slot)


func load_game(slot: int) -> void:
	var pd := SaveManager.load_slot(slot)
	if pd == null:
		push_warning("Save slot %d unreadable, starting a new game" % slot)
		new_game()
		return
	player_data = pd
	pending_slot = slot
	_go_loading()


func _go_loading() -> void:
	state = State.LOADING
	get_tree().paused = false
	get_tree().change_scene_to_file(LOADING_SCENE)


func to_main_menu() -> void:
	state = State.MENU
	get_tree().paused = false
	Input.mouse_mode = Input.MOUSE_MODE_VISIBLE
	AudioManager.stop_ambience()
	get_tree().change_scene_to_file(MENU_SCENE)


func is_new_game() -> bool:
	return pending_slot == -2


func set_paused(p: bool) -> void:
	if state != State.PLAYING and state != State.PAUSED:
		return
	state = State.PAUSED if p else State.PLAYING
	get_tree().paused = p
	Input.mouse_mode = Input.MOUSE_MODE_VISIBLE if p else Input.MOUSE_MODE_CAPTURED


func quit() -> void:
	Settings.save_settings()
	get_tree().quit()


# ------------------------------------------------------------------ death / arrest
func _on_player_died() -> void:
	player_data.stat_add("deaths", 1)
	Events.big_message.emit("AUSGESCHALTET", "", 4.0)
	AudioManager.play_ui("wasted")
	await get_tree().create_timer(4.5).timeout
	var w := GameWorld.instance
	if w == null:
		return
	var fee := mini(RESPAWN_HOSPITAL_FEE, player_data.money)
	player_data.add_money(-fee, "hospital")
	var h := w.data.nearest_poi("hospital", w.player.global_position)
	_respawn_at(h, "Krankenhausrechnung: -$%d" % fee)


func _on_player_busted() -> void:
	player_data.stat_add("arrests", 1)
	Events.big_message.emit("VERHAFTET", "", 4.0)
	AudioManager.play_ui("wasted")
	await get_tree().create_timer(4.5).timeout
	var w := GameWorld.instance
	if w == null:
		return
	var fee := int(player_data.money * BUST_FEE_FRACTION)
	player_data.add_money(-fee, "bail")
	# confiscate weapons
	var p := w.player as Player
	if p:
		for id in p.weapons.owned.keys():
			if id != "unarmed":
				p.weapons.owned.erase(id)
		p.weapons.equip("unarmed")
	var ps := w.data.nearest_poi("police_station", w.player.global_position)
	_respawn_at(ps, "Kaution: -$%d, Waffen beschlagnahmt" % fee)


func _respawn_at(poi: Dictionary, note: String) -> void:
	var w := GameWorld.instance
	var pos: Vector3 = poi.get("entrance_v", w.data.spawn) if not poi.is_empty() else w.data.spawn
	var f: Vector3 = poi.get("facing_v", Vector3.FORWARD) if not poi.is_empty() else Vector3.FORWARD
	pos += f * 2.0 + Vector3.UP * 0.3
	w.streaming.load_area_blocking(pos, 300.0)
	if w.police:
		w.police.call("clear_wanted")
	(w.player as Player).respawn(pos, atan2(-f.x, -f.z) + PI)
	if w.day_night:
		w.day_night.call("advance_hours", 6.0)
	Events.notify.emit(note, 5.0)
