class_name UITheme
extends RefCounted
## Design-System von Life Simulator: Farb-/Abstands-Token und der Aufbau des
## projektweiten Themes (res://assets/ui/theme.tres).
##
## Neu erzeugen (nach Aenderungen an Token oder Stilen):
##   godot --headless --path . --script res://scripts/ui/build_theme.gd
## Alle Bildschirme nutzen Typvarianten (theme_type_variation) statt eigener
## Farben; Laufzeit-Code liest Farben ueber die Konstanten unten.

const THEME_PATH := "res://assets/ui/theme.tres"
const UI_DIR := "res://assets/ui/"
const FONT_UI := "res://assets/fonts/Inter-Variable.ttf"
const FONT_HEAD := "res://assets/fonts/Montserrat-Variable.ttf"

# ---------------------------------------------------------------- Farben
const BG := Color("#070b14")
const SURFACE := Color(0.047, 0.067, 0.114, 0.96)
const SURFACE_2 := Color(0.075, 0.102, 0.161, 0.97)
const GLASS := Color(0.035, 0.055, 0.105, 0.78)
const CARD := Color(1, 1, 1, 0.045)
const CARD_HOVER := Color(1, 1, 1, 0.085)
const BORDER := Color(1, 1, 1, 0.09)
const BORDER_STRONG := Color(1, 1, 1, 0.18)
const TEXT := Color("#eef3fb")
const TEXT_MUTED := Color("#a9b5ca")
const TEXT_FAINT := Color("#74829a")
const ACCENT := Color("#2fa8ff")
const ACCENT_HOVER := Color("#5cbdff")
const ACCENT_PRESSED := Color("#178ae6")
const ACCENT_SOFT := Color(0.184, 0.659, 1.0, 0.18)
const WARM := Color("#ff9f45")
const WARM_SOFT := Color(1.0, 0.62, 0.27, 0.18)
const SUCCESS := Color("#3ddc97")
const WARNING := Color("#ffc145")
const DANGER := Color("#ff5d6c")
const MONEY := Color("#6fe3a3")

## Farben der Beduerfnisse (HUD, Telefon, Shop-Effekte).
const NEED_COLORS := {
	"health": Color("#ff5d6c"), "hunger": Color("#ff9f43"), "thirst": Color("#4cc9f0"),
	"energy": Color("#ffd166"), "hygiene": Color("#7bdff2"), "social": Color("#f78fb3"),
	"fun": Color("#b388ff"), "mood": Color("#3ddc97"),
}
const NEED_LABELS := {
	"health": "Gesundheit", "hunger": "Hunger", "thirst": "Durst", "energy": "Energie",
	"hygiene": "Hygiene", "social": "Soziales", "fun": "Spaß", "mood": "Stimmung",
}
const NEED_ICONS := {
	"health": "health", "hunger": "hunger", "thirst": "thirst", "energy": "energy",
	"hygiene": "hygiene", "social": "social", "fun": "fun", "mood": "mood",
}
const TOAST_COLORS := {
	"info": ACCENT, "success": SUCCESS, "warning": WARNING, "danger": DANGER, "money": MONEY,
}

# ---------------------------------------------------------------- Masse
const RADIUS_S := 8
const RADIUS_M := 12
const RADIUS_L := 18
const RADIUS_XL := 28
const SPACE_XS := 4
const SPACE_S := 8
const SPACE_M := 14
const SPACE_L := 22
const SPACE_XL := 36

const SIZE_BODY := 19
const SIZE_CAPTION := 15
const SIZE_SUBTITLE := 18
const SIZE_TITLE := 25
const SIZE_HEADER := 46
const SIZE_VALUE := 20

## Typvarianten laut ARCHITECTURE.md plus interne Ergaenzungen.
const VARIATIONS := {
	"HeaderLabel": "Label", "TitleLabel": "Label", "SubtitleLabel": "Label", "CaptionLabel": "Label",
	"ValueLabel": "Label", "EyebrowLabel": "Label", "KeyCapLabel": "Label", "BigValueLabel": "Label",
	"CardPanel": "PanelContainer", "GlassPanel": "PanelContainer", "KeyCapPanel": "PanelContainer",
	"SurfacePanel": "PanelContainer", "PillPanel": "PanelContainer", "ToastPanel": "PanelContainer",
	"AccentButton": "Button", "GhostButton": "Button", "ChipButton": "Button", "DangerButton": "Button",
	"MainMenuButton": "Button", "IconButton": "Button", "CardButton": "Button",
}

static var _fonts := {}


