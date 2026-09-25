class_name GameWorld
extends Node3D
## Root of the playable world. Owns the static data (city plan, road graph, props)
## and composes the world systems. Gameplay systems register themselves here so
## other modules can find them without global singletons: `GameWorld.instance.traffic`.

signal loading_progress(value: float, text: String)
signal ready_to_play

static var instance: GameWorld

## Gameplay systems instantiated at boot (each registers itself on GameWorld.instance).
const SYSTEMS := [
	"res://world/day_night.gd", "res://world/weather.gd", "res://ai/traffic_manager.gd", "res://npc/ped_manager.gd",
	"res://police/police_manager.gd", "res://missions/mission_manager.gd", "res://world/random_events.gd",
	"res://economy/poi_manager.gd", "res://world/ambience.gd", "res://ui/hud.gd", "res://ui/pause_menu.gd",
	"res://ui/phone.gd", "res://ui/map_screen.gd", "res://debug/debug_tools.gd",
]

var data: WorldData
var props: PropLibrary
var graph: RoadGraph
var signals: TrafficSignals
var streaming: StreamingManager
var sky: SkyEnvironment
var ocean: Ocean
var lights: LightPool

# gameplay systems (set by their own _ready)
var day_night: Node
var weather: Node
var traffic: Node
var peds: Node
var police: Node
var missions: Node
var events: Node
var economy: Node
var player: Node3D
var hud: CanvasLayer

var loaded := false
var auto_boot := false
var camera_rig: CameraRig
var overlay: CanvasLayer


func _enter_tree() -> void:
	instance = self


func _exit_tree() -> void:
	if instance == self:
		instance = null


## Loads static data and the initial area. Call from the loading screen.
func initialize(start_pos: Vector3, progress: Callable = Callable()) -> void:
	_progress(progress, 0.02, "Stadtplan wird geladen")
	await _yield()
	data = WorldData.new()
	data.load_all()
	if start_pos == Vector3.INF:
		start_pos = spawn_position()
	_progress(progress, 0.08, "Straßennetz wird aufgebaut")
	await _yield()
	graph = RoadGraph.new()
	graph.load_graph()
	signals = TrafficSignals.new(graph)
	_progress(progress, 0.14, "Props werden geladen")
	await _yield()
	props = PropLibrary.new()
	props.load_library()
	sky = SkyEnvironment.new()
	sky.name = "Sky"
	add_child(sky)
	ocean = Ocean.new()
	ocean.name = "Ocean"
	add_child(ocean)
	streaming = StreamingManager.new()
	streaming.name = "Streaming"
	add_child(streaming)
	streaming.setup(data, props, graph, signals)
	lights = LightPool.new()
	lights.name = "LightPool"
	add_child(lights)
	lights.setup(streaming)
	_progress(progress, 0.2, "Welt wird geladen")
	await _yield()
	await streaming.load_area_async(start_pos, minf(streaming.load_radius, 420.0),
		func(v: float, t: String): _progress(progress, 0.2 + v * 0.7, t))
	loaded = true
	_progress(progress, 1.0, "Bereit")


## Where the player starts: saved position for loaded games, the safehouse for new games.
func spawn_position() -> Vector3:
	var pd := Game.player_data
	if not Game.is_new_game() and pd.position != Vector3.ZERO:
		return pd.position
	return data.spawn


func _ready() -> void:
	if auto_boot:
		boot()


## Full startup used by world.tscn: loading overlay, world data, player, camera, systems.
func boot() -> void:
	overlay = preload("res://ui/loading_overlay.gd").new()
	add_child(overlay)
	await initialize(Vector3.INF, func(v: float, t: String): overlay.call("set_progress", v * 0.9, t))
	overlay.call("set_progress", 0.92, "Spieler wird vorbereitet")
	await _yield()
	_spawn_player()
	_spawn_systems()
	overlay.call("set_progress", 1.0, "Bereit")
	await _yield()
	overlay.call("finish")
	Game.state = Game.State.PLAYING
	Input.mouse_mode = Input.MOUSE_MODE_CAPTURED
	Events.world_ready.emit()


func _spawn_player() -> void:
	var pd := Game.player_data
	var pos := spawn_position()
	var p := Player.new()
	p.name = "Player"
	add_child(p)
	p.global_position = pos + Vector3.UP * 0.2
	p.rotation.y = pd.yaw
	player = p
	camera_rig = CameraRig.new()
	camera_rig.name = "CameraRig"
	add_child(camera_rig)
	p.set_camera(camera_rig)
	camera_rig.yaw = pd.yaw
	camera_rig.global_position = p.global_position + Vector3.UP * 1.6
	if not pd.weapons.is_empty():
		p.weapons.deserialize(pd.weapons)
	if not Game.is_new_game():
		p.health.health = pd.health
		p.health.armor = pd.armor


func _spawn_systems() -> void:
	for path in SYSTEMS:
		if ResourceLoader.exists(path):
			var n: Node = load(path).new()
			add_child(n)


func _yield() -> void:
	if is_inside_tree():
		await get_tree().process_frame


func _progress(cb: Callable, v: float, t: String) -> void:
	loading_progress.emit(v, t)
	if cb.is_valid():
		cb.call(v, t)


func _process(delta: float) -> void:
	if not loaded:
		return
	signals.advance(delta)
	var cam := get_viewport().get_camera_3d()
	if player and is_instance_valid(player):
		streaming.set_focus(player.global_position)
	elif cam:
		streaming.set_focus(cam.global_position)


func sea_level() -> float:
	return data.sea_level if data else -1.5
