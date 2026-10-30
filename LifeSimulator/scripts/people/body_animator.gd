class_name BodyAnimator
extends Node
## Bewegt das Skelett eines zugelieferten Personenmodells.
##
## Solche Modelle kommen ohne Bewegungsablaeufe und in T-Haltung: Arme
## waagerecht ausgestreckt. Dieser Knoten dreht die Knochen selbst - erst in
## eine brauchbare Grundhaltung (Arme am Koerper), dann im Takt des Gehens.
## Damit braucht das Spiel keine mitgelieferten Bewegungsdateien.
##
## Die Knochennamen stehen in der Szene (`bone_roles`, von
## tools/prepare_person.gd geschrieben), es wird also nichts geraten.
##
## Die Drehachse jedes Knochens ist bei jedem Modell anders. Deshalb wird aus
## der Ruhelage die Achse berechnet, die der Weltachse entspricht: so dreht
## "Oberschenkel nach vorn" auch wirklich nach vorn, egal wie der Knochen im
## Modell liegt.

## Grundhaltung in Grad: Arme aus der T-Haltung an den Koerper.
##
## Die Haende brauchen eine eigene Drehung: In der T-Haltung zeigen die
## Handflaechen nach unten; sind die Arme erst gesenkt, zeigen sie nach vorn
## und die Finger stehen seitlich ab. Eine Drehung um die Armachse legt sie an
## den Koerper.
const REST_POSE := {
	"upperarm_l": {"axis": "z", "deg": -76.0},
	"upperarm_r": {"axis": "z", "deg": 76.0},
	"lowerarm_l": {"axis": "y", "deg": -10.0},
	"lowerarm_r": {"axis": "y", "deg": 10.0},
	"hand_l": {"axis": "y", "deg": 78.0},
	"hand_r": {"axis": "y", "deg": -78.0},
}

## Ausschlag der Gehbewegung in Grad.
const WALK := {
	"thigh": 26.0, "calf": 38.0, "foot": 10.0,
	"arm": 22.0, "forearm": 14.0, "spine": 3.0, "head": 2.0,
}

## Wird ausgeloest, wenn ein Fuss aufsetzt: side 0 links, 1 rechts.
## Die Lautstaerke richtet sich nach dem Tempo.
signal footstep(side: int, intensity: float)

var skeleton: Skeleton3D = null
var speed := 0.0            ## Laufgeschwindigkeit in m/s (steuert Takt und Ausschlag)
var seated := false         ## Sitzhaltung statt Stehen
var paused := false

var _idx := {}              ## Rolle -> Knochennummer
var _rest := {}             ## Rolle -> Ruhedrehung (Quaternion)
var _axis := {}             ## Rolle -> {x,y,z} als Achse im Knochenraum
var _base := {}             ## Rolle -> zusaetzliche Grunddrehung (Quaternion)
var _phase := 0.0
var _blend := 0.0           ## 0 = stehen, 1 = gehen


## Richtet den Bewegungsablauf auf einem aufbereiteten Personenmodell ein.
## Gibt false zurueck, wenn die Szene kein Skelett mit Rollen mitbringt.
func setup(person: Node3D) -> bool:
	var roles: Dictionary = person.get_meta("bone_roles", {})
	if roles.is_empty():
		return false
	var path: NodePath = person.get_meta("skeleton_path", NodePath())
	skeleton = person.get_node_or_null(path) as Skeleton3D
	if skeleton == null:
		skeleton = _find_skeleton(person)
	if skeleton == null:
		return false
	for role in roles:
		var i := skeleton.find_bone(str(roles[role]))
		if i < 0:
			continue
		_idx[role] = i
		_rest[role] = skeleton.get_bone_rest(i).basis.get_rotation_quaternion()
		# Weltachsen in den Knochenraum holen
		var gb: Basis = skeleton.get_bone_global_rest(i).basis
		var inv := gb.inverse()
		_axis[role] = {
			"x": (inv * Vector3.RIGHT).normalized(),
			"y": (inv * Vector3.UP).normalized(),
			"z": (inv * Vector3.BACK).normalized(),
		}
	for role in REST_POSE:
		if not _idx.has(role):
			continue
		var d: Dictionary = REST_POSE[role]
		_base[role] = Quaternion(_axis_of(role, str(d.axis)), deg_to_rad(float(d.deg)))
	return not _idx.is_empty()


func _find_skeleton(n: Node) -> Skeleton3D:
	if n is Skeleton3D:
		return n
	for c in n.get_children():
		var s := _find_skeleton(c)
		if s != null:
			return s
	return null


## Wurde beim Fortschreiten von `a` nach `b` der Winkel `mark` ueberschritten?
## Beruecksichtigt den Ueberlauf von TAU auf 0.
func _crossed(a: float, b: float, mark: float) -> bool:
	if b >= a:
		return a < mark and mark <= b
	# ueber den Nullpunkt gelaufen
	return mark > a or mark <= b


func _axis_of(role: String, key: String) -> Vector3:
	var a: Dictionary = _axis.get(role, {})
	return a.get(key, Vector3.RIGHT)


