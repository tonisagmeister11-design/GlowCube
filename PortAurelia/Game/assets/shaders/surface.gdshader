// Generic PBR surface: albedo (A = roughness) * colour * vertex tint, normal map,
// world or mesh UVs, macro variation, wetness with puddles on horizontal surfaces.
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_back, diffuse_burley, specular_schlick_ggx;

global uniform float wetness;

uniform sampler2D albedo_tex : source_color, filter_linear_mipmap_anisotropic, repeat_enable, hint_default_white;
uniform sampler2D normal_tex : hint_normal, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D macro_noise : filter_linear_mipmap, repeat_enable, hint_default_white;
uniform vec3 color : source_color = vec3(1.0);
uniform float uv_scale = 0.25;
uniform bool world_uv = true;
uniform bool use_vertex_tint = false;
uniform float rough_mul = 1.0;
uniform float rough_add = 0.0;
uniform float metallic = 0.0;
uniform float normal_strength = 1.0;
uniform float macro_strength = 0.25;
uniform float puddles = 1.0;
uniform bool double_sided = false;

varying vec3 w_pos;
varying vec3 w_nrm;

void vertex() {
	w_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	w_nrm = normalize((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz);
}

void fragment() {
	vec2 uv;
	if (world_uv) {
		// planar projection picked by the dominant normal axis
		vec3 an = abs(w_nrm);
		if (an.y > 0.6) { uv = w_pos.xz; }
		else if (an.x > an.z) { uv = vec2(w_pos.z, w_pos.y); }
		else { uv = vec2(w_pos.x, w_pos.y); }
	} else {
		uv = UV;
	}
	vec4 t = texture(albedo_tex, uv * uv_scale);
	vec3 alb = t.rgb * color;
	if (use_vertex_tint) { alb *= COLOR.rgb; }
	float m = texture(macro_noise, w_pos.xz * 0.013).r;
	alb *= mix(1.0 - macro_strength, 1.0 + macro_strength * 0.5, m);
	float rough = clamp(t.a * rough_mul + rough_add, 0.02, 1.0);

	// wetness + puddles on flat ground
	float flatness = smoothstep(0.85, 0.98, w_nrm.y);
	float pud = smoothstep(0.55, 0.7, texture(macro_noise, w_pos.xz * 0.05).g) * flatness * puddles;
	float wet = wetness;
	alb *= mix(1.0, 0.68, wet);
	rough = mix(rough, 0.18, wet * 0.8);
	float water = clamp(pud * wet * 1.6, 0.0, 1.0);
	alb = mix(alb, alb * 0.5, water);
	rough = mix(rough, 0.02, water);

	ALBEDO = alb;
	ROUGHNESS = rough;
	METALLIC = metallic;
	NORMAL_MAP = mix(texture(normal_tex, uv * uv_scale).rgb, vec3(0.5, 0.5, 1.0), water);
	NORMAL_MAP_DEPTH = normal_strength;
}
