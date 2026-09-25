class_name DebugTools
extends CanvasLayer
## Developer tools, only active in development builds (Game.debug_enabled):
##   F1  debug overlay (FPS, frame time, draw calls, position, chunk, district, AI counts)
##   F2  spawn the next vehicle type in front of the player
##   F3  spawn a pedestrian (cycles roles)
##   F4  all weapons + ammo + armour + $10.000
##   F6  wanted level +1 (cycles 0..5)
##   F7  teleport to the GPS waypoint (or the next POI)
##   F8  complete the running mission / start the next story mission
##   F9  toggle traffic & pedestrian AI
##   F10 performance overlay (monitors)
##   F11 time +3 h, weather cycles with Shift+F11

const VEHICLES := ["compact", "sedan", "luxury", "sports", "supercar", "suv", "pickup", "van", "truck", "bus", "taxi",
	"police", "ambulance", "fire_truck", "delivery", "motorcycle"]
const ROLES := ["civilian", "business", "worker", "tourist", "jogger", "gang", "cop"]
const WEATHER := ["sunny", "cloudy", "rain", "heavy_rain", "fog", "storm"]

var world: GameWorld
var _label: Label
var _mode := 0            # 0 off, 1 debug, 2 perf
var _veh_i := 0
var _role_i := 0
var _poi_i := 0
var _weather_i := 0
var _ai_on := true


func _ready() -> void:
	name = "DebugTools"
	layer = 90
	world = GameWorld.instance
	if not Game.debug_enabled:
		set_process(false)
		set_process_unhandled_input(false)
		return
	_label = Label.new()
	_label.position = Vector2(12, 300)
	_label.add_theme_font_size_override("font_size", 15)
	_label.add_theme_color_override("font_outline_color", Color.BLACK)
	_label.add_theme_constant_override("outline_size", 5)
	_label.visible = false
	add_child(_label)


func _unhandled_input(event: InputEvent) -> void:
	if Game.state != Game.State.PLAYING or not (event is InputEventKey) or not event.pressed or event.is_echo():
		return
	var p := world.player as Player
	if event.is_action_pressed("debug_hud"):
		_mode = 0 if _mode == 1 else 1
		_label.visible = _mode != 0
	elif event.is_action_pressed("debug_perf"):
		_mode = 0 if _mode == 2 else 2
		_label.visible = _mode != 0
	elif event.is_action_pressed("debug_spawn_vehicle"):
		var id: String = VEHICLES[_veh_i % VEHICLES.size()]
		_veh_i += 1
		var v := Vehicle.create(id)
		var fwd := -p.global_basis.z
		v.transform = Transform3D(Basis.looking_at(fwd.cross(Vector3.UP), Vector3.UP), p.global_position + fwd * 6.0 + Vector3.UP)
		world.add_child(v)
		Events.notify.emit("[DEBUG] Fahrzeug: " + VehicleDefs.display_name(id), 2.0)
	elif event.is_action_pressed("debug_spawn_npc"):
		var role: String = ROLES[_role_i % ROLES.size()]
		_role_i += 1
		var n: NPC = world.peds.call("spawn_npc", p.global_position - p.global_basis.z * 4.0 + Vector3.UP * 0.2, role, {}, false)
		if role in ["gang", "cop"]:
			n.weapons.give("pistol", 60)
		n.start_walking()
		Events.notify.emit("[DEBUG] NPC: " + role, 2.0)
	elif event.is_action_pressed("debug_give_weapon"):
		for id in WeaponData.WEAPONS:
			if id != "unarmed":
				p.weapons.give(id)
				if WeaponData.is_ranged(id):
					p.weapons.add_ammo(id, 300)
		p.health.add_armor(100.0)
		p.health.heal(1000.0)
		Game.player_data.add_money(10000, "debug")
		Events.notify.emit("[DEBUG] Waffen, Munition, Weste, $10.000", 2.0)
	elif event.is_action_pressed("debug_wanted"):
		var lvl: int = (int(world.police.get("wanted_level")) + 1) % 6
		if lvl == 0:
			world.police.call("clear_wanted")
		else:
			world.police.call("set_wanted", lvl)
	elif event.is_action_pressed("debug_teleport"):
		var hud := world.hud as HUD
		var dest := Vector3.INF
		if hud and hud.gps.active():
			dest = hud.gps.target
		else:
			var poi: Dictionary = world.data.pois[_poi_i % world.data.pois.size()]
			_poi_i += 1
			dest = (poi["entrance_v"] as Vector3) + (poi["facing_v"] as Vector3) * 3.0
			Events.notify.emit("[DEBUG] Teleport: " + String(poi["name"]), 2.0)
		world.streaming.load_area_blocking(dest, 250.0)
		p.teleport(dest + Vector3.UP * 0.5)
	elif event.is_action_pressed("debug_mission"):
		var mm := world.missions as MissionManager
		if mm.is_active():
			mm.current.complete()
		else:
			var nxt := mm.next_story()
			if not nxt.is_empty():
				mm.start_story(nxt["id"])
	elif event.is_action_pressed("debug_toggle_ai"):
		_ai_on = not _ai_on
		world.traffic.call("set_enabled", _ai_on)
		world.peds.call("set_enabled", _ai_on)
		Events.notify.emit("[DEBUG] KI " + ("an" if _ai_on else "aus"), 2.0)
	elif event.is_action_pressed("debug_time"):
		if (event as InputEventKey).shift_pressed:
			_weather_i += 1
			world.weather.call("set_weather", WEATHER[_weather_i % WEATHER.size()])
			Events.notify.emit("[DEBUG] Wetter: " + WEATHER[_weather_i % WEATHER.size()], 2.0)
		else:
			world.day_night.call("advance_hours", 3.0)


