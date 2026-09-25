class_name InteractMarker
extends Node3D
## Glowing ground marker the player can interact with (E). Used by shops, garages,
## safehouses, mission starts and bus stops. Vehicle markers (drive-in, e.g. the
## mechanic) are triggered by the owner system instead of the E key.

var prompt := "E: Interagieren"
var interact_radius := 2.2
var color := Color(1.0, 0.8, 0.2)
var on_interact: Callable
var condition: Callable
var vehicle_marker := false
var show_visual := true
var _ring: MeshInstance3D
var _t := randf() * 10.0

static var _mat_cache := {}


func _ready() -> void:
	if not vehicle_marker:
		add_to_group("interactable")
	if show_visual:
		_build_visual()


func _build_visual() -> void:
	var key := color.to_html()
	var mat: StandardMaterial3D = _mat_cache.get(key)
	if mat == null:
		mat = StandardMaterial3D.new()
		mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		mat.albedo_color = Color(color.r, color.g, color.b, 0.45)
		mat.cull_mode = BaseMaterial3D.CULL_DISABLED
		mat.no_depth_test = false
		_mat_cache[key] = mat
	_ring = MeshInstance3D.new()
	var cyl := CylinderMesh.new()
	var r := interact_radius * (0.55 if not vehicle_marker else 0.8)
	cyl.top_radius = r
	cyl.bottom_radius = r
	cyl.height = 0.9 if not vehicle_marker else 0.35
	cyl.cap_top = false
	cyl.cap_bottom = false
	cyl.radial_segments = 32
	cyl.material = mat
	_ring.mesh = cyl
	_ring.position.y = cyl.height * 0.5
	_ring.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	_ring.visibility_range_end = 120.0
	add_child(_ring)


func _process(delta: float) -> void:
	if _ring:
		_t += delta
		_ring.scale = Vector3.ONE * (1.0 + sin(_t * 3.0) * 0.04)


func can_interact(p: Node) -> bool:
	return not condition.is_valid() or bool(condition.call(p))


func get_prompt(_p: Node) -> String:
	return prompt


func interact(p: Node) -> void:
	if on_interact.is_valid():
		on_interact.call(p)
