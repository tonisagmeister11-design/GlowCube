class_name UISounds
extends RefCounted
## Klaenge fuer Menues mit einem Aufruf: UISounds.attach(root_control)
## verbindet alle BaseButton-Nodes unterhalb von root (mouse_entered -> Hover,
## pressed -> Klick). Spaeter hinzugefuegte Buttons werden automatisch erfasst
## (watch = true).
##
## Pro Button anpassbar ueber Metadaten:
##   set_meta("ui_sound", "confirm")   # anderer Klang beim Druecken ("" = stumm)
##   set_meta("ui_hover", false)       # kein Hover-Klang


static func attach(root: Node, watch := true) -> void:
	if root == null:
		return
	_attach_node(root, watch)


static func _attach_node(n: Node, watch: bool) -> void:
	if n is BaseButton and not n.has_meta("_ui_sounds"):
		var b: BaseButton = n
		b.set_meta("_ui_sounds", true)
		b.mouse_entered.connect(func() -> void: _on_hover(b))
		b.pressed.connect(func() -> void: _on_pressed(b))
	if watch and not n.has_meta("_ui_sounds_watch"):
		n.set_meta("_ui_sounds_watch", true)
		n.child_entered_tree.connect(func(child: Node) -> void: _attach_node(child, true))
	for c in n.get_children():
		_attach_node(c, watch)


static func _on_hover(b: BaseButton) -> void:
	if not is_instance_valid(b) or b.disabled or not bool(b.get_meta("ui_hover", true)):
		return
	_play("hover")


static func _on_pressed(b: BaseButton) -> void:
	if not is_instance_valid(b):
		return
	var s := str(b.get_meta("ui_sound", "click"))
	if s != "":
		_play(s)


static func _play(sound: String) -> void:
	var tree := Engine.get_main_loop() as SceneTree
	if tree == null:
		return
	var audio := tree.root.get_node_or_null("Audio")
	if audio:
		audio.play_ui(sound)
