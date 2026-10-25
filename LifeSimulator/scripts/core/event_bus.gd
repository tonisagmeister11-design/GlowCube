extends Node
## Globale Signale zwischen entkoppelten Systemen. Simulationsklassen
## (RefCounted) rufen EventBus nur ueber WorldState.emit_event() auf, damit sie
## auch ohne Szenenbaum (Tests) laufen.

# Session
signal game_started(world)                 # neues Spiel oder geladen, Welt bereit
signal game_saved(slot: String)
signal game_loaded(slot: String)
signal returned_to_menu()

# Zeit
signal hour_passed(day: int, hour: int)
signal day_passed(day: int)
signal time_skipped(minutes: float)        # Schlafen, Gefaengnis, Vorspulen

# Spieler
signal player_money_changed(balance: float)
signal player_needs_changed()
signal player_aged(new_age_years: int)
signal player_life_stage_changed(stage: String)
signal player_died(cause: String)
signal player_job_changed(job_id: String)
signal player_home_changed(property_id: int)
signal player_skill_changed(skill: String, value: float)
signal goal_completed(goal: Dictionary)    # Lebensziel erreicht, siehe Goals.info()

# Welt / Simulation
signal notify(text: String, kind: String)  # kind: info|success|warning|danger|money
signal news_posted(entry: Dictionary)      # {day, hour, category, text}
signal crime_committed(crime: Dictionary)
signal wanted_level_changed(level: int)
signal policy_changed(policy: String, value: float)
signal election_finished(result: Dictionary)
signal business_changed(business_id: int)

# Interaktion / UI
signal interaction_available(prompt: String)
signal interaction_cleared()
signal open_panel(panel: String, context: Dictionary)   # z.B. "shop", {"business_id": 3}
signal close_panels()
signal request_route(target: Vector3, label: String)
signal route_cleared()
