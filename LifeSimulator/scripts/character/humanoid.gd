class_name Humanoid
extends Node3D
## Prozedurale Figur (Mensch jeden Alters) mit Skelett, gehautetem Koerper
## und prozeduraler Animation. Geometrie entsteht in `HumanoidBuilder`.
##
## Konventionen:
## * Ursprung = Boden zwischen den Fuessen, Blick nach +Z (entspricht der
##   Yaw-Konvention der Stadtdaten: rotation.y = rot).
## * "sit": Becken senkrecht ueber dem Ursprung auf Hoehe `seat_height()`,
##   Oberschenkel nach vorne, Fuesse am Boden.
## * "sleep": Rueckenlage, Becken ueber dem Ursprung, Kopf Richtung -Z,
##   Liegeflaeche bei y = 0 (Figur auf Bettoberflaeche setzen).
## * Babys unter einem Jahr krabbeln statt zu gehen und sitzen im Stand.
##
## Nutzung: `setup(appearance, age, body)`, dann pro Frame
## `set_motion(geschwindigkeit_m_s, am_boden)` und `set_action(name)`.

signal footstep(side: int, intensity: float)

const ACTIONS := ["idle", "walk", "run", "sit", "sleep", "work", "talk", "wave"]
const LOD_RATES := [0.0, 1.0 / 30.0, 1.0 / 12.0]

var appearance: Dictionary = {}
var age_years := 30.0
var body := "female"
## Detailstufe: 0 voll, 1 vereinfacht, 2 Stellvertreter (ohne Skelett).
var lod_level := 0
## Sekunden zwischen Posenberechnungen (0 = jedes Frame).
var anim_interval := 0.0
## Rotiert die Figur nicht selbst; nur Darstellung.
var proportions: BodyProportions

var _data: Dictionary = {}
var _skel: Skeleton3D
var _mesh: MeshInstance3D
var _speed := 0.0
var _sp := 0.0
var _grounded := true
var _action := "idle"
var _mode := "loco"
var _phase := 0.0
var _t := 0.0
var _acc := 0.0
var _blink_t := 2.0
var _blink := 0.0
var _look := Vector2.ZERO
var _look_target := Vector2.ZERO
var _look_t := 1.0
var _rng := RandomNumberGenerator.new()
var _q: Array = []          ## aktuelle lokale Rotationen
var _hips_off := Vector3.ZERO
var _root_xf := Transform3D()
var _snap_q: Array = []
var _snap_hips := Vector3.ZERO
var _snap_root := Transform3D()
var _blend := 1.0
var _stance := [true, true]
var _rest_pos: Array = []
var _jaw := 0.0


func _init() -> void:
	_rng.randomize()
	_t = _rng.randf() * 20.0
	_phase = _rng.randf()


## Baut die Figur neu auf. appearance siehe Appearance (wird validiert).
func setup(p_appearance: Dictionary, p_age_years: float, p_body: String) -> void:
	appearance = Appearance.validate(p_appearance, p_age_years)
	age_years = clampf(p_age_years, 0.0, 110.0)
	body = "male" if p_body == "male" else "female"
	_rebuild()


## Wechselt die Detailstufe (baut neu, Animation laeuft weiter).
func set_lod(level: int) -> void:
	level = clampi(level, 0, 2)
	if level == lod_level and _data.size() > 0:
		return
	lod_level = level
	anim_interval = LOD_RATES[level]
	if not appearance.is_empty():
		_rebuild()


## Guenstige Variante fuer Menschenmengen (0 voll, 1 vereinfacht, 2 Stellvertreter).
static func create_lod(p_appearance: Dictionary, p_age: float, p_body: String, level: int) -> Node3D:
	var h := Humanoid.new()
	h.lod_level = clampi(level, 0, 2)
	h.anim_interval = LOD_RATES[h.lod_level]
	h.setup(p_appearance, p_age, p_body)
	return h