# ================================================================ Schriften

## FontVariation mit Gewicht (100..900). head = Montserrat statt Inter.
static func font(weight: int, head := false, tabular := false, italic := false) -> FontVariation:
	var key := "%d:%s:%s:%s" % [weight, head, tabular, italic]
	if _fonts.has(key):
		return _fonts[key]
	var fv := FontVariation.new()
	fv.base_font = load(FONT_HEAD if head else FONT_UI)
	var ts := TextServerManager.get_primary_interface()
	var coords := {ts.name_to_tag("wght"): weight}
	if not head:
		coords[ts.name_to_tag("opsz")] = 18
	fv.variation_opentype = coords
	if tabular:
		fv.opentype_features = {"tnum": 1}
	if italic:
		fv.variation_transform = Transform2D(Vector2(1, 0), Vector2(0.2, 1), Vector2.ZERO)
	_fonts[key] = fv
	return fv


# ================================================================ StyleBoxes

static func box(bg: Color, radius := RADIUS_M, border := Color(0, 0, 0, 0), border_w := 0,
		margin_h := 16.0, margin_v := 10.0) -> StyleBoxFlat:
	var s := StyleBoxFlat.new()
	s.bg_color = bg
	s.set_corner_radius_all(radius)
	s.corner_detail = 8
	s.anti_aliasing = true
	if border_w > 0:
		s.border_color = border
		s.set_border_width_all(border_w)
	s.content_margin_left = margin_h
	s.content_margin_right = margin_h
	s.content_margin_top = margin_v
	s.content_margin_bottom = margin_v
	return s


static func shadowed(s: StyleBoxFlat, size := 18, alpha := 0.35, offset := Vector2(0, 6)) -> StyleBoxFlat:
	s.shadow_color = Color(0, 0, 0, alpha)
	s.shadow_size = size
	s.shadow_offset = offset
	return s


static func focus_box(radius := RADIUS_M) -> StyleBoxFlat:
	var s := StyleBoxFlat.new()
	s.draw_center = false
	s.set_corner_radius_all(radius + 3)
	s.border_color = Color(ACCENT, 0.95)
	s.set_border_width_all(2)
	s.expand_margin_left = 3
	s.expand_margin_right = 3
	s.expand_margin_top = 3
	s.expand_margin_bottom = 3
	s.corner_detail = 8
	return s


static func empty(margin := 0.0) -> StyleBoxEmpty:
	var s := StyleBoxEmpty.new()
	s.content_margin_left = margin
	s.content_margin_right = margin
	s.content_margin_top = margin
	s.content_margin_bottom = margin
	return s


static func _tex(name: String) -> Texture2D:
	var p := UI_DIR + name + ".svg"
	return load(p) if ResourceLoader.exists(p) else null


# ================================================================ Theme

static func build() -> Theme:
	var t := Theme.new()
	t.default_font = font(440)
	t.default_font_size = SIZE_BODY
	for v in VARIATIONS:
		t.set_type_variation(v, VARIATIONS[v])
	_labels(t)
	_buttons(t)
	_panels(t)
	_inputs(t)
	_toggles(t)
	_ranges(t)
	_lists(t)
	_misc(t)
	return t


static func _labels(t: Theme) -> void:
	t.set_color("font_color", "Label", TEXT)
	t.set_color("font_shadow_color", "Label", Color(0, 0, 0, 0))
	t.set_constant("line_spacing", "Label", 3)
	t.set_font("font", "HeaderLabel", font(800, true))
	t.set_font_size("font_size", "HeaderLabel", SIZE_HEADER)
	t.set_color("font_color", "HeaderLabel", Color.WHITE)
	t.set_font("font", "TitleLabel", font(700, true))
	t.set_font_size("font_size", "TitleLabel", SIZE_TITLE)
	t.set_color("font_color", "TitleLabel", Color.WHITE)
	t.set_font("font", "SubtitleLabel", font(420))
	t.set_font_size("font_size", "SubtitleLabel", SIZE_SUBTITLE)
	t.set_color("font_color", "SubtitleLabel", TEXT_MUTED)
	t.set_constant("line_spacing", "SubtitleLabel", 5)
	t.set_font("font", "CaptionLabel", font(500))
	t.set_font_size("font_size", "CaptionLabel", SIZE_CAPTION)
	t.set_color("font_color", "CaptionLabel", TEXT_FAINT)
	t.set_font("font", "ValueLabel", font(620, false, true))
	t.set_font_size("font_size", "ValueLabel", SIZE_VALUE)
	t.set_color("font_color", "ValueLabel", TEXT)
	t.set_font("font", "BigValueLabel", font(700, true, true))
	t.set_font_size("font_size", "BigValueLabel", 34)
	t.set_color("font_color", "BigValueLabel", Color.WHITE)
	var eyebrow := font(720)
	t.set_font("font", "EyebrowLabel", eyebrow)
	t.set_font_size("font_size", "EyebrowLabel", 14)
	t.set_color("font_color", "EyebrowLabel", ACCENT)
	t.set_font("font", "KeyCapLabel", font(700, false, true))
	t.set_font_size("font_size", "KeyCapLabel", 15)
	t.set_color("font_color", "KeyCapLabel", TEXT)


