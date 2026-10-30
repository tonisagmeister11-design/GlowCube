extends Node3D
## Vergleichsmessung der Leistungsstufen: baut dieselbe Stadtstelle in jeder
## Stufe auf und misst Aufbauzeit, sichtbare Objekte, Dreiecke und Bildzeit.
## Aufruf: xvfb-run ... res://tests/visual/perf_bench.tscn -- --out=/tmp/x

var out := "/tmp"


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
	var city := CityGenerator.generate(12345, {"size": "medium"})
	var spawn := Vector3(city.spawn_points[0].pos.x, 1.6, city.spawn_points[0].pos.y)
	var env := DayNight.new()
	add_child(env)
	var cam := Camera3D.new()
	cam.position = spawn + Vector3(0, 2.0, 0)
	cam.rotation_degrees = Vector3(-6, 35, 0)
	cam.far = 2000.0
	add_child(cam)
	cam.current = true
	var lines := []
	for lvl in Perf.LEVELS:
		Perf.set_level(lvl, false)
		Perf.apply()
		await get_tree().process_frame
		var cb := CityBuilder.new()
		add_child(cb)
		cb.setup(city, null)
		var t0 := Time.get_ticks_msec()
		cb.build_area_now(spawn, Perf.view_distance())
		var build_ms := Time.get_ticks_msec() - t0
		# Einige Bilder rendern und die Bildzeit messen
		var frames := 0
		var ft := 0.0
		for i in 12:
			var f0 := Time.get_ticks_usec()
			await get_tree().process_frame
			if i >= 4:
				ft += float(Time.get_ticks_usec() - f0) / 1000.0
				frames += 1
		var prims := RenderingServer.get_rendering_info(RenderingServer.RENDERING_INFO_TOTAL_PRIMITIVES_IN_FRAME)
		var draws := RenderingServer.get_rendering_info(RenderingServer.RENDERING_INFO_TOTAL_DRAW_CALLS_IN_FRAME)
		var st: Dictionary = cb.get_stats()
		var line := "Stufe %d %-16s Sicht %4.0f m  Aufbau %5d ms  Chunks %3d  Dreiecke %8d  Zeichenaufrufe %5d  Bildzeit %6.1f ms" % [
			lvl, Perf.NAMES[lvl], Perf.view_distance(), build_ms, int(st.get("loaded", 0)), prims, draws,
			ft / maxf(frames, 1)]
		print(line)
		lines.append(line)
		if lvl == 0 or lvl == 2:
			get_viewport().get_texture().get_image().save_png("%s/perf_level%d.png" % [out, lvl])
		cb.queue_free()
		await get_tree().process_frame
		await get_tree().process_frame
	var f := FileAccess.open(out + "/perf_bench.txt", FileAccess.WRITE)
	if f:
		for l in lines:
			f.store_line(l)
	get_tree().quit()
