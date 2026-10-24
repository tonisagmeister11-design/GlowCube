class_name BodyProportions
extends RefCounted
## Altersabhaengige Koerpermasse einer Figur (Meter, Ruhepose, Boden y = 0,
## Blick nach +Z, linke Koerperseite bei +X).
##
## Grundlage sind gemittelte Wachstumskurven und klassische
## Proportionstabellen: Kopf zu Koerperhoehe ca. 1:4 beim Kleinkind,
## 1:5-6 im Kindesalter, 1:7 bei Jugendlichen und 1:7,5 bei Erwachsenen.
## Kinder bekommen eigene Proportionen (kurze Beine, grosser Kopf, kaum Hals,
## rundes Gesicht) und sind keine verkleinerten Erwachsenen.

var age := 30.0
var body := "female"
## 0 = maennliche, 1 = weibliche Auspraegung (Pubertaet blendet ein).
var fem := 0.5
## Geschlechtsunterschiede 0 (Kleinkind) .. 1 (erwachsen).
var dimorph := 0.0
## Reife des Gesichts/Koerpers 0 (Baby) .. 1 (erwachsen).
var maturity := 1.0
var weight := 0.5
var muscle := 0.5
var height_factor := 0.5

var height := 1.7          ## Standhoehe ohne Schuhe (aufrecht, Ruhepose)
var head_h := 0.23         ## Kinn bis Scheitel
var head_w := 0.155        ## Kopfbreite
var head_d := 0.195        ## Kopftiefe
var head_ratio := 7.5      ## height / head_h (vor Altersschrumpfung)

var ankle_y := 0.07
var knee_y := 0.48
var hip_y := 0.87          ## Hueftgelenk
var crotch_y := 0.81
var waist_y := 1.03
var bust_y := 1.24
var shoulder_y := 1.42     ## Schultergelenk
var chin_y := 1.47
var thigh_len := 0.39
var shin_len := 0.41
var upper_arm_len := 0.31
var forearm_len := 0.25
var hand_len := 0.18
var foot_len := 0.25
var foot_w := 0.09

var shoulder_w := 0.34     ## Abstand der Schultergelenke
var chest_w := 0.30        ## Brustkorbbreite (aussen)
var chest_d := 0.21
var waist_w := 0.26
var waist_d := 0.19
var hip_w := 0.36          ## Hueftbreite (aussen)
var hip_d := 0.22
var hip_sep := 0.17        ## Abstand der Hueftgelenke
var neck_r := 0.055
var neck_gap := 0.09       ## Schultergelenk bis Kinn (sichtbarer Hals)

var thigh_r := 0.08
var knee_r := 0.05
var calf_r := 0.055
var ankle_r := 0.03
var upper_arm_r := 0.045
var elbow_r := 0.035
var forearm_r := 0.04
var wrist_r := 0.026

var bust := 0.0            ## Brustvolumen (Meter Vorwoelbung)
var belly := 0.0           ## Bauch (Meter Vorwoelbung)
var glute := 0.03
var baby_fat := 0.0        ## 0..1 Babyspeck (runde Glieder, Wangen)

## Gesicht (Kopfeinheiten, Kopfhoehe = 1, Mitte = 0)
var eye_y := 0.0           ## Augenhoehe relativ zur Kopfmitte
var eye_r := 0.052         ## Augapfelradius / Kopfhoehe
var nose_scale := 1.0
var ear_scale := 1.0
var chin_amount := 1.0
var cheek_fat := 0.3
var jowls := 0.0           ## Erschlaffung im Alter
var brow_ridge := 0.5

## Alterung
var grey := 0.0            ## Ergrauen 0..1
var skin_age := 0.0        ## Hautalterung 0..1
var stoop := 0.0           ## gebeugte Haltung 0..1
var frailty := 0.0         ## Gebrechlichkeit 0..1 (Gangbild)


## Berechnet alle Masse. build: {weight, muscle, height} 0..1.
static func compute(p_age: float, p_body: String, build := {}) -> BodyProportions:
	var p := BodyProportions.new()
	p._compute(p_age, p_body, build)
	return p