static func _button_set(t: Theme, type: String, normal: StyleBox, hover: StyleBox, pressed: StyleBox,
		disabled: StyleBox, fc: Color, fc_hover: Color, fc_pressed: Color, radius := RADIUS_M) -> void:
	t.set_stylebox("normal", type, normal)
	t.set_stylebox("hover", type, hover)
	t.set_stylebox("pressed", type, pressed)
	t.set_stylebox("hover_pressed", type, pressed)
	t.set_stylebox("disabled", type, disabled)
	t.set_stylebox("focus", type, focus_box(radius))
	t.set_color("font_color", type, fc)
	t.set_color("font_hover_color", type, fc_hover)
	t.set_color("font_pressed_color", type, fc_pressed)
	t.set_color("font_hover_pressed_color", type, fc_pressed)
	t.set_color("font_focus_color", type, fc_hover)
	t.set_color("font_disabled_color", type, Color(fc, 0.35))
	t.set_color("icon_normal_color", type, fc)
	t.set_color("icon_hover_color", type, fc_hover)
	t.set_color("icon_pressed_color", type, fc_pressed)
	t.set_color("icon_hover_pressed_color", type, fc_pressed)
	t.set_color("icon_focus_color", type, fc_hover)
	t.set_color("icon_disabled_color", type, Color(fc, 0.35))


