extends Node3D
## Wurzel der Weltszene (scenes/world/world.tscn).
##
## Kinder: Environment (DayNight: WorldEnvironment, Sonne, Mond, Regen),
## City (CityBuilder), Actors (Spieler, NPCs, Fahrzeuge), UI (CanvasLayer).
## Ablauf: Welt aus Game.world (oder eigene Testwelt), Stadt um den
## Startpunkt aufbauen, Spieler (falls vorhanden) bzw. Flugkamera setzen,
## HUD laden, Verkehr/NPCs starten, Game.world_scene_ready() melden.
## F4 (Aktion "debug_city") schaltet die Stadt-Debugansicht um.

const PLAYER_SCENE := "res://scenes/player/player.tscn"
const UI_SCENE := "res://scenes/ui/game_ui.tscn"
const TRAFFIC_SCRIPT := "res://scripts/traffic/traffic_manager.gd"
const CROWD_SCRIPT := "res://scripts/npc/crowd_manager.gd"
const INITIAL_RADIUS := 260.0

@onready var environment_node: DayNight = $Environment
@onready var city: CityBuilder = $City
@onready var actors: Node3D = $Actors
@onready var ui: CanvasLayer = $UI

var world: WorldState
var player: Node3D
var spawn_pos := Vector3.ZERO
var spawn_yaw := 0.0
var _standalone := false


func _ready() -> void:
	var game := get_node_or_null("/root/Game")
	if game and game.world:
		world = game.world
	else:
		_standalone = true
		world = WorldState.create_new({"seed": 12345, "mode": "sandbox"})
		world.silent = false
		if game:
			game.world = world
	var t0 := Time.get_ticks_msec()
	environment_node.setup(world)
	city.setup(world.city)
	_choose_spawn()
	_spawn_player()
	city.set_focus(player)
	city.build_area_now(spawn_pos, INITIAL_RADIUS)
	var stats := city.get_stats()
	_log("Weltszene aufgebaut in %d ms (%d Chunks, Ø %.1f ms je Chunk)" % [Time.get_ticks_msec() - t0, stats.loaded, stats.avg_gen_ms])
	if player.has_method("teleport"):
		player.teleport(spawn_pos, spawn_yaw)
	_load_ui()
	_start_actors()
	if game and game.has_method("world_scene_ready") and not _standalone:
		game.world_scene_ready()
	elif game and _standalone and game.has_method("world_scene_ready"):
		game.world_scene_ready()


func _choose_spawn() -> void:
	var c := world.city
	var p := Vector2.ZERO
	var found := false
	# Spielerheim, falls das Wohnungssystem eines kennt
	if world.player and world.housing and "home" in world.player:
		var hid = world.player.get("home")
		if hid != null and int(hid) >= 0 and world.housing.has_method("get_property"):
			var prop: Dictionary = world.housing.get_property(int(hid))
			if prop.has("building") and int(prop.building) >= 0:
				var b: Dictionary = c.buildings[int(prop.building)]
				p = b.sidewalk_point
				found = true
	if not found:
		for sp in c.spawn_points:
			if sp.label == "station":
				p = sp.pos
				found = true
				break
	if not found and not c.spawn_points.is_empty():
		p = c.spawn_points[0].pos
	spawn_pos = Vector3(p.x, 0.2, p.y)
	# Blick zur Strasse
	var ne := c.nearest_edge(p)
	if ne.edge >= 0:
		var e: Dictionary = c.edges[ne.edge]
		var q := c.edge_point(e, ne.t)
		var d := q - p
		spawn_yaw = atan2(-d.x, -d.y) if d.length() > 0.1 else 0.0


func _spawn_player() -> void:
	if ResourceLoader.exists(PLAYER_SCENE):
		var ps: PackedScene = load(PLAYER_SCENE)
		player = ps.instantiate()
		actors.add_child(player)
		if not player.is_in_group("player"):
			player.add_to_group("player")
	else:
		var cam := DebugFlyCamera.new()
		cam.name = "DebugFlyCamera"
		actors.add_child(cam)
		cam.make_current()
		player = cam
	if player is Node3D:
		player.global_position = spawn_pos + Vector3(0, 1.7 if player is Camera3D else 0.0, 0)


func _load_ui() -> void:
	if ResourceLoader.exists(UI_SCENE):
		var s: PackedScene = load(UI_SCENE)
		ui.add_child(s.instantiate())


func _start_actors() -> void:
	for path in [TRAFFIC_SCRIPT, CROWD_SCRIPT]:
		if not ResourceLoader.exists(path):
			continue
		var scr: Script = load(path)
		if scr == null or not scr.can_instantiate():
			continue
		var node = scr.new()
		if node is Node:
			node.name = path.get_file().get_basename().to_pascal_case()
			actors.add_child(node)
			if node.has_method("setup"):
				node.setup(world, city)


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("debug_city"):
		city.toggle_debug_overlay()
		get_viewport().set_input_as_handled()


func _log(msg: String) -> void:
	var lg := get_node_or_null("/root/Log")
	if lg:
		lg.info("world", msg)
	else:
		print(msg)
