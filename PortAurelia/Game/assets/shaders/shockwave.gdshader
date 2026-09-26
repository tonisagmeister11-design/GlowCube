// Ground shockwave: a bright dusty ring racing outwards and fading (quad on the ground, UV 0..1).
shader_type spatial;
render_mode blend_mix, unshaded, cull_disabled, depth_draw_never, shadows_disabled;

instance uniform float age = 0.0;

void fragment() {
	vec2 c = UV * 2.0 - 1.0;
	float r = length(c);
	float ring = smoothstep(0.72, 0.92, r) * (1.0 - smoothstep(0.92, 1.0, r));
	float inner = (1.0 - smoothstep(0.0, 0.9, r)) * 0.25;
	ALBEDO = mix(vec3(0.75, 0.68, 0.58), vec3(1.0, 0.8, 0.5), (1.0 - age) * 0.6);
	ALPHA = clamp((ring * 0.85 + inner * (1.0 - age)) * (1.0 - age) * (1.0 - age), 0.0, 1.0);
}
