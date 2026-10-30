// Fassaden-Shader: Wandtextur + prozedurale Fenster mit Innenraum-Illusion
// (Interior Mapping), Fensterrahmen, Fensterbaenken, Vorhaengen,
// Schaufenstern, Glasfassaden und nachts beleuchteten Raeumen.
//
// Vertexdaten (siehe MeshBatch / BuildingBuilder):
//   UV      = (Meter entlang der Fassade, -Meter Hoehe ab Bandunterkante)
//   COLOR   = rgb Wandfarbe (sRGB) bzw. Glastoenung, a = Zustand 0..1
//   CUSTOM0 = (Geschosshoehe, Achsabstand, Zufallswert 0..1, Flags)
//   Flags   = Fensterstil (0..7) + 8 * Modus (0..7) + 64 * Innenraumart (0..3)
//   Modus: 0 Wand, 1 Fenster, 2 Schaufenster, 3 Glasfassade, 4 Oberlicht (Halle),
//          5 vernagelt, 6 Glasfassade ohne Bruestung
shader_type spatial;
render_mode cull_back, diffuse_burley, specular_schlick_ggx;

uniform sampler2D wall_tex : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D wall_nrm : hint_normal, filter_linear_mipmap_anisotropic, repeat_enable;
uniform sampler2D interior_tex : source_color, filter_linear_mipmap, repeat_disable;
uniform sampler2D noise_tex : filter_linear_mipmap, repeat_enable;
uniform float tile = 2.0;
uniform float tint_amount = 1.0;
uniform float wall_metallic = 0.0;
uniform float rough_mul = 1.0;
uniform float normal_depth = 1.0;
uniform vec3 frame_light : source_color = vec3(0.92, 0.92, 0.90);
uniform vec3 frame_dark : source_color = vec3(0.16, 0.17, 0.18);
uniform vec3 sill_col : source_color = vec3(0.78, 0.76, 0.72);
uniform float glass_facade = 0.0;
// Globale Werte (vom Tag-Nacht-Zyklus gesetzt)
uniform float night = 0.0;
uniform float lit_fraction = 0.5;
uniform float shop_lit = 1.0;
uniform float wet = 0.0;
// Leistungsstufe: 0 = flache Fenster ohne Innenraum (spart viel Rechenzeit),
// 1 = Innenraum nur nachts, ab 2 = voller Effekt.
global uniform int perf_facade_detail;
uniform float day_seed = 0.0;

varying vec4 P;
varying vec4 vcol;
varying vec3 wpos;

float hash12(vec2 p) {
	vec3 p3 = fract(vec3(p.xyx) * 0.1031);
	p3 += dot(p3, p3.yzx + 33.33);
	return fract((p3.x + p3.y) * p3.z);
}

vec3 to_linear(vec3 c) {
	return pow(max(c, vec3(0.0)), vec3(2.2));
}

void vertex() {
	P = CUSTOM0;
	vcol = COLOR;
	wpos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
}

// Fensterrechteck im Zellenraum (Meter): x0, y0, x1, y1
vec4 window_rect(int style, float bay, float fh) {
	float top = fh - 0.55;
	if (style == 0) { float w = min(bay * 0.48, 1.45); return vec4((bay - w) * 0.5, 0.9, (bay + w) * 0.5, top); }
	if (style == 1) { float w = min(bay * 0.72, 2.8); return vec4((bay - w) * 0.5, 0.8, (bay + w) * 0.5, top); }
	if (style == 2) { float w = min(bay * 0.42, 1.3); return vec4((bay - w) * 0.5, 0.2, (bay + w) * 0.5, fh - 0.45); }
	if (style == 3) { return vec4(0.05, 0.95, bay - 0.05, fh - 0.4); }
	if (style == 4) { float w = min(bay * 0.46, 1.6); return vec4((bay - w) * 0.5, 0.75, (bay + w) * 0.5, fh - 0.45); }
	if (style == 5) { float w = min(bay * 0.36, 1.1); return vec4((bay - w) * 0.5, 1.0, (bay + w) * 0.5, min(top, 2.3)); }
	if (style == 6) { float w = min(bay * 0.8, 3.2); return vec4((bay - w) * 0.5, 0.55, (bay + w) * 0.5, fh - 0.35); }
	return vec4(0.0, 0.0, bay, fh);
}

