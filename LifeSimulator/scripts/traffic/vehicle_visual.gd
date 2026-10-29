class_name VehicleVisual
extends Node3D
## Darstellung eines Fahrzeugs: Karosserie aus VehicleMeshBuilder, drehende
## Raeder, Scheinwerfer/Rueckleuchten (nachts leuchtend), Bremslichter,
## Blinker, Blaulicht, Aufschriften (Label3D), Bustueren und Zielanzeige.
## Enthaelt keine Spiellogik; KI (VehicleAI) oder Spielerfahrzeug setzen den
## Zustand ueber set_state().

signal arrived                        # Einsatzfahrzeug am Ziel (vom TrafficManager)

var type_id := "sedan"
var model: Dictionary = {}
var body: MeshInstance3D
var wheels: Array = []                # [{node: Node3D, spin: Node3D, r, steer}]
var ai: VehicleAI                     # gesetzt bei KI-Fahrzeugen
var collider: AnimatableBody3D
var vehicle_id := -1
var _surf: Dictionary = {}
var _doors: Array = []
var _door_open := 0.0
var _beams: MeshInstance3D
var _blink_t := 0.0
var _beacon_t := 0.0
var _state_key := ""
var _display: Label3D
var _display_side: Label3D
var color := Color.WHITE


## Baut ein aufbereitetes Fremdmodell ein: Karosserie als fertige Szene,
## Raeder als drehbare Knoten. Gibt false zurueck, wenn die Szene fehlt.
func _build_imported(model_id: String, p_color: Color) -> bool:
	var node := ImportedVehicles.build(model_id, p_color)
	if node == null:
		return false
	var info := ImportedVehicles.info(model_id)
	var t := VehicleCatalog.type_info(str(info.get("type", "sports")))
	model = {"length": float(t.length), "width": float(t.width), "height": float(t.height),
		"wheels": [], "lights": {"head": [], "tail": []}, "doors": [], "labels": [], "beacons": false,
		"surf": {}, "mesh": null, "imported": true}
	_surf = {}
	add_child(node)
	node.name = "ImportedBody"
	# Raeder der Szene uebernehmen, damit sie sich drehen
	for slot in ["WheelFL", "WheelFR", "WheelRL", "WheelRR"]:
		var w := node.get_node_or_null(slot)
		if w == null:
			continue
		var is_front: bool = slot in ["WheelFL", "WheelFR"]
		var r := 0.34
		var mi := w.get_node_or_null("Mesh")
		if mi is MeshInstance3D and (mi as MeshInstance3D).mesh != null:
			r = maxf(0.2, (mi as MeshInstance3D).mesh.get_aabb().size.y * 0.5)
		wheels.append({"node": w, "spin": mi if mi != null else w, "r": r, "steer": is_front})
	return true