static func _buttons(t: Theme) -> void:
	# Standard-Button
	_button_set(t, "Button",
		box(Color(1, 1, 1, 0.065), RADIUS_M, Color(1, 1, 1, 0.10), 1, 20, 11),
		box(Color(1, 1, 1, 0.12), RADIUS_M, Color(ACCENT, 0.55), 1, 20, 11),
		box(Color(ACCENT, 0.26), RADIUS_M, ACCENT, 1, 20, 11),
		box(Color(1, 1, 1, 0.03), RADIUS_M, Color(1, 1, 1, 0.05), 1, 20, 11),
		TEXT, Color.WHITE, Color.WHITE)
	t.set_font("font", "Button", font(560))
	t.set_font_size("font_size", "Button", 18)
	t.set_constant("h_separation", "Button", 10)
	t.set_constant("icon_max_width", "Button", 24)
	t.set_constant("outline_size", "Button", 0)
	# Hauptaktion
	_button_set(t, "AccentButton",
		shadowed(box(ACCENT, RADIUS_M, Color(1, 1, 1, 0.18), 1, 26, 13), 14, 0.0),
		shadowed(box(ACCENT_HOVER, RADIUS_M, Color(1, 1, 1, 0.35), 1, 26, 13), 18, 0.0),
		box(ACCENT_PRESSED, RADIUS_M, Color(1, 1, 1, 0.2), 1, 26, 13),
		box(Color(ACCENT, 0.28), RADIUS_M, Color(0, 0, 0, 0), 0, 26, 13),
		Color.WHITE, Color.WHITE, Color(1, 1, 1, 0.92))
	for st in ["normal", "hover"]:
		var sb: StyleBoxFlat = t.get_stylebox(st, "AccentButton")
		sb.shadow_color = Color(ACCENT, 0.32 if st == "normal" else 0.5)
		sb.shadow_offset = Vector2(0, 4)
	t.set_font("font", "AccentButton", font(680))
	t.set_font_size("font_size", "AccentButton", 19)
	# Nebenaktion
	_button_set(t, "GhostButton",
		box(Color(1, 1, 1, 0.0), RADIUS_M, Color(1, 1, 1, 0.0), 0, 16, 10),
		box(Color(1, 1, 1, 0.07), RADIUS_M, Color(1, 1, 1, 0.0), 0, 16, 10),
		box(Color(1, 1, 1, 0.12), RADIUS_M, Color(1, 1, 1, 0.0), 0, 16, 10),
		box(Color(1, 1, 1, 0.0), RADIUS_M, Color(1, 1, 1, 0.0), 0, 16, 10),
		TEXT_MUTED, Color.WHITE, Color.WHITE)
	t.set_font("font", "GhostButton", font(560))
	# Chips (Umschalter)
	var r := 999
	_button_set(t, "ChipButton",
		box(Color(1, 1, 1, 0.06), r, Color(1, 1, 1, 0.12), 1, 16, 8),
		box(Color(1, 1, 1, 0.11), r, Color(1, 1, 1, 0.28), 1, 16, 8),
		box(Color(ACCENT, 0.22), r, ACCENT, 1, 16, 8),
		box(Color(1, 1, 1, 0.025), r, Color(1, 1, 1, 0.06), 1, 16, 8),
		TEXT_MUTED, Color.WHITE, Color.WHITE, 18)
	t.set_font("font", "ChipButton", font(580))
	t.set_font_size("font_size", "ChipButton", 16)
	t.set_constant("icon_max_width", "ChipButton", 20)
	t.set_constant("h_separation", "ChipButton", 8)
	# Destruktiv
	_button_set(t, "DangerButton",
		box(Color(DANGER, 0.14), RADIUS_M, Color(DANGER, 0.45), 1, 20, 11),
		box(DANGER, RADIUS_M, Color(1, 1, 1, 0.25), 1, 20, 11),
		box(DANGER.darkened(0.2), RADIUS_M, DANGER, 1, 20, 11),
		box(Color(DANGER, 0.06), RADIUS_M, Color(DANGER, 0.15), 1, 20, 11),
		Color("#ffb4bc"), Color.WHITE, Color.WHITE)
	t.set_font("font", "DangerButton", font(600))
	# Hauptmenue-Eintraege (links ausgerichtet, gross)
	var mn := box(Color(0, 0, 0, 0), RADIUS_M, Color(0, 0, 0, 0), 0, 22, 12)
	var mh := box(Color(1, 1, 1, 0.075), RADIUS_M, Color(0, 0, 0, 0), 0, 22, 12)
	mh.border_color = ACCENT
	mh.border_width_left = 3
	var mp := box(Color(ACCENT, 0.2), RADIUS_M, ACCENT, 0, 22, 12)
	mp.border_width_left = 3
	_button_set(t, "MainMenuButton", mn, mh, mp, mn.duplicate(), Color(TEXT, 0.86), Color.WHITE, Color.WHITE)
	t.set_font("font", "MainMenuButton", font(640, true))
	t.set_font_size("font_size", "MainMenuButton", 23)
	t.set_constant("h_separation", "MainMenuButton", 18)
	t.set_constant("icon_max_width", "MainMenuButton", 26)
	# Rundes Symbol-Button
	_button_set(t, "IconButton",
		box(Color(1, 1, 1, 0.06), 999, Color(1, 1, 1, 0.10), 1, 10, 10),
		box(Color(1, 1, 1, 0.14), 999, Color(1, 1, 1, 0.25), 1, 10, 10),
		box(Color(ACCENT, 0.3), 999, ACCENT, 1, 10, 10),
		box(Color(1, 1, 1, 0.02), 999, Color(1, 1, 1, 0.04), 1, 10, 10),
		TEXT_MUTED, Color.WHITE, Color.WHITE, 20)
	t.set_constant("icon_max_width", "IconButton", 22)
	# Kachel-Button (Auswahlkarten, App-Kacheln)
	var cn := box(CARD, RADIUS_L, BORDER, 1, 22, 20)
	var ch := box(CARD_HOVER, RADIUS_L, Color(ACCENT, 0.6), 1, 22, 20)
	var cp := box(Color(ACCENT, 0.16), RADIUS_L, ACCENT, 2, 22, 20)
	_button_set(t, "CardButton", cn, ch, cp, cn.duplicate(), TEXT, Color.WHITE, Color.WHITE, RADIUS_L)


