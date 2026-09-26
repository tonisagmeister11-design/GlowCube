class_name CharacterModel
extends Node3D
## Visual humanoid shared by the player and all NPCs.
##
## * Outfit system: body type, skin tone, hair, top (+ overlay such as a vest),
##   bottom, shoes, hat and glasses are separate skinned meshes that are shown or
##   hidden and tinted per instance (no material duplication).
## * AnimationTree built in code:
##       mode (ground / crouch / fall / swim / drive / custom loop)
##         -> upper-body layer (aim, phone, ...) with a spine+arms filter
##         -> one-shot layer (jump, punch, reload, enter vehicle ...)
## * Aim pitch is added to the spine by an AimModifier after animation.
## * Ragdoll bones are created lazily on death.

signal oneshot_finished(anim: String)

const HUMAN_SCENE := "res://assets/generated/characters/human.glb"
const UPPER_BONES := ["Spine", "Chest", "Neck", "Head", "Shoulder.L", "UpperArm.L", "LowerArm.L", "Hand.L",
	"Shoulder.R", "UpperArm.R", "LowerArm.R", "Hand.R"]
const MODES := ["ground", "crouch", "fall", "swim", "drive", "custom"]
const RAGDOLL_BONES := {
	# bone: [radius, length]
	"Hips": [0.14, 0.2], "Spine": [0.13, 0.16], "Chest": [0.15, 0.24], "Head": [0.11, 0.22],
	"UpperArm.L": [0.055, 0.27], "LowerArm.L": [0.045, 0.25], "UpperArm.R": [0.055, 0.27], "LowerArm.R": [0.045, 0.25],
	"UpperLeg.L": [0.08, 0.42], "LowerLeg.L": [0.06, 0.43], "UpperLeg.R": [0.08, 0.42], "LowerLeg.R": [0.06, 0.43],
}

static var _scene: PackedScene

var model: Node3D
var skeleton: Skeleton3D
var anim_player: AnimationPlayer
var tree: AnimationTree
var aim_mod: AimModifier
var meshes := {}           # name -> MeshInstance3D
var outfit := {}
var body_type := "M"
var mode := "ground"
var ragdolled := false
var _upper_anim: AnimationNodeAnimation
var _oneshot_anim: AnimationNodeAnimation
var _custom_anim: AnimationNodeAnimation
var _oneshot_name := ""
var _sim: PhysicalBoneSimulator3D
var _track_prefix := "Skeleton:"
## NPCs never change clothes: hidden outfit meshes are freed after the first apply_outfit
## (fewer nodes and per-instance shader slots, important for the OpenGL renderer).
var prune_hidden := false
var first_person := false
var _fp_shadow := {}


func _ready() -> void:
	if _scene == null:
		_scene = load(HUMAN_SCENE)
	model = _scene.instantiate()
	add_child(model)
	for mi in model.find_children("*", "MeshInstance3D", true, false):
		meshes[String(mi.name)] = mi
		mi.visible = false
		var mn := String(mi.name)
		(mi as MeshInstance3D).visibility_range_end = 220.0
		if mn.begins_with("Eyes") or mn.begins_with("Brows") or mn.begins_with("Glasses"):
			(mi as MeshInstance3D).cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
			(mi as MeshInstance3D).visibility_range_end = 40.0
	var sks := model.find_children("*", "Skeleton3D", true, false)
	skeleton = sks[0] if sks.size() > 0 else null
	var aps := model.find_children("*", "AnimationPlayer", true, false)
	anim_player = aps[0] if aps.size() > 0 else null
	if anim_player and skeleton:
		_detect_track_prefix()
		_build_tree()
		aim_mod = AimModifier.new()
		skeleton.add_child(aim_mod)
	if not outfit.is_empty():
		apply_outfit(outfit)


func _detect_track_prefix() -> void:
	var a := anim_player.get_animation("idle")
	if a and a.get_track_count() > 0:
		var p := String(a.track_get_path(0))
		var colon := p.find(":")
		if colon > 0:
			_track_prefix = p.substr(0, colon + 1)


