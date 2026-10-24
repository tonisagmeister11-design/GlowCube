extends MenuScreen
## Einstellungen im Hauptmenue. arg: Name des Start-Tabs.

var panel: UISettingsPanel


func on_open() -> void:
	setup_frame("Einstellungen", "Alle Änderungen werden sofort angewendet und gespeichert.", "Hauptmenü", "gear")
	max_content_width = 1360
	panel = UISettingsPanel.new(str(arg) if arg is String else "graphics", false)
	content.add_child(panel)
