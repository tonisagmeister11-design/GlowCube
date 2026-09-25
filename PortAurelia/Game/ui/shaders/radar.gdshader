shader_type canvas_item;
// Radar: shows the pre-rendered city map around the player, rotated so the camera
// looks "up", inside a rounded rectangle with a soft dark rim.

uniform sampler2D map_tex : filter_linear_mipmap, repeat_disable;
uniform vec2 center_uv = vec2(0.5);
uniform float rot = 0.0;
uniform float zoom = 0.04;          // half extent in UV units
uniform float corner = 0.18;
uniform float wanted_flash = 0.0;   // 0..1 red/blue edge flashing
uniform float time_s = 0.0;
uniform float night = 0.0;

float rbox(vec2 p, vec2 b, float r) {
	vec2 q = abs(p) - b + r;
	return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void fragment() {
	vec2 p = UV * 2.0 - 1.0;
	float d = rbox(p, vec2(1.0), corner);
	if (d > 0.0) discard;
	float c = cos(rot), s = sin(rot);
	vec2 w = vec2(p.x * c - p.y * s, p.x * s + p.y * c);
	vec2 uv = center_uv + w * zoom;
	vec3 col;
	if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) {
		col = vec3(0.12, 0.26, 0.38);
	} else {
		col = texture(map_tex, uv).rgb;
	}
	// radar look: slightly desaturated and darker at night
	float lum = dot(col, vec3(0.3, 0.59, 0.11));
	col = mix(col, vec3(lum), 0.15) * mix(0.92, 0.62, night);
	float rim = smoothstep(-0.06, 0.0, d);
	vec3 rim_col = vec3(0.05);
	if (wanted_flash > 0.0) {
		float ph = step(0.5, fract(time_s * 2.0));
		rim_col = mix(vec3(0.9, 0.1, 0.1), vec3(0.15, 0.3, 1.0), ph);
		rim = max(rim, smoothstep(-0.1, 0.0, d) * wanted_flash);
	}
	col = mix(col, rim_col, rim * 0.9);
	COLOR = vec4(col, 0.93);
}
