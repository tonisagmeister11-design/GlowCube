// Characters: vertex colour * per-instance tint (skin tone / clothing colour) plus
// per-material micro detail that stays glued to the body while animating: the generator
// stores the rest-pose position in UV (x, y) and UV2.x (z).
//   mode 0 plain, 1 skin (pores, subsurface scattering, oily T-zone),
//   2 cloth (weave, fuzz sheen, fabric variation), 3 hair (strands, sheen band), 4 eye (wet, clear coat)
// Detail is bump-mapped from screen-space derivatives (no tangents needed) and fades out with
// distance, so it neither aliases nor costs much on far NPCs.
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_back, diffuse_burley, specular_schlick_ggx;

global uniform float wetness;

instance uniform vec3 tint : source_color = vec3(1.0);
uniform int mode = 0;
uniform float roughness = 0.65;
uniform float sss = 0.0;
uniform float metallic = 0.0;
uniform float rim = 0.0;
uniform float detail = 1.0;
uniform float specular_amount = 0.5;

varying vec3 rest;

void vertex() {
	rest = vec3(UV.x, UV.y, UV2.x);
}

float hash3(vec3 p) {
	p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
	p *= 17.0;
	return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vnoise(vec3 x) {
	vec3 i = floor(x);
	vec3 f = fract(x);
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(mix(hash3(i), hash3(i + vec3(1, 0, 0)), f.x),
				   mix(hash3(i + vec3(0, 1, 0)), hash3(i + vec3(1, 1, 0)), f.x), f.y),
			   mix(mix(hash3(i + vec3(0, 0, 1)), hash3(i + vec3(1, 0, 1)), f.x),
				   mix(hash3(i + vec3(0, 1, 1)), hash3(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

// perturbs the view-space normal by the screen-space gradient of height h
vec3 bump(vec3 n, vec3 pos, float h, float strength) {
	vec3 dpdx = dFdx(pos);
	vec3 dpdy = dFdy(pos);
	float dhdx = dFdx(h);
	float dhdy = dFdy(h);
	vec3 r1 = cross(dpdy, n);
	vec3 r2 = cross(n, dpdx);
	float det = dot(dpdx, r1);
	vec3 grad = sign(det) * (dhdx * r1 + dhdy * r2);
	return normalize(abs(det) * n - strength * grad);
}

void fragment() {
	vec3 c = COLOR.rgb * tint;
	float rough = roughness;
	float dist = length(VERTEX);
	float near = clamp(1.0 - (dist - 2.0) / 7.0, 0.0, 1.0) * detail;
	float spec = specular_amount;
	vec3 n = NORMAL;
	float fres = pow(1.0 - clamp(dot(NORMAL, VIEW), 0.0, 1.0), 3.0);
	if (mode == 1) {
		// skin: pores + fine wrinkles, mottled tone, oilier forehead/nose, subsurface scattering
		float pores = vnoise(rest * 850.0);
		float bumps = vnoise(rest * 140.0);
		float mott = vnoise(rest * 35.0);
		c *= 0.95 + mott * 0.1;
		if (near > 0.0) {
			n = bump(n, VERTEX, (pores * 0.6 + bumps * 0.4) * 0.00022, near);
		}
		float tzone = smoothstep(1.62, 1.7, rest.y) * smoothstep(-0.07, -0.11, rest.z);
		rough = clamp(roughness - tzone * 0.18 + (pores - 0.5) * 0.12, 0.25, 0.9);
		spec = 0.35 + tzone * 0.1;
		SSS_STRENGTH = sss;
		c = mix(c, c * vec3(1.08, 0.9, 0.86), fres * 0.35);
	} else if (mode == 2) {
		// cloth: woven threads (diagonal twill), slub variation and a velvety sheen at grazing angles
		float warp = sin((rest.x + rest.y * 0.7 + rest.z) * 1300.0);
		float weft = sin((rest.y - rest.x * 0.5 - rest.z * 0.4) * 1300.0);
		float weave = warp * weft * clamp(1.0 - (dist - 0.8) / 1.6, 0.0, 1.0);
		float slub = vnoise(rest * vec3(40.0, 300.0, 40.0));
		c *= 0.93 + slub * 0.1 + weave * 0.03 * near;
		if (near > 0.0) {
			n = bump(n, VERTEX, weave * 0.00012 + vnoise(rest * 90.0) * 0.00035, near);
		}
		c = mix(c, c * 1.35 + vec3(0.03), fres * 0.4);
		rough = clamp(roughness + (slub - 0.5) * 0.1, 0.4, 1.0);
		spec = 0.3;
	} else if (mode == 3) {
		// hair: strands along the fall direction (height), per-strand tone, sheen band
		float strands = vnoise(vec3(rest.x * 900.0, rest.y * 25.0, rest.z * 900.0));
		float clumps = vnoise(vec3(rest.x * 120.0, rest.y * 8.0, rest.z * 120.0));
		c *= 0.72 + strands * 0.38 + clumps * 0.12;
		if (near > 0.0) {
			n = bump(n, VERTEX, strands * 0.0005 + clumps * 0.0008, near);
		}
		rough = clamp(roughness + (strands - 0.5) * 0.2, 0.2, 0.9);
		spec = 0.55;
		c += c * fres * 0.5;
	} else if (mode == 4) {
		// eyes: wet, sharp reflections
		rough = roughness;
		spec = 0.7;
		CLEARCOAT = 1.0;
		CLEARCOAT_ROUGHNESS = 0.02;
	}
	NORMAL = n;
	ALBEDO = c * mix(1.0, 0.8, wetness * 0.5);
	ROUGHNESS = mix(rough, rough * 0.6, wetness);
	METALLIC = metallic;
	SPECULAR = spec;
	EMISSION = c * fres * rim;
}