# ------------------------------------------------------------------ outfit
## outfit keys: body (M/F), skin, hair, hair_color, top, top_color, overlay, overlay_color,
## bottom, bottom_color, shoes, shoes_color, hat, hat_color, glasses
func apply_outfit(o: Dictionary) -> void:
	outfit = o.duplicate()
	if meshes.is_empty():
		return
	body_type = o.get("body", "M")
	for mi in meshes.values():
		mi.visible = false
	var b := body_type
	var skin: Color = o.get("skin", Color(0.86, 0.66, 0.53))
	for zone in _visible_body_zones(o):
		_show("Body_%s_%s" % [b, zone], skin)
	_show("Head_" + b, skin)
	_show("Eyes_" + b, Color.WHITE)
	_show("Brows_" + b, o.get("hair_color", Color(0.15, 0.1, 0.07)))
	var hair: String = o.get("hair", "Short")
	if hair != "" and hair != "None":
		_show("Hair_" + hair, o.get("hair_color", Color(0.15, 0.1, 0.07)))
	_show("Top_%s_%s" % [o.get("top", "TShirt"), b], o.get("top_color", Color.WHITE))
	if o.get("overlay", "") != "":
		_show("Top_%s_%s" % [o["overlay"], b], o.get("overlay_color", Color(0.1, 0.1, 0.1)))
	_show("Bottom_%s_%s" % [o.get("bottom", "Jeans"), b], o.get("bottom_color", Color(0.2, 0.3, 0.5)))
	_show("Shoes_%s_%s" % [o.get("shoes", "Sneakers"), b], o.get("shoes_color", Color(0.9, 0.9, 0.9)))
	if o.get("hat", "") != "":
		_show("Hat_" + o["hat"], o.get("hat_color", Color(0.1, 0.1, 0.1)))
	if o.get("glasses", false):
		_show("Glasses_Sun", Color.WHITE)
	if prune_hidden:
		for k in meshes.keys():
			var mi: MeshInstance3D = meshes[k]
			if not mi.visible:
				mi.queue_free()
				meshes.erase(k)
	if first_person:
		set_first_person(true)


## First-person view: the head, hair, hat and glasses only cast shadows (the camera sits
## inside the head), the rest of the body stays visible.
func set_first_person(on: bool) -> void:
	first_person = on
	for k in meshes:
		var n := String(k)
		if not (n.begins_with("Head") or n.begins_with("Eyes") or n.begins_with("Brows") or n.begins_with("Hair")
				or n.begins_with("Hat") or n.begins_with("Glasses") or n.ends_with("_Neck")):
			continue
		var mi: MeshInstance3D = meshes[k]
		if on:
			if not _fp_shadow.has(n):
				_fp_shadow[n] = mi.cast_shadow
			mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_SHADOWS_ONLY
		elif _fp_shadow.has(n):
			mi.cast_shadow = _fp_shadow[n]
	if not on:
		_fp_shadow.clear()


## Body zones that are not completely covered by the outfit (covered skin is never drawn,
## so it can't poke through the clothes at low LOD levels either).
func _visible_body_zones(o: Dictionary) -> Array:
	var covered := {}
	var top := String(o.get("top", "TShirt"))
	if top != "" and top != "None":
		covered["Torso"] = true
		covered["Hips"] = true
		if top in ["LongSleeve", "Jacket", "Suit"]:
			covered["Arms"] = true
	match String(o.get("bottom", "Jeans")):
		"Jeans":
			for z in ["Hips", "Thighs", "Shins", "Ankles"]:
				covered[z] = true
		"Shorts":
			covered["Hips"] = true
			covered["Thighs"] = true
		"Skirt":
			covered["Hips"] = true
	match String(o.get("shoes", "Sneakers")):
		"Sneakers":
			covered["Feet"] = true
		"Boots":
			covered["Feet"] = true
			covered["Ankles"] = true
	var out := []
	for z in ["Base", "Neck", "Torso", "Hips", "Arms", "Thighs", "Shins", "Ankles", "Feet"]:
		if not covered.has(z):
			out.append(z)
	return out


func _show(mesh_name: String, tint: Color) -> void:
	var mi: MeshInstance3D = meshes.get(mesh_name)
	if mi == null:
		return
	mi.visible = true
	mi.set_instance_shader_parameter("tint", tint)


