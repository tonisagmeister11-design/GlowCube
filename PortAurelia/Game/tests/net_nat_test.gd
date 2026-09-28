extends Node
## Internet connection test: host and friend behind different simulated home routers
## (Tools/Net/natsim.py), meeting point = local MQTT broker. See Tools/Net/run_net_nat.sh.

func _ready() -> void:
	var r: Node = load("res://tests/net_nat_runner.gd").new()
	r.name = "NetNatRunner"
	get_tree().root.call_deferred("add_child", r)
