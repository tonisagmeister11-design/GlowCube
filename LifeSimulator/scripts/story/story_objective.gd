extends Area3D
## Zielmarkierung einer Story-Mission. Zeigt den Hinweis des aktuellen
## Schritts und leitet die Interaktion an den StoryRunner weiter.

var runner


func get_prompt(_player = null) -> String:
	if runner == null or not is_instance_valid(runner):
		return ""
	return runner.objective_prompt()


func interact(_player = null) -> void:
	if runner != null and is_instance_valid(runner):
		var r: Dictionary = runner.interact_objective()
		if not r.get("ok", false) and str(r.get("reason", "")) != "":
			EventBus.notify.emit(str(r.reason), "warning")