## Altersabhaengiger Faktor fuer Gehtempo (1.0 = junger Erwachsener).
static func age_speed_factor(age: float) -> float:
	if age < 1.0:
		return 0.22
	return BodyProportions.curve(age, [[1.0, 0.35], [2.0, 0.5], [4.0, 0.66], [7.0, 0.8], [11.0, 0.92],
		[15.0, 1.0], [40.0, 1.0], [55.0, 0.95], [65.0, 0.88], [75.0, 0.76], [85.0, 0.62], [95.0, 0.5]])


## Faktor fuer Rennen relativ zum Erwachsenen (Senioren laufen kaum).
static func age_run_factor(age: float) -> float:
	if age < 1.0:
		return 0.0
	return BodyProportions.curve(age, [[1.0, 0.25], [3.0, 0.45], [6.0, 0.65], [10.0, 0.82], [14.0, 0.95],
		[18.0, 1.0], [35.0, 1.0], [50.0, 0.85], [60.0, 0.68], [70.0, 0.45], [80.0, 0.28], [90.0, 0.15]])


func set_motion(speed_mps: float, grounded := true) -> void:
	_speed = maxf(speed_mps, 0.0)
	_grounded = grounded


func set_action(action_name: String) -> void:
	if action_name in ACTIONS:
		_action = action_name


func get_action() -> String:
	return _action


## Standhoehe inklusive Schuhsohle und altersbedingter Haltung.
func height() -> float:
	if _data.is_empty():
		return 1.7
	return float(_data.height) * (1.0 - 0.035 * proportions.stoop)


func seat_height() -> float:
	if proportions == null:
		return 0.45
	return proportions.knee_y + float(_data.get("sole", 0.0))


## Augenhoehe ueber dem Boden (Ruhepose), z. B. fuer die Ego-Kamera.
func eye_height() -> float:
	if proportions == null:
		return 1.6
	return proportions.chin_y + (0.5 + proportions.eye_y) * proportions.head_h + float(_data.get("sole", 0.0))


func get_skeleton() -> Skeleton3D:
	return _skel


func get_mesh_instance() -> MeshInstance3D:
	return _mesh


## Globale Position des Kopfes (Augenmitte); faellt ohne Skelett auf eye_height zurueck.
func get_head_position() -> Vector3:
	if _skel:
		var t := _skel.global_transform * _skel.get_bone_global_pose(HumanoidBuilder.B_LIDS)
		return t.origin
	return global_transform * Vector3(0, eye_height(), 0.05)


## Sichtbarkeit fuer Schatten-only (Ego-Perspektive).
func set_shadow_only(on: bool) -> void:
	if _mesh:
		_mesh.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_SHADOWS_ONLY if on else GeometryInstance3D.SHADOW_CASTING_SETTING_ON


func get_proportions() -> BodyProportions:
	return proportions


func _rebuild() -> void:
	_data = HumanoidBuilder.build(appearance, age_years, body, lod_level)
	proportions = _data.proportions
	for c in get_children():
		if c == _skel or c == _mesh:
			remove_child(c)
			c.queue_free()
	_skel = null
	_mesh = null
	var h: float = _data.height
	var aabb := AABB(Vector3(-h * 0.7, -0.1, -h * 1.2), Vector3(h * 1.4, h * 1.3, h * 2.4))
	if lod_level == 2:
		_mesh = MeshInstance3D.new()
		_mesh.name = "Body"
		_mesh.mesh = _data.mesh
		_mesh.custom_aabb = aabb
		add_child(_mesh)
		return
	_skel = Skeleton3D.new()
	_skel.name = "Skeleton"
	var rest: Array = _data.rest
	_rest_pos.clear()
	for i in HumanoidBuilder.BONE_COUNT:
		_skel.add_bone(HumanoidBuilder.BONE_NAMES[i])
	for i in HumanoidBuilder.BONE_COUNT:
		var par: int = HumanoidBuilder.BONE_PARENTS[i]
		if par >= 0:
			_skel.set_bone_parent(i, par)
		_skel.set_bone_rest(i, rest[i])
		_rest_pos.append((rest[i] as Transform3D).origin)
	_skel.reset_bone_poses()
	add_child(_skel)
	_mesh = MeshInstance3D.new()
	_mesh.name = "Body"
	_mesh.mesh = _data.mesh
	_mesh.skin = _data.skin
	_mesh.custom_aabb = aabb
	_skel.add_child(_mesh)
	_mesh.skeleton = NodePath("..")
	_q.clear()
	for i in HumanoidBuilder.BONE_COUNT:
		_q.append(Quaternion.IDENTITY)
	_snap_q = _q.duplicate()
	_pose(0.0)
	_apply()


