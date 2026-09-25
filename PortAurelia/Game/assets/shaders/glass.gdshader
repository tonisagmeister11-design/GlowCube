// Tinted, reflective glass (vehicle windows, glazing). Broken state shows cracks.
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_back, diffuse_burley, specular_schlick_ggx;

global uniform float wetness;

uniform vec3 tint : source_color = vec3(0.08, 0.1, 0.12);
uniform float opacity = 0.72;
instance uniform float broken = 0.0;
uniform sampler2D noise_tex : filter_linear_mipmap, repeat_enable;

void fragment() {
	float fres = pow(1.0 - clamp(dot(NORMAL, VIEW), 0.0, 1.0), 3.0);
	float crack = 0.0;
	if (broken > 0.5) {
		float n = texture(noise_tex, UV * 6.0).b;
		crack = step(0.82, n);
	}
	ALBEDO = mix(tint, vec3(0.85), crack);
	ROUGHNESS = mix(0.02, 0.6, crack) + wetness * 0.05;
	METALLIC = 0.3;
	SPECULAR = 0.7;
	ALPHA = clamp(opacity + fres * 0.25 + crack * 0.4, 0.0, 1.0);
}