# ------------------------------------------------------------------ animation tree
func _build_tree() -> void:
	tree = AnimationTree.new()
	tree.name = "AnimTree"
	model.add_child(tree)
	tree.anim_player = tree.get_path_to(anim_player)
	var root := AnimationNodeBlendTree.new()

	var loco := AnimationNodeBlendSpace1D.new()
	loco.min_space = 0.0
	loco.max_space = 8.0
	for p in [["idle", 0.0], ["walk", 1.5], ["run", 4.3], ["sprint", 7.2]]:
		var an := AnimationNodeAnimation.new()
		an.animation = p[0]
		loco.add_blend_point(an, p[1])
	var crouch := AnimationNodeBlendSpace1D.new()
	crouch.max_space = 2.0
	for p in [["crouch_idle", 0.0], ["crouch_walk", 1.4]]:
		var an := AnimationNodeAnimation.new()
		an.animation = p[0]
		crouch.add_blend_point(an, p[1])
	var swim := AnimationNodeBlendSpace1D.new()
	swim.max_space = 1.0
	for p in [["swim_idle", 0.0], ["swim", 1.0]]:
		var an := AnimationNodeAnimation.new()
		an.animation = p[0]
		swim.add_blend_point(an, p[1])
	var fall := AnimationNodeAnimation.new()
	fall.animation = "fall"
	var drive := AnimationNodeAnimation.new()
	drive.animation = "drive"
	_custom_anim = AnimationNodeAnimation.new()
	_custom_anim.animation = "idle"

	var trans := AnimationNodeTransition.new()
	trans.xfade_time = 0.2
	for m in MODES:
		trans.add_input(m)
	root.add_node("loco", loco)
	root.add_node("crouch", crouch)
	root.add_node("fall", fall)
	root.add_node("swim", swim)
	root.add_node("drive", drive)
	root.add_node("custom", _custom_anim)
	root.add_node("mode", trans)
	var ts := AnimationNodeTimeScale.new()
	root.add_node("TimeScale", ts)
	root.connect_node("TimeScale", 0, "loco")
	for i in MODES.size():
		root.connect_node("mode", i, ["TimeScale", "crouch", "fall", "swim", "drive", "custom"][i])

	_upper_anim = AnimationNodeAnimation.new()
	_upper_anim.animation = "aim_pistol"
	var ublend := AnimationNodeBlend2.new()
	ublend.filter_enabled = true
	for bn in UPPER_BONES:
		ublend.set_filter_path(NodePath(_track_prefix + bn), true)
	root.add_node("upper", _upper_anim)
	root.add_node("ublend", ublend)
	root.connect_node("ublend", 0, "mode")
	root.connect_node("ublend", 1, "upper")

	_oneshot_anim = AnimationNodeAnimation.new()
	_oneshot_anim.animation = "jump"
	var os := AnimationNodeOneShot.new()
	os.fadein_time = 0.08
	os.fadeout_time = 0.18
	root.add_node("osanim", _oneshot_anim)
	root.add_node("oneshot", os)
	root.connect_node("oneshot", 0, "ublend")
	root.connect_node("oneshot", 1, "osanim")
	root.connect_node("output", 0, "oneshot")
	tree.tree_root = root
	tree.active = true
	# the transition has no active input until the first request
	tree.set("parameters/mode/transition_request", mode if mode in MODES else "ground")
	tree.animation_finished.connect(_on_anim_finished)


func set_locomotion(speed: float) -> void:
	if tree == null:
		return
	if mode == "crouch":
		tree.set("parameters/crouch/blend_position", clampf(speed, 0.0, 1.6))
	elif mode == "swim":
		tree.set("parameters/swim/blend_position", clampf(speed / 1.5, 0.0, 1.0))
	else:
		tree.set("parameters/loco/blend_position", clampf(speed, 0.0, 8.0))
	var ts := 1.0
	if mode == "ground" and speed > 0.2:
		# keep feet from sliding: scale playback relative to the blend point speeds
		ts = clampf(speed / _nearest_point_speed(speed), 0.75, 1.3)
	tree.set("parameters/TimeScale/scale", ts)


func _nearest_point_speed(s: float) -> float:
	if s < 2.9:
		return 1.5 if s > 0.75 else maxf(s, 0.1) / 0.5 * 1.5
	if s < 5.8:
		return 4.3
	return 7.2


func set_mode(m: String) -> void:
	if tree == null or m == mode:
		mode = m
		return
	mode = m
	tree.set("parameters/mode/transition_request", m)


