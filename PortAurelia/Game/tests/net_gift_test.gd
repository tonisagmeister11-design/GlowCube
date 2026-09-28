extends Node
## Creative mode / drops / gifts in multiplayer: Tools/Net/run_net_gift.sh

func _ready() -> void:
	var r: Node = load("res://tests/net_gift_runner.gd").new()
	r.name = "NetGiftRunner"
	get_tree().root.call_deferred("add_child", r)
