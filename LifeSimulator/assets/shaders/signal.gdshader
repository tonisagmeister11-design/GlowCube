// Ampel (MultiMesh): Gehaeuse dunkel, Lichtfelder leuchten je nach Zustand.
// CUSTOM0.x = Feld (0 Gehaeuse, 1 rot, 2 gelb, 3 gruen, 4 Fussgaenger rot, 5 Fussgaenger gruen)
// INSTANCE_CUSTOM.r = Fahrzeugsignal (0 rot, 1 gelb, 2 gruen, 3 rot-gelb)
// INSTANCE_CUSTOM.g = Fussgaengersignal (0 rot, 1 gruen)
shader_type spatial;
render_mode cull_back, diffuse_burley, specular_schlick_ggx;

uniform vec3 housing : source_color = vec3(0.10, 0.11, 0.12);
uniform float night = 0.0;

varying float lens;
varying vec4 state;
varying vec4 vcol;

void vertex() {
	lens = CUSTOM0.x;
	state = INSTANCE_CUSTOM;
	vcol = COLOR;
}

void fragment() {
	int l = int(lens + 0.5);
	int s = int(state.r + 0.5);
	bool ped_green = state.g > 0.5;
	if (l == 0) {
		ALBEDO = pow(vcol.rgb, vec3(2.2));
		ROUGHNESS = 0.5;
		METALLIC = 0.3;
	} else {
		vec3 c = l == 1 || l == 4 ? vec3(1.0, 0.08, 0.05) : (l == 2 ? vec3(1.0, 0.55, 0.0) : vec3(0.1, 1.0, 0.45));
		bool on = (l == 1 && (s == 0 || s == 3)) || (l == 2 && (s == 1 || s == 3)) || (l == 3 && s == 2)
			|| (l == 4 && !ped_green) || (l == 5 && ped_green);
		ALBEDO = c * (on ? 0.6 : 0.05);
		ROUGHNESS = 0.15;
		EMISSION = on ? c * (4.0 + night * 6.0) : c * 0.02;
	}
}
