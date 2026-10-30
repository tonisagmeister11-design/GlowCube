extends RefCounted
## Tests fuer das Audiosystem: Registry, Dateien, Schleifen, AudioManager-API
## (headless mit Dummy-Treiber), fehlende Klaenge, AmbienceDirector.

var runner


func _audio() -> Node:
	return runner.get_node_or_null("/root/Audio")


func test_registry_files_exist() -> void:
	var files := SoundRegistry.all_files()
	runner.check(files.size() >= 40, "Registry enthaelt zu wenige Dateien (%d)" % files.size())
	for p in files:
		runner.check(ResourceLoader.exists(p), "Datei fehlt: " + p)
	for key in ["menu", "day", "night"]:
		runner.check(not SoundRegistry.music(key).is_empty(), "Musiktitel fehlt: " + key)
	for key in ["city_day", "city_night", "park", "residential", "shop", "rain", "river"]:
		runner.check(not SoundRegistry.ambience(key).is_empty(), "Ambiente fehlt: " + key)
	for key in SoundRegistry.PLANNED.keys():
		runner.check(SoundRegistry.sfx(key).is_empty(), "Geplanter Klang ist bereits eingetragen: " + key)


func test_streams_load() -> void:
	var loops := SoundRegistry.loop_files()
	for p in SoundRegistry.all_files():
		var s = load(p)
		if not runner.check(s is AudioStreamWAV, "Kein AudioStreamWAV: " + p):
			continue
		var w: AudioStreamWAV = s
		var length := w.get_length()
		runner.check(w.mix_rate >= 16000, "Abtastrate zu niedrig: " + p)
		if p in loops:
			runner.check(length >= 1.0 and length <= 130.0, "Schleifenlaenge unplausibel (%.2f s): %s" % [length, p])
		else:
			runner.check(length >= 0.03 and length <= 4.0, "Effektlaenge unplausibel (%.2f s): %s" % [length, p])
		runner.check(w.data.size() > 1000, "Keine Audiodaten: " + p)


func test_loop_flags() -> void:
	var loops := SoundRegistry.loop_files()
	for p in SoundRegistry.all_files():
		var w: AudioStreamWAV = load(p)
		if w == null:
			continue
		var frames := int(round(w.get_length() * w.mix_rate))
		if p in loops:
			runner.check(w.loop_mode == AudioStreamWAV.LOOP_FORWARD, "Schleife nicht gesetzt: " + p)
			runner.check(w.loop_begin == 0 and w.loop_end == frames, "Schleifenpunkte falsch (%d..%d / %d): %s" % [w.loop_begin, w.loop_end, frames, p])
		else:
			runner.check(w.loop_mode == AudioStreamWAV.LOOP_DISABLED, "Einzelklang darf nicht loopen: " + p)


func test_manager_api_headless() -> void:
	var a := _audio()
	if not runner.check(a != null, "Autoload Audio fehlt"):
		return
	runner.check(AudioServer.get_bus_index("Music") >= 0, "Bus Music fehlt")
	runner.check(AudioServer.get_bus_index("SFX") >= 0, "Bus SFX fehlt")
	runner.check(AudioServer.get_bus_index("Ambient") >= 0, "Bus Ambient fehlt")
	runner.check(AudioServer.get_bus_index(a.OUTDOOR_BUS) >= 0, "Bus AmbientOutdoor fehlt")
	var before: int = a.warning_count
	runner.check(a.play_ui("click") != null, "play_ui(click)")
	runner.check(a.play_ui("ui_confirm") != null, "play_ui(ui_confirm)")
	var p3 = a.play_sfx("footstep_concrete", Vector3(3, 0, 4))
	runner.check(p3 is AudioStreamPlayer3D, "3D-Effekt liefert AudioStreamPlayer3D")
	if p3 is AudioStreamPlayer3D:
		runner.check(p3.global_position.distance_to(Vector3(3, 0, 4)) < 0.01, "3D-Position")
	runner.check(a.play_sfx("door_open") is AudioStreamPlayer, "2D-Effekt")
	# Viele Klaenge gleichzeitig: Pool stiehlt den aeltesten, kein Fehler
	for i in 40:
		a.play_sfx("footstep_grass", Vector3(i, 0, 0), -3.0, 1.1)
	a.play_music("menu", 0.0)
	runner.check_eq(a.music_track, "menu", "Musiktitel")
	a.play_music("day", 0.5)
	runner.check_eq(a.music_track, "day", "Musikwechsel")
	a.set_ambience({"city_day": 1.0, "park": 0.4}, 0.0)
	var amb: Dictionary = a.get_ambience()
	runner.check(amb.has("city_day") and amb.has("park"), "Ambiente-Schichten gesetzt")
	a.set_ambience({"city_night": 0.7}, 0.5)
	amb = a.get_ambience()
	runner.check(amb.has("city_night") and not amb.has("city_day"), "Ambiente-Wechsel")
	a.set_outdoor_muffled(true)
	runner.check(a.is_outdoor_muffled(), "Tiefpass an")
	var holder := Node3D.new()
	runner.add_child(holder)
	var lp: AudioStreamPlayer3D = a.attach_loop("siren", holder)
	runner.check(lp != null and lp.get_parent() == holder, "attach_loop")
	var eng := EngineSound.new()
	holder.add_child(eng)
	eng.set_load(0.6, 1.0)
	eng._process(0.5)
	# EventBus-Anbindung darf nicht fehlschlagen
	var bus: Node = runner.get_node("/root/EventBus")
	bus.notify.emit("Test", "money")
	bus.notify.emit("Test", "danger")
	bus.notify.emit("Test", "info")
	bus.player_money_changed.emit(100.0)
	bus.player_money_changed.emit(50.0)
	bus.player_job_changed.emit("cashier")
	bus.open_panel.emit("phone", {})
	bus.close_panels.emit()
	runner.check_eq(a.warning_count, before, "Keine Warnungen bei gueltigen Klaengen")
	a.stop_all()
	runner.check_eq(a.music_track, "", "stop_all beendet Musik")
	runner.check(a.get_ambience().is_empty(), "stop_all beendet Ambiente")
	runner.check(not a.is_outdoor_muffled(), "stop_all hebt Tiefpass auf")
	lp.stop()
	holder.remove_child(eng)
	eng.queue_free()
	holder.queue_free()


