// Alpha-tested foliage with wind sway and subsurface-like back lighting.
// Vertex colour R = sway weight (0 at trunk base, 1 at tips).
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_disabled, diffuse_burley, specular_schlick_ggx;

global uniform vec2 wind;
global uniform float wetness;

uniform sampler2D albedo_tex : source_color, filter_linear_mipmap, repeat_enable;
uniform vec3 color : source_color = vec3(1.0);
uniform float alpha_cut = 0.45;
uniform float sway = 0.25;
uniform float flutter = 0.04;
uniform vec3 back_light : source_color = vec3(0.35, 0.45, 0.15);

void vertex() {
	vec3 wp = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	vec3 origin = MODEL_MATRIX[3].xyz;
	float h = max(VERTEX.y, 0.0);
	float t = TIME;
	float phase = dot(origin.xz, vec2(0.07, 0.11));
	float strength = length(wind) * (0.6 + 0.4 * sin(t * 0.7 + phase));
	vec2 wdir = length(wind) > 0.001 ? normalize(wind) : vec2(1.0, 0.0);
	float bend = sway * strength * h * 0.04 * COLOR.r;
	VERTEX.xz += (inverse(mat3(MODEL_MATRIX)) * vec3(wdir.x, 0.0, wdir.y)).xz * bend * (0.8 + 0.2 * sin(t * 1.9 + phase));
	VERTEX += NORMAL * sin(t * 6.0 + wp.x * 1.3 + wp.z * 1.7) * flutter * COLOR.r * strength;
}

void fragment() {
	vec4 t = texture(albedo_tex, UV);
	if (t.a < alpha_cut) { discard; }
	vec3 c = t.rgb * color;
	ALBEDO = c * mix(1.0, 0.8, wetness);
	ROUGHNESS = mix(0.75, 0.35, wetness);
	BACKLIGHT = back_light;
	SPECULAR = 0.3;
}
