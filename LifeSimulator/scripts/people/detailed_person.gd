class_name DetailedPerson
extends Node3D
## Ein zugeliefertes Personenmodell als Spielfigur.
##
## Bietet dieselben Methoden wie die prozedurale Figur (`Humanoid`), damit
## NpcAgent und die Geschichte nichts über die Herkunft wissen müssen:
## `set_motion`, `set_action`, `set_lod`, `height`.

## Wird im Gehtakt ausgeloest, wenn ein Fuss aufsetzt. Die Spielerfigur haengt
## daran ihre Schrittgeraeusche.
signal footstep(side: int, intensity: float)

var model_id := ""
var data: Dictionary = {}
var variant := -1
var animator: BodyAnimator = null
var counts_against_budget := true
var person_name := ""

var _height := 1.80
var _action := "idle"
var _meshes: Array[GeometryInstance3D] = []


func setup(id: String, d: Dictionary, v: Dictionary = {}) -> bool:
	model_id = id
	data = d
	person_name = str(v.get("name", d.get("name", "")))
	var packed := load(str(d.scene)) as PackedScene
	if packed == null:
		return false
	var person: Node3D = packed.instantiate()
	person.name = "Model"
	add_child(person)
	_height = float(person.get_meta("height", d.get("height", 1.80)))
	_collect(person)
	if not v.is_empty():
		_apply_variant(person, v)
	if bool(d.get("animated", false)):
		animator = BodyAnimator.new()
		add_child(animator)
		if animator.setup(person):
			animator.apply_rest_pose()
			animator.footstep.connect(func(side: int, strength: float): footstep.emit(side, strength))
		else:
			animator.queue_free()
			animator = null
	return true


## Hautton, Kleidungsfarben, Statur und Groesse einer Spielart anwenden.
##
## Eingefaerbt wird ueber die Albedo-Farbe, die mit der Textur multipliziert
## wird. Die Materialien der Szene werden dafuer kopiert - sonst traefe die
## Aenderung alle Figuren gleichzeitig, weil sie sich die Materialien teilen.
func _apply_variant(person: Node3D, v: Dictionary) -> void:
	var skin: Color = v.get("skin", Color.WHITE)
	var hair: Color = v.get("hair", Color.WHITE)
	var cloth: Dictionary = v.get("cloth", {})
	for g in _meshes:
		var mi := g as MeshInstance3D
		if mi == null or mi.mesh == null:
			continue
		var mesh_tint: Color = cloth.get(String(mi.name), Color.WHITE)
		for si in mi.mesh.get_surface_count():
			var base: Material = mi.get_surface_override_material(si)
			if base == null:
				base = mi.mesh.surface_get_material(si)
			if not (base is BaseMaterial3D):
				continue
			var mat := (base as BaseMaterial3D).duplicate() as BaseMaterial3D
			var low := mat.resource_name.to_lower()
			var tint := mesh_tint
			for key in PersonModels.SKIN_MATERIALS:
				if low.contains(key):
					tint = skin
					break
			for key in PersonModels.HAIR_MATERIALS:
				if low.contains(key):
					tint = hair
					break
			mat.albedo_color = Color(
				mat.albedo_color.r * tint.r, mat.albedo_color.g * tint.g,
				mat.albedo_color.b * tint.b, mat.albedo_color.a)
			mi.set_surface_override_material(si, mat)
	var bulk: float = float(v.get("bulk", 1.0))
	var size: float = float(v.get("size", 1.0))
	if not is_equal_approx(bulk, 1.0) or not is_equal_approx(size, 1.0):
		person.scale = person.scale * Vector3(bulk * size, size, bulk * size)
		_height *= size


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
	if _released:
		return
	_released = true
	PersonModels.release_variant(model_id, variant)
	if counts_against_budget:
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


## Entfernungsstufe. Weit weg wird nur der Bewegungsablauf angehalten - das
## ist der teuerste Teil, weil dafuer jedes Bild das Skelett neu verformt wird.
##
## Es wird nichts ausgeblendet. Eine fruehere Fassung hat ab Stufe 2 alles
## ausser dem Koerper versteckt, um Dreiecke zu sparen; die Figur stand dann
## ohne Kleidung, Haare und Augen in der Stadt. Dreiecke sind hier nicht das
## Problem - die Figuren erscheinen ohnehin nur in Spielernaehe und ihre Zahl
## begrenzt PersonModels.MAX_BY_LEVEL.
func set_lod(level: int) -> void:
	if animator != null:
		animator.paused = level >= 2


func set_shadow_only(on: bool) -> void:
	for m in _meshes:
		m.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_SHADOWS_ONLY if on else GeometryInstance3D.SHADOW_CASTING_SETTING_ON
