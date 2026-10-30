extends SceneTree
## Build-Schritt: erzeugt res://assets/ui/theme.tres aus UITheme.
##   godot --headless --path . --script res://scripts/ui/build_theme.gd


func _init() -> void:
	var theme_script: GDScript = load("res://scripts/ui/ui_theme.gd")
	var err: int = theme_script.write_theme_file()
	print("Theme geschrieben: %s (Fehler %d)" % [theme_script.THEME_PATH, err])
	quit(0 if err == OK else 1)