// Innenraum per Strahlverfolgung durch eine Box hinter dem Fenster.
// loc: Position in der Zelle, d: Blickrichtung (rechts, oben, aus der Wand).
vec3 interior(vec2 loc, vec3 d, float bay, float fh, float depth, int cell_i, out float hit_back) {
	vec3 rd = d;
	rd.z = min(rd.z, -0.02);
	rd.x = abs(rd.x) < 1e-4 ? 1e-4 : rd.x;
	rd.y = abs(rd.y) < 1e-4 ? 1e-4 : rd.y;
	float tx = rd.x > 0.0 ? (bay - loc.x) / rd.x : -loc.x / rd.x;
	float ty = rd.y > 0.0 ? (fh - loc.y) / rd.y : -loc.y / rd.y;
	float tz = -depth / rd.z;
	float t = min(min(tx, ty), tz);
	vec3 hit = vec3(loc, 0.0) + rd * t;
	vec2 cell = vec2(float(cell_i % 4), float(cell_i / 4));
	vec3 c;
	hit_back = 0.0;
	if (t >= tz - 0.001) {
		vec2 uv = vec2(clamp(hit.x / bay, 0.02, 0.98), clamp(1.0 - hit.y / fh, 0.02, 0.98));
		c = texture(interior_tex, (cell + uv) / vec2(4.0, 2.0)).rgb;
		hit_back = 1.0;
	} else if (t >= ty - 0.001) {
		if (rd.y > 0.0) {
			c = vec3(0.80, 0.80, 0.78);
		} else {
			vec2 uv = vec2(0.5, 0.97);
			c = texture(interior_tex, (cell + uv) / vec2(4.0, 2.0)).rgb * 0.8;
		}
	} else {
		vec2 uv = vec2(rd.x > 0.0 ? 0.97 : 0.03, clamp(1.0 - hit.y / fh, 0.05, 0.6));
		c = texture(interior_tex, (cell + uv) / vec2(4.0, 2.0)).rgb * 0.85;
	}
	float fade = clamp(-hit.z / (depth + 1.0), 0.0, 1.0);
	return c * mix(1.0, 0.6, fade);
}

