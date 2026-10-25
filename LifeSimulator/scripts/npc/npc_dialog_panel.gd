class_name NpcDialogPanel
extends RefCounted
## Gespraechsfenster fuer NPCs auf Basis von DialogueSession:
## Sprecher, Text, Antwortoptionen. Pausiert die Spielzeit (Game.push_ui_pause)
## und meldet sich ueber EventBus.open_panel("dialog")/close_panels an,
## damit der Mauszeiger frei ist.


static func open(from: Node, world: WorldState, person_id: int, on_close := Callable()) -> void:
	var session := DialogueSession.new(world, person_id)
	var st: Dictionary = session.start()
	var host := BusPanel.host(from)
	var m := UIModal.custom(host, str(st.get("speaker", "")), "message", 620.0)
	var game := from.get_node_or_null("/root/Game")
	if game:
		game.push_ui_pause()
	var bus := from.get_node_or_null("/root/EventBus")
	if bus:
		bus.open_panel.emit("dialog", {"person": person_id})
	m.tree_exited.connect(func():
		if game:
			game.pop_ui_pause()
		if bus:
			bus.close_panels.emit()
		if on_close.is_valid():
			on_close.call())
	_render(m, session, st)


static func _render(m: UIModal, session: DialogueSession, st: Dictionary) -> void:
	UIKit.clear(m.body)
	UIKit.clear(m.buttons)
	var sub := str(st.get("subtitle", ""))
	if sub != "":
		m.body.add_child(UIKit.label(sub, "CaptionLabel"))
	var card := UIKit.card("CardPanel")
	var txt := UIKit.label(str(st.get("text", "")), "", true)
	txt.custom_minimum_size.x = 520
	card.add_child(UIKit.margin(txt, 14, 10, 14, 10))
	m.body.add_child(card)
	if st.get("end", false):
		var ok := UIKit.button("Gespräch beenden", "", "AccentButton", 200)
		ok.pressed.connect(m.dismiss)
		m.buttons.add_child(ok)
		return
	var opts := UIKit.vbox(8)
	for o in st.get("options", []):
		var oid: String = o.id
		var b := UIKit.button(str(o.label), "", "GhostButton" if oid == "bye" else "AccentButton", 520)
		b.pressed.connect(func(): _render(m, session, session.choose(oid)))
		opts.add_child(b)
	m.body.add_child(opts)