static func _panels(t: Theme) -> void:
	var surface := shadowed(box(SURFACE, RADIUS_L, BORDER, 1, 24, 22), 30, 0.45, Vector2(0, 10))
	t.set_stylebox("panel", "Panel", surface)
	t.set_stylebox("panel", "PanelContainer", surface)
	t.set_stylebox("panel", "SurfacePanel", surface)
	t.set_stylebox("panel", "CardPanel", box(CARD, RADIUS_L, BORDER, 1, 20, 18))
	var glass := shadowed(box(GLASS, RADIUS_L, Color(1, 1, 1, 0.11), 1, 18, 14), 22, 0.3, Vector2(0, 6))
	t.set_stylebox("panel", "GlassPanel", glass)
	var key := box(Color(1, 1, 1, 0.12), 7, Color(1, 1, 1, 0.28), 1, 9, 3)
	key.border_width_bottom = 3
	t.set_stylebox("panel", "KeyCapPanel", key)
	t.set_stylebox("panel", "PillPanel", box(Color(1, 1, 1, 0.08), 999, Color(1, 1, 1, 0.10), 1, 12, 5))
	t.set_stylebox("panel", "ToastPanel", shadowed(box(SURFACE_2, RADIUS_M, BORDER_STRONG, 1, 16, 12), 20, 0.4))


static func _inputs(t: Theme) -> void:
	var n := box(Color(0, 0, 0, 0.28), RADIUS_M, Color(1, 1, 1, 0.13), 1, 14, 10)
	var f := box(Color(0, 0, 0, 0.36), RADIUS_M, ACCENT, 2, 14, 10)
	var ro := box(Color(1, 1, 1, 0.03), RADIUS_M, Color(1, 1, 1, 0.06), 1, 14, 10)
	t.set_stylebox("normal", "LineEdit", n)
	t.set_stylebox("focus", "LineEdit", f)
	t.set_stylebox("read_only", "LineEdit", ro)
	t.set_font("font", "LineEdit", font(480))
	t.set_font_size("font_size", "LineEdit", 18)
	t.set_color("font_color", "LineEdit", TEXT)
	t.set_color("font_placeholder_color", "LineEdit", TEXT_FAINT)
	t.set_color("font_uneditable_color", "LineEdit", TEXT_MUTED)
	t.set_color("caret_color", "LineEdit", ACCENT)
	t.set_color("selection_color", "LineEdit", Color(ACCENT, 0.35))
	t.set_color("clear_button_color", "LineEdit", TEXT_MUTED)
	t.set_constant("caret_width", "LineEdit", 2)
	t.set_icon("clear", "LineEdit", _tex("close_small"))
	t.set_icon("updown", "SpinBox", _tex("updown"))
	t.set_stylebox("normal", "TextEdit", n)
	t.set_stylebox("focus", "TextEdit", f)
	t.set_color("font_color", "TextEdit", TEXT)
	# OptionButton / PopupMenu
	t.set_icon("arrow", "OptionButton", _tex("arrow_down"))
	t.set_constant("arrow_margin", "OptionButton", 14)
	for st in ["normal", "hover", "pressed", "disabled"]:
		var sb: StyleBoxFlat = t.get_stylebox(st, "Button").duplicate()
		sb.content_margin_left = 16
		sb.content_margin_right = 16
		t.set_stylebox(st, "OptionButton", sb)
	t.set_stylebox("focus", "OptionButton", focus_box())
	t.set_color("font_color", "OptionButton", TEXT)
	t.set_color("font_hover_color", "OptionButton", Color.WHITE)
	t.set_color("font_pressed_color", "OptionButton", Color.WHITE)
	t.set_font("font", "OptionButton", font(520))
	t.set_font_size("font_size", "OptionButton", 18)
	var pm := shadowed(box(SURFACE_2, RADIUS_M, BORDER_STRONG, 1, 6, 6), 20, 0.5)
	t.set_stylebox("panel", "PopupMenu", pm)
	t.set_stylebox("hover", "PopupMenu", box(Color(ACCENT, 0.22), RADIUS_S, Color(0, 0, 0, 0), 0, 10, 6))
	var sep := StyleBoxLine.new()
	sep.color = BORDER
	sep.thickness = 1
	t.set_stylebox("separator", "PopupMenu", sep)
	t.set_color("font_color", "PopupMenu", TEXT)
	t.set_color("font_hover_color", "PopupMenu", Color.WHITE)
	t.set_color("font_disabled_color", "PopupMenu", Color(TEXT, 0.35))
	t.set_color("font_separator_color", "PopupMenu", TEXT_FAINT)
	t.set_font("font", "PopupMenu", font(480))
	t.set_font_size("font_size", "PopupMenu", 17)
	t.set_constant("v_separation", "PopupMenu", 10)
	t.set_constant("h_separation", "PopupMenu", 10)
	t.set_constant("item_start_padding", "PopupMenu", 12)
	t.set_constant("item_end_padding", "PopupMenu", 12)
	t.set_icon("checked", "PopupMenu", _tex("check_small"))
	t.set_icon("unchecked", "PopupMenu", _tex("empty_small"))
	t.set_icon("radio_checked", "PopupMenu", _tex("radio_small"))
	t.set_icon("radio_unchecked", "PopupMenu", _tex("empty_small"))
	t.set_icon("submenu", "PopupMenu", _tex("arrow_right"))


