class_name UIKit
extends RefCounted
## Baukasten fuer alle Bildschirme: Icons, Labels, Buttons, Karten,
## Formatierung (Geld, Zeit) und kleine Animationen. Alles nutzt die
## Typvarianten aus UITheme, damit das Erscheinungsbild einheitlich bleibt.

const ICON_DIR := "res://assets/icons/"
const SOUND_DIR := "res://assets/audio/ui/"

const LIFE_STAGES := {
	"baby": "Babyalter", "toddler": "Kleinkindalter", "child": "Kindheit", "teen": "Jugend",
	"young_adult": "Junges Erwachsenenalter", "adult": "Erwachsenenalter",
	"older_adult": "Reifes Alter", "senior": "Seniorenalter",
}
const BODY_LABELS := {"female": "Weiblich", "male": "Männlich"}
const MODE_ICONS := {"life": "heart", "sandbox": "sliders", "free": "leaf"}
const EDUCATION_LABELS := {
	"none": "Kein Abschluss", "school": "Schulabschluss", "vocational": "Berufsausbildung",
	"bachelor": "Bachelor", "master": "Master", "doctor": "Promotion",
}
const SKILL_LABELS := {
	"cooking": "Kochen", "fitness": "Fitness", "charisma": "Charisma", "logic": "Logik",
	"creativity": "Kreativität", "handiwork": "Handwerk", "driving": "Fahren", "business": "Wirtschaft",
	"programming": "Programmieren", "medicine": "Medizin", "law": "Recht", "crime": "Kriminelles Geschick",
	"teaching": "Pädagogik", "music": "Musik", "sport": "Sport", "communication": "Kommunikation",
}

static var _icons := {}
static var _sound_player: AudioStreamPlayer
## Optionaler Haken fuer Oberflaechengeraeusche: Callable(name: String).
## Ist er gesetzt, ersetzt er die eingebaute Wiedergabe aus SOUND_DIR.
static var sound_hook: Callable


# ================================================================ Icons

static func icon(name: String) -> Texture2D:
	if _icons.has(name):
		return _icons[name]
	var p := ICON_DIR + name + ".svg"
	var tex: Texture2D = load(p) if ResourceLoader.exists(p) else null
	if tex == null and name != "info":
		tex = icon("info")
	_icons[name] = tex
	return tex


static func has_icon(name: String) -> bool:
	return ResourceLoader.exists(ICON_DIR + name + ".svg")


static func icon_rect(name: String, size := 24.0, color := Color.WHITE) -> TextureRect:
	var t := TextureRect.new()
	t.texture = icon(name)
	t.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	t.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	t.custom_minimum_size = Vector2(size, size)
	t.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	t.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	t.self_modulate = color
	t.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return t


## Runder Icon-Hintergrund (z.B. Kategorie-Marker, App-Symbole).
static func icon_badge(name: String, color: Color, size := 44.0, icon_scale := 0.52, solid := false) -> PanelContainer:
	var p := PanelContainer.new()
	var sb := StyleBoxFlat.new()
	sb.bg_color = color if solid else Color(color, 0.18)
	sb.set_corner_radius_all(int(size * 0.5))
	sb.border_color = Color(color, 0.55)
	sb.set_border_width_all(0 if solid else 1)
	sb.anti_aliasing = true
	p.add_theme_stylebox_override("panel", sb)
	p.custom_minimum_size = Vector2(size, size)
	p.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	p.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	p.mouse_filter = Control.MOUSE_FILTER_IGNORE
	p.add_child(icon_rect(name, size * icon_scale, Color.WHITE if solid else color.lightened(0.25)))
	return p


# ================================================================ Bausteine

static func label(text: String, variation := "", wrap := false) -> Label:
	var l := Label.new()
	l.text = text
	if variation != "":
		l.theme_type_variation = variation
	if wrap:
		l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		l.custom_minimum_size.x = 40
	l.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return l


static func button(text: String, icon_name := "", variation := "", min_w := 0.0) -> Button:
	var b := Button.new()
	b.text = text
	if icon_name != "":
		b.icon = icon(icon_name)
	if variation != "":
		b.theme_type_variation = variation
	b.focus_mode = Control.FOCUS_ALL
	if min_w > 0:
		b.custom_minimum_size.x = min_w
	hover_fx(b)
	return b


