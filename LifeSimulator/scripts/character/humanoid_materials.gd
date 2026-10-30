class_name HumanoidMaterials
extends RefCounted
## Gemeinsame Materialien aller Figuren. Farben stecken in Vertexfarben,
## figurenabhaengige Parameter (Hautalter, Sommersprossen, Ergrauen, Stoffart)
## in UV2. Dadurch teilen sich alle Figuren dieselben vier Materialien und
## der Renderer muss zwischen Figuren keine Materialzustaende wechseln.
##
## Vertexattribute:
##   UV  = Ruheposition (x, y) in Metern, UV2.x = Ruheposition z
##   Haut:  UV2.y = Alter (Nachkomma) + Sommersprossen * 10 (ganzzahlig),
##          COLOR.a = Faltenzone (0..1)
##   Stoff: UV2.y = Stoffart (FABRIC_*)
##   Haar:  UV2.y = Grauanteil
##   Auge:  UV = Richtung im Augapfel (x, y), UV2.x = Richtung z

const FABRIC_COTTON := 0
const FABRIC_DENIM := 1
const FABRIC_KNIT := 2
const FABRIC_LEATHER := 3
const FABRIC_PLASTIC := 4
const FABRIC_METAL := 5
const FABRIC_RUBBER := 6
const FABRIC_SKIN := 7
const FABRIC_SATIN := 8
const FABRIC_GLASS := 9

const _NOISE := """
float hh(vec3 p) {
	p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
	p *= 17.0;
	return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}
vec3 lin(vec3 c) {
	return mix(c / 12.92, pow((c + vec3(0.055)) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
}
float vnoise(vec3 x) {
	vec3 i = floor(x);
	vec3 f = fract(x);
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(mix(hh(i + vec3(0, 0, 0)), hh(i + vec3(1, 0, 0)), f.x),
				mix(hh(i + vec3(0, 1, 0)), hh(i + vec3(1, 1, 0)), f.x), f.y),
			mix(mix(hh(i + vec3(0, 0, 1)), hh(i + vec3(1, 0, 1)), f.x),
				mix(hh(i + vec3(0, 1, 1)), hh(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}
"""

const SKIN_SHADER := """
shader_type spatial;
render_mode cull_back, diffuse_burley, specular_schlick_ggx;
varying vec3 rest;
varying vec2 prm;
%s
void vertex() {
	rest = vec3(UV.x, UV.y, UV2.x);
	prm = vec2(fract(UV2.y), floor(UV2.y + 0.0001) / 10.0);
}
void fragment() {
	float age = prm.x;
	float freck = prm.y;
	float zone = COLOR.a;
	vec3 base = pow(COLOR.rgb, vec3(1.55));
	float n1 = vnoise(rest * 140.0);
	float n2 = vnoise(rest * 26.0);
	vec3 col = base * (0.955 + 0.075 * n2);
	// Rötung/Äderchen leicht variieren
	col = mix(col, col * vec3(1.04, 0.95, 0.94), smoothstep(0.55, 0.9, vnoise(rest * 11.0 + 3.0)) * 0.35);
	// Altersflecken
	float spots = smoothstep(0.70, 0.86, vnoise(rest * 38.0 + 7.0)) * age * age;
	col = mix(col, col * vec3(0.80, 0.68, 0.56), spots * 0.45);
	// Sommersprossen
	float fr = smoothstep(0.66, 0.80, vnoise(rest * 210.0 + 1.7)) * freck;
	col = mix(col, col * vec3(0.72, 0.52, 0.38), fr * (0.45 + 0.4 * zone));
	// Falten: feine Linien in den Faltenzonen
	float lines = 0.5 + 0.5 * sin(rest.y * 820.0 + n2 * 9.0 + rest.x * 40.0);
	float wr = smoothstep(0.80, 1.0, lines) * zone * smoothstep(0.15, 1.0, age);
	col *= 1.0 - wr * 0.22;
	ALBEDO = col;
	ROUGHNESS = clamp(0.5 + age * 0.18 + n1 * 0.08 + wr * 0.2, 0.0, 1.0);
	SPECULAR = 0.42 - age * 0.14;
}
"""