func play_loop(anim: String) -> void:
	if tree == null:
		return
	_custom_anim.animation = anim
	mode = ""
	set_mode("custom")


func set_upper(anim: String, weight: float) -> void:
	if tree == null:
		return
	if anim != "" and _upper_anim.animation != StringName(anim):
		_upper_anim.animation = anim
	tree.set("parameters/ublend/blend_amount", clampf(weight, 0.0, 1.0) if anim != "" else 0.0)


func play_oneshot(anim: String, fade_in := 0.08) -> void:
	if tree == null or not anim_player.has_animation(anim):
		return
	_oneshot_anim.animation = anim
	_oneshot_name = anim
	tree.set("parameters/oneshot/request", AnimationNodeOneShot.ONE_SHOT_REQUEST_FIRE)


func is_oneshot_active() -> bool:
	return tree != null and bool(tree.get("parameters/oneshot/active"))


func _on_anim_finished(anim: StringName) -> void:
	if String(anim) == _oneshot_name:
		oneshot_finished.emit(_oneshot_name)


func set_aim_pitch(p: float) -> void:
	if aim_mod:
		aim_mod.pitch = p


func anim_length(anim: String) -> float:
	if anim_player and anim_player.has_animation(anim):
		return anim_player.get_animation(anim).length
	return 0.5


# ------------------------------------------------------------------ attachments
func attach(node: Node3D, bone: String, offset := Transform3D.IDENTITY) -> BoneAttachment3D:
	if skeleton == null:
		return null
	var ba := BoneAttachment3D.new()
	ba.bone_name = bone
	skeleton.add_child(ba)
	ba.add_child(node)
	node.transform = offset
	return ba


func bone_global_position(bone: String) -> Vector3:
	if skeleton == null:
		return global_position
	var i := skeleton.find_bone(bone)
	if i < 0:
		return global_position
	return skeleton.global_transform * skeleton.get_bone_global_pose(i).origin


# ------------------------------------------------------------------ ragdoll
func start_ragdoll(impulse := Vector3.ZERO, at_bone := "Chest") -> void:
	if ragdolled or skeleton == null:
		return
	ragdolled = true
	if tree:
		tree.active = false
	_sim = PhysicalBoneSimulator3D.new()
	skeleton.add_child(_sim)
	var bodies := {}
	for bn in RAGDOLL_BONES:
		var idx := skeleton.find_bone(bn)
		if idx < 0:
			continue
		var pb := PhysicalBone3D.new()
		pb.name = "PB_" + bn.replace(".", "_")
		pb.bone_name = bn
		var cs := CollisionShape3D.new()
		var cap := CapsuleShape3D.new()
		var spec: Array = RAGDOLL_BONES[bn]
		cap.radius = spec[0]
		cap.height = maxf(spec[1], spec[0] * 2.05)
		cs.shape = cap
		cs.position = Vector3(0, spec[1] * 0.5, 0)
		pb.add_child(cs)
		pb.mass = 4.0 if bn in ["Hips", "Chest"] else 2.0
		pb.linear_damp = 0.3
		pb.angular_damp = 2.5
		pb.collision_layer = 1 << 5
		pb.collision_mask = 1 | (1 << 2)
		pb.joint_type = PhysicalBone3D.JOINT_TYPE_NONE if bn == "Hips" else PhysicalBone3D.JOINT_TYPE_CONE
		_sim.add_child(pb)
		bodies[bn] = pb
	_sim.physical_bones_start_simulation()
	await get_tree().physics_frame
	var target: PhysicalBone3D = bodies.get(at_bone, bodies.get("Chest"))
	for b in bodies.values():
		(b as PhysicalBone3D).apply_central_impulse(impulse * 0.35)
	if target:
		target.apply_central_impulse(impulse)


func stop_ragdoll() -> void:
	if _sim:
		_sim.physical_bones_stop_simulation()
		_sim.queue_free()
		_sim = null
	ragdolled = false
	if tree:
		tree.active = true


func ragdoll_center() -> Vector3:
	if _sim == null:
		return global_position
	for c in _sim.get_children():
		if c is PhysicalBone3D and (c as PhysicalBone3D).bone_name == "Hips":
			return (c as PhysicalBone3D).global_position
	return global_position
