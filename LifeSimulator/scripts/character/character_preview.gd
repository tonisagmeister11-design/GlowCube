class_name CharacterPreview
extends Control
## 3D-Vorschau einer Figur in einem Fotostudio (Hohlkehle, Drei-Punkt-Licht).
## Ziehen mit der Maus dreht die Figur, das Mausrad zoomt. Optional dreht
## sich die Figur langsam von selbst. Aenderungen werden gebuendelt (hoechstens
## ein Neuaufbau pro Frame), damit Schieberegler fluessig bleiben.

signal character_rebuilt()

@export var auto_turn := false
@export var turn_speed := 0.35

var viewport: SubViewport
var container: SubViewportContainer
var humanoid: Humanoid
var camera: Camera3D
var _pivot: Node3D
var _yaw := 0.35
var _pitch := 0.06
var _zoom := 1.0
var _focus := "body"        ## "body" oder "face"
var _dragging := false
var _pending: Array = []
var _height := 1.7
var _eye := 1.6
var _cam_target := Vector3(0, 0.9, 0)
var _cam_dist := 4.0
var _action := "idle"
var _idle_t := 0.0


func _ready() -> void:
	clip_contents = true
	if size == Vector2.ZERO:
		custom_minimum_size = Vector2(320, 420)
	container = SubViewportContainer.new()
	container.stretch = true
	container.set_anchors_preset(Control.PRESET_FULL_RECT)
	container.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(container)
	viewport = SubViewport.new()
	viewport.own_world_3d = true
	viewport.msaa_3d = Viewport.MSAA_4X
	viewport.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	container.add_child(viewport)
	_build_studio()
	_pivot = Node3D.new()
	viewport.add_child(_pivot)
	camera = Camera3D.new()
	camera.fov = 28.0
	camera.near = 0.05
	viewport.add_child(camera)
	camera.current = true
	if _pending.is_empty() and humanoid == null:
		set_character(Appearance.defaults("female", 24.0), 24.0, "female")


func _build_studio() -> void:
	var we := WorldEnvironment.new()
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.13, 0.15, 0.19)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.62, 0.66, 0.75)
	env.ambient_light_energy = 0.42
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.tonemap_exposure = 1.05
	we.environment = env
	viewport.add_child(we)
	# Hohlkehle: Boden geht weich in die Rueckwand ueber
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	var prof: Array = []
	for i in 10:
		prof.append(Vector2(6.0 - i * 0.6, 0.0))
	var r := 2.5
	for i in range(1, 13):
		var a := PI * 0.5 * float(i) / 12.0
		prof.append(Vector2(-r * sin(a), r - r * cos(a)))
	prof.append(Vector2(-r, 6.0))
	var w := 7.0
	for k in prof.size() - 1:
		var a2: Vector2 = prof[k]
		var b2: Vector2 = prof[k + 1]
		var z0 := a2.x - 0.5
		var z1 := b2.x - 0.5
		var shade0 := Color(0.34, 0.37, 0.43).lerp(Color(0.22, 0.24, 0.29), clampf(a2.y / 5.0, 0.0, 1.0))
		var shade1 := Color(0.34, 0.37, 0.43).lerp(Color(0.22, 0.24, 0.29), clampf(b2.y / 5.0, 0.0, 1.0))
		var v := [Vector3(-w, a2.y, z0), Vector3(w, a2.y, z0), Vector3(w, b2.y, z1), Vector3(-w, b2.y, z1)]
		var c := [shade0, shade0, shade1, shade1]
		for idx in [0, 2, 1, 0, 3, 2]:
			st.set_color(c[idx])
			st.add_vertex(v[idx])
	st.generate_normals()
	var cyc := MeshInstance3D.new()
	cyc.mesh = st.commit()
	var m := StandardMaterial3D.new()
	m.vertex_color_use_as_albedo = true
	m.roughness = 0.95
	m.cull_mode = BaseMaterial3D.CULL_DISABLED
	cyc.material_override = m
	cyc.position.z = -1.6
	viewport.add_child(cyc)
	# Drei-Punkt-Licht
	var key := DirectionalLight3D.new()
	key.rotation_degrees = Vector3(-38, 35, 0)
	key.light_color = Color(1.0, 0.95, 0.88)
	key.light_energy = 1.15
	key.shadow_enabled = true
	key.directional_shadow_max_distance = 12.0
	viewport.add_child(key)
	var fill := OmniLight3D.new()
	fill.position = Vector3(-2.6, 1.6, 2.4)
	fill.light_color = Color(0.75, 0.84, 1.0)
	fill.light_energy = 0.55
	fill.omni_range = 9.0
	viewport.add_child(fill)
	var rim := SpotLight3D.new()
	rim.position = Vector3(1.2, 2.8, -2.4)
	rim.light_energy = 2.4
	rim.spot_range = 8.0
	rim.spot_angle = 40.0
	rim.light_color = Color(0.9, 0.93, 1.0)
	viewport.add_child(rim)
	rim.look_at(Vector3(0, 1.0, 0))


