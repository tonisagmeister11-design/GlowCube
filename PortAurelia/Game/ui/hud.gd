class_name HUD
extends CanvasLayer
## In-game heads-up display.
##   bottom-left : radar (rotating map, blips, GPS route, flashing when wanted), health + armour
##   top-right   : clock, money (+/- animation), weapon & ammo, wanted stars
##   centre      : crosshair (aiming), big messages (AUSGESCHALTET, MISSION BESTANDEN ...)
##   bottom      : mission objective / subtitles, interaction prompt
##   top-left    : notifications / help texts
##   bottom-right: district name on entering, vehicle name + speedometer

const MAP_PATH := "res://assets/ui/generated/map.png"
const FONT_OUTLINE := 6

var world: GameWorld
var gps := GPS.new()
var visible_hud := true

var _root: Control
var _radar: ColorRect
var _radar_mat: ShaderMaterial
var _radar_overlay: Control
var _hp_bar: ProgressBar
var _armor_bar: ProgressBar
var _money: Label
var _money_delta: Label
var _clock: Label
var _weapon: Label
var _ammo: Label
var _stars: Label
var _crosshair: Control
var _radar_box: Control
var _big_title: Label
var _big_sub: Label
var _big_timer := 0.0
var _objective: Label
var _prompt: Label
var _notify_box: VBoxContainer
var _district: Label
var _district_timer := 0.0
var _speed: Label
var _vehicle_name: Label
var _vehicle_timer := 0.0
var _money_shown := 0
var _money_delta_timer := 0.0
var _radar_radius := 110.0
var _t := 0.0
var _timer_label: Label
var _scope: Control


func _ready() -> void:
	name = "HUD"
	layer = 5
	world = GameWorld.instance
	world.hud = self
	_build()
	Events.notify.connect(notify)
	Events.big_message.connect(big_message)
	Events.mission_objective.connect(func(t): set_objective(t))
	Events.subtitle.connect(func(t, d): if Settings.get_value("gameplay", "subtitles"): set_objective(t, d))
	Events.interaction_prompt.connect(func(t): _prompt.text = t; _prompt.visible = t != "")
	Events.money_changed.connect(_on_money)
	Events.district_entered.connect(_on_district)
	Events.player_entered_vehicle.connect(_on_enter_vehicle)
	Events.waypoint_set.connect(func(p): gps.set_target(p))
	_money_shown = Game.player_data.money if Game.player_data else 0
	var wheel := WeaponWheel.new()
	wheel.name = "WeaponWheel"
	add_child(wheel)


# ------------------------------------------------------------------ construction
func _label(size: int, align := HORIZONTAL_ALIGNMENT_LEFT, col := Color.WHITE) -> Label:
	var l := Label.new()
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", col)
	l.add_theme_color_override("font_outline_color", Color(0, 0, 0, 0.9))
	l.add_theme_constant_override("outline_size", FONT_OUTLINE)
	l.horizontal_alignment = align
	l.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return l


func _bar(col: Color) -> ProgressBar:
	var b := ProgressBar.new()
	b.show_percentage = false
	b.custom_minimum_size = Vector2(0, 9)
	var bg := StyleBoxFlat.new()
	bg.bg_color = Color(0, 0, 0, 0.55)
	var fg := StyleBoxFlat.new()
	fg.bg_color = col
	b.add_theme_stylebox_override("background", bg)
	b.add_theme_stylebox_override("fill", fg)
	b.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return b