const CLOTH_SHADER := """
shader_type spatial;
render_mode cull_back, diffuse_burley, specular_schlick_ggx;
varying vec3 rest;
varying float fabric;
%s
void vertex() {
	rest = vec3(UV.x, UV.y, UV2.x);
	fabric = UV2.y;
}
void fragment() {
	int f = int(fabric + 0.5);
	vec3 col = lin(COLOR.rgb);
	float rough = 0.86;
	float spec = 0.3;
	float metal = 0.0;
	float n = vnoise(rest * 60.0);
	float fine = vnoise(rest * 420.0);
	if (f == 0) {          // Baumwolle
		col *= 0.95 + 0.07 * n + 0.05 * fine;
	} else if (f == 1) {   // Denim: Koeper-Diagonale
		float tw = 0.5 + 0.5 * sin((rest.x + rest.z + rest.y) * 900.0);
		col *= 0.86 + 0.16 * tw * (0.6 + 0.4 * fine) + 0.1 * n;
		col = mix(col, col * vec3(1.15, 1.15, 1.12), smoothstep(0.6, 0.95, vnoise(rest * 14.0)) * 0.12);
		rough = 0.92;
	} else if (f == 2) {   // Strick: Rippen
		float rib = 0.5 + 0.5 * sin((rest.x - rest.z) * 520.0 + n * 2.0);
		col *= 0.84 + 0.2 * rib + 0.06 * fine;
		rough = 0.95;
	} else if (f == 3) {   // Leder
		col *= 0.92 + 0.12 * n;
		rough = 0.38 + 0.2 * fine;
		spec = 0.55;
	} else if (f == 4) {   // Kunststoff
		rough = 0.3;
		spec = 0.5;
	} else if (f == 5) {   // Metall
		rough = 0.28;
		metal = 1.0;
		spec = 0.6;
	} else if (f == 6) {   // Gummi
		rough = 0.75;
	} else if (f == 7) {   // Haut auf Stoffflaeche
		col *= 0.97 + 0.05 * n;
		rough = 0.55;
		spec = 0.4;
	} else if (f == 8) {   // Satin
		col *= 0.95 + 0.06 * n;
		rough = 0.45;
		spec = 0.5;
	} else if (f == 9) {   // Glas, getoent
		rough = 0.06;
		spec = 0.8;
	}
	ALBEDO = col;
	ROUGHNESS = rough;
	SPECULAR = spec;
	METALLIC = metal;
}
"""

const HAIR_SHADER := """
shader_type spatial;
render_mode cull_back, diffuse_burley, specular_schlick_ggx;
varying vec3 rest;
varying float grey;
%s
void vertex() {
	rest = vec3(UV.x, UV.y, UV2.x);
	grey = UV2.y;
}
void fragment() {
	vec3 p = rest;
	float strands = vnoise(vec3(p.x * 260.0, p.y * 30.0, p.z * 260.0));
	float clumps = vnoise(vec3(p.x * 60.0, p.y * 9.0, p.z * 60.0));
	float g = step(1.0 - grey, vnoise(vec3(p.x * 330.0, p.y * 40.0, p.z * 330.0)) * 0.85 + grey * 0.25);
	vec3 base = lin(COLOR.rgb);
	vec3 silver = mix(vec3(0.34, 0.34, 0.35), vec3(0.82, 0.82, 0.8), smoothstep(0.6, 1.0, grey));
	base = mix(base, silver, g);
	vec3 col = base * (0.62 + 0.42 * strands) * (0.85 + 0.25 * clumps);
	ALBEDO = col;
	ROUGHNESS = 0.42 + 0.3 * (1.0 - strands);
	SPECULAR = 0.45;
}
"""

const EYE_SHADER := """
shader_type spatial;
render_mode cull_back, diffuse_burley, specular_schlick_ggx;
varying vec3 dir;
%s
void vertex() {
	dir = vec3(UV.x, UV.y, UV2.x);
}
void fragment() {
	vec3 d = normalize(dir);
	float r = length(d.xy) / 0.5;
	vec3 sclera = vec3(0.80, 0.77, 0.73);
	sclera = mix(sclera, vec3(0.86, 0.72, 0.70), smoothstep(0.75, 0.15, d.z) * 0.6);
	vec3 col = sclera;
	if (d.z > 0.0 && r < 1.0) {
		float ang = atan(d.y, d.x);
		float streak = vnoise(vec3(ang * 9.0, r * 6.0, 1.0));
		vec3 iris = lin(COLOR.rgb) * (0.7 + 0.55 * streak);
		iris = mix(iris, lin(COLOR.rgb) * 1.35 + vec3(0.05, 0.04, 0.0), smoothstep(0.55, 0.3, r) * 0.5);
		iris *= mix(1.0, 0.35, smoothstep(0.78, 1.0, r));
		col = iris;
		col = mix(col, vec3(0.015), smoothstep(0.40, 0.36, r));
	} else if (d.z > 0.0) {
		col = mix(col, lin(COLOR.rgb) * 0.3, smoothstep(1.12, 1.0, r) * 0.6);
	}
	ALBEDO = col;
	ROUGHNESS = 0.07;
	SPECULAR = 0.75;
	CLEARCOAT = 0.4;
}
"""

static var _cache := {}


static func skin() -> ShaderMaterial:
	return _mat("skin", SKIN_SHADER)


static func cloth() -> ShaderMaterial:
	return _mat("cloth", CLOTH_SHADER)


static func hair() -> ShaderMaterial:
	return _mat("hair", HAIR_SHADER)


static func eye() -> ShaderMaterial:
	return _mat("eye", EYE_SHADER)


static func _mat(key: String, code: String) -> ShaderMaterial:
	if _cache.has(key):
		return _cache[key]
	var sh := Shader.new()
	sh.code = code % _NOISE
	var m := ShaderMaterial.new()
	m.shader = sh
	_cache[key] = m
	return m


## Packt Hautalter und Sommersprossen in UV2.y der Haut.
static func skin_param(skin_age: float, freckles: float) -> float:
	return floor(clampf(freckles, 0.0, 1.0) * 9.0 + 0.5) + clampf(skin_age, 0.0, 0.999)
