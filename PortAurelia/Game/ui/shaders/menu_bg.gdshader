// Animated sunset gradient with a stylised skyline silhouette and drifting haze
// (menu and loading screen background).
shader_type canvas_item;

float skyline(float x) {
	float h = 0.0;
	float cell = floor(x * 38.0);
	float r = fract(sin(cell * 91.3) * 43758.5);
	h = 0.12 + r * 0.22;
	if (abs(x - 0.52) < 0.012) { h = 0.55; }
	if (abs(x - 0.47) < 0.02) { h = max(h, 0.42); }
	if (abs(x - 0.6) < 0.018) { h = max(h, 0.4); }
	return h;
}

void fragment() {
	vec2 uv = UV;
	float t = TIME * 0.02;
	vec3 top = vec3(0.10, 0.08, 0.22);
	vec3 mid = vec3(0.85, 0.35, 0.28);
	vec3 low = vec3(1.0, 0.72, 0.35);
	vec3 c = mix(low, mid, smoothstep(0.35, 0.65, 1.0 - uv.y));
	c = mix(c, top, smoothstep(0.6, 1.0, 1.0 - uv.y));
	// sun
	float d = length((uv - vec2(0.52, 0.64)) * vec2(1.7, 1.0));
	c += vec3(1.0, 0.8, 0.45) * smoothstep(0.16, 0.13, d) * 0.9;
	c += vec3(1.0, 0.5, 0.2) * exp(-d * 5.0) * 0.35;
	// skyline
	float h = skyline(uv.x + 0.0);
	float ground = 0.72;
	if (1.0 - uv.y < (1.0 - ground) + h * 0.55 && uv.y > ground - h * 0.55) {
		c = mix(c, vec3(0.06, 0.04, 0.1), 0.92);
	}
	// sea
	if (uv.y > ground) {
		float w = sin(uv.y * 240.0 + TIME * 1.5) * 0.5 + 0.5;
		c = mix(vec3(0.05, 0.08, 0.2), vec3(1.0, 0.6, 0.3), (1.0 - smoothstep(0.0, 0.25, abs(uv.x - 0.52))) * w * (1.0 - (uv.y - ground) * 3.0) * 0.6);
	}
	// haze
	c += vec3(0.08) * sin(uv.x * 6.0 + t * 20.0) * 0.1;
	COLOR = vec4(c, 1.0);
}