static func icon_button(icon_name: String, tooltip := "", size := 44.0) -> Button:
	var b := Button.new()
	b.icon = icon(icon_name)
	b.theme_type_variation = "IconButton"
	b.tooltip_text = tooltip
	b.custom_minimum_size = Vector2(size, size)
	b.icon_alignment = HORIZONTAL_ALIGNMENT_CENTER
	b.expand_icon = false
	b.focus_mode = Control.FOCUS_ALL
	hover_fx(b)
	return b


static func chip(text: String, icon_name := "", toggle := true) -> Button:
	var b := button(text, icon_name, "ChipButton")
	b.toggle_mode = toggle
	return b


static func hbox(sep := 10) -> HBoxContainer:
	var h := HBoxContainer.new()
	h.add_theme_constant_override("separation", sep)
	return h


static func vbox(sep := 10) -> VBoxContainer:
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", sep)
	return v


static func margin(child: Control, l := 0, t := 0, r := 0, b := 0) -> MarginContainer:
	var m := MarginContainer.new()
	m.add_theme_constant_override("margin_left", l)
	m.add_theme_constant_override("margin_top", t)
	m.add_theme_constant_override("margin_right", r)
	m.add_theme_constant_override("margin_bottom", b)
	if child:
		m.add_child(child)
	return m


static func card(variation := "CardPanel") -> PanelContainer:
	var p := PanelContainer.new()
	p.theme_type_variation = variation
	return p


static func spacer(expand_h := true, expand_v := false, min_size := Vector2.ZERO) -> Control:
	var c := Control.new()
	if expand_h:
		c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	if expand_v:
		c.size_flags_vertical = Control.SIZE_EXPAND_FILL
	c.custom_minimum_size = min_size
	c.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return c


static func separator() -> HSeparator:
	return HSeparator.new()


static func scroll(content: Control, horizontal := false) -> ScrollContainer:
	var s := ScrollContainer.new()
	s.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_AUTO if horizontal else ScrollContainer.SCROLL_MODE_DISABLED
	s.size_flags_vertical = Control.SIZE_EXPAND_FILL
	s.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	content.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	s.add_child(content)
	return s


## Tastenkappe, z.B. "E" oder "Linke Maustaste".
static func keycap(text: String) -> PanelContainer:
	var p := PanelContainer.new()
	p.theme_type_variation = "KeyCapPanel"
	p.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	p.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	var l := label(_key_text(text), "KeyCapLabel")
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.custom_minimum_size.x = 18
	p.add_child(l)
	p.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return p


static func _key_text(t: String) -> String:
	var map := {"Escape": "Esc", "Space": "Leertaste", "Shift": "Umschalt", "Ctrl": "Strg", "Period": ".",
		"Comma": ",", "Tab": "Tab", "Enter": "Eingabe", "BackSpace": "Rücktaste", "Left": "Pfeil links",
		"Right": "Pfeil rechts", "Up": "Pfeil oben", "Down": "Pfeil unten", "Delete": "Entf",
		"Insert": "Einfg", "PageUp": "Bild auf", "PageDown": "Bild ab", "Alt": "Alt"}
	return map.get(t, t)


## Tastenkappe fuer die aktuelle Belegung einer Aktion.
static func action_keycap(action: String) -> PanelContainer:
	return keycap(Settings.binding_label(action))


## Kopfzeile eines Abschnitts mit optionalem Icon.
static func section(text: String, icon_name := "") -> HBoxContainer:
	var h := hbox(10)
	if icon_name != "":
		h.add_child(icon_rect(icon_name, 22, UITheme.ACCENT))
	h.add_child(label(text, "TitleLabel"))
	return h


static func eyebrow(text: String) -> Label:
	return label(text.to_upper(), "EyebrowLabel")


## Zeile "Bezeichnung ...... Wert".
static func stat_row(name: String, value: String, icon_name := "", value_color := Color(0, 0, 0, 0)) -> HBoxContainer:
	var h := hbox(10)
	if icon_name != "":
		h.add_child(icon_rect(icon_name, 20, UITheme.TEXT_MUTED))
	var l := label(name, "SubtitleLabel")
	l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	h.add_child(l)
	var v := label(value, "ValueLabel")
	v.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	if value_color.a > 0:
		v.add_theme_color_override("font_color", value_color)
	h.add_child(v)
	return h


