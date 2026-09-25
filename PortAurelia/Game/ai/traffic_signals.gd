class_name TrafficSignals
extends RefCounted
## Fixed-time signal plans for every signalised intersection.
## Phase 0 and phase 1 alternate: green -> yellow -> all red.

const GREEN_A := 15.0
const GREEN_B := 12.0
const YELLOW := 3.0
const ALL_RED := 1.5
const CYCLE := GREEN_A + YELLOW + ALL_RED + GREEN_B + YELLOW + ALL_RED

enum { RED, YELLOW_L, GREEN }

var graph: RoadGraph
var time := 0.0


func _init(g: RoadGraph) -> void:
	graph = g


func advance(dt: float) -> void:
	time += dt


## Signal state for traffic arriving at `node` from `edge`.
func state(node: int, edge: int) -> int:
	var n: Dictionary = graph.nodes[node]
	if n["control"] != "signal":
		return GREEN
	var ph: Dictionary = n["phases"]
	var phase: int = ph.get(edge, 0)
	return phase_state(n["offset"], phase)


func phase_state(offset: float, phase: int) -> int:
	var t := fposmod(time + offset, CYCLE)
	if phase == 0:
		if t < GREEN_A:
			return GREEN
		if t < GREEN_A + YELLOW:
			return YELLOW_L
		return RED
	var b0 := GREEN_A + YELLOW + ALL_RED
	if t >= b0 and t < b0 + GREEN_B:
		return GREEN
	if t >= b0 + GREEN_B and t < b0 + GREEN_B + YELLOW:
		return YELLOW_L
	return RED


## Seconds until the given approach turns green (0 if green now).
func time_to_green(node: int, edge: int) -> float:
	var n: Dictionary = graph.nodes[node]
	if n["control"] != "signal":
		return 0.0
	var phase: int = n["phases"].get(edge, 0)
	var t := fposmod(time + float(n["offset"]), CYCLE)
	var start := 0.0 if phase == 0 else GREEN_A + YELLOW + ALL_RED
	var d := start - t
	if d < 0.0:
		d += CYCLE
	if state(node, edge) == GREEN:
		return 0.0
	return d


## Pedestrians may cross edge `edge` at `node` while cars on that edge have red.
func ped_can_cross(node: int, edge: int) -> bool:
	var n: Dictionary = graph.nodes[node]
	if n["control"] != "signal":
		return true
	return state(node, edge) == RED