func _build() -> void:
	_root = Control.new()
	_root.set_anchors_preset(Control.PRESET_FULL_RECT)
	_root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_root)
	# ---- radar
	var radar_box := VBoxContainer.new()
	radar_box.set_anchors_preset(Control.PRESET_BOTTOM_LEFT)
	radar_box.position = Vector2(28, -300)
	radar_box.anchor_top = 1.0
	radar_box.anchor_bottom = 1.0
	radar_box.offset_top = -292
	radar_box.offset_left = 28
	radar_box.add_theme_constant_override("separation", 4)
	radar_box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_root.add_child(radar_box)
	_radar_box = radar_box
	_radar = ColorRect.new()
	_radar.custom_minimum_size = Vector2(250, 250)
	_radar_mat = ShaderMaterial.new()
	_radar_mat.shader = load("res://ui/shaders/radar.gdshader")
	if ResourceLoader.exists(MAP_PATH):
		_radar_mat.set_shader_parameter("map_tex", load(MAP_PATH))
	_radar.material = _radar_mat
	_radar.clip_contents = true
	_radar.mouse_filter = Control.MOUSE_FILTER_IGNORE
	radar_box.add_child(_radar)
	_radar_overlay = Control.new()
	_radar_overlay.set_anchors_preset(Control.PRESET_FULL_RECT)
	_radar_overlay.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_radar_overlay.draw.connect(_draw_radar_overlay)
	_radar.add_child(_radar_overlay)
	var bars := HBoxContainer.new()
	bars.add_theme_constant_override("separation", 4)
	bars.mouse_filter = Control.MOUSE_FILTER_IGNORE
	radar_box.add_child(bars)
	_hp_bar = _bar(Color(0.36, 0.78, 0.36))
	_hp_bar.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_hp_bar.size_flags_stretch_ratio = 2.0
	bars.add_child(_hp_bar)
	_armor_bar = _bar(Color(0.35, 0.6, 0.95))
	_armor_bar.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bars.add_child(_armor_bar)
	# ---- top right
	var tr := VBoxContainer.new()
	tr.anchor_left = 1.0
	tr.anchor_right = 1.0
	tr.offset_left = -360
	tr.offset_right = -28
	tr.offset_top = 22
	tr.alignment = BoxContainer.ALIGNMENT_BEGIN
	tr.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_root.add_child(tr)
	_clock = _label(22, HORIZONTAL_ALIGNMENT_RIGHT)
	tr.add_child(_clock)
	_money = _label(34, HORIZONTAL_ALIGNMENT_RIGHT, Color(0.45, 0.9, 0.45))
	tr.add_child(_money)
	_money_delta = _label(24, HORIZONTAL_ALIGNMENT_RIGHT, Color(0.45, 0.9, 0.45))
	_money_delta.modulate.a = 0.0
	tr.add_child(_money_delta)
	_weapon = _label(20, HORIZONTAL_ALIGNMENT_RIGHT)
	tr.add_child(_weapon)
	_ammo = _label(26, HORIZONTAL_ALIGNMENT_RIGHT)
	tr.add_child(_ammo)
	_stars = _label(38, HORIZONTAL_ALIGNMENT_RIGHT, Color(1, 1, 1))
	tr.add_child(_stars)
	# ---- crosshair
	_crosshair = Control.new()
	_crosshair.set_anchors_preset(Control.PRESET_CENTER)
	_crosshair.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_crosshair.draw.connect(_draw_crosshair)
	_root.add_child(_crosshair)
	# ---- sniper scope overlay
	_scope = Control.new()
	_scope.set_anchors_preset(Control.PRESET_FULL_RECT)
	_scope.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_scope.visible = false
	_scope.draw.connect(_draw_scope)
	_root.add_child(_scope)
	_root.move_child(_scope, 0)
	# ---- big message
	var center := VBoxContainer.new()
	center.set_anchors_preset(Control.PRESET_CENTER)
	center.anchor_left = 0.0
	center.anchor_right = 1.0
	center.offset_top = -120
	center.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_root.add_child(center)
	_big_title = _label(72, HORIZONTAL_ALIGNMENT_CENTER, Color(0.95, 0.85, 0.35))
	_big_title.add_theme_constant_override("outline_size", 12)
	center.add_child(_big_title)
	_big_sub = _label(28, HORIZONTAL_ALIGNMENT_CENTER)
	center.add_child(_big_sub)
	# ---- bottom texts
	_objective = _label(26, HORIZONTAL_ALIGNMENT_CENTER)
	_objective.anchor_left = 0.15
	_objective.anchor_right = 0.85
	_objective.anchor_top = 1.0
	_objective.anchor_bottom = 1.0
	_objective.offset_top = -120
	_objective.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_root.add_child(_objective)
	_prompt = _label(22, HORIZONTAL_ALIGNMENT_CENTER, Color(1, 0.95, 0.7))
	_prompt.anchor_left = 0.3
	_prompt.anchor_right = 0.7
	_prompt.anchor_top = 1.0
	_prompt.anchor_bottom = 1.0
	_prompt.offset_top = -170
	_prompt.visible = false
	_root.add_child(_prompt)
	# ---- mission timer
	_timer_label = _label(34, HORIZONTAL_ALIGNMENT_CENTER, Color(1, 0.9, 0.5))
	_timer_label.anchor_left = 0.4
	_timer_label.anchor_right = 0.6
	_timer_label.offset_top = 20
	_root.add_child(_timer_label)
	# ---- notifications
	_notify_box = VBoxContainer.new()
	_notify_box.position = Vector2(28, 24)
	_notify_box.custom_minimum_size = Vector2(420, 0)
	_notify_box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_root.add_child(_notify_box)
	# ---- bottom right
	var br := VBoxContainer.new()
	br.anchor_left = 1.0
	br.anchor_right = 1.0
	br.anchor_top = 1.0
	br.anchor_bottom = 1.0
	br.offset_left = -460
	br.offset_right = -28
	br.offset_top = -150
	br.alignment = BoxContainer.ALIGNMENT_END
	br.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_root.add_child(br)
	_vehicle_name = _label(26, HORIZONTAL_ALIGNMENT_RIGHT, Color(0.95, 0.9, 0.6))
	br.add_child(_vehicle_name)
	_district = _label(30, HORIZONTAL_ALIGNMENT_RIGHT)
	br.add_child(_district)
	_speed = _label(30, HORIZONTAL_ALIGNMENT_RIGHT)
	br.add_child(_speed)