## Leerer Zustand: Icon, Titel, Erklaerung.
static func empty_state(icon_name: String, title: String, text := "") -> VBoxContainer:
	var v := vbox(10)
	v.alignment = BoxContainer.ALIGNMENT_CENTER
	v.size_flags_vertical = Control.SIZE_EXPAND_FILL
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var b := icon_badge(icon_name, UITheme.TEXT_MUTED, 72, 0.46)
	v.add_child(b)
	var t := label(title, "TitleLabel")
	t.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(t)
	if text != "":
		var s := label(text, "SubtitleLabel", true)
		s.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		s.custom_minimum_size.x = 280
		v.add_child(s)
	return v


## Kleiner farbiger Hinweis-Chip (nicht klickbar).
static func pill(text: String, color := UITheme.ACCENT, icon_name := "") -> PanelContainer:
	var p := PanelContainer.new()
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(color, 0.16)
	sb.border_color = Color(color, 0.45)
	sb.set_border_width_all(1)
	sb.set_corner_radius_all(999)
	sb.content_margin_left = 10
	sb.content_margin_right = 10
	sb.content_margin_top = 3
	sb.content_margin_bottom = 3
	p.add_theme_stylebox_override("panel", sb)
	p.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	p.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	p.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var h := hbox(5)
	if icon_name != "":
		h.add_child(icon_rect(icon_name, 15, color.lightened(0.2)))
	var l := label(text, "CaptionLabel")
	l.add_theme_color_override("font_color", color.lightened(0.35))
	h.add_child(l)
	p.add_child(h)
	return p


## Flacher Balken (Beduerfnisse, Fortschritt).
static func bar(value: float, color: Color, height := 8.0, max_value := 100.0) -> ProgressBar:
	var pb := ProgressBar.new()
	pb.max_value = max_value
	pb.value = value
	pb.show_percentage = false
	pb.custom_minimum_size = Vector2(60, height)
	pb.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	pb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var f := StyleBoxFlat.new()
	f.bg_color = color
	f.set_corner_radius_all(999)
	pb.add_theme_stylebox_override("fill", f)
	var bg := StyleBoxFlat.new()
	bg.bg_color = Color(color, 0.16)
	bg.set_corner_radius_all(999)
	pb.add_theme_stylebox_override("background", bg)
	pb.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return pb


## Knoten, in den Modale eingehaengt werden (Gruppe "ui_root": Hauptmenue, Spiel-UI).
static func modal_host(from: Node) -> Node:
	var n := from
	while n:
		if n.is_in_group("ui_root"):
			return n
		n = n.get_parent()
	var tree := from.get_tree()
	if tree and tree.current_scene:
		return tree.current_scene
	return from


static func clear(node: Node) -> void:
	for c in node.get_children():
		node.remove_child(c)
		c.queue_free()


## Vollflaechiger Abdunkler (fuer Modale und Panels).
static func dim(alpha := 0.6) -> ColorRect:
	var c := ColorRect.new()
	c.color = Color(0.01, 0.02, 0.05, alpha)
	c.set_anchors_preset(Control.PRESET_FULL_RECT)
	c.mouse_filter = Control.MOUSE_FILTER_STOP
	return c


# ================================================================ Formatierung

## 1234.5 -> "1.234,50 €"
static func money(v: float, decimals := 2) -> String:
	var neg := v < 0.0
	var a := absf(v)
	var whole := int(floor(a + (0.0 if decimals > 0 else 0.5)))
	var s := group(whole)
	if decimals > 0:
		var frac := int(round((a - floor(a)) * pow(10.0, decimals)))
		if frac >= int(pow(10.0, decimals)):
			frac = 0
			s = group(whole + 1)
		s += "," + str(frac).pad_zeros(decimals)
	return ("-" if neg else "") + s + " €"


