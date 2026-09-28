extends Node
## Shared world test (host + friend in the city): Tools/Net/run_net_world.sh

func _ready() -> void:
	var r: Node = load("res://tests/net_world_runner.gd").new()
	r.name = "NetWorldRunner"
	get_tree().root.call_deferred("add_child", r)
