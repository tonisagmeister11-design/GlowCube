extends Node
## Bildkontrolle Verkehr: neues Spiel starten, dann Kreuzung mit Ampel,
## Kreisverkehr, Bus an der Haltestelle, Modellreihe, Fussgaenger und Nacht
## fotografieren. Aufruf:
##   xvfb-run -a -s "-screen 0 1600x900x24" $GODOT --rendering-driver opengl3 --path . res://tests/visual/traffic_shots.tscn -- --out=/tmp/x

var out := "/tmp"


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
	get_tree().root.add_child.call_deferred(_Driver.new(out))
	Game.start_new_game({"seed": 777, "mode": "sandbox", "rules": {"city_size": "medium"},
		"character": {"name": "Mara Lindner", "body": "female", "age": 24.0, "appearance": {}},
		"start": {"situation": "own_apartment", "money": 250000.0}})


class _Driver extends Node:
	var out := ""
	var t := 0.0
	var step := 0
	var cam: Camera3D
	var tm: TrafficManager
	var player: Node3D
	var w: WorldState
	var shots := []
	var cur := {}

	func _init(o: String) -> void:
		out = o

	func _shot(name: String) -> void:
		get_viewport().get_texture().get_image().save_png(out + "/" + name + ".png")
		var vcount: int = tm.sim.vehicles.size() if tm else 0
		var crowd := get_tree().get_first_node_in_group("crowd_manager")
		print("SHOT ", name, " vehicles=", vcount, " peds=", crowd.visible_count() if crowd else -1,
			" step_ms=", tm.stats.step_ms if tm else 0.0, " fps=", Engine.get_frames_per_second())

	func _near_node(kind: String, from: Vector2) -> Dictionary:
		var best := {}
		var bd := INF
		for n in w.city.nodes:
			if n.kind == kind and n.edges.size() == 4:
				var d: float = n.pos.distance_to(from)
				if d < bd:
					bd = d
					best = n
		return best

	func _go(p: Vector2, cam_pos: Vector3, look: Vector3) -> void:
		player.teleport(Vector3(p.x, 0.2, p.y), 0.0)
		cam.global_position = cam_pos
		cam.look_at(look)

	func _process(delta: float) -> void:
		t += delta
		match step:
			0:
				if Game.playing:
					w = Game.world
					player = get_tree().get_first_node_in_group("player")
					tm = get_tree().get_first_node_in_group("traffic_manager")
					cam = Camera3D.new()
					cam.fov = 55.0
					get_tree().root.add_child(cam)
					cam.make_current()
					w.time_minutes = 9.5 * 60.0
					var p0 := Vector2(player.global_position.x, player.global_position.z)
					var sig := _near_node("signal", p0)
					var rb := _near_node("roundabout", p0)
					var sp: Vector2 = sig.pos
					shots.append({"name": "intersection", "p": sp + Vector2(22, 22), "cam": Vector3(sp.x + 30, 22, sp.y + 30), "look": Vector3(sp.x, 0, sp.y), "wait": 14.0})
					if not rb.is_empty():
						var rp: Vector2 = rb.pos
						shots.append({"name": "roundabout", "p": rp + Vector2(15, 15), "cam": Vector3(rp.x + 18, 20, rp.y + 22), "look": Vector3(rp.x, 0, rp.y), "wait": 14.0})
					shots.append({"name": "bus_stop", "bus": true, "wait": 8.0, "warm": 40})
					shots.append({"name": "lineup", "lineup": true, "wait": 4.0, "warm": 0})
					shots.append({"name": "street_level", "p": sp + Vector2(10, 14), "cam": Vector3(sp.x + 9, 1.7, sp.y + 26), "look": Vector3(sp.x - 2, 1.0, sp.y), "wait": 6.0})
					shots.append({"name": "night", "p": sp + Vector2(22, 22), "cam": Vector3(sp.x + 26, 12, sp.y + 34), "look": Vector3(sp.x, 0, sp.y), "wait": 6.0, "night": true})
					step = 1
					t = 0.0
				elif t > 150.0:
					print("TIMEOUT")
					get_tree().quit(1)
			1:
				if shots.is_empty():
					get_tree().quit(0)
					return
				cur = shots.pop_front()
				if cur.get("night", false):
					w.time_minutes = 22.3 * 60.0
				if cur.get("bus", false):
					_setup_bus_shot()
				elif cur.get("lineup", false):
					_setup_lineup()
				else:
					_go(cur.p, cur.cam, cur.look)
				t = 0.0
				step = 2
				_warm(cur.get("warm", 300))
			2:
				if cur.get("bus", false) and t > 1.0:
					_follow_bus()
				if t > 2.5:
					_shot(cur.name)
					step = 1

	var _bus_stop := -1

	func _setup_bus_shot() -> void:
		# Haltestelle, an der als naechstes ein Bus ankommt
		var p0 := Vector2(player.global_position.x, player.global_position.z)
		var best := -1
		var bm := INF
		for st in w.city.bus_stops:
			if st.pos.distance_to(p0) > 900.0:
				continue
			var arr := tm.transit.next_arrivals(w, st.id, 1)
			if not arr.is_empty() and arr[0].minutes < bm:
				bm = arr[0].minutes
				best = st.id
		if best < 0:
			best = 0
		_bus_stop = best
		var st2: Dictionary = w.city.bus_stops[best]
		var wait: float = maxf(bm + 0.05, 0.0)
		w.time_minutes += wait
		var dir: Vector2 = st2.dir
		var side := CityModel.right_of(dir)
		var p: Vector2 = st2.pos + side * 1.5 - dir * 4.0
		_go(p, Vector3(st2.pos.x + side.x * 9.0 - dir.x * 16.0, 4.5, st2.pos.y + side.y * 9.0 - dir.y * 16.0),
			Vector3(st2.curb_pos.x, 1.4, st2.curb_pos.y))

	func _follow_bus() -> void:
		pass

	## Simulation vorspulen (llvmpipe rendert nur ~1 Bild/s).
	func _warm(n: int) -> void:
		var crowd := get_tree().get_first_node_in_group("crowd_manager")
		for i in n:
			w.time_minutes += 0.05
			tm._process(0.05)
			if crowd and i % 10 == 0:
				crowd._process(0.7)

	func _setup_lineup() -> void:
		var p0 := Vector2(player.global_position.x, player.global_position.z)
		var types := ["bicycle", "motorcycle", "hatchback", "sedan", "taxi", "police", "suv", "sports", "luxury", "van", "ambulance", "truck", "fire", "bus"]
		var base := Vector3(p0.x, 0.0, p0.y)
		var holder := Node3D.new()
		get_tree().root.add_child(holder)
		var x := 0.0
		var rng := RandomNumberGenerator.new()
		rng.seed = 5
		# Freie Flaeche ueber der Stadt (y = 0, Fahrzeuge stehen nebeneinander)
		for i in types.size():
			var ty: String = types[i]
			var vis := VehicleVisual.new()
			vis.build(ty, VehicleCatalog.paint_for(ty, rng), i)
			holder.add_child(vis)
			var wd: float = VehicleCatalog.type_info(ty).width
			x += wd * 0.5 + 0.9
			vis.global_position = base + Vector3(x, 60.0, 0)
			vis.rotation.y = -PI * 0.38
			if ty == "bus":
				vis.set_display("3  Hauptbahnhof", Color(0.15, 0.5, 0.85))
			x += wd * 0.5 + 0.9
		var floor := MeshInstance3D.new()
		var pm := PlaneMesh.new()
		pm.size = Vector2(80, 40)
		floor.mesh = pm
		var fm := StandardMaterial3D.new()
		fm.albedo_color = Color(0.32, 0.33, 0.35)
		floor.material_override = fm
		holder.add_child(floor)
		floor.global_position = base + Vector3(x * 0.5, 59.99, 0)
		cam.global_position = base + Vector3(x * 0.5, 64.5, 17.0)
		cam.look_at(base + Vector3(x * 0.5, 60.8, 0))