func _process(delta: float) -> void:
	if skeleton == null or paused:
		return
	step(delta)


## Einen Zeitschritt rechnen. Oeffentlich, damit Aufrufer den Ablauf auch
## selbst takten koennen (etwa im Leistungsmodus nur jeden zweiten Bild).
func step(delta: float) -> void:
	if skeleton == null:
		return
	# Schrittlaenge haengt an der Geschwindigkeit: schneller = hoeherer Takt
	var cadence: float = clampf(speed * 1.8, 0.0, 6.5)
	var prev := _phase
	_phase = fposmod(_phase + delta * cadence * TAU * 0.5, TAU)
	# Ein Fuss setzt auf, wenn sein Bein durch die hinterste Lage geht: links
	# bei Phase 0, rechts bei PI. Gemeldet wird beim Ueberschreiten.
	if speed > 0.15 and delta > 0.0:
		if _crossed(prev, _phase, 0.0):
			footstep.emit(0, clampf(speed / 1.6, 0.25, 1.0))
		if _crossed(prev, _phase, PI):
			footstep.emit(1, clampf(speed / 1.6, 0.25, 1.0))
	_blend = move_toward(_blend, 1.0 if speed > 0.15 else 0.0, delta * 4.0)
	var amp: float = clampf(speed / 1.6, 0.0, 1.35) * _blend
	if seated:
		_apply_seated()
		return
	var t: float = Time.get_ticks_msec() * 0.001
	var breath: float = sin(t * 1.6) * 1.1 * (1.0 - _blend)

	_bone("thigh_l", "x", sin(_phase) * WALK.thigh * amp)
	_bone("thigh_r", "x", sin(_phase + PI) * WALK.thigh * amp)
	# Das Knie beugt nur in eine Richtung
	_bone("calf_l", "x", -maxf(0.0, -sin(_phase - 0.7)) * WALK.calf * amp)
	_bone("calf_r", "x", -maxf(0.0, -sin(_phase + PI - 0.7)) * WALK.calf * amp)
	_bone("foot_l", "x", -sin(_phase + 0.5) * WALK.foot * amp)
	_bone("foot_r", "x", -sin(_phase + PI + 0.5) * WALK.foot * amp)
	# Arme gegenlaeufig zum gleichseitigen Bein
	_bone("upperarm_l", "x", sin(_phase + PI) * WALK.arm * amp + breath * 0.4)
	_bone("upperarm_r", "x", sin(_phase) * WALK.arm * amp - breath * 0.4)
	_bone("lowerarm_l", "x", -absf(sin(_phase + PI)) * WALK.forearm * amp - 8.0)
	_bone("lowerarm_r", "x", -absf(sin(_phase)) * WALK.forearm * amp - 8.0)
	# Rumpf dreht leicht gegen die Beine, Kopf bleibt ruhiger
	_bone("spine", "y", sin(_phase) * WALK.spine * amp)
	_bone("chest", "y", sin(_phase + PI) * WALK.spine * 0.7 * amp + breath * 0.25)
	_bone("head", "y", sin(_phase + PI) * WALK.head * amp)
	_bone("hips", "x", 2.5 * amp)


## Sitzhaltung. Die Oberschenkel gehen waagerecht nach vorn, die Unterschenkel
## senkrecht nach unten - damit sitzt die Figur auf einer Flaeche, statt in der
## Luft gebeugt zu haengen. Der Oberkoerper bleibt fast aufrecht.
func _apply_seated() -> void:
	_bone("hips", "x", -4.0)
	_bone("thigh_l", "x", 88.0)
	_bone("thigh_r", "x", 88.0)
	_bone("calf_l", "x", -88.0)
	_bone("calf_r", "x", -88.0)
	_bone("foot_l", "x", 2.0)
	_bone("foot_r", "x", 2.0)
	_bone("upperarm_l", "x", 14.0)
	_bone("upperarm_r", "x", 14.0)
	_bone("lowerarm_l", "x", -56.0)
	_bone("lowerarm_r", "x", -56.0)
	_bone("spine", "x", 4.0)
	_bone("chest", "x", -2.0)


## Dreht einen Knochen um die angegebene Weltachse, aufgesetzt auf Ruhelage
## und Grundhaltung.
func _bone(role: String, axis: String, degrees: float) -> void:
	if not _idx.has(role):
		return
	var q: Quaternion = _rest[role]
	if _base.has(role):
		q = q * _base[role]
	if not is_zero_approx(degrees):
		q = q * Quaternion(_axis_of(role, axis), deg_to_rad(degrees))
	skeleton.set_bone_pose_rotation(int(_idx[role]), q)


## Nur die Grundhaltung setzen (Arme am Koerper), ohne Bewegung.
func apply_rest_pose() -> void:
	if skeleton == null:
		return
	for role in _idx:
		var q: Quaternion = _rest[role]
		if _base.has(role):
			q = q * _base[role]
		skeleton.set_bone_pose_rotation(int(_idx[role]), q)
