extends Node
## Startszene: wendet Einstellungen an und wechselt ins Hauptmenue.


func _ready() -> void:
	await get_tree().process_frame
	if ResourceLoader.exists(Game.SCENE_MENU):
		get_tree().change_scene_to_file(Game.SCENE_MENU)
	else:
		Log.error("boot", "Hauptmenü fehlt: %s" % Game.SCENE_MENU)
