extends Mission
## 2 · Lieferservice – pick up a van in the harbour and deliver the goods in time without damage.


func run() -> void:
	var hp := district_point("harbor")
	objective("Fahr zum Hafen und hol den Lieferwagen.")
	var van := spawn_vehicle("delivery", hp["pos"], hp["dir"], Color(0.9, 0.9, 0.88))
	var b := add_blip(van, Color(0.3, 0.7, 1.0), "", true)
	fail_if(func(): return not is_instance_valid(van) or van.destroyed, "Der Lieferwagen wurde zerstört.")
	if not await until(func(): return player.vehicle == van):
		return
	remove_blip(b)
	var shop := poi("shop_convenience", district_point("oldtown")["pos"])
	var dist := van.global_position.distance_to(shop["entrance_v"])
	time_left = 45.0 + dist / 11.0
	fail_if(func(): return van.body_health < 350.0, "Die Ware ist beschädigt.")
	objective("Liefere die Ware zu %s, bevor die Zeit abläuft. Beschädige sie nicht!" % shop["name"])
	if not await reach(poi_pos(shop, 6.0), 7.0, true):
		return
	time_left = -1.0
	await wait(1.0)
	complete()
