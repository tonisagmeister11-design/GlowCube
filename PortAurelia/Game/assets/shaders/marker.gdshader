// Interaction / checkpoint marker: translucent cylinder wall fading out towards the top,
// with a soft pulsing glow. Unshaded, visible from inside and outside.
shader_type spatial;
render_mode unshaded, cull_disabled, depth_draw_never, blend_mix, shadows_disabled;

uniform vec4 color : source_color = vec4(1.0, 0.8, 0.2, 0.55);
uniform float height = 1.0;
varying float v_h;

void vertex() {
	v_h = clamp(VERTEX.y / height + 0.5, 0.0, 1.0);
}

void fragment() {
	float pulse = 0.85 + 0.15 * sin(TIME * 3.0);
	ALBEDO = color.rgb * (1.2 + (1.0 - v_h) * 0.6);
	ALPHA = color.a * pow(1.0 - v_h, 1.6) * pulse;
}
