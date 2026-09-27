class_name CreativeMode
extends Node
## Creative mode: every weapon with endless ammo and an invincible player (toggle in the
## pause menu or start it from the main menu). Stored in the save (world_state.creative).

var _t := 0.0


static func set_enabled(on: bool) -> void:
	Game.player_data.world_state["creative"] = on
	var w := GameWorld.instance
	if w == null or w.player == null:
		return
	var p := w.player as Player
	p.health.invulnerable = on
	if on:
		for id in WeaponData.WEAPONS:
			if id != "unarmed":
				p.weapons.give(id, 99999)
		p.health.heal(1000.0)
		p.health.add_armor(100.0)
		Events.big_message.emit("KREATIVMODUS", "Alle Waffen · unendlich Munition · unbesiegbar", 3.0)
	else:
		Events.notify.emit("Kreativmodus aus.", 2.5)


func _ready() -> void:
	name = "CreativeMode"
	process_mode = Node.PROCESS_MODE_PAUSABLE
	await Events.world_ready
	if Game.player_data and Game.player_data.is_creative():
		set_enabled(true)


func _process(delta: float) -> void:
	if Game.player_data == null or not Game.player_data.is_creative():
		return
	_t -= delta
	if _t > 0.0:
		return
	_t = 0.5
	var w := GameWorld.instance
	if w == null or w.player == null:
		return
	var p := w.player as Player
	p.health.invulnerable = true
	# endless ammo: keep every magazine and reserve topped up
	for id in p.weapons.owned:
		var d := WeaponData.get_def(id)
		if WeaponData.is_ranged(id):
			p.weapons.owned[id]["reserve"] = int(d.get("reserve", 0))
			if int(p.weapons.owned[id]["clip"]) < int(d.get("mag", 0)) and not p.weapons.is_reloading():
				p.weapons.owned[id]["clip"] = int(d.get("mag", 0))
