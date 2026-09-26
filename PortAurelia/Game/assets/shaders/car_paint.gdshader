// Vehicle body paint: per-instance colour, metallic flakes, clear coat,
// dirt and damage scratches, rain beading.
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_back, diffuse_burley, specular_schlick_ggx;

global uniform float wetness;

instance uniform vec3 paint : source_color = vec3(0.6, 0.05, 0.05);
instance uniform float dirt = 0.1;
instance uniform float damage = 0.0;
instance uniform float metallic_paint = 0.6;
uniform sampler2D noise_tex : filter_linear_mipmap, repeat_enable;
uniform bool use_vertex_color = false;

varying vec3 l_pos;
varying vec2 seam_mask;

void vertex() {
	l_pos = VERTEX;
	seam_mask = UV2;
}

// panel gap: UV holds alternating signed distances to the nearest shut lines
float gap_line(float d) {
	float w = fwidth(d);
	return 1.0 - smoothstep(0.0016, 0.0016 + max(w, 1e-5) * 1.2, abs(d));
}

void fragment() {
	vec3 base = paint;
	if (use_vertex_color) { base = COLOR.rgb; }
	float flake = texture(noise_tex, l_pos.xz * 4.0 + l_pos.y).b;
	float d = texture(noise_tex, l_pos.xz * 0.6 + l_pos.y * 0.3).r;
	float low = 1.0 - smoothstep(0.1, 0.9, l_pos.y);
	float dirt_amt = clamp(dirt * (0.4 + low) * smoothstep(0.35, 0.75, d), 0.0, 1.0);
	float scratch = step(1.0 - damage * 0.6, texture(noise_tex, l_pos.xz * 2.3 + l_pos.y * 1.7).g) * damage;
	vec3 c = base * (0.92 + flake * 0.16 * metallic_paint);
	c = mix(c, vec3(0.34, 0.3, 0.25), dirt_amt);
	c = mix(c, vec3(0.55, 0.55, 0.56), scratch);
	float gap = 0.0;
	float edge = 0.0;
	float mu = step(0.5, seam_mask.x);
	float mv = step(0.5, seam_mask.y);
	gap = max(gap_line(UV.x) * mu, gap_line(UV.y) * mv);
	// soft rolled edge next to the gap
	edge = max((1.0 - smoothstep(0.0016, 0.007, abs(UV.x))) * mu, (1.0 - smoothstep(0.0016, 0.007, abs(UV.y))) * mv);
	// orange peel in the clear coat
	float peel = texture(noise_tex, l_pos.xz * 9.0 + l_pos.yx * 7.0).r;
	c *= 1.0 - edge * 0.25;
	c = mix(c, vec3(0.012), gap);
	ALBEDO = c;
	METALLIC = mix(mix(metallic_paint * 0.7, 0.2, dirt_amt + scratch), 0.0, gap);
	ROUGHNESS = mix(mix(0.26, 0.85, dirt_amt + scratch * 0.5) * mix(1.0, 0.6, wetness), 0.9, gap);
	CLEARCOAT = (1.0 - dirt_amt) * (1.0 - gap);
	CLEARCOAT_ROUGHNESS = mix(0.03 + peel * 0.05, 0.4, dirt_amt);
	NORMAL_MAP = vec3(0.5 + (peel - 0.5) * 0.04, 0.5 + (texture(noise_tex, l_pos.zy * 9.0).g - 0.5) * 0.04, 1.0);
}
