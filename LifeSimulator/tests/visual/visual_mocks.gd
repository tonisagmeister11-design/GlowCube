extends RefCounted
## Testdaten fuer visuelle Pruefungen (wird ausgebaut).

static func make_sample_saves() -> void:
	pass


static func drive_menu(_menu: Control, _c: String) -> void:
	pass


static func make_world() -> WorldState:
	return null


static func make_3d_backdrop() -> Node3D:
	return Node3D.new()


static func drive_game_ui(_ui: Control, _c: String, _w: WorldState) -> int:
	return 30
