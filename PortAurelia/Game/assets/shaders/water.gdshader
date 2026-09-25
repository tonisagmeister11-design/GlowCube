// Ocean / canal / lake water. A grid mesh follows the camera; waves are summed
// Gerstner waves near the camera with scrolling normal maps, depth based colour,
// shoreline foam, refraction and sky reflections (via roughness/specular).
shader_type spatial;
render_mode blend_mix, depth_draw_always, cull_back, diffuse_burley, specular_schlick_ggx;

global uniform float wetness;
global uniform vec2 wind;
global uniform float night;

uniform sampler2D normal_a : hint_normal, filter_linear_mipmap, repeat_enable;
uniform sampler2D normal_b : hint_normal, filter_linear_mipmap, repeat_enable;
uniform sampler2D depth_tex : hint_depth_texture, filter_linear_mipmap;
uniform sampler2D screen_tex : hint_screen_texture, filter_linear_mipmap;
uniform sampler2D foam_noise : filter_linear_mipmap, repeat_enable;
uniform vec3 shallow_color : source_color = vec3(0.12, 0.42, 0.44);
uniform vec3 deep_color : source_color = vec3(0.02, 0.10, 0.16);
uniform float wave_height = 0.35;
uniform float storm = 0.0;
uniform float depth_fade = 6.0;
uniform float refraction = 0.03;

varying vec3 w_pos;
varying float v_wave;

vec3 gerstner(vec2 p, vec2 dir, float amp, float wl, float speed, inout vec3 tangent, inout vec3 binormal) {
	float k = 6.28318 / wl;
	float c = sqrt(9.8 / k) * speed;
	vec2 d = normalize(dir);
	float f = k * (dot(d, p) - c * TIME);
	float a = amp;
	float steep = 0.5;
	tangent += vec3(-d.x * d.x * steep * sin(f), d.x * steep * cos(f), -d.x * d.y * steep * sin(f)) * k * a;
	binormal += vec3(-d.x * d.y * steep * sin(f), d.y * steep * cos(f), -d.y * d.y * steep * sin(f)) * k * a;
	return vec3(d.x * a * cos(f) * steep, a * sin(f), d.y * a * cos(f) * steep);
}

void vertex() {
	vec3 wp = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	float dist = length(wp.xz - CAMERA_POSITION_WORLD.xz);
	float fade = 1.0 - smoothstep(150.0, 600.0, dist);
	float amp = wave_height * (1.0 + storm * 2.5);
	vec3 t = vec3(1.0, 0.0, 0.0);
	vec3 b = vec3(0.0, 0.0, 1.0);
	vec3 off = vec3(0.0);
	off += gerstner(wp.xz, vec2(1.0, 0.6), amp * 0.5, 38.0, 1.0, t, b);
	off += gerstner(wp.xz, vec2(-0.4, 1.0), amp * 0.3, 21.0, 1.0, t, b);
	off += gerstner(wp.xz, vec2(0.7, -0.3), amp * 0.2, 11.0, 1.0, t, b);
	off *= fade;
	VERTEX += off;
	v_wave = off.y;
	NORMAL = normalize(mix(vec3(0.0, 1.0, 0.0), normalize(cross(b, t)), fade));
	w_pos = wp + off;
}

void fragment() {
	vec2 p = w_pos.xz;
	float spd = 0.02 * (1.0 + storm);
	vec3 n1 = texture(normal_a, p * 0.05 + vec2(TIME * spd, TIME * spd * 0.6)).rgb;
	vec3 n2 = texture(normal_b, p * 0.021 - vec2(TIME * spd * 0.7, -TIME * spd * 0.4)).rgb;
	vec3 n = normalize(mix(n1, n2, 0.5) * 2.0 - 1.0);
	NORMAL_MAP = n * 0.5 + 0.5;
	NORMAL_MAP_DEPTH = 0.6 + storm;

	// depth of the water column
	float depth = texture(depth_tex, SCREEN_UV).r;
	vec4 upos = INV_PROJECTION_MATRIX * vec4(SCREEN_UV * 2.0 - 1.0, depth, 1.0);
	upos.xyz /= upos.w;
	float scene_dist = length(upos.xyz);
	float water_dist = length(VERTEX);
	float thickness = max(scene_dist - water_dist, 0.0);
	float t = clamp(thickness / depth_fade, 0.0, 1.0);

	vec2 ruv = SCREEN_UV + n.xy * refraction * clamp(thickness * 0.3, 0.0, 1.0);
	vec3 refr = texture(screen_tex, ruv).rgb;
	vec3 water_c = mix(shallow_color, deep_color, t);
	water_c *= mix(1.0, 0.25, night);
	vec3 col = mix(refr * shallow_color * 1.6, water_c, clamp(t * 1.3, 0.0, 1.0));

	// shoreline & crest foam
	float fn = texture(foam_noise, p * 0.2 + TIME * 0.02).b;
	float shore = (1.0 - smoothstep(0.0, 1.2, thickness)) * step(0.35, fn);
	float crest = smoothstep(0.25, 0.6, v_wave * (1.0 + storm)) * step(0.5, fn);
	float foam = clamp(shore + crest * (0.3 + storm), 0.0, 1.0);
	col = mix(col, vec3(0.9, 0.93, 0.95) * mix(1.0, 0.2, night), foam);

	ALBEDO = col;
	ROUGHNESS = mix(0.02, 0.6, foam) + storm * 0.05;
	METALLIC = 0.0;
	SPECULAR = 0.6;
	ALPHA = clamp(0.35 + t * 1.2 + foam, 0.0, 1.0);
}
