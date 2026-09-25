// Building facade shader.
// UV  = (metres along this facade side, metres above the building base)
// UV2 = (side length in metres, floor height in metres) - windows are evenly
//       distributed and centred on every side.
// COLOR.rgb = building tint, COLOR.a = per-building random seed.
// Windows use interior mapping (fake rooms behind the glass) and light up at night.
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_back, diffuse_burley, specular_schlick_ggx;

global uniform float night;
global uniform float wetness;
global uniform float city_lights;

// 0 punched windows, 1 ribbon windows, 2 curtain wall glass, 3 industrial, 4 house, 5 shopfront
uniform int style = 0;
uniform sampler2D wall_tex : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D wall_nrm : hint_normal, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D detail_noise : filter_linear_mipmap, repeat_enable;
uniform vec2 wall_scale = vec2(0.25, 0.25);
uniform vec3 wall_color : source_color = vec3(1.0);
uniform float tint_strength = 1.0;
uniform float wall_rough_mul = 1.0;
uniform float default_floor_h = 3.4;
uniform float win_spacing = 3.0;
uniform float win_width = 0.55;
uniform float win_height = 0.55;
uniform float win_sill = 0.28;
uniform vec3 glass_color : source_color = vec3(0.09, 0.12, 0.16);
uniform vec3 frame_color : source_color = vec3(0.22, 0.22, 0.23);
uniform vec3 spandrel_color : source_color = vec3(0.18, 0.2, 0.22);
uniform float glass_rough = 0.06;
uniform float glass_metal = 0.55;
uniform float lit_ratio = 0.38;
uniform vec3 lit_color : source_color = vec3(1.0, 0.82, 0.58);
uniform float lit_energy = 2.2;
uniform float room_depth = 4.0;
uniform bool interior_mapping = true;
uniform float normal_strength = 0.8;

varying vec3 w_pos;
varying vec3 w_nrm;
varying vec4 v_col;
varying vec2 v_par;

float hash21(vec2 p) {
	p = fract(p * vec2(123.34, 456.21));
	p += dot(p, p + 45.32);
	return fract(p.x * p.y);
}

