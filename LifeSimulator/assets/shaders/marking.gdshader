// Fahrbahnmarkierungen: Farbe aus COLOR (sRGB), Abnutzung per Rauschen
// (verworfene Pixel), leichte Tiefenverschiebung zur Kamera gegen Z-Fighting.
shader_type spatial;
render_mode cull_back, diffuse_burley, specular_schlick_ggx, skip_vertex_transform;

uniform sampler2D noise_tex : filter_linear_mipmap, repeat_enable;
uniform float wear = 0.22;
uniform float depth_bias = 0.0006;
uniform float wet = 0.0;
uniform float night = 0.0;

varying vec3 wpos;
varying vec4 vcol;

void vertex() {
	wpos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	vcol = COLOR;
	VERTEX = (MODELVIEW_MATRIX * vec4(VERTEX, 1.0)).xyz;
	VERTEX *= 1.0 - depth_bias;
	NORMAL = normalize((MODELVIEW_MATRIX * vec4(NORMAL, 0.0)).xyz);
}

void fragment() {
	float n = texture(noise_tex, wpos.xz * 0.9).a * 0.6 + texture(noise_tex, wpos.xz * 0.11).g * 0.4;
	float w = wear * (1.0 - vcol.a * 0.0);
	if (n < w) {
		discard;
	}
	vec3 c = pow(vcol.rgb, vec3(2.2));
	c *= 0.82 + 0.18 * texture(noise_tex, wpos.xz * 2.3).b;
	ALBEDO = c * (1.0 - wet * 0.2);
	ROUGHNESS = mix(0.55, 0.2, wet);
	// Retroreflexion nachts leicht andeuten
	EMISSION = c * 0.015 * night;
}