# ------------------------------------------------------------------ public API
func notify(text: String, duration := 4.0) -> void:
	var panel := PanelContainer.new()
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0, 0, 0, 0.6)
	sb.set_corner_radius_all(4)
	sb.content_margin_left = 12
	sb.content_margin_right = 12
	sb.content_margin_top = 8
	sb.content_margin_bottom = 8
	panel.add_theme_stylebox_override("panel", sb)
	panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var l := _label(19)
	l.text = text
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.custom_minimum_size = Vector2(396, 0)
	panel.add_child(l)
	_notify_box.add_child(panel)
	while _notify_box.get_child_count() > 4:
		_notify_box.get_child(0).free()
	AudioManager.play_ui("notify", -8.0)
	var tw := panel.create_tween()
	tw.tween_interval(maxf(duration, 1.0))
	tw.tween_property(panel, "modulate:a", 0.0, 0.5)
	tw.tween_callback(panel.queue_free)


func big_message(title: String, sub := "", duration := 4.0) -> void:
	_big_title.text = title
	_big_sub.text = sub
	_big_timer = duration
	_big_title.modulate.a = 1.0
	_big_sub.modulate.a = 1.0


func set_objective(text: String, duration := 0.0) -> void:
	_objective.text = text
	_objective.modulate.a = 1.0
	if duration > 0.0:
		var tw := _objective.create_tween()
		tw.tween_interval(duration)
		tw.tween_property(_objective, "modulate:a", 0.0, 0.6)


func set_waypoint(p: Vector3) -> void:
	gps.set_target(p)


func set_visible_hud(v: bool) -> void:
	visible_hud = v
	_root.visible = v


# ------------------------------------------------------------------ events
func _on_money(amount: int, delta: int) -> void:
	_money_delta.text = ("+$%d" if delta >= 0 else "-$%d") % absi(delta)
	_money_delta.add_theme_color_override("font_color", Color(0.45, 0.9, 0.45) if delta >= 0 else Color(0.95, 0.35, 0.3))
	_money_delta.modulate.a = 1.0
	_money_delta_timer = 2.5


func _on_district(_id: String, dname: String) -> void:
	_district.text = dname
	_district_timer = 4.0


func _on_enter_vehicle(v: Node) -> void:
	if v is Vehicle:
		_vehicle_name.text = VehicleDefs.display_name((v as Vehicle).type_id)
		_vehicle_timer = 3.5


