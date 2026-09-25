// Emissive surfaces (neon signs, lamp heads, tunnel lights). Colour from vertex colour.
// mode 0: always lit (tunnels), 1: follows the city lights (street lamps), 2: neon sign (dim by day)
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_back, diffuse_burley, specular_schlick_ggx;

global uniform float city_lights;
global uniform float night;

uniform int mode = 1;
uniform vec3 color : source_color = vec3(1.0, 0.92, 0.78);
uniform bool use_vertex_color = true;
uniform float energy = 4.0;
uniform float day_energy = 0.15;

void fragment() {
	vec3 c = use_vertex_color ? COLOR.rgb * color : color;
	float on = 1.0;
	if (mode == 1) { on = mix(day_energy, 1.0, city_lights); }
	if (mode == 2) { on = mix(0.35, 1.0, night); }
	ALBEDO = c * 0.25 + 0.05;
	ROUGHNESS = 0.4;
	EMISSION = c * energy * on;
}
