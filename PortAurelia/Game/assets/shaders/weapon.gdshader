// Weapons: PBR metal / polymer / wood / rubber / glass with
//  * edge wear from vertex colour R (chamfers painted by the Blender generator)
//  * baked ambient occlusion from vertex colour G
//  * triplanar micro roughness variation, optional grip stippling (normal noise)
//  * procedural walnut / maple grain, clear coat, brushed steel anisotropy
shader_type spatial;
render_mode blend_mix, depth_draw_opaque, cull_back, diffuse_burley, specular_schlick_ggx;

global uniform float wetness;

uniform vec3 base_color : source_color = vec3(0.05);
uniform float metallic : hint_range(0.0, 1.0) = 0.8;
uniform float roughness : hint_range(0.0, 1.0) = 0.4;
uniform vec3 wear_color : source_color = vec3(0.55);
uniform float wear_metallic : hint_range(0.0, 1.0) = 1.0;
uniform float wear_roughness : hint_range(0.0, 1.0) = 0.25;
uniform float wear_amount : hint_range(0.0, 1.0) = 0.6;
uniform float micro : hint_range(0.0, 1.0) = 0.2;
uniform float stipple : hint_range(0.0, 1.0) = 0.0;
uniform float stipple_scale = 220.0;
uniform bool wood = false;
uniform vec3 wood_dark : source_color = vec3(0.16, 0.07, 0.03);
uniform float grain_scale = 700.0;
uniform float clearcoat_amount : hint_range(0.0, 1.0) = 0.0;
uniform float aniso : hint_range(-1.0, 1.0) = 0.0;
uniform float specular_amount : hint_range(0.0, 1.0) = 0.5;
uniform float fresnel_tint : hint_range(0.0, 1.0) = 0.0;
uniform sampler2D noise_tex : filter_linear_mipmap, repeat_enable;

varying vec3 l_pos;
varying vec3 l_nrm;

void vertex() {
	l_pos = VERTEX;
	l_nrm = NORMAL;
}

vec4 tri(vec3 p, vec3 n, float s) {
	vec3 w = pow(abs(n), vec3(4.0));
	w /= (w.x + w.y + w.z + 1e-5);
	return texture(noise_tex, p.yz * s) * w.x + texture(noise_tex, p.xz * s) * w.y + texture(noise_tex, p.xy * s) * w.z;
}

void fragment() {
	vec4 coarse = tri(l_pos, l_nrm, 4.0);
	vec4 fine = tri(l_pos, l_nrm, 38.0);
	vec3 c = base_color;
	if (wood) {
		// growth rings around the long (Z) axis: smooth low-frequency warp so the lines stay long
		// and straight along the stock, thin dark late-wood bands, fine pore streaks across
		float d = length(l_pos.xy - vec2(0.004, 0.03));
		vec2 wuv = vec2(l_pos.z * 0.8, d * 3.0 + l_pos.x * 2.0);
		float warp = textureLod(noise_tex, wuv, 3.0).r * 2.2 + textureLod(noise_tex, wuv * 3.1, 2.0).g * 0.6;
		float r = d * grain_scale + warp + l_pos.z * 3.0;
		float g = fract(r);
		float band = smoothstep(0.62, 0.9, g) * (1.0 - smoothstep(0.9, 1.0, g));
		float pores = texture(noise_tex, vec2(l_pos.z * 1.5, (l_pos.x * 0.7 + l_pos.y) * 90.0)).b;
		float figure = textureLod(noise_tex, vec2(l_pos.z * 2.5, d * 11.0), 2.0).a;
		c = mix(base_color, wood_dark, clamp(band * 0.7 + (pores - 0.5) * 0.35 + (figure - 0.5) * 0.5, 0.0, 1.0));
	}
	float edge = COLOR.r;
	float occl = COLOR.g;
	float wear = clamp(edge * wear_amount * smoothstep(0.42, 0.85, coarse.g * 0.75 + fine.b * 0.45), 0.0, 1.0);
	vec3 albedo = mix(c, wear_color, wear) * mix(0.45, 1.0, occl);
	// fine scratches / handling marks lift the colour a little
	albedo *= 0.94 + fine.a * 0.12;
	ALBEDO = albedo * mix(1.0, 0.82, wetness);
	METALLIC = mix(metallic, wear_metallic, wear);
	float rough = roughness * (1.0 - micro + micro * 2.0 * fine.r);
	ROUGHNESS = clamp(mix(rough, wear_roughness, wear), 0.03, 1.0) * mix(1.0, 0.6, wetness);
	SPECULAR = specular_amount;
	if (stipple > 0.0) {
		vec4 st = tri(l_pos, l_nrm, stipple_scale);
		NORMAL_MAP = vec3(0.5 + (st.r - 0.5) * stipple * 2.0, 0.5 + (st.g - 0.5) * stipple * 2.0, 1.0);
	}
	CLEARCOAT = clearcoat_amount;
	CLEARCOAT_ROUGHNESS = 0.12;
	ANISOTROPY = aniso;
	if (fresnel_tint > 0.0) {
		float f = pow(1.0 - clamp(dot(NORMAL, VIEW), 0.0, 1.0), 3.0);
		EMISSION = vec3(0.10, 0.25, 0.35) * f * fresnel_tint;
	}
}
