extends Node
## Two-process multiplayer test. Start the host and the client at the same time:
##   HH_NO_UPNP=1 godot --headless --path Game res://tests/net_test.tscn -- --role host --file /tmp/code
##   HH_NO_UPNP=1 godot --headless --path Game res://tests/net_test.tscn -- --role client --file /tmp/code
## The runner lives under the root so it survives the scene changes (menu -> loading -> world).

func _ready() -> void:
	var r: Node = load("res://tests/net_crowd_runner.gd").new()
	r.name = "NetCrowdRunner"
	get_tree().root.call_deferred("add_child", r)