func _process(delta: float) -> void:
	if _data.is_empty():
		return
	_acc += delta
	if anim_interval > 0.0 and _acc < anim_interval:
		return
	var dt := minf(_acc, 0.25)
	_acc = 0.0
	if lod_level == 2:
		_animate_proxy(dt)
		return
	_pose(dt)
	_apply()


# ================================================================== Pose

static func _qx(a: float) -> Quaternion:
	return Quaternion(Vector3.RIGHT, a)


static func _qy(a: float) -> Quaternion:
	return Quaternion(Vector3.UP, a)


static func _qz(a: float) -> Quaternion:
	return Quaternion(Vector3.BACK, a)


static func _e(x: float, y := 0.0, z := 0.0) -> Quaternion:
	return _qy(y) * _qx(x) * _qz(z)


func _mode_for() -> String:
	if not _grounded and age_years >= 1.0 and _action in ["idle", "walk", "run", "talk", "wave"]:
		return "air"
	match _action:
		"sit":
			return "sit" if age_years >= 1.5 else "floor"
		"sleep":
			return "sleep"
		"work":
			return "work" if age_years >= 1.5 else "floor"
	if age_years < 1.0:
		return "crawl" if _sp > 0.05 else "floor"
	return "loco"


func _pose(dt: float) -> void:
	_t += dt
	_sp = lerpf(_sp, _speed, 1.0 - exp(-8.0 * dt))
	var mode := _mode_for()
	if mode != _mode:
		_snap_q = _q.duplicate()
		_snap_hips = _hips_off
		_snap_root = _root_xf
		_blend = 0.0
		_mode = mode
	_blend = minf(1.0, _blend + dt / 0.35)
	var q: Array = []
	for i in HumanoidBuilder.BONE_COUNT:
		q.append(Quaternion.IDENTITY)
	var hips := Vector3.ZERO
	var root := Transform3D()
	match mode:
		"loco":
			hips = _pose_loco(q, dt)
		"air":
			hips = _pose_air(q)
		"sit":
			hips = _pose_sit(q)
		"floor":
			hips = _pose_floor(q)
		"crawl":
			hips = _pose_crawl(q, dt)
		"work":
			hips = _pose_loco(q, dt)
			_pose_work(q)
		"sleep":
			root = _pose_sleep(q)
	_pose_face(q, dt, mode)
	if _blend < 1.0:
		var b := _blend * _blend * (3.0 - 2.0 * _blend)
		for i in q.size():
			q[i] = (_snap_q[i] as Quaternion).slerp(q[i], b)
		hips = _snap_hips.lerp(hips, b)
		root = _snap_root.interpolate_with(root, b)
	_q = q
	_hips_off = hips
	_root_xf = root


func _apply() -> void:
	if _skel == null:
		return
	for i in _q.size():
		_skel.set_bone_pose_rotation(i, _q[i])
	_skel.set_bone_pose_position(HumanoidBuilder.B_HIPS, _rest_pos[HumanoidBuilder.B_HIPS] + _hips_off)
	_skel.set_bone_pose_position(HumanoidBuilder.B_ROOT, _root_xf.origin)
	_skel.set_bone_pose_rotation(HumanoidBuilder.B_ROOT, _root_xf.basis.get_rotation_quaternion())


## Zwei-Knochen-IK in der Sagittalebene. dy > 0: Knoechel unter der Huefte,
## dz: Knoechel vor der Huefte. Rueckgabe [Oberschenkel-, Knie-Winkel] (X-Rotation).
static func _leg_ik(a: float, b: float, dy: float, dz: float) -> Vector2:
	var d := clampf(sqrt(dy * dy + dz * dz), (a + b) * 0.3, (a + b) * 0.9995)
	var cos_k := clampf((a * a + b * b - d * d) / (2.0 * a * b), -1.0, 1.0)
	var flex := PI - acos(cos_k)
	var alpha := atan2(dz, dy)
	var cos_a := clampf((a * a + d * d - b * b) / (2.0 * a * d), -1.0, 1.0)
	var thigh_fwd := alpha + acos(cos_a)
	return Vector2(-thigh_fwd, flex)