# ------------------------------------------------------------------ update
func _process(delta: float) -> void:
	_t += delta
	var p := world.player as Player
	if p == null:
		return
	_apply_scale()
	_root.visible = visible_hud and bool(Settings.get_value("gameplay", "show_hud"))
	_radar_box.visible = bool(Settings.get_value("gameplay", "show_minimap"))
	# health / armour
	_hp_bar.max_value = p.health.max_health
	_hp_bar.value = p.health.health
	_armor_bar.value = p.health.armor
	_armor_bar.visible = p.health.armor > 0.0
	var low := p.health.health < p.health.max_health * 0.25
	(_hp_bar.get_theme_stylebox("fill") as StyleBoxFlat).bg_color = Color(0.9, 0.2, 0.2) if low and fmod(_t, 0.8) < 0.4 \
		else Color(0.36, 0.78, 0.36)
	# money counts up/down smoothly
	var target: int = Game.player_data.money
	if _money_shown != target:
		var step := maxi(1, absi(target - _money_shown) / 12)
		_money_shown = move_toward(_money_shown, target, step)
	_money.text = "$%s" % _fmt(_money_shown)
	_money_delta_timer -= delta
	if _money_delta_timer < 0.6:
		_money_delta.modulate.a = maxf(0.0, _money_delta_timer / 0.6)
	# clock
	if world.day_night:
		_clock.text = world.day_night.call("clock_text")
	# weapon
	var wid := p.weapons.current_id()
	var wd := WeaponData.get_def(wid)
	_weapon.text = wd["name"] if wid != "unarmed" else ""
	if WeaponData.is_ranged(wid):
		var st: Dictionary = p.weapons.owned[wid]
		_ammo.text = "%d / %d" % [st["clip"], st["reserve"]]
	else:
		_ammo.text = ""
	# wanted stars
	var lvl := 0
	var searching := false
	if world.police:
		lvl = world.police.wanted_level
		searching = world.police.searching
	var stars := ""
	for i in 5:
		stars += "★" if i < lvl else "☆"
	_stars.text = stars if lvl > 0 else ""
	_stars.modulate.a = (0.35 if fmod(_t, 0.6) < 0.3 else 1.0) if searching else 1.0
	_stars.add_theme_color_override("font_color", Color(1, 1, 1))
	# big message fade
	if _big_timer > 0.0:
		_big_timer -= delta
		if _big_timer < 0.8:
			_big_title.modulate.a = maxf(0.0, _big_timer / 0.8)
			_big_sub.modulate.a = _big_title.modulate.a
	elif _big_title.text != "":
		_big_title.text = ""
		_big_sub.text = ""
	# district / vehicle labels
	_district_timer -= delta
	_district.modulate.a = clampf(_district_timer, 0.0, 1.0)
	_vehicle_timer -= delta
	_vehicle_name.modulate.a = clampf(_vehicle_timer, 0.0, 1.0)
	if p.is_in_vehicle() and p.vehicle is Vehicle:
		var kmh := absf((p.vehicle as Vehicle).speed_kmh)
		_speed.text = "%d mph" % int(kmh * 0.621371) if int(Settings.get_value("gameplay", "speed_units")) == 1 \
			else "%d km/h" % int(kmh)
	else:
		_speed.text = ""
	# crosshair / sniper scope
	var scoped: bool = p.cam != null and p.cam.scoped
	_scope.visible = scoped
	if scoped:
		_scope.queue_redraw()
	_crosshair.visible = not scoped and bool(Settings.get_value("gameplay", "crosshair")) and (p.aiming \
		or (p.is_in_vehicle() and Input.is_action_pressed("aim")) or (p.cam and p.cam.first_person and not p.is_in_vehicle()))
	_crosshair.queue_redraw()
	# mission countdown
	var tl: float = world.missions.call("current_time_left") if world.missions else -1.0
	if tl >= 0.0:
		_timer_label.text = "%d:%02d" % [int(tl) / 60, int(tl) % 60]
		_timer_label.add_theme_color_override("font_color", Color(1, 0.3, 0.25) if tl < 10.0 else Color(1, 0.9, 0.5))
	else:
		_timer_label.text = ""
	# radar
	_update_radar(delta, p, lvl, searching)


func _fmt(n: int) -> String:
	var s := str(absi(n))
	var out := ""
	while s.length() > 3:
		out = "." + s.substr(s.length() - 3) + out
		s = s.substr(0, s.length() - 3)
	return ("-" if n < 0 else "") + s + out


func _update_radar(delta: float, p: Player, lvl: int, searching: bool) -> void:
	var yaw := _radar_yaw(p)
	var speed := 0.0
	if p.is_in_vehicle():
		speed = absf((p.vehicle as Vehicle).speed_kmh)
	var want_r := clampf(110.0 + speed * 1.6, 110.0, 300.0)
	_radar_radius = lerpf(_radar_radius, want_r, clampf(delta * 1.5, 0.0, 1.0))
	var ws := world.data.world_size
	var pos := p.global_position
	var uv := Vector2((pos.x - world.data.world_min) / ws, (pos.z - world.data.world_min) / ws)
	_radar_mat.set_shader_parameter("center_uv", uv)
	_radar_mat.set_shader_parameter("rot", -yaw)
	_radar_mat.set_shader_parameter("zoom", _radar_radius / ws)
	_radar_mat.set_shader_parameter("wanted_flash", 1.0 if lvl > 0 and not searching else (0.5 if lvl > 0 else 0.0))
	_radar_mat.set_shader_parameter("time_s", _t)
	_radar_mat.set_shader_parameter("night", float(ShaderGlobals.get_value("night", 0.0)) * 0.6)
	var heading := -p.global_basis.z
	if p.is_in_vehicle():
		heading = -(p.vehicle as Node3D).global_basis.z
	gps.update(delta, pos, heading)
	if gps.active() and pos.distance_to(gps.target) < 25.0:
		gps.set_target(Vector3.INF)
		AudioManager.play_ui("checkpoint", -6.0)
	_radar_overlay.queue_redraw()


