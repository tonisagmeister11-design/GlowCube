// Painted road markings: colour from vertex colour, worn by noise, wet when raining.
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_back, diffuse_burley, specular_schlick_ggx, shadows_disabled;

global uniform float wetness;
uniform sampler2D macro_noise : filter_linear_mipmap, repeat_enable;

varying vec3 w_pos;

void vertex() {
	w_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
}

void fragment() {
	float wear = texture(macro_noise, w_pos.xz * 0.35).b;
	float big = texture(macro_noise, w_pos.xz * 0.02).r;
	vec3 c = COLOR.rgb * mix(0.55, 0.92, smoothstep(0.1, 0.8, wear * 0.6 + big * 0.5));
	c *= mix(1.0, 0.8, wetness);
	ALBEDO = c;
	ROUGHNESS = mix(0.6, 0.2, wetness);
}