func _pose_loco(q: Array, dt: float) -> Vector3:
	var p := proportions
	var a := p.thigh_len
	var b := p.shin_len
	var L := p.leg_length()
	var v_ref := sqrt(L / 0.87)
	var sp := _sp
	var move_w := _sstep(0.05, 0.45, sp)
	var run_w := _sstep(2.0 * v_ref, 2.9 * v_ref, sp)
	var toddler := 1.0 - _sstep(1.5, 4.5, age_years)
	var stoop := p.stoop
	var frail := p.frailty
	var lfac := pow(0.87 / L, 0.35)
	var f_walk := clampf((0.8 + 0.7 * sp / v_ref) * lfac, 0.9, 3.4)
	var f_run := clampf((1.9 + 0.25 * sp / v_ref) * lfac, 2.4, 4.2)
	var f := lerpf(f_walk, f_run, run_w) * (1.0 + 0.12 * toddler) * (1.0 - 0.12 * frail)
	if sp < 0.05:
		f = 0.0
	_phase = fmod(_phase + dt * f * 0.5, 1.0)
	var beta := lerpf(0.6, 0.38, run_w)
	var D := 0.0 if f <= 0.0 else sp * beta / (f * 0.5)
	D = minf(D, L * 1.25)
	var lift_h := L * lerpf(0.075, 0.2, run_w) * (1.0 - 0.5 * frail) * (1.0 + 0.4 * toddler) * move_w
	var knee_soft := lerpf(0.985, 0.94, run_w) * (1.0 - 0.035 * stoop - 0.02 * toddler)
	var leff := (a + b) * knee_soft
	var idle_w := 1.0 - move_w
	# Becken
	var fz := [0.0, 0.0]
	var lift := [0.0, 0.0]
	var pitch := [0.0, 0.0]
	var drop := 0.0
	for side in 2:
		var ph := fmod(_phase + 0.5 * side, 1.0)
		var st := ph < beta
		if st:
			var s := ph / beta
			fz[side] = D * (0.5 - s)
			if s > 0.72:
				pitch[side] = (s - 0.72) / 0.28 * 0.55 * move_w
			var h := sqrt(maxf(leff * leff - fz[side] * fz[side], 0.0))
			drop = maxf(drop, leff - h)
		else:
			var s2 := (ph - beta) / (1.0 - beta)
			fz[side] = D * (-0.5 + s2 * s2 * (3.0 - 2.0 * s2))
			lift[side] = lift_h * pow(sin(PI * s2), 0.8)
			pitch[side] = lerpf(0.45, -0.12, s2) * move_w
		if st and not _stance[side] and sp > 0.3:
			footstep.emit(side, clampf(sp / 4.0, 0.2, 1.0))
		_stance[side] = st
	var cyc := TAU * _phase
	var breathe := sin(_t * TAU * 0.27)
	var shift := sin(_t * 0.41) * 0.6 + sin(_t * 0.17) * 0.4
	var hips := Vector3.ZERO
	hips.y = -drop * (1.0 - run_w) - (a + b) * (1.0 - knee_soft) * 0.6
	hips.y += run_w * (0.03 * L * cos(2.0 * cyc) - 0.03 * L)
	var sway := L * (0.012 + 0.03 * toddler) * move_w
	hips.x = sway * sin(cyc) + idle_w * L * 0.014 * shift
	hips.z = -0.02 * stoop * L
	var pelvis_yaw := -0.11 * clampf(D / L, 0.0, 1.0) * cos(cyc) * (1.0 - 0.4 * run_w)
	var pelvis_roll := -0.04 * move_w * sin(cyc) * (1.0 + p.fem * p.dimorph * 0.8)
	var lean := 0.12 * run_w + 0.04 * move_w + 0.2 * stoop - 0.06 * toddler
	q[HumanoidBuilder.B_HIPS] = _e(lean * 0.3, pelvis_yaw, pelvis_roll)
	q[HumanoidBuilder.B_SPINE] = _e(lean * 0.3 + 0.12 * stoop - 0.004 * breathe, -pelvis_yaw * 0.5, -pelvis_roll * 0.5)
	q[HumanoidBuilder.B_CHEST] = _e(lean * 0.25 + 0.3 * stoop - 0.012 * breathe * (1.0 + idle_w), -pelvis_yaw * 0.7, -pelvis_roll * 0.3)
	var total_lean := lean * 0.85 + 0.42 * stoop
	q[HumanoidBuilder.B_NECK] = _e(-total_lean * 0.45 + 0.05, -pelvis_yaw * 0.2)
	q[HumanoidBuilder.B_HEAD] = _e(-total_lean * 0.45 + _look.y, _look.x)
	# Beine
	var stance_w := L * 0.06 * toddler + 0.01 * L * p.weight
	for side in 2:
		var sgn := 1.0 if side == 0 else -1.0
		var dy: float = (p.hip_y + hips.y) - (p.ankle_y + float(lift[side]))
		var dz: float = float(fz[side]) - hips.z
		var ik := _leg_ik(a, b, dy, dz)
		var thigh_x := ik.x - lean * 0.3
		var shin_x := ik.y
		var abd := sgn * (0.03 + 0.08 * toddler) - (hips.x / L) * 0.9 - pelvis_roll
		abd += sgn * stance_w / L
		var thigh_b := HumanoidBuilder.B_THIGH_L if side == 0 else HumanoidBuilder.B_THIGH_R
		q[thigh_b] = _e(thigh_x, -pelvis_yaw * 0.7, abd)
		q[thigh_b + 1] = _qx(shin_x)
		var foot_world := thigh_x + lean * 0.3 + shin_x
		q[thigh_b + 2] = _e(float(pitch[side]) - foot_world, 0.0, -abd * 0.9)
	# Arme
	var amp := (0.22 + 0.25 * clampf(sp / v_ref, 0.0, 1.5)) * (1.0 - 0.6 * stoop) * move_w
	amp = lerpf(amp, 0.65, run_w)
	var arm_out := 0.08 + p.belly / p.height * 1.2 + (p.hip_w - p.shoulder_w) / p.height * 1.3 + 0.35 * toddler
	arm_out = maxf(arm_out, 0.06)
	for side in 2:
		var sgn := 1.0 if side == 0 else -1.0
		var ph := TAU * fmod(_phase + 0.5 * side, 1.0)
		var fwd := amp * -cos(ph) * (1.0 if side == 0 else 1.0)
		fwd = -fwd
		var elbow := lerpf(0.18 + 0.15 * maxf(-fwd, 0.0) / maxf(amp, 0.01), 1.45, run_w) + 0.5 * toddler + 0.12 * stoop
		var ua := HumanoidBuilder.B_UPPERARM_L if side == 0 else HumanoidBuilder.B_UPPERARM_R
		var idle_sway := idle_w * 0.03 * sin(_t * 0.7 + side)
		q[ua] = _e(fwd - 0.12 * toddler + idle_sway + 0.1 * stoop, 0.0, sgn * arm_out)
		q[ua + 1] = _qx(-elbow)
		q[ua + 2] = _e(0.0, 0.0, sgn * -0.05)
		q[ua - 1] = _e(0.0, 0.0, sgn * (0.02 * breathe * idle_w - 0.05 * run_w))
	# Gesten
	if _action == "talk":
		_overlay_talk(q)
	elif _action == "wave":
		_overlay_wave(q)
	return hips


