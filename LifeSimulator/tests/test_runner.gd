extends Node
## Minimaler Testrunner. Start:
##   godot --headless --path . res://tests/test_runner.tscn [-- --filter=city]
## Laedt alle tests/test_*.gd, ruft jede Methode test_* auf und beendet mit
## Exit-Code 0 (alle bestanden) oder 1.

var passed := 0
var failed := 0
var current := ""
var failures: Array[String] = []


func _ready() -> void:
	var filter := ""
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--filter="):
			filter = a.substr(9)
	var files := []
	var dir := DirAccess.open("res://tests")
	for f in dir.get_files():
		if f.begins_with("test_") and f.ends_with(".gd") and f != "test_runner.gd":
			if filter == "" or f.contains(filter):
				files.append(f)
	files.sort()
	var t0 := Time.get_ticks_msec()
	for f in files:
		var script: GDScript = load("res://tests/" + f)
		if script == null:
			_fail("%s: Skript konnte nicht geladen werden" % f)
			continue
		var inst = script.new()
		if inst is Node:
			add_child(inst)
		inst.set("runner", self)
		for meth in inst.get_method_list():
			var name: String = meth.name
			if not name.begins_with("test_"):
				continue
			current = "%s::%s" % [f.get_basename(), name]
			var before := failed
			# await funktioniert fuer synchrone und asynchrone Testmethoden
			await inst.call(name)
			if failed == before:
				passed += 1
				print("  OK    ", current)
		if inst is Node:
			inst.queue_free()
	print("")
	print("Tests: %d bestanden, %d fehlgeschlagen (%.1f s)" % [passed, failed, (Time.get_ticks_msec() - t0) / 1000.0])
	for fl in failures:
		print("  FAIL  ", fl)
	get_tree().quit(1 if failed > 0 else 0)


func _fail(msg: String) -> void:
	failed += 1
	failures.append("%s: %s" % [current, msg])
	printerr("  FAIL  %s: %s" % [current, msg])


func check(cond: bool, msg := "Bedingung nicht erfuellt") -> bool:
	if not cond:
		_fail(msg)
	return cond


func check_eq(a, b, msg := "") -> bool:
	if a != b:
		_fail("%s erwartet %s, erhalten %s" % [msg, str(b), str(a)])
		return false
	return true


func check_near(a: float, b: float, eps: float, msg := "") -> bool:
	if absf(a - b) > eps:
		_fail("%s erwartet %.4f +/- %.4f, erhalten %.4f" % [msg, b, eps, a])
		return false
	return true