func test_missing_sound_warns_once() -> void:
	var a := _audio()
	if a == null:
		return
	var before: int = a.warning_count
	runner.check(a.play_sfx("gibt_es_nicht_123") == null, "Unbekannter Klang liefert null")
	runner.check(a.play_sfx("gibt_es_nicht_123", Vector3.ZERO) == null, "Unbekannter 3D-Klang liefert null")
	runner.check(a.play_ui("gibt_es_nicht_123") == null, "Unbekannter UI-Klang liefert null")
	runner.check_eq(a.warning_count, before + 1, "Genau eine Warnung je Name")
	var s = a._load_stream("res://assets/audio/sfx/fehlt_absichtlich.wav", false)
	runner.check(s == null, "Fehlende Datei liefert null")
	a._load_stream("res://assets/audio/sfx/fehlt_absichtlich.wav", false)
	runner.check_eq(a.warning_count, before + 2, "Fehlende Datei warnt einmal")
	a.play_music("gibt_es_nicht", 0.0)
	runner.check_eq(a.music_track, "", "Unbekannte Musik wird ignoriert")
	a.set_ambience({"gibt_es_nicht": 1.0}, 0.0)
	runner.check(not a.get_ambience().has("gibt_es_nicht"), "Unbekannte Schicht wird ignoriert")
	a.play_sfx(SoundRegistry.PLANNED.keys()[0])
	a.stop_all()


func test_ui_sounds_attach() -> void:
	var root := VBoxContainer.new()
	var b1 := Button.new()
	var inner := HBoxContainer.new()
	var b2 := CheckBox.new()
	inner.add_child(b2)
	root.add_child(b1)
	root.add_child(inner)
	runner.add_child(root)
	UISounds.attach(root)
	runner.check(b1.has_meta("_ui_sounds") and b2.has_meta("_ui_sounds"), "Buttons verbunden")
	var b3 := Button.new()
	inner.add_child(b3)
	runner.check(b3.has_meta("_ui_sounds"), "Spaeter hinzugefuegter Button verbunden")
	UISounds.attach(root)
	runner.check_eq(b1.pressed.get_connections().size(), 1, "Keine Doppelverbindung")
	b1.pressed.emit()
	b1.mouse_entered.emit()
	root.queue_free()


func test_director_layers() -> void:
	runner.check_eq(AmbienceDirector.music_for_hour(12.0), "day", "Mittag = Tagmusik")
	runner.check_eq(AmbienceDirector.music_for_hour(22.0), "night", "Abend = Nachtmusik")
	runner.check_eq(AmbienceDirector.music_for_hour(3.0), "night", "Nacht")
	runner.check_near(AmbienceDirector.night_factor(12.0), 0.0, 0.001, "Tag")
	runner.check_near(AmbienceDirector.night_factor(23.0), 1.0, 0.001, "Nacht")
	var w := WorldState.new()
	w.city = CityGenerator.generate(4242, {"size": "small"})
	w.time_minutes = 12.0 * 60.0
	var d := AmbienceDirector.new()
	d.setup(w, func() -> Vector3: return Vector3.ZERO)
	if not w.city.parks.is_empty():
		var c: Vector2 = w.city.parks[0].rect.get_center()
		var lay := d.compute_layers(Vector3(c.x, 0, c.y), 12.0)
		runner.check(float(lay.get("park", 0.0)) > 0.9, "Im Park: Park-Ambiente (%s)" % str(lay))
	var found_quiet := false
	var found_urban := false
	for dist in w.city.districts:
		var ctr: Vector2 = dist.center
		var lay2 := d.compute_layers(Vector3(ctr.x, 0, ctr.y), 12.0)
		if d.park_weight(ctr) > 0.0:
			continue
		var zone: String = w.city.district_at(ctr).get("zone", "")
		if zone in AmbienceDirector.QUIET_ZONES and not found_quiet:
			found_quiet = true
			runner.check(float(lay2.get("residential", 0.0)) > 0.5, "Wohngebiet-Ambiente in %s" % zone)
		elif zone in ["downtown", "commercial", "business"] and not found_urban:
			found_urban = true
			runner.check(float(lay2.get("city_day", 0.0)) > 0.5, "Stadt-Ambiente in %s" % zone)
			var night := d.compute_layers(Vector3(ctr.x, 0, ctr.y), 23.0)
			runner.check(night.has("city_night") and not night.has("city_day"), "Nachts Nacht-Ambiente")
	runner.check(found_quiet or found_urban, "Mindestens ein Stadtteil geprueft")
	d.set_indoors(true, "shop")
	var ind := d.compute_layers(Vector3.ZERO, 12.0)
	runner.check(ind.has("shop"), "Laden innen: Laden-Ambiente")
	d.set_indoors(false)
	d.set_weather("rain")
	runner.check(d.compute_layers(Vector3.ZERO, 12.0).has("rain"), "Regen-Schicht")
	d.free()