func _overlay_talk(q: Array) -> void:
	var g := 0.5 + 0.5 * sin(_t * 1.7) * sin(_t * 0.63 + 1.0)
	var g2 := 0.5 + 0.5 * sin(_t * 2.3 + 2.0)
	q[HumanoidBuilder.B_UPPERARM_R] = _e(-0.25 - 0.25 * g, 0.0, -0.18)
	q[HumanoidBuilder.B_FOREARM_R] = _e(-1.0 - 0.4 * g2, 0.3 * g)
	q[HumanoidBuilder.B_UPPERARM_L] = _e(-0.1 - 0.2 * g2 * g, 0.0, 0.15)
	q[HumanoidBuilder.B_FOREARM_L] = _qx(-0.5 - 0.6 * g)
	q[HumanoidBuilder.B_HEAD] = (q[HumanoidBuilder.B_HEAD] as Quaternion) * _e(0.06 * sin(_t * 3.1), 0.1 * sin(_t * 0.9))
	_jaw = 0.11 * absf(sin(_t * 9.0) * sin(_t * 3.3))


func _overlay_wave(q: Array) -> void:
	q[HumanoidBuilder.B_UPPERARM_R] = _e(-0.25, 0.0, -2.45)
	q[HumanoidBuilder.B_FOREARM_R] = _e(0.0, 0.0, -0.35 + 0.4 * sin(_t * 8.0))
	q[HumanoidBuilder.B_HAND_R] = _qz(0.15 * sin(_t * 8.0 + 0.6))
	q[HumanoidBuilder.B_HEAD] = (q[HumanoidBuilder.B_HEAD] as Quaternion) * _qz(-0.08)


