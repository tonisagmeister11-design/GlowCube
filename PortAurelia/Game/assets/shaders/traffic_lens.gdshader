// Traffic light lens. Vertex colour identifies the lens (red / yellow / green);
// INSTANCE_CUSTOM.r carries the head's state (0 red, 1 yellow, 2 green).
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_back, diffuse_burley, specular_schlick_ggx;

varying float state;

void vertex() {
	state = INSTANCE_CUSTOM.r;
}

void fragment() {
	float lens = COLOR.g > 0.8 ? 2.0 : (COLOR.g > 0.4 ? 1.0 : 0.0);
	float on = 1.0 - step(0.5, abs(lens - state));
	vec3 c = COLOR.rgb;
	ALBEDO = c * 0.15;
	ROUGHNESS = 0.2;
	EMISSION = c * on * 6.0 + c * 0.03;
}