func _radar_yaw(p: Player) -> float:
	if not Settings.get_value("gameplay", "minimap_rotate"):
		return 0.0
	return p.cam.yaw if p.cam else p.rotation.y


func _apply_scale() -> void:
	var sc := float(Settings.get_value("gameplay", "hud_scale"))
	if absf(_root.scale.x - sc) < 0.001 and _root.size.is_equal_approx(get_viewport().get_visible_rect().size / sc):
		return
	_root.set_anchors_preset(Control.PRESET_TOP_LEFT)
	_root.scale = Vector2(sc, sc)
	_root.size = get_viewport().get_visible_rect().size / sc


## World position -> radar pixel (relative to the radar rect), clamped flag.
func _to_radar(w: Vector3, center: Vector3, yaw: float, half: float) -> Vector2:
	var d := Vector2(w.x - center.x, w.z - center.z) / _radar_radius
	# inverse of the shader rotation (rot = -yaw)
	var c := cos(yaw)
	var s := sin(yaw)
	var p := Vector2(d.x * c - d.y * s, d.x * s + d.y * c)
	return Vector2(half, half) + p * half


func _draw_radar_overlay() -> void:
	var p := world.player as Player
	if p == null:
		return
	var half := _radar.size.x * 0.5
	var yaw := _radar_yaw(p)
	var center := p.global_position
	var ov := _radar_overlay
	# GPS route
	if gps.active() and gps.points.size() > 1:
		var pts := PackedVector2Array()
		for q in gps.points:
			pts.append(_to_radar(q, center, yaw, half))
		ov.draw_polyline(pts, Color(0.75, 0.3, 0.95, 0.95), 4.0, true)
	# wanted search area
	if world.police and world.police.searching:
		var c := _to_radar(world.police.last_seen, center, yaw, half)
		ov.draw_arc(c, world.police.search_radius() / _radar_radius * half, 0, TAU, 48, Color(1, 0.3, 0.3, 0.6), 2.0)
	# blips
	for b in _collect_blips():
		var bp := _to_radar(b["pos"], center, yaw, half)
		var off := bp - Vector2(half, half)
		var lim := half - 10.0
		var clamped := false
		if absf(off.x) > lim or absf(off.y) > lim:
			if not b.get("edge", false):
				continue
			off = off / maxf(absf(off.x), absf(off.y)) * lim
			bp = Vector2(half, half) + off
			clamped = true
		_draw_blip(ov, bp, b, clamped)
	# player arrow (points in the player's facing relative to the camera)
	var face := p.rotation.y
	if p.is_in_vehicle():
		face = (p.vehicle as Node3D).global_rotation.y
	var a := -(face - yaw)
	var tip := Vector2(0, -9).rotated(a)
	var l := Vector2(-6, 7).rotated(a)
	var r := Vector2(6, 7).rotated(a)
	var o := Vector2(half, half)
	ov.draw_colored_polygon(PackedVector2Array([o + tip, o + r, o + Vector2(0, 3).rotated(a), o + l]), Color.WHITE)
	ov.draw_polyline(PackedVector2Array([o + tip, o + r, o + Vector2(0, 3).rotated(a), o + l, o + tip]), Color.BLACK, 1.5)
	# north marker
	var n := _to_radar(center + Vector3(0, 0, -_radar_radius * 0.9), center, yaw, half)
	var nd := (n - o)
	nd = nd / maxf(absf(nd.x), absf(nd.y)) * (half - 12.0)
	var font := ThemeDB.fallback_font
	ov.draw_circle(o + nd, 9.0, Color(0, 0, 0, 0.7))
	ov.draw_string(font, o + nd + Vector2(-5, 5), "N", HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color.WHITE)