static func _toggles(t: Theme) -> void:
	for type in ["CheckBox", "CheckButton"]:
		var n := box(Color(0, 0, 0, 0), RADIUS_S, Color(0, 0, 0, 0), 0, 6, 6)
		var h := box(Color(1, 1, 1, 0.04), RADIUS_S, Color(0, 0, 0, 0), 0, 6, 6)
		for st in ["normal", "pressed", "disabled"]:
			t.set_stylebox(st, type, n)
		t.set_stylebox("hover", type, h)
		t.set_stylebox("hover_pressed", type, h)
		t.set_stylebox("focus", type, focus_box(RADIUS_S))
		t.set_color("font_color", type, TEXT)
		t.set_color("font_hover_color", type, Color.WHITE)
		t.set_color("font_pressed_color", type, Color.WHITE)
		t.set_color("font_hover_pressed_color", type, Color.WHITE)
		t.set_color("font_focus_color", type, Color.WHITE)
		t.set_color("font_disabled_color", type, Color(TEXT, 0.35))
		t.set_font("font", type, font(480))
		t.set_constant("h_separation", type, 12)
	t.set_icon("checked", "CheckBox", _tex("check_on"))
	t.set_icon("unchecked", "CheckBox", _tex("check_off"))
	t.set_icon("checked_disabled", "CheckBox", _tex("check_on_disabled"))
	t.set_icon("unchecked_disabled", "CheckBox", _tex("check_off_disabled"))
	t.set_icon("radio_checked", "CheckBox", _tex("radio_on"))
	t.set_icon("radio_unchecked", "CheckBox", _tex("radio_off"))
	t.set_icon("radio_checked_disabled", "CheckBox", _tex("radio_on"))
	t.set_icon("radio_unchecked_disabled", "CheckBox", _tex("radio_off"))
	t.set_icon("checked", "CheckButton", _tex("toggle_on"))
	t.set_icon("unchecked", "CheckButton", _tex("toggle_off"))
	t.set_icon("checked_disabled", "CheckButton", _tex("toggle_on_disabled"))
	t.set_icon("unchecked_disabled", "CheckButton", _tex("toggle_off_disabled"))
	t.set_icon("checked_mirrored", "CheckButton", _tex("toggle_on"))
	t.set_icon("unchecked_mirrored", "CheckButton", _tex("toggle_off"))


static func _ranges(t: Theme) -> void:
	var track := box(Color(1, 1, 1, 0.13), 999, Color(0, 0, 0, 0), 0, 0, 3)
	track.content_margin_top = 3
	track.content_margin_bottom = 3
	var fill := box(ACCENT, 999, Color(0, 0, 0, 0), 0, 0, 3)
	var fill_h := box(ACCENT_HOVER, 999, Color(0, 0, 0, 0), 0, 0, 3)
	t.set_stylebox("slider", "HSlider", track)
	t.set_stylebox("grabber_area", "HSlider", fill)
	t.set_stylebox("grabber_area_highlight", "HSlider", fill_h)
	t.set_icon("grabber", "HSlider", _tex("grabber"))
	t.set_icon("grabber_highlight", "HSlider", _tex("grabber_hl"))
	t.set_icon("grabber_disabled", "HSlider", _tex("grabber_disabled"))
	t.set_icon("tick", "HSlider", _tex("tick"))
	t.set_constant("center_grabber", "HSlider", 0)
	t.set_constant("grabber_offset", "HSlider", 0)
	t.set_stylebox("slider", "VSlider", track)
	t.set_stylebox("grabber_area", "VSlider", fill)
	t.set_icon("grabber", "VSlider", _tex("grabber"))
	t.set_icon("grabber_highlight", "VSlider", _tex("grabber_hl"))
	# Fortschrittsbalken
	var pb_bg := box(Color(1, 1, 1, 0.09), 999, Color(0, 0, 0, 0), 0, 0, 0)
	var pb_fill := box(ACCENT, 999, Color(0, 0, 0, 0), 0, 0, 0)
	t.set_stylebox("background", "ProgressBar", pb_bg)
	t.set_stylebox("fill", "ProgressBar", pb_fill)
	t.set_color("font_color", "ProgressBar", TEXT)
	t.set_font("font", "ProgressBar", font(600, false, true))
	t.set_font_size("font_size", "ProgressBar", 14)
	# Scrollbalken
	for type in ["VScrollBar", "HScrollBar"]:
		var sc := empty(0)
		if type == "VScrollBar":
			sc.content_margin_left = 3
			sc.content_margin_right = 3
		else:
			sc.content_margin_top = 3
			sc.content_margin_bottom = 3
		t.set_stylebox("scroll", type, sc)
		t.set_stylebox("scroll_focus", type, sc)
		t.set_stylebox("grabber", type, box(Color(1, 1, 1, 0.16), 999, Color(0, 0, 0, 0), 0, 4, 4))
		t.set_stylebox("grabber_highlight", type, box(Color(1, 1, 1, 0.30), 999, Color(0, 0, 0, 0), 0, 4, 4))
		t.set_stylebox("grabber_pressed", type, box(Color(ACCENT, 0.7), 999, Color(0, 0, 0, 0), 0, 4, 4))
		var nothing := ImageTexture.create_from_image(Image.create(1, 1, false, Image.FORMAT_RGBA8))
		t.set_icon("increment", type, nothing)
		t.set_icon("decrement", type, nothing)
		t.set_icon("increment_highlight", type, nothing)
		t.set_icon("decrement_highlight", type, nothing)
	t.set_stylebox("panel", "ScrollContainer", empty(0))
	t.set_stylebox("focus", "ScrollContainer", empty(0))


