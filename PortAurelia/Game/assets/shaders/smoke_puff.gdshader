// Billowing smoke puff: lit, soft-edged, noise-displaced sphere that thins out with `age`.
// `glow` lets the underside pick up the fire light early in the explosion.
shader_type spatial;
render_mode blend_mix, cull_back, depth_draw_never, shadows_disabled, diffuse_lambert, specular_disabled;

instance uniform float age = 0.0;
instance uniform float seed = 0.0;
instance uniform float glow = 0.0;
uniform vec3 smoke_color : source_color = vec3(0.17, 0.155, 0.14);

varying vec3 o_pos;
varying float n_v;

float hash(vec3 p) {
	p = fract(p * 0.3183099 + vec3(0.1, 0.71, 0.37));
	p *= 17.0;
	return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
	vec3 i = floor(x);
	vec3 f = fract(x);
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
			   mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

void vertex() {
	vec3 d = normalize(VERTEX);
	float n = noise(d * 2.2 + vec3(seed, seed * 0.7, seed * 1.3) + vec3(0.0, -TIME * 0.15, 0.0)) * 0.65
		+ noise(d * 4.7 + vec3(seed)) * 0.35;
	n_v = n;
	o_pos = d;
	VERTEX *= 1.0 + (n - 0.5) * 0.6;
}

void fragment() {
	float facing = clamp(dot(NORMAL, VIEW), 0.0, 1.0);
	float n = n_v * 0.7 + noise(o_pos * 7.0 + vec3(seed, TIME * 0.2, 0.0)) * 0.3;
	ALBEDO = smoke_color * (0.75 + n * 0.5);
	EMISSION = vec3(1.0, 0.35, 0.06) * glow * smoothstep(0.2, -0.6, o_pos.y) * 3.0;
	float soft = smoothstep(0.05, 0.8, facing);
	float wisps = smoothstep(0.25 + age * 0.45, 0.65 + age * 0.3, n);
	ALPHA = clamp(soft * soft * (1.0 - age) * wisps * 0.78, 0.0, 1.0);
	ROUGHNESS = 1.0;
}
