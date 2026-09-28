extends Node
## Long multiplayer session (leaks, disconnects, bandwidth): Tools/Net/run_net_soak.sh

func _ready() -> void:
	var r: Node = load("res://tests/net_soak_runner.gd").new()
	r.name = "NetSoakRunner"
	get_tree().root.call_deferred("add_child", r)