func _pose_work(q: Array) -> void:
	var w1 := sin(_t * 5.0)
	var w2 := sin(_t * 4.3 + 1.3)
	q[HumanoidBuilder.B_SPINE] = _qx(0.06)
	q[HumanoidBuilder.B_CHEST] = _qx(0.08)
	q[HumanoidBuilder.B_NECK] = _qx(0.15)
	q[HumanoidBuilder.B_HEAD] = _e(0.2, 0.1 * sin(_t * 0.5))
	q[HumanoidBuilder.B_UPPERARM_L] = _e(-0.5 + 0.06 * w1, 0.0, 0.12)
	q[HumanoidBuilder.B_FOREARM_L] = _e(-1.15 + 0.12 * w2, -0.25)
	q[HumanoidBuilder.B_UPPERARM_R] = _e(-0.5 + 0.06 * w2, 0.0, -0.12)
	q[HumanoidBuilder.B_FOREARM_R] = _e(-1.15 + 0.12 * w1, 0.25)
	q[HumanoidBuilder.B_HAND_L] = _qx(0.2 * w1)
	q[HumanoidBuilder.B_HAND_R] = _qx(0.2 * w2)


func _pose_air(q: Array) -> Vector3:
	q[HumanoidBuilder.B_THIGH_L] = _e(-0.55, 0.0, 0.05)
	q[HumanoidBuilder.B_THIGH_R] = _e(-0.35, 0.0, -0.05)
	q[HumanoidBuilder.B_SHIN_L] = _qx(0.8)
	q[HumanoidBuilder.B_SHIN_R] = _qx(0.6)
	q[HumanoidBuilder.B_FOOT_L] = _qx(0.2)
	q[HumanoidBuilder.B_FOOT_R] = _qx(0.2)
	q[HumanoidBuilder.B_UPPERARM_L] = _e(-0.35, 0.0, 0.45)
	q[HumanoidBuilder.B_UPPERARM_R] = _e(-0.25, 0.0, -0.45)
	q[HumanoidBuilder.B_FOREARM_L] = _qx(-0.6)
	q[HumanoidBuilder.B_FOREARM_R] = _qx(-0.6)
	q[HumanoidBuilder.B_SPINE] = _qx(0.05)
	return Vector3.ZERO


