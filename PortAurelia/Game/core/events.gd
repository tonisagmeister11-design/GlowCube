extends Node
## Global event bus. Systems emit and listen here instead of referencing each other,
## which keeps modules (police, NPCs, missions, audio, UI) decoupled.

# --- combat / crime --------------------------------------------------------
signal gunshot(position: Vector3, shooter: Node, loudness: float)
signal explosion(position: Vector3, radius: float, source: Node)
signal bullet_impact(position: Vector3, normal: Vector3, surface: String)
signal crime_committed(kind: String, position: Vector3, severity: int, offender: Node)
signal npc_killed(npc: Node, killer: Node)
signal npc_threatened(npc: Node, by: Node)
signal vehicle_collision(vehicle: Node, other: Node, impulse: float, position: Vector3)
signal vehicle_stolen(vehicle: Node, by: Node)
signal vehicle_destroyed(vehicle: Node)

# --- player ------------------------------------------------------------------
signal player_spawned(player: Node)
signal player_damaged(amount: float, source: Node)
signal player_died
signal player_busted
signal player_respawned(reason: String)
signal player_entered_vehicle(vehicle: Node)
signal player_exited_vehicle(vehicle: Node)
signal weapon_changed(weapon_id: String)
signal ammo_changed(weapon_id: String, clip: int, reserve: int)

# --- wanted / police -----------------------------------------------------------
signal wanted_changed(level: int)
signal wanted_search_started(center: Vector3, radius: float)
signal wanted_cleared

# --- economy / progression --------------------------------------------------------
signal money_changed(amount: int, delta: int)
signal item_purchased(item_id: String, price: int)
signal property_purchased(property_id: String)
signal vehicle_purchased(vehicle_id: String)
signal stat_changed(stat: String, value)

# --- missions -----------------------------------------------------------------------
signal mission_available(mission_id: String)
signal mission_started(mission_id: String)
signal mission_objective(text: String)
signal mission_completed(mission_id: String, reward: int)
signal mission_failed(mission_id: String, reason: String)
signal side_activity_started(kind: String)
signal side_activity_ended(kind: String, success: bool, reward: int)
signal random_event(kind: String, position: Vector3)

# --- world ------------------------------------------------------------------------
signal time_changed(hour: float)
signal weather_changed(state: String)
signal district_entered(district_id: String, name: String)
signal world_ready

# --- UI -----------------------------------------------------------------------------
signal notify(text: String, duration: float)
signal subtitle(text: String, duration: float)
signal big_message(title: String, subtitle: String, duration: float)
signal interaction_prompt(text: String)
signal waypoint_set(position: Vector3)
signal waypoint_cleared
signal phone_opened
signal phone_closed
signal menu_opened(name: String)
signal menu_closed(name: String)
