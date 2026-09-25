class_name AimModifier
extends SkeletonModifier3D
## Adds aim pitch (and optional yaw) on top of the animated spine so the upper
## body follows the camera while aiming. Runs after the AnimationTree.

var pitch := 0.0
var yaw := 0.0
var _bones := PackedInt32Array()
var _axes: Array[Vector3] = []
var _up_axes: Array[Vector3] = []


func _ready() -> void:
	var sk := get_skeleton()
	if sk == null:
		return
	for bn in ["Spine", "Chest", "Neck"]:
		var i := sk.find_bone(bn)
		if i < 0:
			continue
		_bones.append(i)
		var rest := sk.get_bone_global_rest(i).basis
		_axes.append((rest.inverse() * Vector3.RIGHT).normalized())
		_up_axes.append((rest.inverse() * Vector3.UP).normalized())


func _process_modification() -> void:
	var sk := get_skeleton()
	if sk == null or (absf(pitch) < 0.001 and absf(yaw) < 0.001):
		return
	var share := [0.35, 0.4, 0.25]
	for k in _bones.size():
		var i := _bones[k]
		var q := sk.get_bone_pose_rotation(i)
		var add := Quaternion(_axes[k], pitch * share[k]) * Quaternion(_up_axes[k], yaw * share[k])
		sk.set_bone_pose_rotation(i, q * add)
