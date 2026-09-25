class_name GameWorld
extends Node3D
## Root of the playable world. Owns the static data (city plan, road graph, props)
## and composes the world systems. Gameplay systems register themselves here so
## other modules can find them without global singletons: `GameWorld.instance.traffic`.

signal loading_progress(value: float, text: String)
signal ready_to_play

static var instance: GameWorld

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


func _enter_tree() -> void:
	instance = self


func _exit_tree() -> void:
	if instance == self:
		instance = null


## Loads static data and the initial area. Call from the loading screen.
func initialize(start_pos: Vector3, progress: Callable = Callable()) -> void:
	_progress(progress, 0.02, "Stadtplan wird geladen")
	data = WorldData.new()
	data.load_all()
	_progress(progress, 0.08, "Straßennetz wird aufgebaut")
	graph = RoadGraph.new()
	graph.load_graph()
	signals = TrafficSignals.new(graph)
	_progress(progress, 0.14, "Props werden geladen")
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
	streaming.load_area_blocking(start_pos, minf(streaming.load_radius, 420.0),
		func(v: float, t: String): _progress(progress, 0.2 + v * 0.7, t))
	loaded = true
	_progress(progress, 1.0, "Bereit")


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
