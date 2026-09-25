extends Control
## Loads the world scene in a background thread while showing the loading artwork,
## then hands over to the world (which continues with its own progress overlay).

const WORLD := "res://scenes/world.tscn"
var _overlay: CanvasLayer


func _ready() -> void:
	Input.mouse_mode = Input.MOUSE_MODE_HIDDEN
	_overlay = preload("res://ui/loading_overlay.gd").new()
	add_child(_overlay)
	_overlay.call("set_progress", 0.0, "Spielwelt wird vorbereitet")
	ResourceLoader.load_threaded_request(WORLD)


func _process(_delta: float) -> void:
	var prog := []
	var st := ResourceLoader.load_threaded_get_status(WORLD, prog)
	if st == ResourceLoader.THREAD_LOAD_IN_PROGRESS:
		_overlay.call("set_progress", 0.02 * (prog[0] if prog.size() > 0 else 0.0), "Spielwelt wird vorbereitet")
	elif st == ResourceLoader.THREAD_LOAD_LOADED:
		set_process(false)
		var packed: PackedScene = ResourceLoader.load_threaded_get(WORLD)
		get_tree().change_scene_to_packed(packed)
	elif st == ResourceLoader.THREAD_LOAD_FAILED or st == ResourceLoader.THREAD_LOAD_INVALID_RESOURCE:
		set_process(false)
		push_error("World scene failed to load")
		Game.to_main_menu()
