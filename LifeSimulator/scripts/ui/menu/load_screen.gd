extends MenuScreen
## Spiel laden: Liste aller Spielstaende mit Laden und Loeschen.

var list: UISaveList


func on_open() -> void:
	setup_frame("Spiel laden", "Setze ein bestehendes Leben fort.", "Hauptmenü", "folder")
	max_content_width = 1280
	list = UISaveList.new(false)
	list.load_requested.connect(func(slot: String) -> void:
		UIKit.sound("confirm")
		Game.load_game(slot))
	content.add_child(list)
	var hint := UIKit.label("Doppelklick auf einen Eintrag lädt ihn direkt. Ältere Stände werden als Sicherungskopie aufbewahrt.", "CaptionLabel")
	footer.alignment = BoxContainer.ALIGNMENT_BEGIN
	footer.add_child(hint)
