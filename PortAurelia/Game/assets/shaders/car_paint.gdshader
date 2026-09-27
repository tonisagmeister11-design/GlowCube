// Vehicle body paint: per-instance colour and livery (two-tone, racing stripes, side stripes,
// fades, sponsor / company logos and race numbers from a decal atlas), four paint finishes
// (metallic with sparkling flakes, pearl, matte, candy), clear coat, dirt, damage scratches.
// Liveries are computed in model space (x = right, y = up, -z = front), so they need no UVs.
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_back, diffuse_burley, specular_schlick_ggx;

global uniform float wetness;

instance uniform vec3 paint : source_color = vec3(0.6, 0.05, 0.05);
instance uniform float dirt = 0.1;
instance uniform float damage = 0.0;
instance uniform float metallic_paint = 0.6;
// livery: 0 plain, 1 two-tone roof, 2 twin stripes, 3 side stripe, 4 race, 5 company, 6 fade, 7 sticker
instance uniform vec3 paint2 : source_color = vec3(0.95, 0.95, 0.95);
instance uniform float livery = 0.0;
// finish: 0 metallic, 1 pearl, 2 matte, 3 candy
instance uniform float finish = 0.0;
// length, width, hood height, roof height
instance uniform vec4 dims = vec4(4.5, 1.8, 0.9, 1.4);
// sponsor cell, number cell, second sponsor cell, door centre z
instance uniform vec4 decals = vec4(-1.0, -1.0, -1.0, 0.0);
uniform sampler2D noise_tex : filter_linear_mipmap, repeat_enable;
uniform sampler2D decal_atlas : source_color, filter_linear_mipmap_anisotropic, repeat_disable;
uniform bool use_vertex_color = false;

varying vec3 l_pos;
varying vec3 l_nrm;
varying vec2 seam_mask;

void vertex() {
	l_pos = VERTEX;
	l_nrm = NORMAL;
	seam_mask = UV2;
}

float hash31(vec3 p) {
	p = fract(p * 0.1031);
	p += dot(p, p.zyx + 31.32);
	return fract((p.x + p.y) * p.z);
}

// panel gap: UV holds alternating signed distances to the nearest shut lines
float gap_line(float d) {
	float w = fwidth(d);
	return 1.0 - smoothstep(0.0016, 0.0016 + max(w, 1e-5) * 1.2, abs(d));
}

vec4 decal(float id, vec2 uv) {
	if (id < 0.0 || uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
		return vec4(0.0);
	}
	vec2 cell = vec2(mod(id, 4.0), floor(id / 4.0));
	return texture(decal_atlas, (cell + clamp(uv, vec2(0.004), vec2(0.996))) / vec2(4.0, 8.0));
}

// decal on the body side centred at (zc, yc), w wide; readable from both sides
vec4 side_decal(float id, float zc, float yc, float w) {
	float sgn = l_pos.x >= 0.0 ? 1.0 : -1.0;
	float h = w * 0.5;
	vec2 uv = vec2(0.5 - sgn * (l_pos.z - zc) / w, 0.5 - (l_pos.y - yc) / h);
	return decal(id, uv) * smoothstep(0.55, 0.75, abs(l_nrm.x));
}

// decal on top surfaces (hood / roof / trunk), readable from the front
vec4 top_decal(float id, float zc, float w) {
	float h = w * 0.5;
	vec2 uv = vec2(0.5 - l_pos.x / w, 0.5 - (l_pos.z - zc) / h);
	return decal(id, uv) * smoothstep(0.45, 0.7, l_nrm.y);
}