static func curve(x: float, pts: Array) -> float:
	if x <= pts[0][0]:
		return pts[0][1]
	for i in range(1, pts.size()):
		if x <= pts[i][0]:
			var a: Array = pts[i - 1]
			var b: Array = pts[i]
			var t: float = (x - a[0]) / (b[0] - a[0])
			# weicher Uebergang zwischen den Stuetzstellen
			t = t * t * (3.0 - 2.0 * t) * 0.35 + t * 0.65
			return lerpf(a[1], b[1], t)
	return pts[pts.size() - 1][1]


func _compute(p_age: float, p_body: String, build: Dictionary) -> void:
	age = clampf(p_age, 0.0, 110.0)
	body = p_body
	weight = clampf(float(build.get("weight", 0.5)), 0.0, 1.0)
	muscle = clampf(float(build.get("muscle", 0.5)), 0.0, 1.0)
	height_factor = clampf(float(build.get("height", 0.5)), 0.0, 1.0)
	var female := body == "female"
	dimorph = smoothstep(9.5 if female else 11.0, 16.0 if female else 17.5, age)
	fem = 0.5 + (0.5 if female else -0.5) * (0.12 + 0.88 * dimorph)
	maturity = smoothstep(0.0, 18.0, age)
	baby_fat = 1.0 - smoothstep(0.5, 6.0, age)

	# ---------------------------------------------------- Koerperhoehe
	var h_m := curve(age, [[0, 0.50], [0.5, 0.67], [1, 0.76], [2, 0.87], [3, 0.96], [4, 1.03], [5, 1.10],
		[6, 1.16], [7, 1.22], [8, 1.28], [9, 1.33], [10, 1.38], [11, 1.43], [12, 1.49], [13, 1.56],
		[14, 1.63], [15, 1.69], [16, 1.73], [17, 1.76], [18, 1.775], [21, 1.78]])
	var h_f := curve(age, [[0, 0.49], [0.5, 0.65], [1, 0.74], [2, 0.86], [3, 0.95], [4, 1.02], [5, 1.09],
		[6, 1.15], [7, 1.21], [8, 1.27], [9, 1.33], [10, 1.38], [11, 1.44], [12, 1.51], [13, 1.57],
		[14, 1.60], [15, 1.62], [16, 1.635], [18, 1.645], [21, 1.65]])
	var h_young := h_f if female else h_m
	var spread := lerpf(0.05, 0.075, maturity)
	h_young *= 1.0 + (height_factor - 0.5) * 2.0 * spread
	head_ratio = curve(age, [[0, 3.9], [1, 4.15], [2, 4.35], [3, 4.55], [4, 4.75], [6, 5.2], [8, 5.6],
		[10, 5.95], [12, 6.35], [14, 6.75], [16, 7.05], [18, 7.25], [22, 7.4], [30, 7.5]])
	head_h = h_young / head_ratio
	# Kopfgroesse variiert weniger als die Koerperhoehe
	head_h *= 1.0 - (height_factor - 0.5) * 2.0 * spread * 0.55
	var shrink := 1.0 - 0.0006 * maxf(0.0, age - 45.0) - 0.0010 * maxf(0.0, age - 70.0)
	height = h_young * shrink
	var H := h_young   # Proportionen beziehen sich auf die jugendliche Groesse

	# ---------------------------------------------------- Kopf
	head_w = head_h * curve(age, [[0, 0.84], [3, 0.80], [8, 0.75], [14, 0.71], [18, 0.68]]) * (0.97 if female and dimorph > 0.5 else 1.0)
	head_d = head_h * curve(age, [[0, 0.98], [3, 0.94], [10, 0.89], [18, 0.86]])
	eye_y = curve(age, [[0, -0.105], [2, -0.085], [6, -0.055], [12, -0.025], [18, 0.0]])
	eye_r = curve(age, [[0, 0.072], [2, 0.066], [6, 0.060], [12, 0.056], [18, 0.052]])
	nose_scale = curve(age, [[0, 0.52], [3, 0.62], [8, 0.75], [14, 0.9], [18, 1.0], [60, 1.04], [90, 1.1]])
	nose_scale *= lerpf(1.0, 0.9, fem * dimorph)
	ear_scale = curve(age, [[0, 0.95], [10, 0.98], [18, 1.0], [50, 1.05], [90, 1.14]])
	chin_amount = curve(age, [[0, 0.25], [4, 0.45], [12, 0.65], [18, 1.0]]) * lerpf(1.0, 0.72, fem * dimorph)
	cheek_fat = curve(age, [[0, 1.0], [2, 0.85], [5, 0.6], [10, 0.4], [18, 0.3], [60, 0.22], [85, 0.12]])
	cheek_fat += (weight - 0.5) * 0.5
	jowls = smoothstep(50.0, 92.0, age)
	brow_ridge = curve(age, [[0, 0.0], [10, 0.2], [18, 0.6]]) * lerpf(1.0, 0.45, fem * dimorph)

	# ---------------------------------------------------- vertikaler Aufbau
	neck_gap = H * curve(age, [[0, 0.010], [1, 0.016], [2, 0.022], [4, 0.030], [8, 0.040], [14, 0.050], [18, 0.055]])
	chin_y = height - head_h
	shoulder_y = chin_y - neck_gap
	var leg_ratio := curve(age, [[0, 0.355], [1, 0.38], [2, 0.40], [3, 0.415], [5, 0.44], [8, 0.465],
		[12, 0.485], [15, 0.495], [18, 0.50]]) - 0.006 * dimorph * (fem - 0.5) * 2.0
	ankle_y = H * curve(age, [[0, 0.036], [5, 0.038], [18, 0.039]])
	hip_y = H * leg_ratio * shrink
	var leg := hip_y - ankle_y
	thigh_len = leg * 0.48
	shin_len = leg - thigh_len
	knee_y = ankle_y + shin_len
	crotch_y = hip_y - H * 0.035
	var torso := shoulder_y - hip_y
	waist_y = hip_y + torso * lerpf(0.42, 0.36, maturity) + torso * 0.03 * fem * dimorph
	bust_y = hip_y + torso * 0.71
	foot_len = H * curve(age, [[0, 0.145], [3, 0.15], [18, 0.152]]) * lerpf(1.0, 0.95, fem * dimorph)
	foot_w = foot_len * curve(age, [[0, 0.45], [6, 0.40], [18, 0.37]])

	# ---------------------------------------------------- Arme
	upper_arm_len = H * curve(age, [[0, 0.150], [1, 0.158], [3, 0.166], [8, 0.176], [14, 0.184], [18, 0.187]])
	forearm_len = H * curve(age, [[0, 0.118], [1, 0.124], [3, 0.132], [8, 0.140], [14, 0.145], [18, 0.146]])
	hand_len = H * curve(age, [[0, 0.088], [3, 0.096], [8, 0.102], [18, 0.106]]) * lerpf(1.0, 0.95, fem * dimorph)

	# ---------------------------------------------------- Breiten (Anteil von H)
	var wmul := 1.0 + (weight - 0.5) * 0.55
	var mmul := 1.0 + (muscle - 0.5) * 0.30 * maturity
	var fat := baby_fat * 0.22
	shoulder_w = H * lerpf(lerpf(0.190, 0.207, maturity), lerpf(0.190, 0.184, maturity), fem) * (1.0 + (muscle - 0.5) * 0.08 * maturity)
	shoulder_w *= 1.0 + (weight - 0.5) * 0.05
	if age < 3.0:
		shoulder_w *= lerpf(1.08, 1.0, age / 3.0)
	chest_w = H * lerpf(0.185, 0.17, fem * dimorph) * (1.0 + (weight - 0.5) * 0.18 + (muscle - 0.5) * 0.06 * maturity) * (1.0 + fat * 0.5)
	chest_d = H * lerpf(0.125, 0.118, fem * dimorph) * (1.0 + (weight - 0.5) * 0.3 + (muscle - 0.5) * 0.08 * maturity) * (1.0 + fat * 0.6)
	waist_w = H * lerpf(lerpf(0.16, 0.165, maturity), lerpf(0.16, 0.142, dimorph), fem) * wmul * (1.0 + fat * 0.9)
	waist_d = H * lerpf(0.105, 0.10, fem * dimorph) * (1.0 + (weight - 0.5) * 0.75) * (1.0 + fat * 1.0)
	hip_w = H * lerpf(lerpf(0.185, 0.198, maturity), lerpf(0.185, 0.226, dimorph), fem) * (1.0 + (weight - 0.5) * 0.3) * (1.0 + fat * 0.5)
	hip_d = H * lerpf(0.118, 0.13, fem * dimorph) * (1.0 + (weight - 0.5) * 0.35) * (1.0 + fat * 0.5)
	hip_sep = H * lerpf(0.092, 0.10, fem * dimorph) * (1.0 + fat * 0.3)
	neck_r = H * lerpf(0.034, 0.029, fem * dimorph) * (1.0 + (weight - 0.5) * 0.3 + (muscle - 0.5) * 0.12 * maturity) * (1.0 + baby_fat * 0.6)
	if age < 10.0:
		neck_r *= lerpf(1.15, 1.0, age / 10.0)

	# ---------------------------------------------------- Gliedmassen
	var limb := wmul * (1.0 + fat * 1.1)
	thigh_r = H * lerpf(0.046, 0.05, fem * dimorph) * limb * (1.0 + (muscle - 0.5) * 0.18 * maturity)
	knee_r = H * 0.0295 * (1.0 + (weight - 0.5) * 0.3) * (1.0 + fat * 0.6)
	calf_r = H * lerpf(0.031, 0.030, fem * dimorph) * limb * (1.0 + (muscle - 0.5) * 0.16 * maturity)
	ankle_r = H * 0.0135 * (1.0 + (weight - 0.5) * 0.15) * (1.0 + fat * 0.5)
	upper_arm_r = H * lerpf(0.026, 0.023, fem * dimorph) * limb * mmul
	elbow_r = H * lerpf(0.0185, 0.0165, fem * dimorph) * (1.0 + (weight - 0.5) * 0.25) * (1.0 + fat * 0.6)
	forearm_r = H * lerpf(0.0215, 0.019, fem * dimorph) * limb * (1.0 + (muscle - 0.5) * 0.12 * maturity)
	wrist_r = H * lerpf(0.0145, 0.0128, fem * dimorph) * (1.0 + (weight - 0.5) * 0.12) * (1.0 + fat * 0.5)
	# Muskelabbau im hohen Alter
	var sarco := smoothstep(60.0, 95.0, age) * 0.12
	upper_arm_r *= 1.0 - sarco
	forearm_r *= 1.0 - sarco * 0.7
	thigh_r *= 1.0 - sarco
	calf_r *= 1.0 - sarco

	bust = H * 0.034 * fem * smoothstep(10.0, 16.0, age) * (0.75 + weight * 0.6) if female else 0.0
	belly = H * (maxf(0.0, weight - 0.45) * 0.075 + smoothstep(35.0, 70.0, age) * 0.012 * (0.4 + weight) + baby_fat * 0.025)
	glute = H * (0.012 + 0.012 * fem * dimorph + (weight - 0.5) * 0.012)

	# ---------------------------------------------------- Alterung
	grey = smoothstep(36.0, 82.0, age)
	skin_age = smoothstep(28.0, 90.0, age)
	stoop = smoothstep(62.0, 96.0, age)
	frailty = smoothstep(68.0, 98.0, age)


## Kopfhoehe zu Koerperhoehe (z. B. 7.5 fuer Erwachsene).
func head_to_height() -> float:
	return height / head_h


## Lichte Beinlaenge (Hueftgelenk bis Knoechel).
func leg_length() -> float:
	return hip_y - ankle_y


func arm_length() -> float:
	return upper_arm_len + forearm_len + hand_len
