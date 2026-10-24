extends Node3D
## Screenshot-Hilfsszene fuer die visuelle Pruefung der Stadt.
## Aufruf:
##   xvfb-run -a -s "-screen 0 1600x900x24" $GODOT --rendering-driver opengl3 --path . \
##     res://tests/visual/shot_city.tscn -- --out=/tmp/shots --views=downtown,night --seed=12345 --size=medium
## Speichert je Ansicht <out>/<name>.png und beendet sich danach.

var city: CityModel
var builder: CityBuilder
var daynight: DayNight
var cam: Camera3D
var out_dir := "/tmp"
var views := ["downtown", "residential", "river", "market", "intersection", "aerial", "night", "golden"]


func _ready() -> void:
	var seed_v := 12345
	var size := "medium"
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out_dir = a.substr(6)
		elif a.begins_with("--views="):
			views = Array(a.substr(8).split(","))
		elif a.begins_with("--seed="):
			seed_v = int(a.substr(7))
		elif a.begins_with("--size="):
			size = a.substr(7)
	DirAccess.make_dir_recursive_absolute(out_dir)
	var t0 := Time.get_ticks_msec()
	city = CityGenerator.generate(seed_v, {"size": size})
	print("Stadt generiert: %d ms" % (Time.get_ticks_msec() - t0))
	daynight = DayNight.new()
	add_child(daynight)
	cam = Camera3D.new()
	cam.far = 6000.0
	cam.fov = 62.0
	add_child(cam)
	cam.make_current()
	builder = CityBuilder.new()
	add_child(builder)
	t0 = Time.get_ticks_msec()
	builder.setup(city, cam)
	builder.stream_enabled = false
	print("Global + Fernebene gestartet: %d ms" % (Time.get_ticks_msec() - t0))
	_run.call_deferred()


func _find(kind: String) -> Dictionary:
	var best := {}
	for b in city.buildings:
		if b.kind == kind:
			if best.is_empty() or b.height > best.height:
				best = b
	return best


func _view(name: String) -> Dictionary:
	var hour := 11.0
	var weather := "clear"
	var pos := Vector3.ZERO
	var target := Vector3.ZERO
	match name:
		"downtown", "night":
			var b := _find("tower")
			if b.is_empty():
				b = _find("office")
			var sp: Vector2 = b.sidewalk_point
			var fd: Vector2 = b.front_dir
			var side := Vector2(-fd.y, fd.x)
			pos = Vector3(sp.x, 1.7, sp.y) + Vector3(fd.x, 0, fd.y) * 8.0 + Vector3(side.x, 0, side.y) * 30.0
			target = Vector3(b.footprint.get_center().x, 25.0, b.footprint.get_center().y)
			if name == "night":
				hour = 21.5
				pos += Vector3(0, 6, 0)
				target = Vector3(sp.x, 8.0, sp.y) - Vector3(side.x, 0, side.y) * 40.0
		"residential":
			var b2 := {}
			for b in city.buildings:
				if b.kind == "house" and b.style.roof == "gable":
					b2 = b
					break
			var sp2: Vector2 = b2.sidewalk_point
			var fd2: Vector2 = b2.front_dir
			var side2 := Vector2(-fd2.y, fd2.x)
			pos = Vector3(sp2.x, 1.7, sp2.y) + Vector3(fd2.x, 0, fd2.y) * 4.0 - Vector3(side2.x, 0, side2.y) * 18.0
			target = Vector3(b2.door.x, 3.0, b2.door.y) + Vector3(side2.x, 0, side2.y) * 10.0
			hour = 16.0
		"river", "golden":
			var e := {}
			for ed in city.edges:
				if ed.bridge and ed.cls == "arterial":
					e = ed
					break
			if e.is_empty():
				for ed in city.edges:
					if ed.bridge:
						e = ed
						break
			var x: float = city.nodes[e.a].pos.x
			var zn: float = city.river.z_north
			pos = Vector3(x + 85.0, 2.2, zn - 3.0)
			target = Vector3(x, -1.0, (zn + city.river.z_south) * 0.5)
			if name == "golden":
				hour = 19.2
				pos = Vector3(x - 120.0, 25.0, city.river.z_south + 40.0)
				target = Vector3(x + 150.0, 20.0, zn - 150.0)
		"market":
			var m := _find("market_square")
			var c: Vector2 = m.footprint.get_center()
			var fd3: Vector2 = m.front_dir
			pos = Vector3(c.x, 5.0, c.y) + Vector3(fd3.x, 0, fd3.y) * (m.footprint.size.length() * 0.5 + 6.0)
			target = Vector3(c.x, 1.0, c.y)
			hour = 10.0
		"intersection":
			var n := {}
			var best := INF
			for nd in city.nodes:
				if nd.kind == "signal":
					var d: float = nd.pos.length()
					if d < best:
						best = d
						n = nd
			var p: Vector2 = n.pos
			pos = Vector3(p.x + 22.0, 7.0, p.y + 26.0)
			target = Vector3(p.x, 0.0, p.y)
			hour = 14.0
		"aerial":
			var b4 := _find("tower")
			var c4: Vector2 = b4.footprint.get_center() if not b4.is_empty() else Vector2.ZERO
			pos = Vector3(c4.x + 450.0, 260.0, c4.y + 520.0)
			target = Vector3(c4.x, 0.0, c4.y)
			hour = 15.0
		"rain":
			var b5 := _find("mixed")
			var sp5: Vector2 = b5.sidewalk_point
			pos = Vector3(sp5.x, 1.7, sp5.y) + Vector3(b5.front_dir.x, 0, b5.front_dir.y) * 5.0
			target = Vector3(b5.door.x, 4.0, b5.door.y)
			weather = "rain"
			hour = 17.0
	return {"pos": pos, "target": target, "hour": hour, "weather": weather}


func _run() -> void:
	var frame_times := []
	for v in views:
		var spec := _view(v)
		daynight.set_weather(spec.weather, true)
		daynight.set_hour(spec.hour)
		cam.global_position = spec.pos
		cam.look_at(spec.target, Vector3.UP)
		var t0 := Time.get_ticks_msec()
		builder.build_area_now(spec.pos, 420.0 if v != "aerial" else 700.0)
		print("[%s] Chunks gebaut: %d ms, %s" % [v, Time.get_ticks_msec() - t0, str(builder.get_stats())])
		for i in 14:
			await get_tree().process_frame
		var tf := Time.get_ticks_msec()
		for i in 4:
			await get_tree().process_frame
		frame_times.append((Time.get_ticks_msec() - tf) / 4.0)
		var img := get_viewport().get_texture().get_image()
		img.save_png("%s/%s.png" % [out_dir, v])
		print("[%s] gespeichert, Frame %.1f ms" % [v, frame_times[-1]])
	get_tree().quit()