void fragment() {
	vec3 base = paint;
	float L = dims.x;
	float W = dims.y;
	float hood = dims.z;
	int lv = int(livery + 0.5);
	int fin = int(finish + 0.5);
	float side = abs(l_nrm.x);
	float ax = abs(l_pos.x);
	float dist = length(VERTEX);
	vec4 dec = vec4(0.0);
	if (use_vertex_color) {
		base = COLOR.rgb;
	} else if (lv > 0) {
		if (lv == 1) {
			// two-tone: roof, pillars and mirror caps in the second colour
			base = mix(base, paint2, smoothstep(hood * 1.02, hood * 1.07, l_pos.y));
		} else if (lv == 2 || lv == 4) {
			// twin racing stripes over nose, hood, roof and tail
			float sw = W * 0.085;
			float g = W * 0.035;
			float s = smoothstep(g, g + 0.004, ax) * (1.0 - smoothstep(g + sw, g + sw + 0.004, ax));
			float pin = smoothstep(g + sw + 0.025, g + sw + 0.029, ax) * (1.0 - smoothstep(g + sw + 0.041, g + sw + 0.045, ax));
			s = max(s, pin) * (1.0 - smoothstep(0.62, 0.8, side));
			base = mix(base, paint2, s);
		} else if (lv == 3) {
			// "hockey stick" side stripe along the flanks with a pinstripe
			float y0 = hood * 0.5;
			float zt = smoothstep(-L * 0.42, -L * 0.36, l_pos.z) * (1.0 - smoothstep(L * 0.3, L * 0.36, l_pos.z));
			float kick = smoothstep(L * 0.22, L * 0.3, l_pos.z) * (1.0 - smoothstep(L * 0.3, L * 0.36, l_pos.z));
			float band = smoothstep(y0, y0 + 0.004, l_pos.y) * (1.0 - smoothstep(y0 + 0.07 + kick * 0.2, y0 + 0.074 + kick * 0.2, l_pos.y));
			float pin = smoothstep(y0 + 0.1, y0 + 0.104, l_pos.y) * (1.0 - smoothstep(y0 + 0.115, y0 + 0.119, l_pos.y));
			base = mix(base, paint2, max(band, pin) * zt * smoothstep(0.55, 0.75, side));
		} else if (lv == 5 && paint2.r >= 0.0) {
			// company car: contrasting lower body
			base = mix(base, paint2, 1.0 - smoothstep(hood * 0.4, hood * 0.43, l_pos.y));
		} else if (lv == 6) {
			// colour fade from nose to tail, broken up with a speckled transition
			float n = texture(noise_tex, l_pos.zy * 3.0).r;
			float t = smoothstep(-L * 0.15, L * 0.35, l_pos.z + (n - 0.5) * 0.35);
			base = mix(base, paint2, t);
		}
	}
	if (lv > 0) {
		// logos and numbers (also on fixed liveries: bus adverts, delivery vans)
		float zc = decals.w;
		if (lv == 4) {
			dec = side_decal(decals.y, zc - 0.32, hood * 0.58, min(0.56, L * 0.13));
			vec4 s2 = side_decal(decals.x, zc + 0.42, hood * 0.5, min(0.8, L * 0.18));
			dec = mix(dec, s2, s2.a);
			vec4 h1 = top_decal(decals.z, -L * 0.36, min(0.9, W * 0.5));
			dec = mix(dec, h1, h1.a);
		} else if (lv == 5) {
			dec = side_decal(decals.x, zc, hood * 0.72, min(1.8, L * 0.34));
			vec4 h1 = top_decal(decals.x, -L * 0.37, min(0.9, W * 0.5));
			dec = mix(dec, h1, h1.a);
		} else if (lv == 7) {
			dec = side_decal(decals.x, L * 0.36, hood * 0.8, min(0.42, L * 0.09));
		}
	}
	// metallic flakes: tiny random facets that glint up close
	float fscale = fin == 3 ? 1100.0 : 800.0;
	vec3 fc = floor(l_pos * fscale);
	vec2 frnd = vec2(hash31(fc), hash31(fc + 17.3)) - 0.5;
	// fade the flakes out once a flake gets smaller than a pixel (no sparkle noise / shimmer)
	float fpx = length(fwidth(l_pos)) * fscale;
	float fnear = (1.0 - smoothstep(0.6, 1.6, fpx)) * (fin == 2 ? 0.0 : 1.0) * (1.0 - dec.a);
	float flake = texture(noise_tex, l_pos.xz * 4.0 + l_pos.y).b;
	float d = texture(noise_tex, l_pos.xz * 0.6 + l_pos.y * 0.3).r;
	float low = 1.0 - smoothstep(0.1, 0.9, l_pos.y);
	float dirt_amt = clamp(dirt * (0.4 + low) * smoothstep(0.35, 0.75, d), 0.0, 1.0);
	float scratch = step(1.0 - damage * 0.6, texture(noise_tex, l_pos.xz * 2.3 + l_pos.y * 1.7).g) * damage;
	vec3 c = base * (0.92 + flake * 0.16 * metallic_paint);
	float fres = pow(1.0 - clamp(dot(NORMAL, VIEW), 0.0, 1.0), 3.0);
	float metal = metallic_paint * 0.7;
	float rough = 0.26;
	float coat = 1.0;
	if (fin == 1) {
		// pearl: hue shifts towards the second tone at grazing angles
		c = mix(c, mix(c.gbr, paint2, 0.4) * 1.1, fres * 0.55);
		metal = 0.45;
		rough = 0.22;
	} else if (fin == 2) {
		// matte / satin wrap
		metal = 0.08;
		rough = 0.58;
		coat = 0.0;
	} else if (fin == 3) {
		// candy: deep saturated colour over a bright metallic base
		c = mix(c * 1.25, c * c * 1.6, 0.5);
		metal = 0.78;
		rough = 0.18;
	}
	c = mix(c, dec.rgb, dec.a);
	metal = mix(metal, 0.1, dec.a);
	rough = mix(rough, 0.35, dec.a);
	c = mix(c, vec3(0.34, 0.3, 0.25), dirt_amt);
	c = mix(c, vec3(0.55, 0.55, 0.56), scratch);
	float mu = step(0.5, seam_mask.x);
	float mv = step(0.5, seam_mask.y);
	float gap = max(gap_line(UV.x) * mu, gap_line(UV.y) * mv);
	// soft rolled edge next to the gap
	float edge = max((1.0 - smoothstep(0.0016, 0.007, abs(UV.x))) * mu, (1.0 - smoothstep(0.0016, 0.007, abs(UV.y))) * mv);
	// orange peel in the clear coat
	float peel = texture(noise_tex, l_pos.xz * 9.0 + l_pos.yx * 7.0).r;
	c *= 1.0 - edge * 0.25;
	c = mix(c, vec3(0.012), gap);
	ALBEDO = c;
	METALLIC = mix(mix(metal, 0.2, dirt_amt + scratch), 0.0, gap);
	ROUGHNESS = mix(mix(rough, 0.85, dirt_amt + scratch * 0.5) * mix(1.0, 0.6, wetness * coat), 0.9, gap);
	CLEARCOAT = coat * (1.0 - dirt_amt) * (1.0 - gap);
	CLEARCOAT_ROUGHNESS = mix(0.03 + peel * 0.05, 0.4, dirt_amt);
	vec2 nm = vec2(peel - 0.5, texture(noise_tex, l_pos.zy * 9.0).g - 0.5) * 0.04 * coat;
	nm += frnd * 0.16 * fnear * metallic_paint;
	NORMAL_MAP = vec3(0.5 + nm.x, 0.5 + nm.y, 1.0);
}