void vertex() {
	w_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	w_nrm = normalize((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz);
	v_col = COLOR;
	v_par = UV2;
}

// returns room colour seen through the window cell and the face id
vec3 room_color(vec2 cell_uv, vec2 cell_size, vec3 view_t, float seed, float lit, out float face) {
	// cell_uv 0..1 inside the room opening, ray from facade into the room
	vec3 p = vec3(cell_uv.x * cell_size.x, cell_uv.y * cell_size.y, 0.0);
	vec3 v = view_t;
	v.z = max(v.z, 0.02);
	float tx = v.x > 0.0 ? (cell_size.x - p.x) / v.x : -p.x / min(v.x, -0.0001);
	float ty = v.y > 0.0 ? (cell_size.y - p.y) / v.y : -p.y / min(v.y, -0.0001);
	float tz = room_depth / v.z;
	float t = min(min(tx, ty), tz);
	vec3 hit = p + v * t;
	float r = hash21(vec2(seed * 91.7, seed * 13.1));
	vec3 wallc = mix(vec3(0.78, 0.74, 0.68), vec3(0.62, 0.68, 0.72), r);
	vec3 floorc = mix(vec3(0.28, 0.22, 0.18), vec3(0.35, 0.34, 0.33), fract(r * 7.3));
	vec3 ceilc = vec3(0.86);
	vec3 c;
	if (t == tz) {
		face = 0.0;
		c = wallc * 0.85;
		// furniture silhouette on the back wall
		float fx = hit.x / cell_size.x;
		float fy = hit.y / cell_size.y;
		float shelf = step(0.15, fx) * step(fx, 0.15 + 0.4 * fract(r * 3.1)) * step(fy, 0.35 + 0.2 * r);
		c = mix(c, vec3(0.25, 0.2, 0.17), shelf);
	} else if (t == ty) {
		face = v.y > 0.0 ? 1.0 : 2.0;
		c = v.y > 0.0 ? ceilc : floorc;
	} else {
		face = 3.0;
		c = wallc * 0.7;
	}
	// depth shading
	c *= mix(1.0, 0.55, clamp(t / (room_depth * 1.5), 0.0, 1.0));
	return c;
}

void fragment() {
	float seed = v_col.a;
	vec3 tint = mix(vec3(1.0), v_col.rgb, tint_strength);
	float fh = v_par.y > 0.5 ? v_par.y : default_floor_h;
	float side_len = max(v_par.x, 0.1);
	vec2 wuv = UV;

	// ------------------------------------------------ wall material
	vec4 wt = texture(wall_tex, wuv * wall_scale);
	vec3 wall = wt.rgb * wall_color * tint;
	float wall_rough = clamp(wt.a * wall_rough_mul, 0.05, 1.0);
	vec3 wn = texture(wall_nrm, wuv * wall_scale).rgb;
	// large scale dirt / weathering from the ground up
	float grime = texture(detail_noise, wuv * vec2(0.03, 0.08) + seed).r;
	float ground_dirt = 1.0 - smoothstep(0.0, 2.5, wuv.y);
	wall *= mix(1.0, 0.82, clamp(grime * 0.6 + ground_dirt * 0.5, 0.0, 1.0));

	// ------------------------------------------------ window grid
	float spacing = win_spacing;
	float ncol = max(1.0, floor(side_len / spacing + 0.5));
	float pitch = side_len / ncol;
	float cu = wuv.x / pitch;
	float col_id = floor(cu);
	float fu = fract(cu);
	float fvf = wuv.y / fh;
	float floor_id = floor(fvf);
	float fv = fract(fvf);
	float edge_margin = 0.35 / max(side_len, 1.0);

	float ww = win_width;
	float wh = win_height;
	float sill = win_sill;
	if (style == 1) { ww = 1.0; wh = 0.46; sill = 0.36; }
	if (style == 2) { ww = 0.96; wh = 0.8; sill = 0.1; }
	if (style == 3) { ww = 0.35; wh = 0.28; sill = 0.62; }
	if (style == 5) { ww = 0.94; wh = 0.9; sill = 0.04; }

	float in_x = step(abs(fu - 0.5), ww * 0.5);
	float in_y = step(sill, fv) * step(fv, sill + wh);
	// keep windows off the ground-floor slab edge for houses
	float win = in_x * in_y;
	if (style == 1) { win = in_y; }
	if (style == 3 && floor_id > 0.5) { win *= 0.0; }
	// frame (thin border around each pane)
	float fx_d = abs(fu - 0.5) - ww * 0.5;
	float fy_d = min(fv - sill, sill + wh - fv);
	float frame = win * (1.0 - step(0.035, -fx_d) * step(0.06 / fh * 3.0, fy_d));
	if (style == 1 || style == 2) {
		// mullions for ribbon / curtain walls
		float m = step(fract(wuv.x / 1.5 + 0.5), 0.04);
		frame = max(frame, win * m);
	}

	// ------------------------------------------------ glass & rooms
	vec3 N = normalize(w_nrm);
	vec3 T = normalize(cross(N, vec3(0.0, 1.0, 0.0)));
	vec3 V = normalize(w_pos - CAMERA_POSITION_WORLD);
	vec3 vt = vec3(dot(V, T), dot(V, vec3(0.0, 1.0, 0.0)), dot(V, -N));

	float room_seed = hash21(vec2(col_id + seed * 331.0, floor_id + seed * 77.0));
	float lit_n = step(room_seed, lit_ratio) * clamp(city_lights, 0.0, 1.0);
	// some rooms flicker on later in the evening
	lit_n *= step(fract(room_seed * 13.7), clamp(night * 1.4, 0.0, 1.0));
	vec3 room = vec3(0.05);
	float face = 0.0;
	vec2 cell_size = vec2(pitch * ww, fh * wh);
	if (style == 2 || style == 1) { cell_size = vec2(pitch, fh); }
	if (interior_mapping && win > 0.5) {
		vec2 cuv = vec2((fu - (0.5 - ww * 0.5)) / max(ww, 0.01), (fv - sill) / max(wh, 0.01));
		if (style == 1 || style == 2) { cuv = vec2(fu, fv); }
		room = room_color(clamp(cuv, 0.0, 1.0), cell_size, vt, room_seed, lit_n, face);
	}
	// blinds / curtains on some windows
	float blind = step(0.72, fract(room_seed * 5.1));
	float blind_level = mix(0.3, 1.0, fract(room_seed * 9.3));
	float cuv_y = (fv - sill) / max(wh, 0.01);
	float blinds_here = blind * step(1.0 - blind_level, cuv_y);
	room = mix(room, vec3(0.75, 0.72, 0.66), blinds_here);

	vec3 glass = glass_color;
	float fres = pow(1.0 - clamp(dot(-V, N), 0.0, 1.0), 4.0);
	// day: rooms are dim behind reflective glass, night: lit rooms glow
	vec3 day_room = room * 0.22;
	vec3 glass_albedo = mix(glass + day_room, glass * 0.6, fres);

	vec3 albedo = wall;
	float rough = wall_rough;
	float metal = 0.0;
	vec3 emission = vec3(0.0);

	if (style == 2) {
		// curtain wall: spandrel bands between floors
		float spandrel = 1.0 - in_y;
		albedo = mix(glass_albedo, spandrel_color * tint, spandrel);
		rough = mix(glass_rough, 0.35, spandrel);
		metal = mix(glass_metal, 0.6, spandrel);
		albedo = mix(albedo, frame_color, frame);
	} else if (win > 0.5) {
		albedo = mix(glass_albedo, frame_color, frame);
		rough = mix(glass_rough, 0.5, frame);
		metal = mix(glass_metal, 0.2, frame);
	}
	if (win > 0.5 && frame < 0.5) {
		vec3 lc = lit_color * mix(0.75, 1.25, fract(room_seed * 3.7));
		if (fract(room_seed * 17.0) > 0.85) { lc = vec3(0.7, 0.85, 1.0); }
		float light_face = face == 1.0 ? 1.3 : 1.0;
		emission = room * lc * lit_n * lit_energy * light_face * (1.0 - blinds_here * 0.4);
		if (style == 5) {
			emission = (room * 0.6 + 0.4) * lit_color * clamp(city_lights, 0.2, 1.0) * lit_energy * 0.6;
		}
	}

	// ------------------------------------------------ wetness
	float wet = wetness * (0.6 + 0.4 * N.y);
	albedo *= mix(1.0, 0.72, wet * (1.0 - win));
	rough = mix(rough, rough * 0.35, wet);

	ALBEDO = albedo;
	ROUGHNESS = rough;
	METALLIC = metal;
	SPECULAR = 0.5;
	EMISSION = emission;
	if (win < 0.5) {
		NORMAL_MAP = wn;
		NORMAL_MAP_DEPTH = normal_strength;
	}
}
