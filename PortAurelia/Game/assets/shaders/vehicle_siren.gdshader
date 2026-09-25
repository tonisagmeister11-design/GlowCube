// Emergency light bar: red and blue halves flash alternately while active.
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_back, diffuse_burley, specular_schlick_ggx;

instance uniform float active = 0.0;
varying vec3 l_pos;

void vertex() {
	l_pos = VERTEX;
}

void fragment() {
	vec3 c = COLOR.rgb;
	float phase = step(0.5, fract(TIME * 2.5));
	float side = step(0.0, l_pos.x);
	float on = active * mix(phase, 1.0 - phase, side) * step(0.5, fract(TIME * 10.0) + 0.3);
	ALBEDO = c * 0.3;
	ROUGHNESS = 0.1;
	EMISSION = c * on * 10.0;
}