func build(p_type: String, p_color: Color, variant := 0, opts := {}) -> void:
	type_id = p_type
	color = p_color
	# Zugeliefertes Modell, falls fuer dieses Fahrzeug eines hinterlegt ist
	var imported_id := str(opts.get("model_id", ""))
	if imported_id != "" and ImportedVehicles.has_model(imported_id):
		if _build_imported(imported_id, p_color):
			return
	model = VehicleMeshBuilder.get_model(p_type)
	_surf = model.surf
	body = MeshInstance3D.new()
	body.name = "Body"
	body.mesh = model.mesh
	add_child(body)
	if _surf.has("paint"):
		body.set_surface_override_material(_surf.paint, VehicleMeshBuilder.paint_material(p_color))
	if opts.has("stripe_color") and _surf.has("stripe"):
		body.set_surface_override_material(_surf.stripe, VehicleMeshBuilder.paint_material(opts.stripe_color, 0.1))
	if _surf.has("rider"):
		var rc := Color.from_hsv(float(variant % 97) / 97.0, 0.45, 0.45)
		body.set_surface_override_material(_surf.rider, VehicleMeshBuilder.paint_material(rc, 0.0))
	for wd in model.wheels:
		var pivot := Node3D.new()
		pivot.position = wd.pos
		add_child(pivot)
		var spin := MeshInstance3D.new()
		spin.mesh = model.wheel_mesh
		pivot.add_child(spin)
		wheels.append({"node": pivot, "spin": spin, "r": float(wd.r), "steer": bool(wd.steer)})
	for lb in model.labels:
		_add_label(lb)
	if model.get("livery", false):
		var lv: Dictionary = VehicleCatalog.LIVERIES[absi(variant) % VehicleCatalog.LIVERIES.size()]
		body.set_surface_override_material(_surf.get("cargo", _surf.get("paint", 0)), VehicleMeshBuilder.paint_material(lv.color, 0.1))
		var hw: float = model.width * 0.5
		var y: float = 1.6 if p_type == "van" else 2.2
		for sx in [-1.0, 1.0]:
			_add_label({"text": lv.text, "pos": Vector3(sx * (hw + 0.02), y, 0.6 if p_type == "van" else 1.2),
				"rot": sx * PI * 0.5, "size": 0.3 if p_type == "van" else 0.5, "color": lv.text_color})
	if p_type == "bus":
		for dd in model.doors:
			var door := MeshInstance3D.new()
			var bm := BoxMesh.new()
			bm.size = Vector3(0.03, dd.h, dd.w * 0.5)
			door.mesh = bm
			door.material_override = VehicleMeshBuilder.material("door")
			for k in 2:
				var leaf := door.duplicate() as MeshInstance3D
				leaf.position = Vector3(dd.x + 0.02, dd.y, dd.z + (k - 0.5) * dd.w * 0.5)
				add_child(leaf)
				_doors.append({"node": leaf, "base": leaf.position, "dir": (k - 0.5) * 2.0, "w": dd.w * 0.5})
			door.free()
		_display = _add_label({"text": "", "pos": Vector3(0, 2.86, -model.length * 0.5 - 0.01), "rot": 0.0, "size": 0.2,
			"color": Color(1.0, 0.68, 0.15), "glow": true})
		_display_side = _add_label({"text": "", "pos": Vector3(model.width * 0.5 + 0.03, 2.9, -model.length * 0.5 + 2.3),
			"rot": PI * 0.5, "size": 0.15, "color": Color(1.0, 0.68, 0.15), "glow": true})
	# Lichtkegel auf der Fahrbahn (nachts)
	if not model.lights.head.is_empty() and p_type != "bicycle":
		_beams = MeshInstance3D.new()
		var q := QuadMesh.new()
		q.size = Vector2(model.width * 1.6, 9.0)
		_beams.mesh = q
		_beams.material_override = VehicleMeshBuilder.material("beam")
		_beams.rotation = Vector3(-PI * 0.5, 0, 0)
		_beams.position = Vector3(0, 0.05, -model.length * 0.5 - 4.6)
		_beams.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		_beams.visible = false
		add_child(_beams)
	if opts.get("collider", false):
		add_collider()


func _add_label(lb: Dictionary) -> Label3D:
	var l := Label3D.new()
	l.text = lb.text
	l.position = lb.pos
	l.rotation.y = lb.rot
	l.pixel_size = float(lb.size) / 40.0
	l.font_size = 48
	l.outline_size = 0
	l.modulate = lb.color
	l.double_sided = false
	l.shaded = not lb.get("glow", false)
	l.alpha_cut = Label3D.ALPHA_CUT_OPAQUE_PREPASS
	add_child(l)
	return l


## Kollisionskoerper (kinematisch) fuer KI- und geparkte Fahrzeuge.
func add_collider(layer := 1) -> void:
	if collider:
		return
	collider = AnimatableBody3D.new()
	collider.sync_to_physics = false
	collider.collision_layer = layer
	collider.collision_mask = 0
	var cs := CollisionShape3D.new()
	var bs := BoxShape3D.new()
	bs.size = Vector3(model.width, model.height * 0.8, model.length)
	cs.shape = bs
	cs.position = Vector3(0, model.height * 0.45, 0)
	collider.add_child(cs)
	add_child(collider)


