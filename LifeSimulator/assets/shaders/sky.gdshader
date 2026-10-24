// Himmel: Farbverlauf, Sonne mit Halo, Mond, prozedurale Wolkendecke,
// Sterne bei Nacht. Alle Farben werden vom Tag-Nacht-Zyklus gesetzt.
shader_type sky;
render_mode use_half_res_pass;

uniform vec3 zenith_col : source_color = vec3(0.20, 0.42, 0.78);
uniform vec3 horizon_col : source_color = vec3(0.72, 0.80, 0.90);
uniform vec3 ground_col : source_color = vec3(0.30, 0.32, 0.33);
uniform vec3 sun_col : source_color = vec3(1.0, 0.95, 0.85);
uniform vec3 sun_dir = vec3(0.0, 0.7, -0.7);
uniform vec3 moon_dir = vec3(0.0, -0.7, 0.7);
uniform float sun_glow = 0.6;
uniform float sun_disk = 1.0;
uniform float cloud_cover = 0.45;
uniform vec3 cloud_lit : source_color = vec3(1.0, 0.97, 0.92);
uniform vec3 cloud_shade : source_color = vec3(0.55, 0.60, 0.68);
uniform vec2 cloud_offset = vec2(0.0);
uniform float stars = 0.0;
uniform float energy = 1.0;
uniform sampler2D noise_tex : filter_linear_mipmap, repeat_enable;

float hash13(vec3 p) {
	p = fract(p * 0.1031);
	p += dot(p, p.zyx + 31.32);
	return fract((p.x + p.y) * p.z);
}

void sky() {
	vec3 d = normalize(EYEDIR);
	float h = d.y;
	vec3 sd = normalize(sun_dir);
	float hz = pow(1.0 - clamp(h, 0.0, 1.0), 3.0);
	vec3 col = mix(zenith_col, horizon_col, hz);
	// Horizontdunst Richtung Sonne waermer
	float toward_sun = max(dot(normalize(vec3(d.x, 0.0, d.z) + 1e-4), normalize(vec3(sd.x, 0.0, sd.z) + 1e-4)), 0.0);
	col += sun_col * hz * pow(toward_sun, 4.0) * sun_glow * 0.6;
	// unter dem Horizont
	col = mix(col, ground_col, smoothstep(0.0, -0.12, h));
	float cs = dot(d, sd);
	// Halo
	col += sun_col * (pow(max(cs, 0.0), 12.0) * 0.35 + pow(max(cs, 0.0), 200.0) * 1.2) * sun_glow;
	// Sonnenscheibe
	if (!AT_CUBEMAP_PASS) {
		col += sun_col * smoothstep(0.99955, 0.99975, cs) * 18.0 * sun_disk * step(-0.02, sd.y);
	}
	// Mond
	float cm = dot(d, normalize(moon_dir));
	col += vec3(0.85, 0.88, 1.0) * smoothstep(0.99965, 0.9998, cm) * 3.0 * stars;
	col += vec3(0.25, 0.3, 0.45) * pow(max(cm, 0.0), 60.0) * 0.25 * stars;
	// Sterne
	if (h > 0.0 && stars > 0.01) {
		vec3 g = floor(d * 380.0);
		float s = hash13(g);
		float tw = step(0.9975, s) * (0.4 + 0.6 * hash13(g + 7.0));
		col += vec3(tw) * stars * smoothstep(0.0, 0.25, h) * (1.0 - cloud_cover);
	}
	// Wolken: Projektion auf eine Ebene
	if (h > -0.02) {
		float hh = max(h, 0.0) + 0.06;
		vec2 cp = d.xz / hh * 0.22 + cloud_offset;
		float n = texture(noise_tex, cp * 0.17).r * 0.5 + texture(noise_tex, cp * 0.53 + 0.3).g * 0.3 + texture(noise_tex, cp * 1.6 + 0.7).b * 0.2;
		float c = smoothstep(1.0 - cloud_cover - 0.08, 1.0 - cloud_cover + 0.28, n);
		c *= smoothstep(-0.02, 0.10, h);
		// Lichtseite: zur Sonne hin heller, Raender durchscheinend
		float dens = smoothstep(0.0, 0.5, c);
		vec3 lit = mix(cloud_shade, cloud_lit, clamp(0.35 + 0.65 * pow(max(cs, 0.0), 3.0) + (1.0 - dens) * 0.4, 0.0, 1.0));
		float n2 = texture(noise_tex, cp * 3.1 + 0.1).a;
		lit *= 0.85 + 0.3 * n2;
		col = mix(col, lit, c * 0.92);
	}
	COLOR = col * energy;
}