static func _lists(t: Theme) -> void:
	var lp := box(Color(0, 0, 0, 0.18), RADIUS_M, BORDER, 1, 6, 6)
	var sel := box(Color(ACCENT, 0.22), RADIUS_S, Color(ACCENT, 0.6), 1, 8, 6)
	var hov := box(Color(1, 1, 1, 0.06), RADIUS_S, Color(0, 0, 0, 0), 0, 8, 6)
	t.set_stylebox("panel", "ItemList", lp)
	t.set_stylebox("focus", "ItemList", empty(0))
	t.set_stylebox("selected", "ItemList", sel)
	t.set_stylebox("selected_focus", "ItemList", sel)
	t.set_stylebox("hovered", "ItemList", hov)
	t.set_stylebox("cursor", "ItemList", empty(0))
	t.set_stylebox("cursor_unfocused", "ItemList", empty(0))
	t.set_color("font_color", "ItemList", TEXT_MUTED)
	t.set_color("font_hovered_color", "ItemList", Color.WHITE)
	t.set_color("font_selected_color", "ItemList", Color.WHITE)
	t.set_color("guide_color", "ItemList", Color(0, 0, 0, 0))
	t.set_constant("v_separation", "ItemList", 8)
	t.set_constant("h_separation", "ItemList", 10)
	t.set_constant("icon_margin", "ItemList", 10)
	t.set_stylebox("panel", "Tree", lp)
	t.set_stylebox("focus", "Tree", empty(0))
	t.set_stylebox("selected", "Tree", sel)
	t.set_stylebox("selected_focus", "Tree", sel)
	t.set_stylebox("hovered", "Tree", hov)
	t.set_stylebox("cursor", "Tree", empty(0))
	t.set_stylebox("cursor_unfocused", "Tree", empty(0))
	t.set_stylebox("title_button_normal", "Tree", box(Color(1, 1, 1, 0.05), 0, Color(0, 0, 0, 0), 0, 8, 6))
	t.set_color("font_color", "Tree", TEXT_MUTED)
	t.set_color("font_selected_color", "Tree", Color.WHITE)
	t.set_color("guide_color", "Tree", Color(1, 1, 1, 0.05))
	t.set_color("relationship_line_color", "Tree", Color(1, 1, 1, 0.08))
	t.set_icon("arrow", "Tree", _tex("arrow_down"))
	t.set_icon("arrow_collapsed", "Tree", _tex("arrow_right"))
	t.set_icon("checked", "Tree", _tex("check_on"))
	t.set_icon("unchecked", "Tree", _tex("check_off"))
	t.set_constant("v_separation", "Tree", 6)
	t.set_constant("item_margin", "Tree", 18)
	# Tabs
	var ts := box(Color(1, 1, 1, 0.07), RADIUS_M, ACCENT, 0, 20, 10)
	ts.border_width_bottom = 3
	var tu := box(Color(0, 0, 0, 0), RADIUS_M, Color(0, 0, 0, 0), 0, 20, 10)
	var th := box(Color(1, 1, 1, 0.04), RADIUS_M, Color(0, 0, 0, 0), 0, 20, 10)
	for type in ["TabContainer", "TabBar"]:
		t.set_stylebox("tab_selected", type, ts)
		t.set_stylebox("tab_unselected", type, tu)
		t.set_stylebox("tab_hovered", type, th)
		t.set_stylebox("tab_disabled", type, tu)
		t.set_stylebox("tab_focus", type, focus_box())
		t.set_color("font_selected_color", type, Color.WHITE)
		t.set_color("font_unselected_color", type, TEXT_MUTED)
		t.set_color("font_hovered_color", type, Color.WHITE)
		t.set_color("font_disabled_color", type, Color(TEXT, 0.3))
		t.set_font("font", type, font(600))
		t.set_font_size("font_size", type, 18)
		t.set_constant("h_separation", type, 8)
		t.set_constant("icon_max_width", type, 22)
	t.set_stylebox("panel", "TabContainer", box(Color(0, 0, 0, 0), RADIUS_L, Color(0, 0, 0, 0), 0, 4, 16))
	t.set_stylebox("tabbar_background", "TabContainer", empty(0))