## Zeigt eine Figur. Mehrere Aufrufe pro Frame werden zusammengefasst.
func set_character(appearance: Dictionary, age: float, body: String) -> void:
	_pending = [appearance.duplicate(true), age, body]
	if not is_inside_tree():
		return
	set_process(true)


func set_auto_turn(on: bool) -> void:
	auto_turn = on


## "body" (Ganzkoerper) oder "face" (Nahaufnahme Gesicht).
func focus(mode: String) -> void:
	_focus = "face" if mode == "face" else "body"
	_zoom = 1.0


func get_focus() -> String:
	return _focus


## Animation der Vorschaufigur (idle, walk, wave, talk, sit ...).
func set_action(action: String) -> void:
	_action = action
	if humanoid:
		humanoid.set_action(action if action != "walk" else "idle")
		humanoid.set_motion(1.3 if action == "walk" else 0.0)


func get_humanoid() -> Humanoid:
	return humanoid


func _rebuild() -> void:
	var app: Dictionary = _pending[0]
	var age: float = _pending[1]
	var bodyk: String = _pending[2]
	_pending = []
	if humanoid == null:
		humanoid = Humanoid.new()
		_pivot.add_child(humanoid)
	humanoid.setup(app, age, bodyk)
	set_action(_action)
	_height = humanoid.height()
	_eye = humanoid.eye_height()
	character_rebuilt.emit()


func _process(delta: float) -> void:
	if _pending.size() == 3 and _pivot:
		_rebuild()
	if humanoid == null:
		return
	if auto_turn and not _dragging:
		_yaw += turn_speed * delta
	_pivot.rotation.y = _yaw
	var target: Vector3
	var dist: float
	if _focus == "face":
		var hh := humanoid.get_proportions().head_h if humanoid.get_proportions() else 0.23
		target = Vector3(0, _eye - hh * 0.05, 0)
		dist = hh * 6.2 / _zoom
	else:
		target = Vector3(0, _height * 0.52, 0)
		dist = (_height * 2.25 + 0.6) / _zoom
	var k := 1.0 - exp(-8.0 * delta)
	_cam_target = _cam_target.lerp(target, k)
	_cam_dist = lerpf(_cam_dist, dist, k)
	var dir := Vector3(0, sin(_pitch), cos(_pitch))
	camera.position = _cam_target + dir * _cam_dist
	camera.look_at(_cam_target)


func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton:
		if event.button_index == MOUSE_BUTTON_LEFT:
			_dragging = event.pressed
			accept_event()
		elif event.pressed and event.button_index == MOUSE_BUTTON_WHEEL_UP:
			_zoom = clampf(_zoom * 1.1, 0.6, 3.0)
			accept_event()
		elif event.pressed and event.button_index == MOUSE_BUTTON_WHEEL_DOWN:
			_zoom = clampf(_zoom / 1.1, 0.6, 3.0)
			accept_event()
	elif event is InputEventMouseMotion and _dragging:
		_yaw += event.relative.x * 0.01
		_pitch = clampf(_pitch + event.relative.y * 0.004, -0.25, 0.6)
		accept_event()