func _draw_blip(ov: Control, bp: Vector2, b: Dictionary, clamped: bool) -> void:
	var col: Color = b.get("color", Color.WHITE)
	var icon: String = b.get("icon", "")
	var size: float = b.get("size", 7.0)
	if icon == "":
		ov.draw_circle(bp, size * 0.6, Color(0, 0, 0, 0.8))
		ov.draw_circle(bp, size * 0.45, col)
		return
	ov.draw_circle(bp, size + 1.5, Color(0, 0, 0, 0.8))
	ov.draw_circle(bp, size, col)
	var font := ThemeDB.fallback_font
	var fs := int(size * 1.5)
	var w := font.get_string_size(icon, HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x
	ov.draw_string(font, bp + Vector2(-w * 0.5, fs * 0.36), icon, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, Color.BLACK if col.get_luminance() > 0.6 else Color.WHITE)


func _collect_blips() -> Array:
	var out: Array = []
	for sys in [world.economy, world.missions, world.events]:
		if sys and sys.has_method("blips"):
			out.append_array(sys.call("blips"))
	if gps.active():
		out.append({"pos": gps.target, "icon": "", "color": Color(0.8, 0.35, 1.0), "size": 9.0, "edge": true})
	if world.police:
		var flash := fmod(_t, 0.5) < 0.25
		for u in world.police.units:
			if is_instance_valid(u["vehicle"]):
				out.append({"pos": (u["vehicle"] as Node3D).global_position, "icon": "",
					"color": Color(0.95, 0.15, 0.15) if flash else Color(0.2, 0.35, 1.0), "size": 8.0, "edge": false})
			for c in u["cops"]:
				if is_instance_valid(c) and not (c as NPC).is_dead():
					out.append({"pos": (c as Node3D).global_position, "icon": "", "color": Color(0.3, 0.45, 1.0), "size": 6.0})
	if world.peds:
		for n in world.peds.call("peds_near", world.player.global_position, 90.0):
			var npc := n as NPC
			if npc.hostile and npc.target == world.player and npc.role != "cop":
				out.append({"pos": npc.global_position, "icon": "", "color": Color(0.95, 0.2, 0.2), "size": 6.0})
	return out


func _draw_scope() -> void:
	var sz := _scope.size
	var c := sz * 0.5
	var r := sz.y * 0.46
	var black := Color(0, 0, 0, 1)
	# mask outside the lens with a thick ring and side bars
	_scope.draw_arc(c, r + sz.x * 0.5, 0, TAU, 96, black, sz.x, true)
	_scope.draw_rect(Rect2(0, 0, c.x - r + 1, sz.y), black)
	_scope.draw_rect(Rect2(c.x + r - 1, 0, sz.x - (c.x + r) + 1, sz.y), black)
	_scope.draw_arc(c, r, 0, TAU, 96, Color(0.05, 0.05, 0.05), 6.0, true)
	var line := Color(0, 0, 0, 0.9)
	_scope.draw_line(Vector2(c.x - r, c.y), Vector2(c.x - 8, c.y), line, 2.0)
	_scope.draw_line(Vector2(c.x + 8, c.y), Vector2(c.x + r, c.y), line, 2.0)
	_scope.draw_line(Vector2(c.x, c.y - r), Vector2(c.x, c.y - 8), line, 2.0)
	_scope.draw_line(Vector2(c.x, c.y + 8), Vector2(c.x, c.y + r), line, 2.0)
	for i in range(1, 5):
		var o := i * r * 0.12
		_scope.draw_line(Vector2(c.x + o, c.y - 6), Vector2(c.x + o, c.y + 6), line, 2.0)
		_scope.draw_line(Vector2(c.x - o, c.y - 6), Vector2(c.x - o, c.y + 6), line, 2.0)
		_scope.draw_line(Vector2(c.x - 6, c.y + o), Vector2(c.x + 6, c.y + o), line, 2.0)
	_scope.draw_circle(c, 2.0, Color(0.9, 0.1, 0.1))


func _draw_crosshair() -> void:
	var p := world.player as Player
	var c := Color(1, 1, 1, 0.9)
	var hit := false
	if p and p.cam:
		var h := p.cam.aim_hit(120.0, [p.get_rid()])
		var col = h.get("collider")
		hit = col is NPC and not (col as NPC).is_dead()
	if hit:
		c = Color(1, 0.25, 0.2, 0.95)
	var cc := _crosshair
	cc.draw_circle(Vector2.ZERO, 2.2, c)
	var g := 6.0
	var l := 7.0
	for d in [Vector2.RIGHT, Vector2.LEFT, Vector2.UP, Vector2.DOWN]:
		cc.draw_line(d * g, d * (g + l), Color(0, 0, 0, 0.6), 4.0)
		cc.draw_line(d * g, d * (g + l), c, 2.0)
