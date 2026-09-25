// Characters: vertex colour * per-instance tint (skin tone / clothing colour),
// optional subsurface scattering for skin, wetness darkening.
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_back, diffuse_burley, specular_schlick_ggx;

global uniform float wetness;

instance uniform vec3 tint : source_color = vec3(1.0);
uniform float roughness = 0.65;
uniform float sss = 0.0;
uniform float metallic = 0.0;
uniform float rim = 0.0;

void fragment() {
	vec3 c = COLOR.rgb * tint;
	ALBEDO = c * mix(1.0, 0.8, wetness * 0.5);
	ROUGHNESS = mix(roughness, roughness * 0.6, wetness);
	METALLIC = metallic;
	SSS_STRENGTH = sss;
	float r = pow(1.0 - clamp(dot(NORMAL, VIEW), 0.0, 1.0), 3.0);
	EMISSION = c * r * rim;
}