## Zielanzeige des Busses, z.B. "3  Hauptbahnhof".
func set_display(text: String, line_color := Color(0.85, 0.2, 0.25)) -> void:
	if _display:
		_display.text = text
	if _display_side:
		_display_side.text = text
	if _surf.has("stripe"):
		body.set_surface_override_material(_surf.stripe, VehicleMeshBuilder.paint_material(line_color, 0.1))


func set_paint(c: Color) -> void:
	color = c
	if _surf.has("paint"):
		body.set_surface_override_material(_surf.paint, VehicleMeshBuilder.paint_material(c))


## Zustand fuer die Darstellung. blinker: -1 links, 1 rechts, 2 Warnblinker.
func set_state(speed: float, steer: float, braking: bool, blinker: int, night: bool, siren: bool, doors_open: bool, delta: float) -> void:
	# Raeder
	for w in wheels:
		var spin: Node3D = w.spin
		spin.rotate_x(-speed * delta / maxf(w.r, 0.1))
		if w.steer:
			(w.node as Node3D).rotation.y = steer
	_blink_t += delta
	_beacon_t += delta
	var blink_on := fmod(_blink_t, 0.7) < 0.38
	var beacon_phase := fmod(_beacon_t, 0.5) < 0.25
	var key := "%d%d%d%d%d%d" % [int(night), int(braking), blinker if blink_on else 0, int(siren), int(beacon_phase) if siren else 0, int(doors_open)]
	if key != _state_key:
		_state_key = key
		_apply_lights(night, braking, blinker if blink_on else 0, siren, beacon_phase)
	if _beams:
		_beams.visible = night
	# Tueren
	if not _doors.is_empty():
		_door_open = move_toward(_door_open, 1.0 if doors_open else 0.0, delta * 1.6)
		for d in _doors:
			var n: Node3D = d.node
			n.position = d.base + Vector3(0.12 * _door_open, 0, d.dir * d.w * 0.85 * _door_open)


func _apply_lights(night: bool, braking: bool, blink: int, siren: bool, phase: bool) -> void:
	if _surf.has("head"):
		body.set_surface_override_material(_surf.head, VehicleMeshBuilder.material("head_on" if night else "head_off"))
	if _surf.has("tail"):
		var t := "brake" if braking else ("tail_on" if night else "tail_off")
		body.set_surface_override_material(_surf.tail, VehicleMeshBuilder.material(t))
	if _surf.has("blink_l"):
		body.set_surface_override_material(_surf.blink_l, VehicleMeshBuilder.material("blink_on" if blink == -1 or blink == 2 else "blink_off"))
	if _surf.has("blink_r"):
		body.set_surface_override_material(_surf.blink_r, VehicleMeshBuilder.material("blink_on" if blink == 1 or blink == 2 else "blink_off"))
	if _surf.has("beacon_a"):
		body.set_surface_override_material(_surf.beacon_a, VehicleMeshBuilder.material("beacon_blue" if siren and phase else "beacon_off"))
	if _surf.has("beacon_b"):
		body.set_surface_override_material(_surf.beacon_b, VehicleMeshBuilder.material("beacon_blue" if siren and not phase else "beacon_off"))


## Uebernimmt Pose und Lichtzustand eines KI-Fahrzeugs.
func sync_ai(night: bool, delta: float) -> void:
	if ai == null:
		return
	global_position = Vector3(ai.pos.x, 0.0, ai.pos.y)
	var yaw := atan2(-ai.heading.x, -ai.heading.y)
	var yaw_rate := wrapf(yaw - rotation.y, -PI, PI) / maxf(delta, 0.001)
	rotation = Vector3(0, yaw, 0)
	var wb: float = model.length * 0.6
	var steer := clampf(atan(yaw_rate * wb / maxf(ai.v, 1.0)), -0.55, 0.55) if ai.v > 0.2 else 0.0
	set_state(ai.v, steer, ai.braking, ai.blinker, night, ai.siren or (ai.role == "emergency" and ai.arrived), ai.doors_open, delta)