func _pose_sit(q: Array) -> Vector3:
	var p := proportions
	var breathe := sin(_t * TAU * 0.25)
	var hips := Vector3(0.0, p.knee_y - p.hip_y, 0.0)
	q[HumanoidBuilder.B_HIPS] = _qx(-0.08)
	q[HumanoidBuilder.B_SPINE] = _qx(0.04 + 0.1 * p.stoop)
	q[HumanoidBuilder.B_CHEST] = _qx(0.06 + 0.2 * p.stoop - 0.012 * breathe)
	q[HumanoidBuilder.B_NECK] = _qx(-0.05 - 0.15 * p.stoop)
	q[HumanoidBuilder.B_HEAD] = _e(_look.y - 0.05 - 0.1 * p.stoop, _look.x)
	for side in 2:
		var sgn := 1.0 if side == 0 else -1.0
		var tb := HumanoidBuilder.B_THIGH_L if side == 0 else HumanoidBuilder.B_THIGH_R
		q[tb] = _e(-PI * 0.5 + 0.08, 0.0, sgn * 0.06)
		q[tb + 1] = _qx(PI * 0.5 - 0.02)
		q[tb + 2] = _e(0.0, 0.0, -sgn * 0.06)
		var ua := HumanoidBuilder.B_UPPERARM_L if side == 0 else HumanoidBuilder.B_UPPERARM_R
		q[ua] = _e(-0.42, 0.0, sgn * 0.12)
		q[ua + 1] = _e(-0.95, -sgn * 0.3)
		q[ua + 2] = _e(0.2, 0.0, 0.0)
	if _action == "talk":
		_jaw = 0.11 * absf(sin(_t * 9.0) * sin(_t * 3.3))
	return hips


## Bodensitz (Babys).
func _pose_floor(q: Array) -> Vector3:
	var p := proportions
	var hips := Vector3(0.0, -(p.hip_y - p.hip_d * 0.45), -p.thigh_len * 0.1)
	var sway := sin(_t * 0.9) * 0.04
	q[HumanoidBuilder.B_HIPS] = _qx(-0.05)
	q[HumanoidBuilder.B_SPINE] = _e(0.12 + sway, sway * 0.5)
	q[HumanoidBuilder.B_CHEST] = _qx(0.05)
	q[HumanoidBuilder.B_HEAD] = _e(_look.y, _look.x * 1.5)
	for side in 2:
		var sgn := 1.0 if side == 0 else -1.0
		var tb := HumanoidBuilder.B_THIGH_L if side == 0 else HumanoidBuilder.B_THIGH_R
		q[tb] = _e(-1.45, sgn * 0.35, sgn * 0.35)
		q[tb + 1] = _qx(0.55)
		q[tb + 2] = _qx(-0.4)
		var ua := HumanoidBuilder.B_UPPERARM_L if side == 0 else HumanoidBuilder.B_UPPERARM_R
		q[ua] = _e(-0.5 + 0.1 * sin(_t * 1.3 + side), 0.0, sgn * 0.3)
		q[ua + 1] = _qx(-0.9 - 0.2 * sin(_t * 1.7 + side * 2.0))
	return hips


func _pose_crawl(q: Array, dt: float) -> Vector3:
	var p := proportions
	var a := p.thigh_len
	var arm := p.upper_arm_len + p.forearm_len + p.hand_len * 0.3
	var hip_h := a + p.knee_r
	var torso := p.shoulder_y - p.hip_y
	var c := clampf((arm * 0.95 - hip_h) / maxf(torso, 0.01), -0.3, 0.6)
	var tilt := acos(c)
	_phase = fmod(_phase + dt * _sp / maxf(0.35 * a + 0.05, 0.05), 1.0)
	var cyc := TAU * _phase
	var hips := Vector3(0.0, hip_h - p.hip_y, 0.0)
	q[HumanoidBuilder.B_HIPS] = _e(tilt, 0.06 * sin(cyc))
	q[HumanoidBuilder.B_NECK] = _qx(-tilt * 0.55)
	q[HumanoidBuilder.B_HEAD] = _e(-tilt * 0.4, _look.x)
	for side in 2:
		var sgn := 1.0 if side == 0 else -1.0
		var sw := 0.28 * sin(cyc + PI * side)
		var tb := HumanoidBuilder.B_THIGH_L if side == 0 else HumanoidBuilder.B_THIGH_R
		q[tb] = _e(-tilt + sw, 0.0, sgn * 0.12)
		q[tb + 1] = _qx(PI * 0.5)
		q[tb + 2] = _qx(0.6)
		var ua := HumanoidBuilder.B_UPPERARM_L if side == 0 else HumanoidBuilder.B_UPPERARM_R
		q[ua] = _e(-tilt - sw * 0.9, 0.0, sgn * 0.1)
		q[ua + 1] = _qx(-0.1 - 0.15 * maxf(sin(cyc + PI * side), 0.0))
		q[ua + 2] = _qx(-1.2)
	return hips


