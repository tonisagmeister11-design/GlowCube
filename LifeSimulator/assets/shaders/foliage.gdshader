// Laub fuer Baeume und Hecken: Blatt-Textur, Farbvariation je Instanz und
// Vertexfarbe, Windbewegung (COLOR.a = Biegsamkeit), Durchscheinen im
// Gegenlicht.
shader_type spatial;

// Windstaerke-Faktor des Leistungsmanagers (0 = kein Wind, spart Vertexarbeit)
global uniform float perf_wind;
render_mode cull_back, diffuse_burley, specular_schlick_ggx;

uniform sampler2D leaf_tex : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D leaf_nrm : hint_normal, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D noise_tex : filter_linear_mipmap, repeat_enable;
uniform float tile = 1.6;
uniform vec3 tint : source_color = vec3(1.0);
uniform float wind = 0.4;
uniform float use_world_uv = 1.0;
uniform float wet = 0.0;
uniform float season = 0.0;

varying vec3 wpos;
varying vec4 vcol;
varying float inst_var;

void vertex() {
	vec3 origin = (MODEL_MATRIX * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
	inst_var = fract(sin(dot(origin.xz, vec2(12.9898, 78.233))) * 43758.5453);
	vcol = COLOR;
	// Im Leistungsmodus steht das Laub still: spart die Bewegungsrechnung
	// an sehr vielen Blattflaechen.
	if (perf_wind > 0.01) {
		float bend = COLOR.a * wind * perf_wind;
		float ph = TIME * 1.3 + origin.x * 0.21 + origin.z * 0.17;
		VERTEX.x += sin(ph) * 0.06 * bend * VERTEX.y * 0.15;
		VERTEX.z += cos(ph * 0.8) * 0.05 * bend * VERTEX.y * 0.15;
		VERTEX += NORMAL * sin(ph * 3.1 + VERTEX.y) * 0.02 * bend;
	}
	wpos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
}

void fragment() {
	vec2 uv = use_world_uv > 0.5 ? vec2(wpos.x + wpos.z * 0.7, -wpos.y + wpos.z * 0.3) / tile : UV / tile;
	vec4 a = texture(leaf_tex, uv);
	vec3 c = a.rgb * pow(vcol.rgb, vec3(2.2)) * tint;
	// Variation je Baum: Farbton und Helligkeit
	c *= mix(vec3(0.85, 0.95, 0.8), vec3(1.15, 1.05, 0.85), inst_var);
	c = mix(c, c * vec3(1.6, 1.1, 0.5), season * inst_var);
	ALBEDO = c * (1.0 - wet * 0.2);
	ROUGHNESS = mix(a.a, 0.35, wet);
	NORMAL_MAP = texture(leaf_nrm, uv).rgb;
	NORMAL_MAP_DEPTH = 1.2;
	BACKLIGHT = c * 0.55;
}
