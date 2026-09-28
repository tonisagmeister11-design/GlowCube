class_name SafeSpot
extends RefCounted
## A safe place to stand: the nearest sidewalk (outdoors - never inside or on top of a building),
## else the nearest road, with the exact ground height. Used for spawning, for friends joining a
## multiplayer game and for the emergency "unstuck" key (U).


## Loads the city around `near` if needed and returns a point on the ground next to it.
static func find(near: Vector3) -> Vector3:
	var w := GameWorld.instance
	if w == null:
		return near
	var base := near
	if base.y < -30.0:
		base.y = 0.0   # interiors sit far below the city: search in the city above
	if w.streaming and not w.streaming.is_loaded_at(base):
		w.streaming.load_area_blocking(base, 140.0)
	var spot := Vector3.INF
	var peds := w.peds as PedManager
	if peds and peds.ped_graph:
		for r in [40.0, 120.0, 300.0]:
			var c: Dictionary = peds.ped_graph.closest(base, r, true)
			if not c.is_empty():
				spot = c["pos"]
				break
	if spot == Vector3.INF and w.graph:
		for r in [40.0, 120.0, 300.0]:
			var l: Dictionary = w.graph.closest_lane(base, r)
			if not l.is_empty():
				spot = l["pos"]
				break
	if spot == Vector3.INF:
		spot = w.data.spawn if w.data else base
	if w.streaming and not w.streaming.is_loaded_at(spot):
		w.streaming.load_area_blocking(spot, 120.0)
	return ground_at(spot)


## The walkable ground at a sidewalk / road point: the first surface slightly above or below it
## (not a roof far above, not a cellar far below).
static func ground_at(p: Vector3) -> Vector3:
	var w := GameWorld.instance
	if w == null:
		return p
	var space := w.get_world_3d().direct_space_state
	var q := PhysicsRayQueryParameters3D.create(p + Vector3.UP * 3.0, p + Vector3.DOWN * 8.0)
	q.collision_mask = 1   # the city (ground, roads, buildings)
	var hit := space.intersect_ray(q)
	if not hit.is_empty():
		return (hit["position"] as Vector3) + Vector3.UP * 0.15
	return p + Vector3.UP * 0.4


## True when the player is below the city surface: either the ground is right above him (seen
## from below, i.e. only its back side - a real ceiling like a bridge faces down and does not
## count), or he fell through with nothing under his feet and ground high above.
static func is_under_ground(p: Vector3, exclude: Array) -> bool:
	var w := GameWorld.instance
	if w == null or p.y < -30.0:
		return false
	var space := w.get_world_3d().direct_space_state
	var up_back := PhysicsRayQueryParameters3D.create(p + Vector3.UP * 0.3, p + Vector3.UP * 14.0)
	up_back.exclude = exclude
	up_back.collision_mask = 1
	up_back.hit_back_faces = true
	var hb := space.intersect_ray(up_back)
	if not hb.is_empty():
		var up_front := PhysicsRayQueryParameters3D.create(p + Vector3.UP * 0.3, p + Vector3.UP * 14.0)
		up_front.exclude = exclude
		up_front.collision_mask = 1
		up_front.hit_back_faces = false
		var hf := space.intersect_ray(up_front)
		if hf.is_empty() or (hf["position"] as Vector3).y > (hb["position"] as Vector3).y + 0.05:
			return true   # the underside of the ground above us: we are inside the ground
	var down := PhysicsRayQueryParameters3D.create(p + Vector3.UP * 0.3, p + Vector3.DOWN * 40.0)
	down.exclude = exclude
	down.collision_mask = 1
	if not space.intersect_ray(down).is_empty():
		return false
	var from_above := PhysicsRayQueryParameters3D.create(p + Vector3.UP * 30.0, p + Vector3.UP * 0.5)
	from_above.exclude = exclude
	from_above.collision_mask = 1
	from_above.hit_back_faces = false
	return not space.intersect_ray(from_above).is_empty()
