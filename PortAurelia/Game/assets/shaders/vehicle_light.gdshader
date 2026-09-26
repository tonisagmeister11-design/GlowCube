// Vehicle lamps: per-instance colour and on/off intensity (head, brake, reverse, turn).
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_back, diffuse_burley, specular_schlick_ggx;

instance uniform vec3 lamp_color : source_color = vec3(1.0, 0.95, 0.85);
instance uniform float intensity = 0.0;

void fragment() {
	vec3 c = lamp_color * COLOR.rgb;
	// off: tinted lens over a chrome reflector; on: bright emission
	ALBEDO = mix(vec3(0.6), c, 0.75) * 0.42;
	ROUGHNESS = 0.06;
	METALLIC = 0.55;
	CLEARCOAT = 1.0;
	CLEARCOAT_ROUGHNESS = 0.02;
	EMISSION = c * intensity * 6.0;
}