func _process(_delta: float) -> void:
	if _mode == 0:
		return
	var p := world.player as Player
	var pos := p.global_position
	if _mode == 1:
		var d := world.data.district_at(pos)
		_label.text = "FPS %d  (%.1f ms)\nPos %.1f / %.1f / %.1f   Chunk %s\nBezirk %s   Zeit %s   Wetter %s\n" % [
			Engine.get_frames_per_second(), 1000.0 / maxf(Engine.get_frames_per_second(), 1.0),
			pos.x, pos.y, pos.z, str(world.data.chunk_of(pos)), d.get("name", "?"),
			world.day_night.call("clock_text"), world.weather.get("state")] + \
			"Chunks %d   Autos %d (%d gesamt)   Fußgänger %d\nFahndung %d   Mission %s\nZustand %d   Deckung %s" % [
			world.streaming.loaded_chunks().size(), world.traffic.get("drivers").size(), world.traffic.get("vehicles").size(),
			world.peds.call("count"), int(world.police.get("wanted_level")), world.missions.get("current_id"),
			p.state, str(p.in_cover)]
	else:
		_label.text = "FPS %d\nDraw Calls %d\nObjekte %d   Primitive %d\nVRAM %.0f MB   Texturen %.0f MB\nPhysik aktiv %d\nStatic Mem %.0f MB" % [
			Engine.get_frames_per_second(),
			Performance.get_monitor(Performance.RENDER_TOTAL_DRAW_CALLS_IN_FRAME),
			Performance.get_monitor(Performance.RENDER_TOTAL_OBJECTS_IN_FRAME),
			Performance.get_monitor(Performance.RENDER_TOTAL_PRIMITIVES_IN_FRAME),
			Performance.get_monitor(Performance.RENDER_VIDEO_MEM_USED) / 1048576.0,
			Performance.get_monitor(Performance.RENDER_TEXTURE_MEM_USED) / 1048576.0,
			Performance.get_monitor(Performance.PHYSICS_3D_ACTIVE_OBJECTS),
			Performance.get_monitor(Performance.MEMORY_STATIC) / 1048576.0]
