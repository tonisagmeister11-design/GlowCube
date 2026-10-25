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
	_render(m, session, st, from, person_id)


## Taschendiebstahl ist moeglich, wenn der Spieler in einer Bande ist oder
## kriminelle Erfahrung hat und nah genug an der Person steht.
static func _crime_option(from: Node, person_id: int) -> bool:
	if person_id < 0 or from == null or not from.is_inside_tree():
		return false
	var cw = from.get_tree().get_first_node_in_group("crime_world")
	if cw == null or not cw.can_pickpocket():
		return false
	var pl := from.get_tree().get_first_node_in_group("player")
	if pl is Node3D and from is Node3D:
		if (pl as Node3D).global_position.distance_to((from as Node3D).global_position) > 4.0:
			return false
	return true


static func _render(m: UIModal, session: DialogueSession, st: Dictionary, from: Node = null, person_id := -1) -> void:
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
		b.pressed.connect(func(): _render(m, session, session.choose(oid), from, person_id))
		opts.add_child(b)
	if _crime_option(from, person_id):
		var pick := UIKit.button("Taschendiebstahl", "", "DangerButton", 520)
		pick.pressed.connect(func():
			var bus := from.get_node_or_null("/root/EventBus")
			m.dismiss()
			if bus:
				bus.open_panel.emit("crime_npc", {"person": person_id}))
		opts.add_child(pick)
	m.body.add_child(opts)
