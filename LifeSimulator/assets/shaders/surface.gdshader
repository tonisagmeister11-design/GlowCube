// Weltkoordinaten-Oberflaeche fuer Boden, Gehwege, Bordsteine, Dachflaechen.
// Waagerechte Flaechen nutzen x/z, senkrechte die passende Wandebene,
// optional echte UVs (use_uv). Albedo-Alpha enthaelt die Rauheit.
// Grossflaechige Helligkeitsvariation bricht die Kachelwiederholung,
// Naesse dunkelt ab und erzeugt Pfuetzen.
// COLOR.rgb faerbt ein (sRGB), COLOR.a steuert die Pfuetzen-Neigung.
shader_type spatial;
render_mode cull_back, diffuse_burley, specular_schlick_ggx, skip_vertex_transform;

uniform sampler2D albedo_tex : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D normal_tex : hint_normal, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D noise_tex : filter_linear_mipmap, repeat_enable;
uniform vec3 tint : source_color = vec3(1.0);
uniform float tile = 4.0;
uniform float macro = 0.15;
uniform float normal_depth = 1.0;
uniform float rough_mul = 1.0;
uniform float metallic = 0.0;
uniform float use_uv = 0.0;
uniform float puddles = 0.0;
// Tiefenversatz zur Kamera (Markierungen / Auflagen gegen Z-Fighting)
uniform float depth_bias = 0.0;
uniform float wet = 0.0;
uniform float night = 0.0;

varying vec3 wpos;
varying vec3 wnrm;
varying vec4 vcol;

vec3 to_linear(vec3 c) {
	return pow(max(c, vec3(0.0)), vec3(2.2));
}

void vertex() {
	wpos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	wnrm = normalize((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz);
	vcol = COLOR;
	VERTEX = (MODELVIEW_MATRIX * vec4(VERTEX, 1.0)).xyz;
	VERTEX *= 1.0 - depth_bias;
	NORMAL = normalize((MODELVIEW_MATRIX * vec4(NORMAL, 0.0)).xyz);
	TANGENT = normalize((MODELVIEW_MATRIX * vec4(TANGENT, 0.0)).xyz);
	BINORMAL = normalize((MODELVIEW_MATRIX * vec4(BINORMAL, 0.0)).xyz);
}

void fragment() {
	vec2 p;
	if (use_uv > 0.5) {
		p = UV;
	} else if (abs(wnrm.y) > 0.6) {
		p = wpos.xz;
	} else if (abs(wnrm.x) > abs(wnrm.z)) {
		p = vec2(-wpos.z * sign(wnrm.x), -wpos.y);
	} else {
		p = vec2(wpos.x * sign(wnrm.z), -wpos.y);
	}
	vec2 uv = p / tile;
	vec4 a = texture(albedo_tex, uv);
	// zweite, gedrehte Abtastung gegen sichtbare Kachelung
	vec2 uv2 = mat2(vec2(0.8, 0.6), vec2(-0.6, 0.8)) * uv * 0.37 + 0.31;
	vec4 a2 = texture(albedo_tex, uv2);
	float m1 = texture(noise_tex, wpos.xz * 0.0071).r;
	float m2 = texture(noise_tex, wpos.xz * 0.031 + 0.5).g;
	float blend = smoothstep(0.35, 0.65, m2);
	vec3 col = mix(a.rgb, a2.rgb, blend * 0.35);
	float macro_v = (m1 - 0.5) * 1.4 + (m2 - 0.5) * 0.6;
	col *= 1.0 + macro_v * macro;
	col *= tint * to_linear(vcol.rgb);
	float rough = clamp(mix(a.a, a2.a, blend * 0.35) * rough_mul, 0.04, 1.0);
	vec3 n = texture(normal_tex, uv).rgb;

	// Naesse und Pfuetzen (nur auf waagerechten Flaechen)
	float flat_f = smoothstep(0.8, 0.95, wnrm.y);
	float pud_n = texture(noise_tex, wpos.xz * 0.045).r * 0.7 + texture(noise_tex, wpos.xz * 0.19).b * 0.3;
	float pud = flat_f * puddles * vcol.a * smoothstep(0.62 - wet * 0.18, 0.66 - wet * 0.18, pud_n) * step(0.05, wet);
	col *= 1.0 - wet * 0.35;
	rough = mix(rough, rough * 0.35, wet);
	col = mix(col, col * 0.55, pud);
	rough = mix(rough, 0.03, pud);
	n = mix(n, vec3(0.5, 0.5, 1.0), pud);

	ALBEDO = col;
	ROUGHNESS = rough;
	METALLIC = metallic;
	NORMAL_MAP = n;
	NORMAL_MAP_DEPTH = normal_depth;
}