## Kompakt fuer grosse Betraege: "12.400 €", "1,2 Mio. €"
static func money_short(v: float) -> String:
	var a := absf(v)
	if a >= 1000000.0:
		return ("-" if v < 0 else "") + ("%.1f" % (a / 1000000.0)).replace(".", ",") + " Mio. €"
	if a >= 10000.0:
		return money(v, 0)
	return money(v, 2)


## 1234567 -> "1.234.567"
static func group(n: int) -> String:
	var s := str(absi(n))
	var out := ""
	var c := 0
	for i in range(s.length() - 1, -1, -1):
		out = s[i] + out
		c += 1
		if c % 3 == 0 and i > 0:
			out = "." + out
	return ("-" if n < 0 else "") + out


static func decimal(v: float, digits := 1) -> String:
	return (("%." + str(digits) + "f") % v).replace(".", ",")


## Sekunden -> "3:07 Std."
static func play_time(seconds: float) -> String:
	var m := int(seconds / 60.0)
	return "%d:%02d Std." % [m / 60, m % 60]


static func distance(m: float) -> String:
	if m >= 1000.0:
		return decimal(m / 1000.0, 1) + " km"
	return "%d m" % int(round(m / 10.0) * 10.0)


## Unix-Zeit -> "23.10.2026, 14:05"
static func datetime(unix: float) -> String:
	if unix <= 0:
		return "-"
	var bias := Time.get_time_zone_from_system().get("bias", 0) as int
	var d := Time.get_datetime_dict_from_unix_time(int(unix) + bias * 60)
	return "%02d.%02d.%04d, %02d:%02d" % [d.day, d.month, d.year, d.hour, d.minute]


## Relative Zeitangabe: "vor 5 Minuten", "gestern" ...
static func ago(unix: float) -> String:
	var now := Time.get_unix_time_from_system()
	var d := now - unix
	if d < 60:
		return "gerade eben"
	if d < 3600:
		var m := int(d / 60)
		return "vor %d %s" % [m, "Minute" if m == 1 else "Minuten"]
	if d < 86400:
		var h := int(d / 3600)
		return "vor %d %s" % [h, "Stunde" if h == 1 else "Stunden"]
	var days := int(d / 86400)
	if days == 1:
		return "gestern"
	return "vor %d Tagen" % days


static func years(age: float) -> String:
	var y := int(floor(age))
	return "%d %s" % [y, "Jahr" if y == 1 else "Jahre"]


static func life_stage_for_age(age: float) -> String:
	if age < 2: return "baby"
	if age < 4: return "toddler"
	if age < 12: return "child"
	if age < 18: return "teen"
	if age < 30: return "young_adult"
	if age < 50: return "adult"
	if age < 65: return "older_adult"
	return "senior"


static func life_stage_label(stage: String) -> String:
	return LIFE_STAGES.get(stage, stage.capitalize())


static func initials(name: String) -> String:
	var parts := name.strip_edges().split(" ", false)
	if parts.is_empty():
		return "?"
	var s := parts[0].substr(0, 1)
	if parts.size() > 1:
		s += parts[parts.size() - 1].substr(0, 1)
	return s.to_upper()


static func mode_label(mode: String) -> String:
	return GameRules.MODES.get(mode, {}).get("label", mode)


static func skill_label(k: String) -> String:
	return SKILL_LABELS.get(k, k.capitalize())


# ================================================================ Sicherer Zugriff

## Eigenschaft eines Objekts lesen, ohne Fehler bei fehlenden Feldern.
static func pget(obj, field: String, default = null):
	if obj == null:
		return default
	if obj is Dictionary:
		return obj.get(field, default)
	if obj is Object:
		var v = obj.get(field)
		return default if v == null else v
	return default


## Methode aufrufen, falls vorhanden; sonst default.
static func pcall(obj, method: String, args := [], default = null):
	if obj == null or not (obj is Object) or not obj.has_method(method):
		return default
	return obj.callv(method, args)


## Laedt ein Skript zur Laufzeit (fuer Kataloge anderer Module, die evtl. fehlen).
static func load_script(path: String) -> Script:
	if not ResourceLoader.exists(path):
		return null
	return load(path)


## Konstante eines optionalen Katalogs (z.B. GoodsCatalog.GOODS).
static func catalog(path: String, const_name: String) -> Dictionary:
	var s := load_script(path)
	if s == null:
		return {}
	var c: Dictionary = s.get_script_constant_map()
	var v = c.get(const_name, {})
	return v if v is Dictionary else {}


