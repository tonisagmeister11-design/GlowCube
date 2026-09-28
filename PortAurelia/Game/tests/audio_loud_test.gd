extends Node
## Music loudness: loud radio, very loud chase music on its own limited bus, city noise ducked
## during a chase and restored afterwards.
##   godot --headless --path Game res://tests/audio_loud_test.tscn

var results := []


func _ready() -> void:
	Settings.apply_audio_only()
	await get_tree().process_frame
	var r := AudioManager.radio
	var ci := AudioServer.get_bus_index("Chase")
	var ri := AudioServer.get_bus_index("Radio")
	check("radio volume setting is full", float(Settings.get_value("audio", "radio")) >= 0.99, str(Settings.get_value("audio", "radio")))
	check("radio bus is boosted", ri >= 0 and AudioServer.get_bus_effect_count(ri) > 0
		and (AudioServer.get_bus_effect(ri, 0) as AudioEffectHardLimiter).pre_gain_db >= 6.0)
	check("chase music has its own very loud bus", ci >= 0 and r.chase.bus == "Chase"
		and (AudioServer.get_bus_effect(ci, 0) as AudioEffectHardLimiter).pre_gain_db >= 12.0
		and AudioServer.get_bus_volume_db(ci) > -0.5 and not AudioServer.is_bus_mute(ci))
	var vi := AudioServer.get_bus_index("Vehicles")
	var v0 := AudioServer.get_bus_volume_db(vi)
	r.start_chase()
	await get_tree().create_timer(0.3).timeout
	check("chase music plays", r.chase.playing)
	check("engines and city noise step back during the chase", AudioServer.get_bus_volume_db(vi) < v0 - 5.0,
		"%.1f -> %.1f dB" % [v0, AudioServer.get_bus_volume_db(vi)])
	r.stop_chase()
	await get_tree().create_timer(2.0).timeout
	check("after the chase everything is back", not r.chase.playing and absf(AudioServer.get_bus_volume_db(vi) - v0) < 0.1)
	var failed := results.filter(func(x): return not x[1]).size()
	print("=== %d checks, %d failed ===" % [results.size(), failed])
	get_tree().quit(1 if failed > 0 else 0)


func check(n: String, ok: bool, info := "") -> void:
	results.append([n, ok])
	print(("PASS " if ok else "FAIL ") + n + ("  (" + info + ")" if info != "" else ""))
