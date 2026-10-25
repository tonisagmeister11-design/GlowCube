extends Node
## Startet ein echtes Spiel in einer Leistungsstufe und misst, was in der
## fertigen Welt ankommt: Bildzeit, Dreiecke, Passanten, Fahrzeuge.
## Aufruf: ... res://tests/visual/perf_live.tscn -- --out=/tmp/x --level=0

var out := "/tmp"
var lvl := 0


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
		elif a.begins_with("--level="):
			lvl = int(a.substr(8))
	Perf.set_level(lvl, false)
	get_tree().root.add_child.call_deferred(_Probe.new(out, lvl))
	Game.start_new_game({"seed": 777, "mode": "life", "rules": {"city_size": "medium"},
		"character": {"name": "Mara Lindner", "body": "female", "age": 24.0, "appearance": {}},
		"start": {"situation": "own_apartment"}})


class _Probe extends Node:
	var out := ""
	var lvl := 0
	var t := 0.0
	var step := 0
	var frames := 0
	var ft := 0.0

	func _init(o: String, l: int) -> void:
		out = o
		lvl = l

	func _process(delta: float) -> void:
		t += delta
		if step == 0:
			if Game.playing:
				t = 0.0
				step = 1
			elif t > 180.0:
				print("TIMEOUT")
				get_tree().quit(1)
			return
		if step == 1 and t > 8.0:
			t = 0.0
			step = 2
			return
		if step == 2:
			ft += delta * 1000.0
			frames += 1
			if t > 6.0:
				var prims := RenderingServer.get_rendering_info(RenderingServer.RENDERING_INFO_TOTAL_PRIMITIVES_IN_FRAME)
				var draws := RenderingServer.get_rendering_info(RenderingServer.RENDERING_INFO_TOTAL_DRAW_CALLS_IN_FRAME)
				var peds := get_tree().get_nodes_in_group("npc").size()
				var veh := get_tree().get_nodes_in_group("vehicle").size()
				print("LIVE Stufe %d (%s): Bildzeit %.1f ms, Dreiecke %d, Zeichenaufrufe %d, Passanten %d, Fahrzeuge %d, Sicht %.0f m" % [
					lvl, Perf.NAMES[lvl], ft / maxf(frames, 1), prims, draws, peds, veh, Perf.view_distance()])
				get_viewport().get_texture().get_image().save_png("%s/live_level%d.png" % [out, lvl])
				get_tree().quit(0)