static func _misc(t: Theme) -> void:
	t.set_stylebox("panel", "TooltipPanel", shadowed(box(Color("#0f1729"), RADIUS_S, BORDER_STRONG, 1, 12, 8), 12, 0.4, Vector2(0, 3)))
	t.set_color("font_color", "TooltipLabel", TEXT)
	t.set_font("font", "TooltipLabel", font(480))
	t.set_font_size("font_size", "TooltipLabel", 16)
	# RichTextLabel
	t.set_font("normal_font", "RichTextLabel", font(430))
	t.set_font("bold_font", "RichTextLabel", font(700))
	t.set_font("italics_font", "RichTextLabel", font(430, false, false, true))
	t.set_font("bold_italics_font", "RichTextLabel", font(700, false, false, true))
	t.set_font("mono_font", "RichTextLabel", font(500, false, true))
	for k in ["normal_font_size", "bold_font_size", "italics_font_size", "bold_italics_font_size", "mono_font_size"]:
		t.set_font_size(k, "RichTextLabel", SIZE_BODY - 1)
	t.set_color("default_color", "RichTextLabel", TEXT_MUTED)
	t.set_color("font_selected_color", "RichTextLabel", Color.WHITE)
	t.set_color("selection_color", "RichTextLabel", Color(ACCENT, 0.35))
	t.set_constant("line_separation", "RichTextLabel", 5)
	t.set_stylebox("normal", "RichTextLabel", empty(0))
	t.set_stylebox("focus", "RichTextLabel", empty(0))
	# Trenner
	for type in ["HSeparator", "VSeparator"]:
		var line := StyleBoxLine.new()
		line.color = BORDER
		line.thickness = 1
		line.vertical = type == "VSeparator"
		t.set_stylebox("separator", type, line)
		t.set_constant("separation", type, 12)
	# Fenster (eingebettete Dialoge, falls Systemdialoge verwendet werden)
	t.set_stylebox("embedded_border", "Window", shadowed(box(SURFACE_2, RADIUS_L, BORDER_STRONG, 1, 10, 36), 30, 0.5))
	t.set_stylebox("embedded_unfocused_border", "Window", shadowed(box(SURFACE_2, RADIUS_L, BORDER, 1, 10, 36), 30, 0.4))
	t.set_color("title_color", "Window", TEXT)
	t.set_font("title_font", "Window", font(700, true))
	t.set_stylebox("panel", "AcceptDialog", box(SURFACE_2, RADIUS_L, Color(0, 0, 0, 0), 0, 16, 16))
	# Container-Abstaende
	t.set_constant("separation", "BoxContainer", SPACE_S + 2)
	t.set_constant("separation", "HBoxContainer", SPACE_S + 2)
	t.set_constant("separation", "VBoxContainer", SPACE_S + 2)
	t.set_constant("h_separation", "GridContainer", SPACE_M)
	t.set_constant("v_separation", "GridContainer", SPACE_M)
	t.set_constant("h_separation", "FlowContainer", SPACE_S)
	t.set_constant("v_separation", "FlowContainer", SPACE_S)
	t.set_constant("h_separation", "HFlowContainer", SPACE_S)
	t.set_constant("v_separation", "HFlowContainer", SPACE_S)


## Schreibt das Theme nach THEME_PATH (Build-Schritt, siehe Kopfkommentar).
static func write_theme_file(path := THEME_PATH) -> int:
	var t := build()
	return ResourceSaver.save(t, path)
