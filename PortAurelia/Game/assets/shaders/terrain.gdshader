// Terrain splat shader: vertex colour = weights (R grass, G dirt, B sand, A rock).
// World-space UVs with macro variation to hide tiling. Wet sand near the waterline.
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_back, diffuse_burley, specular_schlick_ggx;

global uniform float wetness;

uniform sampler2D grass_col : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D grass_nrm : hint_normal, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D dirt_col : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D dirt_nrm : hint_normal, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D sand_col : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D sand_nrm : hint_normal, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D rock_col : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D rock_nrm : hint_normal, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D macro_noise : filter_linear_mipmap, repeat_enable;
uniform float scale_grass = 0.18;
uniform float scale_dirt = 0.15;
uniform float scale_sand = 0.12;
uniform float scale_rock = 0.08;
uniform float sea_level = -1.5;

varying vec3 w_pos;
varying vec4 w_splat;

void vertex() {
	w_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	w_splat = COLOR;
}

void fragment() {
	vec2 p = w_pos.xz;
	float macro = texture(macro_noise, p * 0.004).r;
	float macro2 = texture(macro_noise, p * 0.0011 + 0.3).g;
	vec4 w = w_splat;
	// break up the grass with dry patches
	w.g += clamp((macro2 - 0.55) * 1.5, 0.0, 0.5) * w.r;
	// height-based blend sharpening
	w = max(w, vec4(0.0));
	w /= max(w.r + w.g + w.b + w.a, 0.001);

	vec4 g = texture(grass_col, p * scale_grass) * 0.6 + texture(grass_col, p * scale_grass * 0.23) * 0.4;
	vec4 d = texture(dirt_col, p * scale_dirt);
	vec4 s = texture(sand_col, p * scale_sand);
	vec4 r = texture(rock_col, p * scale_rock);
	vec3 gn = texture(grass_nrm, p * scale_grass).rgb;
	vec3 dn = texture(dirt_nrm, p * scale_dirt).rgb;
	vec3 sn = texture(sand_nrm, p * scale_sand).rgb;
	vec3 rn = texture(rock_nrm, p * scale_rock).rgb;

	vec4 c = g * w.r + d * w.g + s * w.b + r * w.a;
	vec3 n = gn * w.r + dn * w.g + sn * w.b + rn * w.a;
	c.rgb *= mix(0.82, 1.12, macro);

	// wet sand band near the sea
	float shore = 1.0 - smoothstep(sea_level, sea_level + 1.2, w_pos.y);
	float wet = max(shore * w.b, wetness * 0.8);
	c.rgb *= mix(1.0, 0.62, wet);
	float rough = mix(c.a, 0.25, wet);

	ALBEDO = c.rgb;
	ROUGHNESS = rough;
	NORMAL_MAP = n;
	NORMAL_MAP_DEPTH = 1.0;
}