func _pose_sleep(q: Array) -> Transform3D:
	var p := proportions
	var breathe := sin(_t * TAU * 0.2)
	var back := maxf(p.hip_d * 0.5 + p.glute, p.chest_d * 0.5)
	q[HumanoidBuilder.B_CHEST] = _qx(-0.015 * breathe)
	q[HumanoidBuilder.B_NECK] = _qx(0.12)
	q[HumanoidBuilder.B_HEAD] = _e(0.05, 0.28)
	for side in 2:
		var sgn := 1.0 if side == 0 else -1.0
		var tb := HumanoidBuilder.B_THIGH_L if side == 0 else HumanoidBuilder.B_THIGH_R
		q[tb] = _e(-0.12, 0.0, sgn * 0.05)
		q[tb + 1] = _qx(0.22)
		q[tb + 2] = _qx(-0.5)
		var ua := HumanoidBuilder.B_UPPERARM_L if side == 0 else HumanoidBuilder.B_UPPERARM_R
		q[ua] = _e(-0.05, 0.0, sgn * 0.14)
		q[ua + 1] = _qx(-0.25 - 0.3 * float(side))
	var basis := Basis(Vector3.RIGHT, -PI * 0.5)
	var sole: float = _data.get("sole", 0.0)
	return Transform3D(basis, Vector3(0.0, back, p.hip_y + sole))


func _pose_face(q: Array, dt: float, mode: String) -> void:
	# Blinzeln
	_blink_t -= dt
	if _blink_t <= 0.0:
		_blink_t = _rng.randf_range(2.0, 6.0)
		_blink = 1.0
	_blink = maxf(0.0, _blink - dt / 0.16)
	var lids := sin(_blink * PI) * 0.62
	if mode == "sleep":
		lids = 0.62
	q[HumanoidBuilder.B_LIDS] = _qx(lids + 0.06 * proportions.jowls)
	# Blickrichtung
	_look_t -= dt
	if _look_t <= 0.0:
		_look_t = _rng.randf_range(1.5, 4.5)
		_look_target = Vector2(_rng.randf_range(-0.35, 0.35), _rng.randf_range(-0.08, 0.1))
		if _sp > 0.5:
			_look_target *= 0.3
	_look = _look.lerp(_look_target, 1.0 - exp(-3.0 * dt))
	var ey := _look_target.x * 0.6 - _look.x * 0.3
	q[HumanoidBuilder.B_EYE_L] = _e(-_look_target.y * 0.5, ey)
	q[HumanoidBuilder.B_EYE_R] = _e(-_look_target.y * 0.5, ey)
	if _action != "talk":
		_jaw = lerpf(_jaw, 0.0, 1.0 - exp(-10.0 * dt))
	q[HumanoidBuilder.B_JAW] = _qx(_jaw)


## Stellvertreter ohne Skelett: Wippen beim Gehen, Absenken beim Sitzen.
func _animate_proxy(dt: float) -> void:
	_t += dt
	_sp = lerpf(_sp, _speed, 1.0 - exp(-6.0 * dt))
	if _mesh == null:
		return
	var p := proportions
	var L := p.leg_length()
	var f := 0.9 + 0.7 * _sp / sqrt(L / 0.87)
	_phase = fmod(_phase + dt * f * 0.5, 1.0)
	var xf := Transform3D()
	match _action:
		"sleep":
			xf = Transform3D(Basis(Vector3.RIGHT, -PI * 0.5), Vector3(0, p.chest_d * 0.5, p.hip_y))
		"sit":
			xf.origin.y = (p.knee_y - p.hip_y) * 0.85
		_:
			var m := _sstep(0.05, 0.5, _sp)
			xf.origin.y = absf(sin(TAU * _phase)) * 0.025 * L * m
			xf.basis = Basis(Vector3.BACK, 0.03 * m * sin(TAU * _phase))
	_mesh.transform = xf


static func _sstep(a: float, b: float, x: float) -> float:
	var t := clampf((x - a) / (b - a), 0.0, 1.0)
	return t * t * (3.0 - 2.0 * t)
