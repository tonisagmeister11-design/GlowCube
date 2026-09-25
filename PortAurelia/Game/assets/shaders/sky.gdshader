// Procedural sky: atmospheric gradient, sun disc + halo, drifting clouds,
// moon and stars at night. Parameters are driven by DayNight and Weather.
shader_type sky;
render_mode use_quarter_res_pass;

uniform vec3 zenith_day : source_color = vec3(0.16, 0.38, 0.78);
uniform vec3 horizon_day : source_color = vec3(0.62, 0.78, 0.94);
uniform vec3 zenith_night : source_color = vec3(0.01, 0.015, 0.04);
uniform vec3 horizon_night : source_color = vec3(0.04, 0.06, 0.11);
uniform vec3 sunset_color : source_color = vec3(1.0, 0.45, 0.18);
uniform vec3 ground_color : source_color = vec3(0.32, 0.33, 0.34);
uniform float day = 1.0;          // 0 night .. 1 day
uniform float sunset = 0.0;       // 0..1 dawn / dusk tint
uniform float cloud_cover = 0.35; // 0 clear .. 1 overcast
uniform float cloud_dark = 0.0;   // storm darkening
uniform float fog_haze = 0.0;     // weather fog
uniform vec3 moon_dir = vec3(-0.3, 0.6, -0.7);
uniform sampler2D cloud_tex : filter_linear_mipmap, repeat_enable;
uniform float cloud_speed = 0.004;
uniform float star_intensity = 1.0;

float hash(vec3 p) {
	p = fract(p * 0.3183099 + 0.1);
	p *= 17.0;
	return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

void sky() {
	vec3 dir = EYEDIR;
	float y = dir.y;
	float h = clamp(y, 0.0, 1.0);
	vec3 sun = LIGHT0_ENABLED ? LIGHT0_DIRECTION : vec3(0.0, 1.0, 0.0);

	vec3 zen = mix(zenith_night, zenith_day, day);
	vec3 hor = mix(horizon_night, horizon_day, day);
	float sun_side = pow(clamp(dot(normalize(vec3(dir.x, 0.0, dir.z)), normalize(vec3(sun.x, 0.0, sun.z))) * 0.5 + 0.5, 0.0, 1.0), 3.0);
	hor = mix(hor, sunset_color, sunset * (0.35 + 0.65 * sun_side));
	vec3 col = mix(hor, zen, pow(h, 0.45));
	// haze / fog brightening at the horizon
	col = mix(col, hor * 1.05, fog_haze * (1.0 - h * 0.5));

	// sun disc and halo
	float sd = dot(dir, sun);
	float disc = smoothstep(0.9993, 0.9996, sd);
	float halo = pow(max(sd, 0.0), 180.0) * 0.8 + pow(max(sd, 0.0), 12.0) * 0.25;
	vec3 sun_col = mix(vec3(1.0, 0.55, 0.3), vec3(1.0, 0.97, 0.9), clamp(sun.y * 3.0, 0.0, 1.0));
	float sun_vis = clamp(sun.y * 8.0 + 0.3, 0.0, 1.0) * (1.0 - cloud_cover * 0.85);
	col += sun_col * (disc * 20.0 + halo * (0.6 + sunset)) * sun_vis;

	// stars and moon
	float nightf = 1.0 - day;
	if (nightf > 0.01 && y > 0.0) {
		vec3 sp = floor(dir * 420.0);
		float s = step(0.9975, hash(sp)) * hash(sp + 3.1);
		col += vec3(s) * nightf * star_intensity * (1.0 - cloud_cover) * smoothstep(0.0, 0.25, y);
		vec3 md = normalize(moon_dir);
		float m = smoothstep(0.9990, 0.9993, dot(dir, md));
		col += vec3(0.85, 0.88, 0.95) * m * nightf * 2.0;
		col += vec3(0.25, 0.3, 0.4) * pow(max(dot(dir, md), 0.0), 60.0) * nightf * 0.3;
	}

	// clouds on a dome
	if (y > 0.0) {
		vec2 cuv = dir.xz / (y + 0.12) * 0.35;
		vec2 drift = vec2(TIME * cloud_speed, TIME * cloud_speed * 0.4);
		float n1 = texture(cloud_tex, cuv * 0.35 + drift).r;
		float n2 = texture(cloud_tex, cuv * 0.9 - drift * 1.7).g;
		float n3 = texture(cloud_tex, cuv * 2.3 + drift * 2.1).a;
		float n = n1 * 0.62 + n2 * 0.28 + n3 * 0.1;
		float cov = mix(0.62, 0.22, cloud_cover);
		float c = smoothstep(cov, cov + 0.22, n) * smoothstep(0.0, 0.18, y);
		float thick = smoothstep(cov, cov + 0.5, n);
		vec3 lit = mix(vec3(1.0, 0.98, 0.95), sunset_color * 1.1 + 0.2, sunset * 0.8) * mix(0.06, 1.0, day);
		vec3 shade = mix(vec3(0.55, 0.6, 0.68), vec3(0.3, 0.32, 0.36), cloud_dark) * mix(0.03, 1.0, day);
		vec3 cc = mix(lit, shade, thick * 0.8 + cloud_dark * 0.5);
		// silver lining towards the sun
		cc += sun_col * pow(max(sd, 0.0), 8.0) * (1.0 - thick) * 0.6 * day;
		col = mix(col, cc, c * 0.95);
	}

	// below the horizon: ground tone fading into haze
	if (y < 0.0) {
		col = mix(hor, ground_color * mix(0.05, 1.0, day), clamp(-y * 4.0, 0.0, 1.0));
	}
	COLOR = col;
}
