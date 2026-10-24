// Flusswasser: zwei gegeneinander laufende Wellen-Normal-Maps, Stroemung
// entlang x, glatte spiegelnde Oberflaeche (Himmel / SSR spiegeln sich).
shader_type spatial;
render_mode cull_back, diffuse_lambert, specular_schlick_ggx;

uniform sampler2D wave_nrm : hint_normal, filter_linear_mipmap, repeat_enable;
uniform sampler2D noise_tex : filter_linear_mipmap, repeat_enable;
uniform vec3 deep_col : source_color = vec3(0.07, 0.17, 0.20);
uniform vec3 shallow_col : source_color = vec3(0.16, 0.30, 0.30);
uniform float flow = 0.35;
uniform float scale = 1.0;
uniform float wet = 0.0;
uniform float night = 0.0;

varying vec3 wpos;

void vertex() {
	wpos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
}

void fragment() {
	vec2 p = wpos.xz * scale;
	float t = TIME;
	vec3 n1 = texture(wave_nrm, p * 0.035 + vec2(t * 0.012 * flow, t * 0.004)).rgb;
	vec3 n2 = texture(wave_nrm, p * 0.083 + vec2(-t * 0.009, t * 0.017 * flow) + 0.37).rgb;
	vec3 n3 = texture(wave_nrm, p * 0.21 + vec2(t * 0.03 * flow, -t * 0.011) + 0.71).rgb;
	vec3 n = normalize((n1 * 2.0 - 1.0) + (n2 * 2.0 - 1.0) * 0.8 + (n3 * 2.0 - 1.0) * 0.5 * (1.0 + wet));
	float m = texture(noise_tex, wpos.xz * 0.004).r;
	ALBEDO = mix(deep_col, shallow_col, m * 0.6);
	ROUGHNESS = 0.03 + wet * 0.08;
	METALLIC = 0.0;
	SPECULAR = 0.55;
	NORMAL_MAP = n * 0.5 + 0.5;
	NORMAL_MAP_DEPTH = 0.55;
}