# ================================================================ Animation / Klang

## Einblenden (Transparenz + leichter Versatz fuer frei positionierte Controls).
static func appear(c: Control, delay := 0.0, offset := Vector2(0, 14), dur := 0.32) -> void:
	if not c.is_inside_tree():
		return
	c.modulate.a = 0.0
	var tw := c.create_tween().set_parallel(true).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	tw.tween_property(c, "modulate:a", 1.0, dur).set_delay(delay)
	if offset != Vector2.ZERO and not (c.get_parent() is Container):
		var target := c.position
		c.position = target + offset
		tw.tween_property(c, "position", target, dur).set_delay(delay)


## Gestaffeltes Einblenden aller Kinder eines Containers.
static func stagger(container: Control, step := 0.035, start := 0.0) -> void:
	var i := 0
	for ch in container.get_children():
		if ch is Control and ch.visible:
			appear(ch, start + step * i, Vector2.ZERO, 0.26)
			i += 1


## Dezente Hover-Rueckmeldung (Helligkeit) und Klang-Haken.
static func hover_fx(b: BaseButton) -> void:
	b.mouse_entered.connect(func() -> void:
		if not b.disabled:
			sound("hover"))
	b.pressed.connect(func() -> void: sound("click"))


static func sound(name: String) -> void:
	if sound_hook.is_valid():
		sound_hook.call(name)
		return
	var p := SOUND_DIR + name + ".ogg"
	if not ResourceLoader.exists(p):
		return
	var loop := Engine.get_main_loop()
	if not (loop is SceneTree):
		return
	if _sound_player == null or not is_instance_valid(_sound_player):
		_sound_player = AudioStreamPlayer.new()
		_sound_player.bus = "SFX"
		loop.root.add_child(_sound_player)
	_sound_player.stream = load(p)
	_sound_player.play()


## Verlaufsflaeche (z.B. Abdunklung hinter Menues). from/to in 0..1 (UV).
static func gradient_rect(colors: PackedColorArray, offsets: PackedFloat32Array, from := Vector2(0, 0),
		to := Vector2(1, 0)) -> TextureRect:
	var g := Gradient.new()
	g.colors = colors
	g.offsets = offsets
	var gt := GradientTexture2D.new()
	gt.gradient = g
	gt.fill_from = from
	gt.fill_to = to
	gt.width = 256
	gt.height = 256
	var t := TextureRect.new()
	t.texture = gt
	t.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	t.stretch_mode = TextureRect.STRETCH_SCALE
	t.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return t


## Weichzeichner fuer den Hintergrund hinter Menues (Bildschirmtextur).
const BLUR_SHADER := """
shader_type canvas_item;
uniform sampler2D screen_tex : hint_screen_texture, filter_linear;
uniform float radius = 10.0;
uniform vec4 tint : source_color = vec4(0.02, 0.035, 0.07, 0.55);
void fragment() {
	vec2 px = SCREEN_PIXEL_SIZE * radius;
	vec3 acc = vec3(0.0);
	float wsum = 0.0;
	for (int x = -3; x <= 3; x++) {
		for (int y = -3; y <= 3; y++) {
			vec2 o = vec2(float(x), float(y));
			float w = exp(-dot(o, o) / 6.0);
			acc += texture(screen_tex, SCREEN_UV + o * px * 0.5).rgb * w;
			wsum += w;
		}
	}
	vec3 col = acc / wsum;
	COLOR = vec4(mix(col, tint.rgb, tint.a), 1.0);
}
"""


static var _blur_shader: Shader


static func blur_rect(radius := 10.0, tint := Color(0.02, 0.035, 0.07, 0.55)) -> ColorRect:
	var c := ColorRect.new()
	if _blur_shader == null:
		_blur_shader = Shader.new()
		_blur_shader.code = BLUR_SHADER
	var mat := ShaderMaterial.new()
	mat.shader = _blur_shader
	mat.set_shader_parameter("radius", radius)
	mat.set_shader_parameter("tint", tint)
	c.material = mat
	c.set_anchors_preset(Control.PRESET_FULL_RECT)
	c.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return c
