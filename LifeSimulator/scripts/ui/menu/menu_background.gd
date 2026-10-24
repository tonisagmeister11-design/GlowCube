class_name MenuBackground
extends Control
## Hintergrund des Hauptmenues. Verwendet die Live-3D-Kulisse
## (res://scenes/menu/menu_backdrop.tscn), falls vorhanden; sonst das
## Titelbild mit langsamem Ken-Burns-Schwenk, Farbkorrektur und Vignette.
## Der Bildausschnitt meidet das im Titelbild enthaltene Logo, weil das
## Menue sein eigenes Vektorlogo zeigt.

const BACKDROP_SCENE := "res://scenes/menu/menu_backdrop.tscn"
const IMAGE := "res://assets/branding/splash.png"
const FALLBACK_IMAGE := "res://assets/branding/cover.png"
## Ausschnitte (Pixel im 1920x1080-Bild) zwischen denen geschwenkt wird.
const REGION_A := Rect2(690, 420, 1170, 658)
const REGION_B := Rect2(770, 436, 1092, 614)
const PAN_SECONDS := 48.0

const GRADE_SHADER := """
shader_type canvas_item;
uniform float vignette = 0.55;
uniform vec3 tint = vec3(1.02, 0.98, 0.94);
uniform float contrast = 1.06;
uniform float saturation = 1.04;
uniform float darken = 0.86;
void fragment() {
	vec4 c = texture(TEXTURE, UV);
	vec3 col = c.rgb;
	col = (col - 0.5) * contrast + 0.5;
	float l = dot(col, vec3(0.299, 0.587, 0.114));
	col = mix(vec3(l), col, saturation) * tint * darken;
	vec2 d = SCREEN_UV - vec2(0.55, 0.45);
	float v = smoothstep(0.35, 1.05, length(d * vec2(1.25, 1.0)));
	col *= 1.0 - v * vignette;
	COLOR = vec4(col, 1.0);
}
"""

var use_backdrop := true
var _tex: Texture2D
var _t := 0.0
var _image: Control
var _backdrop: Node


func _ready() -> void:
	set_anchors_preset(PRESET_FULL_RECT)
	mouse_filter = MOUSE_FILTER_IGNORE
	var dark := ColorRect.new()
	dark.color = UITheme.BG
	dark.set_anchors_preset(PRESET_FULL_RECT)
	dark.mouse_filter = MOUSE_FILTER_IGNORE
	add_child(dark)
	if use_backdrop and ResourceLoader.exists(BACKDROP_SCENE):
		var ps: PackedScene = load(BACKDROP_SCENE)
		if ps:
			_backdrop = ps.instantiate()
			add_child(_backdrop)
			if _backdrop is Control:
				(_backdrop as Control).set_anchors_preset(PRESET_FULL_RECT)
				(_backdrop as Control).mouse_filter = MOUSE_FILTER_IGNORE
	if _backdrop == null:
		_tex = load(IMAGE) if ResourceLoader.exists(IMAGE) else load(FALLBACK_IMAGE)
		_image = Control.new()
		_image.set_anchors_preset(PRESET_FULL_RECT)
		_image.mouse_filter = MOUSE_FILTER_IGNORE
		var mat := ShaderMaterial.new()
		var sh := Shader.new()
		sh.code = GRADE_SHADER
		mat.shader = sh
		_image.material = mat
		_image.draw.connect(_draw_image)
		add_child(_image)


func has_live_backdrop() -> bool:
	return _backdrop != null


func _process(delta: float) -> void:
	if _image:
		_t += delta
		_image.queue_redraw()


func _draw_image() -> void:
	if _tex == null:
		return
	var tex_size := _tex.get_size()
	var sx := tex_size.x / 1920.0
	var k := 0.5 - 0.5 * cos(_t / PAN_SECONDS * TAU)
	var r := Rect2(REGION_A.position.lerp(REGION_B.position, k), REGION_A.size.lerp(REGION_B.size, k))
	r = Rect2(r.position * sx, r.size * sx)
	# Ausschnitt an das Seitenverhaeltnis des Bildschirms anpassen (bedeckend)
	var dest := _image.size
	var aspect := dest.x / maxf(1.0, dest.y)
	var ra := r.size.x / r.size.y
	if aspect > ra:
		var h := r.size.x / aspect
		r.position.y += (r.size.y - h) * 0.5
		r.size.y = h
	else:
		var w := r.size.y * aspect
		r.position.x += (r.size.x - w) * 0.5
		r.size.x = w
	_image.draw_texture_rect_region(_tex, Rect2(Vector2.ZERO, dest), r)
