class_name DetailedPerson
extends Node3D
## Ein zugeliefertes Personenmodell als Spielfigur.
##
## Bietet dieselben Methoden wie die prozedurale Figur (`Humanoid`), damit
## NpcAgent und die Geschichte nichts über die Herkunft wissen müssen:
## `set_motion`, `set_action`, `set_lod`, `height`.

var model_id := ""
var data: Dictionary = {}
var animator: BodyAnimator = null
var counts_against_budget := true

var _height := 1.80
var _action := "idle"
var _meshes: Array[GeometryInstance3D] = []


func setup(id: String, d: Dictionary) -> bool:
	model_id = id
	data = d
	var packed := load(str(d.scene)) as PackedScene
	if packed == null:
		return false
	var person: Node3D = packed.instantiate()
	person.name = "Model"
	add_child(person)
	_height = float(person.get_meta("height", d.get("height", 1.80)))
	_collect(person)
	if bool(d.get("animated", false)):
		animator = BodyAnimator.new()
		add_child(animator)
		if animator.setup(person):
			animator.apply_rest_pose()
		else:
			animator.queue_free()
			animator = null
	return true


func _collect(n: Node) -> void:
	if n is GeometryInstance3D:
		_meshes.append(n)
	for c in n.get_children():
		_collect(c)


## Das Budget wird freigegeben, sobald die Figur verschwindet - egal ob sie
## nur aus dem Baum genommen oder ganz geloescht wird. `_released` sorgt
## dafuer, dass das genau einmal passiert.
var _released := false


func _release_budget() -> void:
	if _released or not counts_against_budget:
		return
	_released = true
	PersonModels.live = maxi(0, PersonModels.live - 1)


func _exit_tree() -> void:
	_release_budget()


func _notification(what: int) -> void:
	if what == NOTIFICATION_PREDELETE:
		_release_budget()


# ============================================================ Figuren-API

func set_motion(speed_mps: float, _grounded := true) -> void:
	if animator != null:
		animator.speed = speed_mps


func set_action(action_name: String) -> void:
	_action = action_name
	if animator != null:
		animator.seated = action_name in ["sit", "sitzen", "bench", "drive"]


func get_action() -> String:
	return _action


func height() -> float:
	return _height


func eye_height() -> float:
	return _height * 0.94


func seat_height() -> float:
	return _height * 0.27


func get_head_position() -> Vector3:
	return global_position + Vector3(0.0, _height * 0.94, 0.0)


## Entfernungsstufe. Weit weg wird der Bewegungsablauf nicht mehr gerechnet -
## das ist der teuerste Teil, weil jedes Bild das Skelett neu verformt wird.
func set_lod(level: int) -> void:
	if animator != null:
		animator.paused = level >= 2
	var vis := level < 2
	for m in _meshes:
		m.visible = vis or m.name == "CC_Base_Body"


func set_shadow_only(on: bool) -> void:
	for m in _meshes:
		m.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_SHADOWS_ONLY if on else GeometryInstance3D.SHADOW_CASTING_SETTING_ON