void fragment() {
	float fh = max(P.x, 0.5);
	float bay = max(P.y, 0.4);
	float seed = P.z;
	int flags = int(P.w + 0.5);
	int style = flags % 8;
	int mode = (flags / 8) % 8;
	int ikind = (flags / 64) % 4;
	vec2 uvm = vec2(UV.x, -UV.y);
	float cond = vcol.a;

	// ------------------------------------------------------------ Wand
	vec2 tuv = vec2(uvm.x, -uvm.y) / tile;
	vec4 wt = texture(wall_tex, tuv);
	vec3 wall_tint = mix(vec3(1.0), to_linear(vcol.rgb), tint_amount);
	vec3 wall_col = wt.rgb * wall_tint;
	float wall_rough = clamp(wt.a * rough_mul, 0.05, 1.0);
	vec3 wall_n = texture(wall_nrm, tuv).rgb;
	// Verschmutzung: Sockelbereich und Schlieren
	float streak = texture(noise_tex, vec2(uvm.x * 0.21 + seed * 7.0, uvm.y * 0.018)).g;
	float blot = texture(noise_tex, uvm * 0.05 + seed * 3.0).r;
	float dirt = (1.0 - cond) * (0.25 * smoothstep(0.45, 0.8, streak) + 0.35 * (1.0 - smoothstep(0.0, 1.4, wpos.y)) + 0.15 * smoothstep(0.5, 0.9, blot));
	wall_col *= 1.0 - clamp(dirt, 0.0, 0.6);

	vec3 albedo = wall_col;
	float rough = wall_rough;
	float metal = wall_metallic;
	float spec = 0.5;
	vec3 nmap = wall_n;
	float nmap_depth = normal_depth;
	vec3 emis = vec3(0.0);

	// Filterbreite fuer Detailabblendung in der Ferne (Moire vermeiden)
	float px = max(length(dFdx(uvm)), length(dFdy(uvm)));
	float lod = smoothstep(0.10, 0.35, px / min(bay, fh));

	if (mode != 0) {
		vec2 cellv = floor(vec2(uvm.x / bay, uvm.y / fh));
		vec2 loc = vec2(uvm.x - cellv.x * bay, uvm.y - cellv.y * fh);
		float h1 = hash12(cellv + vec2(seed * 173.0, seed * 59.0));
		float h2 = hash12(cellv.yx * 1.37 + vec2(seed * 31.0 + 4.1, 7.7));
		float h3 = hash12(cellv * 0.71 + vec2(day_seed * 13.0 + seed * 11.0, 2.3));
		int wstyle = style;
		vec4 wr;
		float frame_w = 0.07;
		bool curtain = (mode == 3 || mode == 6);
		if (mode == 2) {
			wr = vec4(0.10, 0.35, bay - 0.10, fh - 0.75);
			frame_w = 0.06;
		} else if (curtain) {
			wr = vec4(0.0, mode == 3 ? 0.95 : 0.0, bay, fh);
			frame_w = 0.0;
		} else if (mode == 4) {
			wr = vec4(0.35, fh - 2.6, bay - 0.35, fh - 1.0);
		} else {
			wr = window_rect(wstyle, bay, fh);
			// Paarfenster
			if (wstyle == 7) { wr = vec4(bay * 0.18, 0.9, bay * 0.82, fh - 0.55); }
		}
		bool arched = (mode == 1 && wstyle == 4);
		float hw = (wr.z - wr.x) * 0.5;
		bool inside = loc.x >= wr.x && loc.x <= wr.z && loc.y >= wr.y && loc.y <= wr.w;
		if (arched && inside && loc.y > wr.w - hw) {
			inside = distance(loc, vec2((wr.x + wr.z) * 0.5, wr.w - hw)) <= hw;
		}
		// Fensterbank
		bool sill = (mode == 1 || mode == 5) && loc.y >= wr.y - 0.08 && loc.y < wr.y && loc.x >= wr.x - 0.07 && loc.x <= wr.z + 0.07;
		if (sill) {
			albedo = to_linear(sill_col) * (0.9 + 0.1 * blot);
			rough = 0.6;
			nmap = vec3(0.5, 0.8, 0.85);
			nmap_depth = 1.0;
		}
		if (curtain) {
			// Bruestung (Geschossdecke) und Pfosten
			float post = min(loc.x, bay - loc.x);
			bool spandrel = loc.y < wr.y;
			bool mull = post < 0.045;
			bool transom = abs(loc.y - wr.y) < 0.03 || loc.y > fh - 0.03;
			vec3 tint = to_linear(vcol.rgb);
			if (mull || transom) {
				albedo = to_linear(frame_dark) * 1.3;
				rough = 0.35;
				metal = 0.7;
				nmap = vec3(0.5, 0.5, 1.0);
			} else if (spandrel) {
				albedo = tint * 0.35 + vec3(0.02);
				rough = 0.18;
				metal = 0.6;
				nmap = vec3(0.5, 0.5, 1.0);
			} else {
				inside = true;
			}
			if (!inside) {
				// keine weitere Behandlung
			}
		}
		if (inside) {
			// Rahmen
			bool frame = false;
			if (!curtain) {
				float ex = min(loc.x - wr.x, wr.z - loc.x);
				float ey = min(loc.y - wr.y, wr.w - loc.y);
				frame = min(ex, ey) < frame_w;
				if (arched && loc.y > wr.w - hw) {
					frame = frame || (hw - distance(loc, vec2((wr.x + wr.z) * 0.5, wr.w - hw))) < frame_w;
				}
				// Sprossen / Kaempfer
				float mx = (wr.x + wr.z) * 0.5;
				if (wstyle == 1 || wstyle == 6 || wstyle == 7 || mode == 2) {
					float sp = mode == 2 ? 1.6 : 0.0;
					if (mode == 2) {
						float k = fract((loc.x - wr.x) / max((wr.z - wr.x) / max(floor((wr.z - wr.x) / sp + 0.5), 1.0), 0.1));
						frame = frame || (k < 0.025 || k > 0.975);
					} else {
						frame = frame || abs(loc.x - mx) < 0.035;
					}
				}
				if (wstyle == 0 || wstyle == 1 || wstyle == 2) {
					frame = frame || abs(loc.y - (wr.w - (wr.w - wr.y) * 0.28)) < 0.03;
				}
				if (wstyle == 3) {
					float k2 = fract((loc.x) / 1.35);
					frame = frame || k2 < 0.03;
				}
			}
			if (frame) {
				bool light_frame = (seed > 0.5) == (ikind == 0);
				albedo = to_linear(light_frame ? frame_light : frame_dark);
				rough = 0.45;
				metal = light_frame ? 0.0 : 0.4;
				nmap = vec3(0.5, 0.5, 1.0);
			} else if (mode == 5) {
				// Vernagelt: Bretter oder dunkles, kaputtes Fenster
				if (h1 < 0.6) {
					float board = fract(loc.y * 4.2 + h2);
					albedo = vec3(0.32, 0.22, 0.14) * (0.7 + 0.3 * h2) * (board < 0.08 ? 0.5 : 1.0);
					rough = 0.9;
				} else {
					albedo = vec3(0.02);
					rough = 0.3;
				}
				nmap = vec3(0.5, 0.5, 1.0);
			} else {
				// Glas mit Innenraum
				vec3 up_v = normalize((VIEW_MATRIX * vec4(0.0, 1.0, 0.0, 0.0)).xyz);
				vec3 n_v = normalize(NORMAL);
				vec3 r_v = normalize(cross(up_v, n_v));
				vec3 vd = -VIEW;
				vec3 d = vec3(dot(vd, r_v), dot(vd, up_v), dot(vd, n_v));
				int cell_i = 0;
				if (ikind == 0) {
					cell_i = h2 < 0.3 ? 0 : (h2 < 0.55 ? 1 : (h2 < 0.8 ? 2 : 7));
				} else if (ikind == 1) {
					cell_i = h2 < 0.5 ? 3 : 4;
				} else if (ikind == 2) {
					cell_i = 5;
				} else {
					cell_i = 6;
				}
				float depth = clamp(bay * 1.4, 3.0, 7.0);
				float hb;
				vec2 rloc = loc;
				float rbay = bay;
				if (curtain || mode == 2) {
					// Grossraum: Box ueber 3 Achsen
					rbay = bay * 3.0;
					rloc.x = loc.x + mod(cellv.x, 3.0) * bay;
				}
				vec3 room;
				float hb_tmp = 0.0;
				if (perf_facade_detail >= 2 || (perf_facade_detail == 1 && night > 0.35)) {
					room = interior(rloc, d, rbay, fh, depth, cell_i, hb_tmp);
					hb = hb_tmp;
				} else {
					// Flache Ersatzfarbe statt Strahlverfolgung im Raum
					room = texture(interior_tex, vec2(0.12 + h1 * 0.6, 0.3)).rgb * 0.7;
					hb = 1.0;
				}
				// Vorhaenge / Jalousien
				float blind = (ikind == 0) ? h1 * 0.45 : ((ikind == 1) ? (h2 > 0.7 ? 0.35 : 0.0) : 0.0);
				bool in_blind = (mode == 1) && loc.y > wr.w - (wr.w - wr.y) * blind;
				bool lit;
				float lit_p = lit_fraction;
				if (mode == 2) {
					lit = h3 < 0.95;
					lit_p = shop_lit;
				} else {
					lit = h3 < lit_fraction;
				}
				vec3 lamp = (ikind == 1) ? vec3(0.95, 0.88, 0.74) : vec3(1.0, 0.68, 0.38);
				lamp *= mix(0.75, 1.15, h2);
				float day_bright = (mode == 2) ? 0.35 : 0.16;
				if (curtain) day_bright = 0.07;
				vec3 glass = room * room * day_bright * (1.0 - night * 0.9);
				if (in_blind) {
					vec3 bc = (h2 > 0.5) ? vec3(0.85, 0.82, 0.74) : vec3(0.70, 0.62, 0.52);
					glass = to_linear(bc) * 0.45 * (1.0 - night * 0.85);
					room = to_linear(bc) * 0.7;
				}
				albedo = glass;
				if (curtain) {
					albedo = mix(albedo, to_linear(vcol.rgb) * 0.12, 0.6);
				}
				rough = curtain ? 0.04 : 0.07;
				metal = curtain ? 0.25 : 0.0;
				spec = curtain ? 0.7 : 0.6;
				nmap = vec3(0.5, 0.5, 1.0);
				// leichte Unebenheit des Glases (Spiegelbild-Verzerrung)
				float wob = texture(noise_tex, uvm * 0.31 + seed).b - 0.5;
				nmap.xy += vec2(wob * 0.03, wob * 0.02);
				if (lit && night > 0.01) {
					float strength = (mode == 2) ? 1.5 * lit_p : ((ikind == 1) ? 0.7 : 0.85);
					emis = room * lamp * strength * night;
					// Deckenleuchte als heller Fleck oben im Raum
					emis += lamp * 0.12 * night * smoothstep(0.6, 1.0, loc.y / fh) * (1.0 - float(in_blind));
				} else if (mode == 2) {
					emis = room * 0.08 * (1.0 - night);
				}
				// Laibungsschatten oben und links
				if (!curtain) {
					float sh = smoothstep(0.22, 0.0, wr.w - loc.y) * 0.55 + smoothstep(0.12, 0.0, loc.x - wr.x) * 0.35;
					albedo *= 1.0 - sh;
					emis *= 1.0 - sh * 0.5;
				}
			}
		}
		// Ferne: Mittelwert aus Wand und Fenstern
		if (lod > 0.0) {
			float ratio = curtain ? 0.85 : clamp((wr.z - wr.x) * (wr.w - wr.y) / (bay * fh), 0.0, 0.9);
			if (mode == 5) ratio *= 0.5;
			vec3 avg_glass = curtain ? to_linear(vcol.rgb) * 0.1 : vec3(0.035, 0.04, 0.045);
			vec3 avg_alb = mix(wall_col, avg_glass, ratio);
			vec3 lamp_avg = (ikind == 1) ? vec3(0.8, 0.74, 0.6) : vec3(0.9, 0.58, 0.3);
			float lp = (mode == 2) ? shop_lit * 0.9 : lit_fraction;
			// Hash je Zelle beibehalten, damit ferne Gebaeude ungleichmaessig leuchten
			float far_lit = step(h3, lp);
			vec3 avg_emis = lamp_avg * 0.4 * night * ratio * mix(lp, far_lit, 0.6) * ((mode == 2) ? 2.0 : 1.0);
			if (mode == 5 || mode == 4) avg_emis *= 0.0;
			albedo = mix(albedo, avg_alb, lod);
			emis = mix(emis, avg_emis, lod);
			rough = mix(rough, mix(wall_rough, 0.15, ratio), lod);
			metal = mix(metal, curtain ? 0.25 : wall_metallic, lod);
			nmap = mix(nmap, vec3(0.5, 0.5, 1.0), lod);
		}
	}
	// Naesse
	albedo *= 1.0 - wet * 0.25;
	rough = mix(rough, rough * 0.45, wet);

	ALBEDO = albedo;
	ROUGHNESS = rough;
	METALLIC = metal;
	SPECULAR = spec;
	NORMAL_MAP = nmap;
	NORMAL_MAP_DEPTH = nmap_depth;
	EMISSION = emis;
}
