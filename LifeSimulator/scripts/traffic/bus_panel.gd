class_name BusPanel
extends RefCounted
## Haltestellen-Dialog: naechste Abfahrten, Warten auf den Bus, Einsteigen
## mit Zielwahl. Baut auf UIModal auf (Theme-Varianten, Esc schliesst) und
## haelt waehrenddessen die Spielzeit an.


static func host(from: Node) -> Node:
	var h := from.get_tree().get_first_node_in_group("ui_root")
	if h:
		return h
	var cl := CanvasLayer.new()
	cl.layer = 20
	from.get_tree().root.add_child(cl)
	return cl


## Oeffnet ein UIModal, pausiert die Zeit und meldet das Panel am EventBus an.
static func open_modal(from: Node, title: String, icon_name: String, width := 600.0) -> UIModal:
	var m := UIModal.custom(host(from), title, icon_name, width)
	var game := from.get_node_or_null("/root/Game")
	if game:
		game.push_ui_pause()
	var bus := from.get_node_or_null("/root/EventBus")
	if bus:
		bus.open_panel.emit("modal", {})
	m.tree_exited.connect(func():
		if game:
			game.pop_ui_pause()
		if bus:
			bus.close_panels.emit())
	return m


static func open(from: Node, mgr: TrafficManager, stop_id: int, player) -> void:
	var st: Dictionary = mgr.city.bus_stops[stop_id]
	var m := open_modal(from, "Haltestelle %s" % st.name, "bus_stop")
	var bus := mgr.bus_at_stop(stop_id)
	var fare := mgr.transit.fare(mgr.world)
	if bus:
		var bl: Dictionary = mgr.city.bus_lines[bus.line]
		m.body.add_child(UIKit.label("Linie %d steht bereit. Fahrpreis %s. Wohin möchtest du fahren?" % [int(bl.number), TrafficManager._money(fare)], "SubtitleLabel", true))
		var list := UIKit.vbox(6)
		for d in mgr.transit.destinations_from(mgr.world, bus.line, stop_id):
			var row := UIKit.hbox(10)
			var l := UIKit.label("%s" % d.name, "")
			l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			row.add_child(l)
			row.add_child(UIKit.label("%d min" % int(ceil(d.minutes)), "ValueLabel"))
			var b := UIKit.button("Fahren", "bus", "AccentButton", 110)
			var to: int = d.stop
			b.pressed.connect(func():
				m.dismiss()
				mgr.ride_bus(player, bus, stop_id, to))
			row.add_child(b)
			list.add_child(row)
		var sc := UIKit.scroll(list)
		sc.custom_minimum_size = Vector2(540, 300)
		m.body.add_child(sc)
	else:
		m.body.add_child(UIKit.label("Nächste Abfahrten:", "TitleLabel"))
		var arr := mgr.transit.next_arrivals(mgr.world, stop_id, 5)
		if arr.is_empty():
			m.body.add_child(UIKit.label("Hier fährt derzeit kein Bus.", "SubtitleLabel"))
		for a in arr:
			var row2 := UIKit.hbox(10)
			row2.add_child(UIKit.pill("Linie %d" % a.number, a.color))
			var l2 := UIKit.label("Richtung %s" % a.destination, "")
			l2.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			row2.add_child(l2)
			row2.add_child(UIKit.label("in %d min" % int(ceil(a.minutes)), "ValueLabel"))
			m.body.add_child(row2)
		m.body.add_child(UIKit.label("Fahrpreis je Fahrt: %s" % TrafficManager._money(fare), "CaptionLabel"))
		if not arr.is_empty():
			var wait: float = arr[0].minutes
			var wb := UIKit.button("Auf den Bus warten (%d min)" % int(ceil(wait)), "hourglass", "AccentButton", 260)
			wb.pressed.connect(func():
				m.dismiss()
				var game := from.get_node_or_null("/root/Game")
				if game and game.world == mgr.world:
					game.skip_time(maxf(wait - 0.2, 0.0))
				else:
					mgr.world.advance(maxf(wait - 0.2, 0.0))
				mgr._manage_buses())
			m.buttons.add_child(wb)
	var close := UIKit.button("Schließen", "", "GhostButton", 140)
	close.pressed.connect(m.dismiss)
	m.buttons.add_child(close)
