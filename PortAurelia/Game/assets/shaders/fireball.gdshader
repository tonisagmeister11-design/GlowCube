// Explosion fireball: a noise-displaced sphere that burns from white-hot through orange and
// deep red, breaking up into flame tongues as `age` goes 0 -> 1 (animated per instance).
shader_type spatial;
render_mode blend_add, unshaded, cull_back, depth_draw_never, shadows_disabled, fog_disabled;

instance uniform float age = 0.0;
instance uniform float seed = 0.0;
uniform float intensity = 2.1;

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

float fbm(vec3 p) {
	return noise(p) * 0.55 + noise(p * 2.13) * 0.3 + noise(p * 4.37) * 0.15;
}

void vertex() {
	vec3 d = normalize(VERTEX);
	float t = TIME * 0.9 + age * 2.5;
	float n = fbm(d * 2.4 + vec3(seed, seed * 1.37, seed * 0.71) + vec3(0.0, -t, 0.0));
	n_v = n;
	o_pos = d;
	VERTEX *= 1.0 + (n - 0.5) * (0.5 + age * 0.4);
}

void fragment() {
	float facing = clamp(dot(NORMAL, VIEW), 0.0, 1.0);
	float t = TIME * 1.3 + age * 3.0;
	float fine = fbm(o_pos * 6.0 + vec3(seed * 2.1, -t, seed));
	float n = n_v * 0.6 + fine * 0.4;
	// temperature: hot core facing the camera, cooling with age and at the ragged rim
	float heat = clamp(1.25 - age * 1.35 + (n - 0.5) * 0.9, 0.0, 1.0) * (0.25 + 0.75 * pow(facing, 0.7));
	vec3 col = mix(vec3(0.3, 0.025, 0.0), vec3(0.95, 0.24, 0.02), smoothstep(0.05, 0.4, heat));
	col = mix(col, vec3(1.0, 0.55, 0.12), smoothstep(0.4, 0.72, heat));
	col = mix(col, vec3(1.0, 0.88, 0.6), smoothstep(0.86, 1.0, heat));
	// sooty dark patches swirling through the flames
	col *= mix(1.0, 0.25, smoothstep(0.55, 0.8, fine) * smoothstep(0.15, 0.6, age));
	// break up into flame tongues as it ages
	float keep = smoothstep(age * 1.05 - 0.05, age * 1.05 + 0.12, n + facing * 0.25);
	ALBEDO = col * intensity * (0.35 + heat);
	ALPHA = clamp(keep * (0.2 + heat * 0.8) * smoothstep(0.0, 0.3, facing + 0.05), 0.0, 1.0) * 0.8;
}
